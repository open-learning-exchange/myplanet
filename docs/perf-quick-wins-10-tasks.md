# myPlanet performance quick-wins — 10 independent work orders

date: 2026-08-27
base commit: 89fd72c251df68ed01094091d4de7ba7a2571ebe (tag v0.67.58, branch master)
open PRs checked: 16274,16270,16258,16257,16192,16101,16096,15951,15825,15824,15820,15808,15699,15559,15519,15412,15267,15266,15226,15198,15158,15108,14960,14893,14883,14650,14427,13928,13848,13657,13604,13415,13355,13287,10993,8175,4075

Every file cited below was verified **free** of all 37 open PRs (none of the
files below appears in the touch-set of any open PR), so each task is safe to
implement and merge in parallel without clashing with in-flight work.

Task server roadmap numbers: performance quick wins (7), code health/tests (8),
data layer finish (1), viewmodel layer (3). Tasks that also move the KMP north
star (9) or Compose portability (10) note it; most UI tasks keep state in the
ViewModel and avoid android view access, which keeps them 9/10-friendly.

---

### 1. MembersAdapter: cache name/role per bind instead of re-deriving them (roadmap 7, 3)

context: `MembersAdapter.onBindViewHolder` calls `member.toString()` as many as
three times on the bind path (first `.toString()` in the ternary, then again as
the else-value on line 106) and `getRoleAsString()` once more at line 109.
`UserEntity.toString()` (UserEntity.kt:183) returns `"$name"`, but the call is
invoked repeatedly per recycled-bind, and `getRoleAsString()` (UserEntity.kt:142)
does `StringUtils.join(rolesList, ",")` on every call, allocating a new string.
The interesting perf cost is the repeated allocation on an adapter that rebinds
on scroll and on every membership-list refresh.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt`
  (`onBindViewHolder` at lines 101-156; bind body at 102-124).
  leave alone: `JoinedMemberData` and `UserEntity` models, the render of
  `RowJoinedUserBinding`, the payload diff at lines 43-60.

steps:
1. At the top of `onBindViewHolder` (after `val member = memberData.user`),
   compute `val memberName = member.toString()` and
   `val role = member.getRoleAsString()` once.
2. Replace line 106 (`binding.tvTitle.text = if (member.toString() == " ") member.name else member.toString()`)
   with an expression using the precomputed `memberName`.
3. Replace line 109 (`member.getRoleAsString()`) with the precomputed `role`.
4. Remove any now-dead variables.
5. Run the members unit tests and build.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- `./gradlew :app:compileDebugKotlin` compiles.
- Members screen: scrolling a team's member list and toggling a leader payload
  still shows the correct member title and role; no behavior change.

size budget: ~4 changed lines, 1 file.
out of scope: no model changes, no DAO/repository changes, no change to the
bill avatar loading or the payload diff.

---

### 2. StorageCategoryViewModel: track checkbox count incrementally instead of re-scanning every emission (roadmap 7, 3)

context: `StorageCategoryViewModel.toggleItemChecked` rebuilds the whole list with
`map {}` and the fragment then runs `items.count { it.isChecked }` again over the
full list on every state emission (`StorageCategoryDetailFragment.kt:162`). For a
category with hundreds of offline files the selection flow does an O(n) map plus
an O(n) scan on every single checkbox toggle. The class already exposes a small
data class ui-state, so adding one Int is cheap.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt`
  (`StorageCategoryUiState` at 22-27, `toggleItemChecked` at 61-68).
- neighbors to leave alone: `StorageCategoryDetailFragment.kt`, the
  `ResourcesRepository` interfaces and DAOs, `toggleAllChecked` (line 70-76).

steps:
1. Add `val checkedCount: Int = 0` to `StorageCategoryUiState`.
2. In `loadResources`, when setting the loaded items, also set
   `checkedCount = loaded.count { it.isChecked }`.
3. In `toggleItemChecked`, compute the delta from the target row's current
   `isChecked` and pass `state.checkedCount + (if (was) -1 else 1)` instead of
   re-scanning.
4. In `toggleAllChecked`, set `checkedCount = if (newAllChecked) items.size else 0`.
5. In the fragment `collect`, replace `items.count { it.isChecked }` with
   `state.checkedCount` (one-line change in `StorageCategoryDetailFragment.kt:162`).
6. Verify the select-all row / delete button enablement still matches the UI.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Settings > Storage > a category: tapping checkboxes updates the selected-count
  text and the "Delete selected" button enablement exactly as before, without a
  full-list recount each tap.

size budget: ~12 changed lines across 2 files.
out of scope: no DAO changes, no change to delete/export flow, no sorting work.

---

### 3. ChatHistoryAdapter: hoist the static share-dialog data map (roadmap 7, 8)

context: `ChatHistoryAdapter.bindShareChat` builds the share dialog lazily and on
every share-button click calls `getData()` (ChatHistoryAdapter.kt:300-310) which
recreates a fresh `HashMap` + two `ArrayList`s and re-reads three `getString`
resources, even though the entire map is constant for the adapter lifetime.
The same two-group structure ("community", "team/enterprise") is fixed. Recreating
it per-open allocates needlessly on the hot dialog path.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt`
  (`bindShareChat` at 127-180, `getData()` at 300-310).
  leave alone: `ChatShareTargetItem`, `ChatShareTargetAdapter`, the models.

steps:
1. Add a private val field `private val shareDataMap: Map<String, List<String>>`
   initialized once in the adapter `init` body by calling `getData()` there.
2. Replace the `val dataMap = getData() as? Map<String, List<String>> ?: emptyMap()`
   call at line 137 with the cached `shareDataMap`.
3. Keep `getData()` (it is now only called once at construction), or inline its
   body into an initializer if `context.getString` is available at that point.
4. Run ghost tests / build; share one chat and expand both groups.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Chat history > share icon: the dialog still lists both "Share with community"
  and "Share with team/enterprise" groups and expanding each shows the same
  children and share state as before.

size budget: ~6 changed lines, 1 file.
out of scope: no change to dialog layout, no change to share-target logic, no
change to the ListAdapter diff callback.

---

### 4. SyncTimeLogger: drop the duplicate full-list flatten in the summary builder (roadmap 7, 8)

context: `SyncTimeLogger.buildSummary` flattens `apiCallTimes` (a
`ConcurrentHashMap<String, MutableList<ApiCallLog>>`, SyncTimeLogger.kt:37) twice —
`values.flatten().sumOf { it.duration }` and `values.flatten().count { it.success }`
(lines 246-247) — allocating two throwaway lists to compute scalars. `flatten()`
also builds an intermediate `List` each time. The result is a pure micro-opt with
zero behavior change, in a logging path that runs at the end of every sync.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`
  (summary section at 243-252).
  leave alone: `ApiCallLog`, the latency/timing math, the per-endpoint detail loop
  at 254-265, the format helpers.

steps:
1. Replace the two `values.flatten()` calls with a single pass, e.g. compute
   `var totalApiTime = 0L; var successful = 0; apiCallTimes.values.forEach { l ->
   totalApiTime += l.sumOf { it.duration }; successful += l.count { it.success } }`.
2. Keep `totalApiCalls = apiCallTimes.values.sumOf { it.size }` unchanged.
3. Re-run the summary write path.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- The sync detail log (Settings > Sync > details) prints identical API stats
  lines (total calls, success, failed, total time) as before.

size budget: ~6 changed lines, 1 file.
out of scope: no logging-format changes, no change to `dbOperationTimes`
flattening elsewhere.

---

### 5. ConfigurationsRepositoryImpl: trim the server-check body scan instead of a full split (roadmap 7, 8)

context: `checkServerAvailability` (ConfigurationsRepositoryImpl.kt:209-221) takes
the whole response body, `split(",")`es it into a list, drops trailing empties with
`dropLastWhile`, and then only uses the size to test `>= 8`. The `split`+list is
only needed to count fields; the body is a bounded config blob but the allocation
is avoidable and the intent is clearer as a length/count check.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`
  (`checkServerAvailability` at 209-221).
  leave alone: `buildCouchdbUrl`, `getLanguageCodeFromName`, the availability
  logic's 401 branch, and any Repository interface it implements.

steps:
1. Keep the network call and the `withContext(io)` body read.
2. Replace
   `val myList = ss?.split(",")?.dropLastWhile { it.isEmpty() }`
        `val dbCount = myList?.size ?: 0`
   with a scan that counts non-empty comma-separated segments without building a
   list, e.g. `ss?.trim(',')?.isNotBlank() == true && ss.filter { it == ',' }.count() + 1 >= 8`
   (or a small `countSegments` helper). Ensure blank bodies are handled the same.
3. Leave the `code == 401` branch untouched.
4. Run the unit tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Server reachability on the login/server-select screen still returns the same
  true/false for a healthy and for a degraded config payload.

size budget: ~6 changed lines, 1 file.
out of scope: no change to the network layer, no change to `getServerList`, no
change to the reachability retry/timeout behavior.

---

### 6. CrashLogStore: reuse the shared filename parse between save and load (roadmap 7, 8)

context: CrashLogStore parses the same `"<epoch>_<type>.log"` shape in two places:
`isValidLogFile` (lines 21-27) splits the timestamp from the type, and
`loadPendingLogs` (lines 45-50) re-does `removeSuffix`, `indexOf('_')` and
`substring` to recover the same values. `save` also calls `listFiles()` and
`isValidLogFile` per file just to count. The parse logic is duplicated and each
load re-derives fields that validation already computed on the same iteration.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt`
  (`isValidLogFile` 21-27, `loadPendingLogs` 43-56).
  leave alone: `PendingLog`, `save`, `dir`, the sweep caller that drains files.

steps:
1. Introduce a single private `data class ParsedLogName(val time: String, val type: String)`
   and a private `fun parse(file: File): ParsedLogName?` that does the
   `removeSuffix`/`indexOf('_')`/`substring`/`toLongOrNull` checks once.
2. Rewrite `isValidLogFile` to `return parse(file) != null`.
3. Rewrite `loadPendingLogs` to map over files that parse successfully and build
   `PendingLog` from the parsed fields, removing the duplicated string slicing.
4. Re-run the unit tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- A crash/ANR still gets swept into `ApkLog` on next start with the same
  timestamp/type; `save` still respects `MAX_PENDING_FILES`.

size budget: ~14 changed lines, 1 file.
out of scope: no change to `save` write behavior, no change to crash-receiver
wiring.

---

### 7. RequestsViewModel: drop the nested coroutine for post-approval activity (roadmap 7, 8)

context: `RequestsViewModel.respondToRequest` launches a coroutine to call
`respondToMemberRequest`, and inside that launches a *second* child coroutine via
`launch { teamsRepository.recordTeamActivity() }` (RequestsViewModel.kt:59) after a
success. Launching a fire-and-forget child with no scope supervision adds an
unnecessary coroutine indirection; the call is suspend and can run inline, and the
child being un-cancelled can outlive the scope cleanly anyway.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`
  (`respondToRequest` at 44-64).
  leave alone: `TeamsMembersRepository.respondToMemberRequest` /
  `recordTeamActivity` impls (TeamsRepositoryImpl.kt:902), the optimistic-state
  rollback logic, `fetchMembers`.

steps:
1. Replace `launch { teamsRepository.recordTeamActivity() }` with a direct suspend
   call `teamsRepository.recordTeamActivity()` inside the existing coroutine.
2. Ensure any imports of `launch` still used elsewhere in the file are kept.
3. Run the unit tests; manually accept a join request while offline (failure path).

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Accepting a join request still updates the team activity feed exactly once; the
  request row animates away and the member count increments as before.

size budget: ~2 changed lines, 1 file.
out of scope: no change to repository retry/error handling, no change to
`respondToMemberRequest` semantics.

---

### 8. NotificationsViewModel: stop re-lowercasing the type string on every resolve pass (roadmap 7, 3)

context: every notification passes through `resolveType` and
`buildGroupedList`, and each lowercases the type (and message) with
`lowercase(Locale.ROOT)` one or more times — e.g. `resolveType` lowercases
`type` twice (once for the KNOWN_TYPES check, once for the message scan) and
`buildGroupedList` lowercases it a third time at line 233. For a screen that
renders an entire inbox, the same string is allocated repeatedly. A single
normalization captured per notification removes the duplicate work.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
  (`resolveType` at 258-283, `buildGroupedList` at 224-256).
  leave alone: `NotificationsAdapter`, the `Notification` model, the repo calls.

steps:
1. In `formatNotification` (line 335), compute `val loweredType = notification.type.lowercase(Locale.ROOT)`
   once and thread it into `resolveType(notification.type, notification.message, notification.subType)`.
2. Refactor `resolveType` to accept the pre-lowered type and skip the duplicate
   `lowercase` on `type`, keeping the message scan as-is.
3. Update `buildGroupedList` to use the already-lowered type string (pass it in or
   keep the one-line normalization but only per notification, not multiply).
4. Compile and run tests; open the notifications screen and expand each group.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Each notification still lands in the same group (join_request, task, chat,
  resource, storage, etc.) and the "unread" counts per header are unchanged.

size budget: ~10 changed lines, 1 file.
out of scope: no change to the backend message parsing (`parseTaskDate`), no
change to what counts as unread.

---

### 9. PhotoUploader: batch the uploaded-mark so we stop UPDAting per photo (roadmap 7, 5)

context: `PhotoUploader.uploadSubmitPhotos` uploads photos in batches, and for each
successful photo it calls `submissionsRepository.markPhotoUploaded(photoId, rev, id)`
(PhotoUploader.kt:51), which issues a single `UPDATE submit_photos SET ...` per photo
(`SubmitPhotosDao.markUploaded`, DAO line). Room issues a statement per call, so a
100-photo roll lowers to 100 separate UPDATE statements on the main upload path.
`markSubmitPhotosUploaded` already exists for a single row; a batch variant removes
the per-item write amplification.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt`
  (batch loop 38-72).
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`
  (add a batch `markPhotosUploaded` near `markPhotoUploaded` at 600-602).
- neighbors to leave alone: `data/room/dao/SubmitPhotosDao.kt` and the single-row
  `markUploaded`/`markSubmitPhotosUploaded` callers. (DAO is free, but keep changes
  to the repository/service layer only; no schema change.)

steps:
1. Add a public suspend `markPhotosUploaded(uploads: List<Triple<String, String, String>>)`
   to `SubmissionsRepository` and implement it in `SubmissionsRepositoryImpl` as a
   loop (or, if truly desired later, a single batch statement — keep it dependency-free).
2. In `PhotoUploader`, collect `Triple(photoId, rev, id)` for each successful photo
   in the batch, and after the inner `batch.forEach`, call the new batch method once
   per batch instead of per photo.
3. Keep `markPhotoUploaded` for any other callers.
4. Build and run tests; upload photos through the submit-photos flow.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Submit-photos upload still marks each uploaded photo as uploaded (`uploaded=1`,
  sets `_rev`/`_id`) exactly once; the backend IDs match and attachments still upload.

size budget: ~12 changed lines across 2 files.
out of scope: no schema change, no DAO change, no change to attachment upload or
to the retry/queue logic.

---

### 10. MyCourse: stop rebuilding + deduplicating the userId / links lists needlessly (roadmap 7)

context: `MyCourse.setUserId` (MyCourse.kt:53-60) copies the whole `userId` list,
checks contains, adds, then calls `.distinct()` — a full pass + new list every call,
even though the preceding `contains` check already guarantees uniqueness.
`saveConcatenatedLinksToPrefs` (MyCourse.kt:94-110) builds a new list
(`linksToProcess = concatenatedLinks.toList()`), adds each to a `HashSet`, then
re-`toList()`s and JSON-encodes — three list copies for what is a small set. These
run during course save and sync-links flush, and the allocator churn is avoidable.

files:
- `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt`
  (`setUserId` 53-60, `saveConcatenatedLinksToPrefs` 94-110).
  leave alone: `removeUserId`, `getNumberOfSteps`, `serialize`, and the
  `concatenatedLinks` static-owned lock discipline (keep it synchronized).

steps:
1. In `setUserId`, drop the trailing `.distinct()` — `contains` already guarantees
   uniqueness — and return early from the private `current` list. (Set the list only
   when it changed.)
2. In `saveConcatenatedLinksToPrefs`, add the pending links directly to the
   existing `HashSet` via `existingConcatenatedLinks.addAll(linksToProcess)` and
   JSON-encode that set (no extra `toList()` wrapper required beyond what `gson`
   needs).
3. Keep both methods `synchronized` on the same statics as today.
4. Run the unit tests; save a course and flush links on sync.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- Saving a course keeps the member list deduped as before; the concatenated-links
  pref stores the same set as before and no duplicates creep in.

size budget: ~6 changed lines, 1 file.
out of scope: no change to `serialize` resource mapping, no change to the
`concatenatedLinks` upper/lower API, no change to other models.

---

## self-check results

- exactly 10 tasks: yes
- no file appears in more than one task: yes (each task's files are disjoint sets;
  task 9 uses two files, all others one)
- every cited path opened & confirmed to exist: yes (MembersAdapter, StorageCategoryViewModel/Fragment, ChatHistoryAdapter, SyncTimeLogger, ConfigurationsRepositoryImpl, CrashLogStore, RequestsViewModel, NotificationsViewModel, PhotoUploader, SubmissionsRepositoryImpl, MyCourse all read during exploration)
- every task has all 7 template sections: yes
- no task under 15 lines: yes (all exceed 15 rendered lines)
- no task touches a file from the open-PR list with tag ready-to-merge: yes — every
  cited file was checked against the union of all 37 open PRs' touch-sets and was free
- plan written to docs/: yes (this file)

No repository implementation code was written in this run; the deliverable is the
plan above.
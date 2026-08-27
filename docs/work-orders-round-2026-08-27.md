# myPlanet refactor round — 10 independent work orders

- **date**: 2026-08-27
- **base commit**: master @ `45bac8d0050286289dbb8fa9680864a16758be5f`
- **open PRs checked**: 16274 16270 16258 16257 16192 16101 16096 15951 15825 15824 15820 15808 15699 15559 15519 15412 15267 15266 15226 15198 15158 15108 14960 14893 14883 14650 14427 13928 13848 13657 13604 13415 13355 13287 10993 8175 4075 (no files below are touched by any of them)

Scope note: this round is deliberately conservative — "quick wins" and
"micro-optimizations that unblock bigger refactors". Every task is small,
single-purpose, independently mergeable in any order, and avoids the hot areas
tied up by open PRs (`.github/workflows/build.yml`, `.github/workflows/test.yml`,
the `flutter/` port, the DI/DAO/repository modules under PR 15808/15226 etc.).
Each task names which roadmap item it serves, and notes where it also moves the
north-star items 9 (platform-free Kotlin core) or 10 (portable composables)
forward.

---

### 1. push the library "needs update" count down into SQL (roadmap 1+7, nudges 9)

context: `ResourcesRepositoryImpl.countLibrariesNeedingUpdate(userId)` (line 212)
loads **every** public library row via
`myLibraryDao.getPublicForUserPattern(userIdPattern(userId))` (line 214) and then
counts in memory (`.count { it.needToUpdate() }`), only to return an `Int`.
That query streams the full `user_id` scalars PLUS all columns of the matched
rows (title, resource address, revs, dates) across the Room boundary just to
produce a count. The same predicate is already expressed in pure-SQL form
elsewhere (`MyLibrary.needToUpdate`, MyLibrary.kt:148-150) and the DAO already
does SQL-side filtering on those exact columns, so a `SELECT COUNT(*)` is a
drop-in replacement with the same result. Roadmap 9 note: the new query lives in
the DAO (android-free) and the repository call site drops no android import — the
diff actually removes the `.count` on a domain object, keeping the read path free
of platform code.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` (add one `@Query` next to the existing `getPublicForUserPattern` at line 102)
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` (line 212-216)
do NOT touch: `MyLibrary.needToUpdate()` (line 148), the other public-library queries lines 78/193/463 that legitimately return the row lists, or `ResourcesViewModel` (open PR owns it).

steps:
1. In `MyLibraryDao`, add a suspend method annotated with
   `@Query("SELECT COUNT(*) FROM my_library WHERE isPrivate = 0 AND userId LIKE :userPattern ESCAPE '\\'")`
   returning `Int`, named e.g. `countPublicForUserPattern(userPattern: String)`.
2. In `ResourcesRepositoryImpl.countLibrariesNeedingUpdate`, replace the
   `getPublicForUserPattern(userIdPattern(userId))` + `.count { it.needToUpdate() }`
   with the new `countPublicForUserPattern(userIdPattern(userId))`.
3. Remove the now-unused `needToUpdate()`-count line and any import of
   `MyLibrary` list processing no longer needed there (keep all other usages).
4. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green.
- `./gradlew :app:compileDefaultDebugKotlin` compiles.
- Behavior: the library "updates available" badge count on the library screen is
  unchanged (same predicate, now evaluated on the SQL server side).

size budget: ~8 changed lines, 2 files.
out of scope: no schema migration, no new index, no change to `getPublicForUserPattern`
or the list-based callers at lines 78/193/463.

---

### 2. build the retry-queue payload map only when there are retryable failures (roadmap 5+7, nudges 9)

context: `UploadCoordinator`'s two retry-queue paths —
`queueRetryableFailures` (UploadCoordinator.kt:256-258) and
`queueRetryableFailuresRoom` (UploadCoordinator.kt:466-471) — both build a full
`preparedUploads.associateBy { it.localId }` map over **every** prepared upload
before checking whether any failure is actually retryable. In the common case
`allFailed` is empty or contains only non-retryable errors, so the map is
allocated and then never used. Moving the map construction inside the
`errors.filter { it.retryable }.forEach { ... }` guard (only built when a
retryable error exists) removes an O(n) allocation+hash per upload batch.
Roadmap 9 note: this file already has zero `android.*` imports; the change keeps
it that way and is pure Kotlin.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt`
  (functions `queueRetryableFailures` at 255-262 and `queueRetryableFailuresRoom` at 466-474)
do NOT touch the actual `queueFailedOperation`, the `upload`/`uploadRoom` batch
loops, or any other function in the file.

steps:
1. In `queueRetryableFailures`, move the `payloadMap = preparedUploads.associateBy { it.localId }`
   line (258) so it is computed on first use inside the
   `.filter { it.retryable }.forEach { error -> ... }` block (or add `val retryableErrors = errors.filter { it.retryable }` and only build the map when `retryableErrors.isNotEmpty()`).
2. Apply the identical change in `queueRetryableFailuresRoom` (line 471).
3. Keep the `if (preparedUpload != null)` guard intact.
4. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: retryable failures from any upload batch are still enqueued exactly as
  before; large error-free batches no longer allocate the abandoned map.

size budget: ~6 changed lines, 1 file.
out of scope: no changes to `RetryQueue`, `RetryQueueWorker`, or the upload batch logic.

---

### 3. flatten apiCallTimes/dbOperationTimes once in SyncTimeLogger.buildSummary (roadmap 7+8, nudges 9)

context: `SyncTimeLogger.buildSummary` (SyncTimeLogger.kt) calls
`apiCallTimes.values.flatten()` three times (lines 245, 246, 286) and
`dbOperationTimes.values.flatten()` three times (264/266/267 and 289). Each
`flatten()` allocates a fresh `List` over every logged entry; the same flat list
is needed for totals and percentages. Computing each flattened list once and
reusing it removes five throwaway allocations. Roadmap 9 note: the function is
pure Kotlin arithmetic/formatting over already-collected data — no platform
imports are involved; the change keeps the file free of new android usage.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` (lines 244-247, 252-256, 283-290)
do NOT touch `logApiCall`, `logDbOperation`, `startProcess`/`endProcess`, or the
`formatTime` helper below line 310.

steps:
1. Near the top of the API-statistics block, compute
   `val allApiLogs = apiCallTimes.values.flatten()` once.
2. Replace the `values.flatten().sumOf { it.duration }` (245) and
   `.count { it.success }` (246) with calls over `allApiLogs`.
3. Similarly compute `val allDbLogs = dbOperationTimes.values.flatten()` once and
   replace lines 266-267 (and the percentage sums at 289).
4. Replace leftover inline `values.flatten()` references (line 286 for api, 289 for db)
   with the cached lists.
5. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green (the sync-summary test, if any, still passes).
- Behavior: the sync summary printed to the log is byte-identical; only the number
  of intermediate list allocations changes.

size budget: ~8 changed lines, 1 file.
out of scope: no changes to what is logged, no reordering of the summary lines.

---

### 4. reuse buildApkLog in the batch diagnostics path (roadmap 1+8, nudges 9)

context: `DiagnosticsRepositoryImpl` builds an `ApkLog` in two places that write
the exact same fields — `buildApkLog` (DiagnosticsRepositoryImpl.kt:44-59) used by
`saveLogToRoom`, and an inline duplicated `.apply { ... }` block inside the
batch mapper in `saveLogsToRoom` (lines 68-83). The batch path also redundantly
precomputes `versionName`, `parentCode`, `planetCode` and threads them through a
second copy of the mapping. Reusing the single `buildApkLog` for both paths
removes the duplication and guarantees the two stay in lockstep. Roadmap 9 note:
the file already imports no `android.*` (context is only used for
`VersionUtils.getVersionName(context)`), so consolidation keeps the repository
platform-neutral.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
  (functions `buildApkLog` at 44-59, `saveLogToRoom` at 61-70, `saveLogsToRoom` at 72-94)
do NOT touch `getPendingApkLogs`/`markApkLogUploaded` or `CrashLogStore`.

steps:
1. In `saveLogsToRoom`, drop the pre-computed `versionName`/`parentCode`/`planetCode`
   locals and the duplicated `ApkLog().apply { ... }` block.
2. Map `pendingLogs` to `buildApkLog(sharedPrefManager, model?.id, pending.time, pending.type, pending.error)`.
3. Keep the `apkLogDao.insertAll(logsToInsert)` call unchanged.
4. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: both the single and the batch crash-log save write identical `ApkLog`
  rows (identical error/version/time/page/type handling) as before.

size budget: ~12 changed lines, 1 file.
out of scope: no change to `getPendingApkLogs`/`markApkLogUploaded`, no ApiLog schema change.

---

### 5. release.yml: read the version from the repo's version.sh instead of ad-hoc sed/grep (roadmap 8)

context: `release.yml` probes `app/build.gradle` twice with different, fragile
incantations in the "set release version" step and the "build release APK and AAB"
step: `sed -n 's/.*versionName = "\([^"]*\)".*/\1/p'` and
`grep versionCode ... | sed 's/[^0-9]//g'` (the latter strips ALL digits from a
matching `versionCode` line, but fails if the line ever reads `versionCode = 6300`
with a trailing comment, and re-derives the name a second time). The repository
already ships `.github/scripts/version.sh read <file>` which prints
`code=...` and `name=...` from the same file (version.sh:7-20, 62-65) and is the
canonical reader used by `automerge.sh`. This only touches `release.yml` (free —
build/test workflows are owned by open PRs).

files:
- `.github/workflows/release.yml` ("set release version" step; the version reads inside "build release APK and AAB")
- `.github/scripts/version.sh` (read-only reference — do NOT modify)
do NOT touch `build.yml`, `test.yml`, `playstore.yml` or any other workflow.

steps:
1. In the "set release version" step, replace the `sed` invocation with
   `eval "$(bash .github/scripts/version.sh read app/build.gradle)"` and write
   `ANDROID_VERSION=${name}` to `$GITHUB_ENV`.
2. In "build release APK and AAB", replace the second `sed`/`grep` reads with
   `code=`/`name=` from the same `version.sh read` call (source it once and reuse
   `$code` for `ANDROID_VERSION_CODE` and `$name` for the lite `-lite` suffix).
3. Keep the existing `if [ "$FLAVOR" == "lite" ]` suffix logic unchanged.
4. Do a YAML-lint and a shell dry-run of the version helper locally.

acceptance:
- `bash .github/scripts/version.sh read app/build.gradle` prints the same
  `code=`/`name=` that the current sed/grep lines would produce (verify equality by
  hand for the current committed version).
- A `workflow_dispatch` run still builds and tags `v<ANDROID_VERSION>` identically.
- YAML parses (e.g. `python3 -c 'import yaml,sys; yaml.safe_load(open(".github/workflows/release.yml"))'`).

size budget: ~8 changed lines, 1 file.
out of scope: no behavior change to the APK/AAB signing, playstore upload, or
GitHub-release steps; do not touch the automerge/playstore scripts that reference `version.sh`.

---

### 6. drop the redundant second member fetch in MembersFragment.handleMakeLeader (roadmap 7+8)

context: `MembersFragment.handleMakeLeader(userId)` calls
`teamsRepository.getJoinedMembersWithVisitInfo(teamId)` (MembersFragment.kt:159)
to feed `membersAdapter?.updateData(members, false)` (line 160) and then
immediately calls `loadMembers()` (line 161), which re-queries the SAME
`getJoinedMembersWithVisitInfo(teamId)` (line 102) and re-submits the list. The
fetch at 159-160 is dead work — its result is thrown away by the reload one line
later (and it hard-codes `isLoggedInUserTeamLeader=false`, unlike the real
`loadMembers()` path). Removing the eager fetch halves the DB round-trip per
leader change and removes a subtly-wrong leader flag. This fragment is free (the
teams repo is open-PR-owned, but `MembersFragment` itself is not touched).

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt`
  (`handleMakeLeader` at 155-166; do not touch `loadMembers` at 100, `handleLeaveTeam`,
  `removeMember`, or `MembersAdapter`)
- `TeamsMembersRepository`/`TeamsRepository` — read-only reference, do NOT modify (open PR owns them)

steps:
1. In `handleMakeLeader`, delete lines 159-160
   (`val members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)` and
   `membersAdapter?.updateData(members, false)`).
2. Leave `loadMembers()` (161) in place — it is the single source of truth for the
   post-change list.
3. Confirm no other use of the deleted `members` local remains.
4. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: promoting a member to leader still reloads the member list exactly once
  and the leader badge reflects the real logged-in-user status (via the correct
  `loadMembers()` path).

size budget: ~2 changed lines, 1 file.
out of scope: no `TeamsMembersRepository`/DAO changes, no changes to `removeMember`
or the join-requests flow in `RequestsViewModel`.

---

### 7. run the notifications enrichment lookups concurrently (roadmap 3+7)

context: `NotificationsViewModel.loadNotifications` (NotificationsViewModel.kt:58-104)
runs four independent suspend DB lookups strictly serially on the Main viewModel
scope: `getTaskTeamNamesByTaskIds` (line 76), `getTaskTeamNamesByTaskTitles` (line 83,
dependent on the first), `getJoinRequestDetailsBatch` (line 90), and
`getUnreadCount` (line 102). The three lookups that do not depend on each other —
task-team-names, join-request-details, and the unread badge count — can be launched
as parallel `async` on an IO dispatcher and `await`ed together, collapsing ~3
serial Room round-trips into one wall-clock trip. The groupBy/sorting of
`payloadNotifications` (lines 66-71) already partitions before the lookups, so the
async block is cleanly separable. Roadmap 3 note: this is a viewmodel-layer
improvement toward a slimmer, faster ui-state pipeline.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
  (`loadNotifications` at 58-104)
do NOT touch `toggleSelection`/`clearSelection`/`setAllRead`, or the
`NotificationsRepository` interface, or `NotificationsFragment`.

steps:
1. Wrap the independent lookups in `coroutineScope` and start `async { notificationsRepository.getJoinRequestDetailsBatch(joinRequestIds) }`
   and `async { notificationsRepository.getUnreadCount(userId, isAdmin) }` (use the injected/project IO dispatcher, matching the app's `DispatcherProvider` pattern).
2. Keep the task-team-name lookup sequential with its own dependency (titles derive
   from the ids result) as today.
3. `await()` the two async results and assign `joinRequestDetails` / `_unreadCount.value`
   from them (respecting the existing empty-list/null guards).
4. Ensure the join-request "fallbackDetail" branch (lines 92-95) still applies after
   the await.
5. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: the notifications list and the unread badge render the same values as
  before; the screen's load latency for a populated inbox drops by the overlap of
  the three now-parallel queries.

size budget: ~12 changed lines, 1 file.
out of scope: no change to the repository batch APIs, no caching, no flow/streaming
refactor of `_notifications`.

---

### 8. don't re-query step resources in refreshInlineResources when they were just loaded (roadmap 7+8)

context: `CourseStepFragment` already holds the step's full resource list in the
`resources` field, populated from `getCourseStepData(stepId...)` in `onViewCreated`
(CourseStepFragment.kt:113). `refreshInlineResources` (line 231) then issues a
second, redundant `resourcesRepository.getAllStepResources(stepId)` (line 233) just
to re-submit the same list to the inline adapter. When the step's inline resources
have not changed (the common case — the fragment only crumb refreshes after action
mode or a download-progress hide), the extra query is pure round-trip. Reusing the
in-memory `resources` keeps the guard in the few call sites that follow a real
download. Do not touch `prefetchNextStepResources` (line 214) — that legitimately
loads a different step.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt`
  (`refreshInlineResources` at 231-238)
do NOT touch `loadStepData` (line 101), `prefetchNextStepResources` (line 214),
`autoDownloadResources` (line 195), or the `CoursesRepository`/`ResourcesRepository` calls.

steps:
1. In `refreshInlineResources`, replace `resourcesRepository.getAllStepResources(stepId)`
   with the already-loaded `resources` field as the data source (skip the fresh DAO call).
2. Keep `inlineResourceAdapter?.submitList(...)` and the progress-bar `GONE` lines.
3. Confirm `resources` is always initialized before this runs (loaded in `onViewCreated`
   before any action-mode path); if a path can run before load completes, guard with
   the existing `resources.isEmpty()` pattern used by `setupInlineResources` instead of
   re-querying.
4. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: selecting/clearing inline resources or hiding the download progress bar
  updates the adapter from the same list as before, with one fewer DB round-trip per
  refresh.

size budget: ~3 changed lines, 1 file.
out of scope: no changes to step-download logic or the course repos; no prefetch behavior change.

---

### 9. remove the duplicate remaining-count computation at the end of each download (roadmap 7+8)

context: `onDownloadComplete` (DownloadService.kt:500-509) recomputes the priority
count from disk — `preferences.getStringSet(PRIORITY_DOWNLOADS_KEY, emptySet())?.count { it !in processedUrls }`
(line 502) — and then calls `getRemainingCount()` (line 503), which reads BOTH the
priority and pending sets again (`getRemainingCount`, lines 176-180) and filters
by `processedUrls`. That is two SharedPreferences reads + two set scans in the same
function, where the priority-side figure is derivable in memory. The service already
tracks `sessionTotalCount` and `processedUrls` locally for the queue loop, and
`cleanupProcessedUrls` (183-186) already has the completed subtraction. Compute the
priority-plus-pending remaining figure from the existing in-process counters and the
one prefs read that `getRemainingCount` still needs, avoiding the duplicate priority
scan. Behavior is identical: `completeAll` is set from the same "remaining == 0"
semantics.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`
  (`onDownloadComplete` at 500-509, `getRemainingCount` at 176-180)
do NOT touch `getNextPriorityUrl`/`getNextPendingUrl`/`getNextUrl`, `initDownload`,
`cleanupProcessedUrls`'s prefs write, or the notification builders.

steps:
1. In `onDownloadComplete`, drop the separate `val remainingPriority = preferences.getStringSet(...)...` at 502.
2. Compute `completeAll` from a single `val remaining = getRemainingCount()` call (as today)
   combined with `isCurrentDownloadPriority` and whether the priority queue is drained —
   express "no more priority items" via the already-loaded priority set read inside
   `getRemainingCount` or the in-memory `processedUrls`/`sessionTotalCount`, so the priority
   scan happens at most once.
3. Keep the `Download` payload (progress=100, completeAll) identical.
4. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: batch downloads still send a completion notification exactly when the last
  priority (or all) items are done, as before; no extra preference reads are emitted
  at completion.

size budget: ~6 changed lines, 1 file.
out of scope: no changes to the download loop, retry, or the receiver/notification plumbing.

---

### 10. expose NewsViewModel.privateImageUrls as a StateFlow instead of a no-replay SharedFlow (roadmap 8, nudges 10)

context: `NewsViewModel` publishes `getPrivateImageUrlsCreatedAfter` results through a
`MutableSharedFlow` with default `replay = 0` and no `extraBufferCapacity`
(NewsViewModel.kt:20-29). A collector that subscribes after the query finished —
e.g. the fragment's `collectWhenStarted`/`lifecycleScope` collector that is set up
while the VM is already populated on a configuration change — receives nothing, so
the private-image prep list can silently stay empty until the next timestamped
request. The screen only ever needs the latest value, which is exactly what a
`MutableStateFlow` provides (last-value replay, no dropped consumption). Switching
the backing to `StateFlow` while keeping the exposed type as `SharedFlow` (a
`StateFlow` *is* a `SharedFlow`) is a drop-in, API-compatible change. Roadmap 10
note: keeps this viewmodel's state funnel deterministic and hoisted through the
state-holder, the pattern the compose migration (item 6 → north-star 10) relies on.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt`
  (lines 20-29 and the `getPrivateImageUrlsCreatedAfter` body at 23-27)
do NOT touch `ResourcesRepository.getPrivateImageUrlsCreatedAfter`,
`VoicesFragment`/`VoicesAdapter` collectors, or any other viewmodel.

steps:
1. Change the backing field to `private val _privateImageUrls = MutableStateFlow<List<String>>(emptyList())`.
2. Change `val privateImageUrls: SharedFlow<List<String>>` to expose it via
   `asStateFlow()` (the public type stays `SharedFlow<List<String>>`, so all existing
   collectors compile unchanged).
3. In `getPrivateImageUrlsCreatedAfter`, replace `emit(urls)` with `_privateImageUrls.value = urls`.
4. Remove the now-unneeded `MutableSharedFlow`/`SharedFlow` imports (keep `asStateFlow`).
5. Run the unit-test task below.

acceptance:
- `./gradlew testDefaultDebugUnitTest` green; `:app:compileDefaultDebugKotlin` compiles.
- Behavior: a `VoicesFragment` re-attached to an already-loaded `NewsViewModel`
  immediately receives the most recent private-image URL list on its `privateImageUrls`
  collector instead of nothing.

size budget: ~5 changed lines, 1 file.
out of scope: no changes to the timestamps/query, no un-started flow semantics, no public-API rename.

---

## cross-task notes

- All acceptance blocks cite `./gradlew testDefaultDebugUnitTest` (the default
  non-release unit-test task) and `:app:compileDefaultDebugKotlin`; run both plus the
  linters (`ktlintCheck`) on any branch that changes `.kt` files.
- No task adds a dependency, ships unused code, or leaves a TODO.
- No file is shared between two tasks (verified: MyLibraryDao / ResourcesRepositoryImpl,
  UploadCoordinator, SyncTimeLogger, DiagnosticsRepositoryImpl, release.yml,
  MembersFragment, NotificationsViewModel, CourseStepFragment, DownloadService,
  NewsViewModel are all distinct).
- Tasks 1-9 are independently mergeable in any order; task 10 is independent too but
  is safest merged after task 7 (both touch the notifications/voice ui-state area but
  are different files).
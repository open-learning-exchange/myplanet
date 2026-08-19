# myPlanet refactor round — performance quick wins

- **date:** 2026-08-19
- **base commit:** `9c54a0341557a7e7ae4bdc313fd1c97c0cc23b32` (master, "actions: smoother workflow automerge release retrying (fixes #15814) (#15813)")
- **open PRs checked (42):** 15820, 15808, 15772, 15771, 15769, 15699, 15698, 15656, 15650, 15614, 15594, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14457, 14427, 14030, 13928, 13848, 13657, 13604, 13447, 13415, 13355, 13287, 13051, 10993, 8175, 7052, 5977, 5243, 4075
- **method:** every open PR head was fetched and diffed against its merge-base with master; the union of touched files (126 existing `main` Kotlin files + 29 test files) is off-limits. Every task below was checked against that blocklist and every cited path/symbol/line was opened and confirmed at the base commit.
- **notably off-limits this round** (open PRs own them): `TeamsRepositoryImpl.kt`, `TeamsRepository.kt`, `RequestsViewModel.kt`, `MembersAdapter.kt`, `TeamViewModel.kt`, `TeamCalendarFragment.kt`, `BaseRecyclerFragment.kt`, `BaseContainerFragment.kt`, `UrlUtils.kt`, `FileUtils.kt`, `MyLibrary.kt`, `ResourcesFragment.kt`, `CoursesRepositoryImpl.kt`, `SyncActivity.kt`, `TransactionSyncManager.kt`, `UploadManager.kt`, `RepositoryModule.kt`, `RoomModule.kt`, `AppDatabase.kt`.

---

### 1. memoize `DateTimeFormatter` instances in TimeUtils (roadmap 7, 9)

context: `TimeUtils.formatDate(date: Long, format: String?)` builds a brand-new formatter on every single call — `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt:142` reads `val formatter = DateTimeFormatter.ofPattern(format ?: "", defaultLocale).withZone(ZoneId.systemDefault())`. `DateTimeFormatter.ofPattern` parses the pattern string and builds a printer/parser chain, which is far more expensive than the formatting itself. This runs per row in list binds (`EnterprisesReportsAdapter.kt:59` and `:70` call it four times per row, `EnterprisesFinancesAdapter.kt:42`, `HealthExaminationAdapter.kt:62`) and per visible day cell in the team calendar decorator (`TeamCalendarFragment.kt:183`, `:190`). Nine other sites in the same file do the same thing: lines 96, 99, 115, 119, 206, 208, 211, 223, 224. Only a handful of formatters are ever needed — the file already hoists six of them as `by lazy` vals (lines 21–45), so the pattern to follow is right there.

files: `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt` only — add a private pattern-keyed formatter cache and route the ten per-call `DateTimeFormatter.ofPattern(...)` sites through it (`getAge` lines 96/99, `getFormattedDate(String?, String?)` lines 115/119, `formatDate(Long, String?)` line 142, `formatDateToDDMMYYYY` lines 206/208/211, `convertToISO8601` lines 223/224). Leave the six existing `by lazy` formatters (lines 21–45) exactly as they are, and do NOT touch any caller — `TeamCalendarFragment.kt`, `EnterprisesReportsAdapter.kt`, `EnterprisesFinancesAdapter.kt` and `HealthExaminationAdapter.kt` stay untouched, and `TeamCalendarFragment.kt` is owned by an open PR.

steps:
1. Add a private cache in the existing `companion`-less `object TimeUtils` body, e.g. a `ConcurrentHashMap<String, DateTimeFormatter>` plus a private `fun formatterFor(pattern: String, zone: ZoneId): DateTimeFormatter` that keys on pattern **and** zone id (the file uses both `utcZone` and `ZoneId.systemDefault()`) and calls `DateTimeFormatter.ofPattern(pattern, defaultLocale).withZone(zone)` on miss.
2. Replace the ten inline `DateTimeFormatter.ofPattern(...)` call sites with `formatterFor(...)`, preserving each site's existing locale and zone exactly — note lines 96, 99, 206, 208 and 224 currently use `ofPattern` with **no** locale argument, so give those a distinct key or a no-locale overload rather than silently switching them to `defaultLocale`.
3. Keep `formatDate(date, null)` behaving as today (`ofPattern("")` throws and the existing `catch` returns `""`) — the cache must not turn that into a different result.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.utils.TimeUtilsTest"`. User-visible: enterprise report cards, finance rows, health examination rows and the team calendar all render exactly the same date strings as before; scrolling a long finance/report list is smoother.

size budget: ~35 changed lines, 1 file.

out of scope: no change to any date *format string* or to which zone/locale a call site uses — this is a pure caching change. Do not add a caching library; `java.util.concurrent.ConcurrentHashMap` is already on the classpath.

---

### 2. stop materializing JSON numbers as Strings in JsonUtils (roadmap 7, 9)

context: `JsonUtils` is on the hottest path in the app — every field of every document pulled from CouchDB goes through it. `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt:99` reads `if (el is JsonNull || el.asString.isEmpty()) 0 else el.asInt`: for a numeric primitive `asString` allocates a throwaway `String` ("12345") purely to test emptiness, then the value is converted a second time. `getFloat` does the same at line 106. Separately `getJsonElement` (line 121) eagerly allocates a fresh `JsonObject()` or `JsonArray()` as `default` on **every** call even when the field is present, and `getJsonArray` (line 112) calls `.asJsonArray` on a value it has already smart-cast to `JsonArray`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt` — `getInt` (lines 96–101), `getFloat` (lines 103–108), `getJsonElement` (lines 120–123), `getJsonArray` (lines 110–113). Leave `getString`, `getLong`, `getBoolean`, `extractSharedTeamName` and the `gson` lazy alone; do not touch `model/StepExam.kt` or `model/UserEntity.kt` (other tasks own them).

steps:
1. In `getInt`, short-circuit numeric primitives: when `el.isJsonPrimitive && el.asJsonPrimitive.isNumber`, return `el.asInt` directly; otherwise keep today's exact `el.asString.isEmpty()` → `0` fallback so string-encoded and empty-string values behave identically.
2. Apply the same shape to `getFloat`, keeping its existing `else getInt(fieldName, jsonObject).toFloat()` branch for the absent-field case untouched.
3. In `getJsonElement`, move the `default` construction into the not-present branch so no allocation happens on the hit path.
4. In `getJsonArray`, drop the redundant trailing `.asJsonArray` and return the already-typed `array`.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.utils.JsonUtilsTest"`. User-visible: a full sync parses the same data with no behavior change — numeric, string-encoded-numeric, empty-string, null and missing fields must all still map to the values the existing tests assert.

size budget: ~20 changed lines, 1 file.

out of scope: do not change any method signature, do not add a Gson `TypeAdapter`, and do not "fix" the `safeGet` swallow-and-print error handling.

---

### 3. pick the next download URL in one pass instead of sorting the whole queue (roadmap 7)

context: `DownloadService.getNextUrl` sorts the entire pending-URL set, filters it, wraps every survivor in a `QueuedUrl`, then scans that list for the max priority — all to return one element. `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt:570-572` reads `urls.sorted().filter { it !in processedUrls && it.isNotBlank() }.map { QueuedUrl(it, isPriority) }` followed by `getNextPriorityUrl(queue)`. Every `QueuedUrl` built here takes the default `priority = 0` (`DownloadService.kt:166`), so `maxByOrNull { it.priority }` always returns the first element of the sorted list — i.e. the lexicographic minimum. The whole sort and the per-URL wrapper allocations are discarded. This is called twice per file inside the download loop (`DownloadService.kt:139`), making a large resource batch O(n² log n).

files: `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` — only the companion `getNextUrl` (lines 562–573). Leave the companion `getNextPriorityUrl(downloadQueue: List<QueuedUrl>)` (lines 556–559) and the `QueuedUrl` data class (line 166) exactly as they are: `getNextPriorityUrl` is separately tested and is the real priority selector for other callers.

steps:
1. Replace the `sorted().filter { }.map { }` chain with a single filtering pass over `urls` that takes the minimum URL string — `urls.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()`.
2. Return `null` when nothing survives, otherwise wrap that single winner as `QueuedUrl(winner, isPriority)`.
3. Drop the now-unused call to `getNextPriorityUrl(queue)` from `getNextUrl` (the companion function itself stays — line 168 and line 172 still route through it via the instance wrappers, and its own tests remain).
4. Remove any import left unused by the change.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.services.DownloadServiceUrlSelectionTest"` — all six `getNextUrl` cases (empty set, deterministic first URL `file1` before `file2`, skip processed, all processed → null, skip blank, pending-key variant) and all four `getNextPriorityUrl` cases must still pass unmodified. User-visible: downloading a multi-file resource picks files in the same order as today and the queue no longer slows down as it grows.

size budget: ~8 changed lines, 1 file.

out of scope: do not touch `cleanupProcessedUrls`, `getRemainingCount`, the notification/foreground-service logic, or the `SharedPreferences` queue representation. Do not introduce a real priority scheme.

---

### 4. drop the JSON serialize-and-reparse round-trip in UserEntity.addImageUrl (roadmap 7, 9)

context: `UserEntity.addImageUrl` converts a `JsonObject` to a String and parses it straight back into the same structure before reading one key from it. `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt:162` reads `val element = JsonParser.parseString(jsonDoc["_attachments"].asJsonObject.toString())`, then line 163 does `element.asJsonObject` and line 165 iterates `entrySet()` only to `break` after the first entry. Serializing plus reparsing an attachments map is pure waste, and it runs for every user document during sync.

files: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt` — only `addImageUrl` (lines 160–169) and the now-unused `com.google.gson.JsonParser` import. Leave `parseLeadersJson`, `isManager`/`isLeader`/`isGuest` and every other member alone; do not touch `UrlUtils.kt` (an open PR owns it).

steps:
1. Read `jsonDoc["_attachments"].asJsonObject` directly into a local and delete the `toString()`/`parseString` round-trip and the intermediate `element` val.
2. Replace the `for ((key1) in entries) { ...; break }` loop with a first-key read (`keySet().firstOrNull()`), keeping the existing behavior of doing nothing when there are no attachments.
3. Remove the `JsonParser` import if nothing else in the file uses it.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.model.UserEntityTest"` and `--tests "org.ole.planet.myplanet.model.UserEntityEncodeImageTest"`. User-visible: member avatars in the members/leaders lists and the profile screen resolve to the same URLs as before.

size budget: ~8 changed lines, 1 file.

out of scope: do not change `UrlUtils.getUserImageUrl` or the attachment-name selection rule (first key wins, as today), and do not start handling multiple attachments.

---

### 5. mark synced notifications in one transaction instead of one write per row (roadmap 1, 7)

context: `NotificationsRepositoryImpl.markNotificationsSynced` issues one `UPDATE` per notification with no enclosing transaction — `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:325-327` reads `syncResults.forEach { (id, rev) -> notificationDao.markSynced(id, rev) }`, and `markSynced` is a single-row `@Query` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt:47-48`). Outside a transaction each statement is its own SQLite commit, so a sync that acknowledges N notifications pays N fsyncs. The same DAO already demonstrates the batched shape for reads and deletes (`getByIds`, `deleteByIds`, `markAsRead(ids, createdAt)`).

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` (add one batched method next to `markSynced` at line 47) and `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (`markNotificationsSynced`, lines 323–328). Do NOT touch `getPendingSyncNotifications`, `markNotificationsAsRead`, or any other method in either file, and do not touch `AppDatabase.kt` or `RoomModule.kt` — open PRs own those and no new DAO or entity is being registered here.

steps:
1. In `NotificationDao`, add a `@Transaction suspend fun markAllSynced(updates: List<Pair<String, String?>>)` with a default body that loops the existing `markSynced(id, rev)` — Room supports `@Transaction` on an interface method with a default implementation, so no new `@Query` and no schema change is needed.
2. Change `markNotificationsSynced` to keep its `if (syncResults.isEmpty()) return` guard and then make a single `notificationDao.markAllSynced(syncResults)` call.
3. Confirm no `AppDatabase` `version` bump is required (no entity or column changed) and leave it untouched.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest"`. User-visible: after a sync, notifications that were acknowledged by the server stop reappearing as pending exactly as before, and the acknowledge step no longer stalls when many notifications sync at once.

size budget: ~15 changed lines, 2 files.

out of scope: do not convert the `List<Pair<String, String?>>` signature into a new model type, do not add a `rev`-grouped `WHERE id IN (:ids)` query, and do not touch the read/delete paths in the same DAO.

---

### 6. index storage categories by extension instead of scanning them per file (roadmap 7)

context: the storage breakdown sheet does a linear scan over every category for every file it finds on disk. `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt:230` reads `val index = categories.indexOfFirst { it.extensions.isNotEmpty() && ext in it.extensions }`, inside the `oleDir.walkTopDown().filter { it.isFile }.forEach { ... }` loop opened at line 226. `categories` (lines 55–61) holds five entries whose `extensions` are `Set<String>`, so each file costs up to four set probes plus the `indexOfFirst` lambda dispatch — on a device with thousands of downloaded resource files that is thousands of redundant probes for a lookup that is a constant map.

files: `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt` — only `scanStorage()` (lines 217–234), plus one derived lookup val alongside the existing `categories` list. Leave `populateCategoryRows()` (which builds its own `allKnownExtensions` set for the detail sheet), `onCreateDialog`, and the `CategoryData` shape alone. Do not touch `StorageCategoryDetailFragment.kt` or `ResourcesRepositoryImpl.getOfflineResourceItems` — their scans already use direct set lookups.

steps:
1. Add a private lazily-built `Map<String, Int>` from extension to category index, derived from `categories` by flat-mapping each non-empty `extensions` set to its index.
2. In `scanStorage()`, replace the `indexOfFirst { ... }.let { if (it == -1) categories.lastIndex else it }` expression with a single map lookup defaulting to `categories.lastIndex` (the "other" bucket), preserving today's first-match-wins semantics.
3. Leave the `total`, `sizes` and `counts` accumulation exactly as it is.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: open Settings → storage breakdown; the per-category sizes, file counts and total match what the previous build reported for the same device contents, and the scan spinner clears noticeably faster on a device with many downloads.

size budget: ~12 changed lines, 1 file.

out of scope: do not change the category list, the extension sets, or the `walkTopDown` traversal itself; do not move the scan into a repository or a worker.

---

### 7. classify notification types once per load instead of four times (roadmap 3, 7)

context: `NotificationsViewModel.loadNotifications` walks the full notification list four separate times, allocating a fresh lowercased `String` per notification on each pass: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:63`, `:69`, `:78` and `:84` each read `it.type.lowercase()`. Separately, `buildGroupedList` reallocates its ordering list on every emission — line 220 reads `val typeOrder = listOf("join_request", "team_join", "task", "chat", "voice_reply", "resource", "storage")` — and that function is re-run by the `combine` at lines 48–52 on every selection toggle and every group expand/collapse, not just on load.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` — `loadNotifications` (lines 56–94) and the `typeOrder` local in `buildGroupedList` (line 220), promoted to a `companion object` val next to the existing `KNOWN_TYPES` (line 290). Leave `formatNotification`, `resolveType`, `typeLabelFor`, `parseTaskDate` and the selection/read mutators alone; do not touch `NotificationsRepositoryImpl.kt` (task 5 owns it) or `NotificationsAdapter.kt`.

steps:
1. In `loadNotifications`, compute each notification's lowercased type once — e.g. bind `payloadNotifications` to a list of notification-plus-lowered-type pairs, or group it once by lowered type — and derive the four existing collections (`taskIds`, `taskTitles`, `joinRequestIds`, `joinRequestsWithoutRelatedId`) from that single classification.
2. Keep every downstream call and its ordering identical: `getTaskTeamNamesByTaskIds`, then the `taskTitles` fallback via `getTaskTeamNamesByTaskTitles`, then `getJoinRequestDetailsBatch`, then the `getJoinRequestDetails(null)` fallback keyed at `""`, then `_notifications.value` and `_unreadCount.value`.
3. Move `typeOrder` into the existing `companion object` as a val and reference it from `buildGroupedList`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest"`. User-visible: the notifications screen shows the same groups in the same order (join requests, team joins, tasks, chat, voice replies, resources, storage, then other), the same team names on task rows, the same requester/team names on join-request rows, and the same unread counts.

size budget: ~30 changed lines, 1 file.

out of scope: do not change the grouping rules, the type-ordering, `resolveType`'s message-sniffing heuristics, or the repository interface. Do not restructure `groupedItems` into a different flow shape.

---

### 8. fold the login-activity chart aggregation into a single pass (roadmap 7)

context: `ActivitiesFragment.computeMonthlyCounts` builds three throwaway intermediate lists before it groups anything. `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt:64-69` chains `.mapNotNull { it.loginTime }` → `.filter { it in startMillis..endMillis }` → `.map { ... calendar.get(Calendar.MONTH) }` before `groupingBy`/`eachCount`. It is fed by `activitiesRepository.getOfflineLogins(userName)`, whose whole history is loaded and then narrowed to the last year (`ActivitiesFragment.kt:44-45`), so on a long-lived install these copies scale with every login the device has ever recorded — allocated on the dashboard's activity tab and re-allocated on every flow emission.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt` — only `computeMonthlyCounts` (lines 58–73). Leave `onViewCreated`'s window calculation (lines 44–45), `renderChart`, and the `collectLatestWhenStarted` wiring alone. Do NOT touch `ActivitiesRepository.kt`/`ActivitiesRepositoryImpl.kt` — open PRs own them, so the date filter stays client-side for now.

steps:
1. Convert the chain to a single pass — either `asSequence()` over `logins` before the existing `mapNotNull`/`filter`/`map` steps, or a `fold` into a mutable count map — so no intermediate `List` is materialized.
2. Keep the shared `Calendar` instance and the `calendar.timeInMillis = loginTime` / `Calendar.MONTH` extraction exactly as they are, and keep returning a sorted `Map<Int, Int>` so `renderChart` still gets ascending months.
3. Preserve the inclusive `startMillis..endMillis` bound and the null-`loginTime` skip.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.ui.dashboard.ActivitiesFragmentTest"`. User-visible: the dashboard login-activity bar chart shows the same bars, in the same month order, with the same counts, and the empty state still appears when there are no logins in the window.

size budget: ~12 changed lines, 1 file.

out of scope: do not push the date filter into the DAO or repository (open PRs own those files), do not change the one-year window, and do not touch the chart styling.

---

### 9. hoist the ServerConfigUtils pin map and local-network regex out of the call path (roadmap 7, 9)

context: `ServerConfigUtils.getPinForUrl` rebuilds an eleven-entry map of compile-time `BuildConfig` constants on every invocation — `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt:52-64` builds `val pinMap = mapOf(...)` inside the function body, then line 65 does a single lookup and throws the map away. In the same file `isLocalNetwork` compiles a regex per call: line 72 reads `host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))`. Both are reached from the server-selection dialog (`ServerDialogExtensions.kt:186` and `:187`) on every spinner selection, and both are pure functions of constants, so the work is entirely repeated. The file already has no `android.*` import, so this also keeps a genuinely platform-free util clean.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt` — `getPinForUrl` (lines 51–66) and `isLocalNetwork` (lines 68–76). Leave `getServerAddresses` alone (it needs a `Context` for its strings, so it cannot be hoisted), and leave `getFilteredList`, `saveAlternativeUrl`, `getTrustedServerHosts` and `getChallengeServerUrls` untouched. Do not touch `ServerDialogExtensions.kt`.

steps:
1. Promote the `pinMap` contents to a private object-level val (a `by lazy` map, matching the hoisting style used in `TTSManager`/`Utilities`) and reduce `getPinForUrl` to the lookup plus its `?: ""` fallback.
2. Promote the `^172\.(1[6-9]|2[0-9]|3[0-1])\..*` pattern to a private object-level `Regex` val and reference it from `isLocalNetwork`.
3. Leave every other predicate in `isLocalNetwork` (`192.168.`, `10.`, `localhost`, `127.0.0.1`, `.local`) and the `getDefaultProtocol` URL comparisons unchanged.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.utils.ServerConfigUtilsTest"` — the eleven known-URL pin assertions plus the unknown-URL and empty-URL cases must still pass. User-visible: picking a server in the sync/login server dialog prefills the same PIN and selects the same http/https protocol as before.

size budget: ~20 changed lines, 1 file.

out of scope: do not change any PIN or URL value, do not address the committed-secrets issue flagged in `CLAUDE.md` (that needs server-side rotation, not a code move), and do not alter the local-network detection rules.

---

### 10. replace android.text.TextUtils with Kotlin checks in four entity models (roadmap 1, 9)

context: four Room entities depend on the Android framework purely for a null-or-empty string test, which pins them to an Android target for no benefit and forces model-level unit tests through Robolectric. In each file `android.text.TextUtils` is the **only** `android.*` import, so removing it makes the model platform-free: `model/StepExam.kt:3` with uses at lines 40, 55, 63, 66; `model/Answer.kt:3` with uses at lines 48, 55; `model/HealthExamination.kt:3` with uses at lines 38, 80, 81; `model/TeamTask.kt:3` with a use at line 59. `TextUtils.isEmpty(s)` is exactly `s.isNullOrEmpty()` for a nullable `CharSequence`, so the swap is behavior-identical.

files: `app/src/main/java/org/ole/planet/myplanet/model/StepExam.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Answer.kt`, `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt`, `app/src/main/java/org/ole/planet/myplanet/model/TeamTask.kt`. Do NOT touch `model/News.kt`, `model/Feedback.kt` or `model/Achievement.kt` in this task — `News.kt` and `Feedback.kt` are the same clean case and are deliberately left for a later round, and `Achievement.kt` also imports `android.util.LruCache` and `android.widget.EditText` so it would not become platform-free anyway. Do not touch `model/MyLibrary.kt`, `model/Course.kt`, `model/Meetup.kt` or `model/MeetupCreationParams.kt` — open PRs own them.

steps:
1. In each of the four files, rewrite every `TextUtils.isEmpty(x)` as `x.isNullOrEmpty()` and every `!TextUtils.isEmpty(x)` as `!x.isNullOrEmpty()`, keeping the surrounding expression shape intact — including `StepExam.kt:40`'s `(if (...) parentId else examId).orEmpty()` and `StepExam.kt:55`'s `isFromNation` assignment.
2. Delete the now-unused `import android.text.TextUtils` line from all four files.
3. Confirm no other `android.*` import remains in the four files, so each is left with only `androidx.room` annotations and Gson/JSON imports.
4. Change nothing else — no property renames, no `@Entity` or `@Index` edits, and therefore no `AppDatabase` `version` bump.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular `--tests "org.ole.planet.myplanet.model.StepExamTest"`, `--tests "org.ole.planet.myplanet.model.StepExamBenchmarkTest"` and `--tests "org.ole.planet.myplanet.model.TeamTaskTest"`. Also `grep -L "import android\." ` over the four files should list all four. User-visible: taking an exam or survey, submitting answers, recording a health examination and creating/serializing a team task all behave identically, and a sync round-trips the same JSON payloads.

size budget: ~20 changed lines, 4 files.

out of scope: no schema change, no `version` bump in `AppDatabase.kt` (an open PR owns that file), no removal of `androidx.room` annotations, and no migration of the remaining `android.*`-importing models (`MyPlanet.kt`, `Download.kt`, `Personal.kt`, `MyLife.kt`, `MyCourse.kt`, `NotificationListItem.kt`).

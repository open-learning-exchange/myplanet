# Merged refactor backlog (Devin × Opus A, ordered by cross-plan consensus)

Assembled mechanically from two of the five merged plans, with no new code reading:

- **Body, rating, files and line references:** Devin's backlog (`devin/refactor-tasks-benchmark-merged`, `docs/refactor_tasks.md`), the plan with the most verified line references and the best record on false premises.
- **Risk and sequencing lines:** Opus A (`claude/dazzling-gauss-736kap`, `refactor_tasks.md`), the only plan that consistently flags cross-task conflicts and PR-contested files. Its `verified` and `risk` lines are appended to each task; its `do` line is included when it is the only Opus A source for the task.
- **Order and tiers:** the cross-plan vote in `docs/refactor_plan_vote.md`. `votes` is how many of the five plans kept the raw task; `consensus avg` is the mean of the five plans' ratings; `spread` is max minus min. Tasks kept by two plans or fewer are listed at the end as dropped. Tasks with a spread of 25 or more are marked ⚠ disputed and carry a one-line reading.
- **Corrections applied** to the source text: `showDownload` is dead code and should be deleted, not relocated; the Feedback stale-cache claim is false and only the memoization half stands.

Numbering is a suggested working order. Within a tier, prefer finishing one file's tasks together (see the collision table) over strict numeric order.

## Files touched by more than one task

Land these together or sequence them explicitly; the numbers are task numbers below.

| File | Tasks |
|---|---|
| `ResourcesRepositoryImpl.kt` | 8, 10, 32, 56, 62 |
| `NotificationsRepositoryImpl.kt` | 1, 11, 71, 78 |
| `ResourcesRepository.kt` | 8, 10, 32 |
| `MainApplication.kt` | 57, 68, 92 |
| `SyncTimeLogger.kt` | 2, 36 |
| `AutoSyncWorker.kt` | 4, 23 |
| `SyncActivity.kt` | 4, 23 |
| `WebViewActivity.kt` | 6, 55 |
| `ActivitiesRepositoryImpl.kt` | 7, 26 |
| `StorageBreakdownViewModel.kt` | 8, 100 |
| `NotificationsRepository.kt` | 11, 78 |
| `ProgressViewModel.kt` | 15, 75 |
| `FeedbackListViewModel.kt` | 16, 18 |
| `UploadToShelfService.kt` | 17, 48 |
| `AchievementUploader.kt` | 19, 38 |
| `UserRepository.kt` | 19, 38 |
| `ChatRepositoryImpl.kt` | 20, 41 |
| `SurveysViewModel.kt` | 25, 52 |
| `SubmissionsRepositoryExporter.kt` | 28, 60 |
| `SubmissionsRepositoryImpl.kt` | 28, 31 |
| `HealthExaminationActivity.kt` | 29, 65 |
| `CoursesRepositoryImpl.kt` | 33, 47 |
| `UserRepositoryImpl.kt` | 38, 43 |
| `CoursesAdapter.kt` | 45, 87 |
| `CalendarViewModel.kt` | 49, 64 |
| `LifeCache.kt` | 58, 102 |
| `DownloadService.kt` | 59, 83 |
| `TeamDetailFragment.kt` | 68, 92 |
| `NotificationsViewModel.kt` | 78, 80 |
| `FileUtils.kt` | 94, 99 |

## Tier 1 — unanimous, consensus average 73 or higher (19 tasks)

Every plan kept these and rated them well. The first two are user-visible bugs.

### 1. Compute the task badge per team instead of once globally
votes 5/5 · consensus avg 87 · spread 23 · Devin 90 · Opus A 96 · raw: devin r2 #2
files: repository/NotificationsRepositoryImpl.kt (getTeamNotifications, ~L396-403)

Real correctness bug: `hasTask = tasks.isNotEmpty()` is computed once before the per-team loop, so every `TeamNotificationInfo` reports the same boolean — a team with no tasks shows the badge when any other team has one. Build `tasks.mapNotNull { it.teamId }.toSet()` once, then `hasTask = teamId in taskTeamIds` in the loop. ~6 lines, 1 file.
- **Opus A verified:** `val hasTask = tasks.isNotEmpty()` sits *outside* the `for (teamId in teamIds)` loop and the same boolean is passed into every `TeamNotificationInfo(hasTask, hasChat)`. Confirmed `TeamTask.teamId` exists and is indexed (`model/TeamTask.kt:22`, `@Index("teamId")`), so the grouping fix needs no DAO change.
- **Opus A do:** build `val taskTeamIds = tasks.mapNotNull { it.teamId }.toSet()` once, then `val hasTask = teamId in taskTeamIds` inside the loop.
- **Opus A risk:** ~6 lines, one file. This is a live user-visible defect: any team shows the task badge whenever *any* team has a task due.

### 2. Make SyncTimeLogger's per-key lists thread-safe, drop the write-only detailed-log accumulator, and stop splitting per call
votes 5/5 · consensus avg 86 · spread 7 · Devin 88 · Opus A 89 · raw: claude r1 #1, devin r1 #8
files: utils/SyncTimeLogger.kt (maps L36-38, getOrPut sites L167/184/195, logDetail L192-201, extractProcessName L331-344)

Two findings, same file: (a) `detailedLogs` accumulates strings at 12 call sites but `generateSummary()` never reads them — delete the field, its clear(), and the map append (keep the verbose `Log.d` branch). (b) `getOrPut { mutableListOf() }.add(...)` on a ConcurrentHashMap holding unsynchronized ArrayLists races under parallel sync coroutines — use `computeIfAbsent` + `Collections.synchronizedList` at all three append sites, and wrap generateSummary's iterations in `synchronized(list)`. Also replace `endpoint.split("/")` with `substringAfterLast('/')`. ~25 lines, 1 file.
- **Opus A verified:** `detailedLogs` appears exactly 3× in the file — declared (38), cleared (73), appended (195) — and is never read, so all 12 `logDetail` call sites retain strings nothing consumes. `getOrPut(key) { mutableListOf() }` at 167/184/195 is non-atomic on a `ConcurrentHashMap` and yields an unsynchronised `ArrayList`, appended from parallel sync coroutines. `generateSummary` sums each group for `sortedByDescending` (268, 288) and again while rendering.
- **Opus A do:** delete `detailedLogs` and its clear/append; back the two remaining maps with `Collections.synchronizedList` via `computeIfAbsent`; compute per-endpoint and per-model aggregates once and render from them.
- **Opus A risk:** three concerns in one file — they conflict if split across PRs, so land together. Keep `logDetail`'s signature (12 callers). `extractProcessName`'s `split("/")` can also be cheapened, but `substringAfterLast` is **not** equivalent — the current code walks backwards skipping empty and `?`-prefixed segments.

### 3. Answer "already shared?" with a SQL EXISTS instead of loading every matching news row
votes 5/5 · consensus avg 83 · spread 10 · Devin 84 · Opus A 87 · raw: claude r2 #9
files: data/room/dao/NewsDao.kt (replace getByNewsId L71-72), repository/VoicesRepositoryImpl.kt (isAlreadyShared L76-79), test

`isAlreadyShared` runs `SELECT *` on all matching news rows (full message/images/viewIn blobs through Converters) just to produce a Boolean. Replace `getByNewsId` (its only caller) with `SELECT EXISTS(... viewIn LIKE :pattern ESCAPE '\')`, escaping `\`, `%`, `_` in the pattern — no schema/index change. ~35 lines, 3 files.
- **Opus A verified:** `NewsDao:71-72` is `SELECT * FROM news WHERE newsId = :chatId`; `VoicesRepositoryImpl:76-79` hydrates every row (message, images, viewIn blobs through `Converters`) just to run `.any { ... }`. Confirmed `getByNewsId` has no other caller in `app/src/main`, so it can be replaced outright.
- **Opus A do:** replace with `SELECT EXISTS(SELECT 1 FROM news WHERE newsId = :chatId AND viewIn LIKE :viewInPattern ESCAPE '\')`; build the pattern in the repository, escaping `\`, `%` and `_`.
- **Opus A risk:** SQLite `LIKE` is ASCII-case-insensitive, matching the current `ignoreCase = true`. No schema/index change, so no `AppDatabase` version bump.

### 4. Drop the SharedPrefManager parameter from ConfigurationsRepository.checkVersion
votes 5/5 · consensus avg 81 · spread 15 · Devin 84 · Opus A 86 · raw: claude r2 #1
files: repository/ConfigurationsRepository.kt (L15), repository/ConfigurationsRepositoryImpl.kt (L98 + helpers L487/495), ui/sync/SyncActivity.kt (L233), ui/sync/LoginActivity.kt (L183), services/AutoSyncWorker.kt (L69), test file

The impl already injects `sharedPrefManager` (L49) and uses it in the same function — the parameter is redundant and pins a `services` import into the interface. Remove it from the signature and the three call sites; update three test calls. ~20 lines across 6 files.
- **Opus A verified:** interface line 15 is `fun checkVersion(callback: CheckVersionCallback, spm: SharedPrefManager)`; the impl already injects its own `SharedPrefManager` and still receives `spm`, using it at line 487. Three call sites confirmed: `SyncActivity:233`, `LoginActivity:183`, `AutoSyncWorker:69`.
- **Opus A do:** drop the parameter from the interface and impl, use the injected field throughout, update the three call sites and the three test call sites.
- **Opus A risk:** low and mechanical; removes a `services` import from a repository interface. Two `SharedPrefManager` instances can currently reach one call.

### 5. Hoist the per-attachment SharedPreferences read and dedupe attachments in MyLibrary.insertMyLibrary ⚠ disputed
votes 5/5 · consensus avg 81 · spread 28 · Devin 90 · Opus A 92 · raw: devin r1 #1
files: model/MyLibrary.kt (insertMyLibrary, ~L184-270)

`attachmentList.add(realmAttachment)` is unconditional, so the attachments array grows duplicates on every re-sync of the same document; `params.spm.getCouchdbUrl()` is also re-read per attachment inside `entrySet().forEach`. Resolve the base URL once before the loop; skip `add` when the key is already in the existing attachment-name set. ~15 lines, 1 file.
- **Opus A verified:** line 210 seeds `attachmentList` from `this.attachments?.toMutableList()`, line 223 appends unconditionally, line 234 writes it back — so re-syncing the same document grows the array without bound. Separately `params.spm.getCouchdbUrl()` (line 226) is re-read from SharedPreferences per attachment.
- **Opus A do:** hoist the base URL out of the `entrySet().forEach`; capture existing attachment names into a set and skip already-present keys before `add`.
- **Opus A risk:** sync-path model code — verify attachment identity is keyed on `name`, not the random UUID. Unbounded local DB growth makes this worth the care.

### 6. Take UserRepository out of the viewer activities and put it behind ResourceViewerViewModel
votes 5/5 · consensus avg 80 · spread 20 · Devin 82 · Opus A 85 · raw: claude r2 #2, devin r2 #8
files: ui/viewer/ResourcesExitCoordinator.kt, ui/viewer/ResourceViewerViewModel.kt, ui/viewer/ResourceViewerActivity.kt, ui/viewer/WebViewActivity.kt, test

Both activities field-inject `UserRepository` only to pass to `ResourcesExitCoordinator`, which calls `getUserModel()?.id?.takeIf { it.isNotBlank() }`. Move the lookup into the ViewModel (either a `getActiveUserId()` accessor, or fold it into `shouldShowResourceRatingDialog`/`setRatingPrompted` so they take only `resourceId`), drop the coordinator's `userRepository` param and both activities' injections. ~35 lines, 4-5 files.
- **Opus A verified:** both Activities field-inject `UserRepository` solely to hand it to `ResourcesExitCoordinator`, whose only use is `userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }`. Both already hold a `ResourceViewerViewModel`.
- **Opus A do:** move the lookup into the ViewModel. Claude's variant adds a `getActiveUserId()` accessor; devin's goes further and drops `userId` from `shouldShowResourceRatingDialog` / `setRatingPrompted` entirely — prefer devin's, it removes the parameter from the seam rather than relocating it.
- **Opus A risk:** low. Removes the last repository dependency from both Activities.

### 7. Delete the four unused resource-open overloads from ActivitiesRepository
votes 5/5 · consensus avg 79 · spread 12 · Devin 82 · Opus A 83 · raw: claude r2 #4
files: repository/ActivitiesRepository.kt (L35-38), repository/ActivitiesRepositoryImpl.kt (L174-193)

`getResourceOpenCount(userName)`, `getResourceOpenCount(userName,type)`, `getMostOpenedResource(userName)`, `getMostOpenedResource(userName,type)` have no callers outside the impl (verified — only internal use in `getProfileActivityStats`). Delete the interface declarations, drop `override` in the impl. ~8 lines, 2 files.
- **Opus A verified:** the four declarations at lines 35-38 resolve only to `ActivitiesRepositoryImpl:196` and `:198`, both inside `getProfileActivityStats`. No caller outside the impl anywhere in `app/src/main`.
- **Opus A do:** delete the four from the interface; drop `override` in the impl so they stay callable as plain members.
- **Opus A risk:** very low — the test constructs the concrete class, so no test edit needed. Also lifts `UserSessionManager.KEY_RESOURCE_OPEN` out of the contract.

### 8. Move the storage-breakdown disk scan out of the ViewModel into ResourcesRepository
votes 5/5 · consensus avg 79 · spread 21 · Devin 80 · Opus A 81 · raw: claude r2 #3
files: repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/settings/StorageBreakdownViewModel.kt, two test files

`StorageBreakdownViewModel.scanStorage` walks the whole `ole` tree — raw file I/O in a ViewModel that duplicates the identical `walkTopDown().filter { it.isFile }` in `getOfflineResourceItems` (ResourcesRepositoryImpl L860). Add `getStorageBreakdown(...)` to the repository (lift the body verbatim into `withContext(dispatcherProvider.io)`), delete `scanStorage`/`ScanResult` from the VM, move the fixture-tree tests. Alternative: kimi's micro-fix keeps it in the VM (see task at rating 55) — pick one.
- **Opus A verified:** `StorageBreakdownViewModel:94` runs `oleDir.walkTopDown().filter { it.isFile }.forEach { … }`, duplicating the identical traversal already inside `ResourcesRepositoryImpl.getOfflineResourceItems` (~860). The settings screen therefore walks the tree once for the summary and again when a category opens, and this ViewModel is the only one in `ui/` still doing its own file I/O.
- **Opus A do:** prefer claude's variant — move the scan into `ResourcesRepository` behind a `getStorageBreakdown(...)` returning a small data class, and have the ViewModel call it. Kimi's variant only removes the intermediate sequence stages inside the ViewModel, which leaves the layering violation and the double walk in place.
- **Opus A risk:** moderate (~120 lines, 5 files including moved tests). Keep the caller passing the `ole` path, as `getOfflineResourceItems` already does.

### 9. Drop redundant case-insensitive matching from chat search (plus bucket-concat and query-parts tidy-ups)
votes 5/5 · consensus avg 77 · spread 13 · Devin 78 · Opus A 83 · raw: claude r1 #3, codex r1 #5, grok r2 #5
files: utils/ChatSearch.kt (fullConvoSearch L60/63, searchByTitle L97/99), test

`Utilities.normalizeText` already lowercases both operands (query and candidates), yet all four comparisons pass `ignoreCase = true`, forcing the case-folding regionMatches path on every keystroke. Remove the four `ignoreCase` flags (both sides pre-normalized). Also collapse the four ranked `+` concatenations into one ordered accumulation and skip the intermediate `queryParts` list — keep result ordering identical (title-start → title-contains → body-start → body-contains). ~10 lines, 1 file + tests.
- **Opus A verified:** `Utilities.normalizeText` lowercases via `str.lowercase(Locale.getDefault())` before stripping diacritics, and both query and candidates pass through it (lines 42, 49-50, 81-83, 90-91). All four comparisons still pass `ignoreCase = true` (60, 63, 97, 99), forcing the case-folding `regionMatches` path on every keystroke.
- **Opus A do:** drop `ignoreCase = true` from the two `startsWith` and two `contains` calls.
- **Opus A risk:** low. Grok's variant additionally replaces the four `+`-concatenated rank buckets with a single accumulation and drops the duplicate `queryParts` list — worth folding in; claude's pins the exact lines; codex's adds the mixed-case/accented regression cases.

### 10. Load only id+title for the achievement resource picker
votes 5/5 · consensus avg 77 · spread 19 · Devin 82 · Opus A 84 · raw: devin r2 #1
files: data/room/dao/MyLibraryDao.kt, repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/user/AchievementViewModel.kt, ui/user/EditAchievementFragment.kt

`showResourceDialog` materializes every `my_library` row as a full ~74-property `MyLibrary` entity to show a checkbox list that reads only `title`; `serializeResource()` is needed only for confirmed rows. Add an `IdTitleProjection` query (no ORDER BY), replace `getAllLibraries` with the projection accessor, refetch selected entities via the existing `getLibraryItemsByIds`. ~60 lines, 5 files.
- **Opus A verified:** `EditAchievementFragment:413` → `AchievementViewModel:85` → `resourcesRepository.getAllLibraries()`, an unbounded `SELECT *` over ~74-property entities to populate a checkbox list that reads only `title`. Confirmed this is `getAllLibraries`'s **only** consumer (distinct from `getAllLibrariesToSync`, used by `MainApplication:396` and `SyncActivity:575`), so it can be replaced rather than added to.
- **Opus A do:** add an `id,title` projection query beside the existing `ResourceTitleProjection`; refetch full entities only for the ids the user confirms.
- **Opus A risk:** preserve the current unspecified row order (no `ORDER BY`) so the list looks identical.

### 11. Remove the dead getNotifications from NotificationsRepository and route timestamps through the injected TimeProvider
votes 5/5 · consensus avg 76 · spread 10 · Devin 78 · Opus A 76 · raw: claude r2 #8
files: repository/NotificationsRepository.kt (L22), repository/NotificationsRepositoryImpl.kt (L140 + Date() at L107/115/128/136/461), test

`getNotifications` on the interface is called only by `getEnrichedNotifications` (L166) — make it private. Separately, five `Date()` constructions bypass the injected `timeProvider`; replace with `Date(timeProvider.now())`. ~15 lines, 3 files.
- **Opus A verified:** `getNotifications` (interface line 22) resolves only to `NotificationsRepositoryImpl:166` inside `getEnrichedNotifications`. The impl injects `timeProvider` yet constructs `java.util.Date()` directly at 107, 115, 128, 136 and 461.
- **Opus A do:** make the method private and replace each `Date()` with `Date(timeProvider.now())`.
- **Opus A risk:** low; existing tests mock the DAO, not the repository method. Conflicts with the two other tasks in this file — sequence them.

### 12. Delete the getRatingsById wrapper from RatingsRepository
votes 5/5 · consensus avg 76 · spread 15 · Devin 77 · Opus A 81 · raw: claude r2 #10
files: repository/RatingsRepository.kt (L7), repository/RatingsRepositoryImpl.kt (L20-23), ui/resources/ResourceDetailFragment.kt (onRatingChanged), test

`getRatingsById` is a null-guard over `getRatingSummary` on the next line — two entry points for one query, with exactly one app caller (ResourceDetailFragment L268; four other call sites already use `getRatingSummary`). Delete it; guard the id at the call site. ~20 lines, 4 files.
- **Opus A verified:** the impl (20-23) is nothing but `if (resourceId == null) return null` over `getRatingSummary` on the next line. Confirmed exactly one caller — `ResourceDetailFragment:268`; four other sites already call `getRatingSummary` directly.
- **Opus A do:** delete both, guard the id at the call site with `val resourceId = library.resourceId ?: return@launch`.
- **Opus A risk:** low; rename the existing test case to target `getRatingSummary`.

### 13. Stop re-filtering the resource id list during sync cleanup ⚠ disputed
votes 5/5 · consensus avg 76 · spread 28 · Devin 84 · Opus A 88 · raw: claude r1 #2
files: services/sync/SyncManager.kt (resourceTransactionSync, L294/376/411-420)

`newIds` is declared `MutableList<String?>` but only ever receives `savedIds.filter { it.isNotBlank() }`, so `validNewIds` re-walks the whole list and `validNewIds.size == newIds.size` is always true. Type `newIds` as `MutableList<String>`, delete the re-filter, pass `newIds` to `removeDeletedResources`, reduce the guard to `newIds.isNotEmpty()`, fix the logged item count. ~8 lines, 1 file.
- **Opus A verified:** `newIds` is declared `MutableList<String?>` (294) but only ever receives `validIds` (376), which is already `savedIds.filter { it.isNotBlank() }`. The cleanup block re-walks the whole list at 411, and its guard `validNewIds.size == newIds.size` (414) is therefore always true. The item count logged at 420 is `newIds.size - validNewIds.size` — always 0.
- **Opus A do:** type `newIds` as `MutableList<String>`, drop `validNewIds`, reduce the guard to `newIds.isNotEmpty()`, and log `newIds.size`.
- **Opus A risk:** low; keep the `hadBatchFailure` branch untouched. Saves a full copy of every resource id on servers holding tens of thousands.

### 14. Make challenge progress formatting independent of the dashboard ViewModel
votes 5/5 · consensus avg 75 · spread 12 · Devin 74 · Opus A 80 · raw: codex r2 #1
files: services/ChallengePrompter.kt (L41-42/55-56), ui/dashboard/DashboardViewModel.kt (L155-162)

A service helper reaches into `DashboardViewModel.calculateCommunityProgress`/`calculateIndividualProgress` — a service→UI dependency inversion. Extract the two deterministic calculations into a platform-free function; have both the prompter and the VM call it. ~45 lines, 2 files.
- **Opus A verified:** `ChallengePrompter` imports `DashboardViewModel` (line 8), holds one (13), and calls `calculateCommunityProgress` / `calculateIndividualProgress` at 41-42 and again at 55-56 — a service depending on a UI ViewModel, inverting the layer direction.
- **Opus A do:** extract the two deterministic calculations into a platform-free function/value object and have both the prompter and the ViewModel call it.
- **Opus A risk:** moderate; preserve the Spanish completion check, the caps and the markdown copy exactly.

### 15. Return a typed progress projection from ProgressRepository instead of parsing JsonArray in the ViewModel
votes 5/5 · consensus avg 75 · spread 20 · Devin 77 · Opus A 74 · raw: claude r2 #7
files: repository/ProgressRepository.kt, repository/ProgressRepositoryImpl.kt, ui/courses/ProgressViewModel.kt, test

`fetchCourseData` returns raw `JsonArray` and `ProgressViewModel.loadCourseData` does the CouchDB-doc mapping itself (`asJsonObject`, `getAsJsonObject("progress")`, a `TypeToken` Gson parse). Add `getCourseProgressRows(userId): List<CoursesProgressRow>` to the repository (target type already exists in model/), moving the mapping down unchanged except skipping malformed rows instead of throwing; delete `gson`/`type` from the VM. Keep `fetchCourseData` (DashboardViewModel still uses it). Supersedes the smaller fix at rating 66.
- **Opus A verified:** `fetchCourseData` returns a raw `JsonArray` and `ProgressViewModel` maps it itself: `obj.has("stepMistake")` followed by `obj.get("stepMistake")` (two lookups for one value), and `obj.getAsJsonObject("progress")` resolved twice for `current` and `max`. `obj.get("courseId").asString` throws on a malformed document. Target type `CoursesProgressRow` already exists.
- **Opus A do:** add `getCourseProgressRows(userId): List<CoursesProgressRow>` to the repository and move the mapping there, skipping rows with a missing id rather than throwing. This supersedes the narrower "read each field once" version of the same task — the field-read fix comes free with the move.
- **Opus A risk:** moderate. Leave `fetchCourseData` itself alone: `DashboardViewModel:326` consumes its `JsonArray` for a different purpose.

### 16. Tighten FeedbackRepository: drop the two internal-only builders and stop passing UserEntity into the list query
votes 5/5 · consensus avg 75 · spread 16 · Devin 76 · Opus A 78 · raw: claude r2 #5
files: repository/FeedbackRepository.kt (L8/24/29), repository/FeedbackRepositoryImpl.kt, ui/feedback/FeedbackListViewModel.kt, two test files

`createFeedback` and `saveFeedback` are public interface methods whose only callers are each other inside `createAndSaveFeedback` — make them private and drop from the interface. `getFeedback` is `suspend` without suspending and takes a whole `UserEntity` to read two fields — change to `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>`. ~45 lines, 5 files.
- **Opus A verified:** `createFeedback` (line 8) and `saveFeedback` (29) are interface methods whose only callers are each other inside `createAndSaveFeedback`. `getFeedback(userModel: UserEntity?)` (24) is marked `suspend` though the body only picks a DAO `Flow`, and takes a whole `UserEntity` to read two things.
- **Opus A do:** make the two builders private, and change the signature to `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>` without `suspend`.
- **Opus A risk:** moderate — two test files move from `coEvery` to `every`.

### 17. Move the health-examination upload sequence out of UploadToShelfService into HealthRepository
votes 5/5 · consensus avg 75 · spread 11 · Devin 75 · Opus A 75 · raw: claude r2 #6
files: repository/HealthRepository.kt (L14-19), repository/HealthRepositoryImpl.kt, services/UploadToShelfService.kt (L70-93), test

Both `uploadHealth` and `uploadSingleUserHealth` hand-roll the same fetch→upload→mark sequence across `HealthRepository`, forcing three intermediate methods to stay public. Add `syncPendingHealthExaminations()` / `syncPendingHealthExaminationsForUser(userId)` to the repository, remove the three intermediates from the interface, reduce the service bodies to one call each (keep listener wrappers + signatures). ~60 lines, 4 files.
- **Opus A verified:** `UploadToShelfService` lines 70-77 and 78-93 each run the same fetch → `uploadHealthData` → `markHealthExaminationsUploaded` sequence, differing only in the fetch. Three methods stay public on the interface purely to let a service orchestrate them.
- **Opus A do:** add `syncPendingHealthExaminations()` / `…ForUser(userId)` to the repository; reduce each service body to one call; drop the three intermediates from the interface.
- **Opus A risk:** moderate — `uploadHealth()` / `uploadSingleUserHealth(...)` must keep their public signatures for `AutoSyncWorker`, `UserDataWorker` and `ProcessUserDataActivity`.

### 18. Let the feedback repository flow be the only list refresh source
votes 5/5 · consensus avg 74 · spread 18 · Devin 75 · Opus A 68 · raw: codex r2 #5
files: ui/feedback/FeedbackListFragment.kt, ui/feedback/FeedbackListViewModel.kt

`FeedbackListViewModel.loadFeedback` already continuously collects `FeedbackRepository.getFeedback`, yet the fragment injects `RealtimeSyncManager`, implements `RealtimeSyncMixin`, and restarts the collector on `"feedback"` table events — redundant work + sync-internals coupling. Remove the mixin/manager/helper; keep the single VM collection; retain `refreshFeedback` only for the explicit submission callback. ~35 lines, 2 files.
- **Opus A verified:** `FeedbackListViewModel.loadFeedback` already collects `FeedbackRepository.getFeedback` continuously, yet the fragment injects `RealtimeSyncManager`, implements `RealtimeSyncMixin` and restarts that collector on `"feedback"` events.
- **Opus A do:** delete the mixin and injection; keep the ViewModel's single repository-flow collection alive for its lifetime.
- **Opus A risk:** low-moderate; ensure `refreshFeedback` cannot create parallel collectors. One of five fragments doing this — see the sibling tasks.

### 19. Carve the achievement surface out of UserRepository into a narrow interface
votes 5/5 · consensus avg 74 · spread 14 · Devin 78 · Opus A 70 · raw: devin r2 #7
files: NEW repository/UserAchievementsRepository.kt, repository/UserRepository.kt, NEW di/UserAchievementsRepositoryModule.kt, services/upload/AchievementUploader.kt

`UserRepository` declares ~57 members; `AchievementUploader` uses only `getAchievementsForUpload` + `markAchievementUploaded`. Apply the existing `TeamsFinancesRepository`-style split: new interface holding the six achievement members, `UserRepository : UserAchievementsRepository`, a `@Provides` binding returning the `UserRepository` impl, and a constructor-type swap in the uploader. Do NOT touch UserRepositoryImpl/RepositoryModule (PR-owned). ~80 lines, 4 files (2 new).
- **Opus A verified:** `UserRepository.kt` declares exactly 45 functions; `AchievementUploader` uses two of them. The interface-segregation precedent already exists (`TeamsFinancesRepository` et al).
- **Opus A do:** extract the achievement block into a parent interface that `UserRepository` extends; provide it from `UserRepository` in a new module; narrow the uploader's injected type.
- **Opus A risk:** needs a new Hilt module because `RepositoryModule` is PR-owned — verify Hilt resolves it before relying on `assembleDefaultDebug`.

## Tier 2 — unanimous, consensus average 60 to 72 (59 tasks)

### 20. Inject ServerReachabilityProvider into ChatRepositoryImpl (remove MainApplication dependency)
votes 5/5 · consensus avg 73 · spread 15 · Devin 74 · Opus A 77 · raw: jules r2 #3
files: repository/ChatRepositoryImpl.kt (L41)

Calls `MainApplication.isServerReachable(url)` — an Android static in the repository layer. Inject `ServerReachabilityProvider`, replace the call. ~10 lines, 1 file.
- **Opus A verified:** line 41 is `org.ole.planet.myplanet.MainApplication.isServerReachable(url)` — fully-qualified static call inside a repository. `utils/ServerReachabilityProvider.kt` already exists and is what `MainApplication` delegates to.
- **Opus A do:** inject `ServerReachabilityProvider` and call it directly.
- **Opus A risk:** very low; the provider is already the underlying implementation.

### 21. Inject ServerReachabilityProvider into BellDashboardViewModel (remove MainApplication dependency)
votes 5/5 · consensus avg 72 · spread 15 · Devin 74 · Opus A 75 · raw: jules r2 #2
files: ui/dashboard/BellDashboardViewModel.kt (L137)

Calls static `MainApplication.isServerReachable(serverUrl)`, a global-app dependency blocking offline testing. Inject the existing `ServerReachabilityProvider` (utils/, suspend `isServerReachable`) and replace the call; remove the import. ~10 lines, 1 file.
- **Opus A verified:** line 15 statically imports `MainApplication.Companion.isServerReachable`, called at line 137.
- **Opus A do:** inject `ServerReachabilityProvider` and call it directly.
- **Opus A risk:** very low. The static import is easy to miss in a grep for `MainApplication.` — it does not match.

### 22. Expose the current user through RequestsUiState
votes 5/5 · consensus avg 72 · spread 15 · Devin 74 · Opus A 80 · raw: devin r2 #3
files: ui/teams/members/RequestsViewModel.kt, ui/teams/members/RequestsFragment.kt

`RequestsFragment` injects `UserSessionManager` (L26) solely to resolve the current user for `RequestsAdapter.setUser`, while `fetchMembers` already loads `userRepository.getUserModel()` (L130) and keeps only the id. Add `currentUser` to `RequestsUiState`, populate it in `fetchMembers`, drop the fragment's injection + standalone fetch. ~15 lines, 2 files.
- **Opus A verified:** the fragment injects `UserSessionManager` only to resolve the user for `RequestsAdapter.setUser`, while `RequestsViewModel.fetchMembers` already loads `userRepository.getUserModel()` and discards everything but `user?.id`.
- **Opus A do:** add `currentUser` to `RequestsUiState` (last, so positional constructors stay valid) and consume it in the existing `collectWhenStarted(viewModel.uiState)` collector.
- **Opus A risk:** low. Leave `MembersUiState` and the rest of the file alone.

### 23. Stop threading ConfigurationsRepository into dialog helpers
votes 5/5 · consensus avg 72 · spread 19 · Devin 74 · Opus A 73 · raw: devin r2 #6
files: utils/DialogUtils.kt (getUpdateDialog ~L188, startDownloadUpdate ~L206), ui/sync/SyncActivity.kt (L763), services/AutoSyncWorker.kt (L93)

Both helpers take a `ConfigurationsRepository` only to call `checkCheckSum(path)` inside a UI util. Change the signatures to accept `checkCheckSum: suspend (String) -> Boolean` and pass `configurationsRepository::checkCheckSum` at the two call sites; drop the import. ~25 lines, 3 files.
- **Opus A verified:** `getUpdateDialog` and `startDownloadUpdate` accept a `ConfigurationsRepository` purely for `checkCheckSum(path)`; both callers already hold the repository.
- **Opus A do:** take `checkCheckSum: suspend (String) -> Boolean` instead and pass `configurationsRepository::checkCheckSum` at both sites.
- **Opus A risk:** low; removes a repository import from a UI utility.

### 24. Resolve the current user inside AddResourceViewModel
votes 5/5 · consensus avg 72 · spread 16 · Devin 73 · Opus A 78 · raw: devin r2 #5
files: ui/resources/AddResourceViewModel.kt, ui/resources/AddResourceFragment.kt

`AddResourceFragment` injects `UserSessionManager` (L65) only for `getUserModel()` at ~L264 (`showAlert(path, userModel.id, userModel.name, …)`). Add `UserRepository` to the VM + a `currentUser()` accessor; drop the fragment injection. ~10 lines, 2 files.
- **Opus A verified:** the fragment injects `UserSessionManager` only to call `getUserModel()` at ~264; `AddResourceViewModel` currently injects only `PersonalsRepository`.
- **Opus A do:** add `UserRepository` to the ViewModel with a `currentUser()` accessor; swap the call and delete the injection.
- **Opus A risk:** low. Do not touch `AddResourceActivity.kt` — owned by an open PR.

### 25. SurveysRepository: return raw counts, not formatted strings
votes 5/5 · consensus avg 72 · spread 14 · Devin 76 · Opus A 63 · raw: kimi r2 #2
files: model/SurveyInfo.kt, repository/SurveysRepositoryImpl.kt (getSurveyInfos ~L287-332), ui/surveys/SurveysViewModel.kt, ui/surveys/SurveysAdapter.kt

`getSurveyInfos()` formats `submissionCount` via `context.resources.getQuantityString(R.plurals...)` and `creationDate` via `TimeUtils.formatDate` inside the repository — `SurveyInfo` carries pre-formatted strings, blocking KMP extraction and locale changes. Change fields to `Int`/`Long`, move plural+date formatting into the adapter/ViewModel. ~4 files.
- **Opus A verified:** `getSurveyInfos()` formats `submissionCount` through `context.resources.getQuantityString(R.plurals.survey_taken_count, …)` and `creationDate` through `TimeUtils.formatDate`, so `SurveyInfo` carries strings — locale changes cannot re-render without a re-query.
- **Opus A do:** return `Int`/`Long` raw values and move formatting into the adapter.
- **Opus A risk:** moderate — four files, and the survey repository is PR-contested. Verify each field's producers and consumers first.

### 26. ActivitiesRepositoryImpl: swap the dead Context param for DeviceNameProvider
votes 5/5 · consensus avg 72 · spread 22 · Devin 73 · Opus A 74 · raw: kimi r2 #3
files: repository/ActivitiesRepositoryImpl.kt, utils/NetworkUtils.kt (read-only), test

`serializeLoginActivities(activity, context)` (L307) forwards `context` to `NetworkUtils.getCustomDeviceName(context)` (L317) — which ignores it entirely (NetworkUtils L243-245 delegates to sharedPrefManager). Inject `DeviceNameProvider` and call it instead; drop the param; remove `Log.e` only if a neighboring-impl logger pattern exists (do not invent one).
- **Opus A verified:** `NetworkUtils.getCustomDeviceName(context)` is `fun getCustomDeviceName(context: Context): String { return sharedPrefManager.getCustomDeviceName() }` (`NetworkUtils.kt:243-245`) — the parameter is entirely unused. The repository threads a `Context` through `serializeLoginActivities` solely to supply it.
- **Opus A do:** inject the existing `DeviceNameProvider` (already used by `ResourcesRepositoryImpl` and `PersonalsRepositoryImpl`) and drop the forwarded parameter.
- **Opus A risk:** low, but check the other `context` uses in the file before deleting the constructor param. The dead parameter has six further call sites — leave them to their own tasks.

### 27. Collapse the double file resolution and stop re-statting in InlineResourceAdapter
votes 5/5 · consensus avg 71 · spread 17 · Devin 76 · Opus A 76 · raw: devin r1 #6, grok r2 #1
files: ui/courses/InlineResourceAdapter.kt (updateStatusAndPreview L162-203, getFileCacheKeyIfExist L312-318), test

`updateStatusAndPreview` calls `checkFileExist` (parse URL + stat) then the preview helpers re-run `file.exists()`/`lastModified()`/`length()` on the same resolved file — once per bind per row. Resolve the `File` once, pass it down, reuse one stat set; optionally hold an adapter-scoped `FileExistenceCache` cleared on detach/address change so scroll rebinds skip disk within TTL. ~25 lines, 1 file + tests.
- **Opus A verified:** `updateStatusAndPreview` calls `FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))`, which resolves and stats the file, and the preview helpers plus `getFileCacheKeyIfExist` then re-resolve and re-stat the same `File` (`exists`/`lastModified`/`length`).
- **Opus A do:** resolve the `File` once per bind and thread it through the helpers. Devin's variant is the tighter statement of the fix; grok's adds an adapter-scoped `FileExistenceCache` cleared on detach and list change — take devin's core and grok's cache only if profiling justifies it.
- **Opus A risk:** moderate — do not disturb the preview coroutine structure or the cache-key format.

### 28. Stop passing Context into the submissions PDF exporter
votes 5/5 · consensus avg 71 · spread 21 · Devin 74 · Opus A 65 · raw: kimi r2 #8
files: repository/SubmissionsRepositoryImpl.kt (L50/62-66/814/885), repository/SubmissionsRepositoryExporter.kt (L45-48/131-132), test

`SubmissionsRepositoryImpl` holds `@ApplicationContext` only to forward it to `exporter.generate*Pdf(context, …)` and to `NetworkUtils.getCustomDeviceName(context)` (which ignores the param). Inject the context into the exporter's constructor, drop the method params, and switch `customDeviceName` to the injected `DeviceNameProvider` (precedent in ResourcesRepositoryImpl/PersonalsRepositoryImpl).
- **Opus A verified:** the exporter takes `Context` as a method parameter and pulls in `PdfDocument`/`Paint`/`Canvas`; `SubmissionsRepositoryImpl` threads `@ApplicationContext` into it and into `NetworkUtils.getCustomDeviceName(context)` (which ignores the argument).
- **Opus A do:** grok's is the more complete statement — split rendering and file write into a new `@Inject` helper and keep assembly in the exporter. Kimi's covers the narrower `Context`-injection cleanup and the `DeviceNameProvider` swap; fold that in.
- **Opus A risk:** moderate; keep `generateSubmissionPdf`'s public signature so callers compile unchanged.

### 29. Move the examiner user lookup into HealthExaminationState
votes 5/5 · consensus avg 71 · spread 12 · Devin 73 · Opus A 77 · raw: devin r2 #4
files: ui/health/HealthExaminationViewModel.kt, ui/health/HealthExaminationActivity.kt

The activity reads `userSessionManager.getUserModel()` (L78) for the examiner identity (`isSelfExamination` L251, `createdBy` L256) even though the VM already injects `userRepository`. Add `currentUser` to `HealthExaminationState`, fetch it alongside the patient load, drop the activity's injection. ~20 lines, 2 files.
- **Opus A verified:** the activity reads `userSessionManager.getUserModel()` at line 78 while `HealthExaminationViewModel` already injects `userRepository`; the activity then mixes patient (`state.user`) and examiner (`currentUser`) around lines 251/256.
- **Opus A do:** add `currentUser` to `HealthExaminationState`, populate it in the load path, drop the injection.
- **Opus A risk:** low — keep every `state.user` (patient) reference untouched; the two identities are easy to conflate.

### 30. ReplyViewModel: absorb the last repository/service calls from ReplyActivity ⚠ disputed
votes 5/5 · consensus avg 71 · spread 25 · Devin 77 · Opus A 61 · raw: kimi r2 #1
files: ui/voices/ReplyViewModel.kt, ui/voices/ReplyActivity.kt, NEW test

`ReplyActivity` field-injects `activitiesRepository` (L58), `sharedPrefManager` (L63), `voicesRepository` (L65), `userSessionManager` (L55) and calls `getUserModel()`/`getCommunityLeaders()`/`setRepliedNewsId` directly, though a 17-line `ReplyViewModel` exists. Inject the services into the VM, add thin accessors, remove the Activity injections. New VM test file.
- **Opus A verified:** the activity injects `activitiesRepository`, `sharedPrefManager`, `voicesRepository` and `userSessionManager` and calls them directly, though `ReplyViewModel` already exists with a single method.
- **Opus A do:** move the session read, the two `SharedPrefManager` calls and the member-details fetch behind the ViewModel.
- **Opus A risk:** moderate; `VoicesActions.showMemberDetails` needs its data dependency separated from fragment construction, which is the awkward part.

### 31. Fetch one pending submission directly instead of loading a list
votes 5/5 · consensus avg 71 · spread 24 · Devin 80 · Opus A 78 · raw: codex r2 #10
files: data/room/dao/SubmissionDao.kt, repository/SubmissionsRepositoryImpl.kt (L456, L541)

Two call sites run `getSubmissionsByParentId(parentId, userId, "pending").firstOrNull()`, materializing a full list + hydration for one newest row. Reuse the existing bounded `getLatestPendingByUserAndParent` (LIMIT 1, ORDER BY lastUpdateTime DESC) where ordering matches, or add a precise LIMIT 1 query; hydrate only the returned submission. ~25 lines, 2 files.
- **Opus A verified:** `getSubmissionsByParentId(…, "pending").firstOrNull()` at lines 456 and 541. `SubmissionDao` already shows the bounded shape in `getLatestPendingByUserAndParent`.
- **Opus A do:** reuse that query where its ordering matches; otherwise add one precisely ordered `LIMIT 1` query. Hydrate only the returned row.
- **Opus A risk:** low-moderate — the two call sites differ in nullable user/parent semantics; compare before collapsing them onto one query.

### 32. Put next-step resource prefetch behind ResourcesRepository (or a step-resource coordinator) ⚠ disputed
votes 5/5 · consensus avg 71 · spread 25 · Devin 76 · Opus A 62 · raw: codex r2 #2, grok r1 #9
files: ui/courses/CoursesStepsViewModel.kt (L63-103), repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, optionally NEW helper class

`CoursesStepsViewModel.loadStep` injects `@ApplicationContext Context` + `ResourceDownloadCoordinator`, resolves filesystem markdown base paths and orchestrates next-step downloads itself. Move the orchestration behind a repository operation (or a concrete `@Inject` coordinator): resolve step resources, filter already-offline, schedule the same priority download; remove `Context`/`UrlUtils`/coordinator from the VM. ~80 lines, 3 files.
- **Opus A verified:** `loadStep` performs filesystem path lookup and calls `ResourceDownloadCoordinator.startBackgroundDownload` directly, and the ViewModel injects `Context` for markdown base paths, though the repository already owns `getAllStepResources` and `downloadResourcesPriority`.
- **Opus A do:** the two differ on destination — codex pushes the operation behind `ResourcesRepository` (cleaner layering); grok adds a `CourseStepResourceCoordinator` helper because it treats the repository as off-limits. Prefer codex's if `ResourcesRepository` is free when you start, else grok's.
- **Opus A risk:** moderate; keep `CourseStepUiState.isDownloadingResources` semantics unchanged.

### 33. Replace CourseDao.filterByTitleNormal @RawQuery with a parameterized query
votes 5/5 · consensus avg 71 · spread 17 · Devin 78 · Opus A 72 · raw: kimi r2 #7
files: data/room/dao/CourseDao.kt (L33-34), repository/CoursesRepositoryImpl.kt (search L273-305), test

`search()` string-builds `SELECT * FROM courses WHERE 1 = 1 AND courseTitleNormal LIKE ? ESCAPE '\'` per token into `SimpleSQLiteQuery`. Add a parameterized `@Query` per token, intersect results by id in Kotlin, keep the `\`/`%`/`_` escape rules and the existing starts-with/contains ranking. ⚠️ PR #15808 touches CourseDao — verify it is merged/closed before starting.
- **Opus A verified:** `CourseDao:33-34` is `@RawQuery suspend fun filterByTitleNormal(query: SupportSQLiteQuery)`, and `CoursesRepositoryImpl.search()` string-builds the statement per token and wraps it in `SimpleSQLiteQuery`.
- **Opus A do:** add a parameterised `@Query` per token and intersect by id in Kotlin, preserving the existing escape handling and ranking.
- **Opus A risk:** **check open PR 15808 first** — it touches `CourseDao.kt`; kimi flags this itself. The same raw-SQL pattern exists in `ResourcesRepositoryImpl` (my_library) and is a separate job.

### 34. Collapse the repeated upload-await blocks in UserDataWorker
votes 5/5 · consensus avg 70 · spread 8 · Devin 74 · Opus A 66 · raw: devin r2 #9
files: services/UserDataWorker.kt (UPLOAD_TYPE_BULK branch ~L45-105)

The bulk branch repeats `CompletableDeferred` + `OnSuccessListener` + `withTimeoutOrNull(30000L)` six times (user-activities, exam-result, resource, submit-photos, activities, shelf-user-data). Add one `awaitUploadCompletion(start: (onComplete: () -> Unit) -> Unit)` helper and rewrite the six blocks; keep the login branch's `CompletableDeferred<String>` untouched. ~50 lines, 1 file.
- **Opus A verified:** the `UPLOAD_TYPE_BULK` branch repeats the same `CompletableDeferred` + `OnSuccessListener` + `withTimeoutOrNull(30000L)` shape five times, plus once more for a `() -> Unit` callback.
- **Opus A do:** extract one `awaitUploadCompletion(start: (onComplete: () -> Unit) -> Unit)` helper.
- **Opus A risk:** low; leave the `UPLOAD_TYPE_LOGIN` branch's `CompletableDeferred<String>` alone — different result type.

### 35. Reuse the existing file-existence cache for course cover art ⚠ disputed
votes 5/5 · consensus avg 70 · spread 27 · Devin 81 · Opus A 84 · raw: claude r1 #5
files: utils/CoursesItemUtils.kt (bindCover L49-60)

`bindCover` calls `coverFile?.exists()` — a disk stat on the main thread inside `onBindViewHolder` for every course row (TeamCoursesAdapter L45, CoursesAdapter L330/378). Add a private `FileExistenceCache` (5 s TTL, already used by EnterprisesReportsAdapter) and call `coverExistenceCache.exists(coverFile, timeProvider.now())`. ~6 lines, 1 file.
- **Opus A verified:** line 60 runs `coverFile?.exists() == true` synchronously; `bindCover` is called from `CoursesAdapter:330`, `CoursesAdapter:378` and `TeamCoursesAdapter:45`, all bind paths. `FileExistenceCache` (5 s TTL) already exists at `FileUtils.kt:381` and is used by `EnterprisesReportsAdapter`.
- **Opus A do:** hold a `FileExistenceCache` + `TimeProvider` in the object and route the check through it. Keep `bindCover`'s signature so both callers compile untouched.
- **Opus A risk:** low; a cover that finishes downloading appears within the 5 s TTL or on next reload.

### 36. Compute sync-summary aggregates once per endpoint and model ⚠ disputed
votes 5/5 · consensus avg 70 · spread 36 · Devin 62 · Opus A 89 · raw: codex r1 #6
files: utils/SyncTimeLogger.kt (generateSummary L257-294), test

Each endpoint's durations are summed once for sorting and again while rendering; DB groups repeat the pattern plus item-count rescans. Introduce per-key aggregate locals (total, count, items) used for both sort and render; preserve exact output text. ~70 lines, 2 files. Same file as the rating-88 task — sequence them.
- **Opus A verified:** `detailedLogs` appears exactly 3× in the file — declared (38), cleared (73), appended (195) — and is never read, so all 12 `logDetail` call sites retain strings nothing consumes. `getOrPut(key) { mutableListOf() }` at 167/184/195 is non-atomic on a `ConcurrentHashMap` and yields an unsynchronised `ArrayList`, appended from parallel sync coroutines. `generateSummary` sums each group for `sortedByDescending` (268, 288) and again while rendering.
- **Opus A do:** delete `detailedLogs` and its clear/append; back the two remaining maps with `Collections.synchronizedList` via `computeIfAbsent`; compute per-endpoint and per-model aggregates once and render from them.
- **Opus A risk:** three concerns in one file — they conflict if split across PRs, so land together. Keep `logDetail`'s signature (12 callers). `extractProcessName`'s `split("/")` can also be cheapened, but `substringAfterLast` is **not** equivalent — the current code walks backwards skipping empty and `?`-prefixed segments.
- **Merged reading:** Bundle it into the main SyncTimeLogger task; it is the same file.

### 37. Share one OkHttp connection pool between the two HTTP clients
votes 5/5 · consensus avg 69 · spread 20 · Devin 79 · Opus A 77 · raw: claude r1 #6
files: di/NetworkModule.kt (L75-117)

`buildOkHttpClient` constructs a fresh `ConnectionPool` per call, and it is called twice (`@StandardHttpClient`, `@ReachabilityHttpClient`) — every reachability probe opens a new TCP+TLS handshake to the same host. Hoist one shared pool; keep each client's own `Dispatcher`. ~10 lines, 1 file.
- **Opus A verified:** `buildOkHttpClient` constructs `ConnectionPool(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)` at line 81 and is called twice — for `@StandardHttpClient` (101) and `@ReachabilityHttpClient` (113). Both talk to the same Planet host.
- **Opus A do:** hoist the pool to a single instance shared by both clients.
- **Opus A risk:** low. Keep the per-client `Dispatcher` separate — sharing it would queue reachability probes behind sync traffic. Removes a full TCP+TLS handshake from each probe.

### 38. Batch successful achievement-upload acknowledgements into one DAO transaction
votes 5/5 · consensus avg 69 · spread 12 · Devin 75 · Opus A 71 · raw: codex r2 #9
files: services/upload/AchievementUploader.kt, repository/UserRepository.kt, repository/UserRepositoryImpl.kt, data/room/dao/AchievementDao.kt

`uploadAchievement` calls `markAchievementUploaded` per successful document — one Room write per row in the upload loop. Introduce a small ack value (id + nullable rev), a batch repository method and one DAO transaction; accumulate successes in the loop, flush once after; keep per-doc exception isolation and CV-attachment ordering. ~75 lines, 4 files.
- **Opus A verified:** `markAchievementUploaded` is called inside the result loop, one Room update per successful document.
- **Opus A do:** accumulate successful acknowledgements and flush once through a batch repository method backed by a single DAO transaction.
- **Opus A risk:** moderate (4 files). Keep CV attachment upload immediately after each document — it needs that document's returned revision.

### 39. Move per-event accessibility strings in LifeAdapter to bind time ⚠ disputed
votes 5/5 · consensus avg 69 · spread 38 · Devin 75 · Opus A 91 · raw: devin r1 #7
files: ui/life/LifeAdapter.kt (onBindViewHolder L48-83)

`contentDescription = context.getString(...)` is set inside the `setOnTouchListener` (fires per MotionEvent) and the visibility click listener, though both strings depend only on `myLife.title` fixed at bind. Assign once during bind; remove the assignments inside the listeners. ~8 lines, 1 file.
- **Opus A verified:** line 67 assigns `contentDescription` inside `setOnTouchListener`, which fires on every MotionEvent; line 74 does the same inside `setOnClickListener`. Both strings depend only on `myLife.title`, fixed at bind time.
- **Opus A do:** assign both contentDescriptions once in `onBindViewHolder` next to the existing `imageView.contentDescription`; drop them from the listeners.
- **Opus A risk:** trivial. Also fixes a real accessibility defect — TalkBack has no drag label until the user first touches the control.
- **Merged reading:** Opus A rates it 91 because it is also an accessibility defect. Cheap, do it.

### 40. Remove sync-table events from team list view modelling
votes 5/5 · consensus avg 69 · spread 8 · Devin 74 · Opus A 66 · raw: codex r2 #4
files: ui/teams/TeamViewModel.kt (getTeamUpdateFlow L41), ui/teams/TeamFragment.kt (collector)

`getTeamUpdateFlow` exposes `RealtimeSyncManager.updatesFor("teams")` directly, duplicating the refresh path that `loadTeams`' `getMyTeamDetailsFlow` collection already provides and leaking a table name into UI. Delete the pass-through; ensure every loadTeams branch collects a repository flow. ~55 lines, 2 files.
- **Opus A verified:** `getTeamUpdateFlow` passes `RealtimeSyncManager.updatesFor("teams")` straight through, although `loadTeams` already collects `TeamsRepository.getMyTeamDetailsFlow` — two refresh paths, and a storage table name leaking into the UI layer.
- **Opus A do:** delete the pass-through and the fragment's collector; make every `loadTeams` branch collect the repository flow.
- **Opus A risk:** moderate — `ui/teams/**` is contested by several open PRs. Verify job cancellation still prevents duplicate collectors.

### 41. Observe chat history through ChatRepository instead of the sync service
votes 5/5 · consensus avg 69 · spread 18 · Devin 71 · Opus A 62 · raw: codex r2 #3
files: ui/chat/ChatViewModel.kt (L49-76/126-136), repository/ChatRepository.kt, repository/ChatRepositoryImpl.kt (getChatHistoryForUser L121-129)

`ChatViewModel` injects `RealtimeSyncManager` and translates the global "chats" table event into a refresh signal; the repository exposes only a snapshot and `ChatDao` has no observable query. Add a repository flow (minimal observable adapter confined to the impl), remove the sync dependency, preserve the active search query on emissions. ~90 lines, 3 files.
- **Opus A verified:** the ViewModel injects `RealtimeSyncManager` and maps the global `"chats"` table event to a refresh, while `getChatHistoryForUser` exposes only a snapshot.
- **Opus A do:** add a repository flow for one user's sorted history and drive the ViewModel from it.
- **Opus A risk:** moderate — `ChatViewModel.kt` is owned by open PR 15198; claude's list excludes it for that reason. Re-check before starting. Preserve the active search query across emissions.

### 42. Move retry HTTP execution behind RetryRepository
votes 5/5 · consensus avg 69 · spread 19 · Devin 71 · Opus A 69 · raw: codex r2 #8
files: services/retry/RetryQueueWorker.kt (L32-41), repository/RetryRepository.kt, repository/RetryRepositoryImpl.kt

The worker injects `ApiInterface` and mixes transport execution with queue state transitions. Define a repository result type (success / retryable / terminal, no Retrofit types), move endpoint dispatch + response classification into the impl, and swap the worker's dependency — keep batching, WorkManager results, and exactly-once state transitions. ~120 lines, 3 files.
- **Opus A verified:** the worker injects `ApiInterface` directly (line 41) and combines request execution with queue transitions, while `RetryRepository` already owns enqueue, pending selection, attempts and completion.
- **Opus A do:** move endpoint dispatch and response classification into the repository behind a result type that distinguishes success / retryable / terminal without exposing Retrofit types.
- **Opus A risk:** the largest item here (~120 lines, 3 files). Cancellation must not be recorded as a failed attempt.

### 43. Remove static context from UserRepositoryImpl
votes 5/5 · consensus avg 69 · spread 24 · Devin 72 · Opus A 79 · raw: jules r2 #9
files: repository/UserRepositoryImpl.kt (L527-528)

Uses `MainApplication.context` for `VersionUtils.getAndroidId` and `NetworkUtils.getCustomDeviceName` — static app context in the repository layer. Inject `@ApplicationContext context` (or `DeviceNameProvider` for the device name) and replace both uses. ~10 lines, 1 file.
- **Opus A verified:** lines 527-528 call `VersionUtils.getAndroidId(MainApplication.context)` and `NetworkUtils.getCustomDeviceName(MainApplication.context)` inside a repository.
- **Opus A do:** use the injected `@ApplicationContext` (add it if absent) and drop the `MainApplication` import.
- **Opus A risk:** low. Note `NetworkUtils.getCustomDeviceName` ignores its context argument entirely (see the `DeviceNameProvider` tasks) — routing it through `DeviceNameProvider` instead is the better end state.

### 44. Extract public-survey orchestration out of PublicSurveyActivity (payload builder + ViewModel)
votes 5/5 · consensus avg 68 · spread 10 · Devin 74 · Opus A 64 · raw: kimi r2 #5, grok r1 #8
files: ui/surveys/PublicSurveyActivity.kt (L127-180), NEW repository/PublicSurveyPayloadBuilder.kt or ui/surveys/PublicSurveyViewModel.kt, NEW test(s)

The Activity injects `SurveysRepository` + `SubmissionsRepository`, builds the submission JSON itself (`buildPublicAnswers` L166-180: question fetch, zip by questionId, selectMultiple wire format) and mutates the respondent object (`sanitizeRespondent` L159-164). Move payload building into an injectable `PublicSurveyPayloadBuilder` and the upload orchestration into a `PublicSurveyViewModel` (or a single coordinated extraction); Activity keeps navigation/toasts. Tests for selectMultiple array, select unwrap, age trim/non-numeric.
- **Opus A verified:** `buildPublicAnswers` queries `surveysRepository.getExamQuestions(surveyId)`, zips answers by `questionId` and encodes the `selectMultiple`/`select` wire format; `sanitizeRespondent` mutates the respondent `JsonObject` — data-layer serialization in an Activity.
- **Opus A do:** the two agents disagree on the destination: kimi extracts an injectable `PublicSurveyPayloadBuilder` into `repository/`, grok introduces a `PublicSurveyViewModel`. Prefer grok's — the Activity also needs the upload orchestration moved, and a ViewModel covers both; kimi's builder can live behind it if the payload logic deserves its own unit.
- **Opus A risk:** moderate. Neither may edit the survey/submission interfaces — both are PR-owned.

### 45. Use constant-time course-id membership during adapter removal
votes 5/5 · consensus avg 68 · spread 23 · Devin 64 · Opus A 82 · raw: codex r1 #1
files: ui/courses/CoursesAdapter.kt (removeCourses L119-121), test

`currentList.filter { it.courseId !in courseIds }` does a linear `contains` per item — O(n·m) when the removal set is large. Convert non-empty `courseIds` to a `Set` once; keep submitList/onComplete timing. ~25 lines, 2 files.
- **Opus A verified:** `currentList.filter { it.courseId !in courseIds }` with `courseIds: List<String>` — linear scan per item.
- **Opus A do:** convert `courseIds` to a `Set` once before filtering; short-circuit the empty case through the existing `submitList` path so `onComplete` timing is unchanged.
- **Opus A risk:** low; keep the completion callback firing from the `submitList` commit callback only.

### 46. Route health-record refreshes through HealthViewModel
votes 5/5 · consensus avg 68 · spread 20 · Devin 72 · Opus A 67 · raw: codex r2 #7
files: ui/health/MyHealthFragment.kt (setupRealtimeSync L255-264), ui/health/HealthViewModel.kt (refreshSelectedPatient)

The fragment consumes every `dataUpdateFlow` event to conditionally call `refreshSelectedPatient`, while the VM already owns `currentPatientId`/cancellation/repository reads. Remove the fragment's sync injection; add an explicit VM refresh intent; coalesce repeated refreshes; preserve selection on transient failure. ~55 lines, 2 files.
- **Opus A verified:** `setupRealtimeSync` consumes every `dataUpdateFlow` event and conditionally calls `refreshSelectedPatient`, while the ViewModel already owns `currentPatientId`, cancellation and the repository reads.
- **Opus A do:** replace the table-event collection with one explicit ViewModel refresh intent keyed on the tracked patient id; coalesce repeats while a fetch is in flight.
- **Opus A risk:** moderate; preserve the selected patient on transient failure rather than clearing it.

### 47. Move the PendingCourseResource buffer out of the singleton CoursesRepositoryImpl ⚠ disputed
votes 5/5 · consensus avg 68 · spread 36 · Devin 72 · Opus A 50 · raw: kimi r2 #9
files: repository/CoursesRepositoryImpl.kt (L81-95, flush ~L842)

A `@Singleton` holds `Collections.synchronizedList(mutableListOf<PendingCourseResource>())` — process-wide mutable sync state coupling concurrent runs. Convert to a local buffer threaded through the private parse/flush functions; keep `withTransaction` boundaries; add a two-interleaved-batches test.
- **Opus A verified:** a `Collections.synchronizedList(mutableListOf<PendingCourseResource>())` field on a `@Singleton` repository, shared across concurrent sync runs.
- **Opus A do:** thread the buffer through the parse/flush functions as a local so one run cannot leak into another.
- **Opus A risk:** the task opens with "trace the lifecycle of `pendingCourseResources`" — the analysis is not done, so the change is unspecified. Do the trace first and re-scope.
- **Merged reading:** The task starts with 'trace the lifecycle first', so it is unspecified. Scope it before scheduling.

### 48. ProcessUserDataActivity: introduce a ViewModel for the member-upload chain
votes 5/5 · consensus avg 67 · spread 21 · Devin 74 · Opus A 58 · raw: kimi r2 #6
files: ui/sync/ProcessUserDataActivity.kt, NEW ui/sync/ProcessUserDataViewModel.kt, services/UploadToShelfService.kt, NEW test

The Activity field-injects `syncRepository`/`userRepository`/`uploadToShelfService` (L47-57) and runs a three-deep callback chain (`uploadSingleUserData` → `uploadSingleUserHealth` → `fetchAndLogUserSecurityData` → `fetchUserSecurityData`). Add a suspend `uploadSingleUser(userName)` to the service (keep listener APIs for other callers), a `ProcessUserDataViewModel` exposing a `StateFlow<SyncUiState>` for the three `startUpload` branches, and reduce the Activity to dialog/Toast rendering.
- **Opus A verified:** the activity field-injects `syncRepository`, `userRepository` and `uploadToShelfService`, and chains `uploadSingleUserData` → `uploadSingleUserHealth` → `fetchAndLogUserSecurityData` through nested success listeners.
- **Opus A do:** add a suspend counterpart on the service and a ViewModel exposing the three branches as state.
- **Opus A risk:** high overlap — this edits `UploadToShelfService`, which the health-upload task also rewrites. Sequence them or merge.

### 49. Eliminate re-list-allocation in CalendarViewModel.loadMeetups ⚠ disputed
votes 5/5 · consensus avg 67 · spread 26 · Devin 73 · Opus A 66 · raw: kimi r1 #4
files: ui/calendar/CalendarViewModel.kt (L33-38)

The `getMyTeamsFlow` collector rebuilds `_teamNames` and re-queries `getMeetupsForTeams` on every emission, even when the team list is referentially unchanged. Add `distinctUntilChanged()` before the assignments and derive `teamIds` once into a local feeding both outputs. ~6 lines, 1 file.
- **Opus A verified:** `loadMeetups()` collects `getMyTeamsFlow(userId)` and on every emission rebuilds `_teamNames` and re-queries `getMeetupsForTeams(...)`, even when the team list is unchanged — duplicate DB reads on any teams write.
- **Opus A do:** kimi's fix is the cheap one — `distinctUntilChanged()` on the collected list and derive `teamIds` once. Grok's proposes extracting a `CalendarMeetupsLoader` for the three-repository fan-out; that is the larger structural move and does not itself remove the redundant reload. Do kimi's first; treat grok's as the optional follow-up.
- **Opus A risk:** low for the `distinctUntilChanged` version; keep the public `meetups`/`teamNames` StateFlow API unchanged.
- **Merged reading:** The split is scope: distinctUntilChanged only versus extracting a loader class. Do the small one.

### 50. Introduce DashboardElementViewModel for session and activity calls
votes 5/5 · consensus avg 67 · spread 19 · Devin 72 · Opus A 61 · raw: devin r2 #10
files: ui/dashboard/DashboardElementActivity.kt (L35/107-125), NEW ui/dashboard/DashboardElementViewModel.kt

The abstract dashboard activity injects `ActivitiesRepository` (L35) and calls `profileDbHandler.getUserModel()`/`logoutAsync()` in `lifecycleScope` blocks — data/session calls with no VM. Create a `@HiltViewModel` exposing `currentUser()`, `recordUserChallengeAction(userId)`, `logout()`; reroute the calls; delete the injection. Do NOT touch SyncActivity. ~70 lines, 2 files.
- **Opus A verified:** the activity injects `ActivitiesRepository` directly and calls `profileDbHandler.getUserModel()` / `logoutAsync()` (inherited from `SyncActivity`) inside `lifecycleScope` blocks.
- **Opus A do:** introduce a `@HiltViewModel` for the user lookup, the challenge-action record and logout.
- **Opus A risk:** moderate; do not rename or remove `profileDbHandler` in `SyncActivity`, which still uses it.

### 51. Replace the regex split and no-op trimIndent in the health examination list
votes 5/5 · consensus avg 67 · spread 19 · Devin 72 · Opus A 79 · raw: claude r1 #10
files: ui/health/HealthExaminationAdapter.kt (L69, L104, L176)

Fallback display name does `createdBy.split(colonRegex).dropLastWhile{isEmpty}.toTypedArray().getOrNull(1)` — regex + two copies for one substring; replace with `substringAfter(':', "").takeIf { it.isNotBlank() }` and delete `colonRegex`. Also drop `.trimIndent()` on `getString(R.string.two_strings, …)` ("%1$s %2$s" is single-line). Keep the L140 `trimIndent` (multi-line `vitals_format`). ~8 lines, 1 file.
- **Opus A verified:** line 69 derives one substring via `createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1)`, with `colonRegex` existing solely for it (line 176). Line 104 appends `.trimIndent()` to `getString(R.string.two_strings, …)`, and `two_strings` is `%1$s %2$s` (`values/strings.xml:1028`) — single-line, so `trimIndent` splits, inspects and rejoins to produce an identical string on every bind.
- **Opus A do:** use `createdBy.substringAfter(':', "")`, delete `colonRegex`, drop the `trimIndent` at 104.
- **Opus A risk:** low — leave the `trimIndent` at line 140, which formats the genuinely multi-line `vitals_format`.

### 52. Centralize survey reload state in SurveysViewModel
votes 5/5 · consensus avg 67 · spread 22 · Devin 72 · Opus A 64 · raw: codex r2 #6
files: ui/surveys/SurveyFragment.kt (L36-49/90-106), ui/surveys/SurveysViewModel.kt (loadSurveys L78-103)

The fragment injects `RealtimeSyncManager` + `RealtimeSyncHelper` and knows sync storage details while the VM owns team/adoptable selection. Remove the sync mixin; make `loadSurveys` idempotently retain scope + cancel prior jobs; reload after `adoptSurvey` inside the VM; prevent stale loads overwriting newer selections; retain search/sort on reload. ~70 lines, 2 files.
- **Opus A verified:** the fragment injects `RealtimeSyncManager` and installs `RealtimeSyncHelper` while `SurveysViewModel.loadSurveys` separately owns team/adoptable selection.
- **Opus A do:** move reload policy into the ViewModel; make `loadSurveys` cancel any prior job and retain the active scope; reload after `adoptSurvey` from inside the ViewModel.
- **Opus A risk:** moderate — the stale-load-overwrites-newer-selection guard is the fiddly part and is the actual user-visible bug here.

### 53. Compute the storage selection counters in one pass
votes 5/5 · consensus avg 67 · spread 22 · Devin 73 · Opus A 80 · raw: claude r1 #8
files: ui/settings/StorageCategoryViewModel.kt (StorageCategoryUiState L19-27, updates L42-73)

`checkedCount` is a computed getter scanning the whole list; `allChecked` calls it and scans again — two passes per state emission, read on every checkbox tap by the fragment. Make `checkedCount` a stored property derived during the single `map` already performed; keep names/types so the fragment compiles unchanged. ~20 lines, 1 file.
- **Opus A verified:** `checkedCount` is `items.count { it.isChecked }` and `allChecked` calls `checkedCount` then compares against `items.size` — two full scans per state read, and the fragment reads both on every emission. `toggleItemChecked` emits a new state on every checkbox tap.
- **Opus A do:** make `checkedCount` a real constructor property derived from the `map` already being performed; redefine `allChecked` as a single comparison.
- **Opus A risk:** low — keep both names and types so `StorageCategoryDetailFragment` compiles unchanged.

### 54. Peel Context off DictionaryFileReaderImpl via StoragePathResolver
votes 5/5 · consensus avg 67 · spread 13 · Devin 71 · Opus A 70 · raw: grok r1 #3
files: repository/DictionaryFileReader.kt (L15-23), test

`@ApplicationContext Context` is held only for `FileUtils.checkFileExist`/`getSDPathFromUrl`; `StoragePathResolver` (already `@Inject`) centralizes those paths. Swap the dependency; keep the `DictionaryFileReader` interface and bind target class name stable — no module edit.
- **Opus A verified:** the impl takes `@ApplicationContext Context` to call `FileUtils.checkFileExist(context, …)` / `getSDPathFromUrl(context, …)`, while `StoragePathResolver` already centralises those operations.
- **Opus A do:** inject `StoragePathResolver` instead; keep the interface and bind target name stable so no module edit is needed.
- **Opus A risk:** low by design — the "no `RepositoryModule` edit" constraint is what keeps it small.

### 55. Resolve the local resource directory once in WebViewActivity instead of per navigation
votes 5/5 · consensus avg 67 · spread 20 · Devin 74 · Opus A 75 · raw: devin r1 #5
files: ui/viewer/WebViewActivity.kt (L67/107/174/288-312)

`getLocalResourceDirectory(resourceId)` runs `getExternalFilesDir` + two `canonicalFile` resolutions, and is recomputed in `onCreate`, `setupWebView`, `setupAssetLoader`, and inside `checkUrlSafety` — which fires on every `onPageStarted`/`shouldOverrideUrlLoading`. Cache the resolved `File?` once; keep the canonicalFile traversal guards. ~20 lines, 1 file.
- **Opus A verified:** `getLocalResourceDirectory` runs `getExternalFilesDir` plus two `canonicalFile` resolutions and is called from `onCreate`, `setupWebView`, `setupAssetLoader` and `checkUrlSafety` — the last firing on every `onPageStarted` / `shouldOverrideUrlLoading`.
- **Opus A do:** resolve once into a field and reuse.
- **Opus A risk:** **do not** weaken the `canonicalFile` containment checks — they guard against path traversal. Cache the resolved directory, not the safety decision.

### 56. Remove MainApplication statics from ResourcesRepositoryImpl
votes 5/5 · consensus avg 67 · spread 14 · Devin 72 · Opus A 58 · raw: grok r1 #5
files: repository/ResourcesRepositoryImpl.kt (L399 getExternalFilesDir, L504 applicationScope.launch), test

Replace `MainApplication.context.getExternalFilesDir` with the existing `StoragePathResolver` (new constructor param, no module edit needed) and `MainApplication.applicationScope.launch` with suspend + injected `dispatcherProvider` (or `@ApplicationScope CoroutineScope` if a qualifier exists). Surgical removal only.
- **Opus A verified:** `MainApplication.context.getExternalFilesDir(null)` at line 399 and `MainApplication.applicationScope.launch` at line 504.
- **Opus A do:** resolve paths through the injected `StoragePathResolver` and replace the fire-and-forget launch with structured `withContext` on the injected dispatcher.
- **Opus A risk:** the scope change is the risky half — a fire-and-forget call becoming suspend changes the caller's timing. Grok allows either that or `@ApplicationScope`; prefer the qualifier if it is already bindable.

### 57. Remove static MainApplication.isCollectionSwitchOn in CollectionsFragment
votes 5/5 · consensus avg 67 · spread 15 · Devin 70 · Opus A 68 · raw: jules r2 #4
files: ui/resources/CollectionsFragment.kt (L162/233), MainApplication.kt (L114)

A global static holds UI state that breaks on process death. Remove `isCollectionSwitchOn` from MainApplication; hold it as a private fragment field (or existing VM if one exists). ~15 lines, 2 files.
- **Opus A verified:** `MainApplication.isCollectionSwitchOn` is declared at `MainApplication.kt:114` and used only at `CollectionsFragment.kt:162` (read) and `:233` (write).
- **Opus A do:** move it to a fragment field and delete the static.
- **Opus A risk:** low — with exactly one reader and one writer the move is contained. Process death currently resets it anyway, so no behaviour is lost.

### 58. In-memory layer for LifeCache
votes 5/5 · consensus avg 67 · spread 21 · Devin 70 · Opus A 70 · raw: grok r2 #6
files: repository/LifeCache.kt (read/write L24-35), test

`read` always runs `preferences.getString` + `gson.fromJson`; `getMyLifeForDashboard` hits it on every empty-visible dashboard entry. Add a process-local map checked before prefs, populated on read/write, invalidated on write; return defensive copies/immutable lists. Same file as grok1's package move — sequence accordingly.
- **Opus A verified:** `read` always does `preferences.getString(...)` then `gson.fromJson`, with no in-memory layer; `LifeRepositoryImpl.getMyLifeForDashboard` hits it on the empty-visible path, so repeated dashboard entry pays Gson each time.
- **Opus A do:** add a process-local map checked before prefs, invalidated on write. Return immutable or defensive copies.
- **Opus A risk:** low; keep the `@Singleton` scope and the `myLifeCache_` key prefix. Overlaps with the `LifeCache` package move — do that one first or together.

### 59. Stop SharedPreferences thrash in DownloadService queue bookkeeping ⚠ disputed
votes 5/5 · consensus avg 66 · spread 27 · Devin 72 · Opus A 50 · raw: grok r2 #3
files: services/DownloadService.kt (L153-163/177-193), test

After each URL, `getRemainingCount()` re-reads both prefs StringSets and recounts; `cleanupProcessedUrls()` re-reads, mutates, and writes both sets back then recounts again. Mirror the queues in memory on start/enqueue, decrement the cached count, and persist in batches (drain/stop or per-completion without full recount); keep crash-safe rebuild + priority-before-pending ordering. ~150-line cap.
- **Opus A verified:** after each URL, `getRemainingCount()` re-reads both StringSets and `cleanupProcessedUrls()` re-reads, mutates, writes back both, then recounts.
- **Opus A do:** mirror the queues in memory, pop from memory, and persist in batches.
- **Opus A risk:** highest-risk item here. Grok concedes it "may approach" the size limit, and batching persistence trades crash-safety for speed in a foreground download service — a crash mid-batch loses queue state. Needs a durability decision before coding.

### 60. Split PDF I/O out of SubmissionsRepositoryExporter's data assembly
votes 5/5 · consensus avg 66 · spread 9 · Devin 70 · Opus A 65 · raw: grok r1 #4
files: repository/SubmissionsRepositoryExporter.kt, NEW services/SubmissionPdfWriter.kt, test

The exporter mixes Room DAO assembly with `PdfDocument`/`Paint`/`Canvas`/`Environment`. Keep data assembly in the exporter; move PDF rendering + file write to a concrete `@Inject` writer; keep the public signature so callers compile unchanged. Do not touch SubmissionsRepositoryImpl or DAOs here.
- **Opus A verified:** the exporter takes `Context` as a method parameter and pulls in `PdfDocument`/`Paint`/`Canvas`; `SubmissionsRepositoryImpl` threads `@ApplicationContext` into it and into `NetworkUtils.getCustomDeviceName(context)` (which ignores the argument).
- **Opus A do:** grok's is the more complete statement — split rendering and file write into a new `@Inject` helper and keep assembly in the exporter. Kimi's covers the narrower `Context`-injection cleanup and the `DeviceNameProvider` swap; fold that in.
- **Opus A risk:** moderate; keep `generateSubmissionPdf`'s public signature so callers compile unchanged.

### 61. Cache per-row date strings in PersonalsAdapter
votes 5/5 · consensus avg 65 · spread 11 · Devin 70 · Opus A 69 · raw: kimi r1 #1
files: ui/personals/PersonalsAdapter.kt (onBindViewHolder L38)

`getFormattedDate(item.date)` runs per bind for stable rows. Add a `HashMap<Long,String>` and `getOrPut`, mirroring TeamsAdapter's `dateCache` (L62). Key on `item.date ?: 0L` if nullable. ~6 lines, 1 file.
- **Opus A verified:** `onBindViewHolder` calls `TimeUtils.getFormattedDate(item.date)` per row per bind; `ui/teams/TeamsAdapter.kt:62` already establishes the `dateCache.getOrPut(...)` pattern.
- **Opus A do:** add a `HashMap<Long, String>` date cache and wrap the call.
- **Opus A risk:** low; key on `item.date ?: 0L` if the field is nullable.

### 62. Single-pass startsWith ranking in ResourcesRepositoryImpl.search ⚠ disputed
votes 5/5 · consensus avg 65 · spread 32 · Devin 66 · Opus A 72 · raw: grok r2 #4
files: repository/ResourcesRepositoryImpl.kt (search L81-134), test

After SQL filtering, matches are split into `startsWithQuery`/`containsQuery` ArrayLists then concatenated (extra list + two-pass structure). Keep SQL as-is; use one ordered accumulation (`partition` or single `buildList` pass); preserve empty-query branches and `userIdPattern` escaping. ~3 files incl test.
- **Opus A verified:** after the SQL `LIKE` filter the results are split into `startsWithQuery` / `containsQuery` `mutableListOf`s and returned as `startsWithQuery + containsQuery` — a third allocation.
- **Opus A do:** accumulate once in rank order via `buildList`.
- **Opus A risk:** low; preserve the empty-query branches and `userIdPattern` escaping. Do not introduce FTS — open PR 16624 owns that.

### 63. Cache per-row date strings in HealthUsersAdapter.bindDate
votes 5/5 · consensus avg 65 · spread 9 · Devin 68 · Opus A 68 · raw: kimi r1 #3
files: ui/health/HealthUsersAdapter.kt (bindDate ~L49-50)

`formatDate(user.joinDate)` on every bind including `"joinDate"` payload rebinds. Hoist a `HashMap<Long,String>` to the adapter (survives ViewHolder recycling) + `getOrPut`. ~6 lines, 1 file.
- **Opus A verified:** `ViewHolder.bindDate` calls `TimeUtils.formatDate(user.joinDate)` on every bind including the partial-payload rebind path.
- **Opus A do:** hoist a date cache to the adapter (not the ViewHolder, so it survives recycling) and wrap the call.
- **Opus A risk:** low.

### 64. Extract meetup loading from CalendarViewModel's multi-repository fan-out ⚠ disputed
votes 5/5 · consensus avg 64 · spread 37 · Devin 70 · Opus A 66 · raw: grok r1 #10
files: ui/calendar/CalendarViewModel.kt (L18-38), NEW ui/calendar/CalendarMeetupsLoader.kt, test

The VM coordinates `UserRepository` + `TeamsRepository` + `EventsRepository` to load meetups — cross-feature fan-out in the VM. Add an `@Inject` loader exposing one suspend method over the three existing interfaces; VM maps results to state only.
- **Opus A verified:** `loadMeetups()` collects `getMyTeamsFlow(userId)` and on every emission rebuilds `_teamNames` and re-queries `getMeetupsForTeams(...)`, even when the team list is unchanged — duplicate DB reads on any teams write.
- **Opus A do:** kimi's fix is the cheap one — `distinctUntilChanged()` on the collected list and derive `teamIds` once. Grok's proposes extracting a `CalendarMeetupsLoader` for the three-repository fan-out; that is the larger structural move and does not itself remove the redundant reload. Do kimi's first; treat grok's as the optional follow-up.
- **Opus A risk:** low for the `distinctUntilChanged` version; keep the public `meetups`/`teamNames` StateFlow API unchanged.
- **Merged reading:** The split is scope: distinctUntilChanged only versus extracting a loader class. Do the small one.

### 65. Convert the diagnosis lookup to a Set and hoist per-checkbox styling in HealthExaminationActivity ⚠ disputed
votes 5/5 · consensus avg 64 · spread 26 · Devin 66 · Opus A 82 · raw: devin r1 #3
files: ui/health/HealthExaminationActivity.kt (L199-228)

`mainList = listOf(*arr)` then `contains` in a loop — O(n·m) where a Set gives O(1). `showCheckbox` also re-resolves `getColorStateList`/`getColor`/`dpToPx(8)` ×4 per checkbox — all loop-invariant. Set + hoisted locals. ~12 lines, 1 file.
- **Opus A verified:** `preloadCustomDiagnosis` builds `listOf(*arr)` then calls `mainList.contains(s)` inside the `conditionsMap` loop (O(n·m)); `showCheckbox` re-resolves `ContextCompat.getColorStateList`, `ContextCompat.getColor` and calls `dpToPx(8)` four times for every checkbox in `for (s in arr)`.
- **Opus A do:** make `mainList` a `Set`; hoist the tint, colour and padding values above the loop.
- **Opus A risk:** low, self-contained in two private methods.

### 66. Memoize ResourcesPreviewLoader audio/csv/text work
votes 5/5 · consensus avg 64 · spread 21 · Devin 71 · Opus A 63 · raw: grok r2 #8
files: utils/ResourcesPreviewLoader.kt (L13-50), test

`getAudioPreview` constructs a `MediaMetadataRetriever` per call; `getCsvPreview`/`getTextPreview` rebuild parser/file reads each call. Add a small LRU (~32-64) keyed by `absolutePath + lastModified + length`; keep `DispatcherProvider.io`, formats (`m:ss`, 5 CSV rows, 8 text lines), and error-swallow behavior.
- **Opus A verified:** `getAudioPreview` builds a new `MediaMetadataRetriever` per call with no memoisation; `getCsvPreview` rebuilds its parser stack; `getTextPreview` reopens the file.
- **Opus A do:** add a bounded LRU keyed on `absolutePath + lastModified + length`.
- **Opus A risk:** low-moderate — cap the cache to avoid leaks and keep the return formats (`m:ss`, 5 CSV rows, 8 text lines) byte-identical.

### 67. Stop double formatting in FeedbackAdapter.onBindViewHolder
votes 5/5 · consensus avg 63 · spread 14 · Devin 68 · Opus A 67 · raw: kimi r1 #5
files: ui/feedback/FeedbackAdapter.kt (L53-77)

`getFormattedDate(feedback.openTime)` runs per bind and feeds both the rebuilt `contentDescription` and `tvOpenDate`. Cache by `openTime` in a `HashMap<Long,String>`; reuse for both. ~8 lines, 1 file.
- **Opus A verified:** `onBindViewHolder` formats `feedback.openTime` and rebuilds a multi-part `contentDescription` on every bind.
- **Opus A do:** cache by `openTime` and reuse the string for both the content description and the date text.
- **Opus A risk:** low.

### 68. Decouple TeamPagerAdapter/TeamDetailFragment from MainApplication.listener
votes 5/5 · consensus avg 63 · spread 20 · Devin 66 · Opus A 53 · raw: jules r2 #6
files: ui/teams/TeamDetailFragment.kt (L236-237/325-339), ui/teams/TeamPagerAdapter.kt, MainApplication.kt (L117-120)

Child-fragment→parent events go through a global `MainApplication.listener` (a `WeakReference<OnTeamPageListener>` — mutable shared state that cross-talks when multiple team pages open). Remove the field; in `btnAddDoc` resolve the active ViewPager fragment and cast to the existing `OnTeamPageListener`. ~20 lines, 3 files.
- **Opus A verified:** assigned at `TeamPagerAdapter:84`, `:87` and `TeamDetailFragment:237`; read at `TeamDetailFragment:335`, `:337`, `:339`. A static holding a Fragment is a genuine leak and breaks with multiple team pages.
- **Opus A do:** resolve the active fragment from the ViewPager at click time instead.
- **Opus A risk:** real lifecycle correctness win, but `ui/teams/**` is the single most PR-contested area in the repo (15951, 16623, 15825, 15820) and jules could not check open PRs. Expect a collision; re-verify ownership before starting.

### 69. Replace printStackTrace() with Log.w in TimeUtils
votes 5/5 · consensus avg 63 · spread 21 · Devin 69 · Opus A 65 · raw: kimi r1 #7
files: utils/TimeUtils.kt (7 sites: L72/115/135/158/170/201/214)

`e.printStackTrace()` in `formatInstant` runs inside every `getFormattedDate`/`formatDate` row bind — a full stack trace to stderr per row per rebind on parse failure. Add a TAG and replace each with `Log.w(TAG, "<fn> failed", e)`; keep return values identical. ~12 lines, 1 file.
- **Opus A verified:** `e.printStackTrace()` at seven sites including `formatInstant`, which runs inside every `getFormattedDate` / `formatDate` row bind — a parse failure prints a full trace per row per rebind.
- **Opus A do:** replace with tagged `Log.w`, keeping return values identical.
- **Opus A risk:** low. Note `TimeUtils` was on grok's discard list as PR-contested — re-check.

### 70. Remove static MainApplication.applicationScope from NotificationActionReceiver
votes 5/5 · consensus avg 62 · spread 21 · Devin 70 · Opus A 55 · raw: jules r2 #10
files: services/NotificationActionReceiver.kt (L31-32)

A BroadcastReceiver launches on the global `applicationScope`, breaking testability/lifecycle isolation. It already calls `goAsync()` — use the pending-result context with a local scope or injected DispatcherProvider; remove the MainApplication import. ~15 lines, 1 file.
- **Opus A verified:** line 32 is `MainApplication.applicationScope.launch` inside a `BroadcastReceiver`.
- **Opus A do:** use `goAsync()` with a locally managed scope.
- **Opus A risk:** `goAsync()` must be paired with `finish()` on every path or the receiver leaks — jules' step list does not say so. Get the lifecycle right or leave it.

### 71. Fewer passes in NotificationsRepositoryImpl.getEnrichedNotifications ⚠ disputed
votes 5/5 · consensus avg 62 · spread 37 · Devin 70 · Opus A 54 · raw: grok r2 #7
files: repository/NotificationsRepositoryImpl.kt (L165-239), test

The enrichment walks payloads repeatedly (`mapNotNull`/`distinct`/`filter` chains for task ids, join ids, titles) and always `async { getUnreadCount }` even when the loaded payloads already carry `isRead`. Single-pass collect; compute unread from memory when semantics match (preserve admin/unread-filter edge cases); keep the parallel `async` batch fetches and the `parsedTaskDates` contract.
- **Opus A verified:** the method walks payloads to split task/join lists then runs further `mapNotNull`/`distinct`/`filter` passes, and always issues `async { getUnreadCount(...) }` even when the loaded payloads already carry `isRead`.
- **Opus A do:** collapse to a single pass; derive the unread tally in memory where that matches the DAO's semantics for the current filter.
- **Opus A risk:** the unread-count shortcut is the dangerous part — admin and `unread`-filter cases must keep hitting the DAO or counts silently drift. Conflicts with two other tasks in this file.
- **Merged reading:** Pushes string resources into the data layer, the opposite policy from the Surveys raw-values task. Decide the policy before touching either.

### 72. Hoist the loop-invariant JSON read in ExamQuestion.insertCorrectChoice ⚠ disputed
votes 5/5 · consensus avg 62 · spread 34 · Devin 67 · Opus A 79 · raw: devin r1 #2
files: model/ExamQuestion.kt (else-branch ~L96-108)

`JsonUtils.getString("correctChoice", question)` is re-evaluated every loop iteration though `question` never changes (has/get/null-check each call); `correctChoiceList` also reassigned per match instead of breaking. Read once before the loop; break on first match. ~8 lines, 1 file.
- **Opus A verified:** the else-branch re-evaluates `JsonUtils.getString("correctChoice", question)` on every iteration of `for (a in 0 until array.size())` although `question` is loop-invariant, and keeps assigning `correctChoiceList` instead of stopping at the first match.
- **Opus A do:** hoist the lookup into a local before the loop; `break` on the first match.
- **Opus A risk:** low — currently the *last* matching id wins; ids are unique, so `break` is equivalent, but confirm on your fixtures.

### 73. Reject mismatched multi-select answers before sorting ⚠ disputed
votes 5/5 · consensus avg 62 · spread 25 · Devin 65 · Opus A 75 · raw: codex r1 #9
files: utils/ExamAnswerUtils.kt (checkMultipleSelectAnswer L77-85), test

Both answer collections are lowercased + sorted before `==` — when sizes differ equality is impossible, so the allocations/sorts are wasted on a common wrong-answer path. Return false early on size mismatch; keep the sorted-list comparison (preserves duplicate semantics). ~20 lines, 2 files.
- **Opus A verified:** `checkMultipleSelectAnswer` lowercases and sorts both collections, then compares — even when the sizes differ and equality is impossible.
- **Opus A do:** return false immediately when the sizes differ, after the existing null guard.
- **Opus A risk:** low; keep the sorted comparison for the equal-size path to preserve duplicate-answer semantics.

### 74. Remove the redundant coroutine wrapper in CoursesViewModel.loadCourses ⚠ disputed
votes 5/5 · consensus avg 61 · spread 30 · Devin 68 · Opus A 76 · raw: codex r1 #3
files: ui/courses/CoursesViewModel.kt (L110-145), test

`loadCourses` wraps a single `getCourseProgress` call in `coroutineScope { async { … }.await() }` — a pointless child coroutine + Deferred per load that obscures the call. Call the repository directly in the existing IO context; remove the two imports. ~18 lines, 2 files.
- **Opus A verified:** lines 125-129 wrap one `progressRepository` call in `coroutineScope { val progressDeferred = async { … }; progressDeferred.await() }` with no sibling work.
- **Opus A do:** call the repository directly inside the existing IO context; remove the `async`/`coroutineScope` imports.
- **Opus A risk:** low; preserve exception handling and the single final state publication.

### 75. Read each progress JSON field once in ProgressViewModel
votes 5/5 · consensus avg 61 · spread 23 · Devin 66 · Opus A 74 · raw: claude r1 #9
files: ui/courses/ProgressViewModel.kt (L36-54)

`obj.has("stepMistake")` + `obj.get("stepMistake")` is two hash lookups per value; `getAsJsonObject("progress")` runs twice for current/max — per enrolled course per screen open. Single `get` + local; hoist the nested object. ~12 lines, 1 file. NOTE: superseded if the rating-77 typed-projection task lands.
- **Opus A verified:** `fetchCourseData` returns a raw `JsonArray` and `ProgressViewModel` maps it itself: `obj.has("stepMistake")` followed by `obj.get("stepMistake")` (two lookups for one value), and `obj.getAsJsonObject("progress")` resolved twice for `current` and `max`. `obj.get("courseId").asString` throws on a malformed document. Target type `CoursesProgressRow` already exists.
- **Opus A do:** add `getCourseProgressRows(userId): List<CoursesProgressRow>` to the repository and move the mapping there, skipping rows with a missing id rather than throwing. This supersedes the narrower "read each field once" version of the same task — the field-read fix comes free with the move.
- **Opus A risk:** moderate. Leave `fetchCourseData` itself alone: `DashboardViewModel:326` consumes its `JsonArray` for a different purpose.

### 76. Push the shelf-dispatch map behind an interface-friendly seam in SyncRepositoryImpl ⚠ disputed
votes 5/5 · consensus avg 61 · spread 29 · Devin 68 · Opus A 52 · raw: kimi r2 #4
files: repository/SyncRepository.kt, repository/SyncRepositoryImpl.kt (L38/44-51/95/181), di/RepositoryModule.kt (⚠️ collision-check PRs #15824/#15825), test

`shelfDispatchMap` hard-codes shelf-name→batch-insert in the repository; `dagger.Lazy<TransactionSyncManager>` and two `Log.e` (its only `android.*` import) sit alongside. Extract a `ShelfBatchInserter` implemented by a small `@Inject` class (bind in RepositoryModule only if unblocked, else construct internally); surface errors instead of `Log.e`; keep `Lazy` if a cycle exists.
- **Opus A verified:** the impl injects `dagger.Lazy<TransactionSyncManager>` alongside four repositories and builds `shelfDispatchMap` inline; `Log.e` at two sites.
- **Opus A do:** extract the map behind a small `ShelfBatchInserter` seam and surface errors instead of logging them.
- **Opus A risk:** the task's own fallback ("construct internally if `RepositoryModule` is PR-blocked") undercuts the point of the extraction, and swapping `Lazy` for direct injection risks an init cycle. Low confidence as specified.

### 77. Make resource-filter signatures order-independent without sorting
votes 5/5 · consensus avg 61 · spread 17 · Devin 60 · Opus A 72 · raw: codex r1 #4
files: ui/resources/ResourcesListFilter.kt (Signature L18-20, toSignature L90-92), test

`toSignature` maps tag ids and `.sorted()` them every time inputs are checked — O(t log t) + allocation on an interactive path. Store `searchTagIds` as a `Set` built directly from `searchTags`; keep defensive copies; regression-test that reorder alone doesn't refilter. ⚠️ File is in open PR #17187's scope — re-check before starting.
- **Opus A verified:** `toSignature` does `searchTags.map { it.id }.sorted()` — an allocation plus `O(t log t)` on an interactive path, though tag order is irrelevant to matching.
- **Opus A do:** represent `searchTagIds` as a `Set` built directly from `searchTags`.
- **Opus A risk:** low; keep the defensive copies of the other mutable sets. Note `ResourcesListFilter.kt` is owned by open PR 17187 — re-check before starting.

### 78. Push notification display formatting from the ViewModel into repository DTOs ⚠ disputed
votes 5/5 · consensus avg 61 · spread 31 · Devin 72 · Opus A 52 · raw: grok r1 #7
files: repository/NotificationsRepository.kt, repository/NotificationsRepositoryImpl.kt, ui/notifications/NotificationsViewModel.kt, two test files

`NotificationsViewModel` holds `@ApplicationContext` and applies `context.getString(R.string.*)` in `formatNotification` (~L267-340) to build display strings, though the repo already has `resolveType`. Extend the enriched DTO so the VM maps ready-to-bind fields and drops Context/R.string; keep grouping/selection state in the VM. ~5 files.
- **Opus A verified:** the ViewModel calls `getEnrichedNotifications` then re-applies `Context` + `R.string` in `formatNotification`.
- **Opus A do:** produce ready-to-bind fields from the repository so the ViewModel drops `Context`.
- **Opus A risk:** this pushes `R.string` *into* the data layer, which is the opposite of the `SurveysRepository` task in this same backlog — the two encode contradictory policies. Pick one direction repo-wide before scheduling either. Also collides with two other tasks in `NotificationsRepositoryImpl`.
- **Merged reading:** Pushes string resources into the data layer, the opposite policy from the Surveys raw-values task. Decide the policy before touching either.

## Tier 3 — kept by 3 or 4 plans, or unanimous but rated under 60 (28 tasks)

Do these only when the file is already open for a Tier 1 or 2 task.

### 79. Replace printStackTrace() with structured logging in VoicesLabelManager
votes 5/5 · consensus avg 59 · spread 24 · Devin 62 · Opus A 63 · raw: kimi r1 #10
files: services/VoicesLabelManager.kt (L56, ~L91)

Background label-write failures go to stderr untagged. Add `private const val TAG` in the existing companion and use `Log.w(TAG, "addLabel failed", e)` / `"removeLabel failed"`; keep toast + retry semantics. ~6 lines, 1 file.
- **Opus A verified:** `printStackTrace()` on the addLabel and removeLabel failure paths — background write failures invisible to logcat filtering and crash triage.
- **Opus A do:** replace with tagged `Log.w`; keep the toast and retry semantics.
- **Opus A risk:** low.

### 80. Drop the redundant distinct pass when ordering notification groups ⚠ disputed
votes 5/5 · consensus avg 59 · spread 26 · Devin 63 · Opus A 70 · raw: codex r1 #8
files: ui/notifications/NotificationsViewModel.kt (buildNotificationGroups L201-217), test

`(TYPE_ORDER.filter{grouped.containsKey} + grouped.keys.filter{it !in TYPE_ORDER}).distinct()` — the two inputs are disjoint and each already unique; `distinct()` allocates a set per emission for no effect. Remove it; keep ordering/labels. ~20 lines, 2 files.
- **Opus A verified:** `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()` — the second list explicitly excludes everything in the first, and both sides hold unique keys, so the dedup pass is provably a no-op.
- **Opus A do:** drop the `distinct()`.
- **Opus A risk:** low; retain `TYPE_ORDER` precedence and the iteration order for unknown types.

### 81. Strip null team JSON fields without allocating key snapshots
votes 5/5 · consensus avg 59 · spread 18 · Devin 66 · Opus A 65 · raw: codex r1 #10
files: model/MyTeam.kt (serialize L156-224), test

Both branches filter `keySet()` into a `keysToRemove` list then traverse it again — two passes + an allocation on a sync/upload serialization path. Use iterator-based in-place removal in one pass (iterator remove, not mutate-during-iterate); apply to both branches. ~35 lines, 2 files.
- **Opus A verified:** the filter-then-remove pair appears twice — lines 176/177 and 222/223 — allocating a key list and traversing twice in a function that runs throughout sync and upload.
- **Opus A do:** remove nulls in one iterator pass; apply the same shape to both branches.
- **Opus A risk:** low, but use iterator removal — mutating the `JsonObject` while iterating its `keySet()` view otherwise throws.

### 82. Fewer allocations in TTSManager.stripMarkdown ⚠ disputed
votes 5/5 · consensus avg 57 · spread 29 · Devin 63 · Opus A 56 · raw: grok r2 #9
files: utils/TTSManager.kt (L88-111), test

`stripMarkdown` chains ~10 `.replace(Regex, …)` — each allocates a new string even with no match. Add a cheap `contains` pre-check fast path for plain text, and combine order-safe replacements; preserve stripping behavior for all constructs. ~2 files.
- **Opus A verified:** ten `.replace(Regex, …)` calls in sequence; the regexes are precompiled, so the remaining cost is intermediate strings and repeated full scans.
- **Opus A do:** add a cheap `contains` pre-check fast path for text with no markdown markers.
- **Opus A risk:** the "combine compatible replacements" half is where stripping behaviour breaks; keep the ordering and take only the fast path unless tests cover every marker.

### 83. Eliminate the temporary candidate list when choosing the next download
votes 5/5 · consensus avg 57 · spread 10 · Devin 58 · Opus A 59 · raw: codex r1 #2
files: services/DownloadService.kt (getNextUrl L629-639), test

`getNextUrl` runs `.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()` — a full intermediate list per queue pop. Single-pass minimum selection (or lazy sequence); keep both predicates, lexicographic ordering, `isPriority`. ~20 lines, 2 files.
- **Opus A verified:** `urls.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()` — a full intermediate list per queue pop.
- **Opus A do:** use a lazy sequence or a single-pass minimum.
- **Opus A risk:** low; preserve both eligibility predicates and the lexicographic ordering.

### 84. Memoize the re-parsing valueChoicesArray getter in Answer ⚠ disputed
votes 5/5 · consensus avg 55 · spread 42 · Devin 72 · Opus A 57 · raw: devin r1 #4
files: model/Answer.kt (getter L23-34)

`valueChoicesArray` rebuilds a `JsonArray` and runs `gson.fromJson` per element on every access — hit once per answer inside `createObject` on the submission-upload path. Add a private `@Ignore` cache keyed on the `valueChoices` list it was built from; keep null/empty behavior identical. ~15 lines, 1 file.
- **Opus A verified:** the `@get:Ignore` getter builds a fresh `JsonArray` and runs `gson.fromJson` per element on every read, and it is hit per answer on the upload path.
- **Opus A do:** memoise against the `valueChoices` list it was built from.
- **Opus A risk:** caching derived state on a mutable Room entity is the hazard — the invalidation check must be honest, or this reintroduces the staleness bug the `Feedback` task fixes.
- **Merged reading:** Opus B rated it 30 because the getter has only two call sites that each read once. Low priority.

### 85. Stop allocating a Typeface on every group-header bind ⚠ disputed
votes 5/5 · consensus avg 53 · spread 36 · Devin 71 · Opus A 48 · raw: devin r1 #10
files: ui/chat/ChatShareTargetAdapter.kt (GroupViewHolder.bind L51)

`setTypeface(null, Typeface.BOLD)` allocates a `Typeface` via `Typeface.create` per header rebind. Set it once in `init`/first bind using `setTypeface(listTitleTextView.typeface, Typeface.BOLD)`. ~5 lines, 1 file.
- **Opus A verified:** `listTitleTextView.setTypeface(null, Typeface.BOLD)` at line 51, which routes through `Typeface.create` on every rebind.
- **Opus A do:** set the bold typeface once in the ViewHolder `init`.
- **Opus A risk:** trivial change, negligible payoff — group headers are few.

### 86. Resolve the lazy teams repository once per upload result batch ⚠ disputed
votes 5/5 · consensus avg 50 · spread 33 · Devin 57 · Opus A 45 · raw: codex r1 #7
files: services/upload/UploadConfigs.kt (L95-113), test

`teamsSyncRepository.get()` is called inside each result predicate for `TeamTask` and `TeamActivities` `markUploaded` lambdas. Resolve once at the top of each lambda; keep lazy injection (cycle safety) and sequential suspend calls. ~30 lines, 2 files.
- **Opus A verified:** `teamsSyncRepository.get()` at lines 89, 97, 105, 106 and 110; the calls at 97 and 110 sit inside result loops.
- **Opus A do:** resolve once at the start of each `markUploaded` lambda.
- **Opus A risk:** correct, but Dagger's `Lazy.get()` is a cheap field read after first call — this is readability, not measurable performance. Keep the `Lazy` to avoid reintroducing a dependency cycle.

### 87. Optimize the areAllSelected count in CoursesAdapter
votes 5/5 · consensus avg 49 · spread 17 · Devin 52 · Opus A 44 · raw: jules r1 #5
files: ui/courses/CoursesAdapter.kt (L164)

`currentList.count { isMyCourseLib || !it.isMyCourse }` iterates per selection; when `isMyCourseLib` is true the count is just `currentList.size`. Short-circuit: `if (isMyCourseLib) currentList.size else currentList.count { !it.isMyCourse }`. ~3 lines, 1 file.
- **Opus A verified:** `currentList.count { isMyCourseLib || !it.isMyCourse }` — when `isMyCourseLib` is true the predicate is constant, so the scan is `currentList.size`.
- **Opus A do:** branch on `isMyCourseLib` before counting.
- **Opus A risk:** trivially correct, negligible payoff.

### 88. Replace mutable-list target in TeamCalendarFragment date mapping ⚠ disputed
votes 5/5 · consensus avg 44 · spread 37 · Devin 50 · Opus A 42 · raw: jules r1 #8
files: ui/teams/TeamCalendarFragment.kt (L215)

`meetups.mapTo(mutableListOf()) { … }` — plain `meetups.map` feeds `eventDates.addAll` identically. ~2 lines, 1 file.
- **Opus A verified:** `meetups.mapTo(mutableListOf()) { … }` feeding `eventDates.addAll(newDates)`.
- **Opus A do:** use `map`.
- **Opus A risk:** identical allocation behaviour; this is style, not performance. Ship only alongside other work in the file.

### 89. Memoize the derived messageList getter in Feedback ⚠ disputed
votes 4/5 · consensus avg 68 · spread 27 · Devin 58 · Opus A 85 · raw: devin r1 #9
files: model/Feedback.kt (parsedMessages L42-48, messageList L55+)

`messageList` rebuilds `List<FeedbackReply>` objects on every access though `parsedMessages` caches the `JsonArray`. Extend caching to the derived list. NOTE: the doc's "setMessages leaves cachedMessages stale" claim is wrong — `setMessages` assigns `this.messages`, whose setter already nulls `cachedMessages` (verified); only the memoization part applies. ~20 lines, 1 file.
- **Opus A verified:** `parsedMessages` caches the `JsonArray` but the derived `List<FeedbackReply>` is rebuilt per access, and `setMessages(JsonArray)` reassigns `messages` without invalidating `cachedMessages`.
- **Opus A do:** cache the derived list alongside the raw parse and invalidate both wherever `messages` is reassigned.
- **Opus A risk:** the stale-cache half is a correctness fix, not just allocation. Confirm every write path to `messages` before caching harder.
- **Merged reading:** Only the memoization half is real. The `messages` setter already clears the cache, so the stale-cache claim (repeated in Opus A) is false.

### 90. Hoist constant string lookups out of adapter bind paths (3 adapters)
votes 4/5 · consensus avg 64 · spread 18 · Devin 74 · Opus A 71 · raw: claude r1 #7
files: ui/teams/members/MembersAdapter.kt (L77/111/121), ui/teams/courses/TeamCoursesAdapter.kt (L62), ui/enterprises/EnterprisesReportsAdapter.kt (L65)

Per-row `context.getString` for strings that never vary: `team_leader`/`no_visit`, `remove`, and `team_financial_report` (teamName is a constructor param). Hoist into `by lazy` fields, matching TeamsSelectionAdapter's existing pattern. ~15 lines, 3 files.
- **Opus A verified:** `MembersAdapter` resolves `R.string.team_leader` at lines 77 and 121 and `R.string.no_visit` at 111; `TeamCoursesAdapter:62` resolves `R.string.remove`; `EnterprisesReportsAdapter:65` formats `R.string.team_financial_report` with a constructor-fixed `teamName`. `TeamsSelectionAdapter` already caches exactly this way.
- **Opus A do:** hoist each into a `by lazy` field.
- **Opus A risk:** low, but `ui/teams/**` is heavily contested by open PRs — re-check before starting.

### 91. Cache per-row date strings in UserArrayAdapter
votes 4/5 · consensus avg 64 · spread 9 · Devin 68 · Opus A 68 · raw: kimi r1 #2
files: ui/user/UserArrayAdapter.kt (L62)

`TimeUtils.formatDate(user.joinDate)` per bind; `joinDate` is immutable per row. `HashMap<Long,String>` + `getOrPut` (key `?: 0L` if nullable). ~5 lines, 1 file.
- **Opus A verified:** `onBindViewHolder` calls `TimeUtils.formatDate(user.joinDate)` per bind; `joinDate` is immutable per row.
- **Opus A do:** same date-cache treatment.
- **Opus A risk:** low.

### 92. Remove static MainApplication.showDownload in TeamDetailFragment
votes 4/5 · consensus avg 64 · spread 18 · Devin 68 · Opus A 60 · raw: jules r2 #5
files: ui/teams/TeamDetailFragment.kt (L311/318), MainApplication.kt (L115)

A static coordinates page-switch UI state. Remove from MainApplication; keep as a private fragment property. ~10 lines, 2 files.
- **Opus A verified:** declared at `MainApplication.kt:115` and touched at exactly two sites — `TeamDetailFragment:311` (`= true`) and `:318` (`= false`). **No reader anywhere.** Jules describes it as coordinating page switches; it coordinates nothing.
- **Opus A do:** delete the static and both assignments outright rather than relocating the field.
- **Opus A risk:** low, but grep once more for reflective or generated access before deleting. `ui/teams/**` is PR-contested.
- **Merged reading:** Nothing reads `showDownload`. Delete the property and both assignments; do not relocate it into the fragment as Devin's text says.

### 93. Replace printStackTrace() in News model accessors
votes 4/5 · consensus avg 64 · spread 12 · Devin 60 · Opus A 64 · raw: kimi r1 #8
files: model/News.kt (L146/163/200/235/242)

Five `printStackTrace()` calls run during feed diffing/sorting. Replace with `Log.w(TAG, "…", e)` — note this adds an `android.util.Log` import to a model class; acceptable trade vs stderr spam, but flag if the KMP core needs model/ platform-free soon.
- **Opus A verified:** `printStackTrace()` in `isCommunityNews`, `calculateSortDate`, `createNews` and the conversation-parse catches — all on the voices list/sort path.
- **Opus A do:** replace with tagged `Log.w`; leave parsed results and fallbacks unchanged.
- **Opus A risk:** low.

### 94. Cheap path helpers and existence+length caching in FileUtils
votes 4/5 · consensus avg 63 · spread 20 · Devin 74 · Opus A 62 · raw: grok r2 #2
files: utils/FileUtils.kt (getFileExtension L197, checkFileExist L166-169), utils/FileExistenceCache tests

`getFileExtension(address)` allocates a `File` solely for a string suffix; `checkFileExist` runs `getSDPathFromUrl` + `exists()` + `length() > 0` unmemoized, while `FileExistenceCache.exists` caches bare `exists()` only. Implement the extension with string ops; extend the cache (or a flag) so "present" means `exists && length > 0`, TTL-keyed by absolute path; keep `checkFileExist`'s signature. ~3 files.
- **Opus A verified:** `getFileExtension` is `address?.let { File(it).extension.lowercase() } ?: ""`.
- **Opus A do:** use `substringAfterLast` string operations, matching the current empty/null behaviour exactly.
- **Opus A risk:** low for the extension half. Grok bundles a second change — extending `FileExistenceCache` to mean `exists && length > 0` — which alters shared cache semantics; split that out and treat it separately.

### 95. ChatHistoryAdapter DiffUtil and bind micro-costs ⚠ disputed
votes 4/5 · consensus avg 54 · spread 34 · Devin 45 · Opus A 60 · raw: grok r2 #10
files: ui/chat/ChatHistoryAdapter.kt (L44-49/110-130), test

`areContentsTheSame` compares only `conversations?.firstOrNull()?.query` — verify it doesn't miss title/`_rev`/`lastUsed` changes (a correctness gap more than a perf one). On bind, read first-query once per side; only copy `conversations` inside the click (snapshot at click time); check `PAYLOAD_CHAT_SHARED` still avoids full rebind. Premise is weak — scope to whatever the audit actually finds.
- **Opus A verified:** `areContentsTheSame` evaluates `conversations?.firstOrNull()?.query` per side; the bind path resolves the first query again.
- **Opus A do:** read the first query once per side into locals; avoid re-copying the conversation list outside the click handler.
- **Opus A risk:** low, but grok's own write-up concedes the click-time `toList()` is already correct — the remaining win is small and partly speculative.

### 96. Replace lowercase-first map lookup in StorageCategories.indexOf
votes 4/5 · consensus avg 53 · spread 13 · Devin 55 · Opus A 46 · raw: jules r1 #1
files: ui/settings/StorageCategories.kt (L32)

`indexOf` calls `extension.lowercase()` on every lookup, allocating per call. Try `extensionToIndex[extension] ?: extensionToIndex[extension.lowercase()] ?: OTHER_INDEX`. ~2 lines, 1 file.
- **Opus A verified:** `extensionToIndex[extension.lowercase()] ?: OTHER_INDEX` — one allocation per call.
- **Opus A do:** try the raw key first and fall back to the lowercased form.
- **Opus A risk:** correct but marginal; only pays off when callers already pass lowercase extensions.

### 97. Avoid sequence overhead in the valid log file check
votes 4/5 · consensus avg 48 · spread 21 · Devin 50 · Opus A dropped it · raw: jules r1 #4
files: utils/CrashLogStore.kt (L43-44)

`pendingFiles.asSequence().filter{isValidLogFile(it)}.take(MAX).count()` instantiates a sequence for an early-exit count. Iterate and count until `MAX_PENDING_FILES`, preserving the `>= MAX` guard below. ~3 lines, 1 file.

### 98. Remove the ArrayList pre-allocation overhead in DownloadUtils ⚠ disputed
votes 4/5 · consensus avg 44 · spread 28 · Devin 50 · Opus A dropped it · raw: jules r1 #7
files: utils/DownloadUtils.kt (downloadAllFiles L137-139)

`dbMyLibrary.mapTo(ArrayList()) { … }` — plain `map` suffices, or pre-size `ArrayList(dbMyLibrary.size)`. ~2 lines, 1 file.

### 99. Cache the HTML cover-image directory scan in FileUtils
votes 3/5 · consensus avg 70 · spread 19 · Devin 82 · Opus A dropped it · raw: claude r1 #4
files: utils/FileUtils.kt (findHtmlCoverImage L142-163)

`findHtmlCoverImage` walks a resource dir to depth 4, stats and lowercases every name, once per bind of every HTML resource row (InlineResourceAdapter L254, ResourcesAdapter L413). Add a small `LruCache` keyed on `absolutePath + lastModified` (negative results cached too), matching the existing `FileExistenceCache`/`PdfThumbnailLoader` precedent. ~25 lines, 1 file.

### 100. Batch the storage scan into a single pass in StorageBreakdownViewModel ⚠ disputed
votes 3/5 · consensus avg 68 · spread 26 · Devin 55 · Opus A 81 · raw: kimi r1 #9
files: ui/settings/StorageBreakdownViewModel.kt (scanStorage L86-94)

`walkTopDown().filter { it.isFile }.forEach { … }` adds intermediate sequence stages per file. Iterate the walk directly with an `isFile` guard and compute ext/index/size in one block. Alternative: the rating-80 task moves this scan into the repository — pick one, don't do both.
- **Opus A verified:** `StorageBreakdownViewModel:94` runs `oleDir.walkTopDown().filter { it.isFile }.forEach { … }`, duplicating the identical traversal already inside `ResourcesRepositoryImpl.getOfflineResourceItems` (~860). The settings screen therefore walks the tree once for the summary and again when a category opens, and this ViewModel is the only one in `ui/` still doing its own file I/O.
- **Opus A do:** prefer claude's variant — move the scan into `ResourcesRepository` behind a `getStorageBreakdown(...)` returning a small data class, and have the ViewModel call it. Kimi's variant only removes the intermediate sequence stages inside the ViewModel, which leaves the layering violation and the double walk in place.
- **Opus A risk:** moderate (~120 lines, 5 files including moved tests). Keep the caller passing the `ole` path, as `getOfflineResourceItems` already does.

### 101. Stop static NetworkUtils.getDeviceName() in Personals upload serialization
votes 3/5 · consensus avg 66 · spread 15 · Devin 68 · Opus A 73 · raw: grok r1 #1
files: repository/PersonalsRepositoryImpl.kt (L110-111), test

`serialize` injects `DeviceNameProvider` for `customDeviceName` but still calls `NetworkUtils.getDeviceName()` for `deviceName`. Route both fields through the provider — note: the provider currently exposes only `getCustomDeviceName()`; extend it (or an equivalent injectable) for the device-name value rather than keeping the static call.
- **Opus A verified:** line 110 is `NetworkUtils.getDeviceName()` while line 111 is `deviceNameProvider.getCustomDeviceName()` — the provider is already injected (line 23) and used one line below.
- **Opus A do:** source both JSON fields from the provider.
- **Opus A risk:** confirm `DeviceNameProvider` exposes a plain device-name accessor; `getDeviceName()` and `getCustomDeviceName()` return different values, so this is not a like-for-like swap if the provider lacks the former.

### 102. Move LifeCache out of the repository package
votes 3/5 · consensus avg 64 · spread 11 · Devin 66 · Opus A 67 · raw: grok r1 #2
files: repository/LifeCache.kt → data/cache/LifeCache.kt, repository/LifeRepositoryImpl.kt (import), test

A `@Singleton` SharedPreferences cache sitting under `repository/` keeps `android.content.SharedPreferences` in the repository package and blurs boundaries. Relocate file + package; update the single consumer import and test package. No module edit (constructor `@Inject` unchanged).
- **Opus A verified:** `LifeCache` is a `@Singleton` SharedPreferences-backed cache sitting under `repository/`, keeping `android.content.SharedPreferences` in that package.
- **Opus A do:** move the file and update the single consumer import and the test package.
- **Opus A risk:** very low — constructor and `@Inject` stay identical, so Hilt needs no module change. Sequence against the `LifeCache` memoisation task.

### 103. Give the Teams* split interfaces real consumers (narrow ViewModel injections)
votes 3/5 · consensus avg 53 · spread 17 · Devin 60 · Opus A 55 · raw: kimi r2 #10
files: pick after per-file grep — e.g. ui/voices/VoicesViewModel.kt, ui/community/CommunityServicesViewModel.kt (verified to inject wide TeamsRepository)

`TeamsRepository` extends `TeamsFinancesRepository`/`TeamsMembersRepository`/`TeamsNotificationsRepository`, but many VMs still inject the wide interface (verified examples: VoicesViewModel L35, CommunityServicesViewModel L18, plus team/chat/dashboard files mostly PR-owned). NOTE: the doc's named examples are stale — EnterprisesFinancesViewModel already injects the narrow interface and EnterprisesViewModel doesn't inject TeamsRepository at all. Re-grep each candidate, narrow constructor types where the used methods map to one parent, verify `@Binds` coverage.
- **Opus A verified:** `TeamsRepository` extends `TeamsFinancesRepository`, `TeamsMembersRepository` and `TeamsNotificationsRepository`, but consumers still inject the whole interface.
- **Opus A do:** narrow each ViewModel's constructor type to the smallest parent it actually uses.
- **Opus A risk:** the task is written as an investigation ("pick after verifying actual usage"), not a specified change, and it depends on each parent having a `@Binds` in the PR-owned `RepositoryModule`. Scope it concretely before scheduling.

### 104. Change filteredExistingUsers mapTo(HashSet) to mapNotNullTo
votes 3/5 · consensus avg 51 · spread 17 · Devin 42 · Opus A dropped it · raw: jules r1 #3
files: repository/TeamsRepositoryImpl.kt (L937, L970)

`mapTo(HashSet()) { it.name }` twice — claimed to "include nulls", though `it.name` is effectively non-null; `mapNotNullTo` is equivalent cleanup, not a bug fix. ⚠️ TeamsRepositoryImpl is heavily PR-owned — collision risk. ~2 lines, 1 file.

### 105. Remove the redundant size/isEmpty reads in BaseDashboardFragment
votes 3/5 · consensus avg 44 · spread 10 · Devin 48 · Opus A 38 · raw: jules r1 #9
files: base/BaseDashboardFragment.kt (L189-192)

`filteredCourses.size` for `setCountText` then `isEmpty()` right after — cache `val count = filteredCourses.size` and reuse for both checks. ~4 lines, 1 file.
- **Opus A verified:** `filteredCourses.size` at line 191 then `filteredCourses.isEmpty()` at 192.
- **Opus A do:** hoist a `count` local if touching the file anyway.
- **Opus A risk:** both are O(1) reads on a `List` — the stated "repeated list traversals" does not happen. Cosmetic only.

### 106. Cache filteredList.size in ResourcesFragment.applyFiltersAndUpdateUI
votes 3/5 · consensus avg 43 · spread 13 · Devin 48 · Opus A 35 · raw: jules r1 #2
files: ui/resources/ResourcesFragment.kt (~L412)

`filteredList.size` read repeatedly in sequence (`checkList`, `showNoData`, count text). Extract a `val listSize`. ~4 lines, 1 file.
- **Opus A verified:** `filteredList.size` on three consecutive lines (412-414).
- **Opus A do:** hoist into a local if the file is open for other reasons.
- **Opus A risk:** `List.size` is a field read; the claimed waste is not real. Lowest-value item in the backlog, and the file is owned by open PR 17187.

## Dropped — kept by 2 plans or fewer (2 tasks, plus Kimi r1 #6 which no plan kept)

- **Optimize the passed-steps collection in ProgressRepositoryImpl** (votes 2/5, Devin 40) — raw: jules r1 #6
- **Extract the member size call in MembersFragment** (votes 2/5, Devin 48) — raw: jules r1 #10
- The four Codex-only tasks (three Jules COUNT-query items and the NotificationDao `IS` predicate task) are invalid on code inspection and appear in no other plan; they have no Devin entry and are not listed above.

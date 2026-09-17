# myPlanet refactor backlog — merged, verified, rated

90 tasks merged from 12 agent lists (120 raw). Every premise below was re-checked against the
working tree at `c33390b`; 17 raw tasks whose premise did not survive were dropped.

Rating = `40% evidence quality + 35% impact + 25% risk-adjusted feasibility`, scored 1–100.
`from:` records every agent that proposed the task.

---
## 93 — Fix the per-team task badge in `getTeamNotifications`
**from:** devin swe 2 · **files:** `repository/NotificationsRepositoryImpl.kt`
**verified:** `val hasTask = tasks.isNotEmpty()` is computed once *before* the `for (teamId in teamIds)` loop and the same boolean is passed into every `TeamNotificationInfo`. Any team with a due task lights the badge on all teams. `tasks` rows already carry `teamId`.
**do:** Replace with `val taskTeamIds = tasks.mapNotNull { it.teamId }.toSet()` before the loop and `val hasTask = teamId in taskTeamIds` inside it. Leave the `chatCountsById` lookup alone. ~6 lines, no DAO change.

---
## 90 — Stop `MyLibrary.insertMyLibrary` duplicating attachments on every re-sync
**from:** devin swe 2 · **files:** `model/MyLibrary.kt`
**verified:** `attachmentList` is seeded from `this.attachments?.toMutableList()` (existing rows), then `attachmentList.add(...)` runs unconditionally per attachment and the result is written back. On re-sync of an existing doc the array grows without bound; each new `Attachment` gets a fresh UUID so nothing dedupes. Separately `params.spm.getCouchdbUrl()` is read from SharedPreferences inside the `entrySet().forEach`.
**do:** Hoist the couchdb base URL to a local before the loop; capture existing attachment names into a set and skip `add` for keys already present. Keep the `key.indexOf("/") < 0` guard and the offline-flag logic.

---
## 84 — `SyncTimeLogger`: delete the write-only accumulator, make the per-key lists thread-safe
**from:** claude opus 5, devin swe 2 · **files:** `utils/SyncTimeLogger.kt`
**verified:** `detailedLogs` appears exactly 3× — declared (38), cleared (73), appended (195) — and is never read, so 12 `logDetail` call sites retain strings for the whole session for nothing. `apiCallTimes`/`dbOperationTimes` are `ConcurrentHashMap` but appended via non-atomic `getOrPut { mutableListOf() }` (167, 184) from parallel sync coroutines and iterated unguarded in `generateSummary`.
**do:** Remove `detailedLogs` and its clear; keep `logDetail`'s verbose-log branch and its signature. Replace the two `mutableListOf()` defaults with `Collections.synchronizedList(...)` and wrap the summary's reads in `synchronized(logs)`. Devin's variant also replaces `extractProcessName`'s `split("/")` — only safe if the trailing-empty/`?`-segment handling is preserved.

---
## 83 — Feedback list: drop the realtime-sync restart that fights the repository Flow
**from:** codex sol 5.6 · **files:** `ui/feedback/FeedbackListFragment.kt`, `ui/feedback/FeedbackListViewModel.kt`
**verified:** `loadFeedback` already collects `feedbackRepository.getFeedback(user)` continuously. The fragment nonetheless implements `RealtimeSyncMixin` (24), injects `RealtimeSyncManager` (29) and on `onDataUpdated` (99) calls `refreshFeedback()` → `loadFeedback()`, which does `fetchJob?.cancel()` and restarts the very Flow that would have re-emitted anyway.
**do:** Remove the mixin, the injected manager, the watched-table methods and the setup call. Keep one long-lived collection; retain `refreshFeedback` only for the explicit `OnChangedListener` path and make it unable to spawn parallel collectors.

---
## 83 — Answer "already shared?" with SQL `EXISTS` instead of hydrating News rows
**from:** claude opus 5 · **files:** `data/room/dao/NewsDao.kt`, `repository/VoicesRepositoryImpl.kt`
**verified:** `isAlreadyShared` does `newsDao.getByNewsId(chatId).any { ... viewIn?.contains(...) }`; the DAO query is `SELECT * FROM news WHERE newsId = :chatId`, so every matching row's `message`/`images`/`viewIn` blobs are read and converted to produce a Boolean. `getByNewsId` has exactly one caller, so it can be replaced outright.
**do:** Replace with `SELECT EXISTS(SELECT 1 FROM news WHERE newsId = :chatId AND viewIn LIKE :viewInPattern ESCAPE '\')`; build the pattern as `%"_id":"<escaped>"%`, escaping `\`, `%`, `_` as the other repositories already do. No schema or index change.

---
## 82 — `SyncManager`: remove the resource-id list that is filtered twice and guarded by an always-true test
**from:** claude opus 5 · **files:** `services/sync/SyncManager.kt`
**verified:** `newIds` is declared `MutableList<String?>` (294) but the only thing added is `validIds` (376), already `filter { it.isNotBlank() }`. Line 411 re-filters the whole list, 414 guards on `validNewIds.size == newIds.size` (always true), and 420 logs `newIds.size - validNewIds.size` (always 0).
**do:** Type it `MutableList<String>`, delete `validNewIds`, pass `newIds` straight to `removeDeletedResources`, reduce the guard to `newIds.isNotEmpty()`, and log `newIds.size`. Keep the `hadBatchFailure` branch untouched.

---
## 81 — Drop the redundant `SharedPrefManager` parameter from `ConfigurationsRepository.checkVersion`
**from:** claude opus 5 · **files:** `repository/ConfigurationsRepository.kt`, `ConfigurationsRepositoryImpl.kt`, `ui/sync/SyncActivity.kt`, `ui/sync/LoginActivity.kt`, `services/AutoSyncWorker.kt`, + test
**verified:** The interface takes `spm: SharedPrefManager` (15) while the impl already injects its own (49) and uses it in the same function (108, 113); the param is only forwarded to `UrlUtils.getUpdateUrl(spm)` (487). Three call sites hand one over: SyncActivity 233, LoginActivity 183, AutoSyncWorker 69.
**do:** Reduce the signature to `checkVersion(callback)`, drop the `services.SharedPrefManager` import from the interface, and use the injected field throughout the impl. Update the three call sites and the three test invocations.

---
## 81 — Load only id+title for the achievement resource picker
**from:** devin swe 2 · **files:** `data/room/dao/MyLibraryDao.kt`, `repository/ResourcesRepository.kt`, `ResourcesRepositoryImpl.kt`, `ui/user/AchievementViewModel.kt`, `ui/user/EditAchievementFragment.kt`
**verified:** `showResourceDialog` calls `viewModel.getAllLibraries()` → `resourcesRepository.getAllLibraries()`, materialising every `my_library` row as a full entity for a checkbox list that reads only `title`. That VM/fragment pair are its only consumers (`getAllLibrariesToSync` elsewhere is a different method). `ResourceTitleProjection` (MyLibraryDao:175) is the precedent and `getLibraryItemsByIds` already exists.
**do:** Add an `IdTitleProjection` query with no `ORDER BY`, replace `getAllLibraries` on the interface, and refetch only the confirmed ids for `serializeResource()`.

---
## 78 — Delete the four unused resource-open overloads from `ActivitiesRepository`
**from:** claude opus 5 · **files:** `repository/ActivitiesRepository.kt`, `ActivitiesRepositoryImpl.kt`
**verified:** `getResourceOpenCount(userName)`, `getResourceOpenCount(userName, type)`, `getMostOpenedResource(userName)`, `getMostOpenedResource(userName, type)` (35–38) have no callers outside the impl, where `getProfileActivityStats` uses them internally (196, 198). The no-`type` overloads also drag `UserSessionManager.KEY_RESOURCE_OPEN` into the contract.
**do:** Delete the four declarations; drop `override` in the impl so they stay plain members. Tests construct the concrete class, so no test edit. Leave the DAO and `getProfileActivityStats` alone.

---
## 77 — Move the storage-breakdown disk scan out of the ViewModel into `ResourcesRepository`
**from:** claude opus 5 · **files:** `repository/ResourcesRepository.kt`, `ResourcesRepositoryImpl.kt`, `ui/settings/StorageBreakdownViewModel.kt`, + 2 tests
**verified:** `StorageBreakdownViewModel.scanStorage` (86) runs `oleDir.walkTopDown().filter { it.isFile }.forEach` (94) — the *identical* line the repository already runs at `ResourcesRepositoryImpl:860` inside `getOfflineResourceItems`. The settings screen walks the tree twice and this VM is the only one in `ui/` still doing file I/O.
**do:** Add `getStorageBreakdown(oleDirPath, categoryExtensions, otherIndex)` returning a `StorageBreakdown` data class; lift the scan body verbatim into the impl under `withContext(dispatcherProvider.io)`; delete `scanStorage`/`ScanResult` from the VM. Move the two fixture-tree tests across.

---
## 77 — `SurveysRepository`: return raw counts and epochs, not pre-formatted strings
**from:** copilot kimi k3 · **files:** `model/SurveyInfo.kt`, `repository/SurveysRepositoryImpl.kt`, `ui/surveys/SurveysViewModel.kt`, `ui/surveys/SurveysAdapter.kt`
**verified:** `SurveysRepositoryImpl` formats `submissionCount` via `context.resources.getQuantityString(R.plurals.survey_taken_count, …)` (323) and imports `TimeUtils.formatDate` (36); `SurveyInfo` carries `submissionCount: String`, `lastSubmissionDate: String`, `creationDate: String` — all display strings baked in the data layer, so a locale change needs a re-query.
**do:** Make them `Int`/`Long`, delete the `getQuantityString`/`formatDate` calls from the repository, and move plural and date formatting into the adapter. Leave the survey-reminder prefs in that file alone.

---
## 76 — `NotificationsRepository`: remove the dead `getNotifications` and route timestamps through `TimeProvider`
**from:** claude opus 5 · **files:** `repository/NotificationsRepository.kt`, `NotificationsRepositoryImpl.kt`, + test
**verified:** `getNotifications` is declared on the interface (22) but the only caller is the impl's own `getEnrichedNotifications`. The impl injects `timeProvider` (32) yet constructs wall-clock values with bare `Date()` at 107, 115, 128, 136 and 461 — all five sites confirmed.
**do:** Delete the interface declaration and make the impl's override `private`. Replace each `Date()` with `Date(timeProvider.now())`, keeping the `doc.get("time")` fallback shape at 461. Add a test pinning `timeProvider.now()` through `markNotificationsAsRead`.

---
## 76 — Take `UserRepository` out of the two viewer activities
**from:** claude opus 5, devin swe 2 · **files:** `ui/viewer/ResourceViewerViewModel.kt`, `ResourcesExitCoordinator.kt`, `ResourceViewerActivity.kt`, `WebViewActivity.kt`, + test
**verified:** `ResourceViewerActivity:25/28` and `WebViewActivity:48/51` each field-inject `UserRepository` for the sole purpose of handing it to `ResourcesExitCoordinator` (11), whose only use is `userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }` (25). Both activities already hold the ViewModel.
**do:** Inject `UserRepository` into `ResourceViewerViewModel` instead. Devin's fuller variant also drops `userId` from `shouldShowResourceRatingDialog`/`setRatingPrompted`/`isRatingPrompted` so the VM resolves it internally — prefer that; claude's variant only adds a `getActiveUserId()` accessor.

---
## 75 — Delete the `getRatingsById` wrapper from `RatingsRepository`
**from:** claude opus 5 · **files:** `repository/RatingsRepository.kt`, `RatingsRepositoryImpl.kt`, `ui/resources/ResourceDetailFragment.kt`, + test
**verified:** The impl (20–22) is a null-guard over `getRatingSummary` on the next line. Exactly one caller outside the repository — `ResourceDetailFragment:268`; four other sites already call `getRatingSummary` directly.
**do:** Delete both declaration and implementation; in `onRatingChanged` guard with `val resourceId = library.resourceId ?: return@launch` then call `getRatingSummary`. Retarget the one test case.

---
## 75 — Stop `ChallengePrompter` reaching into `DashboardViewModel`
**from:** codex sol 5.6 · **files:** `services/ChallengePrompter.kt`, `ui/dashboard/DashboardViewModel.kt`
**verified:** A class in `services/` imports `DashboardViewModel` (8), holds one (13) and calls `calculateCommunityProgress`/`calculateIndividualProgress` at 41, 42, 55, 56 — service depending on UI, the wrong layer direction, and the calculation is deterministic and trivially reusable.
**do:** Extract the two calculations into a platform-free function/value object, have both the prompter and the ViewModel call it, and reduce `ChallengePrompter` to `DashboardActivity` + `SharedPrefManager`. Don't touch the dialog copy or the Spanish completion check.

---
## 74 — Tighten `FeedbackRepository`: drop the two internal-only builders, stop passing `UserEntity` to the list query
**from:** claude opus 5 · **files:** `repository/FeedbackRepository.kt`, `FeedbackRepositoryImpl.kt`, `ui/feedback/FeedbackListViewModel.kt`, + 2 tests
**verified:** `createFeedback` (8) and `saveFeedback` (29) have no callers outside the repository — they are only reached from `createAndSaveFeedback`. `getFeedback(userModel: UserEntity?)` (24) is marked `suspend` although the body only picks a DAO `Flow`, and it takes a whole entity to read `isManager()` and `name`.
**do:** Make the two builders `private`; change the signature to `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>` (no `suspend`) and drop the `UserEntity` import. The VM keeps its `getUserModel()` call and passes the two values.

---
## 74 — Carve `UserAchievementsRepository` out of the 57-member `UserRepository`
**from:** devin swe 2 · **files:** `repository/UserAchievementsRepository.kt` (new), `UserRepository.kt`, `di/UserAchievementsRepositoryModule.kt` (new), `services/upload/AchievementUploader.kt`
**verified:** `UserRepository` declares ~57 members; the achievement block is `achievementUpdates` (28) plus `initializeAchievement` (98), `updateAchievement` (99), `getAchievementData` (115), `getAchievementsForUpload` (116), `markAchievementUploaded` (117). `AchievementUploader` uses two of them. The `TeamsFinances/Members/NotificationsRepository` split is the in-repo precedent. (Devin says "45 members" — actual count is higher; the finding stands.)
**do:** Move the six members to a new interface, have `UserRepository` extend it, add a `@Provides` module, and narrow the uploader's injected type. `UserRepositoryImpl` needs no change.

---
## 73 — `CoursesAdapter.removeCourses`: constant-time id membership
**from:** codex sol 5.6 · **files:** `ui/courses/CoursesAdapter.kt`, + test
**verified:** `currentList.filter { it.courseId !in courseIds }` with `courseIds: List<String>` — O(n·m) on bulk removal.
**do:** Short-circuit the empty case through the existing `submitList` path, convert `courseIds` to a set once, keep `onComplete` firing only from the commit callback. Add retained/removed/duplicate-id coverage.

---
## 73 — Stop threading `ConfigurationsRepository` into `DialogUtils`
**from:** devin swe 2 · **files:** `utils/DialogUtils.kt`, `ui/sync/SyncActivity.kt`, `services/AutoSyncWorker.kt`
**verified:** `getUpdateDialog` (193) and `startDownloadUpdate` (213) take a `ConfigurationsRepository` purely to call `checkCheckSum(path)` (216) inside a UI helper; both callers already hold the repository (SyncActivity 130, AutoSyncWorker 42).
**do:** Replace the parameter with `checkCheckSum: suspend (String) -> Boolean`, pass `configurationsRepository::checkCheckSum` at both call sites, and drop the repository import from `DialogUtils`.

---
## 72 — Move the health-examination upload sequence into `HealthRepository`
**from:** claude opus 5 · **files:** `repository/HealthRepository.kt`, `HealthRepositoryImpl.kt`, `services/UploadToShelfService.kt`, + test
**verified:** `uploadHealth()` and `uploadSingleUserHealth()` each hand-roll the same fetch → `uploadHealthData` → `markHealthExaminationsUploaded` sequence, differing only in the fetch. Three interface methods stay public purely to let a service orchestrate a read-modify-write over one repository.
**do:** Add `syncPendingHealthExaminations()` / `…ForUser(userId)`, implement the sequence with an early return on empty, remove the three now-internal methods from the interface, and reduce each service body to one call — keeping the `appScope.launch`, the null guard and both listener messages verbatim.

---
## 72 — Scope `CoursesRepositoryImpl`'s pending-resource buffer to one sync run
**from:** copilot kimi k3 · **files:** `repository/CoursesRepositoryImpl.kt`, + test
**verified:** A `@Singleton` repository holds `Collections.synchronizedList(mutableListOf<PendingCourseResource>())` as a field (81–82), appended at 817 and drained under `synchronized` at 842–845. Process-wide mutable state shared across concurrent sync runs.
**do:** Thread the buffer as a local through the private parse/flush functions so one run's buffer cannot leak into another; keep the `withTransaction` boundaries identical. Add an interleaved two-batch test.

---
## 71 — Replace `printStackTrace()` with tagged `Log.w` in `TimeUtils`, `News` and `VoicesLabelManager`
**from:** copilot kimi k3 (submitted as three separate tasks) · **files:** `utils/TimeUtils.kt`, `model/News.kt`, `services/VoicesLabelManager.kt`
**verified:** 7 sites in `TimeUtils` (72, 115, 135, 158, 170, 201, 214), 5 in `News` (146, 163, 200, 235, 242 — all exactly as cited), 2 in `VoicesLabelManager` (56, 91). `TimeUtils.formatInstant` runs inside every row bind, so a parse failure prints a full stack trace per row per rebind; the others go to stderr untagged and invisible to logcat filtering.
**do:** Add a `TAG` per file and replace each call with `Log.w(TAG, "<fn> failed", e)`. Return values and fallbacks unchanged.

---
## 71 — Collapse the five repeated upload-await blocks in `UserDataWorker`
**from:** devin swe 2 · **files:** `services/UserDataWorker.kt`
**verified:** The `UPLOAD_TYPE_BULK` branch repeats `CompletableDeferred<Unit>` + `OnSuccessListener` + `withTimeoutOrNull(30000L)` five times (52–57, 61–67, 71–77, 83–89, 94–100), plus one same-shape block for `uploadToShelfService.uploadUserData`. The login branch's `CompletableDeferred<String?>` (29–35) is correctly excluded.
**do:** Add `private suspend fun awaitUploadCompletion(start: (() -> Unit) -> Unit)` and route the five (plus the shelf) blocks through it, keeping `uploadTeams()` after the resource await and `uploadHealth()` inside the shelf callback.

---
## 70 — `ChatSearch`: drop `ignoreCase` on already-normalized strings
**from:** claude opus 5, codex sol 5.6, copilot grok 4.5 · **files:** `utils/ChatSearch.kt`, + test
**verified:** `Utilities.normalizeText` lowercases with `str.lowercase(Locale.getDefault())` before stripping diacritics, and both query and candidate go through it. All four comparisons still pass `ignoreCase = true` (60, 63, 97, 99), forcing the case-folding `regionMatches` path on every keystroke. `ResourcesSearchUtils` already documents the no-`ignoreCase` convention.
**do:** Remove `ignoreCase = true` from the two `startsWith` and two `contains` calls; note the convention in a comment. Grok's fuller variant additionally replaces the four-bucket `+` concatenation (70) with a single `buildList` and avoids the duplicate `queryParts`/`normalizedQueryParts` mapping — worth folding in.

---
## 70 — Remove `MainApplication` statics from `ResourcesRepositoryImpl`
**from:** copilot grok 4.5 · **files:** `repository/ResourcesRepositoryImpl.kt`, + test
**verified:** `MainApplication.context.getExternalFilesDir(null)` at 399 and `MainApplication.applicationScope.launch` at 504 — Android globals inside a domain repository.
**do:** Resolve the `ole/$resourceId` path through the injected `StoragePathResolver`; replace the fire-and-forget scope with structured `withContext(dispatcherProvider…)` or an injected `@ApplicationScope CoroutineScope`. Surgical — do not redesign the class.

---
## 70 — `CourseDao`: replace the `@RawQuery` title search with a parameterized query
**from:** copilot kimi k3 · **files:** `data/room/dao/CourseDao.kt`, `repository/CoursesRepositoryImpl.kt`, + test
**verified:** `CourseDao` declares `@RawQuery … filterByTitleNormal(query: SupportSQLiteQuery)` (33–34) and `CoursesRepositoryImpl` string-builds the SQL and passes `SimpleSQLiteQuery` (6, 293). Raw SQL assembly in a repository.
**do:** Add `@Query("… courseTitleNormal LIKE '%' || :token || '%' ESCAPE '\\'")`, issue one call per normalized token and intersect by id in Kotlin, preserving the `\`/`%`/`_` escaping and the existing starts-with/contains ranking. Delete the raw query and both imports.

---
## 69 — Expose the current user through `RequestsUiState`
**from:** devin swe 2 · **files:** `ui/teams/members/RequestsViewModel.kt`, `RequestsFragment.kt`
**verified:** The fragment injects `UserSessionManager` (26), declares `lateinit var currentUser` (29), seeds it with `UserEntity()` in `onAttach` (37) and fetches it again in a coroutine (44) — while `RequestsViewModel.fetchMembers` already loads `userRepository.getUserModel()` and discards all but `user?.id`.
**do:** Add `currentUser: UserEntity? = null` last in `RequestsUiState`, populate it in `fetchMembers`, and set the adapter's user from the existing `collectWhenStarted(viewModel.uiState)` collector. Delete the injection and the standalone fetch.

---
## 68 — Cache per-row formatted dates in the four list adapters that re-format on every bind
**from:** copilot kimi k3 (submitted as four separate tasks) · **files:** `ui/personals/PersonalsAdapter.kt`, `ui/user/UserArrayAdapter.kt`, `ui/health/HealthUsersAdapter.kt`, `ui/feedback/FeedbackAdapter.kt`
**verified:** `PersonalsAdapter:38`, `UserArrayAdapter:62` and `HealthUsersAdapter:50` (reached from both the full bind and the `joinDate` payload path) each call a `TimeUtils` formatter per bind with no cache. `TeamsAdapter:28,62` already does exactly `dateCache.getOrPut(...)`, so the pattern is house style. Note: kimi's "double formatting" framing for `FeedbackAdapter` is wrong — line 62 already computes the date once and reuses it at 65 and 76; only the per-bind re-format applies there.
**do:** Add an adapter-level `HashMap<Long, String>` (not per-ViewHolder, so it survives recycling) and wrap each format call in `getOrPut`, keying on `?: 0L` where the field is nullable.

---
## 68 — Fetch one pending submission directly instead of materializing a list
**from:** codex sol 5.6 · **files:** `data/room/dao/SubmissionDao.kt`, `repository/SubmissionsRepositoryImpl.kt`
**verified:** `getSubmissionsByParentId(parentId, userId, "pending").firstOrNull()` at 456 and 541 hydrates a whole list to keep one row. `SubmissionDao:28` already has `getLatestPendingByUserAndParent` with `ORDER BY lastUpdateTime DESC LIMIT 1`.
**do:** Reuse that query where its ordering and nullable-`userId` semantics match; otherwise add one precisely ordered `LIMIT 1` query. Hydrate only the returned row; keep the no-pending fallback and the `lastUpdateTime` winner.

---
## 68 — Return a typed progress projection from `ProgressRepository`
**from:** claude opus 5 · **files:** `repository/ProgressRepository.kt`, `ProgressRepositoryImpl.kt`, `ui/courses/ProgressViewModel.kt`, + test
**verified:** `fetchCourseData` returns a raw `JsonArray` and `ProgressViewModel` decodes the CouchDB shape itself — `obj.get("courseId").asString`, `getAsJsonObject("progress")`, a `TypeToken<Map<String,Int>>` Gson parse — so the document format is known in the UI layer and a malformed doc throws. `CoursesProgressRow` already exists. Supersedes the micro-fix at rating 51.
**do:** Add `getCourseProgressRows(userId): List<CoursesProgressRow>`, move the mapping into the impl (skipping rows with a missing `courseId`/`courseName` rather than throwing), and reduce the VM to one assignment. Leave `fetchCourseData` in place for `DashboardViewModel`.

---
## 67 — Move the examiner lookup into `HealthExaminationState`
**from:** devin swe 2 · **files:** `ui/health/HealthExaminationViewModel.kt`, `HealthExaminationActivity.kt`
**verified:** The activity injects `UserSessionManager` (43) and populates `currentUser` in a coroutine (49, 78) for use at 251 (`isSelfExamination`) and 256 (`createdBy`) — while the ViewModel already injects `userRepository`. Patient (`state.user`) and examiner identities are split across two places.
**do:** Add `currentUser: UserEntity? = null` to `HealthExaminationState`, fetch it alongside the patient load, and have the activity read it from collected state. Leave every `state.user` use alone.

---
## 67 — Inject `ServerReachabilityProvider` in place of `MainApplication.isServerReachable`
**from:** jules gemini 3.1 pro (submitted as two separate tasks) · **files:** `ui/dashboard/BellDashboardViewModel.kt`, `repository/ChatRepositoryImpl.kt`
**verified:** `BellDashboardViewModel` imports `MainApplication.Companion.isServerReachable` (15); `ChatRepositoryImpl:41` calls `MainApplication.isServerReachable(url)` from inside a repository. `utils/ServerReachabilityProvider` already exists with a matching `suspend fun isServerReachable(urlString: String): Boolean`, so both are drop-in.
**do:** Constructor-inject the provider in each and delete the `MainApplication` import.

---
## 66 — `TeamViewModel`: delete the realtime-sync pass-through
**from:** codex sol 5.6 · **files:** `ui/teams/TeamViewModel.kt`, `ui/teams/TeamFragment.kt`
**verified:** `fun getTeamUpdateFlow() = realtimeSyncManager.updatesFor("teams")` (41) exposes a storage table name straight to the UI, duplicating the refresh path `loadTeams` already has through `TeamsRepository.getMyTeamDetailsFlow`.
**do:** Drop the injection and the pass-through; make every branch of `loadTeams` collect the repository flow rather than take a snapshot; remove the fragment's sync collector. Verify job cancellation still prevents duplicate collectors.

---
## 66 — `ReplyViewModel`: absorb the repository calls still sitting in `ReplyActivity`
**from:** copilot kimi k3 · **files:** `ui/voices/ReplyViewModel.kt`, `ReplyActivity.kt`, + new test
**verified:** The activity field-injects `UserSessionManager` (55), `ActivitiesRepository` (58), `SharedPrefManager` (63) and `VoicesRepository` (65) and calls them directly, while `ReplyViewModel` is 17 lines holding only `getNewsWithReplies`.
**do:** Move the user fetch, the two `SharedPrefManager` calls and the member-details data fetch behind the ViewModel; keep fragment construction in the activity. Retain `voicesRepository` only if `voicesEditActions` still needs it.

---
## 65 — Resolve the current user inside `AddResourceViewModel`
**from:** devin swe 2 · **files:** `ui/resources/AddResourceViewModel.kt`, `ui/resources/AddResourceFragment.kt`
**verified:** The fragment injects `UserSessionManager` (65) only for `getUserModel()` at 264; `AddResourceViewModel` injects just `PersonalsRepository` (15).
**do:** Add `UserRepository` to the VM with `suspend fun currentUser()`, and swap the fragment's call. Do not touch `AddResourceActivity`.

---
## 65 — Move public-survey payload building out of `PublicSurveyActivity`
**from:** copilot grok 4.5, copilot kimi k3 · **files:** `ui/surveys/PublicSurveyActivity.kt` + one new class, + new test
**verified:** The activity field-injects `SurveysRepository` (37) and `SubmissionsRepository` (39) and owns `buildPublicAnswers` (127) and `sanitizeRespondent` (159) — data-layer serialization in an Activity.
**do:** Two shapes were proposed: kimi's `PublicSurveyPayloadBuilder` (`@Inject` class in `repository/`, `suspend fun build(surveyId, submission): Pair<JsonArray, JsonObject?>`) is the tighter one and names the wire rules to preserve (selectMultiple → array, select → first choice, age trim/`toIntOrNull`); grok's `PublicSurveyViewModel` also moves upload orchestration. Pick one; the builder is the lower-risk first step.

---
## 65 — Observe chat history through `ChatRepository` instead of the sync service
**from:** codex sol 5.6 · **files:** `ui/chat/ChatViewModel.kt`, `repository/ChatRepository.kt`, `ChatRepositoryImpl.kt`
**verified:** `ChatViewModel` injects `RealtimeSyncManager` (55) and translates `updatesFor("chats")` (70) into `_refreshChatSignal` emissions; `getChatHistoryForUser` exposes only a snapshot.
**do:** Add a repository flow for one user's sorted history, keeping the snapshot for existing callers; drive `screenData`/`allChats`/`filteredChats` from it and drop the sync manager. Preserve the active search query across emissions rather than resetting the list.

---
## 64 — Compute the storage selection counters in one pass
**from:** claude opus 5 · **files:** `ui/settings/StorageCategoryViewModel.kt`
**verified:** `checkedCount` is a computed getter that scans `items`; `allChecked` calls it and compares to `items.size`. `StorageCategoryDetailFragment` reads both on every emission (142, 143), so each UI update walks the list twice — and `toggleItemChecked` emits on every checkbox tap.
**do:** Make `checkedCount` a real constructor property, redefine `allChecked` as `checkedCount == items.size && items.isNotEmpty()`, and derive the count from the `map` already being performed in `loadResources`/`toggleItemChecked`/`toggleAllChecked`. Field names and types must not change — the fragment compiles untouched.

---
## 64 — Stop the SharedPreferences thrash in `DownloadService` queue bookkeeping
**from:** copilot grok 4.5 · **files:** `services/DownloadService.kt`, + test
**verified:** Per completed URL: `cachedRemainingCount = getRemainingCount()` (153) reads both StringSets (178, 179); `cleanupProcessedUrls()` (163) re-reads both (185, 187), writes both back (190, 191) and then recounts (193). `getNextUrl` re-reads the set on every pop (635).
**do:** Mirror the queues in memory at service start, pop from memory, adjust the count arithmetically, and persist in batches or write only the changed set. Rebuild from prefs on start and flush on destroy; keep priority-before-pending ordering and `completeAll`. Weigh the crash-safety trade-off.

---
## 64 — Stop passing `Context` into the submission PDF exporter
**from:** copilot kimi k3 · **files:** `repository/SubmissionsRepositoryImpl.kt`, `SubmissionsRepositoryExporter.kt`, + test
**verified:** The impl holds `@ApplicationContext context` (50), forwards it to `exporter.generateSubmissionPdf` / `generateMultipleSubmissionsPdf` (62, 66), and passes it to `NetworkUtils.getCustomDeviceName(context)` at 814 and 885 — a function that ignores its `context` parameter entirely and delegates to `sharedPrefManager`.
**do:** Inject `@ApplicationContext` into the exporter's constructor and drop the method parameters; replace both `getCustomDeviceName(context)` calls with the injected `DeviceNameProvider`; delete the constructor param only if no `context.` use remains.

---
## 63 — `InlineResourceAdapter`: collapse the redundant per-bind file stats
**from:** devin swe 2, copilot grok 4.5 · **files:** `ui/courses/InlineResourceAdapter.kt`, + test
**verified:** `updateStatusAndPreview` calls `FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))`, which resolves and stats the file; `getFileCacheKeyIfExist` then re-stats the same file with `exists()`, `lastModified()` and `length()`. The adapter does not use `FileExistenceCache`, though `EnterprisesReportsAdapter:28` does.
**do:** Resolve the `File` once per bind and thread it into the preview helpers. Grok's fuller variant also adds an adapter-scoped `FileExistenceCache` cleared in `onDetachedFromRecyclerView`/`onCurrentListChanged` — take that too. Leave `textCache`/`htmlCoverCache` and the payload logic alone.

---
## 63 — Batch the successful achievement-upload acknowledgements
**from:** codex sol 5.6 · **files:** `services/upload/AchievementUploader.kt`, `repository/UserRepository.kt`, `UserRepositoryImpl.kt`, `data/room/dao/AchievementDao.kt`
**verified:** `userRepository.markAchievementUploaded(id, rev)` is called inside the per-document `forEach`, so each success is its own Room write through the DAO.
**do:** Accumulate successful acknowledgements (id + nullable rev) and flush once after the loop via a batch DAO method in a single transaction. Keep per-document exception isolation, do not acknowledge failed responses, and keep CV attachment upload immediately after each success (it needs that revision).

---
## 63 — `ActivitiesRepositoryImpl`: drop the dead `Context` forward and the `android.util.Log` import
**from:** copilot kimi k3 · **files:** `repository/ActivitiesRepositoryImpl.kt`, `utils/NetworkUtils.kt`, + test
**verified:** Imports `android.content.Context` (3) and `android.util.Log` (4). `serializeLoginActivities` forwards a `Context` only to `NetworkUtils.getCustomDeviceName(context)` (317) — confirmed to ignore the parameter and delegate to `sharedPrefManager`. `Log.e("ActivitiesRepository", …)` at 346.
**do:** Swap in the injectable `DeviceNameProvider` and drop the parameter. Keep the constructor `Context` if other uses remain (they do, around 215 and 435–454). The task hedges on the logger — decide once: either route 346 through an existing logging seam or leave it.

---
## 62 — Move course-step resource orchestration out of `CoursesStepsViewModel`
**from:** codex sol 5.6, copilot grok 4.5 · **files:** `ui/courses/CoursesStepsViewModel.kt`, `repository/ResourcesRepository.kt`, `ResourcesRepositoryImpl.kt` (+ optional new coordinator)
**verified:** The ViewModel injects `@ApplicationContext Context` (47) and `ResourceDownloadCoordinator` (53), builds URLs with `UrlUtils` (99) and calls `startBackgroundDownload` (101), plus resolves a markdown base path itself (72).
**do:** Codex's shape is the more specific: add one repository operation that takes a next-step id, resolves its resources, filters already-offline entries and schedules the same priority/background download by composing the existing `getAllStepResources`/`downloadResourcesPriority`; move the markdown base path behind the repository too. Grok's variant proposes a free-standing `CourseStepResourceCoordinator` instead. Either way `isDownloadingResources` semantics stay.

---
## 62 — Introduce `DashboardElementViewModel` for the session and activity calls
**from:** devin swe 2 · **files:** `ui/dashboard/DashboardElementActivity.kt`, `DashboardElementViewModel.kt` (new)
**verified:** The activity injects `ActivitiesRepository` (35) and calls `profileDbHandler.getUserModel()` (108), `activitiesRepository.recordSyncUserChallengeAction(...)` (114) and `profileDbHandler.logoutAsync()` (120) inside `lifecycleScope` blocks, with no ViewModel for the screen.
**do:** New `@HiltViewModel` exposing `currentUser()`, `recordUserChallengeAction(userId)` and `logout()`. Keep the guest check, `checkMinApk`, `SecurePrefs.clearCredentials` and the login intent where they are. Do not touch `SyncActivity`'s own `profileDbHandler` uses.

---
## 62 — Decouple `TeamPagerAdapter`/`TeamDetailFragment` from `MainApplication.listener`
**from:** jules gemini 3.1 pro · **files:** `ui/teams/TeamDetailFragment.kt`, `TeamPagerAdapter.kt`, `MainApplication.kt`
**verified:** `MainApplication.kt:118` declares `var listener: OnTeamPageListener?`; `TeamPagerAdapter` assigns it at 84 and 87, `TeamDetailFragment` at 237 and reads it at 335. A process-global listener that breaks if two team pages are open and outlives its fragment.
**do:** Delete the global; on `btnAddDoc`, resolve the currently active fragment from the ViewPager and cast to the existing `OnTeamPageListener`. No new interfaces.

---
## 61 — Move retry HTTP execution behind `RetryRepository`
**from:** codex sol 5.6 · **files:** `services/retry/RetryQueueWorker.kt`, `repository/RetryRepository.kt`, `RetryRepositoryImpl.kt`
**verified:** The worker injects `ApiInterface` (41) and calls `putDoc` (215) / `postDoc` (222) directly, mixing transport with queue-state transitions, while `RetryRepository` already owns enqueue/pending/attempts/completion.
**do:** Define a result type distinguishing success, retryable and terminal failure without leaking Retrofit types; move endpoint dispatch and response classification into the impl; keep batching and WorkManager result selection in the worker. Cancellation must never be recorded as a failed attempt. ~120 lines.

---
## 61 — Split PDF rendering out of `SubmissionsRepositoryExporter`
**from:** copilot grok 4.5 · **files:** `repository/SubmissionsRepositoryExporter.kt`, new `SubmissionPdfWriter`, + test
**verified:** The exporter imports `Context`, `Canvas`, `Paint`, `PdfDocument` and `Environment` (3–7), takes `Context` as a method parameter (46) and builds the document inline (54+) — Room assembly and Android graphics in one class in `repository/`.
**do:** Keep data assembly (submission, answers, exam name, question lines) in the exporter behind a small pure model; move drawing and file creation into a new `@Inject` writer under `services/`. Public signature unchanged so callers still compile. Conflicts with the Context-threading task at rating 64 — sequence them.

---
## 60 — Health-examination adapter: replace the regex split and the no-op `trimIndent`
**from:** claude opus 5 · **files:** `ui/health/HealthExaminationAdapter.kt`
**verified:** Line 69 derives one substring via `createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1)`, and the lazily compiled `colonRegex` (176) exists solely for it. Line 104 appends `.trimIndent()` to `getString(R.string.two_strings, …)`, and `two_strings` is `%1$s %2$s` (strings.xml:1028) — a single line, so it splits and rejoins to produce the same string on every bind.
**do:** Use `createdBy.substringAfter(':', "").takeIf { it.isNotBlank() }`, delete `colonRegex`, drop the `trimIndent()` at 104. Leave the one at 140 — that string is genuinely multi-line.

---
## 60 — `ProcessUserDataActivity`: a ViewModel for the member-upload chain
**from:** copilot kimi k3 · **files:** `ui/sync/ProcessUserDataActivity.kt`, new `ProcessUserDataViewModel.kt`, `services/UploadToShelfService.kt`, + new test
**verified:** The activity injects `syncRepository` (47), `uploadToShelfService` (54) and `userRepository` (57), and runs a three-deep callback chain: `uploadSingleUserData` (175) → `uploadSingleUserHealth` (177) → `fetchAndLogUserSecurityData` (180, 247) → `userRepository.fetchUserSecurityData` (250).
**do:** Add a suspend `uploadSingleUser(userName): Result<Unit>` to the service (keeping the listener APIs for other callers), put the three branches behind a `@HiltViewModel` exposing a `StateFlow<SyncUiState>`, and leave only dialog/Toast rendering in the activity.

---
## 60 — Route health-record refreshes through `HealthViewModel`
**from:** codex sol 5.6 · **files:** `ui/health/MyHealthFragment.kt`, `ui/health/HealthViewModel.kt`
**verified:** `setupRealtimeSync` (255) collects every `realtimeSyncManager.dataUpdateFlow` event (256) and conditionally calls `viewModel.refreshSelectedPatient()`, which the fragment also calls at 58 and 78 — while the ViewModel already owns `currentPatientId`, cancellation and the repository reads.
**do:** Drop the injection and the table-event collection; add one explicit refresh intent for resume / editor-result; coalesce repeated intents while `selectPatientJob` is active; keep the selected patient on transient failure instead of clearing it.

---
## 59 — Share one OkHttp connection pool between the two clients
**from:** claude opus 5 · **files:** `di/NetworkModule.kt`
**verified:** `buildOkHttpClient` constructs a fresh `ConnectionPool(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)` (81) and is called twice — for `@StandardHttpClient` (101) and `@ReachabilityHttpClient` (113). Both talk to the same host, so every reachability probe pays a fresh TCP+TLS handshake next to a warm idle connection.
**do:** Hoist the pool to a single instance and pass it to both. Keep each client's own `Dispatcher` — sharing it would queue probes behind sync traffic. No timeout or interceptor changes.

---
## 58 — Remove `MainApplication.isCollectionSwitchOn`
**from:** jules gemini 3.1 pro · **files:** `ui/resources/CollectionsFragment.kt`, `MainApplication.kt`
**verified:** Declared at `MainApplication.kt:114`; read at `CollectionsFragment:162` and written at `:233` — those are its only two uses, both in one fragment. Process-global UI state that does not survive process death anyway.
**do:** Delete it from `MainApplication` and hold it as a private field on the fragment (or the screen's ViewModel if one exists).

---
## 58 — Peel `Context` off `DictionaryFileReaderImpl`
**from:** copilot grok 4.5 · **files:** `repository/DictionaryFileReader.kt`, + test
**verified:** Takes `@ApplicationContext Context` (15) and calls `FileUtils.checkFileExist(context, …)` (19) and `getSDPathFromUrl(context, …)` (23); `StoragePathResolver` already centralizes those.
**do:** Inject `StoragePathResolver` instead and implement `exists()`/`readText()` from the resolved file. Keep the interface and the bound class name so no DI module changes.

---
## 58 — Centralize survey reload state in `SurveysViewModel`
**from:** codex sol 5.6 · **files:** `ui/surveys/SurveyFragment.kt`, `ui/surveys/SurveysViewModel.kt`
**verified:** The fragment implements `RealtimeSyncMixin` (37), injects `RealtimeSyncManager` (47) and installs `RealtimeSyncHelper` (49, 92), while `loadSurveys` separately owns team/adoptable selection — so the fragment knows both sync internals and reload policy.
**do:** Reduce the fragment to user intents; make `loadSurveys` idempotent, cancelling any prior load job; reload from the retained scope after `adoptSurvey` succeeds; stop a slow load from overwriting a newer selection; retain search and sort across reloads.

---
## 57 — Reuse the existing file-existence cache for course cover art
**from:** claude opus 5 · **files:** `utils/CoursesItemUtils.kt`
**verified:** `bindCover` builds the cover `File` (59) and immediately calls `coverFile?.exists()` (60) — a synchronous disk stat on the main thread inside `onBindViewHolder`, for every course row on every rebind, from `TeamCoursesAdapter` and both `CoursesAdapter` view modes. `FileExistenceCache` (FileUtils:381, 5 s TTL) already exists for this.
**do:** Add a private `FileExistenceCache` + `SystemTimeProvider` to the object and swap the `exists()` call. Signature unchanged so both callers still compile. Accept the ≤5 s delay before a freshly downloaded cover appears.

---
## 57 — Remove `MainApplication.context` from `UserRepositoryImpl`
**from:** jules gemini 3.1 pro · **files:** `repository/UserRepositoryImpl.kt`
**verified:** Lines 527 and 528 use `MainApplication.context` for `VersionUtils.getAndroidId` and `NetworkUtils.getCustomDeviceName` inside a repository.
**do:** Inject `@ApplicationContext Context` and swap both. Better still for 528: use the injected `DeviceNameProvider`, since `getCustomDeviceName` ignores the context it is handed.

---
## 56 — Hoist constant string lookups out of adapter bind paths
**from:** claude opus 5 · **files:** `ui/teams/members/MembersAdapter.kt`, `ui/teams/courses/TeamCoursesAdapter.kt`, `ui/enterprises/EnterprisesReportsAdapter.kt`
**verified:** `MembersAdapter` resolves `R.string.team_leader` at 77 and 121 and `R.string.no_visit` at 111; `TeamCoursesAdapter` resolves `R.string.remove` at 62; `EnterprisesReportsAdapter` formats `R.string.team_financial_report` with a constructor-fixed `teamName` at 65 — all once per bind. `TeamsSelectionAdapter` already caches this way.
**do:** Add `by lazy` fields for each and use them. Leave every `getString` that interpolates row data alone.

---
## 56 — `HealthExaminationActivity`: set lookup plus hoisted checkbox styling
**from:** devin swe 2 · **files:** `ui/health/HealthExaminationActivity.kt`
**verified:** `preloadCustomDiagnosis` builds `listOf(*arr)` (201) and calls `mainList.contains(s)` inside the `conditionsMap` loop — a linear scan per entry. `showCheckbox` re-resolves `getColorStateList`, `getColor` and calls `dpToPx(8)` four times for every checkbox in the `for (s in arr)` loop.
**do:** Change `listOf` to `setOf`; hoist the tint list, text colour and padding into locals before the loop.

---
## 55 — Make resource-filter signatures order-independent without sorting
**from:** codex sol 5.6 · **files:** `ui/resources/ResourcesListFilter.kt`, + test
**verified:** `toSignature` builds `searchTagIds = searchTags.map { it.id }.sorted()` on every filter-input check — a map allocation plus an O(t log t) sort on an interactive path, when tag order is irrelevant to matching.
**do:** Represent `Signature.searchTagIds` as a set built directly from `searchTags`. Keep the defensive copies for the other mutable sets. Test that reordering tags does not re-filter but changing one does.

---
## 55 — Resolve the `WebViewActivity` resource directory once
**from:** devin swe 2 · **files:** `ui/viewer/WebViewActivity.kt`
**verified:** `getLocalResourceDirectory(intent.getStringExtra("RESOURCE_ID"))` runs at 67, 107, 174 and 289; the one at 289 is inside `checkUrlSafety`, which fires on every `onPageStarted`/`shouldOverrideUrlLoading`, and 290 calls `getExternalFilesDir` again per check. Each call does a `getExternalFilesDir` plus two `canonicalFile` resolutions.
**do:** Cache the resolved `File?` once in `onCreate` (or a `lazy`) and reuse it at all four sites, caching the app dir too. Keep the `canonicalFile` containment checks exactly as they are — they are the path-traversal guard.

---
## 55 — Delete the dead `MainApplication.showDownload` global
**from:** jules gemini 3.1 pro · **files:** `MainApplication.kt`, `ui/teams/TeamDetailFragment.kt`
**verified:** Declared at `MainApplication.kt:115` and written at `TeamDetailFragment:311` and `:318` — and **read nowhere in the codebase**. Jules describes it as coordinating page switches; it coordinates nothing.
**do:** Delete the property and both assignments outright. Do not relocate it into the fragment as the original task proposed — that would preserve dead code.

---
## 54 — `CalendarViewModel`: `distinctUntilChanged` on the team flow
**from:** copilot kimi k3 · **files:** `ui/calendar/CalendarViewModel.kt`
**verified:** `loadMeetups` collects `teamsRepository.getMyTeamsFlow(userId)` and on **every** emission rebuilds `_teamNames` via `associate` (37) and re-queries `eventsRepository.getMeetupsForTeams(teams.map { it._id })` (38) — a duplicate DB read on any teams-table write, even when the list is unchanged.
**do:** Apply `distinctUntilChanged()` before the two assignments and derive `teamIds` once into a local. Public `meetups`/`teamNames` API unchanged. Overlaps the loader-extraction task at rating 43 — same file.

---
## 54 — Add an in-memory layer to `LifeCache`
**from:** copilot grok 4.5 · **files:** `repository/LifeCache.kt`, + test
**verified:** `read` always does `preferences.getString` + `gson.fromJson`; `write` always does a full `toJson` + prefs edit. No process-local memo, so repeated dashboard entry pays a Gson parse each time.
**do:** Add a `cacheKey → List<CachedMyLifeItem>` map checked before prefs, populated on read/write and replaced on write. Return immutable or defensive copies. Keep the `myLifeCache_` prefix and the companion TypeToken. Conflicts with the package-move task at rating 46.

---
## 53 — Compute the sync-summary aggregates once per endpoint and model
**from:** codex sol 5.6 · **files:** `utils/SyncTimeLogger.kt`, + test
**verified:** `generateSummary` sorts endpoints by `it.value.sumOf { log -> log.duration }` and then re-sums the same logs inside the `forEach` (`val totalTime = logs.sumOf { it.duration }`); the DB-operation block repeats the pattern and also rescans item counts.
**do:** Build small per-key aggregates (total duration, count, items) once, then sort and render from those. Output text, ordering, averages and percentages must stay byte-identical. Conflicts with the task at rating 84 — same file, sequence them.

---
## 53 — Move `LifeAdapter`'s accessibility strings to bind time
**from:** devin swe 2 · **files:** `ui/life/LifeAdapter.kt`
**verified:** `setOnTouchListener` assigns `contentDescription = context.getString(R.string.drag, myLife.title)` **inside the callback**, so it runs on every MotionEvent (down, move, up), and `visibility.setOnClickListener` does the same. Both strings depend only on `myLife.title`, fixed at bind. As written the labels also do not exist until the user touches the control — an accessibility defect, not just churn.
**do:** Assign both content descriptions directly in `onBindViewHolder` next to the other binds and remove them from the listeners. Drag and visibility behaviour unchanged.

---
## 52 — Cheap path helpers and existence+length caching in `FileUtils`
**from:** copilot grok 4.5 · **files:** `utils/FileUtils.kt`, + 2 tests
**verified:** `getFileExtension` is `address?.let { File(it).extension.lowercase() }` — a `File` allocation for a string suffix. `checkFileExist` (166) always resolves and stats with no memo, while `FileExistenceCache` only caches bare `exists()`, so callers needing "non-empty file" cannot share it.
**do:** Reimplement `getFileExtension` with `substringAfterLast` + lowercase, matching current empty/null behaviour. Extend `FileExistenceCache` with an `exists && length > 0` mode, TTL-keyed by absolute path. Keep `checkFileExist`'s public signature and `getSDPathFromUrl`'s segment parsing.

---
## 52 — Drop `MainApplication.applicationScope` from `NotificationActionReceiver`
**from:** jules gemini 3.1 pro · **files:** `services/NotificationActionReceiver.kt`
**verified:** Line 32 launches on the global `MainApplication.applicationScope` from a BroadcastReceiver. Note the receiver already calls `goAsync()` at 31, so the task's instruction to "launch using goAsync() context logic" is partly already done.
**do:** Use a receiver-local scope (or an injected `DispatcherProvider`) tied to the existing `pendingResult`, and drop the `MainApplication` import.

---
## 51 — Read each progress JSON field once
**from:** claude opus 5 · **files:** `ui/courses/ProgressViewModel.kt`
**verified:** `obj.has("stepMistake")` is followed by `obj.get("stepMistake")` — two lookups for one value — and `obj.getAsJsonObject("progress")` is called twice, once for `current` and again for `max`.
**do:** Read `stepMistake` into a local and map it only when non-null and not `JsonNull`; hoist the `progress` object into one local. **Superseded by** the repository-projection task at rating 68, which moves this mapping out of the ViewModel entirely — do that one instead if both are on the table.

---
## 51 — Memoize `ResourcesPreviewLoader`'s audio/text/csv work
**from:** copilot grok 4.5 · **files:** `utils/ResourcesPreviewLoader.kt`, + test
**verified:** `getAudioPreview` constructs a new `MediaMetadataRetriever` per call (15) with no memo; `getCsvPreview` (29) and `getTextPreview` (50) reopen the file each time. No cache exists in the file.
**do:** Add a bounded LRU (≤32–64) keyed on `absolutePath + lastModified + length`, with a `clear()` for tests. Keep `DispatcherProvider.io`, the return formats (`m:ss`, 5 CSV rows, 8 text lines) and the swallow-on-error behaviour.

---
## 50 — Reject mismatched multi-select answers before sorting
**from:** codex sol 5.6 · **files:** `utils/ExamAnswerUtils.kt`, + test
**verified:** `checkMultipleSelectAnswer` lowercases and sorts both collections before comparing, so a learner who selected too few or too many answers pays two maps and two sorts for a comparison that cannot succeed.
**do:** After the existing null guard, return false when the sizes differ; keep the sorted-list comparison for the equal-size path so duplicate-answer semantics are preserved.

---
## 50 — `SyncRepositoryImpl`: extract the shelf-dispatch map
**from:** copilot kimi k3 · **files:** `repository/SyncRepository.kt`, `SyncRepositoryImpl.kt`, + test
**verified:** The impl injects `dagger.Lazy<TransactionSyncManager>` — a service — alongside four repositories (38), hard-codes `shelfDispatchMap` (45) and uses `Log.e` at 95 and 181.
**do:** Extract the map behind a `ShelfBatchInserter` seam and surface the two swallowed errors instead of logging them. Note the task's own caveats: only replace `Lazy` if no init cycle results, and construct the seam internally if `RepositoryModule` is contended.

---
## 49 — Eliminate the temporary candidate list when choosing the next download
**from:** codex sol 5.6 · **files:** `services/DownloadService.kt`, + test
**verified:** `getNextUrl` does `.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()` — a full intermediate list allocated per queue pop when only the minimum is needed.
**do:** Single-pass minimum selection or a lazy sequence, retaining both eligibility predicates, lexicographic ordering and the incoming `isPriority`. Overlaps the prefs-thrash task at rating 64 — same file.

---
## 48 — Strip null team JSON fields in one pass
**from:** codex sol 5.6 · **files:** `model/MyTeam.kt`, + test
**verified:** Both branches of `serialize` do `val keysToRemove = object.keySet().filter { …isJsonNull }` then `keysToRemove.forEach { object.remove(it) }` — at 176–177 and again at 222–223.
**do:** Replace each filter-then-remove with safe iterator removal over the JSON entries, applying the same helper to the `resourceLink` early-return branch and the general one. Wire shape unchanged.

---
## 47 — Single-pass startsWith ranking in `ResourcesRepositoryImpl.search`
**from:** copilot grok 4.5 · **files:** `repository/ResourcesRepositoryImpl.kt`, + test
**verified:** After SQL filtering, results are split into `startsWithQuery`/`containsQuery` mutable lists (124, 125) and returned as `startsWithQuery + containsQuery` (134) — a third list allocation.
**do:** Accumulate in rank order in one structure. Keep the SQL as-is (FTS belongs to another effort), preserve the empty-query branches and the `userIdPattern` escaping.

---
## 46 — Remove the redundant coroutine in `CoursesViewModel.loadCourses`
**from:** codex sol 5.6 · **files:** `ui/courses/CoursesViewModel.kt`, + test
**verified:** Lines 125–129 wrap a single progress-repository call in `coroutineScope { async { … }.await() }` with no sibling work — a child coroutine and a `Deferred` per course load for nothing.
**do:** Call `progressRepository.getCourseProgress` directly in the existing IO context; remove only the `async`/`coroutineScope` imports. Preserve exception handling, call order and the single final state publication.

---
## 46 — Move `LifeCache` out of the repository package
**from:** copilot grok 4.5 · **files:** `repository/LifeCache.kt` → `data/cache/LifeCache.kt`, `repository/LifeRepositoryImpl.kt`, + test
**verified:** `LifeCache` is a `@Singleton` SharedPreferences-backed cache declared in `package org.ole.planet.myplanet.repository` and importing `android.content.SharedPreferences` — not a domain repository. One production consumer.
**do:** Move the file, set the new package, update the single import and the test package. Constructor/`@Inject`/`@AppPreferences` unchanged so no DI edit. Conflicts with the in-memory-layer task at rating 54 — do that one first, then move.

---
## 45 — Hoist the loop-invariant JSON read in `ExamQuestion.insertCorrectChoice`
**from:** devin swe 2 · **files:** `model/ExamQuestion.kt`
**verified:** In the else branch, `JsonUtils.getString("correctChoice", question)` is re-evaluated on every iteration of `for (a in 0 until array.size())` although `question` never changes, and the loop keeps going after a match.
**do:** Read it once into a local before the loop. Careful: the task asserts "only the first matching id should win — preserve that", but the current code lets the **last** match win (it reassigns without breaking). Adding `break` is a behaviour change; harmless if ids are unique, but state the choice explicitly.

---
## 44 — Drop the redundant `distinct` when ordering notification groups
**from:** codex sol 5.6 · **files:** `ui/notifications/NotificationsViewModel.kt`, + test
**verified:** `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()` (207–208). The two halves are disjoint by construction and each already unique — `TYPE_ORDER` is a constant list of unique values, `grouped.keys` is a Set — so `distinct()` is provably a no-op.
**do:** Remove it. Keep `TYPE_ORDER` precedence, the iteration order for unknown types, the lowercasing and the `notification` fallback.

---
## 43 — Extract meetup loading from `CalendarViewModel`'s three-repository fan-out
**from:** copilot grok 4.5 · **files:** `ui/calendar/CalendarViewModel.kt`, new `CalendarMeetupsLoader.kt`, + test
**verified:** The ViewModel injects `EventsRepository`, `TeamsRepository` and `UserRepository` (18–20) and sequences all three in `loadMeetups` (35–38) — cross-feature orchestration in the VM.
**do:** Add an `@Inject class CalendarMeetupsLoader` taking the three interfaces and exposing one suspend method; the VM maps its result to UI state. No changes to the Events/Teams repositories. Overlaps the `distinctUntilChanged` task at rating 54 — same method.

---
## 42 — Fewer passes in `NotificationsRepositoryImpl.getEnrichedNotifications`
**from:** copilot grok 4.5 · **files:** `repository/NotificationsRepositoryImpl.kt`, + test
**verified:** The payload list is walked repeatedly through `mapNotNull`/`distinct` chains (179–191), and `getUnreadCount(userId, isAdmin)` is always dispatched as a separate `async` DAO round-trip (218–219) even when the loaded payloads already carry `isRead`.
**do:** Collect task/join lists and ids in one pass. Be conservative on the unread count: deriving it from in-memory payloads only matches DAO semantics for some filters, so keep the DAO call wherever it does not. Keep the parallel team-name/join-detail fetches. Conflicts with two other tasks in this file.

---
## 42 — Skip the per-item scan in `CoursesAdapter.areAllSelected`
**from:** jules gemini 3.1 pro · **files:** `ui/courses/CoursesAdapter.kt`
**verified:** Line 164 is `val count = currentList.count { isMyCourseLib || !it.isMyCourse }` — when `isMyCourseLib` is true the predicate is constant, so the O(n) scan yields `currentList.size`.
**do:** `if (isMyCourseLib) currentList.size else currentList.count { !it.isMyCourse }`. Leave the `filterTo` at 173 alone.

---
## 41 — Push notification display formatting toward the repository
**from:** copilot grok 4.5 · **files:** `repository/NotificationsRepository.kt`, `NotificationsRepositoryImpl.kt`, `ui/notifications/NotificationsViewModel.kt`, + 2 tests
**verified:** The ViewModel injects `@ApplicationContext` (30) and `formatNotification` (267) builds display strings with `R.string` at 289, 295–296, 311–313 and 338 — after the repository has already enriched the data and resolved types.
**do:** Move the string assembly behind the notifications boundary so the VM drops `Context`, keeping grouping/selection in the VM. Caveat worth weighing before starting: pushing `R.string` *into* the repository moves Android resources deeper into the data layer, which cuts against the platform-free-core goal the task also claims — prefer emitting stable message keys over localized text.

---
## 40 — Fewer allocations in `TTSManager.stripMarkdown`
**from:** copilot grok 4.5 · **files:** `utils/TTSManager.kt`, + test
**verified:** Ten chained `.replace(Regex, …)` calls plus a `trim` — ten full scans and an intermediate string each, even when no pattern matches. The regexes are already precompiled.
**do:** Take the low-risk half only: a cheap `contains` pre-check fast path for text with no markdown markers. The proposed single-pass state machine is a behaviour-change risk across code fences, links, lists, blockquotes and table pipes for little gain.

---
## 38 — Avoid sequence overhead in the valid-log-file count
**from:** jules gemini 3.1 pro · **files:** `utils/CrashLogStore.kt`
**verified:** Line 43 is `pendingFiles.asSequence().filter { isValidLogFile(it) }.take(MAX_PENDING_FILES).count()`, and `isValidLogFile` parses the file. The sequence wrapper objects are avoidable, though the early cap at `MAX_PENDING_FILES` (20) is the thing actually worth keeping.
**do:** Replace with a plain loop that counts valid files and stops at the cap. Do not switch to an uncapped `count { … }` — that would parse every pending file.

---
## 35 — Stop allocating a `Typeface` per group-header bind
**from:** devin swe 2 · **files:** `ui/chat/ChatShareTargetAdapter.kt`
**verified:** `GroupViewHolder.bind` calls `listTitleTextView.setTypeface(null, Typeface.BOLD)` (51), which routes through `Typeface.create(null, BOLD)` on every rebind.
**do:** Set the bold typeface once in the ViewHolder's `init` and remove the per-bind call. Note `setTypeface(view.typeface, BOLD)` is not strictly identical to `setTypeface(null, BOLD)` — verify the rendered header is unchanged.

---
## 33 — Resolve the lazy teams repository once per upload batch
**from:** codex sol 5.6 · **files:** `services/upload/UploadConfigs.kt`, + test
**verified:** `teamsSyncRepository.get()` is called from inside per-result lambdas at 97 and 110 (and per-batch at 89, 105, 106).
**do:** Hoist the `get()` to the top of each `markUploaded` lambda, keeping the lazy injection so no DI cycle is reintroduced. Value is readability only — as the task itself concedes, Dagger's lazy lookup is already cached, so there is no measurable win.

---
## 30 — Memoize `Answer.valueChoicesArray`
**from:** devin swe 2 · **files:** `model/Answer.kt`
**verified:** The `@get:Ignore` getter rebuilds a `JsonArray` and runs `gson.fromJson` per choice on every access, with no memo. But it has only two read sites — `Answer.kt:50` in `createObject` and `PublicSurveyActivity:172` — each reading once per serialization, so the "re-parses every time" cost is one parse per use, not a repeated one.
**do:** Only worth doing if a profile shows repeated serialization of the same instances. The cache and its invalidation add state to a Room entity for a speculative gain.

---
## 28 — Pre-size the `DownloadUtils` mapping list
**from:** jules gemini 3.1 pro · **files:** `utils/DownloadUtils.kt`
**verified:** Line 139 is `dbMyLibrary.mapTo(ArrayList()) { … }`. The task's rationale — that this "forces unnecessary ArrayList instantiation" versus `.map` — is wrong: `.map` allocates an `ArrayList` too.
**do:** The only real remnant is capacity: `mapTo(ArrayList(dbMyLibrary.size))` avoids growth reallocations. Ignore the task's alternative suggestion of an unchecked `as ArrayList<String>` cast.

---
## 22 — Use `map` instead of `mapTo(mutableListOf())` in `TeamCalendarFragment`
**from:** jules gemini 3.1 pro · **files:** `ui/teams/TeamCalendarFragment.kt`
**verified:** Line 215 is `meetups.mapTo(mutableListOf()) { … }` and the result is only fed to `eventDates.addAll(newDates)` at 223, so `.map` is equivalent.
**do:** Straight substitution. Pure readability — there is no allocation difference.

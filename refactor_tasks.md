# Verified refactor backlog

## 88/100 — move the storage-breakdown disk scan out of the ViewModel into `ResourcesRepository` (roadmap 1+3+7)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragmentTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategories.kt`.
- **Work:**
  - Add `suspend fun getStorageBreakdown(oleDirPath: String, categoryExtensions: List<Set<String>>, otherIndex: Int): StorageBreakdown` to `ResourcesRepository`, with `data class StorageBreakdown(val totalBytes: Long, val sizes: List<Long>, val fileCounts: List<Int>)` declared in the same file (mirroring how `OfflineResourceItem` is declared there).
  - Implement it in `ResourcesRepositoryImpl` by lifting the body of `StorageBreakdownViewModel.scanStorage` verbatim — same empty/non-directory short-circuit, same `extension.isEmpty() -> otherIndex` rule — wrapped in `withContext(dispatcherProvider.io)`.
  - In `StorageBreakdownViewModel`, inject `ResourcesRepository`, delete `scanStorage`/`ScanResult`, and have `loadStorage` call `resourcesRepository.getStorageBreakdown(FileUtils.getOlePath(context), categories.map { it.extensions }, StorageCategories.OTHER_INDEX)`.
  - Move the two fixture-tree tests to `ResourcesRepositoryImplTest` against the new method, and rewrite the `forceRefresh` test to `coVerify` the repository call count instead of spying `scanStorage`.

---

## 88/100 — Resolve the current user behind `ResourceViewerViewModel`
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 2; Devin / SWE 2 — Repository boundaries list, task 8.
- **Duplicate-credit allocation:** Claude / Opus 5 55%, Devin / SWE 2 45%, based on specificity, tests, constraints, and coverage of the merged scope.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourcesExitCoordinator.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`.
- **Work:**
  - Add `private val userRepository: UserRepository` to `ResourceViewerViewModel`'s constructor and a `suspend fun getActiveUserId(): String? = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }`.
  - Delete the `userRepository` constructor parameter from `ResourcesExitCoordinator` and call `viewModel.getActiveUserId()` in `handleBackNavigation`; the surrounding null-check and `activity.finish()` path stay byte-for-byte the same.
  - Delete the `@Inject lateinit var userRepository` field, the `javax.inject.Inject` import (if now unused) and the `UserRepository` import from both activities, and update both `ResourcesExitCoordinator(this, viewModel)` constructions.
  - Add a `getActiveUserId` case to `ResourceViewerViewModelTest` (blank id and null user both yield `null`), mocking `UserRepository` the way the existing cases mock `RatingsRepository`.
  - Add `private val userRepository: UserRepository` to `ResourceViewerViewModel`.
  - Change `shouldShowResourceRatingDialog(userId, resourceId)` to `shouldShowResourceRatingDialog(resourceId)`: resolve `val userId = userRepository.getUserModel()?.id ?: return false` first (no user → no prompt), then run the existing prompted/hasRated checks.
  - Change `setRatingPrompted(userId, resourceId)` to resolve the user the same way and no-op on null.
  - Update `ResourcesExitCoordinator` to drop `userRepository` from its constructor and call the new signatures; remove its `UserRepository` import.

---

## 88/100 — return a typed progress projection from `ProgressRepository` instead of parsing `JsonArray` in the ViewModel (roadmap 1+3+9)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/ProgressViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`.
- **Work:**
  - Add `suspend fun getCourseProgressRows(userId: String?): List<CoursesProgressRow>` to `ProgressRepository`.
  - Implement it in `ProgressRepositoryImpl` by calling the existing `fetchCourseData(userId)` and moving the mapping over from the ViewModel unchanged, except that a row whose `courseId` or `courseName` is missing is skipped rather than throwing.
  - Reduce `ProgressViewModel.loadCourseData` to `_courseData.value = progressRepository.getCourseProgressRows(userRepository.getUserModel()?.id)` and delete the `gson`, `type` and any newly unused constructor parameter and imports.
  - Update `ProgressViewModelTest` to stub `getCourseProgressRows` with `CoursesProgressRow` values instead of building a `JsonArray`, keeping the existing assertions on the emitted list.

---

## 87/100 — Put course-step resource prefetch behind a repository-backed coordinator
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 9; Codex / GPT-5.6 Sol — Repository boundaries list, task 2.
- **Duplicate-credit allocation:** Copilot / Grok 4.5 55%, Codex / GPT-5.6 Sol 45%, based on specificity, tests, constraints, and coverage of the merged scope.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`.
- **Work:**
  - Move Context/path + download sequencing into the coordinator; inject `StoragePathResolver` instead of raw Context where possible.
  - VM constructor: replace direct Resources/Configurations (and Context if present) with the coordinator **or** keep Courses/Progress/User and only drop Resources/Configurations/Context.
  - Update ViewModel tests to mock the coordinator.
  - Add one narrowly named repository operation that accepts a next-step id, resolves its resources, filters already-offline entries, and schedules the same priority/background download behavior.
  - Implement it by composing the existing repository resource lookup and download code; preserve the current empty-id and empty-resource no-op behavior.
  - Replace the nested next-step coroutine in `loadStep` with that repository call and remove `UrlUtils` plus the coordinator dependency from the ViewModel.
  - Move construction of the local markdown base path behind an existing repository-returned path or a small repository method so the ViewModel no longer injects `Context`.
  - Keep `CourseStepUiState.isDownloadingResources` semantics unchanged and clean all now-unused imports.

---

## 86/100 — `CoursesRepositoryImpl`: move `PendingCourseResource` buffer out of the singleton repository
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/CoursesRepositoryImplTest.kt`.
- **Work:**
  - Trace the lifecycle of `pendingCourseResources` (read the methods that add/drain it — likely one sync entry point accumulates then flushes). Convert it to a local variable threaded through the private parse/flush functions so one sync run's buffer can never leak into another.
  - Hoist `ParsedCourseSyncPayload` parsing into a `private fun parseCourseSyncPayload(doc: JsonObject): ParsedCourseSyncPayload` if not already, and make the flush path take the buffer as a parameter.
  - Keep `appDatabase.withTransaction` (line ~623) boundaries identical — only the buffer's scope changes.
  - Add a test running two interleaved fake sync batches (MockK DAOs, `runTest`) asserting no cross-contamination of buffered resources.

---

## 86/100 — `ReplyViewModel`: absorb the last repository calls from `ReplyActivity`
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyViewModel.kt`.
- **Work:**
  - Inject `UserSessionManager`, `ActivitiesRepository`, `SharedPrefManager` into `ReplyViewModel`'s constructor.
  - Add `suspend fun getCurrentUser(): UserEntity?` (wraps `userSessionManager.getUserModel()`), `fun getCommunityLeadersJson(): String?` + `fun setRepliedNewsId(id: String)` (wrap the two `SharedPrefManager` calls at ReplyActivity.kt:151–152), and `suspend fun getMemberDetailsFragmentArgs(userModel: UserEntity?)` returning whatever `VoicesActions.showMemberDetails` needs from `ActivitiesRepository` so the fragment construction stays in the activity but the data fetch moves down.
  - Replace the four field injections in `ReplyActivity` with calls through `viewModel`. Remove now-unused imports (`ActivitiesRepository`, `SharedPrefManager`, `UserSessionManager`) if fully unreferenced — keep `voicesRepository` only if still needed for `voicesEditActions` (line 150); if so, that one stays.
  - New test file mocking the three injected services (MockK + `MainDispatcherRule` + `TestDispatcherProvider` per `docs/TESTING.md`); assert delegation and that leaders JSON parsing input is passed through unchanged.

---

## 86/100 — Make `SyncTimeLogger` concurrency-safe and eliminate repeated/unused summary work
- **Provenance:** Claude / Opus 5 — Performance list, task 1; Codex / GPT-5.6 Sol — Performance list, task 6; Devin / SWE 2 — Performance list, task 8.
- **Duplicate-credit allocation:** Claude / Opus 5 40%, Codex / GPT-5.6 Sol 30%, Devin / SWE 2 30%, based on specificity, tests, constraints, and coverage of the merged scope.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt`.
- **Work:**
  - Delete the `detailedLogs` field and its `detailedLogs.clear()` line in `startLogging`.
  - In `logDetail`, keep the `if (!isLogging) return` guard and the verbose `Log.d` branch, and remove the map append — the function becomes verbose-logging only.
  - Replace the `mutableListOf()` defaults at lines 167 and 184 with a thread-safe list (`java.util.Collections.synchronizedList(mutableListOf())`), and wrap the iterations over those lists inside `generateSummary()` that read them (`sumOf`, `count`, `sortedByDescending`) in `synchronized(logs) { ... }` so the summary pass cannot race an in-flight append.
  - Remove any import that becomes unused.
  - Run the unit tests.
  - Introduce small local aggregate values for each API endpoint containing total duration, count, and returned items.
  - Sort and render endpoint rows from those aggregates rather than re-summing raw logs.
  - Do the equivalent for database-model duration, operation count, and item count.

---

## 85/100 — `ActivitiesRepositoryImpl`: swap `android.util.Log` + dead `Context` param for the injected provider pattern
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/DeviceNameProvider.kt`.
- **Work:**
  - Replace `NetworkUtils.getCustomDeviceName(context)` at line 317 with the existing injectable `DeviceNameProvider` (`utils/DeviceNameProvider.kt`, already used by `ResourcesRepositoryImpl` and `PersonalsRepositoryImpl`); inject it into the constructor.
  - Drop the `context` parameter from the private `serializeLoginActivities` and, if `@ApplicationContext context` is then unused in the whole file (check the other `context` uses at lines ~215, 435–454 — `MyPlanet.getNormalMyPlanetActivities`/`getTabletUsages` still need it), keep the constructor param but remove only what becomes dead. If any use remains, keep `Context` and just remove the `Log` import by routing line ~346 through the logger already used elsewhere in the codebase (check how neighboring impls log; if none, keep `Log` — do **not** invent a logger).
  - Leave `NetworkUtils.getCustomDeviceName(context)`'s unused parameter in place if all 6 other call sites (UploadManager, TeamsRepositoryImpl, SubmissionsRepositoryImpl ×2, UserRepositoryImpl, MyPlanet.kt ×2, UserEntity.kt) are outside this task's file budget — note the follow-up instead of touching them. (Keep the diff to the 3 listed files.)

---

## 85/100 — `SubmissionsRepositoryImpl`: stop passing `Context` into the PDF exporter
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`.
- **Work:**
  - Inject `@ApplicationContext Context` into `SubmissionsRepositoryExporter`'s constructor; drop the `context` parameter from both `generate*Pdf` methods.
  - In `SubmissionsRepositoryImpl`, replace `NetworkUtils.getCustomDeviceName(context)` at 814/885 with injected `DeviceNameProvider` (same pattern as `ResourcesRepositoryImpl.kt:932`, `PersonalsRepositoryImpl.kt:111`).
  - If `context` then has no remaining use in `SubmissionsRepositoryImpl` (verify — grep for other `context.` usages first; if any remain, keep the param), delete the constructor param and the `android.content.Context` import.
  - Update exporter/impl tests to assert PDF generation delegates with no Context threading and that `customDeviceName` comes from the provider.

---

## 84/100 — answer "already shared?" with a SQL `EXISTS` instead of loading every matching news row (roadmap 1+7)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt`.
- **Work:**
  - Replace `getByNewsId` with `@Query("SELECT EXISTS(SELECT 1 FROM news WHERE newsId = :chatId AND viewIn LIKE :viewInPattern ESCAPE '\\')") suspend fun isSharedInView(chatId: String, viewInPattern: String): Boolean`, and add a KDoc line explaining the pattern the way `MyLibraryDao`'s header documents its `userPattern` convention.
  - In `isAlreadyShared`, build the pattern as `%"_id":"<escaped viewInId>"%`, escaping `\`, `%` and `_` with a backslash exactly as `ResourcesRepositoryImpl`/`UserRepositoryImpl` already do for their `LIKE` parameters, and return `newsDao.isSharedInView(chatId, pattern)`.
  - Keep the behaviour identical: SQLite `LIKE` is case-insensitive for ASCII, which matches the current `ignoreCase = true`.
  - Add `VoicesRepositoryImplTest` cases for a match, a non-match, and a `viewInId` containing `_` (which must not act as a wildcard).

---

## 83/100 — replace members list load with count query in MembersFragment (roadmap 1+7)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt`.
- **Work:**
  - Ensure `TeamsMembersRepository` exposes `suspend fun getJoinedMemberCount(teamId: String): Int` (already exists).
  - Add `joinedMemberCount` to `MembersUiState` in `RequestsViewModel`.
  - Update `loadJoinedMembers` to query and set this count.
  - In `MembersFragment`, use `state.joinedMemberCount` instead of `state.members.size` for `showNoData`.

---

## 82/100 — replace list-size load with the existing count query in NotificationsViewModel (roadmap 1+7)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`.
- **Work:**
  - Modify the repository to expose a flow that directly queries the count of notifications instead of returning a flow of the list.
  - Update `NotificationsViewModel` to collect this count flow.
  - Ensure UI still properly updates with the notification count.

---

## 82/100 — replace list-size load with the existing count query in RequestsViewModel (roadmap 1+7)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt`.
- **Work:**
  - Add `suspend fun getRequestedMemberCount(teamId: String): Int` to `TeamsMembersRepository`.
  - Expose a `requestedMemberCount` property in `RequestsUiState`.
  - Update `RequestsViewModel.fetchMembers` to fetch this count using `getRequestedMemberCount`.
  - Use `uiState.requestedMemberCount` in `RequestsFragment` for the `showNoData` call instead of `.size`.

---

## 81/100 — delete the `getRatingsById` wrapper from `RatingsRepository` (roadmap 1)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/RatingSummaryProvider.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`.
- **Work:**
  - Delete `getRatingsById` from `RatingsRepository.kt` and its implementation from `RatingsRepositoryImpl.kt`.
  - In `ResourceDetailFragment.onRatingChanged`, guard on the id first — `val resourceId = library.resourceId ?: return@launch` — then call `ratingsRepository.getRatingSummary("resource", resourceId, userModel?.id)`; `lastKnownRating` is already a `RatingSummary?` so nothing downstream changes, and the surrounding `try/catch` and `isAdded` guard stay as they are.
  - Rename the test case to target `getRatingSummary` with the same fixtures and assertions (the test builds `RatingsRepositoryImpl` directly, so no other test edit is needed).
  - Grep for `getRatingsById` across `app/src` afterwards to confirm no reference survives.

---

## 81/100 — delete the four unused resource-open overloads from `ActivitiesRepository` (roadmap 1+8)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImplTest.kt`.
- **Work:**
  - Delete the four declarations from `ActivitiesRepository.kt`.
  - In `ActivitiesRepositoryImpl.kt`, remove `override` from the four matching functions and keep them as ordinary (public) members so `getProfileActivityStats` and the existing tests still compile — `ActivitiesRepositoryImplTest` declares `private lateinit var repository: ActivitiesRepositoryImpl` (line 72) and constructs the concrete class (line 103), so no test edit is needed.
  - Re-run a grep for the four names across `app/src/main` and `app/src/test` to confirm nothing resolves them through the interface type any more.
  - Do not otherwise reorder or reformat either file.

---

## 81/100 — drop the `SharedPrefManager` parameter from `ConfigurationsRepository.checkVersion` (roadmap 1+4)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`.
- **Work:**
  - Change the interface signature to `fun checkVersion(callback: CheckVersionCallback)` and delete the `SharedPrefManager` import from `ConfigurationsRepository.kt`.
  - In `ConfigurationsRepositoryImpl`, drop the `spm` parameter from `checkVersion` and from the private helpers that only forward it; replace every `spm` reference with the injected `sharedPrefManager`.
  - Update the three call sites to `configurationsRepository.checkVersion(this)` / `(this@AutoSyncWorker)`, and delete any local `prefData` / `sharedPrefManager` reference that becomes unused *only* because of this change (leave ones still used elsewhere in those files).
  - Update the three `repository.checkVersion(callback, sharedPrefManager)` calls in the test to the one-argument form; keep the existing assertions unchanged.

---

## 81/100 — move the health-examination upload sequence out of `UploadToShelfService` into `HealthRepository` (roadmap 1+5)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/HealthRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt`, `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt`.
- **Work:**
  - Add `suspend fun syncPendingHealthExaminations()` and `suspend fun syncPendingHealthExaminationsForUser(userId: String)` to `HealthRepository`.
  - Implement both in `HealthRepositoryImpl` as the existing three-call sequence (fetch → `uploadHealthData` → `markHealthExaminationsUploaded`), returning early when the fetch is empty.
  - Remove `getUpdatedHealthExaminations`, `getUpdatedHealthForUser` and `markHealthExaminationsUploaded` from the `HealthRepository` interface and drop their `override` keywords in the impl (they stay as members; the existing tests construct `HealthRepositoryImpl` directly). Keep `uploadHealthData` on the interface — it is exercised on its own by `HealthRepositoryImplTest.uploadHealthData_successful_upload` (line 265).
  - Replace the bodies of `UploadToShelfService.uploadHealth` / `uploadSingleUserHealth` with a single repository call each, keeping the `appScope.launch(dispatcherProvider.io)` wrapper, the `userId.isNullOrEmpty()` guard and both `listener?.onSuccess(...)` messages verbatim.
  - Add two `HealthRepositoryImplTest` cases: pending rows get uploaded and marked; an empty fetch performs no upload call.

---

## 81/100 — remove the dead `getNotifications` from `NotificationsRepository` and route its timestamps through the injected `TimeProvider` (roadmap 1+8)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`.
- **Work:**
  - Delete the `getNotifications` declaration from the interface and change the impl's `override suspend fun getNotifications` (line 140) to `private suspend fun getNotifications`.
  - Replace each `Date()` with `Date(timeProvider.now())`; at line 461 keep the existing `doc.get("time")?.let { Date(it.asLong) } ?: Date(timeProvider.now())` fallback shape.
  - Confirm `timeProvider` has no other unused-import or nullability fallout and that `java.util.Date` is still imported for the type itself.
  - Add a `NotificationsRepositoryImplTest` case that fixes `timeProvider.now()` to a constant and asserts `markNotificationsAsRead` passes that instant to `notificationDao.markAsRead`.

---

## 81/100 — tighten `FeedbackRepository`: drop the two internal-only builders and stop passing `UserEntity` into the list query (roadmap 1+3+9)
- **Provenance:** Claude / Opus 5 — Repository boundaries list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModel.kt`.
- **Work:**
  - Delete `createFeedback` and `saveFeedback` from the interface; in the impl, drop their `override` keywords and mark them `private` (both are only reached from `createAndSaveFeedback`).
  - Change the interface declaration to `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>` (no `suspend`) and delete the now-unused `UserEntity` import from `FeedbackRepository.kt`.
  - Update the impl to branch on the `isManager` flag and pass `ownerName` to `feedbackDao.getByOwnerFlow`; the `distinctByContent` comparator stays exactly as it is.
  - In `FeedbackListViewModel.loadFeedback`, keep the `userRepository.getUserModel()` call and pass `user?.name` and `user?.isManager() == true`.
  - Update the two test files to the new signature (`coEvery { feedbackRepository.getFeedback(...) }` becomes `every { ... }` since the function no longer suspends).

---

## 80/100 — Carve the achievement surface out of UserRepository (roadmap 4+1)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/services/upload/AchievementUploader.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt`.
- **Work:**
  - Create `UserAchievementsRepository` containing `val achievementUpdates: Flow<Unit>` and the five achievement methods exactly as declared today: `initializeAchievement`, `updateAchievement` (copy the full parameter list including the `resumeFileName` default), `getAchievementData`, `getAchievementsForUpload`, `markAchievementUploaded`, with the needed `Achievement`, `AchievementData`, `JsonArray`, `JsonObject`, `Flow` imports.
  - Make `UserRepository` extend it and delete those six members from `UserRepository` — `UserRepositoryImpl`'s existing `override`s satisfy the super-interface with no impl change.
  - Create `UserAchievementsRepositoryModule` with `@Module @InstallIn(SingletonComponent::class)` and `@Provides fun provideUserAchievementsRepository(repo: UserRepository): UserAchievementsRepository = repo`.
  - In `AchievementUploader`, change the injected `userRepository: UserRepository` field to `UserAchievementsRepository` type (rename the field accordingly) and keep the two calls unchanged.
  - Leave `AchievementViewModel` on `UserRepository` — a sibling task owns that file.

---

## 80/100 — Centralize survey reload state in SurveysViewModel (roadmap 1+3+8+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`.
- **Work:**
  - Remove realtime-sync injection and mixin setup from the fragment so it sends only user intents: initial scope, search, sort, team/adoptable selection, and adoption.
  - Make `loadSurveys` idempotently retain the active scope and cancel any prior load job before starting another repository read.
  - After `adoptSurvey` succeeds, reload using the retained scope inside the ViewModel rather than relying on a sync callback into the fragment.
  - Prevent stale slower loads from overwriting a newer radio-button selection, and retain the current search and sort when data reloads.
  - Remove obsolete table-update and realtime-sync imports without changing snackbar event handling.

---

## 80/100 — inject ServerReachabilityProvider to remove MainApplication dependency in BellDashboardViewModel (roadmap 4)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt`.
- **Work:**
  - Inject `ServerReachabilityProvider` into `BellDashboardViewModel`.
  - Replace the static `MainApplication.isServerReachable` call with `serverReachabilityProvider.isServerReachable`.
  - Remove the unused `MainApplication` import.

---

## 80/100 — inject ServerReachabilityProvider to remove MainApplication dependency in ChatRepositoryImpl (roadmap 4+9)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt`.
- **Work:**
  - Inject `ServerReachabilityProvider` into `ChatRepositoryImpl`.
  - Replace the `MainApplication.isServerReachable(url)` call with `serverReachabilityProvider.isServerReachable(url)`.
  - Update any missing imports and remove `MainApplication`.

---

## 80/100 — Introduce DashboardElementViewModel for session and activity calls (roadmap 3+1)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`.
- **Work:**
  - Create `DashboardElementViewModel` injecting `UserRepository`, `ActivitiesRepository`, `UserSessionManager`, and `DispatcherProvider`; expose `suspend fun currentUser() = userRepository.getUserModel()`, `fun recordUserChallengeAction(userId: String?)` which launches `viewModelScope.launch(dispatcherProvider.io) { activitiesRepository.recordSyncUserChallengeAction("$userId") }`, and `suspend fun logout() = userSessionManager.logoutAsync()`.
  - In the activity's server-connect block (~line 107), replace `profileDbHandler.getUserModel()` with `viewModel.currentUser()` and `activitiesRepository.recordSyncUserChallengeAction("${userModel?.id}")` with `viewModel.recordUserChallengeAction(userModel?.id)`; keep the guest-id check and `checkMinApk(...)` call exactly where they are.
  - In `logout()`, replace `profileDbHandler.logoutAsync()` with `viewModel.logout()`; leave `SecurePrefs.clearCredentials`, `prefData` calls, `NotificationUtils.cancelAll`, and the login intent untouched.
  - Delete the `@Inject lateinit var activitiesRepository` field and its import; add `private val viewModel: DashboardElementViewModel by viewModels()`.
  - Run the unit tests.

---

## 80/100 — Let the feedback repository flow be the only list refresh source (roadmap 1+3+8+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt`.
- **Work:**
  - Remove `RealtimeSyncMixin`, the injected manager, helper field, watched-table methods, and setup call from the fragment.
  - Keep the ViewModel's single repository-flow collection alive for its lifetime instead of canceling and restarting it for database invalidations.
  - Retain `refreshFeedback` only for the explicit `OnChangedListener` submission callback, or make it a no-op-safe refresh that cannot create parallel collectors.
  - Preserve adapter submission, scroll-to-top behavior, empty messaging, and header visibility.
  - Remove all unused sync and table-update imports.

---

## 80/100 — Make calendar meetup loading distinct, allocation-light, and single-owner
- **Provenance:** Copilot / Kimi K3 — Performance list, task 4; Copilot / Grok 4.5 — Performance list, task 10.
- **Duplicate-credit allocation:** Copilot / Kimi K3 40%, Copilot / Grok 4.5 60%, based on specificity, tests, constraints, and coverage of the merged scope.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`.
- **Work:**
  - apply `distinctUntilChanged()` to the collected team list before the two assignments, and derive `teamIds` once into a local so the same list instance feeds both `_meetups` and `_teamNames`. Keep the existing public `meetups`/`teamNames` `StateFlow` API unchanged.
  - Read current `CalendarViewModel` load path; move sequential/parallel repo calls into the loader unchanged.
  - VM injects loader only (or loader + nothing else for data).
  - Tests mock loader or repositories at loader level; VM tests assert state updates.

---

## 80/100 — Make challenge progress formatting independent of the dashboard ViewModel (roadmap 3+8+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/ChallengePrompter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`.
- **Work:**
  - Move the two deterministic progress calculations out of the `DashboardViewModel` instance API into an internal, platform-free function or value object colocated in `ChallengePrompter.kt`.
  - Change `ChallengePrompter` to accept only `DashboardActivity` and `SharedPrefManager`, and call the extracted calculation directly when formatting both dialog variants.
  - Update `DashboardViewModel` to call the same extracted calculation wherever it still needs those values, then remove the duplicated ViewModel methods if no callers remain.
  - Remove obsolete imports and constructor wiring without changing the existing Spanish completion check, caps, or markdown text.
  - Add focused unit coverage only if it fits in one of the two named files; otherwise rely on the existing dashboard test suite and do not create a third file.

---

## 80/100 — Move retry HTTP execution behind RetryRepository (roadmap 1+4+5+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`.
- **Work:**
  - Define one repository result type that distinguishes success, retryable transport failure, and terminal failure without exposing Retrofit response classes.
  - Move endpoint/method dispatch and HTTP response classification from the worker into `RetryRepositoryImpl`, preserving supported methods, payloads, and status handling.
  - Replace the worker's `ApiInterface` dependency with the repository operation and keep batching plus WorkManager result selection in the worker.
  - Ensure cancellation propagates and is never converted into a failed queue attempt.
  - Keep queue state transitions exactly once per operation and remove obsolete networking imports from the worker.

---

## 80/100 — Observe chat history through ChatRepository instead of the sync service (roadmap 1+3+5+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt`.
- **Work:**
  - Add a repository flow for one user's sorted chat history while retaining the snapshot method for existing callers.
  - Implement the flow from the DAO's observable query if present; if the DAO only has a snapshot, confine the minimal observable adapter to `ChatRepositoryImpl` without exposing Room or table names.
  - Remove `RealtimeSyncManager` from `ChatViewModel` and drive refreshed `screenData`, `allChats`, and `filteredChats` from the repository flow for the active user.
  - Preserve the current search query when a database emission arrives rather than resetting the visible list unconditionally.
  - Keep `refreshChatSignal` only if a fragment still requires it for rendering; emit it from repository data changes, not sync-table events.

---

## 80/100 — remove static context from UserRepositoryImpl (roadmap 4+9)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`.
- **Work:**
  - Inject `@ApplicationContext private val context: Context` into the `UserRepositoryImpl` constructor (if not already there).
  - Replace `MainApplication.context` with the injected `context` in lines 527 and 528.
  - Remove `MainApplication` import.

---

## 80/100 — Resolve the current user inside AddResourceViewModel (roadmap 3+1)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt`.
- **Work:**
  - Add `private val userRepository: UserRepository` to the `AddResourceViewModel` constructor.
  - Add `suspend fun currentUser(): UserEntity? = userRepository.getUserModel()`.
  - In the fragment, delete `@Inject lateinit var userSessionManager` and its import; replace `userSessionManager.getUserModel() ?: return@launch` at ~line 264 with `viewModel.currentUser() ?: return@launch`.
  - Run the unit tests.

---

## 80/100 — Route health-record refreshes through HealthViewModel (roadmap 1+3+8+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`.
- **Work:**
  - Remove direct `RealtimeSyncManager` injection and table-event collection from `MyHealthFragment`.
  - Add a single explicit ViewModel refresh intent for lifecycle resume or completed editor results, using the already tracked patient id.
  - Coalesce repeated refresh intents while `selectPatientJob` is active so only the latest requested patient is fetched.
  - Preserve the selected patient on transient repository failure instead of clearing selection solely because a refresh failed.
  - Keep initial logged-in-user selection, loading indicators, horizontal record scrolling, and provider-only controls unchanged.

---

## 80/100 — Stop threading ConfigurationsRepository into dialog helpers (roadmap 1+8)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt`.
- **Work:**
  - In `DialogUtils`, change `getUpdateDialog(context, info, progressDialog, scope, configurationsRepository)` to take `checkCheckSum: suspend (String) -> Boolean` as the last parameter.
  - Change `startDownloadUpdate(context, path, progressDialog, scope, configurationsRepository)` the same way, and pass the lambda through where the dialog delegates to `startDownloadUpdate`.
  - Replace the `configurationsRepository.checkCheckSum(path)` invocation inside the helper with `checkCheckSum(path)`; remove the `ConfigurationsRepository` import if unused.
  - At both call sites pass `configurationsRepository::checkCheckSum` — in `SyncActivity` the injected field, in `AutoSyncWorker` the constructor-injected one.

---

## 79/100 — `ProcessUserDataActivity`: ViewModel for the member-upload chain
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`.
- **Work:**
  - In `UploadToShelfService` add a suspend counterpart: `suspend fun uploadSingleUser(userName: String?): Result<Unit>` that performs data→health sequentially (the exact two calls at lines 52–93) and returns instead of chaining listeners; keep the old listener APIs for other callers (check callers first: `grep -rn "uploadSingleUserData\|uploadSingleUserHealth"`).
  - New `ProcessUserDataViewModel` injecting `SyncRepository`, `UserRepository`, `UploadToShelfService`; expose `fun startUpload(source: String, userName: String?): StateFlow<SyncUiState>` covering the three branches of the Activity's `startUpload` (166–172) and `suspend fun fetchUserSecurity(name: String)`.
  - Activity keeps only dialog/Toast rendering and collects the StateFlow; delete its `uploadToShelfService`, `userRepository` injections and `fetchAndLogUserSecurityData`.
  - ViewModel tests: member path success, health-step failure surfaces `SyncUiState.Error`, login path delegates to `syncRepository.uploadLoginData()`.

---

## 79/100 — `SyncRepositoryImpl`: push shelf-dispatch map construction behind an interface-friendly seam
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt`.
- **Work:**
  - Extract the `shelfDispatchMap` into a `ShelfBatchInserter` interface with a `suspend fun batchInsert(shelf: String, shelfId: String?, docs: List<JsonObject>): Int?` (null = unknown shelf) in `SyncRepository.kt`, implemented by a small class that takes the four repositories/writers. Bind in `RepositoryModule` (or construct internally if the module is PR-blocked).
  - Remove `android.util.Log` import: replace both `Log.e` calls with error surfacing — return the failure in `SyncUiState.Error` where reachable, or use `ensureActive()` + rethrow; do not silently swallow.
  - Replace `dagger.Lazy<TransactionSyncManager>` with a direct injection only if no init cycle results (TransactionSyncManager does not depend on SyncRepository — verify before changing); otherwise keep `Lazy` and add a comment explaining the cycle.

---

## 79/100 — Fewer passes in NotificationsRepositoryImpl.getEnrichedNotifications
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`.
- **Work:**
  - Single pass over `payloadNotifications` collecting task list, join list, task ids, join ids, and optional unread tally when valid.
  - Compute `unreadCount` from in-memory payloads when that matches `getUnreadCount` semantics for the current filter; only hit DAO when necessary (e.g. admin/`unread` edge cases — preserve correct counts).
  - Keep parallel `async` for team-name and join-detail batch fetches.
  - Preserve `parsedTaskDates` map contract for `NotificationsViewModel.formatNotification`.

---

## 79/100 — Single-pass startsWith ranking in ResourcesRepositoryImpl.search
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`.
- **Work:**
  - Keep SQL filtering as-is (do not invent FTS — #16624 owns that area).
  - Replace dual-list + concat with one ordered accumulation (`partition` into a pre-sized structure, or `buildList` that appends startsWith first then contains, single iteration).
  - Avoid intermediate `queryParts` list if only `normalizedQueryParts` is needed (optional micro).
  - Preserve empty-query branches and `userIdPattern` escaping.

---

## 78/100 — Remove redundant case folding and allocations from normalized chat search
- **Provenance:** Claude / Opus 5 — Performance list, task 3; Codex / GPT-5.6 Sol — Performance list, task 5; Copilot / Grok 4.5 — Repository boundaries list, task 5.
- **Duplicate-credit allocation:** Claude / Opus 5 25%, Codex / GPT-5.6 Sol 40%, Copilot / Grok 4.5 35%, based on specificity, tests, constraints, and coverage of the merged scope.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt`.
- **Work:**
  - Remove `ignoreCase = true` from the two `startsWith` calls (lines 60 and 97).
  - Remove `ignoreCase = true` from the two `contains` calls (lines 63 and 99).
  - Add a one-line comment above each loop noting that both sides come from `Utilities.normalizeText`, matching the comment style in `ResourcesSearchUtils`.
  - Run the unit tests.
  - Keep normalizing the full query, each query part, titles, questions, and responses exactly once as now.
  - Remove case-insensitive comparison mode only where both operands are already normalized.
  - Preserve the four result-priority buckets and early exit after a chat's first matching conversation.
  - Add explicit mixed-case and accented-query regression cases for title and conversation modes.

---

## 77/100 — `SurveysRepository`: return raw counts, not formatted strings
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/model/SurveyInfo.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt`.
- **Work:**
  - Change `SurveyInfo.submissionCount: String` → `submissionCount: Int` and `creationDate: String` → `creationDate: Long` (epoch millis); `lastSubmissionDate` stays a display string only if already raw — otherwise also becomes `Long?`. Verify each field's only producers/consumers first (`SurveysAdapter.kt:109–112`, `SurveysViewModel.kt:48–54,92`).
  - In `SurveysRepositoryImpl`, delete the `getQuantityString`/`formatDate` calls; return raw values. This removes one `context.` use (survey reminder prefs in the same file are **out of scope** — different task).
  - Move plural formatting into `SurveysAdapter` (it has a `Context` via item views) and date formatting into the adapter or ViewModel using the existing `TimeUtils` helpers.
  - Update the existing `SurveysRepositoryImplTest` (verify it exists under app/src/test/.../repository/ first; if absent, add a focused test for the raw-value mapping).

---

## 77/100 — Stop SharedPreferences thrash in DownloadService queue bookkeeping
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceOnDownloadCompleteTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceUrlSelectionTest.kt`.
- **Work:**
  - Mirror priority/pending queues in memory when the service starts / when new work is enqueued; pop from memory; update `cachedRemainingCount` with simple decrement/increment.
  - Persist StringSets in batches (e.g. every N completions or when the queue drains / service stops), not after every single file — **or** write only the set that changed once per completion without a full recount.
  - Keep crash-safe enough behavior: on start, rebuild memory from prefs; on stop/destroy, flush.
  - Preserve priority-before-pending ordering and `completeAll` logic in `onDownloadComplete`.

---

## 75/100 — Cache course-step resource offline checks on bind
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`.
- **Work:**
  - Hold an adapter-scoped `FileExistenceCache` (and clear it in `onDetachedFromRecyclerView` / when addresses change in `onCurrentListChanged`).
  - Resolve the library file once per bind via existing `FileUtils.getLibraryFile` / `externalFilesDir` instead of rebuilding `"ole/${id}/…"` strings repeatedly.
  - Cache per-resource download presence for the current list generation so scroll rebinds do not re-hit disk within TTL.
  - Keep preview text/html caches; only remove redundant exists checks.

---

## 75/100 — In-memory layer for LifeCache
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`.
- **Work:**
  - Add a process-local map (`cacheKey → List<CachedMyLifeItem>`) checked before prefs; populate on successful read/write; invalidate/replace on write for that key.
  - Return defensive copies **or** immutable lists so callers cannot mutate the cached instance (match #17222 spirit).
  - Keep Gson TypeToken in companion; keep key prefix `myLifeCache_`.

---

## 73/100 — Batch successful achievement upload acknowledgements (roadmap 1+5+7+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/upload/AchievementUploader.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AchievementDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`.
- **Work:**
  - Introduce a small platform-free acknowledgement value carrying achievement id and nullable revision at the repository boundary.
  - Replace the single-item repository method with a batch method, and implement one DAO transaction that applies all successful acknowledgements.
  - Accumulate only successful document uploads in `uploadAchievement`; flush them once after the upload loop, including successes whose optional CV attachment later fails.
  - Preserve per-document exception isolation and do not acknowledge failed or malformed responses.
  - Keep CV attachment upload immediately after each successful document because it requires that document's returned revision.

---

## 73/100 — Collapse the repeated upload-await blocks in UserDataWorker (roadmap 5+8)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt`.
- **Work:**
  - Add `private suspend fun awaitUploadCompletion(start: (onComplete: () -> Unit) -> Unit)`: inside `runCatching`, create `CompletableDeferred<Unit>`, invoke `start { deferred.complete(Unit) }`, then `withTimeoutOrNull(30000L) { deferred.await() }`.
  - Replace the `uploadUserActivities`, `uploadExamResult`, `uploadSubmitPhotos`, and `uploadActivities` blocks with `awaitUploadCompletion { cb -> uploadManager.uploadX(object : OnSuccessListener { override fun onSuccess(success: String?) { cb() } }) }`.
  - Replace the `uploadResource` block the same way and keep `uploadManager.uploadTeams()` immediately after its await, as today.
  - Replace the `uploadToShelfService.uploadUserData` block with `awaitUploadCompletion { cb -> uploadToShelfService.uploadUserData { uploadToShelfService.uploadHealth(); cb() } }` — note the `uploadHealth()` call stays inside the inner callback.
  - Remove now-unused imports (`OnSuccessListener` stays if the login path still uses it — check before removing `CompletableDeferred`, `withTimeoutOrNull` imports).

---

## 73/100 — Compute the task badge per team instead of once globally (roadmap 1+8)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`.
- **Work:**
  - Keep the single `getTasksForUserBetween` call where it is; replace `val hasTask = tasks.isNotEmpty()` with `val taskTeamIds = tasks.mapNotNull { it.teamId }.toSet()` built once before the loop.
  - Inside the per-team loop, compute `val hasTask = teamId in taskTeamIds` (matching the loop variable name actually used) and keep passing it into `TeamNotificationInfo(hasTask, hasChat)`.
  - Verify `hasChat` still comes from `chatCountsById` per team — do not alter that lookup.
  - Run the unit tests.

---

## 73/100 — decouple TeamPagerAdapter and TeamDetailFragment from MainApplication.listener (roadmap 4+6)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt`.
- **Work:**
  - Remove `var listener: OnTeamPageListener?` from `MainApplication.kt`.
  - Update `TeamPagerAdapter` to not set `MainApplication.listener`.
  - In `TeamDetailFragment`, when `btnAddDoc` is clicked, find the currently active fragment from the ViewPager and cast it to `OnTeamPageListener` instead of relying on the static field.

---

## 73/100 — Expose the current user through RequestsUiState (roadmap 3+1)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt`.
- **Work:**
  - Add `val currentUser: UserEntity? = null` to `RequestsUiState` (keep it last so positional constructors stay valid), and populate it with `user` inside `fetchMembers`.
  - In the fragment, delete `@Inject lateinit var userSessionManager: UserSessionManager` and the `UserSessionManager` import.
  - Replace `private lateinit var currentUser: UserEntity` plus the `onAttach` `UserEntity()` init and the `lifecycleScope.launch { currentUser = userSessionManager.getUserModel() ... }` block in `onViewCreated` with a single `uiState.currentUser?.let { (adapter as? RequestsAdapter)?.setUser(it) }` inside the existing `collectWhenStarted(viewModel.uiState)` collector.
  - Run the unit tests.

---

## 73/100 — Fetch one pending submission directly instead of loading a list (roadmap 1+7+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt`.
- **Work:**
  - Compare both list-first call sites' nullable user and parent-id semantics with the existing bounded DAO query.
  - Reuse `getLatestPendingByUserAndParent` where its ordering matches; otherwise add one precisely ordered `LIMIT 1` DAO query rather than changing broad list semantics.
  - Hydrate only the returned submission through `hydrateSubmission` so answers remain available to downstream logic.
  - Preserve fallback behavior when no pending row exists and preserve the current `lastUpdateTime` winner.
  - Remove only the now-redundant list materialization at those two call sites.

---

## 73/100 — Load only id+title for the achievement resource picker (roadmap 7+1)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`.
- **Work:**
  - In `MyLibraryDao`, add `@Query("SELECT id, title FROM my_library")` returning a new `data class IdTitleProjection(val id: String, val title: String?)` declared beside `ResourceTitleProjection`. No `ORDER BY` — preserve the current unspecified order so the list looks identical.
  - In `ResourcesRepository`, replace `suspend fun getAllLibraries(): List<MyLibrary>` with `suspend fun getLibraryIdTitles(): List<IdTitleProjection>`; implement the delegation in `ResourcesRepositoryImpl` (`myLibraryDao.getIdTitles()`).
  - In `AchievementViewModel`, replace `getAllLibraries()` with `getLibraryIdTitles()` and add `suspend fun getLibraryItemsByIds(ids: List<String>)` delegating to the existing `resourcesRepository.getLibraryItemsByIds`.
  - In `EditAchievementFragment`, change `showResourceDialog` to hold `List<IdTitleProjection>`; `createResourceList` keeps matching `prevList` on `title` exactly as today.
  - In the dialog's positive-button block (around EditAchievementFragment.kt:427), collect the selected projections' `id`s, call `viewModel.getLibraryItemsByIds(ids)` inside the same coroutine, and push `serializeResource()` for each fetched entity into `resourceArray` — preserving the current order of selection.
  - Remove the now-unused `MyLibrary` import from the fragment if it is no longer referenced.

---

## 73/100 — Move the examiner user lookup into HealthExaminationState (roadmap 3+1)
- **Provenance:** Devin / SWE 2 — Repository boundaries list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt`.
- **Work:**
  - Add `val currentUser: UserEntity? = null` to `HealthExaminationState`.
  - In the viewmodel's load path, fetch `userRepository.getUserModel()` alongside the existing patient load and set `currentUser` on the emitted state.
  - In the activity, delete the `UserSessionManager` injection and the `lifecycleScope.launch` block that populates the field; keep a plain `private var currentUser: UserEntity? = null` assigned from the collected state.
  - Leave lines using `state.user` (patient) untouched.

---

## 73/100 — remove static MainApplication.applicationScope from NotificationActionReceiver (roadmap 4+5)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt`.
- **Work:**
  - Create a local scope in `NotificationActionReceiver` or inject a DispatcherProvider.
  - Launch the coroutine using `goAsync()` context logic.
  - Remove `MainApplication` import.

---

## 73/100 — remove static MainApplication.isCollectionSwitchOn in CollectionsFragment (roadmap 4+10)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`.
- **Work:**
  - Remove `isCollectionSwitchOn` from `MainApplication.kt`.
  - Add a `private var isCollectionSwitchOn: Boolean = false` directly into `CollectionsFragment.kt` (or create/use a ViewModel if one already exists for this state).
  - Replace all usages of `MainApplication.isCollectionSwitchOn` with the local field.

---

## 73/100 — remove static MainApplication.showDownload in TeamDetailFragment (roadmap 4+10)
- **Provenance:** Jules / Gemini 3.1 Pro — Repository boundaries list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`.
- **Work:**
  - Remove `showDownload` from `MainApplication.kt`.
  - Move `showDownload` into `TeamDetailFragment.kt` as a private property (e.g. `private var showDownload = false`).
  - Replace all accesses to `MainApplication.showDownload` with the local property.

---

## 73/100 — Remove sync-table events from team list view modelling (roadmap 1+3+5+9)
- **Provenance:** Codex / GPT-5.6 Sol — Repository boundaries list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt`.
- **Work:**
  - Delete the realtime-sync dependency and `getTeamUpdateFlow` pass-through from `TeamViewModel`.
  - Ensure every branch of `loadTeams` collects an existing repository flow rather than taking a one-time repository snapshot when a user id is absent.
  - If only a snapshot exists for a branch, refresh that branch from an explicit ViewModel event already owned by `TeamFragment`; do not expose `TableDataUpdate` or table strings.
  - Remove the fragment's sync-event collector and retain one initial `loadTeams` call plus the existing filter/search calls.
  - Verify job cancellation still prevents duplicate collectors when type, dashboard mode, or user changes.

---

## 72/100 — `PublicSurveyActivity`: extract `buildPublicAnswers`/`sanitizeRespondent` into an injectable payload builder
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt`.
- **Work:**
  - Create `PublicSurveyPayloadBuilder` as an `@Inject constructor`-class in `repository/` taking `SurveysRepository`. Expose `suspend fun build(surveyId: String, submission: Submission): Pair<JsonArray, JsonObject?>`.
  - Move both private functions' logic from the Activity into it, unchanged in behavior (same `selectMultiple` → array, `select` → first choice, else string rules; same age-trim/`toIntOrNull` sanitize rule). It calls `surveysRepository.getExamQuestions(surveyId)` internally.
  - Activity injects the builder, calls the single method, and passes results to `surveysRepository.submitPublicSurvey` (line 136).
  - New unit tests for: selectMultiple keeps array, select unwraps first choice, plain answer stringified, age `" 34 "` → `34`, non-numeric age removed.

---

## 72/100 — ChatHistoryAdapter DiffUtil and bind micro-costs
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt`.
- **Work:**
  - In `areContentsTheSame`, read first-query once per side into locals; consider comparing conversation size or first item identity if cheap and already available.
  - On bind, pass `item.conversations` as the list type already held (if it is already a List, skip `toList()`; only copy if the model type is mutable and must be snapshot-on-click — snapshot **inside** click, not earlier).
  - Optional: `getChangePayload` for share-icon-only updates already partially exists via `PAYLOAD_CHAT_SHARED` — ensure full rebind is not forced when only lastUsed changes if UI does not show it (only if tests prove safe).

---

## 72/100 — Cheap path helpers and existence+length caching in FileUtils
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/FileExistenceCacheTest.kt`.
- **Work:**
  - Implement `getFileExtension` with string ops only (`substringAfterLast`, lowercase), matching current empty/null behavior.
  - Extend `FileExistenceCache` with a method (or flag) that treats “present” as `exists && length > 0`, still TTL-keyed by absolute path.
  - Route `checkFileExist` through a small process-level or caller-supplied cache of that stronger check **or** document a shared internal cache used by `checkFileExist` with clear/TTL; keep public signature of `checkFileExist(context, url)` unchanged.
  - Update `FileExistenceCacheTest` for length>0 semantics (empty file → false; delete within TTL still returns cached value; `clear()` forces re-stat).

---

## 72/100 — DAO: replace `CourseDao.filterByTitleNormal` raw query with a parameterized query
- **Provenance:** Copilot / Kimi K3 — Repository boundaries list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/CoursesRepositoryImplTest.kt`.
- **Work:**
  - Add to `CourseDao`: `@Query("SELECT * FROM courses WHERE courseTitleNormal LIKE '%' || :token || '%' ESCAPE '\\'") suspend fun filterByTitleToken(token: String): List<MyCourse>`.
  - In `search()`: issue one DAO call per normalized token and intersect results by `id` in Kotlin (token count is small — split on space), preserving the existing escape rules (`\`, `%`, `_`) applied to each token before the call. Keep the existing starts-with/contains ranking (lines 294–303) untouched.
  - Delete `filterByTitleNormal` from the DAO (confirm no other caller: `grep -rn filterByTitleNormal`) and the `SimpleSQLiteQuery`/`androidx.sqlite.db.SupportSQLiteQuery` imports from both files.
  - Extend `CoursesRepositoryImplTest` (or a Room-in-memory DAO test per docs/TESTING.md) with: multi-token query matches only rows containing all tokens; tokens containing `%`/`_` are literal; empty query returns all.

---

## 72/100 — Introduce `PublicSurveyViewModel` for dual-repository orchestration
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt`.
- **Work:**
  - Move `buildPublicAnswers`, `sanitizeRespondent`, and upload orchestration into the VM; expose a single `submitIfCompleted(surveyId, baseUrl, teamId, launchTime): Boolean` (or sealed result).
  - Activity: `by viewModels()`, call VM from `uploadCompletedSubmission`, keep Toast/navigation.
  - Unit-test VM with MockK repositories (success, missing submission, stale `lastUpdateTime`, malformed user JSON).

---

## 72/100 — Memoize ResourcesPreviewLoader audio/text/csv work
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt`.
- **Work:**
  - Add a small bounded in-memory cache keyed by `absolutePath + lastModified + length` (same idea as adapter `getCacheKey`) for audio duration string, csv preview, and text preview.
  - Cap entries (e.g. LRU ≤ 32–64) to avoid leaks; optional `clear()` for tests.
  - Keep `DispatcherProvider.io` usage; swallow errors as today (`""` / `null`).

---

## 72/100 — Push notification display formatting from ViewModel into repository DTOs
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`.
- **Work:**
  - Identify strings currently formatted in the VM (`formatNotification` and helpers).
  - Produce those strings (or stable message keys + already-localized text) from the repository side so the VM can drop Context. Prefer removing Context from the VM entirely.
  - VM: map DTO → `Notification` list; delete Context field if unused.
  - Tests: repo covers formatting edge cases (task/join/team); VM covers grouping/selection only.

---

## 69/100 — Fewer allocations in TTSManager.stripMarkdown
- **Provenance:** Copilot / Grok 4.5 — Repository boundaries list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/TTSManagerTest.kt`.
- **Work:**
  - Reduce passes: combine compatible replacements where order-safe, or scan once with a small state machine / staged replaces only when a cheap `contains` pre-check finds markdown markers.
  - Preserve observable stripping behavior for code fences, inline code, headers, links/images (keep link text), bold/italic markers, lists, blockquotes, horizontal rules, table pipes → space, final trim.
  - Do not change `formatCsvForSpeech` unless a one-line shared StringBuilder helper clearly helps without risk.

---

## 68/100 — Fix nullable `userId` SQL predicates on `NotificationDao`
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`.
- **Work:**
  - Audit every `@Query` in `NotificationDao` for `userId`.
  - Change `userId = :userId` → `userId IS :userId` where the Kotlin param is `String?` or null must match.
  - Leave non-nullable filters alone.
  - Prefer a focused `NotificationDaoTest` for null vs non-null `userId` unread counts / list filters.

---

## 68/100 — Move `LifeCache` out of the repository package
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`.
- **Work:**
  - Move file; set `package org.ole.planet.myplanet.data.cache`.
  - Update `LifeRepositoryImpl` import of `LifeCache`.
  - Update test package/imports; assert cache hit/miss behavior unchanged.

---

## 66/100 — resolve the lazy teams repository once per upload result batch (roadmap 4+5+7)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`, `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt`.
- **Work:**
  - Resolve `teamsSyncRepository.get()` once at the start of each of the two `markUploaded` lambdas.
  - Reuse that local repository for every result while preserving sequential suspend calls and failed-result filtering.
  - Keep the lazy injection itself so construction does not reintroduce a dependency cycle.
  - Extend tests with multi-result batches and verify each row is marked exactly once with unchanged failure output.
  - Verify no eager repository resolution occurs when an upload config is constructed but not executed.

---

## 65/100 — Remove `MainApplication` statics from `ResourcesRepositoryImpl`
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`.
- **Work:**
  - Locate every `MainApplication` reference; replace path access with `storagePathResolver.resolveOleDirectory()` / path joins equivalent to `ole/$resourceId`.
  - Replace applicationScope launch with suspend + `dispatcherProvider` (or `@ApplicationScope CoroutineScope`).
  - Update tests that mock filesystem/scope assumptions.

---

## 65/100 — Split PDF I/O out of `SubmissionsRepositoryExporter` data assembly
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt`.
- **Work:**
  - Introduce a small pure model (e.g. title, status lines, Q&A lines) built only from DAOs/`TimeProvider`.
  - Move `PdfDocument` drawing and output `File` creation into `SubmissionPdfWriter`.
  - Exporter: assemble model → delegate write; delete Android graphics imports from exporter if possible.
  - Update tests to cover assembly and/or writer with temp files.

---

## 64/100 — hoist the per-attachment SharedPreferences read and dedupe attachments in MyLibrary.insertMyLibrary (roadmap 1+7)
- **Provenance:** Devin / SWE 2 — Performance list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt`.
- **Work:**
  - Resolve `params.spm.getCouchdbUrl().ifEmpty { "http://" }` once before the `entrySet().forEach` loop into a local `baseUrl` val and use it inside the loop.
  - Keep the `FileUtils.checkFileExist` call inside the loop but only for keys without `/` (the existing `if (key.indexOf("/") < 0)` guard stays).
  - Before building `attachmentList`, capture the set of existing attachment names (`this.attachments?.map { it.name }?.toSet()`); inside the loop, skip `add` when `key` is already in that set.
  - Run the unit tests.

---

## 64/100 — Peel `Context` off `DictionaryFileReaderImpl`
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryFileReader.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`.
- **Work:**
  - Replace `@ApplicationContext Context` with `StoragePathResolver`.
  - Implement `exists()` / `readText()` via `resolveFileFromUrl(Constants.DICTIONARY_URL)` (and existing `FileUtils.getStringFromFile` on the resolved `File` if still needed).
  - Update dictionary repository tests’ reader mocks/fakes if they construct the impl; interface-based tests should stay green.

---

## 63/100 — cache the HTML cover-image directory scan in FileUtils (roadmap 7)
- **Provenance:** Claude / Opus 5 — Performance list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt`.
- **Work:**
  - Add a private `android.util.LruCache<String, java.util.Optional<File>>` (or a small `LruCache<String, File>` plus a separate negative-result marker — pick one and keep it consistent) sized around 64 entries, declared next to `previewImageExtensions`.
  - Key the cache on `resourceDir.absolutePath + "_" + resourceDir.lastModified()` so a re-downloaded resource misses the cache.
  - At the top of `findHtmlCoverImage`, return the cached value when present; at each `return` site, store the result (including the `null` result) before returning.
  - Keep the existing early `if (!resourceDir.isDirectory) return null` uncached.
  - Run the unit tests.

---

## 63/100 — reuse the existing file-existence cache for course cover art (roadmap 7)
- **Provenance:** Claude / Opus 5 — Performance list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/CoursesItemUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`.
- **Work:**
  - Add `private val coverExistenceCache = FileExistenceCache()` and `private val timeProvider: TimeProvider = SystemTimeProvider()` as private members of the `CoursesItemUtils` object.
  - Replace `coverFile?.exists() == true` at line 60 with `coverExistenceCache.exists(coverFile, timeProvider.now())`.
  - Add the two imports (`FileExistenceCache` and `SystemTimeProvider` / `TimeProvider` are in the same `utils` package, so no import is needed — confirm and add none).
  - Run the unit tests.

---

## 60/100 — compute the storage selection counters in one pass (roadmap 3+7+10)
- **Provenance:** Claude / Opus 5 — Performance list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategories.kt`.
- **Work:**
  - Add `val checkedCount: Int = 0` as a real constructor property of `StorageCategoryUiState`, replacing the computed getter at line 25.
  - Redefine `allChecked` as `val allChecked: Boolean get() = checkedCount == items.size && items.isNotEmpty()` — one comparison, no second scan.
  - In `loadResources`, set `checkedCount = 0` alongside the loaded items (freshly loaded items are unchecked).
  - In `toggleItemChecked` and `toggleAllChecked`, derive the new `checkedCount` from the single `map` already being performed (increment while mapping, or count the produced list once) and pass it into `state.copy(...)`.
  - Leave `deleteSelected` (line 77) and `deleteAll` as they are.
  - Run the unit tests.

---

## 60/100 — read each progress JSON field once (roadmap 3+7)
- **Provenance:** Claude / Opus 5 — Performance list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/model/CoursesProgressRow.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt`.
- **Work:**
  - Replace the `obj.has("stepMistake")` / `obj.get("stepMistake")` pair with a single `obj.get("stepMistake")` into a local, then map it through `gson.fromJson(..., type)` only when it is non-null and not `JsonNull`.
  - Hoist `obj.getAsJsonObject("progress")` into one local and read `current` and `max` off it.
  - Keep `courseId`, `courseName` and `mistakes` reading exactly as they do today, including the existing null handling.
  - Run the unit tests.

---

## 60/100 — replace the regex split and no-op trim in the health examination list (roadmap 7+8)
- **Provenance:** Claude / Opus 5 — Performance list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt`.
- **Work:**
  - Replace the split chain at line 69 with `createdBy.substringAfter(':', "").takeIf { it.isNotBlank() }`, keeping the surrounding `model?.getFullName() ?: ... ?: createdBy` fallback chain and the `displayNameCache` memoization exactly as they are.
  - Delete the now-unused `colonRegex` declaration at line 176.
  - Remove the `.trimIndent()` at line 104.
  - Remove any import that becomes unused.
  - Run the unit tests.

---

## 60/100 — share one OkHttp connection pool between the two HTTP clients (roadmap 4+7)
- **Provenance:** Claude / Opus 5 — Performance list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt`, `app/src/main/java/org/ole/planet/myplanet/data/api/ApiClient.kt`, `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt`.
- **Work:**
  - Hoist the `ConnectionPool` out of `buildOkHttpClient` into a single `private val` (or a `@Provides @Singleton` function) in the `NetworkModule` object, keeping the same `(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)` arguments.
  - Have `buildOkHttpClient` pass that shared instance to `.connectionPool(...)`.
  - Leave each client's own `Dispatcher` in place — sharing the dispatcher would queue reachability probes behind sync traffic, which is not wanted.
  - Leave the three timeout constant sets and the `TaggedSocketFactory` untouched.
  - Run the unit tests.

---

## 60/100 — stop re-filtering the resource id list during sync cleanup (roadmap 5+7)
- **Provenance:** Claude / Opus 5 — Performance list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`.
- **Work:**
  - Change line 294 to `val newIds: MutableList<String> = ArrayList()`.
  - Delete the `validNewIds` local at line 411 and pass `newIds` straight to `resourcesRepository.removeDeletedResources(...)`.
  - Reduce the guard at line 414 to `newIds.isNotEmpty()`, keeping the `hadBatchFailure` branch above it exactly as it is.
  - Change the `logDbOperation("delete_cleanup", "resources", cleanupDuration, ...)` item count at line 420 from `newIds.size - validNewIds.size` (always 0) to `newIds.size`.
  - Run the unit tests.

---

## 59/100 — avoid sequence overhead in valid log file check (roadmap 7)
- **Provenance:** Jules / Gemini 3.1 Pro — Performance list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt`.
- **Work:**
  - Replace the sequence operations with a simple `pendingFiles.count { isValidLogFile(it) }` if we cap valid Count at the `if` check beneath it
  - Alternatively, keep the cap logic but use regular iterables.
  - Test.

---

## 59/100 — Cache per-row date strings in `HealthUsersAdapter.bindDate`
- **Provenance:** Copilot / Kimi K3 — Performance list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt`.
- **Work:**
  - hoist a `HashMap<Long, String>` to the adapter (not the ViewHolder, so it survives recycling); wrap the format call with `getOrPut`. Confirm `joinDate` nullability and key on `?: 0L` if needed.

---

## 59/100 — Cache per-row date strings in `PersonalsAdapter`
- **Provenance:** Copilot / Kimi K3 — Performance list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt`.
- **Work:**
  - add a private `HashMap<Long, String>` date cache in the adapter; wrap the call as `dateCache.getOrPut(item.date) { getFormattedDate(item.date) }`, mirroring the existing pattern in `ui/teams/TeamsAdapter.kt:62` (`dateCache.getOrPut(...)`). No other behaviour changes.

---

## 59/100 — Cache per-row date strings in `UserArrayAdapter`
- **Provenance:** Copilot / Kimi K3 — Performance list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt`.
- **Work:**
  - add a `HashMap<Long, String>` field; wrap as `dateCache.getOrPut(user.joinDate) { TimeUtils.formatDate(user.joinDate) }`. If `joinDate` is nullable, key on `?: 0L`.

---

## 59/100 — change filteredExistingUsers mapTo HashSet to mapNotNullTo (roadmap 7)
- **Provenance:** Jules / Gemini 3.1 Pro — Performance list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`.
- **Work:**
  - Change the line to use `.mapNotNullTo(HashSet())`
  - Also replace `members.mapTo(HashSet()) { it.name }` on line 970.
  - Verify Room DAO compilation and run tests.

---

## 59/100 — collapse the double file resolution in InlineResourceAdapter.updateStatusAndPreview (roadmap 7)
- **Provenance:** Devin / SWE 2 — Performance list, task 6.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`.
- **Work:**
  - In `updateStatusAndPreview`, resolve the file once — `val file = FileUtils.getSDPathFromUrl(context, UrlUtils.getUrl(resource))` (or the same helper `checkFileExist` uses internally) — then do a single `file.exists()` check and reuse that `File` for the rest of the method.
  - Pass the already-resolved `File` down into the preview helpers instead of letting them re-resolve; if a helper signature must stay, only re-stat on it once.
  - In `getFileCacheKeyIfExist`, reuse the single `exists()`/`lastModified`/`length` results already gathered rather than re-statting.
  - Run the unit tests.

---

## 59/100 — convert the diagnosis lookup to a set and hoist per-checkbox styling in HealthExaminationActivity (roadmap 7)
- **Provenance:** Devin / SWE 2 — Performance list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt`.
- **Work:**
  - In `preloadCustomDiagnosis`, change `listOf(*arr)` to `setOf(*arr)` so `mainList` is a `Set<String>`; keep the `!mainList.contains(s)` check.
  - In `showCheckbox`, before the `for (s in arr)` loop, hoist `ContextCompat.getColorStateList(this, R.color.daynight_textColor)` into a local `tintList` val, `ContextCompat.getColor(this, R.color.daynight_textColor)` into a `textColor` val, and `dpToPx(8)` into a `padPx` val.
  - Inside the loop, assign `c.buttonTintList = tintList`, `c.setTextColor(textColor)`, `c.setPadding(padPx, padPx, padPx, padPx)`.
  - Run the unit tests.

---

## 59/100 — drop the redundant distinct pass when ordering notification groups (roadmap 3+7)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`.
- **Work:**
  - Remove the redundant deduplication from construction of `orderedTypes`.
  - Retain `TYPE_ORDER` precedence and the existing iteration order for unknown grouped types.
  - Preserve lowercasing, fallback to `notification`, unread counts, and item order within groups.
  - Add or tighten a mixed known/unknown type test asserting exact group order and no duplicates.
  - Keep grouping as ViewModel-owned state with no resource or Android-view access added.

---

## 59/100 — eliminate the temporary candidate list when choosing the next download (roadmap 5+7)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceUrlSelectionTest.kt`.
- **Work:**
  - Replace the eager filtered-list pipeline with a lazy sequence or a single-pass minimum selection.
  - Retain both eligibility predicates: ignore blank URLs and URLs already in `processedUrls`.
  - Keep lexicographic minimum ordering and the incoming `isPriority` value exactly as today.
  - Add or tighten tests for mixed processed/blank values, an empty eligible set, and deterministic minimum selection.
  - Confirm the method still reads the selected preference set only once.

---

## 59/100 — hoist the loop-invariant JSON read in ExamQuestion.insertCorrectChoice (roadmap 1+7)
- **Provenance:** Devin / SWE 2 — Performance list, task 2.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/model/ExamQuestion.kt`.
- **Work:**
  - In the else branch, read `JsonUtils.getString("correctChoice", question)` once into a local val before the `for` loop.
  - Inside the loop, compare that val to `JsonUtils.getString("id", res)`; on a match assign `correctChoiceList` and `break` (only the first matching id should win — preserve that).
  - Run the unit tests.

---

## 59/100 — make resource-filter signatures order-independent without sorting (roadmap 6+7)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt`.
- **Work:**
  - Represent `Signature.searchTagIds` as a set rather than a sorted list.
  - Build that set directly from `searchTags` without an intermediate mapped list or sort.
  - Preserve defensive copies for every mutable set entering the signature.
  - Add a regression test showing that reordering identical tags does not trigger filtering again.
  - Add coverage showing a genuinely different tag ID still invalidates the cached signature.

---

## 59/100 — optimize areAllSelected list count condition (roadmap 7)
- **Provenance:** Jules / Gemini 3.1 Pro — Performance list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`.
- **Work:**
  - Update the `count` var to `if (isMyCourseLib) currentList.size else currentList.count { !it.isMyCourse }`
  - Verify compilation.
  - Run unit tests.

---

## 59/100 — reject mismatched multi-select answers before sorting (roadmap 7+9)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`.
- **Work:**
  - After the existing null guard, return false immediately when selected and correct collection sizes differ.
  - Preserve case-insensitive, order-insensitive comparison for equal-sized collections.
  - Preserve duplicate-answer semantics by retaining the sorted-list comparison for the remaining path.
  - Expand tests for subset, superset, reversed order, mixed case, and duplicate values.
  - Do not add Android APIs or locale-independent behavior changes.

---

## 59/100 — remove redundant coroutine creation from course loading (roadmap 3+7)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 3.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesViewModelTest.kt`.
- **Work:**
  - Call `progressRepository.getCourseProgress` directly inside the existing IO context.
  - Remove only the now-unused `async` and `coroutineScope` imports.
  - Preserve exception handling, repository call order, course IDs, and the single final state publication.
  - Add or strengthen a test proving one progress lookup occurs and its map is included in `coursesState`.
  - Keep all state hoisted in the ViewModel without introducing Android view or resource access.

---

## 59/100 — replace lowercase extension map lookup with direct map lookup (roadmap 7)
- **Provenance:** Jules / Gemini 3.1 Pro — Performance list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategories.kt`.
- **Work:**
  - Update `indexOf` to `extensionToIndex[extension] ?: extensionToIndex[extension.lowercase()] ?: OTHER_INDEX`
  - Ensure tests compile.
  - Run unit tests to verify.

---

## 59/100 — replace mutable list creation overhead in TeamCalendarFragment (roadmap 7)
- **Provenance:** Jules / Gemini 3.1 Pro — Performance list, task 8.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarFragment.kt`.
- **Work:**
  - Change `.mapTo(mutableListOf())` to `.map`
  - Verify compilation.
  - Run unit tests.

---

## 59/100 — resolve the local resource directory once in WebViewActivity instead of per navigation (roadmap 7)
- **Provenance:** Devin / SWE 2 — Performance list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt`.
- **Work:**
  - Add a private field initialized once in `onCreate` (or as a `lazy` val) holding the resolved `File?` from `getLocalResourceDirectory(intent.getStringExtra("RESOURCE_ID"))`.
  - Replace the three re-resolutions in `setupWebView`/`setupAssetLoader`/`checkUrlSafety` with the cached field.
  - In `checkUrlSafety`, also hoist the per-call `getExternalFilesDir` (line 290) into the same cached value or a second cached field — keep the `canonicalFile` safety checks that guard against path traversal.
  - Run the unit tests.

---

## 59/100 — strip null team JSON fields without allocating key snapshots (roadmap 1+7+9)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt`, `app/src/test/java/org/ole/planet/myplanet/model/MyTeamTest.kt`.
- **Work:**
  - Replace each filter-then-remove sequence with safe in-place iteration over the JSON entries or keys.
  - Use iterator removal rather than mutating the object separately during iteration.
  - Apply the same helper or compact pattern to both the `resourceLink` early-return branch and the general branch.
  - Extend tests to cover null stripping and non-null retention in both serialization branches.
  - Confirm `_deleted` serialization and course-enriched serialization remain untouched.

---

## 59/100 — use constant-time course-id membership during adapter removal (roadmap 7)
- **Provenance:** Codex / GPT-5.6 Sol — Performance list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt`.
- **Work:**
  - In `removeCourses`, return through the existing `submitList` path without building a lookup set when `courseIds` is empty.
  - Convert non-empty `courseIds` to a set once before filtering `currentList`.
  - Preserve the existing callback timing by continuing to invoke `onComplete` only from the `submitList` commit callback.
  - Extend `CoursesAdapterTest` with retained/removed IDs, duplicate removal IDs, and callback assertions.
  - Remove any imports made unused by the edit and keep the adapter's existing stable-ID behavior unchanged.

---

## 58/100 — memoize the derived messageList getter and fix stale cache invalidation in Feedback (roadmap 1+7)
- **Provenance:** Devin / SWE 2 — Performance list, task 9.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt`.
- **Work:**
  - Keep the existing `cachedMessages`/`parsedMessages` mechanism; extend it to also cache the derived `List<FeedbackReply>` so `messageList` doesn't rebuild it per call.
  - In `setMessages` (and anywhere `messages` is reassigned), invalidate both the raw `JsonArray` cache and the derived list cache so stale parses can't leak.
  - Ensure `message` getter uses the same memoized list rather than re-deriving.
  - Run the unit tests.

---

## 58/100 — Stop double formatting in `FeedbackAdapter.onBindViewHolder`
- **Provenance:** Copilot / Kimi K3 — Performance list, task 5.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt`.
- **Work:**
  - add a `HashMap<Long, String>` date cache keyed on `feedback.openTime`; reuse the cached string for both the contentDescription and `tvOpenDate`. Keep `statusText`/`priorityText`/`openDateText` resolution as-is.

---

## 58/100 — Stop static `NetworkUtils.getDeviceName()` in Personals upload serialization
- **Provenance:** Copilot / Grok 4.5 — Performance list, task 1.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`.
- **Work:**
  - In `serialize`, replace `NetworkUtils.getDeviceName()` with the injected `deviceNameProvider` equivalent (same value the rest of the app uses for device name).
  - Remove the unused `NetworkUtils` import.
  - Adjust/extend unit tests that assert serialized upload JSON so both `deviceName` and `customDeviceName` come from the mocked provider.

---

## 57/100 — move per-event accessibility strings in LifeAdapter to bind time (roadmap 7)
- **Provenance:** Devin / SWE 2 — Performance list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`.
- **Work:**
  - In `onBindViewHolder`, directly assign `holder.dragImageButton.contentDescription = context.getString(R.string.drag, myLife.title)` right after the other binds (near line 58's `imageView.contentDescription`).
  - Assign `holder.visibility.contentDescription = context.getString(R.string.visibility_of, myLife.title)` the same way.
  - Remove the `contentDescription` assignments from inside the touch listener (line 67) and the click listener (line 74); keep the listeners' drag/visibility behavior intact.
  - Run the unit tests.

---

## 56/100 — remove array list allocation on download utils mapping (roadmap 7)
- **Provenance:** Jules / Gemini 3.1 Pro — Performance list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`.
- **Work:**
  - Use the more concise `.map { ... } as ArrayList<String>` or `.mapTo(ArrayList(dbMyLibrary.size)) { ... }` to pre-allocate capacity.
  - Ensure compilation.
  - Verify execution.

---

## 55/100 — memoize the re-parsing valueChoicesArray getter in Answer (roadmap 1+7)
- **Provenance:** Devin / SWE 2 — Performance list, task 4.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/model/Answer.kt`.
- **Work:**
  - Add a private `@Ignore` var (e.g. `cachedChoicesArray`) alongside a marker of what `valueChoices` list it was built from (an identity or content check is fine — the list is only set once before serialization).
  - In the getter, return the cached array when `valueChoices` is unchanged; otherwise rebuild it and store it.
  - Keep the null/empty `valueChoices` behavior identical — empty `JsonArray`.
  - Run the unit tests.

---

## 52/100 — stop allocating a Typeface on every group-header bind in ChatShareTargetAdapter (roadmap 7)
- **Provenance:** Devin / SWE 2 — Performance list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt`.
- **Work:**
  - In `GroupViewHolder`'s `init` block (or on first `bind`), set the bold typeface once on `listTitleTextView` — e.g. `listTitleTextView.setTypeface(listTitleTextView.typeface, Typeface.BOLD)` to reuse the existing typeface instead of `setTypeface(null, Typeface.BOLD)`.
  - Remove the per-bind `setTypeface(null, Typeface.BOLD)` call from `bind`.
  - Run the unit tests.

---

## 50/100 — Replace `printStackTrace()` with `Log.w` in `TimeUtils`
- **Provenance:** Copilot / Kimi K3 — Performance list, task 7.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt`.
- **Work:**
  - add a `private const val TAG = "TimeUtils"` and replace each `e.printStackTrace()` with `Log.w(TAG, "<fnName> failed", e)` guarded by `Log.isLoggable(TAG, Log.WARN)` only where cheap; otherwise plain `Log.w`. Keep return values identical.

---

## 47/100 — Replace `printStackTrace()` with structured logging in `VoicesLabelManager`
- **Provenance:** Copilot / Kimi K3 — Performance list, task 10.
- **Verified evidence:** Current-tree inspection confirmed the premise in `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt`.
- **Work:**
  - add a `private const val TAG = "VoicesLabelManager"` in its companion (it already has one at line ~130) and replace each `printStackTrace()` with `Log.w(TAG, "addLabel failed", e)` / `"removeLabel failed"`. Keep the `Utilities.toast` user feedback and retry semantics unchanged.

---

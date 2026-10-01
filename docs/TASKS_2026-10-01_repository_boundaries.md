# myPlanet refactor round: repository boundaries

date: 2026-10-01 · base commit: `286e66e` (master, "all: smoother importing (fixes #17814)") · open PRs checked: #17812, #17811, #17776, #17694, #17680, #17435, #17254, #17187, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075 (all 33 open PRs)

**How collisions were ruled out:** each open PR's head branch was diffed against its merge-base with `master`. That gave 350 touched paths, and no task below edits any of them. Some files other PRs own are named as "leave alone" neighbors and must not be edited: `TeamsRepositoryImpl`, `ResourcesRepositoryImpl`, `UserRepositoryImpl`, `RepositoryModule`, `AppDatabase`, `HealthRepository(Impl)` and every `*ViewModel` those PRs touch. #15226 (Flutter port) touches no file under `app/`.

**Order:** tasks are ranked by user impact divided by blast radius. Any task can merge in any order, and no file appears in more than one task.

Paths are written in full. `M/` = `app/src/main/java/org/ole/planet/myplanet/` and `T/` = `app/src/test/java/org/ole/planet/myplanet/`, both relative to the repo root.

---

### 1. make notification sync merge and read/delete check-then-act atomic in NotificationDao (roadmap 1+5+7, moves 9)

context: `NotificationsRepositoryImpl.bulkInsertFromSync` (M/repository/NotificationsRepositoryImpl.kt:503-519) loads full rows with `notificationDao.getByIds(...)` just to read `needsSync`/`isRead`. It then calls `upsertAll` as a separate statement. A mark-as-read that lands between the two is overwritten by the server copy. `insert(doc)` (:484-492) has the same read-then-upsert race. Three more methods do a select then a write outside a transaction, so the ids they return can disagree with the rows they changed: `markAllUnreadAsRead` (:129-135: `getUnreadIds` then `markAllUnreadAsRead`), `markNotificationsAsRead` (:120-127) and `deleteNotifications` (:494-501, both `getIdsByIds` then a write).
files:
- M/data/room/dao/NotificationDao.kt: add new `@Transaction` default methods.
- M/repository/NotificationsRepositoryImpl.kt: methods `insert`, `bulkInsertFromSync`, `markAllUnreadAsRead`, `markNotificationsAsRead`, `deleteNotifications`.
- T/repository/NotificationsRepositoryImplTest.kt
- T/data/room/dao/NotificationDaoTest.kt

Leave alone: M/data/room/dao/TeamNotificationDao.kt, `updateCountNotification` and `updateTeamNotification` in the same Impl, and M/repository/NotificationsRepository.kt (the interface does not change).
steps:
1. In NotificationDao.kt, add a projection query `SELECT id, isRead FROM notifications WHERE needsSync = 1 AND id IN (:ids)` that returns a small `data class PendingLocalState(val id: String, val isRead: Boolean)`. Declare that class in the same file. Wrap the query in a chunked (900) default method.
2. Add `@Transaction suspend fun upsertFromServerPreservingLocal(incoming: List<AppNotification>)`. It reads that projection for the incoming ids, copies `needsSync = true` plus the local `isRead` onto each matching incoming row, then calls `upsertAll(incoming)`.
3. Add three `@Transaction` default methods, each returning the affected id list:
   - `markAllUnreadAsReadReturningIds(userId, createdAt)`: `getUnreadIds` + `markAllUnreadAsRead`
   - `markExistingAsRead(ids, createdAt)`: `getIdsByIds` + `markAsRead`
   - `deleteExisting(ids)`: `getIdsByIds` + `deleteByIds`
4. In NotificationsRepositoryImpl, make `bulkInsertFromSync` and `insert` (as `listOf(parsed)`) each a single call to `upsertFromServerPreservingLocal`. Make the three read/delete methods single calls to the new DAO methods, and keep their existing empty-input early returns.
5. Update the repository test stubs (`getById`/`getByIds`/`getUnreadIds`/`getIdsByIds` at T/repository/NotificationsRepositoryImplTest.kt:370-427 and :464-492) to the new DAO calls. In NotificationDaoTest, add one Room in-memory test: a row with `needsSync = 1, isRead = 1` keeps `isRead = 1` after a server upsert that carries `isRead = 0`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest" --tests "*NotificationDaoTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, a notification marked read while a sync is running is still read after the sync finishes. "Mark all as read" still clears the unread badge.
size budget: ~110 changed lines, 4 files
out of scope: no schema or index changes (AppDatabase is owned by open PRs). Do not touch team-notification counting.

---

### 2. slim the retry-queue lookup and drop dead RetryRepository/RetryDao surface (roadmap 1+5+7+8, moves 9)

context: `RetryRepositoryImpl.recordFailure` (M/repository/RetryRepositoryImpl.kt:42-52) calls `retryDao.findExisting(...)` and uses only `existing.id`. The query (M/data/room/dao/RetryDao.kt:38-42) is `SELECT *`, so every upload failure also pulls the stored `serializedPayload` JSON. The interface also exposes `getPendingCount()` and `deletePendingAndAbandonedOperations()` (M/repository/RetryRepository.kt:30,32). Both are called only inside the Impl (:176, :185), and `RetryDao.update` (RetryDao.kt:19-20) has no caller in main or test.
files:
- M/data/room/dao/RetryDao.kt: `findExisting`, `update`.
- M/repository/RetryRepository.kt: interface lines 30 and 32.
- M/repository/RetryRepositoryImpl.kt: `recordFailure`, `getPendingCount` (:144), `deletePendingAndAbandonedOperations` (:153).
- T/repository/RetryRepositoryImplTest.kt
- T/data/room/dao/RetryDaoTest.kt

Leave alone: M/services/retry/RetryQueue.kt, M/services/retry/RetryQueueWorker.kt, T/services/retry/RetryQueueTest.kt (owned by an open PR), M/ui/settings/SettingsViewModel.kt, M/ui/settings/SettingsActivity.kt.
steps:
1. Replace `findExisting` with `findExistingId(itemId, uploadType): String?` using `SELECT id FROM retry_operation WHERE ... LIMIT 1`, with the same WHERE clause.
2. In `recordFailure`, use the returned id: `markFailed(id, ...)` if non-null, otherwise insert. Keep the existing `mutex.withLock`.
3. Delete the unused `@Update suspend fun update(operation)` from RetryDao, and its now-unused `androidx.room.Update` import.
4. Remove `getPendingCount` and `deletePendingAndAbandonedOperations` from `RetryRepository`. Make them `private` in the Impl, without `override`.
5. Tests:
   - Change the `findExisting` stubs at T/repository/RetryRepositoryImplTest.kt:72,104,120 to `findExistingId` returning `null` or `"existingId"`.
   - Make the tests at :357 and :374 assert through `getRetryQueueSnapshot().pendingCount` and `safeClearQueue()`.
   - Add one RetryDaoTest case: `findExistingId` ignores `completed` and `abandoned` rows.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RetryRepositoryImplTest" --tests "*RetryDaoTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. Settings → debug "Retry queue" dialog still shows the pending count, and "Clear retry queue" still empties it.
size budget: ~45 changed lines, 5 files
out of scope: do not change the snapshot shape (`RetryQueueDetails`), the worker, or retry scheduling.

---

### 3. replace the enterprise-report read-modify-upsert with one targeted UPDATE (roadmap 1+7, moves 9)

context: `EnterprisesRepositoryImpl.updateReport` (M/repository/EnterprisesRepositoryImpl.kt:55-79) builds a Gson `JsonObject`, loads the whole `teams` row through `updateTeamEntityById` (:129-135: `teamDao.getById(id)` then `teamDao.upsert(model)`), and rewrites every column. A sync write between the read and the upsert is lost. `MyTeam.populateReportFields` (M/model/MyTeam.kt:149-162) only sets 10 report columns. The doc passed in has no `_attachments`, so `imageName` never changes on this path. `TeamDao.getNonArchivedReportsByTeamId` (M/data/room/dao/TeamDao.kt:25) has no production caller; it duplicates the `observe…` query on :24 and is used only by four tests.
files:
- M/data/room/dao/TeamDao.kt: add `updateReportFields`, remove `getNonArchivedReportsByTeamId`.
- M/repository/EnterprisesRepositoryImpl.kt: `updateReport`, delete the private `updateTeamEntityById`.
- T/repository/EnterprisesRepositoryImplTest.kt
- T/data/room/dao/TeamDaoTest.kt

Leave alone: M/repository/TeamsRepositoryImpl.kt (its own private `updateTeamEntityById` stays; open PRs own the file), `TeamDao.getById`/`getByIds`/`deleteByIds`, M/model/MyTeam.kt.
steps:
1. Add `@Query("UPDATE teams SET description = :description, beginningBalance = :beginningBalance, sales = :sales, otherIncome = :otherIncome, wages = :wages, otherExpenses = :otherExpenses, startDate = :startDate, endDate = :endDate, updatedDate = :updatedDate, isUpdated = 1 WHERE _id = :id") suspend fun updateReportFields(...): Int` to TeamDao. The column for `updated` is `isUpdated` (MyTeam.kt:50).
2. In `updateReport`, drop the `JsonObject` and call `teamDao.updateReportFields(reportId, payload.description, ..., timeProvider.now())`. Keep the blank-id guard and the image-attachment branch exactly as they are.
3. Delete the private `updateTeamEntityById` and any imports left unused (`JsonObject` is still used by `addReport`, so it stays).
4. Delete `getNonArchivedReportsByTeamId` from TeamDao. Retarget its four tests (T/data/room/dao/TeamDaoTest.kt:111-165) to `observeNonArchivedReportsByTeamId(...).first()`.
5. Add an EnterprisesRepositoryImplTest case: `updateReport` calls `updateReportFields` once and never calls `getById` or `upsert`. Add a TeamDaoTest case: `updateReportFields` changes the report columns and sets `isUpdated`, but leaves `imageName` and `_rev` untouched.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*EnterprisesRepositoryImplTest" --tests "*TeamDaoTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, editing an enterprise finance report updates the list row (totals included) and the CSV export. A report's image is still shown after editing its numbers.
size budget: ~60 changed lines (≈20 removed), 4 files
out of scope: do not touch `addReport`, `archiveReport` or the Teams repository.

---

### 4. chunk the remaining unbounded IN-list DAO queries and drop dead DAO methods (roadmap 1+7+8)

context: minSdk is 26, where SQLite caps bound variables at 999. Four `IN (:list)` queries take caller-sized lists without chunking:
- `MyLibraryDao.getByIds`, `getByResourceIds` and `getByResourceIdsNotUserPattern` (M/data/room/dao/MyLibraryDao.kt:35-36, 38-39, 154-158). Unchunked callers: `ResourcesRepositoryImpl.kt:193, 198, 534, 594`.
- `QuestionDao.getByIds` (M/data/room/dao/QuestionDao.kt:10). `ProgressRepositoryImpl.kt:186` passes every answered question id.

Separately, three DAO methods have zero callers in main and test: `QuestionDao.upsertAllBlocking` (:21), `CourseStepDao.upsertAllBlocking` (M/data/room/dao/CourseStepDao.kt:18) and `MyLibraryDao.getByCourseId` (MyLibraryDao.kt:53-54).
files:
- M/data/room/dao/MyLibraryDao.kt
- M/data/room/dao/QuestionDao.kt
- M/data/room/dao/CourseStepDao.kt
- T/data/room/dao/MyLibraryDaoTest.kt
- T/data/room/dao/QuestionDaoTest.kt

Leave alone: every caller (ResourcesRepositoryImpl, ProgressRepositoryImpl, CoursesRepositoryImpl — all owned by open PRs; they must compile unchanged), and the `upsertAllBlocking` methods in AnswerDao/SubmissionDao/CourseDao/ExamDao, which are still used.
steps:
1. For each of the four queries, rename the `@Query` method to `…Internal`. Add a default method with the original name and signature that returns `emptyList()` for empty input, otherwise `ids.distinct().chunked(900).flatMap { …Internal(it) }`. This follows the existing `getByCourseIds` pattern at MyLibraryDao.kt:56-62.
2. Delete `QuestionDao.upsertAllBlocking`, `CourseStepDao.upsertAllBlocking` and `MyLibraryDao.getByCourseId`.
3. In MyLibraryDaoTest, add one in-memory test that inserts 1,000+ rows and gets them all back through `getByIds`. Add one for `getByResourceIdsNotUserPattern` that crosses a chunk boundary.
4. Add one QuestionDaoTest case for `getByIds` with more than 900 ids, including duplicates.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*MyLibraryDaoTest" --tests "*QuestionDaoTest" --tests "*ProgressRepositoryImplTest" --tests "*ResourcesRepositoryImplTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, the library screen, "add all to My Library", and the progress screen for a user with many answers behave as before.
size budget: ~70 changed lines, 5 files
out of scope: no caller changes, no index additions (AppDatabase is owned by open PRs).

---

### 5. replace localized strings in ConfigurationsRepository's version-check contract with a typed error (roadmap 1+8, moves 9)

context: the repository contract carries UI text. `CheckVersionCallback.onError(msg: String, blockSync: Boolean)` (M/repository/ConfigurationsRepository.kt:38-42) is fed `context.getString(R.string.…)` from `ConfigurationsRepositoryImpl.checkVersion` (M/repository/ConfigurationsRepositoryImpl.kt:105, 134, 147, 153-156, 168) and `handleVersionEvaluation` (:552). The only UI consumer then string-matches it: `SyncActivity.onError` (M/ui/sync/SyncActivity.kt:767-771) runs `if (msg.startsWith("Config")) settingDialog()`. No message starts with "Config" (`server_url_not_configured` = "Server URL not configured"), so the server-settings dialog never opens. The interface also exposes four methods nothing outside the Impl calls: `getPlanetType`, `getParentCode`, `getCommunityName` and `clearPreferences` (ConfigurationsRepository.kt:27-29, 33).
files:
- M/repository/ConfigurationsRepository.kt: `CheckVersionCallback`, plus the 4 dead declarations.
- M/repository/ConfigurationsRepositoryImpl.kt: `checkVersion`, `handleVersionEvaluation`, and :397-407, :454.
- M/ui/sync/SyncActivity.kt: `onError`, :767-785.
- M/services/AutoSyncWorker.kt: `onError`, :92.
- T/repository/ConfigurationsRepositoryImplTest.kt

Leave alone: `getMinApk` and its `ConfigurationResult.Failure`, M/ui/sync/SyncConfigurationCoordinator.kt, and `onCheckingVersion` (still verified at T/repository/ConfigurationsRepositoryImplTest.kt:265, 331).
steps:
1. In ConfigurationsRepository.kt, add `sealed interface VersionCheckError` with objects `NotConfigured`, `VersionNotFound`, `UpToDate`, `ApkNotFoundOnServer` and `ConnectionFailed`. Change the callback to `onError(error: VersionCheckError, blockSync: Boolean)`.
2. In the Impl, replace each `context.getString(...)` argument with the matching object. Keep the same `blockSync` values: true for NotConfigured, VersionNotFound and ConnectionFailed; false for UpToDate and ApkNotFoundOnServer.
3. In `SyncActivity.onError`, map the error to the same `R.string` ids for the toast. Replace the `startsWith("Config")` check with `if (error == VersionCheckError.NotConfigured) settingDialog()`. In `AutoSyncWorker.onError`, only the signature changes.
4. Remove `getPlanetType`, `getParentCode`, `getCommunityName` and `clearPreferences` from the interface, and make them `private` (no `override`) in the Impl.
5. Tests:
   - Change `verify { callback.onError("Server URL not configured", true) }` (:212) and the other `onError` verifications to the typed objects.
   - Make the tests at :863-881 and :905-910 assert through `getCommunityConfiguration()` and `clearLocalAppData()`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ConfigurationsRepositoryImplTest" --tests "*SyncActivity*"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a fresh install with no server URL, tapping sync now shows "Server URL not configured" and opens the server-settings dialog. With a server set, the "Planet is up to date." toast still appears and sync continues.
size budget: ~110 changed lines, 5 files
out of scope: do not move `ai_models` reading, `getMinApk` errors or community-config methods. Keep the strings in strings.xml unchanged.

---

### 6. move health-record encryption and entity building out of HealthExaminationActivity into its ViewModel (roadmap 3, moves 9+10)

context: the Activity does data-layer work on its own:
- It generates the user's key and IV, mutates the repository-owned `UserEntity` (`val key = user?.key ?: generateKey().also { user?.key = it }`, M/ui/health/HealthExaminationActivity.kt:281-285), and encrypts the examination (`examination?.data = encrypt(...)`, :284).
- `createPojo()` (:366-383) encrypts the `MyHealth` profile into `pojo.data`.
- `saveData()` (:240-291) builds a new `HealthExamination` with `generateIv()` as its id (:244-248).
- `initExamination()` (:117-127) decrypts with `examination?.getEncryptedDataAsJson(it)` plus a Gson parse.

All of this then goes back through `viewModel.saveExamination(examination, pojo, user)` (M/ui/health/HealthExaminationViewModel.kt:97). Crypto and entity mutation in an Activity blocks moving this screen to Compose and leaves it untestable on the JVM.
files:
- M/ui/health/HealthExaminationActivity.kt: `initExamination`, `saveData`, `createPojo`.
- M/ui/health/HealthExaminationViewModel.kt: `HealthExaminationState`, `loadData`, `saveExamination`.
- T/ui/health/HealthExaminationViewModelTest.kt

Leave alone: M/repository/HealthRepository.kt and HealthRepositoryImpl.kt (owned by open PRs), M/model/HealthExamination.kt, M/ui/health/HealthExaminationAdapter.kt, and the condition-checkbox UI code.
steps:
1. Add `savedExamination: Examination? = null` to `HealthExaminationState`. In `loadData`, decrypt it once using the loaded `examination` and `user`, with the same `runCatching { GsonUtils.gson.fromJson(...) }` fallback. `initExamination` then reads `state.savedExamination` and no longer calls `getEncryptedDataAsJson`.
2. Change `saveExamination` so it takes the form values, `(examination: HealthExamination?, sign: Examination, health: MyHealth?, pojo: HealthExamination?, user: UserEntity?)`, and does the following on `dispatcherProvider.io` before calling `healthRepository.saveExamination`:
   - create the `HealthExamination` with a `generateIv()` id when null;
   - ensure key and IV;
   - set `health.lastExamination`;
   - encrypt `health` into `pojo.data` and `sign` into `examination.data`.
3. In the Activity, delete the encryption block, the `generateKey`/`generateIv`/`encrypt` imports and the encryption half of `createPojo` (it keeps only the null-pojo construction). `saveData` passes the plain `sign` and `health` to the ViewModel.
4. In HealthExaminationViewModelTest, update the three `saveExamination_*` tests (:112, :134, :156) to the new signature. Add one test that a null `user.key` gets generated and `examination.data` is non-empty and decrypts back to the `sign` notes.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*HealthExaminationViewModelTest" --tests "*HealthExaminationActivityTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device: create a new examination, reopen it, and the notes and diagnosis fields show the saved text. Editing an existing examination keeps its earlier values. A first examination for a user with no key still saves.
size budget: ~110 changed lines, 3 files
out of scope: do not change HealthRepository, the encryption algorithm, or the condition/diagnosis splitting.

---

### 7. introduce a narrow UserLookupRepository and move single-purpose callers onto it (roadmap 4+1, moves 9)

context: `UserRepository` (M/repository/UserRepository.kt:23) is a ~50-method interface, yet many consumers call only `getUserModel()` (:84). One is `DiagnosticsRepositoryImpl` (M/repository/DiagnosticsRepositoryImpl.kt:16, used at :63 and :86), which sits on the crash-logging path `DownloadRepositoryImpl → DiagnosticsRepository → UserRepository`. Another is `FeedbackListViewModel` (M/ui/feedback/FeedbackListViewModel.kt:20, used at :35). This mirrors the existing narrow-parent pattern (`UserRepository : UserAchievementsRepository`). `RepositoryModule.kt` is owned by open PRs, so the binding goes in a new module.
files:
- M/repository/UserRepository.kt: move 3 declarations out.
- M/repository/UserLookupRepository.kt (new file)
- M/di/UserLookupModule.kt (new file)
- M/repository/DiagnosticsRepositoryImpl.kt: constructor parameter.
- M/ui/feedback/FeedbackListViewModel.kt: constructor parameter.

Leave alone: M/repository/UserRepositoryImpl.kt and M/di/RepositoryModule.kt (owned by open PRs; they must compile unchanged), and every other `UserRepository` consumer.
steps:
1. Create `interface UserLookupRepository { suspend fun getUserModel(): UserEntity?; suspend fun getUserById(userId: String): UserEntity?; suspend fun getUsersByIds(userIds: List<String>): List<UserEntity> }`. Remove those three declarations from `UserRepository` (lines 28, 30, 84). Declare `interface UserRepository : UserAchievementsRepository, UserLookupRepository`. The Impl's existing `override`s then still compile.
2. Create `@Module @InstallIn(SingletonComponent::class) object UserLookupModule` with `@Provides fun provideUserLookupRepository(repo: UserRepository): UserLookupRepository = repo`. Delegating from `UserRepository` reuses the existing `@Singleton` instance.
3. Change the parameter type `userRepository: UserRepository` to `UserLookupRepository` in DiagnosticsRepositoryImpl and in FeedbackListViewModel. Keep the parameter names, so tests passing named `UserRepository` mocks still compile (a subtype).
4. Fix imports and run the DI build (`assembleDefaultDebug`) to confirm Hilt resolves the graph.

acceptance: `./gradlew assembleDefaultDebug` succeeds (Hilt graph). `./gradlew testDefaultDebugUnitTest --tests "*DiagnosticsRepositoryImplTest" --tests "*FeedbackListViewModelTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, the feedback list still loads for the logged-in user, and a crash log still records the user name.
size budget: ~45 changed lines, 5 files (2 new)
out of scope: do not migrate other consumers in this task, and do not touch RepositoryModule.

---

### 8. split a ResourcesSyncRepository so the sync layer stops depending on the full resources UI surface (roadmap 5+4, moves 9)

context: the sync pull path takes the whole ~70-method `ResourcesRepository` (M/repository/ResourcesRepository.kt). That surface covers UI search, shelves, storage breakdown and downloads, but sync uses three methods:
- `SyncRepositoryImpl` (M/repository/SyncRepositoryImpl.kt:41) calls only `batchInsertMyLibrary` (:55).
- `SyncManager` (M/services/sync/SyncManager.kt:65) calls only `batchInsertResources` (:385) and `removeDeletedResources` (:433).

`ResourcesRepositoryImpl` has no class-level scope and is made `@Singleton` only at its binding in the off-limits `RepositoryModule`. So the narrow type must be provided by delegating from `ResourcesRepository`, never by a second `@Binds` to the Impl.
files:
- M/repository/ResourcesRepository.kt: move 3 declarations out, lines 118, 123, 124.
- M/repository/ResourcesSyncRepository.kt (new file)
- M/di/ResourcesSyncModule.kt (new file)
- M/repository/SyncRepositoryImpl.kt: constructor parameter.
- M/services/sync/SyncManager.kt: constructor parameter.

Leave alone: M/repository/ResourcesRepositoryImpl.kt and M/di/RepositoryModule.kt (owned by open PRs), and M/services/upload/UploadConfigs.kt (upload-side methods stay on `ResourcesRepository`).
steps:
1. Create `interface ResourcesSyncRepository` holding `batchInsertResources(documents: List<JsonObject>): List<String>`, `batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int` and `removeDeletedResources(currentIds: List<String?>)`. Remove those three from `ResourcesRepository` and declare `interface ResourcesRepository : ResourcesSyncRepository`.
2. Create `@Module @InstallIn(SingletonComponent::class) object ResourcesSyncModule` with `@Provides fun provideResourcesSyncRepository(repo: ResourcesRepository): ResourcesSyncRepository = repo`.
3. Change the `resourcesRepository` parameter type to `ResourcesSyncRepository` in `SyncRepositoryImpl` and `SyncManager`. Keep the names, so existing test mocks of `ResourcesRepository` still compile.
4. Remove the now-unused `ResourcesRepository` imports, and confirm the Hilt graph with `assembleDefaultDebug`.

acceptance: `./gradlew assembleDefaultDebug` succeeds. `./gradlew testDefaultDebugUnitTest --tests "*SyncRepositoryImplTest" --tests "*SyncManagerTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, a full sync still pulls new resources into the library, and resources deleted on the server disappear after sync.
size budget: ~40 changed lines, 5 files (2 new)
out of scope: do not split the upload methods, and do not change ResourcesRepositoryImpl.

---

### 9. move the course-step "download if server reachable" decision from CoursesStepsViewModel into ResourceDownloadCoordinator (roadmap 3+5, moves 9)

context: `CoursesStepsViewModel.loadStep` (M/ui/courses/CoursesStepsViewModel.kt:83-93) reaches into a foreign feature's repository to make a download decision. It calls `configurationsRepository.checkServerAvailability()` and then `resourcesRepository.downloadResourcesPriority(notDownloaded)`. `ResourceDownloadCoordinator.startBackgroundDownload` (M/services/ResourceDownloadCoordinator.kt:26-34) already owns the same "check the server, then start the download" policy. The ViewModel depends on `ConfigurationsRepository` only for this one call (constructor :50).
files:
- M/ui/courses/CoursesStepsViewModel.kt: constructor and `loadStep`.
- M/services/ResourceDownloadCoordinator.kt: new method.
- T/ui/courses/CoursesStepsViewModelTest.kt
- T/services/ResourceDownloadCoordinatorTest.kt

Leave alone: M/repository/ResourcesRepository.kt, ResourcesRepositoryImpl.kt and ConfigurationsRepository.kt, plus the next-step prefetch block in `loadStep` (:95-106).
steps:
1. Add `suspend fun downloadPriorityIfReachable(items: List<MyLibrary>): Boolean` to ResourceDownloadCoordinator. It returns false for an empty list or an unreachable server. Otherwise it launches `resourcesRepository.downloadResourcesPriority(items)` in `applicationScope` and returns true.
2. In `loadStep`, replace the `checkServerAvailability` + `viewModelScope.launch { downloadResourcesPriority }` block with `val isDownloading = resourceDownloadCoordinator.downloadPriorityIfReachable(notDownloaded)`.
3. Remove the `configurationsRepository` constructor parameter and its import from CoursesStepsViewModel.
4. In CoursesStepsViewModelTest, drop the `configurationsRepository` mock (:48, :67). Retarget the tests at :111 and :150 to stub `resourceDownloadCoordinator.downloadPriorityIfReachable` and assert `isDownloadingResources`.
5. In ResourceDownloadCoordinatorTest, add three cases: an empty list returns false; an unreachable server returns false and does not download; a reachable server returns true and calls `downloadResourcesPriority`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CoursesStepsViewModelTest" --tests "*ResourceDownloadCoordinatorTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, opening a course step with non-downloaded resources while online shows the downloading indicator and the files download. Offline, no download starts and the step still renders.
size budget: ~55 changed lines, 4 files
out of scope: do not touch `startBackgroundDownload`, `runPostSyncDownloads` or the `context.getExternalFilesDir` call in the ViewModel.

---

### 10. hoist the settings screen's guest-user check from the fragment into SettingsViewModel (roadmap 3, moves 10)

context: `SettingsActivity.SettingFragment` injects `UserSessionManager` (M/ui/settings/SettingsActivity.kt:89-90) and reads the session user three times in the UI layer:
- `onCreatePreferences` (:194) stores it in a mutable `var user` (:97) for `blockGuestSwitches` (:230-231).
- `initStorageBreakdown` (:258-259) checks it again.
- `clearDataButtonInit` (:301-302) checks it again.

Each repeats `userModel?.id?.startsWith("guest")`. The screen already has `SettingsViewModel` (M/ui/settings/SettingsViewModel.kt:19), so this belongs there as hoisted state.
files:
- M/ui/settings/SettingsActivity.kt: `SettingFragment`, its `profileDbHandler` field and `user` var, and the three call sites.
- M/ui/settings/SettingsViewModel.kt: constructor plus a new `isGuest`.
- T/ui/settings/SettingsViewModelTest.kt

Leave alone: M/services/UserSessionManager.kt, M/model/UserEntity.kt, and the retry-queue dialog code in the same fragment.
steps:
1. Inject `UserRepository` into `SettingsViewModel` and add `suspend fun isGuest(): Boolean = userRepository.getUserModel()?.id?.startsWith("guest") == true`. Keep this exact predicate. Do not switch to `UserEntity.isGuest()`, which checks `_id` and roles and would change behavior.
2. In `onCreatePreferences`, call `if (viewModel.isGuest()) blockGuestSwitches()`. Drop the `user` var, and drop the early-return guard inside `blockGuestSwitches`.
3. In `initStorageBreakdown` and `clearDataButtonInit`, replace `profileDbHandler.getUserModel()?.id?.startsWith("guest") == true` with `viewModel.isGuest()`.
4. Remove the `profileDbHandler` field and the now-unused `UserSessionManager`/`UserEntity` imports.
5. In SettingsViewModelTest, add the new constructor argument at :45 and add two tests: a `guest_…` id returns true, and a regular id or null user returns false.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*SettingsViewModelTest"` passes, and `./gradlew testDefaultDebugUnitTest` stays green. On a device, as a guest, toggling any settings switch, tapping "Storage breakdown" and tapping "Reset app" each show the guest dialog. As a member, all three work normally.
size budget: ~35 changed lines, 3 files
out of scope: no changes to the retry dialog, storage breakdown or language/text-size pickers.

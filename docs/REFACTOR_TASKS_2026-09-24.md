# myPlanet refactor round — repository boundaries (10 work orders)

date: 2026-09-24 · base commit: `14da1ba` (master, "all: smoother importing (fixes #17505)") · open PRs checked (31): #17435, #17430, #17356, #17254, #17187, #16624, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

How the files were cleared: every open PR head was diffed against its merge-base with `master`, and every file any of them touches is off-limits here, not only files in PRs tagged review/ready/merge. That rules out `AppDatabase.kt`, `RoomModule.kt`, `RepositoryModule.kt`, all `strings.xml`, and the Teams, Courses, User, Voices, Submissions, Surveys, Progress, Health, Ratings, Tags, Activities, Events, Chat and Community `*RepositoryImpl` files. So no task here changes a schema, adds a Hilt `@Binds`, or adds a string. The 42 files below are all distinct and are not touched by any open PR.

Conventions for every task: MockK + `runTest` (see `docs/TESTING.md`), `DispatcherProvider` instead of hard-coded dispatchers, `IS` for nullable `@Query` params, and a PR title in the house style `scope: smoother thing doing`.

---

### 1. chunk the unbounded notification IN-list lookups (roadmap 1+7, also 5)
context: `NotificationsRepositoryImpl.bulkInsertFromSync` (NotificationsRepositoryImpl.kt:507-521) calls `notificationDao.getByIds(parsedList.map { it.id })` with the whole sync page in one list. `TransactionSyncManager.syncDb` (TransactionSyncManager.kt:208-212) pages `notifications` at `else -> 1000`, so one page can bind 1000 values. minSdk-26 devices ship SQLite older than 3.32, which fails any statement with more than 999 bound variables ("too many SQL variables"). The same unchunked pattern is used by `markNotificationsAsRead` (:123-129, `getIdsByIds` → `markAsRead`) and `deleteNotifications` (:498-505, `getIdsByIds` → `deleteByIds`). The page also loads full `AppNotification` rows when it only reads `needsSync` and `isRead` (:517-519).
files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`: `getByIds` (:39-40), `getIdsByIds` (:42-43), `markAsRead(ids, createdAt)` (:48-49), `deleteByIds` (:102-103). `markSynced` (:63-100) already shows the house chunking shape (`chunked(900)` inside `@Transaction`).
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`: `bulkInsertFromSync`, `markNotificationsAsRead`, `deleteNotifications`.
- tests: `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` (:370-432, :436-502) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`.
- leave alone: `services/sync/TransactionSyncManager.kt` (open PR #15808), `NotificationsRepository.kt` (the interface does not change), and the team-task enrichment code at NotificationsRepositoryImpl.kt:240-400.
steps:
1. In `NotificationDao`, add `SELECT id, isRead FROM notifications WHERE needsSync = 1 AND id IN (:ids)`. It returns a small projection data class declared at the bottom of the DAO file, next to the other projections.
2. Add `@Transaction` default methods to `NotificationDao` that do lookup-then-write per `chunked(900)` slice: `markAsReadChunked(ids, createdAt): List<String>` and `deleteByIdsChunked(ids): List<String>`. Each returns the ids that existed.
3. In `bulkInsertFromSync`, build the existing-state map from the new projection over `chunked(900)` slices, then keep the current `needsSync`/`isRead` carry-over logic unchanged.
4. Point `markNotificationsAsRead` and `deleteNotifications` at the chunked DAO methods, and delete `getByIds` if it has no remaining caller.
5. Update the repository tests to stub the new DAO methods. Add one `NotificationDaoTest` case that inserts 1,200 rows and asserts `markAsReadChunked` marks all of them.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest" --tests "*NotificationDaoTest"` passes, and the full `./gradlew testDefaultDebugUnitTest` stays green.
- On an API 26 emulator a full sync with more than 999 server notifications finishes without `SQLiteException`.
- Locally read notifications stay read after re-sync.
- "Mark all as read" on the notifications screen still clears the badge.
size budget: ~90 changed lines, 4 files
out of scope: no change to page sizes in `TransactionSyncManager`, no schema or index change, no change to the `NotificationsRepository` interface.

---

### 2. move the retry find-or-enqueue decision from RetryQueue into RetryRepository (roadmap 5+1, also 9)
context: `RetryQueue.queueFailedOperation` (services/retry/RetryQueue.kt:26-58) runs repository logic in the service. It calls `retryRepository.getExistingOperation(...)` (:42), then branches to `updateAttempt(existingOperation.id, …)` (:45) or `enqueue(…)` (:48). Two uploads failing on the same item at once can both see `null` and insert duplicate retry rows, and the full `RetryOperation` (including `serializedPayload`) is loaded only to read `.id`. `RetryRepository` also exposes `getPendingCount()` (RetryRepository.kt:34) and `deletePendingAndAbandonedOperations()` (:37), which are only called from inside `RetryRepositoryImpl` (:183 in `safeClearQueue`, :189 in `getRetryQueueSnapshot`).
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`: `enqueue` (:33-47), `updateAttempt` (:50-55), `getExistingOperation` (:155-157), and the existing `mutex` (:31).
- `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt`: `queueFailedOperation`.
- tests: `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt` (:67, :279-285, :288-295, :306-310) and `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueTest.kt` (:88-121).
- leave alone: `data/room/dao/RetryDao.kt` (`findExisting` at :36-40 stays as is), `RetryQueueWorker.kt`, and the three `queueFailedOperation` callers (`UploadManager.kt` is under open PR #10993, `UploadCoordinator.kt`, `TeamsUploader.kt`). The `queueFailedOperation` signature must not change.
steps:
1. Replace `enqueue`, `updateAttempt` and `getExistingOperation` on the interface with one `suspend fun recordFailure(uploadType, failure, payload, endpoint, httpMethod, dbId, modelClassName, userId)` that has the same parameter list as today's `enqueue`.
2. Implement `recordFailure` in the Impl under `mutex.withLock`: `retryDao.findExisting(...)` → `markFailed(existing.id, failure.message, failure.httpCode)`, otherwise `retryDao.insert(RetryOperation.createFromRetryFailure(...))`.
3. Remove `getPendingCount` and `deletePendingAndAbandonedOperations` from the interface and make them `private` in the Impl.
4. Reduce `RetryQueue.queueFailedOperation` to the non-retryable early return, one `retryRepository.recordFailure(...)` call, and its log line.
5. Rewrite the three RetryQueueTest cases as one delegation test. In RetryRepositoryImplTest, replace the `enqueue`/`getExistingOperation` tests with "inserts when none exists" and "records attempt when one exists". Delete the two private-delegate tests; `safeClearQueue` and `getRetryQueueSnapshot` still cover them.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*RetryRepositoryImplTest" --tests "*RetryQueueTest" --tests "*RetryQueueWorkerTest"` passes, and the full suite stays green.
- Go offline mid-upload, trigger two uploads of the same item, and reconnect: the Settings retry-queue count shows one entry for that item, not two.
size budget: ~110 changed lines, 5 files
out of scope: no DAO change, no change to backoff math in `RetryDao.recordFailedAttempt`, no move of `isProcessing` state.

---

### 3. move shelf discovery out of SyncManager into SyncRepository (roadmap 5+1, also 9)
context: `SyncManager.getShelvesWithDataBatchOptimized` (services/sync/SyncManager.kt:449-480) is a data function inside the sync orchestrator. It reads the shelf cache via `syncRepository.getCachedShelvesWithData()` (:452), calls Retrofit directly with `ApiClient.executeWithRetryAndWrap { apiInterface.getDocuments(header, "$url/shelf/_all_docs") }` (:460-462), fans out to `userSyncRepository.checkShelfBatchForDataOptimized` through the private helper at :482-485, and writes back with `syncRepository.cacheShelvesWithData(...)` (:479). The two cache halves already live in `SyncRepositoryImpl` (:204-220). They exist on the interface only so SyncManager can stitch them around its own network call.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`: constructor (:34-46), `getCachedShelvesWithData`, `cacheShelvesWithData`.
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`: delete :449-485 and change the single call at :498.
- test: `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt`, whose named-argument constructor is at :102-114.
- leave alone: `services/sync/TransactionSyncManager.kt` (open PR #15808), `UserRepositoryImpl.kt` (locked), and `SyncManagerTest.kt`. SyncManager keeps `apiInterface` (still used at :302) and `userSyncRepository` (still used at :269), so its constructor does not change.
steps:
1. Add `suspend fun getShelvesWithData(): List<String>` to `SyncRepository`, and remove `getCachedShelvesWithData`/`cacheShelvesWithData` from the interface.
2. Inject `userSyncRepository: dagger.Lazy<UserSyncRepository>` into `SyncRepositoryImpl`. Use `Lazy`, as the Impl already does for `transactionSyncManager`; `UserSyncRepository` is bound to `UserRepositoryImpl`.
3. Move the body verbatim into the Impl: cache check, `_all_docs` fetch, `chunked(25)` + `Semaphore(8)` fan-out on `dispatcherProvider.io`, then cache write. Make the two cache methods `private`.
4. In SyncManager, call `syncRepository.getShelvesWithData()` at the old call site and delete the moved function, its helper, and the now-unused `org.ole.planet.myplanet.model.Rows` import (`Semaphore`/`withPermit` stay: still used at :513-516).
5. Pass the new constructor argument in `SyncRepositoryImplTest`. Add tests for cache hit (no API call), cache miss (API + batch check + cache write), and API failure (empty list, no cache write).
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SyncRepositoryImplTest" --tests "*SyncManagerTest"` passes, and the full suite stays green.
- A manual full sync still logs "Library: Found N shelves with data" with the same N as before.
- A second sync within 6h skips the `_all_docs` request (visible in the OkHttp log).
size budget: ~100 changed lines (≈45 main, ≈55 test), 4 files
out of scope: no change to batch size, semaphore width, or cache TTL; no change to `myLibraryTransactionSync` beyond the one call.

---

### 4. move the community-leaders fetch from LoginSyncManager into ConfigurationsRepository (roadmap 5+1, also 9)
context: `LoginSyncManager.syncAdmin` (services/sync/LoginSyncManager.kt:126-168) builds a Gson `_find` selector, POSTs to `/_users/_find` through `apiInterface.postDoc`, and writes `setCommunityLeaders(...)` and the raw `"user_admin"` pref. That is persistence logic in a sync service. The read side, `getCommunityLeaders()` (ConfigurationsRepositoryImpl.kt:414-416), already lives in `ConfigurationsRepositoryImpl`, which already has `apiInterface`, `sharedPrefManager` and `@PlainGson gson`. After the move, one repository owns both reading and writing the leaders cache.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`: `syncAdmin` and its constructor (:30-37).
- tests: `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt` (positional constructor at :70-77) and `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`.
- leave alone: `services/sync/SyncManager.kt` (task 3 owns it; its `loginSyncManager.syncAdmin()` call at :199 must keep compiling unchanged), `CheckVersionCallback`, and the `getPlanetType`/`getParentCode`/`getCommunityName` getters (task 6 reads `getCommunityConfiguration()`).
steps:
1. Add `suspend fun syncCommunityLeaders()` to `ConfigurationsRepository`.
2. Implement it in the Impl with the current `syncAdmin` body: same blank-header early return, same URL-construction guard, same `docs[0]` → `"user_admin"` write, same catch-and-log. Build the selector as today, and use the Impl's injected `gson` instead of `GsonUtils.gson`.
3. Inject `ConfigurationsRepository` into `LoginSyncManager` and reduce `syncAdmin()` to `applicationScope.launch { configurationsRepository.syncCommunityLeaders() }`. Drop imports that become unused.
4. Add the constructor argument in `LoginSyncManagerTest`. In `ConfigurationsRepositoryImplTest`, add a success test (leaders + `user_admin` written), a non-2xx test (nothing written) and a blank-header test (no API call).
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ConfigurationsRepositoryImplTest" --tests "*LoginSyncManagerTest" --tests "*SyncManagerTest"` passes, and the full suite stays green.
- After a sync, Community → Leaders still lists the admins returned by the server.
size budget: ~90 changed lines, 5 files
out of scope: no change to when `syncAdmin` is triggered, no conversion of `checkVersion` to suspend, no move of `getCommunityLeaders` to another repository.

---

### 5. route LoginActivity's repository calls through LoginViewModel (roadmap 3, also 10)
context: `LoginActivity` bypasses its own `LoginViewModel` (held via `by viewModels()` at LoginActivity.kt:87) and calls repositories directly:
- `userRepository.getUserByName(username)` for the archived-member check (:278)
- `communityRepository.syncCommunityDocs()` inside `withContext(dispatcherProvider.io)` (:373-375)
- `userRepository.createGuestUser(...)` twice (:589, :631)
- `showGuestLoginDialog(userRepository)` (:321)

`GuestLoginExtensions.kt` then calls `validateUsername` (:39, :58), `findUserByName` (:60) and `createGuestUser` (:68) on the raw repository. Screen state cannot be hoisted for a later Compose port while the Activity owns these calls.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt` (constructor :19-23)
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt` (`showGuestLoginDialog` at :19)
- test: `app/src/test/java/org/ole/planet/myplanet/ui/sync/LoginViewModelTest.kt` (`createViewModel` at :29-34)
- leave alone: `ProcessUserDataActivity.kt` (:59 declares `userRepository`) and `SyncActivity.kt` (:133 declares `communityRepository`). Both fields stay because `DashboardActivity` and the other subclasses use them. Also leave `LoginSyncManager.kt` (task 4) and `AuthUtils.login`.
steps:
1. Inject `CommunityRepository` into `LoginViewModel`.
2. Add pass-through methods to `LoginViewModel`:
   - `suspend fun isArchivedMember(name: String): Boolean`
   - `suspend fun validateUsername(name: String): String?`
   - `suspend fun findUserByName(name: String): UserEntity?`
   - `suspend fun createGuestUser(name: String): UserEntity?`
   - `fun syncCommunityDocs()`, which launches on `dispatcherProvider.io` in `viewModelScope`
3. In `LoginActivity`, replace the five direct repository calls with the ViewModel methods, and call `loginViewModel.syncCommunityDocs()` in place of the `lifecycleScope`/`withContext` block.
4. Change the extension to `fun LoginActivity.showGuestLoginDialog(viewModel: LoginViewModel)`, switch its three call sites to the ViewModel, and drop the `UserRepository` import.
5. Pass a `CommunityRepository` mock in `createViewModel()`. Add one test per new method: delegation, archived true/false, and `syncCommunityDocs` running on the test dispatcher.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*LoginViewModelTest"` passes, and the full suite stays green.
- `grep -n "userRepository\.\|communityRepository\." app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt` returns nothing.
- Manual check: an archived member still gets the "is archived" dialog, guest login still creates and logs in a guest, and an existing guest name still shows the "already a guest" dialog.
size budget: ~80 changed lines, 4 files
out of scope: no Compose conversion, no change to the dialogs' UI or strings, no removal of the base-class repository fields.

---

### 6. let ChatViewModel resolve community config instead of ChatHistoryFragment (roadmap 3, also 10)
context: `ChatHistoryFragment.refreshChatHistory` (ui/chat/ChatHistoryFragment.kt:136-142) reads `sharedPrefManager.getParentCode()` and `getCommunityName()` and passes them into `ChatViewModel.loadChatHistoryScreenData(userId, parentCode, communityName)` (ChatViewModel.kt:126-130). The Fragment is doing configuration lookup that `ConfigurationsRepository.getCommunityConfiguration()` (ConfigurationsRepository.kt:23) already provides. Moving it into the ViewModel removes that repository knowledge from the view, and the screen then needs only a user id to load.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`: `refreshChatHistory`.
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`: constructor (:49-56) and `loadChatHistoryScreenData`.
- test: `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` (positional constructor at :59; calls at :208, :242, :273, :288).
- leave alone: `ConfigurationsRepository.kt` and `ConfigurationsRepositoryImpl.kt` (task 4; use the existing `getCommunityConfiguration()`), `ChatDetailFragment.kt`, `DashboardActivity.kt` (open PR #15825), and `TeamsRepository` usage in the ViewModel.
steps:
1. Inject `ConfigurationsRepository` into `ChatViewModel`.
2. Change the signature to `loadChatHistoryScreenData(userId: String?)`. Inside, read `configurationsRepository.getCommunityConfiguration()` and pass its `parentCode` and `communityName` to the existing `loadShareTargets(...)`. Keep the retry and caching behaviour unchanged.
3. In `ChatHistoryFragment.refreshChatHistory`, pass only `sharedPrefManager.getUserId()`. `sharedPrefManager` stays because it is still used at :49.
4. In the test, add the constructor mock and stub `getCommunityConfiguration()` with the parent/community values the four calls used to pass, so the existing assertions keep their meaning.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ChatViewModelTest"` passes, and the full suite stays green.
- Manual check: the chat history screen still loads, and the share dialog still lists the community and team targets it listed before.
size budget: ~35 changed lines, 3 files
out of scope: no change to `ChatRepository`, no removal of `sharedPrefManager` from the Fragment, no move of `getShareableEnterpriseSummaries` out of `TeamsRepository`.

---

### 7. stop ResourcesRepository leaking a Room projection and trim its internal-only surface (roadmap 1, also 9)
context: `ResourcesRepository.getLibraryTitles(): List<LibraryTitleProjection>` (ResourcesRepository.kt:7, :64) exposes a type declared in the DAO file (`data/room/dao/MyLibraryDao.kt:183`). That type then leaks into the UI: `ui/user/AchievementViewModel.kt:15,86` and `ui/user/EditAchievementFragment.kt:44,501` import `org.ole.planet.myplanet.data.room.dao.*`. These are the only UI imports of the Room package in the app. The same interface also publishes five methods that nothing outside `ResourcesRepositoryImpl` calls (repo-wide grep): `getMyLibrary` (:71), `resolveLibraryItemByResourceId` (:79), `updateUserLibrary` (:81), `clearResourceListCache` (:126) and `getResourceTitlesMap` (:130).
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`: `getLibraryTitles` (:83-85) and the `override`s at :179, :219, :345, :750 and :809.
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`: `createResourceList` (:501).
- test: `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementViewModelTest.kt` (:22, :145).
- leave alone: `MyLibraryDao.kt` (the projection stays as the DAO's return type), `MyLibraryDaoTest.kt`, and `ResourcesRepositoryImplTest.kt`. That test types its subject as `ResourcesRepositoryImpl` (:83) and only asserts `.id`/`.title` (:327-329), so it keeps compiling.
steps:
1. In `ResourcesRepository.kt`, add a repository-level `data class LibraryTitle(val id: String, val title: String?)` next to the existing `LibraryWithMetadata`/`StorageBreakdown` declarations. Change `getLibraryTitles()` to return `List<LibraryTitle>` and remove the `data.room.dao` import.
2. Map the DAO rows to `LibraryTitle` in the Impl.
3. Switch the import and type in `AchievementViewModel` and `EditAchievementFragment`, then in the test.
4. Remove the five internal-only methods from the interface. In the Impl, drop `override` and mark them `internal` so the existing Impl tests still compile.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*AchievementViewModelTest" --tests "*ResourcesRepositoryImplTest"` passes, and the full suite stays green.
- `grep -rn "data.room" app/src/main/java/org/ole/planet/myplanet/ui` returns nothing.
- Manual check: Edit Achievement → "add resources" still lists library titles and pre-checks previously chosen ones.
size budget: ~30 changed lines, 5 files
out of scope: no DAO query change, no removal of `search(...)` or other public methods, no touch to `BaseResourceFragment`/`ResourcesFragment` (open PRs #15267, #13355, #17435).

---

### 8. replace enterprise report read-modify-REPLACE with single UPDATE queries (roadmap 1+7)
context: `EnterprisesRepositoryImpl.archiveReport` (:79-85) and the private `attachTeamImage` (:127-139) both go through `updateTeamEntityById` (:140-146). That helper loads the whole `teams` row with `teamDao.getById`, flips one or two fields, and writes every column back with `@Upsert`, just to set `status = "archived"`/`isUpdated = 1` or `imageName`/`isUpdated = 1`. A sync that lands between the read and the write gets its columns overwritten with the stale copy. The column names are confirmed in `model/MyTeam.kt`: `_id`, `status`, `imageName`, and `@ColumnInfo(name = "isUpdated") var updated` at :50.
files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`: `archiveReport`, `attachTeamImage`, `updateTeamEntityById`.
- tests: `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt` (:117, :142, :169) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt`.
- leave alone: `model/MyTeam.kt` (open PR #15951), `TeamsRepositoryImpl.kt` (open PRs), and `updateReport` (:53-77). `updateReport` keeps the read-modify path because it recomputes several fields.
steps:
1. Add `@Query("UPDATE teams SET status = 'archived', isUpdated = 1 WHERE _id = :id") suspend fun archiveById(id: String): Int` to `TeamDao`.
2. Add `@Query("UPDATE teams SET imageName = :imageName, isUpdated = 1 WHERE _id = :id") suspend fun setImageNameById(id: String, imageName: String): Int` to `TeamDao`.
3. Call them from `archiveReport` and `attachTeamImage`. Leave `updateTeamEntityById` used only by `updateReport`.
4. Update the image test to verify `teamDao.setImageNameById("report-1", "logo.png")` in place of the `upsert` match. Keep the "short-circuit" test asserting that no image write happens.
5. Add TeamDaoTest cases: archive hides the report from `observeNonArchivedReportsByTeamId` and sets `updated`, and `setImageNameById` leaves all other columns unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*EnterprisesRepositoryImplTest" --tests "*TeamDaoTest"` passes, and the full suite stays green.
- Manual check: enterprise Reports → archive removes the row immediately. A report saved with an image shows the image, and both changes upload on the next sync.
size budget: ~45 changed lines, 4 files
out of scope: no change to `updateReport`, no dedupe of the profit/loss math in `EnterprisesReportsAdapter`, no schema change.

---

### 9. make the My Life visibility toggle re-read the row it updated (roadmap 1+7)
context: `MyLifeDao.updateVisibility` updates `WHERE _id = :id OR imageId = :id OR title = :id` (MyLifeDao.kt:24-25), because `LifeFragment` falls back to `imageId`/`title` when `_id` is blank (ui/life/LifeFragment.kt:51-54). `LifeRepositoryImpl.updateVisibility` (:23-31) then re-reads with `myLifeDao.getByIds(listOf(myLifeId))`, which matches `_id IN (...)` only. For an imageId or title key it finds nothing and silently falls back to `sharedPrefManager.getUserId()`, rebuilding and caching the list for the wrong owner. Separately, both existence checks use a full `COUNT(*)`: `countByUserId(...) > 0` (:97) and `== 0` (:126).
files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`: `updateVisibility`, `getMyLifeForDashboard`, `seedMyLifeIfEmpty`.
- tests: `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt` (:88, :168-173, :214, :242, :267) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt`.
- leave alone: `LifeRepository.kt` (the interface does not change), `LifeCache.kt`, `LifeViewModel.kt`, `LifeFragment.kt`, and `LifeRepositoryTest.kt`. `LifeRepositoryTest` uses a relaxed mock and only exercises `updateMyLifeListOrder`'s `getByIds`, which stays.
steps:
1. In `MyLifeDao`, add `SELECT userId FROM my_life WHERE _id = :id OR imageId = :id OR title = :id LIMIT 1` returning `String?`. It uses the exact predicate of `updateVisibility`.
2. In `MyLifeDao`, add `existsByUserId(userId: String?): Boolean` as `SELECT EXISTS(SELECT 1 FROM my_life WHERE <same predicate as countByUserId>)`.
3. Use the new owner query in `updateVisibility`, keeping the `sharedPrefManager` fallback only for a genuinely missing row.
4. Swap both `countByUserId` checks for `existsByUserId`, then delete `countByUserId`, which has no other caller.
5. Update the repository tests' stubs. Add DAO tests: the owner query finds a row by `imageId` and by `title`, and `existsByUserId` works for null, `"--"` and real users.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*LifeRepositoryImplTest" --tests "*LifeRepositoryTest" --tests "*MyLifeDaoTest" --tests "*LifeViewModelTest"` passes, and the full suite stays green.
- Manual check: hiding a My Life tile keeps it hidden after leaving and re-entering the screen, and the dashboard My Life row reflects it.
size budget: ~50 changed lines, 4 files
out of scope: no change to the OR-predicate itself, no removal of `UserRepository` from `LifeViewModel`, no seeding-logic change.

---

### 10. drop the dead pending-upload surface from PersonalsRepository (roadmap 1+8, also 9)
context: `PersonalsRepository.getPendingPersonalUploads(userId)` (PersonalsRepository.kt:25; Impl :69-71) has zero production callers. Its only caller is `PersonalsRepositoryImplTest` (:156-164), and it is the only user of `PersonalDao.getPendingUploads` (PersonalDao.kt:26-27). Personals are uploaded one at a time from `PersonalsViewModel.uploadPersonal` (ui/personals/PersonalsViewModel.kt:41-45). `updatePersonalAfterSync` (interface :26, Impl :73-75) is public, but its only production caller is `uploadPersonal` inside the same Impl (:155). Dead interface surface misleads callers into thinking a bulk personal upload path exists.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`: remove `getPendingUploads` (:26-27) only.
- test: `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` (:156-171).
- leave alone: `PersonalDao.findByDocId`/`findById`, which `PersonalDaoTest.kt` (:75, :106, :110, :127) uses as assertions, and `updateRemoteDocRef`/`updateUploadedStatus`. Also leave `PersonalsViewModel.kt` and `PersonalsFragment.kt`.
steps:
1. Delete `getPendingPersonalUploads` from the interface and the Impl, and `getPendingUploads` from `PersonalDao`.
2. Remove `updatePersonalAfterSync` from the interface and make it `private` in the Impl.
3. Delete the two tests at :156-171. The `uploadPersonal` success tests (:372, :438, :465) already verify `personalDao.updateUploadedStatus(...)`.
4. Remove any import in the three main files that becomes unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*PersonalsRepositoryImplTest" --tests "*PersonalDaoTest" --tests "*PersonalsViewModelTest"` passes, and the full suite stays green.
- `grep -rn "getPendingPersonalUploads\|updatePersonalAfterSync" app/src` finds only the private Impl function.
- Manual check: uploading a personal resource from myPersonals still shows the success toast and the item stops showing as pending.
size budget: ~25 changed lines, 4 files
out of scope: no new bulk-upload path for personals, no `android.util.Log` replacement in the Impl, no schema change.

---

## self-check (verified before publishing)
- [x] exactly 10 tasks, separated by `---`
- [x] no file in two tasks: 42 distinct paths, checked with `sort | uniq -d` on the combined list
- [x] every cited path opened and confirmed to exist at `14da1ba`
- [x] every task has context / files / steps / acceptance / size budget / out of scope, plus neighbours to leave alone
- [x] no task under 15 lines
- [x] no task touches a file changed by any of the 31 open PRs, which is stricter than only the review/ready/merge-tagged ones
- [x] every task is under 150 changed lines, under 5 files, adds no dependency, and adds no `@Binds`, schema bump or string resource

# myPlanet refactor round — repository-boundary work orders

date · 2026-08-27
base commit · 89fd72c251df68ed01094091d4de7ba7a2571ebe
open PRs checked (numbers) · 16274 16270 16258 16257 16192 16101 16096 15951 15825 15824 15820 15808 15699 15559 15519 15412 15267 15266 15226 15198 15158 15108 14960 14893 14883 14650 14427 13928 13848 13657 13604 13415 13355 13287 10993 8175 4075 — every file cited below was confirmed via the GitHub REST API not to appear in any open PR before this plan was written.

These are work orders for other coding agents. Each task is independently mergeable in any order. No task modifies a file touched by another task or by any open PR.

---

### 1. drop the unused ExamDao cross-feature injection and push three row hydrations into SQL in the notifications layer (roadmap 1+8, moves 9)

context: `NotificationsRepositoryImpl` injects `examDao: ExamDao` (line 32) but never calls it — a cross-feature DAO leak with no reader. Separately, three call sites load full entity rows only to throw most columns away: `markNotificationsAsRead` (line 116) does `notificationDao.getByIds(...).map { it.id }`, `markAllUnreadAsRead` (line 124) does `notificationDao.getNotifications(actualUserId, "unread", false).map { it.id }`, and `getTeamNotifications` (line 304) calls `teamNotificationDao.getByTypeAndParentIds("chat", teamIds)` but consumes only `parentId` (line 308) and `lastCount` (line 330) of each row.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — remove `examDao` from the constructor (line 32) and its import; replace the two `.map { it.id }` loads (lines 116, 406) with `notificationDao.getExistingIds(...)`; replace the `getNotifications(...).map { it.id }` load (line 124) with `notificationDao.getUnreadIds(actualUserId)`; in `getTeamNotifications` replace `getByTypeAndParentIds` (line 304) with `getCountsByTypeAndParentIds` and build `notificationsById` as `Map<String, Long>` (parentId → lastCount), adjusting the `hasChat` read at line 330 to read the long from that map.
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` — add `@Query("SELECT id FROM notifications WHERE id IN (:ids)") suspend fun getExistingIds(ids: List<String>): List<String>` and `@Query("SELECT id FROM notifications WHERE userId = :userId AND message != 'INVALID' AND message != '' AND isRead = 0") suspend fun getUnreadIds(userId: String): List<String>`. Leave `getNotifications`, `getByIds`, `getUnreadCount`, `markAsRead`, `markAllUnreadAsRead` untouched — `bulkInsertFromSync` (line 425) still needs full rows via `getByIds`.
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamNotificationDao.kt` — add a `data class TeamNotificationCount(val parentId: String, val lastCount: Long)` (top of the file) and `@Query("SELECT parentId, lastCount FROM team_notification WHERE type = :type AND parentId IN (:parentIds)") suspend fun getCountsByTypeAndParentIds(type: String, parentIds: List<String>): List<TeamNotificationCount>`. Leave `findByParentAndType`, `insert`, `update` untouched.
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` — remove the `examDao` field (line 40), its `mockk(relaxed = true)` setup (line 50), and the constructor argument (line 59). The existing tests do not exercise `markNotificationsAsRead`, `markAllUnreadAsRead`, or `getTeamNotifications`, so the relaxed `notificationDao`/`teamNotificationDao` mocks absorb the new method names without new stubs.

steps:
1. Delete the `examDao` constructor parameter, its import, and the three test references; confirm the test still constructs the repository.
2. Add `getExistingIds` and `getUnreadIds` to `NotificationDao`; swap the two `getByIds(...).map { it.id }` sites and the `getNotifications(...).map { it.id }` site to call them.
3. Add `TeamNotificationCount` plus `getCountsByTypeAndParentIds` to `TeamNotificationDao`; rewrite `getTeamNotifications` to `associate { it.parentId to it.lastCount }` and read `lastCount` from the map at line 330.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest"` is green, and `./gradlew testDefaultDebugUnitTest` stays green. The notification badge count and the "mark all read" action behave unchanged on the dashboard.

size budget: ~45 changed lines, 4 files
out of scope: do not touch `TeamsRepositoryImpl`, `TeamTaskDao`, or `VoicesRepository` — the `teamTaskDao` and `voicesRepository` injections in `NotificationsRepositoryImpl` stay (their reads are open-PR-owned).

---

### 2. replace two full-row loads in ResourcesRepository with ID-and-column projections (roadmap 1+7, moves 9)

context: `ResourcesRepositoryImpl.getMyLibIds` (line 482) calls `myLibraryDao.getForUserPattern(...)` which `SELECT *`s every `MyLibrary` row for the user, then `.forEach { jsonArray.add(it.id) }` to emit only the `id`s. `getResourceTitlesMap` (line 703) calls `getWithResourceId()` whose query is `SELECT * FROM my_library WHERE resourceId IS NOT NULL` (MyLibraryDao line 76) but only reads `resourceId` and `title` (line 704). Both load far more columns than they use.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` — narrow `getWithResourceId` (line 76) to `@Query("SELECT resourceId, title FROM my_library WHERE resourceId IS NOT NULL")` (partial-column mapping onto `MyLibrary` is supported by Room); add `@Query("SELECT id FROM my_library WHERE userId LIKE :userPattern ESCAPE '\\\\'") suspend fun getIdsForUserPattern(userPattern: String): List<String>`. Leave `getForUserPattern` (line 97) and `getForUserPatternFlow` (line 100) untouched — other callers use full rows.
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — in `getMyLibIds` (line 482) call `myLibraryDao.getIdsForUserPattern(userIdPattern(userId))` and `forEach { jsonArray.add(it) }`; leave `getResourceTitlesMap` (line 703) as-is since `getWithResourceId` now returns only the two columns it reads. Do not change the `ResourcesRepository` interface — `getMyLibIds` and `getResourceTitlesMap` signatures are unchanged.

steps:
1. Add `getIdsForUserPattern` to `MyLibraryDao` and narrow `getWithResourceId` to the two columns.
2. Swap the `getForUserPattern(...).forEach { ... it.id }` in `getMyLibIds` for `getIdsForUserPattern(...).forEach { jsonArray.add(it) }`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest"` is green (neither method is tested, so the relaxed `myLibraryDao` mock returns empty for the new query and the existing `getByResourceId` assertions are unaffected), and `./gradlew testDefaultDebugUnitTest` stays green. The shelf-sync still uploads the correct resource id list and the resource-title map is unchanged.

size budget: ~12 changed lines, 2 files
out of scope: no `ResourcesRepository` interface change, no `getAllLibrariesToSync` edit — its `.filter { it.needToUpdate() }` is a no-op over the already-`resourceOffline = 0` rows returned by `getSyncable()`, so moving it to SQL would not reduce rows.

---

### 3. move the archived filter and date ordering for enterprise reports into the DAO query (roadmap 1+7, moves 9)

context: `EnterprisesRepositoryImpl.getReportsFlow` (line 87) calls `teamDao.observeByTeamIdAndDocType(teamId, "report")` (line 88), whose query is `SELECT * FROM teams WHERE teamId = :teamId AND docType = :docType` (TeamDao line 23), then `.filter { it.status != "archived" }.sortedByDescending { it.createdDate }` in memory (lines 90-91). The Flow re-runs that filter/sort on every emission even though the predicate and ordering are static SQL concerns. `observeByTeamIdAndDocType` is shared with `TeamsRepositoryImpl` (line 461, docType `"transaction"`), so it cannot be narrowed in place.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` — add `@Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC") fun observeReportsForTeam(teamId: String): Flow<List<MyTeam>>`. Leave `observeByTeamIdAndDocType` (line 23) unchanged for the transaction caller.
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` — in `getReportsFlow` (line 88) call `teamDao.observeReportsForTeam(teamId)` and drop the `.filter { it.status != "archived" }.sortedByDescending { it.createdDate }` lines (90-91); keep the `.distinctUntilChanged { ... }` content-equality guard and `.flowOn(dispatcherProvider.default)`.

steps:
1. Add `observeReportsForTeam` to `TeamDao`.
2. Swap the source Flow in `getReportsFlow` to `observeReportsForTeam` and delete the in-memory `filter`/`sortedByDescending`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` is green (`getReportsFlow` is untested; the relaxed `teamDao` mock returns an empty Flow for the new query). The enterprise reports list still hides archived reports and shows newest-first.

size budget: ~8 changed lines, 2 files
out of scope: do not modify `observeByTeamIdAndDocType` or `TeamsRepositoryImpl`.

---

### 4. consolidate the duplicated getUserInfo URL helper into UrlUtils (roadmap 4, moves 9)

context: `getUserInfo(uri)` — which splits `uri.userInfo` into `[user, pass]` — exists twice: a `companion` copy in `ProcessUserDataActivity` (line 239, called at line 127) and a private copy in `ServerUrlMapper` (line 111, called at line 60). `UrlUtils` already owns the URL helper family (`header`, `hostUrl`, `baseUrl`, `dbUrl`) but has no `getUserInfo`, so this is a plain duplication scattered across the UI and service layers.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` — add `fun getUserInfo(uri: Uri): Array<String>` (parse `uri.userInfo` on `":"` into `[user, pass]`, defaulting to `["", ""]`, matching the `ServerUrlMapper` variant at lines 111-122). This is pure — it must not call `spm()`, so it needs no `UrlUtils.init`.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt` — replace the companion `getUserInfo` body (line 239) with `return UrlUtils.getUserInfo(uri)` (keep the companion signature so the internal call at line 127 and the `ServerConfigUtils` call at its line 99 are unchanged).
- `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt` — delete the private `getUserInfo` (line 111) and call `UrlUtils.getUserInfo(altUri)` at line 60; remove the now-unused import if any.

steps:
1. Add the pure `getUserInfo` helper to `UrlUtils`, copying the parsing from `ServerUrlMapper` lines 111-122.
2. Make `ProcessUserDataActivity.getUserInfo` delegate to it, and delete `ServerUrlMapper.getUserInfo` updating its call site to `UrlUtils.getUserInfo`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.ServerUrlMapperTest"` is green (its `updateUrlPreferences` assertions hold because `UrlUtils.getUserInfo` parses identically), `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.ServerConfigUtilsTest"` is green (`saveAlternativeUrl` still produces `satellite:<pin>@<host>:<port>` because the no-userInfo URL yields `["", ""]` via the now-delegating `ProcessUserDataActivity.getUserInfo`), and `./gradlew testDefaultDebugUnitTest` stays green. Server config save and the sync URL mapping behave unchanged.

size budget: ~22 changed lines, 3 files
out of scope: do not change `ServerConfigUtils.saveAlternativeUrl` itself — it keeps calling `ProcessUserDataActivity.getUserInfo` (now a delegator). `ConfigurationsRepositoryImpl.buildCouchdbUrl` is left to its owner task (#5's scope excludes it) and is not touched here. No new dependencies.

---

### 5. route community/parent-code config reads and clearPreferences through ConfigurationsRepository (roadmap 1+4, moves 9)

context: `CommunityTabViewModel` (lines 34-35) reads `sharedPrefManager.getParentCode()` and `getCommunityName()` directly while `getPlanetType()` on the next line already goes through `configurationsRepository` — an inconsistent split that leaves a `SharedPrefManager` injection in a ViewModel. `HomeCommunityDialogFragment` (lines 96-97) reads the same two keys via `sharedPrefManager` despite already injecting `configurationsRepository` (line 31). `SettingsViewModel.clearAllData` (line 47) calls `sharedPrefManager.clearPreferences()` directly after `configurationsRepository.clearAllData()`, leaving `SettingsViewModel` with a `SharedPrefManager` field (line 22) used only for that one call. `ConfigurationsRepository` already exposes `getPlanetType()` and `clearAllData()` but not these three accessors.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt` — add `fun getCommunityName(): String`, `fun getParentCode(): String`, and `suspend fun clearPreferences()` to the interface.
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` — implement the three as one-line delegations to the already-injected `sharedPrefManager` (line 48): `getCommunityName()` → `sharedPrefManager.getCommunityName()`, `getParentCode()` → `sharedPrefManager.getParentCode()`, `clearPreferences()` → `withContext(dispatcherProvider.io) { sharedPrefManager.clearPreferences() }`, mirroring `getPlanetType()` (line ~107).
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt` — route lines 34-35 through `configurationsRepository`; remove the `sharedPrefManager` field (line 24) and its import (line 12) — those two reads are its only `SharedPrefManager` use.
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt` — replace `sharedPrefManager.clearPreferences()` (line 47) with `configurationsRepository.clearPreferences()`; remove the `sharedPrefManager` field (line 22) and import (line 16) — line 47 is its only use.
- `app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt` — route lines 96-97 through `configurationsRepository`; remove the `sharedPrefManager` field (lines 27-28) and import — lines 96-97 are its only use.

steps:
1. Add the three accessors to `ConfigurationsRepository` and their delegating impls.
2. Swap the four call sites and drop the three now-unused `SharedPrefManager` fields/imports.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ConfigurationsRepositoryImplTest"` is green (the relaxed `sharedPrefManager` mock already in that test absorbs the new delegations), and `./gradlew testDefaultDebugUnitTest` stays green. The community tab, the home/community dialog, and Settings → Clear Data behave unchanged.

size budget: ~30 changed lines, 5 files
out of scope: do not add `getServerUrl`/`getUserId` routing — those touch more callers and would push this task past its file budget. `ConfigurationsRepositoryImplTest` is not edited.

---

### 6. add DiagnosticsRepositoryImplTest covering the pending-log and mark-uploaded paths (roadmap 8)

context: `DiagnosticsRepositoryImpl` has no test. Its read/marking paths are thin delegations worth locking in: `getPendingApkLogs` (line 25 → `apkLogDao.getPending()`), `markApkLogUploaded` (line 29 → `apkLogDao.markUploaded(localId, rev) != 0`), and `saveLogToRoom` (line 51 → `userSessionManager.getUserModel()` + `VersionUtils.getVersionName(context)` + `apkLogDao.insert(...)`). `VersionUtils.getVersionName(context)` requires a real Android context, so this test must run under Robolectric like `HealthRepositoryImplTest`.

files:
- `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt` (new). Do not modify `DiagnosticsRepositoryImpl.kt` or `DiagnosticsRepository.kt`.

steps:
1. Create the test with `@RunWith(RobolectricTestRunner::class)`, relaxed MockK mocks for `ApkLogDao`, `SharedPrefManager`, `UserSessionManager`, and `ApplicationProvider.getApplicationContext()` for the `@ApplicationContext` context.
2. Assert `getPendingApkLogs` returns the list from `apkLogDao.getPending()`; assert `markApkLogUploaded` returns `true` when `markUploaded` returns `1` and `false` when it returns `0`; assert `saveLogToRoom("crash", "msg", "time")` calls `apkLogDao.insert(...)` with an `ApkLog` whose `type == "crash"` and `error == "msg"` (use a `slot<ApkLog>` to capture).
3. Run the new test in isolation and the whole suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DiagnosticsRepositoryImplTest"` is green, and `./gradlew testDefaultDebugUnitTest` stays green.

size budget: ~90 new lines, 1 file
out of scope: no production code changes, no mocking of `VersionUtils` internals beyond what Robolectric provides.

---

### 7. add PersonalsViewModelTest locking in the personals list flow and the upload state machine (roadmap 8)

context: `PersonalsViewModel` has no test. It builds `personals` from `userSessionManager.getUserModel()` then `personalsRepository.getPersonalResources(user?.id)` (lines 27-29, `stateIn`) and runs an `uploadPersonal` state machine `Idle → Loading → Success|Error` with a `resetUploadState()` (lines 33-50). These are the user-visible behaviors most likely to regress.

files:
- `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModelTest.kt` (new). Do not modify `PersonalsViewModel.kt`.

steps:
1. Create the test using MockK + `MainDispatcherRule` + `TestDispatcherProvider` (mirror `TakeCourseViewModelTest`), with relaxed mocks for `PersonalsRepository` and `UserSessionManager`.
2. Assert the `personals` StateFlow collects the list from `getPersonalResources` for the user returned by `getUserModel()` (use `coEvery { userSessionManager.getUserModel() } returns userEntity` and a `flow { emit(listOf(...)) }` for `getPersonalResources`).
3. Assert `uploadPersonal` drives `uploadState` through `Loading` then `Success` when `uploadPersonal` returns a string, and through `Loading` then `Error` when it throws; assert `resetUploadState()` returns it to `Idle`.
4. Run the new test and the whole suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.personals.PersonalsViewModelTest"` is green, and `./gradlew testDefaultDebugUnitTest` stays green.

size budget: ~70 new lines, 1 file
out of scope: no production code changes; do not test `PersonalsRepositoryImpl` here.

---

### 8. add EventsDetailViewModelTest covering the parallel load and the null-meetup branch (roadmap 8)

context: `EventsDetailViewModel` has no test. `loadData` (lines 39-57) runs `userRepository.getUserModel()`, `eventsRepository.getMeetupByLocalId`, and `eventsRepository.getJoinedMembers` concurrently under `coroutineScope { async { } }`, and short-circuits to only the user when `meetUpId` is blank; `updateMeetup` (lines 59+) publishes `updateSuccess`. The concurrency and the blank-id branch are easy to break silently.

files:
- `app/src/test/java/org/ole/planet/myplanet/ui/events/EventsDetailViewModelTest.kt` (new). Do not modify `EventsDetailViewModel.kt` or `EventsRepositoryImpl.kt`.

steps:
1. Create the test using MockK + `MainDispatcherRule` + `TestDispatcherProvider` (mirror `TakeCourseViewModelTest`), with relaxed mocks for `EventsRepository` and `UserRepository`.
2. Assert `loadData("m1")` sets `user`, `meetup`, and `members` from the three repository calls; assert `loadData(null)` sets only `user` and leaves `meetup`/`members` null/empty.
3. Assert `updateMeetup(...)` sets `updateSuccess` to `true` when `eventsRepository.updateMeetup` returns `true` and `false` when it returns `false`.
4. Run the new test and the whole suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.events.EventsDetailViewModelTest"` is green, and `./gradlew testDefaultDebugUnitTest` stays green.

size budget: ~80 new lines, 1 file
out of scope: no production code changes; mock the `EventsRepository` interface only — do not instantiate `EventsRepositoryImpl`.

---

### 9. add LeadersViewModelTest covering the empty-leaders and parsed-leaders branches (roadmap 8)

context: `LeadersViewModel` has no test. `loadLeaders` (runs in `init` via `viewModelScope.launch(dispatcherProvider.default)`) reads `sharedPrefManager.getCommunityLeaders()` and either sets `_leaders` to `emptyList()` when the string is empty or to `UserEntity.parseLeadersJson(string)` otherwise (lines 36-46). The empty-vs-populated branch and the JSON parse delegation are the whole behavior of this screen's data source.

files:
- `app/src/test/java/org/ole/planet/myplanet/ui/community/LeadersViewModelTest.kt` (new). Do not modify `LeadersViewModel.kt`. Because `parseLeadersJson` uses `org.json.JSONObject`, run under `@RunWith(RobolectricTestRunner::class)` like `ServerUrlMapperTest`.

steps:
1. Create the test with MockK for `SharedPrefManager`, a `TestDispatcherProvider` advanced by `MainDispatcherRule`, and the Robolectric runner.
2. Assert that when `getCommunityLeaders()` returns `""`, collecting `leaders` yields an empty list.
3. Assert that when `getCommunityLeaders()` returns a `{"docs":[{"name":"Ada","_id":"org.couchdb.user:Ada"}]}` string, `leaders` yields one `UserEntity` with `name == "Ada"` and `id == "org.couchdb.user:Ada"`.
4. Run the new test and the whole suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.community.LeadersViewModelTest"` is green, and `./gradlew testDefaultDebugUnitTest` stays green.

size budget: ~60 new lines, 1 file
out of scope: no production code changes; do not route `getCommunityLeaders` anywhere in this task.

---

### 10. add StorageCategoryViewModelTest covering item toggles, toggle-all, and the delete re-entry guard (roadmap 8)

context: `StorageCategoryViewModel` has no test. `toggleItemChecked` (lines 60-66) flips `isChecked` for one resource id, `toggleAllChecked` (lines 68-75) sets every item to the inverse of the current all-checked state, and `deleteItems` (lines 77-86) guards re-entry with `if (_uiState.value.isDeleting) return` before calling `resourcesRepository.deleteOfflineResources(...)` and emitting `deleteCompleteEvent`. These UI-state transitions and the guard are the load-bearing logic of the storage cleanup screen.

files:
- `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModelTest.kt` (new). Do not modify `StorageCategoryViewModel.kt`. Run under `@RunWith(RobolectricTestRunner::class)` because `loadResources`/`deleteItems` call `FileUtils.getOlePath(context)`.

steps:
1. Create the test with a relaxed MockK `ResourcesRepository`, a `TestDispatcherProvider` advanced by `MainDispatcherRule`, and `ApplicationProvider.getApplicationContext()` for the `@ApplicationContext` context. Construct the viewmodel directly (it has no Hilt-only field injection).
2. Seed `_uiState.items` (e.g. via a `loadResources` call whose `resourcesRepository.getOfflineResourceItems` returns two unchecked `OfflineResourceItem`s under Robolectric) and assert `toggleItemChecked("r1")` flips only that item's `isChecked`.
3. Assert `toggleAllChecked()` sets all items to `true` when none are checked, and to `false` when all are checked.
4. Assert `deleteItems(list)` sets `isDeleting` true, calls `resourcesRepository.deleteOfflineResources(...)`, then resets `isDeleting` and emits on `deleteCompleteEvent`; assert a second `deleteItems` issued while `isDeleting` is true is a no-op (no second `deleteOfflineResources` call).
5. Run the new test and the whole suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.settings.StorageCategoryViewModelTest"` is green, and `./gradlew testDefaultDebugUnitTest` stays green.

size budget: ~90 new lines, 1 file
out of scope: no production code changes; do not refactor `FileUtils.getOlePath`.

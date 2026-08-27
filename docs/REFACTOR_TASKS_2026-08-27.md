# myPlanet refactor round — 10 independent work orders

**date:** 2026-08-27
**base commit:** `89fd72c251df68ed01094091d4de7ba7a2571ebe` (`master`)
**open PRs checked (37):** 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

Every file each of those PRs touches was enumerated (`git diff` against each PR's merge base; PR 4075 via the GitHub API — it only edits a workflow file) and treated as off-limits. Notably locked areas: all of `TeamsRepository*`, `CoursesRepository*`, `SubmissionsRepository*`, `SurveysRepository*`, `HealthRepository*`, `VoicesRepository*`, `UserRepositoryImpl`, `ActivitiesRepository*`, `ProgressRepository*`, `RatingsRepository*`, `TagsRepository*`, `ChatRepository*`, `CommunityRepository*`, `EventsRepository*`, `FeedbackRepository*`, `data/room/AppDatabase.kt`, `di/RepositoryModule.kt`, `di/RoomModule.kt`, `di/ServiceModule.kt`, `services/UploadManager.kt`, `services/sync/TransactionSyncManager.kt`, and the DAOs `AchievementDao`, `CertificationDao`, `ChatDao`, `CourseDao`, `CourseProgressDao`, `ExamDao`, `FeedbackDao`, `HealthExaminationDao`, `MeetupDao`, `NewsDao`, `OfflineActivityDao`, `RatingDao`, `RemovedLogDao`, `SubmissionDao`, `SyncCursorDao`, `TagDao`, `TeamTaskDao`.

Because `di/RepositoryModule.kt` is locked by PRs 15825/15824, **no task introduces a new repository interface or Hilt binding** — every task works inside repositories, DAOs and ViewModels that already exist. Roadmap numbers refer to the brief's list; 9/10 are the never-scheduled north stars.

---

### 1. push the "needs update" predicate for resources from Kotlin into SQL (roadmap 1+7, moves 9)

context: `ResourcesRepositoryImpl.countLibrariesNeedingUpdate` (line 212-216) loads **every** public shelf row for a user via `myLibraryDao.getPublicForUserPattern(...)` and then does `.count { it.needToUpdate() }` — a full row hydration to produce an `Int` that drives the dashboard "resources need updating" badge. The same load-then-filter shape repeats in `getLibraryListForUser` (line 191-195) and twice in `getDownloadSuggestionList` (line 463-470, where `myLibraryDao.getPublic()` hydrates the entire public catalogue). `MyLibrary.needToUpdate()` (`model/MyLibrary.kt:148-150`) is `!resourceOffline || (resourceLocalAddress != null && _rev != downloadedRev)` — expressible verbatim in SQLite using `IS NOT` for the null-safe `_rev`/`downloadedRev` comparison.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` — add three queries next to the existing shelf-membership block (line 94-121)
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — `getLibraryListForUser`, `countLibrariesNeedingUpdate`, `getDownloadSuggestionList`
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` — the `getLibraryListForUser returns filtered items` test (line 452-463) mocks `getPublicForUserPattern`
- do NOT touch `ResourcesRepository.kt` (the interface signatures do not change), `model/MyLibrary.kt` (keep `needToUpdate()` — other call sites use it), or any other DAO.

steps:
1. In `MyLibraryDao.kt` add three queries. Room's `@Query` takes a literal, so the needs-update predicate is spelled out in each of them: `getPublicForUserPatternNeedingUpdate(userPattern: String): List<MyLibrary>` (`isPrivate = 0 AND userId LIKE :userPattern ESCAPE '\' AND (resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))`), `countPublicForUserPatternNeedingUpdate(userPattern: String): Int` (same `WHERE`, `SELECT COUNT(*)`), and `getPublicNeedingUpdate(): List<MyLibrary>` (`isPrivate = 0 AND (…same needs-update clause…)`).
2. Rewrite `getLibraryListForUser` to call `getPublicForUserPatternNeedingUpdate(userIdPattern(userId))` with no Kotlin `.filter`.
3. Rewrite `countLibrariesNeedingUpdate` to `return myLibraryDao.countPublicForUserPatternNeedingUpdate(userIdPattern(userId))`.
4. Rewrite `getDownloadSuggestionList`: the user branch calls `getPublicForUserPatternNeedingUpdate`, the fallback calls `getPublicNeedingUpdate()`; drop both `.filter { it.needToUpdate() }` calls.
5. Update `ResourcesRepositoryImplTest` to mock the new DAO methods, and add one test asserting `countLibrariesNeedingUpdate` returns the DAO count without calling `getPublicForUserPattern`.

acceptance: `./gradlew testDefaultDebugUnitTest` green. On the dashboard, the resource-update badge shows the same number as before; Library → "download suggestions" still lists exactly the resources that are missing or stale. Verify by installing (`./gradlew installDefaultDebug`) against a synced server and comparing the badge count before/after.

size budget: ~60 changed lines across 3 files.

out of scope: leave `getAllLibrariesToSync` (line 430-432) alone — its `.filter { needToUpdate() }` over `getSyncable()` is a separate (arguably redundant) case and belongs to a follow-up. No new interface members, no Flow changes.

---

### 2. stop hydrating whole notification rows just to read their ids (roadmap 1+4+7, moves 9)

context: `NotificationsRepositoryImpl.markNotificationsAsRead` (line 113-120) calls `notificationDao.getByIds(...).map { it.id }` — it materialises full `AppNotification` rows (message text, dates, sync flags) only to learn which ids exist. `deleteNotifications` (line 404-410) does the same, and `markAllUnreadAsRead` (line 122-128) loads every unread row through `getNotifications(userId, "unread", false)` just to build an id set. The same class also injects `examDao` (line 32) which is never referenced anywhere in the file — a dead Hilt edge into the exam feature.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` — add two id-projection queries
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — `markNotificationsAsRead`, `markAllUnreadAsRead`, `deleteNotifications`, constructor
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` — constructor call (line 51-60) and the `deleteNotifications` tests (line 374-396)
- do NOT touch `NotificationsRepository.kt` (no signature changes), `data/room/dao/TeamTaskDao.kt` or `ExamDao.kt` (both owned by open PRs), and leave `getTaskTeamName` in place — removing it would orphan `TeamTaskDao.getByTitle`, which is locked.

steps:
1. Add to `NotificationDao`: `@Query("SELECT id FROM notifications WHERE id IN (:ids)") suspend fun getExistingIds(ids: List<String>): List<String>`.
2. Add `getUnreadIds(userId: String): List<String>` whose `WHERE` mirrors `getNotifications` with `isAdmin = 0` exactly — `userId = :userId AND message != 'INVALID' AND message != '' AND isRead = 0` — so the returned id set is byte-for-byte what callers see today.
3. Swap the three call sites to the new queries (`existingIds`, `unreadIds`, `deletedIds`), keeping the early-return-on-empty guards and the returned `Set<String>` contract unchanged.
4. Delete the `examDao` constructor parameter and its `import org.ole.planet.myplanet.data.room.dao.ExamDao`.
5. Update the test: drop the `examDao` mock and the argument from the `NotificationsRepositoryImpl(...)` call; re-point the `deleteNotifications` verifications at `getExistingIds`.

acceptance: `./gradlew testDefaultDebugUnitTest` green. In the notifications screen: select several notifications → "mark as read" clears them and the unread badge drops by exactly the number that were unread; "mark all as read" zeroes the badge; delete removes exactly the selected rows.

size budget: ~45 changed lines across 3 files.

out of scope: do not restructure `NotificationsViewModel.loadNotifications`, do not touch the team-name/join-request resolution helpers, and do not add or change any interface method on `NotificationsRepository`.

---

### 3. replace the personals mutation lambda with an explicit repository intent (roadmap 1+3, moves 9)

context: `PersonalsRepository.updatePersonalResource(id: String, updater: (Personal) -> Unit)` (`repository/PersonalsRepository.kt:21`) hands a mutable Room entity out to the UI to be modified in place — `PersonalsFragment.kt:125-128` passes a lambda that sets `description` and `title`, threaded through `PersonalsViewModel.kt:56-60`. That is a data-layer write expressed as a UI callback: the repository cannot validate it, cannot log it, and cannot be reimplemented on a non-JVM target. It also forces a read-modify-write (`personalDao.findByDocId` → fallback `findById` → `update`) for what is a two-column edit.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` (line 67-73, and the comment on line 56 that names the method)
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt` (`onEditPersonal`)
- `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` (line 145-178)
- do NOT touch `data/room/dao/PersonalDao.kt` — keep using the existing `findByDocId`/`findById`/`update` trio so this stays a boundary change, not a schema change.

steps:
1. In `PersonalsRepository.kt` replace `updatePersonalResource(id, updater)` with `suspend fun updatePersonalDetails(id: String, title: String, description: String)`.
2. In `PersonalsRepositoryImpl.kt` implement it: resolve the row as today (`findByDocId(id) ?: findById(id)`), set `title` and `description` on it, `personalDao.update(it)`. Update the `distinctByContent` comment on line 56 to name the new method.
3. In `PersonalsViewModel.kt` replace `updatePersonalResource(id, updater)` with `updatePersonalDetails(id, title, description)` launched on `viewModelScope`.
4. In `PersonalsFragment.onEditPersonal`, call `viewModel.updatePersonalDetails(id, title, desc)` — the `title`/`desc` locals already exist a few lines above.
5. Rewrite the two `updatePersonalResource …` tests as `updatePersonalDetails uses _id if found` / `… falls back to id`, asserting the entity's `title`/`description` and `coVerify(exactly = 1) { personalDao.update(...) }`.

acceptance: `./gradlew testDefaultDebugUnitTest` green. My Personals → edit an item, change title and description, submit: the list row updates immediately (the repository `Flow` emits because `title`/`description` are in its `distinctByContent` key), and the change survives an app restart.

size budget: ~40 changed lines across 5 files.

out of scope: leave `ResourcesRepository.updateLibraryItem(id, updater)` alone (same smell, different owner — see task 1's file list), and do not touch `savePersonalResource` or the upload path.

---

### 4. delete four dead Hilt injections from services and one fragment (roadmap 4+8, moves 9)

context: four classes ask Hilt for dependencies they never use, which keeps a compile-time and DI-graph edge alive for nothing: `services/sync/SyncManager.kt:71` injects `private val teamsRepository: TeamsRepository` (referenced nowhere else in the 560-line file), `services/upload/UploadCoordinator.kt:28` injects `@ApplicationContext private val context: Context` (unused), `services/DownloadService.kt:58` field-injects `dispatcherProvider` (unused), and `ui/submissions/SubmissionsFragment.kt:34` field-injects `userSessionManager` (unused). `SyncManager`'s dead edge is the worst: it makes the sync orchestrator formally depend on the whole teams domain.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` (param line 71, import line 45)
- `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt` (constructs `SyncManager(...)` at line 53 and passes `teamsRepository` at line 64)
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` (param line 28, imports lines 3 and 6)
- `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` (field line 58 with its `@Inject`, import line 47)
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt` (field line 34 with its `@Inject`, import line 22)
- do NOT touch `base/BaseContainerFragment.kt`: its `prefData` field looks unused in that file but subclasses read it (`base/BaseDashboardFragment.kt:292`, `ui/dashboard/BellDashboardFragment.kt:121`, `ui/dashboard/DashboardPluginFragment.kt:56`). Do not touch `di/` — no module edits are needed to drop a constructor parameter.

steps:
1. Remove the `teamsRepository` parameter and the `TeamsRepository` import from `SyncManager`; confirm with `grep -n teamsRepository` that the file has zero hits afterwards.
2. Update `SyncManagerTest` to drop the `teamsRepository` mock field and the corresponding constructor argument.
3. Remove the `@ApplicationContext context` parameter from `UploadCoordinator` plus the now-unused `Context` and `ApplicationContext` imports.
4. Remove the `dispatcherProvider` field, its `@Inject`, and the `DispatcherProvider` import from `DownloadService` (leave the `Context` parameters in the companion helpers — those are locals, not injections).
5. Remove the `userSessionManager` field, its `@Inject`, and the `UserSessionManager` import from `SubmissionsFragment`.

acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew assembleDefaultDebug assembleLiteDebug` all green (a broken Hilt graph fails at KSP time, so a clean assemble is the real check). Behaviour must be identical: run a manual sync from Settings, start a download, and open My Submissions.

size budget: ~15 removed lines across 5 files.

out of scope: do not chase unused injections in files an open PR owns (`services/UploadManager.kt`, `ui/sync/SyncActivity.kt`, `ui/dashboard/DashboardActivity.kt`, `base/BaseResourceFragment.kt`, `ui/teams/tasks/TeamsTasksViewModel.kt`). Do not reformat surrounding constructors.

---

### 5. move the my-life seed-if-empty sequence out of the ViewModel (roadmap 1+3)

context: `LifeViewModel.loadMyLifeList` (`ui/life/LifeViewModel.kt:32-45`) orchestrates a data-layer sequence from the UI: read `getMyLifeByUserId(userId)`, and if the result is empty call `seedMyLifeIfEmpty(...)` then read the whole table *again*. That is three repository round trips per screen open (two of them full table scans) and a race window between the read and the seed that the repository's own `seedMutex` (`LifeRepositoryImpl.kt:29`) cannot close from outside. `LifeRepositoryImpl` already owns exactly this pattern internally for the dashboard (`getMyLifeForDashboard`, line 79-108) — the standalone screen just reimplements it badly.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` (add next to `getMyLifeForDashboard`; reuse `seedMyLifeIfEmpty` and `getMyLifeByUserId`)
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`
- do NOT touch `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt`, and do NOT add a `LifeViewModelTest` under `ui/life/` in the test source set — PR 16096 owns that fragment and introduces a test file at exactly that path.

steps:
1. Add `suspend fun getMyLifeForUser(userId: String?, seedBase: List<MyLife>): List<MyLife>` to `LifeRepository`.
2. Implement it in `LifeRepositoryImpl`: read via `getMyLifeByUserId(userId)`, return it when non-empty, otherwise `seedMyLifeIfEmpty(userId, seedBase)` and return `getMyLifeByUserId(userId, ensureLatest = true)`. Unlike `getMyLifeForDashboard` it must **not** filter on `isVisible` and must **not** touch the SharedPreferences cache — the screen shows hidden items so they can be toggled back on.
3. In `LifeViewModel.loadMyLifeList`, replace the three-call block inside `withContext(dispatcherProvider.io)` with the single `lifeRepository.getMyLifeForUser(userId, MyLife.defaultItems(userId, context::getString))` call.
4. Add two tests to `LifeRepositoryImplTest`: seeded-then-returned when `countByUserId` is 0, and returns-existing-without-seeding when rows exist (`coVerify(exactly = 0) { myLifeDao.insertAll(any()) }`).

acceptance: `./gradlew testDefaultDebugUnitTest` green. My Life on a fresh install shows the full default item list once; hiding an item and reopening the screen still shows it (greyed/toggled, not dropped); reordering still persists.

size budget: ~45 changed lines across 4 files.

out of scope: do not change `getMyLifeForDashboard` or its `CachedMyLifeItem` preferences cache, do not remove `dispatcherProvider` from the ViewModel (`updateVisibility` and `updateMyLifeListOrder` still use it), and do not move `MyLife.defaultItems` out of the ViewModel.

---

### 6. drop the dead retry-queue API surface (roadmap 4+5+8)

context: `services/retry/RetryQueue.kt` is a `@Singleton` facade over `RetryRepository`, and four of its members have zero production callers: `queueFailedOperations` (line 56), `getPendingCount` (line 83), `resetAllPending` (line 104) and — separately — `safeClearQueue`, since `SettingsViewModel.kt:54` calls `retryRepository.safeClearQueue()` directly. `RetryRepository.resetAllPending` (`repository/RetryRepository.kt:29`) exists only to serve that dead facade method, and `RetryDao.resetPendingRetryTime` (`data/room/dao/RetryDao.kt:37-38`) exists only to serve *that*. Three layers of interface kept alive by nothing.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt` — remove `queueFailedOperations`, `getPendingCount`, `resetAllPending`
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt` — remove `resetAllPending`
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` — remove the `resetAllPending` override (line 98-100)
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt` — remove `resetPendingRetryTime`
- `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt` — remove `resetAllPending resets retry time` (line 211-216)
- do NOT touch `services/retry/RetryQueueWorker.kt` (it uses `isCurrentlyProcessing`, `setProcessing`, `getPendingOperations`, `cleanup`, `markInProgress`, `markCompleted`, `markFailed` — all live), `ui/settings/SettingsViewModel.kt`, or `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueTest.kt`.

steps:
1. Delete the three dead methods from `RetryQueue`; check no import becomes unused (`RetryOperation` is still needed by `getPendingOperations`).
2. Delete `resetAllPending` from `RetryRepository` and its override from `RetryRepositoryImpl`; `timeProvider` stays (used by `getPending`, `cleanup`, `recoverStuckOperations`).
3. Delete `resetPendingRetryTime` from `RetryDao`.
4. Delete the one test that covered it. Run `grep -rn 'resetAllPending\|resetPendingRetryTime\|queueFailedOperations\|retryQueue.getPendingCount' app/src` and confirm zero hits.

acceptance: `./gradlew testDefaultDebugUnitTest` green and `./gradlew assembleDefaultDebug` green. No behaviour change: fail an upload while offline, then Settings → clear retry queue still reports the same result, and the periodic `RetryQueueWorker` still drains the queue when connectivity returns.

size budget: ~35 removed lines across 5 files (should earn the `less` label).

out of scope: `RetryQueue.safeClearQueue` stays for now — removing it means editing `RetryQueueTest.kt`, which a follow-up should own together with the rest of that test file. Do not narrow `deletePendingAndAbandonedOperations`, and do not merge `RetryQueue` into `RetryRepository`.

---

### 7. delete the dead `queryPending` upload query contract (roadmap 4+5+8)

context: `UploadRepository.queryPending(config: UploadQueryContract<T>)` (`repository/UploadRepository.kt:7`) has **no** production caller — the only references in the whole tree are its own implementation (`UploadRepositoryImpl.kt:31-40`) and three tests. The pending-item queries the uploaders actually use come from `UploadConfigs`' `fetchPendingItems` lambdas, not from this method. It drags along `UploadQueryContract<T : Any>` (a data class whose type parameter is never used inside it — the impl needs `@Suppress("UNCHECKED_CAST")` to make the casts compile), the `UploadQueryType` enum, the private `hydrateSubmissions` helper (line 78-83) and the whole `answerDao` injection.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt` — remove `queryPending`, `UploadQueryContract`, `UploadQueryType`
- `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt` — remove the override, `hydrateSubmissions`, the `answerDao` parameter, and the imports that go unused (`AnswerDao`, `MembershipDoc`, `Submission`)
- `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt` — remove the three `queryPending …` tests (line 62-110) and the `answerDao` mock
- do NOT touch `services/upload/UploadCoordinator.kt` or `services/upload/UploadConfigs.kt`; `UploadUpdateContract` / `UploadUpdateType` / `markUploaded` are live (`UploadCoordinator.kt:236-248`) and stay exactly as they are.

steps:
1. Remove `queryPending` from the `UploadRepository` interface, then delete the `UploadQueryContract` data class and the `UploadQueryType` enum from the same file.
2. Remove the `queryPending` override and the `@Suppress("UNCHECKED_CAST")` from `UploadRepositoryImpl`, then delete `hydrateSubmissions` (it had no other caller).
3. Remove the `answerDao` constructor parameter and prune the imports that are now unused; keep `examDao` and `submissionDao` (both used by `markUploaded`/`markExamsUploaded`).
4. Delete the three `queryPending` tests and the `answerDao` mock/constructor argument in `UploadRepositoryImplTest`.
5. `grep -rn 'queryPending\|UploadQueryContract\|UploadQueryType' app/src` must return nothing.

acceptance: `./gradlew testDefaultDebugUnitTest` green and `./gradlew assembleDefaultDebug` green. No user-visible change; confirm uploads still work end to end — take a survey offline, sync, and check the submission reaches the server (the path runs through `UploadCoordinator` + `UploadConfigs`, untouched here).

size budget: ~80 removed lines across 3 files (should earn the `less` label).

out of scope: do not simplify `UploadUpdateContract` into a bare enum parameter (that edit needs `UploadCoordinator.kt`, which task 4 owns), and do not touch any DAO.

---

### 8. let the enterprises CSV export read its own data instead of taking the UI's list (roadmap 1+3, moves 9)

context: `EnterprisesRepository.exportReportsAsCsv(reports: List<MyTeam>, teamName: String)` (`repository/EnterprisesRepository.kt:12`) takes a list of already-loaded entities *back* from the UI. `EnterprisesReportsFragment.kt:84` passes its own `reports` field (assigned from the adapter-feeding callback at line 387), so the exported CSV silently depends on whatever the screen last rendered — if the flow has not emitted yet, the export writes a header-only file. The repository already owns the canonical report query and its filter/sort rules (`getReportsFlow`, `EnterprisesRepositoryImpl.kt:87-99`, backed by `teamDao.observeByTeamIdAndDocType(teamId, "report")`), and `TeamDao` already exposes the matching suspend query `getByTeamIdAndDocType(teamId, docType)` (`data/room/dao/TeamDao.kt:21`).

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` (line 101 onwards)
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt` (line 104-106)
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (line 84 only)
- `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt` (line 25-45)
- do NOT touch `data/room/dao/TeamDao.kt` (the query it needs already exists), `EnterprisesReportsAdapter.kt`, or the `reports` field the adapter still uses for rendering.

steps:
1. Change the interface member to `suspend fun exportReportsAsCsv(teamId: String, teamName: String): String`.
2. In `EnterprisesRepositoryImpl`, fetch the rows itself: `teamDao.getByTeamIdAndDocType(teamId, "report").filter { it.status != "archived" }.sortedByDescending { it.createdDate }` — the identical predicate and ordering `getReportsFlow` uses — then run the existing `StringBuilder` body over that list unchanged.
3. Update `EnterprisesViewModel.exportReportsAsCsv` to forward `(teamId, teamName)`.
4. At `EnterprisesReportsFragment.kt:84` pass `teamId` instead of the `reports` field; leave the surrounding `createFileLauncher` result handling and the `teamsRepository.getTeamNameFromPrefs()` call as they are.
5. Update the existing test to stub `teamDao.getByTeamIdAndDocType("team1", "report")` and call `exportReportsAsCsv("team1", "Test Team")`; add one assertion that an `archived` report is excluded from the output.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Enterprises → Reports → Export CSV: the saved file contains the same header line and the same non-archived reports in newest-first order as before, and now also produces correct content when export is tapped immediately after opening the tab.

size budget: ~45 changed lines across 5 files.

out of scope: do not add a suspend `getReports` to the public interface (keep the fetch private to the impl), do not change `getReportsFlow`, and do not touch how the fragment resolves the team name.

---

### 9. keep storage-category selection state in its ViewModel (roadmap 3, moves 10)

context: `StorageCategoryDetailFragment` reaches into `viewModel.uiState.value` twice to re-derive state the ViewModel already owns — `.items.filter { it.isChecked }` for "delete selected" (line 107) and `.items` for "delete all" (line 114) — then hands those lists *back* to `viewModel.deleteItems(...)`. `updateSelectionState` (line 160-174) recomputes `checkedCount` and `allChecked` in the view layer on every emission. Reading `.value` off a `StateFlow` inside a click listener is also the classic stale-snapshot bug, and none of this survives a move to Compose.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt` (`StorageCategoryUiState`, `deleteItems`)
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt` (the two click listeners, `updateSelectionState`, `deleteItems`)
- do NOT touch `ui/settings/StorageBreakdownFragment.kt` (it computes its own directory scan — a separate task), `repository/ResourcesRepository*.kt` (task 1 owns the impl), or `res/layout/fragment_storage_category_detail.xml`.

steps:
1. Add derived fields to `StorageCategoryUiState`: `selectedCount: Int = 0`, `totalCount: Int = 0`, `allSelected: Boolean = false`, and populate them wherever `items` is assigned (`loadResources`, `toggleItemChecked`, `toggleAllChecked`) via a small private `StorageCategoryUiState.withCounts()` helper so there is one place computing them.
2. Replace `deleteItems(items: List<OfflineResourceItem>)` with `deleteSelected()` and `deleteAll()`, both reading `_uiState.value.items` inside the ViewModel and keeping the existing `isDeleting` guard, `dispatcherProvider.io` launch and `_deleteCompleteEvent` emission.
3. In the fragment, make the two click listeners use `state.selectedCount` / `state.totalCount` for the confirmation strings and call `viewModel.deleteSelected()` / `viewModel.deleteAll()`; capture the latest state from the `collectWhenStarted` block rather than reading `.value`.
4. Reduce `updateSelectionState` to consuming `state.allSelected` and `state.selectedCount` — no `count {}` in the fragment — and delete the fragment's private `deleteItems` helper.
5. Confirm no `viewModel.uiState.value` reference remains in the fragment.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Settings → storage breakdown → tap a category: the "select all" checkbox, the "N selected" label and the enabled/disabled state of both delete buttons behave exactly as before; deleting selected files removes exactly those files and dismisses the sheet; deleting all clears the category.

size budget: ~55 changed lines across 2 files.

out of scope: do not convert the screen to Compose, do not change `OfflineResourceItem`, and do not alter the `ResourcesRepository.deleteOfflineResources` call signature.

---

### 10. route the home community dialog through its ViewModel (roadmap 3, moves 10)

context: `HomeCommunityDialogFragment` field-injects `SharedPrefManager` and `ConfigurationsRepository` (`ui/community/HomeCommunityDialogFragment.kt:27-31`) and assembles its own screen state in `initCommunityTab` (line 94-108): `sharedPrefManager.getParentCode()`, `getCommunityName()`, `configurationsRepository.getPlanetType()`. `CommunityTabViewModel` (`ui/community/CommunityTabViewModel.kt:32-47`) already produces exactly those three values as `CommunityTabState`, and the sibling `CommunityTabFragment` (line 21, 30-39) already consumes it from the identical `FragmentTeamDetailBinding`. So one of two fragments over the same layout talks to a repository directly while the other does it properly.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt`
- do NOT touch `ui/community/CommunityTabViewModel.kt` (it already exposes everything needed), `ui/community/CommunityTabFragment.kt`, `ui/community/CommunityPagerAdapter.kt`, or `repository/ConfigurationsRepository*.kt`.

steps:
1. Delete both `@Inject lateinit var` fields and their imports (`SharedPrefManager`, `ConfigurationsRepository`); add `private val viewModel: CommunityTabViewModel by viewModels()` with `androidx.fragment.app.viewModels`.
2. Rewrite `initCommunityTab` to launch on `viewLifecycleOwner.lifecycleScope` and await `viewModel.state.filterNotNull().first()`, copying the collection idiom from `CommunityTabFragment.onViewCreated`.
3. Preserve the current behaviour precisely: the pager argument stays `"${state.communityName}@${state.parentCode}"` and the third `CommunityPagerAdapter` argument stays `true` (this dialog is the community-leader variant — `CommunityTabFragment` passes `false` and uses `planetCode`); `binding.title.text` stays `state.communityName`; `binding.subtitle.text` stays `state.planetType`; keep the three `ContextCompat.getColor` calls and `binding.llActionButtons.visibility = View.GONE`.
4. Because the call site moves inside a coroutine, keep the existing `tabLayoutMediator` assignment and `attach()` inside that block so `onDestroyView`'s `detach()` still pairs with it.
5. Verify with `grep -n 'configurationsRepository\|sharedPrefManager' app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt` returning nothing.

acceptance: `./gradlew testDefaultDebugUnitTest` green and `./gradlew assembleDefaultDebug` green. From the login screen, tap the community button (`LoginActivity.kt:193`): the bottom sheet still shows the community name as the title, the planet type as the subtitle, the same tab set, and no action buttons.

size budget: ~25 changed lines, 1 file.

out of scope: do not give `LeadersViewModel`/`LeadersFragment` the same treatment (already clean), do not change `ConfigurationsRepository.getPlanetType()` to a suspend function, and do not touch `LoginActivity.kt` (owned by open PRs).

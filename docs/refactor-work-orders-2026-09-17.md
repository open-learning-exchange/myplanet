# myPlanet refactor work orders — repository boundaries round

- date: 2026-09-17
- base commit: fcb4c53b2d1c4134789f1c07bd785d97d7f4215b
- open PRs checked: 4075 8175 10993 13287 13355 13415 13604 13657 13848 13928 14427 14650 14883 14893 14960 15108 15198 15226 15266 15267 15412 15519 15559 15808 15820 15824 15825 15951 16270 16594 16623 16624 17187 17222 17254 17255
- every file an open PR touches is off-limits and is named as such in `files:` / `out of scope:` where relevant

Each task is independently mergeable in any order. No file appears in more than one task.

---

### 1. Load only id+title for the achievement resource picker (roadmap 7+1)
context: `EditAchievementFragment.showResourceDialog` calls `viewModel.getAllLibraries()` (ui/user/EditAchievementFragment.kt:413), which materializes every row of `my_library` as a full `MyLibrary` entity (model/MyLibrary.kt has ~74 properties) just to show a checkbox list. `createResourceList` (EditAchievementFragment.kt:489) reads only `title`, and `serializeResource()` is needed only for the rows the user actually confirms. A projection query plus a refetch of the selected ids removes a large, unbounded `SELECT *` from a dialog path.
files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` — add a projection query and a small data class next to the existing `ResourceTitleProjection` at the bottom of the file. Leave all existing queries untouched.
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt` — add one method to the interface.
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — implement it; also remove `getAllLibraries` here and in the interface (its only callers are the two files below).
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt` — replace `getAllLibraries()` with the projection accessor; add a `getLibraryItemsByIds` passthrough (the repository method already exists).
- `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt` — swap the dialog to the projection list and refetch selected entities on confirm.
steps:
1. In `MyLibraryDao`, add `@Query("SELECT id, title FROM my_library")` returning a new `data class IdTitleProjection(val id: String, val title: String?)` declared beside `ResourceTitleProjection`. No `ORDER BY` — preserve the current unspecified order so the list looks identical.
2. In `ResourcesRepository`, replace `suspend fun getAllLibraries(): List<MyLibrary>` with `suspend fun getLibraryIdTitles(): List<IdTitleProjection>`; implement the delegation in `ResourcesRepositoryImpl` (`myLibraryDao.getIdTitles()`).
3. In `AchievementViewModel`, replace `getAllLibraries()` with `getLibraryIdTitles()` and add `suspend fun getLibraryItemsByIds(ids: List<String>)` delegating to the existing `resourcesRepository.getLibraryItemsByIds`.
4. In `EditAchievementFragment`, change `showResourceDialog` to hold `List<IdTitleProjection>`; `createResourceList` keeps matching `prevList` on `title` exactly as today.
5. In the dialog's positive-button block (around EditAchievementFragment.kt:427), collect the selected projections' `id`s, call `viewModel.getLibraryItemsByIds(ids)` inside the same coroutine, and push `serializeResource()` for each fetched entity into `resourceArray` — preserving the current order of selection.
6. Remove the now-unused `MyLibrary` import from the fragment if it is no longer referenced.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: edit an achievement → resources picker lists the same titles in the same order; checking items and confirming attaches them to the achievement exactly as before.
size budget: ~60 changed lines, 5 files
out of scope: no changes to `ResourceTitleProjection`, `getResourceTitles`, or any other DAO query; no Room schema/index changes (AppDatabase is owned by open PRs).

---

### 2. Compute the task badge per team instead of once globally (roadmap 1+8)
context: In `NotificationsRepositoryImpl.getTeamNotifications` (repository/NotificationsRepositoryImpl.kt, ~line 392–403) the code fetches `teamTaskDao.getTasksForUserBetween(userId, current, tomorrow.timeInMillis)` once and sets `hasTask = tasks.isNotEmpty()` before the per-team loop, so every `TeamNotificationInfo` in the returned map reports the same boolean — a team with no tasks still shows the task badge if any other team has one. The tasks list already carries `teamId` per row, so the fix is a Kotlin-side grouping with no DAO change.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — only inside `getTeamNotifications`. Leave `teamTaskDao`/`voicesRepository` injections and the `chatCountsById` lookup alone.
steps:
1. Keep the single `getTasksForUserBetween` call where it is; replace `val hasTask = tasks.isNotEmpty()` with `val taskTeamIds = tasks.mapNotNull { it.teamId }.toSet()` built once before the loop.
2. Inside the per-team loop, compute `val hasTask = teamId in taskTeamIds` (matching the loop variable name actually used) and keep passing it into `TeamNotificationInfo(hasTask, hasChat)`.
3. Verify `hasChat` still comes from `chatCountsById` per team — do not alter that lookup.
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: dashboard/team notification badges show the task indicator only for teams that actually have a task due in the window.
size budget: ~6 changed lines, 1 file
out of scope: no DAO or SQL changes; do not restructure the surrounding notification queries.

---

### 3. Expose the current user through RequestsUiState (roadmap 3+1)
context: `RequestsFragment` injects `UserSessionManager` (ui/teams/members/RequestsFragment.kt:26) only to resolve the current user for `RequestsAdapter.setUser`, while `RequestsViewModel.fetchMembers` already loads `userRepository.getUserModel()` (ui/teams/members/RequestsViewModel.kt:~130) and throws away everything except `user?.id` for the `isLeader` check. Carrying the user in state removes a second, redundant session read and a service injection from the fragment.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt` — touch only `RequestsUiState` and `RequestsViewModel.fetchMembers`. Leave `MembersUiState`, `MemberAction`, `MemberActionResult`, `respondToRequest` and every other member of this file alone.
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt` — remove the injection and the standalone user fetch. Leave `RequestsAdapter` and the rest of the fragment alone.
steps:
1. Add `val currentUser: UserEntity? = null` to `RequestsUiState` (keep it last so positional constructors stay valid), and populate it with `user` inside `fetchMembers`.
2. In the fragment, delete `@Inject lateinit var userSessionManager: UserSessionManager` and the `UserSessionManager` import.
3. Replace `private lateinit var currentUser: UserEntity` plus the `onAttach` `UserEntity()` init and the `lifecycleScope.launch { currentUser = userSessionManager.getUserModel() ... }` block in `onViewCreated` with a single `uiState.currentUser?.let { (adapter as? RequestsAdapter)?.setUser(it) }` inside the existing `collectWhenStarted(viewModel.uiState)` collector.
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: the join-requests list still resolves accept/decline permissions against the real current user (leader actions enabled exactly as before).
size budget: ~15 changed lines, 2 files
out of scope: do not touch `MembersViewModel`/`MembersUiState` in the same file; no adapter changes.

---

### 4. Move the examiner user lookup into HealthExaminationState (roadmap 3+1)
context: `HealthExaminationActivity` reads `currentUser = userSessionManager.getUserModel()` (ui/health/HealthExaminationActivity.kt:78) even though `HealthExaminationViewModel` already injects `userRepository`; the activity then mixes the *patient* (`state.user`) and the *examiner* (`currentUser`, used around lines 251 and 256 for `isSelfExamination` and `createdBy`). Putting the examiner in state removes the session-manager injection and keeps both identities in one observable place.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` — the `HealthExaminationState` data class and the state-loading coroutine only.
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt` — injection removal and the state consumer.
steps:
1. Add `val currentUser: UserEntity? = null` to `HealthExaminationState`.
2. In the viewmodel's load path, fetch `userRepository.getUserModel()` alongside the existing patient load and set `currentUser` on the emitted state.
3. In the activity, delete the `UserSessionManager` injection and the `lifecycleScope.launch` block that populates the field; keep a plain `private var currentUser: UserEntity? = null` assigned from the collected state.
4. Leave lines using `state.user` (patient) untouched.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: `isSelfExamination` is still true only when the patient id equals the signed-in examiner id, and new examinations still get `createdBy` set to the examiner.
size budget: ~20 changed lines, 2 files
out of scope: no changes to `HealthExaminationAdapter` or the patient-loading repository calls.

---

### 5. Resolve the current user inside AddResourceViewModel (roadmap 3+1)
context: `AddResourceFragment` injects `UserSessionManager` (ui/resources/AddResourceFragment.kt:65) just to call `userSessionManager.getUserModel()` at ~line 264 for `showAlert(path, userModel.id, userModel.name, ...)`. `AddResourceViewModel` currently injects only `PersonalsRepository`, so the data call lives in the fragment instead of behind the viewmodel.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModel.kt` — add one dependency and one accessor.
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceFragment.kt` — remove the injection and swap the call.
- Do not touch `AddResourceActivity.kt` (owned by an open PR).
steps:
1. Add `private val userRepository: UserRepository` to the `AddResourceViewModel` constructor.
2. Add `suspend fun currentUser(): UserEntity? = userRepository.getUserModel()`.
3. In the fragment, delete `@Inject lateinit var userSessionManager` and its import; replace `userSessionManager.getUserModel() ?: return@launch` at ~line 264 with `viewModel.currentUser() ?: return@launch`.
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: adding a resource still completes the title-existence alert and save path with the correct user id/name attached.
size budget: ~10 changed lines, 2 files
out of scope: no changes to `showAlert` or the upload/save flow; no state class additions.

---

### 6. Stop threading ConfigurationsRepository into dialog helpers (roadmap 1+8)
context: `DialogUtils.getUpdateDialog` (utils/DialogUtils.kt, ~line 187) and `DialogUtils.startDownloadUpdate` (~line 206) accept a `ConfigurationsRepository` parameter purely to call `configurationsRepository.checkCheckSum(path)` inside a UI helper. Both callers — `SyncActivity` (ui/sync/SyncActivity.kt:763) and `AutoSyncWorker` (services/AutoSyncWorker.kt:93) — already hold the repository themselves, so the checksum concern can be injected as a function and the repository import removed from the UI utils file.
files:
- `app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt` — signatures of `getUpdateDialog` and `startDownloadUpdate` only.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` — the call site at ~line 763.
- `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt` — the call site at ~line 93.
steps:
1. In `DialogUtils`, change `getUpdateDialog(context, info, progressDialog, scope, configurationsRepository)` to take `checkCheckSum: suspend (String) -> Boolean` as the last parameter.
2. Change `startDownloadUpdate(context, path, progressDialog, scope, configurationsRepository)` the same way, and pass the lambda through where the dialog delegates to `startDownloadUpdate`.
3. Replace the `configurationsRepository.checkCheckSum(path)` invocation inside the helper with `checkCheckSum(path)`; remove the `ConfigurationsRepository` import if unused.
4. At both call sites pass `configurationsRepository::checkCheckSum` — in `SyncActivity` the injected field, in `AutoSyncWorker` the constructor-injected one.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: the update dialog still verifies the APK checksum before downloading when a newer build is offered.
size budget: ~25 changed lines, 3 files
out of scope: no behavior change to the download/update flow; no other `DialogUtils` helpers.

---

### 7. Carve the achievement surface out of UserRepository (roadmap 4+1)
context: `UserRepository` declares 45 members (repository/UserRepository.kt), and `AchievementUploader` (services/upload/AchievementUploader.kt:26-31) uses only `getAchievementsForUpload()` and `markAchievementUploaded(id, rev)`. The codebase already has the interface-segregation precedent — `TeamsFinancesRepository`/`TeamsMembersRepository`/`TeamsNotificationsRepository` are narrow interfaces implemented by one class and bound via `@Binds`. Apply the same pattern to the achievement block (`UserRepository.kt` ~lines 98-117) so upload code depends on the narrow contract.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/UserAchievementsRepository.kt` — NEW interface file.
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` — remove the moved members and declare `interface UserRepository : UserAchievementsRepository`.
- `app/src/main/java/org/ole/planet/myplanet/di/UserAchievementsRepositoryModule.kt` — NEW `@Module`/`@Provides` file.
- `app/src/main/java/org/ole/planet/myplanet/services/upload/AchievementUploader.kt` — constructor type swap only.
- Do NOT touch `UserRepositoryImpl.kt` or `di/RepositoryModule.kt` — open PRs own them.
steps:
1. Create `UserAchievementsRepository` containing `val achievementUpdates: Flow<Unit>` and the five achievement methods exactly as declared today: `initializeAchievement`, `updateAchievement` (copy the full parameter list including the `resumeFileName` default), `getAchievementData`, `getAchievementsForUpload`, `markAchievementUploaded`, with the needed `Achievement`, `AchievementData`, `JsonArray`, `JsonObject`, `Flow` imports.
2. Make `UserRepository` extend it and delete those six members from `UserRepository` — `UserRepositoryImpl`'s existing `override`s satisfy the super-interface with no impl change.
3. Create `UserAchievementsRepositoryModule` with `@Module @InstallIn(SingletonComponent::class)` and `@Provides fun provideUserAchievementsRepository(repo: UserRepository): UserAchievementsRepository = repo`.
4. In `AchievementUploader`, change the injected `userRepository: UserRepository` field to `UserAchievementsRepository` type (rename the field accordingly) and keep the two calls unchanged.
5. Leave `AchievementViewModel` on `UserRepository` — a sibling task owns that file.
acceptance: `./gradlew testDefaultDebugUnitTest` green and `assembleDefaultDebug` compiles (Hilt must resolve `UserAchievementsRepository` for the uploader). User-visible: achievement upload during sync is unchanged.
size budget: ~80 changed lines, 4 files (2 new)
out of scope: no changes to `UserRepositoryImpl`, `RepositoryModule`, or `AchievementViewModel`; no other slices of `UserRepository` in this task.

---

### 8. Resolve the viewer user inside ResourceViewerViewModel (roadmap 3+4)
context: `ResourceViewerActivity` (ui/viewer/ResourceViewerActivity.kt:25-29) and `WebViewActivity` (ui/viewer/WebViewActivity.kt, ~lines 45-50) each inject `UserRepository` solely to pass into `ResourcesExitCoordinator`, which calls `userRepository.getUserModel()` and forwards the raw `userId` into `ResourceViewerViewModel.shouldShowResourceRatingDialog(userId, resourceId)` / `setRatingPrompted(userId, resourceId)`. The viewmodel can own that lookup — it already sits next to `ratingsRepository` — which removes an injection from both activities and the coordinator.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt` — add `UserRepository` to the constructor; change `shouldShowResourceRatingDialog`, `isRatingPrompted` (internal), and `setRatingPrompted` to take only `resourceId` and resolve `userRepository.getUserModel()?.id` inside.
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourcesExitCoordinator.kt` — drop the `userRepository` constructor parameter and the `getUserModel()` call; call the new viewmodel signatures.
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerActivity.kt` — remove the `userRepository` injection and the coordinator argument.
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt` — same removal.
steps:
1. Add `private val userRepository: UserRepository` to `ResourceViewerViewModel`.
2. Change `shouldShowResourceRatingDialog(userId, resourceId)` to `shouldShowResourceRatingDialog(resourceId)`: resolve `val userId = userRepository.getUserModel()?.id ?: return false` first (no user → no prompt), then run the existing prompted/hasRated checks.
3. Change `setRatingPrompted(userId, resourceId)` to resolve the user the same way and no-op on null.
4. Update `ResourcesExitCoordinator` to drop `userRepository` from its constructor and call the new signatures; remove its `UserRepository` import.
5. In both activities, delete the `@Inject lateinit var userRepository` fields, the `UserRepository` imports, and the second constructor argument passed to `ResourcesExitCoordinator`.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: the resource-rating dialog still appears on exit only for signed-in users who have not rated, exactly as before.
size budget: ~35 changed lines, 4 files
out of scope: no changes to the rating UI itself, `RatingsRepository`, or playback-progress helpers in the viewmodel.

---

### 9. Collapse the repeated upload-await blocks in UserDataWorker (roadmap 5+8)
context: The `UPLOAD_TYPE_BULK` branch of `UserDataWorker` (services/UserDataWorker.kt, ~lines 45-105) repeats the same `CompletableDeferred` + `OnSuccessListener` + `withTimeoutOrNull(30000L)` wrapper five times (`uploadUserActivities`, `uploadExamResult`, `uploadResource`, `uploadSubmitPhotos`, `uploadActivities`), plus one same-shape block for `uploadToShelfService.uploadUserData` whose callback is `() -> Unit`. A single helper turns ~50 lines of copy-paste into one definition and consistent timeout handling.
files:
- `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt` — only the `UPLOAD_TYPE_BULK` branch and a new private helper. Leave the `UPLOAD_TYPE_LOGIN` branch's `CompletableDeferred<String>` block (it returns a success message) untouched.
steps:
1. Add `private suspend fun awaitUploadCompletion(start: (onComplete: () -> Unit) -> Unit)`: inside `runCatching`, create `CompletableDeferred<Unit>`, invoke `start { deferred.complete(Unit) }`, then `withTimeoutOrNull(30000L) { deferred.await() }`.
2. Replace the `uploadUserActivities`, `uploadExamResult`, `uploadSubmitPhotos`, and `uploadActivities` blocks with `awaitUploadCompletion { cb -> uploadManager.uploadX(object : OnSuccessListener { override fun onSuccess(success: String?) { cb() } }) }`.
3. Replace the `uploadResource` block the same way and keep `uploadManager.uploadTeams()` immediately after its await, as today.
4. Replace the `uploadToShelfService.uploadUserData` block with `awaitUploadCompletion { cb -> uploadToShelfService.uploadUserData { uploadToShelfService.uploadHealth(); cb() } }` — note the `uploadHealth()` call stays inside the inner callback.
5. Remove now-unused imports (`OnSuccessListener` stays if the login path still uses it — check before removing `CompletableDeferred`, `withTimeoutOrNull` imports).
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: bulk upload still runs every pipeline step with the same 30s-per-step timeout and same ordering.
size budget: ~50 changed lines, 1 file
out of scope: do not merge the login branch's `CompletableDeferred<String>` (different result type); no timeout value changes.

---

### 10. Introduce DashboardElementViewModel for session and activity calls (roadmap 3+1)
context: `DashboardElementActivity` injects `ActivitiesRepository` directly (ui/dashboard/DashboardElementActivity.kt:35) and calls `profileDbHandler.getUserModel()` / `profileDbHandler.logoutAsync()` (inherited from `SyncActivity`) inside `lifecycleScope.launch` blocks around lines 107-125. The abstract dashboard activity therefore reaches into two data/session services with no viewmodel — the same pattern the per-feature screens have already moved away from.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt` — remove the `activitiesRepository` injection and reroute the service calls.
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt` — NEW `@HiltViewModel` file in the same package.
- Do NOT touch `SyncActivity.kt` — `profileDbHandler` is declared there and still used by `SyncActivity` itself (lines 402, 650); a different task in this document also edits that file.
steps:
1. Create `DashboardElementViewModel` injecting `UserRepository`, `ActivitiesRepository`, `UserSessionManager`, and `DispatcherProvider`; expose `suspend fun currentUser() = userRepository.getUserModel()`, `fun recordUserChallengeAction(userId: String?)` which launches `viewModelScope.launch(dispatcherProvider.io) { activitiesRepository.recordSyncUserChallengeAction("$userId") }`, and `suspend fun logout() = userSessionManager.logoutAsync()`.
2. In the activity's server-connect block (~line 107), replace `profileDbHandler.getUserModel()` with `viewModel.currentUser()` and `activitiesRepository.recordSyncUserChallengeAction("${userModel?.id}")` with `viewModel.recordUserChallengeAction(userModel?.id)`; keep the guest-id check and `checkMinApk(...)` call exactly where they are.
3. In `logout()`, replace `profileDbHandler.logoutAsync()` with `viewModel.logout()`; leave `SecurePrefs.clearCredentials`, `prefData` calls, `NotificationUtils.cancelAll`, and the login intent untouched.
4. Delete the `@Inject lateinit var activitiesRepository` field and its import; add `private val viewModel: DashboardElementViewModel by viewModels()`.
5. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible: the "user challenged" sync activity is still recorded when connecting to a server, guests still get the guest dialog, and logout still clears session state and returns to the login screen.
size budget: ~70 changed lines, 2 files (1 new)
out of scope: no Compose work, no navigation refactor; do not remove or rename `profileDbHandler` in `SyncActivity`.

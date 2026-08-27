# myPlanet repository-boundary work orders

2026-08-27 · base commit `89fd72c251df68ed01094091d4de7ba7a2571ebe` · open PRs checked: 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

Files touched by any of those open PRs are off-limits. The collision set used for planning contained 418 paths.

---

### 1. remove unused UserSessionManager injection from SubmissionsFragment (roadmap 4+9)

context: `SubmissionsFragment.kt:34` declares `@Inject lateinit var userSessionManager: UserSessionManager` but a full-text search of the file shows the field is never read. Keeping it adds a service-layer dependency to a UI class for no reason, forces the fragment to depend on an Android-bound session manager, and makes the class harder to reuse as the architecture moves toward platform-free ViewModels. Removing it tightens the fragment's boundary and removes a direct leak to `services.UserSessionManager`.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt` (delete the unused field and import)
- neighbors to leave alone: `SubmissionViewModel.kt`, `SubmissionsAdapter.kt`, `SubmissionUiModel.kt`, `UserSessionManager.kt`

steps:
1. Delete the `@Inject lateinit var userSessionManager: UserSessionManager` field.
2. Delete the `import org.ole.planet.myplanet.services.UserSessionManager` line.
3. Confirm there are no remaining references to `userSessionManager` in the file.
4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the Submissions screen still renders submissions and the user-visible behavior is unchanged.

size budget: ~4 changed lines, 1 file

out of scope: do not modify `SubmissionViewModel`, `SubmissionsAdapter`, `SubmissionUiModel`, or `UserSessionManager` itself; do not add new dependencies.

---

### 2. replace UserSessionManager with UserRepository in RequestsViewModel (roadmap 1+4+9)

context: `RequestsViewModel.kt:26` injects `UserSessionManager` only to call `userSessionManager.getUserModel()` at line 38, where the result is used to check team-leader status. `UserRepository` already exposes the same `suspend fun getUserModel(): UserEntity?` at `UserRepository.kt:73`. Routing the current user through the repository makes the ViewModel depend on the data layer instead of a service class, advances the platform-free core goal, and keeps user identity in one place.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`
- neighbors to leave alone: `TeamsMembersRepository.kt`, `TeamsMembersRepositoryImpl.kt`, `UserRepositoryImpl.kt`, `UserSessionManager.kt`

steps:
1. Replace `private val userSessionManager: UserSessionManager` with `private val userRepository: UserRepository` in the constructor.
2. Replace `val user = userSessionManager.getUserModel()` with `val user = userRepository.getUserModel()`.
3. Remove the `UserSessionManager` import and add `import org.ole.planet.myplanet.repository.UserRepository` if needed.
4. Confirm the constructor no longer references `UserSessionManager`.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the team requests screen still shows the correct request list, joined-member count, and leader status for the current user.

size budget: ~6 changed lines, 1 file

out of scope: do not modify `TeamsMembersRepository`, `UserRepositoryImpl` (open PR), or `UserSessionManager`.

---

### 3. replace UserSessionManager with UserRepository in feedback ViewModels (roadmap 1+4+9)

context: `FeedbackListViewModel.kt:20` and `FeedbackComposerViewModel.kt:20` inject `UserSessionManager` only to fetch the current user. `FeedbackListViewModel.kt:35` passes the user object to `feedbackRepository.getFeedback(user)`, and `FeedbackComposerViewModel.kt:38` reads `userSessionManager.getUserModel()?.name`. Both the full user entity and the user's name are available from the existing `UserRepository.getUserModel()` method, so the feedback feature can stop leaking across the service boundary and depend only on the repository layer.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModel.kt`
- neighbors to leave alone: `FeedbackRepository.kt`, `FeedbackRepositoryImpl.kt`, `FeedbackFragment.kt`, `UserSessionManager.kt`

steps:
1. In both ViewModels, swap `private val userSessionManager: UserSessionManager` for `private val userRepository: UserRepository`.
2. In `FeedbackListViewModel` use `userRepository.getUserModel()` when calling `feedbackRepository.getFeedback(...)`.
3. In `FeedbackComposerViewModel` use `userRepository.getUserModel()?.name ?: ""` when building feedback.
4. Remove the `UserSessionManager` import from both files and add `import org.ole.planet.myplanet.repository.UserRepository` if needed.
5. Confirm neither file still imports or injects `UserSessionManager`.
6. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the feedback list still filters by the current user and the composer still attributes feedback to the current user's name.

size budget: ~14 changed lines, 2 files

out of scope: do not change `FeedbackRepository` (open PR), any feedback fragment/activity, or `UserSessionManager`.

---

### 4. replace UserSessionManager with UserRepository in PersonalsViewModel (roadmap 1+4+9)

context: `PersonalsViewModel.kt:29` injects `UserSessionManager` and `PersonalsViewModel.kt:33` calls `userSessionManager.getUserModel()` inside the `personals` StateFlow builder. The `UserRepository.getUserModel()` suspend function returns the same `UserEntity?`, so the ViewModel can pull user identity from the repository layer instead of the session service. This removes another service-to-UI leak and keeps user identity behind the repository boundary.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`
- neighbors to leave alone: `PersonalsRepository.kt`, `PersonalsRepositoryImpl.kt`, `PersonalsFragment.kt`, `UserSessionManager.kt`

steps:
1. Replace `private val userSessionManager: UserSessionManager` with `private val userRepository: UserRepository`.
2. Replace `val user = userSessionManager.getUserModel()` with `val user = userRepository.getUserModel()`.
3. Remove the `UserSessionManager` import and add `import org.ole.planet.myplanet.repository.UserRepository` if needed.
4. Confirm the `personals` StateFlow still falls back to an empty list when the user is null.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the Personals screen still shows resources for the currently logged-in user.

size budget: ~6 changed lines, 1 file

out of scope: do not modify `PersonalsRepository`, `UserRepositoryImpl` (open PR), or `UserSessionManager`.

---

### 5. replace UserSessionManager with UserRepository in ActivitiesViewModel (roadmap 1+4+9)

context: `ActivitiesViewModel.kt:18` injects `UserSessionManager` and `ActivitiesViewModel.kt:23` reads `userSessionManager.getUserModel()?.name` to scope offline login records. The `UserRepository.getUserModel()` method already returns the current user entity, so the ViewModel should request it from the repository layer rather than through a service class. This keeps offline-activity queries scoped to the repository that owns user data.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`
- neighbors to leave alone: `ActivitiesRepository.kt`, `ActivitiesRepositoryImpl.kt`, `DashboardActivity.kt`, `UserSessionManager.kt`

steps:
1. Replace `private val userSessionManager: UserSessionManager` with `private val userRepository: UserRepository`.
2. Replace `val userName = userSessionManager.getUserModel()?.name ?: return@flow` with `val userName = userRepository.getUserModel()?.name ?: return@flow`.
3. Remove the `UserSessionManager` import and add `import org.ole.planet.myplanet.repository.UserRepository` if needed.
4. Confirm the flow still emits an empty list when the current user name is null.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the Activities dashboard card still shows offline login records for the current user.

size budget: ~6 changed lines, 1 file

out of scope: do not modify `ActivitiesRepository` (open PR), `DashboardActivity.kt`, or `UserSessionManager`.

---

### 6. remove UserSessionManager from UserProfileViewModel (roadmap 4+9)

context: `UserProfileViewModel.kt:27` injects `UserSessionManager` only to read the current user's name at line 130 and line 148. The same name is available from `UserRepository.getUserModel()?.name`. The activity-type constant `KEY_RESOURCE_OPEN` still appears at lines 132 and 134, but it can be represented locally in the ViewModel so that the service class import is no longer needed. This removes the last service dependency from the profile ViewModel.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt`
- neighbors to leave alone: `UserProfileFragment.kt` (open PR), `UserRepositoryImpl.kt` (open PR), `ActivitiesRepository.kt` (open PR), `UserSessionManager.kt`

steps:
1. Replace `private val userSessionManager: UserSessionManager` with `private val userRepository: UserRepository`.
2. Add a `private const val KEY_RESOURCE_OPEN = "visit"` companion constant (or keep the same string literal in the two call sites).
3. Replace `userSessionManager.getUserModel()?.name` with `userRepository.getUserModel()?.name` in the `init` block and in `getOfflineVisits()`.
4. Remove the `UserSessionManager` import and add `import org.ole.planet.myplanet.repository.UserRepository` if needed.
5. Confirm no `UserSessionManager` reference remains in the file.
6. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the profile screen still displays the current user's most-opened resource and resource-open count, and offline visits still reflect the current user.

size budget: ~9 changed lines, 1 file

out of scope: do not move `KEY_RESOURCE_OPEN` to `ActivitiesRepository` (open PR) and do not modify `UserRepositoryImpl` (open PR).

---

### 7. replace SharedPrefManager user-id lookup with UserRepository in LifeViewModel (roadmap 1+4+9)

context: `LifeViewModel.kt:25` injects `SharedPrefManager` and `LifeViewModel.kt:35` calls `sharedPrefManager.getUserId().ifEmpty { userRepository.getUserModel()?.id }`. Because `UserRepository.getUserModel()` already returns the current user, the ViewModel can drop `SharedPrefManager` entirely and always derive the active user id from the repository layer. This prevents the UI layer from reading raw shared preferences and keeps user identity behind the repository boundary.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`
- neighbors to leave alone: `LifeRepository.kt`, `LifeRepositoryImpl.kt`, `LifeFragment.kt`, `MyLife.kt`, `SharedPrefManager.kt`

steps:
1. Remove `private val sharedPrefManager: SharedPrefManager` from the constructor.
2. Replace `sharedPrefManager.getUserId().ifEmpty { userRepository.getUserModel()?.id }` with `userRepository.getUserModel()?.id`.
3. Remove the `SharedPrefManager` import and ensure `UserRepository` is already imported.
4. Confirm the `loadMyLifeList()` function still passes a nullable user id to `LifeRepository`.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the My Life dashboard card still shows the correct items for the current user, including seeded defaults when the table is empty.

size budget: ~5 changed lines, 1 file

out of scope: do not remove the `@ApplicationContext` or the `context::getString` label resolution; that `R` dependency is left for a later KMP pass.

---

### 8. replace service lookups with UserRepository in DiagnosticsRepositoryImpl (roadmap 1+4+9)

context: `DiagnosticsRepositoryImpl.kt:18` injects both `UserSessionManager` and `SharedPrefManager`. `saveLogToRoom` and `saveLogsToRoom` call `userSessionManager.getUserModel()` for the user id, while `buildApkLog` reads `sharedPrefManager.getParentCode()` and `getPlanetCode()`. The same identifiers are properties of `UserEntity` (`id`, `parentCode`, and `planetCode`), and `UserRepository.getUserModel()` returns that entity. Routing through the repository removes two service dependencies from this data class and keeps crash/apk-log construction inside the repository boundary.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
- neighbors to leave alone: `ApkLog.kt`, `ApkLogDao.kt`, `CrashLogStore.kt`, `UserRepositoryImpl.kt` (open PR), `UserSessionManager.kt`, `SharedPrefManager.kt`

steps:
1. Replace `private val userSessionManager: UserSessionManager` and `private val sharedPrefManager: SharedPrefManager` with `private val userRepository: UserRepository` in the constructor.
2. In `saveLogToRoom` and `saveLogsToRoom`, call `userRepository.getUserModel()` and use `user?.id`, `user?.parentCode`, and `user?.planetCode`.
3. Remove or refactor `buildApkLog` so it no longer takes a `SharedPrefManager`.
4. Remove the `UserSessionManager` and `SharedPrefManager` imports and add `import org.ole.planet.myplanet.repository.UserRepository` if needed.
5. Confirm `@ApplicationContext` stays because `VersionUtils.getVersionName(context)` is still needed.
6. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the crash/apk log records still contain the correct `userId`, `parentCode`, and `createdOn` values for the current user.

size budget: ~22 changed lines, 1 file

out of scope: do not modify `ApkLogDao`, `UserRepositoryImpl` (open PR), `SharedPrefManager`, or `VersionUtils`.

---

### 9. move community and settings shared-pref access behind ConfigurationsRepository (roadmap 1+4+9)

context: `CommunityTabViewModel.kt:24` injects `SharedPrefManager` to read `getParentCode()` and `getCommunityName()`, `LeadersViewModel.kt:17` injects it to read `getCommunityLeaders()` and then parses JSON with `UserEntity.parseLeadersJson`, and `SettingsViewModel.kt:22` injects it only to call `clearPreferences()`. `ConfigurationsRepository` is already the configuration boundary and has `SharedPrefManager` injected, so it should own these reads. This consolidates cross-feature shared-pref leaks behind one repository interface.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt`

steps:
1. Add `fun getParentCode(): String`, `fun getCommunityName(): String`, `fun clearPreferences()`, and `suspend fun getCommunityLeaders(): List<UserEntity>` to `ConfigurationsRepository`.
2. Implement them in `ConfigurationsRepositoryImpl` by delegating to the existing `SharedPrefManager` methods; run `UserEntity.parseLeadersJson` on `dispatcherProvider.io`.
3. In `CommunityTabViewModel`, replace `sharedPrefManager.getParentCode()`/`getCommunityName()` with the new repository methods and `userSessionManager.getUserModel()` with `userRepository.getUserModel()`; remove `SharedPrefManager` and `UserSessionManager` from the constructor.
4. In `LeadersViewModel`, replace the shared-pref read and JSON parse with `configurationsRepository.getCommunityLeaders()` and remove `SharedPrefManager` and `DispatcherProvider` from the constructor.
5. In `SettingsViewModel`, replace `sharedPrefManager.clearPreferences()` with `configurationsRepository.clearPreferences()` and remove `SharedPrefManager` from the constructor.
6. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; the Community tab still shows the correct parent code, community name, planet type, and planet code; the Leaders list still renders community leaders; and Settings > Clear Data still clears both the database and shared preferences.

size budget: ~45 changed lines, 5 files

out of scope: do not modify `UserRepositoryImpl` (open PR), `CommunityRepository` (open PR), or `SharedPrefManager`.

---

### 10. add id-only notification existence query and use it for mark/delete (roadmap 1+7)

context: `NotificationsRepositoryImpl.kt:116` and `NotificationsRepositoryImpl.kt:406` call `notificationDao.getByIds(ids.toList())` just to map each result to `it.id` before updating or deleting. `getByIds` loads every column for every matched row only to check existence and return the id. A new `NotificationDao` query that selects only `id` WHERE `id IN (:ids)` avoids materializing full `AppNotification` objects, reduces memory churn, and keeps the data layer responsible for the smallest possible read. This is a small Room/DAO optimization with measurable allocation savings when many notifications are selected.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- neighbors to leave alone: `NotificationsViewModel.kt`, `Notification.kt`, `AppNotification.kt`, `NotificationsRepository.kt`

steps:
1. Add `@Query("SELECT id FROM notifications WHERE id IN (:ids)") suspend fun getExistingIds(ids: List<String>): List<String>` to `NotificationDao`.
2. In `NotificationsRepositoryImpl.markNotificationsAsRead`, replace `notificationDao.getByIds(notificationIds.toList()).map { it.id }` with `notificationDao.getExistingIds(notificationIds.toList())`.
3. In `NotificationsRepositoryImpl.deleteNotifications`, replace `notificationDao.getByIds(ids.toList()).map { it.id }` with `notificationDao.getExistingIds(ids.toList())`.
4. Leave `NotificationDao.getByIds` in place because `bulkInsertFromSync` still needs full `AppNotification` objects.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` is green; marking notifications as read or deleting selected notifications still affects exactly the same rows, and the Notifications screen still updates correctly.

size budget: ~9 changed lines, 2 files

out of scope: do not replace `getByIds` usages in `bulkInsertFromSync` that need full `AppNotification` objects, and do not change the notifications table schema or `NotificationsViewModel`.

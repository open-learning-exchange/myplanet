# myPlanet refactor round — repository boundaries

date: 2026-11-10 · base commit: `2093a62` (`origin/master`) · open PRs checked (35): #18555 #18554 #18551 #18540 #18539 #18455 #18076 #18045 #18029 #17832 #17811 #17694 #16594 #15951 #15825 #15824 #15820 #15808 #15559 #15267 #15266 #15226 #15108 #14883 #14650 #14427 #13928 #13848 #13657 #13604 #13355 #13287 #10993 #8175 #4075

How R3 was applied: every open PR head was fetched and diffed against its merge base with
`master`. Together they touch 1317 paths, including nearly every `*RepositoryImpl.kt`,
`AppDatabase.kt`, `RepositoryModule.kt`, `RoomModule.kt`, `MainApplication.kt`,
`DashboardActivity.kt` and most DAO files. All of those are off-limits, so every task below
stays inside files that no open PR touches. None of the open PRs is labelled `review ready` or
`merge`. The plan avoids every open PR's files anyway, not just those two labels. No file appears
in more than one task, and the tasks can merge in any order.

Focus of this round: repository boundaries between layers, cross-feature data leaks, moving data
work out of UI code, DAO/Room work, and cleaner repository ↔ ViewModel relationships.

Shared facts used by several tasks (all verified at `2093a62`):
- `repository/UserReadRepository.kt` declares `getUserById`, `getUsersByIds`, `getUserModel`.
  `repository/UserRepository.kt:23` is `interface UserRepository : UserAchievementsRepository, UserReadRepository`.
- `di/RepositoryModule.kt:229` already binds `UserReadRepository` to `UserRepositoryImpl`, so
  Hilt can inject it into any ViewModel without DI changes.
- Because `UserRepository` extends `UserReadRepository`, existing tests that pass a
  `mockk<UserRepository>()` into a constructor that now takes `UserReadRepository` still compile.

---

### 1. Narrow home-screen ViewModels from UserRepository to UserReadRepository (roadmap 1+3, moves 9)
context: four home-area ViewModels inject the full `UserRepository` (38 members of its own plus 8
inherited: login, sync, profile writes, guest handling) but only ever call `getUserModel()`. That gives a read-only
screen write access to the user store, and it ties these ViewModels to the largest repository
surface in the app, which a platform-free core will have to split anyway. Evidence:
`ui/personals/PersonalsViewModel.kt:31` (used only at :35), `ui/dashboard/DashboardElementViewModel.kt:13`
(only :19), `ui/dashboard/ActivitiesViewModel.kt:22` (only :39), `ui/calendar/CalendarViewModel.kt:24` (only :42).
files: `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`.
Leave alone: their `*ViewModelTest.kt` files (they still compile, see shared facts),
`di/RepositoryModule.kt` and `UserRepositoryImpl.kt` (both owned by open PRs), and the
`ActivitiesRepository`/`TeamsRepository`/`UserSessionManager` parameters in these constructors.
steps:
1. In each of the four files, change the constructor parameter type `userRepository: UserRepository`
   to `userRepository: UserReadRepository`. Keep the parameter name so named-argument call sites in tests still work.
2. Replace `import org.ole.planet.myplanet.repository.UserRepository` with
   `import org.ole.planet.myplanet.repository.UserReadRepository`. Keep imports lexicographically sorted.
3. Confirm that no other `userRepository.` call exists in these files (`grep -n "userRepository\." <file>`
   shows only `getUserModel`).
4. Run the four ViewModel test classes, then the full unit-test task.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*PersonalsViewModelTest" --tests "*DashboardElementViewModelTest" --tests "*ActivitiesViewModelTest" --tests "*CalendarViewModelTest"`
passes, then `./gradlew testDefaultDebugUnitTest` stays green and `./gradlew assembleDefaultDebug` builds
(Hilt graph resolves). On device: the Personals list, the dashboard activity tiles, the Calendar
events and the dashboard logout still show data for the logged-in user.
size budget: ~8 changed lines, 4 files.
out of scope: do not touch any other ViewModel (tasks 2, 8 and 10 own the others). Do not add
methods to `UserReadRepository`.

---

### 2. Narrow learning/resource ViewModels from UserRepository to UserReadRepository (roadmap 1+3, moves 9)
context: same leak as task 1, in the course and resource flows. Each of these injects the full
`UserRepository` but only reads the current user or one user by id:
`ui/courses/CoursesStepsViewModel.kt:49` (only :68 `getUserModel`), `ui/courses/TakeCourseViewModel.kt:42`
(only :117), `ui/resources/ResourcesViewModel.kt:31` (only :39), `ui/submissions/SubmissionDetailViewModel.kt:30`
(only :38 `getUserById`), `base/ResourcesAccessViewModel.kt:21` (only :49). Narrowing makes the
read-only contract explicit and stops screens that only read from also being able to write.
files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt`.
Leave alone: all their test files (task 6 rewrites `SubmissionDetailViewModelTest.kt`), and
`ui/viewer/ResourceViewerViewModel.kt` (it also reads `SharedPrefManager`; separate concern).
steps:
1. In each of the five files, change `userRepository: UserRepository` to `userRepository: UserReadRepository`
   and keep the parameter name.
2. Swap the import to `org.ole.planet.myplanet.repository.UserReadRepository`, keeping imports sorted.
3. `grep -n "userRepository\." <file>` must list only `getUserModel` / `getUserById` calls.
4. Run the affected test classes, then the full unit-test task.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CoursesStepsViewModelTest" --tests "*TakeCourseViewModelTest" --tests "*ResourcesViewModelTest" --tests "*SubmissionDetailViewModelTest" --tests "*ResourcesAccessViewModelTest"`
passes, `./gradlew testDefaultDebugUnitTest` stays green, `./gradlew assembleDefaultDebug` builds.
On device: opening a course step, taking a course, the resource library list, a submission
detail and opening an HTML resource all behave as before. A guest user still sees the guest gating.
size budget: ~10 changed lines, 5 files.
out of scope: no repository or DI changes. Do not touch task 1's ViewModels.

---

### 3. Remove Android Context and R.string from NotificationsViewModel (roadmap 3+6, moves 10)
context: `ui/notifications/NotificationsViewModel.kt:33` injects `@ApplicationContext context: Context`
only to call `context.getString(R.string.…)` while formatting rows (:298, :304-305, :320-323,
:347-349). A ViewModel that holds Context and `R.*` cannot move to a Compose Multiplatform screen,
and it ignores per-Activity locale overrides, because the application context's locale can differ
from the screen's. The unit test also has to pass `mockk<Context>(relaxed = true)` (`NotificationsViewModelTest.kt:49`),
so all formatted text in tests is an empty string.
files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
(`loadNotifications`, `formatNotification`, `formatTaskNotification`),
`app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt`
(call sites :79, :89, :179), `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`.
Leave alone: `NotificationsRepository*` and `NotificationsAdapter.kt`.
steps:
1. In `NotificationsViewModel.kt` add a top-level `data class NotificationStrings` with
   `storageRunningLow: String`, `storageAvailable: String`, `joinRequestPrefix: String` and three
   function-typed fields: `resourceCount: (Int) -> String`, `taskDue: (title: String, date: String) -> String`,
   `userRequestedToJoin: (user: String, team: String) -> String`.
2. Change `loadNotifications(userId, filter, isAdmin)` to
   `loadNotifications(userId, filter, isAdmin, strings: NotificationStrings)`. Pass `strings` into
   `formatNotification`/`formatTaskNotification` and replace every `context.getString(...)` with the
   matching field. Delete the `context` constructor parameter and the `Context`, `ApplicationContext`
   and `R` imports.
3. In `NotificationsFragment.kt` add one `private val notificationStrings by lazy { NotificationStrings(...) }`
   built from `getString(R.string.storage_running_low)`, `R.string.storage_available`,
   `R.string.join_request_prefix`, `R.string.resource_notification`, `R.string.task_notification`
   and `R.string.user_requested_to_join_team`. Pass it at all three `loadNotifications` calls.
4. In the test, drop the Context mock from the constructor. Pass a fake `NotificationStrings` with
   fixed values (e.g. `resourceCount = { "res:$it" }`) from `loadNotificationsWithFilter` and from
   the direct `loadNotifications` calls. Add one assertion that a `resource` row with message `"3"` renders as `"res:3"`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*NotificationsViewModelTest"` passes,
then `./gradlew testDefaultDebugUnitTest` stays green. `grep -n "android\.\|R\.string" NotificationsViewModel.kt` returns nothing.
On device: notifications show the same resource-count, storage, task-due and join-request texts in English and in Arabic.
size budget: ~90 changed lines, 3 files.
out of scope: do not change grouping or selection logic, `formatStorageNotification` /
`formatJoinRequestNotification` signatures, or the repository enrichment.

---

### 4. Stop LifeViewModel reading the My Life list twice per open (roadmap 1+3+7)
context: `ui/life/LifeViewModel.kt:38` calls `lifeRepository.getMyLifeByUserId(userId, defaults)`,
which runs a full list query and seeds defaults when empty. :41 then subscribes to
`observeMyLifeByUserId(userId)`, which immediately runs the same query again and emits the same
list. So every open costs two identical DB reads and two StateFlow emissions, and the ViewModel
has to coordinate two sources of truth. The repository already exposes the seed step on its own:
`repository/LifeRepository.kt` `seedMyLifeIfEmpty(userId, items)`.
files: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` (`loadMyLifeList`),
`app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`.
Leave alone: `repository/LifeRepositoryImpl.kt`, `repository/LifeRepository.kt`, `LifeCache.kt`
and `LifeRepositoryImplTest.kt` (open PRs touch the cache and its tests). Also leave
`updateVisibility` / `updateMyLifeListOrder` in the ViewModel.
steps:
1. In `loadMyLifeList`, replace the `getMyLifeByUserId(...)` call and the `_myLifeList.value = initialList`
   assignment with `lifeRepository.seedMyLifeIfEmpty(userId, LifeItemDefaults.forUser(userId, resolveLabel))`
   (ignore its return value). Keep the existing `observeMyLifeByUserId(userId).distinctUntilChanged().collect { … }` as the only writer of `_myLifeList`.
2. Keep `resolveUserId()` unchanged.
3. Update the test: replace `coEvery { lifeRepository.getMyLifeByUserId(...) }` stubs (lines ~54, 69, 103, 125, 146, 177, 191, 204)
   with `coEvery { lifeRepository.seedMyLifeIfEmpty(...) } returns emptyList()` plus an
   `observeMyLifeByUserId` flow stub where one is missing. Change the `coVerify` checks (lines ~60-61, 79-80, 93, 183, 197, 210)
   to assert `seedMyLifeIfEmpty` is called exactly once and `getMyLifeByUserId` never.
4. Keep the test that checks the captured defaults list (line ~69), now capturing from `seedMyLifeIfEmpty`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*LifeViewModelTest"` passes, then the full
`./gradlew testDefaultDebugUnitTest` stays green. On device: a fresh user opening "myLife" sees the
seven default tiles. Toggling visibility and drag-reordering still persist after leaving and reopening the screen.
size budget: ~6 changed lines in the ViewModel, ~40 in the test, 2 files.
out of scope: do not remove `getMyLifeByUserId` from the interface (the off-limits impl test uses it), and do not touch `BaseDashboardFragment`.

---

### 5. Move the blank-community filter from the server dialog into CommunityDao SQL (roadmap 1+7, moves 9)
context: `ui/sync/ServerDialogExtensions.kt:298-299` loads every community row through
`communityRepository.getAllSorted()` and then filters out blank names in UI code with
`android.text.TextUtils.isEmpty`. That filtering is data selection and belongs in SQL.
`CommunityDao.getAllSorted()` (`data/room/dao/CommunityDao.kt:12-13`) has exactly one consumer
chain (`CommunityRepositoryImpl.getAllSorted` → this dialog), so the predicate can move into the
query without changing any other screen. `Community.name` is a non-null `String` (`model/Community.kt:18`).
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt` (`getAllSorted`),
`app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` (`setupManualConfigEnabled`),
new `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CommunityDaoTest.kt`.
Leave alone: `CommunityRepositoryImpl.kt` and `CommunityRepository.kt` (open PRs), `model/Community.kt`, and `AppDatabase.kt` (no schema change, so no version bump).
steps:
1. Change the `@Query` on `getAllSorted` to `SELECT * FROM community WHERE name != '' ORDER BY weight ASC`
   and add a one-line KDoc saying rows with an empty name are never returned.
2. In `setupManualConfigEnabled` delete the `nonEmptyCommunities` filter and pass `communities` straight to the `ArrayAdapter`.
   Keep the `android.text.TextUtils` import because :93 still uses `TextUtils.equals`.
3. Add `CommunityDaoTest` (Robolectric `@RunWith(AndroidJUnit4::class)`, in-memory `AppDatabase` exactly as
   `DictionaryDaoTest.kt` builds it). Insert three communities (weights 5/"b", 0/"learning", 1/""). Assert
   `getAllSorted()` returns `["learning", "b"]` in that order, and that `replaceAll` replaces the previous rows.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CommunityDaoTest"` passes. The full
`./gradlew testDefaultDebugUnitTest` stays green. On device: Settings → server dialog → enable
manual config, and the community spinner lists the same named communities in the same order, with no blank entry.
size budget: ~5 changed lines in main, ~60-line new test, 3 files.
out of scope: no new DAO method, no repository changes, no tie-break ordering change.

---

### 6. Migrate the last free Mockito unit tests to MockK (roadmap 8)
context: the project convention is MockK (`docs/TESTING.md`, `CLAUDE.md` "MockK (not Mockito)").
Mockito is not declared in `app/build.gradle` or `gradle/libs.versions.toml`, so these tests compile
only against a transitive jar that could disappear in any dependency bump. Four tests that no open
PR touches still use it: `ui/submissions/SubmissionDetailViewModelTest.kt:16-17,35-36,47,94-96`,
`ui/courses/CoursesAdapterTest.kt:16-21,33-43,62-112` (also the deprecated `MockitoAnnotations.initMocks`),
`ui/resources/ResourcesAdapterTest.kt:11-14,29-45,67,85`, `ui/dashboard/DashboardSurveysAdapterTest.kt:15-17,34,73,88`.
files: `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapterTest.kt`.
Leave alone: `ui/submissions/SubmissionViewModelTest.kt` (owned by an open PR) and every main-source file.
steps:
1. `SubmissionDetailViewModelTest`: `mock(X::class.java)` → `mockk()`, and ``` `when`(…).thenReturn(…) ``` → `coEvery { … } returns …`.
   Keep the user mock typed `UserRepository`. It compiles whether or not task 2 has merged.
2. `CoursesAdapterTest` / `ResourcesAdapterTest`: drop `@Mock` + `MockitoAnnotations.initMocks(this)`. Create
   the observer/listener with `mockk(relaxed = true)` in `@Before`. Rewrite
   `verify(m, times(n)).f(eq(a), eq(b), any())` as `verify(exactly = n) { m.f(a, b, any()) }`.
3. `DashboardSurveysAdapterTest`: `mock(AlertDialog::class.java)` → `mockk(relaxed = true)`, and `never()` → `verify(exactly = 0)`.
4. Remove every `org.mockito.*` import from the four files and sort imports. `grep -rn "org.mockito" <these files>` must return nothing.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*SubmissionDetailViewModelTest" --tests "*CoursesAdapterTest" --tests "*ResourcesAdapterTest" --tests "*DashboardSurveysAdapterTest"`
passes with the same number of test cases as before, and the full `./gradlew testDefaultDebugUnitTest` stays green.
No user-visible change.
size budget: ~70 changed lines, 4 files.
out of scope: do not add or remove test cases, do not touch main sources, and do not touch the Gradle files.

---

### 7. Move the two Hilt modules declared outside di/ into one di/ module (roadmap 4)
context: two `@Module` classes sit in non-DI packages: `UserDataUploadSchedulerModule` at
`services/UserDataUploadScheduler.kt:67-75` and `DeviceNameModule` at `utils/DeviceNameProvider.kt:24-30`.
The bindings graph is therefore not discoverable from `di/`, and the `utils`/`services` files depend
on `dagger.*` annotations they otherwise don't need, which blocks moving them to a platform-free
module. `grep -rn "DeviceNameModule\|UserDataUploadSchedulerModule" app/src` finds no other references,
so no `@UninstallModules` will break.
files: `app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt`,
`app/src/main/java/org/ole/planet/myplanet/utils/DeviceNameProvider.kt`,
new `app/src/main/java/org/ole/planet/myplanet/di/PlatformBindingsModule.kt`.
Leave alone: `di/ServiceModule.kt`, `di/RepositoryModule.kt`, `di/NetworkModule.kt` (open PRs), and the
`UserDataUploadScheduler` / `DeviceNameProvider` interfaces and their `@Inject` implementations.
steps:
1. Create `di/PlatformBindingsModule.kt`: `@Module @InstallIn(SingletonComponent::class) abstract class PlatformBindingsModule`
   with the two existing `@Binds @Singleton` functions copied unchanged (`bindUserDataUploadScheduler(impl: WorkManagerUserDataUploadScheduler)`,
   `bindDeviceNameProvider(impl: SharedPrefDeviceNameProvider)`).
2. Delete `UserDataUploadSchedulerModule` from `UserDataUploadScheduler.kt` and `DeviceNameModule` from `DeviceNameProvider.kt`.
3. Remove the now-unused `dagger.Binds`, `dagger.Module`, `dagger.hilt.InstallIn` and `dagger.hilt.components.SingletonComponent`
   imports from both files. Keep `javax.inject.*` and `dagger.hilt.android.qualifiers.ApplicationContext`.
acceptance: `./gradlew assembleDefaultDebug` builds (Hilt validates the graph) and `./gradlew testDefaultDebugUnitTest`
stays green. On device: Settings → user upload (UserData upload) still enqueues and reports progress, and
submissions still carry the device name.
size budget: ~45 changed lines, 3 files.
out of scope: do not rename the interfaces or implementations, and do not touch the other di/ modules or `CLAUDE.md`.

---

### 8. Hoist community view ids and title into CommunityTabState (roadmap 3+6, moves 10)
context: the two community hosts compose data identifiers in the view layer from raw state fields:
`ui/community/CommunityTabFragment.kt:23` builds `"${state.planetCode}@${state.parentCode}"` and :27 picks
the title with `if (state.planetCode.isEmpty()) state.communityName else state.planetCode`.
`ui/community/HomeCommunityDialogFragment.kt:87` builds `"${state.communityName}@${state.parentCode}"`.
The id format is a Voices data contract and should not be repeated in Fragments. A Compose screen
should receive ready values. The shared ViewModel also injects the full `UserRepository`
(`CommunityTabViewModel.kt:24`) only for `getUserModel()` (:33).
files: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt` (`CommunityTabState`, constructor),
`app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModelTest.kt`.
Leave alone: `CommunityPagerAdapter.kt` (its `id` parameter stays a `String`) and `ConfigurationsRepository`.
steps:
1. Add three derived read-only properties in the body of `CommunityTabState`: `planetViewInId`
   (`"$planetCode@$parentCode"`), `communityViewInId` (`"$communityName@$parentCode"`) and `title`
   (`planetCode.ifEmpty { communityName }`).
2. Change the constructor parameter to `userRepository: UserReadRepository` and swap the import.
3. `CommunityTabFragment`: pass `state.planetViewInId` to `CommunityPagerAdapter` and set `binding.title.text = state.title`.
4. `HomeCommunityDialogFragment`: pass `state.communityViewInId` to `CommunityPagerAdapter`. Leave its own title line (:92) as is.
5. Test: assert the three new properties in both existing cases. With a null user, `title` must equal `"community_abc"`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CommunityTabViewModelTest"` passes, the full
`./gradlew testDefaultDebugUnitTest` stays green. On device: Community tab and the home-community dialog
show the same title, and their Voices tab lists the same posts as before.
size budget: ~25 changed lines, 4 files.
out of scope: do not change `CommunityPagerAdapter` or the Voices filtering, and no repository changes.

---

### 9. Move the saved-user lookup out of LoginActivity into LoginViewModel (roadmap 3, moves 9)
context: `ui/sync/LoginActivity.kt:668-682` (`saveUsers`) reads the current user in the Activity through
`profileDbHandler.getUserModel()` (:677), copies `userImage`/`name` out of it and passes them to
`loginViewModel.saveUsers(name, encryptedPassword, source, userProfile, userName)` (:680). The ViewModel
already holds `UserRepository` (`LoginViewModel.kt:23`), so the Activity is doing a data read on the
ViewModel's behalf. Keeping that read in the VM gives the saved-users list one owner.
files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` (`saveUsers` only),
`app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt` (`saveUsers`, :69-74),
`app/src/test/java/org/ole/planet/myplanet/ui/sync/LoginViewModelTest.kt` (test at :105-116).
Leave alone: the Activity-level `saveUsers(name, password, source)` signature (called from
`GuestLoginExtensions.kt:72` and `utils/AuthUtils.kt:23,53`), the `SecurePrefs.encryptString` step, and
`profileDbHandler.saveUserInfoPref` calls at :601 and :644.
steps:
1. Change `LoginViewModel.saveUsers` to `saveUsers(name: String?, encryptedPassword: String?, source: String)`.
   Inside the existing `viewModelScope.launch(dispatcherProvider.io)` block, read `val user = userRepository.getUserModel()`
   and call `userRepository.upsertSavedUser(name, encryptedPassword, source, user?.userImage ?: "", user?.name)`, then `loadSavedUsers()`.
2. In `LoginActivity.saveUsers`, delete the `profileDbHandler.getUserModel()` lookup and the two derived locals,
   and call `loginViewModel.saveUsers(name, encryptedPassword, source)`.
3. Update the test: stub `coEvery { userRepository.getUserModel() } returns null`, call `saveUsers("guest1", "encrypted", "guest")`
   and verify `upsertSavedUser("guest1", "encrypted", "guest", "", null)`. Add one case where the user model has `userImage = "img"`, `name = "n"` and verify they are forwarded.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*LoginViewModelTest"` passes, and the full suite stays green.
On device: log in as a member, log out, and the saved-user chip on the login screen shows the
avatar and name. A guest login still adds the guest chip.
size budget: ~25 changed lines, 3 files.
out of scope: no changes to `UserRepository`/`UserSessionManager`, and do not move password encryption.

---

### 10. Let ChatViewModel resolve the current chat user instead of ChatHistoryFragment reading prefs (roadmap 1+3, moves 9)
context: `ui/chat/ChatHistoryFragment.kt:135-138` (`refreshChatHistory`) reads the user id straight from
`sharedPrefManager.getUserId()` and passes it to `ChatViewModel.loadChatHistoryScreenData(userId)` (`ChatViewModel.kt:134`).
The ViewModel then does `userRepository.getUserById(userId)` (:187-191). That is exactly what
`UserReadRepository.getUserModel()` does (`UserRepositoryImpl.getUserModel` reads the same pref and
calls `userDao.getById`), so the session-identity lookup has leaked into the Fragment. The Fragment is
the only production caller of `loadChatHistoryScreenData`.
files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`loadChatHistoryScreenData`, `loadCurrentUser`),
`app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` (`refreshChatHistory`),
`app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`.
Leave alone: `fetchAiProviders(serverUrl, …)` and its signature (`ChatDetailFragment.kt:444`, owned by an
open PR, calls it), the `serverUrl` getter at `ChatHistoryFragment.kt:47-48` (so `sharedPrefManager` stays
injected), and `ChatViewModel.getUserById` (:296).
steps:
1. Change `loadChatHistoryScreenData(userId: String?)` to `loadChatHistoryScreenData()`. Replace
   `loadCurrentUser(userId)` with `userRepository.getUserModel()`, keeping the `cachedUser ?: … .also { cachedUser = it }` caching.
2. Delete the private `loadCurrentUser` helper.
3. In `refreshChatHistory()` call `sharedViewModel.loadChatHistoryScreenData()`.
4. In the test, replace `coEvery { userRepository.getUserById("user123") } returns …` stubs used by screen-data
   tests (:126, :232, :342, :562, :596) with `coEvery { userRepository.getUserModel() } returns …`. Drop the
   `"user123"`/`userId =` argument at every `loadChatHistoryScreenData` call. Change `coVerify` at :261 and :378 to `getUserModel`.
   Leave the proxy test at :507-515 untouched.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ChatViewModelTest"` passes, then the full suite
stays green. On device: open Chat history while logged in and the list, search and "share to team/enterprise"
targets appear as before. After logging out and in as another user, that user's history appears.
size budget: ~10 changed lines in main, ~30 in the test, 3 files.
out of scope: do not remove `SharedPrefManager` from the Fragment, do not touch `ChatDetailFragment`, and no repository changes.

---

Signed: claude code · opus 5.5 · low

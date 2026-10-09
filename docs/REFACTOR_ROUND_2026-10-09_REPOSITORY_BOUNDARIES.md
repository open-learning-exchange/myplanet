# myPlanet refactor round — repository boundaries, DAO tightening, ViewModel relationships

date: 2026-10-09 · base commit: `f18c50d` (master, "all: smoother importing (fixes #18089) (#18088)") · open PRs checked: 4075, 8175, 10993, 13287, 13355, 13415, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 15108, 15226, 15266, 15267, 15559, 15808, 15820, 15824, 15825, 15951, 16594, 16623, 17694, 17811, 17832, 18029, 18045, 18050, 18076

**Collision policy (stricter than R3).** Each PR head above was fetched and diffed against its merge-base with `master`. No task touches any file changed by **any** of the 32 open PRs, not just the ones from the last week. Those 32 PRs touch 280 source files under `app/src/main/java`, including:

- most `repository/*RepositoryImpl.kt`
- `AppDatabase.kt`, `RoomModule.kt`, `RepositoryModule.kt`
- `UserSessionManager.kt`, `SharedPrefManager.kt`
- every `values*/strings.xml` (PR 13415)

So no task adds a string resource, bumps the Room version, adds a DAO→repository binding, or edits a busy repository Impl. Each task lists those busy neighbours under "leave alone".

**Shared fact used by several tasks.** `repository/UserReadRepository.kt:5-9` is a narrow interface: `getUserById`, `getUsersByIds`, `getUserModel`. `UserRepository` extends it (`repository/UserRepository.kt:23`), and Hilt binds it at `di/RepositoryModule.kt:229`. `ui/feedback/FeedbackListViewModel.kt:20` already injects it. Because `UserRepository` is a subtype, existing tests that pass a `mockk<UserRepository>()` into a ViewModel still compile after the constructor parameter is narrowed.

---

### 1. Narrow `TeamDao.observeAll` to the rows its two consumers keep (roadmap 7 + 1, also 9)

**context:** `data/room/dao/TeamDao.kt:19` runs `SELECT * FROM teams` as a Flow. Its only consumers are:
- `TeamsRepositoryImpl.getMyTeamsFlow`, at `repository/TeamsRepositoryImpl.kt:208-228`
- `TeamsRepositoryImpl.getMyTeamDetailsFlow`, at `repository/TeamsRepositoryImpl.kt:296-319`

The `teams` table holds every membership, request, report, transaction and resourceLink document. Both consumers then throw away everything except two kinds of row:
- membership rows: `docType == "membership"` and `!isDeletePending`
- root-team rows: `isRootTeam()`, which is `teamId.isNullOrBlank()` at `:1398`, and `!isDeletePending`

So every write to `teams` rebuilds the whole table as `MyTeam` objects for the dashboard and the team lists.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (`observeAll`, line 19)
- change `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt` (in-memory Room test; add one case)
- leave alone: `repository/TeamsRepositoryImpl.kt` and `repository/TeamsRepositoryImplTest.kt` (open PRs own them; the test mocks `observeAll` at `:293`, `:544`, `:587`, `:609`, so it is unaffected), and `model/MyTeam.kt`

**steps:**
1. In `TeamDao.observeAll`, replace the query with `SELECT * FROM teams WHERE isDeletePending = 0 AND (docType = 'membership' OR teamId IS NULL OR TRIM(teamId) = '')`. Keep the name and the `Flow<List<MyTeam>>` signature.
2. Add a one-line KDoc above it: it returns only live memberships and root teams, and callers filter further.
3. In `TeamDaoTest`, insert a root team, a live membership, a membership with `isDeletePending = true`, a `report` child and a `request` child.
4. Assert that `observeAll().first()` returns exactly the root team and the live membership.
5. Run the DAO test, then the full suite.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.TeamDaoTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- On a device: the dashboard "My Teams" strip and the Teams / Enterprises "my teams" lists show the same entries as before. Joining or leaving a team still updates them live.

**size budget:** ~25 changed lines, 2 files.

**out of scope:**
- Do not rename `observeAll`; that needs `TeamsRepositoryImpl.kt`.
- Do not add an index; that needs the `AppDatabase` version bump.

---

### 2. Run submission PDF export on the IO dispatcher (roadmap 7 + 8, also 9)

**context:** The call chain runs on the Main thread from top to bottom:
- `SubmissionListViewModel.generateSubmissionPdf` / `generateMultipleSubmissionsPdf` (`ui/submissions/SubmissionListViewModel.kt:44-53`) launch on `viewModelScope`.
- `SubmissionsRepositoryImpl.kt:57-58` passes straight through.
- `SubmissionsRepositoryExporter.generateSubmissionPdf` (`:50`) and `generateMultipleSubmissionsPdf` (`:135`) have no `withContext`.

So all `PdfDocument` drawing, `getExternalFilesDir`, `mkdirs` and `FileOutputStream` writes happen on Main. A multi-submission export can cause an ANR. In addition, both functions end in `catch (e: Exception) { null }` (`:130`, `:246`), which swallows `CancellationException`.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` (constructor `:27-34`, `generateSubmissionPdf`, `generateMultipleSubmissionsPdf`)
- change `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt` (direct constructor call `:29-36`)
- leave alone: `SubmissionsRepositoryImpl.kt` and `SubmissionsRepositoryImplTest.kt` (busy; that test uses a relaxed mock of the exporter), and `SubmissionListViewModel.kt`

**steps:**
1. Add `private val dispatcherProvider: DispatcherProvider` (`org.ole.planet.myplanet.utils.DispatcherProvider`) to the `@Inject` constructor. Hilt already provides it through `DispatcherModule`.
2. Wrap the body of each of the two public functions in `withContext(dispatcherProvider.io) { … }`.
3. Before each of the two `catch (e: Exception)` blocks at `:130` and `:246`, add `catch (e: CancellationException) { throw e }`. Leave the inner `JSONObject` catch at `:296` alone.
4. In the test, pass `TestDispatcherProvider` (from `app/src/test/.../utils/TestDispatcherProvider.kt`) to the constructor.
5. Add one test: `generateSubmissionPdf` returns `null` when `submissionDao.getByIdOrRemoteId` returns `null`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SubmissionsRepositoryExporterTest"` passes, and the full suite stays green.
- On a device, exporting one submission and "export all" from the submission list still produce the PDF and show the share/open result, with no UI freeze.

**size budget:** ~30 changed lines, 2 files.

**out of scope:**
- No changes to the PDF layout.
- No batching of the per-submission DAO reads.

---

### 3. Log resource downloads through `ResourcesAccessViewModel` instead of `UserSessionManager` (roadmap 1 + 3, also 9)

**context:** `base/BaseContainerFragment.kt:51-52` injects the service `UserSessionManager` as `profileDbHandler`. Its only use is `:259`, `profileDbHandler.setResourceOpenCount(items, KEY_RESOURCE_DOWNLOAD)`. Every other tracking call in the same function already goes through `resourcesAccessViewModel.trackOpen` (`:235`, `:247`, `:251`).

`UserSessionManager.kt:83-106` does only two things: it skips users whose `id` starts with `"guest"`, then calls `activitiesRepository.logResourceOpen(...)`. That makes the base fragment of every resources and courses screen go fragment → service → repository.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt`
- change `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt` (field `:51-52`, import `:39`, call `:259`)
- change `app/src/test/java/org/ole/planet/myplanet/base/ResourcesAccessViewModelTest.kt` (constructor `:35-38`)
- leave alone:
  - `services/UserSessionManager.kt` (busy). `setResourceOpenCount` stays because `ResourcesRepositoryImpl.kt:874` still calls it.
  - `repository/ResourcesRepositoryImpl.kt` (busy).
  - `DashboardActivity.kt`, which has its own `profileDbHandler`.

**steps:**
1. In `ResourcesAccessViewModel`, add `private val activitiesRepository: ActivitiesRepository`.
2. In the same class, narrow `userRepository` to `UserReadRepository`; it only calls `getUserModel()` at `:49`.
3. Add `suspend fun trackDownload(item: MyLibrary)`:
   - Load the user with `getUserModel()` and return if `user?.id?.startsWith("guest") == true`.
   - Call `activitiesRepository.logResourceOpen(user?.name, user?.parentCode, user?.planetCode, item.title, item.resourceId, UserSessionManager.KEY_RESOURCE_DOWNLOAD)`.
   - Rethrow `CancellationException`, and log any other exception the same way `UserSessionManager` does.
4. In `BaseContainerFragment`, replace `:259` with `resourcesAccessViewModel.trackDownload(items)`. The code is already inside the `viewLifecycleOwner.lifecycleScope.launch` started at `:228`.
5. Delete the `profileDbHandler` field and the `KEY_RESOURCE_DOWNLOAD` static import.
6. In the test, add `activitiesRepository` to the constructor and add two cases:
   - a guest user gets no `logResourceOpen` call
   - a normal user gets exactly one call, with type `"download"`

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.base.ResourcesAccessViewModelTest"` passes, and the full suite stays green.
- On a device, tapping a resource that is not yet downloaded and not audio/video still starts the download and auto-opens it.
- The profile "Resource opened N times" stat and the activity upload still record the download for non-guest users.

**size budget:** ~45 changed lines, 3 files.

**out of scope:**
- Do not touch `ResourcesRepositoryImpl.trackResourceOpen`.
- Do not remove `UserSessionManager.setResourceOpenCount`.

---

### 4. Load the profile user once in `UserProfileViewModel` (roadmap 3 + 7)

**context:** `ui/user/UserProfileViewModel.kt:35-37` reads the user twice to get one row:
- `getActiveUserIdSuspending()`, which is `getUserModel()?.id` → `userDao.getById(prefId)` (`UserRepositoryImpl.kt:836-838`, `:507-510`)
- then `getUserByAnyId(userId)`, which is `userDao.getById(id)` again (`:121-123`)

On top of that, `init` (`:125`) and `getOfflineVisits` (`:138`) each call `getUserModel()` once more. Opening the profile screen therefore runs four identical user queries, because `UserProfileFragment.kt:146-147` calls both loaders right after construction.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt` (`loadCurrentUserProfile`, `init`, `getOfflineVisits`)
- change `app/src/test/java/org/ole/planet/myplanet/ui/user/UserProfileViewModelTest.kt` (the `loadCurrentUserProfile` test at `:164-177`)
- leave alone:
  - `ui/user/UserProfileFragment.kt` (busy)
  - `repository/UserRepositoryImpl.kt` (busy)
  - `updateCurrentUserProfile` / `updateCurrentUserProfileImage`: they must re-read the id after an edit, so keep them as they are

**steps:**
1. Add a private `Deferred<UserEntity?>` that is started with `viewModelScope.async { userRepository.getUserModel() }`. Copy the pattern from `ui/resources/ResourcesViewModel.kt:38-42`.
2. In `init`, use `await()` on it instead of calling `getUserModel()` directly.
3. In `getOfflineVisits`, do the same.
4. In `loadCurrentUserProfile`, set `_userModel.value` from the awaited value. Drop the `getActiveUserIdSuspending` + `getUserByAnyId` pair, but keep the "blank id → return" guard.
5. Rewrite the test at `:164-177`:
   - Stub `getUserModel()` instead of `getUserByAnyId`.
   - Assert `coVerify(exactly = 1) { userRepository.getUserModel() }` after calling `loadCurrentUserProfile()` and `getOfflineVisits()`.
6. Run the class test and the full suite.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.user.UserProfileViewModelTest"` passes, and the full suite stays green.
- On a device, the profile screen still shows name, image, offline-visit count, "Resource opened N times", most-opened resource and community code.
- Editing the profile still refreshes the header.

**size budget:** ~30 changed lines, 2 files.

**out of scope:**
- Do not narrow `userRepository` to `UserReadRepository`; this ViewModel still needs `updateUserDetails`, `updateUserImage` and `getConnectedCommunityCode`.
- No changes to the update flows.

---

### 5. Make `SettingsViewModel` own the guest check and the auto-download list (roadmap 3 + 4)

**context:** There are two boundary leaks in settings.

- `ui/settings/SettingsViewModel.kt:25` injects the service `UserSessionManager` only for `:41`, `isGuest() = userSessionManager.getUserModel()?.id?.startsWith("guest") == true`. `UserSessionManager.getUserModel()` simply delegates to `userRepository.getUserModel()` (`services/UserSessionManager.kt:28-30`).
- `SettingsActivity.SettingFragment` keeps the data list itself:
  - It holds `private var libraryList: List<MyLibrary>?` (`ui/settings/SettingsActivity.kt:93`).
  - It writes that list from `downloadCompleteEvent` (`:101-104`).
  - It feeds the list back into `viewModel.downloadFiles(libraryList)` (`:212`).
  - So repository output is cached in the view and round-tripped back to the ViewModel.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt` (constructor, `isGuest`, `downloadFiles`)
- change `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt` (`SettingFragment`: field `:93`, collector `:101-104`, listener `:207-216`)
- change `app/src/test/java/org/ole/planet/myplanet/ui/settings/SettingsViewModelTest.kt` (mocks `userSessionManager` at `:44`, `:69-121`)
- leave alone: `services/UserSessionManager.kt` and `services/SharedPrefManager.kt` (busy), `repository/ResourcesRepositoryImpl.kt` (busy), and the `sharedPrefManager.setBetaAutoDownload` calls at `:211`/`:214`

**steps:**
1. In `SettingsViewModel`, replace the `UserSessionManager` parameter with `UserReadRepository`.
2. Make `isGuest()` call `userReadRepository.getUserModel()`. Keep the `id.startsWith("guest")` predicate exactly as it is; `SettingsViewModelTest` pins it, and `UserEntity.isGuest()` checks something different.
3. Add a private `lastDownloadedFiles: List<MyLibrary>?` to the ViewModel.
4. Change `downloadFiles()` to take no argument. It passes `lastDownloadedFiles` to `resourcesRepository.downloadFiles(...)` and stores the result before sending `_downloadCompleteEvent`.
5. In `SettingFragment`, delete `libraryList`. The `downloadCompleteEvent` collector now only re-enables the switch, and the listener calls `viewModel.downloadFiles()`.
6. Update the test:
   - Mock `UserReadRepository` instead of `UserSessionManager`.
   - Add one case: a second `downloadFiles()` call passes the first call's result to `resourcesRepository.downloadFiles`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.settings.SettingsViewModelTest"` passes, and the full suite stays green.
- On a device, guest users still get the blocked switches and the guest prompt on "clear data".
- Toggling "beta auto download" on still disables the switch until the download list returns, then enables it again.

**size budget:** ~35 changed lines, 3 files.

**out of scope:**
- Do not move the `SharedPrefManager` writes into the ViewModel.
- No changes to the retry-queue or storage-breakdown code in the same files.

---

### 6. Let `ChatViewModel` resolve the current user itself (roadmap 1 + 3, also 10)

**context:** `ui/chat/ChatHistoryFragment.kt:139-143` reads `sharedPrefManager.getUserId()` and passes the result into `ChatViewModel.loadChatHistoryScreenData(userId)` (`ui/chat/ChatViewModel.kt:131-151`). The ViewModel then calls `loadCurrentUser(userId)` → `userRepository.getUserById(userId)` (`:178-183`).

That is identical to `UserReadRepository.getUserModel()`: in `UserRepositoryImpl.kt:507-510` it reads the same pref and runs the same `userDao.getById`. So the fragment is doing the ViewModel's data lookup. Because the screen state depends on a value the view passes in, it cannot be hoisted for Compose.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (constructor `:50`, `loadChatHistoryScreenData`, `loadCurrentUser`)
- change `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` (`refreshChatHistory`, `:139-143`)
- change `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` (call sites `:215`, `:248`, `:270`, `:277`, `:291`, `:298`, `:323`, `:336`, `:359`, `:362`; stubs `:203`, `:232`, `:313`, `:349`)
- leave alone:
  - `ui/chat/ChatDetailFragment.kt` (busy). It still calls `ChatViewModel.getUserById` at `:266`, so keep that public method.
  - The `serverUrl` pref read in `ChatHistoryFragment` at `:47-48`.

**steps:**
1. Narrow the `userRepository` constructor parameter to `UserReadRepository`; the ViewModel only uses `getUserById`.
2. Change `loadChatHistoryScreenData()` to take no parameter.
3. Change `loadCurrentUser()` so it returns `userRepository.getUserModel()`. Keep the `cachedUser` short-circuit at `:138`.
4. In `ChatHistoryFragment.refreshChatHistory`, call `sharedViewModel.loadChatHistoryScreenData()`. Keep the `sharedPrefManager` field; `serverUrl` still uses it.
5. In the test, drop the argument at every call site. Change the `getUserById("user123")` stubs and verifies to `getUserModel()`; the `viewModel getUserById proxies` test at `:478-486` stays as it is.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.ChatViewModelTest"` passes, and the full suite stays green.
- On a device, chat history still lists the logged-in user's conversations and the share targets (community, teams, enterprises).
- Search and share-to-voices still work.

**size budget:** ~40 changed lines, 3 files (the test changes are mostly mechanical).

**out of scope:**
- Do not move the shared-news merge at `ChatHistoryFragment.kt:216-225`.
- No changes to the AI-provider loading.

---

### 7. Move the join/leave-course decision from `TakeCourseFragment` into `TakeCourseViewModel` (roadmap 3, also 10)

**context:** `ui/courses/TakeCourseFragment.kt:409-449` (`addRemoveCourse`) does the data work in the view:
- re-fetches the course through `viewModel.getCourseById`
- decides membership with `course?.userId?.contains(userModel?.id)`
- picks `leaveCourse` or `joinCourse`
- hand-patches `currentCourse.userId`

The repository already exposes `CoursesRepository.isMyCourse(userId, courseId)` (`repository/CoursesRepository.kt:31`). The same fragment also injects `UserSessionManager` at `:40-41` (import `:29`) and never uses it.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (constructor `:41`, add a toggle, remove `getCourseById` / `joinCourse` / `leaveCourse` pass-throughs `:73-83`)
- change `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt` (`addRemoveCourse`, the dead field and its import)
- change `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`
- leave alone: `repository/CoursesRepositoryImpl.kt` (busy), `CourseDetailFragment.kt`, `CoursesFragment.kt`

**steps:**
1. Narrow the `userRepository` constructor parameter of `TakeCourseViewModel` to `UserReadRepository`; it only calls `getUserModel()` at `:111`.
2. Add `suspend fun toggleMembership(courseId: String, userId: String): Result<Boolean>`. It:
   - reads `coursesRepository.isMyCourse(userId, courseId)`
   - calls `leaveCourse` or `joinCourse` accordingly
   - on success calls `loadCourse(courseId, forceRefresh = true)` and returns the new joined state
3. Delete the now-unused pass-throughs `getCourseById`, `leaveCourse` and `joinCourse` (`:73-83`).
4. Rewrite `addRemoveCourse` so it calls `viewModel.toggleMembership(cId, userId)` and chooses the "added to" / "removed from" toast from the returned Boolean.
5. Drop the hand-patched `currentCourse.copy(userId = …)`; the forced reload already emits fresh state.
6. Delete the `userSessionManager` field and its import. Add tests for both toggle directions: joined → `leaveCourse`, and not joined → `joinCourse`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest"` passes, and the full suite stays green.
- On a device, on the take-course screen, "join" adds the course to My Courses and "leave" removes it, with the same toasts.
- The join/leave button label updates without navigating away.

**size budget:** ~60 changed lines, 3 files.

**out of scope:**
- Do not fold `changeNextButtonState` (`:301-324`) or the hard-coded survey course id into the ViewModel in this task.
- No layout changes.

---

### 8. Drive `MembersFragment`'s adapters from `RequestsViewModel` state only (roadmap 3 + 7)

**context:** `ui/teams/members/MembersFragment.kt:71-75` launches its own user lookup, `ensureUserResolved()`, which is a `userRepository.getUserModel()` call in `base/BaseTeamFragment.kt:37-39`. It then pushes the result into `requestsAdapter.setUser(...)` and `membersAdapter.setUserId(...)`.

`RequestsViewModel` already publishes the same user:
- `RequestsUiState.currentUser` (`ui/teams/members/RequestsViewModel.kt:21-27`, filled at `:135-143`)
- `MembersUiState.currentUserId` (`:66-75`)

The fragment already consumes `currentUserId` at `:94`, and the sibling `RequestsFragment.kt:37` already calls `setUser(uiState.currentUser)` from state. So the members screen runs a redundant query and has two writers racing to set the adapters' user.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt` (`:71-75` and the `uiState` collector at `:81-88`)
- leave alone:
  - `base/BaseTeamFragment.kt`: its `ensureUserResolved()` is still used by `onCreate` (`:46`) and by subclasses that open PRs own
  - `RequestsViewModel.kt`, `RequestsAdapter.kt`, `MembersAdapter.kt`

**steps:**
1. Delete the `viewLifecycleOwner.lifecycleScope.launch { … ensureUserResolved() … }` block at `:71-75`.
2. In the existing `collectWhenStarted(requestsViewModel.uiState)` collector, add `requestsAdapter?.setUser(state.currentUser)` as its first line, the same way `RequestsFragment.kt:37` does.
3. Keep `membersAdapter?.setUserId(state.currentUserId)` in the `membersState` collector (`:94`). It is now the only writer of the members adapter's user id.
4. Remove any import the deletion orphans; for example, `UserEntity` is still used at `:64`, so check before removing it.
5. Run the unit tests.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` stays green.
- On a device, on a team's Members tab:
  - A leader still sees accept/reject on join requests and the "make leader" / "remove" actions on members.
  - A non-leader does not see them.
  - The current user's own card is still recognised: no "remove" on self.

**size budget:** ~8 changed lines, 1 file.

**out of scope:**
- Do not move `teamsRepository` calls out of `BaseTeamFragment`; `PlanFragment` (busy) depends on the inherited field.
- No adapter API changes.

---

### 9. Filter nameless communities in SQL, not in the server dialog (roadmap 1 + 7)

**context:** `ui/sync/ServerDialogExtensions.kt:298-299` (an extension on `SyncActivity`) loads every community and then filters in the UI:
- `val communities = communityRepository.getAllSorted()`
- `communities.filter { !TextUtils.isEmpty(it.name) }`

`CommunityRepositoryImpl.kt:40-41` passes straight through to `CommunityDao.getAllSorted()` (`data/room/dao/CommunityDao.kt:12-13`, `SELECT * FROM community ORDER BY weight ASC`). The server dialog is that method's only caller in `app/src/main` (grep-verified). `Community.name` is a non-null `String` with default `""` (`model/Community.kt:18`), so the filter belongs in the query.

**files:**
- change `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt` (`getAllSorted`)
- change `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` (`setupManualConfigEnabled`, `:298-299`)
- add `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CommunityDaoTest.kt` (new; copy the in-memory Room setup from `TeamDaoTest.kt:17-34`)
- leave alone: `repository/CommunityRepositoryImpl.kt` (busy; unchanged pass-through), `ui/sync/SyncActivity.kt`, `model/Community.kt` (busy)

**steps:**
1. Change the `getAllSorted` query to `SELECT * FROM community WHERE name != '' ORDER BY weight ASC`. It still uses the `weight` index.
2. Add a KDoc saying nameless rows are excluded.
3. In `ServerDialogExtensions.setupManualConfigEnabled`, pass `communities` straight to the `ArrayAdapter`. Delete the `nonEmptyCommunities` filter, and the `TextUtils` import if nothing else in the file uses it.
4. In the new DAO test, insert three rows (weights 3/1/2), one of them with `name = ""`.
5. Assert that `getAllSorted()` returns the two named rows in weight order. Also assert that `replaceAll` still swaps the whole table.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.CommunityDaoTest"` passes, and the full suite stays green.
- On a device, in the login screen's server dialog, switching to manual configuration still fills the community spinner, in the same order and with no blank entries.

**size budget:** ~60 changed lines, 3 files (about 50 of them in the new test).

**out of scope:**
- Do not move the spinner setup into a ViewModel; `SyncActivity` has none.
- No changes to `CommunityRepository`.

---

### 10. Narrow five ViewModels from `UserRepository` to `UserReadRepository` (roadmap 4 + 1, also 9)

**context:** Five ViewModels inject the full `UserRepository`, more than 40 members across user CRUD, sync, security keys and achievements, but call exactly one read method:

| ViewModel | Constructor line | Only call |
|---|---|---|
| `ui/resources/AddResourceViewModel.kt` | `:18` | `getUserModel()` at `:21` |
| `ui/resources/ResourceDetailViewModel.kt` | `:15` | `getUserModel()` at `:20` |
| `ui/resources/ResourcesViewModel.kt` | `:31` | `getUserModel()` at `:39` |
| `ui/courses/CourseProgressViewModel.kt` | `:17` | `getUserModel()` at `:26` |
| `ui/courses/ProgressViewModel.kt` | `:18` | `getUserModel()` at `:26` |

Depending on the narrow interface documents the real dependency and shrinks what tests have to mock. It also keeps these ViewModels portable once `UserReadRepository` moves into a platform-free core.

**files:**
- change all five ViewModel files in the table above (constructor parameter type and import only)
- leave alone:
  - their tests (`ResourcesViewModelTest`, `AddResourceViewModelTest`, `ResourceDetailViewModelTest`, `CourseProgressViewModelTest`, `ProgressViewModelTest`): their `mockk<UserRepository>()` is a subtype, so they still compile
  - `base/ResourcesAccessViewModel.kt` (task 3), `ui/courses/TakeCourseViewModel.kt` (task 7), `ui/viewer/ResourceViewerViewModel.kt` (its test is in open PR 18029)
  - `di/RepositoryModule.kt`: the binding already exists at `:229`

**steps:**
1. In each of the five files, change the constructor parameter type `UserRepository` → `UserReadRepository`. Keep the parameter name `userRepository` so call sites and named-argument test constructors stay valid.
2. Replace `import org.ole.planet.myplanet.repository.UserRepository` with `import org.ole.planet.myplanet.repository.UserReadRepository`.
3. Grep each file to confirm no other `userRepository.` member is used.
4. Run `./gradlew assembleDefaultDebug`; Hilt resolves the existing binding at compile time.
5. Run the unit tests.

**acceptance:**
- `./gradlew assembleDefaultDebug` and `./gradlew testDefaultDebugUnitTest` both pass, with no test file changes.
- On a device:
  - The resources list, resource detail and "add resource" screens still show user-specific state (My Library, ratings, ownership).
  - Course progress and the progress grid still load for the logged-in user.

**size budget:** ~10 changed lines, 5 files.

**out of scope:**
- Do not change what these ViewModels do. That includes the `user?._id` vs `user?.id` difference in `CourseProgressViewModel` (`:27`); flag it in the PR description instead of fixing it here.
- Do not touch any other ViewModel.

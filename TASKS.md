# myPlanet Improvement Tasks (Sorted by Importance)

## Task 1 — Score: 70
### Add @HiltViewModel + DispatcherProvider to RatingsViewModel
Missing `@HiltViewModel` is a latent runtime bug; DI graph may silently fail. Additionally the two `viewModelScope.launch` blocks have no explicit dispatcher.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt`

**Steps:**
1. Add `@HiltViewModel` annotation above the class declaration
2. Add `DispatcherProvider` as a constructor parameter
3. Change both `viewModelScope.launch {` to `viewModelScope.launch(dispatcherProvider.io) {`

---

## Task 2 — Score: 65
### Fix flowOn(Dispatchers.Main) in RealmRepository.queryListFlow
`RealmRepository.queryListFlow()` and `VoicesRepositoryImpl.getCommunityNews()` both use `flowOn(Dispatchers.Main)` because Realm async queries require a Looper thread. This forces query orchestration onto the main thread. A dedicated single-thread Looper dispatcher for Realm would keep the main thread free.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`

**Steps:**
1. Create a single-thread HandlerThread-backed CoroutineDispatcher in `DatabaseModule` (e.g., `@RealmDispatcher`)
2. Provide it via Hilt so repositories can inject it
3. Replace `flowOn(Dispatchers.Main)` in `RealmRepository.queryListFlow` with `flowOn(realmDispatcher)`
4. Replace `flowOn(Dispatchers.Main)` in `VoicesRepositoryImpl.getCommunityNews` with `flowOn(realmDispatcher)`

---

## Task 3 — Score: 60
### Remove EntryPointAccessors from RealmMyLibrary and RealmSubmission Models
`RealmMyLibrary.insertMyLibrary()` and `RealmSubmission.serialize()` call `EntryPointAccessors.fromApplication()` inside model companion objects. Models should not perform manual DI; the caller should pass already-resolved dependencies as parameters.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt`

**Steps:**
1. Add a `SharedPrefManager` parameter to `RealmMyLibrary.insertMyLibrary()` signature instead of calling `EntryPointAccessors` internally
2. Add a `SharedPrefManager` parameter to `RealmSubmission.serialize()` signature instead of calling `EntryPointAccessors` internally
3. Update all callers of these methods to pass the already-injected `SharedPrefManager`
4. Remove `EntryPointAccessors` imports from both model files

---

## Task 4 — Score: 60
### Replace Raw Realm Queries in UploadManager with Repository Calls
`UploadManager` queries `RealmSubmitPhotos` and `RealmMyLibrary` directly via `databaseService.withRealm` and calls `RealmMyPersonal.serialize()` as a static method. These should route through `SubmissionsRepository`, `ResourcesRepository`, and `PersonalsRepository`.

**Merges:** Tasks 7, 8, and "Replace direct Realm queries in UploadManager"

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`

**Steps:**
1. Add `suspend fun getPhotosByIds(ids: Array<String>): List<RealmSubmitPhotos>` to `SubmissionsRepository` interface and implement it
2. Replace the `databaseService.withRealm { realm.where(RealmSubmitPhotos::class.java)... }` block with `submissionsRepository.getPhotosByIds(photoIds)`
3. Replace the `databaseService.withRealm { realm.where(RealmMyLibrary::class.java)... }` block with `resourcesRepository.getLibraryItemsByIds(libraryIds)`
4. Replace direct `RealmMyPersonal.serialize()` static call with a `PersonalsRepository` method

---

## Task 5 — Score: 55
### Add DispatcherProvider to ProgressViewModel, NotificationsViewModel, UserProfileViewModel
10 total `viewModelScope.launch` blocks across these three ViewModels have no explicit dispatcher while performing Realm work.

**Merges:** Original tasks 2, 3, 5

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt`

**Steps:**
1. Add `private val dispatcherProvider: DispatcherProvider` as a constructor parameter to all three ViewModels
2. Change all `viewModelScope.launch {` calls to `viewModelScope.launch(dispatcherProvider.io) {`

---

## Task 6 — Score: 55
### Move RealmUser Static Methods into UserRepositoryImpl
`RealmUser.companion` contains `createGuestUser()`, `populateUsersTable()`, `isUserExists()`, `updateUserDetails()`, and `cleanupDuplicateUsers()` — over 300 lines of business logic with raw Realm transactions inside a model class.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`

**Steps:**
1. Add `isUserExists(name)` and `updateUserDetails(...)` to `UserRepository` interface
2. Move the Realm query/transaction logic from `RealmUser` companion into `UserRepositoryImpl`
3. Update call sites (UI and services) to use injected `UserRepository`
4. Remove the static companion methods after all callers are migrated

---

## Task 7 — Score: 55
### Replace Thread.sleep in RetryInterceptor with Non-Blocking Retry
`RetryInterceptor` uses `Thread.sleep(delay)` inside the OkHttp interceptor chain, blocking the entire network thread during retries.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`

**Steps:**
1. Move retry logic from the OkHttp interceptor into a Retrofit `CallAdapter` or a suspend wrapper at the `ApiInterface` call-site level
2. Use `kotlinx.coroutines.delay()` instead of `Thread.sleep()` for back-off
3. Remove `Thread.sleep` and `Thread.currentThread().interrupt()` from `RetryInterceptor`
4. If keeping the interceptor pattern, document why blocking is acceptable or limit to a maximum sleep of 500ms

---

## Task 8 — Score: 55
### Remove Direct Realm Access from BaseResourceFragment / Use Injected DatabaseService
`BaseResourceFragment` has two `Realm.getDefaultInstance()` calls that should use the injected `DatabaseService`. The base class is used by many fragments, so this fix has wide impact.

**Merges:** PR-1 and both BaseResourceFragment DatabaseService tasks

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt`

**Steps:**
1. Replace `Realm.getDefaultInstance()` at line 91 with `databaseService.createManagedRealmInstance()`
2. Replace `Realm.getDefaultInstance()` at line 381 with `databaseService.createManagedRealmInstance()`
3. Add repository methods for any remaining direct Realm data access
4. Remove direct `Realm` import if no longer referenced

---

## Task 9 — Score: 50
### Move RealmMyCourse Companion Query Helpers into CoursesRepositoryImpl
Static queries in `RealmMyCourse.companion` leak data access into the model layer. Called from `BaseRecyclerParentFragment` and other UI sites.

**Merges:** Original task 10 and "Move RealmMyCourse companion"

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt`

**Steps:**
1. Add `getMyCoursesByUserId(userId)` and `getAllCourses()` methods to `CoursesRepository` interface
2. Implement them in `CoursesRepositoryImpl`
3. Replace `RealmMyCourse.getMyCourseByUserId()` and `RealmMyCourse.getOurCourse()` calls in `BaseRecyclerParentFragment` with `coursesRepository` calls
4. Deprecate or remove the static companion methods once all callers are migrated

---

## Task 10 — Score: 50
### Move RealmMyTeam Static Query Methods into TeamsRepositoryImpl
`RealmMyTeam.companion` holds `getResourceIds()`, `getResourceIdsByUser()`, `getTeamCreator()`, `isTeamLeader()`, and `getMyTeamsByUserId()` — all taking a raw Realm parameter.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`

**Steps:**
1. Add `getResourceIds(teamId)`, `getResourceIdsByUser(userId)`, `getTeamCreator(teamId)`, and `isTeamLeader(teamId, userId)` to `TeamsRepository` interface
2. Move the Realm query logic from `RealmMyTeam.companion` into `TeamsRepositoryImpl`
3. Update callers to use injected `TeamsRepository`
4. Remove or deprecate the companion methods after migration

---

## Task 11 — Score: 50
### Use Injected DispatcherProvider in DatabaseService
`DatabaseService` hardcodes `Dispatchers.IO`, hurting testability of the core data layer.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt`

**Steps:**
1. Inject `DispatcherProvider` into `DatabaseService` constructor
2. Replace `Dispatchers.IO` with `dispatcherProvider.io`
3. Update the DI module provisioning if needed

---

## Task 12 — Score: 50
### Wrap TeamViewModel.requestToJoin and leaveTeam in withContext(dispatcherProvider.io)
`TeamViewModel` already injects `dispatcherProvider` but `requestToJoin` and `leaveTeam` call Realm-touching repository methods without a dispatcher wrapper.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt`

**Steps:**
1. In `requestToJoin`, wrap `teamsRepository.requestToJoin(…)` and `teamsRepository.syncTeamActivities()` in `withContext(dispatcherProvider.io) { … }`
2. In `leaveTeam`, wrap `teamsRepository.leaveTeam(…)` and `teamsRepository.syncTeamActivities()` the same way

---

## Task 13 — Score: 45
### Replace Direct Realm Queries in TransactionSyncManager with Repository Calls
`TransactionSyncManager` directly queries and mutates `RealmUser` through `DatabaseService` and calls static model methods, bypassing injected repositories.

**Merges:** PR-2 and "Replace direct Realm queries in TransactionSyncManager"

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`

**Steps:**
1. Inject `UserRepository` into `TransactionSyncManager` constructor
2. Replace `databaseService.withRealm { realm.where(RealmUser::class.java)... }` with a `UserRepository` method
3. Replace `RealmUser.populateUsersTable()` static call with a `UserRepository.populateUser()` method
4. Remove direct model companion imports once repository methods are used

---

## Task 14 — Score: 45
### Replace MainApplication.applicationScope with Injected @ApplicationScope
Both `LoginSyncManager` and `TransactionSyncManager` use `MainApplication.applicationScope` while DI already provides `@ApplicationScope`. Split scope pattern causes inconsistent threading control.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt`

**Steps:**
1. Inject `@ApplicationScope CoroutineScope` into `LoginSyncManager`
2. Replace `MainApplication.applicationScope` calls in both managers with injected scope
3. Keep dispatcher usage unchanged to keep PR narrow

---

## Task 15 — Score: 45
### Route databaseService.clearAll() Through ConfigurationsRepository
`SyncActivity` and `SettingsActivity` both call `databaseService.clearAll()` directly from the UI layer for the "reset app" flow. This nuclear database operation should be mediated by a repository.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`

**Steps:**
1. Add `suspend fun resetDatabase()` to `ConfigurationsRepository` interface
2. Implement it in `ConfigurationsRepositoryImpl` by calling `databaseService.clearAll()`
3. Replace `databaseService.clearAll()` in `SyncActivity` and `SettingsActivity` with `configurationsRepository.resetDatabase()`
4. Remove direct `DatabaseService` injection from both UI files if no other usage remains

---

## Task 16 — Score: 40
### Move updateHealthData from UploadToShelfService to HealthRepository
`UploadToShelfService.updateHealthData` performs a raw Realm query inside a `dbService.executeTransactionAsync` callback. `HealthRepository` already owns `RealmHealthExamination`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`

**Steps:**
1. Add `suspend fun updateExaminationUserId(id: String, userId: String)` to `HealthRepository` interface
2. Implement it in `HealthRepositoryImpl`
3. Inject `HealthRepository` into `UploadToShelfService` and replace the direct Realm block
4. Delete the private `updateHealthData(realm, model)` helper

---

## Task 17 — Score: 40
### Shift BaseDashboardFragment Repository Reads into DashboardViewModel
`BaseDashboardFragment` directly uses repositories for user and "My Life" data while also holding `DashboardViewModel`, creating split data ownership.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`

**Steps:**
1. Add a `DashboardViewModel` method for loading/initializing visible My Life items
2. Replace direct `lifeRepository` calls in `myLifeListInit` with that ViewModel method
3. Keep rendering code in fragment as-is

---

## Task 18 — Score: 40
### Move DictionaryActivity Realm Queries into a New DictionaryRepository
`DictionaryActivity` performs four direct Realm operations (count, bulk insert, search) in the UI layer, the only domain without a repository.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`
- New: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt`
- New: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`

**Steps:**
1. Create `DictionaryRepository` interface with `getDictionaryCount()`, `searchWord(word)`, and `loadDictionaryFromJson(jsonArray)` methods
2. Create `DictionaryRepositoryImpl` extending `RealmRepository`
3. Bind the new repository in `RepositoryModule`
4. Update `DictionaryActivity` to inject and use the repository
5. Remove direct Realm imports from `DictionaryActivity`

---

## Task 19 — Score: 35
### Move Shelf Aggregation Realm Work Out of UploadToShelfService
`UploadToShelfService` performs shelf aggregation using direct Realm access via `dbService.withRealm`, bypassing repository boundaries.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`

**Steps:**
1. Add repository function(s) that return pre-merged shelf payload components for a user
2. Replace `dbService.withRealm { getShelfData(...) }` with repository call in `uploadToShelf`
3. Apply same replacement to `uploadSingleUserToShelf` in a follow-up

---

## Task 20 — Score: 35
### Move LoginActivity Team-Member Persistence into Repository
`LoginActivity` fetches team members and mutates saved-user preferences directly, blending UI and data concerns.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`

**Steps:**
1. Add repository/service method that returns team members and updates saved users in one data-layer call
2. Replace `fetchAndSaveTeamMembers` body with a single call + UI binding update
3. Keep adapter/population logic in activity untouched

---

## Task 21 — Score: 35
### Replace Global Realm in EventsDetailFragment with Injected DatabaseService
`EventsDetailFragment` directly calls `Realm.getDefaultInstance()` instead of using DI.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDetailFragment.kt`

**Steps:**
1. Add `@Inject lateinit var databaseService: DatabaseService`
2. Replace `Realm.getDefaultInstance()` with `databaseService.createManagedRealmInstance()`

---

## Task 22 — Score: 35
### Realm Flow Listener Lifecycle Hardening in RealmRepository
`RealmRepository.queryListFlow` creates async listeners pinned to `Dispatchers.Main`. A narrow improvement pass can reduce risk by adding explicit lifecycle/ownership conventions.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt`

**Steps:**
1. Add documentation/comments and helper wrappers for expected collection scope (view lifecycle only)
2. Audit one repository flow consumer and ensure cancellation on `onDestroyView`
3. Keep behavior identical; no schema or API contract changes

---

## Task 23 — Score: 30
### Migrate ChatHistoryFragment + ChatDetailFragment to activityViewModels()
Both fragments use manual `ViewModelProvider` instead of the `by activityViewModels()` delegate.

**Merges:** Both chat fragment tasks

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`

**Steps:**
1. Replace `ViewModelProvider(requireActivity())[ChatViewModel::class.java]` with `by activityViewModels<ChatViewModel>()`
2. Apply to both fragments

---

## Task 24 — Score: 30
### Realtime List Refresh / DiffUtils Adapter Cleanup
`RealtimeSyncHelper` force-refreshes generic `ListAdapter` via unsafe cast. Standardize on explicit `OnDiffRefreshListener`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt`

**Steps:**
1. Update one target screen adapter to implement `OnDiffRefreshListener` explicitly
2. Remove fallback cast path for that screen
3. Repeat per-screen in follow-up PRs

---

## Task 25 — Score: 30
### Eliminate Hardcoded Dispatchers in CameraUtils
`CameraUtils` creates `CoroutineScope(Dispatchers.Default + cameraJob)` making it hard to test.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt`

**Steps:**
1. Modify `CameraUtils` to accept an injected `CoroutineScope` or `CoroutineDispatcher`
2. Remove hardcoded `Dispatchers.Default`

---

## Task 26 — Score: 25
### Remove Unused DatabaseService from UserSessionManager
`UserSessionManager` injects `DatabaseService` as `realmService` but current logic reads/writes through repositories only.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt`

**Steps:**
1. Remove `realmService: DatabaseService` from constructor
2. Update DI wiring if needed
3. Verify no behavior changes in login/logout paths

---

## Task 27 — Score: 25
### Migrate OnboardingAdapter to ViewPager2 with ListAdapter
Refactor from legacy `PagerAdapter` to `RecyclerView.Adapter` for use with `ViewPager2`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingAdapter.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingActivity.kt`

**Steps:**
1. Convert `PagerAdapter` implementation into `RecyclerView.Adapter` with standard `ViewHolder` pattern
2. Update `OnboardingActivity` to use `ViewPager2` instead of `ViewPager`

---

## Task 28 — Score: 20
### Migrate AddHealthActivity to View Binding
Replace all manual `findViewById` calls with View Binding.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt`

**Steps:**
1. Declare `ActivityAddHealthBinding` variable
2. Change `setContentView` to use binding root
3. Replace all `findViewById` references with binding equivalents

---

## Task 29 — Score: 15
### Remove Unused Imports from TakeCourseFragment
`TakeCourseFragment.kt` imports `ActivitiesRepository`, `ProgressRepository`, and `SubmissionsRepository` but never uses them.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt`

**Steps:**
1. Delete the three unused import lines
2. Confirm no `@Inject` field or call site references the symbols

---

## Task 30 — Score: 15
### Replace findViewById in FeedbackFragment with View Binding
Replace `requireView().findViewById<RadioButton>(...)` with direct binding access.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackFragment.kt`

**Steps:**
1. Replace `requireView().findViewById<RadioButton>(binding.rgUrgent.checkedRadioButtonId)` with direct binding access
2. Apply same pattern for `binding.rgType`

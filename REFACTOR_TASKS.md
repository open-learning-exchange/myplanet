# Refactor Roadmap — Round Tasks

### Remove Stale Unused Repository Imports from TakeCourseFragment

`TakeCourseFragment.kt` imports `ActivitiesRepository`, `ProgressRepository`, and `SubmissionsRepository` but never declares `@Inject` fields or calls anything from them. Dead imports mislead reviewers into thinking the fragment couples to those data domains, and create noise during future refactors of those interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L27-L30"}

:::task-stub{title="Drop orphaned repository imports from TakeCourseFragment"}
1. Delete lines 27, 29, and 30 (`import …ActivitiesRepository`, `import …ProgressRepository`, `import …SubmissionsRepository`)
2. Confirm no `@Inject` field or call site inside the class body references any of the three symbols
:::

---

### Add DispatcherProvider to ProgressViewModel

`loadCourseData()` calls `userSessionManager.getUserModel()` and `progressRepository.fetchCourseData()` from a bare `viewModelScope.launch` with no explicit dispatcher. Both suspend functions touch Realm internally. The pattern already established by `FeedbackListViewModel` (uses `viewModelScope.launch(dispatcherProvider.io)`) and `DashboardViewModel` must be applied here to keep DB work off the Main thread at the call site.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt#L22-L27"}

:::task-stub{title="Add DispatcherProvider to ProgressViewModel"}
1. Add `private val dispatcherProvider: DispatcherProvider` as a constructor parameter (injected via Hilt, see `FeedbackListViewModel` for reference)
2. Change `viewModelScope.launch {` to `viewModelScope.launch(dispatcherProvider.io) {` in `loadCourseData`
:::

---

### Add DispatcherProvider to NotificationsViewModel

`NotificationsViewModel` has three `viewModelScope.launch` blocks (lines 36, 128, 155) with no explicit dispatcher while each body delegates to `notificationsRepository` which reads Realm. Even though the repository internally uses `withContext`, the outer coroutine stays on Main until that switch, leaving the scheduler call overhead on the UI thread.

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L34-L43"}

:::task-stub{title="Add DispatcherProvider to NotificationsViewModel"}
1. Add `DispatcherProvider` constructor dependency (following `FeedbackListViewModel` pattern)
2. Change all three `viewModelScope.launch {` calls to `viewModelScope.launch(dispatcherProvider.io) {`
:::

---

### Add @HiltViewModel Annotation and DispatcherProvider to RatingsViewModel

`RatingsViewModel` uses `@Inject constructor` without `@HiltViewModel`. Hilt-managed ViewModels must carry `@HiltViewModel` to be created via the Hilt ViewModel factory; without it the ViewModel is not scoped correctly and DI graph injection may silently fail at runtime. Additionally the two `viewModelScope.launch` blocks at lines 49 and 72 have no explicit dispatcher.

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L15-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L47-L51"}

:::task-stub{title="Add @HiltViewModel and DispatcherProvider to RatingsViewModel"}
1. Add `@HiltViewModel` annotation above the class declaration
2. Add `DispatcherProvider` as a constructor parameter
3. Change both `viewModelScope.launch {` to `viewModelScope.launch(dispatcherProvider.io) {`
:::

---

### Add DispatcherProvider to UserProfileViewModel

`UserProfileViewModel` contains five `viewModelScope.launch` blocks (lines 35, 61, 92, 123, 131) with no explicit dispatcher. Every body delegates to `userRepository` or `userSessionManager` which perform Realm reads. The ViewModel has no `DispatcherProvider` in its constructor unlike the rest of the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt#L20-L40"}

:::task-stub{title="Add DispatcherProvider to UserProfileViewModel"}
1. Add `private val dispatcherProvider: DispatcherProvider` as a constructor dependency
2. Change all five `viewModelScope.launch {` calls to `viewModelScope.launch(dispatcherProvider.io) {`
:::

---

### Wrap TeamViewModel.requestToJoin and leaveTeam in withContext(dispatcherProvider.io)

`TeamViewModel` already injects and uses `dispatcherProvider` for `prepareTeamData` which wraps its logic in `withContext(dispatcherProvider.io)`. However `requestToJoin` (line 98) and `leaveTeam` (line 106) call Realm-touching repository methods before delegating to `prepareTeamData`, with no dispatcher wrapper of their own — the repository calls execute on whichever thread the outer `viewModelScope.launch` was dispatched to.

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=115 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L97-L115"}

:::task-stub{title="Wrap requestToJoin and leaveTeam repo calls in withContext(dispatcherProvider.io)"}
1. In `requestToJoin`, wrap the `teamsRepository.requestToJoin(…)` and `teamsRepository.syncTeamActivities()` calls in `withContext(dispatcherProvider.io) { … }`
2. In `leaveTeam`, wrap the `teamsRepository.leaveTeam(…)` and `teamsRepository.syncTeamActivities()` calls the same way
:::

---

### Use resourcesRepository.getLibraryItemsByIds in UploadManager Instead of Raw Realm Query

Lines 364–371 of `UploadManager.uploadResource` manually reach into `databaseService.withRealm { realm -> realm.where(RealmMyLibrary::class.java).in("id", libraryIds).findAll() }` to fetch libraries after a batch upload. `ResourcesRepository` already declares `suspend fun getLibraryItemsByIds(ids: Collection<String>): List<RealmMyLibrary>` (line 36 of the interface) and `UploadManager` already injects `resourcesRepository`. The raw Realm query is a direct repository-boundary violation that can be erased with a one-line call.

:codex-file-citation[codex-file-citation]{line_range_start=363 line_range_end=372 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L363-L372"}

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L36-L36"}

:::task-stub{title="Replace raw RealmMyLibrary query in UploadManager with resourcesRepository.getLibraryItemsByIds"}
1. Remove the `databaseService.withRealm { … }` block at lines 364–371 in `UploadManager.kt`
2. Replace it with `val libraries: List<RealmMyLibrary> = if (libraryIds.isNotEmpty()) resourcesRepository.getLibraryItemsByIds(libraryIds) else emptyList()`
:::

---

### Move RealmSubmitPhotos Bulk-Fetch from UploadManager to SubmissionsRepository

Lines 277–282 of `UploadManager.uploadPhotos` fetch `RealmSubmitPhotos` records by IDs directly via `databaseService.withRealm { realm -> realm.where(RealmSubmitPhotos::class.java).in("id", photoIds).findAll() }`. `SubmissionsRepositoryImpl` already owns all other `RealmSubmitPhotos` operations. Adding a single fetch-by-IDs method closes this service-layer data leak with minimal surface area change.

:codex-file-citation[codex-file-citation]{line_range_start=277 line_range_end=283 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L277-L283"}

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt#L41-L49"}

:::task-stub{title="Move RealmSubmitPhotos bulk-fetch from UploadManager into SubmissionsRepository"}
1. Add `suspend fun getPhotosByIds(ids: Array<String>): List<RealmSubmitPhotos>` to the `SubmissionsRepository` interface
2. Implement it in `SubmissionsRepositoryImpl` using `queryList(RealmSubmitPhotos::class.java) { \`in\`("id", ids) }`
3. Replace the `databaseService.withRealm { … }` block at lines 277–282 in `UploadManager.kt` with `val photos = submissionsRepository.getPhotosByIds(photoIds)` (already injected at line 67)
:::

---

### Move updateHealthData Realm Query from UploadToShelfService to HealthRepository

`UploadToShelfService.updateHealthData` (lines 217–221) performs a raw `realm.where(RealmHealthExamination::class.java).equalTo("_id", …).findAll()` inside a `dbService.executeTransactionAsync` callback. `HealthRepository` already fully owns `RealmHealthExamination` (interface lines 8–14) — adding one targeted mutate method removes all Realm API calls from the service.

:codex-file-citation[codex-file-citation]{line_range_start=171 line_range_end=174 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L171-L174"}

:codex-file-citation[codex-file-citation]{line_range_start=217 line_range_end=221 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L217-L221"}

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt#L8-L14"}

:::task-stub{title="Move updateHealthData query to HealthRepository"}
1. Add `suspend fun updateExaminationUserId(id: String, userId: String)` to the `HealthRepository` interface
2. Implement it in `HealthRepositoryImpl` using `executeTransaction { realm -> realm.where(RealmHealthExamination::class.java).equalTo("_id", id).findAll().forEach { it.userId = userId } }`
3. Inject `HealthRepository` into `UploadToShelfService` and replace the `dbService.executeTransactionAsync { updateHealthData(it, model) }` block with `healthRepository.updateExaminationUserId(model.id ?: "", model._id ?: "")`
4. Delete the private `updateHealthData(realm, model)` helper from `UploadToShelfService`
:::

---

### Replace RealmMyCourse Static Method Calls in BaseRecyclerParentFragment with coursesRepository

`BaseRecyclerParentFragment.getList()` and `getMyLibItems()` call `RealmMyCourse.getMyCourseByUserId()` and `RealmMyCourse.getOurCourse()` directly (lines 23, 24, 64, 78), leaking model-layer filtering logic into the base fragment. `CoursesRepository` already exposes the non-suspend overload `fun getMyCourses(userId: String?, courses: List<RealmMyCourse>): List<RealmMyCourse>` for exactly this purpose, and `coursesRepository` is already injected into `BaseResourceFragment` (parent class).

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L21-L27"}

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L60-L65"}

:codex-file-citation[codex-file-citation]{line_range_start=75 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L75-L80"}

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/4b7ddaa1671ee7e5f08d0b23bd00ff91d024ed33/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L15-L15"}

:::task-stub{title="Replace RealmMyCourse statics with coursesRepository in BaseRecyclerParentFragment"}
1. Replace `RealmMyCourse.getMyCourseByUserId(model?.id, results)` at line 23 with `coursesRepository.getMyCourses(model?.id, results)`
2. Replace `RealmMyCourse.getOurCourse(model?.id, results)` at line 24 with `results.filterNot { it.userId?.contains(model?.id) == true }` or add `getOurCourses` to `CoursesRepository` if the inverse needs to be named
3. Replace `RealmMyCourse.getOurCourse(model?.id, results)` at line 64 the same way
4. Replace `RealmMyCourse.getMyCourseByUserId(model?.id, results)` at line 78 with `coursesRepository.getMyCourses(model?.id, results)`
5. Remove the `RealmMyCourse` import if the class is no longer referenced in this file
:::

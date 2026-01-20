# Refactoring Tasks - Repository Boundaries & Data Layer Cleanup

### 1. Convert TeamCoursesAdapter to ListAdapter with DiffUtil

TeamCoursesAdapter extends RecyclerView.Adapter directly without DiffUtil support. The adapter holds a MutableList but lacks proper update mechanisms, making list changes inefficient and potentially causing UI glitches.

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L23-L45"}

:::task-stub{title="Convert TeamCoursesAdapter to ListAdapter"}
1. Change TeamCoursesAdapter to extend ListAdapter<RealmMyCourse, ViewHolder>
2. Create DiffUtil.ItemCallback using DiffUtils.itemCallback<RealmMyCourse> pattern
3. Replace list property with currentList from ListAdapter
4. Update getItemCount to use currentList.size
5. Update onBindViewHolder to use getItem(position)
:::

### 2. Move LifeFragment Realm access to LifeRepository

LifeFragment directly accesses mRealm and calls mRealm.copyFromRealm() to detach objects. This data access should be encapsulated in LifeRepository, which already exists but isn't being used for queries.

:codex-file-citation[codex-file-citation]{line_range_start=88 line_range_end=91 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L88-L91"}
:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt#L8-L15"}

:::task-stub{title="Move LifeFragment query to LifeRepository"}
1. Add getMyLifeByUserId(userId: String): List<RealmMyLife> to LifeRepository interface
2. Implement method in LifeRepositoryImpl using RealmRepository.queryList
3. Return detached copies using realm.copyFromRealm internally
4. Inject LifeRepository into LifeFragment
5. Replace direct mRealm call with repository method in refreshList()
:::

### 3. Extract activity tracking from UserSessionManager to ActivitiesRepository

UserSessionManager contains multiple data access methods for RealmOfflineActivity that belong in ActivitiesRepository. Methods like getOfflineVisits() and getLastVisit() are pure data queries that violate service layer responsibilities.

:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=130 path=app/src/main/java/org/ole/planet/myplanet/service/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/UserSessionManager.kt#L103-L130"}
:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L7-L14"}

:::task-stub{title="Move getOfflineVisits to ActivitiesRepository"}
1. Add getOfflineVisitCount(userId: String): Int to ActivitiesRepository interface
2. Implement in ActivitiesRepositoryImpl using queryList with equalTo filters
3. Inject ActivitiesRepository into UserSessionManager
4. Replace direct realm query with repository call in getOfflineVisits()
5. Remove realm import from UserSessionManager if no longer needed
:::

### 4. Convert ConfigurationsRepository checkHealth to suspend function

ConfigurationsRepository uses callback-based patterns (OnSuccessListener) instead of suspend functions, making it inconsistent with the coroutine-based codebase and harder to compose/test.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt#L8-L22"}
:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L45-L80"}

:::task-stub{title="Convert checkHealth to suspend function"}
1. Change interface signature to suspend fun checkHealth(): Result<Boolean>
2. Update implementation to use suspendCancellableCoroutine or direct suspend call
3. Return Result.success(true) or Result.failure(exception)
4. Update callers to use lifecycleScope.launch with result handling
5. Remove OnSuccessListener callback pattern from this method
:::

### 5. Fix ResourcesRepository Realm parameter leak in isResourceOpened

ResourcesRepository.isResourceOpened() takes a Realm parameter directly, leaking implementation details to callers. The method signature should hide Realm internals.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L41-L43"}

:::task-stub{title="Remove Realm parameter from isResourceOpened"}
1. Change interface signature to fun isResourceOpened(resourceId: String): Boolean
2. Update implementation to obtain Realm instance internally via RealmRepository
3. Use withRealm or queryList from base RealmRepository class
4. Update all callers to remove mRealm parameter
5. Verify no other repository methods expose Realm parameters
:::

### 6. Add proper executor cleanup in SyncManager destroy method

SyncManager creates a single-threaded executor converted to CoroutineDispatcher but never shuts it down. This causes thread leaks when SyncManager is destroyed.

:codex-file-citation[codex-file-citation]{line_range_start=76 line_range_end=82 path=app/src/main/java/org/ole/planet/myplanet/service/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/sync/SyncManager.kt#L76-L82"}
:codex-file-citation[codex-file-citation]{line_range_start=145 line_range_end=155 path=app/src/main/java/org/ole/planet/myplanet/service/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/sync/SyncManager.kt#L145-L155"}

:::task-stub{title="Add syncDispatcher cleanup in SyncManager"}
1. Add syncDispatcher.close() call in destroy() method before listener cleanup
2. Wrap close() in try-catch to handle potential exceptions
3. Set listener to null after close to prevent callbacks to dead dispatcher
4. Consider using Dispatchers.IO instead of custom executor for simplicity
5. Add unit test verifying no thread leaks after destroy
:::

### 7. Delegate UploadManager.uploadMyPersonal to PersonalsRepository

UploadManager.uploadMyPersonal() contains direct Realm queries for RealmMyPersonal that should use the existing PersonalsRepository. This separates upload orchestration from data access.

:codex-file-citation[codex-file-citation]{line_range_start=356 line_range_end=400 path=app/src/main/java/org/ole/planet/myplanet/service/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/UploadManager.kt#L356-L400"}
:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt#L7-L20"}

:::task-stub{title="Move uploadMyPersonal query to PersonalsRepository"}
1. Add getPendingPersonalUploads(userId: String): List<RealmMyPersonal> to PersonalsRepository
2. Add updatePersonalAfterSync(id: String, newId: String, rev: String) to interface
3. Implement both methods in PersonalsRepositoryImpl
4. Inject PersonalsRepository into UploadManager
5. Replace direct realm query with repository calls in uploadMyPersonal()
:::

### 8. Fix RealmRepository queryListFlow listener cleanup on cancellation

RealmRepository.queryListFlow() adds a RealmChangeListener that may not be properly removed if the Flow is cancelled before awaitClose is reached, potentially leaking Realm instances.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L48-L73"}

:::task-stub{title="Fix RealmRepository Flow listener cleanup"}
1. Move realm.close() and removeChangeListener to awaitClose block exclusively
2. Add invokeOnCancellation to handle early cancellation scenarios
3. Ensure listener removal happens before realm.close()
4. Add isClosedOrInTransaction check before operations
5. Test with rapid Flow collection cancellation
:::

### 9. Extract logCourseVisit from CoursesRepository to ActivitiesRepository

CoursesRepository.logCourseVisit() is activity tracking that belongs in ActivitiesRepository. This cross-feature method creates coupling between course data access and activity logging.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L38-L41"}
:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L7-L14"}

:::task-stub{title="Move logCourseVisit to ActivitiesRepository"}
1. Add logCourseVisit(courseId: String, title: String, userId: String) to ActivitiesRepository
2. Move implementation from CoursesRepositoryImpl to ActivitiesRepositoryImpl
3. Inject ActivitiesRepository into course-related UI components that log visits
4. Remove logCourseVisit from CoursesRepository interface
5. Update CoursesRepositoryImpl to remove the method
:::

### 10. Add proper error handling to ExamSubmissionUtils executeTransactionAsync

ExamSubmissionUtils.kt uses executeTransactionAsync with empty success/error handlers, silently ignoring failures. This makes debugging difficult and can cause data inconsistency.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/utilities/ExamSubmissionUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/ExamSubmissionUtils.kt#L24-L54"}

:::task-stub{title="Add error handling to ExamSubmissionUtils transactions"}
1. Replace empty error callback with Log.e for exception details
2. Add success callback with completion indication if needed
3. Consider converting to DatabaseService.executeTransactionAsync suspend function
4. Propagate errors to caller via callback or Result type
5. Add Crashlytics or analytics logging for production error tracking
:::

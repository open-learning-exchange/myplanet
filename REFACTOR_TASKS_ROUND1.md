# Refactor Tasks - Repository Boundaries & Data Layer Cleanup

## Round 1: Low-Hanging Fruits (10 Tasks)

---

### 1. Move VoicesActions Direct Realm Queries to Repository

VoicesActions is a UI-layer object that directly instantiates Realm and performs transactions. This violates repository boundaries and makes testing impossible. The `showEditAlert` and related methods should delegate data operations to VoicesRepository.

:codex-file-citation[codex-file-citation]{line_range_start=167 line_range_end=185 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L167-L185"}

:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=206 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L187-L206"}

:codex-file-citation[codex-file-citation]{line_range_start=208 line_range_end=232 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L208-L232"}

:::task-stub{title="Move VoicesActions data operations to VoicesRepository"}
1. Add `getNewsById(id: String)` method to VoicesRepository interface
2. Add `createReply(...)` method to VoicesRepository interface
3. Add `updateNewsMessage(...)` method to VoicesRepository interface
4. Implement methods in VoicesRepositoryImpl using RealmRepository pattern
5. Refactor VoicesActions to accept repository via parameter injection
6. Remove direct Realm.getDefaultInstance() calls from VoicesActions
:::

---

### 2. Remove Realm Dependency from ResourcesAdapter

ResourcesAdapter receives a Realm instance as constructor parameter, violating adapter layer separation. Adapters should receive pre-processed data, not database access objects. The `isResourceOpened` check should happen before data reaches the adapter.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L41-L49"}

:codex-file-citation[codex-file-citation]{line_range_start=131 line_range_end=136 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L131-L136"}

:::task-stub{title="Remove Realm parameter from ResourcesAdapter constructor"}
1. Add `openedResourceIds: Set<String>` parameter to ResourcesAdapter constructor
2. Remove `mRealm: Realm` parameter from constructor
3. Move `isResourceOpened` check to ResourcesFragment before creating adapter
4. Update ResourcesFragment to pre-compute opened resource IDs set
5. Update adapter binding to use the pre-computed set instead of Realm query
:::

---

### 3. Move AddExaminationActivity Data Operations to HealthRepository

AddExaminationActivity directly manages Realm transactions for health examination data. All data operations including queries, creates, and updates should be routed through HealthRepository.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L84-L96"}

:codex-file-citation[codex-file-citation]{line_range_start=236 line_range_end=244 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L236-L244"}

:codex-file-citation[codex-file-citation]{line_range_start=246 line_range_end=295 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L246-L295"}

:::task-stub{title="Extract AddExaminationActivity data ops to HealthRepository"}
1. Create HealthRepository interface with examination CRUD methods
2. Add `getExaminationById(id: String)` method
3. Add `getUserById(id: String)` method returning user with key/iv
4. Add `saveExamination(...)` method encapsulating transaction logic
5. Inject HealthRepository into AddExaminationActivity
6. Replace direct mRealm calls with repository methods
:::

---

### 4. Replace Direct DataService Instantiation in BecomeMemberActivity

BecomeMemberActivity directly instantiates DataService bypassing Hilt DI. This creates untestable code and violates DI boundaries. The user creation should go through UserRepository.

:codex-file-citation[codex-file-citation]{line_range_start=143 line_range_end=155 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L143-L155"}

:::task-stub{title="Replace DataService instantiation with injected repository"}
1. Add `createMember(...)` method to UserRepository interface
2. Implement method in UserRepositoryImpl using existing DataService logic
3. Add `@Inject lateinit var userRepository: UserRepository` to BecomeMemberActivity
4. Replace `DataService(this).becomeMember(...)` with `userRepository.createMember(...)`
5. Use lifecycleScope.launch for async handling instead of callbacks
:::

---

### 5. Move BaseDashboardFragment MyLife Setup to Repository

BaseDashboardFragment directly creates Realm objects for MyLife setup. This data initialization logic belongs in a repository, not the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=271 line_range_end=285 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BaseDashboardFragment.kt#L271-L285"}

:codex-file-citation[codex-file-citation]{line_range_start=291 line_range_end=309 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BaseDashboardFragment.kt#L291-L309"}

:::task-stub{title="Move MyLife initialization to dedicated repository"}
1. Create MyLifeRepository interface with `getMyLifeForUser(userId)` method
2. Add `initializeMyLifeIfNeeded(userId, items)` method to interface
3. Implement in MyLifeRepositoryImpl extending RealmRepository
4. Move `getMyLifeListBase()` logic to repository
5. Replace direct realm.createObject calls with repository method
6. Inject MyLifeRepository in BaseDashboardFragment
:::

---

### 6. Move TakeCourseFragment Survey Check to Repository

TakeCourseFragment directly queries RealmStepExam for survey completion checks. This cross-feature data access should go through SurveysRepository.

:codex-file-citation[codex-file-citation]{line_range_start=392 line_range_end=423 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L392-L423"}

:::task-stub{title="Move survey completion check to SurveysRepository"}
1. Add `getSurveysForStep(stepId: String)` method to SurveysRepository
2. Add `hasUncompletedSurveys(steps, courseId, userId)` method to interface
3. Implement methods in SurveysRepositoryImpl
4. Replace direct mRealm.where(RealmStepExam) query with repository call
5. Remove mRealm.copyFromRealm call from fragment
:::

---

### 7. Move ExamTakingFragment Transaction to Repository

ExamTakingFragment manages submission creation with manual Realm transaction handling. The `createSubmission` method should be in SubmissionsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=245 line_range_end=269 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L245-L269"}

:codex-file-citation[codex-file-citation]{line_range_start=99 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L99-L99"}

:::task-stub{title="Move submission creation to SubmissionsRepository"}
1. Add `createExamSubmission(exam, user, type, teamId)` to SubmissionsRepository
2. Move transaction logic and object creation to repository implementation
3. Add `isCourseCertified(courseId)` method to CoursesRepository
4. Replace direct mRealm.beginTransaction/commitTransaction with repository call
5. Replace static isCourseCertified call with repository method
:::

---

### 8. Move BaseResourceFragment Library Operations to Repository

BaseResourceFragment contains `addToLibrary` and `addAllToLibrary` methods with manual Realm transaction handling. These data operations should be in ResourcesRepository.

:codex-file-citation[codex-file-citation]{line_range_start=419 line_range_end=447 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L419-L447"}

:codex-file-citation[codex-file-citation]{line_range_start=449 line_range_end=476 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L449-L476"}

:::task-stub{title="Move library add operations to ResourcesRepository"}
1. Add `addResourcesToUserLibrary(resourceIds, userId)` to ResourcesRepository
2. Add `addAllResourcesToUserLibrary(resources, userId)` to ResourcesRepository
3. Implement methods in ResourcesRepositoryImpl with proper transaction handling
4. Replace manual mRealm.beginTransaction/commitTransaction with repository calls
5. Remove direct RealmRemovedLog.onAdd calls from fragment
:::

---

### 9. Add Proper Scope Cancellation to AutoSyncWorker

AutoSyncWorker creates a CoroutineScope but may not properly cancel it in all termination paths. The onStopped override exists but scope lifecycle should be more robust.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt#L38-L38"}

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=102 path=app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt#L84-L102"}

:codex-file-citation[codex-file-citation]{line_range_start=112 line_range_end=115 path=app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt#L112-L115"}

:::task-stub{title="Ensure AutoSyncWorker scope is properly cancelled"}
1. Add SupervisorJob to workerScope for better failure isolation
2. Wrap workerScope.launch block in try-catch for cleanup on errors
3. Add explicit isStopped check before launching coroutines
4. Ensure workerScope.cancel() is called in all termination paths
:::

---

### 10. Replace Direct DataService Calls in BaseResourceFragment

BaseResourceFragment creates DataService instances directly in multiple places for planet availability checks. These should use injected services.

:codex-file-citation[codex-file-citation]{line_range_start=210 line_range_end=226 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L210-L226"}

:codex-file-citation[codex-file-citation]{line_range_start=228 line_range_end=242 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L228-L242"}

:codex-file-citation[codex-file-citation]{line_range_start=529 line_range_end=539 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L529-L539"}

:::task-stub{title="Replace DataService instantiation with injected dependency"}
1. Add `@Inject lateinit var dataService: DataService` to BaseResourceFragment
2. Replace `DataService(requireContext())` with injected `dataService`
3. Move static `backgroundDownload` method logic to ResourcesRepository
4. Update companion object to not require DataService instantiation
:::

---

## Summary

| Task | Focus Area | Risk Level | Files Changed |
|------|------------|------------|---------------|
| 1 | Repository boundary | Low | 2 files |
| 2 | Adapter layer separation | Low | 2 files |
| 3 | Repository boundary | Medium | 2-3 files |
| 4 | DI cleanup | Low | 2 files |
| 5 | Repository boundary | Low | 2-3 files |
| 6 | Cross-feature data access | Low | 2 files |
| 7 | Repository boundary | Medium | 2 files |
| 8 | Repository boundary | Low | 2 files |
| 9 | Threading/dispatchers | Low | 1 file |
| 10 | DI cleanup | Low | 1 file |

All tasks are designed to be:
- Granular and focused on single concerns
- Low merge-conflict risk (isolated changes)
- Easily reviewable (small file footprint)
- Following existing patterns (RealmRepository, DiffUtils.itemCallback)

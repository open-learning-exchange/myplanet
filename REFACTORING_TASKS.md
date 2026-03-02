# Repository Boundary Reinforcement Tasks

## Overview
This document outlines 10 low-hanging fruit tasks to reinforce repository boundaries, eliminate cross-feature data leaks, and move data logic from UI/service layers into repositories.

---

### Task 1: Remove Direct Realm Access from BaseResourceFragment

BaseResourceFragment exposes protected mRealm field to 20+ fragment subclasses enabling direct database access, violating repository pattern and creating tight coupling to Realm implementation.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L59-L59"}

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L54-L55"}

:codex-file-citation[codex-file-citation]{line_range_start=65 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L65-L65"}

:::task-stub{title="Remove mRealm from BaseResourceFragment"}
1. Identify all mRealm usage in BaseResourceFragment methods
2. Replace with injected ResourcesRepository method calls
3. Remove protected lateinit var mRealm: Realm declaration
4. Update subclass fragments that relied on inherited mRealm access
5. Test affected fragments (CoursesFragment, ResourcesFragment, AddResourceFragment)
:::

---

### Task 2: Eliminate Cross-Feature Repository Access in TakeCourseFragment

TakeCourseFragment directly injects ActivitiesRepository, ProgressRepository, and SubmissionsRepository instead of accessing through CoursesRepository facade, creating tight cross-feature coupling.

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L51-L51"}

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L49-L49"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L47-L47"}

:::task-stub{title="Add CoursesRepository facade methods"}
1. Add getCourseActivities() method to CoursesRepository interface
2. Add getCourseProgress() method to CoursesRepository interface  
3. Add getCourseSubmissions() method to CoursesRepository interface
4. Implement methods in CoursesRepositoryImpl delegating to respective repositories
5. Update TakeCourseFragment to only inject CoursesRepository
6. Replace direct repository calls with CoursesRepository facade methods
:::

---

### Task 3: Eliminate Cross-Feature Repository Access in CoursesFragment

CoursesFragment directly injects TagsRepository, ProgressRepository, and RatingsRepository instead of accessing through CoursesRepository, violating single responsibility.

:codex-file-citation[codex-file-citation]{line_range_start=93 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L93-L93"}

:codex-file-citation[codex-file-citation]{line_range_start=96 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L96-L96"}

:codex-file-citation[codex-file-citation]{line_range_start=99 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L99-L99"}

:::task-stub{title="Consolidate course-related operations in CoursesRepository"}
1. Add getCourseTags() method to CoursesRepository interface
2. Add getCourseRatings() method to CoursesRepository interface
3. Add getCourseProgressSummary() method to CoursesRepository interface
4. Implement delegation methods in CoursesRepositoryImpl
5. Remove TagsRepository, ProgressRepository, RatingsRepository injections from CoursesFragment
6. Update CoursesFragment to use consolidated CoursesRepository methods
:::

---

### Task 4: Eliminate Cross-Feature Repository Access in ResourcesFragment

ResourcesFragment directly injects RatingsRepository and TagsRepository instead of accessing through ResourcesRepository, creating unnecessary coupling.

:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=89 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L89-L89"}

:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=92 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L92-L92"}

:::task-stub{title="Add resource metadata methods to ResourcesRepository"}
1. Add getResourceRatings(resourceId) method to ResourcesRepository interface
2. Add getResourceTags(resourceId) method to ResourcesRepository interface
3. Implement methods in ResourcesRepositoryImpl delegating to RatingsRepository and TagsRepository
4. Remove RatingsRepository and TagsRepository injections from ResourcesFragment
5. Update ResourcesFragment to use ResourcesRepository facade methods
6. Test resource rating and tag filtering functionality
:::

---

### Task 5: Move Realm CRUD Operations from RetryQueue to RetryOperationRepository

RetryQueue service class contains complete CRUD operations for RealmRetryOperation, violating single responsibility and mixing service/repository concerns.

:codex-file-citation[codex-file-citation]{line_range_start=50 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L50-L55"}

:codex-file-citation[codex-file-citation]{line_range_start=125 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L125-L126"}

:codex-file-citation[codex-file-citation]{line_range_start=135 line_range_end=136 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L135-L136"}

:codex-file-citation[codex-file-citation]{line_range_start=174 line_range_end=176 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L174-L176"}

:::task-stub{title="Create RetryOperationRepository and refactor RetryQueue"}
1. Create RetryOperationRepository interface with CRUD methods
2. Create RetryOperationRepositoryImpl extending RealmRepository
3. Move queueFailedOperation, markInProgress, markCompleted, markFailed to repository
4. Move safeClearQueue and recoverStuckOperations to repository
5. Inject RetryOperationRepository into RetryQueue service
6. Update RetryQueue to delegate database operations to repository
7. Update Hilt modules to provide RetryOperationRepository
:::

---

### Task 6: Move User and Health CRUD from UploadToShelfService to Repositories

UploadToShelfService performs direct Realm CRUD operations for RealmUser and RealmHealthExamination instead of delegating to repositories.

:codex-file-citation[codex-file-citation]{line_range_start=203 line_range_end=204 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L203-L204"}

:codex-file-citation[codex-file-citation]{line_range_start=224 line_range_end=224 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L224-L224"}

:codex-file-citation[codex-file-citation]{line_range_start=274 line_range_end=275 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L274-L275"}

:::task-stub{title="Delegate UploadToShelfService operations to repositories"}
1. Add updateUserEncryption(userId, keyIv) method to UserRepository interface
2. Implement updateUserEncryption in UserRepositoryImpl
3. Add updateHealthData(userId) method to existing or new HealthRepository
4. Update UploadToShelfService to inject UserRepository
5. Replace direct Realm queries in updateExistingUser with UserRepository calls
6. Replace direct Realm queries in updateHealthData with HealthRepository calls
7. Replace saveKeyIv direct Realm access with UserRepository.updateUserEncryption
:::

---

### Task 7: Replace Manual SharedPrefManager Instantiation with Dependency Injection

SharedPrefManager is manually instantiated in 10+ UI classes using SharedPrefManager(context) instead of Hilt injection, causing code duplication and testability issues.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L56-L56"}

:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=92 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L92-L92"}

:codex-file-citation[codex-file-citation]{line_range_start=117 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt#L117-L117"}

:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L61-L61"}

:::task-stub{title="Inject SharedPrefManager via Hilt in UI classes"}
1. Verify SharedPrefManager is provided in SharedPreferencesModule
2. Add @Inject lateinit var sharedPrefManager: SharedPrefManager to DashboardElementActivity
3. Replace prefData = SharedPrefManager(this) with injected instance
4. Repeat for ChatHistoryFragment, TeamDetailFragment, EnterprisesReportsFragment
5. Repeat for MyHealthFragment, FeedbackListFragment, SurveyFragment, AchievementFragment
6. Repeat for OnboardingActivity and SyncActivity
7. Remove all manual SharedPrefManager(context) instantiations
:::

---

### Task 8: Fix Lifecycle-Unsafe Flow Collection in SyncActivity

SyncActivity collects broadcastService.events using lifecycleScope.launch without repeatOnLifecycle wrapper, causing potential memory leaks when activity stops.

:codex-file-citation[codex-file-citation]{line_range_start=775 line_range_end=781 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L775-L781"}

:::task-stub{title="Add repeatOnLifecycle to SyncActivity Flow collection"}
1. Import androidx.lifecycle.repeatOnLifecycle
2. Wrap broadcastService.events.collect with repeatOnLifecycle(Lifecycle.State.STARTED)
3. Change lifecycleScope.launch to use structured concurrency pattern
4. Test that collection stops when activity moves to stopped state
5. Verify no memory leaks with Android Profiler
:::

---

### Task 9: Fix Lifecycle-Unsafe Flow Collection in BaseResourceFragment

BaseResourceFragment collects broadcastService.events using lifecycleScope.launch without repeatOnLifecycle, affecting 20+ fragment subclasses with potential memory leaks.

:codex-file-citation[codex-file-citation]{line_range_start=359 line_range_end=370 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L359-L370"}

:::task-stub{title="Add repeatOnLifecycle to BaseResourceFragment Flow collection"}
1. Import androidx.lifecycle.repeatOnLifecycle in BaseResourceFragment
2. Wrap broadcastService.events.collect with repeatOnLifecycle(Lifecycle.State.STARTED)
3. Change viewLifecycleOwner.lifecycleScope.launch to use structured pattern
4. Test with at least 3 subclass fragments (CoursesFragment, ResourcesFragment, AddResourceFragment)
5. Verify collection stops during fragment lifecycle transitions
:::

---

### Task 10: Remove Unnecessary withContext in ConfigurationsRepositoryImpl

ConfigurationsRepositoryImpl uses multiple withContext(Dispatchers.Main) calls in methods that are already called from Main context, causing unnecessary context switching overhead.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L54-L54"}

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L60-L71"}

:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=85 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L81-L85"}

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L97-L97"}

:::task-stub{title="Remove unnecessary withContext(Dispatchers.Main) calls"}
1. Identify all withContext(Dispatchers.Main) calls in ConfigurationsRepositoryImpl
2. Trace call sites to verify they are called from Main context (lifecycleScope)
3. Remove withContext(Dispatchers.Main) wrapper from lines 54, 60-71, 81, 85, 97
4. Add withContext(Dispatchers.IO) to serviceScope.launch blocks at lines 50 and 96
5. Test configuration sync operations to ensure UI updates still work
6. Verify no ANR issues after removing Main dispatcher switches
:::

---

## Testing Section

### General Testing Approach
Each task should be tested in isolation with the following steps:

1. **Build verification**: Run `./gradlew assembleDefaultDebug` to ensure no compilation errors
2. **Unit test verification**: Run affected unit tests if available
3. **Manual smoke test**: Test the affected feature in the app to ensure no regressions
4. **Memory leak check**: Use Android Profiler to verify no new memory leaks (especially for lifecycle-related tasks)

### Specific Test Cases

**For Tasks 1-4 (Repository boundary tasks)**:
- Verify affected screens load data correctly
- Test data filtering and search functionality
- Verify offline functionality still works

**For Task 5-6 (Service to Repository migration)**:
- Test retry queue functionality after failed sync
- Test upload operations to ensure data saves correctly
- Verify health examination updates work properly

**For Task 7 (Dependency Injection)**:
- Verify all affected screens still function correctly
- Test that preferences are persisted and loaded properly
- Run Instrumentation tests if available

**For Tasks 8-9 (Lifecycle-aware collection)**:
- Rotate device during data loading to verify no crashes
- Navigate away and back to verify collection resumes properly
- Check logcat for no IllegalStateException

**For Task 10 (Threading optimization)**:
- Measure performance with Android Profiler before/after
- Verify no UI jank during configuration sync
- Test both online and offline scenarios

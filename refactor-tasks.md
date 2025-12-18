# Data Layer Refactoring Tasks

## Low-Hanging Fruit for Repository Boundary Reinforcement

### Task 1: Move user search from UI to UserRepository

MyHealthFragment directly queries the Realm database in a TextWatcher using `databaseService.withRealm` to search users by name. This violates repository boundaries and couples the UI layer to database implementation details. The UserRepository already exists and should own all user query logic.

:codex-file-citation[codex-file-citation]{line_range_start=354 line_range_end=361 path=app/src/main/java/org/ole/planet/myplanet/ui/myhealth/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/myhealth/MyHealthFragment.kt#L354-L361"}

:::task-stub{title="Extract user search to UserRepository"}
1. Add `suspend fun searchUsers(query: String, sortField: String, sortOrder: Sort): List<RealmUserModel>` to UserRepository interface
2. Implement method in UserRepositoryImpl with the Realm query logic from lines 355-360
3. Replace lines 354-361 in MyHealthFragment with call to `userRepository.searchUsers(editable.toString(), "joinDate", Sort.DESCENDING)`
4. Remove `databaseService` injection from MyHealthFragment (line 71)
5. Remove `mRealm` field declaration from MyHealthFragment (line 81)
:::

### Task 2: Replace direct database access with SubmissionRepository

SubmissionListFragment bypasses the existing SubmissionRepository and directly queries Realm using `databaseService.withRealm`. This creates unnecessary coupling between UI and data layers when a proper repository interface already exists.

:codex-file-citation[codex-file-citation]{line_range_start=78 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/ui/submission/SubmissionListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/submission/SubmissionListFragment.kt#L78-L97"}

:::task-stub{title="Use SubmissionRepository for submission queries"}
1. Add `suspend fun getSubmissionsByParentId(parentId: String, userId: String): List<RealmSubmission>` to SubmissionRepository interface
2. Implement method in SubmissionRepositoryImpl using the query logic from lines 82-86 and realm.copyFromRealm at line 96
3. Inject SubmissionRepository into SubmissionListFragment
4. Replace lines 78-97 with simple call to `submissionRepository.getSubmissionsByParentId(parentId, userId)`
5. Remove `databaseService` injection from fragment
:::

### Task 3: Move checksum validation from Service to ConfigurationRepository

The `checkCheckSum()` method in Service.kt belongs in ConfigurationRepository as it validates server configuration integrity. Service.kt should not contain business logic for configuration validation.

:codex-file-citation[codex-file-citation]{line_range_start=136 line_range_end=154 path=app/src/main/java/org/ole/planet/myplanet/datamanager/Service.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/datamanager/Service.kt#L136-L154"}

:::task-stub{title="Migrate checkCheckSum to ConfigurationRepository"}
1. Add `suspend fun validateResourceChecksum(path: String?): Boolean` to ConfigurationRepository interface
2. Implement method in ConfigurationRepositoryImpl by copying logic from Service.kt lines 137-153
3. Update all callers of `Service.checkCheckSum()` to use `configurationRepository.validateResourceChecksum()`
4. Mark `Service.checkCheckSum()` as deprecated with message pointing to new method
5. Remove deprecated method after confirming no usages remain
:::

### Task 4: Convert AdapterTeamCourse to ListAdapter with DiffUtil

AdapterTeamCourse manually manages its list and uses `notifyDataSetChanged()` instead of using ListAdapter with DiffUtil. The codebase already has established patterns in AdapterTeamList, AdapterTeamTask, and AdapterMeetup that should be followed.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/ui/team/teamCourse/AdapterTeamCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/team/teamCourse/AdapterTeamCourse.kt#L18-L66"}

:::task-stub{title="Migrate AdapterTeamCourse to ListAdapter"}
1. Change class declaration to extend `ListAdapter<RealmMyCourse, ViewHolderTeamCourse>(CourseDiffCallback())`
2. Create `CourseDiffCallback` class implementing `DiffUtil.ItemCallback<RealmMyCourse>` with `areItemsTheSame` checking `courseId` equality
3. Remove `list` parameter from constructor and remove manual list management
4. Change `onBindViewHolder` to use `getItem(position)` instead of `list[position]`
5. Replace all external calls to adapter constructor with calls to `submitList()`
6. Remove `getList()` method as ListAdapter provides `currentList`
:::

### Task 5: Remove Realm injection from AdapterSurvey constructor

AdapterSurvey accepts a Realm instance in its constructor, leaking database implementation details into the UI layer. Adapters should never directly handle Realm instances and should use repositories for any data operations.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/AdapterSurvey.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/survey/AdapterSurvey.kt#L28-L39"}

:::task-stub{title="Remove Realm parameter from AdapterSurvey"}
1. Remove `mRealm: Realm` parameter from AdapterSurvey constructor at line 30
2. Find all Realm query usages within adapter methods and identify what data they retrieve
3. Create repository methods for any data operations found (or use existing ones)
4. Inject required repositories into adapter constructor
5. Update all callers instantiating AdapterSurvey to remove Realm argument
6. Remove any `mRealm` field references within the adapter
:::

### Task 6: Remove setmRealm method from AdapterNews

AdapterNews has a `setmRealm()` method allowing external code to inject Realm instances, violating data layer boundaries. The adapter already has repositories injected and should use those exclusively for data access.

:codex-file-citation[codex-file-citation]{line_range_start=98 line_range_end=109 path=app/src/main/java/org/ole/planet/myplanet/ui/news/AdapterNews.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/news/AdapterNews.kt#L98-L109"}

:::task-stub{title="Eliminate Realm setter from AdapterNews"}
1. Locate `setmRealm(realm: Realm)` method in AdapterNews
2. Find all callers of `setmRealm()` and identify what Realm operations happen after setting
3. Replace Realm operations with equivalent repository method calls using injected repositories at lines 99-109
4. Remove `lateinit var mRealm: Realm` field declaration at line 102
5. Remove `setmRealm()` method entirely
6. Update ReplyActivity line 100-110 to not call `setmRealm()`
:::

### Task 7: Move getCurrentProgress logic to CourseRepository

TakeCourseFragment calls the static method `RealmCourseProgress.getCurrentProgress()` which performs direct Realm queries. This business logic belongs in CourseRepository to maintain proper layer separation and enable testability.

:codex-file-citation[codex-file-citation]{line_range_start=174 line_range_end=197 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L174-L197"}

:::task-stub{title="Extract getCurrentProgress to CourseRepository"}
1. Add `suspend fun getCurrentProgress(steps: List<RealmCourseStep>, userId: String?, courseId: String?): Int` to CourseRepository interface
2. Implement in CourseRepositoryImpl by moving logic from RealmCourseProgress companion object
3. Replace static call at line 174 with `courseRepository.getCurrentProgress(steps, userModel?.id, courseId)`
4. Replace static call at line 193 with repository call wrapped in coroutine
5. Mark `RealmCourseProgress.getCurrentProgress()` as deprecated
6. Update any other files calling the static method to use repository
:::

### Task 8: Extract exam initialization to ExamRepository

BaseExamFragment directly queries Realm in `initExam()` to fetch exam data. This couples all exam-related fragments to database implementation and should be moved to a dedicated ExamRepository following the established repository pattern.

:codex-file-citation[codex-file-citation]{line_range_start=105 line_range_end=111 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/BaseExamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/exam/BaseExamFragment.kt#L105-L111"}

:::task-stub{title="Create ExamRepository and migrate initExam"}
1. Create `ExamRepository` interface in repository package
2. Add `suspend fun getExamByStepId(stepId: String): RealmStepExam?` method
3. Add `suspend fun getExamById(id: String): RealmStepExam?` method
4. Create `ExamRepositoryImpl` extending `RealmRepository` with both methods implemented
5. Inject `ExamRepository` into BaseExamFragment
6. Replace `initExam()` logic at lines 106-110 with repository call
7. Remove `databaseService` and `mRealm` from BaseExamFragment
8. Add DI binding in RepositoryModule
:::

### Task 9: Move library offline marking to LibraryRepository

SettingActivity directly executes a Realm transaction to mark all library resources offline. This data operation belongs in LibraryRepository to maintain consistent data access patterns and enable proper transaction management.

:codex-file-citation[codex-file-citation]{line_range_start=216 line_range_end=221 path=app/src/main/java/org/ole/planet/myplanet/ui/SettingActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/SettingActivity.kt#L216-L221"}

:::task-stub{title="Add markAllResourcesOffline to LibraryRepository"}
1. Add `suspend fun markAllResourcesOffline(isOffline: Boolean)` to LibraryRepository interface
2. Implement in LibraryRepositoryImpl using executeTransactionAsync with logic from lines 217-220
3. Inject LibraryRepository into SettingActivity (may already be present)
4. Replace lines 216-221 with call to `libraryRepository.markAllResourcesOffline(false)`
5. Remove direct `databaseService` usage for this operation
6. Keep file deletion logic at lines 222-225 as-is since it's filesystem operation
:::

### Task 10: Enhance UserRepository with sort parameter

MyHealthFragment performs complex user searches with sorting that could be simplified by expanding UserRepository's interface. The existing `getUsersSortedBy` method exists but isn't used, and search functionality should support sorting natively.

:codex-file-citation[codex-file-citation]{line_range_start=354 line_range_end=361 path=app/src/main/java/org/ole/planet/myplanet/ui/myhealth/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/ui/myhealth/MyHealthFragment.kt#L354-L361"}

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-daumT/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L10-L14"}

:::task-stub{title="Add searchUsersSorted to UserRepository interface"}
1. Add `suspend fun searchUsersSorted(query: String, sortField: String, sortOrder: Sort): List<RealmUserModel>` to UserRepository interface after line 14
2. Implement in UserRepositoryImpl combining search (firstName/lastName/name contains) and sorting logic
3. Update MyHealthFragment to use `userRepository.searchUsersSorted(query, "joinDate", Sort.DESCENDING)` at line 354
4. Remove direct Realm access from lines 354-361
5. Remove `databaseService` and `mRealm` dependencies from fragment
:::

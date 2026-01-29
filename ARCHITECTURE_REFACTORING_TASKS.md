# Architecture Refactoring Tasks

This document outlines 10 specific, granular refactoring tasks to improve the myPlanet codebase architecture. Each task focuses on reinforcing repository boundaries, eliminating cross-layer data leaks, and moving data logic from UI/service layers into repositories.

---

### Move direct Realm queries from BaseRecyclerFragment to CoursesRepository

BaseRecyclerFragment contains multiple direct `mRealm.where()` queries for course deletion and tag operations that bypass the repository layer. These operations should be delegated to CoursesRepository to maintain separation of concerns and enable proper testing.

:codex-file-citation[codex-file-citation]{line_range_start=226 line_range_end=245 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L226-L245"}

:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=254 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L248-L254"}

:::task-stub{title="Refactor BaseRecyclerFragment to use CoursesRepository"}
1. Add deleteCourseProgress(courseId: String) method to CoursesRepository interface
2. Implement the method in CoursesRepositoryImpl with proper transaction handling
3. Add getTagCount(tagId: String, linkId: String?) method to TagsRepository
4. Replace mRealm.where() calls in deleteCourseProgress() with repository.deleteCourseProgress()
5. Replace mRealm.where() calls in checkAndAddToList() with repository.getTagCount()
6. Test that course deletion still works correctly through repository layer
:::

---

### Migrate RealmUser companion object queries to UsersRepository

RealmUser contains static companion methods that directly access Realm for user queries. This violates repository pattern and creates tight coupling between the model layer and data access logic.

:codex-file-citation[codex-file-citation]{line_range_start=278 line_range_end=299 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt#L278-L299"}

:codex-file-citation[codex-file-citation]{line_range_start=436 line_range_end=437 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt#L436-L437"}

:codex-file-citation[codex-file-citation]{line_range_start=493 line_range_end=494 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt#L493-L494"}

:::task-stub{title="Extract RealmUser companion methods to UsersRepository"}
1. Add findUserById(userId: String) method to UsersRepository interface
2. Add findGuestUser(userName: String) method to UsersRepository interface
3. Add getAllUsers() method to UsersRepository interface
4. Move the query logic from RealmUser companion object to UsersRepositoryImpl
5. Update all call sites to use repository methods instead of RealmUser companion methods
6. Verify login and user management flows work correctly
:::

---

### Replace deprecated mRealm property usage in BaseResourceFragment with repository calls

BaseResourceFragment uses the deprecated `databaseService.realmInstance` property exposing Realm directly to fragments. This should be replaced with repository method calls to enforce proper layer boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L92-L96"}

:codex-file-citation[codex-file-citation]{line_range_start=371 line_range_end=371 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L371-L371"}

:codex-file-citation[codex-file-citation]{line_range_start=388 line_range_end=389 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L388-L389"}

:::task-stub{title="Remove direct Realm access from BaseResourceFragment"}
1. Add getResourceByResourceId(resourceId: String) method to ResourcesRepository
2. Update requireRealmInstance() to throw UnsupportedOperationException with migration message
3. Replace mRealm.where() call at line 388 with resourcesRepository.getResourceByResourceId()
4. Remove mRealm property initialization at lines 94 and 371
5. Update subclasses if they override or depend on mRealm property
6. Verify resource viewing and download flows work correctly
:::

---

### Move direct Realm access from AddExaminationActivity to HealthRepository

AddExaminationActivity performs direct Realm queries for health examination and user data. This activity should use HealthRepository and UsersRepository instead of accessing the database directly.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L84-L90"}

:codex-file-citation[codex-file-citation]{line_range_start=117 line_range_end=118 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L117-L118"}

:::task-stub{title="Refactor AddExaminationActivity to use repositories"}
1. Inject HealthRepository into AddExaminationActivity via Hilt
2. Add getHealthExaminationByUserId(userId: String) to HealthRepository
3. Add getHealthExaminationById(id: String) to HealthRepository
4. Replace mRealm.where() calls at lines 86-88 with repository.getHealthExaminationByUserId()
5. Replace mRealm.where() call at line 118 with repository.getHealthExaminationById()
6. Replace user query at line 90 with existing usersRepository method
7. Test health examination creation and editing flows
:::

---

### Convert TeamCoursesAdapter from RecyclerView.Adapter to ListAdapter with DiffUtil

TeamCoursesAdapter uses legacy RecyclerView.Adapter instead of ListAdapter with DiffUtil, causing inefficient UI updates. This should be converted to use the existing DiffUtils.itemCallback helper.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L17-L41"}

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L59-L61"}

:::task-stub{title="Convert TeamCoursesAdapter to ListAdapter with DiffUtil"}
1. Change TeamCoursesAdapter to extend ListAdapter<RealmMyCourse, ViewHolder>
2. Add DiffUtils.itemCallback comparing course IDs and titles in constructor
3. Remove list property and getItemCount() override (handled by ListAdapter)
4. Update onBindViewHolder to use getItem(position) instead of list[position]
5. Update TeamCoursesFragment to call adapter.submitList() instead of passing mutable list
6. Remove getList() method as it's no longer needed
7. Test that course list updates animate correctly
:::

---

### Replace direct Realm access in TeamCoursesFragment with CoursesRepository

TeamCoursesFragment performs direct `mRealm.where()` queries to fetch team courses. This should be delegated to CoursesRepository for proper layer separation.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L28-L32"}

:::task-stub{title="Refactor TeamCoursesFragment to use CoursesRepository"}
1. Add getCoursesByIds(courseIds: List<String>) method to CoursesRepository interface
2. Implement the method in CoursesRepositoryImpl using proper query with .in() operator
3. Replace mRealm.where() call at line 29 with viewLifecycleScope.launch and repository call
4. Update adapter initialization to work with coroutine result
5. Remove Realm parameter from TeamCoursesAdapter constructor
6. Verify team courses display correctly
:::

---

### Extract Realm queries from BaseRecyclerParentFragment to ResourcesRepository

BaseRecyclerParentFragment contains multiple direct `mRealm.where()` queries for surveys, libraries, and courses. These queries should be moved to appropriate repositories to enforce proper data layer boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L14-L32"}

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L56-L73"}

:codex-file-citation[codex-file-citation]{line_range_start=75 line_range_end=81 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L75-L81"}

:::task-stub{title="Move BaseRecyclerParentFragment queries to repositories"}
1. Add getSurveys() method to SurveysRepository interface
2. Add getPublicLibraries() method to ResourcesRepository interface
3. Make getList() and getMyLibItems() suspend functions
4. Replace mRealm.where() calls with appropriate repository methods using withContext
5. Update all subclass implementations to call getList() within coroutine scope
6. Remove direct Realm dependency from BaseRecyclerParentFragment
7. Test surveys, resources, and courses list display
:::

---

### Remove direct Realm.getDefaultInstance() usage from VoicesActions utility

VoicesActions uses direct `Realm.getDefaultInstance()` instead of DatabaseService, bypassing proper Realm lifecycle management. This should use the repository pattern for news operations.

:codex-file-citation[codex-file-citation]{line_range_start=167 line_range_end=172 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L167-L172"}

:::task-stub{title="Refactor VoicesActions to use NewsRepository"}
1. Create NewsRepository interface if it doesn't exist with getNewsById(id: String) method
2. Modify createPostDialog function to accept newsRepository parameter
3. Replace Realm.getDefaultInstance().use block with repository.getNewsById(id) call
4. Update all call sites to pass injected newsRepository instance
5. Remove direct Realm import from VoicesActions
6. Test news post editing and reply functionality
:::

---

### Move data operations from TransactionSyncManager to UsersRepository

TransactionSyncManager contains direct Realm access for user synchronization that should be delegated to UsersRepository. This will centralize user data operations and improve testability.

:codex-file-citation[codex-file-citation]{line_range_start=64 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L64-L65"}

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L97-L99"}

:codex-file-citation[codex-file-citation]{line_range_start=124 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L124-L125"}

:::task-stub{title="Delegate TransactionSyncManager user operations to UsersRepository"}
1. Inject UsersRepository into TransactionSyncManager constructor
2. Add getAllUsersWithIds() method to UsersRepository returning List<RealmUser>
3. Add updateUserLastSync(userId: String) method to UsersRepository
4. Replace databaseService.withRealm at line 64 with repository.getAllUsersWithIds()
5. Replace databaseService.executeTransactionAsync at line 97 with repository.updateUserLastSync()
6. Replace databaseService.withRealm at line 124 with repository.findUserById()
7. Verify user synchronization still works correctly
:::

---

### Migrate RealmMyTeam companion queries to TeamsRepository

RealmMyTeam contains several static companion methods with direct Realm access for team queries. These should be moved to TeamsRepository to centralize team data operations.

:codex-file-citation[codex-file-citation]{line_range_start=144 line_range_end=145 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L144-L145"}

:codex-file-citation[codex-file-citation]{line_range_start=231 line_range_end=238 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L231-L238"}

:codex-file-citation[codex-file-citation]{line_range_start=297 line_range_end=311 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L297-L311"}

:::task-stub{title="Extract RealmMyTeam companion methods to TeamsRepository"}
1. Add getTeamById(teamId: String) method to TeamsRepository interface
2. Add getTeamsByTeamId(teamId: String, userId: String) method to TeamsRepository
3. Add getTeamsByUserId(userId: String) method to TeamsRepository
4. Move query logic from RealmMyTeam companion object to TeamsRepositoryImpl
5. Update all call sites to use repository methods instead of companion methods
6. Remove mRealm parameter from getTeamCreator and similar helper methods
7. Verify team list display and team operations work correctly
:::

---

## Testing Section

### Validation Approach

Each task should be validated with the following steps:

1. **Build Verification**: Run `./gradlew assembleDefaultDebug` to ensure compilation succeeds
2. **Unit Tests**: Add repository unit tests for new methods if test infrastructure exists
3. **Manual Testing**: Verify the affected UI flows work correctly on device/emulator
4. **Code Review**: Ensure no new direct Realm access is introduced in modified files

### Incremental Testing Strategy

- Test each task independently before moving to the next
- Focus on one feature area at a time (e.g., all course-related tasks together)
- Use existing app functionality as integration tests
- Verify background sync and upload operations still function correctly

### Rollback Plan

If issues arise:
1. Each task is self-contained and can be reverted individually
2. Repository methods are additive, not replacing existing functionality
3. Keep original companion methods as deprecated until migration is complete

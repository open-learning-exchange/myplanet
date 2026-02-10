# Repository Boundary Refactoring Tasks

## Analysis Report: 10 Low-Hanging Fruit Tasks for Data Layer Cleanup

### Task 1: Move becomeMember data logic from BecomeMemberActivity to UserRepository

BecomeMemberActivity directly instantiates DataService to create user accounts, bypassing the repository layer. This logic should be extracted to UserRepository for better separation of concerns.

:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=158 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L146-L158"}

:::task-stub{title="Extract user creation from UI to UserRepository"}
1. Add `suspend fun createMember(userJson: JsonObject): Result<String>` to UserRepository interface
2. Move becomeMember logic from DataService into UserRepositoryImpl with proper error handling
3. Update BecomeMemberActivity to call userRepository.createMember() instead of DataService
4. Remove DataService import and instantiation from BecomeMemberActivity
5. Test member creation flow end-to-end
:::

### Task 2: Remove direct Realm access from AddExaminationActivity

AddExaminationActivity performs complex Realm database operations including queries, transactions, and object creation directly in the UI layer. This violates separation of concerns and makes testing difficult.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=100 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L84-L100"}

:codex-file-citation[codex-file-citation]{line_range_start=249 line_range_end=268 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L249-L268"}

:::task-stub{title="Create HealthRepository for examination data operations"}
1. Create HealthRepository interface and HealthRepositoryImpl extending RealmRepository
2. Add methods: getHealthExamination(userId), saveExamination(examination), getUserHealthProfile(userId)
3. Move Realm transaction logic from AddExaminationActivity into HealthRepositoryImpl
4. Inject HealthRepository into AddExaminationActivity via Hilt
5. Replace all mRealm operations in Activity with repository calls
6. Remove mRealm field and DatabaseService usage from Activity
:::

### Task 3: Remove direct DatabaseService usage from ReplyActivity

ReplyActivity directly uses DatabaseService.withRealm() for database operations instead of using VoicesRepository, creating a cross-layer dependency that should be eliminated.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt#L48-L60"}

:::task-stub{title="Move reply data operations to VoicesRepository"}
1. Identify all DatabaseService.withRealm() calls in ReplyActivity
2. Add corresponding methods to VoicesRepository interface for news/reply operations
3. Implement methods in VoicesRepositoryImpl using RealmRepository base class
4. Update ReplyActivity to use injected VoicesRepository instead of DatabaseService
5. Remove DatabaseService injection from ReplyActivity
:::

### Task 4: Fix Dispatchers.Main usage for IO operations in ConfigurationsRepository

ConfigurationsRepositoryImpl uses withContext(Dispatchers.Main) for network I/O operations, which should use Dispatchers.IO instead. Only callbacks should switch to Main dispatcher.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L40-L78"}

:codex-file-citation[codex-file-citation]{line_range_start=87 line_range_end=150 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L87-L150"}

:::task-stub{title="Correct dispatcher usage in ConfigurationsRepository network calls"}
1. Replace `withContext(Dispatchers.Main)` with `withContext(Dispatchers.IO)` for apiInterface.healthAccess()
2. Update checkVersion() method to perform fetchVersionInfo() on IO dispatcher
3. Only switch to Dispatchers.Main immediately before calling listener callbacks
4. Review all network operations in the repository for proper dispatcher usage
5. Test health check and version check functionality
:::

### Task 5: Make UserRepository.getUserModel() async-safe

getUserModel() in UserRepositoryImpl performs synchronous Realm operations without proper dispatcher handling, potentially blocking the UI thread.

:codex-file-citation[codex-file-citation]{line_range_start=234 line_range_end=244 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L234-L244"}

:::task-stub{title="Add proper dispatchers to getUserModel"}
1. Deprecate getUserModel() method in UserRepository interface
2. Update all callers to use existing getUserModelSuspending() suspend function instead
3. Remove deprecated getUserModel() after migration complete
4. Verify no synchronous Realm access remains on UI thread
:::

### Task 6: Extract Realm queries from BaseRecyclerParentFragment to repositories

BaseRecyclerParentFragment contains direct Realm queries for courses and libraries that should be in their respective repositories for proper layer separation.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L14-L73"}

:::task-stub{title="Move getList() Realm queries to CoursesRepository and ResourcesRepository"}
1. Add getMyLibraryItems(userId, isPrivate) and getOurLibraryItems(userId) to ResourcesRepository
2. Add getMyCourses(userId) and getOurCourses(userId) to CoursesRepository  
3. Update BaseRecyclerParentFragment.getList() to call repository methods instead of direct mRealm queries
4. Keep combineAndFilter logic in base class but delegate data access to repositories
5. Test course and library list displays across all fragments
:::

### Task 7: Remove mRealm initialization from BaseResourceFragment

BaseResourceFragment initializes and exposes a protected mRealm instance to all subclasses, enabling direct database access throughout the UI layer. This breaks encapsulation.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L60-L105"}

:::task-stub{title="Remove mRealm field from BaseResourceFragment"}
1. Audit all subclasses of BaseResourceFragment to find mRealm usage
2. For each usage, add corresponding repository method or use existing repository
3. Remove protected lateinit var mRealm field from BaseResourceFragment
4. Remove requireRealmInstance() and isRealmInitialized() helper methods
5. Update affected fragments to use repository pattern exclusively
6. Test resource browsing, course display, and library functionality
:::

### Task 8: Add ViewModel to ExamTakingFragment for answer state management

ExamTakingFragment manages complex exam state with answerCache map, multiple repository calls, and lifecycle operations that should be in a ViewModel for proper architecture.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L43-L80"}

:::task-stub{title="Create ExamTakingViewModel for exam state"}
1. Create ExamTakingViewModel with StateFlow for exam state, answers, and submission status
2. Move answerCache, listAns, and exam loading logic from Fragment to ViewModel
3. Inject SurveysRepository and SubmissionsRepository into ViewModel constructor
4. Update ExamTakingFragment to observe ViewModel state via viewModel by viewModels()
5. Move repository calls and business logic to ViewModel, keep only UI updates in Fragment
:::

### Task 9: Add ViewModel to MyHealthFragment for sync state management

MyHealthFragment manages complex health sync state with manual Job tracking, server polling, and multiple listeners that would be cleaner in a ViewModel with StateFlow.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L59-L80"}

:::task-stub{title="Create HealthViewModel for health sync management"}
1. Create HealthViewModel with StateFlow for sync status, health list, and user selection
2. Move sync coordination logic from Fragment to ViewModel with proper coroutine scope
3. Inject UserRepository and SyncManager into ViewModel constructor
4. Replace manual Job tracking with ViewModel viewModelScope
5. Update MyHealthFragment to observe ViewModel state and remove direct sync management
:::

### Task 10: Add ViewModel to SurveyFragment for survey state management

SurveyFragment manages complex state with surveyInfoMap, bindingDataMap, and manual Job tracking for async survey loading that should be in a ViewModel.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L38-L78"}

:::task-stub{title="Create SurveyViewModel for survey list management"}
1. Create SurveyViewModel with StateFlow for surveys list, filter state, and sync status
2. Move surveyInfoMap, bindingDataMap, and loadSurveysJob logic to ViewModel
3. Inject SurveysRepository and SyncManager into ViewModel constructor
4. Update SurveyFragment to use by viewModels() and observe ViewModel state
5. Move team filtering and server reachability checks to ViewModel business logic
:::

---

## Testing Section

### Recommended Testing Approach

For each task above:

1. **Before changes**: Run the app and exercise the affected feature to establish baseline behavior
2. **During implementation**: Use existing repository unit tests as patterns (if present)
3. **After changes**: 
   - Build the app with `./gradlew assembleDefaultDebug` to verify compilation
   - Run affected feature manually to verify functionality preserved
   - Check for memory leaks with Android Profiler if removing lifecycle-aware components
4. **Integration testing**: Test sync operations and data persistence across app restarts

### Build Commands

```bash
# Build debug APK
./gradlew assembleDefaultDebug

# Install to device
./gradlew installDefaultDebug

# Run lint checks
./gradlew lintDefaultDebug
```

### Key Testing Areas

- User authentication and member creation (Tasks 1, 5)
- Health examination CRUD operations (Tasks 2, 9)
- News/voices reply functionality (Task 3)
- Server health checks and configuration (Task 4)
- Course and resource browsing (Tasks 6, 7)
- Exam taking and submission (Task 8)
- Survey list and adoption (Task 10)

---

## Notes

- All tasks focus on moving data access from UI/Service layers into Repository layer
- No new features added, only architectural improvements
- Each task is independent and can be completed in a single PR
- All changes maintain existing functionality while improving testability and maintainability
- Tasks prioritized based on impact and ease of review (low-hanging fruit first)

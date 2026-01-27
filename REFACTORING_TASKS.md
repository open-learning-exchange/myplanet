# myPlanet Repository Layer Refactoring Tasks

This document outlines 10 granular, easily reviewable tasks to strengthen repository boundaries and improve architectural patterns in the myPlanet Android application.

## Findings & Tasks

### 1. Remove direct Realm access from BaseExamFragment

The BaseExamFragment directly initializes and uses a Realm instance (`mRealm = databaseService.realmInstance`) and performs manual transaction management with `beginTransaction()` and `commitTransaction()`, bypassing the repository layer abstraction.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt#L73-L73"}

:codex-file-citation[codex-file-citation]{line_range_start=154 line_range_end=163 path=app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt#L154-L163"}

:codex-file-citation[codex-file-citation]{line_range_start=189 line_range_end=200 path=app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt#L189-L200"}

:::task-stub{title="Remove mRealm from BaseExamFragment"}
1. Remove `lateinit var mRealm: Realm` field declaration from BaseExamFragment
2. Remove line 73 that initializes `mRealm = databaseService.realmInstance`
3. Update the `checkId()` method to use `databaseService.withRealm()` for submission queries
4. Update all methods that use `mRealm` to use repository methods or `databaseService.withRealm()`
5. Test exam functionality to ensure queries work correctly
:::

### 2. Move course progress updates to ProgressRepository

The saveCourseProgress() method in BaseExamFragment performs direct database operations with manual transaction management. This business logic should be in ProgressRepository.

:codex-file-citation[codex-file-citation]{line_range_start=154 line_range_end=163 path=app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt#L154-L163"}

:::task-stub{title="Move saveCourseProgress to ProgressRepository"}
1. Add new method `updateCourseProgressForExam(courseId: String?, stepNum: Int, passed: Boolean)` to ProgressRepository interface
2. Implement the method in ProgressRepositoryImpl using existing `saveCourseProgress()` or extending it
3. Inject ProgressRepository into BaseExamFragment
4. Replace saveCourseProgress() implementation with repository call
5. Test course progress update after exam completion
:::

### 3. Move submission creation logic to SubmissionsRepository

The createSubmission() method in ExamTakingFragment handles complex submission object creation with manual Realm transaction management, which should be in SubmissionsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=237 line_range_end=261 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L237-L261"}

:::task-stub{title="Move submission creation to SubmissionsRepository"}
1. Add method `createExamSubmission(exam: RealmStepExam?, userId: String?, type: String?, teamId: String?): RealmSubmission` to SubmissionsRepository interface
2. Implement method in SubmissionsRepositoryImpl, moving logic from ExamTakingFragment.createSubmission()
3. Update ExamTakingFragment to call repository method instead of direct Realm operations
4. Remove manual transaction management from ExamTakingFragment
5. Test exam submission creation flow
:::

### 4. Remove direct DatabaseService access from BaseRecyclerFragment

BaseRecyclerFragment directly accesses `databaseService.realmInstance` and performs `mRealm.refresh()` on the main thread, which is deprecated and violates layer boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=91 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L90-L91"}

:codex-file-citation[codex-file-citation]{line_range_start=167 line_range_end=169 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L167-L169"}

:::task-stub{title="Remove mRealm access from BaseRecyclerFragment"}
1. Remove `mRealm = databaseService.realmInstance` initialization from onViewCreated (line 90)
2. Remove the `mRealm.refresh()` call from addToMyList() method (lines 167-169)
3. Replace adapter recreation with adapter's `notifyDataSetChanged()` or use ListAdapter's `submitList()` properly
4. Ensure repositories handle data freshness internally
5. Test library/course addition functionality
:::

### 5. Remove direct Realm instance from BaseResourceFragment

BaseResourceFragment maintains a protected mRealm instance and exposes requireRealmInstance() method, encouraging UI components to bypass repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=64 line_range_end=64 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L64-L64"}

:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L92-L97"}

:::task-stub{title="Remove mRealm from BaseResourceFragment"}
1. Remove `protected lateinit var mRealm: Realm` field declaration (line 64)
2. Remove `requireRealmInstance()` helper method (lines 92-97)
3. Remove `isRealmInitialized()` helper method
4. Update all subclasses using `mRealm` or `requireRealmInstance()` to use repository methods
5. Test resource viewing and interaction features
:::

### 6. Wrap dictionary operations in DictionaryRepository

DictionaryActivity performs direct database operations using `databaseService.withRealm()` with inline queries and `executeTransactionAsync()`, mixing UI and data concerns.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L93"}

:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L103-L126"}

:::task-stub{title="Create DictionaryRepository and move operations"}
1. Create DictionaryRepository interface with methods: `isDictionaryEmpty()`, `loadDictionary(jsonArray: JsonArray)`, `searchWord(word: String): RealmDictionary?`
2. Create DictionaryRepositoryImpl extending RealmRepository with implementations using `withRealmAsync()` and `executeTransaction()`
3. Register in RepositoryModule for DI
4. Inject DictionaryRepository into DictionaryActivity
5. Replace all direct database calls with repository methods
:::

### 7. Replace manual DiffUtil.ItemCallback with DiffUtils helper in ResourcesAdapter

ResourcesAdapter uses a manual anonymous DiffUtil.ItemCallback implementation instead of the existing DiffUtils.itemCallback() helper, creating inconsistency.

:codex-file-citation[codex-file-citation]{line_range_start=58 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L58-L60"}

:::task-stub{title="Use DiffUtils.itemCallback in ResourcesAdapter"}
1. Replace the manual DIFF_CALLBACK object with DiffUtils.itemCallback() call
2. Pass lambda for areItemsTheSame checking `oldItem.id == newItem.id`
3. Pass lambda for areContentsTheSame checking relevant RealmMyLibrary fields
4. Remove the companion object DIFF_CALLBACK and constants if no longer needed
5. Test resource list updates and verify smooth UI updates
:::

### 8. Add explicit IO dispatcher to lifecycleScope operations in BaseRecyclerFragment

The addToMyList() method uses lifecycleScope.launch without specifying Dispatchers.IO for repository operations that perform database I/O.

:codex-file-citation[codex-file-citation]{line_range_start=143 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L143-L160"}

:::task-stub{title="Add Dispatchers.IO to BaseRecyclerFragment"}
1. Import `kotlinx.coroutines.Dispatchers` in BaseRecyclerFragment
2. Update `lifecycleScope.launch` call on line 143 to `lifecycleScope.launch(Dispatchers.IO)`
3. Wrap UI updates (lines 167-183) with `withContext(Dispatchers.Main)`
4. Verify repository methods are called on IO dispatcher
5. Test adding items to library/courses to ensure smooth operation
:::

### 9. Remove DatabaseService injection from AddHealthActivity

AddHealthActivity directly injects DatabaseService and uses executeTransactionAsync, bypassing the repository pattern despite having UserRepository available.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt#L37-L38"}

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=100 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt#L73-L100"}

:::task-stub{title="Move health data saving to UserRepository"}
1. Add method `updateUserHealthProfile(userId: String, userData: Map<String, Any?>)` to UserRepository interface
2. Implement in UserRepositoryImpl using `executeTransaction()` with parameter mapping
3. Remove `@Inject lateinit var databaseService: DatabaseService` from AddHealthActivity
4. Replace createMyHealth() implementation to call userRepository method with data map
5. Test health profile editing and saving
:::

### 10. Move submit photos insertion to SubmissionsRepository

The insertIntoSubmitPhotos() method in BaseExamFragment creates RealmSubmitPhotos objects with manual transaction management, which should be in SubmissionsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=188 line_range_end=200 path=app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt#L188-L200"}

:::task-stub{title="Move photo submission to SubmissionsRepository"}
1. Add method `addSubmissionPhoto(submissionId: String, examId: String?, courseId: String?, memberId: String?, photoPath: String?)` to SubmissionsRepository interface
2. Implement in SubmissionsRepositoryImpl using `executeTransaction()` to create RealmSubmitPhotos
3. Update BaseExamFragment to call repository method instead of direct insertIntoSubmitPhotos()
4. Remove manual transaction management from insertIntoSubmitPhotos()
5. Test photo capture and attachment in exam submissions
:::

## Implementation Guidelines

### Priority Order
Implement tasks in numerical order (1-10) to minimize conflicts and maintain logical dependencies.

### Testing Strategy
- After each task, run the affected feature manually
- Verify data is correctly saved and retrieved
- Check for any crashes or UI freezes
- Confirm background operations don't block the UI

### Code Review Checklist
- [ ] No direct Realm instance usage in UI layer
- [ ] All database operations go through repositories
- [ ] Proper use of Dispatchers.IO for background work
- [ ] No manual transaction management outside repositories
- [ ] Consistent use of existing patterns (RealmRepository, DiffUtils)
- [ ] No unused code added

### Merge Conflict Prevention
- Each task touches different files or different methods within the same file
- Changes are surgical and localized
- No large refactorings that would conflict with parallel work
- Repository interfaces extended without modifying existing methods

## Benefits

1. **Stronger Layer Boundaries**: UI components will exclusively use repositories, not direct database access
2. **Improved Testability**: Business logic in repositories can be unit tested in isolation
3. **Better Threading**: Explicit dispatcher usage prevents main thread blocking
4. **Consistency**: Using DiffUtils.itemCallback() across all adapters
5. **Maintainability**: Clear separation of concerns makes code easier to understand and modify
6. **Reduced Coupling**: UI components depend on repository interfaces, not database implementation

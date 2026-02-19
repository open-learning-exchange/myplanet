# myPlanet Architecture Refactoring Tasks

This document outlines 10 granular, low-hanging-fruit tasks to reinforce repository boundaries, eliminate cross-layer data leaks, and migrate data operations from UI/service layers into repositories.

---

### Task 1: Remove direct Realm access from BaseResourceFragment

BaseResourceFragment exposes `protected lateinit var mRealm: Realm` which enables ~20+ fragment subclasses to bypass repository layer and directly query/modify database. This violates repository pattern and creates tight coupling between UI and data layer.

:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L61-L61"}

:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L91-L96"}

:::task-stub{title="Remove mRealm from BaseResourceFragment"}
1. Audit all subclasses of BaseResourceFragment to identify direct mRealm usage
2. Replace direct Realm queries with repository method calls
3. Remove `protected lateinit var mRealm: Realm` field from BaseResourceFragment
4. Remove `requireRealmInstance()` and `isRealmInitialized()` helper methods
5. Update any remaining UI components that depended on mRealm access
:::

---

### Task 2: Migrate Dictionary data operations to new DictionaryRepository

DictionaryActivity directly uses databaseService.withRealm for queries and executeTransaction for writes (lines 54, 71, 72, 96, 103). These data operations should be encapsulated in a DictionaryRepository to establish proper layer boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L56"}

:codex-file-citation[codex-file-citation]{line_range_start=71 line_range_end=89 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L71-L89"}

:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L99"}

:::task-stub{title="Create DictionaryRepository with proper data layer encapsulation"}
1. Create DictionaryRepository interface with methods: getDictionaryCount(), isDictionaryEmpty(), insertDictionaryEntries(), searchDictionary()
2. Create DictionaryRepositoryImpl extending RealmRepository
3. Move all database operations from DictionaryActivity to repository
4. Inject DictionaryRepository into DictionaryActivity via Hilt
5. Update DI modules to bind DictionaryRepository
:::

---

### Task 3: Extract achievement data operations from EditAchievementFragment to repository

EditAchievementFragment directly executes Realm transactions (lines 94, 310) to save user achievements. This business logic belongs in UserRepository or a dedicated AchievementsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L92-L107"}

:::task-stub{title="Move achievement write operations to repository layer"}
1. Add saveAchievement() method to UserRepository interface
2. Implement saveAchievement() in UserRepositoryImpl using proper transaction handling
3. Replace databaseService.withRealmAsync + executeTransaction in EditAchievementFragment with repository call
4. Remove direct databaseService injection from EditAchievementFragment if no longer needed
5. Ensure repository method uses Dispatchers.IO internally
:::

---

### Task 4: Eliminate threading dispatcher logic from UI fragments

Multiple UI fragments use withContext(Dispatchers.IO) for data operations (CoursesFragment:118,535, ResourcesFragment:126). Repository methods should handle threading internally, not UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=117 line_range_end=122 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L117-L122"}

:codex-file-citation[codex-file-citation]{line_range_start=535 line_range_end=540 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L535-L540"}

:codex-file-citation[codex-file-citation]{line_range_start=125 line_range_end=131 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L125-L131"}

:::task-stub{title="Remove explicit Dispatchers.IO from UI layer"}
1. Review all repository methods called from CoursesFragment and ResourcesFragment
2. Ensure repository methods already use withContext(Dispatchers.IO) internally
3. Remove withContext(Dispatchers.IO) wrapping from fragment code
4. Update fragments to call repository methods directly from lifecycleScope.launch
5. Verify repository methods properly handle threading with unit tests
:::

---

### Task 5: Move health examination data writes to HealthRepository

AddExaminationActivity creates/saves RealmHealthExamination and RealmMyHealth directly via mRealm (line 94). HealthRepository interface only has read methods - needs write operations moved from UI.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt#L1-L9"}

:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L90-L96"}

:::task-stub{title="Extend HealthRepository with write operations"}
1. Add saveHealthExamination(examination: RealmHealthExamination) to HealthRepository interface
2. Add saveHealthProfile(health: RealmMyHealth) to HealthRepository interface
3. Implement these methods in HealthRepositoryImpl using proper transaction handling
4. Replace direct mRealm usage in AddExaminationActivity with repository calls
5. Remove direct Realm instance creation from AddExaminationActivity (line 94)
:::

---

### Task 6: Refactor BaseRecyclerFragment to use repository-only data access

BaseRecyclerFragment initializes mRealm (line 89) and mixes repository calls with direct Realm queries. Should rely exclusively on injected repositories.

:codex-file-citation[codex-file-citation]{line_range_start=85 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L85-L105"}

:codex-file-citation[codex-file-citation]{line_range_start=142 line_range_end=152 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L142-L152"}

:::task-stub{title="Remove mRealm initialization from BaseRecyclerFragment"}
1. Identify any remaining direct Realm queries in BaseRecyclerFragment methods
2. Extract these queries into appropriate repository methods
3. Remove `mRealm = databaseService.createManagedRealmInstance()` from onViewCreated
4. Update subclass fragments to use only repository-injected dependencies
5. Remove databaseService field if only used for Realm instance creation
:::

---

### Task 7: Move upload batch processing logic into repositories

UploadManager (758 lines) directly queries Realm for batch uploads using processInBatches. These queries should be in repositories, with UploadManager calling repository methods.

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L51-L59"}

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=76 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L74-L76"}

:::task-stub{title="Extract upload data queries from UploadManager to repositories"}
1. Identify all direct Realm queries in UploadManager (38 instances found)
2. Create repository methods for pending upload queries (e.g., getPendingNewsActivities)
3. Update UploadManager to call repository methods instead of direct queries
4. Keep batch processing logic in UploadManager, move only data access to repositories
5. Reduce UploadManager's direct databaseService usage
:::

---

### Task 8: Add missing repository interfaces for underserved domains

Several data domains lack repository abstractions: Dictionary (no repository), Achievements (nested in UserRepository), Teams sub-features. Create proper repository interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository git_url="https://github.com/open-learning-exchange/myplanet/tree/master/app/src/main/java/org/ole/planet/myplanet/repository#L1-L10"}

:::task-stub{title="Create missing repository interfaces"}
1. Create DictionaryRepository (interface + impl) for RealmDictionary operations
2. Consider splitting UserRepository into UserRepository + AchievementsRepository
3. Verify all Realm model classes have corresponding repository coverage
4. Update DI modules to provide new repositories
5. Document repository ownership for each data domain
:::

---

### Task 9: Standardize repository method signatures with Flow for reactive queries

Current repositories mix List returns with Flow returns inconsistently. ResourcesRepository has getRecentResources returning Flow, but getAllLibraryItems returns List. Standardize reactive patterns.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L9-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L12-L16"}

:::task-stub{title="Add Flow-based observation methods to key repositories"}
1. Add observeAllLibraryItems(): Flow<List<RealmMyLibrary>> to ResourcesRepository
2. Add observeAllCourses(): Flow<List<RealmMyCourse>> to CoursesRepository
3. Implement using RealmRepository.observeQuery() pattern
4. Keep existing suspend methods for one-shot queries
5. Document when to use Flow vs suspend methods in repository KDoc
:::

---

### Task 10: Remove static model insertion methods violating repository pattern

Realm model classes have static insert() methods (e.g., RealmMyLibrary.insert, RealmMyCourse.insert) that are called from various layers. These should be private and only used by repositories.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L20-L30"}

:::task-stub{title="Encapsulate model insertion logic within repositories"}
1. Audit static insert() methods across all Realm model companion objects
2. Change visibility from public to internal or private
3. Create corresponding repository methods (e.g., insertLibrary, insertCourse)
4. Update all callsites to use repository methods instead of static insert
5. Ensure repository implementations use these insert methods internally
:::

---

## Testing Guidance

For each task:
1. Verify existing unit tests for repositories still pass
2. Add tests for new repository methods introduced
3. Use BaseRecyclerFragment/BaseResourceFragment subclasses as integration test points
4. Check that UI layer no longer has direct Realm/databaseService imports
5. Run full sync workflow tests to ensure data layer changes don't break sync

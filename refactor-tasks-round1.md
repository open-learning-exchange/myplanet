# Refactoring Tasks Round 1 - Repository Boundaries & Data Layer Cleanup

## Focus Areas
- Reinforcing repository boundaries between layers
- Cross-feature data leaks and tightening repository interfaces
- Moving data functions from UI/data/service into repositories

---

### 1. RealmRepository uses manual Realm.getDefaultInstance instead of injected DatabaseService

RealmRepository already injects DatabaseService but bypasses it in queryListFlow() by calling Realm.getDefaultInstance() directly. This inconsistency undermines the DI pattern and makes testing harder.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L48-L73"}

:::task-stub{title="Use injected DatabaseService in RealmRepository.queryListFlow"}
1. Replace Realm.getDefaultInstance() on line 52 with databaseService.realmInstance or appropriate DatabaseService method
2. Ensure proper realm lifecycle management using existing DatabaseService patterns
3. Verify existing repository subclasses continue to function correctly
:::

---

### 2. ConfigurationRepositoryImpl creates SharedPreferences manually instead of injecting

ConfigurationRepositoryImpl manually creates SharedPreferences via context.getSharedPreferences() despite @AppPreferences being available in the DI module. This violates DI principles and creates inconsistency.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationRepositoryImpl.kt#L32-L39"}
:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/di/SharedPreferencesModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/SharedPreferencesModule.kt#L27-L32"}

:::task-stub{title="Inject SharedPreferences into ConfigurationRepositoryImpl"}
1. Add @AppPreferences SharedPreferences as constructor parameter to ConfigurationRepositoryImpl
2. Remove manual context.getSharedPreferences() call on line 38
3. Update any usages of the local preferences field
:::

---

### 3. AutoSyncWorker creates SharedPreferences and DataService manually instead of using EntryPoint

AutoSyncWorker uses EntryPoint for SyncManager and UploadManager but manually creates SharedPreferences and DataService. This inconsistency bypasses the established DI pattern for workers.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt#L39-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=17 path=app/src/main/java/org/ole/planet/myplanet/di/AutoSyncEntryPoint.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/AutoSyncEntryPoint.kt#L10-L17"}

:::task-stub{title="Add SharedPreferences to AutoSyncEntryPoint"}
1. Add @AppPreferences fun sharedPreferences(): SharedPreferences to AutoSyncEntryPoint interface
2. Update AutoSyncWorker.doWork() to get SharedPreferences from entryPoint instead of manual creation
3. Remove direct context.getSharedPreferences() call on line 40
:::

---

### 4. TeamCoursesFragment directly queries RealmMyCourse bypassing CoursesRepository

TeamCoursesFragment performs direct Realm queries on RealmMyCourse, creating a cross-feature data leak where team UI directly accesses course data without going through CoursesRepository.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L28-L36"}

:::task-stub{title="Use CoursesRepository in TeamCoursesFragment"}
1. Inject CoursesRepository into TeamCoursesFragment
2. Add getCoursesByIds(courseIds: List<String>) method to CoursesRepository interface and implementation
3. Replace direct mRealm.where(RealmMyCourse::class.java) query with repository call
4. Use lifecycleScope.launch for the async repository call
:::

---

### 5. TeamCoursesAdapter lacks DiffUtil support causing inefficient updates

TeamCoursesAdapter extends RecyclerView.Adapter directly with a mutable list and no DiffUtil. Any list changes require full rebind, causing performance issues. The codebase has DiffUtils.itemCallback helper available.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=64 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L17-L64"}
:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/utilities/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/DiffUtils.kt#L5-L15"}

:::task-stub{title="Convert TeamCoursesAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change TeamCoursesAdapter to extend ListAdapter<RealmMyCourse, ViewHolder>
2. Create DiffUtil.ItemCallback using DiffUtils.itemCallback helper comparing courseId and courseTitle
3. Remove mutable list parameter and use submitList() pattern
4. Update TeamCoursesFragment to call adapter.submitList() instead of passing list to constructor
:::

---

### 6. SurveyAdapter has direct Realm access with executeTransactionAsync in ViewHolder

SurveyAdapter receives Realm instance as constructor parameter and performs complex database transactions directly in the ViewHolder.adoptSurvey() method. This violates repository boundaries and makes the adapter non-testable.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyAdapter.kt#L28-L40"}
:codex-file-citation[codex-file-citation]{line_range_start=200 line_range_end=319 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyAdapter.kt#L200-L319"}

:::task-stub{title="Move SurveyAdapter.adoptSurvey logic to SurveysRepository"}
1. Add adoptSurvey(examId, userId, teamId, isTeam) method to SurveysRepository interface
2. Move the adoptSurvey transaction logic from SurveyAdapter lines 200-319 to SurveysRepositoryImpl
3. Replace mRealm parameter in SurveyAdapter with a callback interface for adopt operations
4. Have SurveyFragment/Activity call repository method and notify adapter via callback
:::

---

### 7. UrlUtils object accesses SharedPreferences directly via static context

UrlUtils is a static object that directly accesses SharedPreferences using MainApplication.context, bypassing DI entirely. This makes the utility untestable and violates DI principles.

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/utilities/UrlUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/UrlUtils.kt#L11-L42"}
:codex-file-citation[codex-file-citation]{line_range_start=76 line_range_end=79 path=app/src/main/java/org/ole/planet/myplanet/utilities/UrlUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/UrlUtils.kt#L76-L79"}

:::task-stub{title="Add SharedPreferences parameter to UrlUtils static property getters"}
1. Change header property getter to accept SharedPreferences parameter: fun getHeader(settings: SharedPreferences): String
2. Change hostUrl property getter to accept SharedPreferences parameter: fun getHostUrl(settings: SharedPreferences): String
3. Change getUrl() function to accept SharedPreferences parameter
4. Update callers to pass injected SharedPreferences instead of relying on static context
:::

---

### 8. RealmMyCourse companion object contains query logic that belongs in CoursesRepository

RealmMyCourse.Companion contains multiple query and business logic methods (getMyByUserId, getMyCourseByUserId, isMyCourse, getMyCourseIds) that should be in CoursesRepository for proper layering.

:codex-file-citation[codex-file-citation]{line_range_start=186 line_range_end=268 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L186-L268"}

:::task-stub{title="Move RealmMyCourse.isMyCourse to CoursesRepository"}
1. Add isMyCourse(userId: String?, courseId: String?): Boolean method to CoursesRepository interface
2. Implement in CoursesRepositoryImpl using existing queryList pattern
3. Update CourseStepFragment line 127 to use injected CoursesRepository.isMyCourse() instead of static call
4. Deprecate RealmMyCourse.Companion.isMyCourse with @Deprecated annotation pointing to repository
:::

---

### 9. TeamCoursesAdapter receives Realm instance in constructor for cross-feature query

TeamCoursesAdapter receives Realm in constructor solely to call RealmMyTeam.getTeamCreator() for permission checking. This creates a cross-feature data dependency where course adapter queries team data.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L17-L34"}

:::task-stub{title="Pass teamCreator as parameter to TeamCoursesAdapter instead of Realm"}
1. Remove Realm parameter from TeamCoursesAdapter constructor
2. Add teamCreator: String parameter directly to constructor
3. Update TeamCoursesFragment to fetch teamCreator via TeamsRepository before creating adapter
4. Inject TeamsRepository into TeamCoursesFragment and add getTeamCreator(teamId) method if not exists
:::

---

### 10. CourseStepFragment uses databaseService.withRealm for inline queries instead of repository

CourseStepFragment has a loadStepData() function that performs multiple inline Realm queries using databaseService.withRealm(). These queries for RealmCourseStep, RealmMyLibrary, and RealmStepExam should go through respective repositories.

:codex-file-citation[codex-file-citation]{line_range_start=107 line_range_end=130 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt#L107-L130"}

:::task-stub{title="Use CoursesRepository for step data in CourseStepFragment.loadStepData"}
1. Add getCourseStep(stepId: String): RealmCourseStep? method to CoursesRepository
2. Add getStepExams(stepId: String, type: String): List<RealmStepExam> method to CoursesRepository
3. Replace inline realm queries in loadStepData() with repository method calls
4. Keep existing ResourcesRepository usage for RealmMyLibrary queries (line 113-116 already has resourcesRepository)
:::

---

## Summary

| # | Task | Layer | Risk | Files Changed |
|---|------|-------|------|---------------|
| 1 | RealmRepository use DatabaseService | Data | Low | 1 |
| 2 | ConfigurationRepositoryImpl inject prefs | DI | Low | 1 |
| 3 | AutoSyncWorker use EntryPoint for prefs | DI | Low | 2 |
| 4 | TeamCoursesFragment use repository | UI→Repo | Low | 2-3 |
| 5 | TeamCoursesAdapter add DiffUtil | UI | Low | 2 |
| 6 | SurveyAdapter move adopt to repository | UI→Repo | Medium | 3 |
| 7 | UrlUtils accept prefs parameter | Utilities | Medium | 5+ |
| 8 | Move isMyCourse to CoursesRepository | Model→Repo | Low | 3 |
| 9 | TeamCoursesAdapter remove Realm param | UI | Low | 2 |
| 10 | CourseStepFragment use repository | UI→Repo | Low | 2-3 |

**Recommended PR Order**: 1, 2, 3, 9, 5, 8, 4, 10, 6, 7

Tasks 1-3 and 9 are pure DI/cleanup with minimal risk. Tasks 5 and 8 are isolated improvements. Tasks 4, 10, 6, 7 involve more cross-cutting changes but are still manageable.

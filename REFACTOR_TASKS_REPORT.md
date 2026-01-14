# Refactoring Tasks Report: Repository Boundaries & Data Layer Cleanup

This report identifies 10 granular, easily reviewable tasks focused on reinforcing repository boundaries, eliminating cross-feature data leaks, and moving data functions from UI/service layers into repositories.

---

### 1. Move team leadership logic from MembersFragment to TeamsRepository

The `makeLeaderSync` and `getNextOfKinSync` methods contain complex Realm queries and business logic for team leadership that belong in the repository layer. This creates a cross-layer violation where UI directly manipulates team membership data.

:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=202 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt#L187-L202"}
:codex-file-citation[codex-file-citation]{line_range_start=204 line_range_end=239 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt#L204-L239"}

:::task-stub{title="Move makeLeaderSync and getNextOfKinSync to TeamsRepository"}
1. Add `updateTeamLeader(teamId: String, newLeaderId: String): Boolean` method to TeamsRepository interface
2. Add `getNextLeaderCandidate(teamId: String, excludeUserId: String?): RealmUserModel?` method to TeamsRepository interface
3. Implement both methods in TeamsRepositoryImpl moving logic from MembersFragment lines 187-239
4. Update MembersFragment to call repository methods instead of private sync functions
5. Remove private `makeLeaderSync` and `getNextOfKinSync` methods from MembersFragment
:::

---

### 2. Replace Realm.getDefaultInstance with injected DatabaseService in NewsActions

The `NewsActions` object bypasses dependency injection entirely by calling `Realm.getDefaultInstance().use {}` directly. This creates an untracked database connection and violates the DI pattern used throughout the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=167 line_range_end=185 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt#L167-L185"}

:::task-stub{title="Replace Realm.getDefaultInstance with injected service in NewsActions"}
1. Convert NewsActions from object to class that accepts DatabaseService parameter
2. Update `showEditAlert` method signature to accept DatabaseService
3. Replace `Realm.getDefaultInstance().use { realm ->` with `databaseService.withRealm { realm ->`
4. Update all callers of NewsActions to pass DatabaseService instance
5. Remove direct `io.realm.Realm` import from NewsActions
:::

---

### 3. Move news creation transaction from DiscussionListFragment to VoicesRepository

The fragment directly executes a Realm transaction with `createNews` call, mixing UI concerns with data persistence. This data function should be encapsulated in VoicesRepository to maintain layer separation.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=104 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/discussion/DiscussionListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/discussion/DiscussionListFragment.kt#L84-L104"}

:::task-stub{title="Move createNews transaction to VoicesRepository"}
1. Add `createTeamNews(newsData: Map<String?, String>, user: RealmUserModel, imageList: RealmList<String>?): Boolean` to VoicesRepository interface
2. Implement method in VoicesRepositoryImpl wrapping the executeTransactionAsync and createNews call
3. Update DiscussionListFragment btnSubmit click handler to call voicesRepository.createTeamNews()
4. Remove direct databaseService.executeTransactionAsync call from DiscussionListFragment
5. Ensure error handling is preserved in repository method
:::

---

### 4. Extract ChallengeHelper database queries to appropriate repositories

ChallengeHelper contains direct DatabaseService queries for courses, surveys, and user challenge actions that cross multiple feature boundaries. Each query should be routed through its respective repository.

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L51-L55"}
:codex-file-citation[codex-file-citation]{line_range_start=79 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L79-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=118 line_range_end=129 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L118-L129"}

:::task-stub{title="Route ChallengeHelper queries through repositories"}
1. Add `getCourseTitleById(courseId: String): String?` to CoursesRepository and implement in CoursesRepositoryImpl
2. Add `getSurveysByCourseId(courseId: String): List<RealmStepExam>` to SubmissionsRepository interface
3. Add `hasUserCompletedSync(userId: String): Boolean` to a new ChallengesRepository or ProgressRepository
4. Inject CoursesRepository into ChallengeHelper constructor
5. Replace databaseService.withRealmAsync queries with repository method calls
:::

---

### 5. Create DictionaryRepository for all dictionary data operations

DictionaryActivity contains multiple direct databaseService.withRealm calls for count, load, and search operations. These should be consolidated into a dedicated repository to enforce data layer boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=131 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L131"}

:::task-stub{title="Create DictionaryRepository for dictionary operations"}
1. Create DictionaryRepository interface with methods: `getDictionaryCount(): Long`, `isDictionaryEmpty(): Boolean`, `loadDictionaryFromJson(jsonArray: JsonArray)`, `searchWord(word: String): RealmDictionary?`
2. Create DictionaryRepositoryImpl class implementing the interface with Hilt @Inject
3. Add DictionaryRepository binding to RepositoryModule
4. Inject DictionaryRepository into DictionaryActivity
5. Replace all databaseService.withRealm calls with repository method calls
:::

---

### 6. Create HealthRepository for health examination data operations

AddHealthActivity contains complex Realm transactions for user health data that mix user model updates with health examination creation. This should be separated into a HealthRepository.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=128 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt#L73-L128"}
:codex-file-citation[codex-file-citation]{line_range_start=134 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt#L134-L160"}

:::task-stub{title="Create HealthRepository for health data operations"}
1. Create HealthRepository interface with methods: `getHealthExaminationByUserId(userId: String): RealmHealthExamination?`, `saveHealthData(userId: String, healthProfile: RealmMyHealthProfile, userUpdates: UserHealthUpdates)`, `getHealthDataForUser(userId: String): HealthData?`
2. Create HealthRepositoryImpl with DatabaseService injection
3. Add HealthRepository binding to RepositoryModule
4. Inject HealthRepository into AddHealthActivity and AddExaminationActivity
5. Move createMyHealth and populate data access logic to repository methods
:::

---

### 7. Move deleteSelected Realm transaction from BaseRecyclerFragment to repository

The `deleteSelected` method in BaseRecyclerFragment directly manages Realm transactions for deleting courses and libraries. This transaction logic should be delegated to the respective repositories.

:codex-file-citation[codex-file-citation]{line_range_start=199 line_range_end=220 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L199-L220"}
:codex-file-citation[codex-file-citation]{line_range_start=226 line_range_end=236 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L226-L236"}

:::task-stub{title="Move deleteSelected transaction logic to repositories"}
1. Add `removeFromUserShelf(resourceId: String, userId: String)` method to ResourcesRepository
2. Add `removeFromUserCourses(courseId: String, userId: String, deleteProgress: Boolean)` method to CoursesRepository
3. Implement deletion and progress cleanup logic in repository methods
4. Update deleteSelected to iterate items and call appropriate repository methods
5. Remove direct mRealm.beginTransaction/commitTransaction calls from BaseRecyclerFragment
:::

---

### 8. Track and cancel coroutine Jobs in CoursesAdapter for proper cleanup

CoursesAdapter launches coroutines via `findViewTreeLifecycleOwner()?.lifecycleScope?.launch` without tracking Job references. This can lead to memory leaks if the adapter is detached before jobs complete.

:codex-file-citation[codex-file-citation]{line_range_start=404 line_range_end=417 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L404-L417"}
:codex-file-citation[codex-file-citation]{line_range_start=57 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L57-L58"}

:::task-stub{title="Track coroutine Jobs in CoursesAdapter for cleanup"}
1. Add `private val activeJobs: MutableMap<String, Job> = mutableMapOf()` field to CoursesAdapter
2. Store launched Job with courseId key in displayTagCloud method
3. Cancel previous Job for same courseId before launching new one
4. Override `onViewRecycled` to cancel Job associated with recycled ViewHolder
5. Add `fun cancelAllJobs()` method and call it when adapter is cleared or detached
:::

---

### 9. Use RatingsRepository consistently in CoursesFragment instead of direct mRealm access

CoursesFragment calls `getRatings(mRealm, "course", model?.id)` directly in multiple places instead of using an injected RatingsRepository. This inconsistent pattern mixes data access with UI logic.

:codex-file-citation[codex-file-citation]{line_range_start=184 line_range_end=191 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L184-L191"}
:codex-file-citation[codex-file-citation]{line_range_start=228 line_range_end=231 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L228-L231"}
:codex-file-citation[codex-file-citation]{line_range_start=685 line_range_end=689 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L685-L689"}

:::task-stub{title="Use RatingsRepository in CoursesFragment"}
1. Add `getCourseRatings(userId: String?): HashMap<String?, JsonObject>` to RatingsRepository interface if not exists
2. Implement method in RatingsRepositoryImpl encapsulating getRatings call
3. Inject RatingsRepository into CoursesFragment via Hilt
4. Replace all `getRatings(mRealm, "course", model?.id)` calls with `ratingsRepository.getCourseRatings(model?.id)`
5. Ensure method runs on Dispatchers.IO and returns to Main for UI updates
:::

---

### 10. Move team query from DiscussionListFragment to TeamsRepository

DiscussionListFragment directly queries `mRealm.where(RealmMyTeam::class.java)` to find team by ID. This data access should go through the already-injected TeamsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=106 line_range_end=121 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/discussion/DiscussionListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/discussion/DiscussionListFragment.kt#L106-L121"}

:::task-stub{title="Use TeamsRepository for team lookup in DiscussionListFragment"}
1. Add `getTeamByIdOrTeamId(id: String): RealmMyTeam?` method to TeamsRepository interface
2. Implement in TeamsRepositoryImpl with fallback query logic from lines 107-120
3. Update DiscussionListFragment to use `teamsRepository.getTeamByIdOrTeamId(teamId)`
4. Remove direct mRealm.where(RealmMyTeam::class.java) calls from DiscussionListFragment
5. Make the repository method suspend and call from coroutine scope
:::

---

## Summary

| Task | Layer Violation | Complexity | Files Changed |
|------|-----------------|------------|---------------|
| 1. MembersFragment leadership | UI → Data | Medium | 2 files |
| 2. NewsActions Realm instance | DI bypass | Low | 1-2 files |
| 3. DiscussionListFragment createNews | UI → Data | Low | 2 files |
| 4. ChallengeHelper queries | Cross-feature | Medium | 2-3 files |
| 5. DictionaryRepository | Missing repo | Medium | 3-4 files |
| 6. HealthRepository | Missing repo | Medium | 3-4 files |
| 7. BaseRecyclerFragment delete | UI → Data | Medium | 2-3 files |
| 8. CoursesAdapter Job tracking | Threading | Low | 1 file |
| 9. CoursesFragment ratings | Inconsistent | Low | 1-2 files |
| 10. DiscussionListFragment team | UI → Data | Low | 2 files |

All tasks are designed to be small, focused changes that minimize merge conflict risk and can be reviewed independently within a single PR review cycle.

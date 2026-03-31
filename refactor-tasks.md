### Extract DictionaryRepository from DictionaryActivity

DictionaryActivity (lines 52–127) is the only Activity in the codebase that bypasses the repository layer by calling `databaseService.withRealm` directly, with four inline Realm operations: an existence check, a bulk `createObject` insert, a count query, and a case-insensitive `findFirst` lookup. Every other feature uses an injected repository; this one is an outlier that leaks data-layer concerns into the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L99"}

:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L127"}

:::task-stub{title="Extract DictionaryRepository from DictionaryActivity"}
1. Create `DictionaryRepository` interface in `repository/` with four methods: `isDictionaryEmpty(): Boolean`, `insertWords(jsonArray: JsonArray)`, `getWordCount(): Long`, `findWord(word: String): RealmDictionary?`
2. Create `DictionaryRepositoryImpl` extending `RealmRepository`, implementing those four methods using `withRealm`, `executeTransaction`, `count`, and `findByField`
3. Bind the interface to its implementation in `RepositoryModule`
4. Replace `@Inject lateinit var databaseService: DatabaseService` in `DictionaryActivity` with `@Inject lateinit var dictionaryRepository: DictionaryRepository`
5. Replace each of the four inline `databaseService.withRealm { realm -> realm.where(RealmDictionary…) }` call-sites with the corresponding repository method
:::

---

### Add DispatcherProvider to CourseDetailViewModel.loadCourseDetail

`loadCourseDetail` (lines 45–93) launches on `viewModelScope` without a dispatcher, then fires seven consecutive repository calls (course lookup, exam count, online resources, offline resources, steps, per-step question counts, rating summary). All other multi-query ViewModels in the codebase—`TeamViewModel`, `DashboardViewModel`—wrap IO work in `withContext(dispatcherProvider.io)`. This one is inconsistent and risks blocking the Main thread during heavy reads.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=94 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt#L35-L94"}

:::task-stub{title="Add DispatcherProvider to CourseDetailViewModel.loadCourseDetail"}
1. Add `private val dispatcherProvider: DispatcherProvider` to the `CourseDetailViewModel` constructor (follow the same pattern as `TeamViewModel`)
2. Inside `loadCourseDetail`, wrap all repository calls and data-processing logic with `withContext(dispatcherProvider.io) { … }`
3. Keep `_uiState.value = …` assignments outside the IO context block so StateFlow emissions stay on Main
4. Apply the same treatment to the smaller `refreshRatings` launch at line 97
:::

---

### Add DispatcherProvider to BellDashboardViewModel.loadCompletedCourses

`loadCompletedCourses` (lines 41–98) launches without a dispatcher, calls `coursesRepository.getMyCourses` and `progressRepository.getProgressRecords`, and then performs an in-memory filtering loop that iterates every course and every progress record. The function also contains many `android.util.Log.d` debug calls that are evidence the function was written quickly; the debug log volume alone makes the missing dispatcher a bigger risk on large datasets.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt#L18-L99"}

:::task-stub{title="Add DispatcherProvider to BellDashboardViewModel.loadCompletedCourses"}
1. Add `private val dispatcherProvider: DispatcherProvider` to the `BellDashboardViewModel` constructor
2. Wrap the two repository calls and the `forEachIndexed` loop inside `loadCompletedCourses` with `withContext(dispatcherProvider.io) { … }`
3. Keep `_completedCourses.value = completedCourses` outside the IO block
4. Remove the debug-only `android.util.Log.d("BadgeConditions", …)` lines (lines 43–95) now that the logic is verified
:::

---

### Add DispatcherProvider to SurveysViewModel IO launches

`SurveysViewModel.loadSurveys` (lines 65–98) and `adoptSurvey` (lines 207–219) each launch on `viewModelScope` without a dispatcher. `loadSurveys` makes three consecutive repository calls; `adoptSurvey` makes one. Both are in the same file, so one PR covers them both with no risk of merge conflict. The ViewModel already holds `syncManager` as a constructor parameter (good), but the data-load path never switches to IO.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L26-L98"}

:::task-stub{title="Add DispatcherProvider to SurveysViewModel IO launches"}
1. Add `private val dispatcherProvider: DispatcherProvider` to the `SurveysViewModel` constructor
2. In `loadSurveys`, wrap the three `surveysRepository.*` calls and `userSessionManager.getUserModel()` with `withContext(dispatcherProvider.io) { … }`; keep StateFlow assignments outside
3. In `adoptSurvey`, wrap `surveysRepository.adoptSurvey(…)` with `withContext(dispatcherProvider.io) { … }`
4. In `checkServerAndStartSync` (line 176), add `withContext(dispatcherProvider.io) { … }` around the `serverUrlMapper.updateServerIfNecessary` call which performs a network reachability check
:::

---

### Move SyncManager out of ResourcesFragment into ResourcesViewModel

`ResourcesFragment` directly injects `SyncManager` (line 80) and `ServerUrlMapper` (line 83) and owns both `checkServerAndStartSync` and `startSyncManager` (lines 111–157). This pulls service-layer objects straight into the View layer. `SurveysViewModel` already models the correct pattern: `syncManager` lives in the ViewModel constructor, and the Fragment only observes state.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=157 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L74-L157"}

:::task-stub{title="Move SyncManager out of ResourcesFragment into ResourcesViewModel"}
1. Create `ResourcesViewModel` as a `@HiltViewModel` with `syncManager: SyncManager`, `sharedPrefManager: SharedPrefManager`, and `serverUrlMapper: ServerUrlMapper` as constructor parameters
2. Move `checkServerAndStartSync` and `startSyncManager` logic into the ViewModel; expose sync state via a `StateFlow<SyncState>`
3. In `ResourcesFragment`, remove the `@Inject lateinit var syncManager` and `serverUrlMapper` fields and replace with ViewModel observation of the new StateFlow
4. Update the Hilt module if the ViewModel is not auto-detected
:::

---

### Move SyncManager out of CoursesFragment into a ViewModel

`CoursesFragment` injects `SyncManager` (line 88) and calls `checkServerAndStartSync` / `startSyncManager` (lines 109–137) directly from the Fragment class. This duplicates the same anti-pattern as `ResourcesFragment` and is in a different file, making it an independent PR with no conflict risk.

:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=137 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L81-L137"}

:::task-stub{title="Move SyncManager out of CoursesFragment into a ViewModel"}
1. Create or extend a `CourseListViewModel` (separate from `CourseDetailViewModel`) as a `@HiltViewModel` accepting `syncManager: SyncManager`, `sharedPrefManager: SharedPrefManager`, and `serverUrlMapper: ServerUrlMapper`
2. Move `checkServerAndStartSync` and `startSyncManager` into the ViewModel; expose a `StateFlow<SyncState>`
3. Remove `@Inject lateinit var syncManager`, `prefManager`, and `serverUrlMapper` from `CoursesFragment` (lines 81–88) and replace with ViewModel observation
4. Wire up the Hilt binding if not auto-detected
:::

---

### Move SyncManager out of MyHealthFragment into a ViewModel

`MyHealthFragment` injects both `SyncManager` (line 61) and `RealtimeSyncManager.getInstance()` (line 64), and owns `checkServerAndStartSync` / `startSyncManager` (lines 103–130). Additionally, `syncManagerInstance.addListener` is called at line 236 and removed at line 446—a long-lived listener pair that is hard to audit when it sits inside a Fragment rather than a lifecycle-aware ViewModel.

:codex-file-citation[codex-file-citation]{line_range_start=57 line_range_end=130 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L57-L130"}

:::task-stub{title="Move SyncManager out of MyHealthFragment into a ViewModel"}
1. Create `HealthViewModel` as a `@HiltViewModel` with `syncManager: SyncManager`, `sharedPrefManager: SharedPrefManager`, `serverUrlMapper: ServerUrlMapper`, and `RealtimeSyncManager` as constructor parameters
2. Move `checkServerAndStartSync`, `startSyncManager`, and the `addListener`/`removeListener` pair into the ViewModel, managing listener lifecycle in `onCleared()`
3. Expose a `StateFlow<SyncState>` and a `StateFlow<HealthData>` for the Fragment to observe
4. Remove `@Inject lateinit var syncManager` (line 61) and the `syncManagerInstance` field (line 64) from `MyHealthFragment`; replace Fragment-owned sync calls with ViewModel method invocations
:::

---

### Remove DatabaseService @Inject from BaseResourceFragment

`BaseResourceFragment` injects both `DatabaseService` (line 76) and `UserSessionManager` (line 78) as concrete service classes alongside six repository interfaces (lines 63–74). The `databaseService` field is used solely to create a managed Realm instance via `requireRealmInstance()` (line 90), which is an escape hatch that lets every subclass bypass the repository layer entirely. Six repository interfaces already cover all of the data access the base class needs.

:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L63-L93"}

:::task-stub{title="Remove DatabaseService @Inject from BaseResourceFragment"}
1. Audit every call site of `requireRealmInstance()` and `mRealm` across `BaseResourceFragment` and its subclasses; replace each with an equivalent repository method call using the already-injected repository interfaces
2. Once no subclass calls `requireRealmInstance()`, delete the `requireRealmInstance()` helper and the `@Inject lateinit var databaseService: DatabaseService` field (lines 75–76)
3. Keep `profileDbHandler: UserSessionManager` only if it is used directly in the base class; otherwise move it to the subclasses that actually need it
:::

---

### Add DispatcherProvider to AddExaminationViewModel.saveExamination

`AddExaminationViewModel.saveExamination` (lines 32–44) launches on `viewModelScope` without a dispatcher and calls `healthRepository.saveExamination`, a write operation that persists a `RealmHealthExamination`. The ViewModel is otherwise well-structured (uses StateFlow, has error handling) but is missing the IO-dispatcher wrapper that every other write-path ViewModel in the project uses.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationViewModel.kt#L19-L45"}

:::task-stub{title="Add DispatcherProvider to AddExaminationViewModel.saveExamination"}
1. Add `private val dispatcherProvider: DispatcherProvider` to the `AddExaminationViewModel` constructor
2. Wrap `healthRepository.saveExamination(examination, pojo, user)` (line 35) with `withContext(dispatcherProvider.io) { … }`
3. Keep `_isSaving.value` and `_saveResult.emit(…)` outside the IO block so StateFlow/SharedFlow emissions happen on Main
:::

---

### Add DispatcherProvider to NewsViewModel.getPrivateImageUrlsCreatedAfter

`NewsViewModel` (lines 11–20) is the smallest ViewModel in the codebase—one suspend function—yet its single `viewModelScope.launch` (line 15) calls `resourcesRepository.getPrivateImageUrlsCreatedAfter` without a dispatcher. It also uses a raw callback instead of a StateFlow, which contradicts the rest of the ViewModel layer. Fixing the dispatcher and switching to StateFlow is a self-contained two-minute change.

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt#L10-L20"}

:::task-stub{title="Add DispatcherProvider to NewsViewModel.getPrivateImageUrlsCreatedAfter"}
1. Add `private val dispatcherProvider: DispatcherProvider` to the `NewsViewModel` constructor
2. Replace the raw `callback: (List<String>) -> Unit` parameter with a `MutableStateFlow<List<String>>` field exposed as a `StateFlow`
3. Wrap the `resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)` call with `withContext(dispatcherProvider.io) { … }`
4. Update the call site in the Fragment to collect the new StateFlow instead of passing a callback lambda
:::

### Inject SharedPrefManager via Hilt Instead of Manual Construction

Both `SurveyFragment` and `EnterprisesReportsFragment` manually call `SharedPrefManager(requireContext())` even though `SharedPrefManager` is already annotated `@Singleton @Inject constructor` and both fragments carry `@AndroidEntryPoint`. Replacing the manual call with `@Inject lateinit var` eliminates one-off context wiring and makes the dependency visible to Hilt's graph.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L41-L66"}

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt#L43-L56"}

:::task-stub{title="DI: Replace manual SharedPrefManager(context) with @Inject in SurveyFragment and EnterprisesReportsFragment"}
1. In `SurveyFragment.kt` remove the `private lateinit var prefManager: SharedPrefManager` field assignment in `onCreate` (line 66) and replace the field declaration with `@Inject lateinit var prefManager: SharedPrefManager`.
2. In `EnterprisesReportsFragment.kt` delete the `prefData = SharedPrefManager(requireContext())` call in `onCreateView` (line 56) and add `@Inject lateinit var prefData: SharedPrefManager` to the class fields.
3. Verify no other manual `SharedPrefManager(...)` calls remain in those two files.
:::

### Convert Inline Anonymous RecyclerView.Adapter to ListAdapter with DiffUtils.itemCallback

`ReferencesFragment` and `UserProfileFragment` each define an anonymous `object : RecyclerView.Adapter<…>()` inline inside a method, bypassing `ListAdapter`'s diffing and exposing a raw `notifyDataSetChanged` path. Every other adapter in the codebase already uses `ListAdapter` with `DiffUtils.itemCallback`; these two outliers should follow the same pattern.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesFragment.kt#L43-L65"}

:codex-file-citation[codex-file-citation]{line_range_start=475 line_range_end=502 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L475-L502"}

:::task-stub{title="ListAdapter: Replace inline anonymous RecyclerView.Adapter with ListAdapter + DiffUtils.itemCallback in ReferencesFragment and UserProfileFragment"}
1. Extract the inline adapter in `ReferencesFragment.setRecyclerAdapter()` into a named inner class (or top-level file) that extends `ListAdapter<org.ole.planet.myplanet.model.Reference, …>(DiffUtils.itemCallback({ a, b -> a.title == b.title }, { a, b -> a == b }))`.
2. Call `submitList(list)` instead of constructing a new adapter on every call to `setRecyclerAdapter`.
3. Extract the inline adapter in `UserProfileFragment.setupStatsRecycler()` into a named class that extends `ListAdapter<Pair<String,String>, …>(DiffUtils.itemCallback({ a, b -> a.first == b.first }, { a, b -> a == b }))`.
4. Call `submitList(map.entries.map { it.toPair() })` from `setupStatsRecycler`.
:::

### Remove Redundant withContext(Dispatchers.Main) Inside Main-Scoped Coroutine in MyHealthFragment

`selectPatient()` launches on `viewLifecycleOwner.lifecycleScope` which defaults to `Dispatchers.Main`, then immediately wraps its UI assignment block in `withContext(Dispatchers.Main)`. Switching to the same dispatcher that is already active has no effect and adds misleading noise about what dispatcher the code expects to run on.

:codex-file-citation[codex-file-citation]{line_range_start=266 line_range_end=292 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L266-L292"}

:::task-stub{title="Threading: Remove redundant withContext(Dispatchers.Main) wrapper inside lifecycleScope.launch (Main) in MyHealthFragment.selectPatient()"}
1. In `selectPatient()`, remove the `withContext(Dispatchers.Main) { … }` wrapper around the UI assignment block (lines 268–291).
2. Move the `userRepository.getUsersSortedBy(…)` call into a `withContext(Dispatchers.IO) { … }` block to keep the IO work off the main thread, then write the result back directly in the enclosing Main coroutine.
3. Confirm no other `withContext(Dispatchers.Main)` calls exist inside a plain `lifecycleScope.launch` (no explicit dispatcher) in the same file.
:::

### Fix getUserModel() Called Outside repeatOnLifecycle in PersonalsFragment

`PersonalsFragment.setAdapter()` calls `userSessionManager.getUserModel()` at line 67 in the outer `lifecycleScope.launch` block, before the `repeatOnLifecycle(STARTED)` wrapper. This means the user-ID snapshot is captured once on the first launch; if the lifecycle is stopped and restarted (e.g. screen rotation) the flow restarts with the stale captured value instead of re-reading the current session.

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L62-L77"}

:::task-stub{title="Lifecycle: Move getUserModel() call inside repeatOnLifecycle in PersonalsFragment.setAdapter()"}
1. In `setAdapter()`, move `val model = userSessionManager.getUserModel()` to inside the `repeatOnLifecycle(Lifecycle.State.STARTED)` lambda so the user model is refreshed on every lifecycle restart.
2. Verify the `collectLatest` block still receives the correct `model?.id` after the move.
3. Confirm the same pattern is not duplicated in any sibling fragment that collects a user-scoped Flow.
:::

### Add LifeViewModel to Mediate Repository Calls in LifeFragment

`LifeFragment` directly injects `LifeRepository` and calls `lifeRepository.updateVisibility`, `lifeRepository.updateMyLifeListOrder`, and `lifeRepository.getMyLifeByUserId` from inside `getAdapter()` and `refreshList()`. Placing these calls in a `LifeViewModel` removes business logic from the view layer, survives configuration changes without re-querying, and aligns with the existing pattern used by `ChatViewModel`, `TeamsViewModel`, and others.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=72 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L48-L72"}

:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L89-L96"}

:::task-stub{title="ViewModel: Create LifeViewModel and move LifeRepository calls out of LifeFragment"}
1. Create `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` annotated with `@HiltViewModel` that `@Inject constructor`s `LifeRepository` and `UserSessionManager`.
2. Add `fun loadMyLife(userId: String?)` that calls `lifeRepository.getMyLifeByUserId(userId)` and exposes the result as a `StateFlow<List<RealmMyLife>>`.
3. Add `suspend fun updateVisibility(isVisible: Boolean, id: String)` and `suspend fun reorder(list: List<RealmMyLife>)` delegating to `lifeRepository`.
4. In `LifeFragment`, replace `@Inject lateinit var lifeRepository` with `private val viewModel: LifeViewModel by viewModels()` and wire callbacks and `refreshList()` through the new ViewModel.
:::

### Replace Direct mRealm.where() in CoursesFragment UI Layer

`CoursesFragment.onSelectedListChange()` at line 636 queries `mRealm.where(RealmMyCourse::class.java)` directly from the fragment to look up a managed object. The courses repository already provides query methods; this direct access bypasses the data layer and couples the UI to Realm's threading model.

:codex-file-citation[codex-file-citation]{line_range_start=620 line_range_end=651 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L620-L651"}

:::task-stub{title="Data layer: Replace mRealm.where(RealmMyCourse) in CoursesFragment with a CoursesRepository query"}
1. Add a method `suspend fun getCourseById(courseId: String): RealmMyCourse?` to `CoursesRepository` (interface + `CoursesRepositoryImpl`) that wraps the Realm query using `RealmRepository.queryList`.
2. In `CoursesFragment.onSelectedListChange()` replace the `mRealm.where(RealmMyCourse::class.java).equalTo("courseId", …).findFirst()` call with `coursesRepository.getCourseById(it.courseId)` inside a coroutine.
3. Remove any remaining comment block that explains the direct Realm workaround.
:::

### Add JUnit4 and Coroutines-Test Build Infrastructure

The project has no `src/test/` directory and no `testImplementation` entries in `app/build.gradle` or `gradle/libs.versions.toml`. Adding the minimal JUnit4, Kotlin-test, and `kotlinx-coroutines-test` wiring is the prerequisite for every subsequent unit-test task and is a single, self-contained build change.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=209 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/build.gradle#L160-L209"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=42 path=gradle/libs.versions.toml git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/gradle/libs.versions.toml#L1-L42"}

:::task-stub{title="Testing: Add JUnit4, kotlin-test, and kotlinx-coroutines-test build infrastructure"}
1. In `gradle/libs.versions.toml` add version entries: `junit4 = "4.13.2"`, `kotlinx-coroutines-test` using the existing coroutines version `1.10.2`, and `kotlin-test` using the existing Kotlin version `2.3.10`.
2. Add library entries `junit4`, `kotlin-test`, and `kotlinx-coroutines-test` under `[libraries]`.
3. In `app/build.gradle` add `testImplementation(libs.junit4)`, `testImplementation(libs.kotlin.test)`, and `testImplementation(libs.kotlinx.coroutines.test)` inside the `dependencies` block.
4. Create the directory `app/src/test/java/org/ole/planet/myplanet/` so Android Studio recognises the test source set.
:::

### Unit Test JsonUtils.getString() Pure Functions

`JsonUtils.getString(fieldName, jsonObject)` and `JsonUtils.getBoolean(fieldName, jsonObject)` are pure functions with no Android dependencies. They cover null safety, `JsonNull` elements, missing keys, and type mismatches—all branches that are currently untested. A small JUnit4 test file exercises the contract without a device or emulator.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L16-L75"}

:::task-stub{title="Testing: Write JUnit4 unit tests for JsonUtils.getString() and JsonUtils.getBoolean()"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/JsonUtilsTest.kt`.
2. Add a test verifying `getString("key", jsonObject)` returns the string value when the key exists and is a string primitive.
3. Add a test verifying `getString("key", jsonObject)` returns `""` when the value is `JsonNull`.
4. Add a test verifying `getString("missing", jsonObject)` returns `""` when the key is absent.
5. Add a test verifying `getBoolean("flag", jsonObject)` returns `true` and `false` for the respective boolean primitives.
:::

### Unit Test RetryUtils.retry() Suspend Function

`RetryUtils.retry()` is a pure suspend function with no Android or Realm imports. Its retry-count, delay, and `shouldRetry` predicate branches are currently exercised only at runtime. Using `kotlinx-coroutines-test`'s `runTest` with a virtual clock lets the tests run in milliseconds and cover every branch without touching the file system or network.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt#L1-L35"}

:::task-stub{title="Testing: Write coroutines-test unit tests for RetryUtils.retry()"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/RetryUtilsTest.kt`.
2. Add a test that verifies the block is called exactly once when it succeeds on the first attempt.
3. Add a test that verifies the block is retried up to `maxAttempts` times when `shouldRetry` always returns `true`, and that `retry` returns `null` after exhausting attempts.
4. Add a test using `UnconfinedTestDispatcher` and `advanceTimeBy` to confirm the `delayMs` between attempts is respected.
5. Add a test that verifies an exception thrown by the block is swallowed and treated as a failed attempt rather than propagated.
:::

### Unit Test DiffUtils.itemCallback() Factory

`DiffUtils.itemCallback` is a three-argument factory that creates a `DiffUtil.ItemCallback`. The three callbacks (`areItemsTheSame`, `areContentsTheSame`, `getChangePayload`) are passed as lambdas and wired through to the concrete implementation—a path that is easy to regress. Pure JUnit4 tests verify each method delegates correctly without requiring an emulator.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=25 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-roadmap-tasks-again/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L1-L25"}

:::task-stub{title="Testing: Write JUnit4 unit tests for DiffUtils.itemCallback()"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/DiffUtilsTest.kt`.
2. Add a test that verifies `areItemsTheSame` delegates to the provided lambda and returns `true` when the lambda returns `true`.
3. Add a test that verifies `areContentsTheSame` delegates to the provided lambda and returns `false` when the lambda returns `false`.
4. Add a test that verifies `getChangePayload` returns `null` when no `getChangePayload` lambda is supplied (default parameter).
5. Add a test that verifies `getChangePayload` returns the value from the lambda when one is supplied.
:::

# myPlanet Refactor Round — 10 Work Orders

Generated on 2026-10-01. These work orders are prepared for automated coding agents (jules, codex, copilot, devin, openhands, claude, qwen). They are self-contained and independently mergeable in any order.

## Open PRs Checked (33 Open)

All 33 open pull requests were verified prior to generation, and all files touched by them were strictly excluded from candidate work orders:

- **#17812**: fix(courses): prevent crash on null subject
- **#17811**: fix(ui): dismiss loading state after sync complete
- **#17776**: refactor(upload): modernize UploadCoordinator with reactive flows
- **#17694**: refactor(personals): migrate personals to room and flow
- **#17680**: chore(deps): bump androidx.core:core-ktx from 1.15.0 to 1.16.0
- **#17435**: refactor(courses): decouple TakeCourseFragment from base classes
- **#17254**: feat(chat): stream chat responses via websocket
- **#17187**: refactor(dashboard): migrate DashboardViewModel to StateFlow
- **#16623**: feat(navigation): add global navigation coordinator
- **#16594**: refactor(resources): optimize ResourcesFragment layout and adapter
- **#15951**: fix(sync): prevent concurrent sync executions
- **#15825**: refactor(teams): split TeamsRepository by sub-concerns
- **#15824**: refactor(news): migrate VoicesFragment to Compose
- **#15820**: fix(events): resolve duplicate meetup notification events
- **#15808**: refactor(repository): standardize CRUD methods across repositories
- **#15559**: fix(db): handle migration fallback gracefully
- **#15267**: test(sync): add unit tests for AdaptiveBatchProcessor
- **#15266**: test(upload): add unit tests for BulkDocsUploader
- **#15226**: refactor(di): clean up redundant module bindings
- **#15108**: fix(events): correct date formatting in EventsAdapter
- **#14883**: refactor(models): normalize entity primary keys
- **#14650**: chore(deps): bump com.android.tools.build:gradle
- **#14427**: refactor(activities): migrate ActivitiesRepository to Room
- **#13928**: feat(survey): support offline survey attachments
- **#13848**: fix(audio): release media player resources in AudioRecorder
- **#13657**: chore(deps): bump org.jetbrains.kotlin:kotlin-gradle-plugin
- **#13604**: refactor(ui): extract BaseMemberFragment shared logic
- **#13415**: refactor(voices): streamline reply threading
- **#13355**: test(viewmodels): expand ViewModel test coverage
- **#13287**: fix(search): debounce search input queries
- **#10993**: refactor(ui): update Material 3 theme styling
- **#8175**: feat(health): export health records to CSV
- **#4075**: feat(maps): cache offline map tiles locally

## Roadmap Reference
1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests
*North star (never scheduled directly, never blocked):*
9. kotlin multiplatform: a platform-free kotlin core — repositories, models, sync/upload logic and use cases end up with zero android.* imports
10. compose multiplatform: every compose screen from 6 stays portable — state hoisted into viewmodels, no android views inside composables, no direct R.*

---

### Task 1: Deduplicate Step Status Queries in `playstore.sh`
- **Roadmap goal**: 7. Optimize remaining performance hotspots (Workflow quick win)
- **Target file**:
  - `.github/scripts/playstore.sh`
- **Context & Problem**:
  In `check_release_build()` (lines 91–131 of `.github/scripts/playstore.sh`), the script fetches the list of jobs for a workflow run using `api "/repos/$GITHUB_REPOSITORY/actions/runs/$id/jobs"`. It extracts each job's ID, and then executes a separate API request `api "/repos/$GITHUB_REPOSITORY/actions/jobs/$job_id"` for each job to inspect `.steps[]` conclusions. However, GitHub's Actions API endpoint `/repos/$GITHUB_REPOSITORY/actions/runs/$id/jobs` already returns the complete `.steps[]` array for each job in the initial response. Making repetitive per-job calls consumes unnecessary GitHub API rate limit allowance and adds avoidable round-trip latency to release monitoring.
- **Proposed change**:
  Update `check_release_build()` in `.github/scripts/playstore.sh` to extract the required step conclusions directly from the initial `/runs/$id/jobs` response using `jq`. Remove the inner loop that calls `api "/repos/$GITHUB_REPOSITORY/actions/jobs/$job_id"`.
- **Estimated blast radius**: ~25 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Validate script syntax using `bash -n .github/scripts/playstore.sh`.

---

### Task 2: Streamline Release Metadata Queries in `playstore-quota.sh`
- **Roadmap goal**: 7. Optimize remaining performance hotspots (Workflow quick win)
- **Target file**:
  - `.github/scripts/playstore-quota.sh`
- **Context & Problem**:
  `history_limit_events()` (lines 85–98 of `.github/scripts/playstore-quota.sh`) queries the GitHub Releases API with `/releases?per_page=50`. It downloads complete release objects including Markdown release descriptions, contributor metadata, and asset lists, but only parses `.published_at` and `.tag_name`. In `forecast()` (lines 122–136), it parses the entire stream repeatedly. This wastes memory and network bandwidth during quota checks.
- **Proposed change**:
  In `.github/scripts/playstore-quota.sh`, update the `gh api` call in `history_limit_events()` to extract only the needed fields `[.[] | {published_at: .published_at, tag_name: .tag_name}]` via `--jq`, reducing the JSON payload size by over 90% and speeding up quota projection calculations.
- **Estimated blast radius**: ~15 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Validate script syntax using `bash -n .github/scripts/playstore-quota.sh`.

---

### Task 3: Sort and Truncate Slow Test Class Report in `test_timing_summary.py`
- **Roadmap goal**: 7. Optimize remaining performance hotspots & 8. Improve code health and add tests
- **Target file**:
  - `.github/scripts/test_timing_summary.py`
- **Context & Problem**:
  In CI test workflows, `test_timing_summary.py` parses JUnit test results XML files from `app/build/test-results` to warn when unit test durations exceed the 400s threshold. Currently, it prints an unsorted list of slow classes without ranking by execution time or highlighting the cumulative percentage of total run duration, making it harder for developers to immediately identify the biggest test bottlenecks.
- **Proposed change**:
  In `.github/scripts/test_timing_summary.py`, sort slow test classes in descending order of duration, calculate each class's percentage of total shard execution time, and display the top 10 slowest test classes in a clean, formatted Markdown table before truncating.
- **Estimated blast radius**: ~35 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Run `python3 -m py_compile .github/scripts/test_timing_summary.py` and run a test parse against `app/build/test-results`.

---

### Task 4: Eliminate Per-Test Bytecode Re-instrumentation in `ConfigurationsRepositoryImplTest.kt`
- **Roadmap goal**: 7. Optimize remaining performance hotspots & 8. Improve code health and add tests
- **Target file**:
  - `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`
- **Context & Problem**:
  `ConfigurationsRepositoryImplTest.kt` takes 25.4s in CI Shard 1 (accounting for >4% of the entire shard duration). Lines 36 and 42 call `mockkStatic(Log::class)` in `@Before` and `unmockkStatic(Log::class)` in `@After` across 26 individual test methods. Repeatedly redefining bytecode on the JVM for `android.util.Log` per test causes massive CPU overhead and garbage collection pressure.
- **Proposed change**:
  In `ConfigurationsRepositoryImplTest.kt`, migrate the static mock initialization and teardown of `Log::class` from `@Before`/`@After` to `@BeforeClass` and `@AfterClass` companion object methods (or use relaxed static mocking once for the suite), eliminating 25 redundant bytecode transformations.
- **Estimated blast radius**: ~20 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ConfigurationsRepositoryImplTest"`.

---

### Task 5: Optimize MockK Initialization and Teardown in `RetryQueueWorkerTest.kt`
- **Roadmap goal**: 7. Optimize remaining performance hotspots & 8. Improve code health and add tests
- **Target file**:
  - `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt`
- **Context & Problem**:
  `RetryQueueWorkerTest.kt` takes 20.6s in CI Shard 1. Lines 41–55 invoke `mockkObject(WorkManagerProvider)` and `mockkStatic(WorkManager::class)` inside `@Before`, followed by `unmockkAll()` inside `@After` for each test case. This causes repeated heavy class re-instrumentation for AndroidX WorkManager across every test execution.
- **Proposed change**:
  In `RetryQueueWorkerTest.kt`, move `mockkObject(WorkManagerProvider)` and `mockkStatic(WorkManager::class)` to a companion object with `@BeforeClass` and `@AfterClass`, and use `clearMocks(...)` inside `@Before` instead of tearing down the JVM class transformations with `unmockkAll()` per test.
- **Estimated blast radius**: ~25 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.retry.RetryQueueWorkerTest"`.

---

### Task 6: Decouple `DictionaryRepositoryImpl` from `android.util.Log`
- **Roadmap goal**: 1. Finish cleaning the data layer & 9. Kotlin Multiplatform (platform-free Kotlin core)
- **Target file**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`
- **Context & Problem**:
  `DictionaryRepositoryImpl.kt` imports `android.util.Log` (line 3) solely to log an error at line 62 (`Log.e("DictionaryRepositoryImpl", "Failed to insert dictionary data", e)`). The method already returns a sealed domain result `DictionaryLoad.Failed(e)` which encapsulates the exception for callers. The import of `android.util.Log` is the sole Android dependency preventing `DictionaryRepositoryImpl` from being a pure Kotlin Multiplatform module.
- **Proposed change**:
  Remove `import android.util.Log` and the `Log.e(...)` call from `DictionaryRepositoryImpl.kt`, ensuring the caught exception is cleanly returned via `DictionaryLoad.Failed(e)`.
- **Estimated blast radius**: ~5 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest"`.

---

### Task 7: Decouple `RetryRepositoryImpl` from `android.util.Log`
- **Roadmap goal**: 1. Finish cleaning the data layer & 9. Kotlin Multiplatform (platform-free Kotlin core)
- **Target file**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`
- **Context & Problem**:
  In `RetryRepositoryImpl.kt`, lines 3, 123, 131, and 135 import and call `android.util.Log.w` and `android.util.Log.e`. This repository orchestrates operation retries using Room and Kotlin coroutine Mutex. All failure states and error messages are already persistently stored in Room via `markFailed(operation.id, message, code)` and propagated through `RetryOperationResult.TerminalFailure` and `RetryOperationResult.RetryableFailure`. The direct `android.util.Log` dependency couples this core offline resilience logic to the Android runtime.
- **Proposed change**:
  Remove `import android.util.Log`, remove the `TAG` constant, and remove the `Log.w` / `Log.e` calls from lines 123, 131, and 135 in `RetryRepositoryImpl.kt`, making the repository completely free of `android.*` imports.
- **Estimated blast radius**: ~10 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.RetryRepositoryImplTest"`.

---

### Task 8: Decouple `DiagnosticsRepositoryImpl` from `android.util.Log`
- **Roadmap goal**: 1. Finish cleaning the data layer & 9. Kotlin Multiplatform (platform-free Kotlin core)
- **Target file**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
- **Context & Problem**:
  `DiagnosticsRepositoryImpl.kt` line 3 imports `android.util.Log` and invokes `Log.w(TAG, "saveLogToRoom failed", e)` on line 78 and line 100. Because this repository persists APK logs and crash data to Room via `ApkLogDao` and indicates success/failure with boolean return values, calling Android Log on database write failure introduces an unnecessary Android platform binding.
- **Proposed change**:
  Remove `import android.util.Log`, remove `TAG` from the companion object, and remove the `Log.w(...)` calls in `saveLogToRoom()` and `saveLogsToRoom()` in `DiagnosticsRepositoryImpl.kt`.
- **Estimated blast radius**: ~10 lines changed in 1 file; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DiagnosticsRepositoryImplTest"`.

---

### Task 9: Extract Dialog Presentation from `ThemeManager` Service
- **Roadmap goal**: 3. Expand viewmodel and use-case layers, 7. Performance hotspots, 8. Code health, & 9. Kotlin Multiplatform
- **Target files**:
  - `app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt`
  - `app/src/test/java/org/ole/planet/myplanet/services/ThemeManagerTest.kt`
- **Context & Problem**:
  `ThemeManager.kt` is a `@Singleton` service responsible for managing theme preferences (`getCurrentThemeMode()`, `setThemeMode()`). However, it also defines `showThemeDialog(context: Context)` (lines 17–45) which directly constructs an `AlertDialog.Builder`, inflates views, and manipulates dialog window layouts. Because of this UI code in a service, `ThemeManagerTest.kt` requires `@RunWith(RobolectricTestRunner::class)` and takes 13.2 seconds to run.
- **Proposed change**:
  Separate the UI dialog presentation from `ThemeManager`: keep `ThemeManager` focused strictly on state management (`getCurrentThemeMode()`, `setThemeMode()`), and extract or delegate `showThemeDialog` into a UI helper function. In `ThemeManagerTest.kt`, remove the Robolectric runner and Robolectric activity setup, converting the suite into a standard, fast unit test that executes in <0.1s.
- **Estimated blast radius**: ~50 lines changed across 2 files; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.ThemeManagerTest"`.

---

### Task 10: Apply Consistent Coroutine Dispatcher Discipline in `EnterprisesRepositoryImpl`
- **Roadmap goal**: 1. Finish cleaning the data layer & 8. Improve code health and add tests
- **Target files**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt`
- **Context & Problem**:
  In `EnterprisesRepositoryImpl.kt`, `dispatcherProvider: DispatcherProvider` is injected into the constructor (line 24) and used in `attachTeamImage()` and `getReportsFlow()`. However, `addReport()`, `updateReport()`, `archiveReport()`, and `exportReportsAsCsv()` execute database updates, entity mappings, and CSV string formatting directly without wrapping the work in `withContext(dispatcherProvider.io)` or `withContext(dispatcherProvider.default)`. This creates inconsistent threading discipline compared to other repositories in the codebase.
- **Proposed change**:
  In `EnterprisesRepositoryImpl.kt`, wrap `addReport()`, `updateReport()`, and `archiveReport()` in `withContext(dispatcherProvider.io)`, and wrap `exportReportsAsCsv()` in `withContext(dispatcherProvider.default)` for background CPU formatting. Update `EnterprisesRepositoryImplTest.kt` to verify that operations run deterministically on the provided test dispatchers.
- **Estimated blast radius**: ~30 lines changed across 2 files; 0 new dependencies.
- **Verification**:
  Execute `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.EnterprisesRepositoryImplTest"`.

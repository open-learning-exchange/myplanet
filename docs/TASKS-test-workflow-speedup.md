# Work orders — myPlanet refactor round: test-workflow speedup

date: 2026-10-09 · base commit: `f18c50d25010913a0f85ba9d744705349f13b051` (`master`)

open PRs checked: 18076, 18050, 17811, 18045, 18029, 17832, 17694, 13415 (every PR updated within the last week; full file lists pulled for each — anything they touch is off-limits here, including `.github/workflows/test.yml`, `app/build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `SyncRepositoryImpl.kt`, `SyncRepositoryImplTest.kt`, `RatingsRepositoryImplTest.kt`, `DashboardViewModelTest.kt`, and the KMP-phase test files). Older brainstorm/on-hold PRs were not file-checked; their domains are avoided.

focus: the `myPlanet test` workflow runs `testDefaultDebugUnitTest` in 2 shards with a 15-minute budget; `myPlanet build` finishes `assemble{Default,Lite}Debug` inside 10 minutes. These 10 tasks remove wall-clock waste from the suite (per-test Room/Robolectric setup, real-time sleeps, wasted instrumentations, dead tests) and tighten the workflow plumbing, so the test workflow averages faster than the build workflow.

common acceptance for every task: `./gradlew testDefaultDebugUnitTest` stays green (run a `--tests` filter for the touched classes first); no new dependencies, no unused code, no TODO placeholders.

---

### 1. delete the assertion-free `StepExamBenchmarkTest` (roadmap 8)

context: `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt` builds a 500-document `JsonArray`, calls `StepExam.insertCourseStepsExams` 10 times in a loop, and ends with `println("Benchmark result: ${end - start} ms")`. The file contains zero assertions — it can never fail, it only burns suite time, and its per-test `mockkStatic(TextUtils::class)` setup pays MockK instrumentation for nothing. Nothing in the suite consumes the printed number.

files: `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt` (whole file, class `StepExamBenchmarkTest`, `setup()`, `tearDown()`, `testBenchmarkInsertCourseStepsExams()`). Do NOT touch `StepExamTest.kt`, `StepExam.kt`, or `GsonUtils.kt` — they are independent and the real coverage stays.

steps:
1. Confirm `StepExam.insertCourseStepsExams` keeps real coverage in `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt` (open it and verify assertions exist).
2. Delete `StepExamBenchmarkTest.kt`.
3. Run `./gradlew testDefaultDebugUnitTest --tests '*StepExam*'` to confirm the remaining tests pass.
4. Run the full `testDefaultDebugUnitTest` task.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the suite no longer compiles or runs `StepExamBenchmarkTest`; test count drops by exactly one class and wall time drops by its setup+benchmark cost.

size budget: ~55 deleted lines, 1 file

out of scope: do not add a replacement benchmark; do not touch `TeamsRepositoryBenchmarkTest.kt` or `ResourcesRepositoryBenchmarkTest.kt` — they are owned by open PRs.

---

### 2. share one `AppDatabase` across each round-trip test class (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/data/room/AppDatabaseRoundTripTest.kt:33` and `app/src/test/java/org/ole/planet/myplanet/data/room/CollapsedEntitiesRoundTripTest.kt:41` each rebuild the full 38-entity Room schema via `Room.inMemoryDatabaseBuilder` in `@Before` — 14 schema builds total (6+8 tests). One in-memory DB per class plus `clearAllTables()` between tests gives identical isolation at a fraction of the cost.

files: `app/src/test/java/org/ole/planet/myplanet/data/room/AppDatabaseRoundTripTest.kt` (`setUp()`, `tearDown()`); `app/src/test/java/org/ole/planet/myplanet/data/room/CollapsedEntitiesRoundTripTest.kt` (same-named members). Do NOT touch `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt` — PR 13415 owns it.

steps:
1. In each class move `Room.inMemoryDatabaseBuilder(...).build()` from `@Before setUp()` to a lazily-created companion field (or `@BeforeClass`-style `@JvmStatic` setup).
2. Replace per-test DB creation with `database.clearAllTables()` inside `@Before`, keeping `myLibraryDao`-style accessors obtained from the shared instance.
3. Keep `@After` closing the DB only once via `@AfterClass`, or drop it (process teardown closes the in-memory DB anyway — pick the simpler correct option).
4. Run `./gradlew testDefaultDebugUnitTest --tests '*RoundTripTest'`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; both classes pass with a single schema build each; no test observes rows written by a sibling test.

size budget: ~40 changed lines, 2 files

out of scope: no changes to `AppDatabase`, `Converters`, or any DAO; do not reorder or merge the two files.

---

### 3. share the DB and right-size the 1200-row fixtures in `MyLibraryDaoTest` (roadmap 1+7+8)

context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt:25` rebuilds the whole `AppDatabase` for every one of its 15 `runBlocking` tests, and the fixtures at lines 38, 58, 79, 102, 205, 253, 405 and 419 insert 1200 `MyLibrary` rows each — hundreds of thousands of inserts per run, only to prove DAO queries chunk correctly past SQLite's 999 host-variable limit.

files: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt` (`setup()`, `teardown()`, the eight `handles1200…`/large-input tests). Do NOT touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` and no other `*DaoTest.kt`.

steps:
1. Share one `AppDatabase` per class and call `clearAllTables()` in `@Before` (same pattern as task 2 — implement locally, do not reference the other file).
2. Extract the repeated `(1..1200).map { MyLibrary().apply { id = "pk_$i" } }` fixture into one private `insertLibraries(count: Int)` helper.
3. Reduce each oversized fixture to the minimum that still crosses the chunk boundary it tests (1000 rows when the boundary is the 999-variable limit; keep a `>999`-crossing value where a test names a different boundary, and say why in the test name or comment).
4. Run `./gradlew testDefaultDebugUnitTest --tests '*MyLibraryDaoTest'`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; every chunk-boundary assertion still holds; the class finishes visibly faster (far fewer inserts and one schema build).

size budget: ~90 changed lines, 1 file

out of scope: no DAO signature changes, no production code changes, do not split the file.

---

### 4. hoist repeated `mockkStatic` setup to class level in three test files (roadmap 8)

context: `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt` calls `mockkStatic(Log::class)` inside seven separate test bodies (lines 20, 70, 112, 173, 224, 293, 342), `app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt:26` re-instruments `Looper` and `Toast` in `@Before` for every test, and `app/src/test/java/org/ole/planet/myplanet/utils/WebViewSafetyTest.kt:20` re-instruments `Uri` the same way. Each `mockkStatic` call pays bytecode instrumentation that a single class-level setup buys once.

files: `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt` (`setup()`), `app/src/test/java/org/ole/planet/myplanet/utils/WebViewSafetyTest.kt` (`setUp()`). Do NOT touch `SyncTimeLogger.kt`, `Utilities.kt`, `WebViewSafety.kt`, or `SyncTimeLoggerConcurrencyTest.kt` (task 6 owns it).

steps:
1. In `SyncTimeLoggerTest`, move the `mockkStatic(Log::class)` + `every { Log.* }` stubbing into a single `@Before` (or a `@JvmStatic @BeforeClass` when no per-test state needs rebuilding); delete the seven in-test copies.
2. In `UtilitiesTest` and `WebViewSafetyTest`, keep the per-test `@Before` only if individual tests mutate the stubs; otherwise hoist the `mockkStatic` call itself into `@BeforeClass` and leave the `every { … }` stubs per test where they differ.
3. Keep `unmockkAll`/`unmockkStatic` teardown semantics identical — no leaked static mocks into other classes.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*SyncTimeLoggerTest' --tests '*UtilitiesTest' --tests '*WebViewSafetyTest'`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; identical assertion behavior; each affected class pays static instrumentation once per class instead of once per test.

size budget: ~60 changed lines, 3 files

out of scope: no new shared test-rule file, no changes to other mockkStatic users (`NetworkUtilsMockTest`, `ANRWatchdogTest`, …), no assertion edits.

---

### 5. replace `Thread.sleep` polling with tighter waits in two adapter tests (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt` (~line 50, `waitForList`) and `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementsAdapterTest.kt` (lines ~80-115, two duplicated loops) both poll `adapter.currentList.size` with `Thread.sleep(10)` + `ShadowLooper.idleMainLooper()` up to 50 times — up to ~500 ms of real sleep per call site for `AsyncListDiffer` work that usually lands in a few ms.

files: `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt` (`waitForList`), `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementsAdapterTest.kt` (the two inline poll loops). Do NOT touch `ChatHistoryAdapter.kt`, `AchievementsAdapter.kt`, or `TeamResourcesAdapterTest.kt` (task 7 owns it — it already has its own `idleMainLooperUntil` helper).

steps:
1. In each file, collapse the duplicated `idleMainLooper`/`sleep` loop into one private `waitForList(adapter, size)` helper.
2. Drop `Thread.sleep(10)` to `Thread.sleep(1)` (the poll is bounded; the busy-wait just needs to yield) and keep the existing iteration cap as the failure bound.
3. Keep the final `assertEquals` on `adapter.currentList.size` — no semantic change, only faster convergence.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*ChatHistoryAdapterTest' --tests '*AchievementsAdapterTest'`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; both classes still assert the same list contents; the tests no longer spend hundreds of ms sleeping per call.

size budget: ~35 changed lines, 2 files

out of scope: no production-code changes, no awaitility/new dependency, no runner or `@Config` changes (task 7 handles SDK pins).

---

### 6. bound the `SyncTimeLoggerConcurrencyTest` stress loop (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerConcurrencyTest.kt` spawns 10 real threads × 100 iterations of `logApiCall`/`logDbOperation`/`generateSummary` and then waits up to `executor.awaitTermination(10, TimeUnit.SECONDS)` (lines ~37-57). It is one test doing 2000 logger calls on a real executor — the same race coverage holds at a fraction of the volume.

files: `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerConcurrencyTest.kt` (`testConcurrentApiAndDbLogging`). Do NOT touch `SyncTimeLogger.kt`, `SyncTimeLoggerTest.kt` (task 4 owns it), or `TestDispatcherProvider.kt`.

steps:
1. Reduce `threadCount` from 10 to 4 and `iterationsPerThread` from 100 to 50, keeping both as named local constants.
2. Keep `generateSummary()` inside the loop on the same modulo cadence so contention on the summary path is still exercised.
3. Keep `awaitTermination` but tighten the bound to 5 seconds and keep the timeout assertion.
4. Update `expectedCalls` — it is already computed from the two constants, so only the constants change.
5. Run `./gradlew testDefaultDebugUnitTest --tests '*SyncTimeLoggerConcurrencyTest'`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `Total API calls: 200` and `Total Db operations: 200` still asserted via `expectedCalls`; the test's wall time drops roughly four-fold.

size budget: ~10 changed lines, 1 file

out of scope: do not rewrite the test onto virtual time or a single thread — the concurrency signal is the point; do not touch production logger code.

---

### 7. consolidate Robolectric SDK pins onto one sandbox (roadmap 7, helps 9)

context: `app/src/test/resources/robolectric.properties` sets only `application=android.app.Application`, so classes default to the target SDK while `app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt:20` pins `sdk = [Build.VERSION_CODES.O_MR1]` (27), `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt:21` pins `[28]`, and `app/src/test/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesAdapterTest.kt:24` and `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesCardBinderTest.kt:15` pin `[36]`. Every distinct SDK makes each test fork boot another `android-all` sandbox — the suite stages five jars in `app/build.gradle` (26, 27, 28, 33, 36) for this reason. Fewer distinct SDKs = fewer sandbox boots = faster tests; it also reduces the Android-version surface a future KMP commonTest port must replicate (9).

files: `app/src/test/resources/robolectric.properties`; `app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt` (class annotation); `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` (class annotation); `app/src/test/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesAdapterTest.kt` (class annotation — do not touch its `idleMainLooperUntil` helper); `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesCardBinderTest.kt` (class annotation). Do NOT touch `VersionUtilsTest.kt` (its O/P method-level pins test version-gated code — keep), `NotificationUtilsTest.kt` (keep its O pin), `TeamsRepositoryTaskSyncTest.kt`/`TeamsRepositoryBulkInsertTransactionTest.kt` (open-PR owned), or `app/build.gradle`.

steps:
1. Add `sdk=36` to `app/src/test/resources/robolectric.properties` so the default every unpinned class already uses is explicit.
2. Remove the `@Config(sdk = …)` element from the four listed classes, keeping `application = Application::class` where present.
3. Run each touched class locally; if a class genuinely needs its pinned API level (e.g. `PdfThumbnailLoaderTest` fails on 36), restore that one pin and note why in the file — do not force it.
4. Run `./gradlew testDefaultDebugUnitTest` for the affected packages.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the CI step `check the offline robolectric runtime took effect` still passes (no un-staged jar fetched); the four classes run on the shared default sandbox.

size budget: ~15 changed lines, 5 files

out of scope: no production code, no `app/build.gradle` changes (the staged jar list stays), no touching the version-gated test pins listed above.

---

### 8. add a per-package rollup and an enforceable budget flag to `test_timing_summary.py` (roadmap 8)

context: `.github/scripts/test_timing_summary.py` already ranks the slowest classes/tests and `--warn-over` only prints a warning — the workflow at `.github/workflows/test.yml` passes `--warn-over 400`, but nothing can fail or gate on a budget, and there is no package-level view to tell which directory dominates shard wall time. A `--fail-over` exit code plus a package rollup gives the repo the lever it needs to keep tests faster than builds. (The workflow that would adopt the flag is owned by open PRs — this task ships the capability only.)

files: `.github/scripts/test_timing_summary.py` (`main()`, the argparse block, the report section). Do NOT touch `.github/workflows/test.yml` or `.github/workflows/build.yml` (build.yml is task 9's, test.yml is open-PR owned).

steps:
1. Add `--fail-over SECONDS`: after computing the summed test time, print the same warning text and exit non-zero when the sum exceeds the given seconds; keep `--warn-over` unchanged.
2. Add a "Slowest packages" table that sums test time per `org.ole.planet.myplanet.<package>` (third-level segment after `myplanet`) for the top 10 packages, reusing the existing parsed timings.
3. Keep the script dependency-free (stdlib only) and keep output GitHub-flavored markdown.
4. Exercise it locally against a fixture: `python3 .github/scripts/test_timing_summary.py <dir-with-junit-xml> --fail-over 1` must exit 1; `--fail-over 999999` must exit 0.

acceptance: `./gradlew testDefaultDebugUnitTest` unaffected and still green; the script runs standalone on a JUnit XML directory, prints the new package table, and honors `--fail-over` exit codes.

size budget: ~80 changed lines, 1 file

out of scope: no workflow wiring of the new flag, no XML schema changes, no third-party libraries.

---

### 9. tighten the build workflow's restored build-dir cache (roadmap 7+8)

context: `.github/workflows/build.yml` restores `app/build` plus `.gradle/*` wholesale, while `.github/workflows/test.yml` already excludes `app/build/test-results/**` and `app/build/reports/**` from the same cache family — so the build job saves and restores stale report garbage on every run, inflating cache size and restore time. The build job also lacks the stale-Kotlin incremental-cache retry the test job added (`Incremental compilation failed` → `rm -rf app/build/kotlin` → retry), so a stale restored cache can fail a build that would succeed on retry.

files: `.github/workflows/build.yml` (`restore project build dir` step's `path:` list, `build debug as test` step's `run:`). Do NOT touch `.github/workflows/test.yml` (open-PR owned) — copy its patterns, don't edit it.

steps:
1. Add `!app/build/test-results/**` and `!app/build/reports/**` to the `path:` list of `restore project build dir`, matching test.yml's exclusions.
2. Wrap the `./gradlew assemble${FLAVOR^}Debug …` invocation in the same log-tee + `grep -q "Incremental compilation failed"` + `rm -rf app/build/kotlin` + retry block test.yml uses for `run unit tests`.
3. Keep the gradle build-cache env vars and all other steps unchanged.
4. Validate YAML: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"`.

acceptance: workflow file parses; on the next push the build job's cache entry excludes test reports and a stale incremental cache triggers one clean retry instead of a failed run; `./gradlew testDefaultDebugUnitTest` unaffected.

size budget: ~20 changed lines, 1 file

out of scope: no changes to test.yml, no new workflows or actions, no timeout/matrix changes.

---

### 10. merge the three plain-JUnit `DownloadService` satellite tests into one class (roadmap 8)

context: `DownloadService` helper logic is split across three single-purpose pure-JVM test classes — `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceCompletionTest.kt` (28 lines), `DownloadServiceRemainingCountTest.kt` (67 lines), `DownloadServiceUrlSelectionTest.kt` (128 lines) — each paying its own class load/fixture cost for a few assertion-only tests of the same service. Consolidating into one class removes two test-class boots and puts the service's pure-logic coverage in one findable place; the Robolectric `DownloadServiceTest`/`DownloadServiceOnDownloadCompleteTest` stay as they are (different fixture needs).

files: `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceUrlSelectionTest.kt` (rename to `DownloadServiceLogicTest` or keep filename+class name consistent — pick one name for the merged class), `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceRemainingCountTest.kt` (delete after merge), `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceCompletionTest.kt` (delete after merge). Do NOT touch `DownloadService.kt`, `DownloadServiceTest.kt`, `DownloadServiceOnDownloadCompleteTest.kt`, `DownloadServiceResumeTest.kt`, or `DownloadWorkerTest.kt` (open-PR owned).

steps:
1. Move every `@Test` from `DownloadServiceCompletionTest` and `DownloadServiceRemainingCountTest` into the surviving class, preserving test names verbatim.
2. Merge imports and drop the two emptied files; keep the surviving file free of unused imports.
3. Keep every test pure JUnit — no runner, no Robolectric, no shared state between merged tests.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*DownloadService*'`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; total test count unchanged; one file remains covering completion/remaining-count/url-selection assertions.

size budget: ~100 changed lines (mostly moves), 3 files

out of scope: do not merge the Robolectric `DownloadService*` classes, no production changes, no test renames beyond the class.

# TASK-GENERATION PLAN — myPlanet refactor round (test-workflow focus)

Generated 2026-10-09 against branch state `f18c50d`. Ten independently mergeable
work orders serving the roadmap (numbers below reference the roadmap items:
1 data layer, 2 navigation, 3 viewmodel/use-case, 4 DI cleanup, 5 sync/upload,
6 compose migration, 7 performance, 8 code health & tests, 9 KMP core, 10 CMP).

## Open PRs checked (R3)

All 31 open PRs in `open-learning-exchange/myplanet` were enumerated and every
file each touches was recorded (528 files). Off-limits highlights relevant to
this focus:

| PR | Touches (relevant to this focus) |
|----|----------------------------------|
| #18045, #18029, #17832, #17694 (KMP phases 2–4 + scaffold) | `.github/workflows/test.yml`, `app/build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `shared/**`, and ~35 test files incl. `ChatRepositoryTest.kt`, `NotificationDaoTest.kt`, `MyLibraryTest.kt`, `MyPlanetTest.kt`, `NewsTest.kt`, `TeamTaskTest.kt`, `TeamsTasksViewModelTest.kt`, all `services/upload/*Test.kt`, `UploadManagerTest.kt`, `ResourcesRepository*Test.kt`, `SubmissionsRepositoryImplTest.kt`, `NotificationsRepositoryImplTest.kt`, `ConfigurationsRepositoryImplTest.kt`, `ProgressRepositoryImplTest.kt`, `TeamsRepository*Test.kt`, `VoicesRepositoryNewsSyncTest.kt`, `ActivitiesRepositoryImplTest.kt`, `LifeCacheTest.kt`, `LifeRepositoryImplTest.kt`, `BellDashboardViewModelTest.kt`, `TransactionSyncManagerCheckpointTest.kt` |
| #18076 | `RatingsRepositoryImplTest.kt` |
| #18050 | `SyncRepositoryImplTest.kt` |
| #17811 | `DashboardViewModelTest.kt` |
| #14650 | `SubmissionViewModelTest.kt` |
| #16594 | `.github/scripts/labels.sh`, `.github/scripts/test_helper.bash`, `.github/scripts/test_labels.bats` |
| #8175, #4075 | `ci/robo/**`, `.github/workflows/android-release.yml` (Firebase robo) |

Every file cited below was opened and confirmed to exist. None collides with an
open-PR file, and no file appears in more than one task.

## Findings driving the tasks

- `Thread.sleep(10)` busy-wait loops: `AchievementsAdapterTest.kt:84,111`,
  `ChatHistoryAdapterTest.kt:55`, `TeamResourcesAdapterTest.kt:108` — up to
  50 × 10 ms plus a 5 000 ms wall-clock deadline per call.
- 228 `runBlocking` call-sites across 35 test files where `runTest` (virtual
  time) fits; worst offenders are the Robolectric in-memory-Room DAO tests,
  which rebuild the whole 38-entity `AppDatabase` in `@Before` per test method
  (e.g. `MyLibraryDaoTest` 15 tests, `ExamDaoTest` 13).
- 30 test files hand-roll `Dispatchers.setMain`/`resetMain` in
  `@Before`/`@After` while `utils/MainDispatcherRule.kt` (used by 79 other
  files) already exists.
- Dead weight: `LifeRepositoryTest.kt` (2 tests duplicating
  `LifeRepositoryImplTest.kt` coverage) and `StepExamBenchmarkTest.kt` (a
  wall-clock benchmark with no assertion in a correctness suite).
- `.github/scripts/test_timing_summary.py` exists and is wired into `test.yml`
  with `--warn-over 400` — advisory-only and never fails the build.
- `test.yml` uploads test reports only `if: failure()`, so slow-test data is
  invisible on green runs.

## Task 1 — runBlocking → runTest in five remaining DAO test classes

- **Roadmap served**: 8 (virtual-time tests). Also 9 — the DAO layer is the
  boundary KMP keeps Android-side; cheap, deterministic DAO tests shrink the
  per-push cost of guarding that boundary.
- **Files**: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseDaoTest.kt`
  (7 `runBlocking`), `QuestionDaoTest.kt` (3), `TeamTaskDaoTest.kt` (5),
  `TeamLogDaoTest.kt` (4), `SubmitPhotosDaoTest.kt` (3) — all in the same
  directory.
- **What to do**: Mechanical `kotlinx.coroutines.runBlocking` →
  `kotlinx.coroutines.test.runTest` conversion plus import swap. All five build
  an in-memory `AppDatabase` in `@Before` with `allowMainThreadQueries()`; do
  not change DB setup.
- **Acceptance**: the five classes pass; no `runBlocking` remains in them.
- **Size**: ~50 changed lines, 5 files.

## Task 2 — merge `LifeRepositoryTest` coverage and delete it

- **Roadmap served**: 8 (deduplicated coverage, one fewer MockK setup class).
  Also 9 — the KMP phases are consolidating repository tests; removing the split
  now prevents a double port.
- **Files**: delete
  `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryTest.kt`
  (71 lines, 2 tests). Read-only reference (do NOT edit, open-PR file):
  `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`.
- **What to do**: The two tests — `updateVisibility_delegatesToDao` and
  `updateMyLifeListOrder_itemNotInList_weightNotUpdated` — are already covered
  by same-named/stronger tests in `LifeRepositoryImplTest`
  (`updateVisibility_delegatesToDao` at line 168, `updateMyLifeListOrder_*` at
  lines 191/214/222). Verify this mapping by reading both files, then delete
  `LifeRepositoryTest.kt` only. If any assertion turns out to be unique, drop
  that case and report it instead of editing the PR-touched file.
- **Acceptance**: suite passes; the deleted behaviors remain asserted by
  `LifeRepositoryImplTest`.
- **Size**: ≤71 deleted lines, 1 file.

## Task 3 — delete the assertion-less wall-clock `StepExamBenchmarkTest`

- **Roadmap served**: 8 ("less is more"). Also 7 — benchmarking belongs to
  macrobenchmark/profiling, not the per-push unit gate; its
  `println("Benchmark result: … ms")` output is asserted on by nothing.
- **Files**: delete
  `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt`
  (~66 lines; single test `testBenchmarkInsertCourseStepsExams` builds 500
  `JsonObject`s, times `StepExam.insertCourseStepsExams` with
  `System.currentTimeMillis()`, asserts nothing about the result).
- **What to do**: Verify no other file references it, then delete.
- **Acceptance**: suite passes; one less MockK-static agent instrumentation per
  run.
- **Size**: ~66 deleted lines, 1 file.

## Task 4 — replace `Thread.sleep` polling in the `ui/user` + `ui/chat` adapter tests

- **Roadmap served**: 8 (faster, deterministic tests). Also 10 indirectly —
  adapter tests that assert on `currentList` state instead of sleeping are the
  pattern Compose-state tests will reuse.
- **Files**:
  `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementsAdapterTest.kt`
  (loops at lines 80–87 and 107–114),
  `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt`
  (`waitForList` at lines 51–60).
- **What to do**: Both files poll `ShadowLooper.idleMainLooper()` +
  `Thread.sleep(10)` up to 50× waiting for `AsyncListDiffer` to commit. Replace
  the polling loops with a deterministic wait: drive the differ's background
  executor via `Robolectric.flushBackgroundThreadScheduler()` (or
  `shadowOf(Looper.getMainLooper()).idle()` until `currentList` reaches the
  expected size). Keep every existing assertion identical. No production-code
  changes.
- **Acceptance**:
  `./gradlew testDefaultDebugUnitTest --tests "*AchievementsAdapterTest" --tests "*ChatHistoryAdapterTest"`
  passes; no `Thread.sleep` remains in either file.
- **Size**: ~30 changed lines, 2 files.

## Task 5 — make `TeamResourcesAdapterTest.idleMainLooperUntil` deadline deterministic

- **Roadmap served**: 8. (Not combined with Task 4 to keep file-disjointness.)
- **Files**:
  `app/src/test/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesAdapterTest.kt`
  (`idleMainLooperUntil` at lines 104–110, caller at line 97).
- **What to do**: The helper loops `ShadowLooper.idleMainLooper()` +
  `Thread.sleep(10)` against a wall-clock deadline of 5 000 ms. Replace
  wall-clock polling with a bounded, sleep-free wait: drive the background
  differ executor via `Robolectric.flushBackgroundThreadScheduler()` (or
  `shadowOf(application.backgroundLooper).runToEndOfTasks()`) after each
  `idleMainLooper()`, with a fixed iteration cap and a hard
  `assertTrue(condition())` at the end. Keep or trim the existing explanatory
  comment at lines 95–96. No production-code changes.
- **Acceptance**: `--tests "*TeamResourcesAdapterTest"` passes; no
  `Thread.sleep` or `System.currentTimeMillis()` deadline remains in the file.
- **Size**: ~15 changed lines, 1 file.

## Task 6 — runBlocking → runTest in the two round-trip Room tests

- **Roadmap served**: 8. Also 9 — these are the suite's only real-`AppDatabase`
  converter tests (per their own KDoc); keeping them fast and scheduler-based
  makes them the natural future home of commonized Room-free converter tests.
- **Files**:
  `app/src/test/java/org/ole/planet/myplanet/data/room/AppDatabaseRoundTripTest.kt`
  (12 `runBlocking`),
  `app/src/test/java/org/ole/planet/myplanet/data/room/CollapsedEntitiesRoundTripTest.kt`
  (17 `runBlocking`).
- **What to do**: Swap `kotlinx.coroutines.runBlocking` for
  `kotlinx.coroutines.test.runTest` on every test body; adjust imports. Both
  classes keep `@RunWith(RobolectricTestRunner::class)` and
  `allowMainThreadQueries()`, so the change is mechanical.
- **Acceptance**: `--tests "*AppDatabaseRoundTripTest" --tests "*CollapsedEntitiesRoundTripTest"`
  passes; `runBlocking` count in both files is 0.
- **Size**: ~60 changed lines, 2 files.

## Task 7 — runBlocking → runTest in the four biggest DAO test classes

- **Roadmap served**: 8 and 9, same rationale as Task 1.
- **Files**:
  `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt`
  (15), `ExamDaoTest.kt` (13), `TeamDaoTest.kt` (12), `NewsDaoTest.kt` (11) —
  all in the same directory.
- **What to do**: Mechanical `runBlocking` → `runTest` conversion plus import
  swap. All four build an in-memory `AppDatabase` in `@Before` with
  `allowMainThreadQueries()`; do not change DB setup.
- **Acceptance**: the four classes pass; no `runBlocking` remains in them.
- **Size**: ~110 changed lines, 4 files.

## Task 8 — runBlocking → runTest in six more DAO test classes

- **Roadmap served**: 8 and 9, same rationale as Tasks 1 and 7 (split to stay
  under the file/line budgets and keep file-disjointness).
- **Files**:
  `app/src/test/java/org/ole/planet/myplanet/data/room/dao/UserDaoTest.kt` (10),
  `MeetupDaoTest.kt` (9), `TagDaoTest.kt` (7), `SubmissionDaoTest.kt` (6),
  `AnswerDaoTest.kt` (6), `FeedbackDaoTest.kt` (5) — all in the same directory.
- **What to do**: Identical mechanical conversion to Task 7.
- **Acceptance**: the six classes pass; no `runBlocking` remains in them.
- **Size**: ~95 changed lines, 6 files. If a strict 5-file cap is enforced, drop
  `FeedbackDaoTest.kt`.

## Task 9 — migrate the three heaviest ViewModel tests to `MainDispatcherRule`

- **Roadmap served**: 8 (shared test infrastructure per `docs/TESTING.md`).
  Also 10 — ViewModels are where Compose Multiplatform state will be hoisted;
  their tests should all speak one idiom.
- **Files**:
  `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`
  (`Dispatchers.setMain` at line 56, `resetMain` at 70),
  `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt`,
  `app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt`.
- **What to do**: In each class remove the manual
  `Dispatchers.setMain(testDispatcher)` / `Dispatchers.resetMain()` in
  `@Before`/`@After` and add
  `@get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)` using
  the existing `org.ole.planet.myplanet.utils.MainDispatcherRule`, which accepts
  a `TestDispatcher` constructor parameter (verified, lines 13–15) so each class
  keeps its current dispatcher semantics. Delete now-unused
  `kotlinx.coroutines.test.setMain`/`resetMain` imports.
- **Acceptance**: the three classes pass unchanged; `Dispatchers.setMain`
  appears only inside `MainDispatcherRule.kt`.
- **Size**: ~30 changed lines, 3 files.

## Task 10 — surface slowest-test data on every run and make the timing budget enforceable

- **Roadmap served**: 8 (test-workflow observability; you cannot keep tests
  faster than builds without seeing timings). Also 7 — the summary names the
  app's performance hotspots for free on every push.
- **Files**: modify `.github/scripts/test_timing_summary.py` only (195 lines;
  the open-PR set touches only `labels.sh`, `test_helper.bash`,
  `test_labels.bats` in that directory). **Do not touch
  `.github/workflows/test.yml`** — it is modified by four open KMP PRs.
- **What to do**:
  1. Add a `--fail-class-over SECONDS` flag (distinct from the existing
     advisory `--warn-class-over`): when any class's runtime excluding
     first-test excess exceeds it, print the offenders and `sys.exit(1)`.
     Wire-up in `test.yml` is deliberately left to a later round once the KMP
     PRs merge; document the flag in the module docstring.
  2. In the GitHub step-summary output, always emit a one-line budget footer:
     total summed test time vs the `--warn-over` budget and the count of classes
     over `--warn-class-over`, so green runs (which skip the artifact upload in
     `test.yml`) still show timing data in the job summary.
- **Acceptance**: `python3 .github/scripts/test_timing_summary.py --help` shows
  the new flag; running it against a saved
  `app/build/test-results/testDefaultDebugUnitTest` dir exits 0 under budget and
  1 over budget when `--fail-class-over` is set; existing flags' output is
  unchanged and the workflow's current invocation remains valid.
- **Size**: ~60 changed lines, 1 file.

## Ranking rationale (impact ÷ blast radius)

Deletions and deterministic-wait fixes (2–5) give immediate, zero-risk
wall-clock savings. The `runBlocking → runTest` batches (1, 6–8) are the widest
mechanical win across the suite's heaviest Robolectric+Room classes. The
`MainDispatcherRule` migration (9) cuts boilerplate and flake risk in ViewModel
tests ahead of the Compose state-hoisting roadmap. The timing-summary
enforcement (10) is the leverage point that makes "tests faster than builds"
measurable on every push — highest strategic value, tiny blast radius, and
deliberately decoupled from the PR-contended `test.yml`.

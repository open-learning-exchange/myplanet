# Test-speed refactor round — 10 work orders

date: 2026-10-09 · base commit: `f18c50d` (master, "all: smoother importing (fixes #18089) (#18088)") ·
open PRs checked: 18076, 18050, 18045, 18029, 17832, 17811, 17694, 16623, 16594, 15951, 15825, 15824,
15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415,
13355, 13287, 10993, 8175, 4075 (32 open; full changed-file lists were fetched for every one)

## Where the time goes (measured, not guessed)

Latest master test run [37864186056](https://github.com/open-learning-exchange/myplanet/actions/runs/37864186056)
(`f18c50d`, 2 shards):

| | shard 1/2 | shard 2/2 |
| --- | --- | --- |
| job wall time | 5m26s | 5m29s |
| `run unit tests` step | 4m29s | 4m48s |
| compile and KSP before `:app:testDefaultDebugUnitTest` | ~2m07s | similar |
| `:app:testDefaultDebugUnitTest` itself | ~2m22s | similar |
| summed test time across the 4 forks | 522s (1541 tests, 176 classes) | 543s (1399 tests, 194 classes) |
| of that, first-test excess | 186s | 206s |

`myPlanet build` runs on the same day took 4m–5.5m when they compiled (about 1m when nothing
changed), so a test run is roughly 30–60s slower than a build. The test task has 4 parallel forks, so
every 4s cut from summed test time saves about 1s of wall time. The tasks below remove about
120–160s of summed test time, which is roughly 30–40s of wall time per shard. That is about the gap
to the build.

There are two recurring causes:

1. **Static mocks set up and torn down per test.** Classes call `mockkObject(NetworkUtils)`,
   `mockkObject(UrlUtils)`, `mockkObject(DownloadUtils)` or `mockkStatic(Log::class)` in `@Before`,
   then `unmockkAll()` in `@After`. MockK re-instruments those classes on every test and restores
   them afterwards. That shows up as a flat 0.4–1.5s *median per test*, not as a one-off
   first-test cost (for example `UrlUtilsTest` 0.41s × 44, `DownloadServiceTest` 1.49s × 11,
   `UploadConfigsTest` 0.57s × 18). Fix: mock once per class, clear stubs per test.
2. **One-off Robolectric SDK levels.** Every SDK level the suite pins boots another sandbox in each
   fork that runs one of those tests, which costs 10–14s each time (see `docs/TESTING.md:135`).
   SDK 27 and SDK 28 are each pinned by one or two tests that are not about that API level.

**Hot areas held by open PRs, so not used in any task:** `.github/workflows/test.yml`
(18045, 18029, 17694, 15226), `.github/workflows/build.yml` (15226), `app/build.gradle` (18045, 18029,
17832, 17694, 15824, 15820, 13928, 8175), `gradle/libs.versions.toml`, `settings.gradle`, and the
slowest repository and sync test classes: `UserRepositoryImplTest`, `ActivitiesRepositoryImplTest`,
`LoginSyncManagerTest`, `PersonalsRepositoryImplTest`, `RetryRepositoryImplTest`,
`ConfigurationsRepositoryImplTest`, `DownloadWorkerTest` and `TeamsUploaderTest`, all of which are in
the KMP drafts 18045/18029/17832/17694. Three workflow-level changes are therefore deferred until those
drafts land or close:
- dropping `--warning-mode all --stacktrace` from `test.yml` (about 11,000 of the 12,000 log lines per
  shard are stack traces for the `Configuration.setVisible(boolean)` deprecation warning);
- removing the SDK 27 and 28 entries from `robolectricSdkJars` in `app/build.gradle` once task 1 lands;
- re-tuning `--warn-over 400` in `test.yml`.

**Shared recipe for "mock once per class" (tasks 2, 3, 4, 5, 7, 9).** Every hoisting task below means
exactly this, with no other changes:
- (a) Add a `companion object` holding `@BeforeClass @JvmStatic fun mockStatics()`, which calls the
  listed `mockkObject(...)`/`mockkStatic(...)` once, and `@AfterClass @JvmStatic fun unmockStatics()`,
  which calls `unmockkAll()`. Use `org.junit.BeforeClass`/`org.junit.AfterClass`.
- (b) Keep the per-test `every { ... }` stubs in `@Before` so each test starts from the same answers.
- (c) In `@After`, replace `unmockkAll()` with `clearAllMocks()` (`io.mockk.clearAllMocks`). This
  clears stubs and recorded calls but keeps the instrumentation in place.
- (d) Inside a test body, replace `unmockkObject(X)` or `unmockkStatic(X)` on a class-level mock with
  `clearMocks(X)` (`io.mockk.clearMocks`). A cleared object or static mock falls through to the real
  implementation, which is what those tests wanted.
- (e) Leave mocks that a test creates *inside its own body* (`mockkStatic(...)` used by one test only)
  as they are.
- (f) Remove imports that are no longer used.

**Shared measurement (all test-file tasks).** Before editing, run the target class alone and record
the `time="…"` attribute on the root `<testsuite>` element of
`app/build/test-results/testDefaultDebugUnitTest/TEST-<fully.qualified.ClassName>.xml`. Edit, run it
again, and put both numbers in the PR description.

---

### 1. Move three tests off the one-off Robolectric SDK 27 and 28 sandboxes (roadmap 8 + 7)

context: `SecurePrefsTest` is the only class pinned to API 27 (`utils/SecurePrefsTest.kt:20`
`@Config(sdk = [Build.VERSION_CODES.O_MR1])`). API 28 is pinned only by `utils/PdfThumbnailLoaderTest.kt:21`
`@Config(sdk = [28])` and by the single method `VersionUtilsTest.getVersionCode_should_return_longVersionCode_for_P_and_above`
(`utils/VersionUtilsTest.kt:179`, `@Config(sdk = [Build.VERSION_CODES.P])`). Each extra level boots
another sandbox, costing 10–14s in every fork that hits it (`docs/TESTING.md:135`). None of these
tests is about API 27 or 28: `SecurePrefs.kt:89,97` only branch on `>= N`, `PdfThumbnailLoader.kt` has no SDK
check, and `VersionUtils.kt:27` branches on `>= P`, which the default SDK 36 also satisfies.
files: `app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt` (class-level `@Config`, line 20),
`app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` (class-level `@Config`, line 21),
`app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt` (method-level `@Config` on line 179 only).
Leave alone: the `@Config(sdk = [Build.VERSION_CODES.O])` on `VersionUtilsTest.kt:161` (that test is about
pre-P), `NotificationUtilsTest.kt` (open PR 15825), `TeamsRepositoryBulkInsertTransactionTest.kt` (open PRs) and
`app/build.gradle` (open PRs; its `robolectricSdkJars` map keeps entries 27 and 28 for now).
steps:
1. Delete the `@Config(sdk = [Build.VERSION_CODES.O_MR1])` line from `SecurePrefsTest` so it runs on the default SDK.
   If any of its tests then fails, pin it to `@Config(sdk = [Build.VERSION_CODES.O])` instead (26 is already
   used by other tests). Do not keep 27.
2. Delete the `@Config(sdk = [28])` line from `PdfThumbnailLoaderTest`, with the same fallback to `[Build.VERSION_CODES.O]` if it fails.
3. Delete the `@Config(sdk = [Build.VERSION_CODES.P])` line above
   `getVersionCode_should_return_longVersionCode_for_P_and_above`. The default SDK 36 is ≥ P, so the test still covers that branch.
4. Remove the `org.robolectric.annotation.Config` and `android.os.Build` imports wherever they become unused.
5. Run `grep -rn "sdk = \[\(27\|28\|Build.VERSION_CODES.O_MR1\|Build.VERSION_CODES.P\)\]" app/src/test`. It must print nothing.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.SecurePrefsTest" --tests "org.ole.planet.myplanet.utils.PdfThumbnailLoaderTest" --tests "org.ole.planet.myplanet.utils.VersionUtilsTest"`
passes, and `./gradlew testDefaultDebugUnitTest` stays green. The CI step "check the offline robolectric runtime
took effect" stays green, since no new SDK level is introduced. No app behaviour changes.
size budget: ~6 deleted lines (plus up to 2 imports), 3 files.
out of scope: do not edit `app/build.gradle` or the `robolectricSdkJars` map. Do not touch other `@Config(sdk = …)` users.

---

### 2. Mock `UrlUtils` once per class in `UrlUtilsTest` (roadmap 8 + 7)

context: `UrlUtilsTest` has 44 tests at 18.2s excluding first-test excess, a 0.41s median per test
(shard 1/2, run 37864186056), which makes it the slowest class not held by an open PR. Its
`setUp()` (`utils/UrlUtilsTest.kt:28-39`) calls `mockkObject(UrlUtils)` on line 34, and `tearDown()` calls
`unmockkAll()` on line 43. Nine tests also call `unmockkObject(UrlUtils)` in their body (lines 392, 410, 430,
437, 456, 467, 478, 486, 495) to reach the real implementation. As a result `UrlUtils` is re-instrumented
up to twice per test, under Robolectric.
files: `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt` (`setUp`, `tearDown`, and the nine
in-body `unmockkObject(UrlUtils)` calls). Leave alone: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt`
and its `resetForTesting()`/`init()` calls in `setUp` (lines 31-32). Those must still run before every test.
steps:
1. Apply shared-recipe (a): move `mockkObject(UrlUtils)` from `setUp()` into a class-level `@BeforeClass`, with `unmockkAll()` in `@AfterClass`.
2. Keep `UrlUtils.resetForTesting()`, `UrlUtils.init(mockSpm)` and the `sharedPrefManager` stubs in `setUp()` in their current order.
3. Apply shared-recipe (c): change `tearDown()` from `unmockkAll()` to `clearAllMocks()`.
4. Apply shared-recipe (d): replace each of the nine in-body `unmockkObject(UrlUtils)` calls with `clearMocks(UrlUtils)`.
5. Leave the per-test `mockkStatic(Uri::class)` at line 53 as it is (recipe e), and run the shared measurement.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.UrlUtilsTest"`
passes with all 44 tests, and the class `time` drops by at least 40% against the recorded baseline.
`./gradlew testDefaultDebugUnitTest` stays green. No app behaviour changes.
size budget: ~25 changed lines, 1 file.
out of scope: do not rewrite assertions, do not split the class, and do not touch `UrlUtils.kt`.

---

### 3. Mock the download statics once per class in `DownloadServiceTest` (roadmap 8 + 7)

context: `DownloadServiceTest` runs 11 tests at a 1.49s median per test, 18.5s excluding first-test
excess, the highest per-test median in shard 1/2. `setUp()` (`services/DownloadServiceTest.kt:59-71`)
calls `mockkStatic(ContextCompat::class)`, `mockkObject(DownloadUtils)` and `mockkStatic(Log::class)` on
lines 63-65. `tearDown()` calls `unmockkAll()` on line 75, so a Robolectric test re-instruments three
classes per test.
files: `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt` (`setUp`, `tearDown`).
Leave alone: the per-test `mockkStatic(WorkManagerImpl::class)` (lines 127, 157) and
`mockkStatic(NotificationManagerCompat::class)` (lines 275, 375), and the `ReflectionHelpers.setStaticField`
SDK_INT reset in `tearDown()` (line 77), which must keep running after every test. Also leave alone
`DownloadServiceResumeTest.kt` (open PR 18045) and `DownloadServiceOnDownloadCompleteTest.kt`.
steps:
1. Apply shared-recipe (a) to `ContextCompat`, `DownloadUtils` and `Log` (lines 63-65).
2. Keep the five `every { Log.* }` stubs and `mockPreferences = mockk(relaxed = true)` in `setUp()`.
3. In `tearDown()`, replace `unmockkAll()` with `clearAllMocks()`, keeping the `ReflectionHelpers` line after it.
4. Apply recipe (e) to the four in-test statics. If one of them was undone only by the old `@After unmockkAll()`,
   add a matching `unmockkStatic(...)` in a `finally` inside that test, so it does not leak into the next test.
5. Run the shared measurement.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.DownloadServiceTest"`
passes with all 11 tests, and the class `time` drops by at least 30%. `./gradlew testDefaultDebugUnitTest` stays
green. No app behaviour changes.
size budget: ~25 changed lines, 1 file.
out of scope: no changes to `DownloadService.kt` or `DownloadUtils.kt`, and no merging with the other DownloadService* test classes.

---

### 4. Mock `VersionUtils` and `NetworkUtils` once per class in `UploadConfigsTest` (roadmap 8 + 5)

context: `UploadConfigsTest` runs 18 tests at a 0.57s median per test, 11.2s excluding first-test excess
(shard 2/2). It is a plain JVM test with no Robolectric, so nearly all of that time is MockK
re-instrumentation. `setup()` (`services/upload/UploadConfigsTest.kt:44-71`) calls
`mockkObject(VersionUtils)` (line 46) and `mockkObject(NetworkUtils)` (line 49). `tearDown()` calls
`unmockkAll()` (line 75).
files: `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt` (`setup`, `tearDown`).
Leave alone: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`, and every other test
under `services/upload/`. `UploadCoordinatorTest`, `TeamsUploaderTest`, `PhotoUploaderTest`, `AchievementUploaderTest`,
`VoicesUploaderTest` and `BulkDocsUploaderTest` are all in open PRs 18045/18029/17694.
steps:
1. Apply shared-recipe (a) to `VersionUtils` and `NetworkUtils`.
2. Keep the three `every { … }` stubs and the `UploadConfigs(...)` construction in `setup()`.
3. Apply shared-recipe (c) in `tearDown()`.
4. Remove unused imports, then run the shared measurement.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.upload.UploadConfigsTest"`
passes with all 18 tests, and the class `time` drops by at least 40%. `./gradlew testDefaultDebugUnitTest` stays green.
Uploads behave exactly as before, since only test code changes.
size budget: ~15 changed lines, 1 file.
out of scope: no new test cases, and no edits to `UploadConfigs.kt`.

---

### 5. Drop the dead `TextUtils` mock and hoist `NetworkUtils` in the two model tests (roadmap 8, also 9)

context: `FeedbackTest` runs 15 tests, 9.2s excluding first-test excess (0.62s median), and `MeetupTest` runs
11 tests, 5.4s excluding first-test excess, under Robolectric. Both re-run `mockkObject(NetworkUtils)` per test
(`model/FeedbackTest.kt:31`, `model/MeetupTest.kt:28`) and call `unmockkAll()` in `@After`
(`FeedbackTest.kt:37`, `MeetupTest.kt:34`). That mock is needed because `addDocumentOrigin` defaults its
`androidId` argument to `NetworkUtils.getUniqueIdentifier()` (`utils/DocumentOrigin.kt:7`).
`FeedbackTest` also re-runs `mockkStatic(TextUtils::class)` per test (lines 26-30), but `model/Feedback.kt`
neither imports nor reaches `android.text.TextUtils`, so that mock appears to be dead.
On roadmap 9: the `DocumentOrigin.kt:7` default argument is why a platform-free model still needs an Android-side
mock. This task records the dependency without changing it.
files: `app/src/test/java/org/ole/planet/myplanet/model/FeedbackTest.kt` (`setup`, `tearDown`),
`app/src/test/java/org/ole/planet/myplanet/model/MeetupTest.kt` (`setup`, `tearDown`).
Leave alone: the in-body `mockkStatic(JsonParser::class)` and `unmockkStatic(JsonParser::class)` in `FeedbackTest`
(lines 180, 203, 216). Leave the `@RunWith(RobolectricTestRunner::class)` on `MeetupTest`, because `Meetup.kt:20` uses
`org.json.JSONArray`, which needs the Android runtime. Leave `DocumentOrigin.kt` alone.
steps:
1. In `FeedbackTest`, delete the `mockkStatic(TextUtils::class)` block (lines 26-30) and its import. If any test then
   fails, restore the block, apply shared-recipe (a) to it instead, and say so in the PR description.
2. In both classes, apply shared-recipe (a) to `NetworkUtils`, keeping the `every { NetworkUtils.getUniqueIdentifier() }` stub in `@Before`.
3. In both classes, apply shared-recipe (c). In `MeetupTest` keep `MockKAnnotations.init(this)` in `@Before`.
4. Run the shared measurement for both classes.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.model.FeedbackTest" --tests "org.ole.planet.myplanet.model.MeetupTest"`
passes with 15 and 11 tests, and the combined `time` drops by at least 30%. `./gradlew testDefaultDebugUnitTest` stays green.
No app behaviour changes.
size budget: ~25 changed lines, 2 files.
out of scope: do not change `Feedback.kt`, `Meetup.kt` or `DocumentOrigin.kt`, and do not remove Robolectric from `MeetupTest`.

---

### 6. Delete tests that assert nothing or duplicate cheaper coverage (roadmap 8)

context: Less is more. `model/StepExamBenchmarkTest.kt` contains one test,
`testBenchmarkInsertCourseStepsExams`, that runs `StepExam.insertCourseStepsExams` 5,000 times, has **no
assertion**, and prints "Benchmark result" to stdout. Correctness is already covered by the 8 tests in
`model/StepExamTest.kt`. `utils/CourseRatingUtilsTest.kt:115-155` has two `*_smokeTest` methods that inline-mock
`TextView` and `AppCompatRatingBar` (the whole View hierarchy). One of them took 14.1s of the class's 14.2s
(shard 2/2), while the logic they exercise (`computeRatingDisplay`, `parseRating`) is already tested
directly by the class's 8 other tests. `services/sync/RealtimeSyncManagerTest.kt:59-64`
`testNotifyWithoutCollectorsDoesNotThrow` ends in `assertTrue(true)`.
files: `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt` (delete the file),
`app/src/test/java/org/ole/planet/myplanet/utils/CourseRatingUtilsTest.kt` (delete `showRating_jsonObjectOverload_smokeTest`
and `showRating_ratingSummaryOverload_smokeTest`), `app/src/test/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManagerTest.kt`
(delete `testNotifyWithoutCollectorsDoesNotThrow`). Leave alone: `model/StepExamTest.kt`, `repository/ResourcesRepositoryBenchmarkTest.kt`,
`repository/TeamsRepositoryBenchmarkTest.kt` and `repository/UserRepositoryBulkInsertTest.kt`. Despite their names those three do
assert batching, and they are in open PRs. Also leave `BaseContainerFragmentTest.kt`, which still covers `CourseRatingUtils.showRating` wiring.
steps:
1. `git rm` `StepExamBenchmarkTest.kt`.
2. Delete the two smoke-test methods from `CourseRatingUtilsTest`, then remove imports that become unused (`android.content.Context`,
   `android.widget.TextView`, `androidx.appcompat.widget.AppCompatRatingBar`, `io.mockk.*`, `R`, `RatingSummary` if no other test uses them).
3. Delete `testNotifyWithoutCollectorsDoesNotThrow` from `RealtimeSyncManagerTest`, and drop `assertTrue` from its imports if nothing else uses it.
4. Run `grep -n "CourseRatingUtils" app/src/test/java/org/ole/planet/myplanet/base/BaseContainerFragmentTest.kt`. It must still list the
   `setRatings should call CourseRatingUtils and set average rating when json is provided` test, which is the wiring coverage that remains.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green, with exactly 4 fewer tests than before. Compare
`grep -ho "<testcase " app/build/test-results/testDefaultDebugUnitTest/*.xml | wc -l` before and after. In the CI step summary, `CourseRatingUtilsTest` no longer appears in the 15 slowest classes.
No app behaviour changes.
size budget: ~100 deleted lines, 0 added, 3 files.
out of scope: no new replacement tests, and do not delete any other "benchmark"-named test.

---

### 7. Mock the download helpers once per class in `DownloadUtilsTest` (roadmap 8 + 7)

context: `DownloadUtilsTest` runs 15 tests, 7.6s excluding first-test excess (shard 2/2), under
`AndroidJUnit4`/Robolectric. `setUp()` (`utils/DownloadUtilsTest.kt:37-59`) re-runs `mockkObject(Utilities)` (line 52),
`mockkObject(DownloadService.Companion)` (line 55) and `mockkObject(DownloadUtils, recordPrivateCalls = true)` (line 57),
and `tearDown()` calls `unmockkAll()` (line 64). Two tests call `unmockkObject(DownloadUtils)` in their body (lines 208, 228)
to reach the real implementation.
files: `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt` (`setUp`, `tearDown`, lines 208 and 228).
Leave alone: `DownloadUtils.resetChannelsCreatedForTesting()` in both `setUp` and `tearDown` (it must still run per test),
the `context` spy and SharedPreferences stubs, and `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`.
steps:
1. Apply shared-recipe (a) to the three `mockkObject` calls, keeping `recordPrivateCalls = true` on `DownloadUtils`.
2. Keep `every { Utilities.toast(any(), any()) }` and `every { DownloadUtils["startDownloadWork"](…) }` in `setUp()`.
3. Apply shared-recipe (c) in `tearDown()`, after `resetChannelsCreatedForTesting()`.
4. Apply shared-recipe (d) at lines 208 and 228.
5. Run the shared measurement.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.DownloadUtilsTest"`
passes with all 15 tests, and the class `time` drops by at least 30%. `./gradlew testDefaultDebugUnitTest` stays green.
No app behaviour changes.
size budget: ~20 changed lines, 1 file.
out of scope: do not touch `DownloadWorkerTest.kt` (open PR 18045) or `DownloadUtils.kt`.

---

### 8. Teach the timing summary to flag per-test static-mock churn (roadmap 8)

context: `.github/scripts/test_timing_summary.py` ranks classes by median per test (the
`classes_by_median` table built in `main()`), but it does not say *why* a class is slow. This round
shows the dominant cause is fixable in a few lines (tasks 2–5, 7, 9): `@Before` mocks objects or statics
and `@After` calls `unmockkAll()`. The workflow already runs this script from a checkout with the test
sources present (`test.yml` "summarise slowest tests" step), and `test.yml` itself is locked by open PRs.
So the script is the one place where a hint can reach every future PR's step summary.
files: `.github/scripts/test_timing_summary.py` (`main()`, plus one new helper function next to `_first_test_excess`).
Leave alone: `.github/workflows/test.yml` (open PRs 18045, 18029, 17694, 15226) and the existing tables and their
column order, which people already read.
steps:
1. Add a helper `_static_mock_churn(class_name, src_root)`. It maps a fully qualified class name to
   `<src_root>/<package path>/<SimpleName>.kt`, returning `False` when the file is missing. It returns `True` when the file
   contains `@Before`, at least one of `mockkObject(` / `mockkStatic(` / `mockkConstructor(`, and `unmockkAll()`, but no `@BeforeClass`.
2. Add a `--test-src` argument, defaulting to `app/src/test/java` relative to the current directory.
3. After the "slowest classes per test" table, print a section `### Likely per-test static-mock churn`. It lists the classes with
   ≥5 tests and a median ≥0.30s for which the helper returns `True` (at most `TOP_N`, sorted by seconds), and
   adds a one-line pointer to `docs/TESTING.md` for the fix. When nothing matches, print nothing.
4. Keep the script dependency-free (standard library only) and runnable on Python 3.10+.
acceptance: run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.UrlUtilsTest"` and then
`python3 .github/scripts/test_timing_summary.py app/build/test-results/testDefaultDebugUnitTest`. On base commit `f18c50d`
the new section lists `org.ole.planet.myplanet.utils.UrlUtilsTest`, and it disappears once task 2 has landed.
`python3 .github/scripts/test_timing_summary.py /nonexistent` still prints "No test result XML found" and exits 0.
`./gradlew testDefaultDebugUnitTest` stays green, since no Kotlin changes.
size budget: ~40 added lines, 1 file.
out of scope: no workflow edits, no failing exit codes, and no reading of anything except `.kt` test sources.

---

### 9. Mock the notification statics once per class in `NotificationActionReceiverTest` (roadmap 8 + 7)

context: `NotificationActionReceiverTest` runs 7 Robolectric tests, 10.9s in total, 6.0s excluding first-test
excess and a 0.71s median per test (shard 2/2). `setUp()` (`services/NotificationActionReceiverTest.kt:51-83`)
re-runs `mockkObject(NotificationUtils)` (line 65) and
`mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")` (line 68) on every test. `tearDown()`
calls `unmockkAll()` (line 87). The statics are `NotificationUtils.getInstance` and the top-level
`getBroadcastService(context)` in `di/ServiceDependenciesEntryPoint.kt:18`.
files: `app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt` (`setUp`, `tearDown`).
Leave alone: the per-test `spyk` of the application context and of the receiver, the `Hilt_NotificationActionReceiver`
`injected` reflection block (lines 75-82), the in-test `mockkStatic(android.util.Log::class)` on line 174, and
`app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt`.
steps:
1. Apply shared-recipe (a) to `NotificationUtils` and to the `ServiceDependenciesEntryPointKt` file-class static.
2. Keep `mockNotificationUtils = mockk(relaxed = true)`, `every { NotificationUtils.getInstance(any()) } returns mockNotificationUtils`
   and `every { getBroadcastService(any()) } returns mockk(relaxed = true)` in `setUp()`.
3. Apply shared-recipe (c) in `tearDown()`.
4. If the in-test `Log` static on line 174 was undone only by the old `@After unmockkAll()`, add `unmockkStatic(android.util.Log::class)`
   in a `finally` inside that test. Then run the shared measurement.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.NotificationActionReceiverTest"`
passes with all 7 tests, and the class `time` drops by at least 25%. `./gradlew testDefaultDebugUnitTest` stays green.
Tapping "mark as read" on a notification still behaves as before, since only test code changes.
size budget: ~20 changed lines, 1 file.
out of scope: do not touch `NotificationUtilsTest.kt` (open PR 15825) or `ServiceDependenciesEntryPoint.kt`.

---

### 10. Fix the `Log`-mocking advice in `docs/TESTING.md` and document mock-once-per-class (roadmap 8)

context: `docs/TESTING.md:341` tells contributors that `mockkStatic(Log::class)` plus a stub per overload is
needed "otherwise the JVM has no `android.util.Log` implementation and the test crashes". That is not true for this
project: `app/build.gradle` sets `unitTests.returnDefaultValues = true`, so `Log.*` returns `0` on the plain JVM.
The bad advice is copied into the worker example (`docs/TESTING.md:320-339`, `setUp()` with `mockkStatic(Log::class)`
and `@After unmockkAll()`), which is exactly the per-test churn that tasks 2–5, 7 and 9 remove. 28 test files
currently mock `Log` this way.
files: `docs/TESTING.md` (the worker example around lines 320-339, the paragraph on line 341, and the "Robolectric SDK levels"
section starting at line 133). Leave alone: `docs/CODE_STYLE_GUIDE.md` (open PR 15226), `CLAUDE.md`, and every `.kt` file.
steps:
1. Replace the line-341 paragraph. It should say that `returnDefaultValues = true` makes `Log.*` return 0, so mock `Log` only
   when a test *verifies* a log call. Before writing it, verify the claim by temporarily deleting the `mockkStatic(Log::class)`
   block from `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt`, running that class, and then
   restoring the file. Do not commit that experiment.
2. Rewrite the worker example to the mock-once-per-class shape: a `companion object` with `@BeforeClass`/`@AfterClass` statics,
   stubs in `@Before` and `clearAllMocks()` in `@After`.
3. Add a short subsection, "Mock objects and statics once per class", after the worker example. It explains the 0.4–1.5s
   per-test cost measured in run 37864186056 (cite `UrlUtilsTest` 0.41s × 44 and `DownloadServiceTest` 1.49s × 11) and
   gives the `clearMocks(X)` replacement for in-test `unmockkObject(X)`.
4. In "Robolectric SDK levels", add one sentence. After task 1, the remaining pinned levels are 26 and the default; a new pin
   must reuse one of those unless the test is about that API level.
acceptance: `docs/TESTING.md` renders, with no broken headings or code fences (preview it on GitHub). Every class, file and
line it cites exists at the merge commit. CI runs only the labels check for a doc-only change, which matches
`CLAUDE.md` → "Doc-only pushes start no code workflow".
size budget: ~45 changed lines, 1 file.
out of scope: do not edit any test or build file, and do not mass-remove `Log` mocks. That is follow-up work, once the
open KMP PRs release the files.

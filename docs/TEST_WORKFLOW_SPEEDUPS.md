# Test-workflow speedups — 10 work orders

Work orders for other coding agents. This file is the plan, not an implementation. Snapshot taken 2026-10-09 against `f18c50d`. Re-check open pull requests before executing; the list below is not live.

## Open pull requests (all off-limits for any file they touch)

#18076, #18050, #18045, #18029, #17832, #17811, #17694, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075.

File lists were read for every open PR except the later pages of #15226 (716 files; page 1 has no `app/src/test`, and that PR says the Kotlin app is unchanged). None of the files named below appeared in a fetched list.

Do not edit `.github/workflows/test.yml`, `.github/workflows/build.yml`, `app/build.gradle`, or `settings.gradle`. Those are already in open PRs, so this round cannot change sharding, workers, or the Gradle command. The only legal speedups are inside free test classes.

## What the latest master run actually spent

Run 37864186056 (sha `f18c50d`), jobs 113606872443 (shard 1) and 113606872368 (shard 2). Wall clock about 5m32s. Shard 1 Gradle `BUILD SUCCESSFUL in 4m 28s`. Both shards are over `--warn-over 400`: shard 1 = 1541 tests / 176 classes / 522.1s summed (186.1s first-test excess); shard 2 = 1399 tests / 194 classes / 543.3s (206.5s excess). Paired branch runs are worse: a version-bump build is often ~70–80s while the matching test run is ~235–247s. Master does not run `build.yml`, so the comparison that matters is branch test wall versus branch build wall.

First-test excess is Robolectric or Room sandbox boot. It moves to the next class in the fork if a class is deleted. These orders do not chase that. They cut time the timing script attributed to the test method itself (blank or near-zero excess, high median, or a measured socket timeout).

None of these move roadmap 9 or 10. They stay Android unit tests. They serve **8** (code health and tests). 9 and 10 stay unscheduled.

---

### 1. Drop the CourseRatingUtils widget smoke tests

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/CourseRatingUtilsTest.kt` only.

**Verified premise:**

- Master shard 2: `CourseRatingUtilsTest` 14.2s excluding first-test excess (excess 0.0, median 0.00s, 10 tests). `showRating_ratingSummaryOverload_smokeTest` is 14.1s and is not marked as first-test excess, so that time is inside the method.
- The two smoke tests (`showRating_jsonObjectOverload_smokeTest` at lines 115–133, `showRating_ratingSummaryOverload_smokeTest` at lines 135–155) construct `mockk<AppCompatRatingBar>` with no Robolectric runner.
- `CourseRatingUtils.renderRating` (lines 69–81) only assigns `average?.text`, `ratingCount?.text`, and `ratingBar?.rating` from `computeRatingDisplay`. The eight tests above line 115 already cover `computeRatingDisplay` and `parseRating`, including the user-rating-beats-average bar case that the slow smoke test rechecks.

**Do:**

1. Delete both smoke tests and the imports that become unused (`Context`, `TextView`, `AppCompatRatingBar`, `mockk`, `every`, `verify`, `R`).
2. Leave the eight `computeRatingDisplay` / `parseRating` / `ratingSummary_withUserRating_computesCorrectBarRating` tests as they are.

**Constraints:**

- Do not add `@RunWith(RobolectricTestRunner)`. That would replace a 14s classload with a sandbox boot.
- Do not edit `CourseRatingUtils.kt`. Do not add a dependency.

**Tests:** The remaining eight cases in `CourseRatingUtilsTest` must pass unedited. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.CourseRatingUtilsTest`.

**Out of scope:** `RatingsRepositoryImpl.kt` and `RatingsRepositoryImplTest.kt` (#18076). Any other rating UI test.

---

### 2. Stop mocking UrlUtils on every test

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt` only.

**Verified premise:**

- Master shard 1: `UrlUtilsTest` 18.2s excluding excess (excess only 0.2s, median 0.41s, 44 tests). That median is real per-test cost, not sandbox boot.
- `@Before` (lines 29–39) calls `mockkObject(UrlUtils)` after `UrlUtils.init(mockSpm)`. `@After` calls `unmockkAll()`.
- Only two tests stub the object: `testGetUserImageUrlReturnsCorrectFormattedString` (line 100, `every { UrlUtils.getUrl() }`) and `testGetUserImageUrlWithSpecialCharacters` (line 133). Several later tests immediately call `unmockkObject(UrlUtils)` (`baseUrl memoizes…` at line 392, `getUrl with explicit base…` at line 430, every `redactForLog…` test from line 456) because the class-level mock is in the way.
- `UrlUtils.init` / `resetForTesting` (`UrlUtils.kt` lines 24–54) already swap the `SharedPrefManager`. The real object is what the other 42 tests need.

**Do:**

1. Remove `mockkObject(UrlUtils)` from `setUp`.
2. In the two tests that stub `UrlUtils.getUrl()`, call `mockkObject(UrlUtils)` at the start of that test only, and `unmockkObject(UrlUtils)` at the end of that test (try/finally).
3. Delete the now-redundant `unmockkObject(UrlUtils)` lines in tests that were undoing the setup mock.
4. Stop calling `unmockkAll()` in `tearDown` if the only remaining static mock is `Uri` in `hostUrl fallback behavior when toUri throws Exception` (lines 53–54). Unmock that static in that test. Keep `UrlUtils.resetForTesting()` in setup.

**Constraints:**

- Do not edit `UrlUtils.kt`. Do not drop assertions. Do not add a dependency.
- `header memoizes basic auth header…` (line 352) and `resetForTesting clears cached header` (line 375) must still run against the real object.

**Tests:** All 44 cases in `UrlUtilsTest` must still pass. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.UrlUtilsTest`.

**Out of scope:** `ServerUrlMapper.kt`, `ServerUrlMapperTest.kt` (open PRs). Any production URL change.

---

### 3. Hoist DownloadServiceTest static mocks out of every test

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt` only.

**Verified premise:**

- Master shard 1: `DownloadServiceTest` 18.5s excluding excess, median 1.49s, 11 tests. Highest median of any class with at least 3 tests on that shard. Excess is only 3.7s, so the 1.49s is repeated work.
- `setUp` (lines 59–71) calls `mockkStatic(ContextCompat::class)`, `mockkObject(DownloadUtils)`, and `mockkStatic(Log::class)` plus five `Log.*` stubs, on every test. `tearDown` (lines 73–78) calls `unmockkAll()`, which throws that work away.
- Two tests add their own `mockkStatic(WorkManagerImpl::class)` (`test startService enqueues worker on Android 14…` at line 127, `test startService enqueues worker if startForegroundService throws…` at line 157). Those must stay local.

**Do:**

1. Move the three setup mocks and the `Log` stubs to a JUnit `@BeforeClass` / `@AfterClass` pair (companion object). Re-apply the `every { Log… }` stubs in `@Before` if a test clears them, but do not reconstruct the mocks.
2. Replace `unmockkAll()` in `tearDown` with `clearMocks` / `unmockkStatic` limited to `WorkManagerImpl` in the two tests that mock it. Keep the `SDK_INT` restore at line 77.
3. Leave every assertion and every `ReflectionHelpers.setStaticField(Build.VERSION…)` call where it is.

**Constraints:**

- Do not edit `DownloadService.kt`. Do not merge in `DownloadServiceOnDownloadCompleteTest` or `DownloadServiceResumeTest` (the latter is in an open PR).
- Do not add Robolectric config or a dependency. Stay under ~150 changed lines.

**Tests:** All 11 cases in `DownloadServiceTest` must pass unedited in behavior. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.DownloadServiceTest`.

**Out of scope:** `DownloadWorkerTest`, `DownloadServiceResumeTest`, production download scheduling.

---

### 4. Hoist UploadConfigsTest object mocks out of every test

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt` only.

**Verified premise:**

- Master shard 2: `UploadConfigsTest` 11.2s excluding excess (excess 1.4s, median 0.57s, 18 tests). No Robolectric runner, so this is not sandbox boot.
- `setup` (lines 44–70) calls `mockkObject(VersionUtils)` and `mockkObject(NetworkUtils)` and stubs `getAndroidId`, `getDeviceName`, `getUniqueIdentifier` on every test. `tearDown` (lines 73–76) calls `unmockkAll()`.
- The class rebuilds `UploadConfigs` with the same relaxed mocks every time. The stubs do not change between tests.

**Do:**

1. Move both `mockkObject` calls and their three `every` stubs to `@BeforeClass`. `unmockkAll` once in `@AfterClass`.
2. Keep constructing `UploadConfigs` in `@Before` (tests need a fresh instance). Do not call `unmockkAll` per test.
3. Leave every `fetchPendingItems` / mark-uploaded assertion as it is.

**Constraints:**

- Do not edit `UploadConfigs.kt` (open PR). Do not edit `VersionUtils.kt` or `NetworkUtils.kt`. No new dependency.

**Tests:** All 18 cases in `UploadConfigsTest` must pass. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.upload.UploadConfigsTest`.

**Out of scope:** `TeamsUploaderTest`, `PhotoUploaderTest`, `AchievementUploaderTest`, `UploadCoordinatorTest` (open PRs or a different file).

---

### 5. Make the closed-port reachability test fail fast

**Roadmap:** 8. Does not move 9/10. This is a test of sync reachability, not a change to sync behavior, so it is not roadmap 5.

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperReachabilityTest.kt` only.

**Verified premise:**

- Master shard 2: `isUrlDirectlyReachable returns false for closed port without throwing` took 5.0s and was not first-test excess.
- The test (lines 50–58) binds `HttpServer` on `127.0.0.1:0`, calls `stop(0)`, then calls `isUrlDirectlyReachable`. `ServerUrlMapper.isUrlDirectlyReachable` (lines 115–133) sets `connectTimeout = 5000` and `readTimeout = 5000`.
- On JDK 17, that start-then-stop pattern does not refuse. `HttpURLConnection` HEAD against the just-stopped server waits for the read timeout (measured 5070ms, `SocketTimeoutException: Read timed out`). A never-bound port (`http://127.0.0.1:1`) refuses in 3ms (`ConnectException: Connection refused`) and still returns false from the same catch at line 131.

**Do:**

1. Delete the dummy `HttpServer` in that one test.
2. Call `isUrlDirectlyReachable("http://127.0.0.1:1")` and keep `assertFalse`.
3. Leave the four local-server tests and `withLocalServer` unchanged. Those servers answer and do not hit the timeout.

**Constraints:**

- Do not edit `ServerUrlMapper.kt` or `ServerUrlMapperTest.kt` (both are in open PRs). Do not lower the production 5000ms timeouts.
- Do not use an unresolvable hostname. DNS failure is a different, slower path. Port 1 on 127.0.0.1 is the path that was measured.

**Tests:** All five cases in `ServerUrlMapperReachabilityTest` must pass, and the closed-port case must finish in well under a second. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.sync.ServerUrlMapperReachabilityTest`.

**Out of scope:** `ServerReachabilityProviderTest` (its 5s OkHttp timeouts are builder config, not a waited call in the timing table). `ServerReachabilityWorkerTest`.

---

### 6. Hoist FeedbackTest mocks out of every test

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/model/FeedbackTest.kt` only.

**Verified premise:**

- Master shard 2: `FeedbackTest` 9.2s excluding excess (excess 1.3s, median 0.62s, 15 tests). No Robolectric runner.
- `setup` (lines 24–33) calls `mockkStatic(TextUtils::class)` and `mockkObject(NetworkUtils)` on every test, then stubs `TextUtils.isEmpty` and `NetworkUtils.getUniqueIdentifier()`. `tearDown` calls `unmockkAll()`.
- The `TextUtils.isEmpty` stub is a one-line null/empty check. It does not vary per test.

**Do:**

1. Move both mocks and both stubs to `@BeforeClass`. Unmock once in `@AfterClass`.
2. Delete the per-test `unmockkAll()`.
3. Leave every message-list and serialization assertion as it is.

**Constraints:**

- Do not edit `Feedback.kt` (open PR). Do not edit `NetworkUtils.kt`. No new dependency.

**Tests:** All 15 cases in `FeedbackTest` must pass. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.model.FeedbackTest`.

**Out of scope:** `FeedbackDaoTest` (its 13.9s is first-test excess and will move if touched). Feedback UI.

---

### 7. Hoist DownloadUtilsTest object mocks out of every test

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt` only.

**Verified premise:**

- Master shard 2: `DownloadUtilsTest` 7.6s excluding excess (excess 0.7s, 15 tests).
- `setup` (lines 37–59) calls `mockkObject(Utilities)`, `mockkObject(DownloadService.Companion)`, and `mockkObject(DownloadUtils, recordPrivateCalls = true)` on every test, and stubs `startDownloadWork` via a private-call matcher. `tearDown` (lines 61–65) calls `unmockkAll()` after `resetChannelsCreatedForTesting()`.
- The context spy and preference stubs can stay per test. The three object mocks are identical across tests.

**Do:**

1. Move the three `mockkObject` calls and the `Utilities.toast` / `startDownloadWork` stubs to `@BeforeClass`.
2. Keep `DownloadUtils.resetChannelsCreatedForTesting()` in `@Before` and `@After`. Do not call `unmockkAll()` per test. Unmock the three objects once in `@AfterClass`.
3. Leave `createChannels_executesOncePerProcess` and the other cases' assertions unchanged.

**Constraints:**

- Do not edit `DownloadUtils.kt` or `DownloadService.kt`. Do not touch `DownloadServiceTest.kt` (task 3 owns that file). No new dependency.

**Tests:** All cases in `DownloadUtilsTest` must pass. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.DownloadUtilsTest`.

**Out of scope:** Download worker and resume tests.

---

### 8. Hoist BasePermissionActivityTest static mocks out of every test

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/base/BasePermissionActivityTest.kt` only.

**Verified premise:**

- Master shard 2: `BasePermissionActivityTest` is 19.5s total, of which 12.5s is first-test excess that will move if the class stays. The remaining 7.1s is real (median 0.61s, 12 tests).
- `setup` (lines 24–34) calls `mockkStatic(ContextCompat::class)` and `mockkStatic(Process::class)` and stubs `Process.myUid()` on every test. `teardown` (lines 36–40) unmocks both.
- One test adds `mockkStatic(NotificationManagerCompat::class)` and unmocks it itself (lines 71–79). That one stays local.

**Do:**

1. Move the `ContextCompat` and `Process` static mocks, and the `myUid` stub, to `@BeforeClass`. Unmock them once in `@AfterClass`.
2. Keep the relaxed `BasePermissionActivity` mock and the `callOriginal()` stubs in `@Before`. Those are cheap next to `mockkStatic`.
3. Do not claim the 12.5s excess. This task only removes the repeated static-mock setup.

**Constraints:**

- Do not edit `BasePermissionActivity.kt`. Do not add Robolectric. Do not delete permission cases. No new dependency.

**Tests:** All 12 cases must pass. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.base.BasePermissionActivityTest`.

**Out of scope:** `BaseRecyclerFragmentTest`, `BaseExamFragmentTest`, `BaseDashboardFragmentLayoutTest`. Their slow rows are first-test excess.

---

### 9. Stop launching a new dashboard activity per navigation case

**Roadmap:** 8. Does not move 9/10. This does not introduce navigation architecture (roadmap 2); it only stops paying for four activity boots in one test class.

**Files:** `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt` only.

**Verified premise:**

- Master shard 2: class 8.5s, median 1.34s, 4 tests. `testOpenCallFragment_initialNavigationAddsToBackStack` is 5.0s with 4.1s excess. The other three each call `Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup()` again (lines 68, 87, 113).
- `TestDashboardElementActivity` is in the same file (lines 25–34). Each test only needs a clean back stack and then checks `openCallFragment` / `onClickTabItems`.
- The four cases are: initial add (lines 54–64), reopen visible tag does not duplicate (lines 67–83), switch away and back pops instead of restacking (lines 86–109), teams tab uses the `teams` tag (lines 112–123).

**Do:**

1. Build and `setup()` one `TestDashboardElementActivity` per test class, not per test. Between cases, pop the back stack to empty and `executePendingTransactions()` so each case still starts clean.
2. Keep all four assertions. They may live in one method or four methods that share the activity. Do not drop a case.
3. Leave `TestDashboardElementActivity.onCreate` as it is.

**Constraints:**

- Do not edit `DashboardElementActivity.kt` or any fragment it opens. Do not touch `ResourcesFragmentActionTest.kt` (same activity type, different file, not this task).
- Stay in this one file. No new dependency. Under ~150 changed lines.

**Tests:** The four navigation assertions must still hold. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.dashboard.DashboardElementActivityNavigationTest`.

**Out of scope:** `HealthExaminationActivityTest` (production activity is in an open PR; leave that test file alone). `DashboardViewModelTest` (#17811).

---

### 10. Delete the EditText mocks in ViewExtensionsTest

**Roadmap:** 8. Does not move 9/10.

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/ViewExtensionsTest.kt` only.

**Verified premise:**

- Master shard 2: `ViewExtensionsTest` 6.1s excluding excess (excess 0.0, 5 tests). `textChanges_emitsInitialValue` is 5.9s and is not first-test excess, so the time is inside that method.
- That method (lines 26–34) and `textChanges_emitsOnTextChanged` (lines 37–61) both do `mockk<EditText>` with no Robolectric runner. The class has no `@RunWith`. Loading `EditText` on the plain JVM is the cost.
- The three `debounceDistinct_*` tests (lines 63–101) do not touch `EditText`. Their `delay(100)` / `delay(400)` calls run inside `runTest`, and the class total shows they are not the 5.9s. `debounceDistinct` (`ViewExtensions.kt` lines 23–24) is `debounce(timeoutMillis).distinctUntilChanged()`.

**Do:**

1. Delete `textChanges_emitsInitialValue` and `textChanges_emitsOnTextChanged`.
2. Delete the imports that become unused (`Editable`, `TextWatcher`, `EditText`, `mockk`, `every`, `slot`, `verify`, `flow.first`, `take`, `launch`, `UnconfinedTestDispatcher`).
3. Leave the three `debounceDistinct` tests unchanged.

**Constraints:**

- Do not add `@RunWith(RobolectricTestRunner)` to "keep" the widget tests. A new sandbox costs more than 5.9s.
- Do not edit `ViewExtensions.kt`. Do not extract a test seam. No new dependency.

**Tests:** The three `debounceDistinct` cases must pass unedited. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ViewExtensionsTest`.

**Out of scope:** `setupHintSpinner` (untested, not this round). Any other widget-mock test.

---

## Self-check

- R1: 10 tasks, each one file, mergeable in any order.
- R2: no shared file. Production types are named only as do-not-touch.
- R3: open PRs listed. Cited files were absent from every fetched PR file list. #15226 later pages were not fetched; page 1 has no `app/src/test`.
- R4: every path, class, and function above was opened. Line numbers match the tree at `f18c50d`.
- R5: each order is one file, well under 150 lines, no new dependency, no TODO, no unused code left behind.
- R6: no implementation in the generating run. This document is the deliverable.

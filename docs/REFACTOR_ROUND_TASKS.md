# myPlanet refactor round — task-generation work orders

date: 2026-11-10 · base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 (master)
open PRs checked (35): 18555, 18554, 18551, 18540, 18539, 18455, 18076, 18045, 18029, 17832, 17811, 17694, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13355, 13287, 10993, 8175, 4075

Every file cited below was opened on the base commit and confirmed to exist; none appears in any open PR above.
Evidence from CI logs of the last master test run (42782082676), the last master release run (42782082588), and the last branch builds:
both test shards exceed the 400s `--warn-over` threshold (shard 1/2 summed 499s, shard 2/2 summed 436s; the report names the slowest classes),
and the release log shows the deprecated `android.enableJetifier=true` warning
(not actionable this round — `app/build.gradle` and the remaining blocker dependency `circular-progress-view` are owned by open PRs).

---

### 1. stop swallowing coroutine cancellation in RetryUtils.retry (roadmap 8+9)
context:
app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt:20 catches `catch (e: Exception)` inside the retry loop,
so a `kotlinx.coroutines.CancellationException` is treated as a retryable failure and the loop keeps going after the caller cancelled.
RetryUtils.kt:32 then prints the last exception with `lastException?.printStackTrace()`, the only diagnostic.
The file currently has zero `android.*` imports — keeping it that way preserves a platform-free util for the KMP core (roadmap 9),
so do NOT fix the logging by adding `android.util.Log`.
files:
app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt (`retry`, lines 13-33).
Do NOT touch its callers (ApiClient.kt, UserRepositoryImpl.kt, SyncActivity.kt, DashboardViewModel.kt, ChatViewModel.kt) — several are owned by open PRs.
steps:
1. add `catch (e: CancellationException) { throw e }` before the existing `catch (e: Exception)` and import `kotlinx.coroutines.CancellationException`.
2. delete `lastException?.printStackTrace()` and the now-unused `lastException` variable and its assignment.
3. keep the return contract (`T?`, null on exhausted retries) unchanged.
4. run the existing RetryUtils tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*RetryUtils*"` green;
`grep -c "import android" app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` prints 0;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~6 changed lines, 1 file.
out of scope: no signature changes, no new logging framework, no caller changes.

---

### 2. merge the single-test Robolectric class RetryInterceptorEventTest into plain JUnit RetryInterceptorTest (roadmap 8, unblocks 9)
context:
the last master test run flags RetryInterceptorEventTest as one test costing 10.54s —
a whole Robolectric sandbox boot (app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt:24 `@RunWith(RobolectricTestRunner::class)`)
paid for a single assertion test (lines 39-40).
The sibling RetryInterceptorTest.kt has no RunWith (plain JUnit4) and already mocks everything else;
the only Android type the event test touches is `android.content.Intent`, which mockk can intercept with `mockkConstructor(Intent::class)`.
files:
app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt (add the migrated test).
app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt (delete).
Do NOT touch app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt or utils/TestTimeProvider.kt.
steps:
1. in RetryInterceptorTest.kt add a test equivalent to `testRetryEventUrlContainsOnlyEncodedPath` using `mockkConstructor(Intent::class)`
   and `verify { anyConstructed<Intent>().putExtra("url", "/db/_find") }` plus the attempt/delay extra assertions.
2. reuse the existing `notCancelledCall()` and `createResponse()` helpers already in RetryInterceptorTest.kt.
3. delete RetryInterceptorEventTest.kt.
4. run the interceptor tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*RetryInterceptor*"` green;
RetryInterceptorEventTest.kt no longer exists;
the retry-event coverage (url contains only the encoded path, no credentials, attempt=1, delay=initialDelay) still runs;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~45 changed lines, 2 files (one deletion).
out of scope: no changes to RetryInterceptor itself, no BroadcastService changes.

---

### 3. hoist per-test static mocks in UserRepositoryImplTest to class-level setup (roadmap 8)
context:
UserRepositoryImplTest was the slowest class on master shard 1/2 at 33.7s across 30 tests (1.03s median).
Its @Before (app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:82) re-instruments
`mockkObject(UrlUtils)` (line 84), `mockkObject(NetworkUtils)` (line 88) and `mockkStatic(Log::class)` (line 91) before every test
and tears them down in @After (lines 150-154) — the static-mock churn the CI timing report heuristics flag.
The same per-test `mockkObject(SecurePrefs)` pattern repeats inline at lines 555, 580, 601 and 618.
files:
app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt only.
Do NOT touch UserRepositoryImpl.kt (open PRs own it).
steps:
1. move the `mockkObject(UrlUtils)`, `mockkObject(NetworkUtils)` and `mockkStatic(Log::class)` registration into a `@BeforeClass @JvmStatic` companion function,
   and the matching `unmockkObject`/`unmockkStatic` into `@AfterClass @JvmStatic`.
2. in @Before keep the per-test `every { ... }` stubbing and add `clearMocks(...)`-style resets for the hoisted objects so tests stay isolated.
3. apply the same hoist for the repeated `mockkObject(SecurePrefs)` blocks, keeping each test's own stubs.
4. run the class and compare its summed time in `app/build/test-results/testDefaultDebugUnitTest/` against the 33.7s baseline.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*UserRepositoryImplTest*"` green with all 30 tests still running;
class total time in the JUnit XML measurably below 33.7s;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~60 changed lines, 1 file.
out of scope: no production code changes, no test deletion or weakening of assertions.

---

### 4. hoist per-test static mocks in LoginSyncManagerTest to class-level setup (roadmap 8)
context:
LoginSyncManagerTest cost 24.2s for 19 tests (1.18s median) on master shard 1/2.
Its @Before (app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:54-76) re-instruments four statics per test —
`mockkStatic(Log::class)` (line 56), `mockkStatic(Base64::class)` (line 60), `mockkObject(UrlUtils)` (line 65), `mockkObject(AndroidDecrypter.Companion)` (line 69) —
and @After (lines 83-86) calls `unmockkAll()`. This is the same churn pattern as task 3, in a different file.
files:
app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt only.
Do NOT touch services/sync/LoginSyncManager.kt (open PRs own it).
steps:
1. hoist the four static registrations into a `@BeforeClass @JvmStatic` companion function
   and replace `unmockkAll()` with `@AfterClass @JvmStatic` unmocks of exactly those four.
2. keep the per-test `every { ... }` stubbing in @Before, adding `clearMocks(...)` for the hoisted objects so stubs do not leak between tests.
3. run the class and compare summed time against the 24.2s baseline.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*LoginSyncManagerTest*"` green with all 19 tests still running;
class total time in the JUnit XML measurably below 24.2s;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~40 changed lines, 1 file.
out of scope: no production code changes, no changes to other sync tests (SyncManagerTest.kt is owned by an open PR).

---

### 5. hoist per-test static mocks in VoicesRepositoryNewsSyncTest to class-level setup (roadmap 8)
context:
VoicesRepositoryNewsSyncTest cost 14.7s for 18 tests on master shard 1/2.
Its @Before (app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:69) registers
`mockkObject(UrlUtils)` (line 71) and `mockkObject(NetworkUtils)` (line 73),
and @After (lines 96-99) unmocks them; another `mockkObject(NetworkUtils)` appears inline at line 302.
Same churn pattern as tasks 3 and 4, in a Robolectric class — the Robolectric runner stays, only the mockk churn goes.
files:
app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt only.
Do NOT touch VoicesRepositoryImpl.kt (open PRs own it) and do NOT remove the RobolectricTestRunner.
steps:
1. hoist the `mockkObject(UrlUtils)` and `mockkObject(NetworkUtils)` registrations into `@BeforeClass @JvmStatic`
   and the unmocks into `@AfterClass @JvmStatic`.
2. keep per-test stubbing in @Before with `clearMocks(...)` resets for isolation, including the test at line 302 that re-stubs NetworkUtils.
3. run the class and compare summed time against the 14.7s baseline.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*VoicesRepositoryNewsSyncTest*"` green with all 18 tests still running;
class total time in the JUnit XML measurably below 14.7s;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~35 changed lines, 1 file.
out of scope: no production code changes, no Robolectric config changes.

---

### 6. parallelize the independent loads in TakeCourseViewModel.loadCourse (roadmap 3+7+8)
context:
app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:108 `loadCourse` awaits five repository calls strictly sequentially,
although `getUserModel()` (line 117), `getCourseById(courseId)` (line 118) and `getCourseSteps(courseId)` (line 124) are independent of each other —
only line 125's `getCourseProgress` needs the user.
On a slow tablet this serializes three database round-trips before the course screen renders.
The same method swallows failures with `e.printStackTrace()` at line 131 and would also swallow `CancellationException`.
files:
app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt only.
Do NOT touch CoursesRepositoryImpl.kt or ProgressRepositoryImpl.kt (open PRs own them).
steps:
1. inside the existing `viewModelScope.launch`, start `getUserModel()`, `getCourseById()` and `getCourseSteps()` as `kotlinx.coroutines.async` deferreds
   and `await()` them where their values are first needed (course null-check first, then steps, then user for progress).
2. in the catch block, rethrow `kotlinx.coroutines.CancellationException` before handling other exceptions.
3. replace `e.printStackTrace()` with `Log.e(TAG, "loadCourse failed", e)` and add a `private const val TAG = "TakeCourseViewModel"` companion.
4. run the existing TakeCourse tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*TakeCourse*"` green;
the take-course screen still shows course, steps, progress and blocking step correctly on device/emulator;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~25 changed lines, 1 file.
out of scope: no new UiState variants, no fragment/layout changes, no repository changes.

---

### 7. replace printStackTrace with tagged logging and rethrow cancellation in two viewmodels (roadmap 8)
context:
two ViewModels still report failures via `e.printStackTrace()`, which bypasses logcat tagging and swallows `CancellationException`.
HealthExaminationViewModel is internally inconsistent: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:120 prints the stack trace
while the same file's `encryptSign` (line 136) already does it right with `catch (e: CancellationException) { throw e }` plus `Log.w(TAG, ...)`,
and a `TAG` companion already exists at the bottom of the file.
ResourcesViewModel.loadResources (app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:109) catches `Exception`
inside `withContext(dispatcherProvider.io)`, consuming cancellation of the load job it manages via `loadJob?.cancel()`.
files:
app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt (line 120, inside `saveExamination`).
app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt (line 109, inside `loadResources`).
Do NOT touch HealthExamination.kt or other health/resources classes (open PRs own several).
steps:
1. in HealthExaminationViewModel.saveExamination, add `catch (e: CancellationException) { throw e }` before the existing catch
   and replace the printStackTrace with `Log.e(TAG, "Saving examination failed", e)` (reuse the existing TAG; add the `kotlinx.coroutines.CancellationException` import).
2. in ResourcesViewModel.loadResources, add the CancellationException rethrow before the existing catch
   and replace printStackTrace with `Log.e(TAG, "Loading resources failed", e)`, adding a `private const val TAG = "ResourcesViewModel"` companion.
3. run the health and resources viewmodel tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*HealthExamination*" --tests "*ResourcesViewModel*"` green;
`grep -rn "printStackTrace" app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` returns nothing;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~14 changed lines, 2 files.
out of scope: no behavior change to success paths, no retry logic, no new UiState fields.

---

### 8. match SubmissionsPayloadBuilder's own cancellation pattern in submissionPayload (roadmap 1+8)
context:
app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt has the correct idiom in `getTeamByIdOrNull` at line 112
(`catch (e: CancellationException) { throw e }` before the generic catch),
but the `suspend fun submissionPayload` (declared at line 118) ends with `catch (e: Exception) { e.printStackTrace() }` at lines 162-164 —
swallowing cancellation in suspending data-layer code and reporting payload-build failures only to stderr.
files:
app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt only (lines 118 and 162-164).
Do NOT touch SubmissionsRepositoryImpl.kt or other submission classes (open PRs own them).
steps:
1. in `submissionPayload`'s catch chain, add `catch (e: CancellationException) { throw e }` before `catch (e: Exception)` (the import already exists at line 8).
2. replace `e.printStackTrace()` with `Log.e(TAG, "Building submission payload failed", e)`,
   adding the `android.util.Log` import and a `private const val TAG = "SubmissionsPayloadBuilder"` companion.
3. keep returning the partially built `jsonObject` exactly as today.
4. run the submissions tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*Submission*"` green;
a cancelled coroutine calling submissionPayload now propagates CancellationException;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~8 changed lines, 1 file.
out of scope: no payload schema changes, no repository interface changes.

---

### 9. rethrow cancellation instead of Result.retry() in NetworkMonitorWorker (roadmap 5+8)
context:
app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt:43-50 `doWork` wraps `scheduleServerReachabilityCheck()`
in `catch (e: Exception) { Result.retry() }` (lines 48-49).
When WorkManager stops the worker, the `CancellationException` is swallowed and converted into `Result.retry()`,
so a cancelled worker re-enqueues itself — the opposite of what the sync/upload consolidation needs from well-behaved workers.
files:
app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt only.
Do NOT touch ServerReachabilityWorker.kt or other sync/upload classes (open PRs own them).
steps:
1. add `catch (e: CancellationException) { throw e }` before the existing `catch (e: Exception)` in `doWork`
   and import `kotlinx.coroutines.CancellationException` (`java.util.concurrent.TimeUnit` is already imported; add the coroutines import next to it).
2. keep the `Result.retry()` path for genuine failures unchanged.
3. add a small unit test (NetworkMonitorWorker is a CoroutineWorker with no injected dependencies)
   or extend an existing one if present under app/src/test to assert cancellation propagates.
acceptance:
`./gradlew testDefaultDebugUnitTest` green;
cancelling the worker no longer schedules a retry (verify via the new/updated test);
the network-reconnection reachability check still schedules after 30s on real failure.
size budget: ~30 changed lines including test, 1-2 files.
out of scope: no changes to scheduling policy, constraints, or ServerReachabilityWorker.

---

### 10. inject TimeProvider into PublicSurveyViewModel instead of System.currentTimeMillis (roadmap 3+4)
context:
app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt:62 stamps `launchTime = System.currentTimeMillis()` directly,
while the DI graph already provides `TimeProvider` via di/TimeModule.kt and other ViewModels (e.g. BellDashboardViewModel) already consume it.
Hard-coding the clock makes the upload-recency check at line 74 (`submission.lastUpdateTime < launchTime`) untestable without waiting real time —
the existing test asserts only `viewModel.launchTime > 0` (app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt:69)
and adds a real second at line 104.
files:
app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt (constructor + line 62).
app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt (constructor call at line 43, assertions at lines 69 and 104).
Do NOT edit utils/TimeProvider.kt (an open PR owns it) — only inject the existing interface.
steps:
1. add `private val timeProvider: TimeProvider` as a constructor parameter of PublicSurveyViewModel and import org.ole.planet.myplanet.utils.TimeProvider.
2. replace `System.currentTimeMillis()` with `timeProvider.now()`.
3. in the test, pass the existing deterministic `TestTimeProvider` (app/src/test/java/org/ole/planet/myplanet/utils/TestTimeProvider.kt) as the new constructor argument.
4. set the provider's `currentTime` explicitly in the affected tests, assert launchTime against that fixed value instead of `> 0`,
   and compute the `lastUpdateTime` at line 104 relative to it.
5. run the survey tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*PublicSurveyViewModelTest"` green with deterministic time assertions;
`./gradlew testDefaultDebugUnitTest` green;
public-survey flow still stamps launch time on survey load and skips stale submissions on upload.
size budget: ~20 changed lines, 2 files.
out of scope: no changes to TimeModule, TimeProvider or other ViewModels; no behavior change beyond the injected clock.

---

signed: copilot kimi k3 high

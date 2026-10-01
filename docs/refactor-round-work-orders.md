# myPlanet refactor round — 10 work orders

Generated 2026-10-01 as a task-generation brief for coding agents. Each work order is independently mergeable in any order; no file appears in more than one task.

**Open-PR check (R3):** all 33 currently open pull requests were listed and their touched files excluded. Highlights: all `values*/strings.xml`, `app/build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, and `.github/workflows/{build,test,release,labels}.yml` are off-limits this round (touched by open PRs #17812, #17694, #13928, #15226, #16594, #15824, #15825, #13355). So are the teams/voices/resources/courses/surveys/dashboard hot paths and the sync-writer/DAO surfaces in #15808.

**Workflow-log findings:**
- Last master `test.yml` run (159c0e8, job shard 2/2): 577s summed vs the 400s warn threshold. Slowest classes: `UploadManagerTest` 45.0s, `CourseRatingUtilsTest` 20.4s, `RetryRepositoryImplTest` 20.1s, `ExamDaoTest` 19.9s, `BaseDashboardFragmentLayoutTest` 18.2s, `CourseDaoTest` 17.2s, `BasePermissionActivityTest` 16.7s, `RetryInterceptorTest` 16.5s, `VoicesActionsTest` 13.9s, `BaseResourceFragmentTest` 12.4s, `ThemeManagerTest` 12.4s, `FeedbackTest` 12.0s, `NetworkUtilsMockTest` 11.2s, `DownloadUtilsTest` 11.2s, `DownloadServiceResumeTest` 11.0s.
- Last `release.yml` run tail: ~14s of Gradle cache save/restore per job; Node.js 20 deprecation warning for `dogi/sign-android-release@v5.1`, `dogi/upload-google-play@v1.1.4`, `dogi/upload-release-action@v2.9.0` (forced onto Node 24). `release.yml` is off-limits this round.

---

## Task 1 — de-Robolectricize `RetryInterceptorTest`

**Roadmap:** 8 (code health/tests); also 9 (the class under test sits in `data/api/`, a future platform-free core member — its tests should need no Android).

**Target files (verified to exist):**
- `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`

**Problem:** `RetryInterceptorTest` runs under `@RunWith(RobolectricTestRunner::class)` (lines 28–29) even though nothing in the test needs the Android framework — it mocks `Interceptor.Chain`/`Call` and uses `TestTimeProvider`/`SystemTimeProvider`. It also burns real wall-clock time: `testInterruptedExceptionDuringDelay` (line 207) constructs a `RetryInterceptor` with the real `SystemTimeProvider`, sets `initialDelay = 2000L`, and spawns a thread that sleeps 100ms before interrupting. On the last master run this class logged 16.5s.

**What to do:**
1. Drop `@RunWith(RobolectricTestRunner::class)` and `@Config(...)`; run as a plain JUnit4 test (verify no Robolectric dependency remains — `BroadcastService` is mockk'd relaxed).
2. In `testInterruptedExceptionDuringDelay`, replace the `SystemTimeProvider` + 2000ms real delay with a `TestTimeProvider`-style fake whose `sleep` blocks on a latch until the test thread interrupts the worker (or calls `Thread.currentThread().interrupt()` from within `sleep` after one invocation), so the test completes in milliseconds and asserts the same `IOException("Interrupted during retry delay")`. Keep the fake inside the test file — do not edit `TimeProvider.kt` (Task 2 owns it).

**Done when:** the class has no `org.robolectric` imports, no `Thread.sleep` in test code, and `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.api.RetryInterceptorTest"` passes visibly faster (target <2s summed).

---

## Task 2 — collapse redundant `TimeProvider.sleep` default

**Roadmap:** 8 (code health); also 9 (`utils/TimeProvider.kt` is pure Kotlin minus one `android.os.SystemClock` import — a KMP-core candidate; shrinking its surface helps).

**Target files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt`

**Problem:** `TimeProvider` declares a default `sleep` (line 17, `Thread.sleep(millis)`) and `SystemTimeProvider` overrides it with the identical body (lines 25–27). The override is dead duplication.

**What to do:** delete the override in `SystemTimeProvider` so it inherits the interface default. Confirm no test depends on the override being declared on the concrete class (grep `SystemTimeProvider` in `app/src/test` first).

**Done when:** `./gradlew assembleDefaultDebug` compiles and `SystemTimeProvider` declares only `now()` and `elapsedRealtime()`.

---

## Task 3 — hoist the Gson singleton out of model `toGson()` hot paths (models A)

**Roadmap:** 1 (data-layer cleanup) + 7 (performance hotspot); also 9 (less hidden global state in entities).

**Target files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/utils/GsonUtils.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/Answer.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/MyPlanet.kt`

**Problem:** the model-layer `.toGson()` serialization helpers in `Answer.kt` (lines 41, 49, 62), `MyTeam.kt` (170, 184, 234) and `MyPlanet.kt` (51, 68, 115) build a `com.google.gson.Gson` per call site. Gson instantiation allocates reflection caches; these paths run per-document during sync and upload. A shared lazy singleton already exists: `GsonUtils.gson` (`utils/GsonUtils.kt:64`).

**What to do:** in the three model files, route serialization through `GsonUtils.gson` instead of constructing `Gson()`/`GsonBuilder().create()` locally. The shared instance is a plain `Gson()`; if any call site passes custom adapters (check before swapping), leave that site and note it.

**Done when:** no `Gson(` construction remains in the three files; existing model unit tests pass unchanged.

---

## Task 4 — hoist the Gson singleton (models B)

**Roadmap:** 1 + 7; also 9.

**Target files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/model/ExamQuestion.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/News.kt` — conditional: open PRs #13415/#10993 touch `News.kt`; if either is still open at execution time, drop this file from the task and do the remaining three only.

**Problem & fix:** identical to Task 3, for the remaining model files whose `toGson()`/serialize helpers construct their own Gson (verified call sites: `ExamQuestion.kt:63,133`, `Achievement.kt:154,161`, `HealthExamination.kt:126`, `News.kt:118`). Splitting across two tasks keeps each under 150 changed lines and independently mergeable.

**Done when:** the listed files contain no `Gson(` constructions; unit tests for these models pass.

---

## Task 5 — `UploadManagerTest` runtime triage

**Roadmap:** 8; indirectly 5 (the test file guards the upload workflow the roadmap wants consolidated — it must be fast enough to run willingly).

**Target files (verified):**
- `app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt`

**Problem:** at 45.0s this is the single slowest test class on master (7.8% of shard 2). It is 433 lines / 24 `@Test`s using `mockkStatic`/`spyk` with `SystemClock` and class-level `@BeforeClass`/`@AfterClass` mock setup (`clearAllMocks`/`unmockkAll`). Static mocking of `SystemClock`/`Log` forces MockK to transform JDK classes per test, which dominates the runtime.

**What to do:**
1. Read the file; find every `mockkStatic(SystemClock::class)` / `mockkStatic(Log::class)` usage and replace with constructor-seam fakes: `UploadManager` already receives collaborators, so inject the values the statics provided (a `TimeProvider` for elapsed time is already available via `di/TimeModule.kt`; for `Log`, use `mockkStatic` once in `@BeforeClass` rather than per-test, or delete the log assertions entirely — the logs are not the behavior under test).
2. Convert per-test `spyk` rebuilds into one shared subject built in `@Before` with resettable fakes.
3. Do not delete any assertions that check real behavior; only remove double-mocking of the same statics.

**Done when:** the class sums under ~15s in a local `./gradlew testDefaultDebugUnitTest --tests "*UploadManagerTest"` run and all 24 tests still pass with the same assertions on behavior.

---

## Task 6 — pure-JVM `CourseRatingUtilsTest`

**Roadmap:** 8; also 10 (rating display is a future Compose leaf; its logic should already be proven without views).

**Target files (verified):**
- `app/src/test/java/org/ole/planet/myplanet/utils/CourseRatingUtilsTest.kt`
- `app/src/main/java/org/ole/planet/myplanet/utils/CourseRatingUtils.kt` (edit only to *add* a pure function if the seam below needs it — no other main-code files)

**Problem:** `showRating_withValidRatingSummary_setsAverageTotalAndUserRating` alone logged 20.1s on master — one test, 3.5% of the shard. The test is plain JUnit + MockK over `Context`/`TextView`/`AppCompatRatingBar`; the cost is MockK's relaxed mocking of Android widget classes (each `mockk<AppCompatRatingBar>(relaxed = true)` pays class-transformation cost).

**What to do:** replace the four relaxed widget mocks with `mockk(relaxed = false)` plus explicit `every { ... } just Runs` stubs only for the setters actually exercised, which avoids relaxed-mock interception of every method. If that still exceeds ~2s, the sanctioned alternative is moving the numeric formatting (`"%.2f".format(...)`, count formatting) into a pure function `CourseRatingUtils.formatRating(...)` tested without any widgets, leaving the widget assignment untested at this level. Keep the existing JsonObject + RatingSummary overload coverage either way.

**Done when:** the class keeps the same 7 test cases, stays Robolectric-free, and sums under ~3s.

---

## Task 7 — per-class slow-test warnings in `test_timing_summary.py`

**Roadmap:** 8 (test infra health).

**Target files (verified):**
- `.github/scripts/test_timing_summary.py` (invoked from `test.yml:97` — confirm the path at execution time)

**Do NOT edit `.github/workflows/test.yml`** — it is touched by open PRs #17694 and #15226.

**Problem:** the workflow already warns when a shard sums over 400s (`--warn-over 400`), but nothing surfaces per-class regressions — a class can double in time without tripping anything as long as the shard stays under. The 577s shard-2 run shows slow classes accumulating (15 classes ≥ 11s).

**What to do:** in `.github/scripts/test_timing_summary.py` only, add a per-class warn line: when a single class exceeds 15s, emit a `⚠️ slow class` row (the data is already parsed for the "15 slowest test classes" table, so this is a thresholded print, not new parsing). Optionally also print classes in the 8–15s band in a collapsible section. No workflow-file edits; no new Python dependencies (stdlib only).

**Done when:** running the script locally against any test-results fixture (or a synthetic XML in `/tmp`) prints the new warning rows; the existing `--warn-over 400` behavior is unchanged.

---

## Task 8 — `isNetworkConnectedFlow` startup allocation

**Roadmap:** 7 (performance); also 9 (narrows what `NetworkUtils` needs from Hilt, clarifying the seam a KMP connectivity abstraction will replace).

**Target files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`
- new test file permitted: `app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsInitTest.kt`

**Problem:** `NetworkUtils` builds its `ConnectivityManager`, `WifiManager`, `BluetoothManager`, Hilt `CoreDependenciesEntryPoint`, `@ApplicationScope` coroutine scope, and a `stateIn`-backed flow through nine `ResettableCache`s (lines 42–114). `isNetworkConnectedFlow` (96–102) starts collecting `_currentNetwork` the moment it's read. On cold start, a caller asking only for the device name can still pay for entry-point resolution and scope creation.

**What to do:**
1. Verify each member's actual dependency chain and remove unused caches from `resettableCaches` (lines 104–114) only if genuinely unreferenced by the touched members.
2. Specifically: `getCustomDeviceName` (line 243) pulls `sharedPrefManager` → `coreEntryPoint` → Hilt resolution; that's required, leave it. The win is ensuring `getDeviceName()`/`getUniqueIdentifier()` (lines 235–241) — pure `Build.*` reads — don't transitively initialize the Hilt-dependent caches. If the `stateIn` initial-value read at line 99 forces `coroutineScope`, gate it so the scope cache resolves only when `startListenNetworkState()` is called.

**Done when:** a new unit test demonstrates `getDeviceName()` works after `resetForTesting()` without touching `CoreDependenciesEntryPoint`; existing `NetworkUtilsMockTest` still passes.

---

## Task 9 — `Utilities` object split prep: extract pure text helpers

**Roadmap:** 8; also 9 (the extracted functions have zero `android.*` imports — they are exactly the platform-free core).

**Target files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt`
- new files permitted: `app/src/main/java/org/ole/planet/myplanet/utils/TextNormalizeUtils.kt` and `app/src/test/java/org/ole/planet/myplanet/utils/TextNormalizeUtilsTest.kt` (check first whether `UtilitiesTest.kt` already exists; if so, add coverage there instead)

**Problem:** `object Utilities` (97 lines) mixes pure string functions — `checkNA` (68), `toHex` (76), `normalizeText` (80), `isValidEmail` (22) — with Android-bound ones (`toast`, `getMimeType` via `MimeTypeMap`, `warmUp`). The pure four are callable from a KMP core today but drag `android.util.Patterns`, `Handler`, `Toast` imports along.

**What to do:**
1. Create `TextNormalizeUtils.kt` (`object TextNormalizeUtils`) containing `checkNA`, `toHex`, `normalizeText`, and `isValidEmail` reimplemented without `android.util.Patterns` (use the simplest regex that keeps existing behavior; `Patterns.EMAIL_ADDRESS` is unavailable in pure-JVM tests, which is itself evidence for this task).
2. In `Utilities.kt`, delete the four functions and replace with delegating one-liners (`fun checkNA(s: String?) = TextNormalizeUtils.checkNA(s)`) so no call site changes.
3. Add unit tests covering current behavior (N/A fallback, hex encoding, diacritic stripping, email valid/invalid) — these run without Robolectric.

**Done when:** `TextNormalizeUtils.kt` has no `android.*` imports; delegations keep every existing caller compiling; new tests pass.

---

## Task 10 — `TimeUtils` formatter cache audit

**Roadmap:** 7 (performance hotspot); also 9 (`TimeUtils` is ~90% pure `java.time` — one `android.text.format.DateUtils` call at line 81 and seven `Log.w` calls are its only Android ties).

**Target files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt`

**Conditional (R3):** open PR #15951 lists `utils/TimeUtils.kt`. If #15951 is still open when the executor starts, skip the primary task and execute the fallback instead. (Verified against the PR file list on 2026-10-01.)

**Problem (primary):** `TimeUtils` keeps a `ConcurrentHashMap` formatter cache (`formatterFor`, line 31) yet several call sites may bypass it with fresh `DateTimeFormatter` allocations per call (factories at lines 51–63 — verify at edit time which are cached and which allocate per call). Date formatting runs per-row in list adapters, so per-call formatter allocation is a real cost.

**What to do (primary):** route every formatter used in a per-row path through `formatterFor(...)`, so each (pattern, zone) pair is built once. Do not change any output format; add a unit test asserting two calls to the same public format function return identical strings for fixed inputs.

**Done when (primary):** all formatter factory functions either delegate to `formatterFor` or are inlined into it; existing `TimeUtils` tests pass unchanged.

**Fallback (if #15951 open):** `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt` — the class logged 11.2s on the last master run. Read it and `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`; remove any `Log` static-mocking / Robolectric usage in the test via the same seam-injection approach as Tasks 5/6, touching main code only if a test seam requires it.

---

## Self-check (P5)

- **R1** — 10 tasks (Task 10 has a conditional fallback; only one body executes). Each is independently mergeable: disjoint file sets, no cross-task dependencies.
- **R2** — file sets disjoint (T1 explicitly forbidden from editing `TimeProvider.kt`, owned by T2; T6's optional main-code edit is restricted to adding a pure function in `CourseRatingUtils.kt`).
- **R3** — open PRs listed in the header; colliding candidates (test.yml shard bumps, release.yml Node-20 action bumps, `TeamsRepositoryImpl` splitting, `labels.sh`, strings) discarded; T10 conditional on #15951, T4 conditional on #13415/#10993.
- **R4** — every cited path, class, function, and line number was opened and confirmed in the generation session; slow-test figures come from `test.yml` run 36632327656 (shard 2/2 job).
- **R5** — each task is well under ~150 changed lines / ~5 files; no new dependencies; no TODO placeholders.
- **R6** — no implementation code; this plan is the deliverable.

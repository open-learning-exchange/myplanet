# Refined task backlog — scored and sorted

date: 2026-09-29 · base commit: `3a01b49` (master)
supersedes for execution purposes: `docs/COMBINED_TASK_PLAN_2026-09-29.md` (which holds the
three-plan comparison and the verification notes behind these scores).

Assembled from three plans, all written against `354410d` — 48 commits and 149 files behind
current master. Every claim below was re-verified against `3a01b49`; where master has moved or a
source plan was wrong, the task text here is the corrected one, not the original.

| Source | Branch · file | Prefix |
|---|---|---|
| test speedup | `claude/optimistic-thompson-yyvh9m` · `docs/test-speedup-tasks-2026-09-25.md` | **A** |
| UI→data leaks | `devin/1790366658-refactor-task-plan` · `docs/REFACTOR_TASKS_2026-09-25.md` | **B** |
| data boundaries | `codex/refactor-repository-boundaries-for-data-layers` · `docs/repository-boundary-work-orders-2026-09-25.md` | **C** |

---

## Scoring

**score = (impact × 10 + payback × 6 + confidence × 4) − (cost × 5 + risk × 6)**, normalised to 1–100.

| Factor | 1 | 5 |
|---|---|---|
| **impact** | nobody notices | every user or every contributor, every run |
| **payback** | one-off | compounds — unblocks later work or pays on every push |
| **confidence** | the plan's premise did not survive verification | premise re-verified line-by-line on `3a01b49` |
| **cost** | < 20 lines, 1 file | > 150 lines across a base class or an 850-line file |
| **risk** | test-only, reversible | user-visible path with no unit coverage |

**Bands.** **80+** do now, no debate · **60–79** do this round · **40–59** do when there is review
capacity · **20–39** queue behind the rest · **< 20** do not run as written.

---

## The sorted backlog

| Rank | Score | ID | Task | Cost | Files |
|---:|---:|---|---|:---:|:---:|
| 1 | **94** | A2 | `EdgeToEdgeUtilsTest` → plain JUnit | XS | 1 |
| 2 | **92** | C10 | Preserve coroutine cancellation in `UploadToShelfService` | S | 2 |
| 3 | **88** | C1 | Deterministic `RetryDao.getPending` order | XS | 2 |
| 4 | **86** | A10 | Timing summary: separate warm-up from real slowness | M | 1 |
| 5 | **84** | A1 | Hoist per-test static mocks to class scope | S | 2 |
| 6 | **82** | B7 | `SyncActivity` post-sync bootstrap → `ResourceDownloadCoordinator` | S | 2 |
| 7 | **80** | C8 | `ProgressViewModel` → `getCurrentUserId()` | XS | 2 |
| 8 | **78** | A5 | `ThemeManagerTest`: build the activity only where used | S | 1 |
| 9 | **76** | A3 | Drop dead Hilt from utils tests; fold `NetworkUtilsStateTest` | S | 3 |
| 10 | **74** | A8 | `AndroidDecrypterTest`, `LeadersViewModelTest` → plain JUnit | XS | 2 |
| 11 | **72** | A9 | Extract `parseVitalReading` from `HealthExaminationActivity` | S | 4 |
| 12 | **68** | C2 | Chunk `SubmitPhotosDao.getByIds` | XS | 2 |
| 13 | **66** | A4 | Delete the 3 tautological `DispatcherProvider` tests | XS | 3 |
| 14 | **64** | A7 | Unpin `sdk = [34]` in 3 adapter tests | XS | 3 |
| 15 | **62** | B9a | `_rev` projection query for `ChatRepositoryImpl.getLatestRev` | XS | 2 |
| 16 | **60** | C7 | Deterministic `NewsLogDao.getPendingUploads` order | XS | 1 |
| 17 | **58** | B1 | `PublicSurveyViewModel` | M | 2 |
| 18 | **56** | A6 | Unpin SDK 33 in `CoursesItemUtilsTest`, use a themed context | S | 1 |
| 19 | **54** | C3 | Chunk `MyLifeDao.getByIds` | XS | 2 |
| 20 | **52** | B3 | `TeamDetailFragment` → `TeamViewModel`, as state not proxies | M | 2 |
| 21 | **50** | C4 | One deterministic title per `resourceId` in most-opened | XS | 2 |
| 22 | **48** | B2 | `AddLocalResourceViewModel` | M | 2 |
| 23 | **46** | B10 | Stop `VoicesActions` reaching into `ActivitiesRepository` | M | 4 |
| 24 | **44** | B6 | Extract `NewsImageUploader` from `UploadManager` | M | 2 |
| 25 | **42** | B5 | `ResourceOpenViewModel` out of `BaseContainerFragment` | M | 2 |
| 26 | **38** | B4a | `ExamTakingViewModel` — `BaseExamFragment` half | M | 2 |
| 27 | **34** | B8 | `ProcessUserDataViewModel` | M | 2 |
| 28 | **30** | B4b | `ExamTakingViewModel` — `ExamTakingFragment` half | L | 2 |
| — | **18** | C6 | `CourseActivityDao`: accept empty revisions | XS | 1 |
| — | **12** | B9b | `ChatDao.getByUser` ordering in SQL | XS | 1 |
| — | **8** | C9 | `CourseProgressViewModel` → `getCurrentUserId()` | XS | 2 |
| — | **5** | C5 | `SearchActivityDao`: treat null revisions as pending | XS | 1 |

---

## Band 1 — do now (80+)

### 1 · score 94 · A2 — `EdgeToEdgeUtilsTest` → plain JUnit
The single best ratio in the set: ~17s of CI, for six deleted lines.
`setupEdgeToEdge should configure window and insets` was the slowest test in shard 1 at 17.4s;
the class took 18.3s. Nearly all of it is Robolectric sandbox start-up that the test never uses —
`Activity`, `Window`, `View` and `WindowInsetsControllerCompat` are all mockk mocks, and
`WindowCompat`/`ViewCompat` are statically mocked.

- **files:** `app/src/test/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtilsTest.kt` (class
  annotations at lines 22–23, imports at 4 and 18–20).
- **do:** delete `@RunWith(RobolectricTestRunner::class)` and `@Config(application = Application::class)`
  and the four now-unused imports. Change nothing else.
- **if it fails:** a `Method not mocked` error names the one Android call that needs an `every { … }`
  on an existing relaxed mock — add that rather than restoring the runner, and name it in the PR.
- **accept:** the class shows 2 tests passed; full suite green; `EdgeToEdgeUtilsTest` is gone from
  both top-15 tables in the next CI timing summary.

### 2 · score 92 · C10 — preserve coroutine cancellation in `UploadToShelfService`
**The one genuine correctness bug in all three plans.** Broad catches at lines 44 and 62
(`Exception`) and 86 (`Throwable`) convert a `CancellationException` into a success-listener
message, so cancelling a sync lets obsolete shelf and health uploads keep running. Line 127 still
has a bare `printStackTrace()`.

- **files:** `services/UploadToShelfService.kt` (`uploadUserData`, `uploadSingleUserData`,
  `uploadSingleUserHealth`, `uploadSingleUserToShelf`) · `app/src/test/…/services/UploadToShelfServiceTest.kt`.
- **do:** rethrow `CancellationException` explicitly before each existing error-to-listener branch;
  replace the `printStackTrace()` path with the same structured listener behaviour; add tests that
  cancel the user and health uploads and assert no callback fires afterwards.
- **keep:** application-scope ownership, dispatcher injection, and the current messages for ordinary
  repository and network failures.
- **accept:** cancelling a sync stops shelf/health upload cleanly; genuine failures still surface.

### 3 · score 88 · C1 — deterministic `RetryDao.getPending` order
Confirmed: the pending query has no `ORDER BY`, so retry execution follows SQLite's query plan and
older work can wait behind newer work. A real behaviour fix in about four lines of SQL, and
`RetryDaoTest` already exists to extend.

- **files:** `data/room/dao/RetryDao.kt` (`getPending`, lines 25–29) · `…/dao/RetryDaoTest.kt`.
- **do:** `ORDER BY nextRetryTime ASC, id ASC`. Preserve the status, due-time and max-attempt
  predicates and the return type exactly. Extend the test with out-of-order due times and
  equal-time rows.
- **out of scope:** retry batching, backoff arithmetic, statuses, `RetryRepositoryImpl`, `RetryQueue`.

### 4 · score 86 · A10 — teach the timing summary to separate warm-up from slowness
**Run this before any other A task.** `.github/scripts/test_timing_summary.py` ranks by raw summed
seconds, so "slowest" is dominated by whichever test happened to boot a Robolectric sandbox. That
points contributors at the wrong fixes — and it is the metric every later before/after claim in
this backlog is reported against.

- **files:** `.github/scripts/test_timing_summary.py` (the parse loop at 55–66, the tables at 84–97,
  the docstring at 2–14).
- **do:** collect per-test times per class; add `Tests` and `Median s/test` columns to the existing
  class table (keep it sorted by summed seconds so current readers see the same order); add a third
  table `slowest classes per test (median, ≥3 tests)`; add a `Likely warm-up` column to the
  individual-test table, `yes` when a test exceeds 5× its class median and the class has ≥3 tests.
- **hold fixed:** the CLI (`results_dir`, `--shard`, `--warn-over`), the exit codes, stdlib only —
  so `test.yml` needs no edit (it is owned by #15226).

### 5 · score 84 · A1 — hoist per-test static mocks to class scope
Biggest single CI saving in the set (~25–35s summed) and the only test task that can go flaky.
`UploadManagerTest` was the slowest class in shard 2 at 29.4s for 24 tests: `setup()` calls
`mockkStatic(Log)`, `mockkStatic(SystemClock)`, `mockkStatic(TextUtils)`, `mockkObject(NetworkUtils)`
and `mockkObject(UrlUtils)` before *every* test, and `tearDown()` calls `unmockkAll()`, so the
bytecode is re-transformed 24 times. `NetworkUtilsMockTest` (11.5s, 14 tests) has the same shape.

- **files:** `services/UploadManagerTest.kt` (`setup()` 83–123, `tearDown()` 125–130, and the
  `uploadNews derives mimeType…` test at 323) · `utils/NetworkUtilsMockTest.kt` (`setUp()` 34–54,
  `tearDown()` 56–60).
- **do:** move only the `mockkStatic`/`mockkObject` calls into `@BeforeClass @JvmStatic`, keeping
  their order, with `@AfterClass @JvmStatic { unmockkAll() }`. Keep every `every { … }` default stub
  in the per-test `@Before` — re-stubbing is cheap and preserves isolation. Replace `unmockkAll()` in
  the per-test `@After` with `clearAllMocks(answers = false, recordedCalls = true, childMocks = false,
  verificationMarks = true, exclusionRules = false)`. Drop the redundant `unmockkObject(UrlUtils)`.
  Wrap the `uploadNews` test body in `try { … } finally { unmockkObject(FileUtils) }`.
- **accept:** each class passes 3 runs in a row standalone; full suite green; summed class time for
  `UploadManagerTest` falls ≥30% — report before/after from the XML `time=` in the PR.
- **sequencing:** must land **before** B6, which moves `uploadNews` out from under this test.

### 6 · score 82 · B7 — `SyncActivity` post-sync bootstrap → `ResourceDownloadCoordinator`
The cleanest boundary move in plan B: a data fetch sitting in an activity right next to the
coordinator it feeds. Confirmed at `SyncActivity.kt` 566–576 — `getQueuedDownloads()` →
`openDownloadService`, then `getBetaAutoDownload()` → `getAllLibrariesToSync()` →
`startBackgroundDownload`.

- **files:** `ui/sync/SyncActivity.kt` · `services/ResourceDownloadCoordinator.kt`.
- **do:** inject `ConfigurationsRepository`, `ResourcesRepository` and `SharedPrefManager` into the
  coordinator (match `prefData`'s actual type); add `runPostSyncDownloads()` preserving the current
  order and conditions; replace lines ~560–577 with the single call inside the same launch block;
  grep before removing the activity's injections — keep any with remaining call sites.
- **accept:** with beta auto-download on and queued downloads present, finishing a sync kicks off
  exactly the same downloads.
- **out of scope:** `startBackgroundDownload` internals, `DownloadUtils`, `ProcessUserDataActivity`.

### 7 · score 80 · C8 — `ProgressViewModel` → `getCurrentUserId()`
Verified **and proven equivalent**, which is why this scores 80 while its twin C9 scores 8:
`getUserModel()` is `userDao.getById(sharedPrefManager.getUserId())`, and `getCurrentUserId()` is
that same SharedPrefs value — so `user.id` *is* `getCurrentUserId()`. `ProgressViewModel` reads
`user?.id`, so the substitution is behaviour-preserving and skips a full entity hydration.

- **files:** `ui/courses/ProgressViewModel.kt` (`loadCourseData`, 24–27) · its test.
- **do:** swap the call, pass the nullable id straight to `getCourseProgressRows`, update mocks to
  verify `getUserModel` is no longer called, retain the `StateFlow` and signed-out behaviour.
- **do not** apply the same edit to `CourseProgressViewModel` — see C9 at the bottom of this file.

---

## Band 2 — do this round (60–79)

### 8 · score 78 · A5 — `ThemeManagerTest`: build the activity only where used
12.0s for 5 tests. `setUp()` runs `Robolectric.buildActivity(AppCompatActivity::class.java).setup()`
before every test and `tearDown()` walks it through `pause().stop().destroy()`, but only
`testShowThemeDialog` touches `activity`. The class also declares `application = HiltTestApplication::class`
with no `HiltAndroidRule` and nothing to inject.
**do:** `@Config(…, application = Application::class)`; delete the `activityController`/`activity`
fields and their setUp/tearDown lines; build the controller locally inside `testShowThemeDialog`
with a `finally { controller.pause().stop().destroy() }`. Keep the `AppCompatDelegate` static mock.
**accept:** 5 tests pass; class time falls (report before/after); Settings → theme dialog still
switches Light/Dark/System on device.

### 9 · score 76 · A3 — drop dead Hilt from utils tests; fold `NetworkUtilsStateTest`
`NetworkUtilsTest` (22 tests), `NetworkUtilsStateTest` (1 test) and `IntentUtilsTest` (5 tests) each
build a Hilt test component and call `hiltRule.inject()` with **no `@Inject` or `@BindValue` field
anywhere** — confirmed present on master. `NetworkUtilsTest.testIsWifiEnabled` also duplicates
`NetworkUtilsMockTest`'s two branch tests.
**do:** delete `@HiltAndroidTest`, the `hiltRule` field and the `inject()` call from the two survivors;
`@Config(application = Application::class)`; keep `@LooperMode(PAUSED)`. Delete the duplicate
`testIsWifiEnabled`. Move `startListenNetworkState_isIdempotent` into `NetworkUtilsTest` with the
`@After { NetworkUtils.resetForTesting() }` it relies on, then delete `NetworkUtilsStateTest.kt`.
**accept:** `NetworkUtilsTest` reports 22 (−1 +1); `grep -rn HiltAndroidTest app/src/test/…/utils/`
lists only `MarkdownUtilsTest.kt` once A4 has landed.

### 10 · score 74 · A8 — `AndroidDecrypterTest`, `LeadersViewModelTest` → plain JUnit
Both boot a Robolectric sandbox to test platform-free code. `AndroidDecrypter`'s only Android import
is `android.util.Log`, which `returnDefaultValues = true` makes a silent no-op; `LeadersViewModel`
imports only lifecycle, coroutines and repository interfaces, and its test already swaps
`Dispatchers.Main` via `MainDispatcherRule`.
**do:** delete the runner (and `@Config` on the ViewModel test) plus the freed imports.
**leave alone:** `UserEntityParseLeadersTest` — `parseLeadersJson` uses `org.json.JSONObject`, which
has no real implementation on the plain JVM classpath. **watch:** the
`@Test(expected = StringIndexOutOfBoundsException::class)` case at `AndroidDecrypterTest:82`; if it
behaves differently, revert that file only.

### 11 · score 72 · A9 — extract `parseVitalReading` from `HealthExaminationActivity`
The only task that is both a CI speedup and a real refactor: it deletes a reflection-based Hilt
activity launch *and* adds platform-free domain code. `getFloat(trim: String)` (lines 360–363) is
pure string→float logic — comma-to-dot, finite check, round to 1 decimal — but it is private, so the
test has to launch the whole `@AndroidEntryPoint` activity under Robolectric and reach it by
reflection.
**do:** new `ui/health/HealthVitals.kt` holding `internal fun parseVitalReading(text: String): Float`
with exactly that body and no `android.*` import; replace the three call sites at 255, 257, 258;
new plain-JUnit `HealthVitalsTest` carrying over the six assertions ("36.6", "36,6", "72.46"→72.5,
"170", "", "abc") with one run under `Locale.FRANCE` in a `try/finally`; delete the old test method
and its `java.util.Locale` import.
**accept:** `grep -n "android\." …/HealthVitals.kt` prints nothing; entering "36,6" for temperature
still saves 36.6 on device.

### 12 · score 68 · C2 — chunk `SubmitPhotosDao.getByIds`
Confirmed: a raw `IN (:ids)` bound to an arbitrary `Array<String>`. The house idiom already exists
in three DAOs — `FeedbackDao`, `TagDao` and `NotificationDao` all do `getByIdsInternal` +
`chunked(900)` — so this is consistency work, not a live bug (>999 photos in one submission is
implausible).
**do:** rename the annotated query to an internal method, keep the public `Array<String>` signature
as a wrapper, return early on empty, chunk below the bind limit, flatten without adding dedup
semantics callers did not ask for. **Add the DAO test the source plan omits.**
**out of scope:** photo upload ordering, `markUploadedBatch`, repository APIs, upload payloads.

### 13 · score 66 · A4 — delete the 3 tautological `DispatcherProvider` tests
All three confirmed present, and none protects anything. `DefaultDispatcherProviderTest` asserts
that one-line getters return the dispatchers they return. `DispatcherProviderDITest` starts a full
Hilt component to `assertNotNull` an injected field — a missing binding already fails
`hiltJavaCompileDefaultDebugUnitTest`. `DispatcherProviderIntegrationTest` declares a test-only
`MyService` to check that `withContext(io)` runs. Together: 3 Robolectric setups and 2 Hilt
components per run. Real dispatcher behaviour is covered by every ViewModel and repository test
that injects `TestDispatcherProvider`.
**do:** delete the three files; `grep -rn "MyService\|DefaultDispatcherProviderTest\|DispatcherProviderDITest\|DispatcherProviderIntegrationTest" app/src/test` must come back empty.
**accept:** suite green with 7 fewer tests; `assembleDefaultDebug` still succeeds, which is what
actually proves the DI graph resolves `DispatcherProvider`.
**expect** a review round — "delete tests" always costs one. The `assembleDefaultDebug` argument is
the one to lead with.

### 14 · score 64 · A7 — unpin `sdk = [34]` in three adapter tests
All three pins confirmed on master, none with a comment or an SDK-specific assertion:
`ui/life/LifeAdapterTest.kt:22`, `ui/personals/PersonalsAdapterTest.kt:22`,
`ui/voices/VoicesActionsTest.kt:29`. The suite defaults to targetSdk 36, so each is an extra sandbox
boot on top of the one the fork already has.
**do:** drop `sdk = [34]` from the three `@Config`s. If one genuinely fails on 36, restore that pin
alone with a one-line comment naming the API difference and say so in the PR.
**leave alone:** `ServerAddressAdapterTest` (#17544), the `DownloadService*Test` files (#17596,
#17622), and the deliberate low pins in `NotificationUtilsTest` and
`TeamsRepositoryBulkInsertTransactionTest` (the latter's KDoc explains why).
**sequencing:** touches `VoicesActionsTest.kt`, which B10 also affects — different lines, but land
A7 first, it is cheaper.

### 15 · score 62 · B9a — `_rev` projection for `ChatRepositoryImpl.getLatestRev`
**The good half of B9, split out.** `getLatestRev` calls `ChatDao.getByDocId`, which does
`SELECT * FROM chat_history WHERE _id = :docId`, purely to read `_rev` off each row and take the
highest numeric prefix.
**do:** add `@Query("SELECT _rev FROM chat_history WHERE _id = :docId")` and point `getLatestRev` at
it, keeping the `maxByOrNull { _rev.split("-")[0].toIntOrNull() }` logic in Kotlin. Grep before
removing `getByDocId` — keep it if another caller exists.
**explicitly not** the `ORDER BY` half — see B9b at the bottom of this file.

### 16 · score 60 · C7 — deterministic `NewsLogDao.getPendingUploads` order
Valid change, **corrected premise.** The source plan describes this as a revision predicate; the
query is actually `SELECT * FROM news_log WHERE _id IS NULL OR _id = ''`. The ordering point stands
on its own: there is no `ORDER BY`, so reaction and view logs reach the server in storage-plan
order, making repeated batches and failure recovery unpredictable. `NewsLog` has `time: Long?`.
**do:** `ORDER BY time ASC, id ASC`. Preserve the `_id` predicate and the full-entity return type.
Do not move ordering into `VoicesRepositoryImpl` or the upload services.

---

## Band 3 — do when there is review capacity (40–59)

### 17 · score 58 · B1 — `PublicSurveyViewModel`
Two repositories injected straight into a 201-line activity with no ViewModel: `SurveysRepository`
(37) and `SubmissionsRepository` (39), driving `fetchPublicSurvey` (84), `saveSurveyFromPublicApi`
(95), `getLatestSubmissionByParentId` (121), `submitPublicSurvey` (136) and `getExamQuestions` (167)
from `lifecycleScope.launch`. State cannot survive rotation and cannot be unit tested.
Self-contained — **the right first ViewModel extraction**, to settle the pattern before B5 and B4.
**do:** `@HiltViewModel class PublicSurveyViewModel` with both repositories, matching
`SurveysViewModel`'s `IoDispatcher` use; move the load path (82–101) and the submission path
(118–146, 165–180) behind `StateFlow`; keep the toasts and `navigateOnwardAndFinish` (147–159) in
the activity. **accept:** opening a public survey link still loads it; finishing still shows
`survey_submitted` and lands on `DashboardActivity` or `LoginActivity`.

### 18 · score 56 · A6 — unpin SDK 33 in `CoursesItemUtilsTest`, use a themed context
**Corrected payoff.** The source plan claims this leaves no reason to boot an SDK-33 sandbox
because #17602 removes the other pin — but `ui/chat/ChatAdapterTest.kt:20` still pins `sdk = [33]`
on master, so #17602 has not merged. Either wait for it, or fold its one-line unpin in here and
restate the payoff honestly. The second half stands regardless: `setUp()` builds and fully sets up
an `AppCompatActivity` only to use it as a `Context`, when `bindCover` needs only resources,
colours and Glide.
**do:** drop `@Config(sdk = [33])` (line 24); replace the `activity` field with
`ContextThemeWrapper(ApplicationProvider.getApplicationContext(), Theme_MaterialComponents)`; swap
every use in the single test. **out of scope:** `robolectricSdkJars` in `app/build.gradle` (#17622),
and the TTL logic itself — all three `verify(exactly = …) { spyFile.exists() }` checks must survive.

### 19 · score 54 · C3 — chunk `MyLifeDao.getByIds`
Same house idiom as C2, lower payoff: both callers pass small lists — `listOf(myLifeId)` at
`LifeRepositoryImpl:23`, and the visible-life id set at 43. Pure consistency. `MyLifeDaoTest`
already exists to extend with an empty-input case and a list above the bind limit.
**leave alone:** user-id fallback rules, visibility filtering, dedup, Life repository defaults.

### 20 · score 52 · B3 — `TeamDetailFragment` → `TeamViewModel`, as state not proxies
The fragment holds `TeamViewModel by viewModels()` at line 50 and still calls `teamsRepository`
directly 11 times (117, 123, 146, 181, 276, 279, 298, 356, 357, 379, 397, 433) — bypassing its own
ViewModel, with `requestToJoin` and `leaveTeam` already sitting on the ViewModel at 122 and 143.
**Refined against the source plan:** its step 1 adds six pass-through methods with "same signatures,
no reshaping", which builds a ViewModel-as-repository-proxy and trades one smell for another.
Instead, expose member count and join state (pending / joinable / leave) as `StateFlow` the fragment
observes, and only delegate the fire-and-forget calls (`recordTeamActivity`, `logTeamVisit`).
**leave alone:** `TeamsRepository`/`TeamsRepositoryImpl` (open PRs), `BaseTeamFragment`.
**check first:** `TeamDetailFragment.kt` appears in stale brainstorm PR #14883 and `TeamViewModel.kt`
in stale #15951 — confirm neither took new commits.

### 21 · score 50 · C4 — one deterministic title per `resourceId` in most-opened
**Largely already done on master** by `6f739a5`: the nonblank-title filter and
`ORDER BY openCount DESC, title ASC` that the source plan's steps 1–2 request are both present.
Rewrite the order to the single surviving defect: the query selects a bare `title` under
`GROUP BY resourceId`, so when a resource's title changes SQLite may return either row's title even
though the count is stable.
**do:** choose one deterministic title per `resourceId` (e.g. `MIN(title)`), keep the single total
count, the user/type filtering, the winner ordering, the `ResourceOpenCount` projection and the
nullable return. Add a test where two rows share a `resourceId` with different titles, proving the
opens stay one aggregate. **out of scope:** activity logging, dashboard layout, repository
signatures, historical rows, new indices.

### 22 · score 48 · B2 — `AddLocalResourceViewModel`
`ui/resources/AddResourceActivity.kt` (292 lines) injects `ResourcesRepository` at 38 and calls
`resourceTitleExists` (112), `getResourceById` (124), `updateLocalResource` (150) and
`saveLocalResource` (195). Correctly refuses to bolt onto the existing `AddResourceViewModel`, which
is scoped to the unrelated personals upload flow.
**do:** new `@HiltViewModel class AddLocalResourceViewModel`; `checkTitle(title)` feeding the
duplicate branch at 111–116 from ViewModel state; `updateResource(resourceId, request)` absorbing
124 and 150–161; `saveResource(request)`; keep the `UploadResult` toasts and `finish()` in the
activity. **check first:** `AddResourceActivity.kt` appears in stale on-hold PR #13848.

### 23 · score 46 · B10 — stop `VoicesActions` reaching into `ActivitiesRepository`
`showMemberDetails` (214–231) takes an `ActivitiesRepository` parameter purely to call
`getMemberVisitStats` at 220 and prefill two `MembersDetailFragment` args. Voices code should not be
fetching member stats; the detail screen should load its own.
**constraint, verified:** `ReplyActivity.kt:195` is owned by open PR #17648, so the old signature
must keep compiling — and four `VoicesActionsTest` cases (91, 98, 130, 158) call it too.
**do:** new `MembersDetailViewModel` exposing visit stats as `StateFlow` keyed by member id/name;
`MembersDetailFragment` loads `visits`/`lastLogin` at display time instead of from the args Bundle
(keep the other args, add the member user-id); add a stats-free `showMemberDetails(userModel)`
overload and have the deprecated two-arg one delegate to it; point `BaseVoicesFragment:105` at the
new overload and drop its now-unused `activitiesRepository` injection at 45.
**debt this creates:** a deprecated method with no ticket to remove it. File the follow-up to delete
it and `ReplyActivity`'s injection once #17648 lands, in the same PR description.
**check first:** `BaseVoicesFragment.kt` appears in stale PR #13415.

### 24 · score 44 · B6 — extract `NewsImageUploader` from `UploadManager`
The last non-coordinator upload flow in a service that is otherwise a thin `UploadCoordinator`
caller: `createImage` (122–138), `uploadNews` (271–369), the `/news/_bulk_docs` push (340) and
`markNewsUploaded` (364) — about 100 lines of bespoke image-first orchestration.
**do:** new injectable `services/upload/NewsImageUploader.kt` taking exactly the collaborator set
the current body uses (read it; do not guess); move `createImage`, the per-image POST-plus-attachment
logic, the bulk-docs push and the `markNewsUploaded`/`queueNewsRetry` handling; reduce
`UploadManager.uploadNews` to delegating batch iteration; grep for `createImage` before deleting it.
**accept:** publishing a voice post with an image still uploads it under `/resources`, attaches it to
the news doc and marks the item uploaded; offline, the item still lands in the retry queue.
**sequencing: must land after A1**, which owns the mock lifecycle of the `uploadNews` test at
`UploadManagerTest:324`.

### 25 · score 42 · B5 — `ResourceOpenViewModel` out of `BaseContainerFragment`
Highest structural payoff left in plan B and the highest blast radius, which is why it sits here
rather than higher. The parent of every library detail screen does data work inline:
`userRepository.getUserModel()` (152), `reconcileHtmlResourceOffline` (200), `trackResourceOpen`
(202, 243, 255, 259), `getHtmlResourceDownloadUrls` (218) with the `ResourceUrlsResponse` branches
at 219–231, and `getLibraryItemsByLocalAddress` (238). Every subclass inherits the change untested.
**do:** new `ui/resources/ResourceOpenViewModel.kt` with `ResourcesRepository` and `UserRepository`
(check the interface signatures before wiring); methods `trackOpen`, `reconcileHtmlOffline`,
`resolveHtmlDownloadUrls`, `findByLocalAddress`, `isGuestUser`; **move the 219–231 when-branches into
the ViewModel** so the fragment receives only "open offline / download needed / can't open"; keep
`ResourceOpener`/`startActivity` where they are; remove the two field injections.
**walk on device:** open a resource from the library — the open is recorded, online-vs-offline HTML
resolves correctly, and the viewer or download launches as before.
**out of scope:** the offline-reconcile logic itself, `ResourceOpener` rewrites, any subclass edit.

---

## Band 4 — queue behind the rest (20–39)

### 26 · score 38 · B4a — `ExamTakingViewModel`, `BaseExamFragment` half
**B4 split, per the size correction.** The source plan budgets "~150 changed lines, 3 files" for
3 repository types and 11 call sites spanning a 224-line base class and an **853-line** fragment.
That is not one reviewable PR. Do the base class first: `BaseExamFragment` injects
`SubmissionsRepository` at 46 for `getSubmissionById` (88), `getExamByStepId`/`getExamById` (104,
106) and `updateSubmissionStatus` (158), `addSubmissionPhoto` (179).
**do:** create `ExamTakingViewModel` with all three repositories, but implement and wire **only** the
five base-fragment methods in this PR. Keep view bindings, the photo picker, toasts and pager
navigation in the fragment.

### 27 · score 34 · B8 — `ProcessUserDataViewModel`
Lowest payoff in plan B: it moves three calls behind a ViewModel — `uploadLoginData()` (193),
`uploadBulkData()` (211), `fetchUserSecurityData(name)` (254) — while the actually fiddly part, the
`takeWhile` over `SyncUiState`, stays in the activity. Worth doing for consistency once the pattern
is settled, not before.
**do:** expose the two uploads returning the same `Flow`/`SyncUiState` the activity collects, plus
the security fetch; keep `takeWhile`, UI-state handling and toasts in the activity; grep before
dropping either injection. **out of scope:** `SyncRepository` changes, subclass edits, the
notification/settings code in this activity.

### 28 · score 30 · B4b — `ExamTakingViewModel`, `ExamTakingFragment` half
The other half, against the 853-line fragment: `CoursesRepository` and `SurveysRepository` injected
at 66–68 for `getExamQuestions` (102), `getSubmissionsByParentId` (106), `isCourseCertified` (113),
`startExamSession` (127, 154, 166), `saveExamAnswer` (677) and `updateCourseProgress` (850).
**Land B4a first**, then wire these six onto the same ViewModel and drop both injections.
**accept — and walk it on device, the suite does not cover this:** an exam end to end (questions
render, answers persist per page, status updates on finish) and a survey (answers recorded, course
progress updated). **check first:** `ExamTakingFragment.kt` appears in stale PRs #14650 and #15559.

---

## Below the line — do not run as written (< 20)

### score 18 · C6 — `CourseActivityDao`: accept empty revisions as pending
`CourseActivity._rev` is `String? = null`, so the existing `_rev IS NULL` predicate is already the
live path and `OR _rev = ''` is speculative. Widening an *upload* predicate on a guess risks
re-uploading acknowledged rows.
**Before running:** find a writer that actually stores `""`. If none exists, drop the task.

### score 12 · B9b — `ChatDao.getByUser` ordering in SQL
`createdDate` and `updatedDate` are `String?`, and the Kotlin sort they replace is
`maxOf(createdDate.toLongOrNull(), updatedDate.toLongOrNull())` — **numeric**. The proposed
`ORDER BY MAX(COALESCE(createdDate,''), COALESCE(updatedDate,'')) DESC` compares them
**lexicographically**, so `"9999999999"` sorts above `"10000000000"` and chat history silently
reorders. The source plan hedges ("after confirming how dates are stored") rather than resolving it.
**Hold** until the columns are migrated to INTEGER, or `CAST(... AS INTEGER)` both sides and prove it
against a fixture spanning a digit-length boundary. The projection half ships separately as B9a.

### score 8 · C9 — `CourseProgressViewModel` → `getCurrentUserId()`
**Wrong field.** `loadProgress` reads `user?._id`, not `user?.id`. `UserEntity` declares both —
`@PrimaryKey var id` at line 28 and `var _id: String?` at 29 — and `getCurrentUserId()` returns
`sharedPrefManager.getUserId()`, which is `.id`. Substituting it silently changes which key
`getCourseProgress` is called with. Its twin **C8 is safe for exactly the reason C9 is not**: C8
reads `.id`.
**To salvage:** add `getCurrentUserDocId()` to `UserRepository` returning the `_id`, or leave
`getUserModel()` in place. Do not copy C8's diff onto this file.

### score 5 · C5 — `SearchActivityDao`: treat null revisions as pending
**The premise cannot occur.** `SearchActivity._rev` is declared `var _rev: String = ""` —
non-nullable, so Room generates a `NOT NULL` column and `_rev IS NULL` never matches a row. The
stated motivation, "locally created search events with a null revision can silently remain outside
the upload workflow", describes a state the schema forbids.
**Drop**, or keep the two lines as deliberate defensiveness against a future nullability change and
say so in the context — do not ship it as a bug fix.

---

## Sequencing constraints

1. **A10 → every other A task.** A10 changes what "slowest" means; land it first or every
   before/after number is measured against the metric A10 exists to replace.
2. **A1 → B6.** Both own `UploadManagerTest`; B6 moves `uploadNews` out from under it. A1 is
   smaller and test-only.
3. **A7 → B10.** Both touch `VoicesActionsTest.kt`, on different lines. Git will merge either
   order; A7 first is cheaper.
4. **B4a → B4b.** Settle the ViewModel against the 224-line base before the 853-line fragment.
5. **B1 → B2 → B3 → B10 → B8 → B5 → B4a → B4b.** Self-contained activities first, base class and
   exam flow last, so the pattern is settled before it meets the code that breaks loudest.
6. **A6 waits on #17602**, or folds its one-line `ChatAdapterTest` unpin in and restates the payoff.

Everything not named here is file-disjoint and can run in parallel.

## Standing rules for every task

- Re-verify against HEAD before starting, not against `354410d`.
- Each task is one PR. Title style: `scope: smoother thing doing (fixes #N)`.
- "The suite stays green" is necessary and nowhere near sufficient for anything in band 3 or 4 —
  those all ship a user-visible path with no unit coverage. Walk the acceptance criteria on a device.
- Where a task says *check first*, confirm the named stale PR took no new commits; if it did, skip.

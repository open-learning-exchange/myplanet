# myPlanet Refactor Round — Work Orders

date: 2026-11-10 · base commit: `2093a6224b221079f1413e2ab1f22cebec8cf736` (`2093a62` on `master`) · open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075 (recent past-week open PRs: #18555, #18554, #18551, #18540, #18539, #18455) · workflow runs checked on `master`: test run `42782082676` (`2093a62`), release run `42782082588` (`2093a62`), build run `42781301168` (`b14b2a1`)

### 1. eliminate duplicate release API fetch in playstore quota forecast (roadmap 5+8)
context: `.github/scripts/playstore-quota.sh:117-119` runs `measure` in `forecast()` and then
immediately runs `report=$(status | sed -n "s/^report='\(.*\)'$/\1/p")`, where `status()` at
line 82 calls `measure` a second time inside a command-substitution subshell. Because
`save_epochs()` at lines 33-44 fetches `gh api "repos/$REPO/releases?per_page=100"` whenever
`RELEASES_JSON` is unset, `playstore-quota.sh forecast` makes two identical GitHub Releases API
calls and runs `jq` over 100 releases twice during every Play Store and release workflow check.
Serving roadmap 5 and 8, this trims redundant GitHub API traffic and latency from release and
publishing automation.
files:
- touch: `.github/scripts/playstore-quota.sh` (`save_epochs` at lines 33-44, `status` at lines 81-112, `forecast` at lines 114-133)
- leave alone: `.github/scripts/playstore.sh`
steps:
1. Extract a helper function `build_status_report()` in `.github/scripts/playstore-quota.sh` that formats the `report` string from the already-computed `measure` variables (`used`, `SPARSE_SLOTS`, `remaining`, `status_word`, `next_slot_in`, `sparse_48h`, `sparse_week`) without re-invoking `measure`.
2. Update `status()` (lines 81-112) to call `measure`, call `build_status_report` to populate `report`, and emit the `GITHUB_OUTPUT`-compatible key-value lines.
3. Update `forecast()` (lines 114-133) to call `measure` once, call `build_status_report` directly to obtain `report` instead of spawning `status | sed ...`, and print the single-line summary.
4. Verify with `RELEASES_JSON` fixtures that `status`, `forecast`, and `gate` produce identical output before and after the change.
acceptance: `bash -n .github/scripts/playstore-quota.sh` and
`RELEASES_JSON='[]' NOW_EPOCH=1700000000 bash .github/scripts/playstore-quota.sh forecast` and
`./gradlew testDefaultDebugUnitTest` pass; `forecast` invokes `save_epochs` once per run and
prints the same quota summary string.
size budget: ~25 changed lines, 1 file
out of scope: do not change Play Store slot thresholds (`DAILY_CAP`, `SPARSE_SLOTS`) or
`.github/scripts/playstore.sh`.

---

### 2. defer collaborator API pagination until non-filtered coauthors exist (roadmap 8)
context: `.github/scripts/coauthors.sh:23-31` unconditionally executes
`gh api "repos/$REPO/collaborators?per_page=100" --paginate` at the start of every PR merge
before checking whether any commit author or `Co-authored-by:` trailer survives the PR-author
(`$author_l`), repo-owner (`$owner_l`), and `*[bot]` filters at lines 81-89. In addition,
lines 34-35 spawn two separate `jq` processes on `$pr_json` to read `.author.login` and
`.commits[]`. Serving roadmap 8, lazily loading collaborators only when an unfiltered candidate
login reaches the collaborator check eliminates an unnecessary paginated GitHub API call on
single-author and bot PR merges.
files:
- touch: `.github/scripts/coauthors.sh` (collaborator fetch at lines 23-31, PR JSON parsing at lines 34-35, candidate filter loop at lines 79-104)
- leave alone: `.github/scripts/version.sh`
steps:
1. Replace the eager `collaborators` API fetch at `.github/scripts/coauthors.sh:23-31` with a lazy helper `load_collaborators()` that populates `collaborators` and `collab_loaded=true` on first invocation.
2. Combine the two `jq` invocations on `$pr_json` at lines 34-35 or keep `author_login` and `commits` extraction clean without extra subshells, and exit early after building `raw_candidates` (line 70) if `[[ ${#raw_candidates[@]} -eq 0 ]]`.
3. Inside the candidate loop at lines 79-104, call `load_collaborators` only after a candidate login has passed the `[[ -z "$login" ]]`, `[[ "$login_l" == "$author_l" ]]`, `[[ "$login_l" == "$owner_l" ]]`, `[[ "$login_l" == *'[bot]' ]]`, and `seen[$login_l]` guards.
4. Validate syntax with `bash -n .github/scripts/coauthors.sh`.
acceptance: `bash -n .github/scripts/coauthors.sh` and `./gradlew testDefaultDebugUnitTest`
pass; merging a single-author or bot PR makes zero calls to `repos/$REPO/collaborators` while
multi-author PRs still emit `Co-authored-by:` trailers for non-collaborators.
size budget: ~30 changed lines, 1 file
out of scope: do not alter `noreply` email formatting or `.github/scripts/version.sh`.

---

### 3. batch PR state, review, and mergeable checks in automerge script (roadmap 8)
context: After `pick_pr()` selects a candidate PR in `.github/scripts/automerge.sh:84-102`, the
main loop makes three separate `gh pr view "$NUMBER"` REST API calls back-to-back:
`pr_state "$NUMBER"` at line 502 (`--json state`), `pr_review "$NUMBER"` at line 509
(`--json reviewDecision`), and `check_mergeable "$NUMBER"` at line 518 (`--json mergeable`).
Furthermore, `publish_failed` is called on `$base_at_prepare` at line 470 and called again at
line 605 on `$(git rev-parse "origin/$BASE")` even when `origin/$BASE` has not advanced,
repeating workflow-run and job-annotation API queries. Serving roadmap 8, batching the initial
PR metadata lookup and skipping duplicate `publish_failed` runs for an already-checked base SHA
cuts redundant GitHub API calls per automerge cycle.
files:
- touch: `.github/scripts/automerge.sh` (`pr_state` at lines 104-106, `pr_review` at lines 113-115, `check_mergeable` at lines 183-204, `publish_failed` calls at lines 470 and 605, main loop at lines 501-522)
- leave alone: `.github/workflows/automerge.yml`
steps:
1. Add a helper function in `.github/scripts/automerge.sh` (around line 104) that fetches `state,reviewDecision,mergeable` in a single `gh pr view "$1" --repo "$REPO" --json state,reviewDecision,mergeable` call.
2. In the main loop at lines 501-522, use the batched values for the initial `state`, `review`, and first `mergeable` check, falling back to `check_mergeable "$NUMBER"` retry polling only when the initial `mergeable` value is `"UNKNOWN"` or empty.
3. Track the last base SHA passed to `publish_failed` (at line 470) and skip the second `publish_failed` call at line 605 when `$(git rev-parse "origin/$BASE")` equals that SHA.
4. Verify script syntax with `bash -n .github/scripts/automerge.sh`.
acceptance: `bash -n .github/scripts/automerge.sh` and `./gradlew testDefaultDebugUnitTest`
pass; each automerge PR inspection performs one initial `gh pr view` call instead of three when
`mergeable` is already known, and does not re-query `publish_failed` for an unchanged base SHA.
size budget: ~35 changed lines, 1 file
out of scope: do not change PR selection priority, rebase/merge strategy, or
`.github/workflows/automerge.yml`.

---

### 4. drop non-default SDK 28 Robolectric pin in PdfThumbnailLoaderTest (roadmap 7+8)
context: `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt:21`
annotates `PdfThumbnailLoaderTest` with `@Config(sdk = [28])` and does not set
`application = Application::class, manifest = Config.NONE`. As documented in
`docs/TESTING.md:135-149` and observed in `myPlanet test` CI logs (run `42782082676`), pinning
a non-default SDK forces Robolectric to boot a separate API 28 sandbox and load an additional
`android-all-instrumented` jar in the Gradle test fork, even though
`PdfThumbnailLoader.kt:12-44` has zero SDK-version branching. Serving roadmap 7 and 8,
switching to the shared default SDK sandbox with lightweight `Application` and `Config.NONE`
removes an entire Robolectric sandbox initialization in CI.
files:
- touch: `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` (`PdfThumbnailLoaderTest` class annotations at lines 19-22)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`
steps:
1. Open `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` and import `android.app.Application`.
2. Replace `@Config(sdk = [28])` at line 21 with `@Config(application = Application::class, manifest = Config.NONE)` so the test class reuses the shared default SDK 36 Robolectric sandbox without initializing `MainApplication`.
3. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.PdfThumbnailLoaderTest` to confirm all 3 tests (`targetWidthPxZeroReturnsNull`, `missingOrNonPdfFileReturnsNullAndDoesNotThrow`, `statCallsRunOnIo`) pass.
acceptance: `./gradlew testDefaultDebugUnitTest` passes and `PdfThumbnailLoaderTest` completes
without booting a separate API 28 Robolectric sandbox.
size budget: ~4 changed lines, 1 file
out of scope: do not modify `PdfThumbnailLoader.kt` or add new test dependencies.

---

### 5. remove redundant per-test static and object mocks in unit tests (roadmap 7+8)
context: `.github/scripts/test_timing_summary.py:262-268` flags test classes that run
`mockkStatic`/`mockkObject` inside `@Before` with `unmockkAll()` in `@After`, because
re-instrumenting bytecode before every `@Test` method causes heavy MockK churn in `myPlanet test`
CI runs. Specifically, `StepExamTest.kt:24-35` (12 tests) mocks `android.text.TextUtils`
(unused by `StepExam.kt` and `ExamQuestion.kt`) on every test and mocks `NetworkUtils` even
though only `testSerializeExam` (line 154) touches `StepExam.serializeExam`;
`HealthExaminationTest.kt:20-28` (14 tests) and `NewsTest.kt:27-36` (16 tests) mock
`android.util.Log` on every test even though unit test default return values are enabled and no
test verifies `Log` calls; and `LoginSyncManagerTest.kt:56-64` (17 tests) mocks `android.util.Log`
and `android.util.Base64` (unused because `UrlUtils.basicAuthHeader` at `UrlUtils.kt:80` uses
`java.util.Base64`) on every test. Serving roadmap 7 and 8, removing dead static mocks and
scoping `NetworkUtils` / object mocks eliminates dozens of per-test bytecode re-instrumentations.
files:
- touch: `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt` (`setup` at lines 24-30, `tearDown` at lines 32-35, `testSerializeExam` at lines 154-177), `app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt` (`setUp` at lines 20-23, `tearDown` at lines 25-28), `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt` (`setUp` at lines 27-31, `tearDown` at lines 33-36), `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt` (`setup` at lines 54-81, `tearDown` at lines 83-86)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/model/StepExam.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`
steps:
1. In `StepExamTest.kt`, remove `mockkStatic(TextUtils::class)` and the `TextUtils` import; move `mockkObject(NetworkUtils)` and `every { NetworkUtils.getUniqueIdentifier() } returns "test-device-id"` into `testSerializeExam` (with `unmockkObject(NetworkUtils)` in a `try`/`finally` block) and remove `setup()` and `tearDown()`.
2. In `HealthExaminationTest.kt` and `NewsTest.kt`, remove the `@Before` `mockkStatic(Log::class)` and `@After` `unmockkAll()` methods and clean up the unused `android.util.Log`, `org.junit.After`, `org.junit.Before`, and MockK imports.
3. In `LoginSyncManagerTest.kt`, remove the dead `mockkStatic(Log::class)` (lines 56-58) and `mockkStatic(Base64::class)` (lines 60-63) calls and their unused imports (`android.util.Base64`, `android.util.Log`, `java.util.Base64`, `io.mockk.mockkStatic`), and move `mockkObject(UrlUtils)` and `mockkObject(AndroidDecrypter.Companion)` to `@BeforeClass` / `@AfterClass` on a `companion object` while keeping per-test stubbing in `@Before`.
4. Run all four test classes via `./gradlew testDefaultDebugUnitTest` to verify every test passes.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.model.StepExamTest --tests org.ole.planet.myplanet.model.HealthExaminationTest --tests org.ole.planet.myplanet.model.NewsTest --tests org.ole.planet.myplanet.services.sync.LoginSyncManagerTest`
and `./gradlew testDefaultDebugUnitTest` pass with zero `@Before` `mockkStatic` calls in all
four files.
size budget: ~65 changed lines, 4 files
out of scope: do not modify production model or sync classes (`StepExam.kt`,
`LoginSyncManager.kt`).

---

### 6. remove android TextUtils dependency from HealthExaminationItemMapper (roadmap 3+8, advances 9+10)
context: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt`
is a pure Kotlin domain-to-UI mapper except for `import android.text.TextUtils` at line 3 and
`!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)` at line 21.
Because `GsonUtils.getString("createdBy", json)` at line 15 returns a non-null `String`
(defaulting to `""`), `TextUtils` is completely unnecessary and forces unit callers to either
run under Robolectric or rely on stubbed Android framework classes. Serving roadmap 3 and 8
while directly advancing north stars 9 (KMP platform-free Kotlin core/mappers with zero
`android.*` imports) and 10 (CMP portable UI state mapping), replacing `TextUtils` with Kotlin
stdlib expressions makes `HealthExaminationItemMapper` 100% platform-free.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt` (import at line 3, `map` at line 21)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt`
steps:
1. Open `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt` and remove `import android.text.TextUtils` at line 3.
2. At line 21 inside `HealthExaminationItemMapper.map`, replace `!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)` with `createdBy.isNotEmpty() && createdBy != userModel?.id`.
3. Verify that no `android.*` imports remain in `HealthExaminationItemMapper.kt` and run the unit test suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes;
`grep "^import android\." app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt`
returns no matches, and health examination rows still resolve examiner names and
`isSelfExamination` identically.
size budget: ~3 changed lines, 1 file
out of scope: do not change `HealthExaminationAdapter.kt`, `HealthExamination.kt`, or date
formatting behavior.

---

### 7. inject TimeProvider into PublicSurveyViewModel instead of System.currentTimeMillis (roadmap 3+4+8, advances 9+10)
context: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt:62`
assigns `launchTime = System.currentTimeMillis()` directly when a survey loads and compares
`submission.lastUpdateTime < launchTime` at line 74 before uploading a completed public survey.
Reading wall-clock time directly instead of injecting `TimeProvider` (already provided as a
Hilt singleton in `TimeModule.kt:16` and documented in `TimeProvider.kt:4-10`) makes boundary
tests around `launchTime` dependent on real system clock ticks. Serving roadmap 3, 4, and 8
while advancing north stars 9 and 10 (portable, deterministic ViewModel state management),
injecting `TimeProvider` makes `PublicSurveyViewModel` fully deterministic under test.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt` (constructor at lines 22-26, `loadSurvey` at line 62), `app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt` (`setup` at lines 36-48, tests at lines 56-122)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/di/TimeModule.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt`
steps:
1. In `PublicSurveyViewModel.kt`, import `org.ole.planet.myplanet.utils.TimeProvider` and add `private val timeProvider: TimeProvider` to the `@Inject constructor` parameter list (lines 22-26).
2. In `PublicSurveyViewModel.loadSurvey` at line 62, replace `launchTime = System.currentTimeMillis()` with `launchTime = timeProvider.now()`.
3. In `PublicSurveyViewModelTest.kt`, mock `TimeProvider` in `setup()` (lines 36-48) with `every { timeProvider.now() } returns 1_000_000L`, pass it to `PublicSurveyViewModel`, and assert `assertEquals(1_000_000L, viewModel.launchTime)` in `loadSurvey success saves survey and emits SurveyLoaded`.
4. Add a unit test in `PublicSurveyViewModelTest.kt` verifying that `uploadCompletedSubmission` emits `PublicSurveyEvent.UploadSkipped` when `submission.lastUpdateTime < launchTime`.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.surveys.PublicSurveyViewModelTest`
and `./gradlew testDefaultDebugUnitTest` pass; public survey launch timestamps use
`TimeProvider.now()` and skip stale submissions deterministically.
size budget: ~35 changed lines, 2 files
out of scope: do not modify `TimeModule.kt`, `TimeProvider.kt`, or `SurveysRepository`.

---

### 8. combine dual ID/remoteId lookup and use injected Gson in FeedbackRepositoryImpl (roadmap 1+7, advances 9)
context: In `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt:109`,
`insertFeedbackList` executes two separate chunked Room queries back-to-back
(`(feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id }`), scanning
the `feedback` table twice for every synced batch of feedback documents. In addition, `fromJson`
at line 145 calls the static singleton `GsonUtils.gson.toJson(...)` even though
`@PlainGson private val gson: Gson` is already constructor-injected at line 18. Serving roadmap
1 and 7 while advancing north star 9 (clean, DI-backed repository layer), adding a single
chunked `getByIdsOrRemoteIds` query in `FeedbackDao` halves the SQLite queries during feedback
sync and eliminates the static `GsonUtils.gson` access.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt` (lookup queries at lines 45-63), `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt` (`insertFeedbackList` at lines 107-111, `fromJson` at line 145), `app/src/test/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImplTest.kt` (`insertFeedbackList` tests at lines 84-190)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackSyncWriter.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt`
steps:
1. In `FeedbackDao.kt`, add `@Query("SELECT * FROM feedback WHERE id IN (:ids) OR _id IN (:ids)") suspend fun getByIdsOrRemoteIdsChunk(ids: List<String>): List<Feedback>` and a chunked wrapper `suspend fun getByIdsOrRemoteIds(ids: List<String>): List<Feedback> = ids.chunked(SQLITE_MAX_VARIABLE_NUMBER).flatMap { getByIdsOrRemoteIdsChunk(it) }.distinctBy { it.id }`.
2. In `FeedbackRepositoryImpl.kt:109`, replace `(feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id }` with `feedbackDao.getByIdsOrRemoteIds(ids)`.
3. In `FeedbackRepositoryImpl.kt:145`, replace `GsonUtils.gson.toJson(GsonUtils.getJsonArray("messages", act))` with `gson.toJson(GsonUtils.getJsonArray("messages", act))`.
4. Update the five `insertFeedbackList_*` tests in `FeedbackRepositoryImplTest.kt` (lines 84-190) to stub `feedbackDao.getByIdsOrRemoteIds(listOf("fb1"))` and verify all 9 tests in `FeedbackRepositoryImplTest` pass.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.FeedbackRepositoryImplTest`
and `./gradlew testDefaultDebugUnitTest` pass; feedback sync resolves local and remote feedback
matches in a single chunked Room query per batch.
size budget: ~35 changed lines, 3 files
out of scope: do not remove `getByIds` or `getByRemoteIds` from `FeedbackDao.kt` and do not
modify `Feedback.kt`.

---

### 9. deduplicate single-link tag lookup and short-circuit empty tag names in TagsRepositoryImpl (roadmap 1+7, advances 9)
context: In `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt`,
`getLinkIdsForTagNames(db, tagNames)` at lines 57-64 queries `tagDao.getByNames(tagNames)` even
when `tagNames` is empty (unlike `getLinkedTagsBulk` at line 67 which short-circuits on
`linkIds.isEmpty()`), and `getLinkedTags(db, linkId)` at lines 101-118 duplicates the two-step
tag-ID extraction and `tagDao.getByIds` lookup already implemented in `getLinkedTagsBulk`.
Furthermore, `createUnmanagedTag(act)` at lines 130-153 parses `GsonUtils.getString("_id", act)`
twice (lines 132 and 134) and looks up `attachedTo` twice (lines 140 and 143). Serving roadmap
1 and 7 while advancing north star 9 (lean, platform-free repository code), short-circuiting
empty inputs and reusing local parsed values removes redundant DAO calls and duplicate JSON
lookups.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt` (`getLinkIdsForTagNames` at lines 57-64, `getLinkedTags` at lines 101-118, `createUnmanagedTag` at lines 130-153), `app/src/test/java/org/ole/planet/myplanet/repository/TagsRepositoryImplTest.kt` (tag lookup tests at lines 265-396)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt`, `app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt`
steps:
1. In `TagsRepositoryImpl.getLinkIdsForTagNames` (lines 57-64), add `if (tagNames.isEmpty()) return emptySet()` before calling `tagDao.getByNames(tagNames)`.
2. Extract a private helper `resolveParentTags(links: List<TagEntity>): List<TagEntity>` in `TagsRepositoryImpl.kt` that collects distinct non-null `tagId`s (`val tagIds = links.mapNotNull { it.tagId }.distinct()`) and returns `if (tagIds.isEmpty()) emptyList() else tagDao.getByIds(tagIds)`, then use it in `getLinkedTags` (lines 101-118) and `getLinkedTagsBulk` (lines 66-99).
3. In `TagsRepositoryImpl.createUnmanagedTag` (lines 130-153), read `val docId = GsonUtils.getString("_id", act)` once for both `id` and `_id`, and read `val attachedElement = act.get("attachedTo")` once before branching on `attachedElement?.isJsonArray == true`.
4. Add a unit test in `TagsRepositoryImplTest.kt` verifying `getCourseLinkIds(emptyList())` returns `emptySet()` without calling `tagDao.getByNames(any())`.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.TagsRepositoryImplTest`
and `./gradlew testDefaultDebugUnitTest` pass; resource and course tag filtering skips SQLite
queries when `tagNames` is empty.
size budget: ~40 changed lines, 2 files
out of scope: do not change `TagDao.kt` SQL queries or `TagEntity.kt` schema.

---

### 10. skip redundant user upserts and remove TextUtils in UserRepositoryImpl and HealthExaminationViewModel (roadmap 1+3+7, advances 9)
context: `UserRepositoryImpl.ensureUserSecurityKeys`
(`app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:402-411`) calls
`upsertUser(user)` (`userDao.upsert` + `userDao.getById` at lines 333-337) even when `user.key`
and `user.iv` are already populated, and `saveKeyIv` at lines 669-677 eagerly generates an AES
key and IV via `AndroidDecrypter` before checking `model.key`/`model.iv` with
`android.text.TextUtils.isEmpty` (the only use of `android.text.TextUtils` in
`UserRepositoryImpl.kt`). Meanwhile, `HealthExaminationViewModel.loadData`
(`HealthExaminationViewModel.kt:70-76`) calls `userRepository.getUserById(userId)` immediately
before `userRepository.ensureUserSecurityKeys(userId)` (which calls `getUserById(userId)`
again), and `saveExamination` (`HealthExaminationViewModel.kt:119-121`) catches `Exception`
without rethrowing `CancellationException`. Serving roadmap 1, 3, and 7 while advancing north
star 9 (removing `android.text.TextUtils` from `UserRepositoryImpl`), this eliminates redundant
Room reads/writes and eager key generation on health examination loads.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` (`import android.text.TextUtils` at line 5, `ensureUserSecurityKeys` at lines 402-411, `saveKeyIv` at lines 669-677), `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` (`loadData` at lines 69-77, `saveExamination` at lines 119-121), `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt` (`loadData` tests at lines 56-117 and 285-332)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt`
steps:
1. In `UserRepositoryImpl.ensureUserSecurityKeys` (lines 402-411), track whether `user.key == null` or `user.iv == null` required generating a new value, and only call `upsertUser(user)` when a key or IV was generated (returning `user` directly when both already exist).
2. In `UserRepositoryImpl.saveKeyIv` (lines 669-677), replace the eager `AndroidDecrypter.generateKey()` / `generateIv()` calls and `!TextUtils.isEmpty(...)` checks with `val keyString = model.key?.takeIf { it.isNotEmpty() } ?: AndroidDecrypter.generateKey()` and `val iv = model.iv?.takeIf { it.isNotEmpty() } ?: AndroidDecrypter.generateIv()`, and remove `import android.text.TextUtils` at line 5.
3. In `HealthExaminationViewModel.loadData` (lines 69-77), call `user = userRepository.ensureUserSecurityKeys(userId) ?: userRepository.getUserById(userId)` so `getUserById(userId)` is not called twice when `ensureUserSecurityKeys` succeeds, and in `saveExamination` (lines 119-121), rethrow `if (e is CancellationException) throw e` before sending `false` to `_saveResult`.
4. Update `HealthExaminationViewModelTest.kt` to add a test verifying `saveExamination` rethrows `CancellationException` and confirm all `loadData` and `saveExamination` tests pass.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.health.HealthExaminationViewModelTest --tests org.ole.planet.myplanet.repository.UserRepositoryImplTest`
and `./gradlew testDefaultDebugUnitTest` pass; opening a patient's health examination screen no
longer writes to the `users` table when security keys already exist.
size budget: ~35 changed lines, 3 files
out of scope: do not modify `HealthRepositoryImpl.kt` or `HealthExaminationActivity.kt`.

---

antigravity claude opus 4.6 high

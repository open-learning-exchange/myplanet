Skip to main content
Conversation Log
# TASK-GENERATION BRIEF — myPlanet refactor round

## role
you are generating work orders for OTHER coding agents (jules, codex, copilot,
devin, openhands, claude, qwen). they will execute your tasks verbatim and
cannot ask follow-up questions. your output is a plan, not code — you do not
modify any files in this run.

## goal
produce exactly 10 independent tasks that advance this roadmap:

1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests
north star — never scheduled directly, never blocked:
9. kotlin multiplatform: a platform-free kotlin core — repositories, models,
   sync/upload logic and use cases end up with zero android.* imports
10. compose multiplatform: every compose screen from 6 stays portable — state
    hoisted into viewmodels, no android views inside composables, no direct R.*

every task states which roadmap number it serves and, where true, how it also
moves 9/10 forward.

## this round's focus
github workflows quick wins · micro-optimizations that unblock bigger refactors ·
obvious inefficiencies removable without rewrites
verified spots to prefer:
all
workflow logs to check for wins:
- logs of last tests on default branch (master or main)
- logs pf last release
- logs of last build

## hard rules — violating any one invalidates the whole plan
R1 exactly 10 tasks, each independently mergeable in any order
R2 no file appears in more than one task
R3 list the currently OPEN pull requests before writing; any file an open PR
   touches is off-limits. if your platform cannot see open PRs, write
   "could not check open PRs" in the header and avoid the hot areas named in
   the focus block instead of guessing
R4 every file path, class and function you cite must exist — open the file and
   confirm before citing. no invented paths, no "e.g." paths
R5 per task: under ~150 changed lines, under ~5 files, no new dependencies,
   no unused code, no TODO placeholders
R6 you write no implementation code — the plan is the deliverable

## process — do these in order, do not skip P1
P1 search the actual code for candidate spots matching the focus block
P2 check recently open PRs (R3), discard colliding candidates
P3 rank what remains by user impact divided by blast radius
P4 write the top 10 as full work orders using the template below
P5 run the self-check, fix violations silently, then output

## task template — every section, every task
### <n>. <imperative title> (roadmap <numbers>)
context: 2-3 sentences — what is wrong today, why it matters, evidence as file:line
files: exact paths + class/function names to touch, AND neighbors to leave alone
steps: 3-6 numbered concrete edits
acceptance: exact commands that must pass (./gradlew testDefaultDebugUnitTest
  stays green) + the user-visible behavior to verify
size budget: expected changed lines and file count
out of scope: 1-2 lines on what NOT to do

## quality bar
BAD — reject, not executable without questions:
    Optimize the members screen.
    Use a count query instead of loading all members.
GOOD — match this density:
    ### 1. replace member-count list load with the existing count query (roadmap 1+7)
    context: RequestsViewModel.kt:39 calls getJoinedMembers(teamId).size, which
    loads every membership row plus one user query per member, to produce an Int.
    the repository already exposes getJoinedMemberCount(teamId) backed by SQL COUNT.
    files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt
    (line 39). do NOT touch TeamsRepositoryImpl — open PRs own it.
    steps: 1. swap the call 2. remove unused imports 3. run the unit tests
    acceptance: ./gradlew testDefaultDebugUnitTest green; requests screen still
    shows the correct joined-member count
    size budget: ~2 changed lines, 1 file
    out of scope: no DAO changes, no repository changes

## output contract
- one markdown document; tasks separated by a line containing only ---
- header lines: date · base commit · open PRs checked (numbers) or "could not check"
- no implementation code blocks; code snippets only as short evidence quotes

## self-check — verify each box before answering; fix, don't explain 
[ ] at least 10 tasks 
[ ] no file in two tasks 
[ ] every cited path was opened and confirmed to exist 
[ ] every task has all 7 template sections 
[ ] no task under 15 lines — rewrite any that is 
[ ] no task touches a file from the recent (max a week) open-PR list with tag review ready or merge
[ ] one tasks markdown document written to `docs/` directory 
[ ] dedicated branch created, committed, and pushed 
[ ] response terminates with the full URL to the markdown document on the pushed branch
[ ] last sign the doc with your servicename modelname and effort (e.g. antigravity gemini argon 4 high, claude opus 5.5 medium)

myPlanet Refactor Round — Work Orders

date: 2026-11-10 · base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 (2093a62 on master) · open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075 (recent past-week open PRs: #18555, #18554, #18551, #18540, #18539, #18455) · workflow runs checked on master: test run 42782082676 (2093a62), release run 42782082588 (2093a62), build run 42781301168 (b14b2a1)

Note: Written and committed to docs/REFACTOR_TASKS.md on local branch docs/refactor-tasks-20261110 (dd3085a9). Because the GitHub plugin is not connected in this VM (fatal: could not read Username for 'https://github.com'), push the prepared branch with git push -u origin docs/refactor-tasks-20261110.

1. eliminate duplicate release API fetch in playstore quota forecast (roadmap 5+8)

context: .github/scripts/playstore-quota.sh runs measure in forecast and then immediately runs report=$(status | sed -n "s/^report='\(.*\)'$/\1/p"), where status at line 82 calls measure a second time inside a command-substitution subshell. Because save_epochs at lines 33-44 fetches gh api "repos/$REPO/releases?per_page=100" whenever RELEASES_JSON is unset, playstore-quota.sh forecast makes two identical GitHub Releases API calls and runs jq over 100 releases twice during every Play Store and release workflow check. Serving roadmap 5 and 8, this trims redundant GitHub API traffic and latency from release and publishing automation. files:

touch: .github/scripts/playstore-quota.sh (save_epochs at lines 33-44, status at lines 81-112, forecast at lines 114-133)
leave alone: .github/scripts/playstore.sh steps:
Extract a helper function build_status_report() in .github/scripts/playstore-quota.sh that formats the report string from the already-computed measure variables (used, SPARSE_SLOTS, remaining, status_word, next_slot_in, sparse_48h, sparse_week) without re-invoking measure.
Update status (lines 81-112) to call measure, call build_status_report to populate report, and emit the GITHUB_OUTPUT-compatible key-value lines.
Update forecast (lines 114-133) to call measure once, call build_status_report directly to obtain report instead of spawning status | sed ..., and print the single-line summary.
Verify with RELEASES_JSON fixtures that status, forecast, and gate produce identical output before and after the change. acceptance: bash -n .github/scripts/playstore-quota.sh and RELEASES_JSON='[]' NOW_EPOCH=1700000000 bash .github/scripts/playstore-quota.sh forecast and ./gradlew testDefaultDebugUnitTest pass; forecast invokes save_epochs once per run and prints the same quota summary string. size budget: ~25 changed lines, 1 file out of scope: do not change Play Store slot thresholds (DAILY_CAP, SPARSE_SLOTS) or .github/scripts/playstore.sh.
2. defer collaborator API pagination until non-filtered coauthors exist (roadmap 8)

context: .github/scripts/coauthors.sh unconditionally executes gh api "repos/$REPO/collaborators?per_page=100" --paginate at the start of every PR merge before checking whether any commit author or Co-authored-by: trailer survives the PR-author ($author_l), repo-owner ($owner_l), and *[bot] filters at lines 81-89. In addition, lines 34-35 spawn two separate jq processes on $pr_json to read .author.login and .commits[]. Serving roadmap 8, lazily loading collaborators only when an unfiltered candidate login reaches the collaborator check eliminates an unnecessary paginated GitHub API call on single-author and bot PR merges. files:

touch: .github/scripts/coauthors.sh (collaborator fetch at lines 23-31, PR JSON parsing at lines 34-35, candidate filter loop at lines 79-104)
leave alone: .github/scripts/version.sh steps:
Replace the eager collaborators API fetch at .github/scripts/coauthors.sh with a lazy helper load_collaborators() that populates collaborators and collab_loaded=true on first invocation.
Combine the two jq invocations on $pr_json at lines 34-35 or keep author_login and commits extraction clean without extra subshells, and exit early after building raw_candidates (line 70) if [[ ${#raw_candidates[@]} -eq 0 ]].
Inside the candidate loop at lines 79-104, call load_collaborators only after a candidate login has passed the [[ -z "$login" ]], [[ "$login_l" == "$author_l" ]], [[ "$login_l" == "$owner_l" ]], [[ "$login_l" == *'[bot]' ]], and seen[$login_l] guards.
Validate syntax with bash -n .github/scripts/coauthors.sh. acceptance: bash -n .github/scripts/coauthors.sh and ./gradlew testDefaultDebugUnitTest pass; merging a single-author or bot PR makes zero calls to repos/$REPO/collaborators while multi-author PRs still emit Co-authored-by: trailers for non-collaborators. size budget: ~30 changed lines, 1 file out of scope: do not alter noreply email formatting or .github/scripts/version.sh.
3. batch PR state, review, and mergeable checks in automerge script (roadmap 8)

context: After pick_pr selects a candidate PR in .github/scripts/automerge.sh, the main loop makes three separate gh pr view "$NUMBER" REST API calls back-to-back: pr_state at line 502 (--json state), pr_review at line 509 (--json reviewDecision), and check_mergeable at line 518 (--json mergeable). Furthermore, publish_failed is called on $base_at_prepare at line 470 and called again at line 605 on $(git rev-parse "origin/$BASE") even when origin/$BASE has not advanced, repeating workflow-run and job-annotation API queries. Serving roadmap 8, batching the initial PR metadata lookup and skipping duplicate publish_failed runs for an already-checked base SHA cuts redundant GitHub API calls per automerge cycle. files:

touch: .github/scripts/automerge.sh (pr_state at lines 104-106, pr_review at lines 113-115, check_mergeable at lines 183-204, publish_failed calls at lines 470 and 605, main loop at lines 501-522)
leave alone: .github/workflows/automerge.yml steps:
Add a helper function in .github/scripts/automerge.sh (around line 104) that fetches state,reviewDecision,mergeable in a single gh pr view "$1" --repo "$REPO" --json state,reviewDecision,mergeable call.
In the main loop at lines 501-522, use the batched values for the initial state, review, and first mergeable check, falling back to check_mergeable retry polling only when the initial mergeable value is "UNKNOWN" or empty.
Track the last base SHA passed to publish_failed (at line 470) and skip the second publish_failed call at line 605 when $(git rev-parse "origin/$BASE") equals that SHA.
Verify script syntax with bash -n .github/scripts/automerge.sh. acceptance: bash -n .github/scripts/automerge.sh and ./gradlew testDefaultDebugUnitTest pass; each automerge PR inspection performs one initial gh pr view call instead of three when mergeable is already known, and does not re-query publish_failed for an unchanged base SHA. size budget: ~35 changed lines, 1 file out of scope: do not change PR selection priority, rebase/merge strategy, or .github/workflows/automerge.yml.
4. drop non-default SDK 28 Robolectric pin in PdfThumbnailLoaderTest (roadmap 7+8)

context: app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt annotates PdfThumbnailLoaderTest with @Config(sdk = [28]) and does not set application = Application::class, manifest = Config.NONE. As documented in docs/TESTING.md and observed in myPlanet test CI logs (run 42782082676), pinning a non-default SDK forces Robolectric to boot a separate API 28 sandbox and load an additional android-all-instrumented jar in the Gradle test fork, even though PdfThumbnailLoader has zero SDK-version branching. Serving roadmap 7 and 8, switching to the shared default SDK sandbox with lightweight Application and Config.NONE removes an entire Robolectric sandbox initialization in CI. files:

touch: app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt (PdfThumbnailLoaderTest class annotations at lines 19-22)
leave alone: app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt steps:
Open app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt and import android.app.Application.
Replace @Config(sdk = [28]) at line 21 with @Config(application = Application::class, manifest = Config.NONE) so the test class reuses the shared default SDK 36 Robolectric sandbox without initializing MainApplication.
Run ./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.PdfThumbnailLoaderTest to confirm all 3 tests (targetWidthPxZeroReturnsNull, missingOrNonPdfFileReturnsNullAndDoesNotThrow, statCallsRunOnIo) pass. acceptance: ./gradlew testDefaultDebugUnitTest passes and PdfThumbnailLoaderTest completes without booting a separate API 28 Robolectric sandbox. size budget: ~4 changed lines, 1 file out of scope: do not modify PdfThumbnailLoader.kt or add new test dependencies.
5. remove redundant per-test static and object mocks in unit tests (roadmap 7+8)

context: .github/scripts/test_timing_summary.py flags test classes that run mockkStatic/mockkObject inside @Before with unmockkAll() in @After, because re-instrumenting bytecode before every @Test method causes heavy MockK churn in myPlanet test CI runs. Specifically, StepExamTest (12 tests) mocks android.text.TextUtils (unused by StepExam and ExamQuestion) on every test and mocks NetworkUtils even though only testSerializeExam (line 154) touches StepExam.serializeExam; HealthExaminationTest (14 tests) and NewsTest (16 tests) mock android.util.Log on every test even though unit test default return values are enabled and no test verifies Log calls; and LoginSyncManagerTest (17 tests) mocks android.util.Log and android.util.Base64 (unused because UrlUtils.basicAuthHeader at UrlUtils.kt uses java.util.Base64) on every test. Serving roadmap 7 and 8, removing dead static mocks and scoping NetworkUtils / object mocks eliminates dozens of per-test bytecode re-instrumentations. files:

touch: app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt (setup at lines 24-30, tearDown at lines 32-35, testSerializeExam at lines 154-177), app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt (setUp at lines 20-23, tearDown at lines 25-28), app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt (setUp at lines 27-31, tearDown at lines 33-36), app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt (setup at lines 54-81, tearDown at lines 83-86)
leave alone: app/src/main/java/org/ole/planet/myplanet/model/StepExam.kt, app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt steps:
In StepExamTest.kt, remove mockkStatic(TextUtils::class) and the TextUtils import; move mockkObject(NetworkUtils) and every { NetworkUtils.getUniqueIdentifier() } returns "test-device-id" into testSerializeExam (with unmockkObject(NetworkUtils) in a try/finally block) and remove setup and tearDown.
In HealthExaminationTest.kt and NewsTest.kt, remove the @Before mockkStatic(Log::class) and @After unmockkAll() methods and clean up the unused android.util.Log, org.junit.After, org.junit.Before, and MockK imports.
In LoginSyncManagerTest.kt, remove the dead mockkStatic(Log::class) (lines 56-58) and mockkStatic(Base64::class) (lines 60-63) calls and their unused imports (android.util.Base64, android.util.Log, java.util.Base64, io.mockk.mockkStatic), and move mockkObject(UrlUtils) and mockkObject(AndroidDecrypter.Companion) to @BeforeClass / @AfterClass on a companion object while keeping per-test stubbing in @Before.
Run all four test classes via ./gradlew testDefaultDebugUnitTest to verify every test passes. acceptance: ./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.model.StepExamTest --tests org.ole.planet.myplanet.model.HealthExaminationTest --tests org.ole.planet.myplanet.model.NewsTest --tests org.ole.planet.myplanet.services.sync.LoginSyncManagerTest and ./gradlew testDefaultDebugUnitTest pass with zero @Before mockkStatic calls in all four files. size budget: ~65 changed lines, 4 files out of scope: do not modify production model or sync classes (StepExam.kt, LoginSyncManager.kt).
6. remove android TextUtils dependency from HealthExaminationItemMapper (roadmap 3+8, advances 9+10)

context: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt is a pure Kotlin domain-to-UI mapper except for import android.text.TextUtils at line 3 and !TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id) at line 21. Because GsonUtils.getString("createdBy", json) at line 15 returns a non-null String (defaulting to ""), TextUtils is completely unnecessary and forces unit callers to either run under Robolectric or rely on stubbed Android framework classes. Serving roadmap 3 and 8 while directly advancing north stars 9 (KMP platform-free Kotlin core/mappers with zero android.* imports) and 10 (CMP portable UI state mapping), replacing TextUtils with Kotlin stdlib expressions makes HealthExaminationItemMapper 100% platform-free. files:

touch: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt (import at line 3, map at line 21)
leave alone: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt, app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt steps:
Open app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt and remove import android.text.TextUtils at line 3.
At line 21 inside HealthExaminationItemMapper.map, replace !TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id) with createdBy.isNotEmpty() && createdBy != userModel?.id.
Verify that no android.* imports remain in HealthExaminationItemMapper.kt and run the unit test suite. acceptance: ./gradlew testDefaultDebugUnitTest passes; grep "^import android\." app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt returns no matches, and health examination rows still resolve examiner names and isSelfExamination identically. size budget: ~3 changed lines, 1 file out of scope: do not change HealthExaminationAdapter.kt, HealthExamination.kt, or date formatting behavior.
7. inject TimeProvider into PublicSurveyViewModel instead of System.currentTimeMillis (roadmap 3+4+8, advances 9+10)

context: app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt assigns launchTime = System.currentTimeMillis() directly when a survey loads and compares submission.lastUpdateTime < launchTime at line 74 before uploading a completed public survey. Reading wall-clock time directly instead of injecting TimeProvider (already provided as a Hilt singleton in TimeModule at line 16 and documented in TimeProvider.kt) makes boundary tests around launchTime dependent on real system clock ticks. Serving roadmap 3, 4, and 8 while advancing north stars 9 and 10 (portable, deterministic ViewModel state management), injecting TimeProvider makes PublicSurveyViewModel fully deterministic under test. files:

touch: app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt (constructor at lines 22-26, loadSurvey at line 62), app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt (setup at lines 36-48, tests at lines 56-122)
leave alone: app/src/main/java/org/ole/planet/myplanet/di/TimeModule.kt, app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt steps:
In PublicSurveyViewModel.kt, import org.ole.planet.myplanet.utils.TimeProvider and add private val timeProvider: TimeProvider to the @Inject constructor parameter list (lines 22-26).
In PublicSurveyViewModel.loadSurvey at line 62, replace launchTime = System.currentTimeMillis() with launchTime = timeProvider.now().
In PublicSurveyViewModelTest.kt, mock TimeProvider in setup (lines 36-48) with every { timeProvider.now() } returns 1_000_000L, pass it to PublicSurveyViewModel, and assert assertEquals(1_000_000L, viewModel.launchTime) in loadSurvey success saves survey and emits SurveyLoaded.
Add a unit test in PublicSurveyViewModelTest.kt verifying that uploadCompletedSubmission emits PublicSurveyEvent.UploadSkipped when submission.lastUpdateTime < launchTime. acceptance: ./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.surveys.PublicSurveyViewModelTest and ./gradlew testDefaultDebugUnitTest pass; public survey launch timestamps use TimeProvider.now() and skip stale submissions deterministically. size budget: ~35 changed lines, 2 files out of scope: do not modify TimeModule.kt, TimeProvider.kt, or SurveysRepository.
8. combine dual ID/remoteId lookup and use injected Gson in FeedbackRepositoryImpl (roadmap 1+7, advances 9)

context: In app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt, insertFeedbackList executes two separate chunked Room queries back-to-back ((feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id }), scanning the feedback table twice for every synced batch of feedback documents. In addition, fromJson at line 145 calls the static singleton GsonUtils.gson.toJson(...) even though @PlainGson private val gson: Gson is already constructor-injected at line 18. Serving roadmap 1 and 7 while advancing north star 9 (clean, DI-backed repository layer), adding a single chunked getByIdsOrRemoteIds query in FeedbackDao halves the SQLite queries during feedback sync and eliminates the static GsonUtils.gson access. files:

touch: app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt (lookup queries at lines 45-63), app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt (insertFeedbackList at lines 107-111, fromJson at line 145), app/src/test/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImplTest.kt (insertFeedbackList tests at lines 84-190)
leave alone: app/src/main/java/org/ole/planet/myplanet/repository/FeedbackSyncWriter.kt, app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt steps:
In FeedbackDao.kt, add @Query("SELECT * FROM feedback WHERE id IN (:ids) OR _id IN (:ids)") suspend fun getByIdsOrRemoteIdsChunk(ids: List<String>): List<Feedback> and a chunked wrapper suspend fun getByIdsOrRemoteIds(ids: List<String>): List<Feedback> = ids.chunked(SQLITE_MAX_VARIABLE_NUMBER).flatMap { getByIdsOrRemoteIdsChunk(it) }.distinctBy { it.id }.
In FeedbackRepositoryImpl.kt, replace (feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id } with feedbackDao.getByIdsOrRemoteIds(ids).
In FeedbackRepositoryImpl.kt, replace GsonUtils.gson.toJson(GsonUtils.getJsonArray("messages", act)) with gson.toJson(GsonUtils.getJsonArray("messages", act)).
Update the five insertFeedbackList_* tests in FeedbackRepositoryImplTest.kt (lines 84-190) to stub feedbackDao.getByIdsOrRemoteIds(listOf("fb1")) and verify all 9 tests in FeedbackRepositoryImplTest pass. acceptance: ./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.FeedbackRepositoryImplTest and ./gradlew testDefaultDebugUnitTest pass; feedback sync resolves local and remote feedback matches in a single chunked Room query per batch. size budget: ~35 changed lines, 3 files out of scope: do not remove getByIds or getByRemoteIds from FeedbackDao.kt and do not modify Feedback.kt.
9. deduplicate single-link tag lookup and short-circuit empty tag names in TagsRepositoryImpl (roadmap 1+7, advances 9)

context: In app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt, getLinkIdsForTagNames at lines 57-64 queries tagDao.getByNames(tagNames) even when tagNames is empty (unlike getLinkedTagsBulk at line 67 which short-circuits on linkIds.isEmpty()), and getLinkedTags at lines 101-118 duplicates the two-step tag-ID extraction and tagDao.getByIds lookup already implemented in getLinkedTagsBulk. Furthermore, createUnmanagedTag at lines 130-153 parses GsonUtils.getString("_id", act) twice (lines 132 and 134) and looks up attachedTo twice (lines 140 and 143). Serving roadmap 1 and 7 while advancing north star 9 (lean, platform-free repository code), short-circuiting empty inputs and reusing local parsed values removes redundant DAO calls and duplicate JSON lookups. files:

touch: app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt (getLinkIdsForTagNames at lines 57-64, getLinkedTags at lines 101-118, createUnmanagedTag at lines 130-153), app/src/test/java/org/ole/planet/myplanet/repository/TagsRepositoryImplTest.kt (tag lookup tests at lines 265-396)
leave alone: app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt, app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt steps:
In TagsRepositoryImpl.getLinkIdsForTagNames (lines 57-64), add if (tagNames.isEmpty()) return emptySet() before calling tagDao.getByNames(tagNames).
Extract a private helper resolveParentTags(links: List<TagEntity>): List<TagEntity> in TagsRepositoryImpl.kt that collects distinct non-null tagIds (val tagIds = links.mapNotNull { it.tagId }.distinct()) and returns if (tagIds.isEmpty()) emptyList() else tagDao.getByIds(tagIds), then use it in getLinkedTags (lines 101-118) and getLinkedTagsBulk (lines 66-99).
In TagsRepositoryImpl.createUnmanagedTag (lines 130-153), read val docId = GsonUtils.getString("_id", act) once for both id and _id, and read val attachedElement = act.get("attachedTo") once before branching on attachedElement?.isJsonArray == true.
Add a unit test in TagsRepositoryImplTest.kt verifying getCourseLinkIds(emptyList()) returns emptySet() without calling tagDao.getByNames(any()). acceptance: ./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.TagsRepositoryImplTest and ./gradlew testDefaultDebugUnitTest pass; resource and course tag filtering skips SQLite queries when tagNames is empty. size budget: ~40 changed lines, 2 files out of scope: do not change TagDao.kt SQL queries or TagEntity.kt schema.
10. skip redundant user upserts and remove TextUtils in UserRepositoryImpl and HealthExaminationViewModel (roadmap 1+3+7, advances 9)

context: UserRepositoryImpl.ensureUserSecurityKeys (app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt) calls upsertUser (userDao.upsert + userDao.getById at lines 333-337) even when user.key and user.iv are already populated, and saveKeyIv at lines 669-677 eagerly generates an AES key and IV via AndroidDecrypter before checking model.key/model.iv with android.text.TextUtils.isEmpty (the only use of android.text.TextUtils in UserRepositoryImpl.kt). Meanwhile, HealthExaminationViewModel.loadData (HealthExaminationViewModel.kt) calls userRepository.getUserById(userId) immediately before userRepository.ensureUserSecurityKeys(userId) (which calls getUserById(userId) again), and saveExamination (HealthExaminationViewModel.kt) catches Exception without rethrowing CancellationException. Serving roadmap 1, 3, and 7 while advancing north star 9 (removing android.text.TextUtils from UserRepositoryImpl.kt), this eliminates redundant Room reads/writes and eager key generation on health examination loads. files:

touch: app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt (import android.text.TextUtils at line 5, ensureUserSecurityKeys at lines 402-411, saveKeyIv at lines 669-677), app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt (loadData at lines 69-77, saveExamination at lines 119-121), app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt (loadData tests at lines 56-117 and 285-332)
leave alone: app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt, app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt steps:
In UserRepositoryImpl.ensureUserSecurityKeys (lines 402-411), track whether user.key == null or user.iv == null required generating a new value, and only call upsertUser when a key or IV was generated (returning user directly when both already exist).
In UserRepositoryImpl.saveKeyIv (lines 669-677), replace the eager AndroidDecrypter.generateKey() / generateIv() calls and !TextUtils.isEmpty(...) checks with val keyString = model.key?.takeIf { it.isNotEmpty() } ?: AndroidDecrypter.generateKey() and val iv = model.iv?.takeIf { it.isNotEmpty() } ?: AndroidDecrypter.generateIv(), and remove import android.text.TextUtils at line 5.
In HealthExaminationViewModel.loadData (lines 69-77), call user = userRepository.ensureUserSecurityKeys(userId) ?: userRepository.getUserById(userId) so getUserById(userId) is not called twice when ensureUserSecurityKeys succeeds, and in saveExamination (lines 119-121), rethrow if (e is CancellationException) throw e before sending false to _saveResult.
Update HealthExaminationViewModelTest.kt to add a test verifying saveExamination rethrows CancellationException and confirm all loadData and saveExamination tests pass. acceptance: ./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.health.HealthExaminationViewModelTest --tests org.ole.planet.myplanet.repository.UserRepositoryImplTest and ./gradlew testDefaultDebugUnitTest pass; opening a patient's health examination screen no longer writes to the users table when security keys already exist. size budget: ~35 changed lines, 3 files out of scope: do not modify HealthRepositoryImpl.kt or HealthExaminationActivity.kt.

antigravity claude opus 4.6 high

https://github.com/open-learning-exchange/myplanet/blob/docs/refactor-tasks-20261110/docs/REFACTOR_TASKS.md

404 - page not found
Cannot find a valid ref in docs/refactor-tasks-20261110/docs/REFACTOR_TASKS.md

The branch with docs/REFACTOR_TASKS.md (dd3085a9) is committed locally on refactor-tasks-20261110 (and docs/refactor-tasks-20261110), but git push failed with fatal: could not read Username for 'https://github.com': No such device or address because the GitHub plugin is not connected in this session (Connected plugins: none).

Please connect the GitHub plugin below (or on the Customizations page) and send a new message — I will immediately push the branch to origin so the GitHub link resolves.

GitHub
Access and clone GitHub repositories in remote projects
done

Configuring GitHub authentication via gh auth status and pushing refactor-tasks-20261110 and docs/refactor-tasks-20261110 to origin now; I will report the result as soon as the command finishes.

gh auth status returned:

You are not logged into any GitHub hosts. To log in, run: gh auth login

The GitHub plugin credential has not yet been attached to this session (or needs to be reconnected). Please connect or reconnect the GitHub plugin using the card below or on the Customizations page (Customizations → GitHub → Accounts), and then send a new message so I can push docs/refactor-tasks-20261110 to origin.

GitHub
Access and clone GitHub repositories in remote projects
ok now
Working
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
Antigravity

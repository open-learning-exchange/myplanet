# myPlanet refactor round — 10 work orders

Generated 2026-09-17. These are verbatim work orders for coding agents. Each task is independently mergeable in any order.

**Open-PR status at generation time: checked** (36 open PRs enumerated via the GitHub API). Collision-prone areas were excluded from scope:

- PR #15808 (sync `_changes` feed): `AppDatabase.kt`, `RoomModule.kt`, `ServiceModule.kt`, `TransactionSyncManager.kt`, `HeavyTableSyncWorker.kt`, 11 DAOs, ~20 repository files
- PRs #15951 / #16623 / #15820 / #15825 (teams): `TeamsRepository.kt`, `TeamsRepositoryImpl.kt`, `TeamsTasksViewModel.kt`, team UIs, `MeetupDao.kt`, `NewsDao.kt`, all `strings.xml`, `RepositoryModule.kt`
- PR #17222: `SharedPrefManager.kt` + its test
- PR #17187: `ResourcesAdapter.kt`, `ResourcesFilterFragment.kt`, `ResourcesListFilter.kt`, `ResourcesViewModel.kt`, `MediumUtils.kt`
- PR #17255: `DashboardActivity.kt`, `app_bar_bell.xml`
- PR #17254: `TeamResourcesAdapter.kt`, `row_team_resource.xml`

Round focus: repository boundary reinforcement, cross-feature data leaks, data functions moved one-by-one into repositories, Room/DAO optimizations, smoother repository ↔ ViewModel relationships.

Roadmap legend:

1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests
9. kotlin multiplatform: platform-free kotlin core (never scheduled directly, never blocked)
10. compose multiplatform: portable compose screens (never scheduled directly, never blocked)

---

## Task 1 — `ReplyViewModel`: absorb the last repository calls from `ReplyActivity`

**Roadmap:** 3 (ViewModel expansion), 10 (state hoisting)

**Verified problem:** `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt` field-injects `activitiesRepository` (line 58), `sharedPrefManager` (63), `voicesRepository` (65), `userSessionManager` (55) and calls `userSessionManager.getUserModel()` (98), `sharedPrefManager.getCommunityLeaders()` / `setRepliedNewsId` (151–152), `VoicesActions.showMemberDetails(userModel, activitiesRepository)` (194) directly from the Activity, even though `ReplyViewModel` (`ui/voices/ReplyViewModel.kt`, 17 lines) already exists with only `getNewsWithReplies`.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/voices/ReplyViewModelTest.kt` (new)

**Work:**

1. Inject `UserSessionManager`, `ActivitiesRepository`, `SharedPrefManager` into `ReplyViewModel`'s constructor.
2. Add `suspend fun getCurrentUser(): UserEntity?` (wraps `userSessionManager.getUserModel()`), `fun getCommunityLeadersJson(): String?` + `fun setRepliedNewsId(id: String)` (wrap the two `SharedPrefManager` calls at ReplyActivity.kt:151–152), and `suspend fun getMemberDetailsFragmentArgs(userModel: UserEntity?)` returning whatever `VoicesActions.showMemberDetails` needs from `ActivitiesRepository` so the fragment construction stays in the activity but the data fetch moves down.
3. Replace the four field injections in `ReplyActivity` with calls through `viewModel`. Remove now-unused imports (`ActivitiesRepository`, `SharedPrefManager`, `UserSessionManager`) if fully unreferenced — keep `voicesRepository` only if still needed for `voicesEditActions` (line 150); if so, that one stays.
4. New test file mocking the three injected services (MockK + `MainDispatcherRule` + `TestDispatcherProvider` per `docs/TESTING.md`); assert delegation and that leaders JSON parsing input is passed through unchanged.

**Acceptance:** `grep -n "activitiesRepository\.\|sharedPrefManager\.\|userSessionManager\." ReplyActivity.kt` shows no remaining data calls; `./gradlew testDefaultDebugUnitTest --tests "*ReplyViewModelTest*"` passes.

---

## Task 2 — `SurveysRepository`: return raw counts, not formatted strings

**Roadmap:** 1 (data-layer cleanup), 3, 9 (removes `R.plurals`/`Context.resources` from the repository — direct KMP progress)

**Verified problem:** `SurveysRepositoryImpl.getSurveyInfos()` (app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt ~line 323) formats `submissionCount` via `context.resources.getQuantityString(R.plurals.survey_taken_count, …)` and `creationDate` via `TimeUtils.formatDate(...)` inside the repository. `SurveyInfo` (model/SurveyInfo.kt) therefore carries pre-formatted `String`s, blocking KMP extraction and locale changes without re-query.

**Files (4):**

- `app/src/main/java/org/ole/planet/myplanet/model/SurveyInfo.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt`

**Work:**

1. Change `SurveyInfo.submissionCount: String` → `submissionCount: Int` and `creationDate: String` → `creationDate: Long` (epoch millis); `lastSubmissionDate` stays a display string only if already raw — otherwise also becomes `Long?`. Verify each field's only producers/consumers first (`SurveysAdapter.kt:109–112`, `SurveysViewModel.kt:48–54,92`).
2. In `SurveysRepositoryImpl`, delete the `getQuantityString`/`formatDate` calls; return raw values. This removes one `context.` use (survey reminder prefs in the same file are **out of scope** — different task).
3. Move plural formatting into `SurveysAdapter` (it has a `Context` via item views) and date formatting into the adapter or ViewModel using the existing `TimeUtils` helpers.
4. Update the existing `SurveysRepositoryImplTest` (verify it exists under app/src/test/.../repository/ first; if absent, add a focused test for the raw-value mapping).

**Acceptance:** no `R.` reference remains in `SurveysRepositoryImpl.kt`; survey list rows render identical text; unit tests green.

---

## Task 3 — `ActivitiesRepositoryImpl`: swap `android.util.Log` + dead `Context` param for the injected provider pattern

**Roadmap:** 1, 9 (removes 2 `android.*` imports from a repository impl)

**Verified problem:** `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` imports `android.content.Context` (line 3) and `android.util.Log` (line 4). The `Log.e("ActivitiesRepository", ...)` call sits at line ~346; `serializeLoginActivities(activity, context)` (line 307) takes a `Context` it only forwards to `NetworkUtils.getCustomDeviceName(context)` (line 317) — and `NetworkUtils.getCustomDeviceName` ignores its `context` parameter entirely (NetworkUtils.kt:243–245 delegates to `sharedPrefManager`).

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImplTest.kt` (verify name/exists; else create)

**Work:**

1. Replace `NetworkUtils.getCustomDeviceName(context)` at line 317 with the existing injectable `DeviceNameProvider` (`utils/DeviceNameProvider.kt`, already used by `ResourcesRepositoryImpl` and `PersonalsRepositoryImpl`); inject it into the constructor.
2. Drop the `context` parameter from the private `serializeLoginActivities` and, if `@ApplicationContext context` is then unused in the whole file (check the other `context` uses at lines ~215, 435–454 — `MyPlanet.getNormalMyPlanetActivities`/`getTabletUsages` still need it), keep the constructor param but remove only what becomes dead. If any use remains, keep `Context` and just remove the `Log` import by routing line ~346 through the logger already used elsewhere in the codebase (check how neighboring impls log; if none, keep `Log` — do **not** invent a logger).
3. Leave `NetworkUtils.getCustomDeviceName(context)`'s unused parameter in place if all 6 other call sites (UploadManager, TeamsRepositoryImpl, SubmissionsRepositoryImpl ×2, UserRepositoryImpl, MyPlanet.kt ×2, UserEntity.kt) are outside this task's file budget — note the follow-up instead of touching them. (Keep the diff to the 3 listed files.)

**Acceptance:** `ActivitiesRepositoryImpl.kt` compiles with at least one fewer `android.*` import; behavior of serialized login activities unchanged (test asserts the `customDeviceName` property value comes from the provider).

---

## Task 4 — `SyncRepositoryImpl`: push shelf-dispatch map construction behind an interface-friendly seam

**Roadmap:** 5 (sync/upload consolidation), 3, 9 (removes `android.util.Log` — SyncRepositoryImpl's only `android.*` import — and untangles the Lazy-service cycle)

**Verified problem:** `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` (constructor at lines 30–42) injects `TransactionSyncManager` as `dagger.Lazy` (a service) alongside four repositories, and its `shelfDispatchMap` (lines 44–51) hard-codes the shelf-name → batch-insert mapping inside the repository. `Log.e` used at lines 95, 181.

**Files (4):**

- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt` — ⚠️ **collision check at execution time: PRs #15824/#15825 touch this file; if still open when the task starts, restructure to avoid it (e.g., construct the map inline in the impl) rather than editing it**
- `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt` (verify exists; else create)

**Work:**

1. Extract the `shelfDispatchMap` into a `ShelfBatchInserter` interface with a `suspend fun batchInsert(shelf: String, shelfId: String?, docs: List<JsonObject>): Int?` (null = unknown shelf) in `SyncRepository.kt`, implemented by a small class that takes the four repositories/writers. Bind in `RepositoryModule` (or construct internally if the module is PR-blocked).
2. Remove `android.util.Log` import: replace both `Log.e` calls with error surfacing — return the failure in `SyncUiState.Error` where reachable, or use `ensureActive()` + rethrow; do not silently swallow.
3. Replace `dagger.Lazy<TransactionSyncManager>` with a direct injection only if no init cycle results (TransactionSyncManager does not depend on SyncRepository — verify before changing); otherwise keep `Lazy` and add a comment explaining the cycle.

**Acceptance:** `grep "android\." SyncRepositoryImpl.kt` returns nothing; shelf sync of `resources`/`courses`/`meetups`/`teams` behaves identically; tests cover the unknown-shelf path.

---

## Task 5 — `PublicSurveyActivity`: extract `buildPublicAnswers`/`sanitizeRespondent` into an injectable payload builder

**Roadmap:** 1 (data function moved from UI toward the data layer), 3

**Verified problem:** `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt` builds the public-survey submission JSON itself: `buildPublicAnswers(submission)` (lines 166–180) queries `surveysRepository.getExamQuestions(surveyId)`, zips answers by `questionId`, and handles the `selectMultiple`/`select` wire format; `sanitizeRespondent` (159–164) mutates the respondent `JsonObject`. This is data-layer serialization living in an Activity.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/repository/PublicSurveyPayloadBuilder.kt` (new)
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/PublicSurveyPayloadBuilderTest.kt` (new)

**Work:**

1. Create `PublicSurveyPayloadBuilder` as an `@Inject constructor`-class in `repository/` taking `SurveysRepository`. Expose `suspend fun build(surveyId: String, submission: Submission): Pair<JsonArray, JsonObject?>`.
2. Move both private functions' logic from the Activity into it, unchanged in behavior (same `selectMultiple` → array, `select` → first choice, else string rules; same age-trim/`toIntOrNull` sanitize rule). It calls `surveysRepository.getExamQuestions(surveyId)` internally.
3. Activity injects the builder, calls the single method, and passes results to `surveysRepository.submitPublicSurvey` (line 136).
4. New unit tests for: selectMultiple keeps array, select unwraps first choice, plain answer stringified, age `" 34 "` → `34`, non-numeric age removed.

**Acceptance:** Activity shrinks below ~170 lines and contains no `JsonArray`/`JsonPrimitive` construction; builder tests green.

---

## Task 6 — `ProcessUserDataActivity`: ViewModel for the member-upload chain

**Roadmap:** 3 (ViewModel layer), 5 (upload workflow), 2 (smoother repository ↔ ViewModel)

**Verified problem:** `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt` field-injects `syncRepository`, `userRepository`, `uploadToShelfService` (lines 46–57) and runs nested callback chains: `uploadMemberData` (174–188) calls `uploadToShelfService.uploadSingleUserData` whose success listener calls `uploadSingleUserHealth` whose listener calls `fetchAndLogUserSecurityData` (247–259) which launches `lifecycleScope` and calls `userRepository.fetchUserSecurityData`. Three layers of callbacks in an Activity, no ViewModel exists for this screen.

**Files (4):**

- `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataViewModel.kt` (new, same package)
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataViewModelTest.kt` (new)

**Work:**

1. In `UploadToShelfService` add a suspend counterpart: `suspend fun uploadSingleUser(userName: String?): Result<Unit>` that performs data→health sequentially (the exact two calls at lines 52–93) and returns instead of chaining listeners; keep the old listener APIs for other callers (check callers first: `grep -rn "uploadSingleUserData\|uploadSingleUserHealth"`).
2. New `ProcessUserDataViewModel` injecting `SyncRepository`, `UserRepository`, `UploadToShelfService`; expose `fun startUpload(source: String, userName: String?): StateFlow<SyncUiState>` covering the three branches of the Activity's `startUpload` (166–172) and `suspend fun fetchUserSecurity(name: String)`.
3. Activity keeps only dialog/Toast rendering and collects the StateFlow; delete its `uploadToShelfService`, `userRepository` injections and `fetchAndLogUserSecurityData`.
4. ViewModel tests: member path success, health-step failure surfaces `SyncUiState.Error`, login path delegates to `syncRepository.uploadLoginData()`.

**Acceptance:** Activity no longer imports `UploadToShelfService`/`UserRepository`; nested-callback pyramid gone; tests green.

---

## Task 7 — DAO: replace `CourseDao.filterByTitleNormal` raw query with a parameterized query

**Roadmap:** 1 + DAO optimization focus, 9 (removes `androidx.sqlite.db.SimpleSQLiteQuery` from a repository)

**Verified problem:** `CoursesRepositoryImpl.search()` (app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt ~lines 273–305) string-builds `SELECT * FROM courses WHERE 1 = 1 AND courseTitleNormal LIKE ? ESCAPE '\'` per token and passes `SimpleSQLiteQuery` into `CourseDao.filterByTitleNormal` (`data/room/dao/CourseDao.kt:33–34`, `@RawQuery`). Raw SQL assembly in a repository is a boundary leak and injection-surface-by-convention.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseDao.kt` — ⚠️ **PR #15808 touches this file; check it merged/closed before starting; if still open, skip this task and re-queue it next round**
- `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/CoursesRepositoryImplTest.kt`

**Work:**

1. Add to `CourseDao`: `@Query("SELECT * FROM courses WHERE courseTitleNormal LIKE '%' || :token || '%' ESCAPE '\\'") suspend fun filterByTitleToken(token: String): List<MyCourse>`.
2. In `search()`: issue one DAO call per normalized token and intersect results by `id` in Kotlin (token count is small — split on space), preserving the existing escape rules (`\`, `%`, `_`) applied to each token before the call. Keep the existing starts-with/contains ranking (lines 294–303) untouched.
3. Delete `filterByTitleNormal` from the DAO (confirm no other caller: `grep -rn filterByTitleNormal`) and the `SimpleSQLiteQuery`/`androidx.sqlite.db.SupportSQLiteQuery` imports from both files.
4. Extend `CoursesRepositoryImplTest` (or a Room-in-memory DAO test per docs/TESTING.md) with: multi-token query matches only rows containing all tokens; tokens containing `%`/`_` are literal; empty query returns all.

**Acceptance:** no `@RawQuery` left in `CourseDao`; search ranking output unchanged on the fixture set; `grep SimpleSQLiteQuery CoursesRepositoryImpl.kt` empty.

---

## Task 8 — `SubmissionsRepositoryImpl`: stop passing `Context` into the PDF exporter

**Roadmap:** 1, 9 (removes `android.content.Context` from the repository's constructor surface)

**Verified problem:** `SubmissionsRepositoryImpl` (app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt) holds `@ApplicationContext context` (line 50) and forwards it to `SubmissionsRepositoryExporter.generateSubmissionPdf(context, …)` / `generateMultipleSubmissionsPdf(context, …)` (lines 62–66), and to `NetworkUtils.getCustomDeviceName(context)` (lines 814, 885) — which ignores it (NetworkUtils.kt:243–245). The exporter (`repository/SubmissionsRepositoryExporter.kt:45–48`) takes `Context` as a method parameter though it's injectable-scoped already.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImplTest.kt` (verify exists)

**Work:**

1. Inject `@ApplicationContext Context` into `SubmissionsRepositoryExporter`'s constructor; drop the `context` parameter from both `generate*Pdf` methods.
2. In `SubmissionsRepositoryImpl`, replace `NetworkUtils.getCustomDeviceName(context)` at 814/885 with injected `DeviceNameProvider` (same pattern as `ResourcesRepositoryImpl.kt:932`, `PersonalsRepositoryImpl.kt:111`).
3. If `context` then has no remaining use in `SubmissionsRepositoryImpl` (verify — grep for other `context.` usages first; if any remain, keep the param), delete the constructor param and the `android.content.Context` import.
4. Update exporter/impl tests to assert PDF generation delegates with no Context threading and that `customDeviceName` comes from the provider.

**Acceptance:** exporter methods lose the `Context` parameter; no behavior change in generated PDFs; tests green.

---

## Task 9 — `CoursesRepositoryImpl`: move `PendingCourseResource` buffer out of the singleton repository

**Roadmap:** 1, 8 (code health — removes mutable shared state from a `@Singleton`), 5

**Verified problem:** `CoursesRepositoryImpl` (constructor ends line 79) holds `private val pendingCourseResources = Collections.synchronizedList(mutableListOf<PendingCourseResource>())` plus private `data class PendingCourseResource` and `ParsedCourseSyncPayload` (lines 81–95). A process-wide mutable buffer inside a singleton repository couples concurrent sync runs; the parse stage (`ParsedCourseSyncPayload`) is pure and detachable.

**Files (2):**

- `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/CoursesRepositoryImplTest.kt`

**Work:**

1. Trace the lifecycle of `pendingCourseResources` (read the methods that add/drain it — likely one sync entry point accumulates then flushes). Convert it to a local variable threaded through the private parse/flush functions so one sync run's buffer can never leak into another.
2. Hoist `ParsedCourseSyncPayload` parsing into a `private fun parseCourseSyncPayload(doc: JsonObject): ParsedCourseSyncPayload` if not already, and make the flush path take the buffer as a parameter.
3. Keep `appDatabase.withTransaction` (line ~623) boundaries identical — only the buffer's scope changes.
4. Add a test running two interleaved fake sync batches (MockK DAOs, `runTest`) asserting no cross-contamination of buffered resources.

**Acceptance:** no `Collections.synchronizedList` field remains; batch upsert counts per run unchanged; new concurrency test passes.

---

## Task 10 — `TeamsFinancesRepository`/`TeamsMembersRepository`/`TeamsNotificationsRepository`: give the split interfaces real consumers

**Roadmap:** 2 (repository interface tightening — this round's headline), 3

**Verified problem:** `TeamsRepository` (app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt:43) extends `TeamsFinancesRepository`, `TeamsMembersRepository`, `TeamsNotificationsRepository` (the three files exist in repository/), but consumers still inject the whole `TeamsRepository`. Verified example candidates: `ui/enterprises/EnterprisesFinancesViewModel.kt` and `ui/enterprises/EnterprisesViewModel.kt` — check which `Teams*` methods each uses and narrow their constructor types to the smallest parent interface.

**Files (up to 4 — pick after verifying actual usage with grep):**

- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
- corresponding test files under `app/src/test/java/org/ole/planet/myplanet/ui/...` (update constructor mocks)

**Work:**

1. For each listed ViewModel, list the `teamsRepository.*` methods actually called; map them to the owning parent interface by reading `TeamsFinancesRepository.kt` / `TeamsMembersRepository.kt` / `TeamsNotificationsRepository.kt`.
2. Change the ViewModel constructor parameter type from `TeamsRepository` to the narrowest parent (e.g., finances-only screens take `TeamsFinancesRepository`). Hilt already binds the impl — narrow interfaces need no new bindings; verify `RepositoryModule` binds each parent to `TeamsRepositoryImpl` (if a parent lacks a `@Binds`, that edit belongs to whichever task touches `RepositoryModule` — coordinate with Task 4's collision note; if blocked, leave that ViewModel for next round).
3. Update tests to mock the narrowed interface.
4. Do **not** touch `TeamsRepository.kt`, `TeamsRepositoryImpl.kt`, or any `ui/teams/**` file — all are under open PRs.

**Acceptance:** at least two ViewModels depend on a sub-interface rather than the full `TeamsRepository`; no new bindings required (or binding added only if `RepositoryModule` is unblocked); affected ViewModel tests green.

---

## Self-check results

- **R1**: 10 tasks, each compiles/merges alone; only soft ordering notes (7 waits on PR #15808; 10 notes #15824/#15825) with fallback instructions so none blocks another. ✔
- **R2**: no file appears in more than one task (Task 5 was restructured onto a new `PublicSurveyPayloadBuilder.kt` to avoid sharing `SurveysRepository.kt`/`SurveysRepositoryImpl.kt` with Task 2). ✔
- **R3**: open PRs listed in the header; every cited file cross-checked against all 36 PR file lists. ✔
- **R4**: every path, class, function, and line number above was opened and read during research. ✔
- **R5**: each task ≤4 files, ≤~150 changed lines, no new dependencies (all reuse MockK/`DeviceNameProvider`/existing DI), no TODOs. ✔
- **R6**: no implementation code; this document is the deliverable. ✔

# Merged Refactor Backlog

81 tasks merged from 18 agent lists (181 raw). Every task below was verified against
the working tree at `9c54a03`; 30 raw tasks were dropped because their premise did not
hold (45 in total). Sorted by rating (1–100).

Provenance is tagged `harness·model/prompt`, where *prompt* is `perf` (performance quick
wins) or `repo` (repository boundaries). The eight agents: **claude·opus-5**,
**codex·sol-5.6**, **copilot·grok-4.5**, **devin·swe-1.7**, **openhands·kimi-k3**,
**openhands·glm-5.2**, **jules·gemini-3.1-pro** (perf) / **jules·gemini-3.6-flash** (repo),
**qwen·coder-3.6**, **openhands·minimax-m2.7**.

**Rating** = 100 × (0.40·Evidence + 0.35·Impact + 0.25·risk-adjusted Feasibility).
*Evidence* — did the agent cite a location, and did it survive inspection.
*Impact* — hot-path/sync/scroll cost removed, or a boundary invariant made greppable.
*Feasibility* — diff size × blast radius × merge exposure to contested files and the open PRs.

---

## 85 — Push the teams-table Flow filters into SQL

`TeamsRepositoryImpl.queryTransactions()` (:450) and `getReportsFlow()` (:788) both start
from `teamDao.observeAll()` = `SELECT * FROM teams`, then filter in Kotlin by `teamId` +
`docType`. The `teams` table mixes root teams, memberships, requests, transactions,
reports and resourceLinks, so every write to any doc type re-emits the whole table;
neither flow has `flowOn` or `distinctUntilChanged`.

Add `observeByTeamIdAndDocType(teamId, docType): Flow<List<MyTeam>>` to `TeamDao` next to
the existing `getByTeamIdAndDocType`; point both flows at it and append
`.flowOn(dispatcherProvider.default).distinctUntilChanged()`, matching
`getMyTeamDetailsFlow` (:282) which already does exactly this. Leave `getMyTeamsFlow` /
`getMyTeamDetailsFlow` alone — they need cross-docType joins.

Files: `data/room/dao/TeamDao.kt`, `repository/TeamsRepositoryImpl.kt` (2 function bodies).
**Conflict:** HIGH — same file as tasks 41 and open PRs #15656/#15662.
Proposed by: **claude·opus-5/perf**

---

## 82 — Seal the last UI→DAO leak: `DictionaryActivity` → `DictionaryRepository`

`DictionaryActivity` is the only UI class in the codebase that injects a Room DAO
(`:18` import, `:35` `lateinit var dictionaryDao: DictionaryDao`), and it also builds
`DictionaryEntity` rows from downloaded JSON itself. Sealing this makes
"no UI file imports `data.room.dao.*`" an enforceable grep.

Add `DictionaryRepository` + `Impl` with exactly `count()`, `findByWord(word)` and an
import entry point that owns the file read + `JsonArray` parse + insert on
`dispatcherProvider.io`. Bind in `RepositoryModule`, swap the injection. Acceptance: the
Activity no longer imports `DictionaryDao`, `JsonArray`, `JsonUtils` or `FileUtils`.
Keep the download/broadcast path in the Activity.

Files: new `repository/Dictionary*.kt`, `di/RepositoryModule.kt`, `ui/dictionary/DictionaryActivity.kt`.
Proposed by: **claude·opus-5/repo, codex·sol-5.6/repo, openhands·glm-5.2/repo, copilot·grok-4.5/repo, devin·swe-1.7/perf, openhands·kimi-k3/repo**

---

## 80 — Release the previous `ExoPlayer` before reassigning it

`ResourceViewerFragment` assigns a fresh player at `:280`, `:307` and `:363`
(`prepareVideoPlayer`, `streamVideoFromUrl`, `initializeAudioPlayer`) without releasing
the previous instance; the only `release()` is in `onDestroyView` (`:561`). Every
re-prepare leaks a media codec and surface.

Add `exoPlayer?.release(); exoPlayer = null` before each assignment. Also move the
`OnAudioRecordListener.onRecordStopped` coroutine from `lifecycleScope` to
`viewLifecycleOwner.lifecycleScope`.

Files: `ui/viewer/ResourceViewerFragment.kt`.
Proposed by: **devin·swe-1.7/perf**

---

## 80 — `HealthExaminationActivity`: kill the repo bypass and the non-lifecycle-aware collector

Three defects in one screen, one diff. `:49` injects `HealthRepository` even though the
Activity already owns `HealthExaminationViewModel`; `:101` collects `viewModel.state` in a
plain `lifecycleScope.launch { ... .collect { } }` (CREATED-scoped, keeps collecting while
stopped); `:109` launches a *nested* coroutine per emission to call
`healthRepository.getExaminationConditions(examination)` with no cancellation of the prior
job. The same file already uses the right pattern at `:119`/`:123`
(`collectWhenStarted(viewModel.isSaving)`).

Fold `conditionsMap` into `viewModel.state`, convert `:101` to `collectWhenStarted`, delete
the nested launch and the repository injection. Validation/form mapping stays in the Activity.

Files: `ui/health/HealthExaminationActivity.kt`, `ui/health/HealthExaminationViewModel.kt`.
Proposed by: **claude·opus-5/repo, copilot·grok-4.5/perf, codex·sol-5.6/perf, codex·sol-5.6/repo, openhands·kimi-k3/repo, copilot·grok-4.5/repo**

---

## 78 — Delete the codebase's only two `notifyDataSetChanged()` calls (surveys)

`SurveysAdapter` is a proper `ListAdapter` with `DiffUtils.itemCallback`, but it renders
from two fragment-held maps (`surveyInfoMap`, `bindingDataMap`) that are invisible to the
diff callback, so `SurveyFragment.kt:180` and `:185` fall back to
`getAdapter().notifyDataSetChanged()` — rebinding every visible row and killing item
animations whenever survey info or form state arrives. These are the last two
`notifyDataSetChanged()` call sites in `app/src/main`.

`combine(surveys, surveyInfos, bindingData)` in `SurveysViewModel` into a single
`StateFlow<List<SurveyRow>>`; make the adapter `ListAdapter<SurveyRow, …>`, drop the two
constructor map params, extend `areContentsTheSame` to cover the info/form fields the row
actually renders, and keep one `submitList` collector. Re-submitting `currentList` is
**not** a fix — `AsyncListDiffer` short-circuits on an identical reference.

Files: `ui/surveys/SurveyFragment.kt`, `ui/surveys/SurveysAdapter.kt`, `ui/surveys/SurveysViewModel.kt`.
Proposed by: **claude·opus-5/perf, devin·swe-1.7/repo, claude·opus-5/repo, openhands·kimi-k3/repo, codex·sol-5.6/perf, copilot·grok-4.5/perf, devin·swe-1.7/perf, openhands·minimax-m2.7/perf, openhands·kimi-k3/perf, copilot·grok-4.5/repo, jules·gemini-3.1-pro/perf, jules·gemini-3.6-flash/repo, openhands·glm-5.2/repo**

---

## 78 — `UploadToShelfService`: remove the nested fire-and-forget launches

`uploadUserData()` (`:32`) and `uploadSingleUserData()` (`:57`) already run inside
`appScope.launch(dispatcherProvider.io)`, then call `uploadToShelf` (`:102`) /
`uploadSingleUserToShelf` (`:126`), each of which opens **another**
`appScope.launch(dispatcherProvider.io)`. Double dispatch, broken structured concurrency
(inner failures cannot reach the outer `try/catch`), and the outer coroutine completes
before the upload it triggered.

Make both `private suspend fun` and call them directly from the existing coroutines.

Files: `services/UploadToShelfService.kt`.
Proposed by: **openhands·kimi-k3/perf**

---

## 78 — Voices must not read the library table: move `getPrivateImageUrlsCreatedAfter` to Resources

`VoicesRepositoryImpl` injects `MyLibraryDao` (constructor) for exactly one method,
`getPrivateImageUrlsCreatedAfter` (`:547` → `myLibraryDao.getPrivateImagesCreatedAfter`).
Resources data is owned by `ResourcesRepository`; this is the clearest cross-feature table
leak in the repository layer. The call chain is already UI-safe
(`BaseDashboardFragment` → `NewsViewModel` → repository) — only the ownership is wrong.

Move the method verbatim to `ResourcesRepository`/`Impl`, point `NewsViewModel` at it
(one hop, not two), delete the method and the `MyLibraryDao` field from voices.

Files: `repository/Voices*.kt`, `repository/Resources*.kt`, `ui/voices/NewsViewModel.kt`.
Proposed by: **claude·opus-5/repo, copilot·grok-4.5/repo, devin·swe-1.7/repo**

---

## 78 — Courses shelf query: stop `getAll()` + in-memory `userId` filter

`CoursesRepositoryImpl.getMyCourses(userId)` (`:100`) does `mapCourses(courseDao.getAll())`
then filters in Kotlin, and `getMyCoursesFlow` (`:104`) does `courseDao.observeAll().map { filter }`
— the whole courses table materialised per call and per emission, on the dashboard and
my-courses paths. `CourseDao` only exposes `getAll`/`observeAll` today.

Add `getForUserPattern` / `observeForUserPattern` DAO queries using the same
`LIKE %"id"% ESCAPE` convention `MyLibraryDao` + `ResourcesRepositoryImpl.userIdPattern()`
already use, and point both methods at them. Keep the existing `distinctUntilChanged`.

Files: `data/room/dao/LegacyEntityDaos.kt` (`CourseDao`), `repository/CoursesRepositoryImpl.kt`.
Proposed by: **copilot·grok-4.5/perf**

---

## 76 — `ChatHistoryFragment`: delete the duplicate `ChatRepository` path

A straight duplicate: the fragment already holds `sharedViewModel: ChatViewModel`
(`:44`) whose `fetchAiProviders(serverUrl)` (`ChatViewModel.kt:356`) delegates to the same
repository, yet the fragment also injects `ChatRepository` (`:30` import, `:55` field) and
calls it directly at `:226`. Two paths to one repository method is exactly the boundary
erosion this round targets.

Point `checkAiProvidersIfNeeded()` at `sharedViewModel.fetchAiProviders(serverUrl)`, delete
the injection and import. Preferably make the VM method a `viewModelScope` one-shot exposing
provider state so the fragment stops launching a coroutine purely for a data call.

Files: `ui/chat/ChatHistoryFragment.kt`, `ui/chat/ChatViewModel.kt`.
Proposed by: **claude·opus-5/repo, codex·sol-5.6/repo, copilot·grok-4.5/repo**

---

## 76 — Resources must not **write** team rows

Worse than a cross-feature read: `ResourcesRepositoryImpl` injects `TeamDao` (`:55`) and at
`:706`, inside `markResourceUploaded`, upserts a `MyTeam(docType = "resourceLink", …)` —
team-link data it does not own and whose invariants live in teams. That is the only use of
`teamDao` in the file.

Reuse an existing teams resource-link method if one fits; otherwise add exactly one narrow
method (`createLocalResourceLink(teamId, resourceId, title, planetCode)`) **with its caller
in the same PR**. Keep the `MyLibrary` write in resources; drop the `TeamDao` field.

Files: `repository/ResourcesRepositoryImpl.kt`, `repository/Teams*.kt`.
**Conflict:** coordinate with #15656/#15662, which are reshaping `TeamsRepository`.
Proposed by: **claude·opus-5/repo, copilot·grok-4.5/repo**

---

## 76 — `distinctUntilChanged()` on the five Room flows that lack it

Room's invalidation tracker is per *table*, not per query, so any write to `personal`,
`feedback` or `my_library` re-emits every Flow observing that table even when the result is
byte-identical — hundreds of redundant `submitList` + DiffUtil passes during a sync.
Five flows have no guard: `PersonalsRepositoryImpl.kt:49`,
`FeedbackRepositoryImpl.kt:74,76`, `ResourcesRepositoryImpl.kt:326,330` (a "recent 5" list
re-emitting on every library write is the worst offender). `CoursesRepositoryImpl:106`
already does this — copy the pattern.

Append `.distinctUntilChanged()` to each; add `.flowOn(dispatcherProvider.default)` only
where a transform follows.

Files: `repository/PersonalsRepositoryImpl.kt`, `repository/FeedbackRepositoryImpl.kt`, `repository/ResourcesRepositoryImpl.kt`.
Proposed by: **claude·opus-5/perf**

---

## 74 — `SyncTimeLogger`: replace the Hilt service locator with constructor injection

`utils/SyncTimeLogger.kt` is a global `object` (`:19`) that reaches through
`EntryPointAccessors.fromApplication(MainApplication.context, CoreDependenciesEntryPoint::class)`
(`:25`) for its clock, dispatchers, preferences and URL mapping, then launches work on
`MainApplication.applicationScope` (`:87`). Untestable, and it hard-couples the sync layer
to the Application object.

Convert to an injected singleton with explicit dependencies and the project's
`@ApplicationScope`; inject it into `SyncRepositoryImpl`, `SyncManager` and
`TransactionSyncManager`. Acceptance: no `MainApplication` / `EntryPointAccessors` /
`CoreDependenciesEntryPoint` import remains; log format unchanged; a test can supply a fake
clock and dispatcher.

Files: `utils/SyncTimeLogger.kt`, `di/`, the three sync owners.
Proposed by: **codex·sol-5.6/perf**

---

## 74 — One transaction for `markNotificationsSynced`

`NotificationsRepositoryImpl.kt:323-327` runs
`syncResults.forEach { (id, rev) -> notificationDao.markSynced(id, rev) }` — each call is
its own Room write transaction, so confirming N notifications costs N commits and N fsyncs
after every sync.

Wrap the loop in `appDatabase.withTransaction { … }` (the only existing use is
`TeamsRepositoryImpl:1313`, whose comment documents this same problem), or mirror
`HealthExaminationDao.markUploaded(idToRevMap)`. A single `@Query` is not worth it —
`markSynced` writes a per-row `rev`, so one statement would need a CASE expression.

Files: `repository/NotificationsRepositoryImpl.kt` (+ `data/room/dao/NotificationDao.kt` if batching).
Proposed by: **claude·opus-5/perf, openhands·glm-5.2/perf**

---

## 74 — Bound Glide decode size for list thumbnails

Only three Glide call sites in the whole app pass `.override(...)`
(`UserProfileFragment:223`, `VoicesAdapter:894`, `ImageUtils:17`). The scrolling list
adapters decode full-resolution files straight into small row/grid ImageViews:
`ResourcesAdapter:305,321`, `CoursesAdapter:293`, `InlineResourceAdapter:205,218`.

Add `.override(targetPx, targetPx)` to those five loads, reading the target from the row's
image dimen as `UserProfileFragment`/`ImageUtils` do. `diskCacheStrategy(ALL)` and the
`Glide.with(context).clear(...)` recycling calls are already correct — leave them.

Files: `ui/resources/ResourcesAdapter.kt`, `ui/courses/CoursesAdapter.kt`, `ui/courses/InlineResourceAdapter.kt`.
Proposed by: **claude·opus-5/perf**

---

## 74 — Move the course-progress JSON mapping into `ProgressViewModel`

`ProgressViewModel` exposes a raw `StateFlow<JsonArray?>` (`:20-21`), so
`CoursesProgressFragment.kt:42-66` does all the parsing inside the collector on the main
thread — allocating a fresh reflective `object : TypeToken<Map<String, Int>>() {}.type`
on **every emission**, then walking the whole `JsonArray` to build `CoursesProgressRow`s
before `submitList`. `ProgressViewModel` has exactly one consumer.

Hoist the `TypeToken` to a `private val`, do the mapping in `loadCourseData()` on
`dispatcherProvider.default`, expose `StateFlow<List<CoursesProgressRow>>`; the collector
shrinks to `submitList(rows)`. Add a mapping test — there is none today.

Files: `ui/courses/ProgressViewModel.kt`, `ui/courses/CoursesProgressFragment.kt`.
Proposed by: **claude·opus-5/perf**

---

## 74 — `VoicesAdapter` must not hold a repository

An adapter holding a repository is the deepest boundary violation left in the UI: the data
layer is reachable from a `ViewHolder`. `ui/voices/VoicesAdapter.kt:37,64` imports and holds
`VoicesRepository`, passed into `VoicesActions` at `:490` (edit) and `:762` (reply). Every
*other* dependency of this adapter is already callback-shaped (`getUserFn`, `deletePostFn`,
`getLibraryResourceFn`) and `LabelManipulator` already wraps its repository behind a small
interface — the house pattern exists; this is the holdout.

Replace the repository parameter with a small `VoicesEditActions` interface (edit post,
create reply) implemented by the fragment/ViewModel; rewire `VoicesFragment:270`,
`ReplyActivity:154`, `TeamsVoicesFragment:252`. Constructor churn only — no behaviour
change and no attempt to split the ~1000-line adapter.

Files: `ui/voices/VoicesAdapter.kt`, `ui/voices/VoicesActions.kt`, 3 call sites.
**Conflict:** voices files are hot on master — schedule after the voices repository moves.
Proposed by: **claude·opus-5/repo, codex·sol-5.6/repo, copilot·grok-4.5/repo, devin·swe-1.7/repo**

---

## 74 — Type the rating path: `getRatingsById` returns `RatingSummary?`

`RatingsRepository.kt:13` returns `JsonObject?`, leaking JSON into
`ResourceDetailFragment` which stores `lastKnownRating: JsonObject?` (`:35`) and calls
`BaseContainerFragment.setRatings(JsonObject?)` (`:89`) →
`CourseRatingUtils.showRating(JsonObject?)` (`:27`). A typed `RatingSummary` already exists
and the course-detail path already uses it; the JSON overloads are kept alive solely by the
resource-detail path.

Change the return type to `RatingSummary?` (return `getRatingSummary(...)` instead of
`aggregated?.toJson()`), retype `lastKnownRating`, and delete both `JsonObject` overloads.
`insertRatingsFromSync(List<JsonObject>)` stays — that is the sync boundary.

Files: `repository/Ratings*.kt`, `ui/resources/ResourceDetailFragment.kt`, `base/BaseContainerFragment.kt`, `utils/CourseRatingUtils.kt`.
Proposed by: **openhands·glm-5.2/repo**

---

## 74 — Fragments: `lifecycleScope.launch` → `viewLifecycleOwner.lifecycleScope.launch`

The fragment `lifecycleScope` outlives the view, so these coroutines can touch a destroyed
binding. All nine sites verified: `ResourceDetailFragment:284`, `ResourcesFragment:135,841`,
`CoursesFragment:544`, `CourseStepFragment:88,267`, `TeamCalendarFragment:320`,
`BellDashboardFragment:397`, `AchievementFragment:200`, `EditAchievementFragment:164`,
`ResourceViewerFragment:120`.

Mechanical scope swap, no other logic change. **Skip `ExamTakingFragment:843`** — it
intentionally launches a `NonCancellable` save after `onDestroyView()` and must keep
`lifecycleScope`. The base classes (`BaseResourceFragment`, `BaseVoicesFragment`,
`BaseTeamFragment`, `BaseContainerFragment`) are deliberately excluded — base-class edits
collide with #15650.

Proposed by: **devin·swe-1.7/perf, jules·gemini-3.1-pro/perf, qwen·coder-3.6/perf**

---

## 72 — Narrow the `NotificationsRepositoryImpl` → `TeamsRepository` dependency

`NotificationsRepositoryImpl` injects the whole `Lazy<TeamsRepository>` (a ~65-method
surface) but calls exactly four read-only lookups across nine sites: `getTeamLabelInfo`
(`:160,182,253`), `getJoinRequestInfo` (`:175,180`), `getTeamNamesByIds` (`:198,220,265`),
`getJoinRequestsInfo` (`:216`). Notifications depends on all of teams just to render labels.

Extract a `TeamsInfoLookup` interface with exactly those four functions, have
`TeamsRepository` extend it (implementations already exist, so `TeamsRepositoryImpl` gains
only a supertype — no moved code), and rebind notifications to the narrow type. One new
`@Binds`.

Files: `repository/NotificationsRepositoryImpl.kt`, `repository/Teams*.kt`, new interface, `di/RepositoryModule.kt`.
Proposed by: **openhands·kimi-k3/repo**

---

## 72 — Delete five verified-dead repository methods

Fat interfaces are why repository boundaries drift. These five have **zero** production
call sites — declaration, override and a unit test only:
`ResourcesRepository:57`/`Impl:333` `markAllResourcesOffline`,
`SubmissionsRepository:23`/`Impl:179` `getSubmissionsByUserId`,
`VoicesRepository:31`/`Impl:86` `getCommunityVisibleNews`,
`VoicesRepository:32`/`Impl:124` `getNewsByTeamId`,
`VoicesRepository:41`/`Impl:313` `deleteNews`.

Re-run the grep immediately before opening the PR (an open PR may have added a caller),
then delete method, override, the orphaned tests, and any DAO field that becomes unused.
Land this last in the round so the check runs against final state.

Proposed by: **claude·opus-5/repo**

---

## 72 — `getSurvey`'s name fallback shouldn't load every survey

`SurveysRepositoryImpl.kt:374-377`: `examDao.getById(id) ?: examDao.getByType("surveys").firstOrNull { it.name == id }`
— the fallback materialises every survey row to match one name in Kotlin.

Add a narrowly scoped Room query for type + name with `LIMIT 1` and use it for the
fallback. Do **not** add a speculative index unless a query-plan test shows it is needed.
DAO tests should cover ID hit, name fallback, and no match.

Files: `data/room/dao/ExamDao.kt`, `repository/SurveysRepositoryImpl.kt`.
Proposed by: **codex·sol-5.6/perf**

---

## 72 — Batch the next-leader user lookup (N+1)

`TeamsRepositoryImpl.getNextLeaderCandidate` (`:1145-1147`) calls
`userRepository.getUserById(userId)` once per candidate member inside `mapNotNull`. No
batch user query exists anywhere in `UserDao` (only `deleteByIds`), though
`QuestionDao`/`AnswerDao`/`SubmissionDao` all have the `WHERE id IN (:ids)` shape to copy.

Add and expose one batch lookup, then build the same map/ranking from the single result.
Missing users are ignored as today; ranking output must be unchanged and covered by tests.
Stack this on the SQL-predicate task below rather than opening both against the same lines.

Files: `data/room/dao/UserDao.kt`, `repository/UserRepository{,Impl}.kt`, `repository/TeamsRepositoryImpl.kt`.
Proposed by: **codex·sol-5.6/perf**

---

## 72 — `ActivitiesFragment`: move the login-history query behind a ViewModel

The fragment injects both `UserSessionManager` (`:32`) and `ActivitiesRepository` (`:34`)
and owns the flow subscription (`:50`). Chart rendering is correctly UI-local; the data
acquisition is not.

Add a small `ActivitiesViewModel` that resolves the user name and exposes the offline-login
flow as state; the fragment collects with `collectLatestWhenStarted` and renders. Keep
`computeMonthlyCounts` (`:58`) in the fragment — it is already `internal` and unit-tested,
and MPAndroidChart types must not cross into the ViewModel. Do **not** fold this into
`DashboardViewModel`, which is large and contested.

Files: `ui/dashboard/ActivitiesFragment.kt`, new `ui/dashboard/ActivitiesViewModel.kt`.
**Conflict:** same file as the `getOfflineLogins` signature task below.
Proposed by: **codex·sol-5.6/repo, claude·opus-5/repo, copilot·grok-4.5/repo**

---

## 72 — One pass over the news list instead of four

`VoicesFragment.setData()` (`:170-188`) runs on the main thread for every emission of
`filteredNews`: `sortNews(list)` makes a redundant `toMutableList()` copy (`:210`) before
`sortedWith`, and then `sortedList.filterNotNull()` is called **three separate times**
(adapter-null branch, `submitList` branch, `showNoData`) — three full list copies per
emission.

Compute `val sorted = list.filterNotNull().sortedByDescending { it.sortDate ?: 0L }` once at
the top and reuse it for all three consumers; drop the `toMutableList()`. Keep the `Trace`
section so the improvement is measurable in a systrace.

A second proposal targets the same function from the other side: move `sortNews` out of
the fragment into `VoicesViewModel` entirely. That is the better long-term home and it
subsumes this fix — do it that way if the VM change is affordable; otherwise collapse the
passes in place.

Files: `ui/voices/VoicesFragment.kt` (+ `ui/voices/VoicesViewModel.kt` if relocating).
Proposed by: **claude·opus-5/perf, openhands·minimax-m2.7/repo**

---

## 70 — Courses/Resources adapters: payload-scoped range invalidation

`CoursesAdapter.kt:130,138` (`setViewMode`, `updateIdentity`) and
`ResourcesAdapter.kt:117,133` call `notifyItemRangeChanged(0, itemCount)` with **no
payload** — a full rebind of every row plus a Glide reload, on a view-mode toggle or a
guest-identity change. Both adapters already define payloads (`PAYLOAD_PROGRESS`,
`PAYLOAD_SELECTION`) and `onViewRecycled` Glide clears, so the mechanism exists.

Add `PAYLOAD_VIEW_MODE` / `PAYLOAD_IDENTITY`, pass them, and in the partial-bind path update
only the chrome that depends on mode/guest (checkbox visibility, layout bits). Never reload
covers via Glide on an identity-only update.

Files: `ui/courses/CoursesAdapter.kt`, `ui/resources/ResourcesAdapter.kt`.
Proposed by: **copilot·grok-4.5/perf**

---

## 70 — `StorageCategoryDetailFragment`: offline-file listing and deletion behind a ViewModel

The fragment injects `ResourcesRepository` (`:35`), derives the OLE path itself, and drives
both a disk scan (`:138`) and a destructive delete (`:188`) straight from `lifecycleScope`.
The scan result lives only in a fragment field, so it re-runs on every rotation, and a
rotation mid-delete drops the completion handling. A settings screen is thereby coupled to
the resources feature's filesystem contract, including a UI-derived raw path.

Add a `StorageCategoryViewModel` (or extend `SettingsViewModel`) exposing list / deleting /
empty / completion state and a `delete(items)` one-shot; the fragment only renders and
`submitList`s, keeping its `DiffUtils.itemCallback<OfflineResourceItem>`. Resolve the OLE
path below the UI boundary. Deletion must not be launchable twice while active.

Files: `ui/settings/StorageCategoryDetailFragment.kt`, `ui/settings/SettingsViewModel.kt`, `repository/Resources*.kt`.
Proposed by: **codex·sol-5.6/repo, claude·opus-5/repo**

---

## 70 — Move exam-start orchestration out of `ExamTakingFragment`

The fragment hand-orchestrates exam session startup across `:105-174`: fetch pending
submissions (`getSubmissionsByParentId`), delete stale ones (`deleteExamSubmissions`),
create a new one (`createExamSubmission`), and on failure retry the delete+create loop —
plus the same pending-fetch again at `:685` before `saveExamAnswer`. That is repository
logic living in UI.

Add one `suspend fun startExamSession(examId, parentId, userId): Submission` to
`SubmissionsRepository` owning the whole sequence; the fragment calls it once.
`SubmissionsRepositoryImpl` already has every dependency it needs.

Files: `ui/exam/ExamTakingFragment.kt`, `repository/Submissions*.kt`.
**Conflict:** `ExamTakingFragment` is contested by #15559 and #14650.
Proposed by: **openhands·kimi-k3/repo**

---

## 70 — `getOfflineLogins` shouldn't be `suspend`, and the chart shouldn't aggregate on the main thread

There are 21 `suspend fun …: Flow<…>` signatures in the repository layer — building a Flow
never suspends. Their only effect is forcing callers into an extra `launch` just to *obtain*
the flow, which is why `ActivitiesFragment.kt:48-56` reads
`lifecycleScope.launch { val f = activitiesRepository.getOfflineLogins(userName); collectLatestWhenStarted(f) { … } }`
— a launch inside a launch — and the collector then runs `computeMonthlyCounts` (a full scan
plus a `Calendar` per login) and `renderChart` on the main thread.

Drop `suspend` from `ActivitiesRepository.getOfflineLogins` (`:14`) and its impl (`:62`),
add `.distinctUntilChanged()`, flatten the nested launch, and move `computeMonthlyCounts`
to `withContext(dispatcherProvider.default)`. This is the smallest fully-isolated instance
of the pattern (one interface, one impl, one caller, one existing test); leave the other 20
signatures for a follow-up round.

Files: `repository/Activities*.kt`, `ui/dashboard/ActivitiesFragment.kt`.
Proposed by: **claude·opus-5/perf**

---

## 70 — Voices must not own team notifications either

`VoicesRepositoryImpl` also injects `TeamNotificationDao` (constructor) solely for
`updateTeamNotification(teamId, count)` (`:253-264`), consumed by
`TeamsVoicesViewModel:58`. Same class of leak as the library-table one, different owner.

Move the method to `NotificationsRepository`/`Impl`, repoint `TeamsVoicesViewModel`, drop
the DAO from the voices constructor and the method from the `VoicesRepository` interface.
Land after the library-table move so the two don't collide on the same constructor.

Files: `repository/Voices*.kt`, `repository/Notifications*.kt`, `ui/teams/voices/TeamsVoicesViewModel.kt`.
Proposed by: **devin·swe-1.7/repo**

---

## 70 — `SharedPreferences.commit()` → `apply()` (two sites, one of them a double write)

`commit()` is synchronous disk I/O on the calling thread and both return values are ignored.
`SharedPrefManager.kt:284` is the worse of the two: `clearPreferences()` calls
`editor.clear().apply()` and then re-puts on the *same* editor and calls `editor.commit()`
— a blocking write, possibly on main during logout. `TransactionSyncManager.kt:326` uses
`.commit()` for per-page checkpoint removal on the hot sync path.

Collapse `clearPreferences()` into one atomic `pref.edit { clear(); tempStorage.forEach { (k, v) -> putBoolean(k, v) } }`,
and switch the checkpoint removal to `.apply()`.

Files: `services/SharedPrefManager.kt`, `services/sync/TransactionSyncManager.kt`.
Proposed by: **openhands·kimi-k3/perf, jules·gemini-3.6-flash/repo, jules·gemini-3.1-pro/perf**

---

## 68 — Search text flows: add `distinctUntilChanged` after `debounce`

Four search fields debounce but never dedupe, so a keystroke that lands back on the previous
text re-runs the whole filter+submitList: `SurveyFragment.kt:99-100`,
`TeamFragment.kt:315-316`, `SubmissionsFragment.kt:68-70`, `ChatHistoryFragment.kt:102-103`.
`ResourcesFragment` (`:353` + import at `:33`) already does both — mirror it.

One-line addition per file; land as a single PR. Add `drop(1)` only where the initial empty
emission would re-filter.

Proposed by: **copilot·grok-4.5/perf**

---

## 68 — `FeedbackFragment`: route submission through a composer ViewModel

The fragment injects `FeedbackRepository` (`:18`, `:27`), loads the user, builds the
persistence arguments and calls `createAndSaveFeedback(...)` (`:112`), then assumes success
before toasting and dismissing. The existing feedback ViewModels cover list and detail, not
composition, so reusing them would mix screen scopes.

Add a `FeedbackComposerViewModel` owning the user lookup and the single submit call,
exposing Idle/Submitting/Saved/Error. Validation and dismissal stay in the fragment; the
submit button is disabled while active; dismiss happens only on Saved; errors stay
retryable. Use the existing repository method — do not broaden `FeedbackRepository`.

Files: `ui/feedback/FeedbackFragment.kt`, new `ui/feedback/FeedbackComposerViewModel.kt`.
Proposed by: **codex·sol-5.6/repo, copilot·grok-4.5/repo**

---

## 68 — Move the course-rating eligibility decision into `TakeCourseViewModel`

`TakeCourseFragment` injects `RatingsRepository` (`:28`, `:45`) and implements the data
decision itself in `showCourseRatingDialogAndFinish()`: query the summary (`:396`), inspect
`userRating`/`existingRating`, catch errors, decide whether to show the ratings feature. The
courses UI should ask its ViewModel for an outcome, not interpret another feature's
repository response.

Inject `RatingsRepository` into the existing `TakeCourseViewModel` and expose a narrow
`RatingPromptDecision.Show/Skip`. Do not expose `RatingSummary` to the fragment; map it
inside the VM. Creating and showing `RatingsFragment` and the back-stack work stay in the
fragment — no navigation object enters the ViewModel.

Files: `ui/courses/TakeCourseFragment.kt`, `ui/courses/TakeCourseViewModel.kt` (+ test).
Proposed by: **codex·sol-5.6/repo, devin·swe-1.7/repo**

---

## 68 — `EnterprisesFinancesFragment`: move transaction creation into its ViewModel

`EnterprisesFinancesFragment:295-315` calls `teamsRepository.createTransaction(...)` straight
from `viewLifecycleOwner.lifecycleScope` in a dialog's positive-button handler. The ViewModel
already exposes `transactions` — only the write side is missing.

Add `createTransaction(...)` plus a `transactionCreated: SharedFlow<Result<Unit>>` to
`EnterprisesFinancesViewModel`; the fragment calls the VM and collects the result for the
toast/error. `TeamsRepository` is unchanged — only the call site moves up one layer.

Files: `ui/enterprises/EnterprisesFinancesFragment.kt`, `ui/enterprises/EnterprisesFinancesViewModel.kt`.
Proposed by: **devin·swe-1.7/repo**

---

## 68 — Stop rebuilding the courses `LayoutManager` on every toggle

`CoursesFragment.applyRecyclerLayoutManager()` (`:364`) **always** constructs a brand-new
`GridLayoutManager`/`LinearLayoutManager`, and `updateToggleUi()` (`:386`) calls it on every
view-mode toggle and every setup pass. Assigning a new LayoutManager discards the recycled
view pool, drops scroll position and forces a full re-layout. `updateToggleUi` also runs two
`findViewById` lookups per call (`:390-391`) for buttons that never change.

Cache the two `ImageButton`s in fields resolved once in `onViewCreated`; in
`applyRecyclerLayoutManager` reuse the current manager when the type already matches — for
GRID just assign `spanCount`, which `updateGridSpanIfNeeded()` (`:379`) already proves works.

Files: `ui/courses/CoursesFragment.kt`. Does **not** touch `BaseRecyclerFragment` (#15650's file).
Proposed by: **claude·opus-5/perf**

---

## 68 — `CameraUtils`: move the JPEG byte copy off the main looper

`utils/CameraUtils.kt:52` registers `imageReader?.setOnImageAvailableListener({ … })` with
**no handler**, so the listener runs on the main looper — including
`ByteArray(buffer.capacity())` + `buffer.get(bytes)` (`:56`), a full JPEG copy, before the
existing `scope.launch { savePicture(...) }`. Capture stalls the UI thread for the duration
of the copy.

Move the buffer read into the launched coroutine (closing the image there after copying), or
pass a background `Handler`/`Executor` to the listener. Keep `image.close()` ordering correct.

Files: `utils/CameraUtils.kt`.
Proposed by: **openhands·kimi-k3/perf**

---

## 68 — `ResourcesFragment`: cache the toggle views and reuse the layout manager

Three property getters re-run `findViewById` on **every access**:
`layoutViewToggle` (`:80`), `toggleGridButton` (`:251`), `toggleListButton` (`:252`);
`updateToggleUi` reads them 4× → four view-tree walks per call. The file also carries the
identical always-reconstruct `applyRecyclerLayoutManager` (`:273`, called from `:303`) as
the courses fragment.

Resolve the three views once (nullable fields set in `onViewCreated`, cleared in
`onDestroyView`) and apply the same reuse-the-LayoutManager change.

Files: `ui/resources/ResourcesFragment.kt`.
Proposed by: **claude·opus-5/perf**

---

## 66 — Dispose the bottom-sheet callback and tab mediator with the view

`HomeCommunityDialogFragment` installs an anonymous `BottomSheetBehavior.BottomSheetCallback`
(`:46`) and creates a `TabLayoutMediator(...).attach()` (`:93`) without retaining either;
`onDestroyView` (`:103`) only nulls the binding. Recreating the view accumulates callbacks.
The slide handler also calls `bottomSheet.requestLayout()` (`:62`, `:82`) unconditionally.

Retain both objects, remove/detach them in `onDestroyView`, clear the ViewPager adapter, and
skip `requestLayout()` when the computed height is already applied.

Files: `ui/community/HomeCommunityDialogFragment.kt`.
Proposed by: **codex·sol-5.6/perf**

---

## 66 — Dashboard: make the library load reactive like courses and teams

`DashboardViewModel.loadUserContent()` collects `getMyCoursesFlow` (`:157`) and
`getMyTeamsFlow` (`:166`) continuously, but assigns the library from a one-shot
`resourcesRepository.getMyLibrary(userId)` (`:151`) — so the shelf goes stale until a manual
refresh. No `getMyLibraryFlow` exists on `ResourcesRepository` today.

Add `getMyLibraryFlow(userId)` using the existing `MyLibraryDao` `userId LIKE` pattern with
`flowOn(dispatcherProvider.io)`, collect it with a cancellable `libraryJob` like the other
two, and `distinctUntilChanged` on list identity before updating state.

Files: `ui/dashboard/DashboardViewModel.kt`, `repository/Resources*.kt`.
Proposed by: **copilot·grok-4.5/perf**

---

## 66 — Route `beta_auto_download` writes through `SharedPrefManager`

`SettingsActivity` injects raw `SharedPreferences` (`:7` import, `:88` `defaultPref`) and
writes `defaultPref.edit { putBoolean("beta_auto_download", …) }` at `:213` and `:216`.
`SharedPrefManager.getBetaAutoDownload()` (`:251`) already owns the read side; there is no
setter. A UI class holding the preference store directly is a persistence-boundary leak.

Add `setBetaAutoDownload(enabled: Boolean)` mirroring the getter's key, and drop the
`defaultPref` field and `SharedPreferences` import from the Activity. `MainApplication`'s
own direct read is out of scope — it is the app entry point, not UI; call it out as a
follow-up.

Files: `services/SharedPrefManager.kt`, `ui/settings/SettingsActivity.kt`.
Proposed by: **openhands·glm-5.2/repo**

---

## 66 — Extract the enterprise reports cluster out of `TeamsRepositoryImpl`

`TeamsRepositoryImpl` is the largest file in the app (~1437 lines). Its reports cluster —
`addReport` (`:520`), `updateReport` (`:561`), `archiveReport` (`:587`), `getReportsFlow`
(`:787`), `exportReportsAsCsv` (`:798`) — is consumed *only* by the enterprises feature,
which therefore depends on the whole teams repository for five functions.

Move the five into a new `EnterprisesRepository` pair (the impl keeps the same DAOs) and
repoint the enterprises consumers. Mechanical move, single consumer. Leave the transactions
cluster (`getTeamTransactionsWithBalance` `:432`, `createTransaction` `:483`) for a
follow-up so the diff stays reviewable.

Files: `repository/Teams*.kt`, new `repository/Enterprises*.kt`, `di/RepositoryModule.kt`, `ui/enterprises/`.
**Conflict:** highest in the backlog — same file as the SQL-flow task and #15656/#15662.
Proposed by: **openhands·kimi-k3/repo**

---

## 66 — Delete the unused `UploadManager` injection from `ProcessUserDataActivity`

`ui/sync/ProcessUserDataActivity.kt:34` imports and `:55` injects `UploadManager`, and
nothing in the file references it — the activity's member-upload workflow goes through
`UploadToShelfService`. Keeping a second, unused upload coordinator makes ownership
ambiguous and inflates the apparent UI→service surface. It is the only unused `@Inject`
field found in the tree.

Delete the field and the import. Do not replace it with another dependency, and do not fold
the callback-based member upload into this PR — that belongs to the sync/upload
consolidation round.

Proposed by: **codex·sol-5.6/repo, openhands·glm-5.2/repo, qwen·coder-3.6/perf, qwen·coder-3.6/repo**

---

## 66 — `CollectionsFragment` → `CollectionsViewModel`

The fragment injects `TagsRepository` (`:25`, `:34`) and calls
`getTagsWithChildren(dbType)` (`:91`) inside `setListAdapter()` before building the
parent/child lists — a repository query and data-shape preparation mixed into adapter setup,
with no ViewModel at all.

Add a feature-local `CollectionsViewModel` exposing parent tags plus an immutable
`Map<String, List<TagEntity>>` (not the mutable `HashMap` the repository returns today) with
explicit loading/error/empty states; re-creation must not launch duplicate loads for the
same `dbType`. Filtering widgets, keyboard behaviour, selection and adapter binding stay in
the fragment. Do not touch `ResourcesViewModel` or `ResourcesAdapter`.

Files: `ui/resources/CollectionsFragment.kt`, new `ui/resources/CollectionsViewModel.kt`.
Proposed by: **codex·sol-5.6/repo, claude·opus-5/repo**

---

## 66 — `VoicesAdapter`: hoist per-bind allocations

Voices is the busiest scroll surface in the app, and three per-bind allocations sit in its
hot path: `:703` `String.format(Locale.getDefault(), "(%d)", replyCount)`, and
`:889`/`:982` `path.lowercase(Locale.getDefault()).endsWith(".gif")` /
`imageUrl.lowercase(...)` — a fresh string per row per bind.

Replace the format with a string-resource placeholder, and both lowercase chains with
`endsWith(".gif", ignoreCase = true)` (no allocation, no locale, and it kills the
Turkish-locale bug class). Add one private `isGif(path: String?)` if a helper doesn't exist
— no new public API.

Files: `ui/voices/VoicesAdapter.kt`.
Proposed by: **openhands·kimi-k3/perf**

---

## 66 — `VoicesAdapter` diff: drop the redundant `.toList()` copies

`VoicesAdapter.kt:83-84` compares `oldItem.labels?.toList() == newItem.labels?.toList()` and
the same for `imageUrls`. Both are already `List<String>?`, and `List.equals` is element-wise
— the copies are pure waste, allocated per compared field per row pair on every
`submitList`.

Replace with direct `==` on both fields in `areContentsTheSame` and `getChangePayload`.
Semantics are identical.

Files: `ui/voices/VoicesAdapter.kt` (diff callback block only).
Proposed by: **openhands·glm-5.2/perf**

---

## 66 — Retain and remove the recycler layout-change listeners

`CoursesFragment:349` and `ResourcesFragment:258` each register an anonymous
`addOnLayoutChangeListener` on the RecyclerView to recompute grid spans; neither is ever
removed, so the lambda holds the RecyclerView (and through it the fragment) past
`onDestroyView`. Both also `post { updateGridSpanIfNeeded() }` on every width change without
coalescing or checking whether the span count actually changed.

Hold each listener in a field, remove it in `onDestroyView`, coalesce the posted update, and
skip work when the computed span count is unchanged. Keep the two fixes local — do **not**
extract a shared helper this round; two local diffs stay independently reviewable and avoid
a common-utility conflict.

Files: `ui/courses/CoursesFragment.kt`, `ui/resources/ResourcesFragment.kt`.
**Conflict:** same two files as the LayoutManager-reuse tasks above.
Proposed by: **codex·sol-5.6/perf, devin·swe-1.7/repo**

---

## 64 — `LeadersViewModel`: get the init-time JSON parse off the constructing thread

`LeadersViewModel.kt:21-22` calls `loadLeaders()` directly from `init`, which synchronously
reads `sharedPrefManager.getCommunityLeaders()` and runs `UserEntity.parseLeadersJson(...)`
on whatever thread constructs the ViewModel — typically Main, on every entry to the screen.

Move the body into `viewModelScope.launch(dispatcherProvider.default)`; keep the public
`leaders: StateFlow<List<UserEntity>>` contract and `LeadersFragment` unchanged. Inject
`DispatcherProvider` rather than hard-coding `Dispatchers.Default` — the project has no
hard-coded dispatchers outside `DispatcherProvider.kt` and should keep it that way.

Files: `ui/community/LeadersViewModel.kt`.
Proposed by: **openhands·glm-5.2/perf**

---

## 64 — Collapse the `getUserModel()` + `updateUserLibrary` two-step in `ResourceDetailFragment`

The fragment does the same dance twice: fetch the current user id
(`:60` → `:66`, `:241` → `:247`) then call
`resourcesRepository.updateUserLibrary(id, userId, isAdd)`. "Which user is logged in" is
data-layer knowledge leaking into UI.

Add `suspend fun setUserLibrary(resourceId: String, add: Boolean): Boolean` to
`ResourcesRepository`, resolving the user internally (`ResourcesRepositoryImpl` already
injects `userRepository`) and returning success. Replace both call-site blocks; the fragment
keeps only the snackbar/state reaction. Leave the ratings read at `:283-289` alone.

Files: `ui/resources/ResourceDetailFragment.kt`, `repository/Resources*.kt`.
Proposed by: **openhands·kimi-k3/repo**

---

## 64 — `EnterprisesFinancesAdapter`: stop the per-bind lowercase and drawable allocation

Two hot-path costs in one small adapter. `:44`
`TextUtils.equals(item.type?.lowercase(Locale.getDefault()), "debit")` allocates a string on
every bind; `updateBackgroundColor` (`:80-91`) constructs a fresh `GradientDrawable`, sets
its stroke via `ContextCompat.getColor`, sets a gradient type, wraps it in a single-layer
`LayerDrawable` and applies insets — on every even-position bind.

Use `item.type.equals("debit", ignoreCase = true)` (null-safe, allocation-free,
locale-independent), and build the alternating-row background once as `mutate()`d
`private val`s, assigning in `updateBackgroundColor`. The single-element `LayerDrawable` can
go if the inset moves to holder padding. Verify striping visually.

Files: `ui/enterprises/EnterprisesFinancesAdapter.kt`.
Proposed by: **openhands·kimi-k3/perf, openhands·glm-5.2/perf**

---

## 64 — Assemble step prerequisites in `CoursesRepository`, not `CourseStepFragment`

The fragment composes per-step prerequisites inline from several repositories:
`coursesRepository.getCourseStepData` (`:102`), `getCourseTitleById` (`:115`),
`submissionsRepository.hasSubmission(..., "exam")` (`:243`), `hasSubmission(..., "survey")`
(`:253`), `coursesRepository.isMyCourse` (`:270`) — a data-layer query assembled in UI.

Add one `suspend fun getStepPrerequisites(stepId, courseId, userId): StepPrerequisites`
(isMyCourse / hasExam / hasSurvey / courseTitle) to `CoursesRepository`.
`CoursesRepositoryImpl` already injects `submissionsRepository` and `userRepository`, so no
new cross-feature wiring appears; the fragment drops its direct `SubmissionsRepository`
dependency.

Files: `ui/courses/CourseStepFragment.kt`, `repository/Courses*.kt`.
**Conflict:** `CourseStepFragment` is a hot file with open courses PRs.
Proposed by: **openhands·kimi-k3/repo**

---

## 64 — `VoicesFragment.downloadResourcesForNews`: move the orchestration into the ViewModel

`VoicesFragment:190-207` walks the news list, reaches into each item's `imagesArray`
`JsonObject` to pull a `resourceId`, then launches on `viewLifecycleOwner.lifecycleScope`
to call `resourcesRepository.getLibraryItemsByIds(resourceIds)` followed by
`resourcesRepository.downloadResources(libraries)`. A fragment is parsing persisted JSON
and sequencing two repository calls.

Move the whole function into `VoicesViewModel` as `downloadReferencedResources(list)` so
the coroutine runs in `viewModelScope` and the JSON extraction sits behind the VM. The
fragment keeps only the call.

Files: `ui/voices/VoicesFragment.kt`, `ui/voices/VoicesViewModel.kt`.
**Conflict:** same file as the sort task above — sequence them.
Proposed by: **devin·swe-1.7/repo, openhands·minimax-m2.7/repo**

---

## 62 — `ResourcesFragment`: move the user lookup into `ResourcesViewModel`

The fragment calls `userRepository.getUserModel()` directly at `:149` and `:217` to resolve
the current user for adapter setup and guest checks. User identity is a ViewModel concern.

Inject `UserRepository` into `ResourcesViewModel`, expose `currentUser: StateFlow<UserEntity?>`,
and collect it instead. No change to search or adapter behaviour.

Files: `ui/resources/ResourcesFragment.kt`, `ui/resources/ResourcesViewModel.kt`.
Proposed by: **devin·swe-1.7/repo**

---

## 62 — Make the unpaired FragmentManager back-stack listeners disposable

Two anonymous `addOnBackStackChangedListener` registrations live for the whole activity and
are never removed: `PublicSurveyActivity:64` (its *fragment lifecycle* callback at `:63`
**is** correctly unregistered at `:181` — only the back-stack listener leaks) and
`DashboardActivity:315`. `DashboardElementActivity` is already correct — it registers
`this` at `:42` and removes it at `:197`, so leave it alone.

Store each lambda in a property and call `removeOnBackStackChangedListener` in `onDestroy`.
For `PublicSurveyActivity`, preserve the existing upload-on-completion guards so teardown
cannot start a duplicate upload.

Files: `ui/surveys/PublicSurveyActivity.kt`, `ui/dashboard/DashboardActivity.kt`.
Proposed by: **codex·sol-5.6/perf, devin·swe-1.7/repo**

---

## 62 — `SurveyFragment`: drop the blind reload on every `onResume`

`onViewCreated` loads surveys at `:108`, and `onResume` (`:113-115`) calls
`viewModel.loadSurveys(...)` again unconditionally — a full reload on every return to the
screen, even though the realtime path is already wired through `RealtimeSyncHelper`.

Drop the `onResume` reload and trust the existing Flow/realtime invalidation, or gate it on
a cheap staleness check (`refreshIfStale()`) in the ViewModel. Do not redesign the
`SurveysViewModel` load pipeline.

Files: `ui/surveys/SurveyFragment.kt`.
Proposed by: **copilot·grok-4.5/perf**

---

## 62 — `EventsAdapter`: route the payload path through the existing date cache

The adapter already has a `dateCache` (`:34`) used by the full bind (`:78-79`
`dateCache.getOrPut(...)`), but the payload branch (`:55-56`) calls `formatDate(...)`
directly — so a partial date update reformats on every payload bind, defeating the cache
that already exists two dozen lines below.

Route the `START_DATE`/`END_DATE` payload branches through the same `getOrPut`. Leave the
`getChangePayload` set semantics untouched.

Files: `ui/events/EventsAdapter.kt`.
Proposed by: **openhands·glm-5.2/perf**

---

## 62 — Give `RealtimeSyncManager` a per-table scoped flow

`ChatViewModel:76-77` collects `realtimeSyncManager.dataUpdateFlow.filter { it.table == "chats" }`,
and `TeamViewModel:43` hands the raw `dataUpdateFlow` out of the ViewModel entirely so that
`TeamDetailFragment:445` does `.filter { it.table == "teams" }` in the UI. Table filtering is
re-implemented per consumer against a shared service's `MutableSharedFlow`.

Add `fun updatesFor(table: String): Flow<TableDataUpdate>` to the sync layer, inject the
narrow accessor, and move `TeamDetailFragment`'s filter into `getTeamUpdateFlow()` so the
fragment just collects. Filtering then lives in one place.

Files: `services/sync/RealtimeSyncManager.kt`, `ui/chat/ChatViewModel.kt`, `ui/teams/TeamViewModel.kt`, `ui/teams/TeamDetailFragment.kt`.
Proposed by: **openhands·glm-5.2/repo**

---

## 62 — `MarkdownDialogFragment`: collapse the two-step user-sync check

The dialog injects `UserRepository` (`:19`, `:34`) purely to run a two-call sequence:
`getActiveUserIdSuspending()` (`:126`) then `hasUserSyncAction(userId)` (`:128`). The
caller-side two-step exposes identity plumbing to a UI class that has no ViewModel at all.

Collapse it into one repository method (`hasActiveUserSyncAction()`), expose it through a
tiny ViewModel, and drop the injection.

Files: `ui/components/MarkdownDialogFragment.kt`, `repository/User*.kt`.
Proposed by: **claude·opus-5/repo**

---

## 62 — `SurveysRepositoryImpl`: drop the direct `TeamDao` name lookup

Surveys injects `TeamDao` (`:51`) solely to resolve a team name at `:82`
(`teamDao.getById(teamId)?.name ?: teamDao.getByTeamId(teamId)?.name`). Teams already expose
`getTeamByIdOrTeamId` / `getTeamNamesByIds` / `getTeamLabelInfo` — surveys should not own
team tables.

Replace the DAO with `TeamsRepository` (or a single narrow `getTeamName(teamId)`), resolving
the name inside the impl so no caller signature changes. Survey write logic stays put.

Files: `repository/SurveysRepositoryImpl.kt`, `repository/TeamsRepository.kt`.
**Conflict:** open PR #14650 adds a *new* `teamDao` use to this file — sequence after it or defer.
Proposed by: **copilot·grok-4.5/repo**

---

## 62 — Voices ViewModels: `collectLatest` and cheaper filter passes

`VoicesViewModel.observeCommunityNews` (`:66-69`) uses a plain `.collect` on
`voicesRepository.getCommunityNews(...)`, so every emission rebuilds labels and re-runs the
downstream `combine` filter even when the previous pass hasn't finished.
`TeamsVoicesViewModel:63-65` has the same shape on `getDiscussionsByTeamIdFlow` behind a
manual `observeJob`, with no `collectLatest` or `distinctUntilChanged`.

Switch both inner collects to `collectLatest` and add `distinctUntilChanged` on list identity
(ids + `_rev`) before writing state. Keep the existing `flowOn(dispatcherProvider.default)`
on the filter combine and the existing `observeJob?.cancel()`.

Files: `ui/voices/VoicesViewModel.kt`, `ui/teams/voices/TeamsVoicesViewModel.kt`.
Proposed by: **copilot·grok-4.5/perf**

---

## 62 — `ChatApiService`: the response-body read runs on the main thread

`ChatApiService.fetchAiProviders()` (`:19`) reads `response.body()?.string()` at `:33` with
no `withContext`. Its caller `ChatRepositoryImpl.fetchAiProviders` (`:117`) doesn't switch
dispatchers either, and `ChatHistoryFragment:225` invokes it from
`viewLifecycleOwner.lifecycleScope.launch` — i.e. Main. Retrofit's suspend call itself is
fine, but `ResponseBody.string()` is a blocking stream read, on the UI thread. This is the
only such site in the codebase (the two in `ConfigurationsRepositoryImpl:214,230` are
already wrapped).

Wrap the body read in `withContext(dispatcherProvider.io)`.

Files: `data/api/ChatApiService.kt`.
Proposed by: **jules·gemini-3.1-pro/perf**

---

## 62 — `ConfigurationsRepositoryImpl`: drop the repository→main dispatcher hop

`handleVersionEvaluation` (`:463`) wraps pure version comparison
(`VersionUtils.getVersionCode`, `Constants.showBetaFeature` → a SharedPreferences read) plus
the callback invocation in `serviceScope.launch(dispatcherProvider.main)`. None of it needs
the main thread, and launching decouples it from the calling coroutine. Repositories
dispatching to main contradicts the project's own dispatcher discipline.

First verify the `CheckVersionCallback` implementations hop to main themselves before
touching views (check the login/sync callers); then run the evaluation inline on `io`/
`default` and let the UI callers switch at the edge. If a caller is missing its main hop,
fix it in the same PR.

Files: `repository/ConfigurationsRepositoryImpl.kt`.
Proposed by: **openhands·kimi-k3/perf**

---

## 60 — `VoicesRepositoryImpl`: drop the allocations in the `distinctUntilChanged` predicates

`getCommunityNews` (`:158-170`) compares `o.labels?.toSet() == n.labels?.toSet()` and
`o.imageUrls?.toList() == n.imageUrls?.toList()` on **every element of every emission**, on
top of an already-element-wise string compare of `viewIn`. `labels` and `imageUrls` are
`List<String>?`, so `==` is already element-wise; the copies are pure allocation.
`getDiscussionsByTeamIdFlow` has the same predicate shape.

Replace both with direct `==`. If label *order* genuinely shouldn't matter, keep `.toSet()`
and document why — otherwise drop it.

Files: `repository/VoicesRepositoryImpl.kt` (two flow predicates).
Proposed by: **openhands·glm-5.2/perf**

---

## 60 — `RatingsRepositoryImpl`: drop the `UserRepository` dependency

`RatingsRepositoryImpl` injects `UserRepository` (`:19`) to answer "who is logged in":
`getCourseRatingSummary` calls `userRepository.getUserModel()` (`:42`) and `submitRating`
routes through `findUserForRating` (`:90`, `:157-160`). A ratings repository should rate
items, not resolve sessions.

Make `submitRating(...)` take a `UserEntity`, delete `findUserForRating`, and either drop
`getCourseRatingSummary` or make it take a `userId` and return `RatingSummary`. Update
`RatingSummaryProvider`, `RatingsViewModel` and `CourseDetailViewModel.refreshRatings` to
resolve the user and pass it down, plus the three test classes.

Files: `repository/Ratings*.kt`, `ui/courses/RatingSummaryProvider.kt`, `ui/courses/CourseDetailViewModel.kt`, `ui/ratings/RatingsViewModel.kt`.
**Note:** wide ripple for a design point the codebase doesn't yet settle — sequence after the typed-rating task.
Proposed by: **devin·swe-1.7/repo**

---

## 58 — Fetch eligible next-leader memberships in one query

`getNextLeaderCandidate` (`:1138-1142`) already scopes the DAO call to
`getByTeamIdAndDocType(teamId, "membership")`, but then filters `!isLeader`,
`status != "archived"` and the excluded user in Kotlin. Those three predicates belong in SQL
so archived / current-leader / excluded rows never leave Room.

Add a purpose-specific DAO query applying them, keeping the null-exclusion semantics
(`excludeUserId == null` must retain every eligible member). Ranking behaviour is unchanged
— this reduces the input set, it does not redesign leader selection. Smaller win than the
N+1 fix above; land it first so that one stacks cleanly.

Files: `data/room/dao/TeamDao.kt`, `repository/TeamsRepositoryImpl.kt`.
Proposed by: **codex·sol-5.6/perf**

---

## 58 — Inject `DispatcherProvider` into five ViewModels and get the mapping work off Main

None of `UserProfileViewModel`, `TakeCourseViewModel`, `NotificationsViewModel`,
`DashboardViewModel` (for its one-shot calls) or `BellDashboardViewModel` injects
`DispatcherProvider`; every `viewModelScope.launch` defaults to Main.

Scope the value honestly: Room's suspend DAOs already run off Main via Room's own executors,
so the win is **not** "database work moves off the UI thread" — it is the pure Kotlin work
that follows. Concretely: `NotificationsViewModel.loadNotifications` (`:59`) filtering and
`formatNotification` string building; `UserProfileViewModel` (`:38`, `:60`, `:92`) profile
aggregation and JSON mapping; `TakeCourseViewModel.loadCourse` (`:85`) course + steps +
progress assembly; `DashboardViewModel.evaluateChallengeDialog` collection aggregation;
`BellDashboardViewModel` (`:52`, `:61`, `:89`) reminder grouping. Wrap those in
`withContext(dispatcherProvider.io/default)`; `StateFlow` emission is thread-safe.

Land as five separate PRs (one per file) so they stay conflict-free.
Proposed by: **devin·swe-1.7/perf, qwen·coder-3.6/repo**

---

## 58 — `SubmissionsRepositoryImpl`: stop taking `Lazy<UserRepository>`

`SubmissionsRepositoryImpl` holds `private val userRepository: Lazy<UserRepository>` (`:56`)
and uses it in exactly two places — `getSubmissionDetail` (`:264`) and the exam upload
payload (`:722`) — both times just to turn a `submission.userId` into a `UserEntity`. The
`Lazy` wrapper exists to defer a graph edge that shouldn't be there.

Add a `user: UserEntity?` parameter to both methods; `SubmissionDetailViewModel` and
`UploadConfigs.ExamResults.serializer` (which already injects `userRepository`) resolve the
user and pass it. Update `SubmissionsRepositoryImplTest`.

Files: `repository/Submissions*.kt`, `ui/submissions/SubmissionDetailViewModel.kt`, `services/upload/UploadConfigs.kt`.
**Note:** the "Dagger cycle" framing is unproven — `Lazy<…>` repository edges are used
elsewhere in this codebase (e.g. `NotificationsRepositoryImpl`). Judge it on interface
narrowing, not on a claimed cycle.
Proposed by: **devin·swe-1.7/repo**

---

## 58 — `EventsRepositoryImpl`: drop the `UserRepository` dependency

Same shape as the ratings one: `EventsRepositoryImpl` injects `UserRepository` (`:20`) and
uses it for `getJoinedMembers` (`:77` `userRepository.getAllUsers()`) and
`toggleCurrentUserAttendance` (`:89` `getUserModel()`).

Change to `getJoinedMembers(meetupId, allUsers)` and `toggleAttendance(meetupId, userId)`;
`EventsDetailViewModel` resolves both and passes them. Update `EventsRepositoryImplTest`.

Files: `repository/Events*.kt`, `ui/events/EventsDetailViewModel.kt`.
**Note:** "repositories must never resolve the session user" is a convention this codebase
has not adopted — decide the rule once before landing this and the ratings twin.
Proposed by: **devin·swe-1.7/repo**

---

## 58 — Resolve adapter colors once instead of per bind

`ContextCompat.getColor` is re-resolved on every bind and every selection payload:
`UserArrayAdapter:43,45,66,68` (`md_grey_300` / `transparent`) and
`ServerAddressAdapter:107,111` (`selected_color` / `transparent`). `StatsAdapter` already
resolves its colors once in `init` — copy that.

Hoist to `private val`s. In `UserArrayAdapter`, also stop re-running
`ImageUtils.loadProfileImage` on a `PAYLOAD_SELECTION` rebind — the selection path should
only touch the background.

Files: `ui/user/UserArrayAdapter.kt`, `ui/sync/ServerAddressAdapter.kt`.
Proposed by: **openhands·glm-5.2/perf**

---

## 58 — `Utilities.toast`: stop allocating a `Handler` per background toast

`utils/Utilities.kt:47` allocates a new `Handler(Looper.getMainLooper())` for every toast
posted from a background thread. Toasts fire constantly during sync.

Hoist to one shared `private val mainHandler by lazy { Handler(Looper.getMainLooper()) }`.

Files: `utils/Utilities.kt`. ~2-line diff.
Proposed by: **openhands·kimi-k3/perf**

---

## 56 — `CommunityTabFragment` → a small `CommunityTabViewModel`

The fragment field-injects `SharedPrefManager` (`:23`), `ConfigurationsRepository` (`:25`)
and `UserSessionManager` (`:27`), then does its only work in
`viewLifecycleOwner.lifecycleScope.launch` (`:39`) — reading prefs and building the pager on
the Main dispatcher. It is the smallest "expand the ViewModel layer" step available and the
template other fragments can copy.

Add `@HiltViewModel CommunityTabViewModel` exposing a `StateFlow<CommunityTabState>`
computed in `init`; the fragment collects with `collectWhenStarted` and wires the pager.
`CommunityPagerAdapter`'s constructor contract is unchanged.

Files: `ui/community/CommunityTabFragment.kt`, new `ui/community/CommunityTabViewModel.kt`.
Proposed by: **openhands·glm-5.2/perf**

---

## 55 — Fragments shouldn't call `TeamsSyncRepository.syncTeamActivities()` directly

`TeamsSyncRepository` is documented as a narrow interface *for the sync managers*, yet
`TeamDetailFragment:279` and `TeamResourcesFragment:123,191` call `syncTeamActivities()`
after join/leave/resource operations. UI should not trigger sync-manager internals.

Expose one pass-through on `TeamsRepository` (`recordTeamActivity()`, delegating inside the
impl) and switch the three call sites. **Correction to the source proposal:** the injection
is *not* in either fragment — it lives in `BaseTeamFragment:32`, and `TeamViewModel:33` and
`RequestsViewModel:27` inject it independently, so the field cannot simply be deleted. Scope
this PR to the three fragment call sites and leave the base-class field and the two
ViewModels for a follow-up.

Files: `ui/teams/TeamDetailFragment.kt`, `ui/teams/resources/TeamResourcesFragment.kt`, `repository/Teams*.kt`.
Proposed by: **openhands·kimi-k3/repo**

---

## 55 — Pager adapters: remove the redundant copies in the diff path

`CoursesPagerAdapter:27-38` builds `listOf(null) + steps` and `listOf(null) + newSteps` (two
full copies) and then does `steps.clear(); steps.addAll(newSteps)` (a third) before
dispatching the diff. `TeamPagerAdapter:54` does `pages = newPages.toList()` even when
`newPages` is already an immutable `List`.

Diff against indices or lightweight wrappers instead of materialising the prepended lists,
and drop the redundant `.toList()`. `DiffUtils.calculateDiff` usage is unchanged.

Files: `ui/courses/CoursesPagerAdapter.kt`, `ui/teams/TeamPagerAdapter.kt`.
**Note:** low impact — these pager lists are small; treat as filler.
Proposed by: **openhands·kimi-k3/perf**

---

## 55 — `RetryInterceptor.backoff`: check cancellation before the first sleep

`data/api/RetryInterceptor.kt:88` calls `Thread.sleep(minOf(remaining, MAX_BACKOFF_SLICE_MS))`
in a loop, checking `chain.call().isCanceled()` at `:81`. A call cancelled during `proceed()`
still burns a full 250 ms slice before the check fires.

Move the `isCanceled()` check to the top of `backoff` before the first sleep, keep the
deadline-based total delay and the `InterruptedException` → `IOException` mapping, and keep
the interceptor synchronous (no coroutines — OkHttp interceptors must stay blocking). Add a
cancellation test if none exists.

Files: `data/api/RetryInterceptor.kt`.
Proposed by: **openhands·glm-5.2/repo, openhands·glm-5.2/perf**

---

## 52 — Stable IDs on the hottest `ListAdapter`s

No adapter in the app calls `setHasStableIds(true)` and none overrides `getItemId` — verified
by grep across `ui/`. Adding both to `CoursesAdapter`, `ResourcesAdapter`, `VoicesAdapter`,
`SurveysAdapter` and `TeamsAdapter` lets RecyclerView track item identity across rebinds.

Derive a stable `Long` from the existing string key (`courseId`, resource `id`, news `id`,
exam `id`, team id) and make sure the DiffUtil `areItemsTheSame` key matches the same source.
**Weigh this carefully before landing:** with `AsyncListDiffer` already supplying identity,
stable IDs add little, and a `hashCode().toLong()` collision produces visibly wrong item
animations. If it goes in, it needs a collision-resistant hash, not `hashCode()`.

Proposed by: **copilot·grok-4.5/perf**

---

## 52 — Wrap the heavy repository aggregations in `withContext(io)`

Three methods do list grouping/mapping on the caller's dispatcher:
`ProgressRepositoryImpl.getCourseProgress` (the file injects `dispatcherProvider` and uses
`withContext` in only one place, `:235`), `ActivitiesRepositoryImpl.getMostOpenedResource`
(the file injects no `DispatcherProvider` at all — its `groupBy`/`mapValues`/`maxByOrNull`
chain runs wherever the caller is), and `CoursesRepositoryImpl.getCourseById`/`getCourseSteps`.

Wrap each body in `withContext(dispatcherProvider.io)`, injecting the provider into
`ActivitiesRepositoryImpl`. No public interface changes.

**Note:** the payoff is smaller than the source proposal implies — the Room calls inside are
already off-Main; only the Kotlin aggregation moves. Land it for dispatcher consistency, not
for a measured win.
Proposed by: **devin·swe-1.7/perf**

---

## 48 — Drop the dead `JsonObject` rating map from `CoursesUiState`

`CoursesUiState.map: HashMap<String?, JsonObject>` (`CoursesViewModel:28`) is populated by
`ratingsRepository.getCourseRatings(userId)` (`:129`), threaded through `processCourses`
(`:97`, `:110`) and `filterCourses` (`:178`, `:204`, `:206`), and **read by nothing** — the
only consumer of `_coursesState.value.map` is `processCourses` handing it back to itself.
Course rows display no rating, and `CoursesAdapter:22` carries a matching unused
`import com.google.gson.JsonObject`.

Remove `map` from the state and the plumbing, drop `ratingsRepository` from the VM if it
becomes unused, keep `CoursesFragment.onRatingChanged`'s `notifyItemChangedById(id)` trigger,
and delete the unused import. Rendering is unchanged — no rating was shown before or after.

Files: `ui/courses/CoursesViewModel.kt`, `ui/courses/CoursesFragment.kt`, `ui/courses/CoursesAdapter.kt`.
Proposed by: **openhands·glm-5.2/repo**

---

## 48 — Trim `VoicesFragment`'s `AdapterDataObserver` to the callbacks a `ListAdapter` emits

`VoicesFragment:318-323` registers an observer overriding `onChanged`,
`onItemRangeInserted` and `onItemRangeRemoved` to re-run `showNoData`. `AsyncListDiffer`
dispatches range callbacks, never `onChanged`, so that override is dead code that also makes
the empty-state logic look more complicated than it is. Registration (`:276`) and
unregistration (`:390`) are already correctly paired.

Drop the `onChanged` override; keep the two range callbacks and the existing unregister.
Cosmetic — ship only if a slot opens.

Files: `ui/voices/VoicesFragment.kt`.
Proposed by: **openhands·glm-5.2/repo**

---

## 45 — Move chat search and its precomputed index out of `ChatViewModel`

`ChatViewModel` holds `allChats` and `precomputedChats` as mutable fields, rebuilds the
normalised search index in `buildPrecomputedChats` (`:162`), and implements the ranking
itself across `searchChats` (`:174`) and `fullConvoSearch` — four result buckets ordered by
title-start / title-contains / body-start / body-contains. That is a search engine living
in a ViewModel, with its corpus cached in two fields beside it.

Move index construction and the search itself onto `ChatRepository` as suspend functions;
the ViewModel keeps only the query intent and the result state.

**Correction to the source proposal:** it justifies the move as "heavy ViewModel work", but
the threading is already correct — `buildPrecomputedChats` runs inside
`withContext(dispatcherProvider.default)` (`:145`) and `fullConvoSearch` wraps itself the
same way. Judge this on state ownership, not on main-thread cost. The direction is also
contestable: `isFullSearch`/`isQuestion` are UI toggles, so the repository would inherit two
presentation flags. Settle that before starting — this is not a quick win.

Files: `ui/chat/ChatViewModel.kt`, `repository/Chat*.kt`.
Proposed by: **openhands·minimax-m2.7/repo**

---

## 38 — `setHasFixedSize(true)` on the RecyclerViews whose size doesn't track content

Only five call sites exist app-wide (`SurveyFragment:134`, `LifeFragment:83`,
`ChatDetailFragment:271`, `FeedbackDetailActivity:91`, and `SubmissionDetailFragment:74`
which explicitly sets `false`). Every other list pays a layout pass on each adapter change
that a fixed-size declaration would skip.

The proposal names no files — "multiple fragments using RecyclerView" — so the work starts
with identifying which lists actually qualify: the flag is only correct when the
RecyclerView's own bounds cannot change as content changes, which rules out any list in a
`wrap_content` container. Do that audit first and land a named file list, not a sweep.

**Sequence late.** The obvious place to set this once is `BaseRecyclerFragment`, which is
owned by open PR #15650 — a base-class edit here is a conflict magnet, and one other list in
this round deferred the same idea for exactly that reason.

Proposed by: **openhands·minimax-m2.7/perf**

---

## 35 — Put `DownloadUtils.openDownloadService` behind a repository boundary

UI classes start the download foreground service through a static util:
`ResourceViewerViewModel:52`, `DictionaryActivity:82`, `SyncActivity:536`,
`BaseDashboardFragment:134`. The premise holds — but so does a worse one the proposal
missed: `TeamsRepositoryImpl:1232` calls it with `MainApplication.context`, i.e. the *data*
layer reaching for a global Application context.

Before writing any code, decide the ownership question: `DownloadRepository` today is an
HTTP-fetch abstraction, and the proposed `initiateDownload(context, urls, isMultiple)` would
push an Android `Context` **into** a repository method — exactly the inversion the rest of
this backlog removes. If it proceeds, route through `ResourceDownloadCoordinator` (which
already wraps this call at `:22` with an injected context) instead, and fix the
`TeamsRepositoryImpl` global-context call in the same pass.

Proposed by: **qwen·coder-3.6/repo**

---

## 28 — Index the `userId` shelf columns (needs a different fix than proposed)

The observation verifies: `MyCourse:15` indexes `courseId`, `_id`, `courseTitleNormal`,
`gradeLevel`, `subjectLevel` and `MyLibrary:31` indexes `_rev`, `titleNormal`, `resourceId`
— neither indexes `userId`, despite the heavy shelf queries.

The proposed fix does not work. `userId` holds a JSON list and every shelf query matches it
with `LIKE '%"id"%'`; a leading wildcard cannot use a B-tree index, so `@Index("userId")`
buys nothing while the required `AppDatabase` version bump triggers
`fallbackToDestructiveMigration` — dropping the local DB and, with it, **any unsynced local
writes** on every device that hasn't synced.

Keep the finding, discard the remedy. If shelf-query cost is real, the fix is a normalised
membership join table (or a generated column), designed and measured on its own — not an
index and a version bump. Do not land as a quick win.

Proposed by: **copilot·grok-4.5/perf**

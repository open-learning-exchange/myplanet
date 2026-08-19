# Merged Refactor Backlog

128 tasks merged from 28 agent lists (281 raw). Every task below was verified against
the working tree at `9c54a03`; 30 raw tasks were dropped because their premise did not
hold (66 in total). Sorted by rating (1–100).

Provenance is tagged `harness·model/prompt`, where *prompt* is `perf` (performance quick
wins), `repo` (repository boundaries), or `perf2` (the structured work-order round —
same theme as `perf`, but a template demanding context / files / steps / acceptance /
size budget / out-of-scope per task, plus an open-PR collision check). The eight agents: **claude·opus-5**,
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

## 82 — `JsonUtils`: stop materializing JSON numbers as Strings

`JsonUtils` is on the hottest path in the app — every field of every CouchDB document goes through it. `:99` reads `if (el is JsonNull || el.asString.isEmpty()) 0 else el.asInt`: for a numeric primitive, `asString` allocates a throwaway String purely to test emptiness, then the value is converted a second time. `getFloat` (`:106`) repeats it. Separately `getJsonElement` (`:121`) eagerly allocates a fresh `JsonObject()`/`JsonArray()` as `default` on **every** call even when the field is present, and `getJsonArray` (`:112`) calls `.asJsonArray` on a value already smart-cast to `JsonArray`.

Short-circuit numeric primitives (`el.isJsonPrimitive && el.asJsonPrimitive.isNumber` → `el.asInt`) while keeping the exact string-encoded / empty-string / null fallbacks; move the `default` construction into the not-present branch; drop the redundant `.asJsonArray`.

Files: `utils/JsonUtils.kt`.
Proposed by: **claude·opus-5/perf2**

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

## 80 — `TimeUtils`: memoize the pattern-keyed `DateTimeFormatter`s

`DateTimeFormatter.ofPattern` parses the pattern and builds a printer/parser chain — far more expensive than the formatting itself — and `TimeUtils` does it per call at ten sites (`:96, :99, :115, :119, :142, :206, :208, :211, :223, :224`). This runs per row in list binds (`EnterprisesReportsAdapter:59,70`, `EnterprisesFinancesAdapter:42`, `HealthExaminationAdapter:62`) and per visible day cell in the calendar decorator. The file already hoists six formatters as `by lazy` vals (`:21-45`) — the pattern to follow is right there.

Add a private `ConcurrentHashMap`-backed `formatterFor(pattern, zone)` keyed on **pattern and zone** (the file uses both `utcZone` and `ZoneId.systemDefault()`). **Trap:** `:96, :99, :206, :208, :224` call `ofPattern` with **no** locale argument — give those a distinct key or a no-locale overload rather than silently switching them to `defaultLocale`. `formatDate(date, null)` must keep returning `""` via the existing catch.

Files: `utils/TimeUtils.kt`. Do not touch any caller.
Proposed by: **claude·opus-5/perf2, openhands·kimi-k3/perf2**

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

## 78 — `MapTileUtils.copyAssets`: guard the map-tile copy

`copyAssets` (`:12`) runs on every cold start from `OnboardingActivity:66`, **on the main thread**, and is broken three ways: it unconditionally re-copies both multi-MB `.mbtiles` files; it never creates the destination directory, so `FileOutputStream(outFile)` (`:20`) throws when `osmdroid/` is absent; and **`app/src/main/assets` does not exist in this repo at all**, so `assetManager.open(s)` throws a guaranteed `IOException` into `printStackTrace()` on every single launch.

Skip files not bundled, `mkdirs()` the parent, and skip the copy when the destination already exists non-empty. Keep per-file failure isolation. `MapTileUtilsTest` already mocks `AssetManager`, so the guards are unit-testable.

Files: `utils/MapTileUtils.kt`.
Proposed by: **openhands·kimi-k3/perf2**

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

## 76 — `DownloadService.getNextUrl`: pick one URL in one pass

`:569-572` sorts the entire pending-URL set, filters it, wraps every survivor in a `QueuedUrl`, then scans that list for the max priority — all to return one element. Every `QueuedUrl` built here takes the default `priority = 0` (`:166`), so `getNextPriorityUrl`'s `maxByOrNull { it.priority }` always returns the first element of the sorted list, i.e. the lexicographic minimum. The whole sort and every wrapper allocation is discarded. It is called twice per file inside the download loop (`:139`), making a large batch O(n² log n).

Replace the chain with `urls.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()` and wrap the single winner. Leave the companion `getNextPriorityUrl` and `QueuedUrl` alone — it is separately tested and is the real selector for other callers.

Files: `services/DownloadService.kt`.
Proposed by: **claude·opus-5/perf2**

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
Also proposed independently in the work-order round by **claude·opus-5/perf2** and **openhands·kimi-k3/perf2**, both specifying the `@Transaction` default-method shape and the exact tests to update.

Proposed by: **openhands·kimi-k3/perf2, claude·opus-5/perf2, claude·opus-5/perf, openhands·glm-5.2/perf**

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

Proposed by: **devin·swe-1.7/perf, jules·gemini-3.1-pro/perf, jules·gemini-3.1-pro/perf2, qwen·coder-3.6/perf**

---

## 74 — `UserEntity.addImageUrl`: drop the JSON serialize-and-reparse round-trip

`:162` reads `JsonParser.parseString(jsonDoc["_attachments"].asJsonObject.toString())` — serializing a `JsonObject` to a String and immediately parsing it back into an identical object — then `:165` iterates `entrySet()` only to `break` after the first entry. This runs once per user document during sync.

Read `jsonDoc["_attachments"].asJsonObject` directly, replace the for-with-break with `entrySet().firstOrNull()?.key`, drop the now-unused `JsonParser` import. Attachment selection stays first-key-wins.

Files: `model/UserEntity.kt`.
Proposed by: **claude·opus-5/perf2, openhands·kimi-k3/perf2, openhands·glm-5.2/perf2**

---

## 74 — `RequestsViewModel`: use the existing COUNT query instead of loading every member

`RequestsViewModel:39` calls `teamsRepository.getJoinedMembers(teamId).size` — loading every membership row **plus one user query per member** — to produce an `Int`. `TeamsRepository:143` already exposes `getJoinedMemberCount(teamId)` backed by SQL `COUNT`.

Swap the call. Two lines.

Files: `ui/teams/members/RequestsViewModel.kt`. **Conflict:** two other lists in this round flag this file as owned by an open PR — verify before starting.
Proposed by: **jules·gemini-3.1-pro/perf2, jules·gemini-3.6-flash/perf2**

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

## 72 — `markResourcesAsNotOffline`: one UPDATE instead of read-modify-write

`ResourcesRepositoryImpl:678` loads every matching `MyLibrary` row via `getOfflineByResourceIds`, flips `resourceOffline = false` in memory, and re-writes full rows through `upsertAll` — a full-row SELECT plus a full-row INSERT-OR-REPLACE per resource, to clear one flag. `FreeSpaceWorker:47,52` calls it with every id whose files it deleted.

Add `@Query("UPDATE my_library SET resourceOffline = 0 WHERE resourceId IN (:ids) AND resourceOffline = 1")` and delegate. Leave `getOfflineByResourceIds` in place for other callers.

Files: `data/room/dao/MyLibraryDao.kt`, `repository/ResourcesRepositoryImpl.kt`.
Proposed by: **openhands·kimi-k3/perf2**

---

## 72 — `UploadCoordinator`: index batch reconciliation by `localId`

Four O(n·m) scans per batch of 50: `:69` and `:325` do `succeeded.filter { it !in dbFailed }` (List containment with data-class `equals`), and `:258`/`:441` do `succeeded.find { it.localId == failedResult.localId }` inside `failedResults.mapNotNull`. Every sync upload pays this per batch per config.

Build one `succeeded.associateBy { it.localId }` and one `HashSet` of failed local ids per batch. Preserve `mapNotNull` order, duplicate behaviour, and the ignoring of unknown failed ids.

Files: `services/upload/UploadCoordinator.kt`. **Conflict:** same file as the `UrlUtils` caching task below.
Proposed by: **openhands·kimi-k3/perf2, codex·sol-5.6/perf2**

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
Proposed by: **openhands·kimi-k3/perf, jules·gemini-3.1-pro/perf, jules·gemini-3.6-flash/repo, jules·gemini-3.1-pro/perf2, jules·gemini-3.6-flash/perf2**

---

## 70 — `ProgressRepositoryImpl.submissionMap`: index exams once

`:160` and `:162` call both `examIds.contains(question.examId)` and `examIds.indexOf(question.examId)` inside the nested submission-answer loop — two linear scans per answer, where one exam-ID→index map replaces both and keeps the serialized index keys identical.

Build the map once at the top of `submissionMap`; resolve each question with one lookup; add mistakes only when the lookup succeeds; preserve first-index behaviour if `examIds` contains duplicates.

Files: `repository/ProgressRepositoryImpl.kt`. **Conflict:** same file as the composite-ID task below.
Proposed by: **codex·sol-5.6/perf2**

---

## 70 — `NotificationsViewModel.loadNotifications`: classify types once, not four times

The load walks the full notification list four separate times, calling `it.type.lowercase()` on every element each pass — `:63`, `:69`, `:78`, `:84` — to split `task` from `join_request` rows. Separately `buildGroupedList` reallocates its ordering list on every emission (`:220` `val typeOrder = listOf(...)`), and that function re-runs on every selection toggle and group expand/collapse, not just on load.

Bucket by lowered type in one pass and derive `taskIds` / `taskTitles` / `joinRequestIds` / `joinRequestsWithoutRelatedId` from it, preserving each predicate exactly. Promote `typeOrder` to the existing `companion object` beside `KNOWN_TYPES`. Do not touch `resolveType` — the four filters only ever compare the **raw** type against two literals.

Files: `ui/notifications/NotificationsViewModel.kt`.
Proposed by: **openhands·glm-5.2/perf2, claude·opus-5/perf2**

---

## 70 — `TTSManager`: defer the TextToSpeech engine bind to first use

`:27` constructs `TextToSpeech(context)` inside the `@Singleton`'s `init`, binding to the system TTS service **on the main thread** as soon as Hilt injects it — which happens at `ResourceViewerFragment:106`, i.e. every time any resource viewer opens, including content nobody reads aloud.

Replace the eager init with a private `ensureTts()` called only from `speak()`; make `stop()` and cleanup null-safe no-ops when the engine was never created. Keep `isInitialized` semantics and the `IDLE`/`SPEAKING` transitions. Passive getters must not trigger the bind.

Files: `utils/TTSManager.kt`.
Proposed by: **openhands·kimi-k3/perf2**

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

## 68 — Four Room entities drop `android.text.TextUtils`

In `StepExam.kt`, `Answer.kt`, `HealthExamination.kt` and `TeamTask.kt`, `android.text.TextUtils` is the **only** `android.*` import, so removing it makes each model platform-free and lifts its unit tests out of Robolectric. `TextUtils.isEmpty(s)` is exactly `s.isNullOrEmpty()` for a nullable `CharSequence`.

Swap all uses, delete the import, confirm no other `android.*` import remains. Deliberately excluded: `News.kt` and `Feedback.kt` (same clean case, held for a later round) and `Achievement.kt` (also imports `android.util.LruCache` and `android.widget.EditText`, so it would not become platform-free anyway). No schema change, no version bump.

Files: 4 model files.
Proposed by: **claude·opus-5/perf2**

---

## 68 — `ExamTakingFragment`: index questions before restoring saved answers

`populateCacheFromSavedAnswers` (`:783`) runs `questions?.find { it.id == questionId }` for every saved answer, so restoring a long survey is quadratic in answers × questions — directly visible as resume-screen latency.

Build one question lookup keyed by non-null id before iterating. Preserve handling of missing ids, unknown questions, malformed choice JSON, and every question-type branch. Keep the index local to restoration.

Files: `ui/exam/ExamTakingFragment.kt`. **Conflict:** flagged as open-PR territory by two other lists (#15559, #14650).
Proposed by: **codex·sol-5.6/perf2**

---

## 68 — `SubmissionsRepositoryImpl.getSubmissionDetail`: index answers by question id

`:250-270` scans `submission.answers` with `find` once for every exam question, so large surveys pay quadratic lookup before choice formatting even begins — although `questionId` is the natural association key.

Build one answer lookup keyed by non-null `questionId` after hydration, preserving first-match behaviour for malformed duplicates. Correctness evaluation and formatted-answer fallback stay byte-for-byte equivalent.

Files: `repository/SubmissionsRepositoryImpl.kt`.
Proposed by: **codex·sol-5.6/perf2**

---

## 68 — `SyncManager`: hoist `UrlUtils.getUrl()` / `header` out of the sync loops

`UrlUtils.getUrl()` rebuilds the server base URL from `SharedPreferences` on each call and `UrlUtils.header` recomputes basic auth — and `resourceTransactionSync()` resolves them at the count request (`:278`), inside the resource batch `while` loop (`:305`), and for every `logApiCall` (`:285, :313, :320`), with `getShelvesWithDataBatchOptimized()` repeating the pattern at `:437`. Six call sites inside the longest-running loop in the app.

Capture both into locals before the loop and reuse. Do not touch `UrlUtils.kt` — an open PR owns it.

Files: `services/sync/SyncManager.kt`. **Conflict:** same file as the dead-accumulator task below.
Proposed by: **devin·swe-1.7/perf2**

---

## 68 — `PersonalsRepositoryImpl.updatePersonalAfterSync`: collapse into one statement

`:76` does `findById(id)`, mutates three fields, then `personalDao.update(personal)` — two round-trips and a full-row rewrite per uploaded personal resource, inside the per-item upload loop (`uploadPersonalDocument`, `:97`). `PersonalDao` has no targeted update.

Add `@Query("UPDATE my_personal SET isUploaded = 1, _id = :newId, _rev = :rev WHERE id = :id")` and replace the body. An UPDATE of zero rows matches today's no-op-when-missing behaviour.

Files: `data/room/dao/PersonalDao.kt`, `repository/PersonalsRepositoryImpl.kt`.
Proposed by: **openhands·kimi-k3/perf2**

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

## 66 — `ProgressRepositoryImpl`: stop matching composite IDs with `contains`

`:80` runs `courseIds.firstOrNull { parentId.contains(it) }` inside a `groupBy` over all submissions — O(N·M) against the course list, and a substring match on `examId@courseId` composites that can collide when one course id is a prefix of another.

Split on the known delimiter and resolve through a `HashSet` lookup instead of scanning with `contains`.

Files: `repository/ProgressRepositoryImpl.kt`. **Conflict:** same file as the exam-index task above.
Proposed by: **jules·gemini-3.1-pro/perf2**

---

## 66 — `StorageBreakdownFragment`: index storage categories by extension

`scanStorage()` (`:230`) runs `categories.indexOfFirst { it.extensions.isNotEmpty() && ext in it.extensions }` **inside** the `oleDir.walkTopDown().filter { it.isFile }.forEach` loop — up to four set probes plus a lambda dispatch per file, on a device holding thousands of downloaded resources, for a lookup that is a constant map.

Derive one lazily-built `Map<String, Int>` from `categories` and default to `categories.lastIndex` (the "other" bucket), preserving first-match-wins.

Files: `ui/settings/StorageBreakdownFragment.kt`.
Proposed by: **claude·opus-5/perf2**

---

## 66 — `SyncManager`: delete the dead `batchDocuments` accumulator and the `Pair` wrapper

`:328` allocates `val batchDocuments = JsonArray()` and `:338` appends every valid doc to it — and it is **never read**; the insert path uses `validDocuments.map { it.first }` at `:349`. Every batch of the resources sync allocates and grows a throwaway `JsonArray` holding full document trees. Additionally `validDocuments` stores `Pair(doc, id)` but only `.first` is ever consumed, so the pair wrapper and the trailing `.map { it.first }` are also dead weight.

Delete the accumulator and its `add`; change `validDocuments` to `mutableListOf<JsonObject>()` and pass it straight to `batchInsertResources`. `id` is still needed for the `_design`/blank guard — keep that local.

Files: `services/sync/SyncManager.kt`. **Conflict:** same file as the `UrlUtils` hoist above.
Proposed by: **openhands·glm-5.2/perf2, openhands·kimi-k3/perf2**

---

## 66 — Add an explicit `Locale` to the `%02d` time formatting

Three sites format clock digits without pinning a locale: `TimeUtils:181`,
`EventsDetailFragment:190` (`String.format("%02d:%02d", hour, minute)`) and
`TeamCalendarFragment:202` (`String.format(Locale.getDefault(), "%02d:%02d", …)`). This app
ships Arabic and Nepali translations, both of which carry locale digit sets — so the rendered
time can come out in non-ASCII digits, and anything parsing it back breaks.

Pass `Locale.US` explicitly at all three. (Zero-padded `padStart` interpolation is an equally
correct alternative for pure digits; pick one and apply it consistently.) This is a
correctness fix, not a style preference.

Files: `utils/TimeUtils.kt`, `ui/events/EventsDetailFragment.kt`, `ui/teams/TeamCalendarFragment.kt`.
**Conflict:** two other lists flag `TeamCalendarFragment` as open-PR territory (#15158/#15266) — split that site out if it is still owned.
Proposed by: **jules·gemini-3.1-pro/perf2, jules·gemini-3.6-flash/perf2**

---

## 66 — `VoicesViewModel`: reverse-index the label map

`:110` (in `filterNews`) and `:206` (in `collectLabels`) both run `Constants.LABELS.entries.find { it.value == label }` — a linear scan of the whole label map — for every label of every news item. `filterNews` sits in the `combine` pipeline feeding `filteredNews`, so it re-runs on **every keystroke** of voices search: O(items × labels × LABELS) per character.

Build `Constants.LABELS.entries.associate { it.value to it.key }` once per pass and look up. Keep `VoicesLabelManager.formatLabelValue` as the unknown-label fallback, and do not merge it with the separate display-name→value map already built at `:103`.

Files: `ui/voices/VoicesViewModel.kt`.
Proposed by: **openhands·kimi-k3/perf2, copilot·grok-4.5/perf2**

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

## 64 — `ExamAnswerUtils.checkTextAnswer`: normalize the answer once

`:75-78` lowercases `ans` **inside** the `correctChoices.any` predicate, so the same answer string is re-allocated for every candidate choice.

Compute the locale and the normalized answer once before scanning. Preserve the null-list result, case-insensitive matching, substring behaviour and default-locale semantics; extend the existing tests with multiple candidates so the loop stays covered.

Files: `utils/ExamAnswerUtils.kt`.
Proposed by: **codex·sol-5.6/perf2**

---

## 64 — `ServerConfigUtils`: hoist the pin map and the local-network regex

`getPinForUrl` rebuilds an eleven-entry map of compile-time `BuildConfig` constants on **every** invocation (`:52-64`) then does one lookup and throws the map away; `isLocalNetwork` compiles `Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")` per call (`:72`). Both are pure functions of constants and both are reached from the server dialog on every spinner selection.

Promote both to private object-level vals. Leave `getServerAddresses` alone — it needs a `Context`. Change no PIN or URL value, and do not attempt the committed-secrets problem here; that needs server-side rotation, not a code move.

Files: `utils/ServerConfigUtils.kt`.
Proposed by: **claude·opus-5/perf2**

---

## 64 — `CollectionsFragment.buildTagDataList`: reuse the selection index

`:107` and `:116` scan `selectedItemsList` separately for **every** parent and **every** expanded child, and `:108` additionally calls `currentTagDataList.find` per parent. Expanding a large collection tree multiplies these scans on each rebuild, though only tag ids are needed.

Derive one set of selected non-null tag ids and one parent-row lookup at the top of the function. Preserve expansion state, row order, duplicate-id behaviour and the multi-select flag; keep every index local so no stale state survives a rebuild.

Files: `ui/resources/CollectionsFragment.kt`.
Proposed by: **codex·sol-5.6/perf2**

---

## 64 — `TagsRepositoryImpl`: chunk the `IN`-clause parameters

`:80` (`tagDao.getByIds(allTagIds)`) and `:110` (`tagDao.getByIds(tagIds)`) pass unbounded id lists straight into a Room `IN` query. SQLite's `SQLITE_MAX_VARIABLE_NUMBER` will throw `too many SQL variables` once a user's tag set grows past the limit — a latent crash, not just a slowdown.

Chunk at the repository layer (`.chunked(900).flatMap { tagDao.getByIds(it) }`); leave the DAO alone.

Files: `repository/TagsRepositoryImpl.kt`.
Proposed by: **jules·gemini-3.1-pro/perf2**

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

## 62 — `CourseRatingUtils`: format the average rating with a fixed locale

`:22` and `:46` both read `String.format(Locale.getDefault(), "%.2f", averageRating ?: 0f)`.
`%.2f` under a locale whose decimal separator is a comma renders `4,25` instead of `4.25` —
and this app ships French and Spanish translations (`values-fr`, `values-es`), both of which
do exactly that. Any code or test that parses the rendered string back, or compares it, breaks
on those devices.

Pass `Locale.US` at both sites. Same defect class as the `%02d` task below, different type
specifier and a wider blast radius, because a decimal separator changes the *meaning* of the
string rather than just its digits.

Files: `utils/CourseRatingUtils.kt`.
Proposed by: **jules·gemini-3.6-flash/perf2**

---

## 62 — `ResourcesRepositoryImpl.getFilterFacets`: build the facets in one pass

`:522-528` walks the full library list four separate times to assemble languages, subjects, mediums and levels. Facet rebuilds run whenever the resource filter opens and scale with catalog size.

Allocate four mutable sets and iterate `libraries` once, preserving the same map keys and the blank-filtering / empty-list handling.

Files: `repository/ResourcesRepositoryImpl.kt`.
Proposed by: **copilot·grok-4.5/perf2**

---

## 62 — `UploadCoordinator`: hoist `UrlUtils.getUrl()` out of the batch loops

`uploadBatch()` builds `"${UrlUtils.getUrl()}/${config.endpoint}"` for every item in a batch (`:160, :162, :190`) and `uploadBatchRoom()` repeats it (`:383, :385, :405`) — six `SharedPreferences` reads and six URL string allocations per batch, inside a network loop.

Capture `val baseUrl = UrlUtils.getUrl()` before each `batch.forEach`.

Files: `services/upload/UploadCoordinator.kt`. **Conflict:** same file as the `localId` indexing task above — land one, rebase the other.
Proposed by: **devin·swe-1.7/perf2**

---

## 62 — `ActivitiesFragment.computeMonthlyCounts`: fold into a single pass

`:64-69` chains `.mapNotNull { it.loginTime }` → `.filter { it in startMillis..endMillis }` → `.map { calendar.get(Calendar.MONTH) }` before `groupingBy`/`eachCount`, materializing three throwaway lists. It is fed the device's whole login history and narrowed client-side, so on a long-lived install these copies scale with every login ever recorded — re-allocated on every flow emission.

Use a sequence or a fold into a mutable count map. Keep the shared `Calendar`, the inclusive bounds, the null-`loginTime` skip and the sorted return.

Files: `ui/dashboard/ActivitiesFragment.kt`. **Conflict:** the `getMonth` task below and two repository-round tasks touch this file.
Proposed by: **claude·opus-5/perf2**

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

## 60 — `ActivitiesFragment.getMonth`: cache `DateFormatSymbols().months`

`:125-127` returns `DateFormatSymbols().months[month]`, constructing a fresh `DateFormatSymbols` — which loads the whole locale month-name table — on every call. It is invoked from the chart's `ValueFormatter.getFormattedValue`, i.e. once per visible x-axis label, repeated on every `invalidate()`.

Hoist the array to a `private val`. Keep `getMonth` `internal` with its single-`Int` signature — the existing test calls it directly.

Files: `ui/dashboard/ActivitiesFragment.kt`. **Conflict:** see the task above.
Proposed by: **openhands·glm-5.2/perf2**

---

## 60 — `FeedbackAdapter`: hoist the per-bind colors and drawables

`onBindViewHolder` resolves `ContextCompat.getColor` twice (`:53-54`), calls `ContextCompat.getDrawable(context, R.drawable.bg_primary)` twice (`:56-57`), and builds two fresh `ColorStateList.valueOf(...)` per row.

Cache the two colors as adapter fields. Set each view's `bg_primary` background **once in the ViewHolder constructor**, as two separate drawable instances — per-view `setBackgroundTintList` must stay isolated across recycled holders, so a single shared drawable would bleed tint between rows.

Files: `ui/feedback/FeedbackAdapter.kt`.
Proposed by: **openhands·glm-5.2/perf2, devin·swe-1.7/perf2**

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

`StatsAdapter:20-21` and `ProgressGridAdapter:21-29` already cache exactly this pair — copy them.

Files: `ui/user/UserArrayAdapter.kt`, `ui/sync/ServerAddressAdapter.kt`.
Proposed by: **devin·swe-1.7/perf2, openhands·glm-5.2/perf, openhands·glm-5.2/perf2**

---

## 58 — `Utilities.toast`: stop allocating a `Handler` per background toast

`utils/Utilities.kt:47` allocates a new `Handler(Looper.getMainLooper())` for every toast
posted from a background thread. Toasts fire constantly during sync.

Hoist to one shared `private val mainHandler by lazy { Handler(Looper.getMainLooper()) }`.

Files: `utils/Utilities.kt`. ~2-line diff.
Proposed by: **openhands·kimi-k3/perf**

---

## 58 — `SurveysViewModel`: sort with `CASE_INSENSITIVE_ORDER`

`:141-142` use `sortedBy { it.name?.lowercase(Locale.getDefault()) }` and the descending variant, allocating a fresh String on **every comparison** — O(n log n) throwaway strings per sort.

Swap to `sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })` and its descending twin: one static comparator, zero per-comparison allocation. Existing TITLE_ASC/TITLE_DESC tests pin the ordering.

Files: `ui/surveys/SurveysViewModel.kt`.
Proposed by: **openhands·minimax-m2.7/perf2**

---

## 58 — `MyCourse.saveConcatenatedLinksToPrefs`: drop the second set copy

`:96-100` already builds a mutable set, then `:105` copies it again via `toHashSet()` — and only the copy is used afterwards (`:107` add, `:109` serialize). The original is never read again, so the second full copy is allocated on every save for nothing.

Build the set as a `HashSet` directly in both branches and delete the copy. Use `HashSet`, not `LinkedHashSet`, to preserve today's unspecified-order output.

Files: `model/MyCourse.kt`.
Proposed by: **openhands·glm-5.2/perf2**

---

## 58 — `AndroidDecrypter.bytesToHex`: replace per-byte `String.format`

`:49` calls `String.format("%02x", b)` for **every byte** of encrypted output — a fresh `Formatter` plus format-string parse per byte, on the key/IV generation and encryption path at login. `Sha256Utils:19` does the same with `"%02x".format(it)` for file checksums.

Use a pre-computed lowercase hex `CharArray` and append two chars per byte. Mask with `0xFF` and index by nibble so output is byte-identical.

Files: `utils/AndroidDecrypter.kt`, `utils/Sha256Utils.kt`.
Proposed by: **jules·gemini-3.1-pro/perf2, devin·swe-1.7/perf2, jules·gemini-3.6-flash/perf2**

---

## 58 — `VoicesLabelManager.getLabel`: reverse-index the chip lookup

`:113-119` walks `Constants.LABELS.keys` linearly for every chip, and `showChips` (`:66-72`) calls it once per label while creating a fresh `ChipCloud` per label. Long voice threads pay repeated map scans during bind.

Build a value→display-name reverse map once per `showChips`, falling back to `formatLabelValue` for unknown values. Preserve close-mode delete behaviour and the selected-label resolution in the delete listener.

Files: `services/VoicesLabelManager.kt`.
Proposed by: **copilot·grok-4.5/perf2**

---

## 58 — `RealtimeSyncMixin`: snapshot the watched tables once per subscription

`setupRealtimeSync` (`:27-37`) calls `mixin.getWatchedTables()` and performs **list** membership for every emitted update. Watched tables are per-helper configuration, so rebuilding and rescanning the list on every realtime event is avoidable.

Call it once when the pipeline is created, convert to a `Set`, and use the snapshot in the filter. Keep the snapshot scoped to each setup call so separate helpers cannot share configuration.

Files: `ui/sync/RealtimeSyncMixin.kt`.
Proposed by: **codex·sol-5.6/perf2**

---

## 58 — `ResourcesTagsAdapter`: cache the row colors and hoist the checkbox lookup

`ChildViewHolder.bind` resolves `ContextCompat.getColor` for `multi_select_grey` and `daynight_textColor` on every child bind (`:94-95`), and `createCheckbox()` runs `convertView.findViewById<CheckBox>(R.id.checkbox)` per bind (`:111`) even though View Binding already exposes `binding.checkbox`. `ChatShareTargetAdapter.ChildViewHolder:61-62` already hoists exactly this color pair — copy that.

Move both colors to `private val`s on the holder and pass `binding.checkbox` directly.

Files: `ui/resources/ResourcesTagsAdapter.kt`.
Proposed by: **devin·swe-1.7/perf2, openhands·glm-5.2/perf2**

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

## 56 — `TeamsAdapter.showActionButton`: cache the four action strings and the pending color

`:84, :94, :104, :114` resolve `context.getString(...)` for edit / leave / requested / request-to-join on every bind, and `:107` resolves `ContextCompat.getColor(R.color.pending_request_indicator)`. The teams list scrolls, so all five lookups repeat per visible row.

Hoist to adapter fields initialized in `onCreateViewHolder` from `parent.context`.

Files: `ui/teams/TeamsAdapter.kt`.
Proposed by: **devin·swe-1.7/perf2**

---

## 56 — `NotificationsViewModel.markAsRead`: one traversal instead of two

`:159-179` first searches `currentList.find { it.id == notificationId }`, then either filters the list or maps it as read — a second full traversal for a single-row update triggered on every notification tap.

Fold target detection, prior-unread capture and the list transformation into one pass. Preserve removal under the `"unread"` filter and copy-as-read elsewhere; a missing or already-read notification must not decrement `_unreadCount`; keep `markedIds` as the gate for local mutation.

Files: `ui/notifications/NotificationsViewModel.kt`.
Proposed by: **codex·sol-5.6/perf2**

---

## 56 — `PersonalsAdapter.openResource`: `substringAfterLast` instead of a regex split

`:66` compiles `"\\.".toRegex()` and allocates an intermediate list plus a `TypedArray` just to read a file extension, every time a user opens a personal resource.

Use `path?.substringAfterLast('.', "")?.lowercase()` and match the `when` against it. Keep `java.io.File` — it is still used by the `mp4` branch.

Files: `ui/personals/PersonalsAdapter.kt`.
Proposed by: **devin·swe-1.7/perf2, openhands·minimax-m2.7/perf2**

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

## 54 — `SubmissionViewModel`: build the exam key set directly

`:62-66` materializes a filtered **map** from `examMap` and then retains its `keys` view purely to call `examIds.contains(it.parentId)` per submission. Producing the key set once does strictly less work.

Project matching exam ids straight into a `Set` and filter by membership. Preserve case-insensitive title matching and the empty-query fast path; ordering, grouping and counts stay identical.

Files: `ui/submissions/SubmissionViewModel.kt`.
Proposed by: **codex·sol-5.6/perf2**

---

## 54 — `HealthExaminationActivity`: split blood pressure on a literal, not a regex

`:166` runs `.split("/".toRegex())` inside a `doOnTextChanged` callback — so a `Pattern` is compiled and discarded on **every keystroke** in the blood-pressure field. `"/"` has no regex metacharacters, so `String.split(String)` is the exact equivalent.

One-character change. Keep the trailing `dropLastWhile`/size checks and every validation threshold.

Files: `ui/health/HealthExaminationActivity.kt`.
Proposed by: **openhands·glm-5.2/perf2, openhands·minimax-m2.7/perf2**

---

## 54 — `BaseExamFragment`: `substringBefore` instead of a regex split

`:89` runs `sub?.parentId?.split("@".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.get(0)` — compiling a Regex and building two intermediate collections to take everything before the first `@`.

`substringBefore("@")` is the zero-allocation equivalent **and** returns the original string when the delimiter is absent, so the surrounding `if (contains("@"))` / `else` collapses to one expression.

Files: `base/BaseExamFragment.kt`.
Proposed by: **openhands·minimax-m2.7/perf2**

---

## 54 — `refreshServerList`: strip each server URL once

`:124-147` applies `httpsPrefixRegex` while finding the pinned server (`:129`), filtering duplicates (`:134`) and computing `pinnedIndex` (`:147`) — three passes and three `replace` calls per address on a dialog users open often.

Strip each candidate once into a local structure, then derive the pinned entry, the de-duplicated list and `pinnedIndex` from it. Preserve pin-at-top ordering, the absent-pin fallback, and the `submitList` completion callback. Avoid editing `SyncActivity.kt` itself — an open PR owns it.

Files: `ui/sync/ServerDialogExtensions.kt`.
Proposed by: **copilot·grok-4.5/perf2**

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

## 52 — `SelectionUtils.handleCheck`: drop the redundant membership scan

`:6-9` calls `selectedItems.contains(list[i])` and then `selectedItems.remove(list[i])` — two linear scans when unchecking a present item, where `MutableList.remove` already reports failure for an absent value.

Read `list[i]` once into a local and call `remove` directly. Keep the append branch's duplicate and null handling, and keep the absent-item coverage in the tests.

Files: `utils/SelectionUtils.kt`. Small lists — ship it as filler, not as a win.
Proposed by: **codex·sol-5.6/perf2**

---

## 50 — `UploadRepositoryImpl`: delete two no-op collection copies

`:35` is `examDao.getPendingAdoptedSurveys().map { it } as List<T>` — `.map { it }` allocates a new list of identical elements purely to feed an unchecked cast that works on the DAO list directly. `:105` is `examDao.upsertAll(updated.mapNotNull { it })`, but `updated` is `mutableListOf<StepExam>()` and only non-null exams are ever added, so the filter is a no-op copy on the per-batch mark-uploaded path.

Delete both. Two lines.

Files: `repository/UploadRepositoryImpl.kt`.
Proposed by: **openhands·glm-5.2/perf2**

---

## 50 — `NotificationsRepositoryImpl`: drop the `Set`↔`List` round trips

`:115` and `:391` both do `notificationDao.getByIds(ids.toList()).map { it.id }.toSet()` and then hand a `List` back to the next DAO call — an intermediate `Set` allocated purely to be converted straight back.

Keep the mapped ids as a `List` for the DAO call and return `.toSet()` only at the boundary, so the public `Set` contract is unchanged.

Files: `repository/NotificationsRepositoryImpl.kt`.
Proposed by: **devin·swe-1.7/perf2, openhands·minimax-m2.7/perf2**

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

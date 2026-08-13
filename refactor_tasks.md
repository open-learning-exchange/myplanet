# myPlanet Refactor Tasks — Consolidated & Validated

Extracted from 16 agent task lists (8 agents × 2 prompts), deduplicated, validated against the codebase, and rated 1–100 (evidence quality × impact × risk-adjusted feasibility). Sorted by rating. False-premise tasks (LiveData cleanup, GlobalScope removal, "migrate adapters to ListAdapter" — none of which exist as problems) and vague audit-only tasks were removed.

---

## Delete the vestigial `DatabaseService` and `DatabaseModule` (95/100)

**Files:** `data/DatabaseService.kt`, `di/DatabaseModule.kt` (delete both), `app/src/test/.../data/DatabaseServiceTest.kt` (delete), `SyncManagerTest.kt` / `TransactionSyncManagerTest.kt` (drop unused `databaseService` mocks), `CLAUDE.md` (update DI-module and key-file rows).

`DatabaseService` (`withRoomAsync`, `executeRoomTransactionAsync`, `clearAll`) has zero production call sites — verified: the only `main` references are its own file and the Hilt module providing it. Every repository injects DAOs directly and uses `AppDatabase.withTransaction`. Delete both files and the stale test mocks; removes a dead `@Singleton` and the temptation to reintroduce a god data-access object. Acceptance: `grep -rn "DatabaseService" app/src` returns nothing; unit tests green. *(Proposed independently by 6 of 8 agents.)*

---

## Fix broken `areContentsTheSame` in `ResourcesAdapter` (92/100)

**Files:** `ui/resources/ResourcesAdapter.kt` (~line 47).

The diff callback has no `contentSelector`, so it falls back to `oldItem == newItem`; `ResourceListModel` holds `MyLibrary` (open class, no `equals()`), and repositories rebuild instances per query — so `areContentsTheSame` is permanently `false` and every refresh full-rebinds every visible row (Glide/Markwon work per row). Add an explicit `contentSelector` over the scalar fields (`title`, `description`, `_rev`, `isOffline`, `averageRating`, `timesRated`, `isOpened`, `isLocallyOffline`, `tags`), mirroring `CoursesAdapter`. Audit note from source: this is the only broken callback of the 30 — no sweep needed.

---

## Introduce `DictionaryRepository` — remove the last UI-layer DAO (90/100)

**Files:** new `repository/DictionaryRepository.kt` + `DictionaryRepositoryImpl.kt`, `di/RepositoryModule.kt` (one `@Binds`), `ui/dictionary/DictionaryActivity.kt`.

Verified: `DictionaryActivity` is the only UI class injecting a Room DAO (`DictionaryDao`), calling `insertAll`/`count`/`findByWord` directly. Create a narrow repository with exactly those three operations (plus the seed/import path), inject it into the Activity, and move dispatcher selection inside the impl. While there, move the CPU-heavy Gson parsing/entity mapping of the dictionary asset onto `dispatcherProvider.default` (it currently parses the full JSON array on main). *(Proposed by 6 of 8 agents — the strongest consensus item.)*

---

## Stream file uploads instead of allocating whole files in memory (88/100)

**Files:** `services/UploadManager.kt` (line 368), `services/upload/AchievementUploader.kt` (line 53).

Verified: both call `file.readBytes().toRequestBody(...)` — team image attachments and CV PDFs are fully loaded into memory before upload, spiking peak memory by the attachment size during sync (GC pressure / OOM risk). Replace with a file-backed streaming OkHttp `RequestBody` (`File.asRequestBody(mediaType)`), preserving MIME type, URL encoding, revision handling, and retry behavior. Two small, independently revertible changes.

---

## Fix N+1 DAO lookup in `CoursesRepositoryImpl.flushPendingCourseResources` (86/100)

**Files:** `repository/CoursesRepositoryImpl.kt` (~line 802).

The flush loop calls `myLibraryDao.getById(resourceId)` once per pending resource during course sync. Pre-collect the ids, call the existing `myLibraryDao.getByIds(ids)` once, build a `Map<String, MyLibrary>`, and look up from the map. One `IN (...)` query instead of N single-row queries; the DAO method already exists so no new code surface. Extend `CoursesRepositoryImplTest` with a multi-item batch assertion.

---

## `UserDao`: replace `getAll().filter { }` with narrow queries (85/100)

**Files:** `data/room/dao/UserDao.kt`, `repository/UserRepositoryImpl.kt`; follow-up in `repository/HealthRepositoryImpl.kt`.

Verified: 8 sites in `UserRepositoryImpl` (lines 111, 129, 143, 169, 177, 332, 947, 1176) and 3 in `HealthRepositoryImpl` (155, 161, 206) load the entire `users` table and filter/sort in Kotlin. Add narrow queries: `getByIds` (`WHERE id IN (:ids) OR _id IN (:ids)`), synced-users (`_id IS NOT NULL AND _id != '' AND id NOT LIKE 'guest%'`), a 3-column projection for health sync, and a `LIMIT :limit` pending-sync query. Then convert `HealthRepositoryImpl`'s patient sort/search/records to SQL `ORDER BY` + the new `getByIds` (sequenced after the DAO change — same file). `users` already indexes `_id`/`name`/`planetCode`, so no schema bump. Use `IS` for nullable params per project DAO rules.

---

## Replace `collectNewsAndReplies` recursion with one recursive CTE (85/100)

**Files:** `data/room/dao/NewsDao.kt`, `repository/VoicesRepositoryImpl.kt` (line 277).

Verified: deleting a discussion thread recurses one DAO query per reply node and hydrates full `News` rows just to read `.id`. Replace with a single recursive CTE returning ids only (`WITH RECURSIVE thread(id) AS (SELECT :newsId UNION SELECT news.id FROM news JOIN thread ON news.replyTo = thread.id) SELECT id FROM thread`). `news` already indexes `replyTo`; `UNION` (not `UNION ALL`) also makes it cycle-safe, which the current recursion is not. Test with a 3-level thread and a leaf post.

---

## Cache list adapters in `ResourcesFragment` / `CoursesFragment` `getAdapter()` (85/100)

**Files:** `ui/resources/ResourcesFragment.kt` (~line 146), `ui/courses/CoursesFragment.kt`.

`getAdapter()` unconditionally rebuilds the adapter and re-runs the repository fetch; `BaseRecyclerFragment` calls it from `onViewCreated`, `onRatingChanged`, and `postAddRefresh`, so rating an item or adding to My Library throws away the adapter, its differ, view pool, scroll position, and selection, then re-inflates everything. Mirror `SurveyFragment`'s correct pattern (create once behind a null-check/mutex; later calls only re-fetch and `submitList`). Keep `setListener`/`setOpenedResourceIds`/`setViewMode` applied to the surviving instance. Test in both grid and list mode.

---

## Add `flowOn` + `distinctUntilChanged` to `CoursesRepositoryImpl` Flow chains (85/100)

**Files:** `repository/CoursesRepositoryImpl.kt` (lines ~102, ~112, ~152).

Three Flow-returning functions do real mapping/filtering work in `map { }` with no `flowOn`, so it runs on the collector's dispatcher (Main from a Fragment): `getMyCoursesFlow` filters the whole courses table per emission; `getCourseByCourseIdFlow` maps on the collector; `getCourseDetailModel` hops `withContext(io)` per emission. Append `.flowOn(dispatcherProvider.default)` to the first two, add `.distinctUntilChanged()` to `getMyCoursesFlow` (Room re-emits on any write to `courses` — thousands of identical emissions during sync), and hoist the third's `withContext` into a trailing `.flowOn(dispatcherProvider.io)`. Teams/Voices/Surveys repos already follow this pattern.

---

## Hoist per-call `Regex` compilations to companion constants (85/100)

**Files:** `utils/TTSManager.kt` (~11 patterns recompiled per message), `utils/VersionUtils.kt`, `utils/Utilities.kt`, `model/MyLibrary.kt`.

Each call site recompiles `Regex(...)` / `"...".toRegex()` in hot paths (sync inserts, every TTS utterance, startup version checks). Move each to a `private val` in the companion object / file level. `Regex` is immutable and thread-safe; zero behavior change. Follow-up in the same area: three places independently do `Normalizer.normalize(s, NFD).replace(DIACRITICS_REGEX, "")` (`Utilities`, `MyLibrary`, `SurveysViewModel`, `ChatViewModel`) — extract one `normalizeDiacritics()` helper using the hoisted regex and drop the duplicate constants.

---

## Cancel the leaked app-scoped network collector in `SyncActivity` (85/100)

**Files:** `ui/sync/SyncActivity.kt` (~line 645).

`loginSuccessfully()` launches `isNetworkConnectedFlow.onEach { ... }.launchIn(MainApplication.applicationScope)` — a never-cancelled collector capturing the Activity (`startUpload`, `prefData`, `transactionSyncManager`). Each login re-entry adds another collector and retains the destroyed Activity. Hold the returned `Job` and cancel in `onDestroy()`, or scope to `lifecycleScope` instead of `applicationScope`.

---

## Remove repository dependencies from `VoicesAdapter` (84/100)

**Files:** `ui/voices/VoicesAdapter.kt` (lines 65–66, ~173), construction sites `ui/voices/VoicesFragment.kt`, `ui/voices/ReplyActivity.kt`, `ui/teams/voices/TeamsVoicesFragment.kt`.

`VoicesAdapter` constructor-injects `UserRepository` (and threads `VoicesRepository` into edit actions) — the clearest data-layer leak in `ui/`. Drop the `UserRepository` param: change the leaders input from raw JSON + `parseLeadersJson` to an already-parsed `List<UserEntity>` supplied by the fragments/ViewModel (the adapter already uses the `*Fn` lambda style for other lookups). Leave `voicesRepository`/`VoicesActions.showEditAlert` unwinding for a follow-up — it's a larger change.

---

## Optimize `VoicesAdapter` bind path: hoist JSON parsing, payloads, indexed user lookups (84/100)

**Files:** `ui/voices/VoicesAdapter.kt`.

Three verified inefficiencies on the busiest list: (a) `onBindViewHolder` still parses JSON per bind (`extractSharedTeamName`, image parsing) despite an existing `preParseNews` cache — hoist all parsing to submit time and cache `parsedImages` like `parsedViewIn`/`parsedConversations`; (b) common fine-grained updates (reply count, labels, own-post edit) rerun the full bind pipeline (`resetViews`, `loadImage`, label menus) — extend the existing `PAYLOAD_REPLY_COUNT`/`getChangePayload` path to partial-bind only affected views; (c) on user fetch, the adapter does a full O(n) `currentList.forEachIndexed` scan per fetched user — maintain a `userId → positions` map rebuilt on list commit and notify only those indices.

---

## Payload-only rebinds for `MembersAdapter` and `EnterprisesReportsAdapter` flag flips (84/100)

**Files:** `ui/teams/members/MembersAdapter.kt` (`updateData`, ~line 182), `ui/enterprises/EnterprisesReportsAdapter.kt` (`setNonTeamMember`, ~line 99).

`MembersAdapter.updateData` calls `submitList(list) { notifyItemRangeChanged(0, itemCount) }` when the leader flag flips; `EnterprisesReportsAdapter.setNonTeamMember` does `notifyItemRangeChanged(0, itemCount)` — both fully rebind every visible row (avatar/Glide/formatting work) just to toggle menu/edit-delete visibility. Add a dedicated payload and a payload-aware `onBindViewHolder` that updates only the chrome. Keep `ListAdapter` + `DiffUtils.itemCallback` as the diffing source. Test leader↔member transitions including the logged-in user's row.

---

## Bulk-create survey submissions instead of per-user round trips (83/100)

**Files:** `repository/SubmissionsRepositoryImpl.kt` (`createBulkSurveySubmissions`), `data/room/dao/SubmissionDao.kt`.

The bulk path loops over user IDs calling `getOrCreateSubmission()` each time — repeated DAO reads and writes scaling with users, not data. Add one DAO query fetching existing submissions for the parent + requested users, build only the missing entities in memory, insert via the existing bulk upsert; chunk the `IN` query at the SQLite parameter limit. Preserve IDs, timestamps, status, and duplicate-prevention semantics. Test empty/all-new/mixed/duplicate-input cases asserting one bulk read + one bulk write per chunk.

---

## Cache Room converter `TypeToken` instances (82/100)

**Files:** `data/room/Converters.kt`.

`toStringList()`, `toConversationList()`, and `toAttachmentList()` allocate an anonymous `TypeToken` on every row conversion — small per row but runs across all database reads and sync hydration. Resolve the three generic `Type` values once in the companion object and reuse. No schema, entity, or serialization-format change. Round-trip tests for all three list types plus null/blank inputs.

---

## Replace linear course-ID scans in `ProgressRepositoryImpl.fetchCourseData` (82/100)

**Files:** `repository/ProgressRepositoryImpl.kt`.

Grouping every submission via `courseIds.firstOrNull { submission.parentId?.contains(courseId) == true }` is submissions × courses string scans and allows ambiguous substring matches. Parse the course id once from the established `examId@courseId` parent format, validate against a prebuilt `HashSet`, group in one pass; keep an explicit, tested fallback for malformed/legacy parent IDs. Test normal, malformed, missing, and substring-collision parents.

---

## `LifeFragment.getAdapter()`: cache adapter, fix stacked `ItemTouchHelper` (82/100)

**Files:** `ui/life/LifeFragment.kt` (~line 44).

Same recreate-per-refresh pattern as Courses/Resources, plus a real bug: each call builds and attaches a new `ItemReorderHelper` + `ItemTouchHelper`, so repeated refreshes stack drag-and-drop callbacks on one RecyclerView (one reorder callback fires per accumulated helper per drag). Cache the adapter and attach the touch helper exactly once; verify order persists via `viewModel.updateMyLifeListOrder`.

---

## Remove the unused `ApiInterface` from `UploadToShelfService` (82/100)

**Files:** `services/UploadToShelfService.kt` (line 31), `di/ServiceModule.kt` (`provideUploadToShelfService`).

Verified: `apiInterface` appears only as a constructor parameter — never called. Dead coupling of the network client into a service that already goes through `userSyncRepository`/`healthRepository`. Delete the param, import, and the matching module argument. ~5 lines, zero behavior change.

---

## Memoize `UrlUtils.header` (80/100)

**Files:** `utils/UrlUtils.kt` (~line 29).

Verified: the `header` getter does 2 `SharedPreferences` reads + string concat + `Base64.encodeToString` on every access, with ~92 call sites including `onBindViewHolder` Glide-header paths (per list row) and per-document sync loops. Cache the encoded value keyed on the `(user, pwd)` pair, recompute only on credential change; keep `basicAuthHeader` public/pure and extend `resetForTesting()` to clear the cache. Do NOT cache `hostUrl`/`baseUrl` (alternative-URL failover mutates them mid-session). The invalidation condition is the whole review: login → switch server → sync must still authenticate.

---

## `TeamDao`: narrow queries + stop subscribing to the whole `teams` table (80/100)

**Files:** `data/room/dao/TeamDao.kt`, `repository/TeamsRepositoryImpl.kt`.

(a) Three full-table `teamDao.getAll()`-then-filter sites (`getJoinRequestsInfo` ~399, `getTeamNamesByIds` ~416, `getResourceIds` ~978) → replace with `WHERE _id IN (:ids) OR id IN (:ids)` queries / a `resourceId` projection with the docType predicate in SQL. (b) `getMyTeamDetailsFlow` (~277) subscribes to `teamDao.observeAll()` and re-filters the whole table on every write to `teams` — hundreds of recomputes during one sync. Add `.distinctUntilChanged()` after the existing `flowOn`, or narrow the source to `observeByDocType("membership")`. `teams` already indexes the needed columns — no schema change. Keep the diff surgical; do not start splitting the 1437-line impl here.

---

## `TransactionSyncManager`: checkpoint writes `commit()` → `apply()` (80/100)

**Files:** `services/sync/TransactionSyncManager.kt` (~lines 198, 313, 328).

Each sync batch does `.edit().putInt(checkpointKey, skip).commit()` — a synchronous disk write on the sync coroutine, per page, for large tables. The checkpoint is only consulted on resume-after-crash, never read back in the same loop, so `apply()` is correct and removes blocking I/O from the hot path. Keep the final checkpoint removal after full completion durable.

---

## Centralize ad-hoc `SimpleDateFormat` usage (80/100)

**Files:** `ui/notifications/NotificationsAdapter.kt`, `ui/teams/TeamCalendarFragment.kt`, `ui/enterprises/EnterprisesFinancesFragment.kt`, `services/sync/SyncManager.kt`, `utils/SyncTimeLogger.kt`.

`SimpleDateFormat` is expensive to construct and not thread-safe; these sites instantiate one per call/bind (per-row allocation in adapters, inline `SimpleDateFormat("HH:mm:ss.SSS")` in sync logging). Migrate to the cached `DateTimeFormatter`s `TimeUtils` already holds (thread-safe, immutable), or a `ThreadLocal`-cached instance where the legacy API is required. Mechanical per-file edits, no behavior change. *(Merged from two agents' SimpleDateFormat/ThreadLocal task sets.)*

---

## Replace `toJson → fromJson/parseString` round-trips with `toJsonTree` (80/100)

**Files:** `repository/SyncRepositoryImpl.kt` (`gson.fromJson(gson.toJson(batch), ...)`), `model/MyTeam.kt` (`JsonParser.parseString(gson.toJson(obj))` ×2).

These serialize an object to a String only to immediately re-parse it into a `JsonObject`/`JsonArray`, inside batch sync loops. Replace with `gson.toJsonTree(obj)` — skips the intermediate String entirely. One line per site, no behavior change.

---

## Move text-resource file reading and Markdown setup off the main thread (80/100)

**Files:** `ui/viewer/ResourceViewerFragment.kt` (`setupTextViewer`).

`File.readText()` and Markdown rendering setup run synchronously during view initialization — opening a large text/Markdown resource blocks first render. Read on the injected IO dispatcher, prepare Markwon work off-main where the API permits, apply only the final view update on main; tie the job to `viewLifecycleOwner` and cancel/ignore after view destruction. Dispatcher-controlled test proving I/O is off main and a destroyed view is not touched.

---

## Remove cross-feature lookups from `VoicesRepository` (80/100)

**Files:** `repository/VoicesRepository.kt` + `Impl`, `ui/voices/VoicesViewModel.kt`, `ui/teams/voices/TeamsVoicesViewModel.kt`.

`VoicesRepository` exposes `getUserById` (lazy `UserRepository` proxy) and `getLibraryResource` (direct `MyLibraryDao`) — user and resource data leaking through the voices boundary. Delete both methods; inject `UserRepository`/`ResourcesRepository` into the two ViewModels and route lookups to the owning repositories. Drop `MyLibraryDao` from the voices impl if nothing else needs it. After this, `VoicesRepository` exposes only news/reply/label/discussion responsibilities.

---

## Precompute `HealthExaminationAdapter` display data before binding (80/100)

**Files:** `ui/health/HealthExaminationAdapter.kt` (+ its submit call site).

The adapter parses the `conditions` JSON and builds display strings inside `onBindViewHolder`, competing with frame rendering during scroll. Convert each `HealthExamination` into a small row model with derived display text once per submitted list, submit those through `ListAdapter` + `DiffUtils.itemCallback`. Preserve malformed/empty-condition fallback behavior; no entity/converter/repository changes.

---

## Remove `android.content.Context` from repository interfaces (80/100)

**Files:** `repository/SubmissionsRepository.kt` (`serializeSubmission(..., context, ...)`), `repository/SubmissionsRepositoryImpl.kt`, `repository/TeamsSyncRepository.kt` (`serializeTeamActivities(log, context)`), `repository/TeamsRepositoryImpl.kt`, call sites in `services/upload/UploadConfigs.kt`.

Two repository interfaces take `Context` as a method parameter — the last places a repository's public contract is an Android type, forcing callers to own a `Context` to serialize a model. Inject `@ApplicationContext` into the impls instead (pattern already used by `SyncRepositoryImpl`) and drop the params; remove the `UploadSerializer.WithContext` variant only if it becomes unused. Both methods become unit-testable without Robolectric.

---

## Move `UploadConfigs` DAO access behind repositories (+ `ApkLogRepository`) (80/100)

**Files:** `services/upload/UploadConfigs.kt`; `ProgressRepository`, `ActivitiesRepository`, `VoicesRepository`, `SubmissionsRepository` (+Impls); new `repository/ApkLogRepository{,Impl}.kt`; `di/RepositoryModule.kt`, `di/CoreDependenciesEntryPoint.kt`, `MainApplication.kt`.

`UploadConfigs` still injects 6 raw DAOs (`NewsLogDao`, `CourseProgressDao`, `SearchActivityDao`, `ResourceActivityDao`, `SubmitPhotosDao`, `ApkLogDao`) — the last DAO consumer in `services/`. Two sequenced PRs: (1) add narrow `getPending*`/`mark*Uploaded` APIs on the owning repositories (photos already exist on `SubmissionsRepository`; add a tiny `ApkLogRepository` and move `MainApplication`'s `buildApkLog`/`saveLogToRoom` into it, switching `CoreDependenciesEntryPoint.apkLogDao()` → `apkLogRepository()`); (2) point `UploadConfigs` at the repositories and delete the DAO constructor params. *(Merged from three agents' UploadConfigs/ApkLog task sets.)*

---

## Add Glide `onViewRecycled` clears + lifecycle-safe request managers (78/100)

**Files:** `ui/courses/CoursesAdapter.kt`, `ui/enterprises/EnterprisesReportsAdapter.kt`, `ui/enterprises/EnterprisesFinancesAdapter.kt`; spot-fix `Glide.with(this)` → `Glide.with(viewLifecycleOwner)` in the enterprise fragments and `ResourceViewerFragment`.

These adapters load images with Glide but never clear requests in `onViewRecycled` (Voices/Inline/Chat adapters already do), so recycled holders keep requests/bitmaps alive — jank and memory pressure on fast scroll. Override `onViewRecycled` with `Glide.with(imageView).clear(imageView)` per bound ImageView and null bind-time click listeners. No URL/cache-strategy/layout changes.

---

## Fix hand-rolled `CoroutineScope`s and adapter IO in courses UI (78/100)

**Files:** `ui/courses/CourseFilterController.kt`, `ui/courses/InlineResourceAdapter.kt`, `ui/courses/CoursesFragment.kt` (construction site).

`CourseFilterController` constructs `DefaultDispatcherProvider()` and its own `CoroutineScope(SupervisorJob() + main)` just for a 300 ms debounce — accept the owning fragment's `viewLifecycleOwner.lifecycleScope` (or inject `DispatcherProvider`) so lifecycle cancellation is structural, keeping debounce behavior identical. `InlineResourceAdapter` owns an `adapterScope` and performs four `withContext(io)` blocks of preview IO (PDF render, audio metadata, CSV/text reads) inside an adapter — verify `onDetachedFromRecyclerView` cancels the scope and extract the preview IO into a small injected helper so the adapter stops importing `DispatcherProvider`/`PdfRenderer`/`CSVReader`. *(Merged from four agents' overlapping scope-cleanup tasks.)*

---

## Stop passing `ApiInterface` through `SyncRepository.processShelfParallel` (78/100)

**Files:** `repository/SyncRepository.kt`, `repository/SyncRepositoryImpl.kt`, the single `SyncManager` call site.

The method takes an `ApiInterface` parameter, exposing the network data source through the repository contract. Change to `processShelfParallel(shelfId: String)`, constructor-inject `ApiInterface` in the impl, drop the argument at the call site; remove `SyncManager`'s API dependency only if it becomes unused. No batching/URL/ordering/error changes.

---

## Move `BellDashboardFragment` survey-reminder logic into `BellDashboardViewModel` (78/100)

**Files:** `ui/dashboard/BellDashboardFragment.kt`, `ui/dashboard/BellDashboardViewModel.kt`.

The ViewModel exists (already injects `TeamsRepository` + `ProgressRepository`), yet the fragment field-injects four more repositories and makes ~11 direct data calls (`getLastSurveyDialogShown`, `getUniquePendingSurveys`, `isReminderScheduled`, `scheduleSurveyReminder`, `dueRemindersFlow`, `getSubmissionsByIds`, `isCourseCertified`, …). Absorb `SurveysRepository`/`SubmissionsRepository`/`UserRepository`/`CoursesRepository` into the ViewModel; expose one `StateFlow` for the pending-survey prompt plus suspend functions for the two writes. Splittable: survey-reminder block first, `getUserModel`/`isCourseCertified` second. Fixes a dashboard→surveys/submissions/courses cross-feature leak.

---

## `ResourcesFragment`: move data writes to `ResourcesViewModel`, fix coroutine scoping (78/100)

**Files:** `ui/resources/ResourcesFragment.kt` (~lines 208, 702–729, 809–826).

Two stragglers bypass the ViewModel the fragment already uses: `saveSearchActivity()` and `deleteSelected()` launch `lifecycleScope.launch(io)` and call `resourcesRepository` directly with hand-rolled dispatcher hops the repository already handles — move both into `ResourcesViewModel`, mirroring the adjacent `addToMyList`. Also fix the fragment-scoped `lifecycleScope.launch` wrapping a view-scoped `repeatOnLifecycle` at line 208 → `viewLifecycleOwner.lifecycleScope.launch`, and drop the repository injection if unused after.

---

## Tighten `UserRepository`: evict `parseLeadersJson` and health-profile methods (76/100)

**Files:** `repository/UserRepository.kt` + `Impl`, `model/UserEntity.kt`, `repository/HealthRepository.kt` + `Impl`, `ui/community/LeadersViewModel.kt`, `ui/health/HealthViewModel.kt`.

Two interface-tightening moves on the ~50-method `UserRepository`: (1) `parseLeadersJson(jsonString)` is a pure JSON→model mapper touching no DAO/API — move to a `UserEntity` companion function and update the callers (`LeadersViewModel`, `TeamsRepositoryImpl`), removing one repo-to-repo edge. (2) `getHealthProfile`/`updateUserHealthProfile` are thin wrappers over `HealthRepository` — move the bodies onto `HealthRepositoryImpl`, point `HealthViewModel` at `healthRepository`, and check whether `UserRepositoryImpl` can drop its `healthRepository` injection entirely. Sequence after the VoicesAdapter task (shared caller).

---

## Sweep hand-rolled `repeatOnLifecycle` boilerplate to `collectWhenStarted` (76/100)

**Files:** ~10 fragments with ~24 sites: `RatingsFragment`, `ChatDetailFragment`, `TeamsTasksFragment`, `RequestsFragment`, `MembersFragment`, `SurveyFragment`, `SubmissionDetailFragment`, `NotificationsFragment`, `EnterprisesReportsFragment`, `BaseDashboardFragment`.

`utils/FlowExtensions.kt` already provides `Fragment.collectWhenStarted` (used in ~90 places), but ~30 sites still spell out the 5-line `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { flow.collect { } } }` ceremony — each a spot to forget `viewLifecycleOwner`. Pure mechanical substitution, no behavior change; exclude files claimed by other tasks in the same round to stay conflict-free.

---

## `ProgressRepository`: return a typed `CourseProgress` instead of raw `JsonObject` (76/100)

**Files:** `repository/ProgressRepository.kt` + `Impl`, new `model/CourseProgress.kt`, `ui/courses/CoursesViewModel.kt`, `ui/courses/CoursesAdapter.kt`.

`getCourseProgress` returns `HashMap<String?, JsonObject>` and the adapter calls `JsonUtils.getInt("current"/"max", progress)` — CouchDB wire format parsed in a RecyclerView adapter. Add `data class CourseProgress(current: Int, max: Int)` and a typed map method doing extraction inside the impl; migrate the ViewModel/adapter. Keep the old method if other callers exist and mark for later removal.

---

## Move cross-feature DAO access to owning repositories (75/100)

**Files:** `repository/TeamsRepositoryImpl.kt`, `CoursesRepositoryImpl.kt`, `ResourcesRepositoryImpl.kt`, `VoicesRepositoryImpl.kt`, `UserRepositoryImpl.kt`, `NotificationsRepositoryImpl.kt`, plus owning-repository interfaces (`ResourcesRepository`, `CoursesRepository`, `ActivitiesRepository`, `EventsRepository`, `TeamsRepository`, `VoicesRepository`).

Umbrella for one-PR-per-domain work: `myLibraryDao` is read/written from five repositories → centralize behind `ResourcesRepository`; `TeamsRepositoryImpl` queries `courseDao`/`courseStepDao` directly → add `CoursesRepository.getCoursesWithStepsByIds`; `removedLogDao`/`searchActivityDao`/`resourceActivityDao` calls in Courses/Resources/User impls → `ActivitiesRepository`; `courseProgressDao` writes in `CoursesRepositoryImpl` → `ProgressRepository`; `UserRepositoryImpl.meetupDao` → `EventsRepository` (and remove its unused `offlineActivityDao`); `NotificationsRepositoryImpl`'s `teamTaskDao`/`teamNotificationDao` and team-name lookups → `TeamsRepository`/`VoicesRepository` (removing the `Lazy<TeamsRepository>` if it becomes unused). Sequence the sub-PRs; don't parallelize edits to the same impl. *(Merged from three agents' boundary rounds.)*

---

## Stop `HealthRepositoryImpl` / `SubmissionsRepositoryImpl` injecting `UserDao` (75/100)

**Files:** `repository/HealthRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`, `repository/UserRepository{,Impl}.kt` (at most one narrow added method), their tests.

Both repositories reach into the user table directly (`userDao.getById/getAll/search/upsert`) while `UserRepository` owns users — including a cross-domain write risk where `saveExamination` upserts full user rows from Health. Swap to `UserRepository` (or `Lazy<UserRepository>` for cycles); add a single narrow `searchUsers(query)` only if truly missing; make the Health-side user write an explicit `UserRepository` API. Combines with (and benefits from) the UserDao narrow-query task.

---

## Fix long-lived listener leaks: `OnboardingActivity` and `TakeCourseFragment` (75/100)

**Files:** `ui/onboarding/OnboardingActivity.kt` (~line 110), `ui/courses/TakeCourseFragment.kt` (~lines 127–133).

`OnboardingActivity` registers a `ViewPager.OnPageChangeListener` and `TakeCourseFragment` a `ViewPager2.OnPageChangeCallback` that are never unregistered. Keep references and remove them in `onDestroy`/`onDestroyView`.

---

## `NetworkUtils.isNetworkConnectedFlow`: add the missing `WhileSubscribed` timeout (75/100)

**Files:** `utils/NetworkUtils.kt` (~line 53).

The only `SharingStarted.WhileSubscribed()` in the codebase without a stop timeout (the other 17 use `WhileSubscribed(5000)`), on an app-scoped singleton whose `ConnectivityManager` callback is registered for the whole process — the sharing coroutine tears down and restarts on every fragment transition where the last collector momentarily detaches. Change to `WhileSubscribed(5_000)` (or `Eagerly`). One line; verify airplane-mode toggle still drives `NetworkMonitorWorker`.

---

## `ChatDetailFragment`: collapse seven collectors, remove direct repository access (75/100)

**Files:** `ui/chat/ChatDetailFragment.kt`, `ui/chat/ChatViewModel.kt`.

The fragment runs two `repeatOnLifecycle` blocks with seven separate `launch { collect }` loops and still injects `ChatRepository`/`UserRepository` for send/fetch/AI work alongside an existing `ChatViewModel`. Combine the fields read together into one `chatUiState` via `combine` + `stateIn(WhileSubscribed(5000))`, collect once with `collectWhenStarted`, and move the direct repository calls behind narrowly named ViewModel operations so both repository fields can be deleted. Keep the PR thin — observation consolidation plus the repo moves; no speech-recognizer or layout changes. Same pattern applies as a sibling PR to `SurveyFragment.setupObservers`, which launches six collectors of which three only mutate fragment-side `surveyInfoMap`/`bindingDataMap` the adapter already holds by reference — combine into one `SurveyListUiState`, keeping snackbars on `SharedFlow`. *(Merged from three agents.)*

---

## Move Teams task data work from `TeamsTasksFragment` into `TeamsTasksViewModel` (74/100)

**Files:** `ui/teams/tasks/TeamsTasksFragment.kt`, `ui/teams/tasks/TeamsTasksViewModel.kt`, `repository/TeamsRepository{,Impl}.kt`.

The ViewModel holds only deadline UI state while the fragment calls `teamsRepository`/`userRepository` for load/create/update/assign/complete/delete and name maps. Move the suspend data operations into the ViewModel (StateFlows out, dialogs/pickers/adapter wiring stay in the fragment). Absorb the related interface fix: delete `TeamsRepository.getAssignee(userId)` — a pure passthrough to `userRepository.getUserById` — and call `UserRepository` directly.

---

## Introduce `FeedbackViewModel` / single-step feedback save (74/100)

**Files:** `ui/feedback/FeedbackFragment.kt`, new `ui/feedback/FeedbackViewModel.kt` (or `repository/FeedbackRepository{,Impl}.kt` for the minimal variant).

`FeedbackFragment` injects `FeedbackRepository` + `UserSessionManager`, builds the `Feedback` entity itself (`createFeedback`) and saves in a separate lifecycle coroutine — entity-construction knowledge and a split transaction in the UI. Either add a ViewModel with one `submit(primitives)` operation exposing idle/submitting/saved/failure state and duplicate-tap protection, or minimally collapse to a single `createAndSaveFeedback` suspend on the repository. Fragment keeps validation, toasts, dismissal. *(Merged from three agents.)*

---

## `BaseTeamFragment`: drop redundant dispatcher ceremony (74/100)

**Files:** `base/BaseTeamFragment.kt` (lines ~46, 57, 75–82).

`loadTeamDetails()` wraps two plain suspend DAO-backed reads in `launch(dispatcherProvider.io)` then hops back with `withContext(main)` to set two thread-safe `MutableStateFlow`s — neither hop is needed (Room suspend DAOs run off-main). Remove both, store the load `Job` to prevent double-fetch on rapid re-entry (matching `TeamDetailFragment`'s pattern), and drop the `dispatcherProvider` injection if unused. Worth a slot because this is a base class every team screen inherits and copies.

---

## Run ViewModel CPU-bound sort/filter work off the main dispatcher (72/100)

**Files:** `ui/courses/CoursesViewModel.kt` (`sortByTitle`/`sortByDate`/`sortCourses`), `ui/surveys/SurveysViewModel.kt` (`applyFilterAndSort`, ~lines 123–138).

Both run list sorting/filtering synchronously on the caller (main, via UI clicks) before assigning a `MutableStateFlow`. Wrap in `withContext(dispatcherProvider.default)` with single-flight/cancel-previous semantics, preserving ascending-toggle behavior and emission order. Small unit-test extension if sort order is already covered.

---

## Convert remaining raw `TextWatcher` search boxes to the `textChanges()` Flow (72/100)

**Files:** `ui/teams/TeamFragment.kt` (`setupTextWatcher`), `ui/health/MyHealthFragment.kt` (`setTextWatcher`), reusing `utils/ViewExtensions.kt`.

Both trigger DB queries on every keystroke via raw `TextWatcher` callbacks while the codebase already has a `textChanges()` Flow extension used with debounce elsewhere. Switch to `textChanges() + debounce(...)` collected via `collectWhenStarted`, removing manual watcher bookkeeping and redundant queries.

---

## Stop re-parsing JSON columns inside repository loops (70/100)

**Files:** `repository/VoicesRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`, `repository/FeedbackRepositoryImpl.kt`.

Several methods parse the same JSON string column repeatedly per item in a loop (e.g. `gson.fromJson(news.viewIn, JsonArray::class.java)` in multiple voices methods, `gson.fromJson(feedback.messages, ...)`). Where a method reads a column more than once or parses per-item, parse once into a local `val` and reuse. Pure local change, no API or behavior change.

---

## Make long-running sync/status collectors idempotent with `distinctUntilChanged` (70/100)

**Files:** `ui/health/MyHealthFragment.kt` (`dataUpdateFlow` collector), `ui/sync/SyncActivity.kt` (`syncStatus` collector), `ui/dashboard/DashboardActivity.kt` (sync-status collector).

`collectWhenStarted` re-subscribes on every return to STARTED; these collectors trigger repository reloads/sync re-queries on each emission and each re-subscription. Add `distinctUntilChanged()` (or last-handled-value / in-flight guards) so redundant emissions don't rerun loads. Additive operators, no logic rewrite.

---

## `BaseRecyclerFragment`: `setHasFixedSize(true)` + never swap a live adapter (70/100)

**Files:** `base/BaseRecyclerFragment.kt`.

The base list screen never sets `setHasFixedSize(true)` (item heights are stable), and its refresh paths (`onRatingChanged`/`postAddRefresh`) can reassign `recyclerView.adapter`, dropping the view pool and scroll state even when the same instance would do. Set fixed size where layout is stable and, if the same adapter instance is already attached, call a narrow refresh hook instead of reassigning. Land after the adapter-caching tasks so the base can assume cached adapters.

---

## Small ViewModel extractions: Collections, dashboard Activities, StorageCategoryDetail, ResourceDetail (68/100)

**Files:** `ui/resources/CollectionsFragment.kt`, `ui/dashboard/ActivitiesFragment.kt`, `ui/settings/StorageCategoryDetailFragment.kt`, `ui/resources/ResourceDetailFragment.kt` + one new ViewModel each.

Four screens that inject repositories directly, launch their own data coroutines, and can duplicate work or touch destroyed views on recreation: `CollectionsFragment` (`TagsRepository`), `ActivitiesFragment` (`ActivitiesRepository` + session, long-running Flow subscription), `StorageCategoryDetailFragment` (`ResourcesRepository` load/delete), `ResourceDetailFragment` (resource + user + rating repositories). One narrowly scoped ViewModel per screen exposing compact UI state; presentation (charts, dialogs, formatting, navigation) stays in the fragments. One PR per screen.

---

## Move sync-only `*FromSync(List<JsonObject>)` methods to sync-facing interfaces (68/100)

**Files:** start with the 3 smallest: `repository/EventsRepository.kt`, `FeedbackRepository.kt`, `ChatRepository.kt` (+ Impl declarations), `services/sync/TransactionSyncManager.kt`, `di/RepositoryModule.kt`.

Domain interfaces expose `insertXFromSync(docs: List<JsonObject>)` — CouchDB wire format visible and callable from UI. Split these into narrow per-domain sync-support interfaces implemented by the same impls; `TransactionSyncManager` depends on the sync interface (the `TeamsSyncRepository`/`UserSyncRepository` pattern already exists — copy it).

---

## Parallelize independent loads in `EventsDetailViewModel` (68/100)

**Files:** `ui/events/EventsDetailViewModel.kt` (`loadData`); optionally repeat in `FeedbackDetailViewModel`, `HealthViewModel` (patient detail), `UserProfileViewModel` as separate tiny PRs.

`loadData` awaits user → meetup → members sequentially on one coroutine. Fan out with `coroutineScope { async { } }` and assign StateFlows once results return — cuts time-to-first-paint with no architecture change and establishes the pattern to copy.

---

## Kill no-op diff churn: `refreshWithDiff()` / `submitList(currentList.toList())` (68/100)

**Files:** `ui/courses/CoursesAdapter.kt`, `ui/surveys/SurveysAdapter.kt`, thin call-site edits (`CoursesFragment.onRatingChanged`).

These "refresh" by copying the same list into `submitList`, scheduling DiffUtil work that no-ops after CPU cost. Prefer `notifyItemChanged(index, PAYLOAD)` when the id is known; for full refreshes, require a genuinely new list from ViewModel state. Delete pure copy-resubmit helpers if unused after.

---

## Remove `UploadManager` dependency from `TeamsRepositoryImpl` (68/100)

**Files:** `repository/TeamsRepositoryImpl.kt` (line ~62), `repository/UploadRepository{,Impl}.kt`.

A repository depending on a service (`UploadManager`) inverts the layer direction. Analyze the usage, move the upload-related logic behind `UploadRepository`, and update `TeamsRepositoryImpl` to use it. Keep the diff surgical in the 1437-line impl.

---

## Inject Hilt-provided `Gson` into repositories instead of the `JsonUtils.gson` singleton (66/100)

**Files:** repositories importing `JsonUtils.gson` directly (`VoicesRepositoryImpl`, `SubmissionsRepositoryImpl`, `HealthRepositoryImpl`, `ChatRepositoryImpl`, `ConfigurationsRepositoryImpl`); `di/NetworkModule.kt` already has `provideGson()`.

Hilt-managed classes bypass DI for JSON config, hurting testability. Constructor-inject the provided `Gson`. **Caution (why not higher):** verify the two instances are configured identically (`serializeNulls`, modifiers) before swapping — behavior must stay bit-identical. Leave `Converters`/pure `object` utils on the singleton.

---

## Convert `CoursesPagerAdapter` / `TeamPagerAdapter` manual diffing (65/100)

**Files:** `ui/courses/CoursesPagerAdapter.kt`, `ui/teams/TeamPagerAdapter.kt` (separate PRs, same shape).

The only two files still calling `DiffUtil.calculateDiff` directly (on the main thread, in `FragmentStateAdapter` pagers). Either convert to the house `ListAdapter` + `DiffUtils.itemCallback` + `submitList` pattern, or at minimum compute the diff on a background dispatcher and dispatch updates on main. Lists are small — this is a consistency/pattern win more than a measured one.

---

## Split `TeamsRepository`'s 66-function interface along existing seams (interface-only) (65/100)

**Files:** `repository/TeamsRepository.kt` + 2 new interface files, `ui/enterprises/*ViewModel.kt`, `ui/teams/members/RequestsViewModel.kt`.

ViewModels needing one narrow slice are coupled to the full 66-function surface backed by the repo's largest file. Extract `TeamsFinancesRepository` and `TeamsMembershipRepository` matching call-site clusters, have `TeamsRepository` extend them (impl unchanged — zero-risk mechanically), and migrate the 2–3 narrowest ViewModels to the narrow interfaces. Explicitly not a split of the 1437-line impl.

---

## Introduce ViewModels for the remaining repository-heavy screens (65/100)

**Files (one PR per screen):** `ui/exam/ExamTakingFragment.kt` (4 repositories, 11 call sites, no ViewModel), `ui/courses/CourseStepFragment.kt` (6 repositories, no ViewModel), `ui/courses/TakeCourseFragment.kt` (direct `coursesRepository` calls beside its VM), `ui/surveys/PublicSurveyActivity` (surveys + submissions repos), `ui/health` examination screen.

The largest ViewModel-expansion cluster from the boundary-focused lists. Each screen gets a narrowly scoped ViewModel absorbing its direct repository calls (load paths first), leaving rendering/dialogs in the UI. Rated below the mechanical tasks because each is a bigger-than-average PR; do at most one per review round.

---

## Extract `SyncActivity` provisioning logic out of the UI layer (62/100)

**Files:** `ui/sync/SyncActivity.kt` (~lines 294, 585), `repository/ConfigurationsRepository{,Impl}.kt` or a new `services/sync/SyncProvisioning.kt` — whichever needs fewer new types.

`SyncActivity` injects 6+ repositories plus `UserSessionManager` and additionally owns provisioning work: direct file setup (`File(FileUtils.getOlePath(this))`) and the concatenated-links `openDownloadService` kickoff. Extract that directory-setup/download-kickoff block into one function on the config side so the Activity makes a single call. Deliberately scoped to `SyncActivity` only — leave `LoginActivity` untouched to keep the diff small and avoid clashing with auth work. Rated moderate: real boundary fix on the app's most sensitive flow (login/sync), so it wants its own round with manual sync testing.

---

## `setViewMode` grid↔list toggle: `notifyDataSetChanged` → ranged payload (62/100)

**Files:** `ui/courses/CoursesAdapter.kt` (line 137), `ui/resources/ResourcesAdapter.kt` (line 83) — verified the only two `notifyDataSetChanged()` calls left in `main`.

Proposed by five agents; two others assessed the calls as legitimate (a grid↔list flip changes view types, which forces holder recreation regardless). Middle ground: `notifyItemRangeChanged(0, itemCount)` (optionally with a `PAYLOAD_VIEW_MODE`) preserves animations and stable-ID handling with a two-line change; a payload-only partial bind won't help where the view type actually differs. Include `ResourcesAdapter.setOpenedResourceIds`/`markItemAsOffline` symmetric-difference notifies while there. Low effort, contested payoff.

---

## Route remaining raw `ApiInterface` calls in uploaders through `UploadRepository` (62/100)

**Files:** `repository/UploadRepository{,Impl}.kt`, `services/UploadManager.kt` (lines ~371/457 `uploadResource`), `services/upload/PhotoUploader.kt` (`postDoc`), `services/upload/AchievementUploader.kt` (`putDoc`, `uploadResource`).

Other upload network calls already live on `UploadRepository`; these are the last direct `ApiInterface` uses in the upload path. Add `uploadResource`/`postDoc`/`putDoc` delegating methods and drop the `apiInterface` fields from the three services. Sequence: `UploadManager` first, then the uploaders reuse the added method. Moderately rated: mechanical, but it grows the repository surface with thin passthroughs — coordinate with the streaming-upload task (same lines).

---

## Drop redundant `withContext(io)` around suspend Retrofit calls in `ConfigurationsRepositoryImpl` (60/100)

**Files:** `repository/ConfigurationsRepositoryImpl.kt` (lines ~61, ~206, ~224).

Three `withContext(dispatcherProvider.io)` blocks wrap nothing but a suspend Retrofit call, which already dispatches off-caller — each hop is a dispatch + continuation allocation on the login/sync path. Remove those three; keep the blocks doing real blocking work (reachability composition, `clearAllTables`, `processConfigurationDoc`, file I/O). Rated down as the source itself flags it debatable ("if it draws pushback, drop it"); the value is hygiene precedent. Note: the inverse tasks from another agent (adding `withContext(io)` around Room DAO calls across ~10 repositories) were **removed** as false-premise — Room suspend DAOs and suspend Retrofit already run off the main thread per project conventions.

---

## Replace ViewModel `UserSessionManager`/`SharedPrefManager` identity reads with `UserRepository` (60/100)

**Files:** start with the 3 smallest: `ui/courses/CourseProgressViewModel.kt`, `ui/courses/ProgressViewModel.kt`, `ui/viewer/ResourceViewerViewModel.kt`; `repository/UserRepository{,Impl}.kt` (≤1 new function).

~12 ViewModels inject session/pref managers just to read the current user's id/name; session identity is data and `UserRepository` already exposes `getUserModel()`/`getActiveUserIdSuspending()`. Migrate the smallest three, delete the unused constructor params. Rated moderate: correct direction, but low user-visible payoff and a long tail.

---

*Filtered out (beyond exact duplicates):* generic audit-only tasks with no concrete change ("audit observers", "audit cross-feature imports", "count @Inject fields"); tasks built on false premises verified absent from the codebase (LiveData transformation cleanup, `GlobalScope` removal, `observeForever` cleanup, "migrate adapters off `RecyclerView.Adapter`" — all 48+ list adapters already use `ListAdapter` + `DiffUtils.itemCallback`); blanket `withContext(io)` wrapping of Room DAO calls across ~10 repositories (contradicts Room's main-safety and a higher-rated removal task); removing `@Inject dispatcherProvider` from base fragments purely for MVVM dogma; and the `CommunityServicesFragment` route-parsing-into-`TeamsRepository` pair (marginal value, questionable boundary).

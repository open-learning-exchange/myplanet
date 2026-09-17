### Compute the task badge per team instead of once globally
rating: 90 | provenance: devin2 | roadmap: 1+8
files: repository/NotificationsRepositoryImpl.kt (getTeamNotifications, ~L396-403)
Real correctness bug: `hasTask = tasks.isNotEmpty()` is computed once before the per-team loop, so every `TeamNotificationInfo` reports the same boolean — a team with no tasks shows the badge when any other team has one. Build `tasks.mapNotNull { it.teamId }.toSet()` once, then `hasTask = teamId in taskTeamIds` in the loop. ~6 lines, 1 file.
---

### Hoist the per-attachment SharedPreferences read and dedupe attachments in MyLibrary.insertMyLibrary
rating: 90 | provenance: devin1 | roadmap: 1+7
files: model/MyLibrary.kt (insertMyLibrary, ~L184-270)
`attachmentList.add(realmAttachment)` is unconditional, so the attachments array grows duplicates on every re-sync of the same document; `params.spm.getCouchdbUrl()` is also re-read per attachment inside `entrySet().forEach`. Resolve the base URL once before the loop; skip `add` when the key is already in the existing attachment-name set. ~15 lines, 1 file.
---

### Make SyncTimeLogger's per-key lists thread-safe, drop the write-only detailed-log accumulator, and stop splitting per call
rating: 88 | provenance: claude1 + devin1 (merged) | roadmap: 5+7+8
files: utils/SyncTimeLogger.kt (maps L36-38, getOrPut sites L167/184/195, logDetail L192-201, extractProcessName L331-344)
Two findings, same file: (a) `detailedLogs` accumulates strings at 12 call sites but `generateSummary()` never reads them — delete the field, its clear(), and the map append (keep the verbose `Log.d` branch). (b) `getOrPut { mutableListOf() }.add(...)` on a ConcurrentHashMap holding unsynchronized ArrayLists races under parallel sync coroutines — use `computeIfAbsent` + `Collections.synchronizedList` at all three append sites, and wrap generateSummary's iterations in `synchronized(list)`. Also replace `endpoint.split("/")` with `substringAfterLast('/')`. ~25 lines, 1 file.
---

### Stop re-filtering the resource id list during sync cleanup
rating: 84 | provenance: claude1 | roadmap: 5+7
files: services/sync/SyncManager.kt (resourceTransactionSync, L294/376/411-420)
`newIds` is declared `MutableList<String?>` but only ever receives `savedIds.filter { it.isNotBlank() }`, so `validNewIds` re-walks the whole list and `validNewIds.size == newIds.size` is always true. Type `newIds` as `MutableList<String>`, delete the re-filter, pass `newIds` to `removeDeletedResources`, reduce the guard to `newIds.isNotEmpty()`, fix the logged item count. ~8 lines, 1 file.
---

### Drop the SharedPrefManager parameter from ConfigurationsRepository.checkVersion
rating: 84 | provenance: claude2 | roadmap: 1+4
files: repository/ConfigurationsRepository.kt (L15), repository/ConfigurationsRepositoryImpl.kt (L98 + helpers L487/495), ui/sync/SyncActivity.kt (L233), ui/sync/LoginActivity.kt (L183), services/AutoSyncWorker.kt (L69), test file
The impl already injects `sharedPrefManager` (L49) and uses it in the same function — the parameter is redundant and pins a `services` import into the interface. Remove it from the signature and the three call sites; update three test calls. ~20 lines across 6 files.
---

### Answer "already shared?" with a SQL EXISTS instead of loading every matching news row
rating: 84 | provenance: claude2 | roadmap: 1+7
files: data/room/dao/NewsDao.kt (replace getByNewsId L71-72), repository/VoicesRepositoryImpl.kt (isAlreadyShared L76-79), test
`isAlreadyShared` runs `SELECT *` on all matching news rows (full message/images/viewIn blobs through Converters) just to produce a Boolean. Replace `getByNewsId` (its only caller) with `SELECT EXISTS(... viewIn LIKE :pattern ESCAPE '\')`, escaping `\`, `%`, `_` in the pattern — no schema/index change. ~35 lines, 3 files.
---

### Cache the HTML cover-image directory scan in FileUtils
rating: 82 | provenance: claude1 | roadmap: 7
files: utils/FileUtils.kt (findHtmlCoverImage L142-163)
`findHtmlCoverImage` walks a resource dir to depth 4, stats and lowercases every name, once per bind of every HTML resource row (InlineResourceAdapter L254, ResourcesAdapter L413). Add a small `LruCache` keyed on `absolutePath + lastModified` (negative results cached too), matching the existing `FileExistenceCache`/`PdfThumbnailLoader` precedent. ~25 lines, 1 file.
---

### Take UserRepository out of the viewer activities and put it behind ResourceViewerViewModel
rating: 82 | provenance: claude2 + devin2 (merged) | roadmap: 1+3+4+10
files: ui/viewer/ResourcesExitCoordinator.kt, ui/viewer/ResourceViewerViewModel.kt, ui/viewer/ResourceViewerActivity.kt, ui/viewer/WebViewActivity.kt, test
Both activities field-inject `UserRepository` only to pass to `ResourcesExitCoordinator`, which calls `getUserModel()?.id?.takeIf { it.isNotBlank() }`. Move the lookup into the ViewModel (either a `getActiveUserId()` accessor, or fold it into `shouldShowResourceRatingDialog`/`setRatingPrompted` so they take only `resourceId`), drop the coordinator's `userRepository` param and both activities' injections. ~35 lines, 4-5 files.
---

### Delete the four unused resource-open overloads from ActivitiesRepository
rating: 82 | provenance: claude2 | roadmap: 1+8
files: repository/ActivitiesRepository.kt (L35-38), repository/ActivitiesRepositoryImpl.kt (L174-193)
`getResourceOpenCount(userName)`, `getResourceOpenCount(userName,type)`, `getMostOpenedResource(userName)`, `getMostOpenedResource(userName,type)` have no callers outside the impl (verified — only internal use in `getProfileActivityStats`). Delete the interface declarations, drop `override` in the impl. ~8 lines, 2 files.
---

### Load only id+title for the achievement resource picker
rating: 82 | provenance: devin2 | roadmap: 7+1
files: data/room/dao/MyLibraryDao.kt, repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/user/AchievementViewModel.kt, ui/user/EditAchievementFragment.kt
`showResourceDialog` materializes every `my_library` row as a full ~74-property `MyLibrary` entity to show a checkbox list that reads only `title`; `serializeResource()` is needed only for confirmed rows. Add an `IdTitleProjection` query (no ORDER BY), replace `getAllLibraries` with the projection accessor, refetch selected entities via the existing `getLibraryItemsByIds`. ~60 lines, 5 files.
---

### Reuse the existing file-existence cache for course cover art
rating: 81 | provenance: claude1 | roadmap: 7
files: utils/CoursesItemUtils.kt (bindCover L49-60)
`bindCover` calls `coverFile?.exists()` — a disk stat on the main thread inside `onBindViewHolder` for every course row (TeamCoursesAdapter L45, CoursesAdapter L330/378). Add a private `FileExistenceCache` (5 s TTL, already used by EnterprisesReportsAdapter) and call `coverExistenceCache.exists(coverFile, timeProvider.now())`. ~6 lines, 1 file.
---

### Move the storage-breakdown disk scan out of the ViewModel into ResourcesRepository
rating: 80 | provenance: claude2 | roadmap: 1+3+7
files: repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/settings/StorageBreakdownViewModel.kt, two test files
`StorageBreakdownViewModel.scanStorage` walks the whole `ole` tree — raw file I/O in a ViewModel that duplicates the identical `walkTopDown().filter { it.isFile }` in `getOfflineResourceItems` (ResourcesRepositoryImpl L860). Add `getStorageBreakdown(...)` to the repository (lift the body verbatim into `withContext(dispatcherProvider.io)`), delete `scanStorage`/`ScanResult` from the VM, move the fixture-tree tests. Alternative: kimi's micro-fix keeps it in the VM (see task at rating 55) — pick one.
---

### Fetch one pending submission directly instead of loading a list
rating: 80 | provenance: codex2 | roadmap: 1+7+9
files: data/room/dao/SubmissionDao.kt, repository/SubmissionsRepositoryImpl.kt (L456, L541)
Two call sites run `getSubmissionsByParentId(parentId, userId, "pending").firstOrNull()`, materializing a full list + hydration for one newest row. Reuse the existing bounded `getLatestPendingByUserAndParent` (LIMIT 1, ORDER BY lastUpdateTime DESC) where ordering matches, or add a precise LIMIT 1 query; hydrate only the returned submission. ~25 lines, 2 files.
---

### Share one OkHttp connection pool between the two HTTP clients
rating: 79 | provenance: claude1 | roadmap: 4+7
files: di/NetworkModule.kt (L75-117)
`buildOkHttpClient` constructs a fresh `ConnectionPool` per call, and it is called twice (`@StandardHttpClient`, `@ReachabilityHttpClient`) — every reachability probe opens a new TCP+TLS handshake to the same host. Hoist one shared pool; keep each client's own `Dispatcher`. ~10 lines, 1 file.
---

### Drop redundant case-insensitive matching from chat search (plus bucket-concat and query-parts tidy-ups)
rating: 78 | provenance: claude1 + codex1 + grok2 (3-way merge) | roadmap: 7+9
files: utils/ChatSearch.kt (fullConvoSearch L60/63, searchByTitle L97/99), test
`Utilities.normalizeText` already lowercases both operands (query and candidates), yet all four comparisons pass `ignoreCase = true`, forcing the case-folding regionMatches path on every keystroke. Remove the four `ignoreCase` flags (both sides pre-normalized). Also collapse the four ranked `+` concatenations into one ordered accumulation and skip the intermediate `queryParts` list — keep result ordering identical (title-start → title-contains → body-start → body-contains). ~10 lines, 1 file + tests.
---

### Remove the dead getNotifications from NotificationsRepository and route timestamps through the injected TimeProvider
rating: 78 | provenance: claude2 | roadmap: 1+8
files: repository/NotificationsRepository.kt (L22), repository/NotificationsRepositoryImpl.kt (L140 + Date() at L107/115/128/136/461), test
`getNotifications` on the interface is called only by `getEnrichedNotifications` (L166) — make it private. Separately, five `Date()` constructions bypass the injected `timeProvider`; replace with `Date(timeProvider.now())`. ~15 lines, 3 files.
---

### Replace CourseDao.filterByTitleNormal @RawQuery with a parameterized query
rating: 78 | provenance: kimi2 | roadmap: 1+9
files: data/room/dao/CourseDao.kt (L33-34), repository/CoursesRepositoryImpl.kt (search L273-305), test
`search()` string-builds `SELECT * FROM courses WHERE 1 = 1 AND courseTitleNormal LIKE ? ESCAPE '\'` per token into `SimpleSQLiteQuery`. Add a parameterized `@Query` per token, intersect results by id in Kotlin, keep the `\`/`%`/`_` escape rules and the existing starts-with/contains ranking. ⚠️ PR #15808 touches CourseDao — verify it is merged/closed before starting.
---

### Carve the achievement surface out of UserRepository into a narrow interface
rating: 78 | provenance: devin2 | roadmap: 4+1
files: NEW repository/UserAchievementsRepository.kt, repository/UserRepository.kt, NEW di/UserAchievementsRepositoryModule.kt, services/upload/AchievementUploader.kt
`UserRepository` declares ~57 members; `AchievementUploader` uses only `getAchievementsForUpload` + `markAchievementUploaded`. Apply the existing `TeamsFinancesRepository`-style split: new interface holding the six achievement members, `UserRepository : UserAchievementsRepository`, a `@Provides` binding returning the `UserRepository` impl, and a constructor-type swap in the uploader. Do NOT touch UserRepositoryImpl/RepositoryModule (PR-owned). ~80 lines, 4 files (2 new).
---

### Return a typed progress projection from ProgressRepository instead of parsing JsonArray in the ViewModel
rating: 77 | provenance: claude2 | roadmap: 1+3+9
files: repository/ProgressRepository.kt, repository/ProgressRepositoryImpl.kt, ui/courses/ProgressViewModel.kt, test
`fetchCourseData` returns raw `JsonArray` and `ProgressViewModel.loadCourseData` does the CouchDB-doc mapping itself (`asJsonObject`, `getAsJsonObject("progress")`, a `TypeToken` Gson parse). Add `getCourseProgressRows(userId): List<CoursesProgressRow>` to the repository (target type already exists in model/), moving the mapping down unchanged except skipping malformed rows instead of throwing; delete `gson`/`type` from the VM. Keep `fetchCourseData` (DashboardViewModel still uses it). Supersedes the smaller fix at rating 66.
---

### Delete the getRatingsById wrapper from RatingsRepository
rating: 77 | provenance: claude2 | roadmap: 1
files: repository/RatingsRepository.kt (L7), repository/RatingsRepositoryImpl.kt (L20-23), ui/resources/ResourceDetailFragment.kt (onRatingChanged), test
`getRatingsById` is a null-guard over `getRatingSummary` on the next line — two entry points for one query, with exactly one app caller (ResourceDetailFragment L268; four other call sites already use `getRatingSummary`). Delete it; guard the id at the call site. ~20 lines, 4 files.
---

### ReplyViewModel: absorb the last repository/service calls from ReplyActivity
rating: 77 | provenance: kimi2 | roadmap: 3+10
files: ui/voices/ReplyViewModel.kt, ui/voices/ReplyActivity.kt, NEW test
`ReplyActivity` field-injects `activitiesRepository` (L58), `sharedPrefManager` (L63), `voicesRepository` (L65), `userSessionManager` (L55) and calls `getUserModel()`/`getCommunityLeaders()`/`setRepliedNewsId` directly, though a 17-line `ReplyViewModel` exists. Inject the services into the VM, add thin accessors, remove the Activity injections. New VM test file.
---

### Tighten FeedbackRepository: drop the two internal-only builders and stop passing UserEntity into the list query
rating: 76 | provenance: claude2 | roadmap: 1+3+9
files: repository/FeedbackRepository.kt (L8/24/29), repository/FeedbackRepositoryImpl.kt, ui/feedback/FeedbackListViewModel.kt, two test files
`createFeedback` and `saveFeedback` are public interface methods whose only callers are each other inside `createAndSaveFeedback` — make them private and drop from the interface. `getFeedback` is `suspend` without suspending and takes a whole `UserEntity` to read two fields — change to `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>`. ~45 lines, 5 files.
---

### Put next-step resource prefetch behind ResourcesRepository (or a step-resource coordinator)
rating: 76 | provenance: codex2 + grok1 (merged) | roadmap: 1+3+5+9
files: ui/courses/CoursesStepsViewModel.kt (L63-103), repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, optionally NEW helper class
`CoursesStepsViewModel.loadStep` injects `@ApplicationContext Context` + `ResourceDownloadCoordinator`, resolves filesystem markdown base paths and orchestrates next-step downloads itself. Move the orchestration behind a repository operation (or a concrete `@Inject` coordinator): resolve step resources, filter already-offline, schedule the same priority download; remove `Context`/`UrlUtils`/coordinator from the VM. ~80 lines, 3 files.
---

### Collapse the double file resolution and stop re-statting in InlineResourceAdapter
rating: 76 | provenance: devin1 + grok2 (merged) | roadmap: 7
files: ui/courses/InlineResourceAdapter.kt (updateStatusAndPreview L162-203, getFileCacheKeyIfExist L312-318), test
`updateStatusAndPreview` calls `checkFileExist` (parse URL + stat) then the preview helpers re-run `file.exists()`/`lastModified()`/`length()` on the same resolved file — once per bind per row. Resolve the `File` once, pass it down, reuse one stat set; optionally hold an adapter-scoped `FileExistenceCache` cleared on detach/address change so scroll rebinds skip disk within TTL. ~25 lines, 1 file + tests.
---

### SurveysRepository: return raw counts, not formatted strings
rating: 76 | provenance: kimi2 | roadmap: 1+3+9
files: model/SurveyInfo.kt, repository/SurveysRepositoryImpl.kt (getSurveyInfos ~L287-332), ui/surveys/SurveysViewModel.kt, ui/surveys/SurveysAdapter.kt
`getSurveyInfos()` formats `submissionCount` via `context.resources.getQuantityString(R.plurals...)` and `creationDate` via `TimeUtils.formatDate` inside the repository — `SurveyInfo` carries pre-formatted strings, blocking KMP extraction and locale changes. Change fields to `Int`/`Long`, move plural+date formatting into the adapter/ViewModel. ~4 files.
---

### Move the health-examination upload sequence out of UploadToShelfService into HealthRepository
rating: 75 | provenance: claude2 | roadmap: 1+5
files: repository/HealthRepository.kt (L14-19), repository/HealthRepositoryImpl.kt, services/UploadToShelfService.kt (L70-93), test
Both `uploadHealth` and `uploadSingleUserHealth` hand-roll the same fetch→upload→mark sequence across `HealthRepository`, forcing three intermediate methods to stay public. Add `syncPendingHealthExaminations()` / `syncPendingHealthExaminationsForUser(userId)` to the repository, remove the three intermediates from the interface, reduce the service bodies to one call each (keep listener wrappers + signatures). ~60 lines, 4 files.
---

### Let the feedback repository flow be the only list refresh source
rating: 75 | provenance: codex2 | roadmap: 1+3+8+9
files: ui/feedback/FeedbackListFragment.kt, ui/feedback/FeedbackListViewModel.kt
`FeedbackListViewModel.loadFeedback` already continuously collects `FeedbackRepository.getFeedback`, yet the fragment injects `RealtimeSyncManager`, implements `RealtimeSyncMixin`, and restarts the collector on `"feedback"` table events — redundant work + sync-internals coupling. Remove the mixin/manager/helper; keep the single VM collection; retain `refreshFeedback` only for the explicit submission callback. ~35 lines, 2 files.
---

### Batch successful achievement-upload acknowledgements into one DAO transaction
rating: 75 | provenance: codex2 | roadmap: 1+5+7+9
files: services/upload/AchievementUploader.kt, repository/UserRepository.kt, repository/UserRepositoryImpl.kt, data/room/dao/AchievementDao.kt
`uploadAchievement` calls `markAchievementUploaded` per successful document — one Room write per row in the upload loop. Introduce a small ack value (id + nullable rev), a batch repository method and one DAO transaction; accumulate successes in the loop, flush once after; keep per-doc exception isolation and CV-attachment ordering. ~75 lines, 4 files.
---

### Move per-event accessibility strings in LifeAdapter to bind time
rating: 75 | provenance: devin1 | roadmap: 7
files: ui/life/LifeAdapter.kt (onBindViewHolder L48-83)
`contentDescription = context.getString(...)` is set inside the `setOnTouchListener` (fires per MotionEvent) and the visibility click listener, though both strings depend only on `myLife.title` fixed at bind. Assign once during bind; remove the assignments inside the listeners. ~8 lines, 1 file.
---

### Hoist constant string lookups out of adapter bind paths (3 adapters)
rating: 74 | provenance: claude1 | roadmap: 7
files: ui/teams/members/MembersAdapter.kt (L77/111/121), ui/teams/courses/TeamCoursesAdapter.kt (L62), ui/enterprises/EnterprisesReportsAdapter.kt (L65)
Per-row `context.getString` for strings that never vary: `team_leader`/`no_visit`, `remove`, and `team_financial_report` (teamName is a constructor param). Hoist into `by lazy` fields, matching TeamsSelectionAdapter's existing pattern. ~15 lines, 3 files.
---

### Make challenge progress formatting independent of the dashboard ViewModel
rating: 74 | provenance: codex2 | roadmap: 3+8+9
files: services/ChallengePrompter.kt (L41-42/55-56), ui/dashboard/DashboardViewModel.kt (L155-162)
A service helper reaches into `DashboardViewModel.calculateCommunityProgress`/`calculateIndividualProgress` — a service→UI dependency inversion. Extract the two deterministic calculations into a platform-free function; have both the prompter and the VM call it. ~45 lines, 2 files.
---

### Remove sync-table events from team list view modelling
rating: 74 | provenance: codex2 | roadmap: 1+3+5+9
files: ui/teams/TeamViewModel.kt (getTeamUpdateFlow L41), ui/teams/TeamFragment.kt (collector)
`getTeamUpdateFlow` exposes `RealtimeSyncManager.updatesFor("teams")` directly, duplicating the refresh path that `loadTeams`' `getMyTeamDetailsFlow` collection already provides and leaking a table name into UI. Delete the pass-through; ensure every loadTeams branch collects a repository flow. ~55 lines, 2 files.
---

### Extract public-survey orchestration out of PublicSurveyActivity (payload builder + ViewModel)
rating: 74 | provenance: kimi2 + grok1 (merged) | roadmap: 1+3+10
files: ui/surveys/PublicSurveyActivity.kt (L127-180), NEW repository/PublicSurveyPayloadBuilder.kt or ui/surveys/PublicSurveyViewModel.kt, NEW test(s)
The Activity injects `SurveysRepository` + `SubmissionsRepository`, builds the submission JSON itself (`buildPublicAnswers` L166-180: question fetch, zip by questionId, selectMultiple wire format) and mutates the respondent object (`sanitizeRespondent` L159-164). Move payload building into an injectable `PublicSurveyPayloadBuilder` and the upload orchestration into a `PublicSurveyViewModel` (or a single coordinated extraction); Activity keeps navigation/toasts. Tests for selectMultiple array, select unwrap, age trim/non-numeric.
---

### ProcessUserDataActivity: introduce a ViewModel for the member-upload chain
rating: 74 | provenance: kimi2 | roadmap: 3+5+2
files: ui/sync/ProcessUserDataActivity.kt, NEW ui/sync/ProcessUserDataViewModel.kt, services/UploadToShelfService.kt, NEW test
The Activity field-injects `syncRepository`/`userRepository`/`uploadToShelfService` (L47-57) and runs a three-deep callback chain (`uploadSingleUserData` → `uploadSingleUserHealth` → `fetchAndLogUserSecurityData` → `fetchUserSecurityData`). Add a suspend `uploadSingleUser(userName)` to the service (keep listener APIs for other callers), a `ProcessUserDataViewModel` exposing a `StateFlow<SyncUiState>` for the three `startUpload` branches, and reduce the Activity to dialog/Toast rendering.
---

### Stop passing Context into the submissions PDF exporter
rating: 74 | provenance: kimi2 | roadmap: 1+9
files: repository/SubmissionsRepositoryImpl.kt (L50/62-66/814/885), repository/SubmissionsRepositoryExporter.kt (L45-48/131-132), test
`SubmissionsRepositoryImpl` holds `@ApplicationContext` only to forward it to `exporter.generate*Pdf(context, …)` and to `NetworkUtils.getCustomDeviceName(context)` (which ignores the param). Inject the context into the exporter's constructor, drop the method params, and switch `customDeviceName` to the injected `DeviceNameProvider` (precedent in ResourcesRepositoryImpl/PersonalsRepositoryImpl).
---

### Cheap path helpers and existence+length caching in FileUtils
rating: 74 | provenance: grok2 | roadmap: 7+9
files: utils/FileUtils.kt (getFileExtension L197, checkFileExist L166-169), utils/FileExistenceCache tests
`getFileExtension(address)` allocates a `File` solely for a string suffix; `checkFileExist` runs `getSDPathFromUrl` + `exists()` + `length() > 0` unmemoized, while `FileExistenceCache.exists` caches bare `exists()` only. Implement the extension with string ops; extend the cache (or a flag) so "present" means `exists && length > 0`, TTL-keyed by absolute path; keep `checkFileExist`'s signature. ~3 files.
---

### Resolve the local resource directory once in WebViewActivity instead of per navigation
rating: 74 | provenance: devin1 | roadmap: 7
files: ui/viewer/WebViewActivity.kt (L67/107/174/288-312)
`getLocalResourceDirectory(resourceId)` runs `getExternalFilesDir` + two `canonicalFile` resolutions, and is recomputed in `onCreate`, `setupWebView`, `setupAssetLoader`, and inside `checkUrlSafety` — which fires on every `onPageStarted`/`shouldOverrideUrlLoading`. Cache the resolved `File?` once; keep the canonicalFile traversal guards. ~20 lines, 1 file.
---

### Expose the current user through RequestsUiState
rating: 74 | provenance: devin2 | roadmap: 3+1
files: ui/teams/members/RequestsViewModel.kt, ui/teams/members/RequestsFragment.kt
`RequestsFragment` injects `UserSessionManager` (L26) solely to resolve the current user for `RequestsAdapter.setUser`, while `fetchMembers` already loads `userRepository.getUserModel()` (L130) and keeps only the id. Add `currentUser` to `RequestsUiState`, populate it in `fetchMembers`, drop the fragment's injection + standalone fetch. ~15 lines, 2 files.
---

### Stop threading ConfigurationsRepository into dialog helpers
rating: 74 | provenance: devin2 | roadmap: 1+8
files: utils/DialogUtils.kt (getUpdateDialog ~L188, startDownloadUpdate ~L206), ui/sync/SyncActivity.kt (L763), services/AutoSyncWorker.kt (L93)
Both helpers take a `ConfigurationsRepository` only to call `checkCheckSum(path)` inside a UI util. Change the signatures to accept `checkCheckSum: suspend (String) -> Boolean` and pass `configurationsRepository::checkCheckSum` at the two call sites; drop the import. ~25 lines, 3 files.
---

### Collapse the repeated upload-await blocks in UserDataWorker
rating: 74 | provenance: devin2 | roadmap: 5+8
files: services/UserDataWorker.kt (UPLOAD_TYPE_BULK branch ~L45-105)
The bulk branch repeats `CompletableDeferred` + `OnSuccessListener` + `withTimeoutOrNull(30000L)` six times (user-activities, exam-result, resource, submit-photos, activities, shelf-user-data). Add one `awaitUploadCompletion(start: (onComplete: () -> Unit) -> Unit)` helper and rewrite the six blocks; keep the login branch's `CompletableDeferred<String>` untouched. ~50 lines, 1 file.
---

### Inject ServerReachabilityProvider into BellDashboardViewModel (remove MainApplication dependency)
rating: 74 | provenance: jules2 | roadmap: 4
files: ui/dashboard/BellDashboardViewModel.kt (L137)
Calls static `MainApplication.isServerReachable(serverUrl)`, a global-app dependency blocking offline testing. Inject the existing `ServerReachabilityProvider` (utils/, suspend `isServerReachable`) and replace the call; remove the import. ~10 lines, 1 file.
---

### Inject ServerReachabilityProvider into ChatRepositoryImpl (remove MainApplication dependency)
rating: 74 | provenance: jules2 | roadmap: 4+9
files: repository/ChatRepositoryImpl.kt (L41)
Calls `MainApplication.isServerReachable(url)` — an Android static in the repository layer. Inject `ServerReachabilityProvider`, replace the call. ~10 lines, 1 file.
---

### Compute the storage selection counters in one pass
rating: 73 | provenance: claude1 | roadmap: 3+7+10
files: ui/settings/StorageCategoryViewModel.kt (StorageCategoryUiState L19-27, updates L42-73)
`checkedCount` is a computed getter scanning the whole list; `allChecked` calls it and scans again — two passes per state emission, read on every checkbox tap by the fragment. Make `checkedCount` a stored property derived during the single `map` already performed; keep names/types so the fragment compiles unchanged. ~20 lines, 1 file.
---

### Eliminate re-list-allocation in CalendarViewModel.loadMeetups
rating: 73 | provenance: kimi1 | roadmap: 3+7+9
files: ui/calendar/CalendarViewModel.kt (L33-38)
The `getMyTeamsFlow` collector rebuilds `_teamNames` and re-queries `getMeetupsForTeams` on every emission, even when the team list is referentially unchanged. Add `distinctUntilChanged()` before the assignments and derive `teamIds` once into a local feeding both outputs. ~6 lines, 1 file.
---

### ActivitiesRepositoryImpl: swap the dead Context param for DeviceNameProvider
rating: 73 | provenance: kimi2 | roadmap: 1+9
files: repository/ActivitiesRepositoryImpl.kt, utils/NetworkUtils.kt (read-only), test
`serializeLoginActivities(activity, context)` (L307) forwards `context` to `NetworkUtils.getCustomDeviceName(context)` (L317) — which ignores it entirely (NetworkUtils L243-245 delegates to sharedPrefManager). Inject `DeviceNameProvider` and call it instead; drop the param; remove `Log.e` only if a neighboring-impl logger pattern exists (do not invent one).
---

### Move the examiner user lookup into HealthExaminationState
rating: 73 | provenance: devin2 | roadmap: 3+1
files: ui/health/HealthExaminationViewModel.kt, ui/health/HealthExaminationActivity.kt
The activity reads `userSessionManager.getUserModel()` (L78) for the examiner identity (`isSelfExamination` L251, `createdBy` L256) even though the VM already injects `userRepository`. Add `currentUser` to `HealthExaminationState`, fetch it alongside the patient load, drop the activity's injection. ~20 lines, 2 files.
---

### Resolve the current user inside AddResourceViewModel
rating: 73 | provenance: devin2 | roadmap: 3+1
files: ui/resources/AddResourceViewModel.kt, ui/resources/AddResourceFragment.kt
`AddResourceFragment` injects `UserSessionManager` (L65) only for `getUserModel()` at ~L264 (`showAlert(path, userModel.id, userModel.name, …)`). Add `UserRepository` to the VM + a `currentUser()` accessor; drop the fragment injection. ~10 lines, 2 files.
---

### Replace the regex split and no-op trimIndent in the health examination list
rating: 72 | provenance: claude1 | roadmap: 7+8
files: ui/health/HealthExaminationAdapter.kt (L69, L104, L176)
Fallback display name does `createdBy.split(colonRegex).dropLastWhile{isEmpty}.toTypedArray().getOrNull(1)` — regex + two copies for one substring; replace with `substringAfter(':', "").takeIf { it.isNotBlank() }` and delete `colonRegex`. Also drop `.trimIndent()` on `getString(R.string.two_strings, …)` ("%1$s %2$s" is single-line). Keep the L140 `trimIndent` (multi-line `vitals_format`). ~8 lines, 1 file.
---

### Centralize survey reload state in SurveysViewModel
rating: 72 | provenance: codex2 | roadmap: 1+3+8+9
files: ui/surveys/SurveyFragment.kt (L36-49/90-106), ui/surveys/SurveysViewModel.kt (loadSurveys L78-103)
The fragment injects `RealtimeSyncManager` + `RealtimeSyncHelper` and knows sync storage details while the VM owns team/adoptable selection. Remove the sync mixin; make `loadSurveys` idempotently retain scope + cancel prior jobs; reload after `adoptSurvey` inside the VM; prevent stale loads overwriting newer selections; retain search/sort on reload. ~70 lines, 2 files.
---

### Route health-record refreshes through HealthViewModel
rating: 72 | provenance: codex2 | roadmap: 1+3+8+9
files: ui/health/MyHealthFragment.kt (setupRealtimeSync L255-264), ui/health/HealthViewModel.kt (refreshSelectedPatient)
The fragment consumes every `dataUpdateFlow` event to conditionally call `refreshSelectedPatient`, while the VM already owns `currentPatientId`/cancellation/repository reads. Remove the fragment's sync injection; add an explicit VM refresh intent; coalesce repeated refreshes; preserve selection on transient failure. ~55 lines, 2 files.
---

### Remove MainApplication statics from ResourcesRepositoryImpl
rating: 72 | provenance: grok1 | roadmap: 1+9
files: repository/ResourcesRepositoryImpl.kt (L399 getExternalFilesDir, L504 applicationScope.launch), test
Replace `MainApplication.context.getExternalFilesDir` with the existing `StoragePathResolver` (new constructor param, no module edit needed) and `MainApplication.applicationScope.launch` with suspend + injected `dispatcherProvider` (or `@ApplicationScope CoroutineScope` if a qualifier exists). Surgical removal only.
---

### Push notification display formatting from the ViewModel into repository DTOs
rating: 72 | provenance: grok1 | roadmap: 1+3+10
files: repository/NotificationsRepository.kt, repository/NotificationsRepositoryImpl.kt, ui/notifications/NotificationsViewModel.kt, two test files
`NotificationsViewModel` holds `@ApplicationContext` and applies `context.getString(R.string.*)` in `formatNotification` (~L267-340) to build display strings, though the repo already has `resolveType`. Extend the enriched DTO so the VM maps ready-to-bind fields and drops Context/R.string; keep grouping/selection state in the VM. ~5 files.
---

### Stop SharedPreferences thrash in DownloadService queue bookkeeping
rating: 72 | provenance: grok2 | roadmap: 5+7
files: services/DownloadService.kt (L153-163/177-193), test
After each URL, `getRemainingCount()` re-reads both prefs StringSets and recounts; `cleanupProcessedUrls()` re-reads, mutates, and writes both sets back then recounts again. Mirror the queues in memory on start/enqueue, decrement the cached count, and persist in batches (drain/stop or per-completion without full recount); keep crash-safe rebuild + priority-before-pending ordering. ~150-line cap.
---

### Memoize the re-parsing valueChoicesArray getter in Answer
rating: 72 | provenance: devin1 | roadmap: 1+7
files: model/Answer.kt (getter L23-34)
`valueChoicesArray` rebuilds a `JsonArray` and runs `gson.fromJson` per element on every access — hit once per answer inside `createObject` on the submission-upload path. Add a private `@Ignore` cache keyed on the `valueChoices` list it was built from; keep null/empty behavior identical. ~15 lines, 1 file.
---

### Move the PendingCourseResource buffer out of the singleton CoursesRepositoryImpl
rating: 72 | provenance: kimi2 | roadmap: 1+5+8
files: repository/CoursesRepositoryImpl.kt (L81-95, flush ~L842)
A `@Singleton` holds `Collections.synchronizedList(mutableListOf<PendingCourseResource>())` — process-wide mutable sync state coupling concurrent runs. Convert to a local buffer threaded through the private parse/flush functions; keep `withTransaction` boundaries; add a two-interleaved-batches test.
---

### Introduce DashboardElementViewModel for session and activity calls
rating: 72 | provenance: devin2 | roadmap: 3+1
files: ui/dashboard/DashboardElementActivity.kt (L35/107-125), NEW ui/dashboard/DashboardElementViewModel.kt
The abstract dashboard activity injects `ActivitiesRepository` (L35) and calls `profileDbHandler.getUserModel()`/`logoutAsync()` in `lifecycleScope` blocks — data/session calls with no VM. Create a `@HiltViewModel` exposing `currentUser()`, `recordUserChallengeAction(userId)`, `logout()`; reroute the calls; delete the injection. Do NOT touch SyncActivity. ~70 lines, 2 files.
---

### Remove static context from UserRepositoryImpl
rating: 72 | provenance: jules2 | roadmap: 4+9
files: repository/UserRepositoryImpl.kt (L527-528)
Uses `MainApplication.context` for `VersionUtils.getAndroidId` and `NetworkUtils.getCustomDeviceName` — static app context in the repository layer. Inject `@ApplicationContext context` (or `DeviceNameProvider` for the device name) and replace both uses. ~10 lines, 1 file.
---

### Observe chat history through ChatRepository instead of the sync service
rating: 71 | provenance: codex2 | roadmap: 1+3+5+9
files: ui/chat/ChatViewModel.kt (L49-76/126-136), repository/ChatRepository.kt, repository/ChatRepositoryImpl.kt (getChatHistoryForUser L121-129)
`ChatViewModel` injects `RealtimeSyncManager` and translates the global "chats" table event into a refresh signal; the repository exposes only a snapshot and `ChatDao` has no observable query. Add a repository flow (minimal observable adapter confined to the impl), remove the sync dependency, preserve the active search query on emissions. ~90 lines, 3 files.
---

### Move retry HTTP execution behind RetryRepository
rating: 71 | provenance: codex2 | roadmap: 1+4+5+9
files: services/retry/RetryQueueWorker.kt (L32-41), repository/RetryRepository.kt, repository/RetryRepositoryImpl.kt
The worker injects `ApiInterface` and mixes transport execution with queue state transitions. Define a repository result type (success / retryable / terminal, no Retrofit types), move endpoint dispatch + response classification into the impl, and swap the worker's dependency — keep batching, WorkManager results, and exactly-once state transitions. ~120 lines, 3 files.
---

### Peel Context off DictionaryFileReaderImpl via StoragePathResolver
rating: 71 | provenance: grok1 | roadmap: 1+9
files: repository/DictionaryFileReader.kt (L15-23), test
`@ApplicationContext Context` is held only for `FileUtils.checkFileExist`/`getSDPathFromUrl`; `StoragePathResolver` (already `@Inject`) centralizes those paths. Swap the dependency; keep the `DictionaryFileReader` interface and bind target class name stable — no module edit.
---

### Memoize ResourcesPreviewLoader audio/csv/text work
rating: 71 | provenance: grok2 | roadmap: 7+10
files: utils/ResourcesPreviewLoader.kt (L13-50), test
`getAudioPreview` constructs a `MediaMetadataRetriever` per call; `getCsvPreview`/`getTextPreview` rebuild parser/file reads each call. Add a small LRU (~32-64) keyed by `absolutePath + lastModified + length`; keep `DispatcherProvider.io`, formats (`m:ss`, 5 CSV rows, 8 text lines), and error-swallow behavior.
---

### Stop allocating a Typeface on every group-header bind
rating: 71 | provenance: devin1 | roadmap: 7
files: ui/chat/ChatShareTargetAdapter.kt (GroupViewHolder.bind L51)
`setTypeface(null, Typeface.BOLD)` allocates a `Typeface` via `Typeface.create` per header rebind. Set it once in `init`/first bind using `setTypeface(listTitleTextView.typeface, Typeface.BOLD)`. ~5 lines, 1 file.
---

### Cache per-row date strings in PersonalsAdapter
rating: 70 | provenance: kimi1 | roadmap: 7+10
files: ui/personals/PersonalsAdapter.kt (onBindViewHolder L38)
`getFormattedDate(item.date)` runs per bind for stable rows. Add a `HashMap<Long,String>` and `getOrPut`, mirroring TeamsAdapter's `dateCache` (L62). Key on `item.date ?: 0L` if nullable. ~6 lines, 1 file.
---

### Split PDF I/O out of SubmissionsRepositoryExporter's data assembly
rating: 70 | provenance: grok1 | roadmap: 1+9
files: repository/SubmissionsRepositoryExporter.kt, NEW services/SubmissionPdfWriter.kt, test
The exporter mixes Room DAO assembly with `PdfDocument`/`Paint`/`Canvas`/`Environment`. Keep data assembly in the exporter; move PDF rendering + file write to a concrete `@Inject` writer; keep the public signature so callers compile unchanged. Do not touch SubmissionsRepositoryImpl or DAOs here.
---

### Extract meetup loading from CalendarViewModel's multi-repository fan-out
rating: 70 | provenance: grok1 | roadmap: 3+9
files: ui/calendar/CalendarViewModel.kt (L18-38), NEW ui/calendar/CalendarMeetupsLoader.kt, test
The VM coordinates `UserRepository` + `TeamsRepository` + `EventsRepository` to load meetups — cross-feature fan-out in the VM. Add an `@Inject` loader exposing one suspend method over the three existing interfaces; VM maps results to state only.
---

### In-memory layer for LifeCache
rating: 70 | provenance: grok2 | roadmap: 7
files: repository/LifeCache.kt (read/write L24-35), test
`read` always runs `preferences.getString` + `gson.fromJson`; `getMyLifeForDashboard` hits it on every empty-visible dashboard entry. Add a process-local map checked before prefs, populated on read/write, invalidated on write; return defensive copies/immutable lists. Same file as grok1's package move — sequence accordingly.
---

### Fewer passes in NotificationsRepositoryImpl.getEnrichedNotifications
rating: 70 | provenance: grok2 | roadmap: 1+3+7
files: repository/NotificationsRepositoryImpl.kt (L165-239), test
The enrichment walks payloads repeatedly (`mapNotNull`/`distinct`/`filter` chains for task ids, join ids, titles) and always `async { getUnreadCount }` even when the loaded payloads already carry `isRead`. Single-pass collect; compute unread from memory when semantics match (preserve admin/unread-filter edge cases); keep the parallel `async` batch fetches and the `parsedTaskDates` contract.
---

### Remove static MainApplication.isCollectionSwitchOn in CollectionsFragment
rating: 70 | provenance: jules2 | roadmap: 4+10
files: ui/resources/CollectionsFragment.kt (L162/233), MainApplication.kt (L114)
A global static holds UI state that breaks on process death. Remove `isCollectionSwitchOn` from MainApplication; hold it as a private fragment field (or existing VM if one exists). ~15 lines, 2 files.
---

### Remove static MainApplication.applicationScope from NotificationActionReceiver
rating: 70 | provenance: jules2 | roadmap: 4+5
files: services/NotificationActionReceiver.kt (L31-32)
A BroadcastReceiver launches on the global `applicationScope`, breaking testability/lifecycle isolation. It already calls `goAsync()` — use the pending-result context with a local scope or injected DispatcherProvider; remove the MainApplication import. ~15 lines, 1 file.
---

### Replace printStackTrace() with Log.w in TimeUtils
rating: 69 | provenance: kimi1 | roadmap: 7+8
files: utils/TimeUtils.kt (7 sites: L72/115/135/158/170/201/214)
`e.printStackTrace()` in `formatInstant` runs inside every `getFormattedDate`/`formatDate` row bind — a full stack trace to stderr per row per rebind on parse failure. Add a TAG and replace each with `Log.w(TAG, "<fn> failed", e)`; keep return values identical. ~12 lines, 1 file.
---

### Cache per-row date strings in UserArrayAdapter
rating: 68 | provenance: kimi1 | roadmap: 7+10
files: ui/user/UserArrayAdapter.kt (L62)
`TimeUtils.formatDate(user.joinDate)` per bind; `joinDate` is immutable per row. `HashMap<Long,String>` + `getOrPut` (key `?: 0L` if nullable). ~5 lines, 1 file.
---

### Cache per-row date strings in HealthUsersAdapter.bindDate
rating: 68 | provenance: kimi1 | roadmap: 7+10
files: ui/health/HealthUsersAdapter.kt (bindDate ~L49-50)
`formatDate(user.joinDate)` on every bind including `"joinDate"` payload rebinds. Hoist a `HashMap<Long,String>` to the adapter (survives ViewHolder recycling) + `getOrPut`. ~6 lines, 1 file.
---

### Stop double formatting in FeedbackAdapter.onBindViewHolder
rating: 68 | provenance: kimi1 | roadmap: 7+8
files: ui/feedback/FeedbackAdapter.kt (L53-77)
`getFormattedDate(feedback.openTime)` runs per bind and feeds both the rebuilt `contentDescription` and `tvOpenDate`. Cache by `openTime` in a `HashMap<Long,String>`; reuse for both. ~8 lines, 1 file.
---

### Push the shelf-dispatch map behind an interface-friendly seam in SyncRepositoryImpl
rating: 68 | provenance: kimi2 | roadmap: 3+5+9
files: repository/SyncRepository.kt, repository/SyncRepositoryImpl.kt (L38/44-51/95/181), di/RepositoryModule.kt (⚠️ collision-check PRs #15824/#15825), test
`shelfDispatchMap` hard-codes shelf-name→batch-insert in the repository; `dagger.Lazy<TransactionSyncManager>` and two `Log.e` (its only `android.*` import) sit alongside. Extract a `ShelfBatchInserter` implemented by a small `@Inject` class (bind in RepositoryModule only if unblocked, else construct internally); surface errors instead of `Log.e`; keep `Lazy` if a cycle exists.
---

### Stop static NetworkUtils.getDeviceName() in Personals upload serialization
rating: 68 | provenance: grok1 | roadmap: 1+9
files: repository/PersonalsRepositoryImpl.kt (L110-111), test
`serialize` injects `DeviceNameProvider` for `customDeviceName` but still calls `NetworkUtils.getDeviceName()` for `deviceName`. Route both fields through the provider — note: the provider currently exposes only `getCustomDeviceName()`; extend it (or an equivalent injectable) for the device-name value rather than keeping the static call.
---

### Remove static MainApplication.showDownload in TeamDetailFragment
rating: 68 | provenance: jules2 | roadmap: 4+10
files: ui/teams/TeamDetailFragment.kt (L311/318), MainApplication.kt (L115)
A static coordinates page-switch UI state. Remove from MainApplication; keep as a private fragment property. ~10 lines, 2 files.
---

### Remove the redundant coroutine wrapper in CoursesViewModel.loadCourses
rating: 68 | provenance: codex1 | roadmap: 3+7
files: ui/courses/CoursesViewModel.kt (L110-145), test
`loadCourses` wraps a single `getCourseProgress` call in `coroutineScope { async { … }.await() }` — a pointless child coroutine + Deferred per load that obscures the call. Call the repository directly in the existing IO context; remove the two imports. ~18 lines, 2 files.
---

### Hoist the loop-invariant JSON read in ExamQuestion.insertCorrectChoice
rating: 67 | provenance: devin1 | roadmap: 1+7
files: model/ExamQuestion.kt (else-branch ~L96-108)
`JsonUtils.getString("correctChoice", question)` is re-evaluated every loop iteration though `question` never changes (has/get/null-check each call); `correctChoiceList` also reassigned per match instead of breaking. Read once before the loop; break on first match. ~8 lines, 1 file.
---

### Read each progress JSON field once in ProgressViewModel
rating: 66 | provenance: claude1 | roadmap: 3+7
files: ui/courses/ProgressViewModel.kt (L36-54)
`obj.has("stepMistake")` + `obj.get("stepMistake")` is two hash lookups per value; `getAsJsonObject("progress")` runs twice for current/max — per enrolled course per screen open. Single `get` + local; hoist the nested object. ~12 lines, 1 file. NOTE: superseded if the rating-77 typed-projection task lands.
---

### Strip null team JSON fields without allocating key snapshots
rating: 66 | provenance: codex1 | roadmap: 1+7+9
files: model/MyTeam.kt (serialize L156-224), test
Both branches filter `keySet()` into a `keysToRemove` list then traverse it again — two passes + an allocation on a sync/upload serialization path. Use iterator-based in-place removal in one pass (iterator remove, not mutate-during-iterate); apply to both branches. ~35 lines, 2 files.
---

### Move LifeCache out of the repository package
rating: 66 | provenance: grok1 | roadmap: 1+9
files: repository/LifeCache.kt → data/cache/LifeCache.kt, repository/LifeRepositoryImpl.kt (import), test
A `@Singleton` SharedPreferences cache sitting under `repository/` keeps `android.content.SharedPreferences` in the repository package and blurs boundaries. Relocate file + package; update the single consumer import and test package. No module edit (constructor `@Inject` unchanged).
---

### Single-pass startsWith ranking in ResourcesRepositoryImpl.search
rating: 66 | provenance: grok2 | roadmap: 1+7+9
files: repository/ResourcesRepositoryImpl.kt (search L81-134), test
After SQL filtering, matches are split into `startsWithQuery`/`containsQuery` ArrayLists then concatenated (extra list + two-pass structure). Keep SQL as-is; use one ordered accumulation (`partition` or single `buildList` pass); preserve empty-query branches and `userIdPattern` escaping. ~3 files incl test.
---

### Convert the diagnosis lookup to a Set and hoist per-checkbox styling in HealthExaminationActivity
rating: 66 | provenance: devin1 | roadmap: 7
files: ui/health/HealthExaminationActivity.kt (L199-228)
`mainList = listOf(*arr)` then `contains` in a loop — O(n·m) where a Set gives O(1). `showCheckbox` also re-resolves `getColorStateList`/`getColor`/`dpToPx(8)` ×4 per checkbox — all loop-invariant. Set + hoisted locals. ~12 lines, 1 file.
---

### Decouple TeamPagerAdapter/TeamDetailFragment from MainApplication.listener
rating: 66 | provenance: jules2 | roadmap: 4+6
files: ui/teams/TeamDetailFragment.kt (L236-237/325-339), ui/teams/TeamPagerAdapter.kt, MainApplication.kt (L117-120)
Child-fragment→parent events go through a global `MainApplication.listener` (a `WeakReference<OnTeamPageListener>` — mutable shared state that cross-talks when multiple team pages open). Remove the field; in `btnAddDoc` resolve the active ViewPager fragment and cast to the existing `OnTeamPageListener`. ~20 lines, 3 files.
---

### Reject mismatched multi-select answers before sorting
rating: 65 | provenance: codex1 | roadmap: 7+9
files: utils/ExamAnswerUtils.kt (checkMultipleSelectAnswer L77-85), test
Both answer collections are lowercased + sorted before `==` — when sizes differ equality is impossible, so the allocations/sorts are wasted on a common wrong-answer path. Return false early on size mismatch; keep the sorted-list comparison (preserves duplicate semantics). ~20 lines, 2 files.
---

### Use constant-time course-id membership during adapter removal
rating: 64 | provenance: codex1 | roadmap: 7
files: ui/courses/CoursesAdapter.kt (removeCourses L119-121), test
`currentList.filter { it.courseId !in courseIds }` does a linear `contains` per item — O(n·m) when the removal set is large. Convert non-empty `courseIds` to a `Set` once; keep submitList/onComplete timing. ~25 lines, 2 files.
---

### Drop the redundant distinct pass when ordering notification groups
rating: 63 | provenance: codex1 | roadmap: 3+7
files: ui/notifications/NotificationsViewModel.kt (buildNotificationGroups L201-217), test
`(TYPE_ORDER.filter{grouped.containsKey} + grouped.keys.filter{it !in TYPE_ORDER}).distinct()` — the two inputs are disjoint and each already unique; `distinct()` allocates a set per emission for no effect. Remove it; keep ordering/labels. ~20 lines, 2 files.
---

### Fewer allocations in TTSManager.stripMarkdown
rating: 63 | provenance: grok2 | roadmap: 7+9
files: utils/TTSManager.kt (L88-111), test
`stripMarkdown` chains ~10 `.replace(Regex, …)` — each allocates a new string even with no match. Add a cheap `contains` pre-check fast path for plain text, and combine order-safe replacements; preserve stripping behavior for all constructs. ~2 files.
---

### Compute sync-summary aggregates once per endpoint and model
rating: 62 | provenance: codex1 | roadmap: 5+7
files: utils/SyncTimeLogger.kt (generateSummary L257-294), test
Each endpoint's durations are summed once for sorting and again while rendering; DB groups repeat the pattern plus item-count rescans. Introduce per-key aggregate locals (total, count, items) used for both sort and render; preserve exact output text. ~70 lines, 2 files. Same file as the rating-88 task — sequence them.
---

### Replace printStackTrace() with structured logging in VoicesLabelManager
rating: 62 | provenance: kimi1 | roadmap: 5+8
files: services/VoicesLabelManager.kt (L56, ~L91)
Background label-write failures go to stderr untagged. Add `private const val TAG` in the existing companion and use `Log.w(TAG, "addLabel failed", e)` / `"removeLabel failed"`; keep toast + retry semantics. ~6 lines, 1 file.
---

### Make resource-filter signatures order-independent without sorting
rating: 60 | provenance: codex1 | roadmap: 6+7
files: ui/resources/ResourcesListFilter.kt (Signature L18-20, toSignature L90-92), test
`toSignature` maps tag ids and `.sorted()` them every time inputs are checked — O(t log t) + allocation on an interactive path. Store `searchTagIds` as a `Set` built directly from `searchTags`; keep defensive copies; regression-test that reorder alone doesn't refilter. ⚠️ File is in open PR #17187's scope — re-check before starting.
---

### Replace printStackTrace() in News model accessors
rating: 60 | provenance: kimi1 | roadmap: 1+8+9
files: model/News.kt (L146/163/200/235/242)
Five `printStackTrace()` calls run during feed diffing/sorting. Replace with `Log.w(TAG, "…", e)` — note this adds an `android.util.Log` import to a model class; acceptable trade vs stderr spam, but flag if the KMP core needs model/ platform-free soon.
---

### Give the Teams* split interfaces real consumers (narrow ViewModel injections)
rating: 60 | provenance: kimi2 | roadmap: 2+3
files: pick after per-file grep — e.g. ui/voices/VoicesViewModel.kt, ui/community/CommunityServicesViewModel.kt (verified to inject wide TeamsRepository)
`TeamsRepository` extends `TeamsFinancesRepository`/`TeamsMembersRepository`/`TeamsNotificationsRepository`, but many VMs still inject the wide interface (verified examples: VoicesViewModel L35, CommunityServicesViewModel L18, plus team/chat/dashboard files mostly PR-owned). NOTE: the doc's named examples are stale — EnterprisesFinancesViewModel already injects the narrow interface and EnterprisesViewModel doesn't inject TeamsRepository at all. Re-grep each candidate, narrow constructor types where the used methods map to one parent, verify `@Binds` coverage.
---

### Eliminate the temporary candidate list when choosing the next download
rating: 58 | provenance: codex1 | roadmap: 5+7
files: services/DownloadService.kt (getNextUrl L629-639), test
`getNextUrl` runs `.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()` — a full intermediate list per queue pop. Single-pass minimum selection (or lazy sequence); keep both predicates, lexicographic ordering, `isPriority`. ~20 lines, 2 files.
---

### Memoize the derived messageList getter in Feedback
rating: 58 | provenance: devin1 | roadmap: 1+7
files: model/Feedback.kt (parsedMessages L42-48, messageList L55+)
`messageList` rebuilds `List<FeedbackReply>` objects on every access though `parsedMessages` caches the `JsonArray`. Extend caching to the derived list. NOTE: the doc's "setMessages leaves cachedMessages stale" claim is wrong — `setMessages` assigns `this.messages`, whose setter already nulls `cachedMessages` (verified); only the memoization part applies. ~20 lines, 1 file.
---

### Resolve the lazy teams repository once per upload result batch
rating: 57 | provenance: codex1 | roadmap: 4+5+7
files: services/upload/UploadConfigs.kt (L95-113), test
`teamsSyncRepository.get()` is called inside each result predicate for `TeamTask` and `TeamActivities` `markUploaded` lambdas. Resolve once at the top of each lambda; keep lazy injection (cycle safety) and sequential suspend calls. ~30 lines, 2 files.
---

### Batch the storage scan into a single pass in StorageBreakdownViewModel
rating: 55 | provenance: kimi1 | roadmap: 3+7
files: ui/settings/StorageBreakdownViewModel.kt (scanStorage L86-94)
`walkTopDown().filter { it.isFile }.forEach { … }` adds intermediate sequence stages per file. Iterate the walk directly with an `isFile` guard and compute ext/index/size in one block. Alternative: the rating-80 task moves this scan into the repository — pick one, don't do both.
---

### Replace lowercase-first map lookup in StorageCategories.indexOf
rating: 55 | provenance: jules1 | roadmap: 7
files: ui/settings/StorageCategories.kt (L32)
`indexOf` calls `extension.lowercase()` on every lookup, allocating per call. Try `extensionToIndex[extension] ?: extensionToIndex[extension.lowercase()] ?: OTHER_INDEX`. ~2 lines, 1 file.
---

### Optimize the areAllSelected count in CoursesAdapter
rating: 52 | provenance: jules1 | roadmap: 7
files: ui/courses/CoursesAdapter.kt (L164)
`currentList.count { isMyCourseLib || !it.isMyCourse }` iterates per selection; when `isMyCourseLib` is true the count is just `currentList.size`. Short-circuit: `if (isMyCourseLib) currentList.size else currentList.count { !it.isMyCourse }`. ~3 lines, 1 file.
---

### Avoid sequence overhead in the valid log file check
rating: 50 | provenance: jules1 | roadmap: 7
files: utils/CrashLogStore.kt (L43-44)
`pendingFiles.asSequence().filter{isValidLogFile(it)}.take(MAX).count()` instantiates a sequence for an early-exit count. Iterate and count until `MAX_PENDING_FILES`, preserving the `>= MAX` guard below. ~3 lines, 1 file.
---

### Remove the ArrayList pre-allocation overhead in DownloadUtils
rating: 50 | provenance: jules1 | roadmap: 7
files: utils/DownloadUtils.kt (downloadAllFiles L137-139)
`dbMyLibrary.mapTo(ArrayList()) { … }` — plain `map` suffices, or pre-size `ArrayList(dbMyLibrary.size)`. ~2 lines, 1 file.
---

### Replace mutable-list target in TeamCalendarFragment date mapping
rating: 50 | provenance: jules1 | roadmap: 7
files: ui/teams/TeamCalendarFragment.kt (L215)
`meetups.mapTo(mutableListOf()) { … }` — plain `meetups.map` feeds `eventDates.addAll` identically. ~2 lines, 1 file.
---

### Cache filteredList.size in ResourcesFragment.applyFiltersAndUpdateUI
rating: 48 | provenance: jules1 | roadmap: 7
files: ui/resources/ResourcesFragment.kt (~L412)
`filteredList.size` read repeatedly in sequence (`checkList`, `showNoData`, count text). Extract a `val listSize`. ~4 lines, 1 file.
---

### Remove the redundant size/isEmpty reads in BaseDashboardFragment
rating: 48 | provenance: jules1 | roadmap: 7
files: base/BaseDashboardFragment.kt (L189-192)
`filteredCourses.size` for `setCountText` then `isEmpty()` right after — cache `val count = filteredCourses.size` and reuse for both checks. ~4 lines, 1 file.
---

### Extract the member size call in MembersFragment
rating: 48 | provenance: jules1 | roadmap: 7
files: ui/teams/members/MembersFragment.kt (L86/96)
`state.members.size` evaluated twice in the same render (header text + `showNoData`). Extract a local. ~3 lines, 1 file.
---

### ChatHistoryAdapter DiffUtil and bind micro-costs
rating: 45 | provenance: grok2 | roadmap: 7+8
files: ui/chat/ChatHistoryAdapter.kt (L44-49/110-130), test
`areContentsTheSame` compares only `conversations?.firstOrNull()?.query` — verify it doesn't miss title/`_rev`/`lastUsed` changes (a correctness gap more than a perf one). On bind, read first-query once per side; only copy `conversations` inside the click (snapshot at click time); check `PAYLOAD_CHAT_SHARED` still avoids full rebind. Premise is weak — scope to whatever the audit actually finds.
---

### Change filteredExistingUsers mapTo(HashSet) to mapNotNullTo
rating: 42 | provenance: jules1 | roadmap: 7
files: repository/TeamsRepositoryImpl.kt (L937, L970)
`mapTo(HashSet()) { it.name }` twice — claimed to "include nulls", though `it.name` is effectively non-null; `mapNotNullTo` is equivalent cleanup, not a bug fix. ⚠️ TeamsRepositoryImpl is heavily PR-owned — collision risk. ~2 lines, 1 file.
---

### Optimize the passed-steps collection in ProgressRepositoryImpl
rating: 40 | provenance: jules1 | roadmap: 7
files: repository/ProgressRepositoryImpl.kt (L194-195)
`mapNotNullTo(HashSet()) { if (it.passed) it.stepNum else null }.size` — the HashSet is required for unique-step counting, so `mapNotNull + distinct + size` does the same work; claimed savings are illusory. At most a clarity refactor. ~2 lines, 1 file.

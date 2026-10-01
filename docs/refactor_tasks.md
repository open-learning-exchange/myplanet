### Chunk chat typing animation and decouple scroll

Rating: 87/100 — proposed by 3 agent(s): claude, grok, kimi; raw proposals: perf_claude#9, perf_kimi#1, perf_kimi#2, perf_grok#1

ChatDetailFragment.kt:~271-282 builds `response.substring(0, currentIndex+1)` per character (~10ms ticks, O(n²) allocs) and VoicesAdapterHelper.kt:13-31 duplicates it; ChatAdapter.kt:~76-79 calls scrollToPosition per emitted char. Emit fixed-size chunks (or reveal per word/line) and scroll only when the user is pinned to bottom; cancel-in-place stays a Job cancel.

---

### Remove ChatSearch wrapper copies; stream results with injected dispatcher

Rating: 84/100 — proposed by 4 agent(s): claude, codex, gemini, grok; raw proposals: perf_codex#2, perf_gemini#10, perf_grok#8, data_codex#4, data_claude#6, data_grok#10

utils/ChatSearch.kt:25 takes Dispatchers.Default directly and :39-45/:77-85 materialize ConvoChat/TitleChat wrapper lists plus normalized strings per query. Score on the fly without per-chat wrapper objects, return early on blank query, replace hardcoded Dispatchers with DispatcherProvider. (data_claude's normalize-once Index variant folded in; kimi also proposed the dispatcher fix via its repo #4b alternate — noted, not double-scored since it shares task slot #4.)

---

### Chunk Room IN queries that bypass the 900-variable wrappers

Rating: 84/100 — proposed by 2 agent(s): claude, devin; raw proposals: repo_claude#4, data_devin#1

QuestionDao.getByIds (:10), UserDao.getUsersByAnyIds (:13) and getGuestUsersByNames (:46), MyLibraryDao.getByIds (:35), getByResourceIds (:38), getByResourceIdsNotUserPattern (:155-158) all issue raw `IN (:list)` queries — the codebase's own chunked-wrapper pattern (distinct().chunked(900)) must be applied. Real crash risk past ~999 args. repo_claude also flags dropping 3 dead DAO methods; verify callers per method before deleting.

---

### Enterprises fragments: move readBytesFromUri + getDisplayName off the main thread

Rating: 84/100 — proposed by 1 agent(s): devin; raw proposals: perf_devin#1

EnterprisesFinancesFragment.kt:305-306 and EnterprisesReportsFragment.kt:186-187,:269-270 run ContentResolver queries + full file reads inside setPositiveButton click handlers — a multi-MB image read on the UI thread. lifecycleScope is already imported; shift to IO before the dialog completes.

---

### ANRWatchdog: volatile state + fix duration math + poll at timeout

Rating: 83/100 — proposed by 2 agent(s): devin, gemini; raw proposals: perf_gemini#6, data_devin#2

utils/ANRWatchdog.kt: isWatching/tick (:25-26) are written on the monitor thread and read cross-thread without @Volatile; duration = currentTime - lastTick is computed BEFORE delay(timeout/2), so reported ANR duration is stale by up to a full poll; delay(timeout/2) doubles post-ANR latency and the post-callback delay(timeout) can double-report. Fix all three together.

---

### Add distinct() before chunking in DAO bulk wrappers

Rating: 80/100 — proposed by 1 agent(s): codex; raw proposals: perf_codex#4, perf_codex#6, perf_codex#7, perf_codex#8, perf_codex#9

Every verified chunked wrapper iterates raw input: NotificationDao.getByIds/getIdsByIds/markAsRead/deleteByIds (:42-64,121-124 also used by markAllUnreadAsRead), AnswerDao.getBySubmissionIds/deleteBySubmissionIds, RemovedLogDao.deleteByTypeUserAndDocsChunked (also missing the isEmpty fast-path its siblings have), MyLibraryDao.markAsNotOfflineByResourceIds + getResourceTitlesByResourceIds (siblings at :61/:72/:89 already dedupe), ApkLogDao.markUploadedBatch (distinct on extracted ids).

---

### Group calendar meetups by LocalDate once in the ViewModel

Rating: 79/100 — proposed by 4 agent(s): codex, gemini, grok, kimi; raw proposals: perf_kimi#3, repo_gemini#7, data_codex#1, data_grok#9

CalendarFragment.kt:48-52 allocates a Calendar per meetup, :56-68 re-filters the whole list per day-tap, and :71-86 builds a DateTimeFormatter per dialog. Expose Map<LocalDate,List<Meetup>> (or a getMeetupsForDate query) from CalendarViewModel; hoist formatter + Calendar reuse.

---

### Strip android.util.Log out of the repository/data layer

Rating: 79/100 — proposed by 3 agent(s): codex, gemini, grok; raw proposals: repo_codex#4, repo_codex#5, repo_codex#6, repo_codex#7, data_gemini#6, data_gemini#7, data_gemini#8, repo_grok#9, data_grok#4, data_grok#5, data_grok#6, repo_gemini#8

Verified imports+call-sites: DictionaryRepositoryImpl.kt:57 Log.e, RetryRepositoryImpl.kt:3 import with ~8 Log.* calls, DiagnosticsRepositoryImpl.kt:78,99 Log.w, SyncRepositoryImpl.kt:105,189 Log.e, GsonUtils.kt:79-92 Log (isLoggable-gated), DictionaryFileReader context. Route through SyncTimeLogger/diagnostics where the signal matters, else delete. SyncRepositoryImpl error-logging -> SyncTimeLogger (repo_codex#6) folds in here.

---

### GsonUtils: one get() per field, inline getPrimitive

Rating: 79/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#1

utils/GsonUtils.kt:106-107 fieldElement does has() then get() (double hashmap lookup on every parsed field); getPrimitive (:109) is a non-inline triple-lambda helper; getJsonElement (:185-190) does has()+get() too. Single-get via entrySet contains or asMap, mark the helpers inline.

---

### ConfigurationsRepositoryImpl.getMinApk: take first success instead of awaitAll

Rating: 78/100 — proposed by 1 agent(s): codex; raw proposals: perf_codex#1, data_codex#8

:277-285 launches async probes for primary+alternative and awaitAll()s both, so a dead secondary URL costs a full timeout. Race to first UrlCheckResult.Success (or try primary, fall back); keep the isAlternativeUrl bookkeeping at :290.

---

### DictionaryDao.insertAll: chunk the bulk insert; cancel stale search jobs

Rating: 78/100 — proposed by 2 agent(s): gemini, grok; raw proposals: perf_gemini#1, repo_grok#6

DictionaryRepositoryImpl.insertDictionaryData (:37-60) hands the entire parsed JSON array to a single @Insert; chunk to Room's 999-variable limit inside insertAll. DictionaryViewModel.searchWord (:57-65) launches a new viewModelScope.launch per keystroke with no Job cancel — store a Job and cancel on new query.

---

### PdfThumbnailLoader: build cache key inside the IO block, cap render height

Rating: 78/100 — proposed by 2 agent(s): claude, grok; raw proposals: perf_claude#8, perf_grok#3

utils/PdfThumbnailLoader.kt:19 stats the file (lastModified/length) on the caller dispatcher before withContext(io); :26-28 scales height unbounded (portrait scans). Move key computation into the IO block and coerce the rendered height.

---

### Hoist per-bind listeners/caches in list adapters (sweep)

Rating: 78/100 — proposed by 2 agent(s): devin, kimi; raw proposals: perf_devin#4, perf_devin#5, perf_devin#7, perf_devin#8, perf_devin#9, perf_devin#10, perf_kimi#8

Every named adapter allocates listeners/colors/strings inside onBindViewHolder: PersonalsAdapter (:40,:46,:52,:58 — 4 per bind), StorageCategoryDetailFragment.ResourceAdapter (:190-203 + formatSize per bind), SurveysAdapter (:60-109 startSurvey/sendSurvey listeners + 3 getString per bind), ResourcesTagsAdapter (:79-118 listeners + colors), ChatHistoryAdapter (:123,:137-139 share icon+listener), CheckboxAdapter (:39), TeamsSelectionAdapter (:42-46 null/attach churn), NotificationsAdapter (:104-145), OnboardingAdapter (:28-30 getColor per page), CoursesStepsAdapter (:53 getString per bind), ChatShareTargetAdapter (:38-41 listeners + typeface/color per bind). Move to ViewHolder init / payload-aware rebinds / cached formats.

---

### Date-format caches in SubmissionsListAdapter + FeedbackReplyAdapter

Rating: 78/100 — proposed by 2 agent(s): devin, kimi; raw proposals: perf_devin#3, perf_kimi#5

SubmissionsListAdapter.kt:35 formats lastUpdateTime per bind; FeedbackReplyAdapter.kt:19-26 formats per bind — FeedbackAdapter.kt:36,67 already proves the dateCache pattern in the same package. Bound the cache (kimi suggests ~200).

---

### AutoSyncWorker: overlap the 17 sequential uploads

Rating: 78/100 — proposed by 1 agent(s): devin; raw proposals: perf_devin#2

:114-130 issues uploadExamResult, uploadFeedback, … uploadActivities one after another inside a single launch. Wrap independent uploads in supervisorScope/async (bounded concurrency, e.g. Semaphore(4)) so a slow endpoint doesn't serialize the whole autosync window; keep the compareAndSet guard at :112.

---

### ApiClient: stop retrying 4xx

Rating: 78/100 — proposed by 1 agent(s): grok; raw proposals: data_grok#2

data/api/ApiClient.kt:15 passes shouldRetry = { resp -> resp == null || !resp.isSuccessful } — every client error (incl. 400/401/404) gets retried through RetryUtils. Retry only transport failures + 5xx.

---

### DictionaryActivity: drop the redundant count query

Rating: 77/100 — proposed by 2 agent(s): devin, grok; raw proposals: perf_grok#6, data_devin#9

:58-59 calls viewModel.loadCount() then viewModel.loadDictionary(); DictionaryViewModel.loadDictionary already ends in dictionaryRepository.count() via insertDictionaryData -> Populated. Remove loadCount() (one fewer Room query + one less state churn on every open).

---

### EnterprisesRepositoryImpl.updateReport: targeted DAO update instead of read-modify-upsert

Rating: 77/100 — proposed by 3 agent(s): claude, codex, gemini; raw proposals: repo_codex#2, repo_claude#3, repo_gemini#2

:55 -> updateTeamEntityById (:129-134) does getById -> mutate -> upsert full MyTeam row (all ~30 columns rewritten, _rev churn). Add a typed @Update (FinanceReportParams projection) and skip the round-trip. NOTE: repo_gemini named the method 'saveReport' and wrong field names; actual is updateReport/FinanceReportParams — its proposal is the incomplete version.

---

### DownloadRepositoryImpl: log the actual request URL on 404

Rating: 77/100 — proposed by 2 agent(s): codex, gemini; raw proposals: perf_gemini#4, repo_codex#3

:53-64 runs URL_REGEX.find(response.toString()) which captures Response internals, not the URL. Log the `url` parameter already in scope (or response.raw().request.url); delete URL_REGEX.

---

### ResourceViewerFragment: lazy extractPdfText, drop the duplicate Player.Listener

Rating: 77/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#3

:512 calls extractPdfText() unconditionally (parses the whole PDF on open even when Read-Aloud is never used) and :458-478 initializeAudioPlayer() attaches a second Player.Listener to a player that already has one at :406. Gate extraction on first read-aloud tap; keep a single listener.

---

### ServerUrlMapper: HEAD request + disconnect in finally + URI host parse

Rating: 77/100 — proposed by 2 agent(s): gemini, grok; raw proposals: perf_gemini#2, data_grok#3

isUrlDirectlyReachable (:112-140) uses requestMethod="GET" for a reachability ping and leaks the connection on exceptions before disconnect(); ServerConfigUtils.isLocalNetwork (:27,:71-75) runs a regex per host. HEAD + try/finally + java.net.URI parse (also what data_grok proposes with the @ReachabilityHttpClient).

---

### SyncRepositoryImpl: build keysObject with kotlinx Json directly

Rating: 77/100 — proposed by 1 agent(s): gemini; raw proposals: perf_gemini#3

processShelfDataOptimizedSync :131-143 builds a Gson JsonObject then converts via toKotlinx() to hit a kotlinx-serialized API — double serialization per batch. Build the kotlinx JsonObject once. (gemini also claimed unbounded shelf parallelism, but a Semaphore(8) already exists at :221 for inner batches; the outer map is bounded by ~9 shelf types — that element is dropped.)

---

### TTSManager: fast-path stripMarkdown, StringBuilder in formatCsvForSpeech

Rating: 77/100 — proposed by 1 agent(s): gemini; raw proposals: perf_gemini#8

:99-124 runs 10 sequential regex replaces over every utterance even when no markdown chars are present; formatCsvForSpeech nests mapIndexed + string templates. Early-exit when the text has no markdown metachars; single StringBuilder pass.

---

### checkServerAvailability/updateServerIfNecessary: skip the alternative probe when primary answers

Rating: 76/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#2

ConfigurationsRepositoryImpl.checkServerAvailability (:185-188) and ServerUrlMapper.updateServerIfNecessary (:102-107) unconditionally probe the alternative URL even when the primary is already reachable — one extra network round-trip per sync/login. Evaluate primary first, probe alt only on failure.

---

### OnboardingActivity: move copyAssets off the main thread

Rating: 76/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#5

:68 calls MapTileUtils.copyAssets(this) synchronously in onCreate — two mbtiles (~MBs) copied on the main thread at first launch, with dispatcherProvider (:45) and a lifecycleScope IO block (:93) already available. Wrap in lifecycleScope.launch(dispatcherProvider.io).

---

### BasePermissionActivity: cache the manifest permission set; drop dead install-permission state

Rating: 76/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#6

isPermissionDeclaredInManifest (:117-122) calls getPackageInfo(GET_PERMISSIONS) ~7x per requestAllPermissions; fetch once. BaseContainerFragment.kt:63-64,71 declares installUnknownSourcesRequestCode + hasInstallPermissionValue which are written but never read.

---

### DictionaryMapper: deterministic IDs instead of UUID.randomUUID()

Rating: 76/100 — proposed by 1 agent(s): grok; raw proposals: perf_grok#10

repository/DictionaryMapper.kt:15 generates UUID.randomUUID().toString() per word on every seed — reseeding after a table wipe produces all-new ids, breaking any downstream references. Hash content (word+meaning) into a stable id.

---

### printStackTrace -> Log sweep + drop the dead IllegalAccessException catch

Rating: 76/100 — proposed by 1 agent(s): devin; raw proposals: data_devin#4, data_devin#5, data_devin#6

16 verified printStackTrace() sites across the named files (Utilities.toast's catch of IllegalAccessException at :59-64 is dead — nothing in the block throws it), plus KeyboardUtils, RetryUtils, UserEntity, HealthExamination, Feedback, BaseContainer/BaseTeamFragment, ChatDetailFragment, TakeCourseViewModel, HealthExaminationViewModel, WebViewActivity (25 files hold the pattern overall).

---

### NotificationsRepositoryImpl.updateCountNotification: skip the upsert when nothing changed

Rating: 75/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#10

:78-118 always calls notificationDao.upsert even when the parsed value is unchanged (only isRead/createdAt differ conditionally). Compare formatted message + value first; no-op early.

---

### AndroidDecrypter: shared SecureRandom, drop the redundant key/iv copy arrays

Rating: 75/100 — proposed by 1 agent(s): gemini; raw proposals: perf_gemini#7

utils/AndroidDecrypter.kt: generateIv news up SecureRandom() per call (:117); encrypt (:22-28) allocates fixed ByteArray(16)/ByteArray(32) then arraycopies the decoded bytes instead of using them directly.

---

### ImageViewerUtils.showZoomableImage: decode bounded to screen size

Rating: 75/100 — proposed by 1 agent(s): grok; raw proposals: perf_grok#2

utils/ImageViewerUtils.kt loads full-resolution images into a PhotoView dialog with no inSampleSize/override — OOM/jank on camera-sized uploads. Decode to screen bounds.

---

### SyncTimeLogger: lock-free buckets + single-pass aggregates

Rating: 75/100 — proposed by 2 agent(s): codex, devin; raw proposals: perf_devin#6, perf_codex#3

:170,:187 back per-key lists with Collections.synchronizedList so every log add + every summary iteration takes a global lock; generateSummary (:235-296) then walks each list ~4x under synchronized{}. ConcurrentLinkedQueue + one fold per list (compute sum/count/max in a single pass).

---

### NotificationsRepositoryImpl: keep join-request details in one batch map

Rating: 75/100 — proposed by 2 agent(s): codex, devin; raw proposals: repo_devin#6, data_codex#7

:207-209 falls back to getJoinRequestDetails(null) after getJoinRequestDetailsBatch (:299-314) which round-trips through Triple intermediates — build Map<String,Pair> directly (or a small data class). (devin's 'dedupe resolveType' element is stale: resolveType is already a single override at :413.)

---

### NotificationActionReceiver: drop the delay(200) handshake

Rating: 75/100 — proposed by 1 agent(s): devin; raw proposals: data_devin#10

services/NotificationActionReceiver.kt:103 sleeps 200ms inside the receiver coroutine — a fixed sleep racing a state write; replace with the actual completion signal or remove if the ordering no longer needs it.

---

### UserDataUploadScheduler: distinctUntilChanged on the work-info flow

Rating: 75/100 — proposed by 1 agent(s): codex; raw proposals: data_codex#9

:32-49 maps getWorkInfosForUniqueWorkFlow straight to SyncUiState — WorkManager re-emits identical WorkInfos; distinctUntilChanged() kills duplicate UI churn.

---

### SubmissionsRepositoryExporter: shared PDF plumbing + timeProvider header

Rating: 75/100 — proposed by 1 agent(s): codex; raw proposals: repo_codex#10, data_codex#10

:55-110 and :149-178 duplicate PdfDocument/PageInfo/Paint setup verbatim for the two export paths; :178 stamps Instant.now() though timeProvider is injected and used at :112 — share a page builder and use the provider clock.

---

### AudioRecorder: drop the exists()-poll filename loop, guard stop/forceStop

Rating: 74/100 — proposed by 1 agent(s): gemini; raw proposals: perf_gemini#5

createAudioFile loops `while (audioFile.exists() && attempt < 100)` (:78-84) hammering stat() on a contended name — generate the unique name once (timestamp/uuid); stop() -> handleUncaughtException at :96-102 and forceStop (:35-40) calls recorder.stop() unguarded (IllegalStateException on never-started recorders).

---

### ServerReachabilityProvider.isPrimaryServerReachable: use the existing cache

Rating: 74/100 — proposed by 1 agent(s): kimi; raw proposals: perf_kimi#4

:47-50 bypasses the reachabilityCache + TTL that isServerReachable (:22-36) already maintains — every call hits the network. Route through the cached path (or share a cache key).

---

### HealthExaminationAdapter: drop `toString()+""`, reorder DIFF_CALLBACK cheap-first

Rating: 74/100 — proposed by 1 agent(s): kimi; raw proposals: perf_kimi#7

checkEmpty/checkEmptyInt (:126-132) append "" to an already-String; DiffUtils callback (:178-191) compares the expensive `data` JsonObject mid-chain — move it last so cheap scalar fields short-circuit.

---

### ActivitiesFragment: configure the chart once, update data per emission

Rating: 74/100 — proposed by 1 agent(s): kimi; raw proposals: perf_kimi#9

ui/dashboard/ActivitiesFragment.kt renderChart (:33-80) re-applies axis/legend/formatter config on every flow emission; split configureChartOnce() from updateChartData().

---

### ResourcesPreviewLoader.getCsvPreview: buffer the FileReader

Rating: 74/100 — proposed by 1 agent(s): grok; raw proposals: perf_grok#4

:55-62 hands a raw FileReader to CSVReaderBuilder — unbuffered reads per cell. BufferedReader wrapper (or readText for small files).

---

### Delete CourseDetailProvider + RatingSummaryProvider pass-throughs

Rating: 74/100 — proposed by 2 agent(s): devin, grok; raw proposals: repo_devin#1, repo_grok#2

ui/courses/CourseDetailProvider.kt and RatingSummaryProvider.kt are one-line wrappers over CoursesRepository/RatingsRepository with exactly one caller (CourseDetailViewModel). Inject the repositories directly. (Both devin and grok cited repository/ paths — actual location is ui/courses/.)

---

### ChatViewModel: extract a ChatConversationPager

Rating: 74/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#2

ChatViewModel.kt:59-65 keeps mutable pagination state (allConversations/loadedCount) plus PAGE_SIZE slicing at :197-243 — pull the pure paging logic into a testable pager object.

---

### AchievementViewModel: route achievement calls through UserAchievementsRepository

Rating: 74/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#3

AchievementViewModel calls userRepository.initializeAchievement/updateAchievement/getAchievementData (:66,:71,:96) even though UserAchievementsRepository is already Hilt-bound (RepositoryModule.kt:212). Move the three methods to the narrower interface and update the VM.

---

### HealthExaminationAdapter: extract the entity->item mapping

Rating: 74/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#10

submitExaminations (:58-88) builds HealthExaminationItem + displayNameCache inside the adapter; move to a HealthExaminationItemMapper so name-resolution is unit-testable.

---

### NotificationDao.getNotifications: return a NotificationPayload projection

Rating: 74/100 — proposed by 1 agent(s): codex; raw proposals: repo_codex#1

NotificationsRepositoryImpl.kt:137-159 fetches full AppNotification rows then copies ~14 fields into NotificationPayload. A @Query returning the payload columns skips the row materialization.

---

### NotificationDao.markSynced: typed update instead of hand-built CASE SQL

Rating: 74/100 — proposed by 1 agent(s): gemini; raw proposals: repo_gemini#9

:78-113 builds a RawQuery string with WHEN-clause binding by hand (chunked(250), manual placeholder list). A typed @Update/entity-update path (or at least a named helper) removes the SQL-injection-shaped string concat.

---

### ReplyViewModelTest: cover the reply ViewModel

Rating: 74/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#9

ui/voices/ReplyViewModel.kt exists (used by ReplyActivity :52) with no unit test — add state-transition + repository-mock coverage.

---

### SyncActivity: route raw clock reads through TimeProvider

Rating: 74/100 — proposed by 1 agent(s): devin; raw proposals: data_devin#3

:617 System.currentTimeMillis(), :649/:735 Date().time bypass the injected timeProvider (used elsewhere at :735 for getRelativeTime) — same-file clock inconsistency.

---

### DownloadUtils: dedupe the twin enqueue blocks

Rating: 74/100 — proposed by 1 agent(s): devin; raw proposals: data_devin#7

:147-160 (priority) and :162-176 (pending) are identical getStringSet->merge->putStringSet->startDownloadServiceSafely bodies differing only in the prefs key + flag.

---

### RemovedLogDao.getRemovedDocIds: filter NULLs in SQL, return List<String>

Rating: 74/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#1

:24 returns List<String?> forcing downstream filterNotNull — `AND docId IS NOT NULL` in the query fixes the KSP warning and the callers' null handling.

---

### ExamAnswerUtils.checkMultipleSelectAnswer: linear frequency compare

Rating: 74/100 — proposed by 1 agent(s): codex; raw proposals: perf_codex#10, data_codex#5

utils/ExamAnswerUtils.kt:77-86 lowercases+sorts both selected and correct lists per evaluation — compare via a frequency map / sorted-multiset diff in one pass.

---

### StorageCategoryViewModel.toggleItemChecked: single-row delta instead of recount

Rating: 74/100 — proposed by 1 agent(s): codex; raw proposals: perf_codex#5

ui/settings/StorageCategoryViewModel.kt:61-69 recomputes checkedCount by scanning every item per toggle — update only the toggled row and delta the count.

---

### LoginActivity: drop the built-but-never-shown dialog, run syncCommunityDocs once

Rating: 73/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#4

:355-359 inflates DialogServerUrlBinding + builds a MaterialDialog, assigns currentDialog, never shows it; :380-384 calls syncCommunityDocs() on every onCreate (rotation re-runs a network sync). Delete the dead dialog; gate the sync on a savedInstanceState/once flag.

---

### DownloadService: count remaining without the union-copy; drop the recount in cleanupProcessedUrls

Rating: 73/100 — proposed by 1 agent(s): claude; raw proposals: perf_claude#7

:179-184 builds `priority + pendingUrls` (new Set per call) when it only needs pending count minus processed; :204 recounts immediately after the count was already set at :155 with unchanged inputs.

---

### ProgressGridAdapter: typed cell model instead of ListAdapter<JsonObject>

Rating: 73/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#5

ProgressGridAdapter.kt:15-16 diffs raw JsonObjects; CourseProgressData.steps is a JsonArray materialized in CourseProgressActivity (:52). Introduce a StepProgressCell data class and parse once at the repository/mapper boundary.

---

### TeamDao: report-only projection for getReportsFlow; drop dead getAll/getNonArchivedReportsByTeamId

Rating: 73/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#7

EnterprisesRepositoryImpl.kt:86-91 observes full MyTeam rows then toFinanceReport()s (:138); TeamDao.getAll (:17) and getNonArchivedReportsByTeamId (:25) have zero callers outside the DAO. Add a FinanceReport projection query (the CsvProjection at :27 shows the pattern) and delete the dead queries.

---

### SyncManager: move resource page fetching into SyncRepository

Rating: 73/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#8

resourceTransactionSync (:290-439) calls apiInterface.getJsonObject against _all_docs endpoints directly from the service. Hoist fetchResourceCount/fetchResourceDocsPage into the repository layer so CouchDB plumbing stops living in a service.

---

### RetryDao: findExistingId (id-only) + drop dead update()

Rating: 73/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#1

findExisting (:38-42) does SELECT * where callers only need existence/id; RetryDao.update (:20) has zero callers — replace with a @Query("SELECT id …") projection and delete the dead update.

---

### Typed VersionCheckError + fix SyncActivity's dead startsWith("Config") branch

Rating: 73/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#5

ConfigurationsRepository.CheckVersionCallback routes errors through localized strings; SyncActivity.kt:770 does msg.startsWith("Config") on user-facing text (dead/fragile). Return a sealed VersionCheckError and compare the enum.

---

### Move health examination encryption/entity building out of HealthExaminationActivity

Rating: 73/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#6

encrypt/generateKey/generateIv + GsonUtils.gson entity assembly sit in the Activity (:126-127,:282-284,:377) — push into HealthExaminationViewModel/repository so the crypto path is testable and the Activity stops importing AndroidDecrypter.

---

### ConfigurationsRepositoryImpl: AppVersionProvider + literal protocol compare

Rating: 73/100 — proposed by 2 agent(s): codex, devin; raw proposals: repo_codex#8, data_devin#8

:323 reads context.getString(R.string.app_version) (localized resource as a version string) though AppVersionProvider is already injectable; :298-301 compares NetworkUtils.extractProtocol(url) against localized R.string.http_protocol/https_protocol strings — compare to "http"/"https" literals (or the Constants the file already uses).

---

### CoursesPagerAdapter + TeamPagerAdapter: O(1) containsItem

Rating: 73/100 — proposed by 1 agent(s): codex; raw proposals: data_codex#2, data_codex#3

CoursesPagerAdapter.kt:67-69 and TeamPagerAdapter.kt:62-64 run `pages.any { itemIds[it.id] == itemId }` per containsItem — a reversed id->config map or itemIds.values set turns the hot RecyclerView call constant.

---

### LifeCache: immutable cached items, bound the map, evict corrupt entries

Rating: 72/100 — proposed by 2 agent(s): codex, grok; raw proposals: perf_grok#5, repo_codex#9

repository/LifeCache.kt: read() defensively copies (`map { it.copy() }`) because CachedMyLifeItem vars are mutable — make them vals and return the list directly; the ConcurrentHashMap is unbounded and a corrupt cached entry poisons future reads (catch returns null but keeps the value in memoryCache).

---

### PublicSurveyViewModel: move respondent parsing into SurveysPublicMapper

Rating: 72/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#4

:81-87 does JsonParser.parseString + sanitizeRespondent in the ViewModel; repository/SurveysPublicMapper.kt:28 already owns sanitizeRespondent — add parseRespondent so JSON string handling stays in the mapper.

---

### MarkdownDialogFragment: move progress/course logic into MarkdownViewModel + safe activity cast

Rating: 72/100 — proposed by 1 agent(s): gemini; raw proposals: repo_gemini#10

MarkdownViewModel.kt is a stub (hasActiveUserSyncAction only) while the Fragment does activity casts `(activity as DashboardActivity)` at :79,:141 and reads args at :63-67 — add calculateProgress()/view-state to the VM and use `activity as? OnHomeItemClickListener`.

---

### CoursesRepository.getMyCoursesFlow: drop the suspend modifier

Rating: 72/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#4

CoursesRepository.kt:18 / Impl :117 declare `suspend fun … : Flow<…>` — a cold Flow factory doesn't need suspension; making it a plain function removes a suspend hop for no semantic change.

---

### FreeSpaceWorker: pass the batch set directly

Rating: 72/100 — proposed by 1 agent(s): codex; raw proposals: data_codex#6

FreeSpaceWorker.kt:35-50 builds clearedResourceIds then calls markResourcesAsNotOffline(clearedResourceIds.toSet()) per batch — the MutableSet is already a Set; pass it (and clear) instead of copying.

---

### RetryInterceptorTest: de-Robolectricize + latch-based timing

Rating: 72/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#1

data/api/RetryInterceptorTest.kt runs @RunWith(RobolectricTestRunner) (:28) with Thread.sleep(100) at :222 — a plain JUnit test + CountDownLatch/fake clock removes the sandbox boot and the timing flake.

---

### Drop the redundant SystemTimeProvider.sleep override

Rating: 72/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#2

TimeProvider.kt:17-18 default-implements sleep(); :25-26 overrides with an identical body — delete the override.

---

### NotificationDao: atomic check-then-act in bulkInsertFromSync

Rating: 71/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#2

NotificationsRepositoryImpl.bulkInsertFromSync (:503-516) does getByIds then upsertAll as two statements — under concurrent sync the isRead/needsSync merge can race. One @Transaction (or a preserving-upsert query) closes it.

---

### Drop redundant dispatcher hops in LifeViewModel / CommunityServicesViewModel / LeadersViewModel

Rating: 71/100 — proposed by 1 agent(s): grok; raw proposals: repo_grok#7, repo_grok#8, repo_grok#10

withContext(dispatcherProvider.io) inside already-suspended repository calls (LifeViewModel :35,:45,:55; CommunityServicesViewModel :30,:35) and launch(dispatcherProvider.default) in LeadersViewModel :29 — repositories already choose their dispatcher.

---

### Shared debounceDistinct operator

Rating: 71/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#7

debounce(300) is hand-applied at SubmissionsFragment:58, SurveyFragment:85, CollectionsFragment:97, ResourcesFragment:373 (+FlowPreview opt-ins) — a shared `debounceDistinct()` extension removes the repeated @OptIn + duplicate logic.

---

### CourseRatingUtilsTest: non-relaxed mocks / pure-function coverage

Rating: 71/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#6

utils/CourseRatingUtilsTest.kt:15-18 uses relaxed mockk for Context + TextViews — tighten to explicit stubs or extract the pure rating computation so it needs no Android mocks.

---

### JsonUtils: share the empty JsonObject/JsonArray fallbacks

Rating: 70/100 — proposed by 1 agent(s): gemini; raw proposals: perf_gemini#9

:45-50 allocates JsonObject(emptyMap())/JsonArray(emptyList()) on every miss — two small objects per absent field across every sync parse. Return shared constants. (Its 'numeric fast-path' element is stale: getInt/getLong/getFloat already call longOrNull/floatOrNull first.)

---

### CoursesStepsViewModel: ResourceDownloadCoordinator.downloadPriorityIfReachable

Rating: 70/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#9

:85-89 couples checkServerAvailability + downloadResourcesPriority inline; extract a coordinator single-method so the availability probe + priority download sequencing is one unit-tested seam.

---

### SettingsActivity: hoist the guest check into SettingsViewModel.isGuest

Rating: 70/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#10

userModel?.id?.startsWith("guest") is evaluated at :231,:259,:302 (+guestDialog branches) — centralize as a VM/derived flag.

---

### DictionaryActivity: stop constructing the dictionary download in the Activity

Rating: 70/100 — proposed by 2 agent(s): gemini, kimi; raw proposals: repo_gemini#1, repo_kimi#7

Builds the Download list from Constants.DICTIONARY_URL (:84-89) and calls DownloadUtils.openDownloadService itself — move getDictionaryDownloadUrl/queueDownload behind the ViewModel/repository. (repo_kimi's 'all access via VM' variant merges here.)

---

### ResourceViewerViewModel: drop @ApplicationContext

Rating: 70/100 — proposed by 1 agent(s): gemini; raw proposals: repo_gemini#5

:30 injects Context for getExternalFilesDir (:121), checkFileExist (:125), openDownloadService (:126), PDFBoxResourceLoader.init (:132) — move filesystem paths to StoragePathResolver and PDFBox init to the repository/init path.

---

### Chat fragments: hoist prefs + AI-provider fetch into ChatViewModel

Rating: 70/100 — proposed by 1 agent(s): grok; raw proposals: repo_grok#1

ChatDetailFragment/ChatHistoryFragment each touch SharedPrefManager + ServerUrlMapper + fetchAiProviders — centralize the provider fetch + preference reads in the shared VM.

---

### HealthExaminationViewModel: replace getHealthEntry's Pair destructure

Rating: 70/100 — proposed by 1 agent(s): grok; raw proposals: repo_grok#5

:63 destructures `val (u, p) = healthRepository.getHealthEntry(userId)` — swap for explicit getUserById + getByIdOrUserId reads so callers see named fields.

---

### Dead null-guard sweep (elvis on non-null fields)

Rating: 70/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#2, data_claude#3

Verified dead branches on non-null var fields: UploadConfigs.kt:195 `exam.id ?: ""` (StepExam.id is `var id: String = ""`), PersonalsFragment.kt:119,:136 `personal.id ?: personal._id` (Personal.id non-null), RatingsViewModel.kt:63 `user.id?.takeIf …` (UserEntity.id non-null), NotificationsRepositoryImpl.kt:325 + VoicesLabelManager.kt:83 user/voice id guards, LoginActivity.kt:467 `team._id != null && team._id == …`. Re-verify each model nullability before deleting.

---

### Drop @ApplicationContext from CourseDetailViewModel + CoursesStepsViewModel

Rating: 70/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#4

Both inject Context (:37 and :47) only to resolve the external files dir; StoragePathResolver (already injectable, DictionaryFileReader uses it) exposes resolveExternalFilesDir — route through it.

---

### ResourceViewerFragment: playback-speed off MaterialDialog + simplify library.id?.let

Rating: 70/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#8

:236 still builds a MaterialDialog (deprecated lib) while :124 guards library.id?.let — AlertDialog (or the app's dialog helper) + a single early-return.

---

### Hoist mockkStatic/mockkObject out of per-test bodies

Rating: 70/100 — proposed by 1 agent(s): gemini; raw proposals: data_gemini#4, data_gemini#5

ConfigurationsRepositoryImplTest (:93,:159,:253,:317-337,:505) and RetryQueueWorkerTest (:67-86) re-mock statics per test — @BeforeClass/class-level stubbing cuts per-test overhead and flaky ordering.

---

### NotificationUtils: replace EntryPointAccessors with an injectable binding

Rating: 69/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#8

utils/NotificationUtils.kt:102-103 calls EntryPointAccessors.fromApplication per invocation to reach timeProvider — a lazy-injected dependency removes the service-locator hop. (kimi's 8b — a single-method dep for TeamsVoicesViewModel's 6-repository constructor at :31-37 — merges as the same narrow-dependency theme.)

---

### InlineResourceAdapter: skip redundant preview resets on payloads

Rating: 68/100 — proposed by 1 agent(s): kimi; raw proposals: perf_kimi#10

updateStatusAndPreview (:155-204) re-runs the whole visibility/icon/preview chain even for PAYLOAD_TITLE-only updates; payloads block at :113-145 re-arms the same listener per payload. Skip when the bound (title,address,status) triple is unchanged.

---

### CoursesItemUtils.bindCover: attach Glide to the ImageView, not the context

Rating: 68/100 — proposed by 1 agent(s): grok; raw proposals: perf_grok#7

utils/CoursesItemUtils.kt:111 Glide.with(context) doesn't track the recycled view's lifecycle; use the view's context/fragment scope. GridSpanCalculator.columnCount runs per bind when the container isn't laid out yet (:98) — acceptable fallback, cache the span.

---

### HealthViewModel: collect healthSyncUpdates internally

Rating: 68/100 — proposed by 1 agent(s): gemini; raw proposals: repo_gemini#4

:72 exposes realtimeSyncManager.updatesFor(HEALTH_TABLE) as a public Flow but the VM never consumes it — collect in init and fold refresh into the patient list pipeline.

---

### ChatViewModel: narrow ChatShareActions interface

Rating: 68/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#1

ChatViewModel injects chatRepository+userRepository+teamsRepository+voicesRepository+realtimeSyncManager+configurationsRepository (:50-57) to build share targets — a narrow ChatShareActions reader reduces the dependency surface.

---

### NotificationsRepositoryImpl: narrow VoicesCountsReader/NotificationUsersReader

Rating: 68/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#2

Injects full VoicesRepository + Lazy<UserRepository> (:28,:33) but only uses countTopLevelByTeams (:388) + getUserById/getUsersByIds (:271,:323) — narrow reader interfaces tighten the boundary.

---

### ReplyActivity: move member-details fetch into ReplyViewModel

Rating: 68/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#3

ReplyActivity.kt injects activitiesRepository + voicesRepository (:59,:66) and calls VoicesActions.showMemberDetails(userModel, activitiesRepository) at :195 — the Activity shouldn't own repository access. (kimi's 3b alternative — drop ProcessUserDataActivity's redundant repo fields at :50,:60 — merges here as the same Activity-layer boundary fix.)

---

### TakeCourseViewModel.logCourseVisit -> ActivitiesRepository

Rating: 68/100 — proposed by 1 agent(s): grok; raw proposals: repo_grok#3

:55-56 funnels a visit-logging call through coursesRepository; the activity-logging responsibility belongs in ActivitiesRepository.

---

### Clear compiler warnings in the 3 named test files

Rating: 68/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#9

Claude flagged unused/import-shadow warnings in test sources (details in its doc); silence-only cleanup — verify the warning list when picking this up.

---

### playstore.sh: batch the GitHub API reads

Rating: 68/100 — proposed by 1 agent(s): gemini; raw proposals: data_gemini#1

.github/scripts/playstore.sh calls `gh api` per job/step inside code_for() (:84,:96,:101) run per name at :136 — fold into jobs.steps embedded in the workflow, one call per release lookup.

---

### ThemeManager: extract showThemeDialog + de-Robolectricize its test

Rating: 68/100 — proposed by 1 agent(s): gemini; raw proposals: data_gemini#9

ui ThemeManager.showThemeDialog (:17) builds the dialog inline; splitting + dropping Robolectric for the pure-prefs parts speeds the unit suite.

---

### Extract TextNormalizeUtils out of Utilities

Rating: 68/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#9

Utilities.kt:80 normalizeText is pure string work (called per search hit by ChatSearch) — a focused TextNormalizeUtils makes it unit-testable without the Android-coupled Utilities god-object.

---

### LoginSyncManager.isManager: early-return instead of scanning all roles

Rating: 68/100 — proposed by 1 agent(s): grok; raw proposals: data_grok#8

services/sync/LoginSyncManager.kt:141-149 iterates the roles array setting a flag after a match is already known — early-exit the loop (or `any {}`).

---

### RetryInterceptor: match read-only POSTs by pathSegments, not encodedPath suffix

Rating: 68/100 — proposed by 1 agent(s): grok; raw proposals: data_grok#7

data/api/RetryInterceptor.kt:29 uses `request.url.encodedPath.endsWith(...)` — segment-level matching avoids false positives on prefixed/suffixed paths; keep the extras-propagation on retries.

---

### SubmissionsRepositoryImpl: extract the upload serializer

Rating: 67/100 — proposed by 1 agent(s): kimi; raw proposals: repo_kimi#6

:497-524 builds nested JsonObject payload (parent/user/membershipDoc) inline amid DAO code; a SubmissionsUploadSerializer makes the wire shape testable without the repository.

---

### playstore-quota.sh: --jq field projection

Rating: 67/100 — proposed by 1 agent(s): gemini; raw proposals: data_gemini#2

.github/scripts/playstore-quota.sh fetches whole documents then greps fields — --jq selects the needed fields server-side.

---

### playstore.yml: sparse checkout + persist-credentials:false

Rating: 67/100 — proposed by 1 agent(s): grok; raw proposals: data_grok#1

.github/workflows/playstore.yml checks out with actions/checkout@v7 at default settings (:55-57) — sparse-checkout limits the tree and persist-credentials:false keeps the token off disk.

---

### LifeAdapter: compile-time icon map + hoisted listeners

Rating: 66/100 — proposed by 2 agent(s): grok, kimi; raw proposals: perf_kimi#6, perf_grok#9

:52-57 resolves drawable ids via resources.getIdentifier per bind (mitigated by drawableCache but still string-keyed lookup); fragmentCache at :178-186 shows the compile-time pattern already exists in-file; imageView/drag/visibility listeners rebind per bind (:62,:68,:74).

---

### RequestsViewModel.leaveTeam: single guarded leader-hand-off path

Rating: 66/100 — proposed by 1 agent(s): devin; raw proposals: repo_devin#9

ui/teams/members/RequestsViewModel.kt:81-83 runs nextLeader lookup, updateTeamLeader, removeMember inline; the nextLeader?.let guard means a null candidate still removes the member with no leader assigned — unify the hand-off (or the 'leaving is last member' branch) so the ordering/guard lives in one tested place.

---

### MyLife: Flow-based observation end-to-end

Rating: 66/100 — proposed by 1 agent(s): gemini; raw proposals: repo_gemini#3

MyLifeDao has no Flow-returning query at all (suspend getters only :13-36) and LifeViewModel manually re-fetches after every mutation (:35-55). Add observe*ByUserId(): Flow queries and have the VM collect — removes the manual refresh bookkeeping. (gemini named a non-existent observeAll; the task is to add one.)

---

### RetryDao: getPendingPreview + getPendingDueCount for the settings dialog

Rating: 65/100 — proposed by 1 agent(s): claude; raw proposals: data_claude#5

The settings dialog re-reads pending rows; small preview/count queries (getPendingPreview, getPendingDueCount — neither exists today) avoid materializing full RetryOperation lists.

---

### test_timing_summary.py: sort + cap the slowest-tests table

Rating: 65/100 — proposed by 3 agent(s): claude, gemini, kimi; raw proposals: data_claude#10, data_gemini#3, data_kimi#7

.github/scripts/test_timing_summary.py prints per-class tables unsorted. NOTE: the 'likely warm-up' flag and --warn-over already exist, so claude's warm-up column and kimi's per-class warnings are partially stale — the net-new work is the sorted top-N table (gemini).

---

### ResourcesSyncRepository: narrow interface for resource-only sync calls

Rating: 64/100 — proposed by 1 agent(s): claude; raw proposals: repo_claude#8

SyncRepositoryImpl + SyncManager both expose resource fetching broadly; extract a focused ResourcesSyncRepository (fetchResourceCount/fetchResourceDocsPage + module binding). Lower confidence on exact call-site inventory.

---

### RetryQueueWorker: drop the RetryQueue pass-through

Rating: 64/100 — proposed by 1 agent(s): gemini; raw proposals: repo_gemini#6

services/retry/RetryQueueWorker.kt injects both RetryQueue and RetryRepository where one is a thin delegate — depend on the repository directly.

---

### HealthViewModel: patient loads behind a dedicated reader

Rating: 64/100 — proposed by 1 agent(s): grok; raw proposals: repo_grok#4

:79 calls healthRepository.getPatientsSortedBy from the VM; grok proposes a UserRepository-level patient reader. Premise verified (the fetch exists); the boundary choice is conventional.

---

### Model layer: stop reaching into the global GsonUtils.gson

Rating: 64/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#3, data_kimi#4

Answer.kt:38, MyPlanet.kt:41,55, Achievement.kt:58-108 (and siblings) call the process-global singleton directly — thread an injected/provided Json through the model layer instead of the ambient global. (kimi's stated premise — 'models construct their own Gson' — is wrong: they use the shared one; the real smell is the global dependency.)

---

### EnterprisesRepositoryImpl: consistent withContext discipline

Rating: 62/100 — proposed by 1 agent(s): gemini; raw proposals: data_gemini#10

Only :122 wraps in withContext(dispatcherProvider.io) while other suspend paths run unwrapped — normalize dispatcher usage across the impl (or drop it where Room already handles dispatch).

---

### UploadManagerTest: triage the 45-second test class

Rating: 62/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#5

services/UploadManagerTest.kt (433 lines, ~24 tests) is claimed at ~45s — profile for Robolectric/sleep/real-IO hotspots and restructure. Runtime claim unverified on master.

---

### TimeUtils.formatterFor: audit the fallback construction

Rating: 56/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#10

:29-48 already caches DateTimeFormatter in a ConcurrentHashMap keyed by pattern/zone/locale — the remaining claim is that fallback sites (:48,:67) construct uncached formatters. Verify whether the uncached ones are actually hot before changing.

---

### NetworkUtils: audit the ResettableCache eager paths

Rating: 54/100 — proposed by 1 agent(s): kimi; raw proposals: data_kimi#8

:42-83 builds a stack of ResettableCache lazy wrappers — claimed per-call allocation gating is mostly already lazy; audit which caches actually initialize eagerly (uniqueIdentifierCache reads Build.ID at :74-76) and gate only those. Weak premise — audit-only task.

---


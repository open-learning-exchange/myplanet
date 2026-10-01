Paths are relative to `app/src/main/java/org/ole/planet/myplanet/` unless they start with `app/` or `.github/`. Raw ids: `<round>-<agent>-<n>`, round P = perf quick wins, R = repository boundaries, M = micro-optimizations; agents dev/cdx/cld/gem/kim/grk.

---

### 1. sync: skip the alternative-server probe when the primary already answered

**rating 83**: evidence 10 · impact 7 · feasibility 9  
**proposed by**: claude opus 5.5 `P-cld-2` (1.00)

**verified**: repository/ConfigurationsRepositoryImpl.kt:185-188 always runs checkServerAvailability(alternativeUrl) after the primary; the result is read only when !primaryReachable (:190). services/sync/ServerUrlMapper.kt:102-107 (updateServerIfNecessary) does the same. On LAN-only tablets every reachability check waits out an extra timeout.

**do**: Probe the alternative only when the primary failed, in both places. Extend ServerUrlMapperTest.testUpdateServerIfNecessaryWhenPrimaryIsUp to assert the alternative URL is never probed; keep the 'falls back to alternative url' ConfigurationsRepositoryImplTest case green.

---

### 2. viewer: extract PDF text only when Read Aloud is tapped, and drop the duplicate audio Player.Listener

**rating 81**: evidence 10 · impact 7 · feasibility 8  
**proposed by**: claude opus 5.5 `P-cld-3` (1.00)

**verified**: ui/viewer/ResourceViewerFragment.kt setupPdfViewer calls extractPdfText() on every PDF open (PDFBox full-document text strip via ResourceViewerViewModel.extractPdfText) though pdfText is only read in the fabReadAloud click. initializeAudioPlayer adds a second listener that repeats createExoPlayer's onIsPlayingChanged/STATE_ENDED handling, so progress is saved twice.

**do**: Remove the eager call; in the fabReadAloud listener fill pdfText lazily on viewLifecycleOwner.lifecycleScope then speak. Delete the second player.addListener block in initializeAudioPlayer.

---

### 3. data: chunk the remaining unbounded IN-list DAO queries and delete dead DAO methods

**rating 75**: evidence 9 · impact 6 · feasibility 9  
**proposed by**: claude opus 5.5 `R-cld-4` (1.00), devin swe 2 `M-dev-1` (0.35)

**verified**: minSdk 26 SQLite (API<30) caps bind variables at 999. Unchunked with unchunked callers: MyLibraryDao.getByIds/getByResourceIds/getByResourceIdsNotUserPattern (data/room/dao/MyLibraryDao.kt:35-39,154-158; callers ResourcesRepositoryImpl.kt:193,198,534,594) and QuestionDao.getByIds (QuestionDao.kt:10; ProgressRepositoryImpl.kt:186 passes every answered question id). Zero callers: QuestionDao.upsertAllBlocking, CourseStepDao.upsertAllBlocking, MyLibraryDao.getByCourseId.

**do**: Rename each @Query to ...Internal and add a same-name default wrapper: empty -> emptyList(), else distinct().chunked(900).flatMap (the file's existing getByCourseIds pattern). Delete the three dead methods. Add >900-id in-memory tests for MyLibraryDao.getByIds, getByResourceIdsNotUserPattern and QuestionDao.getByIds.

**watch out**: Do NOT wrap UserDao.getUsersByAnyIds/getGuestUsersByNames (M-dev-1's other half): callers already chunk at 400/500, and getUsersByAnyIds binds the list twice, so a 900 chunk would bind 1,800 variables.

---

### 4. chat: chunk the AI typing animation and stop scrolling the list on every character

**rating 73**: evidence 9 · impact 6 · feasibility 8  
**proposed by**: claude opus 5.5 `P-cld-9` (1.00), copilot kimi k3 `P-kim-1` (0.60), copilot grok 4.6 `P-grk-1` (0.50)

**verified**: ui/chat/ChatDetailFragment.kt:270-282 calls onUpdate(response.substring(0, i+1)) once per character with delay(10); ui/chat/ChatAdapter.kt:76-79 sets the text and calls recyclerView.scrollToPosition on every update. A 3,000-char reply = 3,000 substrings (O(n^2) copying), 3,000 relayouts, 3,000 scrolls on main.

**do**: Advance by max(1, len/200) chars per tick (clamped), keep the 10 ms tick, isActive check, final full-text emit, single onComplete and the cancel lambda. In ResponseViewHolder.bind scroll only when lineCount changes. Add a ChatDetailFragmentTest case: update count bounded, last update == full response, cancel stops updates.

---

### 5. download: cap PDF thumbnail size and keep the cache key's stat calls off the main thread

**rating 73**: evidence 9 · impact 6 · feasibility 8  
**proposed by**: claude opus 5.5 `P-cld-8` (1.00), copilot grok 4.6 `P-grk-3` (0.40)

**verified**: utils/PdfThumbnailLoader.kt:19 builds the key from file.lastModified()/length() before withContext(io), i.e. on the adapter's main-thread scope (ResourcesAdapter.kt:395, InlineResourceAdapter.kt:239). Lines 26-28 size the bitmap as page.height*scale with no cap; an OutOfMemoryError escapes catch(Exception). computePdfRenderSize (utils/PdfRenderUtils.kt:7) already caps maxDim/maxPixels.

**do**: Move key building, cache get and put into the IO block; size with computePdfRenderSize(page.width, page.height, targetWidthPx). Optionally (grok) dedupe concurrent loads of the same key with an in-flight map; a plain cache re-check does not achieve 'one render' on its own.

---

### 6. sync: replace localized strings in the version-check callback with a typed error (fixes the never-opening server dialog)

**rating 73**: evidence 10 · impact 6 · feasibility 7  
**proposed by**: claude opus 5.5 `R-cld-5` (1.00)

**verified**: ui/sync/SyncActivity.kt:767-771 opens settingDialog() only if msg.startsWith("Config"); the message is context.getString(R.string.server_url_not_configured) = "Server URL not configured" (repository/ConfigurationsRepositoryImpl.kt:105), so the dialog never opens. getPlanetType/getParentCode/getCommunityName/clearPreferences on ConfigurationsRepository have no callers outside the Impl (other hits are sharedPrefManager.*).

**do**: Add sealed VersionCheckError (NotConfigured, VersionNotFound, UpToDate, ApkNotFoundOnServer, ConnectionFailed); change CheckVersionCallback.onError to take it; map to the same R.string ids in SyncActivity and replace the startsWith check with error == NotConfigured; AutoSyncWorker only changes signature. Make the 4 dead interface methods private. Update ConfigurationsRepositoryImplTest verifications.

---

### 7. sync: stop retrying 4xx responses in ApiClient

**rating 73**: evidence 9 · impact 6 · feasibility 8  
**proposed by**: copilot grok 4.6 `M-grk-2` (1.00)

**verified**: data/api/ApiClient.kt:13-15 retries with shouldRetry = { resp == null || !resp.isSuccessful } (3 attempts, 2 s delay), so 401/404 cost ~4 s of pointless retries; RetryInterceptor already retries 5xx/IO at the OkHttp layer, so 5xx gets up to 3x3 attempts. 8 call sites.

**do**: Retry only null responses, thrown IO failures and HTTP 5xx; return 4xx immediately. Keep 3 attempts / 2 s for retryable cases. Add ApiClientTest cases: 404/401 -> one call; keep the 503-then-success case.

---

### 8. notifications: make sync merge and read/delete check-then-act atomic in NotificationDao

**rating 70**: evidence 9 · impact 6 · feasibility 7  
**proposed by**: claude opus 5.5 `R-cld-1` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:503-519 bulkInsertFromSync reads getByIds then upsertAll in separate statements; insert(doc) :484-492 likewise, so a mark-as-read landing in between is overwritten by the server copy. markAllUnreadAsRead (:129-135), markNotificationsAsRead (:120-127), deleteNotifications (:494-501) select then write outside a transaction.

**do**: Add @Transaction default methods to NotificationDao: upsertFromServerPreservingLocal (reads a small id/isRead projection for needsSync=1 rows, chunked 900), markAllUnreadAsReadReturningIds, markExistingAsRead, deleteExisting. Make the five repository methods single DAO calls. Add a Room in-memory test that a needsSync=1,isRead=1 row stays read after a server upsert with isRead=0.

---

### 9. perf: drop the double map lookup and per-call lambda allocation in GsonUtils field getters

**rating 69**: evidence 9 · impact 5 · feasibility 9  
**proposed by**: claude opus 5.5 `P-cld-1` (1.00)

**verified**: utils/GsonUtils.kt:106-107 fieldElement does has()+get(); getPrimitive (:109) is not inline, so each getString/getInt/getLong/getBoolean/getFloat allocates two lambdas and boxes; getJsonElement (:185-190) repeats has()+get(). 253 qualified call sites on the sync path.

**do**: fieldElement = jsonObject?.get(fieldName); make getPrimitive private inline (crossinline only if required); single get + null check in getJsonElement. No signature or fallback changes; GsonUtils* tests must pass unchanged.

---

### 10. sync: return the first successful configuration probe without waiting for the slower one

**rating 68**: evidence 9 · impact 6 · feasibility 6  
**proposed by**: codex sol 5.6 `P-cdx-1` (1.00), codex sol 5.6 `M-cdx-8` (1.00)

**verified**: repository/ConfigurationsRepositoryImpl.kt:278-285 getMinApk launches primary and alternative checks concurrently but awaitAll() waits for the slowest (up to the 15 s withTimeout) before picking a Success.

**do**: Consume completions as they arrive; on success cancel and join the rest; on all-failure keep the deterministic URL-order fallback; rethrow cancellation. Add coroutine tests (fast alt + delayed primary; all fail in reversed order).

**watch out**: Today the primary wins when both succeed. Decide explicitly whether an earlier alternative success may now be picked (it flips isAlternativeUrl).

---

### 11. enterprises: replace the finance-report read-modify-upsert with one targeted UPDATE

**rating 67**: evidence 9 · impact 5 · feasibility 8  
**proposed by**: claude opus 5.5 `R-cld-3` (1.00), codex sol 5.6 `R-cdx-2` (0.80)

**verified**: repository/EnterprisesRepositoryImpl.kt:55-79 updateReport builds a Gson JsonObject, loads the whole teams row via updateTeamEntityById (:129-135: getById + upsert) and rewrites every column, so a concurrent sync write is lost. TeamDao.getNonArchivedReportsByTeamId (TeamDao.kt:25) has no production caller.

**do**: Add TeamDao.updateReportFields(...) UPDATE of the 9 report columns + isUpdated=1 WHERE _id; call it from updateReport; delete updateTeamEntityById. Retarget the getNonArchivedReportsByTeamId tests to observeNonArchivedReportsByTeamId(...).first() and delete it.

**watch out**: Keep today's behaviour of attaching the image even when the row is missing unless you mean to change it (codex's spec asserts the opposite while claiming to preserve behaviour).

---

### 12. login: stop inflating an unused server dialog and re-syncing community docs on every LoginActivity create

**rating 67**: evidence 9 · impact 5 · feasibility 8  
**proposed by**: claude opus 5.5 `P-cld-4` (1.00)

**verified**: ui/sync/LoginActivity.kt:355-359 inflates DialogServerUrlBinding and builds a MaterialDialog on each sync-icon tap; for CallerContext.LOGIN_ACTIVITY SyncConfigurationCoordinator never reads currentDialog (:77-81). :380-384 calls loginViewModel.syncCommunityDocs() on every onCreate (rotation, recreate).

**do**: Delete the four dialog lines and their imports. Add a communityDocsSynced flag in LoginViewModel so a successful sync is not repeated (failures still retry); add two LoginViewModelTest cases.

---

### 13. voices: chunk the typing animation in VoicesAdapterHelper

**rating 67**: evidence 9 · impact 5 · feasibility 8  
**proposed by**: copilot kimi k3 `P-kim-2` (1.00)

**verified**: ui/voices/VoicesAdapterHelper.kt:13-31 createOnAnimateTyping uses the same per-character substring + delay(10) loop; used by VoicesFragment, TeamsVoicesFragment and ReplyActivity.

**do**: Advance in chunks keeping the signature, main-dispatcher launch, isActive check and cancel lambda; add VoicesAdapterHelperTest.

---

### 14. startup: copy map-tile assets off the main thread at cold start

**rating 64**: evidence 10 · impact 4 · feasibility 9  
**proposed by**: claude opus 5.5 `P-cld-5` (1.00)

**verified**: ui/onboarding/OnboardingActivity.kt:68 calls MapTileUtils.copyAssets(this) in onCreate; it does external-storage exists/mkdirs/assets.open for two .mbtiles files, and no assets dir exists in app/src, so every launch throws and logs two exceptions on main.

**do**: lifecycleScope.launch(dispatcherProvider.io) { copyAssets(applicationContext) } at the same spot.

---

### 15. dashboard: skip the no-op notification upsert on every dashboard load

**rating 62**: evidence 9 · impact 4 · feasibility 9  
**proposed by**: claude opus 5.5 `P-cld-10` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:78-118 updateCountNotification always ends with notificationDao.upsert (:114) even when message, relatedId and value are unchanged; each write invalidates every notification Flow (badge included).

**do**: Return early when existing != null, !valueChanged, message and relatedId equal. Rewrite the two 'unchanged' tests to coVerify(exactly = 0) upsert.

---

### 16. health: move key generation, encryption and entity building out of HealthExaminationActivity

**rating 62**: evidence 9 · impact 5 · feasibility 6  
**proposed by**: claude opus 5.5 `R-cld-6` (1.00)

**verified**: ui/health/HealthExaminationActivity.kt:240-291 (saveData) generates key/IV, mutates the repository-owned UserEntity and encrypts the examination; createPojo :366-383 encrypts the profile; initExamination :117-127 decrypts with getEncryptedDataAsJson.

**do**: Decrypt once in HealthExaminationViewModel.loadData into state.savedExamination; change saveExamination to take plain sign/health and do id/key/IV/encrypt on dispatcherProvider.io before healthRepository.saveExamination. Update the three saveExamination_* tests and add a null-key round-trip test.

---

### 17. enterprises: read attachment images off the main thread when saving a transaction or report

**rating 61**: evidence 9 · impact 4 · feasibility 8  
**proposed by**: devin swe 2 `P-dev-1` (1.00)

**verified**: FileUtils.readBytesFromUri + getDisplayName run inside setPositiveButton on main: ui/enterprises/EnterprisesFinancesFragment.kt:305-306, EnterprisesReportsFragment.kt:186-187 and :269-270.

**do**: Capture applicationContext and the Uri, launch on viewLifecycleOwner.lifecycleScope, read under withContext(dispatcherProvider.io), then call the unchanged viewModel methods.

---

### 18. audio: stop sending the user to the home screen when a recording is stopped too early

**rating 60**: evidence 7 · impact 5 · feasibility 7  
**proposed by**: copilot gemini 3.8 flash `P-gem-5` (1.00)

**verified**: services/AudioRecorder.kt:101-102 routes MediaRecorder.stop()'s RuntimeException to MainApplication.handleUncaughtException, which logs a crash and starts the HOME intent (MainApplication.kt:199-208); forceStop() (:35-41) calls stop() unguarded (used by AddResourceFragment:159).

**do**: Catch the stop/release RuntimeException in stopRecording and forceStop, release, null the recorder and report via audioRecordListener.onError instead of handleUncaughtException. Add AudioRecorderTest for premature stop.

**watch out**: The 'up to 100 disk probes' claim is overstated: the UUID loop normally does one exists(); simplifying it is optional.

---

### 19. courses: delete the CourseDetailProvider/RatingSummaryProvider passthroughs

**rating 57**: evidence 10 · impact 3 · feasibility 9  
**proposed by**: devin swe 2 `R-dev-1` (1.00), copilot grok 4.6 `R-grk-2` (1.00)

**verified**: ui/courses/CourseDetailProvider.kt and RatingSummaryProvider.kt are one-line wrappers over coursesRepository.getCourseDetailModel and ratingsRepository.getRatingSummary("course", ...); only CourseDetailViewModel (:38-39) and its test use them.

**do**: Inject CoursesRepository and RatingsRepository into CourseDetailViewModel, call them directly, delete both files, switch the test to repository mocks.

---

### 20. reachability: cache primary-server probes in ServerReachabilityProvider

**rating 57**: evidence 8 · impact 4 · feasibility 7  
**proposed by**: copilot kimi k3 `P-kim-4` (1.00)

**verified**: utils/ServerReachabilityProvider.kt:47-50 isPrimaryServerReachable always runs a live tryConnect, while isServerReachable uses the 30 s reachabilityCache.

**do**: Give it the same cache-then-probe flow keyed by the raw URL, without the alternative-URL fallback; test one newCall within TTL.

---

### 21. data: move resource _all_docs fetching and doc filtering from SyncManager into SyncRepository

**rating 56**: evidence 9 · impact 4 · feasibility 6  
**proposed by**: devin swe 2 `R-dev-8` (1.00)

**verified**: services/sync/SyncManager.kt:290-439 resourceTransactionSync performs raw apiInterface.getJsonObject calls (:310, :341), total_rows parsing (:312-314) and _design/doc filtering (:367-377) inside the sync service.

**do**: Add fetchResourceCount / fetchResourceDocsPage to SyncRepository(+Impl) with the moved code (null on failure); SyncManager keeps loop control, logging, progress, checkpointing and cleanup. Add SyncRepositoryImplTest coverage.

---

### 22. courses: drop Context from CourseDetailViewModel and CoursesStepsViewModel via StoragePathResolver

**rating 55**: evidence 9 · impact 3 · feasibility 9  
**proposed by**: claude opus 5.5 `M-cld-4` (1.00)

**verified**: ui/courses/CourseDetailViewModel.kt:37/:69 and CoursesStepsViewModel.kt inject @ApplicationContext only for getExternalFilesDir(null); FileUtils.getExternalFilesDir (utils/FileUtils.kt:37-39) caches it; utils/StoragePathResolver.kt wraps FileUtils.

**do**: Add StoragePathResolver.resolveExternalFilesDir(); inject the resolver instead of Context in both VMs (keep the 'file://null/ole/' behaviour); update both tests.

---

### 23. data: slim the retry-queue duplicate lookup and delete dead RetryRepository/RetryDao surface

**rating 55**: evidence 9 · impact 3 · feasibility 9  
**proposed by**: claude opus 5.5 `R-cld-2` (1.00)

**verified**: repository/RetryRepositoryImpl.kt:42-52 recordFailure uses only existing.id from RetryDao.findExisting (SELECT *, pulls serializedPayload). getPendingCount/deletePendingAndAbandonedOperations are interface methods used only inside the Impl; RetryDao.update has no caller.

**do**: Replace with findExistingId (SELECT id ... LIMIT 1); delete RetryDao.update; make the two methods private; retarget tests through getRetryQueueSnapshot/safeClearQueue.

---

### 24. startup: cache the manifest permission set and delete the dead install-permission check

**rating 55**: evidence 9 · impact 3 · feasibility 9  
**proposed by**: claude opus 5.5 `P-cld-6` (1.00)

**verified**: base/BasePermissionActivity.kt isPermissionDeclaredInManifest calls packageManager.getPackageInfo(GET_PERMISSIONS) on every check, and requestAllPermissions makes up to 4 checks per SDK branch; base/BaseContainerFragment.kt:71 stores hasInstallPermission() in hasInstallPermissionValue (:64), which is never read (installUnknownSourcesRequestCode :63 unused).

**do**: Lazy HashSet of requestedPermissions; delete the two dead fields and the :71 call.

---

### 25. tests: add ReplyViewModel.getNewsWithReplies coverage

**rating 55**: evidence 9 · impact 3 · feasibility 9  
**proposed by**: copilot kimi k3 `R-kim-9` (1.00)

**verified**: No ReplyViewModelTest exists under app/src/test/.../ui/voices/; ReplyViewModel.getNewsWithReplies (:14) returns Pair<News?, List<News>>.

**do**: Add tests: parent with ordered replies, missing parent, repository error.

---

### 26. sync: HEAD-probe and always close connections in ServerUrlMapper.isUrlDirectlyReachable

**rating 54**: evidence 8 · impact 4 · feasibility 6  
**proposed by**: copilot grok 4.6 `M-grk-3` (1.00), copilot gemini 3.8 flash `P-gem-2` (0.60)

**verified**: services/sync/ServerUrlMapper.kt:116-128 opens HttpURLConnection with GET and skips disconnect() if responseCode throws; called from ConfigurationsRepositoryImpl:561.

**do**: Use HEAD and close in finally (grok: inject the existing @ReachabilityHttpClient OkHttpClient and use java.net.URI in extractBaseUrl, keeping processUrl assertions green).

**watch out**: Gemini's ServerConfigUtils.isLocalNetwork regex rewrite is not a hotspot; skip it.

---

### 27. courses: decide course-step downloads in ResourceDownloadCoordinator, not CoursesStepsViewModel

**rating 53**: evidence 9 · impact 3 · feasibility 8  
**proposed by**: claude opus 5.5 `R-cld-9` (1.00)

**verified**: ui/courses/CoursesStepsViewModel.kt loadStep calls configurationsRepository.checkServerAvailability() then resourcesRepository.downloadResourcesPriority(); ConfigurationsRepository is injected only for this. services/ResourceDownloadCoordinator.kt:26-34 already owns the 'check server then download' policy.

**do**: Add downloadPriorityIfReachable(items): Boolean to the coordinator (false for empty/unreachable, otherwise launch in applicationScope); call it from loadStep; drop the ConfigurationsRepository param. Retarget CoursesStepsViewModelTest; add three coordinator tests.

---

### 28. courses: give the progress grid a typed row model

**rating 53**: evidence 9 · impact 3 · feasibility 8  
**proposed by**: devin swe 2 `R-dev-5` (1.00)

**verified**: ui/courses/ProgressGridAdapter.kt is ListAdapter<JsonObject> reading item["percentage"].asString / item["completed"].asBoolean at bind; CourseProgressActivity.kt:52 submits data.steps.map { it.asJsonObject } (CourseProgressData.steps: JsonArray).

**do**: Add StepProgressCell(stepId, percentage, completed) plus a mapper in CourseProgressData.kt; switch the adapter/diff to it; map before submitList.

---

### 29. health: extract examination-item mapping out of HealthExaminationAdapter

**rating 53**: evidence 9 · impact 3 · feasibility 8  
**proposed by**: devin swe 2 `R-dev-10` (1.00)

**verified**: ui/health/HealthExaminationAdapter.kt:58-88 submitExaminations decrypts, resolves creator names (displayNameCache) and formats dates inside the ListAdapter.

**do**: Move the mapping into a HealthExaminationItemMapper in ui/health; keep withContext(default)/submitList in the adapter.

---

### 30. viewer: move the playback-speed picker off material-dialogs and drop a redundant safe call

**rating 53**: evidence 9 · impact 3 · feasibility 8  
**proposed by**: claude opus 5.5 `M-cld-8` (1.00)

**verified**: ui/viewer/ResourceViewerFragment.kt:227 showPlaybackSpeedDialog uses com.afollestad MaterialDialog (import :55), one of the material-dialogs users keeping android.enableJetifier=true (gradle.properties:26); :124 library.id?.let on a non-null id.

**do**: Rewrite with AlertDialog.Builder(...).setSingleChoiceItems; remove the import; call saveTranslationAudioPath(library.id, ...) directly. Build both flavors.

---

### 31. chat: extract conversation pagination from ChatViewModel into a plain pager

**rating 52**: evidence 9 · impact 3 · feasibility 7  
**proposed by**: devin swe 2 `R-dev-2` (1.00)

**verified**: ui/chat/ChatViewModel.kt:59-65 holds PAGE_SIZE/allConversations/loadedCount; :197-243 parseAndBuildInitialPage (Gson parse), buildInitialPage, buildMessagesSlice, loadMoreConversations, clearPaginationState.

**do**: Create ChatConversationPager with the moved bodies; keep the VM's public methods as thin delegations so fragments/tests are unchanged.

---

### 32. chat: hoist server prefs and AI-provider fetching from the chat fragments into ChatViewModel

**rating 52**: evidence 8 · impact 4 · feasibility 5  
**proposed by**: copilot grok 4.6 `R-grk-1` (1.00)

**verified**: ui/chat/ChatDetailFragment.kt injects SharedPrefManager (:104) and ServerUrlMapper (:107) and calls MainApplication.isServerReachable/isPrimaryServerReachable (:34-35, :604-725); ChatHistoryFragment injects SharedPrefManager (:43).

**do**: Add ensureAiProviders() and a no-arg history reload to ChatViewModel; move processServerUrl/updateServerIfNecessary/clearAlternativeUrlIfPrimaryRestored; fragments drop the injections.

---

### 33. data: introduce UserLookupRepository and move single-purpose callers onto it

**rating 52**: evidence 9 · impact 3 · feasibility 7  
**proposed by**: claude opus 5.5 `R-cld-7` (1.00)

**verified**: DiagnosticsRepositoryImpl (:63, :86) and FeedbackListViewModel (:35) call only getUserModel() on the ~50-method UserRepository.

**do**: Move getUserModel/getUserById/getUsersByIds into UserLookupRepository, make UserRepository extend it, provide it from a new module by delegating from UserRepository (RepositoryModule is PR-owned); retype the two consumers.

---

### 34. enterprises: project finance reports and delete dead TeamDao queries

**rating 52**: evidence 9 · impact 3 · feasibility 7  
**proposed by**: devin swe 2 `R-dev-7` (1.00)

**verified**: EnterprisesRepositoryImpl.getReportsFlow (:86-91) observes full MyTeam rows only to map toFinanceReport; TeamDao.getAll() and getNonArchivedReportsByTeamId have no production callers.

**do**: Add a FinanceReport projection Flow (isUpdated AS updated) with the same WHERE/ORDER; delete toFinanceReport and the two dead queries; move their tests.

**watch out**: Overlaps the 'targeted UPDATE' task on getNonArchivedReportsByTeamId - whichever lands second drops that part.

---

### 35. sync: split ResourcesSyncRepository so the sync layer stops depending on the full resources surface

**rating 52**: evidence 9 · impact 3 · feasibility 7  
**proposed by**: claude opus 5.5 `R-cld-8` (1.00)

**verified**: SyncRepositoryImpl uses only batchInsertMyLibrary (:55); SyncManager only batchInsertResources (:385) and removeDeletedResources (:433). ResourcesRepositoryImpl has no class-level scope (singleton only via RepositoryModule:162-164).

**do**: Extract the three methods into ResourcesSyncRepository, make ResourcesRepository extend it, provide by delegation (never a second @Binds to the Impl), retype the two consumers.

---

### 36. calendar: index meetups by local date once per emission (in CalendarViewModel)

**rating 51**: evidence 8 · impact 3 · feasibility 8  
**proposed by**: copilot grok 4.6 `M-grk-9` (0.90), codex sol 5.6 `M-cdx-1` (0.80), copilot kimi k3 `P-kim-3` (0.70), copilot gemini 3.8 flash `R-gem-7` (0.40)

**verified**: ui/calendar/CalendarFragment.kt (87 lines): the day-click listener re-converts every meetup's startDate with ZoneId.systemDefault() and filters on each tap; showAgendaDialog builds a formatter per dialog.

**do**: Derive Map<LocalDate, List<Meetup>> (or epoch-day keys) once per meetups emission, preferably as CalendarViewModel state; the fragment only builds CalendarDay+drawable and looks up the tapped day; keep order and the empty-day no-op; hoist the formatter.

---

### 37. chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default

**rating 51**: evidence 8 · impact 3 · feasibility 8  
**proposed by**: claude opus 5.5 `M-cld-6` (1.00), copilot grok 4.6 `P-grk-8` (0.80), codex sol 5.6 `P-cdx-2` (0.60), codex sol 5.6 `M-cdx-4` (0.60), copilot gemini 3.8 flash `P-gem-10` (0.40), copilot grok 4.6 `M-grk-10` (0.40), copilot kimi k3 `R-kim-4b` (0.30)

**verified**: utils/ChatSearch.kt fullConvoSearch/searchByTitle rebuild normalized ConvoChat/TitleChat copies of every chat on each search (fired per debounced keystroke over the same allChats, ChatViewModel.kt:139,162); :25 defaults dispatcher to Dispatchers.Default (the only Dispatchers.* use outside DispatcherProvider).

**do**: Preferred (claude): a ChatSearch.Index with lazy normalized views rebuilt when allChats changes, so only the query is normalized per call; otherwise stream without wrapper lists. Make the dispatcher required. Keep the four ranking buckets.

**watch out**: Do NOT make a blank query return emptyList() (gemini, grok M-10): ChatSearchTest 'search with empty query returns all chats' pins the current behaviour, and the VM already short-circuits blank queries.

---

### 38. sync: run AutoSyncWorker's independent uploads concurrently

**rating 51**: evidence 8 · impact 5 · feasibility 3  
**proposed by**: devin swe 2 `P-dev-2` (1.00)

**verified**: services/AutoSyncWorker.kt:114-130 calls 17 uploadManager.upload*() functions strictly in sequence inside the isSyncRunning gate.

**do**: supervisorScope + async per upload with per-upload runCatching (rethrow CancellationException), awaitAll, then setLastSync.

**watch out**: High risk: UploadManager shares state across uploads and some orderings may be implicit (exam results before submissions, activities last); needs an ordering audit before parallelising.

---

### 39. test-infra: separate warm-up outliers from real class cost in the CI slow-test summary

**rating 51**: evidence 7 · impact 3 · feasibility 9  
**proposed by**: claude opus 5.5 `M-cld-10` (1.00)

**verified**: .github/scripts/test_timing_summary.py flags warm-up per test (:95, >5x class median with >=3 tests) but ranks classes by raw total (:98), so classes whose total is one warm-up test top the table.

**do**: Sum warm-up time per class, sort by elapsed minus warm-up, add a warm-up column and a total line; keep --warn-over on the raw total.

**watch out**: Call it 'first-test warm-up', not 'Robolectric': CourseRatingUtilsTest has no Robolectric runner (its spike is MockK/agent instrumentation).

---

### 40. ui: hoist ThemeManager's dialog out of the singleton service

**rating 51**: evidence 8 · impact 3 · feasibility 8  
**proposed by**: copilot gemini 3.8 flash `M-gem-9` (1.00)

**verified**: services/ThemeManager.kt:17-45 showThemeDialog builds an AlertDialog inside a @Singleton state service; ThemeManagerTest runs under RobolectricTestRunner only for that.

**do**: Move showThemeDialog to a UI helper; keep ThemeManager to getCurrentThemeMode/setThemeMode; make ThemeManagerTest plain JUnit.

---

### 41. core: extract Utilities' pure text helpers into an Android-free object

**rating 50**: evidence 8 · impact 3 · feasibility 7  
**proposed by**: copilot kimi k3 `M-kim-9` (1.00)

**verified**: utils/Utilities.kt imports Handler/Toast/MimeTypeMap/Patterns; checkNA (:68), toHex (:76), normalizeText (:80), isValidEmail (:22) are pure apart from Patterns.

**do**: Move them to TextNormalizeUtils with delegating one-liners in Utilities; extend UtilitiesTest.

**watch out**: Re-implementing isValidEmail without Patterns.EMAIL_ADDRESS can change which addresses validate; pin the current behaviour with tests first.

---

### 42. dashboard: move the challenge-dialog earnings/progress logic into MarkdownViewModel

**rating 50**: evidence 8 · impact 3 · feasibility 7  
**proposed by**: copilot gemini 3.8 flash `R-gem-10` (1.00)

**verified**: ui/components/MarkdownDialogFragment.kt:94-97 computes earnings and progress; :115-125 matches "no iniciado"/"terminado"; :79 hard-casts (activity as DashboardActivity).

**do**: Add a pure calculateProgress(...) returning a small state model in MarkdownViewModel; use a safe cast. Add unit tests for the math.

---

### 43. teams: unify leader hand-off before member removal in RequestsViewModel

**rating 49**: evidence 8 · impact 4 · feasibility 4  
**proposed by**: devin swe 2 `R-dev-9` (1.00)

**verified**: ui/teams/members/RequestsViewModel.kt leaveTeam (:76-89) promotes a candidate and removes unconditionally; removeMember on self (:91-111) emits CannotRemoveLastLeader when no candidate. Neither checks whether the actor is actually a leader.

**do**: Extract one hand-off helper used by both.

**watch out**: Blocking leaveTeam when there is no successor traps a sole member in the team (product decision), and RequestsViewModelTest is owned by open PR #17776.

---

### 44. data: remove android.util.Log from DiagnosticsRepositoryImpl

**rating 48**: evidence 10 · impact 2 · feasibility 10  
**proposed by**: codex sol 5.6 `R-cdx-7` (1.00), copilot gemini 3.8 flash `M-gem-8` (0.60), copilot gemini 3.8 flash `R-gem-8` (0.40)

**verified**: repository/DiagnosticsRepositoryImpl.kt Log.w in saveLogToRoom/saveLogsToRoom while both already return Boolean success.

**do**: Remove the import, calls and TAG; add DAO-exception -> false tests for both paths.

**watch out**: Skip gemini's extras: withContext(io) around suspend DAO calls is redundant, and ApkLogDao.markUploadedBatch already chunks.

---

### 45. data: remove android.util.Log from DictionaryRepositoryImpl

**rating 48**: evidence 10 · impact 2 · feasibility 10  
**proposed by**: copilot grok 4.6 `M-grk-5` (1.00), codex sol 5.6 `R-cdx-4` (0.80), copilot gemini 3.8 flash `M-gem-6` (0.60), copilot grok 4.6 `R-grk-6` (0.40)

**verified**: repository/DictionaryRepositoryImpl.kt:3,61 - Log.e is the only android.* import; failure is already returned as DictionaryLoad.Failed(e).

**do**: Drop the import and call, keep Failed(e) and CancellationException rethrow. Optional (grok): inject the Hilt-provided Json instead of the Json companion and resolve the dictionary File once in DictionaryFileReaderImpl.

**watch out**: Do not 'chunk' DictionaryDao.insertAll (R-grk-6, P-gem-1): Room's @Insert(List) binds one row at a time, so the 999-variable limit does not apply.

---

### 46. sync: route SyncActivity's raw clock reads through the injected TimeProvider

**rating 48**: evidence 10 · impact 2 · feasibility 10  
**proposed by**: devin swe 2 `M-dev-3` (1.00)

**verified**: ui/sync/SyncActivity.kt:617 System.currentTimeMillis(), :649 and :735 Date().time, :737 Date().time inside getRelativeTime while timeProvider is already injected.

**do**: Replace the four with timeProvider.now(); drop the unused Date import.

---

### 47. upload: stop recounting the whole download queue three times per file

**rating 48**: evidence 7 · impact 3 · feasibility 7  
**proposed by**: claude opus 5.5 `P-cld-7` (1.00)

**verified**: services/DownloadService.kt:179-184 getRemainingCount builds priority + pendingUrls (a new set of the whole queue) and runs at :155, :204 and :584 per file.

**do**: Count without the union; keep one getStringSet per key per call.

**watch out**: Do not drop the :204 recount blindly: URLs enqueued during a download change the count (claude's 'always matches :155' is wrong).

---

### 48. viewer: drop Context from ResourceViewerViewModel

**rating 48**: evidence 8 · impact 3 · feasibility 6  
**proposed by**: copilot gemini 3.8 flash `R-gem-5` (1.00)

**verified**: ui/viewer/ResourceViewerViewModel.kt:30 injects @ApplicationContext for getExternalFilesDir (:120), downloadResource/openDownloadService (:124-127) and PDFBoxResourceLoader.init (:132).

**do**: Move the three Android calls to ResourceViewerFragment (init PDFBox once there); keep extractPdfText as pure PDDocument work; update ResourceViewerViewModelTest.

**watch out**: Coordinate with the 'extract PDF text only on Read Aloud' task (same functions).

---

### 49. data: make RemovedLogDao.getRemovedDocIds return non-null ids

**rating 47**: evidence 9 · impact 2 · feasibility 10  
**proposed by**: claude opus 5.5 `M-cld-1` (1.00)

**verified**: data/room/dao/RemovedLogDao.kt:25-26 returns List<String?> (claude cites a KSP 'meaningless nullable type argument' warning from CI; not re-run here); RemovedLog.docId is nullable.

**do**: Append AND docId IS NOT NULL, return List<String>; add a RemovedLogDaoTest case with a null docId.

---

### 50. data: remove android.util.Log from RetryRepositoryImpl

**rating 47**: evidence 10 · impact 2 · feasibility 9  
**proposed by**: copilot grok 4.6 `M-grk-4` (1.00), codex sol 5.6 `R-cdx-5` (0.90), copilot grok 4.6 `R-grk-9` (0.80), copilot gemini 3.8 flash `M-gem-7` (0.60)

**verified**: repository/RetryRepositoryImpl.kt imports android.util.Log for outcome logging in executeOperation; outcomes are already persisted (markFailed) and returned as RetryOperationResult.

**do**: Remove Log/TAG and the test's Log stubs; keep 409-success, 5xx-retryable, other-terminal, cancellation semantics. Optional (grok): parse with the injected Json.

---

### 51. data: strip android.util.Log from GsonUtils

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: copilot grok 4.6 `M-grk-6` (1.00)

**verified**: utils/GsonUtils.kt logFallback (Log.isLoggable/Log.d) and extractSharedTeamName (Log.w); GsonUtilsNoLogStubTest documents that fallbacks must work without Log.

**do**: Remove the import; silent fallbacks.

**watch out**: Same file as the GsonUtils inlining task (which leaves Log alone) - land them in sequence.

---

### 52. dictionary: drop the duplicate COUNT on DictionaryActivity open

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: copilot grok 4.6 `P-grk-6` (1.00), devin swe 2 `M-dev-9` (1.00)

**verified**: ui/dictionary/DictionaryActivity.kt:58-59 calls loadCount() then loadDictionary(); loadDictionary already emits Populated(count()) (DictionaryViewModel.kt:41-44); two launches race to write loadState.

**do**: Remove the loadCount() call and method; repoint its test at loadDictionary.

---

### 53. download: derive the 404 diagnostic URL from the request instead of regex over response.toString()

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: codex sol 5.6 `R-cdx-3` (1.00), copilot gemini 3.8 flash `P-gem-4` (0.70)

**verified**: repository/DownloadRepositoryImpl.kt:22-24 URL_REGEX; :53-64 parses response.toString() to log the 404 URL.

**do**: Log response.raw().request.url (fall back to the url argument); delete URL_REGEX and the exception-driven branch; test a 404 and a diagnostics-write failure.

---

### 54. settings: hoist the guest-user check into SettingsViewModel

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: claude opus 5.5 `R-cld-10` (1.00)

**verified**: ui/settings/SettingsActivity.kt SettingFragment injects UserSessionManager (:89-90) and repeats userModel?.id?.startsWith("guest") at :194/:231, :258-259, :301-302.

**do**: Add suspend isGuest() to SettingsViewModel with the exact predicate; replace the three checks; drop the field.

---

### 55. surveys: move respondent JSON parsing into SurveysPublicMapper

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: devin swe 2 `R-dev-4` (1.00)

**verified**: ui/surveys/PublicSurveyViewModel.kt:81-87 parses submission.user with JsonParser (import :5) and calls publicMapper.sanitizeRespondent.

**do**: Add parseRespondent(userJson): JsonObject? to the mapper with the same null/blank/{}/malformed semantics; add mapper tests.

---

### 56. sync: route shelf-sync failures through SyncTimeLogger instead of android.util.Log

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: codex sol 5.6 `R-cdx-6` (1.00)

**verified**: repository/SyncRepositoryImpl.kt Log.e in processShelfParallel (:106) and processShelfDataOptimizedSync while SyncTimeLogger (with logDetail) is injected.

**do**: Replace with syncTimeLogger.logDetail; remove the import; verify the failure is logged once.

---

### 57. user: route achievement calls through UserAchievementsRepository

**rating 46**: evidence 9 · impact 2 · feasibility 9  
**proposed by**: devin swe 2 `R-dev-3` (1.00)

**verified**: ui/user/AchievementViewModel.kt uses achievementUpdates/initializeAchievement/updateAchievement/getAchievementData (all on UserAchievementsRepository, bound at di/RepositoryModule.kt:212) via the full UserRepository.

**do**: Inject UserAchievementsRepository for those four; keep UserRepository for getUserModel/updateProfileFields.

---

### 58. anr: mark ANRWatchdog's cross-thread fields @Volatile

**rating 45**: evidence 8 · impact 2 · feasibility 10  
**proposed by**: devin swe 2 `M-dev-2` (1.00)

**verified**: utils/ANRWatchdog.kt:25-26 isWatching/tick are plain vars written on main (tickUpdater) / caller thread (stop) and read on a default-dispatcher coroutine.

**do**: Mark both @Volatile.

---

### 59. health: load patients through UserRepository in HealthViewModel

**rating 44**: evidence 9 · impact 2 · feasibility 8  
**proposed by**: copilot grok 4.6 `R-grk-4` (1.00)

**verified**: repository/HealthRepositoryImpl.kt:166-179 getPatientById/getPatientsSortedBy/searchPatients only delegate to UserRepository; HealthViewModel already injects UserRepository.

**do**: Call getUserById/getUsersSortedBy/searchUsers directly (blank query -> sorted list); retarget HealthViewModelTest.

---

### 60. settings: show at most 10 retry previews plus a COUNT instead of loading every pending row

**rating 44**: evidence 9 · impact 2 · feasibility 8  
**proposed by**: claude opus 5.5 `M-cld-5` (1.00)

**verified**: repository/RetryRepositoryImpl.kt:184-189 getRetryQueueSnapshot loads getPending() (SELECT * with payloads, no LIMIT); the only consumer (ui/settings/SettingsActivity.kt:130-142) shows take(10) and 'and N more'.

**do**: Add getPendingPreview(now, limit) and getPendingDueCount(now) with getPending's WHERE; add pendingDueCount to RetryQueueDetails; use them in the snapshot and dialog.

---

### 61. test-infra: warn on individual slow test classes in test_timing_summary.py

**rating 44**: evidence 8 · impact 2 · feasibility 9  
**proposed by**: copilot kimi k3 `M-kim-7` (1.00)

**verified**: .github/scripts/test_timing_summary.py only warns on the shard total (--warn-over); no per-class threshold.

**do**: Print a slow-class warning row above a class threshold (e.g. 15 s); stdlib only; do not touch test.yml (PR-owned).

---

### 62. upload: suppress duplicate SyncUiState emissions from the user-data upload scheduler

**rating 44**: evidence 8 · impact 2 · feasibility 9  
**proposed by**: codex sol 5.6 `M-cdx-9` (1.00)

**verified**: services/UserDataUploadScheduler.kt:32-49 maps every WorkInfo emission (progress updates included) to SyncUiState with no distinctUntilChanged; Loading/Success are object/data classes.

**do**: Add distinctUntilChanged() after the map.

---

### 63. anr: measure the reported ANR duration after the wait, not before it

**rating 43**: evidence 5 · impact 3 · feasibility 7  
**proposed by**: copilot gemini 3.8 flash `P-gem-6` (1.00)

**verified**: utils/ANRWatchdog.kt:49-56 captures currentTime before delay(timeout/2) and reports currentTime - lastTick, so the duration excludes the wait (it reports ~timeout/2, not the ~0 ms gemini claims); detection fires at timeout/2.

**do**: Compute duration after the delay; decide explicitly whether detection should use the full timeout. Same file as the @Volatile task.

---

### 64. ci: sparse-checkout only .github/scripts in playstore.yml

**rating 43**: evidence 8 · impact 2 · feasibility 8  
**proposed by**: copilot grok 4.6 `M-grk-1` (1.00)

**verified**: .github/workflows/playstore.yml:54-57 checks out the full repo (fetch-depth 1) for a job that only runs .github/scripts/playstore*.sh; the AAB comes from gh release download.

**do**: sparse-checkout .github/scripts, persist-credentials: false; nothing else changes.

---

### 65. notifications: read notification lists as NotificationPayload projections

**rating 43**: evidence 8 · impact 2 · feasibility 8  
**proposed by**: codex sol 5.6 `R-cdx-1` (1.00)

**verified**: NotificationDao.getNotifications (:36-37) returns AppNotification; NotificationsRepositoryImpl.getNotifications (:137-159) copies 14 fields into NotificationPayload.

**do**: Select the payload columns into NotificationPayload directly; delete the copy; extend DAO/repository tests for filter and ordering.

**watch out**: The saving is mostly code: nearly every column is selected anyway.

---

### 66. tests: drop Robolectric and the real 2 s sleep from RetryInterceptorTest

**rating 43**: evidence 8 · impact 2 · feasibility 8  
**proposed by**: copilot kimi k3 `M-kim-1` (1.00)

**verified**: app/src/test/.../data/api/RetryInterceptorTest.kt:28-29 runs under RobolectricTestRunner; testInterruptedExceptionDuringDelay (:217-222) uses SystemTimeProvider with initialDelay=2000 and Thread.sleep(100). app/build.gradle sets returnDefaultValues = true, so RetryInterceptor's Intent construction works on the plain JVM.

**do**: Remove the runner/@Config; replace the real-time case with a fake TimeProvider whose sleep interrupts.

---

### 67. data: share submission loading and page setup in SubmissionsRepositoryExporter

**rating 41**: evidence 8 · impact 2 · feasibility 7  
**proposed by**: codex sol 5.6 `R-cdx-10` (1.00)

**verified**: repository/SubmissionsRepositoryExporter.kt generateSubmissionPdf and generateMultipleSubmissionsPdf duplicate answer/membership attachment and PdfDocument page setup (:47-73, :132-170).

**do**: Extract private helpers for attaching answers/membership and starting a configured page; keep filenames, page size, ordering and DAO call counts.

---

### 68. notifications: remove the fixed 200 ms sleep before read-receipt broadcasts

**rating 41**: evidence 8 · impact 2 · feasibility 7  
**proposed by**: devin swe 2 `M-dev-10` (1.00)

**verified**: services/NotificationActionReceiver.kt:102-103 withContext(main) { delay(200); ... } after the repository write has already completed.

**do**: Delete the delay; keep broadcast order and try/catch; check NotificationActionReceiverTest first.

---

### 69. retry: let RetryQueueWorker call RetryRepository directly

**rating 41**: evidence 8 · impact 2 · feasibility 7  
**proposed by**: copilot gemini 3.8 flash `R-gem-6` (1.00)

**verified**: services/retry/RetryQueue.kt:20-85 forwards tryStartProcessing/getPendingOperations/cleanup/finishProcessing to RetryRepository; RetryQueueWorker (:42-43) injects both.

**do**: Drop RetryQueue from the worker; update RetryQueueWorkerTest.

**watch out**: RetryQueueTest is owned by #17187 - don't touch it.

---

### 70. sync: snapshot SyncTimeLogger aggregates once per summary

**rating 41**: evidence 8 · impact 2 · feasibility 7  
**proposed by**: codex sol 5.6 `P-cdx-3` (1.00)

**verified**: utils/SyncTimeLogger.kt:235-305 generateSummary re-locks and re-sums each log list several times, and sortedByDescending's comparator re-sums inside synchronized on every comparison.

**do**: Build immutable per-key aggregates once under one lock each, derive totals/sort/rows from them; keep the report text identical.

**watch out**: Coordinate with the lock-free append task (same file); land one first.

---

### 71. chat: narrow ChatViewModel's voices/teams dependencies to share-only interfaces

**rating 40**: evidence 8 · impact 3 · feasibility 3  
**proposed by**: copilot kimi k3 `R-kim-1` (1.00)

**verified**: ui/chat/ChatViewModel.kt uses VoicesRepository only for getPlanetNewsMessages/isAlreadyShared/createNews (:136,:276,:279) and TeamsRepository only for three summary getters (:173-181).

**do**: Introduce narrow interfaces implemented by the existing Impls.

**watch out**: Kimi's plan edits VoicesRepositoryImpl, TeamsRepositoryImpl and RepositoryModule, all owned by open PRs; deliver via interface extension + a new module instead.

---

### 72. data: extract submission upload serialization into SubmissionsUploadSerializer

**rating 40**: evidence 8 · impact 3 · feasibility 3  
**proposed by**: copilot kimi k3 `R-kim-6` (1.00)

**verified**: repository/SubmissionsRepositoryImpl.kt (941 lines) getExamUploadPayload (:793) and serializeSubmission (:864).

**do**: Concrete @Inject collaborator mirroring SubmissionsRepositoryExporter.

**watch out**: File owned by open PRs #14427/#15808.

---

### 73. di: replace NotificationUtils' hidden entry-point singleton with an injectable binding

**rating 40**: evidence 8 · impact 3 · feasibility 3  
**proposed by**: copilot kimi k3 `R-kim-8` (1.00)

**verified**: utils/NotificationUtils.kt:96-104 caches a NotificationManager and fetches TimeProvider via EntryPointAccessors.

**do**: Provide it via Hilt; inject into callers that allow it.

**watch out**: NotificationUtils(+Test) owned by #15825, ServiceModule by #15808.

---

### 74. health: let HealthViewModel own its realtime-sync subscription

**rating 40**: evidence 7 · impact 2 · feasibility 7  
**proposed by**: copilot gemini 3.8 flash `R-gem-4` (1.00)

**verified**: ui/health/HealthViewModel.kt:72 exposes healthSyncUpdates; MyHealthFragment.kt:106-108 collects it only to call viewModel.refreshSelectedPatient().

**do**: Collect inside the VM and drop the public flow and fragment collector.

**watch out**: The fragment collector is lifecycle-aware; a VM collector keeps running while stopped. Use a debounce and accept that or gate it.

---

### 75. voices: move ReplyActivity's member-detail fetch into ReplyViewModel

**rating 40**: evidence 8 · impact 3 · feasibility 3  
**proposed by**: copilot kimi k3 `R-kim-3` (1.00)

**verified**: ui/voices/ReplyActivity.kt:58-59 injects ActivitiesRepository for VoicesActions.showMemberDetails (:195); VoicesRepository (:65-66) is only passed as voicesEditActions (:151).

**do**: Move the fetch into ReplyViewModel; retype the field as VoicesEditActions.

**watch out**: ReplyActivity is owned by open PRs #10993/#17776.

---

### 76. sync: build shelf-sync request bodies with kotlinx directly

**rating 38**: evidence 6 · impact 2 · feasibility 7  
**proposed by**: copilot gemini 3.8 flash `P-gem-3` (0.60)

**verified**: repository/SyncRepositoryImpl.kt:131-143 builds a Gson JsonObject (gson.toJsonTree) then converts it with toKotlinx() per batch.

**do**: buildJsonObject { putJsonArray("keys") { ... } } passed straight to postDoc.

**watch out**: Drop gemini's Semaphore(2): 'unbounded parallelism' is four shelf types; throttling them only slows sync.

---

### 77. data: give CoursesRepository.getMyCoursesFlow a non-suspend signature

**rating 37**: evidence 9 · impact 2 · feasibility 4  
**proposed by**: copilot kimi k3 `R-kim-4` (1.00)

**verified**: repository/CoursesRepository.kt:18 and CoursesRepositoryImpl.kt:117 declare suspend fun returning a Flow.

**do**: Make it a plain fun; only DashboardViewModel.kt:189 calls it.

**watch out**: Both files are owned by open PRs #13657/#15808.

---

### 78. notifications: replace NotificationDao.markSynced's raw CASE query with a typed partial update

**rating 37**: evidence 6 · impact 2 · feasibility 6  
**proposed by**: copilot gemini 3.8 flash `R-gem-9` (0.60)

**verified**: data/room/dao/NotificationDao.kt:73-113 builds UPDATE ... CASE id WHEN ? THEN ? via StringBuilder + @RawQuery.

**do**: @Update(entity = AppNotification::class) with a (id, rev, needsSync) projection.

**watch out**: Skip gemini's observeUnreadCount: it is new unused API.

---

### 79. code-health: collapse TimeProvider's duplicate sleep override

**rating 35**: evidence 10 · impact 1 · feasibility 10  
**proposed by**: copilot kimi k3 `M-kim-2` (1.00)

**verified**: utils/TimeProvider.kt: SystemTimeProvider.sleep repeats the interface default Thread.sleep(millis).

**do**: Delete the override.

---

### 80. data: use the injected TimeProvider for the multi-submission report timestamp

**rating 35**: evidence 10 · impact 1 · feasibility 10  
**proposed by**: codex sol 5.6 `M-cdx-10` (1.00)

**verified**: repository/SubmissionsRepositoryExporter.kt multi-report header uses Instant.now() although TimeProvider is injected and used for single-report filenames.

**do**: Use Instant.ofEpochMilli(timeProvider.now()); make the exporter test assert a fixed header.

---

### 81. tests: make CourseRatingUtilsTest cheap by testing pure formatting

**rating 35**: evidence 5 · impact 2 · feasibility 6  
**proposed by**: copilot kimi k3 `M-kim-6` (0.50)

**verified**: app/src/test/.../utils/CourseRatingUtilsTest.kt (plain JUnit, no Robolectric) relaxed-mocks Context/TextView/AppCompatRatingBar; one test carries a ~15-20 s first-test spike.

**do**: Use kimi's fallback: extract formatting into a pure CourseRatingUtils function and test it without widget mocks.

**watch out**: Kimi's primary fix (relaxed=false) still instruments the widget classes, so it would not remove the cost.

---

### 82. tests: set up Log static mocking once in ConfigurationsRepositoryImplTest

**rating 35**: evidence 5 · impact 2 · feasibility 6  
**proposed by**: copilot gemini 3.8 flash `M-gem-4` (1.00)

**verified**: ConfigurationsRepositoryImplTest.kt:84-93 mockkStatic/unmockkStatic(Log) per test across 42 tests.

**do**: Move to class-level setup.

**watch out**: Speedup is unmeasured; class-level static mocks can leak between tests.

---

### 83. tests: set up WorkManager static mocks once in RetryQueueWorkerTest

**rating 35**: evidence 5 · impact 2 · feasibility 6  
**proposed by**: copilot gemini 3.8 flash `M-gem-5` (1.00)

**verified**: RetryQueueWorkerTest.kt:63-91 mockkObject/mockkStatic per test with unmockkAll.

**do**: Class-level mocks + clearMocks per test.

**watch out**: Speedup is unmeasured.

---

### 84. code-health: remove redundant null-handling in personals, ratings and login-team code

**rating 34**: evidence 9 · impact 1 · feasibility 10  
**proposed by**: claude opus 5.5 `M-cld-3` (1.00)

**verified**: ui/personals/PersonalsFragment.kt:119/:136 personal.id ?: personal._id + if (id != null); ui/ratings/RatingsViewModel.kt:63 user.id?.takeIf; ui/sync/LoginActivity.kt:467 team._id != null (all non-null String).

**do**: Drop the dead null branches without inventing new fallbacks.

---

### 85. code-health: remove redundant null-handling on non-null ids (upload, notifications, labels)

**rating 34**: evidence 9 · impact 1 · feasibility 10  
**proposed by**: claude opus 5.5 `M-cld-2` (1.00)

**verified**: services/upload/UploadConfigs.kt:195 exam.id ?: "" (StepExam.id: String); NotificationsRepositoryImpl.kt:325 user.id?.let (UserEntity.id: String); services/VoicesLabelManager.kt:82-83 voiceId != null (News.id: String).

**do**: Use the ids directly.

---

### 86. data: compare URL schemes against literals, not localized strings, in getMinApk

**rating 34**: evidence 9 · impact 1 · feasibility 10  
**proposed by**: devin swe 2 `M-dev-8` (1.00)

**verified**: repository/ConfigurationsRepositoryImpl.kt:298-301 compares NetworkUtils.extractProtocol(url) (literal "<scheme>://") with context.getString(R.string.http_protocol/https_protocol).

**do**: Compare with "http://"/"https://" constants; keep getString only for the messages.

---

### 87. notifications: narrow NotificationsRepositoryImpl's voices/user dependencies

**rating 34**: evidence 8 · impact 2 · feasibility 3  
**proposed by**: copilot kimi k3 `R-kim-2` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:28-33 injects full VoicesRepository (used only for countTopLevelByTeams, :388) and Lazy<UserRepository> (getUserById/getUsersByIds).

**do**: Narrow reader interfaces; could reuse UserLookupRepository for the user half.

**watch out**: Needs PR-owned VoicesRepositoryImpl/UserRepositoryImpl/RepositoryModule.

---

### 88. code-health: add a shared debounceDistinct operator and clear two FlowPreview warnings

**rating 33**: evidence 9 · impact 1 · feasibility 9  
**proposed by**: claude opus 5.5 `M-cld-7` (1.00)

**verified**: ui/chat/ChatHistoryFragment.kt:89 .debounce(300) and ui/resources/CollectionsFragment.kt:97 .debounce(300L) without @OptIn(FlowPreview).

**do**: Add Flow<T>.debounceDistinct(ms) with the opt-in to utils/ViewExtensions.kt; use it in the two fragments; add a ViewExtensionsTest case.

---

### 89. community: drop the redundant IO hop in CommunityServicesViewModel

**rating 33**: evidence 9 · impact 1 · feasibility 9  
**proposed by**: copilot grok 4.6 `R-grk-8` (1.00)

**verified**: ui/community/CommunityServicesViewModel.kt:30-36 wraps suspend DAO-backed getTeamLinks/isMember in launch(io)/withContext(io).

**do**: Call them directly; drop DispatcherProvider if unused.

---

### 90. health: stop using the getHealthEntry pair in HealthExaminationViewModel

**rating 33**: evidence 9 · impact 1 · feasibility 8  
**proposed by**: copilot grok 4.6 `R-grk-5` (1.00)

**verified**: ui/health/HealthExaminationViewModel.kt:63 unpacks healthRepository.getHealthEntry (= userRepository.getUserById + getByIdOrUserId, HealthRepositoryImpl.kt:41-46).

**do**: Call the two methods directly; update the getHealthEntry stubs.

---

### 91. tests: clear the compiler warnings in three test files

**rating 33**: evidence 9 · impact 1 · feasibility 9  
**proposed by**: claude opus 5.5 `M-cld-9` (1.00)

**verified**: TeamChatBadgeIntegrationTest.kt:43 unopted UnconfinedTestDispatcher; ServerAddressAdapterTest.kt:54,65 useless casts; FileUtilsTest.kt:456,493 deprecations.

**do**: Add the opt-in, drop the casts, use IntentCompat.getParcelableExtra, remove or suppress the shadowOf(MimeTypeMap) line.

---

### 92. upload: deduplicate DownloadUtils' twin enqueue blocks

**rating 33**: evidence 9 · impact 1 · feasibility 9  
**proposed by**: devin swe 2 `M-dev-7` (1.00)

**verified**: utils/DownloadUtils.kt:146-174 openPriorityDownloadService/openDownloadService repeat get-set-merge-write; ~8 trailing blank lines at EOF.

**do**: Extract a private enqueueUrls(context, key, urls); delete trailing blanks.

---

### 93. courses: log course visits through ActivitiesRepository in TakeCourseViewModel

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: copilot grok 4.6 `R-grk-3` (1.00)

**verified**: ui/courses/TakeCourseViewModel.kt:55-56 calls coursesRepository.logCourseVisit, which only forwards to activitiesRepository.logCourseVisit (CoursesRepositoryImpl.kt:589-591).

**do**: Inject ActivitiesRepository and call it.

**watch out**: Grok says CoursesRepository.logCourseVisit has other callers; it has none, so it becomes dead (CoursesRepositoryImpl is PR-owned; leave it for now).

---

### 94. health: drop dead string concatenations and reorder HealthExaminationAdapter's diff

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: copilot kimi k3 `P-kim-7` (1.00)

**verified**: ui/health/HealthExaminationAdapter.kt:126-132 value.toString() + ""; DIFF_CALLBACK :178-191 compares examination.data mid-chain.

**do**: Drop the + ""; compare data last.

---

### 95. login: return early from LoginSyncManager.isManager

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: copilot grok 4.6 `M-grk-8` (1.00)

**verified**: services/sync/LoginSyncManager.kt:141-150 scans every role and only then checks isUserAdmin.

**do**: Return on isUserAdmin or on the first manager role.

---

### 96. notifications: build join-request details without the intermediate Triple list

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: codex sol 5.6 `M-cdx-7` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:299-336 getJoinRequestDetailsBatch builds ArrayList<Triple> then walks it twice.

**do**: Index team/user maps and build the result in one pass; keep Unknown Team/User fallbacks.

---

### 97. ui: cache formatted dates and hoist listeners in SubmissionsListAdapter and FeedbackReplyAdapter

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: devin swe 2 `P-dev-3` (1.00), copilot kimi k3 `P-kim-5` (0.50)

**verified**: ui/submissions/SubmissionsListAdapter.kt:35,40-46 formats the date and allocates two listeners per bind; ui/feedback/FeedbackReplyAdapter.kt:21-23 parses and formats per bind.

**do**: Add date caches (bounded for the reply adapter) and move listeners to init.

---

### 98. ui: cache per-bind resource lookups in Onboarding, CoursesSteps and ChatShareTarget adapters

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: devin swe 2 `P-dev-10` (1.00)

**verified**: ui/onboarding/OnboardingAdapter.kt:28,30 getColor x2 per page; ui/courses/CoursesStepsAdapter.kt:53 getString per bind; ui/chat/ChatShareTargetAdapter.kt:38,41 listeners per bind.

**do**: Hoist the color, cache the template, move the listeners to init.

---

### 99. ui: hoist per-bind listeners in PersonalsAdapter, StorageCategoryDetail's ResourceAdapter and ReferencesAdapter

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: devin swe 2 `P-dev-4` (0.70), copilot kimi k3 `P-kim-8` (0.60)

**verified**: ui/personals/PersonalsAdapter.kt:40-63 allocates four listeners per bind; ui/settings/StorageCategoryDetailFragment.kt:185-207 two per bind plus duplicates in the payload bind; ui/references/ReferencesAdapter.kt:28-36 one per bind.

**do**: Move listeners to ViewHolder init resolving the item via bindingAdapterPosition with NO_POSITION guards.

---

### 100. ui: hoist the startSurvey listener and label strings in SurveysAdapter

**rating 32**: evidence 8 · impact 1 · feasibility 9  
**proposed by**: devin swe 2 `P-dev-5` (1.00)

**verified**: ui/surveys/SurveysAdapter.kt:86 startSurvey listener per bind; :103-105 three getString per bind.

**do**: Move the listener to init; cache the labels.

---

### 101. life: drop the redundant IO hops in LifeViewModel

**rating 31**: evidence 8 · impact 1 · feasibility 8  
**proposed by**: copilot grok 4.6 `R-grk-7` (1.00)

**verified**: ui/life/LifeViewModel.kt:35,45,55 wrap suspend LifeRepository calls in withContext(io).

**do**: Call them directly; drop DispatcherProvider if unused.

**watch out**: updateVisibility/updateMyLifeListOrder also run LifeCache JSON encoding; tiny, but it moves to main.

---

### 102. life: hoist LifeAdapter's per-bind listeners onto the ViewHolder

**rating 31**: evidence 8 · impact 1 · feasibility 8  
**proposed by**: copilot grok 4.6 `P-grk-9` (1.00)

**verified**: ui/life/LifeAdapter.kt:62-75 installs click/touch/visibility listeners per bind (7-row list).

**do**: Create listeners once per holder; resolve the item with bindingAdapterPosition.

---

### 103. life: make LifeCache entries immutable and stop copying on every read

**rating 31**: evidence 8 · impact 1 · feasibility 8  
**proposed by**: copilot grok 4.6 `P-grk-5` (1.00)

**verified**: repository/LifeCache.kt:29-41 read() returns map { it.copy() } on every hit because CachedMyLifeItem fields are var.

**do**: Make the fields val, return the cached list, replace the mutation test with an immutability test.

---

### 104. ui: hoist per-bind listeners and constant styling in ResourcesTagsAdapter

**rating 31**: evidence 8 · impact 1 · feasibility 8  
**proposed by**: devin swe 2 `P-dev-7` (1.00)

**verified**: ui/resources/ResourcesTagsAdapter.kt:79,85,99 listeners per bind; :97-98 cached colors re-applied per bind; :116-118 checkbox listener re-allocated.

**do**: Move listeners and constant colors to init; one shared checkbox listener.

---

### 105. ui: hoist per-bind listeners in ChatHistory, Checkbox and TeamsSelection adapters

**rating 31**: evidence 8 · impact 1 · feasibility 8  
**proposed by**: devin swe 2 `P-dev-8` (1.00)

**verified**: ui/chat/ChatHistoryAdapter.kt:123,137-139 (incl. constant setImageResource); ui/components/CheckboxAdapter.kt:39; ui/teams/TeamsSelectionAdapter.kt:42-46.

**do**: Move listeners and the constant drawable to init.

---

### 106. ui: hoist per-bind listeners in NotificationsAdapter

**rating 31**: evidence 8 · impact 1 · feasibility 8  
**proposed by**: devin swe 2 `P-dev-9` (1.00)

**verified**: ui/notifications/NotificationsAdapter.kt:104,134,140-145 allocate listeners per bind.

**do**: Move them to the two ViewHolders' init keeping the conditional callbacks.

---

### 107. code-health: fix the dead toast catch and qualified delay in utils

**rating 30**: evidence 6 · impact 1 · feasibility 9  
**proposed by**: devin swe 2 `M-dev-4` (1.00)

**verified**: utils/Utilities.kt:62 catches IllegalAccessException that Toast.show never throws; RetryUtils.kt:26 calls kotlinx.coroutines.delay fully qualified; KeyboardUtils.kt:16 and RetryUtils.kt:29 printStackTrace.

**do**: Catch Exception (log via Log.w), import delay.

**watch out**: The 'stderr is lost in release logcat' rationale is wrong: Android routes System.err to logcat; the printStackTrace swaps are cosmetic.

---

### 108. courses: make CoursesPagerAdapter.containsItem a set lookup

**rating 30**: evidence 7 · impact 1 · feasibility 8  
**proposed by**: codex sol 5.6 `M-cdx-2` (1.00)

**verified**: ui/courses/CoursesPagerAdapter.kt:67-69 scans all steps per containsItem call (lists are tens of steps).

**do**: Maintain a membership set rebuilt in submitList.

---

### 109. dashboard: configure the activities chart once, update data per emission

**rating 30**: evidence 7 · impact 1 · feasibility 8  
**proposed by**: copilot kimi k3 `P-kim-9` (1.00)

**verified**: ui/dashboard/ActivitiesFragment.kt renderChart re-applies axis/legend/ValueFormatter on every monthlyLoginCounts emission (emissions are rare).

**do**: Split into configureChartOnce + updateChartData.

---

### 110. sync: drop per-event synchronizedList wrappers in SyncTimeLogger

**rating 30**: evidence 8 · impact 1 · feasibility 7  
**proposed by**: devin swe 2 `P-dev-6` (1.00)

**verified**: utils/SyncTimeLogger.kt:170,187 computeIfAbsent { Collections.synchronizedList(...) }.add per API/DB event.

**do**: Use ConcurrentLinkedQueue values.

**watch out**: Uncontended locks cost nanoseconds next to network calls; generateSummary's synchronized blocks must change with it.

---

### 111. teams: make TeamPagerAdapter.containsItem a set lookup

**rating 30**: evidence 7 · impact 1 · feasibility 8  
**proposed by**: codex sol 5.6 `M-cdx-3` (1.00)

**verified**: ui/teams/TeamPagerAdapter.kt:62-64 pages.any per call (a dozen pages at most).

**do**: Maintain a membership set updated in updatePages.

---

### 112. ci: read release step conclusions from the jobs listing in playstore.sh

**rating 29**: evidence 6 · impact 1 · feasibility 8  
**proposed by**: copilot gemini 3.8 flash `M-gem-1` (1.00)

**verified**: .github/scripts/playstore.sh:96-102 lists runs/$id/jobs then calls actions/jobs/$job_id for the same job's steps; the listing already includes steps[].

**do**: Select the step conclusion from the first response.

**watch out**: It is one redundant call per check, not a per-job loop.

---

### 113. storage: pass FreeSpaceWorker's batch without a defensive toSet() copy

**rating 29**: evidence 7 · impact 1 · feasibility 7  
**proposed by**: codex sol 5.6 `M-cdx-6` (1.00)

**verified**: services/FreeSpaceWorker.kt:47-49 copies <=25 ids with toSet() before each markResourcesAsNotOffline call.

**do**: Pass the set and clear after the call returns; capture arguments in the test.

**watch out**: The copy is a cheap safety snapshot; the gain is negligible.

---

### 114. data: reuse empty JsonObject/JsonArray singletons in JsonUtils

**rating 28**: evidence 5 · impact 1 · feasibility 9  
**proposed by**: copilot gemini 3.8 flash `P-gem-9` (0.50)

**verified**: utils/JsonUtils.kt getJsonObject/getJsonArray allocate JsonObject(emptyMap())/JsonArray(emptyList()) per miss (immutable, safe to share).

**do**: private val EMPTY_* constants.

**watch out**: The 'numeric fast path' part is already there (longOrNull ?: ... short-circuits); JsonUtils is used in 10 files, not 'all DAOs'.

---

### 115. courses: bind course covers with the ImageView as Glide's lifecycle owner

**rating 27**: evidence 5 · impact 1 · feasibility 8  
**proposed by**: copilot grok 4.6 `P-grk-7` (1.00)

**verified**: utils/CoursesItemUtils.kt:111 Glide.with(context) instead of the ImageView.

**do**: Glide.with(ivCover).

**watch out**: Grok's second claim is wrong: the when() at :92-101 already prefers the measured width and only computes columns when unmeasured.

---

### 116. dictionary: assign deterministic dictionary ids instead of a UUID per word

**rating 27**: evidence 7 · impact 1 · feasibility 5  
**proposed by**: copilot grok 4.6 `P-grk-10` (1.00)

**verified**: repository/DictionaryMapper.kt:15 UUID.randomUUID() per word during the one-time seed.

**do**: Derive the id from existing fields.

**watch out**: If the JSON contains repeated words, a deterministic key plus @Insert(REPLACE) silently collapses them; check the data first.

---

### 117. life: replace LifeAdapter's getIdentifier lookup with a compile-time map

**rating 27**: evidence 5 · impact 1 · feasibility 8  
**proposed by**: copilot kimi k3 `P-kim-6` (1.00)

**verified**: ui/life/LifeAdapter.kt:52-57 resources.getIdentifier(imgId, "drawable", ...) (already memoized by drawableCache, so this is lint/shrinker hygiene, not a hot path).

**do**: Map the seven known keys to R.drawable ids; keep getIdentifier as the fallback.

**watch out**: Kimi's resId=0 rule is self-contradictory: skipping setImageResource on a miss leaves a recycled holder's old icon instead of clearing it.

---

### 118. retry: make the RetryInterceptor retry-safety check and broadcast extras cheaper

**rating 26**: evidence 5 · impact 1 · feasibility 7  
**proposed by**: copilot grok 4.6 `M-grk-7` (1.00)

**verified**: data/api/RetryInterceptor.kt:27-30 endsWith over three suffixes; :62-65 Intent extras with the full URL.

**do**: pathSegments check; put encodedPath in the extra.

**watch out**: Better follow-up: ACTION_RETRY_EVENT has no receiver anywhere in app/src/main, so the broadcast itself is dead code.

---

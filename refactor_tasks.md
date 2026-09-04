### 1. Route My Life dashboard clicks off the stable imageId, not the localized title

**rating:** 92/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** ui/dashboard/DashboardPluginFragment.kt (handleClickMyLife, getLayout)

**why:** handleClickMyLife matches `when (title)` against hardcoded English literals ("mySubmissions", "myHealth", "Calendar", "mySurveys", "myAchievements", "myPersonals", "References"). The title it receives is DashboardItem.title, i.e. the localized string from MyLife.defaultItems (R.string.submission = "mySubmissions" in en, "misEnvios" in es, "mesSoumissions" in fr, Arabic/Nepali/Somali likewise). On any non-English locale every My Life shortcut falls into `else -> toast(feature_not_available)`. VERIFIED: literals match values/strings.xml exactly and diverge in all five values-<lang>/strings.xml.

**work:** Add an internal pure `myLifeRouteFor(imageId: String?)` mapping the stable ids already used by imageResourceMap (ic_submissions, ic_references, ic_calendar, ic_my_survey, my_achievement, ic_mypersonals, ic_myhealth) to route descriptors; pass obj.imageId into handleClickMyLife instead of obj.title; keep per-route openIfLoggedIn gating and the feature_not_available toast for unknown ids. Unit-test every known id plus blank/unknown.

**note:** Highest user impact in the whole set. Note rows seeded under one locale keep that locale's title in the DB, which is exactly why title is unusable as a key.

---

### 2. Move the achievement CV file copy off the main thread

**rating:** 86/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (perf)

**files:** ui/user/EditAchievementFragment.kt (computeCvFilename + its call site at line 178)

**why:** The save handler runs `lifecycleScope.launch { val cvFilename = computeCvFilename() ... }` on the main dispatcher, and computeCvFilename does contentResolver.openInputStream(uri) + input.copyTo(output) - copying a user-picked PDF of arbitrary size on the UI thread. Debug builds run StrictMode detectAll, so this logs disk-read/write violations on every CV save. VERIFIED.

**work:** Make computeCvFilename suspend and wrap the uri-check/file-copy body in withContext(dispatcherProvider.io) using the dispatcherProvider already injected by BaseContainerFragment; keep the deleteCv early return and the resumeFileName fallback on the calling dispatcher.

---

### 3. Stop counting team chat messages for teams that have no chat badge

**rating:** 85/100 &nbsp;·&nbsp; **proposed by:** claude (perf), copilot-kimi (boundaries), copilot-kimi (perf), copilot-grok (perf)  _(4 lists)_

**files:** repository/NotificationsRepositoryImpl.kt (getTeamNotifications, lines 309-312)

**why:** getTeamNotifications runs one `SELECT COUNT(*)` per team in `for (teamId in teamIds) { chatCountsById[teamId] = voicesRepository.countTopLevelByTeam(teamId) }`, but the count is only ever consumed as `notification != null && notification.lastCount < chatCount`. Every team without a chat row in teamNotificationDao.getByTypeAndParentIds("chat", teamIds) pays for a query whose result is discarded. Runs on the dashboard team strip: 20 teams with 2 chat-tracked teams issues 20 queries where 2 suffice. VERIFIED.

**work:** Build notificationsById first (already done), then loop only over notificationsById.keys for the counts; keep chatCountsById typed Map<String, Long> and the `?: 0L` fallback so teams with no row still report hasChat = false. Assert with MockK coVerify(exactly = 1) that only the chat-tracked team is counted.

**note:** Four agents proposed four different fixes to this hotspot; this one is the smallest and cannot change behaviour. The batch-DAO variants (copilot) need a GROUP BY that drops the `viewIn LIKE :teamPattern` legacy branch of countTopLevelByTeam - only worth doing after that branch is proven dead.

---

### 4. Cache the resolved CouchDB base URL and close the three missing cache invalidations

**rating:** 82/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** utils/UrlUtils.kt, services/SharedPrefManager.kt (setCouchdbUrl, setProcessedAlternativeUrl, setIsAlternativeUrl)

**why:** UrlUtils.getUrl() -> dbUrl(spm()) -> baseUrl(spm) does an isAlternativeUrl() read plus one or two SharedPreferences string reads, a suffix strip and two concatenations on every call, and it is on genuinely hot paths (every serialized upload document, getUserImageUrl/getCourseImageUrl per avatar row, getUrl(resource) per resource bind). The object already caches the cheaper Basic auth header behind a @Volatile generation counter. VERIFIED: setUrlUser/setUrlPwd call invalidateHeaderCache(); setCouchdbUrl/setProcessedAlternativeUrl/setIsAlternativeUrl do not.

**work:** Add a @Volatile cachedBaseUrl cleared by the existing invalidateHeaderCache() and resetForTesting(); give baseUrl(spm) the same read/compute/compare-generation/store shape as the header getter; add invalidateHeaderCache() to the three setters. Test that a preference change plus invalidation returns the new URL and that repeated getUrl() hits preferences once.

**note:** The invalidation gap must land in the same change or a server failover will keep hitting the old host.

---

### 5. Move the inline-resource download check off the main thread

**rating:** 82/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** ui/courses/InlineResourceAdapter.kt (updateStatusAndPreview)

**why:** updateStatusAndPreview is called straight from onBindViewHolder and evaluates FileUtils.checkFileExist(context, UrlUtils.getUrl(resource)), which does f.exists() && f.length() > 0 - two disk stats per row, per bind, on the UI thread. Every sibling preview path in the same class already does withContext(dispatcherProvider.io) { file.exists() }. VERIFIED.

**work:** Default the row to its not-downloaded state synchronously, then move the `resource.isResourceOffline() || checkFileExist(...)` evaluation inside the existing holder.setPreviewJob(adapterScope.launch { ... }) with the stat wrapped in withContext(io), short-circuiting on isResourceOffline() first. Keep cancelPreviousPreviews() where it is so recycled rows cancel stale work.

---

### 6. Drain the retry queue with bounded concurrency instead of one request at a time

**rating:** 80/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** services/retry/RetryQueueWorker.kt (doWork)

**why:** doWork chunks pending operations into batches of 50 and then processes each batch with `batch.forEach { processOperation(...) }` - fully sequential network round trips, so the chunking buys nothing and a backlog of 200 failed uploads takes 200 serial requests inside a 5-minute withTimeout that will often expire first. UploadCoordinator in the same codebase already solves this with async + Semaphore(MAX_CONCURRENT_UPLOADS = 6). VERIFIED.

**work:** Add MAX_CONCURRENT_RETRIES = 6 next to BATCH_SIZE, create the Semaphore once before the chunked loop, and replace the inner forEach with coroutineScope { batch.map { async { semaphore.withPermit { processOperation(...) } } }.awaitAll() }. Replace the now-racy successCount++/failureCount++ with counts over the awaited List<Boolean>. Keep the isSyncRunning bail-out between batches.

---

### 7. Serialize upload payloads per batch instead of all up front

**rating:** 79/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** services/upload/UploadCoordinator.kt (runPipeline, queryItemsToUpload, queueRetryableFailures)

**why:** runPipeline calls queryItemsToUpload(config) which serializes every pending item into a JsonObject before the first request goes out, then uploads in chunks of config.batchSize. On a device offline for weeks that means thousands of live JsonObject trees held at once - the peak-memory spike lands on exactly the low-end hardware this app targets - and the first byte does not leave the device until the last item is serialized. VERIFIED.

**work:** Fetch config.fetchPendingItems() once, early-return Empty, then chunk the raw items and prepare each chunk at the top of the loop body; keep the shouldFilter and serialization-failure mapNotNull behaviour byte-identical. Move queueRetryableFailures inside the loop (per batch) while still accumulating allSucceeded/allFailed for the final result, and log the raw pending count before the loop so log output is unchanged.

**note:** Moving queueRetryableFailures per batch is a real semantic change to failure queueing - cover it with a test where batch 1 fails retryably and batch 2 succeeds.

---

### 8. Parallelize the PhotoUploader batch POSTs

**rating:** 78/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** services/upload/PhotoUploader.kt (uploadSubmitPhotos)

**why:** uploadSubmitPhotos chunks by BATCH_SIZE and then runs `batch.forEach { ... uploadRepository.postUpload(...) }` sequentially; the per-photo uploadAttachment calls after the batch mark are sequential too. Wall-clock upload time scales linearly with photo count on slow links. VERIFIED.

**work:** Within each batch run the POSTs with bounded concurrency using UploadCoordinator's proven Semaphore + async pattern (cap <= 6), collecting successes into a thread-safe list, then keep the single markPhotosUploadedBatch call. Preserve failure isolation - one failure must not cancel the batch - and the listener callbacks.

---

### 9. Cache the invariant device identity and system services in NetworkUtils

**rating:** 78/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** utils/NetworkUtils.kt (getDeviceName, getUniqueIdentifier, isWifiEnabled, isBluetoothEnabled)

**why:** getDeviceName() re-reads Build.MANUFACTURER/Build.MODEL, concatenates and uppercases on every call, and it is called once per serialized document during uploads (Personal, NewsLog, ApkLog, Rating, CourseActivity, MyLibrary, SearchActivity, MyPlanet, ActivitiesRepositoryImpl, UploadManager), so a bulk activity upload allocates thousands of throwaway strings. getUniqueIdentifier() rebuilds the same androidId + "_" + buildId each call; isWifiEnabled()/isBluetoothEnabled() call getSystemService every invocation. The file already has a ResettableCache helper and a connectivityManager cache showing the intended pattern. VERIFIED (10 call sites).

**work:** Add ResettableCache entries for deviceName, uniqueIdentifier, wifiManager and bluetoothManager; keep isWifiEnabled/isBluetoothEnabled reading the live enabled state off the cached manager so it stays dynamic; register all four in resettableCaches so resetForTesting() still clears them.

---

### 10. Add an ASCII fast path to Utilities.normalizeText

**rating:** 78/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** utils/Utilities.kt (normalizeText)

**why:** normalizeText runs Normalizer.normalize(str.lowercase(locale), NFD) followed by .replace(DIACRITICS_REGEX, ""), which allocates a fresh Matcher on every call. It is the per-item primitive of offline search and course filtering - every candidate title and every query token goes through it on every keystroke (call sites in MyLibrary, SurveysViewModel, CoursesRepositoryImpl x4, ResourcesRepositoryImpl) - yet for pure-ASCII titles NFD decomposition and the combining-marks strip are guaranteed no-ops. VERIFIED.

**work:** Lowercase once, return immediately when every char is < 0x80, otherwise fall through to the existing path. Test the fast path (mixed case, empty, digits/punctuation) and the slow path (Cafe, Nino, aeiou, Arabic and Nepali round-trips).

---

### 11. Stop re-reading SharedPreferences and rebuilding the notification builder on every download tick

**rating:** 77/100 &nbsp;·&nbsp; **proposed by:** claude (perf), openhands-kimi (perf), codex (perf)  _(3 lists)_

**files:** services/DownloadService.kt (getRemainingCount, sendNotification, updateNotificationForBatchDownload, processDownloadQueue, downloadFile)

**why:** sendNotification fires every 500 ms (NOTIFICATION_UPDATE_INTERVAL_MS) while a file downloads and calls getRemainingCount(), which does two preferences.getStringSet reads plus a union-set allocation, then filters against processedUrls - purely to render the "N remaining" subtext. The queue only changes between files (processedUrls.add / cleanupProcessedUrls). Each tick also re-allocates NotificationManagerCompat.from(this). updateNotificationForBatchDownload re-runs DownloadUtils.createChannels(this) and constructs a fresh NotificationCompat.Builder once per file. Separately, downloadFile wraps the body in BufferedInputStream(body.byteStream(), 1024 * 8) while reading into a 1024 * 16 BUFFER_SIZE array, so reads bypass the wrapper. ALL VERIFIED.

**work:** Cache the remaining count in a @Volatile field refreshed at the two points where the queue actually changes; read the cache in sendNotification and updateNotificationForBatchDownload (leave onDownloadComplete calling getRemainingCount(priorityUrls) directly - it needs the post-download value); create the channel and builder once per queue session and mutate the existing builder per file; hoist the areNotificationsEnabled() check out of the tick; raise the BufferedInputStream buffer to BUFFER_SIZE. Keep every notification ID, channel ID and subtext format byte-identical.

**note:** Three agents found overlapping slices of this; the union above is the whole finding. Do not also take the 'parallel downloads' variant in the same change - that is a separate, much riskier task.

---

### 12. Push the archived-report filter and sort of the finances CSV export into SQL

**rating:** 76/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries), openhands-glm (boundaries), devin (boundaries), copilot-grok (boundaries), devin (perf)  _(5 lists)_

**files:** data/room/dao/TeamDao.kt (one new @Query), repository/EnterprisesRepositoryImpl.kt (exportReportsAsCsv, lines 102-105)

**why:** exportReportsAsCsv reads every `teams` row for the team with docType='report' via getByTeamIdAndDocType, then drops archived rows and sorts by createdDate in Kotlin. The identical predicate already exists in SQL one line above in the same DAO: observeNonArchivedReportsByTeamId uses `IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`. VERIFIED both.

**work:** Add `@Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC") suspend fun getNonArchivedReportsByTeamId(teamId: String): List<MyTeam>` and call it. Adding a @Query changes no table, so no AppDatabase version bump.

**note:** The IFNULL matters: the current Kotlin `it.status != "archived"` keeps null-status rows, so a bare `status != 'archived'` would silently drop them. Two agents caught that; one proposed a plain sequence conversion of the same lines instead, which buys nothing - prefer the SQL.

---

### 13. Aggregate the most-opened resource in SQL instead of loading the whole activity table

**rating:** 73/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf), copilot-grok (boundaries)  _(2 lists)_

**files:** data/room/dao/ResourceActivityDao.kt, repository/ActivitiesRepositoryImpl.kt (getMostOpenedResource)

**why:** getMostOpenedResource calls resourceActivityDao.getByUserAndType(userName, type) - a plain `SELECT *` - then groupBy/mapValues/maxByOrNull in memory on the default dispatcher. Large offline histories make the profile screen O(n) in rows for a single winner. The DAO has a countByUserAndType but no aggregate. VERIFIED.

**work:** Add a @Query grouping by resourceId with COUNT(*), ordered by count desc and a documented stable tie-break, LIMIT 1, skipping null titles; map to the existing Pair<String, Int>? contract with empty -> null and delete the in-memory grouping. No schema version bump.

---

### 14. Run the independent team-tab ViewModel queries concurrently

**rating:** 73/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries), openhands-kimi (perf), openhands-kimi (perf)  _(3 lists)_

**files:** ui/teams/members/RequestsViewModel.kt (fetchMembers), ui/teams/courses/TeamCoursesViewModel.kt (loadCourses), ui/teams/resources/TeamResourcesViewModel.kt (loadResources)

**why:** Three team-tab ViewModels await independent suspend repository calls strictly sequentially. RequestsViewModel.fetchMembers awaits getRequestedMembers, getJoinedMemberCount and getUserModel in sequence though none depends on another (only isTeamLeader needs the user). TeamCoursesViewModel.loadCourses awaits getTeamCourseIds -> getCoursesByIds -> getTeamCreator, where the creator lookup is independent of the ids. TeamResourcesViewModel.loadResources awaits getTeamResources then isTeamLeader, which are independent. ALL VERIFIED.

**work:** Wrap each load body in coroutineScope { } with async per independent call, keeping every real dependency edge (getCoursesByIds still awaits the ids; isTeamLeader still awaits the user). Preserve exactly one MutableStateFlow assignment per load with identical content - no intermediate emissions.

**note:** A fourth ViewModel was proposed for the same treatment (CommunityTabViewModel) and dropped: three of its four reads are non-suspend SharedPreferences getters.

---

### 15. Guard SyncManager's SyncPerf logging behind Log.isLoggable

**rating:** 73/100 &nbsp;·&nbsp; **proposed by:** claude (perf)

**files:** services/sync/SyncManager.kt (the Log.d("SyncPerf", ...) statements only)

**why:** VERIFIED exactly 20 unconditional Log.d("SyncPerf", ...) calls with string interpolation and zero isLoggable guards (lines 139-141, 207-217, 274, 305, 382, 422, 427, 505, 516, 558, 563). Line 382 fires once per resource batch and interpolates a percentage computation; 139/208 format an Instant on every sync. SyncTimeLogger in the same subsystem already wraps all six of its Log.d calls in `if (Log.isLoggable("SyncPerf", Log.DEBUG))`, so SyncManager is the odd one out and a release device pays for the formatting.

**work:** Add `private inline fun syncPerf(message: () -> String)` guarded by isLoggable and route all 20 sites through it, keeping message text byte-identical. Wrap the three multi-line banner blocks in a single guard so the Instant formatting is skipped too. Leave every Log.e/Log.w and the syncTimeLogger.logDetail calls alone.

---

### 16. Honour the existing News parse caches in imagesArray and isCommunityNews

**rating:** 73/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** model/News.kt (imagesArray, isCommunityNews, messageWithoutMarkdown)

**why:** The @Ignore fields parsedImagesArray/rawImages and parsedViewIn/rawViewIn exist and are maintained by VoicesAdapter (lines 610-614, 638-640) and read by VoicesRepositoryImpl and JsonUtils, and calculateSortDate already prefers parsedViewIn. But the imagesArray getter always runs JsonUtils.gson.fromJson(images, ...) and isCommunityNews always re-parses viewIn, ignoring both caches. VERIFIED.

**work:** Lazy-fill parsedImagesArray on first access and reuse it while rawImages == images; do the same for isCommunityNews against parsedViewIn/rawViewIn. Stay null/empty/JsonSyntaxException safe (empty array / false, no crash) and change no Room field or @Entity shape.

**note:** Conflicts with the 'delete messageWithoutMarkdown' task below - land that one first, then this one has one less consumer to cache for.

---

### 17. Replace the personals read-modify-write update with one targeted SQL UPDATE

**rating:** 72/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries), openhands-glm (boundaries)  _(2 lists)_

**files:** data/room/dao/PersonalDao.kt (one new @Query), repository/PersonalsRepositoryImpl.kt (updatePersonalResource)

**why:** updatePersonalResource issues up to two SELECTs (`findByDocId(id) ?: findById(id)`), mutates the loaded Personal in memory, then writes the whole row back with @Update. Editing a title therefore rewrites every column - including path, _rev and isUploaded - from a snapshot read microseconds earlier: three statements where one suffices, plus a lost-update window against the sync writer that touches isUploaded/_rev via updateUploadedStatus. VERIFIED.

**work:** Add `@Query("UPDATE my_personal SET title = COALESCE(:title, title), description = COALESCE(:description, description) WHERE _id = :id OR id = :id")` - the WHERE mirrors the existing deleteByIdOrDocId so both id shapes resolve, and COALESCE preserves PersonalUpdate's "null means leave unchanged" contract. Reduce the repository method to that single call. Leave findByDocId/findById/update in place for savePersonalResource and the upload path.

**note:** A weaker variant of the same finding only merged the two SELECTs into one; the single UPDATE removes all three statements and the race.

---

### 18. Stop leaking the Room DictionaryEntity into the dictionary UI

**rating:** 71/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries), copilot-grok (boundaries)  _(2 lists)_

**files:** repository/DictionaryRepository.kt, repository/DictionaryRepositoryImpl.kt (findByWord), ui/dictionary/DictionaryViewModel.kt

**why:** DictionaryRepository.findByWord returns the Room @Entity itself and DictionaryViewModel imports it from the persistence package, storing it in UI state as DictionarySearchState.Found(entry: DictionaryEntity). VERIFIED: `grep -rn 'data.room.entity' app/src/main/java | grep -v /data/room/ | grep -v /repository/` returns exactly one hit - this ViewModel - so closing it makes that boundary absolute.

**work:** Declare a plain `data class DictionaryWord(word, meaning, definition, synonym, antonym)` next to the existing DictionaryLoad sealed interface, change the signature, map inside findByWord, and hold DictionaryWord in Found. DictionaryActivity.renderSearchState reads only word/definition/synonym/antonym and compiles untouched.

---

### 19. Drop the false suspend on getReportsFlow and stop nesting the lifecycle-aware collector

**rating:** 71/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries)

**files:** ui/enterprises/EnterprisesViewModel.kt (getReportsFlow), ui/enterprises/EnterprisesReportsFragment.kt (lines ~126-131 and the export block)

**why:** EnterprisesViewModel.getReportsFlow is declared suspend even though it only returns the cold Flow from EnterprisesRepository.getReportsFlow, which is correctly non-suspend. VERIFIED. The false suspend forces the fragment into `viewLifecycleOwner.lifecycleScope.launch { val flow = viewModel.getReportsFlow(teamId); collectLatestWhenStarted(flow) { ... } }`, so a lifecycle-aware collector is nested inside a plain launch that outlives STARTED - a leaked second collector after rotation.

**work:** Remove suspend, keeping the Flow<List<MyTeam>> return type, and collapse the fragment to a single collectLatestWhenStarted(viewModel.getReportsFlow(teamId)) { ... }. Leave exportReportsAsCsv genuinely suspend on both sides.

---

### 20. Remove the dead eager fullName read from UserSessionManager's constructor

**rating:** 70/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** services/UserSessionManager.kt (lines ~27-36)

**why:** UserSessionManager declares `private val fullName: String` and populates it in an init block from sharedPrefManager.getUserName(), wrapped in a catch that only rethrows. VERIFIED: the only two references to fullName in the file are its declaration and that assignment - nothing reads it - so every injection of this singleton performs a SharedPreferences read whose result is discarded, and makes the class fragile to construct before login.

**work:** Delete the property and the whole init block; drop any test stub that existed only to satisfy the removed read, and keep a construction test green against an unstubbed SharedPrefManager mock to prove construction no longer touches preferences.

---

### 21. Move the shelves-with-data preference cache out of SyncManager into SyncRepository

**rating:** 70/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries)

**files:** repository/SyncRepository.kt, repository/SyncRepositoryImpl.kt, services/sync/SyncManager.kt (getCachedShelvesWithData, cacheShelvesWithData, getShelvesWithDataBatchOptimized)

**why:** SyncManager owns a hand-rolled 6-hour SharedPreferences cache of which shelves hold data: the two private functions each re-declare the string keys "shelves_with_data" and "shelves_cache_time", do their own TTL arithmetic (cacheValidityHours * 60 * 60 * 1000) and split/join a comma-separated list. VERIFIED. That is persistence logic - key names, encoding, expiry - living in the sync orchestrator, agreeing by convention, untestable without driving a whole sync.

**work:** Add getCachedShelvesWithData()/cacheShelvesWithData(shelves) to SyncRepository, implement in SyncRepositoryImpl with SharedPrefManager and TimeProvider constructor parameters (both already Hilt-provided, so no module change), moving the keys and TTL into private companion constants. Keep the exact same pref keys, comma encoding and 6-hour window so existing installs keep their cache. Delete the two private functions and point SyncManager at the repository.

---

### 22. Have the free ViewModels read the current user from UserRepository, not UserSessionManager

**rating:** 69/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries), codex (boundaries), codex (boundaries), codex (boundaries), openhands-glm (boundaries)  _(5 lists)_

**files:** ui/community/CommunityTabViewModel.kt, ui/courses/TakeCourseViewModel.kt, ui/surveys/SurveysViewModel.kt

**why:** Three ViewModels inject the services singleton UserSessionManager solely to call getUserModel(), which VERIFIED is a one-line delegation `return userRepository.getUserModel()`. UserSessionManager additionally owns login/logout side effects, prefs writes and resource-open counters, so a ViewModel that only wants the current user drags that whole surface in as a test dependency. SurveysViewModel is the worst case: it already injects UserRepository (line 32) and UserSessionManager (line 33), calling the latter at lines 93 and 188.

**work:** Swap the constructor dependency for UserRepository in CommunityTabViewModel and TakeCourseViewModel; in SurveysViewModel just delete the UserSessionManager parameter and use the UserRepository it already has. Update the ViewModel tests to mock UserRepository.getUserModel(). Do not shrink UserSessionManager or migrate its legitimate fragment/activity login callers.

**note:** UserProfileViewModel also imports UserSessionManager but only for the KEY_RESOURCE_OPEN constant - that is a separate task below.

---

### 23. Give UserEntity one effective-id accessor and use it in the health screens

**rating:** 69/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries)

**files:** model/UserEntity.kt (top-level extension, outside the @Entity class), ui/health/HealthViewModel.kt (line 79), ui/health/MyHealthFragment.kt (lines 142, 273)

**why:** The "prefer the CouchDB _id, fall back to the local id" rule is copy-pasted three times in the health feature: `if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id` in HealthViewModel and the same expression on non-null receivers at MyHealthFragment 142 and 273. VERIFIED. An identity rule deciding which health records a patient sees lives in the UI in triplicate.

**work:** Add `val UserEntity.effectiveId: String? get() = _id?.takeIf { it.isNotEmpty() } ?: id` as a top-level extension (never a member property, so Room's schema is unaffected and no @Ignore is needed) and use it at the three sites. Test _id present, _id null, and _id empty string - the third is what the isNullOrEmpty() check handles and a naive `?:` rewrite would break.

---

### 24. Delete the dead, re-parsing News.messageWithoutMarkdown getter

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (perf)

**files:** model/News.kt (the messageWithoutMarkdown getter)

**why:** messageWithoutMarkdown iterates imagesArray - itself a fresh Gson parse per access - and string-replaces every image's markdown out of the message. VERIFIED: `grep -rn messageWithoutMarkdown app/src/` returns exactly one hit, the declaration itself. Dead code carrying a hidden per-call parse cost for any future caller.

**work:** Confirm again with grep, delete the getter and any import it made unnecessary.

---

### 25. Extract the team bulk-upload pipeline out of UploadManager into a TeamsUploadRunner

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** services/upload/TeamsUploadRunner.kt (new), services/UploadManager.kt (uploadTeams, queueTeamRetry, uploadTeamImageAttachment)

**why:** VERIFIED: UploadManager is 582 lines; uploadTeams (255), queueTeamRetry (328) and uploadTeamImageAttachment (353) form a self-contained pipeline - fetch via teamsSyncRepository.getTeamsForUpload(), _bulk_docs POST via uploadRepository.postUploadArray, per-row error -> retryQueue.queueFailedOperation, then deleteLocalTeamRecords/markTeamsUploaded. uploadAchievement (148) already shows the one-line-delegate pattern, and services/upload/ already holds PhotoUploader and AchievementUploader.

**work:** Create TeamsUploadRunner in services/upload/ injecting Lazy<TeamsSyncRepository>, UploadRepository, RetryQueue, DispatcherProvider and @ApplicationContext Context (uploadTeamImageAttachment needs it for MyTeam.getAttachmentFile). Move the three functions verbatim, keeping the processInBatches chunking and TAG logging. Reduce UploadManager.uploadTeams to a delegate and drop the imports the move frees.

**note:** Behaviour-preserving move of already-covered paths - the value is UploadManager shrinking, so resist adding logic changes.

---

### 26. Resolve notification group labels in the adapter instead of carrying them through state

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries), codex (boundaries)  _(2 lists)_

**files:** model/NotificationListItem.kt (drop label from Header), ui/notifications/NotificationsViewModel.kt (NotificationGroup, buildNotificationGroups, typeLabelFor), ui/notifications/NotificationsAdapter.kt

**why:** typeLabelFor is eight context.getString branches in the ViewModel, and the resolved string is carried as data through NotificationGroup.label into NotificationListItem.Header.label. VERIFIED, along with the sibling concern already done right: NotificationsAdapter maps type -> icon through ICON_BY_TYPE/iconResFor. A locale change also leaves the label stale until the flow re-emits.

**work:** Add LABEL_RES_BY_TYPE + labelResFor(type) beside ICON_BY_TYPE, bind with binding.tvHeaderLabel.setText(labelResFor(header.type)), and delete the label property from Header, from NotificationGroup and from both construction sites along with typeLabelFor. NotificationsViewModelTest reads headers via filterIsInstance and asserts on type, so it should compile unchanged.

**note:** A broader variant proposed a resource-descriptor type covering the message bodies too; the message formatters (formatTaskNotification, formatStorageNotification, formatJoinRequestNotification) are a much larger extraction - keep them out of this change.

---

### 27. Replace the 13 printStackTrace calls in the app-entry and sync classes with tagged logging

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** MainApplication.kt, ui/sync/SyncActivity.kt, services/NotificationActionReceiver.kt

**why:** VERIFIED counts: MainApplication 4, SyncActivity 6, NotificationActionReceiver 3 - thirteen e.printStackTrace() calls writing to stderr with no tag, no level and no chance of reaching the diagnostics pipeline. Each site has a meaningful surrounding operation to name.

**work:** Replace each with Log.e(TAG, "<operation that failed>", e), using each class's existing TAG where present. Keep every catch clause's control flow identical, and leave MainApplication.handleUncaughtException's persistCriticalLog call untouched. Verify grep -rn printStackTrace over the three files returns nothing.

**note:** SyncActivity is claimed by an open PR in several surveys - split it out if ownership collides.

---

### 28. Give AndroidDecrypter's four failure paths real logging

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** utils/AndroidDecrypter.kt, and its test

**why:** VERIFIED 4 printStackTrace calls: all four failure paths (decrypt, androidDecrypter, generateIv, generateKey) catch broadly, print the stack trace and silently return null/false/"". A failed health-record decryption or login key comparison is invisible in logs, and the output bypasses the app's log pipeline.

**work:** Replace the four printStackTrace calls with tagged logging, keeping the existing return contracts exactly. Add tests pinning the contract on bad input: malformed hex -> null, invalid dbPwdKeyValue -> false, generateKey/generateIv non-blank on the happy path.

**note:** The submitted order specified java.util.logging.Logger; use android.util.Log to match house style unless keeping this file android-free is the explicit goal.

---

### 29. Order personal resources in SQL

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (boundaries)

**files:** data/room/dao/PersonalDao.kt (getByUserIdFlow)

**why:** VERIFIED: `@Query("SELECT * FROM my_personal WHERE userId = :userId") fun getByUserIdFlow(userId: String): Flow<List<Personal>>` has no ORDER BY, and PersonalsRepositoryImpl.getPersonalResources exposes that Flow directly, so the My Personals list order is whatever SQLite returns and can change across versions or after a rewrite of the table.

**work:** Add an explicit ORDER BY over existing columns (e.g. date DESC, title COLLATE NOCASE ASC) and assert through the repository Flow that newer entries sort first. No schema version bump - adding ORDER BY to a @Query changes no table.

---

### 30. Extract the chat-share payload builder out of ChatHistoryAdapter

**rating:** 67/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** ui/chat/ChatHistoryAdapter.kt (showEditTextAndShareButton, serializeConversation), model/ChatSharePayload.kt (new)

**why:** showEditTextAndShareButton builds a CouchDB-shaped HashMap<String?, String> payload inside a RecyclerView adapter - _id, _rev, title, user, aiProvider, createdDate, updatedDate, conversations serialized with JsonUtils.gson, wrapped in an outer message/viewInId/viewInSection/messageType/messagePlanetCode/chat/news map. VERIFIED. Document serialization in a view class is untestable where it sits.

**work:** Add a pure `buildShareMap(chat, note, team, section, nowMillis)` producing byte-for-byte the same keys and values, with serializeConversation as a private helper; call it from the adapter passing Date().time from the call site. Test null team (empty viewInId/messageType/messagePlanetCode), populated team, null _id/_rev -> empty strings, a conversation round-trip, and assert the exact outer key set so the schema cannot drift.

---

### 31. Inject ServerReachabilityProvider into SubmissionsUploader instead of the MainApplication static

**rating:** 67/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf), devin (boundaries)  _(2 lists)_

**files:** services/SubmissionsUploader.kt (constructor and the two reachability checks)

**why:** SubmissionsUploader.checkAvailableServer calls the static MainApplication.isServerReachable(url) twice. VERIFIED: ServerReachabilityProvider is an injectable @Singleton exposing the same `suspend fun isServerReachable(urlString: String): Boolean` with an identical 30s TTL cache built on TimeProvider, while MainApplication's companion carries a duplicate cache using raw System.currentTimeMillis. utils/SyncTimeLogger already uses the provider - that is the reference pattern.

**work:** Constructor-inject ServerReachabilityProvider and replace both calls; drop the MainApplication import. Preserve the primary-then-alternative semantics and the 15-second withTimeoutOrNull. Add a test for the reachability short-circuit against a mocked provider. Leave the companion cache in place for the other callers (SyncActivity, ChatRepositoryImpl) - they are separate migrations.

---

### 32. Move the MyHealth decrypt-and-parse out of HealthExaminationViewModel

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** repository/HealthRepository.kt, repository/HealthRepositoryImpl.kt, ui/health/HealthExaminationViewModel.kt (loadData)

**why:** HealthExaminationViewModel decrypts and Gson-parses inline: `JsonUtils.gson.fromJson(decrypt(pojo.data, user?.key, user?.iv), MyHealth::class.java)` inside a try/catch that swallows to null. VERIFIED that HealthRepositoryImpl.decodeHealth already performs the identical AndroidDecrypter.decrypt + gson.fromJson with the same null-on-failure contract, but privately.

**work:** Add `suspend fun getDecryptedHealth(pojo: HealthExamination?, user: UserEntity?): MyHealth?` to HealthRepository, implement it on top of the existing decodeHealth, and replace the ViewModel block with that call, dropping the AndroidDecrypter/JsonUtils imports. Keep the initHealth() fallback. Test encrypted fixture -> parsed, empty data -> null, garbage -> null.

**note:** HealthRepositoryImpl is claimed by an open PR in several agents' surveys - re-check ownership before starting.

---

### 33. Parse community leaders inside ConfigurationsRepository

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries), copilot-grok (boundaries)  _(2 lists)_

**files:** repository/ConfigurationsRepository.kt, repository/ConfigurationsRepositoryImpl.kt (getCommunityLeaders), ui/community/LeadersViewModel.kt

**why:** getCommunityLeaders(): String hands a raw CouchDB JSON blob straight out of SharedPreferences and LeadersViewModel does the deserializing itself with UserEntity.parseLeadersJson, guarded by its own isNotEmpty() check. VERIFIED. The interface exposes a serialization format instead of a domain type, so every caller has to know the blob may be empty.

**work:** Change the interface to `fun getCommunityLeaders(): List<UserEntity>` returning UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders()) - parseLeadersJson already returns an empty list for blank/invalid input, so no extra guard. Reduce loadLeaders to a single assignment, keeping the dispatcherProvider.default launch.

**note:** The three other parseLeadersJson call sites (VoicesFragment, TeamsVoicesFragment, ReplyActivity) read SharedPrefManager directly, not this repository, so the signature change does not reach them.

---

### 34. Stop the two redundant My Life re-queries in LifeRepositoryImpl

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (boundaries), codex (boundaries)  _(2 lists)_

**files:** repository/LifeRepository.kt, repository/LifeRepositoryImpl.kt (updateVisibility, getMyLifeByUserId, seedMyLifeIfEmpty), ui/life/LifeViewModel.kt (updateVisibility)

**why:** Two separate redundancies, both VERIFIED. (1) updateVisibility updates the row, then calls getMyLifeByUserId purely to refresh the prefs cache and throws the list away; LifeViewModel.updateVisibility then calls loadMyLifeList(), querying my_life a second time - so every visibility toggle runs the full query twice after the write. (2) getMyLifeByUserId queries, calls seedMyLifeIfEmpty, then re-queries unconditionally, although seedMyLifeIfEmpty has already built the complete inserted list.

**work:** Change updateVisibility to return List<MyLife> (returning the list it already fetched) and have the ViewModel assign it to _myLifeList.value instead of reloading. Refactor the seeding path to return the newly inserted rows when it wins the mutex, and use them instead of the second getByUserId. Preserve deduplication, weight ordering, null-user normalization and the mutex.

---

### 35. Cache the enterprise attachment existence check out of the row-bind path

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** claude (perf), copilot-kimi (perf)  _(2 lists)_

**files:** ui/enterprises/EnterprisesFinancesAdapter.kt (bindFinanceImage), ui/enterprises/EnterprisesReportsAdapter.kt (bindReportImage)

**why:** VERIFIED: both adapters build a File via MyTeam.getAttachmentFile(...) and call imageFile.exists() directly inside onBindViewHolder - a disk stat on the UI thread for every visible row, repeated on every recycle, in two lists a team treasurer scrolls constantly. The value only changes when the underlying list changes, and ListAdapter already offers onCurrentListChanged to invalidate on.

**work:** Add a per-adapter map keyed on the file path and use getOrPut { imageFile.exists() }, keeping the null-file early return and the View.GONE branch identical; override onCurrentListChanged to clear it so a re-submitted list after an upload or delete re-stats. Leave the onViewRecycled Glide clear() calls alone. Also move the else-branch setOnClickListener(null) so it is not re-cleared on every bind.

**note:** The reports adapter also computes its row totals inline in onBindViewHolder - see the report-totals extraction task; sequence them.

---

### 36. Deduplicate the two count-style notification upserts

**rating:** 65/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (boundaries)

**files:** repository/NotificationsRepositoryImpl.kt (updateResourceNotification, updateStorageNotification)

**why:** VERIFIED: the two methods are near-identical read-modify-upsert routines - build a synthetic id, notificationDao.getById, detect whether the numeric value changed, mark unread and reset createdAt when it did, else construct a new AppNotification, upsert, and delete when the value is healthy. The only differences are the id suffix, type, relatedId, how the previous value is parsed (toIntOrNull() vs replace("%","").toIntOrNull()) and the message format. They have already drifted once: the resource path defaults previousCount to 0, the storage path to null.

**work:** Extract one private suspend helper taking the id suffix, type, relatedId, a parse lambda, a format lambda, the value and a healthy flag, and route both methods through it preserving each one's current semantics exactly (including the parse and threshold differences). Keep the public signatures. Add a test for the 'value unchanged keeps it read' path if absent.

---

### 37. Stop exporting the SubmitPhotosDao.UploadedPhoto projection through SubmissionsRepository

**rating:** 65/100 &nbsp;·&nbsp; **proposed by:** codex (boundaries)

**files:** repository/SubmissionsRepository.kt (line 7 import, markPhotosUploadedBatch at 56), repository/SubmissionsRepositoryImpl.kt, services/upload/PhotoUploader.kt

**why:** VERIFIED: SubmissionsRepository imports SubmitPhotosDao.UploadedPhoto and exposes it in markPhotosUploadedBatch's signature, and PhotoUploader constructs that DAO-owned type at line 62 - Room leaking across the service/repository boundary in both directions.

**work:** Define a small immutable repository-layer batch-update value type beside SubmissionsRepository with the same local id, revision and remote id fields; change the signature to accept it and drop the DAO import from the interface; map to SubmitPhotosDao.UploadedPhoto only inside the Impl immediately before the DAO call. Update PhotoUploader to construct the repository type, preserving chunking, callback timing and the attachment uploads.

**note:** Touches the same file as the PhotoUploader concurrency task - sequence them.

---

### 38. Extract the course-progress sync keys in one pass per document

**rating:** 65/100 &nbsp;·&nbsp; **proposed by:** codex (perf), jules (perf)  _(2 lists)_

**files:** repository/ProgressRepositoryImpl.kt (insertCourseProgressFromSync)

**why:** VERIFIED: the function makes four complete passes over every incoming document to collect docIds, courseIds, userIds and stepNums (each `.map{}.filter{}.distinct()`), then parses the same four fields again while constructing records. That is eight JSON lookups per document plus four intermediate lists, on a sync hot path.

**work:** Introduce a private file-local value type for the four parsed keys, build it in one pass over docs preserving input order and the existing treatment of blank ids and duplicate query keys, derive the DAO query arguments from those rows, and reuse each row's values when resolving existingProgress and localRecord. Keep courseProgressFromJson as the single place that hydrates the entity.

**note:** One agent proposed only the docIds micro-optimisation; the single-pass extraction supersedes it.

---

### 39. Clean up ServerUrlMapper: drop the empty also block and log the malformed-URL failure

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries), openhands-glm (perf)  _(2 lists)_

**files:** services/sync/ServerUrlMapper.kt (processUrl, extractBaseUrl)

**why:** processUrl reads `serverMappings[baseUrl].also { }` - an also block with an empty body, left over from a removed log line, allocating a lambda for nothing - then binds the result to `val result = UrlMapping(...)` and returns it on the next line. extractBaseUrl swallows malformed-URL errors with e.printStackTrace(). VERIFIED. This class is on the sync/upload URL-failover path, so silent failures here hide why a device never fails over to a clone server.

**work:** Reduce to `val alternativeUrl = extractedUrl?.let { serverMappings[it] }` and `return UrlMapping(url, alternativeUrl, extractedUrl)`; replace the printStackTrace with Log.w(TAG, "Could not extract base url from $url", e). Extend the test with a mapped primary, an unmapped host (alternativeUrl == null), a non-default port preserved in extractedBaseUrl, and a malformed string returning null without throwing.

---

### 40. Remove @ApplicationContext from DiagnosticsRepositoryImpl

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (boundaries)

**files:** repository/DiagnosticsRepositoryImpl.kt

**why:** VERIFIED: the constructor takes @ApplicationContext Context solely so saveLogToRoom (line 65) and saveLogsToRoom (line 83) can call VersionUtils.getVersionName(context). That keeps an Android Context dependency on an otherwise DAO/prefs repository.

**work:** Drop the Context parameter and resolve the version from BuildConfig.VERSION_NAME (which carries the flavor's versionNameSuffix, so the -lite value still matches). Keep ApkLogDao, UserRepository and SharedPrefManager usage unchanged. No RepositoryModule change is needed.

---

### 41. Remove @ApplicationContext from StorageCategoryViewModel

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** ui/settings/StorageCategoryViewModel.kt, ui/settings/StorageCategoryDetailFragment.kt

**why:** VERIFIED: the ViewModel holds @ApplicationContext Context (line 36) only to call FileUtils.getOlePath(context) twice - in loadResources (line 52) and again inside deleteItems (line 97). Its only caller, StorageCategoryDetailFragment, already has a Context.

**work:** Drop the Context parameter, take olePath: String on loadResources/deleteSelected/deleteAll/deleteItems, and compute it once in the fragment's onViewCreated. Removes an Android Context field from a ViewModel.

---

### 42. Inject @ApplicationScope into ResourceDownloadCoordinator

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** services/ResourceDownloadCoordinator.kt

**why:** VERIFIED: startBackgroundDownload launches its work on the global MainApplication.applicationScope, while di/ServiceModule already provides an injectable @ApplicationScope CoroutineScope (annotation at line 41, provider at 49-50). Using it removes a direct MainApplication dependency from a @Singleton service class.

**work:** Add `@ApplicationScope private val applicationScope: CoroutineScope` to the constructor, replace MainApplication.applicationScope.launch with applicationScope.launch, and drop the MainApplication import. Background downloads must still launch only when the server is reachable.

---

### 43. Inject @ApplicationScope into NotificationActionReceiver

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** services/NotificationActionReceiver.kt

**why:** VERIFIED: onReceive uses MainApplication.applicationScope.launch, although the receiver is already an @AndroidEntryPoint Hilt BroadcastReceiver that field-injects NotificationsRepository and DispatcherProvider - so it can field-inject the provided @ApplicationScope CoroutineScope and drop the static.

**work:** Add `@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope`, replace the launch call, and remove the MainApplication import. Keep goAsync()/pendingResult handling and the notification routing unchanged.

---

### 44. Purge android.text.TextUtils from the Voices and Health repository implementations

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries), codex (boundaries)  _(2 lists)_

**files:** repository/VoicesRepositoryImpl.kt (lines 3, 454, 459), repository/HealthRepositoryImpl.kt (lines 3, 217)

**why:** VERIFIED all three call sites and both imports: two repository implementations use android.text.TextUtils.isEmpty for plain null/empty String checks. A repository needs no Android framework helper for this, and each removed android.* import is one less platform coupling.

**work:** Replace TextUtils.isEmpty(x) with x.isNullOrEmpty() (and the negated form likewise) at the three sites, preserving current null-and-empty behaviour, and delete both imports. Run VoicesRepositoryImplTest, VoicesRepositoryNewsSyncTest and HealthRepositoryImplTest.

---

### 45. Replace TextUtils with Kotlin stdlib checks in five UI files

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** ui/health/HealthUsersAdapter.kt:55, ui/user/UserArrayAdapter.kt:65, ui/surveys/SendSurveyFragment.kt:29, ui/health/MyHealthFragment.kt:260, ui/enterprises/EnterprisesReportsFragment.kt:350-370

**why:** VERIFIED every site, including exactly six TextUtils.isEmpty checks in the EnterprisesReportsFragment validation `when` - five UI files each importing android.text.TextUtils for emptiness checks the Kotlin idiom does null-safely and allocation-free.

**work:** Swap to isNullOrEmpty() (preserving the negated forms and the existing string-template arguments), delete the five imports, and confirm grep for android.text.TextUtils over the five files returns nothing.

---

### 46. Derive the finances header from transactions and move the totals math onto Transaction

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (boundaries), copilot-grok (boundaries)  _(2 lists)_

**files:** ui/enterprises/EnterprisesFinancesViewModel.kt, model/Transaction.kt

**why:** VERIFIED: the ViewModel keeps two parallel MutableStateFlows, _transactions and _headerState. getTeamTransactions collects the list, sets _transactions.value, then calls the imperative calculateTotal(results), which loops the list again to recompute debit/credit/total and mutates _headerState. headerState is a pure function of transactions.

**work:** Turn calculateTotal into a pure function returning FinanceHeaderState (with the summing itself as a helper on Transaction or its companion), expose headerState as transactions.map { ... }.stateIn(viewModelScope, WhileSubscribed(5000), FinanceHeaderState()), and delete _headerState and the imperative call. Keep isCautionVisible = total < 0. Cover credit-only, debit-only, mixed and empty.

---

### 47. Let LifeRepositoryImpl own user-id normalisation and drop LifeViewModel's needless dispatcher hops

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** claude (boundaries)

**files:** ui/life/LifeViewModel.kt, and its test

**why:** VERIFIED: LifeViewModel.resolveUserId() reimplements the rule the repository already applies internally - LifeRepositoryImpl.normalizeUserId is the identical `takeIf { it.isNotBlank() && it != "--" }` - so the "--" sentinel, a persistence detail, is encoded twice and the copies can drift. The same ViewModel also wraps repository calls in withContext(dispatcherProvider.io) although LifeRepositoryImpl does no blocking work of its own and Room already runs suspend DAO queries off the main thread.

**work:** Delete resolveUserId() and pass the raw id straight through, letting the repository normalise; keep feeding the same value to MyLife.defaultItems so seeded rows carry the identical userId. Drop the three withContext(io) wrappers and the dispatcherProvider parameter if nothing else uses it. Add a test asserting a "--" current-user id is forwarded verbatim - the repository, not the ViewModel, decides what it means.

---

### 48. Consolidate the retry attempt transition and stop the read-modify-write

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf), openhands-glm (boundaries)  _(2 lists)_

**files:** repository/RetryRepositoryImpl.kt (updateAttempt, markFailed), model/RetryOperation.kt, data/room/dao/RetryDao.kt

**why:** VERIFIED: updateAttempt and markFailed each do findById(id) -> mutate the loaded RetryOperation -> update(op), i.e. a SELECT plus an UPDATE on every retry attempt, and both open-code the same transition (increment attemptCount, set lastAttemptTime, recompute nextRetryTime, decide abandon-vs-pending from attemptCount >= maxAttempts). The sibling methods markInProgress and markCompleted are already one-line @Query UPDATEs in RetryDao.

**work:** Put the pure state transition on RetryOperation as a method (it has no Android imports and is unit-testable without a DAO), and add a @Query UPDATE to RetryDao so the two paths become a single statement. Preserve the status transitions exactly - markFailed keeps STATUS_PENDING unless the new count reaches maxAttempts, then STATUS_ABANDONED. No schema change, no AppDatabase version bump, no backoff-constant change.

**note:** Two agents proposed the two halves (single UPDATE / model method); they compose into one change.

---

### 49. Route the personals document POST through UploadRepository

**rating:** 62/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** repository/UploadRepository.kt, repository/UploadRepositoryImpl.kt, repository/PersonalsRepositoryImpl.kt (uploadPersonalDocument)

**why:** VERIFIED: PersonalsRepositoryImpl.uploadPersonalDocument calls apiInterface.postDoc(...) directly at line 82 while already injecting UploadRepository at line 21 (and using it for uploadAttachment at line 117). One repository holding two upload paths, one of them bypassing the upload boundary.

**work:** Add a postDocument(url, payload) wrapper to UploadRepository/Impl around the same apiInterface.postDoc call, switch PersonalsRepositoryImpl to it, and drop the ApiInterface field and import. First confirm UploadRepositoryImpl does not already expose an equivalent POST - if it does, use it and the change shrinks to two files.

---

### 50. Inject TimeProvider into RetryOperation's factory and backoff calculation

**rating:** 62/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf)

**files:** model/RetryOperation.kt (createFromRetryFailure, calculateNextRetryTime), repository/RetryRepositoryImpl.kt

**why:** VERIFIED: createFromRetryFailure sets createdTime/lastAttemptTime from System.currentTimeMillis() and calculateNextRetryTime returns System.currentTimeMillis() + delay. utils/TimeProvider is an injectable interface provided by di/TimeModule, and RetryRepositoryImpl already injects timeProvider (it uses it at line 43) - the only two call sites of both functions are in that same file.

**work:** Thread a TimeProvider through both companion functions and pass the repository's existing instance at both call sites. Keep the exponential backoff values identical (30s, 60s, ... capped at 30 min) and add a fake-clock test asserting them.

---

### 51. Collapse the double collection pass in VoicesRepositoryImpl.insertNewsList

**rating:** 61/100 &nbsp;·&nbsp; **proposed by:** codex (perf), jules (perf)  _(2 lists)_

**files:** repository/VoicesRepositoryImpl.kt (insertNewsList)

**why:** VERIFIED: insertNewsList builds `mappedDocs = docs.map { it to getString("_id", it) }`, then maps that whole list again and filters it (`mappedDocs.map { it.second }.filter { it.isNotEmpty() }`) just to get the lookup ids - two more intermediate collections on potentially hundreds of news records, after the mapping had already removed an N+1 query.

**work:** Keep one ordered document/id representation so _id is parsed once per document and populate the non-blank id query list during that same traversal. Preserve duplicate ids, blank-id handling, input order, the single bulk DAO lookup and associateBy conflict behaviour; keep building every News through buildNewsFromJson with one upsertAll.

---

### 52. Replace the openConnection MIME probe in UploadRepositoryImpl.uploadAttachment

**rating:** 61/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf)

**files:** repository/UploadRepositoryImpl.kt (uploadAttachment)

**why:** VERIFIED: uploadAttachment opens `file.toURI().toURL().openConnection()` solely to read connection.contentType, and never reads a byte from that connection - so every attachment/photo/CV upload does a content-sniffing open of the file before the real upload. The project already has the local-only patterns: URLConnection.guessContentTypeFromName(file.name) (used in WebViewActivity) and FileUtils.getMimeType(name) (used in UploadManager).

**work:** Replace the openConnection + contentType lookup with `URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"` and drop the import if it becomes unused. Leave the header map via FileUploader.getHeaderMap, the request body and the response handling untouched - the resolved type still feeds getHeaderMap(mimeType, rev), so uploads must keep sending the right Content-Type for pdf/jpg/png.

---

### 53. Seal the My Life prefs cache behind SharedPrefManager and collapse the triplicated MyLifeDao predicate

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (boundaries)

**files:** services/SharedPrefManager.kt, repository/LifeRepositoryImpl.kt (cacheMyLifeItems, getMyLifeForDashboard), data/room/dao/MyLifeDao.kt

**why:** VERIFIED: LifeRepositoryImpl reads and writes its cache through sharedPrefManager.rawPreferences directly (getString at line 105, edit { putString(...) } at line 135) using androidx.core.content.edit - an Android preferences API inside a repository. Separately, MyLifeDao repeats the same long nullable-user predicate `(:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)` verbatim in getByUserId, getVisibleByUserId and countByUserId.

**work:** Add typed get/set/clear helpers on SharedPrefManager for the existing myLifeCache_<userId> key space, keeping the key prefix identical, and route the repository through them (Gson + CachedMyLifeItem mapping stays in the repository). Collapse the three DAO predicates into one equivalent form preserving the guest/blank/-- semantics, and pin it with DAO tests for null, blank, '--' and a real id.

---

### 54. Collapse the profile activity-stats fan-out and move KEY_RESOURCE_OPEN behind the repository

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** codex (boundaries), copilot-grok (boundaries)  _(2 lists)_

**files:** repository/ActivitiesRepository.kt, repository/ActivitiesRepositoryImpl.kt, ui/user/UserProfileViewModel.kt

**why:** VERIFIED: UserProfileViewModel imports UserSessionManager solely for the KEY_RESOURCE_OPEN constant and passes it into activitiesRepository.getMostOpenedResource and getResourceOpenCount (lines 131, 133), alongside getGlobalLastVisit - three async calls the ViewModel assembles itself. A ViewModel should not know a session-service storage token.

**work:** Add repository-level convenience methods that need only the user name, with the resource-open token owned inside the repository contract, and (optionally) one ProfileActivityStats aggregate combining most-opened, global last visit and open count. Switch the ViewModel to them and drop the UserSessionManager import, preserving the displayed string formatting and leaving getOfflineVisits() alone.

---

### 55. Stop the per-element deepCopy that defeats Achievement's parse cache

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf)

**files:** model/Achievement.kt (parseStringListToJsonArray)

**why:** VERIFIED: a synchronized LRU parsedJsonCache (capacity 1000) exists, but parseStringListToJsonArray looks up the cache and then calls `array.add(ob?.deepCopy())` for every element, so the cache saves the parse but still pays a full tree clone per item on every call.

**work:** Audit the callers of the functions that use parseStringListToJsonArray: if none mutates the returned JsonArray's elements, add the cached element directly. If mutation risk exists, deep-copy once at cache-insert time and hand out the shared element, documenting the no-mutation contract. Keep CACHE_CAPACITY eviction and the public API. Add a test asserting cache hits return equal content without re-parsing.

**note:** The mutation audit is the real work here - budget for it rather than dropping deepCopy blind.

---

### 56. Delete the dead updateMyLifeSurveyCount stub and wire the real assigned-survey count

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** base/BaseDashboardFragment.kt, ui/dashboard/DashboardViewModel.kt

**why:** VERIFIED: myLifeListInit renders every visible item with `getLayout(dashboardItem, 0)` - surveyCount hardcoded to 0 - and then calls updateMyLifeSurveyCount(), which is an empty body with the comment "Update views with survey count if needed". So the My Life survey chip always renders 0 and the stub is dead residue.

**work:** Delete the empty stub, and either wire the real count (a single assigned-survey count read exposed on DashboardViewModel, passed into getLayout for the survey item) or remove the count badge - decide which, since the current state renders a permanently wrong 0. Add a ViewModel test for the count and the errors-surface-as-0 path.

**note:** The submitted order was hedged and self-referential about what exists on master; the always-0 badge is the concrete finding worth acting on. BaseDashboardFragment is claimed by an open PR.

---

### 57. Cache the step-label pattern in TakeCourseFragment

**rating:** 59/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (perf), openhands-glm (perf)  _(2 lists)_

**files:** ui/courses/TakeCourseFragment.kt (setStepText)

**why:** VERIFIED: setStepText builds its label with String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", ...) - a resource lookup plus template-string concatenation on every call. It runs on each page selection (updateStepDisplay), each next tap and each previous tap, i.e. per swipe through a course.

**work:** Hold the resolved label (or the whole pattern) in a private lazy field per fragment instance and use it in setStepText, keeping Locale.getDefault() as the format locale. A locale change recreates the activity, so the cache cannot go stale. Verify the "Course Details" branch at position 0 is unaffected.

---

### 58. Remove the three identity map { it } copies from course-detail hydration

**rating:** 58/100 &nbsp;·&nbsp; **proposed by:** codex (perf)

**files:** repository/CoursesRepositoryImpl.kt (getCourseProgress, lines ~437-463)

**why:** VERIFIED: three DAO results are copied through identity `.map { it }` calls before grouping or filtering - questionDao.getByExamIds(examIds).map { it }.groupBy {...}, submissionDao.getExamSubmissionsByUser(userId).map { it }.filter {...}, and answerDao.getBySubmissionIds(submissionIds).map { it }.groupBy {...}. Pure allocation with no type, order or value change, on the path assembled whenever course details and progress are built.

**work:** Feed each DAO result directly into its groupBy/filter. Preserve the empty-input short circuits, key filtering, ordering and nullable-key behaviour exactly.

**note:** CoursesRepositoryImpl is claimed by several open PRs - check ownership first; the change is 6 lines and easy to rebase.

---

### 59. Expose the achievement document id from AchievementViewModel

**rating:** 58/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** ui/user/AchievementViewModel.kt, ui/user/EditAchievementFragment.kt

**why:** VERIFIED: the id `"<userId>@<planetCode>"` is derived twice - AchievementViewModel.loadUserAndAchievement builds it at line 59 to load the achievement, and EditAchievementFragment's save handler rebuilds `user?.id + "@" + user?.planetCode` at line 166 to save. A persistence-format concern in the view; if the format changes, the fragment silently writes a different document than the ViewModel reads.

**work:** Expose the id derived during loadUserAndAchievement (e.g. a StateFlow<String?> set alongside _achievement) so the derivation exists once, and consume it in the save handler. If it is unavailable because the user has not loaded, keep the save button's current guard - never save with a fabricated id.

---

### 60. Hoist CourseStepFragment's multi-repo orchestration into a CourseStepViewModel

**rating:** 58/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (boundaries)

**files:** ui/courses/CourseStepFragment.kt, ui/courses/CourseStepViewModel.kt (new)

**why:** VERIFIED: the fragment field-injects ConfigurationsRepository and ProgressRepository and, via BaseContainerFragment, uses coursesRepository, userRepository and resourcesRepository inside lifecycleScope - getCourseStepData (103), getUserModel (109), getCourseTitleById (111), saveCourseProgress (90), checkServerAvailability (205), downloadResourcesPriority (208), getAllStepResources (221, 234). Five-repository orchestration living in a Fragment.

**work:** Create a @HiltViewModel CourseStepViewModel injecting those repositories (plus the download coordinator if the download start moves too) and exposing one StateFlow UI state (step, resources, exams, survey, title, userHasCourse, download flags). Leave view binding, markdown/AI selection, navigation and adapter wiring in the fragment, collecting state instead of launching repository calls. Do not edit any *RepositoryImpl or BaseContainerFragment. Unit-test load, save and empty-step paths.

**note:** The largest task in the set - budget accordingly and keep it to one screen.

---

### 61. Build the survey reminder ids and pending rows in single-pass collections

**rating:** 57/100 &nbsp;·&nbsp; **proposed by:** codex (perf), jules (perf)  _(2 lists)_

**files:** ui/dashboard/BellDashboardViewModel.kt (handleDueReminders)

**why:** VERIFIED: handleDueReminders chains flatMap + filter + distinct for all survey ids, then chains mapNotNull + filter for every reminder group (`surveyIdList.mapNotNull { submissionsById[it] }.filter { it.status == "pending" }`).

**work:** Build all non-blank survey ids into an insertion-ordered set in one traversal and pass them to the existing bulk submission lookup unchanged; build each group's pending list in one pass combining lookup and status filtering. Preserve duplicate suppression for the bulk query, per-group order, empty-group skipping and the emitted prompt contents.

---

### 62. Cache the parsed ai_models map in ChatDetailFragment

**rating:** 57/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** ui/chat/ChatDetailFragment.kt (getModelsMap, getCachedProviderAvailability)

**why:** VERIFIED: getModelsMap re-reads sharedPrefManager.getRawString("ai_models") and Gson-parses the full Map<String, String> on every call, and getCachedProviderAvailability calls getModelsMap again on top of the other call sites.

**work:** Cache the last raw string plus the parsed map in fragment fields, invalidating when the raw string changes (so a mid-session preference change is still picked up), and make getCachedProviderAvailability use the cached map once. Do not touch ChatViewModel, the adapters, or JsonUtils.

**note:** ChatDetailFragment appears in several open-PR file sets - confirm ownership.

---

### 63. Use case-insensitive endsWith for the GIF checks

**rating:** 57/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** utils/ImageViewerUtils.kt:38 (showZoomableImage), ui/voices/VoicesActions.kt:93 (addImageWithRemoveIcon)

**why:** VERIFIED both sites: `imagePath.lowercase(Locale.getDefault()).endsWith(".gif")` allocates a lowercase copy of the whole path on every image binding, and makes the check locale-dependent. String.endsWith(suffix, ignoreCase = true) is allocation-free and locale-independent.

**work:** Replace both with endsWith(".gif", ignoreCase = true); remove the java.util.Locale import from ImageViewerUtils but keep it in VoicesActions, where dateFormatter still uses it. Leave the Glide asGif/load/error branches alone.

---

### 64. Stop EnterprisesReportsFragment reaching into TeamsRepository for the team name

**rating:** 57/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries), copilot-grok (boundaries)  _(2 lists)_

**files:** ui/enterprises/EnterprisesReportsFragment.kt (lines 69, 84, 114)

**why:** VERIFIED three calls to teamsRepository.getTeamNameFromPrefs() - for the CSV filename, the CSV content and one more site - a cross-feature leak from the enterprises screen into Teams for a value the screen already knows. BaseTeamFragment.getEffectiveTeamName() (line 90) resolves it from the fragment's arguments or the loaded team.

**work:** Replace the three prefs-backed lookups with one read of the effective team name (the base-class helper is the smallest change and drops the dependency entirely; routing through the ViewModel keeps the prefs coupling). Remove the teamsRepository reference from this file if the name was its only use, and keep the filename sanitising in the UI.

**note:** Note the two submitted variants disagree on the source of truth: arguments/team vs prefs. They can differ - decide deliberately and cover it with the export test. One order also undercounted the call sites as two.

---

### 65. Collapse the member-visit aggregation into one pass and drop the unused MemberStats

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** codex (perf)

**files:** repository/TeamsRepositoryImpl.kt (getJoinedMembersWithVisitInfo, the visit-log aggregation only)

**why:** VERIFIED: the visit logs are traversed twice - `logs.groupingBy { it.user }.eachCount()` and then `logs.groupBy { it.user }.mapValues { ... maxOfOrNull { it.time ?: 0 } }` - and the second pipeline retains a list per user, so one accumulator map lowers both CPU and peak memory on large teams. A local `data class MemberStats` declared at line 977 is unused.

**work:** Replace the unused MemberStats with a small local aggregate holding a visit count and greatest non-null timestamp, traverse logs once keyed by the same nullable user key both collections use today, and read count and latest timestamp from it while mapping orderedMembers. Preserve the zero-visits / no-latest-timestamp defaults.

**note:** The bigger hotspot in this function is the per-member activitiesRepository.getLastVisit / getOfflineVisitCount N+1 - explicitly out of scope here. TeamsRepositoryImpl is the most PR-contended file in the repo.

---

### 66. Resolve the current user id inside TeamCoursesViewModel

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** ui/teams/courses/TeamCoursesFragment.kt (updateCoursesList), ui/teams/courses/TeamCoursesViewModel.kt (loadCourses)

**why:** VERIFIED: TeamCoursesFragment.updateCoursesList reads `sharedPrefManager.getUserId().ifEmpty { "--" }` and passes the id into viewModel.loadCourses - a platform SharedPrefManager lookup in the view for a value UserRepository.getCurrentUserId() already provides suspendably.

**work:** Inject UserRepository into the ViewModel, change loadCourses(teamId, currentUserId) to loadCourses(teamId) computing the id internally with the same "--" fallback, and drop the sharedPrefManager use from the fragment.

**note:** Overlaps the team-tab parallelization task on the same loadCourses function - land one, rebase the other.

---

### 67. Extract community-service route resolution into a tested pure function

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (boundaries)

**files:** ui/community/CommunityServicesFragment.kt (setRecyclerView), ui/community/CommunityServiceRoute.kt (new)

**why:** VERIFIED: the click listener parses routes inline - http/https prefix check, then rawRoute.split("/") taking segments[3] as a team id when size >= 4, else a WebView fallback - interleaved with the navigation and membership-check code. Navigation policy buried in a view, with zero tests.

**work:** Add a sealed CommunityServiceRoute (ExternalLink / TeamLink / Unhandled) and a pure resolve(route) reproducing today's rules exactly, then switch the listener over it keeping the same resulting actions. Test an https URL, an http URL, a /teams/view/<id>-style route, a route with fewer than 4 segments, and the empty string.

**note:** Three separate orders touch this one fragment (route extraction, the LayoutInflater hoist, and the ViewModel extraction) - sequence them.

---

### 68. Parse each news item's images JSON once in downloadReferencedResources

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (perf)

**files:** ui/voices/VoicesViewModel.kt (downloadReferencedResources)

**why:** VERIFIED: News.imagesArray re-parses the images JSON with Gson on every access (no caching in the getter), and the loop dereferences news?.imagesArray twice per item - once for the isEmpty() check and once for get(0) - so a feed of N posts with images triggers 2N full JSON parses every time the feed loads.

**work:** Hoist `val images = news?.imagesArray` once per item and use the local for both the emptiness check and the element access. Keep the resourceIds dedup set and the repository calls unchanged.

**note:** Complementary to the News parse-cache task: that one fixes the getter, this one stops the double call.

---

### 69. Make FileUtils.findHtmlCoverImage a single lazy pass

**rating:** 55/100 &nbsp;·&nbsp; **proposed by:** codex (perf), copilot-grok (perf)  _(2 lists)_

**files:** utils/FileUtils.kt (findHtmlCoverImage)

**why:** VERIFIED: the function walks up to four directory levels, materializes every matching image with .toList(), then scans that list for a filename hint and may scan it a third time for the largest file. Large offline HTML resources can hold many images.

**work:** Iterate the walk lazily instead of converting to a list, computing each filename's lowercase form once and returning the first hint match immediately (current semantics already take the first hint in walk order), while tracking the largest candidate for the unchanged fallback. Keep the depth limit, extension allowlist, walk order, case-insensitive extension handling and tie behaviour, and null for empty or non-directory input.

---

### 70. Single map read and System.currentTimeMillis in News.updateMessage/createNews

**rating:** 55/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** model/News.kt (updateMessage, createNews)

**why:** VERIFIED: createNews does `if (map.containsKey("news")) { val newsObj = map["news"] ... }` - two lookups in the deserialized map - and both updateMessage (line 101) and createNews (line 167) use Date().time, allocating a java.util.Date purely to read the current timestamp. Both run for every news or voice item parsed or updated.

**work:** Replace the containsKey/get pair with `map["news"]?.let { ... }`, swap both Date().time reads for System.currentTimeMillis(), and drop the now-unused java.util.Date import. No serialization-format or Room-schema change.

---

### 71. Batch the null-rev health markUploaded updates

**rating:** 55/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** data/room/dao/HealthExaminationDao.kt

**why:** VERIFIED: the map overload `markUploaded(idToRevMap)` is a @Transaction that loops `idToRevMap.forEach { (id, rev) -> markUploaded(id, rev) }`, one UPDATE per entry. It is driven from HealthRepositoryImpl.markHealthExaminationsUploaded via UploadToShelfService, whose common path passes null revs for already-synced docs.

**work:** Keep the single-row markUploaded(id, rev), and in the map overload group the null-rev ids and batch them with `UPDATE health_examinations SET isUpdated = 0 WHERE _id IN (:ids)`, chunked at 900 exactly as RemovedLogDao already does. Non-null revs still need per-row updates since the rev differs per row. Add a Room in-memory test covering rows with rev, rows without, a mixed batch and the empty-map no-op.

**note:** Gain is bounded - these UPDATEs already run inside one transaction - so the new DAO test is arguably the bigger win.

---

### 72. Consolidate SubmissionDetailViewModel's five derived flows into one UiState

**rating:** 55/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (boundaries)

**files:** ui/submissions/SubmissionDetailViewModel.kt, ui/submissions/SubmissionDetailFragment.kt

**why:** VERIFIED: five separately-stateIn'd flows (questionAnswers, title, status, date, submittedBy), each a filterNotNull().map{}.stateIn(...) chain off the same submissionDetailState, and the fragment collects all five with five collectWhenStarted blocks.

**work:** Define a SubmissionDetailUiState holding all five fields with the current 'unknown' defaults, expose one StateFlow mapping submissionDetailState (null -> defaults), delete the five flows, and collect once in the fragment.

**note:** A separate order proposed adding distinctUntilChanged() to those five flows instead; that was dropped as a no-op (stateIn already conflates by equality). This consolidation is the real cleanup.

---

### 73. Build the notification group ordering in one LinkedHashSet pass

**rating:** 54/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** ui/notifications/NotificationsViewModel.kt (buildNotificationGroups)

**why:** VERIFIED: orderedTypes is `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()` - three temporary lists plus a set on every notification refresh, and the second filter does O(n x m) membership checks against a List.

**work:** Add the TYPE_ORDER entries present in grouped to a LinkedHashSet, then any remaining grouped.keys, preserving the exact ordering semantics (TYPE_ORDER first, then remaining keys in iteration order). Leave the groupBy and the mapNotNull return block unchanged.

---

### 74. Extract the report-totals math out of EnterprisesReportsAdapter

**rating:** 54/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (boundaries)

**files:** ui/enterprises/EnterprisesReportsAdapter.kt, and its test

**why:** VERIFIED: onBindViewHolder computes totalIncome = sales + otherIncome, totalExpenses = wages + otherExpenses, profitLoss and the ending balance inline for every bound row. The same arithmetic also appears in EnterprisesRepositoryImpl.exportReportsAsCsv.

**work:** Add a private ReportTotals data class and a pure reportTotals(report) function in the same file and call it from onBindViewHolder - no behaviour change - then unit-test income, expenses, profit/loss and ending balance including a negative beginning balance.

**note:** Deliberately does not unify with the CSV path, which the SQL push-down task owns. Same file as the attachment-exists caching task.

---

### 75. Move the CommunityServices repository calls into a ViewModel

**rating:** 54/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** ui/community/CommunityServicesFragment.kt, ui/community/CommunityServicesViewModel.kt (new)

**why:** VERIFIED: the fragment calls teamsRepository.getTeamLinks() at line 61 and teamsRepository.isMember(user?.id, teamId) at line 101 directly from lifecycle scopes. A ViewModel exists in the package (CommunityTabViewModel) but does not cover this tab.

**work:** Create a @HiltViewModel CommunityServicesViewModel injecting TeamsRepository and UserRepository, exposing getTeamLinks() and isMember(teamId) (resolving the user id itself), and switch the fragment to it. Keep the resulting navigation and the isMyTeam argument identical.

---

### 76. Pass androidId and customDeviceName into the model serializers instead of reading MainApplication.context

**rating:** 54/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** services/upload/UploadConfigs.kt, model/NewsLog.kt, model/Rating.kt, model/SearchActivity.kt

**why:** NewsLog.serialize, Rating.serializeRating and SearchActivity.serialize pull androidId / customDeviceName from the MainApplication companion context (VERIFIED: NewsLog line 36, Rating's `import MainApplication.Companion.context`, SearchActivity lines 38 and 40). UploadConfigs already injects @ApplicationContext Context and SharedPrefManager, so it can compute both once and pass them in.

**work:** Compute androidId and customDeviceName once in UploadConfigs and thread them through the three serializer lambdas; change the three signatures to accept them and drop the MainApplication (and now-unused VersionUtils/NetworkUtils) imports. Uploaded payloads must keep the same androidId, deviceName and customDeviceName fields.

**note:** SCOPE CORRECTED: the submitted order also listed model/ApkLog.kt as a fourth site. VERIFIED it is already migrated - ApkLog.serialize takes customDeviceName as a parameter and calls addDocumentOrigin() with no argument. Three files, not four.

---

### 77. Use getOrPut for the TeamPagerAdapter stable ids

**rating:** 53/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** ui/teams/TeamPagerAdapter.kt (init, updatePages)

**why:** VERIFIED both sites: `if (!itemIds.containsKey(page.id)) { itemIds[page.id] = nextId++ }` - a containsKey followed by a put for every page during initialization and on every page update.

**work:** Replace both with itemIds.getOrPut(page.id) { nextId++ }, keeping nextId a Long so stable fragment ids are still generated sequentially. Do not touch createFragment, getItemId or containsItem.

---

### 78. Drop the double processTimes lookup in SyncTimeLogger.endProcess

**rating:** 53/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** utils/SyncTimeLogger.kt (endProcess)

**why:** VERIFIED: endProcess checks `if (!processTimes.containsKey(startKey)) return` and then immediately reads `val startTime = processTimes[startKey] ?: return` - two lookups on a ConcurrentHashMap for every timed sync or upload process.

**work:** Delete the containsKey guard and keep only the `?: return` read. Duration calculation and the processTimes/processItemCounts writes stay unchanged; missing start times are still silently ignored.

---

### 79. Swap java.util.concurrent.TimeUnit for kotlin.time in ForceSyncPolicy

**rating:** 53/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (boundaries)

**files:** ui/sync/ForceSyncPolicy.kt, and its test

**why:** VERIFIED: the object is otherwise pure and well-tested with no Android imports, except `import java.util.concurrent.TimeUnit` used for TimeUnit.MILLISECONDS.toDays(nowMillis - lastSyncMillis); the test uses TimeUnit.DAYS.toMillis for fixtures. TimeUnit is JVM-only and blocks moving this sync-cadence policy into a common module.

**work:** Use the kotlin.time equivalent ((nowMillis - lastSyncMillis).milliseconds.inWholeDays) and drop the import; swap the test fixtures likewise. Keep the signatures, the constants and the inclusive-threshold semantics identical.

---

### 80. Quote the img src attribute in MarkdownUtils.prependBaseUrlToImages

**rating:** 53/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf)

**files:** utils/MarkdownUtils.kt (prependBaseUrlToImages)

**why:** VERIFIED: the function appends `"<img src=$fullUrl width=$width height=$height/>"` with an unquoted src attribute, so any resolved URL containing a space truncates at the space when rendered.

**work:** Lift the tag into a template with a quoted src value and format the URL into it, keeping the matcher/StringBuilder loop identical. Test a relative resources/foo.png image resolving and rendering, plus a URL containing a space.

**note:** The submitted order framed this as a performance/Markwon-caching task; VERIFIED the Markwon instance is already double-check-locked and the pattern already compiled once, so the only real content here is the quoting fix. Kept and re-titled accordingly.

---

### 81. Replace android.util.Base64 in CoursesRepositoryImpl with a byte-identical java.util.Base64

**rating:** 52/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** repository/CoursesRepositoryImpl.kt (line 3 import, line 684)

**why:** VERIFIED: `import android.util.Base64` with a single usage - `Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)` deriving a course step id. java.util.Base64 is available at minSdk 26 and is a platform-free equivalent.

**work:** Read the exact flags first: NO_WRAP suppresses line breaks but KEEPS padding, so the parity-preserving replacement is java.util.Base64.getEncoder() (with padding), not withoutPadding(). This id is persisted and compared, so output must be byte-identical - add a test pinning the derived step id for a fixed JsonObject to the known Base64 string, then remove the android import.

**note:** CoursesRepositoryImpl is heavily PR-contended; the persisted-id parity requirement is the real risk.

---

### 82. Move the notification-feed enrichment into NotificationsRepository

**rating:** 52/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (boundaries)

**files:** repository/NotificationsRepository.kt, repository/NotificationsRepositoryImpl.kt, ui/notifications/NotificationsViewModel.kt (loadNotifications)

**why:** VERIFIED: loadNotifications collects task and join-request id lists, runs parseTaskDate over them, then fans out getTaskTeamNamesByTaskIds, getTaskTeamNamesByTaskTitles, getJoinRequestDetailsBatch and getUnreadCount (already with async, lines 105-127) and maps the result through formatNotification. The join graph - which batch lookups are needed and how their results key together - is repository orchestration living in the ViewModel.

**work:** Add a repository result type carrying the payloads, task team names, join-request details, parsed task dates and unread count, and a loadNotificationFeed(userId, filter, isAdmin) implemented on the existing repository helpers, with parseTaskDate moved alongside. Reduce the ViewModel to that one call plus the existing R.string formatting; keep grouping and selection in the ViewModel. Do not touch NotificationDao.

**note:** Large and it overlaps the label-resolution task on the same ViewModel. The batch calls are already concurrent, so this is a layering win, not a latency win.

---

### 83. Introduce bounded parallelism for non-priority downloads in DownloadService

**rating:** 52/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** services/DownloadService.kt (processDownloadQueue)

**why:** VERIFIED: processDownloadQueue is a `while (true)` loop that takes getNextPriorityUrl() ?: getNextPendingUrl() and awaits initDownload one URL at a time, so multi-file course/library downloads under-utilize bandwidth and stretch foreground-service time.

**work:** Add a small concurrency limit (2-3) for non-priority downloads via Semaphore + async, keeping priority URLs served first (and serial, to preserve UX ordering). Guard the shared session counters and throttle notification updates so parallel completions do not spam. Guarantee the queue still drains to empty -> completion notification -> stopSelf, with no double-download of the same URL.

**note:** Highest-risk task in the perf set: it changes the service's core control flow and interacts with the notification/session counters that the per-tick caching task also touches. The submitted order offers a documented fallback (larger buffer + notification throttle only) - but that fallback is already covered by the per-tick task, so if you do not take the parallelism, close this instead of doing the fallback twice.

---

### 84. Replace the openConnection MIME probe in UploadManager.uploadNews

**rating:** 51/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** services/UploadManager.kt (uploadNews, the news-image loop)

**why:** The same anti-pattern as the UploadRepositoryImpl task: `val mimeType = imageFile.toURI().toURL().openConnection().contentType` for a local file, once per news image. FileUtils.getMimeType / Utilities.getMimeType already exist for extension-based lookup.

**work:** Drop the probe and derive the MIME from the filename, falling back to application/octet-stream, passing it into getHeaderMap as today. Do not change the createImage contract or the news JSON shape.

**note:** PREMISE PARTLY WRONG in the submitted order, which claims the MIME is 'ignored' and the upload always uses application/octet-stream. VERIFIED: only the request *body* media type is octet-stream; mimeType is passed to getHeaderMap(mimeType, resourceRev) and does reach the request. The probe is still pointless I/O, so the change stands - but it is not the correctness fix it was sold as.

---

### 85. Move R.string resolution out of MyLife and LifeViewModel

**rating:** 50/100 &nbsp;·&nbsp; **proposed by:** devin (boundaries)

**files:** model/MyLife.kt, ui/life/LifeViewModel.kt, ui/life/LifeFragment.kt, ui/dashboard/DashboardPluginFragment.kt

**why:** VERIFIED: MyLife's companion holds defaultItemPairs mapping imageIds to R.string ids, and LifeViewModel injects @ApplicationContext Context (line 21) solely to pass context::getString into MyLife.defaultItems (line 40). Android resource ids in a model block a platform-free core and force a Context into the ViewModel.

**work:** Change defaultItems to take resolved labels, have the callers build them, and drop the Context from the ViewModel.

**note:** INCOMPLETE AS SUBMITTED: it lists only LifeFragment as a caller. VERIFIED there is a second one - DashboardPluginFragment.getMyLifeListBase also calls MyLife.defaultItems(userId, requireContext()::getString) - so the change does not compile without updating it too, and that file is also the target of the localized-routing task. Sequence them and add the fourth file.

---

### 86. Expose team update signals through TeamsRepository instead of RealtimeSyncManager

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** codex (boundaries)

**files:** ui/teams/TeamViewModel.kt (getTeamUpdateFlow), repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt

**why:** VERIFIED: TeamViewModel imports RealtimeSyncManager (line 20), injects it (line 33) and exposes `fun getTeamUpdateFlow() = realtimeSyncManager.updatesFor("teams")` (line 41) - UI state depending on a sync service rather than its feature repository, and the "teams" collection-name literal living in the UI.

**work:** Add a narrowly named team-update Flow method to TeamsRepository whose return type matches updatesFor("teams"), delegate to the existing realtime manager inside TeamsRepositoryImpl with the fixed collection key, and remove RealtimeSyncManager from the ViewModel.

**note:** All three files are among the most PR-contended in the repo (TeamsRepositoryImpl is the 1437-line hotspot); feasibility, not correctness, is the constraint.

---

### 87. Reorder the server list without the parallel pair collections

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** codex (perf), devin (perf)  _(2 lists)_

**files:** ui/sync/ServerDialogExtensions.kt (refreshServerList)

**why:** VERIFIED: refreshServerList maps every server to a Pair, searches that list for the pinned entry, then filters and maps it again and concatenates just to place the pinned server first, and the submitList callback searches the derived pair list a third time for the index.

**work:** Find the pinned index directly in filteredList with the existing protocol-stripping regex; when pinning applies and a match exists, make one mutable copy and move that element to index 0, otherwise submit filteredList unchanged (preserving object identity and order). Reuse the computed match state in the submit callback instead of searching again.

**note:** Small list, cold path - this is code clarity more than performance.

---

### 88. Hoist the TypeToken out of SharedPrefManager.getSavedUsers

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf)

**files:** services/SharedPrefManager.kt (getSavedUsers)

**why:** VERIFIED: getSavedUsers constructs `object : TypeToken<List<User>>() {}.type` on every call to deserialize the saved-users JSON, and the login screen calls it on every refresh of the user list. The TypeToken and its reflective type are constant; data/room/Converters.kt already shows the cached-companion pattern for its list types. gson is already injected, so only the type allocation is waste.

**work:** Hold the type in a private val (companion or field) and use it in the fromJson call. No change to the User model.

---

### 89. Use addAll and drop the set-to-list copy in MyCourse.saveConcatenatedLinksToPrefs

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf)

**files:** model/MyCourse.kt (saveConcatenatedLinksToPrefs)

**why:** VERIFIED: the function deserializes the stored links into a HashSet, then runs `for (link in linksToProcess) { existingConcatenatedLinks.add(link) }` - one add per element where addAll would do - and finally serializes `existingConcatenatedLinks.toList()`, a redundant set-to-list copy since Gson.toJson accepts a Collection.

**work:** Replace the loop with addAll(linksToProcess) and serialize the set directly. The reader parses it back as Array<String> and calls toHashSet(), so order-independence is already relied upon.

---

### 90. Hoist the LayoutInflater out of the community services-link loop

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** openhands-kimi (perf)

**files:** ui/community/CommunityServicesFragment.kt (setRecyclerView)

**why:** VERIFIED: `links.forEach { ... LayoutInflater.from(activity).inflate(R.layout.button_single, parent, false) ... }` calls LayoutInflater.from inside the loop for every rendered service link, re-resolving the context theme and service on each call.

**work:** Resolve the inflater once before the loop (parent is non-null after the early return) and reuse it. Keep the padding, the addView order and every listener identical.

**note:** Third task touching this one fragment, alongside the route extraction and the ViewModel extraction - sequence them.

---

### 91. Use the already-known id in the notification delete branches

**rating:** 47/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf)

**files:** repository/NotificationsRepositoryImpl.kt (updateResourceNotification, updateStorageNotification)

**why:** VERIFIED: both methods construct notificationId at the top, call notificationDao.getById(notificationId), and in the delete branch call `existingNotification?.let { notificationDao.deleteById(it.id) }` - reading it.id off the loaded entity when the value is already the local notificationId.

**work:** Pass notificationId directly in both delete branches. Leave the upsert branches untouched.

**note:** Negligible on its own - fold it into the dedupe-the-two-upserts task, which rewrites both methods anyway.

---

### 92. Cache the resolved Teams label in TeamsSelectionAdapter.bind

**rating:** 47/100 &nbsp;·&nbsp; **proposed by:** openhands-glm (perf)

**files:** ui/teams/TeamsSelectionAdapter.kt (TeamSelectionViewHolder.bind)

**why:** VERIFIED: bind evaluates `if (section == itemView.context.getString(R.string.teams))` on every bind to pick between the team and business icon, re-resolving a localized string per visible row for a constructor value that never changes.

**work:** Resolve the comparison value once (an adapter-level lazy field) and compare against it in bind, keeping the icon selection identical.

**note:** The submitted order's own steps waver between three approaches; pick the lazy field and move on.

---

### 93. Inject the clock into NotificationsAdapter's relative-time formatting

**rating:** 47/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf)

**files:** ui/notifications/NotificationsAdapter.kt (formatRelativeTime)

**why:** VERIFIED: `val diff = System.currentTimeMillis() - createdAt` inside formatRelativeTime, called per bind, with a hand-rolled bucket ladder and its own DateTimeFormatter cache. utils/TimeUtils already offers getRelativeTime(timestamp, timeProvider).

**work:** Pass an injectable clock (a `now: () -> Long` from the hosting fragment, backed by TimeProvider) into the adapter and use it, keeping the existing thresholds and rendered strings identical. Add a Robolectric test for the minute/hour/day/fallback boundaries.

**note:** Reuse TimeUtils.getRelativeTime only if the rendered strings match exactly - its DateUtils-based output differs from this ladder. Do not edit TimeUtils (open PR).

---

### 94. Cheapen the StorageBreakdown full-tree scan

**rating:** 47/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** ui/settings/StorageBreakdownFragment.kt (scanStorage)

**why:** VERIFIED: opening the storage breakdown walks every file under the ole directory with walkTopDown(), aggregating sizes and counts per extension via StorageCategories.indexOf on each file.

**work:** Build the extension-to-category index once from StorageCategories instead of calling indexOf per file, and avoid the repeated extension lowercase allocation. Keep the scan off the main thread (the existing launch already does this) and the category totals and file counts identical for a fixture tree.

**note:** CAUTION: the submitted order's headline idea - a cache keyed on the ole directory's lastModified - is unsound, since a directory's mtime does not change when nested files are written. Take the per-file work reduction; if you want a cache, key it on an explicit invalidation from the delete flow, not on mtime.

---

### 95. Dispatch MyPlanet.getTabletUsages on IO and make its window testable

**rating:** 46/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf)

**files:** model/MyPlanet.kt (getTabletUsages), repository/ActivitiesRepositoryImpl.kt (uploadMyPlanetActivities)

**why:** VERIFIED: getTabletUsages runs a UsageStatsManager.queryUsageStats over the window from the last usage upload to System.currentTimeMillis() and loops addStats; ActivitiesRepositoryImpl calls it inline inside uploadMyPlanetActivities.

**work:** Wrap the call in withContext(dispatcherProvider.io) and give getTabletUsages a `now: Long = System.currentTimeMillis()` defaulted parameter so tests can pin the window. No change to the uploaded payload.

**note:** The main-thread risk is theoretical - the only caller is the upload path - so the testable window is the real gain.

---

### 96. Collapse the map-then-filter in flushPendingCourseResources

**rating:** 45/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** repository/CoursesRepositoryImpl.kt (line ~830)

**why:** VERIFIED: `batch.map { JsonUtils.getString("_id", it.doc) }.filter { it.isNotBlank() }` builds an intermediate list of strings before filtering.

**work:** Collapse into a single mapNotNull with a takeIf on the extracted id. Rename the inner lambda parameter rather than shadowing `it`. No functional change.

**note:** CoursesRepositoryImpl is claimed by several open PRs.

---

### 97. Deduplicate TagsRepositoryImpl's allTagIds in one pass

**rating:** 45/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** repository/TagsRepositoryImpl.kt (line ~76)

**why:** VERIFIED: `links.mapNotNull { it.tagId }.distinct()` - two list allocations to build a deduplicated id list.

**work:** Use mapNotNullTo(LinkedHashSet()) and convert back to a list. No change to how tags are fetched or to tag-linkage logic.

---

### 98. Bound the Glide decode size and align the disk-cache strategy in ImageUtils

**rating:** 45/100 &nbsp;·&nbsp; **proposed by:** copilot-grok (perf)

**files:** utils/ImageUtils.kt (loadImage, loadPlaceholderImage)

**why:** VERIFIED: loadProfileImage applies .override(sizePx, sizePx) and diskCacheStrategy(ALL); loadImage has the disk-cache strategy but no override; loadPlaceholderImage has neither.

**work:** Add an optional sizePx parameter (defaulted, so no call site outside this file needs editing) applying .override(size, size), and give loadPlaceholderImage the same diskCacheStrategy as its siblings. Preserve the placeholder/error/circleCrop behaviour.

**note:** PREMISE OVERSTATED: the order claims these 'decode full-resolution bitmaps'. Glide already sizes decodes to the measured target view, so .override() only matters for unmeasured/wrap_content targets. The missing diskCacheStrategy on loadPlaceholderImage is the concrete win.

---

### 99. Deduplicate the pendingSurveys examIds in one pass

**rating:** 44/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** repository/SubmissionsRepositoryImpl.kt (line ~109)

**why:** VERIFIED: `pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()` - mapNotNull allocates a list and distinct() allocates another to guarantee uniqueness.

**work:** Use mapNotNullTo(LinkedHashSet()) and convert back to a list, deduplicating during insertion while preserving order. Do not touch the examIdFromParentId extension or any survey-completion logic.

---

### 100. Short-circuit the crash-log capacity check

**rating:** 44/100 &nbsp;·&nbsp; **proposed by:** codex (perf)

**files:** utils/CrashLogStore.kt (save)

**why:** VERIFIED: `(logDir.listFiles()?.count { isValidLogFile(it) } ?: 0) >= MAX_PENDING_FILES` parses the filename of every file in the pending-log directory even though it only needs to know whether 20 valid reports exist. Crash and ANR persistence is latency-sensitive and deliberately synchronous.

**work:** Convert the listFiles result to a lazy sequence for the capacity check only, filter with the existing isValidLogFile predicate and stop as soon as MAX_PENDING_FILES valid entries are seen. Preserve the rule that malformed neighbours do not consume capacity, plus the exception handling and the create-or-return-null contract.

---

### 101. Merge the filter and mapNotNull in ResourcesRepositoryImpl.downloadResources

**rating:** 43/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** repository/ResourcesRepositoryImpl.kt (line ~466)

**why:** VERIFIED: `resources.filter { !it.isResourceOffline() }.mapNotNull { it.resourceRemoteAddress }` - two passes with an intermediate list before the URL list is built.

**work:** Merge into a single mapNotNull returning null for offline resources. Offline resources must still be excluded and the same remote addresses extracted.

**note:** ResourcesRepositoryImpl is claimed by an open PR.

---

### 102. One-pass expandedGroups set in ChatHistoryAdapter

**rating:** 43/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** ui/chat/ChatHistoryAdapter.kt (the share-target click handler, line ~154)

**why:** VERIFIED: `currentFlatList.filter { it.isGroup && it.isExpanded }.map { it.title }.toMutableSet()` walks the share-target list twice and creates a throwaway filtered list plus a mapped list on every expand/collapse tap.

**work:** Build a LinkedHashSet in one loop over currentFlatList. Preserve the surrounding remove/add of the clicked title and leave generateFlatList and the isExpanded lookup above it alone.

---

### 103. Iterate entrySet instead of keySet in two repository JSON loops

**rating:** 42/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf)

**files:** repository/UserRepositoryImpl.kt (lines ~465-479), repository/HealthRepositoryImpl.kt (lines ~92-95)

**why:** VERIFIED both: UserRepositoryImpl does `payload.keySet().forEach { key -> when (key) { ... payload.get(key).asString ... } }` - a set-view allocation plus a second lookup per key across a 12-branch when - and HealthRepositoryImpl does `for (key in conditions.keySet()) { result[key] = JsonUtils.getBoolean(key, conditions) }`, the same pattern.

**work:** Iterate entrySet() and read entry.value, keeping the same primitive guards and null-safety the current asString / JsonUtils.getBoolean calls rely on (replicate the isJsonPrimitive check inline rather than editing JsonUtils). Output maps must be identical; test non-primitive values and empty objects.

**note:** Small JSON objects on cold paths - the value is uniformity, not speed.

---

### 104. Pre-size the exam upload result buckets

**rating:** 41/100 &nbsp;·&nbsp; **proposed by:** codex (perf)

**files:** repository/UploadRepositoryImpl.kt (markExamsUploaded)

**why:** VERIFIED: markExamsUploaded creates default-capacity mutable lists for the updated exams and the local-lookup failures and grows them while partitioning the successful network results.

**work:** Give the updated-exam bucket capacity equal to the succeeded count and the failure bucket a small hint, using explicit add calls. Preserve input ordering, missing-local-row reporting, revision assignment and the single upsertAll.

**note:** Effectively free but also effectively worthless: ArrayList growth over a <=50-item batch is noise. Take it only as a drive-by while already in this function.

---

### 105. Narrow the TagsRepository course-link API

**rating:** 40/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** repository/TagsRepository.kt, repository/TagsRepositoryImpl.kt

**why:** VERIFIED: CoursesRepositoryImpl line 292 calls `tagsRepository.getLinkIdsForTagNames("courses", tagNames).toSet()`, so the "courses" db-name literal lives in the courses repository rather than behind the tags contract.

**work:** Add getCourseLinkIds(tagNames): Set<String> to TagsRepository, implemented as getLinkIdsForTagNames("courses", tagNames).toSet(), and update the CoursesRepositoryImpl call site in the same change.

**note:** As submitted the order defers the call-site update (because CoursesRepositoryImpl is PR-contended), which would land a method with only a test as its caller. Either include the one-line call-site change or skip the task.

---

### 106. Move the viewer's file operations behind ResourcesRepository

**rating:** 40/100 &nbsp;·&nbsp; **proposed by:** codex (boundaries)

**files:** ui/viewer/ResourceViewerViewModel.kt (getExternalFilesDir, downloadResource, extractPdfText), repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt

**why:** VERIFIED: ResourceViewerViewModel injects @ApplicationContext Context, calls context.getExternalFilesDir(null), starts a download service, and initializes Android PDFBox (PDFBoxResourceLoader.init(context)) to extract text - data and file operations bypassing the repository boundary and keeping a prospective Compose screen tied to Android.

**work:** Add repository operations matching the three behaviours with the same return values (including the empty-string-on-PDF-error contract and the no-download-when-file-exists guard) and make the ViewModel delegate.

**note:** DESIGN CONCERN: this moves Android service-start and PDFBox initialization *into* a repository, which is not obviously better layering than where they are - and ResourcesRepository(Impl) is claimed by an open PR. Consider a dedicated file/PDF gateway instead of the repository.

---

### 107. Use isNullOrEmpty instead of the size-Elvis idiom in ExamTakingFragment

**rating:** 40/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** ui/exam/ExamTakingFragment.kt (line ~118)

**why:** VERIFIED: `if ((questions?.size ?: 0) > 0)` - a nullable-list emptiness test written with an Elvis operator and an integer comparison.

**work:** Replace with `if (!questions.isNullOrEmpty())`. No behaviour change.

**note:** ExamTakingFragment is claimed by open PR #16698 in several surveys; a 2-line change is not worth a conflict - fold it into whatever else touches this file.

---

### 108. Replace SyncRepositoryImpl's shelf when-block with a dispatch map

**rating:** 38/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (boundaries)

**files:** repository/SyncRepositoryImpl.kt (processShelfDataOptimizedSync)

**why:** VERIFIED: `when (shelfData.type) { "resources" -> resourcesRepository.batchInsertMyLibrary(...); "courses" -> coursesRepository.batchInsertMyCourses(...); "meetups" -> eventsRepository.batchInsertMeetups(...); "teams" -> teamsSyncRepository.batchInsertMyTeams(...) }`, with the four feature repositories injected for it.

**work:** Replace the when with a private map from shelf type to the corresponding batch-insert function reference, and add a test pinning today's silent behaviour for an unknown shelf type (contributes 0, does not throw).

**note:** Lateral at best as specified: the order explicitly keeps all four injections, so the coupling it complains about stays. The unknown-type test is the durable part. Consider dropping the rest.

---

### 109. Use isNullOrEmpty instead of the size-Elvis idiom in ResourcesFragment

**rating:** 38/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** ui/resources/ResourcesFragment.kt (line ~409, setupAddToLibListener)

**why:** VERIFIED: `if ((selectedItems?.size ?: 0) > 0)` in the add-to-library click listener.

**work:** Replace with `if (!selectedItems.isNullOrEmpty())`. No change to the adapter's selection logic.

**note:** ResourcesFragment is claimed by open PRs #16702/#16661/#16647 - almost certainly not worth landing standalone.

---

### 110. Use isNullOrEmpty instead of the size-Elvis idiom in CoursesFragment

**rating:** 38/100 &nbsp;·&nbsp; **proposed by:** jules (perf)

**files:** ui/courses/CoursesFragment.kt (line ~268, the onAddToLib lambda)

**why:** VERIFIED: `if ((selectedItems?.size ?: 0) > 0)` in the onAddToLib callback.

**work:** Replace with `if (!selectedItems.isNullOrEmpty())`. No change to how courses are displayed or filtered.

**note:** CoursesFragment is claimed by open PRs #16701/#16647 - same caveat as the ResourcesFragment twin.

---

### 111. Sequence-chain the distinct/sort pipelines in LifeRepositoryImpl

**rating:** 32/100 &nbsp;·&nbsp; **proposed by:** devin (perf)

**files:** repository/LifeRepositoryImpl.kt (getMyLifeByUserId, getVisibleMyLifeByUserId, getMyLifeForDashboard)

**why:** VERIFIED: DAO results are materialized through `.distinctBy { it.dedupKey() }.sortedBy { it.weight }` and `.filter { ... }.sortedBy { ... }` at five sites, each pair creating one intermediate list before sorting.

**work:** If taken: convert to asSequence() chains ending in toList(), keeping return types, deduplication and weight ordering.

**note:** NO MEASURABLE BENEFIT: sortedBy on a Sequence materializes into a list anyway, so the sequence version trades one intermediate list for sequence wrappers plus that same list - on a seven-item My Life list. Listed for completeness; recommend closing as won't-fix.

---

### 112. Reuse the MyLife instances in LifeAdapter's reorder

**rating:** 30/100 &nbsp;·&nbsp; **proposed by:** copilot-kimi (perf)

**files:** ui/life/LifeAdapter.kt (onItemMoveFinished)

**why:** VERIFIED: onItemMoveFinished rebuilds every element - `list.mapIndexed { index, item -> MyLife().apply { _id = item._id; ...; weight = index } }` - allocating N fresh entity objects per drag gesture purely to re-rank.

**work:** If taken: update ranks without rebuilding instances and persist through the existing reorder callback.

**note:** LOWEST-CONFIDENCE ITEM IN THE SET - premise true, remedy doubtful. My Life holds seven items, so the allocation is noise, and mutating instances that are already in a ListAdapter's current list defeats DiffUtil's areContentsTheSame: constructing new objects is arguably the correct pattern here. Recommend closing as won't-fix unless a profile says otherwise.

---

## Dropped (premise did not survive verification)

- **openhands-glm (perf)** — hoist the diagnostics lookups in the single-log path (DiagnosticsRepositoryImpl.saveLogToRoom)
  Premise claims the four lookups (getUserModel, resolveParentCode, resolvePlanetCode, getVersionName) are re-resolved 'for every single log row'. VERIFIED saveLogToRoom writes exactly ONE row and already calls each of them exactly once, as arguments to a single buildApkLog call. Hoisting them into locals is a no-op.

- **openhands-kimi (perf)** — run the community-tab init queries concurrently (CommunityTabViewModel)
  Premise claims four independent suspends / 'four serialized Room round-trips'. VERIFIED ConfigurationsRepository.getParentCode, getCommunityName and getPlanetType are all NON-suspend synchronous SharedPreferences reads (ConfigurationsRepositoryImpl:392-402); only getUserModel() suspends. There is nothing to parallelize and async would add pure overhead.

- **openhands-kimi (perf)** — fetch the formatted app size concurrently with the storage breakdown (StorageBreakdownViewModel)
  File does not exist. VERIFIED ui/settings/ contains SettingsActivity, SettingsViewModel, StorageBreakdownFragment, StorageCategories, StorageCategoryDetailFragment, StorageCategoryViewModel - there is no StorageBreakdownViewModel.kt, and the cited loadStorageBreakdown/getFormattedAppSize/getStorageBreakdown members do not exist.

- **copilot-kimi (perf)** — add distinctUntilChanged() to SubmissionDetailViewModel's derived StateFlows
  Premise claims 'any upstream re-emission re-renders all five consumers'. VERIFIED all five flows terminate in stateIn(...), and StateFlow conflates by equality - it never emits the same value twice to a collector. Adding distinctUntilChanged() before stateIn is a no-op. (The five-flows-into-one-UiState consolidation from another list is kept instead.)

- **devin (perf)** — avoid intermediate collections in StorageBreakdownFragment scan and populate
  Premise claims both filter/forEach pairs 'allocate intermediate lists'. VERIFIED the main one does not: File.walkTopDown() returns a FileTreeWalk, i.e. a lazy Sequence, so .filter{}.forEach{} allocates no list. Only the second site (categories.filter{}.forEach{}) copies a list, and `categories` holds ~8 entries.

- **jules (boundaries)** — all ten orders: replace list .size checks with count queries for empty states (TeamCourses, TeamsTasks, TeamsVoices, Courses, Resources, Members x2, Requests, Voices, EnterprisesReports fragments)
  Shared premise - 'loading full objects into the Fragment just for an empty check wastes memory; use a count query' - is false at every one of the ten cited sites. VERIFIED in each case the list is submitted to an adapter for DISPLAY on the line immediately above (e.g. TeamCoursesFragment:56 submitList(state.courses) then :57 showNoData(..., state.courses.size); same shape in MembersFragment:102, EnterprisesReportsFragment:396, RequestsFragment, VoicesFragment). The .size read is O(1) on an already-materialized list; adding a count to UI state would ADD a query. Several cited files are also open-PR-owned. This list also carries a stale date header (2024-05-24) and states open PRs could not be checked.

- **copilot-kimi (boundaries) [variant only]** — task 4 primary variant: remove the UserRepositoryImpl -> ActivitiesRepository forwarding methods
  Premise claims UserRepositoryImpl:94 getOfflineLoginCount is a forwarding interface method. VERIFIED getOfflineLoginCount is NOT declared on UserRepository at all - line 94 is an internal use inside another method - so only hasUserSyncAction forwards, and it is also called internally at line 893. The order's own conditional fallback (4-alt, route the personals POST through UploadRepository) is verified and is what shipped.


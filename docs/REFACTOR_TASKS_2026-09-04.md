# myPlanet refactor round — performance quick wins

**Date:** 2026-09-04
**Base commit:** `9ff1273dc95f8cbd3590fca12ca821454b2e27bc` (`master`, "courses: smoother progress batch deleting (fixes #16694) (#16676)")
**Open PRs checked (48):** 16705, 16702, 16701, 16698, 16693, 16690, 16688, 16686, 16680, 16677, 16661, 16647, 16624, 16623, 16619, 16594, 16270, 16101, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

Every file touched by any of those 48 branches (915 paths, 179 of them under `app/src/main/java`) was excluded before ranking. In particular the following hot areas are **off-limits this round** because open PRs own them: `ResourcesRepositoryImpl`/`ResourcesFragment`/`ResourcesViewModel` (#16661, #16702, #16705), `CoursesRepositoryImpl`/`CoursesFragment`/`CoursesViewModel`/`CourseFilterController` (#16647, #16698, #16701, #16624), `TeamsRepositoryImpl`/`TeamViewModel` (#15951, #16623), `TimeUtils` (#16686), `JsonUtils` (#16680), `ResourcesSearchUtils` (#16624), `NotificationDao` (#16677), `TransactionSyncManager` (#15808), `EdgeToEdgeUtils` and every activity that calls it (#16101), `BaseDashboardFragment` (#16619), `ResourceViewerActivity`/`WebViewActivity`/`RatingsRepositoryImpl` (#15699), `AppDatabase` (#16661, #15699).

All ten tasks below are independently mergeable in any order. No file appears in two tasks. No task adds a dependency.

---

### 1. stop counting team chat messages for teams that have no chat badge (roadmap 1+7)

context: `NotificationsRepositoryImpl.getTeamNotifications` runs one `SELECT COUNT` per team — `repository/NotificationsRepositoryImpl.kt:310-312` loops `for (teamId in teamIds) { chatCountsById[teamId] = voicesRepository.countTopLevelByTeam(teamId) }` — but the count is only ever consumed at line 322 as `notification != null && notification.lastCount < chatCount`, so every team without a `chat` row in `teamNotificationDao.getByTypeAndParentIds("chat", teamIds)` (line 301) pays for a query whose result is discarded. This runs on the dashboard team strip, so a learner in 20 teams with 2 chat-tracked teams issues 20 queries where 2 suffice.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (the `getTeamNotifications` function, lines 295-326 only) and `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`. Do **NOT** touch `repository/VoicesRepositoryImpl.kt`, `repository/VoicesRepository.kt` or `data/room/dao/NewsDao.kt` — open PRs #13357 and #15112 own them, so no bulk-count DAO method may be added. Leave `getJoinRequestDetailsBatch` and `getTaskTeamNamesByTaskTitles` in the same file alone. `app/src/test/java/org/ole/planet/myplanet/repository/TeamChatBadgeIntegrationTest.kt` must keep passing but must not be edited.

steps:
1. In `getTeamNotifications`, build the `notificationsById` map first (already done at lines 301-307), then replace the `for (teamId in teamIds)` count loop with a loop over `notificationsById.keys` only.
2. Keep `chatCountsById` typed `Map<String, Long>` and keep the `?: 0L` fallback in the result loop at line 321 so teams with no row behave exactly as before (`hasChat = false`).
3. Add a test to `NotificationsRepositoryImplTest.kt` that passes three team ids with a `chat` `TeamNotification` for only one of them and asserts with MockK `coVerify(exactly = 1) { voicesRepository.countTopLevelByTeam(any()) }` plus `coVerify(exactly = 0) { voicesRepository.countTopLevelByTeam("teamWithoutRow") }`.
4. Add a second assertion that the returned `TeamNotificationInfo.hasChat` values are unchanged for all three teams.

acceptance: `./gradlew testDefaultDebugUnitTest` green. On the dashboard, the chat dot on team chips appears and clears exactly as before for teams that have received chat messages, and teams that never had a chat notification still show no dot.

size budget: ~12 changed lines in the repository, ~35 added test lines; 2 files.

out of scope: do not add a batch/`IN (:ids)` count query (that needs the off-limits DAO), and do not change how `hasTask` is computed.

---

### 2. stop re-reading SharedPreferences on every download notification tick (roadmap 5+7)

context: `DownloadService.sendNotification` (`services/DownloadService.kt:451`) is invoked every 500 ms while a file downloads (`NOTIFICATION_UPDATE_INTERVAL_MS`, line 554, checked at line 429) and calls `getRemainingCount()` (line 171), which does two `preferences.getStringSet(...)` reads, allocates the union `priority + pendingUrls`, and then filters it against `processedUrls` — all on the download thread, purely to render the "N remaining" subtext. The queue only changes between files (`cleanupProcessedUrls`, line 154), so the value is recomputed hundreds of times per file for nothing. The same function also wraps the response body in `BufferedInputStream(body.byteStream(), 1024 * 8)` (line 375) while reading into a `1024 * 16` array (`BUFFER_SIZE`, line 548) — reads larger than the buffer bypass it, so the wrapper is dead weight.

files: `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` (`getRemainingCount`, `sendNotification`, `processDownloadQueue`, `downloadFile`, `updateNotificationForBatchDownload`) and `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`. Do **NOT** touch `utils/DownloadUtils.kt`, `services/DownloadWorker.kt`, or `services/FreeSpaceWorker.kt`. `DownloadServiceOnDownloadCompleteTest.kt` and `DownloadServiceUrlSelectionTest.kt` must keep passing untouched.

steps:
1. Add a private `@Volatile var cachedRemainingCount: Int = 0` field and a private `refreshRemainingCount()` that assigns it from the existing `getRemainingCount()`.
2. Call `refreshRemainingCount()` at the two points where the queue actually changes: after `processedUrls.add(nextUrl.url)` in `processDownloadQueue` (line ~148) and after `cleanupProcessedUrls()` (line ~154).
3. Change `sendNotification` and `updateNotificationForBatchDownload` to read `cachedRemainingCount` instead of calling `getRemainingCount()`. Leave `onDownloadComplete` (line ~493) calling `getRemainingCount(priorityUrls)` directly — it needs the post-download value.
4. Raise the `BufferedInputStream` buffer at line 375 to `BUFFER_SIZE` so it matches the read array.
5. Add a test to `DownloadServiceTest.kt` asserting `getRemainingCount()` returns the same value it does today for a mixed priority/pending preference set, so the caching change is pinned to existing behavior.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Downloading several resources still shows a progress notification whose "X completed, Y remaining" subtext decrements once per finished file, and the completion notification still fires.

size budget: ~25 changed lines, ~20 added test lines; 2 files.

out of scope: do not change the download retry/alternative-URL logic, and do not alter notification channels or IDs.

---

### 3. move the inline-resource download check off the main thread (roadmap 6+7)

context: `InlineResourceAdapter.updateStatusAndPreview` is called straight from `onBindViewHolder` (`ui/courses/InlineResourceAdapter.kt:149`, `:162`) and at line 165 calls `FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))`, which does `f.exists() && f.length() > 0` (`utils/FileUtils.kt:154-157`) — two disk stats per row, per bind, on the UI thread. Every sibling preview path in the same class already does the right thing (`withContext(dispatcherProvider.io) { file.exists() }` at lines 206, 221, 232), so this is an inconsistency that costs frames while scrolling a course step's resource list. Hoisting the check into the existing coroutine also removes the last blocking Android I/O call from the bind path, which is what a Compose port of this row will need (roadmap 10).

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt` (`updateStatusAndPreview` only) and `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`. Do **NOT** touch `utils/FileUtils.kt` or `ui/courses/CourseStepFragment.kt`; `ui/resources/ResourcesAdapter.kt` makes the same call but is owned by open PR #16661 — leave it.

steps:
1. Set the row to its "not downloaded" state synchronously (progress bar visible, status icon gone) as the default, exactly as the current `else` branch does.
2. Move the `resource.isResourceOffline() || FileUtils.checkFileExist(...)` evaluation inside the existing `holder.setPreviewJob(adapterScope.launch { ... })` block, wrapping the `checkFileExist` call in `withContext(dispatcherProvider.io) { ... }`, and short-circuit on `resource.isResourceOffline()` first so offline-flagged rows skip disk entirely.
3. Inside that coroutine, flip to the downloaded state (`ivStatus` visible with `R.drawable.ic_eye`, `pbDownload` gone) before dispatching to the existing `when (mimeType)` preview branches.
4. Keep `holder.cancelPreviousPreviews()` and the `ivResourceIcon` assignment where they are so recycled rows still cancel stale work.
5. Add a test to `InlineResourceAdapterTest.kt` that binds a resource whose `resourceOffline` is true and asserts the status icon becomes visible without any file being created on disk.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Opening a course step with downloaded resources still shows the eye icon and the image/PDF/audio preview; undownloaded resources still show the progress bar; scrolling a long resource list is visibly smoother.

size budget: ~35 changed lines, ~25 added test lines; 2 files.

out of scope: do not change `PdfThumbnailLoader`, the text/CSV caches, or the Glide request options.

---

### 4. cache the invariant device identity and system services in NetworkUtils (roadmap 5+7+9)

context: `NetworkUtils.getDeviceName()` (`utils/NetworkUtils.kt:215`) re-reads `Build.MANUFACTURER`/`Build.MODEL`, concatenates and uppercases on every call, and it is called once per serialized document during uploads — `model/Personal.kt:49`, `model/NewsLog.kt:34`, `model/ApkLog.kt:41`, `model/Rating.kt:56`, `model/CourseActivity.kt:40`, `repository/ActivitiesRepositoryImpl.kt:306`, `services/UploadManager.kt:141` — so a bulk activity upload allocates thousands of throwaway strings. `getUniqueIdentifier()` (line 209) rebuilds the same `androidId + "_" + buildId` string each call. `isWifiEnabled()` (line 166) and `isBluetoothEnabled()` (line 181) call `getSystemService` on every invocation even though the object never changes; the file already has a `ResettableCache` helper (line 30) and a `connectivityManager` cache (line 56) that show the intended pattern.

files: `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt` and its tests `app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsTest.kt`, `.../NetworkUtilsMockTest.kt`, `.../NetworkUtilsStateTest.kt`. Do **NOT** touch `utils/VersionUtils.kt` (it already caches the Android ID at line 43) and do **NOT** change the signature of `getCustomDeviceName(context)` — its callers include files owned by open PRs.

steps:
1. Add `private val deviceNameCache = ResettableCache { ... }` holding the current `getDeviceName()` body, and make `getDeviceName()` return it.
2. Add `uniqueIdentifierCache` the same way for `getUniqueIdentifier()`.
3. Add `wifiManagerCache` and `bluetoothManagerCache` `ResettableCache` entries; keep `isWifiEnabled()` reading `wifiManager.isWifiEnabled` and `isBluetoothEnabled()` reading `adapter?.isEnabled` live off the cached manager, so the enabled state stays dynamic.
4. Register all four new caches in the `resettableCaches` list (line 72) so `resetForTesting()` (line 105) still clears them.
5. Run the three NetworkUtils test classes and adjust only what the new caching breaks.

acceptance: `./gradlew testDefaultDebugUnitTest` green, and specifically `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.NetworkUtils*"`. Toggling Wi-Fi or Bluetooth on the device still flips the values reported on the sync/login screens; the device name shown on the login screen is unchanged.

size budget: ~40 changed lines, ~10 test lines; up to 4 files.

out of scope: do not touch `getCurrentNetworkId`, the `NetworkCallback`, or `isNetworkConnectedFlow`.

---

### 5. guard SyncManager's debug logging behind Log.isLoggable (roadmap 5+7+8)

context: `services/sync/SyncManager.kt` makes 20 unconditional `Log.d("SyncPerf", …)` calls with string interpolation and zero `isLoggable` guards — lines 139-141, 207-217, 274, 305, 382, 422, 427, 505, 516, 558, 563. Line 382 fires once per resource batch and interpolates a percentage computation, and lines 139/208 format an `Instant` on every sync. `SyncTimeLogger` in the same subsystem already wraps every one of its `Log.d` calls in `if (Log.isLoggable("SyncPerf", Log.DEBUG))` (`utils/SyncTimeLogger.kt:71, 86, 148, 168, 185, 196`), so SyncManager is the odd one out: on a release device where the tag is not loggable the app still pays for the formatting.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` (the `Log.d("SyncPerf", …)` statements only) and `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt`. Do **NOT** touch `utils/SyncTimeLogger.kt`, `services/sync/TransactionSyncManager.kt` (open PR #15808), or `services/sync/HeavyTableSyncWorker.kt` (open PR #16661). Leave every `Log.e`/`Log.w` call as-is.

steps:
1. Add a private helper in `SyncManager`, e.g. `private inline fun syncPerf(message: () -> String) { if (Log.isLoggable("SyncPerf", Log.DEBUG)) Log.d("SyncPerf", message()) }`, placed next to the existing companion/private members.
2. Replace all 20 `Log.d("SyncPerf", "…")` call sites with `syncPerf { "…" }`, keeping the message text byte-identical so log scraping still works.
3. Where a block emits three banner lines together (139-141, 207-210, 214-217), wrap the whole block in a single `if (Log.isLoggable(...))` instead of three helper calls, so the `timestampFormat.format(Instant.now())` work is also skipped.
4. Confirm no message string has a side effect (none of them do — they are all interpolations of already-computed locals).
5. Add a test to `SyncManagerTest.kt` only if the class already exposes a seam for it; otherwise leave the tests untouched and just keep them green.

acceptance: `./gradlew testDefaultDebugUnitTest` green. `adb shell setprop log.tag.SyncPerf DEBUG` then running a manual sync still prints the same "FULL SYNC STARTED / Resources batch N / Library sync completed" lines in the same order.

size budget: ~45 changed lines, 1-2 files.

out of scope: do not change what is logged, do not remove any log line, and do not touch the `syncTimeLogger.logDetail(...)` call sites.

---

### 6. stop stat-ing enterprise attachment files on every row bind (roadmap 7)

context: `EnterprisesFinancesAdapter.bindFinanceImage` (`ui/enterprises/EnterprisesFinancesAdapter.kt:59-61`) and `EnterprisesReportsAdapter.bindReportImage` (`ui/enterprises/EnterprisesReportsAdapter.kt:98-100`) both build a `File` via `MyTeam.getAttachmentFile(...)` and call `imageFile.exists()` directly inside `onBindViewHolder` (lines 36 and 48 respectively). That is a disk stat on the UI thread for every visible row, repeated on every recycle, in two lists that a team treasurer scrolls constantly. The value only changes when the underlying list changes, and `ListAdapter` already gives both classes an `onCurrentListChanged` hook to invalidate on.

files: `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapterTest.kt`. Do **NOT** touch `model/MyTeam.kt` (open PR #15951 owns it), `EnterprisesFinancesFragment.kt`, `EnterprisesReportsFragment.kt`, or `utils/ImageViewerUtils.kt`.

steps:
1. In each adapter add `private val attachmentExists = HashMap<String, Boolean>()`.
2. Change the existence test to `attachmentExists.getOrPut(imageFile.absolutePath) { imageFile.exists() }`, keeping the null-file early return and the `View.GONE` branch identical.
3. Override `onCurrentListChanged(previousList, currentList)` in each adapter to clear the map, so a re-submitted list after an upload or delete re-stats.
4. Leave the `onViewRecycled` Glide `clear(...)` calls exactly as they are.
5. Add a test to `EnterprisesReportsAdapterTest.kt` that binds the same report twice against a temp file and asserts the row visibility is identical both times (behavior pin), plus one that submits a new list and asserts a newly created file is picked up.

acceptance: `./gradlew testDefaultDebugUnitTest` green. In an enterprise's Finances and Reports tabs, rows with an attached image still show the thumbnail and open the zoomable viewer on tap; rows without an attachment still hide the image; adding a report with an image and returning to the list shows its thumbnail.

size budget: ~30 changed lines across the two adapters, ~30 added test lines; 3 files.

out of scope: do not move the image loading into the ViewModel, and do not change the Glide placeholder/error drawables.

---

### 7. add an ASCII fast path to Utilities.normalizeText (roadmap 7+9)

context: `Utilities.normalizeText` (`utils/Utilities.kt:80-83`) runs `Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)` followed by `.replace(DIACRITICS_REGEX, "")`, which allocates a fresh `Matcher` on every call. It is the per-item primitive of offline search and course filtering — every candidate title, and every query token, goes through it on every keystroke — yet the overwhelming majority of English-locale titles are pure ASCII, for which NFD decomposition and the combining-marks regex are guaranteed no-ops. Making the ASCII case allocation-free is a one-branch change with no behavior difference, and it keeps the function free of Android APIs, which matters for the platform-free core (roadmap 9).

files: `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt` (the `normalizeText` function only) and `app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt`. Do **NOT** touch `utils/ResourcesSearchUtils.kt` (open PR #16624) or `repository/CoursesRepositoryImpl.kt` / `repository/ResourcesRepositoryImpl.kt` (open PRs #16624, #16661) even though they are the callers, and do not edit `CoursesRepositoryImplTest.kt` or `ResourcesRepositoryImplTest.kt`, which also assert on this function.

steps:
1. Compute `val lower = str.lowercase(Locale.getDefault())` once.
2. Return `lower` immediately when every char satisfies `it.code < 0x80` (ASCII cannot carry combining marks, so NFD + the diacritics strip are identity).
3. Otherwise fall through to the existing `Normalizer.normalize(lower, NFD).replace(DIACRITICS_REGEX, "")`.
4. Add cases to `UtilitiesTest.kt` covering the fast path (`"HELLO World"` → `"hello world"`, `""` → `""`, digits/punctuation unchanged) and the slow path (`"Café"` → `"cafe"`, `"Niño"` → `"nino"`, `"äëïöü"` → `"aeiou"`, Arabic and Nepali strings round-tripping unchanged).
5. Verify the pre-existing normalizeText assertions in the two off-limits repository test classes still pass without edits.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Searching the resources and courses lists still matches accented titles when typing unaccented text (e.g. "cafe" finds "Café") and still matches case-insensitively.

size budget: ~10 changed lines, ~25 added test lines; 2 files.

out of scope: do not change the locale used for lowercasing, and do not introduce a memoization cache.

---

### 8. drain the retry queue with bounded concurrency instead of one request at a time (roadmap 5+7)

context: `RetryQueueWorker.doWork` chunks pending operations into batches of 50 (`services/retry/RetryQueueWorker.kt:42`, `:125`) and then processes each batch with `batch.forEach { operation -> processOperation(...) }` (line 132-135) — fully sequential network round trips, so the chunking buys nothing and a backlog of 200 failed uploads takes 200 serial requests inside a 5-minute `withTimeout` (line 124) that will often expire first. `UploadCoordinator` in the same codebase already solves this with `async` plus a `Semaphore(MAX_CONCURRENT_UPLOADS = 6)` (`services/upload/UploadCoordinator.kt:33`, `:128-145`), so the pattern to copy is local and proven.

files: `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt` (`doWork` only) and `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt`. Do **NOT** touch `services/retry/RetryQueue.kt`, `services/upload/UploadCoordinator.kt` (task 10 owns it), or `repository/RetryRepositoryImpl.kt`.

steps:
1. Add `private const val MAX_CONCURRENT_RETRIES = 6` next to `BATCH_SIZE` in the companion object.
2. Inside the `withTimeout` block, replace the inner `batch.forEach { ... }` with `coroutineScope { batch.map { op -> async { semaphore.withPermit { processOperation(op, baseUrl, authHeader) } } }.awaitAll() }`, creating the `Semaphore(MAX_CONCURRENT_RETRIES)` once before the `chunked(...)` loop.
3. Replace the racy `successCount++`/`failureCount++` increments with a count over the returned `List<Boolean>` after `awaitAll()` (`results.count { it }` / `results.count { !it }`), accumulating into the existing outer vars.
4. Keep the `MainApplication.isSyncRunning.get()` check at the top of each batch (line 127) and the `return@withTimeout` bail-out unchanged, so a starting sync still pauses the drain between batches.
5. Extend `RetryQueueWorkerTest.kt` with a test that seeds more operations than `MAX_CONCURRENT_RETRIES`, and asserts every operation was attempted exactly once and the success/failure tallies match a mixed success/failure fixture.

acceptance: `./gradlew testDefaultDebugUnitTest` green, and specifically `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.retry.*"`. After forcing several upload failures offline and reconnecting, the retry worker clears the queue (Logcat `RETRY_QUEUE: Complete - N succeeded, 0 failed`) noticeably faster and no operation is retried twice.

size budget: ~30 changed lines, ~40 added test lines; 2 files.

out of scope: do not change the per-operation retry/backoff policy in `processOperationInternal`, and do not change the 5-minute timeout or the periodic schedule.

---

### 9. cache the resolved CouchDB base URL the way the auth header already is (roadmap 5+7)

context: `UrlUtils.getUrl()` (`utils/UrlUtils.kt:159`) resolves to `dbUrl(spm())` → `baseUrl(spm)` (line 103), which performs an `isAlternativeUrl()` boolean read plus one or two `SharedPreferences` string reads, a suffix strip and two concatenations — on every call. It is on genuinely hot paths: every serialized upload document, `getUserImageUrl`/`getCourseImageUrl` per avatar row, and `UrlUtils.getUrl(resource)` per resource row bind. The same object already caches the far cheaper Basic auth header behind a `@Volatile generation` counter (lines 15-67), so the mechanism exists. The reason it was not applied to the URL is that invalidation is incomplete: `SharedPrefManager.setCouchdbUrl` (line 166), `setProcessedAlternativeUrl` (line 190) and `setIsAlternativeUrl` (line 193) do not call `UrlUtils.invalidateHeaderCache()`, unlike `setUrlUser`/`setUrlPwd` (lines 169-179) and `ServerUrlMapper.updateUrlPreferences` (`services/sync/ServerUrlMapper.kt:94`).

files: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt` (the three setters named above only), `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/SharedPrefManagerTest.kt`. Do **NOT** touch `services/sync/ServerUrlMapper.kt` or `utils/ServerConfigUtils.kt`.

steps:
1. Add a `@Volatile private var cachedBaseUrl: String?` field and clear it inside the existing `invalidateHeaderCache()` (line 34), which keeps its name and still bumps the shared `generation`.
2. Add the cache to `baseUrl(spm)` using the identical read/compute/compare-generation/store shape as the `header` getter, and reset it in `resetForTesting()` (line 42).
3. Add `UrlUtils.invalidateHeaderCache()` to `setCouchdbUrl`, `setProcessedAlternativeUrl` and `setIsAlternativeUrl` in `SharedPrefManager`, matching what `setUrlUser`/`setUrlPwd` already do.
4. Extend `UrlUtilsTest.kt` with a test that reads `getUrl()`, changes the backing preference, calls `invalidateHeaderCache()`, and asserts the new URL is returned — plus one asserting repeated `getUrl()` calls with no preference change hit the preferences only once (MockK `verify(exactly = 1)` on the SharedPrefManager getter).
5. Extend `SharedPrefManagerTest.kt` with assertions that each of the three setters invalidates the cache.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Switching between the primary and an alternative server (Settings → server URL, or an automatic failover during sync) still targets the new host on the very next request: log in against server A, switch to server B, sync, and confirm resources download from B.

size budget: ~45 changed lines across the two source files, ~35 added test lines; 4 files.

out of scope: do not cache `hostUrl` (it has different invalidation), and do not change `basicAuthHeader` or any URL string format.

---

### 10. serialize upload payloads per batch instead of all up front (roadmap 5+7)

context: `UploadCoordinator.runPipeline` calls `queryItemsToUpload(config)` (`services/upload/UploadCoordinator.kt:44`, defined at line 93) which serializes **every** pending item into a `JsonObject` before the first request goes out, then uploads in chunks of `config.batchSize` (line 55). On a device that has been offline for weeks, that means thousands of live `JsonObject` trees held simultaneously — the peak-memory spike lands on exactly the low-end hardware this app targets, and the first byte does not leave the device until the last item is serialized. Serializing per chunk keeps at most `batchSize` payloads alive and starts uploading immediately.

files: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` (`runPipeline`, `queryItemsToUpload`, `queueRetryableFailures`) and `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadCoordinatorTest.kt`. Do **NOT** touch `services/upload/UploadConfigs.kt`, `UploadConfig.kt`, `RoomUploadConfig.kt`, `UploadPipelineConfig.kt`, `services/upload/PhotoUploader.kt`, or `services/UploadManager.kt` (open PR #15825 owns the last one) — the config surface must not change.

steps:
1. Change `queryItemsToUpload` to take the already-chunked `List<T>` of raw items and return the `List<PreparedUpload<T>>` for just that chunk; keep the `shouldFilter` and serialization-failure `mapNotNull` behavior byte-for-byte identical.
2. In `runPipeline`, fetch `config.fetchPendingItems.invoke()` once, early-return `UploadResult.Empty` when it is empty, then `chunked(config.batchSize).forEachIndexed { … }` over the raw items and prepare each chunk at the top of the loop body.
3. Move the `queueRetryableFailures(...)` call inside the loop so it is invoked per batch with that batch's prepared uploads, instead of once at the end against the full `itemsToUpload` list (line 73); keep accumulating `allSucceeded`/`allFailed` for the final `UploadResult`.
4. Keep the existing `Log.d(TAG, "Uploading N … items")` line by logging the raw pending count before the loop, so log output is unchanged.
5. Extend `UploadCoordinatorTest.kt` with a test using a batch size of 2 and 5 items that asserts the serializer is invoked in chunks (MockK call order, or a counter that records the number of serialized items observed before the first `postUpload`), and a test that a retryable failure in batch 1 is queued even when batch 2 succeeds.

acceptance: `./gradlew testDefaultDebugUnitTest` green, and specifically `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.upload.*"`. A manual sync with pending activities and submissions still uploads all of them, the same success/failure counts appear in Logcat, and failed items still reappear in the retry queue.

size budget: ~50 changed lines, ~50 added test lines; 2 files.

out of scope: do not change `MAX_CONCURRENT_UPLOADS`, the 409-conflict recovery path, or `updateDatabaseBatch`/`reconcileDbFailures`.

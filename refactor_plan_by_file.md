# myPlanet refactor plan — grouped by file

Base commit `9ff1273`. Built from 16 generated task lists (8 agents x 2 prompts, 160 raw orders).

## How to read this

**Unit = one branch / one PR.** A unit is keyed on the file that owns it; where several findings touch that file they are bundled into one unit with an explicit step order. Every finding appears in exactly one unit.

Each finding carries these fields, so nothing that matters lives only in prose:

| field | meaning |
|---|---|
| **evidence** | what was read in the tree: coordinates and quoted code. May cite line numbers as provenance; the **anchor** is the locator. |
| **claim** | `holds` = the assertion about behaviour was checked and is true. `corrected` = the coordinates were real but the stated claim or benefit was wrong; the restated remedy follows. `scoped` = claim true, stated file set wrong. Existence of a path or symbol is **not** what this field reports. |
| **value** | evidence quality x user impact. Nothing else. Sort key. |
| **size** | XS/S/M/L by changed lines and blast radius — an independent axis, so a drain can batch the trivial ones and schedule the large ones when slots are free. |
| **risk** | what can go wrong beyond failing to compile: silent behaviour change, cache staleness, PR contention. |
| **anchor** | function name plus a grep-able snippet. Line numbers appear nowhere as anchors — they are stale the moment anything merges. |
| **depends on / supersedes / overlaps** | machine-readable edges, not 'sequence them' in a note. |

Findings recommended against are **not ranked** — they are in *Closed* at the end. Open product or design questions are in *Needs a decision*; they are not in the queue, because a doer cannot action them.

**112 verified findings -> 106 in the queue as 75 units (24 bundles + 51 standalone), 4 closed unranked, 2 awaiting a decision.**

Largest bundles: `model/News.kt` (3), `repository/ActivitiesRepositoryImpl.kt` (3), `ui/enterprises/EnterprisesReportsFragment.kt` (3), `ui/notifications/NotificationsViewModel.kt` (3), `ui/life/LifeViewModel.kt` (3), `ui/community/CommunityServicesFragment.kt` (3), `repository/CoursesRepositoryImpl.kt` (3).

---

## Needs a decision — not in the queue

### Does the My Life survey badge stay?

_from finding #56 (copilot-kimi (boundaries))_

The badge currently renders a permanently wrong 0. Either wire the real assigned-survey count through DashboardViewModel, or delete the badge. That is a product call about what the dashboard should show, not an implementation detail. Deleting the dead stub is safe either way and can ship now.

- **evidence:** myLifeListInit renders every visible item with `getLayout(dashboardItem, 0)` - surveyCount hardcoded to 0 - and then calls updateMyLifeSurveyCount(), which is an empty body with the comment "Update views with survey count if needed". So the My Life survey chip always renders 0 and the stub is dead residue.
- **claim corrected:** Order was hedged and self-referential about what exists on master. What is concretely true: myLifeListInit renders every item with getLayout(dashboardItem, 0) - surveyCount hardcoded - and then calls an empty-bodied updateMyLifeSurveyCount(). The My Life survey badge therefore always renders 0.

### Where should the resource viewer's file and PDF work live?

_from finding #106 (codex (boundaries))_

The order proposes ResourcesRepository. That puts Android service-start and PDFBox init inside a repository. The alternative is a dedicated file/PDF gateway the ViewModel depends on. Pick the target before anyone moves code; ResourcesRepository is also PR-contended.

- **evidence:** ResourceViewerViewModel injects @ApplicationContext Context, calls context.getExternalFilesDir(null), starts a download service, and initializes Android PDFBox (PDFBoxResourceLoader.init(context)) to extract text - data and file operations bypassing the repository boundary and keeping a prospective Compose screen tied to Android.
- **claim corrected:** Order presents moving service-start and PDFBox init INTO a repository as a boundary win. That is not obviously better layering than where they are, and ResourcesRepository is PR-contended. Restated as a design question.

---

## Queue

## 1. `ui/dashboard/DashboardPluginFragment.kt`

**lead value:** 92 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** openhands-kimi

**coordinate with:** unit 30 (`ui/life/LifeViewModel.kt`) via `ui/dashboard/DashboardPluginFragment.kt`

### Route My Life dashboard clicks off the stable imageId, not the localized title

`#1`  **value** 92 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** functions `handleClickMyLife`, `getLayout` — grep `when (title)`
- **files:** ui/dashboard/DashboardPluginFragment.kt (handleClickMyLife, getLayout)
- **evidence:** handleClickMyLife matches `when (title)` against hardcoded English literals ("mySubmissions", "myHealth", "Calendar", "mySurveys", "myAchievements", "myPersonals", "References"). The title it receives is DashboardItem.title, i.e. the localized string from MyLife.defaultItems (R.string.submission = "mySubmissions" in en, "misEnvios" in es, "mesSoumissions" in fr, Arabic/Nepali/Somali likewise). On any non-English locale every My Life shortcut falls into `else -> toast(feature_not_available)`. VERIFIED: literals match values/strings.xml exactly and diverge in all five values-<lang>/strings.xml.
- **work:** Add an internal pure `myLifeRouteFor(imageId: String?)` mapping the stable ids already used by imageResourceMap (ic_submissions, ic_references, ic_calendar, ic_my_survey, my_achievement, ic_mypersonals, ic_myhealth) to route descriptors; pass obj.imageId into handleClickMyLife instead of obj.title; keep per-route openIfLoggedIn gating and the feature_not_available toast for unknown ids. Unit-test every known id plus blank/unknown.
- **risk detail:** seeded rows carry the locale active at seed time; the route table must cover every id
- **note:** Highest user impact in the whole set. Note rows seeded under one locale keep that locale's title in the DB, which is exactly why title is unusable as a key.

---

## 2. `services/SharedPrefManager.kt`  —  **bundle of 2**

**lead value:** 88 &nbsp;·&nbsp; **size:** XS+M &nbsp;·&nbsp; **risk:** high &nbsp;·&nbsp; **from:** claude, openhands-glm

**steps, in order:** #4 -> #88

**coordinate with:** unit 37 (`repository/LifeRepositoryImpl.kt`) via `services/SharedPrefManager.kt`

### step 1 — Cache the resolved CouchDB base URL and close the three missing cache invalidations

`#4`  **value** 88 · **size** M · ~26-70 lines or 2-4 files · **risk** high · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** functions `setCouchdbUrl`, `setProcessedAlternativeUrl`, `setIsAlternativeUrl`
- **files:** utils/UrlUtils.kt, services/SharedPrefManager.kt (setCouchdbUrl, setProcessedAlternativeUrl, setIsAlternativeUrl)
- **evidence:** UrlUtils.getUrl() -> dbUrl(spm()) -> baseUrl(spm) does an isAlternativeUrl() read plus one or two SharedPreferences string reads, a suffix strip and two concatenations on every call, and it is on genuinely hot paths (every serialized upload document, getUserImageUrl/getCourseImageUrl per avatar row, getUrl(resource) per resource bind). The object already caches the cheaper Basic auth header behind a @Volatile generation counter. VERIFIED: setUrlUser/setUrlPwd call invalidateHeaderCache(); setCouchdbUrl/setProcessedAlternativeUrl/setIsAlternativeUrl do not.
- **work:** Add a @Volatile cachedBaseUrl cleared by the existing invalidateHeaderCache() and resetForTesting(); give baseUrl(spm) the same read/compute/compare-generation/store shape as the header getter; add invalidateHeaderCache() to the three setters. Test that a preference change plus invalidation returns the new URL and that repeated getUrl() hits preferences once.
- **risk detail:** a stale base-URL cache silently targets the wrong server; the three invalidations are load-bearing
- **note:** The invalidation gap must land in the same change or a server failover will keep hitting the old host.

### step 2 — Hoist the TypeToken out of SharedPrefManager.getSavedUsers

`#88`  **value** 49 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-glm (perf)_

- **anchor:** functions `getSavedUsers` — grep `object : TypeToken<List<User>>() {}.type`
- **files:** services/SharedPrefManager.kt (getSavedUsers)
- **evidence:** getSavedUsers constructs `object : TypeToken<List<User>>() {}.type` on every call to deserialize the saved-users JSON, and the login screen calls it on every refresh of the user list. The TypeToken and its reflective type are constant; data/room/Converters.kt already shows the cached-companion pattern for its list types. gson is already injected, so only the type allocation is waste.
- **work:** Hold the type in a private val (companion or field) and use it in the fromJson call. No change to the User model.

---

## 3. `ui/user/EditAchievementFragment.kt`  —  **bundle of 2**

**lead value:** 86 &nbsp;·&nbsp; **size:** S+M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-kimi

**steps, in order:** #2 -> #59

### step 1 — Move the achievement CV file copy off the main thread

`#2`  **value** 86 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (perf)_

- **anchor:** grep `lifecycleScope.launch { val cvFilename = computeCvFilename() ... }`
- **files:** ui/user/EditAchievementFragment.kt (computeCvFilename + its call site at
- **evidence:** The save handler runs `lifecycleScope.launch { val cvFilename = computeCvFilename() ... }` on the main dispatcher, and computeCvFilename does contentResolver.openInputStream(uri) + input.copyTo(output) - copying a user-picked PDF of arbitrary size on the UI thread. Debug builds run StrictMode detectAll, so this logs disk-read/write violations on every CV save. VERIFIED.
- **work:** Make computeCvFilename suspend and wrap the uri-check/file-copy body in withContext(dispatcherProvider.io) using the dispatcherProvider already injected by BaseContainerFragment; keep the deleteCv early return and the resumeFileName fallback on the calling dispatcher.

### step 2 — Expose the achievement document id from AchievementViewModel

`#59`  **value** 58 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** grep `user?.id + "@" + user?.planetCode`
- **files:** ui/user/AchievementViewModel.kt, ui/user/EditAchievementFragment.kt
- **evidence:** the id `"<userId>@<planetCode>"` is derived twice - AchievementViewModel.loadUserAndAchievement builds it at line 59 to load the achievement, and EditAchievementFragment's save handler rebuilds `user?.id + "@" + user?.planetCode` at line 166 to save. A persistence-format concern in the view; if the format changes, the fragment silently writes a different document than the ViewModel reads.
- **work:** Expose the id derived during loadUserAndAchievement (e.g. a StateFlow<String?> set alongside _achievement) so the derivation exists once, and consume it in the save handler. If it is unavailable because the user has not loaded, keep the save button's current guard - never save with a fabricated id.

---

## 4. `repository/NotificationsRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 85 &nbsp;·&nbsp; **size:** S+M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, copilot-grok, copilot-kimi, openhands-glm

**steps, in order:** #3 -> #36

**coordinate with:** unit 26 (`ui/notifications/NotificationsViewModel.kt`) via `repository/NotificationsRepositoryImpl.kt`

### step 1 — Stop counting team chat messages for teams that have no chat badge

`#3`  **value** 85 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (perf), copilot-kimi (boundaries), copilot-kimi (perf), copilot-grok (perf)_

- **anchor:** grep `SELECT COUNT(*)`
- **files:** repository/NotificationsRepositoryImpl.kt (getTeamNotifications,
- **evidence:** getTeamNotifications runs one `SELECT COUNT(*)` per team in `for (teamId in teamIds) { chatCountsById[teamId] = voicesRepository.countTopLevelByTeam(teamId) }`, but the count is only ever consumed as `notification != null && notification.lastCount < chatCount`. Every team without a chat row in teamNotificationDao.getByTypeAndParentIds("chat", teamIds) pays for a query whose result is discarded. Runs on the dashboard team strip: 20 teams with 2 chat-tracked teams issues 20 queries where 2 suffice. VERIFIED.
- **work:** Build notificationsById first (already done), then loop only over notificationsById.keys for the counts; keep chatCountsById typed Map<String, Long> and the `?: 0L` fallback so teams with no row still report hasChat = false. Assert with MockK coVerify(exactly = 1) that only the chat-tracked team is counted.
- **note:** Four agents proposed four different fixes to this hotspot; this one is the smallest and cannot change behaviour. The batch-DAO variants (copilot) need a GROUP BY that drops the `viewIn LIKE :teamPattern` legacy branch of countTopLevelByTeam - only worth doing after that branch is proven dead.

### step 2 — Deduplicate the two count-style notification upserts

`#36`  **value** 68 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-glm (boundaries)_

- **anchor:** functions `updateResourceNotification`, `updateStorageNotification`
- **files:** repository/NotificationsRepositoryImpl.kt (updateResourceNotification, updateStorageNotification)
- **evidence:** the two methods are near-identical read-modify-upsert routines - build a synthetic id, notificationDao.getById, detect whether the numeric value changed, mark unread and reset createdAt when it did, else construct a new AppNotification, upsert, and delete when the value is healthy. The only differences are the id suffix, type, relatedId, how the previous value is parsed (toIntOrNull() vs replace("%","").toIntOrNull()) and the message format. They have already drifted once: the resource path defaults previousCount to 0, the storage path to null.
- **work:** Extract one private suspend helper taking the id suffix, type, relatedId, a parse lambda, a format lambda, the value and a healthy flag, and route both methods through it preserving each one's current semantics exactly (including the parse and threshold differences). Keep the public signatures. Add a test for the 'value unchanged keeps it read' path if absent.
- **supersedes:** #91 (closed)

---

## 5. `services/retry/RetryQueueWorker.kt`

**lead value:** 82 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** claude

### Drain the retry queue with bounded concurrency instead of one request at a time

`#6`  **value** 82 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** functions `doWork` — grep `batch.forEach { processOperation(...) }`
- **files:** services/retry/RetryQueueWorker.kt (doWork)
- **evidence:** doWork chunks pending operations into batches of 50 and then processes each batch with `batch.forEach { processOperation(...) }` - fully sequential network round trips, so the chunking buys nothing and a backlog of 200 failed uploads takes 200 serial requests inside a 5-minute withTimeout that will often expire first. UploadCoordinator in the same codebase already solves this with async + Semaphore(MAX_CONCURRENT_UPLOADS = 6). VERIFIED.
- **work:** Add MAX_CONCURRENT_RETRIES = 6 next to BATCH_SIZE, create the Semaphore once before the chunked loop, and replace the inner forEach with coroutineScope { batch.map { async { semaphore.withPermit { processOperation(...) } } }.awaitAll() }. Replace the now-racy successCount++/failureCount++ with counts over the awaited List<Boolean>. Keep the isSyncRunning bail-out between batches.
- **risk detail:** concurrency plus the success/failure tallies - the counters must stop being racy

---

## 6. `services/upload/UploadCoordinator.kt`

**lead value:** 82 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** claude

### Serialize upload payloads per batch instead of all up front

`#7`  **value** 82 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** functions `runPipeline`, `queryItemsToUpload`, `queueRetryableFailures`
- **files:** services/upload/UploadCoordinator.kt (runPipeline, queryItemsToUpload, queueRetryableFailures)
- **evidence:** runPipeline calls queryItemsToUpload(config) which serializes every pending item into a JsonObject before the first request goes out, then uploads in chunks of config.batchSize. On a device offline for weeks that means thousands of live JsonObject trees held at once - the peak-memory spike lands on exactly the low-end hardware this app targets - and the first byte does not leave the device until the last item is serialized. VERIFIED.
- **work:** Fetch config.fetchPendingItems() once, early-return Empty, then chunk the raw items and prepare each chunk at the top of the loop body; keep the shouldFilter and serialization-failure mapNotNull behaviour byte-identical. Move queueRetryableFailures inside the loop (per batch) while still accumulating allSucceeded/allFailed for the final result, and log the raw pending count before the loop so log output is unchanged.
- **risk detail:** moving queueRetryableFailures per batch changes failure-queueing semantics
- **note:** Moving queueRetryableFailures per batch is a real semantic change to failure queueing - cover it with a test where batch 1 fails retryably and batch 2 succeeds.

---

## 7. `ui/courses/InlineResourceAdapter.kt`

**lead value:** 82 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude

### Move the inline-resource download check off the main thread

`#5`  **value** 82 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** functions `updateStatusAndPreview`
- **files:** ui/courses/InlineResourceAdapter.kt (updateStatusAndPreview)
- **evidence:** updateStatusAndPreview is called straight from onBindViewHolder and evaluates FileUtils.checkFileExist(context, UrlUtils.getUrl(resource)), which does f.exists() && f.length() > 0 - two disk stats per row, per bind, on the UI thread. Every sibling preview path in the same class already does withContext(dispatcherProvider.io) { file.exists() }. VERIFIED.
- **work:** Default the row to its not-downloaded state synchronously, then move the `resource.isResourceOffline() || checkFileExist(...)` evaluation inside the existing holder.setPreviewJob(adapterScope.launch { ... }) with the stat wrapped in withContext(io), short-circuiting on isResourceOffline() first. Keep cancelPreviousPreviews() where it is so recycled rows cancel stale work.

---

## 8. `services/DownloadService.kt`  —  **bundle of 2**

**lead value:** 80 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** high &nbsp;·&nbsp; **from:** claude, codex, copilot-grok, openhands-kimi

**steps, in order:** #11 -> #83

### step 1 — Stop re-reading SharedPreferences and rebuilding the notification builder on every download tick

`#11`  **value** 80 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `holds` · _proposed by claude (perf), openhands-kimi (perf), codex (perf)_

- **anchor:** functions `getRemainingCount`, `sendNotification`, `updateNotificationForBatchDownload`, `processDownloadQueue`
- **files:** services/DownloadService.kt (getRemainingCount, sendNotification, updateNotificationForBatchDownload, processDownloadQueue, downloadFile)
- **evidence:** sendNotification fires every 500 ms (NOTIFICATION_UPDATE_INTERVAL_MS) while a file downloads and calls getRemainingCount(), which does two preferences.getStringSet reads plus a union-set allocation, then filters against processedUrls - purely to render the "N remaining" subtext. The queue only changes between files (processedUrls.add / cleanupProcessedUrls). Each tick also re-allocates NotificationManagerCompat.from(this). updateNotificationForBatchDownload re-runs DownloadUtils.createChannels(this) and constructs a fresh NotificationCompat.Builder once per file. Separately, downloadFile wraps the body in BufferedInputStream(body.byteStream(), 1024 * 8) while reading into a 1024 * 16 BUFFER_SIZE array, so reads bypass the wrapper. ALL VERIFIED.
- **work:** Cache the remaining count in a @Volatile field refreshed at the two points where the queue actually changes; read the cache in sendNotification and updateNotificationForBatchDownload (leave onDownloadComplete calling getRemainingCount(priorityUrls) directly - it needs the post-download value); create the channel and builder once per queue session and mutate the existing builder per file; hoist the areNotificationsEnabled() check out of the tick; raise the BufferedInputStream buffer to BUFFER_SIZE. Keep every notification ID, channel ID and subtext format byte-identical.
- **risk detail:** notification IDs, channel and subtext format must stay byte-identical
- **note:** Three agents found overlapping slices of this; the union above is the whole finding. Do not also take the 'parallel downloads' variant in the same change - that is a separate, much riskier task.

### step 2 — Introduce bounded parallelism for non-priority downloads in DownloadService

`#83`  **value** 60 · **size** M · ~26-70 lines or 2-4 files · **risk** high · **claim** `holds` · _proposed by copilot-grok (perf)_

- **anchor:** functions `processDownloadQueue` — grep `while (true)`
- **files:** services/DownloadService.kt (processDownloadQueue)
- **evidence:** processDownloadQueue is a `while (true)` loop that takes getNextPriorityUrl() ?: getNextPendingUrl() and awaits initDownload one URL at a time, so multi-file course/library downloads under-utilize bandwidth and stretch foreground-service time.
- **work:** Add a small concurrency limit (2-3) for non-priority downloads via Semaphore + async, keeping priority URLs served first (and serial, to preserve UX ordering). Guard the shared session counters and throttle notification updates so parallel completions do not spam. Guarantee the queue still drains to empty -> completion notification -> stopSelf, with no double-download of the same URL.
- **depends on:** #11 — builds on the settled notification counters; the order's documented fallback IS finding #11
- **risk detail:** changes the service's core control flow and the shared session counters
- **note:** Highest-risk task in the perf set: it changes the service's core control flow and interacts with the notification/session counters that the per-tick caching task also touches. The submitted order offers a documented fallback (larger buffer + notification throttle only) - but that fallback is already covered by the per-tick task, so if you do not take the parallelism, close this instead of doing the fallback twice.

---

## 9. `services/upload/PhotoUploader.kt`

**lead value:** 80 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok

**coordinate with:** unit 34 (`repository/SubmissionsRepositoryImpl.kt`) via `services/upload/PhotoUploader.kt`

### Parallelize the PhotoUploader batch POSTs

`#8`  **value** 80 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-grok (perf)_

- **anchor:** functions `uploadSubmitPhotos` — grep `batch.forEach { ... uploadRepository.postUpload(...) }`
- **files:** services/upload/PhotoUploader.kt (uploadSubmitPhotos)
- **evidence:** uploadSubmitPhotos chunks by BATCH_SIZE and then runs `batch.forEach { ... uploadRepository.postUpload(...) }` sequentially; the per-photo uploadAttachment calls after the batch mark are sequential too. Wall-clock upload time scales linearly with photo count on slow links. VERIFIED.
- **work:** Within each batch run the POSTs with bounded concurrency using UploadCoordinator's proven Semaphore + async pattern (cap <= 6), collecting successes into a thread-safe list, then keep the single markPhotosUploadedBatch call. Preserve failure isolation - one failure must not cancel the batch - and the listener callbacks.

---

## 10. `utils/NetworkUtils.kt`

**lead value:** 78 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude

### Cache the invariant device identity and system services in NetworkUtils

`#9`  **value** 78 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** functions `getDeviceName`, `getUniqueIdentifier`, `isWifiEnabled`, `isBluetoothEnabled`
- **files:** utils/NetworkUtils.kt (getDeviceName, getUniqueIdentifier, isWifiEnabled, isBluetoothEnabled)
- **evidence:** getDeviceName() re-reads Build.MANUFACTURER/Build.MODEL, concatenates and uppercases on every call, and it is called once per serialized document during uploads (Personal, NewsLog, ApkLog, Rating, CourseActivity, MyLibrary, SearchActivity, MyPlanet, ActivitiesRepositoryImpl, UploadManager), so a bulk activity upload allocates thousands of throwaway strings. getUniqueIdentifier() rebuilds the same androidId + "_" + buildId each call; isWifiEnabled()/isBluetoothEnabled() call getSystemService every invocation. The file already has a ResettableCache helper and a connectivityManager cache showing the intended pattern. VERIFIED (10 call sites).
- **work:** Add ResettableCache entries for deviceName, uniqueIdentifier, wifiManager and bluetoothManager; keep isWifiEnabled/isBluetoothEnabled reading the live enabled state off the cached manager so it stays dynamic; register all four in resettableCaches so resetForTesting() still clears them.

---

## 11. `utils/Utilities.kt`

**lead value:** 78 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude

### Add an ASCII fast path to Utilities.normalizeText

`#10`  **value** 78 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** functions `normalizeText`
- **files:** utils/Utilities.kt (normalizeText)
- **evidence:** normalizeText runs Normalizer.normalize(str.lowercase(locale), NFD) followed by .replace(DIACRITICS_REGEX, ""), which allocates a fresh Matcher on every call. It is the per-item primitive of offline search and course filtering - every candidate title and every query token goes through it on every keystroke (call sites in MyLibrary, SurveysViewModel, CoursesRepositoryImpl x4, ResourcesRepositoryImpl) - yet for pure-ASCII titles NFD decomposition and the combining-marks strip are guaranteed no-ops. VERIFIED.
- **work:** Lowercase once, return immediately when every char is < 0x80, otherwise fall through to the existing path. Test the fast path (mixed case, empty, digits/punctuation) and the slow path (Cafe, Nino, aeiou, Arabic and Nepali round-trips).

---

## 12. `data/room/dao/TeamDao.kt`

**lead value:** 76 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, copilot-grok, devin, openhands-glm

### Push the archived-report filter and sort of the finances CSV export into SQL

`#12`  **value** 76 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries), openhands-glm (boundaries), devin (boundaries), copilot-grok (boundaries), devin (perf)_

- **anchor:** grep `IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`
- **files:** data/room/dao/TeamDao.kt (one new @Query), repository/EnterprisesRepositoryImpl.kt (exportReportsAsCsv,
- **evidence:** exportReportsAsCsv reads every `teams` row for the team with docType='report' via getByTeamIdAndDocType, then drops archived rows and sorts by createdDate in Kotlin. The identical predicate already exists in SQL one line above in the same DAO: observeNonArchivedReportsByTeamId uses `IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`. VERIFIED both.
- **work:** Add `@Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC") suspend fun getNonArchivedReportsByTeamId(teamId: String): List<MyTeam>` and call it. Adding a @Query changes no table, so no AppDatabase version bump.
- **note:** The IFNULL matters: the current Kotlin `it.status != "archived"` keeps null-status rows, so a bare `status != 'archived'` would silently drop them. Two agents caught that; one proposed a plain sequence conversion of the same lines instead, which buys nothing - prefer the SQL.

---

## 13. `model/News.kt`  —  **bundle of 3**

**lead value:** 73 &nbsp;·&nbsp; **size:** XS+M &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** copilot-grok, devin, openhands-kimi

**steps, in order:** #24 -> #70 -> #16

### step 1 — Delete the dead, re-parsing News.messageWithoutMarkdown getter

`#24`  **value** 68 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (perf)_

- **anchor:** grep `val messageWithoutMarkdown`
- **files:** model/News.kt (the messageWithoutMarkdown getter)
- **evidence:** messageWithoutMarkdown iterates imagesArray - itself a fresh Gson parse per access - and string-replaces every image's markdown out of the message. VERIFIED: `grep -rn messageWithoutMarkdown app/src/` returns exactly one hit, the declaration itself. Dead code carrying a hidden per-call parse cost for any future caller.
- **work:** Confirm again with grep, delete the getter and any import it made unnecessary.

### step 2 — Single map read and System.currentTimeMillis in News.updateMessage/createNews

`#70`  **value** 55 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (perf)_

- **anchor:** functions `updateMessage`, `createNews` — grep `if (map.containsKey("news")) { val newsObj = map["news"] ... }`
- **files:** model/News.kt (updateMessage, createNews)
- **evidence:** createNews does `if (map.containsKey("news")) { val newsObj = map["news"] ... }` - two lookups in the deserialized map - and both updateMessage (line 101) and createNews (line 167) use Date().time, allocating a java.util.Date purely to read the current timestamp. Both run for every news or voice item parsed or updated.
- **work:** Replace the containsKey/get pair with `map["news"]?.let { ... }`, swap both Date().time reads for System.currentTimeMillis(), and drop the now-unused java.util.Date import. No serialization-format or Room-schema change.

### step 3 — Honour the existing News parse caches in imagesArray and isCommunityNews

`#16`  **value** 73 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `holds` · _proposed by copilot-grok (perf)_

- **anchor:** functions `imagesArray`, `isCommunityNews`, `messageWithoutMarkdown`
- **files:** model/News.kt (imagesArray, isCommunityNews, messageWithoutMarkdown)
- **evidence:** The @Ignore fields parsedImagesArray/rawImages and parsedViewIn/rawViewIn exist and are maintained by VoicesAdapter (lines 610-614, 638-640) and read by VoicesRepositoryImpl and JsonUtils, and calculateSortDate already prefers parsedViewIn. But the imagesArray getter always runs JsonUtils.gson.fromJson(images, ...) and isCommunityNews always re-parses viewIn, ignoring both caches. VERIFIED.
- **work:** Lazy-fill parsedImagesArray on first access and reuse it while rawImages == images; do the same for isCommunityNews against parsedViewIn/rawViewIn. Stay null/empty/JsonSyntaxException safe (empty array / false, no crash) and change no Room field or @Entity shape.
- **depends on:** #24 — deleting the dead getter first removes a consumer the cache work would otherwise have to cover
- **risk detail:** hand-rolled mutable parse cache; invalidation keys off rawImages/rawViewIn
- **note:** Conflicts with the 'delete messageWithoutMarkdown' task below - land that one first, then this one has one less consumer to cache for.

---

## 14. `repository/ActivitiesRepositoryImpl.kt`  —  **bundle of 3**

**lead value:** 73 &nbsp;·&nbsp; **size:** S+M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, copilot-grok, copilot-kimi

**steps, in order:** #13 -> #95 -> #54

### step 1 — Aggregate the most-opened resource in SQL instead of loading the whole activity table

`#13`  **value** 73 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-grok (perf), copilot-grok (boundaries)_

- **anchor:** functions `getMostOpenedResource`
- **files:** data/room/dao/ResourceActivityDao.kt, repository/ActivitiesRepositoryImpl.kt (getMostOpenedResource)
- **evidence:** getMostOpenedResource calls resourceActivityDao.getByUserAndType(userName, type) - a plain `SELECT *` - then groupBy/mapValues/maxByOrNull in memory on the default dispatcher. Large offline histories make the profile screen O(n) in rows for a single winner. The DAO has a countByUserAndType but no aggregate. VERIFIED.
- **work:** Add a @Query grouping by resourceId with COUNT(*), ordered by count desc and a documented stable tie-break, LIMIT 1, skipping null titles; map to the existing Pair<String, Int>? contract with empty -> null and delete the in-memory grouping. No schema version bump.

### step 2 — Dispatch MyPlanet.getTabletUsages on IO and make its window testable

`#95`  **value** 46 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (perf)_

- **anchor:** functions `getTabletUsages`, `uploadMyPlanetActivities`
- **files:** model/MyPlanet.kt (getTabletUsages), repository/ActivitiesRepositoryImpl.kt (uploadMyPlanetActivities)
- **evidence:** getTabletUsages runs a UsageStatsManager.queryUsageStats over the window from the last usage upload to System.currentTimeMillis() and loops addStats; ActivitiesRepositoryImpl calls it inline inside uploadMyPlanetActivities.
- **work:** Wrap the call in withContext(dispatcherProvider.io) and give getTabletUsages a `now: Long = System.currentTimeMillis()` defaulted parameter so tests can pin the window. No change to the uploaded payload.
- **note:** The main-thread risk is theoretical - the only caller is the upload path - so the testable window is the real gain.

### step 3 — Collapse the profile activity-stats fan-out and move KEY_RESOURCE_OPEN behind the repository

`#54`  **value** 60 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by codex (boundaries), copilot-grok (boundaries)_

- **anchor:** grep `UserSessionManager.KEY_RESOURCE_OPEN`
- **files:** repository/ActivitiesRepository.kt, repository/ActivitiesRepositoryImpl.kt, ui/user/UserProfileViewModel.kt
- **evidence:** UserProfileViewModel imports UserSessionManager solely for the KEY_RESOURCE_OPEN constant and passes it into activitiesRepository.getMostOpenedResource and getResourceOpenCount (lines 131, 133), alongside getGlobalLastVisit - three async calls the ViewModel assembles itself. A ViewModel should not know a session-service storage token.
- **work:** Add repository-level convenience methods that need only the user name, with the resource-open token owned inside the repository contract, and (optionally) one ProfileActivityStats aggregate combining most-opened, global last visit and open count. Switch the ViewModel to them and drop the UserSessionManager import, preserving the displayed string formatting and leaving getOfflineVisits() alone.
- **depends on:** #13 — the stats aggregate consumes the SQL most-opened query, so that must exist first

---

## 15. `services/sync/SyncManager.kt`  —  **bundle of 2**

**lead value:** 73 &nbsp;·&nbsp; **size:** M+L &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** claude

**steps, in order:** #15 -> #21

### step 1 — Guard SyncManager's SyncPerf logging behind Log.isLoggable

`#15`  **value** 73 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by claude (perf)_

- **anchor:** grep `if (Log.isLoggable("SyncPerf", Log.DEBUG))`
- **files:** services/sync/SyncManager.kt (the Log.d("SyncPerf", ...) statements only)
- **evidence:** exactly 20 unconditional Log.d("SyncPerf", ...) calls with string interpolation and zero isLoggable guards (lines 139-141, 207-217, 274, 305, 382, 422, 427, 505, 516, 558, 563). Line 382 fires once per resource batch and interpolates a percentage computation; 139/208 format an Instant on every sync. SyncTimeLogger in the same subsystem already wraps all six of its Log.d calls in `if (Log.isLoggable("SyncPerf", Log.DEBUG))`, so SyncManager is the odd one out and a release device pays for the formatting.
- **work:** Add `private inline fun syncPerf(message: () -> String)` guarded by isLoggable and route all 20 sites through it, keeping message text byte-identical. Wrap the three multi-line banner blocks in a single guard so the Instant formatting is skipped too. Leave every Log.e/Log.w and the syncTimeLogger.logDetail calls alone.

### step 2 — Move the shelves-with-data preference cache out of SyncManager into SyncRepository

`#21`  **value** 72 · **size** L · >70 lines or a new class · **risk** med · **claim** `holds` · _proposed by claude (boundaries)_

- **anchor:** functions `getCachedShelvesWithData`, `cacheShelvesWithData`, `getShelvesWithDataBatchOptimized`
- **files:** repository/SyncRepository.kt, repository/SyncRepositoryImpl.kt, services/sync/SyncManager.kt (getCachedShelvesWithData, cacheShelvesWithData, getShelvesWithDataBatchOptimized)
- **evidence:** SyncManager owns a hand-rolled 6-hour SharedPreferences cache of which shelves hold data: the two private functions each re-declare the string keys "shelves_with_data" and "shelves_cache_time", do their own TTL arithmetic (cacheValidityHours * 60 * 60 * 1000) and split/join a comma-separated list. VERIFIED. That is persistence logic - key names, encoding, expiry - living in the sync orchestrator, agreeing by convention, untestable without driving a whole sync.
- **work:** Add getCachedShelvesWithData()/cacheShelvesWithData(shelves) to SyncRepository, implement in SyncRepositoryImpl with SharedPrefManager and TimeProvider constructor parameters (both already Hilt-provided, so no module change), moving the keys and TTL into private companion constants. Keep the exact same pref keys, comma encoding and 6-hour window so existing installs keep their cache. Delete the two private functions and point SyncManager at the repository.
- **overlaps:** #15 — both edit SyncManager
- **risk detail:** pref keys and the 6-hour TTL must be preserved or existing installs lose their cache

---

## 16. `ui/teams/courses/TeamCoursesViewModel.kt`  —  **bundle of 2**

**lead value:** 73 &nbsp;·&nbsp; **size:** XS+M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin, openhands-kimi

**steps, in order:** #14 -> #66

### step 1 — Run the independent team-tab ViewModel queries concurrently

`#14`  **value** 73 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries), openhands-kimi (perf), openhands-kimi (perf)_

- **anchor:** functions `fetchMembers`, `loadCourses`, `loadResources`
- **files:** ui/teams/members/RequestsViewModel.kt (fetchMembers), ui/teams/courses/TeamCoursesViewModel.kt (loadCourses), ui/teams/resources/TeamResourcesViewModel.kt (loadResources)
- **evidence:** Three team-tab ViewModels await independent suspend repository calls strictly sequentially. RequestsViewModel.fetchMembers awaits getRequestedMembers, getJoinedMemberCount and getUserModel in sequence though none depends on another (only isTeamLeader needs the user). TeamCoursesViewModel.loadCourses awaits getTeamCourseIds -> getCoursesByIds -> getTeamCreator, where the creator lookup is independent of the ids. TeamResourcesViewModel.loadResources awaits getTeamResources then isTeamLeader, which are independent. ALL VERIFIED.
- **work:** Wrap each load body in coroutineScope { } with async per independent call, keeping every real dependency edge (getCoursesByIds still awaits the ids; isTeamLeader still awaits the user). Preserve exactly one MutableStateFlow assignment per load with identical content - no intermediate emissions.
- **note:** A fourth ViewModel was proposed for the same treatment (CommunityTabViewModel) and dropped: three of its four reads are non-suspend SharedPreferences getters.

### step 2 — Resolve the current user id inside TeamCoursesViewModel

`#66`  **value** 56 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (boundaries)_

- **anchor:** functions `updateCoursesList`, `loadCourses` — grep `sharedPrefManager.getUserId().ifEmpty { "--" }`
- **files:** ui/teams/courses/TeamCoursesFragment.kt (updateCoursesList), ui/teams/courses/TeamCoursesViewModel.kt (loadCourses)
- **evidence:** TeamCoursesFragment.updateCoursesList reads `sharedPrefManager.getUserId().ifEmpty { "--" }` and passes the id into viewModel.loadCourses - a platform SharedPrefManager lookup in the view for a value UserRepository.getCurrentUserId() already provides suspendably.
- **work:** Inject UserRepository into the ViewModel, change loadCourses(teamId, currentUserId) to loadCourses(teamId) computing the id internally with the same "--" fallback, and drop the sharedPrefManager use from the fragment.
- **overlaps:** #14 — both rewrite TeamCoursesViewModel.loadCourses
- **note:** Overlaps the team-tab parallelization task on the same loadCourses function - land one, rebase the other.

---

## 17. `data/room/dao/PersonalDao.kt`  —  **bundle of 2**

**lead value:** 72 &nbsp;·&nbsp; **size:** XS+S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, copilot-grok, openhands-glm

**steps, in order:** #17 -> #29

**coordinate with:** unit 46 (`repository/PersonalsRepositoryImpl.kt`) via `repository/PersonalsRepositoryImpl.kt`

### step 1 — Replace the personals read-modify-write update with one targeted SQL UPDATE

`#17`  **value** 72 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries), openhands-glm (boundaries)_

- **anchor:** functions `updatePersonalResource` — grep `findByDocId(id) ?: findById(id)`
- **files:** data/room/dao/PersonalDao.kt (one new @Query), repository/PersonalsRepositoryImpl.kt (updatePersonalResource)
- **evidence:** updatePersonalResource issues up to two SELECTs (`findByDocId(id) ?: findById(id)`), mutates the loaded Personal in memory, then writes the whole row back with @Update. Editing a title therefore rewrites every column - including path, _rev and isUploaded - from a snapshot read microseconds earlier: three statements where one suffices, plus a lost-update window against the sync writer that touches isUploaded/_rev via updateUploadedStatus. VERIFIED.
- **work:** Add `@Query("UPDATE my_personal SET title = COALESCE(:title, title), description = COALESCE(:description, description) WHERE _id = :id OR id = :id")` - the WHERE mirrors the existing deleteByIdOrDocId so both id shapes resolve, and COALESCE preserves PersonalUpdate's "null means leave unchanged" contract. Reduce the repository method to that single call. Leave findByDocId/findById/update in place for savePersonalResource and the upload path.
- **note:** A weaker variant of the same finding only merged the two SELECTs into one; the single UPDATE removes all three statements and the race.

### step 2 — Order personal resources in SQL

`#29`  **value** 68 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by copilot-grok (boundaries)_

- **anchor:** functions `getByUserIdFlow` — grep `@Query("SELECT * FROM my_personal WHERE userId = :userId") fun getByUserIdFlow(userId: String): Flow<List<Personal>>`
- **files:** data/room/dao/PersonalDao.kt (getByUserIdFlow)
- **evidence:** `@Query("SELECT * FROM my_personal WHERE userId = :userId") fun getByUserIdFlow(userId: String): Flow<List<Personal>>` has no ORDER BY, and PersonalsRepositoryImpl.getPersonalResources exposes that Flow directly, so the My Personals list order is whatever SQLite returns and can change across versions or after a rewrite of the table.
- **work:** Add an explicit ORDER BY over existing columns (e.g. date DESC, title COLLATE NOCASE ASC) and assert through the repository Flow that newer entries sort first. No schema version bump - adding ORDER BY to a @Query changes no table.

---

## 18. `ui/courses/CourseStepFragment.kt`

**lead value:** 72 &nbsp;·&nbsp; **size:** L &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** copilot-grok

### Hoist CourseStepFragment's multi-repo orchestration into a CourseStepViewModel

`#60`  **value** 72 · **size** L · >70 lines or a new class · **risk** med · **claim** `holds` · _proposed by copilot-grok (boundaries)_

- **anchor:** functions `loadStepData` — grep `lateinit var progressRepository`
- **files:** ui/courses/CourseStepFragment.kt, ui/courses/CourseStepViewModel.kt (new)
- **evidence:** the fragment field-injects ConfigurationsRepository and ProgressRepository and, via BaseContainerFragment, uses coursesRepository, userRepository and resourcesRepository inside lifecycleScope - getCourseStepData (103), getUserModel (109), getCourseTitleById (111), saveCourseProgress (90), checkServerAvailability (205), downloadResourcesPriority (208), getAllStepResources (221, 234). Five-repository orchestration living in a Fragment.
- **work:** Create a @HiltViewModel CourseStepViewModel injecting those repositories (plus the download coordinator if the download start moves too) and exposing one StateFlow UI state (step, resources, exams, survey, title, userHasCourse, download flags). Leave view binding, markdown/AI selection, navigation and adapter wiring in the fragment, collecting state instead of launching repository calls. Do not edit any *RepositoryImpl or BaseContainerFragment. Unit-test load, save and empty-step paths.
- **risk detail:** new ViewModel over five repositories; easy to change load ordering by accident
- **note:** The largest task in the set - budget accordingly and keep it to one screen.

---

## 19. `ui/enterprises/EnterprisesReportsFragment.kt`  —  **bundle of 3**

**lead value:** 71 &nbsp;·&nbsp; **size:** XS+S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, copilot-grok, devin, openhands-kimi

**steps, in order:** #19 -> #45 -> #64

**coordinate with:** unit 25 (`ui/health/MyHealthFragment.kt`) via `ui/health/MyHealthFragment.kt`

### step 1 — Drop the false suspend on getReportsFlow and stop nesting the lifecycle-aware collector

`#19`  **value** 71 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries)_

- **anchor:** functions `getReportsFlow` — grep `viewLifecycleOwner.lifecycleScope.launch { val flow = viewModel.getReportsFlow(teamId); collectLatestWhenStarted(flow) { ... } }`
- **files:** ui/enterprises/EnterprisesViewModel.kt (getReportsFlow), ui/enterprises/EnterprisesReportsFragment.kt and the export block)
- **evidence:** EnterprisesViewModel.getReportsFlow is declared suspend even though it only returns the cold Flow from EnterprisesRepository.getReportsFlow, which is correctly non-suspend. VERIFIED. The false suspend forces the fragment into `viewLifecycleOwner.lifecycleScope.launch { val flow = viewModel.getReportsFlow(teamId); collectLatestWhenStarted(flow) { ... } }`, so a lifecycle-aware collector is nested inside a plain launch that outlives STARTED - a leaked second collector after rotation.
- **work:** Remove suspend, keeping the Flow<List<MyTeam>> return type, and collapse the fragment to a single collectLatestWhenStarted(viewModel.getReportsFlow(teamId)) { ... }. Leave exportReportsAsCsv genuinely suspend on both sides.

### step 2 — Replace TextUtils with Kotlin stdlib checks in five UI files

`#45`  **value** 63 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** grep `TextUtils.isEmpty`
- **files:** ui/health/HealthUsersAdapter.kt, ui/user/UserArrayAdapter.kt, ui/surveys/SendSurveyFragment.kt, ui/health/MyHealthFragment.kt, ui/enterprises/EnterprisesReportsFragment.kt
- **evidence:** every site, including exactly six TextUtils.isEmpty checks in the EnterprisesReportsFragment validation `when` - five UI files each importing android.text.TextUtils for emptiness checks the Kotlin idiom does null-safely and allocation-free.
- **work:** Swap to isNullOrEmpty() (preserving the negated forms and the existing string-template arguments), delete the five imports, and confirm grep for android.text.TextUtils over the five files returns nothing.

### step 3 — Stop EnterprisesReportsFragment reaching into TeamsRepository for the team name

`#64`  **value** 57 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (boundaries), copilot-grok (boundaries)_

- **anchor:** functions `exportCSV` — grep `teamsRepository.getTeamNameFromPrefs()`
- **files:** ui/enterprises/EnterprisesReportsFragment.kt, 84, 114)
- **evidence:** three calls to teamsRepository.getTeamNameFromPrefs() - for the CSV filename, the CSV content and one more site - a cross-feature leak from the enterprises screen into Teams for a value the screen already knows. BaseTeamFragment.getEffectiveTeamName() (line 90) resolves it from the fragment's arguments or the loaded team.
- **work:** Replace the three prefs-backed lookups with one read of the effective team name (the base-class helper is the smallest change and drops the dependency entirely; routing through the ViewModel keeps the prefs coupling). Remove the teamsRepository reference from this file if the name was its only use, and keep the filename sanitising in the UI.
- **decision made here:** Two variants disagreed on the source of truth: BaseTeamFragment.getEffectiveTeamName() (arguments/team) vs SharedPrefManager. RESOLVED: use getEffectiveTeamName() - it is the screen's own team, drops the dependency entirely, and needs no new injection. Cover it with the export test. One order also undercounted the sites as two; there are three.
- **overlaps:** #19 — both edit EnterprisesReportsFragment
- **note:** Note the two submitted variants disagree on the source of truth: arguments/team vs prefs. They can differ - decide deliberately and cover it with the export test. One order also undercounted the call sites as two.

---

## 20. `repository/DictionaryRepository.kt`

**lead value:** 71 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, copilot-grok

### Stop leaking the Room DictionaryEntity into the dictionary UI

`#18`  **value** 71 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries), copilot-grok (boundaries)_

- **anchor:** functions `findByWord` — grep `grep -rn 'data.room.entity' app/src/main/java \| grep -v /data/room/ \| grep -v /repository/`
- **files:** repository/DictionaryRepository.kt, repository/DictionaryRepositoryImpl.kt (findByWord), ui/dictionary/DictionaryViewModel.kt
- **evidence:** DictionaryRepository.findByWord returns the Room @Entity itself and DictionaryViewModel imports it from the persistence package, storing it in UI state as DictionarySearchState.Found(entry: DictionaryEntity). VERIFIED: `grep -rn 'data.room.entity' app/src/main/java | grep -v /data/room/ | grep -v /repository/` returns exactly one hit - this ViewModel - so closing it makes that boundary absolute.
- **work:** Declare a plain `data class DictionaryWord(word, meaning, definition, synonym, antonym)` next to the existing DictionaryLoad sealed interface, change the signature, map inside findByWord, and hold DictionaryWord in Found. DictionaryActivity.renderSearchState reads only word/definition/synonym/antonym and compiles untouched.

---

## 21. `services/UploadManager.kt`  —  **bundle of 2**

**lead value:** 70 &nbsp;·&nbsp; **size:** XS+L &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok, copilot-kimi

**steps, in order:** #25 -> #84

### step 1 — Extract the team bulk-upload pipeline out of UploadManager into a TeamsUploadRunner

`#25`  **value** 70 · **size** L · >70 lines or a new class · **risk** low · **claim** `holds` · _proposed by copilot-kimi (boundaries)_

- **anchor:** functions `uploadTeams`, `queueTeamRetry`, `uploadTeamImageAttachment`
- **files:** services/upload/TeamsUploadRunner.kt (new), services/UploadManager.kt (uploadTeams, queueTeamRetry, uploadTeamImageAttachment)
- **evidence:** UploadManager is 582 lines; uploadTeams (255), queueTeamRetry (328) and uploadTeamImageAttachment (353) form a self-contained pipeline - fetch via teamsSyncRepository.getTeamsForUpload(), _bulk_docs POST via uploadRepository.postUploadArray, per-row error -> retryQueue.queueFailedOperation, then deleteLocalTeamRecords/markTeamsUploaded. uploadAchievement (148) already shows the one-line-delegate pattern, and services/upload/ already holds PhotoUploader and AchievementUploader.
- **work:** Create TeamsUploadRunner in services/upload/ injecting Lazy<TeamsSyncRepository>, UploadRepository, RetryQueue, DispatcherProvider and @ApplicationContext Context (uploadTeamImageAttachment needs it for MyTeam.getAttachmentFile). Move the three functions verbatim, keeping the processInBatches chunking and TAG logging. Reduce UploadManager.uploadTeams to a delegate and drop the imports the move frees.
- **note:** Behaviour-preserving move of already-covered paths - the value is UploadManager shrinking, so resist adding logic changes.

### step 2 — Drop the pointless openConnection MIME probe in UploadManager.uploadNews (hygiene, not a bug)

`#84`  **value** 44 · **size** XS · ~1-5 lines · **risk** low · **claim** `corrected` · _proposed by copilot-grok (perf)_

> **claim corrected.** Order asserted the resolved MIME is ignored and uploads always send application/octet-stream. FALSE: only the request *body* media type is octet-stream; mimeType is passed to getHeaderMap(mimeType, resourceRev) and does reach the request. What is true: the probe is pointless I/O on a local file. Value is hygiene, not a bug fix.

- **anchor:** grep `val mimeType = imageFile.toURI().toURL().openConnection().contentType`
- **files:** services/UploadManager.kt (uploadNews, the news-image loop)
- **evidence:** The same anti-pattern as the UploadRepositoryImpl task: `val mimeType = imageFile.toURI().toURL().openConnection().contentType` for a local file, once per news image. FileUtils.getMimeType / Utilities.getMimeType already exist for extension-based lookup.
- **work:** Drop the probe and derive the MIME from the filename, falling back to application/octet-stream, passing it into getHeaderMap as today. Do not change the createImage contract or the news JSON shape.
- **note:** PREMISE PARTLY WRONG in the submitted order, which claims the MIME is 'ignored' and the upload always uses application/octet-stream. VERIFIED: only the request *body* media type is octet-stream; mimeType is passed to getHeaderMap(mimeType, resourceRev) and does reach the request. The probe is still pointless I/O, so the change stands - but it is not the correctness fix it was sold as.

---

## 22. `ui/chat/ChatHistoryAdapter.kt`  —  **bundle of 2**

**lead value:** 70 &nbsp;·&nbsp; **size:** XS+L &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin, openhands-kimi

**steps, in order:** #30 -> #102

### step 1 — Extract the chat-share payload builder out of ChatHistoryAdapter

`#30`  **value** 70 · **size** L · >70 lines or a new class · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** functions `showEditTextAndShareButton`, `serializeConversation`
- **files:** ui/chat/ChatHistoryAdapter.kt (showEditTextAndShareButton, serializeConversation), model/ChatSharePayload.kt (new)
- **evidence:** showEditTextAndShareButton builds a CouchDB-shaped HashMap<String?, String> payload inside a RecyclerView adapter - _id, _rev, title, user, aiProvider, createdDate, updatedDate, conversations serialized with JsonUtils.gson, wrapped in an outer message/viewInId/viewInSection/messageType/messagePlanetCode/chat/news map. VERIFIED. Document serialization in a view class is untestable where it sits.
- **work:** Add a pure `buildShareMap(chat, note, team, section, nowMillis)` producing byte-for-byte the same keys and values, with serializeConversation as a private helper; call it from the adapter passing Date().time from the call site. Test null team (empty viewInId/messageType/messagePlanetCode), populated team, null _id/_rev -> empty strings, a conversation round-trip, and assert the exact outer key set so the schema cannot drift.

### step 2 — One-pass expandedGroups set in ChatHistoryAdapter

`#102`  **value** 43 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (perf)_

- **anchor:** grep `currentFlatList.filter { it.isGroup && it.isExpanded }.map { it.title }.toMutableSet()`
- **files:** ui/chat/ChatHistoryAdapter.kt (the share-target click handler,
- **evidence:** `currentFlatList.filter { it.isGroup && it.isExpanded }.map { it.title }.toMutableSet()` walks the share-target list twice and creates a throwaway filtered list plus a mapped list on every expand/collapse tap.
- **work:** Build a LinkedHashSet in one loop over currentFlatList. Preserve the surrounding remove/add of the clicked title and leave generateFlatList and the isExpanded lookup above it alone.

---

## 23. `services/UserSessionManager.kt`

**lead value:** 70 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-kimi

### Remove the dead eager fullName read from UserSessionManager's constructor

`#20`  **value** 70 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** functions `init` — grep `private val fullName: String`
- **files:** services/UserSessionManager.kt
- **evidence:** UserSessionManager declares `private val fullName: String` and populates it in an init block from sharedPrefManager.getUserName(), wrapped in a catch that only rethrows. VERIFIED: the only two references to fullName in the file are its declaration and that assignment - nothing reads it - so every injection of this singleton performs a SharedPreferences read whose result is discarded, and makes the class fragile to construct before login.
- **work:** Delete the property and the whole init block; drop any test stub that existed only to satisfy the removed read, and keep a construction test green against an unstubbed SharedPrefManager mock to prove construction no longer touches preferences.

---

## 24. `ui/community/CommunityTabViewModel.kt`

**lead value:** 69 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, codex, openhands-glm

### Have the free ViewModels read the current user from UserRepository, not UserSessionManager

`#22`  **value** 69 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries), codex (boundaries), codex (boundaries), codex (boundaries), openhands-glm (boundaries)_

- **anchor:** grep `return userRepository.getUserModel()`
- **files:** ui/community/CommunityTabViewModel.kt, ui/courses/TakeCourseViewModel.kt, ui/surveys/SurveysViewModel.kt
- **evidence:** Three ViewModels inject the services singleton UserSessionManager solely to call getUserModel(), which VERIFIED is a one-line delegation `return userRepository.getUserModel()`. UserSessionManager additionally owns login/logout side effects, prefs writes and resource-open counters, so a ViewModel that only wants the current user drags that whole surface in as a test dependency. SurveysViewModel is the worst case: it already injects UserRepository (line 32) and UserSessionManager (line 33), calling the latter at lines 93 and 188.
- **work:** Swap the constructor dependency for UserRepository in CommunityTabViewModel and TakeCourseViewModel; in SurveysViewModel just delete the UserSessionManager parameter and use the UserRepository it already has. Update the ViewModel tests to mock UserRepository.getUserModel(). Do not shrink UserSessionManager or migrate its legitimate fragment/activity login callers.
- **note:** UserProfileViewModel also imports UserSessionManager but only for the KEY_RESOURCE_OPEN constant - that is a separate task below.

---

## 25. `ui/health/MyHealthFragment.kt`

**lead value:** 69 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude

**coordinate with:** unit 19 (`ui/enterprises/EnterprisesReportsFragment.kt`) via `ui/health/MyHealthFragment.kt`

### Give UserEntity one effective-id accessor and use it in the health screens

`#23`  **value** 69 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries)_

- **anchor:** grep `if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id`
- **files:** model/UserEntity.kt (top-level extension, outside the @Entity class), ui/health/HealthViewModel.kt, ui/health/MyHealthFragment.kt, 273)
- **evidence:** The "prefer the CouchDB _id, fall back to the local id" rule is copy-pasted three times in the health feature: `if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id` in HealthViewModel and the same expression on non-null receivers at MyHealthFragment 142 and 273. VERIFIED. An identity rule deciding which health records a patient sees lives in the UI in triplicate.
- **work:** Add `val UserEntity.effectiveId: String? get() = _id?.takeIf { it.isNotEmpty() } ?: id` as a top-level extension (never a member property, so Room's schema is unaffected and no @Ignore is needed) and use it at the three sites. Test _id present, _id null, and _id empty string - the third is what the isNullOrEmpty() check handles and a naive `?:` rewrite would break.

---

## 26. `ui/notifications/NotificationsViewModel.kt`  —  **bundle of 3**

**lead value:** 68 &nbsp;·&nbsp; **size:** S+M+L &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** claude, codex, copilot-grok, devin

**steps, in order:** #26 -> #73 -> #82

**coordinate with:** unit 4 (`repository/NotificationsRepositoryImpl.kt`) via `repository/NotificationsRepositoryImpl.kt`; unit 66 (`ui/notifications/NotificationsAdapter.kt`) via `ui/notifications/NotificationsAdapter.kt`

### step 1 — Resolve notification group labels in the adapter instead of carrying them through state

`#26`  **value** 68 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by claude (boundaries), codex (boundaries)_

- **anchor:** functions `buildNotificationGroups`, `typeLabelFor`
- **files:** model/NotificationListItem.kt (drop label from Header), ui/notifications/NotificationsViewModel.kt (NotificationGroup, buildNotificationGroups, typeLabelFor), ui/notifications/NotificationsAdapter.kt
- **evidence:** typeLabelFor is eight context.getString branches in the ViewModel, and the resolved string is carried as data through NotificationGroup.label into NotificationListItem.Header.label. VERIFIED, along with the sibling concern already done right: NotificationsAdapter maps type -> icon through ICON_BY_TYPE/iconResFor. A locale change also leaves the label stale until the flow re-emits.
- **work:** Add LABEL_RES_BY_TYPE + labelResFor(type) beside ICON_BY_TYPE, bind with binding.tvHeaderLabel.setText(labelResFor(header.type)), and delete the label property from Header, from NotificationGroup and from both construction sites along with typeLabelFor. NotificationsViewModelTest reads headers via filterIsInstance and asserts on type, so it should compile unchanged.
- **note:** A broader variant proposed a resource-descriptor type covering the message bodies too; the message formatters (formatTaskNotification, formatStorageNotification, formatJoinRequestNotification) are a much larger extraction - keep them out of this change.

### step 2 — Build the notification group ordering in one LinkedHashSet pass

`#73`  **value** 54 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by devin (perf)_

- **anchor:** functions `buildNotificationGroups` — grep `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()`
- **files:** ui/notifications/NotificationsViewModel.kt (buildNotificationGroups)
- **evidence:** orderedTypes is `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()` - three temporary lists plus a set on every notification refresh, and the second filter does O(n x m) membership checks against a List.
- **work:** Add the TYPE_ORDER entries present in grouped to a LinkedHashSet, then any remaining grouped.keys, preserving the exact ordering semantics (TYPE_ORDER first, then remaining keys in iteration order). Leave the groupBy and the mapNotNull return block unchanged.

### step 3 — Move the notification-feed enrichment into NotificationsRepository

`#82`  **value** 62 · **size** L · >70 lines or a new class · **risk** med · **claim** `holds` · _proposed by copilot-grok (boundaries)_

- **anchor:** functions `loadNotifications`
- **files:** repository/NotificationsRepository.kt, repository/NotificationsRepositoryImpl.kt, ui/notifications/NotificationsViewModel.kt (loadNotifications)
- **evidence:** loadNotifications collects task and join-request id lists, runs parseTaskDate over them, then fans out getTaskTeamNamesByTaskIds, getTaskTeamNamesByTaskTitles, getJoinRequestDetailsBatch and getUnreadCount (already with async, lines 105-127) and maps the result through formatNotification. The join graph - which batch lookups are needed and how their results key together - is repository orchestration living in the ViewModel.
- **work:** Add a repository result type carrying the payloads, task team names, join-request details, parsed task dates and unread count, and a loadNotificationFeed(userId, filter, isAdmin) implemented on the existing repository helpers, with parseTaskDate moved alongside. Reduce the ViewModel to that one call plus the existing R.string formatting; keep grouping and selection in the ViewModel. Do not touch NotificationDao.
- **depends on:** #26, #73 — both are local edits inside loadNotifications' file that the rewrite would otherwise carry
- **risk detail:** rewrites loadNotifications' join graph
- **note:** Large and it overlaps the label-resolution task on the same ViewModel. The batch calls are already concurrent, so this is a layering win, not a latency win.

---

## 27. `MainApplication.kt`

**lead value:** 68 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-kimi

**coordinate with:** unit 38 (`services/NotificationActionReceiver.kt`) via `services/NotificationActionReceiver.kt`

### Replace the 13 printStackTrace calls in the app-entry and sync classes with tagged logging

`#27`  **value** 68 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** grep `e.printStackTrace()`
- **files:** MainApplication.kt, ui/sync/SyncActivity.kt, services/NotificationActionReceiver.kt
- **evidence:** counts: MainApplication 4, SyncActivity 6, NotificationActionReceiver 3 - thirteen e.printStackTrace() calls writing to stderr with no tag, no level and no chance of reaching the diagnostics pipeline. Each site has a meaningful surrounding operation to name.
- **work:** Replace each with Log.e(TAG, "<operation that failed>", e), using each class's existing TAG where present. Keep every catch clause's control flow identical, and leave MainApplication.handleUncaughtException's persistCriticalLog call untouched. Verify grep -rn printStackTrace over the three files returns nothing.
- **note:** SyncActivity is claimed by an open PR in several surveys - split it out if ownership collides.

---

## 28. `utils/AndroidDecrypter.kt`

**lead value:** 68 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-kimi

### Give AndroidDecrypter's four failure paths real logging

`#28`  **value** 68 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** grep `e.printStackTrace()`
- **files:** utils/AndroidDecrypter.kt, and its test
- **evidence:** 4 printStackTrace calls: all four failure paths (decrypt, androidDecrypter, generateIv, generateKey) catch broadly, print the stack trace and silently return null/false/"". A failed health-record decryption or login key comparison is invisible in logs, and the output bypasses the app's log pipeline.
- **work:** Replace the four printStackTrace calls with tagged logging, keeping the existing return contracts exactly. Add tests pinning the contract on bad input: malformed hex -> null, invalid dbPwdKeyValue -> false, generateKey/generateIv non-blank on the happy path.
- **note:** The submitted order specified java.util.logging.Logger; use android.util.Log to match house style unless keeping this file android-free is the explicit goal.

---

## 29. `services/SubmissionsUploader.kt`

**lead value:** 67 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi, devin

### Inject ServerReachabilityProvider into SubmissionsUploader instead of the MainApplication static

`#31`  **value** 67 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (perf), devin (boundaries)_

- **anchor:** grep `suspend fun isServerReachable(urlString: String): Boolean`
- **files:** services/SubmissionsUploader.kt (constructor and the two reachability checks)
- **evidence:** SubmissionsUploader.checkAvailableServer calls the static MainApplication.isServerReachable(url) twice. VERIFIED: ServerReachabilityProvider is an injectable @Singleton exposing the same `suspend fun isServerReachable(urlString: String): Boolean` with an identical 30s TTL cache built on TimeProvider, while MainApplication's companion carries a duplicate cache using raw System.currentTimeMillis. utils/SyncTimeLogger already uses the provider - that is the reference pattern.
- **work:** Constructor-inject ServerReachabilityProvider and replace both calls; drop the MainApplication import. Preserve the primary-then-alternative semantics and the 15-second withTimeoutOrNull. Add a test for the reachability short-circuit against a mocked provider. Leave the companion cache in place for the other callers (SyncActivity, ChatRepositoryImpl) - they are separate migrations.

---

## 30. `ui/life/LifeViewModel.kt`  —  **bundle of 3**

**lead value:** 66 &nbsp;·&nbsp; **size:** S+M &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** claude, codex, devin, openhands-glm

**steps, in order:** #34 -> #47 -> #85

**coordinate with:** unit 1 (`ui/dashboard/DashboardPluginFragment.kt`) via `ui/dashboard/DashboardPluginFragment.kt`; unit 37 (`repository/LifeRepositoryImpl.kt`) via `repository/LifeRepositoryImpl.kt`

### step 1 — Stop the two redundant My Life re-queries in LifeRepositoryImpl

`#34`  **value** 66 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-glm (boundaries), codex (boundaries)_

- **anchor:** functions `updateVisibility`, `getMyLifeByUserId`, `seedMyLifeIfEmpty`
- **files:** repository/LifeRepository.kt, repository/LifeRepositoryImpl.kt (updateVisibility, getMyLifeByUserId, seedMyLifeIfEmpty), ui/life/LifeViewModel.kt (updateVisibility)
- **evidence:** Two separate redundancies, both VERIFIED. (1) updateVisibility updates the row, then calls getMyLifeByUserId purely to refresh the prefs cache and throws the list away; LifeViewModel.updateVisibility then calls loadMyLifeList(), querying my_life a second time - so every visibility toggle runs the full query twice after the write. (2) getMyLifeByUserId queries, calls seedMyLifeIfEmpty, then re-queries unconditionally, although seedMyLifeIfEmpty has already built the complete inserted list.
- **work:** Change updateVisibility to return List<MyLife> (returning the list it already fetched) and have the ViewModel assign it to _myLifeList.value instead of reloading. Refactor the seeding path to return the newly inserted rows when it wins the mutex, and use them instead of the second getByUserId. Preserve deduplication, weight ordering, null-user normalization and the mutex.

### step 2 — Let LifeRepositoryImpl own user-id normalisation and drop LifeViewModel's needless dispatcher hops

`#47`  **value** 63 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries)_

- **anchor:** grep `takeIf { it.isNotBlank() && it != "--" }`
- **files:** ui/life/LifeViewModel.kt, and its test
- **evidence:** LifeViewModel.resolveUserId() reimplements the rule the repository already applies internally - LifeRepositoryImpl.normalizeUserId is the identical `takeIf { it.isNotBlank() && it != "--" }` - so the "--" sentinel, a persistence detail, is encoded twice and the copies can drift. The same ViewModel also wraps repository calls in withContext(dispatcherProvider.io) although LifeRepositoryImpl does no blocking work of its own and Room already runs suspend DAO queries off the main thread.
- **work:** Delete resolveUserId() and pass the raw id straight through, letting the repository normalise; keep feeding the same value to MyLife.defaultItems so seeded rows carry the identical userId. Drop the three withContext(io) wrappers and the dispatcherProvider parameter if nothing else uses it. Add a test asserting a "--" current-user id is forwarded verbatim - the repository, not the ViewModel, decides what it means.

### step 3 — Move R.string resolution out of MyLife and LifeViewModel

`#85`  **value** 50 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `scoped` · _proposed by devin (boundaries)_

> **claim scoped.** Order listed only LifeFragment as a caller. There is a second: DashboardPluginFragment.getMyLifeListBase also calls MyLife.defaultItems(userId, requireContext()::getString), so the change does not compile without it. Four files, not three.

- **anchor:** functions `defaultItems` — grep `defaultItemPairs`
- **files:** model/MyLife.kt, ui/life/LifeViewModel.kt, ui/life/LifeFragment.kt, ui/dashboard/DashboardPluginFragment.kt
- **evidence:** MyLife's companion holds defaultItemPairs mapping imageIds to R.string ids, and LifeViewModel injects @ApplicationContext Context (line 21) solely to pass context::getString into MyLife.defaultItems (line 40). Android resource ids in a model block a platform-free core and force a Context into the ViewModel.
- **work:** Change defaultItems to take resolved labels, have the callers build them, and drop the Context from the ViewModel.
- **depends on:** #1 — both edit DashboardPluginFragment; #1 is far higher value, so land it first
- **risk detail:** will not compile without the fourth file, which finding #1 also edits
- **note:** INCOMPLETE AS SUBMITTED: it lists only LifeFragment as a caller. VERIFIED there is a second one - DashboardPluginFragment.getMyLifeListBase also calls MyLife.defaultItems(userId, requireContext()::getString) - so the change does not compile without updating it too, and that file is also the target of the localized-routing task. Sequence them and add the fourth file.

---

## 31. `repository/HealthRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 66 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi

**steps, in order:** #32 -> #103

**coordinate with:** unit 43 (`repository/VoicesRepositoryImpl.kt`) via `repository/HealthRepositoryImpl.kt`

### step 1 — Move the MyHealth decrypt-and-parse out of HealthExaminationViewModel

`#32`  **value** 66 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (boundaries)_

- **anchor:** functions `loadData` — grep `JsonUtils.gson.fromJson(decrypt(pojo.data, user?.key, user?.iv), MyHealth::class.java)`
- **files:** repository/HealthRepository.kt, repository/HealthRepositoryImpl.kt, ui/health/HealthExaminationViewModel.kt (loadData)
- **evidence:** HealthExaminationViewModel decrypts and Gson-parses inline: `JsonUtils.gson.fromJson(decrypt(pojo.data, user?.key, user?.iv), MyHealth::class.java)` inside a try/catch that swallows to null. VERIFIED that HealthRepositoryImpl.decodeHealth already performs the identical AndroidDecrypter.decrypt + gson.fromJson with the same null-on-failure contract, but privately.
- **work:** Add `suspend fun getDecryptedHealth(pojo: HealthExamination?, user: UserEntity?): MyHealth?` to HealthRepository, implement it on top of the existing decodeHealth, and replace the ViewModel block with that call, dropping the AndroidDecrypter/JsonUtils imports. Keep the initHealth() fallback. Test encrypted fixture -> parsed, empty data -> null, garbage -> null.
- **note:** HealthRepositoryImpl is claimed by an open PR in several agents' surveys - re-check ownership before starting.

### step 2 — Iterate entrySet instead of keySet in two repository JSON loops

`#103`  **value** 42 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (perf)_

- **anchor:** grep `payload.keySet().forEach { key -> when (key) { ... payload.get(key).asString ... } }`
- **files:** repository/UserRepositoryImpl.kt, repository/HealthRepositoryImpl.kt
- **evidence:** both: UserRepositoryImpl does `payload.keySet().forEach { key -> when (key) { ... payload.get(key).asString ... } }` - a set-view allocation plus a second lookup per key across a 12-branch when - and HealthRepositoryImpl does `for (key in conditions.keySet()) { result[key] = JsonUtils.getBoolean(key, conditions) }`, the same pattern.
- **work:** Iterate entrySet() and read entry.value, keeping the same primitive guards and null-safety the current asString / JsonUtils.getBoolean calls rely on (replicate the isJsonPrimitive check inline rather than editing JsonUtils). Output maps must be identical; test non-primitive values and empty objects.
- **note:** Small JSON objects on cold paths - the value is uniformity, not speed.

---

## 32. `ui/enterprises/EnterprisesReportsAdapter.kt`  —  **bundle of 2**

**lead value:** 66 &nbsp;·&nbsp; **size:** S+M &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** claude, copilot-kimi, openhands-glm

**steps, in order:** #35 -> #74

### step 1 — Cache the enterprise attachment existence check out of the row-bind path

`#35`  **value** 66 · **size** M · ~26-70 lines or 2-4 files · **risk** med · **claim** `holds` · _proposed by claude (perf), copilot-kimi (perf)_

- **anchor:** functions `bindFinanceImage`, `bindReportImage`
- **files:** ui/enterprises/EnterprisesFinancesAdapter.kt (bindFinanceImage), ui/enterprises/EnterprisesReportsAdapter.kt (bindReportImage)
- **evidence:** both adapters build a File via MyTeam.getAttachmentFile(...) and call imageFile.exists() directly inside onBindViewHolder - a disk stat on the UI thread for every visible row, repeated on every recycle, in two lists a team treasurer scrolls constantly. The value only changes when the underlying list changes, and ListAdapter already offers onCurrentListChanged to invalidate on.
- **work:** Add a per-adapter map keyed on the file path and use getOrPut { imageFile.exists() }, keeping the null-file early return and the View.GONE branch identical; override onCurrentListChanged to clear it so a re-submitted list after an upload or delete re-stats. Leave the onViewRecycled Glide clear() calls alone. Also move the else-branch setOnClickListener(null) so it is not re-cleared on every bind.
- **risk detail:** the exists() cache must invalidate on list re-submit or new attachments never appear
- **note:** The reports adapter also computes its row totals inline in onBindViewHolder - see the report-totals extraction task; sequence them.

### step 2 — Extract the report-totals math out of EnterprisesReportsAdapter

`#74`  **value** 54 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by openhands-glm (boundaries)_

- **anchor:** functions `onBindViewHolder` — grep `val totalIncome = report.sales + report.otherIncome`
- **files:** ui/enterprises/EnterprisesReportsAdapter.kt, and its test
- **evidence:** onBindViewHolder computes totalIncome = sales + otherIncome, totalExpenses = wages + otherExpenses, profitLoss and the ending balance inline for every bound row. The same arithmetic also appears in EnterprisesRepositoryImpl.exportReportsAsCsv.
- **work:** Add a private ReportTotals data class and a pure reportTotals(report) function in the same file and call it from onBindViewHolder - no behaviour change - then unit-test income, expenses, profit/loss and ending balance including a negative beginning balance.
- **overlaps:** #35 — both edit EnterprisesReportsAdapter
- **note:** Deliberately does not unify with the CSV path, which the SQL push-down task owns. Same file as the attachment-exists caching task.

---

## 33. `repository/ConfigurationsRepository.kt`

**lead value:** 66 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** claude, copilot-grok

### Parse community leaders inside ConfigurationsRepository

`#33`  **value** 66 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by claude (boundaries), copilot-grok (boundaries)_

- **anchor:** functions `getCommunityLeaders`
- **files:** repository/ConfigurationsRepository.kt, repository/ConfigurationsRepositoryImpl.kt (getCommunityLeaders), ui/community/LeadersViewModel.kt
- **evidence:** getCommunityLeaders(): String hands a raw CouchDB JSON blob straight out of SharedPreferences and LeadersViewModel does the deserializing itself with UserEntity.parseLeadersJson, guarded by its own isNotEmpty() check. VERIFIED. The interface exposes a serialization format instead of a domain type, so every caller has to know the blob may be empty.
- **work:** Change the interface to `fun getCommunityLeaders(): List<UserEntity>` returning UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders()) - parseLeadersJson already returns an empty list for blank/invalid input, so no extra guard. Reduce loadLeaders to a single assignment, keeping the dispatcherProvider.default launch.
- **note:** The three other parseLeadersJson call sites (VoicesFragment, TeamsVoicesFragment, ReplyActivity) read SharedPrefManager directly, not this repository, so the signature change does not reach them.

---

## 34. `repository/SubmissionsRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 65 &nbsp;·&nbsp; **size:** XS+S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, jules

**steps, in order:** #37 -> #99

**coordinate with:** unit 9 (`services/upload/PhotoUploader.kt`) via `services/upload/PhotoUploader.kt`

### step 1 — Stop exporting the SubmitPhotosDao.UploadedPhoto projection through SubmissionsRepository

`#37`  **value** 65 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by codex (boundaries)_

- **anchor:** functions `markPhotosUploadedBatch`, `uploadSubmitPhotos` — grep `SubmitPhotosDao.UploadedPhoto`
- **files:** repository/SubmissionsRepository.kt import, markPhotosUploadedBatch at 56), repository/SubmissionsRepositoryImpl.kt, services/upload/PhotoUploader.kt
- **evidence:** SubmissionsRepository imports SubmitPhotosDao.UploadedPhoto and exposes it in markPhotosUploadedBatch's signature, and PhotoUploader constructs that DAO-owned type at line 62 - Room leaking across the service/repository boundary in both directions.
- **work:** Define a small immutable repository-layer batch-update value type beside SubmissionsRepository with the same local id, revision and remote id fields; change the signature to accept it and drop the DAO import from the interface; map to SubmitPhotosDao.UploadedPhoto only inside the Impl immediately before the DAO call. Update PhotoUploader to construct the repository type, preserving chunking, callback timing and the attachment uploads.
- **overlaps:** #8 — both edit PhotoUploader
- **note:** Touches the same file as the PhotoUploader concurrency task - sequence them.

### step 2 — Deduplicate the pendingSurveys examIds in one pass

`#99`  **value** 44 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()`
- **files:** repository/SubmissionsRepositoryImpl.kt
- **evidence:** `pendingSurveys.mapNotNull { it.examIdFromParentId() }.distinct()` - mapNotNull allocates a list and distinct() allocates another to guarantee uniqueness.
- **work:** Use mapNotNullTo(LinkedHashSet()) and convert back to a list, deduplicating during insertion while preserving order. Do not touch the examIdFromParentId extension or any survey-completion logic.

---

## 35. `repository/ProgressRepositoryImpl.kt`

**lead value:** 65 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, jules

### Extract the course-progress sync keys in one pass per document

`#38`  **value** 65 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by codex (perf), jules (perf)_

- **anchor:** functions `insertCourseProgressFromSync` — grep `.map{}.filter{}.distinct()`
- **files:** repository/ProgressRepositoryImpl.kt (insertCourseProgressFromSync)
- **evidence:** the function makes four complete passes over every incoming document to collect docIds, courseIds, userIds and stepNums (each `.map{}.filter{}.distinct()`), then parses the same four fields again while constructing records. That is eight JSON lookups per document plus four intermediate lists, on a sync hot path.
- **work:** Introduce a private file-local value type for the four parsed keys, build it in one pass over docs preserving input order and the existing treatment of blank ids and duplicate query keys, derive the DAO query arguments from those rows, and reuse each row's values when resolving existingProgress and localRecord. Keep courseProgressFromJson as the single place that hydrates the entity.
- **note:** One agent proposed only the docIds micro-optimisation; the single-pass extraction supersedes it.

---

## 36. `repository/DiagnosticsRepositoryImpl.kt`

**lead value:** 64 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok

### Remove @ApplicationContext from DiagnosticsRepositoryImpl

`#40`  **value** 64 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by copilot-grok (boundaries)_

- **anchor:** functions `saveLogToRoom`, `saveLogsToRoom` — grep `VersionUtils.getVersionName(context)`
- **files:** repository/DiagnosticsRepositoryImpl.kt
- **evidence:** the constructor takes @ApplicationContext Context solely so saveLogToRoom (line 65) and saveLogsToRoom (line 83) can call VersionUtils.getVersionName(context). That keeps an Android Context dependency on an otherwise DAO/prefs repository.
- **work:** Drop the Context parameter and resolve the version from BuildConfig.VERSION_NAME (which carries the flavor's versionNameSuffix, so the -lite value still matches). Keep ApkLogDao, UserRepository and SharedPrefManager usage unchanged. No RepositoryModule change is needed.

---

## 37. `repository/LifeRepositoryImpl.kt`

**lead value:** 64 &nbsp;·&nbsp; **size:** L &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok

**coordinate with:** unit 2 (`services/SharedPrefManager.kt`) via `services/SharedPrefManager.kt`; unit 30 (`ui/life/LifeViewModel.kt`) via `repository/LifeRepositoryImpl.kt`

### Seal the My Life prefs cache behind SharedPrefManager and collapse the triplicated MyLifeDao predicate

`#53`  **value** 64 · **size** L · >70 lines or a new class · **risk** low · **claim** `holds` · _proposed by copilot-grok (boundaries)_

- **anchor:** functions `cacheMyLifeItems`, `getMyLifeForDashboard` — grep `(:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)`
- **files:** services/SharedPrefManager.kt, repository/LifeRepositoryImpl.kt (cacheMyLifeItems, getMyLifeForDashboard), data/room/dao/MyLifeDao.kt
- **evidence:** LifeRepositoryImpl reads and writes its cache through sharedPrefManager.rawPreferences directly (getString at line 105, edit { putString(...) } at line 135) using androidx.core.content.edit - an Android preferences API inside a repository. Separately, MyLifeDao repeats the same long nullable-user predicate `(:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)` verbatim in getByUserId, getVisibleByUserId and countByUserId.
- **work:** Add typed get/set/clear helpers on SharedPrefManager for the existing myLifeCache_<userId> key space, keeping the key prefix identical, and route the repository through them (Gson + CachedMyLifeItem mapping stays in the repository). Collapse the three DAO predicates into one equivalent form preserving the guest/blank/-- semantics, and pin it with DAO tests for null, blank, '--' and a real id.
- **overlaps:** #34, #88 — shares LifeRepositoryImpl with #34 and SharedPrefManager with #88

---

## 38. `services/NotificationActionReceiver.kt`

**lead value:** 64 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

**coordinate with:** unit 27 (`MainApplication.kt`) via `services/NotificationActionReceiver.kt`

### Inject @ApplicationScope into NotificationActionReceiver

`#43`  **value** 64 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (boundaries)_

- **anchor:** functions `onReceive` — grep `MainApplication.applicationScope.launch`
- **files:** services/NotificationActionReceiver.kt
- **evidence:** onReceive uses MainApplication.applicationScope.launch, although the receiver is already an @AndroidEntryPoint Hilt BroadcastReceiver that field-injects NotificationsRepository and DispatcherProvider - so it can field-inject the provided @ApplicationScope CoroutineScope and drop the static.
- **work:** Add `@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope`, replace the launch call, and remove the MainApplication import. Keep goAsync()/pendingResult handling and the notification routing unchanged.

---

## 39. `services/ResourceDownloadCoordinator.kt`

**lead value:** 64 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

### Inject @ApplicationScope into ResourceDownloadCoordinator

`#42`  **value** 64 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (boundaries)_

- **anchor:** functions `startBackgroundDownload` — grep `MainApplication.applicationScope.launch`
- **files:** services/ResourceDownloadCoordinator.kt
- **evidence:** startBackgroundDownload launches its work on the global MainApplication.applicationScope, while di/ServiceModule already provides an injectable @ApplicationScope CoroutineScope (annotation at line 41, provider at 49-50). Using it removes a direct MainApplication dependency from a @Singleton service class.
- **work:** Add `@ApplicationScope private val applicationScope: CoroutineScope` to the constructor, replace MainApplication.applicationScope.launch with applicationScope.launch, and drop the MainApplication import. Background downloads must still launch only when the server is reachable.

---

## 40. `services/sync/ServerUrlMapper.kt`

**lead value:** 64 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm, openhands-kimi

### Clean up ServerUrlMapper: drop the empty also block and log the malformed-URL failure

`#39`  **value** 64 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries), openhands-glm (perf)_

- **anchor:** functions `processUrl`, `extractBaseUrl` — grep `serverMappings[baseUrl].also { }`
- **files:** services/sync/ServerUrlMapper.kt (processUrl, extractBaseUrl)
- **evidence:** processUrl reads `serverMappings[baseUrl].also { }` - an also block with an empty body, left over from a removed log line, allocating a lambda for nothing - then binds the result to `val result = UrlMapping(...)` and returns it on the next line. extractBaseUrl swallows malformed-URL errors with e.printStackTrace(). VERIFIED. This class is on the sync/upload URL-failover path, so silent failures here hide why a device never fails over to a clone server.
- **work:** Reduce to `val alternativeUrl = extractedUrl?.let { serverMappings[it] }` and `return UrlMapping(url, alternativeUrl, extractedUrl)`; replace the printStackTrace with Log.w(TAG, "Could not extract base url from $url", e). Extend the test with a mapped primary, an unmapped host (alternativeUrl == null), a non-default port preserved in extractedBaseUrl, and a malformed string returning null without throwing.

---

## 41. `ui/settings/StorageCategoryDetailFragment.kt`

**lead value:** 64 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

### Remove @ApplicationContext from StorageCategoryViewModel

`#41`  **value** 64 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by devin (boundaries)_

- **anchor:** functions `loadResources`, `deleteItems` — grep `FileUtils.getOlePath(context)`
- **files:** ui/settings/StorageCategoryViewModel.kt, ui/settings/StorageCategoryDetailFragment.kt
- **evidence:** the ViewModel holds @ApplicationContext Context (line 36) only to call FileUtils.getOlePath(context) twice - in loadResources (line 52) and again inside deleteItems (line 97). Its only caller, StorageCategoryDetailFragment, already has a Context.
- **work:** Drop the Context parameter, take olePath: String on loadResources/deleteSelected/deleteAll/deleteItems, and compute it once in the fragment's onViewCreated. Removes an Android Context field from a ViewModel.

---

## 42. `model/RetryOperation.kt`  —  **bundle of 2**

**lead value:** 63 &nbsp;·&nbsp; **size:** S+M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi, openhands-glm

**steps, in order:** #50 -> #48

### step 1 — Inject TimeProvider into RetryOperation's factory and backoff calculation

`#50`  **value** 62 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (perf)_

- **anchor:** functions `createFromRetryFailure`, `calculateNextRetryTime`
- **files:** model/RetryOperation.kt (createFromRetryFailure, calculateNextRetryTime), repository/RetryRepositoryImpl.kt
- **evidence:** createFromRetryFailure sets createdTime/lastAttemptTime from System.currentTimeMillis() and calculateNextRetryTime returns System.currentTimeMillis() + delay. utils/TimeProvider is an injectable interface provided by di/TimeModule, and RetryRepositoryImpl already injects timeProvider (it uses it at line 43) - the only two call sites of both functions are in that same file.
- **work:** Thread a TimeProvider through both companion functions and pass the repository's existing instance at both call sites. Keep the exponential backoff values identical (30s, 60s, ... capped at 30 min) and add a fake-clock test asserting them.

### step 2 — Consolidate the retry attempt transition and stop the read-modify-write

`#48`  **value** 63 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-glm (perf), openhands-glm (boundaries)_

- **anchor:** functions `updateAttempt`, `markFailed`
- **files:** repository/RetryRepositoryImpl.kt (updateAttempt, markFailed), model/RetryOperation.kt, data/room/dao/RetryDao.kt
- **evidence:** updateAttempt and markFailed each do findById(id) -> mutate the loaded RetryOperation -> update(op), i.e. a SELECT plus an UPDATE on every retry attempt, and both open-code the same transition (increment attemptCount, set lastAttemptTime, recompute nextRetryTime, decide abandon-vs-pending from attemptCount >= maxAttempts). The sibling methods markInProgress and markCompleted are already one-line @Query UPDATEs in RetryDao.
- **work:** Put the pure state transition on RetryOperation as a method (it has no Android imports and is unit-testable without a DAO), and add a @Query UPDATE to RetryDao so the two paths become a single statement. Preserve the status transitions exactly - markFailed keeps STATUS_PENDING unless the new count reaches maxAttempts, then STATUS_ABANDONED. No schema change, no AppDatabase version bump, no backoff-constant change.
- **depends on:** #50 — thread the clock through the factory before consolidating the transition on top of it
- **note:** Two agents proposed the two halves (single UPDATE / model method); they compose into one change.

---

## 43. `repository/VoicesRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 63 &nbsp;·&nbsp; **size:** XS+S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, copilot-kimi, jules

**steps, in order:** #44 -> #51

**coordinate with:** unit 31 (`repository/HealthRepositoryImpl.kt`) via `repository/HealthRepositoryImpl.kt`

### step 1 — Purge android.text.TextUtils from the Voices and Health repository implementations

`#44`  **value** 63 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (boundaries), codex (boundaries)_

- **anchor:** functions `getFilteredNews` — grep `TextUtils.isEmpty`
- **files:** repository/VoicesRepositoryImpl.kt, 454, 459), repository/HealthRepositoryImpl.kt, 217)
- **evidence:** all three call sites and both imports: two repository implementations use android.text.TextUtils.isEmpty for plain null/empty String checks. A repository needs no Android framework helper for this, and each removed android.* import is one less platform coupling.
- **work:** Replace TextUtils.isEmpty(x) with x.isNullOrEmpty() (and the negated form likewise) at the three sites, preserving current null-and-empty behaviour, and delete both imports. Run VoicesRepositoryImplTest, VoicesRepositoryNewsSyncTest and HealthRepositoryImplTest.

### step 2 — Collapse the double collection pass in VoicesRepositoryImpl.insertNewsList

`#51`  **value** 61 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by codex (perf), jules (perf)_

- **anchor:** functions `insertNewsList` — grep `mappedDocs = docs.map { it to getString("_id", it) }`
- **files:** repository/VoicesRepositoryImpl.kt (insertNewsList)
- **evidence:** insertNewsList builds `mappedDocs = docs.map { it to getString("_id", it) }`, then maps that whole list again and filters it (`mappedDocs.map { it.second }.filter { it.isNotEmpty() }`) just to get the lookup ids - two more intermediate collections on potentially hundreds of news records, after the mapping had already removed an N+1 query.
- **work:** Keep one ordered document/id representation so _id is parsed once per document and populate the non-blank id query list during that same traversal. Preserve duplicate ids, blank-id handling, input order, the single bulk DAO lookup and associateBy conflict behaviour; keep building every News through buildNewsFromJson with one upsertAll.

---

## 44. `model/Achievement.kt`

**lead value:** 63 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** copilot-kimi

### Stop the per-element deepCopy that defeats Achievement's parse cache

`#55`  **value** 63 · **size** S · ~6-25 lines · **risk** med · **claim** `holds` · _proposed by copilot-kimi (perf)_

- **anchor:** functions `parseStringListToJsonArray` — grep `array.add(ob?.deepCopy())`
- **files:** model/Achievement.kt (parseStringListToJsonArray)
- **evidence:** a synchronized LRU parsedJsonCache (capacity 1000) exists, but parseStringListToJsonArray looks up the cache and then calls `array.add(ob?.deepCopy())` for every element, so the cache saves the parse but still pays a full tree clone per item on every call.
- **work:** Audit the callers of the functions that use parseStringListToJsonArray: if none mutates the returned JsonArray's elements, add the cached element directly. If mutation risk exists, deep-copy once at cache-insert time and hand out the shared element, documenting the no-mutation contract. Keep CACHE_CAPACITY eviction and the public API. Add a test asserting cache hits return equal content without re-parsing.
- **risk detail:** dropping deepCopy is only safe if no caller mutates the returned JsonArray - audit first
- **note:** The mutation audit is the real work here - budget for it rather than dropping deepCopy blind.

---

## 45. `model/Transaction.kt`

**lead value:** 63 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok, openhands-glm

### Derive the finances header from transactions and move the totals math onto Transaction

`#46`  **value** 63 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by openhands-glm (boundaries), copilot-grok (boundaries)_

- **anchor:** functions `calculateTotal`, `getTeamTransactions` — grep `_headerState`
- **files:** ui/enterprises/EnterprisesFinancesViewModel.kt, model/Transaction.kt
- **evidence:** the ViewModel keeps two parallel MutableStateFlows, _transactions and _headerState. getTeamTransactions collects the list, sets _transactions.value, then calls the imperative calculateTotal(results), which loops the list again to recompute debit/credit/total and mutates _headerState. headerState is a pure function of transactions.
- **work:** Turn calculateTotal into a pure function returning FinanceHeaderState (with the summing itself as a helper on Transaction or its companion), expose headerState as transactions.map { ... }.stateIn(viewModelScope, WhileSubscribed(5000), FinanceHeaderState()), and delete _headerState and the imperative call. Keep isCautionVisible = total < 0. Cover credit-only, debit-only, mixed and empty.

---

## 46. `repository/PersonalsRepositoryImpl.kt`

**lead value:** 62 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi

**coordinate with:** unit 17 (`data/room/dao/PersonalDao.kt`) via `repository/PersonalsRepositoryImpl.kt`; unit 47 (`repository/UploadRepositoryImpl.kt`) via `repository/UploadRepositoryImpl.kt`

### Route the personals document POST through UploadRepository

`#49`  **value** 62 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (boundaries)_

- **anchor:** functions `uploadPersonalDocument`
- **files:** repository/UploadRepository.kt, repository/UploadRepositoryImpl.kt, repository/PersonalsRepositoryImpl.kt (uploadPersonalDocument)
- **evidence:** PersonalsRepositoryImpl.uploadPersonalDocument calls apiInterface.postDoc(...) directly at line 82 while already injecting UploadRepository at line 21 (and using it for uploadAttachment at line 117). One repository holding two upload paths, one of them bypassing the upload boundary.
- **work:** Add a postDocument(url, payload) wrapper to UploadRepository/Impl around the same apiInterface.postDoc call, switch PersonalsRepositoryImpl to it, and drop the ApiInterface field and import. First confirm UploadRepositoryImpl does not already expose an equivalent POST - if it does, use it and the change shrinks to two files.
- **overlaps:** #17 — both edit PersonalsRepositoryImpl

---

## 47. `repository/UploadRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 61 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, openhands-glm

**steps, in order:** #52 -> #104

**coordinate with:** unit 46 (`repository/PersonalsRepositoryImpl.kt`) via `repository/UploadRepositoryImpl.kt`

### step 1 — Replace the openConnection MIME probe in UploadRepositoryImpl.uploadAttachment

`#52`  **value** 61 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-glm (perf)_

- **anchor:** functions `uploadAttachment` — grep `file.toURI().toURL().openConnection()`
- **files:** repository/UploadRepositoryImpl.kt (uploadAttachment)
- **evidence:** uploadAttachment opens `file.toURI().toURL().openConnection()` solely to read connection.contentType, and never reads a byte from that connection - so every attachment/photo/CV upload does a content-sniffing open of the file before the real upload. The project already has the local-only patterns: URLConnection.guessContentTypeFromName(file.name) (used in WebViewActivity) and FileUtils.getMimeType(name) (used in UploadManager).
- **work:** Replace the openConnection + contentType lookup with `URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"` and drop the import if it becomes unused. Leave the header map via FileUploader.getHeaderMap, the request body and the response handling untouched - the resolved type still feeds getHeaderMap(mimeType, rev), so uploads must keep sending the right Content-Type for pdf/jpg/png.

### step 2 — Pre-size the exam upload result buckets

`#104`  **value** 41 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by codex (perf)_

- **anchor:** functions `markExamsUploaded`
- **files:** repository/UploadRepositoryImpl.kt (markExamsUploaded)
- **evidence:** markExamsUploaded creates default-capacity mutable lists for the updated exams and the local-lookup failures and grows them while partitioning the successful network results.
- **work:** Give the updated-exam bucket capacity equal to the succeeded count and the failure bucket a small hint, using explicit add calls. Preserve input ordering, missing-local-row reporting, revision assignment and the single upsertAll.
- **note:** Effectively free but also effectively worthless: ArrayList growth over a <=50-item batch is noise. Take it only as a drive-by while already in this function.

---

## 48. `ui/community/CommunityServicesFragment.kt`  —  **bundle of 3**

**lead value:** 60 &nbsp;·&nbsp; **size:** XS+M+L &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin, openhands-kimi

**steps, in order:** #67 -> #75 -> #90

### step 1 — Extract community-service route resolution into a tested pure function

`#67`  **value** 60 · **size** L · >70 lines or a new class · **risk** low · **claim** `holds` · _proposed by openhands-kimi (boundaries)_

- **anchor:** functions `setRecyclerView`
- **files:** ui/community/CommunityServicesFragment.kt (setRecyclerView), ui/community/CommunityServiceRoute.kt (new)
- **evidence:** the click listener parses routes inline - http/https prefix check, then rawRoute.split("/") taking segments[3] as a team id when size >= 4, else a WebView fallback - interleaved with the navigation and membership-check code. Navigation policy buried in a view, with zero tests.
- **work:** Add a sealed CommunityServiceRoute (ExternalLink / TeamLink / Unhandled) and a pure resolve(route) reproducing today's rules exactly, then switch the listener over it keeping the same resulting actions. Test an https URL, an http URL, a /teams/view/<id>-style route, a route with fewer than 4 segments, and the empty string.
- **note:** Three separate orders touch this one fragment (route extraction, the LayoutInflater hoist, and the ViewModel extraction) - sequence them.

### step 2 — Move the CommunityServices repository calls into a ViewModel

`#75`  **value** 58 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by devin (boundaries)_

- **anchor:** functions `setRecyclerView` — grep `teamsRepository.getTeamLinks()`
- **files:** ui/community/CommunityServicesFragment.kt, ui/community/CommunityServicesViewModel.kt (new)
- **evidence:** the fragment calls teamsRepository.getTeamLinks() at line 61 and teamsRepository.isMember(user?.id, teamId) at line 101 directly from lifecycle scopes. A ViewModel exists in the package (CommunityTabViewModel) but does not cover this tab.
- **work:** Create a @HiltViewModel CommunityServicesViewModel injecting TeamsRepository and UserRepository, exposing getTeamLinks() and isMember(teamId) (resolving the user id itself), and switch the fragment to it. Keep the resulting navigation and the isMyTeam argument identical.

### step 3 — Hoist the LayoutInflater out of the community services-link loop

`#90`  **value** 49 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (perf)_

- **anchor:** functions `setRecyclerView` — grep `links.forEach { ... LayoutInflater.from(activity).inflate(R.layout.button_single, parent, false) ... }`
- **files:** ui/community/CommunityServicesFragment.kt (setRecyclerView)
- **evidence:** `links.forEach { ... LayoutInflater.from(activity).inflate(R.layout.button_single, parent, false) ... }` calls LayoutInflater.from inside the loop for every rendered service link, re-resolving the context theme and service on each call.
- **work:** Resolve the inflater once before the loop (parent is non-null after the early return) and reuse it. Keep the padding, the addView order and every listener identical.
- **note:** Third task touching this one fragment, alongside the route extraction and the ViewModel extraction - sequence them.

---

## 49. `repository/TeamsRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 60 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** high &nbsp;·&nbsp; **from:** codex

**steps, in order:** #86 -> #65

### step 1 — Expose team update signals through TeamsRepository instead of RealtimeSyncManager

`#86`  **value** 60 · **size** S · ~6-25 lines · **risk** high · **claim** `holds` · _proposed by codex (boundaries)_

- **anchor:** functions `getTeamUpdateFlow` — grep `fun getTeamUpdateFlow() = realtimeSyncManager.updatesFor("teams")`
- **files:** ui/teams/TeamViewModel.kt (getTeamUpdateFlow), repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt
- **evidence:** TeamViewModel imports RealtimeSyncManager (line 20), injects it (line 33) and exposes `fun getTeamUpdateFlow() = realtimeSyncManager.updatesFor("teams")` (line 41) - UI state depending on a sync service rather than its feature repository, and the "teams" collection-name literal living in the UI.
- **work:** Add a narrowly named team-update Flow method to TeamsRepository whose return type matches updatesFor("teams"), delegate to the existing realtime manager inside TeamsRepositoryImpl with the fixed collection key, and remove RealtimeSyncManager from the ViewModel.
- **risk detail:** all three files are among the most PR-contended in the repo
- **note:** All three files are among the most PR-contended in the repo (TeamsRepositoryImpl is the 1437-line hotspot); feasibility, not correctness, is the constraint.

### step 2 — Collapse the member-visit aggregation into one pass and drop the unused MemberStats

`#65`  **value** 56 · **size** S · ~6-25 lines · **risk** high · **claim** `holds` · _proposed by codex (perf)_

- **anchor:** grep `logs.groupingBy { it.user }.eachCount()`
- **files:** repository/TeamsRepositoryImpl.kt (getJoinedMembersWithVisitInfo, the visit-log aggregation only)
- **evidence:** the visit logs are traversed twice - `logs.groupingBy { it.user }.eachCount()` and then `logs.groupBy { it.user }.mapValues { ... maxOfOrNull { it.time ?: 0 } }` - and the second pipeline retains a list per user, so one accumulator map lowers both CPU and peak memory on large teams. A local `data class MemberStats` declared at line 977 is unused.
- **work:** Replace the unused MemberStats with a small local aggregate holding a visit count and greatest non-null timestamp, traverse logs once keyed by the same nullable user key both collections use today, and read count and latest timestamp from it while mapping orderedMembers. Preserve the zero-visits / no-latest-timestamp defaults.
- **risk detail:** TeamsRepositoryImpl is the most PR-contended file in the repo
- **note:** The bigger hotspot in this function is the per-member activitiesRepository.getLastVisit / getOfflineVisitCount N+1 - explicitly out of scope here. TeamsRepositoryImpl is the most PR-contended file in the repo.

---

## 50. `ui/courses/TakeCourseFragment.kt`

**lead value:** 59 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm, openhands-kimi

### Cache the step-label pattern in TakeCourseFragment

`#57`  **value** 59 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (perf), openhands-glm (perf)_

- **anchor:** functions `setStepText`
- **files:** ui/courses/TakeCourseFragment.kt (setStepText)
- **evidence:** setStepText builds its label with String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", ...) - a resource lookup plus template-string concatenation on every call. It runs on each page selection (updateStepDisplay), each next tap and each previous tap, i.e. per swipe through a course.
- **work:** Hold the resolved label (or the whole pattern) in a private lazy field per fragment instance and use it in setStepText, keeping Locale.getDefault() as the format locale. A locale change recreates the activity, so the cache cannot go stale. Verify the "Course Details" branch at position 0 is unaffected.

---

## 51. `repository/CoursesRepositoryImpl.kt`  —  **bundle of 3**

**lead value:** 58 &nbsp;·&nbsp; **size:** XS+S &nbsp;·&nbsp; **risk:** high &nbsp;·&nbsp; **from:** codex, copilot-kimi, jules

**steps, in order:** #58 -> #81 -> #96

### step 1 — Remove the three identity map { it } copies from course-detail hydration

`#58`  **value** 58 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by codex (perf)_

- **anchor:** grep `.map { it }`
- **files:** repository/CoursesRepositoryImpl.kt (getCourseProgress,
- **evidence:** three DAO results are copied through identity `.map { it }` calls before grouping or filtering - questionDao.getByExamIds(examIds).map { it }.groupBy {...}, submissionDao.getExamSubmissionsByUser(userId).map { it }.filter {...}, and answerDao.getBySubmissionIds(submissionIds).map { it }.groupBy {...}. Pure allocation with no type, order or value change, on the path assembled whenever course details and progress are built.
- **work:** Feed each DAO result directly into its groupBy/filter. Preserve the empty-input short circuits, key filtering, ordering and nullable-key behaviour exactly.
- **note:** CoursesRepositoryImpl is claimed by several open PRs - check ownership first; the change is 6 lines and easy to rebase.

### step 2 — Replace android.util.Base64 in CoursesRepositoryImpl with a byte-identical java.util.Base64

`#81`  **value** 52 · **size** S · ~6-25 lines · **risk** high · **claim** `holds` · _proposed by copilot-kimi (boundaries)_

- **anchor:** grep `import android.util.Base64`
- **files:** repository/CoursesRepositoryImpl.kt import,
- **evidence:** `import android.util.Base64` with a single usage - `Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)` deriving a course step id. java.util.Base64 is available at minSdk 26 and is a platform-free equivalent.
- **work:** Read the exact flags first: NO_WRAP suppresses line breaks but KEEPS padding, so the parity-preserving replacement is java.util.Base64.getEncoder() (with padding), not withoutPadding(). This id is persisted and compared, so output must be byte-identical - add a test pinning the derived step id for a fixed JsonObject to the known Base64 string, then remove the android import.
- **risk detail:** the step id is persisted and compared - output must be byte-identical (NO_WRAP keeps padding)
- **note:** CoursesRepositoryImpl is heavily PR-contended; the persisted-id parity requirement is the real risk.

### step 3 — Collapse the map-then-filter in flushPendingCourseResources

`#96`  **value** 45 · **size** XS · ~1-5 lines · **risk** med · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `batch.map { JsonUtils.getString("_id", it.doc) }.filter { it.isNotBlank() }`
- **files:** repository/CoursesRepositoryImpl.kt
- **evidence:** `batch.map { JsonUtils.getString("_id", it.doc) }.filter { it.isNotBlank() }` builds an intermediate list of strings before filtering.
- **work:** Collapse into a single mapNotNull with a takeIf on the extracted id. Rename the inner lambda parameter rather than shadowing `it`. No functional change.
- **risk detail:** CoursesRepositoryImpl is claimed by several open PRs
- **note:** CoursesRepositoryImpl is claimed by several open PRs.

---

## 52. `data/room/dao/HealthExaminationDao.kt`

**lead value:** 58 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi

### Batch the null-rev health markUploaded updates

`#71`  **value** 58 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `holds` · _proposed by copilot-kimi (boundaries)_

- **anchor:** grep `markUploaded(idToRevMap)`
- **files:** data/room/dao/HealthExaminationDao.kt
- **evidence:** the map overload `markUploaded(idToRevMap)` is a @Transaction that loops `idToRevMap.forEach { (id, rev) -> markUploaded(id, rev) }`, one UPDATE per entry. It is driven from HealthRepositoryImpl.markHealthExaminationsUploaded via UploadToShelfService, whose common path passes null revs for already-synced docs.
- **work:** Keep the single-row markUploaded(id, rev), and in the map overload group the null-rev ids and batch them with `UPDATE health_examinations SET isUpdated = 0 WHERE _id IN (:ids)`, chunked at 900 exactly as RemovedLogDao already does. Non-null revs still need per-row updates since the rev differs per row. Add a Room in-memory test covering rows with rev, rows without, a mixed batch and the empty-map no-op.
- **risk detail:** chunk at 900 as RemovedLogDao already does
- **note:** Gain is bounded - these UPDATEs already run inside one transaction - so the new DAO test is arguably the bigger win.

---

## 53. `ui/submissions/SubmissionDetailFragment.kt`

**lead value:** 58 &nbsp;·&nbsp; **size:** L &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm

### Consolidate SubmissionDetailViewModel's five derived flows into one UiState

`#72`  **value** 58 · **size** L · >70 lines or a new class · **risk** low · **claim** `holds` · _proposed by openhands-glm (boundaries)_

- **anchor:** grep `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000)`
- **files:** ui/submissions/SubmissionDetailViewModel.kt, ui/submissions/SubmissionDetailFragment.kt
- **evidence:** five separately-stateIn'd flows (questionAnswers, title, status, date, submittedBy), each a filterNotNull().map{}.stateIn(...) chain off the same submissionDetailState, and the fragment collects all five with five collectWhenStarted blocks.
- **work:** Define a SubmissionDetailUiState holding all five fields with the current 'unknown' defaults, expose one StateFlow mapping submissionDetailState (null -> defaults), delete the five flows, and collect once in the fragment.
- **note:** A separate order proposed adding distinctUntilChanged() to those five flows instead; that was dropped as a no-op (stateIn already conflates by equality). This consolidation is the real cleanup.

---

## 54. `utils/MarkdownUtils.kt`

**lead value:** 58 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm

### Quote the img src attribute in MarkdownUtils.prependBaseUrlToImages

`#80`  **value** 58 · **size** XS · ~1-5 lines · **risk** low · **claim** `corrected` · _proposed by openhands-glm (perf)_

> **claim corrected.** Order framed this as Markwon-instance caching. FALSE: the Markwon instance is already double-check-locked and the image pattern already compiled once. What is true and was buried in the order's own body: the emitted <img src=$fullUrl ...> has an unquoted attribute, so a URL containing a space truncates. That is a rendering bug.

- **anchor:** functions `prependBaseUrlToImages` — grep `"<img src=$fullUrl width=$width height=$height/>"`
- **files:** utils/MarkdownUtils.kt (prependBaseUrlToImages)
- **evidence:** the function appends `"<img src=$fullUrl width=$width height=$height/>"` with an unquoted src attribute, so any resolved URL containing a space truncates at the space when rendered.
- **work:** Lift the tag into a template with a quoted src value and format the URL into it, keeping the matcher/StringBuilder loop identical. Test a relative resources/foo.png image resolving and rendering, plus a URL containing a space.
- **note:** The submitted order framed this as a performance/Markwon-caching task; VERIFIED the Markwon instance is already double-check-locked and the pattern already compiled once, so the only real content here is the quoting fix. Kept and re-titled accordingly.

---

## 55. `ui/chat/ChatDetailFragment.kt`

**lead value:** 57 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok

### Cache the parsed ai_models map in ChatDetailFragment

`#62`  **value** 57 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-grok (perf)_

- **anchor:** functions `getModelsMap`, `getCachedProviderAvailability`
- **files:** ui/chat/ChatDetailFragment.kt (getModelsMap, getCachedProviderAvailability)
- **evidence:** getModelsMap re-reads sharedPrefManager.getRawString("ai_models") and Gson-parses the full Map<String, String> on every call, and getCachedProviderAvailability calls getModelsMap again on top of the other call sites.
- **work:** Cache the last raw string plus the parsed map in fragment fields, invalidating when the raw string changes (so a mid-session preference change is still picked up), and make getCachedProviderAvailability use the cached map once. Do not touch ChatViewModel, the adapters, or JsonUtils.
- **note:** ChatDetailFragment appears in several open-PR file sets - confirm ownership.

---

## 56. `ui/dashboard/BellDashboardViewModel.kt`

**lead value:** 57 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, jules

### Build the survey reminder ids and pending rows in single-pass collections

`#61`  **value** 57 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by codex (perf), jules (perf)_

- **anchor:** functions `handleDueReminders` — grep `surveyIdList.mapNotNull { submissionsById[it] }.filter { it.status == "pending" }`
- **files:** ui/dashboard/BellDashboardViewModel.kt (handleDueReminders)
- **evidence:** handleDueReminders chains flatMap + filter + distinct for all survey ids, then chains mapNotNull + filter for every reminder group (`surveyIdList.mapNotNull { submissionsById[it] }.filter { it.status == "pending" }`).
- **work:** Build all non-blank survey ids into an insertion-ordered set in one traversal and pass them to the existing bulk submission lookup unchanged; build each group's pending list in one pass combining lookup and status filtering. Preserve duplicate suppression for the bulk query, per-group order, empty-group skipping and the emitted prompt contents.

---

## 57. `ui/voices/VoicesActions.kt`

**lead value:** 57 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

### Use case-insensitive endsWith for the GIF checks

`#63`  **value** 57 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (perf)_

- **anchor:** functions `showZoomableImage`, `addImageWithRemoveIcon` — grep `imagePath.lowercase(Locale.getDefault()).endsWith(".gif")`
- **files:** utils/ImageViewerUtils.kt (showZoomableImage), ui/voices/VoicesActions.kt (addImageWithRemoveIcon)
- **evidence:** both sites: `imagePath.lowercase(Locale.getDefault()).endsWith(".gif")` allocates a lowercase copy of the whole path on every image binding, and makes the check locale-dependent. String.endsWith(suffix, ignoreCase = true) is allocation-free and locale-independent.
- **work:** Replace both with endsWith(".gif", ignoreCase = true); remove the java.util.Locale import from ImageViewerUtils but keep it in VoicesActions, where dateFormatter still uses it. Leave the Glide asGif/load/error branches alone.

---

## 58. `ui/voices/VoicesViewModel.kt`

**lead value:** 56 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-kimi

### Parse each news item's images JSON once in downloadReferencedResources

`#68`  **value** 56 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-kimi (perf)_

- **anchor:** functions `downloadReferencedResources`
- **files:** ui/voices/VoicesViewModel.kt (downloadReferencedResources)
- **evidence:** News.imagesArray re-parses the images JSON with Gson on every access (no caching in the getter), and the loop dereferences news?.imagesArray twice per item - once for the isEmpty() check and once for get(0) - so a feed of N posts with images triggers 2N full JSON parses every time the feed loads.
- **work:** Hoist `val images = news?.imagesArray` once per item and use the local for both the emptiness check and the element access. Keep the resourceIds dedup set and the repository calls unchanged.
- **note:** Complementary to the News parse-cache task: that one fixes the getter, this one stops the double call.

---

## 59. `utils/FileUtils.kt`

**lead value:** 55 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, copilot-grok

### Make FileUtils.findHtmlCoverImage a single lazy pass

`#69`  **value** 55 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by codex (perf), copilot-grok (perf)_

- **anchor:** functions `findHtmlCoverImage`
- **files:** utils/FileUtils.kt (findHtmlCoverImage)
- **evidence:** the function walks up to four directory levels, materializes every matching image with .toList(), then scans that list for a filename hint and may scan it a third time for the largest file. Large offline HTML resources can hold many images.
- **work:** Iterate the walk lazily instead of converting to a list, computing each filename's lowercase form once and returning the first hint match immediately (current semantics already take the first hint in walk order), while tracking the largest candidate for the unchanged fallback. Keep the depth limit, extension allowlist, walk order, case-insensitive extension handling and tie behaviour, and null for empty or non-directory input.

---

## 60. `model/NewsLog.kt`

**lead value:** 54 &nbsp;·&nbsp; **size:** M &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

### Pass androidId and customDeviceName into the model serializers instead of reading MainApplication.context

`#76`  **value** 54 · **size** M · ~26-70 lines or 2-4 files · **risk** low · **claim** `scoped` · _proposed by devin (boundaries)_

> **claim scoped.** Order listed model/ApkLog.kt as a fourth site. It is already migrated - ApkLog.serialize takes customDeviceName as a parameter and calls addDocumentOrigin() with no argument. Three files, not four.

- **anchor:** grep `import MainApplication.Companion.context`
- **files:** services/upload/UploadConfigs.kt, model/NewsLog.kt, model/Rating.kt, model/SearchActivity.kt
- **evidence:** NewsLog.serialize, Rating.serializeRating and SearchActivity.serialize pull androidId / customDeviceName from the MainApplication companion context (VERIFIED: NewsLog line 36, Rating's `import MainApplication.Companion.context`, SearchActivity lines 38 and 40). UploadConfigs already injects @ApplicationContext Context and SharedPrefManager, so it can compute both once and pass them in.
- **work:** Compute androidId and customDeviceName once in UploadConfigs and thread them through the three serializer lambdas; change the three signatures to accept them and drop the MainApplication (and now-unused VersionUtils/NetworkUtils) imports. Uploaded payloads must keep the same androidId, deviceName and customDeviceName fields.
- **note:** SCOPE CORRECTED: the submitted order also listed model/ApkLog.kt as a fourth site. VERIFIED it is already migrated - ApkLog.serialize takes customDeviceName as a parameter and calls addDocumentOrigin() with no argument. Three files, not four.

---

## 61. `ui/sync/ForceSyncPolicy.kt`

**lead value:** 53 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm

### Swap java.util.concurrent.TimeUnit for kotlin.time in ForceSyncPolicy

`#79`  **value** 53 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-glm (boundaries)_

- **anchor:** grep `import java.util.concurrent.TimeUnit`
- **files:** ui/sync/ForceSyncPolicy.kt, and its test
- **evidence:** the object is otherwise pure and well-tested with no Android imports, except `import java.util.concurrent.TimeUnit` used for TimeUnit.MILLISECONDS.toDays(nowMillis - lastSyncMillis); the test uses TimeUnit.DAYS.toMillis for fixtures. TimeUnit is JVM-only and blocks moving this sync-cadence policy into a common module.
- **work:** Use the kotlin.time equivalent ((nowMillis - lastSyncMillis).milliseconds.inWholeDays) and drop the import; swap the test fixtures likewise. Keep the signatures, the constants and the inclusive-threshold semantics identical.

---

## 62. `ui/teams/TeamPagerAdapter.kt`

**lead value:** 53 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

### Use getOrPut for the TeamPagerAdapter stable ids

`#77`  **value** 53 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (perf)_

- **anchor:** functions `init`, `updatePages` — grep `if (!itemIds.containsKey(page.id)) { itemIds[page.id] = nextId++ }`
- **files:** ui/teams/TeamPagerAdapter.kt (init, updatePages)
- **evidence:** both sites: `if (!itemIds.containsKey(page.id)) { itemIds[page.id] = nextId++ }` - a containsKey followed by a put for every page during initialization and on every page update.
- **work:** Replace both with itemIds.getOrPut(page.id) { nextId++ }, keeping nextId a Long so stable fragment ids are still generated sequentially. Do not touch createFragment, getItemId or containsItem.

---

## 63. `utils/SyncTimeLogger.kt`

**lead value:** 53 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** devin

### Drop the double processTimes lookup in SyncTimeLogger.endProcess

`#78`  **value** 53 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by devin (perf)_

- **anchor:** functions `endProcess` — grep `if (!processTimes.containsKey(startKey)) return`
- **files:** utils/SyncTimeLogger.kt (endProcess)
- **evidence:** endProcess checks `if (!processTimes.containsKey(startKey)) return` and then immediately reads `val startTime = processTimes[startKey] ?: return` - two lookups on a ConcurrentHashMap for every timed sync or upload process.
- **work:** Delete the containsKey guard and keep only the `?: return` read. Duration calculation and the processTimes/processItemCounts writes stay unchanged; missing start times are still silently ignored.

---

## 64. `model/MyCourse.kt`

**lead value:** 49 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm

### Use addAll and drop the set-to-list copy in MyCourse.saveConcatenatedLinksToPrefs

`#89`  **value** 49 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-glm (perf)_

- **anchor:** functions `saveConcatenatedLinksToPrefs` — grep `for (link in linksToProcess) { existingConcatenatedLinks.add(link) }`
- **files:** model/MyCourse.kt (saveConcatenatedLinksToPrefs)
- **evidence:** the function deserializes the stored links into a HashSet, then runs `for (link in linksToProcess) { existingConcatenatedLinks.add(link) }` - one add per element where addAll would do - and finally serializes `existingConcatenatedLinks.toList()`, a redundant set-to-list copy since Gson.toJson accepts a Collection.
- **work:** Replace the loop with addAll(linksToProcess) and serialize the set directly. The reader parses it back as Array<String> and calls toHashSet(), so order-independence is already relied upon.

---

## 65. `ui/sync/ServerDialogExtensions.kt`

**lead value:** 49 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex, devin

### Reorder the server list without the parallel pair collections

`#87`  **value** 49 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by codex (perf), devin (perf)_

- **anchor:** functions `refreshServerList`
- **files:** ui/sync/ServerDialogExtensions.kt (refreshServerList)
- **evidence:** refreshServerList maps every server to a Pair, searches that list for the pinned entry, then filters and maps it again and concatenates just to place the pinned server first, and the submitList callback searches the derived pair list a third time for the index.
- **work:** Find the pinned index directly in filteredList with the existing protocol-stripping regex; when pinning applies and a match exists, make one mutable copy and move that element to index 0, otherwise submit filteredList unchanged (preserving object identity and order). Reuse the computed match state in the submit callback instead of searching again.
- **note:** Small list, cold path - this is code clarity more than performance.

---

## 66. `ui/notifications/NotificationsAdapter.kt`

**lead value:** 47 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi

**coordinate with:** unit 26 (`ui/notifications/NotificationsViewModel.kt`) via `ui/notifications/NotificationsAdapter.kt`

### Inject the clock into NotificationsAdapter's relative-time formatting

`#93`  **value** 47 · **size** S · ~6-25 lines · **risk** low · **claim** `holds` · _proposed by copilot-kimi (perf)_

- **anchor:** functions `formatRelativeTime` — grep `val diff = System.currentTimeMillis() - createdAt`
- **files:** ui/notifications/NotificationsAdapter.kt (formatRelativeTime)
- **evidence:** `val diff = System.currentTimeMillis() - createdAt` inside formatRelativeTime, called per bind, with a hand-rolled bucket ladder and its own DateTimeFormatter cache. utils/TimeUtils already offers getRelativeTime(timestamp, timeProvider).
- **work:** Pass an injectable clock (a `now: () -> Long` from the hosting fragment, backed by TimeProvider) into the adapter and use it, keeping the existing thresholds and rendered strings identical. Add a Robolectric test for the minute/hour/day/fallback boundaries.
- **note:** Reuse TimeUtils.getRelativeTime only if the rendered strings match exactly - its DateUtils-based output differs from this ladder. Do not edit TimeUtils (open PR).

---

## 67. `ui/teams/TeamsSelectionAdapter.kt`

**lead value:** 47 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** openhands-glm

### Cache the resolved Teams label in TeamsSelectionAdapter.bind

`#92`  **value** 47 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by openhands-glm (perf)_

- **anchor:** grep `if (section == itemView.context.getString(R.string.teams))`
- **files:** ui/teams/TeamsSelectionAdapter.kt (TeamSelectionViewHolder.bind)
- **evidence:** bind evaluates `if (section == itemView.context.getString(R.string.teams))` on every bind to pick between the team and business icon, re-resolving a localized string per visible row for a constructor value that never changes.
- **work:** Resolve the comparison value once (an adapter-level lazy field) and compare against it in bind, keeping the icon selection identical.
- **note:** The submitted order's own steps waver between three approaches; pick the lazy field and move on.

---

## 68. `repository/TagsRepositoryImpl.kt`  —  **bundle of 2**

**lead value:** 45 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-kimi, jules

**steps, in order:** #97 -> #105

### step 1 — Deduplicate TagsRepositoryImpl's allTagIds in one pass

`#97`  **value** 45 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `links.mapNotNull { it.tagId }.distinct()`
- **files:** repository/TagsRepositoryImpl.kt
- **evidence:** `links.mapNotNull { it.tagId }.distinct()` - two list allocations to build a deduplicated id list.
- **work:** Use mapNotNullTo(LinkedHashSet()) and convert back to a list. No change to how tags are fetched or to tag-linkage logic.

### step 2 — Narrow the TagsRepository course-link API

`#105`  **value** 44 · **size** XS · ~1-5 lines · **risk** low · **claim** `corrected` · _proposed by copilot-kimi (boundaries)_

> **claim corrected.** Order deferred the call-site update, which would land a method whose only caller is its own test. Restated to include the one-line call site; without it, close the finding.

- **anchor:** grep `tagsRepository.getLinkIdsForTagNames("courses", tagNames).toSet()`
- **files:** repository/TagsRepository.kt, repository/TagsRepositoryImpl.kt
- **evidence:** CoursesRepositoryImpl line 292 calls `tagsRepository.getLinkIdsForTagNames("courses", tagNames).toSet()`, so the "courses" db-name literal lives in the courses repository rather than behind the tags contract.
- **work:** Add getCourseLinkIds(tagNames): Set<String> to TagsRepository, implemented as getLinkIdsForTagNames("courses", tagNames).toSet(), and update the CoursesRepositoryImpl call site in the same change.
- **decision made here:** RESOLVED: include the one-line CoursesRepositoryImpl call site in the same change. A method whose only caller is its own test is dead code; if the PR contention makes that unacceptable, close the finding instead of deferring it.
- **overlaps:** #97 — same file - one PR
- **note:** As submitted the order defers the call-site update (because CoursesRepositoryImpl is PR-contended), which would land a method with only a test as its caller. Either include the one-line call-site change or skip the task.

---

## 69. `ui/settings/StorageBreakdownFragment.kt`

**lead value:** 44 &nbsp;·&nbsp; **size:** S &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok

### Build the extension-to-category index once in the StorageBreakdown scan (reject the mtime cache)

`#94`  **value** 44 · **size** S · ~6-25 lines · **risk** low · **claim** `corrected` · _proposed by copilot-grok (perf)_

> **claim corrected.** Order's headline remedy was an in-memory cache keyed on the ole directory's lastModified. UNSOUND: a directory's mtime does not change when a nested file is written, so the cache would serve stale totals indefinitely. What is true: StorageCategories.indexOf plus an extension lowercase runs per file. Take that; reject the cache.

- **anchor:** functions `scanStorage`
- **files:** ui/settings/StorageBreakdownFragment.kt (scanStorage)
- **evidence:** opening the storage breakdown walks every file under the ole directory with walkTopDown(), aggregating sizes and counts per extension via StorageCategories.indexOf on each file.
- **work:** Build the extension-to-category index once from StorageCategories instead of calling indexOf per file, and avoid the repeated extension lowercase allocation. Keep the scan off the main thread (the existing launch already does this) and the category totals and file counts identical for a fixture tree.
- **risk detail:** reject the mtime cache; the per-file work reduction alone is safe
- **note:** CAUTION: the submitted order's headline idea - a cache keyed on the ole directory's lastModified - is unsound, since a directory's mtime does not change when nested files are written. Take the per-file work reduction; if you want a cache, key it on an explicit invalidation from the delete flow, not on mtime.

---

## 70. `utils/CrashLogStore.kt`

**lead value:** 44 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** codex

### Short-circuit the crash-log capacity check

`#100`  **value** 44 · **size** XS · ~1-5 lines · **risk** low · **claim** `holds` · _proposed by codex (perf)_

- **anchor:** functions `save` — grep `(logDir.listFiles()?.count { isValidLogFile(it) } ?: 0) >= MAX_PENDING_FILES`
- **files:** utils/CrashLogStore.kt (save)
- **evidence:** `(logDir.listFiles()?.count { isValidLogFile(it) } ?: 0) >= MAX_PENDING_FILES` parses the filename of every file in the pending-log directory even though it only needs to know whether 20 valid reports exist. Crash and ANR persistence is latency-sensitive and deliberately synchronous.
- **work:** Convert the listFiles result to a lazy sequence for the capacity check only, filter with the existing isValidLogFile predicate and stop as soon as MAX_PENDING_FILES valid entries are seen. Preserve the rule that malformed neighbours do not consume capacity, plus the exception handling and the create-or-return-null contract.

---

## 71. `repository/ResourcesRepositoryImpl.kt`

**lead value:** 43 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** jules

### Merge the filter and mapNotNull in ResourcesRepositoryImpl.downloadResources

`#101`  **value** 43 · **size** XS · ~1-5 lines · **risk** med · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `resources.filter { !it.isResourceOffline() }.mapNotNull { it.resourceRemoteAddress }`
- **files:** repository/ResourcesRepositoryImpl.kt
- **evidence:** `resources.filter { !it.isResourceOffline() }.mapNotNull { it.resourceRemoteAddress }` - two passes with an intermediate list before the URL list is built.
- **work:** Merge into a single mapNotNull returning null for offline resources. Offline resources must still be excluded and the same remote addresses extracted.
- **risk detail:** ResourcesRepositoryImpl is claimed by an open PR
- **note:** ResourcesRepositoryImpl is claimed by an open PR.

---

## 72. `utils/ImageUtils.kt`

**lead value:** 42 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** low &nbsp;·&nbsp; **from:** copilot-grok

### Give ImageUtils.loadPlaceholderImage the disk-cache strategy its siblings already have

`#98`  **value** 42 · **size** XS · ~1-5 lines · **risk** low · **claim** `corrected` · _proposed by copilot-grok (perf)_

> **claim corrected.** Order asserted these 'decode full-resolution bitmaps'. FALSE: Glide already sizes decodes to the measured target view, so .override() only matters for unmeasured/wrap_content targets. What is true: loadPlaceholderImage is missing the diskCacheStrategy its two siblings have. That is the whole finding.

- **anchor:** functions `loadImage`, `loadPlaceholderImage`
- **files:** utils/ImageUtils.kt (loadImage, loadPlaceholderImage)
- **evidence:** loadProfileImage applies .override(sizePx, sizePx) and diskCacheStrategy(ALL); loadImage has the disk-cache strategy but no override; loadPlaceholderImage has neither.
- **work:** Add an optional sizePx parameter (defaulted, so no call site outside this file needs editing) applying .override(size, size), and give loadPlaceholderImage the same diskCacheStrategy as its siblings. Preserve the placeholder/error/circleCrop behaviour.
- **note:** PREMISE OVERSTATED: the order claims these 'decode full-resolution bitmaps'. Glide already sizes decodes to the measured target view, so .override() only matters for unmeasured/wrap_content targets. The missing diskCacheStrategy on loadPlaceholderImage is the concrete win.

---

## 73. `ui/exam/ExamTakingFragment.kt`

**lead value:** 40 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** jules

### Use isNullOrEmpty instead of the size-Elvis idiom in ExamTakingFragment

`#107`  **value** 40 · **size** XS · ~1-5 lines · **risk** med · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `if ((questions?.size ?: 0) > 0)`
- **files:** ui/exam/ExamTakingFragment.kt
- **evidence:** `if ((questions?.size ?: 0) > 0)` - a nullable-list emptiness test written with an Elvis operator and an integer comparison.
- **work:** Replace with `if (!questions.isNullOrEmpty())`. No behaviour change.
- **risk detail:** claimed by open PR #16698
- **note:** ExamTakingFragment is claimed by open PR #16698 in several surveys; a 2-line change is not worth a conflict - fold it into whatever else touches this file.

---

## 74. `ui/courses/CoursesFragment.kt`

**lead value:** 38 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** jules

### Use isNullOrEmpty instead of the size-Elvis idiom in CoursesFragment

`#110`  **value** 38 · **size** XS · ~1-5 lines · **risk** med · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `if ((selectedItems?.size ?: 0) > 0)`
- **files:** ui/courses/CoursesFragment.kt, the onAddToLib lambda)
- **evidence:** `if ((selectedItems?.size ?: 0) > 0)` in the onAddToLib callback.
- **work:** Replace with `if (!selectedItems.isNullOrEmpty())`. No change to how courses are displayed or filtered.
- **risk detail:** claimed by open PRs #16701/#16647
- **note:** CoursesFragment is claimed by open PRs #16701/#16647 - same caveat as the ResourcesFragment twin.

---

## 75. `ui/resources/ResourcesFragment.kt`

**lead value:** 38 &nbsp;·&nbsp; **size:** XS &nbsp;·&nbsp; **risk:** med &nbsp;·&nbsp; **from:** jules

### Use isNullOrEmpty instead of the size-Elvis idiom in ResourcesFragment

`#109`  **value** 38 · **size** XS · ~1-5 lines · **risk** med · **claim** `holds` · _proposed by jules (perf)_

- **anchor:** grep `if ((selectedItems?.size ?: 0) > 0)`
- **files:** ui/resources/ResourcesFragment.kt, setupAddToLibListener)
- **evidence:** `if ((selectedItems?.size ?: 0) > 0)` in the add-to-library click listener.
- **work:** Replace with `if (!selectedItems.isNullOrEmpty())`. No change to the adapter's selection logic.
- **risk detail:** claimed by open PRs #16702/#16661
- **note:** ResourcesFragment is claimed by open PRs #16702/#16661/#16647 - almost certainly not worth landing standalone.

---

## Closed — verified but recommended against, deliberately unranked

- **#111 Sequence-chain the distinct/sort pipelines in LifeRepositoryImpl** _(devin)_
  No measurable benefit. sortedBy on a Sequence materializes a list anyway, so the conversion trades one intermediate list for sequence wrappers plus that same list - on a seven-item My Life list.

- **#112 Reuse the MyLife instances in LifeAdapter's reorder** _(copilot-kimi)_
  Premise true, remedy wrong. My Life holds seven items so the allocation is noise, and mutating instances already in a ListAdapter's current list defeats DiffUtil.areContentsTheSame - constructing new objects is the correct pattern here.

- **#108 Replace SyncRepositoryImpl's shelf when-block with a dispatch map** _(copilot-kimi)_
  Lateral. The order explicitly keeps all four repository injections, so the coupling it complains about survives; only the unknown-shelf-type test is durable. File that test against finding #21 instead.

- **#91 Use the already-known id in the notification delete branches** _(openhands-glm)_
  Fully superseded by #36, which rewrites both methods and subsumes this one-line change.

---

## Cross-unit file edges

| shared file | units |
|---|---|
| `repository/HealthRepositoryImpl.kt` | 31 (`repository/HealthRepositoryImpl.kt`), 43 (`repository/VoicesRepositoryImpl.kt`) |
| `repository/LifeRepositoryImpl.kt` | 37 (`repository/LifeRepositoryImpl.kt`), 30 (`ui/life/LifeViewModel.kt`) |
| `repository/NotificationsRepositoryImpl.kt` | 4 (`repository/NotificationsRepositoryImpl.kt`), 26 (`ui/notifications/NotificationsViewModel.kt`) |
| `repository/PersonalsRepositoryImpl.kt` | 17 (`data/room/dao/PersonalDao.kt`), 46 (`repository/PersonalsRepositoryImpl.kt`) |
| `repository/UploadRepositoryImpl.kt` | 46 (`repository/PersonalsRepositoryImpl.kt`), 47 (`repository/UploadRepositoryImpl.kt`) |
| `services/NotificationActionReceiver.kt` | 27 (`MainApplication.kt`), 38 (`services/NotificationActionReceiver.kt`) |
| `services/SharedPrefManager.kt` | 37 (`repository/LifeRepositoryImpl.kt`), 2 (`services/SharedPrefManager.kt`) |
| `services/upload/PhotoUploader.kt` | 34 (`repository/SubmissionsRepositoryImpl.kt`), 9 (`services/upload/PhotoUploader.kt`) |
| `ui/dashboard/DashboardPluginFragment.kt` | 1 (`ui/dashboard/DashboardPluginFragment.kt`), 30 (`ui/life/LifeViewModel.kt`) |
| `ui/health/MyHealthFragment.kt` | 19 (`ui/enterprises/EnterprisesReportsFragment.kt`), 25 (`ui/health/MyHealthFragment.kt`) |
| `ui/notifications/NotificationsAdapter.kt` | 66 (`ui/notifications/NotificationsAdapter.kt`), 26 (`ui/notifications/NotificationsViewModel.kt`) |

---

## Dropped — premise did not survive verification

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


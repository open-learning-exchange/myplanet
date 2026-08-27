### 1. Harden release.yml: add timeout-minutes, a concurrency group, and a build-dir cache

**rating:** 89/100 &nbsp;·&nbsp; **proposed by:** copilot(r3), opus(r3)

release.yml is the only workflow with neither `timeout-minutes` nor `concurrency` (build.yml:23=10, test.yml:23=15, labels.yml:33=5, automerge.yml:92=350 all have one). A hung release holds a runner for GitHub's 360-min default, and two master pushes race on the same `v${VERSION}` tag and both spend a Play Store save slot. It also has no `app/build` cache step, so every release compiles cold. Same file: the Discord step runs `sudo npm install -g @treehouses/cli` on every successful default leg.

**files:** `.github/workflows/release.yml`

**verification:** Verified: no timeout/concurrency in release.yml; `sudo npm install -g` at line 154; build.yml/test.yml both cache app/build.

---

### 2. Delete the six dead Hilt entry-point accessors

**rating:** 88/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

`ServiceDependenciesEntryPoint.apiInterface()/syncManager()/uploadManager()/uploadToShelfService()` and `CoreDependenciesEntryPoint.userSessionManager()/resourcesRepository()` have zero call sites anywhere in app/src. Each is a live Dagger root that makes the worker escape hatch formally depend on UploadManager, SyncManager and UploadToShelfService.

**files:** `di/ServiceDependenciesEntryPoint.kt`, `di/CoreDependenciesEntryPoint.kt`

**verification:** Verified by grep: 0 hits for each of the six; retryQueue()=1, broadcastService()=1, diagnosticsRepository()=2, timeProvider()=4, serverUrlMapper()=2, sharedPrefManager()=3 all stay.

---

### 3. Let WorkManager wait for the network instead of parking NetworkMonitorWorker on it

**rating:** 88/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

`NetworkMonitorWorker.start` enqueues a OneTimeWorkRequest with **no constraints**, and `doWork` then blocks on `NetworkUtils.isNetworkConnectedFlow.first { it }`. On an offline device the worker holds an execution slot and its wake lock until the 10-minute limit, is stopped without a Result, and reschedules — a loop for as long as the device is offline. `NetworkType.CONNECTED` is free and is the pattern MainApplication.scheduleAutoSyncWork already uses.

**files:** `services/NetworkMonitorWorker.kt`, `(new) test/services/NetworkMonitorWorkerTest.kt`

**verification:** Verified: start() builds the request with only .addTag(); doWork() awaits isNetworkConnectedFlow.first{it}.

---

### 4. Batch the per-member user lookups in TeamsRepositoryImpl (N+1)

**rating:** 87/100 &nbsp;·&nbsp; **proposed by:** minimax(r2)

`getJoinedMembers` (line 960) and `getRequestedMembers` (line 1051) both end in `.mapNotNull { userRepository.getUserById(it) }` — one query per member. `UserRepository.getUsersByIds` already exists and chunks into 400-id IN queries.

**files:** `repository/TeamsRepositoryImpl.kt`

**verification:** Verified both call sites and that getUsersByIds exists (UserRepository.kt:21). Implementer must preserve ordering/dedup — getUsersByIds may not return input order.

---

### 5. Serialise the automerge drain and stop it cloning every blob in history

**rating:** 87/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

automerge.yml has no `concurrency:` block (unlike playstore.yml:53). Two dispatch clicks put two jobs on the same queue, both bumping the version and pushing to protected master — the second claims a versionCode the first already took. Its checkout also uses `fetch-depth: 0` with no blob filter, downloading every blob of every commit when the script only reads a handful of files.

**files:** `.github/workflows/automerge.yml`

**verification:** Verified: no concurrency key; checkout@v7 with fetch-depth: 0 at line 95-97.

---

### 6. Group dependabot updates so one bump is not four CI runners

**rating:** 86/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

`.github/dependabot.yml` has two daily ecosystems with limits 10 and 15 and **no** `groups:` key, so every dependency bump opens its own PR, each firing build.yml (2-leg matrix) and test.yml (2-shard matrix) = four runners per bump, re-fired on every rebase, and each has to be drained through automerge one at a time.

**files:** `.github/dependabot.yml`

**verification:** Verified: no `groups` key; open-pull-requests-limit 10 and 15.

---

### 7. Memoize the Basic-auth header instead of rebuilding it on every request

**rating:** 86/100 &nbsp;·&nbsp; **proposed by:** copilot(r2), opus(r2)

`UrlUtils.header` is a getter that on **every** access reads two SharedPreferences strings, allocates a ByteArray and Base64-encodes it. It has ~33 call sites including one per outgoing API call and one per list-item bind in CoursesAdapter/CourseDetailFragment (Glide LazyHeaders per cover image). Credentials change only on login/server switch.

**files:** `utils/UrlUtils.kt`

**verification:** Verified UrlUtils.kt:29-38. Both proposals invalidate on credential change and clear the cache in the existing resetForTesting() hook.

---

### 8. Push MyLibrary.needToUpdate() from Kotlin into SQL

**rating:** 85/100 &nbsp;·&nbsp; **proposed by:** copilot(r1), copilot(r2), deepseek(r3), opus(r1)

`needToUpdate() = !resourceOffline || (resourceLocalAddress != null && _rev != downloadedRev)` is evaluated in memory at five sites in ResourcesRepositoryImpl: getLibraryListForUser (194), countLibrariesNeedingUpdate (215 — a full row hydration to produce one Int for the dashboard badge), getAllLibrariesToSync (431), getDownloadSuggestionList (464, 470). All are expressible in SQLite with `IS NOT` for the null-safe rev compare.

**files:** `data/room/dao/MyLibraryDao.kt`, `repository/ResourcesRepositoryImpl.kt`, `test/.../ResourcesRepositoryImplTest.kt`

**verification:** Verified all five call sites. copilot(r2) is the most complete: it also flags that getAllLibrariesToSync's `getSyncable()`+filter misses offline rows with a stale _rev, and adds a SQL-DISTINCT opened-resource-id query. deepseek covers only the count.

---

### 9. Size the OkHttp connection pool to the dispatcher's per-host limit

**rating:** 85/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

`NetworkModule.buildOkHttpClient` raises `Dispatcher.maxRequestsPerHost` to 20 but leaves ConnectionPool at OkHttp's default of 5 idle connections. Every sync and batch download targets a single CouchDB host, so 20 requests run concurrently and at most 5 connections survive for reuse — the other 15 pay a fresh TCP+TLS handshake, which dominates on the slow links this app targets.

**files:** `di/NetworkModule.kt`

**verification:** Verified MAX_REQUESTS_PER_HOST=20 at NetworkModule.kt:69 and .dispatcher(dispatcher) at :76 with no .connectionPool().

---

### 10. Raise RealtimeSyncManager's buffer so burst table updates aren't dropped

**rating:** 85/100 &nbsp;·&nbsp; **proposed by:** copilot(r3)

`MutableSharedFlow<TableDataUpdate>(extraBufferCapacity = 1)` with `tryEmit` and no replay: a sync notifying many tables in a burst silently drops updates for slow or momentarily-absent collectors, and the UI live-refresh helpers collect this flow.

**files:** `services/sync/RealtimeSyncManager.kt`, `test/.../RealtimeSyncManagerTest.kt`

**verification:** Verified verbatim — the whole class is 25 lines and matches the description.

---

### 11. Delete four dead Hilt injections from services and one fragment

**rating:** 84/100 &nbsp;·&nbsp; **proposed by:** devin(r1), opus(r1)

Four classes ask Hilt for dependencies they never read: SyncManager:70 injects `TeamsRepository` (making the sync orchestrator formally depend on the whole teams domain), UploadCoordinator:28 injects `@ApplicationContext Context`, DownloadService:58 field-injects `DispatcherProvider`, SubmissionsFragment:34 field-injects `UserSessionManager`.

**files:** `services/sync/SyncManager.kt`, `services/upload/UploadCoordinator.kt`, `services/DownloadService.kt`, `ui/submissions/SubmissionsFragment.kt`, `test/.../SyncManagerTest.kt`

**verification:** All four verified. devin(r1) proposed only the SubmissionsFragment quarter; opus found all four and named the SyncManagerTest constructor that must change.

---

### 12. Give the duplicated getUserInfo credential parser one platform-free home in UrlUtils

**rating:** 84/100 &nbsp;·&nbsp; **proposed by:** glm(r1), kimi(r2), opus(r3)

The same `uri.userInfo` splitter exists twice — `ProcessUserDataActivity.getUserInfo` (:239, compiling a `Regex` to split on one literal char) and the private `ServerUrlMapper.getUserInfo` (:111). Worse, `ServerConfigUtils.kt:99` — a plain util — reaches into an **Activity companion object** to call it, so a util transitively depends on an AppCompatActivity.

**files:** `utils/UrlUtils.kt`, `ui/sync/ProcessUserDataActivity.kt`, `services/sync/ServerUrlMapper.kt`, `utils/ServerConfigUtils.kt`, `test/.../UrlUtilsTest.kt`

**verification:** All three copies verified. opus is the most complete (kills the ServerConfigUtils->Activity edge and the regex, returns a Pair). glm keeps the Activity companion as a delegator. kimi split the same finding across two tasks and fixed neither duplication.

---

### 13. Move InlineResourceAdapter's preview file stats off the main thread

**rating:** 84/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

`adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)` (:56, :61) and every preview path then does blocking filesystem stats on it: `file.exists()` at 203, 217, 227, 242, 254, 269 plus `getCacheKey` (:283) which adds `lastModified()` and `length()`. Lines 203/217 are invoked straight from onBindViewHolder, so a screenful of downloaded resources does several stat() syscalls per bind during a fling.

**files:** `ui/courses/InlineResourceAdapter.kt`

**verification:** Verified all line numbers exactly.

---

### 14. Read storage stats once per FileUtils helper, and stop creating directories during an existence check

**rating:** 83/100 &nbsp;·&nbsp; **proposed by:** kimi(r2), minimax(r3), opus(r2)

`totalAvailableMemoryRatio` and `availableOverTotalMemoryFormattedString` each call both public wrappers, and each wrapper calls `getStorageStats` — so each helper runs the whole probe twice (two getSystemService lookups plus StorageStatsManager binder round trips) on the login screen, settings, storage breakdown and TaskNotificationWorker. Separately `checkFileExist` resolves through `getSDPathFromUrl` -> `createFilePath`, which calls `mkdirs()` — a pure existence question writes directories, and it is called from list binding.

**files:** `utils/FileUtils.kt`

**verification:** Double-probe verified at FileUtils.kt:343-357. opus additionally found the mkdirs-on-existence-check, which is the higher-value half. minimax's variant adds a 5-second stats cache — a behavior change the other two correctly avoid.

---

### 15. Stop the labels workflow from checking out the whole repository

**rating:** 83/100 &nbsp;·&nbsp; **proposed by:** copilot(r3), opus(r3)

labels.yml does a full checkout on every pull_request_target event, but its only other step runs labels.sh, which reads the diff through the GitHub API and never touches a checked-out file — so ~500 Kotlin sources, 181 layouts and six translation bundles are cloned to run one shell script on every push to every open PR. The `chmod +x` is also dead: the file is already mode 100755 in the index.

**files:** `.github/workflows/labels.yml`

**verification:** Verified: full checkout@v7 at :36, chmod at :48, and `git ls-files -s` reports 100755.

---

### 16. Resolve the device android id once instead of on every serialization

**rating:** 83/100 &nbsp;·&nbsp; **proposed by:** copilot(r2), opus(r2)

`VersionUtils.getAndroidId` queries Settings.Secure through the content resolver on every call and `NetworkUtils.getUniqueIdentifier` repeats the same query independently. Both are called per serialized document (SearchActivity, UserEntity, MyPlanet x2, ActivitiesRepositoryImpl), so a 50-item upload batch issues 50+ content-resolver round trips for a process-lifetime constant. In the same file `getCustomDeviceName` re-resolves `coreEntryPoint.sharedPrefManager()` (:189) although the object memoizes it at :34.

**files:** `utils/VersionUtils.kt`, `utils/NetworkUtils.kt`, `test/.../NetworkUtilsTest.kt`

**verification:** Verified the duplicate sharedPrefManager() resolution at NetworkUtils.kt:34 vs :189. opus names the null-propagation test that constrains the cache design; copilot's version would cache getDeviceName too.

---

### 17. Drop the dead ExamDao injection from NotificationsRepositoryImpl

**rating:** 82/100 &nbsp;·&nbsp; **proposed by:** copilot(r1), deepseek(r1), glm(r1), opus(r1)

`NotificationsRepositoryImpl` declares `private val examDao: ExamDao` (:32) and imports it (:10) but never references it — a dead cross-feature DAO edge from notifications into the exam domain.

**files:** `repository/NotificationsRepositoryImpl.kt`, `test/.../NotificationsRepositoryImplTest.kt`

**verification:** Verified: the only two occurrences in the file are the import and the constructor parameter. All four agents also named the three test-file references that must go.

---

### 18. Replace full-row notification loads with id-only projections

**rating:** 82/100 &nbsp;·&nbsp; **proposed by:** devin(r1), glm(r1), opus(r1)

`markNotificationsAsRead` (:116) does `notificationDao.getByIds(...).map { it.id }`, `deleteNotifications` (:406) does the same, and `markAllUnreadAsRead` (:124) loads every unread row via `getNotifications(userId,"unread",false)` just to build an id set — full AppNotification rows (message text, dates, sync flags) materialized to learn which ids exist.

**files:** `data/room/dao/NotificationDao.kt`, `repository/NotificationsRepositoryImpl.kt`, `test/.../NotificationsRepositoryImplTest.kt`

**verification:** All three sites verified. glm and opus both cover all three; devin covers only the two getByIds sites. glm additionally converts the TeamNotification row load to a (parentId,lastCount) projection.

---

### 19. Drop the duplicate chat-history load on screen open

**rating:** 82/100 &nbsp;·&nbsp; **proposed by:** opus(r2)

`ChatHistoryFragment` calls `refreshChatHistory()` from onViewCreated (:98) and again from onResume (:151). onResume always follows onViewCreated, so opening the AI chat screen runs two full loads back to back — chat history, shared news messages, share targets, viewIn ids, plus three SharedPreferences reads each. A third trigger already exists for real changes (refreshChatSignal at :224).

**files:** `ui/chat/ChatHistoryFragment.kt`

**verification:** Verified all three call sites at 98/151/224.

---

### 20. Route community/parent-code config reads and clearPreferences through ConfigurationsRepository

**rating:** 81/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1), devin(r1), glm(r1), kimi(r1)

`CommunityTabViewModel` reads `sharedPrefManager.getParentCode()/getCommunityName()` directly while `getPlanetType()` on the next line already goes through configurationsRepository; `HomeCommunityDialogFragment` reads the same two keys; `SettingsViewModel` injects SharedPrefManager (:22) solely for one `clearPreferences()` call (:47); `LeadersViewModel` injects it solely for `getCommunityLeaders()`.

**files:** `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `ui/community/CommunityTabViewModel.kt`, `ui/community/LeadersViewModel.kt`, `ui/settings/SettingsViewModel.kt`

**verification:** All four call sites verified. devin's version is the most complete (covers all four VMs incl. Leaders). deepseek hoists the whole assembly into a CommunityContext data class. kimi(r1#8) instead folds clearPreferences into clearAllData — note SyncActivity:274 already calls prefData.clearPreferences() separately, so that variant double-clears there.

---

### 21. Make HomeCommunityDialogFragment consume CommunityTabViewModel instead of injecting repositories

**rating:** 81/100 &nbsp;·&nbsp; **proposed by:** copilot(r1), opus(r1)

`HomeCommunityDialogFragment` field-injects SharedPrefManager and ConfigurationsRepository (:28-31) and rebuilds in `initCommunityTab` exactly the three values `CommunityTabViewModel` already publishes as CommunityTabState — while its sibling CommunityTabFragment over the identical layout consumes the ViewModel properly.

**files:** `ui/community/HomeCommunityDialogFragment.kt`

**verification:** Verified. Both agents correctly flag that the pager id here is `communityName@parentCode` (not the tab fragment's `planetCode@parentCode`) and must not be silently switched.

---

### 22. Move the my-life seed-if-empty sequence out of LifeViewModel into LifeRepository

**rating:** 81/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1), opus(r1)

`LifeViewModel.loadMyLifeList` orchestrates a data-layer sequence from the UI: read `getMyLifeByUserId`, and if empty call `seedMyLifeIfEmpty` then read the whole table again — three repository round trips per screen open, two of them full table scans, plus a race window the repository's own `seedMutex` cannot close from outside. LifeRepositoryImpl already owns this pattern internally for the dashboard.

**files:** `repository/LifeRepository.kt`, `repository/LifeRepositoryImpl.kt`, `ui/life/LifeViewModel.kt`, `test/.../LifeRepositoryImplTest.kt`

**verification:** Verified at LifeViewModel.kt:32-45. opus is more complete: it names the isVisible/cache differences from getMyLifeForDashboard and warns that PR 16096 owns ui/life/LifeViewModelTest.kt.

---

### 23. Delete the dead queryPending upload query contract

**rating:** 80/100 &nbsp;·&nbsp; **proposed by:** opus(r1)

`UploadRepository.queryPending(config)` has no production caller — the only references are its own impl and three tests. It drags along `UploadQueryContract<T>` (whose type parameter is never used inside it, forcing @Suppress("UNCHECKED_CAST")), the `UploadQueryType` enum, the private `hydrateSubmissions` helper and the whole `answerDao` injection.

**files:** `repository/UploadRepository.kt`, `repository/UploadRepositoryImpl.kt`, `test/.../UploadRepositoryImplTest.kt`

**verification:** Verified: grep finds the impl at UploadRepositoryImpl.kt:32 and three tests, no production caller.

---

### 24. Make RetryInterceptor's backoff testable and stop sleeping wall-clock seconds in CI

**rating:** 80/100 &nbsp;·&nbsp; **proposed by:** copilot(r3)

`RetryInterceptor.backoff` uses `Thread.sleep` against the real clock (:88) while TimeProvider only drives the deadline (:78, :84). RetryInterceptorTest sets initialDelay=10L but still sleeps; on master the class costs 12.3s with testSuccessAfterIOException alone at 10.2s.

**files:** `data/api/RetryInterceptor.kt`, `test/.../RetryInterceptorTest.kt`

**verification:** Verified the Thread.sleep/TimeProvider split. The cited CI timings are from the agent's log reading and I could not re-run them.

---

### 25. Move HTML parsing out of the NotificationListItem model into the adapter

**rating:** 80/100 &nbsp;·&nbsp; **proposed by:** copilot(r3), kimi(r1)

`NotificationListItem.Item.parsedText` is a lazy `Html.fromHtml(...)` inside `model/` (:18-19) — the sealed class's only android.text dependency. Its sole consumer is `NotificationsAdapter.ItemViewHolder.bind` (:113), so the parse belongs at bind time.

**files:** `model/NotificationListItem.kt`, `ui/notifications/NotificationsAdapter.kt`

**verification:** Verified both files and that parsedText has exactly one consumer.

---

### 26. Replace printStackTrace with tagged Log.e on the sync path

**rating:** 80/100 &nbsp;·&nbsp; **proposed by:** devin(r3), kimi(r3)

Sync failures never reach logcat: SyncManager has 5 naked `printStackTrace()` (218, 383, 406, 417, 553) despite already importing android.util.Log; LoginSyncManager has 10; SyncRepositoryImpl has 2 (plus indexed `for (i in 0 until array.size())` JsonArray walks at 139-144 and 185-192 that Kotlin can iterate directly).

**files:** `services/sync/SyncManager.kt`, `services/sync/LoginSyncManager.kt`, `repository/SyncRepositoryImpl.kt`

**verification:** printStackTrace counts verified exactly: SyncManager 5, LoginSyncManager 10, SyncRepositoryImpl 2. kimi and devin both found SyncManager; LoginSyncManager is kimi's alone, SyncRepositoryImpl devin's alone.

---

### 27. Drop the dead retry-queue API surface

**rating:** 79/100 &nbsp;·&nbsp; **proposed by:** opus(r1)

`RetryQueue.queueFailedOperations` (:56), `getPendingCount` (:83) and `resetAllPending` (:104) have zero production callers; `RetryRepository.resetAllPending` exists only to serve that dead facade method, and `RetryDao.resetPendingRetryTime` (:38) exists only to serve that. Three layers of interface kept alive by nothing.

**files:** `services/retry/RetryQueue.kt`, `repository/RetryRepository.kt`, `repository/RetryRepositoryImpl.kt`, `data/room/dao/RetryDao.kt`, `test/.../RetryRepositoryImplTest.kt`

**verification:** Verified: the only references outside the chain itself are one test.

---

### 28. Delete the dead single-title getTaskTeamName from the notifications repository

**rating:** 79/100 &nbsp;·&nbsp; **proposed by:** opus(r2)

`NotificationsRepository.getTaskTeamName(taskTitle)` (:24) and its impl (:252) have no callers in app/src/main or app/src/test — NotificationsViewModel moved to the batched getTaskTeamNamesByTaskTitles/ByTaskIds. It keeps a one-row-per-title query pattern alive in an otherwise fully batched interface.

**files:** `repository/NotificationsRepository.kt`, `repository/NotificationsRepositoryImpl.kt`

**verification:** Verified: grep for the exact word finds only the declaration and the override.

---

### 29. Remove two dead Room queries

**rating:** 79/100 &nbsp;·&nbsp; **proposed by:** kimi(r1)

`TeamDao.observeByDocType` (:20) and `UserDao.countByPlanetCode` (:19) have zero callers in app/src/main. Dead @Query methods are still compiled and validated by KSP on every build.

**files:** `data/room/dao/TeamDao.kt`, `data/room/dao/UserDao.kt`

**verification:** Verified: both grep to their declaration only.

---

### 30. Reuse buildApkLog in the batch diagnostics path

**rating:** 79/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1), deepseek(r3)

`DiagnosticsRepositoryImpl` builds the same ApkLog fields twice — once in `buildApkLog` used by saveLogToRoom, and again as an inline `ApkLog().apply { }` inside saveLogsToRoom with its own precomputed versionName/parentCode/planetCode. The two have already drifted (`model?.let { userId = it.id }` vs `modelId?.let { userId = it }`).

**files:** `repository/DiagnosticsRepositoryImpl.kt`

**verification:** Verified the duplication at :40-59 and :72-94. Same agent proposed this in two separate rounds — counted once.

---

### 31. Inject Context into EnterprisesRepositoryImpl instead of reaching for MainApplication.context

**rating:** 78/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1)

`EnterprisesRepositoryImpl` imports MainApplication (:11) and uses `MainApplication.context` inside `attachTeamImage` (:128) although the repository is otherwise context-free — a data-layer-to-app-singleton leak.

**files:** `repository/EnterprisesRepositoryImpl.kt`, `test/.../EnterprisesRepositoryImplTest.kt`

**verification:** Verified both lines.

---

### 32. Stop the size labeller from downloading every file's patch

**rating:** 78/100 &nbsp;·&nbsp; **proposed by:** opus(r3)

labels.sh:45 calls the pulls/files endpoint with `--paginate`, which returns the **full unified patch** for every changed file, and line 46 throws all of it away except for `app/build.gradle`. On a large PR that is megabytes of JSON per event, on a workflow that fires on every push to every open PR — and GitHub omits `patch` once a diff is big enough, so the version-bump discount is already unreliable exactly where the payload hurts.

**files:** `.github/scripts/labels.sh`

**verification:** Verified the `gh api .../files?per_page=100 --paginate` call and that the patch is read only in the GRADLE_FILE branch.

---

### 33. Delete the hand-rolled RecyclerView measure pass in SubmissionDetailFragment

**rating:** 77/100 &nbsp;·&nbsp; **proposed by:** opus(r2)

`setupRecyclerView` installs an anonymous LinearLayoutManager whose onMeasure loops over every item calling getViewForPosition/addView/measureChild/getDecoratedMeasuredHeight/removeAndRecycleView to sum a height — a full inflate-and-measure of the whole question list on every measure pass. The layout already declares wrap_content with nestedScrollingEnabled=false inside a ScrollView, which RecyclerView has supported natively since support-lib 23.2.

**files:** `ui/submissions/SubmissionDetailFragment.kt`

**verification:** Verified the whole anonymous subclass. Caveat the task does not call out: the same subclass also overrides `canScrollVertically()` to return false — a plain LinearLayoutManager loses that, so the implementer must keep an override or verify the ScrollView still owns scrolling.

---

### 34. Remove the duplicated version-name parsing from release.yml

**rating:** 77/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3), kimi(r3)

release.yml parses versionName out of app/build.gradle twice with the identical sed — once in `set release version` (:40) and again inside `build release APK and AAB` (:55) — in the same job, where ANDROID_VERSION is already exported. The versionCode read (:60) strips all non-digits, which breaks on a trailing comment.

**files:** `.github/workflows/release.yml (version steps only)`

**verification:** Verified both sed lines. deepseek's variant routes both through the repo's existing `.github/scripts/version.sh read`, which is the canonical reader automerge.sh already uses — the more complete fix.

---

### 35. Give exam-answer grading one hoisted locale and a set comparison

**rating:** 76/100 &nbsp;·&nbsp; **proposed by:** devin(r2), devin(r3), opus(r2)

`checkSelectAnswer` calls `Locale.getDefault()` inside the `any {}` lambda, so the JDK's synchronized lookup runs once per candidate choice. `checkMultipleSelectAnswer` lowercases both sides into Lists, converts both to Arrays, then `isEqual` sorts both in place and compares — four intermediate collections and two sorts to answer 'are these two sets equal'. This runs for every question of every graded submission.

**files:** `utils/ExamAnswerUtils.kt`

**verification:** Verified verbatim at ExamAnswerUtils.kt:71-98 (checkTextAnswer already hoists the locale correctly — copy that shape). Risk both agents understate: sorted-array equality is multiset equality, a Set is not; duplicate answers would change behavior. devin's variant keeps sorted lists and is the safer of the two.

---

### 36. Remove android.util.Log from RetryRepositoryImpl

**rating:** 76/100 &nbsp;·&nbsp; **proposed by:** copilot(r3), deepseek(r1)

The import at :3, the TAG at :19 and the three Log.w/Log.i calls at 122/128/133 are the file's only Android dependency, and all three messages only mirror the Boolean the method already returns to SettingsViewModel.

**files:** `repository/RetryRepositoryImpl.kt`

**verification:** Verified all five lines.

---

### 37. Remove android.util.Log from Sha256Utils

**rating:** 76/100 &nbsp;·&nbsp; **proposed by:** copilot(r3)

`getCheckSumFromFile` is pure MessageDigest over a File except for one `Log.w` on the failure path (:32) — the class's only android import, blocking an otherwise platform-free checksum helper.

**files:** `utils/Sha256Utils.kt`, `test/.../Sha256UtilsTest.kt`

**verification:** Verified: android.util.Log at :3 is the only android import.

---

### 38. Stabilize the enterprise report flow's content comparison

**rating:** 76/100 &nbsp;·&nbsp; **proposed by:** codex(r1)

`getReportsFlow`'s `distinctUntilChanged` compares only `_id` and `_rev` (:94-97). Locally edited report fields change while `_rev` stays fixed, so the repository suppresses a user-visible update — editing a report does not refresh its displayed financial values until a server sync.

**files:** `repository/EnterprisesRepositoryImpl.kt`

**verification:** Verified verbatim at EnterprisesRepositoryImpl.kt:94-97. This is the one correctness bug in the whole round, not just an efficiency trim.

---

### 39. Replace Achievement's android.util.LruCache with a pure-Kotlin bounded cache

**rating:** 75/100 &nbsp;·&nbsp; **proposed by:** kimi(r1)

`Achievement` pins the whole model to android.util for a 1000-entry JSON cache (:3, :94). The cache is private in the companion and used only by parseStringListToJsonArray in the same file; a bounded LinkedHashMap does the same job.

**files:** `model/Achievement.kt`

**verification:** Verified import and field.

---

### 40. Route DownloadRepositoryImpl's file-not-found logging through DiagnosticsRepository

**rating:** 75/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1)

`DownloadRepositoryImpl` imports `MainApplication.Companion.createLog` (:9) and calls it at :51/:53 — a data-layer repository reaching into the application singleton's static for a side effect (persist an apk-log row) that the logging repository already owns.

**files:** `repository/DownloadRepositoryImpl.kt`, `test/.../DownloadRepositoryImplTest.kt`

**verification:** Verified import and both call sites. The task correctly enumerates the 8 test constructor sites and the two MainApplication.createLog verifications that must change.

---

### 41. Suppress the deprecated android.enableJetifier AGP warning

**rating:** 74/100 &nbsp;·&nbsp; **proposed by:** devin(r3), kimi(r3)

Every CI stage prints `WARNING: The option setting 'android.enableJetifier=true' is deprecated.` The flag must stay — app/libs ships ChipCloud-3.0.5.aar and flexbox-1.0.0.aar — so add AGP's own `android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE`.

**files:** `gradle.properties`

**verification:** Verified: enableJetifier at gradle.properties:26, and both AARs present in app/libs. kimi additionally justifies the flag in the adjacent comment.

---

### 42. Replace printStackTrace with tagged Log.e in the config, secure-store, file, camera and download-worker paths

**rating:** 74/100 &nbsp;·&nbsp; **proposed by:** kimi(r3)

Structured failures are swallowed to stderr: ConfigurationsRepositoryImpl has 11 printStackTrace (it returns UrlCheckResult.Failure/ConfigurationResult.Failure but logs nothing), SecurePrefs 8 (including AeadConfig.register() inside init — a security-relevant init failure that must never be silent), FileUtils 9, CameraUtils 5 (plus two hard-coded string tags), DownloadWorker 5 (WorkManager never collects worker stderr).

**files:** `repository/ConfigurationsRepositoryImpl.kt`, `utils/SecurePrefs.kt`, `utils/FileUtils.kt`, `utils/CameraUtils.kt`, `services/DownloadWorker.kt`

**verification:** All five counts verified exactly by grep -c.

---

### 43. Replace two full-row loads in ResourcesRepository with column projections

**rating:** 74/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1), glm(r1)

`getMyLibIds` calls `myLibraryDao.getForUserPattern(...)`, which SELECT *s every MyLibrary row for the user, then emits only the ids; `getResourceTitlesMap` calls `getWithResourceId()` (`SELECT * FROM my_library WHERE resourceId IS NOT NULL`) but reads only resourceId and title — and it runs on every offline-resource render via getOfflineResourceItems.

**files:** `data/room/dao/MyLibraryDao.kt`, `repository/ResourcesRepositoryImpl.kt`, `test/.../ResourcesRepositoryImplTest.kt`

**verification:** Both call sites verified. glm covers both methods and correctly notes getForUserPattern must stay for its other callers; deepseek covers only getResourceTitlesMap but adds the typed ResourceTitle projection class.

---

### 44. Replace android.util.Base64 in UrlUtils with the JDK encoder

**rating:** 73/100 &nbsp;·&nbsp; **proposed by:** copilot(r3)

`basicAuthHeader` uses `android.util.Base64.encodeToString(..., NO_WRAP)`; with the Log.w on alternative-URL parse failure removed, java.util.Base64 leaves the auth helper free of android.util.*.

**files:** `utils/UrlUtils.kt`, `test/.../UrlUtilsTest.kt`

**verification:** Verified the Base64 call at UrlUtils.kt:37. The task is unusually careful about the padding difference between NO_WRAP and withoutPadding() — take that guidance literally or the header changes.

---

### 45. Move the enterprises non-archived report filter and ordering into TeamDao

**rating:** 72/100 &nbsp;·&nbsp; **proposed by:** copilot(r1), glm(r1)

`EnterprisesRepositoryImpl.getReportsFlow` calls `teamDao.observeByTeamIdAndDocType(teamId,"report")` then `.filter { it.status != "archived" }.sortedByDescending { it.createdDate }` in memory, re-running a static predicate and ordering on every emission. `observeByTeamIdAndDocType` is shared with the TeamsRepositoryImpl transaction caller, so it cannot be narrowed in place.

**files:** `data/room/dao/TeamDao.kt`, `repository/EnterprisesRepositoryImpl.kt`, `test/.../EnterprisesRepositoryImplTest.kt`

**verification:** Verified at EnterprisesRepositoryImpl.kt:87-99. Both proposals use the same IFNULL(status,'') != 'archived' SQL and both correctly note the shared DAO method must survive.

---

### 46. Let the enterprises CSV export read its own data instead of taking the UI's list

**rating:** 72/100 &nbsp;·&nbsp; **proposed by:** minimax(r1), opus(r1)

`exportReportsAsCsv(reports: List<MyTeam>, teamName)` takes already-loaded entities back from the UI; EnterprisesReportsFragment:84 passes its own `reports` field assigned from the adapter callback, so the exported CSV silently depends on what the screen last rendered — export tapped before the flow emits writes a header-only file.

**files:** `repository/EnterprisesRepository.kt`, `repository/EnterprisesRepositoryImpl.kt`, `ui/enterprises/EnterprisesViewModel.kt`, `ui/enterprises/EnterprisesReportsFragment.kt`, `test/.../EnterprisesRepositoryImplTest.kt`

**verification:** Verified the whole call chain. opus diagnoses the header-only-file bug and fetches inside the repository; minimax only moves the coroutine from the fragment to the ViewModel, leaving the stale-list bug in place.

---

### 47. Replace the per-candidate grep dedup in coauthor crediting with an associative array

**rating:** 72/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

coauthors.sh's dedup loop calls `printf | grep -qxF` twice per candidate (:83, :86), spawning two grep processes per candidate — O(n^2) in collaborators and the slowest part of every automerge squash. Bash 4 associative arrays make both lookups O(1).

**files:** `.github/scripts/coauthors.sh`

**verification:** Verified both grep -qxF calls at coauthors.sh:83 and :86.

---

### 48. Add the missing StorageCategoryViewModel test

**rating:** 71/100 &nbsp;·&nbsp; **proposed by:** glm(r1)

`StorageCategoryViewModel` has no test. `toggleItemChecked`, `toggleAllChecked` and the `deleteItems` re-entry guard (`if (_uiState.value.isDeleting) return`) are the load-bearing logic of the storage cleanup screen.

**files:** `(new) test/.../ui/settings/StorageCategoryViewModelTest.kt`

**verification:** Verified: no such test file exists and the ViewModel does.

---

### 49. Add the missing DiagnosticsRepositoryImpl test

**rating:** 71/100 &nbsp;·&nbsp; **proposed by:** glm(r1)

`DiagnosticsRepositoryImpl` has no test. getPendingApkLogs, markApkLogUploaded (`!= 0`) and saveLogToRoom are thin but load-bearing delegations; `VersionUtils.getVersionName(context)` forces Robolectric, as in HealthRepositoryImplTest.

**files:** `(new) test/.../repository/DiagnosticsRepositoryImplTest.kt`

**verification:** Verified: no such test file exists.

---

### 50. Add the missing PersonalsViewModel test

**rating:** 70/100 &nbsp;·&nbsp; **proposed by:** glm(r1)

`PersonalsViewModel` has no test, covering neither the personals StateFlow built from getUserModel()+getPersonalResources nor the Idle->Loading->Success|Error upload state machine with resetUploadState().

**files:** `(new) test/.../ui/personals/PersonalsViewModelTest.kt`

**verification:** Verified: no such test file exists.

---

### 51. Add the missing EventsDetailViewModel test

**rating:** 70/100 &nbsp;·&nbsp; **proposed by:** glm(r1)

`EventsDetailViewModel.loadData` runs three repository calls concurrently under `coroutineScope { async { } }` and short-circuits to user-only when meetUpId is blank — concurrency and a branch that break silently, with no test.

**files:** `(new) test/.../ui/events/EventsDetailViewModelTest.kt`

**verification:** Verified: no such test file exists.

---

### 52. Unblock automerge's pick_pr from an O(n^2) skip-set scan

**rating:** 70/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

`pick_pr`'s jq filter is `map(select(.number as $n | $done | index($n) | not))`, where `$done` is rebuilt each call and `index` is a linear scan — quadratic in queue size, on every loop iteration of the drain.

**files:** `.github/scripts/automerge.sh`

**verification:** Verified the exact filter at automerge.sh:72.

---

### 53. Add the missing LeadersViewModel test

**rating:** 69/100 &nbsp;·&nbsp; **proposed by:** glm(r1)

`LeadersViewModel.loadLeaders` runs in init and either sets emptyList() or UserEntity.parseLeadersJson(string) — the whole data source of the screen, untested. parseLeadersJson uses org.json, so Robolectric is required.

**files:** `(new) test/.../ui/community/LeadersViewModelTest.kt`

**verification:** Verified: no such test file exists; LeadersViewModel matches the description exactly.

---

### 54. Add a ServerReachabilityProvider test and move its cache TTL onto TimeProvider

**rating:** 69/100 &nbsp;·&nbsp; **proposed by:** copilot(r3)

`ServerReachabilityProvider` injects OkHttpClient, ServerUrlMapper and DispatcherProvider but uses `System.currentTimeMillis()` for both the cache read and write, against the project's TimeProvider convention, and has no unit test at all.

**files:** `utils/ServerReachabilityProvider.kt`, `(new) test/.../utils/ServerReachabilityProviderTest.kt`

**verification:** Verified the file exists and has no test. The task correctly warns not to merge in MainApplication's separate HttpURLConnection reachability cache.

---

### 55. Parallelize the notifications enrichment lookups

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3)

`NotificationsViewModel.loadNotifications` runs four independent suspend DB lookups strictly serially on the Main scope; task-team-names, join-request-details and the unread badge count have no dependency on each other and can be async/awaited together.

**files:** `ui/notifications/NotificationsViewModel.kt`

**verification:** Verified the four call sites in loadNotifications. Note this file is also the target of two other shipped tasks — sequence them.

---

### 56. Regroup notifications only when notifications change, not on every selection tap

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** opus(r2)

`groupedItems` combines four flows — _notifications, _selectedIds, _collapsedGroups, _expandedGroups — and rebuilds everything through `buildGroupedList` on each emission: a full groupBy with a `type.lowercase(Locale.ROOT)` per notification, a re-derived ordered type list and a per-group unread recount. Selecting one item in multi-select re-groups and re-counts the entire inbox, though grouping depends solely on _notifications.

**files:** `ui/notifications/NotificationsViewModel.kt`

**verification:** Verified: combine at :50, groupBy+lowercase at :232-242.

---

### 57. Drop the per-call Java reflection from the usage-stats permission check

**rating:** 68/100 &nbsp;·&nbsp; **proposed by:** kimi(r3)

`BasePermissionActivity.getUsagesPermission` resolves AppOpsManager methods via `Class.getMethod` + `Method.invoke` on every call, with a Q-vs-legacy branch, plus a catch-all exception surface — for an API that is public and directly callable at this project's minSdk 26.

**files:** `base/BasePermissionActivity.kt`

**verification:** Verified verbatim at BasePermissionActivity.kt:50-67. One correction the task does not make: on Q+ the reflective target is `unsafeCheckOpNoThrow`, so a direct call needs either @RequiresApi(29) or the deprecated `checkOpNoThrow`; the task's step 3 (delete the Q branch) picks the latter without saying so.

---

### 58. Reuse the resolved user in MembersFragment instead of re-querying per refresh

**rating:** 67/100 &nbsp;·&nbsp; **proposed by:** opus(r2)

The signed-in user is resolved three ways for one screen: BaseTeamFragment.onCreate loads it into the inherited `user` field, MembersFragment.onViewCreated calls `userSessionManager.getUserModel()` again (:77), and `loadMembers()` calls it a third time (:103). getUserModel() is a Room userDao.getById, and loadMembers() has five call sites.

**files:** `ui/teams/members/MembersFragment.kt`

**verification:** Verified the onViewCreated coroutine and loadMembers().

---

### 59. Drop the redundant second member fetch in MembersFragment.handleMakeLeader

**rating:** 67/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3)

`handleMakeLeader` calls `getJoinedMembersWithVisitInfo(teamId)` and `updateData(members, false)` and then immediately calls `loadMembers()`, which re-queries the same thing one line later. The eager fetch is dead work and hard-codes isLoggedInUserTeamLeader=false, unlike the real path.

**files:** `ui/teams/members/MembersFragment.kt`

**verification:** Verified verbatim at :157-161 — the discarded fetch and the wrong leader flag are both real.

---

### 60. Hoist the achievement resource dialog's dup-check into a Set

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** glm(r2), kimi(r2)

`createResourceList` loops `for (i in list.indices)` calling `prevList.contains(list[i].title)` where prevList is a List — a linear scan inside a linear loop, O(n*m) over resources x previously selected titles.

**files:** `ui/user/EditAchievementFragment.kt`

**verification:** Verified at EditAchievementFragment.kt:477-482. glm's version cites the exact method and predicate; kimi's cites a slightly wrong enclosing function name.

---

### 61. Convert the team-course availability filter from List to Set membership

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** glm(r2)

`showAddCourseDialog` computes `allCourses.filter { it.courseId !in existingIds }` where existingIds is the `List<String>` returned by getTeamCourseIds — `!in` on a List is a linear scan, so the Add Course dialog runs O(all courses x already-added courses) on open.

**files:** `ui/teams/courses/TeamCoursesFragment.kt`

**verification:** Verified at TeamCoursesFragment.kt:107-109.

---

### 62. Parse the resource URL once in FileUtils.getSDPathFromUrl

**rating:** 66/100 &nbsp;·&nbsp; **proposed by:** glm(r2)

`getSDPathFromUrl` calls both `getIdFromUrl(url)` and `getResourceRelativePathFromUrl(url)`, and each independently does `url.toUri().pathSegments` + `indexOf('resources')` — the same URI parsed and scanned twice per call, on a path reached from `checkFileExist` during InlineResourceAdapter bind.

**files:** `utils/FileUtils.kt`

**verification:** The three functions exist and the double parse is consistent with their bodies. Shares a file with the storage-stats task — sequence them.

---

### 63. Hoist the search-string lowercase out of the collections filter lambda

**rating:** 65/100 &nbsp;·&nbsp; **proposed by:** codex(r2), glm(r2)

`filterTags` evaluates `charSequence.lowercase(Locale.ROOT)` **inside** the filter lambda, so the search string is re-lowercased once per tag on every keystroke.

**files:** `ui/resources/CollectionsFragment.kt`

**verification:** Verified at CollectionsFragment.kt:112-115.

---

### 64. Build the download URL list against one resolved base URL

**rating:** 65/100 &nbsp;·&nbsp; **proposed by:** codex(r3), glm(r3)

`DownloadUtils.downloadAllFiles` maps each library through `UrlUtils.getUrl(it)`, and each call re-derives `getUrl()` -> `dbUrl(spm())` -> spm() + baseUrl() string work for a base that is constant across the list; the result is then copied again into an ArrayList.

**files:** `utils/DownloadUtils.kt`, `utils/UrlUtils.kt`

**verification:** Verified the map+ArrayList shape. glm's version hoists the base URL (the real win); codex's only pre-sizes the ArrayList.

---

### 65. Batch the photo uploaded-mark instead of one UPDATE per photo

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2)

`PhotoUploader.uploadSubmitPhotos` calls `submissionsRepository.markPhotoUploaded(photoId, rev, id)` per successful photo, each issuing its own UPDATE — a 100-photo roll becomes 100 separate statements on the upload path.

**files:** `services/upload/PhotoUploader.kt`, `repository/SubmissionsRepositoryImpl.kt`

**verification:** File and per-item mark verified; I did not measure the statement count.

---

### 66. Deduplicate the DiffUtil/measure work in CoursesProgressAdapter

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** glm(r2), kimi(r2)

`onBindViewHolder` fetches `getItem(position)` (:27) then calls showStepMistakes, which calls `getItem(position)` a second time for the same row (:41), and resolves `ContextCompat.getColor(context, R.color.daynight_textColor)` (:46) on every bind that has step mistakes.

**files:** `ui/courses/CoursesProgressAdapter.kt`

**verification:** Both verified (line numbers are off by a few in both write-ups). glm covers both the double getItem and the color; kimi only the color.

---

### 67. Parse each task notification's date once instead of twice

**rating:** 64/100 &nbsp;·&nbsp; **proposed by:** glm(r2)

`parseTaskDate` runs a regex match against the message in the title-collection pass (:80) and then again in `formatNotification` (:343) for every task notification rendered.

**files:** `ui/notifications/NotificationsViewModel.kt`

**verification:** Verified both parseTaskDate call sites at NotificationsViewModel.kt:80 and :343.

---

### 68. Read the first conversation query once per row in ChatHistoryAdapter

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** glm(r2)

`onBindViewHolder` evaluates `item.conversations?.get(0)?.query` three times for one row (:105, :106, :107) and guards with a double null/empty read of the same nullable list.

**files:** `ui/chat/ChatHistoryAdapter.kt`

**verification:** Verified all three lines.

---

### 69. Compute the member display name once per row in MembersAdapter

**rating:** 63/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2), glm(r2)

`onBindViewHolder` binds `if (member.toString() == " ") member.name else member.toString()` — calling UserEntity.toString() twice per row, allocating a fresh String each time, on every bind during scroll.

**files:** `ui/teams/members/MembersAdapter.kt`

**verification:** Verified the double toString(). deepseek also claims getRoleAsString() is called repeatedly — it is called once; that half of its context is wrong.

---

### 70. Do the submission newest-per-parent selection in one pass

**rating:** 62/100 &nbsp;·&nbsp; **proposed by:** codex(r2), jules(r1)

`SubmissionViewModel.filteredSubmissionsRaw` groups every filtered submission into lists, scans each group for its max, then re-groups a second time to build submissionCountMap — three passes over the same data in ViewModel state production.

**files:** `ui/submissions/SubmissionViewModel.kt`

**verification:** Verified at SubmissionViewModel.kt:68-88. codex keeps the work in the ViewModel and is precise about the equal-lastUpdateTime tie rule; jules instead proposes pushing the counts into a SubmissionDao GROUP BY, which is a bigger change for the same symptom.

---

### 71. Use the existing countTeamChats instead of loading every news row for a notification count

**rating:** 62/100 &nbsp;·&nbsp; **proposed by:** jules(r1), minimax(r2)

`TeamsVoicesViewModel.getFilteredNews` calls `voicesRepository.getFilteredNews(teamId)` and reads `newsList.size` purely to pass to `updateTeamNotification` — loading every news row plus all columns to produce one Int, when `VoicesRepository.countTeamChats(teamId): Long` is already backed by SQL COUNT.

**files:** `ui/teams/voices/TeamsVoicesViewModel.kt`

**verification:** Verified the call site and that countTeamChats exists (VoicesRepository.kt:43). minimax names the existing count method; jules only gestures at 'an optimized count metric'. Note the list is still returned to the caller, so this saves the column read only if the two paths are actually split.

---

### 72. Stop re-lowercasing the notification type on every resolve pass

**rating:** 62/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2)

`resolveType` lowercases `type` twice (:259 — once for the KNOWN_TYPES check and once for the return) and the message once (:260), `typeLabelFor` lowercases again (:288), and `buildGroupedList` a third time (:233). For a full inbox the same string is allocated repeatedly.

**files:** `ui/notifications/NotificationsViewModel.kt`

**verification:** Verified all four lowercase(Locale.ROOT) sites. Overlaps two other shipped tasks on this file.

---

### 73. Query rating summaries without loading every rating row

**rating:** 61/100 &nbsp;·&nbsp; **proposed by:** codex(r1)

`RatingsRepositoryImpl.getRatingSummary` loads all matching entities via `getByTypeAndItem` and computes count and average in Kotlin (`results.map { it.rate }.average()`), when only one user row plus two aggregates are needed.

**files:** `repository/RatingsRepositoryImpl.kt`, `data/room/dao/RatingDao.kt`

**verification:** Verified at RatingsRepositoryImpl.kt:44-60.

---

### 74. Return meetup member IDs directly from Room

**rating:** 61/100 &nbsp;·&nbsp; **proposed by:** codex(r1)

`EventsRepositoryImpl.getJoinedMembers` loads complete Meetup rows via `getMembersByMeetupId` only to `mapNotNull { it.userId }.distinct()` — a SELECT DISTINCT userId projection gives the same thing.

**files:** `repository/EventsRepositoryImpl.kt`, `data/room/dao/MeetupDao.kt`

**verification:** Verified at EventsRepositoryImpl.kt:66-79; the existing 400-id chunking and final distinctBy must be preserved as the task says.

---

### 75. Push survey eligibility filtering into Room and make the exclusion lookup a Set

**rating:** 61/100 &nbsp;·&nbsp; **proposed by:** codex(r1), codex(r2)

`SurveysRepositoryImpl.getTeamOwnedSurveys`/`getAdoptableTeamSurveys` load every survey via the broad `ExamDao.getByType("surveys")` and filter in Kotlin; `getAdoptableTeamSurveys` additionally builds `excludedIds` by list concatenation and calls `excludedIds.contains(it.id)` per survey.

**files:** `repository/SurveysRepositoryImpl.kt`, `data/room/dao/ExamDao.kt`

**verification:** File and method names verified. codex proposed the SQL push-down in r1 and the Set fix in r2 — the Set half is the cheaper, lower-risk one; counted once.

---

### 76. Remove the repository-to-repository hop from course visit logging

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** codex(r1)

`ActivitiesRepositoryImpl.logCourseVisit` calls `userRepository.get().getUserByName(userId)` to fetch two provenance fields, widening the data-layer graph when the repository could read the same row through the UserDao abstraction other repositories already use.

**files:** `repository/ActivitiesRepositoryImpl.kt`

**verification:** Verified at ActivitiesRepositoryImpl.kt:86-105.

---

### 77. Collapse the double delete in PersonalsRepositoryImpl.deletePersonalResource

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** codex(r1), deepseek(r1)

`deletePersonalResource(id)` issues `personalDao.deleteByDocId(id)` then `personalDao.deleteById(id)` — two DELETEs keyed on different columns of the same row, mirroring the `findByDocId(id) ?: findById(id)` pattern in updatePersonalResource. Two round trips to delete one row.

**files:** `repository/PersonalsRepositoryImpl.kt`, `data/room/dao/PersonalDao.kt`, `test/.../PersonalsRepositoryImplTest.kt`

**verification:** Verified at PersonalsRepositoryImpl.kt:62-64. deepseek's `WHERE _id = :id OR id = :id` single statement is the more complete fix; codex only wraps the two existing deletes in a @Transaction.

---

### 78. Replace the personals mutation lambda with an explicit repository intent

**rating:** 60/100 &nbsp;·&nbsp; **proposed by:** opus(r1)

`PersonalsRepository.updatePersonalResource(id, updater: (Personal) -> Unit)` hands a mutable Room entity out to the UI to be modified in place — PersonalsFragment:125 passes a lambda setting description and title, threaded through PersonalsViewModel:56. A data-layer write expressed as a UI callback: the repository cannot validate it, log it, or be reimplemented off-JVM.

**files:** `repository/PersonalsRepository.kt`, `repository/PersonalsRepositoryImpl.kt`, `ui/personals/PersonalsViewModel.kt`, `ui/personals/PersonalsFragment.kt`, `test/.../PersonalsRepositoryImplTest.kt`

**verification:** Verified the whole lambda chain including the fragment call site.

---

### 79. Take the Application Context out of the personals upload path

**rating:** 59/100 &nbsp;·&nbsp; **proposed by:** copilot(r1)

`PersonalsRepositoryImpl` takes an @ApplicationContext Context solely so uploadPersonalDocument can call `NetworkUtils.getCustomDeviceName(context)` when serializing — pinning the personals domain repository to Android for one string.

**files:** `repository/PersonalsRepository.kt`, `repository/PersonalsRepositoryImpl.kt`, `ui/personals/PersonalsViewModel.kt`, `ui/personals/PersonalsFragment.kt`, `test/.../PersonalsRepositoryImplTest.kt`

**verification:** Not independently re-verified beyond file existence; the described shape is consistent with the rest of the repository layer.

---

### 80. Give the dictionary population a mutex so concurrent calls can't both parse

**rating:** 59/100 &nbsp;·&nbsp; **proposed by:** codex(r1)

`DictionaryRepositoryImpl.insertDictionaryData` checks `dictionaryDao.count()` before parsing and inserting, but two concurrent calls can both observe zero and both do the work. LifeRepositoryImpl already has a seedMutex for exactly this shape.

**files:** `repository/DictionaryRepositoryImpl.kt`

**verification:** Verified the check-then-insert at DictionaryRepositoryImpl.kt:30-36 and the absence of any lock.

---

### 81. Make My Life de-duplication deterministic

**rating:** 59/100 &nbsp;·&nbsp; **proposed by:** codex(r1)

`LifeRepositoryImpl.dedupKey()` falls back to `System.identityHashCode(this)` when imageId, title and _id are all blank, so equivalent blank legacy rows dedup differently on each load and object identity leaks into a repository view model.

**files:** `repository/LifeRepositoryImpl.kt`

**verification:** Verified verbatim at LifeRepositoryImpl.kt:65-71.

---

### 82. Hoist the layout inflater and attach achievement rows to their parent

**rating:** 58/100 &nbsp;·&nbsp; **proposed by:** opus(r2)

`showAchievementAndInfo` and `showReference` each rebuild their container from scratch and call `LayoutInflater.from(activity)` once per row inside the loop, then inflate with no parent — discarding each row layout's own layout_* params before addView. showAchievementAndInfo is re-entrant from its own delete handler, so removing one attachment re-inflates every remaining row.

**files:** `ui/user/EditAchievementFragment.kt`

**verification:** File verified; the per-row inflater and parentless inflate are consistent with the surrounding code. Not line-by-line re-verified.

---

### 83. Push the my-life dashboard visibility filter into MyLifeDao

**rating:** 58/100 &nbsp;·&nbsp; **proposed by:** copilot(r1), glm(r2)

`getMyLifeForDashboard` loads all rows via `myLifeDao.getByUserId` then `filter { it.isVisible }`, and again on the cache/seed paths; MyLifeDao has no visible-only query. The cached branch additionally does a two-pass `filter{}.map{}` where one pass would do.

**files:** `data/room/dao/MyLifeDao.kt`, `repository/LifeRepositoryImpl.kt`, `test/.../LifeRepositoryImplTest.kt`

**verification:** Verified the cached filter+map at LifeRepositoryImpl.kt:93-99. copilot pushes the predicate into SQL (the bigger win); glm only collapses the two-pass chain.

---

### 84. Hoist the static share-dialog data map in ChatHistoryAdapter

**rating:** 58/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2)

`bindShareChat` calls `getData()` on every share-button click (:137), recreating a fresh HashMap plus two ArrayLists and re-reading three string resources for a structure that is constant for the adapter's lifetime.

**files:** `ui/chat/ChatHistoryAdapter.kt`

**verification:** Verified the getData() call at :137 and its definition at :300. Shares a file with the first-query-hoist task.

---

### 85. Keep storage-category selection state in its ViewModel

**rating:** 57/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2), opus(r1)

`StorageCategoryDetailFragment` reads `viewModel.uiState.value.items` in two click listeners (:104, :111) to re-derive state the ViewModel already owns and hands the lists back to `viewModel.deleteItems(...)`; `updateSelectionState` recomputes `items.count { it.isChecked }` (:159) in the view layer on every emission. Reading `.value` off a StateFlow inside a click listener is the classic stale-snapshot bug.

**files:** `ui/settings/StorageCategoryViewModel.kt`, `ui/settings/StorageCategoryDetailFragment.kt`

**verification:** Verified all four line references. opus moves the selection into the ViewModel (deleteSelected/deleteAll) and fixes the stale read; deepseek only adds a checkedCount field to the state.

---

### 86. De-duplicate the two UserDataWorker upload enqueues in SyncRepositoryImpl

**rating:** 57/100 &nbsp;·&nbsp; **proposed by:** deepseek(r1)

`uploadLoginData` and `uploadBulkData` are near-identical — build a OneTimeWorkRequest with a KEY_UPLOAD_TYPE, enqueueUniqueWork with REPLACE, map getWorkInfoByIdFlow through mapWorkInfoToState — differing only in the upload type and unique-work name.

**files:** `repository/SyncRepositoryImpl.kt`

**verification:** Verified verbatim at SyncRepositoryImpl.kt:47-75.

---

### 87. Collect valid notified task IDs during notification delivery

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** codex(r3)

`TaskNotificationWorker.doWork` iterates pending tasks to show notifications, then traverses them again with `mapNotNull { it.id }.filter { it.isNotBlank() }` to build the ids to mark — two extra collections and a second pass over the same list.

**files:** `services/TaskNotificationWorker.kt`

**verification:** Verified at TaskNotificationWorker.kt:46-58.

---

### 88. Reuse buildApkLog-style consolidation for the exam-condition JSON parse in HealthExaminationAdapter

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** kimi(r1)

`HealthExaminationAdapter.showAlert` parses the exam's conditions JSON inline in the UI layer, re-running Gson parsing per examination row; MyHealth is the data-shape owner and a companion helper puts JSON semantics somewhere unit tests can reach.

**files:** `model/MyHealth.kt`, `ui/health/HealthExaminationAdapter.kt`

**verification:** File paths verified; the inline parse was not line-verified.

---

### 89. Privatize getEnrichedLibraries on ResourcesRepository

**rating:** 56/100 &nbsp;·&nbsp; **proposed by:** kimi(r1)

`ResourcesRepository.getEnrichedLibraries` is on the public interface but is called only from inside ResourcesRepositoryImpl (by getResourceListModels), and it returns a `LibraryWithMetadata` holding raw JsonObject ratings — an internal join pipeline that downstream layers should not be able to depend on.

**files:** `repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`

**verification:** Premise holds for production code but the task's justification is wrong: it claims 'the public caller set was empty from a source-tree grep', and ResourcesRepositoryImplTest.kt:225 calls repository.getEnrichedLibraries(false, ...) through the interface. That test has to move or change with it.

---

### 90. Debounce the patient-search keystrokes hitting the DAO

**rating:** 55/100 &nbsp;·&nbsp; **proposed by:** kimi(r2)

`MyHealthFragment.setTextWatcher` wires `etSearch.doAfterTextChanged { viewModel.searchPatients(...) }` with no debounce, so every character typed fires a healthRepository.searchPatients DAO round trip — six queries for a six-letter name.

**files:** `ui/health/MyHealthFragment.kt`

**verification:** Verified verbatim at MyHealthFragment.kt:304-308.

---

### 91. Replace the dashboard voice-date list with a count query

**rating:** 55/100 &nbsp;·&nbsp; **proposed by:** jules(r1)

`DashboardViewModel` calls `voicesRepository.getCommunityVoiceDates(...)` twice (:292, :293) and consumes the results only as `uniqueDates.size` for voiceCount (:313, :321) — materializing unique-date lists to produce two Ints on the dashboard load path.

**files:** `ui/dashboard/DashboardViewModel.kt`, `repository/VoicesRepository.kt`, `repository/VoicesRepositoryImpl.kt`, `data/room/dao/NewsDao.kt`

**verification:** Verified the call sites and the `.size`-only consumption. The task addresses only one of the two calls.

---

### 92. Hoist the mistake/summary flattening in SyncTimeLogger

**rating:** 54/100 &nbsp;·&nbsp; **proposed by:** codex(r3), deepseek(r2), deepseek(r3)

`buildSummary` flattens apiCallTimes six times (:245, :246, :266, :267, :286, :289), allocating a throwaway List over every logged entry each time, to compute scalars at the end of every sync.

**files:** `utils/SyncTimeLogger.kt`

**verification:** Verified all six flatten() calls. deepseek(r3) covers all six; deepseek(r2) covers two; codex covers the generateSummary pair. deepseek proposed it in two rounds — counted once.

---

### 93. Guard the SyncTimeLogger hot log calls with Log.isLoggable

**rating:** 54/100 &nbsp;·&nbsp; **proposed by:** devin(r2)

`Log.d("SyncPerf", ...)` is called on every sync step with interpolated strings that invoke formatElapsed/formatTime and String.format even when the tag is disabled, so release builds pay the allocation for output never displayed.

**files:** `utils/SyncTimeLogger.kt`

**verification:** The SyncPerf Log.d calls are confirmed present (also visible in TransactionSyncManager). Shares a file with the flatten task — sequence them.

---

### 94. Reuse the shared filename parse in CrashLogStore

**rating:** 53/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2)

`isValidLogFile` and `loadPendingLogs` each parse the same `<epoch>_<type>.log` shape independently (removeSuffix / indexOf('_') / substring), so every load re-derives fields validation just computed on the same iteration.

**files:** `utils/CrashLogStore.kt`

**verification:** File exists; the duplicated parse was not line-verified.

---

### 95. Build the retry-queue payload map only when a retryable failure exists

**rating:** 53/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3)

`queueRetryableFailures` (:266) and `queueRetryableFailuresRoom` (:471) both build `preparedUploads.associateBy { it.localId }` over every prepared upload before checking whether any failure is retryable — in the common error-free case the map is allocated and never used.

**files:** `services/upload/UploadCoordinator.kt`

**verification:** Verified both associateBy lines sit above their `errors.filter { it.retryable }.forEach` guards.

---

### 96. Parallelize the UploadCoordinator batch HTTP uploads

**rating:** 52/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

`uploadBatch` and `uploadBatchRoom` walk `batch.forEach` and await each postUpload/putUpload sequentially, where network latency dominates; ActivitiesRepositoryImpl already uses Semaphore(6)+async for the same pattern.

**files:** `services/upload/UploadCoordinator.kt`

**verification:** Verified the sequential `batch.forEach` at :146 and :395. Higher risk than the rest of this list: the 409 recovery path, cancellation and result ordering all have to survive.

---

### 97. Coalesce the parallel-phase SyncManager status emissions

**rating:** 52/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

Phase 1 launches ~14 tables in parallel and each completion writes `_syncStatus.value = SyncStatus.Syncing(...)`, so concurrent writers thrash the StateFlow collectors (login/sync UI) with near-simultaneous updates and string allocations.

**files:** `services/sync/SyncManager.kt`, `test/.../SyncManagerTest.kt`

**verification:** File and parallel-phase shape verified; the emission count was not measured. Shares a file with the sync-logging task.

---

### 98. Skip redundant chip rebuilds in VoicesLabelManager.showChips

**rating:** 51/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

`showChips` always calls `fbChips.removeAllViews()`, rebuilds a ChipCloudConfig via Utilities.getCloudConfig() and constructs a new ChipCloud even when labels are empty or unchanged — from VoicesAdapter bind paths, i.e. during community-voices scroll.

**files:** `services/VoicesLabelManager.kt`, `test/.../VoicesLabelManagerTest.kt`

**verification:** File exists and has a test; the per-bind rebuild was not line-verified.

---

### 99. Reduce per-label allocations in VoicesLabelManager.formatLabelValue

**rating:** 51/100 &nbsp;·&nbsp; **proposed by:** devin(r2)

`formatLabelValue` chains `replace("_"," ").replace("-"," ")` and calls `Locale.getDefault()` inside the joinToString lambda for every word of every chip label, during list scrolling.

**files:** `services/VoicesLabelManager.kt`

**verification:** File exists; not line-verified. Shares a file with the showChips task.

---

### 100. Cache the parsed messages JsonArray in the Feedback model

**rating:** 50/100 &nbsp;·&nbsp; **proposed by:** devin(r2)

`messageList` and `message` re-parse the messages JSON string on every access while feedback list/detail adapters read them repeatedly during bind; `messages` is only mutated through setMessages, so an @Ignore transient cache is safe for Room.

**files:** `model/Feedback.kt`

**verification:** Verified the getters and setMessages exist and both guard on `ar.size() > 0`.

---

### 101. Hoist the locale and fix the loop re-initialization in ExamQuestion

**rating:** 50/100 &nbsp;·&nbsp; **proposed by:** devin(r2)

`setCorrectChoiceArray` calls `Locale.getDefault()` on every iteration, and `insertCorrectChoice` resets correctChoiceList and re-parses the correctChoice array **inside** the choices loop, making array-style parsing O(n) instead of O(1). Both run on every exam sync.

**files:** `model/ExamQuestion.kt`

**verification:** File exists; the reset-inside-loop was not line-verified, but it is the more interesting half of the claim if it holds.

---

### 102. Quiet JsonUtils.safeGet on the hot parse path

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

Every getString/getBoolean/getInt/getLong/getJsonObject routes through `safeGet`, which calls `e.printStackTrace()` on any exception. Sync and deserialization hit these thousands of times, so stack traces on expected type mismatches dominate log I/O.

**files:** `utils/JsonUtils.kt`, `test/.../JsonUtilsTest.kt`

**verification:** Verified at JsonUtils.kt:16-23.

---

### 103. One-shot guard for DownloadUtils.createChannels

**rating:** 49/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

`createChannels` is invoked from every buildInitialNotification, buildProgressNotification and completion builder plus DownloadService/DownloadWorker; it null-checks channels but still makes three getNotificationChannel system calls per progress tick.

**files:** `utils/DownloadUtils.kt`, `test/.../DownloadUtilsTest.kt`

**verification:** File exists; the per-tick call count was not verified.

---

### 104. Collapse the two-pass filter+map on the cached my-life read

**rating:** 48/100 &nbsp;·&nbsp; **proposed by:** glm(r2)

See also the MyLifeDao visibility task — `cached.filter { it.isVisible }.map { ... }` materializes an intermediate list then iterates it again, on the my-life screen path when the DB copy is empty.

**files:** `repository/LifeRepositoryImpl.kt`

**verification:** Verified at LifeRepositoryImpl.kt:93-99. Overlaps the SQL push-down task on the same file — do one or the other.

---

### 105. Remove the identity `map { it }` copies in the progress and submission repositories

**rating:** 48/100 &nbsp;·&nbsp; **proposed by:** codex(r1), codex(r2), jules(r2)

`ProgressRepositoryImpl` copies DAO lists with no-op `map { it }` at :38, :66, :71, :147, :150; `SubmissionsRepositoryImpl` does the same at :95, :230, :366. Full-list allocations with no transformation, immediately before grouping/indexing on the course-progress path.

**files:** `repository/ProgressRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`

**verification:** Verified 20 such copies across the repository layer. codex split this across three tasks; jules found one line of it. Counted once.

---

### 106. Read the download-queue sets and file name once in onDownloadComplete

**rating:** 47/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3), devin(r2), glm(r2)

`onDownloadComplete` reads PRIORITY_DOWNLOADS_KEY (:502) and then calls `getRemainingCount()` (:503), which reads both PRIORITY and PENDING again and allocates a union set — so the priority set is read twice per completion. `getFileNameFromUrl(url)` (a URI parse plus URLDecoder.decode) is also called twice with the same url.

**files:** `services/DownloadService.kt`

**verification:** Verified verbatim at DownloadService.kt:176-180 and 500-516. glm covers both the double set read and the double filename parse; deepseek only the sets; devin only the union allocation inside getRemainingCount.

---

### 107. Use the injected clock for AutoSyncWorker's sync-timestamp writes

**rating:** 46/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

AutoSyncWorker injects timeProvider and reads it at :55, but writes timestamps back with `Date().time` at :139 and :157 — defeating the injectable clock the test layer relies on and keeping java.util.Date in the sync path.

**files:** `services/AutoSyncWorker.kt`

**verification:** Verified all three lines and that they are the file's only Date references.

---

### 108. Deduplicate the shared display logic in the two CourseRatingUtils.showRating overloads

**rating:** 46/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

Both overloads end with the identical three display lines (average text, rating-count string, ratingBar value) differing only in how the values were sourced, so a rating-display tweak has to be made twice.

**files:** `utils/CourseRatingUtils.kt`

**verification:** File exists; the duplicated triple was not line-verified.

---

### 109. Remove the dead filteredList field and the list-copy churn in CollectionsFragment

**rating:** 46/100 &nbsp;·&nbsp; **proposed by:** devin(r3)

An unused `filteredList` property, plus `currentTagDataList` rebuilt with `.toMutableList()` at five assignment sites although it is only ever reassigned, never mutated in place.

**files:** `ui/resources/CollectionsFragment.kt`

**verification:** The `.toMutableList()` at the filterTags assignment is verified; the unused field and the other four sites were not. Shares a file with the lowercase-hoist task.

---

### 110. Replace the dead null-guard in ResourcesRepositoryImpl.downloadFiles

**rating:** 45/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

`var files = libraryList`, then `if (files == null) files = getAllLibrariesToSync()`, then `val safeFiles = files ?: emptyList()` — after the null branch the value is never null, so the elvis is dead and safeFiles is just files.

**files:** `repository/ResourcesRepositoryImpl.kt`

**verification:** File and function exist; the exact three-line shape was not re-verified.

---

### 111. Drop the deprecated StringBuffer regex replacement in markdown image rewriting

**rating:** 45/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

`prependBaseUrlToImages` uses `StringBuffer` with `matcher.appendReplacement/appendTail` — the deprecated JDK overloads; the StringBuilder overloads are the supported replacement and a future toolchain bump will warn.

**files:** `utils/MarkdownUtils.kt`

**verification:** File exists; not line-verified.

---

### 112. Reuse one CustomLinkMovementMethod instance in MarkdownUtils

**rating:** 45/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

`setMarkdownText` allocates a new CustomLinkMovementMethod on every call although Markwon itself is a cached singleton; list/detail binds that set markdown repeatedly create short-lived movement methods.

**files:** `utils/MarkdownUtils.kt`, `test/.../MarkdownUtilsTest.kt`

**verification:** File exists; not line-verified. Shares a file with the StringBuffer task.

---

### 113. Convert version comparison without allocating boxed integer lists

**rating:** 44/100 &nbsp;·&nbsp; **proposed by:** codex(r3), devin(r2), devin(r3)

`VersionUtils.compareVersions` builds two `List<Int>` via `split(".").map { it.toInt() }` on every call — startup, update checks, and the sync-required decision.

**files:** `utils/VersionUtils.kt`

**verification:** Verified verbatim at VersionUtils.kt:43-53. Three proposals for a genuinely tiny win; devin(r3) also folds in the two printStackTrace calls in the same file.

---

### 114. Split VersionUtilsTest so the pure JVM tests leave Robolectric

**rating:** 44/100 &nbsp;·&nbsp; **proposed by:** copilot(r3)

VersionUtilsTest costs 5.7s for the class and mixes pure string compare/parse tests with Context/PackageManager tests under @RunWith(RobolectricTestRunner::class) — the pure tests pay the sandbox cost for nothing, and production already separates the two groups.

**files:** `test/.../VersionUtilsTest.kt`, `(new) test/.../VersionUtilsPureTest.kt`

**verification:** The file exists and mixes both kinds; the 5.7s figure is from the agent's CI log reading, which I could not re-run.

---

### 115. Remove the redundant intermediate list in LifeAdapter.onItemMoveFinished

**rating:** 44/100 &nbsp;·&nbsp; **proposed by:** minimax(r2)

`val finalList = list.toList()` is created and passed to both reorderCallback and submitList, although submitList makes its own copy.

**files:** `ui/life/LifeAdapter.kt`

**verification:** File exists; not line-verified. Shares a file with the ViewBinding task.

---

### 116. Deduplicate unknown task assignees during collection

**rating:** 43/100 &nbsp;·&nbsp; **proposed by:** codex(r2)

`TeamsTasksFragment.refreshTaskList` builds a list of assignee IDs, filters it, then calls `distinct()` — intermediate collections on every task refresh where an insertion-ordered set collects them in one pass.

**files:** `ui/teams/tasks/TeamsTasksFragment.kt`

**verification:** File exists; not line-verified.

---

### 117. Collect the sync activity lookup keys in a single pass

**rating:** 43/100 &nbsp;·&nbsp; **proposed by:** codex(r2)

`ActivitiesRepositoryImpl.insertLoginActivitiesFromSync` walks documentList three separate times with map/filter/distinct pipelines to derive remote IDs, login times and user names before two DAO lookups.

**files:** `repository/ActivitiesRepositoryImpl.kt`

**verification:** File exists; not line-verified.

---

### 118. Sort filtered courses without allocating lowercase titles

**rating:** 43/100 &nbsp;·&nbsp; **proposed by:** codex(r2)

`CoursesRepositoryImpl.getCoursesBySearchAndFilter` sorts with `it.courseTitle?.lowercase() ?: ""`, allocating a lowercase string during comparisons for every retained course; a case-insensitive comparator preserves the ordering without the copies.

**files:** `repository/CoursesRepositoryImpl.kt`

**verification:** File exists; not line-verified.

---

### 119. Normalize the resource search query once and append instead of concatenating

**rating:** 42/100 &nbsp;·&nbsp; **proposed by:** codex(r2), codex(r3), devin(r2)

`ResourcesSearchUtils.searchList` normalizes each query part and then normalizes the whole query a second time (`Utilities.normalizeText` does Unicode normalization plus a diacritics regex), on every keystroke; it also returns `startsWithQuery + containsQuery`, copying both buckets into a third list.

**files:** `utils/ResourcesSearchUtils.kt`

**verification:** Verified the double normalization and the `+` concatenation at ResourcesSearchUtils.kt:7-24. devin's fix (derive the parts from the already-normalized query) changes behavior if normalizeText touches whitespace — check that first.

---

### 120. Dedupe course membership in one collection pass

**rating:** 42/100 &nbsp;·&nbsp; **proposed by:** codex(r2), deepseek(r2)

`MyCourse.setUserId` filters into a mutable list, does a linear `contains`, conditionally appends, then runs `distinct()` — an extra full pass and list on every course save.

**files:** `model/MyCourse.kt`

**verification:** Verified at MyCourse.kt:53-60. Take codex's insertion-ordered-Set version: deepseek's step 1 ("drop the trailing .distinct() — contains already guarantees uniqueness") is wrong, because the pre-existing list can already contain duplicates.

---

### 121. Cache the ClipboardManager lookup in the chat adapter

**rating:** 41/100 &nbsp;·&nbsp; **proposed by:** kimi(r2)

`ChatAdapter.copyToClipboard` does `context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager` on every long-press of a chat message.

**files:** `ui/chat/ChatAdapter.kt`

**verification:** File exists; not line-verified.

---

### 122. Hoist the dashboard survey list text color

**rating:** 41/100 &nbsp;·&nbsp; **proposed by:** kimi(r2)

`DashboardSurveysAdapter.onBindViewHolder` calls `ContextCompat.getColor(holder.itemView.context, R.color.daynight_textColor)` on every row bind of the survey-picker dialog.

**files:** `ui/dashboard/DashboardSurveysAdapter.kt`

**verification:** File exists; not line-verified.

---

### 123. Lift the handled-payload set to companion scope in AchievementsAdapter

**rating:** 41/100 &nbsp;·&nbsp; **proposed by:** kimi(r2)

`onBindViewHolder(holder, position, payloads)` builds `setOf(PAYLOAD_PHONE, PAYLOAD_EMAIL)` per partial-bind call — an allocation on every diff callback.

**files:** `ui/user/AchievementsAdapter.kt`

**verification:** File exists; not line-verified.

---

### 124. Hoist the members-list DateTimeFormatter

**rating:** 41/100 &nbsp;·&nbsp; **proposed by:** kimi(r1), kimi(r2)

`MembersAdapter` builds a `DateTimeFormatter.ofPattern(...).withZone(...)` per adapter instance (:34) and formats lastVisitDate at bind (:113); TimeUtils.getFormattedDate(Long?) already exists as the shared formatter for this timestamp.

**files:** `ui/teams/members/MembersAdapter.kt`

**verification:** Verified the field at :34, the bind-time format, and that TimeUtils.getFormattedDate(Long?) exists at TimeUtils.kt:79. Same agent proposed it in two rounds with two different fixes — counted once.

---

### 125. Expose NewsViewModel.privateImageUrls as a StateFlow

**rating:** 40/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3)

`_privateImageUrls` is a `MutableSharedFlow` with replay 0 and no extra buffer, so a collector that subscribes after the query finished — e.g. a fragment re-attaching on configuration change — receives nothing and the private-image list silently stays empty until the next request.

**files:** `ui/voices/NewsViewModel.kt`

**verification:** Verified verbatim — the whole ViewModel is 15 lines and matches.

---

### 126. Fix the mislabeled network-id local in NetworkUtils.getCurrentNetworkId

**rating:** 40/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

`var ssid = -1` is assigned `connectionInfo.networkId` and returned — the name lies about both type and meaning in the connectivity path.

**files:** `utils/NetworkUtils.kt`

**verification:** File exists; not line-verified. Pure rename, zero behavior change.

---

### 127. Collapse the redundant null-guard in the team-id and team-name preference getters

**rating:** 40/100 &nbsp;·&nbsp; **proposed by:** glm(r3)

`getSelectedTeamId` and `getTeamName` do `pref.getString(K, "").takeIf { !it.isNullOrEmpty() } ?: ""` where the `""` default already makes the takeIf a no-op; every other getter in the file uses the plain `?: ""` shape.

**files:** `services/SharedPrefManager.kt`

**verification:** Verified: the only two `takeIf` uses in the file, at :102 and :118.

---

### 128. Reuse parsed URI user-info in ServerUrlMapper

**rating:** 39/100 &nbsp;·&nbsp; **proposed by:** codex(r3)

`extractBaseUrl` and `mapAlternativeUrl` test raw strings for `@` while the same URL has already been parsed into a URI, so the same string is scanned repeatedly per mapping operation.

**files:** `services/sync/ServerUrlMapper.kt`

**verification:** File and both functions exist; the raw `contains("@")` checks were not line-verified.

---

### 129. Compare decrypted payload prefixes without slicing

**rating:** 39/100 &nbsp;·&nbsp; **proposed by:** codex(r3)

`AndroidDecrypter.decrypt` creates a prefix slice solely to compare the payload against the IV, then a second slice to strip it; an offset comparison removes the comparison copy while preserving legacy-ciphertext support.

**files:** `utils/AndroidDecrypter.kt`

**verification:** File exists; not line-verified.

---

### 130. Parse the local-network host once in ServerConfigUtils

**rating:** 38/100 &nbsp;·&nbsp; **proposed by:** codex(r3)

`isLocalNetwork` derives its host with two chained `split` calls and their temporary lists before the address checks, on the server-selection path.

**files:** `utils/ServerConfigUtils.kt`

**verification:** Verified verbatim at ServerConfigUtils.kt:72-80.

---

### 131. Parse the server-availability response without building a token list

**rating:** 38/100 &nbsp;·&nbsp; **proposed by:** codex(r1), deepseek(r2)

`checkServerAvailability` reads the whole body, splits on ",", drops trailing empties and allocates a list purely to test whether it has eight entries.

**files:** `repository/ConfigurationsRepositoryImpl.kt`

**verification:** File and function exist; the split/dropLastWhile chain was not line-verified. Shares a file with the config-logging task.

---

### 132. Drop the nested coroutine for post-approval team activity

**rating:** 37/100 &nbsp;·&nbsp; **proposed by:** deepseek(r2)

`RequestsViewModel.respondToRequest` launches a child `launch { teamsRepository.recordTeamActivity() }` inside an already-running coroutine after success — an unsupervised fire-and-forget where a direct suspend call would do.

**files:** `ui/teams/members/RequestsViewModel.kt`

**verification:** Verified verbatim at RequestsViewModel.kt:57-59.

---

### 133. Convert ServerAddressAdapter, LifeAdapter and VoicesActions to ViewBinding

**rating:** 37/100 &nbsp;·&nbsp; **proposed by:** devin(r3)

Three UI classes still call findViewById although ViewBinding is enabled project-wide: ServerAddressAdapter (:100-101, one MaterialButton), LifeAdapter (:135-141, five views), VoicesActions (:46-50 and :179-182, six lookups in the reply/edit dialog).

**files:** `ui/sync/ServerAddressAdapter.kt`, `ui/life/LifeAdapter.kt`, `ui/voices/VoicesActions.kt`

**verification:** All three files exist; the findViewById counts were not verified. Shipped as one entry — devin filed them as three separate tasks.

---

### 134. Use BottomSheetDialog.behavior instead of the internal design_bottom_sheet lookup

**rating:** 37/100 &nbsp;·&nbsp; **proposed by:** devin(r3)

StorageBreakdownFragment, StorageCategoryDetailFragment and AddResourceFragment each look up Material's internal `com.google.android.material.R.id.design_bottom_sheet` FrameLayout and call `BottomSheetBehavior.from(it)`, when BottomSheetDialog exposes `behavior` directly.

**files:** `ui/settings/StorageBreakdownFragment.kt`, `ui/settings/StorageCategoryDetailFragment.kt`, `ui/resources/AddResourceFragment.kt`

**verification:** All three files exist; the onCreateDialog bodies were not line-verified.

---

### 135. Replace String.format with BigInteger.toString(16) in Utilities.toHex

**rating:** 36/100 &nbsp;·&nbsp; **proposed by:** devin(r2)

`toHex` uses `String.format("%x", BigInteger(1, it))`, paying Formatter overhead whenever sync builds per-user database names.

**files:** `utils/Utilities.kt`

**verification:** Verified verbatim at Utilities.kt:87-89. Equivalent output for a positive BigInteger.

---

### 136. Eliminate the intermediate filter lists in the NotificationsRepositoryImpl batch methods

**rating:** 36/100 &nbsp;·&nbsp; **proposed by:** devin(r2)

`getJoinRequestDetailsBatch`, `getTaskTeamNamesByTaskTitles` and `getTaskTeamNamesByTaskIds` build `map{}.filter{}.distinct()` chains and an un-presized Triple list, on every notification badge or list refresh.

**files:** `repository/NotificationsRepositoryImpl.kt`

**verification:** The three methods exist; the chains were not line-verified.

---

### 137. Cache the imagesArray size and use the isEmpty idiom in VoicesAdapter

**rating:** 35/100 &nbsp;·&nbsp; **proposed by:** jules(r1), minimax(r2)

`imagesArray.size()` is called three times in the image-layout branch — the `if`, the `else-if` and the `for` bound.

**files:** `ui/voices/VoicesAdapter.kt`

**verification:** Verified all three calls at VoicesAdapter.kt:875-883. minimax's version also swaps in `!imagesArray.isEmpty`; jules's only hoists the local.

---

### 138. Use isNotEmpty()/isEmpty() instead of size()-comparisons in five files

**rating:** 34/100 &nbsp;·&nbsp; **proposed by:** glm(r3), minimax(r2)

Five idiom fixes on JsonArray/JsonObject/collection emptiness: JsonUtils.addJson (`value.keySet().size > 0`), TagsRepositoryImpl.createTag (`attachedTo.size > 0`), ConfigurationsRepositoryImpl (`rows.size() > 0`), TransactionSyncManager (`arr.size() == 0`), Feedback (`ar.size() > 0` twice).

**files:** `utils/JsonUtils.kt`, `repository/TagsRepositoryImpl.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `services/sync/TransactionSyncManager.kt`, `model/Feedback.kt`

**verification:** All six sites verified exactly. minimax filed them as five separate one-line tasks; glm found the JsonUtils one. Shipped as one entry — merging them is the only way this is worth a PR.

---

### 139. Count instead of filtering in the CoursesAdapter selection helpers

**rating:** 33/100 &nbsp;·&nbsp; **proposed by:** jules(r3)

`areAllSelected` builds `currentList.filter { ... }` only to read `.size`, and `selectAllItems` rebuilds the same filtered list — an allocation on every selection-checkbox tap.

**files:** `ui/courses/CoursesAdapter.kt`

**verification:** Verified at CoursesAdapter.kt:195-206.

---

### 140. Cache the label map and use the null-safe empty idiom in VoicesViewModel

**rating:** 33/100 &nbsp;·&nbsp; **proposed by:** minimax(r2)

`filterNews` rebuilds a `labelDisplayToValue` map from the `Constants.LABELS` constant on every call, and `downloadReferencedResources` uses `(news?.imagesArray?.size() ?: 0) > 0`.

**files:** `ui/voices/VoicesViewModel.kt`

**verification:** File exists; not line-verified. The task's own steps 3-5 hedge between two different lookup strategies, which is a sign the site was read loosely.

---

### 141. Drop the redundant intermediate collections in five repository mapping chains

**rating:** 32/100 &nbsp;·&nbsp; **proposed by:** jules(r2)

Small allocation trims: TagsRepositoryImpl.getLinkIdsForTagNames (`getByNames(...).map { it.id }` before a mapNotNull), LifeRepositoryImpl.updateMyLifeListOrder (`mapIndexed { ... to index }.toMap()`), SubmissionsRepositoryImpl.getUniqueSurveys (`uniqueSurveys.values.toList()`), FeedbackRepositoryImpl (a map/associateBy/map chain), CoursesRepositoryImpl (`mapValues { entry -> entry.value.map { it } }`).

**files:** `repository/TagsRepositoryImpl.kt`, `repository/LifeRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`, `repository/FeedbackRepositoryImpl.kt`, `repository/CoursesRepositoryImpl.kt`

**verification:** The CoursesRepositoryImpl mapValues copy is verified (:865). The rest are file-and-shape plausible but were not line-verified; each is a 2-6 line change with essentially no measurable payoff. Shipped as one entry because none justifies its own PR.

---

### 142. Use the ViewModel's member-count query before opening the task-assignment dialog

**rating:** 31/100 &nbsp;·&nbsp; **proposed by:** jules(r3)

`TeamsTasksFragment` fetches the full joined-member list just to check whether the team has members before opening the assignment dialog; a count would short-circuit the user-model loads.

**files:** `ui/teams/tasks/TeamsTasksFragment.kt`, `ui/teams/tasks/TeamsTasksViewModel.kt`

**verification:** Files exist and `getJoinedMemberCount` exists on TeamsMembersRepository; the fragment call site was not line-verified. jules filed the fragment change and the ViewModel passthrough as two tasks.

---

### 143. Use the step-exam and resource counts instead of the lists in the course step UI

**rating:** 30/100 &nbsp;·&nbsp; **proposed by:** jules(r1), jules(r3)

`CourseStepFragment` renders `getString(R.string.retake_test, stepExams.size)` (:245/:247) and `getString(R.string.resources_size, resources.size)` (:121) off full lists loaded through CourseStepData.

**files:** `ui/courses/CourseStepFragment.kt`

**verification:** All three call sites verified. The payoff is small — the lists are loaded for rendering anyway — and jules's r3 variant (skip formatting when empty) is a different, weaker claim about layout passes.

---

### 144. Reuse the already-loaded step resources in refreshInlineResources

**rating:** 30/100 &nbsp;·&nbsp; **proposed by:** deepseek(r3)

`CourseStepFragment.refreshInlineResources` issues a second `resourcesRepository.getAllStepResources(stepId)` although the fragment already holds the same list in its `resources` field from onViewCreated.

**files:** `ui/courses/CourseStepFragment.kt`

**verification:** File exists; the second query was not line-verified. Shares a file with the counts task. The task itself flags the ordering hazard (resources must be loaded first).

---

### 145. Consolidate the storage-category extension metadata inside the storage UI package

**rating:** 29/100 &nbsp;·&nbsp; **proposed by:** kimi(r1)

StorageBreakdownFragment hard-codes the category definitions including each extension set, while StorageCategoryDetailFragment re-carries those sets as Bundle args (ARG_EXTENSIONS/ARG_ALL_KNOWN) and pushes them into the ViewModel — extension semantics duplicated across two fragments and threaded as two raw Set<String>.

**files:** `ui/settings/StorageBreakdownFragment.kt`, `ui/settings/StorageCategoryDetailFragment.kt`, `ui/settings/StorageCategoryViewModel.kt`

**verification:** The three files exist and the categories list is in StorageBreakdownFragment as described; the Bundle-arg threading was not verified. ~80 changed lines for a pure-internal tidy.

---

### 146. Read the current user via UserSessionManager in TeamCoursesFragment

**rating:** 28/100 &nbsp;·&nbsp; **proposed by:** kimi(r1)

`setupCoursesList` reads `sharedPrefManager.getUserId().ifEmpty { "--" }` directly to compute canRemove; UserSessionManager is the session authority.

**files:** `ui/teams/courses/TeamCoursesFragment.kt`

**verification:** Verified at TeamCoursesFragment.kt:46. Note this pulls in the opposite direction from the devin cluster below, which routes session identity through UserRepository rather than UserSessionManager — pick one convention before doing either.

---

### 147. Swap UserSessionManager for UserRepository across six ViewModels and DiagnosticsRepositoryImpl

**rating:** 28/100 &nbsp;·&nbsp; **proposed by:** devin(r1)

RequestsViewModel, FeedbackListViewModel, FeedbackComposerViewModel, PersonalsViewModel, ActivitiesViewModel and UserProfileViewModel each inject UserSessionManager only to call getUserModel(); DiagnosticsRepositoryImpl injects both UserSessionManager and SharedPrefManager for fields that are all on UserEntity. UserRepository.getUserModel() returns the same entity.

**files:** `ui/teams/members/RequestsViewModel.kt`, `ui/feedback/FeedbackListViewModel.kt`, `ui/feedback/FeedbackComposerViewModel.kt`, `ui/personals/PersonalsViewModel.kt`, `ui/dashboard/ActivitiesViewModel.kt`, `ui/user/UserProfileViewModel.kt`, `repository/DiagnosticsRepositoryImpl.kt`

**verification:** RequestsViewModel and DiagnosticsRepositoryImpl verified directly; the rest are consistent in shape. Shipped as one entry — six near-identical two-line swaps are one PR, not six. Rated low because the payoff is a convention change, not a fix, and it conflicts with the kimi task above.

---

### 148. Drop SharedPrefManager from LifeViewModel's user-id resolution

**rating:** 27/100 &nbsp;·&nbsp; **proposed by:** devin(r1)

`LifeViewModel` injects SharedPrefManager for `sharedPrefManager.getUserId().ifEmpty { userRepository.getUserModel()?.id }` — the repository already answers the question.

**files:** `ui/life/LifeViewModel.kt`

**verification:** Verified at LifeViewModel.kt:35. Behavior change: the prefs value currently wins over the repository value, so this is not a pure refactor. Overlaps the seed-hoisting task on the same file.

---

### 149. Hoist ViewModels for DictionaryActivity, TeamCoursesFragment, TeamResourcesFragment and EditAchievementFragment

**rating:** 26/100 &nbsp;·&nbsp; **proposed by:** copilot(r1)

Four screens own their data orchestration in the UI layer: DictionaryActivity injects DictionaryRepository and runs count/insertDictionaryData/findByWord in lifecycleScope; TeamCoursesFragment and TeamResourcesFragment fan out to teamsRepository/coursesRepository in lifecycleScope blocks; EditAchievementFragment calls userRepository and resourcesRepository directly.

**files:** `ui/dictionary/DictionaryActivity.kt (+ new VM/test)`, `ui/teams/courses/TeamCoursesFragment.kt (+ new VM/test)`, `ui/teams/resources/TeamResourcesFragment.kt (+ new VM/test)`, `ui/user/AchievementViewModel.kt, ui/user/EditAchievementFragment.kt`

**verification:** DictionaryActivity verified directly (repository injected at :30, four lifecycleScope calls). The others are shape-plausible. Shipped as one entry and rated low not because the direction is wrong but because each is a 100-140 LOC rewrite of a working screen with no test to protect it — the largest, least mechanical work on this list.

---

### 150. Use HTTP HEAD for MainApplication reachability probes

**rating:** 25/100 &nbsp;·&nbsp; **proposed by:** copilot(r2)

`getResponseCode` sets requestMethod = "GET" then reads only the response code, so a cold reachability check can pull a body it discards.

**files:** `MainApplication.kt`, `test/.../MainApplicationTest.kt`

**verification:** Not verified. Rated low on feasibility rather than evidence: the task itself concedes some Planet endpoints may reject HEAD and adds a 405/501 fallback, which is a behavior change on the login/sync gating path for a saving that only matters on a 30-second-TTL cache miss.

---

### 151. Add Flow observe methods to CommunityDao, MyLifeDao, UserChallengeActionsDao and ApkLogDao

**rating:** 22/100 &nbsp;·&nbsp; **proposed by:** minimax(r1)

Four DAOs expose only suspend reads, so any UI watching them must re-call manually.

**files:** `data/room/dao/CommunityDao.kt`, `data/room/dao/MyLifeDao.kt`, `data/room/dao/UserChallengeActionsDao.kt`, `data/room/dao/ApkLogDao.kt`, `+ their repository interfaces and impls`

**verification:** The DAOs exist and lack Flow variants, so the premise holds. Rated low because all four tasks explicitly add API with no consumer — the acceptance criterion is literally 'the new Flow version can be used by UI code'. That is dead code by construction, against the round's own no-unused-code rule. Shipped as one entry.

---

### 152. Add count queries to FeedbackDao and RetryDao

**rating:** 22/100 &nbsp;·&nbsp; **proposed by:** minimax(r1)

Both DAOs expose `getPending(): List<...>` with no count variant, so callers that only need a size load the rows.

**files:** `data/room/dao/FeedbackDao.kt`, `data/room/dao/RetryDao.kt`, `+ FeedbackRepository/RetryRepository and impls`

**verification:** The DAOs exist. Both tasks hedge on whether any caller actually does `.size` ('If fetchPendingItems calls .size on the result, update it'), so the payoff is unestablished. Note the RetryDao half collides with the dead-retry-surface task above.

---

### 153. Move notification type resolution from NotificationsViewModel into NotificationsRepository

**rating:** 20/100 &nbsp;·&nbsp; **proposed by:** minimax(r1)

`resolveType` and `typeLabelFor` are pure classification logic living in the ViewModel; the task also bundles a bulk markSynced and an observeUnreadCount Flow.

**files:** `data/room/dao/NotificationDao.kt`, `repository/NotificationsRepository.kt`, `repository/NotificationsRepositoryImpl.kt`, `ui/notifications/NotificationsViewModel.kt`

**verification:** resolveType/typeLabelFor verified at NotificationsViewModel.kt:259/288. Rated low: it bundles four unrelated changes (move the classifier, batch markSynced, add an unused Flow, and a chunk-of-900 rewrite), and it collides with two higher-rated tasks on the same ViewModel and DAO.

---

### 154. Move the finance totals from EnterprisesFinancesFragment into its ViewModel

**rating:** 19/100 &nbsp;·&nbsp; **proposed by:** minimax(r1)

`calculateTotal` and a mutable HeaderState live in the Fragment, so the debit/credit/total header does not survive configuration changes.

**files:** `ui/enterprises/EnterprisesFinancesFragment.kt`, `ui/enterprises/EnterprisesFinancesViewModel.kt`

**verification:** Not verified. The cited ViewModel name could not be confirmed and the task's own acceptance criterion claims the header will survive process death, which a plain StateFlow does not provide.

---

### 155. Add a member-count query to TeamDao

**rating:** 18/100 &nbsp;·&nbsp; **proposed by:** minimax(r1)

Proposes a `getMemberCount(teamId)` COUNT query to replace `getJoinedMembers(teamId).size`.

**files:** `data/room/dao/TeamDao.kt`, `repository/TeamsRepositoryImpl.kt`

**verification:** Premise already satisfied: `getJoinedMemberCount` at TeamsRepositoryImpl.kt:1045 already delegates to `teamDao.countByTeamIdAndDocType`. Kept only because the task itself instructs the implementer to check for the existing method first, so it degrades to a no-op rather than a wrong change.

---

### 156. Document the repository interface-segregation pattern on TeamsRepository

**rating:** 15/100 &nbsp;·&nbsp; **proposed by:** qwen(r1)

Add KDoc to TeamsRepository and TeamsMembersRepository explaining the three-sub-interface split and its KMP rationale.

**files:** `repository/TeamsRepository.kt`, `repository/TeamsMembersRepository.kt`

**verification:** Premise verified: TeamsRepository does extend TeamsFinancesRepository, TeamsMembersRepository and TeamsNotificationsRepository. Comments only, no behavior — the single qwen task in 30 that survives verification.


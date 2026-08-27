# myPlanet refactor round — performance quick wins (roadmap 7)

**date:** 2026-08-27  
**base:** `copilot/myplanet-refactor-round-another-one` @ `89fd72c`  
**focus:** performance micro-optimizations · no rewrites · ≤~150 LOC · ≤~5 files · no new deps  
**north star notes:** tasks that also move KMP (9) / CMP (10) call that out explicitly  

## Open PRs (R3) — 38 open; their files are off-limits

| # | title |
|---|--------|
| 4075 | robo movie (fixes #4074) |
| 8175 | roboscript update (fixes #7986) |
| 10993 | Voices video |
| 13287 | profile: no char limit for edit texts |
| 13355 | Add P2P resource sharing (Wi‑Fi P2P) |
| 13415 | voices: Add emoji reactions |
| 13604 | teams: Add sort by completeness option in Survey section |
| 13657 | course: Archive course My Courses library |
| 13848 | all: introduce Course/Grade models and wire into UI |
| 13928 | Add baseline profile module and installer |
| 14427 | Course streak |
| 14650 | survey: smoother submissions display |
| 14883 | team: add leaderboard tab |
| 14893 | dashboard: fix ui cropping and logo stretching in landscape |
| 14960 | login: make login history visible and scrollable in landscape |
| 15108 | fix event calendar marking |
| 15158 | teams: allow deleting calendar events |
| 15198 | Preserve input text when switching AI providers |
| 15226 | feat(flutter): Flutter/Dart port of myPlanet |
| 15266 | prevent team calendar cropping in landscape |
| 15267 | prevent download popup dialog cropping when text size is large |
| 15412 | courses limited space for content in landscape |
| 15519 | add dismiss button to last synced status container |
| 15559 | exam: redesign UI with elapsed timer and cards |
| 15699 | resources: updated rating dialog and added unit tests |
| 15808 | sync: intelligent incremental sync via couchdb changes feed |
| 15820 | teams: smoother task and meetup comment threads |
| 15824 | gamification achievement hub offline badges streaks |
| 15825 | local event task reminders workmanager notifications |
| 15951 | teams: smoother repository update requesting |
| 16096 | life: smoother fragment layout sizing and list loading |
| 16101 | all: consistent status bar |
| 16192 | Optimize CommunityServicesFragment link list rendering |
| 16257 | resources: added download filter |
| 16258 | resources: updated search bar color in dark mode |
| 16270 | Initialize Codex Cloud skill submodules |
| 16274 | Refactor SyncActivity sync and upload logic |
| 16292 | Update from task a49913af-2a93-49f6-9f88-b80ce7c09cbf |

**Avoided collision zones (from PR file union):** `AppDatabase` / many DAOs under #15808, `TeamsRepositoryImpl` / teams UI (#15951+), `SyncActivity` (#16274), `ResourcesAdapter`/`ResourcesFragment` (#16257), `LifeFragment` (#16096), `CommunityServicesFragment` (#16192), `BaseDashboardFragment` / `DashboardViewModel` / `BellDashboard*` / `ActivitiesRepositoryImpl` (open PR diffs), `TransactionSyncManager`, `UploadManager`, `TimeUtils`/`NotificationUtils` (#15825), ResourceViewer* (#15699).

Every path/class/function below was opened and confirmed on this checkout.

---

### 1. Push `MyLibrary.needToUpdate()` into Room SQL + SQL-distinct opened resource IDs

**roadmap:** 7 (performance) · also 1 (data layer) · also 9 (SQL/filter logic becomes platform-free DAO surface; repository stays pure Kotlin)

**context:**  
`MyLibrary.needToUpdate()` (`model/MyLibrary.kt`) is  
`!resourceOffline || (resourceLocalAddress != null && _rev != downloadedRev)`.  
`ResourcesRepositoryImpl` loads full lists then filters in memory:

- `getLibraryListForUser` → `getPublicForUserPattern` + `.filter { needToUpdate() }`
- `countLibrariesNeedingUpdate` → same list + `.count { needToUpdate() }`
- `getAllLibrariesToSync` → `getSyncable()` (`resourceOffline = 0` only) + `.filter { needToUpdate() }` — **incomplete vs needToUpdate** (misses offline rows with stale `_rev`)
- `getDownloadSuggestionList` → public-for-user then public-all + filter

Separately, `observeOpenedResourceIds` collects `resourceActivityDao.observeByUserAndType` full rows and maps to `Set` of `resourceId` on every emission.

**files (≤4):**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` (extend only)

**steps:**
1. Add DAO queries on `MyLibraryDao` that encode `needToUpdate` in SQL, e.g.  
   `(resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND IFNULL(_rev,'') != IFNULL(downloadedRev,'')))`  
   variants: for user pattern (public), for all public, and replace/augment `getSyncable` so stale-offline rows are included. Prefer `COUNT(*)` for `countLibrariesNeedingUpdate`.
2. Wire `getLibraryListForUser`, `countLibrariesNeedingUpdate`, `getAllLibrariesToSync`, `getDownloadSuggestionList` to the new queries; remove the in-memory `needToUpdate()` filters for those call sites. Leave `MyLibrary.needToUpdate()` itself intact for other callers.
3. On `ResourceActivityDao`, add `observeOpenedResourceIds(userName, type): Flow<List<String>>` with  
   `SELECT DISTINCT resourceId FROM resource_activity WHERE user = :userName AND type = :type AND resourceId IS NOT NULL`.
4. Change `ResourcesRepositoryImpl.observeOpenedResourceIds` to map that list to a `Set` (no full entity load).
5. Extend `ResourcesRepositoryImplTest` for the four library methods + opened-ids flow (mock new DAO methods).

**acceptance:**
- No behavioral change for “needs download/update” sets vs current `needToUpdate()` semantics (including stale-offline).
- `getAllLibrariesToSync` includes offline rows whose `_rev != downloadedRev`.
- Unit tests green: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest"`.
- ≤150 LOC, no new deps, no TODOs.

**out of scope:** UI/adapters, `AppDatabase` version bump (no schema change), `MyLibrary.kt` API changes, tags/ratings enrichment paths.

---

### 2. Parallelize `UploadCoordinator` batch HTTP uploads

**roadmap:** 7 · also 5 (sync/upload) · also 9 (upload orchestration stays Kotlin; concurrency is coroutine-native)

**context:**  
`UploadCoordinator.uploadBatch` and `uploadBatchRoom` walk `batch.forEach` and await each `postUpload`/`putUpload` sequentially (`UploadCoordinator.kt` ~138–230 and ~387–448). Batch size is already chunked upstream; network latency dominates. `ActivitiesRepositoryImpl` already uses `Semaphore(6)` + `async` for a similar multi-upload pattern — mirror that.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt`
- (optional) add focused tests only if a free test file is needed; prefer extending existing upload-related tests without touching blocked `UploadManager.kt`. If no safe test file, keep production-only and document manual verification.

**steps:**
1. Introduce a private constant (e.g. `MAX_CONCURRENT_UPLOADS = 6`) in `UploadCoordinator`.
2. Rewrite `uploadBatch` and `uploadBatchRoom` to run each item’s network work under `coroutineScope` + `async` + `Semaphore`, collecting into thread-safe lists (`Collections.synchronizedList` or build lists after `awaitAll`).
3. Preserve cancellation (`CancellationException` rethrow), 409 recovery path, `beforeUpload`/`afterUpload` hooks, and return shape `Pair<List<UploadedItem>, List<UploadError>>`.
4. Keep `updateDatabaseBatch` / `updateDatabaseBatchRoom` **after** the network batch completes (do not parallelize DB mark with in-flight HTTP).
5. Do not change `UploadConfig` / `RoomUploadConfig` APIs.

**acceptance:**
- Same success/failure/409 semantics; order of results need not match input order as long as all items are accounted for.
- Under multi-item batches, wall time drops vs sequential (qualitative).
- Existing `./gradlew testDefaultDebugUnitTest` still green for upload-related suites if any touch this class.
- ≤150 LOC, single production file preferred.

**out of scope:** `UploadManager`, `RetryQueue`, serializer changes, batch size tuning, Realm path deletion.

---

### 3. Cache `NetworkUtils.getUniqueIdentifier()` and `getDeviceName()`

**roadmap:** 7 · also 8 (tests)

**context:**  
`getUniqueIdentifier()` reads `Settings.Secure.ANDROID_ID` + `Build.ID` on every call; `getDeviceName()` rebuilds manufacturer/model string every call (`NetworkUtils.kt` 172–186). Callers include activity/news/rating/personal serialization, `SubmissionsRepositoryImpl`, `TeamsRepositoryImpl`, `MyLibrary.serialize`, `ApkLog`, etc. — hot on upload/sync paths. Values are process-lifetime stable.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsTest.kt`

**steps:**
1. Cache both results with `@Volatile` lazy fields or `lazy` (thread-safe).
2. Keep public signatures unchanged.
3. If tests assert format repeatedly, ensure first-call and subsequent-call equality; add a test that two calls return the same instance/value without re-reading settings if practical under Robolectric.
4. Do not cache `getCustomDeviceName` (prefs can change).

**acceptance:**
- Identical string values as before.
- `NetworkUtilsTest` / `NetworkUtilsMockTest` / `NetworkUtilsStateTest` green.
- ≤40 LOC.

**out of scope:** connectivity StateFlow logic, Bluetooth/Wi‑Fi helpers.

---

### 4. One-shot guard for `DownloadUtils.createChannels`

**roadmap:** 7 · also 8

**context:**  
`createChannels` is invoked from every `buildInitialNotification`, `buildProgressNotification`, completion builders, and from `DownloadService`/`DownloadWorker` (`DownloadUtils.kt` 28–57). It already null-checks channels, but still does three `getNotificationChannel` system calls per progress tick. A process-lifetime `channelsCreated` flag avoids repeated manager lookups after first success.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt`

**steps:**
1. Add `@Volatile private var channelsEnsured = false` (or `AtomicBoolean`).
2. At start of `createChannels`, return immediately if already ensured; set flag only after all three channels are confirmed/created.
3. Keep channel IDs/importance/sound settings identical.
4. Extend unit tests for idempotent double-call (mock `NotificationManager` if the test harness already does).

**acceptance:**
- Second+ calls are no-ops; first call still creates missing channels.
- Download notification builders unchanged.
- `DownloadUtilsTest` green.
- ≤30 LOC.

**out of scope:** `DownloadService`/`DownloadWorker` logic, WorkManager scheduling.

---

### 5. Cache `UrlUtils.header` Basic auth encoding

**roadmap:** 7 · also 9 (auth header helper stays pure once credentials are known)

**context:**  
`UrlUtils.header` getter always calls `basicAuthHeader(spm.getUrlUser(), spm.getUrlPwd())`, which allocates a byte array and Base64-encodes on **every** API access (`UrlUtils.kt` 29–38). Credentials change rarely (login/server switch).

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt`

**steps:**
1. Cache last `(user, pwd) → header` pair under a lock or `@Volatile` fields.
2. On `header` get: if user/pwd match cache, return cached string; else recompute via existing `basicAuthHeader` and store.
3. Invalidate cache in `resetForTesting()` and optionally when `init` is called.
4. Leave `basicAuthHeader(username, password)` public behavior unchanged for one-off logins (`LoginSyncManager` uses it directly).
5. Add tests: repeated `header` with same prefs returns same value; changing user/pwd via test prefs forces recompute.

**acceptance:**
- Header string identical to current `basicAuthHeader` output.
- `UrlUtilsTest` green.
- ≤50 LOC.

**out of scope:** host/base/db URL builders, alternative-URL parsing.

---

### 6. Quiet `JsonUtils.safeGet` on the hot parse path

**roadmap:** 7 · also 1 (data parsing) · also 9 (JsonUtils is already platform-free Gson helpers)

**context:**  
Every `getString`/`getBoolean`/`getInt`/`getLong`/`getJsonObject`/… routes through `safeGet`, which on any exception calls `e.printStackTrace()` (`JsonUtils.kt` 16–23). Sync/deserialize paths hit these thousands of times; stack traces on expected type mismatches dominate log I/O and main-thread jank when logging is heavy.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/JsonUtilsTest.kt`

**steps:**
1. In `safeGet`, return `default()` on exception **without** `printStackTrace`. Optionally keep a single debug-level log behind a compile-time/debug flag — prefer silence to match “micro-opt, no new deps”.
2. Leave intentional `printStackTrace` in non-hot helpers only if clearly rare (e.g. `extractSharedTeamName` parse); prefer consistency: remove or demote those too **only if still inside this file and same task**.
3. Extend `JsonUtilsTest` for malformed/missing fields still returning defaults.

**acceptance:**
- Public getters return the same defaults.
- No stack traces from `safeGet` during normal sync of mixed-type Couch docs.
- `JsonUtilsTest` green.
- ≤20 LOC.

**out of scope:** replacing Gson, changing return types, model serializers outside `JsonUtils`.

---

### 7. Coalesce parallel-phase `SyncManager` status emissions

**roadmap:** 7 · also 5 · also 8

**context:**  
Phase 1 launches ~14 tables in parallel; each completion does  
`_syncStatus.value = SyncStatus.Syncing(..., done, parallelTables.size, detail)`  
(`SyncManager.kt` ~154–171). Concurrent writers thrash `StateFlow` collectors (login/sync UI) with near-simultaneous updates and string allocations. Progress still needs to reach “14/14”.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt`

**steps:**
1. Keep `AtomicInteger` progress counter.
2. Coalesce emissions: e.g. emit on first completion, every Nth completion (N=2 or 3), and **always** on the final `done == parallelTables.size`; **or** use a single collector coroutine with a conflated channel. Prefer simplest: only publish when `done == total` or `done % k == 0` or `done == 1`.
3. Do not change phase 2–4 status updates or `transactionSyncManager.syncDb` calls.
4. Do not touch `TransactionSyncManager` (blocked by open PRs).
5. Adjust `SyncManagerTest` expectations if they assert intermediate table-detail strings for every table.

**acceptance:**
- Full sync still completes; final phase-1 status shows all tables done.
- Fewer intermediate `SyncStatus.Syncing` emissions under parallel load.
- `SyncManagerTest` green.
- ≤60 LOC.

**out of scope:** AdaptiveBatchProcessor, RealtimeSyncManager, library/resource phase rewrites.

---

### 8. Skip redundant chip rebuilds in `VoicesLabelManager.showChips`

**roadmap:** 7 · also 8  

**context:**  
`showChips` always `fbChips.removeAllViews()`, builds a new `ChipCloudConfig` via `Utilities.getCloudConfig()`, and constructs a new `ChipCloud` even when labels are empty or unchanged (`VoicesLabelManager.kt` 63–99). Called from `VoicesAdapter` bind paths — scroll jank on community voices.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/VoicesLabelManagerTest.kt`

**steps:**
1. Tag `fbChips` (or use `binding.root` tags carefully) with a signature of `(labels list + canManageLabels)` (e.g. joined labels + mode). If signature matches and child count already correct, only refresh `updateAddLabelVisibility` and return.
2. When labels empty: clear views once if needed, skip `ChipCloud` construction.
3. Optionally reuse a base `ChipCloudConfig` template instead of full `Utilities.getCloudConfig()` every bind (still call `selectMode` as needed). **Do not edit `Utilities.kt` in this task** (keep file sets disjoint).
4. Update `VoicesLabelManagerTest`: when labels unchanged, `removeAllViews` should not be called repeatedly; when labels change, chips rebuild.

**acceptance:**
- Visual chips and delete/add behavior unchanged.
- Bind with same voice/labels no longer forces full tear-down every time.
- `VoicesLabelManagerTest` green.
- ≤80 LOC.

**out of scope:** `VoicesAdapter`/`VoicesFragment` redesign, emoji reactions (#13415 files), Compose migration.

---

### 9. Reuse `MarkdownUtils` link movement method instance

**roadmap:** 7 · also 8 · also 10 (less per-bind Android object churn in shared markdown helper used by future Compose interop hosts)

**context:**  
`setMarkdownText` allocates a new `CustomLinkMovementMethod()` on every call (`MarkdownUtils.kt` 65–69) while `Markwon` itself is already a cached singleton via `create`/`warmUp`. List/detail binds that set markdown repeatedly create short-lived movement methods and touch listeners.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/MarkdownUtilsTest.kt`

**steps:**
1. Hold a single `CustomLinkMovementMethod` instance (lazy/static) and assign it in `setMarkdownText`.
2. Confirm `CustomLinkMovementMethod` is stateless across TextViews (it only reads the event/widget/buffer) — it is; safe to share.
3. Add/adjust unit test that two `setMarkdownText` calls result in the same `movementMethod` instance on the TextView (Robolectric).

**acceptance:**
- Markdown rendering and image-span clicks still work.
- No new Markwon plugins or dependency changes.
- `MarkdownUtilsTest` green.
- ≤25 LOC.

**out of scope:** `prependBaseUrlToImages`, Glide plugin config, Compose markdown.

---

### 10. Use HTTP HEAD for `MainApplication` reachability probes

**roadmap:** 7 · also 5 (server reachability before sync)

**context:**  
`MainApplication.getResponseCode` uses `connection.requestMethod = "GET"` then only reads `responseCode` (`MainApplication.kt` 235–247). Reachability cache TTL is 30s (`isServerReachable`). GET can pull a response body unnecessarily on every cold check / alternative URL try. HEAD is sufficient for 2xx detection used by `tryConnect`.

**files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`
- `app/src/test/java/org/ole/planet/myplanet/MainApplicationTest.kt` (extend if reachability is covered; otherwise add a focused test of `getResponseCode` behavior via package-visible seam only if one already exists — do not invent large test harnesses)

**steps:**
1. Change `requestMethod` from `"GET"` to `"HEAD"`.
2. If some Planet endpoints reject HEAD, fall back once to GET on `405`/`501` **within the same connection attempt** (still no body read beyond response code). Keep timeouts 5000/5000 and `TrafficStats` tagging.
3. Do not change cache TTL, URL formatting, or alternative-URL loop structure.
4. Keep public `isServerReachable` / `isPrimaryServerReachable` signatures.

**acceptance:**
- Reachable servers still return true for 2xx; unreachable still false.
- No regression in login/sync gating that depends on reachability.
- Relevant unit tests green; full `./gradlew testDefaultDebugUnitTest` not required beyond touched tests if CI time is constrained, but agent should run `MainApplicationTest` if present.
- ≤40 LOC.

**out of scope:** `ConfigurationsRepositoryImpl.checkServerAvailability` body parsing, OkHttp stack, `ServerUrlMapper`.

---

## Self-check (P5)

| Rule | Status |
|------|--------|
| R1 exactly 10 independent tasks | yes |
| R2 no file in more than one task | yes — each production path unique |
| R3 open PR files excluded | yes — candidates re-checked against PR file union (~300 paths); dropped `BaseDashboardFragment`, `DashboardViewModel`, `ActivitiesRepositoryImpl`, etc. |
| R4 paths/classes/functions exist | yes — opened on disk before citing |
| R5 size/deps/TODO | each task scoped ≤~150 LOC, ≤5 files, no new deps, no placeholders |
| R6 plan only | no repo files modified this run |
| Roadmap tags | all serve **7**; 1 also **1+9**; 2 also **5+9**; 5/6 also **9**; 7 also **5**; 9 notes **10** |

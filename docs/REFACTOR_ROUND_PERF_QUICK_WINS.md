# myPlanet refactor round — performance quick wins

date: 2026-10-01 · base commit: `286e66e0e` (`all: smoother importing (fixes #17814) (#17813)`) · open PRs checked (33): 17812, 17811, 17776, 17694, 17680, 17435, 17254, 17187, 16623, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

focus: performance quick wins · micro-optimizations that unblock bigger refactors · obvious inefficiencies removable without rewrites

How the plan was built:
- **Off-limits list.** The head of every open PR was fetched and diffed against its merge base with `master`. That covers all 33 PRs, not only the recent `ready` / `merge` ones. Every file any of them touches (1,061 paths, 273 of them under `app/src/main`) is off-limits, tests included.
- **Shared rules.** No file appears in more than one task. Every path, class, function and line number below was opened and checked at `286e66e0e`. All paths are relative to the repo root, and `…/myplanet/` is short for `app/src/main/java/org/ole/planet/myplanet/`. Test paths under `app/src/test/java/org/ole/planet/myplanet/` are written as `test:…`.
- **Every executor** must finish with `./gradlew testDefaultDebugUnitTest` and `./gradlew assembleDefaultDebug` both green. The commit title follows the house style `scope: smoother thing doing`.

---

### 1. drop the double map lookup and per-call lambda allocation from the GsonUtils field getters (roadmap 7+1, moves 9)
context: Sync calls the `GsonUtils` getters for nearly every field of every document: 253 qualified `GsonUtils.get{String,Int,Long,Boolean,Float}(` call sites in 26 files, plus `toSyncDocuments` in the same file.
- `…/myplanet/utils/GsonUtils.kt:106-107` `fieldElement` does `jsonObject?.takeIf { it.has(fieldName) }?.get(fieldName)`. That is two tree-map lookups where one `get` (which returns null for a missing key) is enough.
- `getPrimitive` (line 109) is not `inline`. Every `getString` / `getInt` / `getLong` / `getBoolean` / `getFloat` call therefore allocates two capturing lambdas and boxes the primitive result.
- `getJsonElement` (lines 185-190) repeats the same `has` + `get` pair.

The fix is a pure-Kotlin core helper, so it stays platform-free (roadmap 9).

files:
- edit `…/myplanet/utils/GsonUtils.kt`: object `GsonUtils`, functions `fieldElement`, `getPrimitive`, `getJsonElement`.
- leave alone: the getter signatures and every caller; `safeGet` (already `private inline`, line 68); `logFallback` and its `android.util.Log` use (pinned by `test:utils/GsonUtilsNoLogStubTest.kt`); the `toGson` / `toKotlinx` bridge functions at the top of the file; `services/sync/TransactionSyncManager.kt` and `repository/ResourcesRepositoryImpl.kt` (open PRs own them).

steps:
1. Change `fieldElement` to a single `jsonObject?.get(fieldName)`.
2. Make `getPrimitive` a `private inline fun`. Mark its lambda parameters `crossinline` only if the compiler requires it because they are used inside `safeGet`'s block.
3. In `getJsonElement`, replace `if (!jsonObject.has(fieldName)) return …` plus the later `get` with one `jsonObject.get(fieldName)` and a null check. Keep the same `JsonObject()` / `JsonArray()` defaults.
4. Do not change any public signature, default value or fallback path.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.GsonUtils*"` passes unchanged (GsonUtilsTest, GsonUtilsCoercionTest, GsonUtilsNoLogStubTest, GsonUtilsKotlinxBridgeTest).
- `./gradlew testDefaultDebugUnitTest` is green.
- A full sync on a device completes with the same item counts as before.

size budget: ~15 changed lines, 1 file

out of scope: no `Log` removal, no changes at call sites, no new getters, no kotlinx migration.

---

### 2. stop probing the alternative server when the primary server already answered (roadmap 5+7)
context: Each reachability check on a mapped server makes two network calls, even though the second result is only used when the first one fails.
- `…/myplanet/repository/ConfigurationsRepositoryImpl.kt:185-188` always runs `checkServerAvailability(it)` on `mapping.alternativeUrl` after `val primaryReachable = checkServerAvailability(mapping.primaryUrl)`. The result is only read in `if (!primaryReachable && alternativeReachable)` (line 190).
- `…/myplanet/services/sync/ServerUrlMapper.kt:102-107` (`updateServerIfNecessary`) does the same with `isServerReachable(altUrl)`.

On a LAN-only tablet the cloud clone is unreachable, so every auto-sync, resource download and resource open waits out a full extra timeout.

files:
- edit `…/myplanet/repository/ConfigurationsRepositoryImpl.kt`: `checkServerAvailability()` (no-arg overload, line 174).
- edit `…/myplanet/services/sync/ServerUrlMapper.kt`: `updateServerIfNecessary` (line 98).
- edit `test:services/sync/ServerUrlMapperTest.kt`: `testUpdateServerIfNecessaryWhenPrimaryIsUp` (line 226).
- leave alone: `repository/ChatRepositoryImpl.kt` and `services/ServerReachabilityWorker.kt` (open PRs own them; both call `updateServerIfNecessary` and gain the fix for free); `ui/chat/ChatDetailFragment.kt` (task 9); `checkServerAvailability(url: String)` (line 219).

steps:
1. In `ConfigurationsRepositoryImpl`, compute `alternativeReachable` only when `!primaryReachable`. The `if` / `else` structure and the `serverAvailabilityCache` write stay as they are.
2. Make the same change in `ServerUrlMapper.updateServerIfNecessary`: call `isServerReachable(altUrl)` only when `!primaryAvailable`.
3. In `testUpdateServerIfNecessaryWhenPrimaryIsUp`, make the lambda record every URL it is called with, then assert that `"https://alternative.com"` was never probed.
4. Run the tests in `test:repository/ConfigurationsRepositoryImplTest.kt`. The "falls back to alternative url when primary fails" case must stay green.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.ServerUrlMapperTest" --tests "org.ole.planet.myplanet.repository.ConfigurationsRepositoryImplTest"` is green.
- The full suite is green.
- With the primary up, a resource open logs one availability request instead of two. With the primary down and the clone up, the app still switches to the clone.

size budget: ~15 changed lines, 3 files

out of scope: no change to `RetryInterceptor`, the timeouts or the cache TTL; no parallel probing.

---

### 3. extract PDF text only when Read Aloud is tapped, and drop the duplicate audio listener (roadmap 7)
context: `…/myplanet/ui/viewer/ResourceViewerFragment.kt:512` calls `extractPdfText()` (lines 556-562) every time a PDF opens. That loads the whole document through PDFBox (`ResourceViewerViewModel.extractPdfText`, `ui/viewer/ResourceViewerViewModel.kt:130-137`) and strips text from every page, while `renderPdf()` is opening the same file. `pdfText` is only read in the `fabReadAloud` click handler (line 572), so large PDFs cost seconds of CPU and a memory spike for nothing. Separately, `initializeAudioPlayer` (line 458) adds a second `Player.Listener` (lines 466-478) that repeats the `onIsPlayingChanged` and `STATE_ENDED` handling already registered in `createExoPlayer` (line 406). As a result, each pause or end of track writes playback progress twice.

files:
- edit `…/myplanet/ui/viewer/ResourceViewerFragment.kt`: `setupPdfViewer` (503), `extractPdfText` (556), `setupPdfFabActions` (564), `initializeAudioPlayer` (458).
- leave alone: `ui/viewer/ResourceViewerViewModel.kt` (reuse `extractPdfText` as is); `ui/viewer/WebViewActivity.kt`; `prepareVideoPlayer`; `createExoPlayer`.

steps:
1. Remove the `extractPdfText()` call from `setupPdfViewer`.
2. In the `fabReadAloud` listener:
   - if TTS is speaking, stop it as today;
   - otherwise launch on `viewLifecycleOwner.lifecycleScope`, fill `pdfText` from `viewModel.extractPdfText(file)` if it is still empty, then call `ttsManager.speak(pdfText)`.
3. Reuse or inline the file-existence guard from `extractPdfText()`. Delete the old private function if nothing else calls it.
4. Delete the second `player.addListener(...)` block inside `initializeAudioPlayer`. The `createExoPlayer` listener already handles both callbacks and also sets `isResourceFinished`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.viewer.*"` is green; the full suite is green.
- Opening a large PDF renders page 1 without the PDFBox CPU burst.
- Tapping Read Aloud still speaks the text, with a short delay on the first tap only.
- Pausing an audio resource and reopening it resumes from the same position.

size budget: ~25 changed lines (mostly deletions), 1 file

out of scope: no change to video playback, `renderPdf`, or TTS behaviour.

---

### 4. stop inflating an unused server dialog and re-downloading community docs on every login-screen create (roadmap 7+3)
context: Two pieces of wasted work on the login screen.
- **Unused dialog.** Every tap on the login sync icon runs `…/myplanet/ui/sync/LoginActivity.kt:355-359`. That inflates `DialogServerUrlBinding` (the full server-settings layout) and builds a `MaterialDialog` on the main thread, which is never shown. `SyncConfigurationCoordinator.handleConfigurationSuccess` never reads `currentDialog` for `CallerContext.LOGIN_ACTIVITY` (`ui/sync/SyncConfigurationCoordinator.kt:77-81`); only the sync-activity branch does (lines 88-100).
- **Repeated download.** `LoginActivity.kt:380-384` calls `loginViewModel.syncCommunityDocs()` on every `onCreate`, including rotations and theme or language `recreate()`. Each call downloads and replaces the whole community registration table, which is only read when the user opens manual configuration.

files:
- edit `…/myplanet/ui/sync/LoginActivity.kt`: the `syncIcon.setOnClickListener` block (lines 342-364), plus imports.
- edit `…/myplanet/ui/sync/LoginViewModel.kt`: `syncCommunityDocs` (line 91).
- edit `test:ui/sync/LoginViewModelTest.kt`.
- leave alone: `ui/sync/SyncActivity.kt`, `ui/sync/SyncConfigurationCoordinator.kt`, `ui/sync/ServerDialogExtensions.kt`, `repository/CommunityRepositoryImpl.kt` (an open PR owns it), `ui/dashboard/DashboardElementActivity.kt`.

steps:
1. In the sync-icon click listener, delete the four lines that build `dialogServerUrlBinding`, `contextWrapper`, `builder` and `dialog`, and the `currentDialog = dialog` assignment. Keep `syncIconDrawable.start()` and `checkMinApk(url, serverPin, "LoginActivity")`.
2. Remove the imports this leaves unused: `ContextThemeWrapper`, `LayoutInflater`, `MaterialDialog`, `DialogServerUrlBinding`. Check each with grep first.
3. In `LoginViewModel`, add a private `communityDocsSynced` flag. `syncCommunityDocs()` returns `true` without calling the repository once a previous call has returned `true`, so a failed sync is still retried. The ViewModel survives rotation and `recreate()`.
4. Keep the existing test at `LoginViewModelTest.kt:181`. Add one test: two successful calls produce `coVerify(exactly = 1) { communityRepository.syncCommunityDocs() }`. Add another: a `false` result followed by a second call hits the repository twice.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.sync.*"` is green, including `test:ui/sync/SyncConfigurationCoordinatorTest.kt`; the full suite is green.
- On a device, the login sync icon still runs the version check and sync.
- Rotating the login screen no longer triggers another `communityregistrationrequests` request; check this in the OkHttp log.

size budget: ~30 changed lines, 3 files

out of scope: no time-based throttling, no SharedPreferences flag, no change to `CommunityRepository`.

---

### 5. move the map-tile asset copy off the main thread at cold start (roadmap 7)
context: The launcher activity runs `copyAssets(this)` synchronously in `onCreate`, at `…/myplanet/ui/onboarding/OnboardingActivity.kt:68`.
- `MapTileUtils.copyAssets` (`utils/MapTileUtils.kt:13-35`) does external-storage `exists()` / `mkdirs()` and `assets.open()` for two `.mbtiles` files.
- No `assets` directory or `.mbtiles` file exists anywhere under `app/src`, so every launch throws and logs two `FileNotFoundException`s on the main thread before the first frame.

files:
- edit `…/myplanet/ui/onboarding/OnboardingActivity.kt`: `onCreate`. `dispatcherProvider` is already injected at lines 44-45, and `lifecycleScope` is already imported (line 14).
- leave alone: `utils/MapTileUtils.kt` and `test:utils/MapTileUtilsTest.kt`; `proceedWithLaunch`; the deep-link branches above line 63.

steps:
1. Replace the line-68 call with `lifecycleScope.launch(dispatcherProvider.io) { copyAssets(applicationContext) }`. Pass the application context so the copy does not hold the Activity.
2. Leave the call where it is in the flow, after `setContentView` and before the app-chooser check, so the ordering of everything else is unchanged.
3. Confirm `kotlinx.coroutines.launch` is imported. Line 93 already uses `lifecycleScope.launch`, so it should be.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.MapTileUtilsTest"` is green; the full suite is green.
- On a cold start, `adb logcat -s MapTileUtils` still shows the warnings, but from a background thread.
- The onboarding or login screen appears with no change in behaviour.

size budget: ~2 changed lines, 1 file

out of scope: do not delete `MapTileUtils` or the call. Whether offline tiles should ship at all is a product decision.

---

### 6. cache the manifest permission set and drop the unused install-permission check (roadmap 7+8)
context: Two unnecessary system calls on the main thread when activities and fragments are created.
- **Permission check.** `…/myplanet/base/BasePermissionActivity.kt:116-124` (`isPermissionDeclaredInManifest`) calls `packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)` each time it runs. `requestAllPermissions()` (line 64) calls it up to four times per activity start (lines 74, 78, 82, 103 on Android 13+), from `SyncActivity.onCreate` (`ui/sync/SyncActivity.kt:200`), so every login and dashboard start does a binder round trip each time.
- **Install check.** `…/myplanet/base/BaseContainerFragment.kt:71` calls `hasInstallPermission(requireContext())`, which is `packageManager.canRequestPackageInstalls()`, in `onCreate` of every dashboard, team, course and resource fragment. The result is stored in `hasInstallPermissionValue` (line 64), which is never read. `installUnknownSourcesRequestCode` (line 63) is unused too.

files:
- edit `…/myplanet/base/BasePermissionActivity.kt`: `isPermissionDeclaredInManifest`.
- edit `…/myplanet/base/BaseContainerFragment.kt`: fields at lines 63-64 and `onCreate` line 71.
- leave alone: `base/BaseDashboardFragment.kt`, `base/BaseVoicesFragment.kt`, `base/BaseResourceFragment.kt`, `base/BaseRecyclerFragment.kt` (open PRs own them); `BasePermissionActivity.hasInstallPermission` in the companion (still used by `installApk`); `requestMediaPermissions`.

steps:
1. In `BasePermissionActivity`, add a `private val declaredPermissions: Set<String> by lazy { … }`.
   - It reads `requestedPermissions` once into a `HashSet`.
   - It keeps today's `try` / `catch` that logs and falls back to empty.
2. Make `isPermissionDeclaredInManifest` a lookup in `declaredPermissions`.
3. In `BaseContainerFragment`, delete `installUnknownSourcesRequestCode`, `hasInstallPermissionValue` and the line-71 assignment. Before deleting, grep for each name to confirm there are no other uses.
4. Remove any import that becomes unused.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.base.*"` is green (`test:base/BasePermissionActivityTest.kt`, `test:base/BaseContainerFragmentTest.kt`); the full suite is green.
- On first launch on Android 13+, the same permission prompts appear as before.
- Installing an APK update from the resources list (default flavor) still asks for the unknown-sources permission.

size budget: ~15 changed lines, 2 files

out of scope: no restructuring of `requestAllPermissions`, no companion-level or process-wide cache.

---

### 7. count the remaining download queue without copying it on every file (roadmap 5+7)
context: In `…/myplanet/services/DownloadService.kt:179-184`, `getRemainingCount()` builds `priority + pendingUrls`, which is a new `LinkedHashSet` of the entire queue, and then counts it.
- It runs three times per downloaded file: in `processDownloadQueue` (line 155), in `cleanupProcessedUrls` (line 204) and in `onDownloadComplete` (line 584).
- The recount at line 204 always matches line 155, because the URL was already added to `processedUrls` at line 153.
- A "download all" of N resources is therefore O(N²) work plus a full set allocation per pass on low-end tablets.

files:
- edit `…/myplanet/services/DownloadService.kt`: `getRemainingCount`, `cleanupProcessedUrls`.
- leave alone: `persistProcessedUrls`, `Companion.getNextUrl`, `onDownloadComplete` (only its call into `getRemainingCount` is affected), `services/DownloadWorker.kt` and `test:services/DownloadWorkerTest.kt` (open PRs own them).

steps:
1. Rewrite `getRemainingCount` to count without allocating a union: `priority.count { it !in processedUrls } + pending.count { it !in processedUrls && it !in priority }`.
2. Keep exactly one `getStringSet` read per key per call. `test:services/DownloadServiceOnDownloadCompleteTest.kt` (`onDownloadComplete reads priority set once and parses filename once`) asserts this.
3. In `cleanupProcessedUrls`, drop the `cachedRemainingCount = getRemainingCount()` recount. The value set at line 155 is still correct, because `completedUrls.add` does not change `processedUrls`.
4. `cleanupProcessedUrls` has exactly one caller (line 165, right after line 155 set the count). Re-check with grep before deleting the recount; if a new caller has appeared, keep the recount on that path.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.DownloadService*"` is green (DownloadServiceTest, DownloadServiceCompletionTest, DownloadServiceOnDownloadCompleteTest, DownloadServiceResumeTest, DownloadServiceUrlSelectionTest); the full suite is green.
- A multi-resource download still shows the correct "x / y" progress in the notification.

size budget: ~10 changed lines, 1 file

out of scope: no queue data-structure rewrite, no change to persistence intervals.

---

### 8. keep the PDF thumbnail cache key off the main thread and cap thumbnail bitmap size (roadmap 7)
context: `…/myplanet/utils/PdfThumbnailLoader.kt:19` builds its cache key from `file.lastModified()` and `file.length()`. Those are two stat syscalls that run before `withContext(dispatcherProvider.io)`, so they happen on the caller's main-thread adapter scope for every bound PDF row in `ui/resources/ResourcesAdapter.kt:395` and `ui/courses/InlineResourceAdapter.kt:239`. Lines 26-28 also size the bitmap as `page.height * scale` with no upper limit, so an unusually tall page can request a multi-GB bitmap. The resulting `OutOfMemoryError` is not caught by `catch (_: Exception)`, so the app crashes. The tested `computePdfRenderSize` (`utils/PdfRenderUtils.kt:7`) already applies `maxDim` and `maxPixels` caps.

files:
- edit `…/myplanet/utils/PdfThumbnailLoader.kt`: `firstPageBitmap`.
- leave alone: `ui/resources/ResourcesAdapter.kt` (an open PR owns it); `ui/courses/InlineResourceAdapter.kt`; `utils/PdfRenderUtils.kt` and `test:utils/PdfRenderUtilsTest.kt`; `evictAll`.

steps:
1. Move the whole body into the `withContext(dispatcherProvider.io)` block: building `cacheKey`, `cache.get(cacheKey)` and `cache.put`. `LruCache` is thread-safe.
2. Replace the manual `scale` / `width` / `height` math with `val (width, height) = computePdfRenderSize(page.width, page.height, targetWidthPx)`.
3. Keep the `targetWidthPx <= 0` early return and the `catch (_: Exception) { null }` fallback.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.PdfRenderUtilsTest"` is green; the full suite is green.
- The resources list still shows PDF first-page thumbnails, and they are cached on scroll-back.
- StrictMode (`detectDiskReads`) no longer flags `PdfThumbnailLoader` on the main thread.

size budget: ~10 changed lines, 1 file

out of scope: no change to cache size or eviction, no change to the adapters.

---

### 9. advance the AI typing animation in chunks instead of one character at a time (roadmap 7)
context: `…/myplanet/ui/chat/ChatDetailFragment.kt:270-282` animates each AI reply by calling `onUpdate(response.substring(0, currentIndex + 1))` once per character, with `delay(10L)`. Each update in `…/myplanet/ui/chat/ChatAdapter.kt:76-79` (`ResponseViewHolder.bind`) sets the TextView text and calls `recyclerView.scrollToPosition(bindingAdapterPosition)`. A 3,000-character answer therefore means about 3,000 substrings (O(N²) character copies), 3,000 TextView re-layouts and 3,000 RecyclerView layout requests on the main thread. That is visible jank on low-end tablets.

files:
- edit `…/myplanet/ui/chat/ChatDetailFragment.kt`: the `ChatAdapter(...)` animation lambda in `initChatComponents` (line 260).
- edit `…/myplanet/ui/chat/ChatAdapter.kt`: `ResponseViewHolder.bind` (line 70).
- leave alone: `ui/chat/ChatViewModel.kt`, `ui/chat/ChatHistoryFragment.kt`, the `updateServerIfNecessary` code in this fragment (lines 560-610, related to task 2), `repository/ChatRepositoryImpl.kt` (an open PR owns it).

steps:
1. In the animation loop, advance by `val step = maxOf(1, response.length / 200)` per tick, so every reply finishes in about 200 updates. Clamp with `minOf(response.length, currentIndex + step)`.
2. Keep the `isActive` check, the 10 ms delay, the `onComplete()` call and the returned cancel lambda unchanged.
3. In `ResponseViewHolder.bind`'s `onUpdate` callback, scroll only when the TextView `lineCount` changed since the last update. Track the last count in a local `var`.
4. The final text must still equal `response` exactly.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.*"` is green (`test:ui/chat/ChatAdapterTest.kt`, `test:ui/chat/ChatDetailFragmentTest.kt`); the full suite is green.
- On a device, a long AI answer types out smoothly, ends with the full text, and stays scrolled to the bottom.

size budget: ~12 changed lines, 2 files

out of scope: no Compose port of the chat bubble, no change to markdown rendering or to the non-animated paths.

---

### 10. skip the no-op notification upsert on every dashboard load (roadmap 1+7, moves 9)
context: Every dashboard load calls `updateResourceNotification` and `updateStorageNotification`. Both go through `…/myplanet/repository/NotificationsRepositoryImpl.kt:78-118` (`updateCountNotification`).
- The function always ends with `notificationDao.upsert(notification)` (line 114), even when the stored message and `relatedId` already equal the new values and `valueChanged` is false.
- Each unchanged load therefore pays a write transaction plus a Room invalidation that re-emits every notification-observing `Flow`, including the dashboard badge.

The fix is pure repository logic with no Android types, so this repository stays platform-free (roadmap 9).

files:
- edit `…/myplanet/repository/NotificationsRepositoryImpl.kt`: `updateCountNotification`.
- edit `test:repository/NotificationsRepositoryImplTest.kt`: `updateResourceNotification when count unchanged keeps it read` (line 925) and `updateStorageNotification when percent unchanged keeps it read` (line 1013).
- leave alone: `ui/dashboard/DashboardViewModel.kt`, `ui/notifications/NotificationsViewModel.kt`, `services/TaskNotificationWorker.kt` (open PRs own all three); `bulkInsertFromSync` and the other upserts at lines 491 and 518.

steps:
1. In the `!isHealthy` branch, before building `notification`, return early when all of these hold:
   - `existingNotification != null`
   - `!valueChanged`
   - `existingNotification.message == formattedMessage`
   - `existingNotification.relatedId == relatedId`
2. Leave the create path, the changed-value path (mark unread, bump `createdAt`) and the healthy delete path as they are.
3. Rewrite the two "unchanged" tests to assert `coVerify(exactly = 0) { notificationDao.upsert(any()) }`, not to capture the upsert.
4. Confirm the other `updateResourceNotification` / `updateStorageNotification` tests (lines 899-1062) still pass untouched.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest"` is green; the full suite is green.
- On the dashboard, the resource-count and storage notifications still appear, update when the count changes, and stay read when it does not.

size budget: ~20 changed lines, 2 files

out of scope: no DAO changes, no change to how notifications are worded or when they are created.

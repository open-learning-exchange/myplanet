# myPlanet refactor round — 10 work orders

- **Date:** 2026-11-10
- **Base commit:** `2093a62` (`master`, "all: smoother base recycler download offer tracking (fixes #5278) (#18543)")
- **Open PRs checked (35):** 18555, 18554, 18551, 18540, 18539, 18455, 18076, 18045, 18029, 17832, 17811, 17694, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13355, 13287, 10993, 8175, 4075
- **How R3 was applied:** I took each file list from the GitHub PR files API, not from local diffs. Local diffs give wrong results here because of squash merges. I counted renamed-from paths too. That gives 1,368 off-limits paths. None of the open PRs carries a `review ready` or `merge` label, so I applied R3 strictly to **every** open PR. The large ones are the drafts 18045, 18029 and 17832 (KMP), 17694 (shared module) and 15226 (Flutter). No file below appears in any open PR.
- **Workflow logs read:** test run 42782082676 (both shards), release run 42782082588 (`lite` job) and build run 42781301168 (`default` job).

## CI findings that are not scheduled (their files belong to open PRs)

These are the biggest workflow wins in the logs. Every file they need is owned by an open PR, so none of them can be a task this round. Pick them up once those PRs land or close.

| Finding (evidence) | File it needs | Held by |
|---|---|---|
| About 11.4k of the 11.9k lines in each test, build and release job log are 52 copies of one AGP-internal deprecation trace (`BasePlugin.createAndroidJdkImageConfiguration` → `Configuration.setVisible`). The traces print because `--stacktrace` is passed together with `--warning-mode all`. | `.github/workflows/test.yml:70`, `build.yml:63`, `release.yml:71` | 18045, 18029, 17694, 15226, 18455 |
| The only compiler warning in every job is a KSP warning: `ChatDao.kt:16 … List<String?> … nullable type argument is meaningless` | `data/room/dao/ChatDao.kt`, `repository/ChatRepositoryImpl.kt` | 15808, 18045, 18029, 17832 |
| Manifest merger warning: `AndroidManifest.xml:170 … WorkManagerInitializer … no other declaration present` | `app/src/main/AndroidManifest.xml` | 18540 |
| Both test shards are over the 400 s `--warn-over` budget (499 s and 436 s). The slowest classes (`UserRepositoryImplTest` at 1.03 s per test, `LoginSyncManagerTest` at 1.18 s per test) set up `mockkObject`/`mockkStatic` for every test. | those test classes | 18045, 18029, 17832 |
| `android.enableJetifier=true` is deprecated (AGP 10 removes it). | `gradle.properties` | **Cannot be removed yet:** `material-dialogs-commons` 0.9.6.0 (`app/build.gradle:230`) still uses the support library |

Task 10 is the one workflow-time win that only touches free files.

---

### 1. Await the health upload inside the workers instead of firing and forgetting it into the app scope (roadmap 5 + 7, moves 9)
context: `UploadToShelfService.uploadHealth()` (`services/UploadToShelfService.kt:75-79`) only calls `appScope.launch(dispatcherProvider.io) { healthRepository.syncPendingHealthExaminations() }`. Both callers are WorkManager workers: `services/UserDataWorker.kt:56` (inside the `uploadUserData` callback) and `services/AutoSyncWorker.kt:112` (inside a `workerScope.launch`). A worker can therefore report `Result.success()` while the health upload is still running in the app scope. If the process dies, the upload is lost silently, and its errors never reach the worker's `runCatchingRethrowCancellation`. Running the upload in the worker's own coroutine merges two upload paths into one.
files:
- `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt`: the `UPLOAD_TYPE_BULK` branch of `doWork()`, and the `@AssistedInject` constructor.
- `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt`: `onError()` lines 104-116, and the constructor.
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`: delete `uploadHealth()`.
- `app/src/test/java/org/ole/planet/myplanet/services/UserDataWorkerTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/UploadToShelfServiceTest.kt`: the test `uploadHealth uploads and marks health records`, lines 166-173.
Leave these alone: `HealthRepository`/`HealthRepositoryImpl` (open PRs own them), `UploadManager`, `AutoSyncUploadRunner`, and `uploadSingleUserHealth()`.
steps:
1. Inject `HealthRepository` into both workers' `@AssistedInject` constructors. `HealthRepository.syncPendingHealthExaminations()` is already a `suspend fun` (`repository/HealthRepository.kt:13`).
2. In `UserDataWorker`, remove `uploadToShelfService.uploadHealth()` from the `uploadUserData` callback. Add a separate `runCatchingRethrowCancellation { healthRepository.syncPendingHealthExaminations() }` right after the `awaitUploadCompletion` block that wraps `uploadUserData`.
3. In `AutoSyncWorker.onError()`, replace `uploadToShelfService.uploadHealth()` (line 112) with a direct `healthRepository.syncPendingHealthExaminations()` call in the same `workerScope.launch`, after `checkHealth()`.
4. Delete `UploadToShelfService.uploadHealth()` and its test. Move the "syncs pending health" assertion into `UserDataWorkerTest`: pass a relaxed `HealthRepository` mock to the constructor at line 39 and `coVerify` it once for `UPLOAD_TYPE_BULK`.
5. Remove any imports that are now unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserDataWorkerTest" --tests "*UploadToShelfServiceTest"` passes, and then the full `./gradlew testDefaultDebugUnitTest` stays green.
- `./gradlew assembleDefaultDebug assembleLiteDebug` builds.
- On a device, a manual "upload" sync still pushes pending health examinations: the `health` docs reach the server, and `WorkManager` shows the `UserDataWorker` run finishing only after that upload.
size budget: about 40 changed lines in 5 files.
out of scope: no changes to `HealthRepositoryImpl`, no renaming of `UploadToShelfService`, and no change to the `MainApplication.isSyncRunning` handling in `AutoSyncWorker`.

---

### 2. Use the injected application scope in SyncActivity instead of `MainApplication` statics (roadmap 4)
context: `ui/sync/SyncActivity.kt:645-662` launches the post-login network watcher with `MainApplication.applicationScope.launch { … }` (line 649) and `.launchIn(MainApplication.applicationScope)` (line 662). These reach a static service locator, although Hilt already provides the same scope through the `@ApplicationScope` qualifier (`di/ServiceModule.kt:45`, provided at line 62). Lines 346-347 call `context.getString(...)` on the static `MainApplication.context` (imported at line 47) from inside an Activity instance method, where the Activity's own `getString` is available. With static access, tests cannot substitute the scope, and Hilt cannot see the dependency.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` only.
- Touch only the `@Inject` field block around lines 105-145, the `when (protocol)` block at lines 345-349, and the login-watcher block at lines 640-662.
- Leave alone: the companion `restartApp()` (lines 830-835, it is static on purpose and `SettingsActivity` calls it), `ProcessUserDataActivity` and `LoginActivity` (open PRs own them), and `MainApplication.isServerReachable`.
steps:
1. Add a field `@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope`, importing `org.ole.planet.myplanet.di.ApplicationScope` and `kotlinx.coroutines.CoroutineScope`.
2. Replace `MainApplication.applicationScope` at lines 649 and 662 with that field. Capture it in a local `val` next to the existing captured locals at lines 641-644, as the block already does for `syncRepository` and the others.
3. At lines 346-347, replace `context.getString(R.string.…)` with the Activity's own `getString(R.string.…)`.
4. Keep the `MainApplication.Companion.context` import only if `restartApp()` still needs it; remove any other import that is now unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest` green;
- `./gradlew assembleDefaultDebug` builds.
- On a device: log in while online and check that a `login_activities` sync still runs after login.
- Enter an unreachable server URL and check that the "couldn't reach local/nation server" message still appears for http and https.
size budget: about 12 changed lines in 1 file.
out of scope: do not restructure the nested `launch` inside `onEach`, and do not move this logic into `LoginViewModel`.

---

### 3. Sort resources on the default dispatcher with one hop, and stop swallowing cancellation (roadmap 7 + 3, moves 10)
context: `ui/resources/ResourcesViewModel.kt` runs in-memory sorting on the IO pool: `toggleSortOrder` (line 177), `toggleTitleSortOrder` (line 185) and `applyCurrentSort` (line 193) each wrap pure CPU work in `withContext(dispatcherProvider.io)`. The toggles also call `applyCurrentSort`, so they switch context twice. The toggles write the `sortMode`/`isAscending`/`isTitleAscending` fields from an IO thread, while `getCachedResources()` (lines 91-93) reads the same fields on the main thread, which is a data race. `loadResources()` (lines 105-111) catches `Exception`, which includes `CancellationException`, and calls `e.printStackTrace()`. A cancelled reload therefore does not end the way a cancelled coroutine should. Keeping sort state on the caller's thread is also what a Compose-hoisted ViewModel needs.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` (`toggleSortOrder`, `toggleTitleSortOrder`, `applyCurrentSort`, `loadResources`) and `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt`.
- Leave alone: `ResourcesFragment` and `ResourcesAdapter` (open PRs own them), and `ResourcesListFilter`.
steps:
1. In both toggles, update the sort fields *before* any dispatcher switch. Then compute the result with one `withContext(dispatcherProvider.default)` that calls `applyCurrentSortSynchronous(list)` directly.
2. Change `applyCurrentSort` to use `dispatcherProvider.default`.
3. In `loadResources()`, fetch with `resourcesRepository.getResourceListModels(...)` (a suspend DAO-backed call that needs no IO wrapper), then sort through `applyCurrentSort`. Rethrow `CancellationException` and replace `printStackTrace()` with `Log.e`. The class has neither yet, so add the `android.util.Log` import and a `private const val TAG = "ResourcesViewModel"` in a companion object.
4. Add one test: after `toggleTitleSortOrder` runs, `getCachedResources()` sorts by title. This pins the state-before-switch ordering.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ResourcesViewModelTest"` passes, including the existing test at line 200, `toggleSortOrder sorts by date descending on first toggle and ascending on second toggle`; the full suite stays green.
- On a device, the resources list still toggles date and title sort and keeps its order when you come back to the screen.
size budget: about 30 changed lines in 2 files.
out of scope: no change to the `ResourcesViewModel` public API and no change to the filter or facet code.

---

### 4. Parse chat conversations on the default dispatcher (roadmap 7, moves 9)
context: `ui/chat/ChatConversationPaginator.kt:20` parses the stored conversation JSON with `withContext(dispatcherProvider.io) { GsonUtils.gson.fromJson(newsConversations, Array<Conversation>::class.java) }`. That is CPU-bound deserialisation of a string already in memory, with no I/O, so it occupies an IO-pool thread that disk and network work need. The class has no `android.*` imports, which makes it one of the easy candidates for the KMP core. It should state the right dispatcher now.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt` (`parseAndBuildInitialPage`, line 20)
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginatorTest.kt`
- Leave alone: `ChatDetailFragment` and `ChatRepositoryImpl` (open PRs own them), and `ChatViewModel`.
steps:
1. At line 20, change `dispatcherProvider.io` to `dispatcherProvider.default`.
2. The test already builds a `TestDispatcherProvider(testDispatcher)` (lines 22-23). Add one case where the malformed-JSON path returns an empty page, if no such case exists yet.
3. Remove the `kotlinx.coroutines.Dispatchers` import from the test if it becomes unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ChatConversationPaginatorTest"` passes; the full suite stays green.
- On a device, opening a shared chat from Voices still shows the last 20 exchanges and the "load more" row.
size budget: about 15 changed lines in 2 files.
out of scope: do not switch from Gson to kotlinx-serialization here, and do not change the page size.

---

### 5. Make StableIdGenerator platform-free (roadmap 8, moves 9)
context: `utils/StableIdGenerator.kt` is pure Kotlin: an FNV-1a hash and a `WeakHashMap` fallback. Its only Android dependency is `import androidx.recyclerview.widget.RecyclerView` (line 3), used just for the constant `RecyclerView.NO_ID` (line 11). That one import keeps an otherwise portable ID helper inside the Android-only layer. The callers (`TeamsAdapter.kt:41`, `VoicesAdapter.kt:214`, `CoursesAdapter.kt:100`, `ResourcesAdapter`) compare against `RecyclerView.NO_ID` themselves, so a local constant with the same value (`-1L`) is fully compatible.
files:
- `app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt` (`generateStringId`, lines 3 and 11)
- `app/src/test/java/org/ole/planet/myplanet/utils/StableIdGeneratorTest.kt` (lines 3 and 11-12)
- Leave alone: all four adapters, which open PRs own and which need no change.
steps:
1. Add `const val NO_ID = -1L` to `StableIdGenerator`, return it from `generateStringId` for null or empty keys, and delete the RecyclerView import.
2. In the test, replace the `RecyclerView.NO_ID` assertions (lines 11-12) with `StableIdGenerator.NO_ID`. Add one assertion that `StableIdGenerator.NO_ID == RecyclerView.NO_ID`, keeping the RecyclerView import in the test only, so the two values cannot drift apart.
3. Confirm that `grep -n "^import android" app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt` prints nothing.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*StableIdGeneratorTest"` passes; the full suite stays green.
- Lists that use stable IDs (teams, courses, resources, voices) still scroll and update without flicker.
size budget: about 8 changed lines in 2 files.
out of scope: no changes to the hash or the fallback-ID scheme, and no adapter edits.

---

### 6. Bind BaseContainerFragment's view-touching coroutines to the view lifecycle (roadmap 8 + 7)
context: `base/BaseContainerFragment.kt:357` starts `showDownloadDialog(resources)` from a button click with the *fragment's* `lifecycleScope`. That coroutine outlives `onDestroyView`, so it can show a dialog for a view that no longer exists. Nearby code in the same class already uses `viewLifecycleOwner.lifecycleScope` (lines 117, 148, 187, 226). In `openHtmlResource` (lines 187-190), `context?.getExternalFilesDir(null)` is read *inside* the IO block. If the fragment detaches first, `File(null, "ole/…")` resolves against the process working directory and wrongly reports the entry file as missing. `onDownloadComplete` (lines 117-125) already shows the safe pattern of capturing `context ?: return@launch` first.
files:
- `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt` only (`setResourceButton` and `openHtmlResource`).
- Leave alone: `BaseResourceFragment` and `BaseRecyclerFragment` (open PRs own them), and every subclass.
steps:
1. At line 357, use `viewLifecycleOwner.lifecycleScope.launch` in place of `lifecycleScope.launch`.
2. In `openHtmlResource`, capture `val ctx = context ?: return@launch` before the `withContext(dispatcherProvider.io)` block and use `ctx.getExternalFilesDir(null)` inside it.
3. Check that no other `lifecycleScope.launch` in this file touches views. Leave any non-view ones unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest` green;
- `./gradlew assembleDefaultDebug` builds.
- On a device: open a course with resources, tap "Resources", then rotate the screen or press back right away; the app does not crash and no orphan dialog stays.
- Opening a downloaded HTML resource still starts `WebViewActivity`.
size budget: about 6 changed lines in 1 file.
out of scope: do not move the download dialog into a ViewModel, and do not change `prgDialog` handling.

---

### 7. Stop RetryUtils.retry from retrying and swallowing a cancelled coroutine (roadmap 8, moves 9)
context: `utils/RetryUtils.kt:20` catches `Exception`, which includes `CancellationException`. A cancelled caller is therefore treated as a failed attempt and retried. On the last attempt nothing re-throws, so `retry` returns `null` and the cancellation is lost, and line 32 prints a stack trace for it. Five call sites depend on this helper: `ChatViewModel.kt:140`, `SyncActivity.kt:505`, `DashboardViewModel.kt:244`, `UserRepositoryImpl.kt:696` and `ApiClient.kt:16`. The file itself is pure Kotlin, so fixing it here fixes all of them, and the helper stays KMP-ready.
files:
- `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` and `app/src/test/java/org/ole/planet/myplanet/utils/RetryUtilsTest.kt`.
- Leave alone: all five callers.
- `ApiClient`, `UserRepositoryImpl` and `DashboardViewModel` belong to open PRs, and none of the callers needs a change.
steps:
1. Add `catch (e: CancellationException) { throw e }` ahead of the generic `catch (e: Exception)`, using `kotlin.coroutines.cancellation.CancellationException` so the file stays platform-free.
2. Remove `lastException?.printStackTrace()` (line 32) and the then-unused `lastException` variable. Do not add `android.util.Log`, because this file must stay free of Android imports.
3. Add a `runTest` case in which `block` throws `CancellationException`: `retry` must rethrow it after exactly one call to `block`, with no further attempts.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*RetryUtilsTest"` passes; the full suite stays green.
- Leaving the chat screen while a chat request is retrying no longer logs a `JobCancellationException` stack trace.
size budget: about 20 changed lines in 2 files.
out of scope: no changes to the retry, back-off or `shouldRetry` semantics for real failures.

---

### 8. Give TakeCourseViewModel and HealthExaminationViewModel cancellation-safe error paths (roadmap 3 + 8)
context: `ui/courses/TakeCourseViewModel.kt:130-132` catches every `Exception` in `loadCourse` with only `e.printStackTrace()`. A failed load leaves `_uiState` at `TakeCourseUiState.Loading` forever, so the spinner never stops. `TakeCourseFragment` already handles the failure UX for `NotFound`: it shows a `failed_to_load_course` toast and pops back. In the same file, `getRatingPromptDecision` (lines 99-104) and `HealthExaminationViewModel.saveExamination` (`ui/health/HealthExaminationViewModel.kt:119-121`) also catch `Exception`, which includes `CancellationException`. A save cancelled while leaving the screen is then reported as `_saveResult.send(false)`, so a "save failed" message appears even though nothing failed.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (`loadCourse`, `getRatingPromptDecision`)
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` (`saveExamination`)
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt`
Leave alone: `TakeCourseFragment`, `HealthExaminationActivity` (an open PR owns it), and all repositories.
steps:
1. In `loadCourse`, rethrow `CancellationException`. On any other exception, `Log.e(TAG, …)` and set `_uiState.value = TakeCourseUiState.NotFound`. `TakeCourseViewModel` has no `TAG` or `Log` import yet, so add `import android.util.Log` and a companion `private const val TAG = "TakeCourseViewModel"`.
2. In `getRatingPromptDecision`, rethrow `CancellationException` before the existing `false` fallback.
3. In `saveExamination`, add `catch (e: CancellationException) { throw e }` before the generic catch, and replace `e.printStackTrace()` with `Log.e(TAG, …)`. `TAG` already exists at line 141.
4. Add tests: a repository that throws in `getCourseById` drives the state to `NotFound`, and a `saveExamination` whose repository throws `IOException` still emits `false`.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TakeCourseViewModelTest" --tests "*HealthExaminationViewModelTest"` passes; the full suite stays green.
- On a device, a course that fails to load shows the "failed to load course" toast and goes back instead of an endless spinner.
- Saving an examination still reports success or failure correctly.
size budget: about 45 changed lines in 4 files.
out of scope: no new UI states and no fragment changes.

---

### 9. Move notification text formatting off the ViewModel's Context (roadmap 3 + 6, moves 10)
context: `ui/notifications/NotificationsViewModel.kt:33` injects `@ApplicationContext context: Context` only to resolve strings. It does so at lines 298, 304-305, 320, 322, 347 and 349 (`R.string.resource_notification`, `storage_running_low`, `storage_available`, `user_requested_to_join_team`, `join_request_prefix`, `task_notification`). A ViewModel holding a Context and calling `R.*` directly is exactly what blocks a Compose-Multiplatform port. It also forces the test to pass a relaxed `Context` mock that returns `""` for every string (`NotificationsViewModelTest.kt:49`), so the formatted text is never asserted. The pure helpers `formatStorageNotification` and `formatJoinRequestNotification` (lines 257-275) already take the strings as parameters.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
- a new `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationTextFormatter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`
Leave alone: `NotificationsFragment`, `NotificationsAdapter` and `NotificationsRepository`.
steps:
1. Create `class NotificationTextFormatter @Inject constructor(@ApplicationContext private val context: Context)` with six methods that each return the resolved string: `resourceCount(count)`, `storageRunningLow()`, `storageAvailable()`, `userRequestedToJoin(requester, team)`, `joinRequestPrefix()` and `task(title, date)`. Only these methods should touch `R.string`.
2. Replace the ViewModel's `context` constructor parameter with `NotificationTextFormatter`, and route all seven `context.getString` calls through it. Delete the `android.content.Context` and `ApplicationContext` imports from the ViewModel.
3. In the test, construct the ViewModel with a `mockk<NotificationTextFormatter>()` that returns fixed strings. Add one assertion each for the `task`, `storage` and `join_request` formatted text.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*NotificationsViewModelTest"` passes; the full suite stays green.
- `grep -n "R\.string\|Context" NotificationsViewModel.kt` prints nothing.
- On a device, the notifications list still shows translated task, storage, resource and join-request texts; check in `es` and `ar`.
size budget: about 70 changed lines in 3 files.
out of scope: no Compose code, no new strings, and no changes to the grouping or selection logic.

---

### 10. Drop redundant `android.util.Log` static mocks from four test classes (roadmap 8, CI wall-clock)
context: `app/build.gradle:22` sets `unitTests.returnDefaultValues = true`, so on the JVM every `Log.d/i/w/e` call already returns `0`. Under Robolectric, `ShadowLog` handles them. Four test classes still `mockkStatic(Log::class)` only to stub `Log.* returns 0`. None of them stubs `isLoggable` or `verify`s a Log call. Each `mockkStatic` re-instruments `android.util.Log` bytecode, and that cost appears in the shard timing tables. `DownloadServiceTest` takes 11.5 s for 11 tests (0.57 s median per test) in shard 1, while both shards are over their 400 s budget (499 s and 436 s, run 42782082676). The four classes:
- `services/FreeSpaceWorkerTest.kt:69-70`
- `services/retry/RetryQueueWorkerTest.kt:68` and `85-89`
- `services/DownloadServiceTest.kt:66`, `74` and `86-90`
- `services/DownloadServiceOnDownloadCompleteTest.kt:50-55`
files:
- those 4 files under `app/src/test/java/org/ole/planet/myplanet/`.
- Leave alone: tests that `verify` Log calls or stub `isLoggable` (`ServerReachabilityWorkerTest`, `ServiceModuleTest`, `NotificationActionReceiverTest`, `SyncTimeLoggerTest`, `HealthExaminationTest`, `GsonUtilsCoercionTest` and `SyncTimeLoggerConcurrencyTest`), and `app/build.gradle`.
steps:
1. Delete the `mockkStatic(Log::class)` / `mockkStatic(android.util.Log::class)` lines and every `every { Log.… } returns 0` stub in the four files.
2. Delete `unmockkStatic(Log::class)` in `DownloadServiceTest.kt:74`. Keep the `unmockkAll()` / `clearAllMocks()` calls, because they still reset the other mocks.
3. Remove `import android.util.Log` and any mockk imports that are now unused.
4. Record each class's time before and after the change from `python3 .github/scripts/test_timing_summary.py app/build/test-results/testDefaultDebugUnitTest` and put the numbers in the PR description.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*FreeSpaceWorkerTest" --tests "*RetryQueueWorkerTest" --tests "*DownloadServiceTest" --tests "*DownloadServiceOnDownloadCompleteTest"` passes with the same test count as before; the full `./gradlew testDefaultDebugUnitTest` stays green.
- The timing summary shows no class slower than before.
size budget: about 30 removed lines and 0 added, in 4 files.
out of scope: no production code changes and no Gradle `testOptions` edits.

---

## Self-check
- [x] exactly 10 tasks, each mergeable on its own in any order
- [x] no file appears in two tasks
- [x] every cited path and line was opened at `2093a62`
- [x] every task has all 7 template sections
- [x] no task is shorter than 15 lines
- [x] no task touches a file from any open PR (35 checked, 1,368 paths, renamed-from paths included)
- [x] the plan is written to `docs/`, committed and pushed on its own branch

— signed: Claude Code · effort low

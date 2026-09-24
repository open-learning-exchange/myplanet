# myPlanet refactor round — performance quick wins

date: 2026-09-24 · base commit: `14da1ba6bcec7b8affbf047ac78f16c511f5b357` (master, "all: smoother importing (fixes #17505)")
open PRs checked (31): 17435, 17430, 17356, 17254, 17187, 16624, 16623, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075.
Every file named below is absent from the changed-file lists of all 31 open PRs. That includes the `review` / `ready` PRs 17356, 17254 and 17187.

focus: performance quick wins · micro-optimizations that unblock bigger refactors · obvious inefficiencies removable without rewrites.

Rules that apply to every task:
- Each task names its files, and only those files may be edited.
- Files under "leave alone" belong to open PRs or to another task in this plan. Do not touch them.
- Paths are repo-relative. `…/myplanet/` is short for `app/src/main/java/org/ole/planet/myplanet/`, and `…/test/` is short for `app/src/test/java/org/ole/planet/myplanet/`.
- No new dependencies. No TODOs. No code that nothing calls.
- `./gradlew testDefaultDebugUnitTest` must stay green.

---

### 1. stop reading profile-image URLs as local files in UserEntity.serialize (roadmap 5+7, moves 9)

context: `UserEntity.serialize()` calls `encodeImageToBase64(userImage)` on every call (`model/UserEntity.kt:67`). For every synced user, `userImage` is an http(s) URL built by `UrlUtils.getUserImageUrl` (`model/UserEntity.kt:168`). The `else` branch then runs `File(imagePath).inputStream()` (`model/UserEntity.kt:124`), which throws `FileNotFoundException` and logs it with `e.printStackTrace()` at line 132. `serialize()` runs once per item on the upload paths: `TeamTask.serialize` (`model/TeamTask.kt:79`), `News.createNews` (`model/News.kt:211`), `SubmissionsRepositoryImpl.kt:827/898` and `RatingsRepositoryImpl.kt:146`. So every upload batch pays one failed file open plus one stack trace per item.

files:
- `…/myplanet/model/UserEntity.kt`, function `encodeImageToBase64` (lines 117-135) only.
- `…/test/model/UserEntityEncodeImageTest.kt`, to add test cases.
- leave alone: `serialize()` field layout, `addImageUrl()`, `TeamTask.kt`, `News.kt`, `SubmissionsRepositoryImpl.kt`, `RatingsRepositoryImpl.kt`, `UserRepositoryImpl.kt`. The last four belong to open PRs.

steps:
1. In `encodeImageToBase64`, after the existing null/empty guard, return `null` right away when `imagePath` starts with `http://` or `https://` (ignore case). Such a path is a remote URL, never a local file.
2. In the non-`content://` branch, return `null` without opening a stream when `File(imagePath).isFile` is false.
3. Leave the `content://` branch and the `catch` block as they are.
4. In `UserEntityEncodeImageTest`, add two tests next to `testEncodeImageToBase64File`:
   - an `https://…/_users/org.couchdb.user:x/img.jpg` path returns `null`;
   - a path to a file that does not exist returns `null`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.model.UserEntityEncodeImageTest"` passes, and so do the existing `UserEntityTest` cases.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually: sync a user who has a profile picture, then upload a team task or a rating. Logcat shows no `FileNotFoundException` stack trace from `UserEntity`. A user who picked a local photo (a `content://` URI) still uploads it as `_attachments.img`.

size budget: ~6 changed lines in production code plus ~20 test lines; 2 files.

out of scope: do not add a `serialize(includeImage)` flag and do not change any payload shape. Do not touch the callers.

---

### 2. persist the DownloadService queue in batches instead of after every file (roadmap 7+5)

context: after every downloaded file, `processDownloadQueue` (`services/DownloadService.kt:135-165`) calls `cleanupProcessedUrls()` (lines 184-194). That function copies both URL sets out of SharedPreferences, removes `processedUrls`, and writes both sets back with `preferences.edit { putStringSet(…) }`. So a queue of N resources means N rewrites of a prefs file that holds every remaining URL, which is O(N²) bytes written during a large library download. The rewrites are not needed for correctness: `getNextUrl` (line 631) and `getRemainingCount` (line 177) already filter against the in-memory `processedUrls`. Only process death needs the on-disk copy, and re-trying an already-downloaded URL is skipped cheaply by `FileUtils.checkFileExist`.

files:
- `…/myplanet/services/DownloadService.kt`: `processDownloadQueue`, `cleanupProcessedUrls`, `onDestroy` (line 601), and one new constant in its `companion object` (line 612).
- `…/test/services/DownloadServiceTest.kt`, to add a test.
- leave alone:
  - `getNextUrl`, `onDownloadComplete` (around line 568) and the `startService` companion logic.
  - `utils/DownloadUtils.kt`, `services/DownloadWorker.kt` (task 9) and `services/ResourceDownloadCoordinator.kt`.
  - `DownloadServiceResumeTest.kt` and `DownloadServiceOnDownloadCompleteTest.kt`.

steps:
1. Add `private const val QUEUE_PERSIST_INTERVAL = 10` to the companion object. Add a `private var processedSinceLastPersist = 0` field next to `processedUrls` (line 87).
2. Split `cleanupProcessedUrls()`:
   - it always recomputes `cachedRemainingCount = getRemainingCount()`;
   - it writes the two string sets only when `++processedSinceLastPersist >= QUEUE_PERSIST_INTERVAL`, and then resets the counter.
   - Move the write into a private `persistProcessedUrls()` that holds the current body of lines 185-192.
3. In `processDownloadQueue`, call `persistProcessedUrls()` in the `nextUrl == null` branch before `stopSelf()`.
4. In `onDestroy()`, call `persistProcessedUrls()` before `currentJob?.cancel()`, wrapped in the same try/catch style used there. The next service start then never re-reads URLs that were already processed.
5. Add a Robolectric test in `DownloadServiceTest`. Drive `cleanupProcessedUrls` (via reflection, as the existing tests do with private members) 9 times, then once more, and verify that `SharedPreferences.Editor.putStringSet` ran 0 times and then once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.DownloadService*"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually: queue 30+ resources with "download all". The progress notification counts still go down correctly and the queue ends with the completion notification.
- Kill the app mid-queue and restart the download. Only files not on disk download again.

size budget: ~25 changed lines in production code plus ~30 test lines; 2 files.

out of scope: do not restructure the queue storage (for example into Room) and do not change the URL priority ordering. Leave line 153 (`cachedRemainingCount = getRemainingCount()`) as is.

---

### 3. batch the HTML-resource reconcile pass in ResourcesRepositoryImpl (roadmap 1+7)

context: after each resource sync page, `reconcileHtmlLibraries` (`repository/ResourcesRepositoryImpl.kt:724-735`) loops over the `MyLibrary` rows it has just bulk-upserted. For every HTML row with no local address, it calls the public `reconcileHtmlResourceOffline(resourceId)` (line 400). That function re-fetches the same row with `myLibraryDao.getByResourceId(resourceId)` (line 401), switches to `dispatcherProvider.io` for a file check, then does a single-row `myLibraryDao.upsert(library)` and `clearResourceListCache()` (lines 418-419). So an HTML-heavy catalogue page costs 1 SELECT, 1 dispatcher hop and 1 UPSERT transaction per item, on top of the `upsertAll` at lines 668 and 716.

files:
- `…/myplanet/repository/ResourcesRepositoryImpl.kt`: `reconcileHtmlResourceOffline` (lines 400-420) and `reconcileHtmlLibraries` (lines 724-735).
- `…/test/repository/ResourcesRepositoryImplTest.kt`, to add a test.
- leave alone:
  - the `ResourcesRepository` interface signature;
  - `CoursesRepositoryImpl.kt`, which has its own copy of this loop at lines 878-887 and belongs to an open PR;
  - `BaseContainerFragment.kt:200` (a caller of the public method);
  - `markResourceOfflineByResourceId`.

steps:
1. Move the body of `reconcileHtmlResourceOffline` from line 402 onward into a private helper that:
   - takes the `MyLibrary` directly;
   - mutates it in place (`resourceOffline`, `downloadedRev`, `resourceLocalAddress`);
   - returns `true` only when it changed the row.
   The helper does no DAO writes.
2. Rewrite the public `reconcileHtmlResourceOffline(resourceId)` so it:
   - fetches the row by `resourceId`;
   - calls the helper;
   - upserts the row and calls `clearResourceListCache()` only when the helper returned `true`.
   Its behaviour does not change.
3. Rewrite `reconcileHtmlLibraries(libraries)` so it:
   - calls the helper on the in-hand objects (no `getByResourceId`) and keeps the per-item try/catch and `Log.w`;
   - collects the changed rows;
   - writes them with one `myLibraryDao.upsertAll(changed)` when the list is not empty.
   The existing `clearResourceListCache()` after each call site stays.
4. Add a test: `batchInsertResources` with two HTML docs whose entry files exist on disk. Assert that `myLibraryDao.getByResourceId` is never called, and that `upsertAll` runs once for the sync batch plus once for the reconciled rows.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ResourcesRepository*"` passes. That includes the four existing `reconcileHtmlResourceOffline …` tests at lines 235-290 and 1181, with no changes to them.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually: after a fresh sync on a device that already has HTML resources under `ole/<resourceId>/`, those resources open offline without downloading again.

size budget: ~30 changed lines in production code plus ~35 test lines; 2 files.

out of scope: do not batch `markResourceOfflineByResourceId` and do not change `CoursesRepositoryImpl`. No DAO changes.

---

### 4. stop rebuilding the legacy EncryptedSharedPreferences on every credential call (roadmap 7+4)

context: `SecurePrefs.getLegacyEncryptedPrefs()` (`utils/SecurePrefs.kt:152-168`) builds a Keystore `MasterKey` and calls `EncryptedSharedPreferences.create(…, LEGACY_FILE_NAME, …)` on every call. It runs unconditionally in `saveCredentials` (line 212), which runs on every login via `UserSessionManager.kt:34`, and in `clearCredentials` (line 311). It also runs in `getUserName`/`getPassword` (lines 238, 277) whenever the value is missing, and those are called from `TransactionSyncManager`, `UploadToShelfService` and `UserRepositoryImpl`. Each call costs a Keystore round-trip and a Tink keyset load. It also creates the legacy `secure_prefs.xml` on devices that never had one, so the migration check never goes away. The non-legacy store is already cached in `cachedSecureStore` (lines 29 and 145-149), and `buildSecureStore` already checks that a file exists before migrating (line 82).

files:
- `…/myplanet/utils/SecurePrefs.kt`: `getLegacyEncryptedPrefs` plus one new `@Volatile` field next to line 29.
- `…/test/utils/SecurePrefsTest.kt`, to add a test.
- leave alone:
  - `buildSecureStore`, `getAead`, `performMigration` and the public API signatures;
  - `services/UserSessionManager.kt`;
  - `TransactionSyncManager.kt` and `UserRepositoryImpl.kt` (open PRs).

steps:
1. At the top of `getLegacyEncryptedPrefs`, build `File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_FILE_NAME.xml")` the same way line 82 does, and return `null` when it does not exist.
2. Cache a successfully created instance in `@Volatile private var cachedLegacyPrefs: SharedPreferences?`. Use the same double-checked `synchronized(this)` pattern as `getSecureStore` (lines 145-149), and pass `context.applicationContext`.
3. Keep the existing `catch` that returns `null`. Do not cache a failure.
4. Add a Robolectric test in `SecurePrefsTest` (it already runs `@Config(sdk = [O_MR1])`). Call `SecurePrefs.saveCredentials(...)` and `SecurePrefs.clearCredentials(...)` on a clean context, then assert that `shared_prefs/secure_prefs.xml` was not created.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.SecurePrefsTest"` passes, and `UserSessionManagerTest` still passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - a fresh install can log in, log out and log in again with "remember me";
  - an upgrade from a build that still has `secure_prefs.xml` keeps the saved username and password after the first launch.

size budget: ~15 changed lines in production code plus ~20 test lines; 2 files.

out of scope: do not delete the legacy file and do not change encryption schemes. No new public API.

---

### 5. stop the chat history from reloading on every return to STARTED (roadmap 3+7, moves 10)

context: `ChatViewModel` exposes `_refreshChatSignal = MutableSharedFlow<Unit>(replay = 1)` and seeds it in `init` (`ui/chat/ChatViewModel.kt:65-68`). `ChatHistoryFragment.setupRealtimeSync()` (`ui/chat/ChatHistoryFragment.kt:204-207`) collects that signal with `collectWhenStarted`, which is `repeatOnLifecycle(STARTED)` (`utils/FlowExtensions.kt:14-19`). Because of `replay = 1`, the same stale `Unit` is delivered again on every restart: returning from the detail pane, a notification shade pull, app switch, and so on. Each redelivery runs `loadChatHistoryScreenData`, which reloads planet news and the whole chat history and re-parses every shared news `viewIn` (lines 123-139). The replay is intentional: the test `refreshChatSignal delivers a missed update to a subscriber that attaches after the emit` (`ChatViewModelTest.kt:113`) needs it. So the fix has to keep missed updates while dropping repeats of updates already handled.

files:
- `…/myplanet/ui/chat/ChatViewModel.kt`: the `_refreshChatSignal` / `refreshChatSignal` declarations and the `init` block (lines 65-77).
- `…/myplanet/ui/chat/ChatHistoryFragment.kt`: `setupRealtimeSync()` (lines 204-207) plus one new field.
- `…/test/ui/chat/ChatViewModelTest.kt`: the three `refreshChatSignal …` tests (lines 84-127).
- leave alone:
  - `ChatDetailFragment.kt:668` and `DashboardActivity.kt:737`, which call `refreshChatHistory()` directly (DashboardActivity belongs to an open PR);
  - `loadChatHistoryScreenData` itself;
  - `ChatRepositoryImpl.kt` (open PR).

steps:
1. Replace the replaying `SharedFlow<Unit>` with a generation counter:
   - `private val _refreshChatSignal = MutableStateFlow(0)` and `val refreshChatSignal: StateFlow<Int>`;
   - drop the `tryEmit` in `init`, since the initial value 0 is the first load;
   - in the realtime collector, replace `emit(Unit)` with `_refreshChatSignal.update { it + 1 }`.
2. In `ChatHistoryFragment`, add `private var lastHandledRefreshGeneration = -1`. In `setupRealtimeSync()`, call `refreshChatHistory()` only when the collected generation differs from that field, then store it.
3. Remove imports that are no longer used (`MutableSharedFlow` / `asSharedFlow` stay only if `_shareResult` still uses them, and it does).
4. Rewrite the three tests to assert on generations:
   - a cold ViewModel exposes 0;
   - a `chats` update with `shouldRefreshUI` moves the value to 1;
   - a late subscriber that attaches after an emit still reads the latest generation (1).

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.*"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - open Chat; the history loads once;
  - open a conversation and go back, or background and restore the app; there is no second load (no list flicker, and one `getChatHistoryForUser` call in a debug log);
  - a sync that brings new chats while the screen is in the background still refreshes the list when you come back.

size budget: ~15 changed lines in production code plus ~25 test lines; 3 files.

out of scope: do not move `extractSharedViewInIds` off the main thread in this task, and do not change `ChatHistoryAdapter`.

---

### 6. bound the text-viewer file read and parse Markdown off the main thread (roadmap 7+6)

context: `ResourceViewerFragment.setupTextViewer()` (`ui/viewer/ResourceViewerFragment.kt:587-615`) calls `file.readText()` (line 598) on the whole file before truncating to `MAX_TEXT_VIEWER_CHARS = 500_000` (line 723). A multi-megabyte text resource is therefore fully loaded into memory and copied. For Markdown, it then calls `MarkdownUtils.setMarkdownText(textContent, text)` (line 609). That runs Markwon's parse and span building for up to 500k chars on the main thread (`utils/MarkdownUtils.kt:66-70`), which can freeze the screen and risks an ANR on low-end devices. Markwon supports parsing off-thread with `toMarkdown` and applying the result on the main thread with `setParsedMarkdown`.

files:
- `…/myplanet/ui/viewer/ResourceViewerFragment.kt`: `setupTextViewer()` only.
- `…/myplanet/utils/MarkdownUtils.kt`: add two small functions next to `setMarkdownText` (line 66).
- `…/test/utils/MarkdownUtilsTest.kt`, to add a test.
- leave alone:
  - every other viewer branch in `ResourceViewerFragment` (PDF, image, audio/video, web);
  - `ResourceViewerViewModel`;
  - `buildMarkwon` and its plugin list;
  - all other `setMarkdownText` callers.

steps:
1. In `MarkdownUtils`, add:
   - `parseMarkdown(context: Context, markdown: String): Spanned`, which returns `create(context).toMarkdown(markdown)`;
   - `setParsedMarkdown(textView: TextView, spanned: Spanned)`, which calls `create(textView.context).setParsedMarkdown(textView, spanned)` and sets `textView.movementMethod = linkMovementMethod`, mirroring `setMarkdownText`.
2. In `setupTextViewer`, replace `file.readText()` with a bounded read inside the existing `withContext(dispatcherProvider.io)`:
   - open `file.bufferedReader().use { … }` and fill a `CharArray(MAX_TEXT_VIEWER_CHARS + 1)`;
   - call `read(buf, off, len)` in a loop until the buffer is full or `read` returns -1, because a single `read` may return fewer chars;
   - set `truncated` to whether more than `MAX_TEXT_VIEWER_CHARS` chars were read, and build the string from at most `MAX_TEXT_VIEWER_CHARS` chars.
3. For `ResourceType.MARKDOWN`, compute the `Spanned` with `withContext(dispatcherProvider.default) { MarkdownUtils.parseMarkdown(requireContext().applicationContext, text) }`. Then check `isAdded` again and apply it with `MarkdownUtils.setParsedMarkdown(textContent, spanned)`. Plain text keeps `textContent.text = text`.
4. Add a `MarkdownUtilsTest` case: `parseMarkdown(context, "**bold** text")` returns a `Spanned` whose `toString()` is `"bold text"`. Use the class's existing `@RunWith(RobolectricTestRunner::class)` setup.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.MarkdownUtilsTest"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - open a large `.md` resource (>1 MB); the screen stays responsive while it renders, and the "content truncated" toast still shows for files over 500k chars;
  - links in the rendered Markdown are still clickable;
  - a small `.txt` resource renders as before.

size budget: ~30 changed lines in production code plus ~15 test lines; 3 files.

out of scope: do not add paging or lazy loading for text, do not change `MAX_TEXT_VIEWER_CHARS`, and do not touch `VoicesAdapter`'s Markdown rendering.

---

### 7. move notification formatting and grouping off the main thread (roadmap 3+7, moves 10)

context: `NotificationsViewModel.loadNotifications` (`ui/notifications/NotificationsViewModel.kt:69-78`) launches in `viewModelScope`, which runs on Main. It maps every payload through `formatNotification` (line 267), which does string formatting and date parsing per item on the main thread. It never cancels a previous load, and `NotificationsFragment` calls `loadNotifications` from three places (`NotificationsFragment.kt:77, 87, 173`). So switching filters quickly can let a stale, slower load overwrite the newer list. `groupedNotifications` and `groupedItems` (lines 57-65) run `buildNotificationGroups` and `buildGroupedList` on Main for every selection or expansion change, because there is no `flowOn`. The ViewModel does not inject `DispatcherProvider`, although the style guide requires it.

files:
- `…/myplanet/ui/notifications/NotificationsViewModel.kt`: the constructor (lines 28-31), `groupedNotifications`, `groupedItems` and `loadNotifications`.
- `…/test/ui/notifications/NotificationsViewModelTest.kt`: the constructor call at line 46, plus one test.
- leave alone:
  - `NotificationsFragment.kt`;
  - `NotificationsAdapter.kt`;
  - `repository/NotificationsRepositoryImpl.kt` (already batched);
  - `utils/NotificationUtils.kt` (open PR).

steps:
1. Add `private val dispatcherProvider: DispatcherProvider` (`org.ole.planet.myplanet.utils.DispatcherProvider`) as the last constructor parameter. Hilt already provides it through `DispatcherModule`.
2. Add `.flowOn(dispatcherProvider.default)` before `stateIn` on both `groupedNotifications` and `groupedItems`.
3. In `loadNotifications`:
   - keep a `private var loadJob: Job?` and call `loadJob?.cancel()` before `viewModelScope.launch`;
   - wrap the `payloads.map { formatNotification(…) }` in `withContext(dispatcherProvider.default)`;
   - assign `_notifications.value` and `_unreadCount.value` after that, back on Main.
4. In the test, build the ViewModel with `TestDispatcherProvider(testDispatcher)`, which already exists at `…/test/utils/TestDispatcherProvider.kt`. Add a test: two back-to-back `loadNotifications` calls with different filters, where the first repository call is slower (`coAnswers { delay(…) }`), end with the second call's result in `notifications.value`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.notifications.*"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - open Notifications with 100+ entries; filter chips switch without a stall, and the list always matches the last filter tapped;
  - group expand/collapse and multi-select still work.

size budget: ~15 changed lines in production code plus ~25 test lines; 2 files.

out of scope: do not change `formatNotification` output or the grouping rules. No repository changes.

---

### 8. stop deep-copying the whole exam (questions included) in StepExam.insertCourseStepsExams (roadmap 1+7, moves 9)

context: `StepExam.insertCourseStepsExams` (`model/StepExam.kt:42-66`) starts with `val kExam = exam.toKotlinx().jsonObject` (line 43). `toKotlinx()` (`utils/GsonUtils.kt:48-53`) recursively copies the entire Gson tree, including the full `questions` array with every choice. The function then reads only top-level primitives, plus `questions` just for `.size` (line 60). It runs once per exam and survey on every course and survey sync, so large question banks get copied only to be thrown away. Converting only the top-level fields keeps the exact `JsonUtils` reading semantics while skipping the heavy subtree. It also shrinks the Gson surface of the model, a step toward the platform-free core (9).

files:
- `…/myplanet/model/StepExam.kt`: `insertCourseStepsExams(myCoursesID, stepId, exam, parentId)` only.
- `…/test/model/StepExamTest.kt`, to add a test.
- leave alone:
  - `serializeExam` (lines 77-102);
  - `model/ExamQuestion.kt` (it has the same pattern at line 69; that is a separate follow-up);
  - `SurveysRepositoryImpl.kt` and `CoursesRepositoryImpl.kt` (callers, open PRs);
  - `StepExamBenchmarkTest.kt`.

steps:
1. Replace line 43 with a shallow conversion:
   - build a new Gson `JsonObject` from `exam.entrySet()` that skips the `"questions"` key;
   - call `.toKotlinx().jsonObject` on that smaller object.
   All `JsonUtils.getString/getLong/getInt/getBoolean` reads stay exactly as they are.
2. Replace line 60 with `myExam.noOfQuestions = GsonUtils.getJsonArray("questions", exam).size()`. It reads from the original Gson object and returns an empty array for missing or non-array values, just like `JsonUtils.getJsonArray` (`utils/JsonUtils.kt:48-49`).
3. Remove the imports that are no longer used.
4. Add a `StepExamTest` case: an exam with 3 questions, `teamShareAllowed = true`, `totalMarks = 7` and `type = "survey"` maps to `noOfQuestions == 3`, `isTeamShareAllowed == true`, `totalMarks == 7` and `type == "survey"`. Add one more case: an exam whose `questions` is a string (malformed) maps to `noOfQuestions == 0`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.model.StepExam*"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually: after a full sync, course exams and team surveys show the same question counts and pass marks as before, and taking an exam still works.

size budget: ~8 changed lines in production code plus ~30 test lines; 2 files.

out of scope: do not rewrite the reads to `GsonUtils` getters (their edge cases differ). Do not touch `ExamQuestion`, `MyTeam` or the other `fromJson` helpers.

---

### 9. check foreground eligibility once per DownloadWorker run, not every 500 ms (roadmap 7+5)

context: `DownloadWorker.downloadFileBody` calls `showProgressNotification` every `NOTIFICATION_UPDATE_INTERVAL_MS` while bytes stream (`services/DownloadWorker.kt:138-145`). Each call runs `DownloadUtils.canStartForegroundService(context)` (line 166). That function goes through `isAppInForeground` (`utils/DownloadUtils.kt:224-233`), which makes a binder IPC call to `ActivityManager.runningAppProcesses` and scans the process list. So a long download does two IPC round-trips a second on the IO thread, on top of the file write. Once `setForeground` has succeeded, the worker stays a foreground worker for the rest of its run, so re-checking eligibility after that gives nothing.

files:
- `…/myplanet/services/DownloadWorker.kt`: `showProgressNotification` (lines 157-179) plus one new private field.
- `…/test/services/DownloadWorkerTest.kt`, to add a test.
- leave alone:
  - `utils/DownloadUtils.kt` (`canStartForegroundService` stays as is for its other callers);
  - `services/DownloadService.kt` (task 2);
  - the progress-throttle logic in `downloadFileBody`.

steps:
1. Add `private var isForegroundPromoted = false` to the class.
2. In `showProgressNotification`, change the guard to `if (isForegroundPromoted || DownloadUtils.canStartForegroundService(context))`.
3. Set `isForegroundPromoted = true` right after `setForeground(...)` succeeds, before the `return`.
4. In the `catch`, set `isForegroundPromoted = false`, so the next tick falls back to `notificationManager.notify` without a fresh check that is sure to fail again.
5. Add a `DownloadWorkerTest` case:
   - `mockkObject(DownloadUtils)` with `canStartForegroundService` returning `true`;
   - run a download whose body is big enough for at least 3 progress ticks;
   - verify that `canStartForegroundService` was called exactly once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.DownloadWorkerTest"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - start a large resource download from the background path (auto-download after sync); the ongoing notification still updates its percentage and ends on "downloaded n/n";
  - with the app in the foreground, the worker still shows as a foreground notification.

size budget: ~6 changed lines in production code plus ~25 test lines; 2 files.

out of scope: do not change `NOTIFICATION_UPDATE_INTERVAL_MS` or the notification layout. No changes to `DownloadUtils`.

---

### 10. parse retry payloads straight to kotlinx instead of Gson → kotlinx (roadmap 5+1, moves 9)

context: `RetryRepositoryImpl.executeOperation` parses the stored payload with Gson, `JsonParser.parseString(operation.serializedPayload).asJsonObject` (`repository/RetryRepositoryImpl.kt:74`). Sixteen lines later it deep-copies the result into kotlinx with `payload.toKotlinx().jsonObject` (line 90), because `ApiInterface.putDoc` / `postDoc` take a kotlinx object. The Gson tree is used for nothing else. So every retried upload (submissions, photos, news) is parsed and then fully copied again, and the file keeps a Gson dependency it does not need. That dependency is one of the things blocking this retry logic from moving into a platform-free core.

files:
- `…/myplanet/repository/RetryRepositoryImpl.kt`: `executeOperation` (lines 69-100 and following) and the import block (lines 3-18).
- `…/test/repository/RetryRepositoryImplTest.kt`, to add a test.
- leave alone:
  - `services/retry/RetryQueue.kt`, `services/retry/RetryQueueWorker.kt`, `data/room/dao/RetryDao.kt` and `model/RetryOperation.kt`;
  - `utils/GsonUtils.kt` (`toKotlinx` has many other callers).

steps:
1. Replace the Gson parse at line 74 with `Json.parseToJsonElement(operation.serializedPayload).jsonObject` (`kotlinx.serialization.json.Json`). A non-object payload makes `.jsonObject` throw `IllegalArgumentException`, which the existing `catch (e: Exception)` already turns into `TerminalFailure("Invalid payload", null)`.
2. Delete line 90 (`val kPayload = payload.toKotlinx().jsonObject`) and pass the parsed object to `putDoc` / `postDoc` directly. Rename it so the call sites keep reading naturally.
3. Remove the imports that are no longer used: `com.google.gson.JsonParser` and `org.ole.planet.myplanet.utils.toKotlinx`.
4. Add a test next to `executeOperation invalid payload returns TerminalFailure and records failure` (line 231): a payload of `"[1,2]"` (valid JSON, not an object) also returns `TerminalFailure` and calls `recordFailedAttempt`. The existing 2xx, 409, 4xx and 5xx tests must still pass unchanged.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.RetryRepositoryImplTest"` passes.
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.retry.*"` passes.
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually: submit a survey offline, go online and wait for `RetryQueueWorker`. The submission reaches the server once, and the retry queue count drops to 0.

size budget: ~6 changed lines in production code plus ~15 test lines; 2 files.

out of scope: do not batch `RetryQueue.queueFailedOperation` and do not replace `android.util.Log` in this file. No DAO changes.

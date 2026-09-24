### 1. chunk the notification IN-list lookups so notification sync stops failing on API 26–30 (roadmap 1+7)

rating: 94 (evidence 5 · impact 5 · feasibility 4) · proposed by: claude·repo#1 (0.625), devin·repo#7 (0.375)

context: `TransactionSyncManager.syncDb` pages `notifications` at `else -> 1000` (`services/sync/TransactionSyncManager.kt:208-213`). `NotificationsRepositoryImpl.bulkInsertFromSync` (`:507-521`) passes the whole page to `notificationDao.getByIds(parsedList.map { it.id })`, which is one `SELECT * FROM notifications WHERE id IN (:ids)` (`data/room/dao/NotificationDao.kt:39-40`). That binds up to 1000 variables. Android 8–11 (API 26–30) ship SQLite older than 3.32, where the limit is 999. `syncDb` catches the exception and returns 0, so on those devices notifications silently stop syncing once the server holds 1000 or more. The same unchunked pattern is in `getIdsByIds` (`:42-43`), `markAsRead(ids, createdAt)` (`:48-49`) and `deleteByIds` (`:102-103`), reached from `markNotificationsAsRead` (`:123-129`) and `deleteNotifications` (`:498-505`). `markSynced` in the same DAO already shows the house shape: a `@Transaction` default method that chunks by 900.

files:
- main: `data/room/dao/NotificationDao.kt`, `repository/NotificationsRepositoryImpl.kt` (`bulkInsertFromSync`, `markNotificationsAsRead`, `deleteNotifications`)
- test: `repository/NotificationsRepositoryImplTest.kt`, `data/room/dao/NotificationDaoTest.kt`
- leave alone: `TransactionSyncManager.kt` (PR 15808), the `NotificationsRepository` interface, the single-id `markAsRead(id: String)` overload

steps:
1. Rename the four list `@Query` methods to `*Internal`. Add same-named `@Transaction` default methods that `chunked(900)` and `flatMap`/`sumOf` over them, so every existing caller compiles unchanged. `markAsRead` binds `createdAt` too: 900 + 1 is still under the limit.
2. Optionally (as claude·repo#1 suggests), have `bulkInsertFromSync` read a `SELECT id, isRead FROM notifications WHERE needsSync = 1 AND id IN (:ids)` projection instead of full rows. It only reads `needsSync` and `isRead`.
3. Delete `getByIds` if nothing calls it after step 2.
4. Add a `NotificationDaoTest` case that inserts 1,200 rows and asserts `markAsRead`/`deleteByIds` affect all of them. Update the repository-test stubs for any renamed DAO method.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest" --tests "*NotificationDaoTest"` passes, and the full suite is green.
- On an API 26–30 emulator, a sync with more than 999 server notifications finishes without `SQLiteException`. The Robolectric host SQLite is newer, so the unit test proves chunk correctness, not the limit.
- Locally read notifications stay read after a re-sync, and "mark all as read" still clears the badge.

size budget: ~60–90 changed lines, 4 files.

out of scope / notes: No page-size, schema or interface change. The same bug class exists in `FeedbackRepositoryImpl.insertFeedbackList` → `feedbackDao.getByIds` (1000-doc page too). That file is locked by PR 15808, so do it as a follow-up.

---

### 2. bound the text-viewer file read and parse Markdown off the main thread (roadmap 7+6)

rating: 74 (evidence 5 · impact 3 · feasibility 4) · proposed by: claude·perf#6 (0.625), kimi·perf#3 (0.375)

context: `ResourceViewerFragment.setupTextViewer()` (`ui/viewer/ResourceViewerFragment.kt:587-615`) calls `file.readText()` on the whole file, then truncates to `MAX_TEXT_VIEWER_CHARS = 500_000` (`:723`). A 50 MB resource materialises ~100 MB of UTF-16 before 99% is thrown away. For Markdown it then runs `MarkdownUtils.setMarkdownText` (`utils/MarkdownUtils.kt:66-70`), which is Markwon parse plus span building for up to 500k chars on the main thread. Markwon 4.6.2 has `toMarkdown()` and `setParsedMarkdown()` for off-thread parsing.

files:
- main: `ui/viewer/ResourceViewerFragment.kt` (`setupTextViewer` only), `utils/MarkdownUtils.kt` (two helpers next to `setMarkdownText`)
- test: `utils/MarkdownUtilsTest.kt`
- leave alone: the PDF/image/media/web branches, `ResourceViewerViewModel`, `buildMarkwon`, the other `setMarkdownText` callers

steps:
1. Inside the existing `withContext(dispatcherProvider.io)`, replace `readText()` with a bounded read. Use `bufferedReader().use { }` into a `CharArray(MAX_TEXT_VIEWER_CHARS + 1)` and loop `read(buf, off, len)` until full or -1, because one `read` may return fewer chars. Set `truncated = n > MAX_TEXT_VIEWER_CHARS`. Always do the bounded read; the byte-budget branch in kimi·perf#3 is unnecessary.
2. Add `MarkdownUtils.parseMarkdown(context, markdown): Spanned` (`create(context).toMarkdown(...)`) and `setParsedMarkdown(textView, spanned)`, which also sets `linkMovementMethod`.
3. For `ResourceType.MARKDOWN`: capture `requireContext().applicationContext` before the switch, parse in `withContext(dispatcherProvider.default)`, re-check `isAdded`, then apply on Main. Plain text keeps `textContent.text = text`.
4. Add a `MarkdownUtilsTest` case: `parseMarkdown(ctx, "**bold** text").toString() == "bold text"`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*MarkdownUtilsTest"` passes, and the full suite is green.
- A >1 MB `.md` stays responsive while rendering and shows the "content truncated" toast when over 500k chars. Links stay clickable, and a small `.txt` renders as before.

size budget: ~30 changed lines in main plus ~15 test lines, 3 files.

out of scope / notes: The layout of a 500k-char `Spanned` still runs on Main, so the gain is the parse and the memory spike, not the whole render. No paging, and no change to `MAX_TEXT_VIEWER_CHARS`.

---

### 3. return typed upload results from PersonalsRepository.uploadPersonal (roadmap 3+9, also fixes a bug)

rating: 70 (evidence 4 · impact 3 · feasibility 4) · proposed by: grok·repo#4 (0.714), gemini·repo#2 (0.286)

context: `PersonalsRepository.uploadPersonal(personal): String` (`repository/PersonalsRepository.kt:27`) returns free-form English messages (`PersonalsRepositoryImpl.kt:115,125,145,150,156,159`) and logs with `android.util.Log.w` (`:144,149,158`). `PersonalsViewModel.uploadPersonal` (`ui/personals/PersonalsViewModel.kt:45-46`) wraps every result in `UploadState.Success(result)`. So today every failure string ("Unable to upload resource: …") reaches the Fragment as a success. The ViewModel lives in `ui/personals/`, not `ui/life/` as gemini·repo#2 says.

files:
- main: `repository/PersonalsRepository.kt`, `repository/PersonalsRepositoryImpl.kt`, `ui/personals/PersonalsViewModel.kt`
- test: `repository/PersonalsRepositoryImplTest.kt`, `ui/personals/PersonalsViewModelTest.kt` (`:79, 93, 106, 117` stub `returns "uploaded-id"`)
- leave alone: `PersonalDao`, `UploadRepository*`, DI modules, all `strings.xml` (15 open PRs touch them)

steps:
1. Declare `sealed interface PersonalUploadResult { Success(id); AlreadyUploaded; DocumentFailed(code, cause); AttachmentFailed(code, cause) }` in `PersonalsRepository.kt`. Failure cases carry the HTTP code and `Throwable?`, so stack traces aren't lost.
2. Return it from the Impl. Keep one `Log.w(TAG, …, e)` per failure path, as `docs/CODE_STYLE_GUIDE.md` prescribes, or move the logging into the VM. Don't silently drop the causes.
3. In the VM, map `Success`/`AlreadyUploaded` → `UploadState.Success` and the failures → `UploadState.Error`, reusing the message texts the repository produces today. That way no new string resources are needed.
4. Update both test classes. Add one VM test proving a failure now yields `UploadState.Error`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*PersonalsRepositoryImplTest" --tests "*PersonalsViewModelTest"` passes, and the full suite is green.
- A failed personal upload shows the error path, not the success toast.

size budget: ~60–90 changed lines, 5 files.

out of scope / notes: Sequence this with tasks 26 (TimeProvider) and 43 (dead surface); all three edit `PersonalsRepositoryImpl.kt`.

---

### 4. move monthly login aggregation from ActivitiesFragment into ActivitiesViewModel (roadmap 3+10)

rating: 66 (evidence 5 · impact 2 · feasibility 5) · proposed by: grok·repo#10 (1)

context: `ActivitiesFragment` collects `viewModel.offlineLogins` and runs `computeMonthlyCounts` (`ui/dashboard/ActivitiesFragment.kt:38-59`), a fold on `dispatcherProvider.default`, before `renderChart`. `DispatcherProvider` is injected into the Fragment only for this (`:21,29`). `ActivitiesViewModel` exposes only raw logins (`ActivitiesViewModel.kt:22-25`). The tests sit on the Fragment (`ActivitiesFragmentTest.kt:36,46`).

files:
- main: `ui/dashboard/ActivitiesFragment.kt`, `ui/dashboard/ActivitiesViewModel.kt`
- test: `ui/dashboard/ActivitiesFragmentTest.kt`, `ui/dashboard/ActivitiesViewModelTest.kt` (constructor at `:35,54`)
- leave alone: `ActivitiesRepository*` (PR 14427), layouts, MPAndroidChart

steps:
1. Inject `DispatcherProvider` (and `TimeProvider` for the one-year window) into the VM. Expose `monthlyLoginCounts: StateFlow<Map<Int, Int>>` derived from `offlineLogins`.
2. Move `computeMonthlyCounts` into the VM unchanged, including its current MONTH-only keying.
3. Have the Fragment collect the counts, render the chart or empty state only, and drop its `DispatcherProvider` injection.
4. Move the two Fragment tests into `ActivitiesViewModelTest`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Activities*"` passes, and the full suite is green.
- The Fragment has no aggregation logic, and the chart still groups the last year's logins by month.

size budget: ~50–90 changed lines, 4 files.

out of scope / notes: No chart styling change.

---

### 5. stop opening profile-image URLs as local files in UserEntity.serialize (roadmap 5+7)

rating: 66 (evidence 5 · impact 2 · feasibility 5) · proposed by: claude·perf#1 (1)

context: `UserEntity.serialize()` calls `encodeImageToBase64(userImage)` every time (`model/UserEntity.kt:67`). For synced users, `userImage` is the http(s) URL built by `UrlUtils.getUserImageUrl` (`:168`). The non-`content://` branch then runs `File(imagePath).inputStream()` (`:124`), which throws `FileNotFoundException` and calls `printStackTrace()` (`:132`). `serialize()` runs per item on upload paths (`TeamTask.serialize`, `News.createNews`, submissions, ratings), so every upload batch pays one failed open plus one stack trace per item. The result is `null` either way.

files:
- main: `model/UserEntity.kt` (`encodeImageToBase64` only)
- test: `model/UserEntityEncodeImageTest.kt`
- leave alone: `serialize()` field layout, `addImageUrl()` and every caller

steps:
1. After the null/empty guard, return `null` for paths starting with `http://` or `https://` (ignore case).
2. In the file branch, return `null` when `!File(imagePath).isFile`, without opening a stream.
3. Leave the `content://` branch and the `catch` block as they are.
4. Add two tests: an https `_users/…/img.jpg` URL and a missing path both return `null`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserEntity*"` passes, and the full suite is green.
- After uploading a team task or rating for a synced user with a profile picture, logcat shows no `FileNotFoundException` from `UserEntity`. A `content://` photo still uploads as `_attachments.img`.

size budget: ~6 main lines plus ~20 test lines, 2 files.

out of scope / notes: No payload-shape change, and no `serialize(includeImage)` flag.

---

### 6. stop ResourcesRepository leaking the Room LibraryTitleProjection, and trim its internal-only surface (roadmap 1+9)

rating: 66 (evidence 5 · impact 2 · feasibility 5) · proposed by: claude·repo#7 (0.625), grok·repo#1 (0.375)

context: `ResourcesRepository.getLibraryTitles(): List<LibraryTitleProjection>` (`repository/ResourcesRepository.kt:7,64`) exposes a DAO type (`data/room/dao/MyLibraryDao.kt:183`). The Impl returns it unchanged (`ResourcesRepositoryImpl.kt:83-84`), and it leaks into the only UI imports of the Room package: `ui/user/AchievementViewModel.kt:15,86` and `ui/user/EditAchievementFragment.kt:44,501`. Only `.id` and `.title` are used. Five interface methods have no caller outside the Impl and its test: `getMyLibrary`, `resolveLibraryItemByResourceId`, `updateUserLibrary`, `clearResourceListCache`, `getResourceTitlesMap`. `observeOpenedResourceIds` is `suspend fun …: Flow` over full `ResourceActivity` rows (`ResourcesRepositoryImpl.kt:544-548`).

files:
- main: `repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`, `ui/user/AchievementViewModel.kt`, `ui/user/EditAchievementFragment.kt`, optionally `data/room/dao/ResourceActivityDao.kt`
- test: `ui/user/AchievementViewModelTest.kt` (`:22, :145`), `repository/ResourcesRepositoryImplTest.kt` if you touch `observeOpenedResourceIds`
- leave alone: `MyLibraryDao.kt` (the projection stays the DAO's return type), `ResourcesViewModel.kt` (PRs 13415, 17187), `ActivitiesRepository*` (PR 14427), and `RemovedLogDao` usage

steps:
1. Add `data class LibraryTitle(val id: String, val title: String?)` next to `LibraryWithMetadata` in `ResourcesRepository.kt`, and return `List<LibraryTitle>`. Map it in the Impl, switch the two UI files and the test, and remove the `data.room.dao` import from the interface.
2. Drop the five internal-only methods from the interface. Keep them as `internal` (not `override`) in the Impl so the Impl tests still compile.
3. Optional (grok·repo#1): add `ResourceActivityDao` `SELECT resourceId … ` as a Flow query and make `observeOpenedResourceIds` non-suspend via `flow { emit(getUserById(...)) }.flatMapLatest { … }`. The Impl needs the suspend user lookup first.
4. Do **not** replace the `removedLogDao` batch calls with `ActivitiesRepository.markResourceAdded/Removed`. Those are per-item, and the batching is deliberate (commit 4345d871a; `ResourcesRepositoryImplTest.kt:895` asserts `insertAll` once for 50 rows).

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*AchievementViewModelTest" --tests "*ResourcesRepository*"` passes, and the full suite is green.
- `grep -rn "data.room" app/src/main/java/org/ole/planet/myplanet/ui` is empty.
- Edit Achievement → "add resources" still lists titles and pre-checks earlier picks.

size budget: ~30–60 changed lines, 5 files.

out of scope / notes: No DAO query change beyond the optional step 3.

---

### 7. add DictionaryDao unit tests (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: gemini·repo#8 (1)

context: `data/room/dao/` has 17 DAO tests, but none for `DictionaryDao`. Its `count()`, `insertAll()` and `findByWord` exist. There is no NOCASE index on the dictionary table, so `findByWord` is an unindexed scan. gemini·repo#8 claims an index exists; it doesn't.

files:
- test (new): `data/room/dao/DictionaryDaoTest.kt`

steps:
1. Copy the in-memory Robolectric Room setup from `PersonalDaoTest.kt:29-45`.
2. Test `insertAll` then `count()`, `findByWord` exact and case-insensitive matches, and a miss returning null or empty.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*DictionaryDaoTest"` passes.

size budget: ~60–80 lines, 1 file.

out of scope / notes: No production change. Don't assert index usage.

---

### 8. unpin ResourcesFilterFragmentTest from Robolectric SDK 32 (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: codex·perf#6 (1)

context: `ui/resources/ResourcesFilterFragmentTest.kt:15` has `@Config(sdk = [32], application = Application::class)` for a smoke test that opens the dialog and finds `iv_close`. The default sandbox is SDK 36: targetSdk is 36 and `robolectric.properties` sets no sdk. CI shards classes by `path.hashCode() % 2` (`app/build.gradle:41-50`), and this is the **only** SDK-32 class in shard 1, so dropping the pin removes a whole sandbox build from that shard. `ResourcesFragmentActionTest` already opens this bottom sheet unpinned on 36. The pin was added with the file and has no stated reason.

files:
- test: `ui/resources/ResourcesFilterFragmentTest.kt` (class `@Config` only)
- leave alone: `ResourcesFilterFragment.kt` (PR 17187)

steps:
1. Remove `sdk = [32]` and keep `application = Application::class`.
2. Run the class, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ResourcesFilterFragmentTest"` and the full suite pass.

size budget: 1 line, 1 file.

out of scope / notes: Also drop the row from the pin table in `docs/TESTING.md:143-150`.

---

### 9. unpin ChatAdapterTest from Robolectric SDK 33 (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: codex·perf#5 (1)

context: `ui/chat/ChatAdapterTest.kt:20` has `@Config(sdk = [33], …)` for clipboard-caching tests. It is the only SDK-33 class in CI shard 2; the other 33 pin, `CoursesItemUtilsTest`, hashes to shard 1. Dropping the pin therefore removes the 33 sandbox from that shard. Clipboard state goes through `ShadowClipboardManager`, which resets per test.

files:
- test: `ui/chat/ChatAdapterTest.kt` (class `@Config` only)

steps:
1. Remove `sdk = [33]` and keep `application = Application::class`.
2. Run the class, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ChatAdapterTest"` and the full suite pass.

size budget: 1 line, 1 file.

out of scope / notes: No clipboard or adapter change. If task 54 (ChatAdapter animation state) lands first, rebase.

---

### 10. add RemovedLogDao unit tests (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: gemini·repo#9 (1)

context: `RemovedLogDao` has no test file. Its `deleteByTypeUserAndDoc`, `deleteByTypeUserAndDocsChunked` (a `@Transaction` chunked delete) and `getRemovedDocIds` exist. The chunked delete backs the batched shelf add in `ResourcesRepositoryImpl`.

files:
- test (new): `data/room/dao/RemovedLogDaoTest.kt`

steps:
1. Use the in-memory Robolectric Room setup from `PersonalDaoTest.kt`.
2. Test `deleteByTypeUserAndDoc` with null and non-null `userId`, `deleteByTypeUserAndDocsChunked` across a chunk boundary (e.g. 1,200 ids) including the `userId IS NULL` path, and `getRemovedDocIds`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RemovedLogDaoTest"` passes.

size budget: ~80 lines, 1 file.

out of scope / notes: The host SQLite allows 32,766 variables, so this proves the chunks are correct, not the 999-limit safety.

---

### 11. route ActivitiesRepositoryImpl's user lookup through UserRepository (roadmap 1+4)

rating: 62 (evidence 5 · impact 2 · feasibility 4) · proposed by: devin·repo#2 (1)

context: `ActivitiesRepositoryImpl` injects `userDao: UserDao` (`:62`) only for `userDao.getByName(userId)` in `logCourseVisit` (`:100`), reading the user table directly. It already injects `userRepository: Lazy<UserRepository>` (`:52`) and calls `getUserById` through it (`:249`). `UserRepository.getUserByName` exists (`UserRepository.kt:32`).

files:
- main: `repository/ActivitiesRepositoryImpl.kt`
- test: `repository/ActivitiesRepositoryImplTest.kt` (constructor, and the `userDao` stub)

steps:
1. Replace `userDao.getByName(userId)` with `userRepository.get().getUserByName(userId)`.
2. Remove the `userDao` constructor parameter and its import.
3. Update the test's constructor and stubs.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ActivitiesRepositoryImplTest"` passes, and the full suite is green.
- Course visits still log with `parentCode`/`planetCode` populated.

size budget: ~10 lines, 2 files.

out of scope / notes: `ActivitiesRepositoryImpl.kt` is in PR 14427 (a draft "Course streak", last touched 2026-08-24). It's a trivial rebase either way.

---

### 12. check foreground eligibility once per DownloadWorker run, not on every 500 ms progress tick (roadmap 7)

rating: 62 (evidence 5 · impact 2 · feasibility 4) · proposed by: claude·perf#9 (0.714), grok·perf#8 (0.286)

context: `DownloadWorker.downloadFileBody` calls `showProgressNotification` every `NOTIFICATION_UPDATE_INTERVAL_MS = 500L` while bytes stream (`services/DownloadWorker.kt:138-145, 212`). Each call runs `DownloadUtils.canStartForegroundService(context)` (`:166`), which calls `isAppInForeground` (`utils/DownloadUtils.kt:224-233`). That's a binder IPC to `ActivityManager.runningAppProcesses` plus a process-list scan, twice a second, for the whole download. Once `setForeground` succeeds, the worker stays foreground, so re-checking gains nothing.

files:
- main: `services/DownloadWorker.kt` (`showProgressNotification` plus one field)
- test: `services/DownloadWorkerTest.kt` (already spies `setForeground` at `:82` and `mockkObject(DownloadUtils)` at `:84`)
- leave alone: `utils/DownloadUtils.kt`, `services/DownloadService.kt`, the throttle logic

steps:
1. Add `private var isForegroundPromoted = false`.
2. Change the guard to `if (isForegroundPromoted || DownloadUtils.canStartForegroundService(context))`, and set the flag right after `setForeground` succeeds.
3. In the `catch`, leave the flag `false` so the next tick falls back to `notify`.
4. Add a test: a body long enough for 3 or more ticks, with `canStartForegroundService` called exactly once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*DownloadWorkerTest"` passes, and the full suite is green.
- A long background download still updates its percentage and ends on "downloaded n/n".

size budget: ~6 main lines plus ~25 test lines, 2 files.

out of scope / notes: grok·perf#8's alternative is to swap `isAppInForeground` for `ProcessLifecycleOwner` (the precedent is `Utilities.kt:37-39`; `lifecycle-process` is already a dependency). It would also cheapen the check. But it changes semantics: `ProcessLifecycleOwner` dispatches ON_STOP ~700 ms late, and `IMPORTANCE_FOREGROUND` is not the same as "an activity is started". Keep it as a separate dedupe of the three `isAppInForeground` copies (`DownloadUtils`, `AutoSyncWorker:159`, `Utilities`) if wanted.

---

### 13. page the resource sync with a startkey cursor instead of CouchDB skip (roadmap 7+5)

rating: 60 (evidence 3 · impact 4 · feasibility 2) · proposed by: grok·perf#10 (1)

context: `SyncManager`'s resource pull (`services/sync/SyncManager.kt:313-405`) requests `resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip`. CouchDB `skip` costs O(skip) on the server, so late batches of a large catalogue get progressively slower. `skip` paging can also miss or duplicate ids if the server changes mid-sync. The loop feeds `removeDeletedResources` afterwards, where a missed id could delete a valid local resource. On failure the loop advances with `skip += batchSize` and sets `hadBatchFailure`.

files:
- main: `services/sync/SyncManager.kt` (the resource pull loop only)
- test: `services/sync/SyncManagerTest.kt`
- leave alone: `TransactionSyncManager.kt` (PR 15808), `AdaptiveBatchProcessor`

steps:
1. Page with `startkey=<URL-encoded JSON string of lastId>&skip=1&limit=N`, and send the first page without `startkey`. grok·perf#10's `startkey_docid` alone is meaningless on `_all_docs`, and an unquoted key gets a 400.
2. Loop until `rows.size() < N`, counting processed rows for progress. Keep `total_rows` from the `limit=0` call only as the progress denominator, and keep writing `ResourceSyncPosition` as the processed count.
3. On a failed batch, retry the same cursor up to K times, then break with `hadBatchFailure = true`. Advancing isn't possible without a cursor. Don't retry forever: the existing test `resourceTransactionSync skips removeDeletedResources when batch failure occurs` must still pass.
4. Add tests for two-page continuation, the encoded startkey, the first-row-duplicate skip, and failure termination. Stub URLs with `contains("include_docs=true")`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SyncManagerTest"` passes, and the full suite is green.
- A full resource sync against a multi-page fixture inserts the same non-design docs, with no `skip=<n>` offsets in the URLs.

size budget: ~60 main plus ~80 test lines, 2 files.

out of scope / notes: Risk is high: it's the core sync loop. Verify against a real Planet server before merging. `TransactionSyncManager` uses the same `skip` pattern for every other table; that's a follow-up after PR 15808.

---

### 14. persist the DownloadService queue every N files instead of after every file (roadmap 7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·perf#2 (1)

context: `processDownloadQueue` calls `cleanupProcessedUrls()` after every file (`services/DownloadService.kt:164`). That copies both URL sets out of SharedPreferences and rewrites both with `putStringSet` (`:184-194`): O(N²) bytes for an N-file queue, on the app's main prefs file. Reads already filter against the in-memory `processedUrls` (`getNextUrl :640`, `getRemainingCount :181`), and re-trying an already-downloaded URL is cheap (`FileUtils.checkFileExist`, `:224`).

files:
- main: `services/DownloadService.kt` (`processDownloadQueue`, `cleanupProcessedUrls`, `onDestroy`, one companion constant)
- test: `services/DownloadServiceTest.kt`
- leave alone: `getNextUrl`, `onDownloadComplete`, `DownloadUtils.kt`, `DownloadWorker.kt`, the other DownloadService tests

steps:
1. Add `QUEUE_PERSIST_INTERVAL = 10` and a `processedSinceLastPersist` counter.
2. Split out a private `persistProcessedUrls()` holding the current write. `cleanupProcessedUrls` always updates `cachedRemainingCount`, and persists only when the counter reaches the interval.
3. Persist in the `nextUrl == null` branch before `stopSelf()`, and in `onDestroy()`. Snapshot `processedUrls.toSet()` there, since `currentJob` may still be adding on another thread.
4. Test: drive `cleanupProcessedUrls` 9 times, then once more. Verify `edit()` ran 0 times, then once. Don't count `putStringSet`: each persist calls it twice.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*DownloadService*"` passes, and the full suite is green.
- Killing the app mid-queue and restarting re-downloads only missing files.

size budget: ~25 main plus ~30 test lines, 2 files.

out of scope / notes: No move of queue storage into Room.

---

### 15. single UPDATE queries for enterprise report archive and image attach (roadmap 1+7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#8 (1)

context: `EnterprisesRepositoryImpl.archiveReport` (`:79-85`) and the private `attachTeamImage` (`:127-139`) go through `updateTeamEntityById` (`:140-146`). That loads the whole `teams` row, flips one or two fields and `@Upsert`s every column back, so a sync landing in between gets overwritten by the stale copy. The column names are confirmed in `model/MyTeam.kt`: `_id`, `status`, `imageName`, and `@ColumnInfo(name = "isUpdated") var updated`.

files:
- main: `data/room/dao/TeamDao.kt`, `repository/EnterprisesRepositoryImpl.kt`
- test: `repository/EnterprisesRepositoryImplTest.kt`, `data/room/dao/TeamDaoTest.kt`
- leave alone: `model/MyTeam.kt` (PR 15951), `TeamsRepositoryImpl.kt`, `updateReport`

steps:
1. Add `UPDATE teams SET status = 'archived', isUpdated = 1 WHERE _id = :id` and `UPDATE teams SET imageName = :imageName, isUpdated = 1 WHERE _id = :id` (returning `Int`).
2. Call them from `archiveReport` and `attachTeamImage`. Keep `updateTeamEntityById` for `updateReport` only.
3. Image test: `addReport` uses a random UUID, so verify `setImageNameById(any(), "logo.png")`, not `"report-1"`. The short-circuit test becomes `coVerify(exactly = 0) { teamDao.setImageNameById(any(), any()) }`.
4. `TeamDaoTest`: archive hides the row from `observeNonArchivedReportsByTeamId` and sets `updated`. `setImageNameById` leaves other columns unchanged.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*EnterprisesRepositoryImplTest" --tests "*TeamDaoTest"` passes, and the full suite is green.
- Archive removes the row immediately, and both changes upload on the next sync.

size budget: ~45 lines, 4 files.

out of scope / notes: No schema change. `TeamDao.kt` is not in any open PR.

---

### 16. batch the HTML-resource reconcile pass in ResourcesRepositoryImpl (roadmap 1+7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·perf#3 (1)

context: After each resource sync page, `reconcileHtmlLibraries` (`repository/ResourcesRepositoryImpl.kt:724-735`) calls the public `reconcileHtmlResourceOffline(resourceId)` (`:400`) for every HTML row with no local address. Each call re-fetches the row it was just handed (`getByResourceId`, `:401`), hops to `dispatcherProvider.io` for a file check, and, if the entry file exists, does a single-row `upsert` plus `clearResourceListCache()` (`:418-419`). `MyLibrary.id == resourceId` and `getByResourceId` is `LIMIT 1`, so the in-hand object is equivalent.

files:
- main: `repository/ResourcesRepositoryImpl.kt` (`reconcileHtmlResourceOffline`, `reconcileHtmlLibraries`)
- test: `repository/ResourcesRepositoryImplTest.kt`
- leave alone: the interface, `CoursesRepositoryImpl.kt`'s copy of the loop (open PRs), `BaseContainerFragment.kt:200`, `markResourceOfflineByResourceId`

steps:
1. Extract a private helper that mutates a `MyLibrary` in place and returns whether it changed. The helper does no DAO writes.
2. The public `reconcileHtmlResourceOffline(resourceId)` becomes fetch → helper → upsert and clear cache if changed. Its behaviour is unchanged.
3. `reconcileHtmlLibraries` runs the helper on the in-hand rows, keeps the per-item try/catch and `Log.w`, and then does one `upsertAll(changed)`.
4. Test: two HTML docs with entry files on disk. Assert no `getByResourceId` call, and one extra `upsertAll`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepository*"` passes, with the existing `reconcileHtmlResourceOffline …` tests unchanged, and the full suite is green.
- After a fresh sync, HTML resources already under `ole/<id>/` open offline.

size budget: ~30 main plus ~35 test lines, 2 files.

out of scope / notes: The main saving is the per-item SELECT. The per-item UPSERT only happens for rows whose file is on disk.

---

### 17. move the community-leaders fetch from LoginSyncManager into ConfigurationsRepository (roadmap 5+1)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#4 (1)

context: `LoginSyncManager.syncAdmin` (`services/sync/LoginSyncManager.kt:126-168`) builds a Gson `_find` selector, POSTs to `/_users/_find`, and writes `setCommunityLeaders(...)` plus the raw `"user_admin"` pref. That's persistence logic in a sync service. `ConfigurationsRepositoryImpl` already has `apiInterface`, `sharedPrefManager` and `@PlainGson gson`, and the read side `getCommunityLeaders()` (`:414-416`). There is no Hilt cycle.

files:
- main: `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `services/sync/LoginSyncManager.kt`
- test: `services/sync/LoginSyncManagerTest.kt` (positional constructor at `:70-77`), `repository/ConfigurationsRepositoryImplTest.kt`
- leave alone: `SyncManager.kt` (its `loginSyncManager.syncAdmin()` call keeps compiling), `CheckVersionCallback`

steps:
1. Add `suspend fun syncCommunityLeaders()` to the interface. Implement it with the current body: blank-header early return, URL guard, the `docs[0]` → `"user_admin"` write, catch-and-log. Use the injected `gson`.
2. Inject `ConfigurationsRepository` into `LoginSyncManager`, reduce `syncAdmin()` to `applicationScope.launch { configurationsRepository.syncCommunityLeaders() }`, and drop unused imports.
3. Add repository tests for success (leaders plus `user_admin` written), non-2xx (nothing written) and blank header (no API call). Update the `LoginSyncManagerTest` constructor.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ConfigurationsRepositoryImplTest" --tests "*LoginSyncManagerTest" --tests "*SyncManagerTest"` passes, and the full suite is green.
- Community → Leaders still lists the server's admins.

size budget: ~90 lines, 5 files.

out of scope / notes: Four other places still read the leaders cache straight from prefs, and `"user_admin"` is written but never read. Both are follow-up cleanups. Sequence with task 39 (also `ConfigurationsRepository`).

---

### 18. LifeRepositoryImpl: take userId from the caller and drop SharedPrefManager (roadmap 1+3)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: grok·repo#2 (1)

context: `LifeRepositoryImpl.updateVisibility` and `updateMyLifeListOrder` fall back to `sharedPrefManager.getUserId()` (`repository/LifeRepositoryImpl.kt:26,35`), which is the Impl's only prefs use (`:9,13`). `LifeViewModel` resolves the user for loads via `resolveUserId()` (`ui/life/LifeViewModel.kt:27-31`), but not for these two updates (`:43-59`). `resolveUserId()` reads the same pref through `UserRepository`, so this moves ownership to the user boundary rather than adding a new id source.

files:
- main: `repository/LifeRepository.kt`, `repository/LifeRepositoryImpl.kt`, `ui/life/LifeViewModel.kt`
- test: `repository/LifeRepositoryImplTest.kt`, `repository/LifeRepositoryTest.kt` (`:29-35` constructs the Impl; missing from grok's list), `ui/life/LifeViewModelTest.kt`
- leave alone: `MyLifeDao`, `LifeCache`, `MyLife`, `UserRepository*`

steps:
1. Add a `userId: String?` parameter to both repository methods. Use it when the rows carry no `userId`.
2. Remove `SharedPrefManager` from the Impl's constructor and imports.
3. Pass `resolveUserId()` from the VM.
4. Update the three test classes.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Life*"` passes, and the full suite is green.
- Hiding or reordering My Life tiles persists for the active user.

size budget: ~40–70 lines, 6 files.

out of scope / notes: Sequence with tasks 32 and 50 (`LifeCache`) and 39 (`MyLife` defaults).

---

### 19. route LoginActivity's and GuestLoginExtensions' repository calls through LoginViewModel (roadmap 3+10)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#5 (0.625), devin·repo#9 (0.375)

context: `LoginActivity` bypasses its `LoginViewModel` (`by viewModels()`, `:87`): `userRepository.getUserByName` (`:278`), `communityRepository.syncCommunityDocs()` inside `withContext(io)` (`:373-375`), `userRepository.createGuestUser` (`:589, :631`), and `showGuestLoginDialog(userRepository)` (`:321`). `GuestLoginExtensions.kt` then calls `validateUsername`, `findUserByName` and `createGuestUser` on the raw repository, at 4 call sites in total.

files:
- main: `ui/sync/LoginViewModel.kt`, `ui/sync/LoginActivity.kt`, `ui/sync/GuestLoginExtensions.kt`
- test: `ui/sync/LoginViewModelTest.kt` (`createViewModel` at `:29-34`)
- leave alone: `ProcessUserDataActivity`/`SyncActivity`/`BecomeMemberActivity` base fields (subclasses use them), `ServerDialogExtensions.kt`, `LoginSyncManager.kt`

steps:
1. Inject `CommunityRepository` into `LoginViewModel`. Add pass-throughs: `isArchivedMember`, `validateUsername`, `findUserByName`, `createGuestUser`, and `syncCommunityDocs()` launched on `dispatcherProvider.io`.
2. Replace the Activity's direct calls with them.
3. Change the extension to `showGuestLoginDialog(viewModel: LoginViewModel)` and switch its call sites.
4. Add one VM test per new method.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*LoginViewModelTest"` passes, and the full suite is green.
- `grep -n "userRepository\.\|communityRepository\." ui/sync/LoginActivity.kt ui/sync/GuestLoginExtensions.kt` is empty.
- Archived member, guest create and "already a guest" dialogs all still work.

size budget: ~80 lines, 4 files.

out of scope / notes: Don't do devin·repo#9 step 4. `ServerDialogExtensions` extends `SyncActivity`, so it can't reach `LoginActivity`'s private `loginViewModel`. That needs its own VM.

---

### 20. move the retry find-or-enqueue decision into RetryRepository.recordFailure (roadmap 5+1)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#2 (1)

context: `RetryQueue.queueFailedOperation` (`services/retry/RetryQueue.kt:26-58`) calls `retryRepository.getExistingOperation(...)`, then branches to `updateAttempt` or `enqueue`. New rows get random ids and there is no unique index, so two uploads failing on the same item at once can both see `null` and insert duplicates. The full row (including `serializedPayload`) is loaded just to read `.id`. `getPendingCount()` and `deletePendingAndAbandonedOperations()` are on the interface but only called inside the Impl.

files:
- main: `repository/RetryRepository.kt`, `repository/RetryRepositoryImpl.kt`, `services/retry/RetryQueue.kt`
- test: `repository/RetryRepositoryImplTest.kt` (incl. the `updateAttempt` test at `:91-97`, which claude's list misses), `services/retry/RetryQueueTest.kt` (`:88-121`)
- leave alone: `RetryDao.kt`, `RetryQueueWorker.kt`, the three `queueFailedOperation` callers (its signature doesn't change)

steps:
1. Replace `enqueue`/`updateAttempt`/`getExistingOperation` on the interface with `suspend fun recordFailure(...)`, taking `enqueue`'s parameters.
2. Implement it under the existing `mutex.withLock`: `retryDao.findExisting` → `markFailed`, otherwise `insert`.
3. Make `getPendingCount`/`deletePendingAndAbandonedOperations` private.
4. Reduce `queueFailedOperation` to the non-retryable early return plus one `recordFailure` call.
5. Rewrite the affected tests: "inserts when none exists" and "records attempt when one exists".

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Retry*"` passes, and the full suite is green.
- Two concurrent failures of the same item leave one retry row.

size budget: ~110 lines, 5 files.

out of scope / notes: The mutex is process-local, which is enough here (one process). Sequence with task 44, which also edits `RetryRepositoryImpl.kt`.

---

### 21. cache and file-guard the legacy EncryptedSharedPreferences in SecurePrefs (roadmap 7+4)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·perf#4 (1)

context: `SecurePrefs.getLegacyEncryptedPrefs()` (`utils/SecurePrefs.kt:152-168`) builds a Keystore `MasterKey` and calls `EncryptedSharedPreferences.create(…)` on every call. That's a Keystore round-trip plus a Tink keyset load. It runs on every login (`saveCredentials :212`, from `UserSessionManager.kt:34`), on logout (`clearCredentials :311`), and in `getUserName`/`getPassword` when the value is missing. It also creates `secure_prefs.xml` on devices that never had one. The non-legacy store is already cached (`cachedSecureStore`, `:29, 145-149`).

files:
- main: `utils/SecurePrefs.kt` (`getLegacyEncryptedPrefs` plus one `@Volatile` field)
- test: `utils/SecurePrefsTest.kt`
- leave alone: `buildSecureStore`, `getAead`, `performMigration`, public signatures, `UserSessionManager.kt`

steps:
1. Return `null` when `File(dataDir, "shared_prefs/$LEGACY_FILE_NAME.xml")` doesn't exist, mirroring `:82`.
2. Cache a successful instance with the same double-checked `synchronized` pattern as `getSecureStore`, using `applicationContext`. Don't cache a failure.
3. Test directly: call `getLegacyEncryptedPrefs` via reflection on a clean context and assert it returns `null` and creates no file. The list's `saveCredentials`-based test passes with or without the fix, because Robolectric has no AndroidKeyStore.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SecurePrefsTest" --tests "*UserSessionManagerTest"` passes, and the full suite is green.
- A fresh install can log in, log out and log in again with "remember me". An upgrade that has `secure_prefs.xml` keeps its saved credentials.

size budget: ~15 main plus ~20 test lines, 2 files.

out of scope / notes: The file guard only helps fresh installs; existing installs already have the file, created by this bug. For them only the cache helps. Don't delete the legacy file.

---

### 22. move shelf discovery out of SyncManager into SyncRepository (roadmap 5+1)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#3 (1)

context: `SyncManager.getShelvesWithDataBatchOptimized` (`services/sync/SyncManager.kt:449-485`) is data work inside the orchestrator. It reads the shelf cache (`syncRepository.getCachedShelvesWithData()`), calls `apiInterface.getDocuments(…/shelf/_all_docs)`, fans out `userSyncRepository.checkShelfBatchForDataOptimized` in `chunked(25)` with `Semaphore(8)`, and writes back `cacheShelvesWithData`. The cache halves live in `SyncRepositoryImpl` (`:204-220`) and are public only so `SyncManager` can stitch them around its network call. There is no Hilt cycle: `UserRepositoryImpl` doesn't depend on `SyncRepository`.

files:
- main: `repository/SyncRepository.kt`, `repository/SyncRepositoryImpl.kt`, `services/sync/SyncManager.kt` (delete `:449-485`, change the call at `:498`)
- test: `repository/SyncRepositoryImplTest.kt` (constructor at `:102-114`, and the 5 cache tests at `:247-305`)
- leave alone: `TransactionSyncManager.kt`, `UserRepositoryImpl.kt`, `SyncManagerTest.kt`

steps:
1. Add `suspend fun getShelvesWithData(): List<String>` and remove the two cache methods from the interface.
2. Inject `dagger.Lazy<UserSyncRepository>` into the Impl and move the body verbatim. Make the cache helpers `internal`, not `private`, so the 5 existing tests compile.
3. Call it from `SyncManager` and delete the moved code and the unused `Rows` import.
4. Add tests: cache hit (no API call), miss (API plus batch check plus write), API failure (empty list, no write; needs `mockkObject(ApiClient)`).

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SyncRepositoryImplTest" --tests "*SyncManagerTest"` passes, and the full suite is green.
- "Library: Found N shelves with data" logs the same N, and a second sync within 6 h skips `_all_docs`.

size budget: ~100 lines, 4 files.

out of scope / notes: Sequence with task 13, which also edits `SyncManager.kt`.

---

### 23. hoist loop-invariant patterns in VoicesRepositoryImpl.countTopLevelByTeams (roadmap 1+7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: devin·perf#9 (1)

context: `countTopLevelByTeams` loops every membership row and, inside it, every `teamId`, rebuilding `"\"_id\":\"$teamId\""` and re-running `equals(ignoreCase)` on the same row fields each time (`repository/VoicesRepositoryImpl.kt:496-502`). That's O(rows × teams) allocations. `VoicesRepositoryImplTest.kt:273-295` pins the semantics.

files:
- main: `repository/VoicesRepositoryImpl.kt` (`countTopLevelByTeams` only)

steps:
1. Precompute `teamIds.map { it to "\"_id\":\"$it\"" }` before the row loop.
2. Compute `row.viewableBy.equals("teams", true)` once per row.
3. Stronger version: when `viewableBy == "teams"`, look up the lowercased `viewableId` in a map of pre-lowercased team ids instead of scanning.
4. Keep the `matchesViewable || matchesViewIn` semantics.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*VoicesRepositoryImplTest"` passes, with per-team counts identical.

size budget: ~15–25 lines, 1 file.

out of scope / notes: `VoicesRepositoryImpl.kt` is in PRs 10993 (on hold), 13415 (brainstorm) and 15808 (draft). The edit is function-local, so expect a trivial rebase.

---

### 24. pin getJoinRequestDetailsBatch fallbacks in NotificationsRepositoryImplTest (roadmap 8)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: kimi·perf#9 (1)

context: `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` (`:302-340`) maps join requests to user and team names, with `"Unknown Team"`/`"Unknown User"` fallbacks and an empty-`userId` path. The known-team/known-user case is already tested (`NotificationsRepositoryImplTest.kt:506`); the fallbacks and the short-circuit are not.

files:
- test: `repository/NotificationsRepositoryImplTest.kt`

steps: Through the public entry point the existing tests use, add these cases:
- (b) an unknown team id → `"Unknown Team"`;
- (c) an empty `userId` → `"Unknown User"`, with `coVerify(exactly = 0) { userRepository.get().getUsersByIds(any()) }`;
- (d) no join-request notifications → `getJoinRequestsInfo` is not called;
- (e) a user id missing from the returned users → `"Unknown User"`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest"` passes.

size budget: ~80–120 test lines, 1 file.

out of scope / notes: No production change. Sequence with task 1 (same test file).

---

### 25. unpin the shard-2 SDK-32 test cohort together (roadmap 8)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: codex·perf#7–10 (1, merged: four one-line fragments that save nothing individually)

context: Five test classes are pinned to `sdk = [32]` without a stated reason, and all hash to CI shard 2: `ui/resources/ResourcesAdapterTest.kt:24`, `ui/enterprises/EnterprisesReportsFragmentTest.kt:26`, `data/room/dao/ExamDaoTest.kt:18`, `data/room/dao/CourseDaoTest.kt:20`, and `data/room/dao/MyLibraryDaoTest.kt:18` (not in codex's list). While any one stays, shard 2 still builds a 32 sandbox, so codex's "do not combine" rule defeats the benefit. Equivalent unpinned tests already pass on 36: `CoursesAdapterTest` (same Mockito setup), `BaseTeamFragmentTest`, and more than 10 unpinned Room DAO tests.

files:
- test: the five classes above (class `@Config` only). For `ExamDaoTest`, `CourseDaoTest` and `MyLibraryDaoTest`, delete the whole `@Config` and the now-unused `org.robolectric.annotation.Config` import.

steps:
1. Remove the five pins together, keeping any `application =` override.
2. Update the pin table in `docs/TESTING.md:143-150`.
3. Run each class, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest` passes, and `grep -rn "sdk = \[32\]" app/src/test` is empty once task 8 has also landed.

size budget: ~8 lines, 5 files plus docs.

out of scope / notes: Once 32 is gone from the suite, a follow-up can drop `32:` from `robolectricSdkJars` in `app/build.gradle`. That file is locked by PRs 13928, 15820, 15824 and 8175.

---

### 26. inject TimeProvider into PersonalsRepositoryImpl (roadmap 1+8)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: codex·repo#4 (1)

context: `PersonalsRepositoryImpl.savePersonalResource` uses `Date().time` and `serialize` uses `System.currentTimeMillis()` (`:37-47, 93-99`). The time contract can't be asserted in tests, unlike the other repositories that use the platform-free `TimeProvider` (`di/TimeModule`).

files:
- main: `repository/PersonalsRepositoryImpl.kt`
- test: `repository/PersonalsRepositoryImplTest.kt`, `data/room/dao/PersonalDaoTest.kt` (`:35` constructs the Impl; missing from codex's list)

steps:
1. Inject `TimeProvider` and replace both clock reads with `timeProvider.now()`.
2. Use a fixed fake in both tests. Assert that `Personal.date` and the serialized upload date equal the supplied value.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Personal*"` passes, and the full suite is green.

size budget: ~25–45 lines, 3 files.

out of scope / notes: Drop codex's "two clocks in one operation" rationale; they're separate operations. The value is testability and consistency.

---

### 27. chunk TeamLogDao.getRecentTeamVisits and delete the dead getByRemoteIds (roadmap 1+7)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: devin·repo#6 (1)

context: `TeamLogDao.getRecentTeamVisits(cutoff, teamIds)` (`:15-16`) binds `teamId IN (:teamIds)` unchunked, and `TeamsRepositoryImpl.getRecentVisitCounts` passes the full team-id list. `getByRemoteIds` (`:24-25`) has zero callers. `getTeamVisitsForUsers` (`:18-19`, `user IN (:userNames)`) has the same exposure.

files:
- main: `data/room/dao/TeamLogDao.kt` only (public signatures unchanged)

steps:
1. Rename the query to `getRecentTeamVisitsInternal`. Add a same-named default method that `chunked(900)` and `flatMap`s; cutoff plus 900 ids is 901 variables.
2. Delete `getByRemoteIds`.
3. Consider the same treatment for `getTeamVisitsForUsers`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*TeamsRepositoryImplTest"` passes (mockk still intercepts default methods), and the full suite is green.

size budget: ~20–30 lines, 1 file.

out of scope / notes: A user needs roughly 1,000 teams to trip this, so it's hardening, not a live bug.

---

### 28. route Progress/Courses submission reads and deletes through SubmissionsRepository (roadmap 1+4)

rating: 57 (evidence 4 · impact 3 · feasibility 2) · proposed by: devin·repo#5 (1)

context: `ProgressRepositoryImpl` injects submissions-domain DAOs and calls `submissionDao.getExamSubmissionsByUser` (`:72`) and `answerDao.getBySubmissionIds` (`:186`). `CoursesRepositoryImpl` already injects `SubmissionsRepository` (`:59`), yet still calls `submissionDao.getExamSubmissionsByUser` (`:469`), `answerDao.getBySubmissionIds` (`:480`), and the delete trio at `:619-626`. `SubmissionsRepositoryImpl` owns all four DAOs (`:52-55`).

files:
- main: `repository/SubmissionsRepository.kt`, `repository/SubmissionsRepositoryImpl.kt`, `repository/ProgressRepositoryImpl.kt`, `repository/CoursesRepositoryImpl.kt`
- test: `repository/ProgressRepositoryImplTest.kt`, `repository/CoursesRepositoryImplTest.kt`

steps:
1. Add `getExamSubmissionsByUser`, `getAnswersBySubmissionIds`, `getUnuploadedNonSurveySubmissionsByParentIds` and `deleteSubmissionsWithAnswers` as one-line delegates. `submissionDao.deleteByIds` is not chunked, so call the delete per chunk inside the existing loop, or chunk it yourself.
2. Swap the Progress and Courses call sites and drop `submissionDao`/`answerDao` from their constructors. Leave the exam and question reads where they are; they are course content, not submissions.
3. Keep `appDatabase.withTransaction` in `deleteCoursesProgress`. Room's transaction rides the coroutine context, so the repository call inside it is fine.
4. Update both tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ProgressRepositoryImplTest" --tests "*CoursesRepositoryImplTest" --tests "*SubmissionsRepository*"` passes, and the full suite is green.
- Course progress detail and course deletion behave as before.

size budget: ~90–110 lines, 6 files.

out of scope / notes: This is the most contended task in the backlog: PRs 15808, 14427, 14883, 13657 and 16624 touch these files. Land it after those settle.

---

### 29. enterprise reports UI consumes a report DTO instead of MyTeam (roadmap 1+3)

rating: 54 (evidence 4 · impact 2 · feasibility 3) · proposed by: grok·repo#5 (1)

context: `EnterprisesRepository.getReportsFlow` returns `Flow<List<MyTeam>>` (`:11`; Impl `:87-99`). `EnterprisesViewModel`, `EnterprisesReportsFragment` and `EnterprisesReportsAdapter` bind the teams Room entity's finance fields. The Impl dedupes with a 12-field `distinctByContent` comparator (`:88-97`).

files:
- main: `repository/EnterprisesRepository.kt`, `repository/EnterprisesRepositoryImpl.kt`, `ui/enterprises/EnterprisesViewModel.kt`, `ui/enterprises/EnterprisesReportsAdapter.kt`, `ui/enterprises/EnterprisesReportsFragment.kt`
- test: `EnterprisesViewModelTest`, `EnterprisesReportsAdapterTest`, `EnterprisesReportsFragmentTest`, `EnterprisesRepositoryImplTest`
- leave alone: `TeamDao`, `MyTeam` (PR 15951), the finances adapter and fragment

steps:
1. Add a `FinanceReport` data class with the fields the reports UI reads. The edit dialog reads nearly all of them (`Fragment :241-279`).
2. Map `MyTeam` → DTO in `getReportsFlow`, and replace the manual comparator with `distinctUntilChanged()` via data-class equality.
3. Retype the VM, Fragment and Adapter. Keep the attachment path through `MyTeam.getAttachmentFile(…)`, a static helper, or carry `imageName` plus `reportId`.
4. Update the four tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Enterprises*"` passes, and the full suite is green.
- CRUD, archive and CSV export still work.

size budget: ~120–150 lines, about 9 files. That's over a 5-file cap, so split it into (a) repository plus VM and (b) adapter plus fragment if the cap applies.

out of scope / notes: Sequence with task 15, which also edits `EnterprisesRepositoryImpl.kt`.

---

### 30. batch per-member visit lookups in TeamsRepositoryImpl.getJoinedMembersWithVisitInfo (roadmap 1+7)

rating: 54 (evidence 4 · impact 2 · feasibility 3) · proposed by: devin·perf#1 (1)

context: `getJoinedMembersWithVisitInfo` builds `visitStatsMap` in one pass (`TeamsRepositoryImpl.kt:1015-1021`). devin calls it `getJoinedMembersWithStats`, which doesn't exist. Inside `orderedMembers.map` it still calls `activitiesRepository.getLastVisit(member.name)` (`:1027`) and `getOfflineVisitCount(member.id)` (`:1033`). Each is a single-row SQL query (`ActivitiesRepositoryImpl.kt:154, :66`), so the members screen issues 2N queries.

files:
- main: `repository/TeamsRepositoryImpl.kt`, `data/room/dao/OfflineActivityDao.kt`, `repository/ActivitiesRepository.kt`, `repository/ActivitiesRepositoryImpl.kt`
- test: `repository/TeamsRepositoryImplTest.kt` (stubs at `:626-628` become dead)

steps:
1. Add `GROUP BY` multimap queries: `Map<@MapColumn("userName") String, @MapColumn("last") Long>` for last visits, and per-`userId` login counts. Expose them on `ActivitiesRepository`.
2. Fetch both maps once before the `map`. Use `?: 0` for absent members, and keep "No logout record found" for null names.
3. Skip the `markMembershipsForLeave` batching devin also proposes. It normally sees one row per user per team, so it gains nothing.
4. Add a test asserting the batch methods are called once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TeamsRepositoryImplTest" --tests "*ActivitiesRepository*"` passes, and the full suite is green.
- Member visit counts and last-visit text are unchanged.

size budget: ~70–90 lines, 5 files.

out of scope / notes: Every file is locked. `TeamsRepositoryImpl` is in 5 PRs, `OfflineActivityDao` in 15824, `ActivitiesRepository*` in 14427. `userIds` is also exposed to the IN-list limit.

---

### 31. strip UserRepositoryImpl's foreign MyLibraryDao and MeetupDao (roadmap 1+4)

rating: 54 (evidence 4 · impact 2 · feasibility 3) · proposed by: devin·repo#3 (1)

context: `UserRepositoryImpl` reads two foreign tables once each: `myLibraryDao.getByIds(resourceIds)` in `getAchievementData` (`:986`), where `ResourcesRepository.getLibraryItemsByIds` exists and `resourcesRepositoryLazy` is already injected, and `meetupDao.getByUserId(userId)` in `getShelfData` (`:1252`), which no repository method exposes.

files:
- main: `repository/UserRepositoryImpl.kt`, `repository/EventsRepository.kt`, `repository/EventsRepositoryImpl.kt`
- test: `repository/UserRepositoryImplTest.kt`, `repository/UserRepositoryBulkInsertTest.kt` (3 constructor sites; missing from devin's list)

steps:
1. Add `getMeetupsForUser(userId)` to `EventsRepository`/Impl, delegating to `meetupDao.getByUserId`.
2. In `UserRepositoryImpl`, drop both DAOs, inject `dagger.Lazy<EventsRepository>`, and swap the two call sites.
3. Fix the four test constructors.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserRepository*" --tests "*EventsRepository*"` passes, and the full suite is green.
- Achievements list resources, and the shelf payload has meetup ids.

size budget: ~45 lines, 5 files.

out of scope / notes: Locked by PR 15808 (`UserRepositoryImpl`) and 15108/15820/15825 (`EventsRepository*`). There is a pre-existing oddity, which this task doesn't change: achievements carry resource `_id`s, while `MyLibraryDao.getByIds` matches the local `id`.

---

### 32. LifeCache: Gson to kotlinx.serialization (roadmap 1+9)

rating: 54 (evidence 3 · impact 2 · feasibility 4) · proposed by: gemini·repo#4 (1)

context: `LifeCache` (`repository/LifeCache.kt`) serializes `CachedMyLifeItem` with Gson. The kotlinx serialization plugin is applied (`app/build.gradle:5`; CLAUDE.md's "only 3 plugins" line is out of date). Existing cached JSON stays readable: the injected Gson writes nulls, and the field names equal the property names. Any decode failure already returns `null` and falls back to the DB (`:41-43`).

files:
- main: `repository/LifeCache.kt`, `di/SharedPreferencesModule.kt` (`:57-61` builds `LifeCache` by hand; missing from gemini's list)
- test: `repository/LifeCacheTest.kt`, `repository/LifeRepositoryImplTest.kt` (`:46`; missing from gemini's list)

steps:
1. Mark `CachedMyLifeItem` `@Serializable` and give its nullable fields `= null` defaults.
2. Inject `Json` in place of `Gson`, and update the module and both tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*LifeCacheTest" --tests "*LifeRepository*"` passes.
- An existing cached menu survives the upgrade.

size budget: ~30–50 lines, 4 files.

out of scope / notes: `LifeCache` still depends on `SharedPreferences`; see task 50. Keep the key prefix `myLifeCache_`.

---

### 33. cancel stale loads in NotificationsViewModel and inject DispatcherProvider (roadmap 3+8)

rating: 54 (evidence 3 · impact 2 · feasibility 4) · proposed by: claude·perf#7 (1)

context: `NotificationsViewModel.loadNotifications` (`ui/notifications/NotificationsViewModel.kt:69-78`) launches in `viewModelScope` without cancelling a previous load. `NotificationsFragment` calls it from three places (`:77, 87, 173`), so a slow load for an old filter can overwrite a newer one. The VM doesn't inject `DispatcherProvider`, which the style guide requires. Its perf claim is weak: task dates are already parsed in the repository, and `formatNotification` is cheap.

files:
- main: `ui/notifications/NotificationsViewModel.kt`
- test: `ui/notifications/NotificationsViewModelTest.kt` (constructor at `:46`)

steps:
1. Add `DispatcherProvider` as the last constructor parameter.
2. Keep a `loadJob` and cancel it before relaunching.
3. Optionally run the `formatNotification` map in `withContext(dispatcherProvider.default)` and add `.flowOn(default)` on the two grouped flows.
4. Test: two back-to-back loads where the first repository call is slower (`coAnswers { delay }`) end with the second result.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Notifications*"` passes, and the full suite is green.
- Rapid filter switching always ends on the last filter tapped.

size budget: ~15 main plus ~25 test lines, 2 files.

out of scope / notes: No change to the formatting or grouping rules.

---

### 34. BecomeMemberViewModel for BecomeMemberActivity's validation and create calls (roadmap 3+10)

rating: 53 (evidence 3 · impact 3 · feasibility 2) · proposed by: kimi·repo#2 (1)

context: `BecomeMemberActivity` calls `userRepository.createMember`, `validateUsername` (twice, including a debounced `usernameValidationJob`) and `cleanupDuplicateUsers` inside `lifecycleScope.launch`. `userRepository` is inherited from `ProcessUserDataActivity` (`:59`), which other subclasses use, so it can't be removed.

files:
- main: `ui/user/BecomeMemberActivity.kt`, new `ui/user/BecomeMemberViewModel.kt`
- test (new): `ui/user/BecomeMemberViewModelTest.kt`

steps:
1. Create a `@HiltViewModel` injecting `UserRepository` and `DispatcherProvider`. Move the three calls and the debounce into it. The stale-input guard at `:243` becomes VM-side input tracking.
2. Expose a validation-error `StateFlow` and a create-result event.
3. Have the Activity collect them, and still call the inherited `startUpload(...)` and auto-login itself.
4. Unit-test the VM.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*BecomeMemberViewModelTest"` passes, and the full suite is green.
- Signing up still validates usernames live and creates a member.

size budget: ~120–150 lines, 3 files.

out of scope / notes: Blocked by PR 13848 (on hold), which edits `BecomeMemberActivity` and strings. Drop kimi's "remove the repository fields" step; they're inherited.

---

### 35. ResourceDetailViewModel for ResourceDetailFragment's data calls (roadmap 3+10)

rating: 53 (evidence 3 · impact 3 · feasibility 2) · proposed by: kimi·repo#1 (1)

context: `ResourceDetailFragment` calls `resourcesRepository.resolveLibraryItem`/`setUserLibrary`, `ratingsRepository.getRatingSummary` and `userRepository.getUserModel` from `viewLifecycleOwner.lifecycleScope.launch`. `resourcesRepository` and `userRepository` are declared in `BaseResourceFragment` (`:53-55`) and used there. Only `ratingsRepository` is the Fragment's own.

files:
- main: `ui/resources/ResourceDetailFragment.kt`, new `ui/resources/ResourceDetailViewModel.kt`
- test (new): `ui/resources/ResourceDetailViewModelTest.kt`

steps:
1. Create a `@HiltViewModel` with the three repositories plus `DispatcherProvider`. It owns the calls and exposes a `StateFlow` of library plus rating summary, and add/remove actions.
2. Have the Fragment observe it and delegate. Delete only the `ratingsRepository` field.
3. The base-class callback `onDownloadComplete()` (`:48`) triggers a VM refresh.
4. Unit-test the VM with `MainDispatcherRule`/`TestDispatcherProvider`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ResourceDetailViewModelTest"` passes, and the full suite is green.
- Resource detail shows its rating, and the library add/remove toggle works.

size budget: ~120–150 lines, 3 files.

out of scope / notes: Blocked by PR 13355 (P2P sharing, on hold), which edits this Fragment.

---

### 36. move AchievementFragment's remaining data calls into AchievementViewModel (roadmap 3+10)

rating: 50 (evidence 3 · impact 2 · feasibility 3) · proposed by: kimi·repo#3 (1)

context: `AchievementViewModel` exists and already injects `userRepository` and `resourcesRepository`. `AchievementFragment` still calls `userRepository.getAchievementData(uId, pCode)` (~`:90`), `userRepository.getUserModel()` (~`:106`) and `resourcesRepository.downloadResources(listOf(lib))` (~`:205`) directly.

files:
- main: `ui/user/AchievementFragment.kt`, `ui/user/AchievementViewModel.kt`
- test: `ui/user/AchievementViewModelTest.kt` (constructs the VM at `:42`; missing from kimi's list)

steps:
1. Add `loadAchievementData()` and `downloadResource(lib)`. Populate the existing `_user` via a side-effect-free loader, not `loadUserAndAchievement`.
2. Switch the Fragment to them.
3. Extend the VM test. `EditAchievementFragment` shares this VM and is unaffected.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*AchievementViewModelTest"` passes, and the full suite is green.
- The achievements screen and resource download are unchanged.

size budget: ~50–80 lines, 3 files.

out of scope / notes: `AchievementFragment.kt` is in PR 15824 (gamification). Sequence with task 6, which also edits `AchievementViewModel`.

---

### 37. Dictionary parsing: Gson to kotlinx.serialization (roadmap 1+9)

rating: 50 (evidence 3 · impact 2 · feasibility 3) · proposed by: gemini·repo#5 (1)

context: `repository/DictionaryMapper.kt` and `DictionaryRepositoryImpl.kt` parse the dictionary file through Gson `JsonArray` plus `GsonUtils.getString`. That returns `""` for missing, null or non-string values, and reads the misspelled key `antonoym` (`DictionaryMapper.kt:21`).

files:
- main: `repository/DictionaryMapper.kt`, `repository/DictionaryRepositoryImpl.kt`
- test: `repository/DictionaryRepositoryImplTest.kt`, `repository/DictionaryMapperTest.kt` (missing from gemini's list)

steps:
1. Decode into a `@Serializable` DTO with `String = ""` defaults, `@SerialName("advance_code")` and `@SerialName("antonoym")`, using the provided `Json` (`coerceInputValues`/`ignoreUnknownKeys`, `NetworkModule.kt:86-89`).
2. Map the DTO to `DictionaryEntity`.
3. Add tests for null, missing and numeric fields. With `isLenient` a number decodes as `"1"`, not `""`; decide and pin that.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Dictionary*"` passes.
- The dictionary loads the same word count.

size budget: ~60–90 lines, 4 files.

out of scope / notes: The parse-failure path stays `Failed`.

---

### 38. NotificationsRepository.updateTeamNotification takes a count, not List<News> (roadmap 1+3)

rating: 50 (evidence 3 · impact 2 · feasibility 3) · proposed by: codex·repo#1 (1)

context: `updateTeamNotification(teamId, news: List<News>)` (`NotificationsRepository.kt:31`, Impl `:366-378`) only reads `news.size`, so the notifications boundary imports a Voices entity, and callers materialise rows just to count them.

files:
- main: `repository/NotificationsRepository.kt`, `repository/NotificationsRepositoryImpl.kt`, `ui/teams/voices/TeamsVoicesViewModel.kt` (`:58`, `newsList.size`; missing from codex's list)
- test: `repository/NotificationsRepositoryImplTest.kt`, `repository/TeamChatBadgeIntegrationTest.kt` (`:106`), `ui/teams/voices/TeamsVoicesViewModelTest.kt` (`:67`)

steps:
1. Change the signature to `(teamId, count: Int)` and drop the `News` imports.
2. Update all 5 call sites.
3. In the integration test, use `countTopLevelByTeam` and cover zero and nonzero badge transitions.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest" --tests "*TeamChatBadgeIntegrationTest" --tests "*TeamsVoicesViewModelTest"` passes.
- The team chat badge appears and clears as before.

size budget: ~35–50 lines, 6 files.

out of scope / notes: `TeamsVoicesViewModel.kt` is in PR 10993 (on hold). Sequence with tasks 1 and 24 (same repository and test).

---

### 39. remove the R import from MyLife by moving default items to a UI-side object (roadmap 9)

rating: 49 (evidence 2 · impact 2 · feasibility 4) · proposed by: gemini·repo#1 (1)

context: The Room entity `model/MyLife.kt` imports `org.ole.planet.myplanet.R` for `defaultItemPairs`/`defaultItems` in its companion. `LifeRepository.getMyLifeByUserId(userId, defaultItems)` (`LifeRepository.kt:8`) already takes the defaults as a parameter so the repository stays resource-free. gemini's destination, `LifeRepositoryImpl`, would put `R` into the repository layer.

files:
- main: `model/MyLife.kt`, new `ui/life/LifeDefaults.kt`, `ui/dashboard/DashboardPluginFragment.kt` (`:145`), `ui/life/LifeViewModel.kt` (`:37`)
- test: `ui/life/LifeViewModelTest.kt` (`:77`)

steps:
1. Move `defaultItemPairs`/`defaultItems` into `object LifeDefaults` under `ui/life/`.
2. Update the three callers.
3. Confirm `MyLife.kt` has no `android.*` or `R` imports left.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Life*"` passes.
- A new user's My Life menu is seeded as before.

size budget: ~30–40 lines, 5 files.

out of scope / notes: Sequence with task 18 (`LifeViewModel`).

---

### 40. let ChatViewModel resolve community config instead of ChatHistoryFragment (roadmap 3)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: claude·repo#6 (1)

context: `ChatHistoryFragment.refreshChatHistory` (`:136-142`) reads `sharedPrefManager.getParentCode()`/`getCommunityName()` and passes them into `ChatViewModel.loadChatHistoryScreenData(userId, parentCode, communityName)` (`:126-130`). `ConfigurationsRepository.getCommunityConfiguration()` returns the same values.

files:
- main: `ui/chat/ChatHistoryFragment.kt`, `ui/chat/ChatViewModel.kt`
- test: `ui/chat/ChatViewModelTest.kt` (constructor at `:59`; calls at `:208, 242, 273, 288`)

steps:
1. Inject `ConfigurationsRepository` into the VM and change the method to `loadChatHistoryScreenData(userId)`.
2. Read the config inside and pass it to `loadShareTargets`.
3. Have the Fragment pass only the user id; `sharedPrefManager` stays, still used at `:49`.
4. Stub the config in the test so the existing assertions keep their meaning.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ChatViewModelTest"` passes.
- The share dialog lists the same targets.

size budget: ~35 lines, 3 files.

out of scope / notes: No `ChatRepository` change.

---

### 41. one ConfigurationsRepository entry for Settings clear-data (roadmap 3)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: grok·repo#7 (1)

context: `SettingsViewModel.clearAllData()` (`:42-45`) sequences `configurationsRepository.clearAllData()` then `clearPreferences()` itself.

files:
- main: `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `ui/settings/SettingsViewModel.kt`
- test: `ui/settings/SettingsViewModelTest.kt`, `repository/ConfigurationsRepositoryImplTest.kt`

steps:
1. Add `suspend fun clearLocalAppData()` that calls the two existing methods in order (DB, then prefs).
2. Have the VM call only it, then emit `_clearDataEvent`.
3. Tests: the VM verifies one call, and the Impl test verifies the order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*SettingsViewModelTest" --tests "*ConfigurationsRepositoryImplTest"` passes.

size budget: ~25–40 lines, 5 files.

out of scope / notes: Keep the two underlying methods. Sequence with tasks 17 and 56 (same repository).

---

### 42. setHasFixedSize(true) on the Personals, References and Feedback lists (roadmap 7)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: kimi·perf#5 (1)

context: `binding.rvMypersonal` (`ui/personals/PersonalsFragment.kt:35`), `rvReferences` (`ui/references/ReferencesFragment.kt:29`) and `rvFeedback` (`ui/feedback/FeedbackListFragment.kt:47`) are `match_parent` and ListAdapter-backed, but never call `setHasFixedSize(true)`. The house precedent is `ChatHistoryFragment.kt:172`, `SurveyFragment.kt:115` and `LifeFragment.kt:77`.

files:
- main: the three Fragments

steps: Add `setHasFixedSize(true)` after each `layoutManager =`.

acceptance: `./gradlew assembleDefaultDebug`. Lists render and scroll unchanged.

size budget: 3 lines, 3 files.

out of scope / notes: The benefit is small: it only skips the parent `requestLayout` on granular updates. For the static References list it's zero.

---

### 43. drop the dead pending-upload surface from PersonalsRepository (roadmap 1+8)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: claude·repo#10 (1)

context: `PersonalsRepository.getPendingPersonalUploads(userId)` (`:25`, Impl `:69-71`) has zero production callers. Only `PersonalsRepositoryImplTest` (`:156-164`) uses it, and it is the only user of `PersonalDao.getPendingUploads` (`:26-27`). `updatePersonalAfterSync` (`:26`, Impl `:73-75`) is public, but only `uploadPersonal` in the same Impl calls it (`:155`).

files:
- main: `repository/PersonalsRepository.kt`, `repository/PersonalsRepositoryImpl.kt`, `data/room/dao/PersonalDao.kt` (`getPendingUploads` only)
- test: `repository/PersonalsRepositoryImplTest.kt` (`:156-171`)

steps:
1. Delete `getPendingPersonalUploads` and `PersonalDao.getPendingUploads`.
2. Make `updatePersonalAfterSync` private.
3. Delete the two tests; `uploadPersonal`'s success tests already verify `updateUploadedStatus`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Personal*"` passes, and `grep -rn getPendingPersonalUploads app/src` is empty.

size budget: ~25 lines, 4 files.

out of scope / notes: Sequence with tasks 3 and 26 (same Impl).

---

### 44. parse retry payloads straight to kotlinx in RetryRepositoryImpl (roadmap 5+9)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: claude·perf#10 (1)

context: `RetryRepositoryImpl.executeOperation` parses the payload with `JsonParser.parseString(...)` (`:74`), then deep-copies it with `payload.toKotlinx().jsonObject` (`:90`) because `putDoc`/`postDoc` take kotlinx. The Gson tree is used for nothing else.

files:
- main: `repository/RetryRepositoryImpl.kt`
- test: `repository/RetryRepositoryImplTest.kt`

steps:
1. Use `Json.parseToJsonElement(payload).jsonObject`. A non-object still throws into the existing `catch` → `TerminalFailure`.
2. Delete the `toKotlinx` line and the `JsonParser`/`toKotlinx` imports.
3. Add a `"[1,2]"` → `TerminalFailure` regression test. It passes before and after, so it guards rather than discriminates.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RetryRepositoryImplTest" --tests "*retry*"` passes.

size budget: ~6 main plus ~15 test lines, 2 files.

out of scope / notes: kotlinx is strict where Gson is lenient; every producer writes strict JSON. Sequence with task 20.

---

### 45. fetch only the on-disk resource titles in getOfflineResourceItems (roadmap 7)

rating: 46 (evidence 4 · impact 1 · feasibility 5) · proposed by: grok·perf#5 (1)

context: `ResourcesRepositoryImpl.getOfflineResourceItems` always calls `getResourceTitlesMap()` → `myLibraryDao.getResourceTitles()`, a `SELECT resourceId, title` over the whole `my_library` table, to label the small on-disk subset.

files:
- main: `repository/ResourcesRepositoryImpl.kt`, `data/room/dao/MyLibraryDao.kt`
- test: `repository/ResourcesRepositoryImplTest.kt` (`:1422-1458` must be re-stubbed)

steps:
1. Add `getResourceTitlesByIds(ids)` (`IN`, chunked by 900).
2. Walk and group first. Return empty if nothing is grouped, otherwise fetch titles for `grouped.keys`.
3. Keep the sort and the "Unknown" fallback.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepositoryImplTest"` passes, and storage detail lists the same items.

size budget: ~30 lines, 3 files.

out of scope / notes: The file walk dominates; this saves a few ms of a 2-column scan. Sequence with tasks 6 and 16 (same Impl).

---

### 46. move NotificationsRepositoryImpl's team-task reads behind TeamsNotificationsRepository (roadmap 1+4)

rating: 45 (evidence 2 · impact 2 · feasibility 3) · proposed by: devin·repo#1 (0.5), grok·repo#6 (0.5)

context: `NotificationsRepositoryImpl` injects `TeamTaskDao` (`:35`) and reads the teams feature's table directly: `getById` (`:244`), `getByIds` (`:282`), `getByTitles` (`:346`), `getTasksForUserBetween` (`:396`). Both proposals wrap the DAO in a new pass-through class (`TeamTasksReader` / `TaskNotificationLookup`). That still reads `team_tasks` directly and adds a second path into the teams domain beside the already-injected `Lazy<TeamsNotificationsRepository>` (`:31`, bound at `RepositoryModule.kt:201`).

files:
- main: `repository/TeamsNotificationsRepository.kt`, `repository/TeamsRepositoryImpl.kt` (it already injects `teamTaskDao`, `:77`), `repository/NotificationsRepositoryImpl.kt`
- test: `repository/NotificationsRepositoryImplTest.kt`, `repository/TeamChatBadgeIntegrationTest.kt`

steps:
1. Add the four task reads to `TeamsNotificationsRepository` and implement them in `TeamsRepositoryImpl`.
2. Drop `teamTaskDao` (and, per grok, look at `TeamNotificationDao` too) from `NotificationsRepositoryImpl`.
3. Update both tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Notifications*" --tests "*TeamChatBadge*"` passes, and `NotificationsRepositoryImpl` has no `TeamTaskDao` reference.

size budget: ~60–80 lines, 5 files.

out of scope / notes: The corrected route touches `TeamsRepositoryImpl` (5 open PRs) and `TeamsNotificationsRepository`, so land it after the teams PRs. The pass-through-class version both lists propose is not worth doing.

---

### 47. single-source the meetups sync write (drop CommunitySyncWriter) without swallowing errors (roadmap 1+5)

rating: 44 (evidence 3 · impact 2 · feasibility 2) · proposed by: devin·repo#4 (1)

context: `CommunityRepositoryImpl.insertMeetupsFromSync` writes the events domain's meetups table. Its body matches `EventsRepositoryImpl.batchInsertMeetups` except error handling: the Events version wraps everything in `catch (e: Exception) { printStackTrace(); 0 }`. `TransactionSyncManager` injects `CommunitySyncWriter` only for the `"meetups"` table (`:80, :103`) and saves `heavy_sync_skip_<table>` after the handler (`:257-282`). A straight swap to `batchInsertMeetups` would swallow write failures, including `CancellationException`, and move the checkpoint past a page that was never written.

files:
- main: `services/sync/TransactionSyncManager.kt`, `di/ServiceModule.kt` (`:93`), `di/RepositoryModule.kt` (`bindCommunitySyncWriter`, `:113`), `repository/CommunitySyncWriter.kt` (delete), `repository/CommunityRepositoryImpl.kt`, `repository/EventsRepositoryImpl.kt`
- test: `TransactionSyncManagerTest`, `TransactionSyncManagerCheckpointTest`

steps:
1. Make `batchInsertMeetups` rethrow, or at least rethrow `CancellationException`. Its other caller, `SyncRepositoryImpl.kt:52`, must tolerate that. Alternatively, add a throwing variant to `EventsSyncWriter`.
2. Point `TransactionSyncManager` at `EventsSyncWriter`, and update `ServiceModule`.
3. Remove the binding, the interface, the Community copy and `meetupDao` from `CommunityRepositoryImpl`.
4. Update both TSM tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TransactionSyncManager*" --tests "*Community*" --tests "*Events*"` passes.
- A failing meetups write fails the page instead of advancing the checkpoint.

size budget: ~70–90 lines, 8 files.

out of scope / notes: This collides head-on with PR 15808 (draft sync rewrite: TSM, `ServiceModule`, `CommunitySyncWriter`, `CommunityRepositoryImpl`) and with `RepositoryModule` (15824, 15825). Wait for them.

---

### 48. fix MIME detection for upload file names with spaces or # (dedupe getMimeType) (roadmap 8+9)

rating: 44 (evidence 3 · impact 2 · feasibility 2) · proposed by: gemini·perf#1 (1)

context: `FileUtils.getMimeType` (`utils/FileUtils.kt:233`) uses `MimeTypeMap.getFileExtensionFromUrl`, which returns `""` for names containing spaces, `#` or non-ASCII characters. The upload callers pass decoded file names. `FileUtils.getFileExtension` exists (`:197`), and `Utilities.getMimeType` (`utils/Utilities.kt:89-91`) already implements the robust version. This is a robustness bug, not the performance gain gemini claims.

files:
- main: `utils/FileUtils.kt`, `utils/Utilities.kt`
- test: `utils/FileUtilsTest.kt`

steps:
1. Make `FileUtils.getMimeType` use `getFileExtension` (or delegate to or remove the `Utilities` duplicate, updating its callers).
2. Add a test that `"my photo.png"` maps to `image/png` (check the Robolectric `MimeTypeMap` shadow has the mapping).

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*FileUtilsTest" --tests "*UploadManagerTest"` passes.

size budget: ~10–20 lines, 2–3 files.

out of scope / notes: `FileUtils.kt` is in the **active** PR 17187 (updated 2026-09-24), so do this after it merges.

---

### 49. drop identity .map { it } copies and hoist the loop-invariant split (roadmap 1)

rating: 43 (evidence 4 · impact 1 · feasibility 4) · proposed by: devin·perf#4 (1)

context: `CoursesRepositoryImpl.kt:553-554, 912` and `SubmissionsRepositoryImpl.kt:292, 789` copy DAO results with no-op `.map { it }` / `.map { question -> question }`. The DAOs return read-only `List`s and nobody mutates the copies. `parentId.split("@").firstOrNull()` runs inside the answers loop (`SubmissionsRepositoryImpl.kt:743`), in the sync-download path, not upload as devin's acceptance says.

files:
- main: `repository/CoursesRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`

steps:
1. Delete the identity maps.
2. Hoist `val examIdPart = parentId.split("@").firstOrNull() ?: parentId` above the loop.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CoursesRepository*" --tests "*SubmissionsRepository*"` passes.

size budget: ~10 lines, 2 files.

out of scope / notes: Cleanup only. Both files are open-PR-locked (13657/15808/16624 and 14427/15808), so ride along with task 28 or land after them.

---

### 50. hide SharedPreferences behind a string-store seam in LifeCache (roadmap 9)

rating: 43 (evidence 4 · impact 1 · feasibility 4) · proposed by: codex·repo#3 (1)

context: `repository/LifeCache.kt` imports Android `SharedPreferences` and AndroidX `edit` (`:3-5, 21-23`). That keeps the cache policy out of a platform-free core, and its tests mock `SharedPreferences.Editor` (`LifeCacheTest.kt:19-33`).

files:
- main: `repository/LifeCache.kt`
- test: `repository/LifeCacheTest.kt`

steps:
1. Add a minimal internal `StringStore` (`get(key): String?`, `put(key, value)`).
2. Give `LifeCache` an internal primary constructor that takes the store, plus a public secondary constructor that wraps `SharedPreferences`. Hilt keeps using the `@Provides` in `SharedPreferencesModule`.
3. Rewrite the tests on an in-memory fake, keeping the malformed-JSON, read-through, write-through and defensive-copy cases.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*LifeCacheTest"` passes, and My Life survives a restart.

size budget: ~60–90 lines, 2 files.

out of scope / notes: Speculative until a KMP module split exists. Do it together with task 32 or skip it.

---

### 51. delete the dead MyLibraryDao.getAll (roadmap 1+8)

rating: 43 (evidence 3 · impact 1 · feasibility 5) · proposed by: devin·repo#8 (1)

context: `MyLibraryDao.getAll()` (`:25-26`) has no production caller. Its only use is `ResourcesRepositoryLibrarySyncTest.kt:124`, which counts rows.

files:
- main: `data/room/dao/MyLibraryDao.kt`
- test: `repository/ResourcesRepositoryLibrarySyncTest.kt`

steps:
1. Delete `getAll()`.
2. Assert with the existing `countByTitle(...)` or the test's `getById("res1")` checks. Don't add a test-only `countAll()` as devin proposes; that just swaps one test-only method for another.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepositoryLibrarySyncTest"` passes.

size budget: ~5 lines, 2 files.

out of scope / notes: devin says a `MyLibraryEntity` import would become unused; that import doesn't exist.

---

### 52. view binding for OnboardingAdapter (roadmap 8)

rating: 43 (evidence 3 · impact 1 · feasibility 5) · proposed by: kimi·perf#7 (1)

context: `OnboardingAdapter.instantiateItem()` (`ui/onboarding/OnboardingAdapter.kt:23-38`) inflates `R.layout.onboarding_item` and does three `findViewById`s. It's the last manual-lookup adapter in a view-binding codebase. Generated binding does the same lookups, so there is no speed gain; the value is the convention.

files:
- main: `ui/onboarding/OnboardingAdapter.kt`

steps:
1. Use `OnboardingItemBinding.inflate(...)` and add and return `binding.root`.
2. Drop the unused `ImageView`/`TextView`/`R` imports.

acceptance: `./gradlew assembleDefaultDebug`. Onboarding pages render with the correct colours in light and dark themes.

size budget: ~15 lines, 1 file.

out of scope / notes: None.

---

### 53. skip the deep copy of exam questions in StepExam.insertCourseStepsExams (roadmap 1+9)

rating: 43 (evidence 3 · impact 1 · feasibility 5) · proposed by: claude·perf#8 (1)

context: `StepExam.insertCourseStepsExams` starts with `exam.toKotlinx().jsonObject` (`model/StepExam.kt:43`). `toKotlinx()` (`utils/GsonUtils.kt:48-53`) recursively copies the whole tree, including `questions`, which is then read only for `.size` (`:60`). Its only caller is the exams sync (`SurveysRepositoryImpl.kt:392`), not course sync as claude says. The same questions are deep-copied again by `ExamQuestion.insertExamQuestions`.

files:
- main: `model/StepExam.kt`
- test: `model/StepExamTest.kt`

steps:
1. Build a shallow Gson `JsonObject` from `exam.entrySet()` minus `"questions"`, then call `.toKotlinx()` on it.
2. Set `noOfQuestions = GsonUtils.getJsonArray("questions", exam).size()`.
3. Add tests: 3 questions → 3, and malformed `questions` → 0.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*StepExam*"` passes, and exam question counts are unchanged after sync.

size budget: ~8 main plus ~30 test lines, 2 files.

out of scope / notes: A small saving. Better folded into a broader "stop Gson→kotlinx round-trips in sync parsers" pass that also covers `ExamQuestion.kt:69`.

---

### 54. key ChatAdapter animation state by message id, not adapter position (roadmap 8)

rating: 40 (evidence 3 · impact 1 · feasibility 4) · proposed by: grok·perf#9 (1)

context: `ChatAdapter.animatedMessages` is a `HashMap<Int, Boolean>` keyed by adapter position. `prependMessages` remaps every key when history loads (`ui/chat/ChatAdapter.kt:132-145`), and the bind-time listeners capture `position`, which goes stale after a prepend. `ChatMessage` has a stable `id` (a UUID default). This is a correctness and readability fix, not the performance win grok claims.

files:
- main: `ui/chat/ChatAdapter.kt`, `ui/chat/ChatDetailFragment.kt` (`:514-515` reads `lastAnimatedPosition`)
- test: `ui/chat/ChatAdapterTest.kt`

steps:
1. Key the animation state by `ChatMessage.id` (e.g. `lastAnimatedId`) and drop the remap in `prependMessages`.
2. Resolve items at click time via `bindingAdapterPosition`.
3. Keep, or rename in lockstep, the members `ChatDetailFragment` uses.
4. Add tests: a prepend doesn't re-animate shown responses, and `clearData` resets state.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ChatAdapterTest"` passes. New responses animate once, and loading older history doesn't re-animate.

size budget: ~40–60 lines, 3 files.

out of scope / notes: `ChatMessage` ids are per-instance UUIDs, so re-created messages get new ids. Confirm history loads reuse instances, or key by server id.

---

### 55. unpin the four reason-less SDK-34 adapter/UI tests together (roadmap 8)

rating: 40 (evidence 3 · impact 1 · feasibility 4) · proposed by: codex·perf#1–4 (1, merged: four one-line fragments that save nothing individually)

context: `ui/life/LifeAdapterTest.kt:22`, `ui/sync/ServerAddressAdapterTest.kt:17`, `ui/personals/PersonalsAdapterTest.kt:22` and `ui/voices/VoicesActionsTest.kt:29` pin `sdk = [34]` with no stated reason, and all hash to CI shard 2. `DownloadServiceResumeTest` (also shard 2) keeps a 34 sandbox alive there, so the saving is only per fork: fewer forks need a 34 sandbox. It is never a whole-shard saving unless the DownloadService tests are unpinned too.

files:
- test: the four classes (class `@Config` only)

steps:
1. Remove the four pins together.
2. `VoicesActionsTest`: `VoicesActions` is an `object` whose `dateFormatter` captures `Locale.getDefault()` and `ZoneId.systemDefault()` on first load (`VoicesActions.kt:34-36`). On the shared 36 sandbox, the formatted-date test becomes order-dependent. Either make the formatter resolve the zone at format time, or note the assumption.
3. Update `docs/TESTING.md:143-150`; the table already omits `PersonalsAdapterTest`, `MyLibraryDaoTest` and `CoursesItemUtilsTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` passes.

size budget: ~4–10 lines, 4–5 files plus docs.

out of scope / notes: Unpinning the three DownloadService tests is a separate, riskier call (FGS-type APIs).

---

### 56. make ConfigurationsRepository.checkHealth return a result type instead of R-string text (roadmap 1+9)

rating: 39 (evidence 2 · impact 1 · feasibility 5) · proposed by: gemini·repo#3 (1)

context: `ConfigurationsRepositoryImpl.checkHealth()` returns a `String` built partly from `context.getString(R.string.server_sync_successfully)`. Its only caller is `AutoSyncWorker`, which just logs it. `SyncActivity` doesn't call it, despite gemini's claim. `Context` stays in the class for about 12 other uses.

files:
- main: `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `services/AutoSyncWorker.kt`
- test: `repository/ConfigurationsRepositoryImplTest.kt` (`:110-150`)

steps:
1. Return a small sealed or enum result that includes `NotConfigured` (for today's blank-URL `""`) and `InitFailed`.
2. Log its name in `AutoSyncWorker`.
3. Update the test assertions.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ConfigurationsRepositoryImplTest" --tests "*AutoSyncWorker*"` passes.

size budget: ~30–40 lines, 4 files.

out of scope / notes: This removes one R usage; it does not decouple Context. Sequence with tasks 17 and 41.

---

### 57. route the hand-rolled dataUpdateFlow filters through RealtimeSyncManager.updatesFor (roadmap 1+3)

rating: 39 (evidence 2 · impact 1 · feasibility 5) · proposed by: codex·repo#2 (1)

context: `RealtimeSyncManager` exposes `updatesFor(table)` (`services/sync/RealtimeSyncManager.kt:20-27`). `RealtimeSyncHelper.setupRealtimeSync` (`ui/sync/RealtimeSyncMixin.kt:29-38`) filters the raw `dataUpdateFlow` by a table set itself. `HealthViewModel:72` and `UserRepositoryImpl:90` do the same by hand for single tables.

files:
- main: `services/sync/RealtimeSyncManager.kt`, `ui/sync/RealtimeSyncMixin.kt`, `ui/health/HealthViewModel.kt`
- test: `services/sync/RealtimeSyncManagerTest.kt`

steps:
1. Add `updatesFor(tables: Set<String>)`, and make the string overload delegate to it.
2. Use it in `RealtimeSyncHelper` and `HealthViewModel`.
3. Add `updatesFor` tests: exclusion and ordering. None exist today.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RealtimeSync*" --tests "*HealthViewModel*"` passes.

size budget: ~30–40 lines, 4 files.

out of scope / notes: For an empty set, prefer a never-emitting filter over `emptyFlow()`, which completes. `UserRepositoryImpl` is locked by 15808, so skip it there. No change to debounce or buffers.

---

### 58. DiagnosticsRepositoryImpl: log with Log.w instead of printStackTrace, and rethrow CancellationException (roadmap 8)

rating: 36 (evidence 2 · impact 1 · feasibility 4) · proposed by: codex·repo#7 (1)

context: `saveLogToRoom` and `saveLogsToRoom` catch `Exception` and call `printStackTrace` (`repository/DiagnosticsRepositoryImpl.kt:59-76, 79-95`). The `catch` also swallows `CancellationException` (`:73, :92`). codex's other steps (shared metadata, "no partial-row fallback") are already how the code works.

files:
- main: `repository/DiagnosticsRepositoryImpl.kt`
- test: `repository/DiagnosticsRepositoryImplTest.kt`

steps:
1. Replace `printStackTrace()` with `Log.w(TAG, …, e)`.
2. Rethrow `CancellationException`.
3. Add one test for the rethrow.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*DiagnosticsRepositoryImplTest"` passes.

size budget: ~10–20 lines, 2 files.

out of scope / notes: Don't remove the logging outright; this is the only diagnostic for a failed crash-log write.

---

### 59. setHasFixedSize(true) on the Notifications and Course Progress lists (roadmap 7)

rating: 36 (evidence 2 · impact 1 · feasibility 4) · proposed by: kimi·perf#6 (1)

context: `binding.rvNotifications` (`ui/notifications/NotificationsFragment.kt:65`) and `rvMyprogress` (`ui/courses/CoursesProgressFragment.kt:20`) are `match_parent` and don't call `setHasFixedSize(true)`. kimi's third site, `rvReports`, is `wrap_content` (`fragment_reports.xml:52`), so the flag would be wrong there. That contradicts the list's own precondition check.

files:
- main: `ui/notifications/NotificationsFragment.kt`, `ui/courses/CoursesProgressFragment.kt`

steps: Add `setHasFixedSize(true)` after each `layoutManager =`. Leave `rvReports` alone.

acceptance: `./gradlew assembleDefaultDebug`. Both lists render and scroll unchanged.

size budget: 2 lines, 2 files.

out of scope / notes: Tiny benefit; see task 42.

---

### 60. narrow ResourcesUploadSource interface for resource upload serialization (roadmap 1+4)

rating: 36 (evidence 2 · impact 1 · feasibility 4) · proposed by: kimi·repo#4 (1)

context: `ResourcesRepository.serializeForUpload(library, user)` (`:139`) is consumed only by `services/upload/UploadConfigs.kt:281`, yet sits on the general resources interface. kimi wants to move it into `UploadRepository`, which it says "already owns upload serialization". That's false: `UploadRepository` only sends data, and each domain repository owns its serializer. The serializer also needs `timeProvider` and `deviceNameProvider` from `ResourcesRepositoryImpl` (`:68-69`).

files:
- main: new `repository/ResourcesUploadSource.kt`, `repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`, `services/upload/UploadConfigs.kt`
- test: `services/upload/UploadConfigsTest.kt` (`:51`)

steps:
1. Following the `TeamsSyncRepository` pattern, declare `ResourcesUploadSource` with `getPendingResourceUploads`, `serializeForUpload` and `markResourceUploaded`, implemented by `ResourcesRepositoryImpl`.
2. Have `UploadConfigs` depend on it. That needs one `@Binds`, and `RepositoryModule` is locked by 15824/15825. Alternatively, inject the Impl-bound interface via an existing binding.
3. Update the test.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*UploadConfigsTest" --tests "*ResourcesRepository*"` passes.

size budget: ~40–60 lines, 5 files.

out of scope / notes: Small benefit. Sequence with tasks 6, 16 and 45 (same Impl).

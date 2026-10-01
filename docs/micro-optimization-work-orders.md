# myPlanet refactor round — 10 micro-optimization work orders

- date: 2026-10-01
- base commit: 286e66e0e1daf285c34d8456deb0028016bf858a (`all: smoother importing (fixes #17814)`)
- open PRs checked: 17812, 17811, 17776, 17694, 17680, 17435, 17254, 17187, 16623, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075 — every file an open PR touches was excluded; every path cited below was opened and confirmed to exist on the base commit
- focus: micro-optimizations removable without rewrites, drawn from code and the last `myPlanet test` run on master (workflow run 36847822998, green; heavy `setVisible` deprecation spam and `android.enableJetifier` deprecation noted in the logs but owned by open PRs touching gradle files)
- rule reminders: each task is independently mergeable in any order; no file appears in more than one task; no new dependencies, no TODOs

---

### 1. chunk the last unchunked DAO `IN` queries (roadmap 1+7)

context: Most DAOs already wrap list-binding queries with `distinct().chunked(900)` (pattern from commit eaa529929, e.g. `TeamDao` / `SubmitPhotosDao.getByIds`), because SQLite caps bound variables at 999 — three survivors still bind unbounded `IN (:ids)` lists and can throw `SQLiteException` on large batches: `QuestionDao.getByIds` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:10`, `UserDao.getUsersByAnyIds` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:13-14`, `UserDao.getGuestUsersByNames` at `UserDao.kt:45-46`.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt` (`getByIds`); `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt` (`getUsersByAnyIds`, `getGuestUsersByNames`). Do NOT touch the already-chunked siblings (`getByExamIds`, `deleteByIds`) or any other DAO — most are owned by open PRs.

steps:
1. In `QuestionDao`, rename `getByIds` to `getByIdsInternal` and add a public `getByIds(ids)` wrapper returning `emptyList()` on empty input and `ids.distinct().chunked(900).flatMap { getByIdsInternal(it) }` otherwise, matching the file's existing `getByExamIds` wrapper.
2. In `UserDao`, apply the same rename+wrapper to `getUsersByAnyIds`.
3. Apply the same rename+wrapper to `getGuestUsersByNames`.
4. Add `@Transaction` on the multi-query wrappers only where the file already uses it for that pattern (`UserDao.deleteByIds` has it; `QuestionDao.getByExamIds` does not — mirror each file's own convention).

acceptance: `./gradlew testDefaultDebugUnitTest` green; callers are unchanged since the public signatures are kept — exam question loads and guest-user lookups return the same rows for >999-id inputs instead of crashing.

size budget: ~20 changed lines, 2 files.

out of scope: no caller changes, no new DAOs, no query-logic rewrites.

---

### 2. make `ANRWatchdog`'s cross-thread fields `@Volatile` (roadmap 7+8)

context: `ANRWatchdog.kt:25-26` declares `private var isWatching` and `private var tick` as plain fields shared across threads: `tick` is written on the main thread by `tickUpdater` (line 28, posted at lines 46 and 52) and read on a `dispatcherProvider.default` coroutine (lines 49-52), while `isWatching` is written by `stop()` (line 86) on the caller's thread and read inside the loop. Without `@Volatile` the watchdog can read a stale `tick` and file false ANR reports, or never observe `stop()`. `SyncTimeLogger` in the same package already marks equivalent shared state `@Volatile` (`SyncTimeLogger.kt:39-44`).

files: `app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt` (`isWatching`, `tick` fields). Leave `MainApplication` alone — it is owned by open PRs.

steps:
1. Add `@Volatile` to `private var isWatching` (line 25).
2. Add `@Volatile` to `private var tick` (line 26).
3. Confirm no other shared state in the file needs the same treatment (`job` and `mainHandler` are only touched on the owning scope/looper — leave them).

acceptance: `./gradlew testDefaultDebugUnitTest` green; ANR reporting still fires after the timeout and `stop()` still halts the watchdog.

size budget: ~4 changed lines, 1 file.

out of scope: no behavior redesign of the detection loop, no listener API changes.

---

### 3. route `SyncActivity`'s raw clock reads through the injected `TimeProvider` (roadmap 4+8)

context: `SyncActivity` already injects `timeProvider` and uses it at lines 365 and 590, but four spots bypass it with wall-clock calls: `SyncActivity.kt:617` (`prefData.getLastSync(), System.currentTimeMillis(), maxDays`), `:649` and `:735` (`prefData.setLastUsageUploaded(Date().time)`), and `:737` (`TimeUtils.getRelativeTime(Date().time, timeProvider)`). The mixed clocks make the last-usage-upload threshold and the relative-time label untestable and inconsistent with the rest of the activity — a DI-cleanup loose end that also strips `java.util.Date` coupling (helps roadmap 9's platform-free core).

files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` (the four sites above). Leave `TimeUtils` and `SharedPrefManager` alone — only the call sites change.

steps:
1. Replace `System.currentTimeMillis()` at line 617 with `timeProvider.now()`.
2. Replace `Date().time` at lines 649 and 735 with `timeProvider.now()`.
3. Replace `Date().time` inside the `getRelativeTime` call at line 737 with `timeProvider.now()`.
4. Drop the `java.util.Date` import if it is now unused.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the sync screen still shows the "last sync" relative label and the usage-upload branch still fires past the `maxDays` threshold.

size budget: ~8 changed lines, 1 file.

out of scope: no changes to `SharedPrefManager` accessors, no refactoring of the sync flow itself.

---

### 4. replace stderr writes and a dead catch in `utils` with `Log` (roadmap 8)

context: Three utility files write failures to stderr, which logcat loses in release builds. `Utilities.kt:59-64` wraps `Toast.makeText(...).show()` in `catch (e: IllegalAccessException)` — an exception that call never throws, so the guard is dead and real failures (e.g. a bad window token) still crash. `KeyboardUtils.kt:15-17` and `RetryUtils.kt:29` call `printStackTrace()`; `RetryUtils.kt:26` also references `kotlinx.coroutines.delay` fully qualified inside `retry`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt` (toast helper, lines 55-66); `app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt` (`hideSoftKeyboard`); `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` (`retry`). Leave `DialogUtils`, `Log.e` conventions elsewhere unchanged.

steps:
1. In `Utilities`, widen the catch to `Exception` and log via `Log.w` with a class-tag constant instead of `printStackTrace`.
2. In `KeyboardUtils`, replace `e.printStackTrace()` with `Log.w` on a file-level `TAG`.
3. In `RetryUtils`, import `kotlinx.coroutines.delay`, drop the qualified call, and replace `lastException?.printStackTrace()` with `Log.e` on a file-level `TAG`.
4. Remove now-unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` green; toasts still show on valid contexts and a bad context logs instead of crashing; `retry` still retries and rethrows the last exception.

size budget: ~15 changed lines, 3 files.

out of scope: no change to retry semantics, no new logging framework.

---

### 5. replace `printStackTrace` in models and base fragments with `Log` (roadmap 8+9)

context: Five files still dump to stderr on failure: `model/UserEntity.kt` lines 64, 137, 225; `model/HealthExamination.kt:69`; `model/Feedback.kt:103`; `base/BaseContainerFragment.kt:176,178`; `base/BaseTeamFragment.kt:62`. In the `model/` files this is also a platform-coupling smell — model serializers should emit nothing to stderr on the road to roadmap 9's zero-`android.*` core — and all sites already return a safe fallback (empty string / `null`) after the trace.

files: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt`, `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt`, `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt`. Do NOT touch `FileUtils`, `TransactionSyncManager`, or other callers — several are owned by open PRs and none need edits anyway.

steps:
1. In each model file, replace `printStackTrace()` with `Log.e(TAG, "<short message>", e)`; add a `private const val TAG` where the file lacks one (models already import `android.util.Log` where needed — check per file).
2. Same replacement in `BaseContainerFragment` (both catches at 176 and 178) and `BaseTeamFragment` (line 62).
3. Keep every existing fallback return value exactly as-is.
4. Remove now-unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` green; serializers still return their fallback values on malformed JSON and the base fragments still dismiss dialogs safely.

size budget: ~20 changed lines, 5 files.

out of scope: no changes to serialization logic or fallback values, no new logging abstraction.

---

### 6. replace `printStackTrace` in the UI layer with `Log` (roadmap 8)

context: Four UI files still call `printStackTrace()`: `ui/chat/ChatDetailFragment.kt` lines 368 and 642, `ui/courses/TakeCourseViewModel.kt:122`, `ui/health/HealthExaminationViewModel.kt:108`, and `ui/viewer/WebViewActivity.kt:182` (inside the `WebViewAssetLoader.PathHandler` at lines 174-188). Stderr output is invisible to logcat-based crash triage, and each site already degrades gracefully.

files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt`. Leave sibling files in those packages alone — several are owned by open PRs.

steps:
1. In each file add or reuse a `TAG` constant and replace each `printStackTrace()` with `Log.e(TAG, "<short message>", e)`.
2. In `WebViewActivity`, keep the `PathHandler`'s return contract identical (return `null`/existing value on failure — only the logging changes).
3. Remove now-unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the web viewer still renders local assets and a missing file still returns null from the path handler.

size budget: ~12 changed lines, 4 files.

out of scope: no ViewModel state changes, no WebView security or path changes.

---

### 7. deduplicate `DownloadUtils`' twin URL-enqueue blocks (roadmap 8, helps 5)

context: `DownloadUtils.kt` lines 147-160 (`openPriorityDownloadService`) and 162-176 (`openDownloadService`) repeat the same `getSharedPreferences` → `getStringSet` → `toMutableSet().apply { addAll(urls) }` → `edit { putStringSet }` sequence, differing only in the preference key and the `fromSync` flag passed to `startDownloadServiceSafely`. Consolidating the merge logic is a small step toward roadmap 5's consolidate-sync/upload direction. The file also ends in a run of ~8 trailing blank lines (lines ~258-267).

files: `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt` (`openPriorityDownloadService`, `openDownloadService`, new private helper). Do NOT touch `DownloadService` or `ResourceDownloadCoordinator` — only the constants they expose are referenced, and `DownloadUtilsTest` must keep passing unmodified.

steps:
1. Extract a private `enqueueUrls(context: Context, key: String, urls: ArrayList<String>)` performing the get-set-merge-write once.
2. Have both public functions call it with `DownloadService.PRIORITY_DOWNLOADS_KEY` / `DownloadService.PENDING_DOWNLOADS_KEY` then call `startDownloadServiceSafely` as today.
3. Delete the trailing blank lines at end of file.
4. Remove now-unused imports if any.

acceptance: `./gradlew testDefaultDebugUnitTest` green; enqueuing URLs still merges with already-pending sets for both keys and still starts the service with the right `fromSync` flag.

size budget: ~30 changed lines, 1 file.

out of scope: no DI refactor of the `getSharedPreferences` access, no changes to `startDownloadServiceSafely` internals.

---

### 8. stop comparing URL schemes against localized strings in `getMinApk` (roadmap 8, helps 9)

context: `ConfigurationsRepositoryImpl.kt:298-301` picks the failure message via `when (NetworkUtils.extractProtocol(url)) { context.getString(R.string.http_protocol) -> ...; context.getString(R.string.https_protocol) -> ... }`. `extractProtocol` returns a literal `"<scheme>://"` (`NetworkUtils.kt:247-251`), so the comparison works only because every translation happens to keep the strings identical — a silent fragility, and it couples repository logic to `android` string resources, which roadmap 9 wants gone from the data layer.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (`getMinApk`, lines ~292-304). Do NOT touch `strings.xml` or any `values-*` resource — owned by open PRs and not needed.

steps:
1. Replace the localized-resource comparisons with literal `"http://"` / `"https://"` (or private `const val` scheme strings near the top of the file).
2. Keep `context.getString` only for the user-facing error-message resources.
3. Confirm no other `extractProtocol` result is compared against resources in this file.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `getMinApk` still maps an `http` failure to the local-server message and `https` to the nation-server message.

size budget: ~8 changed lines, 1 file.

out of scope: no change to the error strings themselves, no API or interface changes.

---

### 9. drop `DictionaryActivity`'s redundant `loadCount()` call (roadmap 7+8)

context: `DictionaryActivity.kt:58-59` calls `viewModel.loadCount()` immediately followed by `viewModel.loadDictionary()`. `loadDictionary` already ends by emitting `DictionaryLoadState.Populated(dictionaryRepository.count())` after seeding (`DictionaryViewModel.kt:41-44`), so `loadCount()` runs a duplicate `COUNT` query and can emit a stale `Populated(0)`/`Populated(N)` before seeding finishes — two launches racing to write the same `StateFlow` on every screen open.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt` (`onCreate`), `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt` (`loadCount`, lines 51-55). `DictionaryViewModelTest` must keep passing — update it only if it calls `loadCount` directly (check first; it exists at `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt`).

steps:
1. Remove the `viewModel.loadCount()` call in `DictionaryActivity.onCreate`.
2. Delete the now-unused `loadCount()` method from `DictionaryViewModel`.
3. If the existing test invoked `loadCount`, point it at `loadDictionary` instead; otherwise leave tests untouched.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the dictionary screen still shows `list_size` after seeding and still triggers the download path when the dictionary file is missing.

size budget: ~10 changed lines, 2 files (+ a test edit only if required).

out of scope: no changes to `DictionaryRepository` or the seeding mutex logic.

---

### 10. remove the fixed 200 ms sleep before notification read-receipt broadcasts (roadmap 7)

context: `NotificationActionReceiver.kt:102-104` wraps the read-receipt broadcasts in `withContext(dispatcherProvider.main) { delay(200); ... }`. The repository write at lines 92-97 has already completed before this block, so the fixed sleep only delays the badge refresh ~200 ms per notification, and `context.sendBroadcast` does not require the main dispatcher. The nested try blocks inside the same `withContext` add no ordering guarantees the delay would have provided.

files: `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt` (`markNotificationAsRead`, lines ~88-128). `NotificationActionReceiverTest` at `app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt` must keep passing — read it first since it may assert on ordering.

steps:
1. Delete `delay(200)` and its import if unused.
2. Keep the system broadcast, the local broadcast via `broadcastService`, and the `DashboardActivity` refresh intent in the same order, but send them without the artificial sleep (leave them inside `withContext(dispatcherProvider.main)` only if the test or a receiver needs main-thread semantics — check the test first).
3. Keep every existing `try/catch` and log line.

acceptance: `./gradlew testDefaultDebugUnitTest` green — including `NotificationActionReceiverTest`; marking a notification read from the system shade still clears the badge and refreshes the dashboard.

size budget: ~10 changed lines, 1 file.

out of scope: no changes to notification channels, repository methods, or the broadcast action strings.

---

## notes for executors

- Run `./gradlew testDefaultDebugUnitTest` (with `-ProbolectricOffline=true` as CI does) before and after; the current baseline is green on `286e66e0e`.
- Files were selected against the open-PR list above — if a new PR has since touched a cited file, skip that task rather than resolving the conflict blindly.
- Several attractive areas were checked and deliberately excluded: `app/build.gradle`, `gradle.properties`, and `.github/workflows/{build,release,test,labels}.yml` (including the `enableJetifier`/`setVisible` deprecation spam in CI logs) are all owned by open PRs, as are `FileUtils`, `TransactionSyncManager`, `ActivitiesRepositoryImpl`, and most of `di/` and `ui/teams`.

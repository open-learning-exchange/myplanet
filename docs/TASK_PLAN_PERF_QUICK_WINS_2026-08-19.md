# myPlanet refactor round — task work orders

- **Date:** 2026-08-19
- **Base commit:** `9c54a0341557a7e7ae4bdc313fd1c97c0cc23b32` (`actions: smoother workflow automerge release retrying (fixes #15814) (#15813)`)
- **Open PRs checked (42):** 15820, 15808, 15772, 15771, 15769, 15699, 15698, 15656, 15650, 15614, 15594, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14457, 14427, 14030, 13928, 13848, 13657, 13604, 13447, 13415, 13355, 13287, 13051, 10993, 8175, 7052, 5977, 5243, 4075
- Files touched by those PRs (686 paths, including `TeamsRepositoryImpl.kt`, `UploadManager.kt`, `TransactionSyncManager.kt`, `UrlUtils.kt`, `FileUtils.kt`, `BaseRecyclerFragment.kt`, `DashboardActivity.kt`, `RequestsViewModel.kt`, `UserRepositoryImpl.kt`, `AppDatabase.kt`, and all `ui/teams/members`, `ui/courses/CoursesFragment.kt`, `ui/voices/VoicesAdapter.kt`, `ui/surveys` files) were excluded from every task below. Some off-limits files are cited as *evidence callers only* and are explicitly marked do-not-touch.

---

### 1. Cache pattern-based `DateTimeFormatter`s in TimeUtils (roadmap 7, 9)
context: `TimeUtils.formatDate(date, format)` at `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt:137` builds a new `DateTimeFormatter.ofPattern(...)` on every call (line 142: `val formatter = DateTimeFormatter.ofPattern(format ?: "", defaultLocale).withZone(ZoneId.systemDefault())`). Pattern parsing is one of the most expensive parts of `java.time` setup, and this runs per row in adapters — `HealthExaminationAdapter.kt:62`, `EnterprisesReportsAdapter.kt:59` and `:70`, `EnterprisesFinancesAdapter.kt:42` — and per day cell in the calendar binder. `getAge` at `TimeUtils.kt:89` also constructs two formatters per call (lines 96 and 99). DateTimeFormatter is immutable and thread-safe, so caching is safe; the existing `by lazy` formatters in the same file already pin the locale at first use, so semantics are unchanged.
files: `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt` (functions `formatDate(date: Long, format: String?)` line 137, `getAge` line 89). Extend `app/src/test/java/org/ole/planet/myplanet/utils/TimeUtilsTest.kt`. Do NOT touch the adapter callers (`HealthExaminationAdapter.kt`, `EnterprisesReportsAdapter.kt`, `EnterprisesFinancesAdapter.kt`) or `TeamCalendarFragment.kt` (off-limits, open PR 15158/15266) — the fix is centralized.
steps:
1. Add a private `ConcurrentHashMap<String, DateTimeFormatter>` to the `TimeUtils` object.
2. In `formatDate(date, format)`, replace the per-call `ofPattern` with `getOrPut` on the cache (blank/invalid patterns must keep returning `""` via the existing try/catch).
3. In `getAge`, hoist the `"yyyy-MM-dd HH:mm:ss"` and `"yyyy-MM-dd"` formatters into private vals alongside the existing lazy formatters.
4. Add unit tests in `TimeUtilsTest.kt` asserting identical output for repeated calls with the same pattern and that an invalid pattern still returns `""`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.TimeUtilsTest"` passes; full `./gradlew testDefaultDebugUnitTest` stays green; health examinations list and enterprise reports list still render the same date strings.
size budget: ~25 changed lines, 2 files.
out of scope: no caller changes, no locale-change handling beyond current behavior, no new caching library.

---

### 2. Replace linear scans with map lookups in UploadCoordinator batch reconciliation (roadmap 5, 7)
context: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` reconciles upload results with four O(n·m) scans per batch of 50: line 69 `succeeded.filter { it !in dbFailed }` (List containment with data-class equals), line 258 `succeeded.find { it.localId == failedResult.localId }` inside `failedResults.mapNotNull`, and the duplicated pair at lines 325 and 441 in the Room variant. Every sync upload pays this per batch per config. Building one `associateBy { it.localId }` and one `HashSet` of failed local ids per batch turns this into O(n).
files: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` (functions `upload` line ~69, `updateDatabaseBatch` line ~258, `uploadRoom` line ~325, `updateDatabaseBatchRoom` line ~441). Add `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadCoordinatorTest.kt` (new file — only `UploadConfigsTest.kt` exists there today). Do NOT touch `UploadManager.kt`, `UploadConfigs.kt`, or `UploadRepository.kt` (open PR 15808 owns the upload/sync area around them).
steps:
1. In both `updateDatabaseBatch` and `updateDatabaseBatchRoom`, build `succeeded.associateBy { it.localId }` once and replace the `find` with a map lookup.
2. In both `upload` and `uploadRoom`, build `dbFailed.mapTo(HashSet()) { it.localId }` once and filter on `it.localId !in dbFailedIds`.
3. Confirm `localId` is unique within a batch (it is the local primary key passed through `PreparedUpload`) and add a comment-free, behavior-identical implementation.
4. Add unit tests covering: db-failed items are excluded from `allSucceeded`, and the returned `failedLocally` list matches the previous semantics for duplicate-free batches.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.upload.*"` passes; full `./gradlew testDefaultDebugUnitTest` green; a sync with pending submissions still uploads and clears the pending queue.
size budget: ~40 changed lines (including new test), 2 files.
out of scope: no changes to retry queuing, batch sizes, or network code; no behavior change for duplicate localIds.

---

### 3. Run notification sync-marking in one Room transaction (roadmap 1, 7, 9)
context: `NotificationsRepositoryImpl.markNotificationsSynced` at `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:323` loops `syncResults.forEach { (id, rev) -> notificationDao.markSynced(id, rev) }`, issuing one SQLite write transaction per notification after every sync (called from `TransactionSyncManager.kt:503` — off-limits, do not touch). `NotificationDao` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt:48`) exposes only the single-row `markSynced(id, rev)`. Room supports `@Transaction` on non-abstract DAO methods, so a batch wrapper collapses N fsyncs into one commit without changing any SQL.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` (add one method), `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (swap the loop), `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` (update the two tests at lines 357–372). Do NOT touch `NotificationsRepository.kt` (interface unchanged), `TransactionSyncManager.kt`, or `AppDatabase.kt`.
steps:
1. Add to `NotificationDao`: a `@Transaction`-annotated `suspend fun markSyncedBatch(results: List<Pair<String, String?>>)` with a body that calls the existing `markSynced(id, rev)` per element.
2. In `markNotificationsSynced`, keep the `isEmpty()` early return and replace the `forEach` with one `notificationDao.markSyncedBatch(syncResults)` call.
3. Update `NotificationsRepositoryImplTest`: `markNotificationsSynced marks all as synced` now verifies one `markSyncedBatch(syncResults)` call; the empty-list test verifies `markSyncedBatch` is never called.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest"` passes; full suite green; after a sync, notifications no longer re-upload (rev persisted) as before.
size budget: ~20 changed lines, 3 files.
out of scope: no new `@Query` SQL (revs differ per row, so a single UPDATE is not applicable); no schema change, no version bump.

---

### 4. Delete the dead `batchDocuments` accumulator in resource sync (roadmap 5, 7)
context: In `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `resourceTransactionSync` builds `val batchDocuments = JsonArray()` at line 328 and appends every valid doc at line 338 — but the array is never read (the insert path uses `validDocuments.map { it.first }` at line 349). Every batch of the resources table sync allocates and grows a throwaway `JsonArray` holding full document trees, doubling per-batch memory pressure on low-end devices during the longest sync phase. This is pure dead code.
files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` (lines 328 and 338, plus the now-unused `import com.google.gson.JsonArray` at line 13 — verify no other use remains after the edit). Do NOT touch `TransactionSyncManager.kt`, `HeavyTableSyncWorker.kt`, or `ServerUrlMapper.kt` (open PR 15808); do not alter the parse loop's filtering logic (`_design` doc skip, blank id skip).
steps:
1. Remove the `batchDocuments` declaration and the `batchDocuments.add(doc)` line.
2. Remove `import com.google.gson.JsonArray` if it is unused after step 1 (check the whole file first).
3. Run the existing `SyncManagerTest` plus the sync test package.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.*"` passes; full suite green; a full sync still populates the resources table (identical `resourcesRepository.batchInsertResources(docs)` input).
size budget: ~4 changed lines, 1 file.
out of scope: no restructuring of the batch loop, no logging changes, no behavior change to checkpoint writes.

---

### 5. Make map-tile asset copy skip existing files and missing assets (roadmap 7)
context: `MapTileUtils.copyAssets` (`app/src/main/java/org/ole/planet/myplanet/utils/MapTileUtils.kt:12`) runs on every cold start from `OnboardingActivity.kt:66` (main thread). It unconditionally re-copies both `.mbtiles` files (multi-MB disk writes), never creates the destination directory (`FileOutputStream(outFile)` at line 20 throws if `osmdroid/` is absent), and the assets are not even present in `app/src/main/assets` in this repo, so a guaranteed `IOException` plus `printStackTrace` fires on every launch. `MapTileUtilsTest.kt` already mocks `AssetManager`, so guards are unit-testable.
files: `app/src/main/java/org/ole/planet/myplanet/utils/MapTileUtils.kt` (functions `copyAssets` line 12, `copyFile` line 30). Extend `app/src/test/java/org/ole/planet/myplanet/utils/MapTileUtilsTest.kt`. Do NOT touch `OnboardingActivity.kt` or `OfflineMapsActivity.kt` (callers stay identical).
steps:
1. Before opening the asset, check `context.assets.list("")` membership (or catch the missing-asset case per file) and skip files that are not bundled.
2. Create `outFile.parentFile` with `mkdirs()` before opening the output stream.
3. Skip the copy when `outFile` already exists with a length greater than 0 (tiles are static content; no versioning exists today).
4. Keep per-file failure isolation (one bad tile must not block the other) and extend the test: existing destination → `AssetManager.open` never called; missing asset → no exception, no file created.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.MapTileUtilsTest"` passes; full suite green; first launch shows no `MapTileUtils` stack trace in logcat, and the offline map screen still opens.
size budget: ~35 changed lines, 2 files.
out of scope: no move of the copy off the main thread (bigger refactor), no changes to osmdroid configuration.

---

### 6. Remove the JSON round-trip in `UserEntity.addImageUrl` (roadmap 1, 7, 9)
context: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt:162` does `JsonParser.parseString(jsonDoc["_attachments"].asJsonObject.toString())` — serializing a `JsonObject` to a String and immediately re-parsing it to get back the identical object — then iterates the entry set only to `break` after the first key (lines 163–168). This runs once per user document during user sync (`UserRepositoryImpl.kt:270`, off-limits — cite only). The round-trip allocates the full attachment JSON as a string per user for nothing; the loop is a `firstOrNull`.
files: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt` (function `addImageUrl` lines 158–169). Extend `app/src/test/java/org/ole/planet/myplanet/model/UserEntityTest.kt`. Do NOT touch `UserRepositoryImpl.kt` (open PR 15808/15656 area) or `UrlUtils.kt` (open PR 15614).
steps:
1. Replace the parse round-trip with `jsonDoc["_attachments"].asJsonObject` used directly.
2. Replace the for-with-break loop with `entrySet().firstOrNull()?.key` and keep the same `UrlUtils.getUserImageUrl(id, key)` assignment and no-op-on-empty behavior.
3. Remove the now-unused `JsonParser` import if nothing else in the file uses it.
4. Add tests in `UserEntityTest.kt`: doc with `_attachments` sets `userImage` from the first key; doc without `_attachments` leaves `userImage` untouched.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.model.UserEntityTest"` passes; full suite green; user profile images still resolve after login/sync.
size budget: ~20 changed lines, 2 files.
out of scope: no changes to image URL construction or to how many attachments are considered (still first-key-only, matching current behavior).

---

### 7. Replace read-modify-write with a single UPDATE in `markResourcesAsNotOffline` (roadmap 1, 7, 9)
context: `ResourcesRepositoryImpl.markResourcesAsNotOffline` at `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt:678` loads every matching `MyLibrary` row (`myLibraryDao.getOfflineByResourceIds`), flips `resourceOffline = false` in memory, and re-writes the full rows via `upsertAll` — full-row SELECT plus full-row INSERT-or-REPLACE per resource just to clear one flag. `FreeSpaceWorker` calls this with every id whose files it deleted (`FreeSpaceWorker.kt:47,52` — do not touch). `MyLibraryDao.kt:125` already has the `resourceOffline = 1` WHERE clause; a dedicated UPDATE turns this into one statement.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` (add one `@Query`), `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` (function `markResourcesAsNotOffline` line 678), `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` (add coverage). Do NOT touch `MyLibrary.kt` (open PR 15771/13848), `FreeSpaceWorker.kt`, or `ResourcesRepository.kt` (interface signature unchanged).
steps:
1. Add to `MyLibraryDao`: `@Query("UPDATE my_library SET resourceOffline = 0 WHERE resourceId IN (:resourceIds) AND resourceOffline = 1") suspend fun clearOfflineByResourceIds(resourceIds: List<String>)` (chunk at 900 ids if the DAO already chunks `IN` queries elsewhere — mirror the existing pattern at `ResourcesRepositoryImpl.kt:537`).
2. Rewrite `markResourcesAsNotOffline` to keep its `isEmpty()` early return and delegate to the new DAO method; delete the load/mutate/upsert block.
3. Add a repository test verifying the DAO call is made with the same ids and that empty input short-circuits.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest"` passes; full suite green; after freeing space, previously-offline resources show as online again.
size budget: ~30 changed lines, 3 files.
out of scope: no changes to `getOfflineByResourceIds` (other callers may exist — leave it), no schema or index changes.

---

### 8. Collapse personal-upload post-sync update into one DAO statement (roadmap 1, 7)
context: `PersonalsRepositoryImpl.updatePersonalAfterSync` at `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt:76` does `findById(id)` then mutates three fields then `personalDao.update(personal)` — two round-trips and a full-row rewrite per uploaded personal resource, inside the per-item upload loop (`uploadPersonalDocument`, line 97). `PersonalDao` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`) has no targeted update query. One `UPDATE my_personal SET isUploaded = 1, _id = :newId, _rev = :rev WHERE id = :id` replaces the whole read-modify-write.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt` (add one method), `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` (function `updatePersonalAfterSync` line 76), `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` (rewrite the test at line 176 `updatePersonalAfterSync updates fields properly`). Do NOT touch `PersonalsRepository.kt` (interface unchanged) or `UploadToShelfService.kt`.
steps:
1. Add to `PersonalDao`: `@Query("UPDATE my_personal SET isUploaded = 1, _id = :newId, _rev = :rev WHERE id = :id") suspend fun markUploaded(id: String, newId: String, rev: String): Int`.
2. Replace the body of `updatePersonalAfterSync` with the single DAO call (drop the `findById`/`update` pair; no return-value handling needed — current code is a no-op when the row is missing, and an UPDATE of zero rows matches that).
3. Update the test to stub `personalDao.markUploaded(...)` and verify it is called with `("test-id", "new-id", "rev-1")`, and that `findById` is no longer consulted.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.PersonalsRepositoryImplTest"` passes; full suite green; uploading a personal resource still marks it uploaded and stops it reappearing in the pending list.
size budget: ~25 changed lines, 3 files.
out of scope: no change to `updatePersonalResource` (its updater-callback shape genuinely needs the row); no upload-flow changes.

---

### 9. Build the label reverse-lookup once per filter pass in VoicesViewModel (roadmap 3, 7)
context: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt:110` (in `filterNews`) and `:206` (in `collectLabels`) run `Constants.LABELS.entries.find { it.value == label }` — a linear scan of the whole label map — for every label of every news item. `filterNews` executes on every keystroke of voices search and every news-list emission (it sits in the `combine(...)` pipeline feeding `filteredNews`), so this is O(items × labels × LABELS) per keystroke. `Constants.LABELS` is a `Map<String,String>` (`Constants.kt:34,48`), so one `associate { it.value to it.key }` per pass replaces all scans.
files: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt` (functions `filterNews` line ~95 and `collectLabels` line ~200). Extend `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesViewModelTest.kt`. Do NOT touch `Constants.kt`, `VoicesLabelManager.kt`, `VoicesFragment.kt` or `VoicesAdapter.kt` (open PRs 13415/15519 area).
steps:
1. At the top of each function, build `val labelValueToName = Constants.LABELS.entries.associate({ it.value to it.key })`.
2. Replace both `entries.find { it.value == label }?.key` expressions with `labelValueToName[label]`, keeping the `?: VoicesLabelManager.formatLabelValue(label)` fallback.
3. In `filterNews`, note the existing `labelDisplayToValue` built at line ~103 is a different map (display-name → value); do not merge or remove it.
4. Add/extend tests: filtering by a known label still matches, an unknown label value still falls back to `formatLabelValue`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.voices.VoicesViewModelTest"` passes; full suite green; voices feed label filter and label chips show identical results before/after.
size budget: ~20 changed lines, 2 files.
out of scope: no redesign of the filter pipeline, no debounce changes, no Compose work.

---

### 10. Defer TextToSpeech engine bind until first use in TTSManager (roadmap 7, 10)
context: `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt:28` constructs `TextToSpeech(context)` inside the `@Singleton`'s `init`, binding to the system TTS service on the main thread as soon as Hilt injects it — which happens at `ResourceViewerFragment.kt:106` (off-limits, cite only), i.e., every time any resource viewer screen opens, even for content that is never read aloud. Deferring engine creation to the first `speak()` call removes a service bind from the screen-open path. `TTSManagerTest.kt` only exercises the static `stripMarkdown`, so no test rewrite is needed.
files: `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt` (property `tts` line 19, `init` line 27, `speak` line ~46, `stop` line ~51, and any `shutdown`-style cleanup in the companion/body). Do NOT touch `ResourceViewerFragment.kt` (open PR 15771) or any caller.
steps:
1. Replace the eager `init { tts = TextToSpeech(...) }` with a private `ensureTts()` that creates the engine on first call and installs the existing `UtteranceProgressListener` exactly as today.
2. Call `ensureTts()` from `speak()` only; make `stop()` and cleanup null-safe no-ops when the engine was never created (check `_state` reset still happens).
3. Keep `isInitialized` semantics: `speak()` on a blank string or before init completes still returns without side effects; state flow transitions (`IDLE`/`SPEAKING`) unchanged.
4. Verify no other public API touches `tts` directly (grep the file) and route any that do through `ensureTts()` only if they are part of the speak path — passive getters must not trigger the bind.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.TTSManagerTest"` passes; full suite green; open a text resource and press read-aloud — speech still works; opening the viewer without read-aloud produces no TTS connection in logcat.
size budget: ~30 changed lines, 1 file.
out of scope: no changes to `stripMarkdown`, no caller-side lazy injection, no lifecycle scoping changes.

---

## self-check
- [x] exactly 10 tasks
- [x] no file appears in two tasks (16 distinct main-source files + 8 distinct test files; verified against the deduplicated list)
- [x] every cited path was opened and confirmed to exist (all 16 main files read or grepped with line numbers above; all 8 test files confirmed via directory listing/grep)
- [x] every task has all 7 template sections
- [x] no task under 15 lines
- [x] no task touches a file from the 42 open PRs (off-limits files are cited as evidence only and explicitly marked do-not-touch)

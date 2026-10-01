# Performance quick-win work orders

date: 2026-10-01
base commit: `286e66e0e1daf285c34d8456deb0028016bf858a`
open PRs checked: #17812, #17811, #17776, #17694, #17680, #17435, #17254, #17187, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075
ranking basis: descending user impact divided by blast radius after excluding every path touched by the open PRs above.

### 1. return the first successful configuration probe without waiting for the slower endpoint (roadmap 7)

context: `ConfigurationsRepositoryImpl.getMinApk` launches the primary and alternative URL probes concurrently, but `awaitAll()` at `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt:278-285` makes a successful fast response wait for the slowest probe. That delay sits directly in server configuration, so short-circuiting on success improves perceived setup latency without changing which URLs are attempted; this is platform-bound network orchestration and does not directly advance roadmap 9 or 10.

files: touch `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (`ConfigurationsRepositoryImpl.getMinApk`) and `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt` (the `getMinApk` tests). Leave `ServerUrlMapper`, `ApiInterface`, `NetworkUtils`, and all sync activities alone.

steps:
1. Replace the all-results barrier with structured concurrency that consumes completed probes and returns as soon as any `UrlCheckResult.Success` arrives.
2. Preserve deterministic failure behavior when every probe fails, including the original URL in the fallback result.
3. Cancel and join outstanding probe children after selecting a success so they do not continue network work in the background.
4. Add coroutine tests with one fast success and one suspended or delayed probe, plus an all-fail case.
5. Keep exception logging and the existing `ConfigurationResult` mapping unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ConfigurationsRepositoryImplTest*'` and `./gradlew testDefaultDebugUnitTest` pass; configuring a server proceeds when either reachable endpoint succeeds and does not wait for the other endpoint's timeout.

size budget: about 70 changed lines across 2 files.

out of scope: do not change URL mapping, retry policy, HTTP timeouts, endpoint precedence outside the concurrent probe, or introduce a dependency.

---

### 2. stream chat normalization through the search scan (roadmap 7+8)

context: `ChatSearch.fullConvoSearch` first materializes a `ConvoChat` and a normalized string list for every conversation at `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt:39-45`, even when an early conversation matches. `searchByTitle` similarly builds a complete `TitleChat` list at lines 77-85 before scanning it; removing those transient collections lowers allocation pressure on large chat histories and keeps this pure Kotlin helper aligned with roadmap 9, while roadmap 10 is unaffected.

files: touch `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt` (`ChatSearch.fullConvoSearch`, `ChatSearch.searchByTitle`, and now-redundant private holder types) and `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt` (`ChatSearchTest`). Leave `ChatViewModel`, `ChatHistoryAdapter`, `Utilities.normalizeText`, and repositories alone.

steps:
1. Iterate chats directly and normalize only the title or conversations needed while evaluating that chat.
2. Retain the four existing result-priority buckets and the first-match-per-chat behavior.
3. Remove private wrapper data classes only if they become unused.
4. Add tests proving title-start, title-contains, later-conversation-start, and later-conversation-contains ordering is unchanged.
5. Add a test fixture with null query/response text to preserve current skipping behavior.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ChatSearchTest*'` and `./gradlew testDefaultDebugUnitTest` pass; title, question, and answer searches return the same ordered rows while allocating no precomputed wrapper list.

size budget: about 80 changed lines across 2 files.

out of scope: do not alter normalization semantics, ranking, dispatcher choice, repository APIs, or the chat UI.

---

### 3. snapshot sync timing aggregates once per report (roadmap 5+7+8)

context: `SyncTimeLogger.generateSummary` traverses and locks every API log list for totals at `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt:235-236`, then traverses the same lists again for counts, sorting, and row output at lines 261-279. Database logs repeat the pattern at lines 288-305, adding lock contention and work precisely when a large sync finishes; producing immutable aggregate rows once also isolates pure reporting logic in the direction of roadmap 9, with no roadmap 10 impact.

files: touch `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` (`SyncTimeLogger.generateSummary` and private aggregate representation) plus `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt` and `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerConcurrencyTest.kt`. Leave `SyncManager`, upload classes, log persistence, and UI progress reporting alone.

steps:
1. Under one synchronized block per endpoint or model, calculate duration, count, success count, and item count into immutable local aggregate rows.
2. Derive grand totals, sort order, averages, percentages, and rendered rows from those snapshots instead of rereading mutable lists.
3. Preserve the exact report headings and number formatting.
4. Extend the summary test to assert ordering and totals from multiple API and database groups.
5. Keep the concurrency test green and add coverage that reporting during writes does not throw or emit internally inconsistent per-row counts.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*SyncTimeLoggerTest' --tests '*SyncTimeLoggerConcurrencyTest'` and `./gradlew testDefaultDebugUnitTest` pass; the sync summary text remains user-identical while each mutable log list is locked and traversed only once per report.

size budget: about 120 changed lines across 3 files.

out of scope: do not change timing collection, output destinations, sync scheduling, upload orchestration, or report wording.

---

### 4. deduplicate notification IDs before Room chunking (roadmap 1+7)

context: `NotificationDao` chunks raw IDs in `getByIds`, `getIdsByIds`, `markAsRead`, and `deleteByIds` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt:42-64` and lines 121-124. Duplicate IDs can therefore consume bind slots, cross a chunk boundary, repeat returned rows, or issue redundant updates; normalizing at these platform-free wrapper boundaries advances roadmap 9, while roadmap 10 is unaffected.

files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` (`getByIds`, `getIdsByIds`, `markAsRead`, `deleteByIds`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt` (`NotificationDaoTest`). Leave `NotificationsRepositoryImpl`, `NotificationsViewModel`, entities, schemas, and SQL query bodies alone.

steps:
1. Deduplicate each public wrapper's ID list once, preserving first-seen order, before applying the existing chunk size.
2. Keep empty-input fast paths and transaction annotations unchanged.
3. Ensure update and delete counts continue to describe distinct affected rows.
4. Add DAO tests with duplicate IDs on both sides of a chunk boundary for reads and writes.
5. Confirm unique-input ordering and behavior remain unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*NotificationDaoTest*'` and `./gradlew testDefaultDebugUnitTest` pass; notification selection, mark-read, and deletion still affect every requested notification once without duplicate rows.

size budget: about 45 changed lines across 2 files.

out of scope: do not change notification grouping, sync revision updates, repository logic, indices, or database schema.

---

### 5. update one storage-selection row instead of recounting the full list (roadmap 3+7)

context: `StorageCategoryViewModel.toggleItemChecked` maps every row and recomputes the checked count from scratch on each tap at `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt:61-69`. Large offline libraries therefore do two pieces of per-row work for a one-row state change; retaining the count in hoisted view-model state advances roadmap 10, while the view model still depends on AndroidX and does not yet directly advance roadmap 9.

files: touch `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt` (`StorageCategoryViewModel.toggleItemChecked`) and `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModelTest.kt` (`StorageCategoryViewModelTest`). Leave `StorageCategoryDetailFragment`, `ResourcesRepository`, deletion flows, storage models, and layouts alone.

steps:
1. Locate the requested resource once and return the unchanged state when no row matches.
2. Copy only the matching item with its checked flag inverted while retaining the existing list order.
3. Adjust `checkedCount` by plus or minus one from the prior item state instead of recounting all rows.
4. Keep the update atomic inside the existing `MutableStateFlow.update` block.
5. Add tests for checking, unchecking, an unknown ID, and repeated toggles without count drift.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*StorageCategoryViewModelTest*'` and `./gradlew testDefaultDebugUnitTest` pass; tapping one offline-resource checkbox updates only that row and keeps the selected count and select-all state correct.

size budget: about 45 changed lines across 2 files.

out of scope: do not change loading, deletion, repository queries, screen layout, navigation, or selection identity rules.

---

### 6. deduplicate submission IDs before answer reads and deletes (roadmap 1+7)

context: `AnswerDao.getBySubmissionIds` and `deleteBySubmissionIds` chunk the caller's list without removing duplicates at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt:16-29`. Repeated submission IDs waste query variables and can return the same answers from multiple chunks, inflating hydration work during course and submission processing; this small pure-Kotlin boundary cleanup advances roadmap 9 and does not affect roadmap 10.

files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt` (`getBySubmissionIds`, `deleteBySubmissionIds`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt` (`AnswerDaoTest`). Leave `SubmissionsRepositoryImpl`, `ProgressRepositoryImpl`, `Answer`, `SubmissionDao`, and database schema alone.

steps:
1. Build an encounter-ordered distinct submission-ID list before the existing 900-item chunking.
2. Use that list for both bulk reads and transactional deletes.
3. Preserve empty-input behavior and the sum of actual deleted rows.
4. Add tests for duplicate IDs within a chunk and repeated IDs split around the 900-item boundary.
5. Assert bulk reads contain each stored answer once and deletes report distinct affected rows.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*AnswerDaoTest*'` and `./gradlew testDefaultDebugUnitTest` pass; exam answers hydrate and delete exactly as before for unique IDs and no longer duplicate work for repeated IDs.

size budget: about 35 changed lines across 2 files.

out of scope: do not change answer matching, submission hydration, query schemas, conflict strategy, or blocking APIs.

---

### 7. deduplicate removed-document keys before cleanup chunks (roadmap 1+7)

context: `RemovedLogDao.deleteByTypeUserAndDocsChunked` feeds the raw document-ID list to successive delete statements at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt:18-22`. Duplicate keys consume SQLite bind variables and can produce empty redundant delete calls after an earlier chunk already removed the row; consolidating IDs in this small data-layer wrapper advances roadmap 9, with no roadmap 10 impact.

files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt` (`deleteByTypeUserAndDocsChunked`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt` (`RemovedLogDaoTest`). Leave course/resource repositories, `RemovedLog`, `AppDatabase`, and sync writers alone.

steps:
1. Return immediately for empty input rather than entering the chunk pipeline.
2. Deduplicate document IDs in encounter order before creating 900-ID chunks.
3. Preserve the transaction boundary and nullable user-ID semantics.
4. Add tests covering duplicate IDs, more than 900 requested positions, and isolation by type and user.
5. Verify all matching logs are removed and neighboring users and types remain intact.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*RemovedLogDaoTest*'` and `./gradlew testDefaultDebugUnitTest` pass; stale course/resource cleanup removes the same records with fewer bind values and delete statements.

size budget: about 35 changed lines across 2 files.

out of scope: do not change removal-log creation, repository reconciliation, entity keys, migrations, or cleanup policy.

---

### 8. deduplicate library resource keys before bulk updates and title reads (roadmap 1+7)

context: `MyLibraryDao.markAsNotOfflineByResourceIds` chunks raw IDs at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt:163-166`, while `getResourceTitlesByResourceIds` repeats that pattern at lines 202-204. Duplicates waste bind slots and title rows can be repeated when the same key lands in different chunks; matching this file's already-deduplicated wrappers at lines 61, 72, and 89 advances roadmap 9 and leaves roadmap 10 untouched.

files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` (`markAsNotOfflineByResourceIds`, `getResourceTitlesByResourceIds`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt` (`MyLibraryDaoTest`). Leave `ResourcesRepositoryImpl`, UI resource filters, `MyLibrary`, migrations, and download workers alone.

steps:
1. Deduplicate resource IDs once in each public wrapper before the 900-ID chunks are made.
2. Preserve encounter order so title-result chunk ordering remains stable.
3. Retain current empty-input fast paths and transaction coverage.
4. Add tests for duplicates within a chunk and spanning a chunk boundary for both wrappers.
5. Assert offline flags update once and title projections are not repeated because of repeated request keys.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*MyLibraryDaoTest*'` and `./gradlew testDefaultDebugUnitTest` pass; storage cleanup and resource-title display remain correct with unique and repeated resource IDs.

size budget: about 40 changed lines across 2 files.

out of scope: do not change SQL ordering, offline-resource semantics, repository search, downloads, schema, or indices.

---

### 9. reuse a distinct APK-log ID set during upload acknowledgement (roadmap 1+5+7)

context: `ApkLogDao.markUploadedBatch` maps every update to an ID list, queries that raw list in chunks, and then performs another set conversion at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt:30-36`. Duplicate upload acknowledgements therefore spend extra bind slots and membership work even though the return type is already a set; consolidating this platform-free bookkeeping advances roadmap 9, while roadmap 10 is not involved.

files: touch only `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt` (`ApkLogDao.markUploadedBatch`). Leave `UploadManager`, crash/ANR capture, `ApkLog`, upload DTO construction, and database setup alone.

steps:
1. Derive one encounter-ordered distinct ID collection from the update batch.
2. Use it for chunked existence queries and missing-ID calculation without rebuilding equivalent sets.
3. Keep the full update list passed to Room so revision-update semantics remain unchanged.
4. Preserve the empty-batch fast path and transaction behavior.
5. Rely on the full unit suite to cover compilation and upload acknowledgement behavior; do not add a new test-only abstraction.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; crash and ANR log uploads still mark every supplied update and report each missing local ID once.

size budget: about 10 changed lines in 1 file.

out of scope: do not alter upload batching, revision precedence for duplicate updates, crash-log retention, schema, or add dependencies.

---

### 10. compare multiple-choice answers in linear time (roadmap 7+8)

context: `ExamAnswerUtils.checkMultipleSelectAnswer` lowercases and sorts both answer collections at `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt:77-86`, paying `O(n log n)` time and two sorted-list allocations for an order-insensitive comparison. A frequency comparison preserves duplicate-answer semantics in linear time and keeps the helper Android-free for roadmap 9; roadmap 10 is unaffected because no composable state or resource access is involved.

files: touch `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` (`ExamAnswerUtils.checkMultipleSelectAnswer`) and `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt` (`ExamAnswerUtilsTest`). Leave exam fragments, `ExamQuestion`, answer persistence, locale selection, and single/text answer logic alone.

steps:
1. Keep the existing null and size mismatch fast failures.
2. Build normalized frequency counts for one side and decrement them while scanning the other side, returning early on a missing value.
3. Preserve `Locale.getDefault()` case normalization and duplicate multiplicity.
4. Add tests for reordered answers, case differences, duplicate-count mismatch, and same-size disjoint answers.
5. Confirm single-select and free-text tests remain unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ExamAnswerUtilsTest*'` and `./gradlew testDefaultDebugUnitTest` pass; multiple-choice grading remains order- and case-insensitive while correctly rejecting different duplicate counts.

size budget: about 45 changed lines across 2 files.

out of scope: do not change answer normalization rules, partial-credit behavior, question parsing/cache behavior, UI, or persistence.

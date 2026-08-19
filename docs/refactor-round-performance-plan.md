# myPlanet performance quick-win work orders

date · 2026-08-19  
base commit · `9c54a0341557a7e7ae4bdc313fd1c97c0cc23b32`  
open PRs checked · could not check open PRs (`gh pr list --state open --limit 100 --json number,title,headRefName,baseRefName,url,files` reported `no git remotes found`)

### 1. normalize free-text answers once per correctness check (roadmap 7+8)

context: `ExamAnswerUtils.checkTextAnswer` lowercases `ans` inside the `correctChoices.any` predicate, so the same answer string is allocated again for every candidate at `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt:75-78`. This is a frequently reusable answer-checking path, and its existing test suite already covers input-text correctness at `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt:46-53`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`, specifically `ExamAnswerUtils.checkTextAnswer`, and `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`, specifically `ExamAnswerUtilsTest`. Leave `choicesCache`, `getChoiceTextById`, and `checkMultipleSelectAnswer` alone.

steps:
1. Compute the locale and normalized answer once before scanning `correctChoices`.
2. Reuse the normalized answer while normalizing each candidate choice and checking containment.
3. Preserve the current null-list result, case-insensitive matching, substring behavior, and default-locale semantics.
4. Extend the existing input-text tests with multiple correct-choice candidates so the loop behavior remains covered.
5. Clean any imports made unused by the focused edit.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ExamAnswerUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that free-text answers still match correct choices without regard to case, including when the matching choice is not first.

size budget: approximately 8-15 changed lines across 2 files.

out of scope: Do not replace the existing `LruCache`, change select or multi-select comparison semantics, or introduce locale-policy changes. This task does not directly remove the existing Android dependency and therefore does not claim roadmap 9 progress.

---

### 2. remove the redundant selection membership scan (roadmap 7+8)

context: `SelectionUtils.handleCheck` calls `selectedItems.contains(list[i])` and then `selectedItems.remove(list[i])` at `app/src/main/java/org/ole/planet/myplanet/utils/SelectionUtils.kt:4-9`, causing two linear scans when unchecking a present item. `MutableList.remove` already safely reports failure when the value is absent, and the existing tests cover both present and absent removal at `app/src/test/java/org/ole/planet/myplanet/utils/SelectionUtilsTest.kt:23-45`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/SelectionUtils.kt`, specifically `SelectionUtils.handleCheck`, and `app/src/test/java/org/ole/planet/myplanet/utils/SelectionUtilsTest.kt`, specifically removal tests in `SelectionUtilsTest`. There are no neighboring utility functions to alter.

steps:
1. Read `list[i]` once into a local value so both branches avoid repeated indexed access.
2. On the unchecked branch, call `selectedItems.remove` directly instead of checking `contains` first.
3. Preserve the checked branch’s current append behavior, including its handling of duplicates and null elements.
4. Strengthen the existing tests to assert that only the first equal occurrence is removed when duplicates are present.
5. Retain the absent-item and null-item coverage.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.SelectionUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that checking still appends the selected value and unchecking still removes one matching value without changing an absent selection.

size budget: approximately 8-15 changed lines across 2 files.

out of scope: Do not change the public signature to a `MutableSet`, deduplicate existing selections, or alter index error behavior. Because this utility is already platform-free Kotlin, the edit preserves roadmap 9 compatibility without scheduling a separate multiplatform migration.

---

### 3. index exam questions before restoring saved answers (roadmap 6+7+8)

context: `ExamTakingFragment.populateCacheFromSavedAnswers` performs `questions?.find { it.id == questionId }` for every saved answer at `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:779-784`. Restoring a long survey is therefore quadratic in answer and question counts, directly affecting resume-screen latency; the fragment already has a test fixture in `app/src/test/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragmentTest.kt:7-13`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt`, specifically `ExamTakingFragment.populateCacheFromSavedAnswers`, and `app/src/test/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragmentTest.kt`, specifically `ExamTakingFragmentTest`. Leave `initializeExamData`, answer parsing branches, and submission persistence alone.

steps:
1. Build one question lookup keyed by non-null question ID before iterating saved answers.
2. Replace each per-answer linear `find` with lookup access.
3. Preserve current handling of missing question IDs, unknown questions, malformed choice JSON, and every question-type branch.
4. Extend the fragment test fixture to cover restoring answers whose question order differs from the saved-answer order, using the existing test approach.
5. Confirm the lookup is local to restoration and does not introduce retained fragment state.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.exam.ExamTakingFragmentTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that resuming a survey restores select, multi-select, and text answers exactly as before, including when saved answers are out of question order.

size budget: approximately 25-50 changed lines across 2 files.

out of scope: Do not migrate the exam screen to Compose, change submission schemas, or rewrite answer parsing. The local index is preparatory performance work for roadmap 6, not a direct roadmap 10 migration.

---

### 4. index successful uploads before reconciling local failures (roadmap 5+7)

context: Both `UploadCoordinator.updateDatabaseBatch` and `updateDatabaseBatchRoom` map failed database results back to uploaded items with `succeeded.find`, at `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt:255-261` and `:439-442`. When a batch has multiple local-update failures, reconciliation becomes quadratic even though `localId` is already the stable key used throughout the upload workflow.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt`, specifically `UploadCoordinator.updateDatabaseBatch` and `UploadCoordinator.updateDatabaseBatchRoom`. Leave `uploadBatch`, `uploadBatchRoom`, `queueRetryableFailures`, and all upload configuration types alone.

steps:
1. Build a `localId`-keyed lookup from `succeeded` once in each reconciliation function.
2. Resolve each returned failed result through the lookup instead of scanning the complete success list.
3. Preserve the order and duplicate behavior of `failedResults.mapNotNull`.
4. Keep unknown failed-result IDs ignored exactly as they are today.
5. Avoid extracting a new helper unless doing so reduces the total changed lines without widening visibility.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.UploadManagerTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that full success, partial local-update failure, and unknown failure IDs still produce the same `UploadResult` classification and counts.

size budget: approximately 6-14 changed lines in 1 file.

out of scope: Do not alter batching, retry eligibility, remote upload ordering, Room contracts, or payload preparation. This optimization consolidates the two existing reconciliation paths but does not move Android-bound orchestration into the platform-free core.

---

### 5. use the existing exam-key set directly during submission filtering (roadmap 3+7+10)

context: `SubmissionViewModel.filteredSubmissionsRaw` obtains matching exam IDs from `examMap.filter(...).keys` and then calls `examIds.contains` for every submission at `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt:52-66`. Materializing a filtered map and retaining its key view does more work than producing a key set once, while this state transformation appropriately belongs in the viewmodel rather than the UI.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt`, specifically the query branch of `filteredSubmissionsRaw`, and `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModelTest.kt`, specifically `testQueryFilteringAndGrouping`. Leave the repository calls, grouping logic, and `SubmissionUiModel` mapping alone.

steps:
1. Produce matching exam IDs directly from `examMap` entries into a set instead of creating a filtered map.
2. Use set membership when filtering submissions by `parentId`.
3. Preserve case-insensitive title matching and the empty-query fast path.
4. Extend the existing query-filtering test with several nonmatching exams and submissions plus one matching parent ID.
5. Confirm the resulting ordering, grouping, and submission counts remain unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.submissions.SubmissionViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that typing a submission search still shows only groups whose exam title matches, with the latest submission and count unchanged.

size budget: approximately 10-20 changed lines across 2 files.

out of scope: Do not change flow sharing policies, repository APIs, sorting, or submitter-name fallback behavior. Keeping filtering as platform-neutral viewmodel state transformation directly supports portable Compose screens under roadmap 10.

---

### 6. precompute exam positions while aggregating course mistakes (roadmap 1+7+9)

context: `ProgressRepositoryImpl.submissionMap` calls both `examIds.contains(question.examId)` and `examIds.indexOf(question.examId)` inside the nested submission-answer loop at `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:141-164`. A single exam-ID-to-index map can replace both scans and makes the data-layer aggregation linear while preserving the serialized index keys.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`, specifically `ProgressRepositoryImpl.submissionMap`, and `app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt`, specifically the `fetchCourseData` submission-grouping coverage. Leave DAO queries, `calculateCurrentProgress`, and the exported JSON field names alone.

steps:
1. Build an exam ID to first-position map once at the start of `submissionMap`.
2. Resolve each question’s exam ID with one map lookup inside the answer loop.
3. Add mistakes only when the lookup succeeds, using the same zero-based index string currently emitted.
4. Preserve first-index behavior if `examIds` unexpectedly contains duplicates.
5. Extend the existing fetch-course-data test with multiple exams and answers, asserting both per-index mistake totals and the overall total.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ProgressRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that course progress JSON retains identical `stepMistake` keys and `mistakes` totals for multiple exams.

size budget: approximately 15-30 changed lines across 2 files.

out of scope: Do not change DAO schemas, JSON contracts, course matching, or the cumulative-total behavior. The new indexing logic is platform-free collection code and is directly reusable when this repository aggregation moves toward roadmap 9.

---

### 7. index hydrated submission answers by question ID (roadmap 1+7+9)

context: `SubmissionsRepositoryImpl.getSubmissionDetail` scans `submission.answers` with `find` once for every exam question at `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:250-270`. Large surveys therefore pay quadratic lookup cost before choice formatting even begins, although `questionId` is the natural association key.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, specifically `SubmissionsRepositoryImpl.getSubmissionDetail`, and `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImplTest.kt`, specifically existing `getSubmissionDetail` coverage. Leave `hydrateSubmission`, `getExamById`, choice JSON parsing, and DAO interfaces alone.

steps:
1. Build an answer lookup keyed by non-null `questionId` once after hydration and before mapping questions.
2. Preserve current first-match behavior if malformed data contains duplicate answers for one question.
3. Replace the per-question `find` call with lookup access.
4. Keep correctness evaluation and formatted-answer fallback behavior byte-for-byte equivalent.
5. Extend the existing detail tests with multiple questions, an unanswered question, and answers supplied in a different order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.SubmissionsRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that submission details still show each answer beside the correct question and leave unanswered questions blank.

size budget: approximately 15-35 changed lines across 2 files.

out of scope: Do not change answer entities, choice parsing, correctness rules, or submission lookup fallback. The keyed association is platform-neutral repository logic and advances the platform-free data core described by roadmap 9.

---

### 8. update a single notification in one list traversal (roadmap 3+7+10)

context: `NotificationsViewModel.markAsRead` first searches `currentList.find` and then either filters the list or calls `currentList.markAsRead`, producing a second traversal at `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:159-179`. This action runs from notification interaction and can determine the unread transition during the same transformation without a preliminary scan.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, specifically `NotificationsViewModel.markAsRead`, and `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`, specifically mark-as-read state tests. Leave bulk selection methods, grouping, type resolution, and repository behavior alone.

steps:
1. Fold target detection, prior unread-state capture, and list transformation into one traversal.
2. Preserve removal from the `"unread"` filter and copy-as-read behavior for every other filter.
3. Ensure a missing or already-read notification does not decrement `_unreadCount`.
4. Keep repository confirmation through `markedIds` as the gate for local mutation.
5. Add tests for unread-filter removal, all-filter replacement, already-read input, and a missing target ID.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that opening one unread notification updates its row or removes it under the unread filter and decrements the badge exactly once.

size budget: approximately 25-50 changed lines across 2 files.

out of scope: Do not change bulk mark-all behavior, selection state, notification grouping, or repository contracts. Consolidating this state mutation in the viewmodel keeps UI state hoisted and directly supports roadmap 10 portability.

---

### 9. reuse selection indexes while rebuilding collection tag rows (roadmap 6+7)

context: `CollectionsFragment.buildTagDataList` scans `selectedItemsList` separately for every parent and every expanded child at `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt:103-117`. Expanding a large collection tree therefore multiplies selection-list scans during each rebuild, even though only tag IDs are needed for membership checks.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`, specifically `CollectionsFragment.buildTagDataList`. Leave `filterTags`, `setListAdapter`, `onCheckboxTagSelected`, `ResourcesTagsAdapter`, and repository loading alone.

steps:
1. Derive a set of selected non-null tag IDs once at the beginning of `buildTagDataList`.
2. Use set membership for both parent and child `isSelected` values.
3. Build one lookup of current parent rows by tag ID before the parent loop instead of calling `currentTagDataList.find` per parent.
4. Preserve expansion state, row order, duplicate-ID behavior, and the collection-switch flag.
5. Keep all temporary indexes local so no stale selection or expansion cache survives a rebuild.

acceptance: `./gradlew testDefaultDebugUnitTest` passes. Verify manually that opening Collections, selecting tags, filtering, expanding parents, and toggling multi-select keeps the same checked and expanded rows while rebuilds remain responsive.

size budget: approximately 12-25 changed lines in 1 file.

out of scope: Do not migrate the dialog to Compose, replace the adapter, change tag equality, or alter repository queries. This is an incremental UI performance prerequisite for roadmap 6 rather than a direct roadmap 10 conversion.

---

### 10. snapshot watched tables once per realtime-sync subscription (roadmap 5+7)

context: `RealtimeSyncHelper.setupRealtimeSync` invokes `mixin.getWatchedTables()` and then performs list membership for every emitted update at `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt:27-37`. Watched tables are configuration for a helper instance, so repeatedly reconstructing or scanning the list adds avoidable work on the realtime event stream.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt`, specifically `RealtimeSyncHelper.setupRealtimeSync`. Leave the `RealtimeSyncMixin` interface, debounce interval, distinctness criteria, `onDataUpdated`, and `refreshRecyclerView` alone.

steps:
1. Call `mixin.getWatchedTables()` once when `setupRealtimeSync` creates the collection pipeline.
2. Convert the result to a set for constant-time table membership checks.
3. Use the snapshot in the flow filter without changing downstream operators or callback order.
4. Preserve empty-list behavior and exact string matching.
5. Keep the snapshot scoped to each setup call so separate helpers cannot share configuration accidentally.

acceptance: `./gradlew testDefaultDebugUnitTest` passes. Verify that updates for watched tables still trigger `onDataUpdated` and adapter refresh after debounce, while updates for all other tables remain ignored.

size budget: approximately 3-8 changed lines in 1 file.

out of scope: Do not change realtime event payloads, lifecycle collection, debounce policy, adapter refresh contracts, or the mixin API. The optimization consolidates sync filtering under roadmap 5 but does not itself extract Android-bound lifecycle code for roadmap 9.

# Performance quick wins work orders

date · 2026-09-17  
base commit · `fcb4c53b2d1c4134789f1c07bd785d97d7f4215b`  
open PRs checked · could not check open PRs (the checkout has no remote and GitHub CLI is unauthenticated)

Candidates are ranked by expected user impact divided by blast radius. Because the available platform could not enumerate open pull requests, each work order is deliberately confined to a small, distinct implementation/test pair; executors must re-check open PR files immediately before starting and skip any colliding work order.

### 1. use constant-time course-id membership during adapter removal (roadmap 7)

context: `CoursesAdapter.removeCourses` filters every item in `currentList` while testing `courseId` against the caller's `List` at `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt:120-125`. That makes bulk removal quadratic when both the visible catalog and selected removal set are large, directly delaying the course-list update.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt` (`CoursesAdapter.removeCourses`) and `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt` (add removal coverage). Leave `CoursesFragment`, `CoursesViewModel`, and repository classes alone.

steps:
1. In `removeCourses`, return through the existing `submitList` path without building a lookup set when `courseIds` is empty.
2. Convert non-empty `courseIds` to a set once before filtering `currentList`.
3. Preserve the existing callback timing by continuing to invoke `onComplete` only from the `submitList` commit callback.
4. Extend `CoursesAdapterTest` with retained/removed IDs, duplicate removal IDs, and callback assertions.
5. Remove any imports made unused by the edit and keep the adapter's existing stable-ID behavior unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*CoursesAdapterTest'` and `./gradlew testDefaultDebugUnitTest` pass; removing many selected courses still leaves the same ordered survivors and runs the completion action once.

size budget: about 25 changed lines across 2 files.

out of scope: Do not change selection semantics, DiffUtil payloads, fragments, or repository deletion behavior. This is not a Compose migration and does not directly advance roadmap 9 or 10.

---

### 2. eliminate the temporary candidate list when choosing the next download (roadmap 5+7)

context: `DownloadService.getNextUrl` calls `filter` and then `minOrNull` at `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt:629-639`. Every queue selection therefore allocates a full intermediate list even though only the smallest eligible URL is needed, which is repeated throughout long offline download sessions.

files: Touch `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` (`DownloadService.Companion.getNextUrl`) and `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceUrlSelectionTest.kt` (`getNextUrl` cases). Leave download I/O, notification code, `DownloadWorker`, and preference key formats alone.

steps:
1. Replace the eager filtered-list pipeline with a lazy sequence or a single-pass minimum selection.
2. Retain both eligibility predicates: ignore blank URLs and URLs already in `processedUrls`.
3. Keep lexicographic minimum ordering and the incoming `isPriority` value exactly as today.
4. Add or tighten tests for mixed processed/blank values, an empty eligible set, and deterministic minimum selection.
5. Confirm the method still reads the selected preference set only once.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*DownloadServiceUrlSelectionTest'` and `./gradlew testDefaultDebugUnitTest` pass; queued downloads start in the same deterministic order with no blank or already-processed URL retried.

size budget: about 20 changed lines across 2 files.

out of scope: Do not alter queue persistence, retry policy, foreground-service behavior, or actual file transfer code. The narrower workflow improves roadmap 5 but remains Android-specific, so it does not directly advance roadmap 9.

---

### 3. remove redundant coroutine creation from course loading (roadmap 3+7)

context: `CoursesViewModel.loadCourses` wraps one progress-repository call in `coroutineScope { async { ... }.await() }` at `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt:110-145`. With no sibling work to run concurrently, this creates an unnecessary child coroutine and deferred object on every course load while obscuring a straightforward use-case boundary.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt` (`CoursesViewModel.loadCourses`) and `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesViewModelTest.kt` (course-loading assertions). Leave `CoursesRepository`, `ProgressRepository`, sorting, filtering, and UI classes alone.

steps:
1. Call `progressRepository.getCourseProgress` directly inside the existing IO context.
2. Remove only the now-unused `async` and `coroutineScope` imports.
3. Preserve exception handling, repository call order, course IDs, and the single final state publication.
4. Add or strengthen a test proving one progress lookup occurs and its map is included in `coursesState`.
5. Keep all state hoisted in the ViewModel without introducing Android view or resource access.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*CoursesViewModelTest'` and `./gradlew testDefaultDebugUnitTest` pass; opening Courses shows identical progress values and ordering without an extra loading transition.

size budget: about 18 changed lines across 2 files.

out of scope: Do not parallelize repository calls, redesign `CoursesUiState`, or change dispatcher injection. Keeping UI state in the ViewModel also supports roadmap 10 portability.

---

### 4. make resource-filter signatures order-independent without sorting (roadmap 6+7)

context: `ResourcesFilterCriteria.toSignature` maps tag IDs and sorts them at `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:90-99` each time filter inputs are checked. Tag order is irrelevant to matching, so the `O(t log t)` sort and list allocation add avoidable work to an interactive filter path.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt` (`ResourcesListFilter.Signature` and `toSignature`) and `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt` (signature/cache behavior). Leave `ResourcesFragment`, `ResourcesAdapter`, `ResourcesSearchUtils`, and model classes alone.

steps:
1. Represent `Signature.searchTagIds` as a set rather than a sorted list.
2. Build that set directly from `searchTags` without an intermediate mapped list or sort.
3. Preserve defensive copies for every mutable set entering the signature.
4. Add a regression test showing that reordering identical tags does not trigger filtering again.
5. Add coverage showing a genuinely different tag ID still invalidates the cached signature.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ResourcesListFilterTest'` and `./gradlew testDefaultDebugUnitTest` pass; changing tag order alone leaves the displayed resource results stable, while adding/removing a tag refreshes them.

size budget: about 25 changed lines across 2 files.

out of scope: Do not change matching rules, search normalization, download-state filters, or fragment wiring. The pure filtering/state seam supports later roadmap 10 state hoisting, but this task does not migrate UI.

---

### 5. avoid case-folding already-normalized chat text during every match (roadmap 7+9)

context: `Utilities.normalizeText` already lowercases its input, yet both chat search loops request `ignoreCase = true` for every `startsWith` and `contains` comparison at `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt:47-70` and `:88-103`. Those redundant case-insensitive comparisons add repeated character folding across every conversation in large chat histories.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt` (`fullConvoSearch` and `searchByTitle`) and `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt` (case/diacritic behavior). Leave `Utilities.normalizeText`, repositories, fragments, and chat persistence alone.

steps:
1. Keep normalizing the full query, each query part, titles, questions, and responses exactly once as now.
2. Remove case-insensitive comparison mode only where both operands are already normalized.
3. Preserve the four result-priority buckets and early exit after a chat's first matching conversation.
4. Add explicit mixed-case and accented-query regression cases for title and conversation modes.
5. Verify no Android APIs or resources are introduced into the search utility.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ChatSearchTest'` and `./gradlew testDefaultDebugUnitTest` pass; chat search returns the same case-insensitive, accent-insensitive results in the same priority order.

size budget: about 30 changed lines across 2 files.

out of scope: Do not change tokenization, ranking, dispatcher selection, or `Utilities.normalizeText`. Keeping the search algorithm platform-neutral directly advances roadmap 9.

---

### 6. compute sync-summary aggregates once per endpoint and model (roadmap 5+7)

context: `SyncTimeLogger.generateSummary` sums each endpoint's durations once for sorting and again while rendering at `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt:257-294`; database groups repeat the same pattern and also rescan item counts. Large syncs therefore traverse accumulated diagnostic logs multiple times just to build a report.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` (`SyncTimeLogger.generateSummary`) and `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt` (`testGenerateSummaryMultipleKeysAndLogs` area). Leave log collection, upload behavior, `SyncManager`, and formatting helpers alone.

steps:
1. Introduce small local aggregate values for each API endpoint containing total duration, count, and returned items.
2. Sort and render endpoint rows from those aggregates rather than re-summing raw logs.
3. Do the equivalent for database-model duration, operation count, and item count.
4. Preserve the exact summary text, ordering, averages, percentages, and empty-section behavior.
5. Strengthen the multi-key summary test to assert endpoint/model ordering and representative rendered totals.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*SyncTimeLoggerTest'` and `./gradlew testDefaultDebugUnitTest` pass; completed sync diagnostics display byte-for-byte-equivalent totals and descending-duration rows.

size budget: about 70 changed lines across 2 files.

out of scope: Do not alter what gets logged, persistence, crash-log upload, clocks, or public APIs. Isolating aggregation from Android-facing delivery moves sync logic toward roadmap 9, but full extraction is not part of this task.

---

### 7. resolve the lazy teams repository once per upload result batch (roadmap 4+5+7)

context: `UploadConfigs` calls `teamsSyncRepository.get()` from inside each result predicate for `TeamTask` and `TeamActivities` at `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:86-113`. Although Dagger's lazy lookup is cached, repeating it for every uploaded row adds avoidable indirection in two batch hot paths and hides the batch-scoped dependency.

files: Touch `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` (`TeamTask.markUploaded` and `TeamActivities.markUploaded`) and `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt` (team upload marking cases). Leave every other upload config, repository implementation, serializers, and `UploadCoordinator` alone.

steps:
1. Resolve `teamsSyncRepository.get()` once at the start of each of the two `markUploaded` lambdas.
2. Reuse that local repository for every result while preserving sequential suspend calls and failed-result filtering.
3. Keep the lazy injection itself so construction does not reintroduce a dependency cycle.
4. Extend tests with multi-result batches and verify each row is marked exactly once with unchanged failure output.
5. Verify no eager repository resolution occurs when an upload config is constructed but not executed.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*UploadConfigsTest'` and `./gradlew testDefaultDebugUnitTest` pass; task and team-activity uploads mark the same rows and retry the same failures.

size budget: about 30 changed lines across 2 files.

out of scope: Do not rename public config properties, consolidate all mark callbacks, or modify repository bindings. This advances DI cleanup and upload consolidation, but Android context removal for roadmap 9 remains separate.

---

### 8. drop the redundant distinct pass when ordering notification groups (roadmap 3+7)

context: `NotificationsViewModel.buildNotificationGroups` concatenates known grouped types with grouped keys absent from `TYPE_ORDER`, then calls `distinct` at `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:201-217`. The two inputs are already disjoint and each contains unique keys, so the extra pass and backing set run on every notification emission without changing output.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (`buildNotificationGroups`) and `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` (group ordering coverage). Leave `NotificationsRepository`, fragments, adapters, and read-state persistence alone.

steps:
1. Remove the redundant deduplication from construction of `orderedTypes`.
2. Retain `TYPE_ORDER` precedence and the existing iteration order for unknown grouped types.
3. Preserve lowercasing, fallback to `notification`, unread counts, and item order within groups.
4. Add or tighten a mixed known/unknown type test asserting exact group order and no duplicates.
5. Keep grouping as ViewModel-owned state with no resource or Android-view access added.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*NotificationsViewModelTest'` and `./gradlew testDefaultDebugUnitTest` pass; the Notifications screen shows the same sections, order, unread counts, and contents.

size budget: about 20 changed lines across 2 files.

out of scope: Do not change notification classification, expansion state, or mark-as-read behavior. Keeping presentation state outside composables continues to support roadmap 10.

---

### 9. reject mismatched multi-select answers before sorting (roadmap 7+9)

context: `ExamAnswerUtils.checkMultipleSelectAnswer` lowercases and sorts both answer collections before comparing them at `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt:77-85`. When the learner selected too few or too many answers, equality is impossible, so both allocations and sorts are wasted on a common incorrect-answer path.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` (`checkMultipleSelectAnswer`) and `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt` (`testCheckCorrectAnswer_SelectMultiple`). Leave choice caching, single-select/text scoring, `ExamQuestion`, and exam UI alone.

steps:
1. After the existing null guard, return false immediately when selected and correct collection sizes differ.
2. Preserve case-insensitive, order-insensitive comparison for equal-sized collections.
3. Preserve duplicate-answer semantics by retaining the sorted-list comparison for the remaining path.
4. Expand tests for subset, superset, reversed order, mixed case, and duplicate values.
5. Do not add Android APIs or locale-independent behavior changes.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ExamAnswerUtilsTest'` and `./gradlew testDefaultDebugUnitTest` pass; exam grading produces the same correct/incorrect result for all answer orders and casing, with mismatched counts rejected immediately.

size budget: about 20 changed lines across 2 files.

out of scope: Do not redesign answer storage, change locale rules, or alter the bounded choice cache. Keeping grading logic as a pure Kotlin utility directly advances roadmap 9.

---

### 10. strip null team JSON fields without allocating key snapshots (roadmap 1+7+9)

context: Both branches of `MyTeam.serialize` first filter `JsonObject.keySet()` into a temporary `keysToRemove` list and then traverse it again at `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt:156-224`. Serialization runs throughout sync/upload, so removing null entries in one iterator pass avoids repeated list allocation while keeping the wire shape unchanged.

files: Touch `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt` (`MyTeam.Companion.serialize`) and `app/src/test/java/org/ole/planet/myplanet/model/MyTeamTest.kt` (`testSerializeStripsNulls` plus resource-link coverage). Leave Room annotations, `populate`, overloaded course serialization, repositories, and upload classes alone.

steps:
1. Replace each filter-then-remove sequence with safe in-place iteration over the JSON entries or keys.
2. Use iterator removal rather than mutating the object separately during iteration.
3. Apply the same helper or compact pattern to both the `resourceLink` early-return branch and the general branch.
4. Extend tests to cover null stripping and non-null retention in both serialization branches.
5. Confirm `_deleted` serialization and course-enriched serialization remain untouched.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*MyTeamTest'` and `./gradlew testDefaultDebugUnitTest` pass; team sync/upload JSON contains the same non-null fields and omits every null field without concurrent-modification errors.

size budget: about 35 changed lines across 2 files.

out of scope: Do not change server field names, merge rules, entity schema, or Gson configuration. Reducing allocation in platform-free model serialization directly advances roadmap 9.

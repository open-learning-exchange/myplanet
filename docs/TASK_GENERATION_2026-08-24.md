# myPlanet refactor round: independent work orders

date: 2026-08-24

base commit: `62908f1346b55dece0de8812cc698654815646de`

open PRs checked: #16094, #16088, #16087, #15982, #15981, #15979, #15978, #15962, #15951, #15943, #15940, #15936, #15920, #15919, #15918, #15916, #15906, #15898, #15889, #15887, #15882, #15880, #15879, #15878, #15868, #15866, #15858, #15854, #15841, #15833, #15825, #15824, #15820, #15808, #15699, #15698, #15559, #15519, #15412, #15267, #15266, #15226, #15198, #15158, #15108, #14960, #14893, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

workflow logs reviewed: [latest master tests—success](https://github.com/open-learning-exchange/myplanet/actions/runs/32723273532) · [latest master release—success](https://github.com/open-learning-exchange/myplanet/actions/runs/32723273428) · [latest build—success](https://github.com/open-learning-exchange/myplanet/actions/runs/32728618185)

### 1. keep malformed JUnit timings from breaking CI diagnostics (roadmap 8)
context: `.github/scripts/test_timing_summary.py:48-53` converts every suite and test-case `time` directly with `float(...)`. A malformed or non-finite duration can crash the diagnostics step even when Gradle produced useful results; the latest master test run relies on this script for hotspot visibility. This is a workflow quick win for roadmap 8 and does not directly move roadmap 9 or 10.

files: `.github/scripts/test_timing_summary.py` — `main`; leave the workflow invocation, sharding, and Gradle commands alone.

steps:
1. Add a small duration parser returning a finite, non-negative number or `0.0`.
2. Use it for suite and individual test-case durations.
3. Warn on stderr with the affected result filename while continuing.
4. Preserve all current Markdown, threshold, and exit-code behavior.
5. Exercise valid and malformed XML from a temporary directory.

acceptance: `python3 -m py_compile .github/scripts/test_timing_summary.py` and `./gradlew testDefaultDebugUnitTest` pass; mixed input exits zero without `nan`, `inf`, or a traceback.

size budget: approximately 20-35 changed lines in 1 file.

out of scope: do not alter `.github/workflows/test.yml`, sharding, thresholds, or dependencies.

---

### 2. remove identity copies from submission PDF question loading (roadmap 1+7)
context: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:81` and `:179` call `questionDao.getByExamId(it).map { question -> question }`. Both allocate a second list containing the same objects before rendering. This cleans the export data path but does not remove its Android dependencies for roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` — `generateSubmissionPdf` and `generateMultipleSubmissionsPdf`; leave DAOs, pagination, drawing, and file output alone.

steps:
1. Replace both identity maps with the DAO-returned list.
2. Retain nullable exam-ID handling and the empty-list fallback.
3. Do not mutate, reorder, or cache questions.
4. Remove any import made unused.
5. Run the default unit-test task.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; single and multiple exports retain question order, fallbacks, and filenames.

size budget: approximately 2-6 changed lines in 1 file.

out of scope: do not change PDF layout, file I/O, use cases, DAOs, or repository interfaces.

---

### 3. share upload-result reconciliation between Realm and Room batches (roadmap 5+7)
context: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt:57-75` and `:328-339` duplicate local-update error creation, failed-ID set construction, and success filtering. This allocates multiple collections and risks drift. A private platform-neutral helper also prepares this logic for roadmap 9 without relocating the Android coordinator.

files: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` — `upload`, `uploadRoom`, and one new private helper; leave batch HTTP, retries, and serialization alone.

steps:
1. Add a private reconciliation result and helper.
2. Build local-update errors and retained successes in one pass.
3. Call it from both upload entry points.
4. Preserve ordering, IDs, exception text, retryability, and result branching.
5. Remove only the duplication superseded by the helper.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; success, failure, and mixed batches retain current results and ordering.

size budget: approximately 45-75 changed lines in 1 file.

out of scope: do not merge network loops, change retry policy, response matching, or config types.

---

### 4. classify relevant notification payloads without grouping every type (roadmap 3+7)
context: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:62-64` lowercases and groups every payload only to obtain task and join-request lists. This allocates data for unrelated types. The state stays hoisted toward roadmap 10, but this does not change the roadmap 9 Android boundary.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` — `loadNotifications`; `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` — loading tests; leave repositories and grouped UI construction alone.

steps:
1. Populate two relevant lists during one payload traversal.
2. Compare types case-insensitively without retaining lowercase copies.
3. Preserve order within both lists.
4. Test mixed-case relevant types and one unrelated type.
5. Assert batch lookup arguments and final notification order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass; refresh resolves names and details unchanged.

size budget: approximately 30-55 changed lines across 2 files.

out of scope: do not alter labels, expansion, repository queries, selection, or fragment UI.

---

### 5. iterate course mistake entries without copying map keys (roadmap 7)
context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt:40-50` copies map keys during every bind and `:84-91` then performs a second lookup per step. This is avoidable RecyclerView work. It does not migrate the screen or directly advance roadmap 9/10.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt` — `showStepMistakes`; `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapterTest.kt` — row-binding tests; leave the activity, layout, and diff callback alone.

steps:
1. Use `stepMistake.size` for child count.
2. Iterate entries with an index.
3. Populate recycled views in map iteration order.
4. Test multiple entries and displayed values.
5. Rebind with fewer and zero entries to catch stale views.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.courses.CoursesProgressAdapterTest` and `./gradlew testDefaultDebugUnitTest` pass; labels and totals remain correct after recycling.

size budget: approximately 20-40 changed lines across 2 files.

out of scope: do not change ordering, layouts, Compose migration, or the row model.

---

### 6. build hint-spinner items with one allocation (roadmap 7)
context: `app/src/main/java/org/ole/planet/myplanet/utils/ViewExtensions.kt:33-35` creates `entries.toList()` and concatenates it with `listOf(hint)`, producing intermediates whenever a spinner initializes. This is contained UI work and does not make the Android view portable for roadmap 9/10.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ViewExtensions.kt` — `setupHintSpinner`; `app/src/test/java/org/ole/planet/myplanet/utils/ViewExtensionsTest.kt` — spinner coverage; leave `textChanges` and callers alone.

steps:
1. Construct items with one pre-sized list builder.
2. Keep the hint first and preserve entry order and duplicates.
3. Preserve disabled-hint behavior, colors, and layouts.
4. Test item count, ordering, and disabled position.
5. Test an empty entries array.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ViewExtensionsTest` and `./gradlew testDefaultDebugUnitTest` pass; spinners retain all visible behavior.

size budget: approximately 15-30 changed lines across 2 files.

out of scope: do not change styling, callbacks, parameters, or replace views with Compose.

---

### 7. build collection child maps in a single pass (roadmap 3+7)
context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsViewModel.kt:47-51` filters entries into an intermediate list before associating them. Each refresh traverses relevant entries twice. Keeping this transformation in the ViewModel supports roadmap 3 and state hoisting for roadmap 10, though not the roadmap 9 core boundary.

files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsViewModel.kt` — `loadTags`; `app/src/test/java/org/ole/planet/myplanet/ui/resources/CollectionsViewModelTest.kt` — load-state tests; leave repositories, fragments, and state shapes alone.

steps:
1. Build the child map in one pass.
2. Exclude empty child lists exactly as today.
3. Preserve parent order and missing-ID fallback.
4. Test empty and nonempty children.
5. Retain loading, empty, caching, and error behavior.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.resources.CollectionsViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass; Collections renders the same parents and children.

size budget: approximately 20-40 changed lines across 2 files.

out of scope: do not change queries, state types, caching, dispatchers, UI, or identity rules.

---

### 8. normalize resource-search query tokens in one pass (roadmap 7)
context: `app/src/main/java/org/ole/planet/myplanet/utils/ResourceSearchUtils.kt:10-12` splits, filters, and maps query parts through separate lists. Repeated search filtering pays for both intermediates. The utility has no Android imports, so preserving it as a platform-free primitive supports roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ResourceSearchUtils.kt` — `searchList`; `app/src/test/java/org/ole/planet/myplanet/utils/ResourceSearchUtilsTest.kt` — search tests; leave normalization, model selection, and ViewModels alone.

steps:
1. Produce normalized nonempty tokens in one traversal.
2. Preserve separate full-query normalization.
3. Keep input order within both ranking buckets.
4. Test repeated/edge spaces and separated multi-token matches.
5. Assert empty query returns the original list instance.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ResourceSearchUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass; search ranking and normalization remain unchanged.

size budget: approximately 25-45 changed lines across 2 files.

out of scope: do not add fuzzy search, change ranking, renormalize titles, or alter queries/models.

---

### 9. replace Android LruCache in exam-answer utilities with a bounded Kotlin cache (roadmap 7)
context: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt:3`, `:13`, and `:24-40` use `android.util.LruCache` only for a 100-entry choice map. Removing that sole Android import directly moves this utility toward roadmap 9 while retaining roadmap 7 cache behavior; it does not concern roadmap 10.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` — `choicesCache` and `getChoiceTextById`; `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt` — lookup and correctness tests; leave models, JSON utilities, and callers alone.

steps:
1. Use a private access-order `LinkedHashMap` capped at 100.
2. Make access and insertion concurrency-safe.
3. Preserve keys, parsing, fallback, and LRU eviction.
4. Test caching, changed JSON, missing IDs, and eviction.
5. Check that the production file has no `android.` import.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ExamAnswerUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass; exam labels and correctness semantics remain unchanged.

size budget: approximately 55-90 changed lines across 2 files.

out of scope: do not change answer semantics, expose the cache, add dependencies, or move source sets.

---

### 10. stop scanning unrelated files when enforcing the crash-log cap (roadmap 7+8)
context: `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt:25` counts every directory entry, while `:35-49` loads only valid timestamped `.log` files. Unrelated files can consume the 20-report allowance and the full directory is scanned unnecessarily. This improves crash reliability but retains Android `Context`, so it does not advance roadmap 9/10.

files: `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt` — `save` and one private validity helper; `app/src/test/java/org/ole/planet/myplanet/utils/CrashLogStoreTest.kt` — cap and malformed-file tests; leave consumers and time providers alone.

steps:
1. Extract existing valid-log filename checks into one predicate.
2. Reuse it while loading logs.
3. Count only valid logs and stop at the cap.
4. Test unrelated and malformed files at the cap.
5. Preserve exception handling, format, content, and the 20-log limit.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.CrashLogStoreTest` and `./gradlew testDefaultDebugUnitTest` pass; malformed files no longer suppress valid reports.

size budget: approximately 35-60 changed lines across 2 files.

out of scope: do not change storage location, cap, format, upload workflow, or introduce async I/O.

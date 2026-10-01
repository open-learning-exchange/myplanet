# myPlanet refactor round work orders

date: 2026-10-01
base commit: `286e66e0e1daf285c34d8456deb0028016bf858a`
open PRs checked: #4075, #8175, #10993, #13287, #13355, #13415, #13604, #13657, #13848, #13928, #14427, #14650, #14883, #15108, #15226, #15266, #15267, #15559, #15808, #15820, #15824, #15825, #15951, #16594, #16623, #17187, #17254, #17435, #17680, #17694, #17776, #17811, #17812
workflow evidence checked: the latest default-branch test run, release run, and available build runs were green; workflow files are excluded because #17812 touches `build.yml`, `release.yml`, and `test.yml`.

Candidates below are ranked by expected user impact divided by blast radius. Every listed file was opened at the base commit, and every file touched by any open PR—not only review-ready PRs—was excluded.

### 1. Index calendar meetups by local date when data arrives (roadmap 7)
context: `CalendarFragment.onViewCreated` stores the raw meetup list and creates calendar markers at `CalendarFragment.kt:46-53`.
Every date tap then scans every meetup and repeatedly resolves the system zone at `CalendarFragment.kt:56-65`; this makes interaction cost grow with the full calendar history instead of the selected day's agenda.
files: `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt` — `CalendarFragment.onViewCreated` and its meetup state. Leave `CalendarViewModel`, `EventsAdapter`, calendar resources, and date formatting behavior alone.
steps:
1. Replace the fragment's raw-list lookup state with a `Map<LocalDate, List<Meetup>>` built once for each `viewModel.meetups` emission.
2. Resolve `ZoneId.systemDefault()` once per emission and reuse it while converting `Meetup.startDate` values.
3. Preserve marker creation and ordering exactly, including multiple markers or agenda entries for the same date.
4. Make the click listener retrieve the selected day's list directly and retain the current no-dialog behavior for an empty date.
5. Remove imports or fields made unused by the indexed lookup.
6. Keep all date-index state private to the fragment and derived solely from the latest emission.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; with several meetups on one date and another meetup elsewhere, tapping the populated date shows only that date's agenda in the original order, while tapping an empty date opens nothing.
size budget: about 15-30 changed lines in 1 file.
out of scope: Do not change calendar visuals, meetup persistence, or time-zone semantics.
Do not introduce a new ViewModel API.

---

### 2. Make course pager stable-ID membership constant-time (roadmap 2+7)
context: `CoursesPagerAdapter.submitList` already maintains a stable ID map at `CoursesPagerAdapter.kt:18-37`.
However, ViewPager calls `containsItem` and the adapter linearly scans all steps at `CoursesPagerAdapter.kt:67-69`, multiplying work during page reconciliation and navigation.
files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt` — `CoursesPagerAdapter.submitList` and `containsItem`. Leave `CourseDetailFragment`, `CourseStepFragment`, and `DiffUtils` alone.
steps:
1. Add adapter-owned membership state for the stable IDs present in the current submitted list.
2. Rebuild that state from `newSteps` only after the diff inputs have been prepared, without changing the persistent step-to-ID allocation.
3. Implement `containsItem` as membership in that state, retaining the reserved course-detail ID behavior.
4. Confirm removed steps no longer report membership while reinserted step IDs remain stable.
5. Keep the existing no-op fast path when `newSteps == steps`.
6. Avoid retaining a second copy of the step strings solely for membership checks.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; swiping a course, refreshing with reordered steps, removing a step, and restoring it keeps the correct fragment on each page without flashes or duplicate pages.
size budget: about 10-25 changed lines in 1 file.
out of scope: Do not replace ViewPager2, change fragment arguments, or alter diff semantics.
Do not redesign global navigation in this task.

---

### 3. Make team pager stable-ID membership constant-time (roadmap 2+7)
context: `TeamPagerAdapter` assigns stable IDs in its initializer and `updatePages` at `TeamPagerAdapter.kt:24-49`.
Its `containsItem` still walks every configured page at `TeamPagerAdapter.kt:62-64`, an avoidable hot path whenever ViewPager reconciles role-dependent tabs.
files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt` — initializer, `updatePages`, and `containsItem`. Leave `TeamPageConfig`, `PlanFragment`, `MembersFragment`, and `RequestsFragment` alone.
steps:
1. Track the IDs of pages in the current `pages` list in a dedicated set initialized with the adapter.
2. Recompute current membership when `updatePages` accepts a new list while retaining the existing long ID assigned to each page key.
3. Replace the linear `pages.any` lookup in `containsItem` with the set lookup.
4. Preserve diff dispatch ordering and all fragment/listener argument setup.
5. Cover mentally and manually the empty-page, removed-page, and re-added-page cases before running tests.
6. Keep the membership set private and derived from `pages` rather than exposing new adapter API.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; changing between team roles still adds and removes the appropriate tabs, preserves the selected surviving page, and never resurrects a removed fragment.
size budget: about 10-25 changed lines in 1 file.
out of scope: Do not change which team pages exist, their titles, or fragment factories.
Do not alter the app-wide navigation design.

---

### 4. Search chat records without allocating wrapper copies (roadmap 7+8; advances 9)
context: `ChatSearch.fullConvoSearch` normalizes every conversation into a complete `ConvoChat` graph before matching at `ChatSearch.kt:33-69`.
`searchByTitle` repeats the same eager wrapper pattern with `TitleChat` at `ChatSearch.kt:72-101`, increasing peak memory for large offline histories even when an early conversation matches.
files: `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt` — `fullConvoSearch`, `searchByTitle`, and private wrapper classes; `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt` — existing search-order and match tests. Leave `ChatHistory`, `ChatSearchMode`, and `Utilities.normalizeText` alone.
steps:
1. Iterate chats directly inside the existing dispatcher context and normalize only the title or conversation currently being examined.
2. Remove `TitleChat` and `ConvoChat` once no call path needs the temporary object graphs.
3. Preserve the four full-conversation result buckets and the title-search starts-with-before-contains ordering.
4. Extend the existing tests for a later conversation match, null text, and a chat that satisfies multiple match categories only once.
5. Keep query normalization outside the per-chat loops and retain cancellation-friendly suspending execution.
6. Run the focused `ChatSearchTest` coverage before the required full unit suite.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; title, question, and response searches return the same chats in the same priority order, including diacritic-normalized matches.
size budget: about 45-80 changed lines across 2 files.
out of scope: Do not change matching rules, add fuzzy search, or persist an index.
Do not introduce Android APIs; keeping this utility platform-free directly supports roadmap 9.

---

### 5. Compare multiple exam answers in linear time (roadmap 7+8; advances 9)
context: `ExamAnswerUtils.checkMultipleSelectAnswer` lowercases and sorts both answer collections at `ExamAnswerUtils.kt:77-86`.
Sorting is unnecessary for equality that ignores order, and it creates two lists plus sorting work on every answer check.
files: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` — `checkMultipleSelectAnswer`; `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt` — multiple-selection correctness cases. Leave `ExamQuestion`, choice-cache behavior, and single/text answer matching alone.
steps:
1. Normalize selected and correct values once using the same locale behavior as today.
2. Compare frequency maps rather than sorted lists so duplicate values remain semantically significant.
3. Retain the null and unequal-size early exits.
4. Add regression cases for reordered answers, repeated answers with equal counts, and repeated answers with different counts.
5. Verify no Android type is introduced into the utility or its tests.
6. Keep the helper private so no unused API is added for this optimization.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; a multi-select response remains correct regardless of choice order, but missing, extra, or differently duplicated choices remain incorrect.
size budget: about 15-35 changed lines across 2 files.
out of scope: Do not alter case-folding locale policy, accepted question types, choice parsing, or the bounded choice cache.
Keep the implementation platform-neutral for roadmap 9.

---

### 6. Remove the cleanup worker's per-batch set copy (roadmap 5+7)
context: `FreeSpaceWorker.doWork` already accumulates unique resource IDs in a mutable set at `FreeSpaceWorker.kt:35-47`.
At every 25-item boundary it copies that set with `toSet()` solely to call `markResourcesAsNotOffline` at `FreeSpaceWorker.kt:47-50`, even though the repository accepts a read-only `Set` view and the call completes before the set is cleared.
files: `app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt` — `FreeSpaceWorker.doWork`; `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt` — existing batching assertions. Leave `ResourcesRepository`, recursive deletion, WorkManager scheduling, and progress reporting alone.
steps:
1. Pass the accumulated set directly to the suspending repository call at the full-batch boundary.
2. Keep `clear()` strictly after that call returns so the repository never observes an emptied batch during invocation.
3. Preserve the final partial-batch call and the 25-ID batch limit.
4. Strengthen the existing batching test to capture argument contents at call time and verify no IDs are lost across a full plus partial batch.
5. Remove no behavior or cancellation checks from the worker.
6. Run the existing worker tests with captured repository arguments before the full suite.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; clearing storage still marks every successfully cleared resource offline flag false, skips the `cv` directory, and reports the same deleted-file and byte totals.
size budget: about 5-20 changed lines across 2 files.
out of scope: Do not rewrite recursive deletion, change batch size, or parallelize file deletion.
Do not change storage UI copy.

---

### 7. Build join-request notification details without intermediate triples (roadmap 1+7; advances 9)
context: `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` builds `intermediateList` of `Triple` objects at `NotificationsRepositoryImpl.kt:299-320`, then walks it twice more to gather users and produce the result at lines 321-336.
Join-request refresh can instead index the fetched records directly, reducing allocations in a data-layer enrichment path.
files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — `getJoinRequestDetailsBatch`; `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` — batch-enrichment tests. Leave notification DAOs, `TeamsRepositoryImpl`, `UserRepositoryImpl`, and public notification models alone.
steps:
1. Gather unique team IDs and user IDs from the fetched join requests without creating `Triple` records.
2. Keep the existing batched team-name and user queries and their empty-input guards.
3. Build the final related-ID map in one pass over the original join requests using the two lookup maps.
4. Preserve `Unknown Team` and `Unknown User` fallbacks, empty-ID exclusion, and input-result mapping behavior.
5. Extend existing tests with duplicate team/user IDs and missing names to assert query deduplication and fallbacks.
6. Keep result-map construction local to the existing private function.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; notification rows show the same requester and team labels, and a batch with repeated requesters performs one batched user lookup rather than per-row work.
size budget: about 35-65 changed lines across 2 files.
out of scope: Do not touch repository implementations owned by open PRs, change notification wording, or add DAO queries.
Concentrating mapping in Kotlin is the only roadmap 9 movement here.

---

### 8. Return the first successful configuration probe promptly (roadmap 5+7)
context: `ConfigurationsRepositoryImpl.getMinApk` starts primary and alternative checks concurrently at `ConfigurationsRepositoryImpl.kt:273-280`, but `awaitAll()` at lines 282-286 waits for every probe before selecting a success.
A fast working server can therefore be held hostage by a slow or timing-out peer during login/sync configuration.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` — `getMinApk`; `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt` — configuration URL race tests. Leave `checkConfigurationUrl`, `ServerUrlMapper`, API interfaces, and URL preference updates alone.
steps:
1. Consume probe completions as they arrive and return immediately when the first `UrlCheckResult.Success` is observed.
2. Cancel and join outstanding probe children after selecting a success so no request leaks beyond the method.
3. If every probe fails, retain deterministic fallback behavior based on the original URL order rather than completion timing.
4. Preserve exception logging and the existing mapping into `ConfigurationResult.Success` or `Failure`.
5. Add coroutine tests where the alternative succeeds before a delayed primary and where all probes fail in reversed completion order.
6. Verify cancellation is rethrown rather than converted into a configuration failure.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; when either configured server responds successfully, setup proceeds without waiting for the other probe's timeout, while all-failure messaging remains unchanged.
size budget: about 50-100 changed lines across 2 files.
out of scope: Do not change timeout values, retry policy, URL rewriting, or credentials.
Do not alter server preference persistence.

---

### 9. Suppress duplicate upload scheduler UI states (roadmap 5+7)
context: `WorkManagerUserDataUploadScheduler.enqueueUserDataUpload` maps every emission of a list of `WorkInfo` objects to `SyncUiState` at `UserDataUploadScheduler.kt:32-49`.
WorkManager may emit metadata-only updates while the state is unchanged, causing redundant loading/success renders in every caller.
files: `app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt` — `WorkManagerUserDataUploadScheduler.enqueueUserDataUpload` and `mapWorkInfoToState`. Leave `UserDataWorker`, callers, DI binding scope, and unique-work policy alone.
steps:
1. Apply flow deduplication after mapping `WorkInfo` to `SyncUiState`, not before it.
2. Use structural equality already provided by the UI-state types; do not create a second state model.
3. Preserve the initial idle mapping, loading transition, terminal success message, and error messages.
4. Keep awaiting the enqueue operation before observing unique work.
5. Clean and sort imports after adding the flow operator.
6. Confirm distinct filtering is scoped to each invocation rather than shared between uploads.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; one upload still shows idle/loading followed by exactly one terminal success or error render, and repeated WorkManager metadata emissions do not flicker or repeat terminal feedback.
size budget: about 3-10 changed lines in 1 file.
out of scope: Do not change `ExistingWorkPolicy.KEEP`, merge upload types, or alter worker inputs.
Do not add a new dependency.

---

### 10. Use the injected clock for multi-submission report timestamps (roadmap 4+8; advances 9)
context: `SubmissionsRepositoryExporter` already receives `TimeProvider` at `SubmissionsRepositoryExporter.kt:27-34` and uses it in single-report filenames at lines 112-118.
The multi-report header bypasses that dependency with `Instant.now()` at `SubmissionsRepositoryExporter.kt:173-179`, making output nondeterministic and leaving one direct wall-clock access in repository support code.
files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` — `generateMultipleSubmissionsPdf`;
`app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt` — formatter/clock coverage. Leave PDF layout, DAOs, `TimeUtils`, file naming, and Android `PdfDocument` migration alone.
steps:
1. Convert `timeProvider.now()` to an `Instant` for the generated-at header instead of calling the system clock directly.
2. Keep the existing formatter, locale, zone, and visible timestamp format unchanged.
3. Update the existing test to exercise a fixed `TimeProvider` value through the report timestamp path rather than testing a locally constructed formatter only.
4. Assert the fixed instant produces stable header text across repeated generation.
5. Remove any import made unused by the change while retaining the `Instant` conversion import if needed.
6. Reuse the injected clock already supplied by production DI; add no constructor parameter or binding.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; exported multi-submission PDFs display the same correctly formatted current local time in production, while tests can deterministically control it.
size budget: about 15-40 changed lines across 2 files.
out of scope: Do not redesign PDF rendering, move Android graphics into a shared module, or change report content.
Eliminating direct clock access is the only seam toward roadmap 9 in this task.

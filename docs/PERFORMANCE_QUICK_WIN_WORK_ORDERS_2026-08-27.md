# Performance quick-win work orders

date: 2026-08-27
base commit: `89fd72c251df68ed01094091d4de7ba7a2571ebe`
open PRs checked: could not check open PRs (GitHub CLI had no authentication and the GitHub web/API request returned HTTP 401)

### 1. remove identity copies from submission hydration queries (roadmap 1+7+9)

context: `SubmissionsRepositoryImpl.getExamsByIds` returns `examDao.getByIds(examIds).map { it }` at `SubmissionsRepositoryImpl.kt:94-97`, allocating a second list without transforming any element. The same repository also copies `submission.answers` with `map { it }` in its hydration path, so removing both identity transforms reduces allocation during survey and submission loading while keeping repository results platform-shaped for roadmap 9.

files: touch only `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, specifically `getExamsByIds` and the identity copy inside `hydrateSubmission`; leave `SubmissionDao`, `ExamDao`, and all callers unchanged.

steps:
1. Return the DAO list directly from `getExamsByIds` after preserving the empty-input short circuit.
2. Replace the identity mapping of `submission.answers` with a null-safe direct list value that preserves the existing empty-list fallback.
3. Keep ordering, mutability expectations, nullable handling, and public repository signatures unchanged.
4. Remove imports only if these edits make them unused; do not reformat unrelated repository code.
5. Run the focused repository test and the required full default-flavor unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.SubmissionsRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass; pending-survey and submission screens show the same exams, answers, order, and counts as before.

size budget: about 4 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no DAO query changes, no repository-interface changes, and no submission model rewrite.

---

### 2. eliminate redundant answer and question list copies in progress calculation (roadmap 3+7+9)

context: `ProgressRepositoryImpl.submissionMap` applies `map { it }` to both `answerDao.getBySubmissionIds` and `questionDao.getByIds` at `ProgressRepositoryImpl.kt:146-151`. These two full-list allocations occur immediately before grouping and indexing whenever course progress is calculated, with no value transformation.

files: touch only `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`, specifically private function `submissionMap`; leave `AnswerDao`, `QuestionDao`, `calculateCourseProgress`, and JSON output construction alone.

steps:
1. Use the answer DAO result directly when submission IDs are present.
2. Use the question DAO result directly when question IDs are present.
3. Preserve both empty-input guards so no new database query is issued for an empty ID list.
4. Confirm grouping, question lookup, mistake totals, and generated JSON remain byte-for-byte equivalent for the same inputs.
5. Run the focused progress repository tests and the required full unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ProgressRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass; course progress still reports identical completion and mistake totals.

size budget: about 2 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no schema, DAO, scoring-rule, or progress JSON format changes.

---

### 3. make adoptable-survey exclusion lookup constant-time (roadmap 1+7+9)

context: `SurveysRepositoryImpl.getAdoptableTeamSurveys` builds `excludedIds` with list concatenation and then calls `excludedIds.contains(it.id)` for every survey at `SurveysRepositoryImpl.kt:260-269`. Converting the combined IDs to a set once avoids repeated linear scans on teams with many submissions while retaining platform-free collection logic for roadmap 9.

files: touch only `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt`, specifically `getAdoptableTeamSurveys`; leave `getTeamOwnedSurveys`, `getTeamSubmissionExamIds`, `ExamDao`, and `SubmissionDao` unchanged.

steps:
1. Build `excludedIds` as a set after combining team submission exam IDs and adopted source survey IDs.
2. Preserve the existing null-or-empty team guard and both survey predicates.
3. Keep DAO call count, filtering order, result order, and returned `List<StepExam>` unchanged.
4. Add no helper abstraction; keep the optimization local and remove no unrelated sequence operations.
5. Run focused surveys repository tests plus the full default-flavor unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.SurveysRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass; the adopt-survey picker includes and excludes exactly the same surveys in the same order.

size budget: about 2 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no survey adoption behavior, Room query, entity, or UI changes.

---

### 4. collect synchronized activity lookup keys in single passes (roadmap 1+5+7+9)

context: `ActivitiesRepositoryImpl.insertLoginActivitiesFromSync` walks `documentList` separately to derive remote IDs, login times, and user names at `ActivitiesRepositoryImpl.kt:350-365`. Three `map`/`filter`/`distinct` pipelines allocate intermediate lists before two DAO lookups, increasing sync cost for large activity batches.

files: touch only `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`, specifically `insertLoginActivitiesFromSync`; leave `activityFromJson`, `OfflineActivityDao`, upload handling, and transaction boundaries unchanged.

steps:
1. Allocate insertion-ordered mutable sets for non-empty IDs, positive login times, and non-empty user names.
2. Populate all three sets in one pass over `documentList` using the same `JsonUtils` accessors and validity rules.
3. Pass collection views compatible with the existing DAO method signatures without reintroducing per-field pipelines.
4. Preserve document filtering, fallback-key construction, deduplication semantics, and activity output order.
5. Run the focused activity repository tests and the required full default-flavor unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ActivitiesRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass; activity sync inserts and updates the same records and still deduplicates repeated server documents.

size budget: about 15 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no DAO SQL changes, batching redesign, upload changes, or activity model migration.

---

### 5. sort filtered courses without allocating lowercase titles (roadmap 1+7+9)

context: `CoursesRepositoryImpl.getCoursesBySearchAndFilter` sorts with `it.courseTitle?.lowercase() ?: ""` at `CoursesRepositoryImpl.kt:297-305`. That selector allocates a lowercase string during comparisons for every retained course; a case-insensitive comparator can preserve alphabetical behavior without materializing normalized copies.

files: touch only `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`, specifically `getCoursesBySearchAndFilter`; leave `mapCourses`, `TagsRepository`, `CourseDao`, and activity logging unchanged.

steps:
1. Replace the lowercase sort selector with a null-safe, case-insensitive title comparator.
2. Preserve ascending ordering, null-title filtering, tag filtering, and stable ordering for titles that compare equally ignoring case.
3. Use a locale-independent comparison and do not add a collator or dependency.
4. Keep the lazy filter sequence and final list materialization unchanged.
5. Run focused courses repository tests and the required complete unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.CoursesRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass; course search/filter results retain the same case-insensitive alphabetical presentation.

size budget: about 4 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no SQL-side filtering, pagination, tag-query, or course-screen changes.

---

### 6. avoid concatenation allocation when assembling resource search results (roadmap 7+9+10)

context: `ResourcesSearchUtils.searchList` returns `startsWithQuery + containsQuery` at `ResourcesSearchUtils.kt:13-24`. The plus operator creates a third list and copies both buckets after the function already allocated them; appending the secondary bucket into the primary bucket removes that extra allocation without tying the search utility to Android, advancing roadmap 9 and keeping Compose consumers portable for roadmap 10.

files: touch only `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt`, specifically generic function `searchList`; leave `searchLocalModels`, `Utilities.normalizeText`, resource models, and UI callers unchanged.

steps:
1. Retain the two-bucket scan so prefix matches continue to precede other matches.
2. Append `containsQuery` into `startsWithQuery` after scanning rather than using list concatenation.
3. Return the resulting list without changing the empty-query fast path or nullable-title behavior.
4. Preserve generic typing, match ordering within each bucket, token matching, and normalization semantics.
5. Run the focused utility tests and the required full default-flavor unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ResourcesSearchUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass; resource searches return prefix matches first and otherwise preserve the previous order.

size budget: about 2 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no search algorithm rewrite, fuzzy matching, model changes, Compose work, or new dependency.

---

### 7. normalize the collection filter query once per keystroke (roadmap 6+7+10)

context: `CollectionsFragment.filterTags` lowercases `charSequence` inside the predicate for every tag at `CollectionsFragment.kt:110-119`. The debounced filter still repeats identical query normalization across the whole collection list, producing avoidable strings on each user search; hoisting it also clarifies the state transformation needed for future portable UI under roadmap 10.

files: touch only `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`, specifically `filterTags`; leave `setListeners`, `buildTagDataList`, `CollectionsAdapter`, and selection callbacks unchanged.

steps:
1. Compute the lowercase query once before filtering non-empty input.
2. Reuse that normalized value for every nullable tag-name comparison.
3. Retain `Locale.ROOT`, the empty-query fast path, parent-only filtering, and existing child expansion state.
4. Do not change debounce timing, list submission, selection behavior, or lifecycle collection.
5. Run the required default-flavor unit suite and manually exercise the collection dialog filter.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; typing mixed-case text into the collection filter yields the same matching parents, and clearing it restores the complete list.

size budget: about 3 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no Compose migration, adapter rewrite, child-tag search, debounce change, or new test framework.

---

### 8. select newest submissions without building grouped lists (roadmap 3+7+10)

context: `SubmissionViewModel.filteredSubmissionsRaw` groups every filtered submission into lists and then scans each group for its maximum at `SubmissionViewModel.kt:68-84`. A single pass can retain the newest submission and count per parent, reducing allocations in view-model state production while keeping this work outside the UI and aligned with roadmap 10.

files: touch only `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt`, specifically the `filteredSubmissionsRaw` combine transformation; leave repository calls, `SubmissionViewData`, public flows, and fragment rendering unchanged.

steps:
1. Replace `groupBy` with insertion-ordered maps that track the newest submission and count for each `parentId` in one pass.
2. Preserve the current winner rule for equal `lastUpdateTime` values and the current group encounter order.
3. Build `uniqueRawSubmissions` and `submissionCountMap` from those maps without rescanning grouped lists.
4. Keep query filtering, user fallback lookup, final descending sort, and emitted `Triple` types unchanged.
5. Run focused view-model tests and the required full unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.submissions.SubmissionViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass; submission history shows the same newest row and count for each parent after filtering.

size budget: about 20 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no repository query changes, UI migration, new use case, state-flow contract change, or sort-policy change.

---

### 9. deduplicate unknown task assignees during collection (roadmap 3+7+10)

context: `TeamsTasksFragment.refreshTaskList` builds a list of assignee IDs, filters it, and then calls `distinct()` at `TeamsTasksFragment.kt:295-321`. Collecting valid unknown IDs directly into an insertion-ordered set avoids intermediate collections on every task refresh while preserving fetch order; keeping the optimization at the view-model boundary prepares the data flow for roadmap 10.

files: touch only `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt`, specifically `refreshTaskList`; leave `TeamsTasksViewModel.fetchAssigneeNames`, `TasksAdapter`, task filter helpers, and snapshot comparison unchanged.

steps:
1. Collect non-null assignees directly into an insertion-ordered set.
2. Reject blank IDs and IDs already present in `knownAssigneeIds` during collection rather than in later list passes.
3. Pass the deduplicated collection to `fetchAssigneeNames` in first-seen task order.
4. Preserve the empty-fetch fast path, dispatcher use, snapshot short circuit, and adapter update timing.
5. Run the full default-flavor unit suite and manually refresh each task tab with repeated assignees.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; All, My, and Completed task tabs display the same assignee names, including when several tasks share one assignee.

size budget: about 5 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no task sorting, repository query, adapter, navigation, or Compose changes.

---

### 10. deduplicate course membership in one collection pass (roadmap 1+7+9)

context: `MyCourse.setUserId` filters into a mutable list, performs a linear `contains`, conditionally appends, and then runs `distinct()` at `MyCourse.kt:53-60`. An insertion-ordered set can validate, deduplicate, and add the requested user without repeated scans, keeping this model logic expressed only with platform-free Kotlin collections for roadmap 9.

files: touch only `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt`, specifically `setUserId`; leave `removeUserId`, `copy`, Room annotations, attachment helpers, and repository membership methods unchanged.

steps:
1. Keep the null-or-blank incoming user ID fast return.
2. Collect existing non-null, non-blank membership IDs into an insertion-ordered mutable set.
3. Add the requested user ID to that set and assign its list form back to `this.userId`.
4. Preserve first-occurrence order, duplicate removal, and the public method signature.
5. Run the full default-flavor unit suite and existing course repository coverage.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.CoursesRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass; joining a course never duplicates the user ID and existing valid membership order remains unchanged.

size budget: about 5 changed lines in 1 file, well under 150 lines and 5 files.

out of scope: no Room schema change, membership API redesign, Android-helper extraction, or changes to other models.

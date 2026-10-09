# myPlanet test-workflow refactor work orders

date: 2026-10-09 · base commit: f18c50d25010913a0f85ba9d744705349f13b051
open PRs checked: #18076, #18050, #18045, #18029, #17832, #17811, #17694, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883

Currently open PRs, checked before writing:
- #18076 — Ratings: fixed delayed rating updates (fixes #16958)
- #18050 — sync: smoother repository shelf logging (fixes #18041)
- #18045 — Kmp phase4
- #18029 — Kmp phase3
- #17832 — Kmp phase2
- #17811 — mySurvey: survey number update
- #17694 — Shared kmp module scaffold
- #16623 — teams: smoother interactive team tasks status and board handling (fixes #16616)
- #16594 — actions: smoother size labeller fetching (fixes #16344)
- #15951 — teams: smoother repository update requesting (fixes #15568)
- #15825 — local event task reminders workmanager notifications (address #15115)
- #15824 — gamification achievement hub offline badges streaks (address #15114)
- #15820 — teams: smoother task and meetup comment threads managing (fixes #15112)
- #15808 — sync: intelligent incremental sync via couchdb changes feed (fixes #15807)
- #15559 — exam: redesign UI with elapsed timer and cards (fixes #15558)
- #15267 — prevent download popup dialog cropping when text size is large (fixes #15263)
- #15266 — prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265)
- #15226 — feat(flutter): Flutter/Dart port of myPlanet (phases 1–28)
- #15108 — fix event calendar marking (fixes #15107)
- #14883 — team: add leaderboard tab (fixes #14880)

All changed filenames from all 20 open PRs were excluded, regardless of age, draft status, or labels. Seven were updated within the past week. The test workflow and app Gradle configuration are owned by open PRs, so this round reduces their workload through isolated test edits.

Workflow evidence: `.github/workflows/test.yml:28` runs two shards; line 70 uses offline Robolectric with four Gradle workers; lines 93–104 report timings. `.github/workflows/build.yml:27` builds two flavors, with the build command at line 60. `.github/scripts/test_timing_summary.py:142` explicitly reports time summed across forks, which must not be treated as workflow wall time.

Ranking below uses avoidable work divided by changed lines and coverage risk: assertion-free repetition first, then the largest fixture reductions, then redundant Android tests. These are source-based cost estimates, not a measured slowest-class ranking: GitHub returned no workflow runs for the base commit, no local JUnit results exist, and an attempted baseline failed before executing tests with “Could not determine a usable wildcard IP for this machine.” No speedup or green baseline is claimed.

Execution rules for every work order: change only its listed editable file; use the unchanged neighbors for reference. Preserve real Room execution, a fresh database per test and teardown; keep large request lists independently generated from sparse fixtures. Seed rows immediately before and after each actual chunk boundary so skipping a later chunk still fails; do not mock SQL or reduce input lengths below the existing limits. No new files, dependencies, ignored tests, placeholders, production changes, or Android imports are authorized.

Performance acceptance for each edited class: repeat its targeted command three times before and after on the same runner, and compare the median class time in the fresh JUnit XML. Record fixture/test-count reductions and timings in the implementing PR; do not add timing assertions. For task 1, record removal of 5,000 unchecked mapper invocations instead. After any task, `./gradlew testDefaultDebugUnitTest` must pass; after integrating the round, both `./gradlew testDefaultDebugUnitTest -PtestShardTotal=2 -PtestShardIndex=0 -ProbolectricOffline=true` and the same command with `-PtestShardIndex=1` must pass. Compare the average wall time from five successful test and build workflow runs on the same integrated commits, measured from earliest job start to latest job finish and excluding queue time; report whether tests average faster than builds, without claiming this source-only plan guarantees it.

---

### 1. Remove the assertion-free exam mapper benchmark (roadmap 7+8)
context: `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt:33` creates 500 documents and line 55 repeats their mapping ten times.
The test discards every returned value and only prints elapsed milliseconds at line 62; it provides no correctness or performance threshold while adding 5,000 invocations to every suite run.
files:
- Edit/delete `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt` — class `StepExamBenchmarkTest`, including `setup`, `tearDown`, and `testBenchmarkInsertCourseStepsExams`.
- Leave `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt` unchanged — `StepExamTest.testInsertCourseStepsExams` already asserts mapped values at lines 60–72.
steps:
1. Confirm the neighboring correctness test still asserts identifier, name, course/step association, sharing metadata and question count.
2. Delete the benchmark file completely, including its static Android mock setup and cleanup.
3. Check there are no remaining references to the deleted test class; remove no other tests.
4. Run the acceptance commands and record the removed invocation count in the PR.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.model.StepExamTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; exam metadata mapping remains covered and the unchecked benchmark disappears from reports.
size budget: 64 deleted lines, 1 file.
out of scope: no production mapper changes and no replacement benchmark framework.
This serves roadmap 8 by retaining meaningful regression coverage; it does not directly advance 9/10.

---

### 2. Keep exam chunk coverage with sparse database fixtures (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt:37`, 195, 211, 227 and 248 each create 1,200 exams.
These five tests write 6,001 rows collectively, although chunk traversal depends on request length; the course lookup fixture is also repeated by two tests.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt` — `ExamDaoTest.getByCourseIds_handles1200Courses`, `getByIds_with1200IdsAndDuplicates_returnsAllMatchesWithoutDuplicates`, `getByStepIds_with1200Ids_returnsAllMatches`, `getByCourseIds_with1200Ids_returnsAllMatches`, and `getTeamOwnedSurveys_with1200SubmissionIds_returnsTeamOwnedAndSubmissionSurveysWithoutDuplicates`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt` unchanged — `ExamDao.getByCourseIds`, `getByIds`, `getByStepIds`, and `getTeamOwnedSurveys`; lines 25, 35 and 50 confirm 900-item chunks.
steps:
1. Remove `getByCourseIds_handles1200Courses`; retain the later course test that additionally supplies a duplicate ID.
2. In each remaining large-input test, seed only indices 1, 900, 901 and 1200 while preserving independently generated 1,200-ID requests and their duplicates.
3. Retain the separately seeded team-owned survey in the team query; expect exactly five unique result IDs there and four in the other queries.
4. Assert exact result-ID sets plus list sizes, including the team-owned row only once despite its appearance in each chunk's query.
5. Rename retained methods to describe large request inputs and sparse fixtures; preserve all small filtering tests.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.ExamDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; exams from both request chunks and team-owned surveys still appear exactly once.
size budget: approximately 65–95 changed lines, 1 file.
out of scope: no query changes or shared fixture infrastructure.
This protects data-layer behavior for roadmap 1; Android Room integration remains platform-specific, so it makes no direct 9/10 claim.

---

### 3. Trim library fixture writes and redundant chunk scenarios (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt:39`, 59, 81, 103 and 206 each seed 1,200 library rows.
The later sparse course test at line 405 already exercises large input plus deduplication, and the offline-status test at line 224 covers the large-input behavior repeated at line 382.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt` — `MyLibraryDaoTest.getByIds_handles1200Ids`, `getByResourceIds_handles1200Ids`, `getByResourceIdsNotUserPattern_handles1200Ids_andExcludesUser`, `getByCourseIds_handles1200Courses`, `getByResourceIdsByRowid_handles1200Ids`, and `markAsNotOfflineByResourceIds_clearsResourceOfflineStatusForGivenIds`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` unchanged — `MyLibraryDao.getByIds`, `getByResourceIds`, `getByResourceIdsNotUserPattern`, and `getByResourceIdsByRowid`; its wrappers use 900-item chunks.
steps:
1. Delete only the redundant full-fixture course test and the redundant 1,200-row offline-status test named above; keep their stronger neighboring scenarios.
2. Seed indices 1, 900, 901 and 1200 in the four retained large-input lookup tests.
3. Generate all 1,200 requested keys independently of the seeded rows; retain existing duplicate keys and empty-input assertions.
4. For user-pattern exclusion retain odd/even ownership: expect rows 1 and 901, and explicitly exclude rows 900 and 1200.
5. Assert exact IDs and counts for the other lookups; retain insertion-order assertions for the rowid lookup with first row 1 and last row 1200, and update method names.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.MyLibraryDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; library lookup, ownership filtering, ordering, course deduplication and offline-status clearing remain covered.
size budget: approximately 95–135 changed lines, 1 file.
out of scope: leave stale-public-resource deletion and title-projection fixtures unchanged; no database lifetime changes.
This preserves data-layer contracts for roadmap 1 without moving Android test code into a portable core; no direct 9/10 change.

---

### 4. Reduce task chunk-test setup to boundary rows (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDaoTest.kt:37`, 54 and 71 each build and write 1,200 tasks.
The three wrappers chunk their input at 900, so large request lists with four strategically placed rows can check traversal without 3,600 fixture writes.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDaoTest.kt` — `TeamTaskDaoTest.getByIds_with1200IdsAndDuplicates_returnsAllWithoutDuplicates`, `getByTitles_with1200TitlesAndDuplicates_returnsAllWithoutDuplicates`, and `markTasksNotified_with1200IdsAndDuplicates_updatesAll`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDao.kt` unchanged — `TeamTaskDao.getByIds`, `getByTitles`, and `markTasksNotified`; chunk sizes are visible at lines 22, 48 and 56.
steps:
1. Replace the three dense fixture index ranges with indices 1, 900, 901 and 1200.
2. Keep all existing 1,200-key request ranges and duplicates independently generated; append a duplicate of a seeded key from the second chunk to each request.
3. For both lookup tests assert the exact four returned identities and list size, so duplication cannot hide behind a set comparison.
4. For notification updates read back the exact four seeded tasks, assert their IDs and notified flags, and seed one unrequested false-flag task that must remain false.
5. Rename the three methods to describe sparse fixtures; leave the open-task filtering test unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.TeamTaskDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; task retrieval covers both chunks and notifications update only requested tasks.
size budget: approximately 40–65 changed lines, 1 file.
out of scope: no task-status, repository or production notification changes.
This retains data-layer safety for roadmap 1; no direct contribution to 9/10.

---

### 5. Preserve answer deletion counts with sparse submissions (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt:45`, 59 and 91 seed 1,200, 1,200 and 1,000 answers respectively.
Those rows mainly create request cardinality, and the duplicate-boundary test derives its request from the fixture, unnecessarily tying SQL setup cost to the input size.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt` — `AnswerDaoTest.getBySubmissionIds_with1200SubmissionIds_returnsAllAnswersWithoutThrowing`, `deleteBySubmissionIds_with1200SubmissionIds_deletesAllAndReturnsTotalCount`, and `chunkedOperations_deduplicateRepeatedIdsAcrossChunkBoundary`; reuse `createAnswer`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt` unchanged — `AnswerDao.getBySubmissionIds` and `deleteBySubmissionIds`, which use 900-item chunks at lines 18 and 29.
steps:
1. Seed submissions 1, 900, 901 and 1200 for the two 1,200-input tests; generate their full request lists independently.
2. Seed submissions 1, 900, 901 and 1000 for the duplicate-boundary scenario; retain 1,000 unique requested keys followed by the duplicate of submission 1.
3. Assert exact selected answer IDs and list sizes of four; assert deletion counts of four, not the number of requested keys.
4. Seed an answer for an unrequested submission in each deletion scenario and assert it survives via the existing single-submission query.
5. Preserve the empty-input and mixed-input tests unchanged and rename the edited dense-fixture methods.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.AnswerDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; answer queries span both chunks and deletion returns the number actually removed while preserving unrelated answers.
size budget: approximately 45–70 changed lines, 1 file.
out of scope: no transaction or DAO changes and no mocked deletion counts.
This protects data-layer correctness for roadmap 1; no direct 9/10 migration.

---

### 6. Slim removed-log fixtures while retaining null-user isolation (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt:87`, 111 and 128 materialize a removed-log row for every one of 1,200, 1,000 and 1,200 document IDs.
The useful assertions concern chunk traversal and type/user isolation, which require only boundary targets plus the existing control rows.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt` — `RemovedLogDaoTest.deleteByTypeUserAndDocsChunked_1200Docs_deletesAllAndLeavesOtherTypeAndUser`, `deleteByTypeUserAndDocsChunked_emptyInputAndDeduplicationAcrossChunkBoundary`, and `deleteByTypeUserAndDocsChunked_nullUserId_1200Docs_deletesAllMatchingAndLeavesNonNullUser`; reuse `createLog`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt` unchanged — `RemovedLogDao.deleteByTypeUserAndDocsChunked`, which uses 900-item chunks at line 21.
steps:
1. Keep the full request lists; seed deletion targets only for indices 1, 900, 901 and 1200, or 1000 for the 1,000-ID scenario.
2. Preserve the appended duplicate document ID and empty-input call in the deduplication test.
3. Keep every existing wrong-user and wrong-type control row in the non-null and null-user scenarios.
4. Add one same-user/same-type row whose document ID is outside each deletion request; assert it alone survives in the target partition.
5. Retain the other partitions' exact survivor assertions and rename methods to distinguish request size from seeded row count.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.RemovedLogDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; removed-log cleanup handles both chunks without deleting another user's, another type's or an unrequested document's log.
size budget: approximately 35–60 changed lines, 1 file.
out of scope: no production deletion changes and no consolidation of null/non-null scenarios.
This retains roadmap 1's isolation guarantees; no direct 9/10 change.

---

### 7. Make progress chunk tests sparse at their actual limits (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt:92`, 108 and 121 seed 300, 1,200 and 1,200 progress records.
The tuple query chunks at 250 and the user/course query at 899, so generic 900-boundary fixtures would miss the precise transitions these tests must protect.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt` — `CourseProgressDaoTest.getByCourseUsersAndSteps_handlesLargeTupleChunking`, `getByUserAndCourseIds_handles1200CoursesWithNonNullUserId`, and `getByUserAndCourseIds_handles1200CoursesWithNullUserId`; reuse `createProgress`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt` unchanged — `CourseProgressDao.getByCourseUsersAndSteps` and `getByUserAndCourseIds`; lines 40 and 18 confirm limits 250 and 899.
steps:
1. Seed tuple indices 1, 250, 251 and 300 while retaining all 300 independently generated requested tuples.
2. Seed course indices 1, 899, 900 and 1200 in each user/course test while retaining all 1,200 requested course IDs.
3. Assert exact progress-ID sets and list sizes of four in all three tests.
4. Add one wrong-user control row for a requested course to each user/course test; ensure the null-user scenario explicitly excludes a non-null user's row.
5. Keep the separate cross-product exclusion and empty-input tests intact, and rename edited methods to describe sparse fixtures.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.CourseProgressDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; exact progress tuples and null/non-null user scoping remain correct across both chunk boundaries.
size budget: approximately 40–65 changed lines, 1 file.
out of scope: no raw-query rewrites or artificial changes to chunk sizes.
This preserves data-layer behavior for roadmap 1; no direct 9/10 change.

---

### 8. Reduce visit-query fixtures without losing cutoff coverage (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamLogDaoTest.kt:42` creates 1,200 recent visits and line 52 adds 100 old visits; line 75 creates another 1,200 visits for the user query.
The 2,500 rows are unnecessary for testing the 900-key request split, cutoff predicate and team scoping.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamLogDaoTest.kt` — `TeamLogDaoTest.getRecentTeamVisits_returnsAllRowsAndFiltersByCutoffAcrossLargeChunkedList` and `getTeamVisitsForUsers_returnsAllRowsAcrossLargeChunkedList`.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt` unchanged — `TeamLogDao.getRecentTeamVisits` and `getTeamVisitsForUsers`; lines 20 and 28 use 900-item chunks.
steps:
1. Seed recent matching visits for team indices 1, 900, 901 and 1200; keep the independently generated 1,300-team request.
2. Replace 100 old visits with one at the cutoff and one below it, for requested teams in different chunks; both must be absent.
3. Add a wrong-type visit for a requested team and an otherwise matching visit for an unrequested team; assert exclusion through exact result IDs.
4. For the user query seed users 1, 900, 901 and 1200, retain the 1,200-user request, and add a wrong-team control row.
5. Assert four exact matching IDs in each scenario and keep `emptyInput_returnsEmptyList` unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.TeamLogDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; visit queries still honor strict cutoff, visit type, team and user filters across both chunks.
size budget: approximately 65–95 changed lines, 1 file.
out of scope: no analytics query changes and no shared database across test methods.
This keeps roadmap 1's visit-query contracts intact; no direct 9/10 migration.

---

### 9. Replace the dense news lookup fixture with boundary rows (roadmap 1+7+8)
context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NewsDaoTest.kt:327` creates 1,200 news entities solely for the underscore-ID lookup.
The adjacent large primary-ID tests at lines 342 and 356 already demonstrate that sparse rows can protect large-input behavior, while the underscore-ID test derives its entire request from inserted rows.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NewsDaoTest.kt` — `NewsDaoTest.getByUnderscoreIds_handlesEmptyInputAndLargeChunkedList` only.
- Leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt` unchanged — `NewsDao.getByUnderscoreIds` uses 900-item chunks at line 30.
steps:
1. Seed only indices 1, 900, 901 and 1200, preserving different primary and underscore identifiers.
2. Generate the full 1,200 underscore-ID request independently and append duplicates of indices 1 and 1200.
3. Assert four returned rows with exact primary-ID and underscore-ID sets, including the second chunk's rows.
4. Preserve the empty-input assertion and rename the method to describe sparse fixture coverage.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.NewsDaoTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; remote-ID news lookup spans both chunks without duplicate results.
size budget: approximately 15–25 changed lines, 1 file.
out of scope: leave wildcard escaping, sharing, community-date and primary-ID tests unchanged.
This maintains roadmap 1's identity lookup coverage; no direct 9/10 change.

---

### 10. Remove version tests already covered by cache scenarios (roadmap 7+8)
context: `app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt:147` repeats the exception result asserted at line 76, and line 196 repeats the result asserted at line 133.
The standalone successful version-name test at line 210 repeats the successful lookup exercised at lines 100–116; each extra test repeats Robolectric and MockK setup without protecting a distinct branch.
files:
- Edit `app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt` — remove `VersionUtilsTest.getVersionCode_should_return_0_on_NameNotFoundException`, `getVersionName_should_return_empty_string_on_NameNotFoundException`, and `getVersionName_should_return_versionName`.
- Leave `app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt` unchanged — `VersionUtils.getVersionCode`, `getVersionName`, and `reset`.
steps:
1. Verify retained exception/cache tests assert both the initial fallback and the later successful retry, including exact package-manager call counts.
2. Delete only the three redundant test methods named above and their directly attached annotations/comments.
3. Keep both explicitly pinned pre-P and P SDK tests, the nullable version-name test, Android-ID tests and reset setup unchanged.
4. Remove imports only if deletion actually makes them unused; run the acceptance commands and record three fewer Robolectric tests.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.VersionUtilsTest -ProbolectricOffline=true --rerun-tasks` passes.
- `./gradlew testDefaultDebugUnitTest` passes; version fallback, retry, caching, API branching and nullable metadata behavior remain covered.
size budget: approximately 47–55 deleted lines, 1 file.
out of scope: no SDK pin changes, production cache changes or deletion of distinct Android-ID scenarios.
This advances roadmap 8 through coverage-preserving test pruning; Android platform tests remain appropriate and make no direct 9/10 claim.

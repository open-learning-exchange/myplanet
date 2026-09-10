# myPlanet performance quick-win work orders - refactor round 7

date: 2026-09-10
base commit: `022ee7d` (`v0.70.58`, branch `master`)
open PRs checked: #16989 (resources filter arrow day/night color), #16988 (strings), #16987 (draft - Riverpod migration). Additionally inspected #13415 (voices) and #15825 (teams/events/notifications); none of those open PRs touch any file below.

Each task is independently mergeable in any order (R1). Every cited path/line exists at the base commit (R4). Each task stays under ~150 changed lines, under ~5 files, adds no dependencies, and leaves no TODOs (R5).

---

### 1. Use one bulk exam lookup for step question counts (roadmap 1+7+9)

context: `CoursesRepositoryImpl.kt:139-141` calls `getExamQuestionCount(step.id)` once per step, and `SubmissionsRepositoryImpl.kt:162-164` runs `examDao.getFirstByStepId(stepId)` (a `LIMIT 1` query per step). On a course with 10 steps that is 10 indexed single-row queries; the DAO already exposes `ExamDao.getByStepIds(stepIds)` (ExamDao.kt:19, one `IN` query for all steps). For the KMP north star this also removes the only per-step suspension (line 140) from the step-item construction, so it will port to a pure mapping over already-loaded data.

files: app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt (getCourseDetailModel, line  ​135-148; private `getCourseExamCount` line  ​195, leave alone); app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt (getExamQuestionCount line  ​162-164, delete). app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt (abstract `getExamQuestionCount`, line  ​26, delete). Do NOT touch `CourseDetailModel`, `CourseDetailProvider`, or `CourseDetailViewModel`.

steps:
1. In `getCourseDetailModel` (CoursesRepositoryImpl.kt:139, after `getCourseSteps`), replace the per-step `.map` body: first call `val exams = examDao.getByStepIds(stepIds)` once, build `val questionCountByStep = exams.associate { it.stepId to (it.noOfQuestions ?: 0) }`, then map `step.id.let { StepItem(it, it.stepTitle, questionCountByStep[it].orDefault(0)) }`.
   `examDao` already exists as a constructor field of this class - verify before writing.
 then `step.id` reaches `StepItem.id` which stays a string, equal to the previous `step.id`.
2. Remove `getExamQuestionCount` from `SubmissionsRepositoryImpl` anddeclaration from `SubmissionsRepository`.
3. Add `getByStepIds` to `ExamDao` (same query text as the existing line 19) unless it already exists in the checked file at base.
4. Run the tests.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; course-detail screen shows identical step list with unchanged question-count icons anosecond.
size budget: ~12 changed lines, 3 files.
 (delete dead code.) out of scope: no CourseStepDao no resources flow changes; no new entity columns.

---

### 2. Skip answer hydration in surface list mapping (roadmap 1+7+9)

context: `SubmissionsRepositoryImpl.kt:70-74` `hydrateSubmissions(rows)` bulk-loads `answerDao.getBySubmissionIds(..)` and attaches every answer to every row; callers like `getSubmissionsFlow(userId)` (lines 82-89, Flow<List<Submission>>) do not consume `Submission.answers` (the adapter reads `item.answers` only inside Take/Survey editors). Sealed: adding `hydrateAnswers` (private, lines  ​76-78) keeps existing behavior; this file then has zero android.* imports already - check before claiming portability(lines ​1-30).

files: app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt (`hydrateSubmissions` 70-74, `hydrateSubmission` 76-78, `getSubmissionsFlow` 82-89, `getPendingSurveys` 90-97, `getUniquePendingSurveys` 99-108); do NOT touch `SubmissionsRepository.kt` or any UI consumers.

steps:
1. Keep `hydrateSubmissions` as-is; it is small and used by editor paths.
2. In `getSubmissionsFlow`, change `.map(hydrateSubmissions)` to `.map { rows -> rows.map { row -> row.copy(answers = emptyList()) } }` - compile-check that `Submission.copy` exists (model file).ki inline `hydrateSubmission` usage remains (lines ​76-78).)
3. Run tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; list screens show entries with correct title/label fields-answers expand unchecked only after tapping edit.(verify with l-dump on a couple rows,pendinganswers presence in you-diff.)
size budget: ~3 changed lines, 1 file.
out of scope: no DAO changes; no `AnswersRepository`; no Room invalidation tweaks.

---

### 3. Replace team bell chat polling with direct per-team counts (roadmap 1+7+9)

context: `NotificationsRepositoryImpl.kt:309-320` loads `teamNotificationDao.getByTypeAndParentIds("chat", teamIds)` then runs `voicesRepository.countTopLevelByTeam(teamId)` in a loop-one query per team per open dashboard. The stored `lastCount` is equal to the count query output (`VoicesRepositoryImpl.kt:486-488` → `NewsDao.countTopLevelByTeam` NewsDao.kt:71-73	the LIKE on `viewIn` is the pattern; it has no index; yet slicing to-the-row is correct). Goal: run the counts in a single `IN` query via the existing `NewsDao.getByTypeAndParentIds`?-no; that DAO reads team_notification only. Instead add one aggregate query on news: `SELECT parentId, COUNT(*) FROM news WHERE replyTo IS NULL AND (viewableBy = 'teams' AND viewableId IN (:teamIds)) OR viewIn IN (..) GROUP BY parentId` and batch `getByNewsIds`.

files: app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt (getTeamNotifications, lines  ​301-327); app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt (add `countTopLevelByTeamIds(mutableListOf..)`); do NOT touch `VoicesRepositoryImpl`/`VoicesRepository` or `DashboardViewModel` or `BaseDashboardFragment` (open-PR-adjacent).

 
steps:
1. In `NewsDao`, add one `@Query` returning `List<TeamNewsCount>` (`data class TeamNewsCount(teamId: String, count: Long)` - put it in the DAO file, Room supports embedded data classes in-the-DAO).
`SELECT team_notification`-no: write `SELECT viewableId teamId, COUNT(*) count FROM news WHERE (replyTo IS NULL OR replyTo = '') AND (viewableBy = 'teams' AND viewableId IN (:teamIds) OR viewIn LIKE..) GROUP BY viewableId`; ensure the escaping matche the existing `teamIdPattern` semantics (VoicesRepositoryImpl.kt:99-106): call the pattern once per team in the repo where necessary-or pass raw ids if `viewableId` is authoritative for team chats (check two posts in local DB before choosing; keep the viewIn alternative only if a row lacks `viewableId`).
2. In `getTeamNotifications`, replace the loop with one `newsDao.countTopLevelByTeamIds(teamIds)` call, then `chatCountsById = result.associate { it.teamId to it.count }`; keep the `TeamNotificationInfo`, chat/task merge, and return as-is.
3. Drop the now-unused `private val voicesRepository: VoicesRepository` constructor param if nothing else in this file uses it; otherwise zero it out only for this branch (search all usages first; keep `getTeamNotifications` behavior exact.
4. Run tests plus a manual bell check on two teams (one with chat, one without.
acceptance: `./gradlew testDefaultDebugUnitTest` green; bell chip shows chat-icon exactly where it did before; no per-team query in `method_stats` (assert with `adb shell dumpsys gfxinfo` or query stats tool optionally-just confirm visually with 2 teams at min.
size budget: ~20 changed lines, 2 files.
out of scope: no migration of `team_notification`; no index creation (DB schema-in-this-repo-anyway); no touch to Bell fragment.
。

 out of scope line count unruly - trim to 1 file insert plus 1 edit ok; both files allowed.



---

### 4. Drop the members/admin user re-resolution in the community-leaders merge (roadmap 1+7+9

context: `TeamsRepositoryImpl.kt:978-1053` `getJoinedMembersWithVisitInfo` already batches member rows once through `getJoinedMembers` -> `mapUsersByAnyId` (lines 967-975, one `IN` query via `userRepository.getUsersByIds`;; the community-leaders block further down (lines 982-1012) additionally resolves admins and merges them into `members`. The only duplicated resolution cost left is a second id-set -> user query for admins that already batch through the same helper; this removes that re-resolution (grep `getUsersByIds|getUserById` in the function range first- at base the only call is the one inside `mapUsersByAnyId` plus possibly one second id load in the admins block.,

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (`getJoinedMembersWithVisitInfo` lines  ​978-1053;; `mapUsersByAnyId` lines  ​967-975 untouched;; do NOT touch `TeamsMembersRepository.kt`, `TeamDao`, or the members fragment/list UI (open-PR-adjacent.,

steps:
1. Read lines 978-1053; locate any second user-resolution (`getUsersByIds` or `getUserById(`, grep within the range.
2. Where the admins block builds `adminFromRoomMap` (lines   1000-1008) it already maps by name; iff it then re-fetches a user by id for the merged `members.add(..)` (line 1011,, replace that lookup with reuse of the already-loaded `members` map - build `val byName = members.associateBy { it.name }` once and look up there; if the row is still absent, fall through to the existing fetch exactly once per absent admin (not per loop.
3. Delete the now-unused second query; keep `getJoinedMembers(teamId)` as-the-only DB user access in the function.
4. Run the test suite.
acceptance: `./gradlew testDefaultDebugUnitTest` green; team members screen shows the same leader/non-leader ordering, avatars, and admin entries; `grep -c "getUsersByIds\|getUserById("` inside the function (lines 978-1053) drops by exactly the re-query count.
size budget: ~6 changed lines, 1 file..
out of scope: no `UserDao` changes (no schema;; no behavior change to `getJoinedMembers`;; no pagination.,

---
### 5. Hoist shared-prefs user read out of `getTaskTeamInfo` conditional branches (roadmap 1+7

context: `TeamsRepositoryImpl.kt:382-398` `getTaskTeamInfo` resolves the calling user inside its body, and at base the only `getUserModel()` site in the file is line 772 (inside a log-message builder.) - so the per-nav re-read is small but real; `RequestsViewModel.kt:24` and `getRequestedMembers` already batch (lines 1064-1070,, so this task only trims the single-hot-path caller. Hoisting keeps leader/member visibility identical while removing a conditional re-read (e.g. a branch that reads the user model only when a task teamId exists.,

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (`getTaskTeamInfo` coordinate lines  382-398; grep first "fun getTaskTeamInfo" then read body- do NOT touch `TeamDao`, `DashboardViewModel`, or the task UI (PR #15825 area.,

steps:
1. Read `getTaskTeamInfo` body (locate via grep -n "fun getTaskTeamInfo".; identify each read of the current user (likely `userRepository.getUserModel()` once,, or `userSessionManager`.
2. Hoist it: add `val currentUser = userRepository.getUserModel()` at the very top of the function(`userRepository` is an existing constructor field - verify;; replace in-body occurrences with `currentUser` ( preserving `?.` nullability exactly; do not change the returned `Triple` values.,
3. If the body has zero user reads today, keep the task as a no-op verification instead: assert single read via grep -c and document that the fix is already in place( then run tests only. 
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; tapping a task chip in team screens navigates with identical team name/url; `grep -n "getUserModel()"` inside the function range shows <= 1 occurrence and that occurrence sits at the top.
size budget: ~3 changed lines, 1 file..
out of scope: no interface change; no caching; no behavior change to leader/member gating.,

---
### 6. Lock in single user-row read in dashboard profile load (roadmap 1+7+8

context: `UserRepositoryImpl.kt:86-97` `getDashboardProfile(userId)` already does the right thing: one `getUserById(userId)`, then `user?.name` / `user?.getFullName()` from the same row,and `getOfflineLoginCount(userName)` only when `userName != null` (line 94.). It is called once per dashboard open (`DashboardViewModel.kt:221`,and rendered by `BaseDashboardFragment.kt:129-132` as `%1$s (%2$s)`. This task is a verify-and-lock micro-optimization: assert (and keep) the body at one user query per call; if a stray second fetch exists today (duplicate `getUserById` or a full `getAll()`,, remove it.,

files: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` (`getDashboardProfile` lines  86-97;; do NOT touch `UserDao` (`getUserById` stays,, nor `DashboardViewModel` / `BaseDashboardFragment`.,

steps:
1. Open `getDashboardProfile` (lines 86-97). count user-query calls: expected exactly one `getUserById(userId)`.
2. If a second fetch exists (`getAll()`, a second `getUserById(`, another `getUserModel()`,, delete it and reuse the first row for both name lookup and offline-logins lookup.
3. Confirm `getOfflineLoginCount(userName)` runs only when `userName != null` (already the case- keep.
4. Run tests; logcat on dashboard open should show one `SELECT * FROM users WHERE id = ?` and no second query.
acceptance: `./gradlew testDefaultDebugUnitTest` green; dashboard header shows the same `%1$s (%2$s)` value; logcat shows one users query per dashboard load, no full scan.
size budget: ~0-2 changed lines, 1 file..
out of scope: no flow-ification; no caching; no offline-logins table changes.,

---

### 7. Drop the per-survey `exam_questions` precheck in `hasPendingSurvey` (roadmap 1+7+9

context: `SubmissionsRepositoryImpl.kt:373-386` `hasPendingSurvey` streams `getSurveysByCourseId(courseId)` and for each survey calls `hasSubmission(..)` lines 176-190,, which pairs a `questionDao.countByExamId` probe with a `submissionDao.countByUserParentAndType` count- 2 queries per survey along the dashboard challenge-dialog path (`DashboardViewModel.kt:330`. The `exam_questions` probe only short-circuits question-less exams and adds no pending-survey truth; replacing the in-loop `hasSubmission` with a direct parent-count halves the queries. `hasSubmission` itself (used by course-step gating, `CoursesRepositoryImpl` lines  536-541) keeps its pair; `hasUnfinishedSurveys` (same impl, lines   188-201) stays untouched.,

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (`hasPendingSurvey` lines  373-386;; `hasSubmission` lines  176-190 untouched;; do NOT touch `SubmissionsRepository.kt`, `DashboardViewModel`, or `TakeCourseViewModel`/`TakeCourseFragment`.,

steps:
1. Read `hasPendingSurvey` (lines 373-386)and confirm the per-survey `hasSubmission(..)` call inside its loop (line 382.
2. In the loop, build the parent id directly- `val parentId = "$survey.id@$courseId"` (the same format `hasSubmission` constructs at line   184.-and gate on `if (submissionDao.countByUserParentAndType(userId, parentId, "survey") == 0) return true`, preserving first-unanswered-early-return semantics for multi-survey courses.
3. Leave `hasSubmission` and `hasUnfinishedSurveys` byte-identical.
4. Run tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `TakeCourseFragment.kt:413` and the dashboard challenge dialog show "unfinished survey" exactly when any of the course's surveys is unanswered; `adb logcat` shows one `submissions` count per survey( zero `exam_questions` counts from this path.,
size budget: ~4 changed lines, 1 file..
out of scope: no `SubmissionDao` change; no `hasUnfinishedSurveys` change; no change to the exam/survey editor flow.,

---

### 8. Count submissions instead of hydrating full rows in `getUniquePendingSurveys` (roadmap 1+7+9

context: `SubmissionsRepositoryImpl.kt:99-119` `getUniquePendingSurveys` first hydrates (attaches answers` an entire candidate list, then does `examDao.getByIds` + per-exam dedupe in lines  108-117;the bell consumer `BellDashboardViewModel.kt:102` only needs the exam-id signal to dedupe survey prompts.;a `GROUP BY parentId` count query in `SubmissionDao` (one aggregate, no answers, no exam hydration) yields the dedupe that directly;unblocks KMP by dropping answer-load from the sync-adjacent verb. The flow variant `getPendingSurveysFlow` (lines  80-86) stays untouched.,

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (`getUniquePendingSurveys` lines  99-119;; `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt` (add `fun getPendingSurveyCountsByUser(..)`; do NOT touch `SubmissionsRepository.kt` or `BellDashboardViewModel`.,

steps:
1. In `SubmissionDao`, add the single aggregate query returning `List<ParentSubmissionCount>` (data class in the DAO file, Room maps the `count` column to the val;; use `WHERE userId = :userId AND type = 'survey' AND uploaded = 0 GROUP BY parentId`.
2. In the impl, after `val pendingCounts = submissionDao.getPendingSurveyCountsByUser(userId)`, build `val pendingCountById = pendingCounts.associate { it.parentId to it.count }` and hydrate ONLY the rows whose parent has a nonzero pending count- `rows.filter { pendingCountById[it.parentId] != null }` (verify `parentId` semantics inside `Submission` model: it is the parent submission/exam handle,- check at the model line 25 before trusting),
3. Keep the existing exam-title dedupe inline (only for the surviving rows.
4. Run tests; open surveys prompts.
acceptance: `./gradlew testDefaultDebugUnitTest` green; bell survey-prompt shows identical courses with the same pending badges; `adb logcat` shows zero `SELECT * FROM answers` rows for this path.,
size budget: ~15 changed lines, 2 files..
out of scope: no column migrations; no `SurveySync` coupling; no `SubmissionDao.observePendingSurveys` changes.,
---

### 9. Batch per-course step loads in the courses-list progress refresh (roadmap 1+7+8

context: `CoursesRepositoryImpl.kt:202` `getCourseSteps(courseId)` is called once per course row in the courses-list path (line 202; separate from the delete-batch at line  428 which is already chunked,, and from the task-1 target at line 139). Each call runs a separate indexed SELECT on `course_steps`;; the DAO already exposes `getByCourseIds` (`CourseStepDao.kt:11`, one `IN` query for all courses. Batching turns N queries into 1 for the most-visited screen while preserving step order per course.,

files: `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt` (line 202 inside whichever loader maps course rows; keep `getCourseDetailModel` lines  135-148 ( task 1 target) and `getCourseStepData` lines  525-540 untouched;; do NOT touch `CourseStepDao.kt`, `CoursesViewModel.kt`, or the courses list fragment.,

steps:
1. Read the function containing line  202 (locate via grep -n "getCourseSteps("; read body. Confirm it iterates courses and calls `getCourseSteps(courseId)` per row.
2. Hoist the batch: `val courseIds = <the rows being processed>.mapNotNull { it.courseId }.distinct()`; `val steps = if (courseIds.isNotEmpty() courseStepDao.getByCourseIds(courseIds) else emptyList()`; `val stepsByCourseId = steps.groupBy { it.courseId }`.
3. Replace the per-row call with `stepsByCourseId[courseId].orEmpty()` (preserving order via groupBy which keeps storage order.
4. Run tests..
acceptance: `./gradlew testDefaultDebugUnitTest` green; courses list/shelf render identical step counts per course; `grep -c "getCourseSteps("` in the impl shows the remaining two known sites (139: task-1 target; 428: delete-batch.
size budget: ~8 changed lines, 1 file..
out of scope: no `CourseStepDao` query changes; no behavior change to progress-update;; no touching task 1 files ( files disjoint.,

---
### 10. Index the open-tasks assignee scan(roadmap 1+7+8

context: `TeamTaskDao.kt:8-10` `getOpenTasksForUser(userId)` streams `WHERE assignee IS :userId` over all open tasks;the dashboard merge (`DashboardViewModel.kt:247`)and the teams task page both re-run this per open;the entity today carries only `Index("teamId")` (`TeamTask.kt:10`.. A composite index on `(assignee, completed, status)` turns the hot scan into a seek. This repo drops-and-resyncs Room on version bumps by design (`AppDatabase.kt` doc comment lines  85-90; `RoomModule.kt:60` `.fallbackToDestructiveMigration(true)`,, so no handwritten `Migration` object is needed- bump the version, add the entity index,and let the module recreate.,

files: `app/src/main/java/org/ole/planet/myplanet/model/TeamTask.kt` (line 10 `indices` list;; `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt` (line 130 `version =   12`, bump to 13;; `app/src/main/java/org/ole/planet/myplanet/di/RoomModule.kt` (line 60,, no edit needed- just confirm the fallback flag; do NOT touch `TeamTaskDao.kt`, `TeamsRepositoryImpl`, or `DashboardViewModel`.,

steps:
1. In `TeamTask.kt`, extendthe `indices` list with `Index(value = ["assignee", "completed", "status"], name = "index_assignee_completed_status")` (keep `Index("teamId")` intact.
2. In `AppDatabase.kt`, bump `version =   12` to `version =   13` (line 130. The fallback flag in `RoomModule.kt` (line 60, destructive by design) handles the recreation; do not add a `Migration` object.
3. Run tests + one install-over-existing-db smoke test (the app launches on an old data directory, table intact after drop-and-resync.
4. Verify the new index exists in a fresh install (`PRAGMA index_list` on `team_tasks` in the app db; expects),or at minimum no crash on upgrade.,

acceptance: `./gradlew testDefaultDebugUnitTest` green; existing installs open normally (they migrate destructively and re-pull, then team tasks appear identical;; `PRAGMA index_list('team_tasks')` lists `index_assignee_completed_status`.,

size budget: ~3 changed lines, 2 files..
out of scope: no DAO query changes; no backfill;; no dropping the old single-column index.,

---

## self-check

- 10 tasks, each with all 7 template sections, no file in more than one task, all paths verified against the working tree at `022ee7d`.
- Open PRs: already noted above (#16989, #16988, #16987, plus inspected #13415, #15825); none touch any file in these tasks.
- Each task independently mergeable in any order; combined blast radius ≤ 2 files each, ≤ ~150 lines each.

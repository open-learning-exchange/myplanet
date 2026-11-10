# myPlanet repository-boundary work orders

Date: 2026-11-10 (America/New_York) · Base commit: `3d02e24a44e36d8a08b26f83cfd60bb95df569b1`

Open PRs checked: #18555, #18554, #18455, #18539, #18551, #18540, #17811, #13657, #4075, #15820, #15226, #13355, #14883, #10993, #15108, #18076, #16594, #15267, #8175, #13848, #15951, #15825, #18045, #17832, #13287, #15266, #13604, #15559, #15808, #13928, #14650, #15824, #17694, #18029, #14427.

The currently open PRs, listed before the work orders:

| PR | Title |
| --- | --- |
| [#18555](https://github.com/open-learning-exchange/myplanet/pull/18555) | all: bump gradle-wrapper from 9.8.2 to 9.9.0 |
| [#18554](https://github.com/open-learning-exchange/myplanet/pull/18554) | all: smoother claude codebase inventory documenting |
| [#18455](https://github.com/open-learning-exchange/myplanet/pull/18455) | actions: smoother workflow release obfuscating (fixes #18451) |
| [#18539](https://github.com/open-learning-exchange/myplanet/pull/18539) | resources: smoother download error closing (fixes #18525) |
| [#18551](https://github.com/open-learning-exchange/myplanet/pull/18551) | resources: resolve local WebView content off main thread (fixes #18547) |
| [#18540](https://github.com/open-learning-exchange/myplanet/pull/18540) | resources: smoother download stopping (fixes #18538) |
| [#17811](https://github.com/open-learning-exchange/myplanet/pull/17811) | mySurvey: survey number update |
| [#13657](https://github.com/open-learning-exchange/myplanet/pull/13657) | course: Archive course My Courses library (fixes #13559) |
| [#4075](https://github.com/open-learning-exchange/myplanet/pull/4075) | robo movie (fixes #4074) |
| [#15820](https://github.com/open-learning-exchange/myplanet/pull/15820) | teams: smoother task and meetup comment threads managing (fixes #15112) |
| [#15226](https://github.com/open-learning-exchange/myplanet/pull/15226) | feat(flutter): Flutter/Dart port of myPlanet (phases 1–28) |
| [#13355](https://github.com/open-learning-exchange/myplanet/pull/13355) | Add P2P resource sharing (Wi‑Fi P2P) (fixes #13353) |
| [#14883](https://github.com/open-learning-exchange/myplanet/pull/14883) | team: add leaderboard tab (fixes #14880) |
| [#10993](https://github.com/open-learning-exchange/myplanet/pull/10993) | Voices video |
| [#15108](https://github.com/open-learning-exchange/myplanet/pull/15108) | fix event calendar marking (fixes #15107) |
| [#18076](https://github.com/open-learning-exchange/myplanet/pull/18076) | Ratings: fixed delayed rating updates (fixes #16958) |
| [#16594](https://github.com/open-learning-exchange/myplanet/pull/16594) | actions: smoother size labeller fetching (fixes #16344) |
| [#15267](https://github.com/open-learning-exchange/myplanet/pull/15267) | prevent download popup dialog cropping when text size is large (fixes #15263) |
| [#8175](https://github.com/open-learning-exchange/myplanet/pull/8175) | roboscript update (fixes #7986) |
| [#13848](https://github.com/open-learning-exchange/myplanet/pull/13848) | all: introduce Course/Grade models and wire into UI (fixes #13802) |
| [#15951](https://github.com/open-learning-exchange/myplanet/pull/15951) | teams: smoother repository update requesting (fixes #15568) |
| [#15825](https://github.com/open-learning-exchange/myplanet/pull/15825) | local event task reminders workmanager notifications (address #15115) |
| [#18045](https://github.com/open-learning-exchange/myplanet/pull/18045) | Kmp phase4 |
| [#17832](https://github.com/open-learning-exchange/myplanet/pull/17832) | Kmp phase2 |
| [#13287](https://github.com/open-learning-exchange/myplanet/pull/13287) | profile: no char limit for edit texts (fixes #13283) |
| [#15266](https://github.com/open-learning-exchange/myplanet/pull/15266) | prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265) |
| [#13604](https://github.com/open-learning-exchange/myplanet/pull/13604) | teams: Add sort by completeness option in Survey section (fixes #13590) |
| [#15559](https://github.com/open-learning-exchange/myplanet/pull/15559) | exam: redesign UI with elapsed timer and cards (fixes #15558) |
| [#15808](https://github.com/open-learning-exchange/myplanet/pull/15808) | sync: intelligent incremental sync via couchdb  changes feed (fixes #15807) |
| [#13928](https://github.com/open-learning-exchange/myplanet/pull/13928) | Add baseline profile module and installer (fixes #13927) |
| [#14650](https://github.com/open-learning-exchange/myplanet/pull/14650) | survey: smoother submissions display (fixes #14619) |
| [#15824](https://github.com/open-learning-exchange/myplanet/pull/15824) | gamification achievement hub offline badges streaks (address #15114) |
| [#17694](https://github.com/open-learning-exchange/myplanet/pull/17694) | Shared kmp module scaffold |
| [#18029](https://github.com/open-learning-exchange/myplanet/pull/18029) | Kmp phase3 |
| [#14427](https://github.com/open-learning-exchange/myplanet/pull/14427) | Course streak |

All paginated changed-file lists for these 35 PRs were checked. Every file claimed by any of them is excluded, regardless of PR age or label; this also excludes all recent review-ready/merge candidates. Evidence below refers to the pinned base, not the changing remote default branch. Before executing, refresh the open-PR exclusion check; a newly claimed file requires replanning that work order, not expanding its scope.

The tasks are ranked by expected user impact relative to their edit surface: cancellation and owner isolation first, repository result guarantees and bounded SQL next, then viewmodel loading and narrow consumer dependencies. Their edit lists are disjoint and their public contracts remain compatible, so each can merge directly onto the base in any order. Budgets count production and test additions/deletions together; cap each task at 149 changed lines and four files. No task adds dependencies or leaves unused helpers or placeholders.

Shared read-only foundation for tasks 7–10: `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5-8` already exposes `getUserModel`, `getUserById`, and `getUsersByIds`; `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt:229` already binds it to the existing implementation. Neither foundation file belongs to any task's edit list. These tasks remove access to account/security/upload mutations from unrelated features without adding interfaces, bindings, or implementations. Roadmaps 9 and 10 remain north stars, never standalone tasks or prerequisites.

All acceptance commands run from the repository root. They are requirements for the executing agents, not claims that implementation tests were run while preparing this plan.

---

### 1. Propagate cancellation through submission payload construction (roadmap 1+5+8)

context: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt:110-115` rethrows cancellation from a team lookup, but `submissionPayload` catches it again as a general exception at lines 162–164 and returns a partially assembled JSON object. That crosses the repository/upload boundary as apparent success and can let cancelled work continue with incomplete data.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt` — `SubmissionsPayloadBuilder.submissionPayload`.
- `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilderTest.kt` — `SubmissionsPayloadBuilderTest`.
- Leave neighboring `examUploadPayload`, `getTeamByIdOrNull`, and `resolveTeamJson` behavior and the repository caller unchanged.

steps:
1. Handle `CancellationException` before the existing general exception handler in `submissionPayload` and rethrow the same exception instance.
2. Add regression cases that cancel the exam DAO lookup, the question DAO lookup after a successful exam lookup, and the team repository lookup reached through payload construction.
3. Assert each public payload call throws rather than returning JSON; verify that later collaborators are not invoked after the cancellation point.
4. Keep ordinary exception handling and the existing successful JSON golden assertions intact; use the existing test fixture and coroutine-test dependency.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.SubmissionsPayloadBuilderTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify cancelling a submission upload during local payload preparation stops that operation; an ordinary successful upload still includes its answers, parent, user, and team fields.

size budget: approximately 65–95 changed lines, 2 files.

out of scope: Do not redesign upload retries, change payload keys, or edit upload managers or repository implementations owned by open PRs.
Portability: advances 9 by preserving Kotlin coroutine cancellation across the data boundary; introduces no platform API or Compose requirement.

---

### 2. Keep My Life reordering within the caller's owner scope (roadmap 1+8)

context: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:37-64` derives its reload/cache owner from the first caller-supplied item, then changes every matching persisted ID without checking its owner. A stale or mixed-account list can therefore reorder another owner's rows and select that owner's cache even though the method already receives the caller's user ID.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` — `LifeRepositoryImpl.updateMyLifeListOrder` and its use of `normalizeUserId`.
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryTest.kt` — `LifeRepositoryTest`, including its cache mock setup.
- Leave neighboring `updateVisibility`, dashboard loading, seeding, the DAO, and cache implementation unchanged.

steps:
1. Resolve the reload/cache scope from the method's `userId` using the existing normalization; do not let a supplied item's owner override it.
2. Before mutating a fetched row, reject a nonblank, non-placeholder persisted owner that differs from that scope. Preserve the existing legacy behavior for persisted null/blank/`--` owners, including fallback to the caller for cache writes.
3. Keep the existing input-index ordering, missing-ID handling, empty-input no-op, and update-only-changed-weights behavior.
4. Retain the cache mock as a test field; add mixed-owner and misleading-first-item cases proving other named owners' weights remain unchanged and no other owner's cache is written.
5. Add a legacy-owner regression so this isolation change does not break the supported unowned-row fallback.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.LifeRepositoryTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify dragging My Life items still persists the current user's order; replaying a list containing another named user's IDs leaves that user's order and cache untouched.

size budget: approximately 95–130 changed lines, 2 files.

out of scope: Do not change visibility aliases, migrate legacy owners, renumber filtered items, or modify the other repository test file claimed by an open PR.
Portability: advances 9 by keeping owner selection as repository-owned Kotlin policy rather than relying on UI filtering.

---

### 3. Honor the dictionary repository's typed failure result for preflight errors (roadmap 1+3+8)

context: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:36-60` returns a typed load result for read/parse/insert errors, but its file-existence and database-count checks sit outside that handler. A storage or database error during preflight therefore escapes to the viewmodel instead of producing the same failure result used for the remainder of this operation.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` — `DictionaryRepositoryImpl.insertDictionaryData`.
- `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt` — `DictionaryRepositoryImplTest`.
- Leave neighboring `count`, `findByWord`, the mapper, file-reader abstraction, and viewmodel unchanged.

steps:
1. Extend the existing error boundary to include file-existence checking, mutex acquisition, and the count check, all within the existing IO dispatcher context.
2. Preserve the current ordering of the file check before the population check, and keep the count/check/insert sequence protected by the same mutex.
3. Continue rethrowing cancellation before mapping ordinary exceptions to `DictionaryLoad.Failed`; retain the original cause.
4. Add preflight exception and cancellation cases for the file-existence and DAO-count checks, verifying no insert follows either failure.
5. Reuse the existing concurrent-seeding regression to prove successful concurrent calls still insert only once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify an unavailable dictionary storage source produces the existing failed load state instead of leaving loading without a result; a valid dictionary still populates and searches normally.

size budget: approximately 80–115 changed lines, 2 files.

out of scope: Do not change dictionary formats, result types, lookup behavior, or UI messages.
Portability: advances 9 and supports 10 by enforcing a platform-neutral result contract that a viewmodel can consume without storage-specific recovery logic.

---

### 4. Bound stale course-step deletion parameters (roadmap 1+7+8)

context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt:12-14` already chunks bulk reads, but `deleteStaleSteps` passes the entire retained-ID list into one `NOT IN` statement at lines 18–22. Large synchronized courses bypass the DAO's existing parameter-budget discipline, and naively chunking the retained set would delete valid steps belonging to other chunks.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt` — `CourseStepDao.deleteStaleSteps` and `deleteStaleInternal`.
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseStepDaoTest.kt` — `CourseStepDaoTest` and its in-memory database setup.
- Leave neighboring `getByCourseIds`, `getById`, upserts, and repository sync orchestration unchanged.

steps:
1. Make the public cleanup operation transactional and preserve its signature, small-input path, and empty-retained-set behavior.
2. For more than 900 distinct retained IDs, read only persisted step IDs for the selected course through a DAO-local projection query and subtract the complete retained set in Kotlin.
3. Delete only the resulting stale IDs in positive `IN` batches of at most 900, retaining a course predicate. Add only helpers used by this branch; never chunk `NOT IN` keep sets.
4. Extend the real Room tests with more than 900 retained IDs, more than 900 stale IDs, duplicate input IDs, and a second course whose rows must survive.
5. Record delete-statement bind counts through the test database's query callback and assert no deletion exceeds 901 parameters, including the course parameter.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.data.room.dao.CourseStepDaoTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify syncing a large course retains all current steps, removes obsolete steps, and leaves unrelated courses available offline.

size budget: approximately 105–140 changed lines, 2 files.

out of scope: Do not add tables, indexes, migrations, temporary storage, or change repository APIs.
Portability: supports 9 by containing SQLite batching in its data-source boundary; the repository's course contract remains unchanged.

---

### 5. Bound stale exam-question deletion parameters (roadmap 1+7+8)

context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:27-32` sends every retained question ID for an exam into one `NOT IN` query. Its bulk-read methods already bound parameter lists at lines 12–23, so cleanup is an inconsistent large-exam failure point in the same DAO boundary.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt` — `QuestionDao.deleteStaleQuestions` and `deleteStaleInternal`.
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/QuestionDaoTest.kt` — `QuestionDaoTest` and its in-memory database setup.
- Leave neighboring `getByExamIds`, `getByIds`, question counts, and both course/survey repository callers unchanged.

steps:
1. Preserve the public signature and grouping by non-null exam ID; empty input and null-exam groups must remain no-ops.
2. Make cleanup transactional. Keep the existing small-group query for at most 900 distinct retained IDs.
3. For larger groups, fetch persisted IDs for that exam using a DAO-local ID-only query, subtract the whole retained set, and delete stale IDs with exam-scoped positive `IN` batches of at most 900.
4. Add real Room cases covering a large keep set, multiple stale batches, repeated retained IDs, two represented exams, and an unrepresented exam that must remain untouched.
5. Capture delete bind counts in the test fixture and assert a maximum of 901 parameters per statement; verify retained questions across all batches survive.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.data.room.dao.QuestionDaoTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify syncing a large exam or survey keeps its current questions, removes stale ones, and leaves exams absent from the incoming batch unchanged.

size budget: approximately 110–145 changed lines, 2 files.

out of scope: Do not redesign empty-exam deletion semantics, change question serialization, or share new helpers with another task.
Portability: supports 9 by keeping parameter-limit handling below repositories without introducing Android APIs into repository logic.

---

### 6. Key member-stat loading by the requested member (roadmap 3+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersDetailViewModel.kt:35-50` uses one lifetime Boolean to guard all repository loads and sets it before the load succeeds. A failed request blocks retries, and a later request for a different member is ignored, weakening the relationship between the displayed state and its repository input.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersDetailViewModel.kt` — `MembersDetailViewModel.loadMemberVisitStats` and its load guard.
- `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/MembersDetailViewModelTest.kt` — `MembersDetailViewModelTest`.
- Leave the date format, existing state fields, fragment rendering, and repository statistics calculation unchanged.

steps:
1. Replace the lifetime Boolean with a request key containing both member ID and username, plus tracked in-flight work; retain the existing guard for both identifiers missing.
2. Suppress duplicate active or successfully completed requests for the same key, preserving the existing view-recreation behavior.
3. On a different valid key, clear the previous displayed stats and cancel superseded work. Check the active key before publishing so an older response cannot overwrite the new member.
4. Mark a key complete only after success; rethrow cancellation and handle an ordinary repository failure by keeping stats null and making the key retryable. Guard cleanup so an old job cannot clear a newer request.
5. Add regressions for same-key deduplication, failure then retry, switching members, and a delayed old response; reuse the existing coroutine scheduler.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.teams.members.MembersDetailViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify member details show that member's visit statistics, survive view recreation without a duplicate successful query, and can load again after a transient repository failure.

size budget: approximately 110–145 changed lines, 2 files.

out of scope: Do not add an error UI, refresh timer, new repository method, or change visit-date presentation.
Portability: supports 10 through viewmodel-owned request identity and state; adds no Android view or resource access to presentation logic.

---

### 7. Limit calendar and community identity access to the read interface (roadmap 1+3+4)

context: `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt:24,42` and `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt:24,33` inject the full user repository solely to read the current user model. This exposes unrelated account mutation and upload operations to two read-only feature consumers even though the existing narrow interface and binding already cover their calls.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt` — `CalendarViewModel` constructor and user-repository import.
- `app/src/test/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModelTest.kt` — `CalendarViewModelTest` user mock.
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt` — `CommunityTabViewModel` constructor and user-repository import.
- `app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModelTest.kt` — `CommunityTabViewModelTest` user mock.
- Leave meetup grouping/team queries and community configuration mapping unchanged.

steps:
1. Change each production user dependency and import from the broad repository to `UserReadRepository`; retain the parameter name and order.
2. Change both listed test fixtures to mock only `UserReadRepository`, proving the consumers compile without account-write capabilities.
3. Keep the existing `getUserModel` calls, guest handling, state emissions, and all non-user dependencies intact; clean only imports affected by this substitution.
4. Run the focused tests and full suite, allowing the existing Hilt binding to satisfy both constructors without DI edits.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.calendar.CalendarViewModelTest' --tests 'org.ole.planet.myplanet.ui.community.CommunityTabViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify calendar meetups/team names and community name/planet information still render for a signed-in learner; guest behavior remains covered by the existing tests.

size budget: approximately 12–20 changed lines, 4 files.

out of scope: No new interfaces, bindings, query logic, or calendar refresh changes; leave the separate calendar grouping test file untouched.
Portability: supports 9/10 by restricting presentation dependencies to the existing read contract; this does not claim the Android viewmodels themselves are multiplatform.

---

### 8. Remove account-write access from course-taking viewmodels (roadmap 1+3+4)

context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:49,68` and `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:42,117` use the broad user repository only for `getUserModel`. Course enrollment, progress, ratings, and resource operations already go through their own collaborators, so account-management capabilities unnecessarily cross the course feature boundary.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` — `CoursesStepsViewModel` constructor and user import.
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` — `CoursesStepsViewModelTest` user mock.
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` — `TakeCourseViewModel` constructor and user import.
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt` — `TakeCourseViewModelTest` user mock.
- Leave `loadStep`, `saveCourseProgress`, `loadCourse`, and rating/enrollment behavior unchanged.

steps:
1. Type both production user dependencies as `UserReadRepository`, changing their imports while retaining constructor order and names.
2. Restrict the listed test fixtures to that interface and retain the existing user-model stubs.
3. Keep all course/progress/resource collaborators and call ordering intact; do not move account or course logic between repositories.
4. Run both existing focused test classes, including step-change cancellation and exam-gating cases, followed by the full suite.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest' --tests 'org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify opening a course and its steps still shows the learner's enrollment/progress, enforces exam gates, and starts existing resource downloads as before.

size budget: approximately 12–20 changed lines, 4 files.

out of scope: No gating, download, coroutine, or course-repository changes; do not expand the read interface.
Portability: supports 9/10 by keeping user lookup behind a narrow presentation dependency without adding platform calls or UI resource access.

---

### 9. Restrict dashboard helpers to read-only user access (roadmap 1+3+4)

context: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt:22,39` needs the current user's name, while `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt:13,18-19` simply returns the current user. Both receive the full user repository despite routing activity writes and logout through separate collaborators, leaving a broader account-data boundary than these helpers need.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt` — `ActivitiesViewModel` constructor and user import.
- `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModelTest.kt` — `ActivitiesViewModelTest` user mock.
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt` — `DashboardElementViewModel` constructor and user import.
- `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModelTest.kt` — `DashboardElementViewModelTest` user mock.
- Leave `computeMonthlyCounts`, challenge recording, logout delegation, and other dashboard screens unchanged.

steps:
1. Replace both production user dependency types/imports with `UserReadRepository`, preserving their constructor positions.
2. Update both test user mocks to the narrow interface; keep activity and session collaborators unchanged.
3. Retain existing state-sharing policies, user lookup timing, empty-user behavior, monthly grouping, and logout routing.
4. Run the two focused suites and the full test command; use the existing binding without any DI-file modification.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.dashboard.ActivitiesViewModelTest' --tests 'org.ole.planet.myplanet.ui.dashboard.DashboardElementViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify monthly login counts still belong to the current learner, challenge recording still works, and dashboard logout still completes.

size budget: approximately 12–20 changed lines, 4 files.

out of scope: Do not edit the main/bell dashboard viewmodels claimed by open PRs, aggregate queries, or session management.
Portability: supports 9/10 by removing account-mutation capabilities from reusable dashboard presentation dependencies.

---

### 10. Narrow user access in personals and submission details (roadmap 1+3+4)

context: `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt:31,35-36` only reads the current user before delegating resource queries, and `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:30,38` only resolves a submission author's ID. Injecting the full account repository leaks unrelated security/profile/upload capabilities across both feature boundaries; the existing read interface covers these exact calls.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt` — `PersonalsViewModel` constructor and user import.
- `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModelTest.kt` — `PersonalsViewModelTest` user mock.
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt` — `SubmissionDetailViewModel` constructor and user import.
- `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt` — `SubmissionDetailViewModelTest` user mock.
- Leave personal uploads/edits/deletes and submission-detail formatting/state sharing unchanged.

steps:
1. Type both production user dependencies as `UserReadRepository` and update their imports; retain constructor order and parameter names.
2. Restrict both listed test fixtures to the narrow user interface. In the existing Mockito-based submission fixture, change only its user mock's class token/type; do not add Mockito usage or convert frameworks in this task.
3. Preserve the current ID sources: active-user identity for personals and the submission's own user ID for its author; keep existing null handling.
4. Run both focused suites and the full suite, retaining the existing author-mapping, absent-submission, and personal-upload assertions.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.personals.PersonalsViewModelTest' --tests 'org.ole.planet.myplanet.ui.submissions.SubmissionDetailViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Verify personals still list the current learner's resources and submission details still show the actual author's name, including when the viewer is a different learner.

size budget: approximately 12–24 changed lines, 4 files.

out of scope: No repository implementations, payload changes, test-framework migration, new DI binding, or expansion of the read interface.
Portability: supports 9/10 by expressing both presentation dependencies through existing narrow reads with no added platform coupling.

Signed: Codex · GPT-6 · high effort.

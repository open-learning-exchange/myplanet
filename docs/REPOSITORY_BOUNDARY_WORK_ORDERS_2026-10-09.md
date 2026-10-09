# myPlanet repository-boundary work orders

Date: 2026-10-09 (America/New_York) · Base commit: f18c50d25010913a0f85ba9d744705349f13b051
Open PRs checked: #18050, #18076, #13415, #17811, #18045, #18029, #17832, #17694, #15226, #10993, #15951, #16594, #15808, #15825, #15824, #16623, #8175, #4075, #15267, #14883, #14650, #15820, #15559, #15266, #15108, #14427, #13928, #13848, #13657, #13604, #13355, #13287

The following PRs were open when checked; every changed-file page was inspected, including older, draft, on-hold, and experimental work. All their touched paths are excluded from the editable file lists below. The recent window is October 2–9, 2026; the stronger all-open exclusion also covers its ready/review/merge work.

| Open PR | Title | Last updated (UTC date) | Labels |
| --- | --- | --- | --- |
| #18050 | sync: smoother repository shelf logging (fixes #18041) | 2026-10-08 | small, ready |
| #18076 | Ratings: fixed delayed rating updates (fixes #16958) | 2026-10-08 | small, on hold |
| #13415 | voices: Add emoji reactions (fixes #13357) | 2026-10-08 | priority, change, ⭐⭐⭐., enormous |
| #17811 | mySurvey: survey number update | 2026-10-08 | small, on hold |
| #18045 | Kmp phase4 | 2026-10-06 | experiment, enormous |
| #18029 | Kmp phase3 | 2026-10-06 | experiment, enormous |
| #17832 | Kmp phase2 | 2026-10-06 | experiment, enormous |
| #17694 | Shared kmp module scaffold | 2026-10-06 | experiment, enormous |
| #15226 | feat(flutter): Flutter/Dart port of myPlanet (phases 1–28) | 2026-09-24 | brainstorm, experiment, enormous |
| #10993 | Voices video | 2026-09-15 | on hold, enormous |
| #15951 | teams: smoother repository update requesting (fixes #15568) | 2026-09-10 | brainstorm, enormous |
| #16594 | actions: smoother size labeller fetching (fixes #16344) | 2026-09-08 | brainstorm, ⭐⭐⭐., enormous |
| #15808 | sync: intelligent incremental sync via couchdb  changes feed (fixes #15807) | 2026-09-08 | WIP, experiment, enormous |
| #15825 | local event task reminders workmanager notifications (address #15115) | 2026-09-07 | brainstorm, enormous |
| #15824 | gamification achievement hub offline badges streaks (address #15114) | 2026-09-04 | brainstorm, ⭐⭐⭐. |
| #16623 | teams: smoother interactive team tasks status and board handling (fixes #16616) | 2026-09-01 | brainstorm, enormous |
| #8175 | roboscript update (fixes #7986) | 2026-08-29 | experiment |
| #4075 | robo movie (fixes #4074) | 2026-08-29 | experiment |
| #15267 | prevent download popup dialog cropping when text size is large (fixes #15263) | 2026-08-24 | brainstorm, ⭐⭐⭐. |
| #14883 | team: add leaderboard tab (fixes #14880) | 2026-08-24 | brainstorm |
| #14650 | survey: smoother submissions display (fixes #14619) | 2026-08-24 | don't merge, ⭐⭐⭐` |
| #15820 | teams: smoother task and meetup comment threads managing (fixes #15112) | 2026-08-24 | brainstorm |
| #15559 | exam: redesign UI with elapsed timer and cards (fixes #15558) | 2026-08-24 | brainstorm |
| #15266 | prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265) | 2026-08-24 | on hold |
| #15108 | fix event calendar marking (fixes #15107) | 2026-08-24 | on hold |
| #14427 | Course streak | 2026-08-24 | WIP |
| #13928 | Add baseline profile module and installer (fixes #13927) | 2026-08-24 | brainstorm, experiment |
| #13848 | all: introduce Course/Grade models and wire into UI (fixes #13802) | 2026-08-24 | on hold |
| #13657 | course: Archive course My Courses library (fixes #13559) | 2026-08-24 | brainstorm |
| #13604 | teams: Add sort by completeness option in Survey section (fixes #13590) | 2026-08-24 | brainstorm |
| #13355 | Add P2P resource sharing (Wi‑Fi P2P) (fixes #13353) | 2026-08-24 | brainstorm, experiment, on hold |
| #13287 | profile: no char limit for edit texts (fixes #13283) | 2026-08-24 | brainstorm, on hold, ⭐⭐⭐. |

Code search preceded the PR check. Tasks are ranked by observed user impact divided by blast radius: prevent collateral edits and wrong export data, protect bulk persistence, move lookup semantics behind a repository, then remove excess consumer capabilities. Each task starts from the base commit, owns only its listed editable files, and can merge without any other task. Paths in leave-alone lists are read-only neighbors, never implicit edit permission.

Shared frozen seams, confirmed in this checkout: `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5` exposes `getUserModel`, `getUserById`, and `getUsersByIds`; `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:23` extends it. `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt:229` already binds the read interface, and line 213 binds the existing team-sharing interface. Do not change these seams or DI bindings in any task.

For every task: no new dependencies, schema/version changes, unused helpers, implementation placeholders, or edits outside its editable list. Budget counts additions plus deletions, including tests. Run acceptance commands from the repository root; the commands below are execution requirements for the coding agents, not claims that implementations or tests were run during this planning-only round. Roadmaps 9 and 10 remain north stars and are never scheduled or prerequisites.

---
### 1. Resolve My Life visibility targets to a single persisted row (roadmap 1+8)

context: MyLifeDao.kt:33 matches an identifier against the primary key, image name, or title in one UPDATE, without an owner constraint. LifeFragment.kt:52–54 can send an image or title fallback, so shared labels can change multiple users' rows; even a valid primary key can collide with another row's label.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt — MyLifeDao.updateVisibility.
- app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt — MyLifeDaoTest and its visibility regressions.

Leave alone app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt (LifeRepositoryImpl.updateVisibility), app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt (LifeFragment.initAdapter), and app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt (LifeAdapter.updateVisibility). Preserve the existing two-argument DAO entry point so frozen repository mocks remain valid.

steps:
1. Keep updateVisibility's parameter types and Unit return; make its body a Room transaction that resolves exactly one persisted primary key before updating.
2. Prefer an exact primary-key match. Only when none exists, select at most two primary keys matching imageId or title; update a unique match and make zero or multiple matches a no-op.
3. Use an exact-primary-key UPDATE after resolution, with every new query helper called by that transaction. Do not retain the unrestricted OR update.
4. Extend the existing DAO tests for exact-ID precedence over a colliding label, shared aliases across two users, a unique legacy alias, and an unknown identifier; retain the Flow visibility test.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Hide/show still changes the selected persisted My Life tile and survives reopening; a shared legacy alias changes neither user's rows instead of changing both.

size budget: ~115 changed lines, 2 editable files; hard ceiling 145 lines.

out of scope: Do not redesign legacy caching, normalize owners, change reordering, or add a migration. This prevents ambiguous multi-row writes; it does not claim complete authorization of every caller-supplied identifier.

---

### 2. Keep each exported submission paired with its own exam questions (roadmap 1+7+8)

context: SubmissionsRepositoryExporter.kt:184–185 loads questions from the first submission's parentId, then lines 187–220 reuse them for every submission. A mixed-exam export therefore presents another exam's questions against a submission's answers, crossing an exam-data boundary even though answer loading is correctly grouped.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt — SubmissionsRepositoryExporter.generateMultipleSubmissionsPdf and getExamId.
- app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt — SubmissionsRepositoryExporterTest.

Leave alone app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt (QuestionDao.getByExamIds, lines 21–23) and app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt (SubmissionsPayloadBuilder). The existing batch query already chunks its input.

steps:
1. Extract a used internal suspend helper in the exporter file that accepts loaded submissions and returns questions grouped by each parsed exam ID; keep data selection separate from Canvas/PdfDocument operations.
2. Collect distinct, nonblank exam IDs using getExamId and call getByExamIds once when that collection is nonempty; group returned questions by examId.
3. Inside the existing submission-rendering loop, select only that submission's question group. Use an empty list for absent/blank parents or exams with no questions, retaining question order within each group and the existing answer lookup.
4. Test the helper without constructing a PDF: two submissions from different exams, repeated submissions from one exam, and absent parents. Verify one batch call, no per-exam calls, and no query for an all-empty input.
5. Retain existing timestamp tests and manually inspect the resulting mixed-exam PDF.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SubmissionsRepositoryExporterTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Exporting submissions from two exams shows each submission's own questions and answers; a same-exam export retains its labels, answer text, and ordering.

size budget: ~125 changed lines, 2 editable files; hard ceiling 145 lines.

out of scope: Do not change PDF pagination, filenames, question ordering rules, or upload payloads. The data helper avoids new Android types in its inputs/results, a small step toward 9; Android PDF rendering remains unchanged.

---

### 3. Bound team list reads and deletions inside the DAO (roadmap 1+5+7+8)

context: TeamDao.kt:13 and :35 expose single unbounded IN queries through getByIds and deleteByIds. Repository callers protect some reads with chunking, but bulk deletion delegates directly; owning the bind limit in the DAO makes large sync removals safe without requiring every caller to remember it.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt — TeamDao.getByIds and deleteByIds.
- app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt — TeamDaoTest.

Leave alone app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt (TeamsRepositoryImpl, including deleteLocalTeamRecords and existing chunked reads). Keep both public DAO signatures unchanged.

steps:
1. Move each existing SQL statement behind a used annotated query helper, retaining getByIds and deleteByIds as default method wrappers.
2. Short-circuit empty input; deduplicate IDs and execute chunks of at most 900 IDs. Flatten read results and sum deletion row counts.
3. Put the complete delete wrapper under Room Transaction so a bulk deletion remains atomic across chunks; retain read semantics without inventing an ordering guarantee.
4. Add in-memory Room cases using at least 1,201 IDs, duplicates, nonexistent IDs, and one unrelated row. Assert complete read membership, exact deletion count, and retention of the unrelated row.
5. Add empty-input assertions and preserve all existing finance-report tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.TeamDaoTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- A sync removing more than 1,200 team documents completes without a bind-limit failure or partial deletion; surviving teams remain available.

size budget: ~105 changed lines, 2 editable files; hard ceiling 145 lines.

out of scope: Do not remove caller-side chunking, change membership counts, add indexes, or alter sync protocols. Transaction and bind-budget handling remain persistence details, supporting eventual separation under 9 without introducing Android imports into a new core API.

---

### 4. Move dictionary lookup normalization into its repository (roadmap 1+3+8)

context: DictionaryViewModel.kt:58 trims words before lookup, while DictionaryRepositoryImpl.kt:25–26 forwards words directly to the DAO. The screen supplies a data-lookup rule that another repository caller would miss, so padded words have caller-dependent results.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt — DictionaryRepositoryImpl.findByWord.
- app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt — DictionaryViewModel.searchWord.
- app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt — DictionaryRepositoryImplTest.
- app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt — DictionaryViewModelTest.

Leave alone app/src/main/java/org/ole/planet/myplanet/data/room/dao/DictionaryDao.kt (DictionaryDao.findByWord, line 17) and app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt (DictionaryRepository, DictionaryWord, DictionaryLoad).

steps:
1. Trim input in repository findByWord and return null without querying for an empty normalized word. Retain the existing entity-to-DictionaryWord mapping.
2. Keep the ViewModel's blank-input no-op with an isBlank guard, then pass the original nonblank word to the repository; remove the ViewModel's normalization variable.
3. Extend repository tests for padded words, whitespace-only input with zero DAO calls, and unchanged field mapping. Preserve DAO case-insensitive matching rather than adding another case conversion.
4. Update the ViewModel's whitespace test to stub and verify raw-word delegation while retaining Found, NotFound, and blank-input state assertions.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest" --tests "org.ole.planet.myplanet.ui.dictionary.DictionaryViewModelTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Searching a padded word still finds its entry, and submitting blank input leaves the prior search state untouched.

size budget: ~65 changed lines, 4 editable files; hard ceiling 145 lines.

out of scope: Do not change dictionary seeding, file loading, or search scheduling. Lookup rules become plain Kotlin repository logic, advancing 9; Found/NotFound state stays hoisted in the ViewModel for future portable screens under 10.

---

### 5. Make bulk user lookups own their bind limits (roadmap 1+7+8)

context: UserDao.kt:13–14 places the same ID list in two IN predicates without bounding it, and :45–46 similarly exposes an unbounded guest-name query. Existing repository callers chunk inputs, so this is a persistence-boundary gap rather than proof of a current screen crash; DAO guarantees should hold independently of caller knowledge.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt — UserDao.getUsersByAnyIds and getGuestUsersByNames.
- app/src/test/java/org/ole/planet/myplanet/data/room/dao/UserDaoTest.kt — UserDaoTest.

Leave alone app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt (UserRepositoryImpl.getUsersByIds and existing guest-name batching). Preserve public method signatures and guest matching semantics.

steps:
1. Place both current SELECT statements behind used query helpers and turn the existing public methods into empty-safe wrappers.
2. Deduplicate requested IDs and chunk them at 450: each ID appears twice in the SQL, so each query binds at most 900 values.
3. Deduplicate requested names and chunk them at 900; retain case-sensitive name matching and the existing guest_ remote-ID predicate.
4. Deduplicate the combined user-ID result by local id so a user's local and remote IDs split across different chunks return one entity. Do not collapse distinct users sharing a name.
5. Extend Room tests with at least 1,201 requested IDs/names, both identifiers for one user across chunk boundaries, duplicate input, unknown values, and empty input.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.UserDaoTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Large team-member and guest-user lookups return the same matched users without duplicate entities or bind-limit failures; ordinary user search and login remain unchanged.

size budget: ~120 changed lines, 2 editable files; hard ceiling 145 lines.

out of scope: Do not modify repository batching, name normalization, duplicate-user cleanup, or login identity rules. Bind accounting stays behind persistence boundaries, supporting 9 without expanding platform coupling.

---

### 6. Limit membership ViewModel user access to reads (roadmap 1+3+4+8)

context: RequestsViewModel.kt:52 injects the broad user repository, but its user access at :69, :87, :101, and :135 only reads the current user. The membership feature unnecessarily receives profile, credentials, and sync mutation capabilities; its existing team-members interface already shows the intended boundary.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt — RequestsViewModel constructor; loadJoinedMembers, leaveTeam, removeMember, fetchMembers retain their behavior.
- app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelTest.kt — RequestsViewModelTest user mock declaration/import.
- app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelLeaderHandOffTest.kt — RequestsViewModelLeaderHandOffTest user mock declaration/import.

Leave alone app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt (RequestsFragment) and app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt (MembersFragment), plus the shared frozen seams.

steps:
1. Change only the user constructor dependency/import to the existing UserReadRepository; retain the TeamsMembersRepository dependency.
2. Type the user mocks in both existing test classes as UserReadRepository so these tests cannot compile against unrelated user-write methods.
3. Retain current count/limit methods, optimistic request handling, leader handoff, and null-user behavior; clean only imports affected by the type change.
4. Run both focused suites and the full unit suite; verify membership actions on a team with pending requests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.teams.members.RequestsViewModelTest" --tests "org.ole.planet.myplanet.ui.teams.members.RequestsViewModelLeaderHandOffTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Requests show the same members and capacity; accept/reject, remove, and leader handoff behave as before.

size budget: ~14 changed lines, 3 editable files; hard ceiling 145 lines.

out of scope: Do not change leadership rules, repository implementations, or DI bindings. Depending on the existing Android-import-free read interface supports 9; existing StateFlow ownership remains ready for future portable screens under 10.

---

### 7. Align chat tests and production with narrow repository capabilities (roadmap 1+3+4+8)

context: ChatViewModel.kt:50 injects the broad user repository although loadCurrentUser and getUserById only call getUserById (:182 and :288). Its team/voice dependencies are already narrowed (:51–52), but ChatViewModelTest.kt:45–47 still supplies broad mocks, hiding accidental capability expansion in the feature.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt — ChatViewModel constructor/import; loadCurrentUser and getUserById keep their delegations.
- app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt — ChatViewModelTest repository mock declarations/imports.

Leave alone app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt (ChatConversationPaginator), app/src/main/java/org/ole/planet/myplanet/repository/TeamsShareRepository.kt (TeamsShareRepository), and app/src/main/java/org/ole/planet/myplanet/repository/VoicesShareRepository.kt (VoicesShareRepository).

steps:
1. Replace the production user dependency with UserReadRepository, keeping its parameter name and all public methods unchanged.
2. In the test fixture, narrow user, team, and voice mocks to UserReadRepository, TeamsShareRepository, and VoicesShareRepository respectively, matching the actual constructor.
3. Retain all current sharing, missing-user, pagination, and request-result stubs/assertions; remove only obsolete broad-interface imports.
4. Run the chat suite and full unit suite, then verify share targets and chat requests with the same signed-in user.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.ChatViewModelTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Chat still resolves the current user, offers the same team/enterprise/community targets, and sends or continues a conversation.

size budget: ~18 changed lines, 2 editable files; hard ceiling 145 lines.

out of scope: Do not redesign sharing, move search/pagination, or change repository methods/bindings. Existing narrow interfaces reduce feature coupling on the path to 9; screen state remains in the ViewModel for 10.

---

### 8. Remove user-write access from resource creation and details (roadmap 1+3+4+8)

context: AddResourceViewModel.kt:18 depends on the broad user repository but only currentUser (:21) reads it. ResourceDetailViewModel.kt:15 has the same excess dependency for getUserModel (:20); resource and rating mutations already belong to their separate repositories.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModel.kt — AddResourceViewModel constructor/import and currentUser.
- app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModel.kt — ResourceDetailViewModel constructor/import and getUserModel.
- app/src/test/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModelTest.kt — AddResourceViewModelTest user mock type/import.
- app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModelTest.kt — ResourceDetailViewModelTest user mock type/import.

Leave alone app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceFragment.kt (AddResourceFragment) and app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt (ResourcesRepositoryImpl), plus the shared frozen seams.

steps:
1. Use UserReadRepository for both user constructor dependencies without renaming parameters or changing public return types.
2. Narrow the corresponding user mocks/imports in both existing tests; keep resource, personal, and rating mocks on their current interfaces.
3. Retain duplicate-title checks, save outcomes, library resolution, library add/remove, and rating-summary delegation exactly as written.
4. Run both focused suites and full unit tests, then check adding a personal resource and opening its details.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.resources.AddResourceViewModelTest" --tests "org.ole.planet.myplanet.ui.resources.ResourceDetailViewModelTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- The same user is attached to new personal resources; duplicate titles are still rejected and details retain library actions and ratings.

size budget: ~16 changed lines, 4 editable files; hard ceiling 145 lines.

out of scope: Do not move title checks, change save transactions, or fix rating refresh. Read-only user seams support 9; existing ViewModel state/delegation avoids embedding data access in future composables under 10.

---

### 9. Narrow user reads in both course-progress ViewModels (roadmap 1+3+4+8)

context: ProgressViewModel.kt:18 and CourseProgressViewModel.kt:17 inject the broad user repository solely for getUserModel. Their identity choices differ deliberately in current code: progress rows use local id (:27), while course progress uses remote _id (:27); reducing the dependency must preserve that distinction.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt — ProgressViewModel constructor/import and loadCourseData.
- app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt — CourseProgressViewModel constructor/import and loadProgress.
- app/src/test/java/org/ole/planet/myplanet/ui/courses/ProgressViewModelTest.kt — ProgressViewModelTest user mock type/import.
- app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModelTest.kt — CourseProgressViewModelTest user mock type/import.

Leave alone app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressActivity.kt (CourseProgressActivity) and app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt (CoursesProgressFragment), plus the shared frozen seams.

steps:
1. Replace both broad user dependency types/imports with UserReadRepository, keeping parameter names.
2. Change user mock types/imports in both existing test classes to the narrow interface.
3. Preserve local-id versus remote-_id forwarding, nullable-user handling, current StateFlow values, and the existing loadProgress cache guard.
4. Run both focused test classes and the full suite, then compare progress list and course details for a user whose local and remote IDs differ.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.ProgressViewModelTest" --tests "org.ole.planet.myplanet.ui.courses.CourseProgressViewModelTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Both progress views show the same learner's existing results; repeating the cached detail request retains its current behavior.

size budget: ~16 changed lines, 4 editable files; hard ceiling 145 lines.

out of scope: Do not unify user identifiers, fix progress caching, or alter course/progress repository contracts. The plain read seam advances 9; hoisted progress state remains available for future portable screens under 10.

---

### 10. Give feedback composition only read access to user data (roadmap 1+3+4+8)

context: FeedbackComposerViewModel.kt:20 injects the broad user repository, although submitFeedback only obtains the current user's name (:38). Feedback creation already goes through its own repository (:39), so broad user mutation/sync access leaks an unrelated feature surface into composition.

files: Edit only these existing files:
- app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModel.kt — FeedbackComposerViewModel constructor/import and submitFeedback.
- app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModelTest.kt — FeedbackComposerViewModelTest user mock type/import.

Leave alone app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackFragment.kt (FeedbackFragment) and app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt (FeedbackRepositoryImpl), plus the shared frozen seams.

steps:
1. Switch the constructor's user dependency/import to UserReadRepository; preserve the current getUserModel call and empty-name fallback.
2. Narrow the existing test's user mock to UserReadRepository so the fixture enforces the feature boundary at compile time.
3. Retain feedback arguments, isSubmitting transitions, Saved/Error events, and existing tests; clean only affected imports.
4. Run the focused and full test suites, then submit feedback as a signed-in user and verify the existing error flow.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.feedback.FeedbackComposerViewModelTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Feedback retains the current sender and payload, successful submission closes normally, and a repository failure still clears submitting state and displays the existing error.

size budget: ~8 changed lines, 2 editable files; hard ceiling 145 lines.

out of scope: Do not change error/cancellation semantics, add a use-case abstraction, or alter DI bindings. Existing Android-import-free user-read contracts support 9, while submission state/events remain hoisted for a later portable UI under 10.

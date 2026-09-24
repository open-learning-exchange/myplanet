# myPlanet refactor round — repository boundaries, DAO hygiene, viewmodel slimming

date: 2026-09-24 · base commit: 14da1ba6bcec7b8affbf047ac78f16c511f5b357
open PRs checked: 17435, 17430, 17356, 17254, 17187, 16624, 16623, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075
off-limits applied: only PRs updated in the last 7 days (17187, 17254, 15226, 17435) — their files (ui/resources/*, TeamResourcesAdapter/Fragment, FileUtils, LibraryTypeClassifier, MediumUtils, ResourceCardHelper) do not appear below. All other open PRs are stale enough to ignore per maintainer guidance.

Focus: reinforcing repository boundaries between layers, calling out cross-feature data leaks, tightening repository interfaces, moving data functions from UI into viewmodels/repositories, DAO Room optimizations, and smoother repository–viewmodel relationships. Every file below was opened and confirmed on the base commit; none appears in more than one task; none is touched by the recent open PRs above.

---

### 1. extract TeamTaskDao reads out of NotificationsRepositoryImpl behind a reader seam (roadmap 1+4)

context: `NotificationsRepositoryImpl` injects `private val teamTaskDao: TeamTaskDao` (app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:35) and reads the teams feature's task table directly at `teamTaskDao.getById(it)` (:244), `teamTaskDao.getByIds(taskIds)` (:282), `teamTaskDao.getByTitles(taskTitles)` (:346) and `teamTaskDao.getTasksForUserBetween(userId, current, tomorrow.timeInMillis)` (:396). A repository reaching into a foreign feature's DAO is the cross-feature data leak this round targets; routing the reads through a named reader gives the boundary an explicit type.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (constructor at :31-36, the four call sites above), and new file `app/src/main/java/org/ole/planet/myplanet/repository/TeamTasksReader.kt`. Leave the existing `Lazy<TeamsNotificationsRepository>` injection (:31) and the `voicesRepository.countTopLevelByTeams` call alone — they are already interface-bound seams, not DAO leaks. Do not touch `data/room/dao/TeamTaskDao.kt`, `TeamsRepository.kt`, or `TeamsRepositoryImpl.kt`.

steps:
1. Create `TeamTasksReader` as a concrete class with `@Inject constructor(private val teamTaskDao: TeamTaskDao)` exposing `getTaskById(taskId)`, `getTasksByIds(taskIds)`, `getTasksByTitles(titles)`, and `getTasksForUserBetween(userId, start, end)` delegating to the matching `TeamTaskDao` methods — no DI module or `@Binds` needed for a concrete `@Inject` class.
2. In `NotificationsRepositoryImpl`, remove `private val teamTaskDao: TeamTaskDao` from the constructor and add `private val teamTasksReader: TeamTasksReader`; update imports.
3. Swap the four call sites: `getById` -> `teamTasksReader.getTaskById`, `getByIds` -> `getTasksByIds`, `getByTitles` -> `getTasksByTitles`, `getTasksForUserBetween` -> `teamTasksReader.getTasksForUserBetween`.
4. Delete the now-unused `TeamTaskDao` import and confirm Hilt/KSP compiles cleanly.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the notifications screen still renders team-task notifications with task titles and team names.

size budget: ~60 changed lines, 2 files (1 new).

out of scope: no changes to `TeamTaskDao`, `TeamsRepository`, or other repositories; do not move the `Lazy<TeamsNotificationsRepository>` or `voicesRepository` usages.

---

### 2. route ActivitiesRepositoryImpl's user lookup through UserRepository (roadmap 1+4)

context: `ActivitiesRepositoryImpl` injects `userDao: UserDao` (:62) solely to call `userDao.getByName(userId)` at :100 inside `logCourseVisit`, reading the user table directly instead of going through the user boundary. The class already injects `userRepository: Lazy<UserRepository>` (:52) and already calls `userRepository.get().getUserById(userId)` at :249, so the seam and the matching method (`UserRepository.getUserByName`, declared at UserRepository.kt:32) both exist.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` only (:52, :62, :100). Leave the other five DAO injections — they are the activities domain's own tables.

steps:
1. Change `val user = userDao.getByName(userId)` (:100) to `val user = userRepository.get().getUserByName(userId)`, matching the `getUserById` pattern already used at :249.
2. Remove `private val userDao: UserDao` from the constructor (:62) and drop the `UserDao` import.
3. Confirm no remaining `userDao` references in the file.

acceptance: `./gradlew testDefaultDebugUnitTest` green; course visits still log with the user's `parentCode`/`planetCode` populated.

size budget: ~8 changed lines, 1 file.

out of scope: no changes to `UserRepository`/`UserDao` signatures, no other repository methods.

---

### 3. strip UserRepositoryImpl's foreign DAOs behind the resources and events boundaries (roadmap 1+4)

context: `UserRepositoryImpl` reads two tables owned by other features: `myLibraryDao.getByIds(resourceIds.toList())` at :986 inside `getAchievementData` (resources domain — `ResourcesRepository.getLibraryItemsByIds` already exists at ResourcesRepository.kt:68 and `resourcesRepositoryLazy` is already injected), and `meetupDao.getByUserId(userId)` at :1252 inside `getShelfData` (events domain — `MeetupDao.getByUserId` is declared at MeetupDao.kt:38 but no repository method exposes it). Both DAOs are used exactly once, so the whole foreign-table surface can be removed.

files: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` (constructor, :986, :1252), `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepository.kt` (add `getMeetupsForUser`), `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt` (implement it — it already injects `meetupDao`). Leave all other DAO injections and `Lazy` wrappers untouched.

steps:
1. Add `suspend fun getMeetupsForUser(userId: String): List<Meetup>` to `EventsRepository` and implement it in `EventsRepositoryImpl` as a one-line delegate to `meetupDao.getByUserId(userId)` (exists at MeetupDao.kt:38).
2. In `UserRepositoryImpl`'s constructor, drop `meetupDao: MeetupDao` and `myLibraryDao`, add `private val eventsRepositoryLazy: dagger.Lazy<EventsRepository>` (Lazy matches the file's existing `resourcesRepositoryLazy`/`coursesRepositoryLazy` pattern).
3. Change :986 `myLibraryDao.getByIds(resourceIds.toList())` to `resourcesRepositoryLazy.get().getLibraryItemsByIds(resourceIds.toList())`.
4. Change :1252 `meetupDao.getByUserId(userId)` to `eventsRepositoryLazy.get().getMeetupsForUser(userId)`; remove unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` green; achievements screen still lists resources and the user shelf payload still contains meetup ids.

size budget: ~45 changed lines, 3 files.

out of scope: no changes to `MeetupDao`, `MyLibraryDao`, or `ResourcesRepository`; do not touch the `Lazy` providers or other repository methods.

---

### 4. delete the duplicated meetups sync write by reusing EventsSyncWriter.batchInsertMeetups (roadmap 1+5)

context: `CommunitySyncWriter.insertMeetupsFromSync` (CommunitySyncWriter.kt:6) is implemented in `CommunityRepositoryImpl` (:65-82) writing the **events** domain's meetups table — and it is a byte-for-byte duplicate of `EventsRepositoryImpl.batchInsertMeetups` (:97-118), already exposed on `EventsSyncWriter` (EventsSyncWriter.kt:6) and bound in `RepositoryModule` (:133). The community copy exists only because `TransactionSyncManager` injects `CommunitySyncWriter` (:80) for the `"meetups"` table at :103.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt` (:80 injection, :103 call site), `app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt` (:93 passes `communityRepository: CommunitySyncWriter` into the `TransactionSyncManager` factory), `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt` (:113 `bindCommunitySyncWriter` + :15 import), `app/src/main/java/org/ole/planet/myplanet/repository/CommunitySyncWriter.kt` (delete file — its only method moves), `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt` (drop `insertMeetupsFromSync`, the `CommunitySyncWriter` supertype, and the `meetupDao` injection — its only meetups uses are :69/:81). Do not touch `EventsSyncWriter`/`EventsRepositoryImpl` — the target method already exists.

steps:
1. In `TransactionSyncManager`, replace `private val communityRepository: CommunitySyncWriter` with `private val eventsSyncWriter: EventsSyncWriter` and change `communityRepository.insertMeetupsFromSync(extractDocs(arr))` (:103) to `eventsSyncWriter.batchInsertMeetups(extractDocs(arr))`; update the import.
2. In `ServiceModule`, rename the :93 parameter to `eventsSyncWriter: EventsSyncWriter` and pass it into the `TransactionSyncManager` call.
3. In `RepositoryModule`, remove the `bindCommunitySyncWriter` @Binds method (:113) and its import.
4. In `CommunityRepositoryImpl`, delete `insertMeetupsFromSync`, remove `CommunitySyncWriter` from the supertype list, drop `meetupDao` from the constructor, and clean imports.
5. Delete `CommunitySyncWriter.kt`.

acceptance: `./gradlew testDefaultDebugUnitTest` green and `TransactionSyncManager` compiles against `EventsSyncWriter`; a sync run still ingests the `meetups` table (dedup of updated docs preserved — same logic, now single-sourced).

size budget: ~70 changed lines, 5 files.

out of scope: no changes to `EventsSyncWriter`, `EventsRepositoryImpl`, `MeetupDao`, or `CommunityRepository`'s remaining methods; do not alter `batchInsertMeetups` behavior.

---

### 5. route ProgressRepositoryImpl and CoursesRepositoryImpl submissions-domain access through SubmissionsRepository (roadmap 1+4)

context: `ProgressRepositoryImpl` injects four DAOs that belong to the submissions domain — `examDao.getByCourseIds` (:68), `submissionDao.getExamSubmissionsByUser` (:72), `answerDao.getBySubmissionIds` (:186), `questionDao.getByIds` (:189) — and `CoursesRepositoryImpl` already injects `SubmissionsRepository` (:59) yet still uses `submissionDao.getExamSubmissionsByUser` (:469), `answerDao.getBySubmissionIds` (:480), and a delete trio `submissionDao.getUnuploadedNonSurveyByParentIds`/`answerDao.deleteBySubmissionIds`/`submissionDao.deleteByIds` (:619-626). `SubmissionsRepositoryImpl` already owns all four DAOs (:52-55), so the missing piece is a few interface methods.

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt` (add six methods), `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (implement them as one-line DAO delegates — `AnswerDao.getBySubmissionIds` and `deleteBySubmissionIds` already chunk internally at AnswerDao.kt:16-18/:27-29), `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt` (:68,:72,:186,:189 + drop the four foreign DAOs from its constructor), `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt` (:469,:480,:619-626 + drop `submissionDao`/`answerDao`; keep `examDao`/`questionDao`/`appDatabase` — used elsewhere). Do not touch `ExamDao`/`QuestionDao`/`SubmissionDao`/`AnswerDao`.

steps:
1. Add to `SubmissionsRepository`: `getExamSubmissionsByUser(userId): List<Submission>`, `getAnswersBySubmissionIds(submissionIds): List<Answer>`, `getQuestionsByIds(questionIds): List<ExamQuestion>`, `getExamsByCourseIds(courseIds): List<StepExam>`, `getUnuploadedNonSurveySubmissionsByParentIds(parentIds): List<Submission>`, `deleteSubmissionsWithAnswers(submissionIds): Int`; implement each in `SubmissionsRepositoryImpl` as a delegate to the matching DAO method (delete = `answerDao.deleteBySubmissionIds` + `submissionDao.deleteByIds`).
2. In `ProgressRepositoryImpl`, inject `submissionsRepository: SubmissionsRepository`, swap the four call sites, and remove `examDao`/`submissionDao`/`answerDao`/`questionDao` from the constructor.
3. In `CoursesRepositoryImpl`, swap :469 and :480 to the new methods; in `deleteCoursesProgress` keep `examDao.getByCourseIds` and the `appDatabase.withTransaction` wrapper but swap :619/:625/:626 to `submissionsRepository.*`; remove `submissionDao`/`answerDao` injections.

acceptance: `./gradlew testDefaultDebugUnitTest` green; course progress detail still shows exam/submission data and course deletion still clears unuploaded submissions.

size budget: ~110 changed lines, 4 files.

out of scope: no DAO changes, no `CourseProgressDao`/`CourseStepDao` moves; keep `examDao`/`questionDao` inside `CoursesRepositoryImpl` (used for course content, not submissions).

---

### 6. chunk the IN-clause reads in TeamLogDao (roadmap 1+7)

context: `TeamLogDao.getRecentTeamVisits(cutoff, teamIds)` (:15-16) uses `teamId IN (:teamIds)` and `getByRemoteIds(ids)` (:24-25) uses `_id IN (:ids)` — un-chunked `IN` lists. Room binds each element as a `?` parameter and SQLite caps bind parameters at 999, so a user in hundreds of teams overflows; `TeamsRepositoryImpl.getRecentVisitCounts` passes the full team-id list through.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt` only. Do not touch `TeamsRepositoryImpl.kt` — keep both public signatures identical so callers compile unchanged. Leave `getTeamVisitsForUsers`, `getLastVisit`, and the write methods alone.

steps:
1. Rename the two `@Query` methods to `getRecentTeamVisitsInternal` and `getByRemoteIdsInternal` (keep the `@Query` annotations on them).
2. Add a default `suspend fun getRecentTeamVisits(cutoff: Long, teamIds: List<String>)` that calls `teamIds.chunked(500).flatMap { getRecentTeamVisitsInternal(cutoff, it) }`.
3. Add a default `suspend fun getByRemoteIds(ids: List<String>)` doing the same over `getByRemoteIdsInternal`.
4. Confirm callers compile with zero edits (same public names).

acceptance: `./gradlew testDefaultDebugUnitTest` green; team visit counts still populate on the teams list.

size budget: ~30 changed lines, 1 file.

out of scope: no caller/repository changes, no other DAO methods, no index or schema changes.

---

### 7. chunk the IN-clause reads in NotificationDao (roadmap 1+7)

context: `NotificationDao` declares `getByIds(ids)` (:39-40), `getIdsByIds(ids)` (:42-43), and `markAsRead(ids, createdAt)` (:48-49) with un-chunked `IN (:ids)`; `NotificationsRepositoryImpl.getIdsByIds(notificationIds.toList())` passes an unbounded notification-id list — same SQLite 999-bind-parameter risk. `AnswerDao` in the same codebase already uses the chunked-internal pattern (`getBySubmissionIdsInternal` + `chunked(900)` at AnswerDao.kt:14-18).

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` only. Keep all three public signatures identical — do not touch `NotificationsRepositoryImpl.kt` (another task edits it, and no caller change is needed anyway).

steps:
1. Rename the three `@Query` methods to `getByIdsInternal`, `getIdsByIdsInternal`, `markAsReadInternal`.
2. Add default `getByIds`/`getIdsByIds` that chunk with `chunked(500)` and `flatMap` over the internal methods.
3. Add default `markAsRead(ids, createdAt)` that chunks and `sumOf`s the internal update calls.
4. Confirm callers compile unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green; notifications list loads and mark-as-read works for a user with many notifications.

size budget: ~35 changed lines, 1 file.

out of scope: no repository or caller changes, no schema/index work, no other DAO methods.

---

### 8. remove dead MyLibraryDao.getAll and give the test a count query (roadmap 1+8)

context: `MyLibraryDao.getAll()` (app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt:25-26, `SELECT * FROM my_library`) has zero production callers — its only use is `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryLibrarySyncTest.kt:124` (`assertEquals(1, myLibraryDao.getAll().size)`), which loads every row just to count them. Dead surface on a hot DAO plus a wasteful test query.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` and `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryLibrarySyncTest.kt`. Leave every other `MyLibraryDao` method untouched — most back live repository calls.

steps:
1. Add `@Query("SELECT COUNT(*) FROM my_library") suspend fun countAll(): Int` next to `getAll` in `MyLibraryDao`.
2. Change the test's `assertEquals(1, myLibraryDao.getAll().size)` (:124) to `assertEquals(1, myLibraryDao.countAll())`.
3. Delete `getAll()` and remove the `MyLibraryEntity` import if it becomes unused.

acceptance: `./gradlew testDefaultDebugUnitTest` green including `ResourcesRepositoryLibrarySyncTest`.

size budget: ~10 changed lines, 2 files.

out of scope: no other DAO methods, no production-code changes beyond the DAO.

---

### 9. route LoginActivity's direct repository calls through LoginViewModel (roadmap 3+4+10)

context: `LoginActivity` still reaches into repositories straight from the activity and its extension files: `userRepository.getUserByName(username)` at :278, `communityRepository.syncCommunityDocs()` at :374, `userRepository.createGuestUser(...)` at :589 and :631, and it passes `userRepository` into `showGuestLoginDialog(userRepository)` at :321. `GuestLoginExtensions.kt` then calls `userRepository.validateUsername` (:39, :58), `findUserByName` (:60), `createGuestUser` (:68); `ServerDialogExtensions.kt` calls `communityRepository.getAllSorted()` (:~298). These are viewmodel jobs — hoisting them keeps the sync UI stateless and portable (roadmap 10's state-hoisted north star).

files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` (:278, :321, :374, :589, :631), `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt` (:19 signature, :39, :58, :60, :68), `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` (:~298). Leave `configurationsRepository.checkVersion`/`prefData`/`sharedPrefManager` usages alone — they belong to the SyncActivity config flow.

steps:
1. In `LoginViewModel`, inject `UserRepository` and `CommunityRepository` (it already injects `teamsRepository`) and add thin delegates: `getUserByName(name)`, `createGuestUser(name)`, `syncCommunityDocs()`, `getCommunitiesSorted()`, `validateUsername(name)`, `findUserByName(name)`.
2. In `LoginActivity`, replace the direct calls with `loginViewModel.*` and change `showGuestLoginDialog(userRepository)` to `showGuestLoginDialog(loginViewModel)`; drop the `userRepository`/`communityRepository` injections if nothing else uses them (check first).
3. In `GuestLoginExtensions`, change the signature from `showGuestLoginDialog(userRepository: UserRepository)` to `showGuestLoginDialog(viewModel: LoginViewModel)` and swap its four calls to the viewmodel.
4. In `ServerDialogExtensions`, replace `communityRepository.getAllSorted()` with `loginViewModel.getCommunitiesSorted()`; drop the repository parameter if it becomes unused.
5. Tighten in the same file: swap `teamsRepository.refreshJoinedMembersForLogin(teamId)` (:51) to a new `TeamsMembersRepository` injection — the method lives on the narrow interface; keep `teamsRepository.getAllActiveTeams()` (:43) on `TeamsRepository`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; sign-in proceeds for a valid user, the guest dialog still validates/creates a guest, and the manual server-config dialog still populates communities.

size budget: ~130 changed lines, 4 files.

out of scope: no changes to `SyncActivity`, `ProcessUserDataActivity`, or any repository interface/implementation; no new DI modules.

---

### 10. move ProcessUserDataActivity's sync/upload calls into a new SyncViewModel (roadmap 3+10)

context: `ProcessUserDataActivity` injects `syncRepository` (:~49) and `userRepository` (:~59) directly and calls `syncRepository.uploadLoginData()` (:193), `syncRepository.uploadBulkData()` (:211), and `userRepository.fetchUserSecurityData(name)` (:254) inside its coroutine blocks. An abstract activity juggling dialogs, broadcasts, and raw repository calls is doing two layers' work; a small `SyncViewModel` gives the `ui/sync` package a single owner for sync/upload operations.

files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt` (:49, :59, :193, :211, :254) and new file `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncViewModel.kt`. Leave the dialog helpers, `broadcastReceiver` registration, and `prefData`/`sharedPrefManager` handling in the activity untouched.

steps:
1. Create `SyncViewModel` as `@HiltViewModel` injecting `SyncRepository` and `UserRepository`, exposing `uploadLoginData()`, `uploadBulkData()`, and `fetchUserSecurityData(name)` returning the same Flow/result types the activity consumes today.
2. In `ProcessUserDataActivity`, remove the `lateinit var syncRepository`/`lateinit var userRepository` injections and add `private val syncViewModel: SyncViewModel by viewModels()`.
3. Swap the three call sites to `syncViewModel.*` and clean imports.
4. Confirm the subclass (`SyncActivity`) still compiles unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the post-login "uploading user data" progress flow still completes and the security-data fetch still resolves before navigation.

size budget: ~90 changed lines, 2 files (1 new).

out of scope: do not move `SyncActivity`-specific logic, do not change `SyncRepository`/`UserRepository` interfaces, no lifecycle/flow redesign.

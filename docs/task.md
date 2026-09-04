# myPlanet refactor round — 10 work orders

**Repository:** `open-learning-exchange/myplanet` (master @ `9ff1273`)

## Open PRs checked (R3)

| PR | Title | Main-source files claimed |
|----|-------|---------------------------|
| 16705 | resources: medium filter label formatting | `ui/resources/ResourcesFilterFragment.kt` |
| 16702 | resources: download status filter resetting | `ui/resources/ResourcesFragment.kt` |
| 16701 | courses: progress filter resetting | `ui/courses/CourseFilterController.kt`, `ui/courses/CoursesFragment.kt` |
| 16698 | courses: scope progress updates to acting user | `base/BaseExamFragment.kt`, `data/room/dao/CourseProgressDao.kt`, `repository/CoursesRepository.kt`, `repository/CoursesRepositoryImpl.kt`, `ui/exam/ExamTakingFragment.kt` |
| 16693 | userProfileFragment: select image from gallery | `ui/user/UserProfileFragment.kt` |
| 16690 | onboardingActivity: app initialization | `ui/onboarding/OnboardingActivity.kt` |
| 16688 | resources: injected DispatcherProvider | `ui/resources/AddResourceFragment.kt` |
| 16686 | timeUtils: date formatting refactor | `utils/TimeUtils.kt` |
| 16680 | utils: JsonUtils shared getPrimitive | `utils/JsonUtils.kt`, `repository/TeamsRepositoryBenchmarkTest.kt` (test) |
| 16677 | NotificationDao.markSynced batch updates | `data/room/dao/NotificationDao.kt` |
| 16661 | slow library opening | `data/room/AppDatabase.kt`, `model/MyLibrary.kt`, `model/ResourceListModel.kt`, `model/TagEntity.kt`, `repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`, `ui/resources/ResourcesFragment.kt`, `ui/resources/ResourcesViewModel.kt` |
| 16647 | courses: restoreFilterState refactor | `model/TagEntity.kt`, `ui/courses/CourseFilterController.kt`, `ui/courses/CoursesFragment.kt`, `ui/courses/CoursesViewModel.kt`, `ui/resources/CollectionsFragment.kt` |
| 16624 | search: offline full text search | `repository/CoursesRepository.kt`, `repository/CoursesRepositoryImpl.kt`, `utils/ResourcesSearchUtils.kt` |
| 16623 | teams: interactive team tasks | `callback/OnTaskCompletedListener.kt`, `model/TeamTask.kt`, `repository/TeamsRepository.kt`, `repository/TeamsRepositoryImpl.kt`, `ui/teams/tasks/*` |
| 16619 | flexbox library migration | `app/build.gradle`, `base/BaseDashboardFragment.kt`, dashboard card layouts, `gradle/libs.versions.toml` |
| 16594 | actions: size labeller fetching | `.github/scripts/labels.sh`, workflows |
| 16270 | Codex Cloud skill submodules | `.codex/setup.sh`, `AGENTS.md` |
| 16101 | all: consistent status bar | `utils/EdgeToEdgeUtils.kt`, 13 activity files incl. `ui/dashboard/DashboardActivity.kt`, `ui/sync/LoginActivity.kt`, `ui/settings/SettingsActivity.kt`, login/dashboard layouts |
| 15951 | teams: repository update requesting | `data/room/AppDatabase.kt`, `model/CreateTeamRequest.kt`, `model/MyTeam.kt`, `model/TeamDetails.kt`, `model/TeamUpdateRequest.kt`, `repository/TeamsRepository.kt`, `repository/TeamsRepositoryImpl.kt`, `ui/teams/PlanFragment.kt`, `ui/teams/TeamFragment.kt`, `ui/teams/TeamViewModel.kt`, `ui/teams/TeamsAdapter.kt`, `utils/TimeUtils.kt` |
| 15825 | local event task reminders | `AndroidManifest.xml`, `data/room/dao/MeetupDao.kt`, `di/RepositoryModule.kt`, `repository/EventsRepositoryImpl.kt`, `services/TaskNotificationWorker.kt`, `services/reminders/*`, `ui/dashboard/DashboardActivity.kt`, `utils/NotificationUtils.kt`, `res/xml/pref.xml` |
| 15824 | gamification achievement hub | `app/build.gradle`, `data/room/dao/{CourseProgressDao,NewsDao,OfflineActivityDao,SubmissionDao,TeamTaskDao}.kt`, `di/RepositoryModule.kt`, `model/gamification/*`, `repository/GamificationRepository{,Impl}.kt`, `ui/user/{AchievementFragment,CertificateDialogFragment,CourseCertificatesAdapter,GamificationBadgesAdapter,GamificationViewModel}.kt` |
| 15820 | teams: task/meetup comment threads | `data/room/dao/NewsDao.kt`, `repository/EventsRepository{,Impl}.kt`, `repository/TeamsRepository{,Impl}.kt`, `ui/events/EventsAdapter.kt`, `ui/teams/InlineCommentsAdapter.kt`, `ui/teams/TeamCalendar{Fragment,ViewModel}.kt`, `ui/teams/tasks/*` |
| 15808 | sync: couchdb _changes feed | `data/room/AppDatabase.kt`, `data/room/dao/{AchievementDao,CertificationDao,ChatDao,CourseDao,CourseProgressDao,ExamDao,FeedbackDao,HealthExaminationDao,MeetupDao,RatingDao,SyncCursorDao,TagDao,TeamTaskDao}.kt`, `di/{RoomModule,ServiceModule}.kt`, `model/SyncCursor.kt`, most of `repository/*` (Chat, Community, Courses, Feedback, Health, Progress, Ratings, Submissions, Surveys, Tags, TeamsImpl, TeamsSync, UserImpl, UserSync, Voices), `services/ServerReachabilityWorker.kt`, `services/sync/{HeavyTableSyncWorker,TransactionSyncManager}.kt` |
| 15699 | resources: rating dialog + tests | `data/room/AppDatabase.kt`, `data/room/dao/RatingDao.kt`, `model/RatingPromptLog.kt`, `repository/RatingsRepository{,Impl}.kt`, `ui/viewer/*` |
| 15559 | exam: redesign UI with timer | `ui/exam/ExamTakingFragment.kt`, exam layouts/strings/colors |
| 15519 | dismiss button on last-synced container | `ui/dashboard/DashboardActivity.kt`, `res/layout/activity_dashboard.xml` |
| 15412 | courses landscape content space | `ui/courses/{CourseSelectionController,CoursesAdapter,CoursesFragment,CoursesViewModel}.kt`, `ui/resources/ResourcesFragment.kt`, course/search layouts |
| 15267 | download popup cropping | `base/BaseRecyclerFragment.kt`, `base/BaseResourceFragment.kt`, `ui/resources/{ResourcesAdapter,ResourcesFragment}.kt`, library layouts, `values/styles.xml` |
| 15266 | team calendar cropping | `res/layout/fragment_calendar.xml`, `res/layout/fragment_enterprise_calendar.xml` |
| 15198 | preserve chat input across providers | `ui/chat/{ChatDetailFragment,ChatViewModel}.kt` |
| 15158 | teams: delete calendar events | `data/room/AppDatabase.kt`, `data/room/dao/{MeetupDao,RemovedLogDao}.kt`, `model/Meetup.kt`, `model/MeetupCreationParams.kt`, `repository/{CommunityRepositoryImpl,EventsRepository,EventsRepositoryImpl,UserRepositoryImpl}.kt`, `ui/events/*`, `ui/teams/TeamCalendar*`, `res/layout/add_meetup.xml` |
| 15108 | fix event calendar marking | `model/Meetup.kt`, `model/MeetupCreationParams.kt`, `repository/EventsRepository{,Impl}.kt`, `ui/events/{EventsDetailFragment,EventsDetailViewModel}.kt`, `ui/teams/TeamCalendar*`, `res/layout/add_meetup.xml` |
| 14960 | login history landscape | `ui/sync/LoginActivity.kt` (+ login layouts) |
| 14893 | dashboard landscape cropping | `ui/dashboard/DashboardActivity.kt`, home_card_* layouts, `values/dimens.xml`, `values-land/dimens.xml` |
| 14883 | team leaderboard tab | `model/TeamLeaderboardEntry.kt`, `repository/{ProgressRepositoryImpl,SurveysRepository,SurveysRepositoryImpl}.kt`, `ui/teams/{TeamDetailFragment,TeamPageConfig}.kt`, `ui/teams/leaderboard/*` |
| 14650 | survey: smoother submissions display | `base/BaseDashboardFragment.kt`, `model/AssignedSurvey.kt`, `repository/SurveysRepository{,Impl}.kt`, `ui/dashboard/DashboardViewModel.kt`, `ui/exam/ExamTakingFragment.kt`, `ui/submissions/*` |
| 14427 | course streak | `repository/{ActivitiesRepository,ActivitiesRepositoryImpl,ProgressRepositoryImpl,SubmissionsRepositoryImpl}.kt`, `ui/courses/{CoursesAdapter,CoursesViewModel}.kt`, `ui/dashboard/{BellDashboardFragment,BellDashboardViewModel,DashboardViewModel}.kt`, `utils/StreakUtils.kt` |
| 13928 | baseline profile module | `app/build.gradle`, `baselineprofile/*`, `build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle` |
| 13848 | Course/Grade models | `model/{CourseLevel,GradeLevel}.kt`, `ui/courses/CourseFilterController.kt`, `ui/exam/UserInformationFragment.kt`, `ui/resources/AddResourceActivity.kt`, `ui/user/{BecomeMemberActivity,UserProfileFragment}.kt` |
| 13657 | archive course action | `model/{Course,RealmMyCourse}.kt`, `repository/CoursesRepository{,Impl}.kt`, `ui/courses/{CourseFilterController,CourseSelectionController,CoursesFragment,CoursesViewModel}.kt` |
| 13604 | teams: sort surveys by completeness | `ui/surveys/{SurveyFragment,SurveysViewModel}.kt`, sort string-arrays |
| 13415 | voices: emoji reactions | `base/BaseVoicesFragment.kt`, `data/{DatabaseService,RealmMigrations}.kt`, `model/RealmNews.kt`, `repository/VoicesRepository{,Impl}.kt`, `ui/resources/ResourcesViewModel.kt`, `ui/voices/{VoicesAdapter,VoicesFragment}.kt` |
| 13355 | P2P resource sharing | `AndroidManifest.xml`, `callback/OnLibraryItemSelectedListener.kt`, `services/P2pTransferManager.kt`, `ui/resources/{P2pTransferActivity,ResourceDetailFragment,ResourcesAdapter,ResourcesFragment}.kt` |
| 13287 | profile: no char limit | `res/layout/activity_become_member.xml`, `res/layout/edit_profile_dialog.xml` |
| 10993 | voices video | `base/BaseVoicesFragment.kt`, `callback/OnNewsItemClickListener.kt`, `model/RealmNews.kt`, `repository/VoicesRepository{,Impl}.kt`, `services/UploadManager.kt`, `ui/teams/voices/*`, `ui/voices/{ReplyActivity,VoicesAdapter,VoicesFragment}.kt`, voices layouts |
| 8175 | roboscript update | `app/build.gradle`, `ci/robo/*` |
| 4075 | robo movie | `.github/workflows/android-release.yml` |
| 15226 | Flutter port | `flutter/**` and agent-doc files only — no collision with `app/` |

**Global off-limits rule for all 10 tasks:** do not touch `app/build.gradle`, `gradle/libs.versions.toml`, `settings.gradle`, `build.gradle.kts`, `data/room/AppDatabase.kt`, or `AndroidManifest.xml` — multiple open PRs touch them.

## Template key

Each task: roadmap item served (+ north-star 9/10 contribution where true), files (all verified to exist on master @ `9ff1273`), exact work, acceptance criteria.

---

## Task 1 — notifications: batch team chat counts at the repository boundary

**Roadmap:** 1 (data layer) + 7 (performance hotspot) + 9 (thins cross-feature coupling toward a platform-free core).

**Problem:** `NotificationsRepositoryImpl.getTeamNotifications()` (`repository/NotificationsRepositoryImpl.kt:295-326`) loops `teamIds` and calls `voicesRepository.countTopLevelByTeam(teamId)` once per team (line 311) — an N+1 query pattern on the dashboard path — and reaches across the voices feature boundary to do it. The per-team DAO query exists at `data/room/dao/NewsDao.kt:80-81` (`countTopLevelByTeam(teamId, teamPattern)`).

**Files:**
- `data/room/dao/NewsDao.kt`
- `repository/NotificationsRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Work:**
1. Add to `NewsDao`: `@Query("SELECT viewableId AS teamId, COUNT(*) AS cnt FROM news WHERE (replyTo IS NULL OR replyTo = '') AND viewableBy = 'teams' COLLATE NOCASE AND viewableId IN (:teamIds) COLLATE NOCASE GROUP BY viewableId") suspend fun countTopLevelByTeamIds(teamIds: List<String>): List<TeamChatCount>` with a small `data class TeamChatCount(val teamId: String, val cnt: Long)` declared beside the DAO (mirrors how `RatingAggregate` lives beside `RatingDao`).
2. In `NotificationsRepositoryImpl`, replace the per-team `voicesRepository.countTopLevelByTeam(teamId)` loop with a single `voicesRepository.countTopLevelByTeamIds(teamIds)` call: add that batch method to `VoicesRepository`/`VoicesRepositoryImpl` delegating to the new DAO query (the LIKE-`viewIn` branch of the old per-team query applies only to the caller that passes a pattern; for the notifications use-case the `viewableBy = 'teams'` match is the semantic used by `updateTeamNotification`, which counts via `newsList.size` of `getFilteredNews`). Teams missing from the grouped result map to count 0. Keep the existing single-team `countTopLevelByTeam` (still used elsewhere in VoicesRepositoryImpl).
3. Extend `NotificationsRepositoryImplTest.kt` (exists) with a test asserting one batch call is made and teams without rows get `hasChat = false`.

**Acceptance:** `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest*"` passes; no per-team query loop remains in `getTeamNotifications`.

---

## Task 2 — health: single-statement batch `markUploaded` in HealthExaminationDao

**Roadmap:** 1 (data layer) + 7 (DAO optimization).

**Problem:** `HealthExaminationDao.markUploaded(idToRevMap)` (`data/room/dao/HealthExaminationDao.kt:32-37`) runs one `UPDATE` per entry inside `@Transaction`. Called from `HealthRepositoryImpl.markHealthExaminationsUploaded` (line 68-70), driven by `services/UploadToShelfService.kt:74,85`.

**Files:**
- `data/room/dao/HealthExaminationDao.kt`
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/` — add `HealthExaminationDaoTest.kt` (directory exists; mirror the pattern of `SyncCursorDaoTest.kt`).

**Work:**
1. Add `@Query("UPDATE health_examinations SET _rev = COALESCE(:rev, _rev), isUpdated = 0 WHERE _id = :id") suspend fun markUploadedOne(id: String, rev: String?)` only if needed — preferred: keep the single-row `markUploaded(id, rev)` and rewrite the map overload to group null-rev ids and batch them: `@Query("UPDATE health_examinations SET isUpdated = 0 WHERE _id IN (:ids)") suspend fun markUploadedWithoutRev(ids: List<String>)`, chunking with `chunked(900)` exactly as `RemovedLogDao.kt:20` and `NotificationDao` (master version) already do. Non-null revs still need per-row updates (rev differs per row) — that is acceptable; the common path from `UploadToShelfService` passes null revs for already-synced docs.
2. Add a Room in-memory test (Robolectric, `Room.inMemoryDatabaseBuilder`, `allowMainThreadQueries`) covering: rows with rev, rows without rev, mixed batch of >2 entries, and the empty-map no-op.

**Acceptance:** new DAO test passes via `./gradlew testDefaultDebugUnitTest --tests "*HealthExaminationDaoTest*"`; `HealthRepositoryImplTest.kt` still passes unchanged (interface signature untouched).

---

## Task 3 — health: move MyHealth decryption out of HealthExaminationViewModel

**Roadmap:** 1 (data-layer cleanup) + 3 (viewmodel/use-case expansion) + 9 (removes `AndroidDecrypter` + Gson work from the UI layer into the repository, where identical logic already exists).

**Problem:** `ui/health/HealthExaminationViewModel.kt:71-77` decrypts `pojo.data` and Gson-parses `MyHealth` inline (`JsonUtils.gson.fromJson(decrypt(pojo.data, user?.key, user?.iv), MyHealth::class.java)`). The repository already performs the same decrypt+parse in `HealthRepositoryImpl.kt:168-169` (`AndroidDecrypter.decrypt(data, userModel?.key, userModel?.iv)` → `gson.fromJson(decrypted, MyHealth::class.java)`).

**Files:**
- `repository/HealthRepository.kt`
- `repository/HealthRepositoryImpl.kt`
- `ui/health/HealthExaminationViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/HealthRepositoryImplTest.kt` (exists)

**Work:**
1. Add `suspend fun getDecryptedHealth(pojo: HealthExamination?, user: UserEntity?): MyHealth?` to `HealthRepository`; implement in `HealthRepositoryImpl` reusing the existing decrypt/parse pattern (return null on empty `data` or parse failure, matching the ViewModel's current catch-and-null behavior).
2. In `HealthExaminationViewModel.loadData`, replace lines 71-77 with a call to the new repository method; drop the now-unused `AndroidDecrypter`/`JsonUtils` imports. Keep the `healthRepository.initHealth()` fallback as-is.
3. Add a `HealthRepositoryImplTest` case: encrypted fixture → returns parsed `MyHealth`; empty `data` → null; garbage `data` → null.

**Acceptance:** `./gradlew testDefaultDebugUnitTest --tests "*HealthRepositoryImplTest*"` passes; `HealthExaminationViewModel.kt` no longer imports `AndroidDecrypter` or `JsonUtils`; behavior (state contents) unchanged.

---

## Task 4 — repository: extract dashboard offline-login counting from UserRepositoryImpl into ActivitiesRepository

**Roadmap:** 1 (repository boundaries — stop cross-feature leak: UserRepository reaching into Activities data) + 3.

**Problem:** `UserRepositoryImpl.kt:94` calls `activitiesRepositoryLazy.get().getOfflineLoginCount(userName)` and line 888 calls `activitiesRepositoryLazy.get().hasUserSyncAction(userId)` — a lazy cross-repository dependency that only forwards calls. Check the actual call sites of these two `UserRepository` methods (`grep -rn "getOfflineLoginCount\|hasUserSyncAction" app/src/main/java --include=*.kt`) and move the declared interface methods so UI/ViewModel callers depend on `ActivitiesRepository` directly.

**Files:**
- `repository/UserRepository.kt`
- `repository/UserRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt` (exists)

**Work:**
1. Identify the callers of `UserRepository.getOfflineLoginCount` and `UserRepository.hasUserSyncAction` (expected: dashboard/sync UI or ViewModels — whichever files actually call them, but **do not touch** `ui/dashboard/DashboardActivity.kt`, `ui/dashboard/DashboardViewModel.kt`, `base/BaseDashboardFragment.kt`, `ui/sync/LoginActivity.kt`, or `ui/sync/SyncActivity.kt`, which are claimed by open PRs; if the only callers are those files, narrow this task to just the interface/impl change: deprecate-and-delegate is **not allowed** (no unused code) — in that case restrict the task to: move `hasUserSyncAction` only if its caller is an unclaimed file; otherwise use Task 4-alt below).
2. Delete the forwarding methods from `UserRepository`/`UserRepositoryImpl` and point callers at `ActivitiesRepository` (already injectable).
3. Update `UserRepositoryImplTest.kt` accordingly.

**Task 4-alt (use if step-1 check shows all callers are PR-claimed files) — personals: route uploadDocument through UploadRepository:** `PersonalsRepositoryImpl.uploadPersonalDocument` (`repository/PersonalsRepositoryImpl.kt:81-99`) calls `apiInterface.postDoc(...)` directly (line 82-85), bypassing `UploadRepository` which it already injects (line 21). Work: add `suspend fun postDocument(url: String, payload: JsonObject): JsonObject?` to `repository/UploadRepository.kt`/`UploadRepositoryImpl.kt` wrapping the same `apiInterface.postDoc(UrlUtils.header, "application/json", url, payload)` call; switch `PersonalsRepositoryImpl` to it; drop the `ApiInterface` import/field from `PersonalsRepositoryImpl`; extend `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` and `UploadRepositoryImplTest.kt` (both exist). Files: `repository/UploadRepository.kt`, `repository/UploadRepositoryImpl.kt`, `repository/PersonalsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt`. The executing agent must first confirm `UploadRepositoryImpl`'s existing methods don't already expose an equivalent POST — if one exists, use it and shrink the task to two files. **Roadmap:** 5 + 1.

---

## Task 5 — repositories: purge TextUtils from VoicesRepositoryImpl and HealthRepositoryImpl

**Roadmap:** 1 (data layer) + 9 (zero `android.*` imports in repositories).

**Problem:** Two repository implementations use `android.text.TextUtils.isEmpty` for simple null/empty checks: `VoicesRepositoryImpl.kt:3` (usages at lines 454, 459) and `HealthRepositoryImpl.kt:3` (usage at line 217).

**Files:**
- `repository/VoicesRepositoryImpl.kt`
- `repository/HealthRepositoryImpl.kt`

**Work:**
1. Replace `TextUtils.isEmpty(x)` with `x.isNullOrEmpty()` and `!TextUtils.isEmpty(x)` with `!x.isNullOrEmpty()` at the three call sites; remove both `import android.text.TextUtils` lines.
2. No test changes needed (purely mechanical); run the existing `VoicesRepositoryImplTest.kt`, `VoicesRepositoryNewsSyncTest.kt`, and `HealthRepositoryImplTest.kt`.

**Acceptance:** those three existing test classes pass; `grep -n "android.text.TextUtils" repository/VoicesRepositoryImpl.kt repository/HealthRepositoryImpl.kt` returns nothing.

---

## Task 6 — upload: extract team bulk-upload from UploadManager into TeamsUploadRunner

**Roadmap:** 5 (consolidate sync/upload workflow) + 1 (repository boundary — upload orchestration stops living in a god-object service).

**Problem:** `services/UploadManager.kt:255-351` (`uploadTeams`, `queueTeamRetry`) is a self-contained pipeline: fetch via `teamsSyncRepository.get().getTeamsForUpload()`, `_bulk_docs` POST via `uploadRepository.postUploadArray`, per-row error → `retryQueue.queueFailedOperation`, then `deleteLocalTeamRecords` / `markTeamsUploaded`. `UploadManager` is 582 lines and this block is the largest remaining non-`UploadCoordinator` flow besides news.

**Files:**
- `services/upload/TeamsUploadRunner.kt` (new — mirrors the existing `services/upload/PhotoUploader.kt` and `services/upload/AchievementUploader.kt` pattern)
- `services/UploadManager.kt`

**Work:**
1. Create `TeamsUploadRunner @Inject constructor(teamsSyncRepository: Lazy<TeamsSyncRepository>, uploadRepository: UploadRepository, retryQueue: RetryQueue, dispatcherProvider: DispatcherProvider)` in `services/upload/`. Move `uploadTeams()` and `queueTeamRetry()` verbatim (keeping the `processInBatches` chunked behavior — either move the private `processInBatches` helper too or inline `chunked(BATCH_SIZE)`; keep `TAG` logging). Note: `uploadTeamImageAttachment` (lines 353-373) needs `context` and `MyTeam.getAttachmentFile` — move it with the runner, injecting `@ApplicationContext context` into the runner.
2. `UploadManager` gains `private val teamsUploadRunner: TeamsUploadRunner` and its `uploadTeams()` becomes a one-line delegate, exactly like `uploadAchievement()` (line 148-150). Remove the moved private methods and now-unused imports (`MyTeam`, `TeamUploadData`, `Uri`, `asRequestBody`, `toMediaTypeOrNull`, `FileUtils` if unused after the move — verify by compiling).
3. No new tests required (behavior-preserving move of already-covered paths); ensure existing `UploadManager`-adjacent tests compile.

**Acceptance:** `./gradlew assembleDefaultDebug` compiles; `grep -n "queueTeamRetry\|uploadTeamImageAttachment" services/UploadManager.kt` returns nothing; the same symbols exist in `TeamsUploadRunner.kt`.

---

## Task 7 — courses: narrow TagsRepository course-link API

**Roadmap:** 1 (repository boundaries — courses feature leaks tag-query semantics) + 9.

**Problem:** `CoursesRepositoryImpl.kt:292` calls `tagsRepository.getLinkIdsForTagNames("courses", tagNames).toSet()` and line 589 calls `tagsRepository.getTagsForCourses(courseIds)` — verify at `repository/TagsRepository.kt` whether the `"courses"` db-name literal is the only cross-feature literal of its kind, and check the equivalent call in any other repository (`grep -rn "getLinkIdsForTagNames\|getTagsForCourses" app/src/main/java --include=*.kt`). The task: introduce `TagsRepository.getCourseLinkIds(tagNames: List<String>): Set<String>` (hiding the `"courses"` literal inside `TagsRepositoryImpl`) and switch `CoursesRepositoryImpl` to it, so the db-name string for the courses table lives in exactly one place.

**Files:**
- `repository/TagsRepository.kt`
- `repository/TagsRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/TagsRepositoryImplTest.kt` (exists)

**Work:**
1. Add `suspend fun getCourseLinkIds(tagNames: List<String>): Set<String>` to `TagsRepository`; implement in `TagsRepositoryImpl` as `getLinkIdsForTagNames("courses", tagNames).toSet()`.
2. Update the `CoursesRepositoryImpl.kt:292` call site to the new method and drop the local `.toSet()`/literal. (This line-touch in `CoursesRepositoryImpl.kt` conflicts with open PRs 16624/16698/13657 which also touch that file — **therefore** the call-site update is deferred: this task only adds the new narrow method + test, and `TagsRepositoryImplTest` gains a case asserting the `"courses"` db name is used. No unused code: the method is covered by the test; a follow-up round switches the caller.)
3. If the reviewer judges the test-only caller as "unused code" per R5, the fallback is to include the one-line call-site change and accept the trivial merge conflict — prefer including it.

**Acceptance:** `TagsRepositoryImplTest` passes with the new case; `TagsRepository` exposes the course-scoped method.

---

## Task 8 — dashboard: hoist assigned-survey count behind DashboardViewModel

**Roadmap:** 3 (viewmodel expansion) + 1 (repository boundary) + 10 (state hoisting — keeps the dashboard fragment free of per-item data calls so a future Compose port has a single state source).

**Problem:** In `base/BaseDashboardFragment.kt`, `myLifeListInit` (line 268+) loops visible life items and previously called `updateMyLifeSurveyCount()`; on master the count plumbing flows through `viewModel` (`DashboardViewModel`, line 57 `by viewModels()`), but `BaseDashboardFragment` still mixes direct repository usage for other counters in the same class (e.g., `userRepository.getUserModel()` inside `myTeamsInit`-adjacent code). Scope precisely: the fragment's `getAssignedSurveyCount`-equivalent path — verify the current master shape with `grep -n "surveyCount\|getAssignedSurveyCount" base/BaseDashboardFragment.kt ui/dashboard/DashboardViewModel.kt` before writing code; the work is to route every survey-count read in `BaseDashboardFragment` through `DashboardViewModel` and delete any leftover no-op/`updateMyLifeSurveyCount` residue.

**Files:**
- `base/BaseDashboardFragment.kt`
- `ui/dashboard/DashboardViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/` — extend the existing DashboardViewModel test file if present (check directory first; if none exists, add `DashboardViewModelSurveyCountTest.kt` using the established `MainDispatcherRule` + `TestDispatcherProvider` pattern from `app/src/test/java/org/ole/planet/myplanet/`).

**Work:**
1. Ensure `DashboardViewModel` exposes a single `suspend fun getAssignedSurveyCount(userId: String): Int` implemented via `withContext(dispatcherProvider.io)` over the surveys repository (the exact function may already exist on master — if it does, this task reduces to step 2 only).
2. In `BaseDashboardFragment`, remove any direct survey-count computation or dead `updateMyLifeSurveyCount()` remnants and call the ViewModel method once in `myLifeListInit`, passing the value into `getLayout(itemCnt, dashboardItem, surveyCount)` for every item (current per-item behavior preserved).
3. Add/adjust a ViewModel unit test: repository returns N assigned surveys → ViewModel returns N, and errors surface as 0.

**Acceptance:** existing dashboard tests pass; `BaseDashboardFragment.kt` contains no private survey-count function and no direct `surveysRepository` reference.

---

## Task 9 — sync: move shelf `when(type)` dispatch out of SyncRepositoryImpl

**Roadmap:** 5 (sync consolidation) + 1 (repository boundaries — SyncRepository stops knowing four feature repositories' batch-insert signatures) + 9 (decouples sync orchestration from feature repos via a narrow interface).

**Problem:** `SyncRepositoryImpl.processShelfDataOptimizedSync` (`repository/SyncRepositoryImpl.kt:186-196`) hard-codes `when (shelfData.type) { "resources" -> resourcesRepository.batchInsertMyLibrary(...); "courses" -> coursesRepository.batchInsertMyCourses(...); "meetups" -> eventsRepository.batchInsertMeetups(...); "teams" -> teamsSyncRepository.batchInsertMyTeams(...) }`, forcing `SyncRepositoryImpl` to inject four feature repositories (constructor lines 41-44).

**Files:**
- `repository/SyncRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/` — add `SyncRepositoryImplShelfTest.kt` only if `SyncRepositoryImpl` already has a test file to extend (check first; otherwise one small new test file).

**Work:**
1. Keep the four injections but replace the `when` with a private `Map<String, suspend (String?, List<JsonObject>) -> Int>` built in `init`/`lazy` mapping `"resources"` → `resourcesRepository::batchInsertMyLibrary`, `"courses"` → `coursesRepository::batchInsertMyCourses`, `"meetups"` → `eventsRepository::batchInsertMeetups`, `"teams"` → `teamsSyncRepository::batchInsertMyTeams`. This keeps the diff under 150 lines, touches one main file, and turns the open-coded `when` into a data table.
2. Add a unit test that an unknown `shelfData.type` contributes 0 processed items and does not throw (currently falls through `when` silently — lock that behavior in).

**Acceptance:** `./gradlew testDefaultDebugUnitTest` passes for repository tests; the `when (shelfData.type)` block in `SyncRepositoryImpl.kt` is replaced by the dispatch map.

---

## Task 10 — courses: remove android.util.Base64 from CoursesRepositoryImpl

**Roadmap:** 1 (data layer) + 9 (zero `android.*` in repositories — `CoursesRepositoryImpl` then has only `android.util.Log` left, the last one being a separate logging decision).

**Problem:** `CoursesRepositoryImpl.kt:3` imports `android.util.Base64`; single usage at line 684: `Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)` to derive a step id. `java.util.Base64` (available at min SDK 26) is a drop-in platform-free equivalent.

**Files:**
- `repository/CoursesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/CoursesRepositoryImplTest.kt` (exists)

**Work:**
1. Replace `Base64.encodeToString(bytes, Base64.NO_WRAP)` with `java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)` — **verify output parity**: `android.util.Base64.NO_WRAP` keeps padding by default, so the correct mapping is `java.util.Base64.getEncoder()` (with padding) unless the current call site passes `NO_WRAP or NO_PADDING`. Read the exact flags at line 684 first, choose the `java.util.Base64` encoder that produces byte-identical output (this id is persisted/compared, so output must not change), and remove the android import.
2. Add one test in `CoursesRepositoryImplTest.kt` asserting the derived step id for a fixed `JsonObject` input equals the known Base64 string (locks the parity requirement).

**Acceptance:** `CoursesRepositoryImplTest` passes including the new parity case; `grep -n "android.util.Base64" repository/CoursesRepositoryImpl.kt` returns nothing.

---

## Final task list

1. Batch team chat counts (NewsDao + NotificationsRepositoryImpl) — roadmap 1, 7, 9
2. HealthExaminationDao batch markUploaded — roadmap 1, 7
3. Move MyHealth decryption into HealthRepository — roadmap 1, 3, 9
4. Remove UserRepository→Activities forwarding (or 4-alt: Personals upload via UploadRepository) — roadmap 1, 3 / 5, 1
5. Purge TextUtils from Voices/Health repository impls — roadmap 1, 9
6. Extract TeamsUploadRunner from UploadManager — roadmap 5, 1
7. Narrow TagsRepository course-link API — roadmap 1, 9
8. Hoist dashboard survey count through DashboardViewModel — roadmap 3, 1, 10
9. Shelf-type dispatch map in SyncRepositoryImpl — roadmap 5, 1, 9
10. Drop android.util.Base64 from CoursesRepositoryImpl — roadmap 1, 9

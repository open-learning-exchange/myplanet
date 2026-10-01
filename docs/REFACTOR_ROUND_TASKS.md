# myPlanet refactor round — 10 work orders

> Generated 2026-10-01 by a task-generation session. Each task is a work order
> for an autonomous coding agent; tasks are independently mergeable in any
> order. Every file path, class, and function cited below was verified against
> the codebase before inclusion.

## Roadmap served

1. Finish cleaning the data layer
2. Introduce global navigation architecture
3. Expand viewmodel and use-case layers
4. Complete dependency-injection cleanup
5. Consolidate sync and upload workflow
6. Migrate UI incrementally to compose
7. Optimize remaining performance hotspots
8. Improve code health and add tests
9. (North star) Kotlin Multiplatform: a platform-free Kotlin core — repositories, models, sync/upload logic and use cases with zero `android.*` imports
10. (North star) Compose Multiplatform: every compose screen stays portable — state hoisted into viewmodels, no android views inside composables, no direct `R.*`

This round's focus: reinforcing repository boundaries between layers, calling
out cross-feature data leaks, tightening repository interfaces, moving data
functions one by one from UI/data/service into repositories, other DAO/Room
optimizations, and smoother repository↔viewmodel relationships.

## Open PRs checked — off-limits file inventory

| PR | Title | Off-limits files (app code only) |
|----|-------|----------------------------------|
| #17811 | mySurvey: survey number update | `base/BaseDashboardFragment.kt`, `ui/dashboard/DashboardViewModel.kt`, `ui/dashboard/DashboardViewModelTest.kt` |
| #17776 | showMemberDetails → MembersDetailViewModel | `base/BaseVoicesFragment.kt`, `model/JoinedMemberData.kt`, `repository/TeamsRepositoryImpl.kt`, `ui/community/CommunityLeadersAdapter.kt`, `ui/teams/members/*`, `ui/voices/ReplyActivity.kt`, `ui/voices/VoicesActions.kt`, `values*/strings.xml` |
| #17694 | Shared KMP module scaffold | `settings.gradle`, `app/build.gradle`, `gradle/libs.versions.toml`, `data/NetworkResult.kt`, `repository/PersonalsRepositoryImpl.kt`, `repository/UploadRepository*.kt`, `services/FileUploader.kt`, `services/upload/*` (all), `.github/workflows/test.yml` |
| #17680 | TeamResourcesAdapterTest fix | `ui/resources/ResourceCardHelper.kt`, `ui/teams/resources/TeamResourcesAdapter.kt` |
| #17435 | resources filter labels | `ui/resources/ResourcesFragment.kt`, `values*/strings.xml` |
| #17254 | team resources match library | `repository/ResourcesRepositoryImpl.kt`, `ui/resources/ResourcesAdapter.kt`, `ui/teams/resources/*`, `layout/row_team_resource.xml`, strings |
| #17187 | resources filter logic | `ui/notifications/NotificationsViewModel.kt`, `ui/resources/{ResourcesAdapter,ResourcesFilterFragment,ResourcesListFilter,ResourcesViewModel}.kt`, `utils/{FileUtils,LibraryTypeClassifier,MediumUtils}.kt`, strings |
| #16623 | interactive team tasks | `callback/OnTaskCompletedListener.kt`, `model/TeamTask.kt`, `repository/TeamsRepository{,Impl}.kt`, `ui/teams/tasks/*`, `layout/{fragment_teams_tasks,row_task}.xml`, strings |
| #16594, #17812 | CI/dependabot | `.github/**` |
| #15951 | teams repository update requesting | `data/room/AppDatabase.kt`, `model/{CreateTeamRequest,MyTeam,TeamDetails,TeamUpdateRequest}.kt`, `repository/TeamsRepository{,Impl}.kt`, `ui/teams/{PlanFragment,TeamFragment,TeamViewModel,TeamsAdapter}.kt`, `utils/TimeUtils.kt`, several layouts, strings |
| #15825 | task reminders | `MainApplication.kt`, `AndroidManifest.xml`, `data/room/AppDatabase.kt`, `data/room/dao/MeetupDao.kt`, `di/RepositoryModule.kt`, `model/TeamTask.kt`, `repository/{EventsRepositoryImpl,TeamsRepository,TeamsRepositoryImpl}.kt`, `services/TaskNotificationWorker.kt`, `services/reminders/*`, `ui/dashboard/DashboardActivity.kt`, `ui/teams/tasks/*`, `utils/{ActivityTracker,InAppNotificationHelper,NotificationUtils}.kt` |
| #15824 | gamification hub | `data/room/dao/{CourseProgressDao,NewsDao,OfflineActivityDao,SubmissionDao,TeamTaskDao}.kt`, `di/RepositoryModule.kt`, `model/gamification/*`, `repository/GamificationRepository*.kt`, `ui/user/{AchievementFragment,...}`, strings |
| #15820 | task/meetup comment threads | `data/room/dao/NewsDao.kt`, `repository/EventsRepository{,Impl}.kt`, `repository/TeamsRepository{,Impl}.kt`, `ui/events/EventsAdapter.kt`, `ui/teams/{InlineCommentsAdapter,TeamCalendarFragment,TeamCalendarViewModel}.kt`, `ui/teams/tasks/*` |
| #15808 | couchdb _changes sync | `data/room/AppDatabase.kt`, `di/{RoomModule,ServiceModule}.kt`, many DAO `deleteByIds` additions (`AchievementDao, CertificationDao, ChatDao, CourseDao, CourseProgressDao, ExamDao, FeedbackDao, HealthExaminationDao, MeetupDao, RatingDao, TagDao, TeamTaskDao, SyncCursorDao`), most `repository/*{,Impl}.kt` incl. `CoursesRepository{,Impl}, SurveysRepository{,Impl}, VoicesRepository{,Impl}, Health*, Progress*, Ratings*, Tags*, Submissions*, TeamsSyncRepository, UserSyncRepository, UserRepositoryImpl, Chat*, Community*, Feedback*`, `model/SyncCursor.kt`, `services/sync/{TransactionSyncManager,HeavyTableSyncWorker}.kt`, `services/ServerReachabilityWorker.kt` |
| #15559 | exam UI redesign | `ui/exam/ExamTakingFragment.kt`, exam layouts/drawables/strings |
| #15267 / #15266 | dialog cropping fixes | layout-only (download popup, team calendar layouts) |
| #15226 | Flutter port | `flutter/**`, root docs/config only — no Kotlin app files |
| #15108 | recurring event marking | `model/Meetup.kt`, `model/MeetupCreationParams.kt`, `repository/EventsRepository{,Impl}.kt`, `ui/events/EventsDetail{Fragment,ViewModel}.kt`, `ui/teams/TeamCalendar{Fragment,ViewModel}.kt`, `layout/add_meetup.xml` |
| #14883 | team leaderboards | `model/TeamLeaderboardEntry.kt`, `repository/{ProgressRepositoryImpl,SurveysRepository,SurveysRepositoryImpl}.kt`, `ui/teams/{TeamDetailFragment,TeamPageConfig}.kt`, `ui/teams/leaderboard/*` |
| #14650 | survey continue-progress | `model/AssignedSurvey.kt`, `repository/SurveysRepository{,Impl}.kt`, `ui/exam/ExamTakingFragment.kt`, `ui/submissions/{SubmissionUiModel,SubmissionViewModel,SubmissionsAdapter,SubmissionViewModelTest}` |
| #14427 | course streak | `repository/{ActivitiesRepository,ActivitiesRepositoryImpl,ProgressRepositoryImpl,SubmissionsRepositoryImpl}.kt`, `ui/courses/{CoursesAdapter,CoursesViewModel}.kt`, `ui/dashboard/{BellDashboardFragment,BellDashboardViewModel,DashboardViewModel}.kt`, `utils/StreakUtils.kt`, `layout*/card_profile_bell.xml` |
| #13928 | baseline profile | `app/build.gradle`, `build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle`, `baselineprofile/**` |
| #13848 | Course/Grade models | `ui/courses/CourseFilterController.kt`, `ui/exam/UserInformationFragment.kt`, `ui/resources/AddResourceActivity.kt`, `ui/user/{BecomeMemberActivity,UserProfileFragment}.kt`, strings |
| #13657 | archive course | `model/{Course,RealmMyCourse}.kt`, `repository/CoursesRepository{,Impl}.kt`, `ui/courses/{CourseFilterController,CourseSelectionController,CoursesFragment,CoursesViewModel}.kt`, `layout/fragment_my_course.xml` |
| #13604 | survey completeness sort | `ui/surveys/{SurveyFragment,SurveysViewModel}.kt`, strings |
| #13415 | voices emoji reactions | `base/BaseVoicesFragment.kt`, `data/{DatabaseService,RealmMigrations}.kt`, `model/RealmNews.kt`, `repository/VoicesRepository{,Impl}.kt`, `ui/resources/ResourcesViewModel.kt`, `ui/voices/{VoicesAdapter,VoicesFragment}.kt`, strings |
| #13355 | P2P sharing | `AndroidManifest.xml`, `callback/OnLibraryItemSelectedListener.kt`, `services/P2pTransferManager.kt`, `ui/resources/{P2pTransferActivity,ResourceDetailFragment,ResourcesAdapter,ResourcesFragment}.kt`, layouts |
| #13287 | edit-text char limit | `layout/activity_become_member.xml`, `layout/edit_profile_dialog.xml` |
| #10993 | voices video | `base/BaseVoicesFragment.kt`, `callback/OnNewsItemClickListener.kt`, `model/News.kt`, `repository/VoicesRepository{,Impl}.kt`, `services/UploadManager.kt`, `ui/teams/voices/*`, `ui/voices/{ReplyActivity,VoicesAdapter,VoicesFragment,VoicesViewModel}.kt`, voices layouts |
| #8175, #4075 | roboscript PRs | `.github/**` / docs only |

**Note:** open PRs collectively blanket the teams, voices, sync-feed, and
dashboard areas; tasks below deliberately avoid every file listed above.
`values*/strings.xml` is touched by many PRs, so no task adds user-facing
strings. No Compose runtime exists in the app module (`gradle/libs.versions.toml`
has no compose entries), so roadmap-6 work is represented by its prerequisite
(Compose-ready ViewModels) rather than composables.

---

## Task 1 — Split chat share operations into a narrow `ChatShareActions` interface

- **Roadmap:** 3 (use-case/interface segregation) + 4 (DI cleanup); moves 9 forward by shrinking the surface a future platform-free chat core must expose.
- **Problem:** `ChatViewModel` (`ui/chat/ChatViewModel.kt`) injects four repositories — `ChatRepository`, `TeamsRepository`, `VoicesRepository`, `UserRepository` — but uses `VoicesRepository` for only three methods (`getPlanetNewsMessages`, `isAlreadyShared`, `createNews`) and `TeamsRepository` for only three summary getters (`getTeamSummaries`, `getShareableEnterpriseSummaries`, `getTeamSummaryById`). The full `TeamsRepository` surface is under heavy churn in open PRs, and the full `VoicesRepository` couples chat sharing to the voices upload/read pipeline.
- **Work:** Create `repository/ChatShareActions.kt` declaring exactly the six methods above. Leave `VoicesRepository`/`TeamsRepository` interfaces unchanged; have `VoicesRepositoryImpl` and `TeamsRepositoryImpl` *also* implement the new narrow interface (the methods already exist — zero new logic), add a `@Binds` in `di/RepositoryModule.kt`. If no single impl covers all six methods, split into `ChatShareActions` (voices: `getPlanetNewsMessages`/`isAlreadyShared`/`createNews`) and `ChatShareTargets` (teams: the three summary getters) and bind each. Update `ChatViewModel` to inject the narrow interface(s) instead of the two fat repositories. Update `ui/chat/ChatViewModelTest.kt` mocks accordingly.
- **Files (≤5):** `repository/ChatShareActions.kt` (new), `repository/VoicesRepositoryImpl.kt`, `repository/TeamsRepositoryImpl.kt` (class declaration lines only), `ui/chat/ChatViewModel.kt`, `di/RepositoryModule.kt` (+ `ChatViewModelTest.kt` if lines allow).
- **Acceptance:** `ChatViewModel` no longer imports `VoicesRepository`/`TeamsRepository`; no repository interface gains or loses behavior; existing `ChatViewModelTest` passes with narrowed mocks.
- **Merge safety:** `TeamsRepositoryImpl.kt` is touched by PRs #16623/#15820/#15825/#15951 — if that is disqualifying, drop the teams half and narrow only the voices dependency (`ChatViewModel` keeps `TeamsRepository`, gains `ChatShareActions` for the voices calls). Either variant stays under the file/line budget.

## Task 2 — Narrow the `VoicesRepository`/`UserRepository` dependencies in `NotificationsRepositoryImpl`

- **Roadmap:** 1 (data-layer boundary) + 4 (DI cleanup).
- **Problem:** `NotificationsRepositoryImpl` already consumes the narrow `TeamsNotificationsRepository` (good precedent), but still injects `Lazy<UserRepository>` and the full `VoicesRepository`, using the latter only for `countTopLevelByTeams` (`repository/NotificationsRepositoryImpl.kt`). Cross-feature reads from notifications into voices news counts is exactly the kind of data leak this round targets.
- **Work:** Introduce `repository/VoicesCountsReader.kt` with the single method `suspend fun countTopLevelByTeams(teamIds: List<String>): Map<String, Long>`; have `VoicesRepositoryImpl` implement it (method exists); bind in `di/RepositoryModule.kt`; swap the constructor param in `NotificationsRepositoryImpl` from `VoicesRepository` to the new interface. Do the same for the `UserRepository` usage: verify which `UserRepository` methods `NotificationsRepositoryImpl` calls (`getUserById`, `getUsersByIds`); if both are already exposed on an existing narrow interface, use it — otherwise declare `NotificationUsersReader` in the same new file with just those two, implement in `UserRepositoryImpl`, bind in `di/RepositoryModule.kt`.
- **Files:** `repository/VoicesCountsReader.kt` (new, may also host `NotificationUsersReader`), `repository/NotificationsRepositoryImpl.kt`, `repository/VoicesRepositoryImpl.kt`, `repository/UserRepositoryImpl.kt`, `di/RepositoryModule.kt`.
- **Acceptance:** `NotificationsRepositoryImpl` imports neither `VoicesRepository` nor `UserRepository`; constructor takes only narrow reader interfaces; existing notifications repository tests compile/pass with mock updates.
- **Merge safety:** `VoicesRepositoryImpl`/`UserRepositoryImpl` changes are one-line class-declaration additions; no method bodies change. Conflicts with open PRs touching those files are trivially resolvable and behavior-free.

## Task 3 — Move `ReplyActivity`'s member-detail data fetch out of the activity

- **Roadmap:** 3 (viewmodel/use-case layer) + 10 (state hoisted so a future compose screen needs no repository handle).
- **Problem:** `ui/voices/ReplyActivity.kt` injects `ActivitiesRepository` (field `activitiesRepository`) and calls `VoicesActions.showMemberDetails(userModel, activitiesRepository)` directly from `onMemberSelected`, and also injects the full `VoicesRepository` merely to pass as `voicesEditActions`. The activity is doing cross-feature data assembly (activities + voices + user) inline.
- **Work:** Add a `showMemberDetails(userModel: UserEntity?)` method to the existing `ReplyViewModel` (already injected via `by viewModels()`) that internally uses an injected `ActivitiesRepository` and returns whatever `VoicesActions.showMemberDetails` needs (or have the ViewModel expose a `MemberDetailState` consumed by the activity). Remove the `@Inject lateinit var activitiesRepository` field from `ReplyActivity`. Retype the `voicesRepository` field as `VoicesEditActions`, since only `voicesEditActions = voicesRepository` uses it.
- **Files:** `ui/voices/ReplyActivity.kt`, `ui/voices/ReplyViewModel.kt`, `ui/voices/VoicesActions.kt` only if its signature must accept the ViewModel-produced value (keep unchanged if possible).
- **Acceptance:** `ReplyActivity` has no `ActivitiesRepository` field and no `VoicesRepository`-typed field; `ReplyViewModel` owns the member-details data path; unit test for the new ViewModel method added or existing `ReplyViewModel` test extended.
- **Merge safety:** PR #17776 touches `ReplyActivity.kt` and `VoicesActions.kt` — if the collision is judged blocking, use Task 3b instead. Choose one, not both.

## Task 3b (alternative to 3 — pick exactly one) — Drop the redundant repository fields from `ProcessUserDataActivity`

- **Roadmap:** 5 (sync/upload consolidation) + 3.
- **Problem:** `ui/sync/ProcessUserDataActivity.kt` field-injects `SyncRepository` and `UserRepository` while its `UserUploadViewModel` already wraps both (`uploadLoginData()`, `uploadBulkData()`, `fetchUserSecurityData()`). Verify no direct `syncRepository.`/`userRepository.` calls remain in the activity; migrate any stragglers into `UserUploadViewModel`, then delete the unused injected fields.
- **Work:** Grep the activity for direct uses; migrate stragglers to `UserUploadViewModel`; remove the two `@Inject` fields and imports; extend `UserUploadViewModel` only if a call has no existing wrapper.
- **Files:** `ui/sync/ProcessUserDataActivity.kt`, `ui/sync/UserUploadViewModel.kt`, and its test if it exists.
- **Acceptance:** activity compiles with zero repository fields; all upload entry points flow through `UserUploadViewModel`.

## Task 4 — Give `CoursesRepository.getMyCoursesFlow` a proper (non-suspend) return type

- **Roadmap:** 3 (smoother repository↔viewmodel modelling) + 9 (reactive read API a platform-free core can expose without suspend-polling).
- **Problem:** `CoursesRepository.getMyCoursesFlow(userId: String): Flow<List<MyCourse>>` is declared `suspend fun` (`repository/CoursesRepository.kt`) and its impl (`CoursesRepositoryImpl.getMyCoursesFlow`) builds the DAO flow inside a suspend function. Room `Flow` queries are non-suspend by convention; a `suspend` flow-getter forces callers into needless coroutine launches and mis-models cold streams.
- **Work:** Change the interface declaration and `CoursesRepositoryImpl` override to plain `fun … : Flow<List<MyCourse>>` (the body already just wraps `courseDao.observeForUserPattern(...)` — the `suspend` wrapper is removable). Verify by compiling; adjust only call sites that break and are not off-limits (`ui/dashboard/DashboardViewModel.kt` is off-limits via PRs #17811/#14427 — leave it untouched; a `suspend fun`→`fun` change is source-compatible for callers that invoke it inside a coroutine).
- **Files:** `repository/CoursesRepository.kt`, `repository/CoursesRepositoryImpl.kt`, plus the courses repository test files under `app/src/test` if they stub the method.
- **Acceptance:** method is non-suspend in interface and impl; no behavior change; unit tests updated and passing.
- **Merge safety:** `CoursesRepository{,Impl}.kt` are touched by open PRs #13657 and #15808 — if that blocks, use Task 4b instead.

## Task 4b (alternative to 4 — pick exactly one) — Replace `ChatSearch`'s hard-coded dispatcher with `DispatcherProvider`

- **Roadmap:** 7 (performance hygiene) + 8 (code health/testability).
- **Problem:** `utils/ChatSearch.kt` references `Dispatchers.` directly (one of only two files outside `DispatcherProvider.kt` that do). Hard-coded dispatchers bypass the injectable `DispatcherProvider` convention and make chat search untestable with deterministic dispatchers.
- **Work:** Thread `DispatcherProvider` into `ChatSearch` (constructor param if it's a class, function param if object methods — match how `ChatViewModel` already injects `DispatcherProvider`), replace the `Dispatchers.*` references, and update `ChatViewModel`/tests that construct it. Add/adjust a unit test under `app/src/test/.../utils/` using `TestDispatcherProvider`.
- **Files:** `utils/ChatSearch.kt`, `ui/chat/ChatViewModel.kt` (only if the construction site changes), one test file.
- **Acceptance:** no `Dispatchers.` references remain in `ChatSearch.kt`; chat search tests run on test dispatchers.

## Task 5 — Prune proven-unused methods from the dictionary data path

- **Roadmap:** 1 (finish cleaning the data layer) + 8 (code health).
- **Problem:** The codebase migrated dictionary access to `DictionaryDao`/`DictionaryRepository` (`count`, `findByWord`, `insertDictionaryData`). `repository/DictionaryMapper.kt` and the dictionary DAO/impl may carry dead helpers left over from the Room migration.
- **Work:** Grep each public function of `DictionaryDao`, `DictionaryRepository`, `DictionaryRepositoryImpl`, and `DictionaryMapper` for call sites outside its own file. For each confirmed-unused symbol, delete it from the DAO interface, repository interface, and impl, and delete its now-dead tests; do not touch anything with a live call site. Keep deletions to methods proven unused, with the grep output quoted in the PR description.
- **Files:** `data/room/dao/DictionaryDao.kt`, `repository/DictionaryRepository.kt`, `repository/DictionaryRepositoryImpl.kt`, `repository/DictionaryMapper.kt`, plus deletion of matching test functions in the existing dictionary test file.
- **Acceptance:** every deleted symbol is shown unused by grep; build and dictionary tests pass; no interface method removed that any UI/service file calls.

## Task 6 — Extract submission upload-payload serialization into a `SubmissionsUploadSerializer` collaborator

- **Roadmap:** 1 + 3; helps 9 by isolating serialization logic from DAO plumbing.
- **Problem:** `repository/SubmissionsRepositoryImpl.kt` (~941 lines) mixes three concerns: Room reads/writes, exam-session orchestration (`createExamSubmission`/`saveExamAnswer` flows used by `ExamTakingFragment`), and upload payload serialization (`getExamUploadPayload`, `serializeSubmission`). The codebase already has the `SubmissionsRepositoryExporter` precedent for carving out a concrete collaborator.
- **Work:** Create `repository/SubmissionsUploadSerializer.kt` (concrete `@Inject` class, mirroring `SubmissionsRepositoryExporter`) and move `getExamUploadPayload` + `serializeSubmission` bodies into it unchanged, injecting the DAOs they need; `SubmissionsRepositoryImpl` delegates. Do not touch `createExamSubmission`/`saveExamAnswer` in this task. Keep moved code byte-for-byte where possible.
- **Files:** `repository/SubmissionsUploadSerializer.kt` (new), `repository/SubmissionsRepositoryImpl.kt`, and the existing submissions repository test file updated for the delegation.
- **Acceptance:** `SubmissionsRepositoryImpl` loses ~100+ lines; public `SubmissionsRepository` interface unchanged; all existing submissions tests pass.
- **Merge safety:** `SubmissionsRepositoryImpl.kt` is touched by PRs #14427 and #15808 — if blocking, defer rather than re-scope; the fallback task is Task 4b (collision-free).

## Task 7 — Route all `DictionaryActivity` data access through `DictionaryViewModel`

- **Roadmap:** 3 + 1.
- **Problem:** `ui/dictionary/DictionaryActivity.kt` predates the current ViewModel pattern and accesses the dictionary DAO directly. `ui/dictionary/DictionaryViewModel.kt` already exists and injects the repository.
- **Work:** Read `DictionaryActivity.kt`; for each direct DAO/repository call, add a matching suspending wrapper in `DictionaryViewModel.kt` and replace the call site; remove any `@Inject` repository/DAO fields from the activity.
- **Files:** `ui/dictionary/DictionaryActivity.kt`, `ui/dictionary/DictionaryViewModel.kt`, existing dictionary ViewModel test if present.
- **Acceptance:** `DictionaryActivity` contains no `Dao`/`Repository` references; the ViewModel exposes the needed operations; existing tests pass.
- **Merge safety:** no open PR touches `ui/dictionary/`.

## Task 8 — Replace `NotificationUtils`' hidden singleton entry-point fetch with an injectable binding

- **Roadmap:** 4 (DI cleanup).
- **Problem:** `utils/NotificationUtils.kt` keeps a private `notificationManagerInstance` singleton and fetches `TimeProvider` via `EntryPointAccessors.fromApplication(...)` inside `getInstance(context)` — a service-locator pattern that hides a dependency. (`NetworkUtils.kt` uses the same pattern but is heavily entangled; out of scope.)
- **Work:** Add `@Provides fun provideNotificationManager(@ApplicationContext context: Context, timeProvider: TimeProvider)` to `di/ServiceModule.kt`, change `NotificationUtils.getInstance` callers that are Hilt-injectable to receive it via injection (workers that legitimately use entry points stay as-is), and delete the cached-singleton + `EntryPointAccessors` code path from `NotificationUtils.kt`. Document any remaining non-injectable caller in the PR body.
- **Files:** `utils/NotificationUtils.kt`, `di/ServiceModule.kt`, plus the 1–2 caller files that can take constructor injection (verify via grep on `NotificationUtils.getInstance`), plus `utils/NotificationUtilsTest.kt` updates.
- **Acceptance:** no `EntryPointAccessors` reference remains in `NotificationUtils.kt`; singleton cache deleted; notification tests pass with an injected fake `TimeProvider`.
- **Merge safety:** PR #15825 touches `NotificationUtils.kt` — if blocking, use Task 8b instead.

## Task 8b (alternative to 8 — pick exactly one) — Narrow one single-method repository dependency in `TeamsVoicesViewModel`

- **Roadmap:** 4 + 3.
- **Problem:** `ui/teams/voices/TeamsVoicesViewModel.kt` injects eight repositories — the fattest ViewModel constructor outside the dashboard. Several are used for a single method each.
- **Work:** Identify the single-method dependency with the cleanest extraction, then follow the Task 1 pattern: a new narrow interface in `repository/` named for the use case, implemented by the relevant existing `Impl` (methods already exist), bound in `di/RepositoryModule.kt`, consumed by `TeamsVoicesViewModel`.
- **Files:** one new interface file, one `*Impl.kt` one-line change, `di/RepositoryModule.kt`, `ui/teams/voices/TeamsVoicesViewModel.kt`, its test.
- **Acceptance:** one fewer fat-repository dependency in `TeamsVoicesViewModel`; tests updated.

## Task 9 — Add unit tests for `ReplyViewModel.getNewsWithReplies`

- **Roadmap:** 8 (tests) + 3.
- **Problem:** `ui/voices/ReplyActivity` calls `viewModel.getNewsWithReplies(id)` (a `ReplyViewModel` method returning `Pair<News?, List<News>>`); there is no `ReplyViewModelTest` under `app/src/test/java/org/ole/planet/myplanet/ui/voices/`, so the reply thread's core read (parent news + sorted replies, including the deleted/missing-parent case) is untested.
- **Work:** Write `app/src/test/java/org/ole/planet/myplanet/ui/voices/ReplyViewModelTest.kt` using the standard stack (`runTest`, `MainDispatcherRule`, `TestDispatcherProvider`, MockK): cover (a) parent found with replies ordered, (b) parent missing → null parent + empty list, (c) repository error → the ViewModel's existing error path. Test-only change; no production edits — unless MockK stubbing reveals the `ReplyViewModel` constructor is untestable as-is (e.g. missing `DispatcherProvider` injection), in which case include the minimal production change to make it injectable.
- **Files:** `app/src/test/java/org/ole/planet/myplanet/ui/voices/ReplyViewModelTest.kt` (new).
- **Acceptance:** new test class runs green under `./gradlew testDefaultDebugUnitTest`; mirrors existing `ui/voices` test conventions.
- **Merge safety:** no open PR touches `ReplyViewModel` or this test path (PR #17776 touches only `VoicesActionsTest` — a different file).

## Task 10 — Add unit tests for `SyncConfigurationCoordinator` failure branches

- **Roadmap:** 8 + 5 (sync consolidation confidence).
- **Problem:** `ui/sync/SyncConfigurationCoordinator.kt` drives server selection and calls `configurationsRepository.getMinApk(url, pin)`, branching on `ConfigurationsRepository.ConfigurationResult.Success/Failure` — the highest-stakes offline-first path (a wrong branch strands a device against an unreachable server).
- **Work:** Check `app/src/test/.../ui/sync/` for existing coordinator coverage; add tests only for uncovered branches: (a) `Failure` result propagates the error message to the coordinator's callback/state; (b) `Success` with `isAlternativeUrl = true` takes the mapped-URL path; (c) an exception from `getMinApk` is contained. Use MockK + `runTest` per `docs/TESTING.md`. Test-only.
- **Files:** one test file under `app/src/test/java/org/ole/planet/myplanet/ui/sync/` (new, or extend the existing coordinator test if present).
- **Acceptance:** the three branches are covered and green in `testDefaultDebugUnitTest`.
- **Merge safety:** no open PR touches `SyncConfigurationCoordinator.kt` or its tests.

---

## Self-check results

- **Exactly 10 tasks** (1, 2, 3-or-3b, 4-or-4b, 5, 6, 7, 8-or-8b, 9, 10), each with explicit alternatives so exactly one variant is executed per pair; tasks are order-independent.
- **No file overlap** between tasks' primary variants; the only shared-file alternatives (1/8b one-line `TeamsRepositoryImpl.kt` class-declaration changes) produce trivially resolvable conflicts.
- **Open PRs respected:** two initially-drafted tasks (a pending-survey-count DAO flow; a News table index) were discarded because every file they needed is off-limits.
- **Verified citations:** every cited path/class/function was opened or grepped during generation: `ChatViewModel.kt` (4-repo constructor), `NotificationsRepositoryImpl.kt`, `TeamsNotificationsRepository.kt`, `VoicesRepository.kt`/`VoicesEditor.kt`, `ReplyActivity.kt`, `ProcessUserDataActivity.kt`, `UserUploadViewModel.kt`, `CoursesRepository.getMyCoursesFlow` + impl, `ChatSearch.kt`, `DictionaryDao.kt`/`DictionaryRepository.kt`, `SubmissionsRepositoryImpl.kt` + `SubmissionsRepositoryExporter.kt`, `ui/dictionary/` package, `NotificationUtils.kt`, `SyncConfigurationCoordinator.kt`.
- **Size budget:** each task is ≤5 files and well under ~150 changed lines (largest: Task 6's byte-preserving move); no new dependencies; no TODO placeholders.

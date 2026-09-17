# Repository boundary work orders

date: 2026-09-17  
base commit: `fcb4c53b2d1c4134789f1c07bd785d97d7f4215b`  
open PRs checked: could not check open PRs (GitHub CLI had no authentication and the GitHub API was unavailable); accordingly, these work orders avoid the large repository, sync/upload-manager, global-navigation, and Compose hot areas rather than guessing about ownership.

### 1. Make challenge progress formatting independent of the dashboard ViewModel (roadmap 3+8+9)
context: `ChallengePrompter.showChallengeDialog` reaches from a service helper into `DashboardViewModel.calculateCommunityProgress` and `calculateIndividualProgress` at `app/src/main/java/org/ole/planet/myplanet/services/ChallengePrompter.kt:41-42` and again at lines 55-56. That service-to-UI dependency reverses the intended layer direction and makes the otherwise deterministic progress calculation difficult to reuse or test; extracting a platform-free calculation also advances roadmap 9.
files: `app/src/main/java/org/ole/planet/myplanet/services/ChallengePrompter.kt` — `ChallengePrompter.showChallengeDialog`; `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt` — `calculateCommunityProgress` and `calculateIndividualProgress`. Leave `DashboardActivity`, `ChallengeDialogData`, and `MarkdownDialogFragment` alone.
steps:

1. Move the two deterministic progress calculations out of the `DashboardViewModel` instance API into an internal, platform-free function or value object colocated in `ChallengePrompter.kt`.
2. Change `ChallengePrompter` to accept only `DashboardActivity` and `SharedPrefManager`, and call the extracted calculation directly when formatting both dialog variants.
3. Update `DashboardViewModel` to call the same extracted calculation wherever it still needs those values, then remove the duplicated ViewModel methods if no callers remain.
4. Remove obsolete imports and constructor wiring without changing the existing Spanish completion check, caps, or markdown text.
5. Add focused unit coverage only if it fits in one of the two named files; otherwise rely on the existing dashboard test suite and do not create a third file.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; opening the dashboard challenge shows the same community and individual earnings for incomplete and completed challenges, and the congratulations dialog still appears only once.
size budget: about 45 changed lines across 2 files.
out of scope: Do not redesign challenge copy, persistence, or dialogs. Do not introduce a new use-case framework or Android resource abstraction.

---

### 2. Put next-step resource prefetch behind ResourcesRepository (roadmap 1+3+5+9)
context: `CoursesStepsViewModel.loadStep` performs filesystem path lookup and directly orchestrates `ResourceDownloadCoordinator.startBackgroundDownload` at `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:72-80` and lines 94-103. The repository already owns `getAllStepResources` and `downloadResourcesPriority`, so the ViewModel should request a domain operation instead of assembling URLs and invoking a service; removing `Context` and `ResourceDownloadCoordinator` from the ViewModel moves its state logic toward roadmap 9.
files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` — constructor and `loadStep`; `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt` — `ResourcesRepository`; `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — `getAllStepResources` and `downloadResourcesPriority`. Leave `TakeCourseFragment`, `ResourceDownloadCoordinator`, and `CoursesRepositoryImpl` alone.
steps:

1. Add one narrowly named repository operation that accepts a next-step id, resolves its resources, filters already-offline entries, and schedules the same priority/background download behavior.
2. Implement it by composing the existing repository resource lookup and download code; preserve the current empty-id and empty-resource no-op behavior.
3. Replace the nested next-step coroutine in `loadStep` with that repository call and remove `UrlUtils` plus the coordinator dependency from the ViewModel.
4. Move construction of the local markdown base path behind an existing repository-returned path or a small repository method so the ViewModel no longer injects `Context`.
5. Keep `CourseStepUiState.isDownloadingResources` semantics unchanged and clean all now-unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; opening a course step still renders local markdown images, downloads missing current-step resources, and quietly prefetches missing resources for the next step.
size budget: about 80 changed lines across 3 files.
out of scope: Do not change download notifications, URL mapping, storage layout, or course navigation. Do not add a generic download use-case layer.

---

### 3. Observe chat history through ChatRepository instead of the sync service (roadmap 1+3+5+9)
context: `ChatViewModel` injects `RealtimeSyncManager` and translates the global `"chats"` table event into a refresh signal at `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:49-76`. `ChatRepository.getChatHistoryForUser` currently exposes only a snapshot while `ChatRepositoryImpl` reads `ChatDao` at `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt:123-129`; a repository-owned flow would hide table names and sync infrastructure from UI state, advancing roadmap 9 at the interface boundary.
files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` — constructor, initializer, and `loadChatHistoryScreenData`; `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt` — `ChatRepository`; `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt` — `getChatHistoryForUser` and `sortChats`. Leave `ChatDao`, `ChatHistoryFragment`, and `RealtimeSyncManager` alone.
steps:

1. Add a repository flow for one user's sorted chat history while retaining the snapshot method for existing callers.
2. Implement the flow from the DAO's observable query if present; if the DAO only has a snapshot, confine the minimal observable adapter to `ChatRepositoryImpl` without exposing Room or table names.
3. Remove `RealtimeSyncManager` from `ChatViewModel` and drive refreshed `screenData`, `allChats`, and `filteredChats` from the repository flow for the active user.
4. Preserve the current search query when a database emission arrives rather than resetting the visible list unconditionally.
5. Keep `refreshChatSignal` only if a fragment still requires it for rendering; emit it from repository data changes, not sync-table events.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; chat history updates after a sync-created Room change and an active chat search remains applied to the refreshed rows.
size budget: about 90 changed lines across 3 files.
out of scope: Do not modify AI provider networking, pagination, chat sharing, `ChatDao`, or the sync protocol.

---

### 4. Remove sync-table events from team list view modelling (roadmap 1+3+5+9)
context: `TeamViewModel.getTeamUpdateFlow` exposes `RealtimeSyncManager.updatesFor("teams")` directly at `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt:29-41`, even though `loadTeams` already collects `TeamsRepository.getMyTeamDetailsFlow` at lines 63-77. This duplicates refresh paths and leaks a storage table name into the UI layer; relying on repository observation gives smoother list updates and a cleaner platform-facing ViewModel contract.
files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt` — constructor, `getTeamUpdateFlow`, and `loadTeams`; `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt` — the collector of `getTeamUpdateFlow`. Leave `TeamsRepository`, `TeamsRepositoryImpl`, `TeamDetailFragment`, and team member screens alone.
steps:

1. Delete the realtime-sync dependency and `getTeamUpdateFlow` pass-through from `TeamViewModel`.
2. Ensure every branch of `loadTeams` collects an existing repository flow rather than taking a one-time repository snapshot when a user id is absent.
3. If only a snapshot exists for a branch, refresh that branch from an explicit ViewModel event already owned by `TeamFragment`; do not expose `TableDataUpdate` or table strings.
4. Remove the fragment's sync-event collector and retain one initial `loadTeams` call plus the existing filter/search calls.
5. Verify job cancellation still prevents duplicate collectors when type, dashboard mode, or user changes.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; team and enterprise lists still load, search, and reflect locally inserted or synced teams without duplicate rows or visible flicker.
size budget: about 55 changed lines across 2 files.
out of scope: Do not alter `TeamsRepositoryImpl`, team membership behavior, navigation, or the database schema.

---

### 5. Let the feedback repository flow be the only list refresh source (roadmap 1+3+8+9)
context: `FeedbackListViewModel.loadFeedback` already continuously collects `FeedbackRepository.getFeedback` at `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt:32-39`. Nevertheless, `FeedbackListFragment` injects `RealtimeSyncManager`, implements `RealtimeSyncMixin`, and restarts that collector for `"feedback"` events at `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt:23-57` and lines 97-107, creating redundant work and coupling presentation to sync internals.
files: `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt` — class declaration, `setupRealtimeSync`, and `onDataUpdated`; `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt` — `loadFeedback` and `refreshFeedback`. Leave `FeedbackRepository`, `FeedbackFragment`, and `RealtimeSyncHelper` alone.
steps:

1. Remove `RealtimeSyncMixin`, the injected manager, helper field, watched-table methods, and setup call from the fragment.
2. Keep the ViewModel's single repository-flow collection alive for its lifetime instead of canceling and restarting it for database invalidations.
3. Retain `refreshFeedback` only for the explicit `OnChangedListener` submission callback, or make it a no-op-safe refresh that cannot create parallel collectors.
4. Preserve adapter submission, scroll-to-top behavior, empty messaging, and header visibility.
5. Remove all unused sync and table-update imports.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; newly submitted feedback and feedback arriving through sync appear once, with the newest item at the top and correct empty-state headers.
size budget: about 35 changed lines across 2 files.
out of scope: Do not change feedback creation, upload status, sorting SQL, or shared realtime-sync utilities.

---

### 6. Centralize survey reload state in SurveysViewModel (roadmap 1+3+8+9)
context: `SurveyFragment` injects `RealtimeSyncManager` and installs `RealtimeSyncHelper` at `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt:36-49` and lines 90-106, while `SurveysViewModel.loadSurveys` separately owns the team/adoptable selection at `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt:78-103`. The fragment therefore knows both sync storage details and repository reload policy, which makes state restoration brittle and blocks a portable presentation layer.
files: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt` — class declaration and `onViewCreated`; `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt` — `loadSurveys`, current team/filter fields, and adoption completion path. Leave `SurveysRepository`, `SubmissionsRepository`, `SurveysAdapter`, and `RealtimeSyncHelper` alone.
steps:

1. Remove realtime-sync injection and mixin setup from the fragment so it sends only user intents: initial scope, search, sort, team/adoptable selection, and adoption.
2. Make `loadSurveys` idempotently retain the active scope and cancel any prior load job before starting another repository read.
3. After `adoptSurvey` succeeds, reload using the retained scope inside the ViewModel rather than relying on a sync callback into the fragment.
4. Prevent stale slower loads from overwriting a newer radio-button selection, and retain the current search and sort when data reloads.
5. Remove obsolete table-update and realtime-sync imports without changing snackbar event handling.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; switching rapidly between team and adoptable surveys shows the final selection, and adopting a survey refreshes the list while preserving search and sort.
size budget: about 70 changed lines across 2 files.
out of scope: Do not change survey submission forms, repository SQL, adapter row layout, or team sharing rules.

---

### 7. Route health-record refreshes through HealthViewModel (roadmap 1+3+8+9)
context: `MyHealthFragment.setupRealtimeSync` consumes every `RealtimeSyncManager.dataUpdateFlow` event and conditionally calls `HealthViewModel.refreshSelectedPatient` at `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:256-264`. The ViewModel already owns `currentPatientId`, cancellation, and repository reads in `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:90-133`, so refresh policy belongs there rather than in an Android fragment observing sync infrastructure.
files: `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` — injected sync manager and `setupRealtimeSync`; `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt` — `refreshSelectedPatient` and `fetchPatientData`. Leave `HealthRepository`, `HealthExaminationActivity`, and health adapters alone.
steps:

1. Remove direct `RealtimeSyncManager` injection and table-event collection from `MyHealthFragment`.
2. Add a single explicit ViewModel refresh intent for lifecycle resume or completed editor results, using the already tracked patient id.
3. Coalesce repeated refresh intents while `selectPatientJob` is active so only the latest requested patient is fetched.
4. Preserve the selected patient on transient repository failure instead of clearing selection solely because a refresh failed.
5. Keep initial logged-in-user selection, loading indicators, horizontal record scrolling, and provider-only controls unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; selecting a patient then returning from add/edit refreshes that same patient's records once, and rotation does not reset to the logged-in user.
size budget: about 55 changed lines across 2 files.
out of scope: Do not change health encryption, examination validation, DAO queries, or layouts.

---

### 8. Move retry HTTP execution behind RetryRepository (roadmap 1+4+5+9)
context: `RetryQueueWorker` injects `ApiInterface` directly at `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt:32-41` and combines transport execution with queue state transitions. `RetryRepository` currently owns enqueue, pending selection, attempts, and completion at `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt:8-35`, so making it the worker's complete retry boundary consolidates workflow policy and removes a data-source leak.
files: `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt` — constructor and per-operation execution; `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt` — `RetryRepository`; `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` — queue operations and new execution method. Leave `ApiInterface`, `RetryDao`, `UploadCoordinator`, and DI modules alone.
steps:

1. Define one repository result type that distinguishes success, retryable transport failure, and terminal failure without exposing Retrofit response classes.
2. Move endpoint/method dispatch and HTTP response classification from the worker into `RetryRepositoryImpl`, preserving supported methods, payloads, and status handling.
3. Replace the worker's `ApiInterface` dependency with the repository operation and keep batching plus WorkManager result selection in the worker.
4. Ensure cancellation propagates and is never converted into a failed queue attempt.
5. Keep queue state transitions exactly once per operation and remove obsolete networking imports from the worker.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; a queued operation succeeds and is completed, a retryable response remains eligible, and a terminal response records one failed attempt without duplicate HTTP calls.
size budget: about 120 changed lines across 3 files.
out of scope: Do not change retry backoff constants, database schema, upload payload formats, or WorkManager scheduling.

---

### 9. Batch successful achievement upload acknowledgements (roadmap 1+5+7+9)
context: `AchievementUploader.uploadAchievement` calls `UserRepository.markAchievementUploaded` inside its network-result loop at `app/src/main/java/org/ole/planet/myplanet/services/upload/AchievementUploader.kt:25-45`, producing one Room update per successful document. The call passes through `UserRepositoryImpl` to `AchievementDao.markUploaded` at `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:1051-1053` and `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AchievementDao.kt:22-23`; a repository batch keeps persistence out of the uploader and reduces transaction churn.
files: `app/src/main/java/org/ole/planet/myplanet/services/upload/AchievementUploader.kt` — `uploadAchievement`; `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` — `markAchievementUploaded`; `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` — `markAchievementUploaded`; `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AchievementDao.kt` — `markUploaded`. Leave `UploadRepository`, CV attachment upload, achievement UI files, and database entities alone.
steps:

1. Introduce a small platform-free acknowledgement value carrying achievement id and nullable revision at the repository boundary.
2. Replace the single-item repository method with a batch method, and implement one DAO transaction that applies all successful acknowledgements.
3. Accumulate only successful document uploads in `uploadAchievement`; flush them once after the upload loop, including successes whose optional CV attachment later fails.
4. Preserve per-document exception isolation and do not acknowledge failed or malformed responses.
5. Keep CV attachment upload immediately after each successful document because it requires that document's returned revision.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; multiple successful achievements are marked clean together, failed documents remain pending, and a failed CV attachment does not cause the already-saved achievement document to upload again.
size budget: about 75 changed lines across 4 files.
out of scope: Do not parallelize network uploads, change CouchDB URLs, alter CV files, or add a migration.

---

### 10. Fetch one pending submission directly instead of loading a list (roadmap 1+7+9)
context: `SubmissionsRepositoryImpl` calls `getSubmissionsByParentId(..., "pending").firstOrNull()` at `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:456` and again around line 541, forcing Room to materialize and hydrate a list when only one newest pending submission is required. `SubmissionDao` already demonstrates the intended bounded-query shape in `getLatestPendingByUserAndParent` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt:28`.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt` — `getLatestPendingByUserAndParent`; `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` — the two pending-submission `firstOrNull` paths. Leave `SubmissionsRepository`, `AnswerDao`, `SubmissionsRepositoryExporter`, and upload services alone.
steps:

1. Compare both list-first call sites' nullable user and parent-id semantics with the existing bounded DAO query.
2. Reuse `getLatestPendingByUserAndParent` where its ordering matches; otherwise add one precisely ordered `LIMIT 1` DAO query rather than changing broad list semantics.
3. Hydrate only the returned submission through `hydrateSubmission` so answers remain available to downstream logic.
4. Preserve fallback behavior when no pending row exists and preserve the current `lastUpdateTime` winner.
5. Remove only the now-redundant list materialization at those two call sites.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew lintDefaultDebug` passes; resuming a pending survey/exam chooses the same newest submission, includes its answers, and still creates or falls back correctly when none exists.
size budget: about 25 changed lines across 2 files.
out of scope: Do not change submission status rules, delete behavior, answer persistence, PDF export, or database schema.

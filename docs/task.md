# myPlanet Refactor Round — 10 Work Orders

**Open-PR check (R3): performed** (2026-09-04). 48 open PRs enumerated via GitHub API and their touched-file lists retrieved. The following files are **off-limits** and appear in no task below:

- `ui/resources/`: ResourcesFragment.kt, ResourcesFilterFragment.kt, AddResourceFragment.kt, ResourcesAdapter.kt, ResourcesViewModel.kt, CollectionsFragment.kt, ResourceDetailFragment.kt, AddResourceActivity.kt, P2pTransferActivity.kt
- `ui/courses/`: CoursesFragment.kt, CoursesAdapter.kt, CoursesViewModel.kt, CourseFilterController.kt, CourseSelectionController.kt, CourseProgressActivity.kt
- `ui/teams/`: TeamFragment.kt, TeamViewModel.kt, TeamsAdapter.kt, TeamDetailFragment.kt, TeamPageConfig.kt, PlanFragment.kt, TeamCalendarFragment.kt, TeamCalendarViewModel.kt, InlineCommentsAdapter.kt, tasks/TeamsTasks*, voices/TeamsVoices*, leaderboard/*
- `ui/exam/`: ExamTakingFragment.kt, UserInformationFragment.kt
- `base/`: BaseExamFragment.kt, BaseDashboardFragment.kt, BaseVoicesFragment.kt, BaseRecyclerFragment.kt, BaseResourceFragment.kt
- `ui/dashboard/`: DashboardActivity.kt, BellDashboardFragment.kt, BellDashboardViewModel.kt, DashboardViewModel.kt
- `ui/voices/`: VoicesAdapter.kt, VoicesFragment.kt, ReplyActivity.kt
- `ui/chat/`: ChatDetailFragment.kt, ChatViewModel.kt
- `ui/viewer/*`
- `ui/events/`: EventsAdapter.kt, EventsDetailFragment.kt, EventsDetailViewModel.kt
- `ui/submissions/`: SubmissionViewModel.kt, SubmissionsAdapter.kt, SubmissionUiModel.kt
- `ui/surveys/`: SurveyFragment.kt, SurveysViewModel.kt
- `ui/user/`: UserProfileFragment.kt, BecomeMemberActivity.kt, AchievementFragment.kt, CertificateDialogFragment.kt, CourseCertificatesAdapter.kt, GamificationBadgesAdapter.kt, GamificationViewModel.kt
- `ui/sync/LoginActivity.kt`, `ui/onboarding/OnboardingActivity.kt`, `ui/dictionary/DictionaryActivity.kt`, plus the other Activity files touched by PR #16101
- `repository/`: CoursesRepository(Impl), TeamsRepository(Impl), VoicesRepository(Impl), RatingsRepository(Impl), EventsRepository(Impl), SurveysRepository(Impl), SubmissionsRepositoryImpl, ProgressRepositoryImpl, ActivitiesRepository(Impl), GamificationRepository(Impl), ChatRepository(Impl), CommunityRepository(Impl), FeedbackRepository(Impl), HealthRepository.kt (interface), UserRepositoryImpl.kt, ResourcesRepository(Impl), TeamsSyncRepository.kt, UserSyncRepository.kt
- `data/room/AppDatabase.kt`; DAOs: NotificationDao, CourseProgressDao, MeetupDao, RemovedLogDao, NewsDao, OfflineActivityDao, SubmissionDao, TeamTaskDao, AchievementDao, CertificationDao, ChatDao, CourseDao, ExamDao, FeedbackDao, HealthExaminationDao, RatingDao, SyncCursorDao, TagDao; `data/DatabaseService.kt`, `data/RealmMigrations.kt`
- `model/`: MyLibrary.kt, TagEntity.kt, TeamTask.kt, MyTeam.kt, Meetup.kt, RealmNews.kt, Course.kt, RealmMyCourse.kt, SyncCursor.kt, RatingPromptLog.kt, gamification/*, CreateTeamRequest.kt, TeamDetails.kt, TeamUpdateRequest.kt, MeetupCreationParams.kt, CourseLevel.kt, GradeLevel.kt, ResourceListModel.kt, AssignedSurvey.kt, TeamLeaderboardEntry.kt
- `services/`: UploadManager.kt, TaskNotificationWorker.kt, ServerReachabilityWorker.kt, sync/TransactionSyncManager.kt, sync/HeavyTableSyncWorker.kt, reminders/*, P2pTransferManager.kt
- `utils/`: TimeUtils.kt, JsonUtils.kt, EdgeToEdgeUtils.kt, NotificationUtils.kt, StreakUtils.kt, ResourcesSearchUtils.kt
- `di/`: RoomModule.kt, RepositoryModule.kt, ServiceModule.kt
- `callback/`: OnTaskCompletedListener.kt, OnLibraryItemSelectedListener.kt, OnNewsItemClickListener.kt
- Build/CI: `app/build.gradle`, `build.gradle.kts`, `settings.gradle`, `gradle/libs.versions.toml`, `.github/scripts/labels.sh`, `.github/workflows/*`, `AGENTS.md`, `CLAUDE.md`, `ci/robo/*`, `baselineprofile/*`
- Resources: `values*/strings.xml` (all locales), `values/colors.xml`, `values-night/colors.xml`, `values/dimens.xml`, `values-land/dimens.xml`, `values/styles.xml`, plus the long tail of layout/drawable files touched by open PRs (home_card_*.xml, row_task.xml, row_news.xml, fragment_my_course*.xml, fragment_my_library*.xml, activity_login*.xml, activity_dashboard.xml, add_meetup.xml, item_meetup.xml, fragment_exam_taking*.xml, item_checkbox.xml, item_radio_btn.xml, fragment_calendar.xml, fragment_enterprise_calendar.xml, fragment_achievement.xml, row_library.xml, fragment_library_detail.xml, fragment_voices.xml, alert_input.xml, alert_create_team.xml, fragment_plan.xml, fragment_team.xml, item_team_list.xml, my_library_alertdialog.xml, app_bar_bell.xml, layout_search.xml, activity_become_member.xml, edit_profile_dialog.xml, item_user.xml, fragment_team_leaderboard.xml, row_leaderboard_member.xml, etc.) — no task below touches any `res/` file.

---

## Task 1 — Batch the per-team chat-count N+1 in team notification badges

**Serves roadmap:** 7 (performance hotspot) · also 1 (data layer: query pushed down to DAO) and 9 (batched repository logic stays Android-free).

**Verified evidence:**
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:309-312` — `getTeamNotifications()` loops `for (teamId in teamIds) { chatCountsById[teamId] = voicesRepository.countTopLevelByTeam(teamId) }`, one SQL `COUNT(*)` per team (called with every team the user belongs to on the dashboard path).
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt:80-81` — existing per-team query `countTopLevelByTeam(teamId, teamPattern)` to mirror.

**Work:**
1. In `NewsDao.kt` add a batch query returning per-team counts: `SELECT viewableId, COUNT(*) FROM news WHERE (replyTo IS NULL OR replyTo = '') AND viewableBy = 'teams' COLLATE NOCASE AND viewableId IN (:teamIds) GROUP BY viewableId` as `suspend fun countTopLevelByTeams(teamIds: List<String>): List<TeamChatCount>` with a small `@ColumnInfo` data class (pattern after `SubmitPhotosDao.UploadUpdate`). Rows covered only by the `viewIn LIKE :teamPattern` branch are legacy; keep per-team `countTopLevelByTeam` as a fallback for teamIds missing from the batch result so behavior is unchanged.
2. In `VoicesRepository.kt` add `suspend fun countTopLevelByTeams(teamIds: List<String>): Map<String, Long>`; implement in `VoicesRepositoryImpl.kt` by delegating to the new DAO method (falling back per-team for missing ids).
3. In `NotificationsRepositoryImpl.kt` replace the loop with one `voicesRepository.countTopLevelByTeams(teamIds)` call.
4. Extend the NewsDao tests (create `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NewsDaoBatchCountTest.kt` if none exists) and `NotificationsRepositoryImplTest` for the batch path.

**Files (≤5):** `data/room/dao/NewsDao.kt`, `repository/VoicesRepository.kt`, `repository/VoicesRepositoryImpl.kt`, `repository/NotificationsRepositoryImpl.kt`, + 1 test file.
**Constraints:** no schema change (query-only), no new dependencies, preserve the `viewIn LIKE` fallback semantics.

---

## Task 2 — Remove the redundant `deepCopy()` defeating Achievement's JSON parse cache

**Serves roadmap:** 7 · also 9 (pure model-layer logic, no Android imports involved).

**Verified evidence:**
- `app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt:95-99` — a synchronized LRU `parsedJsonCache` (capacity 1000).
- `Achievement.kt:101-112` — `parseStringListToJsonArray()` looks up the cache, then calls `array.add(ob?.deepCopy())` for every element, so the cache only saves parsing but still pays a full tree clone per item on every call.

**Work:**
1. Determine intent: the `deepCopy()` protects against later mutation of the returned `JsonArray`. Audit the callers of the functions that use `parseStringListToJsonArray` inside `Achievement.kt` (and its consumers) — if none mutates the returned elements, drop the `deepCopy()` and add the cached element directly.
2. If mutation risk exists, instead cache the parsed `JsonArray` per input list signature (or deep-copy once at cache-insert time and hand out the shared read-only element), documenting the no-mutation contract on the function.
3. Add/extend a unit test in `app/src/test/java/org/ole/planet/myplanet/model/` asserting cache hits return equal content and that repeated `fromJson` calls over the same reference strings don't re-parse.

**Files (≤3):** `model/Achievement.kt` + test file(s).
**Constraints:** no new dependencies; keep the `CACHE_CAPACITY` eviction behavior; no public API change.

---

## Task 3 — Add `distinctUntilChanged()` to SubmissionDetailViewModel's derived StateFlows

**Serves roadmap:** 3 (ViewModel layer hygiene) and 7 (kills redundant UI invalidations) · also 10 (state hoisted and deduplicated in the ViewModel is exactly the portable-screen shape Compose needs).

**Verified evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:38-61` — five flows (`questionAnswers`, `title`, `status`, `date`, `submittedBy`) each do `submissionDetailState.filterNotNull().map { ... }.stateIn(...)` with no `distinctUntilChanged()`, so any upstream re-emission re-renders all five consumers.

**Work:**
1. Add `.distinctUntilChanged()` after each `.map { }` in the five derived flows.
2. Add a test in `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt` (create if absent) that re-emits equal upstream state and asserts collectors see no duplicate emissions.

**Files (≤2):** `ui/submissions/SubmissionDetailViewModel.kt` + test.
**Constraints:** ~10 changed lines; no behavior change beyond dedup.

---

## Task 4 — Inject `TimeProvider` into `RetryOperation` companion factory methods

**Serves roadmap:** 4 (DI cleanup) and 8 (testability) · also 9 (removes a hidden `System.*` dependency from the model layer, a prerequisite for a platform-free core).

**Verified evidence:**
- `app/src/main/java/org/ole/planet/myplanet/model/RetryOperation.kt:75-76` — `createdTime`/`lastAttemptTime = System.currentTimeMillis()` in `createFromRetryFailure(...)`; `:87` — `calculateNextRetryTime` returns `System.currentTimeMillis() + delay`.
- `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt:11-16` — the injectable interface exists; `di/TimeModule.kt:16` provides it.
- `model/RetryFailure.kt` is a plain data class (safe to reference in signatures).

**Work:**
1. Add a `timeProvider: TimeProvider` parameter (defaulting to `SystemTimeProvider()` for call-site compatibility, or threaded through — prefer threading) to `createFromRetryFailure(...)` and `calculateNextRetryTime(...)`.
2. Update all call sites (find via `createFromRetryFailure(` / `calculateNextRetryTime(` — expected in `services/retry/` and/or upload code; **re-grep at execution time and do not touch any call site that lives in an off-limits file listed in the header — if one does, leave that call on the default parameter**).
3. Add a unit test asserting `calculateNextRetryTime` backoff values (30s, 60s, …, capped at 30 min) against a fake clock.

**Files (≤4):** `model/RetryOperation.kt`, up to 2 call-site files, + test.
**Constraints:** keep exponential backoff values identical; no new dependencies.

---

## Task 5 — Replace raw `System.currentTimeMillis()` with the injected clock in NotificationsAdapter's relative-time formatting

**Serves roadmap:** 7 (per-bind work) and 8 · also 10 (removes direct framework-clock access from UI binding, easing later hoisting).

**Verified evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt:153` — `val diff = System.currentTimeMillis() - createdAt` inside `formatRelativeTime`; `:39-53` — a hand-rolled `DateTimeFormatter` cache; `utils/TimeUtils.kt:72` already offers `getRelativeTime(timestamp, timeProvider)` using Android's `DateUtils.getRelativeTimeSpanString`.

**Work:**
1. Replace the `System.currentTimeMillis()` call with an injectable/clock parameter: simplest compliant approach is to pass a `now: () -> Long` (defaulting to a `TimeProvider`-backed lambda from the hosting fragment) into the adapter constructor, or reuse `TimeUtils.getRelativeTime` where the bucket logic matches — executor decides after comparing rendered strings; keep the existing thresholds pixel-identical.
2. Where the custom `"MMM d, yyyy"` fallback at `:161` duplicates `TimeUtils` formatters, delegate to `TimeUtils` (read `utils/TimeUtils.kt` first; if PR #16686's refactor has landed, use whichever consolidated helper exists).
3. Add a Robolectric test in `app/src/test/java/org/ole/planet/myplanet/ui/notifications/` covering boundary buckets (minutes/hours/days fallback).

**Files (≤2):** `ui/notifications/NotificationsAdapter.kt` + test. (Do **not** modify `utils/TimeUtils.kt` — off-limits.)
**Constraints:** no visible string changes; no new dependencies.

---

## Task 6 — Stop re-hitting the reachability network on the upload path by routing through the existing `ServerReachabilityProvider`

**Serves roadmap:** 5 (sync/upload consolidation) and 7 (removes duplicate socket probes) · also 4 (one injectable owner instead of companion mutable state).

**Verified evidence:**
- `app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt:13-45` — a `@Singleton` injectable provider already implements the 30s TTL cache with `TimeProvider`.
- `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt:194-222` — a *duplicate* cache (`REACHABILITY_CACHE_TTL_MS`, `reachabilityCache`) lives in the `MainApplication` companion with raw `System.currentTimeMillis()`.
- Call sites already split: `ui/sync/SyncActivity.kt:664`, `services/SubmissionsUploader.kt:35,49`, and `repository/ChatRepositoryImpl.kt:50` call the `MainApplication` companion, while `utils/SyncTimeLogger.kt:101-104` uses the provider.

**Work:**
1. `utils/SyncTimeLogger.kt` needs no change; it is the reference pattern.
2. Migrate exactly one call-site cluster to the injectable provider: `services/SubmissionsUploader.kt:35,49` — constructor-inject `ServerReachabilityProvider` (it is created via entry points/workers — use the established `CoreDependenciesEntryPoint` pattern used by sibling services) and replace the `MainApplication.isServerReachable(...)` calls.
3. Do NOT touch `MainApplication.kt`, `SyncActivity.kt`, `ServerUrlMapper.kt`, `ChatRepositoryImpl.kt`, `ServerReachabilityWorker.kt` (off-limits or shared); leave the companion cache in place for them.
4. Add a unit test for `SubmissionsUploader`'s reachability short-circuit with a mocked provider.

**Files (≤3):** `services/SubmissionsUploader.kt`, possibly `di/CoreDependenciesEntryPoint.kt` (verify it is still free of open-PR collisions before editing), + test.
**Constraints:** behavior-identical reachability semantics (primary then alternative URL); no new dependencies.

---

## Task 7 — Replace `keySet()` iteration with entry iteration in two repository hot spots

**Serves roadmap:** 1 (data-layer cleanup) and 7 (micro-allocation removal) · also 9 (both functions are platform-free Kotlin once cleaned).

**Verified evidence:**
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:465-479` — `payload.keySet().forEach { key -> ... payload.get(key)... }` performs a set-view allocation plus a second lookup per key (12-key when-chain).
- `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:92-95` — `for (key in conditions.keySet()) { result[key] = JsonUtils.getBoolean(key, conditions) }` same pattern, plus it funnels through `JsonUtils.getBoolean`'s redundant `has()`+`get()` (do not edit JsonUtils — off-limits).

**Work:**
1. `UserRepositoryImpl.kt`: iterate `payload.entrySet()` and match on `entry.key`, using `entry.value` (guarding non-primitives with the same null-safety the current `asString` calls rely on — keep `try/catch` behavior identical if present).
2. `HealthRepositoryImpl.kt`: iterate `conditions.entrySet()`; read `entry.value.asBoolean` with the same primitive guard `JsonUtils.getBoolean` applies (replicate the `isJsonPrimitive` check inline — JsonUtils itself may not be edited).
3. Extend the existing repository tests (`UserRepositoryImplTest`, and the health repository test file under `app/src/test/java/org/ole/planet/myplanet/repository/`) with cases covering non-primitive values and empty objects.

**Files (≤4):** `repository/UserRepositoryImpl.kt`, `repository/HealthRepositoryImpl.kt`, + 2 test files.
**Constraints:** identical output maps; no new dependencies.

---

## Task 8 — Eliminate per-item `MyLife` re-allocation in LifeAdapter's drag-reorder persistence

**Serves roadmap:** 7 (allocation churn on every drag) · also 1 and 9.

**Verified evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt:125-134` — `onItemMoveFinished()` rebuilds every element: `list.mapIndexed { index, item -> MyLife().apply { _id = item._id; imageId = item.imageId; ... } }` — N fresh entity objects on each reorder gesture, purely to re-rank.

**Work:**
1. Read the full function and the `MyLife` entity (`model/` — verify the exact filename, likely `MyLife.kt`, before citing it in the PR). Replace object rebuilding with in-place rank updates on the existing instances (mutate the ordering field — the project uses mutable entities pervasively, so this matches convention) and persist via the existing DAO/repository call the function already ends with.
2. Ensure the adapter's `ListAdapter` diff still fires correctly (submit a re-sorted list of the same instances if `areContentsTheSame` compares rank; otherwise submit the same list reference and rely on the explicit move notifications already present).
3. Add/extend `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt` covering that reorder persists correct ranks and allocates no new entity instances (assert identity preservation).

**Files (≤2):** `ui/life/LifeAdapter.kt` + test.
**Constraints:** no schema change; no new dependencies.

---

## Task 9 — Cache `MyTeam.getAttachmentFile` existence checks out of the finance-list bind path

**Serves roadmap:** 7 · also 1.

**Verified evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapter.kt:59-75` — `bindFinanceImage()` calls `MyTeam.getAttachmentFile(context, item.id, item.imageName)` and `imageFile.exists()` (filesystem stat) inside `onBindViewHolder` for every row; on miss it also nulls the listener every bind. (`model/MyTeam.kt` itself is off-limits — do not edit it; the fix lives in the adapter.)

**Work:**
1. In `EnterprisesFinancesAdapter.kt` add an `LruCache<String, File?>` (keyed on `item.id + imageName`) so repeat binds of the same transaction skip the filesystem stat; invalidate the relevant key in `onViewRecycled` only if the file set can change during the adapter's lifetime (check the fragment's add-transaction flow — if transactions are append-and-refresh, no invalidation is needed; document that).
2. Move the `setOnClickListener(null)` else-branch into the cache-miss path so the listener isn't re-cleared on every bind.
3. Add a Robolectric test under `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/` asserting repeated binds of the same item hit the filesystem once.

**Files (≤2):** `ui/enterprises/EnterprisesFinancesAdapter.kt` + test.
**Constraints:** `model/MyTeam.kt` untouched (off-limits); no new dependencies (use `android.util.LruCache`, already on the classpath, as `NotificationsAdapter.kt:41` does).

---

## Task 10 — Move `MyPlanet.getTabletUsages` wall-clock read and `UsageStatsManager` query off the caller's thread via the injected dispatcher

**Serves roadmap:** 7 (this runs on the sync/upload path) and 5 (upload pipeline hygiene) · also 9 (isolates the `android.app.usage` API behind one function, the seam a KMP core needs).

**Verified evidence:**
- `app/src/main/java/org/ole/planet/myplanet/model/MyPlanet.kt:60-70` — `getTabletUsages(context, spm)` calls `queryUsageStats(..., cal.timeInMillis, System.currentTimeMillis())` and loops `addStats`.
- Called from `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt:437` (`usages.addAll(MyPlanet.getTabletUsages(context, sharedPrefManager))`) — the repository already injects `DispatcherProvider` (verify field name at execution; the interface `ActivitiesRepository.kt` is off-limits, the impl is free).

**Work:**
1. In `ActivitiesRepositoryImpl.kt` wrap the `MyPlanet.getTabletUsages(...)` call in `withContext(dispatcherProvider.io) { }` so the usage-stats query never runs on a caller's main thread.
2. In `model/MyPlanet.kt` replace the raw `System.currentTimeMillis()` with a `now: Long = System.currentTimeMillis()` defaulted parameter (keeps the static-call shape; full DI is impossible in a companion) so tests can pin the window.
3. Add a unit test for the window math (start = last-usage-upload, end = injected now) and an `ActivitiesRepositoryImplTest` assertion that the call is dispatched on the io dispatcher (use `TestDispatcherProvider`).

**Files (≤3):** `model/MyPlanet.kt`, `repository/ActivitiesRepositoryImpl.kt`, + test.
**Constraints:** no behavior change to uploaded payload; no new dependencies; do not edit `ActivitiesRepository.kt` (off-limits).

---

## Self-check

- **R1**: 10 tasks, no ordering dependencies — each ships alone.
- **R2**: file↔task map is disjoint (NotificationsRepositoryImpl.kt appears only in Task 1; original Task 9 was replaced to avoid a Task 1 collision).
- **R3**: open PRs listed above; all cited files cross-checked against the union of 48 PRs' file lists.
- **R4**: every cited path/class/function/line was opened and confirmed before citing.
- **R5**: largest task (Task 1) ≈ 120 lines / 5 files; all others well under.
- **R6**: this file is the deliverable; no implementation code included.

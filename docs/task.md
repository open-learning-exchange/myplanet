# myPlanet refactor round — 10 work orders

Generated 2026-09-10. Each task is independently mergeable in any order, touches files no
other task touches, and avoids every file currently modified by an open pull request.

## Open PRs checked (off-limits sources)

38 open PRs were enumerated via the GitHub API and their changed files excluded. Notable
exclusions that shaped the plan:

| PR | Off-limits files (Kotlin-relevant) |
|---|---|
| #16986 | `base/BasePermissionActivity.kt`, `ui/chat/ChatDetailFragment.kt`, `ui/resources/AddResourceFragment.kt`, `ui/user/UserProfileFragment.kt` |
| #16690 | `ui/onboarding/OnboardingActivity.kt` |
| #16624 | `repository/CoursesRepository.kt`, `CoursesRepositoryImpl.kt`, `utils/ResourcesSearchUtils.kt` |
| #16623 | `repository/TeamsRepository.kt`, `TeamsRepositoryImpl.kt`, `model/TeamTask.kt`, `callback/OnTaskCompletedListener.kt`, `ui/teams/tasks/*` |
| #15951 | `data/room/AppDatabase.kt`, `repository/TeamsRepository*`, `model/MyTeam.kt`, `ui/teams/PlanFragment.kt`, `TeamFragment.kt`, `TeamViewModel.kt`, `TeamsAdapter.kt`, `utils/TimeUtils.kt` |
| #15825 | `MainApplication.kt`, `data/room/AppDatabase.kt`, `di/RepositoryModule.kt`, `services/TaskNotificationWorker.kt`, `utils/NotificationUtils.kt` |
| #15824 | `app/build.gradle`, DAOs: `CourseProgressDao.kt`, `NewsDao.kt`, `OfflineActivityDao.kt`, `SubmissionDao.kt`, `TeamTaskDao.kt`, `ui/user/AchievementFragment.kt` |
| #15820 | `app/build.gradle`, `data/room/dao/NewsDao.kt`, `repository/EventsRepository*`, `ui/teams/TeamCalendar*`, `ui/events/EventsAdapter.kt` |
| #15808 | `data/room/AppDatabase.kt`, `data/room/dao/SyncCursorDao.kt`, many DAOs' `deleteByIds`, `repository/HealthRepository*.kt`, `ProgressRepository*.kt`, `RatingsRepository(Impl).kt` (deleteByIds additions), `di/RoomModule.kt`, `di/ServiceModule.kt` |
| #15559 | `ui/exam/ExamTakingFragment.kt` |
| #15519 / #14893 / #15825 | `ui/dashboard/DashboardActivity.kt` |
| #16101 | `utils/EdgeToEdgeUtils.kt`, `ui/sync/LoginActivity.kt` (+ many activities) |
| #15412 / #13657 | `ui/courses/CoursesFragment.kt`, `CoursesAdapter.kt`, `CoursesViewModel.kt`, `CourseSelectionController.kt`, `CourseFilterController.kt` |
| #14427 | `repository/ActivitiesRepository*.kt`, `ui/dashboard/DashboardViewModel.kt`, `BellDashboard*` |
| #14650 | `repository/SurveysRepository*.kt`, `ui/submissions/SubmissionViewModel.kt`, `SubmissionsAdapter.kt` |
| #14883 | `repository/SurveysRepository*.kt`, `ProgressRepositoryImpl.kt`, `ui/teams/TeamDetailFragment.kt`, `TeamPageConfig.kt` |
| #15198 | `ui/chat/ChatViewModel.kt`, `ChatDetailFragment.kt` |
| #15108 | `model/Meetup.kt`, `repository/EventsRepository*.kt`, `ui/events/EventsDetail*`, `ui/teams/TeamCalendarFragment.kt` |
| #14960 | `ui/sync/LoginActivity.kt` |
| #13604 | `ui/surveys/SurveyFragment.kt`, `SurveysViewModel.kt` |
| #13415 / #10993 | `repository/VoicesRepository*.kt`, `ui/voices/VoicesFragment.kt`, `VoicesAdapter.kt`, `base/BaseVoicesFragment.kt`, `services/UploadManager.kt`, `model/RealmNews.kt` |
| #13355 | `AndroidManifest.xml`, `ui/resources/ResourcesFragment.kt`, `ResourcesAdapter.kt`, `ResourceDetailFragment.kt` |
| #15267 | `base/BaseRecyclerFragment.kt`, `base/BaseResourceFragment.kt` |
| #15266 | `res/layout/fragment_calendar.xml`, `fragment_enterprise_calendar.xml` |
| #13928 | `app/build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `build.gradle.kts` |
| #13848 | `ui/exam/UserInformationFragment.kt`, `ui/user/BecomeMemberActivity.kt`, `ui/resources/AddResourceActivity.kt` |
| #16987 / #15226 / #16270 / #16594 | root docs, `.github/*`, `flutter/*`, `.claude/*`, `AGENTS.md`, `CLAUDE.md` |
| #16989 / #16988 / #13287 / #8175 / #4075 | drawables, `values-*/strings.xml`, member layouts, `ci/robo/*`, `.github/workflows/android-release.yml` |

Consequently `TeamsRepository*`, `CoursesRepository*`, `VoicesRepository*`, `AppDatabase.kt`,
`RepositoryModule.kt`, `RoomModule.kt`, `ServiceModule.kt`, all of `ui/teams/tasks`,
`ui/dashboard`, `ui/courses`, `ui/voices` fragments/adapters, `ui/sync/LoginActivity.kt`,
`app/build.gradle` and `values*/strings.xml` are avoided everywhere below. All cited files,
classes and functions were opened and confirmed in the clone. No file appears in more than
one task.

---

### Task 1 — Give `NotificationsRepository` a typed presentation-enrichment method

- **Roadmap**: 3 (expand viewmodel/use-case layers); also 9 — removes per-call orchestration
  of teams/user lookups from the ViewModel and concentrates cross-feature reads (team tasks,
  join requests, team names, user names) behind one repository boundary instead of the UI
  layer knowing the lookup recipe.
- **Problem**: `ui/notifications/NotificationsViewModel.kt` (`loadNotifications`, lines
  70–145) hand-orchestrates five repository calls (`getTaskTeamNamesByTaskIds`,
  `getTaskTeamNamesByTaskTitles`, `getJoinRequestDetailsBatch`, `getJoinRequestDetails`,
  `getUnreadCount`) plus message parsing (`parseTaskDate`) to decide *which* lookups each
  notification batch needs. That is data-assembly logic living in a ViewModel, and the
  repository interface exposes six narrow cross-feature read methods
  (NotificationsRepository.kt lines 19–23) purely to serve this one screen.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
    (interface-side regions only; the `getTeamNotifications` body belongs to Task 2 — see
    the coordination note below)
  - `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
  - `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`
- **Approach**: Introduce one suspend method on `NotificationsRepository`, e.g.
  `suspend fun getEnrichedNotifications(userId: String, filter: String, isAdmin: Boolean): EnrichedNotifications`,
  returning a small data class (declared in the repository file) holding the raw payloads
  plus `taskTeamNames: Map<String, String>`,
  `joinRequestDetails: Map<String, Pair<String, String>>`, and `unreadCount: Int`. Move the
  batching/distinct/parallel-fetch logic from `NotificationsViewModel.loadNotifications`
  into `NotificationsRepositoryImpl` (it may keep using the existing private DAO/repository
  collaborators; `coroutineScope`+`async` is fine in the impl). The task-date parsing needed
  to derive `taskTitles` (`NotificationsViewModel.parseTaskDate`) moves into the impl as a
  private function. Delete the now-unused interface methods `getTaskTeamNamesByTaskIds`,
  `getTaskTeamNamesByTaskTitles`, `getJoinRequestDetailsBatch`, and the null-arg
  `getJoinRequestDetails` fallback path **only if** a full-repo grep confirms no remaining
  callers (`getTaskDetails` and `getJoinRequestTeamId` stay — they are used by
  `NotificationsFragment` lines 124–129). ViewModel keeps formatting (string resources) and
  grouping. Update the ViewModel test; assert the ViewModel no longer calls the removed
  methods and that the impl returns an empty-details map when there are no task/join_request
  notifications. **Coordination note**: do not edit the body of
  `NotificationsRepositoryImpl.getTeamNotifications` or
  `NotificationsRepositoryImplTest.kt` in this task.
- **Constraints**: <150 changed lines; no new dependencies; the `resolvedType`/string
  formatting stays in the ViewModel (needs `Context`).
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*Notifications*"`.

### Task 2 — Align `ChatDao.getByUser` with the repository-owned chat ordering

- **Roadmap**: 1 (repository boundary cleanup), 8 (code health).
- **Problem**: `ChatRepositoryImpl.getChatHistoryForUser` (ChatRepositoryImpl.kt lines
  130–136) calls `chatDao.getByUser`, whose SQL carries `ORDER BY id DESC` (ChatDao.kt line
  12), then immediately re-sorts the rows in Kotlin by `createdDate`/`updatedDate`
  (`sortChats`, lines 138–142). The SQL ordering is dead weight, disagrees with the real
  ordering, and misleads future callers into trusting the DAO order.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ChatDao.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt`
- **Approach**: Remove `ORDER BY id DESC` from `ChatDao.getByUser` and add a one-line comment
  that `ChatRepositoryImpl.sortChats` owns ordering (the string-typed date columns make a
  correct SQL ordering unsafe, so ordering stays in Kotlin — do not attempt a SQL rewrite).
  In `ChatRepositoryImplTest` (existing cases at lines 76–95), assert that
  `getChatHistoryForUser` output order follows `sortChats` semantics regardless of DAO
  return order (feed the mocked DAO an intentionally reversed list).
- **Constraints**: <20 lines; do not add `deleteByIds` to `ChatDao` (open PR #15808 adds it —
  avoid the name); no schema change.
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*ChatRepository*"`.

### Task 3 — `ChatRepository.searchChats`: stop round-tripping the chat list through the caller

- **Roadmap**: 1 (tighten repository interfaces), 9 (fewer domain rules dependent on
  caller-managed state).
- **Problem**: `ChatRepository.searchChats(query, mode, chats)` (ChatRepository.kt line 24,
  ChatRepositoryImpl.kt line 157) takes the full `List<ChatHistory>` as a parameter — the
  ViewModel first calls `getChatHistoryForUser` (ChatViewModel.kt line 135) and passes the
  same list back in for every keystroke. The repository owns `ChatDao`; the list parameter
  is a leaked caching concern of the UI.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt`
  - `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt`
    — **shared with Task 2**: Task 2 edits only the `getChatHistoryForUser` cases; this task
    edits only the `searchChats` cases (lines 97–109). If both land, whichever merges second
    rebases; the hunks are disjoint.
- **Approach**: Change the signature to
  `suspend fun searchChats(query: String, mode: ChatSearchMode, userName: String?): List<ChatHistory>`;
  inside the impl reuse the existing `chatDao.getByUser(userName)` path
  (ChatRepositoryImpl.kt line 134) followed by the existing
  `buildPrecomputedChats`/`searchByTitle`/`fullConvoSearch` pipeline. Update
  `ChatViewModel.searchChats` (ChatViewModel.kt lines 146–160) to pass the current user's
  name instead of `allChats`; remove the now-redundant in-memory list plumbing there (keep
  the fragment-facing API of the ViewModel unchanged so `ChatHistoryFragment` stays
  untouched). Update the `searchChats` test cases (lines 97–109) to the new signature.
- **Constraints**: <150 lines; no new dependencies; `ChatHistoryFragment.kt`,
  `ChatDetailFragment.kt` untouched.
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*ChatRepository*" --tests "*ChatViewModel*"`.

### Task 4 — Give `RatingsRepository` a typed summary map; deprecate the `JsonObject` aggregate API

- **Roadmap**: 1 (repository boundary cleanup), 9 (a repository interface whose public type
  surface is free of transport JSON types is a prerequisite for a platform-free core).
- **Problem**: `RatingsRepository` exposes `getRatings`, `getCourseRatings`,
  `getResourceRatings` returning `HashMap<String?, JsonObject>` (RatingsRepository.kt lines
  7–10). Verified by full-repo grep: the **only** callers in `app/src` are
  `RatingsRepositoryImpl` itself and its test — production UI already consumes the typed
  `RatingSummary` path. These methods also load every `Rating` row of a type into memory
  (`RatingDao.getByType`, RatingsRepositoryImpl.kt line 21) and aggregate in Kotlin even
  though `RatingDao.getAggregate` (RatingDao.kt line 26) already proves SQL-side aggregation
  for the single-item case.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt`
  - `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RatingDao.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/RatingsRepositoryImplTest.kt`
- **Approach**: Add
  `suspend fun getRatingSummariesByType(type: String, userId: String?): Map<String, RatingSummary>`
  to the interface; implement it with a new grouped SQL query in `RatingDao`
  (`SELECT item, COUNT(*) AS totalCount, AVG(rate) AS averageRate FROM rating WHERE type IS :type GROUP BY item`
  returning a small projection next to the existing `RatingAggregate`, plus one query for
  the caller's own ratings
  `SELECT item, rate FROM rating WHERE type IS :type AND userId = :userId`), assembling
  typed `RatingSummary` values (the existing data class in RatingsRepository.kt) in the
  impl. Delete `getRatings`, `getCourseRatings`, `getResourceRatings` and the private
  `RatingAggregation`/`aggregateRatings` helpers; update `RatingsRepositoryImplTest` (lines
  50–96) to the new method. Keep `insertRatingsFromSync` as-is (sync boundary;
  `deleteByIds` must NOT be added — open PR #15808 adds exactly that to these files, so
  name-conflict-check before merging if both land).
- **Constraints**: <150 lines; `RatingDao.kt` addition must not duplicate any `deleteByIds`
  method name; no Gson types remain in the interface except `insertRatingsFromSync`.
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*Ratings*"`.

### Task 5 — De-JSON the achievement edit flow between ViewModel and repository

- **Roadmap**: 1, 3, 9 (typed models instead of Gson trees at the ViewModel↔repository
  seam), 10 (no `JsonArray` state in view-layer state holders).
- **Problem**: `ui/user/AchievementViewModel.kt` declares `AchievementSaveRequest` with
  `achievements: JsonArray`, `references: JsonArray`, `profileFields: JsonObject` (lines
  22–35), and `UserRepository.updateAchievement`/`updateProfileFields` take those Gson types
  straight through (UserRepository.kt lines 64–97). `EditAchievementFragment` mutates raw
  `JsonArray`s (`resourceArray`, `referenceArray` — EditAchievementFragment.kt lines 74–76,
  322, 350, 424, 447) as its editing model.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`
  - `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`
  - `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementViewModelTest.kt`
- **Approach**: Introduce typed lists in the request model —
  `AchievementSaveRequest.achievements: List<String>` (already the on-disk shape:
  `Achievement.achievements` is `List<String>?` serialized via `parseStringListToJsonArray`,
  model/Achievement.kt lines 12–35) and a small `ProfileFieldUpdate` map
  (`Map<String, String>`) for the profile fields. Change `UserRepository.updateAchievement`
  to accept `List<String>` and do the `JsonArray` wrapping inside
  `UserRepositoryImpl.updateAchievement` (line 907) where it already builds the Couch
  document; same for `updateProfileFields` (`Map<String, String>` → `JsonObject` in impl).
  In `EditAchievementFragment`, replace the `resourceArray`/`referenceArray` `JsonArray`
  accumulators with `MutableList<String>` of serialized entries at the existing mutation
  sites and convert at the boundary. Do NOT change `AchievementFragment.kt` (open PR #15824
  touches it) or `UserInformationFragment.kt` (#13848) — `updateProfileFields` keeps a
  deprecated `JsonObject` overload only if those files call it (verified:
  UserInformationFragment.kt line 163 does), so keep the old overload delegating to the new
  one until those PRs land, and note that in the PR description.
- **Constraints**: <150 lines net; no schema changes (Achievement entity untouched).
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*Achievement*" --tests "*UserRepository*"`.

### Task 6 — `TagDao`: fetch child tags by parent ids instead of scanning the whole table

- **Roadmap**: 7 (DAO optimization), 1.
- **Problem**: `TagsRepositoryImpl.getTagsWithChildren` (TagsRepositoryImpl.kt lines 17–39)
  loads `tagDao.getAll()` — the entire `tag` table — then builds the parent→children map in
  Kotlin. `TagEntity` (model/TagEntity.kt lines 14–23) already has indices on `tagId`, `db`,
  `linkId`; child rows are linked via `attachedTo` (a JSON `List<String>` column, so SQL
  can't join on it — but parent linkage can be resolved by loading only *attached* rows, not
  the whole table).
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/TagsRepositoryImplTest.kt`
- **Approach**: Add `TagDao.getAttachedForDb(db: String?)`:
  `SELECT * FROM tag WHERE isAttached = 1 AND (:db IS NULL OR db = :db)` —
  `getTagsWithChildren` then reads parents (existing `getParentTags`) plus only attached
  rows instead of `getAll()`. Keep the existing Kotlin grouping logic unchanged (it handles
  the multi-valued `attachedTo` column, which SQL cannot). Also the
  `list.isEmpty() || list.last() !== t` identity-dedup at TagsRepositoryImpl.kt line 29
  should become an id-set-guarded append, matching the dedup intent without reference
  equality on a fresh query's objects (behavior for duplicate `attachedTo` entries must stay
  identical — cover with a test). Update `TagsRepositoryImplTest` with a case asserting
  `getAll()` is no longer called.
- **Constraints**: <150 lines; do not touch `TagEntity` (no schema/version change); do not
  add `deleteByIds` to `TagDao` (open PR #15808 adds it — avoid the name).
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*TagsRepository*"`.

### Task 7 — Add `(userId, isRead)` composite index to the notifications table

- **Roadmap**: 7 (Room DAO/query optimization).
- **Problem**: `NotificationDao` hot queries filter on `userId` + `isRead` + ordering by
  `createdAt`: `getUnreadCount` (NotificationDao.kt line 21), `getNotifications` (line 32),
  `getUnreadIds` (line 40). `AppNotification` only has single-column indices
  `Index("userId"), Index("type")` (model/AppNotification.kt line 9), so the unread-badge
  count and list refresh scan the per-user slice without a covering composite index.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/model/AppNotification.kt`
- **Approach**: Add `Index(value = ["userId", "isRead"])` to the entity's `indices` array.
  Do NOT bump the Room `version` in `AppDatabase.kt` — that file is touched by open PRs
  #15808/#15825/#15951. The PR description must state that the version bump lands with
  whichever in-flight PR bumps `AppDatabase` next (drop-and-resync strategy means no
  migration code; the index simply won't exist until the next schema version bump —
  acceptable because indices are performance-only, never correctness). Title/label the PR so
  the automerge queue orders it adjacent to a version bump.
- **Constraints**: 1 file, <10 lines; no other entity changes; do not edit `AppDatabase.kt`.
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*Notification*"` (MockK-based,
  unaffected); visual check of the annotation only.

### Task 8 — Composite index for the team-notification lookup table

- **Roadmap**: 7.
- **Problem**: `TeamNotificationDao.findByParentAndType` /
  `getByTypeAndParentIds` (TeamNotificationDao.kt lines 8–13) filter on
  `parentId` + `type` and are hit from
  `NotificationsRepositoryImpl.getTeamNotifications` on every dashboard team-badge refresh.
  `TeamNotification` has only `Index("type")` (model/TeamNotification.kt line 12), so these
  are partial table scans. (First-choice target `TeamTask(assignee, deadline)` is blocked:
  `model/TeamTask.kt` is touched by open PR #16623.)
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/model/TeamNotification.kt`
- **Approach**: Add `Index(value = ["parentId", "type"])` to the entity's `indices` array.
  Same one-file, no-`AppDatabase`-edit constraint as Task 7: the index activates with the
  next schema version bump; note it in the PR description. Before editing, re-verify the DAO
  predicate shapes in TeamNotificationDao.kt lines 8–13 still match.
- **Constraints**: 1 file, <10 lines; no `AppDatabase.kt` edit.
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*TeamNotification*"`;
  annotation check only.

### Task 9 — `LifeViewModel`: drop `@ApplicationContext` and the double user-id fallback

- **Roadmap**: 3, 10 (viewmodel holds no `Context`, state fully hoisted — the pattern Compose
  screens will need), 9.
- **Problem**: `LifeViewModel` injects `@ApplicationContext context` (ui/life/LifeViewModel.kt
  line 22) solely to call `MyLife.defaultItems(userId, context::getString)` (line 40), and
  re-implements active-user resolution inline (`resolveUserId`, lines 33–37:
  `getCurrentUserId().orEmpty().ifEmpty { getUserModel()?.id }`) instead of the shared
  `UserRepository.getActiveUserIdSuspending()` that every other ViewModel uses (e.g.
  `SubmissionViewModel`, `DashboardViewModel`).
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`
  - `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt` (one call-site
    argument only)
  - `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`
- **Approach**: Two moves. (a) Replace `resolveUserId()` with
  `userRepository.getActiveUserIdSuspending()` (UserRepository.kt line 77). (b) Move the
  label-resolution lambda across the boundary without the `Context`: have `LifeFragment`
  (sole caller of the ViewModel's `loadMyLifeList`) pass `requireContext()::getString` into
  the ViewModel call, so the lambda — not the `Context` — reaches
  `MyLife.defaultItems(userId, resolveLabel)` (model/MyLife.kt line 67). Keep
  `LifeRepository` unchanged. Remove the `Context` constructor param; update
  `LifeViewModelTest` constructor call sites.
- **Constraints**: <150 lines; `LifeAdapter.kt` untouched; `MyLife.kt` untouched;
  `LifeFragment.kt` edit limited to the one call-site argument.
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*Life*"`.

### Task 10 — `StorageBreakdownFragment`: hoist scan state into a ViewModel

- **Roadmap**: 3 (viewmodel expansion), 10 (state hoisting prerequisite for any future
  Compose port of the settings screens), 8.
- **Problem**: `StorageBreakdownFragment` (ui/settings/StorageBreakdownFragment.kt) owns the
  storage-scan state machine in the view layer: a `loadJob: Job?` (line 45), mutable
  `categories: List<CategoryData>` with `var sizeBytes`/`var fileCount` (lines 47–56,
  192–195), `scanStorage(context)` executed via `lifecycleScope.launch` +
  `withContext(dispatcherProvider.io)` inline (lines 181–209). State is lost on bottom-sheet
  recreation and the scan logic is only testable through Robolectric fragment tests.
- **Scope (files)**:
  - `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt`
  - `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt`
  - `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragmentTest.kt`
  - `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModelTest.kt`
- **Approach**: Verify first whether `StorageCategoryViewModel` (existing,
  ui/settings/StorageCategoryViewModel.kt) already exposes a scan entry point used by
  `StorageCategoryDetailFragment`; if yes, reuse its pattern in a new
  `StorageBreakdownViewModel` declared in `StorageCategoryViewModel.kt` (same file, avoids
  touching DI — ViewModels need no module registration beyond `@HiltViewModel`). Move
  `ScanResult`, `scanStorage(File)`, the available-space fetch, and the
  loading/empty/content tri-state into the ViewModel as a
  `StateFlow<StorageBreakdownUiState>`; `CategoryData` becomes immutable (copy-on-write in
  the state). The fragment reduces to collecting the flow and keeping the `freeUpSpace()`
  WorkManager orchestration (WorkManager is view-appropriate here — leave it). Move/adapt
  the pure scan tests from `StorageBreakdownFragmentTest` into plain JUnit tests against the
  ViewModel (the fragment already has `internal fun scanStorage(oleDir: File)` test seams at
  lines 213–239 — keep them `internal`).
- **Constraints**: ≤4 files, <150 lines; no new Hilt module changes; `FreeSpaceWorker`,
  `StorageCategories.kt`, `StorageCategoryDetailFragment.kt` untouched
  (`SettingsActivity.kt` is off-limits via #16101).
- **Validation**: `./gradlew testDefaultDebugUnitTest --tests "*Storage*"`.

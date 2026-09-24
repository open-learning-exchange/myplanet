# myPlanet refactor round — repository boundaries work orders

**Mode:** plan only (no code changes in the generating run)  
**Focus:** reinforce repository boundaries, stop cross-feature data leaks, move data work out of UI/services into repositories, light DAO/query polish, smoother repository ↔ ViewModel seams  
**Open-PR file coverage:** best-effort via GitHub MCP `get_files` on open PRs (aggregated exclusion set). Prefer the free domains below; do not touch files already in open PRs.

## Currently open pull requests (at plan generation)

| # | Title |
|---|--------|
| 17435 | resources: added filter labels indications (fixes #16922) |
| 17430 | Remove global MainApplication.listener cross-talk on team screens |
| 17356 | Refactor PublicSurveyActivity to PublicSurveyViewModel |
| 17254 | teams: refactored resources to match library (fixes #16927) |
| 17187 | resources: refactored filter logic (fixes #16282) |
| 16624 | search: smoother offline full text search matching (fixes #16615) |
| 16623 | teams: smoother interactive team tasks status and board handling (fixes #16616) |
| 16594 | actions: smoother size labeller fetching (fixes #16344) |
| 15951 | teams: smoother repository update requesting (fixes #15568) |
| 15825 | local event task reminders workmanager notifications (address #15115) |
| 15824 | gamification achievement hub offline badges streaks (address #15114) |
| 15820 | teams: smoother task and meetup comment threads managing (fixes #15112) |
| 15808 | sync: intelligent incremental sync via couchdb changes feed (fixes #15807) |
| 15559 | exam: redesign UI with elapsed timer and cards (fixes #15558) |
| 15267 | prevent download popup dialog cropping when text size is large (fixes #15263) |
| 15266 | prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265) |
| 15226 | feat(flutter): Flutter/Dart port of myPlanet (phases 1–28) |
| 15108 | fix event calendar marking (fixes #15107) |
| 14883 | team: add leaderboard tab (fixes #14880) |
| 14650 | survey: smoother submissions display (fixes #14619) |
| 14427 | Course streak |
| 13928 | Add baseline profile module and installer (fixes #13927) |
| 13848 | all: introduce Course/Grade models and wire into UI (fixes #13802) |
| 13657 | course: Archive course My Courses library (fixes #13559) |
| 13604 | teams: Add sort by completeness option in Survey section (fixes #13590) |
| 13415 | voices: Add emoji reactions (fixes #13357) |
| 13355 | Add P2P resource sharing (Wi‑Fi P2P) (fixes #13353) |
| 13287 | profile: no char limit for edit texts (fixes #13283) |
| 10993 | Voices video |
| 8175 | roboscript update (fixes #7986) |
| 4075 | robo movie (fixes #4074) |

**Off-limits this round (high-churn open-PR surfaces):** Courses/Progress/Surveys/Submissions/Teams/User/Voices/Chat/Community/Feedback/Health/Ratings/Tags/Events impls & many DAOs (`CourseDao`, `ExamDao`, `SubmissionDao`, `TeamTaskDao`, `NewsDao`, `AppDatabase`, `RoomModule`, `RepositoryModule`, …), Resources UI (`ResourcesFragment` / `ResourcesViewModel` / filter), team detail/calendar/leaderboard, exam UI, dashboards. Tasks below use only verified-free paths.

**Global constraints for every task:** independently mergeable; ≤ ~5 files; ≤ ~150 LOC; no new dependencies; no TODOs; no unused code; do not edit open-PR files; keep unit tests green for touched symbols.

**Roadmap map:** 1 data layer · 2 global nav · 3 ViewModel/use-case · 4 DI cleanup · 5 sync/upload · 6 Compose UI · 7 performance · 8 code health/tests · 9 KMP core · 10 CMP portable Compose

---

### Task 1 — Hide `LibraryTitleProjection` and stop Resources using `RemovedLogDao` directly

**Roadmap:** 1 (data layer), 3 (ViewModel boundary). **Also 9:** repository API stops exporting Room DAO types and activity-table DAOs.

**Why / verified problem**
- `ResourcesRepository.getLibraryTitles()` returns `List<LibraryTitleProjection>` and the interface imports `org.ole.planet.myplanet.data.room.dao.LibraryTitleProjection` (`ResourcesRepository.kt`).
- `AchievementViewModel.getLibraryTitles()` and `EditAchievementFragment.createResourceList(...)` import that DAO type.
- `ResourcesRepositoryImpl.getLibraryTitles()` returns the DAO projection unchanged.
- `ResourcesRepositoryImpl.addResourcesToUserLibrary` calls `removedLogDao.deleteByTypeUserAndDocsChunked(...)`; `removeResourcesFromShelf` builds `RemovedLog` rows via `removedLogDao.insertAll(...)` while `updateUserLibrary` already uses `activitiesRepository.markResourceAdded` / `markResourceRemoved`.
- `observeOpenedResourceIds` is incorrectly `suspend fun …: Flow<…>` and maps full `ResourceActivity` rows client-side.

**Allowed files (exactly these production files)**
1. `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
3. `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`
4. `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`
5. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`

**Also update tests that break (same PR, not other tasks’ files):**  
`app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`,  
`app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementViewModelTest.kt`,  
and any other test that only fails because of the type rename above.

**Do this**
1. Add a repository-layer data class (e.g. `LibraryTitle(id, title)`) next to other DTOs in `ResourcesRepository.kt`. Change `getLibraryTitles()` to return `List<LibraryTitle>`. Remove the DAO import from the interface.
2. In `ResourcesRepositoryImpl.getLibraryTitles()`, map `myLibraryDao.getLibraryTitles()` into `LibraryTitle`. Do not change `MyLibraryDao` or `LibraryTitleProjection` itself.
3. Update `AchievementViewModel.getLibraryTitles()` and `EditAchievementFragment` (`createResourceList` and call sites) to use `LibraryTitle` only — zero `data.room.dao` imports in UI.
4. Replace `removedLogDao` shelf add/remove paths with existing `ActivitiesRepository.markResourceAdded` / `markResourceRemoved` (already injected). Remove the `RemovedLogDao` constructor dependency and unused `RemovedLog` imports if nothing else needs them.
5. On `ResourceActivityDao`, add a Flow query that returns opened `resourceId` values for a user/type (no full entity list). Wire `observeOpenedResourceIds` to it; drop `suspend` from the repository signature so it is a plain `fun …: Flow<Set<String>>`. Do not edit blocked `ResourcesViewModel.kt` — removing `suspend` stays source-compatible.

**Do not**
- Touch `MyLibraryDao.kt`, `ActivitiesRepository*`, `RemovedLogDao.kt`, `ResourcesFragment` / `ResourcesViewModel`, DI modules, or entity schema / `AppDatabase`.
- Add new dependencies or leave dead `removedLogDao` fields.

**Acceptance**
- No UI/repository interface type is `LibraryTitleProjection`.
- `ResourcesRepositoryImpl` does not inject or call `RemovedLogDao`.
- `observeOpenedResourceIds` is non-suspend and uses the new DAO query.
- `./gradlew testDefaultDebugUnitTest --tests "…ResourcesRepositoryImplTest" --tests "…AchievementViewModelTest"` (and related) pass.

**Est. LOC:** ~90–130

---

### Task 2 — Life repository: require caller `userId`, drop `SharedPrefManager`

**Roadmap:** 1, 3. **Also 9:** remove Android prefs from Life domain repository.

**Why / verified problem**
- `LifeRepositoryImpl.updateVisibility` and `updateMyLifeListOrder` fall back to `sharedPrefManager.getUserId()` when row/`list` userId is missing (`LifeRepositoryImpl.kt`).
- `LifeViewModel` already resolves user via `UserRepository` (`resolveUserId()`) for loads, but visibility/order updates do not pass that id into the repository.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
3. `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`
4. `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`
5. `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`

**Do this**
1. Change `updateVisibility` / `updateMyLifeListOrder` (and any other prefs-backed fallbacks) so effective `userId` comes only from parameters and/or `MyLife.userId` on the entities — never `SharedPrefManager`.
2. Remove `SharedPrefManager` from `LifeRepositoryImpl` constructor and imports.
3. `LifeViewModel.updateVisibility` / `updateMyLifeListOrder`: resolve `userId` with existing `resolveUserId()` (or ensure list items carry userId) and pass it into the repository API as needed.
4. Keep cache/`LifeCache` behavior; do not edit `LifeCache.kt` in this task.
5. Update unit tests for new signatures and for “no SharedPrefManager”.

**Do not**
- Edit `MyLifeDao`, `LifeCache`, `MyLife` model, or UserRepository.
- Introduce a second user-id source of truth.

**Acceptance**
- `LifeRepositoryImpl` has no `SharedPrefManager` dependency.
- Visibility/reorder after VM actions still refresh `myLifeList` correctly for the active user.
- Life repository/VM tests green.

**Est. LOC:** ~40–70

---

### Task 3 — Diagnostics: drop `SharedPrefManager`; resolve planet codes via existing repositories

**Roadmap:** 1. **Also 9:** diagnostics logging path free of prefs.

**Why / verified problem**
- `DiagnosticsRepositoryImpl.resolveParentCode` / `resolvePlanetCode` use `sharedPrefManager.getParentCode()` / `getPlanetCode()` when `UserEntity` fields are blank.
- `ConfigurationsRepository` already exposes `getParentCode()` and community/planet helpers; `UserRepository.getUserModel()` is already used.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
2. `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`

Use only these two files if Hilt constructor change needs no module edit (concrete `@Inject` constructor swap is enough).

**Do this**
1. Replace `SharedPrefManager` with `ConfigurationsRepository` (already free; methods `getParentCode()`, `getCommunityConfiguration()` / planet type as needed). Prefer user model codes first, then configurations repository — never prefs.
2. Remove `SharedPrefManager` import/field.
3. Update `DiagnosticsRepositoryImplTest` mocks/verifies accordingly.
4. Keep `saveLogToRoom` / `saveLogsToRoom` / `ApkLogDao` behavior identical for happy paths.

**Do not**
- Edit `ConfigurationsRepository*`, `ApkLogDao`, `UserRepository*`, or DI modules.
- Change `DiagnosticsRepository` interface unless a signature change is truly required (prefer not).

**Acceptance**
- No `SharedPrefManager` in diagnostics impl.
- Pending log insert still sets parent/planet/version/user fields.
- Diagnostics tests green.

**Est. LOC:** ~25–45

---

### Task 4 — Personals: typed upload results, no `android.util.Log`, smoother active-user flow

**Roadmap:** 1, 3, 8. **Also 9:** remove Android Log from repository; typed results stay platform-free.

**Why / verified problem**
- `PersonalsRepository.uploadPersonal` returns free-form `String` status messages and uses `android.util.Log.w` on failures (`PersonalsRepositoryImpl.kt`).
- `PersonalsViewModel` maps those strings into `UploadState` and builds `personals` by calling `userRepository.getUserModel()` then `getPersonalResources(user?.id)` (multi-repo orchestration in the Flow builder).

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`
3. `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`
4. `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
5. `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModelTest.kt`

**Do this**
1. Introduce a small sealed result type on the repository interface (e.g. already uploaded / success / document failure / attachment failure) instead of raw English strings from the repository.
2. Implement mapping in `PersonalsRepositoryImpl.uploadPersonal`; delete all `android.util.Log` usage (return structured failures only).
3. `PersonalsViewModel.uploadPersonal`: map sealed results to existing `UploadState.Success` / `Error` UI strings (string resources or existing message text may live in the VM only).
4. Optional small VM cleanup: resolve `userId` once (still via `UserRepository`) and expose personals through a clearer helper — do not add methods to blocked `UserRepositoryImpl`.
5. Update both test classes.

**Do not**
- Touch `PersonalDao`, `UploadRepository*`, `PersonalsFragment`, or DI modules.
- Leave unused string constants or dual APIs (`uploadPersonal` old + new).

**Acceptance**
- Repository has no `android.util.Log` import.
- Upload outcomes are typed at the repository boundary; VM owns user-facing copy.
- Personals tests green.

**Est. LOC:** ~60–100

---

### Task 5 — Enterprises reports: stop UI depending on raw `MyTeam` report rows

**Roadmap:** 1, 3. **Also 9/10:** UI consumes a report DTO, not the teams Room entity shape.

**Why / verified problem**
- `EnterprisesRepository.getReportsFlow` returns `Flow<List<MyTeam>>`; `EnterprisesViewModel.getReportsFlow` and `EnterprisesReportsFragment` / `EnterprisesReportsAdapter` bind `MyTeam` finance fields and call `MyTeam.getAttachmentFile`.
- Report totals helper `reportTotals(report: MyTeam)` lives beside the adapter.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`
3. `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt`
4. `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`
5. `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt`

**Also fix tests broken only by the DTO rename:**  
`EnterprisesViewModelTest`, `EnterprisesReportsAdapterTest`, `EnterprisesReportsFragmentTest`, `EnterprisesRepositoryImplTest` as needed.

**Do this**
1. Add a `FinanceReportItem` (or equivalent) data class on the repository side with the fields the reports UI actually uses (`_id`, description, balances, dates, imageName, status, computed totals if useful).
2. Map `MyTeam` → DTO inside `EnterprisesRepositoryImpl.getReportsFlow` (keep DAO/`TeamDao` usage inside the impl).
3. Change ViewModel + Fragment + Adapter generics/callbacks to the DTO.
4. Keep attachment path resolution working: either pass a path/name the UI already understands or a small UI helper that does not require importing team membership APIs — do not expand into finances screens (`EnterprisesFinances*`).
5. Preserve add/update/archive/`exportReportsAsCsv` behavior.

**Do not**
- Edit `TeamDao`, `MyTeam` model, finances adapter/fragment, or Teams repositories.
- Redesign report layouts or strings beyond type plumbing.

**Acceptance**
- Reports UI package does not need `MyTeam` for list binding (attachment helper may remain a thin file-path call if unavoidable — prefer DTO carrying `reportId` + `imageName` only).
- CSV export and CRUD still work.
- Related unit tests green.

**Est. LOC:** ~100–140

---

### Task 6 — Notifications: remove direct `TeamTaskDao` ownership

**Roadmap:** 1, 4. **Also 9:** notifications domain depends on a task-lookup port, not a Room DAO type.

**Why / verified problem**
- `NotificationsRepositoryImpl` injects `TeamTaskDao` and calls `getById`, `getByIds`, `getByTitles`, `getTasksForUserBetween` (task detail, team-name enrichment, team notification badges).
- It already correctly uses `TeamsNotificationsRepository` for team labels/join requests — task rows are the remaining cross-feature leak.
- `TeamTaskDao.kt` / `TeamTask.kt` are open-PR-touched: **do not edit those files**; injecting the existing DAO from a new wrapper is allowed.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/TaskNotificationLookup.kt` (**new** interface + any small lookup DTOs if needed)
2. `app/src/main/java/org/ole/planet/myplanet/repository/TaskNotificationLookupImpl.kt` (**new** `@Inject` impl wrapping `TeamTaskDao` only)
3. `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
4. `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`
5. `app/src/test/java/org/ole/planet/myplanet/repository/TaskNotificationLookupImplTest.kt` (**new**, optional but preferred over leaving untested)

If keeping to four files, skip the new test file and extend `NotificationsRepositoryImplTest` coverage instead.

**Do this**
1. Define lookup methods that cover the four DAO call shapes used today (by id, ids, titles, user+time range). Return either `TeamTask` (type-only use, no file edit) or minimal data classes (`id`, `title`, `teamId`, `link`, …) if that keeps notifications free of `model.TeamTask` — prefer minimal DTOs when cheap.
2. Implement via `TeamTaskDao` in the new impl only.
3. `NotificationsRepositoryImpl`: inject the lookup port; delete `TeamTaskDao` field/import; keep `TeamsNotificationsRepository` / `VoicesRepository` / `NotificationDao` as they are.
4. Hilt: constructor `@Inject` on the concrete impl is enough if notifications depends on the concrete class **or** the interface is satisfied without touching blocked `RepositoryModule` (inject concrete type to avoid module edits).
5. Update notification tests to mock the lookup port.

**Do not**
- Edit `TeamTaskDao.kt`, `TeamTask.kt`, `TeamsRepositoryImpl`, `TeamsNotificationsRepository.kt`, `RepositoryModule`, `NotificationDao`, or UI notifications files.
- Move join-request/user enrichment (already on the right ports).

**Acceptance**
- `NotificationsRepositoryImpl` source has no `TeamTaskDao` reference.
- Task-related enrichment and `getTeamNotifications` behavior unchanged.
- Notification repository tests green.

**Est. LOC:** ~80–120

---

### Task 7 — Settings clear-data: one repository orchestration entry

**Roadmap:** 3, 1. **Also 9:** ViewModel stops sequencing two low-level clear operations.

**Why / verified problem**
- `SettingsViewModel.clearAllData()` calls `configurationsRepository.clearAllData()` then `configurationsRepository.clearPreferences()` separately.
- Both methods already exist on `ConfigurationsRepository` / `ConfigurationsRepositoryImpl`.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`
3. `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt`
4. `app/src/test/java/org/ole/planet/myplanet/ui/settings/SettingsViewModelTest.kt`
5. `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`

**Do this**
1. Add a single suspending API (e.g. `clearLocalAppData()`) on `ConfigurationsRepository` that performs table clear then preference clear in one place (order must stay: DB then prefs, matching today’s VM).
2. Implement in `ConfigurationsRepositoryImpl` by delegating to existing `clearAllData` / `clearPreferences` bodies (avoid copy-paste drift).
3. `SettingsViewModel.clearAllData()` calls only the new API, then emits `_clearDataEvent`.
4. Update Settings + Configurations tests: VM verifies one repo call; impl test covers orchestration order.

**Do not**
- Touch `AppDatabase`, `RoomModule`, retry/download paths, or Settings UI activity/fragment.
- Remove the underlying `clearAllData` / `clearPreferences` if other callers need them — keep them and have the new method call them.

**Acceptance**
- Settings VM no longer double-calls clear APIs.
- Clear-data behavior unchanged.
- Listed tests green.

**Est. LOC:** ~25–40

---

### Task 8 — Upload: make `UploadRepository` network-only; finish Room upload configs for exams/submissions

**Roadmap:** 5 (sync/upload), 1. **Also 9:** upload repository drops Exam/Submission DAO imports.

**Why / verified problem**
- `UploadRepositoryImpl` injects `ExamDao` + `SubmissionDao` and implements `markUploaded` for `UploadUpdateType.Exams` / `Submissions`.
- `UploadConfig.persistUploaded` always routes through that API.
- Remaining `UploadConfig` users in `UploadConfigs`: `AdoptedSurveys`, `ExamResults`, `Submissions` — everything else already uses `RoomUploadConfig` with domain `markUploaded` lambdas.
- `ExamDao` / `SubmissionDao` / Surveys & Submissions repository **impls** are open-PR-blocked — do not edit those files. Injecting the existing DAOs only inside `UploadConfigs` mark lambdas (service wiring) is acceptable as a step to free the network repository; prefer calling existing repository methods if any already mark exams/submissions without new interface methods on blocked files.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt`
3. `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`
4. `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfig.kt`
5. `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt`

**Also update:** `UploadRepositoryImplTest`, `UploadConfigsTest`, `UploadCoordinatorTest` as required for compile/green.

**Do this**
1. Convert `AdoptedSurveys`, `ExamResults`, and `Submissions` to `RoomUploadConfig` with explicit `markUploaded` lambdas that perform the same persistence `UploadRepositoryImpl.markUploaded` / `markExamsUploaded` does today (inline the logic next to other Room configs, or extract a private helper **inside** `UploadConfigs.kt` only).
2. Remove `markUploaded`, `UploadUpdateContract`, and `UploadUpdateType` from `UploadRepository` / impl; drop `ExamDao`/`SubmissionDao` constructor deps so the impl is API/network (+ attachment) only.
3. Delete or empty-migrate `UploadConfig` if no remaining call sites; remove `UploadCoordinator.upload(config: UploadConfig)` overload if unused. Leave `RoomUploadConfig` / `uploadRoom` as the single path.
4. Keep serializers/fetchPending/guest filters behavior identical.
5. Do not edit blocked Survey/Submission repository files; do not edit DAO source files.

**Do not**
- Change batch sizes, endpoints, or retry wiring outside these files.
- Leave orphaned `UploadUpdateType` references.
- Add new Gradle dependencies.

**Acceptance**
- `UploadRepositoryImpl` has no Room DAO imports.
- Exam results, submissions, and adopted surveys still mark local rows after successful upload.
- Upload unit tests green; coordinator still uploads Room configs.

**Est. LOC:** ~100–150

---

### Task 9 — Retry repository: remove `android.util.Log` boundary noise

**Roadmap:** 8, 1. **Also 9:** repository free of Android Log.

**Why / verified problem**
- `RetryRepositoryImpl` imports `android.util.Log` and logs at error/debug/warn across `executeOperation` paths while already returning `RetryOperationResult` and updating DAO state.

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`
2. `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`

**Do this**
1. Remove all `Log.*` calls and the `android.util.Log` import.
2. Keep control flow, DAO marks, and returned `RetryOperationResult` values identical.
3. Adjust tests if they asserted on log side effects (unlikely); ensure execute/enqueue tests still pass.

**Do not**
- Refactor retry algorithm, endpoints, or `RetryDao`.
- Introduce Timber or another logger dependency.

**Acceptance**
- No Android Log in retry repository.
- Retry behavior and tests unchanged functionally.

**Est. LOC:** ~15–30

---

### Task 10 — Activities chart: move monthly aggregation out of the Fragment

**Roadmap:** 3, 8. **Also 10:** chart input becomes hoisted state suitable for a future Compose screen (Fragment only renders).

**Why / verified problem**
- `ActivitiesFragment` collects `viewModel.offlineLogins` then calls `computeMonthlyCounts` (CPU fold over `OfflineActivity.loginTime`) before `renderChart`.
- `ActivitiesViewModel` only exposes raw `offlineLogins` from `ActivitiesRepository.getOfflineLogins` + `UserRepository.getUserModel`.
- Tests live on the Fragment (`ActivitiesFragmentTest.computeMonthlyCounts_*`).

**Allowed files**
1. `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt`
2. `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`
3. `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragmentTest.kt`
4. `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModelTest.kt`

**Do this**
1. Move monthly aggregation into `ActivitiesViewModel` (same windowing: now → minus one year) as a `StateFlow<Map<Int, Int>>` (or expose a pure function on the VM used to derive state). Use `DispatcherProvider` if the Fragment currently injects it only for this work — prefer VM-owned dispatchers consistent with other VMs; remove Fragment `DispatcherProvider` injection if it becomes unused.
2. Fragment collects aggregated counts and only renders chart/empty state.
3. Relocate unit tests from Fragment to ViewModel (preserve the empty-list and in-range grouping cases).
4. Do not change chart styling beyond what the move requires.

**Do not**
- Edit `ActivitiesRepository*` (blocked / out of scope), layouts, or MPAndroidChart dependencies.
- Keep duplicate `computeMonthlyCounts` on both layers.

**Acceptance**
- Fragment has no login-time aggregation logic.
- Chart still groups logins by month for the last year.
- Dashboard activities tests green.

**Est. LOC:** ~50–90

---

## Self-check (R1–R6)

| Rule | Status |
|------|--------|
| R1 Exactly 10 independent tasks | Yes — any order |
| R2 No shared files across tasks | Yes — production file sets disjoint (test files only update within their task) |
| R3 Open PRs listed; tasks avoid open-PR paths | Yes — free domains only; TeamTaskDao/ExamDao/etc. not edited |
| R4 Paths/symbols verified in tree | Yes — cited classes/functions opened before inclusion |
| R5 ~≤5 files, ~≤150 LOC, no new deps/TODOs/unused | Yes — scoped per task |
| R6 No implementation code in the generating deliverable | Yes — plan only |

**Roadmap coverage this round:** 1, 3, 4, 5, 7 (opened-resource query), 8, plus forward motion on **9** (and light **10** via Activities/Enterprises DTO/state hoist). Items 2/6 not targeted this round.

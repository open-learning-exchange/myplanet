# myPlanet refactor round — 10 work orders

**Open PRs checked (48):** #4075, #8175, #10993, #13287, #13355, #13415, #13604, #13657, #13848, #13928, #14427, #14650, #14883, #14893, #14960, #15108, #15158, #15198, #15226, #15266, #15267, #15412, #15519, #15559, #15699, #15808, #15820, #15824, #15825, #15951, #16101, #16270, #16594, #16619, #16623, #16624, #16647, #16661, #16677, #16680, #16686, #16688, #16690, #16693, #16698, #16701, #16702, #16705.

**Off-limits:** every path currently touched by those PRs (aggregated ~230+ files). Major blocked domains include `TeamsRepositoryImpl`, `CoursesRepository(+Impl)`, `ResourcesRepository(+Impl)`, `UserRepositoryImpl`, `CommunityRepositoryImpl`, `RepositoryModule`, `AppDatabase`, and DAOs such as `NotificationDao`, `CourseProgressDao`, `NewsDao`, `TeamTaskDao`, `OfflineActivityDao`, plus many feature ViewModels/Fragments already under review.

**Focus this round:** reinforce repository boundaries, stop cross-feature data leaks, push data orchestration out of UI/services into repositories, Room DAO push-down, smoother repository↔ViewModel seams. Each task is independently mergeable; no shared files across tasks.

---

### Task 1 — Seal MyLife cache behind SharedPrefManager and simplify MyLifeDao user predicates

**Roadmap:** 1 (data layer). Also **9** (repo stops calling Android `SharedPreferences.edit` / `androidx.core.content.edit`).

**Problem:** `LifeRepositoryImpl.cacheMyLifeItems` writes cache via `sharedPrefManager.rawPreferences.edit { putString(...) }` (`androidx.core.content.edit`). `getMyLifeForDashboard` reads the same raw key. `MyLifeDao.getByUserId` / `getVisibleByUserId` / `countByUserId` triple-copy a long nullable-user SQL predicate.

**Why now:** Isolated life vertical; high boundary value; free of open-PR collisions.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` (edit)
3. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt` (edit)
4. `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt` (edit)
5. `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt` (edit)

**Steps:**
1. On `SharedPrefManager`, add typed helpers for the existing `myLifeCache_<userId>` key space (get/set/clear JSON string). Keep key prefix behavior identical to `LifeRepositoryImpl`’s `MY_LIFE_CACHE_PREFIX`.
2. In `LifeRepositoryImpl`, replace `rawPreferences` / `edit { }` usage in `cacheMyLifeItems` and `getMyLifeForDashboard` with those helpers. Leave Gson + `CachedMyLifeItem` mapping in the repository.
3. In `MyLifeDao`, collapse the three duplicated user-match predicates into one equivalent form (e.g. `COALESCE(NULLIF(userId,''),'--') = COALESCE(NULLIF(:userId,''),'--')`) while preserving current guest/`--`/blank semantics used by `LifeRepositoryImpl.normalizeUserId`.
4. Update `LifeRepositoryImplTest` and `MyLifeDaoTest` for the new helpers and SQL behavior.

**Acceptance:**
- `LifeRepositoryImpl` has no `androidx.core.content.edit` / `rawPreferences.edit` usage.
- Cache read/write behavior for dashboard seeding unchanged.
- DAO queries still match null/blank/`--` user rows the same way.
- Existing life unit tests pass under `testDefaultDebugUnitTest`.

**Tests:** update the two listed test files.
**Out of scope:** `LifeViewModel`, `LifeRepository` interface, `UserRepository`, schema bumps.
**North star 9/10:** 9 — prefs Android API leaves the repository.
**Size budget:** ≤5 files, ~100–140 LOC.

---

### Task 2 — Stop exposing Room `DictionaryEntity` on DictionaryRepository

**Roadmap:** 1. Also **9** (repository surface is a plain domain type, not a Room entity).

**Problem:** `DictionaryRepository.findByWord` returns `DictionaryEntity` (`data/room/entity`). `DictionaryViewModel.DictionarySearchState.Found` and the search path therefore depend on a persistence type.

**Why now:** Dictionary stack is already VM-backed; this is the remaining Room leak on the interface. No open-PR file collisions.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` (edit)
3. `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt` (edit)
4. `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt` (edit)
5. `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt` (edit)

**Steps:**
1. Add a plain `data class DictionaryEntry` (word, definition, synonym, antonym, and any other fields the UI already reads) in `DictionaryRepository.kt` (or same package) — not annotated with Room.
2. Change `findByWord` to return `DictionaryEntry?`. Map from `DictionaryEntity` inside `DictionaryRepositoryImpl.findByWord` only. Keep `insertDictionaryData` writing `DictionaryEntity` via DAO.
3. Change `DictionarySearchState.Found` to hold `DictionaryEntry`. Do not edit `DictionaryActivity` unless field names change (today it reads `word` / `definition` / `synonym` / `antonym`).
4. Update both listed tests; do not touch `DictionaryDao` or `DictionaryEntity`.

**Acceptance:**
- No `data.room.entity.DictionaryEntity` import in `DictionaryRepository` or `DictionaryViewModel`.
- Search hit/miss behavior unchanged in the activity.
- Dictionary unit tests pass.

**Tests:** the two listed test files.
**Out of scope:** file download / `FileUtils` / `@ApplicationContext` removal (separate larger lift), Compose.
**North star 9/10:** 9 — domain type on the repository API.
**Size budget:** ≤5 files, ~80–120 LOC.

---

### Task 3 — Push non-archived report filtering into TeamDao and stop Enterprises reports using TeamsRepository for team name

**Roadmap:** 1 + 7 (DAO push-down / avoid load-all-then-filter). Also boundary cleanup (enterprises UI no longer calls teams prefs API).

**Problem:**
- `EnterprisesRepositoryImpl.exportReportsAsCsv` loads `teamDao.getByTeamIdAndDocType(teamId, "report")` then filters `status != "archived"` and sorts in Kotlin, while `observeNonArchivedReportsByTeamId` already encodes the non-archived predicate for Flow.
- `EnterprisesReportsFragment` calls `teamsRepository.getTeamNameFromPrefs()` three times for CSV/filename — a cross-feature leak into Teams.

**Why now:** Both are free files; high clarity win; no need to edit blocked `TeamsRepositoryImpl`.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` (edit)
3. `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt` (edit)
4. `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (edit)
5. `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt` (edit)

**Steps:**
1. Add `TeamDao` one-shot `getNonArchivedReportsByTeamId(teamId)` mirroring the WHERE/ORDER BY of existing `observeNonArchivedReportsByTeamId` (line 23).
2. Change `exportReportsAsCsv` to use that method; drop in-memory archived filter/sort.
3. Inject existing `SharedPrefManager` into `EnterprisesViewModel` (constructor only; do not edit `SharedPrefManager.kt`). Add a thin `exportReportsAsCsv(teamId: String)` that resolves `sharedPrefManager.getTeamName().orEmpty()` and delegates to the repository overload that already takes `teamName`.
4. In `EnterprisesReportsFragment`, replace every `teamsRepository.getTeamNameFromPrefs()` use with ViewModel/SharedPref-backed team name (filename sanitize stays in UI). Remove teams-repo dependency for this screen path if it is only used for the name.
5. Extend `EnterprisesRepositoryImplTest` for CSV export against non-archived DAO results.

**Acceptance:**
- CSV export does not call `getByTeamIdAndDocType` + Kotlin archived filter.
- Enterprises reports UI does not call `TeamsRepository.getTeamNameFromPrefs`.
- Exported CSV content and naming behavior stay equivalent for non-archived reports.
- Enterprises repository tests pass.

**Tests:** `EnterprisesRepositoryImplTest`.
**Out of scope:** `TeamsRepository` / `TeamsRepositoryImpl`, finances, membership.
**North star 9/10:** partial 9 — less cross-feature Android prefs coupling in UI.
**Size budget:** ≤5 files, ~90–130 LOC.

---

### Task 4 — Parse community leaders inside ConfigurationsRepository

**Roadmap:** 1 + 3 (data parsing leaves ViewModel). Also **9** (JSON parse behind repository).

**Problem:** `LeadersViewModel.loadLeaders` calls `configurationsRepository.getCommunityLeaders()` (raw String) then `UserEntity.parseLeadersJson` in the ViewModel. Parsing belongs with configuration data access.

**Why now:** Tiny surface; existing `UserEntity.parseLeadersJson` and `LeadersViewModelTest` already encode expected behavior.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (edit)
3. `app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersViewModel.kt` (edit)
4. `app/src/test/java/org/ole/planet/myplanet/ui/community/LeadersViewModelTest.kt` (edit)

**Steps:**
1. Keep `getCommunityLeaders(): String` for other callers (`SharedPrefManager` raw string still used elsewhere).
2. Add `getCommunityLeaderUsers(): List<UserEntity>` on `ConfigurationsRepository`; implement in `ConfigurationsRepositoryImpl` by reading the same source as `getCommunityLeaders()` and calling `UserEntity.parseLeadersJson`. Empty string → empty list.
3. `LeadersViewModel` only calls `getCommunityLeaderUsers()` and assigns the list to `_leaders` (no `parseLeadersJson` in the VM).
4. Update `LeadersViewModelTest` mocks/assertions for the new method.

**Acceptance:**
- `LeadersViewModel` has no `UserEntity.parseLeadersJson` call.
- Empty leaders string still yields empty list UI state.
- Leaders ViewModel tests pass.

**Tests:** `LeadersViewModelTest`.
**Out of scope:** Voices/Teams call sites still using `parseLeadersJson` + raw prefs (blocked/other tasks); do not edit `UserEntity.kt`.
**North star 9/10:** 9 — parse not in UI layer.
**Size budget:** 4 files, ~40–70 LOC.

---

### Task 5 — Move notification feed enrichment out of NotificationsViewModel

**Roadmap:** 3 + 1. Also **10** (VM keeps presentation/`R.string` formatting only).

**Problem:** `NotificationsViewModel.loadNotifications` fans out task/join-request ID collection, `parseTaskDate`, parallel `getTaskTeamNamesByTaskIds` / `getTaskTeamNamesByTaskTitles` / `getJoinRequestDetailsBatch` / `getUnreadCount`, then maps via `formatNotification`. That is repository orchestration living in the VM. (`NotificationDao` is off-limits — do not touch it.)

**Why now:** Highest remaining free notifications boundary fix without colliding #16677.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (edit)
3. `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (edit)
4. `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` (edit)
5. `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` (edit)

**Steps:**
1. Add a small result type on the repository API (e.g. `NotificationLoadResult`) holding: `payloads: List<NotificationPayload>`, `taskTeamNames: Map<String,String>`, `joinRequestDetails: Map<String, Pair<String,String>>`, `parsedTaskDates: Map<String, Pair<String,String>?>`, `unreadCount: Int`.
2. Implement `loadNotificationFeed(userId, filter, isAdmin)` in `NotificationsRepositoryImpl` by moving the enrichment loop/async batching currently in the VM (still using existing repo helpers `getNotifications`, `getTaskTeamNamesByTaskIds`, `getTaskTeamNamesByTaskTitles`, `getJoinRequestDetailsBatch`, `getJoinRequestDetails`, `getUnreadCount`). Move `parseTaskDate` logic into the repository (or a package-private helper in the same Impl file) so the VM does not own task-title extraction for batching.
3. `NotificationsViewModel.loadNotifications` becomes: call `loadNotificationFeed`, then map each payload with existing `formatNotification` / `R.string` helpers only. Keep grouping/selection/`typeLabelFor` in the VM (those need `Context`/`R`).
4. Update both notification test files for the new API; do not edit `NotificationDao.kt`.

**Acceptance:**
- VM `loadNotifications` no longer calls the four batch helpers directly or builds task/join ID lists.
- Formatted notification text and unread counts unchanged for existing fixtures.
- Notification unit tests pass.

**Tests:** the two listed test files.
**Out of scope:** DAO markSynced work, notification worker, Compose.
**North star 9/10:** 10 — presentation stays in VM; data join graph in repository.
**Size budget:** ≤5 files, ~120–150 LOC.

---

### Task 6 — Single profile activity-stats API + SQL most-opened resource

**Roadmap:** 3 + 7. Also **9** (profile stats aggregation not in UI).

**Problem:** `UserProfileViewModel` `init` fans out `getMostOpenedResource`, `getGlobalLastVisit`, `getResourceOpenCount`. `ActivitiesRepositoryImpl.getMostOpenedResource` loads all matching rows via `resourceActivityDao.getByUserAndType` then groups in memory. (`OfflineActivityDao` / `UserRepositoryImpl` are off-limits.)

**Why now:** Free activities/profile stack; clear repo↔VM smoothing.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` (edit)
3. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt` (edit)
4. `app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt` (edit)
5. `app/src/test/java/org/ole/planet/myplanet/ui/user/UserProfileViewModelTest.kt` (edit)

**Steps:**
1. Add a Room query on `ResourceActivityDao` that returns the top title/count for `(userName, type)` (group by resource id/title, order by count desc, limit 1). Use a small query result type local to the DAO file if needed.
2. Point `getMostOpenedResource` at that query (preserve `Pair<String, Int>?` contract for any other callers).
3. Add `data class ProfileActivityStats` + `suspend fun getProfileActivityStats(userName: String, resourceOpenType: String): ProfileActivityStats` on `ActivitiesRepository`, implemented by combining most-opened, `getGlobalLastVisit()`, and `getResourceOpenCount` inside the Impl (same dispatcher discipline as today).
4. `UserProfileViewModel` init uses one `getProfileActivityStats(fullName, UserSessionManager.KEY_RESOURCE_OPEN)` call to fill `_maxOpenedResource`, `_lastVisit`, `_numberOfResourceOpen`. Leave `getOfflineVisits()` as-is (uses `getOfflineVisitCount` / user id).
5. Update `UserProfileViewModelTest` mocks.

**Acceptance:**
- Profile init makes one activities-repo stats call (plus existing user name lookup).
- Most-opened no longer loads the full activity list into memory for aggregation.
- Display strings for max opened / open count remain the same format.
- Profile ViewModel tests pass.

**Tests:** `UserProfileViewModelTest` (add/adjust repository test coverage only if required for compile).
**Out of scope:** `UserRepositoryImpl`, offline-visit DAO, upload activity paths.
**North star 9/10:** 9/10 — stats domain method, VM only formats.
**Size budget:** ≤5 files, ~100–140 LOC.

---

### Task 7 — Order personal resources in SQL

**Roadmap:** 1 + 7 (Room ordering).

**Problem:** `PersonalDao.getByUserIdFlow` is `SELECT * FROM my_personal WHERE userId = :userId` with no `ORDER BY`. `PersonalsRepositoryImpl.getPersonalResources` exposes that Flow directly; list order is non-deterministic across SQLite versions.

**Why now:** One-line DAO fix with clear UX impact; free files.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` (edit only if a comment/test hook or distinctBy needs to acknowledge order — prefer DAO-only behavior change; keep file in set only if Impl needs a touch for compile/docs of ordering guarantee, otherwise still allowed for a one-line pass-through assert in test via Impl)
3. `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` (edit)

**Steps:**
1. Change `getByUserIdFlow` to `ORDER BY date DESC, title COLLATE NOCASE ASC` (uses existing `Personal.date` / `title` columns).
2. Keep `PersonalsRepositoryImpl.getPersonalResources` wiring unchanged unless tests need a clearer assertion path.
3. Add/adjust `PersonalsRepositoryImplTest` (or DAO-level assertion through the repository Flow) proving newer `date` sorts first.

**Acceptance:**
- Flow emissions are stably ordered by date descending.
- Upload/update/delete personal paths unchanged.
- Personals repository tests pass.

**Tests:** `PersonalsRepositoryImplTest`.
**Out of scope:** `PersonalsViewModel`, upload HTTP, schema version bump.
**North star 9/10:** no.
**Size budget:** ≤3 files, ~20–40 LOC.

---

### Task 8 — Hoist CourseStepFragment data orchestration into CourseStepViewModel

**Roadmap:** 3. Also **10** (state ready for a future Compose course-step screen).

**Problem:** `CourseStepFragment` injects `configurationsRepository` and `progressRepository`, and via `BaseContainerFragment` uses `coursesRepository`, `userRepository`, and `resourcesRepository` inside `lifecycleScope` for `loadStepData` / title / progress save / server check / download / prefetch. Multi-repo orchestration lives in the Fragment. (Do **not** edit blocked course/resource/progress repository files.)

**Why now:** UI-only extraction; repositories stay untouched; free of open-PR collisions on this fragment.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepViewModel.kt` (**create**)
3. `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseStepViewModelTest.kt` (**create**)

**Steps:**
1. Create `@HiltViewModel CourseStepViewModel` injecting the same repositories the fragment uses today: `CoursesRepository`, `UserRepository`, `ProgressRepository`, `ConfigurationsRepository`, `ResourcesRepository`, and `ResourceDownloadCoordinator` as needed for download orchestration already in the fragment.
2. Move into the VM: resolve user; `getCourseStepData`; `getCourseTitleById`; `saveCourseProgress`; `checkServerAvailability` + `downloadResourcesPriority`; `getAllStepResources` for current/next step; expose a single UI state (step, resources, exams, survey, title, `userHasCourse`, download progress flags) via `StateFlow`.
3. Fragment keeps View binding, markdown/AI selection, navigation to exam/chat, and adapter wiring; collect VM state instead of launching repository calls directly. Remove fragment `@Inject` of configurations/progress repositories if fully unused.
4. Unit-test VM load + save + empty-step paths with MockK.

**Acceptance:**
- Fragment has no direct calls to `coursesRepository` / `userRepository` / `progressRepository` / `configurationsRepository` / `resourcesRepository` for the load/save/download paths listed above (coordinator may remain if download start must stay UI-tied — prefer VM).
- Step UI still shows title, resources count, tests/surveys, and still saves progress when `userHasCourse`.
- New ViewModel tests pass.

**Tests:** new `CourseStepViewModelTest`.
**Out of scope:** editing any `*RepositoryImpl`, `BaseContainerFragment`, Compose migration of the screen.
**North star 9/10:** 10 — state hoisted for portable UI.
**Size budget:** 3 files, ~120–150 LOC.

---

### Task 9 — Move finance header totals onto Transaction domain helper

**Roadmap:** 3. Also **9** (pure Kotlin totals, no Android).

**Problem:** `EnterprisesFinancesViewModel.calculateTotal` walks transactions summing credit/debit and builds `FinanceHeaderState`. That pure domain math does not need a ViewModel (and cannot move into `TeamsFinancesRepository` without editing blocked `TeamsRepositoryImpl`).

**Why now:** Smallest free finances boundary; existing `EnterprisesFinancesViewModelTest`.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/model/Transaction.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModel.kt` (edit)
3. `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModelTest.kt` (edit)

**Steps:**
1. On `Transaction`, add a pure function/companion (e.g. `summarize(list): FinanceTotals` with debit, credit, total = credit − debit).
2. `EnterprisesFinancesViewModel.calculateTotal` delegates to it and only maps into `FinanceHeaderState` (`isCautionVisible = total < 0`).
3. Extend finances ViewModel tests (or add direct assertions on the helper via the VM) for credit-only, debit-only, mixed, empty.

**Acceptance:**
- Totals math lives in `Transaction` (or its companion), not as private VM arithmetic beyond mapping.
- Header caution visibility still true iff total < 0.
- Finances ViewModel tests pass.

**Tests:** `EnterprisesFinancesViewModelTest`.
**Out of scope:** `TeamsFinancesRepository` / `TeamsRepositoryImpl`, transaction create path.
**North star 9/10:** 9 — pure Kotlin domain helper.
**Size budget:** 3 files, ~40–70 LOC.

---

### Task 10 — Remove Application Context from DiagnosticsRepositoryImpl version reads

**Roadmap:** 1 + 4 (DI/data cleanup). Also **9** (no `Context` in this repository impl).

**Problem:** `DiagnosticsRepositoryImpl` takes `@ApplicationContext Context` solely to call `VersionUtils.getVersionName(context)` in `saveLogToRoom` / `saveLogsToRoom`. That keeps an Android `Context` dependency on an otherwise DAO/prefs repository.

**Why now:** One-file behavioral fix; Diagnostics tests already exist; no `RepositoryModule` change required if Context is simply dropped from the `@Inject constructor`.

**Files (only these):**
1. `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` (edit)
2. `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt` (edit)

**Steps:**
1. Remove `@ApplicationContext Context` from the constructor.
2. Resolve version name without a `Context` parameter — prefer `BuildConfig.VERSION_NAME` (already available to the app module) for apk log `version` field. Do not expand `VersionUtils` unless absolutely required; do not edit `VersionUtils.kt` in this task.
3. Keep `ApkLogDao`, `UserRepository`, and `SharedPrefManager` usage for parent/planet codes unchanged.
4. Update `DiagnosticsRepositoryImplTest` construction/mocks (no Context).

**Acceptance:**
- `DiagnosticsRepositoryImpl` has zero `android.content.Context` import/usage.
- Pending apk logs still persist with a non-null version string equivalent to the app version name.
- Diagnostics repository tests pass.

**Tests:** `DiagnosticsRepositoryImplTest`.
**Out of scope:** `CrashLogStore`, upload of apk logs, new DI modules (`RepositoryModule` is off-limits).
**North star 9/10:** 9 — Context-free diagnostics repository.
**Size budget:** 2 files, ~20–40 LOC.

---

## Self-check (P5)

| Rule | Status |
|------|--------|
| R1 exactly 10 independent tasks | yes |
| R2 no file in more than one task | yes (38 unique existing paths + 2 new CourseStep files only in task 8; DictionaryEntry nested in task 2’s repository file) |
| R3 open-PR files avoided | yes (checked against aggregated open-PR path set) |
| R4 cited paths/classes/functions verified on disk | yes |
| R5 ≤~5 files / ~150 LOC, no new deps/TODOs | yes |
| R6 plan only, no implementation in this run | yes |

**Roadmap coverage this round:** 1 (tasks 1–5, 7, 10), 3 (4–6, 8–9), 4 (10), 7 (3, 6, 7), 8 via tests on each; **9** called out on 1, 2, 4, 6, 9, 10; **10** on 5 and 8.

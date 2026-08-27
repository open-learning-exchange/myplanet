# myPlanet refactor round — repository-boundary work orders

**Role:** plan only (no implementation in the generating session)  
**Focus:** reinforce repository boundaries; move data orchestration out of UI; DAO query push-down; smoother repository↔ViewModel wiring  
**Open PRs checked:** yes (GitHub API, 37 open). Any path touched by an open PR is off-limits for these tasks. Locked inventory ≈370 unique paths (from open PR file lists + PR #16192 `CommunityServicesFragment.kt` and PR #16096 `LifeFragment.kt` / `fragment_life.xml` / `LifeViewModelTest.kt` supplements).

## Open pull requests (do not touch their files)

| # | Title |
|---|--------|
| 16274 | Refactor SyncActivity sync and upload logic (fixes #16265) |
| 16270 | Initialize Codex Cloud skill submodules with .codex/setup.sh and update AGENTS.md |
| 16258 | resources: updated search bar color in dark mode (fixes: #16129) |
| 16257 | resources: added download filter (fixes #16102) |
| 16192 | Optimize CommunityServicesFragment link list rendering |
| 16101 | all: consistent status bar (fixes #16099) |
| 16096 | life: smoother fragment layout sizing and list loading (fixes #16089) |
| 15951 | teams: smoother repository update requesting (fixes #15568) |
| 15825 | local event task reminders workmanager notifications (address #15115) |
| 15824 | gamification achievement hub offline badges streaks (address #15114) |
| 15820 | teams: smoother task and meetup comment threads managing (fixes #15112) |
| 15808 | sync: intelligent incremental sync via couchdb changes feed (fixes #15807) |
| 15699 | resources: updated rating dialog and added unit tests (fixes #15444) |
| 15559 | exam: redesign UI with elapsed timer and cards (fixes #15558) |
| 15519 | add dismiss button to last synced status container (fixes #15516) |
| 15412 | courses limited space for content in landscape mode (fixes #15264) |
| 15267 | prevent download popup dialog cropping when text size is large (fixes #15263) |
| 15266 | prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265) |
| 15226 | feat(flutter): Flutter/Dart port of myPlanet (phases 1–28) |
| 15198 | Preserve input text when switching AI providers (fixes #15125) |
| 15158 | teams: allow deleting calendar events (fixes #15111) |
| 15108 | fix event calendar marking (fixes #15107) |
| 14960 | login: make login history visible and scrollable in landscape mode (fixes #14947) |
| 14893 | dashboard: fix ui cropping and logo stretching in landscape orientation (fixes #14891) |
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

**Also locked this round (examples that blocked hotter candidates):** `TeamsRepository(.kt/Impl)`, `CoursesRepository(.kt/Impl)`, `UserRepositoryImpl`, `CommunityRepository(.kt/Impl)`, `di/RepositoryModule.kt`, `di/RoomModule.kt`, `AppDatabase.kt`, many DAOs (`ExamDao`, `TeamTaskDao`, …), `CommunityServicesFragment.kt` (#16192), `LifeFragment.kt` (#16096). **Do not edit `RepositoryModule` / `RoomModule`** — no new `@Binds` / DAO providers. New `@HiltViewModel` classes need no module change.

**Global constraints for every task:** ≤~5 files, ≤~150 LOC net change, no new dependencies, no unused code, no TODO placeholders, independently mergeable, file sets pairwise disjoint.

---

## Task 1 — Hoist DictionaryActivity repository calls into DictionaryViewModel

**Roadmap:** 3 (ViewModel layer). Also **10** (state hoisted out of UI for future Compose).

**Problem:** `DictionaryActivity` injects `DictionaryRepository` and runs `count()`, `insertDictionaryData()`, and `findByWord()` inside `lifecycleScope`. That is UI-owned data orchestration with no ViewModel.

**Why now:** Small, free surface; cleanest NO_VM → VM conversion in the dictionary stack. Does not touch open-PR files.

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt` (**create**)
3. `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt` (**create**)

**Steps:**

1. Create `@HiltViewModel DictionaryViewModel` injecting only `DictionaryRepository` (and `DispatcherProvider` if you need IO — match other VMs).
2. Expose: load/count refresh, `ensureDictionaryLoaded(): DictionaryLoad` (delegate `insertDictionaryData()`), `search(word): DictionaryEntity?`, and a `StateFlow`/`SharedFlow` for count + last search result / errors. Do **not** start downloads inside the VM — keep `DownloadUtils.openDownloadService` and toasts in the Activity.
3. Replace Activity `@Inject dictionaryRepository` with `by viewModels()`. On create and on download-complete broadcast, call VM methods and collect state into `tvResult` / click listener.
4. Unit-test VM with MockK: count path, `DictionaryLoad.FileMissing` vs `Inserted`, search hit/miss.

**Acceptance:**

- Activity has zero `DictionaryRepository` injection or direct calls.
- Download-complete still reloads via VM.
- Search UI behavior unchanged.
- New unit tests pass under `testDefaultDebugUnitTest`.

**Tests:** `DictionaryViewModelTest` as above.

**Non-goals:** Do not edit `DictionaryRepository` / `Impl` / DAO; no Compose; no BroadcastService redesign.

**North star 9/10:** 10 — screen state lives in VM with no Android Views inside the VM.

**Size budget:** ~3 files, ~80–120 LOC.

---

## Task 2 — Hoist TeamCoursesFragment multi-repo orchestration into TeamCoursesViewModel

**Roadmap:** 3. Also **10**.

**Problem:** `TeamCoursesFragment` (extends free `BaseTeamFragment`) launches coroutines that fan out to `teamsRepository.getTeamCourseIds` / `getTeamCreator` / `removeCourseFromTeam` / `addCoursesToTeam` and `coursesRepository.getCoursesByIds` / `getAllCourses`, plus in-memory `filter { courseId !in existingIds }` for the add dialog.

**Why now:** Clear cross-repo UI orchestration; both repository **interfaces** are injectable as-is (do **not** edit locked `TeamsRepository.kt` / `CoursesRepository.kt` or their Impls). Existing methods already cover the calls.

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt` (**create**)
3. `app/src/test/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModelTest.kt` (**create**)

**Steps:**

1. Create `@HiltViewModel TeamCoursesViewModel` injecting `TeamsRepository`, `CoursesRepository`, `SharedPrefManager`.
2. Move list load into something like `loadCourses(teamId)` → course list + `canRemove` (creator vs `sharedPrefManager.getUserId()`).
3. Move add-dialog data prep into `loadAvailableCourses(teamId)` using existing `getTeamCourseIds` + `getAllCourses` and filtering out existing IDs **inside the VM** (not the Fragment).
4. Move `removeCourseFromTeam` / `addCoursesToTeam` success/failure into VM `Result` or one-shot events; Fragment only shows toasts and refreshes list from state.
5. Fragment uses `by viewModels()`, keeps dialog UI chrome (AlertDialog / CheckboxAdapter) only.
6. MockK tests for load, available-filter, add/remove success and failure.

**Acceptance:**

- Fragment has no direct `teamsRepository` / `coursesRepository` calls for these flows (membership/team still may come from `BaseTeamFragment`).
- Add-dialog still excludes courses already on the team.
- Tests cover happy path + empty available list.

**Non-goals:** No new Teams/Courses repository methods; no `RepositoryModule`; no adapter redesign; do not edit `BaseTeamFragment`.

**North star 9/10:** 10 — list/dialog data hoisted.

**Size budget:** ~3 files, ~100–140 LOC.

---

## Task 3 — Hoist TeamResourcesFragment repository calls into TeamResourcesViewModel

**Roadmap:** 3. Also **10**.

**Problem:** `TeamResourcesFragment` owns all team-resource data I/O: `getTeamResources`, `isTeamLeader`, `getAvailableResourcesToAdd`, `addResourceLinks`, `removeResourceLink`, `recordTeamActivity` via `teamsRepository` in multiple `lifecycleScope.launch` blocks.

**Why now:** Parallel to Task 2 but disjoint files; same locked-interface constraint — call **existing** `TeamsRepository` APIs only.

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesFragment.kt` (edit)
2. `app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesViewModel.kt` (**create**)
3. `app/src/test/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesViewModelTest.kt` (**create**)

**Steps:**

1. Create `@HiltViewModel TeamResourcesViewModel` with `TeamsRepository`.
2. APIs: `loadLibrary(teamId, userId)`, `loadAvailableToAdd(teamId)`, `addLinks(teamId, resources, userId)`, `removeLink(teamId, resourceId)` + `recordTeamActivity()` on success — mirror current order of calls.
3. Expose `StateFlow` of library list + `canRemove` / one-shot failure events (map to `R.string.failed_to_remove_resource` only in Fragment).
4. Fragment keeps FAB visibility from existing `isMemberFlow`, dialog UI, and `AddResourceFragment` show path; all repository calls go through VM.
5. Unit-test load, add, remove success/failure with MockK.

**Acceptance:**

- No `teamsRepository.*` calls remain in `TeamResourcesFragment` for list/add/remove/activity recording.
- Leader-only remove and member FAB behavior unchanged.
- Tests pass.

**Non-goals:** No TeamsRepository edits; no `AddResourceFragment` changes; no BaseTeam changes.

**North star 9/10:** 10.

**Size budget:** ~3 files, ~100–140 LOC.

---

## Task 4 — Push MyLife dashboard visibility filter into MyLifeDao

**Roadmap:** 1 (data layer) + 7 (hotspot). Mild **9** (less in-memory domain filtering in repository).

**Problem:** `LifeRepositoryImpl.getMyLifeForDashboard` loads **all** rows via `myLifeDao.getByUserId` then `filter { it.isVisible }` (and again on cache/seed paths). `MyLifeDao` has no visible-only query. (`LifeFragment` / `LifeViewModelTest` are locked by #16096 — do not touch them.)

**Confirmed APIs:**

- `MyLifeDao.getByUserId(userId)`
- `LifeRepository.getMyLifeForDashboard(userId, seedBase)`
- Entity field `isVisible: Boolean` on `MyLife`

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
3. `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`

**Steps:**

1. Add `getVisibleByUserId(userId: String?): List<MyLife>` with SQL equivalent to existing user predicate **plus** `AND isVisible = 1`, same `ORDER BY weight`.
2. In `getMyLifeForDashboard`, when DB has rows, call `getVisibleByUserId` (still apply `distinctBy { dedupKey() }` if needed for parity).
3. Keep cache JSON path filtering visible items in memory (cache DTO has no Room). After seed, prefer visible DAO query instead of full list + filter.
4. Extend `LifeRepositoryImplTest` for dashboard-visible filtering (mock DAO returning mixed visibility).

**Acceptance:**

- No `.filter { it.isVisible }` on live DAO results in `getMyLifeForDashboard`.
- Dashboard still seeds when empty; order by weight preserved.
- Existing + new tests green.

**Non-goals:** Do not edit `LifeViewModel`, `LifeFragment`, `MyLife` entity, `LifeRepository` interface (signature already sufficient), or bump `AppDatabase`.

**North star 9/10:** 9 — repository less responsible for row predicates Room can express.

**Size budget:** ~3 files, ~40–70 LOC.

---

## Task 5 — Push MyLibrary `needToUpdate()` filters into MyLibraryDao

**Roadmap:** 1 + 7. Mild **9**.

**Problem:** `ResourcesRepositoryImpl` repeatedly loads shelves then filters with `MyLibrary.needToUpdate()`:

```text
needToUpdate() = !resourceOffline || (resourceLocalAddress != null && _rev != downloadedRev)
```

Call sites include `getLibraryListForUser` (~194), `countLibrariesNeedingUpdate` (~212–215), `getAllLibrariesToSync` (~431), `getDownloadSuggestionList` (~464, ~470).

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
3. `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`

**Steps:**

1. Add DAO queries that encode the same predicate (Boolean `resourceOffline` as 0/1), e.g.:
   - needing-update for public+user pattern
   - count needing-update for public+user pattern
   - needing-update for `getPublic()`
   - optional: syncable ∩ needing-update (note: `getSyncable()` is already `resourceOffline = 0`, for which `needToUpdate()` is always true — keep behavioral parity; either keep `getSyncable()` alone or document that the extra filter is a no-op)
2. Replace in-memory `.filter { it.needToUpdate() }` / `.count { it.needToUpdate() }` at the sites above with the new DAO methods. **Do not** change method signatures on `ResourcesRepository` interface.
3. Leave `MyLibrary.needToUpdate()` in the model for any non-DAO callers; do not delete it in this task.
4. Add/adjust unit tests with MockK verifying the new DAO methods are invoked and counts/lists match prior semantics on fixtures.

**Acceptance:**

- Those four repository methods no longer call `needToUpdate()` in Kotlin filters.
- Download-suggestion and count behaviors unchanged for typical fixtures.
- Tests pass; no schema version bump required (query-only).

**Non-goals:** No removal of `Context` / `MainApplication` / `R.string` from `ResourcesRepositoryImpl` (separate later work); no interface renames; no search/`RawQuery` rewrite.

**North star 9/10:** 9 — less entity-method filtering in Android repository.

**Size budget:** ~3 files, ~80–120 LOC.

---

## Task 6 — Drop dead cross-domain `ExamDao` from NotificationsRepositoryImpl

**Roadmap:** 1 + 4 (DI cleanup). Mild **9**.

**Problem:** `NotificationsRepositoryImpl` constructor injects `ExamDao` (`private val examDao: ExamDao`) but **the only occurrence of `examDao` in the file is the constructor parameter** — dead cross-domain DAO leak. `TeamTaskDao` is still used (and `TeamTaskDao.kt` is locked — leave it).

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
2. `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Steps:**

1. Remove `examDao` parameter and `ExamDao` import from `NotificationsRepositoryImpl`.
2. Update `NotificationsRepositoryImplTest` setUp: drop `examDao` mock and constructor argument (today it passes `examDao` into the Impl).
3. Run notification repository tests; no production behavior change.

**Acceptance:**

- Impl compiles without `ExamDao`.
- Test constructs Impl with remaining deps only (`userRepository`, `teamsRepository`, `timeProvider`, `teamNotificationDao`, `notificationDao`, `teamTaskDao`, `voicesRepository`).
- Full `NotificationsRepositoryImplTest` green.

**Non-goals:** Do not migrate `TeamTaskDao` usage to `TeamsRepository` (would need locked interface/DAO work); do not edit `NotificationsRepository` interface or UI.

**North star 9/10:** 9 — removes an unused Android Room dependency edge from notifications domain.

**Size budget:** ~2 files, ~15–30 LOC.

---

## Task 7 — Remove Application Context from PersonalsRepository upload path

**Roadmap:** 1. Strong **9**.

**Problem:** `PersonalsRepositoryImpl` takes `@ApplicationContext Context` solely so `uploadPersonalDocument` can call `NetworkUtils.getCustomDeviceName(context)` when serializing via `Personal.serialize(personal, customDeviceName)`. That pins the personals domain repository to Android.

**Confirmed:** `PersonalsRepository.uploadPersonalDocument` / `uploadPersonal`; only UI caller of upload is `PersonalsViewModel.uploadPersonal` ← `PersonalsFragment`.

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`
3. `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
4. `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`
5. `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt`

**Steps:**

1. Change interface to pass device name explicitly, e.g. `uploadPersonalDocument(personal, customDeviceName: String)` and `uploadPersonal(personal, customDeviceName: String)` (or only the outer `uploadPersonal` if document stays private — keep public API consistent with current test surface).
2. Remove `@ApplicationContext` / `Context` from `PersonalsRepositoryImpl`; use the string parameter in `Personal.serialize`.
3. `PersonalsViewModel.uploadPersonal(personal, customDeviceName)` forwards the string.
4. `PersonalsFragment` resolves `NetworkUtils.getCustomDeviceName(requireContext())` (or application context) at the call site when invoking the VM — UI may touch Android; repository must not.
5. Update `PersonalsRepositoryImplTest` constructor (drop context) and upload tests to pass a fixed device-name string.

**Acceptance:**

- Zero `android.content.Context` / Hilt `@ApplicationContext` in `PersonalsRepositoryImpl`.
- Upload still sends `customDeviceName` in JSON.
- Personals repository tests green; Fragment still triggers upload.

**Non-goals:** Do not rewrite attachment upload; do not edit `Personal.kt` / `NetworkUtils.kt` unless absolutely required (prefer not).

**North star 9/10:** 9 — personals repository drops Android Context.

**Size budget:** 5 files, ~60–100 LOC.

---

## Task 8 — Push enterprises report “non-archived” filter into TeamDao

**Roadmap:** 1 + 7.

**Problem:** `EnterprisesRepositoryImpl.getReportsFlow` uses `teamDao.observeByTeamIdAndDocType(teamId, "report")` then `.map { entities.filter { it.status != "archived" }.sortedByDescending { it.createdDate } }`. Filtering/sorting belong in SQL.

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`
2. `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`
3. `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt`

**Steps:**

1. Add e.g. `observeActiveReports(teamId: String): Flow<List<MyTeam>>` with  
   `WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`  
   (match existing team status null-handling style already used in `TeamDao`).
2. Point `getReportsFlow` at the new method; keep `distinctUntilChanged` on `_id`/`_rev` if still useful; drop in-memory filter/sort.
3. Update `EnterprisesRepositoryImplTest` mocks/verifications for the new DAO method.

**Acceptance:**

- `getReportsFlow` does not `filter { status != "archived" }` or `sortedByDescending` in Kotlin.
- Archived reports never emitted; order newest-first.
- Tests green.

**Non-goals:** Do not remove `MainApplication.context` from `attachTeamImage` in this task; no `EnterprisesRepository` interface change; no UI/ViewModel edits.

**North star 9/10:** 9 — query predicate in DAO.

**Size budget:** ~3 files, ~40–70 LOC.

---

## Task 9 — Move EditAchievementFragment data operations into AchievementViewModel

**Roadmap:** 3. Also **10**.

**Problem:** `AchievementViewModel` only re-shares `userRepository.achievementUpdates`. `EditAchievementFragment` still calls `userRepository.getUserModel()`, `initializeAchievement`, `updateAchievement`, `updateProfileFields`, and `resourcesRepository.getAllLibraries()` on the UI layer (via base-injected repos).

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`
2. `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`
3. `app/src/test/java/org/ole/planet/myplanet/ui/user/EditAchievementFragmentTest.kt`

**Steps:**

1. Expand `AchievementViewModel` to inject `UserRepository` + `ResourcesRepository` (interfaces only; **do not** edit locked `UserRepositoryImpl`).
2. Add suspend/state APIs used by the screen: load user, `initializeAchievement`, save achievement+profile payload, `getAllLibraries()` for resource picker. Keep CV file copy in the Fragment (needs `ContentResolver` / filesystem) — VM accepts already-computed `resumeFileName`.
3. Fragment uses `by viewModels()`; replace direct repository calls for those operations with VM; retain validation, date picker, and dialog UI in Fragment.
4. Update `EditAchievementFragmentTest` (30 lines today) for VM interaction or smoke compile; add focused VM tests **inside the same test file** if needed to stay ≤5 files (prefer small VM method tests here rather than a sixth file).

**Acceptance:**

- Edit/save/load achievement and library list fetch no longer call repositories from the Fragment.
- Save still updates achievement then profile fields in the same order.
- Tests green.

**Non-goals:** No `UserRepository` / Impl changes; no full form Compose rewrite; no CV storage redesign.

**North star 9/10:** 10 — achievement edit state/actions hoisted.

**Size budget:** 3 files, ~100–150 LOC.

---

## Task 10 — HomeCommunityDialogFragment: stop bypassing CommunityTabViewModel

**Roadmap:** 3. Also **10**.

**Problem:** `CommunityTabFragment` already loads community chrome via `CommunityTabViewModel` (`planetCode`, `parentCode`, `communityName`, `planetType`). `HomeCommunityDialogFragment` **re-injects** `SharedPrefManager` + `ConfigurationsRepository` and rebuilds the same values in `initCommunityTab()` (`communityName@parentCode`, subtitle `planetType`) — duplicate data access beside the ViewModel boundary.

**Files (only these):**

1. `app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt`

**Steps:**

1. Remove `@Inject sharedPrefManager` and `@Inject configurationsRepository`.
2. Obtain `CommunityTabViewModel` via `by viewModels()` (dialog-scoped is fine; same load logic as the tab).
3. In `initCommunityTab()`, collect `viewModel.state` (non-null) the same way `CommunityTabFragment` does: adapter id = `"${state.communityName}@${state.parentCode}"` to preserve **current** HomeCommunity behavior (note: tab fragment uses `planetCode@parentCode` — **do not** silently switch HomeCommunity to planetCode; keep communityName).
4. Set subtitle from `state.planetType`; keep bottom-sheet behavior code unchanged.

**Acceptance:**

- No repository/SharedPref injection left on `HomeCommunityDialogFragment`.
- Pager tabs and subtitle still populate when state arrives.
- No edits to `CommunityTabViewModel` / `CommunityTabFragment` required if existing state fields suffice.

**Non-goals:** Do not touch locked `CommunityServicesFragment` (#16192); no pager adapter changes; no new module bindings.

**North star 9/10:** 10 — community dialog reads hoisted VM state only.

**Size budget:** 1 file, ~30–50 LOC.

---

## Self-check (R1–R6)

| Rule | Status |
|------|--------|
| R1 Exactly 10 independent tasks | Yes |
| R2 No file in more than one task | Yes (verified counter; new VM/test paths unique) |
| R3 Open PRs listed; locked paths excluded | Yes (37 PRs; task paths rechecked against lock set) |
| R4 Cited paths/classes/functions exist | Yes (opened; Teams/Courses/User/Resources methods confirmed on interfaces) |
| R5 ≤~5 files, ≤~150 LOC, no new deps/TODOs/unused | Yes by construction |
| R6 No implementation code in this deliverable | Yes |

**Roadmap coverage this round:** 1 (tasks 4–8), 3 (1–3, 9–10), 4 (6), 7 (4, 5, 8); **9** on 4–8; **10** on 1–3, 9–10. Intentionally avoided locked monoliths (`TeamsRepositoryImpl`, `CoursesRepositoryImpl`, `UserRepositoryImpl`, `RepositoryModule`).

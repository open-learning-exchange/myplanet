# TASK-GENERATION BRIEF — myPlanet refactor round: WORK ORDERS

**Header / open-PR sweep (R3):** Checked. 31 open PRs in `open-learning-exchange/myplanet`. All file paths cited below were confirmed **not** present in any open PR's changed-file list. The following files are **off-limits** (touched by an open PR) and are excluded from every task: `ResourcesFragment.kt`, `TeamDetailFragment.kt`, `MainApplication.kt`, `TeamPagerAdapter.kt`, `PublicSurvey*.kt`, `CoursesRepository.kt/Impl.kt`, `TeamsRepository.kt/Impl.kt`, `SurveysRepository.kt/Impl.kt`, `AppDatabase.kt`, `TransactionSyncManager.kt`, `TeamTask.kt`, `TeamCalendarFragment.kt`, `ExamTakingFragment.kt`, `BaseDashboardFragment.kt`, `BaseRecyclerFragment.kt`, `DashboardViewModel.kt`, `SubmissionViewModel.kt`, all of `ui/voices/VoicesFragment.kt`, `BaseVoicesFragment.kt`, all `values*/strings.xml`, `app/build.gradle`, and everything under `.github/`.

**Focus served:** repository boundaries, cross-feature data leaks, moving data fns into repositories, DAO optimizations, smoother repository↔viewmodel relationships. All paths/classes/functions below were opened and confirmed to exist.

---

### Task 1 — Introduce `ResourceDetailViewModel` (data access out of fragment)
**Roadmap:** 3 (expand viewmodel layer) · also 10 (state hoisted, fragment becomes composable-ready).
**Files (≤3):**
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailFragment.kt`
- new `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModel.kt`
- new `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModelTest.kt`

**Work:** `ResourceDetailFragment` currently injects `ResourcesRepository`, `RatingsRepository`, `UserRepository` directly and calls `resourcesRepository.resolveLibraryItem`, `resourcesRepository.setUserLibrary`, `ratingsRepository.getRatingSummary`, `userRepository.getUserModel` from `viewLifecycleOwner.lifecycleScope.launch` blocks. Create `ResourceDetailViewModel` (Hilt, injects the same three repositories + `DispatcherProvider`), move all four repository calls into it, expose a `StateFlow` for the loaded library + rating summary and `suspend`/launching methods for the add/remove actions. Fragment observes state and delegates actions. Remove the three `@Inject` repository fields from the fragment. Write a unit test for the ViewModel using the existing `MainDispatcherRule`/`TestDispatcherProvider` pattern. Keep under ~150 changed lines; only the four call sites move.

---

### Task 2 — Introduce `BecomeMemberViewModel` (form data access out of activity)
**Roadmap:** 3 · also 10.
**Files (≤3):**
- `app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt`
- new `app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberViewModel.kt`
- new `app/src/test/java/org/ole/planet/myplanet/ui/user/BecomeMemberViewModelTest.kt`

**Work:** `BecomeMemberActivity` calls `userRepository.createMember(info)`, `userRepository.validateUsername(input)` (twice, incl. a debounced `usernameValidationJob`), and `userRepository.cleanupDuplicateUsers()` directly in `lifecycleScope.launch`. Create `BecomeMemberViewModel` injecting `UserRepository` + `DispatcherProvider`; move the three calls and the username-validation debounce job into it, exposing a `StateFlow` for validation error and a `SharedFlow`/state for create-member result. Activity collects state and calls `viewModel.*`. Remove direct `userRepository`/`dispatcherProvider` fields. Unit-test the ViewModel.

---

### Task 3 — Move remaining data calls in `AchievementFragment` into `AchievementViewModel`
**Roadmap:** 3 · also 10.
**Files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`

**Work:** `AchievementViewModel` exists but the fragment still calls `userRepository.getAchievementData(uId, pCode)` (line ~90), `userRepository.getUserModel()` (~106), and `resourcesRepository.downloadResources(listOf(lib))` (~205) directly in `lifecycleScope.launch`. `AchievementViewModel` already injects `userRepository` and `resourcesRepository`. Add three thin ViewModel methods (`loadAchievementData`, `loadUser`, `downloadResource`) wrapping those calls and emitting results on existing/new `StateFlow`s; change the fragment to call `viewModel.*` and remove its direct repository fields. No interface changes (methods already exist on the repositories).

---

### Task 4 — Split resource-upload serialization out of `ResourcesRepository` (tighten the 79-method interface)
**Roadmap:** 1 (finish cleaning data layer) + 4 (DI cleanup) · also 9 (upload logic toward platform-free core).
**Files (≤3):**
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`

**Work:** `ResourcesRepository.serializeForUpload(library, user): JsonObject` (ResourcesRepository.kt:139) is an upload-pipeline concern consumed only by `services/upload/UploadConfigs.kt:281`, yet it sits on the general resources interface. Move `serializeForUpload` off `ResourcesRepository`/`Impl` into the existing `UploadRepository` (or `UploadRepositoryImpl`) which already owns upload serialization. Update the single caller `UploadConfigs.kt:281` to call it there. This shrinks the largest repository interface toward single-responsibility. Add/adjust a test in `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt` if present, else extend the existing resources upload test.

---

### Task 5 — Consolidate duplicated `OfflineActivityDao` count queries (DAO optimization)
**Roadmap:** 1 (data-layer cleaning) + 7 (perf hotspot) · also 9.
**Files (≤3):**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/OfflineActivityDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/OfflineActivityDaoTest.kt` (create or extend if present)

**Work:** `OfflineActivityDao` has two near-identical COUNT queries — `countByUserIdAndType(userId,type)` (line 13) and `countByUserNameAndType(userName,type)` (line 16) — and two near-identical MAX queries `getGlobalLastVisit()` / `getLastVisit(userName)` (lines 25–29). Consolidate the COUNT pair into one query keyed by a single optional user identifier, and the MAX pair into one query with a nullable `userName` (`WHERE (:userName IS NULL OR userName = :userName)`). Update `ActivitiesRepositoryImpl` (calls at lines 66, 144, 149–150) to the consolidated methods. Remove the superseded DAO methods. Add a DAO test asserting both consolidated queries return correct values. **Do not** bump `AppDatabase` version — query-only change, no schema impact.

---

### Task 6 — Add typed projection to a hot `MeetupDao` read used by `CommunityRepositoryImpl` (DAO optimization)
**Roadmap:** 1 + 5 (consolidate) · also 9.
**Files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MeetupDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt`

**Work:** `CommunityRepositoryImpl` injects both `CommunityDao` and `MeetupDao`. Audit `MeetupDao` for any query reachable through a single Community call whose full `SELECT *` rows are immediately reduced to ids/titles by the repository. Following the existing `ResourceTitleProjection`/`LibraryTitleProjection` pattern (`MyLibraryDao.kt:178-186`), add a typed projection for the hottest meetup read in the community screen and switch the repository to it, reducing row materialization. Confirm the `CommunityServicesViewModel`/`LeadersViewModel` consumer chain is unaffected. Query-only change — do not change schema or bump DB version.

---

### Task 7 — Add a paged top-level query to `NewsDao` for the voices feed (DAO optimization)
**Roadmap:** 7 (perf hotspot) + 1 · also 9.
**Files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`

**Work:** `NewsDao` (96 lines) serves the voices/community feed with `ORDER BY time DESC` full-row queries (lines 33–45). The feed re-reads the whole top-level set on each realtime `TableDataUpdate`. Add a bounded variant `getTopLevelPaged(limit: Int, offset: Int)` (top-level rows, `replyTo IS NULL OR replyTo = ''`) and wire `VoicesRepositoryImpl` (injects `NewsDao` + `NewsLogDao`, lines 14/31) to expose a paged read used by the initial feed load, keeping the existing full query for count/refresh. Query-only change — no schema bump, no `AppDatabase.kt` edit (off-limits). Add a DAO test for the paged ordering.

---

### Task 8 — Push dictionary search/count composition fully behind `DictionaryRepository`
**Roadmap:** 1 + 3 · also 9.
**Files (≤3):**
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`

**Work:** `DictionaryViewModel` calls `loadDictionary()`, `loadCount()`, `searchWord(query)` and renders `loadState`/`searchState`. Inspect `DictionaryRepository`/`Impl` (which uses `DictionaryFileReader`/`DictionaryMapper` + `DictionaryDao`) for any search/count logic still living in the ViewModel that composes multiple repository calls or filters/sorts raw results. Push that composition down into `DictionaryRepositoryImpl` so the interface exposes a single `search(query)` / `count()` and the ViewModel becomes a thin collector. Keeps the repository as the single source of dictionary truth. Add a repository test; keep the ViewModel test green.

---

### Task 9 — Replace `CourseProgressDao`'s hand-built dynamic query with a parameterized one
**Roadmap:** 1 + 7 · also 9.
**Files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt`
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt` (create or extend if present)

**Work:** `CourseProgressDao` (63 lines) builds SQL via string concatenation at line 37 (`val sql = "SELECT * FROM course_progress WHERE $clauses"`) for a variable set of course ids, alongside fixed queries (`getByCourseId`, `getByUserId`, `getByIds`). Replace the hand-built dynamic `@RawQuery` path with a single parameterized `@Query("SELECT * FROM course_progress WHERE userId IS :userId AND courseId IN (:courseIds)")`-style query (partially present at line 13) and route the repository through it, deleting the string-interpolated builder. This removes SQL-injection-adjacent string construction and redundant DAO surface. Verify the repository caller compiles against the consolidated method. Query-only; no schema bump.

---

### Task 10 — Give `ReplyActivity` a dedicated `ReplyViewModel` (close the last repo-injecting activity)
**Roadmap:** 3 + 4 · also 10.
**Files (≤3):**
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt`
- new `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyViewModel.kt`
- new `app/src/test/java/org/ole/planet/myplanet/ui/voices/ReplyViewModelTest.kt`

**Work:** `ReplyActivity` is one of the few remaining UI classes injecting repositories directly — `VoicesRepository` (line 66) and `ActivitiesRepository` (line 59) — passing `voicesEditActions = voicesRepository` (line 151) and calling `VoicesActions.showMemberDetails(userModel, activitiesRepository)` (line 195). Create `ReplyViewModel` injecting both repositories; expose the edit-actions slice (`voicesEditActions`) from the ViewModel and a `showMemberDetails(userModel)` suspend fun delegating to `VoicesActions` + `activitiesRepository`. Activity swaps its two `@Inject` repository fields for `by viewModels()`. Note: do **not** touch `VoicesFragment.kt` or `BaseVoicesFragment.kt` (off-limits, open PRs #13415/#10993). Unit-test the ViewModel with MockK.

---

## Self-check (P5)
- **R1** 10 tasks, each mergeable alone. ✔
- **R2** No file repeated across tasks — disjoint file sets (only test files are new, all distinct). ✔
- **R3** Open PRs listed in header; all cited files confirmed absent from every open PR's diff. ✔
- **R4** Every path/class/function opened and confirmed. ✔
- **R5** Each task ≤3 files, ≤~150 lines, no new dependencies, no TODO/unused code. ✔
- **R6** No implementation code written — plan only. ✔

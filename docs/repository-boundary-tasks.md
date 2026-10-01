# myPlanet refactor round — repository boundaries

Ten independent, mergeable work orders for other coding agents. Any merge order is valid. No file appears in more than one task. Schema bumps are forbidden (`AppDatabase.kt` is in open PRs).

**Roadmap served:** 1 data layer, 3 ViewModel/use-case, 7 performance, plus north-star 9 (KMP: zero `android.*` in core) and 10 (CMP: state in ViewModels).

**This round's focus:** repository boundaries, cross-feature leaks, UI/service data moved into repos/VMs, schema-free DAO work, smoother VM–repo wiring.

Open PRs at generation time (files those PRs uniquely touch are off-limits):

| # | Title |
|---|--------|
| 17812 | actions: bump gradle/actions from 6.3.0 to 6.4.0 |
| 17811 | mySurvey: survey number update |
| 17776 | Refactor showMemberDetails to load visit stats in MembersDetailViewModel |
| 17694 | Shared kmp module scaffold |
| 17680 | Fix TeamResourcesAdapterTest failure and Glide preview clearing |
| 17435 | resources: added filter labels indications (fixes #16922) |
| 17254 | teams: refactored resources to match library (fixes #16927) |
| 17187 | resources: refactored filter logic (fixes #16282) |
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

Hard caps per task: under ~150 changed lines, under ~5 files, no new dependencies, no unused code, no TODO placeholders.

---

## Task 1 — Hoist chat server prefs and AI-provider fetch into ChatViewModel

**Roadmap:** 3 (also 9: fragments stop calling `MainApplication` reachability/prefs; VM already owns chat state for 10)

### Problem

`ChatDetailFragment` injects `SharedPrefManager`, `ServerUrlMapper`, and `DispatcherProvider`, reads `serverUrl` / `ai_models` / alternative-URL flags, and calls `MainApplication.isServerReachable` / `isPrimaryServerReachable`. `ChatHistoryFragment` injects `SharedPrefManager` for `getServerUrl()` and `getUserId()`. Data access belongs in `ChatViewModel`.

### Verified symbols

- `ChatDetailFragment.checkAiProviders` → `sharedViewModel.fetchAiProviders(serverUrl, getCachedProviderAvailability())`
- `ChatDetailFragment.processServerUrl`, `updateServerIfNecessary`, `getModelsMap`, `getCachedProviderAvailability`, `clearAlternativeUrlIfPrimaryRestored`
- `ChatHistoryFragment.refreshChatHistory` → `sharedViewModel.loadChatHistoryScreenData(sharedPrefManager.getUserId())`
- `ChatHistoryFragment.checkAiProvidersIfNeeded` → `sharedViewModel.fetchAiProviders(serverUrl)`
- `ChatViewModel.fetchAiProviders(serverUrl: String, cachedProviders: Map<String, Boolean>?)`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragmentTest.kt`

### Required work

Inject `SharedPrefManager` (and `ServerUrlMapper` only if still required after the move) into `ChatViewModel`. Add `ensureAiProviders()` that reads `getServerUrl()` / cached `ai_models` and calls existing `fetchAiProviders`. Add a no-arg history reload that resolves the user id via `UserRepository` (already injected) instead of prefs. Move `processServerUrl` / `updateServerIfNecessary` / `clearAlternativeUrlIfPrimaryRestored` onto the VM. Fragments drop those `@Inject` fields and call the new VM methods.

### Do not

Change `ChatRepository` / `ChatRepositoryImpl`. Do not add unused overloads of `fetchAiProviders`. Do not touch `ChatDetailFragmentInjectionTest.kt` (it only asserts no `ChatApiService` field).

### Acceptance

Neither fragment injects `SharedPrefManager` or `ServerUrlMapper`. AI-provider fetch and chat-history reload still work with the same `ChatViewModel` state.

### Tests

Update `ChatViewModelTest` constructor and the `fetchAiProviders(serverUrl)` case to cover `ensureAiProviders()`. Update `ChatDetailFragmentTest` where it assigns `fragment.sharedPrefManager`.

---

## Task 2 — Delete CourseDetailProvider / RatingSummaryProvider; ViewModel talks to repositories

**Roadmap:** 1, 3 (also 10: course detail state already lives on the VM)

### Problem

`CourseDetailViewModel` depends on one-line wrappers instead of repositories. That hides the real boundary and extra types with no behavior.

### Verified symbols

- `CourseDetailProvider.invoke` → `coursesRepository.getCourseDetailModel(courseId)`
- `RatingSummaryProvider.invoke` → `ratingsRepository.getRatingSummary("course", courseId, userId)`
- `CourseDetailViewModel.loadCourseDetail`, `refreshRatings`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailProvider.kt` (delete)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/RatingSummaryProvider.kt` (delete)
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModelTest.kt`

### Required work

Inject `CoursesRepository` and `RatingsRepository` into `CourseDetailViewModel`. Call `getCourseDetailModel` and `getRatingSummary("course", …)` directly. Delete both provider classes. Keep `@ApplicationContext` markdown path behavior unchanged.

### Do not

Change `CoursesRepository` / `RatingsRepository` interfaces or impls. Do not add new wrapper types.

### Acceptance

No remaining references to `CourseDetailProvider` or `RatingSummaryProvider`. `loadCourseDetail` / `refreshRatings` behavior unchanged.

### Tests

`CourseDetailViewModelTest` already mocks `CoursesRepository` and `RatingsRepository`; construct the VM with those mocks instead of the providers.

---

## Task 3 — TakeCourseViewModel logs visits through ActivitiesRepository

**Roadmap:** 1, 3

### Problem

`TakeCourseViewModel.logCourseVisit` calls `CoursesRepository.logCourseVisit`, which only forwards to `ActivitiesRepository.logCourseVisit`. The VM already injects `ProgressRepository` for progress; visit logging is an activities concern leaking through courses.

### Verified symbols

- `TakeCourseViewModel.logCourseVisit(courseId, courseTitle, userName)` → `coursesRepository.logCourseVisit(...)`
- `ActivitiesRepository.logCourseVisit(courseId: String, title: String, userId: String)`
- `TakeCourseFragment` calls `viewModel.logCourseVisit` (do not edit that fragment)
- `TakeCourseViewModelTest` constructs `TakeCourseViewModel(coursesRepository, progressRepository, userRepository, ratingsRepository)`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`

### Required work

Inject `ActivitiesRepository`. Route `logCourseVisit` to `activitiesRepository.logCourseVisit` with the same three arguments. Leave `getCurrentProgress` / `isStepCompleted` / `hasUnfinishedSurveys` on `CoursesRepository` (`ProgressRepository` has `getCurrentProgress` but not `isStepCompleted`).

### Do not

Edit `CoursesRepository`, `CoursesRepositoryImpl`, `ActivitiesRepository`, or `TakeCourseFragment`. Do not remove `CoursesRepository.logCourseVisit` (other callers). Do not add unused VM methods.

### Acceptance

`logCourseVisit` no longer touches `CoursesRepository`. Public VM signature stays the same.

### Tests

Add an `ActivitiesRepository` mock to the test constructor. If you add a `logCourseVisit` unit test, verify the activities mock is called once.

---

## Task 4 — HealthViewModel loads patients from UserRepository

**Roadmap:** 1, 3 (also 9: user rows stop traveling through the health repository at the VM boundary)

### Problem

`HealthRepository.getPatientById` / `getPatientsSortedBy` / `searchPatients` return `UserEntity` and, in `HealthRepositoryImpl`, only delegate to `UserRepository`. `HealthViewModel` already injects `UserRepository` (`loadHealthData` uses `getUserById`). Patient identity is leaking across the health boundary.

### Verified symbols

- `HealthViewModel.loadPatients` → `healthRepository.getPatientsSortedBy`
- `HealthViewModel.searchPatients` → `healthRepository.searchPatients`
- `HealthViewModel.fetchPatientData` → `healthRepository.getPatientById` then `getPatientHealthRecords(userId, user)`
- `UserRepository.getUserById`, `getUsersSortedBy`, `searchUsers`
- `HealthRepositoryImpl.getPatientById` / `getPatientsSortedBy` / `searchPatients` (blank query → `getUsersSortedBy`)

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt`

### Required work

`loadPatients` → `userRepository.getUsersSortedBy`. `searchPatients` → blank query uses `getUsersSortedBy`, otherwise `searchUsers` (same as the impl). `fetchPatientData` → `userRepository.getUserById` then existing `healthRepository.getPatientHealthRecords`. Keep health-record / profile / save paths on `HealthRepository`.

### Do not

Edit `HealthRepository.kt` or `HealthRepositoryImpl.kt` (open PR #15808). Do not add unused `UserRepository` methods.

### Acceptance

VM no longer calls `getPatientById`, `getPatientsSortedBy`, or `searchPatients`. Patient list/detail behavior unchanged.

### Tests

Retarget `HealthViewModelTest` stubs/verifies from those three health methods to the matching `UserRepository` methods. Keep `getPatientHealthRecords` on the health mock.

---

## Task 5 — HealthExaminationViewModel stops using getHealthEntry Pair

**Roadmap:** 1, 3

### Problem

`loadData` unpacks `healthRepository.getHealthEntry(userId)` (`Pair<UserEntity?, HealthExamination?>`). The impl is `userRepository.getUserById` + `healthExaminationDao.getByIdOrUserId`. The VM already has `UserRepository` and `HealthRepository.getByIdOrUserId`.

### Verified symbols

- `HealthExaminationViewModel.loadData` uses `getHealthEntry`, then `userRepository.ensureUserSecurityKeys`, `getDecryptedHealth`, `initHealth`, `getExaminationById`, `getExaminationConditions`
- `HealthRepository.getByIdOrUserId(id: String): HealthExamination?`
- `UserRepository.getUserById`, `ensureUserSecurityKeys`
- `HealthExaminationViewModelTest.loadData_success_updatesState` stubs `getHealthEntry`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt`

### Required work

Replace `getHealthEntry` with `userRepository.getUserById(userId)` and `healthRepository.getByIdOrUserId(userId)`. Keep `ensureUserSecurityKeys` overwrite and the rest of `loadData` / `saveExamination` as they are.

### Do not

Edit `HealthRepository` / Impl. Do not leave `getHealthEntry` calls in this VM.

### Acceptance

Same `HealthExaminationState` fields. No `Pair` unpack from health.

### Tests

Update every `getHealthEntry` stub in `HealthExaminationViewModelTest` to `getUserById` + `getByIdOrUserId`.

---

## Task 6 — Chunk DictionaryDao.insertAll; drop android.util.Log from DictionaryRepositoryImpl

**Roadmap:** 7, 1 (also 9: `DictionaryRepositoryImpl` currently imports `android.util.Log`)

### Problem

`DictionaryDao.insertAll` is a single `@Insert` of the full seed list (SQLite bind limit ~999). `DictionaryRepositoryImpl.insertDictionaryData` inserts the mapped array in one shot and logs failures with `android.util.Log`.

### Verified symbols

- `DictionaryDao.insertAll(items: List<DictionaryEntity>)`
- `DictionaryRepositoryImpl.insertDictionaryData` → `dictionaryDao.insertAll(entities)` and `Log.e("DictionaryRepositoryImpl", ...)`
- `DictionaryDaoTest.count_returnsZeroOnEmptyTableAndThreeAfterInsertAllOfThreeEntries`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/DictionaryDao.kt`
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/DictionaryDaoTest.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`

### Required work

Same pattern as `AnswerDao.getBySubmissionIds`: keep `@Insert insertAllInternal`, add a default `insertAll` that no-ops on empty and `chunked(900)`. Remove `android.util.Log` from `DictionaryRepositoryImpl`; keep `DictionaryLoad.Failed(e)` / `CancellationException` rethrow. No schema / index / `AppDatabase` changes.

### Do not

Change `DictionaryRepository` interface or `DictionaryViewModel`. Do not add unused queries (`LIKE`, Flow). Do not bump Room version.

### Acceptance

Existing three-row insert test still passes. Empty list does not hit Room. Impl has zero `android.*` imports.

### Tests

Keep `DictionaryDaoTest` insert/count. Optionally assert `insertAll(emptyList())` does not throw. Do not add a 900-row fixture.

---

## Task 7 — LifeViewModel: stop wrapping suspend repository calls in withContext(IO)

**Roadmap:** 3

### Problem

`LifeViewModel` injects `DispatcherProvider` only to `withContext(dispatcherProvider.io)` around `LifeRepository` / `UserRepository` suspend functions. Room DAOs already offload; the extra hop is noise and forces a dispatcher in tests.

### Verified symbols

- `loadMyLifeList`, `updateVisibility`, `updateMyLifeListOrder` all use `withContext(dispatcherProvider.io)`
- `LifeRepository.getMyLifeByUserId`, `updateVisibility`, `updateMyLifeListOrder` are suspend
- `LifeViewModelTest` constructs `LifeViewModel(lifeRepository, userRepository, testDispatcherProvider)`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`

### Required work

Call the suspend repo methods directly from `viewModelScope.launch`. Remove `DispatcherProvider` if it becomes unused. Keep `resolveUserId()` and `LifeItemDefaults.forUser`.

### Do not

Edit `LifeRepository` / Impl / `MyLifeDao`. Do not leave an unused `dispatcherProvider` field.

### Acceptance

List load / visibility / reorder still update `_myLifeList`. Constructor matches remaining dependencies.

### Tests

Drop `TestDispatcherProvider` from `LifeViewModelTest` if the VM no longer takes it. Existing assertions stay.

---

## Task 8 — CommunityServicesViewModel: same redundant IO hop

**Roadmap:** 3

### Problem

Community services UI talks to `TeamsRepository` (team links are team docs). Extra `launch(dispatcherProvider.io)` / `withContext(dispatcherProvider.io)` around suspend repo calls.

### Verified symbols

- `loadTeamLinks` → `viewModelScope.launch(dispatcherProvider.io) { teamsRepository.getTeamLinks() }`
- `isMember` → `withContext(dispatcherProvider.io) { teamsRepository.isMember(userId, teamId) }`
- `CommunityServicesViewModelTest` constructs `CommunityServicesViewModel(teamsRepository, dispatcherProvider)`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModelTest.kt`

### Required work

`viewModelScope.launch { _teamLinks.value = teamsRepository.getTeamLinks() }`. `isMember` becomes a plain suspend call. Remove `DispatcherProvider` if unused.

### Do not

Edit `TeamsRepository` / Impl (open PRs). Do not add a CommunityRepository method. Do not leave unused injections.

### Acceptance

`teamLinks` still fills on init. `isMember` still returns the repo boolean.

### Tests

Update constructor in `CommunityServicesViewModelTest`. Keep the init/`isMember` cases.

---

## Task 9 — Strip android.util.Log from RetryRepositoryImpl

**Roadmap:** 1 (also 9: retry core must not import `android.*`)

### Problem

`RetryRepositoryImpl` is otherwise platform-free (DAO + `ApiInterface` + `TimeProvider`) but imports `android.util.Log` for payload/HTTP/network traces. `RetryRepositoryImplTest` also imports `android.util.Log`.

### Verified symbols

- `RetryRepositoryImpl.executeOperation` — `Log.e` invalid payload; `Log.d` success / 409; `Log.w` HTTP and `IOException`; `Log.e` unexpected
- Control flow already returns `TerminalFailure` / `RetryableFailure` / `Success` after those logs

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`

### Required work

Delete every `Log.*` call and the `android.util.Log` import. Keep `TAG` only if still used; otherwise remove it. Do not change retry/DAO/API behavior. Clean the test import if it becomes unused.

### Do not

Edit `RetryDao`, `RetryQueue`, `RetryQueueWorker` (`RetryQueueTest` is in #17187). Do not add a logging facade. Do not add unused Flow queries.

### Acceptance

`RetryRepositoryImpl` has zero `android.*` imports. Existing retry tests still pass.

### Tests

Re-run `RetryRepositoryImplTest`. Only change the test file if the Log import or shadow config requires it.

---

## Task 10 — LeadersViewModel: do not hop to Default for a repository read

**Roadmap:** 3

### Problem

`LeadersViewModel.loadLeaders` is `viewModelScope.launch(dispatcherProvider.default) { configurationsRepository.getCommunityLeaders() }`. `DispatcherProvider` exists only for that hop. (Moving `getCommunityLeaders` off `ConfigurationsRepository` would require `UserRepositoryImpl`, which is in the incremental-sync blast radius — out of scope.)

### Verified symbols

- `LeadersViewModel.loadLeaders` → `configurationsRepository.getCommunityLeaders()`
- `LeadersViewModelTest` builds an anonymous `DispatcherProvider` and `LeadersViewModel(configurationsRepository, dispatcherProvider)`

### Files (exclusive)

- `app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/community/LeadersViewModelTest.kt`

### Required work

`viewModelScope.launch { _leaders.value = configurationsRepository.getCommunityLeaders() }`. Remove `DispatcherProvider` if unused. Keep `ConfigurationsRepository` as the source.

### Do not

Edit `ConfigurationsRepository` / Impl or change `getCommunityLeaders()`’s `List<UserEntity>` return type. Do not add unused UserRepository methods.

### Acceptance

`leaders` still emits the repo list on init. VM constructor has no unused dispatcher.

### Tests

Construct `LeadersViewModel(configurationsRepository)` (or remaining deps only). Keep the Alice/`leader_1` assertion.

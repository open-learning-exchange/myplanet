### ChatViewModel State Management Tests

`ChatViewModel` has zero test coverage despite containing non-trivial guard logic in `shouldFetchAiProviders()` and multi-field reset logic in `clearChatState()`. Both are pure StateFlow mutations with no Android dependencies, making them ideal low-hanging-fruit unit tests that require only a plain `ViewModel()` constructor and MockK.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L59-L69"}

:::task-stub{title="Add ChatViewModelTest covering state mutations and guards"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` following the existing `app/src/test/java/org/ole/planet/myplanet/ui/teams/TeamViewModelTest.kt` pattern (no Hilt, plain constructor).
2. Add a test verifying `shouldFetchAiProviders()` returns `true` when `_aiProviders` is `null` and `_aiProvidersLoading` is `false`.
3. Add a test verifying `shouldFetchAiProviders()` returns `false` when `_aiProvidersLoading` is set to `true` via `setAiProvidersLoading(true)`.
4. Add a test verifying `shouldFetchAiProviders()` returns `false` after `setAiProviders(mapOf("openai" to true))`.
5. Add a test verifying `clearChatState()` resets `selectedChatHistory`, `selectedId`, `selectedRev`, and `selectedAiProvider` to their initial values.
:::

---

### CoursesViewModel processCourses Sorting Tests

`CoursesViewModel.processCourses()` contains sorting and `isMyCourse` marking logic for two separate modes (`isMyCourseLib` vs library view), but has no tests. The method is a pure function of its inputs with no Android or Realm dependencies, making it very easy to test without mocks.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L28-L58"}

:::task-stub{title="Add CoursesViewModelTest covering processCourses sort order and isMyCourse marking"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesViewModelTest.kt` with a plain `CoursesViewModel()` (no injected dependencies needed).
2. Add a test for `isMyCourseLib = true`: verify the emitted `courses` list is sorted alphabetically by `courseTitle` and every item has `isMyCourse = true`.
3. Add a test for `isMyCourseLib = false`: verify courses belonging to the given `userId` appear after courses that do not (`isMyCourse` ascending), and within each group entries are alphabetical.
4. Add a test verifying that when `validCourses` is empty and `isMyCourseLib = false` the resulting `coursesState` has an empty `courses` list.
5. Use `MainDispatcherRule` from the existing test helpers to satisfy `viewModelScope`.
:::

---

### SurveysViewModel Filter, Sort, and Normalize Tests

`SurveysViewModel` contains three independently testable pure functions — `filter()`, `normalizeText()`, and `applyFilterAndSort()` — plus the `toggleTitleSort()` state machine. None of these are currently covered by tests, yet they depend only on `List<RealmStepExam>` and locale logic.

:codex-file-citation[codex-file-citation]{line_range_start=105 line_range_end=165 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L105-L165"}

:::task-stub{title="Add SurveysViewModelTest covering filter, sort, toggleTitleSort, and normalizeText"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt`; mock `SurveysRepository`, `SyncManager`, `UserSessionManager`, `SharedPrefManager`, and `ServerUrlMapper` with `mockk(relaxed = true)`.
2. Add a test that calls `search("math")` after seeding `rawSurveys` via `loadSurveys` and verifies that only surveys whose names contain "math" appear in `surveys.value`.
3. Add a test verifying that `sort(SortOption.TITLE_ASC)` orders surveys alphabetically and `sort(SortOption.TITLE_DESC)` reverses that order.
4. Add a test verifying `toggleTitleSort()` alternates between `TITLE_ASC` and `TITLE_DESC` on successive calls.
5. Add a test verifying that diacritic characters are matched case-insensitively (e.g., searching "cafe" matches a survey named "Café").
:::

---

### UserProfileViewModel ProfileUpdateState Transition Tests

`UserProfileViewModel` has a sealed `ProfileUpdateState` with `Idle`, `Success`, and `Error` branches driven by `updateUserProfile()`. None of these branches are tested, and the null-userId guard path is also untested. Tests require only `UserRepository` and `UserSessionManager` mocks.

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt#L15-L107"}

:::task-stub{title="Add UserProfileViewModelTest covering ProfileUpdateState transitions"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/user/UserProfileViewModelTest.kt` using `MainDispatcherRule` and MockK mocks for `UserRepository` and `UserSessionManager`.
2. Add a test verifying that calling `updateUserProfile(null, ...)` immediately sets `updateState` to `ProfileUpdateState.Error` without invoking `userRepository`.
3. Add a test verifying that a successful `userRepository.updateUserDetails(...)` result sets `updateState` to `ProfileUpdateState.Success` and updates `userModel`.
4. Add a test verifying that a thrown exception from `userRepository.updateUserDetails(...)` sets `updateState` to `ProfileUpdateState.Error` carrying the exception message.
5. Add a test verifying `loadUserProfile(userId)` sets `userModel.value` to the object returned by `userRepository.getUserByAnyId(userId)`.
:::

---

### AddExaminationViewModel isSaving Guard and SharedFlow Tests

`AddExaminationViewModel.saveExamination()` uses an `isSaving` StateFlow guard to prevent concurrent saves and emits `true`/`false` on a `SharedFlow`. Neither the guard nor the emission paths are covered by any test. This ViewModel has no Android dependencies beyond `HealthRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationViewModel.kt#L29-L43"}

:::task-stub{title="Add AddExaminationViewModelTest covering isSaving guard and saveResult SharedFlow emissions"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/health/AddExaminationViewModelTest.kt` using `MainDispatcherRule` and a MockK mock for `HealthRepository`.
2. Add a test verifying that when `healthRepository.saveExamination(...)` succeeds, `saveResult` emits `true` and `isSaving` returns to `false` afterwards.
3. Add a test verifying that when `healthRepository.saveExamination(...)` throws, `saveResult` emits `false` and `isSaving` returns to `false` afterwards.
4. Add a test verifying that calling `saveExamination()` while `isSaving` is `true` is a no-op (the repository is not called a second time).
5. Collect `saveResult` using `UnconfinedTestDispatcher` and `toList()` following the pattern already established in `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailViewModelTest.kt`.
:::

---

### FeedbackListViewModel collectLatest Observer Tests

`FeedbackListViewModel` opens a long-lived `collectLatest` observer inside `viewModelScope.launch` from its `init` block. The `refreshFeedback()` method starts a second coroutine but does not cancel the first, which can lead to duplicate emissions. Neither the initial population nor the refresh path has a test.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt#L22-L40"}

:::task-stub{title="Add FeedbackListViewModelTest covering init collection and refreshFeedback"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModelTest.kt` using `MainDispatcherRule`, MockK for `FeedbackRepository` and `UserSessionManager`.
2. Add a test verifying that after construction `feedbackList.value` contains the items emitted by `feedbackRepository.getFeedback(user)`.
3. Add a test verifying that a second emission from the repository flow updates `feedbackList.value` to the new list.
4. Add a test verifying that `refreshFeedback()` causes `feedbackList` to reflect the latest list from the repository.
5. Use `flowOf(listOf(...))` as the stub return so `collectLatest` resolves deterministically within `advanceUntilIdle()`.
:::

---

### DiffUtils calculateDiff Tests

`DiffUtilsTest` already covers `itemCallback` thoroughly but does not test the `calculateDiff` function at all. `calculateDiff` wraps a `DiffUtil.Callback` whose size, identity, and content methods all delegate to the provided lambdas; these paths need their own test cases.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L20-L41"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=63 path=app/src/test/java/org/ole/planet/myplanet/utils/DiffUtilsTest.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/test/java/org/ole/planet/myplanet/utils/DiffUtilsTest.kt#L1-L63"}

:::task-stub{title="Extend DiffUtilsTest with calculateDiff coverage"}
1. Add a test in the existing `DiffUtilsTest` that calls `DiffUtils.calculateDiff(oldList, newList, ...)` with two identical lists and verifies no change callbacks are triggered by inspecting the resulting `DiffResult` update count is zero (use a simple `RecyclerView.Adapter` stub or `ListUpdateCallback`).
2. Add a test verifying that inserting one element into `newList` produces a `DiffResult` that dispatches exactly one insertion.
3. Add a test verifying that providing a non-null `getChangePayload` lambda causes it to be invoked when `areItemsTheSame` returns `true` but `areContentsTheSame` returns `false`.
4. Add a test verifying that swapping two elements produces a move dispatch (not two independent insert/remove pairs).
:::

---

### SubmissionDetailViewModel Derived StateFlow Default Value Tests

`SubmissionDetailViewModel` chains five `stateIn(viewModelScope, SharingStarted.Lazily, default)` operators onto a single upstream `StateFlow`. The default values are hard-coded strings ("Submission Details", "Status: Unknown", etc.) that are essential for the UI before data loads, but none are currently tested.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt#L17-L54"}

:::task-stub{title="Add SubmissionDetailViewModelTest covering derived StateFlow defaults and populated values"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt` using `MainDispatcherRule`, a MockK mock for `SubmissionsRepository`, and a `SavedStateHandle(mapOf("id" to "sub-1"))`.
2. Add a test verifying that before `advanceUntilIdle()`, `title.value` equals `"Submission Details"`, `status.value` equals `"Status: Unknown"`, `date.value` equals `"Date: Unknown"`, and `submittedBy.value` equals `"Submitted by: Unknown"`.
3. Add a test verifying that after `advanceUntilIdle()` with a stubbed `SubmissionsRepository.getSubmissionDetail("sub-1")` returning a `SubmissionDetail`, `title.value`, `status.value`, `date.value`, and `submittedBy.value` reflect the stub's data.
4. Add a test verifying `questionAnswers.value` contains the same `QuestionAnswer` list returned by the repository stub.
:::

---

### EnterprisesViewModel TeamsRepository Delegation Tests

`EnterprisesViewModel` is a thin facade over `TeamsRepository` with five suspension-point methods (`addReport`, `updateReport`, `archiveReport`, `getReportsFlow`, `exportReportsAsCsv`) and has zero test coverage. Because the ViewModel purely delegates, the tests verify that each call reaches the repository with the correct arguments.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt#L1-L35"}

:::task-stub{title="Add EnterprisesViewModelTest covering TeamsRepository delegation"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModelTest.kt` using `MainDispatcherRule` and a MockK mock for `TeamsRepository`.
2. Add a test verifying that `viewModel.addReport(doc)` invokes `teamsRepository.addReport(doc)` exactly once with the same `JsonObject`.
3. Add a test verifying that `viewModel.updateReport(reportId, doc)` invokes `teamsRepository.updateReport(reportId, doc)` exactly once.
4. Add a test verifying that `viewModel.archiveReport(reportId)` invokes `teamsRepository.archiveReport(reportId)` exactly once.
5. Add a test verifying that `viewModel.getReportsFlow(teamId)` returns the `Flow` provided by `teamsRepository.getReportsFlow(teamId)`.
:::

---

### NewsViewModel Callback Invocation Tests

`NewsViewModel.getPrivateImageUrlsCreatedAfter()` bridges a suspend repository call to a classic callback via `viewModelScope.launch`. The callback contract — that it is always called with the repository result and never with a stale or empty list — is untested despite being relied upon by `VoicesFragment` for image rendering decisions.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=19 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/21e560b07a46665e9dbc240d792a4af4253e6cab/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt#L14-L19"}

:::task-stub{title="Add NewsViewModelTest covering getPrivateImageUrlsCreatedAfter callback contract"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/voices/NewsViewModelTest.kt` using `MainDispatcherRule` and a MockK mock for `ResourcesRepository`.
2. Add a test that stubs `resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)` to return a known list, calls `viewModel.getPrivateImageUrlsCreatedAfter(timestamp, callback)`, advances the dispatcher with `advanceUntilIdle()`, and asserts the callback received that exact list.
3. Add a test verifying that when the repository returns an empty list the callback is still invoked (not skipped) and receives an empty list.
4. Use a `var capturedResult: List<String>? = null` capture variable for the callback to avoid complex MockK lambda matchers.
:::

### Add JVM test source set and core test dependencies

The project has zero test infrastructure: no `src/test` directories, no `testImplementation` entries in `app/build.gradle`, and no `testOptions` block. Every subsequent testing task depends on this scaffold being in place first.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=209 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L160-L209"}

:::task-stub{title="Add JVM test source set and core test dependencies"}
1. Add `testImplementation` entries to `app/build.gradle` for `junit:junit:4.13.2`, `io.mockk:mockk:1.13.x`, `org.jetbrains.kotlinx:kotlinx-coroutines-test`, and `androidx.arch.core:core-testing`.
2. Add a `testOptions { unitTests { returnDefaultValues = true } }` block inside the `android { }` closure so Android stubs do not throw during JVM tests.
3. Create the directory `app/src/test/java/org/ole/planet/myplanet/` to hold all new unit-test files.
:::

### Add TestDispatcherProvider to the test source set

`DispatcherProvider` is already injected into `TeamViewModel`, `RequestsViewModel`, and others, but there is no test double that replaces every dispatcher with a controllable scheduler. Without it, coroutine timing in ViewModels cannot be deterministically advanced during tests.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DispatcherProvider.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/DispatcherProvider.kt#L6-L18"}

:::task-stub{title="Create TestDispatcherProvider in the test source set"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/TestDispatcherProvider.kt`.
2. Implement `DispatcherProvider` with all four dispatcher properties (`main`, `io`, `default`, `unconfined`) backed by `kotlinx.coroutines.test.StandardTestDispatcher` so tests control time advancement explicitly via `advanceUntilIdle()`.
3. Expose a no-arg constructor so tests can instantiate it directly without Hilt.
4. In the same file, add a `MainDispatcherRule` JUnit `@Rule` class that calls `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` and `Dispatchers.resetMain()` in `@After`; subsequent ViewModel test classes use this rule instead of repeating the setup inline.
:::

### Unit-test DiffUtils.itemCallback contract

`DiffUtils.itemCallback` is a pure-Kotlin factory with no Android-framework dependencies. It wraps the three `ItemCallback` contracts (`areItemsTheSame`, `areContentsTheSame`, `getChangePayload`) and is shared by every `ListAdapter` in the project; a regression here silently breaks all list diffing.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L5-L18"}

:::task-stub{title="Unit-test DiffUtils.itemCallback contract"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/DiffUtilsTest.kt`.
2. Add a test that calls `itemCallback<String>({ a, b -> a == b }, { a, b -> a == b })` and asserts `areItemsTheSame("x", "x")` returns `true` while `areItemsTheSame("x", "y")` returns `false`.
3. Add a test that verifies `areContentsTheSame` delegates to the supplied lambda independently of `areItemsTheSame`.
4. Add a test that omitting `getChangePayload` results in `null` being returned, and supplying it returns the lambda's value.
:::

### Unit-test JsonUtils string extraction helpers

`JsonUtils.getString` (and its siblings) is called across the data layer to extract fields from server JSON. All overloads are pure Kotlin/Gson with no Android dependencies, making them straightforward candidates for JVM unit tests.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L16-L46"}

:::task-stub{title="Unit-test JsonUtils string/number extraction"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/JsonUtilsTest.kt`.
2. Add a test that `getString(fieldName, null)` returns `""`.
3. Add a test that `getString` returns `""` when the field holds a `JsonNull` element.
4. Add a test that `getString` returns the correct string for a valid `JsonPrimitive`.
5. Add a test that `getString(array, index)` returns `""` for a `JsonNull` element at that index.
:::

### Unit-test TimeUtils.getAge edge cases

`TimeUtils.getAge` uses only `java.time` APIs and contains several implicit branches: blank input, datetime-formatted strings, date-only strings, and invalid input. Because there is no Android framework call, the method is directly testable on the JVM.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=92 path=app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt#L74-L92"}

:::task-stub{title="Unit-test TimeUtils.getAge edge cases"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/TimeUtilsTest.kt`.
2. Add a test that `getAge("")` returns `0`.
3. Add a test that `getAge` with a well-formed `yyyy-MM-dd` date far in the past returns a positive integer.
4. Add a test that `getAge` accepts the `yyyy-MM-ddTHH:mm:ss.000Z` format (the server timestamp format) without throwing.
5. Add a test that `getAge("not-a-date")` returns `0` and does not throw.
:::

### Unit-test ProgressViewModel.loadCourseData StateFlow emission

`ProgressViewModel` holds a single `_courseData: MutableStateFlow<JsonArray?>` and exposes `loadCourseData()` which calls two injected suspend functions. Both dependencies are interfaces, so the ViewModel can be instantiated with `mockk` stubs without Hilt or Realm.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=28 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt#L14-L28"}

:::task-stub{title="Unit-test ProgressViewModel.loadCourseData StateFlow emission"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/courses/ProgressViewModelTest.kt`.
2. Apply `@get:Rule val mainDispatcherRule = MainDispatcherRule()` (from Task 2) to handle `Dispatchers.setMain` / `resetMain` automatically.
3. Create stubs: `mockk<ProgressRepository>()` where `fetchCourseData(any())` returns a non-empty `JsonArray`; `mockk<UserSessionManager>()` returning a stub user.
4. Assert that `courseData.value` is `null` before calling `loadCourseData()`.
5. Call `loadCourseData()`, advance the test scheduler with `advanceUntilIdle()`, and assert `courseData.value` is the `JsonArray` returned by the stub.
:::

### Unit-test TeamViewModel.prepareTeamData sort order

`TeamViewModel.prepareTeamData` uses the injectable `DispatcherProvider` and produces a sorted `List<TeamDetails>` emitted through `_teamData`. The sort contract (leader > member > non-member, then by visit count) and the archived-team filter are not covered anywhere.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L38-L95"}

:::task-stub{title="Unit-test TeamViewModel.prepareTeamData sort order"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/teams/TeamViewModelTest.kt`.
2. Inject `TestDispatcherProvider` (from Task 2) and a `mockk<TeamsRepository>()` stub where `getRecentVisitCounts` and `getTeamMemberStatuses` return deterministic maps.
3. Add a test that a team with `isLeader=true` appears before one with `isMember=true` which appears before one with neither.
4. Add a test that teams with `status="archived"` are absent from `teamData.value`.
5. Add a test that an empty input list produces an empty `teamData.value` without hitting the repository.
:::

### Unit-test RequestsViewModel optimistic update and revert

`RequestsViewModel.respondToRequest` immediately removes the target member from `_uiState` (optimistic update) and reverts on failure. This branching logic is a good candidate for focused unit tests using `TestDispatcherProvider`.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt#L37-L65"}

:::task-stub{title="Unit-test RequestsViewModel optimistic update and revert"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelTest.kt`.
2. Use `TestDispatcherProvider` and stubs for `TeamsRepository` and `UserSessionManager`.
3. Add a test for `fetchMembers`: stub `getRequestedMembers` to return two `RealmUser` objects and assert `uiState.value.members` contains both after `advanceUntilIdle()`.
4. Add a test for the success path of `respondToRequest`: stub `respondToMemberRequest` to return `Result.success(Unit)`, call `respondToRequest`, and assert the user is removed from `uiState.value.members`.
5. Add a test for the failure path: stub `respondToMemberRequest` to return `Result.failure(Exception("err"))` and assert `uiState.value.members` reverts to the original list.
:::

### Unit-test RatingsViewModel state transitions

`RatingsViewModel.loadRatingData` transitions `_ratingState` from `Loading` → `Success` on the normal path and to `Error` when the repository throws. Both sealed-class states carry parameters that should be verified.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L48-L60"}

:::task-stub{title="Unit-test RatingsViewModel state transitions"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModelTest.kt`.
2. Apply `@get:Rule val mainDispatcherRule = MainDispatcherRule()` (from Task 2) to handle `Dispatchers.setMain` / `resetMain` automatically.
3. Add a test where `getRatingSummary` returns a stub `RatingSummary` and assert `ratingState.value` is `RatingUiState.Success` after `advanceUntilIdle()`.
4. Add a test where `getRatingSummary` throws a `RuntimeException("fail")` and assert `ratingState.value` is `RatingUiState.Error` with the matching message.
5. Add a test that `userState.value` is populated with the value returned by `userRepository.getUserById` on the success path.
:::

### Unit-test CourseProgressViewModel load-once guard

`CourseProgressViewModel.loadProgress` contains an early-return guard (`if (_courseProgress.value != null) return`) to prevent duplicate fetches. This guard is a behaviour contract that should be protected by tests, as accidental removal would cause unnecessary re-fetches on every screen rotation.

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt#L23-L29"}

:::task-stub{title="Unit-test CourseProgressViewModel load-once guard"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModelTest.kt`.
2. Apply `@get:Rule val mainDispatcherRule = MainDispatcherRule()` (from Task 2) to handle `Dispatchers.setMain` / `resetMain` automatically.
3. Add a test that `courseProgress.value` is `null` before any call.
4. Add a test that after one `loadProgress("id")` call, `courseProgress.value` equals the `CourseProgressData` returned by the `CoursesRepository` stub.
5. Add a test that calling `loadProgress` a second time does not invoke `coursesRepository.getCourseProgress` again (verify with `verify(exactly = 1) { ... }` from MockK).
:::

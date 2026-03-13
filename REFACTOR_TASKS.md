### Add JUnit and coroutines-test dependencies to enable unit testing

The project has zero test dependencies declared in `build.gradle` and `libs.versions.toml`. No unit tests can be written or run until JUnit 4/5, kotlinx-coroutines-test, and a mocking library (MockK or Mockito-Kotlin) are added. This is the foundational blocker for every other testing task.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=209 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L160-L209"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=41 path=gradle/libs.versions.toml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle/libs.versions.toml#L1-L41"}

:::task-stub{title="Add test dependencies to build config"}
1. Add `junit`, `mockk`, `kotlinx-coroutines-test`, and `turbine` version entries to `gradle/libs.versions.toml`
2. Add corresponding `testImplementation` library aliases in `libs.versions.toml` `[libraries]` section
3. Add `testImplementation(libs.junit)`, `testImplementation(libs.mockk)`, `testImplementation(libs.coroutines.test)`, `testImplementation(libs.turbine)` to `app/build.gradle` dependencies block
4. Create the directory `app/src/test/java/org/ole/planet/myplanet/` to host unit tests
5. Verify `./gradlew testDefaultDebugUnitTest` runs successfully with zero tests
:::

### Add CI workflow step to run unit tests on every build

The build workflow only assembles the APK but never executes `./gradlew test`. Even once test files exist, CI would silently skip them. A single additional step ensures regressions are caught before merge.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=32 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L28-L32"}

:::task-stub{title="Add unit test step to CI build workflow"}
1. In `.github/workflows/build.yml`, add a new step after the existing `build debug as test` step
2. The new step should run `./gradlew test${FLAVOR^}DebugUnitTest --parallel --max-workers=4`
3. Name the step `run unit tests`
:::

### Add unit tests for TimeUtils pure date-formatting functions

`TimeUtils` contains pure functions like `getAge`, `getFormattedDate`, and `formatDateToDDMMYYYY` that accept primitives and return strings or ints with no Android or Realm dependencies. These are the easiest first tests to write in the project.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=92 path=app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt#L74-L92"}
:codex-file-citation[codex-file-citation]{line_range_start=185 line_range_end=215 path=app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt#L185-L215"}

:::task-stub{title="Add unit tests for TimeUtils date helpers"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/TimeUtilsTest.kt`
2. Write tests for `getAge()` covering valid ISO date, blank string, date-only format, and future date edge case
3. Write tests for `formatDateToDDMMYYYY()` covering valid input, null input, and malformed string
4. Write tests for `convertDDMMYYYYToISO()` covering roundtrip with `formatDateToDDMMYYYY`
:::

### Add unit tests for JsonUtils extraction functions

`JsonUtils` provides null-safe JSON field extraction (`getString`, `getInt`, `getLong`, `getBoolean`, `getJsonArray`) that are used across 30+ model companion objects. Testing these pure Gson functions requires only constructing `JsonObject` instances.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L17-L35"}
:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=74 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L62-L74"}
:codex-file-citation[codex-file-citation]{line_range_start=102 line_range_end=155 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L102-L155"}

:::task-stub{title="Add unit tests for JsonUtils extraction helpers"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/JsonUtilsTest.kt`
2. Test `getString` with present key, missing key, null JsonObject, and JsonNull value
3. Test `getInt` with valid int, missing key, and null fallback
4. Test `getLong`, `getFloat`, `getBoolean` with present/missing/null cases
5. Test `getJsonArray` and `getJsonObject` with present key and missing key
:::

### Add unit tests for FileUtils path-parsing functions

`getFileNameFromLocalAddress`, `getFileNameFromUrl`, `getIdFromUrl`, and `getFileExtension` are pure string-parsing functions with no Android context requirement. They are heavily relied upon for download and resource display logic.

:codex-file-citation[codex-file-citation]{line_range_start=78 line_range_end=111 path=app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt#L78-L111"}

:::task-stub{title="Add unit tests for FileUtils path and URL parsing"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`
2. Test `getFileNameFromLocalAddress` with normal path, null, blank, and path with no slash
3. Test `getFileNameFromUrl` with encoded URL, simple URL, null, and malformed URL
4. Test `getIdFromUrl` with valid resource URL and edge cases
5. Test `getFileExtension` with common extensions, no extension, null, and dotfiles
:::

### Add unit tests for ExamAnswerUtils answer-checking logic

`ExamAnswerUtils` contains branching logic for select, multi-select, and text answer validation. The private helpers (`checkSelectAnswer`, `checkMultipleSelectAnswer`, `checkTextAnswer`, `isEqual`) are exercised through the public `checkCorrectAnswer` entry point which only depends on `RealmExamQuestion` fields.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=62 path=app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt#L8-L62"}

:::task-stub{title="Add unit tests for ExamAnswerUtils answer validation"}
1. Create `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`
2. Test `checkCorrectAnswer` with type="select", correct answer, wrong answer, and case-insensitive match
3. Test `checkCorrectAnswer` with type="selectMultiple", matching set, subset, and superset
4. Test `checkCorrectAnswer` with type="input" (text), partial match, no match, and case-insensitive match
5. Test null question and empty answer edge cases
:::

### Add dispatcher to NotificationsViewModel coroutine launches

`NotificationsViewModel.loadNotifications` calls repository suspend functions (which access Realm) inside `viewModelScope.launch` without specifying `dispatcherProvider.io`. This risks running database I/O on the Main dispatcher. Adding the explicit dispatcher is a one-line change per launch site.

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L34-L41"}

:::task-stub{title="Add IO dispatcher to NotificationsViewModel launches"}
1. In `NotificationsViewModel.kt`, change `viewModelScope.launch {` to `viewModelScope.launch(dispatcherProvider.io) {` at every launch site that calls repository suspend functions
2. Verify `dispatcherProvider` is already injected (it should be via constructor); if not, add the injection
3. Write a unit test in `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` using `TestDispatcher` to verify `loadNotifications` emits the expected state
:::

### Add dispatcher to RatingsViewModel coroutine launches

`RatingsViewModel.loadRatingData` and `submitRating` call `ratingsRepository` and `userRepository` suspend functions inside `viewModelScope.launch` without an explicit dispatcher. Both methods perform Realm-backed I/O that should be off the main thread.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=62 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L48-L62"}
:codex-file-citation[codex-file-citation]{line_range_start=72 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L72-L84"}

:::task-stub{title="Add IO dispatcher to RatingsViewModel launches"}
1. Change both `viewModelScope.launch {` calls in `loadRatingData` and `submitRating` to `viewModelScope.launch(dispatcherProvider.io) {`
2. Ensure `dispatcherProvider` is injected; add if missing
3. Write a unit test `RatingsViewModelTest.kt` verifying `loadRatingData` transitions state from Loading to success/error using `TestDispatcher`
:::

### Add dispatcher to SurveysViewModel coroutine launches

`SurveysViewModel.loadSurveys` performs multiple repository calls (`getAdoptableTeamSurveys`, `getSurveyInfos`, `getSurveyFormState`) inside `viewModelScope.launch` on the default (Main) dispatcher. These are Realm-backed suspend functions that should execute on IO.

:codex-file-citation[codex-file-citation]{line_range_start=65 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L65-L95"}

:::task-stub{title="Add IO dispatcher to SurveysViewModel launches"}
1. Change `viewModelScope.launch {` in `loadSurveys` and other launch sites to `viewModelScope.launch(dispatcherProvider.io) {`
2. Ensure `dispatcherProvider` is injected; add if missing
3. Write a unit test `SurveysViewModelTest.kt` confirming `loadSurveys` sets `_isLoading` to true then false, and handles exceptions via `_errorMessage`
:::

### Add dispatcher to UserProfileViewModel init block and methods

`UserProfileViewModel` has an `init {}` block launching `viewModelScope.launch` without a dispatcher, calling `userSessionManager` methods that read from SharedPreferences and Realm. The same pattern repeats in `getOfflineVisits`. All should use an explicit IO dispatcher.

:codex-file-citation[codex-file-citation]{line_range_start=122 line_range_end=134 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt#L122-L134"}

:::task-stub{title="Add IO dispatcher to UserProfileViewModel launches"}
1. Change `viewModelScope.launch {` in the `init` block (line 123) to `viewModelScope.launch(dispatcherProvider.io) {`
2. Change `viewModelScope.launch {` in `getOfflineVisits` (line 131) and all other launch sites to use `dispatcherProvider.io`
3. Ensure `dispatcherProvider` is injected; add if missing
4. Write a unit test `UserProfileViewModelTest.kt` verifying that init-block state flows emit expected values using `TestDispatcher`
:::

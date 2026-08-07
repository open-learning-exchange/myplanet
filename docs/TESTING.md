# myPlanet Testing Guide

This guide explains how tests are actually written in this codebase, based on the 163 test classes (984 `@Test` methods) under `app/src/test/`. When writing a new test, find the closest existing test of the same kind listed below and copy its shape — don't invent a new pattern.

> The app's local database is **Room** (the Realm migration is complete). Tests mock DAOs with MockK, or spin up a real in-memory Room database under Robolectric when actual SQL behavior matters.

---

## Table of Contents

1. [Test Source Sets](#test-source-sets)
2. [Libraries in Use](#libraries-in-use)
3. [Shared Test Infrastructure](#shared-test-infrastructure)
4. [How to Test Each Layer](#how-to-test-each-layer)
5. [Naming Conventions](#naming-conventions)
6. [Running Tests](#running-tests)
7. [Patterns Worth Copying, by Example File](#patterns-worth-copying-by-example-file)
8. [Things to Avoid](#things-to-avoid)

---

## Test Source Sets

### `app/src/test/` — JVM unit tests (the only source set)

Everything runs on the local JVM — no emulator or device needed. **CI runs this**: `.github/workflows/test.yml` runs `./gradlew testDefaultDebugUnitTest` on every push to every branch. If a test isn't in `src/test/`, it isn't verified automatically.

There is currently **no `app/src/androidTest/` (instrumented) source set** — the `androidTestImplementation` dependencies are still declared in `app/build.gradle`, but no instrumented sources exist and no workflow runs an emulator.

When you need to verify real database behavior (actual SQL, `Converters`, transactions), you don't need a device: use **Robolectric + `Room.inMemoryDatabaseBuilder`** inside `src/test/` — see [DAO / Room round-trip tests](#daos--room-round-trip-tests) below.

Package breakdown (166 files = 163 test classes + 3 shared infra): `utils/` 44, `ui/` 39, `repository/` 32, `services/` 22, `model/` 11, `data/` 8, `base/` 7, `di/` 2, root 1.

---

## Libraries in Use

From `app/build.gradle` (`testImplementation` block) and what's actually imported across the suite:

| Library | Purpose | Notes |
|---------|---------|-------|
| JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`) | Test runner and assertions | Used everywhere |
| **MockK** (`io.mockk.*`) | Mocking | **The standard.** Used in 113 of the 166 files. |
| Mockito (`org.mockito.*`) | Mocking | Legacy — exactly 2 files (`CoursesAdapterTest`, `SubmissionViewModelTest`). Don't introduce new Mockito usage; use MockK. |
| Robolectric (`org.robolectric.*`) | Android framework on the JVM | 43 files — wherever a test needs real Android classes (`Context`, `View`, resource strings, Room) without an emulator |
| `kotlinx-coroutines-test` | `runTest`, `TestDispatcher`, `UnconfinedTestDispatcher`, `StandardTestDispatcher` | For suspend functions and Flow/StateFlow-based ViewModels |
| `androidx.test` (`ApplicationProvider`, `AndroidJUnit4`) | Application context access | Used inside Robolectric JVM tests |
| `androidx.room:room-testing` | Room test helpers | Backs the in-memory Room tests |

---

## Shared Test Infrastructure

All three helpers live in `app/src/test/java/org/ole/planet/myplanet/utils/` (package `org.ole.planet.myplanet.utils`).

### `MainDispatcherRule`

Swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope.launch { }` runs synchronously. Use it in any ViewModel test. There is exactly **one** copy — import it from `utils`.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(testDispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
```

### `TestDispatcherProvider`

A test double for the app's `DispatcherProvider` interface (`utils/DispatcherProvider.kt`) that routes every dispatcher to a single `TestDispatcher` (required constructor parameter — no default):

```kotlin
class TestDispatcherProvider(private val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
```

Use it for any class that takes `DispatcherProvider` in its constructor (most repositories, sync managers, several ViewModels). Production code injects `DispatcherProvider` rather than hard-coding `Dispatchers.*` precisely so tests can do this.

### `TestTimeProvider`

A controllable `TimeProvider`: `TestTimeProvider(var currentTime: Long = 0L)` with `advanceBy(millis)`. Use it for anything time-dependent instead of `System.currentTimeMillis()`.

---

## How to Test Each Layer

### ViewModels

Mock the repository/manager dependencies with MockK, install `MainDispatcherRule`, construct the ViewModel directly (no Hilt needed), and assert on the exposed `StateFlow` value.

Reference: `ui/courses/CourseProgressViewModelTest.kt`

```kotlin
class CourseProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coursesRepository = mockk<CoursesRepository>()
    private val userSessionManager = mockk<UserSessionManager>()
    private val viewModel = CourseProgressViewModel(coursesRepository, userSessionManager)

    @Test
    fun `courseProgress value is null before any call`() {
        assertNull(viewModel.courseProgress.value)
    }

    @Test
    fun `calling loadProgress twice only invokes coursesRepository once`() = runTest {
        // ... coEvery setup ...
        viewModel.loadProgress(courseId)
        viewModel.loadProgress(courseId)
        coVerify(exactly = 1) { coursesRepository.getCourseProgress(courseId, userId) }
    }
}
```

Key things to copy:
- Constructor-inject mocks directly; don't use Hilt's test runner for plain ViewModel unit tests.
- Use `coEvery { }` for suspend functions, `every { }` for non-suspend.
- Test idempotency/caching with `coVerify(exactly = N) { }` — this codebase cares about not re-fetching data unnecessarily.
- Test the *initial* state before any action, not just the state after.

### Repositories (mocked DAOs)

Repositories inject Room DAOs directly, so the standard repository test mocks **every DAO and collaborator with `mockk(relaxed = true)`** and stubs DAO returns with `coEvery`.

Reference: `repository/CoursesRepositoryImplTest.kt`

```kotlin
class CoursesRepositoryImplTest {
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val courseDao: CourseDao = mockk(relaxed = true)
    private val courseStepDao: CourseStepDao = mockk(relaxed = true)
    private val examDao: ExamDao = mockk(relaxed = true)
    // ... the impl's other DAOs, all mockk(relaxed = true) ...
    private val userRepository: dagger.Lazy<UserRepository> = mockk(relaxed = true)

    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setup() {
        repository = CoursesRepositoryImpl(/* mocks + a test dispatcher */)
    }

    @Test
    fun `search empty query returns all courses`() = runTest {
        coEvery { courseDao.getAll() } returns listOf(
            MyCourse(id = "id1", courseId = "id1", courseTitle = "Math", courseTitleNormal = "math")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val result = repository.search("")

        assertEquals(1, result.size)
        assertEquals("Math", result.first().courseTitle)
    }

    @Test
    fun testMatchesAllParts() {
        assertTrue(repository.matchesAllParts("hello world", listOf("hello", "world")))
        assertFalse(repository.matchesAllParts("hello world", listOf("hello", "universe")))
    }
}
```

Two kinds of assertions dominate: pure helper logic tested directly (no stubbing needed), and DAO-interaction logic verified with `coEvery` + `coVerify`. Entities are plain Kotlin classes — construct them with named args, no database needed.

### DAOs / Room round-trip tests

When mocked DAOs aren't enough — you need real SQL, `Converters` serialization, or transaction semantics — use Robolectric with an in-memory Room database. Six tests do this today.

References: `data/room/AppDatabaseRoundTripTest.kt` (insert-and-read round-trips through the real DAOs and `Converters` against Robolectric's SQLite — guards the JSON list/embedded-object converters and the LIKE-on-JSON shelf-membership query), `data/room/dao/NewsDaoTest.kt` (LIKE-escaping in queries), `repository/TeamsRepositoryBulkInsertTransactionTest.kt` (verifies `bulkInsertFromSync` commits inside a single `appDatabase.withTransaction { }`). Note: the schema is not exported (`exportSchema = false`), so these tests exercise the live schema, not JSON schema files.

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [32])
class NewsDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun getTopLevelByTeam_escapesLikeWildcards() = runBlocking { /* insert via DAO, query, assert rows */ }
}
```

Always `db.close()` in `@After`, and keep these tests focused on behavior a mock can't express.

### Entity / model classes

Room entities behave like plain Kotlin objects — construct with `UserEntity()` + property assignment, no database. If the class's methods touch global app state (`MainApplication`, `Utilities.toast`), stub it in `@Before` and restore in `@After`.

Reference: `model/UserEntityTest.kt`

```kotlin
class UserEntityTest {
    private var originalContext: Context? = null
    private var originalScope: CoroutineScope? = null

    @Before
    fun setup() {
        // applicationScope is lateinit — reading it before anything initialized it throws,
        // so the capture is guarded (same reason the context capture below is guarded)
        originalScope = try { MainApplication.applicationScope } catch (_: UninitializedPropertyAccessException) { null }
        Dispatchers.setMain(Dispatchers.Unconfined)
        MainApplication.applicationScope = CoroutineScope(Dispatchers.Unconfined)
        mockkObject(Utilities)
        every { Utilities.toast(any(), any()) } returns Unit
        originalContext = try { MainApplication.context } catch (_: Exception) { null }
        MainApplication.testContext = mockContext
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        // cancel + restore only when an original scope existed — never leave the
        // global holding a cancelled scope (later tests would silently no-op)
        originalScope?.let {
            MainApplication.applicationScope.cancel()
            MainApplication.applicationScope = it
        }
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testIsManagerWithManagerRole() {
        val user = UserEntity()
        user.rolesList = mutableListOf("manager")
        user.userAdmin = false
        assertTrue(user.isManager())
    }
}
```

### Workers (`CoroutineWorker` / WorkManager)

Mock every constructor dependency, mock the static `WorkManager`/`WorkManagerImpl` calls with `mockkStatic`, and verify scheduling/enqueue calls rather than letting WorkManager actually run.

Reference: `services/retry/RetryQueueWorkerTest.kt`

```kotlin
class RetryQueueWorkerTest {
    @MockK(relaxed = true) lateinit var workManagerImpl: WorkManagerImpl
    @MockK(relaxed = true) lateinit var context: MainApplication
    @MockK lateinit var workerParams: WorkerParameters
    @MockK lateinit var retryQueue: RetryQueue
    @MockK lateinit var apiInterface: ApiInterface

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        // ... stub other Log levels the same way ...
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManagerImpl
        mockkStatic(WorkManager::class)
    }

    @After
    fun tearDown() = unmockkAll()
}
```

`mockkStatic(Log::class)` plus stubbing each `Log.d/i/w/e` overload is needed whenever the class under test logs — otherwise the JVM has no `android.util.Log` implementation and the test crashes. Copy that block whenever `Log.*` calls are reachable.

### Adapters (`RecyclerView.Adapter` / `ListAdapter`)

Use Robolectric (`@RunWith(RobolectricTestRunner::class)`) when the adapter touches real Android view/context behavior. Mock the `Context` and any `AdapterDataObserver` you verify against.

Reference (pattern): `ui/courses/CoursesAdapterTest.kt` — but note this specific file is one of the 2 legacy **Mockito** files. **For a new adapter test, use MockK's equivalents** (`mockk<Context>()`, `verify(exactly = 1) { mockObserver.onItemRangeChanged(...) }`); the MockK-based adapter tests (`ui/events/EventsAdapterTest.kt`, `ui/notifications/NotificationsAdapterTest.kt`, `ui/teams/TeamsSelectionAdapterTest.kt`, …) are the shapes to copy.

### Base/Abstract Classes

You can't instantiate an `abstract class` directly. Create a minimal private test subclass inside the test file that implements only what's required to compile, then test the base class's concrete methods through it.

Reference: `base/BaseRecyclerFragmentTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BaseRecyclerFragmentTest {

    class TestBaseRecyclerFragment : BaseRecyclerFragment<Any>() {
        override fun getLayout(): Int = 0
        override suspend fun getAdapter(): ListAdapter<*, *> { throw NotImplementedError() }
    }

    @Test
    fun showNoData_withZeroCount_makesViewVisibleAndSetsMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val textView = TextView(context)
        BaseRecyclerFragment.showNoData(textView, 0, "courses")
        assertEquals(View.VISIBLE, textView.visibility)
        assertEquals(context.getString(R.string.no_courses), textView.text.toString())
    }
}
```

Real `strings.xml` resources are used in assertions (`context.getString(R.string.no_courses)`) rather than hardcoded literals — the standard approach whenever Robolectric gives you a real `Context`, so the test doesn't drift from the actual string resource.

### Plain Utility Functions

If the utility is pure Kotlin with no Android dependency, a plain JUnit test with no `@RunWith` annotation is enough (`utils/TimeUtilsTest.kt`, `utils/JsonUtilsTest.kt`). If it touches `Context`, `SharedPreferences`, or other framework classes, add `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [...])` and get the context from `ApplicationProvider` (`utils/ConstantsTest.kt`).

### DI Modules and the API/auth layer

- `di/NetworkModuleTest.kt` calls `NetworkModule.provideGson()` directly (no mocks) and asserts serialization behavior.
- `utils/DispatcherProviderGuardTest.kt` reflectively asserts `DispatcherModule` carries `@Module`/`@Provides`/`@Singleton`.
- `data/auth/AuthSessionUpdaterTest.kt`, `data/api/ApiClientTest.kt`, `data/api/RetryInterceptorTest.kt` cover the network/auth layer with MockK + coroutine test dispatchers.

---

## Naming Conventions

The suite is genuinely mixed, roughly 50/50 (512 backtick-style vs 472 camelCase-style methods), sometimes within one file. Neither is enforced — pick whichever reads more clearly, but when adding tests to an existing file, match that file's style.

```kotlin
// Backtick descriptive style — common for ViewModel/behavior tests
@Test
fun `loadProgress sets courseProgress value correctly`() = runTest { ... }

// camelCase style — common for utility/model tests
@Test
fun testIsManagerWithManagerRole() { ... }

// camelCase, scenario-suffixed style — common for base-class/adapter tests
@Test
fun showNoData_withZeroCount_makesViewVisibleAndSetsMessage() { ... }
```

Test class names always match `{ClassUnderTest}Test.kt` (e.g. `CoursesRepositoryImplTest.kt`). Don't append `UnitTest`, `Tests` (plural), or `Spec`.

---

## Running Tests

```bash
# Run all unit tests (what CI runs)
./gradlew testDefaultDebugUnitTest

# Run a single test class
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CourseProgressViewModelTest"

# Run a single test method
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CourseProgressViewModelTest.loadProgress sets courseProgress value correctly"

# Lite flavor (NOT covered by CI — run locally when touching flavor-specific code)
./gradlew testLiteDebugUnitTest
```

CI (`.github/workflows/test.yml`, "myPlanet test") runs on every push to every branch (no `pull_request` trigger — the push itself triggers it) plus manual dispatch: `./gradlew test${FLAVOR^}DebugUnitTest --configuration-cache-problems=warn --warning-mode all --stacktrace --parallel --max-workers=4` (matrix is `default` only) on `ubuntu-24.04`. On failure it uploads `app/build/reports/tests/` as the `test-reports-default` artifact (7-day retention); a timing summary always runs. There is no separate lint or coverage gate — passing `testDefaultDebugUnitTest` is the bar.

---

## Patterns Worth Copying, by Example File

| You're testing a... | Copy the shape of | Key technique |
|---|---|---|
| ViewModel with `StateFlow` | `ui/courses/CourseProgressViewModelTest.kt` | `MainDispatcherRule` + `mockk()` + `coEvery`/`coVerify` |
| Repository (DAO-backed) | `repository/CoursesRepositoryImplTest.kt` | `mockk(relaxed = true)` for every DAO/collaborator |
| Real SQL / Converters / transactions | `data/room/AppDatabaseRoundTripTest.kt`, `data/room/dao/NewsDaoTest.kt` | Robolectric + `Room.inMemoryDatabaseBuilder` |
| Transaction atomicity in a repository | `repository/TeamsRepositoryBulkInsertTransactionTest.kt` | In-memory Room + `withTransaction` verification |
| Entity with role/permission logic | `model/UserEntityTest.kt` | Plain construction; `mockkObject(Utilities)` + `MainApplication.testContext` for side effects |
| `CoroutineWorker` / WorkManager scheduling | `services/retry/RetryQueueWorkerTest.kt` | `mockkStatic(WorkManager::class)`, mock `Log.*` |
| `RecyclerView`/`ListAdapter` | `ui/events/EventsAdapterTest.kt` | `RobolectricTestRunner` + MockK (avoid the Mockito legacy files) |
| Abstract base class | `base/BaseRecyclerFragmentTest.kt` | Minimal private test subclass implementing only the abstract members |
| Pure Kotlin utility | `utils/TimeUtilsTest.kt`, `utils/JsonUtilsTest.kt` | Plain JUnit, no `@RunWith` |
| Utility touching `Context`/`SharedPreferences` | `utils/ConstantsTest.kt` | `RobolectricTestRunner` + `ApplicationProvider.getApplicationContext()` |
| Sync managers | `services/sync/SyncManagerTest.kt`, `services/sync/LoginSyncManagerTest.kt` | MockK + `TestDispatcherProvider` |

---

## Things to Avoid

**Don't introduce new Mockito usage.** Two legacy files use it (`CoursesAdapterTest`, `SubmissionViewModelTest`); the other 113 mocking files use MockK. Use MockK for anything new.

**Don't forget `mockkStatic(Log::class)` (and stub each level) when the code under test logs.** Otherwise the test crashes calling into the real `android.util.Log`, which doesn't exist on the JVM.

**Don't forget `unmockkAll()` in `tearDown()`** after any `mockkStatic`/`mockkObject` call, or static mocks leak into other tests in the same JVM process.

**Don't assert query results against a mocked DAO.** A `mockk(relaxed = true)` DAO returns whatever you stub — it doesn't run SQL. If you need to confirm a query actually filters/escapes correctly, use an in-memory Room database (see `NewsDaoTest.kt`), still inside `src/test/`.

**Don't forget `db.close()` in `@After`** when using `Room.inMemoryDatabaseBuilder`.

**Don't hardcode expected UI strings when Robolectric gives you a real `Context`.** Use `context.getString(R.string.my_string)` in the assertion, like `BaseRecyclerFragmentTest` does.

**Don't skip `MainDispatcherRule` on a ViewModel test that calls `viewModelScope.launch`.** Without it, the coroutine won't run synchronously and your assertion checks state before the launch block executed.

**Don't hard-code `Dispatchers.IO` in production code you want to test.** Inject `DispatcherProvider` and pass `TestDispatcherProvider(testDispatcher)` in tests — that's why the abstraction exists.

**Don't create an `androidTest/` source set for something an in-memory Room test can cover.** Instrumented tests don't run in CI; a bug caught only there won't block a PR.

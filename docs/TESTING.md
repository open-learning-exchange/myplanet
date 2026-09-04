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

Swaps `Dispatchers.Main` for a test dispatcher. With the default `UnconfinedTestDispatcher`, `viewModelScope.launch { }` runs eagerly (effectively synchronously); if you pass a `StandardTestDispatcher` instead, coroutines queue until you call `runCurrent()` / `advanceUntilIdle()`. Use it in any ViewModel test. There is exactly **one** copy — import it from `utils`.

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

### The Robolectric application class

`app/src/test/resources/robolectric.properties` sets `application=android.app.Application` for
the whole `src/test/` source set. Leave it that way, and reach for
`@Config(application = ...)` only when a test genuinely needs a different one
(`HiltTestApplication` for `@HiltAndroidTest`).

Without that default Robolectric boots the manifest's application class — the real
`@HiltAndroidApp MainApplication` — for every test that doesn't say otherwise.
`MainApplication.onCreate()` builds the Hilt graph, opens the Room database and then
launches fire-and-forget coroutines on the *real* `Dispatchers.IO`/`Default` from an
application scope nothing cancels: the deferred warm-ups, `scheduleWorkersOnStart`, and
the ANR watchdog — which promptly "detects" an ANR, because Robolectric only pumps the
main looper when a test asks it to. Those coroutines outlive the test class. A later
class in the same fork that `mockkObject(MainApplication.Companion)`s and unmocks it
turns their next companion call into `MockKException: can't find stub Companion`, which
escapes to the global handler, where `kotlinx-coroutines-test` queues it and charges it
to whichever test calls `runTest` next. The report then blames an unrelated class with
`UncaughtExceptionsBeforeTest` — a flake that a rerun always "fixes". Six PRs hit it on
2026-09-04 alone, all on shard 2, which is where most of the affected classes hash.

If you see `UncaughtExceptionsBeforeTest`, the named test is the victim, not the cause:
open the uploaded `test-reports-*` artifact and read the `Suppressed:` stack trace, which
names the coroutine that actually threw.

### Robolectric SDK levels

**Don't pin `@Config(sdk = [...])` unless the test is actually about that API level.** Robolectric builds one sandbox per distinct (SDK level, config) per test fork and each sandbox loads a 95-215 MB `android-all-instrumented` jar, so every extra level the suite mentions is paid again — up to `maxParallelForks` (4) times per shard. That cost shows up as a multi-second first test in the class: it was 16.0s for `TeamsRepositoryBulkInsertTransactionTest` (SDK 26) against a 0.43s average for the rest of its methods.

Omit `sdk` and the test runs on the default, which Robolectric takes from `targetSdk` (36). Keep the rest of `@Config` (`application = ...`, `manifest = Config.NONE`) as needed — omitting `sdk` only drops the pin.

One consequence worth knowing: Robolectric keys a sandbox by SDK level (plus the instrumentation config and the `LooperMode`/`GraphicsMode`/`SQLiteMode` settings), **not** by test class, so every class on the same level shares one classloader and therefore one copy of your `object` statics. Meanwhile each test *method* gets a fresh temp directory. A `File` or `Context` cached in an `object` and never reset will outlive the directory it points at, and unpinning a class changes which other classes can leave that cache dirty — see the note in [Things to Avoid](#things-to-avoid).

Pin only when the assertion depends on the level, and say why in a comment so the next reader doesn't fold it away:

| Test | Pin | Why |
| --- | --- | --- |
| `services/DownloadServiceTest.kt` | class `UPSIDE_DOWN_CAKE` (34), methods `R` (30) / `S` (31) | asserts the API-gated foreground-service/worker branches |
| `utils/VersionUtilsTest.kt` | methods `O` (26), `P` (28) | `VersionUtils` branches on `SDK_INT >= P` |
| `utils/SecurePrefsTest.kt` | `O_MR1` (27) | keystore-backed prefs path |
| `utils/NotificationUtilsTest.kt` | `O` (26) | notification channels exist only from `O` |
| `repository/TeamsRepositoryBulkInsertTransactionTest.kt` | `26` | needs to sit below `S` so `processDescription` short-circuits instead of reaching for `MainApplication.context` |

The suite therefore needs sandboxes at 26, 27, 28, 30, 31, 34 and the default 36 — nothing else.

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
@Config(application = Application::class)
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

If the utility is pure Kotlin with no Android dependency, a plain JUnit test with no `@RunWith` annotation is enough (`utils/TimeUtilsTest.kt`, `utils/JsonUtilsTest.kt`). If it touches `Context`, `SharedPreferences`, or other framework classes, add `@RunWith(RobolectricTestRunner::class)` and get the context from `ApplicationProvider` (`utils/ConstantsTest.kt`). Leave the SDK level alone — see [Robolectric SDK levels](#robolectric-sdk-levels) below.

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

CI (`.github/workflows/test.yml`, "myPlanet test") runs on every push to every branch (no `pull_request` trigger — the push itself triggers it) plus manual dispatch: `./gradlew test${FLAVOR^}DebugUnitTest -PtestShardTotal=2 -PtestShardIndex=<0|1> -ProbolectricOffline=true --configuration-cache-problems=warn --warning-mode all --stacktrace --parallel --max-workers=4` (matrix is `default` only) on `ubuntu-24.04`, split across two shards that each run half the test classes (`app/build.gradle` `testOptions` hashes each top-level class into a shard). On failure each shard uploads `app/build/reports/tests/` as `test-reports-default-shard-<n>` (7-day retention); a timing summary always runs. There is no separate lint or coverage gate — passing `testDefaultDebugUnitTest` is the bar.

### The Robolectric `android-all` runtime

Robolectric doesn't ship the Android framework it runs your test against — it downloads an `android-all-instrumented` jar (95–215 MB, one per API level) the first time a test fork needs that level. Left to itself it does that *from inside the test fork*, and because tests run in four parallel forks two of them can fetch the same jar at once; the loser reads a half-written file and every Robolectric test that follows in that JVM fails (`AndroidVersions.CURRENT` is null, then `NoSuchFieldError` on framework fields) while the other forks stay green. A rerun passes, which is what makes it costly.

So CI passes `-ProbolectricOffline=true`: Gradle resolves the jars into `build/robolectric-sdks` before the test task starts and Robolectric is told to look there and fetch nothing (`robolectric.offline`, `robolectric.dependency.dir`). The list of levels lives in `robolectricSdkJars` in `app/build.gradle`.

The workflow also fails the job if a jar turns up in Robolectric's own runtime cache (`~/.m2/repository/org/robolectric`), which can only happen if `-ProbolectricOffline=true` stopped reaching the test forks — the one regression whose other symptom is just the flake coming back.

**If you add `@Config(sdk = [N])` for a level nothing else uses**, add the matching jar version to that map, or the CI run fails with `Path is not a file: build/robolectric-sdks/android-all-instrumented-<version>.jar`. The version strings come from Robolectric's `DefaultSdkProvider` and all of them change when Robolectric is bumped. Prefer reusing a level the suite already pins — every extra level is another jar to download, and one more Robolectric sandbox to build per fork.

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

**Don't let a production `object` cache leak across tests.** `FileUtils.cachedExternalFilesDir`, `LocaleUtils.cachedLanguage`, `UrlUtils`' state — these live for the whole sandbox, which is shared by every test class on the same SDK level. Reset them in `@Before` (reflection on the private field, as `FileUtilsTest` and `LocaleUtilsTest` do, or a purpose-built `internal fun resetForTesting()` like `UrlUtils` has), otherwise the test passes only as long as nothing else happens to run first.

**Don't pin `@Config(sdk = [...])` out of habit.** A pin the assertions don't need still costs a per-fork sandbox build and an `android-all` jar download; see [Robolectric SDK levels](#robolectric-sdk-levels).

**Don't introduce new Mockito usage.** Two legacy files use it (`CoursesAdapterTest`, `SubmissionViewModelTest`); the other 113 mocking files use MockK. Use MockK for anything new.

**Don't forget `mockkStatic(Log::class)` (and stub each level) when the code under test logs.** Otherwise the test crashes calling into the real `android.util.Log`, which doesn't exist on the JVM.

**Don't forget `unmockkAll()` in `tearDown()`** after any `mockkStatic`/`mockkObject` call, or static mocks leak into other tests in the same JVM process.

**Don't assert query results against a mocked DAO.** A `mockk(relaxed = true)` DAO returns whatever you stub — it doesn't run SQL. If you need to confirm a query actually filters/escapes correctly, use an in-memory Room database (see `NewsDaoTest.kt`), still inside `src/test/`.

**Don't forget `db.close()` in `@After`** when using `Room.inMemoryDatabaseBuilder`.

**Don't hardcode expected UI strings when Robolectric gives you a real `Context`.** Use `context.getString(R.string.my_string)` in the assertion, like `BaseRecyclerFragmentTest` does.

**Don't skip `MainDispatcherRule` on a ViewModel test that calls `viewModelScope.launch`.** Without it, the coroutine won't run synchronously and your assertion checks state before the launch block executed.

**Don't hard-code `Dispatchers.IO` in production code you want to test.** Inject `DispatcherProvider` and pass `TestDispatcherProvider(testDispatcher)` in tests — that's why the abstraction exists.

**Don't create an `androidTest/` source set for something an in-memory Room test can cover.** Instrumented tests don't run in CI; a bug caught only there won't block a PR.

# myPlanet Testing Guide

This guide explains how tests are actually written in this codebase, based on the 130+ unit tests that already exist under `app/src/test/`. When writing a new test, find the closest existing test of the same kind listed below and copy its shape — don't invent a new pattern.

---

## Table of Contents

1. [Test Source Sets — Two Different Worlds](#test-source-sets--two-different-worlds)
2. [Libraries in Use](#libraries-in-use)
3. [Shared Test Infrastructure](#shared-test-infrastructure)
4. [How to Test Each Layer](#how-to-test-each-layer)
5. [Naming Conventions](#naming-conventions)
6. [Running Tests](#running-tests)
7. [Patterns Worth Copying, by Example File](#patterns-worth-copying-by-example-file)
8. [Things to Avoid](#things-to-avoid)

---

## Test Source Sets — Two Different Worlds

There are two test source sets and they behave completely differently. Picking the right one matters.

### `app/src/test/` — JVM unit tests (130+ files, this is where you almost always write tests)

These run on the local JVM, no emulator or device needed. **CI only runs this source set** — `.github/workflows/test.yml` runs `./gradlew testDefaultDebugUnitTest` on every push. If a test isn't in `src/test/`, it isn't verified automatically.

Critically: **no test in `src/test/` uses a real Realm database.** Even `RealmRepositoryTest.kt`, which is the closest thing to a "Realm integration test" in this source set, mocks `Realm` itself with `mockk(relaxed = true)` — it never calls `Realm.init()` or builds a real `RealmConfiguration`. Every repository test mocks `DatabaseService` and asserts on logic that doesn't require an actual database round-trip (string normalization, filtering, mapping, what gets called and with what arguments).

This works because `RealmObject` subclasses behave like plain Kotlin objects until they're persisted — you can do `val user = RealmUser(); user.rolesList = RealmList<String?>().apply { add("manager") }` with no live Realm at all, as long as you're just reading/writing fields and not calling Realm query methods. See `model/RealmUserTest.kt`.

### `app/src/androidTest/` — instrumented tests (2 files only, rarely needed)

These run on a real device or emulator via `AndroidJUnit4` and **do** spin up a real, in-memory Realm:

```kotlin
@RunWith(AndroidJUnit4::class)
class DatabaseServiceTest {
    @Before
    fun setUp() {
        Realm.init(ApplicationProvider.getApplicationContext())
        realmConfiguration = RealmConfiguration.Builder()
            .name("test-realm")
            .inMemory()
            .allowWritesOnUiThread(true)
            .allowQueriesOnUiThread(true)
            .schemaVersion(1)
            .build()
        Realm.setDefaultConfiguration(realmConfiguration)
    }

    @After
    fun tearDown() {
        Realm.deleteRealm(realmConfiguration)
    }
}
```

Only write a test here if you genuinely need to verify real Realm query behavior (e.g. confirming a query actually returns the right rows, not just that a method was called). This source set is **not run in CI** — there's no emulator step in any workflow — so a bug only caught here won't block a PR. Default to `src/test/` unless you have a specific reason not to.

---

## Libraries in Use

From `app/build.gradle` (`testImplementation` block) and what's actually imported across the suite:

| Library | Purpose | Notes |
|---------|---------|-------|
| JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`) | Test runner and assertions | Used everywhere |
| **MockK** (`io.mockk.*`) | Mocking | **The standard.** Used in ~100 of the ~130 test files. |
| Mockito (`org.mockito.*`) | Mocking | Legacy — only 2 files (`SubmissionViewModelTest`, `CoursesAdapterTest`). Don't introduce new Mockito usage; use MockK. |
| Robolectric (`org.robolectric.*`) | Android framework shadow for JVM tests | Used wherever a test needs real Android classes (`Context`, `View`, resource strings) without an emulator. ~25 files use it. |
| `kotlinx-coroutines-test` | `runTest`, `TestDispatcher`, `UnconfinedTestDispatcher`, `StandardTestDispatcher` | For suspend functions and Flow/StateFlow-based ViewModels |
| `androidx.test` (`ApplicationProvider`, `AndroidJUnit4`) | Application context access | Used both in Robolectric JVM tests and real `androidTest` instrumented tests |

---

## Shared Test Infrastructure

### `MainDispatcherRule`

Swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope.launch { }` runs synchronously in tests. Use this in any ViewModel test.

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

### `TestDispatcherProvider`

A test double for the app's `DispatcherProvider` interface (`utils/DispatcherProvider.kt`) that routes every dispatcher (`main`, `io`, `default`, `unconfined`) to a single test dispatcher, so coroutine code under test runs deterministically:

```kotlin
class TestDispatcherProvider(private val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
```

Use this (or manually mock `DispatcherProvider` and stub each property to a `TestDispatcher`) for any class that takes `DispatcherProvider` in its constructor — see `RepositoryModule` consumers and most repository implementations.

---

## How to Test Each Layer

### ViewModels

Mock the repository/manager dependencies with MockK, install `MainDispatcherRule`, construct the ViewModel directly (no Hilt needed in the test), and assert on the exposed `StateFlow` value.

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
    fun `loadProgress sets courseProgress value correctly`() = runTest {
        val courseId = "id"
        val userId = "userId"
        val user = RealmUser().apply { _id = userId }
        coEvery { userSessionManager.getUserModel() } returns user

        val expected = mockk<CourseProgressData>()
        coEvery { coursesRepository.getCourseProgress(courseId, userId) } returns expected

        viewModel.loadProgress(courseId)

        assertEquals(expected, viewModel.courseProgress.value)
    }

    @Test
    fun `calling loadProgress twice only invokes coursesRepository once`() = runTest {
        // ... same setup ...
        viewModel.loadProgress(courseId)
        viewModel.loadProgress(courseId)
        coVerify(exactly = 1) { coursesRepository.getCourseProgress(courseId, userId) }
    }
}
```

Key things to copy from this pattern:
- Constructor-inject mocks directly; don't use Hilt's test runner for plain ViewModel unit tests.
- Use `coEvery { }` for suspend functions, `every { }` for non-suspend.
- Test idempotency/caching behavior with `coVerify(exactly = N) { }` — this codebase cares about not re-fetching data unnecessarily.
- Test the *initial* state before any action, not just the state after.

### Repositories

Repository tests in this codebase mostly test **pure helper logic inside the repository**, not actual Realm persistence (since `src/test/` doesn't run real Realm — see above). Mock `DatabaseService` with `mockk(relaxed = true)` and the repository's other collaborator repositories the same way.

Reference: `repository/CoursesRepositoryImplTest.kt`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class CoursesRepositoryImplTest {
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    // ... other collaborator repos, also mockk(relaxed = true) ...

    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setup() {
        repository = CoursesRepositoryImpl(databaseService, testDispatcher, progressRepository, /* ... */)
    }

    @Test
    fun testNormalizeText() {
        assertEquals("hello world", repository.normalizeText("HELLO World"))
        assertEquals("cafe", repository.normalizeText("Café"))
    }
}
```

If you need to verify what gets written to Realm without a live database, follow `RealmRepositoryTest.kt`'s approach: mock `Realm` itself (`mockk(relaxed = true)`) and `DatabaseService.createManagedRealmInstance()` to return it, then verify the right methods were called with the right arguments — don't try to assert on actual query results, since the mock won't really filter anything.

```kotlin
@Before
fun setup() {
    databaseService = mockk()
    realm = mockk(relaxed = true)
    every { databaseService.ioDispatcher } returns testDispatcher
    every { databaseService.createManagedRealmInstance() } returns realm
}
```

For benchmark-style repository tests (see `repository/TeamsRepositoryBenchmarkTest.kt`), the same full-mock approach is used, just with more collaborators wired up — useful as a template when a repository under test has many dependencies.

### Realm Models

Model tests construct the `RealmObject` subclass directly with `ClassName()` — no Realm instance needed — set fields, and assert on the model's own logic methods (`isManager()`, `isGuest()`, serialization helpers, etc).

Reference: `model/RealmUserTest.kt`

```kotlin
class RealmUserTest {
    @MockK lateinit var mockRealm: Realm
    @MockK lateinit var mockContext: Context
    private var originalContext: Context? = null

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(Dispatchers.Unconfined)
        MainApplication.applicationScope = CoroutineScope(Dispatchers.Unconfined)
        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } returns Unit
        originalContext = try { MainApplication.context } catch (e: Exception) { null }
        MainApplication.context = mockContext
    }

    @After
    fun tearDown() {
        if (originalContext != null) MainApplication.context = originalContext!!
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testIsManagerWithManagerRole() {
        val user = RealmUser()
        val roles = RealmList<String?>()
        roles.add("manager")
        user.rolesList = roles
        user.userAdmin = false
        assertTrue(user.isManager())
    }
}
```

If the model's companion-object methods touch `MainApplication.context` or `Utilities` (a lot of `insert`/`serialize` companion methods do), mock those statics with `mockkStatic` and restore with `unmockkAll()` in `tearDown()` — copy the setup/teardown block above rather than reinventing it.

### Workers (`CoroutineWorker` / `WorkManager`)

Mock every constructor dependency, mock the static `WorkManager`/`WorkManagerImpl` calls, and verify scheduling/enqueue calls rather than letting WorkManager actually run.

Reference: `services/retry/RetryQueueWorkerTest.kt`

```kotlin
class RetryQueueWorkerTest {
    @MockK(relaxed = true) lateinit var workManagerImpl: androidx.work.impl.WorkManagerImpl
    @MockK(relaxed = true) lateinit var context: MainApplication
    @MockK lateinit var workerParams: WorkerParameters
    @MockK lateinit var retryQueue: RetryQueue
    @MockK lateinit var apiInterface: ApiInterface

    private lateinit var worker: RetryQueueWorker

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        // ... stub other Log levels the same way ...

        every { context.applicationContext } returns context
        mockkStatic(androidx.work.impl.WorkManagerImpl::class)
        every { androidx.work.impl.WorkManagerImpl.getInstance(any()) } returns workManagerImpl
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl

        worker = RetryQueueWorker(context, workerParams, retryQueue, apiInterface)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun schedule_enqueuesUniquePeriodicWork() {
        every { workManagerImpl.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) } returns mockk(relaxed = true)
        RetryQueueWorker.schedule(context)
        verify(exactly = 1) {
            workManagerImpl.enqueueUniquePeriodicWork("retryQueueWork", any(), any<PeriodicWorkRequest>())
        }
    }
}
```

`mockkStatic(Log::class)` and stubbing each `Log.d/i/w/e` overload is needed in any test where the class under test logs — otherwise Robolectric/the JVM has no real `android.util.Log` implementation and the test crashes. Copy that block whenever `Log.*` calls are reachable from the code under test.

### Adapters (`RecyclerView.Adapter` / `ListAdapter`)

Use Robolectric (`@RunWith(RobolectricTestRunner::class)`) when the adapter touches real Android view/context behavior. Mock the `Context` and any `AdapterDataObserver` you need to verify against.

Reference: `ui/courses/CoursesAdapterTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = android.app.Application::class)
class CoursesAdapterTest {
    @Mock lateinit var mockContext: Context
    @Mock lateinit var mockObserver: RecyclerView.AdapterDataObserver
    private lateinit var adapter: CoursesAdapter

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        adapter = CoursesAdapter(mockContext, HashMap(), false, false)
        adapter.registerAdapterDataObserver(mockObserver)
    }

    @Test
    fun `test selectAllItems sets all unowned courses and triggers notifyItemRangeChanged`() {
        val courses = listOf(/* ... */)
        adapter.submitList(courses)
        adapter.selectAllItems(true)
        assertEquals(true, adapter.areAllSelected())
        verify(mockObserver, times(1)).onItemRangeChanged(/* ... */)
    }
}
```

Note this specific file uses Mockito (legacy) instead of MockK — it's one of the only 2 files that do. **For a new adapter test, use MockK's equivalents** (`mockk<Context>()`, `verify(exactly = 1) { mockObserver.onItemRangeChanged(...) }`) even though this reference file uses Mockito; match the project-wide convention, not this one outlier file.

### Base/Abstract Classes

You can't instantiate an `abstract class` directly. Create a minimal private test subclass inside the test file that implements only what's required to compile, then test the base class's concrete methods through it.

Reference: `base/BaseRecyclerFragmentTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BaseRecyclerFragmentTest {

    class TestBaseRecyclerFragment : BaseRecyclerFragment<Any>() {
        override fun getLayout(): Int = 0
        override suspend fun getAdapter(): androidx.recyclerview.widget.ListAdapter<*, *> {
            throw NotImplementedError()
        }
    }

    @Test
    fun showNoData_withZeroCount_makesViewVisibleAndSetsMessage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textView = TextView(context)
        BaseRecyclerFragment.showNoData(textView, 0, "courses")
        assertEquals(View.VISIBLE, textView.visibility)
        assertEquals(context.getString(R.string.no_courses), textView.text.toString())
    }
}
```

Real `string.xml` resources are used for assertions (`context.getString(R.string.no_courses)`) rather than hardcoded literal strings — this is the standard approach when Robolectric gives you a real `Context`, since it keeps the test correct even if the string copy changes later. Prefer this over hardcoding the expected string.

### Plain Utility Functions

If the utility is pure Kotlin with no Android dependency, a plain JUnit test with no `@RunWith` annotation is enough. If it touches `Context`, `SharedPreferences`, or other Android framework classes, add `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [...], application = ...)`.

Reference: `utils/ConstantsTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ConstantsTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }
    // ...
}
```

---

## Naming Conventions

Both styles below appear throughout the suite (roughly 56 backtick-style files vs 111 camelCase-style files, including mixed files) — neither is enforced, pick whichever reads more clearly for the case you're testing. When adding tests to an existing file, match that file's existing style rather than mixing both within one file.

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

Test class names always match `{ClassUnderTest}Test.kt` (e.g. `CoursesRepositoryImplTest.kt` for `CoursesRepositoryImpl`). Don't append `UnitTest`, `Tests` (plural), or `Spec`.

---

## Running Tests

```bash
# Run all unit tests (what CI runs)
./gradlew testDefaultDebugUnitTest

# Run a single test class
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CourseProgressViewModelTest"

# Run a single test method
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CourseProgressViewModelTest.loadProgress sets courseProgress value correctly"

# Run instrumented tests (needs a connected device/emulator — not used in CI)
./gradlew connectedDefaultDebugAndroidTest
```

CI runs unit tests via `.github/workflows/test.yml` on every push to every branch. There is no separate lint-only or coverage-gate step — passing `testDefaultDebugUnitTest` is the bar.

---

## Patterns Worth Copying, by Example File

Use this table to find the closest existing test to copy from when you're about to write a new one.

| You're testing a... | Copy the shape of | Key technique |
|---|---|---|
| ViewModel with `StateFlow` | `ui/courses/CourseProgressViewModelTest.kt` | `MainDispatcherRule` + `mockk()` + `coEvery`/`coVerify` |
| Repository helper logic | `repository/CoursesRepositoryImplTest.kt` | `mockk(relaxed = true)` for every collaborator, test pure functions |
| Repository's Realm-facing calls | `repository/RealmRepositoryTest.kt` | Mock `Realm` itself + `DatabaseService.createManagedRealmInstance()` |
| Repository with many dependencies | `repository/TeamsRepositoryBenchmarkTest.kt` | Same full-mock pattern, more wiring |
| Realm model with role/permission logic | `model/RealmUserTest.kt` | Plain `RealmObject` construction, `mockkStatic` for companion-object side effects |
| `CoroutineWorker` / WorkManager scheduling | `services/retry/RetryQueueWorkerTest.kt` | `mockkStatic(WorkManager::class)`, mock `Log.*` |
| `RecyclerView`/`ListAdapter` | `ui/courses/CoursesAdapterTest.kt` | `RobolectricTestRunner`, mock `Context` (use MockK, not Mockito) |
| Abstract base class | `base/BaseRecyclerFragmentTest.kt` | Minimal private test subclass implementing only the abstract members |
| Pure Kotlin utility, no Android deps | `utils/TimeUtilsTest.kt`, `utils/JsonUtilsTest.kt` | Plain JUnit, no `@RunWith` |
| Utility touching `Context`/`SharedPreferences` | `utils/ConstantsTest.kt` | `RobolectricTestRunner` + `ApplicationProvider.getApplicationContext()` |
| Real Realm query behavior (rare) | `androidTest/.../DatabaseServiceTest.kt` | `Realm.init()` + in-memory `RealmConfiguration`, only when you genuinely need it |

---

## Things to Avoid

**Don't put a new test in `androidTest/` by default.** It won't run in CI. Use `src/test/` unless you specifically need a real, queryable Realm instance.

**Don't introduce new Mockito usage.** Two legacy files use it; the rest of the suite (~100 files) uses MockK. Use MockK for anything new, including adapter tests, even though the one existing adapter test reference uses Mockito.

**Don't forget `mockkStatic(Log::class)` (and stub each level) when the code under test logs.** Otherwise the test will crash trying to call into the real Android `Log` class, which doesn't exist on the JVM.

**Don't forget to call `unmockkAll()` in `tearDown()`** after any `mockkStatic`/`mockkObject` call, or static mocks will leak into other tests run in the same JVM process.

**Don't try to assert real query results against a mocked `Realm`.** A `mockk(relaxed = true)` Realm doesn't filter anything — if you need to confirm a query actually returns the right rows, that belongs in `androidTest/` with a real in-memory Realm, not in `src/test/`.

**Don't hardcode expected UI strings when Robolectric gives you a real `Context`.** Use `context.getString(R.string.my_string)` in the assertion, the same as `BaseRecyclerFragmentTest` does, so the test doesn't silently drift from the actual string resource.

**Don't skip `MainDispatcherRule` on a ViewModel test that calls `viewModelScope.launch`.** Without it, the coroutine won't run synchronously and your assertion will check the state before the launch block has executed.

**Don't create a third `MainDispatcherRule`.** Two already exist (`org.ole.planet.myplanet.MainDispatcherRule` and `org.ole.planet.myplanet.utils.MainDispatcherRule`); import the `utils` one for new tests.

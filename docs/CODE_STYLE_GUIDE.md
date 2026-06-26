# myPlanet Code Style Guide

This guide documents the conventions actually used in the myPlanet codebase. AI agents and contributors should follow these when writing or modifying code. When in doubt, look at a nearby file in the same package and match it.

---

## Table of Contents

1. [Kotlin Conventions](#kotlin-conventions)
2. [Naming Conventions](#naming-conventions)
3. [File & Package Structure](#file--package-structure)
4. [Architecture Patterns](#architecture-patterns)
5. [Coroutines & Async](#coroutines--async)
6. [Realm Database](#realm-database)
7. [Dependency Injection (Hilt)](#dependency-injection-hilt)
8. [UI Layer](#ui-layer)
9. [Resource Files](#resource-files)
10. [Logging](#logging)
11. [Error Handling](#error-handling)
12. [Branch & PR Standards](#branch--pr-standards)
13. [Things to Avoid](#things-to-avoid)

---

## Kotlin Conventions

### General

- **Kotlin-first.** All new files are `.kt`. Java interop exists in a few legacy spots — don't add more.
- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) as the baseline. This guide narrows them down to what the codebase actually does.
- **4-space indent.** No tabs.
- Max line length: ~120 chars (not strictly enforced, but keep it readable).
- One blank line between top-level declarations; use judgement within functions.

### Null Safety

Prefer non-null types. Use `?` only when null is genuinely meaningful. Avoid the `!!` operator — if you find yourself writing it, restructure with `?.let`, early returns, or elvis `?:`.

```kotlin
// Preferred
val userId = userModel?.id ?: return
val course = courseId?.let { coursesRepository.getCourseById(it) } ?: return

// Avoid
val course = coursesRepository.getCourseById(courseId!!)
```

### Lambdas and Functional Style

Use trailing lambda syntax. Prefer `filter`, `map`, `forEach` over indexed loops when operating on collections. For named-parameter lambdas where the parameter is unused, use `_`.

```kotlin
// Good
realm.where(RealmMyCourse::class.java)
    .isNotEmpty("courseTitle")
    .findAll()

// Good - unused lambda params named _
builder.setPositiveButton(getString(R.string.ok)) { _, _ -> doSomething() }
```

### Data Classes and Sealed Classes

Use `data class` for DTOs and UI state objects. Use `sealed class` (or `sealed interface`) for state machines and discriminated unions.

```kotlin
// UI state - data class with sensible defaults
data class CoursesUiState(
    val courses: List<Course> = emptyList(),
    val map: HashMap<String?, JsonObject> = HashMap(),
    val progressMap: HashMap<String?, JsonObject>? = null,
    val tagsMap: Map<String, List<Tag>> = emptyMap()
)

// Status/result types - sealed class
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Failed(val message: String?) : SyncStatus()
}
```

### `when` Expressions

Prefer `when` over `if-else if` chains with more than two branches. Use it as an expression when assigning.

```kotlin
when (view.id) {
    R.id.next_step -> handleNext()
    R.id.previous_step -> handlePrevious()
    R.id.finish_step -> checkSurveyCompletion()
    R.id.btn_remove -> addRemoveCourse()
}
```

### Object Declarations

Use `object` for singletons and utility holders. The codebase uses this extensively for utilities (`DialogUtils`, `JsonUtils`, `Constants`, `Utilities`). Don't create stateless classes where `object` would do.

```kotlin
object DialogUtils {
    @JvmStatic
    fun getProgressDialog(context: Context): CustomProgressDialog { ... }
}
```

### `@JvmStatic` and Java Interop

Use `@JvmStatic` on companion object members that are called from Java code or in utility objects that may be referenced from older patterns. Don't add it gratuitously in purely Kotlin code paths.

### Extension Functions

Use extension functions for one-off helpers on standard or framework types. Keep them in the `utils/` package and name the file after what they extend (e.g. `ViewExtensions.kt`, `TextViewExtensions.kt`, `FlowExtensions.kt`).

---

## Naming Conventions

### Casing

| Kind | Convention | Example |
|------|-----------|---------|
| Classes, interfaces, objects | PascalCase | `CoursesRepository`, `SyncStatus` |
| Functions | camelCase | `getCourseById`, `checkSurveyCompletion` |
| Properties and local vars | camelCase | `courseId`, `currentStep` |
| Constants (top-level or companion) | UPPER_SNAKE_CASE | `TAG`, `BATCH_SIZE`, `KEY_LOGIN` |
| Layout IDs (XML `android:id`) | snake_case | `@+id/next_step`, `@+id/btn_remove` |
| Resource file names | snake_case | `fragment_take_course.xml`, `row_course.xml` |

### Specific Patterns

**Backing properties for binding:**
```kotlin
private var _binding: FragmentTakeCourseBinding? = null
private val binding get() = _binding!!
```
Always null `_binding` in `onDestroyView()`.

**Backing StateFlow:**
```kotlin
private val _coursesState = MutableStateFlow(CoursesUiState())
val coursesState: StateFlow<CoursesUiState> = _coursesState
```
Private mutable, public read-only — every time, no exceptions.

**TAG in companion object:**
```kotlin
companion object {
    private const val TAG = "ClassName"
}
```

**`newInstance` factory method on Fragments:**
```kotlin
companion object {
    @JvmStatic
    fun newInstance(b: Bundle?): MyFragment {
        val fragment = MyFragment()
        fragment.arguments = b
        return fragment
    }
}
```

---

## File & Package Structure

### Kotlin File Names

| Component | Suffix | Example |
|-----------|--------|---------|
| Activity | `Activity` | `LoginActivity.kt` |
| Fragment | `Fragment` | `TakeCourseFragment.kt` |
| ViewModel | `ViewModel` | `CoursesViewModel.kt` |
| Adapter | `Adapter` | `CoursesAdapter.kt` |
| Repository interface | `Repository` | `CoursesRepository.kt` |
| Repository implementation | `RepositoryImpl` | `CoursesRepositoryImpl.kt` |
| Realm model | `Realm` prefix | `RealmMyCourse.kt` |
| Worker | `Worker` | `AutoSyncWorker.kt` |
| Callback interface | `On` prefix | `OnCourseItemSelectedListener.kt` |
| DI module | `Module` suffix | `RepositoryModule.kt` |
| DI entry point | `EntryPoint` suffix | `NetworkDependenciesEntryPoint.kt` |

### Package Layout

New features belong in `ui/<featurename>/`. Don't dump things in `utils/` or `base/` unless they're genuinely reusable across multiple features.

```
ui/
└── myfeature/
    ├── MyFeatureFragment.kt
    ├── MyFeatureAdapter.kt
    └── MyFeatureViewModel.kt       # only if needed
```

If a feature needs a new data type:
- Realm-backed persistent model → `model/Realm*.kt`
- Non-persistent DTO or UI model → `model/MyDto.kt`
- New data domain → `repository/MyRepository.kt` + `repository/MyRepositoryImpl.kt`

---

## Architecture Patterns

### Layered Architecture

```
UI Layer      (Fragment/Activity + ViewModel)
    ↓  inject via Hilt
Repository Layer  (interface + Impl extending RealmRepository)
    ↓
Data Sources  (Realm local DB via DatabaseService, REST via ApiInterface)
```

Never skip a layer. A Fragment should never directly query Realm. A Repository should never know about Views or Context (except `@ApplicationContext`).

### Repository Pattern

Every data domain has an interface and a `RealmRepository`-extending implementation. The interface is what gets injected everywhere.

```kotlin
// Interface in repository/
interface CoursesRepository {
    suspend fun getCourseById(courseId: String): RealmMyCourse?
    suspend fun joinCourse(courseId: String, userId: String): Result<Unit>
    // ...
}

// Implementation in repository/
class CoursesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val sharedPrefManager: SharedPrefManager
) : RealmRepository(databaseService, realmDispatcher), CoursesRepository {
    override suspend fun getCourseById(courseId: String): RealmMyCourse? {
        return withRealm { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }
}
```

**Rule:** Always `realm.copyFromRealm(...)` before returning objects from a `withRealm { }` block. Realm objects are thread-bound; returning a live managed object causes crashes outside the Realm thread.

### ViewModel Pattern

ViewModels use `@HiltViewModel` and expose state via `StateFlow`, never `LiveData` (the codebase has migrated to Flow).

```kotlin
@HiltViewModel
class MyFeatureViewModel @Inject constructor(
    private val myRepository: MyRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    fun loadData() {
        viewModelScope.launch {
            val data = withContext(dispatcherProvider.io) {
                myRepository.getData()
            }
            _uiState.value = _uiState.value.copy(items = data)
        }
    }
}
```

Collect StateFlow in the Fragment using `viewLifecycleOwner.lifecycleScope` and `repeatOnLifecycle` or `collectLatest`.

### Registering a New Repository

After creating the interface and impl, register the binding in `di/RepositoryModule.kt`:

```kotlin
@Binds
@Singleton
abstract fun bindMyRepository(impl: MyRepositoryImpl): MyRepository
```

---

## Coroutines & Async

### Dispatcher Discipline

Never hardcode `Dispatchers.IO` or `Dispatchers.Main`. Inject `DispatcherProvider` and use its properties:

```kotlin
class MyRepositoryImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun doWork() = withContext(dispatcherProvider.io) { ... }
}
```

Realm operations use `@RealmDispatcher` — a dedicated single-threaded dispatcher to keep all Realm access on one thread.

### Fragment Scope

Always use `viewLifecycleOwner.lifecycleScope` in Fragments, not `lifecycleScope`. The Fragment's `lifecycleScope` survives `onDestroyView()`, which can cause view binding NPEs.

```kotlin
// Good
viewLifecycleOwner.lifecycleScope.launch { ... }

// BAD - can outlive the view
lifecycleScope.launch { ... }
```

### Cancellation

Clean up coroutine jobs in `onDestroyView()`:

```kotlin
override fun onDestroyView() {
    lifecycleScope.coroutineContext.cancelChildren()
    _binding = null
    super.onDestroyView()
}
```

Hold references to cancellable jobs when needed:

```kotlin
private var loadJob: Job? = null

fun reload() {
    loadJob?.cancel()
    loadJob = viewLifecycleOwner.lifecycleScope.launch { ... }
}
```

### Result Wrapping

Use `Result<T>` from the Kotlin stdlib for operations that can fail and need to propagate the failure to the caller:

```kotlin
suspend fun joinCourse(courseId: String, userId: String): Result<Unit> {
    return try {
        // ...
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Caller
result.onSuccess { /* update UI */ }
      .onFailure { e -> Utilities.toast(activity, "Failed: ${e.message}") }
```

For internal-only network calls use `NetworkResult<T>` (the sealed class in `data/NetworkResult.kt`).

---

## Realm Database

### Model Classes

- Must extend `RealmObject`.
- Must be `open class` — Realm generates a proxy subclass.
- All properties must be `var` — Realm needs to intercept get/set.
- Primary keys use `@PrimaryKey`. Indexed fields for frequent queries use `@Index`.
- Non-persisted fields use `@Transient`.

```kotlin
open class RealmMyCourse : RealmObject() {
    @PrimaryKey
    var id: String? = null

    @Index
    var courseId: String? = null

    var courseTitle: String? = null
    var createdDate: Long = 0

    @Transient
    var isMyCourse: Boolean = false
}
```

### Static Helpers in Companion

Put `insert`/`serialize`/`from` factory methods in the model's `companion object`, not in the repository. This keeps serialization logic co-located with the schema.

```kotlin
companion object {
    @JvmStatic
    fun serialize(course: RealmMyCourse, realm: Realm): JsonObject { ... }
}
```

### Writing Data

Always use `executeTransaction` or `executeTransactionAsync` for writes. Never modify a managed Realm object outside a transaction.

```kotlin
// Synchronous write (on Realm thread via withRealm)
realm.executeTransaction { r ->
    r.copyToRealmOrUpdate(myObject)
}

// For bulk inserts during sync, use the bulkInsertFromSync pattern
fun bulkInsertFromSync(realm: Realm, jsonArray: JsonArray) {
    realm.executeTransaction { r ->
        jsonArray.forEach { element ->
            insertOrUpdate(r, element.asJsonObject)
        }
    }
}
```

### Reading Data

Read inside `withRealm { }` and always `copyFromRealm` before returning:

```kotlin
override suspend fun getCourseById(courseId: String): RealmMyCourse? {
    return withRealm { realm ->
        realm.where(RealmMyCourse::class.java)
            .equalTo("courseId", courseId)
            .findFirst()
            ?.let { realm.copyFromRealm(it) }  // REQUIRED
    }
}
```

For reactive/live queries, use `Flow`-returning methods from `RealmRepository`:

```kotlin
override suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>> {
    return queryListFlow(RealmMyCourse::class.java) {
        equalTo("userId", userId)
    }
}
```

### Schema Migrations

All Realm schema changes must have a corresponding migration in `data/RealmMigrations.kt`. Increment `SCHEMA_VERSION` in `DatabaseModule.kt`. Never use `deleteRealmIfMigrationNeeded()` in production builds — that's only for throwaway dev setups.

---

## Dependency Injection (Hilt)

### Annotations

| Annotation | Where |
|-----------|-------|
| `@HiltAndroidApp` | `MainApplication` only |
| `@AndroidEntryPoint` | Every Activity and Fragment that uses `@Inject` |
| `@HiltViewModel` | Every ViewModel |
| `@Inject` | Constructor injection (preferred) or field injection |
| `@Singleton` | Repositories, managers — things with a single app-wide instance |

### Constructor vs Field Injection

Prefer constructor injection for repositories, services, and ViewModels. Use field injection (`@Inject lateinit var`) in Fragments and Activities where constructor injection isn't available.

```kotlin
// Repository - constructor injection
class CoursesRepositoryImpl @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) : CoursesRepository { ... }

// Fragment - field injection
@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>() {
    @Inject lateinit var userSessionManager: UserSessionManager
    @Inject lateinit var dispatcherProvider: DispatcherProvider
}
```

### Workers

Workers can't use constructor injection. Use entry points:

```kotlin
class MyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CoreDependenciesEntryPoint::class.java
        )
        val repo = entryPoint.someRepository()
        // ...
    }
}
```

If you need a dependency not in an existing entry point, add it — don't try to manually instantiate things.

---

## UI Layer

### View Binding — Always, No `findViewById`

```kotlin
// Activity
class MyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}

// Fragment
class MyFragment : Fragment() {
    private var _binding: FragmentMyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### Adapter Pattern — `ListAdapter` with `DiffUtil`

New adapters extend `ListAdapter`, not `RecyclerView.Adapter`. Use `DiffUtils.itemCallback<T>()` for the differ.

```kotlin
class MyAdapter : ListAdapter<MyItem, MyAdapter.ViewHolder>(
    DiffUtils.itemCallback<MyItem>(
        areItemsTheSame = { old, new -> old.id == new.id },
        areContentsTheSame = { old, new -> old == new }
    )
) {
    // Partial-update payloads go in companion object
    companion object {
        private const val SELECTION_PAYLOAD = "payload_selection"
    }
}
```

### Toasts and Snackbars

Use `Utilities.toast(activity, message)` for toasts — not `Toast.makeText(...)` directly. This null-checks the activity for you. Use Material `Snackbar` for undoable actions.

### Dialogs

Build dialogs using `MaterialAlertDialogBuilder` (Material 3). Use `DialogUtils` helpers for common patterns (progress dialogs, confirmation dialogs). Title and message strings must come from string resources.

### Strings in UI

Never hardcode user-facing text in Kotlin. Use `getString(R.string.my_string)` or `context.getString(...)`. The one exception is log messages, which can be inline strings.

---

## Resource Files

### Layout Naming

| Layout type | Prefix | Example |
|------------|--------|---------|
| Activity | `activity_` | `activity_login.xml` |
| Fragment | `fragment_` | `fragment_take_course.xml` |
| RecyclerView row | `row_` | `row_course.xml` |
| Dialog | `dialog_` | `dialog_progress.xml` |
| Alert/dialog (legacy) | `alert_` | `alert_task.xml` |
| Item (alternative to row) | `item_` | `item_tag.xml` |

### View ID Naming

Use `snake_case`. Prefix with an abbreviated type hint for clarity: `tv_` for TextView, `btn_` for Button, `iv_` for ImageView, `rv_` for RecyclerView, `et_` for EditText.

```xml
<TextView android:id="@+id/tv_course_title" />
<Button android:id="@+id/btn_remove" />
<RecyclerView android:id="@+id/recycler" />
```

### String Resources

- All strings in `res/values/strings.xml`.
- Use `snake_case` for the name attribute.
- When adding a new string, add a placeholder in **all 5 translation files** too (`values-ar`, `values-es`, `values-fr`, `values-ne`, `values-so`). Even if you don't know the translation, copy the English string so the build doesn't fail — Crowdin will sync the real translation later.

### Colors and Dimensions

Reference color resources via `ContextCompat.getColor(context, R.color.my_color)`. Reference dimension resources via `resources.getDimensionPixelSize(R.dimen.standard_padding)`. Don't hardcode hex values or dp values in Kotlin.

---

## Logging

Use `Log.d`, `Log.e`, etc. with a `TAG` constant defined in the companion object. Don't use `println` or `System.out`.

```kotlin
companion object {
    private const val TAG = "CoursesRepositoryImpl"
}

// In code
Log.d(TAG, "Loading courses for user: $userId")
Log.e(TAG, "Failed to sync courses", exception)
```

Remove debug logs before merging — or use `if (BuildConfig.DEBUG)` guards if you want to keep them.

---

## Error Handling

### Network Calls

```kotlin
try {
    val response = apiInterface.getCourses()
    if (response.isSuccessful) {
        response.body()?.let { processData(it) }
    } else {
        Log.e(TAG, "API error: ${response.code()} ${response.message()}")
    }
} catch (e: IOException) {
    Log.e(TAG, "Network error", e)
} catch (e: Exception) {
    Log.e(TAG, "Unexpected error", e)
}
```

### Realm Writes

```kotlin
try {
    realm.executeTransaction { r -> r.copyToRealmOrUpdate(obj) }
} catch (e: RealmException) {
    Log.e(TAG, "Realm write failed", e)
}
```

### Coroutine Cancellation

Don't catch `CancellationException` — or if you must, rethrow it. Swallowing it breaks structured concurrency.

```kotlin
try {
    doSuspendWork()
} catch (e: CancellationException) {
    throw e   // must rethrow
} catch (e: Exception) {
    handleError(e)
}
```

---

## Branch & PR Standards

### Branch Naming

Branches must follow the `{prefix}/{issue-number}-{short-description}` pattern:

| Contributor type | Prefix | Example |
|-----------------|--------|---------|
| AI agent (Claude) | `claude/` | `claude/13752-unable-to-complete-certain-courses` |
| AI agent (Jules) | `jules/` | `jules/13530-move-nav-buttons-below-content` |
| AI agent (Codex) | `codex/` | `codex/13559-archive-and-remove-course-actions` |
| Human contributor | `{number}-{description}` | `13755-add-ability-to-edit-meetups` |

The description slug is kebab-case, derived from the GitHub issue title. Match it as closely as possible.

**Push flag:** Always use `-u` on the first push: `git push -u origin <branch-name>`.

### Commit Messages

Use the imperative mood, present tense. Reference the issue number. Keep the first line under 72 chars.

```
fix: return to step 1 after exam completion

Tracks lastPositionBeforeExam in TakeCourseFragment so that
setNavigationButtons() correctly restores position after exam.

Fixes #13752
```

Common type prefixes: `fix:`, `feat:`, `refactor:`, `chore:`, `docs:`, `style:`.

Don't combine unrelated changes in a single commit.

### PR Checklist

Before opening a PR:

- [ ] Branch builds cleanly: `./gradlew assembleDefaultDebug`
- [ ] Tested manually on a physical device or emulator
- [ ] Offline mode still works (no network assumption introduced)
- [ ] All user-facing strings are in `strings.xml` (not hardcoded)
- [ ] New strings added to all 5 translation files
- [ ] Dark theme renders correctly (no hardcoded colors)
- [ ] No debug-only `Log.d` calls left in without a `BuildConfig.DEBUG` guard
- [ ] `_binding` is nulled in `onDestroyView()` if you added a new Fragment
- [ ] `realm.copyFromRealm(...)` used before returning Realm objects

---

## Things to Avoid

These are real patterns that have caused bugs in this codebase before.

**Don't return managed Realm objects.** Always `copyFromRealm` before the object leaves the Realm thread.

**Don't use `!!` unless you are absolutely certain the value cannot be null.** The codebase has had NPE crashes from this.

**Don't hardcode course IDs or server URLs.** There is one hardcoded course ID (`4e6b78800b6ad18b4e8b0e1e38a98cac`) in `TakeCourseFragment` — that's a known tech debt, not a pattern to follow.

**Don't write to Realm outside a transaction.** Realm throws at runtime.

**Don't call `realm.close()` inside a `withRealm { }` block.** The `RealmRepository` base class manages Realm lifecycle.

**Don't use `lifecycleScope` in Fragments for view-touching work.** Use `viewLifecycleOwner.lifecycleScope`.

**Don't create a new `Realm` instance directly.** Go through `DatabaseService` or the `RealmRepository` helpers. Mismatched Realm instances cause thread violations.

**Don't add new dependencies without updating `gradle/libs.versions.toml`.** All versions are centralized there. Don't put a version directly in `app/build.gradle`.

**Don't add a new Activity without registering it in `AndroidManifest.xml`.**

**Don't add a new repository without binding it in `di/RepositoryModule.kt`.**

**Don't swallow `CancellationException` in coroutines** — it breaks cooperative cancellation.

**Don't block the main thread** with Realm queries, file I/O, or network calls. Use `withContext(dispatcherProvider.io)`.
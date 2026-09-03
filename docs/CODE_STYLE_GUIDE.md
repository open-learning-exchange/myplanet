# myPlanet Code Style Guide

This guide documents the conventions actually used in the myPlanet codebase. AI agents and contributors should follow these when writing or modifying code. When in doubt, look at a nearby file in the same package and match it.

---

## Table of Contents

1. [Kotlin Conventions](#kotlin-conventions)
2. [Naming Conventions](#naming-conventions)
3. [File & Package Structure](#file--package-structure)
4. [Architecture Patterns](#architecture-patterns)
5. [Coroutines & Async](#coroutines--async)
6. [Room Database](#room-database)
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

- **Kotlin-first.** All new files are `.kt` — the codebase is 100% Kotlin, no Java sources remain.
- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) as the baseline. This guide narrows them down to what the codebase actually does.
- **4-space indent.** No tabs.
- Max line length: ~120 chars (not strictly enforced, but keep it readable).
- One blank line between top-level declarations; use judgement within functions.

### Imports — No Inline Fully-Qualified Names

Always import a type and use its simple name. Never reference a class by its fully-qualified name in the middle of code — that hides the file's real dependencies from the import block and makes every usage line harder to read.

```kotlin
// Good
import org.ole.planet.myplanet.utils.JsonUtils

val title = JsonUtils.getString("title", doc)

// BAD - inline fully-qualified name
val title = org.ole.planet.myplanet.utils.JsonUtils.getString("title", doc)
```

**On a name collision, use an import alias** instead of falling back to the fully-qualified name:

```kotlin
import androidx.appcompat.app.AlertDialog
import android.app.AlertDialog as AndroidAlertDialog

val builder = AndroidAlertDialog.Builder(context, R.style.CustomAlertDialog)
```

**The only exception is library `R` classes.** `com.google.android.material.R`, `androidx.media3.ui.R`, etc. cannot be imported without shadowing the app's own `R`, so those stay fully qualified at the usage site.

Keep the import block sorted alphabetically and don't use wildcard imports (`import foo.*`).

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

Use trailing lambda syntax. Prefer `filter`, `map`, `forEach` over indexed loops when operating on collections. For lambdas where a parameter is unused, use `_`.

```kotlin
// Good
val myCourses = courses.filter { it.userId?.contains(userId) == true }
    .sortedBy { it.courseTitle }

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

(Note: Room `@Entity` classes are the exception — they stay `open class` with `var` properties; see [Room Database](#room-database).)

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

### `@JvmStatic`

Use `@JvmStatic` on companion object members in utility objects that follow the existing pattern. Don't add it gratuitously in purely Kotlin code paths.

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
private val _uiState = MutableStateFlow(DashboardUiState())
val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
```
Private mutable, public read-only via `.asStateFlow()` — every time, no exceptions. For one-shot events, use the SharedFlow equivalent:
```kotlin
private val _surveyNavigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
val surveyNavigationEvent: SharedFlow<String> = _surveyNavigationEvent.asSharedFlow()
```

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

| Component | Convention | Example |
|-----------|--------|---------|
| Activity | `*Activity.kt` | `LoginActivity.kt` |
| Fragment | `*Fragment.kt` | `CoursesFragment.kt` |
| ViewModel | `*ViewModel.kt` | `CoursesViewModel.kt` |
| Adapter | `*Adapter.kt` | `CoursesAdapter.kt` |
| Repository interface | `*Repository.kt` | `CoursesRepository.kt` |
| Repository implementation | `*RepositoryImpl.kt` | `CoursesRepositoryImpl.kt` |
| Room entity | plain name in `model/` | `MyCourse.kt`, `Submission.kt`, `UserEntity.kt` |
| Room DAO | `*Dao.kt` in `data/room/dao/` | `RatingDao.kt` (several small DAOs share `LegacyEntityDaos.kt`) |
| Worker | `*Worker.kt` | `AutoSyncWorker.kt` |
| Callback interface | `On` prefix | `OnCourseItemSelectedListener.kt` |
| DI module | `Module` suffix | `RepositoryModule.kt` |
| DI entry point | `EntryPoint` suffix | `CoreDependenciesEntryPoint.kt` |

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
- Persistent model → Room `@Entity` in `model/` + a DAO in `data/room/dao/` (register both in `AppDatabase` and `RoomModule`, bump the DB `version`)
- Non-persistent DTO or UI model → `model/MyDto.kt`
- New data domain → `repository/MyRepository.kt` + `repository/MyRepositoryImpl.kt`

---

## Architecture Patterns

### Layered Architecture

```
UI Layer      (Fragment/Activity + ViewModel)
    ↓  inject via Hilt
Repository Layer  (interface + Impl, injects Room DAOs)
    ↓
Data Sources  (Room DAOs, REST via ApiInterface, SharedPreferences)
```

Never skip a layer. A Fragment should never query a DAO directly. A Repository should never know about Views or Context (except `@ApplicationContext`).

### Repository Pattern

Every data domain has an interface and a plain implementation. **There is no base repository class** — each Impl injects the DAO(s) it needs directly. The interface is what gets injected everywhere.

```kotlin
// Real example — repository/CommunityRepositoryImpl.kt
class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao,
    private val meetupDao: MeetupDao
) : CommunityRepository {

    override suspend fun getAllSorted(): List<Community> {
        return communityDao.getAllSorted()
    }
}
```

Reactive queries return `Flow` from a non-suspend DAO method — named `observe*` or `*Flow` — mapped in the repository:

```kotlin
override suspend fun getMyCoursesFlow(userId: String): Flow<List<MyCourse>> {
    return courseDao.observeAll().map { courses ->
        mapCourses(courses).filter { it.userId?.contains(userId) == true }
    }
}
```

DAO methods return plain (unmanaged) Kotlin objects — there is no thread-bound-object problem and no copy-out step; just return what the DAO gives you.

### ViewModel Pattern

ViewModels use `@HiltViewModel` and expose state via `StateFlow`. **LiveData is fully gone** (zero usages) — don't reintroduce it.

```kotlin
@HiltViewModel
class MyFeatureViewModel @Inject constructor(
    private val myRepository: MyRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

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

### Model Companion Helpers

Models keep `serialize*`/`insert*`/`create*` factory methods in their `companion object` (e.g. `News.createNews(...)`, `MyLibrary.insertMyLibrary(...)`, `UserEntity.serialize()`). The contract post-Room-migration: **these helpers build or serialize unmanaged objects; the caller persists the result via a DAO.** Keep serialization logic co-located with the model, keep persistence in the repository.

---

## Coroutines & Async

### Dispatcher Discipline

Never hardcode `Dispatchers.IO` or `Dispatchers.Main`. Inject `DispatcherProvider` (`utils/DispatcherProvider.kt` — properties `main`, `mainImmediate`, `io`, `default`, `unconfined`; provided by `DispatcherModule`) and use its properties:

```kotlin
// Real example — repository/DownloadRepositoryImpl.kt
class DownloadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider
) : DownloadRepository {
    override suspend fun downloadFileResponse(url: String, authHeader: String): DownloadResult =
        withContext(dispatcherProvider.io) { ... }
}
```

This is what lets tests substitute `TestDispatcherProvider` (see `docs/TESTING.md`).

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

For internal-only network calls use `NetworkResult<T>` (the sealed class in `data/NetworkResult.kt`: `Success` / `Error(code, message)` / `Exception(throwable)`).

---

## Room Database

All local persistence goes through Room: `AppDatabase` (`data/room/AppDatabase.kt`, 37 entities, `version = 6`), DAOs in `data/room/dao/`, and `Converters` (`data/room/Converters.kt`). There is no other local store.

### Entity Classes

Entities live in `model/` with plain names (no prefix). They keep the Realm-era shape — **`open class` with `var` properties and default values**, not `data class`:

```kotlin
@Entity(tableName = "courses", indices = [Index("courseId"), Index("_id"), Index("courseTitleNormal")])
open class MyCourse(
    @PrimaryKey @JvmField var id: String = "",
    var userId: List<String>? = null,
    @JvmField @ColumnInfo(name = "_id") var _id: String? = null,
    @ColumnInfo(name = "_rev") var courseRev: String? = null,
    var courseTitle: String? = null,
    // ...
) {
    @Ignore var courseSteps: MutableList<CourseStep>? = null   // non-persisted helper
}
```

- `@PrimaryKey` for the key; `indices = [Index(...)]` on `@Entity` for frequently queried columns.
- `@ColumnInfo(name = "_id")` / `"_rev"` map the CouchDB field names to Kotlin-friendly property names.
- Non-persisted, computed, or in-memory-only fields use `@Ignore` (often combined with `@Transient`).
- Multi-valued fields (`List<String>`, nested lists, `Date`) persist via `Converters` — Gson-serialized JSON strings, using the shared `JsonUtils.gson`.

### DAOs

DAO methods are `suspend` (except `Flow`-returning `observe*`/`*Flow` methods, which Room requires to be non-suspend). Upserts use `@Upsert` or `@Insert(onConflict = OnConflictStrategy.REPLACE)`.

```kotlin
// data/room/dao/RatingDao.kt
@Dao
interface RatingDao {
    // IS for nullable params (matches NULL rows), = for non-null params
    @Query("SELECT * FROM rating WHERE type IS :type AND item IS :item")
    suspend fun getByTypeAndItem(type: String?, item: String?): List<Rating>

    @Query("SELECT * FROM rating WHERE type = :type AND userId = :userId AND item = :item LIMIT 1")
    suspend fun findByTypeUserItem(type: String, userId: String, item: String): Rating?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Rating>)
}
```

**`IS` vs `=` matters:** in SQL, `= :param` never matches `NULL`. Use `IS :param` whenever a nullable argument should match `NULL` rows.

### Writing Data

Persist through DAOs. For an atomic multi-DAO write (e.g. bulk sync inserts), use Room's `withTransaction` on the `AppDatabase`:

```kotlin
appDatabase.withTransaction {
    courseDao.upsertAll(courses)
    courseStepDao.upsertAll(steps)
}
```

(`DatabaseService` still exists but is vestigial — no repository injects it anymore; don't build new code on it.)

### Schema Changes — Drop-and-Resync, No Migrations

`RoomModule` builds the database with `fallbackToDestructiveMigration(true)`. There are **no hand-written `Migration` objects and there should be none**: on any entity/schema change, bump `version` in `AppDatabase` — the local DB is dropped and repopulated from the Planet/CouchDB server on next launch. Don't write `Migration` classes and don't forget the version bump (Room crashes with "cannot verify the data integrity" if the schema changed without one).

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
class CoursesFragment : BaseRecyclerFragment<MyCourse?>() {
    @Inject lateinit var userSessionManager: UserSessionManager
    @Inject lateinit var dispatcherProvider: DispatcherProvider
}
```

### Workers

Workers can't use constructor injection. Use entry points — the two that exist are `CoreDependenciesEntryPoint` and `ServiceDependenciesEntryPoint` (both in `di/`):

```kotlin
class MyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CoreDependenciesEntryPoint::class.java
        )
        val repo = entryPoint.resourcesRepository()
        // ...
    }
}
```

If you need a dependency not in an existing entry point, add it there — don't manually instantiate things. (`apiInterface()` lives on `ServiceDependenciesEntryPoint`.)

### Existing Qualifiers

`@StandardHttpClient` / `@StandardRetrofit` (NetworkModule), `@ApplicationScope` (ServiceModule), `@AppPreferences` / `@DefaultPreferences` / `@DownloadPreferences` (SharedPreferencesModule). There are no dispatcher qualifiers — dispatchers come from the unqualified `DispatcherProvider`.

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

New adapters extend `ListAdapter`, not `RecyclerView.Adapter`. Use the helpers in `utils/DiffUtils.kt` — `itemCallback` for the general case, `standardItemCallback(idSelector, contentSelector, payloadSelector)` as the preferred shorthand.

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

Use `Utilities.toast(context, message)` for toasts — not `Toast.makeText(...)` directly (it null-checks the context and has a `duration` default). Use Material `Snackbar` for undoable actions.

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
```

### String Resources

- All strings in `res/values/strings.xml`.
- Use `snake_case` for the name attribute.
- When adding a new string, add a placeholder in **all 5 translation files** too (`values-ar`, `values-es`, `values-fr`, `values-ne`, `values-so`). Even if you don't know the translation, copy the English string so the build doesn't fail, and note it in the PR so a translation can follow.

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

### Database Writes

```kotlin
try {
    courseDao.upsertAll(courses)
} catch (e: SQLiteException) {
    Log.e(TAG, "Database write failed", e)
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

Branches follow the `{prefix}/{slug}` pattern, where the slug is kebab-case derived from the issue title (with the issue number when there is one):

| Contributor type | Prefix | Example |
|-----------------|--------|---------|
| AI agent (Claude) | `claude/` | `claude/13752-unable-to-complete-certain-courses` |
| AI agent (Jules) | `jules/` or `jules-` | `jules/13530-move-nav-buttons-below-content` |
| AI agent (Codex) | `codex/` (also `<id>-codex/`) | `codex/13559-archive-and-remove-course-actions` |
| AI agent (Copilot) | `copilot/` | `copilot/fix-notification-badge-count` |
| Human contributor | `{number}-{description}` | `13755-add-ability-to-edit-meetups` |

(The full field guide to summoning AI agents on PRs — reviewers vs doers, incantations, side effects, and the Laws of Summoning — is the `agents-summoning` skill: `.agents/skills/agents-summoning/SKILL.md`, or the `agents-summoning@summoning` plugin in a Claude Code session.)

**Push flag:** Always use `-u` on the first push: `git push -u origin <branch-name>`.

### Commit Messages

History is linear squash-merges; merged commit subjects follow this observed pattern:

```text
<area>: <description> (fixes #<issue>) (#<pr>)
```

e.g. `sync: smoother user repository inserting (fixes #15432) (#15270)` — areas in use include `sync`, `all`, `resources`, `courses`, `teams`, `login`, `dashboard`, `chat`, `life`.

For your working commits: imperative mood, present tense, reference the issue number, first line under 72 chars. Conventional prefixes (`fix:`, `feat:`, `refactor:`, `chore:`, `docs:`) are also fine for intermediate commits. Don't combine unrelated changes in a single commit.

### PR Checklist

Before opening a PR:

- [ ] Branch builds cleanly: `./gradlew assembleDefaultDebug`
- [ ] Unit tests pass: `./gradlew testDefaultDebugUnitTest` (this is the CI gate)
- [ ] Tested manually on a physical device or emulator
- [ ] Offline mode still works (no network assumption introduced)
- [ ] All user-facing strings are in `strings.xml` (not hardcoded)
- [ ] New strings added to all 5 translation files
- [ ] Dark theme renders correctly (no hardcoded colors)
- [ ] No debug-only `Log.d` calls left in without a `BuildConfig.DEBUG` guard
- [ ] `_binding` is nulled in `onDestroyView()` if you added a new Fragment
- [ ] `AppDatabase` `version` bumped if you changed any `@Entity`

### PR Reviews

Besides a human reviewer and a Claude session, several bots can review a PR — the full roster and their side effects are in the `agents-summoning` skill (`.agents/skills/agents-summoning/SKILL.md`). **CodeRabbit** is the default reviewer, but it is opt-in here: label a PR `review` and it reviews every push after that, drafts included. On an unlabelled PR `@coderabbitai review` still summons one by hand. It's the token-cheap shortcut — an incremental automated review without spending a human's time or a Claude session's context.

Useful commands (as PR comments):

```text
@coderabbitai help                    # list all commands
@coderabbitai review                  # incremental review of new changes
@coderabbitai full review             # re-review the whole PR from scratch
@coderabbitai resolve                 # resolve all its open comments
@coderabbitai regenerate summary      # regenerate the PR summary
@coderabbitai fix ci                  # analyze + fix failing CI via a stacked PR
@coderabbitai fix ci commit           # fix failing CI on the current branch
@coderabbitai resolve merge conflict  # resolve merge conflicts automatically
@coderabbitai pause / resume          # pause/resume automatic reviews
```

Min-max rule of thumb: let CodeRabbit do the first mechanical pass (nits, obvious bugs, style drift), then spend human/Claude attention only on what it can't judge — architecture fit, domain correctness, offline/sync behavior.

---

## Things to Avoid

These are real patterns that have caused bugs in this codebase before.

**Don't bypass the DAO layer.** All persistence goes through Room DAOs (or `AppDatabase.withTransaction` for multi-DAO atomicity). No raw SQLite, no third-party store, no new code on the vestigial `DatabaseService`.

**Don't write Room `Migration` objects.** The strategy is drop-and-resync (`fallbackToDestructiveMigration`); bump `AppDatabase.version` instead.

**Don't use `=` in a DAO `@Query` for a nullable parameter.** `= :param` never matches `NULL` rows — use `IS :param`.

**Don't reference types by their fully-qualified name inline.** Import the type (aliased if the name collides) and use the simple name — see [Imports](#imports--no-inline-fully-qualified-names). Library `R` classes are the one exception.

**Don't use `!!` unless you are absolutely certain the value cannot be null.** The codebase has had NPE crashes from this.

**Don't hardcode course IDs or server URLs.** One course ID (`4e6b78800b6ad18b4e8b0e1e38a98cac`) is hardcoded in **4 places** (`TakeCourseFragment` ×2, `MarkdownDialogFragment`, `DashboardViewModel`) — that's known tech debt, not a pattern to follow.

**Don't hardcode `Dispatchers.IO`/`Dispatchers.Main`.** Inject `DispatcherProvider` so tests can substitute deterministic dispatchers.

**Don't use `lifecycleScope` in Fragments for view-touching work.** Use `viewLifecycleOwner.lifecycleScope`.

**Don't reintroduce LiveData.** The codebase is fully on `StateFlow`/`SharedFlow` (zero LiveData usages).

**Don't add new dependencies without updating `gradle/libs.versions.toml`.** All versions are centralized there. Don't put a version directly in `app/build.gradle`.

**Don't add a new Activity without registering it in `AndroidManifest.xml`.**

**Don't add a new repository without binding it in `di/RepositoryModule.kt`.**

**Don't add a new entity/DAO without registering both in `AppDatabase` + `RoomModule` and bumping the DB `version`.**

**Don't swallow `CancellationException` in coroutines** — it breaks cooperative cancellation.

**Don't block the main thread** with database queries, file I/O, or network calls. Use `withContext(dispatcherProvider.io)`.

**Don't add `@Index` to fields storing JSON lists (like `userId`) if queried using `LIKE` with wildcards.** B-tree indices cannot optimize leading-wildcard queries (e.g. `LIKE '%"id"%'`). Adding an index will do nothing for performance but will trigger a database version bump and destructive migration, wiping local unsynced data.

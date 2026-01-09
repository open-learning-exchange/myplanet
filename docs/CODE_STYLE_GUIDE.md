# myPlanet Code Style Guide

**Version**: 1.0
**Last Updated**: 2026-01-09

This guide establishes coding conventions for the myPlanet Android application. All contributions must adhere to these standards.

---

## Table of Contents

1. [Kotlin Conventions](#kotlin-conventions)
2. [File Organization](#file-organization)
3. [Naming Conventions](#naming-conventions)
4. [Architecture Patterns](#architecture-patterns)
5. [Dependency Injection](#dependency-injection)
6. [Realm Database](#realm-database)
7. [Asynchronous Programming](#asynchronous-programming)
8. [Error Handling](#error-handling)
9. [Resource Management](#resource-management)
10. [Anti-Patterns](#anti-patterns)

---

## Kotlin Conventions

### General Principles

Follow the [Official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with these project-specific additions:

```kotlin
// Use expression bodies for simple functions
fun getFullName(): String = "$firstName $lastName"

// Use explicit types for public API
fun calculateProgress(): Float { ... }

// Prefer immutability
val items: List<Item> = listOf()  // Prefer val over var
```

### Null Safety

```kotlin
// CORRECT: Safe calls and elvis operator
val name = user?.name ?: getString(R.string.default_name)

// CORRECT: Smart casts after null check
if (user != null) {
    displayUser(user)  // user is non-null here
}

// AVOID: Non-null assertions (use sparingly)
val name = user!!.name  // Can cause NPE

// CORRECT: Use let for nullable scoping
user?.let { displayUser(it) }
```

### Extension Functions

```kotlin
// Use for readability improvements
fun String.toSafeFileName(): String =
    replace(Regex("[^a-zA-Z0-9.-]"), "_")

// Place in appropriate utility files
// Location: utilities/StringExtensions.kt
```

### Scope Functions

```kotlin
// apply: Configure object properties
val textView = TextView(context).apply {
    text = "Hello"
    textSize = 16f
    setTextColor(color)
}

// let: Transform nullable or scoped values
val length = nullableString?.let { it.length } ?: 0

// with: Multiple operations on same object
with(binding) {
    tvTitle.text = title
    tvDescription.text = description
    ivIcon.setImageResource(iconRes)
}

// run: Execute block and return result
val result = service.run {
    initialize()
    process()
    getResult()
}

// also: Side effects (logging, debugging)
return data.also { Log.d(TAG, "Returning: $it") }
```

---

## File Organization

### Package Structure

```
org.ole.planet.myplanet/
├── base/                    # Base classes (abstract/open)
│   ├── BaseActivity.kt
│   ├── BaseFragment.kt
│   └── BaseRecyclerFragment.kt
├── callback/                # Interfaces and listeners
│   └── OnItemClickListener.kt
├── data/                    # Data layer
│   ├── ApiInterface.kt
│   └── DatabaseService.kt
├── di/                      # Hilt modules
│   ├── NetworkModule.kt
│   └── RepositoryModule.kt
├── model/                   # Realm models
│   └── RealmMyModel.kt
├── repository/              # Repository pattern
│   ├── FeatureRepository.kt
│   └── FeatureRepositoryImpl.kt
├── service/                 # Background services
│   └── sync/
│       └── SyncManager.kt
├── ui/                      # UI components by feature
│   └── feature/
│       ├── FeatureActivity.kt
│       ├── FeatureFragment.kt
│       └── FeatureAdapter.kt
└── utilities/               # Helper functions
    └── NetworkUtils.kt
```

### File Ordering Within Classes

```kotlin
class MyActivity : BaseActivity() {

    // 1. Companion object (constants, factory methods)
    companion object {
        private const val TAG = "MyActivity"
        private const val ARG_ID = "arg_id"

        fun newIntent(context: Context, id: String): Intent {
            return Intent(context, MyActivity::class.java).apply {
                putExtra(ARG_ID, id)
            }
        }
    }

    // 2. Injected dependencies
    @Inject lateinit var repository: FeatureRepository
    @Inject lateinit var sharedPreferences: SharedPreferences

    // 3. View binding
    private lateinit var binding: ActivityMyBinding

    // 4. Instance variables
    private var currentId: String = ""
    private val items = mutableListOf<Item>()

    // 5. Lifecycle methods (in order)
    override fun onCreate(savedInstanceState: Bundle?) { ... }
    override fun onResume() { ... }
    override fun onPause() { ... }
    override fun onDestroy() { ... }

    // 6. Interface implementations
    override fun onItemClick(item: Item) { ... }

    // 7. Public methods
    fun refreshData() { ... }

    // 8. Private methods
    private fun setupUI() { ... }
    private fun loadData() { ... }

    // 9. Inner classes
    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
```

---

## Naming Conventions

### Classes and Files

| Type | Convention | Example |
|------|------------|---------|
| Activity | `*Activity.kt` | `CourseDetailActivity.kt` |
| Fragment | `*Fragment.kt` | `CourseListFragment.kt` |
| Adapter | `*Adapter.kt` | `CourseAdapter.kt` |
| ViewHolder | `*ViewHolder.kt` | `CourseViewHolder.kt` |
| Repository Interface | `*Repository.kt` | `CourseRepository.kt` |
| Repository Implementation | `*RepositoryImpl.kt` | `CourseRepositoryImpl.kt` |
| Realm Model | `Realm*.kt` | `RealmMyCourse.kt` |
| Worker | `*Worker.kt` | `AutoSyncWorker.kt` |
| Utility | `*Utils.kt` | `NetworkUtils.kt` |
| Extensions | `*Extensions.kt` | `StringExtensions.kt` |

### Variables and Functions

```kotlin
// Variables: camelCase
val userName: String
var isLoading: Boolean
private val _items = MutableLiveData<List<Item>>()

// Constants: UPPER_SNAKE_CASE
const val MAX_RETRY_COUNT = 3
const val DEFAULT_TIMEOUT_MS = 30_000L

// Functions: camelCase, verb prefix
fun loadCourses() { ... }
fun calculateProgress(): Float { ... }
fun isUserLoggedIn(): Boolean { ... }

// Boolean getters: is/has/can prefix
fun isOnline(): Boolean
fun hasPermission(): Boolean
fun canEdit(): Boolean
```

### Layout Resources

```xml
<!-- Activities: activity_*.xml -->
activity_course_detail.xml

<!-- Fragments: fragment_*.xml -->
fragment_course_list.xml

<!-- List items: row_*.xml or item_*.xml -->
row_course.xml
item_library.xml

<!-- Dialogs: dialog_*.xml -->
dialog_rating.xml

<!-- Includes: include_*.xml -->
include_toolbar.xml

<!-- Views in layouts: type_description -->
android:id="@+id/tv_course_title"
android:id="@+id/iv_course_thumbnail"
android:id="@+id/btn_enroll"
android:id="@+id/rv_courses"
android:id="@+id/et_search"
```

### View ID Prefixes

| Prefix | View Type |
|--------|-----------|
| `tv_` | TextView |
| `et_` | EditText |
| `btn_` | Button |
| `iv_` | ImageView |
| `rv_` | RecyclerView |
| `sv_` | ScrollView |
| `pb_` | ProgressBar |
| `cb_` | CheckBox |
| `rb_` | RadioButton |
| `fab_` | FloatingActionButton |
| `tl_` | TabLayout |
| `vp_` | ViewPager |
| `cl_` | ConstraintLayout |
| `ll_` | LinearLayout |

---

## Architecture Patterns

### Repository Pattern

Every data domain must have:

1. **Interface** - Defines contract
2. **Implementation** - Provides concrete logic
3. **DI Binding** - Connects interface to implementation

```kotlin
// 1. Interface: repository/CourseRepository.kt
interface CourseRepository {
    suspend fun getCourses(): List<RealmMyCourse>
    suspend fun getCourseById(id: String): RealmMyCourse?
    suspend fun syncCourses(): Result<Unit>
    suspend fun enrollInCourse(courseId: String): Result<Unit>
}

// 2. Implementation: repository/CourseRepositoryImpl.kt
class CourseRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService,
    private val sharedPreferences: SharedPreferences
) : CourseRepository {

    override suspend fun getCourses(): List<RealmMyCourse> {
        return withContext(Dispatchers.IO) {
            databaseService.realm.where(RealmMyCourse::class.java)
                .findAll()
                .toList()
        }
    }

    override suspend fun syncCourses(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiInterface.getCourses()
            if (response.isSuccessful) {
                response.body()?.let { courses ->
                    saveCourses(courses)
                }
                Result.success(Unit)
            } else {
                Result.failure(ApiException("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 3. DI Binding: di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindCourseRepository(
        impl: CourseRepositoryImpl
    ): CourseRepository
}
```

### Base Class Extension

Extend base classes for common functionality:

```kotlin
// For activities with toolbar and navigation
class CourseDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initActionBar()  // From BaseActivity
    }
}

// For list-based fragments
class CourseListFragment : BaseRecyclerFragment<RealmMyCourse>() {
    override fun getAdapter(): RecyclerView.Adapter<*> {
        return CourseAdapter(items, this)
    }

    override fun fetchData() {
        lifecycleScope.launch {
            items.clear()
            items.addAll(repository.getCourses())
            adapter?.notifyDataSetChanged()
        }
    }
}
```

---

## Dependency Injection

### Hilt Setup

```kotlin
// Activities/Fragments: Use @AndroidEntryPoint
@AndroidEntryPoint
class CourseListFragment : BaseRecyclerFragment<RealmMyCourse>() {
    @Inject lateinit var courseRepository: CourseRepository
    @Inject lateinit var sharedPreferences: SharedPreferences
}

// ViewModels: Use @HiltViewModel
@HiltViewModel
class CourseViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {
    // ViewModel implementation
}

// Workers: Use @HiltWorker with EntryPoint
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CourseRepository
) : CoroutineWorker(context, params) {
    // Worker implementation
}
```

### Module Organization

```kotlin
// NetworkModule.kt - HTTP client and API
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient { ... }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit { ... }

    @Provides
    @Singleton
    fun provideApiInterface(retrofit: Retrofit): ApiInterface { ... }
}

// DatabaseModule.kt - Realm database
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideRealmConfiguration(): RealmConfiguration { ... }
}

// RepositoryModule.kt - Repository bindings
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository
    @Binds abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository
}
```

---

## Realm Database

### Model Definition

```kotlin
open class RealmMyCourse : RealmObject() {
    @PrimaryKey
    var _id: String = ""
    var courseTitle: String? = null
    var description: String? = null
    var languageOfInstruction: String? = null
    var createdDate: Long = 0
    var userId: String? = null
    var isEnrolled: Boolean = false

    // Companion object for static operations
    companion object {
        const val TAG = "RealmMyCourse"

        fun insert(realm: Realm, jsonObject: JsonObject): RealmMyCourse {
            val course = realm.createObject(
                RealmMyCourse::class.java,
                jsonObject.get("_id").asString
            )
            course.courseTitle = jsonObject.get("courseTitle")?.asString
            course.description = jsonObject.get("description")?.asString
            course.createdDate = System.currentTimeMillis()
            return course
        }

        fun getCourseById(realm: Realm, id: String): RealmMyCourse? {
            return realm.where(RealmMyCourse::class.java)
                .equalTo("_id", id)
                .findFirst()
        }
    }
}
```

### Transaction Patterns

```kotlin
// CORRECT: Always use transactions for writes
mRealm.executeTransaction { realm ->
    val course = realm.createObject(RealmMyCourse::class.java, id)
    course.courseTitle = title
}

// CORRECT: Async transaction for UI thread
mRealm.executeTransactionAsync(
    { realm ->
        // Write operations
    },
    {
        // Success callback
        onSyncComplete()
    },
    { error ->
        // Error callback
        Log.e(TAG, "Transaction failed", error)
    }
)

// CORRECT: Copy to realm or update existing
mRealm.executeTransaction { realm ->
    realm.copyToRealmOrUpdate(courseObject)
}

// CORRECT: Delete operations
mRealm.executeTransaction { realm ->
    realm.where(RealmMyCourse::class.java)
        .equalTo("_id", courseId)
        .findFirst()
        ?.deleteFromRealm()
}

// CRITICAL: Close realm when done
override fun onDestroy() {
    super.onDestroy()
    if (::mRealm.isInitialized && !mRealm.isClosed) {
        mRealm.close()
    }
}
```

---

## Asynchronous Programming

### Coroutines

```kotlin
// Repository: Use suspend functions with IO dispatcher
override suspend fun fetchData(): List<Item> = withContext(Dispatchers.IO) {
    apiInterface.getData().body() ?: emptyList()
}

// Fragment: Launch in lifecycle scope
viewLifecycleOwner.lifecycleScope.launch {
    try {
        showLoading()
        val data = repository.fetchData()
        updateUI(data)
    } catch (e: Exception) {
        showError(e.message)
    } finally {
        hideLoading()
    }
}

// ViewModel: Launch in viewModelScope
class CourseViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    private val _courses = MutableStateFlow<List<RealmMyCourse>>(emptyList())
    val courses: StateFlow<List<RealmMyCourse>> = _courses.asStateFlow()

    fun loadCourses() {
        viewModelScope.launch {
            _courses.value = repository.getCourses()
        }
    }
}
```

### StateFlow for UI State

```kotlin
// Define sealed class for UI states
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

// Expose as StateFlow
private val _uiState = MutableStateFlow<UiState<List<Course>>>(UiState.Loading)
val uiState: StateFlow<UiState<List<Course>>> = _uiState.asStateFlow()

// Collect with lifecycle awareness
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            when (state) {
                is UiState.Loading -> showLoading()
                is UiState.Success -> displayData(state.data)
                is UiState.Error -> showError(state.message)
            }
        }
    }
}
```

---

## Error Handling

### Network Errors

```kotlin
suspend fun fetchData(): Result<List<Item>> = withContext(Dispatchers.IO) {
    try {
        val response = apiInterface.getData()
        when {
            response.isSuccessful -> {
                Result.success(response.body() ?: emptyList())
            }
            response.code() == 401 -> {
                Result.failure(AuthenticationException("Session expired"))
            }
            response.code() in 500..599 -> {
                Result.failure(ServerException("Server error: ${response.code()}"))
            }
            else -> {
                Result.failure(ApiException("Request failed: ${response.code()}"))
            }
        }
    } catch (e: IOException) {
        Result.failure(NetworkException("Network unavailable", e))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Exception Types

```kotlin
// Define custom exceptions in data/exceptions/
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AuthenticationException(message: String) : Exception(message)
class ServerException(message: String) : Exception(message)
class ApiException(message: String) : Exception(message)
class DatabaseException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

### User Feedback

```kotlin
// Use DialogUtils for consistent error display
fun showError(message: String) {
    DialogUtils.showAlert(
        context = requireContext(),
        title = getString(R.string.error),
        message = message
    )
}

// Use Snackbar for non-blocking feedback
fun showNetworkError() {
    Snackbar.make(
        binding.root,
        R.string.network_error,
        Snackbar.LENGTH_LONG
    ).setAction(R.string.retry) {
        retryOperation()
    }.show()
}
```

---

## Resource Management

### String Resources

```kotlin
// ALWAYS use string resources for user-facing text
binding.tvTitle.text = getString(R.string.course_title)

// With format arguments
getString(R.string.progress_format, current, total)
// strings.xml: <string name="progress_format">%1$d of %2$d completed</string>

// Plurals
resources.getQuantityString(R.plurals.items_count, count, count)
```

### Dimension Resources

```kotlin
// Use dimension resources for consistent spacing
val padding = resources.getDimensionPixelSize(R.dimen.padding_normal)

// Reference in XML
android:padding="@dimen/padding_normal"
android:layout_margin="@dimen/padding_large"
```

### Color Resources

```kotlin
// ALWAYS reference colors through resources
val color = ContextCompat.getColor(context, R.color.colorPrimary)

// Use theme attributes when possible
android:textColor="?attr/colorOnSurface"
android:background="?attr/colorSurface"
```

---

## Anti-Patterns

### Prohibited Practices

```kotlin
// NEVER: Hardcoded strings
binding.tvTitle.text = "Course Title"  // Use R.string

// NEVER: Hardcoded colors
binding.tvTitle.setTextColor(Color.parseColor("#000000"))  // Use R.color

// NEVER: Hardcoded dimensions
binding.card.setPadding(16, 16, 16, 16)  // Use R.dimen

// NEVER: Non-null assertion without check
val name = user!!.name  // Prefer safe call

// NEVER: Blocking calls on main thread
val result = networkCall()  // Use coroutines

// NEVER: Direct style manipulation in code
binding.card.background = ColorDrawable(Color.WHITE)  // Use XML styles

// NEVER: Ignore exceptions silently
try {
    riskyOperation()
} catch (e: Exception) {
    // Don't leave empty
}

// NEVER: Store passwords or tokens in code
const val API_KEY = "abc123"  // Use BuildConfig or secure storage
```

### Preferred Alternatives

```kotlin
// CORRECT: Use resources
binding.tvTitle.text = getString(R.string.course_title)
binding.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary))

// CORRECT: Safe null handling
val name = user?.name ?: getString(R.string.default_name)

// CORRECT: Coroutines for async
lifecycleScope.launch {
    val result = withContext(Dispatchers.IO) { networkCall() }
    updateUI(result)
}

// CORRECT: Log and handle exceptions
try {
    riskyOperation()
} catch (e: Exception) {
    Log.e(TAG, "Operation failed", e)
    showError(e.message ?: getString(R.string.unknown_error))
}

// CORRECT: Use BuildConfig for configuration
val serverUrl = BuildConfig.SERVER_URL
```

---

## Commit Message Format

```
type(scope): brief description

- Detail 1
- Detail 2

Fixes #123
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code restructuring
- `style`: Formatting, no logic change
- `docs`: Documentation only
- `test`: Adding tests
- `chore`: Build, CI, dependencies

**Examples:**
```
feat(courses): add offline course enrollment

- Implement local enrollment storage
- Add sync queue for pending enrollments
- Update CourseRepository interface

Fixes #456
```

---

## References

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- [UI_GUIDELINES.md](UI_GUIDELINES.md) - Material Design implementation
- [CLAUDE.md](../CLAUDE.md) - Project overview and architecture

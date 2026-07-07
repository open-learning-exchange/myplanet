# CLAUDE.md - AI Assistant Guide for myPlanet

## Project Overview

**myPlanet** is an Android mobile application serving as an offline extension of the Open Learning Exchange's Planet Learning Management System. It enables learners to access educational resources (books, videos, courses) without continuous internet connectivity.

### Key Characteristics
- **Primary Language**: Kotlin (100% — no Java sources remain)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16); **Compile SDK**: 37
- **Current Version**: 0.59.69 (versionCode: 5969)
- **Build System**: Gradle 9.5.1 with Android Gradle Plugin 9.2.1
- **License**: AGPL v3

### Build Flavors
- **default**: Full-featured version
- **lite**: Lightweight version with reduced features

---

## Codebase Structure

### Directory Layout

```
myplanet/
├── .github/                    # CI/CD workflows and Dependabot config
│   └── workflows/
│       ├── build.yml          # Build workflow for all branches
│       ├── release.yml        # Release and Play Store publishing
│       └── test.yml           # Unit test workflow
├── app/                       # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/org/ole/planet/myplanet/
│   │   │   │   ├── MainApplication.kt       # App entry point with Hilt
│   │   │   │   ├── base/                    # Base classes for activities/fragments
│   │   │   │   ├── callback/                # Event listeners and interfaces
│   │   │   │   ├── data/                    # Data services and API
│   │   │   │   ├── di/                      # Dependency injection modules
│   │   │   │   ├── model/                   # Realm data models (83 files)
│   │   │   │   ├── repository/              # Repository pattern implementations
│   │   │   │   ├── services/                # Background services and workers
│   │   │   │   ├── ui/                      # UI components (28 packages)
│   │   │   │   └── utils/                   # Helper utilities
│   │   │   ├── res/                         # Android resources
│   │   │   │   ├── layout/                  # 186 layout files
│   │   │   │   ├── values/                  # Strings, colors, styles
│   │   │   │   ├── values-{lang}/           # Translations (ar, es, fr, ne, so)
│   │   │   │   └── drawable*/               # Images and icons
│   │   │   └── AndroidManifest.xml
│   │   └── lite/
│   │       └── AndroidManifest.xml          # Lite variant manifest
│   ├── build.gradle                         # Module build config
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml                   # Centralized dependency versions
├── build.gradle.kts                         # Root build config
├── settings.gradle                          # Project settings
├── gradle.properties                        # Gradle configuration
└── README.md
```

### Package Organization (`org.ole.planet.myplanet`)

| Package | Purpose | Files | Key Items |
|---------|---------|-------|-----------|
| `base/` | Base classes for common functionality | 12 | BaseActivity, BaseRecyclerFragment, BasePermissionActivity, BaseContainerFragment, BaseDashboardFragment, BaseResourceFragment, BaseTeamFragment, BaseExamFragment, BaseMemberFragment, BaseDialogFragment, BaseVoicesFragment, BaseRecyclerParentFragment |
| `callback/` | Event listeners and interfaces | 29 | OnLibraryItemSelectedListener, OnSyncListener, OnTeamUpdateListener, OnChatItemClickListener, OnNewsItemClickListener, and more |
| `data/` | Data access and API services | 8 | DatabaseService.kt, NetworkResult.kt, RealmMigrations.kt; sub-packages: `api/` (ApiInterface, ApiClient, ChatApiService, RetryInterceptor), `auth/` (AuthSessionUpdater) |
| `di/` | Hilt dependency injection | 13 | Modules (NetworkModule, DatabaseModule, RepositoryModule, ServiceModule, SharedPreferencesModule, DispatcherModule) + entry points (CoreDependenciesEntryPoint, NetworkDependenciesEntryPoint, RepositoryDependenciesEntryPoint, ServiceDependenciesEntryPoint) + RealmDispatcher |
| `model/` | Realm database models and DTOs | 87 | Realm models + DTOs including ChatMessage, ChatRequest, ChatResponse, CourseProgressData, Download, ServerAddress, User |
| `repository/` | Repository pattern implementations | 50 | 23+ repository domains with Interface + Impl pairs + RealmRepository base + SubmissionsRepositoryExporter |
| `services/` | Background services and workers | 37 | 21 root-level + `sync/` (7), `upload/` (7), `retry/` (2) |
| `ui/` | User interface components | 174 | 28 feature packages with 16+ ViewModels (courses, resources, teams, chat, etc.) |
| `utils/` | Helper functions | 46 | NetworkUtils, ImageUtils, DialogUtils, FileUploader, AuthUtils, SecurePrefs, ANRWatchdog, and more |

### UI Sub-packages (28 feature packages, 174 files)

| Package | Files | Key Components |
|---------|-------|----------------|
| `ui/calendar/` | 1 | CalendarFragment |
| `ui/chat/` | 7 | ChatDetailFragment, ChatHistoryFragment, ChatViewModel |
| `ui/community/` | 6 | CommunityTabFragment, LeadersFragment |
| `ui/components/` | 5 | CustomSpinner, MarkdownDialogFragment, FragmentNavigator |
| `ui/courses/` | 19 | CourseDetailFragment, TakeCourseFragment, ProgressViewModel |
| `ui/dashboard/` | 11 | DashboardActivity, DashboardViewModel, BellDashboardViewModel |
| `ui/dictionary/` | 1 | DictionaryActivity |
| `ui/enterprises/` | 6 | EnterprisesViewModel, FinancesFragment, ReportsFragment |
| `ui/events/` | 4 | EventsDetailFragment, EventsAdapter |
| `ui/exam/` | 2 | ExamTakingFragment, UserInformationFragment |
| `ui/feedback/` | 6 | FeedbackFragment, FeedbackDetailActivity, FeedbackListViewModel |
| `ui/health/` | 7 | MyHealthFragment, AddExaminationActivity |
| `ui/life/` | 2 | LifeFragment, LifeAdapter |
| `ui/maps/` | 1 | OfflineMapsActivity |
| `ui/notifications/` | 3 | NotificationsFragment, NotificationsViewModel |
| `ui/onBoarding/` | 2 | OnboardingActivity, OnboardingAdapter (note the capital B in the package path) |
| `ui/personals/` | 3 | PersonalsFragment, PersonalsAdapter |
| `ui/ratings/` | 2 | RatingsFragment, RatingsViewModel |
| `ui/references/` | 2 | ReferencesFragment, ReferencesAdapter |
| `ui/resources/` | 10 | ResourcesFragment, AddResourceFragment, CollectionsFragment |
| `ui/settings/` | 4 | SettingsActivity, SettingsViewModel, StorageBreakdownFragment, StorageCategoryDetailFragment |
| `ui/submissions/` | 10 | SubmissionsFragment, SubmissionViewModel |
| `ui/surveys/` | 4 | SurveyFragment, SendSurveyFragment |
| `ui/sync/` | 9 | LoginActivity, LoginViewModel, SyncActivity, SyncConfigurationCoordinator, ProcessUserDataActivity |
| `ui/teams/` | 24 | TeamFragment, TeamDetailFragment, TeamViewModel (largest UI package) |
| `ui/user/` | 8 | UserProfileFragment, UserProfileViewModel, BecomeMemberActivity |
| `ui/viewer/` | 4 | ResourceViewerActivity, ResourceViewerFragment, ResourceViewerViewModel, WebViewActivity (all media types render through the shared resource viewer) |
| `ui/voices/` | 11 | VoicesFragment, NewsViewModel, ReplyActivity |

### Critical Files to Understand

1. **`MainApplication.kt`** (~517 lines)
   - Application initialization with Hilt DI
   - WorkManager scheduling (AutoSyncWorker, TaskNotificationWorker, NetworkMonitorWorker, RetryQueueWorker)
   - Server reachability checking with alternative URL mapping
   - Theme/locale management, ANR watchdog, uncaught exception handling
   - Location: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`

2. **`SyncManager.kt`** (~721 lines)
   - Orchestrates data synchronization with server via StateFlow-based state management (`SyncStatus` Idle/Syncing/Success/Error)
   - Delegates per-table pulls to TransactionSyncManager; notifies UI via RealtimeSyncManager's SharedFlow; batch sizing via AdaptiveBatchProcessor
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`

3. **`UploadManager.kt`** (~501 lines)
   - File and data uploads with batch processing (BATCH_SIZE = 50)
   - Integrates with UploadCoordinator for orchestrated uploads
   - Handles activities, submissions, photos, news uploads
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`

4. **`TeamsRepositoryImpl.kt`** (~1785 lines — largest file; candidate for splitting by responsibility)
   - Team management with reactive Flow-based queries
   - Team creation, task management, membership roles
   - Location: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`

5. **`ApiInterface.kt`** (~65 lines)
   - All REST API endpoint definitions (file downloads/uploads, document CRUD, version checking, health access, AI/chat endpoints)
   - Location: `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt`

---

## Technology Stack

### Core Technologies

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Kotlin | 2.4.0 | Primary development language |
| **Build System** | Gradle | 9.5.1 | Build automation |
| **Build Plugin** | Android Gradle Plugin | 9.2.1 | Android build tooling |
| **DI Framework** | Dagger Hilt | 2.60 | Dependency injection |
| **Database** | Realm | 10.19.0 (Realm-Java, **EOL**) | Local object database |
| **Networking** | Retrofit | 3.0.0 | REST API client |
| **HTTP Client** | OkHttp | 5.4.0 | HTTP communication |
| **JSON** | Gson | 2.14.0 | JSON serialization |
| **Async** | Kotlin Coroutines | 1.11.0 | Asynchronous programming |
| **Background Tasks** | AndroidX Work | 2.11.2 | Background job scheduling |
| **UI Framework** | Material Design 3 | 1.14.0 | UI components |
| **Image Loading** | Glide | 5.0.7 | Image loading and caching |
| **Media Playback** | Media3 (ExoPlayer) | 1.10.1 | Audio/video playback |
| **Markdown** | Markwon | 4.6.2 | Markdown rendering |
| **Maps** | OSMDroid | 6.1.20 | OpenStreetMap integration |
| **Encryption** | Tink | 1.21.0 | Cryptographic operations |
| **Serialization** | Kotlin Serialization | 1.11.0 | Kotlin-native serialization |
| **CSV** | OpenCSV | 5.12.0 | CSV file parsing |

### Build Configuration

**Gradle Plugins:**
- `com.android.application`
- `kotlin-android`
- `kotlin-kapt` (Annotation processing)
- `com.google.devtools.ksp` (Symbol processing)
- `com.google.dagger.hilt.android`
- `realm-android`

**Compiler Settings:**
- Java Compatibility: 17
- Kotlin JVM Target: 17
- View Binding: Enabled
- Data Binding: Enabled
- BuildConfig: Enabled

---

## Architecture Patterns

### 1. Layered Architecture

```
UI Layer (Activities/Fragments + 16+ ViewModels)
    ↓
Repository Layer (23 domains, Interface + Impl pairs, Flow-based queries)
    ↓
Service Layer (ApiInterface, SyncManager, UploadCoordinator)
    ↓
Data Sources (Realm local DB, REST API, SharedPreferences)
```

### 2. Repository Pattern

**Convention**: Each data domain has an interface and implementation

```kotlin
// Interface
interface CourseRepository {
    suspend fun getCourses(): List<RealmMyCourse>
    suspend fun syncCourses(): Result<Unit>
}

// Implementation
class CourseRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService
) : CourseRepository {
    override suspend fun getCourses(): List<RealmMyCourse> { ... }
    override suspend fun syncCourses(): Result<Unit> { ... }
}
```

**All 23 Domain Repositories:**
Activities, Chat, Community, Configurations, Courses, Download, Events, Feedback, Health, Life, Notifications, Personals, Progress, Ratings, Resources, Retry, Submissions, Surveys, Tags, Teams, User, Voices

**Utility Classes:**
- `RealmRepository` - Generic base repository
- `SubmissionsRepositoryExporter` - Export utilities

**Location**: `app/src/main/java/org/ole/planet/myplanet/repository/`

### 4. Dependency Injection (Hilt)

**Module Structure (6 modules):**
- `NetworkModule.kt` - Provides Retrofit, OkHttp
- `DatabaseModule.kt` - Provides Realm instances
- `RepositoryModule.kt` - Binds repository interfaces to implementations
- `ServiceModule.kt` - Provides service dependencies
- `SharedPreferencesModule.kt` - Provides SharedPreferences
- `DispatcherModule.kt` - Provides coroutine dispatchers (see also `RealmDispatcher.kt`)

**Entry Points for Workers (4 entry point files):**
- `CoreDependenciesEntryPoint`
- `NetworkDependenciesEntryPoint`
- `RepositoryDependenciesEntryPoint`
- `ServiceDependenciesEntryPoint`

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkDependenciesEntryPoint {
    fun apiInterface(): ApiInterface
    fun sharedPreferences(): SharedPreferences
}
```

**Location**: `app/src/main/java/org/ole/planet/myplanet/di/`

### 5. Base Classes for Code Reuse (12 classes)

| Base Class | Purpose |
|------------|---------|
| `BaseActivity` | Common activity functionality (permission handling, dialogs) |
| `BasePermissionActivity` | Runtime permission request handling |
| `BaseRecyclerFragment` | List-based fragments (pagination, filtering, search) |
| `BaseRecyclerParentFragment` | Parent fragment for recycler views |
| `BaseContainerFragment` | Navigation containers (fragment transactions) |
| `BaseDashboardFragment` | Dashboard-specific base functionality |
| `BaseResourceFragment` | Resource handling (download, view, share) |
| `BaseTeamFragment` | Team-specific base functionality |
| `BaseExamFragment` | Exam-specific base functionality |
| `BaseMemberFragment` | Member management base functionality |
| `BaseDialogFragment` | Dialog base class |
| `BaseVoicesFragment` | Voices/news-specific base functionality |

**Location**: `app/src/main/java/org/ole/planet/myplanet/base/`

### 6. Background Processing

**AndroidX Work for Scheduled Tasks:**
- `AutoSyncWorker` - Periodic data synchronization
- `NetworkMonitorWorker` - Network state monitoring
- `ServerReachabilityWorker` - Server availability checking
- `TaskNotificationWorker` - Task deadline notifications
- `DownloadWorker` - Background file downloads
- `FreeSpaceWorker` - Disk space monitoring
- `RetryQueueWorker` - Retries failed operations (`services/retry/`)

**Services and Managers (21 root-level files):**
- `SyncManager` - Manual synchronization (`services/sync/`)
- `UploadManager` - File upload coordination (extends FileUploader)
- `UploadToShelfService` - Shelf upload operations
- `UploadCoordinator` - Upload orchestration (`services/upload/`)
- `AudioRecorder` - Audio recording
- `BroadcastService` - Service broadcasting
- `SharedPrefManager` - SharedPreferences management
- `UserSessionManager` - User session handling
- `ThemeManager` - App theming
- `FileUploader` - File upload utilities
- `DownloadService` - Background file download service (foreground service)
- `ResourceDownloadCoordinator` - Orchestrates resource downloads
- `SubmissionUploadExecutor` - Executes submission uploads
- `VoicesLabelManager` - Voice/discussion forum label management
- `ChallengePrompter` - Challenge prompt generation
- `NotificationActionReceiver` - Broadcast receiver for notification actions

**Sync Sub-package (`services/sync/` - 7 files):**
- `SyncManager` (~721) - Orchestrates sync via StateFlow; the entry point for full syncs
- `TransactionSyncManager` (~519) - Per-table paginated pulls from CouchDB with checkpoint/resume
- `LoginSyncManager` (~195) - Sync triggered around the login flow
- `ServerUrlMapper` (~116) - Maps primary server URLs to alternative/clone URLs
- `HeavyTableSyncWorker` (~66) - WorkManager worker for large-table background sync
- `AdaptiveBatchProcessor` (~37) - Batch-size tuning used by SyncManager
- `RealtimeSyncManager` (~27) - SharedFlow of `TableDataUpdate` events; UI collects `dataUpdateFlow` (via `RealtimeSyncHelper`/`collectWhenStarted`)

**Upload Sub-package (`services/upload/` - 7 files):**
- `UploadCoordinator` - Central orchestration for all upload operations with batch processing and retry
- `UploadConfigs` - Configuration objects for different upload types (NewsActivities, Submissions, Photos, etc.)
- `UploadConfig` - Generic configuration template with batch size and Realm model binding
- `UploadResult` - Result wrapper with success/failure/empty states
- `UploadConstants` - Shared upload constants
- `PhotoUploader`, `AchievementUploader` - Type-specific uploaders

**Retry Sub-package (`services/retry/` - 2 files):**
- `RetryQueue` - Queue-based retry mechanism for failed operations
- `RetryQueueWorker` - Background worker for processing retries

**Location**: `app/src/main/java/org/ole/planet/myplanet/services/`

---

## Development Workflows

### Setting Up Development Environment

1. **Clone Repository**
   ```bash
   git clone https://github.com/open-learning-exchange/myplanet.git
   cd myplanet
   ```

2. **Gradle Build**
   ```bash
   # Build debug APK (default flavor)
   ./gradlew assembleDefaultDebug

   # Build debug APK (lite flavor)
   ./gradlew assembleLiteDebug

   # Build release
   ./gradlew assembleDefaultRelease bundleDefaultRelease
   ```

3. **Run on Device/Emulator**
   ```bash
   # Install debug build
   ./gradlew installDefaultDebug

   # Install and run
   ./gradlew installDefaultDebug && adb shell am start -n org.ole.planet.myplanet/.ui.onboarding.OnBoardingActivity
   ```

### Branch Strategy

**Important**: Always work on branches starting with `claude/` and matching the session ID format.

```bash
# Create feature branch
git checkout -b claude/feature-name-sessionid

# Develop changes
# ...

# Commit with descriptive messages
git add .
git commit -m "feat: add user profile photo upload

- Implement camera integration
- Add image cropping functionality
- Update UserSessionManager"

# Push to remote (MUST use -u flag)
git push -u origin claude/feature-name-sessionid
```

### CI/CD Pipeline

**Build Workflow** (`.github/workflows/build.yml`)
- Triggers: All branches except `master` (includes `claude/**`, `codex/**`, `dependabot/**`, `jules/**`)
- Runs on Ubuntu 24.04
- Matrix builds both `default` and `lite` flavors with fail-fast disabled
- Uses `gradle/actions/setup-gradle@v6` with a remote Gradle build cache
- Build command: `./gradlew assemble${FLAVOR}Debug --parallel --max-workers=4`

**Test Workflow** (`.github/workflows/test.yml`)
- Triggers: every push (all branches) + manual dispatch; `permissions: contents: read`
- Runs `./gradlew testDefaultDebugUnitTest` — **fails the build on any unit-test failure**
- `default` flavor only (the `lite` flavor's unit tests are not run in CI)
- No instrumented (`androidTest`) execution in CI

**Release Workflow** (`.github/workflows/release.yml`)
- Triggers: `master` branch push or manual dispatch
- Builds signed APK and AAB for both flavors
- Signs with keystore credentials via GitHub Secrets
- Generates SHA256 checksums for integrity verification
- Publishes to Google Play Store (internal track) with fallback retry
- Creates GitHub release with artifacts (tag: `v${VERSION}`)
- Sends Discord notifications via Treehouses CLI

**Dependabot** (`.github/dependabot.yml`)
- Daily checks for GitHub Actions updates (max 10 open PRs)
- Daily checks for Gradle dependency updates (max 15 open PRs)

### Adding New Features

1. **Identify the Layer**
   - UI change? → `ui/` package
   - Data model? → `model/` package
   - Business logic? → `repository/` or `services/`
   - Network API? → `data/ApiInterface.kt`

2. **Create Necessary Components**
   - Model class (Realm if persistent)
   - Repository interface + implementation
   - UI components (Activity/Fragment)
   - Layout XML files

3. **Update Dependencies**
   - Add dependencies in `gradle/libs.versions.toml`
   - Reference in `app/build.gradle`

4. **Register in Manifest**
   - Add activities in `AndroidManifest.xml`
   - Add permissions if needed

5. **Update DI Modules**
   - Provide new dependencies in appropriate module
   - Bind repository interfaces in `RepositoryModule`

### Modifying Existing Features

1. **Locate Relevant Files**
   ```bash
   # Find by class name
   find app/src -name "*ClassName*.kt"

   # Search for functionality
   grep -r "functionality keyword" app/src/main/java
   ```

2. **Understand Dependencies**
   - Check constructor injection (Hilt)
   - Identify repository usage
   - Review layout bindings

3. **Make Changes**
   - Update model if data structure changes
   - Modify repository if data access changes
   - Update UI components for visual changes

4. **Test Changes**
   - Build and run on device
   - Verify offline functionality
   - Test synchronization if applicable

---

## Key Conventions

### Code Style

**Kotlin Conventions:**
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use camelCase for functions and variables
- Use PascalCase for classes
- Use UPPER_SNAKE_CASE for constants

**File Naming:**
- Activities: `*Activity.kt` (e.g., `LoginActivity.kt`)
- Fragments: `*Fragment.kt` (e.g., `CourseListFragment.kt`)
- ViewModels: `*ViewModel.kt` (e.g., `ChatViewModel.kt`)
- Adapters: `*Adapter.kt` (e.g., `CourseAdapter.kt`)
- ViewHolders: `*ViewHolder.kt`
- Repositories: `*Repository.kt` and `*RepositoryImpl.kt`
- Models: `Realm*.kt` for Realm objects
- Workers: `*Worker.kt` (e.g., `AutoSyncWorker.kt`)

**Layout Naming:**
- Activities: `activity_*.xml`
- Fragments: `fragment_*.xml`
- List items: `row_*.xml` or `item_*.xml`
- Dialogs: `dialog_*.xml`

### Realm Database Conventions

**Model Classes:**
```kotlin
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmMyCourse : RealmObject() {
    @PrimaryKey
    var _id: String = ""
    var courseTitle: String? = null
    var description: String? = null
    var createdDate: Long = 0
    // ... other fields
}
```

**Key Points:**
- All Realm models must extend `RealmObject`
- Use `open` class modifier
- Use `var` for all properties (Realm requirement)
- Primary keys use `@PrimaryKey` annotation
- Nullable types use `?` suffix

**Database Operations — use `DatabaseService` (preferred pattern):**

Data access goes through `data/DatabaseService.kt`, **not** raw `Realm` instances in the UI. `DatabaseService` opens a thread-local Realm, always closes it in a `finally`, and confines work to coroutine dispatchers. Repositories **return detached copies** (`copyFromRealm`) so Realm objects never cross threads or leak to the UI — only `DictionaryActivity` still uses the raw API directly, and new code should not.

```kotlin
// Read (suspend) — runs on the IO dispatcher, returns DETACHED copies
val courses = databaseService.withRealmAsync { realm ->
    realm.queryList(RealmMyCourse::class.java) {
        equalTo("userId", userId)
    } // queryList already applies copyFromRealm
}

// Single object, detached
val course = databaseService.withRealmAsync { realm ->
    realm.findCopyByField(RealmMyCourse::class.java, "_id", courseId)
}

// Write (suspend) — confined to a limited-parallelism Realm dispatcher
databaseService.executeTransactionAsync { realm ->
    realm.copyToRealmOrUpdate(course)
}

// Delete
databaseService.executeTransactionAsync { realm ->
    realm.where(RealmMyCourse::class.java)
        .equalTo("_id", courseId)
        .findFirst()
        ?.deleteFromRealm()
}
```

**Rules:**
- Never return live `RealmObject`/`RealmResults` from a repository — return `copyFromRealm` copies (helpers `queryList` / `findCopyByField` do this for you).
- Never hold a `Realm` instance as a long-lived field or pass it between threads; let `DatabaseService` manage open/close.
- Do not call blocking Realm APIs on `Dispatchers.Main`; use the `withRealmAsync` / `executeTransactionAsync` suspend helpers.
- Inject `DispatcherProvider` (don't hard-code `Dispatchers.IO`) so tests can substitute deterministic dispatchers.
- The raw `mRealm.where(...).findAll()` style is **legacy**; prefer the helpers above.

### Dependency Injection Patterns

**Constructor Injection (Preferred):**
```kotlin
@AndroidEntryPoint
class CourseListFragment : BaseRecyclerFragment() {
    @Inject lateinit var courseRepository: CourseRepository
    @Inject lateinit var sharedPreferences: SharedPreferences
}
```

**Manual Injection for Workers:**
```kotlin
class AutoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NetworkDependenciesEntryPoint::class.java
        )
        val apiInterface = entryPoint.apiInterface()
        // ... use injected dependencies
    }
}
```

### Asynchronous Programming

**Use Kotlin Coroutines:**
```kotlin
// In Fragment/Activity
viewLifecycleOwner.lifecycleScope.launch {
    try {
        val result = courseRepository.syncCourses()
        // Update UI
    } catch (e: Exception) {
        // Handle error
    }
}

// In Repository
override suspend fun syncCourses(): Result<Unit> = withContext(Dispatchers.IO) {
    val response = apiInterface.getCourses()
    if (response.isSuccessful) {
        response.body()?.let { courses ->
            saveCourses(courses)
        }
        Result.success(Unit)
    } else {
        Result.failure(Exception("Sync failed"))
    }
}
```

**Background Work:**
```kotlin
// Schedule periodic work
val workRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(
    15, TimeUnit.MINUTES
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).build()

WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork(
        "AutoSync",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
```

### View Binding

**Activity:**
```kotlin
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            // Handle click
        }
    }
}
```

**Fragment:**
```kotlin
class CourseListFragment : Fragment() {
    private var _binding: FragmentCourseListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCourseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### Error Handling

**Network Errors:**
```kotlin
try {
    val response = apiInterface.getData()
    if (response.isSuccessful) {
        // Handle success
    } else {
        showError("Server error: ${response.code()}")
    }
} catch (e: IOException) {
    showError("Network error: ${e.message}")
} catch (e: Exception) {
    showError("Unexpected error: ${e.message}")
}
```

**Realm Errors:**
```kotlin
try {
    mRealm.executeTransaction { realm ->
        // Database operations
    }
} catch (e: RealmException) {
    Log.e(TAG, "Database error", e)
    showError("Failed to save data")
}
```

### Resource Management

**String Resources:**
```kotlin
// Use resource strings for all user-facing text
binding.tvTitle.text = getString(R.string.course_title)

// With arguments
getString(R.string.course_progress, currentStep, totalSteps)
```

**Dimensions:**
```kotlin
// Use dimension resources
val padding = resources.getDimensionPixelSize(R.dimen.standard_padding)
```

**Colors:**
```kotlin
// Use color resources
val color = ContextCompat.getColor(context, R.color.primary)
```

### Localization

**Support Languages:**
- English (default)
- Arabic (ar)
- Spanish (es)
- French (fr)
- Nepali (ne)
- Somali (so)

**Adding Translations:**
1. Update `app/src/main/res/values/strings.xml`
2. Sync with Crowdin (automatic via `crowdin.yml`)
3. Translations appear in `values-{lang}/strings.xml`

---

## Common Tasks

### Adding a New Data Model

1. **Create Model Class**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/model/RealmMyNewModel.kt
   package org.ole.planet.myplanet.model

   import io.realm.RealmObject
   import io.realm.annotations.PrimaryKey

   open class RealmMyNewModel : RealmObject() {
       @PrimaryKey
       var _id: String = ""
       var title: String? = null
       var createdDate: Long = 0

       companion object {
           fun insert(realm: Realm, data: JsonObject) {
               val model = realm.createObject(RealmMyNewModel::class.java, data.get("_id").asString)
               model.title = data.get("title")?.asString
               model.createdDate = System.currentTimeMillis()
           }
       }
   }
   ```

2. **Update API Interface**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/data/ApiInterface.kt
   @GET("myendpoint")
   suspend fun getMyNewModels(): Response<List<JsonObject>>
   ```

3. **Create Repository**
   ```kotlin
   // Interface
   interface MyNewModelRepository {
       suspend fun getModels(): List<RealmMyNewModel>
       suspend fun syncModels(): Result<Unit>
   }

   // Implementation
   class MyNewModelRepositoryImpl @Inject constructor(
       private val apiInterface: ApiInterface,
       private val databaseService: DatabaseService
   ) : MyNewModelRepository {
       // Implementation
   }
   ```

4. **Register in DI Module**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt
   @Binds
   abstract fun bindMyNewModelRepository(
       impl: MyNewModelRepositoryImpl
   ): MyNewModelRepository
   ```

### Adding a New Screen

1. Create layout XML in `res/layout/activity_my_feature.xml`
2. Create `@AndroidEntryPoint` Activity/Fragment extending appropriate base class with view binding
3. Register in `AndroidManifest.xml`
4. Navigate with `Intent(context, MyFeatureActivity::class.java)`

### Adding a New API Endpoint

1. **Update ApiInterface**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/data/ApiInterface.kt
   @POST("api/endpoint")
   suspend fun postData(
       @Body data: JsonObject,
       @Header("Authorization") auth: String
   ): Response<JsonObject>
   ```

2. **Use in Repository**
   ```kotlin
   override suspend fun submitData(data: MyData): Result<Unit> = withContext(Dispatchers.IO) {
       try {
           val jsonData = convertToJson(data)
           val response = apiInterface.postData(jsonData, getAuthHeader())
           if (response.isSuccessful) {
               Result.success(Unit)
           } else {
               Result.failure(Exception("API error: ${response.code()}"))
           }
       } catch (e: Exception) {
           Result.failure(e)
       }
   }
   ```

### Implementing Offline Sync

1. **Download**: Fetch from API, save to Realm via `executeTransaction` with model `insert()` method
2. **Upload**: Query unsynced items (`equalTo("synced", false)`), POST each to server, mark synced on success

### Adding Background Work

1. Create `CoroutineWorker` subclass with `doWork()` returning `Result.success()` or `Result.retry()`
2. Schedule with `PeriodicWorkRequestBuilder<MyWorker>(interval, unit)` + network constraints + `WorkManager.enqueueUniquePeriodicWork`

---

## Testing Guidelines

### Current State
- **A real unit-test suite exists**: 140 unit-test files in `app/src/test/` + 2 instrumented tests in `app/src/androidTest/` (142 test files total).
- **Stack**: JUnit4, **MockK** (`mockk` / `mockk-android`), **Robolectric**, `kotlinx-coroutines-test`, AndroidX Test (`core`/`ext`/`runner`/`arch-core-testing`), and **Hilt testing** (`hilt-android-testing` with `kspTest`). Dependencies are declared in `app/build.gradle` (test block) and `gradle/libs.versions.toml`.
- **Coverage**: nearly all 23 repositories, the sync managers (`services/sync/`), upload/retry services, most ViewModels, many `utils/`, several Realm models, DI modules, and the API/auth layer.
- **Shared test infra**: `MainDispatcherRule`, `TestDispatcherProvider` (inject deterministic dispatchers — production code uses an injectable `DispatcherProvider`, so avoid hard-coding `Dispatchers.*` in new code).
- **CI enforcement**: `.github/workflows/test.yml` runs `./gradlew testDefaultDebugUnitTest` on every push and fails the build on any test failure. (Instrumented tests are **not** run in CI.)

### Running Tests

```bash
# Unit tests (default flavor) — what CI runs
./gradlew testDefaultDebugUnitTest

# Unit tests (lite flavor) — NOT covered by CI; run locally when touching flavor-specific code
./gradlew testLiteDebugUnitTest

# A single test class
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.CoursesRepositoryImplTest"

# Instrumented tests (needs a connected device/emulator)
./gradlew connectedDefaultDebugAndroidTest
```

### Writing Tests (conventions used in this repo)

```kotlin
// Repository/ViewModel unit test — MockK + coroutines-test
class CoursesRepositoryImplTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val databaseService: DatabaseService = mockk(relaxed = true)

    @Test
    fun `getCourses returns detached copies`() = runTest {
        val repository = CoursesRepositoryImpl(apiInterface, databaseService, TestDispatcherProvider())
        coEvery { databaseService.withRealmAsync<Any>(any()) } returns /* ... */
        val result = repository.getCourses()
        assertTrue(result.isNotEmpty())
    }
}
```

**Conventions:**
- Prefer **MockK** (`mockk`, `coEvery`, `coVerify`) — not Mockito.
- Use `runTest { }` + `MainDispatcherRule` for coroutine code; inject `TestDispatcherProvider` instead of real dispatchers.
- Use **Robolectric** (`@RunWith(RobolectricTestRunner::class)`) for tests needing Android framework classes without a device.
- Add new tests next to existing ones (mirror the `main` package path) so CI picks them up automatically.

### Manual Testing Checklist

When making changes, verify:
- [ ] App builds successfully
- [ ] Feature works in offline mode
- [ ] Synchronization works correctly
- [ ] UI renders on different screen sizes
- [ ] Dark theme works correctly (if applicable)
- [ ] All supported languages display correctly
- [ ] Permissions are requested appropriately
- [ ] Background sync continues to work

---

## Security Considerations

### Sensitive Data Handling

**Never hardcode:**
- API keys
- Passwords
- Server URLs / server PINs
- User credentials

> ⚠️ **KNOWN ISSUE — secrets currently committed.** `gradle.properties` is **tracked in git** (it is *not* gitignored) and holds real `PLANET_*_URL` / `PLANET_*_PIN` values. `app/build.gradle` bakes each into `BuildConfig`, and since `minifyEnabled=false` they are trivially recoverable from any shipped APK. These PINs are real CouchDB `satellite` credentials (used in `UrlUtils.header`, `ConfigurationsRepositoryImpl.buildCouchdbUrl`, and the `/healthaccess` PIN). **Do not add new secrets here.** Remediation: rotate the exposed PINs server-side, move values to an untracked file / CI secrets, gitignore `gradle.properties`, and purge it from git history.

**Preferred pattern — inject config via untracked properties or CI secrets, then expose as `BuildConfig` fields:**
```properties
# A gitignored local file (e.g. local.properties / secrets.properties), or CI-injected -P properties
PLANET_LEARNING_URL=https://example.org
PLANET_LEARNING_PIN=****
```

**Access in code:**
```kotlin
val serverUrl = BuildConfig.PLANET_LEARNING_URL
```

### Network Security

**Network Security Config:**
- Location: `app/src/main/res/xml/network_security_config.xml`
- Configure trusted certificates
- Set cleartext traffic policies

**HTTPS Enforcement:**
```kotlin
// Prefer HTTPS URLs
val baseUrl = "https://example.org/"

// Validate URLs
if (!url.startsWith("https://")) {
    throw SecurityException("Only HTTPS URLs allowed")
}
```

### Data Encryption

**Encrypted SharedPreferences:**
```kotlin
val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

**Password Hashing:**
```kotlin
// Use PBKDF2 for password hashing
val hashedPassword = Sha256Utils.hash(password)
```

### Permissions

**Request at Runtime:**
```kotlin
// Check permission
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {

    // Request permission
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        REQUEST_CAMERA
    )
}
```

**Declare in Manifest:**
```xml
<uses-permission android:name="android.permission.CAMERA" />
```

### ProGuard/R8

**Current state:** `minifyEnabled` is `false` for both debug and release builds. If enabling for release:
```gradle
buildTypes {
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

---

## Troubleshooting

### Common Build Issues

**Issue: Gradle sync failed**
```bash
# Solution: Clean and rebuild
./gradlew clean
./gradlew build --refresh-dependencies
```

**Issue: KAPT/KSP annotation processing errors**
```bash
# Solution: Clean build cache
./gradlew clean
rm -rf .gradle/
./gradlew build
```

**Issue: Realm schema migration errors**
```kotlin
// Solution: Increment schema version or delete and rebuild
val config = RealmConfiguration.Builder()
    .deleteRealmIfMigrationNeeded()  // Development only!
    .build()
```

**Issue: Hilt dependency not found**
- Ensure `@AndroidEntryPoint` annotation is present
- Verify module provides the dependency
- Check injection point is correct (constructor vs field)

### Runtime Issues

**Issue: Network requests fail**
```kotlin
// Debug: Check network state
val isConnected = NetworkUtils.isNetworkAvailable(context)
Log.d(TAG, "Network available: $isConnected")

// Debug: Log request/response
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

**Issue: Realm database locked**
```kotlin
// Solution: Ensure realm is closed properly
override fun onDestroy() {
    super.onDestroy()
    if (::mRealm.isInitialized && !mRealm.isClosed) {
        mRealm.close()
    }
}
```

**Issue: Out of memory with images**
```kotlin
// Solution: Use Glide with proper sizing
Glide.with(context)
    .load(imageUrl)
    .override(800, 600)  // Limit size
    .into(imageView)
```

### Git Issues

**Issue: Push fails with 403**
- Ensure branch name starts with `claude/`
- Ensure branch name ends with matching session ID
- Use `git push -u origin <branch-name>`

**Issue: Merge conflicts**
```bash
# Solution: Fetch latest and rebase
git fetch origin
git rebase origin/master

# Resolve conflicts in files
# Then:
git add .
git rebase --continue
```

### CI/CD Issues

**Issue: Build workflow fails**
- Check Gradle version compatibility
- Verify all dependencies are accessible
- Review workflow logs in GitHub Actions

**Issue: Release workflow fails**
- Ensure signing credentials are configured
- Verify Play Store API access
- Check bundle/APK generation

---

## Additional Resources

### External Documentation
- [myPlanet Manual](https://open-learning-exchange.github.io/#!pages/manual/myplanet/overview.md)
- [Android Developer Documentation](https://developer.android.com/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Realm Documentation](https://www.mongodb.com/docs/realm/)
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)

### Community
- [Discord Server](https://discord.gg/BVrFEeNtQZ)
- [GitHub Issues](https://github.com/open-learning-exchange/myplanet/issues)

### Key File References

| Purpose | File Path | Line Count |
|---------|-----------|------------|
| Main entry point | `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt` | ~517 |
| REST API endpoints | `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt` | ~65 |
| Sync orchestration | `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` | ~721 |
| Upload handling | `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` | ~501 |
| Upload orchestration | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` | ~306 |
| Team management | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` | ~1785 |
| Realm abstraction | `app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt` | ~112 |
| Build configuration | `app/build.gradle` | ~232 |
| Dependency versions | `gradle/libs.versions.toml` | ~130 |

---

## Codebase Inventory Summary

### Source Files (457 total Kotlin files in `app/src/main/java`) + 142 test files (140 in `app/src/test`, 2 in `app/src/androidTest`)

| Component | Files | Purpose |
|-----------|-------|---------|
| `model/` | 87 | Realm database models + DTOs |
| `repository/` | 50 | Data access abstraction (23+ domain Interface+Impl pairs + utilities) |
| `ui/` | 174 | User interface across 28 feature packages |
| `services/` | 37 | Background tasks & managers (21 root-level + sync/upload/retry sub-packages) |
| `di/` | 13 | Dependency injection (modules + entry points + RealmDispatcher) |
| `base/` | 12 | Reusable base classes |
| `callback/` | 29 | Event listeners and interfaces |
| `data/` | 8 | Data services, API, auth |
| `utils/` | 46 | Helper utilities |
| Root | 1 | MainApplication.kt |

### Resource Files

| Category | Count |
|----------|-------|
| Layout files (main) | 186 |
| Translation languages | 5 (ar, es, fr, ne, so) |
| Menu files | 2 |
| XML config files | 3 |

### AndroidManifest Permissions (17 total)

**Network**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_NETWORK_STATE
**Device**: CAMERA, RECORD_AUDIO, WAKE_LOCK, BLUETOOTH
**System**: PACKAGE_USAGE_STATS, REQUEST_INSTALL_PACKAGES (default flavor only; removed in lite)
**Notifications**: POST_NOTIFICATIONS, C2DM RECEIVE
**Foreground services**: FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC
**Other**: SEND_DOWNLOAD_COMPLETED_INTENTS; REQUEST_WRITE_PERMISSION (not a real Android permission — candidate for removal)

Note: SYSTEM_ALERT_WINDOW is **not** declared (removed at some point; older docs claimed it).

---

**Last Updated**: 2026-07-07
**Version**: 0.59.69
**Maintainer**: Open Learning Exchange

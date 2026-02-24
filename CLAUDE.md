# CLAUDE.md - AI Assistant Guide for myPlanet

This document provides comprehensive guidance for AI assistants working on the myPlanet Android application codebase.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Codebase Structure](#codebase-structure)
3. [Technology Stack](#technology-stack)
4. [Architecture Patterns](#architecture-patterns)
5. [Development Workflows](#development-workflows)
6. [Key Conventions](#key-conventions)
7. [Common Tasks](#common-tasks)
8. [Testing Guidelines](#testing-guidelines)
9. [Security Considerations](#security-considerations)
10. [Troubleshooting](#troubleshooting)
11. [Codebase Inventory Summary](#codebase-inventory-summary)

---

## Project Overview

**myPlanet** is an Android mobile application serving as an offline extension of the Open Learning Exchange's Planet Learning Management System. It enables learners to access educational resources (books, videos, courses) without continuous internet connectivity.

### Key Characteristics
- **Primary Language**: Kotlin (with Java compatibility layer)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15)
- **Current Version**: 0.46.0 (versionCode: 4600)
- **Build System**: Gradle 9.3.1 with Android Gradle Plugin 9.0.0
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
│       └── release.yml        # Release and Play Store publishing
├── app/                       # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/org/ole/planet/myplanet/
│   │   │   │   ├── MainApplication.kt       # App entry point with Hilt
│   │   │   │   ├── base/                    # Base classes for activities/fragments
│   │   │   │   ├── callback/                # Event listeners and interfaces
│   │   │   │   ├── data/                    # Data services and API
│   │   │   │   ├── di/                      # Dependency injection modules
│   │   │   │   ├── model/                   # Realm data models (40 Realm classes, 67 total)
│   │   │   │   ├── repository/              # Repository pattern implementations
│   │   │   │   ├── services/                # Background services and workers
│   │   │   │   ├── ui/                      # UI components (28 packages)
│   │   │   │   └── utils/                   # Helper utilities
│   │   │   ├── res/                         # Android resources
│   │   │   │   ├── layout/                  # 169 layout files
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
| `callback/` | Event listeners and interfaces | 34 | OnLibraryItemSelectedListener, OnSyncListener, OnTeamUpdateListener, OnChatItemClickListener, OnNewsItemClickListener, and 29 more |
| `data/` | Data access and API services | 8 | DatabaseService.kt, NetworkResult.kt, RealmMigrations.kt; sub-packages: `api/` (ApiInterface, ApiClient, ChatApiService, RetryInterceptor), `auth/` (AuthSessionUpdater) |
| `di/` | Hilt dependency injection | 16 | 5 modules (NetworkModule, DatabaseModule, RepositoryModule, ServiceModule, SharedPreferencesModule) + 11 entry points |
| `model/` | Realm database models and DTOs | 67 | 40 Realm models + 27 DTOs including ChatMessage, ChatRequest, ChatResponse, CourseProgressData, Download, ServerAddress, User |
| `repository/` | Repository pattern implementations | 38 | 19 repositories with Interface + Impl pairs + RealmRepository base + SubmissionsRepositoryExporter |
| `services/` | Background services and workers | 37 | 20 root-level + `sync/` (11), `upload/` (4), `retry/` (2) |
| `ui/` | User interface components | 147 | 28 feature packages with 16 ViewModels (courses, resources, teams, chat, etc.) |
| `utils/` | Helper functions | 39 | NetworkUtils, ImageUtils, DialogUtils, FileUploader, AuthUtils, SecurePrefs, ANRWatchdog, and 32 more |

### UI Sub-packages (28 feature packages, 147 files)

| Package | Files | Key Components |
|---------|-------|----------------|
| `ui/calendar/` | 1 | CalendarFragment |
| `ui/chat/` | 6 | ChatDetailFragment, ChatHistoryFragment, ChatViewModel |
| `ui/community/` | 6 | CommunityTabFragment, LeadersFragment |
| `ui/components/` | 5 | CustomSpinner, MarkdownDialogFragment, FragmentNavigator |
| `ui/courses/` | 12 | CourseDetailFragment, TakeCourseFragment, ProgressViewModel |
| `ui/dashboard/` | 11 | DashboardActivity, DashboardViewModel, BellDashboardViewModel |
| `ui/dictionary/` | 1 | DictionaryActivity |
| `ui/enterprises/` | 5 | EnterprisesViewModel, FinancesFragment, ReportsFragment |
| `ui/events/` | 2 | EventsDetailFragment, EventsAdapter |
| `ui/exam/` | 2 | ExamTakingFragment, UserInformationFragment |
| `ui/feedback/` | 6 | FeedbackFragment, FeedbackDetailActivity, FeedbackListViewModel |
| `ui/health/` | 5 | MyHealthFragment, AddExaminationActivity |
| `ui/life/` | 2 | LifeFragment, LifeAdapter |
| `ui/maps/` | 1 | OfflineMapsActivity |
| `ui/notifications/` | 3 | NotificationsFragment, NotificationsViewModel |
| `ui/onboarding/` | 2 | OnboardingActivity, OnboardingAdapter |
| `ui/personals/` | 2 | PersonalsFragment, PersonalsAdapter |
| `ui/ratings/` | 2 | RatingsFragment, RatingsViewModel |
| `ui/references/` | 2 | ReferencesFragment, ReferencesAdapter |
| `ui/resources/` | 8 | ResourcesFragment, AddResourceFragment, CollectionsFragment |
| `ui/settings/` | 1 | SettingsActivity |
| `ui/submissions/` | 8 | SubmissionsFragment, SubmissionViewModel |
| `ui/surveys/` | 4 | SurveyFragment, SendSurveyFragment |
| `ui/sync/` | 7 | LoginActivity, SyncActivity, ProcessUserDataActivity |
| `ui/teams/` | 22 | TeamFragment, TeamDetailFragment, TeamViewModel (largest UI package) |
| `ui/user/` | 7 | UserProfileFragment, UserProfileViewModel, BecomeMemberActivity |
| `ui/viewer/` | 8 | ImageViewer, VideoViewer, AudioPlayer, PDFReader, WebView, MarkdownViewer, TextFileViewer, CSVViewer |
| `ui/voices/` | 6 | VoicesFragment, NewsViewModel, ReplyActivity |

### Critical Files to Understand

1. **`MainApplication.kt`** (~448 lines)
   - Application initialization with Hilt DI
   - WorkManager scheduling (AutoSyncWorker, StayOnlineWorker, TaskNotificationWorker, NetworkMonitorWorker, RetryQueueWorker)
   - Server reachability checking with alternative URL mapping
   - Theme/locale management, ANR watchdog, uncaught exception handling
   - Location: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`

2. **`SyncManager.kt`** (~1058 lines)
   - Orchestrates data synchronization with server via StateFlow-based state management
   - Integrates with ImprovedSyncManager, TransactionSyncManager, RealtimeSyncManager
   - Semaphore-based concurrency control, adaptive batch processing
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`

3. **`UploadManager.kt`** (~770 lines)
   - File and data uploads with batch processing (BATCH_SIZE = 50)
   - Integrates with UploadCoordinator for orchestrated uploads
   - Handles activities, submissions, photos, news uploads
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`

4. **`TeamsRepositoryImpl.kt`** (~1097 lines)
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
| **Language** | Kotlin | 2.3.10 | Primary development language |
| **Build System** | Gradle | 9.3.1 | Build automation |
| **Build Plugin** | Android Gradle Plugin | 9.0.0 | Android build tooling |
| **DI Framework** | Dagger Hilt | 2.59.1 | Dependency injection |
| **Database** | Realm | 10.19.0 | Local object database |
| **Networking** | Retrofit | 3.0.0 | REST API client |
| **HTTP Client** | OkHttp | 5.3.2 | HTTP communication |
| **JSON** | Gson | 2.13.2 | JSON serialization |
| **Async** | Kotlin Coroutines | 1.10.2 | Asynchronous programming |
| **Background Tasks** | AndroidX Work | 2.11.1 | Background job scheduling |
| **UI Framework** | Material Design 3 | 1.13.0 | UI components |
| **Image Loading** | Glide | 5.0.5 | Image loading and caching |
| **Media Playback** | Media3 (ExoPlayer) | 1.9.2 | Audio/video playback |
| **Markdown** | Markwon | 4.6.2 | Markdown rendering |
| **Maps** | OSMDroid | 6.1.20 | OpenStreetMap integration |
| **Encryption** | Tink | 1.20.0 | Cryptographic operations |
| **Serialization** | Kotlin Serialization | 1.10.0 | Kotlin-native serialization |
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
┌─────────────────────────────────────────┐
│     UI Layer (Activities/Fragments)     │
│  - User interaction & view binding      │
│  - Lifecycle management                 │
│  - 16 ViewModels for state management   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Repository Layer (19 domains)       │
│  - Data access abstraction              │
│  - Interface + Implementation pairs     │
│  - Reactive Flow-based queries          │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Service Layer                       │
│  - ApiInterface (remote operations)     │
│  - SyncManager (synchronization)        │
│  - UploadCoordinator (upload orchestr.) │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Data Sources                        │
│  - Realm Database (local)               │
│  - REST API (remote)                    │
│  - SharedPreferences (settings)         │
└─────────────────────────────────────────┘
```

### 2. MVVM with ViewModels

The UI layer uses ViewModels for state management across 16 feature areas:

| ViewModel | Package | Purpose |
|-----------|---------|---------|
| `ChatViewModel` | `ui/chat/` | Chat message state and AI interactions |
| `TeamViewModel` | `ui/teams/` | Team data and operations |
| `RequestsViewModel` | `ui/teams/` | Team join requests |
| `DashboardViewModel` | `ui/dashboard/` | Dashboard data aggregation |
| `BellDashboardViewModel` | `ui/dashboard/` | Bell community dashboard |
| `ProgressViewModel` | `ui/courses/` | Course progress tracking |
| `EnterprisesViewModel` | `ui/enterprises/` | Enterprise finances and reports |
| `RatingsViewModel` | `ui/ratings/` | Resource ratings |
| `NewsViewModel` | `ui/voices/` | News/voices feed |
| `ReplyViewModel` | `ui/voices/` | Reply composition |
| `FeedbackListViewModel` | `ui/feedback/` | Feedback listing |
| `FeedbackDetailViewModel` | `ui/feedback/` | Feedback detail view |
| `SubmissionViewModel` | `ui/submissions/` | Submission management |
| `SubmissionDetailViewModel` | `ui/submissions/` | Submission details |
| `UserProfileViewModel` | `ui/user/` | User profile data |
| `NotificationsViewModel` | `ui/notifications/` | Notification management |

### 3. Repository Pattern

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

**All 19 Domain Repositories:**
Activities, Chat, Configurations, Courses, Events, Feedback, Life, Notifications, Personals, Progress, Ratings, Resources, Submissions, Surveys, Tags, Teams, User, Voices

**Utility Classes:**
- `RealmRepository` - Generic base repository
- `SubmissionsRepositoryExporter` - Export utilities

**Location**: `app/src/main/java/org/ole/planet/myplanet/repository/`

### 4. Dependency Injection (Hilt)

**Module Structure:**
- `NetworkModule.kt` - Provides Retrofit, OkHttp
- `DatabaseModule.kt` - Provides Realm instances
- `RepositoryModule.kt` - Binds repository interfaces to implementations
- `ServiceModule.kt` - Provides service dependencies
- `SharedPreferencesModule.kt` - Provides SharedPreferences

**Entry Points for Workers (11 entry point files):**
- `AutoSyncEntryPoint`, `ApiClientEntryPoint`, `ApiInterfaceEntryPoint`
- `ApplicationScopeEntryPoint`, `BroadcastServiceEntryPoint`, `DatabaseServiceEntryPoint`
- `RepositoryEntryPoint`, `RetryQueueEntryPoint`, `ServiceEntryPoint`
- `TeamsRepositoryEntryPoint`, `WorkerDependenciesEntryPoint`

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSyncEntryPoint {
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
- `StayOnlineWorker` - Keeps connection alive
- `RetryQueueWorker` - Retries failed operations (`services/retry/`)

**Services and Managers (20 root-level files):**
- `SyncManager` - Manual synchronization (`services/sync/`)
- `UploadManager` - File upload coordination (extends FileUploader)
- `UploadToShelfService` - Shelf upload operations
- `UploadCoordinator` - Upload orchestration (`services/upload/`)
- `AudioRecorder` - Audio recording
- `BroadcastService` - Service broadcasting
- `ConfigurationManager` - Configuration management
- `SharedPrefManager` - SharedPreferences management
- `UserSessionManager` - User session handling
- `ThemeManager` - App theming
- `FileUploader` - File upload utilities
- `DownloadService` - Background file download service (foreground service)
- `VoicesLabelManager` - Voice/discussion forum label management
- `ChallengePrompter` - Challenge prompt generation
- `NotificationActionReceiver` - Broadcast receiver for notification actions

**Sync Sub-package (`services/sync/` - 11 files):**
- `SyncManager`, `LoginSyncManager`, `TransactionSyncManager`
- `ImprovedSyncManager`, `RealtimeSyncManager`
- `AdaptiveBatchProcessor`, `StandardSyncStrategy`, `SyncStrategy`
- `ThreadSafeRealmManager`, `RealmConnectionPool`, `ServerUrlMapper`

**Upload Sub-package (`services/upload/` - 4 files):**
- `UploadCoordinator` - Central orchestration for all upload operations with batch processing and retry
- `UploadConfigs` - Configuration objects for different upload types (NewsActivities, Submissions, Photos, etc.)
- `UploadConfig` - Generic configuration template with batch size and Realm model binding
- `UploadResult` - Result wrapper with success/failure/empty states

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
- Uses `gradle/actions/setup-gradle@v5` with caching
- Build command: `./gradlew assemble${FLAVOR}Debug --parallel --max-workers=4`

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

**Database Operations:**
```kotlin
// Query
val courses = mRealm.where(RealmMyCourse::class.java)
    .equalTo("userId", userId)
    .findAll()

// Write
mRealm.executeTransactionAsync { realm ->
    realm.copyToRealmOrUpdate(course)
}

// Delete
mRealm.executeTransaction { realm ->
    realm.where(RealmMyCourse::class.java)
        .equalTo("_id", courseId)
        .findFirst()
        ?.deleteFromRealm()
}
```

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
            AutoSyncEntryPoint::class.java
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

1. **Create Layout**
   ```xml
   <!-- app/src/main/res/layout/activity_my_feature.xml -->
   <?xml version="1.0" encoding="utf-8"?>
   <androidx.constraintlayout.widget.ConstraintLayout
       xmlns:android="http://schemas.android.com/apk/res/android"
       android:layout_width="match_parent"
       android:layout_height="match_parent">

       <!-- UI components -->

   </androidx.constraintlayout.widget.ConstraintLayout>
   ```

2. **Create Activity/Fragment**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/ui/myfeature/MyFeatureActivity.kt
   package org.ole.planet.myplanet.ui.myfeature

   import android.os.Bundle
   import dagger.hilt.android.AndroidEntryPoint
   import org.ole.planet.myplanet.base.BaseActivity
   import org.ole.planet.myplanet.databinding.ActivityMyFeatureBinding

   @AndroidEntryPoint
   class MyFeatureActivity : BaseActivity() {
       private lateinit var binding: ActivityMyFeatureBinding

       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           binding = ActivityMyFeatureBinding.inflate(layoutInflater)
           setContentView(binding.root)

           setupUI()
       }

       private fun setupUI() {
           // Initialize UI components
       }
   }
   ```

3. **Register in Manifest**
   ```xml
   <!-- app/src/main/AndroidManifest.xml -->
   <activity
       android:name=".ui.myfeature.MyFeatureActivity"
       android:label="@string/my_feature_title"
       android:theme="@style/AppTheme" />
   ```

4. **Add Navigation**
   ```kotlin
   // From another activity/fragment
   val intent = Intent(context, MyFeatureActivity::class.java)
   startActivity(intent)
   ```

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

1. **Download Data**
   ```kotlin
   suspend fun syncFromServer() {
       val response = apiInterface.getData()
       if (response.isSuccessful) {
           response.body()?.let { data ->
               saveToRealm(data)
           }
       }
   }

   private fun saveToRealm(data: List<JsonObject>) {
       mRealm.executeTransaction { realm ->
           data.forEach { item ->
               RealmMyModel.insert(realm, item)
           }
       }
   }
   ```

2. **Upload Changes**
   ```kotlin
   suspend fun syncToServer() {
       val pendingChanges = mRealm.where(RealmMyModel::class.java)
           .equalTo("synced", false)
           .findAll()

       pendingChanges.forEach { item ->
           val response = apiInterface.postData(item.toJson())
           if (response.isSuccessful) {
               markAsSynced(item._id)
           }
       }
   }
   ```

### Adding Background Work

1. **Create Worker**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/services/MyWorker.kt
   class MyWorker(
       context: Context,
       params: WorkerParameters
   ) : CoroutineWorker(context, params) {

       override suspend fun doWork(): Result {
           return try {
               // Perform background task
               Result.success()
           } catch (e: Exception) {
               Result.retry()
           }
       }
   }
   ```

2. **Schedule Work**
   ```kotlin
   val workRequest = PeriodicWorkRequestBuilder<MyWorker>(
       1, TimeUnit.HOURS
   ).setConstraints(
       Constraints.Builder()
           .setRequiredNetworkType(NetworkType.CONNECTED)
           .build()
   ).build()

   WorkManager.getInstance(context)
       .enqueueUniquePeriodicWork(
           "MyWork",
           ExistingPeriodicWorkPolicy.REPLACE,
           workRequest
       )
   ```

---

## Testing Guidelines

### Current State
- No formal testing framework currently configured
- Manual testing on devices/emulators

### Recommended Testing Approach

**Unit Testing:**
```kotlin
// Add to app/build.gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.3.1'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2'

// Example unit test
class CourseRepositoryTest {
    @Test
    fun `syncCourses returns success when API call succeeds`() = runTest {
        // Arrange
        val mockApi = mock(ApiInterface::class.java)
        val repository = CourseRepositoryImpl(mockApi, mockDatabase)

        // Act
        val result = repository.syncCourses()

        // Assert
        assertTrue(result.isSuccess)
    }
}
```

**Instrumented Testing:**
```kotlin
// Add to app/build.gradle
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'

// Example instrumented test
@RunWith(AndroidJUnit4::class)
class LoginActivityTest {
    @Test
    fun loginButton_clickWithValidCredentials_navigatesToDashboard() {
        // Test UI interaction
    }
}
```

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
- Server URLs (use gradle.properties)
- User credentials

**Use gradle.properties for configuration:**
```properties
# gradle.properties (gitignored)
myplanet.server.url=https://example.org
myplanet.server.pin=1234
```

**Access in code:**
```kotlin
val serverUrl = BuildConfig.SERVER_URL
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
| Main entry point | `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt` | ~448 |
| REST API endpoints | `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt` | ~65 |
| Sync orchestration | `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` | ~1058 |
| Upload handling | `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` | ~770 |
| Upload orchestration | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` | ~309 |
| Team management | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` | ~1097 |
| Build configuration | `app/build.gradle` | ~250 |
| Dependency versions | `gradle/libs.versions.toml` | ~200 |

---

## Quick Command Reference

```bash
# Build Commands
./gradlew assembleDefaultDebug          # Build default debug APK
./gradlew assembleLiteDebug            # Build lite debug APK
./gradlew assembleDefaultRelease       # Build default release APK
./gradlew bundleDefaultRelease         # Build default release AAB
./gradlew installDefaultDebug          # Install default debug on device

# Clean Commands
./gradlew clean                        # Clean build artifacts
./gradlew clean build                  # Clean and rebuild

# Dependency Commands
./gradlew dependencies                 # List all dependencies
./gradlew app:dependencies             # List app module dependencies

# Diagnostic Commands
./gradlew build --scan                 # Build with build scan
./gradlew build --stacktrace          # Build with stack traces
./gradlew build --info                # Build with info logging

# Git Commands
git status                             # Check current state
git checkout -b claude/feature-id      # Create feature branch
git add .                              # Stage changes
git commit -m "message"                # Commit changes
git push -u origin claude/feature-id   # Push to remote
```

---

## Codebase Inventory Summary

### Source Files (394 total Kotlin files)

| Component | Files | Purpose |
|-----------|-------|---------|
| `model/` | 67 | Realm database models (40) + DTOs (27) |
| `repository/` | 38 | Data access abstraction (19 domains + utilities) |
| `ui/` | 147 | User interface across 28 feature packages |
| `services/` | 37 | Background tasks & managers (20 root + 3 sub-packages) |
| `di/` | 16 | Dependency injection (5 modules + 11 entry points) |
| `base/` | 12 | Reusable base classes |
| `callback/` | 34 | Event listeners and interfaces |
| `data/` | 8 | Data services, API, auth |
| `utils/` | 39 | Helper utilities |
| Root | 1 | MainApplication.kt |

### Resource Files

| Category | Count |
|----------|-------|
| Layout files (main) | 169 |
| Layout files (all variants) | 181 |
| Drawable files | 129 |
| Translation languages | 5 (ar, es, fr, ne, so) |
| String resources | ~1,194 lines |
| Menu files | 2 |
| XML config files | 3 |

### AndroidManifest Permissions (23 total)

**Network**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_NETWORK_STATE
**Device**: CAMERA, RECORD_AUDIO, WAKE_LOCK
**System**: PACKAGE_USAGE_STATS, REQUEST_INSTALL_PACKAGES (default flavor only)
**Notifications**: POST_NOTIFICATIONS, C2DM RECEIVE
**Other**: BLUETOOTH, FOREGROUND_SERVICE_DATA_SYNC, SYSTEM_ALERT_WINDOW

---

## Conclusion

This document provides a comprehensive guide for AI assistants working on myPlanet. Key principles:

1. **Understand the architecture** - Layered architecture with clear separation
2. **Follow conventions** - Consistent naming, patterns, and structure
3. **Use dependency injection** - Hilt for all dependency management
4. **Think offline-first** - All features should work offline when possible
5. **Leverage existing patterns** - Base classes, repositories, utilities
6. **Test thoroughly** - Build, offline mode, sync, multiple screen sizes
7. **Document changes** - Clear commit messages and code comments
8. **Security first** - Never hardcode secrets, use encryption

For questions or clarifications, refer to the Discord community or GitHub issues.

---

**Last Updated**: 2026-02-10
**Version**: 0.46.0
**Maintainer**: Open Learning Exchange

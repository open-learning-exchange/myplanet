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
- **Current Version**: 0.48.54 (versionCode: 4854)
- **Build System**: Gradle 9.1.0 with Android Gradle Plugin 9.1.0
- **License**: AGPL v3

### Build Flavors
- **default**: Full-featured version (includes `REQUEST_INSTALL_PACKAGES` permission, `LITE = false`)
- **lite**: Lightweight version with reduced features (`LITE = true` BuildConfig flag, `-lite` version suffix)

---

## Codebase Structure

### Directory Layout

```
myplanet/
├── .github/                    # CI/CD workflows and Dependabot config
│   └── workflows/
│       ├── build.yml          # Build workflow for all branches except master
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
│   │   │   │   ├── model/                   # Realm data models (40 Realm classes, 75 total)
│   │   │   │   ├── repository/              # Repository pattern implementations
│   │   │   │   ├── services/                # Background services and workers
│   │   │   │   ├── ui/                      # UI components (28+ packages)
│   │   │   │   └── utils/                   # Helper utilities
│   │   │   ├── res/                         # Android resources
│   │   │   │   ├── layout/                  # 170 layout files
│   │   │   │   ├── layout-night/            # Dark mode layout overrides
│   │   │   │   ├── layout-w600dp/           # Tablet layout overrides
│   │   │   │   ├── layout-large-land/       # Large landscape layouts
│   │   │   │   ├── layout-xlarge-land/      # Extra-large landscape layouts
│   │   │   │   ├── values/                  # Strings, colors, styles
│   │   │   │   ├── values-{lang}/           # Translations (ar, es, fr, ne, so)
│   │   │   │   ├── values-night/            # Dark theme values
│   │   │   │   ├── values-sw320dp/          # Small screen values
│   │   │   │   ├── values-sw400dp/          # Medium screen values
│   │   │   │   ├── values-sw600dp/          # Tablet values
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
| `data/` | Data access and API services | 7 | DatabaseService.kt, NetworkResult.kt, RealmMigrations.kt; sub-packages: `api/` (ApiInterface, ApiClient, ChatApiService, RetryInterceptor), `auth/` (AuthSessionUpdater) |
| `di/` | Hilt dependency injection | 18 | 6 modules (NetworkModule, DatabaseModule, RepositoryModule, ServiceModule, SharedPreferencesModule, DispatcherModule) + 12 entry points |
| `model/` | Realm database models and DTOs | 75 | 40 Realm models + 35 DTOs including ChatMessage, ChatRequest, ChatResponse, ChatShareTargets, CourseProgressData, Download, ServerAddress, User, TeamNotificationInfo, TaskNotificationResult |
| `repository/` | Repository pattern implementations | 42 | 21 repositories with Interface + Impl pairs + RealmRepository base + SubmissionsRepositoryExporter |
| `services/` | Background services and workers | 38 | 21 root-level + `sync/` (11), `upload/` (4), `retry/` (2) |
| `ui/` | User interface components | 153 | 28+ feature packages with 20 ViewModels (courses, resources, teams, chat, etc.) |
| `utils/` | Helper functions | 40 | NetworkUtils, ImageUtils, DialogUtils, FileUploader, AuthUtils, SecurePrefs, ANRWatchdog, EdgeToEdgeUtils, and 32 more |

### UI Sub-packages (28+ feature packages, 153 files)

| Package | Files | Key Components |
|---------|-------|----------------|
| `ui/calendar/` | 1 | CalendarFragment |
| `ui/chat/` | 6 | ChatDetailFragment, ChatHistoryFragment, ChatViewModel, ChatAdapter, ChatHistoryAdapter, ChatShareTargetAdapter |
| `ui/community/` | 6 | CommunityTabFragment, LeadersFragment, CommunityServicesFragment, CommunityPagerAdapter, CommunityLeadersAdapter, HomeCommunityDialogFragment |
| `ui/components/` | 5 | CustomSpinner, MarkdownDialogFragment, FragmentNavigator, CheckboxListView, CustomClickableSpan |
| `ui/courses/` | 15 | CourseDetailFragment, CourseDetailViewModel, TakeCourseFragment, ProgressViewModel, CourseProgressActivity, CourseProgressViewModel, CoursesFragment, CoursesAdapter, CoursesStepsAdapter, CoursesProgressAdapter, CoursesProgressFragment, CoursesPagerAdapter, InlineResourceAdapter, ProgressGridAdapter, CourseStepFragment |
| `ui/dashboard/` | 11 | DashboardActivity, DashboardViewModel, BellDashboardViewModel, BellDashboardFragment, DashboardElementActivity, DashboardPluginFragment, DashboardSurveysAdapter, ActivitiesFragment, AboutFragment, DisclaimerFragment, InactiveDashboardFragment |
| `ui/dictionary/` | 1 | DictionaryActivity |
| `ui/enterprises/` | 5 | EnterprisesViewModel, EnterprisesFinancesFragment, EnterprisesFinancesAdapter, EnterprisesReportsFragment, EnterprisesReportsAdapter |
| `ui/events/` | 2 | EventsDetailFragment, EventsAdapter |
| `ui/exam/` | 2 | ExamTakingFragment, UserInformationFragment |
| `ui/feedback/` | 6 | FeedbackFragment, FeedbackListFragment, FeedbackDetailActivity, FeedbackListViewModel, FeedbackDetailViewModel, FeedbackAdapter |
| `ui/health/` | 6 | MyHealthFragment, AddExaminationActivity, AddExaminationViewModel, AddHealthActivity, HealthExaminationAdapter, HealthUsersAdapter |
| `ui/life/` | 2 | LifeFragment, LifeAdapter |
| `ui/maps/` | 1 | OfflineMapsActivity |
| `ui/notifications/` | 3 | NotificationsFragment, NotificationsViewModel, NotificationsAdapter |
| `ui/onboarding/` | 2 | OnboardingActivity, OnboardingAdapter |
| `ui/personals/` | 2 | PersonalsFragment, PersonalsAdapter |
| `ui/ratings/` | 2 | RatingsFragment, RatingsViewModel |
| `ui/references/` | 2 | ReferencesFragment, ReferencesAdapter |
| `ui/resources/` | 8 | ResourcesFragment, AddResourceActivity, AddResourceFragment, CollectionsFragment, ResourceDetailFragment, ResourcesAdapter, ResourcesFilterFragment, ResourcesTagsAdapter |
| `ui/settings/` | 1 | SettingsActivity |
| `ui/submissions/` | 8 | SubmissionsFragment, SubmissionListFragment, SubmissionDetailFragment, SubmissionViewModel, SubmissionDetailViewModel, SubmissionsAdapter, SubmissionsListAdapter, QuestionAnswerAdapter |
| `ui/surveys/` | 5 | SurveyFragment, SendSurveyFragment, SurveysAdapter, SurveysViewModel, SurveyFormState |
| `ui/sync/` | 8 | LoginActivity, SyncActivity, ProcessUserDataActivity, GuestLoginExtensions, RealtimeSyncMixin, ServerAddressAdapter, ServerDialogExtensions, SyncConfigurationCoordinator |
| `ui/teams/` | 9 | TeamFragment, TeamDetailFragment, TeamViewModel, TeamsAdapter, TeamsSelectionAdapter, PlanFragment, TeamCalendarFragment, TeamPageConfig, TeamPagerAdapter |
| `ui/teams/courses/` | 2 | TeamCoursesFragment, TeamCoursesAdapter |
| `ui/teams/members/` | 6 | MembersFragment, MembersDetailFragment, MembersAdapter, RequestsFragment, RequestsViewModel, RequestsAdapter |
| `ui/teams/resources/` | 2 | TeamResourcesFragment, TeamResourcesAdapter |
| `ui/teams/tasks/` | 2 | TeamsTasksFragment, TeamsTasksAdapter |
| `ui/teams/voices/` | 1 | TeamsVoicesFragment |
| `ui/user/` | 7 | UserProfileFragment, UserProfileViewModel, BecomeMemberActivity, AchievementFragment, EditAchievementFragment, UserArrayAdapter, UsersAdapter |
| `ui/viewer/` | 8 | ImageViewerActivity, VideoViewerActivity, AudioPlayerActivity, PDFReaderActivity, WebViewActivity, MarkdownViewerActivity, TextFileViewerActivity, CSVViewerActivity |
| `ui/voices/` | 6 | VoicesFragment, NewsViewModel, ReplyActivity, ReplyViewModel, VoicesAdapter, VoicesActions |

### Critical Files to Understand

1. **`MainApplication.kt`** (~449 lines)
   - Application initialization with Hilt DI
   - WorkManager scheduling (AutoSyncWorker, StayOnlineWorker, TaskNotificationWorker, NetworkMonitorWorker, RetryQueueWorker)
   - Server reachability checking with alternative URL mapping
   - Theme/locale management, ANR watchdog, uncaught exception handling
   - Location: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`

2. **`SyncManager.kt`** (~1022 lines)
   - Orchestrates data synchronization with server via StateFlow-based state management
   - Integrates with ImprovedSyncManager, TransactionSyncManager, RealtimeSyncManager
   - Semaphore-based concurrency control, adaptive batch processing
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`

3. **`UploadManager.kt`** (~883 lines)
   - File and data uploads with batch processing (BATCH_SIZE = 50)
   - Integrates with UploadCoordinator for orchestrated uploads
   - Handles activities, submissions, photos, news uploads
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`

4. **`TeamsRepositoryImpl.kt`** (~1243 lines)
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
| **Build System** | Gradle | 9.1.0 | Build automation |
| **Build Plugin** | Android Gradle Plugin | 9.1.0 | Android build tooling |
| **DI Framework** | Dagger Hilt | 2.59.2 | Dependency injection |
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
| **Charts** | MPAndroidChart | v3.1.0 | Chart rendering |
| **GIF** | android-gif-drawable | 1.2.31 | GIF image support |

### Build Configuration

**Gradle Plugins:**
- `com.android.application`
- `com.android.legacy-kapt` (Annotation processing)
- `com.google.devtools.ksp` (Symbol processing)
- `com.google.dagger.hilt.android`
- `realm-android`

**Compiler Settings:**
- Java Compatibility: 17
- Kotlin JVM Target: 17
- View Binding: Enabled
- Data Binding: Enabled
- BuildConfig: Enabled
- Kotlin compiler arg: `-Xannotation-default-target=param-property`

**Pre-configured Server URLs (via `gradle.properties`):**
The build supports many pre-configured server instances via BuildConfig fields:
`PLANET_LEARNING`, `PLANET_GUATEMALA`, `PLANET_SANPABLO`, `PLANET_SANPABLO_CLONE`, `PLANET_EARTH`, `PLANET_SOMALIA`, `PLANET_VI`, `PLANET_XELA`, `PLANET_URIUR`, `PLANET_URIUR_CLONE`, `PLANET_RUIRU`, `PLANET_EMBAKASI`, `PLANET_EMBAKASI_CLONE`, `PLANET_CAMBRIDGE`

Each has a `_URL` and `_PIN` variant. Leave empty in local development; set in CI via GitHub Secrets.

---

## Architecture Patterns

### 1. Layered Architecture

```
┌─────────────────────────────────────────┐
│     UI Layer (Activities/Fragments)     │
│  - User interaction & view binding      │
│  - Lifecycle management                 │
│  - 20 ViewModels for state management   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Repository Layer (21 domains)       │
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

The UI layer uses ViewModels for state management:

| ViewModel | Package | Purpose |
|-----------|---------|---------|
| `ChatViewModel` | `ui/chat/` | Chat message state and AI interactions |
| `TeamViewModel` | `ui/teams/` | Team data and operations |
| `RequestsViewModel` | `ui/teams/members/` | Team join requests |
| `DashboardViewModel` | `ui/dashboard/` | Dashboard data aggregation |
| `BellDashboardViewModel` | `ui/dashboard/` | Bell community dashboard |
| `CourseDetailViewModel` | `ui/courses/` | Course detail data |
| `CourseProgressViewModel` | `ui/courses/` | Course progress tracking |
| `ProgressViewModel` | `ui/courses/` | Progress grid display |
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
| `AddExaminationViewModel` | `ui/health/` | Health examination form |
| `SurveysViewModel` | `ui/surveys/` | Survey state management |

### 3. Repository Pattern

**Convention**: Each data domain has an interface and implementation.

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

**All 21 Domain Repositories:**
Activities, Chat, Community, Configurations, Courses, Events, Feedback, Health, Life, Notifications, Personals, Progress, Ratings, Resources, Submissions, Surveys, Tags, Teams, User, Voices

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
- `DispatcherModule.kt` - Provides coroutine dispatchers via `DispatcherProvider`

**Entry Points for Workers (12 entry point files):**
- `AutoSyncEntryPoint`, `ApiClientEntryPoint`, `ApiInterfaceEntryPoint`
- `ApplicationScopeEntryPoint`, `BroadcastServiceEntryPoint`, `DatabaseServiceEntryPoint`
- `RepositoryEntryPoint`, `RetryQueueEntryPoint`, `ServerUrlMapperEntryPoint`
- `ServiceEntryPoint`, `TeamsRepositoryEntryPoint`, `WorkerDependenciesEntryPoint`

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

**Services and Managers (21 root-level files):**
- `SyncManager` - Manual synchronization (`services/sync/`)
- `UploadManager` - File upload coordination (extends FileUploader)
- `UploadToShelfService` - Shelf upload operations
- `UploadCoordinator` - Upload orchestration (`services/upload/`)
- `ResourceDownloadCoordinator` - Resource download orchestration
- `SubmissionUploadExecutor` - Dedicated submission upload execution
- `AudioRecorder` - Audio recording
- `BroadcastService` - Service broadcasting
- `ChallengePrompter` - Challenge prompt generation
- `SharedPrefManager` - SharedPreferences management
- `UserSessionManager` - User session handling
- `ThemeManager` - App theming
- `FileUploader` - File upload utilities
- `DownloadService` - Background file download service (foreground service)
- `VoicesLabelManager` - Voice/discussion forum label management
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
- Build command: `./gradlew assemble${FLAVOR}Debug --configuration-cache-problems=warn --warning-mode all --stacktrace --parallel --max-workers=4`

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
- Extensions: `*Extensions.kt` (e.g., `GuestLoginExtensions.kt`, `ServerDialogExtensions.kt`)
- Mixins: `*Mixin.kt` (e.g., `RealtimeSyncMixin.kt`)
- Coordinators: `*Coordinator.kt` (e.g., `SyncConfigurationCoordinator.kt`, `ResourceDownloadCoordinator.kt`)
- Executors: `*Executor.kt` (e.g., `SubmissionUploadExecutor.kt`)

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

**Coroutine Dispatchers:**
Use the injected `DispatcherProvider` (from `DispatcherModule`) rather than hardcoded dispatchers for testability:
```kotlin
class MyRepositoryImpl @Inject constructor(
    private val dispatchers: DispatcherProvider
) {
    suspend fun doWork() = withContext(dispatchers.io) { ... }
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

1. Create Realm model in `model/` extending `RealmObject` with `@PrimaryKey` and companion `insert()` method
2. Add API endpoint in `ApiInterface.kt`
3. Create repository interface + implementation in `repository/`
4. Bind in `RepositoryModule.kt` using `@Binds`

### Adding a New Screen

1. Create layout XML in `res/layout/` (activity_*.xml or fragment_*.xml)
2. Create `@AndroidEntryPoint` Activity/Fragment in `ui/<feature>/` using view binding
3. Register activity in `AndroidManifest.xml`
4. Add navigation via `Intent` from calling code

### Adding a New API Endpoint

1. Add method to `ApiInterface.kt` with Retrofit annotations (`@GET`, `@POST`, etc.)
2. Call from repository implementation using `withContext(Dispatchers.IO)`

### Implementing Offline Sync

1. Download: Fetch from API, save to Realm via `executeTransaction`
2. Upload: Query unsynced items from Realm, POST to server, mark as synced

### Adding Background Work

1. Create `CoroutineWorker` subclass in `services/`
2. Schedule via `WorkManager.enqueueUniquePeriodicWork()` with appropriate constraints
3. Use `EntryPointAccessors` for Hilt dependency injection in workers

---

## Testing Guidelines

- No formal testing framework currently configured; manual testing on devices/emulators
- When making changes, verify: app builds (`./gradlew assembleDefaultDebug`), offline mode works, sync works, UI renders on different screen sizes, dark theme works, translations display correctly, permissions requested appropriately

---

## Security Considerations

- **Never hardcode** API keys, passwords, server URLs, or credentials. Use `gradle.properties` (gitignored) and access via `BuildConfig`
- **Network security config**: `app/src/main/res/xml/network_security_config.xml`. Prefer HTTPS
- **Encryption**: Use `EncryptedSharedPreferences` for sensitive data, `Sha256Utils` for password hashing (Tink library)
- **Runtime permissions**: Check with `ContextCompat.checkSelfPermission`, request via `ActivityCompat.requestPermissions`
- **ProGuard/R8**: Currently `minifyEnabled = false` for both debug and release

---

## Troubleshooting

- **Gradle sync failed**: `./gradlew clean && ./gradlew build --refresh-dependencies`
- **KAPT/KSP errors**: `./gradlew clean && rm -rf .gradle/ && ./gradlew build`
- **Realm migration errors**: Increment schema version or use `deleteRealmIfMigrationNeeded()` (dev only)
- **Hilt not found**: Ensure `@AndroidEntryPoint`, verify module provides dependency, check injection point
- **Network requests fail**: Check `NetworkUtils.isNetworkAvailable()`, add `HttpLoggingInterceptor`
- **Realm database locked**: Ensure realm is closed in `onDestroy()`
- **OOM with images**: Use `Glide.with(context).load(url).override(800, 600)`
- **Git push 403**: Branch must start with `claude/` and end with session ID; use `-u` flag
- **Merge conflicts**: `git fetch origin && git rebase origin/master`, resolve, `git add . && git rebase --continue`

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
| Main entry point | `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt` | ~449 |
| REST API endpoints | `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt` | ~65 |
| Sync orchestration | `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` | ~1022 |
| Upload handling | `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` | ~883 |
| Upload orchestration | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` | ~309 |
| Team management | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` | ~1243 |
| Build configuration | `app/build.gradle` | ~210 |
| Dependency versions | `gradle/libs.versions.toml` | ~105 |

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
./gradlew assembleDefaultDebug --configuration-cache-problems=warn  # Warn on cache issues

# Git Commands
git status                             # Check current state
git checkout -b claude/feature-id      # Create feature branch
git add .                              # Stage changes
git commit -m "message"                # Commit changes
git push -u origin claude/feature-id   # Push to remote
```

---

## Key Principles

1. **Layered architecture** with clear separation of concerns
2. **Hilt DI** for all dependency management
3. **Offline-first** - all features should work without connectivity
4. **Follow existing patterns** - base classes, repositories, naming conventions
5. **Security first** - never hardcode secrets, use encryption

---

**Last Updated**: 2026-03-11
**Version**: 0.48.54
**Maintainer**: Open Learning Exchange

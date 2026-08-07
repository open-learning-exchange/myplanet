# CLAUDE.md - AI Assistant Guide for myPlanet

## Project Overview

**myPlanet** is an Android mobile application serving as an offline extension of the Open Learning Exchange's Planet Learning Management System. It enables learners to access educational resources (books, videos, courses) without continuous internet connectivity.

### Key Characteristics
- **Primary Language**: Kotlin (100% — no Java sources remain)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16); **Compile SDK**: 37
- **Current Version**: 0.63.42 (versionCode: 6342)
- **Build System**: Gradle 9.6.1 with Android Gradle Plugin 9.3.1
- **Local Database**: Room (AndroidX) 2.8.4 — the only local persistence store
- **License**: AGPL v3

### Build Flavors
- **default**: Full-featured version
- **lite**: Lightweight version with reduced features (removes `REQUEST_INSTALL_PACKAGES`; `-lite` version-name suffix)

### Documentation Map

| Document | Read it when… |
|----------|---------------|
| `CLAUDE.md` (this file) | You need the codebase layout, architecture, build/CI facts, or task recipes |
| `docs/DOMAIN_MODEL.md` | You need to understand the learning domain — roles, courses, teams, surveys, sync concepts |
| `docs/CODE_STYLE_GUIDE.md` | You're writing code — naming, imports, coroutines, Room, Hilt, UI conventions |
| `docs/TESTING.md` | You're writing or fixing tests — patterns to copy per layer |

---

## Codebase Structure

### Directory Layout

```
myplanet/
├── .github/                    # CI/CD workflows and Dependabot config
│   └── workflows/
│       ├── automerge.yml      # Manually-dispatched queue drainer for `automerge`-labelled PRs
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
│   │   │   │   ├── data/                    # Data services, API, and Room (room/, api/, auth/)
│   │   │   │   ├── di/                      # Dependency injection modules
│   │   │   │   ├── model/                   # Room @Entity models + DTOs (92 files)
│   │   │   │   ├── repository/              # Repository pattern implementations
│   │   │   │   ├── services/                # Background services and workers
│   │   │   │   ├── ui/                      # UI components (28 packages)
│   │   │   │   └── utils/                   # Helper utilities
│   │   │   ├── res/                         # Android resources
│   │   │   │   ├── layout/                  # 181 layout files
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
| `base/` | Base classes for common functionality | 13 | BaseActivity, BaseRecyclerFragment, BasePermissionActivity, BaseContainerFragment, BaseDashboardFragment, BaseResourceFragment, BaseTeamFragment, BaseExamFragment, BaseMemberFragment, BaseDialogFragment, BaseVoicesFragment, BaseRecyclerParentFragment |
| `callback/` | Event listeners and interfaces | 28 | OnLibraryItemSelectedListener, OnSyncListener, OnTeamUpdateListener, OnChatItemClickListener, OnNewsItemClickListener, and more |
| `data/` | Data access, Room persistence, and API | 40 | DatabaseService.kt, NetworkResult.kt; `room/` (AppDatabase, Converters, 37 DAO interfaces in 30 files — several share `LegacyEntityDaos.kt`), `api/` (ApiInterface, ApiClient, ChatApiService, RetryInterceptor), `auth/` (AuthSessionUpdater) |
| `di/` | Hilt dependency injection | 10 | Modules (NetworkModule, DatabaseModule, RoomModule, RepositoryModule, ServiceModule, SharedPreferencesModule, DispatcherModule, TimeModule) + entry points (CoreDependenciesEntryPoint, ServiceDependenciesEntryPoint) |
| `model/` | Room `@Entity` models and DTOs | 92 | 37 `@Entity` classes (MyCourse, MyLibrary, News, Submission, TeamTask, UserEntity, …) + DTOs (ChatMessage, ChatRequest, ChatResponse, CourseProgressData, Download, ServerAddress, User) |
| `repository/` | Repository pattern implementations | 50 | 23 domain Interface + Impl pairs + sync-facing interfaces (SyncRepository, TeamsSyncRepository, UserSyncRepository) + SubmissionsRepositoryExporter |
| `services/` | Background services and workers | 39 | 22 root-level + `sync/` (7), `upload/` (8), `retry/` (2) |
| `ui/` | User interface components | 183 | 28 feature packages with 16+ ViewModels (courses, resources, teams, chat, etc.) |
| `utils/` | Helper functions | 46 | NetworkUtils, ImageUtils, DialogUtils, FileUploader, AuthUtils, SecurePrefs, ANRWatchdog, and more |

### UI Sub-packages (28 feature packages, 183 files)

| Package | Files | Key Components |
|---------|-------|----------------|
| `ui/calendar/` | 1 | CalendarFragment |
| `ui/chat/` | 8 | ChatDetailFragment, ChatHistoryFragment, ChatViewModel |
| `ui/community/` | 6 | CommunityTabFragment, LeadersFragment |
| `ui/components/` | 5 | CustomSpinner, MarkdownDialogFragment, FragmentNavigator |
| `ui/courses/` | 22 | CourseDetailFragment, TakeCourseFragment, ProgressViewModel |
| `ui/dashboard/` | 12 | DashboardActivity, DashboardViewModel, BellDashboardViewModel |
| `ui/dictionary/` | 1 | DictionaryActivity |
| `ui/enterprises/` | 6 | EnterprisesViewModel, FinancesFragment, ReportsFragment |
| `ui/events/` | 4 | EventsDetailFragment, EventsAdapter |
| `ui/exam/` | 2 | ExamTakingFragment, UserInformationFragment |
| `ui/feedback/` | 7 | FeedbackFragment, FeedbackDetailActivity, FeedbackListViewModel |
| `ui/health/` | 7 | MyHealthFragment, AddExaminationActivity |
| `ui/life/` | 2 | LifeFragment, LifeAdapter |
| `ui/maps/` | 1 | OfflineMapsActivity |
| `ui/notifications/` | 3 | NotificationsFragment, NotificationsViewModel |
| `ui/onboarding/` | 2 | OnboardingActivity, OnboardingAdapter |
| `ui/personals/` | 3 | PersonalsFragment, PersonalsAdapter |
| `ui/ratings/` | 2 | RatingsFragment, RatingsViewModel |
| `ui/references/` | 2 | ReferencesFragment, ReferencesAdapter |
| `ui/resources/` | 10 | ResourcesFragment, AddResourceFragment, CollectionsFragment |
| `ui/settings/` | 4 | SettingsActivity, SettingsViewModel, StorageBreakdownFragment, StorageCategoryDetailFragment |
| `ui/submissions/` | 10 | SubmissionsFragment, SubmissionViewModel |
| `ui/surveys/` | 5 | SurveyFragment, SendSurveyFragment |
| `ui/sync/` | 10 | LoginActivity, LoginViewModel, SyncActivity, SyncConfigurationCoordinator, ProcessUserDataActivity |
| `ui/teams/` | 25 | TeamFragment, TeamDetailFragment, TeamViewModel (largest UI package) |
| `ui/user/` | 10 | UserProfileFragment, UserProfileViewModel, BecomeMemberActivity |
| `ui/viewer/` | 4 | ResourceViewerActivity, ResourceViewerFragment, ResourceViewerViewModel, WebViewActivity (all media types render through the shared resource viewer) |
| `ui/voices/` | 9 | VoicesFragment, NewsViewModel, ReplyActivity |

### Critical Files to Understand

1. **`MainApplication.kt`** (~537 lines)
   - Application initialization with Hilt DI
   - WorkManager scheduling (AutoSyncWorker, TaskNotificationWorker, NetworkMonitorWorker, RetryQueueWorker)
   - Server reachability checking with alternative URL mapping
   - Theme/locale management, ANR watchdog, uncaught exception handling
   - Location: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`

2. **`AppDatabase.kt`** (~170 lines) — the Room database
   - `@Database` with 37 entities, `version = 6`, `@TypeConverters(Converters::class)`
   - Declares all 30+ DAO accessors; provisioned by `RoomModule` with a **drop-and-resync** (`fallbackToDestructiveMigration`) strategy — no hand-written migrations; data is re-pulled from CouchDB on first launch after a schema bump
   - Location: `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt`

3. **`SyncManager.kt`** (~691 lines)
   - Orchestrates data synchronization with server via StateFlow-based state management (`SyncStatus` Idle/Syncing/Success/Error)
   - Delegates per-table pulls to TransactionSyncManager; notifies UI via RealtimeSyncManager's SharedFlow; batch sizing via AdaptiveBatchProcessor
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`

4. **`UploadManager.kt`** (~615 lines)
   - File and data uploads with batch processing (BATCH_SIZE = 50)
   - Integrates with UploadCoordinator for orchestrated uploads
   - Handles activities, submissions, photos, news uploads
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`

5. **`TeamsRepositoryImpl.kt`** (~1437 lines — largest file; candidate for splitting by responsibility)
   - Team management with reactive Flow-based queries
   - Team creation, task management, membership roles
   - Location: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`

6. **`ApiInterface.kt`** (~65 lines)
   - All REST API endpoint definitions (file downloads/uploads, document CRUD, version checking, health access, AI/chat endpoints)
   - Location: `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt`

---

## Technology Stack

### Core Technologies

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Kotlin | 2.4.10 | Primary development language |
| **Build System** | Gradle | 9.6.1 | Build automation |
| **Build Plugin** | Android Gradle Plugin | 9.3.1 | Android build tooling |
| **DI Framework** | Dagger Hilt | 2.60.1 | Dependency injection |
| **Database** | Room (AndroidX) | 2.8.4 | Local SQLite object database |
| **Networking** | Retrofit | 3.0.0 | REST API client |
| **HTTP Client** | OkHttp | 5.4.0 | HTTP communication |
| **JSON** | Gson | 2.14.0 | JSON serialization |
| **Async** | Kotlin Coroutines | 1.11.0 | Asynchronous programming |
| **Background Tasks** | AndroidX Work | 2.11.2 | Background job scheduling |
| **UI Framework** | Material Design 3 | 1.14.0 | UI components |
| **Image Loading** | Glide | 5.0.9 | Image loading and caching |
| **Media Playback** | Media3 (ExoPlayer) | 1.10.1 | Audio/video playback |
| **Markdown** | Markwon | 4.6.2 | Markdown rendering |
| **Maps** | OSMDroid | 6.1.20 | OpenStreetMap integration |
| **Encryption** | Tink | 1.23.0 | Cryptographic operations |
| **Serialization** | Kotlin Serialization | 1.11.0 | Kotlin-native serialization |
| **CSV** | OpenCSV | 5.12.0 | CSV file parsing |

### Build Configuration

**Gradle Plugins** (declared in `app/build.gradle` — only these three):
- `com.android.application`
- `com.google.devtools.ksp` — the **only** annotation-processing path (`kapt` was removed entirely); Room, Glide, and all Hilt compilers run through KSP (`ksp`/`kspTest`/`kspAndroidTest`)
- `com.google.dagger.hilt.android`

Kotlin itself is applied via AGP's built-in Kotlin support (no `kotlin-android` plugin alias); `kotlin-gradle-plugin` and `kotlin-serialization` sit on the root buildscript classpath.

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
Data Sources (Room local DB via DAOs, REST API, SharedPreferences)
```

### 2. Repository Pattern

**Convention**: Each data domain has an interface and implementation. Implementations inject the **Room DAOs** they need (plus `ApiInterface`, `DispatcherProvider`, other repositories as needed) and return plain `@Entity`/data-class instances. `DatabaseService` still exists (`data/DatabaseService.kt`) but is essentially vestigial — no repository injects it anymore; multi-DAO atomic work uses Room's `withTransaction` on the `AppDatabase`.

```kotlin
// Real example — repository/CommunityRepositoryImpl.kt
class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao,
    private val meetupDao: MeetupDao
) : CommunityRepository {
    override suspend fun getAllSorted(): List<Community> = communityDao.getAllSorted()
}

// Dispatcher discipline — repository/DownloadRepositoryImpl.kt
class DownloadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider
) : DownloadRepository {
    override suspend fun downloadFileResponse(url: String, authHeader: String): DownloadResult =
        withContext(dispatcherProvider.io) { ... }
}
```

**All 23 Domain Repositories:**
Activities, Chat, Community, Configurations, Courses, Download, Events, Feedback, Health, Life, Notifications, Personals, Progress, Ratings, Resources, Retry, Submissions, Surveys, Tags, Teams, Upload, User, Voices

**Sync-facing interfaces & utilities:**
- `SyncRepository`, `TeamsSyncRepository`, `UserSyncRepository` - narrow interfaces the sync managers depend on
- `SubmissionsRepositoryExporter` - Export utilities

There is no generic base repository; each implementation talks to its Room DAO(s) directly.

**Location**: `app/src/main/java/org/ole/planet/myplanet/repository/`

### 3. Dependency Injection (Hilt)

**Module Structure (8 modules):**
- `NetworkModule.kt` - Provides Retrofit, OkHttp
- `RoomModule.kt` - Builds the `AppDatabase` (with `fallbackToDestructiveMigration`) and provides every DAO
- `DatabaseModule.kt` - Provides `DatabaseService` (wraps `AppDatabase` + dispatcher helpers)
- `RepositoryModule.kt` - Binds repository interfaces to implementations
- `ServiceModule.kt` - Provides service dependencies
- `SharedPreferencesModule.kt` - Provides SharedPreferences
- `DispatcherModule.kt` - Provides coroutine dispatchers and `@ApplicationScope`
- `TimeModule.kt` - Provides time/clock abstractions

**Entry Points for Workers (2 entry point files):** `CoreDependenciesEntryPoint`, `ServiceDependenciesEntryPoint`. Workers can't use constructor injection, so they fetch dependencies via `EntryPointAccessors.fromApplication(applicationContext, CoreDependenciesEntryPoint::class.java)`.

**Location**: `app/src/main/java/org/ole/planet/myplanet/di/`

### 4. Base Classes for Code Reuse (13 classes)

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

### 5. Background Processing

**AndroidX Work for Scheduled Tasks:**
- `AutoSyncWorker` - Periodic data synchronization
- `NetworkMonitorWorker` - Network state monitoring
- `ServerReachabilityWorker` - Server availability checking
- `TaskNotificationWorker` - Task deadline notifications
- `DownloadWorker` - Background file downloads
- `FreeSpaceWorker` - Disk space monitoring
- `UserDataWorker` - Background processing of pulled user data
- `HeavyTableSyncWorker` - Large-table background sync (`services/sync/`)
- `RetryQueueWorker` - Retries failed operations (`services/retry/`)

**Services and Managers (22 root-level files):**
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
- `SyncManager` (~691) - Orchestrates sync via StateFlow; the entry point for full syncs
- `TransactionSyncManager` (~519) - Per-table paginated pulls from CouchDB with checkpoint/resume
- `LoginSyncManager` (~195) - Sync triggered around the login flow
- `ServerUrlMapper` (~116) - Maps primary server URLs to alternative/clone URLs
- `HeavyTableSyncWorker` (~66) - WorkManager worker for large-table background sync
- `AdaptiveBatchProcessor` (~37) - Batch-size tuning used by SyncManager
- `RealtimeSyncManager` (~27) - SharedFlow of `TableDataUpdate` events; UI collects `dataUpdateFlow` (via `RealtimeSyncHelper`/`collectWhenStarted`)

**Upload Sub-package (`services/upload/` - 8 files):**
- `UploadCoordinator` - Central orchestration for all upload operations with batch processing and retry
- `UploadConfigs` - Configuration objects for different upload types (NewsActivities, Submissions, Photos, etc.)
- `UploadConfig` - Generic configuration template with batch size and model binding
- `RoomUploadConfig` - Room-DAO-backed upload configuration
- `UploadResult` - Result wrapper with success/failure/empty states
- `UploadConstants` - Shared upload constants
- `PhotoUploader`, `AchievementUploader` - Type-specific uploaders

**Retry Sub-package (`services/retry/` - 2 files):**
- `RetryQueue` - Queue-based retry mechanism for failed operations
- `RetryQueueWorker` - Background worker for processing retries

**Location**: `app/src/main/java/org/ole/planet/myplanet/services/`

---

## Development Workflows

### Building and Running

```bash
# Build debug APK (default / lite flavor)
./gradlew assembleDefaultDebug
./gradlew assembleLiteDebug

# Build release
./gradlew assembleDefaultRelease bundleDefaultRelease

# Install and run on a device/emulator
./gradlew installDefaultDebug && adb shell am start -n org.ole.planet.myplanet/.ui.onboarding.OnboardingActivity
```

### Branch Strategy

**Important**: Always work on branches starting with `claude/` and matching the session ID format. Always use `-u` on the first push:

```bash
git checkout -b claude/feature-name-sessionid
# ...develop, commit with descriptive messages (fix:/feat:/refactor: prefixes)...
git push -u origin claude/feature-name-sessionid
```

See `docs/CODE_STYLE_GUIDE.md` → "Branch & PR Standards" for commit-message and PR conventions.

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

**Automerge Workflow** (`.github/workflows/automerge.yml`)
- Manually dispatched (`workflow_dispatch`) queue drainer for PRs labelled `automerge`
- For each labelled PR: merges the base branch in, bumps the version, waits for build + test to pass, then squash-merges
- Logic lives in `.github/scripts/automerge.sh`; requires `AUTOMERGE_TOKEN` (the default `GITHUB_TOKEN` can't push to the protected base branch)

**Dependabot** (`.github/dependabot.yml`)
- Daily checks for GitHub Actions updates (max 10 open PRs)
- Daily checks for Gradle dependency updates (max 15 open PRs)

**PR Reviews — CodeRabbit**
- On any PR, commenting `@coderabbitai review` summons CodeRabbit as a third review option (besides human and Claude review) — the token-cheap first pass for nits and obvious bugs
- Also: `@coderabbitai full review` (from scratch), `@coderabbitai resolve` (resolve its comments); see `docs/CODE_STYLE_GUIDE.md` → "PR Reviews — Three Options"

**AI Agent Roster** (verified against PR history 2026-08-07)

| Agent | GitHub identity | Footprint |
|-------|-----------------|-----------|
| Claude Code | human account, `claude/**` branches | primary AI contributor; automerge/docs/refactor PRs |
| CodeRabbit | `coderabbitai[bot]` | auto-reviews every PR; `@coderabbitai` commands |
| OpenAI Codex | human account via connector; `codex` label, `*-codex/*` branches, "Codex Task" links in PR bodies | feature/refactor PRs |
| Google Jules | `google-labs-jules[bot]`; `jules-*`/`jules/**` branches | 20+ bot-authored PRs |
| Copilot coding agent | `Copilot` (`copilot-swe-agent`); `copilot/**` branches | 36+ bot-authored PRs (incl. the Realm→Room migration PR #14850) |
| Devin | `devin-ai-integration[bot]` | occasional PRs |
| OpenHands (All Hands) | PRs under the requesting user's account, "created by an AI agent (OpenHands)" footer | occasional PRs; mention handle is `@openhands` |
| Dependabot | `dependabot[bot]` | daily dependency PRs |

### Adding New Features

1. **Identify the Layer**
   - UI change? → `ui/` package
   - Data model? → `model/` package
   - Business logic? → `repository/` or `services/`
   - Network API? → `data/api/ApiInterface.kt`

2. **Create Necessary Components**
   - Model class (Room `@Entity` + a DAO if persistent)
   - Repository interface + implementation
   - UI components (Activity/Fragment)
   - Layout XML files

3. **Wire everything up**
   - New dependencies go in `gradle/libs.versions.toml`, referenced from `app/build.gradle`
   - Register new activities (and permissions) in `AndroidManifest.xml`
   - Provide/bind new dependencies in the appropriate `di/` module (`RepositoryModule` for repositories)

---

## Key Conventions

> Full coding conventions live in **`docs/CODE_STYLE_GUIDE.md`** (naming, imports, null safety, coroutines, Hilt, UI, resources, error handling). The rules below are the project-specific ones that most often trip up newcomers.

**File Naming:**
- Activities/Fragments/ViewModels/Adapters/Workers: `*Activity.kt`, `*Fragment.kt`, `*ViewModel.kt`, `*Adapter.kt`, `*Worker.kt`
- Repositories: `*Repository.kt` (interface) and `*RepositoryImpl.kt`
- Room DAOs: `*Dao.kt` in `data/room/dao/` (e.g., `RatingDao.kt`; the legacy-entity DAOs like `CourseDao` share `LegacyEntityDaos.kt`)
- Models/Entities: plain names in `model/` (e.g., `MyCourse.kt`, `Submission.kt` — the old `Realm*` prefix is gone)
- Layouts: `activity_*.xml`, `fragment_*.xml`, `row_*.xml` / `item_*.xml`, `dialog_*.xml`

### Room Database Conventions

> All local persistence goes through Room — the `AppDatabase` (`data/room/AppDatabase.kt`), its DAOs (`data/room/dao/`), and `Converters` (`data/room/Converters.kt`). There is no other local store, so reach for DAOs (or `DatabaseService`), never a raw SQLite or third-party-DB API.

**Entity (model) Classes:**
```kotlin
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses", indices = [Index("courseId"), Index("_id")])
open class MyCourse(
    @PrimaryKey @JvmField var id: String = "",
    @ColumnInfo(name = "_id") var _id: String? = null,
    var courseTitle: String? = null,
    var description: String? = null,
    var createdDate: Long = 0,
    // ... other fields
) {
    @Ignore var courseSteps: MutableList<CourseStep>? = null   // non-persisted helper
}
```

**Key Points:**
- Entities live in `model/` and are annotated with `@Entity(tableName = "...")` (e.g. `MyCourse`, `MyLibrary`, `News`, `Submission`, `UserEntity`, `TeamTask`).
- Use `@PrimaryKey` for the key; add `@Index`/`indices` for frequently queried columns.
- `@ColumnInfo(name = "_id")` maps the CouchDB `_id`/`_rev` fields to Kotlin-friendly property names.
- Non-persisted, computed, or in-memory-only fields use `@Ignore` (and often `@Transient`).
- Multi-valued fields (`List<String>`, nested objects, `Date`) are persisted via `@TypeConverters(Converters::class)` — the converters serialize them to JSON strings with Gson.

**Database Operations — use DAOs (preferred pattern):**

Repositories inject the **DAO(s)** they need directly (provided by `RoomModule`) and call `suspend` DAO functions. Reactive queries return `Flow<…>` (non-suspend, per Room's requirement) — 8 of the 30 DAO files expose them, named either `observe*` (e.g. `CourseDao.observeAll()`) or `*Flow` (e.g. `NewsDao.getTopLevelFlow()`).

```kotlin
// Real DAO examples
// data/room/dao/LegacyEntityDaos.kt — several DAOs share this file, @Upsert style
@Dao
interface CourseDao {
    @Query("SELECT * FROM courses") suspend fun getAll(): List<MyCourse>
    @Query("SELECT * FROM courses") fun observeAll(): Flow<List<MyCourse>>
    @Upsert suspend fun upsertAll(items: List<MyCourse>)
}

// data/room/dao/RatingDao.kt — IS for nullable params, = for non-null
@Query("SELECT * FROM rating WHERE type IS :type AND item IS :item")
suspend fun getByTypeAndItem(type: String?, item: String?): List<Rating>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAll(items: List<Rating>)
```

**Rules:**
- Inject the specific DAO into a repository. For an atomic multi-DAO transaction, use Room's `withTransaction` on the `AppDatabase`. (`DatabaseService.withRoomAsync`/`executeRoomTransactionAsync` still exist but no repository uses them anymore.)
- DAO methods are `suspend` and confined to IO — do not block the main thread. `DictionaryActivity` also uses a DAO (`DictionaryDao`) now; there is no raw-DB escape hatch.
- Use `IS` (not `=`) in DAO `@Query` predicates when a `null` argument should match `NULL` rows (`=` never matches `NULL` in SQL).
- **Migration strategy is drop-and-resync**: `RoomModule` builds the DB with `fallbackToDestructiveMigration(true)`. On any schema change bump `version` in `AppDatabase`; there are **no** hand-written `Migration` objects — data is re-pulled from the Planet/CouchDB server on first launch.
- Inject `DispatcherProvider` (don't hard-code `Dispatchers.IO`) so tests can substitute deterministic dispatchers.

### Localization

Supported languages: English (default) + Arabic (ar), Spanish (es), French (fr), Nepali (ne), Somali (so). Add new strings to `app/src/main/res/values/strings.xml`; Crowdin syncs translations into `values-{lang}/strings.xml` automatically (config: `crowdin.yml`).

---

## Common Tasks

### Adding a New Data Model

1. **Create the Entity**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/model/MyNewModel.kt
   @Entity(tableName = "my_new_models")
   open class MyNewModel(
       @PrimaryKey var _id: String = "",
       var title: String? = null,
       var createdDate: Long = 0,
   )
   ```

2. **Create a DAO**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyNewModelDao.kt
   @Dao
   interface MyNewModelDao {
       @Query("SELECT * FROM my_new_models")
       suspend fun getAll(): List<MyNewModel>

       @Insert(onConflict = OnConflictStrategy.REPLACE)
       suspend fun insertAll(items: List<MyNewModel>)
   }
   ```

3. **Register the entity + DAO in `AppDatabase`** and provide the DAO in `RoomModule`
   ```kotlin
   // AppDatabase.kt: add MyNewModel::class to @Database(entities = [...]) and BUMP `version`
   abstract fun myNewModelDao(): MyNewModelDao
   // RoomModule.kt
   @Provides fun provideMyNewModelDao(db: AppDatabase) = db.myNewModelDao()
   ```

4. **Add the endpoint** in `data/api/ApiInterface.kt` if the model syncs with the server.

5. **Create Repository** (interface + Impl injecting the DAO) and **bind it** in `di/RepositoryModule.kt`:
   ```kotlin
   @Binds
   abstract fun bindMyNewModelRepository(impl: MyNewModelRepositoryImpl): MyNewModelRepository
   ```

### Adding a New Screen

1. Create layout XML in `res/layout/activity_my_feature.xml`
2. Create `@AndroidEntryPoint` Activity/Fragment extending appropriate base class with view binding
3. Register in `AndroidManifest.xml`
4. Navigate with `Intent(context, MyFeatureActivity::class.java)`

### Implementing Offline Sync

1. **Download**: Fetch from API, upsert into Room via a DAO `@Insert(onConflict = REPLACE)` (see `TransactionSyncManager` for the paginated per-table pulls)
2. **Upload**: Query unsynced items via a DAO (`... WHERE synced = 0`), POST each to server through `UploadCoordinator`, mark synced on success

### Adding Background Work

1. Create `CoroutineWorker` subclass with `doWork()` returning `Result.success()` or `Result.retry()`; fetch dependencies via `CoreDependenciesEntryPoint`/`ServiceDependenciesEntryPoint`
2. Schedule with `PeriodicWorkRequestBuilder<MyWorker>(interval, unit)` + network constraints + `WorkManager.enqueueUniquePeriodicWork`

---

## Testing Guidelines

> Full testing patterns (what to copy per layer, shared infra, naming) live in **`docs/TESTING.md`**.

### Current State
- **A real unit-test suite exists**: 166 unit-test files in `app/src/test/`. There is currently **no** `app/src/androidTest/` (instrumented) source set.
- **Stack**: JUnit4, **MockK** (`mockk` / `mockk-android`), **Robolectric**, `kotlinx-coroutines-test`, AndroidX Test (`core`/`ext`/`runner`/`arch-core-testing`), **Room testing** (`room-testing`), and **Hilt testing** (`hilt-android-testing` with `kspTest`). Dependencies are declared in `app/build.gradle` (test block) and `gradle/libs.versions.toml`.
- **Coverage**: nearly all 23 repositories, the sync managers (`services/sync/`), upload/retry services, most ViewModels, many `utils/`, several Room entities/DAOs, DI modules, and the API/auth layer.
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
```

**Core conventions**: MockK (not Mockito), `runTest { }` + `MainDispatcherRule` for coroutine code, `TestDispatcherProvider` instead of real dispatchers, Robolectric for Android framework classes, and mirror the `main` package path so CI picks new tests up automatically.

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

**Never hardcode:** API keys, passwords, server URLs / server PINs, user credentials.

> ⚠️ **KNOWN ISSUE — secrets currently committed.** `gradle.properties` is **tracked in git** (it is *not* gitignored) and holds real `PLANET_*_URL` / `PLANET_*_PIN` values. `app/build.gradle` bakes each into `BuildConfig`, and since `minifyEnabled=false` they are trivially recoverable from any shipped APK. These PINs are real CouchDB `satellite` credentials (used in `UrlUtils.header`, `ConfigurationsRepositoryImpl.buildCouchdbUrl`, and the `/healthaccess` PIN). **Do not add new secrets here.** Remediation: rotate the exposed PINs server-side, move values to an untracked file / CI secrets, gitignore `gradle.properties`, and purge it from git history.

**Preferred pattern** — inject config via untracked properties (`local.properties` / `secrets.properties`) or CI-injected `-P` properties, expose as `BuildConfig` fields, and read them as `BuildConfig.PLANET_LEARNING_URL`.

### Other Security Facts

- **Network security config**: `app/src/main/res/xml/network_security_config.xml` (trusted certs, cleartext policy)
- **Encrypted storage**: `SecurePrefs` (Tink-based) for sensitive preferences; password hashing via `Sha256Utils`/PBKDF2 (`AuthUtils`)
- **ProGuard/R8**: `minifyEnabled` is currently `false` for both debug and release builds

---

## Troubleshooting

**Issue: Room schema mismatch / "Room cannot verify the data integrity"**
- The app uses drop-and-resync: bump `version` in `AppDatabase` after any entity change. `RoomModule` already builds with `fallbackToDestructiveMigration(true)`, so the local DB is rebuilt and re-pulled from the server — no hand-written `Migration` needed.

**Issue: KSP annotation processing errors**
- `./gradlew clean`, remove `.gradle/`, rebuild.

**Issue: Hilt dependency not found**
- Ensure `@AndroidEntryPoint` annotation is present; verify a module provides the dependency; check injection point (constructor vs field).

**Issue: Blocking the main thread on DB access**
- Room DAO methods in this project are all `suspend` and run on IO — call them from a coroutine (`viewLifecycleOwner.lifecycleScope.launch`), never synchronously from the main thread.

**Issue: Push fails with 403**
- Ensure branch name starts with `claude/` and ends with the matching session ID; use `git push -u origin <branch-name>` (see **Branch Strategy** above for details).

---

## Additional Resources

### External Documentation
- [myPlanet Manual](https://open-learning-exchange.github.io/#!pages/manual/myplanet/overview.md)
- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)

### Community
- [Discord Server](https://discord.gg/BVrFEeNtQZ)
- [GitHub Issues](https://github.com/open-learning-exchange/myplanet/issues)

### Key File References

| Purpose | File Path | Line Count |
|---------|-----------|------------|
| Main entry point | `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt` | ~537 |
| REST API endpoints | `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt` | ~65 |
| Room database | `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt` | ~170 |
| Sync orchestration | `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` | ~691 |
| Upload handling | `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` | ~615 |
| Upload orchestration | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` | ~478 |
| Team management | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` | ~1437 |
| DB service wrapper | `app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt` | ~33 |
| Build configuration | `app/build.gradle` | ~231 |
| Dependency versions | `gradle/libs.versions.toml` | ~132 |

---

## Codebase Inventory Summary

### Source Files (502 total Kotlin files in `app/src/main/java`) + 166 unit-test files in `app/src/test` (no `app/src/androidTest` source set)

| Component | Files | Purpose |
|-----------|-------|---------|
| `model/` | 92 | Room `@Entity` models + DTOs |
| `repository/` | 50 | Data access abstraction (23 domain Interface+Impl pairs + sync interfaces + utilities) |
| `ui/` | 183 | User interface across 28 feature packages |
| `services/` | 39 | Background tasks & managers (22 root-level + sync/upload/retry sub-packages) |
| `di/` | 10 | Dependency injection (8 modules + 2 entry points) |
| `base/` | 13 | Reusable base classes |
| `callback/` | 28 | Event listeners and interfaces |
| `data/` | 40 | Data services, Room (AppDatabase, Converters, 30 DAOs, entity/), API, auth |
| `utils/` | 46 | Helper utilities |
| Root | 1 | MainApplication.kt |

### Resource Files

| Category | Count |
|----------|-------|
| Layout files (main) | 181 |
| Translation languages | 5 (ar, es, fr, ne, so) |
| Menu files | 2 |
| XML config files | 3 |

### AndroidManifest Permissions (16 total)

**Network**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_NETWORK_STATE
**Device**: CAMERA, RECORD_AUDIO, WAKE_LOCK, BLUETOOTH
**System**: PACKAGE_USAGE_STATS, REQUEST_INSTALL_PACKAGES (default flavor only; removed in lite)
**Notifications**: POST_NOTIFICATIONS, C2DM RECEIVE
**Foreground services**: FOREGROUND_SERVICE_DATA_SYNC (FOREGROUND_SERVICE appears only as the `android:permission` attribute on the DownloadService `<service>` element, not as a `<uses-permission>`)
**Other**: SEND_DOWNLOAD_COMPLETED_INTENTS; REQUEST_WRITE_PERMISSION (not a real Android permission — candidate for removal)

Note: SYSTEM_ALERT_WINDOW is **not** declared (removed at some point; older docs claimed it).

---

**Last Updated**: 2026-08-07
**Version**: 0.63.42
**Maintainer**: Open Learning Exchange

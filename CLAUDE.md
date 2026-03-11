# CLAUDE.md - AI Assistant Guide for myPlanet

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
├── .github/workflows/         # CI/CD (build.yml, release.yml)
├── app/src/
│   ├── main/java/org/ole/planet/myplanet/
│   │   ├── MainApplication.kt       # App entry point with Hilt
│   │   ├── base/                    # Base classes (12 files)
│   │   ├── callback/                # Event listeners (34 files)
│   │   ├── data/                    # Data services and API (8 files)
│   │   ├── di/                      # DI modules (16 files)
│   │   ├── model/                   # Realm models + DTOs (67 files)
│   │   ├── repository/              # Repository implementations (38 files)
│   │   ├── services/                # Background services (37 files)
│   │   ├── ui/                      # UI components (147 files, 28 packages)
│   │   └── utils/                   # Helpers (39 files)
│   ├── main/res/                    # Resources (169 layouts, 5 translations)
│   └── lite/AndroidManifest.xml     # Lite variant manifest
├── gradle/libs.versions.toml        # Centralized dependency versions
├── build.gradle.kts                 # Root build config
└── app/build.gradle                 # Module build config
```

### Package Organization (`org.ole.planet.myplanet`)

| Package | Purpose | Key Items |
|---------|---------|-----------|
| `base/` | Base classes (12) | BaseActivity, BaseRecyclerFragment, BasePermissionActivity, BaseContainerFragment, BaseDashboardFragment, BaseResourceFragment, BaseTeamFragment, BaseExamFragment, BaseMemberFragment, BaseDialogFragment, BaseVoicesFragment, BaseRecyclerParentFragment |
| `callback/` | Event listeners (34) | OnLibraryItemSelectedListener, OnSyncListener, OnTeamUpdateListener, OnChatItemClickListener, OnNewsItemClickListener, etc. |
| `data/` | Data access and API (8) | DatabaseService, NetworkResult, RealmMigrations; `api/` (ApiInterface, ApiClient, ChatApiService, RetryInterceptor), `auth/` (AuthSessionUpdater) |
| `di/` | Hilt DI (16) | 5 modules (Network, Database, Repository, Service, SharedPreferences) + 11 entry points |
| `model/` | Database models (67) | 40 Realm models + 27 DTOs (ChatMessage, ChatRequest, CourseProgressData, Download, ServerAddress, User, etc.) |
| `repository/` | Repositories (38) | 19 Interface+Impl pairs + RealmRepository base + SubmissionsRepositoryExporter |
| `services/` | Background tasks (37) | Root (20) + `sync/` (11) + `upload/` (4) + `retry/` (2) |
| `ui/` | UI components (147) | 28 feature packages with 16 ViewModels |
| `utils/` | Helpers (39) | NetworkUtils, ImageUtils, DialogUtils, FileUploader, AuthUtils, SecurePrefs, ANRWatchdog, etc. |

### UI Sub-packages (28 feature packages)

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
| `ui/teams/` | 22 | TeamFragment, TeamDetailFragment, TeamViewModel (largest) |
| `ui/user/` | 7 | UserProfileFragment, UserProfileViewModel, BecomeMemberActivity |
| `ui/viewer/` | 8 | ImageViewer, VideoViewer, AudioPlayer, PDFReader, WebView, MarkdownViewer, TextFileViewer, CSVViewer |
| `ui/voices/` | 6 | VoicesFragment, NewsViewModel, ReplyActivity |

### Critical Files

| File | Lines | Purpose |
|------|-------|---------|
| `MainApplication.kt` | ~448 | App init, Hilt DI, WorkManager scheduling, server reachability, theme/locale |
| `SyncManager.kt` | ~1058 | Sync orchestration via StateFlow, semaphore concurrency, adaptive batching |
| `UploadManager.kt` | ~770 | Batch uploads (BATCH_SIZE=50), integrates with UploadCoordinator |
| `TeamsRepositoryImpl.kt` | ~1097 | Team management with reactive Flow queries |
| `ApiInterface.kt` | ~65 | All REST endpoint definitions |

---

## Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.3.10 |
| Build | Gradle / AGP | 9.3.1 / 9.0.0 |
| DI | Dagger Hilt | 2.59.1 |
| Database | Realm | 10.19.0 |
| Network | Retrofit / OkHttp | 3.0.0 / 5.3.2 |
| JSON | Gson | 2.13.2 |
| Async | Kotlin Coroutines | 1.10.2 |
| Background | AndroidX Work | 2.11.1 |
| UI | Material Design 3 | 1.13.0 |
| Images | Glide | 5.0.5 |
| Media | Media3 (ExoPlayer) | 1.9.2 |
| Markdown | Markwon | 4.6.2 |
| Maps | OSMDroid | 6.1.20 |
| Encryption | Tink | 1.20.0 |
| Serialization | Kotlin Serialization | 1.10.0 |
| CSV | OpenCSV | 5.12.0 |

**Compiler**: Java 17, Kotlin JVM 17. View Binding, Data Binding, BuildConfig enabled. Annotation processing via KAPT and KSP.

---

## Architecture Patterns

### Layered Architecture

```
UI Layer (Activities/Fragments + 16 ViewModels)
    ↓
Repository Layer (19 domain repositories, Interface+Impl pairs)
    ↓
Service Layer (ApiInterface, SyncManager, UploadCoordinator)
    ↓
Data Sources (Realm local DB, REST API, SharedPreferences)
```

### ViewModels (16)

ChatViewModel, TeamViewModel, RequestsViewModel, DashboardViewModel, BellDashboardViewModel, ProgressViewModel, EnterprisesViewModel, RatingsViewModel, NewsViewModel, ReplyViewModel, FeedbackListViewModel, FeedbackDetailViewModel, SubmissionViewModel, SubmissionDetailViewModel, UserProfileViewModel, NotificationsViewModel

### Repository Domains (19)

Activities, Chat, Configurations, Courses, Events, Feedback, Life, Notifications, Personals, Progress, Ratings, Resources, Submissions, Surveys, Tags, Teams, User, Voices + RealmRepository base + SubmissionsRepositoryExporter

### DI Modules

- `NetworkModule` - Retrofit, OkHttp
- `DatabaseModule` - Realm instances
- `RepositoryModule` - Binds repository interfaces to implementations
- `ServiceModule` - Service dependencies
- `SharedPreferencesModule` - SharedPreferences

**Entry Points for Workers (11):** AutoSyncEntryPoint, ApiClientEntryPoint, ApiInterfaceEntryPoint, ApplicationScopeEntryPoint, BroadcastServiceEntryPoint, DatabaseServiceEntryPoint, RepositoryEntryPoint, RetryQueueEntryPoint, ServiceEntryPoint, TeamsRepositoryEntryPoint, WorkerDependenciesEntryPoint

### Background Processing

**Workers:** AutoSyncWorker, NetworkMonitorWorker, ServerReachabilityWorker, TaskNotificationWorker, DownloadWorker, FreeSpaceWorker, StayOnlineWorker, RetryQueueWorker

**Services/Managers (root):** SyncManager, UploadManager, UploadToShelfService, UploadCoordinator, AudioRecorder, BroadcastService, ConfigurationManager, SharedPrefManager, UserSessionManager, ThemeManager, FileUploader, DownloadService, VoicesLabelManager, ChallengePrompter, NotificationActionReceiver

**Sync sub-package (11):** SyncManager, LoginSyncManager, TransactionSyncManager, ImprovedSyncManager, RealtimeSyncManager, AdaptiveBatchProcessor, StandardSyncStrategy, SyncStrategy, ThreadSafeRealmManager, RealmConnectionPool, ServerUrlMapper

**Upload sub-package (4):** UploadCoordinator, UploadConfigs, UploadConfig, UploadResult

**Retry sub-package (2):** RetryQueue, RetryQueueWorker

---

## Development Workflows

### Build Commands

```bash
./gradlew assembleDefaultDebug     # Build default debug APK
./gradlew assembleLiteDebug        # Build lite debug APK
./gradlew assembleDefaultRelease   # Build default release APK
./gradlew bundleDefaultRelease     # Build default release AAB
./gradlew installDefaultDebug      # Install on device
./gradlew clean                    # Clean build artifacts
```

### CI/CD

**Build Workflow** (`build.yml`): Triggers on all branches except `master`. Matrix builds both flavors on Ubuntu 24.04 using `gradle/actions/setup-gradle@v5`. Command: `./gradlew assemble${FLAVOR}Debug --parallel --max-workers=4`

**Release Workflow** (`release.yml`): Triggers on `master` push. Builds signed APK+AAB, generates SHA256 checksums, publishes to Play Store (internal track), creates GitHub release (`v${VERSION}`), sends Discord notifications.

**Dependabot**: Daily checks for GitHub Actions (max 10 PRs) and Gradle dependencies (max 15 PRs).

### Adding New Features

1. **Identify layer**: UI → `ui/`, Data model → `model/`, Logic → `repository/` or `services/`, API → `data/ApiInterface.kt`
2. **Create components**: Model (Realm if persistent), Repository interface+impl, UI Activity/Fragment, Layout XML
3. **Dependencies**: Add in `gradle/libs.versions.toml`, reference in `app/build.gradle`
4. **Register**: Activities in `AndroidManifest.xml`, permissions if needed
5. **DI**: Provide in appropriate module, bind repositories in `RepositoryModule`

---

## Key Conventions

### Naming

- **Classes**: PascalCase. Activities: `*Activity.kt`, Fragments: `*Fragment.kt`, ViewModels: `*ViewModel.kt`, Adapters: `*Adapter.kt`, Repositories: `*Repository.kt`/`*RepositoryImpl.kt`, Models: `Realm*.kt`, Workers: `*Worker.kt`
- **Layouts**: Activities: `activity_*.xml`, Fragments: `fragment_*.xml`, Items: `row_*.xml`/`item_*.xml`, Dialogs: `dialog_*.xml`
- **Code**: camelCase for functions/variables, UPPER_SNAKE_CASE for constants

### Realm Database

- All models extend `RealmObject` with `open` class modifier
- Properties must be `var` (Realm requirement)
- Primary keys use `@PrimaryKey` annotation, nullable types use `?`
- Queries: `mRealm.where(Model::class.java).equalTo("field", value).findAll()`
- Writes: `mRealm.executeTransactionAsync { realm -> realm.copyToRealmOrUpdate(obj) }`
- Always close: `if (::mRealm.isInitialized && !mRealm.isClosed) mRealm.close()`

### Dependency Injection

- **Preferred**: Constructor injection with `@Inject lateinit var` in `@AndroidEntryPoint` classes
- **Workers**: Use `EntryPointAccessors.fromApplication()` with custom entry point interfaces

### Async Patterns

- Use Kotlin Coroutines with `viewLifecycleOwner.lifecycleScope.launch` in UI
- Repository operations use `withContext(Dispatchers.IO)`
- Background scheduling via `PeriodicWorkRequestBuilder` with constraints

### View Binding

- Activities: `ActivityXBinding.inflate(layoutInflater)` + `setContentView(binding.root)`
- Fragments: nullable `_binding` pattern, nullify in `onDestroyView()`

### Localization

Supported: English (default), Arabic (ar), Spanish (es), French (fr), Nepali (ne), Somali (so). Translations synced via Crowdin (`crowdin.yml`).

---

## Testing

No formal testing framework currently configured. Manual testing on devices/emulators.

**When making changes, verify:** App builds, feature works offline, sync works, UI renders on different screen sizes, dark theme works, supported languages display correctly, permissions requested appropriately.

---

## Security Notes

- Never hardcode API keys, passwords, server URLs, or credentials
- Use `gradle.properties` for configuration, access via `BuildConfig`
- Network security config: `app/src/main/res/xml/network_security_config.xml`
- Encrypted SharedPreferences via Tink, password hashing via `Sha256Utils`
- `minifyEnabled` is `false` for both debug and release builds
- 23 manifest permissions (network, camera, audio, bluetooth, notifications, etc.)

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Gradle sync failed | `./gradlew clean && ./gradlew build --refresh-dependencies` |
| KAPT/KSP errors | Clean build cache: `rm -rf .gradle/` then rebuild |
| Realm migration errors | Increment schema version or use `deleteRealmIfMigrationNeeded()` (dev only) |
| Hilt dependency not found | Verify `@AndroidEntryPoint`, module provides dependency, correct injection point |
| Network requests fail | Check `NetworkUtils.isNetworkAvailable()`, add OkHttp logging interceptor |
| Realm database locked | Ensure realm closed in `onDestroy()` |
| OOM with images | Use `Glide.with(context).load(url).override(800, 600)` |
| Git push 403 | Branch must start with `claude/` and end with matching session ID |

---

## Codebase Inventory

**394 total Kotlin source files** across: model (67), ui (147), utils (39), repository (38), services (37), callback (34), di (16), base (12), data (8), root (1)

**Resources**: 169 layouts, 129 drawables, 5 translation languages, ~1,194 string resource lines, 2 menus, 3 XML configs

---

**Last Updated**: 2026-02-10
**Version**: 0.46.0
**Maintainer**: Open Learning Exchange

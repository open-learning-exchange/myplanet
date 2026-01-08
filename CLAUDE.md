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

---

## Project Overview

**myPlanet** is an Android mobile application serving as an offline extension of the Open Learning Exchange's Planet Learning Management System. It enables learners to access educational resources (books, videos, courses) without continuous internet connectivity.

### Key Characteristics
- **Primary Language**: Kotlin (with Java compatibility layer)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15)
- **Current Version**: 0.42.56 (versionCode: 4256)
- **Build System**: Gradle 8.14.2
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
│   │   │   │   ├── model/                   # Realm data models (53 classes)
│   │   │   │   ├── repository/              # Repository pattern implementations
│   │   │   │   ├── service/                 # Background services and workers
│   │   │   │   ├── ui/                      # UI components (28 packages)
│   │   │   │   └── utilities/               # Helper utilities
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

| Package | Purpose | Key Files |
|---------|---------|-----------|
| `base/` | Base classes for common functionality | BaseActivity, BaseRecyclerFragment, PermissionActivity |
| `callback/` | Event listeners and interfaces | OnLibraryItemSelected, SyncListener, TeamUpdateListener |
| `data/` | Data access and API services | DataService.kt, DatabaseService.kt, ApiInterface, auth/ |
| `di/` | Hilt dependency injection | NetworkModule, DatabaseModule, RepositoryModule |
| `model/` | Realm database models | 53 models including RealmMyTeam, RealmMyCourse, RealmMyLibrary |
| `repository/` | Repository pattern implementations | 18 repositories with Interface + Impl pairs |
| `service/` | Background services | sync/SyncManager.kt, UploadManager.kt, AutoSyncWorker |
| `ui/` | User interface components | 28 feature packages (courses, resources, teams, chat, etc.) |
| `utilities/` | Helper functions | NetworkUtils, ImageUtils, DialogUtils, FileUploadService |

### Critical Files to Understand

1. **`MainApplication.kt`** (~420 lines)
   - Application initialization
   - Hilt setup
   - Network monitoring
   - Auto-sync worker scheduling
   - Location: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`

2. **`DataService.kt`** (~450 lines)
   - Main data service for local database operations
   - Location: `app/src/main/java/org/ole/planet/myplanet/data/DataService.kt`

3. **`SyncManager.kt`** (~1080 lines)
   - Orchestrates data synchronization with server
   - Location: `app/src/main/java/org/ole/planet/myplanet/service/sync/SyncManager.kt`

4. **`UploadManager.kt`** (~1330 lines)
   - Handles upload operations
   - Location: `app/src/main/java/org/ole/planet/myplanet/service/UploadManager.kt`

5. **`TeamsRepositoryImpl.kt`** (~915 lines)
   - Team management functionality
   - Location: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`

---

## Technology Stack

### Core Technologies

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Kotlin | 2.2.21 | Primary development language |
| **Build System** | Gradle | 8.14.2 | Build automation |
| **DI Framework** | Dagger Hilt | 2.57.2 | Dependency injection |
| **Database** | Realm | 10.19.0 | Local object database |
| **Networking** | Retrofit | 3.0.0 | REST API client |
| **HTTP Client** | OkHttp | 5.3.2 | HTTP communication |
| **JSON** | Gson | 2.13.2 | JSON serialization |
| **Async** | Kotlin Coroutines | 1.10.2 | Asynchronous programming |
| **Background Tasks** | AndroidX Work | 2.11.0 | Background job scheduling |
| **UI Framework** | Material Design 3 | 1.13.0 | UI components |
| **Image Loading** | Glide | 5.0.5 | Image loading and caching |
| **Media Playback** | Media3 (ExoPlayer) | 1.9.0 | Audio/video playback |
| **Markdown** | Markwon | 4.6.2 | Markdown rendering |
| **Maps** | OSMDroid | 6.1.20 | OpenStreetMap integration |

### Build Configuration

**Gradle Plugins:**
- `com.android.application`
- `kotlin-android`
- `kotlin-kapt` (Annotation processing)
- `com.google.devtools.ksp` (Symbol processing)
- `dagger.hilt.android.plugin`
- `io.realm.kotlin`

**Compiler Settings:**
- Java Compatibility: 17
- Kotlin JVM Target: 17
- View Binding: Enabled
- BuildConfig: Enabled

---

## Architecture Patterns

### 1. Layered Architecture

```
┌─────────────────────────────────────────┐
│     UI Layer (Activities/Fragments)     │
│  - User interaction                     │
│  - View binding                         │
│  - Lifecycle management                 │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Repository Layer                    │
│  - Data access abstraction              │
│  - Interface + Implementation           │
│  - Caching logic                        │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Data Manager Layer                  │
│  - Service.kt (local operations)        │
│  - ApiInterface (remote operations)     │
│  - SyncManager (synchronization)        │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     Data Sources                        │
│  - Realm Database (local)               │
│  - REST API (remote)                    │
│  - SharedPreferences (settings)         │
└─────────────────────────────────────────┘
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

**Location**: `app/src/main/java/org/ole/planet/myplanet/repository/`

### 3. Dependency Injection (Hilt)

**Module Structure:**
- `NetworkModule.kt` - Provides Retrofit, OkHttp
- `DatabaseModule.kt` - Provides Realm instances
- `RepositoryModule.kt` - Binds repository interfaces to implementations
- `ServiceModule.kt` - Provides service dependencies
- `SharedPreferencesModule.kt` - Provides SharedPreferences

**Entry Points for Workers:**
```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSyncEntryPoint {
    fun apiInterface(): ApiInterface
    fun sharedPreferences(): SharedPreferences
}
```

**Location**: `app/src/main/java/org/ole/planet/myplanet/di/`

### 4. Base Classes for Code Reuse

| Base Class | Purpose | Key Methods |
|------------|---------|-------------|
| `BaseActivity` | Common activity functionality | Permission handling, dialogs |
| `BaseRecyclerFragment` | List-based fragments | Pagination, filtering, search |
| `BaseContainerFragment` | Navigation containers | Fragment transactions |
| `BaseResourceFragment` | Resource handling | Download, view, share |
| `PermissionActivity` | Runtime permissions | Permission request handling |

**Location**: `app/src/main/java/org/ole/planet/myplanet/base/`

### 5. Background Processing

**AndroidX Work for Scheduled Tasks:**
- `AutoSyncWorker` - Periodic data synchronization
- `NetworkMonitorWorker` - Network state monitoring
- `ServerReachabilityWorker` - Server availability checking
- `TaskNotificationWorker` - Task deadline notifications
- `DownloadWorker` - Background file downloads

**Services:**
- `SyncManager` - Manual synchronization
- `UploadManager` - File upload coordination
- `AudioRecorderService` - Audio recording
- `BroadcastService` - Service broadcasting

**Location**: `app/src/main/java/org/ole/planet/myplanet/service/`

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
- Update UserProfileService"

# Push to remote (MUST use -u flag)
git push -u origin claude/feature-name-sessionid
```

### CI/CD Pipeline

**Build Workflow** (`.github/workflows/build.yml`)
- Triggers: All branches except `master`
- Builds both `default` and `lite` flavors
- Validates compilation

**Release Workflow** (`.github/workflows/release.yml`)
- Triggers: `master` branch push or manual dispatch
- Builds signed APK and AAB
- Publishes to Google Play Store (internal track)
- Creates GitHub release with artifacts
- Sends Discord notifications

### Adding New Features

1. **Identify the Layer**
   - UI change? → `ui/` package
   - Data model? → `model/` package
   - Business logic? → `repository/` or `service/`
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
- Adapters: `*Adapter.kt` (e.g., `CourseAdapter.kt`)
- ViewHolders: `*ViewHolder.kt`
- Repositories: `*Repository.kt` and `*RepositoryImpl.kt`
- Models: `Realm*.kt` for Realm objects

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
   // app/src/main/java/org/ole/planet/myplanet/service/MyWorker.kt
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

**Enable for release builds:**
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
| Main entry point | `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt` | ~420 |
| Core data service | `app/src/main/java/org/ole/planet/myplanet/data/DataService.kt` | ~450 |
| Sync orchestration | `app/src/main/java/org/ole/planet/myplanet/service/sync/SyncManager.kt` | ~1080 |
| Upload handling | `app/src/main/java/org/ole/planet/myplanet/service/UploadManager.kt` | ~1330 |
| Team management | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` | ~915 |
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

**Last Updated**: 2026-01-06
**Version**: 0.42.56
**Maintainer**: Open Learning Exchange

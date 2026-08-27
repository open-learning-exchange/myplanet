# Task Generation Brief — myPlanet Refactor Round

**Date:** 2025-08-27  
**Base Commit:** 45bac8d (tag: v0.67.59, master)  
**Open PRs Checked:** could not check open PRs

---

### 1. Replace hardcoded GitHub workflow timeout values with constants (roadmap 8)
context: GitHub Actions workflows use magic numbers like 60, 120, 300 for timeout minutes across multiple workflow files. These should be centralized as reusable constants to improve maintainability and consistency. The hardcoded values make it difficult to adjust timeouts systematically across all workflows.
files: .github/workflows/build.yml, .github/workflows/test.yml, .github/workflows/release.yml. Do NOT modify any other workflow files outside these three.
steps: 
1. Create a variables section in each workflow file with timeout values
2. Replace all hardcoded timeout values with the corresponding variable references
3. Ensure all workflows still execute properly after the change
4. Run local validation of YAML syntax
acceptance: All GitHub workflow files validate syntactically and functional tests pass; workflows execute with same timeout behavior as before
size budget: ~15 changed lines, 3 files
out of scope: No changes to workflow logic, only replacing hardcoded values with variables

---

### 2. Consolidate duplicate lint configuration across build files (roadmap 8)
context: Multiple module build.gradle files contain identical or similar lint configuration blocks that could be standardized through a shared configuration. This duplication increases maintenance overhead and can lead to inconsistent lint behavior across modules. The configuration appears in several build files with slight variations.
files: app/build.gradle, wearApp/build.gradle. Do NOT modify settings.gradle or gradle.properties for lint configurations.
steps:
1. Identify common lint options across build files
2. Standardize the configuration format
3. Remove redundant configurations while maintaining functionality
4. Verify lint checks still run correctly
acceptance: ./gradlew lint runs successfully and produces consistent results across modules; no new lint errors appear
size budget: ~20 changed lines, 2 files
out of scope: No addition of new lint rules, only consolidation of existing ones

---

### 3. Optimize database queries in LibraryFragment to reduce N+1 problem (roadmap 1+7)
context: LibraryFragment.kt makes repeated individual queries for resource metadata when displaying multiple resources, creating unnecessary database round trips. This pattern significantly impacts performance when users browse large collections of resources, as evidenced by slow scrolling in library views.
files: app/src/main/java/org/ole/planet/myplanet/ui/library/LibraryFragment.kt. Do NOT modify the underlying DAO implementations.
steps:
1. Identify queries being called in loops
2. Replace with batch query methods
3. Update UI binding logic to accommodate batch results
4. Test performance improvement with large dataset
acceptance: ./gradlew testDefaultDebugUnitTest passes; library screen loads faster with many resources displayed
size budget: ~25 changed lines, 1 file
out of scope: No changes to database schema or DAO methods

---

### 4. Refactor duplicate permission checking logic in camera-related fragments (roadmap 8)
context: Multiple fragments contain identical permission request code for camera functionality, violating DRY principles. The CameraPermissionHelper or similar utility exists but isn't consistently used across CameraFragment, ExamQuestionFragment, and other camera-accessing components.
files: app/src/main/java/org/ole/planet/myplanet/ui/camera/CameraFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamQuestionFragment.kt. Do NOT modify CameraPermissionHelper itself.
steps:
1. Locate duplicate permission request code in both files
2. Replace with calls to existing permission helper functions
3. Remove redundant permission handling code
4. Verify permissions still work correctly in both fragments
acceptance: ./gradlew testDefaultDebugUnitTest passes; camera features work properly in both fragments
size budget: ~18 changed lines, 2 files
out of scope: No modification of the permission helper implementation

---

### 5. Eliminate redundant SharedPreferences access patterns in SyncManager (roadmap 5+7)
context: SyncManager contains multiple instances of direct SharedPreferences access instead of using centralized preference utilities. This creates inconsistency and potential race conditions during sync operations, particularly evident in long-running sync processes that may fail intermittently.
files: app/src/main/java/org/ole/planet/myplanet/service/SyncManager.kt. Do NOT modify PreferenceUtil or related utility classes.
steps:
1. Identify direct SharedPreferences.get/put calls
2. Replace with calls to existing preference utility methods
3. Ensure all preference keys remain consistent
4. Test sync functionality with various network conditions
acceptance: ./gradlew testDefaultDebugUnitTest passes; sync operations continue to work reliably
size budget: ~22 changed lines, 1 file
out of scope: No changes to preference key names or storage structure

---

### 6. Consolidate similar animation properties in Compose previews (roadmap 6+10)
context: Multiple Compose preview functions define identical animation parameters repeatedly, increasing code volume unnecessarily. These animations follow the same patterns across different screens but are implemented separately, making future animation changes require multiple updates.
files: Multiple composable preview files such as app/src/main/java/org/ole/planet/myplanet/ui/*.kt files with @Preview annotations. Do NOT modify actual UI component implementations.
steps:
1. Identify common animation parameter patterns in previews
2. Standardize preview animation configurations
3. Remove redundant parameter specifications
4. Verify previews render correctly with new standardization
acceptance: Previews render correctly in Android Studio; no visual differences in preview outputs
size budget: ~15 changed lines across 3 files
out of scope: No changes to actual animation behavior of running app

---

### 7. Optimize image loading memory usage in RecyclerView adapters (roadmap 7)
context: Several RecyclerView adapters load high-resolution images directly into list items without proper size constraints, causing memory spikes during scrolling. This leads to OOM errors particularly on lower-end devices when browsing content with many images.
files: app/src/main/java/org/ole/planet/myplanet/ui/library/AdapterLibrary.java, app/src/main/java/org/ole/planet/myplanet/ui/news/NewsAdapter.java. Do NOT modify the image loading library configuration.
steps:
1. Identify Glide or similar image loading calls without size constraints
2. Add appropriate size limits based on view dimensions
3. Implement proper placeholder and error handling
4. Test with various image sizes and network conditions
acceptance: ./gradlew testDefaultDebugUnitTest passes; scrolling performance improves without image quality degradation
size budget: ~20 changed lines, 2 files
out of scope: No changes to image loading library version or base configuration

---

### 8. Streamline duplicate analytics tracking calls across login flow (roadmap 8+7)
context: Login-related fragments contain nearly identical Firebase Analytics event tracking code that should be consolidated into a shared utility. Multiple tracking calls use the same parameters and formatting, creating unnecessary code duplication that affects app startup performance.
files: app/src/main/java/org/ole/planet/myplanet/ui/login/LoginActivity.java, app/src/main/java/org/ole/planet/myplanet/ui/login/SignupActivity.java. Do NOT modify AnalyticsUtil if it exists.
steps:
1. Identify duplicate analytics event calls in both activities
2. Standardize event naming and parameter formats
3. Remove redundant tracking code while preserving functionality
4. Verify analytics events still fire correctly
acceptance: ./gradlew testDefaultDebugUnitTest passes; analytics events continue to track properly
size budget: ~16 changed lines, 2 files
out of scope: No new analytics events or modifications to analytics service setup

---

### 9. Replace repeated color definitions with theme attributes in Compose UI (roadmap 6+10)
context: Multiple Compose screens define identical color values as local constants instead of using theme attributes, breaking consistency and increasing maintenance. These hardcoded colors prevent dynamic theme switching and create inconsistencies across the UI.
files: app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardScreen.kt, app/src/main/java/org/ole/planet/myplanet/ui/profile/ProfileScreen.kt. Do NOT modify the Theme definition files.
steps:
1. Identify hardcoded color values in both composables
2. Replace with appropriate MaterialTheme.colorScheme references
3. Ensure UI maintains same appearance after changes
4. Test theme switching functionality if available
acceptance: ./gradlew testDefaultDebugUnitTest passes; UI colors remain consistent and respond to theme changes
size budget: ~14 changed lines, 2 files
out of scope: No changes to actual color scheme values or theme definitions

---

### 10. Optimize repeated network status checks in background services (roadmap 5+7)
context: Multiple background services perform redundant network connectivity checks using different approaches, consuming unnecessary resources. The NetworkUtils provides standardized connectivity checks but aren't consistently utilized across sync and upload services.
files: app/src/main/java/org/ole/planet/myplanet/service/BackgroundSyncService.kt, app/src/main/java/org/ole/planet/myplanet/service/UploadService.kt. Do NOT modify NetworkUtils implementation.
steps:
1. Locate duplicate network connectivity checking logic
2. Replace with calls to standardized network utility functions
3. Remove redundant connectivity checking code
4. Test background operations under various network conditions
acceptance: ./gradlew testDefaultDebugUnitTest passes; background sync and upload continue to work properly
size budget: ~18 changed lines, 2 files
out of scope: No changes to network utility functions or connectivity algorithms

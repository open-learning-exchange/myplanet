# Task Plan — myPlanet Refactor Round

**Date:** August 24, 2026  
**Base Commit:** Current  
**Open PRs Checked:** Could not check open PRs  

---

### 1. Replace hardcoded strings with string resources in login activity (roadmap 8)
context: LoginActivity.kt contains multiple hardcoded strings for error messages and UI labels that should use string resources for localization and maintainability. This affects code health and makes testing harder as string values are scattered throughout the code. 
files: app/src/main/java/org/ole/planet/myplanet/ui/splash/LoginActivity.kt. Do not touch string.xml files as other PRs may be modifying them.
steps: 1. Identify all hardcoded strings in the LoginActivity 2. Replace with appropriate R.string references where available 3. Remove any duplicate string definitions 4. Run unit tests to ensure functionality remains unchanged
acceptance: ./gradlew testDefaultDebugUnitTest passes; login screen displays the same text as before but uses string resources internally
size budget: ~25 changed lines, 1 file
out of scope: No addition of new string resources to values/strings.xml, no UI layout modifications

---

### 2. Convert inline lambda expressions to method references in sync service (roadmap 7+8)
context: SyncManager.kt:45-67 uses multiple inline lambda expressions that can be converted to method references for better performance and readability. This creates unnecessary object allocation during sync operations.
files: app/src/main/java/org/ole/planet/myplanet/service/SyncManager.kt (lines 45-67). Do not modify SyncService.java as it may be involved in other sync-related PRs.
steps: 1. Identify inline lambdas that can be converted to method references 2. Replace with appropriate method references 3. Verify all callback behaviors remain the same 4. Run sync-related tests
acceptance: ./gradlew testDefaultDebugUnitTest passes; sync functionality works identically to before
size budget: ~15 changed lines, 1 file
out of scope: No changes to sync logic, no new methods or classes

---

### 3. Eliminate redundant null checks in ViewModel constructors (roadmap 4+8)
context: Multiple ViewModels including LibraryViewModel.kt:23 contain redundant null checks for parameters that are non-null by design, creating unnecessary conditional branches. These checks slow down instantiation and reduce code clarity.
files: app/src/main/java/org/ole/planet/myplanet/ui/library/LibraryViewModel.kt (line 23), app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseViewModel.kt (line 19). Do not modify Repository interfaces.
steps: 1. Identify redundant null checks in ViewModel constructors 2. Remove unnecessary safety checks 3. Ensure all dependencies remain properly validated 4. Run all ViewModel unit tests
acceptance: ./gradlew testDefaultDebugUnitTest passes; ViewModels continue to initialize correctly with valid parameters
size budget: ~20 changed lines, 2 files
out of scope: No changes to dependency injection setup, no parameter type modifications

---

### 4. Replace manual JSON parsing with Moshi in preferences helper (roadmap 1+7)
context: PreferencesHelper.kt:123-145 manually parses JSON strings when storing complex objects, which is error-prone and inefficient. Using Moshi would provide safer, faster parsing with proper error handling.
files: app/src/main/java/org/ole/planet/myplanet/utilities/PreferencesHelper.kt (lines 123-145). Do not modify BaseApplication.java as it handles initialization.
steps: 1. Add Moshi instance to the class 2. Replace manual JSON parsing with Moshi adapters 3. Handle potential parsing exceptions appropriately 4. Test preference saving and retrieval functionality
acceptance: ./gradlew testDefaultDebugUnitTest passes; preferences save and load complex objects correctly
size budget: ~30 changed lines, 1 file
out of scope: No changes to preference key names, no new dependency injection modules

---

### 5. Optimize database queries with index hints in DAO classes (roadmap 1+7)
context: LibraryDao.kt contains queries like getAllLibraries() that could benefit from database indexing hints to improve query performance. Slow database access impacts app responsiveness significantly.
files: app/src/main/java/org/ole/planet/myplanet/datamanager/LibraryDao.kt (getAllLibraries method). Do not modify DatabaseManager.java as it handles database creation.
steps: 1. Analyze existing queries for optimization opportunities 2. Add appropriate index hints to frequently used queries 3. Verify query execution times have improved 4. Run database unit tests
acceptance: ./gradlew testDefaultDebugUnitTest passes; database operations complete faster while maintaining data integrity
size budget: ~18 changed lines, 1 file
out of scope: No schema changes, no new database tables

---

### 6. Consolidate duplicate color definitions in colors.xml (roadmap 8)
context: res/values/colors.xml contains multiple similar color definitions that create inconsistency and bloat the resource file. This affects UI consistency and increases APK size unnecessarily.
files: app/src/main/res/values/colors.xml. Do not modify style.xml files as they may be part of theme work.
steps: 1. Identify duplicate or very similar color definitions 2. Merge duplicates keeping the most commonly used variant 3. Update references to use consolidated colors 4. Verify UI appearance remains consistent
acceptance: ./gradlew testDefaultDebugUnitTest passes; UI elements display the same colors as before
size budget: ~35 changed lines, 1 file
out of scope: No changes to actual color values, no UI component modifications

---

### 7. Replace findViewById with View Binding in profile fragment (roadmap 6+8)
context: ProfileFragment.kt:34-67 still uses findViewById calls instead of View Binding, making the code verbose and prone to errors. This pattern is outdated and harder to maintain.
files: app/src/main/java/org/ole/planet/myplanet/ui/profile/ProfileFragment.kt (lines 34-67). Do not modify ProfileViewModel.kt as it may be involved in other refactoring.
steps: 1. Enable View Binding for the fragment 2. Replace all findViewById calls with binding properties 3. Clean up view references after view destruction 4. Run fragment tests
acceptance: ./gradlew testDefaultDebugUnitTest passes; profile screen functions identically to before
size budget: ~40 changed lines, 1 file
out of scope: No changes to fragment lifecycle logic, no ViewModel modifications

---

### 8. Optimize image loading configuration in RecyclerView adapters (roadmap 7)
context: LibraryAdapter.kt:56-78 configures Glide image loading inefficiently with repeated options that could be centralized, causing memory overhead during scrolling. This impacts performance on library screens.
files: app/src/main/java/org/ole/planet/myplanet/ui/library/LibraryAdapter.kt (lines 56-78). Do not modify GlideModule classes as they handle global configuration.
steps: 1. Identify repeated Glide configuration patterns 2. Create centralized RequestOptions where possible 3. Apply optimized configurations to image loading calls 4. Test image display in lists
acceptance: ./gradlew testDefaultDebugUnitTest passes; images load correctly in library lists with better memory usage
size budget: ~22 changed lines, 1 file
out of scope: No changes to Glide dependency version, no custom transformation implementations

---

### 9. Simplify conditional statements with Kotlin idioms in utilities (roadmap 8+7)
context: DateUtils.kt:23-45 contains verbose Java-style conditional statements that could leverage Kotlin's idiomatic patterns like elvis operator and safe calls for cleaner, more efficient code.
files: app/src/main/java/org/ole/planet/myplanet/utilities/DateUtils.kt (lines 23-45). Do not modify TimeLogger classes as they may be involved in other time-related features.
steps: 1. Identify verbose conditionals that can use Kotlin idioms 2. Replace with elvis operators, let calls, and safe calls 3. Verify all edge cases still handled correctly 4. Run date utility tests
acceptance: ./gradlew testDefaultDebugUnitTest passes; date formatting and comparison work identically to before
size budget: ~18 changed lines, 1 file
out of scope: No changes to date calculation logic, no new utility methods

---

### 10. Cache expensive computations in base activity (roadmap 7+8)
context: BaseActivity.kt:89-95 recomputes certain values on every method call instead of caching results, causing unnecessary CPU cycles during UI operations. This affects all screens that extend this base.
files: app/src/main/java/org/ole/planet/myplanet/ui/BaseActivity.kt (lines 89-95). Do not modify BaseFragment.kt as it may be undergoing separate refactoring.
steps: 1. Identify expensive computations that can be cached 2. Implement lazy properties or memoization for computed values 3. Ensure cache invalidation occurs when needed 4. Test base activity functionality across different screens
acceptance: ./gradlew testDefaultDebugUnitTest passes; BaseActivity methods return same results but with better performance
size budget: ~15 changed lines, 1 file
out of scope: No changes to lifecycle methods, no new activity-specific functionality

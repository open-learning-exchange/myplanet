# Refactor Roadmap Actionable Tasks (Phase 1)

This document contains 10 highly specific, granular refactoring tasks designed to deliver low-hanging fruit and performance wins across the codebase. These tasks focus on targeted optimizations without requiring sweeping architecture rewrites.

---
### 1. Remove synchronous SharedPreferences `.commit()` on non-critical paths
**Category:** Performance / I/O Optimization
**Location:** `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
**Task:** Replace blocking `.commit()` calls with `.apply()` to prevent main thread blocking, particularly in `TransactionSyncManager` and `SharedPrefManager`. Only use `.commit()` if the application strictly depends on the write succeeding before the next line of code executes (which is rare for checkpoints).

---
### 2. Fix Fragment Coroutine Scope Leaks
**Category:** Concurrency / Lifecycle
**Location:** Multiple Fragments (e.g., `CourseStepFragment`, `CoursesFragment`, `EditAchievementFragment`, `BellDashboardFragment`)
**Task:** Identify `lifecycleScope.launch` calls within Fragments (which run until the Fragment is completely destroyed) and convert them to `viewLifecycleOwner.lifecycleScope.launch`. This ensures coroutines are properly canceled when the View is destroyed, preventing leaks and crashes upon view recreation.

---
### 3. Replace generic `notifyDataSetChanged()` with `submitList()`
**Category:** UI Performance / RecyclerView
**Location:** `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
**Task:** `notifyDataSetChanged()` forces a full rebind and bypasses DiffUtil optimizations. Modify the survey data update flow to rely entirely on `getAdapter().submitList(newData)` or `DiffUtil` to ensure smooth animations and minimal rebinding overhead.

---
### 4. Remove Hardcoded `DefaultDispatcherProvider()` Default Arguments
**Category:** Testing / Dependency Injection
**Location:** `app/src/main/java/org/ole/planet/myplanet/utils/DispatcherProvider.kt`, `app/src/main/java/org/ole/planet/myplanet/di/DispatcherModule.kt` (and any implementing classes)
**Task:** Remove instances where `DefaultDispatcherProvider()` is assigned as a default parameter in class constructors. This enforces explicit dependency injection of the `DispatcherProvider` at instantiation, enabling reliable testing and centralized concurrency control.

---
### 5. Remove Deprecated TTSManager Code
**Category:** Code Health / Tech Debt
**Location:** `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`
**Task:** Identify the legacy method or block marked with `@Deprecated` in `TTSManager`. Migrate any remaining callers to the standard implementation and delete the deprecated code to prevent duplication and reduce maintenance overhead.

---
### 6. Optimize File Uploading `RequestBody` Creation
**Category:** Performance / Memory Optimization
**Location:** Verify usages across Upload Services (`UploadManager`, `AchievementUploader`, `UploadRepositoryImpl`)
**Task:** Ensure all file upload handlers utilize `file.asRequestBody(mediaType)`. Remove any legacy paths that load entire files into memory via `file.readBytes().toRequestBody(mediaType)`, preventing OOM exceptions on low-memory devices during large media uploads.

---
### 7. Refactor ViewModels to Inject `@ApplicationContext` Properly
**Category:** Dependency Injection / Context Leaks
**Location:** Review all ViewModels currently injecting Context.
**Task:** Verify that ViewModels requiring `Context` (e.g., `LifeViewModel`, `NotificationsViewModel`, `ResourceViewerViewModel`) properly use the `@ApplicationContext` qualifier in their constructors via Hilt. Ensure that Activity/Fragment Contexts are never passed into ViewModels as method parameters to prevent severe memory leaks.

---
### 8. Optimize String Formatting for Numerical/Duration Data
**Category:** Performance / Code Health
**Location:** Utility classes and string extensions.
**Task:** Locate high-frequency uses of `String.format` for simple tasks like hex encoding or duration formatting. Replace them with dedicated bitwise loops or ensure `String.format` is strictly localized (e.g., `Locale.US`) to prevent locale-specific crashes and reduce object allocation overhead on hot paths.

---
### 9. Safely Move Network I/O Stream Reads to Background Dispatchers
**Category:** Performance / ANR Prevention
**Location:** Retrofit response handlers reading `ResponseBody.string()`
**Task:** Identify any Retrofit synchronous stream reads (like `response.body()?.string()`) that are executed outside of a background dispatcher context. Wrap these specific calls in `withContext(dispatcherProvider.io)` to prevent `NetworkOnMainThreadException` and main thread stutters.

---
### 10. Centralize RecyclerView Adapter Caching Logic
**Category:** UI Performance / RecyclerView
**Location:** Fragment base classes and heavy fragments (e.g., `CoursesFragment`, `StorageCategoryDetailFragment`)
**Task:** Ensure dynamic configurations (e.g., `setListener`, `submitList`) are applied outside the adapter's initial `::isInitialized` or null-check blocks. This guarantees that when an adapter instance is cached and reused after view recreation, it receives the latest listeners and data without requiring a full reinstantiation.

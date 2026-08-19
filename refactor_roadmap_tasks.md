# Refactor Roadmap: 10 Low-Hanging Fruit Tasks

This report provides a set of 10 granular, low-risk, easily reviewable tasks designed to advance our refactoring roadmap while minimizing merge conflicts during PR review rounds.

---

### Task 1: Replace Blocking SharedPreferences `.commit()` with Asynchronous `.apply()` in `SharedPrefManager.kt`
* **Roadmap Category**: 1. Finish Cleaning the Data Layer / 7. Optimize Remaining Performance Hotspots
* **Focus Area**: Data Layer & Disk I/O Performance
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt`
* **Description**: Replace synchronous `.commit()` calls with asynchronous `.apply()` when persisting non-critical preference updates. Calling `.commit()` forces synchronous disk I/O blocks on the calling thread, whereas `.apply()` updates the in-memory cache synchronously and writes to disk asynchronously in the background.
* **Why it's a Quick Win**: Prevents main/UI thread stutter and reduces disk I/O latency without altering preference data structures or public method signatures.

---

### Task 2: Switch Checkpoint Removal in `TransactionSyncManager.kt` from `.commit()` to `.apply()`
* **Roadmap Category**: 5. Consolidate Sync and Upload Workflow / 7. Optimize Remaining Performance Hotspots
* **Focus Area**: Sync Workflow & Threading
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
* **Description**: During paginated CouchDB table syncs, clearing checkpoint keys in `rawPreferences` uses `.commit()`, introducing repeated synchronous disk I/O blocks on the hot sync path. Converting these calls to `.apply()` unblocks sync iterations.
* **Why it's a Quick Win**: Highly scoped line change in sync cleanup logic that accelerates overall sync throughput without affecting CouchDB transaction safety.

---

### Task 3: Replace `notifyDataSetChanged()` with `ListAdapter.submitList()` in `SurveyFragment.kt`
* **Roadmap Category**: 6. Migrate UI Incrementally to Compose / 7. Optimize Remaining Performance Hotspots
* **Focus Area**: DiffUtil / ListAdapter
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
* **Description**: `SurveyFragment.kt` currently calls `getAdapter().notifyDataSetChanged()` when list items update, bypassing DiffUtil and causing full `RecyclerView` item rebinds. Replacing this with `submitList()` utilizes `DiffUtils.itemCallback` for granular item animations and updates.
* **Why it's a Quick Win**: Pure UI dispatch improvement that eliminates list flickering and unnecessary layout passes.

---

### Task 4: Standardize DiffUtil Callbacks Using `DiffUtils.itemCallback` in `CoursesProgressAdapter.kt`
* **Roadmap Category**: 8. Improve Code Health and Add Tests / 7. Optimize Remaining Performance Hotspots
* **Focus Area**: DiffUtil / ListAdapter
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt`
* **Description**: Standardize manual item comparison logic in `CoursesProgressAdapter.kt` by using the project's centralized `DiffUtils.itemCallback` or `DiffUtils.standardItemCallback`.
* **Why it's a Quick Win**: Reduces adapter boilerplate, eliminates duplicate equality checks, and ensures consistent diffing behavior across course views.

---

### Task 5: Enforce Background Dispatcher Scoping for Date/String Formatting in `VoicesRepositoryImpl.kt`
* **Roadmap Category**: 1. Finish Cleaning the Data Layer / 3. Expand ViewModel and Use Layers
* **Focus Area**: Threading & Dispatchers Usage
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`
* **Description**: Ensure date formatting and timestamp transformations inside `VoicesRepositoryImpl.kt` execute off the main thread by appending `.flowOn(dispatcherProvider.io)` or scoping operations inside `withContext(dispatcherProvider.io)`.
* **Why it's a Quick Win**: Guarantees that formatting logic does not block UI coroutines when rendering large lists of community posts.

---

### Task 6: Inject `@ApplicationContext Context` into `DashboardViewModel.kt` to Remove UI Context Passing
* **Roadmap Category**: 3. Expand ViewModel and Use Layers / 4. Complete Dependency Injection Cleanup
* **Focus Area**: ViewModels & Dependency Injection
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`
* **Description**: Refactor methods in `DashboardViewModel` that accept a `Context` parameter from Fragments/Activities. Instead, inject `@ApplicationContext private val context: Context` directly into the ViewModel constructor via Hilt.
* **Why it's a Quick Win**: Decouples UI controllers from ViewModel method contracts and prevents potential Fragment/Activity context leaks.

---

### Task 7: Replace Hardcoded Coroutine Dispatchers with Injectable `DispatcherProvider` in `MainApplication.kt`
* **Roadmap Category**: 4. Complete Dependency Injection Cleanup / 8. Improve Code Health and Add Tests
* **Focus Area**: Dependency Injection & Coroutine Dispatchers
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`
* **Description**: Replace remaining direct `Dispatchers.IO` / `Dispatchers.Main` references in background initialization tasks with the injected `DispatcherProvider` (`dispatcherProvider.io` / `dispatcherProvider.main`).
* **Why it's a Quick Win**: Unblocks deterministic testing using `TestDispatcherProvider` without changing application runtime logic.

---

### Task 8: Scope Fragment Flow Collectors to `viewLifecycleOwner.lifecycleScope` in UI Fragments
* **Roadmap Category**: 2. Introduce Global Navigation Architecture / 8. Improve Code Health and Add Tests
* **Focus Area**: Long Running Observers or Listeners
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
* **Description**: Audit Flow observation calls to ensure they use `viewLifecycleOwner.lifecycleScope` in combination with `collectWhenStarted` or `collectLatestWhenStarted` rather than fragment-level `lifecycleScope`.
* **Why it's a Quick Win**: Prevents background flow collection leaks after fragment views are destroyed, avoiding duplicate emissions on fragment view recreation.

---

### Task 9: Optimize High-Frequency String Hex Formatting Loops in Utility Loggers
* **Roadmap Category**: 7. Optimize Remaining Performance Hotspots
* **Focus Area**: Micro-Optimizations & Performance Quick Wins
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt`
* **Description**: Replace runtime `String.format("%02x", ...)` allocations inside high-frequency iteration loops with lightweight bitwise character conversions or direct `StringBuilder` appends.
* **Why it's a Quick Win**: Eliminates String format string parsing overhead and short-lived allocations on hot execution paths.

---

### Task 10: Preserve Adapter Instances Across Fragment View Re-creations in `SurveyFragment.kt`
* **Roadmap Category**: 6. Migrate UI Incrementally to Compose / 7. Optimize Remaining Performance Hotspots
* **Focus Area**: DiffUtil / ListAdapter & RecyclerView Reuse
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
* **Description**: Avoid instantiating a new adapter object every time `onCreateView`/`onViewCreated` executes if the cached adapter instance is still valid.
* **Why it's a Quick Win**: Preserves `RecyclerView` view pools and allows `AsyncListDiffer` to diff old and new lists accurately without triggering full list rebinds.

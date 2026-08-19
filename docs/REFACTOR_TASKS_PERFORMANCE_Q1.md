# Performance Quick Wins - Refactor Tasks

**Focus Areas:** DI, Data Layers, DiffUtil/ListAdapter, ViewModels, Threading/Dispatchers, Long-running Observers
**Goal:** Low-hanging fruits, granular PRs, minimal merge conflicts

---

## Task 1: Fix `SurveyFragment` Bypassing DiffUtil with `notifyDataSetChanged()`

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
**Lines:** 180, 185

**Issue:**
```kotlin
collectWhenStarted(viewModel.surveyInfos) { infos ->
    surveyInfoMap.clear()
    surveyInfoMap.putAll(infos)
    getAdapter().notifyDataSetChanged()  // <-- defeats ListAdapter
}
```

**Problem:** `SurveysAdapter` is a `ListAdapter` but external maps (`surveyInfoMap`, `bindingDataMap`) are mutated and `notifyDataSetChanged()` is called, bypassing DiffUtil entirely.

**Fix:** Refactor `SurveysAdapter` to accept a combined data model that includes all display data. Let DiffUtil detect changes naturally.

**PR Title:** `fix(surveys): integrate external maps into adapter data model to enable DiffUtil`

---

## Task 2: Add `distinctUntilChanged()` to BellDashboardViewModel Network Status Flow

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt`

**Issue:** `networkStatus` StateFlow is collected and updates UI even when value hasn't changed.

**Fix:** Apply `distinctUntilChanged()` operator:
```kotlin
val networkStatus: StateFlow<NetworkStatus> = _networkStatus
    .distinctUntilChanged()
    .stateIn(...)
```

**PR Title:** `perf(dashboard): add distinctUntilChanged to networkStatus flow to reduce recompositions`

---

## Task 3: Replace Inner `MemberMenuAdapter` ArrayAdapter with Simple Custom View

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt`
**Lines:** 207-216

**Issue:** Uses `ArrayAdapter` for a simple static list of 1-2 menu items, which is overkill and creates unnecessary adapter overhead.

**Fix:** Replace with a simple custom `RecyclerView` or `LinearLayout` approach, or use `AlertDialog` with `ArrayAdapter<CharSequence>` passing items directly.

**PR Title:** `refactor(teams): simplify MemberMenuAdapter to avoid ArrayAdapter overhead`

---

## Task 4: Add `@JvmOverloads` or Secondary Constructor to CoursesAdapter for Optional Parameters

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`

**Issue:** Multiple call sites may need to create `CoursesAdapter` with only some parameters, potentially causing inconsistency.

**Fix:** Audit call sites and ensure consistent adapter initialization patterns. Consider `@JvmOverloads` for nullable parameters.

**PR Title:** `refactor(courses): audit CoursesAdapter initialization patterns for consistency`

---

## Task 5: Extract Constant Strings in `CoursesAdapter` to Companion Object

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`

**Issue:** String resources like `"course_steps_count"`, `"steps_done_of_total"` are retrieved via `context.getString()` inside bind methods, causing repeated resource lookups.

**Fix:** Pre-fetch commonly used strings in `init` block or companion object:
```kotlin
companion object {
    private var stepsDoneTemplate: String? = null
    // Lazy load on first bind or in init
}
```

**PR Title:** `perf(courses): cache frequently used string resources in CoursesAdapter`

---

## Task 6: Add `setHasFixedSize(true)` to RecyclerViews in Fragment `onViewCreated`

**Files:** Multiple fragments using `RecyclerView`

**Issue:** RecyclerViews don't declare fixed size when adapter size doesn't change dynamically, causing unnecessary layout passes.

**Fix:** Add `recyclerView.setHasFixedSize(true)` where applicable (when list size is determined by adapter alone).

**PR Title:** `perf(ui): add setHasFixedSize(true) to static RecyclerViews`

---

## Task 7: Convert `TeamsTasksAdapter` DiffUtil to Use `DiffUtils.itemCallback`

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksAdapter.kt`

**Issue:** Check if adapter is using inline DiffUtil callback instead of the project's `DiffUtils.itemCallback` helper.

**Fix:** Ensure all adapters use `DiffUtils.itemCallback` or `DiffUtils.standardItemCallback` for consistency and potential future improvements.

**PR Title:** `refactor(teams): use project DiffUtils helper in TeamsTasksAdapter`

---

## Task 8: Add `Job` Cancellation in `ResourcesViewModel.observeOpenedResourcesJob`

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`
**Lines:** 52, 62

**Issue:** `observeOpenedResourcesJob` is launched but may not be properly cancelled on ViewModel clear.

**Fix:** Ensure job is cancelled in `onCleared()` or use `Job.cancel()` pattern:
```kotlin
override fun onCleared() {
    observeOpenedResourcesJob?.cancel()
    super.onCleared()
}
```

**PR Title:** `perf(resources): properly cancel observeOpenedResourcesJob in ResourcesViewModel`

---

## Task 9: Audit `Dispatchers.IO` Usage in Repository Layer

**Files:** `app/src/main/java/org/ole/planet/myplanet/repository/*.kt`

**Issue:** Some repository methods may be using `Dispatchers.IO` directly instead of injected `DispatcherProvider.io`.

**Fix:** Search for `Dispatchers.IO` in repository files and replace with `dispatcherProvider.io`:
```kotlin
// Before
withContext(Dispatchers.IO) { ... }

// After  
withContext(dispatcherProvider.io) { ... }
```

**PR Title:** `perf(repositories): use DispatcherProvider instead of hardcoded Dispatchers.IO`

---

## Task 10: Add `Lifecycle.State` Comments to Flow Collection Patterns

**Files:** Multiple fragments using `collectWhenStarted`

**Issue:** Unclear which lifecycle state is used for flow collection across the codebase.

**Fix:** Add KDoc comments to `FlowExtensions.kt` explaining `STARTED` vs `RESUMED` lifecycle behavior, and audit if any collections should use `repeatOnLifecycle(Lifecycle.State.RESUMED)` instead for more precise control.

**PR Title:** `docs: clarify lifecycle states for flow collection patterns`

---

## Summary Table

| # | Task | Area | Files | Estimated Size |
|---|------|------|-------|---------------|
| 1 | Fix SurveyFragment DiffUtil bypass | DiffUtil | 1 | Small |
| 2 | Add distinctUntilChanged | Flow | 1 | Tiny |
| 3 | Simplify MemberMenuAdapter | Adapter | 1 | Tiny |
| 4 | Audit CoursesAdapter init | Adapter | 1-2 | Small |
| 5 | Cache string resources | Perf | 1 | Small |
| 6 | setHasFixedSize | RecyclerView | 5-8 | Small |
| 7 | Use DiffUtils helper | DiffUtil | 1 | Tiny |
| 8 | Cancel observeOpenedResourcesJob | Lifecycle | 1 | Tiny |
| 9 | Use DispatcherProvider | Threading | 5-10 | Medium |
| 10 | Document lifecycle states | Docs | 1 | Tiny |

**Total Estimated PRs:** 10 (all small/medium, easily reviewable)

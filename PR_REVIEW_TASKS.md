# PR Review Round Tasks - 10 Micro-Optimization Tasks

Focus: Performance quick wins, micro-optimizations, avoiding merge conflicts

---

## Task 1: Consolidate Flow Collections in `ChatDetailFragment.observeAiProviders()`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`
**Lines**: ~358-396

**Issue**: 3 separate `collect` calls in a `repeatOnLifecycle` block that could be combined using `combine()`.

**Change**: Replace 3 separate `collect` blocks with a single `combine()` call:
```kotlin
combine(
    sharedViewModel.aiProviders,
    sharedViewModel.aiProvidersLoading,
    sharedViewModel.aiProvidersError
) { providers, isLoading, hasError -> Triple(providers, isLoading, hasError) }
    .collect { (providers, isLoading, hasError) -> /* handle all 3 */ }
```

**Impact**: Reduces coroutine overhead, improves code readability.

---

## Task 2: Consolidate Flow Collections in `ChatDetailFragment.observeViewModelData()`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`
**Lines**: ~398-436

**Issue**: 4 separate `collect` calls that could be combined.

**Change**: Combine `selectedChatHistory`, `selectedAiProvider`, `selectedId`, `selectedRev` using `combine()` or a data class for the UI state.

**Impact**: Single emission pattern is more efficient and atomic.

---

## Task 3: Consolidate Flow Collections in `SurveyFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
**Lines**: ~180-224

**Issue**: 6 separate `collect` calls inside `repeatOnLifecycle`.

**Change**: Group related state:
- `surveys` + `isLoading` → `combine()`
- `surveyInfos` + `bindingData` → `combine()` 
- `errorMessage` + `userMessage` → `combine()`

**Impact**: Reduces job overhead, enables atomic UI updates.

---

## Task 4: Consolidate Flow Collections in `SubmissionDetailFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt`
**Lines**: ~81-111

**Issue**: 5 separate `collect` calls for simple UI bindings.

**Change**: Create a `SubmissionDetailUiState` data class and use `combine()` to emit all fields at once.

**Impact**: Single emission = single UI update = less layout passes.

---

## Task 5: Consolidate Flow Collections in `NotificationsFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt`
**Lines**: ~85-115

**Issue**: 4 separate `collect` calls.

**Change**: Combine `groupedItems`, `unreadCount`, `isSelectionMode`, `selectedCount` into a single UI state using `combine()`.

**Impact**: Reduces individual emissions and UI updates.

---

## Task 6: Consolidate Flow Collections in `RatingsFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt`
**Lines**: ~80-115

**Issue**: 3 separate `collect` calls for `ratingState`, `userState`, `submitState`.

**Change**: Create `RatingsUiState` data class and use `combine()`.

**Impact**: Cleaner code, single source of truth for UI state.

---

## Task 7: Consolidate Flow Collections in `TeamCalendarFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarFragment.kt`
**Lines**: ~215-260

**Issue**: 2 separate `collect` calls for `meetups` and `createMeetupResult`.

**Change**: Consider combining if these affect the same UI elements, or at minimum ensure both use `collectWhenStarted`.

**Impact**: Consistent pattern usage.

---

## Task 8: Consolidate Flow Collections in `MembersFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt`
**Lines**: ~85-100

**Issue**: 2 separate `collect` calls for `uiState` and `successAction`.

**Change**: Consider combining into single state if they update same UI elements.

**Impact**: Cleaner flow management.

---

## Task 9: Consolidate Flow Collections in `RequestsFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt`
**Lines**: ~49-60

**Issue**: 2 separate `collect` calls.

**Change**: Consider combining into single `RequestsUiState` for atomic updates.

**Impact**: Single emission = single UI update.

---

## Task 10: Consolidate Flow Collections in `TeamsVoicesFragment`

**File**: `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt`
**Lines**: ~130-135

**Issue**: 2 separate `collect` calls for `discussions` and `createNewsSuccess`.

**Change**: Consider combining or using `launchIn` properly with lifecycle awareness.

**Impact**: Consistent pattern, proper lifecycle handling.

---

## Summary

All 10 tasks focus on:
1. **Flow consolidation** - reducing multiple `collect` calls to `combine()` patterns
2. **Low risk** - pure refactoring, no business logic changes
3. **No merge conflicts** - each file is modified independently
4. **Easy to review** - single pattern repeated across multiple files
5. **Performance benefit** - reduces coroutine overhead and UI update passes

**Priority order**: Start with Task 1-4 (most impact), then 5-10.

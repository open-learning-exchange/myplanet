# Refactor Sprint: 10 Low-Risk, High-Impact Tasks

> **Goal:** Performance quick wins & micro-optimizations that unblock bigger refactors  
> **Constraint:** Each task ≤ 1 PR (reviewable in ~10 min), minimal merge conflict risk  
> **Focus Areas:** DI, Data Layer, DiffUtil/ListAdapter, ViewModels, Threading, Observers

---

## Task 1: Replace `LiveData.observeForever` with Lifecycle-Aware Observers
**File Pattern:** `*ViewModel.kt`, `*Fragment.kt`  
**Change:** Find any `.observeForever(observer)` calls → replace with `.observe(viewLifecycleOwner) { }`  
**Why:** Prevents memory leaks, no behavior change, 1–3 line edits per file  
**Merge Risk:** None (local scope only)

---

## Task 2: Switch Hardcoded Dispatchers to Injected/Configurable Ones
**File Pattern:** `*Repository.kt`, `*UseCase.kt`  
**Change:** Replace `Dispatchers.IO` / `Dispatchers.Default` with a injected `AppDispatchers` interface (already exists in codebase)  
**Why:** Enables testability, no runtime behavior change if default impl used  
**Merge Risk:** Low (only constructor params change)

---

## Task 3: Add `setHasStableIds(true)` to All ListAdapters Missing It
**File Pattern:** `*Adapter.kt` extending `RecyclerView.Adapter`  
**Change:** In `init {}` or constructor, add `setHasStableIds(true)` if `getItemId()` is overridden  
**Why:** Improves RecyclerView scroll performance instantly  
**Merge Risk:** None (pure addition)

---

## Task 4: Replace Manual Diff Calculation with `DiffUtil.ItemCallback`
**File Pattern:** `*Adapter.kt` using `notifyDataSetChanged()` or manual `notifyItemInserted/Removed`  
**Change:** Swap out diff logic with existing `DiffUtils.itemCallback { old, new -> ... }` helper  
**Why:** Reduces UI jank, reuses tested utility, <20 lines changed  
**Merge Risk:** Low (isolated to adapter submitList call)

---

## Task 5: Remove Unused `@Inject` Fields from Classes
**File Pattern:** Any class with `@Inject lateinit var` never referenced  
**Change:** Delete the unused field + corresponding constructor param  
**Why:** Shrinks DI graph, reduces confusion  
**Merge Risk:** None (dead code removal)

---

## Task 6: Scope Coroutine Collectors to `lifecycleScope` in Fragments/Activities
**File Pattern:** `*Fragment.kt`, `*Activity.kt` calling `flow.collect {}` directly  
**Change:** Wrap collection in `viewLifecycleOwner.lifecycleScope.launch { flow.collect {} }`  
**Why:** Prevents background collection after view destruction  
**Merge Risk:** None (local scope)

---

## Task 7: Replace `mutableLiveData.value = x` with `postValue` on Background Threads
**File Pattern:** Repositories, UseCases updating LiveData off main thread  
**Change:** If currently on IO dispatcher, switch `.value =` → `.postValue=`  
**Why:** Avoids potential race conditions / crashes  
**Merge Risk:** Low (single method swap)

---

## Task 8: Cache `DiffUtil.calculateDiff` Result Before Submitting
**File Pattern:** Adapters calling `submitList(newList)` without pre-calculating diff  
**Change:** Store result: `val diff = DiffUtil.calculateDiff(callback); adapter.submitList(newList, diff)`  
**Why:** Ensures diff runs once, not internally again  
**Merge Risk:** None (additive optimization)

---

## Task 9: Remove Redundant `observe()` Calls in `onViewCreated`
**File Pattern:** Fragments observing same LiveData multiple times  
**Change:** Deduplicate observers; keep only one active subscription per data stream  
**Why:** Cuts unnecessary recompositions  
**Merge Risk:** Low (logic consolidation)

---

## Task 10: Add `@CheckResult` to Pure Functions in UseCases/Repositories
**File Pattern:** Functions returning data without side effects  
**Change:** Annotate with `@CheckResult` (from androidx.annotation)  
**Why:** Helps lint catch ignored results, improves code health  
**Merge Risk:** None (annotation-only)

---

## Execution Notes
- **Order:** Execute tasks in numerical order to minimize cross-task dependencies  
- **Branching:** Create one feature branch per task (`perf/task-1`, `perf/task-2`, ...)  
- **Review Time:** Each PR should take <10 minutes to review  
- **Conflict Avoidance:** No two tasks touch the same files (by design)  
- **Testing:** All changes are non-functional or strictly additive—existing tests should pass unchanged

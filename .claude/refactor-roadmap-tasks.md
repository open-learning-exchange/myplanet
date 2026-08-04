# Refactor Roadmap — 10 Reviewable Tasks

> Goal: low-hanging fruit that reinforces repository boundaries, removes cross-feature data leaks, and moves data concerns out of UI/services into repositories.  
> Constraint: ~10 reviewable PRs, avoid merge conflicts, no unused code, keep each change granular.

```
---
```

## 1. Move `RealtimeSyncMixin` data fan-out into `TeamsRepository` / `ResourcesRepository`

**What:** `RealtimeSyncMixin` currently calls `refreshWithDiff()` on UI adapters directly after receiving `TableDataUpdate` events. The adapter refresh logic reaches across features to decide which repository to query.

**Do:** Add `observeTableUpdates(tableNames: List<String>): Flow<TableDataUpdate>` to the relevant repositories (start with `TeamsRepository` and `ResourcesRepository`). Let fragments consume a repository-managed `Flow` instead of `RealtimeSyncManager.getInstance()`.

**Why:** Closes a UI→service leak; only repositories know how to map a table update to fresh domain data.

**Files likely touched:** `RealtimeSyncMixin.kt`, `TeamsRepository.kt`, `ResourcesRepository.kt`, affected fragments.

```
---
```

## 2. Stop passing `ApiInterface` into `SyncManager`; route through `SyncRepository`

**What:** `SyncManager` holds `ApiInterface` directly and orchestrates server pulls (`processShelfParallel`, `processShelfDataOptimizedSync`).

**Do:** Expand the existing `SyncRepository` interface with the shelf/pull operations `SyncManager` needs, implement in `TeamsRepositoryImpl` or a dedicated `SyncRepositoryImpl`, and inject `SyncRepository` into `SyncManager`.

**Why:** Tightens the data layer boundary; `SyncManager` coordinates state only, repositories own network calls.

**Files likely touched:** `SyncRepository.kt`, `SyncManager.kt`, repository implementations, `ServiceModule.kt`.

```
---
```

## 3. Remove remaining `notifyDataSetChanged()` calls in `CoursesPagerAdapter` and `TeamPagerAdapter`

**What:** Both pager adapters still call `notifyDataSetChanged()` inside `submitList`.

**Do:** Replace with `ListAdapter` + `DiffUtils.itemCallback`/`DiffUtils.standardItemCallback`. Provide stable IDs; if the pager host cannot use `ListAdapter`, switch to `RecyclerView.Adapter` with a calculated `DiffUtil.DiffResult` and `dispatchUpdatesTo(this)`.

**Why:** Low-risk RecyclerView perf fix; exercises the shared `DiffUtils` helper.

**Files likely touched:** `CoursesPagerAdapter.kt`, `TeamPagerAdapter.kt`, maybe host fragments.

```
---
```

## 4. Convert legacy `RecyclerView.Adapter`s to `ListAdapter` using `DiffUtils`

**What:** Several adapters still use custom diff logic or `notifyItemChanged` heavy updates: `ResourcesAdapter`, `CoursesAdapter`, `SurveysAdapter`, `VoicesAdapter`, `ChatAdapter`, `ChatHistoryAdapter`.

**Do:** Pick one feature package at a time (e.g. `ui/resources/` first, then `ui/courses/`). Standardize on `ListAdapter<T, VH>(DiffUtils.itemCallback { ... })`. Move payload generation into the adapter, not the fragment.

**Why:** Removes duplicate diff callbacks and reduces fragment code; consistent with `BaseRecyclerFragment`.

**Files likely touched:** Adapters in `resources/`, `courses/`, `surveys/`, `voices/`, `chat/`; their fragment hosts.

```
---
```

## 5. Move direct `SharedPreferences`/`SharedPrefManager` reads out of UI into repositories

**What:** Many fragments/activities still read `SharedPrefManager`/`SharedPreferences` directly for userId, server config, and filters.

**Do:** Introduce small repository methods such as `UserRepository.getCurrentUserId()` / `ConfigurationsRepository.getCurrentServer()` and inject them where needed. Start with the highest-leak files: `LoginActivity`, `TeamFragment`, `ResourcesFragment`, `CommunityTabFragment`, `SettingsActivity`.

**Why:** Hides persistence keys and schema; UI only sees typed domain getters.

**Files likely touched:** `UserRepository.kt`, `ConfigurationsRepository.kt`, UI fragments above, `RepositoryModule.kt` if new interfaces are added.

```
---
```

## 6. Replace hard-coded `Dispatchers.IO/Main` usage with injected `DispatcherProvider`

**What:** Search shows only `DispatcherProvider.kt` contains explicit `Dispatchers.*` references, but check usages in services (e.g. `UploadManager`, `TransactionSyncManager`, `SyncManager`) and any `viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO)` patterns.

**Do:** Wherever a service/VM/fragment hard-codes `Dispatchers.IO`/`Dispatchers.Main`, inject or receive `DispatcherProvider` and use `dispatcherProvider.io` / `.main`. Keep the change to one vertical slice per PR.

**Why:** Testability and consistent threading; enables `TestDispatcherProvider` in unit tests.

**Files likely touched:** `SyncManager.kt`, `UploadManager.kt`, `TransactionSyncManager.kt`, `BaseRecyclerFragment.kt`, any affected ViewModels.

```
---
```

## 7. Shrink `ServiceModule` constructor-injection bloat

**What:** `ServiceModule` manually constructs `SyncManager` and `UploadManager` with 15+ dependencies each, mirroring the class constructors exactly.

**Do:** Add `@Inject` constructors to `SyncManager` and `UploadManager` and delete their `provide*` methods in `ServiceModule`. Keep `RealtimeSyncManager` provider because it uses `getInstance()`.

**Why:** Removes boilerplate and makes constructor changes less error-prone; pure DI cleanup.

**Files likely touched:** `SyncManager.kt`, `UploadManager.kt`, `ServiceModule.kt`.

```
---
```

## 8. Move `ProcessUserDataActivity` and `GuestLoginExtensions` DB touches into `UserRepository`

**What:** `ProcessUserDataActivity` and `GuestLoginExtensions` still touch `DatabaseService`/`AppDatabase` directly from the UI layer.

**Do:** Add `UserRepository.processGuestLogin()` and `UserRepository.processUserData(...)` methods; move the direct DB calls there. UI calls the repository method inside a `lifecycleScope.launch`.

**Why:** Removes the last explicit database access from UI; keeps Room access behind repository interfaces.

**Files likely touched:** `ProcessUserDataActivity.kt`, `GuestLoginExtensions.kt`, `UserRepository.kt`/`UserRepositoryImpl.kt`, `UserSyncRepository.kt`.

```
---
```

## 9. Extract remaining `UploadManager` data operations into `UploadRepository`

**What:** `UploadManager` mixes upload orchestration with direct repository calls (`submissionsRepository`, `personalsRepository`, `voicesRepository`, `resourcesRepository`, etc.).

**Do:** Add batch upload helpers to `UploadRepository` for the data types `UploadManager` currently pushes directly. Let `UploadManager` call `uploadRepository.uploadPendingSubmissions()`, `uploadPendingNews()`, etc. Start with one type per PR.

**Why:** Repositories own the data mutation + network mapping; `UploadManager` only schedules/coordinates.

**Files likely touched:** `UploadRepository.kt`, `UploadRepositoryImpl.kt`, `UploadManager.kt`, `ServiceModule.kt`.

```
---
```

## 10. Audit and close long-lived observers in `BaseRecyclerFragment` and list fragments

**What:** `BaseRecyclerFragment` and several list fragments set adapters in `onViewCreated` and may keep observers/listeners active across configuration changes. Some adapters capture `context`/`activity`.

**Do:**
- In `BaseRecyclerFragment`, clear `recyclerView.adapter = null` in `onDestroyView` and null out listeners.
- In `LifeFragment`, `NotificationsFragment`, `SubmissionsFragment`, ensure any `LiveData`/`Flow` collection uses `viewLifecycleOwner` and is cancelled properly.
- Remove `context`/`activity` fields from adapters where possible; use `LayoutInflater` from parent or a `DiffUtils` callback interface.

**Why:** Reduces memory leaks and observer churn; safe because no business logic changes.

**Files likely touched:** `BaseRecyclerFragment.kt`, `BaseRecyclerParentFragment.kt`, `LifeFragment.kt`, `NotificationsFragment.kt`, `SubmissionsFragment.kt`, leaky adapters.

```
---
```

## Suggested review order (to avoid conflicts)

1. Task 7 (`ServiceModule` inject constructors) — touches only DI wiring; merge first.  
2. Task 6 (`DispatcherProvider`) — small mechanical replacements.  
3. Task 3 (`notifyDataSetChanged` pagers) — isolated adapter files.  
4. Task 4 (`ListAdapter` conversions) — one feature package per PR.  
5. Task 5 (`SharedPreferences` behind repositories) — data layer + UI.  
6. Task 8 (`ProcessUserDataActivity` DB moves) — UI + `UserRepository`.  
7. Task 2 (`SyncManager` API boundary) — sync layer.  
8. Task 9 (`UploadManager` → `UploadRepository`) — upload layer.  
9. Task 1 (`RealtimeSyncMixin` repository fan-out) — UI + repositories.  
10. Task 10 (observer/adapter cleanup) — mostly UI/adapter-only changes.

> Total: 10 tasks, each a small, reviewable PR. No new features, no unused code.

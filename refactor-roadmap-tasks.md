# Refactor Roadmap — Low-Hanging Fruit Tasks

> Scope: performance quick wins, micro-optimizations that unblock bigger refactors later, and obvious inefficiency removal.  No large rewrites, no unused code, granular PRs that avoid merge conflicts in a single review round.

---

## 1. Move heavy list building off the main thread in `ChatViewModel`

**Files**: `ui/chat/ChatViewModel.kt`

**What**: `loadChatHistoryScreenData()` does `sortChats()` and `buildPrecomputedChats()` inside `withContext(dispatcherProvider.default)`, but the heavy filtering in `searchChats()` still runs on `viewModelScope` (main). Move `fullConvoSearch` / `searchByTitle` to `dispatcherProvider.default`.

**Why**: Chat history search performs multiple `normalizeText()` calls and list allocations on every keystroke on the main thread; this causes jank on large histories and unblocks later search architecture refactors.

---

## 2. Avoid reloading the whole user model on every `BaseRecyclerFragment` setup

**Files**: `base/BaseRecyclerFragment.kt`, `ui/resources/ResourcesFragment.kt`, `ui/courses/CoursesFragment.kt`, `ui/teams/TeamFragment.kt`

**What**: Cache the current `UserEntity` in the ViewModel/fragment and replace repeated `userRepository.getUserModel()` / `userSessionManager.getUserModel()` calls inside `lifecycleScope.launch` blocks with the cached instance. Read it once in `onViewCreated` and pass it to adapter factories.

**Why**: User model loading hits Room on the IO dispatcher multiple times per screen creation; caching removes redundant DB reads and makes later DI/user-data cleanup easier.

---

## 3. Convert remaining `RecyclerView.Adapter` holdouts to `ListAdapter` with `DiffUtils.itemCallback`

**Files**: `ui/sync/ServerAddressAdapter.kt`, `ui/dashboard/DashboardSurveysAdapter.kt`, `ui/onboarding/OnboardingAdapter.kt`, `ui/personals/PersonalsAdapter.kt`, `ui/settings/StorageCategoryDetailFragment.kt`

**What**: These adapters still extend `RecyclerView.Adapter` and call `notifyDataSetChanged()` (or reassign the list manually). Migrate each to `ListAdapter` using the existing `DiffUtils.itemCallback` helper, keeping the same item/payload semantics.

**Why**: Full dataset refreshes during sync and configuration changes waste layout passes and are a blocker for incremental Compose migration later.

---

## 4. Use payloads instead of full rebinds in `VoicesAdapter`

**Files**: `ui/voices/VoicesAdapter.kt`, `ui/voices/VoicesFragment.kt`

**What**: `VoicesAdapter` recomputes `RowState` and rebinds the entire row on every `submitList` even when only reply counts, leader status, or user images change. Add a payload for `PAYLOAD_REPLY_COUNT` / `PAYLOAD_USER_FETCHED` / `PAYLOAD_TEAM_LEADER_CHANGED` and only update the affected view(s).

**Why**: Voices lists are long-lived and update frequently; full rebinds on every micro-change are a measurable frame cost and the adapter is on the critical path for later news-feed refactors.

---

## 5. Replace `lifecycleScope.launch` + `withContext(dispatcherProvider.io)` anti-pattern with direct suspend calls or `viewModelScope`

**Files**: `ui/resources/ResourcesFragment.kt`, `ui/viewer/ResourceViewerFragment.kt`, `ui/sync/SyncActivity.kt`, `ui/exam/ExamTakingFragment.kt`, `ui/components/MarkdownDialogFragment.kt`

**What**: Move IO-bound work currently wrapped in `lifecycleScope.launch { withContext(dispatcherProvider.io) { ... } }` into the relevant ViewModel (e.g. `ResourcesViewModel`, `ResourceViewerViewModel`, `ExamTakingViewModel`) and expose a `StateFlow`/`SharedFlow` or suspend result. Fragments should only collect the result.

**Why**: Mixing dispatchers in fragments creates leaked scope risks, complicates testing, and duplicates work across configuration changes. Centralizing this is a prerequisite for cleaner ViewModel/use-case layers.

---

## 6. Remove redundant `recyclerView.adapter = getAdapter()` re-creations

**Files**: `base/BaseRecyclerFragment.kt`, `ui/resources/ResourcesFragment.kt`, `ui/courses/CoursesFragment.kt`, `ui/teams/TeamFragment.kt`

**What**: `BaseRecyclerFragment.getAdapter()` creates a fresh adapter every time it is called (e.g. in `onRatingChanged`, `postAddRefresh`). Keep a single adapter instance and call `submitList()` instead of assigning a new adapter.

**Why**: Adapter re-creation throws away `RecycledViewPool`, diff state, and selection state; this is a direct performance regression and blocks stable list architecture work.

---

## 7. Cancel stale coroutine jobs in long-lived fragments

**Files**: `ui/resources/ResourcesFragment.kt`, `ui/courses/CoursesFragment.kt`, `ui/teams/TeamFragment.kt`, `ui/teams/tasks/TeamsTasksFragment.kt`, `ui/health/MyHealthFragment.kt`

**What**: Several fragments launch coroutines on `lifecycleScope`/`viewLifecycleOwner.lifecycleScope` without holding the `Job` and cancelling the previous invocation when a new request arrives (search, filter, refresh). Add `private var searchJob: Job?` / `refreshJob: Job?` and cancel before launching the next.

**Why**: Prevents stale results from overwriting newer results, reduces memory pressure, and is a micro-fix that unifies how screens handle concurrency before extracting use cases.

---

## 8. Move `RealtimeSyncHelper`/`RealtimeSyncMixin` collection off `viewLifecycleOwner.lifecycleScope.launch` nested inside `setupRealtimeSync`

**Files**: `ui/sync/RealtimeSyncMixin.kt`, fragments implementing `RealtimeSyncMixin` (`ResourcesFragment`, `CoursesFragment`, etc.)

**What**: `RealtimeSyncHelper.setupRealtimeSync()` already uses the lifecycle-aware `collectWhenStarted` extension, but the `refreshRecyclerView()` helper wraps the adapter refresh in an additional `viewLifecycleOwner.lifecycleScope.launch` for a one-shot operation that should happen inside the collector. Use the existing `flow` + `debounce` to keep work coalesced and avoid a second scope hop.

**Why**: Reduces scope nesting, avoids launching a new coroutine per sync event, and makes the real-time sync path easier to reason about when consolidating sync/upload workflows later.

---

## 9. Lazily load and cache `SimpleDateFormat` / `DateTimeFormatter` in adapters

**Files**: `ui/notifications/NotificationsAdapter.kt`, `ui/teams/members/MembersAdapter.kt`, `ui/teams/TeamsAdapter.kt`, `ui/chat/ChatHistoryAdapter.kt`

**What**: Adapters create or re-format dates on every bind. Move formatters to a static/lazy cached field keyed by locale (NotificationsAdapter already does this partially — apply the same pattern to the others).

**Why**: `SimpleDateFormat`/`DateTimeFormatter` allocation and locale resolution are surprisingly expensive during fast scrolling; caching is a one-line win and removes a class of GC pressure during list animations.

---

## 10. Remove unused `MutableList` copies and `toList()` snapshots in `ListAdapter` callers

**Files**: `ui/voices/VoicesFragment.kt`, `ui/chat/ChatHistoryAdapter.kt`, `ui/courses/CoursesAdapter.kt`, `ui/surveys/SurveysAdapter.kt`, `ui/teams/tasks/TeamsTasksAdapter.kt`

**What**: Several adapters still copy their `currentList` into a mutable list, mutate it, then call `submitList()`. Replace with direct `submitList(newImmutableList)` from the ViewModel/fragment, and delete the adapter-side mutable working list (e.g. `VoicesAdapter.originalList`, `LifeAdapter.workingList`, `SurveysAdapter` mutable sorts).

**Why**: Extra list copies double memory churn on every update; making adapters pure consumers of immutable lists is the first step toward a shared `DiffUtil`/`ListAdapter` pattern and later Compose integration.

---

## Suggested review order (to minimize conflicts)

1. Tasks 9 and 10 — pure adapter/utility cleanups, no architectural changes.
2. Task 3 — self-contained adapter migrations, easy to test with existing UI.
3. Task 4 — localized to Voices feature.
4. Task 6 — touches `BaseRecyclerFragment` and two callers; do before wider ViewModel moves.
5. Task 7 — add job cancellation across a few screens.
6. Task 2 — user model caching; small surface area.
7. Task 5 — move IO work out of fragments into ViewModels.
8. Task 1 — chat search threading.
9. Task 8 — real-time sync cleanup after the fragment/ViewModel flow wiring from task 5.

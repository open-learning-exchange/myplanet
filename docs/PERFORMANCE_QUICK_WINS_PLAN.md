# Performance Quick Wins — PR Round Plan

A granular, review-friendly plan of **10 low-risk tasks** targeting performance
micro-optimizations and obvious inefficiencies. Each task is scoped to one PR,
touches a small surface, and avoids the big refactors (navigation, Compose, sync
rework) so they can be reviewed and merged in parallel **without merge conflicts**.

Focus areas honored: DI, data layers, DiffUtil/ListAdapter (uses
`DiffUtils.itemCallback`), ViewModels, threading/dispatchers, long-running
observers.

Convention: every task ends with `./gradlew testDefaultDebugUnitTest` green.

---

## Task 1 — Hoist per-call `Regex` compilations into `companion`/file-level constants

**Files**
- `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt` (`stripMarkdown` recompiles ~11 patterns on every chat/TTS message)
- `app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt` (`parseApkVersionString`: `"v".toRegex()`, `"\\.".toRegex()` per call)
- `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt` (`normalizeText`: `Regex("\\p{InCombiningDiacriticalMarks}+")` per call)
- `app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt` (same diacritics `Regex(...)` per insert during sync)

**Change**
Move each `Regex(...)` / `"...".toRegex()` to a `private val` in the relevant
`companion object` (or file-level `private val`) and reference it. No behavioral
change — `Regex` is immutable and thread-safe.

**Why it matters** Regex compilation is expensive; these run in hot paths (sync
inserts, every TTS utterance, version checks on startup). Hoisting is the
canonical "unblock later, zero-risk" micro-optimization.

**Scope** ~4 files, ~15 line edits. No new APIs, no unused code.

---

## Task 2 — Replace `notifyDataSetChanged()` with targeted payload updates

**Files**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt:137` (`setViewMode`)
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt:83` (`setViewMode`)

**Change**
`setViewMode` currently calls `notifyDataSetChanged()`, which rebinds every
visible row. Since only the layout (grid vs list) changes — not the item
identity/contents — switch to a dedicated `PAYLOAD_VIEW_MODE` and
`notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE)` so `onBindViewHolder`
partial-bind handles only the layout-affecting views. The adapters already use
the payload pattern elsewhere (`PAYLOAD_SELECTION`, `PAYLOAD_PROGRESS`), so this
matches existing convention.

**Why it matters** Eliminates full rebind flicker when toggling list/grid mode
on large course/resource lists; the existing `DiffUtils.itemCallback` stays
untouched.

**Scope** 2 files, ~10 lines. Self-contained in adapters.

---

## Task 3 — Fix N+1 DAO lookup in `CoursesRepositoryImpl.flushPendingCourseResources`

**Files**
- `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt` (~line 802)
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` (already has `getByIds(ids: List<String>)`)

**Change**
The flush loop calls `myLibraryDao.getById(resourceId)` once per pending
resource. Pre-collect `resourceId`s, call the existing
`myLibraryDao.getByIds(ids)` **once**, build a `Map<String, MyLibrary>`, then
look up `existing` from the map inside `mapNotNull`.

**Why it matters** During course sync each batch flush triggers N single-row
queries; this collapses to one `IN (...)` query. Pure data-layer win, no UI/API
change. The DAO method already exists, so no new code is added.

**Scope** 1 file, ~8 lines. Existing test `CoursesRepositoryImplTest` covers the
flush path — extend it with a multi-item batch assertion.

---

## Task 4 — Switch `TransactionSyncManager` checkpoint writes from `commit()` to `apply()`

**Files**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt` (lines ~198, ~313, ~328)

**Change**
Each sync batch currently does
`sharedPrefManager.rawPreferences.edit().putInt(checkpointKey, skip).commit()`,
which is a **synchronous disk write on the sync coroutine**. Since the next
batch's checkpoint is only consulted on resume-after-crash (not read back in the
same loop), `apply()` (async, in-memory then persisted) is correct and removes
blocking I/O from the hot sync loop. Keep the final `remove(checkpointKey)`
after full completion as `commit()` (or `apply()` + a comment) so a clean finish
is durable.

**Why it matters** `commit()` blocks the sync thread per page; for large tables
(ratings/submissions) this is a real throughput tax. Micro-optimization that
unblocks the larger "consolidate sync workflow" refactor later.

**Scope** 1 file, ~3 line edits + comment. Update `TransactionSyncManagerTest`
checkpoint assertions to reflect async semantics if any.

---

## Task 5 — Cancel the unbounded `isNetworkConnectedFlow` collector in `SyncActivity`

**Files**
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` (~line 645)

**Change**
`loginSuccessfully()` does
`isNetworkConnectedFlow.onEach { ... }.launchIn(MainApplication.applicationScope)`
— a never-cancelled collector that captures the Activity (`startUpload`,
`prefData`, `transactionSyncManager`). Hold the returned `Job` in a field and
`cancel()` it in `onDestroy()` (or use a `lifecycleScope`-scoped launch instead
of `applicationScope`).

**Why it matters** Long-running observer leak: each login re-entry adds a new
collector; the Activity is retained after destroy. Classic "long-running
observer/listener" cleanup with no behavior change.

**Scope** 1 file, ~6 lines. Add/extend `SyncActivity` test for collector
cancellation if feasible (Robolectric).

---

## Task 6 — Collapse `VoicesAdapter` full-list rescan on user fetch into a targeted index lookup

**Files**
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt` (~line 424)

**Change**
On `getUserFn` completion, the adapter does
`currentList.forEachIndexed { index, item -> if (item.userId == userId) safeNotifyItemChanged(...) }`
— a full O(n) scan per fetched user. Since items with the same `userId` are
often contiguous, build (or update incrementally) a `Map<String, IntArray>` of
`userId -> positions` when `submitList`/`onCurrentListChanged` fires, then notify
only those indices.

**Why it matters** Voices lists can be long; on first render every distinct user
triggers a full rescan. Targeted notify keeps payload updates (already using
`PAYLOAD_USER_FETCHED`) efficient and avoids redundant rebinds.

**Scope** 1 file, ~20 lines. Keep the existing `DiffUtils.itemCallback<News>`
unchanged. Unit-test the position map update with a small fake list.

---

## Task 7 — Move `CourseFilterController` and `InlineResourceAdapter` off hand-rolled `CoroutineScope`

**Files**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseFilterController.kt` (`CoroutineScope(SupervisorJob() + dispatcherProvider.main)`)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt` (`adapterScope`)

**Change**
Both create their own `CoroutineScope` with `DefaultDispatcherProvider()` /
injected dispatcher. `CourseFilterController` only needs the 300 ms search
debounce — accept a `CoroutineScope` from the owning `CoursesFragment`
(`viewLifecycleOwner.lifecycleScope`) instead of owning one, and drop its
`coroutineScope.cancel()` in `clear()` (the lifecycle cancels it). For
`InlineResourceAdapter`, keep the scope but ensure `onDetachedFromRecyclerView`
cancels it (verify it already does; if not, add).

**Why it matters** Removes a class of leak/scoping bugs and aligns with the
project's `DispatcherProvider` + lifecycle discipline (CLAUDE.md: "avoid
hard-coding `Dispatchers.*`"; prefer lifecycle-owned scopes). Sets up the later
"expand ViewModel/use layers" refactor.

**Scope** 2 files, ~15 lines. Keep `DefaultDispatcherProvider` removable if the
scope is now injected. Existing `CourseFilterController` debounce tests stay
green with a `TestScope`.

---

## Task 8 — Add `setHasFixedSize(true)` to the main list `RecyclerView`s

**Files**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt` (and other `BaseRecyclerFragment` subclasses where the adapter count drives layout only)

**Change**
Where the `RecyclerView`'s size does not depend on its content (the common case
here — fixed-height items in a `LinearLayoutManager`/`GridLayoutManager`), call
`recyclerView.setHasFixedSize(true)` after `findViewById(R.id.recycler)`. This
skips unnecessary layout passes when the adapter content changes.

**Why it matters** Cheap, well-understood RecyclerView optimization; reduces
layout thrash during `submitList`/diff updates. No behavior change.

**Scope** ~5 files, 1 line each. Optionally centralize in
`BaseRecyclerFragment.onCreateView`.

---

## Task 9 — Make `DatabaseService` actually unused and remove it (DI cleanup)

**Files**
- `app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/DatabaseModule.kt`

**Change**
Per CLAUDE.md, `DatabaseService` is "essentially vestigial — no repository
injects it anymore." A grep confirms only `DatabaseService.kt` and
`DatabaseModule.kt` reference it. Delete the class and the
`provideDatabaseService` `@Provides` (keep `AppDatabase` provision in
`RoomModule`). If any test references it, point them at `AppDatabase` directly.

**Why it matters** DI cleanup quick win: removes a dead `@Singleton` binding and
a wrapper that obscures the real data source. Directly serves "Finish Cleaning
the Data Layer" and "Complete Dependency Injection Cleanup" without a rewrite.

**Scope** 2 files deleted/trimmed, ~20 lines removed. Verify
`./gradlew testDefaultDebugUnitTest` and a DI graph compile (Hilt) pass.

---

## Task 10 — Centralize diacritics normalization to remove duplicated `Normalizer` + `Regex` code

**Files**
- `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt` (`normalizeText`)
- `app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt` (inline `Normalizer` + diacritics regex in `insertMyLibrary`)
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt` (`DIACRITICS_REGEX`)
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`DIACRITICS_REGEX`)

**Change**
Three places independently do
`Normalizer.normalize(s, NFD).replace(DIACRITICS_REGEX, "")` (with varying
`lowercase` locale handling). Extract one `Utilities.normalizeDiacritics(s:
String): String` (using the hoisted `Regex` from Task 1) and call it from all
three. Drop the duplicate `DIACRITICS_REGEX` constants.

**Why it matters** Removes obvious duplication, guarantees one compiled regex,
and clarifies the data-layer normalization contract — a small, safe consolidation
that the later data-layer cleanup depends on.

**Scope** ~4 files, net line count drops. Depends on Task 1's hoisted regex (do
Task 1 first, or fold this regex into this task). Add a tiny
`UtilitiesTest` for the normalized output.

---

## Sequencing & conflict-avoidance notes

- Tasks are ordered so later ones can build on earlier ones, but each is
  independently mergeable.
- **Do Task 1 before Task 10** (Task 10 reuses the hoisted diacritics regex);
  otherwise tasks touch disjoint files.
- No two tasks edit the same lines: adapter tasks (2, 6, 8) are in different
  adapters; data/DI tasks (3, 4, 9) are in different files; observer/scope tasks
  (5, 7) are in different files; utility tasks (1, 10) share only the regex
  constant (ordered).
- At ~9.99 PRs/round, this fits within one review round; if trimming, drop 8 and
  10 first (lowest impact), keep 3, 4, 5 (highest impact / leak fixes).

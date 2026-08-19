# Refactor Round — Performance Quick Wins (10 PRs, one review round)

**Round budget:** ~10 PRs (≈9.99/day review capacity)
**Theme:** performance quick wins, micro-optimizations that unblock later refactors, removing obvious inefficiencies — no rewrites.
**Roadmap coverage:** (1) data layer, (3) ViewModel layer, (4) DI/threading, (5) sync/upload, (7) performance hotspots, (8) code health.

## Conflict-avoidance rules for this round

- **One task = one PR = one disjoint file set.** No two tasks below touch the same file, so the round can merge in any order.
- Only **T1** carries real conflict risk: `TeamsRepositoryImpl.kt` is already contested by open PRs **#15656** (split Teams interface) and **#15662** (remove UploadManager dep). Land T1 **first** (it is a 2-function diff) or hold it to the next round.
- Files deliberately **avoided** because open PRs own them: `BaseRecyclerFragment.kt` (#15650), `TeamFragment.kt` / `MyHealthFragment.kt` (#15644), `SyncActivity.kt` (#15603), `UrlUtils.kt` (#15614), `services/sync/*` (#15808, #15720).
- Every task keeps the existing `DiffUtils.itemCallback` / `DispatcherProvider` / DAO conventions. **No new unused code** — every added DAO query or field has exactly one caller in the same PR.

## Suggested merge order

`T2 → T3 → T4 → T5 → T6 → T7 → T9 → T10 → T8 → T1`
(cheapest and most isolated first; T8 is the largest; T1 last unless #15656/#15662 slip.)

---

## T1 — Push the teams-table Flow filters into SQL

**Roadmap:** 1 (data layer) · 7 (performance) — **biggest single win in the round**

**Problem**
`TeamsRepositoryImpl.queryTransactions()` (~line 449) and `getReportsFlow()` (~line 787) both start from `teamDao.observeAll()` = `SELECT * FROM teams`, then filter in Kotlin by `teamId` + `docType`. The `teams` table mixes *root teams, memberships, requests, transactions, reports and resourceLinks* in one table, so:
- every write to **any** of those doc types re-emits the **entire** table,
- the whole table is re-copied and re-filtered **on the collector's dispatcher** (neither flow has `flowOn`),
- neither flow has `distinctUntilChanged()`, so a sync page that changes nothing relevant still re-diffs the finances/reports lists.

**Change**
1. `data/room/dao/TeamDao.kt` — add one query next to the existing `getByTeamIdAndDocType`:
   `@Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = :docType") fun observeByTeamIdAndDocType(teamId: String, docType: String): Flow<List<MyTeam>>`
2. `repository/TeamsRepositoryImpl.kt` — `queryTransactions()` uses it with `"transaction"`, `getReportsFlow()` with `"report"`. Keep the remaining in-Kotlin predicates (`status != "archived"`, date range, sort). Add `.flowOn(dispatcherProvider.default).distinctUntilChanged()` to both, matching `getMyTeamDetailsFlow` (line 297) which already does exactly this.

**Do not** touch `getMyTeamsFlow` / `getMyTeamDetailsFlow` in this PR — they need cross-docType joins and belong to a later round.

**Files:** `data/room/dao/TeamDao.kt`, `repository/TeamsRepositoryImpl.kt` (2 function bodies)
**Size:** ~15 lines · **Conflict risk:** HIGH (see rules above)
**Verify:** `TeamsRepositoryImplTest`, `TeamsRepositoryBenchmarkTest`; manually open Enterprises → Finances and Reports and confirm rows, ordering and running balance are unchanged.
**Commit:** `perf(teams): filter transaction and report flows in sql`

---

## T2 — One transaction for `markNotificationsSynced`

**Roadmap:** 1 (data layer) · 5 (sync/upload)

**Problem**
`repository/NotificationsRepositoryImpl.kt:324-328`:
```
syncResults.forEach { (id, rev) -> notificationDao.markSynced(id, rev) }
```
Each `markSynced` is its own Room transaction, so a sync that confirms N notifications performs N commits (and N fsyncs). `withTransaction` is used in exactly **one** place in the whole app today (`TeamsRepositoryImpl:1313`) — and the comment there documents this same problem for teams.

**Change**
Inject `AppDatabase` into `NotificationsRepositoryImpl` and wrap the loop in `appDatabase.withTransaction { ... }` → one commit per sync batch. (Preferred over a new batch `@Query`: `markSynced` writes a per-row `rev`, so a single statement would need a CASE expression — not worth it.)

**Files:** `repository/NotificationsRepositoryImpl.kt`
**Size:** ~6 lines · **Conflict risk:** none
**Verify:** `NotificationsRepositoryImplTest` (mirror the existing `TeamsRepositoryBulkInsertTransactionTest` assertion style); trigger a sync with pending notifications.
**Commit:** `perf(notifications): mark synced notifications in one transaction`

---

## T3 — Stop rebuilding the courses LayoutManager on every toggle

**Roadmap:** 7 (performance) · UI micro-opt

**Problem**
`ui/courses/CoursesFragment.kt`:
- `updateToggleUi()` (line ~386) ends with `applyRecyclerLayoutManager(mode)`, and `applyRecyclerLayoutManager()` (line ~364) **always** constructs a brand-new `GridLayoutManager`/`LinearLayoutManager`. Assigning a new LayoutManager discards the recycled view pool, drops scroll position and forces a full re-layout — on every view-mode toggle and every setup pass.
- `updateToggleUi()` also runs two `findViewById` lookups per call (lines ~390-391) for buttons that never change.

**Change**
- Cache the two `ImageButton`s in fields resolved once in `onViewCreated`.
- In `applyRecyclerLayoutManager`, reuse the current LayoutManager when the type already matches — for GRID just assign `spanCount` (the existing `updateGridSpanIfNeeded()` already proves this path works); only construct a new one when the type actually changes.

**Files:** `ui/courses/CoursesFragment.kt`
**Size:** ~20 lines · **Conflict risk:** none (does not touch `BaseRecyclerFragment` — #15650's file)
**Verify:** toggle grid/list, rotate the device, confirm scroll position survives and span count still adapts to width.
**Commit:** `perf(courses): reuse recycler layout manager on view mode toggle`

---

## T4 — Same fix for resources, plus kill the `findViewById` getters

**Roadmap:** 7 (performance) · UI micro-opt

**Problem**
`ui/resources/ResourcesFragment.kt` has the identical `applyRecyclerLayoutManager` / `updateToggleUi` pair (lines ~273-304), **and** three property getters that re-run `findViewById` on *every access*:
```
private val layoutViewToggle   get() = binding.root.findViewById<View>(R.id.layout_view_toggle) ?: ...
private val toggleGridButton   get() = binding.root.findViewById<ImageButton>(R.id.toggle_grid)
private val toggleListButton   get() = binding.root.findViewById<ImageButton>(R.id.toggle_list)
```
`updateToggleUi` reads those getters 4× → 4 view-tree walks per call.

**Change**
Resolve the three views once (nullable fields set in `onViewCreated`, cleared in `onDestroyView`), and apply the same reuse-the-LayoutManager change as T3.

**Files:** `ui/resources/ResourcesFragment.kt`
**Size:** ~25 lines · **Conflict risk:** none
**Verify:** library grid/list toggle, rotation, and the "my library" vs "all resources" variants.
**Commit:** `perf(resources): cache toggle views and reuse layout manager`

---

## T5 — `distinctUntilChanged()` on the Room-backed repository flows that lack it

**Roadmap:** 1 (data layer) · 7 (performance)

**Problem**
Room's invalidation tracker is **per table**, not per query: any write to `personal`, `feedback` or `my_library` re-emits every Flow observing that table, even when the query result is byte-identical. During a sync that means hundreds of redundant emissions → redundant `submitList` + DiffUtil passes. Four flows currently have no guard:
- `repository/PersonalsRepositoryImpl.kt:53` — `personalDao.getByUserIdFlow(userId)`
- `repository/FeedbackRepositoryImpl.kt:74,76` — `getAllSortedFlow()` / `getByOwnerFlow(...)`
- `repository/ResourcesRepositoryImpl.kt:326,330` — `getRecentForUserPatternFlow(...)` / `getPendingDownloadsForUserPatternFlow(...)` (a "recent 5" list re-emitting on every library write is the worst offender)

`CoursesRepositoryImpl:106`, `SubmissionsRepositoryImpl:87` and `VoicesRepositoryImpl:158,190` already do this — copy their pattern.

**Change**
Append `.distinctUntilChanged()` to each of the five flows. Add `.flowOn(dispatcherProvider.default)` only where a `map`/transform follows.

**Files:** `repository/PersonalsRepositoryImpl.kt`, `repository/FeedbackRepositoryImpl.kt`, `repository/ResourcesRepositoryImpl.kt`
**Size:** 5 one-line additions · **Conflict risk:** none
**Verify:** `PersonalsRepositoryImplTest`, `FeedbackRepositoryImplTest`, `ResourcesRepositoryImplTest`.
**Commit:** `perf(data): drop duplicate room flow emissions`

---

## T6 — One pass over the news list instead of four

**Roadmap:** 7 (performance)

**Problem**
`ui/voices/VoicesFragment.kt` `setData()` (lines ~171-188) runs, on the **main thread**, for every emission of `filteredNews`:
1. `sortNews(list)` — which itself makes a redundant `toMutableList()` copy before calling `sortedWith` (already wrapped in `Trace.beginSection`, i.e. someone suspected this),
2. `sortedList.filterNotNull()` **three separate times** (adapter-null branch, `submitList` branch, `showNoData`) — three full list copies per emission.

**Change**
Compute `val sorted: List<News> = list.filterNotNull().sortedByDescending { it.sortDate ?: 0L }` **once** at the top of `setData` and reuse it for all three consumers. Delete the `toMutableList()` copy in `sortNews` (or fold `sortNews` into the single expression). Keep the `Trace` section so the improvement is measurable in a systrace.

**Files:** `ui/voices/VoicesFragment.kt`
**Size:** ~15 lines removed/reshaped · **Conflict risk:** none
**Verify:** voices list ordering unchanged; post a new voice and confirm the scroll-to-top callback still fires; empty state still correct with a search filter and a label filter active.
**Commit:** `perf(voices): sort and filter the news list once per update`

---

## T7 — Bound Glide decode size for list thumbnails

**Roadmap:** 7 (performance) — memory + decode time

**Problem**
Only three Glide call sites in the app pass `.override(...)`: `UserProfileFragment`, `VoicesAdapter` and `ImageUtils`. The list adapters decode **full-resolution** files straight into small row/grid ImageViews:
- `ui/resources/ResourcesAdapter.kt:305, 321`
- `ui/courses/CoursesAdapter.kt:293`
- `ui/courses/InlineResourceAdapter.kt:205, 218`

These are exactly the screens that scroll, so every fling decodes and caches oversized bitmaps.

**Change**
Add `.override(targetPx, targetPx)` (target read from the row's image dimen, following `UserProfileFragment:223` / `ImageUtils:17`) to those five loads. `diskCacheStrategy(ALL)` and the `Glide.with(context).clear(...)` recycling calls are already correct — leave them alone.

**Files:** `ui/resources/ResourcesAdapter.kt`, `ui/courses/CoursesAdapter.kt`, `ui/courses/InlineResourceAdapter.kt`
**Size:** 5 one-line additions · **Conflict risk:** none
**Verify:** thumbnails still fill their views (no visible downscale artifacts) in list *and* grid mode; check the Android Studio memory profiler before/after a long fling.
**Commit:** `perf(resources): bound thumbnail decode size`

---

## T8 — Remove both `notifyDataSetChanged()` calls from the survey list

**Roadmap:** 3 (ViewModel layer) · 7 (performance) — **the last two `notifyDataSetChanged()` in the codebase**

**Problem**
`SurveysAdapter` is a proper `ListAdapter` with a `DiffUtils.itemCallback`, but it reads **two mutable maps owned by the fragment** (`surveyInfoMap`, `bindingDataMap`, `SurveysAdapter.kt:25-26`). Those maps are invisible to the diff callback, so `SurveyFragment.kt:180` and `:185` fall back to `getAdapter().notifyDataSetChanged()` — rebinding **every** visible row (and killing item animations) whenever survey info or form state arrives. This is also the only place in `app/src/main` still calling `notifyDataSetChanged`.

**Change**
1. `ui/surveys/SurveysViewModel.kt` — `combine(surveys, surveyInfos, bindingData)` into a single `StateFlow<List<SurveyRow>>` where `SurveyRow(exam, info, formState)` is a small data class.
2. `ui/surveys/SurveysAdapter.kt` — become `ListAdapter<SurveyRow, …>`; drop the two constructor map params; extend the existing `DiffUtils.itemCallback` `areContentsTheSame` to compare the info/form fields the row actually renders.
3. `ui/surveys/SurveyFragment.kt` — delete `surveyInfoMap` / `bindingDataMap` and the two collectors that only existed to poke the adapter; keep one `submitList` collector.

**Files:** `ui/surveys/SurveyFragment.kt`, `ui/surveys/SurveysAdapter.kt`, `ui/surveys/SurveysViewModel.kt` (+ a `SurveyRow` data class)
**Size:** largest task in the round (~80 lines, one package). **Drop this one first if the round needs to shrink.**
**Conflict risk:** low, but glance at #14650 (survey submissions display) before starting.
**Verify:** survey list with team + adopt filters, all four sort options, adopt flow, and the "already submitted" state; row content must be identical to today.
**Commit:** `perf(surveys): diff survey rows instead of rebinding the whole list`

---

## T9 — `getOfflineLogins` shouldn't be `suspend`, and the chart shouldn't aggregate on the main thread

**Roadmap:** 3 (ViewModel layer) · 4 (threading) — **unblocks a later `stateIn` refactor**

**Problem**
There are 21 `suspend fun …: Flow<…>` signatures in the repo layer — a smell, since building a Flow never suspends. Their only effect is forcing every caller into an extra `lifecycleScope.launch` just to *obtain* the flow, which is why `ui/dashboard/ActivitiesFragment.kt:48-56` reads:
```
viewLifecycleOwner.lifecycleScope.launch {
    val loginsFlow = activitiesRepository.getOfflineLogins(userName)
    collectLatestWhenStarted(loginsFlow) { ... }   // launch inside a launch
}
```
and the collector then runs `computeMonthlyCounts()` (a full scan + `Calendar` per login) plus `renderChart()` on the main thread. This task is the smallest, fully-isolated instance of the pattern (one interface, one impl, one caller, one existing test).

**Change**
1. `repository/ActivitiesRepository.kt` + `ActivitiesRepositoryImpl.kt` — drop `suspend` from `getOfflineLogins`; add `.distinctUntilChanged()`.
2. `ui/dashboard/ActivitiesFragment.kt` — remove the nested `launch`; get the username in one launch, then a single `collectLatestWhenStarted`. Move `computeMonthlyCounts` off the main thread (`withContext(dispatcherProvider.default)`) and keep only `renderChart` on it — `computeMonthlyCounts` is already `internal` and unit-tested, so this is safe.

Leave the other 20 `suspend`-Flow signatures alone; they are a follow-up round (and several sit in T1's file).

**Files:** `repository/ActivitiesRepository.kt`, `repository/ActivitiesRepositoryImpl.kt`, `ui/dashboard/ActivitiesFragment.kt`
**Size:** ~20 lines · **Conflict risk:** none
**Verify:** `ActivitiesRepositoryImplTest:99` (`getOfflineLogins returns flow of activities`) needs its `runTest`/`collect` shape adjusted; dashboard login-history chart renders identically.
**Commit:** `refactor(activities): expose offline logins as a plain flow`

---

## T10 — Move the course-progress JSON mapping into its ViewModel

**Roadmap:** 3 (ViewModel layer) · 7 (performance)

**Problem**
`ProgressViewModel` exposes a raw `StateFlow<JsonArray?>` (`ProgressViewModel.kt:20-21`), so `ui/courses/CoursesProgressFragment.kt:42-66` does all the parsing **inside the collector, on the main thread**: it allocates a fresh reflective `object : TypeToken<Map<String, Int>>() {}.type` on **every emission**, then walks the whole `JsonArray` building `CoursesProgressRow`s before `submitList`.

`ProgressViewModel` has exactly **one** consumer (`CourseProgressActivity` uses the separate `CourseProgressViewModel`), so this is a contained change.

**Change**
1. `ui/courses/ProgressViewModel.kt` — hoist the `TypeToken` to a `private val` created once; do the `JsonArray → List<CoursesProgressRow>` mapping in `loadCourseData()` on `dispatcherProvider.default`; expose `StateFlow<List<CoursesProgressRow>>`.
2. `ui/courses/CoursesProgressFragment.kt` — the collector shrinks to `progressAdapter.submitList(rows)`; the injected `Gson` moves to the ViewModel.

**Files:** `ui/courses/ProgressViewModel.kt`, `ui/courses/CoursesProgressFragment.kt`
**Size:** ~35 lines moved, net roughly neutral · **Conflict risk:** none
**Verify:** my-progress list — course names, current/max progress, mistake counts and per-step mistakes must match today exactly (add a `ProgressViewModel` mapping test; there is no existing one).
**Commit:** `refactor(courses): map progress rows in the view model`

---

## Bench (substitutes if a task gets cut)

Each of these is a real, confirmed inefficiency — held back only because it would collide with a task above or with an open PR.

- **`TeamsRepositoryImpl.markMembershipsForLeave` (~line 1368)** — per-row `teamDao.deleteById(...)` in a loop while `deleteByIds(ids)` already exists in `TeamDao`. Fold into T1's PR, or defer a round (same file).
- **`TeamsRepositoryImpl.batchInsertMyTeams` (line 1243) and `bulkInsertMyTeamsFromSync` (line 1305)** — both do `teamDao.getAll().filter { (it._id ?: it.id) in ids }`, loading the whole teams table per sync page. Needs one new DAO query (`WHERE _id IN (:ids) OR id IN (:ids)`); same file as T1.
- **`TagsRepositoryImpl.getTagsWithChildren` (line 19)** — loads the entire `tags` table to build a child map when only children of the requested parents are needed.
- **The remaining 20 `suspend fun …: Flow<…>` signatures** (Voices, Courses, Feedback, Personals, Resources, Teams) — mechanical, but they overlap T1/T5/T9's files. Good standalone round-2 PR once this round merges.
- **`NotificationsRepositoryImpl.getTeamNotifications` (~line 296)** — `getTeamChatViewableIds` returns every matching row just to count occurrences in Kotlin; `NewsDao` could return `viewableId, COUNT(*) … GROUP BY viewableId`. Same file as T2.

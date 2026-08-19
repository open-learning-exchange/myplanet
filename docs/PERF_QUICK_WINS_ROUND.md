# Performance Quick Wins — 10 Reviewable Tasks

Round constraints: ~10 PRs/day, low merge-conflict risk, no big rewrites.
Focus: DiffUtil/payloads, ViewModel/collect lifecycle, search flow noise, data-layer filter waste, micro list opts.
Each task is intentionally 1–3 files in a different package so they can land in parallel.

Shared helper (already exists — use it, do not reinvent):
- `app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt` → `DiffUtils.itemCallback` / `standardItemCallback` / `calculateDiff`
- Lifecycle collect helper: `collectWhenStarted` / `collectLatestWhenStarted`

---

## Task 1 — Surveys: replace `notifyDataSetChanged` with DiffUtil payloads

**Priority:** High (only remaining `notifyDataSetChanged` in main source)
**Roadmap map:** #7 perf + #3 ViewModel/list layer
**Conflict risk:** Very low (surveys package only)

**Spots**
- `ui/surveys/SurveyFragment.kt` — `setupObservers()` calls `getAdapter().notifyDataSetChanged()` when `surveyInfos` or `bindingData` maps update
- `ui/surveys/SurveysAdapter.kt` — already `ListAdapter` + `DiffUtils.itemCallback`, but side maps (`surveyInfoMap`, `bindingDataMap`) are read in `bind()` and are **not** part of item equality, so DiffUtil never rebinds those cells

**Do**
- Fold survey info / form state into the list item model (small UI row data class) **or** add a narrow payload path (`notifyItemRangeChanged(0, itemCount, PAYLOAD_SURVEY_META)`) and handle it in `onBindViewHolder(..., payloads)`
- Prefer payload-only rebind of title-adjacent fields (`tvNoSubmissions`, dates, start button label) — do not full-rebind the row
- Keep using `DiffUtils.itemCallback`; extend `areContentsTheSame` / `getChangePayload` if side data moves onto the item

**Out of scope:** survey load/sort logic, schema, Compose

---

## Task 2 — Search text flows: add `distinctUntilChanged` (noise cut)

**Priority:** High / tiny
**Roadmap map:** #7 perf + observers
**Conflict risk:** Low (one-line edits in 4 UI files; land as **one** PR)

**Spots** (debounce present, distinct missing — Resources already does both)
- `ui/surveys/SurveyFragment.kt` — `textChanges().debounce(300)`
- `ui/teams/TeamFragment.kt` — `setupTextWatcher()`
- `ui/submissions/SubmissionsFragment.kt` — search `textChanges()`
- `ui/chat/ChatHistoryFragment.kt` — `searchBar.textChanges()`

**Do**
- After `debounce`, add `.distinctUntilChanged()` (and `drop(1)` only where initial empty emission would re-filter, matching Submissions if needed)
- Mirror `ResourcesFragment.setupSearchTextListener()` / `CollectionsFragment` pattern

**Out of scope:** moving search into ViewModels, new debounce helpers

---

## Task 3 — Courses/Resources adapters: payload-scoped range invalidation

**Priority:** High (large lists, full rebind today)
**Roadmap map:** #7 DiffUtil / listadapter
**Conflict risk:** Low–medium (2 adapter files only)

**Spots**
- `ui/courses/CoursesAdapter.kt` — `setViewMode()` and `updateIdentity()` call `notifyItemRangeChanged(0, itemCount)` with **no payload** (full rebind + Glide reload)
- `ui/resources/ResourcesAdapter.kt` — same for `setViewMode()` / `updateIdentity()`
- Both already define payloads (`PAYLOAD_PROGRESS`, `PAYLOAD_SELECTION`) and `onViewRecycled` Glide clears

**Do**
- Introduce small payloads e.g. `PAYLOAD_VIEW_MODE`, `PAYLOAD_IDENTITY` (or reuse selection where correct)
- Call `notifyItemRangeChanged(0, itemCount, payload)`
- In partial bind path, update only chrome that depends on mode/guest (checkbox visibility, layout bits) — **do not** reload covers via Glide on identity-only updates

**Out of scope:** grid/list layout XML changes, filter logic

---

## Task 4 — Stable IDs on the hottest `ListAdapter`s

**Priority:** Medium-high (unblocks smoother DiffUtil + animation; tiny diffs)
**Roadmap map:** #7 DiffUtil / listadapter
**Conflict risk:** Very low (per-adapter init + `getItemId`; split if needed)

**Spots** (none of the main `ListAdapter`s set stable IDs today; pagers already do)
- Round A (recommended single PR): `CoursesAdapter`, `ResourcesAdapter`, `VoicesAdapter`, `SurveysAdapter`, `TeamsAdapter`
- IDs: stable `Long` from existing string keys (`courseId`, resource `id`, news `id`, exam `id`, team id) via a shared hash helper **or** local `hashCode().toLong()` consistently

**Do**
- `init { setHasStableIds(true) }` + `override fun getItemId(position: Int): Long`
- Ensure DiffUtil `areItemsTheSame` keys match the stable id source
- No behavior change beyond RecyclerView identity tracking

**Out of scope:** every remaining adapter (defer quieter lists to a later round)

---

## Task 5 — Courses shelf query: stop `getAll()` + in-memory filter

**Priority:** High (dashboard + my-courses path)
**Roadmap map:** #1 data layer + #7 perf
**Conflict risk:** Low (DAO + repository + existing tests)

**Spots**
- `repository/CoursesRepositoryImpl.kt`
  - `getMyCourses(userId)` → `mapCourses(courseDao.getAll())` then `userId.contains`
  - `getMyCoursesFlow(userId)` → `courseDao.observeAll().map { filter }`
- `data/room/dao/CourseDao.kt` — only `getAll` / `observeAll` today
- Pattern to copy: `MyLibraryDao` + `ResourcesRepositoryImpl.userIdPattern()` (`LIKE %"id"%` on JSON list column)

**Do**
- Add `getForUserPattern` / `observeForUserPattern` DAO queries (same LIKE/ESCAPE convention as library)
- Point `getMyCourses` / `getMyCoursesFlow` at the filtered queries
- Keep `distinctUntilChanged` on the flow
- Extend `CoursesRepositoryImplTest` only

**Out of scope:** rewriting `mapCourses`, UI, schema bump (index is Task 6)

---

## Task 6 — Index `userId` JSON shelf columns (courses + my_library)

**Priority:** Medium (amplifies Task 5; pure data-layer)
**Roadmap map:** #1 data layer + #7 perf
**Conflict risk:** Low (2 model files + `AppDatabase` version) — **coordinate merge order after Task 5**

**Spots**
- `model/MyCourse.kt` — indices today: `courseId`, `_id`, `courseTitleNormal`, `gradeLevel`, `subjectLevel` (no `userId`)
- `model/MyLibrary.kt` — indices: `_rev`, `titleNormal`, `resourceId` (no `userId`) despite heavy `userId LIKE` shelf queries in `MyLibraryDao`
- `data/room/AppDatabase.kt` — bump `version` (drop-and-resync strategy already in `RoomModule`)

**Do**
- Add `@Index("userId")` (or note LIMITATIONS of LIKE vs index — still helps prefix/other paths and documents intent) **or** if LIKE cannot use the index usefully, add a generated/normalized membership side table only if still “small”; prefer simple index first
- Bump DB version once
- Do **not** hand-write migrations

**Caution:** destructive migration discards unsynced local writes — treat as a release-aware bump; keep PR description explicit

**Out of scope:** new membership tables, query rewrites beyond Task 5

---

## Task 7 — Lifecycle-bound collectors in Health examination + nested relaunch

**Priority:** Medium-high (long-lived observer / wasted work)
**Roadmap map:** #3 ViewModels + observers/listeners
**Conflict risk:** Very low (single activity file)

**Spots**
- `ui/health/HealthExaminationActivity.kt`
  - `lifecycleScope.launch { viewModel.state.collect { ... } }` — **not** `collectWhenStarted`; keeps collecting when stopped
  - Inside each emission, another `lifecycleScope.launch { getExaminationConditions; initExamination() }` with no cancellation of the previous job → stacked work on rapid state updates
- Same file already uses `collectWhenStarted` for `isSaving` / `saveResult` — inconsistent

**Do**
- Switch `state` collection to `collectWhenStarted` (or `repeatOnLifecycle(STARTED)`)
- Hold a single `loadUiJob` and cancel/replace on new state
- Optionally `distinctUntilChanged` on relevant state fields before UI work

**Out of scope:** moving more logic into `HealthExaminationViewModel`, layout changes

---

## Task 8 — SurveyFragment: stop full reload on every `onResume`

**Priority:** Medium (redundant DB/network-ish work)
**Roadmap map:** #3 ViewModels + observers
**Conflict risk:** Very low (surveys only; land **after or separate from** Task 1)

**Spots**
- `ui/surveys/SurveyFragment.kt`
  - `onViewCreated` → `viewModel.loadSurveys(...)`
  - `onResume` → `viewModel.loadSurveys(...)` again unconditionally
- Realtime path already wired via `RealtimeSyncHelper` / `RealtimeSyncMixin`

**Do**
- Drop blind `onResume` reload **or** gate on a cheap dirty flag / last-sync generation from ViewModel
- Prefer trusting existing Flow/realtime invalidation; if resume refresh is required for edge cases, call a lightweight `refreshIfStale()` in the ViewModel

**Out of scope:** redesign of `SurveysViewModel` load pipeline

---

## Task 9 — Dashboard: one-shot library load → reactive Flow (match courses/teams)

**Priority:** Medium (dashboard freshness + less manual refresh)
**Roadmap map:** #3 ViewModels + #1 data layer (thin)
**Conflict risk:** Low (ViewModel + repository API if Flow already exists)

**Spots**
- `ui/dashboard/DashboardViewModel.kt` → `loadUserContent()`
  - `coursesRepository.getMyCoursesFlow` + `teamsRepository.getMyTeamsFlow` collected continuously
  - `resourcesRepository.getMyLibrary(userId)` is a **one-shot** suspend assign to `_uiState.library`
- `repository/ResourcesRepository` / `ResourcesRepositoryImpl` — confirm/add `getMyLibraryFlow` using existing DAO `userId LIKE` pattern + `flowOn(dispatcherProvider.io)`

**Do**
- Add/use `getMyLibraryFlow(userId)` 
- Collect like courses/teams with cancelable `libraryJob`
- `distinctUntilChanged` on list identity/size/revs before `_uiState.update`

**Out of scope:** full dashboard UI rewrite, Compose

---

## Task 10 — Voices ViewModels: `collectLatest` + cheaper label/filter passes

**Priority:** Medium (busy community feed path)
**Roadmap map:** #3 ViewModels + long-running observers
**Conflict risk:** Very low (2 ViewModel files)

**Spots**
- `ui/voices/VoicesViewModel.kt` — `observeCommunityNews` uses plain `.collect`; every emission rebuilds labels via `collectLabels` then downstream `combine` filters again on `dispatcherProvider.default`
- `ui/teams/voices/TeamsVoicesViewModel.kt` — `getDiscussionsByTeamIdFlow(teamId).collect { ... }` with manual `observeJob`; no `collectLatest` / `distinctUntilChanged` on discussion lists
- Fragments already lifecycle-wrap UI collection (`collectWhenStarted` / `repeatOnLifecycle`); the waste is in the VM still doing full work for duplicate emissions

**Do**
- Switch inner repository collects to `collectLatest`
- Add `distinctUntilChanged` on list identity (ids + `_rev` / updated fields) before writing `_baseNewsList` / discussions state
- Keep existing `flowOn(dispatcherProvider.default)` on filter `combine`; avoid double-mapping `as News?` if types already match
- Cancel pattern already present (`observeJob?.cancel()`) — keep it; optional `onCleared` cancel is fine if not redundant

**Out of scope:** VoicesAdapter Gson pre-parse redesign, Compose, repository SQL rewrites

---

# Suggested merge order (conflict-safe)

| Order | Task | Why first |
|------:|------|-----------|
| 1 | Task 2 — search `distinctUntilChanged` | Trivial, no deps |
| 2 | Task 7 — HealthExamination collect | Isolated activity |
| 3 | Task 10 — Voices VM collectLatest | Isolated VMs |
| 4 | Task 4 — stable IDs (hot adapters) | Pure adapter init |
| 5 | Task 1 — Surveys payloads | Surveys package |
| 6 | Task 8 — Survey onResume | After/near Task 1 |
| 7 | Task 3 — Courses/Resources payloads | Larger adapters |
| 8 | Task 5 — Courses DAO shelf filter | Data layer |
| 9 | Task 9 — Dashboard library Flow | Resources Flow API; independent of Task 5 |
| 10 | Task 6 — DB indices + version bump | Last; destructive migration note |

---

# Explicitly deferred (not this round)

- Global navigation architecture
- Jetpack Compose migration
- Splitting `TeamsRepositoryImpl` (~1.4k LOC)
- Full sync/upload consolidation
- `BaseRecyclerFragment` default `setHasFixedSize` / animator flags (base-class conflict magnet; do next round alone)
- Pager DiffUtil background threading (`CoursesPagerAdapter` / `TeamPagerAdapter` lists are tiny — not worth it yet)
- `TagsRepositoryImpl.getTagsWithChildren` full-table walk (real, but needs a designed DAO query)
- `CoursesRepositoryImpl` `Collections.synchronizedList` → mutex/`CopyOnWriteArrayList` (tiny; good filler PR if a slot opens)
- `UserRepositoryImpl` / `TeamsRepositoryImpl` post-hoc `distinctBy` → set-based dedup while building
- ExamTakingFragment / ChatHistoryAdapter Gson & dialog allocation cleanup (larger UI surface)
- Hard-coded `Dispatchers.*` — already essentially gone outside `DefaultDispatcherProvider`

---

# PR checklist (every task)

- Touch only the listed files (+ matching unit tests if repository/DAO)
- Use existing `DiffUtils.itemCallback` — no new DiffUtil copies
- No unused helpers, no drive-by renames
- Prefer payloads over full rebinds
- Prefer `collectWhenStarted` / `viewModelScope` over bare `lifecycleScope.collect`
- Inject/`DispatcherProvider` already in place — do not reintroduce raw `Dispatchers.IO`
- Keep PR reviewable in < ~15 minutes

# Performance Quick Wins — 10 Granular PR Tasks

**Round goal:** ~10 reviewable PRs/day, low merge-conflict risk, no big rewrites.  
**Focus:** list rebinds, DiffUtil/payloads, dispatchers/DI micro-cleanup, long-lived collectors/listeners, data-layer thrash that blocks bigger refactors later.

**Ordering tip for the round:** ship adapter-only PRs first (1–4), then base/fragment lifecycle (5–6), then VM/DI (7–10). Touch different packages per PR so parallel review/merge stays easy.

**House rules**
- One concern per PR; keep diffs small and reviewable
- Prefer existing helpers (`DiffUtils.itemCallback` / `standardItemCallback`, `DispatcherProvider`, `collectWhenStarted` / `collectLatestWhenStarted`)
- No unused code, no speculative abstractions
- Do not bump Room schema unless the PR is solely about a cheap index and you accept drop-and-resync cost
- Avoid editing mega-files (`TeamsRepositoryImpl`, `UserRepositoryImpl`, `SyncManager`) in this round

---

## Task 1 — Cache list adapters in Courses/Resources `getAdapter()` (stop recreate + reload thrash)

**Why:** `CoursesFragment.getAdapter()` always rebuilds `CoursesAdapter`, resubmits the list, and calls `viewModel.loadCourses(...)` again. `ResourcesFragment.getAdapter()` always rebuilds `ResourcesAdapter` and reloads library models. Base paths call `getAdapter()` from `onViewCreated`, `onRatingChanged`, and `postAddRefresh`, so every rating/add can wipe scroll state and re-query.

**Where**
- `ui/courses/CoursesFragment.kt` (`getAdapter`, also `loadCourses` at ~L92/L108/L139)
- `ui/resources/ResourcesFragment.kt` (`getAdapter`)
- Reference pattern already correct: `ui/surveys/SurveyFragment.kt` (null-check + mutex, create once)

**Do**
- Create adapter once; return cached instance on later calls
- Move one-shot load (`loadCourses` / `getLibraryListModels`) out of the recreate path (init / first create only)
- Keep listener wiring idempotent (`setListener` ok; do not re-factory)

**Avoid:** redesigning `BaseRecyclerFragment`; leave base to Task 5.

**Conflict surface:** courses + resources fragments only — low.

---

## Task 2 — Kill `notifyDataSetChanged()` on Courses/Resources grid↔list toggle (+ cheap ID lookups)

**Why:** Both list adapters are already `ListAdapter` + `DiffUtils`, but `setViewMode()` still full-invalidates every row. Resources also does full-list scans on open/offline flag updates.

**Where**
- `ui/courses/CoursesAdapter.kt` (`setViewMode` → `notifyDataSetChanged()`)
- `ui/resources/ResourcesAdapter.kt` (`setViewMode` → `notifyDataSetChanged()`; `setOpenedResourceIds`; `markItemAsOffline`)
- Call sites already isolated in `CoursesFragment` / `ResourcesFragment` toggle handlers

**Do**
- Prefer `notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE)` **or** a single `submitList(...)` only if view-type change requires rebind and payloads are insufficient
- If view types differ (GRID vs LIST), keep range change; ensure `getItemViewType` reads current mode
- Bind path: honor payload so unchanged visual bits are skipped when possible
- `setOpenedResourceIds`: notify only the symmetric-difference of old/new id sets (O(changed), not O(n))
- `markItemAsOffline`: `indexOfFirst` once (or a tiny id→index map rebuilt on list commit) instead of scanning every row

**Avoid:** layout/XML changes; fragment logic changes beyond what the adapter API needs.

**Conflict surface:** two adapter files — very low.

---

## Task 3 — Payload-only rebinds for Members + Enterprise Reports flag flips

**Why:** Full-range invalidation after DiffUtil already ran (or instead of payloads).

**Where**
- `ui/teams/members/MembersAdapter.kt` — `updateData(...)` does `submitList(newList) { notifyItemRangeChanged(0, itemCount) }` when leader flag flips
- `ui/enterprises/EnterprisesReportsAdapter.kt` — `setNonTeamMember` → `notifyItemRangeChanged(0, itemCount)`

**Do**
- Add a dedicated payload (Members already has `PAYLOAD_KEY_LEADER` style; extend for “menu/leader-chrome” if needed)
- On external flag change: either include flag in item content + `submitList`, or `notifyItemRangeChanged(0, itemCount, PAYLOAD_…)` **without** also forcing a full bind after `submitList`
- Implement/extend `onBindViewHolder(..., payloads)` to apply only chrome (overflow menu / edit-delete visibility)

**Avoid:** fragment/ViewModel refactors; membership business rules.

**Conflict surface:** two unrelated adapter packages — can be one PR or two 1-file PRs.

---

## Task 4 — Glide recycle clears + lifecycle-safe `Glide.with(...)`

**Why:** `CoursesAdapter` and enterprise adapters load with Glide but lack `onViewRecycled` clear (Voices/Inline/Chat already clear). Recycled holders keep requests/bitmaps → jank + memory pressure on fast scroll. Some fragments use `Glide.with(this)` instead of the view lifecycle owner.

**Where**
- `ui/courses/CoursesAdapter.kt` (Glide cover load; no `onViewRecycled`)
- `ui/enterprises/EnterprisesReportsAdapter.kt`
- `ui/enterprises/EnterprisesFinancesAdapter.kt`
- Spot-check fragments: `EnterprisesReportsFragment`, `EnterprisesFinancesFragment`, `ResourceViewerFragment`
- Reference: `ui/voices/VoicesAdapter.kt`, `ui/courses/InlineResourceAdapter.kt`

**Do**
- Override `onViewRecycled`; `Glide.with(imageView).clear(imageView)` for every ImageView bound
- Null click listeners on recycled image views where set in bind (reports image)
- In Fragments: prefer `Glide.with(viewLifecycleOwner)` (or the target view) over `Glide.with(this)`

**Avoid:** changing image URLs, cache strategy, or layouts.

**Conflict surface:** adapter-only (+ tiny fragment one-liners) — very low; can ship as one tiny PR.

---

## Task 5 — `BaseRecyclerFragment` list micro-opts (fixed size + no adapter swap on refresh)

**Why:** Base list screen never sets `setHasFixedSize(true)`. `onRatingChanged` / `postAddRefresh` call `getAdapter()` and may swap `recyclerView.adapter`, which drops pool/scroll and forces full rebind — worse once Task 1 caches adapters, still wrong if base swaps unnecessarily.

**Where**
- `base/BaseRecyclerFragment.kt` (`onCreateView` recycler setup; `onRatingChanged`; `postAddRefresh`)

**Do**
- `recyclerView.setHasFixedSize(true)` when layout size is stable (same pattern as `LifeFragment` / `SurveyFragment` / chat)
- Refresh path: if adapter already attached and same instance, **do not** reassign; call a narrow refresh hook (existing `OnDiffRefreshListener.refreshWithDiff` / submit current VM state) instead of `getAdapter()` factory side effects
- Keep API minimal — no new framework

**Avoid:** rewriting filter/delete/add business logic; touching every subclass in the same PR beyond what’s required to compile.

**Conflict surface:** one base file — medium attention, still small diff if disciplined.

---

## Task 6 — Stop no-op Diff churn: `refreshWithDiff()` / `submitList(currentList.toList())`

**Why:** `CoursesAdapter` and `SurveysAdapter` “refresh” by copying the same list into `submitList`, scheduling DiffUtil work that usually no-ops after CPU cost. Targeted `notifyItemChanged(index, payload)` already exists in places.

**Where**
- `ui/courses/CoursesAdapter.kt` — `refreshWithDiff()` / `refreshWithDiff(id)`
- `ui/surveys/SurveysAdapter.kt` — `refreshWithDiff()`
- Call sites: `CoursesFragment.onRatingChanged(type, id)`, survey rating/adopt refresh if any

**Do**
- Prefer `notifyItemChanged(index, PAYLOAD_…)` when id known
- Full refresh: only `notifyItemRangeChanged` with a light payload, or require caller to submit a **new** list from VM state
- Delete pure `submitList(currentList.toList())` helpers if unused after change

**Avoid:** changing rating domain logic; only the refresh mechanism.

**Conflict surface:** adapters + thin call-site edits — low.

---

## Task 7 — `CourseFilterController`: inject `DispatcherProvider`, drop hard-coded default

**Why:** Creates `DefaultDispatcherProvider()` and its own `CoroutineScope(SupervisorJob() + main)` instead of the app `DispatcherProvider`. Blocks testability and is inconsistent with DI cleanup; scope lifecycle is easy to get wrong if `clear()` is missed.

**Where**
- `ui/courses/CourseFilterController.kt`
- Construction site(s) in `ui/courses/CoursesFragment.kt` (pass fragment/`viewModel` dispatcher or inject via fragment field)

**Do**
- Constructor-param `DispatcherProvider` (required; no default `DefaultDispatcherProvider()` in production path)
- Prefer `viewLifecycleOwner.lifecycleScope` from the fragment for debounce jobs **or** keep internal scope but ensure `clear()` always cancels (already present — verify all exit paths call it)
- Keep 300ms debounce behavior unchanged

**Avoid:** rewriting filter UX; do not move all filter state into VM in this PR (that’s a later ViewModel expansion task).

**Conflict surface:** courses filter only — low.

---

## Task 8 — Run `CoursesViewModel` client-side sorts on `dispatcherProvider.default`

**Why:** `sortByTitle` / `sortByDate` call `sortCourses` synchronously on the caller thread (often main via UI click) and assign `MutableStateFlow`. Large course lists allocate + compare on main. Load/filter paths already use `withContext(dispatcherProvider.io)`.

**Where**
- `ui/courses/CoursesViewModel.kt` (`sortByTitle`, `sortByDate`, private `sortCourses`)
- Call sites in `CoursesFragment` sort buttons (likely just call VM — keep them)

**Do**
- Make sort entry points `viewModelScope.launch { val sorted = withContext(dispatcherProvider.default) { sortCourses(...) }; _coursesState.update ... }`
- Preserve ascending toggle semantics and emission ordering (cancel previous sort job or single-flight)
- Add/extend a small unit test if one already covers sort order

**Avoid:** changing repository queries or filter algorithm.

**Conflict surface:** one ViewModel file — low.

---

## Task 9 — Collapse Chat multi-collectors into one UI state (ViewModel edge)

**Why:** `ChatDetailFragment` runs two `repeatOnLifecycle` blocks with **seven** separate `launch { flow.collect }` loops, and still injects `ChatRepository` / `UserRepository` for send/fetch/AI work. Extra collectors + fragment-side IO keep the chat surface heavy and block later navigation/Compose moves.

**Where**
- `ui/chat/ChatDetailFragment.kt` (`observeAiProviders`, `observeViewModelData`, repository `@Inject`s, send/fetch launches)
- `ui/chat/ChatViewModel.kt` (already holds many `MutableStateFlow`s)

**Do (keep PR thin — observation + one IO move max)**
- Add a single `chatUiState` / `aiProvidersUiState` via `combine` + `stateIn(WhileSubscribed(5000))` for the fields the fragment only reads together
- Fragment: **one** `collectLatestWhenStarted(uiState)` (use `utils/FlowExtensions.kt`)
- Optionally move `fetchAiProviders` off the fragment into the VM in the same PR if diff stays small; otherwise leave send paths for a follow-up

**Avoid:** speech recognizer rewrite; full “God ViewModel”; layout changes.

**Conflict surface:** chat package only — medium file size but localized.

---

## Task 10 — Parallelize independent ViewModel loads (start with Events detail)

**Why:** Several detail VMs await repository calls one-after-another on the same coroutine. Easy `async`/`await` fan-out cuts time-to-first-paint without architecture changes and unblocks later “expand ViewModel layer” work with a pattern to copy.

**Where (primary PR)**
- `ui/events/EventsDetailViewModel.kt` — `loadData` loads user → meetup → members sequentially

**Optional same-pattern follow-ups (separate tiny PRs if time)**
- `ui/feedback/FeedbackDetailViewModel.kt`
- `ui/health/HealthViewModel.kt` (patient detail path only)
- `ui/user/UserProfileViewModel.kt` (independent stats fetches only)

**Do**
- `coroutineScope { val a = async { ... }; val b = async { ... }; ... }` then assign StateFlows once results return
- Keep cancellation behavior (`viewModelScope`); do not introduce GlobalScope
- Use existing injected repositories + `DispatcherProvider` only if a call is CPU-bound after fetch

**Avoid:** merging unrelated screens into one PR; changing UI contracts; touching SyncManager.

**Conflict surface:** one ViewModel file per PR — very low.

---

## Task 10b (alt slot) — Survey list UI: single state collect + stop map side-channel thrash

Use this instead of Task 9/10 only if chat is too hot this round.

**Why:** `SurveyFragment.setupObservers` launches six collectors. Three only mutate fragment-side `surveyInfoMap` / `bindingDataMap` the adapter already holds by reference.

**Where:** `ui/surveys/SurveyFragment.kt`, `SurveysViewModel.kt`, `SurveysAdapter.kt`

**Do:** one `SurveyListUiState` via `combine` + `stateIn`; one fragment collector; snackbars stay `SharedFlow`.

---

# Stretch (only if a slot frees up — still small)

| Idea | Why later |
|------|-----------|
| `JsonUtils.gson` vs `NetworkModule.provideGson()` dual instances | DI cleanup; behavior must stay identical (`serializeNulls` / modifiers) before swapping |
| `DatabaseService` production-dead in main; still in DI + tests | Cleanup only; not a user-visible perf win |
| Room `@Index` on hot FK/filter columns + `LIKE '%x%'` queries | Real cost, but **schema bump = drop-and-resync** — not a same-day quick win |
| Move remaining Chat send/continue IO fully into `ChatViewModel` | Natural follow-up to Task 9 |
| Dedupe `ResourcesAdapter` `selectedItemIds` vs `selectedItemsMap` | Correctness micro-clean; small perf only |

---

# Suggested merge order (conflict-safe)

1. Task 4 (Glide clear / lifecycle)  
2. Task 2 (view-mode notify + resource id scans)  
3. Task 3 (payload flags)  
4. Task 6 (refreshWithDiff)  
5. Task 10 (EventsDetail parallel load)  
6. Task 8 (VM sort thread)  
7. Task 7 (filter dispatcher)  
8. Task 1 (adapter cache courses/resources)  
9. Task 5 (base recycler) — **after** Task 1 so base assumes cached adapters  
10. Task 9 (chat combine) — largest of the set; end of round  

Tasks 2/3/4/6/7/8/10 can parallelize across authors with almost no overlap.

---

# Definition of done (each PR)

- [ ] Single-purpose diff; description names the hotspot and files  
- [ ] No new public API unless required; no dead code  
- [ ] Existing unit tests still pass (`./gradlew testDefaultDebugUnitTest` for touched area)  
- [ ] Manual smoke: affected list scrolls, toggles grid/list (if relevant), no flicker on rating/sync refresh  
- [ ] Does not start Compose migration, navigation graph work, or SyncManager consolidation  

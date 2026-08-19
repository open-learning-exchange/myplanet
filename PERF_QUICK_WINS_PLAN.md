# Performance Quick-Wins & Micro-Optimizations — PR Merge Round

Goal: 10 granular, easily-reviewable PRs. Performance quick wins and
micro-optimizations that unblock bigger refactors (data layer, DI, ViewModels,
threading). Low risk of merge conflicts (each task touches a small, isolated
surface). No unused code, no big rewrites.

Branch prefix: `claude/perf-<slug>` for each.

---

## Task 1 — Batch the notification "mark synced" DAO calls

**Why:** `NotificationsRepositoryImpl.markNotificationsSynced` (line ~323)
loops `syncResults.forEach { (id, rev) -> notificationDao.markSynced(id, rev) }`
— one `UPDATE` + one transaction per row after every sync. Each call is a
separate Room write transaction; on a large batch this is N separate disk
writes where one batched statement would do.

**Scope (one PR):**
- `data/room/dao/NotificationDao.kt` — add a single batched `@Query` update:
  `markSynced(ids: List<String>, revsById: ...)` OR a `@Transaction` wrapper
  over the existing `markSynced(id, rev)` (mirror the
  `HealthExaminationDao.markUploaded(idToRevMap: Map<String, String?>)`
  pattern already in the repo at line ~32).
- `repository/NotificationsRepositoryImpl.kt` — `markNotificationsSynced`
  calls the batched DAO method once instead of looping.
- Update `NotificationsRepositoryImplTest` to cover the batched path.

**Touches:** 2 main files + 1 test. No UI, no DI wiring changes.
**Unblocks:** data-layer cleanup (per-row write removal is a repeated
pattern; this establishes the batched DAO convention).

**Conflict risk:** Low — `NotificationDao` and the repo method are touched
by no other in-flight feature work.

---

## Task 2 — Replace `Thread.sleep` backoff in `RetryInterceptor` with a
cancellable wait

**Why:** `data/api/RetryInterceptor.kt` `backoff()` (line ~77) uses
`Thread.sleep(minOf(remaining, MAX_BACKOFF_SLICE_MS))` in a loop. This blocks
an OkHttp dispatcher thread for up to the full backoff window per retry, and
the 250 ms slicing still keeps the thread parked. The loop already checks
`chain.call().isCanceled()` on each slice, so swapping `Thread.sleep` for
`chain.call().isCanceled()` + a `Thread.sleep`/`wait`-free approach is a
micro-optimization that frees dispatcher threads and improves cancellation
latency.

**Scope (one PR):**
- Replace the sleep loop with `Thread.sleep` replaced by a tighter
  cancel-aware wait (e.g. use the call's cancellation via a shorter sleep
  interval + check, OR move to a wait on `chain.call()` if a timeout API is
  available). Keep the exact same total delay semantics (deadline-based) and
  the `InterruptedException` → `IOException` mapping.
- Keep `MAX_BACKOFF_SLICE_MS` cancel-check cadence but lower the per-slice
  sleep so cancellation is detected sooner (e.g. 50 ms slice).
- No behavior change to retry count or delay magnitudes.

**Touches:** 1 file (`RetryInterceptor.kt`) + its test.
**Unblocks:** threading cleanup; removes the project's one flagged
`Thread.sleep` blocking call.

**Conflict risk:** Low — single interceptor file, no other task here touches
the API layer.

---

## Task 3 — Cache `ContextCompat.getColor` results in adapter/holder init

**Why:** Several adapters call `ContextCompat.getColor(context, …)` inside
`onBindViewHolder` or per-bind — re-resolving the same color resource on every
row bind. Examples:
- `ui/user/UserArrayAdapter.kt` (lines 43, 45, 66, 68) — `md_grey_300` and
  `transparent` resolved on every bind and every selection payload.
- `ui/sync/ServerAddressAdapter.kt` (lines 107, 111) — `selected_color` /
  `transparent` resolved in `updateSelectionState`.
- `ui/enterprises/EnterprisesFinancesAdapter.kt` `updateBackgroundColor` —
  resolves colors + builds a new `GradientDrawable`/`LayerDrawable` per even
  row bind.

**Scope (one PR):** resolve the constants once per adapter (in `init` or as
`private val` fields, like `StatsAdapter` already does at lines 20–21) and
reuse. For `EnterprisesFinancesAdapter`, build the two background drawables
once and reuse `mutate()`-d instances.

**Touches:** 3 adapter files. No DI, no data layer.
**Unblocks:** RecyclerView bind hot-path cleanup (establishes the
"resolve resources once" convention for the later Compose migration).

**Conflict risk:** Low — each adapter is self-contained; these specific
adapters aren't touched by any other task in this round.

---

## Task 4 — `UserArrayAdapter`: stop re-resolving the avatar dimension and
hardening the selection diff

**Why:** `UserArrayAdapter.kt` resolves
`parent.context.resources.getDimensionPixelSize(R.dimen._80dp)` lazily only
in `onCreateViewHolder` (good), but the selection-update path
(`onBindViewHolder` payloads, lines 38–50) calls
`notifyItemChanged(prevPos, PAYLOAD_SELECTION)` + current — fine — yet the
full `onBindViewHolder` re-resolves `ContextCompat.getColor` twice per row
(see Task 3) and re-runs `ImageUtils.loadProfileImage` on every rebind even
when the image string is unchanged.

**Scope (one PR):**
- Hoist the two colors to `init` (overlaps Task 3 only in this file — do them
  together in this PR instead and drop the `UserArrayAdapter` lines from
  Task 3's scope).
- In the selection payload path, skip reloading the profile image (only
  update background). The diff payload already carries `PAYLOAD_SELECTION`;
  just don't fall through to `super.onBindViewHolder` for it.
- Keep the `getFullName`/`joined` text logic unchanged.

**Touches:** 1 file (`UserArrayAdapter.kt`).
**Unblocks:** the "payload = partial bind" pattern that the later Compose
migration will rely on conceptually.

**Conflict risk:** Low — single file. Coordinate with Task 3 (this PR owns
`UserArrayAdapter`; Task 3 owns the other two adapters).

---

## Task 5 — `EnterprisesFinancesAdapter`: stop allocating drawables per bind

**Why:** `updateBackgroundColor` (lines 80–91) constructs a new
`GradientDrawable`, sets stroke + gradient type, wraps in a single-layer
`LayerDrawable`, and sets insets — on every even-position bind. This is a
per-scroll allocation + GC pressure hot path.

**Scope (one PR):**
- Build the alternating-row background drawables once (two `GradientDrawable`
  instances, `mutate()`-d), store as `private val`, and just assign in
  `updateBackgroundColor`.
- Drop the single-element `LayerDrawable` wrapper (a `GradientDrawable` alone
  can be set as the background; keep the inset if visually required by setting
  padding once on the holder's root instead).
- Verify visually that row striping is unchanged.

**Touches:** 1 file. Test: existing adapter test if present; else add a small
Robolectric bind test asserting background is set on even/odd.

**Unblocks:** the RecyclerView bind-cost cleanup narrative; removes an obvious
allocation inefficiency before the broader UI refactor.

**Conflict risk:** Low — single adapter file.

---

## Task 6 — `EventsAdapter`: cache formatted dates for payload path too, and
avoid re-creating the payload `Set` per bind

**Why:** `EventsAdapter.kt` has a `dateCache` (lines 78–79) used in the full
bind but the payload path (lines 55–56) calls `formatDate` directly without
the cache — so partial updates (date change) reformat on every payload bind.
Also the diff returns a fresh `mutableSetOf` per changed field comparison.

**Scope (one PR):**
- Route the payload `START_DATE`/`END_DATE` branches through the same
  `dateCache.getOrPut(...)` used by full bind.
- Keep the `getChangePayload` set semantics; no change to the diff contract.

**Touches:** 1 file (`EventsAdapter.kt`).
**Unblocks:** the DiffUtil/payload consistency pattern — payload and full bind
should hit the same helpers (a convention that makes the later Compose
migration's `items(key=…)` swap trivial).

**Conflict risk:** Low — single adapter file, no overlapping task.

---

## Task 7 — `VoicesAdapter` diff: drop redundant `.toList()` allocations in
`areContentsTheSame` / `getChangePayload`

**Why:** `VoicesAdapter.kt` (lines 83–84, 93, 96) calls
`oldItem.labels?.toList() == newItem.labels?.toList()` and the same for
`imageUrls`. `labels` and `imageUrls` are already `List<String>?` (see
`News.kt` lines 47–49). `.toList()` creates a defensive copy per comparison
field per row pair during every diff — O(n) allocations on each
`submitList`. `List.equals` already does element-wise equality; the copies are
unnecessary.

**Scope (one PR):**
- Replace `oldItem.labels?.toList() == newItem.labels?.toList()` with
  `oldItem.labels == newItem.labels` (and `imageUrls` likewise) in
  `areContentsTheSame` and `getChangePayload`.
- Keep all other field comparisons unchanged. The semantics are identical
  because `List<String>.equals` is element-wise.

**Touches:** 1 file (`VoicesAdapter.kt`), diff callback block only.
Test: add/extend `VoicesAdapterTest` to assert two News with same labels but
different list implementations are equal under the callback.

**Unblocks:** the DiffUtil hot path is a repeated offender; this is the
smallest, safest removal of an obvious inefficiency before a broader adapter
review.

**Conflict risk:** Low — single diff block. Confirm no other PR this round
touches `VoicesAdapter` (none do).

---

## Task 8 — `VoicesRepositoryImpl`: avoid double JSON parse in
`getCommunityNews` and dedupe the `distinctUntilChanged` predicate

**Why:** `VoicesRepositoryImpl.kt` `getCommunityNews` (lines 156–186) parses
`viewIn` into `parsedViewIn` inside `.map` (line 175) and then calls
`isVisibleToUser` (line 178), which re-reads `news.parsedViewIn` (line 139) —
fine, that's the cached parse — but the `distinctUntilChanged` predicate
(lines 158–170) compares `o.viewIn == n.viewIn` (the raw JSON string) AND
separately compares `imageUrls?.toList()` (same unnecessary copy as Task 7)
and `labels?.toSet()` (another allocation). The string compare already
captures viewIn changes; the extra `.toList()`/`.toSet()` allocations on
every emission are avoidable.

**Scope (one PR):**
- In the `distinctUntilChanged` predicates of `getCommunityNews` and
  `getDiscussionsByTeamIdFlow`, replace `imageUrls?.toList() ==
  n.imageUrls?.toList()` with `o.imageUrls == n.imageUrls`, and
  `labels?.toSet() == n.labels?.toSet()` with `o.labels == n.labels`
  (`List<String>.equals` is element-wise; set-equality only differs by order
  — if order matters, keep `.toSet()` but document why; if not, drop it).
- No change to the actual filtering logic or `isVisibleToUser`.

**Touches:** 1 file (`VoicesRepositoryImpl.kt`), two flow predicates.
Test: extend `VoicesRepositoryImplTest` to assert two identical-emission
lists don't re-trigger downstream.

**Unblocks:** the Flow/distinctUntilChanged cleanup that the later
"expand ViewModel/use layers" refactor depends on.

**Conflict risk:** Low — single repo file, flow predicates only.

---

## Task 9 — DI cleanup: move `SharedPrefManager`/`UserSessionManager` field
injection in `CommunityTabFragment` to a small ViewModel (or at least
lazy-resolve)

**Why:** `CommunityTabFragment.kt` (lines 22–26) field-injects
`SharedPrefManager`, `ConfigurationsRepository`, `UserSessionManager` and
then does its only async work in `viewLifecycleOwner.lifecycleScope.launch`
(lines 38–52) — reading prefs and building a pager. The fragment holds these
refs across its lifecycle and does the work on the main-safe `lifecycleScope`
(default Main dispatcher). This is the smallest "expand ViewModel and use
layers" micro-step: move the synchronous pref reads + `getUserModel()` into a
`CommunityTabViewModel` that exposes a `StateFlow` so the fragment only
collects.

**Scope (one PR):**
- Add `ui/community/CommunityTabViewModel.kt` (`@HiltViewModel`) that takes
  `SharedPrefManager`, `ConfigurationsRepository`, `UserSessionManager` and
  exposes a `StateFlow<CommunityTabState>` computed in `init` /
  `viewModelScope`.
- `CommunityTabFragment` collects the state with `collectWhenStarted` and
  just wires the pager + titles.
- No change to `CommunityPagerAdapter` or its constructor contract.

**Touches:** 2 files (new ViewModel + fragment). Bind ViewModel in
`RepositoryModule`/no new binding needed (it's `@HiltViewModel`).
**Unblocks:** the "Expand ViewModel and Use Layers" roadmap item — this is
the template the other fragments can follow.

**Conflict risk:** Low — `CommunityTabFragment` and a brand-new ViewModel
file; no other task here touches community UI.

---

## Task 10 — `LeadersViewModel`: expose a `StateFlow` from a ViewModel-scoped
job and stop reading prefs on the Main thread in `init`

**Why:** `LeadersViewModel.kt` calls `loadLeaders()` in `init` (line 16) which
synchronously calls `sharedPrefManager.getCommunityLeaders()` and parses JSON
(`UserEntity.parseLeadersJson`) on whatever thread constructs the ViewModel
— typically Main. For a large leaders blob this is a main-thread JSON parse
on every screen entry.

**Scope (one PR):**
- Move `loadLeaders()` into `viewModelScope.launch(Dispatchers.Default)` (use
  the injected `DispatcherProvider` if one is available; if not, add it via
  the existing `DispatcherModule` — but prefer not adding a new dep, so use
  `Dispatchers.Default` and note the `DispatcherProvider` refactor as
  follow-up).
- Keep the public `leaders: StateFlow<List<UserEntity>>` API identical.
- No UI changes to `LeadersFragment`.

**Touches:** 1 file (`LeadersViewModel.kt`).
**Unblocks:** the threading/dispatcher cleanup narrative; demonstrates the
"ViewModels shouldn't do I/O in `init`" convention for the broader VM pass.

**Conflict risk:** Low — single ViewModel file.

---

## Conflict-safety summary

| Task | Files touched | Overlaps with |
|------|---------------|---------------|
| 1 | NotificationDao, NotificationsRepositoryImpl (+test) | none |
| 2 | RetryInterceptor (+test) | none |
| 3 | ServerAddressAdapter, EnterprisesFinancesAdapter | Task 5 (see note) |
| 4 | UserArrayAdapter | none (absorbs the UserArrayAdapter part of 3) |
| 5 | EnterprisesFinancesAdapter | Task 3 — **split**: Task 3 does NOT touch EnterprisesFinancesAdapter; Task 5 owns it |
| 6 | EventsAdapter | none |
| 7 | VoicesAdapter (diff block) | none |
| 8 | VoicesRepositoryImpl (flow predicates) | none |
| 9 | CommunityTabFragment + new CommunityTabViewModel | none |
| 10 | LeadersViewModel | none |

To keep merge conflicts at zero: do Tasks 3 and 5 in sequence (or merge 3
before starting 5) since they touch sibling adapters in the same package.
Every other task is file-disjoint and can be reviewed in parallel.

## Per-PR checklist (apply to each of the 10)

- [ ] Branch: `claude/perf-<slug>`
- [ ] One concern per PR (no drive-by edits outside the listed files)
- [ ] No unused code added (no speculative helpers, no "in case we need it")
- [ ] Build: `./gradlew assembleDefaultDebug`
- [ ] Tests: `./gradlew testDefaultDebugUnitTest` (CI gates on this)
- [ ] Title in house style: `perf: <what> (fixes #<issue>)` — create a
      tracking issue if none exists (the `prepping` skill handles this)
- [ ] If touching a DiffUtil callback: add/extend the matching adapter test
- [ ] If touching a DAO: add/extend the repository test for the batched path

# myPlanet Refactor Quick-Wins — 10 Reviewable Tasks

Context: ~9.99 PRs/day review budget. These are deliberately **small, independent,
non-overlapping** changes so they can be merged in any order without conflicts.
Focus: **performance quick wins + micro-optimizations** that unblock bigger
refactors (DI cleanup, data-layer finish, ViewModel expansion) later.
No big rewrites. No unused scaffolding. Granular, easily reviewable.

Evidence basis (verified against current sources):
- `DiffUtils.kt` (itemCallback / standardItemCallback / calculateDiff) already exists and is the house standard.
- Adapters already on `ListAdapter` (48 files) — so remaining work is *correctness/efficiency inside binders*, not conversions.
- `DispatcherProvider` injected everywhere (77 files) — hard-coded `Dispatchers.*` is essentially gone; remaining threading wins are elsewhere.
- `JsonUtils.gson` is a shared singleton; `Converters` uses it (good). Some repos still `gson.fromJson(...JsonArray)` per-row.
- Only 2 `notifyDataSetChanged()` left: `CoursesAdapter.setViewMode` and `ResourcesAdapter.setViewMode`.
- `SimpleDateFormat` instances scattered in adapters/fragments (per-row alloc risk); `TimeUtils` already has cached `DateTimeFormatter`s.
- `DatabaseService` is vestigial (only `DatabaseModule` provides it; no repository injects it).
- Realtime sync (`RealtimeSyncManager.dataUpdateFlow`) + `collectWhenStarted` used widely — but some observers run heavy work off the main thread or re-collect on every row bind.

---

## Task 1 — Replace `notifyDataSetChanged()` with payload-aware diffing in `CoursesAdapter` + `ResourcesAdapter`

**Type:** DiffUtil / ListAdapter  |  **Files:** `ui/courses/CoursesAdapter.kt`, `ui/resources/ResourcesAdapter.kt`

Both adapters are already `ListAdapter` + `DiffUtils.standardItemCallback`, but
`setViewMode()` still calls `notifyDataSetChanged()` (full rebind, no animations,
scroll-position jank). `CoursesAdapter` already has a `payloadSelector`
(`PAYLOAD_SELECTION`/`PAYLOAD_PROGRESS`) — extend the same pattern for a
`PAYLOAD_VIEW_MODE` (grid↔list) so toggling view mode rebinds via targeted
`onBindViewHolder(payload)` instead of nuking the whole list. Do the same minimal
change in `ResourcesAdapter`. Removes the last two `notifyDataSetChanged()` in the app.

**Unblocks:** consistent payload-based partial rebinds everywhere (precedent for Compose migration later).

---

## Task 2 — Move per-row JSON parsing out of `VoicesAdapter.onBindViewHolder`

**Type:** Data layer / performance hotspot  |  **Files:** `ui/voices/VoicesAdapter.kt`, `utils/JsonUtils.kt`, `model/News.kt`

`News` stores `viewIn`, `conversations`, `images` as JSON strings. The adapter already
has a `preParseNews()`/`parsedViewIn`/`parsedConversations` cache, but `onBindViewHolder`
still calls `JsonUtils.extractSharedTeamName(news)` and image parsing per bind, and
`parseViewIn`/`parseConversations`/`parseImageUrls` catch-and-retry on every scroll.
Hoist all parsing into `prepareSubmitList`/`preParseNews` (already the intended path),
guaranteeing each row's JSON is parsed **once** at submit time instead of per-bind.
Cache `parsedImages` the same way `parsedViewIn`/`parsedConversations` are cached.

**Unblocks:** finishing the data layer (model exposes parsed objects, not raw strings), and smooth scrolling on the largest UI surface (Voices).

---

## Task 3 — Centralize the remaining ad-hoc `SimpleDateFormat` usages into cached `TimeUtils` formatters

**Type:** Performance micro-optimization  |  **Files:** `ui/notifications/NotificationsAdapter.kt`, `ui/teams/TeamCalendarFragment.kt`, `ui/enterprises/EnterprisesFinancesFragment.kt`, `services/sync/SyncManager.kt`, `utils/SyncTimeLogger.kt`

`SimpleDateFormat` is not thread-safe and is expensive to construct. Several spots
instantiate one per call/site (`NotificationsAdapter.getDateFormat()`,
`TeamCalendarFragment.getDateFormat()`, per-fragment formatter in finances,
and inline `SimpleDateFormat("HH:mm:ss.SSS")` in `SyncManager` logs). `TimeUtils`
already holds cached `DateTimeFormatter` instances. Migrate these to shared
`TimeUtils`/`DateTimeFormatter` (thread-safe, immutable) or a single cached instance
per class. Small, mechanical, per-file edits — no behavior change.

**Unblocks:** consistent time formatting ahead of any Compose/UI-layer migration; removes per-row allocation in list adapters.

---

## Task 4 — Remove the vestigial `DatabaseService` + `DatabaseModule` (DI cleanup)

**Type:** Dependency-injection cleanup  |  **Files:** `data/DatabaseService.kt`, `di/DatabaseModule.kt`

Per `CLAUDE.md`, "no repository injects `DatabaseService` anymore; multi-DAO atomic
work uses Room's `withTransaction`." Confirmed: only `DatabaseModule` provides it and
nothing consumes it. Delete `DatabaseService` and its provider in `DatabaseModule`.
Zero call sites to update. Shrinks the DI graph and removes a misleading wrapper so
future data-layer work goes straight to DAOs/`withTransaction`.

**Unblocks:** "Complete Dependency Injection Cleanup" + "Finish Cleaning the Data Layer" (roadmap #1, #4).

---

## Task 5 — Stop re-parsing `viewIn` JsonArray per call in repository aggregation paths

**Type:** Data layer micro-optimization  |  **Files:** `repository/VoicesRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`, `repository/FeedbackRepositoryImpl.kt`

Several repository methods parse the same JSON string columns repeatedly inside loops
(e.g. `gson.fromJson(news.viewIn, JsonArray::class.java)` in multiple VoicesRepository
methods, `gson.fromJson(feedback.messages, JsonArray::class.java)` in FeedbackRepository).
Where a method reads the same column more than once or parses per-item in a loop,
parse once into a local `val` and reuse. Pure local change, no API/behavior change,
no new abstractions.

**Unblocks:** cheaper sync/upload preparation; aligns with roadmap #5 (consolidate sync/upload) by making the hot paths cheaper before merging them.

---

## Task 6 — Debounce/search-input hardening: convert remaining `TextWatcher` search boxes to the existing `textChanges()` Flow extension

**Type:** Threading / Flow / performance  |  **Files:** `ui/teams/TeamFragment.kt`, `ui/health/MyHealthFragment.kt`, `utils/ViewExtensions.kt`

`utils/ViewExtensions.kt` already provides a `textChanges()` Flow (used with debounce
elsewhere), and `CourseFilterController` shows the manual-watcher-then-remove pattern.
`TeamFragment.setupTextWatcher()` and `MyHealthFragment.setTextWatcher(...)` still use
raw `TextWatcher` callbacks that trigger DB queries on every keystroke. Switch these
two to `textChanges()` + `debounce(...)` collected via `collectWhenStarted`, removing
the manual watcher bookkeeping. Localized to the two fragments.

**Unblocks:** consistent Flow-based input handling (ViewModel-expansion roadmap #3) and fewer redundant DB queries.

---

## Task 7 — Cache `Realm`-legacy→Room row mapping in sync repositories (avoid double `fromJson` per doc)

**Type:** Sync performance micro-optimization  |  **Files:** `repository/SyncRepositoryImpl.kt` (`keysObject.add("keys", gson.fromJson(gson.toJson(batch), ...))`), plus identical serialize→parse round-trips in `model/MyTeam.kt` (`JsonParser.parseString(JsonUtils.gson.toJson(object))` ×2)

A handful of spots serialize an object with Gson only to immediately re-parse it back
into a `JsonObject`/`JsonArray` (`toJson` → `fromJson` / `JsonParser.parseString`).
These round-trips run inside batch sync loops. Replace with the direct tree conversion
(`gson.toJsonTree(obj).asJsonObject`) which skips the intermediate String entirely.
One-line-per-site change, clearly reviewable, no behavior change.

**Unblocks:** faster batch sync (roadmap #5) with zero structural change.

---

## Task 8 — Guard long-running collectors against duplicate work on re-START (idempotent `collectWhenStarted`)

**Type:** Long-running observers/listeners  |  **Files:** `ui/myhealth/MyHealthFragment.kt` (the `collectWhenStarted(realtimeSyncManager.dataUpdateFlow)` block), `ui/sync/SyncActivity.kt` (`collectWhenStarted(syncManager.syncStatus)`), `ui/dashboard/DashboardActivity.kt` (sync-status collector)

`collectWhenStarted` re-subscribes every time the screen returns to STARTED. Most
collectors are pure UI updates (fine), but a few trigger repository reloads / sync
re-queries on each emission *and* each re-subscription. Audit these three collectors
and make the handler idempotent: skip reload when the emitted value equals the last
handled value (`distinctUntilChanged()` on the flow) or when a load is already in
flight. Add `distinctUntilChanged()` where the upstream is a `StateFlow` of the same
data. Small, additive operators — no logic rewrite.

**Unblocks:** reduced redundant DB/sync work; safer observer patterns before ViewModel expansion.

---

## Task 9 — Provide a single app-scoped `Gson` via Hilt and stop relying on the `JsonUtils.gson` singleton in DI-managed classes

**Type:** Dependency-injection cleanup / testability  |  **Files:** `di/NetworkModule.kt` (already has `provideGson()`), repositories that import `JsonUtils.gson` directly (`VoicesRepositoryImpl`, `SubmissionsRepositoryImpl`, `HealthRepositoryImpl`, `ChatRepositoryImpl`, `ConfigurationsRepositoryImpl`)

`NetworkModule.provideGson()` already exists, but many `@Inject constructor` repositories
bypass DI and reach for the `JsonUtils.gson` object directly. Inject the provided `Gson`
into those repositories (constructor param) instead of the static singleton. Mechanical,
one-file-per-change, improves testability (can inject a configured Gson) and matches the
DI graph. Leave `Converters`/pure `object` utils on the singleton (they can't take DI) —
this task targets only Hilt-managed classes.

**Unblocks:** "Complete Dependency Injection Cleanup" (#4); makes JSON config swappable for tests.

---

## Task 10 — Move `VoicesAdapter` image/label heavy setup off the bind path with payload-scoped partial binds

**Type:** DiffUtil / ListAdapter + performance  |  **Files:** `ui/voices/VoicesAdapter.kt`

`onBindViewHolder(position)` unconditionally runs the full pipeline (`resetViews`,
`loadImage`, `labelManager.setupAddLabelMenu`, `handleChat`, `setMemberClickListeners`)
even when only the reply count changed (there's already a `PAYLOAD_REPLY_COUNT` path and
a payloads-override). Extend the payload handling so that the common fine-grained updates
(reply-count increment, label add/remove, own-post edit) only rebind the affected views
instead of the entire row. Builds directly on the existing `getChangePayload`/payloads
override and the `DiffUtils.itemCallback` the adapter already passes in.

**Unblocks:** smooth scrolling on the busiest list; proves out the payload pattern used in Task 1 across the codebase before the Compose migration (#6).

---

## How to sequence these in a ~10 PR/day review round

- **Order is free** — every task touches a disjoint file set, so any merge order avoids conflicts.
- Suggested pairing by reviewer familiarity:
  - Adapter/DiffUtil reviewer: Tasks 1, 2, 10
  - Data/Repository reviewer: Tasks 5, 7, 9
  - DI/architecture reviewer: Tasks 4, 9
  - UI/Flow reviewer: Tasks 6, 8
  - Util/perf reviewer: Task 3
- Each task is designed to be a single, small PR (1–3 files, no new dependencies, no dead code) so it stays within the easy-review budget.

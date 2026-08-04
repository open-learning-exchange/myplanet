# Curated task backlog

Distilled from 14 agent task lists (7 agents × 2 prompts: a performance/quick-win prompt and a
layering/repository-boundary prompt). Duplicates are merged into a single entry, and proposals that
did not survive a check against the codebase are listed with the reason in
[Rejected proposals](#rejected-proposals).

Every claim below was verified against `master` at `968f8bf` unless the entry says otherwise.
Line numbers are from that commit and will drift.

**Legend** — `Sources:` names which of the 14 lists proposed the task.
Agent order per prompt was: jules, allhands, claude, codex, kimi, qwen, devin.
`A` = performance prompt, `B` = boundaries prompt.

---

## Round A — performance, threading, list rendering

### A1. Fix `areContentsTheSame` on adapters whose model has no `equals()`

`DiffUtils.itemCallback(..., areContentsTheSame = { old, new -> old == new })` falls back to
*identity* comparison when the model is a plain or `open` class with no `equals()` override. Room
re-materializes new instances on every emission, so the comparison is **always false** and every
`submitList` rebinds every visible row — defeating the point of `ListAdapter`.

Verified: 20 call sites use bare `oldItem == newItem`. Confirmed-broken ones (model is a
plain/`open` class, no `equals`):

| Adapter | Model | Line |
|---|---|---|
| `ui/references/ReferencesAdapter.kt` | `Reference` (class) | 17 |
| `ui/life/LifeAdapter.kt` | `MyLife` (class) | 147 |
| `ui/health/HealthExaminationAdapter.kt` | `HealthExamination` (class) | 146 |
| `ui/surveys/SurveysAdapter.kt` | `StepExam` (open @Entity) | 30 |
| `ui/enterprises/EnterprisesReportsAdapter.kt` | `MyTeam` (open @Entity) | 106 |

Audit the other 15 sites too (`TeamsAdapter:145`, `MembersAdapter:44`, `StatsAdapter:17`,
`AchievementsAdapter:88`, `SubmissionsAdapter:25`, `SubmissionsListAdapter:61`,
`QuestionAnswerAdapter:57`, `FeedbackReplyAdapter:33`, `ChatShareTargetAdapter:81`,
`NotificationsAdapter:32`, `EventsDescriptionAdapter:15`, `CoursesProgressAdapter:111`,
`ProgressGridAdapter:18`, `CoursesStepsAdapter:69`, `ServerAddressAdapter:21`,
`EnterprisesFinancesAdapter:30`) — the ones over `data class`/`String` are fine, the rest are not.

**Do:** replace `oldItem == newItem` with an explicit comparison of the fields the row actually
binds, mirroring `ui/teams/tasks/TeamsTasksAdapter.kt:124-130`.
**Do not:** add `equals()`/`hashCode()` to `@Entity` models or convert them to `data class` — Room
entities are `open` on purpose.

*Sources: claude-A#1.*

---

### A2. Split `data/room/dao/LegacyEntityDaos.kt` into per-DAO files

One 154-line file holds `UserDao`, `CourseDao`, `CourseStepDao`, `ExamDao`, `QuestionDao`,
`SubmissionDao`, `AnswerDao`, `TeamDao`. Every future data-layer task has to edit it, and it
violates the repo's own `*Dao.kt` convention — the other 30 DAOs each have their own file.

**Do:** pure file move — 8 new files, delete `LegacyEntityDaos.kt`. Same package, so zero import
changes elsewhere. No query-text changes in this PR.

**Scheduling:** this collides with A7, A9 and A11. Merge it first, alone, or defer it a round.

*Sources: claude-A#2.*

---

### A3. Lifecycle-scope bare fragment flow collections

Bare `lifecycleScope.launch { flow.collect { … } }` keeps collecting while the fragment is stopped,
holding upstream Room `Flow`s and their query observers alive and pushing updates into detached
views. `utils/FlowExtensions.kt` already ships `Fragment.collectWhenStarted` /
`collectLatestWhenStarted` (plus `LifecycleOwner` overloads), used by 20 other files.

Files with a bare collect (union of every list that proposed this):
`ui/teams/TeamFragment.kt`, `ui/teams/PlanFragment.kt`,
`ui/teams/resources/TeamResourcesFragment.kt`, `ui/teams/courses/TeamCoursesFragment.kt`,
`ui/teams/TeamDetailFragment.kt`, `ui/teams/tasks/TeamsTasksFragment.kt`,
`ui/teams/voices/TeamsVoicesFragment.kt`, `ui/teams/TeamCalendarFragment.kt`,
`ui/courses/TakeCourseFragment.kt`, `ui/courses/CoursesFragment.kt`,
`ui/courses/CourseStepFragment.kt`, `ui/personals/PersonalsFragment.kt`,
`ui/resources/CollectionsFragment.kt`, `ui/resources/AddResourceFragment.kt`,
`ui/enterprises/EnterprisesFinancesFragment.kt`, `ui/dashboard/ActivitiesFragment.kt`,
`ui/community/CommunityServicesFragment.kt`, `ui/community/CommunityTabFragment.kt`,
`ui/exam/ExamTakingFragment.kt`, `ui/exam/UserInformationFragment.kt`,
`ui/user/UserProfileFragment.kt`, `ui/user/AchievementFragment.kt`,
`ui/user/EditAchievementFragment.kt`, `ui/feedback/FeedbackFragment.kt`,
`ui/health/MyHealthFragment.kt`, `ui/settings/StorageBreakdownFragment.kt` (line 198 only — 121-122
is already correct), `ui/components/MarkdownDialogFragment.kt`, `ui/chat/ChatHistoryFragment.kt`,
`ui/viewer/ResourceViewerFragment.kt`, `ui/voices/VoicesFragment.kt`.

**Do:** swap to `collectWhenStarted(flow) { … }`, or `collectLatestWhenStarted` where only the
newest emission matters. **Do not** add a new extension — the helpers exist.
**Split into 3-4 PRs by feature package**; do not land 30 files at once.

*Sources: claude-A#3, devin-A#2/#3, jules-B#5/#6, kimi-B#10 (partly).*

---

### A4. Lifecycle-scope bare Activity flow collections

Same defect, Activity side. `LifecycleOwner.collectWhenStarted` already exists.

Files: `ui/sync/LoginActivity.kt`, `ui/settings/SettingsActivity.kt`,
`ui/dictionary/DictionaryActivity.kt`, `ui/health/HealthExaminationActivity.kt`,
`ui/feedback/FeedbackDetailActivity.kt`, `ui/voices/ReplyActivity.kt`,
`ui/user/BecomeMemberActivity.kt`.

Kept separate from A3 because `LoginActivity.kt` (702 lines) is churn-heavy and worth reverting
independently.

*Sources: claude-A#4, devin-A#3.*

---

### A5. `NetworkUtils` rebuilds its Hilt entry point on every access

`utils/NetworkUtils.kt:28-29` declares the entry point with a `get()` accessor, so **every read**
performs a fresh `EntryPointAccessors.fromApplication` graph lookup and cast. `NetworkUtils` is
called from network-state callbacks and sync paths. Two properties below, the same file already
does it right (`sharedPrefManager by lazy`, `coroutineScope by lazy`).

**Do:** change `get() =` to `by lazy { }`. One line.

*Sources: claude-A#5.*

---

### A6. `NotificationUtils.getInstance()` allocates per call

`utils/NotificationUtils.kt:93-96` does an `EntryPointAccessors.fromApplication` lookup **and**
constructs a new `NotificationManager(context, timeProvider)` on every invocation, while callers
treat it as a getter.

**Do:** cache the `timeProvider` lazily (off `MainApplication.context`, as the sibling utils do), or
cache the manager itself if the call sites pass a stable application context. State which in the PR.

*Sources: claude-A#6.*

---

### A7. `TeamsRepositoryImpl`: push `teamDao.getAll()` filtering into SQL

`teamDao.getAll()` is called at **15 sites** — lines 81, 125, 180, 214, 241, 312, 323, 341, 431,
444, 776, 1003, 1134, 1264, 1326 — each loading the entire `teams` table into memory and then
filtering in Kotlin. On a synced device this runs on every team screen open.

**Do:** add a `@Query` on `TeamDao` per site. Cheapest, most unambiguous first:

- `:81` `.filter { it.updated }.map { it }` → `WHERE updated = 1` (the `.map { it }` is a no-op copy)
- `:1134` `.any { … }` → `SELECT EXISTS(...)`
- `:776` `removeResourceLink` `.firstOrNull { … }` → `WHERE teamId = :teamId AND resourceId =
  :resourceId AND docType = 'resourceLink' LIMIT 1`; preserve the existing soft-removal behavior
- `:311/:322/:333` (`getShareableEnterpriseDetails`, `getTeamDetails`,
  `getShareableEnterpriseSummaries`) → filter `docType`/`type`/`status` in SQL

**Scope guard:** this is the 1434-line file. Query pushdown only — do not split or restructure it.
Cap the PR at ~8 sites and file a follow-up for the rest.

*Sources: claude-A#7, devin-A#6, codex-A#1.*

---

### A8. `getTeamMemberStatuses`: one pass with set-backed IDs

`TeamsRepositoryImpl.getTeamMemberStatuses()` keeps valid IDs as a list and scans `userEntries`
three times to build the membership, leader and pending-request sets.

**Do:** convert IDs to a set once, preserve input order separately for the returned map, and fill
all three result sets in a single loop. Keep deletion-pending membership semantics and leader/request
rules exactly as they are.

**Acceptance:** blank and duplicate team IDs behave as today, output key order is stable,
delete-pending memberships are excluded only where currently excluded, unrelated team rows do not
appear. Merge after A7; limit edits to this helper.

*Sources: codex-A#10.*

---

### A9. `UserRepositoryImpl`: same pushdown for `userDao.getAll()`

Identical pattern, separate file — lines 103, 121, 135, 161, 169, 179, 336, 910, 1015, 1244.
Line 161 is `userDao.getAll().map { it }`, an outright no-op copy of the whole table.

**Do:** targeted `@Query` per site; delete the `.map { it }` at `:161`.
Kept separate from A7 so the two large repositories never sit in the same PR.

*Sources: claude-A#8.*

---

### A10. `insertUsersFromSync`: linear matching instead of repeated scans

`UserRepositoryImpl.insertUsersFromSync()` (`userDao.getAll().toMutableList()` at :1244) repeatedly
uses `firstOrNull` on `existingUsers` and `removeAll` on `usersToUpsert` for every incoming document,
so a large user sync becomes quadratic.

**Do:** build lookup maps for canonical IDs and eligible guest usernames once, and accumulate
upserts in an insertion-ordered map keyed by final ID. Update the maps when a guest is promoted so
duplicate documents in the same batch retain current last-write behavior.

**Acceptance:** existing-user matching, guest promotion/deletion, duplicate IDs, malformed documents
and bulk `upsertAll` stay covered; add a test proving each final ID is upserted once.
Touches `UserRepositoryBulkInsertTest.kt`. Coordinate with A9 (same file).

*Sources: codex-A#2.*

---

### A11. Use `COUNT(*)` where the code only reads `.size`

Two sites materialise a full entity list purely to read its size. `SubmissionDao`/`ExamDao` already
carry 22 `SELECT COUNT` queries, so the pattern is established.

- `repository/CoursesRepositoryImpl.kt:183` — `examDao.getByCourseIdAndType(courseId, "courses").size`
- `repository/SurveysRepositoryImpl.kt:367` — `submissionDao.getPendingSurveys(userId).size`

**Do not** add count variants for queries nothing calls.

*Sources: claude-A#9.*

---

### A12. Move list sorting out of adapters into their ViewModels

Adapters sort their own `currentList` and re-`submitList`, so the sort runs on the main thread and
the adapter owns UI state that survives config changes badly.

- `ui/resources/ResourcesAdapter.kt:295-318` — `isAscending`/`isTitleAscending` fields plus
  `sortLibraryList()` / `sortLibraryListByTitle()`, calling `.lowercase(Locale.ROOT)` per element
  *inside the comparator*
- `ui/courses/CoursesAdapter.kt:167-197` — two `sortedWith { }` comparators

**Do:** move the sort-order flag and the sort into the existing `ResourcesViewModel` /
`CoursesViewModel`, emit the sorted list, let the adapter render. Precompute the lowercased sort key
once instead of inside the comparator. Split into two PRs (resources / courses) if it grows.

`SurveysAdapter.kt:53-72` has the same smell but is excluded — A1 already edits that file.

*Sources: claude-A#10, kimi-A#10 (partly).*

---

### A13. Add `indices` to the 8 entities that have none

28 of 36 `@Entity` classes declare `indices`; these 8 do not (verified: zero `indices` occurrences),
so any non-PK lookup is a table scan: `Community`, `Achievement`, `ChatHistory`, `Feedback`,
`SubmitPhotos`, `ApkLog`, `UserChallengeActions`, `Certification`.

**Do:** grep each entity's DAO for the columns actually used in `WHERE`/`ORDER BY`, add `@Index` for
exactly those, then bump `version` in `AppDatabase.kt`. The repo uses
`fallbackToDestructiveMigration(true)` — no `Migration` object needed, data re-pulls from CouchDB.
**Do not** index a column no query filters on; that is a write-cost regression for nothing.

Put the backing query line reference next to each index in the PR body so the reviewer can check.

*Sources: claude-A#11.*

---

### A14. `SharingStarted.Lazily` → `WhileSubscribed(5000)`

`Lazily` keeps the upstream flow alive **forever** once the first subscriber appears — the Room query
observer never stops, even with the screen gone. `WhileSubscribed(5000)` is what the rest of the
codebase uses (`NotificationsViewModel`, `PersonalsViewModel`).

Verified — exactly 10 sites, all of them:
- `ui/submissions/SubmissionDetailViewModel.kt:28,33,38,43,48,53` (`stateIn`)
- `ui/submissions/SubmissionViewModel.kt:42,46,90` (`shareIn`)
- `ui/voices/VoicesViewModel.kt:60` (`stateIn`)

**Do:** swap the `SharingStarted` argument, nothing else. Reviewer should sanity-check that no
downstream reads these outside a lifecycle scope.

*Sources: claude-A#12, devin-A#1.*

---

### A15. Use the injected `Gson` instead of constructing one at call sites

`Gson()` construction builds the full type-adapter registry; `NetworkModule` already provides a
shared singleton. Verified sites:

- `ui/courses/CoursesProgressFragment.kt:46` — `Gson().fromJson(...)` in a parse path
- `repository/EventsRepositoryImpl.kt:120` — `val gson = Gson()` per call
- `repository/UserRepositoryImpl.kt:1329` — the worst one:
  `Gson().fromJson(Gson().toJson(shelfIds), JsonArray::class.java)`, two allocations plus a
  serialize/deserialize round-trip that a direct `JsonArray` build replaces

**Do:** inject `Gson` (constructor for repositories, `@Inject lateinit var` for the fragment).
`utils/JsonUtils.kt:13` holds a single shared instance and is fine.
The `UserRepositoryImpl` site collides with A9/A10 — fold it into whichever lands first.

*Sources: claude-A#13, devin-A#5.*

---

### A16. `ResourceSearchUtils` re-normalises every title on every keystroke

`utils/ResourceSearchUtils.kt:16-22` calls `Utilities.normalizeText(title)` for **every item in the
list, on every character typed**. With a full resource library this is the search-lag source.

**Do:** hoist normalisation so each title is normalised once per list, not once per keystroke — e.g.
normalise into a local array when the list identity changes, or have the caller pass pre-normalised
keys. Also check `Utilities.normalizeText`: if it lowercases, the `ignoreCase = true` on
`startsWith`/`contains` (lines 18, 20) is forcing the slow locale-aware path for nothing.
**Do not** introduce a cache class or new util object — keep it inside the existing function.

*Sources: claude-A#14.*

---

### A17. Hoist per-call `SimpleDateFormat` allocations into cached formatters

`SimpleDateFormat` construction is expensive and several instances are created inside hot paths or
per-item code:

- `services/sync/SyncManager.kt:145,214` — inline `java.text.SimpleDateFormat("HH:mm:ss.SSS")` per
  sync log entry, with no `Locale`
- `repository/TeamsRepositoryImpl.kt:1094` — per-call formatter inside member-visit mapping
- `ui/voices/VoicesActions.kt:218`
- `ui/enterprises/EnterprisesFinancesFragment.kt:41,170`
- `repository/SubmissionsRepositoryExporter.kt:173`
- adapters that format per bind: `ui/teams/members/MembersAdapter.kt`, `ui/teams/TeamsAdapter.kt`,
  `ui/chat/ChatHistoryAdapter.kt`, `ui/events/EventsAdapter.kt`, `ui/courses/CoursesProgressAdapter.kt`

**Do:** hoist to a `companion object` val (or reuse `utils/TimeUtils`) with an explicit `Locale`,
following the caching pattern already in `NotificationsAdapter.getDateFormat()`. `SimpleDateFormat`
is not thread-safe — keep each cached instance confined to the main thread or use a
`ThreadLocal`/`DateTimeFormatter`.

*Sources: devin-A#4, kimi-A#9, qwen-A#10.*

---

### A18. `ChatHistoryAdapter` shares one binding across all ViewHolders

`ui/chat/ChatHistoryAdapter.kt:54` declares `private lateinit var rowChatHistoryBinding` as an
**adapter-level** field and assigns it in `onCreateViewHolder` (line 87) — so every ViewHolder ends up
referencing the last-created binding. This is a live correctness bug, not just a style issue.

**Do:** move the binding into the ViewHolder constructor, the pattern every other adapter in the repo
uses. Also verify `notifyChatShared` keeps using the `PAYLOAD_CHAT_SHARED` path.

While in the file: line 138 is an unchecked `getData() as HashMap<String, List<String>>` — the only
unsafe cast of its kind left in `main`. Replace with `as? Map<String, List<String>> ?: emptyMap()`.

*Sources: devin-A#8, qwen-A#3 (cast only).*

---

### A19. Replace `notifyDataSetChanged()` in the two pager adapters

Verified: exactly two `notifyDataSetChanged()` calls remain in the app, both in `FragmentStateAdapter`
subclasses — `ui/teams/TeamPagerAdapter.kt:32` (`updatePages`) and
`ui/courses/CoursesPagerAdapter.kt:26`. On a `FragmentStateAdapter` this destroys and recreates
*every* page fragment.

**Do:** implement stable `getItemId()`/`containsItem()` (`CoursesPagerAdapter` already tracks
`itemIds`) and compute targeted insert/remove/change notifications by diffing the old and new page
lists — a plain `DiffUtil.calculateDiff` over page ids, consistent with `utils/DiffUtils.kt`.

**Note:** one proposal argued the opposite (delete diffing and rely on `notifyDataSetChanged`). That
is backwards for `FragmentStateAdapter` and is not the task here.

*Sources: devin-A#9, kimi-B#3, devin-B#10, allhands-B#3/#5.*

---

### A20. `VoicesAdapter.removePost` rebuilds the whole list for a parent removal

`ui/voices/VoicesAdapter.kt:317`: removing the parent post calls `submitList(emptyList())`, blowing
away the entire list (full unbind/rebind) even though the DiffUtil callback exists to avoid exactly
that.

**Do:** submit the correctly-filtered new list once instead of the empty-then-refill pattern. Keep
the existing `PAYLOAD_REPLY_COUNT` payload for the reply-count update (line 341).

*Sources: devin-A#10.*

---

### A21. Add `flowOn` to `TeamsRepositoryImpl`'s mapping flows

`getMyTeamDetailsFlow()` (line 292), `getTasksFlow()` and `getMyTeamsFlow()` run non-trivial `.map { }`
transforms (filtering plus `mapToTeamDetails`) in the collector's context — typically the main thread.
The class injects dispatchers but has **zero** `flowOn` usages (verified).

**Do:** add `.flowOn(dispatcherProvider.default)` (or `io`) to these flows. One line per flow.

*Sources: devin-A#7.*

---

### A22. Guard `SyncManager` / `SyncTimeLogger` debug logging

`services/sync/SyncManager.kt` has 20 `Log.d("SyncPerf", ...)` calls (verified) that build formatted
strings — including the `SimpleDateFormat` work from A17 — unconditionally on every sync;
`utils/SyncTimeLogger.kt` adds roughly a dozen more.

**Do:** wrap in `if (BuildConfig.DEBUG)` or route through a single `debugLog` helper so release builds
skip the string building entirely.

*Sources: devin-A bonus 2.*

---

### A23. Convert `ThemeManager` from an `EntryPointAccessors` object to an injectable class

`services/ThemeManager.kt` uses `EntryPointAccessors` plus a manually cached `SharedPrefManager`, with
a test-only `clearSharedPrefManager()` hack.

**Do:** convert to `@Singleton class ThemeManager @Inject constructor(private val sharedPrefManager:
SharedPrefManager)` and inject at the use sites.

This is the smallest of the 7 `EntryPointAccessors` call sites (`MainApplication`, `ThemeManager`,
`ServiceDependenciesEntryPoint`, `NetworkUtils`, `SyncTimeLogger`, `DownloadUtils`,
`NotificationUtils`) and serves as the template for cleaning up the others (see A5, A6, B8).

*Sources: devin-A bonus 1.*

---

### A24. Move `ChatViewModel` search filtering off the main thread

`loadChatHistoryScreenData()` correctly runs `sortChats()` and `buildPrecomputedChats()` inside
`withContext(dispatcherProvider.default)`, but the heavy filtering in `searchChats()` still runs on
`viewModelScope` (main). `fullConvoSearch` / `searchByTitle` perform multiple `normalizeText()` calls
and list allocations on every keystroke.

**Do:** move both to `dispatcherProvider.default`.

*Sources: kimi-A#1.*

---

### A25. Stop reloading the whole user model on every screen setup

`BaseRecyclerFragment` and its subclasses call `userRepository.getUserModel()` /
`userSessionManager.getUserModel()` from several separate `lifecycleScope.launch` blocks per screen
creation, each hitting Room.

**Do:** read the `UserEntity` once in `onViewCreated` (or hold it in the ViewModel), cache it, and
pass it to adapter factories.
Files: `base/BaseRecyclerFragment.kt`, `ui/resources/ResourcesFragment.kt`,
`ui/courses/CoursesFragment.kt`, `ui/teams/TeamFragment.kt`.

*Sources: kimi-A#2.*

---

### A26. Move `lifecycleScope.launch { withContext(io) { … } }` work into ViewModels

Distinct from A3: A3 fixes *when* collection happens, this moves the *work* out of the fragment.
Mixing dispatchers in fragments risks leaked scopes, complicates testing, and duplicates work across
configuration changes.

Files: `ui/resources/ResourcesFragment.kt`, `ui/viewer/ResourceViewerFragment.kt`,
`ui/sync/SyncActivity.kt`, `ui/exam/ExamTakingFragment.kt`, `ui/components/MarkdownDialogFragment.kt`.

**Do:** move the IO-bound work into the relevant ViewModel (`ResourcesViewModel`,
`ResourceViewerViewModel`, `ExamTakingViewModel`), expose a `StateFlow`/`SharedFlow` or suspend
result, and let the fragment only collect. One vertical slice per PR.

*Sources: kimi-A#5.*

---

### A27. Stop re-creating adapters in `BaseRecyclerFragment`

`BaseRecyclerFragment.getAdapter()` builds a fresh adapter every time it is called (e.g. from
`onRatingChanged`, `postAddRefresh`), throwing away the `RecycledViewPool`, diff state and selection
state.

**Do:** keep a single adapter instance and call `submitList()` instead of assigning a new adapter.
While in the file, clear `recyclerView.adapter = null` and null out listeners in `onDestroyView`.
Files: `base/BaseRecyclerFragment.kt`, `base/BaseRecyclerParentFragment.kt`,
`ui/resources/ResourcesFragment.kt`, `ui/courses/CoursesFragment.kt`, `ui/teams/TeamFragment.kt`.

*Sources: kimi-A#6, kimi-B#10.*

---

### A28. Cancel stale coroutine jobs in long-lived fragments

Several fragments launch search/filter/refresh coroutines without holding the `Job` and cancelling
the previous invocation, so a slow older request can overwrite a newer result.

**Do:** add `private var searchJob: Job?` / `refreshJob: Job?` and cancel before launching the next.
Files: `ui/resources/ResourcesFragment.kt`, `ui/courses/CoursesFragment.kt`,
`ui/teams/TeamFragment.kt`, `ui/teams/tasks/TeamsTasksFragment.kt`, `ui/health/MyHealthFragment.kt`.

`ui/courses/CourseFilterController.kt` already does this correctly (`searchJob?.cancel()` at 97, 176)
— use it as the reference. See B6 for the separate scope-leak bug in that same file.

*Sources: kimi-A#7.*

---

### A29. Remove the extra scope hop in `RealtimeSyncMixin.refreshRecyclerView`

`RealtimeSyncHelper.setupRealtimeSync()` already uses the lifecycle-aware `collectWhenStarted`
extension, but `refreshRecyclerView()` wraps the adapter refresh in an additional
`viewLifecycleOwner.lifecycleScope.launch` for a one-shot operation that belongs inside the collector.

**Do:** perform the refresh in the collector, using the existing `flow` + `debounce` so work stays
coalesced. Files: `ui/sync/RealtimeSyncMixin.kt` and its implementing fragments.

*Sources: kimi-A#8.*

---

### A30. Remove adapter-side mutable working lists

Several adapters copy `currentList` into a mutable list, mutate it, then `submitList()` — doubling
memory churn on every update and keeping list state in the adapter.

**Do:** submit an immutable list built by the ViewModel/fragment, and delete the adapter-side working
list (`VoicesAdapter.originalList`, `LifeAdapter.workingList`, the `SurveysAdapter` mutable sorts).
Files: `ui/voices/VoicesFragment.kt`, `ui/chat/ChatHistoryAdapter.kt`, `ui/courses/CoursesAdapter.kt`,
`ui/surveys/SurveysAdapter.kt`, `ui/teams/tasks/TeamsTasksAdapter.kt`.

Overlaps A1 (`SurveysAdapter`), A12 (`CoursesAdapter`) and A20 (`VoicesAdapter`) — sequence after
those or scope this PR to the files they don't touch.

*Sources: kimi-A#10.*

---

### A31. Use payloads instead of full rebinds in `VoicesAdapter`

`VoicesAdapter` recomputes `RowState` and rebinds the entire row on every `submitList`, even when only
reply counts, leader status, or user images changed. Voices lists are long-lived and update
frequently.

**Do:** add `PAYLOAD_REPLY_COUNT` / `PAYLOAD_USER_FETCHED` / `PAYLOAD_TEAM_LEADER_CHANGED` handling in
`onBindViewHolder(holder, position, payloads)` and update only the affected views.
Files: `ui/voices/VoicesAdapter.kt`, `ui/voices/VoicesFragment.kt`.

**Conflicts with A20 and B7** (same file) — pick an order, do not run in parallel.

*Sources: kimi-A#4.*

---

### A32. Remove repeated survey-ID scans when grouping submissions

`SurveysRepositoryImpl.getSurveyInfos()` scans every survey ID once while filtering and again while
grouping each submission — avoidable CPU on a frequently rendered summary.

**Do:** resolve each completed submission's survey parent once, then group directly by the resolved
ID. Use an exact-ID set for the common case and a small explicit helper for the existing
`surveyId@...` format. Do not change accepted parent-ID semantics.

**Acceptance:** exact IDs, suffixed IDs, unrelated submissions, incomplete submissions and
overlapping-looking IDs produce the same counts as today, via a single grouping pipeline rather than
separate `any` and `find` scans. Touches `SurveysRepositoryImplTest.kt`.

*Sources: codex-A#3.*

---

### A33. Deduplicate course filter tags without quadratic `any` checks

`CourseFilterController.setTags()` checks the growing list with `any` for every input tag.

**Do:** deduplicate by tag name in one pass while preserving first-seen order — a local `HashSet`
alongside the existing list, or an insertion-ordered keyed collection. Keep the externally visible
list/order and state emissions unchanged; do **not** move the controller into a ViewModel here.

**Acceptance:** duplicate names removed, first occurrence wins, one filter-state update per bulk set,
`addTag` still ignores an existing name. Coordinate with B6 (same file).

*Sources: codex-A#4.*

---

### A34. Cancel stale username-validation work in `BecomeMemberActivity`

A new coroutine is launched for every username text change, so older validations keep running and can
overwrite a newer result out of order.

**Do:** keep one validation `Job`, cancel it before scheduling the next value, add a short debounce,
and avoid dispatching back to main when already in `lifecycleScope`. Capture the submitted text and
ignore the result if the field has changed since. Cancel and clear the job with the other watchers
during teardown.

**Acceptance:** rapid typing validates only the latest stable value; stale results cannot set an error
or lowercase newer input; blank/unchanged behavior preserved; no raw `Dispatchers.*` introduced.

*Sources: codex-A#5.*

---

### A35. Stop dashboard `StateFlow` collection while the view is below STARTED

In `BaseDashboardFragment.observeUiState()`, four `uiState` collectors live for the entire view
lifecycle while only the news collector is guarded by `repeatOnLifecycle(STARTED)`. A hidden dashboard
can still rebuild UI state.

**Do:** put all dashboard UI collectors under one `repeatOnLifecycle(Lifecycle.State.STARTED)` block,
retaining the existing child collectors, `map` and `distinctUntilChanged`. Do **not** redesign
`DashboardUiState` here.

**Acceptance:** collection starts at STARTED, stops below it, resumes with the latest value, and
exactly one collector per slice exists after repeated stop/start cycles. Touches
`BaseDashboardFragmentTest.kt`. Merge before A36 — same file, different method.

*Sources: codex-A#6.*

---

### A36. Reuse the health-user dialog adapter instead of rebuilding it per emission

The health-user dialog collector in `BaseDashboardFragment` creates a new `HealthUsersAdapter`,
`LinearLayoutManager` and adapter attachment every time `uiState` emits. `HealthUsersAdapter` is
already a `ListAdapter` using the shared `DiffUtils.itemCallback`, so recreating it discards all
diffing benefit.

**Do:** construct and attach the layout manager and adapter once when creating the dialog, then only
`submitList` and update visibility/loading in the collector. Keep the existing callback and selection
behavior, and keep cancelling the dialog-specific job on dismiss.

**Acceptance:** repeated emissions reuse the same adapter, clicking a user still opens the correct
library flow, dismissal leaves no collector running. Merge after A35.

*Sources: codex-A#7.*

---

### A37. Delete the dead `RealtimeSyncManager.getInstance()` singleton

`services/sync/RealtimeSyncManager.kt` carries a `@Volatile INSTANCE` + `getInstance()` companion
while `ServiceModule:121` also provides it as `@Singleton`. Verified: **no `main` code calls
`getInstance()`** — the mixin and consumers already use Hilt. So this is dead code that creates a
second, silent ownership path and forces tests onto global reset state.

**Do:** delete the companion object; give Hilt sole ownership (constructor injection or the existing
provider). Keep the same shared-flow capacity and non-suspending notification behavior. Update
`RealtimeSyncManagerTest.kt` to construct isolated instances without reflection/global reset. Do not
broaden into worker or service DI cleanup.

**Scoped down** from the original proposal, which assumed live `getInstance()` call sites.

*Sources: codex-A#8.*

---

### A38. Keep crash-log sweeping on one IO context

`MainApplication.sweepPendingLogs()` enters the IO dispatcher to load files, returns to its parent
dispatcher, then switches to IO again for each successful deletion — dispatcher hops inside a
potentially large loop.

**Do:** run load/save/delete inside one `withContext(dispatcherProvider.io)` boundary. Preserve
per-record ordering and the rule that a file is deleted only after its Room save succeeds. Do not
change app-start sequencing or introduce parallel writes.

**Acceptance:** failed saves retain their files, successful saves delete theirs, exception handling
unchanged, dispatcher-provider tests stay deterministic.

*Sources: codex-A#9.*

---

## Round B — layering and repository boundaries

### B1. Extract `DictionaryRepository` + `DictionaryViewModel`

Verified: `ui/dictionary/DictionaryActivity.kt` is the **only** file in `ui/` that injects a Room DAO
(`DictionaryDao` at :21/:37, used at :135/:147/:156). It also parses the dictionary JSON blob and
picks its own dispatchers (`withContext(dispatcherProvider.io)` at :109, :134, :146, :155).

**Do:**
1. New interface with exactly 3 methods, no speculative surface: `suspend fun count(): Long`,
   `suspend fun findByWord(word: String): DictionaryEntity?`,
   `suspend fun importFromFile(path: String): Int`.
2. Move the `JsonArray` → `DictionaryEntity` mapping (lines 104-143) and the
   `FileUtils.getStringFromFile` read into `importFromFile` in the implementation.
3. Move all four `withContext(dispatcherProvider.io)` blocks into the impl.
4. Thin `DictionaryViewModel` holding `resultText`/`count` state; the Activity renders and keeps the
   `BroadcastService` collector.
5. Remove `DictionaryDao` and `DispatcherProvider` from the Activity's `@Inject` fields, plus the now
   unused `DictionaryEntity` import.
6. Bind in `RepositoryModule`; `RoomModule` already provides the DAO.

**Done when:** no DAO, entity construction, `JsonArray` or dispatcher import remains in the Activity;
malformed JSON does not crash it; no unused repository method exists.
Tests: empty import, non-empty no-op, lookup, malformed input.

**Conflict risk:** none — nothing else touches `ui/dictionary/`.

*Sources: jules-B#2, allhands-B#1/#4/#9, claude-B#1, codex-B#1, devin-B#6.*

---

### B2. Move `UploadConfigs`' 8 DAOs behind repositories, one slice per round

Verified: `services/upload/UploadConfigs.kt` injects 11 repositories **and** 8 DAOs — `apkLogDao`,
`searchActivityDao`, `courseProgressDao`, `resourceActivityDao`, `submitPhotosDao`, `newsLogDao`,
`teamLogDao`, `teamTaskDao`. Largest remaining data leak in the service layer.

**This round — the two team-owned DAOs:**
1. Add to `TeamsSyncRepository`: `suspend fun getPendingTaskUploads(): List<TeamTask>`,
   `suspend fun markTaskUploaded(localId, remoteId, remoteRev): Boolean`, and the `TeamLog`
   equivalents.
2. Point the `TeamTask` / `TeamLog` upload configs' `fetchPendingItems` and `markUploaded` lambdas at
   the repository.
3. Delete the two DAO params and imports.

**Follow-up rounds (do not bundle):** `newsLogDao` → `VoicesRepository`, `courseProgressDao` →
`ProgressRepository`, then `searchActivityDao` / `resourceActivityDao` → `ActivitiesRepository`,
`submitPhotosDao`, `apkLogDao`.

*Sources: allhands-B#2/#7, claude-B#2, devin-B#7, kimi-B#9.*

---

### B3. Delete dead repository interface surface

Candidates with no callers outside their own `*RepositoryImpl` and no test references:

| Repository | Method |
|---|---|
| `CoursesRepository` | `getCourseExamCount` |
| `CoursesRepository` | `getCourseOnlineResources` |
| `EventsRepository` | `getMeetupIdsForUser` |
| `FeedbackRepository` | `insertFromJson` |
| `LifeRepository` | `getVisibleMyLifeByUserId` |
| `NotificationsRepository` | `getTeamNotificationInfo` |
| `ResourcesRepository` | `getCourseResourcesGroupedByStepId` |
| `TagsRepository` | `getTagsForCourse` |
| `TeamsRepository` | `attachTeamImage` |
| `TeamsRepository` | `getResourceIds` |
| `UserRepository` | `getMonthlyLoginCounts` |
| `UserRepository` | `isUserExists` |
| `VoicesRepository` | `insertNewsFromJson` |

**Re-verify each one before deleting** — a spot check found `getTagsForCourse` with 5 references
across main and test, so at least one entry in the original list is not actually dead. Delete only
what a fresh `grep -rn <name> app/src` proves unused, plus any DAO query that becomes orphaned.

**Do:** deletions only, no behaviour change. Run `./gradlew testDefaultDebugUnitTest`.
**Merge this first in the round** so later PRs rebase cleanly.

*Sources: claude-B#3.*

---

### B4. Remove the two dead repository injections in UI

Verified — each name appears exactly once in its file, i.e. the declaration and nothing else:

- `ui/sync/SyncActivity.kt:128` — `communityRepository: CommunityRepository`
- `ui/resources/AddResourceActivity.kt:38` — `resourcesRepository` is used; the dead one is
  `teamsRepository: TeamsRepository`

**Do:** delete the two fields and their now-unused imports.
**Do not** touch `DashboardActivity:115 resourcesRepository` — it looks unused but overrides
`SyncActivity`'s `open` property and is required.

*Sources: claude-B#4, jules-A#5, qwen-A#9.*

---

### B5. Drop redundant `withContext(io)` in `DashboardViewModel`

Verified: 11 `withContext(dispatcherProvider.io)` wrappers. Every repository method is `suspend` and
already confines itself (Room dispatches on its own executor; impls use `dispatcherProvider.io` for
network), so these are no-op context switches that also hide where threading really lives.

**Do:** remove the wrapper around each *repository* call, keeping the call in `viewModelScope.launch`.
**Keep** the `.flowOn(dispatcherProvider.io)` on the two `Flow` collectors (courses, teams) — those
are correct. Drop the constructor param if it ends up unused.
Do not extend to other ViewModels this round.

*Sources: claude-B#5, allhands-B#6 (as a real change rather than a doc comment).*

---

### B6. Fix the leaked `CoroutineScope` in `CourseFilterController`

Verified: `ui/courses/CourseFilterController.kt:51` creates `CoroutineScope(dispatcherProvider.main)`
that is **never cancelled**, so the search-debounce `Job` outlives `CoursesFragment`'s view.
`InlineResourceAdapter` does this correctly (`SupervisorJob()` + `cancel()` in
`onDetachedFromRecyclerView`).

**Do:** `CoroutineScope(SupervisorJob() + dispatcherProvider.main)`, add `fun clear() {
coroutineScope.cancel() }`, call it from `CoursesFragment.onDestroyView()` (controller constructed at
`CoursesFragment.kt:183`). Coordinate with A33 (same file).

*Sources: claude-B#6.*

---

### B7. Make `VoicesAdapter` presentation-only

`VoicesAdapter` (958 lines, 2nd-largest UI file) receives `VoicesRepository` and `UserRepository`,
deserializes persisted CouchDB JSON (`parseViewIn` at 526, `parseImageUrls` at 546,
`getParsedImageUrls` at 788, raw `asJsonObject` indexing at 819/825), calls
`userRepository.parseLeadersJson`, and forwards repositories into bind/click helpers.
`ui/voices/VoicesFragment.kt:57` injects both repositories solely to construct the adapter.

**Do:**
1. Add `fun parseNewsImages(news: News): List<NewsImage>` and
   `fun parseViewIn(news: News): List<ViewInTarget>` to `VoicesRepository` (non-suspend pure mapping
   is fine — the point is that Gson stays out of `ui/`), plus one small DTO in `model/`.
2. Map leader flag/display data and action eligibility once in `VoicesViewModel` into a small
   immutable row model.
3. Replace the adapter's repository parameters with row data and ID-carrying action callbacks.
4. Keep `ListAdapter` + `DiffUtils.itemCallback` with a stable identity comparing all rendered
   content; keep the existing `AdapterDataObserver` unregistration in `onDestroyView`.
5. Delete the three private parse helpers and the `com.google.gson.*` imports from the adapter.

**Do not** also touch `VoicesActions.kt` in this PR. Conflicts with A20/A31 — sequence them.

*Sources: claude-B#7, codex-B#4.*

---

### B8. Break the `DownloadUtils` → repository `EntryPoint` back-channel

`utils/DownloadUtils.kt:252-267` is a stateless `object` that reaches into Hilt with
`EntryPointAccessors` + `by lazy` to grab `ResourcesRepository` and call
`markResourceOfflineByUrl(url)`. A util object silently owning a data-layer dependency is untestable.

**Do:** find the callers of `DownloadUtils.updateResourceOfflineStatus`, inject `ResourcesRepository`
into the calling service (`services/DownloadService.kt` or `services/ResourceDownloadCoordinator.kt`,
both already Hilt-managed) and call `markResourceOfflineByUrl` there in its own scope. Delete the util
function, the lazy entry-point block and the repository import.

Same family as A5, A6, A23. Sequence before B10 (shares `ResourcesRepository`).

*Sources: claude-B#8.*

---

### B9. Stop `ChatHistoryFragment` writing Voices data directly

Verified: `ui/chat/ChatHistoryFragment.kt:58` injects `VoicesRepository` and runs the share-to-voices
write inline (`isAlreadyShared` at 182, `createNews` at 186) in a `lifecycleScope.launch` — a chat
Fragment that understands Voices persistence.

**Do:**
1. Add `shareChatToVoices(chatId, viewInId, payload)` to `ChatViewModel`, returning a sealed result
   (`AlreadyShared` / `Shared(news)`) on a `SharedFlow`.
2. Compose the existing data operations below the UI layer; avoid exposing `News` unless Chat needs it
   to refresh cached presentation data.
3. Fragment collects the result and does only Snackbar + adapter update.
4. Remove `@Inject lateinit var voicesRepository` from the Fragment.

`ChatViewModel` legitimately keeps its `VoicesRepository`/`TeamsRepository` use (lines 117, 236-244) —
a ViewModel composing domains is fine; the *Fragment* doing it is the leak. Update `ChatViewModelTest`
for both the duplicate and created paths.

*Sources: claude-B#9, codex-B#3.*

---

### B10. Move storage scanning and deletion out of `StorageCategoryDetailFragment`

`buildResourceItems()` (line 168) walks `File(FileUtils.getOlePath(...))` and joins it against
`resourcesRepository.getResourceTitlesMap()`; `deleteItems()` (220) deletes files then calls
`markResourcesAsNotOffline`. Both run under manual `withContext(dispatcherProvider.io)` in the
Fragment (`resourcesRepository` injected at :37).

**Do:** add `suspend fun getOfflineResourceItems(oleDirPath: String): List<ResourceItem>` and
`suspend fun deleteOfflineResources(items: List<ResourceItem>)` to `ResourcesRepository` — pass the
path in, do not leak `Context` into the repository interface. The fragment calls the two methods and
drops its `withContext` blocks and `File` import.

**Out of scope:** `StorageBreakdownFragment.scanStorage()` (line 222) has the same smell — follow-up
PR so the two don't conflict. Sequence after B8.

*Sources: claude-B#10, jules-B#5/#7 (partly).*

---

### B11. Add ViewModels for three tiny repository-injecting Fragments

The cheapest ViewModel-layer expansion available — the smallest fragments still holding a repository
field and doing data work in `lifecycleScope`. Roughly a 40-line diff each.

- `ui/community/LeadersFragment.kt` (48 lines, `userRepository` at :22, calls
  `userRepository.parseLeadersJson(leaders)` at :36) → `LeadersViewModel` over `UserRepository`
- `ui/life/LifeFragment.kt` (115 lines) → `LifeViewModel` over `LifeRepository`
- `ui/surveys/SendSurveyFragment.kt` (69 lines) → fold into the existing `SurveysViewModel`
  (`createBulkSurveySubmissions` at :54) rather than adding a class

**Do:** move the repository field and the `lifecycleScope.launch` body into the ViewModel, expose a
`StateFlow` of the rendered state, collect with `collectWhenStarted`.
Three disjoint packages — split into three PRs if the round has room.

*Sources: claude-B#11, devin-B#9.*

---

### B12. Remove cross-repository passthroughs on `CoursesRepository`

`CoursesRepositoryImpl` injects `ProgressRepository` and `RatingsRepository` purely to re-export their
methods verbatim: `getCourseProgress(userId, courseIds)` (line 488) is
`progressRepository.getCourseProgress(...)`, and `getCourseRatings(userId)` (line 504) is
`ratingsRepository.getCourseRatings(...)`. Two interfaces claim the same data and callers can't tell
which is canonical.

**Do:** inject `ProgressRepository` / `RatingsRepository` into `ui/courses/CoursesViewModel.kt`
(lines 75, 77, 100) and `ui/courses/TakeCourseViewModel.kt` (line 61) — the real owners — and delete
both methods from the interface and impl.
**Keep** the genuinely composing uses: `getCourseProgress(courseId, userId)` at 356 builds
`CourseProgressData`, and `ratingsRepository.getRatingSummary` at 135 enriches a course payload.

Shares `ui/courses/` with A33/B6 but different files — safe in parallel.

*Sources: claude-B#12.*

---

### B13. Delete the `AuthUtils.validateUsername` wrapper

Verified: `utils/AuthUtils.kt:13-18` is a function whose entire body is
`userRepository.validateUsername(username)`. Two of the four call sites
(`BecomeMemberActivity:175, :226`) already call the repository directly, so there are two routes to
the same thing.

**Do:** delete it and inline at `ui/sync/GuestLoginExtensions.kt:40, :59`. Leave `AuthUtils.login`
alone — it does real orchestration. Perfect warm-up PR.

*Sources: claude-B#13.*

---

### B14. Remove foreign-DAO leaks between repositories

Verified from the constructors — repositories reaching directly into another domain's table:

| Repository | Foreign DAO | Use |
|---|---|---|
| `HealthRepositoryImpl` | `UserDao` | `getById` (~:32), `upsert` (~:66) for the health user key |
| `SurveysRepositoryImpl` | `TeamDao` | team name lookup only (~:82) |
| `SubmissionsRepositoryImpl` | `UserDao` | `getById` in two serialization paths (~:241, ~:685) |
| `ResourcesRepositoryImpl` | `TeamDao` | writes a `MyTeam` "resourceLink" row in `markResourceUploaded` (~:636) |
| `TeamsRepositoryImpl` | `CourseDao`, `CourseStepDao`, `MyLibraryDao` | upload serialization (`getTeamsForUpload` ~:80, `getCoursesForSerialization` ~:1368) |

**Do:** one PR per row.
- Health / Submissions → route through `UserRepository` (add a narrow method only if one doesn't
  already exist), drop the `UserDao` param.
- Surveys → a `getTeamName(teamId)`-style call on `TeamsRepository`.
- Resources → add `TeamsRepository.createResourceLink(teamId, title, resourceId, planetCode)`;
  Resources should never write to the teams table.
- Teams → move `getCoursesForSerialization(courseIds)` into `CoursesRepository` (e.g.
  `getCoursesWithSteps`), expose the `myLibraryDao.getByCourseIds(...)` grouping via
  `ResourcesRepository`, then drop all three foreign DAOs. This also trims the file that most needs
  splitting.

**Ordering:** the Surveys and Resources PRs both add to `TeamsRepository.kt` — land them in sequence.

*Sources: devin-B#1/#2/#3/#4/#5.*

---

### B15. Route Fragment repository calls through their ViewModels

`ui/teams/TeamFragment.kt:43` injects `TeamsRepository` even though `TeamViewModel` exists, calling
`isTeamNameExists(...)` (:195) and `updateTeam(...)` (:213) straight from the Fragment.

**Do (this round, `TeamFragment` only):** move both calls into `TeamViewModel`, which already injects
`TeamsRepository`, and delete the Fragment's repository field.

**Same pattern, later rounds — one PR each:** `ui/teams/voices/TeamsVoicesFragment.kt:43`
(`voicesRepository`), `ui/settings/StorageCategoryDetailFragment.kt:37` (covered by B10),
`ui/exam/ExamTakingFragment.kt:65,67`, `ui/exam/UserInformationFragment.kt:42,44`,
`ui/courses/CourseStepFragment.kt:48,50`, `ui/courses/TakeCourseFragment.kt:44`,
`ui/feedback/FeedbackFragment.kt:27`, `ui/resources/CollectionsFragment.kt:34`,
`ui/components/MarkdownDialogFragment.kt:34`, `ui/voices/ReplyActivity.kt:63,68,72`.

*Sources: devin-B#8, jules-B#4.*

---

### B16. Route "upload personal" through `PersonalsViewModel` / `PersonalsRepository`

`PersonalsFragment` injects `UploadManager` and runs `uploadMyPersonal` directly, while
`PersonalsRepository` already owns pending-personal queries (`getPendingPersonalUploads`),
`uploadPersonalDocument` and `updatePersonalAfterSync`. One workflow split across UI, service and
repository.

**Do:** expose one behavior-level repository operation for uploading a `Personal` (reusing the
existing primitives, or moving only `uploadMyPersonal` out of the service); add a ViewModel command
plus a small success/error/in-progress state; remove the `UploadManager` injection and the
fragment-owned coroutine, keeping dialog rendering in the fragment.

**Done when:** the fragment depends only on its ViewModel for upload, upload bookkeeping stays atomic,
and `UploadManager` has no personal-specific entry point left. Do not consolidate the rest of
`UploadManager` here. Extend `PersonalsRepositoryImplTest`.

*Sources: codex-B#2, kimi-B#9 (partly).*

---

### B17. Remove `UserSessionManager` from presentation code

`EventsDetailViewModel` reads the active user from a service even though all meetup reads and
attendance changes go through `EventsRepository`, whose `toggleAttendance(meetupId, currentUserId)`
forces every caller to resolve session identity.

**Do (this round):** add `toggleCurrentUserAttendance(meetupId)` (or equivalent) plus a narrowly named
active-user read; keep explicit-user methods only if a real caller needs them; remove
`UserSessionManager` from `ui/events/EventsDetailViewModel.kt`, retaining its state flows and refresh
behavior. Extend `EventsRepositoryImplTest` for the missing-active-user and successful-toggle cases.
Do not touch calendar/team event creation.

**Scope note:** `UserSessionManager` appears in 32 UI files. This is a per-file campaign, not one PR.
Follow-ups: `ui/courses/TakeCourseFragment.kt`, `ui/dashboard/ActivitiesFragment.kt`,
`ui/community/CommunityTabFragment.kt`, `ui/teams/TeamDetailFragment.kt`. Uses *inside* ViewModels
(`UserProfileViewModel`, `SurveysViewModel`, `PersonalsViewModel`, …) are lower priority than uses
inside Fragments/Activities.

*Sources: codex-B#5, jules-B#4, jules-B#9.*

---

### B18. Move My Health user lookups, search and sort behind the health boundary

`ui/health/MyHealthFragment.kt:57` injects `UserRepository` and calls `getUserByAnyId`,
`getUsersSortedBy`, `searchUsers` and `getHealthRecordsAndAssociatedUsers` from the view lifecycle.
These are health-provider use cases leaking another feature's broad repository into a Fragment. The
fragment also injects `SharedPrefManager` directly.

**Do:** add narrowly named patient lookup/search/sort methods to `HealthRepository`, implemented over
its own data sources rather than exposing `UserRepository` to UI; move the fragment coroutines into
`HealthViewModel` and expose patient/list/record/loading/error state; keep navigation and dialog
construction in the Fragment and collect with the view lifecycle. Keep `HealthUsersAdapter` as-is —
it already uses `ListAdapter` + `DiffUtils.itemCallback`.

**Done when:** the fragment injects no repository/session/prefs for data reads and rapid searches
leave no stale results. Leave examination editing/encryption untouched.
Overlaps A28 (job cancellation in this file) — fold or sequence.

*Sources: codex-B#6, jules-B#3, jules-B#6.*

---

### B19. Replace `SettingsViewModel`'s service dependencies with repository boundaries

`ui/settings/SettingsViewModel.kt` coordinates `RetryQueue`, `ResourceDownloadCoordinator`,
`SharedPrefManager` and two repositories: `isCurrentlyProcessing`, `clearRetryQueue`,
`getRetryQueueSnapshot`, `downloadFiles`. The retry snapshot is assembled from three service calls in
presentation code.

**Do:** put snapshot/clear/processing behind `RetryRepository`, returning a domain snapshot rather
than an assembled triple; put "download these / all pending libraries" behind the existing
download/resources boundary, keeping notification and service mechanics below it; remove direct
retry/download service injection. Defer preference clearing — that is a separate session/data-reset
transaction.

**Done when:** the ViewModel cannot call `RetryQueue` or `ResourceDownloadCoordinator`, and each user
click maps to one repository call. Do not refactor the full Settings screen.

*Sources: codex-B#7.*

---

### B20. Hide viewer auth/server mechanics behind one use case

`ui/viewer/ResourceViewerViewModel.kt` exposes an `AuthSessionUpdater` factory/callback to its
Fragment (`getAuthSessionUpdater()`) and directly coordinates `ServerUrlMapper`, raw
`SharedPrefManager`, reachability and server mutation (`ensureServerUrlUpdated()`).
`ui/viewer/ResourceViewerFragment.kt` implements `AuthSessionUpdater.AuthCallback` — UI implementing a
data-layer callback interface.

**Do:** introduce one callable viewer-session boundary that ensures the reachable server and performs
the auth refresh, returning a small success/failure result; the ViewModel exposes that state, the
Fragment renders it. Preserve receiver registration/unregistration and media lifecycle. Do **not**
push this into `ResourcesRepository` — it is not resource persistence.

Also remove the ViewModel's injected `DispatcherProvider` in the same PR if it is unused rather than
keeping it for hypothetical work. Unit-test URL-alternative, direct URL, auth success and failure.

*Sources: codex-B#8, jules-B#8.*

---

### B21. Move dashboard-triggered key sync out of `BaseDashboardFragment`

The fragment injects `TransactionSyncManager` and calls `syncDashboardKeyId(...)` — a UI base class
starting a table sync against a large sync implementation.

**Do:** add one dashboard command on `DashboardViewModel` that takes only the role/input the sync
needs and owns its coroutine and error result; inject a narrow sync-facing interface (`SyncRepository`
or a tiny compatible abstraction), not the concrete manager; remove the sync-manager injection from
the Fragment; collect a one-shot result only if the UI displays one.

**Done when:** the fragment imports no sync implementation and a lifecycle-safe owner prevents
overlapping calls (add a ViewModel test for repeated view recreation). Do not refactor dashboard's
other nine repositories. Sequence with A35/A36 — same file.

*Sources: codex-B#9.*

---

### B22. Replace `AchievementViewModel`'s raw realtime listener with repository state

`ui/user/AchievementViewModel.kt` depends directly on `RealtimeSyncManager`, exposing generic sync
table events to a user-feature ViewModel.

**Do:** add a `UserRepository` flow representing achievement changes (or the achievement list itself),
filtering generic `TableDataUpdate` events inside the implementation; use `stateIn`/`shareIn` with
`WhileSubscribed` in the ViewModel instead of a forever-running `init` collector; remove the concrete
manager. Collect in `AchievementFragment` through `collectWhenStarted`.

**Done when:** user UI knows no sync table names, collection is lifecycle-bound, and no listener
survives teardown. Sequence after A37.

*Sources: codex-B#10.*

---

### B23. Move `RealtimeSyncMixin` table-update fan-out into repositories

`RealtimeSyncMixin` calls `refreshWithDiff()` on UI adapters after receiving `TableDataUpdate` events,
and the refresh logic reaches across features to decide which repository to query.

**Do:** add `observeTableUpdates(tableNames: List<String>): Flow<TableDataUpdate>` to the relevant
repositories — start with `TeamsRepository` and `ResourcesRepository` — so fragments consume a
repository-managed `Flow`. Only repositories know how to map a table update to fresh domain data.

Same theme as B22; do them in sequence, and after A37/A29.

*Sources: kimi-B#1.*

---

### B24. Route `SyncManager`'s server pulls through `SyncRepository`

`SyncManager` holds `ApiInterface` directly and orchestrates the pulls (`processShelfParallel`,
`processShelfDataOptimizedSync`).

**Do:** expand the existing `SyncRepository` interface with the shelf/pull operations `SyncManager`
needs, implement in `TeamsRepositoryImpl` or a dedicated `SyncRepositoryImpl`, and inject
`SyncRepository` into `SyncManager` so it coordinates state only.
Files: `SyncRepository.kt`, `services/sync/SyncManager.kt`, `di/ServiceModule.kt`.

Large; land after B26 (which touches the same `ServiceModule` provider).

*Sources: kimi-B#2.*

---

### B25. Move direct `SharedPreferences` / `SharedPrefManager` reads out of UI

Verified: 20 UI files read `SharedPrefManager` directly for userId, server config and filters,
exposing persistence keys to the UI.

**Do:** add small typed repository getters (`UserRepository.getCurrentUserId()`,
`ConfigurationsRepository.getCurrentServer()`, …) and inject those. Start with the highest-leak
files: `ui/sync/LoginActivity.kt`, `ui/teams/TeamFragment.kt`, `ui/resources/ResourcesFragment.kt`,
`ui/community/CommunityTabFragment.kt`, `ui/settings/SettingsActivity.kt`. One file per PR.

`ui/enterprises/EnterprisesReportsAdapter.kt` and `ui/community/CommunityPagerAdapter.kt` reading
prefs from an *adapter* are the worst offenders — prioritise those.

*Sources: kimi-B#5, jules-B#3.*

---

### B26. Shrink `ServiceModule` constructor-injection bloat

`di/ServiceModule.kt` manually constructs `SyncManager` (`provideSyncManager`, :70) and
`UploadManager` (`provideUploadManager`, :92) with 15+ dependencies each, mirroring the class
constructors exactly.

**Do:** add `@Inject` constructors to `SyncManager` and `UploadManager` and delete the two `provide*`
methods. `provideRealtimeSyncManager` (:121) can also become constructor injection once A37 removes
the `getInstance()` companion.

*Sources: kimi-B#7.*

---

### B27. Move `ProcessUserDataActivity` / `GuestLoginExtensions` DB touches into `UserRepository`

Both still touch `DatabaseService`/`AppDatabase` directly from the UI layer.

**Do:** add `UserRepository.processGuestLogin()` and `UserRepository.processUserData(...)`, move the
direct DB calls there, and have the UI call the repository method inside a `lifecycleScope.launch`.
This removes the last explicit database access from UI. Files: `ui/sync/ProcessUserDataActivity.kt`,
`ui/sync/GuestLoginExtensions.kt`, `UserRepository.kt`/`UserRepositoryImpl.kt`, `UserSyncRepository.kt`.

Overlaps B13 (`GuestLoginExtensions`) — land B13 first, it is 6 lines.

*Sources: kimi-B#8.*

---

### B28. Rename `shouldQueryTeamFromRealm()`

Verified: `base/BaseTeamFragment.kt:85` declares it, `:55` uses it, and
`ui/teams/voices/TeamsVoicesFragment.kt:91` overrides/calls it. Realm was removed from the project;
the name leaks a dead data-layer implementation detail into the UI.

**Do:** rename to `shouldQueryTeamLocally()` or `shouldQueryTeamFromDatabase()`. Three call sites.
Pair it with a sweep of the `Realm`-referencing KDoc in `model/` (`News.kt:21` explicitly flags a
later rename pass) if the reviewer wants one PR for the whole terminology cleanup.

*Sources: jules-B#1.*

---

## Rejected proposals

Each of these was proposed by at least one agent and dropped. Reason given so the decision can be
re-litigated with evidence rather than re-derived.

**Stale — the codebase already changed:**

| Proposal | Reason |
|---|---|
| "Migrate legacy `RecyclerView.Adapter`s to `ListAdapter`" (jules-A#1, kimi-A#3, qwen-A#1, kimi-B#4, qwen-B#3) | `grep -rn "RecyclerView.Adapter<"` returns **zero** hits in `main`. Every adapter is already a `ListAdapter`. The named files (`ServerAddressAdapter`, `DashboardSurveysAdapter`, `OnboardingAdapter`, `PersonalsAdapter`, `ChatHistoryAdapter`, `ChatShareTargetAdapter`, `SurveysAdapter`) all already use it. The *real* remaining defect is A1 (broken `areContentsTheSame`), which those lists missed. |
| "Inject `DispatcherProvider` instead of hardcoded `Dispatchers.IO/Main/Default`" (jules-A#2, qwen-A#4, kimi-B#6, qwen-B#4) | `grep -rn "Dispatchers\.\(IO\|Main\|Default\|Unconfined\)"` outside `DispatcherModule`/`DispatcherProvider` returns **zero** hits. Already done project-wide. |
| "Replace `Thread.sleep()` / `while(true)` with `delay()`" (qwen-A#8) | One `Thread.sleep` in `main`, at `data/api/RetryInterceptor.kt:88`. OkHttp interceptors are a blocking API by contract — `delay()` is not usable there. `DownloadService`/`DownloadWorker` contain none. No `while(true)` loops. |
| "Audit `observeForever` / missing observer removal" (qwen-B#8) | Zero `observeForever` occurrences in `main`. |
| "Remove database primitives (`mRealm`, `RealmDatabase`, `AppDatabase`) from repository method signatures" (qwen-B#6) | Realm is gone; no repository interface method takes a database handle. |
| `RealtimeSyncManager.getInstance()` call sites in `RealtimeSyncMixin` (codex-A#8 as written, jules-A#9) | No `main` code calls `getInstance()`; consumers already use Hilt. Kept as A37, scoped down to deleting the dead companion. |

**Not actionable as written:**

| Proposal | Reason |
|---|---|
| The entire flow-`combine()` consolidation round (allhands-A, all 10 tasks) | Four of the ten are explicitly non-committal ("Consider combining if these affect the same UI elements"). The stated benefit — coroutine overhead — is negligible next to what `combine()` costs in behavior: it conflates independent state slices into one emission, so an update to any field re-renders all of them and per-slice `distinctUntilChanged` is lost. That is a behavior change presented as a perf win. The genuine defect in those same files (unsafe/unscoped collection) is covered by A3 and A4. |
| jules-A#3 (`COLLATE NOCASE` / `LIKE` escaping), #4 (N+1 chunked loops), #6 (atomic cache-state wrappers), #8 (factory methods in `companion object`) | No file or line anchors, and no matching pattern found by search. Real if a concrete site is produced — refile with one. |
| qwen-A#5 (reduce `lateinit var`), #6 (consolidate "identical" DiffUtil callbacks), #7 (verify payload overrides exist) | Phrased as verification, not change ("verify `onBindViewHolder(holder, position, payloads)` exists", "identify identical callbacks"). A1 and A31 cover the real defects in that area. |
| qwen-B#1, qwen-B#5 | Deliverable is a spreadsheet / audit document, not a change. The findings they would produce are already in this file. |
| qwen-B#2, #10 ("select ONE function and move it"), qwen-B#3, allhands-B#10, allhands-B#6 | Placeholder tasks with no target chosen, or no-ops ("document that the existing pattern is correct"). B1/B2/B14/B16 are the concrete versions. |
| allhands-B#4 ("add `provideDictionaryDao` if not already present"), allhands-B#9 ("verify imports after task 1") | Conditional no-ops; `RoomModule` already provides the DAO, and the import cleanup is part of B1. |

**Rejected on the merits:**

| Proposal | Reason |
|---|---|
| jules-A#7 — "remove custom diffing from `FragmentStateAdapter`s and let native `notifyDataSetChanged()` handle it" | Backwards. `notifyDataSetChanged()` on a `FragmentStateAdapter` destroys and recreates every page fragment; stable IDs do not prevent that. A19 is the inverse and is the task to do. |
| qwen-A#2 — `ArrayList<T>()` → `mutableListOf<T>()` | Cosmetic, and the stated rationale is false: `mutableListOf()` returns a `java.util.ArrayList`. Identical bytecode, no "Java interop overhead", no null-safety change. |
| allhands-B#8 — drop the `Lazy<TeamsSyncRepository>` wrapper in `UploadConfigs` | `Lazy` around a `@Singleton` in a class that also injects 11 other repositories is the standard way to break a Dagger dependency cycle. Removing it risks reintroducing one for no gain. If someone wants this, prove there is no cycle first. |
| qwen-A#3 (broad "replace unsafe casts") | Only one such cast exists in `main` (`ChatHistoryAdapter.kt:138`); `UploadRepositoryImpl` has none. Folded into A18 rather than kept as its own sweep. |

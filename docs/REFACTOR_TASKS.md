# Refactor Tasks — Round Plan (10 PRs, conflict-minimised)

Scope rules for this round: low-hanging fruit only, one concern per PR, no
unused scaffolding, each PR reviewable in ~15 min. All tasks reinforce
repository boundaries — moving data work out of UI/services into repositories,
tightening interfaces, and killing cross-feature leaks. Files touched by
different tasks were chosen to overlap as little as possible to avoid merge
conflicts during the ~10-PR review round.

Legend: **Touches** = primary files in the PR (keep these disjoint).

---

## Task 1 — Remove `VoicesRepository` / `UserRepository` from `VoicesAdapter`

**Why:** Adapters must be pure view logic. `VoicesAdapter` currently takes
`voicesRepository` and `userRepository` as constructor params
(`ui/voices/VoicesAdapter.kt:65-66`) and calls `userRepository.parseLeadersJson`
at line 173 plus repository calls at 485/745. This is the clearest
cross-layer leak in the UI package.

**Do:**
- Trace each repository call inside the adapter (lines ~173, ~485, ~745) and
  replace it with a lambda in the existing `*Fn` constructor style
  (the adapter already receives `getUserFn`, `getReplyCountFn`, etc.).
- Move the `parseLeadersJson` call up into `VoicesFragment` / `ReplyActivity`
  (both already inject the repositories) or into `NewsViewModel`, and pass the
  parsed list in.
- Delete the two repository constructor params and update the three call sites
  (`VoicesFragment:273`, `ReplyActivity:157`, `TeamsVoicesFragment:254`).

**Touches:** `ui/voices/VoicesAdapter.kt`, `ui/voices/VoicesFragment.kt`,
`ui/voices/ReplyActivity.kt`, `ui/teams/voices/TeamsVoicesFragment.kt`.

---

## Task 2 — Give `DictionaryActivity` a real repository boundary

**Why:** `ui/dictionary/DictionaryActivity.kt` injects `DictionaryDao`
directly (line 35), hand-parses a JSON file into `DictionaryEntity` objects
(96-129), and runs the count/search queries itself. It is the only UI class
still holding a DAO — the one remaining Room escape hatch CLAUDE.md warns
about.

**Do:**
- Create `DictionaryRepository` + `DictionaryRepositoryImpl` with three
  suspend functions mirroring exactly what the activity does today:
  `count()`, `findByWord(word)`, `importIfEmpty(jsonArray → entities)`.
  Inject `DictionaryDao` + `DispatcherProvider`; the `withContext(io)` calls
  move into the impl.
- Bind it in `di/RepositoryModule.kt`.
- The activity keeps only: file reading (`FileUtils.getStringFromFile`),
  Gson parsing of the raw file, and calling `repository.importIfEmpty`.

**Touches:** new `repository/DictionaryRepository{,Impl}.kt`,
`ui/dictionary/DictionaryActivity.kt`, `di/RepositoryModule.kt` (one line).

---

## Task 3 — Split `TeamsRepository` interface along its existing seams

**Why:** `TeamsRepository` has 66 functions (`repository/TeamsRepository.kt`)
and its impl is the largest file in the app (~1437 lines). ViewModels that
need one narrow slice (`EnterprisesViewModel`, `EnterprisesFinancesViewModel`,
`TeamCalendarViewModel` via events) are coupled to the whole surface. This
blocks the "Expand ViewModel layer" roadmap item.

**Do (interface-only split — do NOT touch `TeamsRepositoryImpl`):**
- Extract 2-3 narrow interfaces out of `TeamsRepository.kt` into their own
  files, matching call-site clusters, e.g. `TeamsFinancesRepository`
  (finances/reports functions used by `enterprises/`) and
  `TeamsMembershipRepository` (members/requests used by `teams/members/`).
- `TeamsRepository` then extends them:
  `interface TeamsRepository : TeamsFinancesRepository, TeamsMembershipRepository { ... }`
  so the impl needs zero changes.
- Migrate the 2-3 narrowest ViewModels (`EnterprisesViewModel`,
  `EnterprisesFinancesViewModel`, `RequestsViewModel`) to depend on the narrow
  interface only.

**Touches:** `repository/TeamsRepository.kt` + 2 new interface files,
`ui/enterprises/*ViewModel.kt`, `ui/teams/members/RequestsViewModel.kt`.

---

## Task 4 — Move sync-only `*FromSync` JsonObject methods off the public repository interfaces

**Why:** Interfaces like `ActivitiesRepository`, `ChatRepository`,
`CommunityRepository`, `EventsRepository`, `ProgressRepository`,
`NotificationsRepository` expose `insertXFromSync(docs: List<JsonObject>)` —
a CouchDB-wire type — purely for `TransactionSyncManager`. UI code can see and
call sync plumbing, and `gson.JsonObject` leaks the wire format across the
boundary.

**Do:**
- Create one `repository/sync/` interface file (e.g. `SyncInsertRepository`
  split per domain, or narrow per-domain `*SyncSupport` interfaces — pick the
  smallest that compiles) holding only the `*FromSync(docs)` functions.
- Have the domain interfaces drop those methods; the *impls* keep them and
  implement the new sync interface (`XRepositoryImpl : XRepository,
  XSyncSupport`).
- `TransactionSyncManager` switches its dependency to the sync-facing
  interface (it already works this way for `TeamsSyncRepository` /
  `UserSyncRepository` — copy that pattern).
- Keep this to the 3 interfaces with the smallest diff (start with
  `EventsRepository`, `FeedbackRepository`, `ChatRepository`).

**Touches:** 3 `repository/*Repository.kt` interfaces + their Impl class
declarations, `services/sync/TransactionSyncManager.kt`, `di/RepositoryModule.kt`.

---

## Task 5 — De-`JsonObject` one repository interface used by the dashboard

**Why:** `ProgressRepository.getCourseProgress(...): HashMap<String?, JsonObject>`
and `findProgressForCourse(courseData: JsonArray, ...)` return raw Gson to UI
(`CoursesAdapter` then calls `JsonUtils.getInt("current", progress)`). The
adapter renders domain state; it shouldn't parse CouchDB JSON.

**Do:**
- Introduce a small model in `model/`, e.g.
  `data class CourseProgress(val current: Int, val max: Int)`.
- Add `getCourseProgressMap(courseIds, userId): Map<String, CourseProgress>`
  to `ProgressRepository`, doing the `JsonUtils.getInt` extraction inside
  `ProgressRepositoryImpl` (the old function stays for now if other callers
  exist — mark it for later removal rather than deleting in this PR).
- Update `CoursesViewModel` to expose the typed map and `CoursesAdapter`'s
  `progressMap` field to `Map<String, CourseProgress>`, deleting the
  `getInt("current"/"max")` calls in `progressState()`.

**Touches:** `repository/ProgressRepository{,Impl}.kt`,
`ui/courses/CoursesViewModel.kt`, `ui/courses/CoursesAdapter.kt`,
new `model/CourseProgress.kt`. (Check for other `getCourseProgress` callers
first — if more than ~3, keep the old method and only migrate the adapter.)

---

## Task 6 — Stop ViewModels reaching into `UserSessionManager`/`SharedPrefManager` for user identity

**Why:** ~12 ViewModels inject `UserSessionManager` or `SharedPrefManager`
(`UserProfileViewModel`, `SurveysViewModel`, `TakeCourseViewModel`,
`CourseProgressViewModel`, `ProgressViewModel`, `RequestsViewModel`,
`PersonalsViewModel`, `SettingsViewModel`, `LeadersViewModel`, `LifeViewModel`,
`ResourceViewerViewModel`, `FeedbackListViewModel`) just to get the current
user's name/id — session state is data and should come through
`UserRepository` (which already has `getUserModel()`, `getActiveUserIdSuspending()`).

**Do:**
- Pick the 3 smallest ViewModels (`CourseProgressViewModel`,
  `ProgressViewModel`, `ResourceViewerViewModel`).
- For each, replace the `UserSessionManager`/`SharedPrefManager` usage with
  the existing `UserRepository` (already injected in some) — add one narrow
  function to `UserRepository` only if an equivalent truly doesn't exist
  (check `getUserModel` / `getUserProfile` first; reuse them).
- Delete the now-unused session-manager constructor params and their imports.

**Touches:** `repository/UserRepository{,Impl}.kt` (≤1 new function),
`ui/courses/CourseProgressViewModel.kt`, `ui/courses/ProgressViewModel.kt`,
`ui/viewer/ResourceViewerViewModel.kt`.

---

## Task 7 — Migrate `UserArrayAdapter`-style leftovers and remaining manual-diff adapters to `ListAdapter` + `DiffUtils.standardItemCallback`

**Why:** The project has `utils/DiffUtils.standardItemCallback` used by 41
adapters, but a few still extend `RecyclerView.Adapter`/`ArrayAdapter` and
hand-roll updates (`ui/user/UserArrayAdapter.kt`, plus `CoursesAdapter.setViewMode`
and `ResourcesAdapter.setViewMode` still call `notifyDataSetChanged()`). The
two `notifyDataSetChanged()` call sites in Courses/Resources adapters are the
only ones left in `ui/`.

**Do:**
- `CoursesAdapter.setViewMode` and `ResourcesAdapter.setViewMode`: replace
  `notifyDataSetChanged()` with `submitList(currentList.toList())` so the
  view-type flip goes through DiffUtil like everything else (one line each).
- Check `ui/user/UserArrayAdapter.kt`: if it backs a `ListView`/`Spinner`,
  leave it; if it backs a `RecyclerView`, migrate it to
  `ListAdapter<UserEntity, ...>(DiffUtils.standardItemCallback(idSelector = { it.id }))`
  using the existing pattern from `UsersAdapter.kt` in the same package.

**Touches:** `ui/courses/CoursesAdapter.kt` (1 line),
`ui/resources/ResourcesAdapter.kt` (1 line),
`ui/user/UserArrayAdapter.kt` (only if RecyclerView-backed — otherwise skip
and note it).

---

## Task 8 — Move `TeamsRepositoryImpl`'s non-team DAO queries to their owning repositories

**Why:** `TeamsRepositoryImpl` imports `CourseDao`, `CourseStepDao`,
`MyLibraryDao` (`TeamsRepositoryImpl.kt`) — it queries courses and library
resources directly, which is a cross-feature data leak: the courses/resources
feature's read paths live in a teams class, so teams changes can silently
change courses behavior.

**Do:**
- List the functions in `TeamsRepositoryImpl` that touch `CourseDao` /
  `CourseStepDao` / `MyLibraryDao` (team-courses and team-resources linking).
- For each DAO call, find or add the equivalent query in
  `CoursesRepository` / `ResourcesRepository` and have `TeamsRepositoryImpl`
  call the repository instead of the DAO. Add at most the missing queries —
  reuse `CourseDao.getAll`-style existing DAO methods where they suffice.
- Remove the DAO constructor params that become unused.
- If a function would force `CoursesRepository` to grow a team-specific query,
  leave it and note it — don't contort the boundary for one call.

**Touches:** `repository/TeamsRepositoryImpl.kt`,
`repository/CoursesRepository{,Impl}.kt`,
`repository/ResourcesRepository{,Impl}.kt`, `di/RepositoryModule.kt` (ctor
wiring only).

---

## Task 9 — Move `LoginActivity`/`SyncActivity` file + user provisioning logic into repositories/services

**Why:** `ui/sync/SyncActivity.kt` injects 6+ repositories plus
`UserSessionManager` and does direct file work (`File(FileUtils.getOlePath(this))`,
line 294) and `openDownloadService` calls (585). `ui/sync/LoginActivity.kt`
mixes auth flow with storage checks. These two screens are the sync flow's UI;
they shouldn't own provisioning logic.

**Do (one screen only — SyncActivity):**
- Extract the directory-setup / concatenated-links download kickoff
  (around lines 294 and 585) into a small function on
  `ConfigurationsRepository` (config/storage) or a new `SyncProvisioning`
  helper in `services/sync/` — whichever needs fewer new types.
- Keep `SyncActivity` calling one function; move the `FileUtils.getOlePath`
  and `openDownloadService` wiring inside.
- Don't touch `LoginActivity` in this PR (keeps the diff small and avoids
  clashing with any auth work).

**Touches:** `ui/sync/SyncActivity.kt`,
`repository/ConfigurationsRepository{,Impl}.kt` or new
`services/sync/SyncProvisioning.kt`.

---

## Task 10 — Retire the vestigial `DatabaseService` wrapper

**Why:** CLAUDE.md notes `data/DatabaseService.kt` (~33 lines) is "essentially
vestigial — no repository injects it anymore". It is still provided by
`di/DatabaseModule.kt`, so it sits in the DI graph as dead weight and invites
new code to bypass the DAO convention.

**Do:**
- Grep for remaining `DatabaseService` usages (expected: only
  `di/DatabaseModule.kt` and possibly a test or `MainApplication`).
- Migrate the last 1-2 callers (if any) to the DAO / `AppDatabase.withTransaction`
  pattern the rest of the codebase uses.
- Delete `DatabaseService.kt` and its provider in `DatabaseModule.kt`.
- Update the CLAUDE.md line that mentions it (one sentence).

**Touches:** `data/DatabaseService.kt` (delete), `di/DatabaseModule.kt`,
`CLAUDE.md`, plus whatever 1-2 files still reference it.

---

## Suggested PR order (conflict-avoidance)

| # | Task | Package(s) touched | Conflicts with |
|---|------|--------------------|----------------|
| 1 | VoicesAdapter repo removal | `ui/voices`, `ui/teams/voices` | — |
| 2 | Dictionary repository | `ui/dictionary`, `repository`, `di` | — |
| 3 | TeamsRepository interface split | `repository`, `ui/enterprises`, `ui/teams/members` | 8 |
| 4 | Sync `*FromSync` interface split | `repository`, `services/sync`, `di` | 8, 10 |
| 5 | ProgressRepository typed map | `repository/Progress*`, `ui/courses` | 6 (courses VM) |
| 6 | ViewModel session-manager cleanup | `ui/courses`, `ui/viewer` VMs | 5 |
| 7 | Adapter diff cleanup | `ui/courses`, `ui/resources`, `ui/user` adapters | 5 (CoursesAdapter) |
| 8 | TeamsRepositoryImpl DAO de-leak | `repository/Teams*`, `Courses*`, `Resources*` | 3, 4 |
| 9 | SyncActivity logic extraction | `ui/sync`, `repository/Configurations*` | — |
| 10 | DatabaseService removal | `data`, `di`, `CLAUDE.md` | 4 (di module) |

Merge order 1 → 2 → 9 → 5 → 6 → 7 → 3 → 4 → 8 → 10 keeps overlapping pairs
(3/8, 4/8, 4/10, 5/6, 5/7) maximally separated in the review round.

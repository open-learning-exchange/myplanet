# Refactor Round — Performance Quick Wins (12 reviewable PRs)

**Round goal:** ship the cheap, high-leverage parts of the roadmap items
*1. Finish Cleaning the Data Layer*, *3. Expand ViewModel Layers*, *4. Complete DI Cleanup*,
*7. Optimize Remaining Performance Hotspots* — without any big rewrite.

**Constraints honoured:**
- ~10 PRs per review round → 12 tasks listed, 10 core + 2 sequenced extras.
- **Zero file overlap between tasks 1–10** (see the conflict matrix at the bottom), so they can be
  opened in parallel and merged in any order.
- No new abstractions, no dead code, no schema/`AppDatabase` version bumps
  (nothing here changes an `@Entity`, so no destructive resync is triggered).
- Each PR is 1–2 files, one idea, one commit.

**Baseline notes (so reviewers don't re-litigate settled ground):**
- All list adapters are already `ListAdapter` + `DiffUtils.itemCallback` — there is no
  `RecyclerView.Adapter<>` left, and only 2 `notifyDataSetChanged()` calls remain (both legitimate:
  grid/list view-type flips).
- No `GlobalScope`, no `runBlocking`, no `observeForever`, no `LiveData` anywhere in `main`.
- `SharingStarted.WhileSubscribed(5000)` is already the house standard in ViewModels (1 exception, task 12).
- `TimeUtils` already caches its formatters; `Gson` is already injected (2 construction sites, both correct).

---

## Task 1 — Delete the vestigial `DatabaseService` and `DatabaseModule`

**Roadmap:** #4 Complete DI Cleanup · #1 Finish Cleaning the Data Layer

**Files**
- delete `app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt`
- delete `app/src/main/java/org/ole/planet/myplanet/di/DatabaseModule.kt`
- delete `app/src/test/java/org/ole/planet/myplanet/data/DatabaseServiceTest.kt`
- edit `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt` (line 22, 40)
- edit `app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt` (line 19, 49)

**What / why**
`DatabaseService` (`withRoomAsync`, `executeRoomTransactionAsync`, `clearAll`) has **zero production
call sites left** — the only references in `app/src/main` are its own file and the Hilt module that
provides it. Every repository now injects DAOs directly and uses `AppDatabase.withTransaction`.
The two sync tests still declare a `databaseService` mock that nothing consumes.

Removing it deletes a whole `@Singleton` from the graph, kills the last "God object" data-access
entry point, and removes the temptation to reintroduce it in new repositories.

**Acceptance**
- `./gradlew testDefaultDebugUnitTest` green.
- `grep -rn "DatabaseService" app/src` returns nothing.
- `ConfigurationsRepositoryImpl.clearAllData()` still clears tables (it already calls
  `appDatabase.clearAllTables()` directly — no behavioural change).

**Risk:** ~none. Pure deletion of unreachable code.

---

## Task 2 — `ResourcesAdapter`: broken `areContentsTheSame` rebinds every visible row

**Roadmap:** #7 Performance Hotspots (DiffUtil correctness)

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt` (line ~47)

**What / why**
Today:

```
private val ITEM_CALLBACK = DiffUtils.standardItemCallback<ResourceListModel>(
    idSelector = { it.item.id ?: "" },
    payloadSelector = { ... }        //  <-- no contentSelector
)
```

With no `contentSelector`, `DiffUtils.standardItemCallback` falls back to `oldItem == newItem`.
`ResourceListModel` is a `data class`, but it holds `library: MyLibrary`, and `MyLibrary` is an
`open class` with **no `equals()` override** → identity comparison. Repositories rebuild `MyLibrary`
instances on every query, so `areContentsTheSame` is **permanently `false`** and every
`setLibraryList()` full-rebinds every visible row (image reload + Markwon/Glide work per row) even
when nothing changed.

Fix: pass an explicit `contentSelector` over the scalar fields, exactly the shape `CoursesAdapter`
already uses successfully:

```
contentSelector = { listOf(it.item.title, it.item.description, it.item._rev,
                           it.item.isOffline, it.item.averageRating, it.item.timesRated,
                           it.isOpened, it.isLocallyOffline, it.tags) }
```

Keep the existing `payloadSelector` untouched.

**Acceptance**
- Trigger a resource list refresh (rating change, sync tick) with no data change → no visible flicker,
  and `onBindViewHolder` is not called for unchanged rows (verify with a temporary log or Layout
  Inspector, not a committed log).
- Scroll position and selection survive a refresh.

**Note for the reviewer:** I audited all 30 diff callbacks. This is the **only** broken one — every
other `oldItem == newItem` sits on a real `data class` of scalars (`TeamDetails`, `SubmissionItem`,
`SubmissionUiModel`, `QuestionAnswer`, `FeedbackReply`, `ChatShareTargetItem`, `ReferenceRow`,
`Pair`). No sweep needed.

**Risk:** low. One expression. Worst case a field is missing from the selector → stale cell, caught in review.

---

## Task 3 — `ResourcesFragment.getAdapter()` builds a brand-new adapter on every refresh

**Roadmap:** #7 Performance Hotspots · #3 Expand ViewModel Layer

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt` (line ~146)

**What / why**
`getAdapter()` unconditionally calls `factory.createResourcesAdapter(...)` and reassigns
`adapterLibrary`. `BaseRecyclerFragment` calls `getAdapter()` from **three** places —
`onViewCreated`, `onRatingChanged`, and `postAddRefresh` — so rating a resource or adding to
"My Library" throws away the adapter, its `AsyncListDiffer`, the whole view pool, scroll position,
and the current selection set, then re-inflates every row from scratch. It also does a full
repository fetch (`viewModel.getLibraryListModels(...)`) inside adapter construction.

`SurveyFragment` already solves this correctly (`adapterMutex` + `if (adapter == null)`).
Mirror that: create once, and on subsequent calls only re-fetch and `setLibraryList(...)`.

**Acceptance**
- Rate a resource → list keeps scroll position, no full re-inflate.
- Add to My Library → same.
- `setListener` / `setOpenedResourceIds` / `setViewMode` still applied to the surviving instance.

**Risk:** low-medium. Single file, but touches the "which instance is live" logic — worth a careful read.
Test manually in both grid and list view mode.

---

## Task 4 — `LifeFragment.getAdapter()`: same recreate-per-refresh, plus a leaked `ItemTouchHelper`

**Roadmap:** #7 Performance Hotspots

**File:** `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt` (line ~44)

**What / why**
Same pattern as task 3, with an extra cost: each call also builds a new `ItemReorderHelper` and a new
`ItemTouchHelper`, and attaches it — so repeated refreshes stack drag-and-drop callbacks on one
RecyclerView. Cache the adapter and attach the touch helper exactly once.

**Acceptance**
- Toggle a My Life item's visibility, then drag-reorder → exactly one reorder callback fires per drag
  (today it fires once per accumulated helper).
- Order persists via `viewModel.updateMyLifeListOrder`.

**Risk:** low. Deliberately kept as a separate PR from task 3 (different file, different reviewer path,
and the touch-helper bug is worth its own commit message).

---

## Task 5 — `CoursesRepositoryImpl`: Flow chains map on the collector's dispatcher (Main)

**Roadmap:** #7 Performance Hotspots (threading) · #1 Data Layer

**File:** `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
(lines ~102, ~112, ~152)

**What / why**
Three Flow-returning functions do real work in `map { }` with **no `flowOn`**:

| fn | problem |
|---|---|
| `getMyCoursesFlow(userId)` (102) | `courseDao.observeAll()` → `mapCourses(...)` + `.filter { it.userId?.contains(userId) }` over the **whole** courses table, on whatever dispatcher collects (Main from a Fragment) |
| `getCourseByCourseIdFlow(courseId)` (152) | `mapCourse(...)` on the collector |
| `getCourseDetailModel(courseId)` (112) | wraps its body in `withContext(io)` inside `map`, so the hop happens per emission instead of once for the chain |

`TeamsRepositoryImpl` (lines 80, 204, 293), `VoicesRepositoryImpl` (173, 188) and
`SurveysRepositoryImpl` (438) already terminate their chains with
`.flowOn(dispatcherProvider.default / .io)`. Bring Courses in line:

- append `.flowOn(dispatcherProvider.default)` to `getMyCoursesFlow` and `getCourseByCourseIdFlow`
- append `.distinctUntilChanged()` to `getMyCoursesFlow` (Room re-emits on **any** write to `courses`;
  during a sync that's thousands of emissions of an identical mapped list)
- for `getCourseDetailModel`, hoist the `withContext(dispatcherProvider.io)` into a single trailing
  `.flowOn(dispatcherProvider.io)`

**Acceptance**
- `./gradlew testDefaultDebugUnitTest` green (existing `CoursesRepositoryImplTest` uses
  `TestDispatcherProvider`, so the added `flowOn` stays deterministic).
- Course list no longer janks while a sync is running.

**Risk:** low. Additive operators, no logic change. Watch that no test asserts emission *counts*
that `distinctUntilChanged` would collapse.

---

## Task 6 — `UserDao`: replace `getAll().filter { }` with narrow queries

**Roadmap:** #1 Finish Cleaning the Data Layer

**Files**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`

**What / why**
`UserRepositoryImpl` loads the **entire** `users` table and filters in Kotlin at 8 sites
(lines 111, 129, 143, 169, 177, 332, 947, 1176). Concretely:

| current | replace with |
|---|---|
| `getUsersByIds` (111) — `getAll().filter { it.id in set \|\| it._id in set }` | `@Query("SELECT * FROM users WHERE id IN (:ids) OR _id IN (:ids)")` |
| `getSyncedUsers` (129) — `getAll().filter { !_id.isNullOrBlank() && !id.startsWith("guest") }` | `@Query("... WHERE _id IS NOT NULL AND _id != '' AND id NOT LIKE 'guest%'")` |
| `getUsersForHealthSync` (143) — loads full rows only to copy 3 fields into `mapToLightweightUser` | a projection query returning `id`, `name`, `planetCode` only |
| `getPendingSyncUsers(limit)` (177) — `getAll()` then `.take(limit)` | `@Query("... WHERE _id IS NULL OR _id = '' OR isUpdated = 1 LIMIT :limit")` |

`users` already has `Index("_id")`, `Index("name")`, `Index("planetCode")` — no `@Entity` change,
therefore **no `AppDatabase` version bump and no destructive resync**.

Use `IS`/`IS NOT NULL` (not `=`) where a `NULL` should match, per the project's DAO rules.

**Acceptance**
- Existing `UserRepositoryImplTest` green; add one DAO test per new query if a matching pattern exists
  in `app/src/test/.../data/room/`.
- `getUsersForHealthSync()` returns objects with only the 3 fields populated (same contract as today's
  `mapToLightweightUser`) — delete `mapToLightweightUser` if the projection replaces it entirely.

**Risk:** low-medium. SQL is mechanical, but `guest%` LIKE vs `startsWith("guest")` and the
`_id`-blank-vs-null distinction need one careful read each.

---

## Task 7 — `TeamDao`: narrow queries + stop subscribing to the whole `teams` table

**Roadmap:** #1 Data Layer · #7 Performance Hotspots

**Files**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`

**What / why**
Two separate inefficiencies, same pair of files, so one PR:

**(a) three full-table scans** — `teamDao.getAll()` then filter in Kotlin:
- `getJoinRequestsInfo(requestIds)` (399) → `@Query("SELECT * FROM teams WHERE _id IN (:ids) OR id IN (:ids)")`
- `getTeamNamesByIds(ids)` (416) → same query, or a 2-column projection (`_id`, `name`)
- `getResourceIds(teamId)` (978) → `@Query("SELECT resourceId FROM teams WHERE teamId = :teamId AND resourceId IS NOT NULL AND resourceId != '' AND (docType IS NULL OR docType = '' OR docType IN ('resourceLink','link'))")`

`teams` already indexes `_id`, `teamId`, `userId`, `type`, `docType` — no schema change.

**(b) `getMyTeamDetailsFlow(userId)` (277)** subscribes to `teamDao.observeAll()` and re-filters the
whole table on every emission. Room invalidates that Flow on **every write to `teams`**, and sync
upserts teams in batches — so this recomputes hundreds of times for one sync. Add
`.distinctUntilChanged()` after the existing `.flowOn(dispatcherProvider.default)`, or narrow the
source to `observeByDocType("membership")` combined with a root-teams query.

**Acceptance**
- Existing `TeamsRepositoryImplTest` green.
- Team list does not visibly rebuild repeatedly during a sync.

**Risk:** medium — `TeamsRepositoryImpl` is the largest file in the repo (~1437 lines), so keep the
diff surgical and resist the urge to start splitting it here (that's a later, bigger roadmap item).

---

## Task 8 — `VoicesRepositoryImpl.collectNewsAndReplies`: N+1 recursion → one recursive CTE

**Roadmap:** #1 Data Layer · #7 Performance Hotspots

**Files**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` (line ~279)

**What / why**

```
private suspend fun collectNewsAndReplies(newsId: String): List<String> {
    val ids = mutableListOf(newsId)
    newsDao.getDirectReplies(newsId).forEach { reply ->
        ids.addAll(collectNewsAndReplies(reply.id))   // one query per node
    }
    return ids
}
```

Deleting a discussion thread issues **one query per reply, recursively**, and loads full `News` rows
just to read `.id`. Replace with a single recursive CTE that returns ids only:

```
@Query("""
    WITH RECURSIVE thread(id) AS (
        SELECT :newsId
        UNION
        SELECT news.id FROM news JOIN thread ON news.replyTo = thread.id
    )
    SELECT id FROM thread
""")
suspend fun getThreadIds(newsId: String): List<String>
```

`news` already indexes `replyTo`. Keep `deleteByIds(...)` as-is. Deep threads go from *N* queries and
*N* row hydrations to 1 query returning strings.

**Acceptance**
- Deleting a post with nested replies removes the whole subtree (add a Room DAO test with a 3-level
  thread if `NewsDao` tests exist; otherwise cover it at the repository level with MockK).
- Deleting a leaf post still returns just its own id.

**Risk:** low. The CTE is self-contained and easy to reason about; `UNION` (not `UNION ALL`) also
makes it cycle-safe, which the current recursion is not.

---

## Task 9 — Memoize `UrlUtils.header` (Base64 recomputed on every access)

**Roadmap:** #7 Performance Hotspots (micro-optimization)

**File:** `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` (line ~30)

**What / why**

```
val header: String
    get() {
        val spm = spm()
        return basicAuthHeader(spm.getUrlUser(), spm.getUrlPwd())
    }
```

Every access does 2 `SharedPreferences` reads, a `String` concat, a `toByteArray()`, and a
`Base64.encodeToString()`. There are **92** `UrlUtils.header` / `UrlUtils.getUrl()` call sites,
including inside `onBindViewHolder` paths that build Glide `LazyHeaders` **per list row** and inside
per-document sync loops.

Fix: cache the encoded value keyed on the `(user, pwd)` pair, recompute only when they change.
Keep `basicAuthHeader(user, pwd)` public and pure so the existing tests keep working, and extend
`resetForTesting()` to clear the cache.

**Acceptance**
- Existing `UrlUtilsTest` green; add one case asserting the header changes after credentials change.
- Login → switch server → sync still authenticates (the cache must invalidate on credential change,
  not just on process start).

**Risk:** low, but it is a shared util on the auth path — the invalidation condition is the whole
review. Do **not** cache `hostUrl`/`baseUrl` in the same PR (alternative-URL failover mutates those
mid-session).

---

## Task 10 — `ConfigurationsRepositoryImpl`: drop redundant `withContext(io)` around suspend Retrofit calls

**Roadmap:** #7 Performance Hotspots (threading hygiene) · #5 Consolidate Sync Workflow

**File:** `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`

**What / why**
This file has 8 `withContext(dispatcherProvider.io)` blocks — the most of any repository. Three of
them wrap **nothing but a `suspend` Retrofit call**, which Retrofit already dispatches off the caller's
thread:

- line 61 — `withContext(io) { apiInterface.healthAccess(healthUrl) }`
- line 206 — `checkServerAvailability`, body is one `apiInterface.isPlanetAvailable(url)` + response parsing
- line 224 — `checkCheckSum`, wraps `apiInterface.getChecksum(...)`

Each redundant hop is a dispatch + continuation allocation on a path the sync/login flow hits
repeatedly. Remove those three.

**Keep** the ones doing real blocking work — line 170 (reachability composition), line 200
(`appDatabase.clearAllTables()`), line 343 (`processConfigurationDoc`) — and keep the
`FileUtils`/checksum file I/O inside `io` if it stays in the same block.

**Acceptance**
- `./gradlew testDefaultDebugUnitTest` green.
- Login against a reachable and an unreachable server still behaves identically (this is the one to
  smoke-test manually).

**Risk:** low, but it is the **most debatable** task in this list — a reviewer may reasonably say the
wrappers are harmless. If it draws pushback, drop it rather than argue; the value is hygiene plus a
precedent for the other ~120 `withContext(dispatcherProvider.*)` sites, not raw milliseconds.

---

## Task 11 (sequenced — merge **after** Task 6) — `HealthRepositoryImpl` full-table scans

**Roadmap:** #1 Data Layer

**File:** `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`
(lines 155, 161, 206)

**What / why**
Three more `userDao.getAll()` scans, in the health flow:
- `getPatientsSortedBy` (155) — loads all users, sorts in Kotlin → push `ORDER BY` into SQL
  (`joinDate`, `name COLLATE NOCASE`, both directions)
- `searchPatients` (161) — `getAll()` on a blank query → reuse the sorted query
- `getPatientHealthRecords` (206) — `getAll().filter { it.id in userIdSet }` → reuse the
  `getByIds(...)` query **added in Task 6**

**Why sequenced:** it consumes `UserDao.getByIds(...)` from Task 6. Opening both at once means two
PRs editing `UserDao.kt` — exactly the merge conflict we're avoiding this round. Land Task 6 first,
then rebase this on top. If the round runs out of slots, this is the one to defer.

**Acceptance:** existing `HealthRepositoryImplTest` green; patient list sort order unchanged for both
fields and both directions.

**Risk:** low once Task 6 is in.

---

## Task 12 (optional filler) — `NetworkUtils.isNetworkConnectedFlow` restarts on every subscriber gap

**Roadmap:** #7 Performance Hotspots (long-running observers)

**File:** `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt` (line ~53)

**What / why**
This is the **only** `SharingStarted.WhileSubscribed()` in the codebase without a stop timeout — the
other 17 all use `WhileSubscribed(5000)`. It sits on an app-scoped singleton whose underlying
`ConnectivityManager` callback is registered for the whole process lifetime
(`MainApplication` line 381), so the sharing coroutine tears down and restarts on every
fragment transition where the last collector momentarily detaches.

Fix: `SharingStarted.WhileSubscribed(5_000)` (or `Eagerly`, given the callback is always registered
anyway) — one line, consistent with every other call site.

**Acceptance**
- `NetworkUtils` tests green.
- Airplane-mode toggle still flips `isNetworkConnected` and still triggers `NetworkMonitorWorker`
  (which does `isNetworkConnectedFlow.first { it }`).

**Risk:** very low. Listed last because it is a one-liner — use it as filler if another task gets cut,
or fold it into whichever PR you'd rather not send alone.

---

## Conflict matrix

Tasks 1–10 touch **disjoint** file sets, so they can all be open at once:

| # | Files owned |
|---|---|
| 1 | `data/DatabaseService.kt`, `di/DatabaseModule.kt`, 3 test files |
| 2 | `ui/resources/ResourcesAdapter.kt` |
| 3 | `ui/resources/ResourcesFragment.kt` |
| 4 | `ui/life/LifeFragment.kt` |
| 5 | `repository/CoursesRepositoryImpl.kt` |
| 6 | `data/room/dao/UserDao.kt`, `repository/UserRepositoryImpl.kt` |
| 7 | `data/room/dao/TeamDao.kt`, `repository/TeamsRepositoryImpl.kt` |
| 8 | `data/room/dao/NewsDao.kt`, `repository/VoicesRepositoryImpl.kt` |
| 9 | `utils/UrlUtils.kt` |
| 10 | `repository/ConfigurationsRepositoryImpl.kt` |
| 11 | `repository/HealthRepositoryImpl.kt` — **but needs `UserDao.kt` from #6** |
| 12 | `utils/NetworkUtils.kt` |

**Only ordering constraint:** 11 after 6. Everything else is order-independent.

**Nothing in tasks 1–12 changes an `@Entity`**, so no `AppDatabase` `version` bump and no
drop-and-resync — no risk of shipping unsynced-local-write loss this round.

## Suggested review order

Cheapest-to-verify first, so the round front-loads merges:
**1 → 2 → 9 → 12 → 5 → 8 → 4 → 6 → 11 → 7 → 3 → 10**

## Explicitly out of scope this round

Named so nobody bundles them in: splitting `TeamsRepositoryImpl` (1437 lines), the navigation
architecture (roadmap #2), any Compose migration (#6), new ViewModels (#3 beyond what tasks 3–5
touch), moving secrets out of the tracked `gradle.properties`, and enabling R8/`minifyEnabled`.

# myPlanet refactor round — repository-boundary work orders

date: 2026-09-04 · base commit: `9ff1273dc95f8cbd3590fca12ca821454b2e27bc` (tag v0.69.36) ·
open PRs checked: 48 open PRs on `master`; every file cited below was matched
against the exact off-limits file set and is free of any open-PR touch.

These are work orders for other coding agents (jules, codex, copilot, devin,
openhands, claude, qwen). Each task is independently mergeable in any order,
touches no file that another task in this set touches, and stays under ~150
changed lines / ~5 files with no new dependencies. The acceptance command for
every task is:

```
./gradlew testDefaultDebugUnitTest
```

Run it from the repo root; it must stay green. The user-visible behavior noted
per task must also hold.

Roadmap keys: 1 finish cleaning the data layer · 2 global navigation · 3
viewmodel/use-case layers · 4 DI cleanup · 5 sync/upload consolidation · 6
compose migration · 7 performance hotspots · 8 code health + tests · 9 KMP
core (north star) · 10 compose-multiplatform portability (north star).

---

### 1. move archived-report filtering and sorting into the TeamDao query (roadmap 1+7)

context: `EnterprisesRepositoryImpl.exportReportsAsCsv` (line 103) loads every
report row for a team with `teamDao.getByTeamIdAndDocType(teamId, "report")`
and then filters and sorts in Kotlin:

```
val reports = teamDao.getByTeamIdAndDocType(teamId, "report")
    .filter { it.status != "archived" }
    .sortedByDescending { it.createdDate }
```

That pulls archived rows across the Room/SQLite boundary only to drop them, and
does an in-memory sort the database can do. The DAO already shows the right
pattern next door: `TeamDao.observeNonArchivedReportsByTeamId` (line 22) uses
`IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`. Note the
`IFNULL` matters: the current Kotlin `it.status != "archived"` keeps
null-status rows (because `null != "archived"` is true), so the SQL must use
`IFNULL(status,'') != 'archived'` to preserve that behavior — a bare
`status != 'archived'` would silently drop every null-status report.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` — add a
  new `suspend` query `getNonArchivedReportsByTeamId(teamId: String)` modelled
  on the existing `observeNonArchivedReportsByTeamId` (line 22) but returning
  `List<MyTeam>` (no `Flow`). Do NOT change the existing `observe…` or
  `getByTeamIdAndDocType` queries.
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`
  — in `exportReportsAsCsv` (line 103), replace the
  `getByTeamIdAndDocType(...).filter{...}.sortedByDescending{...}` chain with
  the new `getNonArchivedReportsByTeamId(teamId)` call. Leave the CSV column
  math below it untouched.
- `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt`
  — add/adjust a test asserting archived reports are excluded and remaining
  reports arrive newest-first.

steps:
1. Add `getNonArchivedReportsByTeamId` to `TeamDao` using
   `SELECT * FROM teams WHERE teamId = :teamId AND docType = 'report' AND
   IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`.
2. Swap the call site in `exportReportsAsCsv`.
3. Update the repository test to cover the archived-exclusion + ordering.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the enterprises CSV
export still omits archived reports and lists them newest-first.

size budget: ~25 changed lines, 3 files

out of scope: no changes to `MyTeam`, no new index, do not touch the live
`observeNonArchivedReportsByTeamId` flow used by the reports list.

---

### 2. return the refreshed list from LifeRepository.updateVisibility and stop the ViewModel re-query (roadmap 1+3)

context: `LifeRepositoryImpl.updateVisibility` (line 21) updates one row, then
calls `getMyLifeByUserId(effectiveUserId)` (line 27) purely to refresh the
cache — it throws the returned list away. Then `LifeViewModel.updateVisibility`
(line 46) calls `lifeRepository.updateVisibility(...)` and immediately calls
`loadMyLifeList()` (line 51), which calls `getMyLifeByUserId` a second time.
So every visibility toggle runs the full `my_life` query twice after the
write. The repository already has the refreshed list in hand; returning it
removes one redundant DB round-trip and lets the ViewModel update its
`StateFlow` directly from the result.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`
  — change `updateVisibility` (line 6) to return `List<MyLife>` instead of
  `Unit`.
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
  — `updateVisibility` (line 21) already computes `updatedLives` (line 27);
  return it instead of discarding it. Do not change `updateMyLifeListOrder`
  or the caching behavior.
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`
  — assert `updateVisibility` returns the refreshed list.
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`
  — in `updateVisibility` (line 46), assign the returned list to
  `_myLifeList.value` and drop the `loadMyLifeList()` call (line 51).
- `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`
  — the test `updateVisibility calls repository and reloads list` asserts
  `getMyLifeByUserId` is called after `updateVisibility`; rewrite it to assert
  `updateVisibility` returns the list and `getMyLifeByUserId` is NOT called
  again from `updateVisibility`.

steps:
1. Change the `LifeRepository.updateVisibility` signature to return
   `List<MyLife>`.
2. In the impl, return the already-fetched `updatedLives`.
3. In the ViewModel, set `_myLifeList.value` from the return value; remove the
   `loadMyLifeList()` call.
4. Update both tests to match the new contract.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; toggling a My Life
item's visibility still updates the list on screen, with one DB query instead
of two.

size budget: ~40 changed lines, 5 files

out of scope: do not change `updateMyLifeListOrder`, do not add a projection
query to `MyLifeDao`, do not touch `MyLife` or `DashboardPluginFragment`.

---

### 3. replace the UserSessionManager dependency in CommunityTabViewModel with UserRepository (roadmap 4+9)

context: `CommunityTabViewModel` (line 24) injects `UserSessionManager` — a
service class that wraps Android `Context`/`SharedPreferences` — solely to call
`userSessionManager.getUserModel()` (line 33) and read `user.planetCode`. A
ViewModel depending on an Android-`Context`-bound service is a
dependency-injection leak and a KMP blocker: the smoother, platform-free
dependency is `UserRepository`, whose interface (`UserRepository.kt` line 74)
already exposes `suspend fun getUserModel(): UserEntity?`. Swapping it keeps
the same data, removes a service-layer dependency from the ViewModel layer,
and makes `CommunityTabViewModel` one step closer to a platform-free core.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`
  — replace the `UserSessionManager` constructor parameter with
  `UserRepository`; change `userSessionManager.getUserModel()` (line 33) to
  `userRepository.getUserModel()`. Update the import.
- `app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModelTest.kt`
  — replace the `UserSessionManager` mock with a `UserRepository` mock and
  adjust the `coEvery`/`coVerify` for `getUserModel()`.

steps:
1. In the ViewModel, swap the constructor dependency and the call.
2. Update the test's mock setup and verifications.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the community tab
still resolves the current user's `planetCode` into state on init.

size budget: ~20 changed lines, 2 files

out of scope: do not change `UserSessionManager` or `UserRepositoryImpl`; do
not touch other ViewModels that use `UserSessionManager`.

---

### 4. derive EnterprisesFinancesViewModel.headerState from transactions instead of mutating parallel state (roadmap 3+6+10)

context: `EnterprisesFinancesViewModel` (line 31) keeps two parallel
`MutableStateFlow`s — `_transactions` and `_headerState`. Inside
`getTeamTransactions` (line 58) it collects the transaction list, sets
`_transactions.value`, then calls the imperative `calculateTotal(results)`
(line 66) which loops the list again to recompute debit/credit/total and
mutates `_headerState`. `headerState` is a pure function of `transactions`,
so it should be a derived `StateFlow` via `map { ... }.stateIn(...)`,
removing the parallel mutable state and the manual `calculateTotal` call.
This is the Compose-portable pattern (state hoisted, derived, no imperative
mutation) and removes a class of stale-state bugs.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModel.kt`
  — keep the `calculateTotal(list)` logic but turn it into a pure mapping
  function (return `FinanceHeaderState`); expose `headerState` as
  `transactions.map { calculateTotal(it) }.stateIn(viewModelScope,
  SharingStarted.WhileSubscribed(5000), FinanceHeaderState())`. Delete the
  `_headerState` `MutableStateFlow` and the `calculateTotal(results)` call
  inside `getTeamTransactions`. Keep `_transactions` and the `collectLatest`.
- `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModelTest.kt`
  — the test asserts `headerState.first()` after `getTeamTransactions`; it
  stays valid (the derived flow still emits the computed value) — adjust only
  if the test directly references the removed `_headerState`.

steps:
1. Convert `calculateTotal` to return `FinanceHeaderState`.
2. Define `headerState` as the `map{}.stateIn` derived flow.
3. Remove `_headerState` and the imperative call in `getTeamTransactions`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the finances header
still shows correct debit/credit/total and the caution flag when total < 0.

size budget: ~30 changed lines, 2 files

out of scope: do not change `TeamsFinancesRepository` or the
`getTeamTransactionsWithBalance` query; do not touch the fragment.

---

### 5. collapse the two-query personal lookup into a single PersonalDao query (roadmap 1+7)

context: `PersonalsRepositoryImpl.updatePersonalResource` (line 64) locates a
row with `personalDao.findByDocId(id) ?: personalDao.findById(id)` — two
separate `LIMIT 1` queries run back-to-back for every update. The same DAO
already demonstrates the single-query union pattern for deletes:
`PersonalDao.deleteByIdOrDocId` (line 39) uses
`WHERE _id = :id OR id = :id`. A read-side `findByDocIdOrId(id)` mirroring that
turns two round-trips into one.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`
  — add `@Query("SELECT * FROM my_personal WHERE _id = :id OR id = :id LIMIT 1") suspend fun findByDocIdOrId(id: String): Personal?`. Keep the existing
  `findByDocId` (line 31) and `findById` (line 34) untouched (other callers and
  tests use them).
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`
  — in `updatePersonalResource` (line 64), replace
  `findByDocId(id) ?: findById(id)` with `findByDocIdOrId(id)`.
- `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
  — update/confirm the `updatePersonalResource` test verifies lookup by either
  `_id` or `id` via the single query.

steps:
1. Add `findByDocIdOrId` to `PersonalDao`.
2. Swap the call site in `updatePersonalResource`.
3. Update the repository test if it asserts the two-call fallback.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; editing a personal
resource by either its `_id` or `id` still loads and updates the row.

size budget: ~15 changed lines, 3 files

out of scope: do not change `deleteByIdOrDocId`, do not add an index, do not
touch `PersonalsViewModel`.

---

### 6. deduplicate the count-style upsert in NotificationsRepositoryImpl (roadmap 8)

context: `NotificationsRepositoryImpl.updateResourceNotification` (line 50)
and `updateStorageNotification` (line 69) are near-identical
read-modify-upsert routines: build a synthetic id, `notificationDao.getById`,
detect whether the numeric value changed, mark unread + set `createdAt` when
it did, else insert a new `AppNotification`, then `upsert`; and delete when
the value is zero/healthy. The only differences are the id suffix, the
`type`, the `relatedId`, how the previous value is parsed
(`toIntOrNull()` vs `replace("%","").toIntOrNull()`), and the formatted
message. This duplication is a maintenance hazard (the two drifted once
already: the resource path defaults `previousCount` to `0`, the storage path
to `null`).

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
  — extract a private `suspend fun upsertCountNotification(userId, idSuffix, type, relatedId, parsePrev: (String?) -> Int?, format: (Int) -> String, value: Int, healthy: Boolean)` (or equivalent) that both methods delegate to, preserving the existing semantics of each (including the parse and healthy-threshold differences). Keep the public method signatures.
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`
  — keep the existing `updateResourceNotification`/`updateStorageNotification`
  tests green; add one test covering the "value unchanged keeps it read" path
  if absent.

steps:
1. Extract the shared helper.
2. Route both methods through it without changing behavior.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; resource-count and
storage-percent notifications still appear, flip to unread on change, and are
deleted when the value is healthy.

size budget: ~60 changed lines, 2 files

out of scope: do not change `NotificationsRepository` interface, do not add
queries to the off-limits `NotificationDao`, do not touch `getTeamNotifications`.

---

### 7. extract the per-report financial calculation in EnterprisesReportsAdapter into a pure helper (roadmap 8+9)

context: `EnterprisesReportsAdapter.onBindViewHolder` (line 48) computes
`totalIncome`, `totalExpenses`, `profitLoss`, and `endingBalance` inline for
every bound row (lines 55-68):

```
val totalIncome = report.sales + report.otherIncome
val totalExpenses = report.wages + report.otherExpenses
val profitLoss = totalIncome - totalExpenses
```

The same arithmetic also appears in `EnterprisesRepositoryImpl.exportReportsAsCsv`.
Extracting a pure, testable helper here localizes the report-totals math
inside the enterprises package and makes it unit-testable without a
RecyclerView. (This task deliberately does not unify with the CSV path — that
file is owned by task 1 — it only cleans and tests the adapter's copy.)

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`
  — add a private top-level `data class ReportTotals(...)` and a pure
  `reportTotals(report: MyTeam): ReportTotals` in the same file; call it from
  `onBindViewHolder` (line 48). No behavior change.
- `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapterTest.kt`
  — add a unit test for `reportTotals` covering income, expenses, profit/loss,
  and ending balance (including a negative `beginningBalance`).

steps:
1. Add the `ReportTotals` data class and `reportTotals` function.
2. Use it in `onBindViewHolder`.
3. Add the unit test.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the reports list rows
still show the same totals, and the new `reportTotals` test passes.

size budget: ~35 changed lines, 2 files

out of scope: do not touch `EnterprisesRepositoryImpl` (task 1 owns it), do
not add computed properties to `MyTeam` (off-limits), do not change the
diff callback.

---

### 8. centralize the retry-attempt transition on the RetryOperation model (roadmap 8+9)

context: `RetryRepositoryImpl.updateAttempt` (line 37) and `markFailed`
(line 63) both perform the same sequence on a fetched `RetryOperation`:
increment `attemptCount`, set `lastAttemptTime`, recompute
`nextRetryTime` via `RetryOperation.calculateNextRetryTime`, decide
abandon-vs-pending from `attemptCount >= maxAttempts`, and `retryDao.update`.
The abandon/threshold logic is duplicated across the two methods and the
attempt-mutation is written imperatively in the repository. Moving the pure
state transition onto `RetryOperation` (a plain model with no Android imports)
removes the duplication, is unit-testable without a DAO, and keeps that logic
in a KMP-eligible model class.

files:
- `app/src/main/java/org/ole/planet/myplanet/model/RetryOperation.kt`
  — add a `fun applyAttempt(...)` (or two narrow methods) that mutates the
  instance's `attemptCount`/`lastAttemptTime`/`status`/`nextRetryTime`/error
  fields per the existing rules. Keep `calculateNextRetryTime` (line 85) and
  `createFromRetryFailure` (line 55) as-is.
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`
  — in `updateAttempt` (line 37) and `markFailed` (line 63), replace the
  inline mutation with the new model method; keep the `findById`/`update`
  IO in the repo.
- `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`
  — keep existing tests green; optionally add a direct test for the model
  transition.

steps:
1. Add the transition method(s) to `RetryOperation`.
2. Delegate `updateAttempt` and `markFailed` to it.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; a failing upload still
retries, increments attempts, abandons after `maxAttempts`, and recomputes the
backoff time exactly as before.

size budget: ~45 changed lines, 3 files

out of scope: do not change `RetryDao` queries, do not change the public
`RetryRepository` signatures, do not alter backoff constants.

---

### 9. make ForceSyncPolicy platform-free by replacing java.util.concurrent.TimeUnit with kotlin.time (roadmap 9)

context: `ForceSyncPolicy` (`ui/sync/ForceSyncPolicy.kt`) is an otherwise pure,
well-tested object — no Android imports — except it relies on
`java.util.concurrent.TimeUnit` (line 3) for
`TimeUnit.MILLISECONDS.toDays(...)` (line 27) and the test uses
`TimeUnit.DAYS.toMillis(...)` for fixtures. `java.util.concurrent.TimeUnit` is
JVM-only and blocks moving this sync-cadence policy into a KMP common module.
`kotlin.time.Duration` / `DurationUnit` are common-KMP and express the same
day/millisecond conversions.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/ForceSyncPolicy.kt`
  — replace `TimeUnit.MILLISECONDS.toDays(nowMillis - lastSyncMillis)` with
  the common equivalent using `kotlin.time` (e.g.
  `(nowMillis - lastSyncMillis).milliseconds.inWholeDays`); drop the
  `java.util.concurrent.TimeUnit` import. Keep the function signatures and
  return semantics identical.
- `app/src/test/java/org/ole/planet/myplanet/ui/sync/ForceSyncPolicyTest.kt`
  — replace `TimeUnit.DAYS.toMillis(...)` fixtures with the `kotlin.time`
  equivalent so the test stays common-KMP compatible.

steps:
1. Swap the duration conversions in the policy.
2. Swap the fixture conversions in the test.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; forced-sync threshold
behavior is unchanged (7-day weekly, 30-day monthly, never-synced and
within-window devices are not forced, threshold is inclusive).

size budget: ~15 changed lines, 2 files

out of scope: do not change the policy constants or the `maxDaysForAutoSync`
logic; do not move the file.

---

### 10. consolidate SubmissionDetailViewModel's five derived flows into one UiState (roadmap 3+6+10)

context: `SubmissionDetailViewModel` exposes five separately-`stateIn`'d flows
— `questionAnswers`, `title`, `status`, `date`, `submittedBy` (lines 30-63) —
each a `filterNotNull().map{}.stateIn(...)` chain off the same
`submissionDetailState`. `SubmissionDetailFragment.observeViewModel` (line 47)
then collects all five with five `collectWhenStarted` blocks. For a
Compose/Compose-Multiplatform screen the idiomatic, portable shape is one
hoisted `StateFlow<UiState>` the UI maps in a single pass — fewer flows to
wire, one source of truth, no per-field `filterNotNull` boilerplate.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`
  — define a `data class SubmissionDetailUiState(...)` holding the title,
  status, date string, submittedBy, and questionAnswers (with sensible
  "unknown" defaults); expose a single
  `val uiState: StateFlow<SubmissionDetailUiState> = submissionDetailState
  .map { it?.toUiState() ?: SubmissionDetailUiState() }.stateIn(...)`.
  Remove the five separate flows. Keep the upstream
  `submissionDetailState` definition.
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt`
  — replace the five `collectWhenStarted(viewModel.xxx)` blocks (lines 48-61)
  with one `collectWhenStarted(viewModel.uiState) { state -> ... }` that binds
  all fields.

steps:
1. Add the `SubmissionDetailUiState` and a mapper.
2. Expose `uiState`; remove the five flows.
3. Update the fragment to collect `uiState` once.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the submission detail
screen still shows title, status, date, submitted-by, and the question/answer
list identically.

size budget: ~90 changed lines, 2 files

out of scope: do not change `SubmissionsRepository` (off-limits), do not
introduce a new base class, do not touch the question/answer adapter.

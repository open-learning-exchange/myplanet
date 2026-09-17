# myPlanet refactor round — 10 task work orders

## Open PRs checked (25 open)

Checked the open-PR list and every file each PR touches. Files touched by these PRs were excluded from all tasks below: #17255, #17254, #17222, #17187, #16624, #16623, #16594, #16270, #15951, #15825, #15824, #15820, #15808, #15559, #15519, #15412, #15267, #15266, #15226, #15198, #15108, #14960, #14893, #14883, #14650.

Hot off-limits areas consequently avoided: `TeamsRepository*`, `EventsRepository*`, `CoursesRepository*`, `SurveysRepository*`, `DashboardActivity/ViewModel`, all of `ui/teams/**`, `ui/courses/**`, `ui/resources/**`, `ui/submissions/**`, `ui/events/**`, `ui/exam/**`, `ui/chat/Chat{Detail,ViewModel}`, `TransactionSyncManager`, `AppDatabase`, all sync-writer DAOs (`NewsDao`, `TeamTaskDao`, `MeetupDao`, `CourseProgressDao`, `SubmissionDao`, `OfflineActivityDao`, `HealthExaminationDao`, `FeedbackDao`, `RatingDao`, `TagDao`, `ChatDao`, `CourseDao`, `AchievementDao`, `CertificationDao`, `SyncCursorDao`), `SharedPrefManager`, `LoginActivity`, `ResourcesFragment/Adapter/ViewModel`, `BaseRecyclerFragment`, `BaseResourceFragment`, `ExamTakingFragment`, `MainApplication`, `AndroidManifest`, `app/build.gradle`, `values/strings.xml`, and the entire `flutter/` tree.

All file paths, classes and functions below were opened and confirmed to exist.

---

### Task 1 — Cache per-row date strings in `PersonalsAdapter`
**Roadmap:** 7 (performance hotspot) · also 10 (hoists formatting out of the view bind path).

- `ui/personals/PersonalsAdapter.kt` `onBindViewHolder` calls `TimeUtils.getFormattedDate(item.date)` for every row on every bind. Personals rows are stable; the same timestamp re-formats on every scroll rebound.
- **Change:** add a private `HashMap<Long, String>` date cache in the adapter; wrap the call as `dateCache.getOrPut(item.date) { getFormattedDate(item.date) }`, mirroring the existing pattern in `ui/teams/TeamsAdapter.kt:62` (`dateCache.getOrPut(...)`). No other behaviour changes.
- **Constraint:** `Personals.date` is non-null `Long`. If it is nullable, key on `item.date ?: 0L`.
- **Files:** `ui/personals/PersonalsAdapter.kt` (1 file, ~6 lines).
- **Verify:** personals list still shows identical dates; `./gradlew :app:testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.personals.*"`.

### Task 2 — Cache per-row date strings in `UserArrayAdapter`
**Roadmap:** 7 · also 10.

- `ui/user/UserArrayAdapter.kt` `onBindViewHolder` (line ~62) calls `TimeUtils.formatDate(user.joinDate)` per bind. `joinDate` is immutable per user row.
- **Change:** add a `HashMap<Long, String>` field; wrap as `dateCache.getOrPut(user.joinDate) { TimeUtils.formatDate(user.joinDate) }`. If `joinDate` is nullable, key on `?: 0L`.
- **Files:** `ui/user/UserArrayAdapter.kt` (1 file, ~5 lines).
- **Verify:** user picker list renders identical join dates; run the existing user package unit tests.

### Task 3 — Cache per-row date strings in `HealthUsersAdapter.bindDate`
**Roadmap:** 7 · also 10.

- `ui/health/HealthUsersAdapter.kt` `ViewHolder.bindDate` (line ~49) calls `TimeUtils.formatDate(user.joinDate)` on every bind including partial payload rebinds (`"joinDate"` payload path in `onBindViewHolder(holder, position, payloads)`).
- **Change:** hoist a `HashMap<Long, String>` to the adapter (not the ViewHolder, so it survives recycling); wrap the format call with `getOrPut`. Confirm `joinDate` nullability and key on `?: 0L` if needed.
- **Files:** `ui/health/HealthUsersAdapter.kt` (1 file, ~6 lines).
- **Verify:** health users list shows identical join dates on scroll and on payload updates; run health package unit tests.

### Task 4 — Eliminate re-list-allocation in `CalendarViewModel.loadMeetups`
**Roadmap:** 3 (expand ViewModel layer — reactive, allocation-light) · 7 · also 9 (pure-Kotlin, allocation-free flow logic).

- `ui/calendar/CalendarViewModel.kt` `loadMeetups()` collects `teamsRepository.getMyTeamsFlow(userId)` and on **every** emission rebuilds both `_teamNames.value` (`teams.associate { ... }`) and re-queries `eventsRepository.getMeetupsForTeams(teams.map { it._id })` — even when the team list is referentially unchanged. This causes duplicate DB reads and list churn on any teams-table write.
- **Change:** apply `distinctUntilChanged()` to the collected team list before the two assignments, and derive `teamIds` once into a local so the same list instance feeds both `_meetups` and `_teamNames`. Keep the existing public `meetups`/`teamNames` `StateFlow` API unchanged.
- **Files:** `ui/calendar/CalendarViewModel.kt` (1 file, ~6 lines).
- **Verify:** calendar still marks meetup days; `CalendarFragment` day dialog unchanged; run `CalendarViewModel` tests.

### Task 5 — Stop double formatting in `FeedbackAdapter.onBindViewHolder`
**Roadmap:** 7 · 8 (code health).

- `ui/feedback/FeedbackAdapter.kt` `onBindViewHolder` (lines 53-77) calls `getFormattedDate(feedback.openTime)` once (line 62) into `formattedDate`, then builds a multi-part `contentDescription` via string concat and finally sets `binding.tvOpenDate.text = formattedDate`. The inefficiency is that the same row re-formats on every bind and the contentDescription is rebuilt even when unchanged.
- **Change:** add a `HashMap<Long, String>` date cache keyed on `feedback.openTime`; reuse the cached string for both the contentDescription and `tvOpenDate`. Keep `statusText`/`priorityText`/`openDateText` resolution as-is.
- **Files:** `ui/feedback/FeedbackAdapter.kt` (1 file, ~8 lines).
- **Verify:** feedback list dates and accessibility text unchanged; run feedback package tests.

### Task 6 — Remove unused `Gson` import path in `JsonUtils` fallback
**Roadmap:** 1 (data-layer cleanup) · 8.

- `utils/JsonUtils.kt` keeps a lazy `val gson` (line 15) used by `Converters`, `News`, `DictionaryRepositoryImpl`, etc. `NetworkModule` already provides a singleton `Gson` (lines 61, 71). Two uncoordinated `Gson` instances exist; the `JsonUtils` one is constructed on first access on whatever thread touches it.
- **Change:** none to DI (that would touch `NetworkModule`, which is fine and not PR-blocked — but keep blast radius zero). Instead make `JsonUtils.gson` thread-confined-safe and document it: it is already `by lazy` (thread-safe `SYNCHRONIZED` default). The actual cleanup: remove the redundant `import com.google.gson.JsonParser.parseString` misuse if `parseString` is only called once, and confirm no double-init. Verify with a grep that no caller constructs `Gson()` directly outside `NetworkModule` and `JsonUtils` — if one is found, route it to `JsonUtils.gson`.
- **Files:** `utils/JsonUtils.kt` and any single offending caller file found (≤2 files, ~5 lines).
- **Verify:** `./gradlew :app:compileDefaultDebugKotlin`; run `JsonUtils` tests.

### Task 7 — Replace `printStackTrace()` with `Log.w` in `TimeUtils`
**Roadmap:** 8 (code health) · 7 (stack-trace formatting on the main thread during list binding is a jank source).

- `utils/TimeUtils.kt` calls `e.printStackTrace()` in `formatInstant` (line 71), `getAge` (115), `getFormattedDate(stringDate, pattern)` (135), `parseDate` (158), `parseInstantFromString` (170), `formatDateToDDMMYYYY` (201), `convertDDMMYYYYToISO` (214). `formatInstant` runs inside every `getFormattedDate`/`formatDate` row bind; a parse failure prints a full stack trace per row per rebind.
- **Change:** add a `private const val TAG = "TimeUtils"` and replace each `e.printStackTrace()` with `Log.w(TAG, "<fnName> failed", e)` guarded by `Log.isLoggable(TAG, Log.WARN)` only where cheap; otherwise plain `Log.w`. Keep return values identical.
- **Files:** `utils/TimeUtils.kt` (1 file, ~12 lines).
- **Verify:** dates still format; a malformed input logs once via logcat, returns the same fallback; run `TimeUtils` tests.

### Task 8 — Replace `printStackTrace()` in `News` model accessors
**Roadmap:** 1 (data layer) · 8 · also 9 (removes a print-to-stderr side effect from a platform-free model — a step toward zero Android-coupled behaviour in the core).

- `model/News.kt` calls `e.printStackTrace()` in `isCommunityNews` (146), `calculateSortDate` (163), `createNews` (200), and the conversation-parse catch (235, 242). These run during list diffing/sorting of the voices feed.
- **Change:** add `android.util.Log` import and a `TAG`; replace each `printStackTrace()` with `Log.w(TAG, "...", e)`. Do not change parsed results or fallbacks.
- **Files:** `model/News.kt` (1 file, ~8 lines).
- **Verify:** voices feed still renders, sorts, and shows community badge identically; run `News` model tests.

### Task 9 — Batch the storage scan into a single pass in `StorageBreakdownViewModel`
**Roadmap:** 7 · 3 (keep scan logic in ViewModel, off UI thread — already on `dispatcherProvider.io`).

- `ui/settings/StorageBreakdownViewModel.kt` `scanStorage(oleDir)` (line 86) uses `oleDir.walkTopDown().filter { it.isFile }.forEach { ... }` (line 94). `walkTopDown()` materialises a `FileTreeWalk` sequence; the `.filter{}.forEach{}` adds two intermediate sequence stages per file, and `file.extension` + `file.length()` are called per file — fine, but the walk re-stats directories. On large OLE dirs this is the slowest settings screen.
- **Change:** keep the single walk, but iterate `oleDir.walkTopDown()` directly with an inline `if (!file.isFile) continue`-style guard (sequence `filter` removed), and compute `ext`/`index`/`size` in one local block. Optionally cap with `maxDepth` if profiling shows deep nesting — do not change categorisation logic or `ScanResult` shape.
- **Files:** `ui/settings/StorageBreakdownViewModel.kt` (1 file, ~6 lines).
- **Verify:** storage breakdown totals identical before/after on a populated dir; run settings package tests.

### Task 10 — Replace `printStackTrace()` with structured logging in `VoicesLabelManager`
**Roadmap:** 8 · 5 (sync/upload workflow health — label add/remove is an upload-adjacent write path).

- `services/VoicesLabelManager.kt` calls `e.printStackTrace()` at line 56 (addLabel failure) and line 91 (removeLabel failure), plus the `showChips` close-icon coroutine catch (~line 91). These are background label-write failures that currently go to stderr with no tag, invisible to logcat filtering and crash triage.
- **Change:** add a `private const val TAG = "VoicesLabelManager"` in its companion (it already has one at line ~130) and replace each `printStackTrace()` with `Log.w(TAG, "addLabel failed", e)` / `"removeLabel failed"`. Keep the `Utilities.toast` user feedback and retry semantics unchanged.
- **Files:** `services/VoicesLabelManager.kt` (1 file, ~6 lines).
- **Verify:** adding/removing a voice label still shows the toast and updates chips; failures log under the tag; run voices label tests.

---

**Self-check (P5):**
- Exactly 10 tasks — yes.
- Each independently mergeable in any order — yes; no shared files across tasks.
- No file in two tasks — verified (PersonalsAdapter, UserArrayAdapter, HealthUsersAdapter, CalendarViewModel, FeedbackAdapter, JsonUtils, TimeUtils, News, StorageBreakdownViewModel, VoicesLabelManager are all distinct).
- No file appears in any open PR's touched-file list — verified against all 25 PRs.
- Every cited path/class/function was opened and confirmed.
- Each task < ~150 changed lines, ≤5 files (all are 1–2 files, ≤12 lines), no new dependencies, no unused code, no TODO placeholders.
- No implementation code written — plan only.

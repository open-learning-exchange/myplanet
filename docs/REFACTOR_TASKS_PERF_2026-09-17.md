# myPlanet refactor round — performance quick wins

**date:** 2026-09-17
**base commit:** `fcb4c53b2d1c4134789f1c07bd785d97d7f4215b` (`teams: smoother events repository calendar view modelling (fixes #17217) (#17218)`)
**open PRs checked:** 17255, 17254, 17222, 17187, 16624, 16623, 16594, 16270, 15951, 15825, 15824, 15820, 15808, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075
**method:** every open PR head branch was fetched and diffed against its merge base with `master`; the union (1184 paths) was treated as off-limits. No task below touches any of those paths. PRs carrying `review` / `ready` labels (17254, 17222, 17187) are fully excluded, as are all others.
**focus:** performance quick wins · micro-optimizations that unblock bigger refactors · obvious inefficiencies removable without rewrites

---

### 1. drop the write-only detailed-log accumulator in SyncTimeLogger (roadmap 5+8)

context: `SyncTimeLogger.kt:38` declares `private val detailedLogs = ConcurrentHashMap<String, MutableList<String>>()`, `logDetail` appends to it at `SyncTimeLogger.kt:195`, and `startLogging` clears it at `SyncTimeLogger.kt:73` — but `generateSummary()` never reads it, so nothing in the app ever consumes those strings. There are 12 `logDetail(...)` call sites, several inside per-batch loops, so a large sync accumulates thousands of retained strings that are dead weight for the whole session. Separately, `apiCallTimes` (`SyncTimeLogger.kt:36`) and `dbOperationTimes` (`SyncTimeLogger.kt:37`) hold plain `ArrayList`s that are appended from parallel sync coroutines (library sync runs 6 concurrent shelf jobs), so `getOrPut { mutableListOf() }.add(log)` at lines 167 and 184 races.

files: `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` — the `detailedLogs` field (line 38), its `clear()` in `startLogging` (line 73), the body of `logDetail` (lines 192-201), and the two `getOrPut` sites (lines 167, 184). Do NOT change the `logDetail(context: String, message: String)` signature — its 12 call sites live in `SyncManager.kt` and `SyncRepositoryImpl.kt`, which task 2 and other PRs own. Do NOT touch `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt`; the change is behaviour-preserving for it.

steps:
1. Delete the `detailedLogs` field and its `detailedLogs.clear()` line in `startLogging`.
2. In `logDetail`, keep the `if (!isLogging) return` guard and the verbose `Log.d` branch, and remove the map append — the function becomes verbose-logging only.
3. Replace the `mutableListOf()` defaults at lines 167 and 184 with a thread-safe list (`java.util.Collections.synchronizedList(mutableListOf())`), and wrap the iterations over those lists inside `generateSummary()` that read them (`sumOf`, `count`, `sortedByDescending`) in `synchronized(logs) { ... }` so the summary pass cannot race an in-flight append.
4. Remove any import that becomes unused.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green (`SyncTimeLoggerTest` in particular, unmodified). Trigger a full sync from the dashboard: it completes, and the "sync summary" row written to Room by `saveSummaryToRoom` still contains the `PROCESS BREAKDOWN`, `API CALL STATISTICS`, `DB OPERATION STATISTICS` and `PERFORMANCE INSIGHTS` sections with the same shape as before.

size budget: ~20 changed lines, 1 file

out of scope: no changes to any `logDetail` caller, no new logging sink, no change to what `generateSummary` prints.

---

### 2. stop re-filtering the resource id list during sync cleanup (roadmap 5+7)

context: `SyncManager.kt:294` declares `val newIds: MutableList<String?> = ArrayList()`, but the only thing ever added to it is `validIds` at line 376, which is already `savedIds.filter { it.isNotBlank() }` — every element is a non-blank `String`. The cleanup block then re-walks that whole list at `SyncManager.kt:411` (`newIds.filter { !it.isNullOrBlank() }`), allocating a second copy of every resource id on a server that can hold tens of thousands of resources, and guards on `validNewIds.size == newIds.size` (line 414), which is always true. `ResourcesRepository.removeDeletedResources(currentIds: List<String?>)` accepts a `List<String>` unchanged, so no repository edit is needed.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` — `resourceTransactionSync()` only (lines 294, 411-420). Do NOT touch `ResourcesRepository.kt` / `ResourcesRepositoryImpl.kt` (an open PR owns the impl), and do NOT touch `SyncTimeLogger.kt` (task 1 owns it).

steps:
1. Change line 294 to `val newIds: MutableList<String> = ArrayList()`.
2. Delete the `validNewIds` local at line 411 and pass `newIds` straight to `resourcesRepository.removeDeletedResources(...)`.
3. Reduce the guard at line 414 to `newIds.isNotEmpty()`, keeping the `hadBatchFailure` branch above it exactly as it is.
4. Change the `logDbOperation("delete_cleanup", "resources", cleanupDuration, ...)` item count at line 420 from `newIds.size - validNewIds.size` (always 0) to `newIds.size`.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green (`SyncManagerTest` included). Run a full sync against a server where resources have been deleted: the deleted resources disappear locally exactly as before, and a sync whose batches partially failed still skips the delete-cleanup.

size budget: ~8 changed lines, 1 file

out of scope: no change to batch sizing, checkpointing, or the `hadBatchFailure` semantics; no DAO or repository changes.

---

### 3. drop redundant case-insensitive matching from chat search (roadmap 7+9)

context: `ChatSearch` normalizes every candidate string through `Utilities.normalizeText`, which lowercases with `str.lowercase(Locale.getDefault())` before stripping diacritics (`Utilities.kt:80-86`), and it normalizes the query the same way (`ChatSearch.kt:50`, `ChatSearch.kt:91`). Both sides are therefore already lowercase, yet all four comparisons still pass `ignoreCase = true` (`ChatSearch.kt:60`, `63`, `97`, `99`), which forces Java's case-folding `regionMatches` path instead of the fast identity compare — on every keystroke, across every conversation turn of every chat. `ResourcesSearchUtils.searchList` already documents and uses the pre-normalized, no-`ignoreCase` convention.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt` — `fullConvoSearch` (lines 60, 63) and `searchByTitle` (lines 97, 99). Do NOT touch `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt` or `ui/chat/ChatViewModel.kt` — open PRs own both, so do not refactor `ChatSearch` to delegate to `searchList`.

steps:
1. Remove `ignoreCase = true` from the two `startsWith` calls (lines 60 and 97).
2. Remove `ignoreCase = true` from the two `contains` calls (lines 63 and 99).
3. Add a one-line comment above each loop noting that both sides come from `Utilities.normalizeText`, matching the comment style in `ResourcesSearchUtils`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `ChatSearchTest` in particular. In the app, open Chat history and search in all three modes (title, question, response) using mixed-case and accented queries: the same chats are returned, in the same order (starts-with matches before contains matches).

size budget: ~6 changed lines, 1 file

out of scope: no change to `Utilities.normalizeText`, no caching of normalized conversations, no change to the result ordering.

---

### 4. cache the HTML cover-image directory scan in FileUtils (roadmap 7)

context: `FileUtils.findHtmlCoverImage(resourceDir)` (`FileUtils.kt:142-163`) walks a resource directory to depth 4, stats every file (`file.length()` at line 156) and lowercases every name, with no memoization. It is called from `InlineResourceAdapter.kt:254` and `ResourcesAdapter.kt:413` — i.e. once per bind of every HTML resource row, so scrolling a list of HTML resources re-walks the same directories repeatedly. The same file already ships `FileExistenceCache` (`FileUtils.kt:381-406`) and `PdfThumbnailLoader` already caches its per-file result in an `android.util.LruCache`, so the pattern is established.

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` — the `findHtmlCoverImage` function and the two private constants above it (lines 139-163). Do NOT touch `ui/resources/ResourcesAdapter.kt` or `ui/courses/InlineResourceAdapter.kt` (an open PR owns `ResourcesAdapter.kt`), and do NOT modify `FileExistenceCache` (task 7's adapters instantiate it).

steps:
1. Add a private `android.util.LruCache<String, java.util.Optional<File>>` (or a small `LruCache<String, File>` plus a separate negative-result marker — pick one and keep it consistent) sized around 64 entries, declared next to `previewImageExtensions`.
2. Key the cache on `resourceDir.absolutePath + "_" + resourceDir.lastModified()` so a re-downloaded resource misses the cache.
3. At the top of `findHtmlCoverImage`, return the cached value when present; at each `return` site, store the result (including the `null` result) before returning.
4. Keep the existing early `if (!resourceDir.isDirectory) return null` uncached.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `FileUtilsTest` in particular. In the app, open Resources filtered to HTML items and scroll the list up and down several times: the same cover thumbnails appear, and scrolling back over already-seen rows no longer re-scans (verify by scrolling a list of 20+ HTML resources and observing no new jank on the second pass).

size budget: ~25 changed lines, 1 file

out of scope: no change to which image is chosen (name hints first, then largest file), no change to the two adapters, no new caching library.

---

### 5. reuse the existing file-existence cache for course cover art (roadmap 7)

context: `CoursesItemUtils.bindCover` builds a cover `File` at `CoursesItemUtils.kt:59` and immediately calls `coverFile?.exists()` at line 60 — a disk stat executed synchronously on the main thread inside `onBindViewHolder`, for every course row, on every rebind. `bindCover` is called from `TeamCoursesAdapter.kt:45` and from both view modes of `CoursesAdapter.kt` (lines 331 and 379), so a fling through a course grid issues one stat syscall per visible row per frame. `FileExistenceCache` (`FileUtils.kt:381`, 5 s TTL) already exists for exactly this and is used by `EnterprisesReportsAdapter.kt:110`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/CoursesItemUtils.kt` — the object header (line 17) and `bindCover` (lines 49-60). Do NOT touch `ui/courses/CoursesAdapter.kt` (an open PR owns it), do NOT touch `utils/FileUtils.kt` (task 4 owns it), and do NOT change `bindCover`'s signature — both callers must keep compiling untouched.

steps:
1. Add `private val coverExistenceCache = FileExistenceCache()` and `private val timeProvider: TimeProvider = SystemTimeProvider()` as private members of the `CoursesItemUtils` object.
2. Replace `coverFile?.exists() == true` at line 60 with `coverExistenceCache.exists(coverFile, timeProvider.now())`.
3. Add the two imports (`FileExistenceCache` and `SystemTimeProvider` / `TimeProvider` are in the same `utils` package, so no import is needed — confirm and add none).
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green (`CoursesAdapterTest` included). In the app, open My Courses in both list and grid view: courses with a downloaded cover still show the cover image, courses without one still show the subject icon, and a course whose cover finishes downloading shows the image within ~5 s (the cache TTL) or on the next list reload.

size budget: ~6 changed lines, 1 file

out of scope: no change to the Glide request (sizing, signature, cache strategy), no move of the stat onto a background dispatcher, no change to `MyCourse.getCoverImageFile`.

---

### 6. share one OkHttp connection pool between the two HTTP clients (roadmap 4+7)

context: `NetworkModule.buildOkHttpClient` (`NetworkModule.kt:77-93`) constructs a fresh `ConnectionPool(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)` on line 81 for every client it builds, and it is called twice — once for `@StandardHttpClient` (line 100) and once for `@ReachabilityHttpClient` (line 112). Both clients talk to the same Planet/CouchDB host, so every reachability probe opens a brand-new TCP+TLS connection even though the sync client already holds a warm, idle one to that host. Sharing the pool is the documented OkHttp practice and removes a full handshake from each probe.

files: `app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt` — the `MAX_REQUESTS_PER_HOST` constant (line 75), `buildOkHttpClient` (lines 77-93) and the two `@Provides` client functions (lines 98-117). Do NOT touch `data/api/ApiClient.kt`, `data/api/RetryInterceptor.kt`, or `utils/ServerReachabilityProvider.kt`.

steps:
1. Hoist the `ConnectionPool` out of `buildOkHttpClient` into a single `private val` (or a `@Provides @Singleton` function) in the `NetworkModule` object, keeping the same `(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)` arguments.
2. Have `buildOkHttpClient` pass that shared instance to `.connectionPool(...)`.
3. Leave each client's own `Dispatcher` in place — sharing the dispatcher would queue reachability probes behind sync traffic, which is not wanted.
4. Leave the three timeout constant sets and the `TaggedSocketFactory` untouched.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `di/NetworkModuleTest` in particular. In the app: sign in and run a manual sync (standard client path), then let the app fall back to an alternative server URL (reachability client path) — both still work, and the "server unreachable" fallback still switches URLs.

size budget: ~10 changed lines, 1 file

out of scope: no timeout tuning, no interceptor added or removed, no shared `Dispatcher`, no new dependency.

---

### 7. hoist constant string lookups out of adapter bind paths (roadmap 7)

context: three list adapters resolve a string resource that never varies per row, once per `onBindViewHolder` call. `MembersAdapter.kt:121` (and again at line 77 in the payload path) calls `context.getString(R.string.team_leader)`, and line 111 calls `context.getString(R.string.no_visit)`; `TeamCoursesAdapter.kt:62` calls `context.getString(R.string.remove)`; `EnterprisesReportsAdapter.kt:65` calls `context.getString(R.string.team_financial_report, teamName)` where `teamName` is a constructor parameter (line 22) and so is fixed for the adapter's lifetime. Each call walks the resource table and allocates a new `String` on every bind. `TeamsSelectionAdapter.kt:24,33` already caches exactly this way, so the fix matches existing house style.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt` (lines 77, 111, 121)
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt` (line 62)
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt` (line 65)

Do NOT touch `ui/teams/TeamsSelectionAdapter.kt` (it is the reference, already done), and do NOT touch `EnterprisesReportsAdapter`'s `attachmentPresenceCache` / image binding (lines 28-35, 100-115).

steps:
1. In `MembersAdapter`, add `private val leaderLabel: String by lazy { context.getString(R.string.team_leader) }` and `private val noVisitLabel: String by lazy { context.getString(R.string.no_visit) }` next to the existing `avatarSize` lazy field (line 29), and use them at lines 77, 111 and 121.
2. In `TeamCoursesAdapter`, add a `private val removeLabel: String by lazy { context.getString(R.string.remove) }` and use it at line 62.
3. In `EnterprisesReportsAdapter`, add a `private val reportTitle: String by lazy { context.getString(R.string.team_financial_report, teamName) }` and assign it at line 65.
4. Leave every per-row `getString(...)` that interpolates row data exactly as it is.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green (`MembersAdapterTest` and `EnterprisesReportsAdapterTest` included). In the app: the team Members list still shows the "Team leader" badge on leaders and "no visit" for members who never visited; the team Courses list still exposes the remove affordance's content description; the Enterprises reports list still titles each card with the team name.

size budget: ~15 changed lines, 3 files

out of scope: no layout XML changes, no DiffUtil changes, no change to the payload-based partial bind logic.

---

### 8. compute the storage selection counters in one pass (roadmap 3+7+10)

context: `StorageCategoryUiState` exposes `checkedCount` as a computed getter that scans the whole list (`StorageCategoryViewModel.kt:25`) and `allChecked` as a second getter that calls `checkedCount` and scans again (line 26). `StorageCategoryDetailFragment.kt:142-149` reads both on every state emission, so each UI update walks the item list twice; a storage category on a well-used device holds hundreds to thousands of grouped resources, and `toggleItemChecked` (line 61) emits a new state on every single checkbox tap. Hoisting the counter into a stored property makes it one pass per state, computed where the list is already being built.

files: `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt` — the `StorageCategoryUiState` data class (lines 19-27) and the `_uiState.update { ... }` blocks in `loadResources`, `toggleItemChecked` and `toggleAllChecked` (lines 42-73). Do NOT touch `ui/settings/StorageCategoryDetailFragment.kt` — `state.checkedCount` and `state.allChecked` must keep the same names and types so the fragment compiles unchanged. Do NOT touch `StorageBreakdownViewModel.kt` or `StorageCategories.kt`.

steps:
1. Add `val checkedCount: Int = 0` as a real constructor property of `StorageCategoryUiState`, replacing the computed getter at line 25.
2. Redefine `allChecked` as `val allChecked: Boolean get() = checkedCount == items.size && items.isNotEmpty()` — one comparison, no second scan.
3. In `loadResources`, set `checkedCount = 0` alongside the loaded items (freshly loaded items are unchecked).
4. In `toggleItemChecked` and `toggleAllChecked`, derive the new `checkedCount` from the single `map` already being performed (increment while mapping, or count the produced list once) and pass it into `state.copy(...)`.
5. Leave `deleteSelected` (line 77) and `deleteAll` as they are.
6. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `ui/settings/StorageCategoryViewModelTest` in particular. In the app: Settings → storage breakdown → open a category; tick and untick individual items and use select-all — the "N selected" label, the select-all checkbox state, and the enabled/disabled state of the delete button all track exactly as before, and deleting the selection still removes the right files.

size budget: ~20 changed lines, 1 file

out of scope: no change to the deletion logic, no change to the fragment, no change to what `getOfflineResourceItems` returns.

---

### 9. read each progress JSON field once (roadmap 3+7)

context: `ProgressViewModel.loadCourseData` parses the course-progress payload row by row and looks the same members up twice. `ProgressViewModel.kt:40-41` calls `obj.has("stepMistake")` and then `obj.get("stepMistake")` — two hash lookups for one value — and lines 49-50 call `obj.getAsJsonObject("progress")` twice, allocating and re-resolving the nested object for `current` and then again for `max`. This runs for every enrolled course every time the progress screen is opened, on `dispatcherProvider.default`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt` — the `jsonArray?.map { ... }` block only (lines 36-54). Do NOT touch `repository/ProgressRepository*` or `model/CoursesProgressRow.kt`, and do NOT touch `ui/courses/CoursesProgressAdapter.kt`.

steps:
1. Replace the `obj.has("stepMistake")` / `obj.get("stepMistake")` pair with a single `obj.get("stepMistake")` into a local, then map it through `gson.fromJson(..., type)` only when it is non-null and not `JsonNull`.
2. Hoist `obj.getAsJsonObject("progress")` into one local and read `current` and `max` off it.
3. Keep `courseId`, `courseName` and `mistakes` reading exactly as they do today, including the existing null handling.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `ui/courses/ProgressViewModelTest` in particular. In the app, open a member's course-progress screen: every enrolled course still lists with the same step progress ("x / y"), the same mistake count, and rows with no `stepMistake` or no `progress` object still render without crashing.

size budget: ~12 changed lines, 1 file

out of scope: no change to `CoursesProgressRow`, no move of the parsing into the repository, no new DTO.

---

### 10. replace the regex split and no-op trim in the health examination list (roadmap 7+8)

context: `HealthExaminationAdapter.kt:69` derives a fallback display name with `createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1)` — a regex split on a literal `":"` plus a list, a `dropLastWhile` copy and an array copy, to obtain one substring; the lazily compiled `colonRegex` at line 176 exists solely for this. Separately, `HealthExaminationAdapter.kt:104` appends `.trimIndent()` to `context.getString(R.string.two_strings, ...)`, and `two_strings` is `"%1$s %2$s"` (`values/strings.xml:1028`) — a single-line string, so `trimIndent()` splits, inspects and rejoins lines to produce the identical string on every single bind.

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt` — the `displayNameCache.getOrPut` body (line 69), the `colonRegex` declaration (line 176), and the `txtDate` assignment in `onBindViewHolder` (line 104). Do NOT touch the `.trimIndent()` at line 140 — that one formats the genuinely multi-line `vitals_format` string in the detail dialog. Do NOT touch `ui/health/HealthViewModel.kt` or `HealthUsersAdapter.kt`.

steps:
1. Replace the split chain at line 69 with `createdBy.substringAfter(':', "").takeIf { it.isNotBlank() }`, keeping the surrounding `model?.getFullName() ?: ... ?: createdBy` fallback chain and the `displayNameCache` memoization exactly as they are.
2. Delete the now-unused `colonRegex` declaration at line 176.
3. Remove the `.trimIndent()` at line 104.
4. Remove any import that becomes unused.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. In the app, open My Health → examinations for a user with records created by someone else: rows created by another user still show "<date> <name>" with the grey background, self-examinations still show the self-examination label with the green background, and a record whose `createdBy` is an unresolvable `id:name` string still falls back to the name after the colon.

size budget: ~8 changed lines, 1 file

out of scope: no change to the diff callback, no change to the examination detail dialog, no change to how `encrypted` data is read.

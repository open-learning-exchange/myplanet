# myPlanet refactor round — performance quick wins

**Open PRs checked (31):** #17435, #17430, #17356, #17254, #17187, #16624, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

**Lock set:** 1032 unique paths from those PRs (via GitHub MCP `get_files`). Every path below was verified present on disk and absent from that lock set.

**Focus:** roadmap **#7** (performance hotspots / micro-opts). Notes where work also helps **#5**, **#9**, or **#10**.

**Rules:** exactly 10 independent tasks · no shared files · ≤~5 files · ≤~150 LOC · no new deps · no TODOs · plan only.

---

### Task 1 — Cap Glide decode size in finance row thumbnails

**Roadmap:** 7  
**Also advances 9/10:** no

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapterTest.kt` (extend only if needed)

**Problem:**  
`bindFinanceImage` loads attachment files with Glide into `financeImage` (layout size **64dp × 64dp** in `row_finance.xml`) without `.override(...)`, `.centerCrop()`, or `DiskCacheStrategy`. Full-resolution bitmaps are decoded on every bind/scroll.

**Required changes:**
- In `bindFinanceImage`, after `.load(imageFile)`, add `.override(sizePx, sizePx)`, `.centerCrop()`, and `.diskCacheStrategy(DiskCacheStrategy.RESOURCE)` (or `ALL`).
- Resolve `sizePx` once per bind from `binding.financeImage.layoutParams` / measured size, falling back to `64dp` via `resources.displayMetrics.density` (do not change XML).
- Keep existing clear-on-recycle in `onViewRecycled` and click-to-zoom behavior.

**Acceptance criteria:**
- Bound list images decode at ~display size, not full file resolution.
- Zoom click still opens full path via `ImageViewerUtils.showZoomableImage`.
- Missing/nonexistent attachments still hide the `ImageView`.

**Out of scope:** `FileExistenceCache` / `FileUtils.kt`; reports adapter; layout XML.

**Test notes:** Existing `EnterprisesFinancesAdapterTest` must still pass. Prefer a unit assertion on Glide request options only if the test harness already stubs Glide; otherwise manual scroll of finance list with large attachments.

**Constraints:** R5; no new libraries.

---

### Task 2 — Cap Glide decode size in report row thumbnails

**Roadmap:** 7  
**Also advances 9/10:** no

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapterTest.kt` (extend only if needed)

**Problem:**  
`bindReportImage` loads into `reportImage` (**120dp × 120dp** in `report_list_item.xml`) with the same full-decode Glide chain as finances.

**Required changes:**
- Mirror Task 1 pattern in `bindReportImage`: `.override(sizePx, sizePx)`, `.centerCrop()`, `.diskCacheStrategy(DiskCacheStrategy.RESOURCE)` (or `ALL`).
- Fallback size = 120dp density-scaled; do not edit layout XML.
- Preserve `onViewRecycled` Glide clear and zoom click handler.

**Acceptance criteria:**
- Report list scroll no longer decodes full attachment bitmaps into 120dp views.
- Non-member / no-image paths unchanged.

**Out of scope:** Finances adapter; `FileUtils` / `FileExistenceCache`.

**Test notes:** Keep `EnterprisesReportsAdapterTest` green.

**Constraints:** R5; independent of Task 1 (separate file).

---

### Task 3 — Thread-safe PDF thumbnail cache with eviction recycle

**Roadmap:** 7  
**Also advances 9/10:** no (Android `Bitmap` / `PdfRenderer` stay platform-bound)

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`
- optional new: `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` (no test file exists today)

**Problem:**  
`firstPageBitmap` does `cache.get` **outside** `withContext`, then renders on IO, then `cache.put` via `?.also`. Concurrent callers for the same key can double-render. `LruCache` has no `entryRemoved` recycle, so evicted bitmaps hold native memory until GC.

**Required changes:**
- After IO render, re-check cache under a single lock (or `synchronized(cache)`) and return the existing entry if another coroutine won the race; only then `put`.
- Override `entryRemoved` to `recycle()` non-recycled bitmaps when `evicted == true` (and not the same instance still held).
- Keep public API: `firstPageBitmap(file, dispatcherProvider, targetWidthPx)` and `evictAll()`.
- Do not change callers.

**Acceptance criteria:**
- Same `cacheKey` concurrent loads produce one render path and one cached bitmap.
- Eviction / `evictAll` recycles bitmaps without use-after-recycle for in-flight UI (return copies only if needed; prefer holding cache as source of truth for list thumbs).
- `targetWidthPx <= 0` still returns null.

**Out of scope:** `ResourceViewerFragment` PDF viewer; Glide; adding disk cache.

**Test notes:** Robolectric/unit test with a tiny PDF fixture if available; otherwise mock-free logic test of cache hit after put. Keep change under ~80 LOC.

**Constraints:** R5.

---

### Task 4 — Downscale first-page PDF preview in resource viewer

**Roadmap:** 7  
**Also advances 9/10:** no (View-based UI; #10 only later when this screen is Compose)

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`

**Problem:**  
`renderPdf()` (`setupPdfViewer` path) creates `createBitmap(page.width, page.height)` at native PDF page pixels, then shows it in an `ImageView` with `FIT_CENTER`. Large PDFs allocate multi‑MB bitmaps and risk OOM / jank on open.

**Required changes:**
- Inside the existing `withContext(dispatcherProvider.io)` block in `renderPdf`, scale like `PdfThumbnailLoader`: compute `targetWidth` from `resources.displayMetrics.widthPixels` (or parent width if already laid out), scale height proportionally, `eraseColor` white, render into the scaled bitmap.
- Keep `ParcelFileDescriptor` / `PdfRenderer` use-blocks and error logging.
- Do not change `extractPdfText`, FAB actions, or non-PDF branches.

**Acceptance criteria:**
- Opening a large offline PDF shows page 0 without full native-resolution bitmap.
- Missing file still no-ops; render failures still log and leave placeholder.

**Out of scope:** Shared refactor onto `PdfThumbnailLoader` (that file is Task 3’s exclusive surface); full multi-page viewer; Compose migration.

**Test notes:** No dedicated fragment test; smoke open a PDF resource. `ResourceViewerViewModelTest` unaffected if ViewModel untouched.

**Constraints:** R5; single file.

---

### Task 5 — Avoid full-library title map on offline storage listing

**Roadmap:** 7  
**Also advances 9/10:** yes for **#9** — keeps title lookup in repository/DAO (platform-free query surface)

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` (or existing storage-related tests only if already covering these methods)

**Problem:**  
`getOfflineResourceItems` always calls `getResourceTitlesMap()` → `myLibraryDao.getResourceTitles()` (`SELECT resourceId, title FROM my_library WHERE resourceId IS NOT NULL`), loading **every** library title before walking OLE files. Devices with large catalogs pay a full table read to label a small offline subset.  
`getStorageBreakdown` walks with `.filter { it.isFile }.forEach` (extra intermediate sequence) and calls `StorageCategoryType.indexOf(file.extension)` which lowercases again (acceptable; optional micro-trim only inside this task’s walk loop by lowercasing once locally—do **not** edit `StorageCategoryType.kt`).

**Required changes:**
- Add a DAO query, e.g. `getResourceTitlesByIds(ids: List<String>): List<ResourceTitleProjection>` with `WHERE resourceId IN (:ids)` (chunk if you must stay under SQLite variable limits; reuse existing chunk patterns in this repo if any).
- In `getOfflineResourceItems`: walk/group first; if `grouped` is empty return empty; else fetch titles only for `grouped.keys`; keep sort-by-title behavior and unknown-title string.
- Optionally tighten `getStorageBreakdown` walk to `for (file in oleDir.walkTopDown()) { if (!file.isFile) continue; ... }` without changing breakdown math.
- Leave `getResourceTitlesMap()` API intact for other callers.

**Acceptance criteria:**
- Offline category detail with N on-disk resource folders issues title queries proportional to N, not full table size.
- Breakdown totals/counts unchanged for a fixed fixture tree.
- Empty / missing `oleDir` still returns empty / zero breakdown.

**Out of scope:** UI storage fragments; deleting files; `FreeSpaceWorker`; interface changes beyond what Impl needs (prefer private DAO use).

**Test notes:** Extend `ResourcesRepositoryImplTest` with a fake DAO or in-memory Room if the suite already does; assert DAO method not called with empty groups / called with discovered ids only.

**Constraints:** R5; no new deps; MyLibraryDao only in this task.

---

### Task 6 — Cut LifeCache read copies and blocking preference writes

**Roadmap:** 7  
**Also advances 9/10:** partial **#9** — cache shape stays pure data; `SharedPreferences` remains Android until a later prefs abstraction

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`

**Problem:**  
`read` always does `cached.map { it.copy() }` on hit and after parse (`LifeCache.kt` lines 27–37), allocating a full list of copies for a small Life menu. `write` stores then `preferences.edit { putString(... gson.toJson ...) }` synchronously on the caller’s thread (often main via repository).

**Required changes:**
- Make `CachedMyLifeItem` fields `val` (data class already) so stored lists are immutable-by-convention; return the stored list (or an unmodifiable wrapper) without per-element `copy()` **or** copy only if tests prove callers mutate cache entries.
- If mutation safety is still required, copy once into memory on parse/write and return that list without a second map-copy on every read.
- On `write`, keep memory update immediate; persist JSON with `edit(commit = false)` / apply semantics already used by `edit { }` (confirm not forcing `commit()`); do not introduce coroutines/deps—ok to keep sync apply if documented as cheap for tiny payloads, but avoid double `toJson` and redundant work.
- Preserve key prefix `myLifeCache_` and Gson type token.

**Acceptance criteria:**
- Consecutive `read` after warm cache does not allocate N `CachedMyLifeItem` copies (or measurably fewer allocations in test).
- `write` then `read` round-trips visibility/weight/title/imageId.
- Corrupt JSON still returns null.

**Out of scope:** `LifeRepositoryImpl`, `LifeAdapter`, UI.

**Test notes:** Update `LifeCacheTest` for immutability / no cross-key leakage.

**Constraints:** R5.

---

### Task 7 — Faster notification type resolution

**Roadmap:** 7  
**Also advances 9/10:** yes **#9** — pure string classification, no Android types

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Problem:**  
`resolveType` (lines 417–442) lowercases message then runs many `String.contains` checks per notification. `NotificationsViewModel.formatNotification` calls it for every payload on load (`resolveType(notification.type, notification.message, notification.subType)`), so notification open scales with O(notifications × keywords).

**Required changes:**
- Keep public signature and observable mappings identical (same return strings for the same inputs).
- Restructure with early exits already present (`KNOWN_TYPES`, `team` + `subType`, `newtask`, `newresource`).
- Replace long `contains` chains with compact ordered keyword lists / `firstNotNullOfOrNull`-style scan, still using plain `contains` (no new regex dependency unless already local—prefer no Regex).
- Do not change DAO, enrichment batching, or ViewModel (ViewModel is another task’s file if ever touched).

**Acceptance criteria:**
- Golden cases: join request phrases (EN/ES), chat/voice phrases, team add, task due, storage, resource, unknown → same labels as today.
- `KNOWN_TYPES` short-circuit unchanged.
- Existing `NotificationsRepositoryImplTest` cases updated/extended, all green.

**Out of scope:** Grouping UI; unread counts; server parse path beyond type string.

**Constraints:** R5; do not touch `NotificationsRepository.kt` unless a constant must move (prefer keep constants where they are).

---

### Task 8 — Cheap foreground check for download FGS eligibility

**Roadmap:** 7  
**Also advances 9/10:** no

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt`

**Problem:**  
`canStartForegroundService` → `isAppInForeground` walks `ActivityManager.runningAppProcesses` on every check (`DownloadUtils.kt` 212–233). That binder call is expensive versus process lifecycle state. `Utilities.kt` already uses `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)` for the same idea—but **do not edit Utilities** (keep tasks disjoint).

**Required changes:**
- Implement `isAppInForeground` via `ProcessLifecycleOwner` (same STARTED threshold), matching app-level foreground semantics.
- Keep API of `canStartForegroundService(context)` and API-level branches (pre-O, O–R, S+ with `hasSpecialForegroundPermissions`).
- Preserve `hasSpecialForegroundPermissions` behavior on S+.
- If tests mock `ActivityManager`, retarget them to lifecycle or add a test seam (`@VisibleForTesting` foreground provider) **inside DownloadUtils only**.

**Acceptance criteria:**
- When app process is started/resumed, FGS eligibility still true on O–R.
- When backgrounded without special alarms permission on S+, still false unless `canScheduleExactAlarms`.
- No behavior change to WorkManager enqueue helpers in the same file beyond the foreground helper.

**Out of scope:** `DownloadService` notification throttling; WorkManager constraints.

**Test notes:** Extend `DownloadUtilsTest` for the helper.

**Constraints:** R5.

---

### Task 9 — Chat list append/animation without position-map thrash

**Roadmap:** 7  
**Also advances 9/10:** no (RecyclerView adapter; helps future Compose chat if state stays id-keyed)

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`

**Problem:**  
`addQuery` / `addResponse` copy the full `currentList` to a `MutableList` then `submitList` (`ChatAdapter.kt` 109–124). `animatedMessages` is `HashMap<Int, Boolean>` keyed by **position**; `prependMessages` remaps every key when history loads (lines 132–145). `ChatMessage` already has stable `id: String = UUID.randomUUID().toString()`.

**Required changes:**
- Key `animatedMessages` by `ChatMessage.id` (or response id) instead of adapter position; update bind/animate callback accordingly; drop position remap in `prependMessages`.
- Prefer `submitList(currentList + newMessage)` or `buildList` for single appends (still one list alloc, clearer; avoid double mutation patterns).
- Keep `lastAnimatedPosition` only if still required for “animate latest network response”; if redundant after id keys, remove carefully without breaking typing animation.
- Preserve `LOAD_MORE`, DiffUtil callback, recycle cancel.

**Acceptance criteria:**
- Adding query/response still scrolls to end and animates only the new AI response once.
- Prepending history does not re-animate already-shown responses.
- `clearData` clears animation state.

**Out of scope:** `ChatViewModel`, `ChatDetailFragment`, markdown rendering.

**Test notes:** Extend `ChatAdapterTest` for id-stable animation flags across prepend.

**Constraints:** R5.

---

### Task 10 — Resource sync pagination without CouchDB `skip`

**Roadmap:** 7 (primary), also **#5** consolidate sync workflow  
**Also advances 9/10:** yes **#9** — pagination logic is pure Kotlin against JSON rows (Android only at call edges already in file)

**Files (only these):**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt`

**Problem:**  
Resource pull loop (`SyncManager` ~312–399) pages `_all_docs?include_docs=true&limit=$batchSize&skip=$skip`. CouchDB `skip` is O(skip) server-side, so late batches get slower as catalogs grow—dominates full sync wall time. Progress prefs write every 10 batches is fine; leave unless trivial.

**Required changes:**
- Replace `skip` cursor with `startkey_docid` (and matching `startkey`) pagination: after each non-empty batch, take the last row’s `id`, request next page with `startkey_docid=<lastId>&startkey=<lastId>` plus `limit`, and **skip the first duplicate** doc when it equals the previous end id (standard `_all_docs` pattern).
- Keep `AdaptiveBatchProcessor`, design-doc filtering (`_design`), `resourcesRepository.batchInsertResources`, progress `_syncStatus` (use processed count / `totalRows` instead of skip).
- On empty rows or partial last page, break as today.
- On batch failure, do not silently advance past unfetched docs (retry/backoff behavior should not drop a window—mirror current failure handling intent).
- Do **not** edit `TransactionSyncManager.kt` (locked by open PRs).

**Acceptance criteria:**
- Full resource sync still inserts the same non-design docs for a fixture multi-page `_all_docs` sequence.
- No `skip=` in the resources pagination URL builder after the change.
- Progress denominator still `totalRows` from `limit=0` count call.
- `SyncManagerTest` covers at least two-page continuation and duplicate-boundary handling (mock API).

**Out of scope:** Library shelf parallel sync; upload; AdaptiveBatchProcessor file (logic stays in SyncManager).

**Constraints:** R5; URL building must stay correct with existing `UrlUtils` / header usage already in the method.

---

## Self-check (R1–R6)

| Rule | Status |
|------|--------|
| R1 exactly 10 independent tasks | yes |
| R2 no file in >1 task | yes (each path unique) |
| R3 open PRs listed; cited files not in 1032-path lock set | yes |
| R4 paths/classes/functions verified on disk | yes |
| R5 size/deps/TODO limits | yes by design |
| R6 no implementation in this run | yes |

**Ranking rationale (impact / blast):** Sync `skip` removal (T10) and storage title map (T5) cut offline/sync wall time; Glide/PDF tasks (T1–T4) cut scroll/open jank and memory; Life/notifications/download/chat (T6–T9) are low-blast CPU wins on frequent paths.

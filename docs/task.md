# myPlanet refactor round — performance quick wins (10 work orders)

**Generated:** 2026-09-17 · **Focus:** roadmap **7** (performance micro-optimizations / quick wins)  
**Deliverable:** plan only — no implementation in the generating run · **Rules:** R1–R6 applied

## Open PRs checked (R3) — 36 open; their files are off-limits

`#17255`, `#17254`, `#17222`, `#17187`, `#16624`, `#16623`, `#16594`, `#16270`, `#15951`, `#15825`, `#15824`, `#15820`, `#15808`, `#15559`, `#15519`, `#15412`, `#15267`, `#15266`, `#15226`, `#15198`, `#15108`, `#14960`, `#14893`, `#14883`, `#14650`, `#14427`, `#13928`, `#13848`, `#13657`, `#13604`, `#13415`, `#13355`, `#13287`, `#10993`, `#8175`, `#4075`

**Notable exclusions that shaped this slate:** `SharedPrefManager` (#17222), `TeamResourcesAdapter` / team resources UI (#17254), `DashboardActivity` / bell app bar (#17255), resources filter stack (#17187), offline FTS (#16624), large teams/courses/sync/upload/voices surfaces in older open PRs. ~389 unique open-PR paths were treated as locked.

**Candidates discarded for collision or weak ROI:** `ResourcesAdapter`, `VoicesAdapter`, `CoursesAdapter`, `TeamsRepositoryImpl`, `TransactionSyncManager`, `UploadManager`, `TimeUtils`, `MainApplication`, `AdaptiveBatchProcessor`, `PdfThumbnailLoader`, `Sha256Utils`, `Converters` (already tight).

Every path/class/function below was opened on disk before citation.

---

### Task 1 — Cache course-step resource offline checks on bind
**Roadmap:** 7 (also unblocks 6/10 by keeping bind free of repeated FS work before any Compose port of course resources)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`

**Verified problem:**
- `updateStatusAndPreview` (≈L162–179) calls `FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))` on every full/partial bind when `isResourceOffline()` is false.
- `showImagePreview` / `showVideoPreview` / `showPdfPreview` / `getFileCacheKeyIfExist` each re-stat the same `File` with `file.exists()` (≈L205–318).
- `FileExistenceCache` already exists in `FileUtils.kt` and is used by enterprise adapters, but this adapter does not use it.

**Required change:**
- Hold an adapter-scoped `FileExistenceCache` (and clear it in `onDetachedFromRecyclerView` / when addresses change in `onCurrentListChanged`).
- Resolve the library file once per bind via existing `FileUtils.getLibraryFile` / `externalFilesDir` instead of rebuilding `"ole/${id}/…"` strings repeatedly.
- Cache per-resource download presence for the current list generation so scroll rebinds do not re-hit disk within TTL.
- Keep preview text/html caches; only remove redundant exists checks.

**Constraints:** ≤~150 LOC · no new deps · no behavior change for offline badge or click · do not edit `FileUtils.kt` (Task 2 owns shared helpers if needed — this task uses the existing `FileExistenceCache` API only).

**Acceptance:**
- Unit tests cover: second bind within TTL does not require a fresh positive disk hit after first positive check; cache cleared on detach / address change; offline badge still true when `isResourceOffline()` is true without FS.
- Existing `InlineResourceAdapterTest` still green.

**Out of scope:** Glide, PDF loader, `ResourcesPreviewLoader`, DiffUtil payload design.

---

### Task 2 — Cheap path helpers and existence+length caching in FileUtils
**Roadmap:** 7 (feeds every adapter/service that calls `checkFileExist` / `getFileExtension`; moves pure path logic toward 9)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/FileExistenceCacheTest.kt`

**Verified problem:**
- `getFileExtension(address)` (FileUtils) does `File(it).extension.lowercase()` — allocates a `File` solely for a string suffix.
- `checkFileExist` always `getSDPathFromUrl` + `exists()` + `length() > 0` with no memoization, while `FileExistenceCache.exists` only caches bare `exists()` (not length>0), so callers that need “non-empty file” cannot share the cache correctly.

**Required change:**
- Implement `getFileExtension` with string ops only (`substringAfterLast`, lowercase), matching current empty/null behavior.
- Extend `FileExistenceCache` with a method (or flag) that treats “present” as `exists && length > 0`, still TTL-keyed by absolute path.
- Route `checkFileExist` through a small process-level or caller-supplied cache of that stronger check **or** document a shared internal cache used by `checkFileExist` with clear/TTL; keep public signature of `checkFileExist(context, url)` unchanged.
- Update `FileExistenceCacheTest` for length>0 semantics (empty file → false; delete within TTL still returns cached value; `clear()` forces re-stat).

**Constraints:** no Android API changes beyond existing helpers · no new deps · do not change `getSDPathFromUrl` segment parsing semantics · ≤5 files.

**Acceptance:**
- `FileUtilsTest` / `FileExistenceCacheTest` assert extension parity for multi-dot names, empty, null; existence cache honors length and TTL.
- Call sites compile unchanged.

**Out of scope:** rewriting `getSDPathFromUrl`, APK install path, callers themselves.

---

### Task 3 — Stop SharedPreferences thrash in DownloadService queue bookkeeping
**Roadmap:** 7 + 5 (download/upload workflow health)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`
  (adjust only if needed: `DownloadServiceOnDownloadCompleteTest.kt` / `DownloadServiceUrlSelectionTest.kt` — **pick at most one extra test file**, stay ≤5 total; prefer extending `DownloadServiceTest` only)

**Verified problem:**
- `processDownloadQueue` after each URL: `cachedRemainingCount = getRemainingCount()` then `cleanupProcessedUrls()`.
- `getRemainingCount` re-reads both `PRIORITY_DOWNLOADS_KEY` and `PENDING_DOWNLOADS_KEY` StringSets, unions them, counts not in `processedUrls`.
- `cleanupProcessedUrls` re-reads both sets, mutates, writes both back via `preferences.edit`, then calls `getRemainingCount()` again.
- `Companion.getNextUrl` re-reads the StringSet and filters every pop.

**Required change:**
- Mirror priority/pending queues in memory when the service starts / when new work is enqueued; pop from memory; update `cachedRemainingCount` with simple decrement/increment.
- Persist StringSets in batches (e.g. every N completions or when the queue drains / service stops), not after every single file — **or** write only the set that changed once per completion without a full recount.
- Keep crash-safe enough behavior: on start, rebuild memory from prefs; on stop/destroy, flush.
- Preserve priority-before-pending ordering and `completeAll` logic in `onDownloadComplete`.

**Constraints:** no new deps · no notification UX redesign · foreground service contract unchanged · ≤~150 LOC net if possible (batching logic may approach limit — stay surgical).

**Acceptance:**
- Tests show remaining count stays correct across a multi-URL queue without requiring a prefs read per file (mock SharedPreferences verify reduced `getStringSet` / `putStringSet` frequency).
- Existing resume / URL selection tests still pass.

**Out of scope:** `DownloadWorker`, `DownloadUtils` channels, network stack, alternative URL mapping logic beyond not regressing it.

---

### Task 4 — Single-pass startsWith ranking in ResourcesRepositoryImpl.search
**Roadmap:** 7 + 1 (data layer) + 9 (pure Kotlin ranking over already-normalized titles)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`

**Verified problem:**
- `search` (≈L81–134) runs SQL `titleNormal LIKE` for tokens, then splits matches into `startsWithQuery` and `containsQuery` ArrayLists and returns `startsWithQuery + containsQuery` (extra list alloc + second pass structure).
- Ranking already depends on in-memory `titleNormal.startsWith(normalizedQuery)` after SQL.

**Required change:**
- Keep SQL filtering as-is (do not invent FTS — #16624 owns that area).
- Replace dual-list + concat with one ordered accumulation (`partition` into a pre-sized structure, or `buildList` that appends startsWith first then contains, single iteration).
- Avoid intermediate `queryParts` list if only `normalizedQueryParts` is needed (optional micro).
- Preserve empty-query branches and `userIdPattern` escaping.

**Constraints:** do not change DAO interfaces or SQL dialect · no new deps · public `ResourcesRepository.search` signature unchanged.

**Acceptance:**
- Existing ranking tests (“Ápple Tree” startsWith before contains) still pass; add/adjust one test that result identity/order is stable for mixed startsWith/contains.
- Escape test for `% _ \` still passes.

**Out of scope:** `ResourcesSearchUtils`, UI filter controllers, schema/`titleNormal` backfill.

---

### Task 5 — Tighten ChatSearch normalization and ranking lists
**Roadmap:** 7 + 9 (pure search utility; already dispatcher-injectable)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt`

**Verified problem:**
- `fullConvoSearch` / `searchByTitle` normalize titles/bodies once (good) but still call `startsWith`/`contains` with `ignoreCase = true` on already-normalized strings.
- Four ranked buckets are concatenated with `+` (multiple list copies).
- `queryParts` built then mapped to `normalizedQueryParts` (extra list).

**Required change:**
- Drop redundant `ignoreCase` on normalized comparisons.
- Build result with one `ArrayList` and bucket index ranges, or `buildList` appending in rank order without intermediate `+`.
- Split/normalize query once into normalized parts only.
- Keep default `dispatcher: CoroutineDispatcher = Dispatchers.Default` **or** leave default but ensure tests pass an explicit dispatcher (do not hard-require DI in this object if that expands scope — optional note only).

**Constraints:** ranking order must stay: title-start → title-contains → body-start → body-contains (convo mode) and start → contains (title mode) · no new deps.

**Acceptance:**
- `ChatSearchTest` covers order and multi-token AND; add assertion that search is case-insensitive via normalization still works for accented/mixed case inputs already in tests.

**Out of scope:** Chat UI, Room chat DAO, changing `ChatSearchMode`.

---

### Task 6 — In-memory layer for LifeCache
**Roadmap:** 7 (same class of win as open #17222 for SharedPrefManager, different file)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`

**Verified problem:**
- `read` always `preferences.getString` + `gson.fromJson` with `cachedListType`.
- `write` always full `toJson` + prefs edit.
- `LifeRepositoryImpl.getMyLifeForDashboard` hits `lifeCache.read` on the empty-visible path — repeated dashboard entry pays Gson every time.

**Required change:**
- Add a process-local map (`cacheKey → List<CachedMyLifeItem>`) checked before prefs; populate on successful read/write; invalidate/replace on write for that key.
- Return defensive copies **or** immutable lists so callers cannot mutate the cached instance (match #17222 spirit).
- Keep Gson TypeToken in companion; keep key prefix `myLifeCache_`.

**Constraints:** `@Singleton` stays · no new deps · no API break for `read`/`write` · do not edit `LifeRepositoryImpl` unless absolutely required (prefer not — keep file set to LifeCache + test).

**Acceptance:**
- Tests: second `read` does not call `getString` again; `write` then `read` returns new data; malformed JSON still null; mutation of returned list does not corrupt cache (if copy strategy chosen).

**Out of scope:** Room MyLife DAO, dashboard UI, SharedPrefManager.

---

### Task 7 — Fewer passes in NotificationsRepositoryImpl.getEnrichedNotifications
**Roadmap:** 7 + 1 + 3 (repository enrichment feeding ViewModel)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Verified problem:**
- `getEnrichedNotifications` (≈L165–239) walks payloads once to split task/join lists, then multiple `mapNotNull`/`distinct`/`filter` passes for ids/titles.
- Always `async { getUnreadCount(userId, isAdmin) }` even though payloads already carry `isRead` for the loaded filter set (extra DAO round-trip when filter is `"all"` / data already loaded).
- `parsedTaskDates` via `associateBy` always runs `TaskNotificationUtils.splitTitleAndDate` for every task row.

**Required change:**
- Single pass over `payloadNotifications` collecting task list, join list, task ids, join ids, and optional unread tally when valid.
- Compute `unreadCount` from in-memory payloads when that matches `getUnreadCount` semantics for the current filter; only hit DAO when necessary (e.g. admin/`unread` edge cases — preserve correct counts).
- Keep parallel `async` for team-name and join-detail batch fetches.
- Preserve `parsedTaskDates` map contract for `NotificationsViewModel.formatNotification`.

**Constraints:** do not change `NotificationsViewModel` in this task · no new deps · do not touch team DAO APIs beyond existing calls.

**Acceptance:**
- Repository tests for enriched payloads, task team names, join details, unread count remain correct; add case ensuring unread matches previous DAO behavior for `filter = "all"`.

**Out of scope:** adapter HTML cache, marking read/delete APIs, `TaskNotificationUtils` regex.

---

### Task 8 — Memoize ResourcesPreviewLoader audio/text/csv work
**Roadmap:** 7 (also helps any future Compose resource row that reuses the loader — 10)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt`

**Verified problem:**
- `getAudioPreview` constructs a new `MediaMetadataRetriever` per call with no path/mtime memo (InlineResourceAdapter has its own `textCache`, but other callers and rapid rebinds still pay).
- `getCsvPreview` builds a new CSV parser stack per call; `getTextPreview` opens the file each time.

**Required change:**
- Add a small bounded in-memory cache keyed by `absolutePath + lastModified + length` (same idea as adapter `getCacheKey`) for audio duration string, csv preview, and text preview.
- Cap entries (e.g. LRU ≤ 32–64) to avoid leaks; optional `clear()` for tests.
- Keep `DispatcherProvider.io` usage; swallow errors as today (`""` / `null`).

**Constraints:** no new deps · do not change return formats (`m:ss` audio, 5 CSV rows, 8 text lines) · do not edit InlineResourceAdapter here.

**Acceptance:**
- Tests: second call with unchanged file returns cached value; after touch/mtime change, refreshes; failure paths unchanged.

**Out of scope:** Glide/PDF thumbnails, mime detection.

---

### Task 9 — Fewer allocations in TTSManager.stripMarkdown
**Roadmap:** 7 + 9 (pure string utility)

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/TTSManagerTest.kt`

**Verified problem:**
- `stripMarkdown` (companion, ≈L99–111) chains 10 `.replace(Regex, …)` calls; each allocates a new string even when patterns do not match.
- Regexes are already precompiled (good) — remaining cost is intermediate strings + multiple full scans.

**Required change:**
- Reduce passes: combine compatible replacements where order-safe, or scan once with a small state machine / staged replaces only when a cheap `contains` pre-check finds markdown markers.
- Preserve observable stripping behavior for code fences, inline code, headers, links/images (keep link text), bold/italic markers, lists, blockquotes, horizontal rules, table pipes → space, final trim.
- Do not change `formatCsvForSpeech` unless a one-line shared StringBuilder helper clearly helps without risk.

**Constraints:** no new deps · public API of `stripMarkdown` unchanged · TTS engine wiring untouched.

**Acceptance:**
- Existing TTS markdown tests stay green; add cases for mixed markdown and “plain text fast path” (no markdown chars → same string / single trim).

**Out of scope:** speech queue, utterance listeners, Android `TextToSpeech` lifecycle.

---

### Task 10 — ChatHistoryAdapter DiffUtil and bind micro-costs
**Roadmap:** 7 (list scroll) · minor 8 if tests gain coverage

**Files (exclusive):**
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt`

**Verified problem:**
- `areContentsTheSame` (≈L44–49) evaluates `conversations?.firstOrNull()?.query` twice (old/new each once is fine; but each side may walk; still cheap — larger issue is bind).
- `onBindViewHolder` (≈L110–130) sets title from `getOrNull(0)?.query`, then click listener does `item.conversations?.toList()` allocating a full copy on every bind (listener capture), not only on click — the lambda closes over `item` and calls `toList()` **on click** (OK). Still, DiffUtil does not use payload for title-only updates.
- Share dialog rebuilds flat lists repeatedly (acceptable); focus on DiffUtil comparing `_rev`, `lastUsed`, `title`, and first query without redundant list ops, and avoid storing `chatTitle` global field races if trivial.

**Required change:**
- In `areContentsTheSame`, read first-query once per side into locals; consider comparing conversation size or first item identity if cheap and already available.
- On bind, pass `item.conversations` as the list type already held (if it is already a List, skip `toList()`; only copy if the model type is mutable and must be snapshot-on-click — snapshot **inside** click, not earlier).
- Optional: `getChangePayload` for share-icon-only updates already partially exists via `PAYLOAD_CHAT_SHARED` — ensure full rebind is not forced when only lastUsed changes if UI does not show it (only if tests prove safe).

**Constraints:** no layout XML changes · no new deps · share/dialog behavior identical.

**Acceptance:**
- Adapter tests: contents same/different for title and first query; click still delivers conversations; `notifyChatShared` still payload-binds.

**Out of scope:** `ChatSearch`, ViewModel paging, Compose migration of chat list.

---

## Self-check (R1–R6)

| Rule | Status |
|------|--------|
| R1 exactly 10 independent tasks | Yes |
| R2 no file in >1 task | Yes — each production/test file appears once |
| R3 open PRs listed; locked paths avoided | Yes — 36 PRs; slate avoids locked hot areas |
| R4 paths/classes/functions verified | Yes — opened on disk before cite |
| R5 ~150 LOC / ≤5 files / no new deps / no TODOs | Yes — 2–3 files each |
| R6 no implementation in this run | Yes — plan only |

**Roadmap coverage:** all 10 serve **7**; 3/4/5/6/7 also **1** or **5** or **9** as noted; 1/8 nudge **10** portability readiness without Compose code.

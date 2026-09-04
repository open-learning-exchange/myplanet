# myPlanet refactor round — performance quick wins

**Mode:** plan only (no implementation in this document)  
**Focus:** performance micro-optimizations that unblock larger refactors  
**Open PRs checked:** GitHub MCP `list_pull_requests` + per-PR `get_files` (collision set built from open PR paths). Files touched by any open PR are off-limits.

## Open PRs (off-limits for file ownership)

#16705, #16702, #16701, #16698, #16693, #16690, #16688, #16686, #16680, #16677, #16661, #16647, #16624, #16623, #16619, #16594, #16270, #16101, #15951, #15825, #15824, #15820, #15808, #15699, #15559, #15519, #15412, #15267, #15266, #15226, #15198, #15158, #15108, #14960, #14893, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993

**Notable hot zones already claimed (do not re-touch):** Resources\*/Courses\*/Teams\*/Voices\*/NewsDao, TimeUtils, JsonUtils, TransactionSyncManager, many activities under edge-to-edge, Chat adapters under older PRs where paths collide, Submissions/Progress stacks under #15808.

**Self-check (P5):** exactly 10 tasks · independent merge order · no shared files across tasks · every path/function opened and confirmed · each task ≤~5 files / ≤~150 LOC guidance · no new deps · no implementation code in this plan.

## Roadmap legend

1. finish cleaning the data layer  
2. introduce global navigation architecture  
3. expand viewmodel and use-case layers  
4. complete dependency-injection cleanup  
5. consolidate sync and upload workflow  
6. migrate ui incrementally to compose  
7. optimize remaining performance hotspots  
8. improve code health and add tests  
9. kotlin multiplatform: platform-free kotlin core  
10. compose multiplatform: portable compose screens  

---

### Task 1 — SQL-aggregate most-opened resource

**Roadmap:** 7 (performance); also 1 (data layer)  
**Also moves 9:** yes — pure repository/DAO logic, no Android UI; SQL keeps domain aggregation off the JVM heap.

**Why / user impact**  
Profile “most opened resource” loads every matching `resource_activity` row then groups in memory on every call. Large offline histories make this O(n) memory and CPU on the default dispatcher.

**Verified problem**  
- `ActivitiesRepositoryImpl.getMostOpenedResource` (`app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` ~173–195) calls `resourceActivityDao.getByUserAndType`, then `groupBy` / `maxByOrNull`.  
- `ResourceActivityDao.getByUserAndType` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt` ~18–19) is `SELECT *` with no aggregate.  
- Entity fields used: `ResourceActivity.resourceId`, `title`, `user`, `type`.

**Allowed files (only these)**  
1. `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`  
2. `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`  
3. `app/src/test/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImplTest.kt`

**Change instructions**  
- Add a DAO query that returns the single most-opened resource for `(userName, type)`: group by `resourceId`, count rows, pick max count, return title + count (skip null titles). Prefer one `@Query` with `GROUP BY` / `ORDER BY COUNT(*) DESC LIMIT 1` (or a small result DTO / pair of columns Room can map).  
- Change `getMostOpenedResource` to call that query and map to `Pair<String, Int>?` with empty → null. Remove in-memory groupBy path.  
- Keep public repository signature unchanged. Do not change schema version.

**Acceptance**  
- Same results as today for empty set, single winner, and ties (document tie-break: any stable ORDER BY, e.g. title or resourceId).  
- No full-table materialization for this call path.  
- Existing tests in `ActivitiesRepositoryImplTest` for `getMostOpenedResource` still pass; add/adjust mocks for the new DAO method.

**Out of scope**  
Other activity queries, upload paths, offline activity DAOs.

**Tests**  
Extend `ActivitiesRepositoryImplTest` (`getMostOpenedResource returns null when no activities`, `returns correct pair`).

**Constraints**  
Under ~150 LOC, ≤3 files, no new deps, no TODOs.

---

### Task 2 — Parallelize PhotoUploader batch POSTs

**Roadmap:** 7; also 5 (sync/upload consolidation)  
**Also moves 9:** partial — upload orchestration stays Kotlin/coroutines; keep Android-free beyond existing FileUploader base.

**Why / user impact**  
Unuploaded exam photos POST one-by-one inside each batch. Wall-clock upload time scales linearly with photo count on slow links.

**Verified problem**  
`PhotoUploader.uploadSubmitPhotos` (`app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt` ~25–80): `photosToUpload.chunked(BATCH_SIZE).forEach` then **`batch.forEach`** sequential `uploadRepository.postUpload`. Attachment uploads after mark are also sequential.  
`UploadCoordinator` already uses `Semaphore` + `async` / `coroutineScope` with `MAX_CONCURRENT_UPLOADS = 6` — copy that pattern, do not invent a new framework.

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt`  
2. `app/src/test/java/org/ole/planet/myplanet/services/upload/PhotoUploaderTest.kt`

**Change instructions**  
- Within each batch, run POSTs with bounded concurrency (reuse `UploadCoordinator`’s semaphore size or a local constant ≤6). Collect successes into a thread-safe list, then keep a single `markPhotosUploadedBatch` call.  
- Optionally parallelize `uploadAttachment` calls the same way after marks; preserve listener callbacks and failure isolation (one failure must not cancel the batch).  
- Do not change `BATCH_SIZE` semantics or repository APIs.

**Acceptance**  
- Empty list still returns `"No photos to upload"`.  
- Successful photos still marked in batch; failures logged and skipped.  
- Wall-clock for N independent POSTs improves under concurrency without exceeding semaphore limit.  
- `PhotoUploaderTest` updated/passing.

**Out of scope**  
`UploadManager`, `UploadCoordinator`, submissions DAO.

**Constraints**  
Under ~150 LOC, ≤2 files, no new deps.

---

### Task 3 — Stop UploadManager news image `openConnection` MIME probe

**Roadmap:** 7; also 5  
**Also moves 9:** yes if MIME resolution stays on `FileUtils`/`Utilities` pure helpers already used elsewhere.

**Why / user impact**  
Each news image opens a `URLConnection` solely to read `contentType`, then **ignores it** and uploads as `application/octet-stream`. That is wasted disk/network metadata I/O on every image during news upload.

**Verified problem**  
`UploadManager.uploadNews` (`app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` ~406–523):  
`val mimeType = imageFile.toURI().toURL().openConnection().contentType` then  
`imageFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())` — MIME unused.  
`FileUtils.getMimeType` / `Utilities.getMimeType` already exist for extension-based MIME.

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`  
2. `app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt`

**Change instructions**  
- Remove the `toURI().toURL().openConnection().contentType` probe.  
- Derive MIME via existing `FileUtils.getMimeType(imageFile.name)` (or `Utilities.getMimeType`) and pass that into `asRequestBody` (fallback `application/octet-stream` if null).  
- Keep sequential image upload order unless you can parallelize **within the same function** under ≤~40 extra LOC without changing mark/upload transaction semantics; prefer the MIME fix as the mandatory win.  
- Do not touch `createImage` contract or news JSON shape.

**Acceptance**  
- No `URLConnection` opened for local news images.  
- Upload request body uses a sensible MIME when extension is known.  
- Existing news-upload tests still pass; add assertion that MIME helper path is used if tests stub files.

**Out of scope**  
PhotoUploader, AchievementUploader, other UploadManager methods beyond `uploadNews` image loop / helpers strictly required by that loop.

**Constraints**  
Under ~150 LOC, ≤2 files.

---

### Task 4 — Early-exit `FileUtils.findHtmlCoverImage`

**Roadmap:** 7  
**Also moves 9:** yes — pure file utility, no Android UI (Context unused in this function).

**Why / user impact**  
HTML resource previews walk the tree to depth 4, **materialize every image into a List**, then scan for name hints. Called from adapters/list binding paths; large resource trees allocate and touch many files unnecessarily.

**Verified problem**  
`FileUtils.findHtmlCoverImage` (`app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` ~142–152):  
`walkTopDown().maxDepth(4).filter { … }.toList()` then `firstOrNull` name-hint else `maxByOrNull { length }`.  
Tests already cover preference order in `FileUtilsTest` (`findHtmlCoverImage_*`).

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`  
2. `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`

**Change instructions**  
- Single pass over the walk: track best name-hint match (prefer shallowest / first stable order consistent with tests) and largest image by length.  
- If a name-hint match is found, return immediately **without** collecting the full list (still allow deeper search only if you keep current preference semantics required by tests — if tests require “any hint over larger non-hint”, you may finish the walk only when no hint found yet, or return first hint if that matches existing tests).  
- Preserve: null when no images; nested subdirectory discovery; hint names from `previewImageNameHints`; extension set `previewImageExtensions`.  
- Do not change other FileUtils APIs.

**Acceptance**  
All existing `findHtmlCoverImage_*` tests pass; add a test that a deep tree still returns quickly when a shallow cover exists (behavior), without asserting timing flakily.

**Constraints**  
Under ~150 LOC, ≤2 files.

---

### Task 5 — Cache / cheapen StorageBreakdown full-tree scan

**Roadmap:** 7; also 3 (ViewModel/use-case hygiene if logic stays in fragment minimally)  
**Also moves 10:** n/a (View system); keep logic extractable later.

**Why / user impact**  
Opening storage breakdown walks **every file** under the ole download directory on each load via `walkTopDown`, blocking UI work on the fragment’s coroutine path.

**Verified problem**  
`StorageBreakdownFragment.scanStorage` (`app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt` ~205–223): full `oleDir.walkTopDown().filter { isFile }` aggregating sizes/counts by extension via `StorageCategories.indexOf`.  
Note: category detail uses `ResourcesRepository.getOfflineResourceItems` (off-limits under open PRs) — **do not** change that repository; only this fragment’s own scan.

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt`  
(optional second file only if needed for a tiny pure helper **new** file under `ui/settings/` — prefer keep single-file)

**Change instructions**  
- Keep one pass (already one pass) but: avoid repeated `file.extension.lowercase` allocations where cheap (`substringAfterLast` + cached category map from extension→index built once from `StorageCategories`).  
- Add short-lived in-memory cache keyed by ole path + directory `lastModified` (or total child count if reliable) so revisiting the screen without FS changes skips the walk. Invalidate when user returns after deletion if the fragment already reloads — wire cache clear on known delete completion if that callback exists in-fragment; otherwise TTL ≤ few seconds is enough.  
- Ensure scan still runs off main thread (existing `launch` path).  
- Do not add dependencies; do not touch `StorageCategoryViewModel` / Resources repository.

**Acceptance**  
- Category totals and file counts match pre-change for a fixture tree.  
- Second scan with unchanged directory does not re-walk (verifiable via test double or package-visible counter if you add a tiny internal hook; otherwise manual). Prefer a package-private/testable function if that stays under ~150 LOC.

**Out of scope**  
`StorageCategoryViewModel`, `ResourcesRepositoryImpl`, `StorageCategories.kt` unless read-only.

**Constraints**  
Prefer 1 file; ≤2; under ~150 LOC.

---

### Task 6 — Bound Glide decode size in `ImageUtils`

**Roadmap:** 7; also 10 (CMP-ready image loading habits: size from caller, no unbounded decode)  
**Also moves 10:** yes — decode bounds belong with target size; profile path already uses `.override(sizePx)`.

**Why / user impact**  
`loadImage` / `loadPlaceholderImage` decode full-resolution bitmaps into list/avatar ImageViews, causing jank and memory spikes versus `loadProfileImage` which already overrides size.

**Verified problem**  
`ImageUtils` (`app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt`):  
- `loadProfileImage` ~9–22: has `.override(sizePx, sizePx)`.  
- `loadImage` ~24–36: no override.  
- `loadPlaceholderImage` ~38–44: no override, no disk cache strategy.

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt`  
2. `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt`

**Change instructions**  
- Add optional `sizePx` parameter (with sensible default from view layout params when >0, else a small constant e.g. matching common avatar dp→px already used by callers of `loadProfileImage`) to `loadImage` and `loadPlaceholderImage`, applying `.override(size, size)`.  
- Align `loadPlaceholderImage` with `diskCacheStrategy` used by siblings.  
- Preserve placeholders/errors/circleCrop behavior of `loadImage`.  
- Binary-compatible defaults so existing call sites compile without edits **outside allowed files** (defaulted params only — do not bulk-edit call sites in other files).

**Acceptance**  
- `ImageUtilsTest` still passes; extend to ensure non-null path still loads and empty/null still sets fallback drawable.  
- No new Gradle deps.

**Constraints**  
Under ~150 LOC, ≤2 files; **do not** edit call-site files (would violate R2/R3 sprawl).

---

### Task 7 — Cache News JSON-derived getters

**Roadmap:** 7; also 1 (model/data cleanliness); **9** strongly  
**Also moves 9:** yes — model stays pure Kotlin + Gson; zero new Android APIs. Enables CMP list binding without re-parse.

**Why / user impact**  
Voices/list code hits `imagesArray`, `messageWithoutMarkdown`, and `isCommunityNews` repeatedly. Each access re-runs `JsonUtils.gson.fromJson` on `images` / `viewIn`. Ignore fields `parsedImagesArray` / `parsedViewIn` already exist but getters ignore them.

**Verified problem**  
`News` (`app/src/main/java/org/ole/planet/myplanet/model/News.kt`):  
- `imagesArray` getter ~85–86 always `fromJson(images, …)`.  
- `messageWithoutMarkdown` ~113–120 iterates `imagesArray` (re-parse).  
- `isCommunityNews` ~123–135 always `fromJson(viewIn, …)` even though `calculateSortDate` ~137–152 already prefers `parsedViewIn`.  
- `@Ignore var parsedImagesArray` / `parsedViewIn` ~66–80 unused by these getters.

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/model/News.kt`  
2. (optional) new `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt` if no existing unit test file for News getters

**Change instructions**  
- Make `imagesArray` return `parsedImagesArray` when set and `rawImages == images` (or simpler: lazy-fill `parsedImagesArray` on first access and reuse until `images` reference/string changes — track via `rawImages`).  
- Make `isCommunityNews` use `parsedViewIn` / cache boolean invalidating when `viewIn` changes (`rawViewIn` already present).  
- `messageWithoutMarkdown` must use cached images array.  
- Null/empty/`JsonSyntaxException` safe: empty array / false, no crash.  
- Do not change Room fields or `@Entity` shape; only `@Ignore` / getters / small private helpers.

**Acceptance**  
- Repeated access to getters does not re-parse when underlying strings unchanged (unit test with counter via wrapping is optional; at minimum assert correctness for community vs team `viewIn` and markdown strip).  
- No Android imports added.

**Out of scope**  
VoicesAdapter, NewsDao, VoicesRepository (open PR territory).

**Constraints**  
≤2 files, under ~150 LOC.

---

### Task 8 — Parallelize team chat count fetches in notifications

**Roadmap:** 7; also 5  
**Also moves 9:** partial — repository stays coroutine-based; **do not** add bulk DAO APIs on off-limits News/Voices files.

**Why / user impact**  
Team badge/notification refresh issues **one** `voicesRepository.countTopLevelByTeam(teamId)` per team sequentially. Many teams → long main-path latency before dashboard/team lists show chat badges.

**Verified problem**  
`NotificationsRepositoryImpl.getTeamNotifications` (`app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` ~295–326): loop  
`for (teamId in teamIds) { chatCountsById[teamId] = voicesRepository.countTopLevelByTeam(teamId) }` after a single batched `teamNotificationDao.getByTypeAndParentIds`.  
Bulk SQL would need `NewsDao` / `VoicesRepository` API changes — **off-limits** (#15820 / #13415 / #15808). Parallelize calls only.

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`  
2. `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Change instructions**  
- Replace sequential count loop with `coroutineScope` + `async` per teamId (or chunked with a small concurrency cap if teamLists can be huge — cap ≤8).  
- Preserve map semantics: missing counts → 0; `hasChat = notification != null && notification.lastCount < chatCount`; `hasTask` still from single `getTasksForUserBetween`.  
- Do not modify `VoicesRepository` interface or DAOs.  
- Add unit test that multiple teamIds invoke `countTopLevelByTeam` once each and return combined map (MockK `coVerify`).

**Acceptance**  
- Behavioral parity with sequential version.  
- `TeamChatBadgeIntegrationTest` remains valid (untouched file — don’t edit it).  
- NotificationsRepositoryImplTest gains coverage for multi-team path.

**Constraints**  
≤2 files, under ~150 LOC, no new deps.

---

### Task 9 — Cache AI models map in ChatDetailFragment

**Roadmap:** 7; also 3 (UI state hygiene)  
**Also moves 10:** yes — hoist/cache parsed config away from repeated UI recomputation; keep parsing out of tight UI paths.

**Why / user impact**  
Every provider/model lookup re-reads SharedPreferences and Gson-parses the full `ai_models` map.

**Verified problem**  
`ChatDetailFragment.getModelsMap` (`app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt` ~596–603) always `sharedPrefManager.getRawString("ai_models")` + `JsonUtils.gson.fromJson`.  
Call sites include ~443, ~480, and `getCachedProviderAvailability` ~605–613 (which calls `getModelsMap` again).

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`  
2. (optional) `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragmentInjectionTest.kt` only if needed for compile; prefer no test file churn

**Change instructions**  
- Cache last raw string + parsed `Map<String, String>` in fragment fields; invalidate when raw string changes.  
- Ensure `getCachedProviderAvailability` uses the cached map once.  
- Clear cache on logout/destroy if those hooks already clear other chat state; otherwise fragment field lifetime is enough.  
- Do not change ChatViewModel, adapters, or networking.

**Acceptance**  
- Behavior unchanged when pref value changes mid-session (detect via raw string compare).  
- No new dependencies; no JSON utility refactors (JsonUtils is off-limits).

**Constraints**  
1 file preferred, under ~80 LOC delta.

---

### Task 10 — Bounded parallel downloads in DownloadService

**Roadmap:** 7; also 5  
**Also moves 9:** limited (service is Android); keep download core logic isolatable.

**Why / user impact**  
Download queue processes **one URL at a time** (`processDownloadQueue` while-loop). Multi-file course/library downloads under-utilize bandwidth and stretch foreground-service time.

**Verified problem**  
`DownloadService.processDownloadQueue` (`app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` ~131+): sequential `getNextPriorityUrl` / `getNextPendingUrl` then `initDownload`.  
`BUFFER_SIZE = 1024 * 16` (~548).  
Tests: `DownloadServiceTest`, `DownloadServiceOnDownloadCompleteTest`, `DownloadServiceUrlSelectionTest` (only edit the first if needed; **do not** claim the others if unused).

**Allowed files**  
1. `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`  
2. `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`

**Change instructions**  
- Introduce small concurrency limit (2 or 3) for **non-priority** downloads using `Semaphore`/`async`, still preferring priority URLs first (priority may remain serial to preserve UX ordering).  
- Protect shared mutable session counters / notification updates with existing main-thread/`synchronized` patterns already in the service; throttle notification updates so parallel completions don’t spam.  
- Optional micro-win in same PR: raise `BUFFER_SIZE` modestly (e.g. 32–64 KiB) if it stays local.  
- Guarantee: queue still drains to empty → completion notification → `stopSelf`; no double-download of same URL (`processedUrls`).  
- Keep public companion queue helpers’ behavior.

**Acceptance**  
- Existing URL selection / completion tests pass.  
- Two distinct pending URLs can complete without requiring full serial wall-time (logic test with fakes if present; otherwise careful instrumentation of queue).  
- No new deps; no manifest changes.

**Out of scope**  
`DownloadWorker`, `ResourceDownloadCoordinator`, Resources UI.

**Constraints**  
≤2 files, target under ~150 LOC changed; if parallel path risks over ~150 LOC, **fall back** to: (a) larger buffer + (b) notification throttle only — still a valid completion of this task.

---

## Cross-task guarantees

| # | Primary files | Roadmap | Overlap |
|---|---------------|---------|---------|
| 1 | ResourceActivityDao, ActivitiesRepositoryImpl (+test) | 7,1 →9 | none |
| 2 | PhotoUploader (+test) | 7,5 | none |
| 3 | UploadManager (+test) | 7,5 | none |
| 4 | FileUtils (+test) | 7 →9 | none |
| 5 | StorageBreakdownFragment | 7 | none |
| 6 | ImageUtils (+test) | 7 →10 | none |
| 7 | News.kt (+optional NewsTest) | 7,1 →9 | none |
| 8 | NotificationsRepositoryImpl (+test) | 7,5 | none |
| 9 | ChatDetailFragment | 7,3 →10 | none |
| 10 | DownloadService (+test) | 7,5 | none |

**Agents:** implement exactly one task per PR; do not “helpfully” touch neighboring upload/sync files; do not add dependencies; do not leave TODOs; verify cited symbols still exist at start of work (branch may move).

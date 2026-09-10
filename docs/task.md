# myPlanet refactor round — performance quick wins

**Open PRs checked:** 38 open (`#4075` … `#16989`; 33 non-draft, 5 draft).  
**Off-limits:** 267 unique paths collected from open PR file lists. Any file those PRs touch is excluded below.  
**Focus:** roadmap **7** performance micro-optimizations; several also nudge **5** (sync/upload), **1** (data layer), **8** (tests), and **9** (platform-free model core).  
**Deliverable:** plan only — no implementation in the generating run.

Hard rules applied: exactly 10 independent tasks; no shared files across tasks; every path verified on disk; each task under ~150 LOC and ~5 files; no new dependencies.

---

### Task 1 — Buffer download writes in `DownloadService`

**Roadmap:** 7 (also 5 — download path of offline content)

**Problem**  
`DownloadService.downloadFile` writes the network stream through a raw `FileOutputStream` (`DownloadService.kt` ~383–399, `copyStreamWithProgress` ~415–447). Each `output.write(data, 0, readCount)` is a syscall-sized write with no intermediate buffer. Large media downloads thrash I/O and slow completion.

**Goal**  
Wrap the file output in `BufferedOutputStream` (reuse existing `BUFFER_SIZE`) so chunk writes coalesce. Keep rename/temp-file/error behavior identical.

**Allowed files (only these)**  
- `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`  
- `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt` (extend if needed)

**Steps**  
1. In `downloadFile`, wrap `FileOutputStream(tempFile)` with `BufferedOutputStream(..., BUFFER_SIZE)` (or equivalent).  
2. Ensure the buffer is flushed before rename/copy-to-final.  
3. Do not change progress notification cadence, URL handling, or repository calls.  
4. Extend existing unit tests if they cover the write path; otherwise keep behavior-only change and run `DownloadServiceTest`.

**Acceptance**  
- Downloads still complete to the same final path.  
- Temp file + rename/fallback copy still work.  
- No new dependencies.  
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.DownloadServiceTest"` passes.

**Out of scope**  
Worker download path, notification UI, URL selection.

**Estimate:** ~20–40 LOC · 1–2 files

---

### Task 2 — Size Glide course covers in `CoursesAdapter`

**Roadmap:** 7 (also 10-prep — bitmap decode bounded before Compose migration)

**Problem**  
`CoursesAdapter.bindCover` (`CoursesAdapter.kt` ~285–316) loads cover images with Glide `centerCrop()` but **no** `.override(w,h)`. Full-resolution covers are decoded for small grid/list thumbs → scroll jank and memory spikes.

**Goal**  
Add a fixed decode size (from existing cover `ImageView` layout size / density) via Glide `.override(...)`, matching `ImageUtils.loadProfileImage`’s pattern. Preserve signature, disk cache, error drawable.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`  
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt`

**Steps**  
1. Resolve target px once (e.g. from `ivCover.layoutParams` or a small density-based constant used for both grid and list).  
2. Apply `.override(targetPx, targetPx)` (or width×height) on the Glide request in `bindCover`.  
3. Keep `ObjectKey(course.courseRev)`, `DiskCacheStrategy.ALL`, `centerCrop`, error logo.  
4. Update/add a test that the adapter still binds without crashing when cover is null/local/remote (existing tests).

**Acceptance**  
- Covers still show for local file and remote `GlideUrl`.  
- Null model still shows subject icon.  
- No API/signature changes outside the adapter.  
- `CoursesAdapterTest` passes.

**Out of scope**  
`MyCourse.getCoverImageFile`, fragment list loading, ResourcesAdapter (open-PR locked).

**Estimate:** ~15–30 LOC · 1–2 files

---

### Task 3 — Size Glide previews in `InlineResourceAdapter`

**Roadmap:** 7 (also 10-prep)

**Problem**  
`InlineResourceAdapter.showImagePreview` / `showVideoPreview` / `showHtmlPreview` (`InlineResourceAdapter.kt` ~205–263) load full files into thumbnail `ImageView`s without `.override(...)`. Course step resource rows decode full images/video frames.

**Goal**  
Add `.override` based on the preview view size (or existing `PDF_PREVIEW_WIDTH_DP`-style constant for consistency). Leave PDF path (already sized) alone.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`  
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`

**Steps**  
1. Introduce one private helper or constant for preview decode size in px.  
2. Apply to Glide loads in `showImagePreview`, `showVideoPreview`, and HTML cover load.  
3. Do not change mime routing, existence checks, or PDF/audio/text paths.  
4. Keep `InlineResourceAdapterTest` green; add a focused assertion only if practical without Robolectric Glide flakiness.

**Acceptance**  
- Image/video/html previews still appear when files exist.  
- Missing files still hide previews.  
- `InlineResourceAdapterTest` passes.

**Out of scope**  
`FileUtils.findHtmlCoverImage`, `PdfThumbnailLoader`, resource download.

**Estimate:** ~20–40 LOC · 1–2 files

---

### Task 4 — Memoize JSON getters on `News`

**Roadmap:** 7 (also 1 data-layer polish; **9** — pure Kotlin model, fewer repeated parses)

**Problem**  
`News.imagesArray` (`News.kt` ~83–85) always `gson.fromJson`s `images`.  
`News.isCommunityNews` (~111–124) always reparses `viewIn` and ignores existing `parsedViewIn`.  
`calculateSortDate` already prefers `parsedViewIn`; getters do not. List binding (voices/chat share) hits these repeatedly.

**Goal**  
Use `@Ignore` fields already on the entity (`parsedImagesArray`, `parsedViewIn`) to memoize parse results; invalidate when underlying string fields change if setters exist, or document that caches are session/`@Ignore` only (Room does not persist them).

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/model/News.kt`  
- `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt`

**Steps**  
1. Change `imagesArray` to return `parsedImagesArray` if set; else parse once, store in `parsedImagesArray`, return. Empty/`null` images → empty `JsonArray` without thrashing.  
2. Change `isCommunityNews` to use `parsedViewIn` the same way `calculateSortDate` does.  
3. Keep public API and serialization helpers unchanged.  
4. Add unit tests for repeated access returning equal content and for community detection with sample `viewIn` JSON.

**Acceptance**  
- Second read of `imagesArray` / `isCommunityNews` does not re-parse when cache is warm.  
- Existing `NewsTest` cases still pass; new cases cover memoization.  
- No Room schema / version bump.

**Out of scope**  
`VoicesRepositoryImpl`, adapters, DAO (open-PR / out of budget).

**Estimate:** ~40–70 LOC · 1–2 files

---

### Task 5 — Parallelize team chat count lookups in notifications

**Roadmap:** 7 (also 3 — repository hot path)

**Problem**  
`NotificationsRepositoryImpl.getTeamNotifications` (`NotificationsRepositoryImpl.kt` ~301–331) loops `for (teamId in notificationsById.keys)` and awaits `voicesRepository.countTopLevelByTeam(teamId)` **sequentially** — classic N+1 latency when many teams have chat badge rows.  
`VoicesRepository` / `NewsDao` bulk APIs are **off-limits** (open PRs); fix must stay inside this repository.

**Goal**  
Fetch counts concurrently with `coroutineScope` + `async`/`awaitAll` (same dispatcher the caller already uses), preserving result map semantics.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`  
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Steps**  
1. Replace the sequential count loop with parallel async calls over `notificationsById.keys`.  
2. Keep `hasTask` / `hasChat` logic and empty-input early return.  
3. Do not add methods on `VoicesRepository`.  
4. Extend `getTeamNotifications only counts messages for teams with chat notification row` (and peers) to cover multiple team IDs if missing.

**Acceptance**  
- Same `Map<String, TeamNotificationInfo>` for given fixtures.  
- Only teams present in `notificationsById` are counted (existing invariant).  
- `NotificationsRepositoryImplTest` + relevant badge tests that only touch this class’s expectations pass.

**Out of scope**  
Bulk DAO API, UI badge rendering, `TeamChatBadgeIntegrationTest` file ownership (do not edit if it forces extra production files).

**Estimate:** ~25–50 LOC · 1–2 files

---

### Task 6 — Cut redundant offline work in `ResourcesRepositoryImpl` list + offline scan

**Roadmap:** 7 (also 1)

**Problem**  
1. `getResourceListModels` (`ResourcesRepositoryImpl.kt` ~754–776) sorts with `library.isResourceOffline()` then maps and calls `isResourceOffline()` **again** per row.  
2. `getOfflineResourceItems` (~846–874) walks the whole OLE tree and lowercases extensions repeatedly; title map is fine.

**Goal**  
Single offline flag per library when building models; one lowercase extension per file in the offline scan. Behavior and cache keys unchanged.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`  
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`

**Steps**  
1. In the map after sort (or map-then-sort with precomputed flag), compute `val offline = library.isResourceOffline()` once and pass into `ResourceItem.isOffline`.  
2. In `getOfflineResourceItems`, use `file.extension.lowercase()` once per file.  
3. Keep cache fields `cachedMyCourseLibModels` / `cachedPublicLibModels` semantics.  
4. Run existing `getResourceListModels*` tests; add a small offline-scan test only if cheap with temp dirs.

**Acceptance**  
- List model `isOffline` matches `MyLibrary.isResourceOffline()`.  
- Offline items still group by parent folder name and sort by title.  
- `ResourcesRepositoryImplTest` passes.

**Out of scope**  
Tag bulk loading, SQL search builder, `ResourcesAdapter` (locked).

**Estimate:** ~30–60 LOC · 1–2 files

---

### Task 7 — Speed storage category scan in `StorageBreakdownFragment`

**Roadmap:** 7

**Problem**  
`scanStorage` (`StorageBreakdownFragment.kt` ~217–238) does, per file:

```text
indexOf(ext); if OTHER then indexOf(ext.lowercase())
```

So many files pay **two** map lookups. `StorageCategories.extensionToIndex` keys are already lowercase (`StorageCategories.kt` ~13–32).

**Goal**  
Always classify with a single `extension.lowercase(Locale.ROOT)` (or `lowercase()`) + one `StorageCategories.indexOf`. Same totals/counts.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt`  
- `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragmentTest.kt`

**Steps**  
1. Replace the dual-`indexOf` block with one lowercase + one lookup.  
2. Empty extension still maps to `OTHER_INDEX`.  
3. Update `StorageBreakdownFragmentTest` fixtures for mixed-case extensions if present; add one case `JPG` → images bucket.

**Acceptance**  
- Category sizes/counts unchanged for known trees.  
- Mixed-case extensions count in the correct bucket.  
- Existing storage tests pass.

**Out of scope**  
`StorageCategories` rewrite, delete flow, `StorageCategoryDetailFragment`.

**Estimate:** ~15–35 LOC · 1–2 files

---

### Task 8 — O(1) tag matching in `ResourcesListFilter`

**Roadmap:** 7

**Problem**  
`filterBySearchAndTags` (`ResourcesListFilter.kt` ~55–62) uses nested `tags.any { searchTag -> model.tags.any { it.id == searchTag.id } }` → O(models × searchTags × modelTags) on every keystroke/filter apply.  
`toSignature` also allocates fresh `HashSet` copies of criteria sets every call (~91–99) — acceptable to leave, or reuse sets only if equality stays correct.

**Goal**  
Build a `Set` of search-tag IDs once per filter call; test membership with `model.tags.any { it.id in searchTagIds }`. Preserve `filterIfChanged` signature short-circuit.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt`  
- `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt`

**Steps**  
1. Precompute `searchTagIds` when `tags.isNotEmpty()`.  
2. Filter models with set membership.  
3. Keep facet/download logic untouched.  
4. Extend `ResourcesListFilterTest` for multi-tag intersection/union semantics **exactly as today** (any-of search tags).

**Acceptance**  
- Same filtered lists as before for identical inputs.  
- `filterIfChanged` still returns `null` when signature unchanged.  
- `ResourcesListFilterTest` passes.

**Out of scope**  
`ResourcesSearchUtils`, ViewModel, adapter (locked).

**Estimate:** ~20–40 LOC · 1–2 files

---

### Task 9 — Cache `SharedPrefManager.getSavedUsers`

**Roadmap:** 7 (also 4 DI/prefs hygiene)

**Problem**  
`getSavedUsers` (`SharedPrefManager.kt` ~73–80) runs `gson.fromJson` on every call. Login/team/user flows call it repeatedly (`UserRepositoryImplTest` / teams tests mock it heavily). `setSavedUsers` always rewrites JSON but does not keep an in-memory cache.

**Goal**  
Cache the decoded `List<User>` in the `@Singleton` manager; invalidate/update on `setSavedUsers` and any clear/remove path that mutates `SAVED_USERS` in this class.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt`  
- `app/src/test/java/org/ole/planet/myplanet/services/SharedPrefManagerTest.kt`

**Steps**  
1. Add a nullable/in-memory cache field for saved users.  
2. `getSavedUsers`: return cache if present; else parse, store, return.  
3. `setSavedUsers`: write prefs **and** update cache (store the list passed in, or re-read once).  
4. If this class has remove/clear for saved users, invalidate there too (search within the file only).  
5. Add tests: second `getSavedUsers` equals first; after `setSavedUsers` cache reflects new list.

**Acceptance**  
- Behavioral parity with disk.  
- No API change.  
- `SharedPrefManagerTest` passes.

**Out of scope**  
Other pref keys, encrypted prefs, multi-process invalidation.

**Estimate:** ~25–45 LOC · 1–2 files

---

### Task 10 — Replace CouchDB `skip` paging in resource sync

**Roadmap:** 5 + 7 (unblocks larger sync consolidations; large libraries)

**Problem**  
`SyncManager.resourceTransactionSync` (`SyncManager.kt` ~313–408) pages with `_all_docs?include_docs=true&limit=$batchSize&skip=$skip`. CouchDB `skip` is O(skip) per request — cost grows as the resource table grows. Progress still uses `skip` as a counter.

**Goal**  
Page with `startkey` (and `skip=1` after the first page, or exclusive start after last `_id`) while keeping adaptive batch sizing, insert, cleanup, and progress UI. Encode keys safely for URLs.

**Allowed files**  
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`  
- `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt`

**Steps**  
1. Track `lastKey` / `startKey` from the last row’s `id` in each batch.  
2. Build request URL without `skip` offset (except the standard `skip=1` when restarting at last key to avoid duplicates).  
3. Keep `AdaptiveBatchProcessor`, failure handling, `ResourceSyncPosition` meaning as “docs processed” if still written.  
4. Stop when a page returns fewer than requested or empty rows.  
5. Update/add unit tests around URL construction or paging loop if the class is mockable; otherwise test pure helper if you extract a **private** URL builder in the same file only (no new public modules).

**Acceptance**  
- Full resource sync still inserts the same docs and runs cleanup when no batch failed.  
- No duplicate inserts from paging overlap.  
- Progress still advances.  
- `SyncManagerTest` passes.

**Out of scope**  
`TransactionSyncManager` (open-PR locked), other tables’ loops, UI strings.

**Estimate:** ~60–120 LOC · 1–2 files

---

## Self-check (R1–R6)

| Rule | Status |
|------|--------|
| R1 Exactly 10 independent tasks | Yes |
| R2 No file in more than one task | Yes — each production file unique; tests paired only with their task |
| R3 Open PRs listed; locked files avoided | 38 PRs; candidates cross-checked against 267 touched paths |
| R4 Paths/symbols verified on disk | Yes — line refs opened before citing |
| R5 ~≤150 LOC, ≤5 files, no new deps, no TODOs | Yes |
| R6 No implementation in the generating run | Yes |

**Ranking rationale (impact / blast radius):** download I/O and resource sync paging hit offline-first UX hardest; Glide overrides fix scroll jank cheaply; `News`/filter/notifications/prefs are high-frequency micro-costs with 1-file blast radius.

# Performance quick-win work orders

Plan-only work orders for other coding agents. Each task is independently mergeable in any order. No file appears in more than one task.

**Roadmap served:** 7 (optimize remaining performance hotspots), plus 1 / 3 / 8 / 9 / 10 where noted.

**Constraints (every task):** under ~150 changed lines, under ~5 files, no new dependencies, no unused code, no TODO placeholders.

## Open pull requests (off-limits files)

Any file an open PR already touches is off-limits. Chosen tasks were checked against extracted file lists from these PRs.

| # | Title | Draft |
|---|---|---|
| 17812 | actions: bump gradle/actions from 6.3.0 to 6.4.0 | |
| 17811 | mySurvey: survey number update | yes |
| 17776 | Refactor showMemberDetails to load visit stats in MembersDetailViewModel | |
| 17694 | Shared kmp module scaffold | yes |
| 17680 | Fix TeamResourcesAdapterTest failure and Glide preview clearing | |
| 17435 | resources: added filter labels indications (fixes #16922) | yes |
| 17254 | teams: refactored resources to match library (fixes #16927) | |
| 17187 | resources: refactored filter logic (fixes #16282) | |
| 16623 | teams: smoother interactive team tasks status and board handling (fixes #16616) | |
| 16594 | actions: smoother size labeller fetching (fixes #16344) | |
| 15951 | teams: smoother repository update requesting (fixes #15568) | |
| 15825 | local event task reminders workmanager notifications (address #15115) | |
| 15824 | gamification achievement hub offline badges streaks (address #15114) | |
| 15820 | teams: smoother task and meetup comment threads managing (fixes #15112) | |
| 15808 | sync: intelligent incremental sync via couchdb changes feed (fixes #15807) | yes |
| 15559 | exam: redesign UI with elapsed timer and cards (fixes #15558) | |
| 15267 | prevent download popup dialog cropping when text size is large (fixes #15263) | |
| 15266 | prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265) | |
| 15226 | feat(flutter): Flutter/Dart port of myPlanet (phases 1–28) | yes |
| 15108 | fix event calendar marking (fixes #15107) | |
| 14883 | team: add leaderboard tab (fixes #14880) | |
| 14650 | survey: smoother submissions display (fixes #14619) | |
| 14427 | Course streak | yes |
| 13928 | Add baseline profile module and installer (fixes #13927) | |
| 13848 | all: introduce Course/Grade models and wire into UI (fixes #13802) | |
| 13657 | course: Archive course My Courses library (fixes #13559) | |
| 13604 | teams: Add sort by completeness option in Survey section (fixes #13590) | |
| 13415 | voices: Add emoji reactions (fixes #13357) | |
| 13355 | Add P2P resource sharing (Wi‑Fi P2P) (fixes #13353) | |
| 13287 | profile: no char limit for edit texts (fixes #13283) | |
| 10993 | Voices video | |
| 8175 | roboscript update (fixes #7986) | |
| 4075 | robo movie (fixes #4074) | |

Avoided hot areas already in those PRs: dashboard, teams, voices, resources adapters/filters, upload/sync managers, AppDatabase/DAOs, CoursesAdapter, SurveysRepository, EventsAdapter, FileUtils, strings.xml.

---

## Task 1 — Stop per-character RecyclerView scrolls during chat typing

**Roadmap:** 7. Does not move 9/10 (Android view holder).

**Why:** AI replies animate character-by-character. Each frame calls `RecyclerView.scrollToPosition`, forcing layout on the whole chat list.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`

**Classes / functions (verified):** `ChatAdapter.ResponseViewHolder.bind`, `ChatAdapter.scrollToLastItem`, `ChatAdapter.addResponse`

**What to change:** Keep the typing animation and the existing `onAnimateTyping` callback. Stop scrolling the recycler on every character. Scroll at most when the response is first shown and when animation finishes (or throttle so a long reply does not layout every frame). Preserve `onViewRecycled` cancel behavior and clipboard copy.

**What not to change:** `ChatDetailFragment`, `VoicesAdapter`, DiffUtil, view types, message models.

**Constraints:** under ~150 lines, these two files only, no new dependencies, no TODOs, no unused helpers.

**Acceptance:** A long network-sourced reply still types out; the list stays pinned to the latest message without per-character jank. Recycled holders still cancel animation.

**Tests:** Extend `ChatAdapterTest`. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.chat.ChatAdapterTest`.

---

## Task 2 — Decode fullscreen images at screen size

**Roadmap:** 7. Does not move 9/10 (Glide/Android dialog).

**Why:** `ImageViewerUtils.showZoomableImage` loads the full file/URL into `photoView` with no size cap, so large resource photos decode at native resolution.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt`

**Classes / functions (verified):** `ImageViewerUtils.showZoomableImage`

**What to change:** Keep one fullscreen dialog, URL vs file vs gif branching, `DiskCacheStrategy.ALL`, `fitCenter`, close/dismiss cleanup. Cap the Glide request to the current screen pixel size for still images. Do not apply a still-image size override to the gif branch in a way that breaks animation.

**What not to change:** `ImageUtils`, layouts, Glide version, dialog theme.

**Constraints:** under ~150 lines, this file only (a new `ImageViewerUtilsTest.kt` is allowed only if needed to lock the size cap), no new dependencies, no TODOs.

**Acceptance:** Still images decode no larger than the display. Gif and error-placeholder behavior stay the same. Only one dialog remains active.

**Tests:** If a test file is added, run it. Otherwise run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ImageUtilsTest`.

---

## Task 3 — Avoid duplicate PDF first-page rasterization

**Roadmap:** 7. Does not move 9/10 (`PdfRenderer` is Android).

**Why:** `PdfThumbnailLoader.firstPageBitmap` checks `LruCache` only on the calling thread, then rasterizes inside `withContext(io)` and always `put`s. Concurrent binds of the same file (course/resource grids) decode the same page twice.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`

**Classes / functions (verified):** `PdfThumbnailLoader.firstPageBitmap`, `PdfThumbnailLoader.evictAll`

**What to change:** Keep the existing cache key (path + lastModified + length + target width) and scaled white-background render. Recheck the cache after switching to IO and before opening `PdfRenderer`. Do not replace a cache entry that another caller already stored for the same key. Leave `evictAll` as-is.

**What not to change:** `ResourcesPreviewLoader`, `InlineResourceAdapter`, bitmap config, dispatcher type (keep injected `DispatcherProvider`).

**Constraints:** under ~150 lines, this file only, no new dependencies, no TODOs.

**Acceptance:** Two overlapping loads for the same file/width produce one render. Cache hits still return without IO. Failures still return null.

**Tests:** Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.courses.InlineResourceAdapterTest`.

---

## Task 4 — Buffer CSV preview reads

**Roadmap:** 7. Does not move 9/10 (Android `MediaMetadataRetriever` stays).

**Why:** `ResourcesPreviewLoader.getCsvPreview` builds a new `FileReader` + `CSVParserBuilder` per call. `getTextPreview` already uses `bufferedReader`; CSV does not.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt`

**Classes / functions (verified):** `ResourcesPreviewLoader.getCsvPreview`, `ResourcesPreviewLoader.getTextPreview`, `ResourcesPreviewLoader.getAudioPreview`

**What to change:** Read CSV through a buffered reader. Keep the five-row cap, `"  |  "` formatting, null-on-failure, and `csvCache`. Do not change audio/text preview behavior or `MAX_CACHE_SIZE`.

**What not to change:** OpenCSV version, `PdfThumbnailLoader`, adapter call sites.

**Constraints:** under ~150 lines, these two files only, no new dependencies, no TODOs.

**Acceptance:** Existing CSV preview strings stay identical. Cached second call still skips disk. Invalid files still return null.

**Tests:** Extend `ResourcesPreviewLoaderTest` (`getCsvPreview returns formatted rows up to 5` and the cache test). Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ResourcesPreviewLoaderTest`.

---

## Task 5 — Stop copy-on-read in LifeCache

**Roadmap:** 7 and 1. Also 9: cache entries become immutable data, not Android-mutated `var` bags.

**Why:** `LifeCache.read` does `parsed.map { it.copy() }` on every hit, including the in-memory `ConcurrentHashMap` path. `LifeRepositoryImpl.getMyLifeForDashboard` already maps into new `MyLife` instances, so the extra copies are wasted. Copies exist only because `CachedMyLifeItem` fields are `var` (`LifeCacheTest.read_returnsDefensiveCopy_mutationDoesNotAffectSubsequentRead`).

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`

**Classes / functions (verified):** `CachedMyLifeItem`, `LifeCache.read`, `LifeCache.write`

**What to change:** Make `CachedMyLifeItem` fields immutable. Return the cached list without per-item copies. Keep kotlinx serialization, SharedPreferences write path, and the `myLifeCache_` key prefix. Replace the mutation test with an immutability/identity assertion.

**What not to change:** `LifeRepositoryImpl`, `MyLife`, Room, dashboard fragments.

**Constraints:** under ~150 lines, these two files only, no new dependencies, no TODOs.

**Acceptance:** Repeat `read` after `write` still returns the same titles/weights/visibility. Callers cannot mutate cache contents. Prefs are still hit only once per key until the next write.

**Tests:** Update `LifeCacheTest`. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.LifeCacheTest --tests org.ole.planet.myplanet.repository.LifeRepositoryImplTest`.

---

## Task 6 — Drop duplicate dictionary COUNT on launch

**Roadmap:** 7 and 3.

**Why:** `DictionaryActivity.onCreate` calls `viewModel.loadCount()` then `viewModel.loadDictionary()`. Both hit `dictionaryRepository.count()`. `loadDictionary` already emits `DictionaryLoadState.Populated(count)` after insert/already-populated.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt`

**Classes / functions (verified):** `DictionaryActivity.onCreate`, `DictionaryViewModel.loadCount`, `DictionaryViewModel.loadDictionary`

**What to change:** Launch dictionary from one ViewModel method. Remove `loadCount` if nothing else needs it (today only the activity and `DictionaryViewModelTest.loadCount emits Populated...` use it). Keep `loadDictionary` states: Populated, FileMissing, Failed. Keep download-complete receiver calling `loadDictionary()`.

**What not to change:** `DictionaryRepository`, `DictionaryDao`, `DictionaryMapper`, search (`searchWord`).

**Constraints:** under ~150 lines, these three files only, no new dependencies, no leftover unused `loadCount`.

**Acceptance:** Opening the dictionary performs one count after insert/already-populated, not two. Missing-file still starts download. Search still works.

**Tests:** Remove or replace the `loadCount` test. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.dictionary.DictionaryViewModelTest`.

---

## Task 7 — Bind course covers with the ImageView Glide target

**Roadmap:** 7. Also 10: cover bind stays a size-aware image load with no extra view work, which Compose ports can reuse via the same pixel sizes.

**Why:** `CoursesItemUtils.bindCover` uses `Glide.with(context)` instead of the `ImageView`, and when the container is unmeasured in grid mode it calls `GridSpanCalculator.columnCount` on every bind.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/CoursesItemUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/CoursesItemUtilsTest.kt`

**Classes / functions (verified):** `CoursesItemUtils.bindCover`, `CoursesItemUtils.setCoverColor`

**What to change:** Load with the cover `ImageView` as Glide’s lifecycle owner. Keep existence cache, `ObjectKey(course.courseRev)`, `override(targetWidth, targetHeight)`, subject-color fallback. When `coverContainer` already has width/height, do not recompute columns from display metrics.

**What not to change:** `CoursesAdapter` (open PR #14427), `GridSpanCalculator.kt`, `Course` model, UrlUtils auth header.

**Constraints:** under ~150 lines, these two files only, no new dependencies, no TODOs.

**Acceptance:** Cover still shows local file or authenticated URL at the same pixel override. Unmeasured grid still gets a positive size. Existence TTL cache test still passes.

**Tests:** Extend `CoursesItemUtilsTest.testBindCover_cachesExistenceCheckWithinTtlAndRestatsAfterTtl`. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.CoursesItemUtilsTest`.

---

## Task 8 — Cut ChatSearch per-query allocations

**Roadmap:** 7 and 8. Also 9: remove the `Dispatchers.Default` default so this object stays dispatcher-injected and Android-free.

**Why:** `ChatSearch.search` defaults to `Dispatchers.Default`. `searchByTitle` / `fullConvoSearch` allocate `TitleChat`/`ConvoChat` lists plus split/filter/map query parts on every search. `ChatViewModel` already passes `dispatcherProvider.default`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt`

**Classes / functions (verified):** `ChatSearch.search`, `ChatSearch.searchByTitle`, `ChatSearch.fullConvoSearch`

**What to change:** Require an explicit dispatcher (no hardcoded Default). Keep ranking (title-start, title-contains, body-start, body-contains) and `Utilities.normalizeText`. Avoid building wrapper lists when a single pass over chats is enough. Keep empty-query behavior as today’s tests define it.

**What not to change:** `ChatViewModel.kt`, `ChatHistoryFragment`, `ChatHistory` model.

**Constraints:** under ~150 lines, these two files only, no new dependencies, no TODOs.

**Acceptance:** All current `ChatSearchTest` cases still pass. Callers must pass a dispatcher. No `Dispatchers.*` remain in this file.

**Tests:** Update `ChatSearchTest` if the signature changes. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ChatSearchTest`.

---

## Task 9 — Hoist LifeAdapter bind listeners onto the ViewHolder

**Roadmap:** 7. Also 10: bind only applies state; click/drag/visibility are holder-owned, which matches later Compose event hoisting.

**Why:** `LifeAdapter.onBindViewHolder` installs click, touch, and visibility listeners on every bind, plus contentDescription string work. Drawable ids are already cached.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt`

**Classes / functions (verified):** `LifeAdapter.onBindViewHolder`, `LifeAdapter.updateVisibility`, `LifeAdapter.LifeViewHolder`, companion `findFragment` / `transactionFragment`

**What to change:** Create listeners once in the holder. On click/drag/visibility, resolve the item with `bindingAdapterPosition`. Bind should only set title, image, content descriptions, and visibility alpha/icon. Keep `drawableCache`, DiffUtil, and reorder/`onItemMoveFinished`.

**What not to change:** `LifeFragment`, `LifeViewModel`, `LifeCache`, fragment destinations in `fragmentCache`.

**Constraints:** under ~150 lines, these two files only, no new dependencies, no TODOs.

**Acceptance:** Rebind of the same holder does not require new listener objects for correctness. Hide/show and drag still update the same item. Click still opens the mapped fragment.

**Tests:** Extend `LifeAdapterTest`. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.life.LifeAdapterTest`.

---

## Task 10 — Stop UUID-per-word dictionary seeding

**Roadmap:** 7 and 1. Also 9: mapper stays platform-free (no `UUID`, no `android.*`).

**Why:** `DictionaryMapper.mapJsonArrayToEntities` calls `UUID.randomUUID()` for every dictionary word during seed. Lookups are by word (`DictionaryDao` COLLATE NOCASE), so random ids are wasted work and extra entropy on a large JSON array.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryMapper.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryMapperTest.kt`

**Classes / functions (verified):** `DictionaryMapper.mapJsonArrayToEntities`, `DictionaryMapper.str`

**What to change:** Assign a deterministic non-empty primary key from existing JSON fields (word + language + code is enough). Distinct words must still get distinct ids (`mapJsonArrayToEntities assigns unique IDs to each entity`). Keep `antonoym` → `antonym` and empty-field defaults. Do not bump Room or edit `DictionaryEntity`.

**What not to change:** `DictionaryDao`, `DictionaryRepositoryImpl`, `AppDatabase` version, `DictionaryActivity`.

**Acceptance:** Mapping tests still match field values. Two different words get different ids. Re-mapping the same JSON yields the same ids. Empty array still returns empty.

**Tests:** Update `DictionaryMapperTest` if it only asserted non-empty random ids. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.DictionaryMapperTest --tests org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest`.

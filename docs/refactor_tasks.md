# Merged refactor backlog — 2026-10-16

98 merged tasks from 120 raw proposals across 12 lists (devin ×3, copilot ×4, claude ×4, codex ×1). Sorted by rating desc. Rating = 0.35·evidence + 0.40·impact + 0.25·risk-adjusted feasibility (each 0–100), verified against the current `master` tree (HEAD `897cdbdb4`). `⚠` = touches a file other lists report as owned by an open PR — sequence after that PR lands.

## 1. Delete the assertion-free benchmark test
**Rating 93 · Provenance: devin-1#1, copilot-3#3, codex#1, claude-5#6**
`app/src/test/java/.../model/StepExamBenchmarkTest.kt` builds a 500-doc benchmark loop and ends with `println("Benchmark result: ${end - start} ms")` (line 62) — zero assertions, pays DB setup cost plus unbounded runtime. Delete the file; revisit if a microbenchmark harness (e.g. `BenchmarkRule`) is adopted.

---

## 2. Skip retries on permanent failures in RetryInterceptor
**Rating 90 · Provenance: claude-10#1**
`data/api/RetryInterceptor.kt` (`intercept`, ~:45-60) retries `SocketTimeoutException`/`IOException` like 5xx but has no classification for permanent failures (404, 4xx, malformed URLs, certificate errors) — callers see ~7 s delay for a permanently failing endpoint offline. Add a non-retryable classification (4xx, TLS failures) that breaks immediately; add unit tests distinguishing retried vs. returned responses.

---

## 3. Consolidate Robolectric SDK pins
**Rating 88 · Provenance: devin-1#7, claude-5#1**
Six pins cover only five allowed SDK levels {26,27,28,33,36}: `SecurePrefsTest.kt:20 [O_MR1]`, `PdfThumbnailLoaderTest.kt:21 [28]`, `TeamResourcesAdapterTest.kt:24` + `ResourcesCardBinderTest.kt:15 [36]`, `VersionUtilsTest.kt:161 [O]` and `:179 [P]` (method-level; O and P jars exist). Drop the 27/28 pins and the P method pin (one sandbox emission, not two per class); pin default `sdk=36` in `robolectric.properties` so unpinned tests get the warmed jar; keep the class-level `@Config` where the code path is version-sensitive.

---

## 4. Drop the two widget smoke tests in CourseRatingUtilsTest
**Rating 88 · Provenance: copilot-2#1, claude-5#6**
`utils/CourseRatingUtilsTest.kt:116,:136` spin up `mockk<Context>`/`mockk<Resources>`/`LayoutInflater` per call (~14 s measured) just to prove the production wrapper doesn't crash — a hypothetical contract with 100-callable mocks. Production entry points `computeRatingDisplay`/`parseRating` exist; wiring is covered by `BaseContainerFragmentTest.kt:35`. Delete the two smoke tests; leave a one-line comment noting the wrapper is trivially exercised.

---

## 5. Publish step content before the server-availability probe
**Rating 86 · Provenance: claude-10#3**
`ui/courses/CoursesStepsViewModel.kt` `loadStep` (~:62) awaits `configurationsRepository.checkServerAvailability()` (:84) before emitting `_uiState` (:106) — opening a course offline blocks the UI ~5-15 s on a probe that only decorates a banner. Emit a "loaded, unverified" state first so the fragment renders immediately, then update the server-banner field when the probe resolves; add a test asserting state arrives before the check completes.

---

## 6. MyLibraryDaoTest: share the DB and shrink the fixture rows
**Rating 85 · Provenance: devin-1#3, codex#3**
`data/room/dao/MyLibraryDaoTest.kt` rebuilds the in-memory DB in `@Before` (:25) and seeds `(1..1200)` rows in three tests (:38,:58,:79+) plus 15 `runBlocking` calls. Promote the DB to a lazy class-level instance cleared between tests; rewrite fixtures as sparse edge-case rows (empty page, page boundary, descending order) instead of synthetic volumes; drop redundant coverage now owned elsewhere.

---

## 7. UrlUtilsTest: hoist `mockkObject(UrlUtils)` to class level
**Rating 85 · Provenance: copilot-2#2, claude-5#2**
`utils/UrlUtilsTest.kt` calls `mockkObject(UrlUtils)` at :34 then `unmockkAll()` at :43 plus nine in-body `unmockkObject` calls (:392-495) — ~18 s of test time, most of it mock machinery. Mock once per class in `@Before`/`@After` with `mockkObject`/`unmockkAll`, drop the per-test unmock calls, keep the single unit test that exercises the real `UrlUtils` so the production code isn't fully masked.

---

## 8. Chat history: don't reload the conversation on every foreground
**Rating 84 · Provenance: claude-10#4**
`ui/chat/ChatViewModel.kt` exposes `_refreshChatSignal = MutableSharedFlow(replay = 1)` (:70) feeding `updatesFor("chats")` (:75); every `onStart`/navigate-forward re-enters `loadChats`/`loadChats` and re-serializes the list. Cache the last sync timestamp + remote revision for the active room and only refresh when the signal fired or the room changed; run the repository fetch + JSON parse inside `async` on the IO dispatcher so the list doesn't stall behind a network call on every resume.

---

## 9. Exporter: group questions by each submission's exam, not one `examId`
**Rating 84 · Provenance: claude-12#2**
`repository/SubmissionsRepositoryExporter.kt:184-186` fetches `questionDao.getByExamId(examId)` once for `submissions.firstOrNull()?.parentId` — mixed-exam exports render every other exam's questions blank. Build `Map<examId, questions>` keyed per `submission.parentId`, resolve the submission's own questions inside the loop, and add an export test with two distinct `examId`s.

---

## 10. DownloadServiceTest: hoist static mocks to class level
**Rating 84 · Provenance: copilot-2#3, claude-5#3**
`services/DownloadServiceTest.kt` builds `mockkStatic(NotificationUtils)`/`(ServiceDependenciesEntryPointKt)`/`mockkObject(UrlUtils)` at :63-65 with `unmockkAll` at :75 and per-test `WorkManagerImpl`/`NotificationManagerCompat` mocks at :127,:157,:275 — full static-mock machinery per test (~3.2 s setup × 8). Hoist the three to `@Before`/`@After` and keep only the two genuinely per-test mocks; assert the same failure/success paths.

---

## 11. Clear the obsolete Glide request before setting a placeholder
**Rating 82 · Provenance: claude-9#3**
`utils/ImageUtils.kt` `loadProfileImage` (:9) and `loadImage` (:25) set the placeholder drawable without clearing the previous Glide request first — a recycled row can flash the old image until the new request resolves. Call `Glide.with(...).clear(target)` (or `RequestOptions.placeholderOf` after `clear`) before `into()`, and keep `loadImage` in the same fix.

---

## 12. Resolve `updateVisibility` to a single my_life row
**Rating 82 · Provenance: claude-12#1**
`data/room/dao/MyLifeDao.kt:33-34` matches `_id OR imageId OR title`, so toggling one item can hide a second that shares an `imageId`/`title`. Have `LifeFragment.kt` resolve the one row (lookup by `_id`, fall back to `imageId`/`title`) then pass the concrete `_id` to the DAO; add a DAO test proving only the intended row updates.

---

## 13. TakeCourseFragment: log a course visit once per open
**Rating 82 · Provenance: claude-10#5**
`ui/courses/TakeCourseFragment.kt:241` calls `viewModel.logCourseVisit` from `setCourseData`, which re-fires on every fragment recreation/resume — one visit inflates the activity counters. Track `lastLoggedCourseId`/`loggedAt` in `TakeCourseViewModel` or a fragment-level `seen` set keyed by `courseId`, and emit only on the first bind after open.

---

## 14. Parallize the two-URL reachability probe
**Rating 82 · Provenance: claude-10#2**
`utils/ServerReachabilityProvider.kt:36-45` probes `urlsToTry` serially (`for (url) { if (tryConnect(url)) break }`) — an offline flow waits `connectTimeout × 2`. Fire `async { tryConnect(url) }` for both URLs, `awaitAll`, cancel losers on the first `true`; cache the result as today.

---

## 15. Replace `Thread.sleep(10)` with deterministic waits in adapter tests
**Rating 82 · Provenance: devin-1#5, copilot-3#4**
`ui/chat/ChatHistoryAdapterTest.kt:55` and `ui/user/AchievementsAdapterTest.kt:84,:111` sleep 10 ms (plus `idleMainLooperUntil` in `TeamResourcesAdapterTest` ~:103-110) to wait for main-thread dispatches. Poll a shared `waitForIdle`/`flushBackgroundThreadScheduler` (already in the codebase) or expose a test dispatch hook so the scheduler drains before asserting — removes timing-dependent flakes without extra sleeps.

---

## 16. UploadConfigsTest: hoist object mocks
**Rating 82 · Provenance: copilot-2#4, claude-5#4**
`services/upload/UploadConfigsTest.kt` `mockkObject(VersionUtils)` (:46), `mockkObject(NetworkUtils)` (:49), `unmockkAll` (:75) per test. Hoist to class level.

---

## 17. DownloadUtilsTest: hoist object mocks to class level
**Rating 81 · Provenance: copilot-2#7, claude-5#7**
`utils/DownloadUtilsTest.kt` calls three `mockkObject` at :52/:55/:57 plus `unmockkAll` at :64 and in-body `unmockkObject` at :208/:228 — per-test static machinery. Hoist to `@Before`/`@After`.

---

## 18. FeedbackTest: hoist mocks; drop the dead TextUtils mock
**Rating 81 · Provenance: copilot-2#6, claude-5#5**
`model/FeedbackTest.kt` mocks `TextUtils` (:26) — `Feedback.kt` has no `TextUtils` import, so the mock is dead — and `NetworkUtils` (:31) per test with `unmockkAll` (:37); the in-test `JsonParser` mocks (:180,:203,:216) also repeat. Hoist the real mocks to class level, delete the dead one, and dedupe `JsonParser` setup.

---

## 19. TeamDao.observeAll → targeted membership/root-team queries
**Rating 80 · Provenance: claude-10#7, devin-11#1** ⚠ `TeamsRepositoryImpl` flagged open-PR-owned in other lists — implement the DAO part first.
`data/room/dao/TeamDao.kt:19` emits the full table; `TeamsRepositoryImpl.kt:210,298` filters it in Kotlin for `docType="membership"`/`teamId` rows, and `markMembershipsForLeave` (:1367) loops one row per write. Add `observeMemberships`/`observeRootTeams` queries and a batch update; downstream emits only matching rows.

---

## 20. Coalesce InlineResourceAdapter payload refreshes
**Rating 80 · Provenance: claude-9#2**
`ui/resources/InlineResourceAdapter.kt` `checkFileExist` (:171) → `resourceFile.exists()` (:192) per bind plus `onCurrentListChanged` (:82) full-list refresh — N stat calls per layout pass. Cache `exists()` per path (or per file mtime) and debounce/coalesce `submitList` updates so a bulk refresh doesn't trigger N item changes.

---

## 21. Hoist ResourcesListFilter canonicalization and short-circuit fields
**Rating 80 · Provenance: copilot-7#3, claude-9#4**
`ui/resources/ResourcesListFilter.kt` `matchesFacets` (:71) calls `equals(ignoreCase)` at :76/:79/:81 and `canonicalMedium` at :91/:107 per item per facet — recalculates case-normalization and medium-string joins on every list change. Canonicalize `mediums`/`levels`/`subjects` once per filter, hoist `lowercase()`/`trim()` outside the loop, and order checks so the cheapest fields short-circuit first.

---

## 22. Delete the two EditText-mock tests in ViewExtensionsTest
**Rating 80 · Provenance: copilot-2#10**
`utils/ViewExtensionsTest.kt` mocks `EditText` at :27 and :38 — ~5.9 s inside one method per copilot's measurement — for tests that exercise Robolectric mock plumbing more than production code. Delete both tests; the remaining cases cover the real extension behavior.

---

## 23. Delete the dead `OnDiffRefreshListener` path
**Rating 80 · Provenance: devin-6#5**
`callback/OnDiffRefreshListener` is only called via `RealtimeSyncMixin.kt:39-47`; `shouldAutoRefresh` is overridden `false` in `ResourcesFragment:800` and `CoursesFragment:566`, so the mixin never auto-refreshes and the listener path is dead code. Remove the interface, the mixin callback, and the overrides; keep the manual refresh triggers.

---

## 24. BaseExamFragment: pre-render Markwon editor
**Rating 80 · Provenance: claude-10#10**
`base/BaseExamFragment.kt:191` constructs `MarkwonEditorTextWatcher.withProcess(markwonEditor)` — the editor renders markdown synchronously per keystroke. Switch to `withPreRender(markwonEditor, Dispatchers.IO, view)` (same Markwon artifact already in use) so edit latency drops while typing long answers.

---

## 25. Delete the assertion-free RealtimeSyncManager noop test
**Rating 80 · Provenance: claude-5#6**
`services/sync/RealtimeSyncManagerTest.kt:60-63` ends `assertTrue(true)` — a doc-only placeholder. Delete `testNotifyWithoutCollectorsDoesNotThrow` or replace it with a real assertion (e.g. a collector sees the emission); the other coverage already exercises `notify` semantics.

---

## 26. Fail-fast the closed-port reachability test
**Rating 80 · Provenance: copilot-2#5**
`services/sync/ServerUrlMapperReachabilityTest.kt` starts `HttpServer.create`, stops it (`stop(0)`), then waits through `connectTimeout/readTimeout = 5000` (`ServerUrlMapper.kt:122-123`) — ~5 s of wall clock for a known-dead socket. Point the test at a deliberately closed port with a shorter timeout override (or inject the timeout) so it asserts failure in ~3 ms.

---

## 27. ExamDaoTest: sparse fixture seeds
**Rating 79 · Provenance: codex#2**
`data/room/dao/ExamDaoTest.kt` seeds 1000-row fixtures for boundary assertions (plus 13 `runBlocking`). Seed only the rows the assertion needs (empty, boundary index, unordered) and keep `runTest` semantics — fixture cost drops to milliseconds while the exercised code path stays identical.

---

## 28. CourseProgressDaoTest: sparse fixture seeds
**Rating 79 · Provenance: codex#7**
`data/room/dao/CourseProgressDaoTest.kt` seeds 1000 rows (with distinct chunk sizes 250/899 per copilot's count) for count/boundary checks. Seed sparse edge rows only.

---

## 29. Replace per-view OnTouchListeners with one hide-on-touch listener
**Rating 78 · Provenance: claude-10#9**
`utils/KeyboardUtils.kt` `setupUI` (:14-38) installs a fresh `OnTouchListener` on every non-EditText child — dozens of listeners per screen, each firing `hideSoftKeyboard` on every `MotionEvent` including ACTION_MOVE. Install a single shared listener that only reacts on `ACTION_DOWN`, and stop the listener from firing while scrolling.

---

## 30. Share one AppDatabase across each round-trip test class
**Rating 78 · Provenance: devin-1#2**
`data/room/AppDatabaseRoundTripTest.kt:37` and `CollapsedEntitiesRoundTripTest.kt:43` rebuild `Room.inMemoryDatabaseBuilder` per test (~2 s each in agent measurements). Make the DB a class-level `@Before`/`@After` instance and `clearAllTables()` between tests; keep the per-test `dao.insert` the same.

---

## 31. TeamTaskDaoTest: sparse fixture seeds
**Rating 78 · Provenance: codex#4**
`data/room/dao/TeamTaskDaoTest.kt` seeds 1000-row fixtures (6 `runBlocking`, copilot claimed 5 — count was off, premise holds). Same sparse-seeding treatment.

---

## 32. AnswerDaoTest: sparse fixture seeds
**Rating 78 · Provenance: codex#5**
`data/room/dao/AnswerDaoTest.kt` (6 `runBlocking`, 1000-row fixtures). Same sparse-seeding treatment.

---

## 33. RemovedLogDaoTest: sparse fixture seeds
**Rating 78 · Provenance: codex#6**
`data/room/dao/RemovedLogDaoTest.kt` 1200-row fixtures for chunk/count checks. Same sparse-seeding treatment.

---

## 34. TeamLogDaoTest: sparse fixture seeds
**Rating 78 · Provenance: codex#8**
`data/room/dao/TeamLogDaoTest.kt` 1000-row fixtures (4 `runBlocking`). Same sparse-seeding treatment.

---

## 35. WebViewActivity: inject a stylesheet instead of walking the DOM
**Rating 78 · Provenance: copilot-7#9**
`ui/viewer/WebViewActivity.kt` `applyNightMode` (:243) runs `querySelectorAll('*')` + `getComputedStyle` per element at :252/:269 — O(n) DOM walks on every page. Inject a CSS rule (e.g. `* { filter: invert(1) }` or a color-scheme stylesheet) once after `onPageFinished`, covering all nodes in one pass.

---

## 36. VoicesAdapterHelper: chunk the reveal through TextRevealer
**Rating 78 · Provenance: claude-9#1**
`ui/voices/VoicesAdapterHelper.kt` reveals text char-by-char in a `for` loop with `delay(10L)` (:19-25) — 300-frame animations on long blurbs. Route through `utils/TextRevealer.reveal` (`:14`, `chunkSize` :12) or batch the loop so a 300-char reveal finishes in ~30 steps.

---

## 37. ChatHistoryAdapter: stop leaking `chatTitle` into recycled holders
**Rating 78 · Provenance: copilot-8#3**
`ui/chat/ChatHistoryAdapter.kt` stores `chatTitle` on the adapter at :54 — written in `onBindViewHolder` (:137), read in `onCreateViewHolder` (:101) — so a holder created mid-scroll shows the wrong title. Move the title into the bound item or the ViewHolder itself and drop the field.

---

## 38. MyHealthFragment+HealthViewModel: load the patient list once
**Rating 78 · Provenance: claude-10#6**
`ui/health/MyHealthFragment.kt` rebuilds `rvRecords` (layoutManager + adapter + `scrollToPosition`) whenever a spinner fires (:195-204) and `HealthViewModel.searchPatients` (:101-103) cancels only `searchJob` while `loadPatients` (:95) runs untracked — initial load overlaps a search on rotation. Track `loadPatients`/`searchPatients` under one supervisor job, cancel superseded loads, and rebuild the RecyclerView only when the list itself changes.

---

## 39. ResourceActivityDao: GROUP BY the hot query + guard the cached sort
**Rating 78 · Provenance: claude-10#8**
`data/room/dao/ResourceActivityDao.kt:26` already has a GROUP BY/ORDER BY projection for the hot resource lookup; `observeByUserAndType` (:29-30) still returns the full table. Add a `GROUP BY resourceId` DAO projection for `observeByUserAndType`, and in `ResourcesViewModel.getCachedResources` (:91-99) skip the re-sort when `_resourcesState.value.isEmpty()` is false.

---

## 40. ResourcesCardBinder: bound cover decode + drop double stat
**Rating 77 · Provenance: copilot-7#1**
`ui/resources/ResourcesCardBinder.kt` `coverWidthDp` (:29) decodes at full size; `showMediaPreview` (:104) has no size override; `showPdfPreview` (:128) stats `file.exists()` though `cachedFileLength` (:183) already exists. Bound the decode by the view size and reuse the cached length instead of re-statting.

---

## 41. NewsDaoTest: sparse fixture seeds
**Rating 76 · Provenance: codex#9**
`data/room/dao/NewsDaoTest.kt` 1000-row fixtures (11 `runBlocking`). Same sparse-seeding treatment.

---

## 42. Hoist `mockkStatic` in the three utility tests
**Rating 76 · Provenance: devin-1#4**
`utils/SyncTimeLoggerTest.kt` re-mocks `Log` at 7 sites (:20,:70,:112,:173,:224,:293,:342); `utils/UtilitiesTest.kt:28-29` mocks `Looper`+`Toast`; `utils/WebViewSafetyTest.kt:20` mocks `Uri` — Robolectric already provides these shadows, so the per-test static mocks are pure overhead. Hoist to `@Before`/`@After` (or drop where a shadow suffices).

---

## 43. NotificationActionReceiverTest: hoist statics to class level
**Rating 76 · Provenance: claude-5#9**
`services/NotificationActionReceiverTest.kt` `mockkObject(NotificationUtils)` (:65), `mockkStatic(ServiceDependenciesEntryPointKt)` (:68), `unmockkAll` (:87), plus in-test `Log` mock (:174). Hoist the three statics; keep the single `Log` mock only where needed.

---

## 44. BasePermissionActivityTest: hoist static mocks
**Rating 76 · Provenance: copilot-2#8**
`base/BasePermissionActivityTest.kt` `mockkStatic(ContextCompat)` (:31), `mockkStatic(Process)` (:32), plus per-test `NotificationManagerCompat` (:71). Hoist the two statics; keep the per-test one.

---

## 45. Bound DAO `IN` queries in TeamDao
**Rating 76 · Provenance: claude-12#3**
`data/room/dao/TeamDao.kt:13` `getByIds(ids)` and :35 `deleteByIds(ids)` bind unbounded lists — SQLite hits the 999-variable cap on wide syncs. Chunk the lists internally (or add `WHERE _id IN` pagination at call sites); add a test with >999 ids.

---

## 46. ResourcesViewModel.getFilterFacets: cache until content changes
**Rating 76 · Provenance: copilot-7#10**
`ui/resources/ResourcesViewModel.kt` `getFilterFacets` (:135) recomputes `lowercase` (:125) + `canonicalMedium` (:131) per facet per call; the title-sort decoration also rebuilds at ~:203. Cache facet computation keyed on the backing list version and decorate the title list in place.

---

## 47. Bound the SyncTimeLogger stress loop
**Rating 75 · Provenance: devin-1#6**
`utils/SyncTimeLoggerConcurrencyTest.kt:39-56` runs `threadCount=10` × `iterationsPerThread=100` (~1000 launches + handler dispatches) before `awaitTermination(10, SECONDS)` — a smoke-level wall clock that scales linearly with machine slowness. Cap the loop (e.g. 2 threads × 10 iterations) and assert the drain-time bound deterministically, or gate the big test behind a system property so the default suite stays fast.

---

## 48. HealthViewModel: cancel superseded initial loads
**Rating 75 · Provenance: claude-9#5**
`ui/health/HealthViewModel.kt` `searchJob` (:68) is tracked but `loadPatients` (:95) launches untracked — a configuration change or fast spinner spins up a second untracked load. Track all loads under one job family and cancel the previous on restart.

---

## 49. TeamPagerAdapter: skip work when pages haven't changed
**Rating 75 · Provenance: claude-9#9**
`ui/teams/TeamPagerAdapter.kt` `updatePages` (:37) diffs against `currentIds` (:26) and rebuilds page ids (:54) on every update — even when the caller hands it the same list. Early-return on equal inputs, and skip the diff when ids are identical.

---

## 50. ChatAdapter: append without a full-list copy
**Rating 75 · Provenance: copilot-7#5**
`ui/chat/ChatAdapter.kt` `addQuery` (:119-122) and `addResponse` (:127-131) copy `currentList` + `submitList` — O(n) alloc per append. Keep a backing list and submit a copy only for the diff engine, or use `submitList` with the same list and a payload.

---

## 51. MeetupTest: hoist the NetworkUtils mock
**Rating 74 · Provenance: claude-5#5**
`model/MeetupTest.kt:28` per-test `mockkStatic(NetworkUtils)` + `unmockkAll` (:34). Same hoist.

---

## 52. Bound the reachability probe / dedupe URL-fallback probing
**Rating 74 · Provenance: devin-11#7** ⚠ `ServerUrlMapper` flagged in KMP service PRs.
`services/ServerReachabilityWorker.kt` (:104-108, :170-189) and `services/SubmissionsUploader.kt` each hand-roll `processUrl` + `withTimeoutOrNull(15000)` + `isServerReachable` + `updateUrlPreferences`. Extract one `ReachabilityProbe` (or move into `ServerReachabilityProvider`) both call.

---

## 53. InlineResourceAdapter: cache `File.exists()` per path
**Rating 74 · Provenance: copilot-7#8**
`ui/resources/InlineResourceAdapter.kt` `checkFileExist` (:171) → `resourceFile.exists()` (:192) on every bind. Memoize per path (invalidate on `notifyDataSetChanged` or a clear() call) so a re-layout doesn't restat the filesystem.

---

## 54. RequestsAdapter: dispatch one payload diff instead of a rebind
**Rating 74 · Provenance: copilot-8#2**
`ui/teams/requests/RequestsAdapter.kt` `setData` (:36) calls `submitList` (:41) then `notifyItemRangeChanged` (:42) — every diffed item rebinds twice. Move the refresh logic into `DiffUtil.ItemCallback` payloads (`onBindViewHolder(payloads)`); drop the range-changed call.

---

## 55. Adapter date caches: bound with an LRU
**Rating 74 · Provenance: copilot-8#1**
`PersonalsAdapter.kt:23`, `FeedbackAdapter.kt:36`, `HealthUsersAdapter.kt:17`, `UserArrayAdapter.kt:28` each keep `dateCache = HashMap<Long,String>()` that grows unboundedly per adapter lifetime (copilot hedged on `MembersAdapter` — it has no such cache; apply where present). Swap in a bounded `LruCache` (e.g. 256 entries) keyed by epoch-day.

---

## 56. Share one activity across DashboardElementActivityNavigationTest
**Rating 74 · Provenance: copilot-2#9**
`ui/dashboard/DashboardElementActivityNavigationTest.kt` calls `Robolectric.buildActivity` in each of 4 tests (:55,:68,:87,:113). Build once per class, reset the scenario between tests.

---

## 57. Push the guest-user exclusion into NewsDao
**Rating 74 · Provenance: devin-11#4** ⚠ `NewsDao`+`VoicesRepositoryImpl` flagged open-PR-owned.
`repository/VoicesRepositoryImpl.kt:38-43` `getNewsForUpload` calls `newsDao.getAll()` then filters `userId?.startsWith("guest")` in Kotlin. Add `WHERE userId NOT LIKE 'guest%'` to the DAO query so the table never leaves SQLite for guests.

---

## 58. Debounce the realtime collectors (three ViewModels/Fragments)
**Rating 73 · Provenance: devin-6#1, devin-6#2, devin-6#3** ⚠ `SurveysViewModel` flagged open-PR-owned in copilot-7's exclusion list.
`SurveysViewModel.kt:101` `updatesFor("exams")` has no debounce; `TeamDetailFragment.kt:416` → `getTeamUpdateFlow` (:418) → `refreshTeamDetails` (:263/:421) refires on every sync tick; `ChatViewModel` `updatesFor("chats")` (:75) re-emits via `replay=1` SharedFlow (:70). Add `.debounce()`/`conflate()` or a minimum-refresh interval on each collector so rapid sync events collapse into one UI update.

---

## 59. Bound DAO `IN` queries in UserDao
**Rating 72 · Provenance: claude-12#5**
`data/room/dao/UserDao.kt` `getUsersByAnyIds(userIds)` (:13-14) and `getGuestUsersByNames(names)` (:45+) bind unbounded `IN` lists. Same chunking treatment.

---

## 60. Timing script: add `--fail-over` and a package rollup
**Rating 72 · Provenance: devin-1#8**
`.github/scripts/test_timing_summary.py` has `--warn-over`/`--warn-class-over` but no hard-fail flag and no per-package rollup. Add `--fail-over` (nonzero exit when exceeded) plus a package-level aggregation line so slow-test regression blocks the job.

---

## 61. Timing script: add `--fail-class-over` and an always-on budget footer
**Rating 72 · Provenance: copilot-3#10**
Same file — add a class-level fail flag and print the budget summary unconditionally so the footer appears even when nothing is over threshold.

---

## 62. CSV preview: stop parsing after the fifth row
**Rating 72 · Provenance: claude-9#6**
`ui/resources/ResourcesPreviewLoader.kt` reads `for (row in reader) { if (count >= 5) break ... }` (~:66-70) — the iterator parses the 6th record before the check fires, and a malformed trailing line can throw mid-preview. Break on `count == 5` before reading the next record, or wrap the loop so a parse error after the preview point is ignored.

---

## 63. Bound `ResourcesMediaType.classify` cache
**Rating 72 · Provenance: copilot-7#4**
`utils/ResourcesMediaType.kt` `classify` (:20) re-derives `extensionOf` (:69-72) → `FileUtils.getFileExtension` per call. Memoize `classify(path)` per canonical extension (bounded LRU) since the mapping is pure.

---

## 64. Cache `findHtmlCoverImage` positive hits
**Rating 72 · Provenance: copilot-7#2**
`utils/FileUtils.kt` `findHtmlCoverImage` (:123) `walkTopDown().maxDepth(4)` (:128) scans the directory on every call. Cache the resolved cover path per directory (invalidate on file-change signal or TTL).

---

## 65. Normalize ExamAnswerUtils strings once per comparison
**Rating 72 · Provenance: copilot-7#6**
`utils/ExamAnswerUtils.kt` calls `Locale.getDefault()` (:72,:83,:91) plus `lowercase`/`sorted` inside the comparison loop — the same string is normalized per call. Hoist the normalized values out of the loop and pass a fixed `Locale`.

---

## 66. FreeSpaceWorker: throttle progress updates
**Rating 72 · Provenance: copilot-7#7**
`services/FreeSpaceWorker.kt` calls `setProgress` (:31,:77) every-10-files (:75-76) — still per-batch handler posts on the main thread. Sample progress at a coarser interval (e.g. every 100 files or 500 ms) so notifications don't queue during large purges.

---

## 67. Timing script: flag static-mock churn
**Rating 71 · Provenance: claude-5#8**
Same file — add a heuristic that reports tests dominated by `mockkStatic`/`mockkObject` setup (grep the test source for mock counts vs. assert counts) so future mock-hoist work has a measurable signal.

---

## 68. Fix the Log-mocking advice in TESTING.md
**Rating 71 · Provenance: claude-5#10**
`docs/TESTING.md` (~:341) claims `mockkStatic(Log::class)` is needed "otherwise the JVM has no `android.util.Log`" — wrong under `unitTests.returnDefaultValues = true` (`app/build.gradle:22`), and `robolectric.properties` provides shadows anyway. Rewrite the section to describe mock-once-per-class (and prefer shadows where they exist).

---

## 69. merge the three DownloadService satellite test files
**Rating 70 · Provenance: devin-1#10**
`services/DownloadServiceCompletionTest.kt` (28L), `DownloadServiceRemainingCountTest.kt` (67L), `DownloadServiceUrlSelectionTest.kt` (128L) are single-purpose satellites of the main test. Merge into `DownloadServiceTest` as nested classes (or keep separate files but share the fixture builder) — one Robolectric sandbox instead of three.

---

## 70. Migrate the three ViewModel tests onto MainDispatcherRule
**Rating 70 · Provenance: copilot-3#9**
Three ViewModel tests hand-roll `Dispatchers.setMain`/`resetMain`/`UnconfinedTestDispatcher` — `utils/MainDispatcherRule.kt` already exists and accepts a `TestDispatcher`. Swap to the rule for consistency (code-health, not speed).

---

## 71. runBlocking → runTest in the round-trip tests
**Rating 70 · Provenance: copilot-3#6**
`AppDatabaseRoundTripTest.kt` (6 `runBlocking`) and `CollapsedEntitiesRoundTripTest.kt` (9). Swap to `runTest` for virtual-time discipline; note this conflicts with task #17's shared-DB change — land whichever lands first, rebase the other.

---

## 72. runBlocking → runTest in the five light DAO tests
**Rating 70 · Provenance: copilot-3#1**
CourseDaoTest (7), QuestionDaoTest (3), TeamTaskDaoTest (6), TeamLogDaoTest (4), SubmitPhotosDaoTest (4) — mechanical `runBlocking`→`runTest` swap.

---

## 73. runBlocking → runTest in the four heavy DAO tests
**Rating 70 · Provenance: copilot-3#7**
MyLibraryDaoTest (15), ExamDaoTest (13), TeamDaoTest (12), NewsDaoTest (11). Same swap; pairs naturally with the fixture-shrink tasks above.

---

## 74. runBlocking → runTest in the remaining DAO tests
**Rating 70 · Provenance: copilot-3#8**
UserDaoTest (10), MeetupDaoTest (9), TagDaoTest (7), SubmissionDaoTest (6), AnswerDaoTest (6), FeedbackDaoTest (5).

---

## 75. Delete `LifeRepositoryTest` — coverage moved to the impl test
**Rating 70 · Provenance: copilot-3#2**
`repository/LifeRepositoryTest.kt` (3 tests: :36,:45,:63 — copilot said 2, actual is 3) duplicates cases already covered by `LifeRepositoryImplTest.kt` (:168,:191,:55,:70). Delete the file after confirming each case exists in the impl test.

---

## 76. Drop the redundant tests in VersionUtilsTest
**Rating 70 · Provenance: codex#10**
`utils/VersionUtilsTest.kt` has redundant cases at :147,:196,:210 that re-assert paths already covered by the cache-behavior tests (and the O/P pins change the cost but not the logic). Delete the redundant trio; keep the pinned-version cases.

---

## 77. Date-formatter + payload correctness on remaining adapters
**Rating 69 · Provenance: copilot-8#5,#6**
`ui/surveys/SurveysAdapter.kt` calls `context.getString` (:110-112) and `userId?.startsWith("guest")` (:115) per bind; `ui/notifications/NotificationsAdapter.kt` builds `getDateFormatter` (:44-45) with `Locale.getDefault()` per bind and keys `parsedHtmlCache` (:42) on `formattedText.toString()` (:159). Resolve strings/labels at bind-in or adapter level, and key the parsed cache on the raw input + locale.

---

## 78. Resolve team/label strings once (selection adapters)
**Rating 68 · Provenance: copilot-8#4**
`ui/teams/TeamsSelectionAdapter.kt` `cachedTeamsLabel` (:24) is read but `getString` (:43) still refires in bind; same pattern in `ChatShareTargetAdapter`. Initialize the label once in `init`/first bind and drop the per-item `getString`.

---

## 79. CollectionsFragment: bound the normalized-name cache
**Rating 68 · Provenance: claude-9#7**
`ui/resources/CollectionsFragment.kt` `refreshTagList` (:109) calls `lowercase` (:113,:115) per tag per refresh. Cache normalized names in a bounded map keyed on tag id; invalidate when the tag list changes.

---

## 80. TagEntity.getTagsArray: build the Gson string directly
**Rating 68 · Provenance: claude-9#8**
`model/TagEntity.kt` `getTagsArray` (:63) does `buildJsonArray{...}.toGson()` — a double serialization. Build the JSON array directly to a string (or cache per tag-set) so callers don't pay the intermediate tree.

---

## 81. ResourcesViewModel title sort: decorate in place
**Rating 68 · Provenance: claude-9#10**
`ui/resources/ResourcesViewModel.kt` title sort decorates then re-sorts (~:203) on every refresh. Decorate in place (compute the sort key once per item, sort, drop the key) or cache the sorted projection between content changes.

---

## 82. Normalize the dictionary lookup once
**Rating 67 · Provenance: claude-12#4**
`ui/dictionary/DictionaryViewModel.kt:58` trims the query; `repository/DictionaryRepositoryImpl.kt:25-26` does not lowercase/normalize — callers passing inconsistent casing miss. Apply `word.trim().lowercase()` inside the repo (single normalization point), keep the DAO query case-exact.

---

## 83. Remaining adapter hot-path cleanups
**Rating 67 · Provenance: copilot-8#7,#8,#9,#10**
`ui/personals/PersonalsAdapter.kt` `openResource` (:67) does `substringAfterLast` (:68) per call — precompute the `ResourceType` map; `ui/health/HealthUsersAdapter.kt` (:76-77) + `ui/user/UsersAdapter.kt` (:20-21) compute `avatarSize` lazily per holder — hoist to `init`; `ui/dashboard/DashboardSurveysAdapter.kt` sets `setTextColor` per bind (:44) — the "raw position" claim is stale (`bindingAdapterPosition` already used at :30) but the per-bind color work is real; `ui/references/ReferencesAdapter.kt` `pos == 0` (:27) check — fine, keep; `ui/teams/members/MembersAdapter.kt` `submitList`+`notifyItemRangeChanged(...PAYLOAD_KEY...)` (:187) — same double-dispatch fix as RequestsAdapter.

---

## 84. Batched ViewModel user-reads → UserReadRepository seam
**Rating 66 · Provenance: claude-12#6-#10**
Five ViewModels inject the broad `UserRepository` but only call `getUserModel`/`getUserById`/`getUsersByIds`: `RequestsViewModel` (:52,:69,:87,:101,:135), `ChatViewModel` (:53,:185,:291), `AddResourceViewModel` (:18,:21), `ResourceDetailViewModel`, `ProgressViewModel`, `CourseProgressViewModel`, `FeedbackComposerViewModel`. `UserReadRepository` already exists (`repository/UserReadRepository.kt` — three methods) and is bound in `RepositoryModule.kt:229`. Repoint each constructor + callsite to the narrow interface.

---

## 85. Route submission/exam/question reads through SubmissionsRepository
**Rating 66 · Provenance: devin-11#2** ⚠ repository impls flagged open-PR-owned under the strict exclusion rule.
`repository/SurveysRepositoryImpl.kt` (:52-54) injects `examDao`/`questionDao`/`submissionDao` directly; `ProgressRepositoryImpl` injects `examDao`/`questionDao`. Expose narrow `submissionsRepository` read methods and drop the DAO injections from consumers.

---

## 86. Centralize search-activity + removed-log writes behind ActivitiesRepository
**Rating 66 · Provenance: devin-11#3** ⚠ same collision caveat.
`CoursesRepositoryImpl` (:67,:69,:341,:426), `ResourcesRepositoryImpl` (:59,:61,:477,:542,:602), `UserRepositoryImpl` (:83-84,:1259-60) each inject `searchActivityDao`/`removedLogDao`/`offlineActivityDao` — `offlineActivityDao` at `UserRepositoryImpl.kt:83` is injected but never used. Move the audit/removed-log writes behind `ActivitiesRepository` methods and drop the dead injections.

---

## 87. Extract challenge-dialog evaluation out of DashboardViewModel
**Rating 66 · Provenance: devin-11#6** ⚠ `DashboardViewModel` flagged PR-owned (#17811).
`ui/dashboard/DashboardViewModel.kt:286` `evaluateChallengeDialog` builds `ChallengeDialogData` (:60,:327) inline with progress→status mapping (:320,:348). Move the pure evaluation into a use-case/helper; the VM just collects the flow and emits the event.

---

## 88. Dedup voices draft-image serialization
**Rating 66 · Provenance: devin-11#5** ⚠ `BaseVoicesFragment`/`ReplyActivity`/`News.kt` flagged open-PR-owned.
`base/BaseVoicesFragment.kt:162-164` and `ui/voices/ReplyActivity.kt:222-223` build identical `JsonObject { imageUrl, fileName }` entries plus an `isImageAlreadyAdded` check (:180-184). Extract one `ImageEntry` builder/dedupe helper used by both.

---

## 89. Move exam-signature encryption into HealthRepository
**Rating 66 · Provenance: devin-11#9**
`ui/health/HealthExaminationViewModel.kt:128` `encryptSign` uses `AndroidDecrypter.generateIv/generateKey` + `GsonUtils.gson.toJson(sign)` inline (:23-25,:115-116,:132) — cryptography lives in the VM. Move `encryptSign` into `HealthRepositoryImpl` (it already does `AndroidDecrypter.generateKey` at :50) so the VM only calls `saveExamination`.

---

## 90. Move bell-dashboard probing into the ViewModel
**Rating 66 · Provenance: devin-11#10**
`ui/dashboard/BellDashboardFragment.kt` holds `serverUrlMapper` (:60), `isServerReachable` (:109-110), and `handleConnectingState` (:124-129) — server probing in a Fragment. Shift the probe into `BellDashboardViewModel` (`checkServerConnection` already exists at :137-138) and emit a `NetworkStatus`-shaped state.

---

## 91. Achievement JSON assembly moves out of the fragment
**Rating 66 · Provenance: devin-11#8**
`ui/user/EditAchievementFragment.kt` builds `achievementArray: JsonArray` (:77,:118,:191,:268-284) with per-row `JsonObject`/`getAsJsonArray` traversal. Move save/load shaping into `AchievementViewModel`/`Achievement` (`createReference` at :51 already exists there) so the fragment only binds rows.

---

## 92. printStackTrace → Log.e in the exam-taking path
**Rating 64 · Provenance: devin-6#7** ⚠ `ExamTakingFragment` flagged PR-owned (#15559).
`ui/exam/ExamTakingFragment.kt:142,:177,:664` raw `printStackTrace` — invisible in release logs. Route through `Log.e(TAG, ..., e)` (or the app's logger) so crashes surface in logcat.

---

## 93. printStackTrace → Log.e in base fragments
**Rating 64 · Provenance: devin-6#8**
`base/BaseTeamFragment.kt:62` and `base/BaseContainerFragment.kt:173,:175`. Same fix.

---

## 94. printStackTrace → Log.e in the three utils
**Rating 64 · Provenance: devin-6#9**
`utils/FileUtils.kt:255`, `utils/KeyboardUtils.kt:16`, `utils/RetryUtils.kt:31`. Same fix — keeps the exception reachable when the util throws off-main.

---

## 95. UserInformationFragment: one TAG, fewer Log.d sites
**Rating 63 · Provenance: devin-6#10**
`ui/exam/UserInformationFragment.kt` has ~10 `Log.d` sites with literal tags. Consolidate under one companion `TAG` and drop redundant debug logs (or gate them behind `BuildConfig.DEBUG`).

---

## 96. Hoist the TabLayout measurement out of the global layout listener
**Rating 62 · Provenance: devin-6#6** ⚠ `DashboardActivity` flagged PR-owned (#15825).
`ui/dashboard/DashboardActivity.kt` `OnGlobalLayoutListener` (:278-279) → `topBarVisible()` (:800) + `removeOnGlobalLayoutListener` (:977) fires per layout pass. Resolve the listener after the first pass (or use `doOnPreDraw`/`ViewTreeObserver` once) instead of a persistent global listener.

---

## 97. RoomModule: mark DAO providers `@Singleton`
**Rating 60 · Provenance: devin-6#4** ⚠ `di/RoomModule` flagged off-limits in claude-10's list.
`di/RoomModule.kt` only `provideAppDatabase` is `@Singleton` (:55); ~37 DAO providers from :64 are unscoped — each injection site constructs a fresh `Dao_Impl` proxy. Annotate DAO providers `@Singleton` so all sites share one instance.

---

## 98. build.yml: exclude test artifacts from the build-dir cache
**Rating 55 · Provenance: devin-1#9** ⚠ `.github/workflows/build.yml` is listed as open-PR-owned (#15226) in three other lists — sequence after that PR or accept the conflict.
`build.yml` caches `app/build/**` without the `!app/build/test-results/**`/`!app/build/reports/**` exclusions that `test.yml` already has, and lacks test.yml's incremental-compile retry. Mirror the two exclusions + retry into build.yml.

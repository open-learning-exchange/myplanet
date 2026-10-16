# myPlanet merged refactor backlog

Merged from 12 agent task lists (5 test-workflow, 5 performance, 2 repository-boundary). Every premise was re-checked against `master` @ `897cdbd` (2026-10-15), not the plans' base `f18c50d`. Line numbers are as of `897cdbd`.

Rating = 100 × E^0.30 × I^0.45 × F^0.25, where E = evidence quality, I = impact and F = risk-adjusted feasibility, each scored 0–1. The product follows 'evidence × impact × feasibility', and impact carries the largest exponent. Sorted by rating; tasks sharing a file say so in their premise.

---

### P1 · RetryInterceptor: don't retry unrecoverable network errors

**Rating 79** (E 90 · I 70 · F 85) · proposed by: claude (list 10 #1, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`, `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`

**Verified on master:** `catch (e: IOException)` treats every IOException as retryable (:50-53) with `maxRetries = 3`, `initialDelay = 1000`, `factor = 2.0` (:18-20). `UnknownHostException`/TLS handshake failures therefore wait ~7 s before failing.

**Do:** Add `isPermanentFailure` (UnknownHost, SSLHandshake, SSLPeerUnverified) and break immediately: no broadcast, no backoff. Keep timeouts and ConnectException on the retry path.

**Acceptance:** New tests: `proceed` called exactly once and no broadcast. In airplane mode, sync fails in about 1 s.

---

### P2 · CoursesStepsViewModel: publish step content before the server-availability probe

**Rating 75** (E 90 · I 65 · F 80) · proposed by: claude (list 10 #3, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt`

**Verified on master:** `loadStep` awaits `configurationsRepository.checkServerAvailability()` (:84) before assigning `_uiState` (:106), so steps with undownloaded resources stay blank for a full probe.

**Do:** Assign the state first with `isDownloadingResources=false`, then probe in the existing background launch and `update { if same step → copy(isDownloadingResources=true) }`.

**Acceptance:** New test with a suspended probe sees `step` set and `isLoading=false`; the existing download-flag test still passes.

---

### T1 · Make the closed-port reachability test fail fast

**Rating 74** (E 95 · I 55 · F 95) · proposed by: copilot (list 02 #5, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperReachabilityTest.kt`

**Verified on master:** The test binds `HttpServer` on port 0, calls `stop(0)`, then probes the port; `ServerUrlMapper.isUrlDirectlyReachable` uses 5000 ms connect/read timeouts (`ServerUrlMapper.kt:122-123`). Reproduced locally on JDK: the just-stopped port takes **5027 ms** (`SocketTimeoutException`), `http://127.0.0.1:1` refuses in **2 ms** (`ConnectException`).

**Do:** Drop the dummy server in `isUrlDirectlyReachable returns false for closed port without throwing`; probe `http://127.0.0.1:1` and keep `assertFalse`. Do not touch the production timeouts or the four local-server tests.

**Acceptance:** All five cases pass; the closed-port case finishes well under 1 s.

---

### T2 · Move SecurePrefsTest, PdfThumbnailLoaderTest and one VersionUtilsTest method off the one-off SDK 27/28 sandboxes

**Rating 68** (E 85 · I 55 · F 75) · proposed by: claude (list 05 #1, 0.60), devin (list 01 #7, 0.40)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt (method pin at :179 only)`

**Verified on master:** On master, API 27 is pinned only by `SecurePrefsTest.kt:20` and API 28 only by `PdfThumbnailLoaderTest.kt:21` and `VersionUtilsTest.kt:179`. `SecurePrefs` branches only on `>= N`, `PdfThumbnailLoader` has no SDK check, and `VersionUtils.kt:27` branches on `>= P`, which the default SDK 36 also satisfies. `app/build.gradle` stages 26/27/28/33/36 jars. The explicit `sdk = [36]` pins in `TeamResourcesAdapterTest` and `ResourcesCardBinderTest` match the default, so removing them (list 01) is cosmetic: the speedup comes only from retiring 27 and 28.

**Do:** Delete the three pins. If a class then fails, fall back to SDK 26 (`O`), never 27/28. Keep `VersionUtilsTest.kt:161` (pre-P) and `NotificationUtilsTest` pins. Optional: add `sdk=36` to `robolectric.properties` so the default is explicit. Leave `robolectricSdkJars` alone until this lands.

**Acceptance:** `grep -rn 'sdk = \[\(27\|28\|Build.VERSION_CODES.O_MR1\|Build.VERSION_CODES.P\)\]' app/src/test` is empty; suite green; offline-Robolectric CI check still green.

---

### P3 · Chat history: stop full reloads on every onStart; parallelise the two reads

**Rating 65** (E 85 · I 50 · F 75) · proposed by: claude (list 10 #4, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`

**Verified on master:** `_refreshChatSignal = MutableSharedFlow(replay = 1)` (:70) is collected through `collectWhenStarted` (`ChatHistoryFragment.kt:206`), which uses `repeatOnLifecycle(STARTED)`, so the replayed Unit reruns `loadChatHistoryScreenData` on every foreground. Planet news and chat history load sequentially (:139-140). Also worth knowing: nothing emits `"chats"` on the realtime bus (only `"courses"` is ever emitted), so the init seed is the only real signal.

**Do:** Collect the signal once per view (no repeatOnLifecycle), `async` the two independent reads, and move `extractSharedViewInIds` to `dispatcherProvider.default`. Keep `replay = 1` (a test depends on it).

**Acceptance:** Home → return shows no reload; sharing still refreshes.

---

### P5 · Narrow `TeamDao.observeAll` to membership and root-team rows

**Rating 65** (E 85 · I 50 · F 75) · proposed by: claude (list 10 #7, 0.60), devin (list 11 #1, 0.40)

**Files:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt`, (stretch) app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt

**Verified on master:** `SELECT * FROM teams` (:19) feeds `getMyTeamsFlow` (:210) and `getMyTeamDetailsFlow` (:298), which keep only membership rows and `isRootTeam()` rows (:1400). Every report, transaction, link and request row is decoded and then discarded on each `teams` write.

**Do:** List 10 (DAO-only): `WHERE docType='membership' OR teamId IS NULL OR TRIM(teamId, ' '||char(9,10,13))=''`, same signature, and a Room test. List 11's stretch goal is user-scoped queries in the repository plus batched `markMembershipsForLeave` (:1367); the batching saves little because the loop covers one user's rows in one team.

**Acceptance:** Dashboard teams, teams list and calendar are unchanged; the new DAO test passes.

---

### P8 · VoicesAdapterHelper: reuse TextRevealer's chunked reveal for typing animation

**Rating 65** (E 90 · I 45 · F 85) · proposed by: claude (list 09 #1, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/TextRevealerTest.kt`

**Verified on master:** `createOnAnimateTyping` emits one substring per character with `delay(10)` (:19-26). `TextRevealer.reveal` already chunks up to 8 characters per tick and is used by chat (`ChatDetailFragment.kt:272`). Three screens use the helper.

**Do:** Delegate to `TextRevealer.reveal`; complete once and never after cancellation; keep the signature.

**Acceptance:** A 3000-character reply gives 375 updates; cancellation stops callbacks.

---

### P7 · ImageUtils: clear the pending Glide request before showing an empty-source placeholder

**Rating 64** (E 85 · I 45 · F 85) · proposed by: claude (list 09 #3, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt`

**Verified on master:** `loadProfileImage` (:10-12) and `loadImage` (:38-39) call `setImageResource` for null/empty sources without `Glide.with(view).clear(view)`, so a recycled row's old request can overwrite the placeholder.

**Do:** Clear the request first in both empty branches; leave everything else unchanged.

**Acceptance:** A test confirms the outstanding request is cleared; no stale portraits appear while scrolling.

---

### R1 · MyLifeDao.updateVisibility: resolve to exactly one row

**Rating 64** (E 85 · I 50 · F 70) · proposed by: claude (list 12 #1, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt`

**Verified on master:** `UPDATE my_life … WHERE _id = :id OR imageId = :id OR title = :id` (:33) has no owner constraint. `LifeFragment` falls back to `imageId`/`title` when `_id` is blank (:52-54), so a shared default tile label can flip every user's row.

**Do:** Wrap it in a transaction: exact `_id` first; otherwise update only a *unique* imageId/title match; ambiguity is a no-op. Keep the signature.

**Acceptance:** Collision/alias/unknown-ID DAO tests pass.

---

### T3 · Stop re-mocking `UrlUtils` on every test in UrlUtilsTest

**Rating 64** (E 80 · I 50 · F 75) · proposed by: copilot (list 02 #2, 0.55), claude (list 05 #2, 0.45)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt`

**Verified on master:** Robolectric class; `setUp` calls `mockkObject(UrlUtils)` (:34), `tearDown` calls `unmockkAll()` (:43), and nine tests call `unmockkObject(UrlUtils)` (:392…:495) just to reach the real object. The per-test timings (0.41 s × 44) are the agents' CI numbers and weren't re-measured here.

**Do:** Preferred (list 02): drop the class-wide `mockkObject`, mock `UrlUtils` only inside the two tests that stub `getUrl()` (try/finally), delete the now-redundant `unmockkObject` calls, and unmock the one in-test `Uri` static locally. Fallback (list 05): mock once in `@BeforeClass`, `clearAllMocks()` per test, `clearMocks(UrlUtils)` in place of `unmockkObject`.

**Acceptance:** All 44 tests pass; record the before and after `<testsuite time>` in the PR.

---

### P4 · Health patient picker: one initial load, no sort race, no RecyclerView rebuild

**Rating 63** (E 85 · I 45 · F 80) · proposed by: claude (list 10 #6, 0.65), claude (list 09 #5, 0.35)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt`

**Verified on master:** `loadPatients()` (`MyHealthFragment.kt:272`, default `descending=true`) runs untracked (`HealthViewModel.kt:95-99`). The spinner's first `onItemSelected` then calls `searchPatients(…, "joinDate", false)` (:304), so two loads race in opposite orders. Lines 196-198 reassign the layoutManager and adapter on every emission. `loadPatients` has no other production caller.

**Do:** List 10 approach: delete the `loadPatients()` call and method (rewrite its one test onto `searchPatients`), and only assign the layoutManager/adapter when `rvRecords.adapter !== healthAdapter`. List 09's alternative is to route `loadPatients` through `searchJob`.

**Acceptance:** The list appears once in the spinner's order; sort and search still work; the records strip keeps scrolling.

---

### T4 · Hoist DownloadServiceTest's three static mocks to class level

**Rating 61** (E 80 · I 50 · F 65) · proposed by: claude (list 05 #3, 0.55), copilot (list 02 #3, 0.45)

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`

**Verified on master:** Robolectric; `setUp` re-runs `mockkStatic(ContextCompat)`, `mockkObject(DownloadUtils)` and `mockkStatic(Log)` (:63-65), and `tearDown` calls `unmockkAll()` (:75). Two tests add `mockkStatic(WorkManagerImpl)` (:127, :157).

**Do:** Mock the three once per class (`@BeforeClass`/`@AfterClass`), keep the `Log` stubs and the SDK_INT restore per test, replace `unmockkAll()` with `clearAllMocks()`, and give every in-test static its own `unmockkStatic` in `finally`.

**Acceptance:** All 11 tests pass; the class time drops (record before and after).

---

### P6 · TakeCourseViewModel: log a course visit once per open, not per onStart

**Rating 60** (E 80 · I 45 · F 70) · proposed by: claude (list 10 #5, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`

**Verified on master:** `collectLatestWhenStarted(uiState)` (`TakeCourseFragment.kt:74`) → `bindCourse` → `setCourseData()` → `logCourseVisit` (:241) inserts a `visit` row each time (`ActivitiesRepositoryImpl.kt:97-110`).

**Do:** Add `loggedVisitCourseId`, return early on a repeat, and set it only after a successful insert. Confirm with Planet that a 'visit' means an open, not a resume.

**Acceptance:** Three resumes add one row; reopening adds one more.

---

### P9 · BaseExamFragment: run Markwon editor highlighting off the main thread

**Rating 60** (E 80 · I 45 · F 70) · proposed by: claude (list 10 #10, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/base/BaseExamFragmentTest.kt`

**Verified on master:** `MarkwonEditorTextWatcher.withProcess(markwonEditor)` (:191) parses on every keystroke on the main thread. `markwon-editor` 4.6.2 is already a dependency and offers `withPreRender`.

**Do:** Add a lazy single-thread executor, switch to `withPreRender(editor, executor, etAnswer)`, and `shutdownNow()` in `onDestroy` if the executor was initialised.

**Acceptance:** Typing 500+ characters with markdown stays smooth; no leaked threads.

---

### R2 · TeamDao: chunk getByIds/deleteByIds inside the DAO

**Rating 60** (E 85 · I 40 · F 80) · proposed by: claude (list 12 #3, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt`

**Verified on master:** Both are single unbounded `IN (:ids)` queries (:13, :35). Readers chunk at their call sites, but `deleteLocalTeamRecords` passes every ID straight through (`TeamsRepositoryImpl.kt:130-136`). minSdk 26 devices have SQLite's 999-variable limit.

**Do:** Add internal queries plus default wrappers: distinct, chunked(900), with the delete under `@Transaction`, matching the other DAOs.

**Acceptance:** A Room test with 1201+ IDs gets exact counts and keeps an unrelated row.

---

### T11 · Delete CourseRatingUtilsTest's two widget smoke tests

**Rating 59** (E 80 · I 40 · F 85) · proposed by: copilot (list 02 #1, 0.60), claude (list 05 #6, 0.40)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/CourseRatingUtilsTest.kt`

**Verified on master:** `showRating_jsonObjectOverload_smokeTest` (:116) and `showRating_ratingSummaryOverload_smokeTest` (:136) inline-mock `AppCompatRatingBar`/`TextView` with no runner. The 8 tests above them cover `computeRatingDisplay`/`parseRating`, and `BaseContainerFragmentTest` covers the `showRating` wiring. The 14.1 s figure is the agents' CI measurement; some of it may be first-load of View classes that moves to another test in the fork.

**Do:** Delete both methods and the imports that become unused. Do not add a Robolectric runner.

**Acceptance:** The remaining 8 tests pass unedited; check whether `CourseRatingUtilsTest` drops out of the slowest-classes table.

---

### T33 · docs/TESTING.md: correct the `Log` mocking advice and document mock-once-per-class

**Rating 59** (E 90 · I 35 · F 90) · proposed by: claude (list 05 #10, 1.00)

**Files:** `docs/TESTING.md`

**Verified on master:** `TESTING.md:341` (and again :468, which list 05 missed) says tests crash without `mockkStatic(Log)`, but `app/build.gradle:22` sets `unitTests.returnDefaultValues = true`. The worker example (:320-339) teaches the per-test churn pattern.

**Do:** Verify on one class (temporarily remove its Log mock and run it), then fix both paragraphs, rewrite the example to class-level mocks plus `clearAllMocks()`, and add a 'mock once per class' subsection.

**Acceptance:** Every citation resolves at the merge commit (doc-only, so only the labels check runs).

---

### T5 · Hoist UploadConfigsTest's `VersionUtils`/`NetworkUtils` object mocks

**Rating 59** (E 80 · I 40 · F 80) · proposed by: copilot (list 02 #4, 0.50), claude (list 05 #4, 0.50)

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt`

**Verified on master:** Plain JVM test; `mockkObject(VersionUtils)` (:46) and `mockkObject(NetworkUtils)` (:49) run per test, and `unmockkAll()` runs at :75.

**Do:** Mock both once per class, keep the stubs and the `UploadConfigs` construction in `@Before`, and use `clearAllMocks()` in `@After`. Re-check open-PR ownership first: the KMP PRs touch `services/upload/*Test`.

**Acceptance:** All 18 tests pass; record timings.

---

### P10 · ServerReachabilityProvider: probe primary and alternative URLs concurrently

**Rating 57** (E 85 · I 45 · F 55) · proposed by: claude (list 10 #2, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ServerReachabilityProviderTest.kt`

**Verified on master:** `isServerReachable` loops `urlsToTry` sequentially (:37-42). It gates SyncActivity, chat, the bell dashboard and others (10+ call sites).

**Do:** With two URLs, race them and cancel the loser's blocking `Call` via `invokeOnCompletion`; keep the cache and the HEAD→GET fallback. Medium risk: the cancellation plumbing around blocking `execute()` is subtle.

**Acceptance:** New test: alternative succeeds while the primary blocks, and the primary's `cancel()` is invoked.

---

### P11 · KeyboardUtils.setupUI: one shared listener, and hide only on ACTION_DOWN

**Rating 56** (E 85 · I 35 · F 80) · proposed by: claude (list 10 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/KeyboardUtilsTest.kt`

**Verified on master:** `setupUI` creates a new `OnTouchListener` per view that calls `hideSoftKeyboard` on every MotionEvent, including every MOVE (:20-37). There are 7 call sites.

**Do:** Create one listener per `setupUI` call, gate it on `actionMasked == ACTION_DOWN`, and move the recursion into a private helper.

**Acceptance:** MOVE doesn't hide; all children share one listener instance.

---

### T6 · FeedbackTest/MeetupTest: drop the dead `TextUtils` mock and hoist `NetworkUtils`

**Rating 55** (E 80 · I 35 · F 80) · proposed by: claude (list 05 #5, 0.60), copilot (list 02 #6, 0.40)

**Files:** `app/src/test/java/org/ole/planet/myplanet/model/FeedbackTest.kt`, `app/src/test/java/org/ole/planet/myplanet/model/MeetupTest.kt`

**Verified on master:** FeedbackTest runs `mockkStatic(TextUtils)` (:26) and `mockkObject(NetworkUtils)` (:31) per test, plus `unmockkAll()`. `model/Feedback.kt` never references `TextUtils`. MeetupTest repeats the `NetworkUtils` pattern (:28/:34). The mock is needed because `addDocumentOrigin` defaults to `NetworkUtils.getUniqueIdentifier()` (`DocumentOrigin.kt:7`). FeedbackTest has no Robolectric runner (list 05 says it does).

**Do:** Delete the TextUtils block. If something then fails, restore it and hoist it instead. Mock `NetworkUtils` once per class and use `clearAllMocks()` per test. Keep the in-test `JsonParser` statics.

**Acceptance:** 15 + 11 tests pass; record timings.

---

### P15 · ResourcesListFilter: prepare selected facets/mediums once and short-circuit

**Rating 53** (E 85 · I 30 · F 85) · proposed by: claude (list 09 #4, 0.60), copilot (list 07 #3, 0.40)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt`

**Verified on master:** `matchesFacets` evaluates subject, level, language and medium eagerly before combining them (:71-84). `matchesMedium` canonicalises each *selected* medium per library row (:90-91) and calls `classify`/`isExplicitNonBook` even when no target type needs it. List 07's 're-lowercases' wording is loose because `equals(ignoreCase)` doesn't allocate.

**Do:** Precompute the canonical selected mediums and target types per `matching()` call, return early in facet order, and resolve the library's canonical medium at most once.

**Acceptance:** `apply` and `countMatching` agree; order preserved; same results across mixed-case and alias cases.

---

### T14 · MyLibraryDaoTest: sparse boundary fixtures instead of 1200-row inserts

**Rating 53** (E 75 · I 35 · F 75) · proposed by: codex (list 04 #3, 0.60), devin (list 01 #3, 0.40)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt`

**Verified on master:** Dense 1200-row fixtures appear at :39, :59, :81, :103, :206 and :383; the DAO wrappers chunk at **900** (`MyLibraryDao.kt:40…213`). List 01 has two wrong claims: the tests at :405/:419 already seed only 2 rows, and the boundary is 900, not 999. List 04 got both right.

**Do:** Keep 1200-key requests generated independently of the fixtures. Seed only indices 1/900/901/1200, assert exact IDs and sizes, and drop the redundant dense course and offline-status cases (list 04). Keep a fresh DB per test.

**Acceptance:** All chunk-boundary assertions hold; record the class time before and after.

---

### P12 · InlineResourceAdapter: coalesce payloads into a single preview refresh

**Rating 52** (E 85 · I 30 · F 80) · proposed by: claude (list 09 #2, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`

**Verified on master:** The payload bind iterates payloads (:122), and both `PAYLOAD_ADDRESS` (:127-129) and `PAYLOAD_STATUS` (:131) call `updateStatusAndPreview`. The diff emits both when a download completes (:48-49).

**Do:** Flatten the payloads into a flag set; refresh the title once and the status/preview once.

**Acceptance:** A combined address+status payload gives exactly one preview refresh.

---

### P13 · Opened-resources flow: one row per resource; skip the discarded cached sort

**Rating 52** (E 85 · I 30 · F 80) · proposed by: claude (list 10 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDaoTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`

**Verified on master:** `observeByUserAndType` returns every open event (:28-29), and the only caller reduces it to `mapNotNull{resourceId}.toSet()` (`ResourcesRepositoryImpl.kt:551-555`). `loadResources` runs `getCachedResources` (a sync sort) even when `_resourcesState` is non-empty and the result is thrown away (:98-101).

**Do:** Add `AND resourceId IS NOT NULL GROUP BY resourceId` and guard the cached sort behind the isEmpty check.

**Acceptance:** The new DAO test gets 2 rows for {A×3, B, null, other-user}; the opened indicator is unchanged.

---

### P14 · WebViewActivity night mode: replace the whole-DOM `getComputedStyle` walk

**Rating 52** (E 85 · I 35 · F 60) · proposed by: copilot (list 07 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/viewer/WebViewActivityTest.kt`

**Verified on master:** The injected JS runs `querySelectorAll('*')` + `getComputedStyle` per element for both modes (:252-271).

**Do:** Inject one stylesheet or a root class instead. Caveat: today's script recolours only elements whose computed colour is pure black/white, so a stylesheet is not exactly equivalent and needs a visual check on real resources.

**Acceptance:** The injected text has no `querySelectorAll`/`getComputedStyle`; dark and light modes still read correctly.

---

### T15 · ExamDaoTest: sparse boundary fixtures

**Rating 51** (E 80 · I 30 · F 75) · proposed by: codex (list 04 #2, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt`

**Verified on master:** 1200-exam fixtures at :37, :195, :211, :227 and the team-owned test at :242. `ExamDao` chunks at 900 (:15-50).

**Do:** Seed 1/900/901/1200 against full 1200-ID requests; drop the redundant `getByCourseIds_handles1200Courses`; keep the team-owned dedup assertion.

**Acceptance:** Both chunks are still covered; team-owned surveys appear exactly once.

---

### T20 · TeamLogDaoTest: sparse fixtures with explicit cutoff controls

**Rating 51** (E 80 · I 30 · F 75) · proposed by: codex (list 04 #8, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamLogDaoTest.kt`

**Verified on master:** The recent-visits test inserts 1200 + 100 rows in loops (`for (i in 1..1200)`, `1201..1300`), and the user test another 1200; chunked at 900.

**Do:** Seed boundary rows, replace the 100 old visits with one at the cutoff and one below it, and add wrong-type/unrequested controls.

**Acceptance:** Cutoff, type, team and user filters hold across both chunks.

---

### T7 · Hoist DownloadUtilsTest's three object mocks

**Rating 49** (E 80 · I 30 · F 65) · proposed by: claude (list 05 #7, 0.60), copilot (list 02 #7, 0.40)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt`

**Verified on master:** `mockkObject(Utilities)` (:52), `mockkObject(DownloadService.Companion)` (:55) and `mockkObject(DownloadUtils, recordPrivateCalls=true)` (:57) run per test, plus `unmockkAll()` (:64). Two tests call `unmockkObject(DownloadUtils)` (:208, :228); list 02 missed these.

**Do:** Mock once per class and keep `resetChannelsCreatedForTesting()` per test. Replace the two in-test `unmockkObject` calls with `clearMocks(DownloadUtils)`.

**Acceptance:** All 15 tests pass.

---

### T10 · Delete the assertion-free `StepExamBenchmarkTest`

**Rating 48** (E 95 · I 20 · F 100) · proposed by: codex (list 04 #1, 0.30), devin (list 01 #1, 0.30), copilot (list 03 #3, 0.20), claude (list 05 #6, 0.20)

**Files:** `app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt`

**Verified on master:** Zero assertions. It builds 500 docs, runs `insertCourseStepsExams` 10× (:33, :55) and prints `Benchmark result` (:62); it also does a per-test `mockkStatic(TextUtils)`. `StepExamTest` keeps 8 real tests, and nothing references the benchmark.

**Do:** Delete the file. Leave `StepExamTest` and the asserting `*BenchmarkTest` repository classes alone.

**Acceptance:** Suite green with exactly one fewer test.

---

### T16 · TeamTaskDaoTest: sparse boundary fixtures

**Rating 47** (E 80 · I 25 · F 75) · proposed by: codex (list 04 #4, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDaoTest.kt`

**Verified on master:** 1200-task fixtures at :37, :54, :71 (line numbers unchanged on master despite the +15-line commit). `TeamTaskDao` chunks at 900.

**Do:** Same recipe, plus an unrequested false-flag control row for `markTasksNotified`.

**Acceptance:** Lookups cover both chunks; notification updates touch only requested tasks.

---

### T17 · AnswerDaoTest: sparse boundary fixtures

**Rating 47** (E 80 · I 25 · F 75) · proposed by: codex (list 04 #5, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt`

**Verified on master:** Fixtures of 1200/1200/1000 at :45, :59, :91; `AnswerDao` chunks at 900 (:18, :29).

**Do:** Seed 1/900/901/(1000|1200), assert deletion counts of 4, and keep an unrequested survivor.

**Acceptance:** Queries span both chunks; deletion returns the rows actually removed.

---

### T18 · RemovedLogDaoTest: sparse boundary fixtures

**Rating 47** (E 80 · I 25 · F 75) · proposed by: codex (list 04 #6, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt`

**Verified on master:** 1200/1000/1200 doc fixtures at :86, :110, :127; chunked at 900 (`RemovedLogDao.kt:21`).

**Do:** Seed only the boundary targets; keep the wrong-user/wrong-type controls and add an unrequested same-partition survivor.

**Acceptance:** Cleanup handles both chunks without collateral deletes.

---

### T19 · CourseProgressDaoTest: sparse fixtures at the real 250/899 limits

**Rating 47** (E 85 · I 25 · F 75) · proposed by: codex (list 04 #7, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt`

**Verified on master:** Fixtures of 300/1200/1200 at :92, :108, :121. The limits really are 250 (tuples, `CourseProgressDao.kt:40`) and 899 (:18), not 900.

**Do:** Seed 1/250/251/300 and 1/899/900/1200; add wrong-user controls.

**Acceptance:** Exact tuples and null/non-null scoping still hold.

---

### R3 · Centralise search-activity writes in ActivitiesRepository; drop dead `offlineActivityDao`

**Rating 46** (E 85 · I 25 · F 65) · proposed by: devin (list 11 #3, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`

**Verified on master:** `SearchActivityDao` is injected into Courses (:67, insert :341) and Resources (:59, insert :477) as well as Activities (:60). `UserRepositoryImpl.offlineActivityDao` (:83) is never used, and `removedLogDao` is used only at :1259-1260.

**Do:** Add `recordSearchActivity`/`getRemovedDocIds` to ActivitiesRepository, delegate to them, and remove the dead injections. Five files: re-check open-PR ownership (the KMP PRs touch the `*Impl` files).

**Acceptance:** Searches still write rows; shelf still excludes removed docs.

---

### T12 · Delete ViewExtensionsTest's two `mockk<EditText>` tests

**Rating 46** (E 70 · I 30 · F 60) · proposed by: copilot (list 02 #10, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/ViewExtensionsTest.kt`

**Verified on master:** `textChanges_emitsInitialValue` (:26) and `textChanges_emitsOnTextChanged` (:37) mock `EditText` on a plain JVM. Caveat: `textChanges` is production code used by `TeamFragment`, `SubmissionsFragment` and `VoicesFragment`, so this removes its only coverage. Whether the 5.9 s is per-class or per-fork class loading wasn't verified.

**Do:** Delete both tests and their imports; keep the three `debounceDistinct` tests. Note the coverage loss in the PR.

**Acceptance:** The three remaining tests pass.

---

### T23 · Share one in-memory AppDatabase per round-trip test class

**Rating 46** (E 80 · I 25 · F 70) · proposed by: devin (list 01 #2, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/AppDatabaseRoundTripTest.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/CollapsedEntitiesRoundTripTest.kt`

**Verified on master:** Both build the full 38-entity DB in `@Before` (6 + 8 tests = 14 schema builds).

**Do:** Build once per class and call `clearAllTables()` per test. This conflicts with list 04's 'fresh DB per test' house rule, so state the trade-off in the PR.

**Acceptance:** Both classes pass and no test sees a sibling's rows.

---

### T8 · Hoist BasePermissionActivityTest's `ContextCompat`/`Process` static mocks

**Rating 46** (E 75 · I 25 · F 75) · proposed by: copilot (list 02 #8, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/base/BasePermissionActivityTest.kt`

**Verified on master:** `setup` runs `mockkStatic(ContextCompat)` and `mockkStatic(Process)` (:31-32) and `teardown` unmocks both (:38-39). The in-test `NotificationManagerCompat`/`ActivityCompat` statics are self-contained.

**Do:** Move the two statics and the `myUid` stub to class level; keep the relaxed activity mock in `@Before`. Don't claim the 12.5 s first-test excess.

**Acceptance:** All 12 tests pass.

---

### P17 · ResourcesPreviewLoader: stop CSV parsing at the fifth record

**Rating 44** (E 85 · I 20 · F 85) · proposed by: claude (list 09 #6, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt`

**Verified on master:** `for (row in reader) { if (count >= 5) break … }` (:66-70) pulls a sixth record before breaking, so a malformed sixth record throws and nulls the whole preview.

**Do:** Read records explicitly and check the limit before each `readNext()`.

**Acceptance:** Five valid rows followed by an unterminated quote still preview.

---

### P18 · FreeSpaceWorker: throttle `setProgress` during the delete walk

**Rating 44** (E 80 · I 20 · F 85) · proposed by: copilot (list 07 #7, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt`, `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt`

**Verified on master:** `setProgress` runs every 10 deleted files (:76-77). Each call is a WorkManager progress write.

**Do:** Throttle by time or a larger count and always send a final update.

**Acceptance:** Far fewer updates; the final update still fires; same files deleted.

---

### P19 · ChatHistoryAdapter: stop writing `chatTitle` from bind (wrong contentDescription)

**Rating 44** (E 85 · I 20 · F 85) · proposed by: copilot (list 08 #3, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt`

**Verified on master:** `onBindViewHolder` writes the adapter field `chatTitle` (:137, :141), and the card's `contentDescription` reads it (:101), so it gets the last-bound row's title. This is an accessibility bug, not a performance one.

**Do:** Delete the field and derive the description from the bound item. Ignore the self-cancelled part (b) of the original order.

**Acceptance:** Each card announces its own title.

---

### R5 · Deduplicate primary→alternative probing in ServerReachabilityWorker and SubmissionsUploader

**Rating 44** (E 80 · I 25 · F 60) · proposed by: devin (list 11 #7, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/services/ServerReachabilityWorker.kt`, `app/src/main/java/org/ole/planet/myplanet/services/SubmissionsUploader.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`

**Verified on master:** Both run processUrl → 15 s primary probe → alternative probe → `updateUrlPreferences` (Worker :170-189, Uploader :26-72), and they have drifted on total failure. `ServerUrlMapper` is touched by open PRs per lists 02, 08 and 10.

**Do:** Extract `probeWithFallback` and preserve each caller's failure behaviour.

**Acceptance:** Both paths fall back and rewrite prefs identically.

---

### T9 · Hoist NotificationActionReceiverTest's `NotificationUtils`/entry-point statics

**Rating 44** (E 75 · I 25 · F 65) · proposed by: claude (list 05 #9, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt`

**Verified on master:** Robolectric; `mockkObject(NotificationUtils)` (:65) and `mockkStatic("…ServiceDependenciesEntryPointKt")` (:68) run per test, with `unmockkAll()` at :87. An in-test `mockkStatic(Log)` sits at :174.

**Do:** Mock once per class and keep the stubs per test. Give the in-test `Log` static its own `unmockkStatic` in `finally`.

**Acceptance:** All 7 tests pass.

---

### R4 · Bell dashboard: move primary/alternative probing into the ViewModel

**Rating 43** (E 85 · I 20 · F 75) · proposed by: devin (list 11 #10, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt`

**Verified on master:** The fragment reads `prefData`, runs `serverUrlMapper.processUrl` and probes primary then alternative (:109-136). It double-probes, because `checkServerConnection` → `ServerReachabilityProvider.isServerReachable` already tries the alternative URL.

**Do:** Add one mapping-aware VM call and drop the fragment's `serverUrlMapper`. Simplest version: call `checkServerConnection(serverUrl)` once, since the provider already falls back.

**Acceptance:** The indicator is green when reachable and yellow otherwise.

---

### T13 · DashboardElementActivityNavigationTest: build one activity per class instead of four

**Rating 41** (E 70 · I 25 · F 55) · proposed by: copilot (list 02 #9, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt`

**Verified on master:** Each of the four tests calls `Robolectric.buildActivity(TestDashboardElementActivity…).setup()` (:55, :68, :87, :113).

**Do:** Share one activity, and pop the back stack plus `executePendingTransactions()` between cases. Keep all four assertions.

**Acceptance:** All four navigation assertions hold.

---

### T24 · Hoist repeated `mockkStatic` in SyncTimeLoggerTest, UtilitiesTest, WebViewSafetyTest

**Rating 41** (E 75 · I 20 · F 70) · proposed by: devin (list 01 #4, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/WebViewSafetyTest.kt`

**Verified on master:** SyncTimeLoggerTest calls `mockkStatic(Log)` in 7 test bodies (:20…:342). UtilitiesTest mocks `Looper`/`Toast` (:28-29) and WebViewSafetyTest mocks `Uri` (:20) in `@Before`, each with `unmockkAll()`.

**Do:** Mock once per class and keep stubs per test. Per list 05 / T33, `Log` may not need mocking at all (`returnDefaultValues = true`).

**Acceptance:** Assertions unchanged; no static mock leaks across classes.

---

### T28 · test_timing_summary.py: add an enforceable fail-over exit code

**Rating 41** (E 65 · I 20 · F 85) · proposed by: devin (list 01 #8, 0.55), copilot (list 03 #10, 0.45)

**Files:** `.github/scripts/test_timing_summary.py`

**Verified on master:** Only `--warn-over`/`--warn-class-over` exist (:64-67), and neither changes the exit code. List 03 says green runs show no timing data, which is **false**: the summary step runs `if: always()` and tees to `$GITHUB_STEP_SUMMARY` (`test.yml:93-104`). Nothing calls the new flag until `test.yml` adopts it.

**Do:** Add `--fail-over` (summed) and/or `--fail-class-over` (per class) returning exit 1. Optionally add list 01's per-package rollup table. Stdlib only.

**Acceptance:** Exit 1 over budget and 0 under; the existing invocation's output is unchanged.

---

### P16 · Delete the unreachable diff-refresh path from RealtimeSyncMixin

**Rating 40** (E 95 · I 15 · F 85) · proposed by: devin (list 06 #5, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt`, `app/src/main/java/org/ole/planet/myplanet/callback/OnDiffRefreshListener.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt`

**Verified on master:** No class implements `OnDiffRefreshListener`, and the only two mixins (`ResourcesFragment.kt:800`, `CoursesFragment.kt:566`) return `shouldAutoRefresh=false`.

**Do:** Delete the interface, `getSyncRecyclerView`, `shouldAutoRefresh`, `refreshRecyclerView` and their overrides. Check open-PR ownership of `ResourcesFragment.kt` (list 08 flags #13355).

**Acceptance:** Builds; the lists still refresh via `onDataUpdated`.

---

### T22 · VersionUtilsTest: delete three tests subsumed by the cache tests

**Rating 40** (E 85 · I 15 · F 90) · proposed by: codex (list 04 #10, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt`

**Verified on master:** `getVersionCode_should_return_0_on_NameNotFoundException` (:147) is a strict subset of `…does_not_cache_NameNotFoundException` (:62); likewise :196 vs :120 and :210 vs :90.

**Do:** Delete the three methods only; keep the SDK-pinned and nullable tests. This shares a file with T2 but touches different hunks.

**Acceptance:** Suite green with 3 fewer Robolectric tests.

---

### P20 · DashboardSurveysAdapter: guard NO_POSITION; set text colour once

**Rating 37** (E 75 · I 15 · F 80) · proposed by: copilot (list 08 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesAdapter.kt`

**Verified on master:** The click listener passes the raw `bindingAdapterPosition` (:29-30), which can be -1 during removal animations, and `setTextColor` runs per bind (:44). `ReferencesAdapter` routes by `pos == 0`, which is safe today because the list is static (low value).

**Do:** Add the NO_POSITION guard and move the colour into the holder's `init`. Skip the ReferencesAdapter change unless the list becomes dynamic.

**Acceptance:** No click during animations reaches `onItemClick(-1)`.

---

### R6 · Move examination-signature encryption from HealthExaminationViewModel into HealthRepository

**Rating 36** (E 80 · I 15 · F 65) · proposed by: devin (list 11 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`

**Verified on master:** The VM's private `encryptSign` (:128-141) mutates the user's key/IV and the examination data before `healthRepository.saveExamination` (:116). Caveat: the repository then imports `AndroidDecrypter`, which pushes an Android-flavoured utility into the data layer (against roadmap 9).

**Do:** Extend `saveExamination(…, sign)` and move the body; keep the catch-and-log behaviour.

**Acceptance:** Saved exams still decrypt.

---

### R8 · SubmissionsRepositoryExporter: pair each submission with its own exam's questions

**Rating 36** (E 75 · I 15 · F 75) · proposed by: claude (list 12 #2, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt`

**Verified on master:** Questions are loaded from the first submission's parentId only (:184-185). **Latent**: the only caller (`SubmissionListFragment.generateReport`) lists submissions for a single `parentId`, so mixed-exam exports aren't reachable today.

**Do:** Group questions by examId with one batch query.

**Acceptance:** A helper test covers mixed exams, a repeated exam, and absent parents.

---

### T29 · test_timing_summary.py: flag likely per-test static-mock churn

**Rating 36** (E 70 · I 15 · F 75) · proposed by: claude (list 05 #8, 1.00)

**Files:** `.github/scripts/test_timing_summary.py`

**Verified on master:** The script ranks slow classes but never says why. 28 test files `mockkStatic(Log::class)`, and the `@Before`-mock + `@After unmockkAll()` pattern is common.

**Do:** Add a source-sniffing helper and a 'likely per-test static-mock churn' section for classes with ≥5 tests and median ≥0.30 s. This is the same file as T28, so land them sequentially.

**Acceptance:** The section lists `UrlUtilsTest` before T3 lands and drops it after.

---

### R7 · Route survey/progress submission and exam reads through SubmissionsRepository

**Rating 35** (E 80 · I 15 · F 60) · proposed by: devin (list 11 #2, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`

**Verified on master:** SurveysRepositoryImpl reads `submissionDao` at :207-209, :279, :301-303, :353, :369, and ProgressRepositoryImpl uses `examDao`/`questionDao` at :65 and :186. Note: CLAUDE.md's stated convention is that each Impl injects the DAOs it needs, so this is a design choice rather than a defect. Six new interface methods.

**Do:** Add passthroughs and swap the reads; use `dagger.Lazy` to avoid DI cycles (Submissions already depends on Teams).

**Acceptance:** Survey list, badges and `fetchCourseData` unchanged.

---

### T21 · NewsDaoTest: sparse fixture for the underscore-ID lookup

**Rating 35** (E 80 · I 12 · F 85) · proposed by: codex (list 04 #9, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NewsDaoTest.kt`

**Verified on master:** 1200 entities at :327 for one test; `getByUnderscoreIds` chunks at 900 (`NewsDao.kt:30`).

**Do:** Seed 1/900/901/1200 and request 1200 underscore IDs with duplicates.

**Acceptance:** Lookup spans both chunks without duplicates.

---

### P21 · InlineResourceAdapter: cache per-path existence checks

**Rating 34** (E 75 · I 15 · F 55) · proposed by: copilot (list 07 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`

**Verified on master:** Bind runs `FileUtils.checkFileExist` (:171) and `resourceFile.exists()` (:192), both on IO. Stale-cache risk after download/delete is the main hazard. This touches the same file as P12, so land them sequentially.

**Do:** Cache by absolute path for the adapter lifetime and evict on `onCurrentListChanged`.

**Acceptance:** Rebinding an unchanged row doesn't stat; a returning row with a missing file is not served stale.

---

### P22 · DashboardActivity: stop running `findViewById` on every global layout

**Rating 34** (E 85 · I 12 · F 70) · proposed by: devin (list 06 #6, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt`

**Verified on master:** `OnGlobalLayoutListener { topBarVisible() }` (:278-279), where `topBarVisible` calls `findViewById<TabLayout>` each pass (:800-808). `DashboardActivity` is in open PR #15825 per list 08.

**Do:** Cache the TabLayout and apply on create and on configuration change, or keep the listener but early-return.

**Acceptance:** The tab bar still hides in portrait and shows in landscape.

---

### R10 · RequestsViewModel: depend on UserReadRepository

**Rating 34** (E 90 · I 10 · F 95) · proposed by: claude (list 12 #6, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelLeaderHandOffTest.kt`

**Verified on master:** Injects `UserRepository` (:52) but only calls `getUserModel()` (:69, :87, :101, :135). `UserReadRepository` exists and is bound (`RepositoryModule.kt:229`).

**Do:** Change the type and imports; narrow the test mocks.

**Acceptance:** Both test classes pass.

---

### R11 · ChatViewModel + test: narrow to UserReadRepository and the share interfaces

**Rating 34** (E 90 · I 10 · F 95) · proposed by: claude (list 12 #7, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`

**Verified on master:** Production injects `UserRepository` (:53) for `getUserById` only (:185, :291). The test declares broad `TeamsRepository`/`VoicesRepository` mocks (:46-47) although the constructor takes `TeamsShareRepository`/`VoicesShareRepository`. Shares a file with P3 and T32, so sequence them.

**Do:** Narrow the production type and all three test mocks.

**Acceptance:** ChatViewModelTest passes.

---

### R12 · AddResourceViewModel/ResourceDetailViewModel: depend on UserReadRepository

**Rating 34** (E 90 · I 10 · F 95) · proposed by: claude (list 12 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModel.kt`, + their two tests

**Verified on master:** Both only call `getUserModel()` (:21, :20).

**Do:** Narrow the types and test mocks.

**Acceptance:** Both tests pass.

---

### R13 · ProgressViewModel/CourseProgressViewModel: depend on UserReadRepository

**Rating 34** (E 90 · I 10 · F 95) · proposed by: claude (list 12 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt`, + their two tests

**Verified on master:** Both only call `getUserModel()` (:26). Keep the local-id versus remote-_id distinction.

**Do:** Narrow the types and test mocks.

**Acceptance:** Both tests pass.

---

### R14 · FeedbackComposerViewModel: depend on UserReadRepository

**Rating 34** (E 90 · I 10 · F 95) · proposed by: claude (list 12 #10, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModelTest.kt`

**Verified on master:** Only `getUserModel()?.name` (:38).

**Do:** Narrow the type and test mock.

**Acceptance:** Test passes.

---

### T30 · build.yml: exclude test reports from the build cache and retry stale incremental compiles

**Rating 34** (E 60 · I 15 · F 75) · proposed by: devin (list 01 #9, 1.00)

**Files:** `.github/workflows/build.yml`

**Verified on master:** `build.yml` caches `app/build` without the `!test-results`/`!reports` exclusions `test.yml` has (:47-48), and it lacks test.yml's `Incremental compilation failed` retry (:73). Caveat: `assemble*Debug` produces no test results, so the exclusions are close to a no-op; the retry is the real value.

**Do:** Add the two exclusions and the tee+grep+`rm -rf app/build/kotlin`+retry block. Re-check open-PR ownership of `build.yml`.

**Acceptance:** YAML parses and a stale cache triggers exactly one retry.

---

### T25 · Replace `Thread.sleep` polling in AchievementsAdapterTest and ChatHistoryAdapterTest

**Rating 32** (E 85 · I 12 · F 60) · proposed by: copilot (list 03 #4, 0.50), devin (list 01 #5, 0.50)

**Files:** `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementsAdapterTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt`

**Verified on master:** `Thread.sleep(10)` loops at `AchievementsAdapterTest.kt:84,111` and `ChatHistoryAdapterTest.kt:55`. Caveat: `AsyncListDiffer` diffs on a real executor, so list 03's `flushBackgroundThreadScheduler()` idea likely won't help in PAUSED looper mode. List 01's one helper with a 1 ms yield is the safe floor.

**Do:** Extract one `waitForList` helper per file; idle the main looper and yield briefly, keeping an iteration cap. Assertions unchanged.

**Acceptance:** Both classes pass with fewer real-time sleeps.

---

### T32 · Migrate ChatViewModelTest, ResourcesViewModelTest, SurveysViewModelTest to MainDispatcherRule

**Rating 32** (E 85 · I 10 · F 85) · proposed by: copilot (list 03 #9, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt`

**Verified on master:** All three hand-roll `Dispatchers.setMain/resetMain` (:56/:70, :41/:51, :46/:66). `MainDispatcherRule(testDispatcher)` exists, and 30 test files still call `setMain`.

**Do:** Replace the manual set/reset with `@get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)` and drop the unused imports.

**Acceptance:** All three classes pass unchanged.

---

### P24 · BaseTeamFragment/BaseContainerFragment: replace `printStackTrace`

**Rating 31** (E 95 · I 8 · F 90) · proposed by: devin (list 06 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt`

**Verified on master:** `printStackTrace` at `BaseTeamFragment.kt:62` and `BaseContainerFragment.kt:173,175`; neither file imports `Log`.

**Do:** Add the import and a TAG; use `Log.e`.

**Acceptance:** `grep printStackTrace` is empty in both.

---

### R9 · UserDao: chunk getUsersByAnyIds/getGuestUsersByNames inside the DAO

**Rating 31** (E 80 · I 10 · F 80) · proposed by: claude (list 12 #5, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/UserDaoTest.kt`

**Verified on master:** Unbounded IN queries (:13-14, :45-46), but every caller already chunks (400/400/500 in `UserRepositoryImpl.kt:115, 1099, 1104`). Defensive only.

**Do:** Wrap with dedup + chunked(450 for the double-IN, 900 for names) and dedup results by local id.

**Acceptance:** Room tests with 1201+ inputs pass.

---

### T27 · Shrink the SyncTimeLoggerConcurrencyTest stress loop

**Rating 31** (E 85 · I 10 · F 75) · proposed by: devin (list 01 #6, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerConcurrencyTest.kt`

**Verified on master:** `threadCount = 10`, `iterationsPerThread = 100`, `awaitTermination(10s)` (:39-56).

**Do:** Use 4 threads × 50 iterations and keep the `generateSummary` cadence and the timeout assertion. This weakens race coverage somewhat; the time saved is small.

**Acceptance:** Passes; `expectedCalls` derives from the constants.

---

### P25 · FileUtils/KeyboardUtils/RetryUtils: remove `printStackTrace`

**Rating 30** (E 90 · I 8 · F 90) · proposed by: devin (list 06 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt`

**Verified on master:** `printStackTrace` at FileUtils:255, KeyboardUtils:16 (one call, not the 'both' list 06 claims) and RetryUtils:31. RetryUtils has no `android.*` imports.

**Do:** Use `Log.e` in the first two; delete the RetryUtils line without adding `android.*`. KeyboardUtils also has P11, so sequence the two.

**Acceptance:** grep is empty; RetryUtils stays platform-free.

---

### R15 · Share the voices draft-image JSON entry builder between BaseVoicesFragment and ReplyActivity

**Rating 30** (E 80 · I 10 · F 65) · proposed by: devin (list 11 #5, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/model/News.kt`

**Verified on master:** Duplicated `imageUrl`/`fileName` building and duplicate checks (BaseVoicesFragment :157-184, ReplyActivity :217-238; shifted since the base). All three files changed on master this week and sit in voices open-PR territory.

**Do:** Add `News.createImageListEntry`/`imageListEntryPath` companions and use them in both places.

**Acceptance:** Duplicate images are still rejected in both screens.

---

### P23 · ExamTakingFragment: replace `printStackTrace` with tagged `Log.e`

**Rating 29** (E 95 · I 8 · F 70) · proposed by: devin (list 06 #7, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt`

**Verified on master:** `printStackTrace` at :142, :177, :664. The file is in open PRs per lists 07 and 10.

**Do:** Use `Log.e(TAG, "…", e)` with an existing or new TAG.

**Acceptance:** `grep printStackTrace` is empty.

---

### R16 · EditAchievementFragment: move achievement-entry JSON assembly to model/VM

**Rating 28** (E 75 · I 10 · F 55) · proposed by: devin (list 11 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt`

**Verified on master:** The fragment owns `achievementArray` and mutates it at :118, :268-281, :382, :413, :454; `Achievement.createReference` (:156) is the precedent.

**Do:** Add `Achievement.createEntry`, hold the array in the VM, and keep dialogs in the fragment.

**Acceptance:** Create/edit preserves entries, resources and references.

---

### T26 · TeamResourcesAdapterTest: bound `idleMainLooperUntil` without a wall-clock deadline

**Rating 26** (E 80 · I 8 · F 55) · proposed by: copilot (list 03 #5, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesAdapterTest.kt`

**Verified on master:** The helper loops `idleMainLooper()` + `Thread.sleep(10)` against `System.currentTimeMillis()` (:105-108).

**Do:** Use a fixed iteration cap with a hard `assertTrue(condition())` at the end. The same executor caveat as T25 applies.

**Acceptance:** The class passes; no wall-clock deadline remains.

---

### P27 · FileUtils.findHtmlCoverImage: memoise a positive name-hint hit

**Rating 25** (E 70 · I 8 · F 60) · proposed by: copilot (list 07 #2, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`

**Verified on master:** Walks to depth 4 (:128). Both callers already cache per libraryId (`ResourcesCardBinder.kt:150-154`, `InlineResourceAdapter.kt:252`), so this only helps cross-adapter or new-adapter cases and still pays on first open. Shares FileUtils with P25.

**Do:** Cache by canonical directory path and validate by existence and length.

**Acceptance:** Walk order and tie-break unchanged.

---

### P35 · TagEntity.getTagsArray: build the Gson array directly

**Rating 24** (E 85 · I 5 · F 85) · proposed by: claude (list 09 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/GsonUtilsKotlinxBridgeTest.kt`

**Verified on master:** `buildJsonArray{…}.toGson()` (:63-67) builds two trees for a handful of IDs.

**Do:** Build a `JsonArray` directly, preserving nulls and order.

**Acceptance:** Identical payloads.

---

### P36 · TeamPagerAdapter.updatePages: skip equal page lists

**Rating 24** (E 85 · I 5 · F 85) · proposed by: claude (list 09 #9, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapterTest.kt`

**Verified on master:** Always diffs (:37-49); there is one caller (`TeamDetailFragment.kt:158`). Lists have at most ~10 tabs.

**Do:** Early-return on structural equality.

**Acceptance:** Stable IDs and containment unchanged.

---

### P33 · UsersAdapter: set the click listener once in ViewHolder init

**Rating 23** (E 80 · I 5 · F 85) · proposed by: copilot (list 08 #8, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt`

**Verified on master:** `setOnClickListener` runs per bind (:31-37); HealthUsersAdapter already does it in init. The avatar-size half of the original order is a no-op because it is already resolved once.

**Do:** Move the listener into init using `bindingAdapterPosition`.

**Acceptance:** Taps still open the right user.

---

### R18 · Dictionary lookup: trim inside the repository

**Rating 23** (E 80 · I 5 · F 85) · proposed by: claude (list 12 #4, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`, + their two tests

**Verified on master:** The VM trims (:58-61) and the repository doesn't (:25-26), but the VM is the only caller, so the inconsistency is hypothetical today.

**Do:** Trim and blank-guard in the repository; the VM keeps the isBlank no-op.

**Acceptance:** Padded words are found; blank input doesn't query.

---

### P26 · UserInformationFragment: single TAG, trim happy-path `Log.d` noise

**Rating 21** (E 85 · I 4 · F 85) · proposed by: devin (list 06 #10, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt`

**Verified on master:** Ten `Log.d("UserInformationFragment", …)` literals (:92…:296; lines shifted by 1 since the base).

**Do:** Add one TAG constant and drop or downgrade the happy-path debug logs.

**Acceptance:** One TAG; behaviour unchanged.

---

### P28 · ChatAdapter: append without copying the whole list

**Rating 21** (E 70 · I 6 · F 45) · proposed by: copilot (list 07 #5, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`

**Verified on master:** `addQuery`/`addResponse` do `currentList.toMutableList()` + `submitList` (:119-133). The copy is O(n) references, not message bodies, and the diff runs off-thread. Leaving `ListAdapter` semantics is the main risk.

**Do:** Only if profiling shows it matters; otherwise skip.

**Acceptance:** Append and prepend behaviour unchanged.

---

### P37 · ResourcesViewModel title sort: sort the decorated list in place

**Rating 21** (E 85 · I 4 · F 85) · proposed by: claude (list 09 #10, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt`

**Verified on master:** `map{…}.sortedBy{…}.map{…}` (:203-205) costs one extra list allocation. Same file as P13, so sequence them.

**Do:** Pre-size a mutable list and use a stable in-place sort.

**Acceptance:** Tie order preserved in both directions.

---

### R17 · Extract challenge-dialog evaluation into a use case

**Rating 21** (E 75 · I 5 · F 60) · proposed by: devin (list 11 #6, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`, new usecase/EvaluateChallengeDialogUseCase.kt

**Verified on master:** The logic is in `evaluateChallengeDialog` (:286-340), but its promo window is hard-coded to 2024-11-01..2024-12-16 (:292-293) and the date gate runs only after four repository queries. Extracting it preserves dead work on every dashboard open; deleting the expired promo is the better follow-up (not proposed by any agent). DashboardViewModel is in open PR #17811.

**Do:** If kept, at least move the date gate before the fan-out.

**Acceptance:** Dialog data unchanged.

---

### P29 · ResourcesMediaType.classify: bounded memo

**Rating 20** (E 65 · I 5 · F 60) · proposed by: copilot (list 07 #4, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaType.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaTypeTest.kt`

**Verified on master:** `classify`/`extensionOf` lowercase and parse the extension per call (:20-29, :69-73). The cache key (three strings) costs about the same to hash as the work it saves. Note: the file already imports `Context` (`displayName`), so list 07's 'portable' claim is overstated.

**Do:** Low priority. If done, a small access-ordered map keyed by the triple.

**Acceptance:** Every existing classify case returns the same enum.

---

### T31 · Merge the three plain-JUnit DownloadService* helper test classes

**Rating 20** (E 50 · I 5 · F 80) · proposed by: devin (list 01 #10, 1.00)

**Files:** `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceCompletionTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceRemainingCountTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceUrlSelectionTest.kt`

**Verified on master:** The three runner-free classes exist (28/67/128 lines). Class-load cost for plain JVM classes is milliseconds, so this is code-organisation only.

**Do:** Move all tests into one class with names preserved; delete the two emptied files.

**Acceptance:** Test count unchanged.

---

### P30 · ExamAnswerUtils: normalise each answer once

**Rating 18** (E 70 · I 3 · F 80) · proposed by: copilot (list 07 #6, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`

**Verified on master:** Three `Locale.getDefault()` reads and lowercase/sort per check (:70-95). This runs once per answer submission, not in a hot loop.

**Do:** Normalise once per call.

**Acceptance:** Existing scoring tests pass.

---

### P32 · SurveysAdapter: resolve button labels and guest flag once

**Rating 18** (E 70 · I 3 · F 85) · proposed by: copilot (list 08 #5, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt`

**Verified on master:** `context.getString` ×3 and `userId?.startsWith("guest")` per bind (:109-117). Both are trivially cheap.

**Do:** Hoist both to `onCreateViewHolder`/constructor.

**Acceptance:** Labels unchanged.

---

### P34 · CollectionsFragment: memoise lowercased tag names

**Rating 16** (E 70 · I 3 · F 60) · proposed by: claude (list 09 #7, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/CollectionsFragmentTest.kt`

**Verified on master:** `it.name?.lowercase(Locale.ROOT)` per tag per refresh (:113-115). Tag lists are small, and a 128-entry LRU adds more code than it saves.

**Do:** Recommend skipping.

**Acceptance:** n/a

---

### P31 · ResourcesViewModel.getFilterFacets: cache by content signature

**Rating 15** (E 60 · I 3 · F 50) · proposed by: copilot (list 07 #10, 1.00)

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt`

**Verified on master:** Rebuilds on each call (:135-155) on `dispatcherProvider.default`. The content signature the task requires is itself an O(n) walk of the same fields, so the net gain is close to zero.

**Do:** Recommend skipping.

**Acceptance:** n/a

---

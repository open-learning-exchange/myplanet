# myPlanet refactor round: performance quick wins (10 work orders)

- **Date:** 2026-10-09
- **Base commit:** `f18c50d` (`origin/master`, "all: smoother importing (fixes #18089) (#18088)")
- **Open PRs checked:** 32 open PRs, all checked. Numbers: 4075, 8175, 10993, 13287, 13355, 13415, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 15108, 15226, 15266, 15267, 15559, 15808, 15820, 15824, 15825, 15951, 16594, 16623, 17694, 17811, 17832, 18029, 18045, 18050, 18076.
- **Collision rule:** I computed the files each open PR touches with `git diff --name-only origin/master...pr/<n>`, which gave 1,308 distinct files. No task below touches any of them. This rule is stricter than "recent PRs labelled ready or merge only": it covers **every** open PR, including drafts and the KMP phase PRs.
- **Off-limits areas:** these are owned by open PRs, so every task here avoids them:
  - `TeamsRepositoryImpl`, `UserRepositoryImpl`, `ResourcesRepositoryImpl`, `CoursesRepositoryImpl`, `ChatRepositoryImpl`, `ActivitiesRepositoryImpl`, `ConfigurationsRepositoryImpl`
  - `SyncManager`, `TransactionSyncManager`, `UploadManager`, `UploadCoordinator`
  - `AppDatabase`, `RoomModule`, `NetworkModule`
  - `ChatDetailFragment`, `TakeCourseFragment`, `ExamTakingFragment`, `ResourcesFragment`, `DashboardActivity`
- **Conventions for every task:**
  - Paths are relative to the repo root. `M/` = `app/src/main/java/org/ole/planet/myplanet/` and `T/` = `app/src/test/java/org/ole/planet/myplanet/`.
  - Each task is independent and can merge in any order. No file appears in two tasks.
  - Follow `docs/CODE_STYLE_GUIDE.md`: MockK, `runTest`, `TestDispatcherProvider`, and no hard-coded `Dispatchers.*`.
  - PR title style (per the `merge-prepping` skill): `scope: smoother thing doing (fixes #N)`.

---

### 1. Stop retrying network errors that cannot recover in `RetryInterceptor` (roadmap 5+7, moves 9)

**context:**
- `RetryInterceptor.intercept` treats every `IOException` the same way. See `M/data/api/RetryInterceptor.kt:50-53`: `} catch (e: IOException) { // Network failures (e.g. SocketTimeoutException) are retried like 5xx. lastError = e }`.
- So `UnknownHostException` (device offline, DNS failure) and TLS handshake failures get 3 retries with 1 s + 2 s + 4 s of backoff. Each request through the shared client wastes about 7 s before the caller sees an error that was certain from the first attempt.
- This client is the default for sync and upload, so offline sync attempts stall for the full backoff on every call.

**files:**
- Edit `M/data/api/RetryInterceptor.kt` (class `RetryInterceptor`, function `intercept`).
- Edit `T/data/api/RetryInterceptorTest.kt` (add cases next to `testRetriesUpToMaxRetriesOnIOException`, line 100).
- Leave alone:
  - `M/di/NetworkModule.kt` (an open PR owns it).
  - `M/data/api/ApiClient.kt` (off-limits).
  - `RetryQueue`.

**steps:**
1. Add a private helper to `RetryInterceptor`'s companion object: `isPermanentFailure(e: IOException): Boolean`. It returns true for:
   - `java.net.UnknownHostException`
   - `javax.net.ssl.SSLHandshakeException`
   - `javax.net.ssl.SSLPeerUnverifiedException`

   Use only `java.*`/`javax.*` types and add no new `android.*` import.
2. In the `catch (e: IOException)` block (line 50), record `lastError = e` as today. Then break out of the loop immediately when `isPermanentFailure(e)` is true, so no broadcast and no backoff happen. The existing `return response ?: throw (lastError ...)` at line 74 rethrows it.
3. Keep `SocketTimeoutException`, `ConnectException` and other `IOException`s on the retry path. Their behaviour must stay exactly as it is.
4. Add two tests to `RetryInterceptorTest`, modelled on `testRetriesUpToMaxRetriesOnIOException`:
   - One where `chain.proceed` throws `UnknownHostException`.
   - One where it throws `SSLHandshakeException`.

   Each asserts the same exception type is rethrown, `verify(exactly = 1) { chain.proceed(request) }`, and `verify(exactly = 0) { broadcastService.trySendBroadcast(any()) }`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.api.RetryInterceptorTest"` is green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- `./gradlew assembleDefaultDebug` succeeds.
- Manual: in airplane mode, start a manual sync. The failure message appears within about 1 s instead of after about 7 s. With the server reachable, sync still completes normally.

**size budget:** about 15 changed lines of production code and about 40 of test code; 2 files.

**out of scope:**
- Do not change `maxRetries`, the backoff factor, or the retry rules for POST paths.
- Do not touch the `Intent`/`BroadcastService` plumbing.

---

### 2. Probe the primary and alternative server URLs in parallel in `ServerReachabilityProvider` (roadmap 5+7, moves 9)

**context:**
- `isServerReachable` checks URLs one after the other. See `M/utils/ServerReachabilityProvider.kt:37-42`: `for (url in urlsToTry) { if (tryConnect(url)) { reachable = true; break } }`.
- Each probe can take the reachability client's full timeout, plus a GET retry after a 405 or 501. When the primary is down but the mapped alternative URL is up, the user waits one full timeout before the alternative is even tried.
- This check gates sync start (`ui/sync/SyncActivity.kt`) and chat. The class is pure Kotlin plus OkHttp with no `android.*` imports, so it is already KMP-shaped.

**files:**
- Edit `M/utils/ServerReachabilityProvider.kt` (functions `isServerReachable`, `tryConnect`, `probe`).
- Edit `T/utils/ServerReachabilityProviderTest.kt`.
- Leave alone:
  - `M/services/sync/ServerUrlMapper.kt` (an open PR owns it).
  - `M/repository/ConfigurationsRepositoryImpl.kt` (an open PR owns it).
  - `M/di/NetworkModule.kt` (an open PR owns it).

**steps:**
1. When `urlsToTry.size == 1`, keep today's single `tryConnect` call.
2. When there are two URLs, start both `tryConnect` calls concurrently inside `coroutineScope` (for example, `channelFlow { urlsToTry.forEach { launch { send(tryConnect(it)) } } }.firstOrNull { it } ?: false`). Return as soon as one returns `true`, and return `false` only after both are done.
3. Make the losing probe abort promptly. `probe` calls blocking `execute()`, and structured concurrency waits for a cancelled child, so without this step nothing is gained.
   - Create the `Call` in `tryConnect`.
   - Register `coroutineContext.job.invokeOnCompletion { cause -> if (cause is CancellationException) call.cancel() }`.
   - Then execute it.

   Keep the existing HEAD → GET fallback on 405/501 and the `CancellationException` rethrow.
4. Keep the cache write (`reachabilityCache[urlString] = reachable to timeProvider.now()`) and `isPrimaryServerReachable` unchanged.
5. Update the tests:
   - In `isServerReachable falls back to alternative URL if primary fails`, stub `cancel()` on both mocked `Call`s, because the losing call may now be cancelled.
   - Add a test where the alternative succeeds and the primary's `execute()` blocks until cancelled (for example, a `CountDownLatch` released by a stubbed `cancel()`). Assert the result is `true` and that `cancel()` was invoked on the primary call.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.ServerReachabilityProviderTest"` is green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- Manual: configure a server whose primary URL is unreachable but whose alternative URL (from `ServerUrlMapper`) is up. Sync starts without the extra timeout wait. With only the primary configured, behaviour is unchanged.

**size budget:** about 35 changed lines of production code and about 50 of test code; 2 files.

**out of scope:**
- No change to timeouts, cache TTL (`REACHABILITY_CACHE_TTL_MS`) or the `@ReachabilityHttpClient` client.
- No new dependency (do not add `okhttp-coroutines`).

---

### 3. Show course-step content before the server check, not after it (roadmap 3+7, moves 10)

**context:**
- `CoursesStepsViewModel.loadStep` publishes nothing until a network reachability check finishes. Line 84: `val serverAvailable = configurationsRepository.checkServerAvailability()`. Line 106: `_uiState.value = CourseStepUiState(...)` comes after it.
- Whenever a step has a resource that isn't downloaded, the title, markdown and exam buttons stay hidden behind that HTTP probe. In an offline-first app, that means a full timeout on every step open while offline.
- Publishing state first and patching the download flag afterwards is also the hoisted-state shape that a later Compose step screen needs.

**files:**
- Edit `M/ui/courses/CoursesStepsViewModel.kt` (function `loadStep`, lines 61-120).
- Edit `T/ui/courses/CoursesStepsViewModelTest.kt`.
- Leave alone:
  - `M/ui/courses/CourseStepFragment.kt` (it already renders `isDownloadingResources` at lines 108-112).
  - `M/repository/ConfigurationsRepositoryImpl.kt` (an open PR owns it).
  - `M/ui/courses/TakeCourseFragment.kt` (an open PR owns it).

**steps:**
1. In `loadStep`, build and assign `_uiState.value = CourseStepUiState(...)` with `isDownloadingResources = false`. Do this right after `markdownContentWithLocalPaths` and `notDownloaded` are computed, before any server check.
2. Move the reachability check into the existing fire-and-forget `viewModelScope.launch` used for `downloadResourcesPriority`.
   - When `notDownloaded.isNotEmpty()` and `checkServerAvailability()` returns true, call `_uiState.update { if (it.step?.id == data.step.id) it.copy(isDownloadingResources = true) else it }`.
   - Then call `resourcesRepository.downloadResourcesPriority(notDownloaded)`.
3. Leave the `nextStepId` prefetch block as is.
4. Remove the now-unused local `var isDownloading`.
5. Add a test asserting that `uiState.value.step` is set and `isLoading == false` while `checkServerAvailability` is still suspended (stub it with a `CompletableDeferred` that is completed later). The existing test around line 199 (`assertTrue(state.isDownloadingResources)` after `advanceUntilIdle()`) must still pass unchanged.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest"` is green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- Manual, offline: opening a course step with undownloaded resources shows its text immediately. Online, the download progress bar still appears and resources still download.

**size budget:** about 20 changed lines of production code and about 30 of test code; 2 files.

**out of scope:**
- Do not cache server availability in the ViewModel.
- Do not change `CourseStepFragment` rendering or `ResourceDownloadCoordinator`.

---

### 4. Stop reloading all chat history every time the chat list returns to the foreground (roadmap 3+7, moves 10)

**context:**
- `ChatViewModel` declares `_refreshChatSignal = MutableSharedFlow<Unit>(replay = 1)` (line 67) and seeds it in `init` (line 70).
- `ChatHistoryFragment.setupRealtimeSync` collects it with `collectWhenStarted(sharedViewModel.refreshChatSignal) { refreshChatHistory() }` (lines 206-208). `collectWhenStarted` (`M/utils/FlowExtensions.kt:14-20`) uses `repeatOnLifecycle(STARTED)`, so the replayed `Unit` is re-delivered on every `onStart`.
- Each re-delivery runs `loadChatHistoryScreenData`. Lines 139-144 run planet news and chat history **sequentially**, then call `chatRepository.extractSharedViewInIds(newsMessages)`, which Gson-parses every news row on the main dispatcher.

**files:**
- Edit `M/ui/chat/ChatHistoryFragment.kt` (function `setupRealtimeSync`).
- Edit `M/ui/chat/ChatViewModel.kt` (function `loadChatHistoryScreenData`).
- Leave alone:
  - `M/ui/chat/ChatDetailFragment.kt` (an open PR owns it).
  - `M/repository/ChatRepositoryImpl.kt` (an open PR owns it).
  - `M/utils/FlowExtensions.kt` (shared by many screens).
  - The `replay = 1` declaration. `T/ui/chat/ChatViewModelTest.kt:119` ("delivers a missed update to a subscriber that attaches after the emit") depends on it.

**steps:**
1. In `ChatHistoryFragment.setupRealtimeSync`, replace the `collectWhenStarted(sharedViewModel.refreshChatSignal) { ... }` call with one collection per view: `viewLifecycleOwner.lifecycleScope.launch { sharedViewModel.refreshChatSignal.collect { refreshChatHistory() } }`. Leave the `shareResult` collector below it unchanged.
2. In `ChatViewModel.loadChatHistoryScreenData`, inside the `RetryUtils.retry` block, run these two independent calls concurrently with `coroutineScope { async { ... } }`, then await both:
   - `voicesRepository.getPlanetNewsMessages(...)`
   - `chatRepository.getChatHistoryForUser(...)`
3. Wrap `chatRepository.extractSharedViewInIds(newsMessages)` in `withContext(dispatcherProvider.default)`. `ChatViewModel` already receives `dispatcherProvider`.
4. Fix imports with `python3 .agents/skills/kotlin-importing/kotlin-importing.py --check app/src` after `git submodule update --init --recursive`, or by hand.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.*"` is green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- Manual:
  - Open the chat history and press Home, then return. No reload spinner appears and the list keeps its scroll position.
  - A new chat synced from the server still appears, since realtime updates still emit.
  - Sharing a chat still updates the list.

**size budget:** about 20 changed lines; 2 files.

**out of scope:**
- Do not change `ChatRepository.extractSharedViewInIds` or `ChatConversationPaginator`.
- Leave the fragment's post-share recompute (around line 220) as is.

---

### 5. Log a course visit once per course open, not on every `onStart` (roadmap 3+7+8)

**context:**
- `TakeCourseFragment` collects `viewModel.uiState` with `collectLatestWhenStarted` (line 74). Each `Success` re-delivery calls `bindCourse` (line 85), which calls `setCourseData()` (line 136), which calls `viewModel.logCourseVisit(cId, courseTitle, userName)` (line 241).
- Re-deliveries happen when returning from background, from an exam, and after the join `forceRefresh`. Each one makes `ActivitiesRepositoryImpl.logCourseVisit` insert a new `CourseActivity` row of type `visit`, which is later uploaded.
- That is a wasted DB write plus an upload per resume, and it inflates visit statistics on the server.

**files:**
- Edit `M/ui/courses/TakeCourseViewModel.kt` (function `logCourseVisit`, lines 57-59; private state near `hasOfferedJoinDialog`, line 50).
- Edit `T/ui/courses/TakeCourseViewModelTest.kt`.
- Leave alone:
  - `M/ui/courses/TakeCourseFragment.kt` (an open PR owns it).
  - `M/repository/ActivitiesRepositoryImpl.kt` (an open PR owns it).

**steps:**
1. Add `private var loggedVisitCourseId: String? = null` to `TakeCourseViewModel`, next to `hasOfferedJoinDialog`.
2. In `logCourseVisit(courseId, courseTitle, userName)`:
   - Return immediately when `courseId == loggedVisitCourseId`.
   - Otherwise call `activitiesRepository.logCourseVisit(...)`, and set `loggedVisitCourseId = courseId` only after that call returns without throwing.
3. Keep the public signature unchanged so the fragment needs no edit. The ViewModel is fragment-scoped, so reopening the course creates a new instance and logs again, which is intended.
4. Add tests next to `logCourseVisit_delegatesToActivitiesRepositoryWithUserName` (line 180):
   - `logCourseVisit_calledTwiceForSameCourse_logsOnce`: `coVerify(exactly = 1)`.
   - `logCourseVisit_forDifferentCourse_logsAgain`.
   - `logCourseVisit_whenRepositoryThrows_retriesOnNextCall`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest"` is green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- Manual: open a course, background the app, and return 3 times. Exactly one new `visit` row exists for that open (check with Database Inspector, table of `CourseActivity`). Back out and reopen the course, and exactly one more row is added.

**size budget:** about 8 changed lines of production code and about 35 of test code; 2 files.

**out of scope:**
- Do not move the visit-logging call site out of the fragment.
- Do not dedupe in `ActivitiesRepositoryImpl`.

---

### 6. Load the health patient list once and stop rebuilding the records RecyclerView (roadmap 3+7)

**context:**
- `MyHealthFragment` makes two calls when it opens the select-patient dialog:
  - `viewModel.loadPatients()` at line 272.
  - The `spn_sort` Spinner (`android:entries="@array/sort_member"` in `alert_health_list.xml:19`) fires `onItemSelected(0)` on first layout, which calls `viewModel.searchPatients(memberQuery, "joinDate", false)` (lines 294-304).
- So there are two full user-table loads and sorts. `HealthViewModel.loadPatients` (lines 95-99) defaults to `descending = true` and is not tracked by `searchJob`, so the two results race and can show the opposite order from the spinner.
- On every patient-state emission, lines 194-198 also assign a new `LinearLayoutManager` and re-set `adapter = healthAdapter`, which throws away and re-creates every records view holder.

**files:**
- Edit `M/ui/health/MyHealthFragment.kt` (dialog setup at line 272, records binding at lines 194-198).
- Edit `M/ui/health/HealthViewModel.kt` (`loadPatients`, lines 95-99).
- Edit `T/ui/health/HealthViewModelTest.kt` (test `loadPatients updates patientList`, line 237).
- Leave alone:
  - `M/ui/health/HealthExaminationActivity.kt` (an open PR owns it).
  - `M/ui/health/HealthExaminationAdapter.kt` (an open PR owns it).
  - `M/repository/UserRepositoryImpl.kt` (an open PR owns it).

**steps:**
1. Delete `viewModel.loadPatients()` at `MyHealthFragment.kt:272`. The spinner's initial `onItemSelected` already loads the list with the sort the user sees.
2. Delete `HealthViewModel.loadPatients`, which is now unused in production.
3. Rewrite the test at `HealthViewModelTest.kt:237` to call `viewModel.searchPatients("", "joinDate", false)`. Keep its assertion that `patientList` is updated.
4. In `MyHealthFragment` lines 194-198:
   - Only assign `layoutManager`, `isNestedScrollingEnabled` and `adapter` when `binding.rvRecords.adapter !== healthAdapter`.
   - Keep `healthAdapter.updateData(...)` and the `binding.rvRecords.post { ... }` scroll unconditional.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.health.*"` is green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- Manual:
  - Open My Health → select member. The list appears once, oldest join date first, matching the spinner's first entry.
  - Changing the sort and searching still work.
  - Selecting a patient shows the records strip. Editing a record and returning keeps the horizontal list and scrolls to the last item.

**size budget:** about 15 changed lines; 3 files.

**out of scope:**
- No change to `UserRepository.getUsersSortedBy` or `searchUsers`.
- No layout XML changes.

---

### 7. Narrow `TeamDao.observeAll` to the rows its callers actually use (roadmap 1+7+8, moves 9)

**context:**
- `M/data/room/dao/TeamDao.kt:19` is `@Query("SELECT * FROM teams") fun observeAll(): Flow<List<MyTeam>>`.
- Its only callers, `TeamsRepositoryImpl.getMyTeamsFlow` (line 209) and `getMyTeamDetailsFlow` (line 297), keep only two kinds of rows:
  - membership rows (`docType == "membership"`);
  - root teams (`teamId.isNullOrBlank()`, via `isRootTeam()` at `TeamsRepositoryImpl.kt:1398`).
- Every report, transaction, resource link, request and course row is decoded through the type converters and then thrown away. This happens again on every write to `teams`, and the flows feed the always-visible dashboard team list, the calendar and `TeamViewModel`.

**files:**
- Edit `M/data/room/dao/TeamDao.kt` (`observeAll` only).
- Edit `T/data/room/dao/TeamDaoTest.kt`.
- Leave alone:
  - `M/repository/TeamsRepositoryImpl.kt` (an open PR owns it; its Kotlin filters stay as a second guard).
  - `M/data/room/AppDatabase.kt` (no schema change, no version bump).

**steps:**
1. Change the query to:

   `SELECT * FROM teams WHERE docType = 'membership' OR teamId IS NULL OR TRIM(teamId, ' ' || char(9, 10, 13)) = ''`

   Trimming tabs and newlines as well as spaces keeps the set a strict superset of what `isNullOrBlank()` keeps, so the repository's results are unchanged.
2. Keep the method name and signature, because the repository depends on them. Add a one-line KDoc: "Membership rows and root teams only. The rows `getMyTeamsFlow` / `getMyTeamDetailsFlow` filter from."
3. Add a Robolectric/Room test to `TeamDaoTest`, modelled on the existing `observeNonArchivedFinanceReportsByTeamId` tests (line 51). Insert:
   - a root team (`teamId = null`);
   - a root team with `teamId = ""`;
   - a membership row;
   - a `report` row;
   - a `resourceLink` row.

   Assert `observeAll().first()` returns exactly the first three.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.TeamDaoTest"` is green.
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.TeamsRepositoryImplTest"` is green (it mocks the DAO).
- Full suite green.
- Manual: Dashboard "My Teams", Teams list and Calendar show the same teams and enterprises as before for a user with memberships.

**size budget:** about 3 changed lines of production code and about 40 of test code; 2 files.

**out of scope:**
- Do not rename `observeAll`.
- Do not move the membership join into SQL inside `TeamsRepositoryImpl`.
- No new indices, because they would need an `AppDatabase` version bump.

---

### 8. Shrink the "opened resources" flow and drop the redundant main-thread resource sort (roadmap 1+3+7)

**context:**
- `M/data/room/dao/ResourceActivityDao.kt:29-30`: `SELECT * FROM resource_activity WHERE user = :userName AND type = :type` returns one full row **per open event**. Its only caller (`ResourcesRepositoryImpl.observeOpenedResourceIds`, line 554) reduces it to `mapNotNull { it.resourceId }.toSet()`. The result therefore grows with the total number of opens, and it is re-emitted on every insert while the Resources list is visible.
- Separately, `ResourcesViewModel.loadResources` (lines 97-101) calls `getCachedResources(...)` first, which runs `applyCurrentSortSynchronous` on the main thread. It only uses the result when `_resourcesState.value.isEmpty()`, so on every refresh after the first one the sort runs and is discarded.

**files:**
- Edit `M/data/room/dao/ResourceActivityDao.kt` (`observeByUserAndType` only).
- Edit `T/data/room/dao/ResourceActivityDaoTest.kt`.
- Edit `M/ui/resources/ResourcesViewModel.kt` (`loadResources`).
- Leave alone:
  - `M/repository/ResourcesRepositoryImpl.kt` (an open PR owns it).
  - `M/ui/resources/ResourcesFragment.kt` (an open PR owns it).
  - `M/ui/resources/ResourcesAdapter.kt` (an open PR owns it).

**steps:**
1. Change the DAO query to `SELECT * FROM resource_activity WHERE user = :userName AND type = :type AND resourceId IS NOT NULL GROUP BY resourceId`. This returns one row per distinct resource, so the repository's resulting set is identical. Add a one-line KDoc saying it returns one row per resource.
2. Add a test to `ResourceActivityDaoTest`. Insert:
   - three `resource_opened` rows for resource A;
   - one for resource B;
   - one row with null `resourceId`;
   - one row for another user.

   Assert `observeByUserAndType(user, "resource_opened").first()` has size 2 and resource IDs `{A, B}`.
3. In `ResourcesViewModel.loadResources`, replace lines 98-101 with a guarded form, so the cached sort only runs when its result will be used:

   `if (_resourcesState.value.isEmpty()) getCachedResources(isMyCourseLib, modelId)?.let { _resourcesState.value = it }`

4. Keep `getCachedResources` public, since `ResourcesFragment` calls it.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.ResourceActivityDaoTest"` is green.
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.resources.ResourcesViewModelTest"` is green, including the cached-state test around line 169.
- Full suite green.
- Manual: open a resource several times. The "opened" indicator in the Resources list still marks it. Sorting by date and title still works. Pull-to-refresh is unchanged.

**size budget:** about 6 changed lines of production code and about 35 of test code; 3 files.

**out of scope:**
- Do not change dispatcher choices for `applyCurrentSort` or `toggle*SortOrder`.
- Do not touch the repository mapping.

---

### 9. Hide the keyboard on touch-down only, and reuse one touch listener (roadmap 7+8)

**context:**
- `KeyboardUtils.setupUI` (`M/utils/KeyboardUtils.kt:20-37`) creates a new `View.OnTouchListener` for **every** view in the tree. Each listener calls `hideSoftKeyboard(activity)` on every `MotionEvent`, including each `ACTION_MOVE` while scrolling. See lines 22-24: `View.OnTouchListener { _: View?, _: MotionEvent? -> hideSoftKeyboard(activity); false }`.
- `hideSoftKeyboard` does `getSystemService`, reads `currentFocus` and calls `hideSoftInputFromWindow`, which is binder IPC when a field has focus. That runs dozens of times per scroll gesture on every screen that calls `setupUI` (5 call sites in `app/src/main`).

**files:**
- Edit `M/utils/KeyboardUtils.kt` (object `KeyboardUtils`, function `setupUI`).
- Edit `T/utils/KeyboardUtilsTest.kt`.
- Leave alone: all 5 `setupUI` call sites (signature unchanged), including `M/ui/dashboard/DashboardActivity.kt`, which an open PR owns.

**steps:**
1. Keep `fun setupUI(v: View, activity: Activity)` as the public entry point. Inside it, create one listener: `View.OnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) hideSoftKeyboard(activity); false }`.
2. Pass that listener to a new `private fun attachHideKeyboardListener(v: View, listener: View.OnTouchListener)`. This helper does today's recursion: set the listener on non-`EditText` views, and recurse into `ViewGroup` children.
3. Leave `hideSoftKeyboard` unchanged.
4. Update `KeyboardUtilsTest`:
   - The existing test at lines 75-88 uses a strict `mockk<MotionEvent>()`. Stub `every { actionMasked } returns MotionEvent.ACTION_DOWN`.
   - Add a test that `ACTION_MOVE` does **not** call `hideSoftInputFromWindow`.
   - Add a test that all children of a `ViewGroup` receive the same listener instance (capture with `slot` / `mutableListOf`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.KeyboardUtilsTest"` is green.
- Full suite green.
- Manual: on the Resources or Courses list, focus the search field, then tap outside it. The keyboard hides as before. Scrolling the list with no field focused feels unchanged, and scrolling with the keyboard open hides it on the first touch.

**size budget:** about 15 changed lines of production code and about 30 of test code; 2 files.

**out of scope:**
- Do not convert call sites to `WindowInsetsControllerCompat`.
- Do not change which views get the listener.

---

### 10. Render the exam markdown editor off the main thread (roadmap 7+8)

**context:**
- `BaseExamFragment.setMarkdownViewAndShowInput` attaches `MarkwonEditorTextWatcher.withProcess(markwonEditor)` for `textarea` questions (`M/base/BaseExamFragment.kt:191`).
- `withProcess` runs the full commonmark parse and span pass synchronously in `afterTextChanged` on the main thread for every keystroke. Typing in long survey and exam answers gets slower as the answer grows, on the low-end devices this app targets.
- Markwon's editor module (`libs.markwon.editor`, already a dependency) provides `MarkwonEditorTextWatcher.withPreRender(editor, executorService, editText)`, which parses on a background executor and applies spans on the main thread.

**files:**
- Edit `M/base/BaseExamFragment.kt` (fields near line 60-63, `setMarkdownViewAndShowInput` at line 185, `onDestroy` at line 212).
- Edit `T/base/BaseExamFragmentTest.kt`.
- Leave alone:
  - `M/ui/exam/ExamTakingFragment.kt` (an open PR owns it).
  - `M/ui/surveys/SurveyFragment.kt` (an open PR owns it).
  - `M/utils/MarkdownUtils.kt`.

**steps:**
1. Add `private val markdownExecutor: Lazy<ExecutorService> = lazy(LazyThreadSafetyMode.NONE) { Executors.newSingleThreadExecutor() }` to `BaseExamFragment`, next to `markwonEditor` (line 63). It is a plain `Lazy` field, not a delegate, so step 3 can check whether it was ever created.
2. At line 191, replace `MarkwonEditorTextWatcher.withProcess(markwonEditor)` with `MarkwonEditorTextWatcher.withPreRender(markwonEditor, markdownExecutor.value, etAnswer)`.
3. In `onDestroy()` (line 212), next to `CameraUtils.release()`, add `if (markdownExecutor.isInitialized()) markdownExecutor.value.shutdownNow()`. A fragment that never showed a textarea then never creates a thread.
4. In `BaseExamFragmentTest.setUp` (line 52), change the stub to `every { MarkwonEditorTextWatcher.withPreRender(editor, any(), any()) } returns markwonEditorTextWatcher`. Update any `verify { ...withProcess... }` to `withPreRender`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.base.BaseExamFragmentTest"` is green.
- Full suite green.
- `./gradlew assembleDefaultDebug` succeeds.
- Manual: take a survey with a textarea question. Type a long answer (500+ characters) with `**bold**` and `- list` markdown. Highlighting still appears, typing stays smooth, and the submitted answer text is unchanged.
- Leave the screen and return. There are no crashes or leaked-thread warnings in logcat.

**size budget:** about 12 changed lines of production code and about 5 of test code; 2 files.

**out of scope:**
- No change to the non-textarea `TextWatcher` branch or to `MarkdownUtils`.
- No switch to a Compose text field.

---

## Ranking rationale (P3: user impact ÷ blast radius)

| # | Task | User impact | Files | Risk |
|---|------|-------------|-------|------|
| 1 | Skip pointless retries | about 7 s saved per offline request | 2 | low |
| 2 | Parallel reachability probes | up to 1 timeout saved per sync start | 2 | medium |
| 3 | Course step renders before probe | instant step render offline | 2 | low |
| 4 | Chat history no-reload on resume | no reload or re-parse per foreground | 2 | low |
| 5 | One visit log per open | fewer DB writes and uploads, correct stats | 2 | low |
| 6 | Health patient list once | half the queries, no sort race | 3 | low |
| 7 | Narrow `TeamDao.observeAll` | smaller dashboard flow per team write | 2 | low |
| 8 | Opened-ids GROUP BY + no wasted sort | smaller flow, less main-thread work | 3 | low |
| 9 | Keyboard hide on DOWN only | less IPC while scrolling | 2 | low |
| 10 | Pre-render exam markdown | smooth typing in long answers | 2 | low |

## File-ownership matrix (R2: no file in two tasks)

| Task | Files |
|------|-------|
| 1 | `M/data/api/RetryInterceptor.kt`, `T/data/api/RetryInterceptorTest.kt` |
| 2 | `M/utils/ServerReachabilityProvider.kt`, `T/utils/ServerReachabilityProviderTest.kt` |
| 3 | `M/ui/courses/CoursesStepsViewModel.kt`, `T/ui/courses/CoursesStepsViewModelTest.kt` |
| 4 | `M/ui/chat/ChatHistoryFragment.kt`, `M/ui/chat/ChatViewModel.kt` |
| 5 | `M/ui/courses/TakeCourseViewModel.kt`, `T/ui/courses/TakeCourseViewModelTest.kt` |
| 6 | `M/ui/health/MyHealthFragment.kt`, `M/ui/health/HealthViewModel.kt`, `T/ui/health/HealthViewModelTest.kt` |
| 7 | `M/data/room/dao/TeamDao.kt`, `T/data/room/dao/TeamDaoTest.kt` |
| 8 | `M/data/room/dao/ResourceActivityDao.kt`, `T/data/room/dao/ResourceActivityDaoTest.kt`, `M/ui/resources/ResourcesViewModel.kt` |
| 9 | `M/utils/KeyboardUtils.kt`, `T/utils/KeyboardUtilsTest.kt` |
| 10 | `M/base/BaseExamFragment.kt`, `T/base/BaseExamFragmentTest.kt` |

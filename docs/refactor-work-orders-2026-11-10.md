2026-11-10 · base `2093a6224b221079f1413e2ab1f22cebec8cf736` · open PRs checked: 35 (#18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075). Every path those PRs touch is off-limits. None of the PRs updated since 2026-11-03 carry a review-ready or merge label.

Logs checked: master unit tests run 42782082676 (both shards over the 400s warn; "Robolectric classes dominate"), master release run 42782082588, build run 42781301168. The release discord `npm install` lives in `.github/workflows/release.yml`, which open PRs own, so it is not tasked. `android.enableJetifier=true` is warned in all three logs, but `gradle.properties:22-23` says it cannot be removed while material-dialogs and the other support-library artifacts remain, and `app/build.gradle` is off-limits. Do not flip that flag.

---

### 1. save a master debug build cache so PR builds stop cold-compiling (roadmap 7)

context: `.github/workflows/build.yml:5-7` runs on every branch except `master`, and its actions cache key is `build-dir-build-${{ matrix.build }}-${{ github.sha }}` with restore prefix `build-dir-build-${{ matrix.build }}-` (lines 50-52). GitHub only shares caches saved on the default branch. Build run 42781301168 therefore logged `Cache not found for input keys: build-dir-build-default-…, build-dir-build-default-` and the lite twin, then executed every task (default 4m36s, lite 4m3s). A master push that writes that same key prefix is the collision-free fix. This does not move roadmap 9 or 10; it only shortens the compile that those refactors have to wait on.

files: add `.github/workflows/warm-debug-cache.yml` only. Neighbor to leave alone: `.github/workflows/build.yml` (open PR #15226 owns it; copy its cache key, do not modify it).

steps:
1. New workflow, push to `master` only, `permissions: contents: read`, concurrency group that is not `build-${{ github.ref }}`.
2. Matrix `build: [default, lite]` on `ubuntu-24.04`, timeout 10 minutes, `fail-fast: false`.
3. Copy the checkout, `gradle/actions/setup-gradle@v6.4.1` (`cache-read-only: false`), and `actions/cache@v6` steps from `build.yml`, including the same path exclusions and the exact key and restore-keys above. A different key will not be restored by PR builds.
4. Run the same `assemble${FLAVOR^}Debug` command, including the existing retry that deletes `app/build/kotlin` only when the log contains `Incremental compilation failed`. Pass the same `GRADLE_BUILD_CACHE_*` env vars `build.yml` passes; do not invent new secrets.
5. Do not upload artifacts. The actions cache save is the deliverable.

acceptance: the new file is valid YAML; its cache `key` and `restore-keys` strings match `build.yml:50-52` exactly; `on.push.branches` is only `master`. No change to `./gradlew testDefaultDebugUnitTest` behavior. After it has run once on master, a later PR build's "restore project build dir" step must no longer log `Cache not found` for the `build-dir-build-default-` / `build-dir-build-lite-` prefix.

size budget: ~70 lines, 1 new file.

out of scope: do not edit `build.yml`, do not enable the remote Gradle cache, do not change product flavors.

---

### 2. move the retry Intent into BroadcastService and drop the Robolectric event test (roadmap 7+9)

context: `RetryInterceptor.kt:62-68` builds an `android.content.Intent` inside OkHttp `intercept` and is the class's only `android.*` import. The extras contract (`url`, `attempt`, `delay` on `Constants.ACTION_RETRY_EVENT`) is asserted only by `RetryInterceptorEventTest`, which is `@RunWith(RobolectricTestRunner)` for that one read. On master test run 42782082676 shard 2, `RetryInterceptorEventTest.testRetryEventUrlContainsOnlyEncodedPath` took 10.5s and the class took 12.4s. `RetryInterceptorTest` is already plain JUnit and sets `initialDelay = 10L` with `TestTimeProvider`. Moving Intent construction to the Android edge removes the platform import from the interceptor (roadmap 9) and deletes a Robolectric sandbox the shard is over budget for (roadmap 7).

files: `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt` (`intercept`), `app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt` (`trySendBroadcast`), `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`, and delete `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt`. Leave `app/src/test/java/org/ole/planet/myplanet/services/BroadcastServiceTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt`, and `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` alone.

steps:
1. Add `trySendRetryEvent(url: String, attempt: Int, delayMs: Long): Boolean` on `BroadcastService`. It must build `Intent(Constants.ACTION_RETRY_EVENT)` with the same three extra names and types the interceptor writes today, then call the existing `trySendBroadcast`.
2. Replace the Intent block in `RetryInterceptor.intercept` with that call. Delete the `android.content.Intent` import. Do not add any other `android.*` import.
3. Port the encoded-path assertion into `RetryInterceptorTest` by verifying `trySendRetryEvent("/db/_find", 1, 10L)` for the same `http://satellite:1234@host:5984/db/_find?x=y` POST that the Robolectric test uses. Keep `initialDelay = 10L`. Do not add `@RunWith` to `RetryInterceptorTest`.
4. Delete `RetryInterceptorEventTest.kt`. No suite list references it.

acceptance: `./gradlew testDefaultDebugUnitTest` green. `RetryInterceptor.kt` has zero `android.*` imports. The encoded path, attempt, and delay still match the old extras. Retrying a 5xx still broadcasts once per attempt; a successful response still does not.

size budget: ~40 changed lines, 3 files edited, 1 file deleted.

out of scope: do not change backoff, `maxRetries`, or which requests are retry-safe. Do not rename the action string or the extra keys.

---

### 3. query only the display-name column when resolving a content URI (roadmap 7)

context: `FileUtils.getDisplayName` (`FileUtils.kt:217`) and `FileUtils.resolveUriToPath` (`FileUtils.kt:243`) both call `contentResolver.query(uri, null, …)`. A null projection asks the provider for every column, then the code reads only `OpenableColumns.DISPLAY_NAME` (already imported at line 12). That runs on the attachment and share path, so a photo provider that also exposes a thumbnail blob does extra work for a file name. This is a local I/O cut, not a roadmap 9/10 move: both functions still take `Context`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` (`getDisplayName`, `resolveUriToPath`) and `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`. Do not edit `app/src/main/java/org/ole/planet/myplanet/utils/AttachmentReader.kt` or `findHtmlCoverImage`.

steps:
1. Pass `arrayOf(OpenableColumns.DISPLAY_NAME)` as the projection in both queries. Leave selection, selectionArgs, and sortOrder null.
2. Keep the existing fallbacks: `uri.lastPathSegment`, then `image_${timeProvider.now()}.jpg` in `getDisplayName`; the UUID file name and the traversal sanitizer in `resolveUriToPath`.
3. In `resolveUriToPath_copiesContentUriUsingDisplayName`, fail the test if the projection argument is null or is not that single column. The current fake provider ignores the projection, so this is a new assertion, not a behavior change.
4. Do not change `findHtmlCoverImage`. Its walk-order tests must stay green without edits.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including `FileUtilsTest`. Picking a content URI still copies a file named with the provider's display name, and a missing display name still falls back as today.

size budget: ~15 changed lines, 2 files.

out of scope: no caching of display names, no change to `readBytesFromUri` or `copyUriToFile`.

---

### 4. build the news-sync Room database once per test class (roadmap 7+8)

context: `VoicesRepositoryNewsSyncTest.setUp` (`app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:60-88`) calls `Room.inMemoryDatabaseBuilder` and `mockkObject` on `UrlUtils` and `NetworkUtils` before every test, then `db.close()` and `unmockkObject` in `tearDown`. Master test run 42782082676 shard 1 recorded 13.9s for this class excluding 0.8s of first-test excess, median 0.76s, across 18 tests. The class comment says this is the only suite that opens a real Room database for the news insert path. Reusing one database removes that fixed cost and does not change production code, so it does not itself advance roadmap 9.

files: `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt` only. Do not edit `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` or `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt`; both are owned by open PRs.

steps:
1. Open the in-memory `AppDatabase` in `@BeforeClass` with the same `allowMainThreadQueries()` builder. Close it in `@AfterClass`.
2. In `@Before`, call `db.clearAllTables()` before constructing `VoicesRepositoryImpl` with `db.newsDao()`. Do not add a DAO delete method.
3. Move `mockkObject(UrlUtils)` and `mockkObject(NetworkUtils)`, and their `every` stubs, to `@BeforeClass`. Move `unmockkObject` to `@AfterClass`. Keep the `getConcatenatedLinks()` null stub; the class comment explains why a relaxed mock NPEs.
4. Do not share inserted rows across tests. Each test must still start from an empty `news` table.

acceptance: `./gradlew testDefaultDebugUnitTest` green. The 18 tests still assert the same JSON-to-entity mapping, dedup, and top-level/reply queries. A second test in the class must not see rows inserted by the first.

size budget: ~40 changed lines, 1 file.

out of scope: do not switch this class off Robolectric, and do not change `insertNewsList`.

---

### 5. rewrite course-step markdown off the main dispatcher (roadmap 7+10)

context: `CoursesStepsViewModel.loadStep` hops to `dispatcherProvider.io` only to resolve the files dir (`CoursesStepsViewModel.kt:72-74`), then runs `MarkdownUtils.prependBaseUrlToImages` (line 76) and `data.resources.filter { !it.isResourceOffline() }` (line 82) back on the view-model dispatcher, which is Main. `prependBaseUrlToImages` scans the whole step body for `![…](…)`. The next-step prefetch (line 124) also filters `getAllStepResources` on that same dispatcher. Room already suspends inside the repositories; this task only moves the CPU that resumes on Main. Roadmap 10: the screen state is already a `StateFlow`. A later Compose step screen can collect it without inheriting a main-thread rewrite. Do not add Compose here.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` (`loadStep`). Leave `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` alone unless a test hangs, in which case only add the `advanceUntilIdle()` that file already uses. Do not edit `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt`, or the courses and resources repositories (open PRs own those repositories).

steps:
1. Build the markdown string and the `notDownloaded` list inside `withContext(dispatcherProvider.io)`, using the base dir already resolved there. Keep the `file://${baseDirPath}/ole/` prefix and the 600 by 350 arguments.
2. Assign `_uiState.value` only after that `withContext` returns, with the same `CourseStepUiState` fields as today, including `isLoading = false`.
3. Inside the existing `if (nextStepId != null)` launch, call `getAllStepResources` and filter `isResourceOffline()` on `dispatcherProvider.io` before `UrlUtils.getUrl`. Keep `startBackgroundDownload` and the empty-list skip.
4. Do not cancel or merge `loadDataJob` differently. The download launch at line 111 stays as it is.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including `CoursesStepsViewModelTest.loadStep_whenResolverReturnsFile_buildsMarkdownDescriptionWithLocalOlePath`. Opening a step still shows the same local image paths and still prefetches the next step's missing resources. The step title and exam flags do not flicker to empty while the rewrite runs.

size budget: ~25 changed lines, 1 file.

out of scope: no new repository method, no change to image width/height, no Compose screen.

---

### 6. run the team course picker off the main dispatcher (roadmap 7+10)

context: `TeamCoursesViewModel.getAvailableCourses` (`TeamCoursesViewModel.kt:55-59`) loads `coursesRepository.getAllCourses()` and then filters out ids from `teamsRepository.getTeamCourseIds`. `getAllCourses` maps the full course table on the caller thread. `TeamCoursesFragment.kt:109` calls it from `lifecycleScope.launch`, which is Main, before showing the add-course dialog. The repositories are owned by open PRs, so a SQL exclusion cannot be added in this round. Moving the existing calls onto `DispatcherProvider.io` is the unblock: the fragment can stay a thin collector, which is what a later Compose dialog needs (roadmap 10). The view model stays an Android `ViewModel`, so this is not roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt` and `app/src/test/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModelTest.kt`. Do not edit `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt`, the courses or teams repositories, or `app/src/main/java/org/ole/planet/myplanet/di/DispatcherModule.kt` (`provideDispatcherProvider` already exists).

steps:
1. Inject `DispatcherProvider` as a third constructor parameter. Hilt already binds it; do not add a module.
2. Wrap the two repository calls and the `courseId !in existingIds` filter in `withContext(dispatcherProvider.io)`. Return the same `List<MyCourse>`.
3. Update `TeamCoursesViewModelTest` to pass `TestDispatcherProvider(testDispatcher)`. The class already runs those tests with `runTest(testDispatcher)`.
4. Keep `loadCourses`, `addCourses`, and `removeCourse` on their current dispatchers.

acceptance: `./gradlew testDefaultDebugUnitTest` green. `getAvailableCourses filters out existing course ids` and `getAvailableCourses returns all when none are linked` still pass. The add-course dialog still lists courses that are not already on the team, and lists all courses when the team has none.

size budget: ~20 changed lines, 2 files.

out of scope: do not add `getCoursesExcluding`, do not change the dialog UI, do not parallelize `loadCourses`.

---

### 7. stop the playstore quota script from downloading full release documents (roadmap 7)

context: `playstore-quota.sh` `save_epochs` (`.github/scripts/playstore-quota.sh:36-44`) calls `gh api "repos/$REPO/releases?per_page=100"` whenever `RELEASES_JSON` is unset. That payload includes every release body and asset list. The function only reads `published_at` of non-drafts, then `measure` keeps epochs inside 24h plus `PLAYSTORE_SLOT_LAG`. `automerge.yml` and the playstore workflow both invoke this on the release path. Cutting the payload is a workflow quick win. It does not move roadmap 9 or 10.

files: `.github/scripts/playstore-quota.sh` (`save_epochs`) only. Do not edit any workflow YAML.

steps:
1. Leave the `RELEASES_JSON` branch unchanged so a fixture file still feeds the existing `jq` epoch pipeline.
2. Replace only the live `gh api repos/…/releases` call with one GraphQL request for the 100 newest releases, requesting `isDraft` and `publishedAt` and nothing else. Split `REPO` on `/` into owner and name.
3. Keep the epoch stream identical: non-drafts only, `fromdateiso8601`, newest first, one epoch per line. Drop nodes whose `publishedAt` is null.
4. Do not change `status`, `report`, or `forecast` output keys (`limit`, `used`, `held`, `free`, `next_free_epoch`, `next_free_local`, `frees_within_hour`, `report`).

acceptance: `bash -n .github/scripts/playstore-quota.sh` passes. With `RELEASES_JSON` set to a two-release fixture and `NOW_EPOCH` fixed, `status` still prints the same keys and the same `used` count as before this change. No Gradle test depends on this script. `./gradlew testDefaultDebugUnitTest` is untouched and must stay green.

size budget: ~25 changed lines, 1 file.

out of scope: do not change `LIMIT`, `LAG`, or the 24h window. Do not add a new dependency.

---

### 8. replace the automerge publish-failure N+1 with one check-run list (roadmap 7)

context: `publish_failed` in `.github/scripts/automerge.sh:336-360` lists every completed Actions run for the SHA, then calls the jobs API once per run, then the annotations API for the first job named `$PUBLISH_JOB`. A master SHA also has the test workflow, so this is one request plus two more per unrelated run, on the drain path that decides whether a playstore warning is still live. `PUBLISH_JOB` defaults empty and is set to `myPlanet release (lite)` by `automerge.yml`. The check-runs API returns that job id directly. This is a workflow quick win only; it does not move roadmap 9 or 10.

files: `.github/scripts/automerge.sh` (`publish_failed` only). Do not edit the workflow that sets `PUBLISH_JOB`.

steps:
1. Replace the runs loop with one `gh api repos/$REPO/commits/$sha/check-runs?per_page=100` filtered to `status == "completed"` and `name == $PUBLISH_JOB`. Use those ids as today's `job_id`.
2. Keep the annotations request and the `PUBLISH_FAIL_MARKER` prefix check. Keep the `PLAYSTORE_SH pending` stale-warning branch, including the `pending=false` plus `track_code=` escape.
3. Keep the return codes: 0 when a live publish-failure warning is found, 1 when `PUBLISH_JOB` is empty, no matching check run exists, or the warning is stale.
4. Do not change `POLL_INTERVAL_SEC`, `rerun_failed_runs`, or the callers at lines 470 and 605.

acceptance: `bash -n .github/scripts/automerge.sh` passes. A dry read of `publish_failed` shows one check-runs list per call, not one jobs list per workflow run. Return codes and log lines (`the $BASE track carries it now`) are unchanged. `./gradlew testDefaultDebugUnitTest` is untouched and must stay green.

size budget: ~20 changed lines, 1 file.

out of scope: do not retry annotations, do not change which job name counts as a publish failure, do not edit the workflow that sets `PUBLISH_JOB`.

---

### 9. stop ImageUtils clear-tests from decoding ole_logo under a spy (roadmap 7+8)

context: Master test run 42782082676 shard 1 recorded `ImageUtilsTest` at 7.0s excluding first-test excess, of which `testLoadImage_ClearsInFlightRequest_WhenEmptyUrl` (`ImageUtilsTest.kt:233`) took 4.6s. That test, and five siblings (lines 149, 169, 190, 215, 256), each call `mockkStatic(Glide::class)` and `spyk(ImageView(context))`. `ImageUtils.loadImage` then calls the real `setImageResource(R.drawable.ole_logo)` on the spy. The class is already Robolectric; the spy-plus-static-mock inside each test is the extra cost. Speeding this class is part of getting shard 1 back under 400s. It does not move roadmap 9 or 10: production `ImageUtils` still takes an `ImageView`.

files: `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt` only. Do not edit `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt`.

steps:
1. Install `mockkStatic(Glide::class)` in `@BeforeClass` and clear it in `@AfterClass`. Remove the per-test `mockkStatic` / `unmockkStatic` pairs in this file.
2. In the six clear-in-flight tests, replace `spyk(ImageView(context))` with `mockk<ImageView>(relaxed = true)`. Stub `context` to the existing Robolectric context and stub `isOpaque` the way the spy tests do today.
3. Keep every `verify` and `verifyOrder`, including `requestManager.clear(...)` before `setImageResource(R.drawable.ole_logo)` for the empty and null URL cases, and the "does not clear" cases.
4. Leave the `@Before` real `ImageView` in place for the tests that still need `shadowOf`.

acceptance: `./gradlew testDefaultDebugUnitTest` green. The empty-URL test still fails if `loadImage` stops clearing the request or stops setting `ole_logo`. It must not decode the drawable to do that.

size budget: ~50 changed lines, 1 file.

out of scope: do not remove `@RunWith(RobolectricTestRunner)`, do not change Glide `override` behavior, do not add a test dependency.

---

### 10. drop the unused TextUtils static mock from StepExamTest (roadmap 7+8)

context: `StepExamTest.setup` (`app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:21-28`) calls `mockkStatic(TextUtils::class)` and stubs `TextUtils.isEmpty` before every test, and `tearDown` calls `unmockkAll()`. `StepExam.kt` has no `TextUtils` import and no `isEmpty` call; its only Android-adjacent call on this path is `addDocumentOrigin()` at `StepExam.kt:108`, which defaults to `NetworkUtils.getUniqueIdentifier()`. On master test run 42782082676 shard 2 this class took 6.6s excluding 0.1s of first-test excess, across 12 tests that are otherwise plain JUnit. Paying for an `android.text` static mock that production no longer uses is a shard tax. Removing it does not make `StepExam` platform-free — that file is owned by an open PR — so this is roadmap 7+8 only.

files: `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt` only. Do not edit `app/src/main/java/org/ole/planet/myplanet/model/StepExam.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/DocumentOrigin.kt`, or `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`.

steps:
1. Delete the `TextUtils` import, the `mockkStatic(TextUtils::class)` call, and the `every { TextUtils.isEmpty(any()) }` stub.
2. Keep `mockkObject(NetworkUtils)` and `every { NetworkUtils.getUniqueIdentifier() } returns "uniqueIdentifier"`. `insertCourseStepsExams` calls `addDocumentOrigin()` with no argument.
3. Keep `unmockkAll()` in `tearDown` so the `NetworkUtils` object mock does not leak into the next class on the shard.
4. Do not add Robolectric, and do not stub `TextUtils` from another test helper.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including `testInsertCourseStepsExams` and `testInsertCourseStepsExams_withParentId`. Those tests still assert the same ids, names, and `isFromNation` flags. The class no longer imports `android.text.TextUtils`.

size budget: ~8 changed lines, 1 file.

out of scope: do not remove the `NetworkUtils` mock, do not change how exams are parsed, do not touch the Room entity.

copilot grok-4.7 high

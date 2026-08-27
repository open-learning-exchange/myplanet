# myPlanet refactor round — work orders

- **date**: 2026-08-27
- **base commit**: `45bac8d` (`origin/master`, "all: smoother importing (fixes #16295) (#16293)")
- **open PRs checked (36)**: 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824,
  15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893,
  14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075
- **method**: every open PR head was fetched and diffed against its own merge-base with
  `origin/master`; the union (319 files) is the off-limits set. None of the files named below appear
  in it. The `ready`-tagged PRs (16274, 16258, 16257, 16192) own `ui/sync/SyncActivity.kt`,
  `ui/resources/Resources{Fragment,Adapter}.kt`, `ui/community/CommunityServicesFragment.kt`,
  `res/layout*/fragment_my_library.xml`, `res/layout/layout_search_pill.xml`,
  `res/values*/strings.xml` and `res/values-night/colors.xml` — no task touches any of them.
- **also off-limits and untouched here**: `app/build.gradle`, `settings.gradle`, `build.gradle.kts`,
  `gradle/libs.versions.toml` (all claimed by open PRs, chiefly 16101's version bump).
- **roadmap key**: 1 data layer · 2 navigation · 3 viewmodel/use-case · 4 DI · 5 sync+upload ·
  6 compose · 7 performance · 8 code health/tests · 9 KMP core · 10 compose multiplatform

---

### 1. give the release workflow a timeout, a concurrency group and a build-dir cache (roadmap 7+8)

context: `.github/workflows/release.yml` is the only workflow in the repo with neither
`timeout-minutes` nor `concurrency` — compare `build.yml:23` (`timeout-minutes: 10`),
`test.yml:23` (15), `labels.yml:33` (5), `automerge.yml:92` (350), and the `concurrency:` blocks at
`build.yml:15`, `test.yml:15`, `labels.yml:21`, `playstore.yml:53`. A hung release job therefore
holds a runner for GitHub's 360-minute default, and two master pushes in quick succession run two
releases at once that race on the same `v${VERSION}` tag and both spend a Play Store save slot.
It also has no `app/build` cache step, so every release compiles cold: in run 33058620684 the
"build release APK and AAB" step took 3m25s (default) and 4m29s (lite) — the whole cost of the
workflow — while `build.yml:37-47` and `test.yml:37-51` already cache `app/build` + `.gradle/*`.

files: `.github/workflows/release.yml` only — the `release:` job header (lines 14-20), the
`checkout repository code`/`setup gradle` steps (lines 29-37) and the top-level `on:` block.
Do NOT touch `.github/workflows/{build,test,playstore,automerge}.yml`, `.github/scripts/playstore*.sh`,
or `app/build.gradle` (an open PR owns the last one).

steps:
1. Add `timeout-minutes: 30` to the `release:` job, directly under `runs-on: ubuntu-24.04` (line 16).
   30 leaves ~6x headroom over the observed 5m30s wall clock.
2. Add a top-level `concurrency:` block after `permissions:` (after line 12) with
   `group: release-${{ github.ref }}` and `cancel-in-progress: false`. It must be `false`: cancelling
   a run mid-way through the "publish AAB to playstore" step (lines 90-114) would leave a half-spent
   save slot and an untagged build.
3. Insert a `restore project build dir` step between `setup gradle` (line 37) and
   `set release version` (line 39), copying the shape of `build.yml:37-47`: `uses: actions/cache@v6`,
   `path:` of `app/build` + `.gradle/*` + `!.gradle/configuration-cache`, and
   `key: build-dir-release-${{ matrix.build }}-${{ github.sha }}` with a single
   `restore-keys:` entry `build-dir-release-${{ matrix.build }}-`. Keep the `release` and
   `${{ matrix.build }}` segments in the key so a debug or cross-flavor entry can never be restored
   into a signed release build.
4. Push, let the master release run, and read the job log.

acceptance: the next `myPlanet release` run on master is green for both matrix legs; its log shows a
`restore project build dir` step and a `Post restore project build dir` save; the GitHub release for
`v<versionName>` still carries all eight assets (`myPlanet.apk`, `myPlanet.aab`, both `.sha256`, and
the four `myPlanet-lite*` counterparts) and the lite `.aab` still reaches the Play Store internal
track with the same `versionCode` as `app/build.gradle`. `./gradlew testDefaultDebugUnitTest` stays
green (unaffected — no Kotlin changes).

size budget: ~20 changed lines, 1 file.

out of scope: do not change the signing action, the Play Store steps, the Discord notification, the
matrix, or `setup-gradle`'s inputs; do not add a remote build cache.

---

### 2. group dependabot updates so one bump is not four CI runners (roadmap 8)

context: `.github/dependabot.yml` declares two daily ecosystems with
`open-pull-requests-limit: 10` (github-actions, line 10) and `15` (gradle, line 19) and **no**
`groups:` key, so every single dependency bump opens its own PR. Each of those PRs fires
`build.yml` (a 2-leg matrix, `build.yml:27-28`) and `test.yml` (a 2-shard matrix, `test.yml:28-29`)
= four runners per bump, and every subsequent rebase re-fires all four. At the configured limits a
quiet day can burn 100 runner-jobs on dependency noise, and each of those PRs also has to be drained
through `automerge.yml` one at a time.

files: `.github/dependabot.yml` only (the two `updates:` entries). Do NOT touch
`.github/workflows/build.yml`, `.github/workflows/test.yml`, `.github/workflows/automerge.yml`, or
`gradle/libs.versions.toml` (an open PR owns the last one).

steps:
1. Under the `github-actions` entry, add a `groups:` block with a single group named `actions`
   whose `patterns: ["*"]`, so all Action bumps land in one PR.
2. Under the `gradle` entry, add a `groups:` block with two groups: `androidx` matching
   `patterns: ["androidx.*", "com.google.dagger*", "com.google.devtools.ksp*"]`, and `gradle-minor`
   matching `patterns: ["*"]` with `update-types: ["minor", "patch"]`. Leaving majors ungrouped keeps
   a breaking Kotlin/AGP bump reviewable on its own.
3. Lower `open-pull-requests-limit` to `5` on both entries — grouping makes the old headroom
   pointless and the limit is the backstop if a group ever fails to form.
4. Commit; no build step is involved.

acceptance: `.github/dependabot.yml` parses (GitHub shows no "Dependabot configuration error" on the
repository's Insights → Dependency graph → Dependabot tab); the next daily run opens at most a
handful of grouped PRs instead of one-per-dependency. `./gradlew testDefaultDebugUnitTest` stays
green (unaffected).

size budget: ~14 changed lines, 1 file.

out of scope: do not change `schedule.interval`, the `commit-message` prefixes (`actions:` / `all:`
feed the repo's commit convention), or the `directories` lists; do not add `ignore` rules.

---

### 3. stop the labels workflow from checking out the whole repository (roadmap 8)

context: `.github/workflows/labels.yml:41-44` does a full `actions/checkout@v7` of the repository on
every `pull_request_target` event — `opened`, `synchronize`, `reopened`, `ready_for_review` — but the
job's only other step (lines 45-49) runs `.github/scripts/labels.sh`, which reads the diff through
the GitHub API and never touches a checked-out file. The workflow therefore clones ~500 Kotlin
sources, 181 layouts and six translation bundles to run one shell script, on every push to every open
PR. The `chmod +x` at line 48 is also dead: `git ls-files -s .github/scripts/labels.sh` reports mode
`100755`, so the file is already executable when checked out.

files: `.github/workflows/labels.yml` — the `checkout repository code` step (lines 41-44) and the
`label the pull request by size` step (lines 45-49). Do NOT edit `.github/scripts/labels.sh` itself
(task 4 owns it) and do not touch the `env:`/`concurrency:` blocks at lines 21-28.

steps:
1. Add a `sparse-checkout: .github/scripts` input (plus `sparse-checkout-cone-mode: false`) to the
   `actions/checkout@v7` step, keeping `fetch-depth: 1`.
2. Delete the `chmod +x .github/scripts/labels.sh` line from the run block, leaving
   `.github/scripts/labels.sh` as the only command.
3. Trigger the workflow by hand against an existing open PR using its `workflow_dispatch` input
   (`pr:` = the PR number, `dry_run: true`) and confirm the log reaches the "#N is +a/-d over N
   file(s)" line from `labels.sh:100`.
4. Re-run once with `dry_run: false` on a PR whose size label is already correct and confirm it
   reports "already labelled … -- nothing to write" (`labels.sh:124`) rather than failing.

acceptance: the dispatched run is green and its checkout step reports only the `.github/scripts`
path; a fresh push to any open PR still gets exactly one of `small`/`medium`/`large`/`enormous`, with
`less` added when the diff has zero additions. `./gradlew testDefaultDebugUnitTest` stays green
(unaffected).

size budget: ~4 changed lines, 1 file.

out of scope: do not change the trigger types, the `EXCLUDE_PATHS`/`GRADLE_FILE` env values, or the
`permissions:` block; do not convert the job to a `pull_request` trigger (it would lose write access
on fork and Dependabot PRs).

---

### 4. stop the size labeller from downloading every file's patch (roadmap 8)

context: `.github/scripts/labels.sh:45` calls
`gh api "repos/$REPO/pulls/$PR/files?per_page=100" --paginate`, which returns the **full unified
patch** for every changed file, and line 46 immediately throws all of it away except for one file:
the base64 patch is only read at lines 65-75, and only when `$path` equals `$GRADLE_FILE`
(`app/build.gradle`). On a large PR that is megabytes of JSON fetched and base64-encoded per event,
on a workflow that fires on every push to every open PR. GitHub also omits `patch` entirely once a
diff is big enough, so the version-bump discount is already unreliable on exactly the PRs where the
payload hurts most.

files: `.github/scripts/labels.sh` — the `read_files` function (lines 41-47) and the loop body's
gradle-file branch (lines 65-76). Leave `version_lines` (49-51), the label arithmetic (82-121) and
the retry loop (139-148) untouched, and do NOT edit `.github/workflows/labels.yml` (task 3 owns it).

steps:
1. Rewrite `read_files` to fetch filename/additions/deletions only, without patches: query the same
   endpoint with `--jq` reducing each element to `[.filename, .additions, .deletions] | @tsv`, or
   swap in `gh pr view "$PR" --repo "$REPO" --json files --jq '.files[] | [.path, .additions, .deletions] | @tsv'`.
   Emit three TSV columns instead of four. Keep honouring the `FILES_JSON` override so existing
   fixtures still work — when `FILES_JSON` is set, project the same three columns from it.
2. Change the `while IFS=$'\t' read -r` header at line 59 to read three fields (drop `patch64`).
3. In the `$GRADLE_FILE` branch, fetch that one file's patch on demand instead of relying on the
   bulk payload: a single `gh api "repos/$REPO/pulls/$PR/files?per_page=100" --paginate --jq
   'map(select(.filename == "'"$GRADLE_FILE"'")) | .[0].patch // ""'` guarded so a failure yields an
   empty patch and simply skips the discount. Feed the result to the existing `version_lines`.
4. Run the script locally against a real PR to check both paths:
   `REPO=open-learning-exchange/myplanet PR=16192 DRY_RUN=true .github/scripts/labels.sh` (no gradle
   file in the diff) and `PR=16101` (gradle file present, version bump discounted).

acceptance: both local `DRY_RUN=true` invocations exit 0 and print the same
"#N is +a/-d over N file(s) -- T line(s), <label>" verdict as the current script does for those PRs;
for 16101 the log still shows the "discounting the version bump in app/build.gradle" line.
`./gradlew testDefaultDebugUnitTest` stays green (unaffected).

size budget: ~25 changed lines, 1 file.

out of scope: do not change the size thresholds, the `less` rule, the `EXCLUDE_PATHS` matching, or
the label names; do not add a new runtime dependency beyond `gh` and `jq`, which the script already
requires.

---

### 5. serialise the automerge drain and stop it cloning every blob in history (roadmap 8)

context: `.github/workflows/automerge.yml` has no `concurrency:` block, unlike `playstore.yml:53-55`
(`group: myPlanet-playstore`, `cancel-in-progress: false`). Two `workflow_dispatch` clicks — easy to
do, since the drain is started by hand — put two jobs on the same queue, both merging the base in,
bumping the version via `version.sh` and pushing to protected `master`; the second will bump to a
`versionCode` the first already claimed. Separately, `automerge.yml:97` uses `fetch-depth: 0`, which
downloads every blob of every commit in a repository with 3000+ releases; the script only ever needs
the commit graph plus the blobs of the files it merges (`automerge.sh:483` `git show
"origin/$BASE:$GRADLE_FILE"`, and the `git merge`/`git checkout` calls at 490-509).

files: `.github/workflows/automerge.yml` — the top-level block after `permissions:` (lines 77-83)
and the `checkout repository code` step (lines 94-98). Do NOT edit `.github/scripts/automerge.sh`,
`.github/scripts/version.sh`, or `.github/workflows/playstore.yml`.

steps:
1. Add a top-level `concurrency:` block after the `permissions:` block with
   `group: myPlanet-automerge` and `cancel-in-progress: false`, matching how `playstore.yml` guards
   its own single-flight section. `false` is required: cancelling mid-drain can leave a PR pushed and
   version-bumped but unmerged.
2. Add `filter: blob:none` to the `actions/checkout@v7` step, keeping `fetch-depth: 0` and the
   existing `token:` input. This keeps the full commit graph the script walks while deferring blob
   downloads to the handful of files a merge actually reads.
3. Dispatch the workflow with `dry_run: true` (its default) and read the log: the drain must still
   enumerate the labelled PRs, report each one's mergeability, and compute the next version.
4. Dispatch a second run while the first is still going and confirm GitHub queues it behind the
   first instead of running both.

acceptance: the `dry_run: true` dispatch is green and its log still shows the per-PR
"prepared commit gets its own build and test run." / mergeability lines from `automerge.sh`; a
concurrent second dispatch shows as *pending* rather than *in progress*; one subsequent real drain
(`dry_run: false`) squash-merges a labelled PR and pushes exactly one version bump.
`./gradlew testDefaultDebugUnitTest` stays green (unaffected).

size budget: ~5 changed lines, 1 file.

out of scope: do not change any `workflow_dispatch` input, its defaults, the `timeout-minutes: 350`,
the script-staging step (lines 100-112), or the `env:` wiring at lines 116-137.

---

### 6. size the OkHttp connection pool to the dispatcher's per-host limit (roadmap 5+7)

context: `NetworkModule.buildOkHttpClient` (`di/NetworkModule.kt:71-87`) raises
`Dispatcher.maxRequestsPerHost` to `MAX_REQUESTS_PER_HOST = 20` (lines 69, 72-74) but leaves the
`ConnectionPool` at OkHttp's default of 5 idle connections. Every sync and every batch download in
this app targets a single CouchDB host, so during a pull 20 requests run concurrently against that
host and at most 5 of the resulting connections survive to be reused — the other 15 are closed and
re-established, each paying a fresh TCP + TLS handshake. On the slow, high-latency links this app is
built for, that handshake cost dominates. This is a one-client change: `provideStandardOkHttpClient`
(lines 89-99) is the only caller, and it is `@Singleton`.

files: `app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt` — `buildOkHttpClient` (lines
71-87) and the `MAX_REQUESTS_PER_HOST` constant (line 69). Do NOT touch `provideGson`/
`providePlainGson` (lines 53-67), the `TaggedSocketFactory` (lines 25-32), the qualifiers
(lines 34-44), or `data/api/ApiClient.kt` / `data/api/RetryInterceptor.kt`.

steps:
1. Import `okhttp3.ConnectionPool` (OkHttp 5.4.0 is already on the classpath — no new dependency).
2. Add a `KEEP_ALIVE_MINUTES = 5L` constant next to `MAX_REQUESTS_PER_HOST`, matching OkHttp's own
   default keep-alive so only the pool *size* changes.
3. In `buildOkHttpClient`, chain
   `.connectionPool(ConnectionPool(MAX_REQUESTS_PER_HOST, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))`
   onto the builder, next to the existing `.dispatcher(dispatcher)` call, so the idle-connection
   ceiling and the per-host request ceiling are derived from the same constant.
4. Run the unit suite and confirm the DI graph still builds.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: log in against a Planet server and run a full manual sync — it completes
with the same tables and record counts as before, and no new socket/timeout errors appear in logcat.

size budget: ~5 changed lines, 1 file.

out of scope: do not change the connect/read/write timeouts (lines 49-51), do not add a response
cache, an interceptor, or HTTP/2 tuning, and do not introduce a second `OkHttpClient` flavour.

---

### 7. let WorkManager wait for the network instead of parking a worker on it (roadmap 5+7)

context: `NetworkMonitorWorker.start` (`services/NetworkMonitorWorker.kt:28-35`) enqueues a
`OneTimeWorkRequestBuilder<NetworkMonitorWorker>()` with **no constraints**, and `doWork`
(lines 38-47) then blocks on `NetworkUtils.isNetworkConnectedFlow.first { it }` (line 40) until the
device has connectivity. On an offline-first app whose users are frequently offline for long
stretches, that worker occupies a WorkManager execution slot and its wake lock until it hits the
10-minute execution limit, is stopped without ever returning a `Result`, and gets rescheduled — a
loop that repeats for as long as the device stays offline. WorkManager already offers exactly this
wait for free: `NetworkType.CONNECTED` is enforced by the OS and costs nothing while unmet. It is the
pattern this codebase already uses in `MainApplication.scheduleAutoSyncWork`
(`MainApplication.kt:462-464`).

files: `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt` — the `start`
companion function (lines 28-35) and `doWork` (lines 38-47). Leave `scheduleServerReachabilityCheck`
(lines 49-66) exactly as it is. Do NOT touch `MainApplication.kt`, `ServerReachabilityWorker`, or
`utils/NetworkUtils.kt`.

steps:
1. In `start`, build a `Constraints` object with `.setRequiredNetworkType(NetworkType.CONNECTED)` and
   attach it to the request via `.setConstraints(constraints)`; import `androidx.work.Constraints`
   and `androidx.work.NetworkType`.
2. Keep `addTag(WORK_TAG)` and the `enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.KEEP, …)` call
   unchanged so an already-enqueued monitor is still not duplicated.
3. Leave the `isNetworkConnectedFlow.first { it }` await in `doWork` in place — with the constraint
   satisfied it returns immediately, and it still guards the case where connectivity drops between
   the constraint being met and the worker starting.
4. Add a unit test at `app/src/test/java/org/ole/planet/myplanet/services/NetworkMonitorWorkerTest.kt`
   that builds the request the same way `start` does and asserts
   `workRequest.workSpec.constraints.requiredNetworkType == NetworkType.CONNECTED`. Follow the
   existing `services/FreeSpaceWorkerTest.kt` for the Robolectric + WorkManager setup this module
   already uses.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including the new
`NetworkMonitorWorkerTest`. Behaviour to verify by hand: put the device in airplane mode and cold
start the app — `adb shell dumpsys jobscheduler` shows the `network_monitor_work` job *pending on
connectivity* rather than repeatedly running; turn the network back on and the
`server_reachability_work` follow-up is still enqueued ~30s later (`UPLOAD_DELAY_SECONDS`, line 26).

size budget: ~10 changed lines in the worker plus a ~35-line new test, 2 files.

out of scope: do not convert the worker to periodic, do not change `UPLOAD_DELAY_SECONDS` or the
`ExistingWorkPolicy.REPLACE` on the reachability follow-up, and do not replace `isNetworkConnectedFlow`
with a `ConnectivityManager` callback.

---

### 8. move the inline-resource preview's file stats off the main thread (roadmap 7)

context: `InlineResourceAdapter` runs its preview coroutines on the **main** dispatcher —
`adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)`
(`ui/courses/InlineResourceAdapter.kt:56`, repeated at line 61) — and every preview path then does
blocking filesystem stats on that thread: `file.exists()` at lines 203, 217, 227, 242, 254 and 269,
plus `getCacheKey` (line 283), which adds a `lastModified()` and a `length()` call. Lines 203 and 217
are worse still: `showImagePreview`/`showVideoPreview` are invoked straight from
`onBindViewHolder` (lines 180-181), so a course with a screenful of downloaded resources performs
several `stat()` syscalls per bind while the list is being flung.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt` —
`showImagePreview` (202-213), `showVideoPreview` (215-224), `showPdfPreview` (226-239),
`showAudioPreview` (241-252), `showCsvPreview` (253-266), `showTextPreview` (268-281) and
`getCacheKey` (283). Leave `onCurrentListChanged` (79-92), the payload diffing (44-52), the
`textCache` map (54) and the `ViewHolder` job plumbing (65-77) alone. Do NOT touch
`utils/ResourcesPreviewLoader.kt`, `utils/PdfThumbnailLoader.kt` or `utils/ResourceOpener.kt`.

steps:
1. In the four `suspend` preview functions, replace the bare `if (!file.exists()) return` guard and
   the `getCacheKey(file)` call with a single `withContext(dispatcherProvider.io) { … }` block that
   returns both the existence flag and the cache key (a small private
   `suspend fun statFor(file: File): String?` returning `null` when the file is missing reads best).
   Bail out on `null` before touching any view. Import `kotlinx.coroutines.withContext`.
2. In `showImagePreview` and `showVideoPreview` — which are not suspend and run on the bind path —
   delete the `file.exists()` guards entirely and let Glide handle a missing file: the image path
   already declares `.error(R.drawable.ole_logo)` (line 209). Give the video path the same
   `.error(R.drawable.ole_logo)` so a missing thumbnail degrades the same way.
3. Keep `getCacheKey`'s format string exactly as it is so previously cached entries in `textCache`
   still match.
4. Run the unit suite, then scroll a course detail screen that has several downloaded resources.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: open a course whose steps contain downloaded PDF, audio, CSV, text,
image and video resources — every preview still renders (audio duration, CSV/text snippet, PDF first
page, image and video thumbnails), and repeated scrolling still reads the cached text previews rather
than re-parsing them.

size budget: ~30 changed lines, 1 file.

out of scope: do not change `adapterScope`'s dispatcher itself (view work must stay on main), do not
alter the DiffUtil payloads or `onViewRecycled`, and do not add a disk cache or change
`PDF_PREVIEW_WIDTH_DP`.

---

### 9. delete the six dead Hilt entry-point accessors (roadmap 4+9)

context: `di/ServiceDependenciesEntryPoint.kt:43-50` declares six accessors, but only
`broadcastService()` (used by the `getBroadcastService` helper in the same file, line 57) and
`retryQueue()` (`MainApplication.kt:394`) have a call site — `apiInterface()`, `syncManager()`,
`uploadManager()` and `uploadToShelfService()` have none anywhere in `app/src/main` or
`app/src/test`. `di/CoreDependenciesEntryPoint.kt:17-26` has the same problem: `userSessionManager()`
and `resourcesRepository()` are never called, while `applicationScope()`, `sharedPrefManager()`,
`serverUrlMapper()`, `dispatcherProvider()`, `diagnosticsRepository()` and `timeProvider()` all are.
Each dead accessor is a live edge in the Dagger graph — it makes the entry point a root for
`UploadManager`, `SyncManager` and `UploadToShelfService`, which is exactly the coupling the DI
cleanup is meant to remove, and it obscures which services genuinely need the worker escape hatch.

files: `app/src/main/java/org/ole/planet/myplanet/di/ServiceDependenciesEntryPoint.kt` and
`app/src/main/java/org/ole/planet/myplanet/di/CoreDependenciesEntryPoint.kt`. Do NOT touch
`MainApplication.kt`, `utils/NetworkUtils.kt` or `utils/NotificationUtils.kt` — they are the three
`EntryPointAccessors.fromApplication` call sites and every accessor they use is being kept.

steps:
1. In `ServiceDependenciesEntryPoint.kt`, delete the `apiInterface()`, `syncManager()`,
   `uploadManager()` and `uploadToShelfService()` declarations, then remove the now-unused imports
   for `ApiInterface`, `SyncManager`, `UploadManager` and `UploadToShelfService` (lines 34, 36, 37, 39).
   Keep `broadcastService()`, the `getBroadcastService` helper and its `Context`/`EntryPointAccessors`
   imports.
2. In `CoreDependenciesEntryPoint.kt`, delete the `userSessionManager()` and `resourcesRepository()`
   declarations and the `UserSessionManager` / `ResourcesRepository` imports (lines 8, 10).
3. Before committing, re-run the search that justified this — grep `app/src` for each removed name
   followed by `()` and confirm the only hits left are in the two edited files (there should be none).
4. Build both flavours so KSP regenerates the Hilt components.

acceptance: `./gradlew testDefaultDebugUnitTest` green — in particular
`app/src/test/java/org/ole/planet/myplanet/MainApplicationTest.kt`, which mocks `serverUrlMapper()`
and must keep compiling. `./gradlew assembleDefaultDebug` and `./gradlew assembleLiteDebug` both
green (a wrongly removed accessor shows up as a Hilt/KSP compile error, not a runtime failure).
Behaviour to verify by hand: cold start the app, complete a sync, and confirm the retry-queue
recovery in `MainApplication` and the broadcast service still work.

size budget: ~12 deleted lines, 2 files.

out of scope: do not add accessors, do not convert any of these consumers to constructor injection,
and do not touch `RepositoryModule`, `ServiceModule` or `RoomModule` (open PRs own the last one).

---

### 10. give the duplicated `getUserInfo` one platform-free home in `UrlUtils` (roadmap 1+9)

context: the same credential parser exists twice.
`ProcessUserDataActivity.getUserInfo` (`ui/sync/ProcessUserDataActivity.kt:239-248`) splits
`uri.userInfo` with `":".toRegex()` — compiling a `Regex` to split on a single literal character —
while `ServerUrlMapper.getUserInfo` (`services/sync/ServerUrlMapper.kt:111-121`) is a private copy of
the same logic using a plain string split. Both return an `Array<String>` of two elements, a Java
conversion leftover. Worse for the KMP goal: `ServerConfigUtils.kt:99` — a plain util — reaches up
into an **Activity companion object** to call `ProcessUserDataActivity.getUserInfo(uri)`, so a util
that has no business knowing about Android UI transitively depends on an `AppCompatActivity`.
Parking one copy in `UrlUtils` that takes a `String?` rather than a `Uri` removes the duplication,
drops the regex, and leaves the helper with zero `android.*` references.

files: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` (add the helper),
`app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt` (companion at 238-249,
call site at 127), `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`
(private function at 111-121, call site at 60), and
`app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt` (call site at 99). Do NOT
touch `utils/AuthUtils.kt`, `services/SharedPrefManager.kt`, or `UrlUtils`' existing
`header`/`basicAuthHeader`/`hostUrl` members.

steps:
1. Add `fun credentialsFrom(userInfo: String?): Pair<String, String>` to `UrlUtils`: split on the
   literal `":"`, and return `parts[0] to parts[1]` when there are at least two non-trailing-empty
   parts, otherwise `"" to ""` — matching today's behaviour for both a null `userInfo` and a
   single-part one. Use no `Regex` and add no `android.*` import.
2. In `ProcessUserDataActivity`, delete the whole `getUserInfo` companion function and change line
   127 to `val (urlUser2, urlPwd2) = UrlUtils.credentialsFrom(uri.userInfo)`, assigning into the
   existing `urlUser`/`urlPwd` vals. Drop the `Uri` import only if nothing else in the file uses it.
3. In `ServerUrlMapper`, delete the private `getUserInfo` and destructure
   `UrlUtils.credentialsFrom(altUri.userInfo)` at line 60 into the existing `urlUser`/`urlPwd` vals.
4. In `ServerConfigUtils.saveAlternativeUrl`, replace the `ProcessUserDataActivity.getUserInfo(uri)`
   call at line 99 with `UrlUtils.credentialsFrom(uri.userInfo)` and destructure it into the existing
   `Triple(...)`; remove the now-unused `ProcessUserDataActivity` import.
5. Extend `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt` with cases for
   `"user:pass"`, `"user"`, `""` and `null`, asserting the pair the old `Array<String>` contract
   produced.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including the extended `UrlUtilsTest` and the
existing `services/sync/ServerUrlMapperTest.kt`. `./gradlew assembleDefaultDebug` green. Behaviour to
verify by hand: sign in against a server URL written as
`https://user:pin@host:port` — login succeeds, and the alternative-URL path (a server with a clone
URL configured) still resolves the same credentials.

size budget: ~35 changed lines across 4 source files plus ~20 test lines, 5 files.

out of scope: do not change the `"satellite"` fallback branches, the `couchdbURL` string building, or
`ServerUrlMapper`'s reachability logic; do not move `UrlUtils`' `SharedPrefManager` singleton or any
other member in this change.

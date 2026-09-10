# myPlanet refactor round — performance quick wins

- **date**: 2026-09-10
- **base commit**: `022ee7d` (`all: smoother importing (fixes #16991) (#16990)`)
- **open PRs checked**: 16989, 16988, 16987, 16986, 16690, 16624, 16623, 16594, 16270, 16101, 15951, 15825, 15824, 15820, 15808, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075 (all 38 open PRs; every branch was fetched and diffed against `origin/master`, and the union of the files they touch is off-limits below)
- **off-limits, do not touch in any task**: everything the PRs above modify. Most notably `TeamsRepository{,Impl}.kt`, `CoursesRepository{,Impl}.kt`, `SubmissionsRepository{,Impl}.kt`, `HealthRepository{,Impl}.kt`, `VoicesRepository{,Impl}.kt`, `EventsRepository{,Impl}.kt`, `data/room/AppDatabase.kt`, `di/RoomModule.kt`, `di/RepositoryModule.kt`, `di/ServiceModule.kt`, `services/sync/TransactionSyncManager.kt`, `services/UploadManager.kt`, `MainApplication.kt`, `base/BasePermissionActivity.kt`, `base/BaseRecyclerFragment.kt`, `base/BaseDashboardFragment.kt`, every `values*/strings.xml`, and 20 named DAOs (`NewsDao`, `CourseDao`, `RatingDao`, `SubmissionDao`, `TeamTaskDao`, `MeetupDao`, `CourseProgressDao`, `ChatDao`, `ExamDao`, `FeedbackDao`, `HealthExaminationDao`, `AchievementDao`, `CertificationDao`, `TagDao`, `OfflineActivityDao`, `SyncCursorDao`, …).
- **every path, class and function cited below was opened and confirmed on `022ee7d`.** No file appears in two tasks.
- baseline for every task: `./gradlew testDefaultDebugUnitTest` is green on `022ee7d` and must stay green.

---

### 1. stop double-querying team transactions on every finances screen entry (roadmap 3+7)

context: `EnterprisesFinancesFragment.onViewCreated` calls `observeTransactions()` at line 273 and `onResume` calls the identical `observeTransactions()` again at line 356 with the same `teamId`/sort/date arguments. `EnterprisesFinancesViewModel.getTeamTransactions` (line 71) unconditionally does `transactionsJob?.cancel()` and relaunches the Room `Flow` collection, so first display cancels the in-flight query and re-runs `getTeamTransactionsWithBalance` from scratch — two full transaction queries plus two balance passes per screen entry, and one more on every return from background.

files: `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesFragment.kt` (lines 273, 354-356, and `observeTransactions` at 371-381), `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModel.kt` (`getTeamTransactions`, lines 71-84). Tests to update: `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModelTest.kt`. Do NOT touch `EnterprisesFinancesAdapter.kt` or `EnterprisesReportsAdapter.kt` — task 3 owns them; do NOT touch `TeamsFinancesRepository` or any repository.

steps:
1. In `EnterprisesFinancesViewModel`, add a private field holding the last-requested `(teamId, sortAscending, startDate, endDate)` tuple.
2. At the top of `getTeamTransactions`, return early when the incoming arguments equal the stored tuple **and** `transactionsJob?.isActive == true`; otherwise store the new tuple and proceed exactly as today.
3. Leave the `onPause`/`resetFilterAndSort` path alone — it changes `headerState`, so the next `observeTransactions()` legitimately passes different arguments and must still re-query.
4. Add a test to `EnterprisesFinancesViewModelTest` asserting that two back-to-back `getTeamTransactions` calls with identical arguments subscribe to the repository flow once, and that a call with a changed `sortAscending` re-subscribes.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open a team → Finances, background and foreground the app, and confirm the transaction list, the debit/credit/total header and the caution icon are unchanged, then apply a date filter and toggle the sort arrow and confirm both still re-query and re-render.

size budget: ~35 changed lines across 3 files (2 production + 1 test).

out of scope: no repository or DAO changes; do not remove the `onResume` hook (it is the recovery path when the flow was cancelled while stopped) — only make the duplicate call cheap.

---

### 2. stop double-loading team resources on every screen entry (roadmap 3+7, moves 9)

context: `TeamResourcesFragment.onViewCreated` calls `showLibraryList()` (line 49) and `onResume` calls it again (line 63); `showLibraryList` (line 72) forwards to `TeamResourcesViewModel.loadResources` (line 31), which launches an unguarded `viewModelScope.launch` running `getTeamResources(teamId)` **and** `isTeamLeader(teamId, userId)` concurrently. Entering the tab therefore issues four queries instead of two, and because nothing cancels the earlier launch, two independent jobs race to write `_uiState`, so the adapter can be submitted a stale list.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesFragment.kt` (lines 49, 61-64, 72-74), `app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesViewModel.kt` (`loadResources`, lines 31-42, plus the `android.util.Log` import at line 3). Tests: `app/src/test/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesViewModelTest.kt`. Do NOT touch `TeamsRepository`/`TeamsRepositoryImpl` (open PRs own them) or `TeamResourcesAdapter.kt`.

steps:
1. In `TeamResourcesViewModel`, hold the load in a `private var loadJob: Job?` and `cancel()` it at the start of `loadResources` so only one load can write `_uiState`.
2. Guard against the redundant repeat: skip the relaunch when the requested `(teamId, userId)` matches the last request and `loadJob?.isActive == true`.
3. Replace the `android.util.Log` usage in `recordActivitySafely` with a plain `runCatching { … }` that swallows the failure, and drop the `android.util.Log` import — this is the only `android.*` dependency in the class, so removing it makes the ViewModel platform-free.
4. Keep the explicit `showLibraryList()` calls after add/remove (lines 126, 150) — those must still force a refresh, so route them through a parameter or a dedicated `reload()` that bypasses the dedup guard.
5. Extend `TeamResourcesViewModelTest` with a case asserting one repository round-trip for two identical `loadResources` calls and a fresh round-trip after `reload()`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open a team → Resources, background/foreground the app, then add a resource via the FAB and remove one, confirming the grid, the empty-state text and the remove affordance (leader only) all still update immediately.

size budget: ~50 changed lines across 3 files (2 production + 1 test).

out of scope: no repository, DAO or adapter changes; do not convert the ViewModel to a `Flow`-based repository subscription in this task.

---

### 3. share one attachment-presence cache between the two enterprise adapters (roadmap 7+8)

context: `EnterprisesFinancesAdapter` (lines 33-42, 72-86) and `EnterprisesReportsAdapter` (lines 27-36, 108-122) each carry a byte-identical hand-rolled `HashMap<String, Pair<Boolean, Long>>` + `cacheTtlMs = 5000L` TTL cache around `File.exists()`, and each wipes the whole cache in `onCurrentListChanged`. Because both screens call `submitList` on every filter, sort and realtime-sync tick, that blanket clear throws away a still-valid cache and forces a synchronous `File.exists()` disk hit for every visible row on the binding thread.

files: `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`, new file `app/src/main/java/org/ole/planet/myplanet/utils/AttachmentPresenceCache.kt`. Tests: `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapterTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapterTest.kt`. Do NOT touch `model/MyTeam.kt` (`getAttachmentFile` stays as it is — an open PR owns that file) or `EnterprisesFinancesFragment.kt`/`EnterprisesFinancesViewModel.kt` (task 1).

steps:
1. Add `AttachmentPresenceCache` in `utils/`: a small class taking a `TimeProvider` and a TTL, exposing one `fun exists(file: File?): Boolean` that returns `false` for `null`, serves a non-expired entry, and otherwise calls `file.exists()` and stores `(result, now)`.
2. Replace the cache field and the inline TTL block in `EnterprisesFinancesAdapter.bindFinanceImage` with a single `presenceCache.exists(imageFile)` call.
3. Do the same in `EnterprisesReportsAdapter.bindReportImage`.
4. Delete both `onCurrentListChanged` overrides that call `attachmentExistsCache.clear()` — the TTL already bounds staleness, so a list update no longer discards live entries.
5. Add a unit test for `AttachmentPresenceCache` (hit inside the TTL does not re-stat, expiry re-stats) using the existing `TimeProvider` fake pattern, and keep both adapter tests passing unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open a team → Finances and → Reports, scroll a list that has attachment photos, apply a date filter, and confirm thumbnails still appear for rows with images, stay hidden for rows without, and remain tappable into the zoomable viewer.

size budget: ~90 changed lines across 5 files (2 modified adapters + 1 new util + 2 test files).

out of scope: do not move the presence check off the binding thread or introduce a coroutine here; do not change how attachment files are named or resolved.

---

### 4. hoist the per-bind click listener out of the courses-progress adapter (roadmap 7+8)

context: `CoursesProgressAdapter.onBindViewHolder` (lines 28-40) allocates a fresh `holder.itemView.setOnClickListener { … }` on every bind, and it does so **only inside** the `if (item.progressCurrent != null && item.progressMax != null)` branch (line 33). A recycled holder that lands on a row without progress values therefore keeps the previous row's listener, so tapping it opens `CourseProgressActivity` for the wrong `courseId`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt` (`onBindViewHolder` lines 28-40, `CoursesProgressViewHolder` at line 99, `onCreateViewHolder` at line 23). Tests: `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapterTest.kt`. Do NOT touch `CourseProgressActivity.kt` (an open PR owns it), `ProgressGridAdapter.kt` or `CoursesProgressFragment.kt`.

steps:
1. Move the click handling into `CoursesProgressViewHolder`'s `init`, resolving the row through `bindingAdapterPosition` and bailing out on `RecyclerView.NO_POSITION`.
2. Inside that listener, start `CourseProgressActivity` only when the resolved item has non-null `progressCurrent` and `progressMax`; otherwise do nothing — this preserves today's intended behavior without leaving a stale listener behind.
3. Remove the `setOnClickListener` call from `onBindViewHolder` so binds no longer allocate a lambda.
4. Add a test asserting that a holder bound to a progress row and then rebound to a no-progress row does not launch the activity.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open My Courses → Progress, scroll a list mixing courses with and without recorded progress, and confirm tapping a progress row opens that same course's progress screen while tapping a no-progress row does nothing.

size budget: ~30 changed lines across 2 files.

out of scope: do not restructure `showStepMistakes` or the `llProgress` child-recycling logic; no layout changes.

---

### 5. stop re-running the patient query on every health screen resume (roadmap 3+7)

context: `MyHealthFragment.onViewCreated` reaches `setupInitialData()` (line 117 → line 238) which calls `viewModel.loadInitialPatient()`; `HealthViewModel.loadInitialPatient` (line 76) resolves the current user and calls `selectPatient(normalizedId)` (line 83). `MyHealthFragment.onResume` (line 327) then calls `viewModel.selectPatient(normalizedId)` again for the same id, and `selectPatient` (line 88) cancels the in-flight job and redoes both `getPatientById` and `getPatientHealthRecords` — the second of which decrypts the stored health record. Every screen entry and every foreground pays for that twice.

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` (lines 117, 238-240, 327-334), `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt` (`loadInitialPatient` 76-86, `selectPatient` 88-101). Tests: `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt`. Do NOT touch `HealthRepository`/`HealthRepositoryImpl` (open PRs own them), `HealthViewModel.loadHealthData`, `HealthExaminationViewModel.kt` or `HealthExaminationAdapter.kt`.

steps:
1. In `HealthViewModel`, track the id of the patient currently loaded (or loading) in a private field set at the top of `selectPatient`.
2. Make `selectPatient` return early when the requested id equals that field **and** either the job is still active or `_patientDetailState.value` already holds a record for it; clear the field when a load fails so a retry still works.
3. Add an explicit `refreshSelectedPatient()` for the paths that must force a reload, and point the realtime-sync/refresh callers in `MyHealthFragment` (the `refreshHealthData` path at line 86 and the post-edit path at line 277) at it.
4. Extend `HealthViewModelTest` with a case asserting `loadInitialPatient()` followed by `selectPatient(sameId)` performs one `getPatientHealthRecords` call, and that `refreshSelectedPatient()` performs another.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open My Health, background/foreground the app, then edit the personal details and confirm the vital-sign card, the DOB/name fields and the examination list all refresh with the edited values.

size budget: ~45 changed lines across 3 files (2 production + 1 test).

out of scope: no repository or encryption changes; do not touch the patient-search or patient-list flows (`loadPatients`, `searchPatients`).

---

### 6. batch the crash-log upload acknowledgement into one transaction (roadmap 5+1)

context: `UploadConfigs.CrashLog.markUploaded` (line 223) is `results.filter { !diagnosticsRepository.markApkLogUploaded(result.localId, result.remoteRev) }` — one suspend call per uploaded row, each reaching `ApkLogDao.markUploaded` (line 23), a bare `UPDATE` that Room runs in its own implicit transaction. A sync that ships a backlog of crash logs therefore performs N separate committed writes where one batched write would do. `SubmitPhotosDao` already has the exact pattern to copy (`markUploadedBatchInternal` + `@Transaction markUploadedBatch`, `data/room/dao/SubmitPhotosDao.kt` lines 26-39), and `submissionsRepository.markPhotosUploadedBatch` shows the repository-side shape.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepository.kt` (line 8), `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` (line 22), `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` (the `CrashLog` config, lines 215-227). Tests: `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt`. Do NOT touch `model/ApkLog.kt`, `AppDatabase.kt` (no schema change, so no `version` bump) or any other config in `UploadConfigs.kt` — `Feedback` and `SubmitPhotos` keep their current per-row marking.

steps:
1. In `ApkLogDao`, add an `@Update(entity = ApkLog::class)` partial-update method over a small `data class` carrying `@ColumnInfo("id")` and `@ColumnInfo("_rev")`, plus a `@Transaction suspend fun markUploadedBatch(...)` that maps and delegates — mirroring `SubmitPhotosDao`. Keep the existing single-row `markUploaded` for callers that still need it.
2. Add `suspend fun markApkLogsUploaded(updates: List<…>): Set<String>` to `DiagnosticsRepository`, returning the ids that were **not** applied.
3. Implement it in `DiagnosticsRepositoryImpl`: read back the affected ids with a single query after the batch update and return the difference, so the caller keeps today's "row was gone ⇒ report a local failure" semantics.
4. Rewrite `UploadConfigs.CrashLog.markUploaded` to make one batched call and return `results.filter { it.localId in notApplied }`.
5. Update both tests: assert the DAO batch is invoked once for a multi-row result, and that a missing row is still reported back as a failed item.

acceptance: `./gradlew testDefaultDebugUnitTest` green; trigger a sync on a device with pending crash logs and confirm the logs are uploaded once, disappear from the pending set (`apk_log` rows gain a `_rev`), and are not re-uploaded on the next sync.

size budget: ~90 changed lines across 6 files (4 production + 2 test).

out of scope: no `AppDatabase` version bump and no entity/index change; do not batch the other upload configs in this task.

---

### 7. evaluate the SyncPerf log gate once per sync instead of per event (roadmap 5+7)

context: `Log.isLoggable("SyncPerf", Log.DEBUG)` is called at `utils/SyncTimeLogger.kt` lines 71, 86, 144, 164, 181, 192 and `services/sync/SyncManager.kt` lines 77, 145, 215, 224. `SyncTimeLogger.logApiCall` (154), `logDbOperation` (172) and `logDetail` (187) run per API call and per DB write during a sync, and `SyncManager.syncPerf` (76-80) is the inline gate used throughout the sync loop — so a single sync does a system-property lookup thousands of times, and the `"SyncPerf"` tag literal is duplicated in ten places.

files: `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` (the six gates plus `startLogging` at line 61), `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` (`syncPerf` at 76-80 and the three inline gates at 145, 215, 224). Tests: `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt`. Do NOT touch `TransactionSyncManager.kt`, `HeavyTableSyncWorker.kt` or `LoginSyncManager.kt` — open PRs own the first two.

steps:
1. Add a `const val TAG = "SyncPerf"` (or reuse the existing companion tag) in `SyncTimeLogger` and an `internal val isVerbose: Boolean` backed by a field that `startLogging()` refreshes with `Log.isLoggable(TAG, Log.DEBUG)`.
2. Replace all six `if (Log.isLoggable(...))` gates in `SyncTimeLogger` with `if (isVerbose)`, keeping the message construction inside the guard exactly as it is.
3. In `SyncManager`, change `syncPerf` to gate on `syncTimeLogger.isVerbose` and log with the shared tag constant, and convert the three inline gates at 145, 215 and 224 to call `syncPerf { … }` so there is a single gate expression left in the file.
4. Keep `stopLogging`'s summary path unconditional — the Room summary write must not depend on the verbose flag.
5. Add a `SyncTimeLoggerTest` case asserting that flipping the tag's loggability after `startLogging()` does not change behaviour mid-session (documenting the deliberate per-session snapshot) and that `startLogging()` picks up the current value.

acceptance: `./gradlew testDefaultDebugUnitTest` green; with `adb shell setprop log.tag.SyncPerf DEBUG` set before a sync, run a manual sync and confirm the same `SyncPerf` lines still appear (started / per-table / API / DB / completed) and the sync-summary log row is still written to `apk_log` either way.

size budget: ~45 changed lines across 4 files (2 production + 2 test).

out of scope: do not change what is logged or the summary format; do not add a new logging dependency or a `BuildConfig.DEBUG` gate.

---

### 8. reuse the cached shared-team name and skip the label scan in voices filtering (roadmap 3+7)

context: `VoicesViewModel.filterNews` calls `JsonUtils.extractSharedTeamName(news)` per item (line 129) and again in the label collector (line 203). That helper (`utils/JsonUtils.kt` lines 37-55) falls back to `gson.fromJson(news.viewIn, JsonArray::class.java)` whenever `news.parsedViewIn` is null, so filtering re-parses JSON for the whole list on every keystroke and every label change — even though `VoicesAdapter` already memoizes the result into `News.parsedSharedTeamName` (`ui/voices/VoicesAdapter.kt` line 644) and reads the cache first at lines 310 and 329. On top of that, the `dynamicLabelDisplayToValue` map (lines 106-114) is rebuilt over every item's labels on every invocation even when `labelDisplayToValue[selectedLabel]` already resolves the selection.

files: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt` (`filterNews` lines 98-143, and the label collector around line 203). Tests: `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesViewModelTest.kt`. Do NOT touch `utils/JsonUtils.kt`, `model/News.kt`, `ui/voices/VoicesAdapter.kt` or `ui/voices/VoicesFragment.kt` (the last is owned by an open PR).

steps:
1. In `filterNews`, read the memoized value first: `news?.parsedSharedTeamName ?: JsonUtils.extractSharedTeamName(news)`, matching how `VoicesAdapter` already reads it.
2. Do the same at the label-collector call site around line 203.
3. Build `dynamicLabelDisplayToValue` lazily: compute it only when `labelDisplayToValue[selectedLabel]` is null, so the common "static label selected" path skips the whole O(items × labels) pass.
4. Drop the pointless `query.trim().lowercase()` on line 137 (the three `contains` calls at 139-141 already pass `ignoreCase = true`) and use the trimmed query directly, so a needle is not lowercased on every filter call.
5. Extend `VoicesViewModelTest` with cases covering: search matching regardless of case, a "Shared Chat" selection, and a shared-team-name label selection where `parsedSharedTeamName` is pre-populated.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open Voices, type into the search box and confirm matches on message text, author name and title in any case; switch the label chip between "All", a static label, "Shared Chat" and a shared-team label and confirm the list filters correctly in each.

size budget: ~40 changed lines across 2 files (1 production + 1 test).

out of scope: do not change `JsonUtils.extractSharedTeamName` or where `parsedSharedTeamName` gets populated; no repository or DAO changes.

---

### 9. write the server-config preference blocks in one commit instead of eight (roadmap 7+4)

context: `ServerConfigUtils.saveAlternativeUrl` (lines 113-120) fires eight consecutive `SharedPrefManager` setters, each of which is its own `pref.edit { }` commit, and five of them (`setUrlUser`, `setUrlPwd`, `setCouchdbUrl`, `setProcessedAlternativeUrl`, `setIsAlternativeUrl` — see `services/SharedPrefManager.kt` lines 166-203) additionally call `UrlUtils.invalidateCaches()`. `ProcessUserDataActivity.setUrlParts` (lines 141-147) does the same with seven setters and three invalidations, and `UserSessionManager.saveUserInfoPref` (lines 32-46) adds two single-key commits before its own batched `edit`. Beyond the redundant disk writes, the interleaved invalidations mean anything reading `UrlUtils.header` mid-sequence rebuilds the auth header from a half-written config.

files: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt` (lines 151-203, 211, 229), `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt` (`saveAlternativeUrl`, lines 91-121), `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt` (`setUrlParts`, lines 120-150), `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt` (`saveUserInfoPref`, lines 32-46). Tests: `app/src/test/java/org/ole/planet/myplanet/services/SharedPrefManagerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ServerConfigUtilsTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/UserSessionManagerTest.kt`. Do NOT touch `ui/sync/ServerDialogExtensions.kt` (its 18 setter calls are on separate user actions, not one sequence) or `utils/UrlUtils.kt`.

steps:
1. Add one batching entry point to `SharedPrefManager`, e.g. `fun editBatch(block: SharedPreferences.Editor.() -> Unit)` that runs a single `pref.edit { block() }` and then calls `UrlUtils.invalidateCaches()` exactly once. Keep every existing setter as-is so no other caller changes.
2. Expose the private key constants needed by the two call sites (or, preferably, add two narrow batched setters — `saveServerConnection(...)` and `saveAlternativeServerConnection(...)` — that keep the keys private).
3. Rewrite `ServerConfigUtils.saveAlternativeUrl` to make one batched call carrying all eight values.
4. Rewrite `ProcessUserDataActivity.setUrlParts` to make one batched call carrying all seven values, leaving its return value and URL-parsing logic untouched.
5. Fold the `setUserId`/`setUserName` calls in `UserSessionManager.saveUserInfoPref` into its existing `rawPreferences.edit { }` block so login writes preferences once.
6. Update the three tests to assert the stored values are unchanged and that `UrlUtils.invalidateCaches()` (or an observable `UrlUtils.header` recompute) happens once per save rather than per key.

acceptance: `./gradlew testDefaultDebugUnitTest` green; log in against a server whose alternative/clone URL is configured, confirm the sync completes and `UrlUtils.header` authenticates (no 401s in the sync log), then force-stop and reopen the app and confirm the saved server URL, PIN, scheme/host and alternative-URL flag all survive.

size budget: ~80 changed lines across 7 files (4 production + 3 test).

out of scope: do not move any secret out of `BuildConfig` or change the PIN/credential handling; do not rename existing setters or change the preference keys.

---

### 10. cancel the superseded progress query when swiping course steps (roadmap 3+7)

context: `TakeCourseFragment.updateStepDisplay` (line 193) launches an uncancelled `viewLifecycleOwner.lifecycleScope.launch { viewModel.getCurrentProgress(steps, userModel?.id, courseId) }` (line 203). It is invoked from the page-change callback (line 140), `onResume` (line 151) and the step-jump path (line 313), so swiping quickly through a course stacks one DB query per swipe and whichever coroutine finishes last wins the write to `binding.courseProgress` and `currentCourseProgress` — leaving the progress bar showing a step the user already left.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt` (`updateStepDisplay` lines 193-210, and the call sites at 140, 151, 313). Tests: `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`. Do NOT touch `CourseStepFragment.kt`, `CoursesPagerAdapter.kt`, `TakeCourseViewModel.kt`'s repository calls, or `CoursesRepository`/`CoursesRepositoryImpl` (open PRs own the latter).

steps:
1. Add a `private var progressJob: Job?` field to `TakeCourseFragment`.
2. In `updateStepDisplay`, `progressJob?.cancel()` before assigning `progressJob = viewLifecycleOwner.lifecycleScope.launch { … }`, keeping the body identical.
3. Null the field in `onDestroyView` alongside the existing binding teardown so no stale job reference outlives the view.
4. Leave the synchronous UI writes (`tvStep`, `nextStep`, `courseStepProgressBar`) at the top of the function outside the coroutine — they must stay immediate on swipe.
5. Add a `TakeCourseViewModelTest` case pinning `getCurrentProgress`'s contract (it delegates to the repository and is safe to call repeatedly), so the fragment-side cancellation cannot regress the value shown.

acceptance: `./gradlew testDefaultDebugUnitTest` green; open a course from My Courses, swipe rapidly forward and back through its steps, and confirm the step counter, the "Start"/"Next" button label and both progress bars settle on the values for the step actually displayed; then background/foreground mid-course and confirm the progress bar is still correct.

size budget: ~25 changed lines across 2 files (1 production + 1 test).

out of scope: do not move progress tracking into `TakeCourseViewModel` as a `StateFlow` in this task; no repository or layout changes.

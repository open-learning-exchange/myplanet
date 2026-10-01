# Repository-boundary work orders — 2026-10-01

date: 2026-10-01  
base commit: `286e66e0e1daf285c34d8456deb0028016bf858a`  
open PRs checked: #17812, #17811, #17776, #17694, #17680, #17435, #17254, #17187, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075  
ranking basis: user impact divided by blast radius; every listed implementation and test file was checked against every open PR's file list, with extra attention to PRs updated during the last week and labelled `ready` or `merge`.

### 1. Make notification list reads return presentation projections (roadmap 1+7+9)
context: `NotificationDao.getNotifications` selects full `AppNotification` rows at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt:36-37`, although `NotificationsRepositoryImpl.getNotifications` immediately copies only payload fields at `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:137-159`. That makes a UI-shaped repository read pay for entity hydration and leaks persistence shape farther than necessary.
roadmap contribution: Tightens the Room/repository boundary, cuts query and mapping work, and returns a platform-neutral read model suitable for a future shared core.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` (`NotificationDao.getNotifications`); `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (`getNotifications`); `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`; `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`. Leave `NotificationsRepository.kt`, notification UI files, and sync insert functions alone.
steps:
1. Change the Room query result to the existing `NotificationPayload` projection while selecting every constructor field explicitly and preserving the current filter and ordering clauses.
2. Remove the repository's entity-to-payload copy and return the DAO projection directly.
3. Extend the DAO test to prove read/unread filtering, system-admin inclusion, and unread-first/date ordering are unchanged.
4. Update the repository test to assert that the projection is passed through without mutation.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*NotificationDaoTest' --tests '*NotificationsRepositoryImplTest'` passes; the notifications screen shows the same rows, order, filters, and unread count.
size budget: about 55 changed lines across 4 files.
out of scope: Do not change notification writes, sync JSON parsing, `AppNotification`, or UI rendering.

---

### 2. Update enterprise reports without read-modify-upsert (roadmap 1+7+9)
context: `EnterprisesRepositoryImpl.updateTeamEntityById` loads a complete `MyTeam`, mutates it, and upserts it at `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt:129-134`. The report update and image attachment paths only need targeted columns, so full entity hydration expands the repository's coupling and risks overwriting concurrently changed fields.
roadmap contribution: Makes the repository own a precise persistence operation, reduces Room work, and avoids spreading the broad database entity contract.
files: `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` (`updateReport`, `attachTeamImage`, `updateTeamEntityById`); `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (`archiveById`/`setImageNameById` neighborhood); `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt`. Leave `EnterprisesRepository.kt`, team repositories, and enterprise viewmodels alone.
steps:
1. Add one targeted DAO update for the editable finance-report columns, keyed by `_id`, and mark the row updated in the same SQL statement.
2. Replace `updateReport`'s entity load and mutation with that DAO method while preserving missing-report behavior.
3. Delete the now-unused `updateTeamEntityById` helper and remove stale imports.
4. Add tests proving all editable columns are forwarded and a missing row does not trigger image attachment.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*EnterprisesRepositoryImplTest'` passes; editing a finance report still updates its values and optional image without changing unrelated team fields.
size budget: about 45 changed lines across 3 files.
out of scope: Do not change CSV export, report creation, schema versions, or general team update APIs.

---

### 3. Derive download diagnostics from the HTTP request (roadmap 1+5+8+9)
context: `DownloadRepositoryImpl.downloadFileResponse` parses `response.toString()` with `URL_REGEX` at `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt:22-24,53-63` just to record a 404 URL. Retrofit already retains the actual request URL, so the regex couples repository behavior to a debug-string format and can log `null` instead of the failed resource.
roadmap contribution: Consolidates download diagnostics at the repository boundary and removes brittle response-string processing from the workflow.
files: `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt` (`URL_REGEX`, `downloadFileResponse`); `app/src/test/java/org/ole/planet/myplanet/repository/DownloadRepositoryImplTest.kt`. Leave `DownloadRepository.kt`, `ApiInterface.kt`, download workers, and `DiagnosticsRepository` alone.
steps:
1. Read the failed URL from `response.raw().request.url` for 404 diagnostics, falling back to the method argument only if required by the existing test response fixture.
2. Remove `URL_REGEX` and the nested exception-driven parsing branch.
3. Preserve cancellation propagation and every existing `DownloadResult.Error` mapping.
4. Add tests for a 404 request URL and for a diagnostics write failure that must not replace the download error.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*DownloadRepositoryImplTest'` passes; a missing download still reports “File not found” and records the exact requested URL once.
size budget: about 30 changed lines across 2 files.
out of scope: Do not change retry policy, API signatures, authentication headers, or user-facing error text.

---

### 4. Remove Android logging from dictionary seeding (roadmap 1+8+9)
context: `DictionaryRepositoryImpl.insertDictionaryData` already converts every parsing and I/O failure into `DictionaryLoad.Failed`, but also calls `android.util.Log` at `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:3,37-63`. The extra Android dependency adds no contract value and prevents this otherwise platform-neutral repository logic from moving toward the shared core.
roadmap contribution: Keeps failure semantics in the repository contract while removing one Android dependency from data logic.
files: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` (`insertDictionaryData`); `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`. Leave `DictionaryRepository.kt`, `DictionaryFileReader.kt`, `DictionaryMapper.kt`, and the DAO alone.
steps:
1. Remove the `android.util.Log` import and the redundant log call from the failure branch.
2. Keep the original exception in `DictionaryLoad.Failed` so callers and tests retain diagnostic detail.
3. Preserve `CancellationException` rethrowing and the mutex-protected one-time seed behavior.
4. Simplify test setup by deleting any Android `Log` static mocking, then assert the same failure cause is returned.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*DictionaryRepositoryImplTest'` passes; opening Dictionary still seeds once, reports a missing file distinctly, and permits lookup after a successful seed.
size budget: about 12 changed lines across 2 files.
out of scope: Do not change JSON format, dictionary entities, file locations, or seed concurrency.

---

### 5. Remove Android logging from retry execution (roadmap 5+8+9)
context: `RetryRepositoryImpl.executeOperation` imports `android.util.Log` and logs every outcome at `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt:3,68-136`, while its sealed `RetryOperationResult` and persisted failure state already describe those outcomes. Keeping the repository dependent on Android logging complicates JVM tests and blocks the retry workflow from a platform-free core.
roadmap contribution: Consolidates retry outcomes in the repository/Room workflow and moves that workflow closer to platform-free Kotlin.
files: `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` (`TAG`, `executeOperation`); `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`. Leave `RetryRepository.kt`, `RetryDao.kt`, `RetryQueueWorker.kt`, and API definitions alone.
steps:
1. Remove the Android log import, `TAG`, and outcome log calls without altering result selection.
2. Retain persisted error messages and HTTP codes as the single repository diagnostic record.
3. Keep 409 as success, 5xx as retryable, other HTTP failures as terminal, and cancellation as rethrown.
4. Remove static `Log` mocking from the unit test and strengthen assertions on DAO status transitions for each outcome.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*RetryRepositoryImplTest'` passes; retry queue entries still complete, retry, or terminate under exactly the same conditions.
size budget: about 28 changed lines across 2 files.
out of scope: Do not alter backoff timing, queue cleanup, HTTP requests, or worker scheduling.

---

### 6. Route shelf-sync failures through the existing sync logger (roadmap 5+8+9)
context: `SyncRepositoryImpl` has an injected `SyncTimeLogger`, yet `processShelfParallel` and `processShelfDataOptimizedSync` call `android.util.Log` at `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt:3,47,102-106,186-190`. This splits sync observability across two mechanisms and leaves repository orchestration tied to Android.
roadmap contribution: Consolidates shelf-sync telemetry and removes an Android API from repository orchestration.
files: `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` (`processShelfParallel`, `processShelfDataOptimizedSync`); `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt`. Leave `SyncRepository.kt`, `SyncManager.kt`, `TransactionSyncManager.kt`, and upload code alone.
steps:
1. Replace the two Android error logs with concise `SyncTimeLogger.logDetail` events using the existing shelf-sync category.
2. Remove the Android log import while preserving exception-to-zero processed-count behavior.
3. Keep cancellation rethrowing, adaptive batch sizing, and dispatch-map behavior unchanged.
4. Remove `Log` static mocking from tests and verify failure details are sent to `SyncTimeLogger` once.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*SyncRepositoryImplTest'` passes; failed shelf batches remain visible in sync diagnostics and do not crash or inflate the processed count.
size budget: about 22 changed lines across 2 files.
out of scope: Do not change concurrency limits, endpoint construction, batching, cache policy, or public sync state.

---

### 7. Keep diagnostics failure handling platform-neutral (roadmap 1+8+9)
context: `DiagnosticsRepositoryImpl.saveLogToRoom` and `saveLogsToRoom` already express write failure as `false`, but also call `android.util.Log` at `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt:3,61-105`. That Android-only side effect makes the diagnostics data boundary harder to reuse and forces local JVM tests to accommodate framework logging.
roadmap contribution: Makes the Boolean repository contract authoritative and removes an Android-only side channel from data persistence.
files: `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` (`saveLogToRoom`, `saveLogsToRoom`, `TAG`); `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`. Leave `DiagnosticsRepository.kt`, `ApkLogDao.kt`, crash capture, and upload services alone.
steps:
1. Remove the Android log import, both warning calls, and the unused `TAG` companion.
2. Preserve the Boolean success/failure contract and rethrow `CancellationException` unchanged.
3. Ensure both single and batch paths still resolve user, parent, planet, and version metadata before insertion.
4. Remove any `Log` mocking and add explicit tests for DAO exceptions returning `false` in both paths.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*DiagnosticsRepositoryImplTest'` passes; crash and diagnostic records still persist when Room is available and fail cleanly when it is not.
size budget: about 18 changed lines across 2 files.
out of scope: Do not change log payload fields, UUID generation, upload batching, or crash-file retention.

---

### 8. Source the installed version through the version provider (roadmap 1+4+9)
context: `ConfigurationsRepositoryImpl.checkConfigurationUrl` reads `R.string.app_version` through Android `Context` at `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt:312-326`, even though the class already injects `AppVersionProvider`. Using two version sources obscures the repository boundary and makes configuration negotiation harder to isolate in plain Kotlin tests.
roadmap contribution: Cleans DI usage by making the injected abstraction authoritative and reduces direct Android resource access in repository logic.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (`checkConfigurationUrl`); `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`. Leave `ConfigurationsRepository.kt`, string resources, `AppVersionProvider.kt`, networking modules, and preferences alone.
steps:
1. Replace the resource lookup in `checkConfigurationUrl` with `appVersionProvider.versionName`.
2. Define the same explicit fallback currently expected when the provider has no version name, without changing URL or PIN validation.
3. Remove only imports made unused by this narrow substitution; keep Context for the remaining localized messages.
4. Add tests for a normal provider version and a missing provider version when comparing configuration metadata.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*ConfigurationsRepositoryImplTest'` passes; login configuration accepts/rejects server metadata exactly as before for the installed app version.
size budget: about 18 changed lines across 2 files.
out of scope: Do not redesign localized errors, change server reachability, clear data, or alter the public callback interface.

---

### 9. Bound and invalidate the in-memory life cache (roadmap 1+7+8)
context: `LifeCache` stores a copied list for every cache key in an unbounded `ConcurrentHashMap` at `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt:23-47`. Long-lived sessions that switch among users can retain stale lists indefinitely, while corrupt persisted JSON returns `null` but leaves the bad value available for repeated decoding.
roadmap contribution: Bounds a repository-layer memory hotspot and makes corrupt cache recovery deterministic without changing consumers.
files: `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt` (`memoryCache`, `read`, `write`); `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`. Leave `LifeRepository.kt`, `LifeRepositoryImpl.kt`, `MyLifeDao.kt`, and dashboard UI alone.
steps:
1. Cap the in-memory cache to a small fixed number of user keys using synchronized access and deterministic oldest-entry eviction with standard-library collections only.
2. On decode failure, remove the corrupt persisted entry and any matching memory entry before returning `null`.
3. Preserve defensive copies on every read and write so callers cannot mutate cached state.
4. Add tests for eviction order, corrupt-value cleanup, and the existing round-trip/copy guarantees.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*LifeCacheTest'` passes; dashboard life items remain stable across account switches and corrupt cache data self-recovers from the repository source.
size budget: about 55 changed lines across 2 files.
out of scope: Do not change life seeding, Room ordering, serialized field names, or add a cache library.

---

### 10. Share PDF submission loading and page setup paths (roadmap 1+3+8; advances 9 by isolating the remaining Android renderer)
context: `SubmissionsRepositoryExporter.generateSubmissionPdf` and `generateMultipleSubmissionsPdf` duplicate submission/answer attachment and Android page initialization at `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:47-73,132-170`. Consolidating the data-loading seam makes the repository-to-renderer relationship explicit and reduces the Android-specific surface that a later multiplatform extraction must replace.
roadmap contribution: Smooths the repository/renderer relationship and isolates Android PDF work behind a smaller internal seam for eventual shared-core extraction.
files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` (`generateSubmissionPdf`, `generateMultipleSubmissionsPdf`, page setup helpers); `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt`. Leave `SubmissionsRepositoryImpl.kt`, all four DAOs, viewmodels, and model classes alone.
steps:
1. Extract private helpers inside the existing class for attaching bulk-fetched answers/membership data and for starting a configured PDF page.
2. Make both public generation functions use those helpers without introducing additional DAO calls.
3. Preserve single-export and bulk-export filenames, page dimensions, question order, and null-on-failure contract.
4. Replace the formatter-only test with focused helper-visible-through-output tests for empty IDs, answer association, and multi-page setup using existing test dependencies.
5. Confirm single-item loading remains one submission query, one answer query, one exam query, and one question query.
verification note: Use test doubles already present in the repository test suite; do not expose private helpers solely for tests.
risk control: Compare generated document metadata and DAO call counts rather than brittle binary PDF bytes.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `./gradlew testDefaultDebugUnitTest --tests '*SubmissionsRepositoryExporterTest'` passes; single and multi-submission exports contain the same headings, answers, ordering, and page breaks as before.
size budget: about 85 changed lines across 2 files.
out of scope: Do not redesign PDF appearance, move files, change storage directories, add a PDF dependency, or alter DAO queries.

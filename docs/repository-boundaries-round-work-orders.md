# myPlanet repository-boundary refactor work orders

**Date:** 2026-09-24  
**Base commit:** `14da1ba6bcec7b8affbf047ac78f16c511f5b357`  
**Open PRs checked:** #17435, #17430, #17356, #17254, #17187, #16624, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

Open-PR file lists were retrieved before ranking candidates. Every implementation and test file below is outside all 31 open PRs, not only the PRs labelled `review` or `ready`.

### 1. Replace the notifications repository’s cross-feature `News` list with a count (roadmap 1+3+7; advances 9)

context: `NotificationsRepository.updateTeamNotification` accepts `List<News>` only to derive `news.size`, so the notifications boundary leaks an entire Voices feature model and makes callers materialize rows merely to pass a count (`NotificationsRepository.kt:31`, `NotificationsRepositoryImpl.kt:366-378`). The integration test repeats that leak by fetching filtered news before updating the badge (`TeamChatBadgeIntegrationTest.kt:106`).

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (`NotificationsRepository.updateTeamNotification`); `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (`updateTeamNotification`); `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` (the two `updateTeamNotification` tests); `app/src/test/java/org/ole/planet/myplanet/repository/TeamChatBadgeIntegrationTest.kt` (badge update scenarios). Leave `VoicesRepository`, `NewsDao`, and all team UI files alone.

steps:
1. Change `updateTeamNotification` to accept a non-negative `Int` count rather than `List<News>`.
2. Remove the now-unused `News` imports from the interface and implementation.
3. Make the implementation compare and persist the supplied count without reconstructing or retaining feature entities.
4. Update repository tests to pass counts directly and retain assertions for insert-versus-update behavior.
5. Update the integration test to use `countTopLevelByTeam` and verify badge transitions at zero and nonzero counts.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest" --tests "org.ole.planet.myplanet.repository.TeamChatBadgeIntegrationTest"` and `./gradlew testDefaultDebugUnitTest` pass; a team chat badge still appears and clears at the same message counts.

size budget: about 35 changed lines across 4 files.

out of scope: Do not redesign notification persistence, alter Voices filtering semantics, or touch any open-PR file.

---

### 2. Move multi-table sync filtering behind `RealtimeSyncManager` (roadmap 1+3+5; advances 9)

context: `RealtimeSyncManager` owns table-update publication but exposes only a single-table selector (`RealtimeSyncManager.kt:20-27`), forcing `RealtimeSyncHelper` to read the raw stream and duplicate membership filtering in the UI layer (`RealtimeSyncMixin.kt:29-38`). A set-based repository-style query keeps selection rules at the event-source boundary and reduces UI knowledge of stream internals.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManager.kt` (`updatesFor` and `dataUpdateFlow`); `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt` (`RealtimeSyncHelper.setupRealtimeSync`); `app/src/test/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManagerTest.kt` (`updatesFor` tests). Leave concrete feature fragments and `SyncManager` alone.

steps:
1. Add a set-based `updatesFor(tables: Set<String>)` overload that returns only updates whose table is requested.
2. Preserve the existing string overload by delegating to the set overload so current callers do not churn.
3. Return an empty flow immediately for an empty set and cover that contract in unit tests.
4. Switch `RealtimeSyncHelper` to the set overload and remove its direct Flow `filter` import.
5. Test that unrelated updates are excluded and each requested table is delivered in publication order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.RealtimeSyncManagerTest"` and `./gradlew testDefaultDebugUnitTest` pass; screens watching several tables still refresh once relevant sync updates arrive and ignore unrelated tables.

size budget: about 40 changed lines across 3 files.

out of scope: Do not change debounce timing, buffer capacity, refresh callbacks, or sync scheduling.

---

### 3. Hide `SharedPreferences` behind the Life cache boundary (roadmap 1+4+8; advances 9)

context: `LifeCache` lives in the repository package yet directly imports Android `SharedPreferences` and AndroidX `edit` (`LifeCache.kt:3-5,21-23`), preventing this cache policy from moving into a platform-free core. Its tests consequently mock Android editor details instead of testing the cache contract (`LifeCacheTest.kt:19-33`).

files: `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt` (`LifeCache` constructor, `read`, and `write`); `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt` (`LifeCacheTest`). Leave `LifeRepositoryImpl`, Hilt modules, and the shared-preference qualifiers alone.

steps:
1. Define a minimal internal string-store contract alongside `LifeCache`, with nullable read and write operations only.
2. Keep the injected constructor as an Android adapter that wraps the qualified `SharedPreferences`, while route cache logic through a second internal constructor taking the string store.
3. Move key composition, JSON parsing, defensive copies, and memory caching onto the platform-free constructor path.
4. Rewrite unit tests around an in-memory fake store, removing mocks of `SharedPreferences.Editor`.
5. Retain tests for malformed JSON, read-through caching, write-through caching, fallback keys, and defensive copies.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.LifeCacheTest"` and `./gradlew testDefaultDebugUnitTest` pass; My Life ordering and visibility survive process restart exactly as before.

size budget: about 90 changed lines across 2 files.

out of scope: Do not change the persisted key prefix or JSON shape, and do not migrate existing preference data.

---

### 4. Make personal-resource timestamps deterministic at the repository boundary (roadmap 1+3+8; advances 9)

context: `PersonalsRepositoryImpl.savePersonalResource` calls `Date().time` and serialization calls `System.currentTimeMillis()` (`PersonalsRepositoryImpl.kt:37-47,93-99`), so one repository operation can contain two unrelated clocks and tests cannot assert its time contract. The project already uses the platform-free `TimeProvider` abstraction in repositories.

files: `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` (`PersonalsRepositoryImpl` constructor, `savePersonalResource`, and `serialize`); `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` (constructor setup and save/upload timestamp tests). Leave `PersonalsRepository`, `PersonalDao`, and personal UI files alone.

steps:
1. Inject `TimeProvider` into `PersonalsRepositoryImpl` and remove direct `Date` and system-clock reads.
2. Capture one timestamp when creating a personal record and store it as `Personal.date`.
3. Use `TimeProvider.now()` for the upload-date field during serialization, with a single read per serialization.
4. Update test construction with a fixed fake or mock time provider.
5. Add assertions that creation and upload timestamps use the supplied values and do not drift with wall-clock time.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.PersonalsRepositoryImplTest"` and `./gradlew testDefaultDebugUnitTest` pass; saving and uploading a personal resource retains its existing success/error messages and dates.

size budget: about 45 changed lines across 2 files.

out of scope: Do not change personal upload URLs, attachment behavior, identifiers, or UI date formatting.

---

### 5. Make retry queue state derive from the DAO instead of mutable process flags (roadmap 1+5+8; advances 9)

context: `RetryRepository` exposes `isCurrentlyProcessing` and `setProcessing`, while `RetryRepositoryImpl` also has authoritative DAO states such as pending and in-progress (`RetryRepository.kt:33-41`, `RetryRepositoryImpl.kt:143-172`). Two sources of truth can diverge after cancellation or process recreation, which makes workers and diagnostics report a stale busy state.

files: `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` (`isCurrentlyProcessing`, `setProcessing`, `safeClearQueue`, and queue snapshot logic); `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt` (processing and clear-queue tests). Leave `RetryRepository`, `RetryDao`, and retry workers alone so this remains independently mergeable.

steps:
1. Keep the public compatibility methods, but constrain the in-memory flag to an active execution guard rather than queue truth.
2. Ensure every `executeOperation` exit path clears the guard with structured finalization, including invalid payloads and thrown exceptions.
3. Make `safeClearQueue` reject only genuinely active execution and otherwise use DAO state for deletion.
4. Make queue snapshots source pending counts exclusively from `RetryDao.getActiveCount`.
5. Add cancellation, malformed-payload, exception, and successful-execution tests proving the guard is always released.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.RetryRepositoryImplTest"` and `./gradlew testDefaultDebugUnitTest` pass; retry diagnostics no longer remain “processing” after a failed or cancelled operation, and active work still cannot be cleared.

size budget: about 80 changed lines across 2 files.

out of scope: Do not alter backoff values, HTTP retryability rules, DAO schema, or worker scheduling.

---

### 6. Normalize shelf cache values inside `SyncRepositoryImpl` (roadmap 1+5+8; advances 9)

context: `SyncRepositoryImpl.getCachedShelvesWithData` splits a raw comma-separated preference and only filters blanks (`SyncRepositoryImpl.kt:205-213`), allowing whitespace and duplicate shelf IDs to escape the repository boundary. Downstream sync work can then be scheduled repeatedly for the same shelf.

files: `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` (`getCachedShelvesWithData` and `cacheShelvesWithData`); `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt` (shelf-cache tests). Leave `SyncRepository`, sync managers, upload managers, and preference storage keys alone.

steps:
1. Normalize every shelf ID by trimming it, dropping blanks, and retaining first-seen order while removing duplicates.
2. Apply the same normalization before writing and after reading so legacy cache strings are repaired logically without migration.
3. Persist the canonical comma-separated form from `cacheShelvesWithData`.
4. Return a fresh immutable list so callers cannot mutate repository cache state.
5. Add tests for whitespace, duplicates, empty input, legacy malformed strings, and stable order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SyncRepositoryImplTest"` and `./gradlew testDefaultDebugUnitTest` pass; a shelf represented multiple times in cached preferences is synchronized only once.

size budget: about 45 changed lines across 2 files.

out of scope: Do not rename the preference key, change shelf network calls, or modify parallelism.

---

### 7. Stop diagnostics persistence from printing exceptions across its boundary (roadmap 1+8; advances 9)

context: both diagnostics write paths catch broad exceptions and call `printStackTrace` (`DiagnosticsRepositoryImpl.kt:59-76,79-95`), leaking an uncontrolled side effect from a repository that otherwise reports failure with `Boolean`. Tests cover return values but not the important guarantee that a failed batch never falls back to partial row inserts.

files: `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` (`saveLogToRoom`, `saveLogsToRoom`, and `buildApkLog`); `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt` (failure and batch tests). Leave `DiagnosticsRepository`, `ApkLogDao`, `CrashLogStore`, and upload services alone.

steps:
1. Remove `printStackTrace` calls and keep failure reporting entirely in the repository return contract.
2. Extract the shared user/version metadata lookup used by single and batch saves into one private value-building path.
3. Preserve one DAO call for a batch and never retry items individually after `insertAll` fails.
4. Add tests proving empty batches avoid collaborators, batch metadata is fetched once, and DAO failures return `false` without single inserts.
5. Retain the current field mapping for parent code, planet code, model ID, version, type, time, and error.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DiagnosticsRepositoryImplTest"` and `./gradlew testDefaultDebugUnitTest` pass; crash logs continue to queue on success and callers receive a clean failure signal when Room rejects a write.

size budget: about 65 changed lines across 2 files.

out of scope: Do not introduce a logging dependency, change crash-log fields, or alter upload batching.

---

### 8. Hoist course resource download selection out of the ViewModel (roadmap 3+6+7; advances 9+10)

context: `CoursesStepsViewModel.loadStep` repeats `filter { !it.isResourceOffline() }` for current and next-step resources and directly builds download URLs (`CoursesStepsViewModel.kt:82-103`). This mixes repository data policy with Android ViewModel orchestration and makes current-step and prefetch behavior easy to diverge.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` (`loadStep`); `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` (`loadStep` download and prefetch tests). Leave repository interfaces, resource implementations, fragments, and download services alone.

steps:
1. Add a private pure function that selects non-offline resources and maps valid download URLs once for both branches.
2. Have current-step download selection reuse that result while still passing resource models to `downloadResourcesPriority`.
3. Have next-step prefetch use the same selection rule and skip coordinator calls for empty selections.
4. Keep server availability checks limited to the current-step foreground download path.
5. Add tests for all-offline, mixed, empty, and next-step-null cases and verify no unnecessary coordinator calls.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest"` and `./gradlew testDefaultDebugUnitTest` pass; opening a course step downloads missing current resources and prefetches only missing next-step resources exactly as before.

size budget: about 70 changed lines across 2 files.

out of scope: Do not change URL construction, download priority, markdown rendering, or screen layout.

---

### 9. Deduplicate upload-success identifiers before DAO updates (roadmap 1+5+7+8; advances 9)

context: `UploadRepositoryImpl.markUploaded` forwards every successful submission result to an individual DAO update, while exam updates query all supplied IDs and accumulate mutations (`UploadRepositoryImpl.kt:46-55,85-107`). Duplicate success responses therefore cause redundant Room writes and can apply competing revisions in one batch.

files: `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt` (`markUploaded` and `markExamsUploaded`); `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt` (mark-uploaded tests). Leave `UploadRepository`, DAOs, `UploadCoordinator`, and uploader configurations alone.

steps:
1. Canonicalize successes by `localId` at entry, retaining the last server result for each ID because it carries the newest observed revision.
2. Use the canonical list for submission update calls and failed-result reporting.
3. Query exams once with distinct IDs, update each entity once, and preserve canonical input order in failures.
4. Keep empty-input short circuits so neither DAO is called.
5. Add tests for duplicate identical results, duplicate competing revisions, missing exam rows, and submission update failures.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.UploadRepositoryImplTest"` and `./gradlew testDefaultDebugUnitTest` pass; a successful upload marks each local row once and still reports every distinct row Room could not update.

size budget: about 65 changed lines across 2 files.

out of scope: Do not change HTTP upload calls, response bridging, attachment MIME detection, or coordinator retry policy.

---

### 10. Make chunked answer DAO operations stable and no-op on empty IDs (roadmap 1+7+8; advances 9)

context: `AnswerDao.getBySubmissionIds` and `deleteBySubmissionIds` manually chunk IDs for SQLite limits (`AnswerDao.kt:13-29`), but their boundary behavior for empty lists, duplicate IDs, and result order must be explicit before more repositories depend on them. These helpers are the correct place to absorb Room parameter-limit details rather than leaking chunking upward.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt` (`getBySubmissionIds`, `deleteBySubmissionIds`, and internal query methods); `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt` (chunking tests). Leave `SubmissionDao`, `SubmissionsRepositoryExporter`, entities, and database schema alone.

steps:
1. Return immediately for empty ID collections without invoking either internal Room query.
2. Deduplicate submission IDs while retaining first-seen order before forming chunks.
3. Preserve deterministic output grouped by requested submission order, with each DAO row returned once.
4. Sum delete counts across chunks without overflow-prone intermediate collection allocation.
5. Add boundary tests at zero, one, the chunk limit, limit plus one, and duplicated IDs.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.AnswerDaoTest"` and `./gradlew testDefaultDebugUnitTest` pass; exporting or deleting submissions returns the same answers while large selections no longer risk SQLite bind-limit failures or duplicate work.

size budget: about 75 changed lines across 2 files.

out of scope: Do not alter answer entities, add indices, bump the Room version, or change submission export formatting.

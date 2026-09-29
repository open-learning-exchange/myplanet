# Repository-boundary work orders

date: 2026-09-25
base commit: `354410d0ae49734313dc01d8d90a35e0240ec787`
open PRs checked: #17679, #17674, #17673, #17672, #17671, #17670, #17668, #17667, #17666, #17665, #17664, #17663, #17662, #17661, #17659, #17655, #17654, #17653, #17652, #17648, #17647, #17644, #17643, #17638, #17634, #17633, #17632, #17631, #17630, #17629, #17622, #17621, #17620, #17619, #17618, #17616, #17613, #17612, #17610, #17609, #17604, #17603, #17602, #17596, #17595, #17594, #17593, #17592, #17589, #17587, #17586, #17580, #17579, #17578, #17577, #17576, #17575, #17571, #17570, #17569, #17565, #17564, #17563, #17562, #17561, #17560, #17551, #17550, #17545, #17544, #17543, #17542, #17541, #17540, #17539, #17538, #17537, #17435, #17254, #17187, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

Candidates were ranked by user impact divided by blast radius after excluding every file touched by these open pull requests. Tasks below intentionally use disjoint file sets and can merge in any order.

### 1. Make retry dequeue order deterministic (roadmap 1+5+7+8; advances 9)
context: `RetryDao.getPending` selects every eligible row without an order at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt:25-29`. Retry execution can therefore vary with SQLite's query plan, making older work wait behind newer work and making sync behavior harder to test; keeping the policy in SQL also moves scheduling logic toward a platform-free repository boundary.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt` (`RetryDao.getPending`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RetryDaoTest.kt` (`RetryDaoTest`). Leave `RetryRepositoryImpl`, `RetryQueue`, and all worker classes alone because open PRs own those paths.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Order eligible pending operations by `nextRetryTime` ascending and add `id` as a stable tie-breaker in the existing query.
2. Preserve the current status, due-time, and maximum-attempt predicates exactly.
3. Extend `RetryDaoTest` with out-of-order due times and equal-time rows to prove the full deterministic order.
4. Keep the DAO return type and repository-facing contract unchanged.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.RetryDaoTest"` and `./gradlew testDefaultDebugUnitTest` pass; after restart, the retry queue processes the oldest due operation first and does not process future or exhausted operations.
size budget: about 20 changed lines across 2 files.
out of scope: do not add retry batching, change backoff arithmetic, or alter retry statuses. Do not touch `RetryRepositoryImpl` or `RetryQueue`.

---
### 2. Chunk submission-photo ID lookups at the DAO boundary (roadmap 1+5+7+8; advances 9)
context: `SubmitPhotosDao.getByIds` binds an arbitrary `Array<String>` directly to one `IN` clause at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmitPhotosDao.kt:17-18`. Large survey uploads can exceed SQLite's bind-variable ceiling; the DAO already contains transaction-aware batch behavior in `markUploadedBatch` at lines 29-33, so it is the correct boundary for hiding Room limits.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch only `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmitPhotosDao.kt` (`SubmitPhotosDao.getByIds` and a renamed internal query). Leave `SubmissionsRepositoryImpl`, `SubmissionDao`, `AnswerDao`, and upload coordinator files alone because open PRs own the repository/upload paths.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Rename the current annotated ID query to an internal DAO method without changing its SQL projection.
2. Add a public suspend `getByIds` wrapper with the existing `Array<String>` signature so callers do not learn about Room constraints.
3. Return immediately for an empty array and query non-empty IDs in chunks safely below SQLite's bind limit.
4. Flatten chunk results without changing entity shape or adding deduplication semantics that callers did not request.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; a submission containing more than 999 photo IDs can resolve its local photo records without a SQLite “too many SQL variables” error, while an empty request returns an empty list.
size budget: about 12 changed lines in 1 file.
out of scope: do not modify photo upload ordering, `markUploadedBatch`, repository APIs, or upload payloads. Do not add a dependency or database migration.

---
### 3. Chunk My Life document lookups inside Room (roadmap 1+7+8; advances 9)
context: `MyLifeDao.getByIds` sends its entire ID list through one `IN` expression at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:21-22`. `LifeRepositoryImpl` uses that method for managed-life reconciliation, so a large configuration can leak SQLite's variable limit through the repository instead of receiving ordinary domain data.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt` (`MyLifeDao.getByIds`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt` (`MyLifeDaoTest`). Leave `LifeRepositoryImpl`, `LifeRepository`, `MyLife`, and `AppDatabase` alone.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Rename the annotated `getByIds` query to an internal method.
2. Keep `getByIds(List<String>)` as the public DAO operation and split non-empty inputs into safe chunks.
3. Return an empty list without issuing SQL when the input is empty.
4. Add DAO tests covering empty input and a list larger than SQLite's bind-variable limit, asserting all matching rows are returned.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest"` and `./gradlew testDefaultDebugUnitTest` pass; opening Life after a large configuration sync shows all configured items without a database exception.
size budget: about 30 changed lines across 2 files.
out of scope: do not change user-ID fallback rules, visibility filtering, deduplication, or Life repository defaults. Do not bump the Room schema.

---
### 4. Stabilize most-opened resource aggregation (roadmap 1+7+8; advances 9)
context: `ResourceActivityDao.getMostOpenedResource` groups by `resourceId` but selects a non-aggregated `title` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt:26-31`. When a resource's title changes, SQLite may return either title even though the count is stable, leaking storage-history details into dashboard view modelling.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt` (`ResourceActivityDao.getMostOpenedResource`) and `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDaoTest.kt` (`ResourceActivityDaoTest`). Leave `ActivitiesRepositoryImpl`, `DashboardElementViewModel`, and `ResourceActivity` alone.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Make the SQL choose one deterministic nonblank title for each `resourceId` while retaining a single total count per resource.
2. Preserve filtering by user and activity type and preserve the current winner ordering by count then title.
3. Add a DAO test where two rows share a resource ID but have different titles, proving their opens remain one aggregate.
4. Retain the `ResourceOpenCount` projection and nullable return contract.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.ResourceActivityDaoTest"` and `./gradlew testDefaultDebugUnitTest` pass; the dashboard's most-opened resource count includes every open for that resource and its displayed title is stable across reloads.
size budget: about 25 changed lines across 2 files.
out of scope: do not change activity logging, dashboard layout, repository method signatures, or historical rows. Do not add an entity index in this task.

---
### 5. Treat null search revisions as pending uploads (roadmap 1+5+8; advances 9)
context: `SearchActivityDao.getPendingUploads` only accepts `_rev = ''` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SearchActivityDao.kt:10-12`. Other upload DAOs treat a missing revision as pending, so locally created search events with a null revision can silently remain outside the repository upload workflow.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch only `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SearchActivityDao.kt` (`SearchActivityDao.getPendingUploads`). Leave `ActivitiesRepositoryImpl`, `UploadManager`, `SearchActivity`, and upload configuration classes alone because their paths are either shared orchestration or open-PR territory.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Expand the existing pending predicate to include both null and empty revision values.
2. Keep non-empty revisions excluded so acknowledged events are not uploaded again.
3. Do not alter the DAO method name, return type, insertion behavior, or acknowledgement update.
4. Rely on Room's compile-time query validation and the full unit suite for integration coverage.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; an offline search event whose `_rev` is null appears in the next activity upload, while an event with a server revision remains excluded.
size budget: about 2 changed lines in 1 file.
out of scope: do not modify the `SearchActivity` schema, activity payload serialization, or upload retry policy. Do not add migration or dependency changes.

---
### 6. Align course-activity pending semantics with the upload boundary (roadmap 1+5+8; advances 9)
context: `CourseActivityDao.getPendingUploads` recognizes only null revisions at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseActivityDao.kt:10-12`, while legacy/local records may use an empty revision to mean not acknowledged. That persistence encoding should be normalized by the DAO rather than forcing repository or service code to know which sentinel was stored.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch only `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseActivityDao.kt` (`CourseActivityDao.getPendingUploads`). Leave `ActivitiesRepositoryImpl`, `UploadManager`, `CourseActivity`, and course UI files alone.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Update the pending query to accept null or empty `_rev` values while retaining the `type != 'sync'` exclusion.
2. Preserve the existing entity projection and insert and acknowledgement methods.
3. Ensure rows with any non-empty revision still remain outside the pending result.
4. Use Room compilation and the project unit suite to catch query/type regressions.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; course visits created offline with either missing-revision representation upload once, and sync-marker activities remain excluded.
size budget: about 2 changed lines in 1 file.
out of scope: do not merge normal and sync activity queues, alter course progress, or touch upload managers. No schema or index changes belong here.

---
### 7. Give pending news logs a deterministic upload order (roadmap 1+5+7+8; advances 9)
context: `NewsLogDao.getPendingUploads` returns null/empty-revision rows without ordering at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsLogDao.kt:10-12`. Reaction and view logs may consequently reach the server in storage-plan order, which makes repeated upload batches and failure recovery less predictable.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch only `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsLogDao.kt` (`NewsLogDao.getPendingUploads`). Leave `VoicesRepositoryImpl`, `UploadManager`, `NewsLog`, and all Voices UI files alone because open PRs touch those neighbors.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Inspect the existing `NewsLog` columns used by this query and select the stable local creation/key column already present on every row.
2. Add ascending ordering by that existing column and `id` as the deterministic tie-breaker.
3. Preserve the null-or-empty pending predicate and full-entity return type.
4. Do not move ordering into Voices repository or upload services.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; pending news logs upload oldest-first with stable ordering after an app restart, and acknowledged logs remain absent.
size budget: about 3 changed lines in 1 file.
out of scope: do not change news payloads, reactions, retry behavior, or the `NewsLog` entity. Do not add a Room migration or index.

---
### 8. Request only the active user ID for course progress rows (roadmap 3+7+8; advances 9+10)
context: `ProgressViewModel.loadCourseData` loads a full `UserEntity` through `UserRepository.getUserModel` only to read `id` at `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt:24-27`. This couples view modelling to the persistence-shaped user model and does extra hydration when `UserRepository.getCurrentUserId` already exposes the needed boundary value.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt` (`ProgressViewModel.loadCourseData`) and `app/src/test/java/org/ole/planet/myplanet/ui/courses/ProgressViewModelTest.kt` (`ProgressViewModelTest`). Leave `ProgressRepository`, `UserRepository`, all repository implementations, and course fragments alone.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Replace the full-user lookup with the existing current-user-ID repository operation.
2. Pass that nullable ID directly to `ProgressRepository.getCourseProgressRows` without introducing Android state into the ViewModel.
3. Update mocks and verifications to prove the full-user operation is no longer called.
4. Retain the existing `StateFlow` and loading behavior for signed-out users.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.ProgressViewModelTest"` and `./gradlew testDefaultDebugUnitTest` pass; the course progress grid shows the same rows for the signed-in user and remains empty/valid when no user is active.
size budget: about 12 changed lines across 2 files.
out of scope: do not change progress calculations, fragment rendering, repository interfaces, or user session storage. Do not introduce a new use-case class in this task.

---
### 9. Remove full-user hydration from single-course progress loading (roadmap 3+7+8; advances 9+10)
context: `CourseProgressViewModel.loadProgress` fetches a full user and then reads `_id` at `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt:23-27`. The view model needs only the repository's current identity; keeping entity selection out of UI state makes this relationship smoother and more portable for later Compose screens.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt` (`CourseProgressViewModel.loadProgress`) and `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModelTest.kt` (`CourseProgressViewModelTest`). Leave `CoursesRepository`, `UserRepository`, their implementations, and `CourseProgressActivity` alone.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Replace `getUserModel` with the existing current-user-ID lookup.
2. Pass the returned nullable identifier to `CoursesRepository.getCourseProgress` and retain the one-load guard.
3. Update the existing success and double-load tests to mock the ID-level contract.
4. Add a missing-user assertion showing that loading still completes through the repository with a null ID.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CourseProgressViewModelTest"` and `./gradlew testDefaultDebugUnitTest` pass; opening a course shows the same progress and repeated composition/activity recreation does not duplicate the load.
size budget: about 18 changed lines across 2 files.
out of scope: do not alter identifier persistence, progress computation, navigation, or repository method signatures. Do not combine this ViewModel with `ProgressViewModel`.

---
### 10. Preserve coroutine cancellation in shelf uploads (roadmap 5+8; advances 9)
context: `UploadToShelfService.uploadUserData`, `uploadSingleUserData`, and `uploadSingleUserHealth` catch broad failures at `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt:31-48`, `52-67`, and `76-90`. Cancellation can therefore be converted into a success-listener message, allowing obsolete sync/upload work to continue and leaking orchestration policy across the repository boundary.
This is intentionally a narrow boundary change with no public API or schema expansion.
files: touch `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt` (`uploadUserData`, `uploadSingleUserData`, `uploadSingleUserHealth`, and `uploadSingleUserToShelf`) and `app/src/test/java/org/ole/planet/myplanet/services/UploadToShelfServiceTest.kt` (`UploadToShelfServiceTest`). Leave `UserRepository`, `UserSyncRepository`, `HealthRepository`, `UploadManager`, and workers alone.
Only the named files are assigned to this work order; all other neighboring layers remain untouched.
steps:
1. Explicitly rethrow `CancellationException` before existing error-to-listener handling in every broad catch path.
2. Replace the remaining `printStackTrace` path with the same structured listener behavior already used by this service, without swallowing cancellation.
3. Add coroutine tests that cancel user and health upload operations and assert no success/error callback is emitted after cancellation.
4. Retain current messages for ordinary repository and network failures.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.UploadToShelfServiceTest"` and `./gradlew testDefaultDebugUnitTest` pass; cancelling sync stops shelf/health upload cleanly, while genuine failures still surface through the existing listener.
size budget: about 40 changed lines across 2 files.
The existing application-scope ownership and dispatcher injection remain unchanged.
Ordinary exception reporting must continue to use the current callback contract.
out of scope: do not redesign callbacks, change batching, merge this service into `UploadManager`, or alter repository contracts. Do not add logging dependencies.
# myPlanet refactor round — repository boundaries

**date:** 2026-09-17 · **base commit:** `fcb4c53` (master) · **branch:** `claude/cool-lamport-lf9y0m`

**open PRs checked (37 open at the time of writing):** 17255, 17254, 17222, 17187, 16624,
16623, 16594, 16270, 15951, 15825, 15824, 15820, 15808, 15559, 15519, 15412, 15267, 15266,
15226, 15198, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415,
13355, 13287, 10993, 8175, 4075.
Changed-file lists were pulled for the ones that touch Kotlin under
`app/src/main/java`: 17255, 17254, 17222, 17187, 16624, 16623, 15951, 15825, 15824, 15820,
15808, 15198, 14650, 13848, 13415.

**hard-excluded files** (owned by a PR tagged `review` / `ready`):

| PR | label | files off-limits |
|----|-------|------------------|
| 17254 | review | `ui/teams/resources/TeamResourcesAdapter.kt`, `res/layout/row_team_resource.xml` |
| 17222 | ready | `services/SharedPrefManager.kt`, `test/.../services/SharedPrefManagerTest.kt` |
| 17187 | review | `ui/resources/ResourcesAdapter.kt`, `ResourcesFilterFragment.kt`, `ResourcesListFilter.kt`, `ResourcesViewModel.kt`, `utils/MediumUtils.kt`, `test/.../ResourcesListFilterTest.kt` |

**also avoided** (untagged but actively pushed, or large in-flight rewrites): `DashboardActivity.kt`
and `res/layout/app_bar_bell.xml` (17255, pushed today); `TeamsRepository.kt` /
`TeamsRepositoryImpl.kt` / `MyTeam.kt` / `data/room/AppDatabase.kt` / `ui/teams/**` (15951, 16623,
15825, 15820); `CoursesRepository.kt` / `CoursesRepositoryImpl.kt` / `utils/ResourcesSearchUtils.kt`
(16624); `ui/chat/ChatViewModel.kt` + `ChatDetailFragment.kt` (15198); `SurveysRepository.kt` /
`SurveysRepositoryImpl.kt` / `SubmissionViewModel.kt` / `BaseDashboardFragment.kt` (14650);
`UserProfileFragment.kt` / `BecomeMemberActivity.kt` / `UserInformationFragment.kt` /
`CourseFilterController.kt` (13848); **every `res/values*/strings.xml`** (touched by four open PRs).
No task below adds or edits a string resource, and none bumps the Room schema version.

**a note on PR 15808** (`sync: intelligent incremental sync via couchdb _changes feed`, **draft,
`WIP`**): it is a 50-file rewrite that touches most `*RepositoryImpl` files and 13 DAOs. Treating it
as a blocker leaves essentially no mergeable surface in the data layer, so it is *not* treated as
off-limits here. Tasks 5, 6, 7, 9 and 10 touch files it also touches; all five are additive or
delete-only edits in method bodies that PR does not rewrite, so a merge conflict would be textual
and trivial. PRs 13415 and 15198 are likewise not blockers: 13415 is written against the
pre-Room (Realm) tree and cannot apply to `fcb4c53` at all.

All ten tasks are independently mergeable in any order. No file appears in two tasks.

---

### 1. drop the `SharedPrefManager` parameter from `ConfigurationsRepository.checkVersion` (roadmap 1+4)

context: `ConfigurationsRepository.kt:15` declares `fun checkVersion(callback: CheckVersionCallback,
spm: SharedPrefManager)` — the repository interface takes a *service instance* as a call parameter,
so every caller has to own and hand over a `SharedPrefManager`. The implementation already injects
its own: `ConfigurationsRepositoryImpl.kt:49` has `private val sharedPrefManager: SharedPrefManager`
and uses it at `ConfigurationsRepositoryImpl.kt:108` (`sharedPrefManager.rawPreferences.getLong(...)`)
and `:113` (`sharedPrefManager.getVersionDetail()`) in the same function that receives `spm`. The
parameter is redundant, lets two different `SharedPrefManager` instances reach one call, and pins an
`org.ole.planet.myplanet.services` import into the repository interface (roadmap 9 wants that
package out of the core).
files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt` (line 15,
plus the now-unused `import org.ole.planet.myplanet.services.SharedPrefManager`);
`app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (`checkVersion`
at line 98, and the private helpers `fetchApkVersionString(spm)` at line 495 and the
`UrlUtils.getUpdateUrl(spm)` call at line 487 — both should use the injected field);
`app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` (line 233);
`app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` (line 183);
`app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt` (line 69);
`app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`
(lines 171, 221, 288). Do NOT touch `services/SharedPrefManager.kt` — PR 17222 (`ready`) owns it, and
do not change any other method on the interface.
steps:
1. Change the interface signature to `fun checkVersion(callback: CheckVersionCallback)` and delete the
   `SharedPrefManager` import from `ConfigurationsRepository.kt`.
2. In `ConfigurationsRepositoryImpl`, drop the `spm` parameter from `checkVersion` and from the private
   helpers that only forward it; replace every `spm` reference with the injected `sharedPrefManager`.
3. Update the three call sites to `configurationsRepository.checkVersion(this)` / `(this@AutoSyncWorker)`,
   and delete any local `prefData` / `sharedPrefManager` reference that becomes unused *only* because
   of this change (leave ones still used elsewhere in those files).
4. Update the three `repository.checkVersion(callback, sharedPrefManager)` calls in the test to the
   one-argument form; keep the existing assertions unchanged.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: launching the app on a stale version still shows the update dialog on the
login/sync screen, and a version check performed less than 24 h ago still serves the cached result
(no extra network call).
size budget: ~20 changed lines across 6 files (4 of them one-line call-site edits).
out of scope: no change to the caching policy, the 24-hour window, or `CheckVersionCallback`; do not
move `getCommunityLeaders` or any other method off this interface in the same PR.

---

### 2. take `UserRepository` out of the two viewer activities and put it behind `ResourceViewerViewModel` (roadmap 1+3+10)

context: `ResourceViewerActivity.kt:25` and `WebViewActivity.kt:48` each field-inject
`lateinit var userRepository: UserRepository` for one reason only: to hand it to
`ResourcesExitCoordinator(this, userRepository, viewModel)` (`ResourceViewerActivity.kt:28`,
`WebViewActivity.kt:51`). The coordinator's single use of it is
`userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }` at `ResourcesExitCoordinator.kt:25`.
Both activities already hold a `ResourceViewerViewModel`, which is the layer that is supposed to own
repository access — so two Activities reach past their ViewModel into the data layer to fetch one
string. Routing it through the ViewModel removes the last repository dependency from these two
Activities, which is exactly the shape a Compose migration (roadmap 6/10) needs.
files: `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourcesExitCoordinator.kt` (constructor
and `handleBackNavigation`); `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`
(add one suspend accessor next to the existing `shouldShowResourceRatingDialog`);
`app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerActivity.kt` (lines 24-28 and the
`javax.inject.Inject` / `UserRepository` imports);
`app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt` (lines 47-51 and the same
imports); `app/src/test/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModelTest.kt`.
Leave `ui/viewer/ResourceViewerFragment.kt` and `ui/ratings/RatingsFragment.kt` alone.
steps:
1. Add `private val userRepository: UserRepository` to `ResourceViewerViewModel`'s constructor and a
   `suspend fun getActiveUserId(): String? = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }`.
2. Delete the `userRepository` constructor parameter from `ResourcesExitCoordinator` and call
   `viewModel.getActiveUserId()` in `handleBackNavigation`; the surrounding null-check and
   `activity.finish()` path stay byte-for-byte the same.
3. Delete the `@Inject lateinit var userRepository` field, the `javax.inject.Inject` import (if now
   unused) and the `UserRepository` import from both activities, and update both
   `ResourcesExitCoordinator(this, viewModel)` constructions.
4. Add a `getActiveUserId` case to `ResourceViewerViewModelTest` (blank id and null user both yield
   `null`), mocking `UserRepository` the way the existing cases mock `RatingsRepository`.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: finishing a resource (PDF/video) and pressing back still shows the rating
dialog once for a user who has not rated it, and goes straight back for a guest or an already-rated
resource; the same holds for an HTML resource opened in `WebViewActivity`.
size budget: ~35 changed lines across 5 files.
out of scope: do not convert these Activities to Compose, and do not change when the rating dialog is
decided (`shouldShowResourceRatingDialog` keeps its current logic).

---

### 3. move the storage-breakdown disk scan out of the ViewModel into `ResourcesRepository` (roadmap 1+3+7)

context: `StorageBreakdownViewModel.kt:86` declares `internal open fun scanStorage(oleDir: File)` and
walks the whole `ole` tree at `StorageBreakdownViewModel.kt:94`
(`oleDir.walkTopDown().filter { it.isFile }.forEach { ... }`) to total bytes and file counts per
category. That is raw data access inside a ViewModel, and it duplicates a traversal the repository
already owns: `ResourcesRepositoryImpl.kt:860` runs the identical `oleDir.walkTopDown().filter { it.isFile }`
inside `getOfflineResourceItems` (`ResourcesRepositoryImpl.kt:844`). The settings screen therefore
walks the tree twice — once in the ViewModel for the summary, once through the repository when a
category is opened — and the ViewModel is the only place in `ui/` still doing file I/O of its own.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt` (add one method
next to `getOfflineResourceItems`); `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
(new override beside `getOfflineResourceItems`, reusing its `withContext(dispatcherProvider.io)` shape);
`app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownViewModel.kt` (delete
`scanStorage`, `ScanResult` and the `java.io.File` import; `loadStorage` calls the repository);
`app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragmentTest.kt` (the two
`scanStorage` fixture-tree cases at lines 57 and 107 move out; the `loadStorage`/`forceRefresh`
case at line 127 stays and verifies the repository call count instead);
`app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` (receives the
two moved fixture-tree cases). Do NOT touch `ui/settings/StorageCategories.kt`,
`StorageCategoryViewModel.kt` or `StorageCategoryDetailFragment.kt` — `getOfflineResourceItems` and
`deleteOfflineResources` keep their current signatures.
steps:
1. Add `suspend fun getStorageBreakdown(oleDirPath: String, categoryExtensions: List<Set<String>>, otherIndex: Int): StorageBreakdown`
   to `ResourcesRepository`, with `data class StorageBreakdown(val totalBytes: Long, val sizes: List<Long>, val fileCounts: List<Int>)`
   declared in the same file (mirroring how `OfflineResourceItem` is declared there).
2. Implement it in `ResourcesRepositoryImpl` by lifting the body of `StorageBreakdownViewModel.scanStorage`
   verbatim — same empty/non-directory short-circuit, same `extension.isEmpty() -> otherIndex` rule —
   wrapped in `withContext(dispatcherProvider.io)`.
3. In `StorageBreakdownViewModel`, inject `ResourcesRepository`, delete `scanStorage`/`ScanResult`, and
   have `loadStorage` call `resourcesRepository.getStorageBreakdown(FileUtils.getOlePath(context), categories.map { it.extensions }, StorageCategories.OTHER_INDEX)`.
4. Move the two fixture-tree tests to `ResourcesRepositoryImplTest` against the new method, and rewrite
   the `forceRefresh` test to `coVerify` the repository call count instead of spying `scanStorage`.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: Settings → storage breakdown still shows the same per-category sizes,
file counts, total and "available / total" line, and pull-to-refresh still rescans.
size budget: ~120 changed lines across 5 files.
out of scope: do not change the category list or the extension→category mapping; do not have the
repository resolve the `ole` path itself in this PR (the caller keeps passing it, as
`getOfflineResourceItems` already does).

---

### 4. delete the four unused resource-open overloads from `ActivitiesRepository` (roadmap 1+8)

context: `ActivitiesRepository.kt:35-38` exposes four methods —
`getResourceOpenCount(userName)`, `getResourceOpenCount(userName, type)`,
`getMostOpenedResource(userName)` and `getMostOpenedResource(userName, type)` — that no code outside
`ActivitiesRepositoryImpl` calls. A repo-wide grep for them over `app/src/main/java` returns only the
declarations and `ActivitiesRepositoryImpl.kt:196` / `:198`, where `getProfileActivityStats` uses them
internally; every UI caller goes through `getProfileActivityStats` instead. Four public methods of a
27-repository interface that nothing outside the class needs is exactly the surface this round is
meant to shrink, and the no-`type` overloads also drag `services.UserSessionManager.KEY_RESOURCE_OPEN`
into the contract.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt` (lines 35-38 only);
`app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` (lines 174-193 —
drop the `override` keyword on all four so they stay callable as plain members). Leave
`getOfflineVisitCount`, `getOfflineLoginCount`, `getProfileActivityStats` and
`data/room/dao/ResourceActivityDao.kt` untouched — they are live API and the DAO queries are already
`COUNT(*)` / `GROUP BY`.
steps:
1. Delete the four declarations from `ActivitiesRepository.kt`.
2. In `ActivitiesRepositoryImpl.kt`, remove `override` from the four matching functions and keep them
   as ordinary (public) members so `getProfileActivityStats` and the existing tests still compile —
   `ActivitiesRepositoryImplTest` declares `private lateinit var repository: ActivitiesRepositoryImpl`
   (line 72) and constructs the concrete class (line 103), so no test edit is needed.
3. Re-run a grep for the four names across `app/src/main` and `app/src/test` to confirm nothing
   resolves them through the interface type any more.
4. Do not otherwise reorder or reformat either file.
acceptance: `./gradlew testDefaultDebugUnitTest` green (the 14 existing references in
`ActivitiesRepositoryImplTest.kt` must still pass untouched); `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: the user profile screen still shows "most opened resource" and
"number of resources opened" with the same values as before.
size budget: ~8 changed lines across 2 files.
out of scope: do not merge the two remaining `getProfileActivityStats` queries, do not touch
`ProgressRepository.hasUserCompletedSync` (which forwards into this repository), and do not rename
anything.

---

### 5. tighten `FeedbackRepository`: drop the two internal-only builders and stop passing `UserEntity` into the list query (roadmap 1+3+9)

context: `FeedbackRepository.kt:8` (`createFeedback`) and `:29` (`saveFeedback`) are public interface
methods whose only callers are each other, inside
`FeedbackRepositoryImpl.createAndSaveFeedback` (`FeedbackRepositoryImpl.kt:32-33`); no UI or service
code calls them. Separately, `FeedbackRepository.kt:24` declares
`suspend fun getFeedback(userModel: UserEntity?): Flow<List<Feedback>>` — it is marked `suspend` even
though the body (`FeedbackRepositoryImpl.kt:73-84`) never suspends, it just picks a DAO `Flow`; and it
takes a whole `UserEntity` to read exactly two things, `userModel?.isManager()` and `userModel?.name`.
That forces `FeedbackListViewModel.kt:35-36` to fetch the full user entity before it can ask for a list.
files: `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt` (lines 8, 24, 29 and
the `UserEntity` import); `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt`
(lines 36, 73, 115); `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt`
(`loadFeedback`); `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModelTest.kt`
(lines 42, 64, 75, 87, 98, 106); `app/src/test/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImplTest.kt`
(lines 138, 154). Leave `ui/feedback/FeedbackComposerViewModel.kt` and
`data/room/dao/FeedbackDao.kt` untouched — `createAndSaveFeedback` keeps its signature and the DAO is
already correct.
steps:
1. Delete `createFeedback` and `saveFeedback` from the interface; in the impl, drop their `override`
   keywords and mark them `private` (both are only reached from `createAndSaveFeedback`).
2. Change the interface declaration to
   `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>` (no `suspend`) and
   delete the now-unused `UserEntity` import from `FeedbackRepository.kt`.
3. Update the impl to branch on the `isManager` flag and pass `ownerName` to `feedbackDao.getByOwnerFlow`;
   the `distinctByContent` comparator stays exactly as it is.
4. In `FeedbackListViewModel.loadFeedback`, keep the `userRepository.getUserModel()` call and pass
   `user?.name` and `user?.isManager() == true`.
5. Update the two test files to the new signature (`coEvery { feedbackRepository.getFeedback(...) }`
   becomes `every { ... }` since the function no longer suspends).
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: a normal user still sees only their own feedback, a manager still sees
all of it, and the list still refreshes in place after closing a ticket or posting a reply.
size budget: ~45 changed lines across 5 files.
out of scope: do not change `Feedback` the entity, do not touch the feedback upload path
(`getPendingFeedback` / `markFeedbackUploaded`), and do not introduce a feedback projection model.

---

### 6. move the health-examination upload sequence out of `UploadToShelfService` into `HealthRepository` (roadmap 1+5)

context: `UploadToShelfService.kt:70-77` and `:78-93` each hand-roll the same three-step data
sequence — `healthRepository.getUpdatedHealthExaminations()` (or `getUpdatedHealthForUser(userId)`),
then `healthRepository.uploadHealthData(myHealths)`, then
`healthRepository.markHealthExaminationsUploaded(uploadedHealths)`. A service is orchestrating a
read-modify-write across one repository, so the "which rows are pending, upload them, mark them
uploaded" rule lives in `services/` instead of in the data layer, and the three intermediate methods
have to stay public on `HealthRepository` (`HealthRepository.kt:15-20`) purely to let it do so. The
sequence is also duplicated with only the fetch differing.
files: `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt` (lines 14-19);
`app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt` (lines 58-68 and the
existing `uploadHealthData` at line 102); `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`
(`uploadHealth` and `uploadSingleUserHealth` bodies only);
`app/src/test/java/org/ole/planet/myplanet/repository/HealthRepositoryImplTest.kt`.
Leave `services/AutoSyncWorker.kt` (line 113), `services/UserDataWorker.kt` (line 54) and
`ui/sync/ProcessUserDataActivity.kt` (line 177) untouched — `uploadHealth()` and
`uploadSingleUserHealth(userId, listener)` keep their current public signatures and semantics.
steps:
1. Add `suspend fun syncPendingHealthExaminations()` and
   `suspend fun syncPendingHealthExaminationsForUser(userId: String)` to `HealthRepository`.
2. Implement both in `HealthRepositoryImpl` as the existing three-call sequence (fetch → `uploadHealthData`
   → `markHealthExaminationsUploaded`), returning early when the fetch is empty.
3. Remove `getUpdatedHealthExaminations`, `getUpdatedHealthForUser` and
   `markHealthExaminationsUploaded` from the `HealthRepository` interface and drop their `override`
   keywords in the impl (they stay as members; the existing tests construct `HealthRepositoryImpl`
   directly). Keep `uploadHealthData` on the interface — it is exercised on its own by
   `HealthRepositoryImplTest.uploadHealthData_successful_upload` (line 265).
4. Replace the bodies of `UploadToShelfService.uploadHealth` / `uploadSingleUserHealth` with a single
   repository call each, keeping the `appScope.launch(dispatcherProvider.io)` wrapper, the
   `userId.isNullOrEmpty()` guard and both `listener?.onSuccess(...)` messages verbatim.
5. Add two `HealthRepositoryImplTest` cases: pending rows get uploaded and marked; an empty fetch
   performs no upload call.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: after editing a health examination offline and then syncing, the record
reaches the server exactly once and stops being re-sent on the next sync; the per-user path from the
login/process-user-data screen still reports its success toast.
size budget: ~60 changed lines across 4 files.
out of scope: do not change how `ProcessUserDataActivity` builds the
`"org.couchdb.user:${userName}"` id, do not touch the `OnSuccessListener` callback style, and do not
merge `uploadUserData` / `uploadSingleUserData` in the same PR.

---

### 7. return a typed progress projection from `ProgressRepository` instead of parsing `JsonArray` in the ViewModel (roadmap 1+3+9)

context: `ProgressViewModel.kt:34` calls `progressRepository.fetchCourseData(user?.id)`, which returns
a raw `com.google.gson.JsonArray` (`ProgressRepository.kt:13`), and then the ViewModel does the
mapping itself at `ProgressViewModel.kt:38-56`: `element.asJsonObject`, `obj.get("courseId").asString`,
`obj.getAsJsonObject("progress")?.get("current")?.asInt`, plus a `TypeToken<Map<String, Int>>` Gson
parse for `stepMistake`. So the CouchDB document shape is decoded in the UI layer, `Gson` is injected
into a ViewModel, and `obj.get("courseId").asString` throws on a malformed document instead of being
handled where the data is produced. The target type `CoursesProgressRow` already exists in `model/`.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt` (add one method
beside `fetchCourseData` at line 13); `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`
(new override next to `fetchCourseData` at line 57); `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt`
(the whole `loadCourseData` body, plus the `Gson`, `TypeToken` and `DispatcherProvider` constructor
params if they become unused); `app/src/test/java/org/ole/planet/myplanet/ui/courses/ProgressViewModelTest.kt`
(lines 58, 66, 100). Leave `ui/dashboard/DashboardViewModel.kt` alone — it consumes
`fetchCourseData`'s `JsonArray` for a different purpose (line 326) and is owned by PR 14650, so
`fetchCourseData` itself stays exactly as it is.
steps:
1. Add `suspend fun getCourseProgressRows(userId: String?): List<CoursesProgressRow>` to
   `ProgressRepository`.
2. Implement it in `ProgressRepositoryImpl` by calling the existing `fetchCourseData(userId)` and
   moving the mapping over from the ViewModel unchanged, except that a row whose `courseId` or
   `courseName` is missing is skipped rather than throwing.
3. Reduce `ProgressViewModel.loadCourseData` to `_courseData.value = progressRepository.getCourseProgressRows(userRepository.getUserModel()?.id)`
   and delete the `gson`, `type` and any newly unused constructor parameter and imports.
4. Update `ProgressViewModelTest` to stub `getCourseProgressRows` with `CoursesProgressRow` values
   instead of building a `JsonArray`, keeping the existing assertions on the emitted list.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: the course-progress screen still lists every enrolled course with the
same current/max progress, mistake count and per-step mistake breakdown as before.
size budget: ~60 changed lines across 4 files.
out of scope: do not delete or change `fetchCourseData`, do not touch `findProgressForCourse`, and do
not change how progress is computed inside `getCourseProgress`.

---

### 8. remove the dead `getNotifications` from `NotificationsRepository` and route its timestamps through the injected `TimeProvider` (roadmap 1+8)

context: two things are wrong in the same pair of files. `NotificationsRepository.kt:22` declares
`getNotifications(userId, filter, isAdmin)`, but the only caller anywhere is
`NotificationsRepositoryImpl.kt:166` inside `getEnrichedNotifications`; the UI went over to
`getEnrichedNotifications` and the older method stayed on the interface. And although
`NotificationsRepositoryImpl.kt:31` injects `private val timeProvider: TimeProvider`, the class still
constructs wall-clock values directly with `java.util.Date()` at lines 107, 115, 128, 136 and 461 —
so read-receipt and creation timestamps bypass the abstraction the rest of the repository layer uses
and cannot be pinned in a test.
files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (line 22 and
the `NotificationPayload` import if it becomes unused);
`app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (line 140 and
the five `Date()` sites at 107, 115, 128, 136, 461);
`app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` (add one
case; the existing cases mock `notificationDao.getNotifications`, not the repository method, so they
keep passing). Leave `ui/notifications/NotificationsViewModel.kt` and
`data/room/dao/NotificationDao.kt` untouched.
steps:
1. Delete the `getNotifications` declaration from the interface and change the impl's
   `override suspend fun getNotifications` (line 140) to `private suspend fun getNotifications`.
2. Replace each `Date()` with `Date(timeProvider.now())`; at line 461 keep the existing
   `doc.get("time")?.let { Date(it.asLong) } ?: Date(timeProvider.now())` fallback shape.
3. Confirm `timeProvider` has no other unused-import or nullability fallout and that
   `java.util.Date` is still imported for the type itself.
4. Add a `NotificationsRepositoryImplTest` case that fixes `timeProvider.now()` to a constant and
   asserts `markNotificationsAsRead` passes that instant to `notificationDao.markAsRead`.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: the notifications screen still lists, groups and filters
(all / read / unread) identically, the unread badge still decrements when a notification is opened,
and "mark all as read" still clears the list under the unread filter.
size budget: ~15 changed lines across 3 files.
out of scope: do not touch `markNotificationAsRead` (the singular, `summary_`-prefix variant), do not
deduplicate `getJoinRequestTeamId` against the identically named method on `TeamsRepository`
(that file is owned by open PRs), and do not change `KNOWN_TYPES`.

---

### 9. answer "already shared?" with a SQL `EXISTS` instead of loading every matching news row (roadmap 1+7)

context: `VoicesRepositoryImpl.kt:76-79` implements `isAlreadyShared` as
`newsDao.getByNewsId(chatId).any { news -> news.viewIn?.contains("\"_id\":\"$viewInId\"", ignoreCase = true) == true }`.
`NewsDao.kt:71-72` backs that with `SELECT * FROM news WHERE newsId = :chatId`, so every matching row —
including the full `message`, `images` and `viewIn` JSON blobs — is read, converted through
`Converters` and allocated as a `News` object, just to produce a `Boolean`. It runs on the chat-share
path (`ChatViewModel.kt:275`) every time the user opens the share sheet for a conversation.
`getByNewsId` has no other caller anywhere in `app/src/main`, so it can be replaced outright.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt` (replace `getByNewsId` at
lines 71-72); `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`
(`isAlreadyShared` at lines 76-79 only);
`app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt` (new cases —
there is currently no test for `isAlreadyShared`). Do NOT touch
`app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt`: adding an index on
`news.newsId` would need a schema-version bump and that file is owned by open PRs 15951 and 15825.
steps:
1. Replace `getByNewsId` with
   `@Query("SELECT EXISTS(SELECT 1 FROM news WHERE newsId = :chatId AND viewIn LIKE :viewInPattern ESCAPE '\\')") suspend fun isSharedInView(chatId: String, viewInPattern: String): Boolean`,
   and add a KDoc line explaining the pattern the way `MyLibraryDao`'s header documents its
   `userPattern` convention.
2. In `isAlreadyShared`, build the pattern as `%"_id":"<escaped viewInId>"%`, escaping `\`, `%` and
   `_` with a backslash exactly as `ResourcesRepositoryImpl`/`UserRepositoryImpl` already do for their
   `LIKE` parameters, and return `newsDao.isSharedInView(chatId, pattern)`.
3. Keep the behaviour identical: SQLite `LIKE` is case-insensitive for ASCII, which matches the
   current `ignoreCase = true`.
4. Add `VoicesRepositoryImplTest` cases for a match, a non-match, and a `viewInId` containing `_`
   (which must not act as a wildcard).
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: in AI chat → share to a team/community, a conversation already shared to
that target still shows as "Already shared" and cannot be shared twice, and one not yet shared there
still shares successfully.
size budget: ~35 changed lines across 3 files.
out of scope: no Room schema or index change, no new `AppDatabase` version, and no change to how
`viewIn` is written when a chat is shared.

---

### 10. delete the `getRatingsById` wrapper from `RatingsRepository` (roadmap 1)

context: `RatingsRepository.kt:7` declares
`suspend fun getRatingsById(type: String, resourceId: String?, userId: String?): RatingSummary?`, and
`RatingsRepositoryImpl.kt:20-23` implements it as nothing but a null-guard over the method on the very
next line: `if (resourceId == null) return null; return getRatingSummary(type, resourceId, userId)`.
The interface therefore ships two entry points for one query, differing only in nullability, and the
nullable one has exactly one caller in the whole app — `ResourceDetailFragment.kt:268`. Four other
call sites (`ResourceViewerViewModel.kt:41`, `RatingSummaryProvider.kt:11`, `TakeCourseViewModel.kt:92`,
`RatingsViewModel.kt:65`) already use `getRatingSummary` directly.
files: `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt` (line 7);
`app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt` (lines 20-23);
`app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailFragment.kt` (`onRatingChanged`
at lines 267-282); `app/src/test/java/org/ole/planet/myplanet/repository/RatingsRepositoryImplTest.kt`
(the `getRatingsById returns specific aggregated rating summary` case at line 50). Do NOT touch
`ui/ratings/RatingsFragment.kt`, `ui/courses/RatingSummaryProvider.kt` or
`ui/viewer/ResourceViewerViewModel.kt`, and do not change `getRatingSummary`'s own signature.
steps:
1. Delete `getRatingsById` from `RatingsRepository.kt` and its implementation from
   `RatingsRepositoryImpl.kt`.
2. In `ResourceDetailFragment.onRatingChanged`, guard on the id first —
   `val resourceId = library.resourceId ?: return@launch` — then call
   `ratingsRepository.getRatingSummary("resource", resourceId, userModel?.id)`; `lastKnownRating` is
   already a `RatingSummary?` so nothing downstream changes, and the surrounding `try/catch` and
   `isAdded` guard stay as they are.
3. Rename the test case to target `getRatingSummary` with the same fixtures and assertions (the test
   builds `RatingsRepositoryImpl` directly, so no other test edit is needed).
4. Grep for `getRatingsById` across `app/src` afterwards to confirm no reference survives.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` green.
Behaviour to verify by hand: open a resource's detail screen, submit or change a rating — the average,
the total count and the user's own stars still refresh immediately, and a resource with no
`resourceId` still renders without crashing.
size budget: ~20 changed lines across 4 files.
out of scope: do not introduce a `ResourceDetailViewModel` in this PR, and do not move the remaining
direct repository injections out of `ResourceDetailFragment`.

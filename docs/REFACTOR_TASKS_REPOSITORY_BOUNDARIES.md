# myPlanet refactor round — repository boundaries

date: 2026-09-10 · base commit: `022ee7d` (`master`) · open PRs checked: 16989, 16988, 16987, 16986, 16690, 16624, 16623, 16594, 16270, 16101, 15951, 15825, 15824, 15820, 15808, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

File lists were pulled for every open PR whose label set or recent activity put it near this round's focus: #16989 (`review`), #16988 (`ready`), #16986, #16690, #16624, #16623, #16101, #15951, #15808. **No task below touches any file owned by those PRs.** The blocked set that shaped this plan:

- `repository/TeamsRepository.kt`, `repository/TeamsRepositoryImpl.kt`, `ui/teams/{TeamFragment,TeamViewModel,TeamsAdapter,PlanFragment}.kt`, `model/{MyTeam,TeamTask,TeamDetails,CreateTeamRequest,TeamUpdateRequest}.kt`, `data/room/AppDatabase.kt`, `utils/TimeUtils.kt` — #15951, #16623
- `ui/teams/tasks/*`, `callback/OnTaskCompletedListener.kt` — #16623
- `repository/{Courses,Submissions,Surveys,Ratings,Progress,Health,Tags,Voices}Repository{,Impl}.kt`, `repository/{User,Feedback,Chat,Community}RepositoryImpl.kt`, `di/RoomModule.kt`, `di/ServiceModule.kt`, `services/sync/TransactionSyncManager.kt`, and `data/room/dao/{Achievement,Certification,Chat,Course,CourseProgress,Exam,Feedback,HealthExamination,Meetup,Rating,SyncCursor,Tag,TeamTask}Dao.kt` — #15808
- `utils/ResourcesSearchUtils.kt`, `repository/CoursesRepository{,Impl}.kt` — #16624
- `base/BasePermissionActivity.kt`, `ui/chat/ChatDetailFragment.kt`, `ui/resources/AddResourceFragment.kt`, `ui/user/UserProfileFragment.kt` — #16986
- `utils/EdgeToEdgeUtils.kt` plus the `onCreate` of 15 activities incl. `ui/dashboard/DashboardActivity.kt`, `ui/voices/ReplyActivity.kt`, `ui/resources/AddResourceActivity.kt`, `ui/surveys/PublicSurveyActivity.kt`, `ui/health/HealthExaminationActivity.kt` — #16101
- `ui/onboarding/OnboardingActivity.kt` — #16690
- all `res/values*/strings.xml` and `res/drawable/{down_arrow,outline_keyboard_arrow_up_24}.xml` — #16988, #16989

Every task is independently mergeable in any order; no file appears in two tasks.

---

### 1. drop the two unused repository injections from BaseResourceFragment (roadmap 1+4+8)
context: `base/BaseResourceFragment.kt:61` and `:63` inject `SubmissionsRepository` and
`SurveysRepository` into the base class that every resource, course, voices, team and
recycler fragment inherits from, and neither field is read anywhere in the class or in
any subclass. Each of the three real users declares its own: `base/BaseExamFragment.kt:46`
(`submissionsRepository`), `ui/exam/ExamTakingFragment.kt:68` (`surveysRepository`),
`ui/exam/UserInformationFragment.kt:42`. The result is that opening any library or team
screen builds a submissions and a surveys repository it never calls — a survey/submission
dependency leaking into every resource screen through the base class.
files: `app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt` — remove the
`@Inject lateinit var submissionsRepository` (line 61) and `@Inject lateinit var surveysRepository`
(line 63) declarations and the two now-dead imports (lines 34-35). Leave
`userRepository`, `resourcesRepository`, `coursesRepository`, `sharedPrefManager`,
`broadcastService` and `timeProvider` alone — all of those are used in this file.
Do NOT touch `base/BaseExamFragment.kt` or `ui/exam/ExamTakingFragment.kt`; their own
declarations are the correct ones and must stay.
steps:
1. Delete lines 60-63 of `BaseResourceFragment.kt` (the two `@Inject` + `lateinit var` pairs).
2. Delete the `import org.ole.planet.myplanet.repository.SubmissionsRepository` and
   `import org.ole.planet.myplanet.repository.SurveysRepository` lines.
3. Compile the debug variant to prove no subclass resolved either field through inheritance.
4. Run the unit tests.
acceptance: `./gradlew assembleDefaultDebug` compiles and
`./gradlew testDefaultDebugUnitTest` stays green (`base/BaseResourceFragmentTest.kt` and
`base/BaseResourceFragmentTrackTest.kt` reference no repositories, so neither needs editing).
Manually: open Library, My Courses and a team's Resources tab — lists, the download dialog
and add/remove-from-library still behave as before.
size budget: ~6 removed lines, 1 file.
out of scope: no changes to `SubmissionsRepository`/`SurveysRepository` themselves, and do
not "tidy" the other injections in the same block.

---

### 2. tighten the ResourcesRepository surface: no pass-through, no pure folds, one id resolver (roadmap 1+3+9)
context: three separate boundary problems in one interface.
`repository/ResourcesRepository.kt:53` exposes `markResourceAdded`, whose implementation at
`repository/ResourcesRepositoryImpl.kt:307-309` is a one-line delegate to
`activitiesRepository.markResourceAdded` and which has **no caller outside the impl itself**
(the impl calls it at line 297).
`repository/ResourcesRepository.kt:95` exposes `suspend fun getFilterFacets(libraries: List<MyLibrary>)`,
implemented at `repository/ResourcesRepositoryImpl.kt:612-631` as a pure in-memory fold over
a list the *caller* supplies — it touches no DAO, no API and no preferences, yet it is a
`suspend` repository call that `ui/resources/ResourcesViewModel.kt:120-122` already wraps in
`withContext(dispatcherProvider.default)`, so the work hops dispatchers for nothing.
And `ui/resources/ResourceDetailFragment.kt:37-40` re-implements the repository's own
"id or resourceId" resolution rule in the UI (`getLibraryItemById(id) ?: getLibraryItemByResourceId(id)`),
duplicating a rule the impl already owns at lines 313 and 342-343.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt` — delete
  `markResourceAdded` (line 53) and `getFilterFacets` (line 95); add
  `suspend fun resolveLibraryItem(id: String): MyLibrary?`.
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — turn
  `markResourceAdded` (lines 307-309) into a `private suspend fun`, delete `getFilterFacets`
  (lines 612-631), add the `resolveLibraryItem` override delegating to
  `getLibraryItemById(id) ?: getLibraryItemByResourceId(id)`.
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` — make
  `getFilterFacets` (line 120) compute the four facet sets itself inside the existing
  `withContext(dispatcherProvider.default)` block instead of calling the repository.
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailFragment.kt` — make
  `fetchLibrary` (lines 37-40) a single `resourcesRepository.resolveLibraryItem(libraryId)` call.
Leave `ui/resources/ResourcesFragment.kt` alone: it calls `viewModel.getFilterFacets(...)`
at line 690 and that signature does not change. Leave `getLibraryItemById`,
`getLibraryItemByResourceId` and the impl's internal `getLibraryItemByResourceId(x) ?: getLibraryItemById(x)`
sites (lines 313, 342-343) as they are — their order is the reverse of the fragment's and
flipping it would change behavior.
steps:
1. Move the facet fold body from `ResourcesRepositoryImpl.getFilterFacets` into
   `ResourcesViewModel.getFilterFacets` verbatim; delete the repository copy and the
   interface declaration.
2. Add `resolveLibraryItem` to the interface and impl; rewrite `ResourceDetailFragment.fetchLibrary`
   to call it (the method can then be inlined at its single call site in `onViewCreated`).
3. Remove `markResourceAdded` from the interface and mark the impl's copy `private`.
4. Drop imports that go unused in each edited file.
5. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green — no existing test references
`getFilterFacets` or `markResourceAdded`, and `ResourcesRepositoryImplTest`/`ResourcesViewModelTest`
must still pass untouched. Manually: the Library filter sheet still offers the same
language/subject/medium/level options, and opening a resource detail from both the
Library list and from Add Resource still finds the resource.
size budget: ~55 changed lines, 4 files.
out of scope: no DAO changes, no new caching, and do not restructure the resource-list cache
methods (`getCachedResourceListModels`, `clearResourceListCache`) in the same PR.

---

### 3. give ActivitiesRepository one call for a member's visit stats (roadmap 1+3+9)
context: `ui/voices/VoicesActions.kt:211-231` is a UI helper object that takes an
`ActivitiesRepository` parameter and runs two independent repository queries itself —
`getOfflineVisitCount(userModel.id)` at line 223 and `getLastVisit(userModel.name)` at
line 224 — to fill in two strings on a member-detail fragment. Query orchestration for the
activity domain belongs behind that domain's repository, not in a `ui/` object, and the
same pair of calls is issued member-by-member elsewhere, so the bundled accessor has an
obvious second consumer.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt` — add a
  `data class MemberVisitStats(val offlineVisitCount: Int, val lastVisit: Long?)` next to
  the existing `ProfileActivityStats` (line 11) and declare
  `suspend fun getMemberVisitStats(userId: String?, userName: String): MemberVisitStats`
  after `getLastVisit` (line 27).
- `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` —
  implement it by reusing the existing `offlineActivityDao.countByUserIdAndType(...)` and
  `offlineActivityDao.getLastVisit(...)` calls already used by `getOfflineVisitCount`
  (lines 61-63) and `getLastVisit` (lines 143-145).
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt` — in
  `showMemberDetails`, replace the two calls with one `getMemberVisitStats` call and keep
  the existing `dateFormatter` formatting and the `"No logout record found"` fallback in
  the UI.
Keep `showMemberDetails`'s **signature unchanged** so its two callers,
`base/BaseVoicesFragment.kt:104` and `ui/voices/ReplyActivity.kt:194`, need no edit —
`ReplyActivity.kt` is owned by open PR #16101 and must not be touched. Do NOT remove
`getOfflineVisitCount` or `getLastVisit`; both still have other callers
(`ui/user/UserProfileViewModel.kt:139`, `ui/dashboard/DashboardViewModel.kt:112`).
steps:
1. Add `MemberVisitStats` and the interface method.
2. Implement it in `ActivitiesRepositoryImpl` from the two existing DAO calls.
3. Rewrite the two lines in `VoicesActions.showMemberDetails` to consume the single result.
4. Add a `MemberVisitStats` case to `app/src/test/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImplTest.kt`
   only if that file already covers `getOfflineVisitCount`; otherwise add no test file.
5. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: from Community Voices and
from a team's Voices tab, tap a member's avatar — the member-detail sheet still shows the
same offline-visit count and last-visit date (or "No logout record found" when there is none).
size budget: ~30 changed lines, 3 files.
out of scope: do not touch `repository/TeamsRepositoryImpl.kt` (open PRs own it) even though
its per-member loop is the natural second caller; no DAO query changes.

---

### 4. tighten the personals data surface (roadmap 1+8+9)
context: three loose ends on the personals boundary.
`repository/PersonalsRepository.kt:22` declares `suspend fun getPersonalResources(userId: String?): Flow<List<Personal>>` —
a `Flow`-returning accessor marked `suspend`, which the project's own rule forbids
("Reactive queries return `Flow<…>` (non-suspend, per Room's requirement)", `CLAUDE.md`),
and the impl at `repository/PersonalsRepositoryImpl.kt:47-55` suspends over nothing but a
null check and a DAO `Flow`.
`repository/PersonalsRepository.kt:27` exposes `uploadPersonalDocument`, which in production
is called only from `uploadPersonal` inside the same impl (line 100) — no other production
caller exists, so it is interface surface nobody outside the repository needs.
`data/room/dao/PersonalDao.kt:36-37` declares `@Update suspend fun update(item: Personal)`
with zero callers in `main` or `test`; all writes go through `updateFields` and
`updateUploadedStatus`.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt` — drop
  `suspend` from `getPersonalResources` (line 22); delete the `uploadPersonalDocument`
  declaration (line 27).
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` — drop
  `suspend` and `override` accordingly on line 47; keep `uploadPersonalDocument` as a
  **public non-override** `suspend fun` (line 74) so
  `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
  (lines 173-212, which calls it on the concrete `PersonalsRepositoryImpl`) still compiles.
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt` — delete the
  unused `@Update suspend fun update(item: Personal)` and the now-unused
  `androidx.room.Update` import.
Leave `data/room/dao/PersonalDao.kt`'s `findByDocId`/`findById` alone — `PersonalDaoTest`
uses them as assertion helpers. `ui/personals/PersonalsViewModel.kt` needs no edit: its
`flow { emitAll(...) }` builder still compiles against a non-suspend accessor.
steps:
1. Remove `suspend` from the interface and impl signatures of `getPersonalResources`.
2. Remove `uploadPersonalDocument` from the interface; strip `override` from the impl copy.
3. Delete `PersonalDao.update` and its import.
4. Run the unit tests, including `PersonalsRepositoryImplTest` and `PersonalDaoTest`.
acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: My Personals still lists
resources newest-first, editing a title/description still updates the row live, deleting
still removes it, and uploading a personal resource still reports success.
size budget: ~8 changed lines, 3 files.
out of scope: do not restructure `uploadPersonal`'s error strings or its attachment
handling, and do not de-suspend any other repository's `Flow` accessor here.

---

### 5. stop re-decrypting health examinations on the main thread (roadmap 1+7+8)
context: `ui/health/HealthExaminationAdapter.kt:62` already decrypts each examination's
payload off the main thread (`submitExaminations` runs on `dispatcherProvider.default`) but
throws the `JsonObject` away, keeping only the boolean `hasEncryptedData` (line 80,
declared line 42). `onBindViewHolder`'s click handler at line 120 then decrypts the very
same row a second time — AES-decrypt plus a Gson parse — synchronously on the main thread,
once per tap, to build the detail dialog.
files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt` —
change `HealthExaminationItem` (lines 37-43) to carry `val encrypted: JsonObject?` in place
of `hasEncryptedData`, populate it from the value already computed at line 62, and make the
line-119/120 click handler use `item.encrypted` directly. Leave `showAlert` and
`showEncryptedData` (lines 134-166) unchanged — they already take a `JsonObject`. Do not
touch `ui/health/HealthExaminationActivity.kt` (open PR #16101 owns it) or
`repository/HealthRepositoryImpl.kt` (open PR #15808 owns it).
steps:
1. Replace the `hasEncryptedData: Boolean` field with `encrypted: JsonObject?`.
2. In `submitExaminations`, pass the already-computed `encrypted` into the item instead of
   `encrypted != null`.
3. In `onBindViewHolder`, gate on `item.encrypted != null` and hand that object to
   `showAlert` — delete the second `getEncryptedDataAsJson` call.
4. Leave `DIFF_CALLBACK` (lines 172-187) comparing the same fields it compares today; it
   already compares `examination.data`, which is what the decrypted payload derives from,
   so do not add `encrypted` to the comparison.
5. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green (no test references this adapter).
Manually: in My Health → examinations, the list still shows vitals, self-examination
green/grey backgrounds and the examiner name; tapping a row still opens the dialog with
notes/diagnosis/treatments/medications populated, and rows with no encrypted payload still
do nothing on tap.
size budget: ~12 changed lines, 1 file.
out of scope: no changes to the decryption helper on the model, and no move of decryption
into `HealthRepository` — that repository is owned by an open PR.

---

### 6. read community leaders through ConfigurationsRepository instead of re-parsing prefs in the UI (roadmap 1+3+10)
context: `repository/ConfigurationsRepository.kt:18` already owns this read —
`getCommunityLeaders()`, implemented at `repository/ConfigurationsRepositoryImpl.kt:405-407`
as exactly `UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders())`. Two UI
files bypass it and inline the identical expression against raw `SharedPreferences`:
`ui/voices/VoicesFragment.kt:248` and `ui/teams/voices/TeamsVoicesFragment.kt:253`. That is
JSON parsing of user records happening in fragments, and it duplicates the repository's
parse rule in three places, so a change to the leaders payload has to be made three times.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt` — add
  `@Inject lateinit var configurationsRepository: ConfigurationsRepository` next to the
  existing `voicesRepository` injection (line 53) and replace line 248's
  `leadersList = UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders())`
  with `leadersList = configurationsRepository.getCommunityLeaders()`.
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt` — the
  same two edits against its `voicesRepository` injection (line 43) and line 253.
Both fragments keep `sharedPrefManager` — each still calls `setRepliedNewsId` on it on the
next line. Drop the `UserEntity` import only if it becomes unused in that file (it is used
3× in `VoicesFragment.kt`, 2× in `TeamsVoicesFragment.kt`, so check before deleting).
Leave `ui/voices/ReplyActivity.kt:151` alone even though it has the same duplicate —
open PR #16101 owns that file; and leave `repository/TeamsRepositoryImpl.kt:980` alone
(owned by #15951/#16623).
steps:
1. Add the injection and the import in both fragments.
2. Swap both `leadersList = …` expressions to the repository call (it is non-suspend, so the
   call sites need no coroutine change).
3. Remove any import left unused.
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: in Community Voices and in
a team's Voices tab, posts by community leaders still render with the leader badge/style,
and non-leader posts are unchanged.
size budget: ~10 changed lines, 2 files.
out of scope: do not change `ConfigurationsRepository`, `SharedPrefManager`, or
`UserEntity.parseLeadersJson`; do not migrate `ReplyActivity`.

---

### 7. remove the unused SubmissionsRepository edge from UploadManager (roadmap 4+5+8)
context: `services/UploadManager.kt:56` injects `SubmissionsRepository` (imported at line 27)
and the class never calls it — the only mentions in the file are the import and the
constructor parameter. It is a submissions dependency wired into the upload orchestrator
purely in the DI graph, which makes the upload workflow look coupled to submissions when it
is not (photo submission uploads go through the separately-injected `photoUploader`, which
holds its own `SubmissionsRepository`).
files:
- `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` — delete the
  `private val submissionsRepository: SubmissionsRepository,` parameter (line 56) and the
  import (line 27). Leave every other constructor parameter alone; all are used.
- `app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt` — remove the
  `submissionsRepository,` positional argument from the `UploadManager(...)` construction
  (line 107). Keep the test's own `submissionsRepository` field (line 66): it is still
  needed for the `PhotoUploader(...)` construction at line 102 and for the
  `getUnuploadedPhotos`/`markPhotosUploadedBatch`/`getPhotosByIds` stubs the photo tests use.
Do NOT touch `services/upload/PhotoUploader.kt` — its `SubmissionsRepository` is genuinely used.
steps:
1. Drop the parameter and import from `UploadManager`.
2. Drop the matching positional argument in `UploadManagerTest`.
3. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green — in particular the photo-upload
cases in `UploadManagerTest` (`uploadSubmitPhotos`) must still pass unchanged.
Manually: trigger a sync with pending uploads — activities, submissions, photos and news
still upload as before.
size budget: ~3 changed lines, 2 files.
out of scope: no reordering of the remaining constructor parameters, and no changes to
`UploadCoordinator` or `UploadConfigs`.

---

### 8. replace the team-notification read-modify-write with one UPDATE (roadmap 1+7)
context: `repository/NotificationsRepositoryImpl.kt:285-299` updates a team's last-seen
voice count by first pulling the whole row (`teamNotificationDao.findByParentAndType`,
`data/room/dao/TeamNotificationDao.kt:12-13`, a `SELECT *`), mutating the entity in Kotlin,
and writing it back via `@Update` — three round trips and a lost-update window for what is
one integer column. Room can express it as a single conditional `UPDATE`, with an insert
only when no row matched.
files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamNotificationDao.kt` — add
  `@Query("UPDATE team_notification SET lastCount = :count WHERE parentId = :parentId AND type = :type") suspend fun updateCount(parentId: String, type: String, count: Int): Int`
  and delete both `findByParentAndType` (lines 12-13) and `@Update suspend fun update(item: TeamNotification)`
  (lines 21-22) plus the `androidx.room.Update` import: `updateTeamNotification` at
  `NotificationsRepositoryImpl.kt:286` is the only caller of `findByParentAndType` anywhere
  in `app/src`, and `update` has none at all.
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` —
  rewrite `updateTeamNotification` (lines 285-299) as: if `teamNotificationDao.updateCount(teamId, "chat", count)`
  returns `0`, build the `TeamNotification` exactly as today (random UUID `id`, `parentId`,
  `type = "chat"`, `lastCount`) and `insert` it.
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` —
  add two cases: an existing row takes the `updateCount` path with no `insert`, and a
  missing row (`updateCount` returns 0) inserts a row carrying the given count.
Leave `getTeamNotifications` (line 301 onward) untouched — it reads through
`getByTypeAndParentIds` and is unaffected.
steps:
1. Add `updateCount` to the DAO; remove the unused `@Update` method and import.
2. Rewrite `updateTeamNotification` to the update-then-insert form.
3. Add the two tests.
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green with the two new cases passing.
Manually: open a team's Voices tab, note the unread-voices badge clears; leave and re-enter
after a new post — the badge reappears with the right count, and the count does not reset
when the tab is opened twice in a row.
size budget: ~25 changed lines, 3 files.
out of scope: no change to `updateResourceNotification`/`updateStorageNotification`, and do
not move the team-task reads in this impl behind `TeamsNotificationsRepository` — that
needs `TeamsRepositoryImpl`, which open PRs own.

---

### 9. drop the unused DispatcherProvider from the voices label delegate and NewsViewModel (roadmap 4+8+9)
context: `ui/voices/LabelManipulator.kt:13` takes a `DispatcherProvider` into
`DefaultLabelManipulator` and never uses it — both overrides are bare pass-throughs to
`voicesRepository`. Two ViewModels thread the dependency through only to satisfy that
constructor: `ui/voices/VoicesViewModel.kt:39` and `ui/teams/voices/TeamsVoicesViewModel.kt:38`,
and for `TeamsVoicesViewModel` the delegate is the *only* use of its `dispatcherProvider`
(line 34), so the whole parameter is dead there. Separately,
`ui/voices/NewsViewModel.kt:18` injects a `DispatcherProvider` that the class never reads.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/LabelManipulator.kt` — remove the
  `dispatcherProvider` parameter (line 13) and the `DispatcherProvider` import.
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt` — remove the
  argument from the `DefaultLabelManipulator(...)` delegation on line 39 and **keep** the
  `dispatcherProvider` constructor parameter (line 36): it is still used at lines 65 and 192.
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt` —
  remove the argument from the delegation on line 38 and then remove the now-unused
  `dispatcherProvider` parameter (line 34) and its import.
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt` — remove the
  `dispatcherProvider` parameter (line 18) and its import.
Leave `ui/teams/tasks/TeamsTasksViewModel.kt` alone — it has the same unused
`dispatcherProvider`, but open PR #16623 owns that file.
steps:
1. Strip the parameter from `DefaultLabelManipulator` and fix both delegation sites.
2. Strip the now-dead parameter and import from `TeamsVoicesViewModel` and `NewsViewModel`.
3. Update any test that constructs these ViewModels or `DefaultLabelManipulator` positionally.
4. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: adding and removing a
label chip on a post still works in both Community Voices and a team's Voices tab, and the
voices image cleanup that `NewsViewModel.getPrivateImageUrlsCreatedAfter` feeds still runs.
size budget: ~10 changed lines, 4 files (plus test constructor fixes if any exist).
out of scope: do not collapse `LabelManipulator`/`DefaultLabelManipulator` into the
ViewModels, and do not touch `services/VoicesLabelManager.kt`.

---

### 10. hoist UserInformationFragment's repository access into a ViewModel (roadmap 3+1+10)
context: `ui/exam/UserInformationFragment.kt` is a `BaseDialogFragment` that injects two
repositories directly — `submissionsRepository` (line 42) and `userRepository` (line 44) —
and drives them from `viewLifecycleOwner.lifecycleScope` at line 163
(`userRepository.updateProfileFields`) and line 271
(`submissionsRepository.markSubmissionComplete`). Both writes die with the dialog on
rotation, and the survey-profile flow is the only screen in `ui/exam/` with no ViewModel,
so its state cannot be hoisted for a later Compose migration.
files:
- new `app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationViewModel.kt` — a
  `@HiltViewModel` taking `SubmissionsRepository` and `UserRepository`, exposing
  `fun updateProfile(userId: String?, profile: JsonObject)` and
  `fun markSubmissionComplete(submissionId: String, profile: JsonObject)`, each launching in
  `viewModelScope` and emitting the outcome on a `MutableSharedFlow<Result<Unit>>` (one flow
  per operation, or one flow carrying a small sealed result — pick one and use it for both).
- `app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt` — delete the
  two `@Inject` repository fields (lines 41-44) and their imports, obtain the ViewModel with
  `by viewModels()`, collect the result flow in `onViewCreated` next to the existing
  `lifecycleScope.launch` (lines 79-82) and keep every toast/`dialog?.dismiss()` branch
  currently inside `submitForm` (lines 150-173) and `saveSubmission` (lines 257-290) in the
  fragment, driven by the collected result.
Keep `userSessionManager` (line 46), `sharedPrefManager`, `submissionsUploader` and
`createUserProfile()` in the fragment untouched — `userModel` still comes from
`userSessionManager.getUserModel()` at line 81. Do NOT touch
`base/BaseExamFragment.kt` or `ui/exam/ExamTakingFragment.kt`.
steps:
1. Add `UserInformationViewModel` with the two operations and the result flow.
2. Replace the fragment's two repository call sites with ViewModel calls.
3. Collect the result flow with `viewLifecycleOwner.lifecycleScope` and move the existing
   success/failure toasts and dismissals into the collector.
4. Delete the two `@Inject` fields and the two repository imports.
5. Run the unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: from a team survey, submit
the user-information dialog — the "thank you for taking this survey" toast still appears and
the dialog dismisses; from the profile path (no `teamId`, empty `id`), submitting still shows
"user profile updated" (or the failure toast) and dismisses; rotating the device
mid-submission no longer loses the write.
size budget: ~90 changed lines (~55 new), 2 files.
out of scope: do not restructure `createUserProfile()`/`UserSurveyProfile`, do not move
`submissionsUploader` behind the ViewModel, and add no new test file — the existing suite
must simply keep passing.

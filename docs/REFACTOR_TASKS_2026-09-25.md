# Refactor round work orders — repository boundaries

date: 2026-09-25
base commit: 354410d0ae49734313dc01d8d90a35e0240ec787 (master)
open PRs checked: all 100 open PRs — 13415, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 15108, 15226, 15266, 15267, 15559, 15808, 15820, 15824, 15825, 15951, 16594, 16623, 17187, 17254, 17435, 17537, 17538, 17539, 17540, 17541, 17542, 17543, 17544, 17545, 17550, 17551, 17560, 17561, 17562, 17563, 17564, 17565, 17569, 17570, 17571, 17575, 17576, 17577, 17578, 17579, 17580, 17586, 17587, 17589, 17592, 17593, 17594, 17595, 17596, 17602, 17603, 17604, 17609, 17610, 17612, 17613, 17616, 17618, 17619, 17620, 17621, 17622, 17629, 17630, 17631, 17632, 17633, 17634, 17638, 17643, 17644, 17647, 17648, 17652, 17653, 17654, 17655, 17659, 17661, 17662, 17663, 17664, 17665, 17666, 17667, 17668, 17670, 17671, 17672, 17673, 17674, 17679

How off-limits was applied: no task touches a file that appears in any open PR labeled ready, merge, review, or change. A few tasks touch files that appear only in stale brainstorm/WIP/on-hold PRs — each such task says so and asks you to verify that PR got no new commits before starting. If it did, skip the task.

Round focus covered: repository boundaries between layers, cross-feature data leaks, moving data functions out of UI/services, Room/DAO optimizations, smoother repository-viewmodel relationships. Roadmap items 2, 4 and 6 are not covered this round.

### 1. Route PublicSurveyActivity's repository calls through a new PublicSurveyViewModel (roadmap 3+10)

context: `ui/surveys/PublicSurveyActivity.kt` injects `SurveysRepository` (line 37) and `SubmissionsRepository` (line 39) and runs data work straight from `lifecycleScope.launch`: `surveysRepository.fetchPublicSurvey(code)` at line 84, `saveSurveyFromPublicApi(...)` at 95, `submissionsRepository.getLatestSubmissionByParentId(surveyId)` at 121, `submitPublicSurvey(...)` at 136 and `surveysRepository.getExamQuestions(examId)` at 167. The activity has no ViewModel, so its state cannot survive rotation and cannot be unit tested — exactly what roadmap 3 fixes and what 10 needs for portable screens.

files: app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt. Do NOT touch `ui/exam/ExamTakingFragment.kt` (a different task owns it) or `base/BaseExamFragment.kt`.

steps:
1. Create `@HiltViewModel class PublicSurveyViewModel` injecting `SurveysRepository` and `SubmissionsRepository` — keep `IoDispatcher` use consistent with other ViewModels (see `ui/surveys/SurveysViewModel.kt`).
2. Move the load path (`loadSurvey` lines 82-101) into a ViewModel function that returns the loaded survey or an error state; expose it via `StateFlow`.
3. Move the submission path (`submitCompletedSubmission` lines 118-146, `getExamQuestions`/`buildPublicAnswers` lines 165-180) into a ViewModel function taking `surveyId`/`surveyDocId`/`launchTime` and returning the saved submission result.
4. In the activity, replace `surveysRepository`/`submissionsRepository` injections and the `lifecycleScope.launch` data calls with `private val viewModel: PublicSurveyViewModel by viewModels()` and calls that observe the ViewModel's state.
5. Keep the toasts and `navigateOnwardAndFinish` (lines 147-159, routes to `DashboardActivity` or `LoginActivity`) in the activity.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening a public survey link still loads the survey, finishing it still shows the "survey_submitted" toast and lands on `DashboardActivity` (logged in) or `LoginActivity` (logged out).

size budget: ~120 changed lines, 2 files.

out of scope: no repository interface changes, no Compose work, no changes to the exam-taking flow (`BaseExamFragment`/`ExamTakingFragment` are owned by a different task).

---

### 2. Route AddResourceActivity's library-resource calls through a new AddLocalResourceViewModel (roadmap 3+10)

context: `ui/resources/AddResourceActivity.kt` injects `ResourcesRepository` (line 38) and calls `resourceTitleExists(title)` at 112, `getResourceById(resourceId)` at 124, `updateLocalResource(...)` at 150 and `saveLocalResource(request)` at 195. The existing `AddResourceViewModel.kt` is scoped to the personals upload flow (`AddResourceFragment` uses it via `PersonalsRepository`); bolting library-resource methods onto it would blur the repository boundary this round is about, so the activity gets its own ViewModel.

files: app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/ui/resources/AddLocalResourceViewModel.kt. Do NOT touch `ui/resources/AddResourceViewModel.kt` or `ui/resources/AddResourceFragment.kt` — the personals flow is unrelated.

steps:
1. Create `@HiltViewModel class AddLocalResourceViewModel` injecting `ResourcesRepository`.
2. Add a `checkTitle(title)` ViewModel method wrapping `resourceTitleExists`, and make the activity's duplicate-title branch (lines 111-116) read from ViewModel state instead of calling the repository inline.
3. Move `getResourceById` (line 124) plus the `updateLocalResource` call (lines 150-161) into a ViewModel `updateResource(resourceId, request)` method.
4. Move `saveLocalResource` (line 195) into a ViewModel `saveResource(request)` method; keep the `UploadResult` toasts and `finish()` in the activity.
5. Replace the `ResourcesRepository` injection with `by viewModels()`.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; adding a resource from the detail screen still saves it locally, editing a resource still updates it, and the "resource already exists" duplicate check still fires.

size budget: ~110 changed lines, 2 files.

out of scope: no changes to `ResourcesRepository`, no navigation changes, nothing under `ui/resources/personal` or the personals upload path. `AddResourceActivity.kt` appears in stale on-hold PR #13848 — verify it got no new commits before starting.

---

### 3. Route TeamDetailFragment's direct teamsRepository calls through the existing TeamViewModel (roadmap 3)

context: `ui/teams/TeamDetailFragment.kt` holds `private val teamViewModel: TeamViewModel by viewModels()` (line 50) yet calls `teamsRepository` directly 11 times: `getTeamByIdOrTeamId` at 117 and 356, `getTeamById` at 123 and 357, `hasPendingRequest` at 146, `getJoinedMemberCount` at 181/379/397, `requestToJoin` at 276, `recordTeamActivity` at 279, `leaveTeam` at 298 and `logTeamVisit` at 433. `requestToJoin` and `leaveTeam` already exist on `TeamViewModel` (lines 122 and 143), so the fragment is bypassing its own ViewModel — the "smoother repository-viewmodel relationship" leak this round targets.

files: app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt. Do NOT touch `TeamsRepository.kt`, `TeamsRepositoryImpl.kt` (both owned by open PRs) or `base/BaseTeamFragment.kt` (a parent used by other screens).

steps:
1. In `TeamViewModel`, add thin delegating methods for the repository calls the fragment needs but the ViewModel lacks: `getTeamByIdOrTeamId`, `getTeamById`, `hasPendingRequest`, `getJoinedMemberCount`, `recordTeamActivity`, `logTeamVisit` — same signatures the repository exposes, no reshaping.
2. Swap every `teamsRepository.` call site in `TeamDetailFragment` to `teamViewModel.`.
3. For `requestToJoin` (line 276) and `leaveTeam` (line 298) call the existing `TeamViewModel` methods — do not add duplicates.
4. Remove the `teamsRepository` field injection from the fragment and prune unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; on a team detail screen, the member count, join-button state (pending vs joinable vs leave) and leaving the team all still behave the same.

size budget: ~120 changed lines, 2 files.

out of scope: no repository or DAO changes, no work in `PlanFragment` or `BaseTeamFragment`. `TeamDetailFragment.kt` appears in stale brainstorm PR #14883 and `TeamViewModel.kt` in stale PR #15951 — verify both got no new commits before starting.

---

### 4. Introduce ExamTakingViewModel for the exam and survey taking flow (roadmap 3+9)

context: `base/BaseExamFragment.kt` injects `SubmissionsRepository` (line 46) used at 88 (`getSubmissionById`), 104/106 (`getExamByStepId`/`getExamById` inside `initExam`), 158 (`updateSubmissionStatus`) and 179 (`addSubmissionPhoto`). `ui/exam/ExamTakingFragment.kt` additionally injects `CoursesRepository` and `SurveysRepository` (lines 66-68) for `getExamQuestions` (102), `getSubmissionsByParentId` (106), `isCourseCertified` (113), `startExamSession` (127/154/166), `saveExamAnswer` (677) and `updateCourseProgress` (850). Three repository types called straight from fragments is the heaviest UI->data leak left in the exam flow.

files: app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingViewModel.kt. Do NOT touch `ui/surveys/PublicSurveyActivity.kt` (task 1 owns it) or any repository file.

steps:
1. Create `@HiltViewModel class ExamTakingViewModel` injecting `SubmissionsRepository`, `SurveysRepository` and `CoursesRepository`.
2. Add ViewModel methods covering the BaseExamFragment calls: submission lookup by id, exam lookup by step id and by exam id, `updateSubmissionStatus`, `addSubmissionPhoto`.
3. Add ViewModel methods covering the ExamTakingFragment calls: `getExamQuestions`, `getSubmissionsByParentId`, `isCourseCertified`, `startExamSession`, `saveExamAnswer`, `updateCourseProgress`.
4. Replace all repository call sites in both fragments with `examTakingViewModel.` calls (`by viewModels()` in each fragment) and drop the injected repository fields.
5. Keep all UI-only code (view bindings, photo picker, toasts, pager navigation) in the fragments.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; taking an exam end-to-end still works (questions render, answers persist per page, submission status updates on finish) and taking a survey still records answers and course progress.

size budget: ~150 changed lines, 3 files.

out of scope: no repository interface changes, no changes to `SubmissionsRepository` exporters or the submission photo pipeline. `ExamTakingFragment.kt` appears in stale PRs #14650 and #15559 — verify they got no new commits before starting.

---

### 5. Move resource-open tracking and download-url resolution out of BaseContainerFragment (roadmap 3+10)

context: `base/BaseContainerFragment.kt` — the parent of the library-item detail screens — does data work inline: `userRepository.getUserModel()` inside `initRatingView` (line 152), `resourcesRepository.reconcileHtmlResourceOffline(item)` at 200, `resourcesRepository.trackResourceOpen(...)` at 202, 243, 255 and 259, `resourcesRepository.getHtmlResourceDownloadUrls(itemId)` at 218 (with the `ResourceUrlsResponse` when-branches at 219-231) and `resourcesRepository.getLibraryItemsByLocalAddress(localAddress)` at 238. These repository calls belong in a ViewModel so subclasses inherit a clean open-resource flow and the screen's state can survive rotation — groundwork for a portable compose resource viewer later.

files: app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceOpenViewModel.kt. Do NOT touch any subclass (e.g. `ui/resources/ResourceDetailFragment.kt`) or `ResourcesRepository` files.

steps:
1. Create `@HiltViewModel class ResourceOpenViewModel` injecting `ResourcesRepository` and `UserRepository` (check the constructor signatures on the interfaces before wiring).
2. Add methods `trackOpen(itemId)`, `reconcileHtmlOffline(item)`, `resolveHtmlDownloadUrls(resourceId)` returning the `ResourceUrlsResponse`, `findByLocalAddress(localAddress)` and `isGuestUser()`.
3. Replace the repository calls at lines 152, 200, 202, 218, 238, 243, 255 and 259 with ViewModel calls; keep the `ResourceOpener`/`startActivity` intent handling where it is.
4. Move the `ResourceUrlsResponse` when-branch resolution (lines 219-231) into the ViewModel so the fragment only gets a "open offline / download needed / can't open" outcome.
5. Remove the `resourcesRepository`/`userRepository` field injections used only for these calls and prune unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening a resource from the library still records the open, resolves online-vs-offline HTML correctly and launches the viewer or download as before.

size budget: ~130 changed lines, 2 files.

out of scope: no changes to the offline-reconcile logic itself, no `ResourceOpener` rewrites, no subclass edits.

---

### 6. Extract the news image-upload pipeline out of UploadManager (roadmap 5)

context: `services/UploadManager.kt` still contains the only non-coordinator upload flow: `uploadNews` (lines 271-369) iterates news items, builds image docs via `createImage` (lines 122-138, which reaches `NetworkUtils.getDeviceName()` / `NetworkUtils.getCustomDeviceName(MainApplication.context)`), POSTs each image to `/resources`, uploads the attachment, then pushes the news doc through `BulkDocsUploader` to `news/_bulk_docs`. That is ~100 lines of bespoke image-first orchestration living inside a service that is otherwise a thin caller of `UploadCoordinator` — splitting it out is the sync/upload consolidation roadmap asks for.

files: app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/services/upload/NewsImageUploader.kt. Do NOT touch `UploadCoordinator`, `BulkDocsUploader` or any repository file.

steps:
1. Create `NewsImageUploader` as an injectable class under `services/upload/` taking the same collaborators the current code uses (`uploadRepository`, `voicesRepository`/news lookup, `RetryQueue`, `Gson`, `TimeProvider`, `DispatcherProvider` — read the actual `uploadNews` body and copy the dependency set exactly).
2. Move `createImage` and the per-image POST-plus-attachment logic out of `UploadManager` into `NewsImageUploader`, keeping identical request shapes and error handling.
3. Move the `news/_bulk_docs` push and the `markNewsUploaded`/`queueNewsRetry` handling with it.
4. Reduce `UploadManager.uploadNews` to delegating the batch iteration to `NewsImageUploader`.
5. Verify nothing else in `UploadManager` referenced the moved helpers (search the file for `createImage` before deleting it).

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; publishing a voice post with an attached image while online still uploads the image under `/resources`, attaches it to the news doc, and marks the news item uploaded; with no connectivity the news item still lands in the retry queue.

size budget: ~130 changed lines, 2 files.

out of scope: no behavior changes to the upload protocol, no changes to `UploadCoordinator`, no sync-activity work.

---

### 7. Move SyncActivity's post-login download bootstrap into ResourceDownloadCoordinator (roadmap 5+3)

context: after a successful sync, `ui/sync/SyncActivity.kt` does its own data resolution inline: `configurationsRepository.getQueuedDownloads()` at line 566 feeding `openDownloadService`, then `prefData.getBetaAutoDownload()` at 571 branching into `resourcesRepository.getAllLibrariesToSync()` at 575 whose result feeds `resourceDownloadCoordinator.startBackgroundDownload(...)` at 576. The activity already delegates downloads to `services/ResourceDownloadCoordinator.kt` (injected at line ~29), so the link-resolution part should live there too — it is a data fetch, not UI logic.

files: app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt, app/src/main/java/org/ole/planet/myplanet/services/ResourceDownloadCoordinator.kt. Do NOT touch `SyncRepository`, `ConfigurationsRepository` impl files, or `DownloadUtils`.

steps:
1. In `ResourceDownloadCoordinator`, inject `ConfigurationsRepository`, `ResourcesRepository` and `SharedPrefManager` (match the property types used in `SyncActivity` — check `prefData`'s type).
2. Add a method e.g. `runPostSyncDownloads()` that performs the current block: `getQueuedDownloads()` -> `openDownloadService`, then `getBetaAutoDownload()` -> `getAllLibrariesToSync()` -> `startBackgroundDownload`, preserving order and conditions.
3. Replace lines ~560-577 in `SyncActivity` with a single `resourceDownloadCoordinator.runPostSyncDownloads()` call inside the same launch block.
4. Remove now-unused injections/imports from `SyncActivity` (`configurationsRepository`, `resourcesRepository` only if no other call sites remain — grep the file first; if other uses exist, keep them).

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; with beta auto-download enabled and queued downloads present, finishing a sync still kicks off exactly the same downloads.

size budget: ~70 changed lines, 2 files.

out of scope: no changes to `startBackgroundDownload`'s internals, no work on `ProcessUserDataActivity` (task 8 owns it), no navigation changes.

---

### 8. Move ProcessUserDataActivity's upload and security flows into a new ProcessUserDataViewModel (roadmap 3+5)

context: `ui/sync/ProcessUserDataActivity.kt` calls `syncRepository.uploadLoginData()` at line 193 (collecting `SyncUiState` with `takeWhile`), `uploadBulkData()` at 211, and `userRepository.fetchUserSecurityData(name)` at 254 inside `fetchAndLogUserSecurityData` — suspend orchestration and repository access inside a base activity. A ViewModel here continues roadmap 3 and isolates the sync-side upload flow for later consolidation under roadmap 5.

files: app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataViewModel.kt. Do NOT touch `SyncActivity.kt` or `ResourceDownloadCoordinator.kt` (task 7 owns them), and do NOT touch any subclass of `ProcessUserDataActivity`.

steps:
1. Create `@HiltViewModel class ProcessUserDataViewModel` injecting `SyncRepository` and `UserRepository`.
2. Expose `uploadLoginData()` and `uploadBulkData()` as ViewModel methods returning the same `Flow`/`SyncUiState` the activity currently collects.
3. Expose `fetchUserSecurityData(name)` wrapping `userRepository.fetchUserSecurityData`.
4. Replace the three call sites (lines ~192-225 and ~251-265) with ViewModel calls; keep the `takeWhile`, UI-state handling and toasts in the activity.
5. Drop the `syncRepository`/`userRepository` injections from the activity if they have no remaining call sites (grep first).

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the "process user data" sync screen still runs login-data upload, bulk upload and the security-data fetch and shows the same progress/toasts.

size budget: ~90 changed lines, 2 files.

out of scope: no `SyncRepository` changes, no subclass edits, no notification/settings work in this activity.

---

### 9. Push chat-history ordering and rev lookup into ChatDao queries, covered by a new ChatDaoTest (roadmap 1+7+8)

context: `repository/ChatRepositoryImpl.kt` `getChatHistoryForUser` (lines 123-134) loads every chat row for the user then sorts in memory via `sortChats` on `max(createdDate, updatedDate)`; `getLatestRev` (lines 137-140) SELECTs full rows through `ChatDao.getByDocId` just to read `_rev` and `maxByOrNull` the numeric prefix. `data/room/dao/ChatDao.kt` `getByUser` (lines 12-13) has no `ORDER BY` and `getByDocId` (lines 15-16) fetches full rows. Room can do both in SQL — a classic data-layer cleanup plus a performance hotspot, and there is no `ChatDaoTest` yet (17 DAO tests exist under `app/src/test/java/org/ole/planet/myplanet/data/room/dao/`).

files: app/src/main/java/org/ole/planet/myplanet/data/room/dao/ChatDao.kt, app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt, plus a new file app/src/test/java/org/ole/planet/myplanet/data/room/dao/ChatDaoTest.kt. Do NOT touch `ChatRepository.kt` (the interface) or any chat UI/ViewModel file.

steps:
1. Add `ORDER BY` to `ChatDao.getByUser` matching the in-memory semantics: order by the max of `createdDate`/`updatedDate` descending (check `model/ChatHistory.kt` field types — they are `String?` timestamps, so mirror the current comparison, e.g. `ORDER BY MAX(COALESCE(createdDate, ''), COALESCE(updatedDate, '')) DESC` after confirming how dates are stored).
2. Add a projection query `SELECT _rev FROM chat_history WHERE _id = :docId` to `ChatDao` and switch `ChatRepositoryImpl.getLatestRev` to it, keeping the numeric-prefix `maxByOrNull` logic in Kotlin.
3. Delete `sortChats` and the `getByDocId` row fetch from `ChatRepositoryImpl`; keep `getByDocId` on the DAO only if another caller exists (grep first).
4. Write `ChatDaoTest` following the existing pattern (`Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()`, `@RunWith(AndroidJUnit4::class)`, `runBlocking`) covering: ordering by newest activity across two docs, `_rev` projection returning only revisions, empty list for unknown user.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green including the new `ChatDaoTest`; the chat history screen shows the same ordering as before.

size budget: ~90 changed lines, 3 files.

out of scope: no schema or migration changes, no changes to any other DAO, no chat UI work. Both files appear only in stale WIP PR #15808 — verify it got no new commits before starting.

---

### 10. Stop VoicesActions.showMemberDetails from reaching across features into ActivitiesRepository (roadmap 3+8)

context: `ui/voices/VoicesActions.kt` `showMemberDetails` (lines 214-231) takes an `ActivitiesRepository` parameter (line 216) and calls `activitiesRepository.getMemberVisitStats(userModel.id, userModel.name)` at 220 just to prefill `MembersDetailFragment.newInstance` args (`visits`, `lastLogin` — see `ui/teams/members/MembersDetailFragment.kt` companion at lines 60-84). Voices code should not fetch member stats; the detail screen should load its own data. Note: `ui/voices/ReplyActivity.kt` also calls this function at line 195 and is off-limits (open merge PR #17648), so the old signature must keep working.

files: app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt, app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersDetailFragment.kt, app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt, plus a new file app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersDetailViewModel.kt. Do NOT touch `ui/voices/ReplyActivity.kt` (open PR owns it) or `ActivitiesRepository`/`VoicesEditActions` files.

steps:
1. Create `@HiltViewModel class MembersDetailViewModel` exposing member visit stats (delegating to `ActivitiesRepository.getMemberVisitStats`) as a `StateFlow`, keyed by member id/name args.
2. Change `MembersDetailFragment` to load `visits`/`lastLogin` from the ViewModel at display time instead of requiring them prefilled in the args Bundle; keep the other `newInstance` args (name, email, dob, language, phone, username, memberLevel, imageUrl) and add the member user-id arg needed for the stats lookup.
3. In `VoicesActions`, add an overload `showMemberDetails(userModel)` that no longer fetches stats and passes the member id/name through; keep the existing `showMemberDetails(userModel, activitiesRepository)` signature — deprecate it and have it delegate to the new overload — so `ReplyActivity.kt` compiles untouched.
4. Update `BaseVoicesFragment.kt` line 105 to the new overload; its `activitiesRepository` field (line 45) then becomes unused — remove the injection and import.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; tapping a member in voices/news still opens the member-detail dialog showing visit count and last login, and ReplyActivity compiles unchanged.

size budget: ~110 changed lines, 4 files.

out of scope: do not remove the deprecated overload or `ReplyActivity`'s `activitiesRepository` injection (owned by open PR #17648); no other `VoicesActions` refactors. `BaseVoicesFragment.kt` appears in stale PR #13415 — verify it got no new commits before starting.

# myPlanet refactor round — generated work orders

- date: 2026-10-09
- base commit: `f18c50d25` (master HEAD)
- open PRs checked: #18076, #18050, #18045, #18029, #17832, #17694 (plus full open-PR listing reviewed)
- off-limits applied: files touched by PRs opened in the last week carrying ready/merge-style labels — #18050 "ready" owns `repository/SyncRepositoryImpl.kt` and `SyncRepositoryImplTest.kt`; #18076 "on hold" owns `repository/RatingsRepositoryImpl.kt` and `RatingsRepositoryImplTest.kt` (excluded as a courtesy). #18045/#18029/#17832/#17694 are "experiment" KMP branches — per the round's tag rule they do not bind, but their hot areas (`services/upload/`, `data/api/`, `model/*Json.kt`, `utils/` platform shims) were avoided anyway.

### 1. replace full-table team scans with targeted membership queries (roadmap 1+7, serves 9)
context: `TeamsRepositoryImpl.getMyTeamsFlow` (TeamsRepositoryImpl.kt:208-209) and `getMyTeamDetailsFlow` (:295-297) call `teamDao.observeAll()`, which emits every row of the teams table — memberships, requests, reports — then filters in Kotlin on each emission. `markMembershipsForLeave` (:1365-1378) issues one `deleteById` or `upsert` per membership row. Targeted Room queries cut per-emission work to the user's own memberships and the teams they point at.
files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 208-230, 295-315, 1365-1378); `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add queries near `observeAll` at :19). Leave alone: `TeamsMembersRepository.kt`, `TeamsNotificationsRepository.kt`, `EnterprisesRepositoryImpl.kt` (also uses teamDao), and `getTeamsForUpload`/`getCoursesForSerialization` (:84-95, :1344-1353).
steps: 1. add `observeMembershipTeamIds(userId)` (`SELECT teamId FROM teams WHERE userId = :userId AND docType = 'membership' AND isDeletePending = 0`) and a subquery-backed `observeTeamsForUser(userId, type)` (`WHERE _id IN (...) AND status != 'archived' AND isDeletePending = 0` plus the existing root-team/type predicates); 2. swap `observeAll()` for the new query in both flows, keeping the downstream `TeamDetails` mapping; 3. in `markMembershipsForLeave`, partition rows into deletes vs updates and issue one `upsertAll` plus one bulk `deleteByIds`; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; dashboard/TeamsFragment still lists my teams and team details; leaving a team still marks or removes membership rows.
size budget: ~90 changed lines, 2 files.
out of scope: no `TeamsRepository` interface changes; no serialization/upload-path edits.

---

### 2. route exam/question/submission reads through SubmissionsRepository (roadmap 1+4, serves 9)
context: `SurveysRepositoryImpl` injects `submissionDao` and reads the submissions table directly at :207-209 (`getByUserIdAndTeamId`/`getByUserIdWithoutTeam`), :279, :301-303, :353 (`getByParentIdsAndTeamId`), :369 (`countPendingSurveys`), while `SubmissionsRepositoryImpl` already owns `SubmissionDao`. `ProgressRepositoryImpl` likewise injects `examDao`/`questionDao` (:30-31) for reads at :65 and :186 that belong to the submissions/exams domain. Cross-feature DAO injection blurs table ownership.
files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt` (new read methods), `repository/SubmissionsRepositoryImpl.kt` (delegate to its already-injected `examDao`/`questionDao`/`submissionDao`), `repository/SurveysRepositoryImpl.kt` (swap the listed reads only), `repository/ProgressRepositoryImpl.kt` (swap :65 and :186, drop the constructor params at :30-31 after confirming no other use). Leave alone: survey writes (`submissionDao.upsertAll` :119, `examDao`/`questionDao` upserts) and `examDao.getByIds` at :472.
steps: 1. add `getExamsByCourseIds(courseIds)`, `getQuestionsByIds(questionIds)`, `getSubmissionsByTeamId(teamId)`, `getSubmissionsByUserIdAndTeamId(userId, teamId)`, `getSubmissionsByUserIdWithoutTeam(userId)`, `countPendingSurveySubmissions(userId)` to `SubmissionsRepository` and implement as DAO passthroughs; 2. replace the listed call sites in SurveysRepositoryImpl and ProgressRepositoryImpl; 3. remove the now-unused `examDao`/`questionDao` injections from ProgressRepositoryImpl; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; survey list, survey info badges, and `fetchCourseData` output are unchanged.
size budget: ~120 changed lines, 4 files.
out of scope: keep all write paths where they are; no DAO changes.

---

### 3. centralize search-activity and removed-log access behind ActivitiesRepository (roadmap 1+4, serves 9)
context: `SearchActivityDao` is owned by `ActivitiesRepositoryImpl` (:60), yet `CoursesRepositoryImpl` (:27/:67, insert at :345) and `ResourcesRepositoryImpl` (:26/:59, insert at :477) each inject it to run near-identical `saveSearchActivity` inserts. `UserRepositoryImpl` reads `removed_log` rows via `removedLogDao` (:1259-1260) despite already holding `activitiesRepositoryLazy` (:81), and injects `offlineActivityDao` (:83) that is never called.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt` (add `recordSearchActivity(...)` and `getRemovedDocIds(type, userId)`), `repository/ActivitiesRepositoryImpl.kt` (implement over its existing `searchActivityDao`/`removedLogDao`), `repository/CoursesRepositoryImpl.kt` (delegate the :335-355 insert; drop the DAO import+param at :27/:67), `repository/ResourcesRepositoryImpl.kt` (delegate :465-489; drop :26/:59 — it already injects `activitiesRepository` at :56), `repository/UserRepositoryImpl.kt` (route :1259-1260 through `activitiesRepositoryLazy`; drop `offlineActivityDao` :83 and, if unused afterward, `removedLogDao` :84). Leave alone: CoursesRepositoryImpl's `removedLogDao` calls at :250/:430 and its `myLibraryDao` usage, ResourcesRepositoryImpl's `removedLogDao`.
steps: 1. add the two interface methods with signatures covering both call sites (user, time, createdOn, parentCode, text, type, filter payload); 2. implement once in ActivitiesRepositoryImpl building the `SearchActivity` row and delegating `getRemovedDocIds` to `removedLogDao`; 3. replace the two `saveSearchActivity` bodies and the `getShelfData` reads with repository calls; 4. drop unused injections/imports; 5. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; course and resource searches still write `search_activity` rows; shelf building still excludes removed resources/courses.
size budget: ~110 changed lines, 5 files.
out of scope: other `removedLogDao` call sites in courses/resources; no interface renames.

---

### 4. push the guest-user exclusion for news uploads into NewsDao (roadmap 1+7, serves 9)
context: `VoicesRepositoryImpl.getNewsForUpload` (:37-40) calls `newsDao.getAll()` — `SELECT * FROM news` at `NewsDao.kt:33` — then drops every row whose `userId?.startsWith("guest")` in Kotlin. A SQL `NOT LIKE 'guest%'` clause returns only uploadable rows and skips deserializing guest posts.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt` (add `getAllExcludingGuests()` next to `getAll` at :33); `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` (:37-40). Leave alone: `NewsLogDao.kt`, `VoicesEditor.kt`, upload callers of `getNewsForUpload`.
steps: 1. add `@Query("SELECT * FROM news WHERE userId IS NULL OR userId NOT LIKE 'guest%'") suspend fun getAllExcludingGuests(): List<News>`; 2. swap `newsDao.getAll()` for it in `getNewsForUpload` and remove the redundant `startsWith("guest")` `mapNotNull` check; 3. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; news upload still skips guest-authored posts and uploads the rest.
size budget: ~15 changed lines, 2 files.
out of scope: no changes to upload batching or `NewsUploadData` shaping.

---

### 5. deduplicate voices draft-image entry serialization (roadmap 8, serves 9)
context: `BaseVoicesFragment` builds a `{"imageUrl", "fileName"}` `JsonObject` per picked image (:150-153) and parses every entry back for the duplicate check in `isImageAlreadyAdded` (:168-175); `ReplyActivity` re-implements both blocks (:225-247). Stringly-typed JSON assembled in two UI classes — the model layer already owns this serialization style (`Achievement.createReference`, model/Achievement.kt:156).
files: `app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt` (:150-153, :168-175), `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt` (:225-247), `app/src/main/java/org/ole/planet/myplanet/model/News.kt` (new companion helpers). Leave alone: `VoicesAdapter.setImageList`, `VoicesUploader.createImage`, `FileUtils.getFileNameFromUrl` (reuse it).
steps: 1. add `News.createImageListEntry(path: String, fileName: String): String` returning the serialized JsonObject, and `News.imageListEntryPath(json: String): String?` returning the parsed `imageUrl`; 2. replace the inline `JsonObject` construction in both files with `createImageListEntry(path, getFileNameFromUrl(path))`; 3. rewrite both `isImageAlreadyAdded` bodies over `imageListEntryPath`; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; attaching the same image twice is still rejected in the voices composer and the reply screen.
size budget: ~60 changed lines, 3 files.
out of scope: no adapter or fragment API changes; no compose work.

---

### 6. extract challenge-dialog evaluation into a use case (roadmap 3, serves 9)
context: `DashboardViewModel.evaluateChallengeDialog` (:298-358) fans out to progress, voices, courses, and submissions repositories behind hardcoded promo constants (:305-307: `startTime`, `endTime`, `courseId`) and holds all the prerequisite logic in the ViewModel, where it cannot be unit-tested without the VM harness. `ChallengeDialogData` (:62) and `getCourseStatusString` (:361-372) live next to it.
files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt` (:298-358; keep `getCourseStatusString` :361-372 — it needs `application.getString`); new `app/src/main/java/org/ole/planet/myplanet/usecase/EvaluateChallengeDialogUseCase.kt`. Leave alone: `ChallengePrompter.kt` (consumes the event unchanged), `BellDashboardViewModel.kt`.
steps: 1. create `usecase/EvaluateChallengeDialogUseCase.kt` as an `@Singleton` injecting `ProgressRepository`, `VoicesRepository`, `CoursesRepository`, `SubmissionsRepository`; 2. move the `coroutineScope` fan-out, the date-window gate, and the prereqs/valid-sync checks into an `invoke(...)` returning a small result type (progress JsonObject, courseName, voiceCount, allVoiceCount, hasUnfinishedSurvey, hasValidSync) or null; 3. keep `evaluateChallengeDialog` in the VM delegating to the use case and doing the `getCourseStatusString` formatting + `_challengeDialogEvent` emit; 4. move the promo constants into the use case unchanged; 5. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; challenge-dialog eligibility evaluates identically (window is already past — keep the constants verbatim).
size budget: ~110 changed lines, 2 files (one new).
out of scope: no changes to promo dates, courseId, or dialog UI; no new test scaffolding.

---

### 7. deduplicate server reachability and URL-fallback probing (roadmap 5+8)
context: `ServerReachabilityWorker.checkAvailableServerAndUpload` (:168-195) and `SubmissionsUploader.checkAvailableServer` (:33-72) each run the same sequence: `serverUrlMapper.processUrl`, probe primary with a 15s timeout, probe `alternativeUrl`, then `serverUrlMapper.updateUrlPreferences` when only the alternative answered. Two copies drift apart (worker uploads anyway on total failure; uploader skips).
files: `app/src/main/java/org/ole/planet/myplanet/services/ServerReachabilityWorker.kt` (:168-195), `app/src/main/java/org/ole/planet/myplanet/services/SubmissionsUploader.kt` (:33-72), `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt` (add helper next to `updateUrlPreferences` :56). Leave alone: the third copy in `TeamsRepositoryImpl.kt` (:945-958) — a different work item owns that file; `MainApplication.isServerReachable`.
steps: 1. add a `suspend fun probeWithFallback(currentUrl: String, timeoutMs: Long, isReachable: suspend (String) -> Boolean)` (or equivalently named) to `ServerUrlMapper` returning a result (mapping, reachable flag, primaryAlive) after probing primary then alternative and rewriting prefs when only the alternative works; 2. call it from `checkAvailableServerAndUpload`, preserving the "upload even when unreachable" behavior; 3. call it from `checkAvailableServer`, preserving "skip upload when unreachable" and its logging; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; both upload paths still fall back to the alternative URL and rewrite prefs identically.
size budget: ~80 changed lines, 3 files.
out of scope: the `TeamsRepositoryImpl` copy; timeout/logging policy changes.

---

### 8. move achievement-entry JSON assembly out of EditAchievementFragment (roadmap 3, serves 9)
context: `EditAchievementFragment` owns `achievementArray: JsonArray?` (:77) and builds each entry `JsonObject` in `saveAchievement` (:404-414), with mutation scattered across `showAchievementAndInfo` (:268-284), the edit dialog (:381), and `populateAchievementData` (:453+). The `Achievement` companion already hosts JsonObject builders (`createReference`, model/Achievement.kt:156); the VM already accepts the array via `AchievementSaveRequest.achievements` (AchievementViewModel.kt:31).
files: `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt` (:77, :268-284, :381, :404-414), `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt` (host array + add/remove), `app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt` (new `createEntry`). Leave alone: `AchievementsAdapter.kt` (:27 has its own small JsonObject — next round), `referenceArray` handling.
steps: 1. add `Achievement.createEntry(description, title, date, link, resourceArray): JsonObject` mirroring `createReference`; 2. hoist `achievementArray` into `AchievementViewModel` exposing `addAchievementEntry(...)`, `removeAchievementEntry(obj)`, and the current `JsonArray` for `AchievementSaveRequest`; 3. have the fragment call the VM helpers and keep only dialogs/date-picking; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; creating/editing an achievement preserves entries, resources, and references on save.
size budget: ~80 changed lines, 3 files.
out of scope: no layout changes; leave `referenceArray` and `AchievementsAdapter` JSON for a later pass.

---

### 9. move examination-signature encryption into HealthRepository (roadmap 1+3, serves 9)
context: `HealthExaminationViewModel.saveExamination` (:103-122) runs private `encryptSign` (:128-141) — generates key/iv onto the user and `AndroidDecrypter.encrypt`s the sign payload into `examination.data` — before calling `healthRepository.saveExamination`. Crypto-on-persist is a data-layer detail; the repository already persists the mutated user (`HealthRepositoryImpl.kt:81-85` calls `userRepository.get().saveUser(it)`).
files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` (:113-115 call site, :128-141 private fn, unused imports :24-25 after the move), `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt` (extend `saveExamination` signature :12), `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt` (:80-86). Leave alone: `HealthExaminationActivity.kt` (its `viewModel.saveExamination(...)` call keeps the same VM signature), `AndroidDecrypter.kt`.
steps: 1. change `saveExamination(examination, pojo, user, sign: Examination)` on the interface and impl; 2. move the `encryptSign` body into the impl ahead of the upserts, keeping the same catch-and-log behavior; 3. delete the VM's private function and its `generateKey`/`generateIv` imports; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; saving an examination still encrypts `data` and persists the generated user key/iv so it can be decrypted later.
size budget: ~45 changed lines, 3 files.
out of scope: no crypto changes; no other `AndroidDecrypter` call sites.

---

### 10. move bell dashboard's server-URL probing into the ViewModel (roadmap 3, serves 10)
context: `BellDashboardFragment.isServerReachable` (:109-121) and `handleConnectingState` (:123-136) read `prefData`, run `serverUrlMapper.processUrl`, and drive the primary→alternative probe inside the fragment, even though `BellDashboardViewModel.checkServerConnection` (:136-141) already wraps a single-URL probe and owns `NetworkStatus`. Fragment-side probing is untestable and duplicates URL-mapping logic; hoisting it keeps the composable/fragment layer free of data plumbing.
files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt` (:109-136; drop the `serverUrlMapper` field — its only use is :127 — but keep `prefData`, still used at :373/:438), `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt` (new mapping-aware check). Leave alone: `NetworkStatus` flow wiring, `updateNetworkIndicator` color mapping.
steps: 1. inject `SharedPrefManager` and `ServerUrlMapper` into `BellDashboardViewModel`; 2. add `checkServerConnectionWithFallback()` that reads the stored URL, processes the mapping, probes primary then alternative via `serverReachabilityProvider`, updates `_networkStatus`, and returns Boolean; 3. reduce `handleConnectingState` to calling it and picking the indicator color; delete `isServerReachable`; 4. run unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; bell icon still goes green on reachable and stays yellow on unreachable, using the alternative URL when primary is down.
size budget: ~50 changed lines, 2 files.
out of scope: no status-flow redesign; no changes to sync scheduling.

# myPlanet refactor round — generated work orders

- date: 2026-10-09
- base commit: `f18c50d25` (master HEAD)
- open PRs checked: #18076, #18050, #18045, #18029, #17832, #17694 (plus the full open-PR listing)
- off-limits applied: files touched by PRs opened in the last week carrying ready/merge-style labels — #18050 "ready" owns `repository/SyncRepositoryImpl.kt` and `SyncRepositoryImplTest.kt`; #18076 "on hold" owns `repository/RatingsRepositoryImpl.kt` and `RatingsRepositoryImplTest.kt` (excluded as a courtesy). #18045/#18029/#17832/#17694 are "experiment" KMP branches — per the round's tag rule they do not bind, but their hot areas (`services/upload/`, `data/api/`, `model/*Json.kt`, `utils/` platform shims) were avoided anyway.

### 1. replace full-table team scans with targeted membership queries (roadmap 1+7, serves 9)

context: `TeamsRepositoryImpl.getMyTeamsFlow` (TeamsRepositoryImpl.kt:208-209) and `getMyTeamDetailsFlow` (:295-297) call `teamDao.observeAll()`, which emits every row of the teams table — memberships, requests, reports — then filters in Kotlin on each emission. `markMembershipsForLeave` (:1365-1378) issues one `deleteById` or `upsert` per membership row in a loop. Targeted Room queries cut per-emission work to the user's own memberships and the teams they point at, and batch writes cut N round-trips to 2.

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 208-230, 295-315, 1365-1378) and `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add the queries next to `observeAll` at :19). Leave alone: `TeamsMembersRepository.kt`, `TeamsNotificationsRepository.kt`, `EnterprisesRepositoryImpl.kt` (also uses `teamDao`), and the upload/serialization paths `getTeamsForUpload` / `getCoursesForSerialization` (:84-95, :1344-1353).

steps:
1. In `TeamDao`, add `observeMembershipTeamIds(userId)` (`SELECT teamId FROM teams WHERE userId = :userId AND docType = 'membership' AND isDeletePending = 0`) and a subquery-backed `observeTeamsForUser(userId, type)` (`WHERE _id IN (...) AND status != 'archived' AND isDeletePending = 0` plus the existing root-team and type predicates).
2. Swap `teamDao.observeAll()` for the new query in `getMyTeamsFlow`, keeping the same `MyTeam` mapping.
3. Do the same in `getMyTeamDetailsFlow`, keeping the downstream `TeamDetails` assembly (member count, resources, tasks, reports lookups) untouched.
4. In `markMembershipsForLeave`, partition the memberships into deletions vs updates and issue one `upsertAll` plus one bulk `deleteByIds` instead of the per-row loop.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the dashboard team card, TeamsFragment "my teams" list, and team detail screens still populate; leaving a team still marks/removes its membership rows.

size budget: ~90 changed lines, 2 files.

out of scope: no `TeamsRepository` interface signature changes; no upload-path or serialization edits.

---

### 2. route exam/question/submission reads through SubmissionsRepository (roadmap 1+4, serves 9)

context: `SurveysRepositoryImpl` injects `submissionDao` and reads the submissions table directly at :207-209 (`getByUserIdAndTeamId`/`getByUserIdWithoutTeam`), :279, :301-303, :353 (`getByParentIdsAndTeamId`), and :369 (`countPendingSurveys`), while `SubmissionsRepositoryImpl` already owns `SubmissionDao`. `ProgressRepositoryImpl` likewise injects `examDao`/`questionDao` (:30-31) for reads at :65 and :186 that belong to the submissions/exams domain. Cross-feature DAO injection blurs which repository owns these tables.

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt` (new read methods) and `repository/SubmissionsRepositoryImpl.kt` (delegating implementations — `examDao`, `questionDao`, `submissionDao` are already injected there), `repository/SurveysRepositoryImpl.kt` (swap the listed reads only), `repository/ProgressRepositoryImpl.kt` (swap :65 and :186, then drop the constructor params at :30-31 after confirming no other use). Leave alone: survey write paths (`submissionDao.upsertAll` at :119, all `examDao`/`questionDao` upserts) and `examDao.getByIds` at :472.

steps:
1. Add `getExamsByCourseIds(courseIds)`, `getQuestionsByIds(questionIds)`, `getSubmissionsByTeamId(teamId)`, `getSubmissionsByUserIdAndTeamId(userId, teamId)`, `getSubmissionsByUserIdWithoutTeam(userId)`, and `countPendingSurveySubmissions(userId)` to `SubmissionsRepository`.
2. Implement them in `SubmissionsRepositoryImpl` as plain DAO passthroughs.
3. Replace the listed call sites in `SurveysRepositoryImpl` (:207-209, :279, :301-303, :353, :369).
4. Replace `ProgressRepositoryImpl` :65 and :186, then remove the unused `examDao`/`questionDao` injections and imports.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; survey list, per-survey info badges, and `fetchCourseData` output are unchanged.

size budget: ~120 changed lines, 4 files.

out of scope: keep all write paths where they are; no DAO-level changes.

---

### 3. centralize search-activity and removed-log access behind ActivitiesRepository (roadmap 1+4, serves 9)

context: `SearchActivityDao` is owned by `ActivitiesRepositoryImpl` (:60), yet `CoursesRepositoryImpl` (:27/:67, insert at :345) and `ResourcesRepositoryImpl` (:26/:59, insert at :477) each inject it to run near-identical `saveSearchActivity` inserts. `UserRepositoryImpl` reads `removed_log` rows via `removedLogDao` (:1259-1260) despite already holding `activitiesRepositoryLazy` (:81), and injects an `offlineActivityDao` (:83) that is never called — dead wiring plus a leaked table.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt` (add `recordSearchActivity(...)` and `getRemovedDocIds(type, userId)`), `repository/ActivitiesRepositoryImpl.kt` (implement over its existing `searchActivityDao`/`removedLogDao`), `repository/CoursesRepositoryImpl.kt` (delegate the :335-355 insert; drop the DAO import+param at :27/:67), `repository/ResourcesRepositoryImpl.kt` (delegate :465-489; drop :26/:59 — it already injects `activitiesRepository` at :56), `repository/UserRepositoryImpl.kt` (route :1259-1260 through `activitiesRepositoryLazy`; drop `offlineActivityDao` :83 and, if unused afterward, `removedLogDao` :84). Leave alone: CoursesRepositoryImpl's `removedLogDao` calls at :250/:430 and its `myLibraryDao` usage, and ResourcesRepositoryImpl's `removedLogDao` — those are next-round leaks.

steps:
1. Add `recordSearchActivity(...)` to `ActivitiesRepository` with a signature covering both call sites (user, time, createdOn, parentCode, text, type, filter payload).
2. Add `getRemovedDocIds(type, userId): List<String>` to the same interface.
3. Implement both in `ActivitiesRepositoryImpl` — build the `SearchActivity` row once and delegate `getRemovedDocIds` to `removedLogDao`.
4. Replace the `saveSearchActivity` bodies in CoursesRepositoryImpl and ResourcesRepositoryImpl with the repository call; drop the now-unused DAO injections.
5. Route `UserRepositoryImpl.getShelfData`'s two `removedLogDao.getRemovedDocIds` calls through `activitiesRepositoryLazy`; remove dead injections.
6. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; course and resource searches still write `search_activity` rows; shelf building still excludes removed resources/courses.

size budget: ~110 changed lines, 5 files.

out of scope: the remaining `removedLogDao` call sites in courses/resources; no interface renames.

---

### 4. push the guest-user exclusion for news uploads into NewsDao (roadmap 1+7, serves 9)

context: `VoicesRepositoryImpl.getNewsForUpload` (:37-40) calls `newsDao.getAll()` — `SELECT * FROM news` at `NewsDao.kt:33` — then drops every row whose `userId?.startsWith("guest")` inside a Kotlin `mapNotNull`. A SQL `NOT LIKE 'guest%'` clause returns only uploadable rows and skips deserializing guest posts on every upload pass.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt` (add `getAllExcludingGuests()` next to `getAll` at :33) and `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` (:37-40). Leave alone: `NewsLogDao.kt`, `VoicesEditor.kt`, and the upload-side callers of `getNewsForUpload` — semantics stay identical.

steps:
1. Add `@Query("SELECT * FROM news WHERE userId IS NULL OR userId NOT LIKE 'guest%'") suspend fun getAllExcludingGuests(): List<News>` to `NewsDao`.
2. In `getNewsForUpload`, swap `newsDao.getAll()` for `getAllExcludingGuests()`.
3. Remove the now-redundant `startsWith("guest")` check inside the `mapNotNull`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; news upload still skips guest-authored posts and uploads everything else.

size budget: ~15 changed lines, 2 files.

out of scope: no changes to upload batching, `NewsUploadData` shaping, or `news_log` handling.

---

### 5. deduplicate voices draft-image entry serialization (roadmap 8, serves 9)

context: `BaseVoicesFragment` builds a `{"imageUrl", "fileName"}` `JsonObject` per picked image (:150-153) and parses every entry back for the duplicate check in `isImageAlreadyAdded` (:168-175); `ReplyActivity` re-implements both blocks (:225-247). Stringly-typed JSON is assembled in two UI classes even though the model layer already owns this serialization style (`Achievement.createReference`, model/Achievement.kt:156).

files: `app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt` (:150-153, :168-175), `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt` (:225-247), and `app/src/main/java/org/ole/planet/myplanet/model/News.kt` (new companion helpers). Leave alone: `VoicesAdapter.setImageList` (it consumes the same JsonArray — unchanged), `VoicesUploader.createImage`, and `FileUtils.getFileNameFromUrl` (reuse it, do not move it).

steps:
1. Add `News.createImageListEntry(path: String, fileName: String): String` in the `News` companion, returning the serialized `{"imageUrl": path, "fileName": fileName}` object.
2. Add `News.imageListEntryPath(json: String): String?` beside it, returning the parsed `imageUrl`.
3. In `BaseVoicesFragment`, replace the inline `JsonObject` construction with `createImageListEntry(path, getFileNameFromUrl(path))` and rewrite `isImageAlreadyAdded` over `imageListEntryPath`.
4. Do the same replacement in `ReplyActivity` (:225-247).
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; attaching the same image twice is still rejected in both the voices composer and the reply screen.

size budget: ~60 changed lines, 3 files.

out of scope: no adapter or fragment API changes; no compose work.

---

### 6. extract challenge-dialog evaluation into a use case (roadmap 3, serves 9)

context: `DashboardViewModel.evaluateChallengeDialog` (:298-358) fans out to progress, voices, courses, and submissions repositories behind hardcoded promo constants (:305-307: `startTime`, `endTime`, `courseId`) and holds all prerequisite logic in the ViewModel, where it cannot be unit-tested without the VM harness. `ChallengeDialogData` (:62) and `getCourseStatusString` (:361-372) sit next to it; the latter needs `application.getString` so it stays.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt` (:298-358) and new `app/src/main/java/org/ole/planet/myplanet/usecase/EvaluateChallengeDialogUseCase.kt`. Leave alone: `ChallengePrompter.kt` (consumes `_challengeDialogEvent` unchanged) and `BellDashboardViewModel.kt` — a different task owns that file.

steps:
1. Create `usecase/EvaluateChallengeDialogUseCase.kt` as an `@Singleton` injecting `ProgressRepository`, `VoicesRepository`, `CoursesRepository`, and `SubmissionsRepository`.
2. Move the `coroutineScope` fan-out, the date-window gate, and the prereqs/valid-sync checks into an `invoke(...)` returning a small result type (progress JsonObject, courseName, voiceCount, allVoiceCount, hasUnfinishedSurvey, hasValidSync) or null.
3. Keep `evaluateChallengeDialog` in the VM delegating to the use case, then doing the `getCourseStatusString` formatting and the `_challengeDialogEvent` emit.
4. Move the promo constants (`startTime`, `endTime`, `courseId`) into the use case verbatim — the window is already past, do not "fix" the dates.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; challenge-dialog eligibility evaluates identically (constants unchanged) and the dialog event still carries the same `ChallengeDialogData`.

size budget: ~110 changed lines, 2 files (one new).

out of scope: no changes to promo dates, courseId, dialog UI, or `ChallengeDialogData` shape; no new test scaffolding.

---

### 7. deduplicate server reachability and URL-fallback probing (roadmap 5+8)

context: `ServerReachabilityWorker.checkAvailableServerAndUpload` (:168-195) and `SubmissionsUploader.checkAvailableServer` (:33-72) each run the same sequence: `serverUrlMapper.processUrl`, probe primary with a 15s `withTimeoutOrNull`, probe `mapping.alternativeUrl`, then `serverUrlMapper.updateUrlPreferences` when only the alternative answered. Two copies have already drifted (the worker uploads anyway on total failure; the uploader skips).

files: `app/src/main/java/org/ole/planet/myplanet/services/ServerReachabilityWorker.kt` (:168-195), `app/src/main/java/org/ole/planet/myplanet/services/SubmissionsUploader.kt` (:33-72), and `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt` (add the helper next to `updateUrlPreferences` at :56). Leave alone: the third copy in `TeamsRepositoryImpl.kt` (:945-958) — a different work item owns that file — and `MainApplication.isServerReachable`.

steps:
1. Add a `suspend fun probeWithFallback(currentUrl: String, timeoutMs: Long, isReachable: suspend (String) -> Boolean)` (or equivalently named) to `ServerUrlMapper`, returning a result holding the mapping, a reachable flag, and whether the primary answered.
2. Inside it, run the existing sequence: `processUrl`, primary probe, alternative probe, `updateUrlPreferences` when only the alternative works.
3. Call it from `checkAvailableServerAndUpload`, preserving the worker's "upload even when unreachable" behavior.
4. Call it from `checkAvailableServer`, preserving "skip upload when unreachable" and its timing log.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; both upload paths still fall back to the alternative URL and rewrite prefs identically to today.

size budget: ~80 changed lines, 3 files.

out of scope: the `TeamsRepositoryImpl` copy; timeout values, logging policy, and pref keys stay as-is.

---

### 8. move achievement-entry JSON assembly out of EditAchievementFragment (roadmap 3, serves 9)

context: `EditAchievementFragment` owns `achievementArray: JsonArray?` (:77) and builds each entry `JsonObject` inside `saveAchievement` (:404-414), with mutation scattered across `showAchievementAndInfo` (:268-284), the edit dialog (:381), and `populateAchievementData` (:453+). The `Achievement` companion already hosts JsonObject builders (`createReference`, model/Achievement.kt:156), and the VM already accepts the array via `AchievementSaveRequest.achievements` (AchievementViewModel.kt:31) — the fragment is doing data-layer assembly.

files: `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt` (:77, :268-284, :381, :404-414), `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt` (host the array plus add/remove helpers), and `app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt` (new `createEntry` next to `createReference` at :156). Leave alone: `AchievementsAdapter.kt` (:27 has its own small JsonObject — next round) and `referenceArray` handling.

steps:
1. Add `Achievement.createEntry(description, title, date, link, resourceArray): JsonObject` to the companion, mirroring `createReference`.
2. In `AchievementViewModel`, hold `achievementArray` and expose `addAchievementEntry(...)`, `removeAchievementEntry(obj)`, and the current `JsonArray` for `AchievementSaveRequest`.
3. Have `saveAchievement` build the entry via `Achievement.createEntry` and hand it to the VM.
4. Rewire `showAchievementAndInfo`, the edit dialog, and `populateAchievementData` to read/mutate the VM-held array; keep dialogs and date picking in the fragment.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; creating and editing an achievement preserves entries, resources, and references on save.

size budget: ~80 changed lines, 3 files.

out of scope: no layout changes; leave `referenceArray` and the adapter-side JSON for a later pass.

---

### 9. move examination-signature encryption into HealthRepository (roadmap 1+3, serves 9)

context: `HealthExaminationViewModel.saveExamination` (:103-122) runs private `encryptSign` (:128-141) — generates key/iv onto the user and `AndroidDecrypter.encrypt`s the sign payload into `examination.data` — before calling `healthRepository.saveExamination`. Crypto-on-persist is a data-layer detail living in a ViewModel; the repository already persists the mutated user (`HealthRepositoryImpl.kt:81-85` calls `userRepository.get().saveUser(it)`).

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` (:113-115 call site, :128-141 private fn, and the `AndroidDecrypter`/key-gen imports that become unused), `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt` (extend the `saveExamination` signature at :12), and `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt` (:80-86). Leave alone: `HealthExaminationActivity.kt` — its `viewModel.saveExamination(...)` call keeps the same VM signature — and `AndroidDecrypter.kt` itself.

steps:
1. Change `saveExamination(examination, pojo, user, sign: Examination)` on `HealthRepository` and `HealthRepositoryImpl`.
2. Move the `encryptSign` body into the impl ahead of the upserts, keeping the same catch-and-log error behavior.
3. In the VM, delete the private function, pass `sign` through, and drop the now-unused crypto imports.
4. Keep `_saveResult` emission semantics identical.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; saving an examination still encrypts `data` and persists the generated user key/iv so the record can be decrypted later.

size budget: ~45 changed lines, 3 files.

out of scope: no crypto algorithm changes; no other `AndroidDecrypter` call sites.

---

### 10. move bell dashboard's server-URL probing into the ViewModel (roadmap 3, serves 10)

context: `BellDashboardFragment.isServerReachable` (:109-121) and `handleConnectingState` (:123-136) read `prefData`, run `serverUrlMapper.processUrl`, and drive the primary→alternative probe inside the fragment, even though `BellDashboardViewModel.checkServerConnection` (:136-141) already wraps a single-URL probe and owns `NetworkStatus`. Fragment-side probing is untestable and duplicates URL-mapping logic; hoisting it keeps the UI layer free of data plumbing (a CMP requirement).

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt` (:109-136; drop the `serverUrlMapper` field — its only use is :127 — but keep `prefData`, still used at :373/:438) and `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt` (new mapping-aware check). Leave alone: `NetworkStatus` flow wiring and the `updateNetworkIndicator` color mapping.

steps:
1. Inject `SharedPrefManager` and `ServerUrlMapper` into `BellDashboardViewModel`.
2. Add `checkServerConnectionWithFallback()` that reads the stored server URL, processes the mapping, probes primary then alternative via `serverReachabilityProvider`, updates `_networkStatus`, and returns Boolean.
3. Reduce `handleConnectingState` to calling it and picking the indicator color; delete `isServerReachable` and the fragment's `serverUrlMapper` dependency.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the bell icon still goes green when reachable and stays yellow when unreachable, using the alternative URL when the primary is down.

size budget: ~50 changed lines, 2 files.

out of scope: no status-flow redesign; no changes to sync scheduling or indicator colors.

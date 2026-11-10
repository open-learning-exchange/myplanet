# myPlanet refactor round — performance quick wins (2026-11-10)

- **date:** 2026-11-10
- **base commit:** `2093a62` (`all: smoother base recycler download offer tracking (fixes #5278) (#18543)`)
- **open PRs checked (35 listed):** 18555, 18554, 18551, 18540, 18539, 18455, 18076, 18045, 18029, 17832, 17811, 17694, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13355, 13287, 10993, 8175, 4075
- **file lists inspected:** every PR opened or updated in the last week (18555, 18554, 18551, 18540, 18539), plus the small active PRs 18076 and 17811. The enormous draft or experiment PRs (KMP phases, Flutter port, sync changes feed) were avoided by staying out of `TeamsRepositoryImpl`, `UserRepositoryImpl` and `MainApplication`.
- **off-limits files (touched by open PRs):**
  - **PR 18540:** `AndroidManifest.xml`, `MainApplication.kt`, `ResourcesRepositoryImpl.kt`, `DownloadService.kt`, `DownloadStopReceiver.kt`, `DownloadWorker.kt`, `ResourcesAdapter.kt`, `ResourcesCardBinder.kt`, `StorageBreakdownFragment.kt`, `StorageBreakdownViewModel.kt`, `DialogUtils.kt`, `DownloadUtils.kt`, `PartialDownloads.kt`, all `strings.xml`
  - **PR 18551:** `WebViewActivity.kt`
  - **PR 18539:** `BaseResourceFragment.kt`
  - **PR 18554:** `CLAUDE.md`
  - **PR 18076:** `RatingsRepositoryImpl.kt`
  - **PR 17811:** `BaseDashboardFragment.kt`, `DashboardViewModel.kt`
  - **PR 18555:** the gradle wrapper
  - **PR 18455:** workflows

## Ground rules for every task

All tasks are independent: none shares a file with another, and they can merge in any order. None of them adds a dependency, changes an `@Entity`, or bumps the `AppDatabase` version. Paths are relative to the repository root. `main/…` means `app/src/main/java/org/ole/planet/myplanet/…` and `test/…` means `app/src/test/java/org/ole/planet/myplanet/…`. Before you push, run:

```
./gradlew testDefaultDebugUnitTest
./gradlew assembleDefaultDebug assembleLiteDebug
```

---

### 1. Count survey questions in SQL instead of loading every question row (roadmap 1+7, moves 9)

**context:**
- `main/repository/SurveysRepositoryImpl.kt:357` builds the "N questions" badge for each survey card. To get one integer per survey it loads every full `ExamQuestion` row (body, choices JSON, marks) for all surveys on screen:
  `val questionCounts = questionDao.getByExamIds(surveyIds).groupingBy { it.examId }.eachCount()`
- `getSurveyFormState` runs on every load of the survey list and the team survey list, including realtime "exams" refreshes.
- `QuestionDao` already has a single-exam `countByExamId` at `main/data/room/dao/QuestionDao.kt:25`, but no grouped version.

**files:**
- touch:
  - `main/data/room/dao/QuestionDao.kt` (add a grouped count next to `countByExamId`, line 25)
  - `main/repository/SurveysRepositoryImpl.kt` (`getSurveyFormState`, lines 344-366)
  - `test/repository/SurveysRepositoryImplTest.kt` (it already mocks `questionDao` at line 55)
- leave alone:
  - `SubmissionsRepositoryImpl.kt` and `SubmissionDao.kt` (task 2)
  - `SubmissionsRepositoryExporter.kt`, which needs full question rows

**steps:**
1. In `QuestionDao`, add a projection data class (for example `ExamQuestionCount(examId: String?, count: Int)`, modelled on `RatingAggregate` in `main/data/room/dao/RatingDao.kt:11-14`).
2. Add `@Query("SELECT examId, COUNT(*) AS count FROM exam_questions WHERE examId IN (:examIds) GROUP BY examId")` as an `…Internal` method.
3. Add a public `suspend fun countByExamIds(examIds: List<String>): Map<String, Int>` default method. Copy the chunking of `getByExamIds` (lines 21-24): empty guard, `distinct()`, `chunked(900)`. Merge the chunks into one map.
4. In `getSurveyFormState`, replace the line 357 expression with `questionDao.countByExamIds(surveyIds)`. Keep the `questionCounts[surveyId] ?: 0` lookup unchanged.
5. Add two tests to `SurveysRepositoryImplTest`:
   - `getSurveyFormState` returns the counts that the mocked `countByExamIds` reports;
   - `getByExamIds` is never called (`coVerify(exactly = 0)`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green, including `SurveysRepositoryImplTest` and `ui/surveys/SurveysViewModelTest`.
- Manually, the Surveys tab and a team's Surveys tab show the same per-survey question counts as before.

**size budget:** ~45 changed lines, 3 files

**out of scope:**
- Do not touch the other `getByExamIds` callers.
- Do not add an index on `examId` (that needs a DB version bump).

---

### 2. Batch submission rev updates and upload marks into one transaction each (roadmap 5+7, moves 9)

**context:**
- `main/repository/SubmissionsRepositoryImpl.kt:811` issues one `UPDATE` per pending submission during every submissions sync page, outside any transaction, so each row is its own SQLite commit:
  `revUpdates.forEach { (id, rev) -> submissionDao.updateRev(id, rev) }`
- Line 850 does the same for every uploaded submission in a batch of up to 50:
  `results.filter { submissionDao.markUploaded(it.localId, it.remoteId, it.remoteRev) == 0 }`
- `SubmitPhotosDao.markUploadedBatch` (`main/data/room/dao/SubmitPhotosDao.kt:34-38`) and `ApkLogDao.markUploadedBatch` (`ApkLogDao.kt:30-37`) already show the `@Transaction` default-method pattern.

**files:**
- touch:
  - `main/data/room/dao/SubmissionDao.kt` (next to `updateRev` / `markUploaded`, lines 64-65)
  - `main/repository/SubmissionsRepositoryImpl.kt` (`upsertRoomSubmissionsFromSync` line 811; `markSubmissionsUploaded` lines 849-850)
  - `test/repository/SubmissionsRepositoryImplTest.kt` (tests at lines 1358-1375)
  - `test/repository/SubmissionsRepositorySyncTest.kt`
- leave alone:
  - `SubmissionsPayloadBuilder.kt` and `SubmissionsRepositoryExporter.kt`
  - `UploadConfigs.kt` (task 3)

**steps:**
1. In `SubmissionDao`, add `@Transaction suspend fun updateRevs(updates: List<Pair<String, String>>)`. It loops over `updateRev` inside the transaction and returns early on an empty list.
2. Add a DAO-local `data class UploadMark(val localId: String, val remoteId: String?, val remoteRev: String?)`, nested like `SubmitPhotosDao.UploadedPhoto`. Do not import the repository's `UploadedItemResult` (`main/repository/UploadRepository.kt:15`) into the DAO. Then add `@Transaction suspend fun markUploadedBatch(marks: List<UploadMark>): Set<String>`. It loops over `markUploaded` and returns the `localId`s whose update count is 0.
3. Replace line 811 with `if (revUpdates.isNotEmpty()) submissionDao.updateRevs(revUpdates)`. In `markSubmissionsUploaded`, map `results` to `UploadMark`s and call `markUploadedBatch`. Return `results.filter { it.localId in failedIds }`, which is the same contract as today.
4. Update the two existing `markSubmissionsUploaded` tests. The DAO is a MockK mock, so stub and verify the new batch method instead of the per-row method. Keep the "returns failure if dao returns 0" assertion.
5. In `SubmissionsRepositorySyncTest`, add or adjust a case where a pending (`isUpdated`) row receives a new `_rev`, and verify `updateRevs` is called once with that pair.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, finish a survey offline, then sync. The survey uploads once, is not re-uploaded on the next sync, and the submissions list still shows it as complete.

**size budget:** ~60 changed lines, 4 files

**out of scope:**
- Do not wrap the rest of `upsertRoomSubmissionsFromSync` in `withTransaction`.
- Do not change `UploadedItemResult`.

---

### 3. Commit each upload batch's "mark uploaded" writes in one transaction (roadmap 5+7)

**context:**
- `main/services/upload/UploadConfigs.kt` marks rows uploaded one at a time, and each row is a separate SQLite commit. The configs affected are:
  - NewsActivities (lines 58-62), CourseProgress (71-75), TeamTask (87-91), TeamActivities (100-104), SearchActivity (113-117)
  - ResourceActivities (130-134), ResourceActivitiesSync (143-147), CourseActivities (156-160)
  - Meetups (175-179), Feedback (201-204), SubmitPhotos (228-232), Rating (298-302)
- The pattern is `results.filter { result -> !repo.markXUploaded(result.localId, …) }`.
- `RoomUploadConfig` sends batches of up to 50 rows (`main/services/upload/RoomUploadConfig.kt:35`). These configs run on every auto-sync, login upload and bulk upload, so a single sync can do hundreds of separate commits.

**files:**
- touch:
  - `main/services/upload/UploadConfigs.kt` (constructor at lines 33-48, plus the `markUploaded` lambdas listed above)
  - `test/services/upload/UploadConfigsTest.kt` (constructor call at lines 73-88)
- leave alone:
  - `getResourcesConfig` (lines 267-289): `markResourceUploaded` moves folders on disk, and file IO must not run inside a DB transaction
  - CrashLog (already batched)
  - ExamResults and Submissions (task 2 owns `markSubmissionsUploaded`)
  - every `*RepositoryImpl`

**steps:**
1. Inject `org.ole.planet.myplanet.data.room.AppDatabase` (already provided by `main/di/RoomModule.kt:56`) into the `UploadConfigs` constructor.
2. Add one private helper, `suspend fun <T> inTransaction(block: suspend () -> T): T = appDatabase.withTransaction { block() }`. Wrap the `results.filter { … }` of each of the 12 lambdas listed in the context in it. Keep the filter logic identical.
3. In `UploadConfigsTest`, pass a relaxed `AppDatabase` mock. Stub `withTransaction` the way `test/repository/CoursesRepositoryImplTest.kt:469-487` does (`mockkStatic("androidx.room.RoomDatabaseKt")` plus `coAnswers` that invokes the captured block), and unmock it in `@After`.
4. Add one test proving that a config's `markUploaded` (for example `CourseProgress`) runs inside `withTransaction` and still returns only the failed results.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, create course progress and a feedback item offline, then sync. Both upload once and are not re-sent on the next sync.

**size budget:** ~60 changed lines, 2 files

**out of scope:**
- Do not move the batching into the repositories.
- Do not change `RoomUploadConfig` or `UploadCoordinator`.

---

### 4. Count course steps in SQL for progress bars instead of loading step bodies (roadmap 1+7, moves 9)

**context:**
- `ProgressRepositoryImpl.getCourseProgress` (`main/repository/ProgressRepositoryImpl.kt:34-55`) loads every full `CourseStep` row, including the step description markdown, for every course id with `courseStepDao.getByCourseIds(courseIds)` (line 38).
- It then uses only `steps.size`: as `max` at line 50, and in `calculateCurrentProgress` (lines 205-221), whose only use of the list is `steps?.size`.
- It runs on every Courses screen load for all course ids (`main/ui/courses/CoursesViewModel.kt:125`), on Take Course open (`TakeCourseViewModel.kt:125`), and inside `fetchCourseData`.

**files:**
- touch:
  - `main/data/room/dao/CourseStepDao.kt` (lines 11-15)
  - `main/repository/ProgressRepositoryImpl.kt` (`getCourseProgress` 34-55, `getCurrentProgress` 147-152, `calculateCurrentProgress` 205-221)
  - `test/repository/ProgressRepositoryImplTest.kt`
- leave alone:
  - `ProgressRepository.kt` (keep every public signature, including `getCurrentProgress(steps, …)`)
  - `CoursesRepositoryImpl.kt` (task 5)
  - `saveEarnedExamPasses` / `getBlockingStepIndex`

**steps:**
1. In `CourseStepDao`, add a projection (`CourseStepCount(courseId: String?, count: Int)`) and `@Query("SELECT courseId, COUNT(*) AS count FROM course_steps WHERE courseId IN (:courseIds) GROUP BY courseId")`.
2. Add a public chunked default method, `countByCourseIds(courseIds): Map<String, Int>`, mirroring `getByCourseIds` (empty guard, `distinct()`, `chunked(900)`).
3. Change the private `calculateCurrentProgress` to take `stepsSize: Int` instead of the list. `getCurrentProgress` passes `steps?.size ?: 0`.
4. In `getCourseProgress`, replace the `getByCourseIds` and `groupBy` with `countByCourseIds`. Set `max = stepCounts[courseId] ?: 0` and pass that count to `calculateCurrentProgress`.
5. Add tests for `getCourseProgress`:
   - `max` and `current` come from the mocked counts and progresses, including the case where a progress `stepNum` is beyond the step count;
   - `courseStepDao.getByCourseIds` is not called.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, the course cards' progress bars show the same "x / y steps" before and after the change, both on Courses and in Take Course.

**size budget:** ~55 changed lines, 3 files

**out of scope:**
- Do not refactor the JSON round trip in `getCourseProgressRows`.
- Do not change `fetchCourseData`'s `getMyCourses` call.

---

### 5. Stop loading every course step just to read a course title or id (roadmap 1+7, moves 9)

**context:**
- In `main/repository/CoursesRepositoryImpl.kt`, `getCourseById` (lines 125-128) always runs `mapCourse`, which loads all of that course's step rows (lines 900-909). Three callers only need a scalar:
  - `getCourseTitleById` (lines 525-527) is called from `ui/courses/CoursesStepsViewModel.kt:70` and the dashboard;
  - `getCourseProgress` (lines 441-444) has just loaded the steps and then calls `getCourseById(courseId)?.courseTitle`, so the same steps load a second time;
  - `getMyCourseIds` (lines 568-572) runs `getMyCourses(userId)`, which loads every course plus all of their steps (`mapCourses` at lines 885-898), only to read `courseId`. It runs at the start of every full sync (`main/services/sync/SyncManager.kt:277`) and once per synced user during shelf upload.
- `CourseDao.getByCourseId` (`main/data/room/dao/CourseDao.kt:14`) and `CourseDao.getForUserPattern` (line 28) return the bare rows.

**files:**
- touch:
  - `main/repository/CoursesRepositoryImpl.kt` (the three functions above)
  - `test/repository/CoursesRepositoryImplTest.kt`
- leave alone:
  - `CourseDao.kt`
  - `getCourseById` itself, which still needs steps for its other callers
  - `getCourseDetailModel`
  - `ProgressRepositoryImpl.kt` (task 4)

**steps:**
1. `getCourseTitleById`: return `if (courseId.isBlank()) null else courseDao.getByCourseId(courseId)?.courseTitle`.
2. `getCourseProgress`: replace `getCourseById(courseId)?.courseTitle` with `courseDao.getByCourseId(courseId)?.courseTitle`.
3. `getMyCourseIds`: build the array from `courseDao.getForUserPattern(userIdPattern(userId)).mapNotNull { it.courseId }`. This is the same SQL `getMyCourses(userId)` uses at line 112, minus `mapCourses`.
4. Add or adjust tests: each of the three functions returns the same value as before, and `courseStepDao.getByCourseId` / `getByCourseIds` are not called (`coVerify(exactly = 0)`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - the course steps screen still shows the course title;
  - the course progress screen still shows title and step counts;
  - a full sync still uploads the user's shelf with the right course ids.

**size budget:** ~35 changed lines, 2 files

**out of scope:**
- Do not add new DAO queries.
- Do not change `mapCourse` / `mapCourses`.

---

### 6. Drain the download-link buffers after each save instead of rewriting them forever (roadmap 5+7, moves 9)

**context:**
- Two app-lifetime buffers collect download links during sync and are never cleared:
  - `VoicesRepositoryImpl` (a `@Singleton`) keeps `private val concatenatedLinks = ArrayList<String>()` (`main/repository/VoicesRepositoryImpl.kt:40`), so duplicates pile up. It is filled at lines 468-473 and saved after every news sync page (line 435).
  - `MyCourse`'s companion `HashSet` (`main/model/MyCourse.kt:81`) is saved per courses sync page, per shelf batch and at the end of sync.
- Each save (`VoicesRepositoryImpl.kt:553-567`, `MyCourse.kt:96-110`) parses the whole stored JSON from prefs, merges in the entire buffer again, and rewrites the SharedPreferences XML. So cost grows with every page for the life of the process.

**files:**
- touch:
  - `main/repository/VoicesRepositoryImpl.kt` (field at line 40, `saveConcatenatedLinksToPrefs` at lines 553-567)
  - `main/model/MyCourse.kt` (companion lines 81-110)
  - `test/repository/VoicesRepositoryImplTest.kt`
  - `test/model/MyCourseTest.kt`
- leave alone:
  - the call sites in `CoursesRepositoryImpl.kt` and `SyncManager.kt:499`
  - `SharedPrefManager.kt`
  - `ConfigurationsRepositoryImpl.getQueuedDownloads`

**steps:**
1. In both save functions, inside the existing `synchronized` block, copy the buffer and then `clear()` it.
2. Return early before touching prefs when the copied list is empty.
3. Change the `VoicesRepositoryImpl` field to a `LinkedHashSet<String>` so a page with repeated links doesn't buffer duplicates. Keep the `synchronized(concatenatedLinks)` usage at line 468.
4. Keep the merge-with-existing-prefs logic, so links saved earlier are not lost. Only the in-memory buffer is drained.
5. Tests:
   - two consecutive saves with no new links call `setConcatenatedLinks` once (`VoicesRepositoryImplTest`, via `insertNewsList` / bulk insert with a link in the message);
   - `MyCourse.saveConcatenatedLinksToPrefs` called twice after one `addConcatenatedLink` writes once and keeps the link (`MyCourseTest`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, after a fresh full sync, the "queued downloads" list (read by `ConfigurationsRepositoryImpl.getQueuedDownloads`) contains the same links as before, with no duplicates.

**size budget:** ~50 changed lines, 4 files

**out of scope:**
- Do not remove the per-shelf-batch save in `CoursesRepositoryImpl`.
- Do not move link storage to Room.

---

### 7. Cap the notification read sync at the file's existing concurrency limit (roadmap 5+7)

**context:**
- `TransactionSyncManager.syncNotificationReads` (`main/services/sync/TransactionSyncManager.kt:441-474`) runs on every full sync (`SyncManager.kt:204`).
- It launches one unbounded `async` per pending read notification, and each makes two HTTP calls (`getJsonObject` then `putDoc`). After a user clears a long notification list, that can mean hundreds of concurrent requests against a small Raspberry-Pi Planet server.
- The same file already caps its attachment downloads with `Semaphore(MAX_CONCURRENT_ATTACHMENT_DOWNLOADS)` (constant at line 98, used at lines 350/368/384). The uploaders cap at 6 too (`UploadCoordinator.kt:132`).

**files:**
- touch:
  - `main/services/sync/TransactionSyncManager.kt` (`companion object` at line 97, `syncNotificationReads` at lines 441-474)
  - `test/services/sync/TransactionSyncManagerTest.kt` (existing test at line 129)
- leave alone:
  - `NotificationsRepositoryImpl.kt`
  - `SyncManager.kt`
  - the per-table pull code in the same file

**steps:**
1. Add `private const val MAX_CONCURRENT_NOTIFICATION_SYNCS = 6` to the companion object.
2. In `syncNotificationReads`, create one `Semaphore` before `pending.map` and wrap each `async` body in `semaphore.withPermit { … }`. Keep the 404 → `Pair(id, null)`, the error → `null` and the `CancellationException` rethrow exactly as they are.
3. Keep the single `markNotificationsSynced(successfulSyncs)` call at the end.
4. Add a test with more pending notifications than the limit. It uses a `getJsonObject` stub that records peak concurrent calls, then asserts the peak is ≤ 6 and every notification is still marked synced.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, mark 20+ notifications read offline, then sync. All are read on the Planet server, and the unread badge stays cleared after the next sync.

**size budget:** ~45 changed lines, 2 files

**out of scope:**
- Do not convert to `_bulk_docs` / `_all_docs`. That is a follow-up once this is merged.

---

### 8. Open Team Voices with one feed load and a SQL count for the badge watermark (roadmap 3+7, moves 10)

**context:**
- When a team's Voices tab opens, `main/ui/teams/voices/TeamsVoicesFragment.kt:123-128` subscribes to `observeDiscussions` and also runs a one-shot load:
  `val realmNewsList = viewModel.getFilteredNews(getEffectiveTeamId()); showRecyclerView(realmNewsList)`
- That one-shot reads every top-level team post with full message text through `NewsDao.getTopLevelByTeam`, which is the same SQL the flow already runs. It renders the adapter twice and exists only so `TeamsVoicesViewModel.getFilteredNews` (`TeamsVoicesViewModel.kt:58-62`) can pass `newsList.size` to `updateTeamNotification`.
- `VoicesRepository.countTopLevelByTeam` (`main/repository/VoicesRepository.kt:46`, backed by `NewsDao.countTopLevelByTeam`) already returns that number. `test/repository/TeamChatBadgeIntegrationTest.kt:144-155` asserts that the count equals the displayed feed size.

**files:**
- touch:
  - `main/ui/teams/voices/TeamsVoicesFragment.kt` (`onViewCreated`, lines 123-128)
  - `main/ui/teams/voices/TeamsVoicesViewModel.kt` (`getFilteredNews`, lines 58-62)
  - `test/ui/teams/voices/TeamsVoicesViewModelTest.kt` (test at lines 62-74)
- leave alone:
  - `VoicesRepository.kt` / `VoicesRepositoryImpl.kt` (task 6 owns the Impl; `getFilteredNews` stays in the interface for its other users)
  - `VoicesAdapter.kt` (task 10)

**steps:**
1. In the ViewModel, replace `suspend fun getFilteredNews(teamId)` with `fun markDiscussionsSeen(teamId: String)`. It launches in `viewModelScope` and calls `notificationsRepository.updateTeamNotification(teamId, voicesRepository.countTopLevelByTeam(teamId).toInt())`.
2. In the fragment, delete the one-shot `lifecycleScope.launch { … showRecyclerView(realmNewsList) }` block and call `viewModel.markDiscussionsSeen(getEffectiveTeamId())` in its place. The `discussions` collector (line 133-135, `setData` → `showRecyclerView`) stays the only render path.
3. Rewrite the ViewModel test. It verifies:
   - `updateTeamNotification(teamId, count)` is called with the stubbed `countTopLevelByTeam` value;
   - `voicesRepository.getFilteredNews` is never called.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green, including `TeamChatBadgeIntegrationTest`.
- Manually:
  - opening a team's Voices tab shows the posts once, with no double flicker;
  - the team's chat badge clears after opening;
  - a new post from another member brings the badge back.

**size budget:** ~35 changed lines, 3 files

**out of scope:**
- Do not make `_discussions` nullable or change the empty-state handling.
- Do not change `NewsDao`.

---

### 9. Replace O(n²) id lookups in the Resources list with a map built once per list update (roadmap 7+8)

**context:**
- `ResourcesFragment.onSelectedListChange` (`main/ui/resources/ResourcesFragment.kt:572-580`) maps the adapter's whole selected list back to library models with `allResourceModels.find { it.item.id == item.id }`. It does this on every checkbox tap and on "select all", which over N resources is ~N²/2 string compares on the UI thread.
- `onTagClicked(TagItem)` (line 583) flattens the tags of every resource on each tag tap.
- `onSingleResourceDownloaded` (line 596) and `onResourceClicked` (line 602) also scan linearly.

**files:**
- touch:
  - `main/ui/resources/ResourcesFragment.kt`:
    - field `allResourceModels` (line 86) and its assignments at lines 212, 249 and 781-788
    - `onSelectedListChange`, `onTagClicked(TagItem)`, `onSingleResourceDownloaded`, `onResourceClicked`
- leave alone:
  - `ResourcesAdapter.kt` and `ResourcesCardBinder.kt` (open PR 18540)
  - `ResourcesViewModel.kt`
  - `ResourcesListFilter`

**steps:**
1. Turn `allResourceModels` into a property with a private setter-backed field. Each assignment rebuilds three maps (or computes them lazily per assignment):
   - `modelsById = value.associateBy { it.item.id }`
   - `modelsByLocalAddress = value.associateBy { it.item.resourceLocalAddress }`
   - `tagsById = value.asSequence().flatMap { it.tags.asSequence() }.associateBy { it.id }`
   The three existing assignment sites then need no other change.
2. `onSelectedListChange`: `list.mapNotNull { modelsById[it.id]?.library }`.
3. `onTagClicked(TagItem)`: `tagsById[tag.id]`. `onSingleResourceDownloaded`: `modelsByLocalAddress[localAddress]?.item?.id`. `onResourceClicked`: `modelsById[item.id]`.
4. Remove the now-unused linear scans. Do not leave the old code commented out.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green (`ResourcesFragmentActionTest`, `ResourcesFragmentStateTest`).
- Manually, in My Library with many resources:
  - "select all" is instant, and the Download/Remove buttons enable correctly;
  - tapping a tag chip filters by that tag;
  - tapping a card opens its detail;
  - a finished single download flips the card to offline.

**size budget:** ~35 changed lines, 1 file

**out of scope:**
- Do not move `applyFiltersAndUpdateUI` / `getFilteredCount` off the main thread. The `OnFilterListener.filter(): Int` contract makes that a bigger change.

---

### 10. Use the shared zoomable-image viewer in VoicesAdapter and hoist its per-bind dimension lookups (roadmap 6+7+8, moves 10)

**context:**
- `main/ui/voices/VoicesAdapter.kt:1168-1190` has a private `showZoomableImage` that copies `utils/ImageViewerUtils.showZoomableImage` (`main/utils/ImageViewerUtils.kt:17-58`), except that the shared one:
  - dismisses an already-open dialog, so the copy can stack dialogs on a double tap;
  - handles `http(s)` URLs;
  - inflates through view binding.
- The Enterprises adapters and `MarkdownUtils` already use the shared one.
- `loadSingleImage`, `addImageToContainer`, `loadLibraryImage` and `addLibraryImageToContainer` (lines 1094-1164) call `getDimensionPixelSize(R.dimen.image_thumbnail_size_120/100)` and recompute the 4dp margin for every image on every bind of every news row.

**files:**
- touch:
  - `main/ui/voices/VoicesAdapter.kt`: the four image helpers at lines 1094-1164, the private `showZoomableImage` at 1168-1190, and imports at lines 4, 19, 20, 26 and 44 (only if they become unused)
  - `test/ui/voices/VoicesAdapterImagesTest.kt`
- leave alone:
  - `ImageViewerUtils.kt` (reuse it unchanged)
  - `VoicesRepositoryImpl.kt` (task 6)
  - `TeamsVoicesFragment.kt` (task 8)
  - `loadGlideImage` / `isGif`

**steps:**
1. Delete the private `showZoomableImage` and route its four call sites to `ImageViewerUtils.showZoomableImage(context, path)`.
2. Add three adapter-level `private val … by lazy` values read from `context.resources`: `thumbnailSize120`, `thumbnailSize100` and `thumbnailMarginPx` (`(4 * displayMetrics.density).toInt()`). Use them in the four helpers instead of the per-call lookups.
3. Remove only the imports that become unused: `Dialog`, `PhotoView`, `toDrawable`, `ContextCompat`, `EdgeToEdgeUtils`. Grep the file first, because `ContextCompat` and `EdgeToEdgeUtils` may still be used elsewhere in it.
4. Extend `VoicesAdapterImagesTest` so a bound row with two images still adds two thumbnails of the expected size to `llNewsImages`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- `python3 .agents/skills/kotlin-importing/kotlin-importing.py --check app/src` reports nothing for `VoicesAdapter.kt` (only if the submodule is initialised).
- Manually, in community and team Voices:
  - tapping a post image opens the full-screen viewer;
  - double-tapping does not stack two viewers;
  - close returns to the feed;
  - thumbnails keep their size.

**size budget:** ~50 changed lines (net negative), 2 files

**out of scope:**
- Do not touch the emoji/label popup dimension lookups (lines 839-964).
- Do not change Glide options.

---

## self-check

- [x] 10 tasks, each independently mergeable
- [x] No file appears in two tasks:
  - production: QuestionDao, SurveysRepositoryImpl, SubmissionDao, SubmissionsRepositoryImpl, UploadConfigs, CourseStepDao, ProgressRepositoryImpl, CoursesRepositoryImpl, VoicesRepositoryImpl, MyCourse, TransactionSyncManager, TeamsVoicesFragment, TeamsVoicesViewModel, ResourcesFragment, VoicesAdapter
  - tests: one set per task
- [x] Every cited path, class, function and line was opened at `2093a62`
- [x] Every task has all 7 template sections and is over 15 lines
- [x] No task touches a file from an open PR updated in the last week; the open-PR files are listed in the header
- [x] No new dependencies, no schema or DB-version changes, no TODO placeholders

— claude code · claude opus 5.5 · low

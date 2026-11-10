# myPlanet refactor round — task-generation plan

date: 2026-11-10 · base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 (master) · open PRs checked: 18555, 18554, 18551, 18540, 18539, 18455, 18076, 18045, 18029, 17832, 17811, 17694, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13355, 13287, 10993, 8175, 4075

Off-limits ruling applied: files touched by PRs updated within the last week (18555 gradle wrapper; 18554 CLAUDE.md; 18551 WebViewActivity.kt; 18540 the download/storage stack including ResourcesRepositoryImpl.kt, DownloadService.kt, DownloadWorker.kt, DownloadStopReceiver.kt, ResourcesAdapter.kt, ResourcesCardBinder.kt, StorageBreakdownFragment.kt, StorageBreakdownViewModel.kt, DialogUtils.kt, DownloadUtils.kt, PartialDownloads.kt, MainApplication.kt, AndroidManifest.xml; 18539 BaseResourceFragment.kt; 18455 release workflows, app/build.gradle, proguard-rules.pro) are excluded from every task below. No open PR carries a "review ready" or "merge" label. Every path, class and function cited below was opened and confirmed on the base commit.

---

### 1. strip android.util.Log from CommunityRepositoryImpl (roadmap 1+9)

context: `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt:3` has `import android.util.Log`, the file's only android import. It is used exactly once, in `syncCommunityDocs()` at line ~57 (`Log.w(TAG, "syncCommunityDocs failed", e)`), where the failure is already signaled to the caller by returning `false`. Roadmap 9 requires repositories to end with zero android.* imports; this file is one import away from clean.
files: app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt only. Leave alone: CommunityDao.kt, model/Community.kt, di/RepositoryModule.kt, and the callers of syncCommunityDocs.
steps:
1. Delete the `import android.util.Log` line.
2. In `syncCommunityDocs()` catch block, drop the Log.w line and keep `false` as the return value.
3. Delete the now-unused `TAG` constant and the `companion object` block.
4. Run the repository unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; `grep -n "android" app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt` returns nothing; a failed community sync still returns false (unchanged behavior, no crash path added).
size budget: ~5 changed lines, 1 file
out of scope: do not introduce a logger abstraction or Timber; do not change retry or sync scheduling behavior.

---

### 2. strip android.util.Log from RetryRepositoryImpl (roadmap 1+9)

context: `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt:3` imports android.util.Log for two log calls inside `executeOperation()`: `Log.w(TAG, "Network error during retry for ${operation.id}", e)` (~line 130) and `Log.e(TAG, "Unexpected error during retry for ${operation.id}", e)` (~line 134). Both catch blocks already call `markFailed(operation.id, e.message, null)`, which persists the error message in the retry_operations table — the Log lines are redundant diagnostics and the file's only android import.
files: app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt only. Leave alone: RetryDao.kt, model/RetryOperation.kt, services/retry/ (the queue processor), di/RepositoryModule.kt.
steps:
1. Delete `import android.util.Log` (line 3).
2. Remove the Log.w line in the `catch (e: IOException)` block; keep the markFailed + RetryableFailure returns.
3. Remove the Log.e line in the generic `catch (e: Exception)` block; keep the markFailed + RetryableFailure returns.
4. Remove the unused `TAG` constant from the companion object (keep `PREVIEW_LIMIT`).
5. Run retry repository tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; no android imports remain in the file; failed operations still record their error via markFailed and surface as RetryableFailure/TerminalFailure exactly as before.
size budget: ~6 changed lines, 1 file
out of scope: no changes to retry backoff, attempt counters, or the RetryQueue worker; no new logging framework.

---

### 3. strip android.util.Log from HealthRepositoryImpl (roadmap 1+9)

context: `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:3` imports android.util.Log, used in five catch blocks (`getExaminationConditions`, `uploadHealthData`, `decodeHealth`, `updateUserHealthProfile`, `getPatientHealthRecords`), each of which already degrades gracefully (empty map, skipped upload, null return). This android import blocks roadmap 9's zero-android-imports goal for the repository layer.
files: app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt only. Leave alone: HealthExaminationDao.kt, utils/AndroidDecrypter.kt (its Android coupling is a separate, larger problem — do not touch), services/UploadToShelfService.kt.
steps:
1. Delete `import android.util.Log` (line 3).
2. Remove all five Log.e lines, keeping each catch block's existing fallback behavior identical.
3. Verify no leftover unused imports or constants (there is no TAG constant in this file; confirm nothing else referenced Log).
4. Run health repository unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; no android imports remain in HealthRepositoryImpl.kt; health profile save/load and pending-examination sync behave as before (failures still return null/empty results, never throw).
size budget: ~7 changed lines, 1 file
out of scope: do not refactor AndroidDecrypter, encryption key handling, or the userRepository Lazy dependency; do not add a logging abstraction.

---

### 4. strip android.util.Log from PersonalsRepositoryImpl (roadmap 1+9)

context: `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt:3` imports android.util.Log for three Log.w calls, all inside `uploadPersonal()`. Every failure path there already returns a typed `PersonalUploadResult` (`AttachmentFailed`/`DocumentFailed`) carrying the message and cause, so the log lines duplicate information the caller surfaces in the UI. It is the file's only android import.
files: app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt only. Leave alone: PersonalsRepository.kt, PersonalDao.kt, ui/personals/PersonalsViewModel.kt, ui/personals/PersonalsFragment.kt.
steps:
1. Delete `import android.util.Log` (line 3).
2. In `uploadPersonal()`, remove the Log.w in the attachment catch block, the Log.w for the unsuccessful attachment response, and the Log.w in the outer catch — keep all three return values unchanged.
3. Remove the now-unused `TAG` constant and companion object.
4. Run personals repository unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; no android imports remain in the file; uploading a personal resource still returns Success/AlreadyUploaded/AttachmentFailed/DocumentFailed with the same messages.
size budget: ~6 changed lines, 1 file
out of scope: do not change upload ordering, attachment naming, or the PersonalUploadResult contract; no logger injection.

---

### 5. strip android.util.Log from the News model (roadmap 1+9)

context: `app/src/main/java/org/ole/planet/myplanet/model/News.kt:3` imports android.util.Log, used in six catch blocks across `isCommunityNews`, `calculateSortDate()` and `createNews(...)` — every one of which already falls back to a safe default (false, `time`, empty fields). Roadmap 9 requires models to end with zero android.* imports; Log is the only android.* import in this file (the androidx.room annotations stay until the KMP module exists).
files: app/src/main/java/org/ole/planet/myplanet/model/News.kt only. Leave alone: data/room/dao/NewsDao.kt, repository/VoicesRepositoryImpl.kt, ui/voices/ (all of it).
steps:
1. Delete `import android.util.Log` (line 3).
2. Remove the Log.w in `isCommunityNews` catch, keeping `return false` behavior.
3. Remove the Log.w in `calculateSortDate()` catch, keeping the `return time` fallback.
4. Remove the three Log.w lines inside `createNews(...)` (updatedDate parse, conversation parse, news json parse catches), keeping the existing fallbacks.
5. Remove the now-unused `TAG` constant from the companion object.
6. Run model/news unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; News.kt contains no android.* import (androidx.room imports remain); voices feed, community detection, and news creation behave as before.
size budget: ~8 changed lines, 1 file
out of scope: no Room/@Entity changes, no viewIn format changes, no logging replacement.

---

### 6. hoist task filtering and assignee filtering out of TeamsTasksFragment into TeamsTasksViewModel (roadmap 3+10, helps 1)

context: `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt` holds five data-shaping functions in its companion object (`filterAllTasks`, `filterTodoTasks`, `filterInProgressTasks`, `filterCompletedTasks`, `filterMyTasks`) plus member filtering inside `loadAssignableMembers()` (`userList.filter { user.getFullName().isNotBlank() || !user.name.isNullOrBlank() }`). This is domain logic living in the view layer: it is untestable without a fragment, and roadmap 10 needs state and shaping hoisted so a future Compose screen stays portable. TeamsTasksViewModel (same package) already exposes repository passthroughs `getJoinedMembers(teamId)` and `fetchAssigneeNames(...)`, so it is the natural home.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt and app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModel.kt; optionally one new test file app/src/test/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModelFilterTest.kt. Leave alone: TeamsTasksAdapter.kt, ui/teams/TeamViewModel.kt, repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt.
steps:
1. Add to TeamsTasksViewModel: a pure `filterTasks(tasks: List<TeamTask>, tab: TaskTab, userId: String?): List<TeamTask>` (introduce a small `enum class TaskTab { ALL, TODO, IN_PROGRESS, COMPLETED, MINE }` in the viewmodel file) implementing exactly the five current companion functions' filter+sort rules.
2. Add `suspend fun getAssignableMembers(teamId: String): List<UserEntity>` to TeamsTasksViewModel applying the current name/non-blank filter.
3. In TeamsTasksFragment, map the checked toggle id to TaskTab, call `teamsTasksViewModel.filterTasks(...)` inside `updateTasks()`, and delegate `loadAssignableMembers()` to `getAssignableMembers(teamId)`.
4. Delete the fragment companion object with the five filter functions and the private allTasks/todoTasks/inProgressTasks/completedTasks/myTasks wrappers.
5. Add the focused unit test for filterTasks (all five tabs, completed ordering last, my-tasks excludes completed) if the test file is created.
acceptance: ./gradlew testDefaultDebugUnitTest green; the teams tasks screen shows identical lists for all five tabs (All/Todo/In progress/Completed/Mine) and the assign-member dialog still lists only members with a usable name.
size budget: ~120 changed lines, 2-3 files
out of scope: no Compose migration, no changes to task creation/deadline handling, no repository or DAO changes, no adapter changes.

---

### 7. extract survey search matching out of SurveysViewModel into a tested pure component (roadmap 3+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt` embeds two pieces of domain logic: `private fun filter(s: String, list: List<StepExam>)` (normalize query, rank startsWith matches above contains matches) and `private fun getSortDate(survey: StepExam)` (adopted surveys sort by adoptionDate when set). Both are pure functions of model data trapped in a Hilt viewmodel, untested, and unavailable to a future use-case layer (roadmap 3). There is no unit test covering the ranking behavior today.
files: app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt (edit); new app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveySearchMatcher.kt; new app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveySearchMatcherTest.kt. Leave alone: SurveysRepository.kt, SurveysRepositoryImpl.kt, ui/surveys fragment and adapter, utils/StringUtils.kt.
steps:
1. Create `internal object SurveySearchMatcher` in the new file with `fun sortDateOf(survey: StepExam): Long` and `fun filter(query: String, list: List<StepExam>): List<StepExam>` copied verbatim in behavior (same StringUtils.normalizeText usage, same startsWith-then-contains ranking).
2. In SurveysViewModel, replace the bodies of `getSortDate` and `filter` with delegation to SurveySearchMatcher (or delete the privates and call the matcher directly from applyFilterAndSort).
3. Keep the four SortOption branches and their ordering semantics exactly as-is.
4. Add SurveySearchMatcherTest covering: empty query returns input order, startsWith outranks contains, multi-word query requires all parts, adopted survey with adoptionDate > 0 sorts by adoptionDate, non-adopted sorts by createdDate.
5. Run the new test plus the existing surveys tests.
acceptance: ./gradlew testDefaultDebugUnitTest green (including the new SurveySearchMatcherTest); survey search and the date/title sort toggles behave identically on the surveys screen.
size budget: ~90 changed lines, 3 files
out of scope: no repository/DAO changes, no changes to adoptSurvey/sendSurveyToUsers, no UI layout changes, no new dependencies.

---

### 8. drop the redundant re-query in EventsRepositoryImpl.toggleAttendance (roadmap 7+1)

context: In `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt`, `toggleAttendance(meetupId, userId)` fetches the row with `meetupDao.getByMeetupId(meetupId)`, mutates `userId` on the in-memory instance, calls `meetupDao.upsert(meetup)`, and then runs `return getMeetupById(meetupId)` — which is the identical getByMeetupId query again, a second database round-trip returning the row already in hand. On slow devices this doubles the join/leave tap latency for no consistency benefit (upsert is synchronous within the same suspend fun).
files: app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt only. Leave alone: data/room/dao/MeetupDao.kt, EventsRepository.kt, model/Meetup.kt, and the meetup UI callers (the returned Meetup instance carries the same data they receive today).
steps:
1. In `toggleAttendance`, after `meetupDao.upsert(meetup)`, return the mutated `meetup` instance directly instead of calling `getMeetupById(meetupId)`.
2. Confirm the early-return branch (`userId.isBlank()` → `getMeetupById(meetupId)`) is untouched — that path performs no mutation and still needs the fetch.
3. Run events repository tests; if any test asserts the re-fetch (e.g., verifies getByMeetupId called twice), update the verification to once.
acceptance: ./gradlew testDefaultDebugUnitTest green; tapping join/leave on a meetup still flips attendance and returns the updated meetup with the correct userId set or cleared.
size budget: ~3 changed lines, 1 file
out of scope: no DAO changes, no transaction annotation changes, no changes to insertMeetupsFromSync or batchInsertMeetups.

---

### 9. resolve latest chat revision with a SQL MAX query instead of loading all revs (roadmap 7+1)

context: `ChatRepositoryImpl.getLatestRev(id)` (app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt) calls `chatDao.getRevsByDocId(id)` and computes `maxByOrNull { rev -> rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }` in Kotlin. The DAO method (app/src/main/java/org/ole/planet/myplanet/data/room/dao/ChatDao.kt:15-16, `SELECT _rev FROM chat_history WHERE _id = :docId`) loads every revision string for the document just to pick one. CouchDB revs are `N-hash`, so SQLite can pick the winner with `ORDER BY CAST(substr(_rev, 1, instr(_rev, '-') - 1) AS INTEGER) DESC LIMIT 1`.
files: app/src/main/java/org/ole/planet/myplanet/data/room/dao/ChatDao.kt and app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt; update app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt (it references getRevsByDocId today). Leave alone: ui/chat/ChatViewModel.kt, ui/chat/ChatHistoryFragment.kt, model/ChatHistory.kt.
steps:
1. Add to ChatDao: `@Query("SELECT _rev FROM chat_history WHERE _id = :docId AND _rev IS NOT NULL ORDER BY CAST(substr(_rev, 1, instr(_rev, '-') - 1) AS INTEGER) DESC LIMIT 1") suspend fun getLatestRevByDocId(docId: String): String?`.
2. Switch ChatRepositoryImpl.getLatestRev to return `chatDao.getLatestRevByDocId(id)` directly.
3. Remove `getRevsByDocId` from ChatDao if nothing else uses it (search shows only getLatestRev and the test); remove it to honor the no-unused-code rule, otherwise keep.
4. Update ChatRepositoryImplTest to stub/assert the new DAO call, preserving the existing expectations for null/blank results.
5. Run chat repository tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; continuing an existing chat still resolves the highest-generation revision (chat continue works, no "conflict" regressions).
size budget: ~15 changed lines, 2-3 files
out of scope: no changes to sendNewChatRequest/continueConversation flows, no schema or index changes, no new DAO return types beyond the nullable String.

---

### 10. inject BroadcastService into NotificationActionReceiver instead of the EntryPoint lookup (roadmap 4)

context: `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt` is already `@AndroidEntryPoint` and field-injects NotificationsRepository, DispatcherProvider and the application scope — yet inside `markNotificationAsRead(...)` it still resolves BroadcastService through the manual entry-point helper `getBroadcastService(context)` (imported from di/ServiceDependenciesEntryPoint.kt, used at `val broadcastService = getBroadcastService(context)`). BroadcastService is a plain `@Singleton` with an `@Inject constructor()` (services/BroadcastService.kt:10), so the indirection is leftover DI debt that roadmap 4 wants gone.
files: app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt and app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt. Leave alone: di/ServiceDependenciesEntryPoint.kt — its `broadcastService()`/`getBroadcastService` must stay because DownloadService.kt still uses them and DownloadService.kt is owned by open PR 18540.
steps:
1. Add `@Inject lateinit var broadcastService: BroadcastService` to NotificationActionReceiver alongside the existing injected fields.
2. Replace the `getBroadcastService(context)` call in `markNotificationAsRead` with the injected field; delete the `import org.ole.planet.myplanet.di.getBroadcastService` line.
3. In NotificationActionReceiverTest, replace the `every { getBroadcastService(any()) } returns mockk(relaxed = true)` static mock with a relaxed BroadcastService mock set on the receiver, matching how the test already handles notificationsRepository.
4. Run the receiver tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; marking a notification read from a system notification still emits the NOTIFICATION_READ_FROM_SYSTEM_LOCAL broadcast and clears the badge as before.
size budget: ~15 changed lines, 2 files
out of scope: do not remove or alter ServiceDependenciesEntryPoint, do not touch DownloadService or the retry queue entry point, no behavior changes to notification actions.

---

Self-check passed: 10 tasks; no file appears in two tasks; every cited path/class/function opened and confirmed on 2093a6224b221079f1413e2ab1f22cebec8cf736; every task has context/files/steps/acceptance/size budget/out of scope; no task touches a file from an open PR updated within the last week, and no open PR carries a review-ready or merge label.

signed: github copilot · kimi k3 · high

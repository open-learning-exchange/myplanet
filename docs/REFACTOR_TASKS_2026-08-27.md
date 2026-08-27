# myPlanet refactor round — performance quick wins

- **Date**: 2026-08-27
- **Base commit**: `89fd72c251df68ed01094091d4de7ba7a2571ebe` (`master`, "all: smoother notifications icons lookup handling (fixes #16286) (#16186)")
- **Open PRs checked (37)**: 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075
- **Method**: every open PR head was fetched and diffed against its merge-base with `master`; the union (803 files, 308 under `app/src`) was excluded before candidate selection. None of the 13 files below is touched by any open PR — including all `ready`/`review`/`merge`-labelled ones (16274, 16258, 16257, 16192).
- **Focus**: performance quick wins · micro-optimizations that unblock bigger refactors · obvious inefficiencies removable without rewrites.
- Every task is independently mergeable in any order. No file appears in two tasks. No new dependencies.

---

### 1. read storage stats once per FileUtils helper and stop creating directories during existence checks (roadmap 7)

context: `FileUtils.totalAvailableMemoryRatio` (`utils/FileUtils.kt:347-351`) and `availableOverTotalMemoryFormattedString` (`:353-357`) each call `totalMemoryCapacity(context)` and `totalAvailableMemory(context)`, and both of those delegate to `getStorageStats(context)` (`:359`) — so each helper runs the whole probe twice: two `getSystemService` lookups plus `StorageStatsManager.getTotalBytes`/`getFreeBytes` binder round trips. They run on the login screen (`ui/sync/LoginActivity.kt:157`), the settings screen, the storage breakdown screen and `TaskNotificationWorker`. Separately, `checkFileExist` (`:127-131`) resolves its path through `getSDPathFromUrl` → `createFilePath` (`:51-80`), which calls `mkdirs()` and can throw `RuntimeException` — so a pure "does this file exist?" question writes directories to disk, and it is called from list binding (`ui/courses/InlineResourceAdapter.kt:159`).

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` — `totalMemoryCapacity`, `totalAvailableMemory`, `totalAvailableMemoryRatio`, `availableOverTotalMemoryFormattedString`, `getStorageStats`, `checkFileExist`, `createFilePath`, `getSDPathFromUrl`. Do NOT touch `utils/UrlUtils.kt`, `utils/NetworkUtils.kt` or `ui/courses/InlineResourceAdapter.kt` — other tasks in this round own them, and `getSDPathFromUrl`'s directory-creating behaviour must stay intact for its download callers.

steps:
1. In `totalAvailableMemoryRatio`, call `getStorageStats(context)` once into a local `Pair` and derive total/available from `.first`/`.second` instead of calling the two public wrappers.
2. Do the same in `availableOverTotalMemoryFormattedString`.
3. Leave `totalMemoryCapacity` and `totalAvailableMemory` in place with their current signatures — `TaskNotificationWorker` and `StorageBreakdownFragment` call them.
4. Add a private path resolver that mirrors `createFilePath`'s path arithmetic (the `filename.contains("/")` split into sub-directory + file name) but performs no `mkdirs()` and throws nothing, and make `checkFileExist` use it via a non-creating counterpart of `getSDPathFromUrl`.
5. Keep `getSDPathFromUrl` itself unchanged so download code still gets its directories created.

acceptance: `./gradlew testDefaultDebugUnitTest` green — in particular `FileUtilsTest.checkFileExist_returnsTrueWhenFileExists`, `checkFileExist_returnsFalseWhenFileDoesNotExist`, `checkFileExist_returnsFalseWhenUrlIsNullOrEmpty`, `getSDPathFromUrl_preservesNestedAttachmentPath` and `getSDPathFromUrl_singleSegmentFileHasNoSubdirectory` must still pass unmodified. Manually: the login screen and Settings still show the same "available/total" storage string, and the storage breakdown screen still reports the same totals.

size budget: ~30 changed lines, 1 file.

out of scope: no caching of the storage figures across calls (they must stay live), and no change to `getOlePath`, `getExternalFilesDir` or the existing `cachedExternalFilesDir` warm-up.

---

### 2. memoize the basic-auth header instead of rebuilding it on every request (roadmap 7)

context: `UrlUtils.header` (`utils/UrlUtils.kt:29-33`) is a getter that, on every single access, reads two `SharedPreferences` strings, concatenates them, allocates a `ByteArray` and Base64-encodes it. It has 33 call sites, including one per outgoing API call in the upload/sync repositories and one per list item bind in `ui/courses/CoursesAdapter.kt:300` and `ui/courses/CourseDetailFragment.kt:107`, where it builds a Glide `LazyHeaders` for each cover image. The credentials change only on login/server switch, so the encode is pure repeated work.

files: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` — the `header` property and `basicAuthHeader`, plus the existing `resetForTesting()` hook at `:24-27`. Do NOT touch `ui/courses/CoursesAdapter.kt` or `ui/courses/CourseDetailFragment.kt` (call sites stay as they are), and do NOT touch `utils/FileUtils.kt` or `utils/NetworkUtils.kt` — other tasks own them.

steps:
1. Add a private `@Volatile` field holding the last `(username, password, encodedHeader)` triple used to build a header.
2. In the `header` getter, read `spm.getUrlUser()`/`spm.getUrlPwd()` as before, return the cached encoded value when both match the cached credentials, and otherwise call `basicAuthHeader` and store the new triple.
3. Leave `basicAuthHeader(username, password)` public and uncached — it is the pure function and is called directly elsewhere.
4. Clear the cached triple inside the existing `resetForTesting()` so test isolation is preserved.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `UrlUtilsTest` included. Manually: sign in, sync, and open a course list with cover images — images still load and uploads still authenticate; then switch to a different server/user and confirm requests still succeed (proving the cache invalidates on credential change).

size budget: ~15 changed lines, 1 file.

out of scope: no change to `getUrl()`, `baseUrl`, `dbUrl` or any URL-building function; do not introduce a DI-provided header or an OkHttp interceptor here.

---

### 3. resolve the device android id once instead of on every serialization (roadmap 7, 4)

context: `VersionUtils.getAndroidId` (`utils/VersionUtils.kt:35-37`) queries `Settings.Secure` through the content resolver on every call, and `NetworkUtils.getUniqueIdentifier` (`utils/NetworkUtils.kt:172-176`) repeats the same query independently. Both are called per serialized document (`model/SearchActivity.kt:37`, `model/UserEntity.kt:64`, `model/MyPlanet.kt:32/51`, `repository/ActivitiesRepositoryImpl.kt:418`), so a 50-item upload batch issues 50+ content-resolver round trips for a value that cannot change while the process lives. In the same file, `NetworkUtils.getCustomDeviceName` (`:188-191`) re-resolves `coreEntryPoint.sharedPrefManager()` on every call even though the object already holds a memoized `sharedPrefManager` at `:33-35`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt` (`getAndroidId`), `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt` (`getUniqueIdentifier`, `getCustomDeviceName`), `app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsTest.kt` (`init`). Do NOT change the `getCustomDeviceName(context: Context)` signature — `NetworkUtilsMockTest.kt:117` and 15 production call sites pass a context; and do NOT touch `utils/UrlUtils.kt` or `utils/FileUtils.kt`.

steps:
1. In `VersionUtils`, add a `@Volatile private var` holding the resolved id plus a `@Volatile private var` "resolved" flag (the raw platform value can legitimately be `null`, and `NetworkUtilsTest.getUniqueIdentifier_withNullAndroidId_returnsExpectedFormat` depends on that null propagating).
2. Make `getAndroidId` return the cached value when resolved, otherwise query `Settings.Secure`, store it, and return it.
3. Add an `@VisibleForTesting internal fun resetForTesting()` in `VersionUtils` that clears both fields, mirroring `UrlUtils.resetForTesting()`.
4. Make `NetworkUtils.getUniqueIdentifier` build its string from `VersionUtils.getAndroidId(context)` instead of its own `Settings.Secure` lookup, keeping the exact `"${androidId}_${Build.ID}"` format.
5. Make `NetworkUtils.getCustomDeviceName` use the memoized `sharedPrefManager` property instead of calling `coreEntryPoint.sharedPrefManager()`.
6. Call `VersionUtils.resetForTesting()` from `NetworkUtilsTest.init()` so the two `getUniqueIdentifier_*` tests stay independent.

acceptance: `./gradlew testDefaultDebugUnitTest` green — `NetworkUtilsTest.getUniqueIdentifier_returnsExpectedFormat`, `NetworkUtilsTest.getUniqueIdentifier_withNullAndroidId_returnsExpectedFormat`, `NetworkUtilsMockTest.getCustomDeviceName returns correct name from SharedPrefManager` and `VersionUtilsTest` all pass. Manually: sync and upload activities/submissions; the uploaded documents still carry the same `uniqueAndroidId`/`androidId`/`customDeviceName` values as before.

size budget: ~30 changed lines, 3 files.

out of scope: do not remove the now-unused `context` parameter from `getCustomDeviceName` (it would fan out into files other tasks and open PRs own), and do not touch the network-state flow, `startListenNetworkState` or the `NetworkCallback`.

---

### 4. stop re-resolving the locale and copying arrays while grading answers (roadmap 7, 9)

context: `ExamAnswerUtils.checkSelectAnswer` (`utils/ExamAnswerUtils.kt:71-74`) calls `Locale.getDefault()` inside the `any {}` lambda, so the JDK's synchronized default-locale lookup runs once per candidate choice. `checkMultipleSelectAnswer` (`:76-83`) lowercases both sides into two `List`s, converts both to `Array`s via `toTypedArray()`, then `isEqual` (`:94-98`) sorts both arrays in place and compares with `contentEquals` — four intermediate collections and two sorts to answer "are these two sets equal?". This runs for every question of every submission graded in `repository/SubmissionsRepositoryImpl.kt:544` and `:575`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` — `checkSelectAnswer`, `checkMultipleSelectAnswer`, `isEqual`. Leave `checkCorrectAnswer`, `checkTextAnswer`, `getChoiceTextById` and `choiceDisplayValue` behaving exactly as they do (`checkTextAnswer` at `:85-92` already hoists the locale correctly — copy that shape). Do NOT touch `repository/SubmissionsRepositoryImpl.kt` or `repository/CoursesRepositoryImpl.kt` — open PRs own them.

steps:
1. In `checkSelectAnswer`, hoist `Locale.getDefault()` into a local `val` and use it for both the answer and each choice, matching `checkTextAnswer`.
2. In `checkMultipleSelectAnswer`, hoist the locale once and lowercase each side into a `Set<String>` instead of a `List` → `Array`.
3. Replace the `isEqual(Array, Array)` comparison with a set equality check that keeps the existing null semantics (both null → equal; exactly one null → not equal).
4. Delete `isEqual` and the now-unused `java.util.Arrays` import once nothing references them.
5. Confirm no other file calls `isEqual` before deleting it.

acceptance: `./gradlew testDefaultDebugUnitTest` green — `ExamAnswerUtilsTest` must pass untouched, including the `matchingSet` / `subset` / `superset` multi-select cases at lines 34-40 and the case-insensitivity case at line 26. Manually: take a course exam with a select-multiple question, submit, and confirm correct/incorrect grading is unchanged.

size budget: ~20 changed lines, 1 file.

out of scope: no change to the grading rules themselves (duplicate answers, trimming, or partial credit stay exactly as they are), and no signature change to `checkCorrectAnswer`.

---

### 5. drop the duplicate chat-history load on screen open (roadmap 7)

context: `ChatHistoryFragment` calls `refreshChatHistory()` from `onViewCreated` (`ui/chat/ChatHistoryFragment.kt:99`) and again from `onResume` (`:150-153`). Since `onResume` always follows `onViewCreated`, opening the AI chat screen runs `sharedViewModel.loadChatHistoryScreenData(...)` twice back to back — two full loads of chat history, shared news messages, share targets and viewIn ids, plus three `SharedPreferences` reads each (`:155-161`). A third trigger already exists for real changes (`setupRealtimeSync` collects `refreshChatSignal` at `:224-226`), so the `onViewCreated` call is pure duplication.

files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` — the `refreshChatHistory()` call at line 99 inside `onViewCreated`, and `onResume`/`refreshChatHistory` at `:150-161`. Do NOT touch `ui/chat/ChatViewModel.kt` or `ui/chat/ChatDetailFragment.kt` — open PR #15198 owns that area; do NOT touch `ui/chat/ChatHistoryAdapter.kt`.

steps:
1. Remove the `refreshChatHistory()` call at line 99 in `onViewCreated`, keeping the surrounding pane/click setup and the `observeScreenData()` call intact.
2. Keep the `onResume` call as the single entry-point load so returning from `ChatDetailFragment` still refreshes.
3. Verify `observeScreenData()` (`:163-217`) is still called before the first emission arrives, so the adapter is attached when data lands.
4. Leave `setupRealtimeSync()`'s `refreshChatSignal` collection unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: open the AI chat screen from the dashboard — the history list populates exactly as before; open a conversation, press back, and confirm the list refreshes; send a message and confirm the new conversation appears in the list without a manual reload.

size budget: ~2 changed lines, 1 file.

out of scope: no change to `ChatViewModel.loadChatHistoryScreenData` or to how the screen data flow is built; do not add a "loaded once" boolean guard — removing the redundant call is the whole fix.

---

### 6. delete the redundant hand-rolled RecyclerView measure pass in submission detail (roadmap 7, 6)

context: `SubmissionDetailFragment.setupRecyclerView` (`ui/submissions/SubmissionDetailFragment.kt:36-70`) installs an anonymous `LinearLayoutManager` whose `onMeasure` loops over every item, calls `recycler.getViewForPosition(i)`, `addView`, `measureChild`, `getDecoratedMeasuredHeight` and `removeAndRecycleView` to sum a total height. That is a full inflate-and-measure of the entire question list on every measure pass. It is also unnecessary: `app/src/main/res/layout/fragment_submission_detail.xml:61-68` already declares the RecyclerView `layout_height="wrap_content"` with `nestedScrollingEnabled="false"` inside a `ScrollView`, and RecyclerView has supported `wrap_content` measurement natively since support library 23.2.

files: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt` — `setupRecyclerView`. Do NOT touch `app/src/main/res/layout/fragment_submission_detail.xml` (it is already correct), and do NOT touch `ui/submissions/SubmissionsAdapter.kt`, `ui/submissions/SubmissionViewModel.kt` or `ui/submissions/SubmissionUiModel.kt` — open PR #14650 owns them. `ui/submissions/QuestionAnswerAdapter.kt` stays as it is.

steps:
1. Replace the anonymous `LinearLayoutManager` subclass with a plain `LinearLayoutManager(context)`.
2. Keep `binding.rvQuestionsAnswers.isNestedScrollingEnabled = false` and `setHasFixedSize(false)` exactly as they are — the outer `ScrollView` still owns scrolling and item heights still vary.
3. Remove the now-unused `RecyclerView` import if nothing else in the file references it.
4. Leave `observeViewModel()` and `adapter.submitList(...)` untouched.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: open a survey/exam submission with many questions from the Submissions list — every question/answer row is visible, the page scrolls to the bottom of the last answer with no clipping and no inner scroll region, and a submission with a single question renders at its natural height.

size budget: ~35 changed lines (mostly deletions), 1 file.

out of scope: do not convert the screen to Compose in this task, and do not change the layout XML or the item layout `item_question_answer.xml`.

---

### 7. hoist the layout inflater and attach rows to their parent in the achievement editor (roadmap 7)

context: `EditAchievementFragment.showAchievementAndInfo` (`ui/user/EditAchievementFragment.kt:249-270`) and `showReference` (`:272-285`) each rebuild their whole container from scratch, and inside the loop they call `LayoutInflater.from(activity)` once per row — re-resolving the inflater per item — then inflate with `EditAttachementBinding.inflate(inflater)` / `EditOtherInfoBinding.inflate(inflater)` with no parent, which discards the row layout's own `layout_*` params before `addView`. `showAchievementAndInfo` is also re-entrant: the delete handler at `:262-265` calls it again, so removing one attachment re-inflates every remaining row.

files: `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt` — `showAchievementAndInfo`, `showReference`, and the `editAttachmentBinding` / `editOtherInfoBinding` fields they assign. Do NOT touch `ui/user/AchievementFragment.kt`, `ui/user/UserProfileFragment.kt` or `ui/user/GamificationViewModel.kt` — open PRs own them; leave `showAddAchievementAlert` and `showReferenceDialog` alone.

steps:
1. In `showAchievementAndInfo`, resolve `LayoutInflater.from(requireContext())` once before the loop into a local `val` and reuse it for every iteration.
2. Inflate each attachment row with the parent form `EditAttachementBinding.inflate(inflater, binding.llAttachment, false)` so the row's own layout params survive, then `addView` the root as today.
3. Make the per-row binding a loop-local `val` instead of reassigning the fragment-level `editAttachmentBinding` field, and delete that field if nothing outside the loop reads it.
4. Apply the same three changes to `showReference` with `binding.llOtherInfo` and `EditOtherInfoBinding`, dropping `editOtherInfoBinding` if it is likewise loop-only.
5. Keep `Utilities.getCloudConfig()` resolved once before the attachment loop (it already is, at `:250`) and keep the existing delete/edit click handlers and their `showAchievementAndInfo()` / `showReference()` re-entry.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: open Profile → Edit Achievements, confirm existing achievement rows and reference rows render with the same spacing and chips as before, add one of each, delete one of each, and confirm the lists rebuild correctly after every edit.

size budget: ~25 changed lines, 1 file.

out of scope: do not convert either container to a RecyclerView, and do not change the row layouts `edit_attachement.xml` / `edit_other_info.xml`.

---

### 8. remove the dead single-title team-name lookup from the notifications repository (roadmap 1, 8, 9)

context: `NotificationsRepository.getTaskTeamName(taskTitle: String): String?` (`repository/NotificationsRepository.kt:24`) and its implementation (`repository/NotificationsRepositoryImpl.kt:252-256`) have no callers left anywhere in `app/src/main` or `app/src/test` — `NotificationsViewModel` was moved onto the batched `getTaskTeamNamesByTaskTitles` (`ui/notifications/NotificationsViewModel.kt:83-85`) and `getTaskTeamNamesByTaskIds` (`:76`). The dead method is the last user of the per-title `teamTaskDao.getByTitle` path and of `teamsRepository.getTeamLabelInfo` from this repository, so it keeps a one-row-per-title query pattern alive in an interface that is otherwise fully batched.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (the `getTaskTeamName` declaration) and `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (the `getTaskTeamName` override). Do NOT touch `data/room/dao/TeamTaskDao.kt` — open PRs own it, so `getByTitle` stays declared there; do NOT touch `ui/notifications/NotificationsViewModel.kt` (task 9 owns it) and do NOT remove `getJoinRequestDetails`, which is still called at `NotificationsViewModel.kt:95`.

steps:
1. Confirm with a repo-wide search over `app/src/main` and `app/src/test` that `getTaskTeamName` (exact word, not the `...ByTaskTitles`/`...ByTaskIds` variants) has no remaining references.
2. Delete the declaration from `NotificationsRepository`.
3. Delete the override from `NotificationsRepositoryImpl`.
4. Remove any import in `NotificationsRepositoryImpl` left unused by the deletion; keep `teamTaskDao` and `teamsRepository`, which the batched methods still use.
5. Leave `getTaskTeamNamesByTaskIds` (`:190-211`) and `getTaskTeamNamesByTaskTitles` (`:258-278`) untouched.

acceptance: `./gradlew testDefaultDebugUnitTest` green, `NotificationsRepositoryImplTest` included. Manually: open the Notifications screen with task notifications present — each task notification still shows its team name, both for notifications carrying a `relatedId` and for those matched by title.

size budget: ~8 changed lines (deletions), 2 files.

out of scope: do not delete `TeamTaskDao.getByTitle`, and do not touch any other method on `NotificationsRepository`.

---

### 9. regroup notifications only when notifications change, not on every selection tap (roadmap 3, 7)

context: `NotificationsViewModel.groupedItems` (`ui/notifications/NotificationsViewModel.kt:50-54`) combines four flows — `_notifications`, `_selectedIds`, `_collapsedGroups`, `_expandedGroups` — and rebuilds everything through `buildGroupedList` (`:224-256`) on each emission. `buildGroupedList` does a full `groupBy` with a `type.lowercase(Locale.ROOT)` per notification (`:232-235`), re-derives the ordered type list (`:236-237`) and recounts unread per group (`:242`). Because `_selectedIds` changes on every tap in multi-select, selecting one item re-groups and re-counts the entire notification list, even though grouping depends solely on `_notifications`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` — the `groupedItems` declaration and `buildGroupedList`. Leave `isGroupDefaultExpanded`, `resolveType`, `typeLabelFor`, `markAsRead`, `loadNotifications` and the `KNOWN_TYPES`/`TYPE_ORDER` companion values as they are. Do NOT touch `ui/notifications/NotificationsAdapter.kt`, `ui/notifications/NotificationsFragment.kt`, `model/NotificationListItem.kt`, or `repository/NotificationsRepositoryImpl.kt` (task 8 owns it).

steps:
1. Add a private function that takes `List<Notification>` and returns the grouping work only: the ordered list of `(type, items, unreadCount)` produced today by lines 232-242, using the same `lowercase(Locale.ROOT)` + `KNOWN_TYPES` normalization and the same `TYPE_ORDER`-then-remainder ordering.
2. Derive a private flow from `_notifications` that maps through that function, so it re-runs only when the notification list itself changes.
3. Rewrite `groupedItems` to combine that derived flow with `_selectedIds`, `_collapsedGroups` and `_expandedGroups`, and have the combine block only assemble `NotificationListItem.Header` / `NotificationListItem.Item` from the pre-grouped data — keeping the expansion rule (`expandedGroups` wins, then `collapsedGroups`, else `unreadCount > 0`) and the `inSelectionMode` flag byte for byte.
4. Delete `buildGroupedList` once both halves replace it, or reduce it to the assembly half.
5. Keep the `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())` sharing configuration unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green — `NotificationsViewModelTest.testGroupIsExpandedByDefaultOnlyWhenItHasUnread`, `testToggleGroupExpansionOverridesTheDefault`, `testToggleGroupExpansionTwiceRestoresTheDefault`, `testMarkAllAsReadClearsOverridesAndCollapsesEveryGroup` and `testLoadNotificationsExtractsRelevantTypesCaseInsensitivelyInOrder` must all pass unmodified. Manually: open Notifications, confirm group order, headers, unread counts and default expansion are unchanged; long-press to enter selection mode and select/deselect several items — headers and counts stay stable and selection is responsive.

size budget: ~45 changed lines, 1 file.

out of scope: do not change the grouping taxonomy, `resolveType`'s message-sniffing rules, or the group labels; do not add caching keyed on anything other than the notification list.

---

### 10. reuse the resolved user in the team members screen instead of re-querying per refresh (roadmap 7, 3)

context: `MembersFragment` resolves the signed-in user three separate ways for one screen. `BaseTeamFragment.onCreate` already loads it into the inherited `user` field (`base/BaseTeamFragment.kt:41-44`), then `MembersFragment.onViewCreated` calls `userSessionManager.getUserModel()` again (`ui/teams/members/MembersFragment.kt:77`), and `loadMembers()` calls it a third time (`:103`). `getUserModel()` is a Room `userDao.getById` query (`repository/UserRepositoryImpl.kt:482-485`), and `loadMembers()` has five call sites (`:82`, `:96`, `:120`, `:146`, `:161`) — accepting a join request, removing a member, promoting a leader and leaving the team each fire another redundant single-row query.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt` — `onViewCreated`'s user-resolution coroutine (`:76-80`) and `loadMembers()` (`:100-109`). Do NOT touch `base/BaseTeamFragment.kt`, `ui/teams/members/RequestsViewModel.kt`, `ui/teams/members/MembersAdapter.kt` or `ui/teams/members/RequestsAdapter.kt`; `repository/TeamsRepositoryImpl.kt` is owned by open PRs and must not be edited.

steps:
1. Add a nullable `currentUserId` field to the fragment, set from the `resolvedUser` already fetched in the `onViewCreated` coroutine at `:76-80`.
2. Change `loadMembers()` to use `currentUserId` when it is already set, and to resolve it once via `userSessionManager.getUserModel()?.id` (storing the result) only when it is still null.
3. Keep the rest of `loadMembers()` identical: `teamsRepository.getJoinedMembersWithVisitInfo(teamId)`, the `isLeader` derivation from the returned members, `membersAdapter?.setUserId(...)`, `updateData(members, isLeader)` and the `showNoData` call.
4. Leave the ordering in `onViewCreated` intact — `loadMembers()` at `:82` may still run before the id resolves, and the null fallback path must cover that case.
5. Leave all five `loadMembers()` call sites unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manually: open a team → Members. The member list shows the same rows, the current user's own row still exposes the correct actions, and after accepting a join request, removing a member, promoting a leader, and leaving the team, the list refreshes with the same content and the leader-only controls appear for a leader and not for a non-leader.

size budget: ~15 changed lines, 1 file.

out of scope: do not move this state into a ViewModel in this task, and do not change `RequestsViewModel.fetchMembers` or the requests section wiring.

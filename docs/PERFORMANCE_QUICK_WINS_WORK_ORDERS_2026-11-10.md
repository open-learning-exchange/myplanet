# myPlanet Refactor Work Orders — Performance Quick Wins

date: 2026-11-10 · base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 · open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075

### 1. replace multi-select answer dual-list sort with frequency-map comparison (roadmap 7+8)
context: `ExamAnswerUtils.kt:77-87` (`checkMultipleSelectAnswer`) normalizes and sorts both `listAns.values`
and `correctChoices` on every multi-select exam question evaluation via
`val selectedAns = listAns.values.map { it.lowercase(locale) }.sorted()` and
`val correctList = correctChoices.map { it.lowercase(locale) }.sorted()`. This allocates four intermediate
`List<String>` instances and runs two $O(N \log N)$ sorts even though multi-select grading only needs
case-insensitive multiset equality. Replacing the two map-and-sort chains with $O(N)$ lowercase frequency
counts keeps `ExamAnswerUtils` free of `android.*` imports and advances roadmap 9 (Kotlin Multiplatform)
by keeping grading logic portable.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` (`ExamAnswerUtils.checkMultipleSelectAnswer`, lines 77-87)
- touch: `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt` (`ExamAnswerUtilsTest`)
- leave alone: `ExamAnswerUtils.getChoiceTextById`, `ExamAnswerUtils.checkSelectAnswer`, `ExamAnswerUtils.checkTextAnswer`, and `ExamQuestion` (touched by open PR #18045)
steps:
1. Open `ExamAnswerUtils.kt` and locate `checkMultipleSelectAnswer(listAns: Map<String, String>?, correctChoices: List<String>?): Boolean` at lines 77-87.
2. Keep the null guard (`if (listAns == null || correctChoices == null) return false`), the size guard (`if (listAns.size != correctChoices.size) return false`), and an empty short-circuit (`if (correctChoices.isEmpty()) return true`).
3. Replace `listAns.values.map { it.lowercase(locale) }.sorted()` and `correctChoices.map { it.lowercase(locale) }.sorted()` with `groupingBy { it.lowercase(locale) }.eachCount()` on both `listAns.values` and `correctChoices`, returning `selectedCounts == correctCounts`.
4. In `ExamAnswerUtilsTest.kt`, add a unit test verifying `ExamAnswerUtils.checkCorrectAnswer` on `selectMultiple` questions with empty choice lists and larger unordered choice sets with mixed casing and duplicate entries.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.ExamAnswerUtilsTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; multi-select exam and survey questions grade
case-insensitive, unordered, and duplicate-choice selections identically to before.
size budget: ~20 changed lines, 2 files
out of scope: do not modify `ExamQuestion`, `getChoiceTextById`, single-select grading, or free-text answer matching.

---

### 2. parallelize independent course, user, step, and progress reads in TakeCourseViewModel.loadCourse (roadmap 3+7)
context: `TakeCourseViewModel.kt:117-127` (`loadCourse`) sequentially awaits `userRepository.getUserModel()`,
`coursesRepository.getCourseById(courseId)`, `coursesRepository.getCourseSteps(courseId)`, and
`progressRepository.getCourseProgress(listOf(courseId), userModel?.id)` inside `viewModelScope.launch`.
Because `getUserModel()` and `getCourseById(courseId)` have no dependency on each other, and
`getCourseSteps(courseId)` and `getCourseProgress(listOf(courseId), userModel?.id)` are likewise independent
once `course` and `userModel` are resolved, running them serially doubles the DAO wait latency on course open.
Running each pair concurrently with `coroutineScope` and `async` advances roadmap 9 and 10 by keeping all
state orchestration in pure `kotlinx.coroutines` inside `TakeCourseViewModel` with zero `android.*` imports.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (`TakeCourseViewModel.loadCourse`, lines 108-134)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt` (`TakeCourseViewModelTest`)
- leave alone: `TakeCourseViewModel.getBlockingStepIndex`, `TakeCourseViewModel.getRatingPromptDecision`, and `TakeCourseFragment`
steps:
1. Open `TakeCourseViewModel.kt` and add `kotlinx.coroutines.async` and `kotlinx.coroutines.coroutineScope` imports in alphabetical order.
2. In `loadCourse` (lines 117-123), wrap `userRepository.getUserModel()` and `coursesRepository.getCourseById(courseId)` in a `coroutineScope` with two `async` coroutines and await both before checking `if (course == null) { _uiState.value = TakeCourseUiState.NotFound; return@launch }`.
3. Immediately after the `course == null` guard (lines 124-126), wrap `coursesRepository.getCourseSteps(courseId)` and `progressRepository.getCourseProgress(listOf(courseId), userModel?.id)` in a second `coroutineScope` with two `async` coroutines, await both, and pass `steps` and `userModel` into `getBlockingStepIndex(steps, userModel, courseId)` as before.
4. In `TakeCourseViewModelTest.kt`, add a concurrency test using `CompletableDeferred` gates asserting that `getUserModel` and `getCourseById` start concurrently and that `getCourseSteps` and `getCourseProgress` start concurrently after `course` is found.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening a course in `TakeCourseFragment`
transitions through `TakeCourseUiState.Loading` to `TakeCourseUiState.Success` (or `NotFound` when missing)
with identical steps, progress, and blocking step index.
size budget: ~45 changed lines, 2 files
out of scope: do not change `getBlockingStepIndex` ordering (`saveEarnedExamPasses` must still complete before `getBlockingStepIndex` reads the lock), and do not touch `TakeCourseFragment` or repository implementations.

---

### 3. parallelize joined-members and current-user reads in RequestsViewModel.loadJoinedMembers (roadmap 3+7)
context: `RequestsViewModel.kt:67-78` (`loadJoinedMembers`) sequentially awaits
`val members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)` and
`val currentUserId = userRepository.getUserModel()?.id` before publishing `MembersUiState`. In the same
class, `fetchMembers` (`RequestsViewModel.kt:131-149`) already uses `coroutineScope` and `async` to run
`teamsRepository` and `userRepository.getUserModel()` queries concurrently. Running the two independent
reads in `loadJoinedMembers` concurrently removes serial wait latency on opening the team members tab and
after leadership or membership mutations while keeping `RequestsViewModel` free of `android.*` imports
(advancing roadmap 9 and 10).
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt` (`RequestsViewModel.loadJoinedMembers`, lines 67-78)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelTest.kt` (`RequestsViewModelTest`)
- leave alone: `RequestsViewModel.fetchMembers`, `RequestsViewModel.removeMember`, `RequestsViewModel.leaveTeam`, `MembersFragment`, and `TeamsRepositoryImpl` (owned by open PRs)
steps:
1. Open `RequestsViewModel.kt` and inspect `loadJoinedMembers(teamId: String)` at lines 67-78 (`async` and `coroutineScope` are already imported at lines 7-8).
2. Inside `viewModelScope.launch` in `loadJoinedMembers`, wrap the two repository calls in `coroutineScope` with `val membersDeferred = async { teamsRepository.getJoinedMembersWithVisitInfo(teamId) }` and `val userDeferred = async { userRepository.getUserModel() }`.
3. Await `val members = membersDeferred.await()` and `val currentUserId = userDeferred.await()?.id`, then construct and assign `_membersState.value = MembersUiState(...)` with the existing `isLeader = members.any { it.user.id == currentUserId && it.isLeader }` calculation.
4. In `RequestsViewModelTest.kt`, add a test verifying that `getJoinedMembersWithVisitInfo` and `getUserModel` both start before either finishes during `loadJoinedMembers`, while keeping all existing `coVerifySequence` assertions in `removeMember` green.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.teams.members.RequestsViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the team Joined Members screen displays the
member list, current user leadership controls, and visit info identically.
size budget: ~35 changed lines, 2 files
out of scope: do not alter `removeMember`, `leaveTeam`, `makeLeader`, `fetchMembers`, or any DAO/repository classes.

---

### 4. parallelize team, enterprise, and community summary queries in ChatViewModel.loadShareTargets (roadmap 3+7)
context: `ChatViewModel.kt:193-217` (`loadShareTargets`) sequentially awaits
`teamsRepository.getTeamSummaries(userId)`, `teamsRepository.getShareableEnterpriseSummaries(userId)`, and
`teamsRepository.getTeamSummaryById(id)` when building `ChatShareTargets` during chat history screen
initialization (`loadChatHistoryScreenData` at line 147). All three `TeamsShareRepository` queries are
read-only and independent of one another, and `ChatViewModel` already imports `async` and `coroutineScope`
(lines 8 and 10) for news and chat history reads. Running the three share-target reads concurrently in
`coroutineScope` reduces cold-load latency on the AI chat history screen and advances roadmap 9 and 10 by
keeping `ChatViewModel` platform-free.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`ChatViewModel.loadShareTargets`, lines 193-217)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` (`ChatViewModelTest`)
- leave alone: `ChatViewModel.loadChatHistoryScreenData`, `ChatConversationPaginator`, and `TeamsRepositoryImpl` (owned by open PRs)
steps:
1. Open `ChatViewModel.kt` and locate `private suspend fun loadShareTargets(parentCode: String?, communityName: String?, userId: String?): ChatShareTargets` at lines 193-217.
2. Compute `communityId` upfront as `if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) "$communityName@$parentCode" else null`.
3. Wrap the body of `loadShareTargets` in `coroutineScope` and launch `val teamsDeferred = async { teamsRepository.getTeamSummaries(userId) }`, `val enterprisesDeferred = async { teamsRepository.getShareableEnterpriseSummaries(userId) }`, and `val communityDeferred = async { communityId?.let { id -> teamsRepository.getTeamSummaryById(id) ?: TeamSummary(...) } }`.
4. Return `ChatShareTargets(communityDeferred.await(), teamsDeferred.await(), enterprisesDeferred.await())`.
5. In `ChatViewModelTest.kt`, add a test verifying that `getTeamSummaries`, `getShareableEnterpriseSummaries`, and `getTeamSummaryById` start concurrently before any of their `CompletableDeferred` gates complete.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening the chat history share sheet lists
the same community, team, and enterprise targets.
size budget: ~45 changed lines, 2 files
out of scope: do not change `cachedShareTargets` invalidation in `loadChatHistoryScreenData` or modify `TeamsShareRepository`.

---

### 5. pre-normalize facet filter sets once per pass in ResourcesListFilter (roadmap 3+7)
context: `ResourcesListFilter.kt:59-129` (`matching` and `matchesFacets`) precomputes `searchTagIds` and
`precomputedMediums` once per filter call, but leaves `criteria.subjects`, `criteria.levels`, and
`criteria.languages` un-normalized. Inside `matchesFacets` (lines 98-120), every candidate
`ResourceListModel` runs nested `criteria.subjects.all { critSub -> libSubjects.any { libSub -> libSub.equals(critSub, ignoreCase = true) } }`,
`criteria.levels.all { ... }`, and `criteria.languages.any { it.equals(library.language, ignoreCase = true) }`.
Pre-normalizing `languages` into a lowercase `HashSet<String>` (turning language matching into an $O(1)$
set lookup) and deduplicating/lowercasing `subjects` and `levels` once in `matching` cuts per-item string
comparisons across large resource catalogs while keeping `ResourcesListFilter` 100% platform-free for
roadmap 9 and 10.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt` (`ResourcesListFilter.matching` and `ResourcesListFilter.matchesFacets`, lines 59-129)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt` (`ResourcesListFilterTest`)
- leave alone: `ResourcesListFilter.matchesMedium`, `ResourcesListFilter.filterIfChanged`, and `ResourcesMediaType`
steps:
1. Open `ResourcesListFilter.kt` and import `java.util.Locale`.
2. Define a private `FacetCriteria` holder (next to `MediumCriteria` at lines 30-33) containing `val subjects: Set<String>`, `val levels: Set<String>`, `val languages: Set<String>`, and `val mediums: List<MediumCriteria>`.
3. In `matching` (lines 59-89), precompute `subjects = criteria.subjects.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }`, `levels = criteria.levels.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }`, and `languages = criteria.languages.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }` when non-empty (or `emptySet()` when empty), and pass `FacetCriteria` into `matchesFacets`.
4. In `matchesFacets`, check `facets.languages` via `val lang = library.language ?: return false; if (lang.lowercase(Locale.ROOT) !in facets.languages) return false`, and check `facets.subjects` and `facets.levels` against the deduplicated lowercase sets.
5. In `ResourcesListFilterTest.kt`, add a test verifying case-insensitive filtering across mixed-case `levels`, `languages`, and duplicate-case `subjects` entries for both `apply` and `countMatching`.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; filtering resources by subject, level,
language, medium, tag, and download status returns identical results and badge counts.
size budget: ~45 changed lines, 2 files
out of scope: do not alter `Signature` caching in `toSignature` or media-type classification in `ResourcesMediaType`.

---

### 6. avoid full-list remap and recount on single-item toggle in StorageCategoryViewModel (roadmap 3+7)
context: `StorageCategoryViewModel.kt:61-71` (`toggleItemChecked`) maps the entire `state.items` list
(`state.items.map { item -> ... }`), checks `item.resourceId == resourceId` on every element, and recounts
`checkedCount` from zero across the whole list on every single-item checkbox click. When `resourceId` is
not present in `state.items`, it still allocates a new list and emits a copy of `StorageCategoryUiState`.
Finding the target index with `indexOfFirst`, updating only that element, and adjusting `state.checkedCount`
by `+1` or `-1` reduces per-click allocations to a shallow list copy and keeps `StorageCategoryViewModel`
portable with zero `android.*` imports (advancing roadmap 9 and 10).
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt` (`StorageCategoryViewModel.toggleItemChecked`, lines 61-71)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModelTest.kt` (`StorageCategoryViewModelTest`)
- leave alone: `StorageCategoryViewModel.toggleAllChecked`, `StorageCategoryViewModel.deleteSelected`, and `StorageCategoryDetailFragment`
steps:
1. Open `StorageCategoryViewModel.kt` and locate `toggleItemChecked(resourceId: String)` at lines 61-71.
2. Inside `_uiState.update { state -> ... }`, find `val index = state.items.indexOfFirst { it.resourceId == resourceId }` and return `state` unchanged if `index < 0`.
3. Read `val current = state.items[index]`, compute `val toggled = current.copy(isChecked = !current.isChecked)`, and create `val updatedItems = state.items.toMutableList().apply { this[index] = toggled }`.
4. Compute `val updatedCheckedCount = if (toggled.isChecked) state.checkedCount + 1 else (state.checkedCount - 1).coerceAtLeast(0)` and return `state.copy(items = updatedItems, checkedCount = updatedCheckedCount)`.
5. In `StorageCategoryViewModelTest.kt`, add a test verifying that calling `toggleItemChecked` with an unknown `resourceId` leaves `uiState.value` referentially unchanged and preserves `checkedCount`.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; toggling individual resource checkboxes in
the storage category detail sheet updates row checkmarks, `checkedCount`, and the Select All header state
accurately.
size budget: ~25 changed lines, 2 files
out of scope: do not modify `StorageCategoryDetailFragment`, `OfflineResourceItem`, or `ResourcesRepository`.

---

### 7. reuse precomputed group unread state and normalize group keys in NotificationsViewModel.toggleGroupExpansion (roadmap 3+7+8)
context: `NotificationsViewModel.kt:99-105` (`toggleGroupExpansion`) falls back to
`isGroupDefaultExpanded(type, notifications.value)` (`NotificationsViewModel.kt:206-208`), which scans the
entire `notifications.value` list (`notifications.any { it.type == type && !it.isRead }`) even though
`groupedNotifications` (`NotificationsViewModel.kt:61-63`) already groups items and precomputes
`unreadCount` per group. Worse, `buildNotificationGroups` (`NotificationsViewModel.kt:212-215`) normalizes
unknown or mixed-case types to `val t = notif.type.lowercase(Locale.ROOT); if (t in NotificationsRepository.KNOWN_TYPES) t else "notification"`,
whereas `isGroupDefaultExpanded` compares raw `it.type == type`, so toggling the default-expanded
`"notification"` fallback group fails to recognize that it is currently expanded. Reusing
`groupedNotifications.value` first and sharing the group-key normalization helper eliminates the linear
rescan and fixes fallback group toggling while advancing roadmap 10.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (`NotificationsViewModel.toggleGroupExpansion`, `NotificationsViewModel.isGroupDefaultExpanded`, and `NotificationsViewModel.buildNotificationGroups`, lines 99-115 and 206-227)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` (`NotificationsViewModelTest`)
- leave alone: `NotificationsViewModel.markSelectedAsRead`, `NotificationsViewModel.formatNotification`, `NotificationsFragment`, and `NotificationsRepository`
steps:
1. Open `NotificationsViewModel.kt` and extract a private helper `private fun groupTypeOf(rawType: String): String { val t = rawType.lowercase(Locale.ROOT); return if (t in NotificationsRepository.KNOWN_TYPES) t else "notification" }`.
2. Update `buildNotificationGroups` (lines 212-215) to group notifications by `groupTypeOf(notif.type)`.
3. Update `isGroupDefaultExpanded(type: String, notifications: List<Notification>): Boolean` (lines 206-208) to first check `groupedNotifications.value.firstOrNull { it.type == type }?.let { return it.unreadCount > 0 }` and otherwise fall back to `notifications.any { groupTypeOf(it.type) == type && !it.isRead }`.
4. In `NotificationsViewModelTest.kt`, add a test verifying that an unread notification with an unknown type (such as `type = "unknown_type"`) renders expanded under the `"notification"` header by default and collapses on the first `viewModel.toggleGroupExpansion("notification")` call.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; tapping any notification group header
(including the `"notification"` fallback group) expands and collapses the section on the first tap.
size budget: ~30 changed lines, 2 files
out of scope: do not change `NotificationsFragment`, `NotificationsRepository`, or notification text formatting methods.

---

### 8. eliminate per-word string concatenation and duplicate text measurement in SubmissionsRepositoryExporter (roadmap 1+7)
context: `SubmissionsRepositoryExporter.kt:247-279` (`drawMultilineText`) splits every question and answer
body on `" "` and, for every word after the first on a line, allocates a `" $word"` string
(`val addition = " $word"`) and calls `paint.measureText(addition)`; when the line wraps (lines 260-266),
it calls `paint.measureText(word)` a second time for that same word. In the same file, `getExamId`
(`SubmissionsRepositoryExporter.kt:307-313`) checks `parentId?.contains("@") == true` and then allocates
a `List<String>` via `parentId.split("@")[0]` on every submission instead of using `substringBefore('@')`.
Measuring `" "` once per `drawMultilineText` invocation and measuring each word once removes per-word
string allocations and duplicate JNI `Paint.measureText` calls during single- and multi-submission PDF exports.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` (`SubmissionsRepositoryExporter.drawMultilineText`, lines 247-279, and `SubmissionsRepositoryExporter.getExamId`, lines 307-313)
- touch: `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt` (`SubmissionsRepositoryExporterTest`)
- leave alone: `SubmissionsRepositoryExporter.generateSubmissionPdf`, `SubmissionsRepositoryExporter.generateMultipleSubmissionsPdf`, and `SubmissionsRepositoryImpl` (owned by open PRs)
steps:
1. Open `SubmissionsRepositoryExporter.kt` and locate `drawMultilineText` at lines 247-279 and `getExamId` at lines 307-313.
2. In `drawMultilineText`, measure `val spaceWidth = paint.measureText(" ")` once before iterating `words`, and inside `words.forEach { word -> ... }` compute `val wordWidth = paint.measureText(word)` once per word.
3. When `line.isEmpty()`, append `word` and set `lineWidth = wordWidth`; otherwise compare `lineWidth + spaceWidth + wordWidth > maxWidth`: on wrap, draw `line.toString()`, advance `currentY += LINE_HEIGHT`, clear `line`, append `word`, and set `lineWidth = wordWidth`; when it fits, call `line.append(' ').append(word)` and `lineWidth += spaceWidth + wordWidth`.
4. Replace the body of `getExamId(parentId: String?): String?` (lines 307-313) with `return parentId?.substringBefore('@')` and mark `getExamId` as `internal` so it can be unit-tested directly alongside `getQuestionsByExam`.
5. In `SubmissionsRepositoryExporterTest.kt`, add unit tests for `getExamId` (null, plain ID, and `examId@courseId` inputs) and run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SubmissionsRepositoryExporterTest"`.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; exporting single or multiple exam/survey
submissions to PDF wraps multiline questions and answers identically without extra string or list allocations.
size budget: ~30 changed lines, 2 files
out of scope: do not alter PDF page dimensions, font sizes, or `SubmissionsRepositoryImpl`.

---

### 9. stream CSV speech formatting into a single StringBuilder in TTSManager.formatCsvForSpeech (roadmap 7+8)
context: `TTSManager.kt:119-129` (`formatCsvForSpeech`) formats parsed CSV rows for text-to-speech by
calling `rows.drop(1).mapIndexed { index, row -> ... row.mapIndexed { col, value -> ... }.joinToString(", ") ... }.joinToString(". ")`.
For a CSV with $R$ data rows and $C$ columns, this allocates a sliced `List<Array<String>>`, an outer
`List<String>` of size $R - 1$, $R - 1$ inner `List<String>` instances of size $C$, and $R \times C$
intermediate cell strings before joining. Streaming directly into a single `buildString` block eliminates
all intermediate collection allocations and advances roadmap 9 by keeping `TTSManager`'s pure-Kotlin text
formatting helpers self-contained.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt` (`TTSManager.Companion.formatCsvForSpeech`, lines 119-129)
- touch: `app/src/test/java/org/ole/planet/myplanet/utils/TTSManagerTest.kt` (`TTSManagerTest`)
- leave alone: `TTSManager.Companion.stripMarkdown`, `TTSManager.ensureTts`, and `ResourceViewerFragment`
steps:
1. Open `TTSManager.kt` and locate `fun formatCsvForSpeech(rows: List<Array<String>>): String` at lines 119-129.
2. Replace `if (rows.isEmpty()) return ""` with `if (rows.size <= 1) return ""` so header-only CSVs return `""` immediately without allocating a builder.
3. Replace `rows.drop(1).mapIndexed { ... }.joinToString(". ")` with `buildString` iterating `for (rowIndex in 1 until rows.size)`: append `". "` when `rowIndex > 1`, append `"Row $rowIndex. "`, and iterate `for (col in row.indices)` appending `", "` when `col > 0` followed by `header.getOrElse(col) { "column ${col + 1}" }`, `": "`, and `row[col]`.
4. In `TTSManagerTest.kt`, add a test verifying `formatCsvForSpeech` on a single-row (header-only) CSV and a multi-row CSV with ragged column counts.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.TTSManagerTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; tapping Read Aloud on a CSV in
`ResourceViewerFragment` speaks the exact same `"Row 1. Header: Value, ..."` text.
size budget: ~25 changed lines, 2 files
out of scope: do not modify `stripMarkdown`, `formatCsvTextForSpeech` CSV parser settings, or `TextToSpeech` lifecycle code.

---

### 10. short-circuit label set allocations in VoicesLabelManager.updateAddLabelVisibility and cache label values (roadmap 7+8)
context: `VoicesLabelManager.kt:108-122` (`updateAddLabelVisibility`) is called from `showChips` on every
RecyclerView row bind whose label state changed, and unconditionally allocates two `Set<String>` instances:
`val usedLabels = voice?.labels?.toSet() ?: emptySet()` and `val labels = Constants.LABELS.values.toSet()`,
then checks `usedLabels.containsAll(labels)`. Because `Constants.LABELS` is a static map (`Constants` lines
32-36), converting its `.values` to a new `Set` on every row bind is redundant, and converting
`voice.labels` to a `Set` is unnecessary whenever `labels.size < Constants.LABELS.size` (which is true for
the vast majority of Voices posts that have 0, 1, or 2 labels). Caching the constant label-value set in
`VoicesLabelManager.Companion` and short-circuiting on list size removes both allocations from the common
RecyclerView bind path.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt` (`VoicesLabelManager.showChips`, `VoicesLabelManager.updateAddLabelVisibility`, and `VoicesLabelManager.Companion`, lines 64-92 and 108-132)
- touch: `app/src/test/java/org/ole/planet/myplanet/services/VoicesLabelManagerTest.kt` (`VoicesLabelManagerTest`)
- leave alone: `VoicesLabelManager.setupAddLabelMenu`, `VoicesLabelManager.launchLabelWrite`, and `Constants`
steps:
1. Open `VoicesLabelManager.kt` and add `private val allLabelValues: Set<String> = Constants.LABELS.values.toSet()` inside `companion object` (lines 128-132).
2. Change `updateAddLabelVisibility(binding: RowNewsBinding, voice: News?, canManageLabels: Boolean)` (lines 108-122) to accept `labels: List<String>` directly instead of `voice: News?`, and update its call site in `showChips` (line 91) to pass `labels` (already resolved as `val labels = voice.labels ?: emptyList()` at line 65).
3. In `updateAddLabelVisibility`, replace `val usedLabels = ...` and `val labels = Constants.LABELS.values.toSet()` with `val allUsed = labels.size >= allLabelValues.size && labels.toSet().containsAll(allLabelValues)` and set `binding.btnAddLabel.visibility = if (allUsed) View.GONE else View.VISIBLE`.
4. In `VoicesLabelManagerTest.kt`, add a test verifying `btnAddLabel.visibility` when `voice.labels` has duplicate entries equal in count to `Constants.LABELS.size` (such as `listOf("offer", "offer", "offer")` staying `View.VISIBLE`) versus all distinct labels (`View.GONE`).
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.VoicesLabelManagerTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; Voices feed items show or hide the Add Label
button identically based on whether all available labels are attached.
size budget: ~25 changed lines, 2 files
out of scope: do not modify `Constants`, `setupAddLabelMenu`, or chip view creation in `showChips`.

Signed: antigravity claude opus 4.6 high

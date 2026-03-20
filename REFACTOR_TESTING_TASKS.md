# Testing & Architecture Cleanup — Round Tasks

10 granular, low-conflict PRs focused on testing and light architecture hygiene.

---

### 1. Add unit tests for EventsRepositoryImpl

EventsRepositoryImpl is a small 74-line repository with five methods, all using RealmRepository helpers (`queryList`, `findByField`, `update`, `executeTransaction`). It has zero test coverage and its compact size makes it an ideal first test target with no risk of merge conflicts.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=74 path=app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L9-L74"}

:::task-stub{title="Add unit tests for EventsRepositoryImpl"}
1. Create `app/src/test/java/org/ole/planet/myplanet/repository/EventsRepositoryImplTest.kt`
2. Follow the existing test pattern in `ConfigurationsRepositoryImplTest.kt` (mockk + runTest)
3. Test `getMeetupsForTeam` returns filtered results by teamId
4. Test `getMeetupById` returns null for blank id
5. Test `getJoinedMembers` returns empty list for blank meetupId and empty memberIds
6. Test `toggleAttendance` toggles userId between empty and provided value
7. Test `createMeetup` returns true on success and false on exception
:::

---

### 2. Add unit tests for LifeRepositoryImpl

LifeRepositoryImpl is 57 lines with four well-scoped methods. It exercises `queryList`, `executeTransaction`, and sorting — all easily mockable. No existing test file and no overlap with other PRs.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L8-L57"}

:::task-stub{title="Add unit tests for LifeRepositoryImpl"}
1. Create `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`
2. Test `getMyLifeByUserId` returns items sorted by weight
3. Test `updateVisibility` calls executeTransaction and toggles isVisible
4. Test `updateMyLifeListOrder` updates weight based on list index
5. Test `seedMyLifeIfEmpty` inserts items when no existing data and skips when data exists
:::

---

### 3. Add unit tests for PersonalsRepositoryImpl

PersonalsRepositoryImpl is 69 lines with six methods covering save, delete, update, flow query, and sync marking. Each method maps cleanly to a single RealmRepository helper, making mocking straightforward.

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt#L11-L69"}

:::task-stub{title="Add unit tests for PersonalsRepositoryImpl"}
1. Create `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
2. Test `savePersonalResource` constructs RealmMyPersonal with correct fields and calls save
3. Test `getPersonalResources` returns flowOf(emptyList()) for null/blank userId
4. Test `deletePersonalResource` calls delete on both `_id` and `id` fields
5. Test `updatePersonalAfterSync` sets isUploaded=true and updates _id and _rev
6. Test `getPendingPersonalUploads` filters by userId and isUploaded=false
:::

---

### 4. Add unit tests for TagsRepositoryImpl

TagsRepositoryImpl is 106 lines with pure data-transformation logic in `buildChildMap` and `getLinkedTags` that can be tested with minimal mocking. The mapping and filtering logic is a good candidate for catching regressions.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=106 path=app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L7-L106"}

:::task-stub{title="Add unit tests for TagsRepositoryImpl"}
1. Create `app/src/test/java/org/ole/planet/myplanet/repository/TagsRepositoryImplTest.kt`
2. Test `getTags` filters by dbType, non-empty name, and isAttached=false
3. Test `buildChildMap` correctly groups tags by their attachedTo parents
4. Test `getTagsForResource` resolves linked tags through tagId lookup
5. Test `getTagsForResources` returns correct per-resource tag map
6. Test edge cases: empty linkIds returns emptyMap, empty tagIds returns emptyList
:::

---

### 5. Add unit tests for ChatRepositoryImpl

ChatRepositoryImpl is 53 lines with four methods. `saveNewChat` and `continueConversation` use `withRealmAsync { realm.executeTransaction }` instead of the inherited `executeTransaction()` helper — the test should verify behavior and the code could be simplified in a follow-up.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=53 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L14-L53"}

:::task-stub{title="Add unit tests for ChatRepositoryImpl"}
1. Create `app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt`
2. Test `getChatHistoryForUser` returns empty list for null/empty userName
3. Test `getChatHistoryForUser` queries with correct user and DESCENDING sort
4. Test `getLatestRev` finds the highest _rev by numeric prefix
5. Test `saveNewChat` delegates to RealmChatHistory.insert inside a transaction
6. Test `continueConversation` delegates to addConversationToChatHistory
:::

---

### 6. Add unit tests for FeedbackListViewModel

FeedbackListViewModel is 40 lines with no test file. It uses `viewModelScope.launch` without an injected `DispatcherProvider`, making it untestable with `StandardTestDispatcher`. The test should expose this gap and the fix is to inject `DispatcherProvider`.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt#L17-L40"}

:::task-stub{title="Add unit tests for FeedbackListViewModel"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModelTest.kt`
2. Inject `DispatcherProvider` into FeedbackListViewModel constructor for testability
3. Use `TestDispatcherProvider` and `MainDispatcherRule` from existing test infrastructure
4. Test that `feedbackList` StateFlow emits data from `feedbackRepository.getFeedback`
5. Test that `refreshFeedback` re-triggers the flow collection
6. Verify initial state is an empty list
:::

---

### 7. Add unit tests for EnterprisesViewModel

EnterprisesViewModel is 35 lines — a thin delegation layer over TeamsRepository. It has no test file. Testing it verifies the delegation contract and catches accidental breakage from repository refactors.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt#L12-L35"}

:::task-stub{title="Add unit tests for EnterprisesViewModel"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModelTest.kt`
2. Mock TeamsRepository with mockk
3. Test `addReport` delegates to `teamsRepository.addReport`
4. Test `updateReport` delegates with correct reportId and doc
5. Test `archiveReport` delegates with correct reportId
6. Test `getReportsFlow` returns the flow from teamsRepository
7. Test `exportReportsAsCsv` passes reports and teamName through
:::

---

### 8. Fix ChatAdapter DiffUtil to use field-level comparison

ChatAdapter uses `{ old, new -> old == new }` for both `areItemsTheSame` and `areContentsTheSame`. Since `ChatMessage` is a data class this works but defeats the purpose of DiffUtil — items are never considered "the same item with changed content", so partial updates never fire. Comparing by identity field improves performance on large chat lists.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=28 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt#L24-L28"}
:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/model/ChatMessage.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/ChatMessage.kt#L3-L15"}

:::task-stub{title="Fix ChatAdapter DiffUtil to use field-level comparison"}
1. Change `areItemsTheSame` to compare by message+viewType (the identity of a ChatMessage)
2. Keep `areContentsTheSame` as data-class equals (covers source field changes)
3. Verify the existing `DiffUtilsTest.kt` patterns are followed
4. Manually test chat screen to confirm no regressions in message rendering or animation
:::

---

### 9. Refactor ChatRepositoryImpl to use inherited executeTransaction

`saveNewChat` and `continueConversation` manually call `realm.executeTransaction` inside `withRealmAsync` instead of using the inherited `executeTransaction()` helper from RealmRepository. This inconsistency makes testing harder and bypasses centralized transaction handling.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=52 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L38-L52"}
:codex-file-citation[codex-file-citation]{line_range_start=199 line_range_end=201 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L199-L201"}

:::task-stub{title="Refactor ChatRepositoryImpl to use inherited executeTransaction"}
1. Replace `withRealmAsync { realm -> realm.executeTransaction { ... } }` with `executeTransaction { realm -> ... }` in `saveNewChat`
2. Apply the same change to `continueConversation`
3. Remove unused imports (`Realm`, `TextUtils`, `JsonArray`, `Case`, `Sort`, `RealmNews`) if any become dead
4. Verify chat history save and continue-conversation flows work end-to-end
:::

---

### 10. Add unit tests for SubmissionViewModel

SubmissionViewModel is 104 lines with complex Flow logic (flatMapLatest, combine, shareIn, stateIn) and already injects `DispatcherProvider`. It has no test file despite being one of the more intricate ViewModels. Testing the filtering and grouping logic guards against regressions in the submissions feature.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=104 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L28-L104"}

:::task-stub{title="Add unit tests for SubmissionViewModel"}
1. Create `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModelTest.kt`
2. Use `TestDispatcherProvider` and `MainDispatcherRule` from existing test infra
3. Mock `SubmissionsRepository` and `UserRepository` with mockk
4. Test that `submissions` StateFlow emits filtered list when `setFilter("survey", "")` is called
5. Test that `submissionCounts` groups by parentId correctly
6. Test that search query filters submissions by exam name match
7. Test initial state emits empty list
:::

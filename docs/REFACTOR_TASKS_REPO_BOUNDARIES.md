# myPlanet refactor work orders — repository boundaries round

2026-10-01 · base commit `286e66e0e1daf285c34d8456deb0028016bf858a` (master) · open PRs checked: 17812, 17811, 17776, 17694, 17680, 17435, 17254, 17187, 16623, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

All tasks avoid every file touched by those PRs. Each is independently mergeable; no file appears in more than one task.

### 1. remove the course-detail provider passthroughs (roadmap 3+8)
context: `ui/courses/CourseDetailProvider.kt:9` is a 14-line class whose entire body is `operator fun invoke(courseId) = coursesRepository.getCourseDetailModel(courseId)`, and `ui/courses/RatingSummaryProvider.kt:9` does the same for `ratingsRepository.getRatingSummary("course", ...)`. Both are injected into `CourseDetailViewModel` at `ui/courses/CourseDetailViewModel.kt:38-39`, adding two files and two Hilt bindings for zero behavior — pure indirection between the ViewModel and repositories it could call directly.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailProvider.kt` — delete
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/RatingSummaryProvider.kt` — delete
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt` — inject `CoursesRepository` and `RatingsRepository` and call them where the providers were invoked
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModelTest.kt` — replace provider construction (lines ~51-52, ~60-62) with repository mocks
- leave alone: `CoursesRepository`/`RatingsRepository` interfaces and impls (open PRs own them), every other `ui/courses/` file
steps:
1. In `CourseDetailViewModel`, replace the two provider constructor params with `CoursesRepository` and `RatingsRepository`.
2. Replace each provider call site (`courseDetailProvider(...)`, `ratingSummaryProvider(...)`) with the equivalent repository call from the provider bodies.
3. Delete both provider files; remove now-unused imports in the ViewModel.
4. Update `CourseDetailViewModelTest` to mock the two repositories instead of the two providers, preserving each test's stubbed return values.
acceptance: `./gradlew testDefaultDebugUnitTest` green; CourseDetailFragment still renders course details and the rating summary row exactly as before (no visual change)
size budget: ~40 changed lines net-negative (two 13-14 line files deleted), 4 files
out of scope: no repository signature changes; no DI module changes (interfaces are already bound)

---

### 2. extract chat-conversation pagination into a platform-free pager (roadmap 3+9)
context: `ui/chat/ChatViewModel.kt:59-65` holds pagination state (`PAGE_SIZE`, `@VisibleForTesting internal var allConversations`, `loadedCount`) and `ChatViewModel.kt:197-243` implements `parseAndBuildInitialPage` — including a Gson `fromJson` of `Array<Conversation>` at line 201 — plus `processChatHistory`, `buildInitialPage`, `buildMessagesSlice`, `loadMoreConversations`, `clearPaginationState`. This is data-layer JSON parsing plus pure list math inside a ViewModel that owns nine StateFlows; it blocks the pager logic from being reused or moved to a platform-free module later.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` — remove lines 59-65 and 197-243; delegate to the new pager
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPager.kt` — NEW: constructor-injected `DispatcherProvider`-free pure class (or plain class; the `withContext(dispatcherProvider.io)` at line 198 may stay in the VM call site or move with the pager via an injected `DispatcherProvider`) exposing `parseConversations(json)`, `setConversations(list)`, `initialPage()`, `loadMore()`, `reset()`, `hasMore()`
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` — keep existing pagination behavior tests green; add direct `ChatConversationPager` unit tests only if trivially movable
- leave alone: `ChatHistoryFragment`, `ChatDetailFragment`, `ChatRepository`/impl (open PRs own them), `model/Conversation`
steps:
1. Create `ChatConversationPager` holding `allConversations`, `loadedCount`, `PAGE_SIZE`; move `buildInitialPage`/`buildMessagesSlice`/`loadMore`/`reset` bodies verbatim.
2. Move the Gson `Array<Conversation>` parse (lines 198-205) into `parseConversations` returning `List<Conversation>`, preserving the catch-and-return-empty behavior.
3. In `ChatViewModel`, replace the moved members with a private `pager` instance; keep public method names/signatures (`parseAndBuildInitialPage`, `processChatHistory`, `loadMoreConversations`, `clearPaginationState`) as thin delegations so fragments and tests are unchanged.
4. Remove the `@VisibleForTesting` fields and the `GsonUtils`/`Conversation` imports if unused; keep `PAGE_SIZE` accessible if the test references it (or re-point the test).
acceptance: `./gradlew testDefaultDebugUnitTest` green; chat detail screen still pages in 20-message chunks with the LOAD_MORE marker row appearing/disappearing at the same boundaries
size budget: ~120 lines moved, ~10 lines of delegation; 3 files
out of scope: no changes to what is paginated or page size; no fragment changes; the pager keeps Gson (GsonUtils) — do not swap serializers

---

### 3. route achievement calls through `UserAchievementsRepository` (roadmap 3+4)
context: `ui/user/AchievementViewModel.kt:41` injects the fat `UserRepository` (~70 methods) but uses `achievementUpdates` (line 44), `initializeAchievement` (66), `updateAchievement` (71-83) and `getAchievementData` (96) — all declared on the narrow `UserAchievementsRepository` sub-interface (`repository/UserAchievementsRepository.kt`, already bound to `UserRepositoryImpl` at `di/RepositoryModule.kt:212`). Only `getUserModel` (62, 100) and `updateProfileFields` (84) need the full interface. Injecting the narrow contract makes the screen's real dependency surface explicit and keeps the interface-segregation precedent consistent.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt` — add `UserAchievementsRepository` constructor param; route the four achievement calls through it
- `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementViewModelTest.kt` — add a `UserAchievementsRepository` mock and move achievement stubs onto it
- leave alone: `UserRepository.kt`, `UserRepositoryImpl`, `UserAchievementsRepository.kt`, `di/RepositoryModule.kt` (binding already exists), `AchievementFragment`/`EditAchievementFragment`
steps:
1. Add `private val userAchievementsRepository: UserAchievementsRepository` to the constructor, keeping `userRepository` for `getUserModel`/`updateProfileFields`.
2. Point lines 44, 66, 71-83 and 96 at `userAchievementsRepository`.
3. Update the test constructor call and move `achievementUpdates`/`initializeAchievement`/`updateAchievement`/`getAchievementData` stubs to the new mock; keep `getUserModel`/`updateProfileFields` stubs on `userRepository`.
4. Fix imports (`org.ole.planet.myplanet.repository.UserAchievementsRepository`); remove `UserRepository` import only if fully unused.
acceptance: `./gradlew testDefaultDebugUnitTest` green; achievements screen still loads the user achievement, saves edits, and emits update events identically
size budget: ~20 changed lines, 2 files
out of scope: do not re-route `getUserModel`/`updateProfileFields` or change any interface contents; no DI edits

---

### 4. move respondent JSON parsing into `SurveysPublicMapper` (roadmap 1+3)
context: `ui/surveys/PublicSurveyViewModel.kt:81-87` parses `submission.user` with `JsonParser.parseString(it).asJsonObject` inside a try/catch, then calls `publicMapper.sanitizeRespondent` — raw JSON decoding plus null/blank/`"{}"` guards living in the ViewModel while the mapper it already injects (`SurveysPublicMapper.kt:28` `sanitizeRespondent`) owns the adjacent logic. The VM also imports `com.google.gson.JsonParser` (line 5) solely for this.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/SurveysPublicMapper.kt` — add `fun parseRespondent(userJson: String?): JsonObject?` encapsulating the blank/`"{}"` guard, parse, `sanitizeRespondent`, and catch-to-null
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt` — replace lines 81-87 with `publicMapper.parseRespondent(submission.user)`; drop the `JsonParser` import
- `app/src/test/java/org/ole/planet/myplanet/repository/SurveysPublicMapperTest.kt` — add cases: valid user JSON (sanitized), blank, `"{}"`, malformed JSON → null
- `app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt` — adjust stubs from `sanitizeRespondent` to `parseRespondent` where the VM path is exercised
- leave alone: `SubmissionsRepository`/`SurveysRepository` interfaces+impls (open PRs), `SurveyFragment`, `model/Submission`
steps:
1. In `SurveysPublicMapper`, add `parseRespondent(userJson: String?): JsonObject?` implementing exactly the current VM semantics (null/blank/`"{}"` → null; parse → `asJsonObject` → `sanitizeRespondent` → return; any exception → null).
2. Replace the VM's inline block with the single mapper call and remove the `JsonParser` import.
3. Add mapper unit tests for the four input shapes.
4. Update `PublicSurveyViewModelTest` stubs to `parseRespondent`; keep the `UploadFinished`/`NavigateOnward` assertions unchanged.
acceptance: `./gradlew testDefaultDebugUnitTest` green; public-survey submission still uploads with a sanitized respondent object and malformed respondent payloads still skip the field instead of crashing
size budget: ~35 changed lines, 4 files
out of scope: do not touch `buildPublicAnswers` semantics or the upload request shape; no repository-interface changes

---

### 5. give the course-progress grid a typed row model (roadmap 8+3)
context: `ui/courses/ProgressGridAdapter.kt` is a `ListAdapter<JsonObject, …>` whose `onBindViewHolder` pulls `item["percentage"].asString`, `item["completed"].asBoolean` and `item["stepId"]` from raw Gson objects, and `ui/courses/CourseProgressActivity.kt:52` feeds it `adapter.submitList(data.steps.map { it.asJsonObject })` where `model/CourseProgressData.kt` declares `val steps: JsonArray`. Untyped JSON crosses the model→activity→adapter boundary, so a malformed step crashes at bind time instead of failing visibly at parse.
files:
- `app/src/main/java/org/ole/planet/myplanet/model/CourseProgressData.kt` — add `data class StepProgressCell(stepId, percentage, completed)` (field names/types matching the current JSON reads) and a `steps.toStepCells()`-style mapping or a `List<StepProgressCell>` factory — do not remove the existing `steps: JsonArray` field if other code reads it
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt` — change adapter/diff/item types to `StepProgressCell`
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressActivity.kt` — submit the typed list
- leave alone: `ProgressViewModel`, `CourseProgressDao`, `CourseDetailFragment`, `TakeCourseFragment` (open PRs own several neighbors)
steps:
1. Define `StepProgressCell` in `CourseProgressData.kt` with the same three values the adapter reads.
2. Add a mapping (top-level or companion) from `JsonArray`/`List<JsonObject>` to `List<StepProgressCell>` using the same `getString`/`asBoolean` extraction the adapter uses today.
3. Switch `ProgressGridAdapter` to `ListAdapter<StepProgressCell, …>`; replace `getString(...)` calls in `onBindViewHolder` with field access; update its DiffUtil callbacks.
4. In `CourseProgressActivity`, map `data.steps` through the new factory before `submitList`.
acceptance: `./gradlew testDefaultDebugUnitTest` green; the progress grid still renders each step's percentage text and completed-state styling identically for all rows
size budget: ~50 changed lines, 3 files
out of scope: no DAO or ViewModel changes; do not change which keys are read from the step JSON

---

### 6. deduplicate `resolveType` and batch the join-request fallback in `NotificationsRepositoryImpl` (roadmap 1+7)
context: `repository/NotificationsRepositoryImpl.kt:413-439` `resolveType` runs the same substring→type matching twice — the "team" branch (417-425) and the fallback branch (430-437) duplicate the keyword loop. Separately, `getEnrichedNotifications` (line ~209) calls `getJoinRequestDetails(null)` for notifications with no relatedId, which does three sequential single-row lookups (264-272: `getJoinRequestInfo` + `getTeamLabelInfo` + `getUserById`) per notification even though `getJoinRequestDetailsBatch` (299-337) exists for the batch path — N extra DB round-trips on the notifications screen.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — merge the duplicated match blocks into one helper; reuse the batch lookup for the empty-relatedId case
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` — adjust/extend coverage for both paths
- leave alone: `NotificationDao`, `TeamNotificationDao`, `NotificationsViewModel`, `NotificationsFragment` (open PRs own them)
steps:
1. Extract the substring→type matcher shared by the two `resolveType` branches into one private function; have both branches call it, preserving the "team"-first precedence and every existing keyword mapping.
2. In the fallback path of `getEnrichedNotifications`, group the no-relatedId notifications and resolve their join-request details via `getJoinRequestDetailsBatch` (collect their teamIds once) instead of per-item `getJoinRequestDetails(null)` calls; keep the single-item call for genuinely missing-batch results.
3. Keep `getJoinRequestDetails` private-signature intact if still used by the batch path; otherwise remove the now-dead single-lookup overload.
4. Extend the impl test to assert the batch DAO methods are called once for a page of join-request notifications rather than once per notification.
acceptance: `./gradlew testDefaultDebugUnitTest` green; notifications screen shows identical type chips and join-request team/requester names; number of join-request queries for a list of N such notifications drops from ~3N to a constant batch
size budget: ~60 changed lines, 2 files
out of scope: no DAO-interface additions (use existing queries); no changes to notification types or display strings

---

### 7. project finance reports and drop dead `TeamDao` queries (roadmap 1+7)
context: `repository/EnterprisesRepositoryImpl.kt:86-91` observes full `MyTeam` entities via `teamDao.observeNonArchivedReportsByTeamId(teamId)` only to map each row through `toFinanceReport()` (line 138) — every emission materializes ~30 columns for the 16 `FinanceReport` fields. `data/room/dao/TeamDao.kt:26` already proves the projection pattern with `getNonArchivedReportCsvProjectionsByTeamId`. `TeamDao.getAll()` (line 17) and `getNonArchivedReportsByTeamId` (line 25) have no production callers — the latter is exercised only by `TeamDaoTest.kt:111-163`.
files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` — add `observeNonArchivedReportProjectionsByTeamId` selecting only the `FinanceReport` columns (`isUpdated AS updated` needed: `MyTeam.isUpdated` → `FinanceReport.updated`, see `MyTeam.kt:50`); delete `getAll()` and `getNonArchivedReportsByTeamId`
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` — return the projected Flow directly; remove the `.map { it.toFinanceReport() }` and the now-dead private `toFinanceReport()` extension at line 138
- `app/src/main/java/org/ole/planet/myplanet/model/FinanceReport.kt` — reuse as the projection class; no change expected unless Room needs a field renamed (then prefer a `SELECT ... AS` alias instead)
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt` — remove the `getNonArchivedReportsByTeamId` tests (111-163); add an equivalent assertion against the new projection query
- leave alone: `MyTeam.kt`, `TeamsRepositoryImpl`, `EnterprisesRepository.kt` interface (signature stays `Flow<List<FinanceReport>>`), `EnterprisesRepositoryImplTest.kt` — update only if its stubs reference the removed DAO call
steps:
1. Add the projection `Flow` query to `TeamDao` with the same WHERE/ORDER BY as line 24, selecting the 16 `FinanceReport` columns with `AS` aliases where names differ.
2. Repoint `EnterprisesRepositoryImpl.getReportsFlow` to it; delete `toFinanceReport()`.
3. Delete `getAll()` and `getNonArchivedReportsByTeamId` from `TeamDao`.
4. Update `TeamDaoTest` (and `EnterprisesRepositoryImplTest` if it mocks the old method) — projection test should assert ordering by `createdDate` DESC and the archived exclusion, mirroring deleted tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; enterprise finances/report list shows identical rows in identical order; no remaining references to `getAll`/`getNonArchivedReportsByTeamId`/`toFinanceReport`
size budget: ~70 changed lines (mostly test moves), 4-5 files
out of scope: no entity/schema changes; do not touch `getNonArchivedReportCsvProjectionsByTeamId` or CSV export

---

### 8. move resource `_all_docs` fetching into `SyncRepository` (roadmap 5+1)
context: `services/sync/SyncManager.kt:290-439` `resourceTransactionSync` performs raw CouchDB calls (`apiInterface.getJsonObject(header, "$url/resources/_all_docs?…")` at 310 and 341), count extraction (312-314), and per-row doc filtering (`has("doc")`, `getString("_id")`, `_design` skip at 367-377) inside the sync service, while `SyncRepository` (`repository/SyncRepository.kt:5`) already encapsulates sibling sync primitives (`processShelfParallel`, `getShelvesWithData`). Fetching and doc-parsing belong behind the repository; the service should keep only loop control, progress reporting and batch-size adaptation.
files:
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt` — add e.g. `suspend fun fetchResourceCount(url: String, header: String): Int?` and `suspend fun fetchResourceDocsPage(url: String, header: String, limit: Int, skip: Int): List<JsonObject>?`
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` — implement both with the moved `ApiClient.executeWithRetryAndWrap` + `toGson()` + row filtering logic
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` — replace inline API/parse code with repository calls; keep `AdaptiveBatchProcessor`, `syncTimeLogger` logging, `_syncStatus` progress updates, `sharedPrefManager` position checkpointing and the `hadBatchFailure`/`removeDeletedResources` cleanup in place
- `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt` — cover: count parsed from `total_rows`, `_design` docs and missing-`doc` rows filtered out, null body → null
- leave alone: `TransactionSyncManager` (open PR owns it), `ResourcesRepository`/`ResourcesRepositoryImpl` (open PRs), `ApiClient`, `ApiInterface`
steps:
1. Add the two methods to `SyncRepository` (name them to taste; keep them suspend and nullable-returning to preserve failure semantics).
2. Move lines 309-316 (count) and 340-377 (batch fetch + valid-doc filtering) into `SyncRepositoryImpl` implementations, unchanged in behavior including null-on-failure.
3. In `SyncManager`, call the repository; keep every log line, batch counter, `skip` bookkeeping and progress emission exactly as today.
4. Add repository unit tests for count/parse/filter/failure; run existing `SyncManager` tests.
acceptance: `./gradlew testDefaultDebugUnitTest` green; a real resource sync still pages through `_all_docs`, saves batches via `resourcesRepository.batchInsertResources`, writes the same `syncTimeLogger` entries, and runs delete-cleanup only when no batch failed
size budget: ~130 changed lines (mostly a move), 4 files
out of scope: no changes to batch sizing, retry policy, or the `myLibraryTransactionSync`/other sync phases; no new dependencies

---

### 9. unify leader hand-off before member removal in `RequestsViewModel` (roadmap 8+3)
context: `ui/teams/members/RequestsViewModel.kt:81-83` (`leaveTeam`) and `97-104` (`removeMember` on self) duplicate the same sequence — `getNextLeaderCandidate` → `updateTeamLeader` → `removeMember`. Worse, `leaveTeam` skips the guard at 100-102: when the leaving leader has no eligible successor, `removeMember` emits `MemberActionResult.CannotRemoveLastLeader` but `leaveTeam` removes them anyway, leaving the team leaderless. One shared path fixes the divergence and shrinks the VM.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt` — extract a private `handOffLeadershipAndRemove(teamId, memberId, …)` used by both functions; apply the `CannotRemoveLastLeader` outcome to `leaveTeam`
- leave alone: `RequestsViewModelTest.kt`, `RequestsFragment.kt`, `RequestsAdapter.kt`, `TeamsMembersRepository`/`TeamsRepositoryImpl` (open PRs own the test file and impl)
steps:
1. Extract the candidate-lookup → promote → remove sequence into one private suspend function returning a sealed outcome (`Removed`/`NoSuccessor`), parameterized on the self-removal condition so `removeMember` keeps only applying it when `currentUserId == memberId`.
2. In `leaveTeam`, map `NoSuccessor` to `_actionResults.emit(MemberActionResult.CannotRemoveLastLeader)` and skip `removeMember` — matching `removeMember`'s existing behavior.
3. Keep `loadJoinedMembers(teamId)` and the per-action result emissions (`LeftTeam`, `MemberRemoved`, `Failed`) on each public function exactly as today.
4. Note in the PR description that `leaveTeam` on a last-leader team now blocks instead of orphaning leadership — existing tests must still pass; add no new test file (the test file is off-limits this round).
acceptance: `./gradlew testDefaultDebugUnitTest` green; on the members screen a leader leaving with no successor now shows the "cannot remove last leader" outcome instead of leaving; removing a non-leader member still works
size budget: ~35 changed lines, 1 file
out of scope: no repository changes (the hand-off should stay multi-call for now — moving it behind one repo call needs files open PRs own); no UI changes

---

### 10. extract examination-item mapping from `HealthExaminationAdapter` (roadmap 3+10)
context: `ui/health/HealthExaminationAdapter.kt:58-88` `submitExaminations` does per-item data work inside a `ListAdapter`: Tink-backed decryption `item.getEncryptedDataAsJson(user)` (63), `createdBy` extraction (64), creator-name resolution against `userMap` with a `displayNameCache` (66-74), and date formatting (62) — a pure mapping `List<HealthExamination> → List<HealthExaminationItem>` trapped in an Android-bound class. Hoisting it into a standalone mapper makes it unit-testable and is exactly the "state hoisted out of views" move roadmap 10 wants before this screen is ever ported.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt` — NEW: pure function/class `buildItems(list, userModel, userMap): List<HealthExaminationItem>` containing lines 60-83's logic (decrypt, resolve name, format date — keep `formatDate` call; `dispatcherProvider` stays in the adapter call site)
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt` — replace the inline `list.map { … }` block with the mapper call; keep `withContext(dispatcherProvider.default)`/`submitList` here
- leave alone: `MyHealthFragment`, `HealthExaminationActivity`, `HealthRepository`/impl, `HealthExamination` model (`getEncryptedDataAsJson` stays put)
steps:
1. Create the mapper file in `ui/health/` exposing the pure mapping (no `android.*` imports beyond what `getEncryptedDataAsJson`/`formatDate` already pull transitively — keep `TextUtils` out by using `isNullOrBlank()`).
2. Move the `displayNameCache` + item-construction block verbatim; `HealthExaminationItem` stays nested in the adapter (reference it as `HealthExaminationAdapter.HealthExaminationItem` or move it alongside the mapper — pick one, keep imports compiling).
3. Call the mapper from `submitExaminations` inside the existing `withContext(dispatcherProvider.default)`; delete now-unused imports (`TextUtils`, `GsonUtils.getString`, `formatDate`) from the adapter if unused.
4. Add a small unit test only if a trivial non-Android path exists for `getEncryptedDataAsJson` inputs — otherwise rely on existing suite.
acceptance: `./gradlew testDefaultDebugUnitTest` green; examinations list still shows dates, self-vs-other styling, resolved creator names, and the encrypted-detail alert unchanged
size budget: ~80 lines moved, ~10 lines of glue; 2 files
out of scope: no decryption-logic changes; no adapter UI changes; do not move `HealthExaminationItem` into `model/`

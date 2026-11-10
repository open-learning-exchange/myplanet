# myPlanet Refactor Round — Repository Boundaries & Layer Tightening Work Orders

date: 2026-11-10 · base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 · open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075

### 1. replace UserSessionManager dependency in SettingsViewModel with UserReadRepository (roadmap 1+3+4)
context: `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt:25` injects `UserSessionManager` (`SettingsViewModel.kt:16`), an Android service-layer class backed by `Context`, `SharedPrefManager`, `ActivitiesRepository`, and `TimeProvider`, solely to evaluate `userSessionManager.getUserModel()?.id?.startsWith("guest") == true` in `SettingsViewModel.isGuest()` (`SettingsViewModel.kt:41`).
`UserSessionManager.getUserModel()` is a pass-through to `userRepository.getUserModel()`, which is already exposed on the narrow `UserReadRepository` contract.
Swapping `UserSessionManager` for `UserReadRepository` removes the service-layer leak from `SettingsViewModel` and advances roadmap 9 (Kotlin Multiplatform) by keeping the ViewModel bound to platform-free repository interfaces.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt` (class `SettingsViewModel`, constructor parameter at line 25 and function `isGuest` at line 41)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/settings/SettingsViewModelTest.kt` (class `SettingsViewModelTest`, mock field at line 44 and test cases at lines 47-142)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt` untouched; do NOT touch `UserSessionManager` — an open PR owns it
steps:
1. In `SettingsViewModel.kt`, replace `import org.ole.planet.myplanet.services.UserSessionManager` (line 16) with `import org.ole.planet.myplanet.repository.UserReadRepository`.
2. In `SettingsViewModel` constructor (`SettingsViewModel.kt:20-26`), replace `private val userSessionManager: UserSessionManager` (line 25) with `private val userRepository: UserReadRepository`.
3. In `SettingsViewModel.isGuest()` (`SettingsViewModel.kt:41`), replace `userSessionManager.getUserModel()?.id?.startsWith("guest") == true` with `userRepository.getUserModel()?.id?.startsWith("guest") == true`.
4. In `SettingsViewModelTest.kt`, replace `import org.ole.planet.myplanet.services.UserSessionManager` (line 22) and `private val userSessionManager: UserSessionManager = mockk(relaxed = true)` (line 44) with `UserReadRepository` (`private val userRepository: UserReadRepository = mockk(relaxed = true)`).
5. In `SettingsViewModelTest.kt` (lines 50-140), update the `coEvery { ... }` stubs and `SettingsViewModel(...)` constructor calls to pass `userRepository`.
6. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.settings.SettingsViewModelTest"` and confirm all 6 unit tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening Settings as a guest or registered learner still accurately detects guest status via `SettingsViewModel.isGuest()`.
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `UserSessionManager`, `SettingsActivity.kt`, or any Hilt binding modules.

---

### 2. remove unused UserSessionManager injection from TakeCourseFragment and narrow TakeCourseViewModel to UserReadRepository (roadmap 1+3+4)
context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt:42-43` field-injects `@Inject lateinit var userSessionManager: UserSessionManager` (`TakeCourseFragment.kt:30`) even though `userSessionManager` is never read anywhere in the 533-line fragment.
Meanwhile, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:42` injects the full 50-method `UserRepository` interface, leaking user authentication, security-key management, and sync-upload operations into the courses feature when `TakeCourseViewModel.loadCourse` (`TakeCourseViewModel.kt:117`) only calls `userRepository.getUserModel()` (unlike neighbor `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt:18`, which already injects `UserReadRepository`).
Removing the dead service injection from `TakeCourseFragment` and narrowing `TakeCourseViewModel` to `UserReadRepository` tightens the cross-feature boundary and advances roadmaps 9 and 10.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt` (class `TakeCourseFragment`, unused imports at lines 19, 30 and `@Inject lateinit var userSessionManager` at lines 42-43)
- touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (class `TakeCourseViewModel`, import at line 18, constructor parameter at line 42, and `loadCourse` at line 117)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt` (class `TakeCourseViewModelTest`, import at line 26 and mock field at line 40)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt` untouched; do NOT touch `CoursesRepositoryImpl` or `ProgressRepositoryImpl` — open PRs own them
steps:
1. In `TakeCourseFragment.kt`, delete `@Inject` and `lateinit var userSessionManager: UserSessionManager` (lines 42-43).
2. In `TakeCourseFragment.kt`, remove the unused `javax.inject.Inject` (line 19) and `org.ole.planet.myplanet.services.UserSessionManager` (line 30) imports.
3. In `TakeCourseViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 18) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change constructor parameter `private val userRepository: UserRepository` (line 42) to `private val userRepository: UserReadRepository`.
4. In `TakeCourseViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 26) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change `private val userRepository: UserRepository = mockk()` (line 40) to `private val userRepository: UserReadRepository = mockk()`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening a course in `TakeCourseFragment` still loads the active user, course steps, progress, and blocking-step index identically.
size budget: ~12 changed lines, 3 files
out of scope: Do not touch `CoursesRepositoryImpl`, `ProgressRepositoryImpl`, or `ProgressViewModel.kt`.

---

### 3. move active-user lookup from ChatHistoryFragment into ChatViewModel via UserReadRepository (roadmap 1+3+8)
context: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt:135-139` reads `sharedPrefManager.getUserId()` directly in `refreshChatHistory()` and passes that raw preference string into `sharedViewModel.loadChatHistoryScreenData(userId)` (`app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:134-160`), which then resolves the user via `loadCurrentUser(userId)` (`ChatViewModel.kt:187-192`) calling `userRepository.getUserById(userId)`.
However, `ChatViewModel` (`ChatViewModel.kt:56`) already injects `userRepository: UserReadRepository`, which exposes `getUserModel()` to resolve the active user inside the repository layer without the UI reading `SharedPrefManager.getUserId()`.
Giving `ChatViewModel.loadChatHistoryScreenData(userId: String? = null)` a default `null` parameter and falling back to `userRepository.getUserModel()` inside `loadCurrentUser(userId)` when `userId.isNullOrEmpty()` moves active-user resolution out of `ChatHistoryFragment` and into the repository boundary, advancing roadmaps 9 and 10.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` (class `ChatHistoryFragment`, function `refreshChatHistory` at lines 135-139)
- touch `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (class `ChatViewModel`, functions `loadChatHistoryScreenData` at lines 134-160 and `loadCurrentUser` at lines 187-192)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` (class `ChatViewModelTest`, `userRepository` field at lines 33, 44 and screen-data tests at lines 223-267)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryScreenData.kt` untouched; do NOT touch `ChatDetailFragment` or `ChatRepositoryImpl` — open PRs own them
steps:
1. In `ChatViewModel.kt`, give `loadChatHistoryScreenData(userId: String? = null)` (lines 134-136) a default `null` argument.
2. In `ChatViewModel.loadCurrentUser(userId: String?)` (`ChatViewModel.kt:187-192`), replace `if (userId.isNullOrEmpty()) { return null }` with `if (userId.isNullOrEmpty()) { return userRepository.getUserModel() }`.
3. In `ChatHistoryFragment.refreshChatHistory()` (`ChatHistoryFragment.kt:135-139`), call `sharedViewModel.loadChatHistoryScreenData()` without reading `sharedPrefManager.getUserId()`.
4. In `ChatViewModelTest.kt`, narrow `private lateinit var userRepository: UserRepository` (lines 33, 44) to `UserReadRepository` and add a unit test verifying that `loadChatHistoryScreenData()` with no `userId` argument resolves `currentUser` via `userRepository.getUserModel()`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.ChatViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening Chat History still loads the signed-in user's chat history, news messages, and share targets on initial load and realtime refresh.
size budget: ~28 changed lines, 3 files
out of scope: Do not modify `ChatDetailFragment`, `ChatRepositoryImpl`, or `ChatHistoryScreenData.kt`.

---

### 4. check database population before file existence in DictionaryRepositoryImpl and remove duplicate count query in DictionaryActivity (roadmap 1+3+7)
context: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:36-45` checks `if (!dictionaryFileReader.exists()) return@withContext DictionaryLoad.FileMissing` (lines 38-40) before checking `if (dictionaryDao.count() > 0) return@withLock DictionaryLoad.AlreadyPopulated` (lines 43-45).
Consequently, if the Room `dictionary` table is already populated but the source `dictionary.json` file was cleaned up from disk, `insertDictionaryData()` returns `DictionaryLoad.FileMissing` and causes `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt:84-94` to trigger an unnecessary download of `Constants.DICTIONARY_URL`.
In addition, `DictionaryActivity.onCreate` (`DictionaryActivity.kt:60-61`) invokes both `viewModel.loadCount()` and `viewModel.loadDictionary()` back-to-back, issuing two concurrent `dictionaryRepository.count()` queries on launch; checking `dictionaryDao.count() > 0` first and removing the redundant `viewModel.loadCount()` call fixes the false `FileMissing` state and eliminates the extra Room `COUNT` query, advancing roadmap 9.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` (class `DictionaryRepositoryImpl`, function `insertDictionaryData` at lines 36-64)
- touch `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt` (class `DictionaryActivity`, function `onCreate` at lines 52-67)
- touch `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt` (class `DictionaryRepositoryImplTest`, test cases at lines 43-62)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt` and `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt` untouched
steps:
1. In `DictionaryRepositoryImpl.insertDictionaryData()` (`DictionaryRepositoryImpl.kt:36-46`), inside `withContext(dispatcherProvider.io)` and `seedMutex.withLock`, check `if (dictionaryDao.count() > 0) return@withLock DictionaryLoad.AlreadyPopulated` first, and only check `if (!dictionaryFileReader.exists()) return@withLock DictionaryLoad.FileMissing` when `dictionaryDao.count() == 0L`.
2. In `DictionaryActivity.onCreate` (`DictionaryActivity.kt:60-61`), remove the redundant `viewModel.loadCount()` call at line 60 so `viewModel.loadDictionary()` is the single entry point on screen creation.
3. In `DictionaryRepositoryImplTest.kt`, update `insertDictionaryData returns FileMissing if file does not exist` (lines 44-51) to stub `coEvery { dictionaryDao.count() } returns 0L`.
4. In `DictionaryRepositoryImplTest.kt`, add a test verifying that `insertDictionaryData()` returns `DictionaryLoad.AlreadyPopulated` without touching `dictionaryFileReader` when `dictionaryDao.count() > 0` even if `dictionaryFileReader.exists()` is `false`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening `DictionaryActivity` when the dictionary table is already populated immediately displays the word count without requiring `dictionary.json` on disk or running a duplicate `COUNT(*)` query.
size budget: ~25 changed lines, 3 files
out of scope: Do not modify `DictionaryViewModel.kt` or `DictionaryViewModelTest.kt`.

---

### 5. replace pre-update SELECT with UPDATE row-count check in MyLifeDao.updateVisibility (roadmap 1+7+8)
context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:43-54` implements `updateVisibility(id: String, isVisible: Boolean)` by first executing `getIdsByIdInternal(id)` (`SELECT _id FROM my_life WHERE _id = :id` at lines 34-35) solely to check `if (exactMatches.isNotEmpty())` before executing `updateVisibilityByIdInternal(id, isVisible)` (`UPDATE my_life SET isVisible = :isVisible WHERE _id = :id` at lines 40-41).
Because Room `@Query("UPDATE ...")` functions can return `Int` (the number of updated rows), `updateVisibilityByIdInternal` can return the affected row count directly and `updateVisibility` can short-circuit whenever `updateVisibilityByIdInternal(id, isVisible) > 0`.
This eliminates the pre-update `SELECT` query on every exact-ID hit and removes the unused `getIdsByIdInternal` DAO method.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt` (interface `MyLifeDao`, functions `getIdsByIdInternal`, `updateVisibilityByIdInternal`, and `updateVisibility` at lines 34-54)
- touch `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt` (class `MyLifeDaoTest`, visibility update tests at lines 164-244)
- leave `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` untouched; do NOT touch `LifeRepositoryImplTest` — an open PR owns it
steps:
1. In `MyLifeDao.kt`, remove `getIdsByIdInternal(id: String): List<String>` (lines 34-35).
2. Change `suspend fun updateVisibilityByIdInternal(id: String, isVisible: Boolean)` (`MyLifeDao.kt:40-41`) to return `Int`.
3. In `MyLifeDao.updateVisibility(id: String, isVisible: Boolean)` (`MyLifeDao.kt:43-54`), call `if (updateVisibilityByIdInternal(id, isVisible) > 0) return`, then fall back to `val aliasMatches = getIdsByAliasInternal(id)` and `if (aliasMatches.size == 1) updateVisibilityByIdInternal(aliasMatches[0], isVisible)` as before.
4. In `MyLifeDaoTest.kt`, add a test verifying that `updateVisibilityByIdInternal` returns the updated row count (`1` on an existing `_id` and `0` on a missing `_id`) while keeping all existing `updateVisibility_*` tests at lines 164-244 green.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; toggling MyLife item visibility in the Life screen updates the target row in a single SQLite `UPDATE` without a preceding `SELECT` when matched by `_id`.
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `LifeRepositoryImpl.kt` or `LifeRepositoryImplTest`.

---

### 6. wrap TeamLogDao.deleteByIds in @Transaction and short-circuit on empty input (roadmap 1+7+8)
context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt:52-54` implements `deleteByIds(ids: List<String>)` by chunking `ids.distinct()` into 900-item batches and calling `deleteByIdsInternal(it)` (`TeamLogDao.kt:49-50`), but omits `@Transaction` and does not return early when `ids.isEmpty()`.
Without `@Transaction`, deleting more than 900 team log IDs commits each chunk in a separate SQLite transaction, risking partial deletes on cancellation and paying per-chunk transaction overhead.
Adding `@Transaction` and an `if (ids.isEmpty()) return` guard aligns `TeamLogDao.deleteByIds` with `TeamDao.deleteByIds` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:44-48`).
files:
- touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt` (interface `TeamLogDao`, imports at lines 3-7 and function `deleteByIds` at lines 52-54)
- touch `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamLogDaoTest.kt` (class `TeamLogDaoTest`, test cases at lines 82-90)
- leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` untouched; do NOT touch `TeamsRepositoryImpl` — open PRs own it
steps:
1. In `TeamLogDao.kt`, add `import androidx.room.Transaction` (after line 6).
2. Annotate `suspend fun deleteByIds(ids: List<String>)` (`TeamLogDao.kt:52-54`) with `@Transaction`.
3. Add `if (ids.isEmpty()) return` as the first statement of `TeamLogDao.deleteByIds(ids: List<String>)` before `ids.distinct().chunked(900).forEach { deleteByIdsInternal(it) }`.
4. In `TeamLogDaoTest.kt`, add tests verifying `deleteByIds` with an empty list (no rows removed) and with >900 IDs plus duplicates (all targeted rows across chunks removed, unrequested rows preserved).
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.TeamLogDaoTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; batch team-log deletions execute atomically inside a single Room transaction and no-op cleanly on empty input.
size budget: ~30 changed lines, 2 files
out of scope: Do not modify `TeamsRepositoryImpl` or `TeamDao.kt`.

---

### 7. wrap QuestionDao.deleteStaleQuestions in @Transaction and guard empty input (roadmap 1+7+8)
context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:29-33` implements `deleteStaleQuestions(questions: List<ExamQuestion>)` by grouping `questions` by `examId` and issuing a separate `deleteStaleInternal(examId, ...)` `DELETE` statement per exam without a `@Transaction` annotation or an `if (questions.isEmpty()) return` check.
When exam sync processes a batch of exams, `deleteStaleQuestions` executes each exam's `DELETE` in its own auto-commit SQLite transaction instead of a single atomic transaction, and allocates an empty group map when `questions` is empty.
Annotating `deleteStaleQuestions` with `@Transaction` and returning early when `questions.isEmpty()` makes multi-exam stale-question cleanup atomic and avoids wasted work on empty batches.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt` (interface `QuestionDao`, imports at lines 3-5 and function `deleteStaleQuestions` at lines 29-33)
- touch `app/src/test/java/org/ole/planet/myplanet/data/room/dao/QuestionDaoTest.kt` (class `QuestionDaoTest`, test cases at lines 38-54)
- leave `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt` untouched; do NOT touch `SurveysRepositoryImpl` or `ExamDao` — open PRs own them
steps:
1. In `QuestionDao.kt`, add `import androidx.room.Transaction` (after line 4).
2. Annotate `suspend fun deleteStaleQuestions(questions: List<ExamQuestion>)` (`QuestionDao.kt:29-33`) with `@Transaction`.
3. Add `if (questions.isEmpty()) return` at the start of `QuestionDao.deleteStaleQuestions(questions: List<ExamQuestion>)`.
4. In `QuestionDaoTest.kt`, add a test verifying that `deleteStaleQuestions(emptyList())` leaves existing questions intact and that a multi-exam batch (`exam1` and `exam2`) removes stale questions across both exams in one call.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.QuestionDaoTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; syncing exams deletes stale questions across all synced exams in a single Room transaction.
size budget: ~25 changed lines, 2 files
out of scope: Do not modify `SurveysRepositoryImpl`, `ExamDao`, or `AnswerDao.kt`.

---

### 8. narrow ResourcesViewModel user dependency to UserReadRepository and move in-memory sorting to default dispatcher (roadmap 1+3+7)
context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:31` injects the full `UserRepository` interface (`ResourcesViewModel.kt:25`) even though `ResourcesViewModel` only calls `userRepository.getUserModel()` in `userDeferred` (`ResourcesViewModel.kt:39`), which is already provided by `UserReadRepository`.
In addition, `ResourcesViewModel.toggleSortOrder` (`ResourcesViewModel.kt:177`), `toggleTitleSortOrder` (`ResourcesViewModel.kt:185`), and `applyCurrentSort` (`ResourcesViewModel.kt:193`) switch to `dispatcherProvider.io` to perform pure in-memory list sorting in `applyCurrentSortSynchronous` (`ResourcesViewModel.kt:197-214`), whereas `getFilterFacets` (`ResourcesViewModel.kt:137`) properly uses `dispatcherProvider.default`.
Narrowing `userRepository` to `UserReadRepository` tightens the repository boundary (advancing roadmap 9), and moving the three sort functions onto `dispatcherProvider.default` keeps CPU-bound sorting off the I/O thread pool.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` (class `ResourcesViewModel`, import at line 25, constructor parameter at line 31, and sort functions at lines 177-195)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt` (class `ResourcesViewModelTest`, import at line 26 and mock field at line 35)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesSortFragment.kt` untouched; do NOT touch `ResourcesRepositoryImpl` or `ResourcesFragment` — open PRs own them
steps:
1. In `ResourcesViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 25) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change constructor parameter `private val userRepository: UserRepository` (line 31) to `private val userRepository: UserReadRepository`.
2. In `ResourcesViewModel.kt`, change `withContext(dispatcherProvider.io)` to `withContext(dispatcherProvider.default)` in `toggleSortOrder` (line 177), `toggleTitleSortOrder` (line 185), and `applyCurrentSort` (line 193).
3. In `ResourcesViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 26) with `import org.ole.planet.myplanet.repository.UserReadRepository`.
4. In `ResourcesViewModelTest.kt`, change `private val userRepository = mockk<UserRepository>(relaxed = true)` (line 35) to `private val userRepository = mockk<UserReadRepository>(relaxed = true)`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.resources.ResourcesViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; sorting resources by date or title in the Library screen produces the same ascending/descending order on `dispatcherProvider.default`.
size budget: ~14 changed lines, 2 files
out of scope: Do not modify `ResourcesRepositoryImpl`, `ResourcesFragment`, or `ResourcesSortFragment.kt`.

---

### 9. narrow SubmissionDetailViewModel to UserReadRepository and collapse duplicate stateIn pipeline (roadmap 1+3+7)
context: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:30` injects the full `UserRepository` interface (`SubmissionDetailViewModel.kt:16`) solely to call `userRepository.getUserById(it)` (`SubmissionDetailViewModel.kt:38`), which is already part of `UserReadRepository`.
In addition, `SubmissionDetailViewModel` materializes a private intermediate `StateFlow<SubmissionDetail?>` (`submissionDetailState` at lines 35-43) with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)` and immediately maps it into a second `StateFlow<SubmissionDetailUiState>` (`uiState` at lines 45-59) with a second `.stateIn(...)`, maintaining two `StateFlow` coroutines when `submissionDetailState` has no other consumers.
Narrowing `userRepository` to `UserReadRepository` and mapping the upstream `flow { ... }` directly into `uiState` tightens the repository boundary (advancing roadmap 9) and simplifies the repository-to-ViewModel reactive pipeline.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt` (class `SubmissionDetailViewModel`, import at line 16, constructor at line 30, and flows at lines 35-59)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt` (class `SubmissionDetailViewModelTest`, import at line 23 and mock setup at lines 30, 36)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt` untouched; do NOT touch `SubmissionsRepositoryImpl` — open PRs own it
steps:
1. In `SubmissionDetailViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 16) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change `private val userRepository: UserRepository` (line 30) to `private val userRepository: UserReadRepository`.
2. In `SubmissionDetailViewModel.kt` (lines 35-59), remove the intermediate `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)` on `submissionDetailState` (and remove unused `import org.ole.planet.myplanet.model.SubmissionDetail` at line 14 if inlined) so only `uiState` calls `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubmissionDetailUiState())`.
3. In `SubmissionDetailViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 23) with `import org.ole.planet.myplanet.repository.UserReadRepository`.
4. In `SubmissionDetailViewModelTest.kt`, update `private lateinit var userRepository: UserReadRepository` (line 30) and `mock(UserReadRepository::class.java)` (line 36).
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.submissions.SubmissionDetailViewModelTest"` and confirm both unit tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening `SubmissionDetailFragment` renders the submission title, status, formatted date, submitter name, and question-answer list identically.
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `SubmissionsRepositoryImpl` or `SubmissionDetailFragment.kt`.

---

### 10. promote getCurrentUserId to UserReadRepository and narrow LifeViewModel off UserRepository (roadmap 1+3+4)
context: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt:20` injects the full 50-method `UserRepository` (`LifeViewModel.kt:15`) only because `resolveUserId()` (`LifeViewModel.kt:28-32`) calls `userRepository.getCurrentUserId()` alongside `userRepository.getUserModel()`.
However, `getCurrentUserId(): String?` is a pure read operation declared on `UserRepository` (`app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:80`) rather than on its super-interface `UserReadRepository` (`app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5-9`).
Moving `suspend fun getCurrentUserId(): String?` from `UserRepository` up to `UserReadRepository` (which `UserRepository` already extends at `UserRepository.kt:23`) preserves source and binary compatibility for all `UserRepository` callers while allowing `LifeViewModel` to depend strictly on `UserReadRepository`, advancing roadmap 9.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt` (interface `UserReadRepository` at lines 5-9)
- touch `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` (interface `UserRepository`, function `getCurrentUserId` at line 80)
- touch `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` (class `LifeViewModel`, import at line 15 and constructor parameter at line 20)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt` (class `LifeViewModelTest`, import at line 22 and mock field at line 28)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt` untouched; do NOT touch `UserRepositoryImpl` — open PRs own it
steps:
1. In `UserReadRepository.kt` (lines 5-9), add `suspend fun getCurrentUserId(): String?` to `interface UserReadRepository`.
2. In `UserRepository.kt`, remove the duplicate `suspend fun getCurrentUserId(): String?` declaration at line 80 (since `UserRepository` extends `UserReadRepository` at line 23).
3. In `LifeViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 15) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change constructor parameter `private val userRepository: UserRepository` (line 20) to `private val userRepository: UserReadRepository`.
4. In `LifeViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 22) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change `private lateinit var userRepository: UserRepository` (line 28) to `private lateinit var userRepository: UserReadRepository`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.life.LifeViewModelTest"` and confirm all 10 unit tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the MyLife screen loads, reorders, and toggles visibility for the active user via `UserReadRepository`.
size budget: ~12 changed lines, 4 files
out of scope: Do not modify `UserRepositoryImpl` or `LifeFragment.kt`.

Signed: antigravity gemini 3.1 pro high

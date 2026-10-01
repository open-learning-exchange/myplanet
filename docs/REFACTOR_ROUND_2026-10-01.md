# myPlanet refactor round: 10 work orders

- **Date:** 2026-10-01
- **Base commit:** `286e66e0e1daf285c34d8456deb0028016bf858a` (`master`, "all: smoother importing (fixes #17814) (#17813)")
- **Open PRs checked (33):** 17812, 17811, 17776, 17694, 17680, 17435, 17254, 17187, 16623, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075. For each one I fetched the head and diffed it against its merge-base with `master`. That gives 367 unique files, and **no task below touches any of them**.
- **Workflow logs read:**
  - test: run 36847822998 on `master`, both shards
  - release: run 36847822969 on `master`, `lite` job
  - build: run 36857156308, `default` job, the newest build run
- **Off-limits because open PRs touch them:** `.github/workflows/{build,test,release,labels}.yml`, `.github/scripts/labels.sh`, `app/build.gradle`, `build.gradle.kts`, `settings.gradle`, `gradle/libs.versions.toml`, `CLAUDE.md`, `AndroidManifest.xml`, `TeamsRepositoryImpl`, `UserRepositoryImpl`, `SubmissionsRepositoryImpl`, `ChatDao`, `FileUtils`, `TimeUtils`, and the other open-PR files.
- **Log findings that are not tasks:**
  - The Play Store refused 7433 twice in the release run. The fix belongs in `release.yml`, which is off-limits.
  - `android.enableJetifier=true` is deprecated (AGP removes it in 10.0). It cannot be dropped while `com.afollestad.material-dialogs:commons:0.9.6.0` remains (`gradle.properties:25-26`). Task 8 removes one of the five material-dialogs call sites.

Every task is independent: no file appears in two tasks, and they can merge in any order. Each one must leave `./gradlew testDefaultDebugUnitTest` green.

---

### 1. Make `RemovedLogDao.getRemovedDocIds` return non-null ids and clear its KSP warning (roadmap 1+8, moves 9)
**context:**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt:25-26` declares `suspend fun getRemovedDocIds(type: String, userId: String?): List<String?>`.
- KSP prints this warning on every build, test and release job: `RemovedLogDao.kt:26: The DAO function return type (kotlin.collections.List<kotlin.String?>) with the nullable type argument is meaningless because for now Room will never put a null value in a result.` It appears in test run 36847822998 and release run 36847822969.
- `RemovedLog.docId` is nullable (`model/RemovedLog.kt:17`). So the honest fix is to filter NULL in SQL and return `List<String>`. Then every caller gets a clean, platform-free contract.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt`: the `getRemovedDocIds` query and signature only.
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt`: add one test.
- Leave these alone:
  - `repository/UserRepositoryImpl.kt:1259-1260`, which calls `.filterNotNull()` on the result. That still compiles and is harmless. An open PR owns the file, so do NOT edit it.
  - `data/room/dao/ChatDao.kt:19`, which has the same warning but is owned by an open PR.

**steps:**
1. In `RemovedLogDao.kt:25`, append `AND docId IS NOT NULL` to the `@Query` string of `getRemovedDocIds`.
2. In `RemovedLogDao.kt:26`, change the return type from `List<String?>` to `List<String>`.
3. In `RemovedLogDaoTest.kt`, add `getRemovedDocIds_skipsRowsWithNullDocId`:
   - Insert two logs with the existing `createLog(...)` helper. Both use the same type/userId; one has `docId = null` and one has `docId = "doc1"`.
   - Assert the result is `listOf("doc1")`.
4. Run the DAO test class, then the full unit suite.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.RemovedLogDaoTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- `./gradlew assembleDefaultDebug 2>&1 | grep "RemovedLogDao.kt:26"` prints nothing.
- User-visible: courses and resources the user removed from myLibrary/myCourses stay hidden after a sync.

**size budget:** ~2 changed lines in the DAO plus ~15 lines of test, 2 files.

**out of scope:**
- Do not touch `UserRepositoryImpl` or `ChatDao`.
- Do not bump the `AppDatabase` version; the schema does not change.

---

### 2. Remove redundant null-handling on non-null ids in the upload, notification and label code (roadmap 5+8, moves 9)
**context:** The Kotlin compiler reports these lines in build run 36857156308 (`default`). Each one null-checks a property that is already a non-null `String`.

| Location | Code | Warning | Why it is non-null |
|---|---|---|---|
| `services/upload/UploadConfigs.kt:195` | `surveysRepository.getExamQuestions(exam.id ?: "")` | USELESS_ELVIS | `StepExam.id: String = ""`, `model/StepExam.kt:17` |
| `repository/NotificationsRepositoryImpl.kt:325` | `user.id?.let { id -> userMap[id] = … }` | UNNECESSARY_SAFE_CALL | `UserEntity.id: String = ""`, `model/UserEntity.kt:28` |
| `services/VoicesLabelManager.kt:83` | `if (voiceId != null)`, where `voiceId = voice.id` and `voice: News` (line 64) | SENSELESS_COMPARISON | `News.id: String = ""`, `model/News.kt:30-32` |

Dead null branches in the sync/upload core get in the way of the planned KMP extraction: readers cannot tell which values can really be null.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` (the `exams` config `serializer` lambda, around line 194)
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (the `for (user in users)` loop, lines 324-328)
- `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt` (`showChips`, lines 82-85)
- Leave alone: every other `UploadConfigs` entry, the per-row `mark*Uploaded` lambdas, and `UploadCoordinator`.

**steps:**
1. In `UploadConfigs.kt:195`, replace `exam.id ?: ""` with `exam.id`.
2. In `NotificationsRepositoryImpl.kt:324-328`, collapse the `user.id?.let { id -> … }` block into a direct `userMap[user.id] = user.name ?: "Unknown User"`. Keep the `?: "Unknown User"`, because `name` is nullable.
3. In `VoicesLabelManager.kt:82-85`, drop the `voiceId` local and its `if (voiceId != null)` guard. Call `launchLabelWrite("removeLabel") { removeLabelFn(voice.id, label) }` directly.
4. Rebuild, and confirm that none of the three locations appears in the compiler warnings.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` stays green. This covers `UploadConfigsTest`, `NotificationsRepositoryImplTest` and `VoicesLabelManagerTest`, if present.
- `./gradlew assembleDefaultDebug --warning-mode all 2>&1 | grep -E "UploadConfigs.kt:195|NotificationsRepositoryImpl.kt:325|VoicesLabelManager.kt:83"` prints nothing.
- User-visible:
  - Adopted surveys still upload with their questions.
  - Notification rows still show the sender's name.
  - Tapping a label chip's close icon on a voice still removes the label.

**size budget:** ~8 changed lines, 3 files.

**out of scope:**
- Do not batch the `mark*Uploaded` calls. Their repository implementations are owned by open PRs.
- No other cleanups in these files.

---

### 3. Remove redundant null-handling in personals, ratings and login-team UI code (roadmap 8, moves 10)
**context:** Build run 36857156308 reports these lines. All three guard against nulls on non-null `String` ids, which hides the real data contract from the screens that are next in line for Compose (roadmap 6/10).

| Location | Code | Warning | Why it is non-null |
|---|---|---|---|
| `ui/personals/PersonalsFragment.kt:119` and `:136` | `val id = personal.id ?: personal._id` followed by `if (id != null)` | USELESS_ELVIS | `Personal.id: String = ""`, `model/Personal.kt:11` |
| `ui/ratings/RatingsViewModel.kt:63` | `user.id?.takeIf { it.isNotBlank() } ?: user._id ?: ""` | UNNECESSARY_SAFE_CALL | `UserEntity.id: String` |
| `ui/sync/LoginActivity.kt:467` | `if (team._id != null && team._id == lastSelection)` | SENSELESS_COMPARISON | `MyTeam._id: String = ""`, `model/MyTeam.kt:22` |

**files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt` (`onEditPersonal` lines 119-125, `onDeletePersonal` lines 136-139)
- `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt` (`loadRatingData`, line 63)
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` (the team-spinner restore loop, lines 465-472)
- Leave alone:
  - The `MaterialDialog` usages in `LoginActivity`; they belong to a separate migration.
  - `PersonalsViewModel` and `PersonalsAdapter`.

**steps:**
1. `PersonalsFragment.kt:119` and `:136`: replace `personal.id ?: personal._id` plus the `if (id != null)` guard with a direct use of `personal.id`. Today the `_id` fallback can never run, so this keeps behaviour identical. Do not invent a new blank-id fallback.
2. `RatingsViewModel.kt:63`: change `user.id?.takeIf` to `user.id.takeIf`, and keep the `?: user._id ?: ""` chain. `takeIf` still returns a nullable value.
3. `LoginActivity.kt:467`: drop `team._id != null &&` from the condition.
4. Rebuild, and confirm the four warnings are gone.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` stays green. This includes `RatingsViewModelTest` and any `PersonalsFragment`/`LoginActivity` Robolectric tests.
- `./gradlew assembleDefaultDebug --warning-mode all 2>&1 | grep -E "PersonalsFragment.kt:(119|136)|RatingsViewModel.kt:63|LoginActivity.kt:467"` prints nothing.
- User-visible:
  - Editing and deleting a myPersonal entry still works.
  - The rating dialog still shows the user's own rating.
  - The login screen still preselects the last-chosen team.

**size budget:** ~10 changed lines, 3 files.

**out of scope:**
- No dialog migrations.
- No ViewModel extraction from `LoginActivity`.

---

### 4. Drop `Context` from `CourseDetailViewModel` and `CoursesStepsViewModel` by resolving the files dir through `StoragePathResolver` (roadmap 3+4, moves 9+10)
**context:** Both ViewModels inject `@ApplicationContext Context` for a single use, the external-files path that gets prepended to markdown image URLs:
- `ui/courses/CourseDetailViewModel.kt:37` (constructor) and `:69`, `"file://${context.getExternalFilesDir(null)}/ole/"`. This runs an uncached `getExternalFilesDir` disk call inside a `collect` on the main dispatcher.
- `ui/courses/CoursesStepsViewModel.kt:47` (constructor) and `:72-74`, `context.getExternalFilesDir(null)?.toString()`.

The injectable `utils/StoragePathResolver.kt:9-17` already wraps `FileUtils` path lookups. `FileUtils.getExternalFilesDir(context)` (`utils/FileUtils.kt:37-39`) caches the result. With the resolver injected, neither ViewModel needs an Android `Context`, which is a prerequisite for moving their state into portable presentation code.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/StoragePathResolver.kt`: add one function.
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt`: the constructor and `loadCourseDetail`.
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`: the constructor and `loadStep`.
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModelTest.kt` (lines 48, 58, 66-67)
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` (lines 44, 58, 62-63)
- Leave alone:
  - `utils/FileUtils.kt`, which is owned by an open PR. Call it; do not edit it.
  - `ResourceViewerViewModel` and `StorageBreakdownViewModel`, whose Context removal is not trivial.

**steps:**
1. In `StoragePathResolver`, add `fun resolveExternalFilesDir(): File? = FileUtils.getExternalFilesDir(context)`. Do NOT reuse `resolveOleDirectory()`: it yields `File("")` when the path is null, while the current code yields `"file://null/ole/"`.
2. In `CourseDetailViewModel`, replace the `@ApplicationContext private val context: Context` constructor parameter with `private val storagePathResolver: StoragePathResolver`. Build line 69 from `storagePathResolver.resolveExternalFilesDir()`. Remove the `android.content.Context` and `ApplicationContext` imports.
3. Make the same change in `CoursesStepsViewModel`. Keep the existing `withContext(dispatcherProvider.io)` wrapper around the resolver call at lines 72-74. Remove the now-unused imports.
4. In both tests:
   - Replace the `context` mock with `private val storagePathResolver: StoragePathResolver = mockk()`.
   - Stub `every { storagePathResolver.resolveExternalFilesDir() } returns null` in place of `context.getExternalFilesDir(null)`.
   - Pass the resolver to the constructor.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "*CourseDetailViewModelTest" --tests "*CoursesStepsViewModelTest"` passes.
- The full `./gradlew testDefaultDebugUnitTest` stays green.
- `grep -n "android.content.Context" app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` prints nothing.
- User-visible: images embedded in a downloaded course's description and in its step descriptions still render offline.

**size budget:** ~25 changed lines, 5 files.

**out of scope:**
- Do not change `MarkdownUtils.prependBaseUrlToImages`.
- Do not touch the other Context-holding ViewModels.

---

### 5. Make the retry-queue dialog load 10 preview rows and a COUNT instead of every pending row with its payload (roadmap 1+7)
**context:**
- `repository/RetryRepositoryImpl.kt:184-189` (`getRetryQueueSnapshot`) calls `getPending()`. That is `RetryDao.kt:24-30`, a `SELECT *` with no `LIMIT`, and each row carries the potentially large `RetryOperation.serializedPayload` column.
- The only consumer, `ui/settings/SettingsActivity.kt:130-142`, shows `pendingOps.take(10)` and `"... and ${pendingOps.size - 10} more"`.
- The existing `RetryDao.getActiveCount()` (`RetryDao.kt:32-33`) has a different `WHERE` clause (`pending OR in_progress`), so it cannot stand in for the "more" count.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt`: add two queries next to `getPending`.
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt`: the `RetryQueueDetails` data class (line 12) only.
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`: `getRetryQueueSnapshot` (lines 184-189) only.
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt`: the `retryQueueDetailsEvent` collector (lines 118-145) only.
- `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RetryDaoTest.kt`: add tests.
- Leave alone:
  - `RetryQueueWorker` and `RetryQueue`, which still need the full `getPending()`.
  - `SettingsViewModel.fetchRetryQueueDetails`.

**steps:**
1. In `RetryDao`, add two queries that use the exact `WHERE` of `getPending`:
   - `getPendingPreview(now: Long, limit: Int): List<RetryOperation>`, with the same `ORDER BY` plus `LIMIT :limit`.
   - `getPendingDueCount(now: Long): Long`, a `SELECT COUNT(*)`.
2. Add `val pendingDueCount: Long = 0` to `RetryQueueDetails`. The default keeps every existing constructor call compiling.
3. Change `getRetryQueueSnapshot` to read `retryDao.getPendingPreview(timeProvider.now(), 10)` and `retryDao.getPendingDueCount(timeProvider.now())`. Use one `now` value for both. Copy whatever `getPending()` in the same class passes, since it already uses `timeProvider.now()` (see `RetryRepositoryImplTest.kt:348`).
4. In `SettingsActivity.kt:140-141`:
   - Change the condition to `detailsData.pendingDueCount > pendingOps.size`.
   - Print `detailsData.pendingDueCount - pendingOps.size` as the "more" number.
   - Keep `pendingOps.take(10)`.
5. In `RetryDaoTest`, insert 12 due pending rows plus 1 not-yet-due row. Assert that the preview returns 10 rows in `getPending` order, and that the count returns 12.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "*RetryDaoTest" --tests "*RetryRepositoryImplTest"` passes.
- The full suite stays green.
- User-visible: Settings → retry-queue details lists at most 10 operations followed by "... and N more", with N matching the real number of due pending operations.

**size budget:** ~40 changed lines, 5 files.

**out of scope:**
- Do not change `getPendingCount()`/`getActiveCount()` semantics.
- No `AppDatabase` version bump; no schema change.

---

### 6. Normalise chat-search text once per chat list instead of once per keystroke (roadmap 7+3, moves 9)
**context:**
- `utils/ChatSearch.kt:33-45` (`fullConvoSearch`) and `:72-85` (`searchByTitle`) rebuild a normalised copy of every chat's whole conversation on each search. Each copy goes through `Utilities.normalizeText` (`utils/Utilities.kt:80`), which lowercases and, for non-ASCII text, runs NFD plus a regex.
- `ChatViewModel.kt:162` fires the search for each debounced keystroke from `ChatHistoryFragment.kt:87-91`, always over the same `allChats` (assigned once at `ChatViewModel.kt:139`).
- Separately, `ChatSearch.kt:25` hard-codes `dispatcher: CoroutineDispatcher = Dispatchers.Default`. That breaks the `DispatcherProvider` rule, and every caller already passes a dispatcher.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`allChats` at line 66, its assignment at line 139, `searchChats` at lines 148-164)
- `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt` (9 tests; calls at lines 23, 41, 59, 71, 87, …)
- Leave alone: `ChatHistoryFragment.kt`, which task 7 owns, and `ChatRepository`/`ChatSearchMode`.

**steps:**
1. In `ChatSearch`, add a public `class Index(chats: List<ChatHistory>)`.
   - It holds the chats plus three `by lazy` normalised views: the title per chat, which mirrors the current `searchByTitle` precompute, and the query list and the response list per chat, which mirror the `fullConvoSearch` precompute.
   - Because the views are lazy, a mode that is never searched costs nothing.
2. Change `search(...)` to take `index: ChatSearch.Index` instead of `chats: List<ChatHistory>`, and remove the `= Dispatchers.Default` default and its import. Then `searchByTitle` and `fullConvoSearch` read the precomputed views; only the query is normalised per call.
3. In `ChatViewModel`, add `private var searchIndex = ChatSearch.Index(emptyList())`. Rebuild it wherever `allChats` is assigned (line 139), and pass it at line 162.
4. Update `ChatSearchTest` to wrap each `chats` list in `ChatSearch.Index(...)`. Keep every existing assertion unchanged, including the ordering ones, because ranking must not change.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "*ChatSearchTest" --tests "*ChatViewModelTest"` passes with the assertions unchanged.
- The full suite stays green.
- User-visible: in chat history, title, question and response searches return the same results in the same order, and typing in the search bar feels no slower with a long history.

**size budget:** ~50 changed lines, 3 files.

**out of scope:**
- Do not change the ranking rules or the 300 ms debounce.
- Do not move search into the repository.

---

### 7. Add one shared search-debounce Flow operator and remove the two unopted `FlowPreview` warnings (roadmap 8, moves 10)
**context:** The build log (run 36857156308) reports OPT_IN_USAGE ("This declaration is in a preview state… `@kotlinx.coroutines.FlowPreview`") for:
- `ui/chat/ChatHistoryFragment.kt:89`, `.debounce(300)` after `textChanges().drop(1)`
- `ui/resources/CollectionsFragment.kt:97`, `.debounce(300L)`

Other screens copy the same `debounce(…).distinctUntilChanged()` chain and each opts in separately (`ui/teams/TeamFragment.kt:315`, `ui/voices/VoicesFragment.kt:305`, `ui/health/MyHealthFragment.kt:47`). A single helper next to the existing `EditText.textChanges()` (`utils/ViewExtensions.kt:19`) holds the opt-in in one place. Because the operator works on a plain `Flow`, the search-debounce logic can later move into ViewModels unchanged.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/utils/ViewExtensions.kt`: add one operator under `textChanges()`.
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` (lines 87-91 and the `debounce`/`distinctUntilChanged` imports, lines 16-17)
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt` (lines 96-99 and imports, lines 13-14)
- `app/src/test/java/org/ole/planet/myplanet/utils/ViewExtensionsTest.kt`: add a test.
- Leave alone:
  - `TeamFragment`, `VoicesFragment`, `MyHealthFragment`, `SubmissionsFragment` and `HealthViewModel`. They are already opted in; migrating them is a follow-up.
  - `ResourcesFragment` and `SurveyFragment`, which are owned by open PRs.

**steps:**
1. In `ViewExtensions.kt`, add `fun <T> Flow<T>.debounceDistinct(timeoutMillis: Long): Flow<T>`.
   - It returns `debounce(timeoutMillis).distinctUntilChanged()`.
   - Annotate the function itself with `@OptIn(FlowPreview::class)`.
   - Add the `kotlinx.coroutines.FlowPreview`, `flow.debounce` and `flow.distinctUntilChanged` imports.
2. `ChatHistoryFragment.kt:87-91`: replace `.debounce(300).distinctUntilChanged()` with `.debounceDistinct(300)`. Keep `.drop(1)` before it, and remove the two now-unused imports.
3. `CollectionsFragment.kt:96-98`: replace `.debounce(300L).distinctUntilChanged()` with `.debounceDistinct(300L)`, and remove the unused imports.
4. In `ViewExtensionsTest`, add a `runTest` case. Build a `flow { emit("a"); delay(100); emit("ab"); delay(400); emit("ab") }` and assert that `debounceDistinct(300).toList()` equals `listOf("ab")`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "*ViewExtensionsTest"` passes.
- The full suite stays green.
- `./gradlew assembleDefaultDebug --warning-mode all 2>&1 | grep -E "ChatHistoryFragment.kt:89|CollectionsFragment.kt:97"` prints nothing.
- User-visible: the chat-history search and the collections tag filter still update about 300 ms after the user stops typing.

**size budget:** ~25 changed lines, 4 files.

**out of scope:**
- Do not migrate the already-opted-in screens.
- Do not change the debounce durations.

---

### 8. Move the playback-speed picker off material-dialogs and remove a redundant safe call in `ResourceViewerFragment` (roadmap 6+8, moves 10)
**context:**
- `ui/viewer/ResourceViewerFragment.kt:236-247` (`showPlaybackSpeedDialog`) uses `com.afollestad.materialdialogs.MaterialDialog.Builder` (import at line 55). That is one of the five material-dialogs call sites keeping `android.enableJetifier=true` alive (`gradle.properties:25-26`). Every CI job warns that the flag is deprecated and will be removed in AGP 10.0.
- The same file already imports `androidx.appcompat.app.AlertDialog` (line 27). `ui/settings/SettingsActivity.kt:374` shows the house `setSingleChoiceItems` pattern.
- The compiler also flags `ResourceViewerFragment.kt:124`, `library.id?.let { … }`, as UNNECESSARY_SAFE_CALL, because `MyLibrary.id` is non-null.

**files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`: `showPlaybackSpeedDialog`, the recording-stop block (lines 121-126), and the line-55 import.
- Leave alone:
  - The material-dialogs uses in `ui/sync/*` and `ui/dashboard/DashboardElementActivity.kt`.
  - `gradle.properties` (Jetifier stays on until all five call sites are gone).
  - `ResourceViewerViewModel`.

**steps:**
1. Rewrite `showPlaybackSpeedDialog`:
   - Use `AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)` with `.setTitle(R.string.playback_speed)`.
   - Call `.setSingleChoiceItems(speedOptions, selectedIndex) { dialog, which -> … }`. The body does the same `savePlaybackSpeed` / `setPlaybackSpeed` / `invalidateOptionsMenu` calls and then `dialog.dismiss()`.
   - Finish with `.setPositiveButton(android.R.string.ok, null).show()`.
2. Delete `import com.afollestad.materialdialogs.MaterialDialog` (line 55).
3. At line 124, replace `library.id?.let { viewModel.saveTranslationAudioPath(it, outputFile) }` with a direct `viewModel.saveTranslationAudioPath(library.id, outputFile)`. Keep the `::library.isInitialized` guard.
4. Build both flavors.

**acceptance:**
- `./gradlew assembleDefaultDebug assembleLiteDebug` succeeds.
- `./gradlew testDefaultDebugUnitTest` stays green.
- `grep -n "materialdialogs" app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt` prints nothing.
- User-visible: open a video resource and tap the playback-speed menu item.
  - The picker shows 0.75x–2.0x with the current speed checked.
  - Choosing a speed applies it immediately and it persists.
  - Stopping a translation recording still saves the audio path.

**size budget:** ~20 changed lines, 1 file.

**out of scope:**
- Do not remove the material-dialogs dependency or the Jetifier flag.
- Do not touch the other four call sites.

---

### 9. Clear the compiler warnings in three unit-test files (roadmap 8)
**context:** Both test shards of run 36847822998 print these warnings for test sources:

| Location | Code | Warning |
|---|---|---|
| `repository/TeamChatBadgeIntegrationTest.kt:43` | `kotlinx.coroutines.test.UnconfinedTestDispatcher()` without an opt-in | OPT_IN_USAGE, ExperimentalCoroutinesApi |
| `ui/sync/ServerAddressAdapterTest.kt:54` and `:65` | `adapter.onCreateViewHolder(parent, 0) as ServerAddressAdapter.ViewHolder` | USELESS_CAST; `onCreateViewHolder` already returns `ViewHolder`, `ServerAddressAdapter.kt:66` |
| `utils/FileUtilsTest.kt:456` | `intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)` | DEPRECATION |
| `utils/FileUtilsTest.kt:493` | `shadowOf(MimeTypeMap.getSingleton())` | DEPRECATION |

Warning noise in the test log hides new, real warnings.

**files:** all under `app/src/test/java/org/ole/planet/myplanet/`:
- `repository/TeamChatBadgeIntegrationTest.kt`
- `ui/sync/ServerAddressAdapterTest.kt`
- `utils/FileUtilsTest.kt`

Leave alone: the production classes these tests cover, and `LifeRepositoryImplTest.kt`, which has the same opt-in warning but is owned by an open PR.

**steps:**
1. `TeamChatBadgeIntegrationTest`:
   - Add `@OptIn(ExperimentalCoroutinesApi::class)` on the class (line 23), with the import.
   - Replace the fully-qualified `kotlinx.coroutines.test.UnconfinedTestDispatcher()` at line 43 with an imported `UnconfinedTestDispatcher()`.
2. `ServerAddressAdapterTest.kt:54` and `:65`: delete the `as ServerAddressAdapter.ViewHolder` casts.
3. `FileUtilsTest.kt:456`: replace the call with `IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)`. `androidx.core.content.IntentCompat` comes from `core-ktx` 1.19.1, which is already a dependency.
4. `FileUtilsTest.kt:493`: delete the `shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png", "image/png")` line and run the test.
   - If `getMimeType_returnsCorrectMimeType` still passes (Robolectric 4.17 ships real MIME data), also remove the `MimeTypeMap` and `shadowOf` imports if nothing else uses them.
   - If it fails, restore the line, put a statement-level `@Suppress("DEPRECATION")` on it, and keep the imports.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "*TeamChatBadgeIntegrationTest" --tests "*ServerAddressAdapterTest" --tests "*FileUtilsTest"` passes.
- The full suite stays green.
- `./gradlew testDefaultDebugUnitTest --rerun-tasks --warning-mode all 2>&1 | grep -E "TeamChatBadgeIntegrationTest.kt:43|ServerAddressAdapterTest.kt:(54|65)|FileUtilsTest.kt:456"` prints nothing.
- No user-visible change.

**size budget:** ~10 changed lines, 3 files.

**out of scope:**
- No assertion changes.
- No test-speed work.
- Do not touch `LifeRepositoryImplTest`.

---

### 10. Make the CI slow-test summary separate Robolectric warm-up from real class cost (roadmap 8, CI)
**context:**
- Both shards of test run 36847822998 trip the warning `⚠️ Slow tests: shard 1/2 summed 599s, over the 400s threshold` (shard 2: 534s), which tells the reader to "speed up the slowest classes below".
- The "15 slowest test classes" table ranks classes by raw total, and that total is inflated by the one test that boots the Robolectric sandbox in its fork:
  - `CourseRatingUtilsTest`: 14.8s total, median 0.01s/test. Its single warm-up test took 14.8s.
  - `AnswerDaoTest`: 18.0s total; one warm-up took 14.2s.
  - `MeetupDaoTest`: 18.6s total; one warm-up took 14.3s.
- `.github/scripts/test_timing_summary.py` already detects warm-ups (lines 93-95: `case_elapsed > 5 * class_median` with ≥3 tests), but it only shows the flag per individual test. Agents following the warning therefore chase classes that are not actually slow.

**files:**
- `.github/scripts/test_timing_summary.py` (the `main()` aggregation at lines 85-104 and the class table at lines 121-127)
- Leave alone:
  - `.github/workflows/test.yml`, which invokes the script at lines 97-98 and is owned by open PRs.
  - The CLI flags (`--shard`, `--warn-over`) and the other two tables.

**steps:**
1. In the per-class loop (lines 85-95), sum the elapsed time of the cases flagged as warm-up into `class_warmup` and add it to the `classes` tuple. Also keep a running `total_warmup`.
2. Sort `classes` by `elapsed - class_warmup` (descending, name as the tie-breaker), so the table ranks real cost.
3. Add a `Warm-up s` column to the "slowest test classes" table, between `Seconds` and `% of total`, and rename the header from `Seconds` to `Seconds (excl. warm-up)`. Leave `% of total` computed from the raw elapsed time.
4. Under the "N tests in M classes, …s summed" line, print one extra line: `{total_warmup:.1f}s of that is likely Robolectric sandbox warm-up.` Keep the `--warn-over` check on the raw `total`, so the existing threshold in `test.yml` keeps its meaning.
5. Update the module docstring (lines 1-18) to mention the warm-up column.

**acceptance:**
- `python3 -m py_compile .github/scripts/test_timing_summary.py` succeeds.
- After `./gradlew testDefaultDebugUnitTest -PtestShardTotal=2 -PtestShardIndex=0 -ProbolectricOffline=true`, `python3 .github/scripts/test_timing_summary.py app/build/test-results/testDefaultDebugUnitTest --shard 1/2 --warn-over 400` prints:
  - the new column,
  - the warm-up total line,
  - a class table where a class like `CourseRatingUtilsTest` (median 0.01s) no longer sits near the top.
- An empty results directory still prints `No test result XML found…` and exits 0.
- `./gradlew testDefaultDebugUnitTest` stays green; no Kotlin changes.

**size budget:** ~20 changed lines, 1 file.

**out of scope:**
- Do not change `test.yml`, the 400s threshold or the shard count.
- Do not add new CLI flags.

# Refactor Tasks – Round 1 (Repository Boundaries & Data-Layer Hygiene)

> 10 granular, low-conflict PRs targeting repository boundary reinforcement,
> cross-feature data-leak tightening, and misplaced data-function extraction.

---

### 1. Extract DictionaryActivity Realm operations into a new DictionaryRepository

DictionaryActivity performs four direct Realm operations (count, isEmpty check, bulk insert from JSON, search-by-word) that bypass the repository layer entirely. Creating a `DictionaryRepository` / `DictionaryRepositoryImpl` pair and wiring it through Hilt keeps this feature aligned with the existing 20-repository convention and removes `io.realm` imports from UI code.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L70-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L98"}
:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L103-L126"}

:::task-stub{title="Extract DictionaryRepository from DictionaryActivity"}
1. Create `repository/DictionaryRepository.kt` interface with `isDictionaryEmpty()`, `insertFromJson(JsonArray)`, `count()`, and `searchWord(String)` methods
2. Create `repository/DictionaryRepositoryImpl.kt` extending `RealmRepository`, moving the four Realm operations from DictionaryActivity lines 54-56, 70-88, 95-98, 103-125
3. Add `@Binds` binding in `di/RepositoryModule.kt`
4. Inject `DictionaryRepository` into `DictionaryActivity` via `@Inject lateinit var` and replace all direct Realm calls
5. Remove `io.realm` imports from `DictionaryActivity`
:::

---

### 2. Move AddExaminationActivity Realm transactions into HealthRepository

AddExaminationActivity holds a raw `lateinit var mRealm: Realm` field and manually manages `beginTransaction` / `commitTransaction` for health profile creation and examination saving. These operations belong in the existing `HealthRepository`, which already covers other health-related queries.

:codex-file-citation[codex-file-citation]{line_range_start=261 line_range_end=281 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L261-L281"}
:codex-file-citation[codex-file-citation]{line_range_start=283 line_range_end=349 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L283-L349"}

:::task-stub{title="Move AddExaminationActivity Realm writes into HealthRepository"}
1. Add `suspend fun initHealth(userKey: String): RealmMyHealth` and `suspend fun saveExamination(examination: RealmHealthExamination, pojo: RealmMyHealthPojo)` to `HealthRepository` interface
2. Implement both methods in `HealthRepositoryImpl` using `RealmRepository.save()` and `executeTransaction`, moving logic from lines 261-281 and 283-349
3. Inject `HealthRepository` into `AddExaminationActivity` and replace `mRealm` field with repository calls
4. Remove the raw `lateinit var mRealm: Realm` field and its manual lifecycle management in `onDestroy()`
:::

---

### 3. Extract AchievementFragment Realm queries into a new AchievementRepository

AchievementFragment and EditAchievementFragment both perform direct `realm.where(RealmAchievement::class.java)` queries, `realm.copyFromRealm()` calls, and `realm.createObject()` writes. None of the existing 20 repositories cover achievements. A dedicated `AchievementRepository` removes Realm coupling from both fragments.

:codex-file-citation[codex-file-citation]{line_range_start=170 line_range_end=204 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt#L170-L204"}
:codex-file-citation[codex-file-citation]{line_range_start=93 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L93-L107"}
:codex-file-citation[codex-file-citation]{line_range_start=305 line_range_end=315 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L305-L315"}

:::task-stub{title="Create AchievementRepository for achievement CRUD"}
1. Create `repository/AchievementRepository.kt` interface with `getAchievement(userId: String, planetCode: String)`, `getOrCreateAchievement(id: String)`, `updateAchievement(id, header, goals, purpose, ...)`, and `getResourcesByIds(ids: Array<String>)` methods
2. Create `repository/AchievementRepositoryImpl.kt` extending `RealmRepository`, moving queries from AchievementFragment lines 170-204 and EditAchievementFragment lines 93-107, 305-315
3. Add `@Binds` binding in `di/RepositoryModule.kt`
4. Inject into both `AchievementFragment` and `EditAchievementFragment`, replacing all direct Realm access
:::

---

### 4. Replace RealmList parameter leak in VoicesRepository interface with List

VoicesRepository exposes `io.realm.RealmList<String>` in four method signatures (`createNews`, `createTeamNews`, `postReply`, `editPost`). This is the only repository interface that leaks a Realm collection type, forcing callers to import `io.realm`. Changing the parameter type to `List<String>?` and converting inside the Impl keeps the interface persistence-agnostic.

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt#L15-L16"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt#L28-L29"}

:::task-stub{title="Remove io.realm.RealmList from VoicesRepository interface"}
1. In `VoicesRepository.kt`, change the four `io.realm.RealmList<String>?` parameters (lines 15, 16, 28, 29) to `List<String>?`
2. In `VoicesRepositoryImpl.kt`, convert `List<String>?` to `RealmList<String>` at the start of each method body using `RealmList<String>().apply { addAll(list) }`
3. Update all callers (VoicesFragment, ReplyActivity, NewsViewModel) to pass plain `List<String>` instead of `RealmList`
4. Remove the `io.realm` import from `VoicesRepository.kt`
:::

---

### 5. Move RetryQueue Realm operations into RealmRepository base methods

RetryQueue contains 9 separate `databaseService.executeTransactionAsync` / `withRealmAsync` blocks performing raw `realm.where(RealmRetryOperation::class.java)` queries. The class is already a `@Singleton` service but duplicates patterns that `RealmRepository` already provides (`findByField`, `save`, `update`, `delete`). Extending `RealmRepository` or extracting a thin `RetryOperationRepository` removes this duplication.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L49-L57"}
:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=131 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L123-L131"}
:codex-file-citation[codex-file-citation]{line_range_start=145 line_range_end=164 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L145-L164"}
:codex-file-citation[codex-file-citation]{line_range_start=172 line_range_end=181 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L172-L181"}

:::task-stub{title="Refactor RetryQueue to use RealmRepository base methods"}
1. Create `repository/RetryOperationRepository.kt` interface with methods: `findPending(itemId, uploadType)`, `markInProgress(id)`, `markCompleted(id)`, `markFailed(id, error, httpCode)`, `resetAllPending()`, `cleanupCompleted()`, `recoverStuck()`
2. Create `repository/RetryOperationRepositoryImpl.kt` extending `RealmRepository`, using `findByField`, `update`, and `queryList` where applicable
3. Add `@Binds` binding in `di/RepositoryModule.kt`
4. Inject `RetryOperationRepository` into `RetryQueue` and replace all 9 raw Realm blocks
5. Keep `RetryQueue` as the orchestration/business-logic layer, delegating persistence to the repository
:::

---

### 6. Move UploadManager team and news Realm queries into existing repositories

UploadManager directly queries `RealmMyTeam`, `RealmNews`, and `RealmOfflineActivity` using raw `realm.where()` calls and `createObject()` for team-resource linking. These operations belong in `TeamsRepository`, `VoicesRepository`, and `ActivitiesRepository` respectively, which already exist but are bypassed here.

:codex-file-citation[codex-file-citation]{line_range_start=444 line_range_end=455 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L444-L455"}
:codex-file-citation[codex-file-citation]{line_range_start=471 line_range_end=478 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L471-L478"}
:codex-file-citation[codex-file-citation]{line_range_start=624 line_range_end=637 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L624-L637"}

:::task-stub{title="Move UploadManager team/news Realm queries into repositories"}
1. Add `suspend fun getUpdatedTeams(): List<TeamData>` and `suspend fun markTeamSynced(teamId: String, rev: String)` to `TeamsRepository` interface
2. Add `suspend fun getUploadableNews(): List<NewsUploadData>` and `suspend fun clearNewsImageUrls(newsId: String)` to `VoicesRepository` interface
3. Implement both sets of methods in their respective `*RepositoryImpl` classes, moving Realm queries from UploadManager lines 444-455, 471-478, 624-637
4. Inject `TeamsRepository` and `VoicesRepository` into `UploadManager` and replace direct Realm access with repository calls
:::

---

### 7. Fix ChallengePrompter dispatching repository calls on Dispatchers.Main

ChallengePrompter launches a coroutine on `Dispatchers.Main` (line 41) and then immediately calls `progressRepository.fetchCourseData()`, `voicesRepository.getCommunityVoiceDates()`, and `coursesRepository.getCourseTitleById()` — all suspend functions performing Realm I/O — on the main thread. The dispatcher should be `Dispatchers.Default` or omitted (letting the repository handle the IO switch), with only UI work returning to Main.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=74 path=app/src/main/java/org/ole/planet/myplanet/services/ChallengePrompter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/ChallengePrompter.kt#L41-L74"}

:::task-stub{title="Fix ChallengePrompter to not run repository I/O on Main dispatcher"}
1. In `ChallengePrompter.evaluateChallengeDialog()`, remove the explicit `Dispatchers.Main` from the `launch(Dispatchers.Main)` call on line 41 (lifecycleScope already defaults to Main for UI results)
2. Verify that `progressRepository.fetchCourseData()`, `voicesRepository.getCommunityVoiceDates()`, and `coursesRepository.getCourseTitleById()` internally use `withContext(Dispatchers.IO)` or `withRealmAsync` for their Realm operations
3. If any of those repository methods lack an IO dispatcher switch, add `withContext(Dispatchers.IO)` inside the repository implementation
:::

---

### 8. Fix ProcessUserDataActivity dispatching uploads on Dispatchers.Main

ProcessUserDataActivity launches all upload operations (`uploadAchievement`, `uploadNews`, `uploadResource`, etc.) inside `applicationScope.launch(Dispatchers.Main)` at line 221. Upload operations perform network I/O and Realm writes, which should not block the main thread. The outer scope should use `Dispatchers.IO` or `Dispatchers.Default`, with only the toast/progress-dismiss switching to Main.

:codex-file-citation[codex-file-citation]{line_range_start=213 line_range_end=216 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L213-L216"}
:codex-file-citation[codex-file-citation]{line_range_start=221 line_range_end=294 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L221-L294"}

:::task-stub{title="Fix ProcessUserDataActivity upload dispatcher to Dispatchers.IO"}
1. Change `applicationScope.launch(Dispatchers.Main)` on line 221 to `applicationScope.launch(Dispatchers.IO)`
2. Change `applicationScope.launch(Dispatchers.Main)` on line 213 to `applicationScope.launch(Dispatchers.IO)`
3. Wrap the UI-touching callbacks (`customProgressDialog.dismiss()`, `Toast.makeText`) at lines 228-234 in `withContext(Dispatchers.Main)` blocks
4. Verify each `uploadManager.*` method does not assume it is on Main
:::

---

### 9. Wrap SurveyFragment and CoursesProgressFragment flow collectors in repeatOnLifecycle

SurveyFragment collects six StateFlows inside a bare `lifecycleScope.launch` without `repeatOnLifecycle`. CoursesProgressFragment has the same pattern with `progressViewModel.courseData.collect`. Without `repeatOnLifecycle(STARTED)`, these collectors remain subscribed when the fragment is in the back stack, wasting resources, and do not re-collect when the fragment returns to foreground.

:codex-file-citation[codex-file-citation]{line_range_start=172 line_range_end=215 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L172-L215"}
:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt#L38-L46"}

:::task-stub{title="Add repeatOnLifecycle to SurveyFragment and CoursesProgressFragment"}
1. In `SurveyFragment.setupObservers()`, wrap the outer `lifecycleScope.launch` body in `viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { ... }` around lines 173-215
2. In `CoursesProgressFragment.observeCourseData()`, wrap the `lifecycleScope.launch` body in `viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { ... }` around lines 39-46
3. Add `import androidx.lifecycle.repeatOnLifecycle` to both files
4. Verify both fragments import `Lifecycle` from `androidx.lifecycle`
:::

---

### 10. Migrate InlineResourceAdapter from RecyclerView.Adapter to ListAdapter with DiffUtils.itemCallback

InlineResourceAdapter is the only remaining adapter that extends raw `RecyclerView.Adapter` and uses `notifyDataSetChanged()`. Every other adapter in the codebase (28+) already uses `ListAdapter` with the project's `DiffUtils.itemCallback` helper. Migrating this adapter aligns it with the codebase convention and enables efficient incremental updates.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L27-L30"}
:codex-file-citation[codex-file-citation]{line_range_start=203 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L203-L208"}

:::task-stub{title="Migrate InlineResourceAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change `InlineResourceAdapter` to extend `ListAdapter<RealmMyLibrary, InlineResourceAdapter.ViewHolder>(DIFF_CALLBACK)` instead of `RecyclerView.Adapter<...>`
2. Add a companion `DIFF_CALLBACK` using `DiffUtils.itemCallback(areItemsTheSame = { old, new -> old.id == new.id }, areContentsTheSame = { old, new -> old.id == new.id && old._rev == new._rev })`
3. Replace `resources[position]` in `onBindViewHolder` with `getItem(position)`
4. Remove the `resources` field, `getItemCount()` override, and the `updateResources()` method
5. Update callers to use `adapter.submitList(newResources)` instead of `adapter.updateResources()`
:::

---

## Testing Guidance

For all 10 tasks above, verify the following after each change:

- App builds successfully with `./gradlew assembleDefaultDebug`
- No new lint warnings introduced around `io.realm` imports in UI/interface files
- Repository method signatures do not expose Realm types
- Affected screens function correctly (dictionary search, health exam save, achievement display, survey list, course progress, news/voice posting, upload flow)

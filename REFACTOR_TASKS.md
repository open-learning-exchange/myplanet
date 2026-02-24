### Move DictionaryActivity Realm logic into a new DictionaryRepository

DictionaryActivity performs all CRUD operations directly against Realm: count checks, bulk inserts via `executeTransactionAsync`, and search queries with `findFirst`. These belong behind a repository interface so the UI only calls named methods like `isDictionaryLoaded()`, `importDictionary(json)`, and `searchWord(term)`.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=71 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L71-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L103-L125"}

:::task-stub{title="Extract DictionaryRepository from DictionaryActivity"}
1. Create `DictionaryRepository` interface with `isDictionaryEmpty()`, `importDictionary(jsonArray)`, and `searchWord(term): RealmDictionary?`
2. Create `DictionaryRepositoryImpl` extending `RealmRepository`, move Realm queries from DictionaryActivity lines 54-56, 71-88, and 103-125 into the new impl
3. Bind the new repository in `RepositoryModule`
4. Inject `DictionaryRepository` into `DictionaryActivity` and replace all `databaseService.withRealm` / `realm.where(RealmDictionary::class.java)` calls with repository method calls
5. Remove direct `io.realm` imports from `DictionaryActivity`
:::

### Move AddExaminationActivity transaction logic into HealthRepository

AddExaminationActivity manages raw Realm transactions: `beginTransaction`, `commitTransaction`, `cancelTransaction`, and `copyToRealmOrUpdate` across ~90 lines. This health-record persistence logic should move into `HealthRepository` so the Activity only calls a single `saveExamination(...)` suspend function.

:codex-file-citation[codex-file-citation]{line_range_start=94 line_range_end=94 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L94-L94"}
:codex-file-citation[codex-file-citation]{line_range_start=261 line_range_end=281 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L261-L281"}
:codex-file-citation[codex-file-citation]{line_range_start=283 line_range_end=349 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L283-L349"}

:::task-stub{title="Move health-record persistence from AddExaminationActivity into HealthRepository"}
1. Add `saveExamination(examination: RealmHealthExamination, health: RealmMyHealth, pojo: RealmUser)` to `HealthRepository` interface
2. Implement the method in `HealthRepositoryImpl` using `databaseService.executeTransactionAsync`, moving the `beginTransaction`/`commitTransaction`/`copyToRealmOrUpdate` block from lines 283-349
3. Move `initHealth()` helper (lines 261-281) into `HealthRepositoryImpl` as a private function
4. In `AddExaminationActivity`, replace the `mRealm` field and manual transactions with a call to the new repository method
5. Remove `createManagedRealmInstance()` call at line 94 and the `mRealm` property
:::

### Move SyncActivity.clearRealmDb into a data-layer service

`SyncActivity.Companion.clearRealmDb()` executes `realm.executeTransaction { it.deleteAll() }` directly from a UI-layer companion object. This destructive data operation is then imported by `SettingsActivity`. It should live in a data-layer class so no UI code owns database-wipe logic.

:codex-file-citation[codex-file-citation]{line_range_start=825 line_range_end=832 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L825-L832"}

:::task-stub{title="Relocate clearRealmDb from SyncActivity companion to DatabaseService or a dedicated ResetRepository"}
1. Add a `suspend fun clearAll()` method to `DatabaseService` (or create a small `ResetRepository` interface + impl)
2. Move the `deleteAll()` transaction from `SyncActivity.clearRealmDb()` (lines 825-832) into the new method
3. Update `SyncActivity` and `SettingsActivity` to call the injected service/repository instead of the companion function
4. Delete `SyncActivity.clearRealmDb()` companion method
:::

### Migrate RealmMyCourse companion object data functions into CoursesRepository

`RealmMyCourse.companion` contains `insertMyCourses()`, `getMyByUserId()`, `saveConcatenatedLinksToPrefs()`, and `serialize()` -- over 150 lines of Realm queries, SharedPreferences access, and JSON serialization living inside the model. These should move one-by-one into `CoursesRepositoryImpl`.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L73-L97"}
:codex-file-citation[codex-file-citation]{line_range_start=175 line_range_end=181 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L175-L181"}

:::task-stub{title="Move RealmMyCourse.getMyByUserId into CoursesRepository"}
1. Add `suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse>` to `CoursesRepository` interface
2. Implement in `CoursesRepositoryImpl` using `databaseService.withRealmAsync`, porting the query from `RealmMyCourse.getMyByUserId()` (lines 175-181)
3. Update all callers of `RealmMyCourse.getMyByUserId(mRealm, settings)` to use the injected repository method instead
4. Deprecate `RealmMyCourse.getMyByUserId` with `@Deprecated("Use CoursesRepository.getCoursesByUserId")`
:::

### Migrate RealmSubmission companion query helpers into SubmissionsRepository

`RealmSubmission.companion` has `getExamMap()` and `createSubmission()` that execute `mRealm.where(RealmStepExam::class.java)` queries and `mRealm.createObject()` calls. These cross the model-to-data boundary and should be repository methods.

:codex-file-citation[codex-file-citation]{line_range_start=202 line_range_end=209 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L202-L209"}
:codex-file-citation[codex-file-citation]{line_range_start=211 line_range_end=219 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L211-L219"}

:::task-stub{title="Move RealmSubmission.createSubmission and getExamMap into SubmissionsRepository"}
1. Add `suspend fun getOrCreateSubmission(existing: RealmSubmission?): RealmSubmission` to `SubmissionsRepository` interface
2. Add `suspend fun getExamMapForSubmissions(submissions: List<RealmSubmission>): Map<String?, RealmStepExam>` to `SubmissionsRepository` interface
3. Implement both in `SubmissionsRepositoryImpl` using `executeTransaction` and `withRealmAsync`, porting from companion lines 202-209 and 211-219
4. Update callers to use the injected repository
5. Deprecate the companion methods
:::

### Migrate RealmMyLibrary.removeDeletedResource into ResourcesRepository

`RealmMyLibrary.companion.removeDeletedResource()` (already marked `@Deprecated`) performs a batch Realm delete query. The deprecation message says to use `ResourcesRepository.removeDeletedResources` but callers may still reference the companion. Finish the migration.

:codex-file-citation[codex-file-citation]{line_range_start=198 line_range_end=214 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L198-L214"}

:::task-stub{title="Remove deprecated RealmMyLibrary.removeDeletedResource and update remaining callers"}
1. Search for all call sites of `RealmMyLibrary.removeDeletedResource`
2. Replace each call with the existing `ResourcesRepository.removeDeletedResources` method
3. Delete the deprecated companion function at lines 198-233 in `RealmMyLibrary.kt`
4. Remove any now-unused `getIds()` private helper (lines 193-196) if it was only used by the deleted function
:::

### Convert InlineResourceAdapter from RecyclerView.Adapter to ListAdapter with DiffUtils.itemCallback

`InlineResourceAdapter` is the only remaining `RecyclerView.Adapter` that calls `notifyDataSetChanged()`. The codebase already has `DiffUtils.itemCallback` and 30 other adapters using `ListAdapter`. This is a one-file, mechanical conversion.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L27-L30"}
:codex-file-citation[codex-file-citation]{line_range_start=205 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L205-L208"}

:::task-stub{title="Convert InlineResourceAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change class declaration from `RecyclerView.Adapter<...>` to `ListAdapter<RealmMyLibrary, InlineResourceAdapter.ViewHolder>(diffCallback)` using `DiffUtils.itemCallback`
2. Define `diffCallback` comparing `resourceId` for item identity and full equality for contents
3. Replace `resources` field access with `getItem(position)` in `onBindViewHolder`
4. Replace `getItemCount()` override (line 203) -- ListAdapter handles this
5. Replace `updateResources()` (lines 205-208) body with `submitList(newResources)` and remove `notifyDataSetChanged()`
:::

### Extend NotificationsRepositoryImpl from RealmRepository base class

Only 2 of 19 repository impls (`LifeRepositoryImpl`, `ProgressRepositoryImpl`) extend `RealmRepository`. `NotificationsRepositoryImpl` already extends it but most others duplicate Realm lifecycle management. Start migrating one more repository to prove the pattern and reduce boilerplate.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=19 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L17-L19"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L1-L8"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L1-L5"}

:::task-stub{title="Migrate CoursesRepositoryImpl to extend RealmRepository"}
1. Change `CoursesRepositoryImpl` constructor to pass `databaseService` to `RealmRepository(databaseService)` and extend `RealmRepository`
2. Replace inline `databaseService.withRealmAsync { realm -> realm.where(...)... }` calls with `queryList(...)`, `findByField(...)`, `count(...)` from `RealmRepository`
3. Replace inline `databaseService.executeTransactionAsync { ... }` calls with `executeTransaction { ... }` from `RealmRepository`
4. Verify no public API changes to `CoursesRepository` interface
5. Build and confirm no regressions
:::

### Move UploadManager team-upload Realm queries into TeamsRepository

`UploadManager.uploadTeams()` queries `RealmMyTeam` with `equalTo("updated", true)`, serializes via `RealmMyTeam.serialize()`, then updates `_rev` after upload -- all inline. These data operations belong in `TeamsRepository` behind named methods.

:codex-file-citation[codex-file-citation]{line_range_start=445 line_range_end=456 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L445-L456"}

:::task-stub{title="Extract team upload data access from UploadManager into TeamsRepository"}
1. Add `suspend fun getTeamsForUpload(): List<TeamUploadData>` to `TeamsRepository` interface (define a simple data class with teamId, teamName, coursesCount, serializedJson)
2. Add `suspend fun markTeamUploaded(teamId: String, rev: String)` to `TeamsRepository` interface
3. Implement both in `TeamsRepositoryImpl` -- move the `realm.where(RealmMyTeam::class.java).equalTo("updated", true)` query and `RealmMyTeam.serialize()` call from UploadManager lines 445-456
4. Update `UploadManager.uploadTeams()` to call the repository methods instead of querying Realm directly
5. Remove direct `RealmMyTeam` Realm imports from `UploadManager` if no longer needed
:::

### Fix unmanaged CoroutineScope in UserRepositoryImpl

`UserRepositoryImpl` creates a bare `CoroutineScope(Dispatchers.IO).launch { ... }` at line 323. This scope is never cancelled and can outlive the caller. The project already provides `@ApplicationScope` via DI; the repository should use that instead.

:codex-file-citation[codex-file-citation]{line_range_start=323 line_range_end=325 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L323-L325"}
:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt#L45-L45"}

:::task-stub{title="Replace bare CoroutineScope in UserRepositoryImpl with injected ApplicationScope"}
1. Add `@ApplicationScope private val appScope: CoroutineScope` to `UserRepositoryImpl` constructor injection
2. Replace `kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { uploadToShelf(obj) }` at line 323 with `appScope.launch { uploadToShelf(obj) }`
3. Remove the direct `kotlinx.coroutines.CoroutineScope` import
4. Verify `ServiceModule` already provides `@ApplicationScope` (it does at line 38-40)
:::

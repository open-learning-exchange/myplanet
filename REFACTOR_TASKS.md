# Refactor Task Plan — Round 1 (10 PR-sized items)

> Focus: reinforce repository boundaries, eliminate cross-feature data leaks,
> move data operations one-by-one from service/UI into repositories.
> Constraints: no use-cases, no Jetpack Compose, no unused code, each task ≤ 1 small PR.

---

### RetryQueue: convert service-level Realm CRUD to a proper RetryRepository

RetryQueue lives in the service layer yet it owns every CRUD operation for `RealmRetryOperation` — insert, update status, mark-completed, cleanup, and reset. All of those belong in a repository extending `RealmRepository`. Extracting them removes the only Realm dependency from this service class and opens the door to testing the queue logic in isolation.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L49-L86"}

:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=180 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L123-L180"}

:::task-stub{title="Extract RetryOperation Realm CRUD from RetryQueue into RetryOperationRepository"}
1. Create `RetryOperationRepository` interface with methods: `enqueue`, `updateAttempt`, `markInProgress`, `markCompleted`, `markFailed`, `getPending`, `cleanup`, `resetAllPending`.
2. Create `RetryOperationRepositoryImpl` extending `RealmRepository`; copy each `databaseService.executeTransactionAsync` block from `RetryQueue` into the corresponding method.
3. Inject `RetryOperationRepository` into `RetryQueue` via constructor; replace all direct `databaseService` calls with repository calls.
4. Bind `RetryOperationRepository → RetryOperationRepositoryImpl` in `RepositoryModule`.
:::

---

### UploadToShelfService: delegate RealmUser write-backs to UserRepository

`UploadToShelfService` directly calls `dbService.executeTransactionAsync` to write `_id`, `_rev`, `isUpdated`, `key`, and `iv` back to `RealmUser` after successful API responses. These mutations belong in `UserRepository`, which already owns the `RealmUser` data contract.

:codex-file-citation[codex-file-citation]{line_range_start=148 line_range_end=155 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L148-L155"}

:codex-file-citation[codex-file-citation]{line_range_start=206 line_range_end=211 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L206-L211"}

:codex-file-citation[codex-file-citation]{line_range_start=277 line_range_end=284 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L277-L284"}

:::task-stub{title="Move RealmUser post-upload write-backs from UploadToShelfService into UserRepository"}
1. Add `suspend fun markUserUploaded(userId: String, id: String, rev: String)` to `UserRepository` interface and implement in `UserRepositoryImpl` using `RealmRepository.update`.
2. Add `suspend fun markUserKeyIvSaved(userId: String, key: String, iv: String?)` to `UserRepository`; implement similarly.
3. Add `suspend fun markUserRevUpdated(userId: String, rev: String?)` to `UserRepository`; implement.
4. In `UploadToShelfService`, replace the three `dbService.executeTransactionAsync` blocks that touch `RealmUser` with calls to the new `UserRepository` methods.
:::

---

### UploadToShelfService: delegate RealmHealthExamination CRUD to HealthRepository

`uploadHealth()` and `uploadSingleUserHealth()` in `UploadToShelfService` query `RealmHealthExamination` directly and write `_rev` / `isUpdated` back after a successful upload. `HealthRepository` already manages examination persistence; it should own these bulk-upload helpers too.

:codex-file-citation[codex-file-citation]{line_range_start=287 line_range_end=326 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L287-L326"}

:codex-file-citation[codex-file-citation]{line_range_start=328 line_range_end=384 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L328-L384"}

:::task-stub{title="Move RealmHealthExamination upload queries from UploadToShelfService into HealthRepository"}
1. Add `suspend fun getUnuploadedExaminations(userId: String? = null): List<RealmHealthExamination>` to `HealthRepository`; implement in `HealthRepositoryImpl` with a `queryList` call filtered by `isUpdated == true`.
2. Add `suspend fun markExaminationsUploaded(revMap: Map<String, String?>)` to `HealthRepository`; implement using `RealmRepository.executeTransaction`.
3. In `UploadToShelfService.uploadHealth()`, replace the `dbService.withRealm` query block and the subsequent `dbService.executeTransactionAsync` block with calls to the two new `HealthRepository` methods.
4. Apply the same replacement in `uploadSingleUserHealth()`.
:::

---

### UploadManager: move RealmMyLibrary upload-query and write-back to ResourcesRepository

`UploadManager.uploadResource()` queries `RealmMyLibrary` for records where `_rev` is null directly via `databaseService.withRealm`, then writes `_rev` / `_id` back inside its own `executeTransaction` block. `ResourcesRepository` owns the `RealmMyLibrary` contract and should expose these operations.

:codex-file-citation[codex-file-citation]{line_range_start=301 line_range_end=318 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L301-L318"}

:codex-file-citation[codex-file-citation]{line_range_start=349 line_range_end=380 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L349-L380"}

:::task-stub{title="Expose getUnuploadedResources / markResourceUploaded on ResourcesRepository and use them in UploadManager"}
1. Add `suspend fun getUnuploadedResources(user: RealmUser?): List<ResourceUploadData>` to `ResourcesRepository` (where `ResourceUploadData` is a plain data class holding the fields needed for the upload); implement in `ResourcesRepositoryImpl` using `RealmRepository.queryList`.
2. Add `suspend fun markResourceUploaded(libraryId: String, id: String, rev: String)` to `ResourcesRepository`; implement using `RealmRepository.update`.
3. In `UploadManager.uploadResource()`, replace the `databaseService.withRealm` query block with `resourcesRepository.getUnuploadedResources(user)` and the inner `executeTransaction` with `markResourceUploaded(...)` calls.
:::

---

### PersonalsFragment: introduce PersonalsViewModel

`PersonalsFragment` injects `PersonalsRepository` and `UserSessionManager` directly and calls them inside `lifecycleScope.launch` blocks without a ViewModel. State would be lost on rotation and the fragment bears responsibility for coroutine lifecycle management. A thin `PersonalsViewModel` removes that coupling.

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=76 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L36-L76"}

:::task-stub{title="Add PersonalsViewModel to mediate PersonalsRepository access in PersonalsFragment"}
1. Create `PersonalsViewModel` annotated with `@HiltViewModel`, injecting `PersonalsRepository` and `UserSessionManager`.
2. Expose a `StateFlow<List<RealmMyPersonal>>` (or `Flow`) produced by collecting `personalsRepository.getPersonalResources(userId)` inside `viewModelScope`.
3. In `PersonalsFragment`, remove `@Inject lateinit var personalsRepository` and `@Inject lateinit var userSessionManager`; obtain the ViewModel via `by viewModels()`.
4. Collect the `StateFlow` from the ViewModel using `repeatOnLifecycle(STARTED)` and call `personalAdapter?.submitList(...)`.
:::

---

### LifeFragment: introduce LifeViewModel and remove hardcoded Dispatchers.IO

`LifeFragment` directly calls `lifeRepository` from `lifecycleScope` blocks (no ViewModel), and two callbacks inside `getAdapter()` hard-code `withContext(Dispatchers.IO)` in the UI layer. A `LifeViewModel` should own the data calls and threading.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L48-L96"}

:::task-stub{title="Add LifeViewModel to own LifeRepository calls and dispatcher logic in LifeFragment"}
1. Create `LifeViewModel` (`@HiltViewModel`) injecting `LifeRepository` and `UserSessionManager`.
2. Expose `fun loadList()` that emits to a `StateFlow<List<RealmMyLife>>`, calling `lifeRepository.getMyLifeByUserId(userId)` on `viewModelScope`.
3. Expose `fun updateVisibility(id: String, visible: Boolean)` and `fun reorder(list: List<RealmMyLife>)` that call the repository methods inside `viewModelScope` without exposing dispatchers.
4. In `LifeFragment`, remove `@Inject lateinit var lifeRepository`; obtain the ViewModel via `by viewModels()`, collect its `StateFlow` with `repeatOnLifecycle(STARTED)`, and forward visibility/reorder events to ViewModel methods.
:::

---

### SurveyFragment: replace manual SharedPrefManager instantiation with @Inject

`SurveyFragment.onCreate()` calls `SharedPrefManager(requireContext())` to create the instance manually. The class is a Hilt-managed `@Singleton`; it should arrive via field injection like every other fragment in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L62-L69"}

:::task-stub{title="Inject SharedPrefManager into SurveyFragment via @Inject instead of manual constructor call"}
1. Remove `prefManager = SharedPrefManager(requireContext())` from `SurveyFragment.onCreate()`.
2. Add `@Inject lateinit var prefManager: SharedPrefManager` as a field next to the other `@Inject` fields in `SurveyFragment`.
3. Verify the field is no longer a local `var` and delete any now-unused import for `SharedPrefManager` constructor call site if applicable.
:::

---

### EnterprisesReportsFragment: replace manual SharedPrefManager instantiation with @Inject

`EnterprisesReportsFragment.onCreateView()` calls `SharedPrefManager(requireContext())` manually. Like `SurveyFragment`, it should receive the singleton via Hilt field injection.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt#L54-L57"}

:::task-stub{title="Inject SharedPrefManager into EnterprisesReportsFragment via @Inject instead of manual constructor call"}
1. Remove `prefData = SharedPrefManager(requireContext())` from `onCreateView()`.
2. Add `@Inject lateinit var prefData: SharedPrefManager` as a field in `EnterprisesReportsFragment`; annotate the class with `@AndroidEntryPoint` if not already present.
3. Confirm all `prefData.*` call sites compile and the fragment still correctly reads team name and related preferences.
:::

---

### BaseResourceFragment: make mRealm private and guard it behind requireRealmInstance()

`BaseResourceFragment` declares `protected lateinit var mRealm: Realm` at line 60, granting 20+ fragment subclasses direct write access to the database and bypassing the repository layer entirely. The existing `requireRealmInstance()` accessor is safer because it lazily initialises `mRealm` through `databaseService.createManagedRealmInstance()` and guards against accessing a closed instance — preventing the `IllegalStateException` that raw field access causes after `onDestroyView`. The field should be made private so all subclass access is forced through that accessor or, better, through repository calls.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L60-L60"}

:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L89-L98"}

:::task-stub{title="Make BaseResourceFragment.mRealm private; route all subclass Realm access through requireRealmInstance()"}
1. Change `protected lateinit var mRealm: Realm` to `private lateinit var mRealm: Realm` in `BaseResourceFragment`.
2. Compile the project to surface every subclass that references `mRealm` directly (expect ~5–10 sites).
3. For each failing call site: replace `mRealm` with `requireRealmInstance()` if the call is a read-only query, or replace with the appropriate repository method if a write is involved.
4. Verify no subclass re-declares a shadowing `mRealm` field.
:::

---

### VoicesFragment and ReplyActivity: remove cross-feature TeamsRepository injection

Both `VoicesFragment` and `ReplyActivity` inject `TeamsRepository` — a cross-feature dependency that leaks team-domain logic into the news/voices feature. Any team data needed in the voices context should be exposed through `VoicesRepository` (which can itself depend on `TeamsRepository` internally), keeping the UI layer isolated to its own feature repository.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L56-L58"}

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-clean-data-layer/app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt#L62-L65"}

:::task-stub{title="Move TeamsRepository usage in VoicesFragment / ReplyActivity behind VoicesRepository"}
1. Audit every call to `teamsRepository.*` in `VoicesFragment` and `ReplyActivity` to identify which team data they fetch.
2. Add a corresponding method to `VoicesRepository` (and `VoicesRepositoryImpl`) for each team query found, injecting `TeamsRepository` only into the impl via its constructor.
3. In `VoicesFragment` and `ReplyActivity`, replace `teamsRepository.*` calls with the new `voicesRepository.*` methods.
4. Remove `@Inject lateinit var teamsRepository: TeamsRepository` from both `VoicesFragment` and `ReplyActivity`.
:::

### Convert DictionaryActivity to use repository pattern instead of direct DatabaseService access

DictionaryActivity is the only UI class that directly calls `databaseService.withRealm` for synchronous Realm queries, including one inside a click listener on the main thread. Moving these queries behind a repository (or an existing one) removes the last UI-layer Realm coupling and eliminates a main-thread blocking query.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=71 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L71-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L117"}

:::task-stub{title="Move DictionaryActivity Realm access behind a repository"}
1. Create a DictionaryRepository interface with suspend functions for count, load, and search
2. Create DictionaryRepositoryImpl extending RealmRepository, move the three withRealm blocks from DictionaryActivity into it
3. Bind the new repository in RepositoryModule
4. Inject DictionaryRepository into DictionaryActivity and replace all databaseService.withRealm calls with suspend repository calls
5. Remove the databaseService import from DictionaryActivity
:::

### Replace deprecated synchronous getUserModel() calls with suspend getUserModelSuspending()

`getUserModel()` is a non-suspend function that runs a blocking Realm query via `databaseService.withRealm`. It is already marked `@Deprecated` with a suspend replacement (`getUserModelSuspending()`), but 40 UI files and `UserSessionManager.userModel` still call it. The `UserSessionManager` wrapper properties `userModel` and `getUserModelCopy()` are also deprecated. Migrating callers removes main-thread Realm access.

:codex-file-citation[codex-file-citation]{line_range_start=338 line_range_end=349 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L338-L349"}
:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=82 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L80-L82"}
:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L36-L42"}

:::task-stub{title="Migrate callers from getUserModel() to getUserModelSuspending()"}
1. Audit all 40 UI callers of `userSessionManager.getUserModel()` and `userRepository.getUserModel()`
2. Replace each call with the suspend variant inside an existing coroutine scope (most callers already have lifecycleScope.launch)
3. Remove the deprecated `userModel` property and `getUserModelCopy()` from UserSessionManager
4. Remove the deprecated `getUserModel()` from UserRepository interface and UserRepositoryImpl
:::

### Convert hasAtLeastOneUser() to a suspend function

`hasAtLeastOneUser()` performs a synchronous Realm query via `databaseService.withRealm` and is called from `SyncActivity.authenticateUser()` which is already a suspend function. One call site already wraps it in `withContext(Dispatchers.IO)` as a workaround. Making it suspend removes the blocking call and the manual dispatcher wrapping.

:codex-file-citation[codex-file-citation]{line_range_start=684 line_range_end=686 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L684-L686"}
:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=89 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L89-L89"}
:codex-file-citation[codex-file-citation]{line_range_start=393 line_range_end=396 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L393-L396"}
:codex-file-citation[codex-file-citation]{line_range_start=487 line_range_end=490 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L487-L490"}

:::task-stub{title="Make hasAtLeastOneUser() a suspend function"}
1. Change `fun hasAtLeastOneUser(): Boolean` to `suspend fun hasAtLeastOneUser(): Boolean` in UserRepository interface
2. In UserRepositoryImpl, replace `databaseService.withRealm` with the inherited `withRealm` (which uses withRealmAsync)
3. Update SyncActivity callers to remove the manual `withContext(Dispatchers.IO)` wrapper at line 488 since the function will be async internally
:::

### Replace synchronous databaseService.withRealm with inherited withRealm in SubmissionsRepositoryImpl

Two suspend functions in SubmissionsRepositoryImpl call `databaseService.withRealm` (synchronous) instead of the inherited `withRealm` from RealmRepository (which delegates to `withRealmAsync`). This means the Realm query runs on the calling thread rather than the IO dispatcher, blocking unnecessarily.

:codex-file-citation[codex-file-citation]{line_range_start=613 line_range_end=619 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L613-L619"}
:codex-file-citation[codex-file-citation]{line_range_start=662 line_range_end=670 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L662-L670"}

:::task-stub{title="Use inherited async withRealm in SubmissionsRepositoryImpl"}
1. In `getLastPendingSubmission()` (line 614), replace `databaseService.withRealm` with `withRealm`
2. In `getPhotosByIds()` (line 664), replace `databaseService.withRealm` with `withRealm`
3. Verify no other direct `databaseService.withRealm` calls remain in the file
:::

### Convert ConfigurationsRepository.checkHealth to a suspend function

`checkHealth()` is a non-suspend function that internally launches a coroutine via `serviceScope.launch` and delivers the result through an `OnSuccessListener` callback. This hides the async nature, prevents structured concurrency, and forces a callback pattern. Its single caller in AutoSyncWorker is already in a coroutine context.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt#L8-L8"}
:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=91 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L52-L91"}
:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L81-L86"}

:::task-stub{title="Convert checkHealth to suspend, remove OnSuccessListener callback"}
1. Change interface signature from `fun checkHealth(listener: OnSuccessListener)` to `suspend fun checkHealth(): String`
2. In ConfigurationsRepositoryImpl, remove the `serviceScope.launch` wrapper and return the result string directly
3. Update AutoSyncWorker caller to use the return value instead of the callback lambda
:::

### Add @HiltViewModel to ChatViewModel for DI consistency

ChatViewModel is the only ViewModel in the project without `@HiltViewModel`. While it currently has no injected dependencies, adding the annotation now prevents a breakage later when dependencies are added and ensures consistency with the other 21 ViewModels.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L9-L10"}

:::task-stub{title="Annotate ChatViewModel with @HiltViewModel and @Inject constructor"}
1. Add `@HiltViewModel` annotation to ChatViewModel
2. Add `@Inject constructor()` to the class declaration
3. Verify the Fragment that hosts it uses `by viewModels()` (already does)
:::

### Replace hardcoded Dispatchers with DispatcherProvider in BaseTeamFragment

BaseTeamFragment uses hardcoded `Dispatchers.IO` and `Dispatchers.Main` despite the project having a `DispatcherProvider` abstraction for testability. Since BaseTeamFragment is a base class inherited by multiple fragments, fixing it propagates the improvement to all team-related UI.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=52 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L52-L52"}
:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L70-L70"}

:::task-stub{title="Inject DispatcherProvider into BaseTeamFragment, replace hardcoded Dispatchers"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to BaseTeamFragment
2. Replace `Dispatchers.IO` with `dispatcherProvider.io` at line 52
3. Replace `Dispatchers.Main` with `dispatcherProvider.main` at line 70
:::

### Replace hardcoded Dispatchers with DispatcherProvider in viewer Activities

Five viewer Activities (TextFileViewerActivity, MarkdownViewerActivity, CSVViewerActivity, and others) use hardcoded `Dispatchers.IO`/`Dispatchers.Main` for file-reading coroutines. These are simple, isolated files with 2-5 dispatcher references each, making them easy one-file PRs that chip away at the hardcoded dispatcher count.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/TextFileViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/TextFileViewerActivity.kt#L40-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/MarkdownViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/MarkdownViewerActivity.kt#L41-L51"}
:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=89 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/CSVViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/CSVViewerActivity.kt#L48-L89"}

:::task-stub{title="Replace hardcoded Dispatchers in viewer Activities with DispatcherProvider"}
1. Add @AndroidEntryPoint and `@Inject lateinit var dispatcherProvider: DispatcherProvider` to TextFileViewerActivity, MarkdownViewerActivity, and CSVViewerActivity
2. Replace all `Dispatchers.IO` with `dispatcherProvider.io` and `Dispatchers.Main` with `dispatcherProvider.main` in each file
3. Remove direct `import kotlinx.coroutines.Dispatchers` from each file
:::

### Convert ActivitiesRepository sync-only functions to suspend with internal Realm management

`insertActivity()` and `getRecentLogin()` are non-suspend functions that require a caller-managed `Realm` parameter. Their only caller is `TransactionSyncManager` which passes its own Realm instance. Converting them to suspend functions that use the inherited `withRealm` from RealmRepository removes raw Realm leakage through the interface and aligns with the rest of the repository pattern.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L43-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=303 line_range_end=339 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L303-L339"}
:codex-file-citation[codex-file-citation]{line_range_start=310 line_range_end=313 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L310-L313"}

:::task-stub{title="Make insertActivity and getRecentLogin suspend functions with internal Realm"}
1. Remove `realm: Realm` parameter from `insertActivity` and `getRecentLogin` in ActivitiesRepository interface, make them suspend
2. In ActivitiesRepositoryImpl, wrap the body of each function with `executeTransaction` (for insertActivity) and `withRealm` (for getRecentLogin)
3. Update TransactionSyncManager caller to remove the raw Realm parameter pass-through
:::

### Convert TeamsRepository insertTeamLog and getLastVisit to suspend functions

Similar to ActivitiesRepository, `insertTeamLog()` and `getLastVisit()` take raw `Realm` parameters and are non-suspend. They are called from `TransactionSyncManager`. Converting them to suspend functions with internal Realm management removes Realm leakage through the TeamsRepository interface.

:codex-file-citation[codex-file-citation]{line_range_start=1314 line_range_end=1331 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1314-L1331"}
:codex-file-citation[codex-file-citation]{line_range_start=1333 line_range_end=1339 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1333-L1339"}

:::task-stub{title="Make insertTeamLog and getLastVisit suspend functions with internal Realm"}
1. Remove `realm: Realm` parameter from `insertTeamLog` and `getLastVisit` in TeamsRepository interface, make them suspend
2. In TeamsRepositoryImpl, wrap insertTeamLog body with `executeTransaction` and getLastVisit body with `withRealm`
3. Update TransactionSyncManager caller to remove the raw Realm parameter pass-through
4. Also convert `serializeTeamActivities` if it no longer needs the Realm context
:::

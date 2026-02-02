# Refactoring Tasks - Repository Boundary Reinforcement

This document outlines 10 low-hanging fruit refactoring tasks focused on reinforcing repository boundaries between layers, eliminating cross-feature data leaks, and moving data functions from UI/data/service layers into repositories.

### Task 1: Move `becomeMember` from DataService to UserRepository

The `BecomeMemberActivity` directly calls `DataService.becomeMember()` with callback patterns instead of using the repository layer with coroutines. This bypasses repository abstraction and uses legacy callback threading with `runOnUiThread`.

:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=158 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L146-L158"}

:::task-stub{title="Move becomeMember to UserRepository"}
1. Add `suspend fun becomeMember(userJson: JsonObject): Result<String>` to `UserRepository` interface
2. Implement the function in `UserRepositoryImpl` using proper coroutine dispatchers
3. Update `BecomeMemberActivity` to call `userRepository.becomeMember()` with `lifecycleScope.launch`
4. Remove callback interfaces (`CreateUserCallback`, `OnSecurityDataListener`) from DataService
5. Remove or deprecate `DataService.becomeMember()` method
:::

### Task 2: Move `isPlanetAvailable` from DataService to ConfigurationsRepository

Three locations directly instantiate `DataService` to call `isPlanetAvailable()` with callback patterns. This server availability check belongs in ConfigurationsRepository as a suspend function returning a boolean or Result type.

:codex-file-citation[codex-file-citation]{line_range_start=206 line_range_end=221 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L206-L221"}

:codex-file-citation[codex-file-citation]{line_range_start=225 line_range_end=237 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L225-L237"}

:codex-file-citation[codex-file-citation]{line_range_start=479 line_range_end=487 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L479-L487"}

:::task-stub{title="Move isPlanetAvailable to ConfigurationsRepository"}
1. Add `suspend fun isPlanetAvailable(): Boolean` to `ConfigurationsRepository` interface
2. Implement in `ConfigurationsRepositoryImpl` using proper error handling
3. Update `BaseResourceFragment` to inject `ConfigurationsRepository` and call the suspend function
4. Replace callback pattern with coroutine-based flow control
5. Remove `PlanetAvailableListener` callback interface from DataService
:::

### Task 3: Move `checkCheckSum` from DataService to ConfigurationsRepository

The `DialogUtils.checkApkUpdate` function directly instantiates `DataService` to call `checkCheckSum()`. This APK verification logic should be in ConfigurationsRepository where other configuration and version management exists.

:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=184 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L179-L184"}

:::task-stub{title="Move checkCheckSum to ConfigurationsRepository"}
1. Add `suspend fun checkCheckSum(path: String): Boolean` to `ConfigurationsRepository` interface
2. Implement in `ConfigurationsRepositoryImpl` moving logic from DataService
3. Update `DialogUtils.checkApkUpdate` to accept `ConfigurationsRepository` as parameter
4. Replace direct DataService instantiation with repository call
5. Remove or deprecate `DataService.checkCheckSum()` method
:::

### Task 4: Eliminate Direct DataService Usage in AutoSyncWorker

The `AutoSyncWorker` directly instantiates `DataService` for `checkVersion()` and `healthAccess()` calls. Workers should use repositories through Hilt EntryPoints for proper dependency injection and separation of concerns.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L56-L57"}

:codex-file-citation[codex-file-citation]{line_range_start=82 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L82-L84"}

:::task-stub{title="Refactor AutoSyncWorker to use ConfigurationsRepository"}
1. Add `ConfigurationsRepository` to the existing `AutoSyncEntryPoint` interface
2. Move `checkVersion()` and `healthAccess()` to `ConfigurationsRepository` as suspend functions
3. Update `AutoSyncWorker.doWork()` to obtain repository from EntryPoint
4. Replace DataService calls with repository calls using proper coroutine context
5. Remove deprecated methods from DataService
:::

### Task 5: Standardize Threading in UI Layer with ViewModel Pattern

Multiple fragments use `lifecycleScope.launch(Dispatchers.IO)` directly for data operations instead of delegating to ViewModels. This violates separation of concerns and makes threading inconsistent across the app.

:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L121-L126"}

:codex-file-citation[codex-file-citation]{line_range_start=601 line_range_end=610 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L601-L610"}

:codex-file-citation[codex-file-citation]{line_range_start=112 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L112-L117"}

:::task-stub{title="Create ResourcesViewModel for ResourcesFragment"}
1. Create `ResourcesViewModel` extending `ViewModel` with `@HiltViewModel` annotation
2. Inject `ResourcesRepository` and expose LiveData/StateFlow for resources list
3. Move `saveSearchActivity()` data logic into ViewModel using `viewModelScope`
4. Update `ResourcesFragment` to observe ViewModel data on Main thread
5. Remove `lifecycleScope.launch(Dispatchers.IO)` calls from fragment, use ViewModel instead
:::

### Task 6: Replace Callback Pattern with Coroutines in SyncManager Calls

Multiple fragments use `SyncManager.start()` with callback listeners instead of suspend functions. This creates nested callback hell and inconsistent error handling patterns.

:codex-file-citation[codex-file-citation]{line_range_start=112 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L112-L117"}

:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=135 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L121-L135"}

:::task-stub{title="Convert SyncManager to suspend function based API"}
1. Add new suspend function `suspend fun syncData(syncType: String = "all"): Result<Unit>` to SyncManager
2. Keep existing callback-based `start()` for backward compatibility temporarily
3. Update `FeedbackListFragment.startSyncManager()` to use new suspend function
4. Use `viewLifecycleOwner.lifecycleScope.launch` without `Dispatchers.IO` (let repository handle it)
5. Replace `OnSyncListener` callback with Result-based error handling
:::

### Task 7: Consolidate DiffUtil Usage with DiffUtils Helper

Many adapters define their own DiffUtil.ItemCallback objects instead of using the existing `DiffUtils.itemCallback` helper. This creates code duplication and inconsistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L1-L18"}

:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L80-L108"}

:::task-stub{title="Refactor VoicesAdapter to use DiffUtils.itemCallback"}
1. Identify the inline DiffUtil.ItemCallback in VoicesAdapter constructor
2. Replace with `DiffUtils.itemCallback` call extracting the lambda logic
3. Verify adapter still uses ListAdapter pattern correctly
4. Test that list updates and animations work correctly
5. Document the pattern for other adapters to follow
:::

### Task 8: Move Data Query Logic from DashboardActivity to DashboardViewModel

The `DashboardActivity.checkAndCreateNewNotifications()` method performs complex data queries across multiple repositories directly in the UI layer. This logic belongs in the ViewModel layer.

:codex-file-citation[codex-file-citation]{line_range_start=655 line_range_end=670 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L655-L670"}

:::task-stub{title="Move notification logic to DashboardViewModel"}
1. Add `suspend fun checkAndCreateNewNotifications(userId: String?): List<NotificationConfig>` to DashboardViewModel
2. Move the multi-repository query logic from Activity to ViewModel
3. Expose notification count and list as StateFlow from ViewModel
4. Update `DashboardActivity` to observe ViewModel StateFlow and update UI
5. Remove direct repository calls from `lifecycleScope.launch(Dispatchers.IO)` in Activity
:::

### Task 9: Extract Suspend Function Callbacks from VoicesAdapter

The `VoicesAdapter` accepts suspend function lambdas (`isTeamLeaderFn`, `onLikeClickListener`, etc.) which creates tight coupling between adapter and data layer. Adapters should receive simple data and callbacks, not coroutine logic.

:codex-file-citation[codex-file-citation]{line_range_start=78 line_range_end=143 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L78-L143"}

:::task-stub{title="Refactor VoicesAdapter to remove suspend callbacks"}
1. Create `NewsViewModel` to handle like/unlike operations and team leader status
2. Change VoicesAdapter to accept `NewsViewModel` instead of suspend function lambdas
3. Move `isTeamLeaderFn` logic to ViewModel, expose as StateFlow<Boolean>
4. Convert click listeners to simple callback interfaces that delegate to ViewModel
5. Update VoicesFragment to pass ViewModel to adapter instead of lambda functions
:::

### Task 10: Standardize RealmRepository Usage Across All Repository Implementations

Several repository implementations don't extend `RealmRepository` and instead duplicate database access patterns. The `RealmRepository` base class provides `queryList`, `findByField`, `save`, `update`, and `delete` helper methods that should be reused.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L1-L50"}

:::task-stub{title="Migrate TeamsRepositoryImpl to extend RealmRepository"}
1. Change `TeamsRepositoryImpl` to extend `RealmRepository(databaseService)`
2. Replace manual `databaseService.withRealmAsync` calls with inherited `queryList<RealmMyTeam>`
3. Replace manual transaction code with inherited `executeTransaction` and `save` methods
4. Use inherited `findByField<RealmMyTeam>` instead of custom query methods
5. Verify all team-related database operations still work correctly through tests
:::

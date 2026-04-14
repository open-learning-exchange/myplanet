### CheckboxAdapter: Replace manual DiffCallback with DiffUtils.itemCallback

CheckboxAdapter is the only RecyclerView adapter that hand-rolls a `DiffUtil.ItemCallback` class instead of using the project's `DiffUtils.itemCallback` helper. Replacing the 9-line inner class with a one-liner aligns it with all 36 other adapters and removes trivial code.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=64 path=app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt#L56-L64"}
:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L6-L18"}

:::task-stub{title="CheckboxAdapter: use DiffUtils.itemCallback"}
1. Remove the inner `CheckboxDiffCallback` class (lines 56-64) from `CheckboxAdapter.kt`
2. Replace the `ListAdapter` constructor argument with `DiffUtils.itemCallback<String>({ old, new -> old == new }, { old, new -> old == new })`
3. Remove the now-unused `import androidx.recyclerview.widget.DiffUtil` line
4. Build to verify no regressions
:::

### SyncActivity: Remove redundant withContext(Dispatchers.Main) in sync status collector

The `syncStatus` Flow is collected inside `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`, which already runs on `Dispatchers.Main`. The three `withContext(Dispatchers.Main)` wrappers at lines 168, 175, and 182 are no-ops that add unnecessary coroutine overhead on every sync state change.

:codex-file-citation[codex-file-citation]{line_range_start=159 line_range_end=188 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L159-L188"}

:::task-stub{title="SyncActivity: strip redundant Main dispatcher switches in sync collector"}
1. In `SyncActivity.onCreate`, remove the `withContext(Dispatchers.Main)` wrapper around `onSyncStarted()` at line 168
2. Remove the `withContext(Dispatchers.Main)` wrapper around `onSyncComplete()` at line 175
3. Remove the `withContext(Dispatchers.Main)` wrapper around `onSyncFailed(status.message)` at line 182
4. Call the three methods directly since the collector already runs on Main
5. Remove the `Dispatchers` import if it becomes unused
:::

### ProcessUserDataActivity: Replace runOnUiThread with lifecycleScope in checkDownloadResult

`checkDownloadResult()` uses `runOnUiThread` which posts blindly to the main looper even if the Activity is finishing. Replacing it with `lifecycleScope.launch(Dispatchers.Main.immediate)` ties execution to the Activity lifecycle and prevents wasted work after destruction.

:codex-file-citation[codex-file-citation]{line_range_start=93 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L93-L108"}

:::task-stub{title="ProcessUserDataActivity: replace runOnUiThread with lifecycleScope"}
1. Change `checkDownloadResult` to use `lifecycleScope.launch(Dispatchers.Main.immediate)` instead of `runOnUiThread`
2. Remove the inner `isFinishing`/`isDestroyed` guard since the lifecycle scope already cancels when the Activity is destroyed
3. Verify the build succeeds and download progress still displays
:::

### VideoViewerActivity: Replace runOnUiThread callbacks with lifecycleScope

`setAuthSession()` and `onError()` use `runOnUiThread` for UI updates from background callbacks. These fire even after the Activity is destroyed. Switching to `lifecycleScope.launch` ties the work to the Activity lifecycle and avoids leaked UI updates.

:codex-file-citation[codex-file-citation]{line_range_start=88 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/VideoViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/VideoViewerActivity.kt#L88-L105"}

:::task-stub{title="VideoViewerActivity: replace runOnUiThread with lifecycleScope"}
1. Replace `runOnUiThread { … }` in `setAuthSession()` (line 91) with `lifecycleScope.launch { … }`
2. Replace `runOnUiThread { … }` in `onError()` (line 104) with `lifecycleScope.launch { … }`
3. Add the necessary `lifecycleScope` import if not already present
4. Build to verify video playback still works
:::

### SyncActivity.downloadAdditionalResources: Move JSON deserialization off the main thread

`Json.decodeFromString` at line 581 parses a potentially large JSON string on whatever thread calls it (often Main). Wrapping it in `withContext(Dispatchers.IO)` prevents a main-thread stall after sync completes.

:codex-file-citation[codex-file-citation]{line_range_start=578 line_range_end=584 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L578-L584"}

:::task-stub{title="SyncActivity: wrap JSON deserialization in Dispatchers.IO"}
1. Make `downloadAdditionalResources()` a `suspend` function
2. Wrap the `Json.decodeFromString` call at line 581 in `withContext(Dispatchers.IO) { … }`
3. Update all call sites to call within a coroutine scope
4. Build to verify additional resource downloads still trigger correctly
:::

### ImprovedSyncManager: Replace orphaned CoroutineScope with injected applicationScope

`ImprovedSyncManager` creates its own `CoroutineScope(Dispatchers.IO + SupervisorJob())` at line 42 which is never cancelled, leaking coroutines. It should reuse the injected `applicationScope` already available in the class to get proper lifecycle management and consistent dispatcher configuration.

:codex-file-citation[codex-file-citation]{line_range_start=42 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt#L42-L42"}
:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=103 path=app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt#L90-L103"}

:::task-stub{title="ImprovedSyncManager: replace orphaned CoroutineScope with injected scope"}
1. Verify the class constructor already receives an `applicationScope: CoroutineScope` parameter
2. Remove the `private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())` field at line 42
3. Replace all `syncScope.launch` calls with `applicationScope.launch(dispatcherProvider.io)`
4. Remove the unused `SupervisorJob` and `Dispatchers` imports
5. Build and verify sync still completes end-to-end
:::

### SyncActivity.onLogin: Scope Flow collection to lifecycleScope instead of applicationScope

The `isNetworkConnectedFlow` collection at lines 649-664 is launched in `MainApplication.applicationScope`, which means it keeps running indefinitely even after the Activity is destroyed. Scoping it to `lifecycleScope` stops the collection when the user leaves the screen, preventing background work on a dead Activity.

:codex-file-citation[codex-file-citation]{line_range_start=649 line_range_end=664 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L649-L664"}

:::task-stub{title="SyncActivity.onLogin: replace applicationScope with lifecycleScope for flow collection"}
1. Change `MainApplication.applicationScope.launch` at line 653 to `lifecycleScope.launch`
2. Change `.launchIn(MainApplication.applicationScope)` at line 664 to `.launchIn(lifecycleScope)`
3. Verify that `startUpload("login")` and `transactionSyncManager.syncDb("login_activities")` still fire when network reconnects while the Activity is active
:::

### VoicesFragment: Move filtered data lists into NewsViewModel

`VoicesFragment` stores `filteredNewsList`, `searchFilteredList`, and `labelFilteredList` as fragment member variables (lines 55-57). These are wiped on configuration change, causing a full reload. Moving them into the existing `NewsViewModel` preserves state across rotation and unblocks future ViewModel-first refactors in the voices feature.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L55-L57"}
:codex-file-citation[codex-file-citation]{line_range_start=102 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L102-L117"}

:::task-stub{title="VoicesFragment: hoist filtered news lists into NewsViewModel"}
1. Add `filteredNewsList`, `searchFilteredList`, and `labelFilteredList` properties to `NewsViewModel` (backed by simple `var` or `MutableStateFlow`)
2. In `VoicesFragment`, obtain `NewsViewModel` via `by viewModels()` or `by activityViewModels()`
3. Replace reads/writes of the three member variables with ViewModel property access
4. Remove the three `private var` declarations from `VoicesFragment` (lines 55-57)
5. Verify news list, search, and label filtering still work after device rotation
:::

### TeamsTasksFragment: Move task list into TeamViewModel

`TeamsTasksFragment` stores its task list as a public `var list: List<RealmTeamTask>` (line 48). This is lost on configuration change and publicly mutable. Moving it into the existing `TeamViewModel` preserves it across rotation and gives a single source of truth for task data.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=48 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt#L48-L48"}

:::task-stub{title="TeamsTasksFragment: hoist task list into TeamViewModel"}
1. Add a `taskList` property to `TeamViewModel` (backed by `MutableStateFlow<List<RealmTeamTask>>`)
2. In `TeamsTasksFragment`, replace all reads/writes of `list` with `teamViewModel.taskList.value`
3. Remove the `var list` declaration from `TeamsTasksFragment` (line 48)
4. Verify task list display and filtering still work after device rotation
:::

### BecomeMemberActivity: Remove nested runOnUiThread inside Main coroutine

In `addMember()`, `runOnUiThread` at line 153 runs inside a coroutine that is already on `Dispatchers.Main` (line 148). This double-posts to the main looper unnecessarily, adding a full message-queue round-trip delay before the progress dialog is dismissed.

:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L146-L159"}

:::task-stub{title="BecomeMemberActivity: remove redundant runOnUiThread inside Main coroutine"}
1. Remove the `runOnUiThread { … }` wrapper at line 153 inside the `OnSecurityDataListener` callback
2. Call `customProgressDialog.dismiss()` and `autoLoginNewMember(…)` directly since the enclosing coroutine is already on `Dispatchers.Main`
3. Build and verify member creation flow still dismisses the dialog and navigates correctly
:::

# Refactoring Tasks Report - Repository Boundaries & Data Layer Cleanup

This report identifies 10 granular, easily reviewable tasks to reinforce repository boundaries between layers, eliminate cross-feature data leaks, and move data functions from UI/data/service layers into repositories.

---

### Task 1: Move Health Examination Transaction Logic from AddExaminationActivity to HealthRepository

The AddExaminationActivity directly manages Realm transactions for creating and saving health examination data (lines 262-280 and 284-348). This violates repository boundaries by placing data layer logic in the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=262 line_range_end=280 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L262-L280"}

:codex-file-citation[codex-file-citation]{line_range_start=284 line_range_end=348 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L284-L348"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L1-L32"}

:::task-stub{title="Move health examination save logic to HealthRepository"}
1. Add `suspend fun saveExamination(examination: RealmHealthExamination, health: RealmMyHealth, user: RealmUser): Result<Unit>` method to HealthRepository interface
2. Implement the method in HealthRepositoryImpl using withRealm/RealmRepository pattern to handle the transaction
3. Refactor AddExaminationActivity.saveData() to call healthRepository.saveExamination() instead of direct mRealm operations
4. Remove mRealm field and databaseService injection from AddExaminationActivity (lines 52, 60, 94)
5. Test health examination creation flow to verify functionality preserved
:::

---

### Task 2: Eliminate Flow Collection Without Lifecycle Binding in SyncActivity

SyncActivity collects from broadcastService.events (line 775) using lifecycleScope.launch without repeatOnLifecycle, causing potential memory leaks. The onDestroy method doesn't cancel any jobs.

:codex-file-citation[codex-file-citation]{line_range_start=773 line_range_end=781 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L773-L781"}

:::task-stub{title="Add repeatOnLifecycle to SyncActivity broadcast Flow collector"}
1. Wrap the collect block in registerReceiver() with viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)
2. Store the launch job in a class-level Job? variable for explicit cancellation tracking
3. Add job?.cancel() in onPause() or onDestroy() for proper cleanup
4. Test sync activity lifecycle to ensure no leaks when activity is paused/destroyed
:::

---

### Task 3: Replace Application Scope with Lifecycle Scope in SyncActivity.onLogin()

The onLogin method (line 654-669) uses MainApplication.applicationScope to launch network operations that should be bound to the Activity lifecycle, causing operations to continue after Activity destruction.

:codex-file-citation[codex-file-citation]{line_range_start=654 line_range_end=669 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L654-L669"}

:::task-stub{title="Replace applicationScope with lifecycleScope in SyncActivity"}
1. Replace `MainApplication.applicationScope.launch` with `lifecycleScope.launch` at line 658
2. Replace `.launchIn(MainApplication.applicationScope)` with `.launchIn(lifecycleScope)` at line 669
3. Add proper error handling for coroutine cancellation (CancellationException)
4. Test login flow to verify upload/sync still works correctly and stops when activity destroyed
:::

---

### Task 4: Convert InlineResourceAdapter to ListAdapter with DiffUtil

InlineResourceAdapter extends RecyclerView.Adapter directly and uses manual list updates instead of the project's standard ListAdapter+DiffUtil pattern used by all other adapters.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L27-L30"}

:::task-stub{title="Refactor InlineResourceAdapter to use ListAdapter+DiffUtil"}
1. Change InlineResourceAdapter to extend ListAdapter<RealmMyLibrary, InlineResourceAdapter.ViewHolder>(DiffUtils.itemCallback())
2. Remove resources field from constructor and getItemCount() override
3. Update onBindViewHolder to use getItem(position) instead of resources[position]
4. Update all call sites to use submitList() instead of passing resources in constructor
5. Test inline resource display in courses to verify no visual regressions
:::

---

### Task 5: Create ViewModel for LifeFragment to Remove Direct Repository Access

LifeFragment directly injects and uses LifeRepository in the UI layer without a ViewModel, violating MVVM architecture and making the fragment difficult to test.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L1-L50"}

:::task-stub{title="Create LifeViewModel to encapsulate LifeRepository access"}
1. Create LifeViewModel class in ui/life/ package with @HiltViewModel annotation
2. Inject LifeRepository into LifeViewModel constructor and expose StateFlow<List<RealmLife>> for life items
3. Move business logic from LifeFragment coroutines into ViewModel methods
4. Update LifeFragment to inject LifeViewModel and collect StateFlow with repeatOnLifecycle
5. Remove direct lifeRepository injection from LifeFragment
6. Test life feature to ensure data loads correctly through ViewModel
:::

---

### Task 6: Create ViewModel for PersonalsFragment to Remove Direct Repository Access

PersonalsFragment directly injects PersonalsRepository, UploadManager, and UserSessionManager, performing data operations in UI lifecycle callbacks instead of using a ViewModel.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L1-L50"}

:::task-stub{title="Create PersonalsViewModel to encapsulate business logic"}
1. Create PersonalsViewModel class in ui/personals/ package with @HiltViewModel annotation
2. Inject PersonalsRepository, UploadManager, UserSessionManager into ViewModel constructor
3. Expose StateFlow<List<RealmPersonals>> and upload state for UI observation
4. Move all data fetching and upload logic from fragment lifecycle callbacks into ViewModel
5. Update PersonalsFragment to inject and observe PersonalsViewModel state
6. Test personals feature including list display and upload functionality
:::

---

### Task 7: Tighten TeamResourcesFragment Cross-Repository Boundary

TeamResourcesFragment directly queries ResourcesRepository (line 103) to get all library items for team association, bypassing proper repository boundaries. This should be mediated through TeamsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=100 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesFragment.kt#L100-L125"}

:::task-stub{title="Move available resources query into TeamsRepository"}
1. Add `suspend fun getAvailableResourcesToAdd(teamId: String): List<RealmMyLibrary>` method to TeamsRepository interface
2. Implement method in TeamsRepositoryImpl to inject ResourcesRepository, fetch all resources, and filter by existing team resources
3. Update TeamResourcesFragment.showResourceListDialog() to call teamsRepository.getAvailableResourcesToAdd() instead of direct resourcesRepository access
4. Remove resourcesRepository injection from TeamResourcesFragment (inherited from BaseResourceFragment)
5. Test adding resources to teams to verify filtering works correctly
:::

---

### Task 8: Move Resource Batch Transaction Logic from SyncManager to ResourcesRepository

SyncManager.resourceTransactionSync() (lines 567-770) directly executes Realm batch transactions for resource inserts, which should be delegated to ResourcesRepository for proper separation of concerns.

:codex-file-citation[codex-file-citation]{line_range_start=664 line_range_end=675 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L664-L675"}

:codex-file-citation[codex-file-citation]{line_range_start=703 line_range_end=712 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L703-L712"}

:::task-stub{title="Extract resource batch insert to ResourcesRepository"}
1. Add `suspend fun batchInsertResources(resources: List<JsonObject>): Result<List<String>>` method to ResourcesRepository interface
2. Implement method in ResourcesRepositoryImpl using withRealm to execute batch transaction
3. Update SyncManager.resourceTransactionSync() to call resourcesRepository.batchInsertResources() instead of direct realm.executeTransaction
4. Remove direct DatabaseService usage from resource sync section
5. Test resource synchronization to ensure batch insert performance maintained
:::

---

### Task 9: Inject ServerUrlMapper via Hilt Instead of Manual Instantiation

ServerUrlMapper is manually instantiated with `private val serverUrlMapper = ServerUrlMapper()` in 14+ locations across UI and repository layers, bypassing dependency injection.

:codex-file-citation[codex-file-citation]{line_range_start=83 line_range_end=83 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L83-L83"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L47-L47"}

:::task-stub{title="Convert ServerUrlMapper to Hilt-injected singleton"}
1. Add @Singleton annotation to ServerUrlMapper class and @Inject constructor() annotation
2. Provide ServerUrlMapper in ServiceModule.kt with @Provides @Singleton method if needed
3. Replace all manual instantiations with @Inject lateinit var serverUrlMapper: ServerUrlMapper in fragments
4. Update MainApplication to inject ServerUrlMapper instead of manual instantiation
5. Test server URL mapping functionality across login, sync, and resource loading
:::

---

### Task 10: Move Chat History Realm Insert from TransactionSyncManager to ChatRepository

TransactionSyncManager.insertToChat() (line 240 area) directly performs Realm operations for chat data insertion, which should be delegated to ChatRepository for proper layer separation.

:codex-file-citation[codex-file-citation]{line_range_start=240 line_range_end=256 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L240-L256"}

:codex-file-citation[codex-file-citation]{line_range_start=298 line_range_end=312 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L298-L312"}

:::task-stub{title="Move chat sync insert logic to ChatRepository"}
1. Add `suspend fun syncInsertChatHistory(chatData: JsonArray): Result<Unit>` method to ChatRepository interface
2. Implement method in ChatRepositoryImpl using withRealm for transaction management
3. Inject ChatRepository into TransactionSyncManager via constructor
4. Replace direct insertToChat/insertDocs calls with chatRepository.syncInsertChatHistory()
5. Test chat synchronization to verify messages sync correctly from server
:::

---

## Testing Recommendations

For each task:
1. Run the app and manually test the affected feature area
2. Verify no Realm transaction errors or threading issues
3. Check logcat for any coroutine cancellation warnings
4. Confirm repository methods use Dispatchers.IO via RealmRepository base class
5. Ensure no memory leaks by testing lifecycle transitions (pause/resume/destroy)

## Priority Order

Recommended execution order to minimize merge conflicts:
1. Task 9 (ServerUrlMapper DI) - touches many files but simple changes
2. Task 2, 3 (SyncActivity lifecycle fixes) - isolated to one file
3. Task 4 (InlineResourceAdapter) - isolated UI component
4. Task 1 (Health transactions) - isolated feature area
5. Task 5, 6 (ViewModels) - new files, minimal conflicts
6. Task 7 (Team resources boundary) - depends on repository patterns
7. Task 8, 10 (Sync service extractions) - similar patterns
8. Task 10 (Chat sync) - touches sync infrastructure

Each task is designed to be completed in ~1-3 hours, easily reviewable in a single PR, and independently testable.

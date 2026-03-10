### BaseResourceFragment exposes Realm to UI
BaseResourceFragment keeps a protected Realm handle that fragments initialize in onCreate, inviting direct database access from the UI layer instead of routing through repositories.

:codex-file-citation[codex-file-citation]{line_range_start=58 line_range_end=83 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L58-L83"}
:codex-file-citation[codex-file-citation]{line_range_start=379 line_range_end=383 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L379-L383"}

:::task-stub{title="Move Realm ownership into repositories"}
1. Replace the protected Realm field with repository-provided data helpers.
2. Inject scoped repository methods that wrap RealmRepository patterns.
3. Remove direct Realm initialization from onCreate and rely on injected data access.
:::

### BaseResourceFragment mixes cross-feature repositories
The base fragment injects multiple feature repositories (resources, courses, submissions, surveys, configurations) into a single UI class, blurring feature boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=65 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L65-L75"}

:::task-stub{title="Introduce feature-scoped facades for resources UI"}
1. Create a ResourceUI facade that aggregates needed calls instead of injecting five repositories.
2. Update BaseResourceFragment to depend only on the facade.
3. Pass feature-specific facades down to subclasses to avoid cross-feature data leaks.
:::

### BaseRecyclerFragment performs Realm deletes on the UI layer
Deletion logic executes Realm transactions directly inside the base UI fragment, handling course progress and exam cleanup on the main coroutine scope.

:codex-file-citation[codex-file-citation]{line_range_start=201 line_range_end=229 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L201-L229"}

:::task-stub{title="Move deletion flows into repository layer"}
1. Add repository methods to encapsulate course and exam deletion using RealmRepository.
2. Invoke the repository from deleteSelected() and remove direct Realm operations.
3. Ensure the repository handles dispatcher selection for IO work.
:::

### BaseRecyclerFragment recreates adapters after transactions
After write operations the fragment refreshes Realm and replaces the RecyclerView adapter instance, risking UI flicker and state loss instead of relying on DiffUtil updates.

:codex-file-citation[codex-file-citation]{line_range_start=169 line_range_end=175 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L169-L175"}

:::task-stub{title="Adopt ListAdapter with DiffUtils for refresh"}
1. Switch BaseRecyclerFragment implementations to ListAdapter with DiffUtils.itemCallback.
2. Replace adapter recreation with submitList updates post-transaction.
3. Verify selection state handling survives DiffUtil-driven updates.
:::

### UploadManager writes directly to Realm from service layer
UploadManager issues databaseService.executeTransactionAsync calls to mutate Realm models while orchestrating network uploads, coupling service logic to storage.

:codex-file-citation[codex-file-citation]{line_range_start=196 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L196-L208"}
:codex-file-citation[codex-file-citation]{line_range_start=334 line_range_end=357 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L334-L357"}

:::task-stub{title="Extract upload persistence into repository"}
1. Create an UploadRepository that encapsulates Realm mutations for uploads.
2. Have UploadManager call repository methods instead of executing transactions.
3. Ensure repository methods run on Dispatchers.IO and return minimal DTOs to the service.
:::

### RetryQueue persists operations directly with Realm
RetryQueue updates and creates RealmRetryOperation objects via direct Realm transactions inside the queue manager, binding retry orchestration to storage details.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L49-L84"}
:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=180 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L123-L180"}

:::task-stub{title="Move retry persistence into repository layer"}
1. Add a RetryOperationsRepository that wraps RealmRetryOperation CRUD via RealmRepository.
2. Refactor RetryQueue to call the repository instead of executing transactions.
3. Centralize nextRetryTime and status updates inside the repository for reuse.
:::

### PersonalsFragment bypasses ViewModel and talks to repositories/services directly
PersonalsFragment handles repository flows and calls UploadManager from the fragment, leaving no ViewModel boundary between UI and data layers.

:codex-file-citation[codex-file-citation]{line_range_start=30 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L30-L75"}
:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L92-L107"}

:::task-stub{title="Introduce PersonalsViewModel for uploads and edits"}
1. Create a ViewModel to expose personals flow and upload actions to the UI.
2. Move uploadManager and repository calls from the fragment into the ViewModel.
3. Keep the fragment focused on rendering and delegating user intents.
:::

### LifeFragment performs repository calls without a ViewModel
LifeFragment executes repository updates and visibility changes directly from the fragment using lifecycleScope and Dispatchers.IO, missing a ViewModel intermediary.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L48-L95"}

:::task-stub{title="Add LifeViewModel to own list state"}
1. Create LifeViewModel that exposes MyLife lists and visibility/reorder actions.
2. Move repository calls and dispatcher management into the ViewModel.
3. Update the fragment to observe state and submit lists via ListAdapter.
:::

### SurveyFragment manually instantiates SharedPrefManager
SurveyFragment constructs SharedPrefManager with a context instead of using injected dependencies, sidestepping the DI graph.

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L62-L67"}

:::task-stub{title="Inject SharedPrefManager via Hilt"}
1. Replace manual SharedPrefManager construction with constructor injection.
2. Update fragment fields to lateinit var with @Inject annotation.
3. Remove context-based instantiation to align with DI cleanup goals.
:::

### BaseRecyclerFragment manages add-to-library logic in UI layer
Join/add flows in the base recycler fragment orchestrate repository calls and Realm refreshes inside the UI, mixing presentation with domain work.

:codex-file-citation[codex-file-citation]{line_range_start=150 line_range_end=185 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L150-L185"}

:::task-stub{title="Shift add/join orchestration into repositories"}
1. Move add-to-library/course join logic into dedicated repository/service methods.
2. Have the fragment call a single suspend function and react to result state.
3. Ensure data layer handles transactions and returns status to the UI.
:::

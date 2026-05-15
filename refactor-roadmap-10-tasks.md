### Bound the network monitor worker
`NetworkMonitorWorker` is a one-time worker, but it currently collects a network flow indefinitely. This is a high-value quick win because it removes a long-running observer from background work without touching other modules.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt#L37-L50"}

:::task-stub{title="Bound NetworkMonitorWorker collection"}
1. Change the worker so it reacts to the first reconnect transition and then finishes normally.
2. Preserve the delayed server reachability scheduling behavior.
3. Verify the worker no longer keeps a perpetual collector alive after doing its job.
:::

### Inject the Realm dispatcher into upload repository
`UploadRepositoryImpl` extends `RealmRepository`, but it is wired with a generic IO dispatcher from `ServiceModule` instead of the dedicated `@RealmDispatcher` already provided by `DatabaseModule`. Fixing this is small, isolated, and helps the DI and data-layer cleanup converge on one Realm threading path.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt#L12-L16"}
:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt#L62-L69"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/di/DatabaseModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/DatabaseModule.kt#L28-L35"}

:::task-stub{title="Align UploadRepositoryImpl with @RealmDispatcher"}
1. Switch upload repository construction to use the dedicated Realm dispatcher qualifier.
2. Remove the manual generic-dispatcher wiring for that repository from the service layer.
3. Keep repository behavior unchanged while standardizing the threading contract.
:::

### Remove nested IO dispatch in course membership writes
`CoursesRepositoryImpl` wraps several write paths in `withContext(databaseService.ioDispatcher)` even though the actual writes already go through `executeTransaction`. Removing the extra context hop is a contained micro-optimization and makes the repository more consistent with `RealmRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=177 line_range_end=216 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L177-L216"}
:codex-file-citation[codex-file-citation]{line_range_start=352 line_range_end=392 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L352-L392"}

:::task-stub{title="Drop redundant IO wrapping in CoursesRepositoryImpl"}
1. Remove the extra dispatcher wrapping around course add, join, and leave writes.
2. Keep transaction boundaries and removed-log behavior exactly as they are.
3. Re-check the related realtime update trigger after the dispatcher cleanup.
:::

### Remove nested IO dispatch in resource library writes
`ResourcesRepositoryImpl` has the same double-dispatch pattern around `addResourcesToUserLibrary`. This is a one-method cleanup with low merge-conflict risk and fits the broader data-layer standardization.

:codex-file-citation[codex-file-citation]{line_range_start=414 line_range_end=442 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L414-L442"}

:::task-stub{title="Drop redundant IO wrapping in ResourcesRepositoryImpl"}
1. Let the repository write path rely on the existing Realm transaction helper instead of an extra IO context switch.
2. Preserve chunking and removed-log cleanup exactly as-is.
3. Keep the change scoped to this one repository method.
:::

### Standardize submission writes on RealmRepository helpers
`SubmissionsRepositoryImpl` already mixes `executeTransaction` with direct `databaseService.executeTransactionAsync` calls. Cleaning the remaining raw calls is a granular RealmRepository adoption step that reduces data-layer inconsistency before larger refactors.

:codex-file-citation[codex-file-citation]{line_range_start=356 line_range_end=373 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L356-L373"}
:codex-file-citation[codex-file-citation]{line_range_start=422 line_range_end=433 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L422-L433"}
:codex-file-citation[codex-file-citation]{line_range_start=509 line_range_end=523 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L509-L523"}

:::task-stub{title="Finish RealmRepository cleanup in SubmissionsRepositoryImpl"}
1. Replace the remaining raw transaction entry points with the shared repository helper where possible.
2. Keep submission deletion, photo creation, and answer persistence behavior unchanged.
3. Limit the PR to write-path standardization only.
:::

### Switch storage detail rows to ListAdapter diffs
`StorageCategoryDetailFragment` keeps a mutable list and calls `notifyDataSetChanged()` for both full loads and select-all changes. This bottom sheet is a good low-conflict target for `ListAdapter` plus `DiffUtils.itemCallback`, which the repo already uses widely.

:codex-file-citation[codex-file-citation]{line_range_start=106 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L106-L114"}
:codex-file-citation[codex-file-citation]{line_range_start=141 line_range_end=153 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L141-L153"}
:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=273 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L248-L273"}
:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L5-L18"}

:::task-stub{title="Convert storage detail list to ListAdapter"}
1. Replace the inner raw adapter with a `ListAdapter` backed by `DiffUtils.itemCallback`.
2. Stop mutating list rows in place and submit new list snapshots for load and selection changes.
3. Keep the delete flow and bottom-sheet behavior unchanged.
:::

### Keep login team and member UI state out of the activity
`LoginActivity` already caches teams, but it still rebuilds the spinner adapter during dropdown setup and keeps member-list state inside the activity. A tiny ViewModel plus adapter reuse here is a granular performance win that also reduces activity bloat.

:codex-file-citation[codex-file-citation]{line_range_start=71 line_range_end=81 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L71-L81"}
:codex-file-citation[codex-file-citation]{line_range_start=219 line_range_end=223 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L219-L223"}
:codex-file-citation[codex-file-citation]{line_range_start=416 line_range_end=440 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L416-L440"}
:codex-file-citation[codex-file-citation]{line_range_start=542 line_range_end=560 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L542-L560"}

:::task-stub{title="Add a small LoginActivity state holder"}
1. Introduce a tiny ViewModel to own cached teams, selected-team members, and loading state across recreation.
2. Reuse the existing spinner and saved-user adapters instead of recreating them during refreshes.
3. Keep selection persistence, autologin, and saved-user behavior unchanged.
:::

### Move life list mutations behind a small ViewModel
`LifeFragment` performs list loading and repository writes directly from the UI layer, including explicit `Dispatchers.IO` hops. Moving just this screen onto a tiny ViewModel is a low-risk way to expand ViewModel usage while trimming UI threading noise.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L48-L95"}
:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=76 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L73-L76"}
:codex-file-citation[codex-file-citation]{line_range_start=102 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L102-L105"}
:codex-file-citation[codex-file-citation]{line_range_start=164 line_range_end=186 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L164-L186"}

:::task-stub{title="Add a focused LifeViewModel"}
1. Add a small ViewModel that owns the life list state and update actions for this screen.
2. Route visibility toggles and reorder writes through that ViewModel so the fragment stops managing IO dispatch directly.
3. Keep drag-and-drop behavior, toast messages, and current UI layout unchanged.
:::

### Replace feedback realtime listeners with flow collection
`FeedbackListFragment` still uses the older `RealtimeSyncManager.addListener/removeListener` path even though the repo already has a flow-based `RealtimeSyncHelper`. Migrating this fragment is isolated, reduces manual listener lifecycle work, and aligns it with the newer observer model.

:codex-file-citation[codex-file-citation]{line_range_start=76 line_range_end=91 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L76-L91"}
:codex-file-citation[codex-file-citation]{line_range_start=129 line_range_end=172 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt#L129-L172"}
:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L22-L41"}

:::task-stub{title="Migrate FeedbackListFragment to RealtimeSyncHelper"}
1. Replace the manual realtime listener registration with the existing flow-based helper.
2. Keep the current feedback refresh trigger and sync-status UI unchanged.
3. Remove the fragment-specific listener cleanup once the flow path is in place.
:::

### Replace chat history realtime listeners with flow collection
`ChatHistoryFragment` has the same legacy listener pattern as feedback. Converting it separately keeps the PR small and reviewable while removing another manual long-lived listener from the UI.

:codex-file-citation[codex-file-citation]{line_range_start=375 line_range_end=390 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L375-L390"}
:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L22-L41"}

:::task-stub{title="Migrate ChatHistoryFragment to RealtimeSyncHelper"}
1. Replace manual realtime listener wiring with the existing flow-based sync helper.
2. Keep chat-history refresh timing and search behavior unchanged.
3. Remove the fragment-specific listener teardown after the migration.
:::

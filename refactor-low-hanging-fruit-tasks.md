### Stop double-loading notifications on first render
The notifications screen currently triggers its first load twice: once from the spinner selection callback and again immediately after wiring the spinner. Removing the duplicate startup fetch is a very small change that cuts redundant repository work and adapter updates on every first render.

:codex-file-citation[codex-file-citation]{line_range_start=72 line_range_end=85 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt#L72-L85"}

:::task-stub{title="Remove the duplicate initial notifications fetch"}
1. Keep exactly one startup path responsible for the first `loadNotifications` call.
2. Leave spinner selection changes responsible only for later filter switches.
3. Verify the empty state and unread badge still update from the single load path.
:::

### Finish notification lookup batching
`loadNotifications` already batches task and join-request identifiers, but formatting still falls back to per-item repository calls when batch maps miss. Closing that gap removes an obvious N+1 pattern without changing screen structure.

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L36-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=93 line_range_end=156 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L93-L156"}

:::task-stub{title="Make notification formatting fully batch-backed"}
1. Precompute every task-team and join-request lookup needed for the current payload before mapping UI models.
2. Make `formatNotification` and `formatTaskNotification` pure formatting helpers with no repository I/O.
3. Preserve existing text output and fallback behavior for unknown or missing related records.
:::

### Parallelize dashboard challenge prefetch
The dashboard challenge evaluation waits on several independent repository reads one after another before deciding whether to emit the dialog event. Running those reads together is a contained ViewModel-only quick win that reduces latency before any larger dashboard refactor.

:codex-file-citation[codex-file-citation]{line_range_start=313 line_range_end=337 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L313-L337"}

:::task-stub{title="Run independent challenge-dialog reads concurrently"}
1. Group the independent course, voice, and title lookups into one concurrent fetch block on the IO dispatcher.
2. Keep the sync-completion check conditional on the prerequisite gate that already exists.
3. Verify the emitted dialog payload stays identical for both prompt and no-prompt cases.
:::

### Reuse course metadata during filtering
`loadCourses` fetches ratings, progress, and tags, then `filterCourses` refetches ratings and progress again even though only the visible subset changed. Reusing cached metadata inside the ViewModel is a low-risk performance win that also makes later data-layer cleanup easier.

:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=167 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L121-L167"}

:::task-stub{title="Cache ratings progress and tags across course filtering"}
1. Store the latest ratings, progress, and tag metadata after the full course load completes.
2. Reuse that cached metadata during filter-only updates instead of refetching it on every search or facet change.
3. Invalidate or rebuild the cache only when the underlying course dataset is reloaded from storage.
:::

### Collapse duplicate username listeners in login
The login screen attaches one watcher that rewrites the username field to lowercase and a second callback that validates the same field afterward. Consolidating those responsibilities into one listener removes duplicate text events and avoids extra `setText` churn on every keystroke.

:codex-file-citation[codex-file-citation]{line_range_start=350 line_range_end=378 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L350-L378"}
:codex-file-citation[codex-file-citation]{line_range_start=743 line_range_end=751 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L743-L751"}

:::task-stub{title="Merge username normalization and validation into one watcher"}
1. Keep a single listener path for username normalization, validation, and sign-in button state updates.
2. Avoid self-triggered text rewrite loops when lowercasing input.
3. Keep the existing cleanup path removing only the listeners that remain in use.
:::

### Align course write helpers with RealmRepository
`RealmRepository` already exposes shared Realm access and transaction helpers, but the course membership write paths still wrap manual `withContext(databaseService.ioDispatcher)` blocks around custom transaction code. Standardizing these small mutations on the shared base keeps dispatcher rules consistent and trims duplication before deeper data-layer work.

:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L179-L199"}
:codex-file-citation[codex-file-citation]{line_range_start=169 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L169-L208"}
:codex-file-citation[codex-file-citation]{line_range_start=344 line_range_end=384 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L344-L384"}

:::task-stub{title="Move course membership writes onto RealmRepository helpers"}
1. Route course add, join, and leave mutations through the shared RealmRepository transaction path.
2. Preserve chunking, removed-log cleanup, and realtime notification behavior exactly as today.
3. Remove redundant dispatcher wrapping once the shared repository helper owns the transaction lifecycle.
:::

### Align resource bulk writes with RealmRepository
`addResourcesToUserLibrary` repeats the same manual dispatcher-plus-transaction pattern even though the repository already inherits common Realm helpers. Converging this hot bulk update path on the shared base reduces duplication and makes later sync cleanup safer.

:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L179-L199"}
:codex-file-citation[codex-file-citation]{line_range_start=414 line_range_end=441 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L414-L441"}

:::task-stub{title="Move resource library bulk-add writes onto RealmRepository helpers"}
1. Let the shared repository helper own the transaction boundary for resource-library bulk adds.
2. Keep the chunked updates and removed-log cleanup exactly as they work now.
3. Leave repository inputs outputs and sync side effects unchanged so the review stays isolated.
:::

### Align team course mutations with RealmRepository
`TeamsRepositoryImpl` still handles `addCoursesToTeam` with the same hand-rolled dispatcher and transaction pattern seen elsewhere. Cleaning up this one write path is small, low-conflict, and directly supports the broader data-layer cleanup goal.

:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L179-L199"}
:codex-file-citation[codex-file-citation]{line_range_start=364 line_range_end=385 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L364-L385"}

:::task-stub{title="Move team course writes onto the shared RealmRepository path"}
1. Refactor `addCoursesToTeam` to use the repository base transaction helper.
2. Preserve the duplicate-course guard and `updated` flag semantics.
3. Keep the change limited to this mutation path so it can merge independently from other team work.
:::

### Modernize resource list diff and selection bookkeeping
The resource list still uses an inline `DiffUtil.ItemCallback` and tracks selected rows in a mutable list, so every bind and checkbox toggle pays repeated linear membership checks. Switching to `DiffUtils.itemCallback` plus a keyed selection set is a localized performance win that also aligns the adapter with the project’s existing list-diff utility.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L5-L18"}
:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L27-L43"}
:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=145 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L80-L145"}

:::task-stub{title="Adopt shared diff utilities and O(1) selection tracking in ResourcesAdapter"}
1. Replace the inline adapter diff callback with `DiffUtils.itemCallback`.
2. Store selection state by stable resource identifier instead of scanning a mutable list on every bind.
3. Keep the existing payload-based partial refresh behavior for selection rating and opened-state updates.
:::

### Contain AutoSyncWorker background work to the worker scope
The worker returns success quickly, then later launches more work on `MainApplication.applicationScope` with raw `Dispatchers.Main` and `Dispatchers.IO`. Moving that follow-on work back under the worker’s own coroutine lifecycle is a high-value cleanup because it improves cancellation, avoids runaway work, and sets up the larger sync/upload refactor with less risk.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L43-L57"}
:codex-file-citation[codex-file-citation]{line_range_start=72 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L72-L126"}

:::task-stub{title="Replace global AutoSyncWorker launches with worker-owned coroutine flow"}
1. Keep version checks sync work and uploads inside the worker-owned coroutine scope so cancellation is respected.
2. Replace raw dispatcher usage with injected dispatcher access for any background hops that still need explicit context.
3. Ensure the worker result is reported only after the work it starts has either finished or been intentionally delegated.
:::

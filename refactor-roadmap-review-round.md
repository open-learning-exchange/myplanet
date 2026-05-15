### Move dictionary persistence out of the activity
`DictionaryActivity` still owns Realm reads, file parsing, and initial inserts, so the UI is acting as both screen and data layer. This is a small, isolated place to reinforce repository boundaries and make later data-layer cleanup easier.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L39-L60"}

:codex-file-citation[codex-file-citation]{line_range_start=75 line_range_end=129 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L75-L129"}

:::task-stub{title="Extract dictionary reads and writes into a repository"}
1. Add a small dictionary repository backed by `RealmRepository` for count, initial import, and word lookup.
2. Move the file-to-Realm import path out of `DictionaryActivity` one function at a time, keeping the existing screen behavior unchanged.
3. Leave the activity responsible only for triggering load, rendering the result, and showing the empty or not-found states.
:::

### Replace dashboard sync logging's direct database access
`DashboardElementActivity` injects `DatabaseService` only to record a sync action, which leaks write logic into the UI layer. This is a low-conflict boundary fix because the call site is narrow and already maps well to `ActivitiesRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L19-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=119 line_range_end=142 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L119-L142"}

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L7-L15"}

:::task-stub{title="Route dashboard sync activity logging through ActivitiesRepository"}
1. Add a repository method for the sync action currently created from the activity.
2. Remove `DatabaseService` from `DashboardElementActivity` once the repository call exists.
3. Keep logout and sync navigation behavior unchanged while tightening the UI-to-data boundary.
:::

### Move resource list assembly behind the repository boundary
`ResourcesViewModel` is doing cross-feature assembly by loading libraries, filtering privacy rules, then joining ratings and tags itself. That makes the ViewModel a data combiner instead of a screen state holder and is a good low-hanging fruit for repository tightening.

:codex-file-citation[codex-file-citation]{line_range_start=72 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L72-L105"}

:codex-file-citation[codex-file-citation]{line_range_start=83 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L83-L86"}

:::task-stub{title="Push resource list enrichment into ResourcesRepository"}
1. Introduce one repository method that returns the already-enriched resource list model needed by the screen.
2. Move the privacy filtering, ratings lookup, and tags lookup out of the ViewModel without widening the UI API.
3. Keep the ViewModel focused on sync state and screen events instead of cross-feature joins.
:::

### Collapse team membership side effects into the repository
`TeamViewModel` owns long-running collectors, wraps repository calls in extra dispatcher hops, and manually chains `requestToJoin` or `leaveTeam` with `syncTeamActivities`. This is a good granular fix because the affected flows are already centralized in one ViewModel and one repository.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L37-L42"}

:codex-file-citation[codex-file-citation]{line_range_start=53 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L53-L77"}

:codex-file-citation[codex-file-citation]{line_range_start=96 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L96-L126"}

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L73-L80"}

:::task-stub{title="Make TeamsRepository own membership updates and observer-safe refreshes"}
1. Add repository-level operations for join and leave flows that also handle the team activity sync they currently require.
2. Separate the task observer job from the team list observer job so repeated screen actions cannot accumulate collectors.
3. Remove redundant `withContext` wrapping from the ViewModel where repository methods already define their own threading.
:::

### Tighten team voices so it stops pulling data from three feature repositories
`TeamsVoicesViewModel` and `VoicesAdapter` both reach across feature boundaries for team leadership, user lookup, and library lookup. That makes the team voices screen depend on Voices, Teams, Users, and Resources details at once, which is exactly the kind of cross-feature data leak we want to reduce.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt#L24-L95"}

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L56-L75"}

:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=149 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L146-L149"}

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt#L25-L39"}

:::task-stub{title="Reduce cross-feature lookups in the team voices flow"}
1. Move team-discussion-specific metadata access behind a tighter repository contract instead of exposing separate user, team, and library lookups to the screen.
2. Remove adapter-level repository knowledge that is only needed to render the discussion row state.
3. Keep the ViewModel responsible for screen state and events while repository methods provide the required discussion data.
:::

### Stop building course detail view data inside the ViewModel
`CourseDetailViewModel` listens to a course flow and then performs extra repository joins for steps, exams, resources, ratings, and current user data inside the collector. This is a classic low-risk extraction target because the screen needs one composite payload, not five separate data concerns.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=109 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt#L54-L109"}

:codex-file-citation[codex-file-citation]{line_range_start=122 line_range_end=149 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt#L122-L149"}

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L24-L31"}

:::task-stub{title="Have repositories provide a course detail payload instead of assembling it in the ViewModel"}
1. Introduce a small repository-facing aggregate for course details, step counts, and rating summary.
2. Move the expensive joins and dispatcher work out of the flow collector so the ViewModel only reacts to one course-detail result.
3. Keep ratings refresh aligned with the same repository contract so the screen updates through one path.
:::

### Split the oversized team repository into narrower sync and screen contracts
`TeamsRepository` exposes UI queries, membership actions, report helpers, upload serializers, and sync bulk-insert methods all through one interface. That broad surface leaks sync concerns into regular feature code and raises merge-conflict risk whenever unrelated team work lands together.

:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=146 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L45-L146"}

:codex-file-citation[codex-file-citation]{line_range_start=240 line_range_end=252 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L240-L252"}

:codex-file-citation[codex-file-citation]{line_range_start=968 line_range_end=973 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L968-L973"}

:codex-file-citation[codex-file-citation]{line_range_start=134 line_range_end=148 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L134-L148"}

:::task-stub{title="Extract team sync and upload interfaces and bind them cleanly in DI"}
1. Carve the sync-only and upload-only team methods out of the UI-facing repository contract.
2. Rebind the new interfaces in Hilt so sync services depend on the narrower contracts they actually use.
3. Leave existing screen calls on the smaller `TeamsRepository` surface to reduce future cross-feature conflicts.
:::

### Route resource uploads through the shared upload pipeline
`UploadManager.uploadResource` still performs its own batching, API posting, local update bookkeeping, and attachment follow-up instead of leaning on the shared upload pipeline. That duplicates workflow logic already living in `UploadCoordinator` and increases the chance of upload behavior drifting across features.

:codex-file-citation[codex-file-citation]{line_range_start=201 line_range_end=289 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L201-L289"}

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt#L34-L86"}

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt#L62-L69"}

:::task-stub{title="Migrate resource upload to UploadCoordinator and clean up the upload DI slice"}
1. Move the main resource upload batch path onto `UploadCoordinator` so upload batching and retry rules are shared.
2. Leave only resource-specific post-upload work in `UploadManager`, such as attachment follow-up that is not yet generic.
3. Normalize the upload repository binding while making this change so the repository lives in the repository layer, not as an ad hoc service provider.
:::

### Push user shelf-sync helpers down into UserSyncRepository
`UploadToShelfService` still owns user existence checks, user creation, user updates, key and IV writes, and health side effects even though `UserSyncRepository` already exists. This is a good incremental cleanup because the service can stay as the orchestrator while the data mutations move behind a narrower repository boundary.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L56-L90"}

:codex-file-citation[codex-file-citation]{line_range_start=122 line_range_end=223 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L122-L223"}

:codex-file-citation[codex-file-citation]{line_range_start=226 line_range_end=273 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L226-L273"}

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt#L7-L12"}

:::task-stub{title="Move user upload and security sync primitives into UserSyncRepository"}
1. Extend `UserSyncRepository` to own user existence, create, update, and key or IV persistence concerns.
2. Keep `UploadToShelfService` as the coordinator that selects users and reports status back to the caller.
3. Migrate one user-sync function at a time so the review stays small and the upload flow remains stable after each PR.
:::

### Convert the storage cleanup sheet to a diff-based adapter
`StorageCategoryDetailFragment` uses a manual inner adapter and full `notifyDataSetChanged()` refreshes for selection and reloads. This is an easy reviewable UI health improvement that also aligns with the shared `DiffUtils.itemCallback` helper already used elsewhere.

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=52 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L51-L52"}

:codex-file-citation[codex-file-citation]{line_range_start=106 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L106-L114"}

:codex-file-citation[codex-file-citation]{line_range_start=150 line_range_end=153 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L150-L153"}

:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=273 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L248-L273"}

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L5-L18"}

:::task-stub{title="Replace the storage detail adapter with ListAdapter and shared DiffUtils"}
1. Convert the inner adapter to a `ListAdapter` that uses `DiffUtils.itemCallback`.
2. Replace full refresh calls with list submission and payload-based selection updates.
3. Keep the bottom-sheet behavior and deletion flow unchanged while reducing UI churn and future conflict risk.
:::

### Move DictionaryActivity's direct Realm access into a new DictionaryRepository
DictionaryActivity is the last UI class that injects DatabaseService and runs raw `realm.where(...)` queries and `executeTransactionAsync` imports directly in the activity. Dictionary is also the only data domain without a repository, so this is the clearest remaining repository-boundary violation in the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L28-L33"}

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60-L107"}

:codex-file-citation[codex-file-citation]{line_range_start=109 line_range_end=135 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L109-L135"}

:::task-stub{title="Create DictionaryRepository and remove DatabaseService from DictionaryActivity"}
1. Add `DictionaryRepository` interface in `repository/` with `getDictionaryCount(): Long`, `isDictionaryEmpty(): Boolean`, `importDictionary(json: JsonArray)`, and `searchWord(word: String): RealmDictionary?`.
2. Add `DictionaryRepositoryImpl` extending `RealmRepository`, moving the count, JSON-import transaction, and case-insensitive word search out of `DictionaryActivity` (reuse `count`/`findByField`/`executeTransaction` helpers from the base).
3. Bind the pair in `di/RepositoryModule.kt` next to the other repositories.
4. In `DictionaryActivity`, replace the `databaseService` field with `@Inject lateinit var dictionaryRepository: DictionaryRepository`, call the new suspend methods from the existing `lifecycleScope` blocks, and drop the `DatabaseService`, `RealmDictionary`, and `io.realm.Case` imports.
:::

### Stop NotificationsRepositoryImpl from querying RealmMyTeam directly
NotificationsRepositoryImpl reaches into the teams domain with nine direct `RealmMyTeam` queries (team lookups, join-request lookups, team-name maps), duplicating logic TeamsRepository already owns. It already delegates user lookups via `dagger.Lazy<UserRepository>`, so the same pattern can tighten the teams boundary.

:codex-file-citation[codex-file-citation]{line_range_start=176 line_range_end=217 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L176-L217"}

:codex-file-citation[codex-file-citation]{line_range_start=232 line_range_end=242 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L232-L242"}

:::task-stub{title="Delegate team lookups in NotificationsRepositoryImpl to TeamsRepository"}
1. Inject `dagger.Lazy<TeamsRepository>` into `NotificationsRepositoryImpl`, mirroring the existing `userRepository` Lazy injection.
2. Replace `findByField(RealmMyTeam::class.java, "_id", teamId)` calls with the existing `TeamsRepository.getTeamById`.
3. Add narrow methods to `TeamsRepository`/`TeamsRepositoryImpl` only where no equivalent exists (e.g. `getJoinRequestById(id: String?): RealmMyTeam?` and `getTeamNamesByIds(ids: List<String>): Map<String, String>`) and use them for the join-request and team-name-map queries.
4. Remove the `RealmMyTeam` import and all direct `queryList(RealmMyTeam::class.java)` blocks from `NotificationsRepositoryImpl`.
:::

### Delegate TeamsRepositoryImpl's library queries to ResourcesRepository
TeamsRepositoryImpl queries `RealmMyLibrary` directly in `getTeamResources` and `getAvailableResourcesToAdd`, leaking the resources domain into the teams repository. ResourcesRepository already exposes `getLibraryItemsByIds`, so only two small lookup methods are missing to close the gap.

:codex-file-citation[codex-file-citation]{line_range_start=340 line_range_end=357 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L340-L357"}

:codex-file-citation[codex-file-citation]{line_range_start=1439 line_range_end=1448 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1439-L1448"}

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L38"}

:::task-stub{title="Replace direct RealmMyLibrary queries in TeamsRepositoryImpl with ResourcesRepository calls"}
1. Add `getLibraryItemsByResourceIds(ids: Collection<String>)`, `getTeamPrivateResources(teamId: String)`, and `getPublicLibraryItems()` to `ResourcesRepository`/`ResourcesRepositoryImpl` (thin wrappers over existing `queryList` patterns).
2. Inject `dagger.Lazy<ResourcesRepository>` into `TeamsRepositoryImpl` (one-directional dependency; ResourcesRepositoryImpl must not inject TeamsRepository back).
3. Rewrite `getTeamResources` to combine `getLibraryItemsByResourceIds(getResourceIds(teamId))` with `getTeamPrivateResources(teamId)`.
4. Rewrite `getAvailableResourcesToAdd` to filter `getPublicLibraryItems()` against the existing team resource ids, then remove the `RealmMyLibrary` import from `TeamsRepositoryImpl`.
:::

### Remove the dead DatabaseService write helper from the RealmUserChallengeActions model
The model class carries a `createActionAsync` companion that takes a `DatabaseService` and writes to Realm, but it has zero callers — all live challenge-action queries already go through repositories. A model importing the data layer is a boundary violation, and this one can simply be deleted.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUserChallengeActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmUserChallengeActions.kt#L6"}

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUserChallengeActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmUserChallengeActions.kt#L16-L34"}

:::task-stub{title="Delete unused createActionAsync companion from RealmUserChallengeActions"}
1. Confirm with a project-wide search that `createActionAsync` has no callers.
2. Delete the companion object block and the `org.ole.planet.myplanet.data.DatabaseService` import from `RealmUserChallengeActions.kt`.
3. Build both flavors to confirm nothing referenced it.
:::

### Drop the unused DatabaseService dependency from UploadToShelfService
UploadToShelfService takes `dbService: DatabaseService` in its constructor but never uses it — all data access already flows through repositories. Removing it shrinks the DI graph and prevents the dead parameter from inviting raw Realm access back into the service layer.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L18"}

:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L33"}

:::task-stub{title="Remove unused DatabaseService constructor parameter from UploadToShelfService"}
1. Verify `dbService` is referenced nowhere in `UploadToShelfService` beyond the constructor declaration.
2. Remove the constructor parameter and the `DatabaseService` import; Hilt construction needs no other change.
3. Build both flavors to confirm the injection graph still resolves.
:::

### Move news-conversation parsing out of ChatDetailFragment into ChatViewModel
ChatDetailFragment parses conversation JSON and builds the first page on a hardcoded `Dispatchers.IO` inside the fragment, while a ChatViewModel with an injected DispatcherProvider already exists for this screen. Moving the parsing/pagination into the ViewModel keeps data work out of the view and removes a hardcoded dispatcher.

:codex-file-citation[codex-file-citation]{line_range_start=338 line_range_end=364 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt#L338-L364"}

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=24 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L24"}

:::task-stub{title="Move conversation parsing and paging from ChatDetailFragment to ChatViewModel"}
1. Add a suspend function on `ChatViewModel` that parses the `newsConversations` JSON and returns the initial page, running on `dispatcherProvider.io`.
2. Move the `allConversations`/`loadedCount` pagination state and the `buildInitialPage` logic from the fragment into the ViewModel.
3. In `loadNewsConversations`, call the ViewModel function from the existing `lifecycleScope` block and keep only `submitList`, scrolling, and progress-dialog handling in the fragment.
4. Remove the `Dispatchers` import from `ChatDetailFragment` if it is now unused.
:::

### Route ResourceViewerFragment's repository access through a ViewModel
ResourceViewerFragment injects PersonalsRepository and ResourcesRepository directly and performs library lookups/updates from the fragment; the viewer package has no ViewModel at all. A slim @HiltViewModel keeps the data calls off the view and matches the pattern used by the other 16+ screens.

:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=110 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt#L103-L110"}

:codex-file-citation[codex-file-citation]{line_range_start=126 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt#L126"}

:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=179 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt#L179"}

:::task-stub{title="Add ResourceViewerViewModel and move repository calls out of ResourceViewerFragment"}
1. Create `ResourceViewerViewModel` (`@HiltViewModel`) injecting `PersonalsRepository`, `ResourcesRepository`, and `DispatcherProvider`.
2. Expose suspend functions for the existing calls (`getLibraryItemById`, `updateLibraryItem` for the translation audio path, and the personals lookups) and move that logic into the ViewModel.
3. Replace the fragment's `@Inject` repository fields with `by viewModels()` and call the ViewModel from the existing coroutine launch sites.
4. Keep the change mechanical — no new state holders or LiveData/Flow plumbing beyond what the calls need.
:::

### Inject dispatchers into InlineResourceAdapter instead of per-ViewHolder hardcoded scopes
Each ViewHolder in InlineResourceAdapter creates its own `CoroutineScope(Dispatchers.Main + SupervisorJob())` and the preview loaders hardcode `Dispatchers.IO` four times, bypassing the project's DispatcherProvider and leaving scopes alive when the RecyclerView detaches. The host CourseStepFragment can pass the provider in.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L59-L67"}

:codex-file-citation[codex-file-citation]{line_range_start=189 line_range_end=215 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L189-L215"}

:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=187 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt#L187"}

:::task-stub{title="Pass DispatcherProvider into InlineResourceAdapter and cancel preview scopes on detach"}
1. Add a `DispatcherProvider` constructor parameter to `InlineResourceAdapter` and pass it from `CourseStepFragment` where the adapter is created.
2. Build each ViewHolder scope from `dispatcherProvider.main + SupervisorJob()` and replace the four `withContext(Dispatchers.IO)` preview blocks with `dispatcherProvider.io`.
3. Override `onDetachedFromRecyclerView` to cancel outstanding preview jobs so no scope outlives the list.
4. Remove the direct `kotlinx.coroutines.Dispatchers` import.
:::

### Replace remaining hardcoded Dispatchers in activities and AuthUtils with DispatcherProvider
DashboardElementActivity, BasePermissionActivity, and AuthUtils still call `withContext(Dispatchers.IO)` directly even though the project provides an injectable DispatcherProvider (used everywhere else, e.g. DictionaryActivity). These are the last injectable call sites with hardcoded dispatchers in the UI/base layer.

:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=146 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L146"}

:codex-file-citation[codex-file-citation]{line_range_start=436 line_range_end=443 path=app/src/main/java/org/ole/planet/myplanet/base/BasePermissionActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BasePermissionActivity.kt#L436-L443"}

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/utils/AuthUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/AuthUtils.kt#L25-L27"}

:::task-stub{title="Swap hardcoded Dispatchers.IO for DispatcherProvider in DashboardElementActivity, BasePermissionActivity, and AuthUtils"}
1. Inject (or reuse an inherited) `DispatcherProvider` in `DashboardElementActivity` and replace `withContext(Dispatchers.IO)` at the credentials-clear call with `dispatcherProvider.io`.
2. Do the same for the two `withContext(Dispatchers.IO)` blocks in `BasePermissionActivity`.
3. Change `AuthUtils.login` to accept an `ioDispatcher: CoroutineDispatcher` parameter supplied by `LoginActivity`'s injected provider and use it for the `SecurePrefs.saveCredentials` block.
4. Remove the now-unused `Dispatchers` imports from all three files.
:::

### Consolidate challenge-action queries under a single owning repository
RealmUserChallengeActions is queried independently by UserRepositoryImpl, ActivitiesRepositoryImpl, and ProgressRepositoryImpl, so the same domain has three owners and three slightly different query shapes. Tightening this to one owner with narrow delegate methods prevents the duplicated queries from drifting apart.

:codex-file-citation[codex-file-citation]{line_range_start=1100 line_range_end=1100 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L1100"}

:codex-file-citation[codex-file-citation]{line_range_start=243 line_range_end=243 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L243"}

:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=248 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L248"}

:::task-stub{title="Make ActivitiesRepository the single owner of RealmUserChallengeActions queries"}
1. Add narrow methods to `ActivitiesRepository`/`ActivitiesRepositoryImpl` covering the three existing query shapes (e.g. `getChallengeActions(userId)`, `countChallengeActions(userId, actionType)`).
2. Inject `dagger.Lazy<ActivitiesRepository>` into `UserRepositoryImpl` and `ProgressRepositoryImpl` and replace their direct `RealmUserChallengeActions` queries with the new methods.
3. Remove the `RealmUserChallengeActions` imports from the two delegating repositories.
4. Keep query semantics identical — this is a relocation, not a behavior change.
:::

### Clean up ServerAddressAdapter: static app context, dead constant, full-item selection rebinds
ServerAddressAdapter pulls the static `MainApplication.context` for string lookup instead of the view's own context, carries an unused `URL_PROTOCOL_REGEX` companion, and rebinds whole items via bare `notifyItemChanged` for what is only a selection-highlight toggle, despite already using `DiffUtils.itemCallback`.

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt#L10"}

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt#L25-L57"}

:codex-file-citation[codex-file-citation]{line_range_start=102 line_range_end=104 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt#L102-L104"}

:::task-stub{title="Use view context, payload-based selection, and remove dead code in ServerAddressAdapter"}
1. Replace the `MainApplication.context.getString(...)` call in the ViewHolder with `itemView.context.getString(...)` and drop the static import.
2. Delete the unused `URL_PROTOCOL_REGEX` companion object.
3. Pass a `SELECTION_PAYLOAD` constant to the `notifyItemChanged` calls in `setSelectedPosition`/`revertSelection`/`clearSelection` and handle it in an `onBindViewHolder(holder, position, payloads)` override that only toggles the button's selected state (mirroring `ResourcesAdapter`'s payload pattern).
:::

### Testing
- ⚠️ Not run (planning report only). Each task should be verified with `./gradlew assembleDefaultDebug` and `./gradlew assembleLiteDebug`; no unit test framework is configured in this repo.

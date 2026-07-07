### Move dictionary persistence into a repository
DictionaryActivity still imports, counts, and searches dictionary rows through DatabaseService, so the screen owns write logic that should live behind a repository boundary. This is a small, isolated move that reinforces the data layer without touching unrelated features.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L37-L39"}

:codex-file-citation[codex-file-citation]{line_range_start=105 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L105-L159"}

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L22-L49"}

:::task-stub{title="Create DictionaryRepository and remove direct Realm work from DictionaryActivity"}
1. Add a small dictionary repository backed by RealmRepository for count, import, and exact-word lookup operations.
2. Move the JSON-to-Realm dictionary import logic out of the activity and into repository methods.
3. Update DictionaryActivity to consume repository calls only and drop its DatabaseService dependency.
:::

### Move saved-user preference CRUD behind UserRepository
LoginViewModel currently owns saved-user preference reads and writes through SharedPrefManager, which mixes login UI flow with persistence details. Pulling those operations behind UserRepository gives one home for user storage rules and keeps the screen layer narrower.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt#L19-L23"}

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt#L60-L127"}

:codex-file-citation[codex-file-citation]{line_range_start=78 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt#L78-L90"}

:::task-stub{title="Hide saved login users behind UserRepository"}
1. Add repository methods for loading, saving, replacing, and removing saved login users.
2. Move the duplicate-user handling rules out of LoginViewModel and into the repository implementation.
3. Update LoginViewModel to use repository calls instead of SharedPrefManager for saved-user persistence.
:::

### Stop RatingsFragment from pulling user IDs from SharedPrefManager
RatingsFragment still reaches into SharedPrefManager for the active user ID even though RatingsViewModel already depends on UserRepository. This is a clean, low-conflict place to move one more user lookup out of the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt#L28-L30"}

:codex-file-citation[codex-file-citation]{line_range_start=142 line_range_end=173 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt#L142-L173"}

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L19-L23"}

:::task-stub{title="Move rating user identity lookup into RatingsViewModel"}
1. Add a ViewModel-level path for loading rating data and submitting ratings without the fragment supplying a preference-derived user ID.
2. Resolve the active user through repository or session-facing code inside the ViewModel layer.
3. Remove SharedPrefManager from RatingsFragment once the fragment only passes rating form values.
:::

### Stop UserProfileFragment from pulling user IDs from SharedPrefManager
UserProfileFragment reads the current user ID in multiple places for load, update, and image-change actions, so user identity is still leaking into the UI layer. Centralizing that lookup in UserProfileViewModel keeps profile rules in one place and reduces repeated screen logic.

:codex-file-citation[codex-file-citation]{line_range_start=75 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L75-L78"}

:codex-file-citation[codex-file-citation]{line_range_start=145 line_range_end=151 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L145-L151"}

:codex-file-citation[codex-file-citation]{line_range_start=402 line_range_end=421 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L402-L421"}

:codex-file-citation[codex-file-citation]{line_range_start=554 line_range_end=556 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L554-L556"}

:::task-stub{title="Move profile user identity handling into UserProfileViewModel"}
1. Add ViewModel entry points that operate on the current profile user without requiring the fragment to read SharedPrefManager.
2. Consolidate load, profile update, and image update user-ID resolution inside the ViewModel or repository layer.
3. Remove SharedPrefManager from UserProfileFragment after the fragment only forwards form inputs and media results.
:::

### Run login team loads on background dispatchers
The repository layer already returns detached Realm copies, but LoginViewModel still keeps team and member loads on the main dispatcher because of an outdated Realm safety assumption. Fixing this is a very small threading cleanup that lowers UI blocking risk and aligns with the repository contract.

:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=100 path=app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt#L81-L100"}

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt#L38-L57"}

:::task-stub{title="Move LoginViewModel repository reads off the main dispatcher"}
1. Update LoginViewModel team and member loading to run repository reads on injected background dispatchers.
2. Keep only state publication on the main thread and remove the Realm-specific comments that no longer match the repository behavior.
3. Verify the login screen still publishes detached lists without changing feature behavior.
:::

### Remove SharedPrefManager from ConfigurationsRepository.checkVersion
ConfigurationsRepository already injects SharedPrefManager, but its public API still requires callers to pass the same dependency back in. That leaks storage details through the repository boundary and forces UI and worker code to carry an implementation detail they should not know about.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt#L6-L13"}

:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L45-L55"}

:codex-file-citation[codex-file-citation]{line_range_start=94 line_range_end=101 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L94-L101"}

:codex-file-citation[codex-file-citation]{line_range_start=68 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L68-L71"}

:::task-stub{title="Make checkVersion a self-contained repository call"}
1. Remove the SharedPrefManager parameter from the repository interface and implementation.
2. Update the repository to use its injected preference dependency internally for version-check inputs and caching.
3. Adjust worker and activity call sites so they only pass the callback object.
:::

### Split TeamsRepository into feature-scoped interfaces
TeamsRepository is serving list browsing, membership, tasks, reports, finances, and creation flows through one very wide interface, and several unrelated ViewModels all depend on it directly. Slicing the interface without changing the implementation is a low-conflict way to tighten cross-feature boundaries and shrink future merge surfaces.

:codex-file-citation[codex-file-citation]{line_range_start=71 line_range_end=167 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L71-L167"}

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt#L26-L29"}

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L28-L32"}

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt#L26-L31"}

:::task-stub{title="Introduce narrower team-facing repository interfaces"}
1. Carve small read and action interfaces out of TeamsRepository for team browsing, membership requests, and enterprise reports.
2. Keep TeamsRepositoryImpl as the implementation but bind the narrower interfaces in Hilt.
3. Update each ViewModel to depend only on the methods it actually uses.
:::

### Make upload repository own upload contracts
UploadRepository currently imports UploadConfig and UploadedItem from the service layer, so the repository depends upward on orchestration types. Moving those contracts down to the repository side tightens the layer boundary before any larger upload cleanup.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt#L3-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfig.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfig.kt#L10-L33"}

:::task-stub{title="Re-home upload data contracts below the service layer"}
1. Define repository-owned upload query and upload-result contracts instead of importing service package types into UploadRepository.
2. Update UploadRepository and UploadRepositoryImpl to depend on the new lower-layer contracts.
3. Adjust UploadCoordinator and upload helpers to map into the repository-owned contracts at the boundary.
:::

### Move upload HTTP mutations behind UploadRepository
UploadCoordinator still performs post, put, conflict recovery, and response parsing directly through ApiInterface, so service orchestration and persistence mutation are still mixed together. Pushing those network mutations into UploadRepository is a focused next step that keeps the coordinator on batching and retry policy only.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt#L24-L30"}

:codex-file-citation[codex-file-citation]{line_range_start=148 line_range_end=249 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt#L148-L249"}

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt#L21-L30"}

:::task-stub{title="Make UploadCoordinator orchestration-only"}
1. Add repository methods for upload request execution, conflict resolution, and upload result normalization.
2. Move direct ApiInterface post, put, and 409 handling out of UploadCoordinator into UploadRepositoryImpl.
3. Leave UploadCoordinator responsible only for batching, retries, and final state aggregation.
:::

### Untangle voices observers and adapter side effects
The voices feature keeps long-running collectors alive in ViewModels and also lets VoicesAdapter reshape submitted lists while carrying data-facing callbacks. This is the clearest low-hanging spot to tighten lifecycle ownership and make the ListAdapter stay focused on UI diffing and binding.

:codex-file-citation[codex-file-citation]{line_range_start=31 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt#L31-L71"}

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt#L26-L54"}

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L51-L69"}

:codex-file-citation[codex-file-citation]{line_range_start=107 line_range_end=133 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L107-L133"}

:::task-stub{title="Make the voices feature lifecycle-bound and adapter-pure"}
1. Move community and team discussion observation ownership to lifecycle-bound collection paths instead of keeping always-on ViewModel jobs.
2. Stop VoicesAdapter from doing list shaping and data-facing work that belongs one layer up.
3. Keep the adapter as a pure ListAdapter that receives prepared UI data and continues to use DiffUtils.itemCallback for diffing.
:::

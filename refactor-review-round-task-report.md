### Extract dictionary persistence out of DictionaryActivity
DictionaryActivity still owns Realm reads, writes, and search behavior directly, which keeps data-layer details in the UI and bypasses the existing RealmRepository helpers. This is a small, isolated screen, so it is a good first PR for reinforcing repository boundaries with low merge-conflict risk.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L126"}

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L23-L199"}

:::task-stub{title="Add a dictionary repository backed by RealmRepository"}
1. Create a focused DictionaryRepository that owns dictionary count, initial import, and word lookup.
2. Move the Realm transaction and search logic out of DictionaryActivity into that repository one function at a time.
3. Leave DictionaryActivity responsible only for file availability, form events, and rendering results.
:::

### Move resource sync gating behind ResourcesRepository
ResourcesViewModel currently orchestrates SyncManager, SharedPrefManager, and ServerUrlMapper directly, so sync policy lives in the UI layer instead of at the repository boundary. This is a contained cleanup that removes service leakage without changing navigation or screen structure.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L18-L66"}

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L27-L86"}

:::task-stub{title="Hide resource sync policy behind a repository entrypoint"}
1. Add one repository-facing method that decides whether resource fast sync should run and triggers it.
2. Remove direct SyncManager, SharedPrefManager, and ServerUrlMapper coordination from ResourcesViewModel.
3. Keep ResourcesViewModel focused on exposing sync state and UI events only.
:::

### Move survey sync gating behind SurveysRepository
SurveysViewModel repeats the same service-layer sync orchestration pattern and mixes survey data loading with server reachability and preference checks. Pulling that policy into the repository layer gives a low-conflict cleanup that can be reviewed independently from survey UI behavior.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=215 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L29-L215"}

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt#L8-L31"}

:::task-stub{title="Hide survey sync policy behind a repository entrypoint"}
1. Add a repository method that encapsulates the fast-sync guard, server remap, and exam sync trigger.
2. Remove direct SyncManager, SharedPrefManager, and ServerUrlMapper dependencies from SurveysViewModel.
3. Keep SurveysViewModel centered on filtering, sorting, and presenting survey state.
:::

### Collapse local resource creation into ResourcesRepository
AddResourceActivity builds RealmMyLibrary, checks title uniqueness, updates the user library, and also triggers team activity sync, so one screen currently coordinates multiple data concerns across features. Moving that workflow into a single repository method is a low-hanging way to tighten boundaries and reduce future conflicts in resource and team screens.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt#L39-L45"}

:codex-file-citation[codex-file-citation]{line_range_start=143 line_range_end=203 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt#L143-L203"}

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L27-L86"}

:::task-stub{title="Create one repository workflow for adding a local resource"}
1. Add a repository method that owns local resource creation, duplicate-title handling, and user-library updates.
2. Move the private-team resource branch and its team-side effect behind repository-level APIs instead of coordinating it in the activity.
3. Leave AddResourceActivity responsible only for validation, form data capture, and success or error messaging.
:::

### Return team screen data from TeamsRepository
TeamViewModel still maps TeamSummary into TeamDetails, merges visit counts and membership state, and collects long-running flows inside IO blocks. This is a clear cross-feature leak because the view model is rebuilding team-domain data that should already be shaped by the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L39-L47"}

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L55-L199"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=72 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L47-L72"}

:::task-stub{title="Add repository-shaped team detail queries for the team screens"}
1. Introduce repository methods that return fully prepared TeamDetails-style data for listing and filtering.
2. Move long-running task and team collectors out of withContext blocks and keep collection lifetimes in normal viewModelScope flows.
3. Remove the visit-count and membership-status composition work from TeamViewModel once repository methods exist.
:::

### Pull profile stats out of UserSessionManager
UserProfileViewModel mixes user profile editing with profile analytics pulled from UserSessionManager, even though activity-style counts and last-visit data already live in repository code. Moving those reads behind repositories removes a service-layer leak and lets profile logic evolve without touching session internals.

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=138 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt#L23-L138"}

:codex-file-citation[codex-file-citation]{line_range_start=120 line_range_end=188 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L120-L188"}

:::task-stub{title="Move profile analytics reads behind repositories"}
1. Add repository-backed profile stat methods for last visit, offline visits, and most-opened resource data.
2. Remove direct UserSessionManager analytics calls from UserProfileViewModel.
3. Keep UserProfileViewModel focused on profile state, edits, and simple UI-derived formatting.
:::

### Shrink TeamsRepository to app-facing methods
TeamsRepository currently exposes raw Realm, Context, JsonArray, and sync insert helpers alongside normal app-facing operations, which makes the public contract leak persistence and sync internals. Trimming that surface is a low-conflict interface cleanup that reinforces repository boundaries before larger sync work lands.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=138 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L43-L138"}

:::task-stub{title="Split sync-only helpers away from the public teams repository"}
1. Remove raw Realm, Context, and bulk-insert sync helpers from the public TeamsRepository contract.
2. Keep only app-facing read and write methods on the public interface used by UI and view models.
3. Rehome sync-only insert and serialization helpers behind implementation-only collaborators or narrow internal interfaces.
:::

### Shrink UserRepository to app-facing methods
UserRepository has the same problem: its public interface mixes normal user queries with SharedPreferences-aware population, sync serialization, bulk inserts, and shelf payload assembly. Cleaning that contract is a small but high-value way to stop cross-layer data leaks before more user or sync work is reviewed.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=112 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L13-L112"}

:::task-stub{title="Split sync and persistence helpers away from the public user repository"}
1. Remove SharedPreferences, raw Realm, bulk-insert, and shelf-payload helpers from the public UserRepository contract.
2. Leave only app-facing identity, profile, auth, and upload-state methods exposed to UI and services.
3. Rehome the sync-only population and serialization helpers behind implementation-only collaborators or narrow internal interfaces.
:::

### Route activity upload through ActivitiesRepository
UploadManager still performs activity upload with direct ApiInterface calls and manual document assembly even though ActivitiesRepository already owns the activity data set and sync-state updates. This is a strong repository-boundary win because it consolidates one upload workflow without touching unrelated upload types.

:codex-file-citation[codex-file-citation]{line_range_start=88 line_range_end=134 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L88-L134"}

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=28 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L21-L28"}

:codex-file-citation[codex-file-citation]{line_range_start=191 line_range_end=218 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L191-L218"}

:::task-stub{title="Let ActivitiesRepository own the login and activity upload flow"}
1. Add one repository method that prepares pending activity payloads, performs upload work, and marks revisions on success.
2. Remove the direct ApiInterface-driven activity upload path from UploadManager once that repository method exists.
3. Keep this PR scoped to activity upload only so the larger upload stack does not conflict with unrelated work.
:::

### Standardize realtime list refresh around DiffUtils and ListAdapter
RealtimeSyncMixin falls back to resubmitting the current list, while some adapters still rely on broad notifyItemRangeChanged calls instead of leaning on ListAdapter plus DiffUtils.itemCallback. This is a clean, reviewable UI-health task that reduces unnecessary redraws without introducing new architecture.

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L47-L59"}

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L56-L60"}

:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=120 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L90-L120"}

:codex-file-citation[codex-file-citation]{line_range_start=144 line_range_end=148 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L144-L148"}

:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=201 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/aeabe32f27e7e4856a641f00b7020743203b0b83/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L187-L201"}

:::task-stub{title="Normalize adapter refresh behavior around DiffUtils.itemCallback"}
1. Audit the realtime-refreshed adapters and make them use explicit OnDiffRefreshListener behavior instead of generic currentList resubmission.
2. Replace broad notifyItemRangeChanged patterns with payload-based or list-diff-based updates where the adapter already uses ListAdapter.
3. Keep each adapter cleanup in its own PR so UI behavior changes stay small and easy to review.
:::

### Move dictionary persistence behind a repository
DictionaryActivity still parses the dictionary file, writes Realm rows, counts records, and searches words directly even though the project already has DatabaseService and RealmRepository abstractions for detached Realm access. This is a clean low-conflict place to finish the data-layer cleanup by moving one feature fully behind a repository boundary.

:codex-file-citation[codex-file-citation]{line_range_start=105 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L105-L159"}

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L22-L49"}

:::task-stub{title="Extract dictionary reads and imports into a DictionaryRepository"}
1. Add a small DictionaryRepository built on RealmRepository for count, exact-word lookup, and one-time JSON import.
2. Move the dictionary import and search functions out of DictionaryActivity and leave the activity responsible only for download state and rendering.
3. Keep the PR limited to the dictionary feature so it can merge independently without touching shared sync code.
:::

### Route personal uploads through the personals repository flow
PersonalsFragment still injects UploadManager and launches uploads from the fragment, while PersonalsRepository already owns the personal upload contract and persistence rules. This is a low-risk boundary fix that pulls upload behavior back to the feature repository and removes one direct UI-to-service leak.

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L34-L35"}

:codex-file-citation[codex-file-citation]{line_range_start=83 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L83-L97"}

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=28 path=app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt#L23-L28"}

:::task-stub{title="Move personal upload actions from PersonalsFragment to PersonalsViewModel"}
1. Add a small upload action in PersonalsViewModel that delegates to PersonalsRepository instead of UploadManager.
2. Remove UploadManager injection from PersonalsFragment and have the fragment observe upload result state from the ViewModel.
3. Keep the change scoped to personals upload only so the PR stays reviewable and avoids broader sync conflicts.
:::

### Pull survey submission upload orchestration out of UserInformationFragment
UserInformationFragment currently mixes form handling with server reachability checks, preference rewrites, and submission upload calls. That makes the exam UI own cross-feature sync behavior that belongs behind repository or sync-facing boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=265 line_range_end=300 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L265-L300"}

:codex-file-citation[codex-file-citation]{line_range_start=321 line_range_end=394 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L321-L394"}

:::task-stub{title="Move post-submit server checks and uploads behind a submissions sync boundary"}
1. Add a submissions-side entry point that owns reachability checks, alternate URL handling, and upload kickoff for team survey submissions.
2. Leave UserInformationFragment responsible only for collecting input, saving the submission, and reacting to a success or failure result.
3. Keep the PR focused on this fragment plus the receiving repository or sync helper so it does not collide with unrelated exam UI work.
:::

### Remove WorkManager and server preference orchestration from ProcessUserDataActivity
ProcessUserDataActivity still builds upload work requests, observes their completion, and writes server URL pieces directly from the UI layer. Those are good low-hanging targets for pushing data and sync behavior into repositories without introducing a new architecture layer.

:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=156 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L123-L156"}

:codex-file-citation[codex-file-citation]{line_range_start=171 line_range_end=251 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L171-L251"}

:::task-stub{title="Wrap user-data upload jobs and URL persistence behind repository APIs"}
1. Move server URL parsing and preference persistence into a configuration-facing repository method.
2. Wrap the login and bulk upload WorkManager entry points in a sync or upload repository that returns simple UI state.
3. Shrink ProcessUserDataActivity to dialog and navigation behavior so the PR stays isolated to one base activity and one repository seam.
:::

### Stop SyncActivity from owning login sync side effects
SyncActivity injects SyncManager and TransactionSyncManager directly, then starts a long-lived application-scope connectivity collector that triggers uploads and table syncs from the activity. This is exactly the kind of UI-to-service coupling that creates merge pressure and makes threading harder to reason about.

:codex-file-citation[codex-file-citation]{line_range_start=135 line_range_end=138 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L135-L138"}

:codex-file-citation[codex-file-citation]{line_range_start=634 line_range_end=659 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L634-L659"}

:::task-stub{title="Introduce a sync-facing API for the post-login upload and table refresh path"}
1. Move the post-login connectivity observer and the login_activities sync trigger behind a sync repository or coordinator interface.
2. Keep SyncActivity limited to sending the login event and observing sync status updates for UI feedback.
3. Make this a small PR that only touches the login path so it does not conflict with manual sync dialog work.
:::

### Move manual server defaults and PIN policy out of SyncActivity UI extensions
The server dialog extensions still decide default server URLs, default PIN behavior, and manual configuration state directly in UI code, including direct BuildConfig access. Centralizing that policy behind repository or config helpers would tighten boundaries and cut repeated cross-feature logic.

:codex-file-citation[codex-file-citation]{line_range_start=72 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L72-L80"}

:codex-file-citation[codex-file-citation]{line_range_start=289 line_range_end=323 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L289-L323"}

:::task-stub{title="Shift server-selection defaults into a configuration repository or helper"}
1. Move default protocol, default PIN, and selected-community URL resolution into one configuration-focused API.
2. Leave ServerDialogExtensions responsible only for binding values and dispatching user actions.
3. Keep the PR limited to server dialog policy extraction so it can merge separately from sync execution changes.
:::

### Centralize the server allowlists used by dashboard and web viewer
DashboardActivity and WebViewActivity each maintain their own BuildConfig-derived server allowlists in the UI layer. That duplication invites drift and keeps environment policy scattered across features instead of behind one shared source.

:codex-file-citation[codex-file-citation]{line_range_start=183 line_range_end=197 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L183-L197"}

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt#L41-L58"}

:::task-stub{title="Create one shared server policy source for challenge checks and WebView trust"}
1. Introduce a shared provider for allowed and trusted server URLs instead of rebuilding lists in each activity.
2. Update DashboardActivity and WebViewActivity to consume that shared source without changing their feature behavior.
3. Keep the PR narrow to the two call sites and the new shared policy object to minimize review overlap.
:::

### Move chat realtime refresh and list diff policy out of the fragment
ChatHistoryFragment reads RealtimeSyncManager through a singleton, owns refresh triggers, and keeps mutable cached share state in the fragment, while ChatShareTargetAdapter still defines its own DiffUtil callback instead of the project helper. This is a good low-conflict cleanup that improves repository and UI boundaries at the same time.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L55-L61"}

:codex-file-citation[codex-file-citation]{line_range_start=252 line_range_end=257 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L252-L257"}

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L15-L18"}

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L73-L80"}

:::task-stub{title="Push chat realtime refresh and diff policy into ViewModel-owned state"}
1. Move the chat refresh trigger behind ChatViewModel or a repository-provided flow instead of calling the realtime singleton from the fragment.
2. Convert the share-target adapter to the shared DiffUtils.itemCallback helper so list diff rules stay consistent with the rest of the app.
3. Keep the PR scoped to chat history and its adapter to avoid conflicts with chat detail work.
:::

### Move health search, sort, and refresh orchestration into a ViewModel
MyHealthFragment currently owns realtime refresh wiring, debounced search jobs, sorting, dialog list state, and repeated repository lookups. This is a classic low-hanging boundary issue where the fragment is doing both UI work and feature data orchestration.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L56-L57"}

:codex-file-citation[codex-file-citation]{line_range_start=154 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L154-L159"}

:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=259 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L187-L259"}

:::task-stub{title="Introduce a small health screen ViewModel state layer for patient selection and search"}
1. Move patient list loading, sorting, and debounced search into a dedicated health screen state holder backed by UserRepository.
2. Expose refresh events to the fragment through ViewModel state instead of reading RealtimeSyncManager directly in the fragment.
3. Keep the PR focused on the health screen only so it remains easy to review and does not overlap with submission work.
:::

### Narrow sync and feature injections before splitting larger repositories
RepositoryModule binds the same large implementations to both feature and sync interfaces, while UserRepository and TeamsRepository still expose very wide surfaces. A small DI cleanup pass can reduce cross-feature data leaks immediately by changing injection sites to consume the narrowest interface already available.

:codex-file-citation[codex-file-citation]{line_range_start=137 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L137-L159"}

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt#L7-L18"}

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsSyncRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsSyncRepository.kt#L9-L22"}

:codex-file-citation[codex-file-citation]{line_range_start=57 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L57-L71"}

:::task-stub{title="Retarget sync-heavy injection sites to UserSyncRepository and TeamsSyncRepository"}
1. Audit current injections and switch sync-only consumers away from the full UserRepository and TeamsRepository interfaces where narrow sync interfaces already exist.
2. Identify the first few methods in the wide feature interfaces that can stop leaking into other layers once those injections are narrowed.
3. Keep the PR to DI and injection-site edits only so it merges cleanly ahead of larger repository splits.
:::

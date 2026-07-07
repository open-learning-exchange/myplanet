# Refactor Round — Repository Boundary Tasks (2026-07-07)

11 independent, low-hanging tasks focused on reinforcing repository boundaries, cross-feature data leaks, and moving data functions out of UI/services into repositories. File sets are disjoint across tasks, so all PRs can be reviewed and merged in one round without conflicts. Findings verified against `master` @ `be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2`.

### Extract dictionary data access into a DictionaryRepository
DictionaryActivity is the last UI class that injects DatabaseService and runs raw Realm queries and write transactions directly (bulk insert, count, word lookup). There is no Dictionary domain among the 23 repositories, so this is a clean, isolated extraction with an existing RealmRepository base to build on.

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L36-L38"}

:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=137 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L121-L137"}

:codex-file-citation[codex-file-citation]{line_range_start=145 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L145-L159"}

:::task-stub{title="Create DictionaryRepository and remove DatabaseService from DictionaryActivity"}
1. Add `repository/DictionaryRepository.kt` with `suspend fun getDictionaryCount(): Long`, `suspend fun findWord(word: String): RealmDictionary?`, and `suspend fun saveDictionary(jsonArray: JsonArray)`.
2. Add `DictionaryRepositoryImpl` extending `RealmRepository`, moving the `executeTransactionAsync` insert loop, the `count()` query, and the case-insensitive `word` lookup from `DictionaryActivity` verbatim.
3. Bind the pair in `di/RepositoryModule.kt` next to the existing `@Binds` entries.
4. In `DictionaryActivity`, replace the three `databaseService` call sites with repository calls and delete the `DatabaseService` injection plus the `io.realm.Case`/`RealmDictionary` query imports.
5. Add `DictionaryRepositoryImplTest` mirroring an existing repository test (MockK + `TestDispatcherProvider`) and run `./gradlew testDefaultDebugUnitTest`.
:::

### Tighten the TeamsRepository interface: delete dead methods and relocate the cross-domain resource query
TeamsRepository (largest impl, ~1785 lines) exposes 11 methods with zero callers outside its own interface/impl files (`getResourceIdsByUser`, `getShareableEnterprises`, `getJoinRequestById`, `getTaskNotifications`, `getJoinRequestNotifications`, `getTasks`, `getTeamMemberStatuses`, `getRecentVisitCounts`, `upsertTask`, `createEnterprise`, `getTeamVisitCount`), and `getTeamResources` returns `RealmMyLibrary` — a resources-domain type leaking through the teams boundary to a single fragment. Deleting dead surface and routing the one caller through `ResourcesRepository.getLibraryItemsByIds` shrinks the interface with no behavior change.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=83 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L74-L83"}

:codex-file-citation[codex-file-citation]{line_range_start=99 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L99-L114"}

:codex-file-citation[codex-file-citation]{line_range_start=157 line_range_end=164 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L157-L164"}

:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=74 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesFragment.kt#L70-L74"}

:::task-stub{title="Remove 11 uncalled TeamsRepository methods and stop returning RealmMyLibrary from the teams domain"}
1. For each of the 11 methods listed above, confirm zero call sites with a project-wide search, then delete it from `TeamsRepository` and its override in `TeamsRepositoryImpl` (keep any variant still used internally as a private function).
2. In `TeamResourcesFragment`, replace `teamsRepository.getTeamResources(teamId)` with `resourcesRepository.getLibraryItemsByIds(teamsRepository.getResourceIds(teamId))` (inject `ResourcesRepository`; `getResourceIds` thereby gains its first caller and stays).
3. Delete `getTeamResources` from the interface; keep the query as a private helper in `TeamsRepositoryImpl` for its internal use at the resource-link path (`TeamsRepositoryImpl.kt:1508`).
4. Remove any now-orphaned tests for the deleted methods and run `./gradlew testDefaultDebugUnitTest`.
:::

### Trim unused methods from ResourcesRepository
Nine ResourcesRepository methods (`getStepResources`, `getPrivateImagesCreatedAfter`, `saveLibraryItem`, `markResourceAdded`, `markResourceOfflineByLocalAddress`, `getLibraryByUserId`, `getResourceTags`, `getResourceRatingsBulk`, `getResourceTagsBulk`) have no callers outside the interface/impl pair. Removing them tightens the contract other features depend on and shrinks the impl before any deeper resources work.

:codex-file-citation[codex-file-citation]{line_range_start=64 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L64-L77"}

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L97-L108"}

:::task-stub{title="Delete 9 uncalled methods from ResourcesRepository and ResourcesRepositoryImpl"}
1. Confirm each of the 9 methods has zero call sites outside `ResourcesRepository.kt`/`ResourcesRepositoryImpl.kt` with a project-wide search (including `app/src/test`).
2. Delete each from the interface; delete or privatize the impl override depending on internal usage.
3. Remove imports that become unused (e.g. `RealmTag`, `JsonObject`) and any orphaned tests.
4. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Remove the raw io.realm.Realm leak and dead methods from CoursesRepository
`CoursesRepository.insertCertification(realm: io.realm.Realm, doc: JsonObject)` passes a live Realm instance through the repository interface — the exact boundary violation repositories exist to prevent — and it is uncalled, as are `getCourseByCourseId`, `getCourseStepIds`, `getCourseTags`, and `filterCoursesByTag`. All five can simply be deleted.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=28 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L20-L28"}

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L62-L69"}

:::task-stub{title="Delete insertCertification(realm, doc) and 4 other uncalled CoursesRepository methods"}
1. Confirm zero call sites for `insertCertification`, `getCourseByCourseId`, `getCourseStepIds`, `getCourseTags`, and `filterCoursesByTag` outside the interface/impl pair.
2. Delete them from `CoursesRepository` and `CoursesRepositoryImpl`; if the certification insert logic is needed by sync, keep it as a private impl function without the `Realm` parameter in the public surface.
3. Drop the now-unneeded `io.realm.Realm` import from the interface file and remove orphaned tests.
4. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Trim unused methods from ActivitiesRepository and SubmissionsRepository
ActivitiesRepository exposes 7 uncalled methods (`getOfflineActivities`, `getUnuploadedLoginActivities`, `markActivitiesUploaded`, `insertActivity`, `getRecentLogin`, `insertSearchActivityFromNewsLog`, `serializeLoginActivities` — the last one leaking JSON serialization plus a `Context` through the data layer), and SubmissionsRepository exposes 3 (`createSurveySubmission`, `getAllPendingSubmissions`, `getSurveysByCourseId`). Both interfaces sit on the upload path, so a smaller surface directly de-risks the later sync/upload consolidation step of the roadmap.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L12-L12"}

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L26-L35"}

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt#L35-L46"}

:::task-stub{title="Delete uncalled methods from ActivitiesRepository and SubmissionsRepository interfaces"}
1. Confirm zero external call sites for the 7 ActivitiesRepository and 3 SubmissionsRepository methods listed above.
2. Delete each from its interface; keep any impl logic still used by the upload flow as private functions inside the respective Impl.
3. Remove now-unused imports (`Context`, `RealmNewsLog`, `RealmStepExam`, etc.) and orphaned tests.
4. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Remove dead repository injections from five UI classes
Five UI classes inject a repository field that is never referenced (declaration is the only occurrence in the file, and none are overrides used by a base class). Each is a 3-line deletion that removes a phantom cross-feature dependency — e.g. LoginActivity appears to depend on the teams domain but doesn't.

:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt#L33-L34"}

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt#L49-L50"}

:codex-file-citation[codex-file-citation]{line_range_start=125 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L125-L126"}

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L66-L67"}

:codex-file-citation[codex-file-citation]{line_range_start=64 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceFragment.kt#L64-L65"}

:::task-stub{title="Delete unused @Inject repository fields in 5 UI classes"}
1. In `StorageBreakdownFragment`, delete the unused `resourcesRepository` injection (`@Inject` + declaration + import).
2. In `HealthExaminationActivity`, delete the unused `healthRepository` injection.
3. In `SyncActivity`, delete the unused `communityRepository` injection.
4. In `LoginActivity`, delete the unused `teamsRepository` injection.
5. In `AddResourceFragment`, delete the unused `personalsRepository` injection, then build both flavors to confirm nothing referenced them via data binding.
:::

### Move the team fetch in TeamsVoicesFragment into TeamsVoicesViewModel
TeamsVoicesFragment launches its own coroutine in `onCreateView` to call `teamsRepository.getTeamByIdOrTeamId` and derive the posting policy, even though `TeamsVoicesViewModel` already exists and already injects `TeamsRepository`. Moving the call gives the fragment a single observable source and removes a direct fragment→repository data path.

:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt#L89-L96"}

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=28 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt#L26-L28"}

:::task-stub{title="Route TeamsVoicesFragment's getTeamByIdOrTeamId call through TeamsVoicesViewModel"}
1. Add `fun loadTeam(teamId: String)` to `TeamsVoicesViewModel` that calls `teamsRepository.getTeamByIdOrTeamId(teamId)` in `viewModelScope` and exposes the result (team + `toVoicePostingPolicy()`) as a `StateFlow`.
2. In `TeamsVoicesFragment`, replace the `viewLifecycleOwner.lifecycleScope.launch { teamsRepository... }` block with `viewModel.loadTeam(teamId)` and collect the flow via the existing `collectWhenStarted` helper to call `updateCanPostMessage`.
3. Remove the fragment's direct repository call so the fragment no longer performs data access in `onCreateView`.
4. Run `./gradlew testDefaultDebugUnitTest` and add a small ViewModel test for `loadTeam`.
:::

### Move the MyLife cache-then-refresh policy out of BaseDashboardFragment into LifeRepository
`BaseDashboardFragment.myLifeListInit` implements a full data policy in UI code: read a gson cache from SharedPreferences, render, refresh from Realm in a background launch, seed when empty, and write the cache back — while `SharedPrefManager` (a service) serializes `RealmMyLife` models into prefs. This orchestration belongs in `LifeRepositoryImpl`, leaving the fragment to render a single returned list.

:codex-file-citation[codex-file-citation]{line_range_start=277 line_range_end=305 path=app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt#L277-L305"}

:codex-file-citation[codex-file-citation]{line_range_start=275 line_range_end=288 path=app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt#L275-L288"}

:::task-stub{title="Internalize MyLife caching and seeding inside LifeRepositoryImpl"}
1. Add `suspend fun getMyLifeForDashboard(userId: String, seedBase: List<RealmMyLife>): List<RealmMyLife>` to `LifeRepository`; implement it in `LifeRepositoryImpl` with the current cache-read → fallback-query → seed-if-empty → cache-write sequence, injecting `SharedPrefManager` for the pref cache.
2. Move `getCachedMyLifeItems`/`cacheMyLifeItems` usage (and the `CachedMyLifeItem` mapping) behind the repository so no UI or service class serializes `RealmMyLife` again.
3. Rewrite `BaseDashboardFragment.myLifeListInit` to call the new method once and only build views from the returned visible items; delete its nested `lifecycleScope.launch` refresh.
4. Keep the background cache refresh inside the repository (fire-and-forget on the injected dispatcher) so UI behavior is unchanged; run `./gradlew testDefaultDebugUnitTest`.
:::

### Drop Realm types and the dead teams dependency from AddResourceActivity
AddResourceActivity holds `RealmUser` and three `io.realm.RealmList<String>` fields as activity state, plus an injected `TeamsRepository` it never calls — even though persistence already goes through `resourcesRepository.saveLocalResource(request)`. Plain Kotlin collections remove the last Realm-type dependency from this screen with no behavior change.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt#L44-L50"}

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt#L66-L70"}

:::task-stub{title="Replace RealmList fields with MutableList and delete unused TeamsRepository in AddResourceActivity"}
1. Change `subjects`, `levels`, and `resourceFor` from `RealmList<String>?` to non-null `MutableList<String>` initialized inline; delete the three `RealmList()` assignments in `onCreate` and the `io.realm.RealmList` import.
2. Adjust `LocalResourceRequest` construction (or the mapping in `saveResource`) to accept plain lists if it currently expects `RealmList`.
3. Delete the unused `teamsRepository` injection and its import.
4. Build both flavors and exercise the add-resource flow save path via existing tests (`./gradlew testDefaultDebugUnitTest`).
:::

### Tighten UserSessionManager.setResourceOpenCount to primitives instead of RealmMyLibrary
`UserSessionManager.setResourceOpenCount(item: RealmMyLibrary)` takes a whole Realm model but only reads `title` and `resourceId` before delegating to `ActivitiesRepository.logResourceOpen`. Narrowing the signature removes a model-type dependency from a session service and keeps Realm objects from traveling through service APIs.

:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=110 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L95-L110"}

:codex-file-citation[codex-file-citation]{line_range_start=171 line_range_end=171 path=app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L171-L171"}

:codex-file-citation[codex-file-citation]{line_range_start=233 line_range_end=233 path=app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L233-L233"}

:::task-stub{title="Change setResourceOpenCount to take title and resourceId strings"}
1. Replace both `setResourceOpenCount(item: RealmMyLibrary, ...)` overloads in `UserSessionManager` with a single `fun setResourceOpenCount(title: String?, resourceId: String?, type: String? = KEY_RESOURCE_OPEN)`.
2. Update the two `BaseContainerFragment` call sites to pass `items.title` and `items.resourceId`.
3. Remove the `RealmMyLibrary` import from `UserSessionManager`.
4. Run `./gradlew testDefaultDebugUnitTest` (UserSessionManager has existing tests — update them for the new signature).
:::

### Remove global applicationScope + hard-coded Dispatchers.Main from DialogUtils.guestDialog
`guestDialog` launches on `MainApplication.applicationScope` with a hard-coded `Dispatchers.Main` — an unstructured, process-lifetime coroutine started from a button click — solely because `UserSessionManager.getUserModel()` is suspend. Passing the already-known username in eliminates the coroutine, the global scope, and the util's dependency on `UserSessionManager` entirely.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L39-L39"}

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L55-L63"}

:::task-stub{title="Change guestDialog to take a username and drop the applicationScope launch"}
1. Change the signature to `fun guestDialog(context: Context, userName: String?)` and build the `BecomeMemberActivity` intent synchronously in the click listener — delete the `MainApplication.applicationScope.launch(Dispatchers.Main)` wrapper and the `Dispatchers`/`UserSessionManager` imports.
2. Update the call sites in `CoursesFragment`, `ResourcesFragment` (x2), `BellDashboardFragment` (x2), and `DashboardActivity` (x2) to pass the user name they already hold (or fetch it in their own lifecycle scope before showing the dialog).
3. Run `./gradlew testDefaultDebugUnitTest` and smoke-test the guest flow on one caller.
:::

### Testing
This round is planning-only; no code was changed and no Gradle tasks were run. Each task above ends with `./gradlew testDefaultDebugUnitTest` (what CI enforces). For tasks touching flavor-specific UI (dead injections, AddResourceActivity), also run `./gradlew assembleLiteDebug` locally since the lite flavor's tests are not covered by CI.

# Refactor Tasks — Round Plan

### Replace Static `ApiClient.client` Calls in `UploadManager` with Injected `ApiInterface`

`UploadManager` already receives `ApiInterface` via constructor injection, yet seven of its methods shadow that field by calling `ApiClient.client.create(ApiInterface::class.java)` locally. This bypasses Hilt entirely, making the dependency untestable and creating redundant Retrofit instance allocations on every upload call.

:codex-file-citation[codex-file-citation]{line_range_start=68 line_range_end=72 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L68-L72"}

:codex-file-citation[codex-file-citation]{line_range_start=79 line_range_end=79 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L79-L79"}

:::task-stub{title="Remove local ApiClient.client.create() in UploadManager"}
1. In each of the seven methods that shadow `apiInterface` (`uploadActivities`, `uploadUserActivities`, `uploadAnswers`, `uploadChatHistory`, `uploadFeedback`, `uploadPhotos`, `uploadTeamTasks`), delete the `val apiInterface = client.create(ApiInterface::class.java)` local declaration.
2. Let each method body use the constructor-injected `apiInterface` field inherited from `FileUploader`.
3. Remove the now-unused `import org.ole.planet.myplanet.data.api.ApiClient.client` statement.
:::

---

### Replace Static `ApiClient.client` Calls in `UploadToShelfService` with Injected `ApiInterface`

`UploadToShelfService` is annotated `@Singleton` and receives `DatabaseService` plus several repositories via Hilt, but still creates its own `ApiInterface` locally via `ApiClient.client.create()` in every public method. It also declares a `lateinit var mRealm: Realm` that is never assigned anywhere in the class — dead field that should be removed.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L44-L57"}

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L59-L59"}

:::task-stub{title="Inject ApiInterface into UploadToShelfService and delete dead mRealm field"}
1. Add `private val apiInterface: ApiInterface` as a constructor parameter (Hilt will provide it from `NetworkModule`).
2. Remove all eight `val apiInterface = client.create(ApiInterface::class.java)` local declarations inside the methods.
3. Delete the `lateinit var mRealm: Realm` field (line 56) — it is never initialised and never used.
4. Remove the now-unused `import io.realm.Realm` and `import org.ole.planet.myplanet.data.api.ApiClient.client` imports.
:::

---

### Replace Hardcoded `Dispatchers.IO/Main` in `BaseTeamFragment` with `DispatcherProvider`

`BaseTeamFragment.loadTeamDetails()` launches coroutines with literal `Dispatchers.IO` and switches back with `Dispatchers.Main`. The project already has a `DispatcherProvider` interface bound by `DispatcherModule` but `BaseTeamFragment` never injects it, making the coroutine dispatcher untestable and inconsistent with the rest of the base hierarchy.

:codex-file-citation[codex-file-citation]{line_range_start=50 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L50-L73"}

:::task-stub{title="Inject DispatcherProvider into BaseTeamFragment and replace hardcoded Dispatchers"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `BaseTeamFragment` (the class is already `@AndroidEntryPoint`).
2. In `loadTeamDetails()`, replace `lifecycleScope.launch(Dispatchers.IO)` with `lifecycleScope.launch(dispatcherProvider.io)` and `withContext(Dispatchers.Main)` with `withContext(dispatcherProvider.main)`.
3. Remove the now-unused `import kotlinx.coroutines.Dispatchers` statement.
:::

---

### Move Repository Data-Loading Out of `CoursesFragment` into `CoursesViewModel`

`CoursesViewModel` exists but is essentially a state-holder stub: all actual Realm queries (`getAllCourses`, `getCourseRatings`, `getCourseProgress`, `filterCourses`, etc.) are called directly from `CoursesFragment` via the `coursesRepository` field it inherits from `BaseResourceFragment`. This keeps heavy I/O logic in the UI layer and duplicates query calls across `loadDataAsync`, `getAdapter`, and `filterCoursesAndUpdateUi`.

:codex-file-citation[codex-file-citation]{line_range_start=174 line_range_end=200 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L174-L200"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L1-L57"}

:::task-stub{title="Move loadDataAsync and filterCoursesAndUpdateUi logic into CoursesViewModel"}
1. Inject `CoursesRepository` and `DispatcherProvider` into `CoursesViewModel` via constructor.
2. Add a `loadCourses(isMyCourseLib: Boolean, userId: String?)` suspend function in the ViewModel that calls `coursesRepository.getAllCourses()`, ratings, and progress in parallel with `async`, then calls `processCourses`.
3. Add a `filterCourses(searchText, grade, subject, tagNames, userId, isMyCourseLib)` function in the ViewModel that calls `coursesRepository.filterCourses(...)` and updates `_coursesState`.
4. In `CoursesFragment`, replace calls to `loadDataAsync()` and `filterCoursesAndUpdateUi()` with `viewModel.loadCourses(...)` and `viewModel.filterCourses(...)` respectively.
:::

---

### Wire `VoicesFragment` to `NewsViewModel` Instead of Injecting `VoicesRepository` Directly

`VoicesFragment` injects `VoicesRepository` and calls `voicesRepository.getCommunityNews()` inside a `repeatOnLifecycle` block. A `NewsViewModel` already exists in the same package but currently only exposes one image-URL helper. Moving the community-news `Flow` collection into the ViewModel follows the established pattern (e.g., `NotificationsFragment` → `NotificationsViewModel`) and keeps the fragment lifecycle-safe by default.

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L51-L54"}

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L97-L125"}

:::task-stub{title="Expose getCommunityNews Flow through NewsViewModel; collect in VoicesFragment via viewModels()"}
1. Inject `VoicesRepository` and `DispatcherProvider` into `NewsViewModel`.
2. Add `fun observeCommunityNews(userIdentifier: String): Flow<List<RealmNews>>` that delegates to `voicesRepository.getCommunityNews(userIdentifier)`.
3. In `VoicesFragment`, replace `@Inject lateinit var voicesRepository: VoicesRepository` with `private val viewModel: NewsViewModel by viewModels()`.
4. Change the `repeatOnLifecycle` collector to call `viewModel.observeCommunityNews(getUserIdentifier())` instead of the repository directly.
5. Keep all other `voicesRepository` call-sites in `VoicesFragment` (post, delete, share) via field injection as-is — this task is scoped to the read flow only.
:::

---

### Move `VoicesAdapter` JSON Parsing from `onBindViewHolder` to the Model / Pre-Bind Step

`VoicesAdapter.extractSharedTeamName()` and `handleConversations()` call `JsonUtils.gson.fromJson()` directly inside (or immediately triggered by) `onBindViewHolder`, parsing raw JSON strings stored in `RealmNews.viewIn` and `RealmNews.conversations` on every bind. This is a per-frame allocation that runs on the main thread at scroll time.

:codex-file-citation[codex-file-citation]{line_range_start=207 line_range_end=220 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L207-L220"}

:codex-file-citation[codex-file-citation]{line_range_start=363 line_range_end=375 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L363-L375"}

:::task-stub{title="Cache parsed viewIn and conversations in RealmNews or a wrapper; read cache in bind"}
1. Add two `@Ignore`-annotated (or transient) fields to `RealmNews`: `var parsedViewIn: JsonArray? = null` and `var parsedConversations: List<RealmConversation>? = null`.
2. In `VoicesAdapter.submitList()` override (or before `submitList` in the fragment), iterate the incoming list and populate both parsed fields once using `JsonUtils.gson.fromJson(...)`.
3. In `extractSharedTeamName()` and `handleConversations()`, read from the pre-parsed fields instead of calling `fromJson` again.
4. Apply the same pattern to the image-URL `fromJson` calls at lines 646 and 652.
:::

---

### Replace `mRealm` Parameter Pattern in `FeedbackRepositoryImpl.insertFeedbackToRealm` with `RealmRepository.executeTransaction`

`FeedbackRepositoryImpl` already extends `RealmRepository` but `insertFeedbackToRealm(mRealm: Realm, act: JsonObject)` takes a raw `Realm` parameter, bypassing the base class transaction helpers. This is the same raw-Realm anti-pattern that `RealmRepository.executeTransaction` was designed to eliminate.

:codex-file-citation[codex-file-citation]{line_range_start=112 line_range_end=132 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L112-L132"}

:::task-stub{title="Refactor insertFeedbackToRealm to use RealmRepository.executeTransaction"}
1. Change `private fun insertFeedbackToRealm(mRealm: Realm, act: JsonObject)` to `private suspend fun insertFeedbackToRealm(act: JsonObject)`.
2. Wrap the body in `executeTransaction { realm -> … }` using the inherited helper, replacing the `mRealm` parameter with the lambda-scoped `realm`.
3. Update the two call-sites at lines ~99 and ~105 (which already call `executeTransaction` themselves) to simply call `insertFeedbackToRealm(act)` directly.
4. Remove the `import io.realm.Realm` statement if it becomes unused after this change.
:::

---

### Replace `mRealm` Parameter in `VoicesRepositoryImpl.insertNewsToRealm` with `RealmRepository.executeTransaction`

`VoicesRepositoryImpl` extends `RealmRepository` and uses `executeTransaction` correctly in most of its write paths, but `insertNewsToRealm(mRealm: Realm, doc: JsonObject)` (called from `insertNewsFromJson` and `insertNewsList`) still takes a raw `Realm` parameter — the same inconsistency as `FeedbackRepositoryImpl`.

:codex-file-citation[codex-file-citation]{line_range_start=515 line_range_end=550 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L515-L550"}

:::task-stub{title="Refactor insertNewsToRealm to use the inherited executeTransaction helper"}
1. Change `private fun insertNewsToRealm(mRealm: Realm, doc: JsonObject)` to `private suspend fun insertNewsToRealm(doc: JsonObject)`.
2. Wrap the entire body in `executeTransaction { realm -> … }`, replacing the `mRealm` parameter references with `realm`.
3. In `insertNewsFromJson` (line 515) and `insertNewsList` (line 506), remove the outer `executeTransaction` wrappers and call `insertNewsToRealm(doc)` directly since it now owns its own transaction.
:::

---

### Replace Hardcoded `Dispatchers.IO/Main` in `CoursesFragment` with `DispatcherProvider`

`CoursesFragment` uses `withContext(Dispatchers.IO)` at lines 113 and 551, and `withContext(Dispatchers.Main)` at line 634. The `DispatcherProvider` is already provided by `DispatcherModule` and injected elsewhere. `BaseResourceFragment` (parent class) has no dispatcher injection, so it must be added at the fragment level to stay consistent with ViewModel and repository layers.

:codex-file-citation[codex-file-citation]{line_range_start=111 line_range_end=116 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L111-L116"}

:codex-file-citation[codex-file-citation]{line_range_start=549 line_range_end=558 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L549-L558"}

:::task-stub{title="Inject DispatcherProvider into CoursesFragment; replace literal Dispatchers.IO/Main"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `CoursesFragment` (already `@AndroidEntryPoint`).
2. Replace `withContext(Dispatchers.IO)` at lines 113 and 551 with `withContext(dispatcherProvider.io)`.
3. Replace `withContext(Dispatchers.Main)` at line 634 with `withContext(dispatcherProvider.main)`.
4. Remove the `import kotlinx.coroutines.Dispatchers` import if no other references remain in the file.
:::

---

### Move Repository Calls in `PersonalsFragment` into a Dedicated `PersonalsViewModel`

`PersonalsFragment` injects `PersonalsRepository` and `UserSessionManager` directly and drives a `repeatOnLifecycle` Flow collection without any ViewModel. This is one of the few remaining fragments that skips the ViewModel layer entirely for its read path, making the data loading non-survivalble across configuration changes and harder to extend.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/521d4eaaf26f7baeafd4bc632989b0cc8ca5ae3b/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L29-L78"}

:::task-stub{title="Create PersonalsViewModel; move getPersonalResources Flow and userId lookup into it"}
1. Create `PersonalsViewModel` annotated `@HiltViewModel`, injecting `PersonalsRepository` and `UserSessionManager`.
2. Add a `StateFlow<List<RealmMyPersonal>>` backed by `viewModelScope.launch` that calls `userSessionManager.getUserModel()` then collects `personalsRepository.getPersonalResources(userId)` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`.
3. In `PersonalsFragment.setAdapter()`, replace the direct `personalsRepository` flow collection with `viewModel.personals.collectLatest { ... }`.
4. Remove `@Inject lateinit var personalsRepository: PersonalsRepository` and the `getUserModel()` call from the fragment; keep `userSessionManager` only if needed for non-read operations.
:::

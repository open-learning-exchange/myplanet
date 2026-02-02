### Move RealmRating query and aggregation logic from model companion into RatingsRepository

ResourcesFragment directly imports and calls `RealmRating.Companion.getRatings(mRealm, ...)` passing the raw Realm instance from the UI layer. The `getRatings` and `getRatingsById` methods in the model companion perform Realm queries and rating aggregation that belong in the existing `RatingsRepository` interface. Moving them closes the leak where UI touches Realm through a model shortcut and lets the repository own all rating data access.

:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L33-L41"}
:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L44-L51"}
:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=179 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L179-L179"}
:codex-file-citation[codex-file-citation]{line_range_start=208 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L208-L208"}

:::task-stub{title="Move getRatings/getRatingsById from RealmRating companion into RatingsRepository"}
1. Add `suspend fun getResourceRatings(userId: String?): HashMap<String?, JsonObject>` to `RatingsRepository` interface
2. Implement it in `RatingsRepositoryImpl` by inlining the query and aggregation logic from `RealmRating.getRatings`, using `RealmRepository.withRealm`
3. Update `ResourcesFragment.refreshResourcesData()` and `getAdapter()` to call the new repository method instead of `getRatings(mRealm, ...)`
4. Remove the static import of `RealmRating.Companion.getRatings` from `ResourcesFragment`
5. Deprecate `RealmRating.getRatings` and `RealmRating.getRatingsById` companion methods
:::

### Move library filter helpers from RealmMyLibrary companion into ResourcesRepository

`ResourcesFragment.getData()` directly calls `RealmMyLibrary.Companion.getArrayList`, `getLevels`, and `getSubjects` — utility methods that extract filter facets from library lists. These are data-layer operations exposed through a model companion, leaking domain logic into the UI. The existing `ResourcesRepository` interface should own facet extraction so the fragment never imports model companion methods.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L39-L41"}
:codex-file-citation[codex-file-citation]{line_range_start=540 line_range_end=543 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L540-L543"}

:::task-stub{title="Move getArrayList/getLevels/getSubjects from RealmMyLibrary companion into ResourcesRepository"}
1. Add `suspend fun getFilterFacets(libraries: List<RealmMyLibrary>): Map<String, Set<String>>` to `ResourcesRepository`
2. Implement in `ResourcesRepositoryImpl` by moving `getArrayList`, `getLevels`, `getSubjects` logic there
3. Update `ResourcesFragment.getData()` to call `resourcesRepository.getFilterFacets(...)` instead of model companion methods
4. Remove the three static imports from `ResourcesFragment`
5. Deprecate the companion methods in `RealmMyLibrary`
:::

### Replace mRealm.copyFromRealm calls in ResourcesFragment with repository methods

`ResourcesFragment` calls `mRealm.copyFromRealm(filteredLibraryList)` in at least four places: `refreshResourcesData`, `getAdapter`, `setupSearchTextListener`, and `onFilterApplied`. This tightly couples the fragment to Realm's managed-object lifecycle. The `ResourcesRepository` already provides query methods that return detached copies; the fragment should use those and avoid touching `mRealm` directly.

:codex-file-citation[codex-file-citation]{line_range_start=194 line_range_end=194 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L194-L194"}
:codex-file-citation[codex-file-citation]{line_range_start=211 line_range_end=211 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L211-L211"}
:codex-file-citation[codex-file-citation]{line_range_start=531 line_range_end=531 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L531-L531"}

:::task-stub{title="Remove direct mRealm.copyFromRealm calls from ResourcesFragment"}
1. Ensure `ResourcesRepository.getAllLibraryItems()` and `getLibraryListForUser()` return detached copies
2. Replace `mRealm.copyFromRealm(filteredLibraryList)` in `refreshResourcesData()` with the repository call result
3. Replace `mRealm.copyFromRealm(libraryList)` in `getAdapter()` with the repository call result
4. Replace `mRealm.copyFromRealm(...)` in `onFilterApplied()` and `setupSearchTextListener()` similarly
5. Remove any now-unused `mRealm` references from `ResourcesFragment` if fully decoupled
:::

### Replace mRealm.copyFromRealm calls in CoursesFragment with repository methods

`CoursesFragment` calls `mRealm.copyFromRealm(managedCourseList)` in `refreshCoursesData` and `getAdapter`, then manually copies the `isMyCourse` flag in a `forEachIndexed` loop. This Realm-specific copying belongs in `CoursesRepository` which already provides course query methods and can return detached copies with the flag set.

:codex-file-citation[codex-file-citation]{line_range_start=190 line_range_end=194 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L190-L194"}
:codex-file-citation[codex-file-citation]{line_range_start=226 line_range_end=230 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L226-L230"}

:::task-stub{title="Remove direct mRealm.copyFromRealm calls from CoursesFragment"}
1. Add a repository method in `CoursesRepository` that returns detached courses with the `isMyCourse` flag already set
2. Replace `mRealm.copyFromRealm(managedCourseList)` + `forEachIndexed` in `refreshCoursesData()` with the new repository method
3. Replace `mRealm.copyFromRealm(managedCourses)` + `forEachIndexed` in `getAdapter()` similarly
4. Remove `mRealm.refresh()` from `refreshCoursesData()` — let the repository handle freshness via `ensureLatest`
:::

### Move RealmMyCourse.createMyCourse and getMyCourse from model companion into CoursesRepository

`RealmMyCourse.createMyCourse` manages `beginTransaction`/`commitTransaction` directly inside the model companion, and `getMyCourse` runs a raw Realm query. Both are already partially duplicated by `CoursesRepository` methods. Consolidating them removes the transaction management leak from the model layer and enforces the single-owner pattern for course data.

:codex-file-citation[codex-file-citation]{line_range_start=265 line_range_end=281 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L265-L281"}
:codex-file-citation[codex-file-citation]{line_range_start=260 line_range_end=262 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L260-L262"}
:codex-file-citation[codex-file-citation]{line_range_start=241 line_range_end=257 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L241-L257"}

:::task-stub{title="Move createMyCourse/getMyCourse/insert from RealmMyCourse companion into CoursesRepository"}
1. Add `suspend fun enrollUserInCourse(courseId: String, userId: String)` to `CoursesRepository` interface
2. Implement in `CoursesRepositoryImpl` using `RealmRepository.executeTransaction`, replacing `createMyCourse`'s manual transaction logic
3. Ensure `CoursesRepository.getCourseById` covers `getMyCourse`'s use case
4. Update all callers of `RealmMyCourse.createMyCourse` and `getMyCourse` to use the repository
5. Deprecate `createMyCourse`, `getMyCourse`, and `insert` on `RealmMyCourse.Companion`
:::

### Move RealmNews.insert and serializeNews from model companion into ChatRepository

`RealmNews.insert` (50+ lines) performs complex Realm object creation with field mapping, link extraction, and nested object parsing directly in the model companion. `serializeNews` (30+ lines) handles JSON serialization with conditional fields. The existing `ChatRepository` should own news data persistence and serialization, keeping the model as a plain data object.

:codex-file-citation[codex-file-citation]{line_range_start=130 line_range_end=179 path=app/src/main/java/org/ole/planet/myplanet/model/RealmNews.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmNews.kt#L130-L179"}
:codex-file-citation[codex-file-citation]{line_range_start=182 line_range_end=213 path=app/src/main/java/org/ole/planet/myplanet/model/RealmNews.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmNews.kt#L182-L213"}

:::task-stub{title="Move insert/serializeNews from RealmNews companion into ChatRepository"}
1. Add `suspend fun insertNewsFromJson(doc: JsonObject)` to `ChatRepository` interface
2. Implement in `ChatRepositoryImpl` by moving `RealmNews.insert` logic, using `RealmRepository.executeTransaction`
3. Add `fun serializeNews(news: RealmNews): JsonObject` to `ChatRepository`
4. Update all callers (SyncManager, UploadManager) to use the repository methods
5. Deprecate `RealmNews.insert` and `RealmNews.serializeNews` companion methods
:::

### Replace Realm.getDefaultInstance in VoicesActions with injected dependency

`VoicesActions.kt` calls `Realm.getDefaultInstance().use { realm -> ... }` directly in a standalone function, bypassing DI entirely. This creates an untracked Realm instance outside the repository layer. The function should receive a repository or `DatabaseService` through its caller so that all Realm access goes through the DI graph.

:codex-file-citation[codex-file-citation]{line_range_start=167 line_range_end=172 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L167-L172"}

:::task-stub{title="Replace Realm.getDefaultInstance in VoicesActions with injected DatabaseService"}
1. Change the function signature to accept `DatabaseService` as a parameter (or make it a method on an injected class)
2. Replace `Realm.getDefaultInstance().use { realm -> ... }` with `databaseService.withRealm { realm -> ... }`
3. Update all call sites to pass the `DatabaseService` instance
4. Verify no other `Realm.getDefaultInstance()` calls exist outside `DatabaseService` and `RealmRepository`
:::

### Replace notifyDataSetChanged in ResourcesAdapter with granular DiffUtil update

`ResourcesAdapter.setOpenedResourceIds` calls `notifyDataSetChanged()` which forces a full rebind of all visible items. The adapter already extends `ListAdapter` with `DiffUtils.itemCallback`. The opened-resource-IDs change can be expressed as a payload-based partial update similar to how `setTagsMap` already uses `notifyItemRangeChanged` with a payload constant.

:codex-file-citation[codex-file-citation]{line_range_start=208 line_range_end=211 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L208-L211"}
:codex-file-citation[codex-file-citation]{line_range_start=203 line_range_end=206 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L203-L206"}

:::task-stub{title="Replace notifyDataSetChanged in ResourcesAdapter.setOpenedResourceIds with payload update"}
1. Add an `OPENED_PAYLOAD` constant alongside the existing `TAGS_PAYLOAD`
2. Replace `notifyDataSetChanged()` in `setOpenedResourceIds` with `notifyItemRangeChanged(0, currentList.size, OPENED_PAYLOAD)`
3. Handle the `OPENED_PAYLOAD` in the existing `onBindViewHolder(holder, position, payloads)` override to update only the opened-state UI
4. Verify the opened-resource visual indicator updates correctly without a full rebind
:::

### Extract direct Realm queries from AddExaminationActivity into a HealthRepository

`AddExaminationActivity` holds a `lateinit var mRealm` and performs raw Realm queries for `RealmHealthExamination`, `RealmUser`, plus manual `beginTransaction`/`commitTransaction` for key generation and data saves. This is the most direct UI→Realm coupling in the codebase. A `HealthRepository` (new) should own examination CRUD, keeping the activity focused on UI.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L84-L108"}

:::task-stub{title="Extract Realm queries from AddExaminationActivity into a new HealthRepository"}
1. Create `HealthRepository` interface with methods: `getExamination(userId: String): RealmHealthExamination?`, `getUser(userId: String): RealmUser?`, `ensureUserEncryptionKeys(userId: String)`, `saveExamination(data: ...)`
2. Create `HealthRepositoryImpl` extending `RealmRepository`, implementing with `withRealm`/`executeTransaction`
3. Register binding in `RepositoryModule`
4. Inject `HealthRepository` into `AddExaminationActivity` and replace all direct `mRealm` queries
5. Remove `lateinit var mRealm` from the activity
:::

### Move RealmMyCourse.getMyCourseIds from model companion into CoursesRepository

`RealmMyCourse.getMyCourseIds` runs a Realm query and builds a `JsonArray` of course IDs. It is called from `UploadManager` during sync and leaks Realm access through the model layer. The `CoursesRepository` already has course query methods; adding an ID-list method there consolidates data access and lets `UploadManager` go through the repository instead of a model companion with a raw Realm parameter.

:codex-file-citation[codex-file-citation]{line_range_start=284 line_range_end=291 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L284-L291"}

:::task-stub{title="Move getMyCourseIds from RealmMyCourse companion into CoursesRepository"}
1. Add `suspend fun getMyCourseIds(userId: String): JsonArray` to `CoursesRepository` interface
2. Implement in `CoursesRepositoryImpl` using `withRealm` to query and build the ID array
3. Update `UploadManager` to call `coursesRepository.getMyCourseIds(userId)` instead of `RealmMyCourse.getMyCourseIds(realm, userId)`
4. Deprecate `RealmMyCourse.getMyCourseIds` companion method
:::

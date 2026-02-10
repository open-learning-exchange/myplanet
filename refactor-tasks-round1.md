### Extract DictionaryActivity Realm operations into a new DictionaryRepository

DictionaryActivity performs direct Realm queries (count, search, bulk insert with JSON parsing) entirely inside the Activity. All three operations — loading, counting, and searching — bypass any repository and operate on raw Realm instances. A new DictionaryRepository (interface + impl extending RealmRepository) should own these operations so the Activity only calls suspend functions.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L127"}
:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/model/RealmDictionary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/model/RealmDictionary.kt#L6-L16"}

:::task-stub{title="Create DictionaryRepository and move Realm operations out of DictionaryActivity"}
1. Create `DictionaryRepository` interface with `suspend fun count(): Long`, `suspend fun loadFromJson(jsonArray: JsonArray)`, `suspend fun search(word: String): RealmDictionary?`
2. Create `DictionaryRepositoryImpl` extending `RealmRepository` implementing the three methods using `queryList`, `executeTransaction`, and `findByField`
3. Bind interface to implementation in `RepositoryModule`
4. Inject `DictionaryRepository` into `DictionaryActivity` and replace all `databaseService.withRealm`/`withRealmAsync` calls with repository calls
5. Remove direct Realm and Gson imports from `DictionaryActivity`
:::

### Move RealmCommunity sync from DataService into a CommunityRepository

`DataService.syncPlanetServers()` opens a raw Realm instance, deletes all `RealmCommunity` rows, parses a JSON array, and creates new objects — all inside the data-service class. This is a full CRUD cycle that belongs in a dedicated `CommunityRepository`, making `DataService` a thin coordinator that calls `communityRepository.replaceAll(jsonArray)`.

:codex-file-citation[codex-file-citation]{line_range_start=221 line_range_end=277 path=app/src/main/java/org/ole/planet/myplanet/data/DataService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/data/DataService.kt#L221-L277"}
:codex-file-citation[codex-file-citation]{line_range_start=304 line_range_end=306 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L304-L306"}

:::task-stub{title="Create CommunityRepository and move syncPlanetServers Realm logic out of DataService"}
1. Create `CommunityRepository` interface with `suspend fun replaceAll(rows: JsonArray)` and `suspend fun getAllSorted(): List<RealmCommunity>`
2. Create `CommunityRepositoryImpl` extending `RealmRepository`; move the `realm.delete(RealmCommunity)` + create loop from `DataService` lines 234-252
3. Bind in `RepositoryModule`
4. Inject into `DataService`, replace Realm transaction block with `communityRepository.replaceAll(arr)`
5. Inject into `ServerDialogExtensions.setupManualConfigEnabled` and replace its direct `realm.where(RealmCommunity)` query with `communityRepository.getAllSorted()`
:::

### Move AddExaminationActivity Realm queries behind UserRepository and a new HealthExaminationRepository

AddExaminationActivity holds a raw `mRealm` field and performs six direct Realm queries, manual transaction management (`beginTransaction`/`commitTransaction`), and `createObject` calls. The user lookup should go through the existing `UserRepository`, and the examination CRUD should live in a new `HealthExaminationRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L84-L108"}
:codex-file-citation[codex-file-citation]{line_range_start=270 line_range_end=331 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L270-L331"}
:codex-file-citation[codex-file-citation]{line_range_start=407 line_range_end=423 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L407-L423"}

:::task-stub{title="Create HealthExaminationRepository and remove raw Realm from AddExaminationActivity"}
1. Create `HealthExaminationRepository` interface with `suspend fun findByIdOrUserId(id: String): RealmHealthExamination?`, `suspend fun save(examination: RealmHealthExamination)`, `suspend fun createIfMissing(userId: String): RealmHealthExamination`
2. Create `HealthExaminationRepositoryImpl` extending `RealmRepository`; move the six `mRealm.where` queries and the `createObject`/save logic from the activity
3. Bind in `RepositoryModule`
4. Inject both `HealthExaminationRepository` and `UserRepository` into `AddExaminationActivity`; replace raw Realm field with repository calls wrapped in `lifecycleScope.launch`
5. Remove `lateinit var mRealm: Realm`, direct `beginTransaction`/`commitTransaction`, and `onDestroy` Realm close
:::

### Route TeamCoursesFragment course query through CoursesRepository

TeamCoursesFragment calls `mRealm.where(RealmMyCourse::class.java).in("id", ...).findAll()` directly on the main thread, bypassing the existing `CoursesRepository` which already provides course-query methods. This should use `coursesRepository.getCoursesByIds(ids)` (a small addition to the existing interface) and be wrapped in a coroutine.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L28-L36"}

:::task-stub{title="Replace direct Realm query in TeamCoursesFragment with CoursesRepository call"}
1. Add `suspend fun getCoursesByIds(ids: List<String>): List<RealmMyCourse>` to `CoursesRepository` interface
2. Implement in `CoursesRepositoryImpl` using `queryList` with an `in("id", ...)` filter
3. Inject `CoursesRepository` into `TeamCoursesFragment`
4. Wrap `setupCoursesList()` body in `viewLifecycleOwner.lifecycleScope.launch` and call `coursesRepository.getCoursesByIds(team?.courses ?: emptyList())`
5. Remove direct `mRealm.where(RealmMyCourse::class.java)` call
:::

### Move EditAchievementFragment Realm operations into a new AchievementsRepository

EditAchievementFragment performs direct `withRealmAsync { realm.executeTransaction { realm.where(RealmAchievement).findFirst() } }` to load, create, and update achievement records, and a separate `realm.where(RealmMyLibrary).findAll()` for the resource picker. These should be extracted into an `AchievementsRepository` and the resource query should use the existing `ResourcesRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=92 line_range_end=112 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L92-L112"}
:codex-file-citation[codex-file-citation]{line_range_start=272 line_range_end=295 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L272-L295"}
:codex-file-citation[codex-file-citation]{line_range_start=302 line_range_end=320 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L302-L320"}
:codex-file-citation[codex-file-citation]{line_range_start=170 line_range_end=204 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt#L170-L204"}

:::task-stub{title="Create AchievementsRepository and remove Realm from EditAchievementFragment and AchievementFragment"}
1. Create `AchievementsRepository` interface with `suspend fun getOrCreate(id: String): RealmAchievement`, `suspend fun update(id: String, header: String, goals: String, purpose: String, sendToNation: String, achievements: JsonArray, references: JsonArray)`, `suspend fun getWithResources(id: String): AchievementData`
2. Create `AchievementsRepositoryImpl` extending `RealmRepository`, moving logic from both fragments
3. Bind in `RepositoryModule`
4. Inject `AchievementsRepository` and `ResourcesRepository` (already exists for `getAllLibraryItems()`) into `EditAchievementFragment`; replace `databaseService.withRealmAsync` calls
5. Inject `AchievementsRepository` into `AchievementFragment`; replace `loadAchievementDataAsync()` with a repository call
:::

### Move BaseDashboardFragment MyLife setup logic into LifeRepository

`BaseDashboardFragment.setUpMyLife()` calls `databaseService.executeTransactionAsync` and `realm.createObject(RealmMyLife)` to seed the MyLife list, while `myLifeListInit()` calls the static `RealmMyLife.getMyLifeByUserId()`. Both bypass the existing `LifeRepository` which already provides `getMyLifeByUserId()`. The seeding logic should move into a new `seedMyLife(userId, items)` method on `LifeRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=268 line_range_end=306 path=app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt#L268-L306"}
:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLife.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLife.kt#L26-L39"}
:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt#L5-L9"}

:::task-stub{title="Add seedMyLife to LifeRepository and wire BaseDashboardFragment through it"}
1. Add `suspend fun seedMyLifeIfEmpty(userId: String?, items: List<RealmMyLife>)` to `LifeRepository` interface
2. Implement in `LifeRepositoryImpl`: query by userId, if empty create objects via `executeTransaction`
3. Inject `LifeRepository` into `BaseDashboardFragment`
4. Replace `setUpMyLife()` body with `lifeRepository.seedMyLifeIfEmpty(userId, getMyLifeListBase(userId))`
5. Replace `myLifeListInit()` static call `RealmMyLife.getMyLifeByUserId(realmInstance, settings)` with `lifeRepository.getMyLifeByUserId(userId)`
:::

### Remove MarkdownDialogFragment direct Realm query for challenge actions

MarkdownDialogFragment reaches through the parent DashboardActivity to get a `databaseService`, then runs `realm.where(RealmUserChallengeActions::class.java).equalTo(...).count()` inside a UI component. This cross-feature data leak should be replaced by passing the boolean result as an argument or querying through a repository injected via Hilt.

:codex-file-citation[codex-file-citation]{line_range_start=106 line_range_end=116 path=app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt#L106-L116"}

:::task-stub{title="Pass hasSyncAction as fragment argument instead of querying Realm in MarkdownDialogFragment"}
1. Add `ARG_HAS_SYNC_ACTION` boolean argument to `MarkdownDialogFragment.newInstance()` companion factory
2. Move the `realm.where(RealmUserChallengeActions::class.java).equalTo(...).count()` query to the caller (DashboardActivity or wherever `newInstance` is called) and pass the boolean result
3. In `setupCourseButton()`, read `hasSyncAction` from `arguments` instead of querying through `dashboard.databaseService`
4. Remove the `databaseService.withRealm` call and the cast to `DashboardActivity` for database access
:::

### Remove SyncActivity direct `realm.deleteAll()` and route through a repository method

`SyncActivity.onUpdateAvailable()` calls `databaseService.executeTransactionAsync { realm -> realm.deleteAll() }` to wipe the database when a mandatory update is detected. This destructive operation should be exposed as a dedicated method (e.g., on `ConfigurationsRepository` or a new `DatabaseMaintenanceRepository`) so the UI never manipulates Realm directly.

:codex-file-citation[codex-file-citation]{line_range_start=741 line_range_end=754 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L741-L754"}

:::task-stub{title="Move realm.deleteAll from SyncActivity into ConfigurationsRepository"}
1. Add `suspend fun clearAllData()` to `ConfigurationsRepository` interface
2. Implement in `ConfigurationsRepositoryImpl` using `executeTransaction { it.deleteAll() }`
3. In `SyncActivity.onUpdateAvailable()`, replace `databaseService.executeTransactionAsync { realm -> realm.deleteAll() }` with `configurationsRepository.clearAllData()` inside the existing `lifecycleScope.launch(Dispatchers.IO)`
4. Remove direct `databaseService` usage from the `onUpdateAvailable` code path
:::

### Deprecate RealmMyLife.getMyLifeByUserId companion and route callers through LifeRepository

The `RealmMyLife` companion object exposes two static `getMyLifeByUserId()` methods that take a raw `Realm` parameter and run `realm.where().findAll()`. The identical query already exists in `LifeRepositoryImpl.getMyLifeByUserId()`. All callers should migrate to the repository and the companion methods should be marked `@Deprecated`.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLife.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLife.kt#L26-L38"}
:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L7-L31"}

:::task-stub{title="Deprecate RealmMyLife companion queries and migrate callers to LifeRepository"}
1. Add `@Deprecated("Use LifeRepository.getMyLifeByUserId instead")` to both `RealmMyLife.getMyLifeByUserId` companion methods
2. Search all callers of `RealmMyLife.getMyLifeByUserId` (BaseDashboardFragment, any other usages)
3. Replace each call with `lifeRepository.getMyLifeByUserId(userId)` ensuring `LifeRepository` is injected
4. Verify no new callers of the deprecated methods exist
:::

### Remove BaseRecyclerFragment direct Realm instance field and move data access to repositories

`BaseRecyclerFragment` holds a `mRealm` field, opens it in `onViewCreated`, and performs `mRealm.where()` queries, manual transactions (`beginTransaction`/`commitTransaction`), and cleanup in `onDestroyView`. Subclass fragments inherit this Realm handle and use it to bypass repositories. The base class should be refactored so subclasses use injected repositories instead of inheriting a raw Realm instance.

:codex-file-citation[codex-file-citation]{line_range_start=85 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/5292577e6635781498ac527d6b9a1ff648390185/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L85-L105"}

:::task-stub{title="Plan removal of mRealm from BaseRecyclerFragment in favor of repository injection"}
1. Audit all subclasses of `BaseRecyclerFragment` that reference `mRealm` directly (CoursesFragment, ResourcesFragment, etc.)
2. For each subclass, identify which repository already covers its `mRealm.where()` calls
3. As a first step, mark `mRealm` as `@Deprecated` in `BaseRecyclerFragment` with a message directing to repositories
4. Move `mRealm.where(RealmTag::class.java)` tag-filtering query (around line 332) into `TagsRepository`
5. Convert one subclass (the simplest) to inject its repository and stop using `mRealm` as the reference pattern for the rest
:::

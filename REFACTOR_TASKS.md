### Move RealmCourseProgress companion insert/serialize into ProgressRepository

`RealmCourseProgress.insert()` and `serializeProgress()` live in the model companion object and take a raw `Realm` parameter. ProgressRepositoryImpl already extends RealmRepository, so these methods should move there, removing direct Realm coupling from the model.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L27-L57"}
:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L21-L21"}

:::task-stub{title="Move RealmCourseProgress.insert and serializeProgress into ProgressRepositoryImpl"}
1. Add `insertFromJson(act: JsonObject)` method to ProgressRepository interface
2. Implement it in ProgressRepositoryImpl using RealmRepository.executeTransaction, moving the body of companion `insert()`
3. Add `serializeProgress(progress: RealmCourseProgress): JsonObject` to ProgressRepositoryImpl
4. Update all call sites of `RealmCourseProgress.insert()` to use the repository method instead
5. Remove the companion object methods from RealmCourseProgress
:::

### Move RealmRating companion insert/serialize into RatingsRepository

`RealmRating.insert()` and `serializeRating()` sit in the model companion with raw Realm access. RatingsRepositoryImpl already extends RealmRepository and is the natural owner of this logic.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L32-L75"}
:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt#L13-L16"}

:::task-stub{title="Move RealmRating.insert and serializeRating into RatingsRepositoryImpl"}
1. Add `insertFromJson(act: JsonObject)` to RatingsRepository interface
2. Implement in RatingsRepositoryImpl using RealmRepository.executeTransaction
3. Move `serializeRating()` logic into the repository implementation
4. Update all call sites (sync services) to use the repository
5. Remove companion object methods from RealmRating
:::

### Move RealmTag companion insert/getTagsArray into TagsRepository

`RealmTag.insert()` and `getTagsArray()` live in the model companion. TagsRepositoryImpl extends RealmRepository and should own all tag persistence logic.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/model/RealmTag.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmTag.kt#L35-L67"}
:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L9-L9"}

:::task-stub{title="Move RealmTag.insert and getTagsArray into TagsRepositoryImpl"}
1. Add `insertFromJson(act: JsonObject)` and `getTagsArray(tags: List<RealmTag>): JsonArray` to TagsRepository interface
2. Implement both in TagsRepositoryImpl using RealmRepository helpers
3. Update sync call sites to use TagsRepository instead of the companion
4. Remove companion object methods from RealmTag
:::

### Move RealmResourceActivity companion methods into ActivitiesRepository

`RealmResourceActivity.onSynced()` manually manages Realm transactions and queries RealmUser, crossing model boundaries. `serializeResourceActivities()` also belongs in the repository. ActivitiesRepositoryImpl already extends RealmRepository.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/model/RealmResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmResourceActivity.kt#L28-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=17 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L17-L17"}

:::task-stub{title="Move RealmResourceActivity.onSynced and serialize into ActivitiesRepositoryImpl"}
1. Add `recordSyncActivity(userId: String)` to ActivitiesRepository interface
2. Implement in ActivitiesRepositoryImpl using RealmRepository.executeTransaction, replacing raw beginTransaction/commitTransaction
3. Move `serializeResourceActivities()` into the repository
4. Update SyncManager and UploadManager call sites
5. Remove companion object methods from RealmResourceActivity
:::

### Extract DictionaryActivity Realm operations into a new DictionaryRepository

DictionaryActivity performs four direct Realm operations (count, bulk insert, search query) with no repository abstraction. This is the only UI file with zero repository coverage for its data domain.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L98"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L127"}

:::task-stub{title="Create DictionaryRepository and extract Realm access from DictionaryActivity"}
1. Create DictionaryRepository interface with `count()`, `isEmpty()`, `searchWord(word: String): RealmDictionary?`, `bulkInsert(jsonArray: JsonArray)`
2. Create DictionaryRepositoryImpl extending RealmRepository
3. Register the binding in RepositoryModule
4. Inject DictionaryRepository into DictionaryActivity, replacing all databaseService.withRealm calls
5. Remove direct Realm and DatabaseService usage from DictionaryActivity
:::

### Replace manual SharedPreferences in BellDashboardFragment with injected instance

BellDashboardFragment calls `requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)` in three separate places, bypassing the DI framework and creating multiple file handles instead of using an injected SharedPreferences.

:codex-file-citation[codex-file-citation]{line_range_start=159 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L159-L159"}
:codex-file-citation[codex-file-citation]{line_range_start=232 line_range_end=232 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L232-L232"}
:codex-file-citation[codex-file-citation]{line_range_start=252 line_range_end=252 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L252-L252"}

:::task-stub{title="Inject SharedPreferences into BellDashboardFragment instead of manual getSharedPreferences"}
1. Add a named Hilt provider for the survey-reminders SharedPreferences in SharedPreferencesModule (or reuse an existing qualifier)
2. Add `@Inject lateinit var surveyPrefs: SharedPreferences` field in BellDashboardFragment
3. Replace all three `requireActivity().getSharedPreferences(PREF_SURVEY_REMINDERS, 0)` calls with the injected field
4. Verify the fragment is annotated with `@AndroidEntryPoint`
:::

### Lift TeamFragment member-variable state into TeamViewModel

TeamFragment stores `user`, `teamList`, and `conditionApplied` in fragment member variables despite having a `TeamViewModel` instance. These fields are lost on configuration change and fragment recreation.

:codex-file-citation[codex-file-citation]{line_range_start=46 line_range_end=52 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt#L46-L52"}

:::task-stub{title="Move TeamFragment data state (user, teamList, conditionApplied) into TeamViewModel"}
1. Add `user: RealmUser?`, `teamList: List<TeamSummary>`, and `conditionApplied: Boolean` as state in TeamViewModel (use StateFlow or simple var)
2. Update TeamFragment to read/write these values through viewModel instead of local fields
3. Remove the corresponding member variables from TeamFragment
4. Verify configuration-change behavior is preserved
:::

### Lift ResourcesFragment member-variable state into a new ResourcesViewModel

ResourcesFragment holds `searchTags`, `tagsMap`, `allLibraryItems`, and `map` as fragment fields with no ViewModel. This state is lost on rotation and mixes data concerns into the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=76 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L66-L76"}

:::task-stub{title="Create ResourcesViewModel and move data state out of ResourcesFragment"}
1. Create ResourcesViewModel with StateFlows for searchTags, tagsMap, allLibraryItems, and map
2. Inject ResourcesRepository into the ViewModel for data loading
3. Update ResourcesFragment to observe ViewModel state instead of holding local mutable fields
4. Remove the corresponding member variables from ResourcesFragment
5. Register ViewModel usage with `by viewModels()` delegation
:::

### Make ConfigurationsRepositoryImpl extend RealmRepository

ConfigurationsRepositoryImpl directly holds a `databaseService` field without extending RealmRepository, missing out on its `queryList`, `findByField`, `executeTransaction`, and `withRealm` helpers. All other 18 repository implementations extend RealmRepository.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=48 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L40-L48"}
:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L21-L21"}

:::task-stub{title="Make ConfigurationsRepositoryImpl extend RealmRepository"}
1. Change class declaration to `class ConfigurationsRepositoryImpl @Inject constructor(..., databaseService: DatabaseService, ...) : RealmRepository(databaseService), ConfigurationsRepository`
2. Remove the private `databaseService` field (now inherited from RealmRepository)
3. Replace any raw `databaseService.withRealmAsync` calls with the inherited `withRealm` / `withRealmAsync` helpers
4. Verify no behavior change in configuration sync flows
:::

### Fix main-thread Realm query in DictionaryActivity search click listener

`DictionaryActivity.setClickListener()` runs `databaseService.withRealm { realm.where(...).findFirst() }` synchronously inside an onClick handler on the main thread, risking ANR on slow queries.

:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L127"}

:::task-stub{title="Move DictionaryActivity search query off the main thread"}
1. Wrap the search click handler body in `lifecycleScope.launch` with `withContext(Dispatchers.IO)` for the Realm query
2. Use `withContext(Dispatchers.Main)` to update the UI with the result
3. If task 5 (DictionaryRepository) is done first, call the repository suspend function instead
4. Verify the search still updates the UI correctly
:::

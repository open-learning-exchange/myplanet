### Extract DictionaryActivity Realm operations into a new DictionaryRepository

DictionaryActivity performs bulk JSON import, count queries, and keyword search directly against Realm inside an Activity. These three operations should live behind a DictionaryRepository interface + implementation pair, registered in RepositoryModule, matching the existing codebase convention and reusing RealmRepository helpers.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L127"}

:::task-stub{title="Extract DictionaryActivity Realm operations into DictionaryRepository"}
1. Create DictionaryRepository interface with three methods: getDictionaryCount(), importDictionaries(jsonArray), searchWord(word)
2. Create DictionaryRepositoryImpl extending RealmRepository, move lines 52-93 (import logic) and 101-127 (search logic) into it
3. Bind the interface in RepositoryModule
4. Inject DictionaryRepository into DictionaryActivity and replace all direct databaseService/Realm calls
:::

### Extract EditAchievementFragment Realm write operations into UserRepository

EditAchievementFragment runs executeTransaction to update achievements (lines 95-108), create-if-not-exists logic (lines 306-317), and a full library query (line 276-277) all inside the fragment. These belong in the existing UserRepository or a dedicated AchievementRepository, keeping the fragment free of data-layer concerns.

:codex-file-citation[codex-file-citation]{line_range_start=94 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L94-L114"}
:codex-file-citation[codex-file-citation]{line_range_start=304 line_range_end=322 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L304-L322"}
:codex-file-citation[codex-file-citation]{line_range_start=274 line_range_end=278 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L274-L278"}

:::task-stub{title="Extract EditAchievementFragment Realm writes into UserRepository"}
1. Add getOrCreateAchievement(achievementId) suspend method to UserRepository interface and impl
2. Add updateAchievement(achievementId, header, goals, purpose, sendToNation, achievements, references) suspend method
3. Move the library query (line 276) to ResourcesRepository.getAllLibraries() if not already present
4. Replace all databaseService.withRealmAsync calls in EditAchievementFragment with repository method calls
:::

### Move UploadManager.uploadUserActivities Realm queries into ActivitiesRepository

uploadUserActivities (lines 466-536) queries RealmOfflineActivity directly, filters guest users, serializes, and updates _rev fields all inline. The query-filter-serialize step and the post-upload field update should be repository methods, leaving UploadManager to only orchestrate the API call.

:codex-file-citation[codex-file-citation]{line_range_start=466 line_range_end=536 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L466-L536"}

:::task-stub{title="Move UploadManager.uploadUserActivities queries into ActivitiesRepository"}
1. Add getPendingLoginActivities() suspend method to ActivitiesRepository returning DTOs with activityId, userId, serialized JsonObject
2. Add markActivityUploaded(activityId, responseJson) suspend method to ActivitiesRepository
3. Update UploadManager.uploadUserActivities to call repository methods instead of databaseService.withRealm directly
:::

### Move UploadManager.uploadNews Realm queries into VoicesRepository

uploadNews (lines 592-712) queries RealmNews, filters guests, serializes, uploads images, then updates _id, _rev, images, and clears imageUrls directly via executeTransactionAsync. The data query and post-upload update are repository concerns.

:codex-file-citation[codex-file-citation]{line_range_start=592 line_range_end=712 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L592-L712"}

:::task-stub{title="Move UploadManager.uploadNews Realm queries into VoicesRepository"}
1. Add getPendingNews() suspend method to VoicesRepository returning DTOs with id, _id, message, imageUrls, newsJson
2. Add markNewsUploaded(newsId, responseId, responseRev, imagesJson) suspend method to VoicesRepository
3. Update UploadManager.uploadNews to call repository methods instead of databaseService.withRealm/executeTransactionAsync
:::

### Move UploadToShelfService.uploadHealth Realm operations into HealthRepository

uploadHealth (lines 283-311) queries RealmHealthExamination, serializes, posts to API, then updates _rev and isUpdated fields directly. Both the query and the post-upload update should be encapsulated behind a HealthRepository if one does not exist, or added to the nearest existing repository.

:codex-file-citation[codex-file-citation]{line_range_start=283 line_range_end=311 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L283-L311"}

:::task-stub{title="Move UploadToShelfService.uploadHealth Realm queries into a repository"}
1. Identify or create a HealthRepository interface + impl extending RealmRepository
2. Add getUpdatedHealthExaminations() suspend method returning unmanaged copies
3. Add markHealthUploaded(examinationId, rev) suspend method
4. Update UploadToShelfService.uploadHealth to call repository methods, removing direct Realm access
:::

### Move UploadToShelfService.getShelfData Realm queries into UserRepository

getShelfData (lines 447-459) and uploadToShelf (lines 360-406) query RealmUser, call helper functions like getMyMeetUpIds and removedIds that take a raw Realm instance, and assemble shelf JSON. This data-assembly logic should be in UserRepository so the service only orchestrates the API call.

:codex-file-citation[codex-file-citation]{line_range_start=360 line_range_end=406 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L360-L406"}
:codex-file-citation[codex-file-citation]{line_range_start=447 line_range_end=459 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L447-L459"}

:::task-stub{title="Move UploadToShelfService shelf data assembly into UserRepository"}
1. Add getAllSyncableUsers() suspend method to UserRepository returning unmanaged user copies with non-empty _id
2. Add buildShelfData(userId, serverShelfJson, myLibIds, myCourseIds) suspend method to UserRepository, internalizing getMyMeetUpIds and removedIds helpers
3. Update uploadToShelf and uploadSingleUserToShelf to call repository methods instead of passing raw Realm instances to helper functions
:::

### Migrate InlineResourceAdapter from RecyclerView.Adapter to ListAdapter with DiffUtils.itemCallback

InlineResourceAdapter is the only RecyclerView.Adapter in the codebase that does not use ListAdapter. It calls notifyDataSetChanged() on line 206 instead of using the project's DiffUtils.itemCallback utility. All 33 other adapters already follow the ListAdapter pattern.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L26-L29"}
:codex-file-citation[codex-file-citation]{line_range_start=204 line_range_end=207 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L204-L207"}

:::task-stub{title="Migrate InlineResourceAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change class declaration to extend ListAdapter<RealmMyLibrary, ViewHolder> with DiffUtils.itemCallback using id for areItemsTheSame
2. Replace the mutable resources property with getItem(position) in onBindViewHolder
3. Replace updateResources(newResources) body with submitList(newResources)
4. Remove getItemCount() override (ListAdapter provides it)
:::

### Replace notifyDataSetChanged with submitList re-submission in MembersAdapter and RequestsAdapter

Both MembersAdapter (line 36) and RequestsAdapter (line 39) already extend ListAdapter with DiffUtils.itemCallback but fall back to notifyDataSetChanged when a non-list property changes. Re-submitting the current list forces DiffUtil to re-bind visible items efficiently.

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=37 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt#L34-L37"}
:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt#L37-L40"}

:::task-stub{title="Replace notifyDataSetChanged with submitList re-submission in MembersAdapter and RequestsAdapter"}
1. In MembersAdapter.setUserId, replace notifyDataSetChanged() with submitList(currentList.toList()) to trigger DiffUtil
2. In RequestsAdapter.setUser, replace notifyDataSetChanged() with submitList(currentList.toList()) to trigger DiffUtil
:::

### Replace direct getSharedPreferences calls with Hilt @AppPreferences injection in fragments

At least 7 fragments call requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE) directly instead of using the existing Hilt @AppPreferences injection qualifier. The correct pattern is already established in CommunityTabFragment. Each fragment touched is a small, isolated change.

:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=104 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L101-L104"}
:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=94 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L90-L94"}

:::task-stub{title="Replace direct getSharedPreferences with Hilt @AppPreferences injection"}
1. In ResourcesFragment: replace line 103 settings assignment with @Inject @AppPreferences lateinit var settings: SharedPreferences, remove manual assignment in onCreate
2. In MyHealthFragment: replace line 93 settings assignment with @Inject @AppPreferences, remove manual assignment in onCreate
3. In RatingsFragment, BellDashboardFragment, UserInformationFragment, UserProfileFragment, HomeCommunityDialogFragment: apply the same pattern
:::

### Remove Dispatchers.IO from UI-layer fragments, ensure repositories handle their own dispatching

Multiple fragments call withContext(Dispatchers.IO) directly for repository or data operations. Repository suspend functions should handle their own dispatcher internally (RealmRepository already dispatches via databaseService), so the fragment should just call the suspend function without specifying a dispatcher.

:codex-file-citation[codex-file-citation]{line_range_start=124 line_range_end=133 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L124-L133"}
:codex-file-citation[codex-file-citation]{line_range_start=109 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L109-L114"}

:::task-stub{title="Remove Dispatchers.IO from UI-layer fragments that call repository methods"}
1. In ResourcesFragment.checkServerAndStartSync (line 128): remove withContext(Dispatchers.IO) wrapper, call updateServerIfNecessary directly since it should be a suspend function handling its own dispatcher
2. In MyHealthFragment.checkServerAndStartSync: apply the same pattern
3. In PersonalsFragment (lines 96, 131, 151): remove withContext(Dispatchers.IO) wrappers around repository calls that already handle dispatching internally
4. Verify each called repository method dispatches to IO internally via RealmRepository.withRealm or databaseService
:::

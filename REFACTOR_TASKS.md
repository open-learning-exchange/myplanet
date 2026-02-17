### Move DictionaryActivity Realm queries into a new DictionaryRepository

DictionaryActivity performs three direct Realm operations: checking if the dictionary is empty, bulk-inserting entries via `executeTransactionAsync`, and querying a word by exact match. All three should move behind a `DictionaryRepository` interface backed by `RealmRepository`, leaving the Activity with only UI and navigation logic.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=55 path="app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L55"}
:codex-file-citation[codex-file-citation]{line_range_start=71 line_range_end=88 path="app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L71-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=110 path="app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L103-L110"}
:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=98 path="app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L98"}

:::task-stub{title="Extract DictionaryRepository from DictionaryActivity"}
1. Create `DictionaryRepository` interface with `isDictionaryEmpty()`, `insertDictionaries(JsonArray)`, and `searchWord(String): RealmDictionary?`
2. Create `DictionaryRepositoryImpl` extending `RealmRepository` with the three methods above, moving the Realm queries from DictionaryActivity lines 54-55, 71-88, and 103-110
3. Bind the interface in `RepositoryModule`
4. Inject `DictionaryRepository` into `DictionaryActivity` via Hilt and replace all direct `databaseService.withRealm` calls
5. Remove `io.realm` imports from `DictionaryActivity`
:::

### Convert InlineResourceAdapter to ListAdapter with DiffUtils.itemCallback

InlineResourceAdapter extends `RecyclerView.Adapter` directly and calls `notifyDataSetChanged()` on every update. The codebase already provides `DiffUtils.itemCallback` and other adapters (e.g. `RequestsAdapter`, `ChatAdapter`) use `ListAdapter`. Converting this adapter aligns it with the project convention and enables efficient partial updates.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=30 path="app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L27-L30"}
:codex-file-citation[codex-file-citation]{line_range_start=203 line_range_end=208 path="app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L203-L208"}

:::task-stub{title="Migrate InlineResourceAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change `InlineResourceAdapter` to extend `ListAdapter<RealmMyLibrary, InlineResourceAdapter.ViewHolder>` with a `DiffUtils.itemCallback` comparing by `id` and `_rev`
2. Replace the `resources` field and `getItemCount()` override with `getItem(position)` from `ListAdapter`
3. Replace `updateResources()` body with `submitList(newResources)` and remove `notifyDataSetChanged()`
4. Update callers that pass the initial list to use `submitList()` after construction
:::

### Move RealmRating query and aggregation logic from model companion into RatingsRepository

`RealmRating.getRatings()` and `getRatingsById()` perform Realm queries and multi-step aggregation inside the model companion object. This data-access logic belongs in `RatingsRepositoryImpl` (which already extends `RealmRepository`), keeping the model as a plain data holder.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=51 path="app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L32-L51"}
:codex-file-citation[codex-file-citation]{line_range_start=53 line_range_end=68 path="app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L53-L68"}

:::task-stub{title="Move RealmRating.getRatings into RatingsRepository"}
1. Add `getRatings(type: String?, userId: String?): HashMap<String?, JsonObject>` and `getRatingsById(type, id, userId): JsonObject?` to `RatingsRepository` interface
2. Implement both methods in `RatingsRepositoryImpl` using `queryList` from `RealmRepository`, moving the aggregation logic from `RealmRating` companion lines 32-68
3. Deprecate the companion object methods in `RealmRating` with `@Deprecated("Use RatingsRepository")`
4. Update all callers of `RealmRating.getRatings` and `getRatingsById` to use the injected repository
:::

### Move RealmFeedback.insert from model companion into FeedbackRepository

`RealmFeedback.insert()` queries Realm for an existing feedback by `_id`, creates-or-updates, and sets all properties. This is data-access work that should live in `FeedbackRepositoryImpl`, not in the model companion object. The existing `FeedbackRepository` already extends `RealmRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=108 line_range_end=130 path="app/src/main/java/org/ole/planet/myplanet/model/RealmFeedback.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/model/RealmFeedback.kt#L108-L130"}

:::task-stub{title="Move RealmFeedback.insert into FeedbackRepository"}
1. Add `insertFromJson(JsonObject)` to `FeedbackRepository` interface
2. Implement the method in `FeedbackRepositoryImpl` using `DatabaseService`, moving the query-and-upsert logic from `RealmFeedback.insert()` (lines 108-130)
3. Deprecate `RealmFeedback.insert()` with `@Deprecated("Use FeedbackRepository.insertFromJson")`
4. Update sync-layer callers to use the repository method instead of the companion function
:::

### Move LoginActivity guest-user Realm creation into UserRepository

`LoginActivity` directly calls `RealmUser.createGuestUser()` inside `databaseService.withRealm` blocks in two places (click handler and guest dialog). This user-creation logic should be behind `UserRepository`, keeping the login UI free of Realm details.

:codex-file-citation[codex-file-citation]{line_range_start=482 line_range_end=485 path="app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L482-L485"}
:codex-file-citation[codex-file-citation]{line_range_start=514 line_range_end=518 path="app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L514-L518"}

:::task-stub{title="Move guest-user creation from LoginActivity into UserRepository"}
1. Add `createGuestUser(name: String, settings: SharedPreferences): RealmUser?` to `UserRepository` interface
2. Implement in `UserRepositoryImpl`, wrapping the existing `RealmUser.createGuestUser` + `copyFromRealm` pattern using `DatabaseService`
3. Inject `UserRepository` into `LoginActivity` (or its parent `SyncActivity`) via Hilt
4. Replace both `databaseService.withRealm { RealmUser.createGuestUser(...) }` call sites (lines 483-484 and 515-517) with the repository method
:::

### Move UploadToShelfService user queries into UserRepository

`UploadToShelfService.uploadUserData()` and `uploadSingleUserData()` perform direct Realm queries for `RealmUser` objects that need syncing. These data-access queries should be encapsulated in `UserRepository` so the service only orchestrates the upload flow.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=61 path="app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L55-L61"}
:codex-file-citation[codex-file-citation]{line_range_start=98 line_range_end=103 path="app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L98-L103"}

:::task-stub{title="Move UploadToShelfService user queries into UserRepository"}
1. Add `getPendingSyncUsers(limit: Int): List<RealmUser>` and `getUserByName(name: String): RealmUser?` to `UserRepository` interface
2. Implement both in `UserRepositoryImpl` using `queryList`/`findByField` from `RealmRepository`
3. Inject `UserRepository` into `UploadToShelfService`
4. Replace the `dbService.withRealm { realm.where(RealmUser::class.java)... }` blocks at lines 55-61 and 98-103 with the repository methods
:::

### Move UploadManager photo queries into SubmissionsRepository

`UploadManager.uploadSubmitPhotos()` queries `RealmSubmitPhotos` directly for unuploaded items and later updates their sync status. This data-access pattern crosses the repository boundary. The query, serialization lookup, and status update should be methods on `SubmissionsRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=229 line_range_end=242 path="app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L229-L242"}
:codex-file-citation[codex-file-citation]{line_range_start=262 line_range_end=270 path="app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L262-L270"}
:codex-file-citation[codex-file-citation]{line_range_start=273 line_range_end=277 path="app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L273-L277"}

:::task-stub{title="Move UploadManager photo queries into SubmissionsRepository"}
1. Add `getUnuploadedPhotos(): List<Pair<String?, JsonObject>>` and `markPhotoUploaded(photoId: String, rev: String, id: String)` to `SubmissionsRepository` interface
2. Implement both in `SubmissionsRepositoryImpl` using `DatabaseService`, moving the query from lines 229-242 and the transaction from lines 262-270
3. Inject `SubmissionsRepository` into `UploadManager`
4. Replace the direct Realm query and update blocks in `uploadSubmitPhotos()` with the repository methods
:::

### Extract MarkdownDialogFragment Realm query into a repository method

`MarkdownDialogFragment.setupCourseButton()` reaches through `DashboardActivity.databaseService` to perform a raw Realm query on `RealmUserChallengeActions`. This cross-layer access couples a UI dialog to both the host activity internals and the database layer. A single repository method would eliminate the leak.

:codex-file-citation[codex-file-citation]{line_range_start=109 line_range_end=115 path="app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt#L109-L115"}

:::task-stub{title="Extract MarkdownDialogFragment challenge query into a repository"}
1. Add `hasUserSyncAction(userId: String?): Boolean` to an existing repository interface (e.g. `UserRepository` or `ActivitiesRepository`)
2. Implement the method using `queryList` from `RealmRepository` to count `RealmUserChallengeActions` with the given userId and actionType "sync"
3. Make `MarkdownDialogFragment` an `@AndroidEntryPoint` and inject the repository
4. Replace the `dashboard.databaseService.withRealm { realm.where(RealmUserChallengeActions...) }` block at lines 109-115 with the repository call
5. Remove the cast to `DashboardActivity` for database access
:::

### Remove VoicesFragment direct transaction management and redundant Dispatchers.Main

VoicesFragment manually commits or cancels stale Realm transactions in `onCreateView` and uses `withContext(Dispatchers.Main)` inside `collect` blocks that already run on the main dispatcher via `repeatOnLifecycle`. The transaction guard is a symptom of leaked Realm instances; the redundant context switch adds unnecessary complexity.

:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=97 path="app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L91-L97"}
:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=131 path="app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L113-L131"}
:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=159 path="app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L146-L159"}

:::task-stub{title="Clean up VoicesFragment transaction guard and redundant dispatchers"}
1. Remove the `if (mRealm.isInTransaction)` commit/cancel block at lines 91-97 (the fragment should not manage raw transactions)
2. Remove the `kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main)` wrapper inside the `collect` block at lines 115-128 since `repeatOnLifecycle(STARTED)` already dispatches on Main
3. Remove the `kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main)` wrapper in the submit handler at lines 148-158 since `lifecycleScope.launch` defaults to Main
4. Verify the fragment no longer imports `kotlinx.coroutines.Dispatchers` directly
:::

### Replace Realm.getDefaultInstance retry loop in RealmRepository with DatabaseService

`RealmRepository.queryAsFlow()` manually calls `Realm.getDefaultInstance()` in a busy-wait retry loop (up to 10 retries with 50ms delay) to work around in-transaction states. This bypasses the injected `DatabaseService` and its connection management. The loop should use `databaseService` for Realm acquisition, which already handles instance lifecycle.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=100 path="app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L84-L100"}
:codex-file-citation[codex-file-citation]{line_range_start=138 line_range_end=138 path="app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt" git_url="https://github.com/open-learning-exchange/myplanet/blob/47e4197da7f7baac95288dda86e3f30ceff3217f/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L138-L138"}

:::task-stub{title="Replace manual Realm.getDefaultInstance in RealmRepository with DatabaseService"}
1. Replace the retry-loop at lines 84-100 that calls `Realm.getDefaultInstance()` with a call to `databaseService` to obtain a Realm instance
2. Keep the `awaitClose` cleanup at line 131-133 to close the instance when the Flow completes
3. Verify `databaseService.ioDispatcher` usage at line 113 is consistent with the new instance acquisition
4. Remove the direct `import io.realm.Realm` if no longer needed after the change
:::

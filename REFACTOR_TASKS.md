### Move DictionaryActivity's raw Realm access into a DictionaryRepository

`DictionaryActivity` is the last UI class that talks to `DatabaseService`/`Realm` directly: it counts rows, bulk-inserts the dictionary JSON inside `executeTransactionAsync`, and runs the word lookup query straight from the click listener. Pull these three operations behind a small `DictionaryRepository` (extending `RealmRepository`) so the activity only renders results, finishing the data-layer cleanup CLAUDE.md flags. This file is untouched by any other task, so it merges in isolation.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=135 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60-L135"}

:::task-stub{title="Extract DictionaryRepository and remove DatabaseService from DictionaryActivity"}
1. Add `DictionaryRepository`/`DictionaryRepositoryImpl` (extending `RealmRepository`) with `suspend fun count(): Long`, `suspend fun seedFromJson(json: JsonArray)`, and `suspend fun findWord(word: String): RealmDictionary?`, using `count`, `executeTransaction`, and `findByField`/`withRealm` helpers.
2. Bind the interface in `RepositoryModule` with an `@Binds` method.
3. Inject `DictionaryRepository` into `DictionaryActivity`, replace the three inline Realm blocks (`loadDictionaryCount`, `loadDictionaryIfNeeded`, `setClickListener`) with repository calls.
4. Delete the `DatabaseService` field and the `io.realm.*` imports from the activity.
:::

### Move getMyLifeListBase seed-data construction into LifeRepository

`DashboardPluginFragment.getMyLifeListBase()` builds the canonical list of `RealmMyLife` rows (the My Life menu seed) in the UI layer, and `BaseDashboardFragment` then hands that UI-built list back to `lifeRepository.seedMyLifeIfEmpty(...)`. The seed definition is data the repository owns, so the repository should produce it rather than receive it from a fragment. Moving it keeps the menu definition on the data side and shrinks the fragment.

:codex-file-citation[codex-file-citation]{line_range_start=151 line_range_end=161 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt#L151-L161"}

:codex-file-citation[codex-file-citation]{line_range_start=298 line_range_end=298 path=app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt#L298"}

:::task-stub{title="Let LifeRepository own the default My Life seed list"}
1. Add `seedMyLifeIfEmpty(userId: String?, titles: ...)` overload (or a `defaultMyLifeItems(userId)` helper) to `LifeRepository` that builds the seven `RealmMyLife` rows from string resources passed in.
2. Update `BaseDashboardFragment` (line 298) to call the repository without first calling `getMyLifeListBase`.
3. Remove `getMyLifeListBase` from `DashboardPluginFragment`, passing only the localized titles the repository needs.
:::

### Stop leaking io.realm.RealmList through LocalResourceRequest

`LocalResourceRequest` is a repository request DTO, yet three of its fields are typed `io.realm.RealmList<String>`, which forces `AddResourceActivity` to import `io.realm.RealmList` and allocate Realm collections just to gather spinner selections. The Realm type belongs inside the repository where the `RealmObject` is built, not in the UI. Switch the DTO to plain `List<String>?` and convert at the persistence boundary.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L44-L46"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceActivity.kt#L47-L69"}

:::task-stub{title="Type LocalResourceRequest selections as List instead of RealmList"}
1. Change `subjects`/`levels`/`resourceFor` in `LocalResourceRequest` to `List<String>?`.
2. In `ResourcesRepositoryImpl.saveLocalResource`, wrap the incoming lists in `RealmList(...)` when constructing the `RealmMyLibrary` inside the existing transaction.
3. In `AddResourceActivity`, change the three fields to `MutableList<String>`/`ArrayList()` and delete the `io.realm.RealmList` import.
:::

### Tighten ChatRepository: own the sync-insert transaction

`ChatRepository.bulkInsertFromSync(realm, jsonArray)` takes a live `Realm` from `TransactionSyncManager`, leaking the transaction boundary into the repository interface. `VoicesRepository.insertNewsList` and `FeedbackRepository.insertFeedbackList` already show the target shape: a `suspend` method that opens its own `executeTransaction`. Migrating chat to that shape removes Realm from one more interface; the only shared edit is a single non-adjacent line in `TransactionSyncManager`.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L20"}

:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=248 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L248"}

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt#L51"}

:::task-stub{title="Replace ChatRepository.bulkInsertFromSync(realm,...) with a self-contained insert"}
1. Add `suspend fun insertChatHistoryFromSync(docs: List<JsonObject>)` to `ChatRepository`, mirroring `VoicesRepository.insertNewsList`.
2. In `ChatRepositoryImpl`, wrap the existing per-doc insert logic in `executeTransaction { realm -> ... }` and delete the `bulkInsertFromSync(realm, ...)` override.
3. Change `TransactionSyncManager` line 248 to `chatRepository.insertChatHistoryFromSync(arr.map { it.asJsonObject })`.
:::

### Tighten ActivitiesRepository: own the login-activities sync transaction

`ActivitiesRepository.bulkInsertLoginActivitiesFromSync(realm, jsonArray)` accepts the sync manager's `Realm`, the same boundary leak as the other sync inserts. Convert it to a self-contained `suspend` insert so the repository, not the service, owns the write transaction.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L32"}

:codex-file-citation[codex-file-citation]{line_range_start=250 line_range_end=250 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L250"}

:::task-stub{title="Replace ActivitiesRepository.bulkInsertLoginActivitiesFromSync(realm,...)"}
1. Add `suspend fun insertLoginActivitiesFromSync(docs: List<JsonObject>)` to `ActivitiesRepository`.
2. In `ActivitiesRepositoryImpl`, move the insert body into `executeTransaction { realm -> ... }` and drop the `Realm` parameter overload.
3. Change `TransactionSyncManager` line 250 to call the new method with `arr.map { it.asJsonObject }`.
:::

### Tighten RatingsRepository: own the sync-insert transaction

`RatingsRepository.bulkInsertFromSync(realm, jsonArray)` carries the same `Realm`-in-the-interface leak. Move the transaction ownership inside the repository following the established `insertNewsList`/`insertFeedbackList` precedent.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt#L18"}

:codex-file-citation[codex-file-citation]{line_range_start=252 line_range_end=252 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L252"}

:::task-stub{title="Replace RatingsRepository.bulkInsertFromSync(realm,...)"}
1. Add `suspend fun insertRatingsFromSync(docs: List<JsonObject>)` to `RatingsRepository`.
2. In `RatingsRepositoryImpl`, wrap the insert logic in `executeTransaction { realm -> ... }` and remove the `Realm`-parameter override.
3. Update `TransactionSyncManager` line 252 to use the new method.
:::

### Tighten CoursesRepository: own the courses sync-insert transaction

`CoursesRepository.bulkInsertFromSync(realm, jsonArray)` takes the shared `Realm`. It is the same one-method migration; keep the adjacent `saveConcatenatedLinksToPrefs` call exactly where it is so this change stays a single non-adjacent line edit in the sync manager.

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L66"}

:codex-file-citation[codex-file-citation]{line_range_start=255 line_range_end=255 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L255"}

:::task-stub{title="Replace CoursesRepository.bulkInsertFromSync(realm,...)"}
1. Add `suspend fun insertCoursesFromSync(docs: List<JsonObject>)` to `CoursesRepository`.
2. In `CoursesRepositoryImpl`, move the insert into `executeTransaction { realm -> ... }` and delete the `Realm`-parameter override.
3. Change only line 255 in `TransactionSyncManager`; leave the `saveConcatenatedLinksToPrefs` line 256 untouched.
:::

### Tighten CommunityRepository: own the meetups sync-insert transaction

`CommunityRepository.bulkInsertFromSync(realm, jsonArray)` (the `meetups` table path) still requires the caller's `Realm`. Convert it to a self-contained insert so meetup syncing no longer depends on an externally managed transaction.

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepository.kt#L10"}

:codex-file-citation[codex-file-citation]{line_range_start=261 line_range_end=261 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L261"}

:::task-stub{title="Replace CommunityRepository.bulkInsertFromSync(realm,...)"}
1. Add `suspend fun insertMeetupsFromSync(docs: List<JsonObject>)` to `CommunityRepository`.
2. In `CommunityRepositoryImpl`, wrap the insert logic in `executeTransaction { realm -> ... }` and remove the `Realm`-parameter override.
3. Update `TransactionSyncManager` line 261 to call the new method.
:::

### Tighten ProgressRepository: own the courses_progress sync-insert transaction

`ProgressRepository.bulkInsertFromSync(realm, jsonArray)` (the `courses_progress` path) keeps the `Realm`-parameter leak. Migrate it to a self-managed transaction, matching the rest of this round's interface tightening.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=24 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L24"}

:codex-file-citation[codex-file-citation]{line_range_start=264 line_range_end=264 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L264"}

:::task-stub{title="Replace ProgressRepository.bulkInsertFromSync(realm,...)"}
1. Add `suspend fun insertCourseProgressFromSync(docs: List<JsonObject>)` to `ProgressRepository`.
2. In `ProgressRepositoryImpl`, move the insert into `executeTransaction { realm -> ... }` and drop the `Realm`-parameter override.
3. Update `TransactionSyncManager` line 264 to call the new method.
:::

### Tighten UserSyncRepository: own the tablet_users sync-insert transaction

`UserSyncRepository.bulkInsertUsersFromSync(realm, jsonArray)` (the `tablet_users` path) takes the sync manager's `Realm`. Leave `bulkInsertAchievementsFromSync` for a later round (its sync line sits next to the teams block) and migrate only the users path so this PR edits a single, well-separated line.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt#L16"}

:codex-file-citation[codex-file-citation]{line_range_start=246 line_range_end=246 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/3a597660107340b48f26162508739a3481f7e094/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L246"}

:::task-stub{title="Replace UserSyncRepository.bulkInsertUsersFromSync(realm,...)"}
1. Add `suspend fun insertUsersFromSync(docs: List<JsonObject>)` to `UserSyncRepository`.
2. In its impl, wrap the existing user-insert logic in `executeTransaction { realm -> ... }` and remove the `Realm`-parameter override.
3. Update `TransactionSyncManager` line 246 to call the new method; leave the `achievements`/`tablet_users` neighbours untouched.
:::

### Testing

- Run `./gradlew testDefaultDebugUnitTest` (the CI gate); add/adjust repository unit tests next to existing ones for any repository whose interface changes (Dictionary, Chat, Activities, Ratings, Courses, Community, Progress, UserSync).
- Manually verify a full sync still imports each affected table, and that the dictionary search, My Life dashboard tiles, and Add Resource save still work.

#### Merge-order note (avoiding conflicts this round)

Tasks 1–3 touch disjoint files and never collide. Tasks 4–10 each change **exactly one** line in `TransactionSyncManager.kt`'s `syncDb` `when` block, on deliberately **non-adjacent** lines (248, 250, 252, 255, 261, 264, 246) with at least one unchanged line between every pair, and none of them alters the line count — so git's 3-way merge applies them as independent hunks with no rebase drift. Keep each sync table in its own PR (do **not** split adjacent cases such as tags/ratings/submissions across two PRs in the same round) and the seven repository-tightening PRs merge cleanly in any order. `RepositoryModule` only receives append-only `@Binds` additions, which also merge without conflict.

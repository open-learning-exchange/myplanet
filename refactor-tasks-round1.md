### Move SurveyFormState out of the UI package into the model layer

The `SurveysRepository` interface and `SurveysRepositoryImpl` both import `SurveyFormState` from `ui.surveys`, creating an upward dependency from the repository layer into the UI layer. This violates layered architecture: the data/repository layer must never depend on UI types. `SurveyFormState` is a pure data class with no UI logic and belongs in the `model/` package.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt#L6-L6"}
:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L21-L21"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFormState.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFormState.kt#L1-L8"}

:::task-stub{title="Move SurveyFormState from ui.surveys to model package"}
1. Move `SurveyFormState.kt` from `ui/surveys/` to `model/`
2. Update its package declaration to `org.ole.planet.myplanet.model`
3. Update imports in `SurveysRepository.kt`, `SurveysRepositoryImpl.kt`, and any UI files that reference it
4. Verify build succeeds
:::

### Extract DictionaryActivity Realm operations into a new repository

`DictionaryActivity` performs raw Realm queries and transactions directly: counting entries, bulk-inserting from JSON, and searching by word. This bypasses the repository layer entirely. All dictionary data operations should live in a `DictionaryRepository` behind an interface, following the project's established repository pattern.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L70-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L98"}
:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=110 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L103-L110"}

:::task-stub{title="Create DictionaryRepository and move Realm logic out of DictionaryActivity"}
1. Create `DictionaryRepository` interface with `countEntries()`, `insertFromJson(JsonArray)`, and `searchWord(String): RealmDictionary?`
2. Create `DictionaryRepositoryImpl` extending `RealmRepository`, implementing the three methods using `queryList()`, `save()`, and `findByField()`
3. Bind the interface in `RepositoryModule`
4. Inject `DictionaryRepository` into `DictionaryActivity` and replace all direct Realm calls
5. Remove Realm imports from `DictionaryActivity`
:::

### Move RealmChatHistory.insert and addConversation into ChatRepository

`RealmChatHistory` has companion object methods `insert()` and `addConversationToChatHistory()` that contain Realm queries and object creation. These are data-access operations that should live in the repository layer. `ChatRepository` already exists and is the natural home for this logic.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/model/RealmChatHistory.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmChatHistory.kt#L26-L40"}
:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/model/RealmChatHistory.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmChatHistory.kt#L49-L65"}

:::task-stub{title="Move RealmChatHistory companion insert/addConversation methods to ChatRepositoryImpl"}
1. Add `insertChatHistory(realm: Realm, json: JsonObject)` and `addConversation(realm: Realm, chatHistoryId: String?, query: String?, response: String?, newRev: String?)` methods to `ChatRepositoryImpl`
2. Move the body of the companion methods into the repository methods
3. Update callers in `TransactionSyncManager` and any other call sites to use the repository
4. Remove the companion methods from `RealmChatHistory`
:::

### Move RealmOfflineActivity data functions into ActivitiesRepository

`RealmOfflineActivity` has companion methods `insert()`, `getRecentLogin()`, and `serializeLoginActivities()` that perform Realm queries and JSON serialization. These are data-layer operations that belong in `ActivitiesRepository`, which already exists for this domain.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/model/RealmOfflineActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmOfflineActivity.kt#L38-L56"}
:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/model/RealmOfflineActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmOfflineActivity.kt#L59-L63"}
:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/model/RealmOfflineActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmOfflineActivity.kt#L66-L96"}

:::task-stub{title="Move RealmOfflineActivity companion methods to ActivitiesRepositoryImpl"}
1. Add `insertActivity(realm: Realm, json: JsonObject)`, `getRecentLogin(realm: Realm): RealmOfflineActivity?`, and `serializeLoginActivities(activity: RealmOfflineActivity, context: Context): JsonObject` to `ActivitiesRepositoryImpl`
2. Move method bodies from the companion object into the repository
3. Update callers (sync managers, upload manager) to use the repository
4. Remove companion methods from `RealmOfflineActivity`
:::

### Move RealmTeamLog data functions into TeamsRepository

`RealmTeamLog` has companion methods `insert()`, `getLastVisit()`, and `serializeTeamActivities()` that perform Realm queries and JSON serialization. `TeamsRepository` already exists and should own all team-related data access, including team activity logs.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/model/RealmTeamLog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmTeamLog.kt#L27-L33"}
:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=53 path=app/src/main/java/org/ole/planet/myplanet/model/RealmTeamLog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmTeamLog.kt#L36-L53"}
:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/model/RealmTeamLog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmTeamLog.kt#L56-L73"}

:::task-stub{title="Move RealmTeamLog companion methods to TeamsRepositoryImpl"}
1. Add `insertTeamLog(realm: Realm, json: JsonObject)`, `getLastVisit(realm: Realm, userName: String?, teamId: String?): Long?`, and `serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject` to `TeamsRepositoryImpl`
2. Move method bodies from the companion object
3. Update callers to use the repository
4. Remove companion methods from `RealmTeamLog`
:::

### Move RealmResourceActivity.onSynced into ActivitiesRepository

`RealmResourceActivity.onSynced()` manually manages Realm transactions (beginTransaction/commitTransaction/cancelTransaction), queries for a user, and creates a resource activity record. This is pure data-layer logic and belongs in the repository that owns activity tracking.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/model/RealmResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmResourceActivity.kt#L43-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/model/RealmResourceActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmResourceActivity.kt#L28-L40"}

:::task-stub{title="Move RealmResourceActivity.onSynced and serialize to ActivitiesRepositoryImpl"}
1. Add `recordSyncActivity(realm: Realm, settings: SharedPreferences)` to `ActivitiesRepositoryImpl` with the body from `onSynced()`
2. Add `serializeResourceActivities(activity: RealmResourceActivity): JsonObject` to the repository
3. Use `RealmRepository.executeTransaction()` instead of manual transaction management
4. Update callers to use the repository
5. Remove companion methods from `RealmResourceActivity`
:::

### Move TransactionSyncManager notification queries to NotificationsRepository

`TransactionSyncManager.syncNotificationReads()` directly queries Realm for `RealmNotification` objects with `needsSync=true`, builds JSON payloads, and updates records after sync. The query and update logic should live in `NotificationsRepository`; only the API call orchestration belongs in the sync manager.

:codex-file-citation[codex-file-citation]{line_range_start=325 line_range_end=383 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L325-L383"}

:::task-stub{title="Extract notification Realm queries from TransactionSyncManager into NotificationsRepository"}
1. Add `getPendingSyncNotifications(): List<RealmNotification>` to `NotificationsRepository` interface and impl
2. Add `markNotificationsSynced(syncResults: List<Pair<String, String?>>)` to `NotificationsRepository`
3. In `TransactionSyncManager.syncNotificationReads()`, replace the direct Realm queries (lines 326-332 and 370-381) with repository calls
4. Keep the API call logic in `TransactionSyncManager`
:::

### Remove io.realm.Sort leak from DashboardViewModel

`DashboardViewModel` imports `io.realm.Sort` and passes it directly to repository methods. This couples the ViewModel to the Realm API. The repository interface should accept a domain-level sort direction or use a simple Boolean/enum instead, so the ViewModel stays Realm-agnostic.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=7 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L7-L7"}
:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt#L29-L29"}

:::task-stub{title="Replace io.realm.Sort usage in repository interfaces and ViewModels with a domain enum"}
1. Create a simple `SortOrder` enum (ASCENDING, DESCENDING) in the `model/` or `repository/` package
2. Update repository interfaces that accept `io.realm.Sort` to accept `SortOrder` instead
3. Map `SortOrder` to `io.realm.Sort` inside the repository implementations
4. Remove `io.realm.Sort` imports from all ViewModels
:::

### Move RealmSubmission.insert logic into SubmissionsRepository

`RealmSubmission.insert()` is a 140-line companion method that manages its own transactions, queries existing submissions, and creates/updates complex object graphs (answers, team references, membership docs). This is the most complex model-level data operation and should be owned by `SubmissionsRepositoryImpl` for testability and to enforce repository boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L47-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=186 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L90-L186"}
:codex-file-citation[codex-file-citation]{line_range_start=189 line_range_end=204 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L189-L204"}

:::task-stub{title="Move RealmSubmission.insert and serializeExamResult to SubmissionsRepositoryImpl"}
1. Add `insertSubmission(realm: Realm, json: JsonObject)` to `SubmissionsRepositoryImpl` with the full insert logic
2. Move the private helpers (`updateBasicFields`, `updateTeam`, `updateMembership`, `updateUserId`, `updateAnswers`) as private methods in the repository
3. Add `serializeExamResult(realm: Realm, sub: RealmSubmission, context: Context, spm: SharedPrefManager): JsonObject` to the repository
4. Update all callers (sync managers, upload manager) to use the repository
5. Remove companion methods from `RealmSubmission`
:::

### Move TransactionSyncManager.insertToChat and insertDocs into respective repositories

`TransactionSyncManager` has `insertToChat()` and `insertDocs()` methods that parse JSON arrays and delegate to model-level insert methods. These are data transformation and insertion operations that belong in their respective domain repositories. The sync manager should call repository methods directly rather than doing its own JSON extraction loop.

:codex-file-citation[codex-file-citation]{line_range_start=266 line_range_end=276 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L266-L276"}
:codex-file-citation[codex-file-citation]{line_range_start=278 line_range_end=306 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L278-L306"}
:codex-file-citation[codex-file-citation]{line_range_start=309 line_range_end=323 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/eafdfa6da2262a17d3b4800416ada2e46d06a895/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L309-L323"}

:::task-stub{title="Move insertToChat and insertDocs from TransactionSyncManager to repositories"}
1. Add `insertChatHistoryBatch(realm: Realm, jsonArray: JsonArray)` to `ChatRepositoryImpl` with the JSON extraction loop from `insertToChat()`
2. Add a `bulkInsertFromSync(realm: Realm, jsonArray: JsonArray)` method to the appropriate domain repositories for generic document insertion
3. Replace `insertToChat()` call in `TransactionSyncManager` with `chatRepository.insertChatHistoryBatch()`
4. Replace `insertDocs()` call with the appropriate repository call
5. Remove `insertToChat()`, `insertDocs()`, `continueInsert()`, and `callMethod()` from `TransactionSyncManager`
:::

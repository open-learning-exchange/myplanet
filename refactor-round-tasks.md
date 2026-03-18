### Move DictionaryActivity Realm queries into a new DictionaryRepository

DictionaryActivity performs direct Realm queries for count, search-by-word, and bulk-insert operations instead of delegating to a repository. Extracting these into a DictionaryRepository + DictionaryRepositoryImpl (extending RealmRepository) enforces the repository boundary and removes all Realm imports from the UI layer.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L54-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L95-L127"}

:::task-stub{title="Extract DictionaryRepository from DictionaryActivity"}
1. Create DictionaryRepository interface with isDictionaryEmpty(), getDictionaryCount(), loadDictionaryFromJson(JsonArray), and searchByWord(String) methods
2. Create DictionaryRepositoryImpl extending RealmRepository with implementations for all four methods
3. Register the binding in RepositoryModule
4. Inject DictionaryRepository into DictionaryActivity and replace all databaseService.withRealm calls
:::

### Move UploadManager login-activity queries into ActivitiesRepository

UploadManager.uploadUserActivities() directly queries RealmOfflineActivity with Realm where-clauses, filters out guest users, serializes results, and updates _rev fields after upload. These data-access concerns belong in the ActivitiesRepository, not in the upload service.

:codex-file-citation[codex-file-citation]{line_range_start=563 line_range_end=609 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L563-L609"}

:::task-stub{title="Move login-activity Realm queries from UploadManager to ActivitiesRepository"}
1. Add getUnuploadedLoginActivities() to ActivitiesRepository interface returning serialized DTOs (excluding guests)
2. Add markActivitiesUploaded(ids, revMap) to ActivitiesRepository interface
3. Implement both methods in ActivitiesRepositoryImpl using RealmRepository helpers (queryList, executeTransaction)
4. Replace direct Realm queries in UploadManager.uploadUserActivities() with the new repository calls
:::

### Move UploadManager team-log queries into a TeamLogsRepository method

UploadManager.uploadTeamActivities() queries RealmTeamLog where _rev is null, serializes each log, then performs a chunked Realm transaction to update uploaded entries. This should be behind the repository boundary.

:codex-file-citation[codex-file-citation]{line_range_start=638 line_range_end=679 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L638-L679"}

:::task-stub{title="Move team-log Realm queries from UploadManager into ActivitiesRepository"}
1. Add getUnuploadedTeamLogs() to ActivitiesRepository interface returning serialized team log data
2. Add markTeamLogsUploaded(results) to ActivitiesRepository interface
3. Implement both in ActivitiesRepositoryImpl extending RealmRepository
4. Replace direct Realm queries in UploadManager.uploadTeamActivities() with the new repository calls
:::

### Move UploadManager news queries into VoicesRepository

UploadManager.uploadNews() directly queries RealmNews, filters out guest users, serializes via chatRepository, then updates _rev fields in a Realm transaction. The query and update belong in VoicesRepository so UploadManager only orchestrates the HTTP upload.

:codex-file-citation[codex-file-citation]{line_range_start=755 line_range_end=768 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L755-L768"}

:::task-stub{title="Move news upload queries from UploadManager to VoicesRepository"}
1. Add getNewsForUpload() to VoicesRepository interface returning serialized news data (excluding guests)
2. Add markNewsUploaded(idToRevMap) to VoicesRepository interface
3. Implement both in VoicesRepositoryImpl using RealmRepository helpers
4. Replace direct Realm queries in UploadManager.uploadNews() with the new repository calls
:::

### Move UploadToShelfService health queries into HealthRepository methods

UploadToShelfService.uploadHealth() and uploadSingleUserHealth() perform direct Realm queries on RealmHealthExamination, copy from Realm, and mark records as uploaded. These are pure data-layer operations that belong behind the existing HealthRepository interface.

:codex-file-citation[codex-file-citation]{line_range_start=287 line_range_end=326 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L287-L326"}
:codex-file-citation[codex-file-citation]{line_range_start=328 line_range_end=384 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L328-L384"}

:::task-stub{title="Move health-exam Realm queries from UploadToShelfService to HealthRepository"}
1. Add getUpdatedHealthExaminations() and getUpdatedHealthForUser(userId) to HealthRepository interface
2. Add markHealthExaminationsUploaded(idToRevMap) to HealthRepository interface
3. Implement all three in HealthRepositoryImpl using RealmRepository helpers (queryList, executeTransaction)
4. Replace direct Realm queries in UploadToShelfService.uploadHealth() and uploadSingleUserHealth() with repository calls
:::

### Move UploadToShelfService user queries into UserRepository

UploadToShelfService queries RealmUser directly in uploadToShelf() and uploadSingleUserToShelf() to get synced users and look up users by name. These are standard data-access operations that UserRepository should provide.

:codex-file-citation[codex-file-citation]{line_range_start=386 line_range_end=432 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L386-L432"}
:codex-file-citation[codex-file-citation]{line_range_start=435 line_range_end=472 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L435-L472"}

:::task-stub{title="Move user Realm queries from UploadToShelfService to UserRepository"}
1. Add getSyncedUsers() returning List of copied RealmUser (isNotEmpty _id, excluding guests) to UserRepository interface
2. Add getSyncedUserByName(name) to UserRepository interface
3. Implement both in UserRepositoryImpl using RealmRepository queryList/findByField helpers
4. Replace direct Realm queries in UploadToShelfService.uploadToShelf() and uploadSingleUserToShelf() with repository calls
:::

### Move UploadToShelfService shelf-data assembly into a ShelfRepository

getShelfData() in UploadToShelfService performs Realm queries (getMyMeetUpIds, removedIds) and constructs the shelf JSON document. This data-transformation and query logic crosses the service-data boundary and should live in a dedicated repository.

:codex-file-citation[codex-file-citation]{line_range_start=474 line_range_end=483 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L474-L483"}

:::task-stub{title="Extract shelf-data assembly from UploadToShelfService into a new ShelfRepository"}
1. Create ShelfRepository interface with buildShelfData(userId, jsonDoc, myLibs, myCourseIds) method
2. Create ShelfRepositoryImpl extending RealmRepository, moving getShelfData(), getMyMeetUpIds(), removedIds(), and mergeJsonArray() into it
3. Register the binding in RepositoryModule
4. Inject ShelfRepository into UploadToShelfService and replace direct Realm calls and getShelfData() usage
:::

### Move RetryQueue Realm operations behind RealmRepository pattern

RetryQueue is a @Singleton service that performs 10+ direct Realm where-queries for status management (markInProgress, markCompleted, markFailed, resetAllPending, safeClearQueue, recoverStuckOperations). It should extend or delegate to RealmRepository for all persistence.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L49-L86"}
:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=227 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L123-L227"}

:::task-stub{title="Move RetryQueue Realm queries into a RetryOperationRepository"}
1. Create RetryOperationRepository interface with findPendingByItemId(itemId, uploadType), markInProgress(id), markCompleted(id), markFailed(id, error, httpCode), resetAllPending(), safeClear(), and recoverStuck() methods
2. Create RetryOperationRepositoryImpl extending RealmRepository
3. Register the binding in RepositoryModule
4. Refactor RetryQueue to inject and delegate to RetryOperationRepository for all Realm operations, keeping only orchestration logic (mutex, AtomicBoolean) in RetryQueue
:::

### Move JSON construction out of UserInformationFragment into SubmissionsRepository

UserInformationFragment.submitForm() builds a JsonObject with 10+ user-profile fields inline in the Fragment, then passes raw JSON to the repository. The JSON structure is a data-layer concern and should be assembled by the repository from typed parameters.

:codex-file-citation[codex-file-citation]{line_range_start=175 line_range_end=259 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L175-L259"}

:::task-stub{title="Move user-profile JSON assembly from UserInformationFragment to SubmissionsRepository"}
1. Create a data class UserSurveyProfile (fname, lname, mName, email, phone, dob, yob, level, gender, language) in model/
2. Add buildUserProfileJson(profile: UserSurveyProfile) to SubmissionsRepository interface
3. Implement in SubmissionsRepositoryImpl, moving the JsonObject construction logic from the Fragment
4. Update UserInformationFragment.submitForm() to construct a UserSurveyProfile DTO and call the repository method
:::

### Move UploadManager achievement queries into SubmissionsRepository

UploadManager.uploadAchievement() runs a direct Realm transaction querying all RealmAchievement objects and processing them in batches. This data access should go through an existing or new repository method so the upload service doesn't hold Realm query logic.

:codex-file-citation[codex-file-citation]{line_range_start=200 line_range_end=213 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L200-L213"}

:::task-stub{title="Move achievement Realm queries from UploadManager to SubmissionsRepository"}
1. Add getAchievementsForUpload() to SubmissionsRepository interface, returning non-guest achievements as serialized data
2. Implement in SubmissionsRepositoryImpl using RealmRepository queryList helper with guest filtering
3. Replace the direct executeTransactionAsync + realm.where(RealmAchievement) in UploadManager.uploadAchievement() with the repository call
:::

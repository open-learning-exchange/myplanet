### Move DictionaryActivity Realm queries into a new DictionaryRepository

DictionaryActivity performs four direct Realm operations (count, bulk insert, search) entirely inside the UI layer, bypassing the repository pattern used everywhere else. Extracting these into a DictionaryRepository brings dictionary data access in line with the existing 19-domain repository convention and removes raw Realm imports from UI code.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L98"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L127"}

:::task-stub{title="Create DictionaryRepository and move Realm access out of DictionaryActivity"}
1. Create DictionaryRepository interface with getDictionaryCount(), searchWord(word), and loadDictionaryFromJson(jsonArray) methods
2. Create DictionaryRepositoryImpl extending RealmRepository, inject DatabaseService, move the four Realm operations from DictionaryActivity lines 54-110 into the impl
3. Bind the new repository in RepositoryModule
4. Update DictionaryActivity to inject DictionaryRepository and call repository methods instead of databaseService.withRealm
5. Remove direct Realm and RealmDictionary imports from DictionaryActivity
:::

### Move RealmMyCourse companion object query helpers into CoursesRepositoryImpl

RealmMyCourse.companion contains getMyByUserId(), getMyCourseByUserId(), getAllCourses(), and insertMyCourses() — all performing direct Realm queries inside the model layer. These belong in CoursesRepositoryImpl so that the model stays a plain data holder and the repository owns data access.

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L66-L90"}
:codex-file-citation[codex-file-citation]{line_range_start=170 line_range_end=189 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L170-L189"}

:::task-stub{title="Move RealmMyCourse query helpers from companion into CoursesRepositoryImpl"}
1. Add getMyCoursesByUserId(userId) and getAllCourses() methods to CoursesRepository interface
2. Implement them in CoursesRepositoryImpl using RealmRepository.queryListFlow or direct Realm queries
3. Update each call site of RealmMyCourse.getMyCourseByUserId() and RealmMyCourse.getMyByUserId() to use the injected CoursesRepository instead
4. Deprecate or remove the static companion methods once all callers are migrated
:::

### Move RealmMyTeam static query methods into TeamsRepositoryImpl

RealmMyTeam.companion holds getResourceIds(), getResourceIdsByUser(), getTeamCreator(), isTeamLeader(), and getMyTeamsByUserId() — all taking a raw Realm parameter and running queries. These leak data-access logic into the model and force callers to manage Realm instances.

:codex-file-citation[codex-file-citation]{line_range_start=198 line_range_end=214 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L198-L214"}
:codex-file-citation[codex-file-citation]{line_range_start=158 line_range_end=196 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L158-L196"}

:::task-stub{title="Move RealmMyTeam static query methods into TeamsRepositoryImpl"}
1. Add getResourceIds(teamId), getResourceIdsByUser(userId), getTeamCreator(teamId), and isTeamLeader(teamId, userId) to TeamsRepository interface
2. Move the Realm query logic from RealmMyTeam.companion into TeamsRepositoryImpl implementations
3. Update callers (UI fragments, services) to use injected TeamsRepository instead of static calls
4. Remove or deprecate the companion methods after migration
:::

### Move RealmUser static data methods into UserRepositoryImpl

RealmUser.companion contains createGuestUser(), populateUsersTable(), isUserExists(), updateUserDetails(), and cleanupDuplicateUsers() — over 300 lines of business logic with raw Realm transactions inside a model class. UserRepositoryImpl already exists and should own these operations.

:codex-file-citation[codex-file-citation]{line_range_start=202 line_range_end=219 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt#L202-L219"}
:codex-file-citation[codex-file-citation]{line_range_start=425 line_range_end=455 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt#L425-L455"}

:::task-stub{title="Move RealmUser.isUserExists and updateUserDetails into UserRepositoryImpl"}
1. Add isUserExists(name) and updateUserDetails(...) to UserRepository interface
2. Move the Realm query/transaction logic from RealmUser companion lines 425-455 into UserRepositoryImpl
3. Update call sites (UI and services) to use injected UserRepository
4. Remove the static companion methods after all callers are migrated
:::

### Remove EntryPointAccessors from RealmMyLibrary and RealmSubmission model classes

RealmMyLibrary.insertMyLibrary() and RealmSubmission.serialize() call EntryPointAccessors.fromApplication() inside model companion objects to get SharedPrefManager. Models should not perform manual DI; the caller (repository or service) should pass already-resolved dependencies as parameters.

:codex-file-citation[codex-file-citation]{line_range_start=238 line_range_end=250 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L238-L250"}
:codex-file-citation[codex-file-citation]{line_range_start=199 line_range_end=206 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L199-L206"}

:::task-stub{title="Remove EntryPointAccessors from RealmMyLibrary and RealmSubmission models"}
1. Add a SharedPrefManager parameter to RealmMyLibrary.insertMyLibrary() signature instead of calling EntryPointAccessors internally
2. Add a SharedPrefManager parameter to RealmSubmission.serialize() signature instead of calling EntryPointAccessors internally
3. Update all callers of these methods to pass the already-injected SharedPrefManager
4. Remove EntryPointAccessors imports from both model files
:::

### Route databaseService.clearAll() through a dedicated repository method in SyncActivity and SettingsActivity

SyncActivity and SettingsActivity both call databaseService.clearAll() directly from the UI layer for the "reset app" flow. This nuclear database operation should be mediated by a repository or a ConfigurationsRepository method so the UI does not hold a direct handle to the database wipe.

:codex-file-citation[codex-file-citation]{line_range_start=262 line_range_end=274 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L262-L274"}
:codex-file-citation[codex-file-citation]{line_range_start=271 line_range_end=280 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt#L271-L280"}

:::task-stub{title="Route clearAll() through ConfigurationsRepository instead of direct databaseService call"}
1. Add a resetDatabase() suspend method to ConfigurationsRepository interface
2. Implement resetDatabase() in ConfigurationsRepositoryImpl by calling databaseService.clearAll()
3. In SyncActivity, replace databaseService.clearAll() (line 267) with configurationsRepository.resetDatabase()
4. In SettingsActivity, replace databaseService.clearAll() (line 274) with configurationsRepository.resetDatabase()
5. Remove direct DatabaseService injection from both UI files if no other usage remains
:::

### Replace direct Realm queries in TransactionSyncManager with repository calls

TransactionSyncManager calls databaseService.withRealm { realm.where(RealmUser::class.java)... } and invokes model companion methods like RealmUser.populateUsersTable() and RealmChatHistory.insert() directly. It already injects VoicesRepository, ChatRepository, and FeedbackRepository but bypasses them for user and chat-history operations.

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L21-L47"}
:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L70-L80"}

:::task-stub{title="Replace direct Realm queries in TransactionSyncManager with UserRepository calls"}
1. Inject UserRepository into TransactionSyncManager constructor
2. Replace the databaseService.withRealm { realm.where(RealmUser::class.java)... } block (lines 72-80) with a UserRepository method that returns user stubs
3. Replace RealmUser.populateUsersTable() static call with a UserRepository.populateUser() method
4. Remove direct model companion imports (lines 21-26) once repository methods are used
:::

### Replace direct Realm queries in UploadManager with repository calls

UploadManager queries RealmSubmitPhotos and RealmMyLibrary directly via databaseService.withRealm { realm.where(...) } and calls RealmMyPersonal.serialize() as a static method. These data operations should route through the corresponding SubmissionsRepository, ResourcesRepository, and PersonalsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=275 line_range_end=289 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L275-L289"}

:::task-stub{title="Replace direct Realm queries in UploadManager with repository calls"}
1. Inject SubmissionsRepository into UploadManager (or use existing injection if present)
2. Replace realm.where(RealmSubmitPhotos::class.java) query (lines 277-280) with a SubmissionsRepository method
3. Replace realm.where(RealmMyLibrary::class.java) query with a ResourcesRepository method
4. Replace direct RealmMyPersonal.serialize() static call with a PersonalsRepository method
:::

### Fix flowOn(Dispatchers.Main) in RealmRepository.queryListFlow

RealmRepository.queryListFlow() and VoicesRepositoryImpl.getCommunityNews() both use flowOn(Dispatchers.Main) because Realm async queries require a Looper thread. This forces query orchestration onto the main thread. A dedicated single-thread Looper dispatcher for Realm would keep the main thread free while satisfying Realm's Looper requirement.

:codex-file-citation[codex-file-citation]{line_range_start=125 line_range_end=133 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L125-L133"}
:codex-file-citation[codex-file-citation]{line_range_start=188 line_range_end=202 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L188-L202"}

:::task-stub{title="Replace flowOn(Dispatchers.Main) with a dedicated Realm Looper dispatcher"}
1. Create a single-thread HandlerThread-backed CoroutineDispatcher in DatabaseModule (e.g., @RealmDispatcher)
2. Provide it via Hilt so repositories can inject it
3. Replace flowOn(Dispatchers.Main) in RealmRepository.queryListFlow (line 133) with flowOn(realmDispatcher)
4. Replace flowOn(Dispatchers.Main) in VoicesRepositoryImpl.getCommunityNews (line 194) with flowOn(realmDispatcher)
:::

### Replace Thread.sleep in RetryInterceptor with non-blocking retry

RetryInterceptor uses Thread.sleep(delay) inside the OkHttp interceptor chain, blocking the entire network thread during retries. Replacing this with OkHttp's built-in response caching or moving retry logic to a coroutine-based layer would free the thread pool.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt#L25-L47"}

:::task-stub{title="Replace Thread.sleep in RetryInterceptor with non-blocking delay"}
1. Move retry logic from the OkHttp interceptor into a Retrofit CallAdapter or a suspend wrapper at the ApiInterface call-site level
2. Use kotlinx.coroutines delay() instead of Thread.sleep() for back-off
3. Remove Thread.sleep and Thread.currentThread().interrupt() from RetryInterceptor
4. If keeping the interceptor pattern, document why blocking is acceptable or limit to a maximum sleep of 500ms
:::

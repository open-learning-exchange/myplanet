### Add unit-test infrastructure and first JsonUtils tests

The project has zero test dependencies and zero test files. Adding JUnit + coroutines-test to `app/build.gradle` and a first test class for `JsonUtils` (pure JVM, no Android deps) unblocks all future test PRs and proves the pipeline works.

:codex-file-citation[codex-file-citation]{line_range_start=180 line_range_end=209 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L180-L209"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=186 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L1-L186"}

:::task-stub{title="Add unit-test infrastructure and first JsonUtils tests"}
1. Add `testImplementation libs.junit` and `testImplementation libs.kotlinx.coroutines.test` to `app/build.gradle`
2. Add JUnit and coroutines-test version entries to `gradle/libs.versions.toml`
3. Create `app/src/test/java/org/ole/planet/myplanet/utils/JsonUtilsTest.kt` with tests for getString, getInt, getLong, getBoolean, getFloat, getJsonArray, getJsonObject
4. Verify `./gradlew testDefaultDebugUnitTest` passes
:::

### Add unit tests for ConfigurationsRepositoryImpl pure functions

`compareVersions` and `parseApkVersionString` inside `ConfigurationsRepositoryImpl` are pure functions with no Android or network dependencies. Extracting them to a companion or top-level and covering them with JUnit tests catches version-parsing regressions cheaply.

:codex-file-citation[codex-file-citation]{line_range_start=397 line_range_end=461 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L397-L461"}

:::task-stub{title="Add unit tests for version-parsing helpers in ConfigurationsRepositoryImpl"}
1. Move `compareVersions`, `parseApkVersionString`, and `isVersionAllowed` to `internal` visibility inside a companion object
2. Create `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsVersionTest.kt`
3. Add test cases: equal versions, newer version, older version, v-prefix, lite-suffix, null/empty input for parseApkVersionString
4. Verify `./gradlew testDefaultDebugUnitTest` passes
:::

### Add unit test for RatingsRepositoryImpl.roundToSupportedRating

`roundToSupportedRating` is already an `internal` companion function with pure math logic. A simple JUnit test file covers boundary values (0, 0.4, 1, 2.5, 5, 6) in a single small PR.

:codex-file-citation[codex-file-citation]{line_range_start=195 line_range_end=203 path=app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt#L195-L203"}

:::task-stub{title="Add unit test for RatingsRepositoryImpl.roundToSupportedRating"}
1. Create `app/src/test/java/org/ole/planet/myplanet/repository/RoundToSupportedRatingTest.kt`
2. Test boundary values: 0f→1, 0.4f→1, 1f→1, 2.5f→3, 5f→5, 5.6f→5, negative→1
3. Verify `./gradlew testDefaultDebugUnitTest` passes
:::

### Inject ApiInterface into LoginSyncManager instead of using ApiClient.client.create

`LoginSyncManager` already receives its dependencies via `@Inject constructor` but creates `ApiInterface` manually via `ApiClient.client.create(ApiInterface::class.java)` in two places. It should use the Hilt-provided `ApiInterface` instead, which improves testability and aligns with the DI pattern used everywhere else.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt#L25-L42"}
:codex-file-citation[codex-file-citation]{line_range_start=126 line_range_end=134 path=app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt#L126-L134"}

:::task-stub{title="Inject ApiInterface into LoginSyncManager instead of manual creation"}
1. Add `private val apiInterface: ApiInterface` to the `@Inject constructor` of `LoginSyncManager`
2. Remove the `ApiClient.client.create(ApiInterface::class.java)` call at line 41 in `login()`
3. Remove the `ApiClient.client.create(ApiInterface::class.java)` call at line 134 in `syncAdmin()`
4. Remove the now-unused `import org.ole.planet.myplanet.data.api.ApiClient` import
5. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

### Inject ApiInterface into FileUploader instead of using ApiClient.client.create

`FileUploader` manually creates `ApiInterface` via `ApiClient.client.create()`. Since `UploadManager` extends `FileUploader` and is already a Hilt-managed `@Singleton`, the injected `ApiInterface` can be passed down, removing the last `ApiClient.client.create` usage in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt#L20-L46"}

:::task-stub{title="Inject ApiInterface into FileUploader instead of manual creation"}
1. Add an `apiInterface: ApiInterface` constructor parameter to `FileUploader`
2. Update `UploadManager` (which extends `FileUploader`) to pass its already-injected `ApiInterface` to the super constructor
3. Remove the `ApiClient.client.create(ApiInterface::class.java)` call at line 46 of `FileUploader.uploadDoc()`
4. Remove the now-unused `ApiClient` import from `FileUploader`
5. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

### Replace MainApplication.applicationScope usage in LoginSyncManager with injected scope

`LoginSyncManager.login()` and `syncAdmin()` launch coroutines on `MainApplication.applicationScope` directly instead of using an injected `CoroutineScope`. This couples the class to the global application object and makes testing impossible. The DI layer already provides `@ApplicationScope CoroutineScope`.

:codex-file-citation[codex-file-citation]{line_range_start=31 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt#L31-L33"}
:codex-file-citation[codex-file-citation]{line_range_start=126 line_range_end=128 path=app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt#L126-L128"}

:::task-stub{title="Replace MainApplication.applicationScope with injected scope in LoginSyncManager"}
1. Add `@ApplicationScope private val scope: CoroutineScope` to the `@Inject constructor`
2. Replace `MainApplication.applicationScope.launch(Dispatchers.IO)` at line 32 with `scope.launch(Dispatchers.IO)`
3. Replace `MainApplication.applicationScope.launch` at line 127 with `scope.launch(Dispatchers.IO)`
4. Remove the `import org.ole.planet.myplanet.MainApplication` if no longer used
5. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

### Replace MainApplication.applicationScope usage in FileUploader with injected scope

`FileUploader.uploadDoc()` launches on `MainApplication.applicationScope` directly. Since `UploadManager` already has an injected `@ApplicationScope CoroutineScope`, it can be passed to the parent class, removing the global coupling.

:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=48 path=app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt#L45-L48"}

:::task-stub{title="Replace MainApplication.applicationScope with injected scope in FileUploader"}
1. Add a `scope: CoroutineScope` constructor parameter to `FileUploader`
2. Update `UploadManager` to pass its already-injected `@ApplicationScope` scope to the super constructor
3. Replace `MainApplication.applicationScope.launch` at line 47 of `FileUploader.uploadDoc()` with `scope.launch`
4. Remove the `import org.ole.planet.myplanet.MainApplication` from `FileUploader` if no longer used
5. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

### Use RealmRepository helper methods in EventsRepositoryImpl

`EventsRepositoryImpl` already extends `RealmRepository` but still uses raw `realm.where()` calls in `getJoinedMembers()` and `toggleAttendance()`. The `findByField()` and `queryList()` helpers from `RealmRepository` can replace some of these, reducing boilerplate and improving consistency.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L25-L67"}

:::task-stub{title="Use RealmRepository helper methods in EventsRepositoryImpl"}
1. Replace the raw `realm.where(RealmMeetup::class.java).equalTo("meetupId", meetupId).findFirst()` in `toggleAttendance()` with `findByField(RealmMeetup::class.java, "meetupId", meetupId)`
2. Replace the raw meetup member query in `getJoinedMembers()` with `queryList(RealmMeetup::class.java) { equalTo("meetupId", meetupId); isNotEmpty("userId") }`
3. Keep the user lookup in `getJoinedMembers()` as `withRealmAsync` since it uses `in()` operator not supported by `queryList`
4. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

### Use RealmRepository helper methods in HealthRepositoryImpl

`HealthRepositoryImpl` extends `RealmRepository` but uses raw `realm.where()` for every query. The simple lookups in `getHealthEntry()` and `getExaminationById()` can use `findByField()` from the base class.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L16-L36"}

:::task-stub{title="Use RealmRepository helper methods in HealthRepositoryImpl"}
1. Replace `getExaminationById()` body with `findByField(RealmHealthExamination::class.java, "_id", id)`
2. In `getHealthEntry()` replace the user lookup with `findByField(RealmUser::class.java, "id", userId)`
3. In `getHealthEntry()` replace the examination lookups with two `findByField` calls (first by `_id`, fallback by `userId`)
4. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

### Remove Dispatchers.Main hop from ConfigurationsRepositoryImpl.handleVersionEvaluation

`handleVersionEvaluation()` wraps every callback in `serviceScope.launch { withContext(Dispatchers.Main) { ... } }` for four branches. Since this method is only called from `checkVersion()` which already runs inside `serviceScope.launch`, the redundant scope launch creates unnecessary coroutines. The `withContext(Dispatchers.Main)` calls should be kept but the extra `serviceScope.launch` wrappers should be removed.

:codex-file-citation[codex-file-citation]{line_range_start=464 line_range_end=495 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L464-L495"}

:::task-stub{title="Remove redundant serviceScope.launch wrappers in handleVersionEvaluation"}
1. Make `handleVersionEvaluation` a `suspend` function
2. Remove the `serviceScope.launch { }` wrapper around `withContext(Dispatchers.Main) { callback.onUpdateAvailable(...) }` at lines 467-471
3. Remove the `serviceScope.launch { }` wrapper around `withContext(Dispatchers.Main) { callback.onUpdateAvailable(...) }` at lines 475-479
4. Remove the `serviceScope.launch { }` wrapper around `withContext(Dispatchers.Main) { callback.onUpdateAvailable(...) }` at lines 483-487
5. Remove the `serviceScope.launch { }` wrapper around `withContext(Dispatchers.Main) { callback.onError(...) }` at lines 489-493
6. Verify the project builds with `./gradlew assembleDefaultDebug`
:::

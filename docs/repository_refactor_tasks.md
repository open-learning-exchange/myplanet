# Repository Refactoring Round Tasks

This document contains exactly 10 independent refactoring tasks focused on reinforcing repository boundaries, tightening repository interfaces, optimizing Room DAOs, and establishing cleaner view-modeling relationships in `myPlanet` to advance portability toward Kotlin Multiplatform.

---

### Task 1: Decouple `MyLife.kt` model from Android R resource strings
* **Milestone**: 9 (Kotlin Multiplatform portability: platform-free Kotlin core)
* **Focus**: Reinforcing repository boundaries by separating domain entities from Android UI resources.
* **Goal**: Isolate `MyLife` from Android resource framework dependencies to make the model platform-free.
* **Detailed Work Order**:
  1. Remove the import of `org.ole.planet.myplanet.R` inside the Room `@Entity` class `app/src/main/java/org/ole/planet/myplanet/model/MyLife.kt`.
  2. Extract the `defaultItemPairs` list and `defaultItems` helper function out of `MyLife.kt`'s companion object and relocate them to `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`.
  3. Ensure that `MyLife.kt` is entirely free of Android/platform imports, making it compile in a pure Kotlin environment.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/model/MyLife.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest` to verify compilation and correctness.

---

### Task 2: Refactor `PersonalsRepository` to return structured results instead of raw user-facing English strings
* **Milestone**: 3 (ViewModel and Use-Case layers), 9 (Kotlin Multiplatform portability)
* **Focus**: Tightening repository boundaries and resolving presentation layer leaks.
* **Goal**: Avoid leaking presentation-level English strings into the repository data layer.
* **Detailed Work Order**:
  1. In `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`, define a platform-free sealed interface `PersonalUploadResult` with cases representing different outcomes (e.g., `Success`, `AlreadyUploaded`, `MissingResponse`, `AttachmentUploadFailed(val code: Int)`, `Failure(val message: String?)`).
  2. Modify the return type of `uploadPersonal` from `String` to `PersonalUploadResult` in both `PersonalsRepository.kt` and `PersonalsRepositoryImpl.kt`.
  3. Update `app/src/main/java/org/ole/planet/myplanet/ui/life/PersonalsViewModel.kt` to consume the structured `PersonalUploadResult` and map each case to a localized string utilizing the android context.
  4. Update assertions in `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` to match the new return values.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest` to confirm all repository and viewModel unit tests pass successfully.

---

### Task 3: Decouple `ConfigurationsRepository` from Android Context and R resources
* **Milestone**: 1 (Cleaning the data layer), 9 (Kotlin Multiplatform portability)
* **Focus**: Reinforcing repository boundaries and tightening repository interfaces.
* **Goal**: Cleanse the configurations repository of direct dependencies on Android `Context` and localization.
* **Detailed Work Order**:
  1. Define a platform-free sealed interface/class `HealthCheckResult` (e.g., `Success`, `Unauthorized`, `NotFound`, `ServerError(val code: Int)`, `ConnectionTimeout`, `Unreachable`, `NetworkError(val message: String?)`) inside `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`.
  2. Change the return type of `checkHealth()` from `String` to `HealthCheckResult` in both `ConfigurationsRepository.kt` and `ConfigurationsRepositoryImpl.kt`.
  3. Remove direct references to `android.content.Context` and `R.string.server_sync_successfully` from `ConfigurationsRepositoryImpl.kt`'s `checkHealth` method.
  4. Let the calling view models or activities (e.g. `SyncActivity`) handle the `HealthCheckResult` and translate it to localized UI text.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest` to confirm compilation.

---

### Task 4: Consolidate `LifeCache` serialization from Gson to Kotlin Serialization
* **Milestone**: 1 (Cleaning the data layer), 9 (Kotlin Multiplatform portability)
* **Focus**: Removing legacy/redundant serialization library dependencies from repositories.
* **Goal**: Standardize local repository caching on Kotlin Serialization instead of legacy Gson.
* **Detailed Work Order**:
  1. Add the `@Serializable` annotation to the `CachedMyLifeItem` data class.
  2. Refactor `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt` to serialize/deserialize `CachedMyLifeItem` items using `kotlinx.serialization.json.Json` instead of `com.google.gson.Gson`.
  3. Update `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt` to remove its dependencies on `com.google.gson.Gson` and align with `kotlinx.serialization` assertion expectations.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.LifeCacheTest` to verify caching correctness.

---

### Task 5: Migrate `DictionaryRepository` and `DictionaryMapper` parsing to Kotlin Serialization
* **Milestone**: 1 (Cleaning the data layer), 9 (Kotlin Multiplatform portability)
* **Focus**: Removing legacy Gson usages from repositories.
* **Goal**: Convert dictionary file parsing to be completely platform-free and robust.
* **Detailed Work Order**:
  1. Remove legacy Gson types (`com.google.gson.JsonArray`, `GsonUtils`) from `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryMapper.kt` and `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`.
  2. Update `DictionaryMapper.kt` to deserialize the dictionary file content directly from a JSON string using `kotlinx.serialization.json.Json` into a list of `DictionaryEntity` or a lightweight DTO.
  3. Adjust `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt` to verify parsing with Kotlin Serialization JSON inputs.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryMapper.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest` to confirm parser accuracy.

---

### Task 6: Optimize `MyLifeDao` query complexity by normalizing userId in Repository
* **Milestone**: 7 (Optimize remaining performance hotspots), 1 (Cleaning the data layer)
* **Focus**: Room database and DAO query optimization.
* **Goal**: Simplify SQLite queries and maximize Room index efficiency.
* **Detailed Work Order**:
  1. In `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, remove the complex, high-overhead SQL logic `(:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)` from the queries `getByUserId`, `getVisibleByUserId`, and `countByUserId`.
  2. Simplify these queries to look up direct exact matches: `userId = :userId` (and `userId IS :userId`).
  3. Ensure that `LifeRepositoryImpl.kt` consistently normalizes the `userId` argument to a default non-null string fallback (such as `"--"` or `""`) before invoking any `MyLifeDao` functions.
  4. Adjust `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt` to match the exact-match expectation.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`
  - `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest` to ensure database constraints hold.

---

### Task 7: Optimize `PersonalDao` query complexity by normalizing userId in Repository
* **Milestone**: 7 (Optimize remaining performance hotspots), 1 (Cleaning the data layer)
* **Focus**: Room database and DAO query optimization.
* **Goal**: Simplify SQLite conditional evaluations to maximize index utilization.
* **Detailed Work Order**:
  1. In `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`, simplify the `countByTitle` query by removing the conditional filter `AND (:userId IS NULL OR :userId = '' OR userId = :userId)`.
  2. Replace it with a straightforward exact comparison: `AND userId = :userId` (or `userId IS :userId`).
  3. Tighten the repository caller in `PersonalsRepositoryImpl.kt` to normalize blank/null user identifiers to a consistent non-null string fallback (such as `""` or `"--"`) before executing the DAO count check.
* **Files touched**:
  - `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.PersonalDaoTest` to verify that all personal dao tests continue to pass.

---

### Task 8: Create a database unit test suite for `DictionaryDao`
* **Milestone**: 8 (Improve code health and add tests)
* **Focus**: Room DAO correctness and database validation.
* **Goal**: Validate that `DictionaryDao` executes NOCASE queries correctly on the local SQLite engine.
* **Detailed Work Order**:
  1. Create a new database unit test file `app/src/test/java/org/ole/planet/myplanet/data/room/dao/DictionaryDaoTest.kt` using Robolectric.
  2. Setup an in-memory instance of `AppDatabase` and retrieve the `DictionaryDao`.
  3. Write test cases validating `count()`, bulk insertions via `insertAll()`, and case-insensitive word lookups via `findByWord` (utilizing the `COLLATE NOCASE` index).
* **Files touched**:
  - `app/src/test/java/org/ole/planet/myplanet/data/room/dao/DictionaryDaoTest.kt` (New file)
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.DictionaryDaoTest` to execute the database tests.

---

### Task 9: Create a database unit test suite for `RemovedLogDao`
* **Milestone**: 8 (Improve code health and add tests)
* **Focus**: Room DAO correctness and transaction boundary validation.
* **Goal**: Add database-level validation for soft-deletion and record pruning.
* **Detailed Work Order**:
  1. Create a new database unit test file `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt` using Robolectric.
  2. Setup an in-memory instance of `AppDatabase` and retrieve the `RemovedLogDao`.
  3. Test `deleteByTypeUserAndDoc` with nullable parameters.
  4. Validate `deleteByTypeUserAndDocsChunked` behaves correctly with chunked list parameters (including sizes exceeding 1000 items) to guarantee transaction safety on SQLite's parameter limits.
  5. Test that `getRemovedDocIds` correctly fetches matching document identifiers.
* **Files touched**:
  - `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt` (New file)
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.RemovedLogDaoTest` to verify correctness.

---

### Task 10: Create a comprehensive unit test suite for `DeviceNameProvider`
* **Milestone**: 8 (Improve code health and add tests)
* **Focus**: Repository dependency-injection validation and test coverage expansion.
* **Goal**: Add complete test coverage for device identification utilities.
* **Detailed Work Order**:
  1. Create a new test file `app/src/test/java/org/ole/planet/myplanet/utils/DeviceNameProviderTest.kt`.
  2. Mock the injected `SharedPrefManager` dependency using MockK.
  3. Assert that `SharedPrefDeviceNameProvider` correctly resolves custom-configured device names from the preferences manager, and resolves the system-level fallback device name when no custom configuration is provided.
* **Files touched**:
  - `app/src/test/java/org/ole/planet/myplanet/utils/DeviceNameProviderTest.kt` (New file)
* **Verification**: Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.DeviceNameProviderTest` to execute the new unit tests.

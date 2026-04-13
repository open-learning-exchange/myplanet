1. **Update `app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt`**:
   - The instrumented tests test `RealmUser.cleanupDuplicateUsers` which no longer exists.
   - `UserRepositoryImpl` has a `cleanupDuplicateUsers` method which handles this now.
   - I need to update the instrumented tests to instantiate `UserRepositoryImpl` (with dummy dependencies or just real context) or `RealmUserTest` can be renamed to `UserRepositoryImplAndroidTest` if it relies on `UserRepositoryImpl`.
   - Actually, wait, `UserRepositoryImpl.cleanupDuplicateUsers` relies on `withRealm`, which makes it a suspend function and gets the realm from `databaseService`.
   - To make it testable in this instrumented test, I need to instantiate a `UserRepositoryImpl` with a mock `DatabaseService` or adapt the test to inject `UserRepository`. Wait, since it's an instrumented test, it can use actual real implementations if needed, but it currently uses `Realm.getInstance(realmConfiguration)`.
   - Wait, `cleanupDuplicateUsers()` in `UserRepositoryImpl` looks like this:
     ```kotlin
     override suspend fun cleanupDuplicateUsers() {
         executeTransaction { realm ->
             ...
         }
     }
     ```
   - I can test it by instantiating a `DatabaseService` that uses `realmConfiguration`. But `UserRepositoryImpl` takes 10 parameters!
   - Is it better to just delete `RealmUserTest.kt` in `androidTest`? The logic for `cleanupDuplicateUsers` is fairly simple. Wait, the user explicitly asked to "update these to go through the repository, or the tests need to be reworked". Let's rework the test.
   - Or maybe just delete the test because `RealmUser` no longer has it and the logic is identical to `UserRepositoryImpl.cleanupDuplicateUsers()` and we can write a unit test or instrumented test for `UserRepositoryImpl`. Let's mock `UserRepositoryImpl` dependencies for the instrumented test.

2. **Fix `settings.edit().apply()` -> `settings.edit { putString(...) }` in `UserRepositoryImpl.kt`**:
   - I'll replace `settings.edit().putString("planetCode", planetCodes).apply()` with `settings.edit { putString("planetCode", planetCodes) }` using `androidx.core.content.edit`.

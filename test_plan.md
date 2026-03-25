1. **Add `UserRepository` dependency to `TransactionSyncManager`**
   - Use `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`.
   - Update imports to include `org.ole.planet.myplanet.repository.UserRepository`.
   - Remove `import org.ole.planet.myplanet.model.RealmUser.Companion.populateUsersTable`.
   - Add `private val userRepository: UserRepository` to the injected parameters.

2. **Replace direct Realm query in `syncAllHealthData`**
   - Change `val usersToSync = databaseService.withRealm { ... }` to `val usersToSync = userRepository.getSyncedUsers()`.

3. **Replace direct Realm mutation in `syncHealthData`**
   - Use `userRepository.markUserKeyIvSaved(userModel.id, key, iv)` instead of the `databaseService.executeTransactionAsync` block that manually mutates `key` and `iv`. This requires the ID so handle null appropriately (it's safe since `managedUser` check inherently needed ID). Wait, `userModel.id` is nullable String.

4. **Replace direct Realm query in `syncKeyIv`**
   - Replace the `databaseService.withRealm { realm -> realm.where(RealmUser::class.java).equalTo("id", id).findFirst()?.let { realm.copyFromRealm(it) } }` with `val userModel = id?.let { userRepository.getUserById(it) }`.

5. **Expose `populateUser` logic in `UserRepository` and `UserRepositoryImpl`**
   - In `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`: Add `fun populateUser(jsonDoc: JsonObject?, realm: io.realm.Realm, settings: SharedPreferences): RealmUser?` or a coroutine alternative. Wait, `TransactionSyncManager.continueInsert` executes within an async transaction (`executeTransactionAsync { mRealm -> ... }`). The `mRealm` is passed explicitly! This means we need a synchronous version or one that accepts `Realm` in `UserRepository`, OR we need to update the repository to provide this. `UserRepositoryImpl` extends `RealmRepository` but the async transaction is happening outside.
   - Wait, `RealmUser.Companion.populateUsersTable` takes `(jsonDoc: JsonObject?, mRealm: Realm?, settings: SharedPreferences)`.
   - `TransactionSyncManager.continueInsert` is called within `executeTransactionAsync`.
   - So `UserRepository` needs a method like `fun populateUser(jsonDoc: JsonObject?, realm: io.realm.Realm, settings: SharedPreferences): RealmUser?`
   - In `UserRepository.kt` add: `fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser?`
   - In `UserRepositoryImpl.kt` add: `override fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser? { return RealmUser.populateUsersTable(jsonDoc, mRealm, settings) }`

6. **Replace `populateUsersTable` call in `continueInsert`**
   - In `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`:
   - Change `populateUsersTable(jsonDoc, mRealm, sharedPrefManager.rawPreferences)` to `userRepository.populateUser(jsonDoc, mRealm, sharedPrefManager.rawPreferences)`.

7. **Compile and run tests**
   - `./gradlew testDefaultDebugUnitTest`
   - Fix any issues if found.

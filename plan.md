1. **Create internal interface `UserSyncHelper`**
   - Extends the core `UserRepository` but adds the implementation-specific / sync / persistence helper methods.
   - Or, just separate those methods into an `InternalUserHelper` interface which `UserRepositoryImpl` implements, but `UserRepository` does not.

Wait, the prompt says:
"Split sync and persistence helpers away from the public user repository
1. Remove SharedPreferences, raw Realm, bulk-insert, and shelf-payload helpers from the public UserRepository contract.
2. Leave only app-facing identity, profile, auth, and upload-state methods exposed to UI and services.
3. Rehome the sync-only population and serialization helpers behind implementation-only collaborators or narrow internal interfaces."

Let's look at the methods to remove from `UserRepository`:
- `fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser?`
- `suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser?`
- `suspend fun saveUser(jsonDoc: JsonObject?, settings: SharedPreferences, key: String? = null, iv: String? = null): RealmUser?`
- `fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)`
- `fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences)`
- `suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject`

Wait, `saveUser` and `createGuestUser` are used in `LoginActivity` and `GuestLoginExtensions.kt` and `ProcessUserDataActivity.kt` and `SyncActivity.kt`.

If we remove `SharedPreferences` from `UserRepository`, how should UI handle `saveUser` and `createGuestUser`?
Maybe the UI should just pass the fields (like `JsonObject`) or we create a separate component like `UserSyncRepository` or `UserAuthRepository` that takes `SharedPreferences`?
Or maybe `UserRepository` doesn't take `SharedPreferences` as argument, but injects `SharedPrefManager` internally? `UserRepositoryImpl` already has `sharedPrefManager: SharedPrefManager`!

Wait, `settings` argument can be removed and we can use `sharedPrefManager.rawPreferences` inside `UserRepositoryImpl`.
Let's see if we can just remove `SharedPreferences` and `Realm` parameters!

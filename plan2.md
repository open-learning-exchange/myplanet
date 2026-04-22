If `UserRepository` exposes `bulkInsertAchievementsFromSync(realm, jsonArray)` and `bulkInsertUsersFromSync(realm, jsonArray, settings)` and `populateUser(jsonDoc, realm, settings)`, we should extract these into a `UserSyncHelper` or `UserPersistenceHelper` interface. Wait, they are used by `TransactionSyncManager` and `UploadToShelfService` and `LoginSyncManager`.

The prompt explicitly says: "Rehome the sync-only population and serialization helpers behind implementation-only collaborators or narrow internal interfaces."

Let's introduce `UserSyncHelper` interface:
```kotlin
interface UserSyncHelper {
    fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences)
    suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject
    fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser?
}
```

Wait, `saveUser` and `createGuestUser` use SharedPreferences but they are app-facing auth methods. The prompt says: "Remove SharedPreferences, raw Realm, bulk-insert, and shelf-payload helpers from the public UserRepository contract."
"Leave only app-facing identity, profile, auth, and upload-state methods exposed to UI and services."

So `createGuestUser` should just not take `SharedPreferences` as an argument.
Instead of `createGuestUser(username, settings)`, it could be `createGuestUser(username)` and use `sharedPrefManager.rawPreferences` inside.
Same for `saveUser(jsonDoc, key, iv)` — no `settings` argument!
Wait, the prompt says "Remove SharedPreferences, raw Realm, bulk-insert, and shelf-payload helpers from the public UserRepository contract."
Let's see if `createGuestUser` uses `settings`:
```kotlin
    override suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser? {
        return withRealm { realm ->
            // ...
            val user = populateUser(object, realm, settings)
```

Wait, `populateUser` uses `settings` to save some prefs, maybe? Let's check `populateUser` implementation.

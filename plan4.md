Okay, `UserRepositoryImpl` already receives `settings: SharedPreferences` and `sharedPrefManager`. So it does not need SharedPreferences to be passed in to `createGuestUser` or `saveUser` or `populateUser`! We can remove `settings` argument from all of them.

Also, we need to extract internal methods like `bulkInsertAchievementsFromSync`, `bulkInsertUsersFromSync`, `getShelfData`, `parseLeadersJson`, `populateUser` into a separate interface `UserSyncHelper` and `UserSyncHelper` will not be in `UserRepository`! Wait, if `UserRepositoryImpl` implements `UserSyncHelper`, we can just inject `UserRepositoryImpl` where needed or bind it to `UserSyncHelper`. Wait, in `TransactionSyncManager` and `UploadToShelfService`, they inject `UserRepository` and use these methods. We will need to change them to inject `UserSyncHelper`.

Wait, Dagger / Hilt binds `UserRepositoryImpl` to `UserRepository`. We'd need to bind it to `UserSyncHelper` as well, or we can just change those specific services to depend on `UserSyncHelper` instead of `UserRepository`.

Let's look at what needs to be removed from `UserRepository`:
1. `fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser?`
2. `suspend fun createGuestUser(username: String): RealmUser?` (removed `settings` parameter). Wait, `createGuestUser` is auth/identity method so it stays in `UserRepository` but WITHOUT `SharedPreferences` param.
3. `suspend fun saveUser(jsonDoc: JsonObject?, key: String? = null, iv: String? = null): RealmUser?` (removed `settings` param, stays in `UserRepository`).
4. `fun parseLeadersJson(jsonString: String): List<RealmUser>` - wait, is this exposed to UI? `LeadersFragment` uses it. Maybe it should stay in `UserRepository`, or move somewhere else? The prompt says: "Rehome the sync-only population and serialization helpers behind implementation-only collaborators or narrow internal interfaces." ParseLeadersJson might just be an internal helper, or we can leave it if we must. Actually, let's move it out or keep it? "Remove SharedPreferences, raw Realm, bulk-insert, and shelf-payload helpers from the public UserRepository contract. Leave only app-facing identity, profile, auth, and upload-state methods exposed to UI and services." So `parseLeadersJson` doesn't fit the "must be removed" description explicitly, but it's serialization helper.
5. `fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)` - REMOVE
6. `fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences)` - REMOVE
7. `suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject` - REMOVE (shelf-payload helper)

So we create:
```kotlin
interface UserSyncHelper {
    fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences)
    suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject
    fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser?
}
```

Wait! The prompt says "Rehome the sync-only population and serialization helpers behind implementation-only collaborators or narrow internal interfaces."
If we just move them to `UserSyncHelper`, `TransactionSyncManager` and `UploadToShelfService` will need to inject `UserSyncHelper`.
Let's see if we can just define `interface UserSyncHelper` in `org.ole.planet.myplanet.repository`.
And `UserRepositoryImpl` implements both `UserRepository` and `UserSyncHelper`.
We will also need to update Dagger bindings.

Wait, `settings` argument can be removed entirely from `populateUser` and `bulkInsertUsersFromSync` because `UserRepositoryImpl` already has `settings` injected!
Let's check if they pass a DIFFERENT SharedPreferences!
In `TransactionSyncManager.kt`:
`userRepository.bulkInsertUsersFromSync(mRealm, arr, sharedPrefManager.rawPreferences)`
In `LoginSyncManager.kt`:
`userRepository.saveUser(jsonDoc, sharedPrefManager.rawPreferences)`
In `UserRepositoryImpl.kt`: `settings` is injected with `@AppPreferences`. Is `sharedPrefManager.rawPreferences` the same?
Let's verify!

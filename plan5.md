Okay, `sharedPrefManager` has `pref` which is `SharedPreferences` but wait, in `UserRepositoryImpl` it has `@AppPreferences private val settings: SharedPreferences` and `private val sharedPrefManager: SharedPrefManager`. The `settings` is exactly the same one we want.
Wait, let's trace: "Rehome the sync-only population and serialization helpers behind implementation-only collaborators or narrow internal interfaces."

1. Create `internal interface UserSyncHelper` in `org.ole.planet.myplanet.repository`:
```kotlin
package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm

interface UserSyncHelper {
    fun populateUser(jsonDoc: JsonObject?, mRealm: Realm?, settings: SharedPreferences): org.ole.planet.myplanet.model.RealmUser?
    fun parseLeadersJson(jsonString: String): List<org.ole.planet.myplanet.model.RealmUser>
    fun bulkInsertAchievementsFromSync(realm: Realm, jsonArray: JsonArray)
    fun bulkInsertUsersFromSync(realm: Realm, jsonArray: JsonArray, settings: SharedPreferences)
    suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject
}
```

Wait, `parseLeadersJson` doesn't strictly have to be sync-only. LeadersFragment uses it. We can just keep `parseLeadersJson` in `UserRepository` or move it out if we want. The prompt specifically asks to: "Remove SharedPreferences, raw Realm, bulk-insert, and shelf-payload helpers from the public UserRepository contract." So anything using `SharedPreferences`, `io.realm.Realm`, `bulkInsert...` or `getShelfData` must be removed.

2. `UserRepository` will NOT have:
- `fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser?`
- `fun bulkInsertAchievementsFromSync(...)`
- `fun bulkInsertUsersFromSync(...)`
- `suspend fun getShelfData(...)`

What about `suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser?` and `suspend fun saveUser(jsonDoc: JsonObject?, settings: SharedPreferences, key: String? = null, iv: String? = null): RealmUser?`?
They have `SharedPreferences` in signature!
So we MUST change their signatures in `UserRepository` to:
- `suspend fun createGuestUser(username: String): RealmUser?`
- `suspend fun saveUser(jsonDoc: JsonObject?, key: String? = null, iv: String? = null): RealmUser?`
And update all callers (LoginActivity, GuestLoginExtensions, ProcessUserDataActivity, SyncActivity, AuthUtils, etc.) to not pass `settings`.

3. `UserRepositoryImpl` will implement BOTH `UserRepository` and `UserSyncHelper`.
Wait, if `UserRepositoryImpl` implements both, we need to provide `UserSyncHelper` in DI.
In `di/ServiceModule.kt` or `di/RepositoryModule.kt` we can add `@Binds` or `@Provides` for `UserSyncHelper`!
But actually `UserSyncHelper` is an `internal interface`. We can just make `UserRepositoryImpl` implement it. And the places that need it (TransactionSyncManager, UploadToShelfService, LoginSyncManager) can just inject `UserSyncHelper` instead of `UserRepository`, or cast `UserRepository` to `UserSyncHelper`.
Better to inject `UserSyncHelper` and update Dagger to bind it. Or inject `Lazy<UserSyncHelper>`.

Let's check `di/RepositoryModule.kt`.

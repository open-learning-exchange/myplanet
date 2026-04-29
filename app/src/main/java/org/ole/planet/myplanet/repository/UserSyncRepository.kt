package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmUser

interface UserSyncRepository {
    suspend fun saveUser(jsonDoc: JsonObject?, key: String? = null, iv: String? = null): RealmUser?
    fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject
}

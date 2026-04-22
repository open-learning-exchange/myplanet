package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject

interface UserSyncHelper {
    fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?): org.ole.planet.myplanet.model.RealmUser?
    fun parseLeadersJson(jsonString: String): List<org.ole.planet.myplanet.model.RealmUser>
    fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    suspend fun getShelfData(userId: String?, jsonDoc: JsonObject?, myLibs: JsonArray, myCourseIds: JsonArray): JsonObject
}

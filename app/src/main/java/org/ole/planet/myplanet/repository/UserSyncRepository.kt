package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmUser

interface UserSyncRepository {
    suspend fun changeUserSecurity(model: RealmUser, obj: JsonObject)
    suspend fun saveKeyIv(apiInterface: org.ole.planet.myplanet.data.api.ApiInterface, model: RealmUser, obj: JsonObject)
    fun replacedUrl(model: RealmUser): String
    suspend fun checkIfUserExists(apiInterface: org.ole.planet.myplanet.data.api.ApiInterface, header: String, model: RealmUser): Boolean
    suspend fun processUserAfterCreation(apiInterface: org.ole.planet.myplanet.data.api.ApiInterface, model: RealmUser, obj: JsonObject, updateHealthFn: suspend (String, String) -> Unit)
    suspend fun uploadNewUser(apiInterface: org.ole.planet.myplanet.data.api.ApiInterface, model: RealmUser, updateHealthFn: suspend (String, String) -> Unit)
    suspend fun updateExistingUser(apiInterface: org.ole.planet.myplanet.data.api.ApiInterface, header: String, model: RealmUser)
    suspend fun saveUser(jsonDoc: JsonObject?, key: String? = null, iv: String? = null): RealmUser?
    fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    suspend fun uploadShelfData(user: RealmUser)
}

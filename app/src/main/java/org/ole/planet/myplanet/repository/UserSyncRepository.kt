package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.UserEntity

interface UserSyncRepository {
    suspend fun changeUserSecurity(model: UserEntity, obj: JsonObject)
    suspend fun saveKeyIv(model: UserEntity, obj: JsonObject)
    suspend fun checkIfUserExists(header: String, model: UserEntity): Boolean
    suspend fun processUserAfterCreation(model: UserEntity, obj: JsonObject, updateHealthFn: suspend (String, String) -> Unit)
    suspend fun uploadNewUser(model: UserEntity, updateHealthFn: suspend (String, String) -> Unit)
    suspend fun updateExistingUser(header: String, model: UserEntity)
    suspend fun saveUser(jsonDoc: JsonObject?, key: String? = null, iv: String? = null): UserEntity?
    suspend fun bulkInsertAchievementsFromSync(jsonArray: JsonArray)
    suspend fun insertUsersFromSync(docs: List<JsonObject>)
    suspend fun uploadShelfData(user: UserEntity)
    suspend fun checkShelfBatchForDataOptimized(shelfIds: List<String>): List<String>
}

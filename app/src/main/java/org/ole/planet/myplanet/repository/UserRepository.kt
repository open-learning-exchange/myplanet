package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmUserModel

interface UserRepository {
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getUserByAnyId(id: String): RealmUserModel?
    suspend fun getUserByName(name: String): RealmUserModel?
    suspend fun getAllUsers(): List<RealmUserModel>
    suspend fun getMonthlyLoginCounts(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Int>
    suspend fun saveUser(jsonDoc: JsonObject?, settings: SharedPreferences, key: String? = null, iv: String? = null): RealmUserModel?
    suspend fun updateSecurityData(
        name: String,
        userId: String?,
        rev: String?,
        derivedKey: String?,
        salt: String?,
        passwordScheme: String?,
        iterations: String?,
    )
}

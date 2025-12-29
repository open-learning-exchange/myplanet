package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonObject
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.HealthRecord

interface UserRepository {
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getUserByAnyId(id: String): RealmUserModel?
    suspend fun getUserByName(name: String): RealmUserModel?
    suspend fun getAllUsers(): List<RealmUserModel>
    suspend fun getUsersSortedBy(fieldName: String, sortOrder: Sort): List<RealmUserModel>
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

    suspend fun updateUserDetails(
        userId: String?,
        firstName: String?,
        lastName: String?,
        middleName: String?,
        email: String?,
        phoneNumber: String?,
        level: String?,
        language: String?,
        gender: String?,
        dob: String?,
    ): RealmUserModel?

    suspend fun updateUserImage(
        userId: String?,
        imagePath: String?,
    ): RealmUserModel?

    suspend fun updateProfileFields(
        userId: String?,
        payload: JsonObject
    )

    suspend fun becomeMember(obj: JsonObject): Pair<Boolean, String>

    suspend fun searchUsers(query: String, sortField: String, sortOrder: Sort): List<RealmUserModel>
    suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: RealmUserModel
    ): HealthRecord?

    fun getUserModel(): RealmUserModel?
    suspend fun getUserModelSuspending(): RealmUserModel?
    fun getActiveUserId(): String
    suspend fun validateUsername(username: String): String?
    suspend fun cleanupDuplicateUsers()
}

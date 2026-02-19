package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonObject
import io.realm.Sort
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

interface UserRepository {
    suspend fun getHealthProfile(userId: String): RealmMyHealth?
    suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>)

    suspend fun getUserById(userId: String): RealmUser?
    suspend fun getUserByAnyId(id: String): RealmUser?
    suspend fun getUserByName(name: String): RealmUser?
    suspend fun findUserByName(name: String): RealmUser?
    suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser?
    suspend fun getAllUsers(): List<RealmUser>
    suspend fun getUsersSortedBy(fieldName: String, sortOrder: Sort): List<RealmUser>
    suspend fun getMonthlyLoginCounts(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Int>
    suspend fun saveUser(jsonDoc: JsonObject?, settings: SharedPreferences, key: String? = null, iv: String? = null): RealmUser?
    suspend fun ensureUserSecurityKeys(userId: String): RealmUser?
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
    ): RealmUser?

    suspend fun updateUserImage(
        userId: String?,
        imagePath: String?,
    ): RealmUser?

    suspend fun updateProfileFields(
        userId: String?,
        payload: JsonObject
    )

    suspend fun createMember(user: JsonObject): Pair<Boolean, String>

    suspend fun becomeMember(obj: JsonObject): Pair<Boolean, String>

    suspend fun searchUsers(query: String, sortField: String, sortOrder: Sort): List<RealmUser>
    suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: RealmUser
    ): HealthRecord?

    @Deprecated("Use getUserModelSuspending() instead")
    fun getUserModel(): RealmUser?
    @Deprecated("Use getUserModelSuspending() instead")
    fun getCurrentUser(): RealmUser?
    suspend fun getUserModelSuspending(): RealmUser?
    suspend fun getUserProfile(): RealmUser?
    suspend fun getUserImageUrl(): String?
    @Deprecated("Use getActiveUserIdSuspending() instead")
    fun getActiveUserId(): String
    suspend fun getActiveUserIdSuspending(): String
    suspend fun validateUsername(username: String): String?
    suspend fun cleanupDuplicateUsers()
    fun authenticateUser(username: String?, password: String?, isManagerMode: Boolean): RealmUser?
    fun hasAtLeastOneUser(): Boolean
    suspend fun hasUserSyncAction(userId: String?): Boolean
}

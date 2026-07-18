package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.AchievementData
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MemberInfo
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.DashboardProfile
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.User

interface UserRepository {
    suspend fun getSavedUsers(): List<User>
    suspend fun upsertSavedUser(name: String?, encryptedPassword: String?, source: String, userProfile: String?, userName: String?)
    suspend fun resetGuestAsMember(username: String?)
    suspend fun getHealthProfile(userId: String): MyHealth?
    suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>)

    suspend fun getUserById(userId: String): UserEntity?
    suspend fun getDashboardProfile(userId: String): DashboardProfile
    suspend fun getUsersByIds(userIds: List<String>): List<UserEntity>
    suspend fun getUserByAnyId(id: String): UserEntity?
    suspend fun getUserByName(name: String): UserEntity?
    suspend fun findUserByName(name: String): UserEntity?
    suspend fun getSyncedUsers(): List<UserEntity>
    suspend fun getUsersForHealthSync(): List<UserEntity>
    suspend fun getSyncedUserByName(name: String): UserEntity?
    suspend fun createGuestUser(username: String): UserEntity?
    suspend fun getAllUsers(): List<UserEntity>
    suspend fun getUsersSortedBy(fieldName: String, descending: Boolean): List<UserEntity>
    suspend fun getPendingSyncUsers(limit: Int): List<UserEntity>
    suspend fun getMonthlyLoginCounts(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Int>
    suspend fun isUserExists(name: String?): Boolean
    fun parseLeadersJson(jsonString: String): List<UserEntity>
    suspend fun ensureUserSecurityKeys(userId: String): UserEntity?
    suspend fun fetchUserSecurityData(name: String)
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
    ): UserEntity?

    suspend fun updateUserImage(
        userId: String?,
        imagePath: String?,
    ): UserEntity?

    suspend fun updateProfileFields(
        userId: String?,
        payload: JsonObject
    )

    suspend fun createMember(user: MemberInfo): Pair<Boolean, String>

    suspend fun becomeMember(obj: JsonObject): Pair<Boolean, String>

    suspend fun searchUsers(query: String, sortField: String, descending: Boolean): List<UserEntity>
    suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: UserEntity
    ): HealthRecord?
    suspend fun getUserModel(): UserEntity?
    suspend fun getUserProfile(): UserEntity?
    suspend fun getUserImageUrl(): String?
    suspend fun getActiveUserIdSuspending(): String
    suspend fun validateUsername(username: String): String?
    suspend fun cleanupDuplicateUsers()
    suspend fun authenticateUser(username: String?, password: String?, isManagerMode: Boolean): UserEntity?
    suspend fun hasAtLeastOneUser(): Boolean
    suspend fun hasUserSyncAction(userId: String?): Boolean
    suspend fun initializeAchievement(achievementId: String): Achievement?
    suspend fun updateAchievement(
        achievementId: String,
        header: String,
        goals: String,
        purpose: String,
        sendToNation: String,
        achievements: JsonArray,
        references: JsonArray,
        createdOn: String,
        username: String,
        parentCode: String,
        resumeFileName: String = ""
    )
    suspend fun markUserUploaded(userId: String, id: String, rev: String)
    suspend fun markUserKeyIvSaved(userId: String, key: String, iv: String?)
    suspend fun markUserRevUpdated(userId: String, rev: String?)
    suspend fun getAchievementData(userId: String, planetCode: String): AchievementData
    suspend fun getAchievementsForUpload(): List<JsonObject>
    suspend fun markAchievementUploaded(id: String, rev: String?)
}

package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmUserModel

interface UserRepository {
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getUserByAnyId(id: String): RealmUserModel?
    suspend fun getAllUsers(): List<RealmUserModel>
    suspend fun getMonthlyLoginCounts(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Int>
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
}

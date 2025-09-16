package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmUserModel

interface UserRepository {
    suspend fun getUserProfile(): String?
    suspend fun saveUserData(data: String)
    suspend fun getCurrentUser(): RealmUserModel?
    suspend fun getCurrentUser(userId: String): RealmUserModel?
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getUserByName(username: String): RealmUserModel?
    suspend fun getAllUsers(): List<RealmUserModel>
}

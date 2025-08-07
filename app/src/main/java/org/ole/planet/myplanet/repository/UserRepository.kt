package org.ole.planet.myplanet.repository

import io.realm.Realm
import org.ole.planet.myplanet.model.RealmUserModel

interface UserRepository {
    suspend fun getUserProfile(): String?
    suspend fun saveUserData(data: String)
    suspend fun getCurrentUser(): RealmUserModel?
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getUserByName(username: String): RealmUserModel?
    suspend fun getAllUsers(): List<RealmUserModel>
    @Deprecated("Use async version", ReplaceWith("getCurrentUser()"))
    fun getRealm(): Realm
}

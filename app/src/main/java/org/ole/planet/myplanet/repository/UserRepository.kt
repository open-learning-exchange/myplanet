package org.ole.planet.myplanet.repository

import io.realm.Realm
import org.ole.planet.myplanet.model.RealmUserModel

interface UserRepository {
    suspend fun getUserProfile(): String?
    suspend fun saveUserData(data: String)
    fun getRealm(): Realm
    fun getCurrentUser(): RealmUserModel?
}

package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmUserModel

interface UserRepository {
    suspend fun getUserById(userId: String): RealmUserModel?
    suspend fun getAllUsers(): List<RealmUserModel>
}

package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.UserEntity

interface UserReadRepository {
    suspend fun getUserById(userId: String): UserEntity?
    suspend fun getUsersByIds(userIds: List<String>): List<UserEntity>
    suspend fun getUserModel(): UserEntity?
}

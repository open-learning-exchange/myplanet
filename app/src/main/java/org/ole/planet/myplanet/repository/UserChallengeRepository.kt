package org.ole.planet.myplanet.repository

interface UserChallengeRepository {
    suspend fun hasValidSync(userId: String?): Boolean
}

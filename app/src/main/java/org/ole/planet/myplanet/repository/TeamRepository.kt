package org.ole.planet.myplanet.repository

interface TeamRepository {
    suspend fun acceptRequest(teamId: String, userId: String)
    suspend fun rejectRequest(teamId: String, userId: String)
}


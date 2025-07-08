package org.ole.planet.myplanet.domain.repository

interface NetworkRepository {
    suspend fun isServerReachable(url: String): Boolean
}

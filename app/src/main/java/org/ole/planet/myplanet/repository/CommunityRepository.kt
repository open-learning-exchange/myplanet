package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.callback.OnSuccessListener

interface CommunityRepository {
    suspend fun syncPlanetServers(callback: OnSuccessListener)
}

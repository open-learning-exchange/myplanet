package org.ole.planet.myplanet.repository

interface PlanetRepository {
    suspend fun syncPlanetServers(): String
}

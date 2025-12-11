package org.ole.planet.myplanet.repository

interface IRealmRepository {
    suspend fun refresh()
}

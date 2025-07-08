package org.ole.planet.myplanet.data.repository

import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.domain.repository.NetworkRepository

class NetworkRepositoryImpl : NetworkRepository {
    override suspend fun isServerReachable(url: String): Boolean {
        return MainApplication.isServerReachable(url)
    }
}

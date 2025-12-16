package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface OfflineActivityRepository {
    suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>>
}

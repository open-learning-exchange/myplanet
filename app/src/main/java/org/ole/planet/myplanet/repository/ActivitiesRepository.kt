package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface ActivitiesRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
    suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>>
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun markResourceRemoved(userId: String, resourceId: String)
}

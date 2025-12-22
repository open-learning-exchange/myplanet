package org.ole.planet.myplanet.repository

import io.realm.RealmResults
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface ActivityRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
    suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>>
}

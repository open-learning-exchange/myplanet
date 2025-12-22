package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmOfflineActivity

interface ActivityRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
}

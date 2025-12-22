package org.ole.planet.myplanet.repository

import io.realm.RealmResults
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface ActivityRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
}

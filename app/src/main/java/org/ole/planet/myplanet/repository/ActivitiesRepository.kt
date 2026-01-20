package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmResourceActivity

interface ActivitiesRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
    suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>>
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun markResourceRemoved(userId: String, resourceId: String)
    suspend fun logLogin(userId: String?, userName: String?, parentCode: String?, planetCode: String?)
    suspend fun logLogout()
    suspend fun logResourceOpen(userName: String?, parentCode: String?, planetCode: String?, type: String?, title: String?, resourceId: String?)
    suspend fun getOfflineVisits(userName: String?): Int
    suspend fun getLastVisit(userName: String?): Long?
    suspend fun getGlobalLastVisit(): Long?
    suspend fun getNumberOfResourceOpen(userName: String?, type: String?): Long
    suspend fun getAllResourceActivities(userName: String?, type: String?): List<RealmResourceActivity>
}

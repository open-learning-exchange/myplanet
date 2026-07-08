package org.ole.planet.myplanet.repository
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUser

interface ActivitiesRepository {
    suspend fun getOfflineVisitCount(userId: String): Int
    suspend fun getOfflineLoginCount(userName: String): Int
    suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>>
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun markResourceRemoved(userId: String, resourceId: String)
    suspend fun logCourseVisit(courseId: String, title: String, userId: String)
    suspend fun logLogin(userId: String?, userName: String?, parentCode: String?, planetCode: String?)
    suspend fun logLogout(userName: String?)
    suspend fun getGlobalLastVisit(): Long?
    suspend fun getLastVisit(userName: String): Long?
    suspend fun logResourceOpen(userName: String?, parentCode: String?, planetCode: String?, title: String?, resourceId: String?, type: String?)
    suspend fun getResourceOpenCount(userName: String, type: String): Long
    suspend fun getMostOpenedResource(userName: String, type: String): Pair<String, Int>?
    suspend fun recordSyncActivity(userId: String)
    suspend fun recordSyncUserChallengeAction(userId: String)
    suspend fun hasUserSyncAction(userId: String?): Boolean
    suspend fun hasUserCompletedSync(userId: String): Boolean
    suspend fun insertLoginActivitiesFromSync(docs: List<JsonObject>)
    suspend fun uploadActivities()
    suspend fun uploadMyPlanetActivities(userModel: RealmUser)
}

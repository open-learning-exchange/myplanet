package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface ActivitiesRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
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
    suspend fun getUnuploadedLoginActivities(): List<org.ole.planet.myplanet.model.LoginActivityData>
    suspend fun markActivitiesUploaded(ids: Array<String>, revMap: Map<String, com.google.gson.JsonObject?>)
    suspend fun recordSyncActivity(userId: String)
    suspend fun insertActivity(json: JsonObject)
    suspend fun getRecentLogin(): RealmOfflineActivity?
    fun serializeLoginActivities(activity: RealmOfflineActivity, context: android.content.Context): JsonObject
    fun bulkInsertLoginActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}

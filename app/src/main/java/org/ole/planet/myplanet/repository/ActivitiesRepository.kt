package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.UserEntity

interface ActivitiesRepository {
    suspend fun getOfflineVisitCount(userId: String): Int
    suspend fun getOfflineLoginCount(userName: String): Int
    fun getOfflineLogins(userName: String): Flow<List<OfflineActivity>>
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
    suspend fun uploadMyPlanetActivities(userModel: UserEntity)
    suspend fun getPendingCourseActivityUploads(): List<CourseActivity>
    suspend fun markCourseActivityUploaded(localId: String, remoteId: String, rev: String): Boolean
    suspend fun getPendingSearchActivityUploads(): List<SearchActivity>
    suspend fun markSearchActivityUploaded(localId: String, remoteId: String, rev: String): Boolean
    suspend fun getPendingResourceActivityUploads(): List<ResourceActivity>
    suspend fun getPendingResourceActivitySyncUploads(): List<ResourceActivity>
    suspend fun markResourceActivityUploaded(localId: String, remoteId: String, rev: String): Boolean
}

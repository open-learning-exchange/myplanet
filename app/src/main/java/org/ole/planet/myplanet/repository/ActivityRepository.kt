package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface ActivityRepository {
    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity>
    suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>>
    suspend fun markCourseAdded(userId: String, courseId: String)
    suspend fun markCourseRemoved(userId: String, courseId: String)
}

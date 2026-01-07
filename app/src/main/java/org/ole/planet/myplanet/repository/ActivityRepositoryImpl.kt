package org.ole.planet.myplanet.repository

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.service.UserProfileDbHandler

class ActivityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), ActivityRepository {
    override suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity> {
        return queryList(RealmOfflineActivity::class.java) {
            equalTo("userName", userName)
            equalTo("type", type)
        }
    }

    override suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>> {
        return queryListFlow(RealmOfflineActivity::class.java) {
            equalTo("userName", userName)
            equalTo("type", UserProfileDbHandler.KEY_LOGIN)
        }
    }

    override suspend fun markCourseAdded(userId: String, courseId: String) {
        executeTransaction { realm ->
            realm.where(RealmRemovedLog::class.java)
                .equalTo("type", "courses")
                .equalTo("userId", userId)
                .equalTo("docId", courseId)
                .findAll()
                .deleteAllFromRealm()
        }
    }

    override suspend fun markCourseRemoved(userId: String, courseId: String) {
        executeTransaction { realm ->
            val log = realm.createObject(RealmRemovedLog::class.java, UUID.randomUUID().toString())
            log.docId = courseId
            log.userId = userId
            log.type = "courses"
        }
    }
}

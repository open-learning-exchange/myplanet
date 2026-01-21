package org.ole.planet.myplanet.repository

import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserSessionManager

class ActivitiesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), ActivitiesRepository {
    override suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity> {
        return queryList(RealmOfflineActivity::class.java) {
            equalTo("userName", userName)
            equalTo("type", type)
        }
    }

    override suspend fun getOfflineVisitCount(userId: String): Int {
        return queryList(RealmOfflineActivity::class.java) {
            equalTo("userId", userId)
            equalTo("type", UserSessionManager.KEY_LOGIN)
        }.size
    }

    override suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>> {
        return queryListFlow(RealmOfflineActivity::class.java) {
            equalTo("userName", userName)
            equalTo("type", UserSessionManager.KEY_LOGIN)
        }
    }

    override suspend fun markResourceAdded(userId: String?, resourceId: String) {
        withRealmAsync { realm ->
            if (!realm.isInTransaction) realm.beginTransaction()
            realm.where(RealmRemovedLog::class.java)
                .equalTo("type", "resources")
                .equalTo("userId", userId)
                .equalTo("docId", resourceId)
                .findAll().deleteAllFromRealm()
            realm.commitTransaction()
        }
    }

    override suspend fun markResourceRemoved(userId: String, resourceId: String) {
        withRealmAsync { realm ->
            if (!realm.isInTransaction) realm.beginTransaction()
            val log = realm.createObject(RealmRemovedLog::class.java, UUID.randomUUID().toString())
            log.docId = resourceId
            log.userId = userId
            log.type = "resources"
            realm.commitTransaction()
        }
    }

    override suspend fun logLogin(userId: String?, userName: String?, parentCode: String?, planetCode: String?) {
        executeTransaction { realm ->
            val offlineActivities = realm.createObject(RealmOfflineActivity::class.java, UUID.randomUUID().toString())
            offlineActivities.userId = userId
            offlineActivities.userName = userName
            offlineActivities.parentCode = parentCode
            offlineActivities.createdOn = planetCode
            offlineActivities.type = UserSessionManager.KEY_LOGIN
            offlineActivities._rev = null
            offlineActivities._id = null
            offlineActivities.description = "Member login on offline application"
            offlineActivities.loginTime = Date().time
        }
    }

    override suspend fun logLogout() {
        executeTransaction { realm ->
            RealmOfflineActivity.getRecentLogin(realm)?.logoutTime = Date().time
        }
    }

    override suspend fun logResourceOpen(userName: String?, parentCode: String?, planetCode: String?, type: String?, title: String?, resourceId: String?) {
        executeTransaction { realm ->
            val offlineActivities = realm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
            offlineActivities.user = userName
            offlineActivities.parentCode = parentCode
            offlineActivities.createdOn = planetCode
            offlineActivities.type = type
            offlineActivities.title = title
            offlineActivities.resourceId = resourceId
            offlineActivities.time = Date().time
        }
    }

    override suspend fun getOfflineVisits(userName: String?): Int {
        return withRealm { realm ->
            realm.where(RealmOfflineActivity::class.java)
                .equalTo("userName", userName)
                .equalTo("type", UserSessionManager.KEY_LOGIN)
                .findAll()
                .size
        }
    }

    override suspend fun getLastVisit(userName: String?): Long? {
        return withRealm { realm ->
            realm.where(RealmOfflineActivity::class.java)
                .equalTo("userName", userName)
                .max("loginTime") as Long?
        }
    }

    override suspend fun getGlobalLastVisit(): Long? {
        return withRealm { realm ->
            realm.where(RealmOfflineActivity::class.java)
                .max("loginTime") as Long?
        }
    }

    override suspend fun getNumberOfResourceOpen(userName: String?, type: String?): Long {
        return count(RealmResourceActivity::class.java) {
            equalTo("user", userName)
            equalTo("type", type)
        }
    }

    override suspend fun getAllResourceActivities(userName: String?, type: String?): List<RealmResourceActivity> {
        return queryList(RealmResourceActivity::class.java) {
             equalTo("user", userName)
             equalTo("type", type)
        }
    }

    override suspend fun logCourseVisit(courseId: String, title: String, userId: String) {
        executeTransaction { realm ->
            val activity = realm.createObject(RealmCourseActivity::class.java, UUID.randomUUID().toString())
            activity.type = "visit"
            activity.title = title
            activity.courseId = courseId
            activity.time = Date().time
            activity.user = userId

            val user = realm.where(RealmUserModel::class.java).equalTo("name", userId).findFirst()
            if (user != null) {
                activity.parentCode = user.parentCode
                activity.createdOn = user.planetCode
            }
        }
    }
}

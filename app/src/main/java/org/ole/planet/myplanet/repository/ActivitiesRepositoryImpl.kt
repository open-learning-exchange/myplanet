package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.NetworkUtils

class ActivitiesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : RealmRepository(databaseService, realmDispatcher), ActivitiesRepository {
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
        executeTransaction { realm ->
            realm.where(RealmRemovedLog::class.java)
                .equalTo("type", "resources")
                .equalTo("userId", userId)
                .equalTo("docId", resourceId)
                .findAll().deleteAllFromRealm()
        }
    }

    override suspend fun markResourceRemoved(userId: String, resourceId: String) {
        executeTransaction { realm ->
            val log = realm.createObject(RealmRemovedLog::class.java, UUID.randomUUID().toString())
            log.docId = resourceId
            log.userId = userId
            log.type = "resources"
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

            val user = realm.where(RealmUser::class.java).equalTo("name", userId).findFirst()
            if (user != null) {
                activity.parentCode = user.parentCode
                activity.createdOn = user.planetCode
            }
        }
    }

    override suspend fun logLogin(
        userId: String?,
        userName: String?,
        parentCode: String?,
        planetCode: String?
    ) {
        executeTransaction { realm ->
            val offlineActivities =
                realm.createObject(RealmOfflineActivity::class.java, UUID.randomUUID().toString())
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

    override suspend fun logLogout(userName: String?) {
        executeTransaction { realm ->
            RealmOfflineActivity.getRecentLogin(realm)
                ?.logoutTime = Date().time
        }
    }

    override suspend fun getGlobalLastVisit(): Long? {
        return withRealm { realm ->
            realm.where(RealmOfflineActivity::class.java).max("loginTime") as Long?
        }
    }

    override suspend fun getLastVisit(userName: String): Long? {
        return withRealm { realm ->
            realm.where(RealmOfflineActivity::class.java)
                .equalTo("userName", userName)
                .max("loginTime") as Long?
        }
    }

    override suspend fun logResourceOpen(
        userName: String?,
        parentCode: String?,
        planetCode: String?,
        title: String?,
        resourceId: String?,
        type: String?
    ) {
        executeTransaction { realm ->
            val offlineActivities =
                realm.createObject(RealmResourceActivity::class.java, "${UUID.randomUUID()}")
            offlineActivities.user = userName
            offlineActivities.parentCode = parentCode
            offlineActivities.createdOn = planetCode
            offlineActivities.type = type
            offlineActivities.title = title
            offlineActivities.resourceId = resourceId
            offlineActivities.time = Date().time
        }
    }

    override suspend fun getResourceOpenCount(userName: String, type: String): Long {
        return count(RealmResourceActivity::class.java) {
            equalTo("user", userName)
            equalTo("type", type)
        }
    }

    override suspend fun getMostOpenedResource(userName: String, type: String): Pair<String, Int>? {
        return withRealm { realm ->
            val activities = realm.where(RealmResourceActivity::class.java)
                .equalTo("user", userName)
                .equalTo("type", type)
                .findAll()

            if (activities.isEmpty()) {
                return@withRealm null
            }

            val resourceCounts = activities
                .groupBy { it.resourceId }
                .mapValues { entry ->
                    val count = entry.value.size
                    val title = entry.value.first().title
                    Pair(count, title)
                }

            val maxEntry = resourceCounts.maxByOrNull { it.value.first }

            if (maxEntry == null || maxEntry.value.first == 0) {
                null
            } else {
                Pair(maxEntry.value.second!!, maxEntry.value.first)
            }
        }
    }

    override suspend fun getUnuploadedLoginActivities(): List<org.ole.planet.myplanet.model.LoginActivityData> {
        return queryList(RealmOfflineActivity::class.java) {
            isNull("_rev")
            equalTo("type", "login")
        }.mapNotNull { activity ->
            if (activity.userId?.startsWith("guest") == true || activity.id == null || activity.userId == null) {
                null
            } else {
                org.ole.planet.myplanet.model.LoginActivityData(
                    activity.id!!,
                    activity.userId!!,
                    RealmOfflineActivity.serializeLoginActivities(activity, context)
                )
            }
        }
    }

    override suspend fun getUnuploadedTeamLogs(): List<TeamLogData> {
        return withRealm { realm ->
            val results = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
            results.map { log ->
                TeamLogData(
                    id = log.id,
                    time = log.time,
                    user = log.user,
                    type = log.type,
                    serialized = RealmTeamLog.serializeTeamActivities(log, context)
                )
            }
        }
    }

    override suspend fun markActivitiesUploaded(ids: Array<String>, revMap: Map<String, com.google.gson.JsonObject?>) {
        executeTransaction { transactionRealm ->
            val activities = transactionRealm.where(RealmOfflineActivity::class.java)
                .`in`("id", ids)
                .findAll()

            activities.forEach { activity ->
                revMap[activity.id]?.let { activity.changeRev(it) }
            }
        }
    }

    override suspend fun markTeamLogsUploaded(results: List<TeamLogUploadResult>) {
        if (results.isEmpty()) return

        executeTransaction { realm ->
            val ids = results.mapNotNull { it.id }
            val managedLogs = mutableMapOf<String, RealmTeamLog>()

            if (ids.isNotEmpty()) {
                ids.chunked(999).forEach { chunk ->
                    val queryResults = realm.where(RealmTeamLog::class.java)
                        .`in`("id", chunk.toTypedArray())
                        .findAll()
                    queryResults.forEach { log ->
                        log.id?.let { id -> managedLogs[id] = log }
                    }
                }
            }

            val uploadsWithoutId = results.filter { it.id == null }
            val fallbackLogs = mutableMapOf<Triple<Long?, String?, String?>, RealmTeamLog>()

            if (uploadsWithoutId.isNotEmpty()) {
                uploadsWithoutId.chunked(250).forEach { chunk ->
                    val query = realm.where(RealmTeamLog::class.java)
                    query.beginGroup()
                    chunk.forEachIndexed { index, upload ->
                        if (index > 0) query.or()
                        query.beginGroup()
                            .equalTo("time", upload.time)
                            .equalTo("user", upload.user)
                            .equalTo("type", upload.type)
                        .endGroup()
                    }
                    query.endGroup()

                    val queryResults = query.findAll()
                    queryResults.forEach { log ->
                        val key = Triple(log.time, log.user, log.type)
                        fallbackLogs[key] = log
                    }
                }
            }

            results.forEach { upload ->
                val managedLog = if (upload.id != null) {
                    managedLogs[upload.id]
                } else {
                    val key = Triple(upload.time, upload.user, upload.type)
                    fallbackLogs[key]
                }
                managedLog?._id = upload._id
                managedLog?._rev = upload._rev
            }
        }
    }

    override suspend fun recordSyncActivity(settings: SharedPreferences) {
        executeTransaction { realm ->
            val user = realm.where(RealmUser::class.java).equalTo("id", settings.getString("userId", "")).findFirst()
            if (user == null || user.id?.startsWith("guest") == true) {
                return@executeTransaction
            }
            val activities = realm.createObject(RealmResourceActivity::class.java, UUID.randomUUID().toString())
            activities.user = user.name
            activities._rev = null
            activities._id = null
            activities.parentCode = user.parentCode
            activities.createdOn = user.planetCode
            activities.type = "sync"
            activities.time = Date().time
        }
    }

    override fun serializeResourceActivities(activity: RealmResourceActivity): JsonObject {
        val ob = JsonObject()
        ob.addProperty("user", activity.user)
        ob.addProperty("resourceId", activity.resourceId)
        ob.addProperty("type", activity.type)
        ob.addProperty("title", activity.title)
        ob.addProperty("time", activity.time)
        ob.addProperty("createdOn", activity.createdOn)
        ob.addProperty("parentCode", activity.parentCode)
        ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        ob.addProperty("deviceName", NetworkUtils.getDeviceName())
        return ob
    }
}

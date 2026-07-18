package org.ole.planet.myplanet.repository

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CourseActivityDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.UserChallengeActionsDao
import org.ole.planet.myplanet.model.LoginActivityData
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.UserChallengeActions
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

class ActivitiesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: Lazy<UserRepository>,
    private val apiInterface: ApiInterface,
    private val sharedPrefManager: SharedPrefManager,
    private val timeProvider: TimeProvider,
    private val userChallengeActionsDao: UserChallengeActionsDao,
    private val courseActivityDao: CourseActivityDao,
    private val resourceActivityDao: ResourceActivityDao,
    private val offlineActivityDao: OfflineActivityDao,
    private val removedLogDao: RemovedLogDao
) : ActivitiesRepository {
    override suspend fun getOfflineVisitCount(userId: String): Int {
        return offlineActivityDao.countByUserIdAndType(userId, UserSessionManager.KEY_LOGIN)
    }

    override suspend fun getOfflineLoginCount(userName: String): Int {
        return offlineActivityDao.countByUserNameAndType(userName, UserSessionManager.KEY_LOGIN)
    }

    override suspend fun getOfflineLogins(userName: String): Flow<List<OfflineActivity>> {
        return offlineActivityDao.observeByUserNameAndType(userName, UserSessionManager.KEY_LOGIN)
    }

    override suspend fun markResourceAdded(userId: String?, resourceId: String) {
        removedLogDao.deleteByTypeUserAndDoc("resources", userId, resourceId)
    }

    override suspend fun markResourceRemoved(userId: String, resourceId: String) {
        removedLogDao.insert(
            RemovedLog().apply {
                id = UUID.randomUUID().toString()
                docId = resourceId
                this.userId = userId
                type = "resources"
            }
        )
    }

    override suspend fun logCourseVisit(courseId: String, title: String, userId: String) {
        val user = userRepository.get().getUserByName(userId)
        val parentCode = user?.parentCode
        val createdOn = user?.planetCode

        courseActivityDao.insert(
            CourseActivity().apply {
                id = UUID.randomUUID().toString()
                type = "visit"
                this.title = title
                this.courseId = courseId
                time = Date().time
                this.user = userId

                if (user != null) {
                    this.parentCode = parentCode
                    this.createdOn = createdOn
                }
            }
        )
    }

    override suspend fun logLogin(
        userId: String?,
        userName: String?,
        parentCode: String?,
        planetCode: String?
    ) {
        offlineActivityDao.insert(
            OfflineActivity().apply {
                id = UUID.randomUUID().toString()
                this.userId = userId
                this.userName = userName
                this.parentCode = parentCode
                createdOn = planetCode
                type = UserSessionManager.KEY_LOGIN
                _rev = null
                _id = null
                description = "Member login on offline application"
                loginTime = Date().time
            }
        )
    }

    override suspend fun logLogout(userName: String?) {
        offlineActivityDao.getLatestByType(UserSessionManager.KEY_LOGIN)?.let { activity ->
            offlineActivityDao.updateLogoutTime(activity.id, Date().time)
        }
    }

    override suspend fun getGlobalLastVisit(): Long? {
        return offlineActivityDao.getGlobalLastVisit()
    }

    override suspend fun getLastVisit(userName: String): Long? {
        return offlineActivityDao.getLastVisit(userName)
    }

    override suspend fun logResourceOpen(
        userName: String?,
        parentCode: String?,
        planetCode: String?,
        title: String?,
        resourceId: String?,
        type: String?
    ) {
        resourceActivityDao.insert(
            ResourceActivity().apply {
                id = UUID.randomUUID().toString()
                user = userName
                this.parentCode = parentCode
                createdOn = planetCode
                this.type = type
                this.title = title
                this.resourceId = resourceId
                time = Date().time
            }
        )
    }

    override suspend fun getResourceOpenCount(userName: String, type: String): Long {
        return resourceActivityDao.countByUserAndType(userName, type)
    }

    override suspend fun getMostOpenedResource(userName: String, type: String): Pair<String, Int>? {
        val activities = resourceActivityDao.getByUserAndType(userName, type)
        if (activities.isEmpty()) {
            return null
        }

        val resourceCounts = activities
            .groupBy { it.resourceId }
            .mapValues { entry ->
                val count = entry.value.size
                val title = entry.value.first().title
                Pair(count, title)
            }
            .filterValues { it.second != null }

        val maxEntry = resourceCounts.maxByOrNull { it.value.first }

        return if (maxEntry == null || maxEntry.value.first == 0) {
            null
        } else {
            Pair(maxEntry.value.second ?: "", maxEntry.value.first)
        }
    }

    private suspend fun getUnuploadedLoginActivities(): List<LoginActivityData> {
        return offlineActivityDao.getPendingLoginUploads().mapNotNull { activity ->
            if (activity.userId?.startsWith("guest") == true || activity.userId == null) {
                null
            } else {
                LoginActivityData(
                    activity.id,
                    activity.userId ?: return@mapNotNull null,
                    serializeLoginActivities(activity, context)
                )
            }
        }
    }

    private suspend fun markActivitiesUploaded(ids: Array<String>, revMap: Map<String, JsonObject?>) {
        val activities = offlineActivityDao.getByIds(ids.toList())
        activities.forEach { activity ->
            revMap[activity.id]?.let { activity.changeRev(it) }
        }
        if (activities.isNotEmpty()) {
            offlineActivityDao.upsertAll(activities)
        }
    }


    override suspend fun recordSyncUserChallengeAction(userId: String) {
        val action = UserChallengeActions().apply {
            id = UUID.randomUUID().toString()
            this.userId = userId
            actionType = "sync"
            resourceId = null
            time = timeProvider.now()
        }
        userChallengeActionsDao.insert(action)
    }

    override suspend fun recordSyncActivity(userId: String) {
        val user = userRepository.get().getUserById(userId)
        if (user == null || user.id?.startsWith("guest") == true) {
            return
        }
        val userName = user.name
        val parentCode = user.parentCode
        val createdOn = user.planetCode

        resourceActivityDao.insert(
            ResourceActivity().apply {
                id = UUID.randomUUID().toString()
                this.user = userName
                _rev = null
                _id = null
                this.parentCode = parentCode
                this.createdOn = createdOn
                type = "sync"
                time = Date().time
            }
        )
    }

    private fun activityFromJson(
        json: JsonObject,
        existingActivitiesMap: MutableMap<String, OfflineActivity>,
        fallbackActivitiesMap: MutableMap<String, OfflineActivity>
    ): OfflineActivity {
        val serverId = JsonUtils.getString("_id", json)
        val loginTime = JsonUtils.getLong("loginTime", json)
        val userName = JsonUtils.getString("user", json)

        val fallbackKey = "${loginTime}_${userName}"
        val activity = existingActivitiesMap[serverId]
            ?: fallbackActivitiesMap[fallbackKey]
            ?: OfflineActivity().apply { id = serverId }

        activity._rev = JsonUtils.getString("_rev", json)
        activity._id = serverId
        activity.loginTime = loginTime
        activity.type = JsonUtils.getString("type", json)
        activity.userName = userName
        activity.parentCode = JsonUtils.getString("parentCode", json)
        activity.createdOn = JsonUtils.getString("createdOn", json)
        activity.logoutTime = JsonUtils.getLong("logoutTime", json)
        activity.androidId = JsonUtils.getString("androidId", json)

        existingActivitiesMap[serverId] = activity
        if (loginTime > 0 && userName.isNotEmpty()) {
            fallbackActivitiesMap.putIfAbsent(fallbackKey, activity)
        }
        return activity
    }

    override suspend fun hasUserSyncAction(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        return hasUserCompletedSync(userId)
    }

    override suspend fun hasUserCompletedSync(userId: String): Boolean {
        if (userId.isEmpty()) return false
        return userChallengeActionsDao.countByUserAndType(userId, "sync") > 0
    }

    private fun serializeLoginActivities(activity: OfflineActivity, context: Context): JsonObject {
        val ob = JsonObject()
        ob.addProperty("user", activity.userName)
        ob.addProperty("type", activity.type)
        ob.addProperty("loginTime", activity.loginTime)
        ob.addProperty("logoutTime", activity.logoutTime)
        ob.addProperty("createdOn", activity.createdOn)
        ob.addProperty("parentCode", activity.parentCode)
        ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        ob.addProperty("deviceName", NetworkUtils.getDeviceName())
        ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
        if (activity._id != null) {
            ob.addProperty("_id", activity.logoutTime)
        }
        if (activity._rev != null) {
            ob.addProperty("_rev", activity._rev)
        }
        return ob
    }

    override suspend fun uploadActivities() {
        val activitiesToUpload = getUnuploadedLoginActivities()

        activitiesToUpload.chunked(50).forEach { batch ->
            val successfulUpdates = mutableMapOf<String, JsonObject?>()

            val semaphore = Semaphore(6)
            coroutineScope {
                val deferreds = batch.map { activityData ->
                    async {
                        try {
                            val `object` = semaphore.withPermit {
                                apiInterface.postDoc(
                                    UrlUtils.header, "application/json",
                                    "${UrlUtils.getUrl()}/login_activities", activityData.serialized
                                ).body()
                            }
                            activityData.id to `object`
                        } catch (e: IOException) {
                            Log.e("ActivitiesRepository", "Exception in UploadManager", e)
                            null
                        }
                    }
                }
                deferreds.awaitAll().filterNotNull().forEach { (id, obj) ->
                    successfulUpdates[id] = obj
                }
            }

            if (successfulUpdates.isNotEmpty()) {
                val idsToUpdate = successfulUpdates.keys.toTypedArray()
                markActivitiesUploaded(idsToUpdate, successfulUpdates)
            }
        }
    }

    override suspend fun insertLoginActivitiesFromSync(docs: List<JsonObject>) {
        val documentList = docs.filter { jsonDoc ->
            !JsonUtils.getString("_id", jsonDoc).startsWith("_design")
        }
        if (documentList.isEmpty()) return

        val ids = documentList.map { JsonUtils.getString("_id", it) }.filter { it.isNotEmpty() }.distinct()
        val existingActivitiesMap = if (ids.isNotEmpty()) {
            offlineActivityDao.getByRemoteIds(ids).associateBy { it._id ?: "" }.toMutableMap()
        } else {
            mutableMapOf()
        }

        val loginTimes = documentList.map { JsonUtils.getLong("loginTime", it) }.filter { it > 0 }.distinct()
        val userNames = documentList.map { JsonUtils.getString("user", it) }.filter { it.isNotEmpty() }.distinct()
        val fallbackActivitiesMap = if (loginTimes.isNotEmpty() && userNames.isNotEmpty()) {
            offlineActivityDao.getByLoginTimesAndUserNames(loginTimes, userNames)
                .associateBy { "${it.loginTime}_${it.userName}" }
                .toMutableMap()
        } else {
            mutableMapOf()
        }

        val activities = documentList.map { jsonDoc ->
            activityFromJson(jsonDoc, existingActivitiesMap, fallbackActivitiesMap)
        }
        offlineActivityDao.upsertAll(activities)
    }

    override suspend fun uploadMyPlanetActivities(userModel: RealmUser) {
        apiInterface.postDoc(
            UrlUtils.header,
            "application/json",
            "${UrlUtils.getUrl()}/myplanet_activities",
            MyPlanet.getNormalMyPlanetActivities(context, sharedPrefManager, userModel)
        )

        val response = apiInterface.getJsonObject(
            UrlUtils.header,
            "${UrlUtils.getUrl()}/myplanet_activities/${org.ole.planet.myplanet.utils.VersionUtils.getAndroidId(context)}@${NetworkUtils.getUniqueIdentifier()}"
        )

        var `object` = response.body()

        if (`object` != null) {
            val usages = `object`.getAsJsonArray("usages")
            usages.addAll(MyPlanet.getTabletUsages(context, sharedPrefManager))
            `object`.add("usages", usages)
        } else {
            `object` = MyPlanet.getMyPlanetActivities(context, sharedPrefManager, userModel)
        }

        apiInterface.postDoc(
            UrlUtils.header,
            "application/json",
            "${UrlUtils.getUrl()}/myplanet_activities",
            `object`
        )
    }
}

internal fun serializeResourceActivities(activity: ResourceActivity): JsonObject {
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

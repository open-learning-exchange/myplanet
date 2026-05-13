package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import dagger.Lazy
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.TaskNotificationResult
import org.ole.planet.myplanet.model.TeamNotificationInfo

class NotificationsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val userRepository: Lazy<UserRepository>,
    private val apiInterface: ApiInterface
) : RealmRepository(databaseService, realmDispatcher), NotificationsRepository {
    override suspend fun refresh() {
        withRealm { it.refresh() }
    }

    override suspend fun markNotificationAsRead(notificationId: String, userId: String?) {
        if (notificationId.startsWith("summary_")) {
            val type = notificationId.removePrefix("summary_")
            executeTransaction { realm ->
                realm.where(RealmNotification::class.java)
                    .equalTo("userId", userId)
                    .equalTo("type", type)
                    .equalTo("isRead", false)
                    .findAll()
                    .forEach { it.isRead = true; it.needsSync = it.isFromServer }
            }
        } else {
            executeTransaction { realm ->
                val notification = realm.where(RealmNotification::class.java)
                    .equalTo("id", notificationId)
                    .findFirst()
                notification?.isRead = true
                notification?.needsSync = notification.isFromServer == true
            }
        }
    }

    override suspend fun getUnreadCount(userId: String?, isAdmin: Boolean): Int {
        if (userId == null) return 0

        return count(RealmNotification::class.java) {
            beginGroup()
            equalTo("userId", userId)
            if (isAdmin) {
                or()
                equalTo("userId", "SYSTEM")
            }
            endGroup()
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) {
        userId ?: return

        val notificationId = "$userId:resource:count"
        val existingNotification = findByField(RealmNotification::class.java, "id", notificationId)

        if (resourceCount > 0) {
            val previousCount = existingNotification?.message?.toIntOrNull() ?: 0
            val countChanged = previousCount != resourceCount

            val notification = existingNotification?.apply {
                message = "$resourceCount"
                relatedId = "$resourceCount"
                if (countChanged) {
                    this.isRead = false
                    this.createdAt = Date()
                }
            } ?: RealmNotification().apply {
                this.id = notificationId
                this.userId = userId
                this.type = "resource"
                this.message = "$resourceCount"
                this.relatedId = "$resourceCount"
                this.createdAt = Date()
            }
            save(notification)
        } else {
            existingNotification?.let { delete(RealmNotification::class.java, "id", it.id) }
        }
    }

    override suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String> {
        if (notificationIds.isEmpty()) return emptySet()

        val updatedIds = mutableSetOf<String>()
        val now = Date()
        executeTransaction { realm ->
            val notifications = realm.where(RealmNotification::class.java)
                .`in`("id", notificationIds.toTypedArray())
                .findAll()

            notifications.forEach { notification ->
                notification.isRead = true
                notification.createdAt = now
                if (notification.isFromServer) notification.needsSync = true
                updatedIds.add(notification.id)
            }
        }
        return updatedIds
    }

    override suspend fun markAllUnreadAsRead(userId: String?): Set<String> {
        val actualUserId = userId ?: return emptySet()
        val updatedIds = mutableSetOf<String>()
        val now = Date()
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .equalTo("isRead", false)
                .findAll()
                ?.forEach { notification ->
                    notification.isRead = true
                    notification.createdAt = now
                    if (notification.isFromServer) notification.needsSync = true
                    updatedIds.add(notification.id)
                }
        }
        return updatedIds
    }

    override suspend fun getNotifications(userId: String, filter: String, isAdmin: Boolean): List<NotificationPayload> {
        return queryList(RealmNotification::class.java) {
            beginGroup()
            equalTo("userId", userId)
            if (isAdmin) {
                or()
                equalTo("userId", "SYSTEM")
            }
            endGroup()
            notEqualTo("message", "INVALID")
            isNotEmpty("message")
            when (filter) {
                "read" -> equalTo("isRead", true)
                "unread" -> equalTo("isRead", false)
            }
            sort("isRead", io.realm.Sort.ASCENDING, "createdAt", io.realm.Sort.DESCENDING)
        }.map {
            NotificationPayload(
                id = it.id,
                userId = it.userId,
                message = it.message,
                isRead = it.isRead,
                createdAt = it.createdAt.time,
                type = it.type,
                relatedId = it.relatedId,
                title = it.title,
                link = it.link,
                priority = it.priority,
                isFromServer = it.isFromServer,
                rev = it.rev,
                needsSync = it.needsSync
            )
        }
    }

    override suspend fun getSurveyId(relatedId: String?): String? {
        return relatedId?.let {
            findByField(org.ole.planet.myplanet.model.RealmStepExam::class.java, "name", it)?.id
        }
    }

    override suspend fun getTaskDetails(relatedId: String?): TaskNotificationResult? {
        return relatedId?.let {
            val task = findByField(org.ole.planet.myplanet.model.RealmTeamTask::class.java, "id", it)
            val linkJson = org.json.JSONObject(task?.link ?: "{}")
            val teamId = linkJson.optString("teams")
            if (teamId.isNotEmpty()) {
                val teamObject = findByField(org.ole.planet.myplanet.model.RealmMyTeam::class.java, "_id", teamId)
                TaskNotificationResult(teamId, teamObject?.name, teamObject?.type)
            } else {
                null
            }
        }
    }

    override suspend fun getJoinRequestTeamId(relatedId: String?): String? {
        return relatedId?.let {
            val actualJoinRequestId = if (it.startsWith("join_request_")) {
                it.removePrefix("join_request_")
            } else {
                it
            }
            queryList(org.ole.planet.myplanet.model.RealmMyTeam::class.java) {
                equalTo("_id", actualJoinRequestId)
                equalTo("docType", "request")
            }.firstOrNull()?.teamId
        }
    }

    override suspend fun getJoinRequestDetails(relatedId: String?): Pair<String, String> {
        val joinRequest = queryList(RealmMyTeam::class.java) {
            equalTo("_id", relatedId)
            equalTo("docType", "request")
        }.firstOrNull()
        val team = joinRequest?.teamId?.let { tid ->
            findByField(RealmMyTeam::class.java, "_id", tid)
        }
        val uid = joinRequest?.userId
        val teamName = team?.name ?: "Unknown Team"

        val requester = uid?.let { userRepository.get().getUserById(it) }
        return Pair(requester?.name ?: "Unknown User", teamName)
    }

    override suspend fun getTaskTeamNamesByTaskIds(taskIds: List<String>): Map<String, String> {
        if (taskIds.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()

        val tasks = queryList(RealmTeamTask::class.java) {
            beginGroup()
            taskIds.forEachIndexed { index, taskId ->
                if (index > 0) or()
                equalTo("id", taskId)
            }
            endGroup()
        }

        val teamIds = tasks.mapNotNull { it.teamId }.filter { it.isNotEmpty() }.distinct()
        if (teamIds.isNotEmpty()) {
            val teams = queryList(RealmMyTeam::class.java) {
                beginGroup()
                teamIds.forEachIndexed { index, id ->
                    if (index > 0) or()
                    equalTo("_id", id)
                }
                endGroup()
            }
            val teamMap = teams.associateBy({ it._id ?: "" }, { it.name ?: "" })

            tasks.forEach { task ->
                val taskId = task.id
                val teamId = task.teamId
                if (!taskId.isNullOrEmpty() && !teamId.isNullOrEmpty()) {
                    teamMap[teamId]?.let { teamName ->
                        map[taskId] = teamName
                    }
                }
            }
        }
        return map
    }

    override suspend fun getJoinRequestDetailsBatch(relatedIds: List<String>): Map<String, Pair<String, String>> {
        if (relatedIds.isEmpty()) return emptyMap()

        val joinRequests = queryList(RealmMyTeam::class.java) {
            equalTo("docType", "request")
            beginGroup()
            relatedIds.forEachIndexed { index, id ->
                if (index > 0) or()
                equalTo("_id", id)
            }
            endGroup()
        }

        val teamIds = joinRequests.mapNotNull { it.teamId }.distinct()

        val teamMap = if (teamIds.isNotEmpty()) {
            val teams = queryList(RealmMyTeam::class.java) {
                beginGroup()
                teamIds.forEachIndexed { index, id ->
                    if (index > 0) or()
                    equalTo("_id", id)
                }
                endGroup()
            }
            teams.associateBy({ it._id ?: "" }, { it.name ?: "Unknown Team" })
        } else emptyMap()

        val intermediateList = mutableListOf<Triple<String, String, String>>()
        joinRequests.forEach { jr ->
            val id = jr._id
            if (!id.isNullOrEmpty()) {
                val tName = teamMap[jr.teamId ?: ""] ?: "Unknown Team"
                intermediateList.add(Triple(id, jr.userId ?: "", tName))
            }
        }

        val map = mutableMapOf<String, Pair<String, String>>()
        val userIds = intermediateList.map { it.second }.filter { it.isNotEmpty() }.distinct()
        val userMap = mutableMapOf<String, String>()
        if (userIds.isNotEmpty()) {
            val users = userRepository.get().getUsersByIds(userIds)
            for (user in users) {
                user.id?.let { id ->
                    userMap[id] = user.name ?: "Unknown User"
                }
            }
        }

        for (triple in intermediateList) {
            val uName = if (triple.second.isNotEmpty()) userMap[triple.second] ?: "Unknown User" else "Unknown User"
            map[triple.first] = Pair(uName, triple.third)
        }

        return map
    }

    override suspend fun getTaskTeamName(taskTitle: String): String? {
        val taskObj = findByField(RealmTeamTask::class.java, "title", taskTitle)
        val team = taskObj?.teamId?.let { findByField(RealmMyTeam::class.java, "_id", it) }
        return team?.name
    }

    override suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo {
        val current = System.currentTimeMillis()
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        val notification = queryList(RealmTeamNotification::class.java) {
            equalTo("parentId", teamId)
            equalTo("type", "chat")
        }.firstOrNull()

        val chatCount = count(RealmNews::class.java) {
            equalTo("viewableBy", "teams")
            equalTo("viewableId", teamId)
        }

        val hasChat = notification != null && notification.lastCount < chatCount

        val tasks = queryList(RealmTeamTask::class.java) {
            equalTo("assignee", userId)
            between("deadline", current, tomorrow.timeInMillis)
        }

        val hasTask = tasks.isNotEmpty()

        return TeamNotificationInfo(hasTask, hasChat)
    }

    override suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        if (teamIds.isEmpty()) {
            return emptyMap()
        }
        val notificationMap = mutableMapOf<String, TeamNotificationInfo>()

        // 1. Fetch all relevant notifications in a single query
        val notificationsResult = queryList(RealmTeamNotification::class.java) {
            equalTo("type", "chat")
            beginGroup()
            teamIds.forEachIndexed { index, id ->
                if (index > 0) or()
                equalTo("parentId", id)
            }
            endGroup()
        }
        val notificationsById = mutableMapOf<String, RealmTeamNotification>()
        notificationsResult.forEach {
            it.parentId?.let { parentId ->
                notificationsById[parentId] = it
            }
        }

        // 2. Fetch all relevant chat counts in a single query
        val chatsResult = queryList(RealmNews::class.java) {
            equalTo("viewableBy", "teams")
            beginGroup()
            teamIds.forEachIndexed { index, id ->
                if (index > 0) or()
                equalTo("viewableId", id)
            }
            endGroup()
        }
        val chatCountsById = mutableMapOf<String, Long>()
        chatsResult.forEach {
            it.viewableId?.let { viewableId ->
                val currentCount = chatCountsById[viewableId] ?: 0
                chatCountsById[viewableId] = currentCount + 1
            }
        }

        // 3. Fetch all relevant tasks once
        val current = System.currentTimeMillis()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tasks = queryList(RealmTeamTask::class.java) {
            equalTo("assignee", userId)
            between("deadline", current, tomorrow.timeInMillis)
        }
        val hasTask = tasks.isNotEmpty()

        // 4. Combine the results in memory
        for (teamId in teamIds) {
            val notification = notificationsById[teamId]
            val chatCount = chatCountsById[teamId] ?: 0L
            val hasChat = notification != null && notification.lastCount < chatCount
            notificationMap[teamId] = TeamNotificationInfo(hasTask, hasChat)
        }
        return notificationMap
    }

    override suspend fun getPendingSyncNotifications(): List<RealmNotification> {
        return queryList(RealmNotification::class.java) {
            equalTo("needsSync", true)
            isNotNull("rev")
        }
    }

    override suspend fun markNotificationsSynced(syncResults: List<Pair<String, String?>>) {
        if (syncResults.isEmpty()) return
        val ids = syncResults.map { it.first }.toTypedArray()
        val revMap = syncResults.toMap()
        executeTransaction { realm ->
            val notifications = realm.where(RealmNotification::class.java)
                .`in`("id", ids)
                .findAll()

            notifications.forEach { notification ->
                notification.needsSync = false
                revMap[notification.id]?.let { newRev ->
                    notification.rev = newRev
                }
            }
        }
    }

    override suspend fun insert(doc: com.google.gson.JsonObject) {
        executeTransaction { realm ->
            internalInsert(realm, doc)
        }
    }

    private fun internalInsert(mRealm: io.realm.Realm, doc: com.google.gson.JsonObject) {
        val id = doc.get("_id")?.asString ?: return
        val notification = mRealm.where(RealmNotification::class.java)
            .equalTo("id", id).findFirst()
            ?: mRealm.createObject(RealmNotification::class.java, id)
        notification.apply {
            userId = doc.get("user")?.asString ?: ""
            message = doc.get("message")?.asString ?: ""
            type = doc.get("type")?.asString ?: ""
            link = doc.get("link")?.asString
            priority = doc.get("priority")?.asInt ?: 0
            rev = doc.get("_rev")?.asString
            // Preserve local read state if a change is pending upload
            if (!needsSync) {
                isRead = doc.get("status")?.asString != "unread"
            }
            createdAt = doc.get("time")?.let { java.util.Date(it.asLong) } ?: java.util.Date()
            isFromServer = true
        }
    }

    override suspend fun fetchAndSaveNotificationsForUser(userId: String): Boolean {
        return try {
            val url = "${UrlUtils.getUrl()}/notifications/_find"
            val selector = JsonObject().apply {
                add("selector", JsonObject().apply { addProperty("user", userId) })
            }
            val response = apiInterface.findDocs(UrlUtils.header, "application/json", url, selector)
            if (!response.isSuccessful || response.body() == null) return false
            val docs = JsonUtils.getJsonArray("docs", response.body())
            executeTransaction { realm ->
                for (element in docs) {
                    val doc = element.asJsonObject
                    if (!JsonUtils.getString("_id", doc).startsWith("_design")) {
                        internalInsert(realm, doc)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = ArrayList<com.google.gson.JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            internalInsert(realm, jsonDoc)
        }
    }
}

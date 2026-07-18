package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.TeamNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.TaskNotificationResult
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeProvider

private const val STORAGE_WARNING_AVAILABLE_PERCENT = 10

class NotificationsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val userRepository: Lazy<UserRepository>,
    private val teamsRepository: Lazy<TeamsRepository>,
    private val timeProvider: TimeProvider,
    private val teamNotificationDao: TeamNotificationDao,
    private val notificationDao: NotificationDao,
    private val teamTaskDao: TeamTaskDao
) : RealmRepository(databaseService, realmDispatcher), NotificationsRepository {
    override suspend fun refresh() {
        withRealm { it.refresh() }
    }

    override suspend fun markNotificationAsRead(notificationId: String, userId: String?) {
        if (notificationId.startsWith("summary_")) {
            val type = notificationId.removePrefix("summary_")
            notificationDao.markSummaryAsRead(userId, type)
        } else {
            notificationDao.markAsRead(notificationId)
        }
    }

    override suspend fun getUnreadCount(userId: String?, isAdmin: Boolean): Int {
        if (userId == null) return 0

        return notificationDao.getUnreadCount(userId, isAdmin)
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) {
        userId ?: return

        val notificationId = "$userId:resource:count"
        val existingNotification = notificationDao.getById(notificationId)

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
            notificationDao.upsert(notification)
        } else {
            existingNotification?.let { notificationDao.deleteById(it.id) }
        }
    }

    override suspend fun updateStorageNotification(userId: String?, availablePercent: Int) {
        userId ?: return

        val notificationId = "$userId:storage"
        val existingNotification = notificationDao.getById(notificationId)

        if (availablePercent <= STORAGE_WARNING_AVAILABLE_PERCENT) {
            val previousPercent = existingNotification?.message?.replace("%", "")?.toIntOrNull()
            val percentChanged = previousPercent != availablePercent

            val notification = existingNotification?.apply {
                message = "$availablePercent%"
                relatedId = "storage"
                if (percentChanged) {
                    this.isRead = false
                    this.createdAt = Date()
                }
            } ?: RealmNotification().apply {
                this.id = notificationId
                this.userId = userId
                this.type = "storage"
                this.message = "$availablePercent%"
                this.relatedId = "storage"
                this.createdAt = Date()
            }
            notificationDao.upsert(notification)
        } else {
            existingNotification?.let { notificationDao.deleteById(it.id) }
        }
    }

    override suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String> {
        if (notificationIds.isEmpty()) return emptySet()

        val existingIds = notificationDao.getByIds(notificationIds.toList()).map { it.id }.toSet()
        if (existingIds.isEmpty()) return emptySet()
        notificationDao.markAsRead(existingIds.toList(), Date())
        return existingIds
    }

    override suspend fun markAllUnreadAsRead(userId: String?): Set<String> {
        val actualUserId = userId ?: return emptySet()
        val unreadIds = notificationDao.getNotifications(actualUserId, "unread", false).map { it.id }.toSet()
        if (unreadIds.isEmpty()) return emptySet()
        notificationDao.markAllUnreadAsRead(actualUserId, Date())
        return unreadIds
    }

    override suspend fun getNotifications(userId: String, filter: String, isAdmin: Boolean): List<NotificationPayload> {
        val normalizedFilter = when (filter) {
            "read", "unread" -> filter
            else -> ""
        }
        return notificationDao.getNotifications(userId, normalizedFilter, isAdmin).map {
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
            findByField(RealmStepExam::class.java, "name", it)?.id
        }
    }

    override suspend fun getTaskDetails(relatedId: String?): TaskNotificationResult? {
        return relatedId?.let {
            val task = teamTaskDao.getById(it)
            val linkJson = org.json.JSONObject(task?.link ?: "{}")
            val teamId = linkJson.optString("teams")
            if (teamId.isNotEmpty()) {
                val teamObject = teamsRepository.get().getTeamLabelInfo(teamId)
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
            teamsRepository.get().getJoinRequestInfo(actualJoinRequestId)?.teamId
        }
    }

    override suspend fun getJoinRequestDetails(relatedId: String?): Pair<String, String> {
        val joinRequest = teamsRepository.get().getJoinRequestInfo(relatedId)
        val teamName = joinRequest?.teamId?.let { tid ->
            teamsRepository.get().getTeamLabelInfo(tid)?.name
        } ?: "Unknown Team"
        val uid = joinRequest?.userId

        val requester = uid?.let { userRepository.get().getUserById(it) }
        return Pair(requester?.name ?: "Unknown User", teamName)
    }

    override suspend fun getTaskTeamNamesByTaskIds(taskIds: List<String>): Map<String, String> {
        if (taskIds.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()

        val tasks = teamTaskDao.getByIds(taskIds)

        val teamIds = tasks.mapNotNull { it.teamId }.filter { it.isNotEmpty() }.distinct()
        if (teamIds.isNotEmpty()) {
            val teamMap = teamsRepository.get().getTeamNamesByIds(teamIds)

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

        val joinRequests = teamsRepository.get().getJoinRequestsInfo(relatedIds)

        val teamIds = joinRequests.map { it.teamId }.filter { it.isNotEmpty() }.distinct()

        val teamMap = teamsRepository.get().getTeamNamesByIds(teamIds)

        val intermediateList = mutableListOf<Triple<String, String, String>>()
        joinRequests.forEach { jr ->
            val id = jr.id
            if (id.isNotEmpty()) {
                val tName = teamMap[jr.teamId] ?: "Unknown Team"
                intermediateList.add(Triple(id, jr.userId, tName))
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
        val taskObj = teamTaskDao.getByTitle(taskTitle)
        val teamInfo = taskObj?.teamId?.let { teamsRepository.get().getTeamLabelInfo(it) }
        return teamInfo?.name
    }

    override suspend fun getTaskTeamNamesByTaskTitles(taskTitles: List<String>): Map<String, String> {
        if (taskTitles.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()

        val tasks = teamTaskDao.getByTitles(taskTitles)

        val teamIds = tasks.mapNotNull { it.teamId }.filter { it.isNotEmpty() }.distinct()
        if (teamIds.isNotEmpty()) {
            val teamMap = teamsRepository.get().getTeamNamesByIds(teamIds)

            tasks.forEach { task ->
                val taskTitle = task.title
                val teamId = task.teamId
                if (!taskTitle.isNullOrEmpty() && !teamId.isNullOrEmpty()) {
                    teamMap[teamId]?.let { teamName ->
                        map[taskTitle] = teamName
                    }
                }
            }
        }
        return map
    }

    override suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo {
        val current = timeProvider.now()
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        val notification = teamNotificationDao.findByParentAndType(teamId, "chat")

        val chatCount = count(RealmNews::class.java) {
            equalTo("viewableBy", "teams")
            equalTo("viewableId", teamId)
        }

        val hasChat = notification != null && notification.lastCount < chatCount

        val tasks = teamTaskDao.getTasksForUserBetween(userId, current, tomorrow.timeInMillis)

        val hasTask = tasks.isNotEmpty()

        return TeamNotificationInfo(hasTask, hasChat)
    }

    override suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        if (teamIds.isEmpty()) {
            return emptyMap()
        }
        val notificationMap = mutableMapOf<String, TeamNotificationInfo>()

        // 1. Fetch all relevant notifications in a single query
        val notificationsResult = teamNotificationDao.getByTypeAndParentIds("chat", teamIds)
        val notificationsById = mutableMapOf<String, TeamNotification>()
        notificationsResult.forEach {
            it.parentId?.let { parentId ->
                notificationsById[parentId] = it
            }
        }

        // 2. Fetch all relevant chat counts in a single query
        val chatsResult = queryList(RealmNews::class.java) {
            equalTo("viewableBy", "teams")
            `in`("viewableId", teamIds.toTypedArray())
        }
        val chatCountsById = mutableMapOf<String, Long>()
        chatsResult.forEach {
            it.viewableId?.let { viewableId ->
                val currentCount = chatCountsById[viewableId] ?: 0
                chatCountsById[viewableId] = currentCount + 1
            }
        }

        // 3. Fetch all relevant tasks once
        val current = timeProvider.now()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tasks = teamTaskDao.getTasksForUserBetween(userId, current, tomorrow.timeInMillis)
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
        return notificationDao.getPendingSyncNotifications()
    }

    override suspend fun markNotificationsSynced(syncResults: List<Pair<String, String?>>) {
        if (syncResults.isEmpty()) return
        syncResults.forEach { (id, rev) ->
            notificationDao.markSynced(id, rev)
        }
    }

    private fun parseNotification(doc: JsonObject): RealmNotification? {
        val id = doc.get("_id")?.asString ?: return null
        return RealmNotification().apply {
            this.id = id
            userId = doc.get("user")?.asString ?: ""
            message = doc.get("message")?.asString ?: ""
            type = doc.get("type")?.asString ?: ""
            link = doc.get("link")?.asString
            priority = doc.get("priority")?.asInt ?: 0
            rev = doc.get("_rev")?.asString
            isRead = doc.get("status")?.asString != "unread"
            createdAt = doc.get("time")?.let { Date(it.asLong) } ?: Date()
            isFromServer = true
        }
    }

    override suspend fun insert(doc: JsonObject) {
        val parsed = parseNotification(doc) ?: return
        val existing = notificationDao.getById(parsed.id)
        if (existing?.needsSync == true) {
            parsed.needsSync = true
            parsed.isRead = existing.isRead
        }
        notificationDao.upsert(parsed)
    }

    override suspend fun deleteNotifications(ids: Set<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val deletedIds = notificationDao.getByIds(ids.toList()).map { it.id }.toSet()
        if (deletedIds.isNotEmpty()) {
            notificationDao.deleteByIds(deletedIds.toList())
        }
        return deletedIds
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        val parsedList = documentList.mapNotNull { parseNotification(it) }
        val existingNotifications = if (parsedList.isNotEmpty()) {
            notificationDao.getByIds(parsedList.map { it.id }).associateBy { it.id }
        } else {
            emptyMap()
        }
        parsedList.forEach { parsed ->
            val existing = existingNotifications[parsed.id]
            if (existing?.needsSync == true) {
                parsed.needsSync = true
                parsed.isRead = existing.isRead
            }
        }
        notificationDao.upsertAll(parsedList)
    }
}

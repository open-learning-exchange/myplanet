package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.model.TaskNotificationResult
import org.ole.planet.myplanet.model.TeamNotification
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeProvider

private const val STORAGE_WARNING_AVAILABLE_PERCENT = 10

class NotificationsRepositoryImpl @Inject constructor(
    private val userRepository: Lazy<UserRepository>,
    private val teamsRepository: Lazy<TeamsNotificationsRepository>,
    private val timeProvider: TimeProvider,
    private val teamNotificationDao: TeamNotificationDao,
    private val notificationDao: NotificationDao,
    private val teamTaskDao: TeamTaskDao,
    private val voicesRepository: VoicesRepository
) : NotificationsRepository {
    override suspend fun refresh() = Unit

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
            } ?: AppNotification().apply {
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
            } ?: AppNotification().apply {
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

        val existingIds = notificationDao.getIdsByIds(notificationIds.toList())
        if (existingIds.isEmpty()) return emptySet()
        notificationDao.markAsRead(existingIds, Date())
        return existingIds.toSet()
    }

    override suspend fun markAllUnreadAsRead(userId: String?): Set<String> {
        val actualUserId = userId ?: return emptySet()
        val unreadIds = notificationDao.getUnreadIds(actualUserId).toSet()
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
                needsSync = it.needsSync,
                subType = it.subType
            )
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

        val teamIds = LinkedHashSet<String>()
        tasks.forEach { task -> task.teamId?.takeIf { it.isNotEmpty() }?.let { teamIds.add(it) } }
        if (teamIds.isNotEmpty()) {
            val teamMap = teamsRepository.get().getTeamNamesByIds(teamIds.toList())

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

        val teamIds = LinkedHashSet<String>()
        joinRequests.forEach { jr -> jr.teamId.takeIf { it.isNotEmpty() }?.let { teamIds.add(it) } }

        val teamMap = teamsRepository.get().getTeamNamesByIds(teamIds.toList())

        val intermediateList = ArrayList<Triple<String, String, String>>(joinRequests.size)
        joinRequests.forEach { jr ->
            val id = jr.id
            if (id.isNotEmpty()) {
                val tName = teamMap[jr.teamId] ?: "Unknown Team"
                intermediateList.add(Triple(id, jr.userId, tName))
            }
        }

        val map = mutableMapOf<String, Pair<String, String>>()
        val userIds = LinkedHashSet<String>()
        intermediateList.forEach { triple -> triple.second.takeIf { it.isNotEmpty() }?.let { userIds.add(it) } }
        val userMap = mutableMapOf<String, String>()
        if (userIds.isNotEmpty()) {
            val users = userRepository.get().getUsersByIds(userIds.toList())
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

    override suspend fun getTaskTeamNamesByTaskTitles(taskTitles: List<String>): Map<String, String> {
        if (taskTitles.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()

        val tasks = teamTaskDao.getByTitles(taskTitles)

        val teamIds = LinkedHashSet<String>()
        tasks.forEach { task -> task.teamId?.takeIf { it.isNotEmpty() }?.let { teamIds.add(it) } }
        if (teamIds.isNotEmpty()) {
            val teamMap = teamsRepository.get().getTeamNamesByIds(teamIds.toList())

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

    override suspend fun updateTeamNotification(teamId: String, count: Int) {
        val existing = teamNotificationDao.findByParentAndType(teamId, "chat")
        if (existing != null) {
            existing.lastCount = count
            teamNotificationDao.update(existing)
        } else {
            val notification = TeamNotification().apply {
                id = UUID.randomUUID().toString()
                parentId = teamId
                type = "chat"
                lastCount = count
            }
            teamNotificationDao.insert(notification)
        }
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
        val chatViewableIds = voicesRepository.getTeamChatViewableIds(teamIds)
        val chatCountsById = mutableMapOf<String, Long>()
        chatViewableIds.forEach { viewableId ->
            val currentCount = chatCountsById[viewableId] ?: 0
            chatCountsById[viewableId] = currentCount + 1
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

    override suspend fun getPendingSyncNotifications(): List<AppNotification> {
        return notificationDao.getPendingSyncNotifications()
    }

    override suspend fun markNotificationsSynced(syncResults: List<Pair<String, String?>>) {
        if (syncResults.isEmpty()) return
        notificationDao.markSynced(syncResults)
    }

    override fun resolveType(type: String, message: String, subType: String?): String {
        val lowerType = type.lowercase(Locale.ROOT)
        if (lowerType in NotificationsRepository.KNOWN_TYPES) return lowerType
        val lower = message.lowercase(Locale.ROOT)
        // Raw server type "team" covers every team-related event (message/request/added/rejected/removed) in
        // whatever language the server rendered the message in, so classify structurally first and only fall
        // back to English message-sniffing to pick a more specific sub-bucket when it's recognizable.
        if (lowerType == "team") {
            if (subType != null) return subType.lowercase(Locale.ROOT)
            return when {
                lower.contains("requested to join") || lower.contains("wants to join") ||
                    lower.contains("solicitado unirse") -> "join_request"
                lower.contains("posted a message on") || lower.contains("posted a new voice") ||
                    lower.contains("new voice in") || lower.contains("posted in") -> "chat"
                else -> "team_join"
            }
        }
        if (lowerType == "newtask") return "task"
        if (lowerType == "newresource") return "resource"
        return when {
            lower.contains("requested to join") || lower.contains("wants to join") -> "join_request"
            lower.contains("added you to") || lower.contains("you've been added") || lower.contains("you have been added") -> "team_join"
            lower.contains("replied to your") || lower.contains("replied on your") || lower.contains("new reply to") -> "voice_reply"
            lower.contains("posted a new voice") || lower.contains("new voice in") || lower.contains("posted in") -> "chat"
            lower.contains("is due") || lower.contains("due:") -> "task"
            lower.contains("storage") -> "storage"
            lower.contains("resource") -> "resource"
            else -> "notification"
        }
    }

    private fun parseNotification(doc: JsonObject): AppNotification? {
        val id = doc.get("_id")?.asString ?: return null
        val rawType = doc.get("type")?.asString ?: ""
        val message = doc.get("message")?.asString ?: ""
        val link = doc.get("link")?.asString
        return AppNotification().apply {
            this.id = id
            userId = doc.get("user")?.asString ?: ""
            this.message = message
            type = rawType
            subType = extractTeamSubtype(rawType, doc)
            relatedId = extractRelatedId(rawType, link, doc)
            this.link = link
            priority = doc.get("priority")?.asInt ?: 0
            rev = doc.get("_rev")?.asString
            isRead = doc.get("status")?.asString != "unread"
            createdAt = doc.get("time")?.let { Date(it.asLong) } ?: Date()
            isFromServer = true
        }
    }

    /**
     * Raw type "team" covers join requests, team-membership changes, and chat posts alike, and the
     * server renders `message` in the recipient's locale, so it can't be classified reliably by
     * sniffing English/Spanish phrases. `linkParams.activeTab == "applicantTab"` is a locale-independent
     * signal the server sends specifically for join-request notifications; use it when present.
     */
    private fun extractTeamSubtype(rawType: String, doc: JsonObject): String? {
        if (rawType != "team") return null
        val activeTab = doc.getAsJsonObject("linkParams")?.get("activeTab")?.asString
        return if (activeTab == "applicantTab") "join_request" else null
    }

    private fun extractRelatedId(rawType: String, link: String?, doc: JsonObject): String? {
        return when (rawType) {
            "team" -> doc.get("item")?.asString
            "replyMessage" -> doc.get("replyTo")?.asString
            "newTask" -> extractIdFromLink(link)
            else -> null
        }
    }

    private fun extractIdFromLink(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val segments = link.trim('/').split('/')
        val viewIndex = segments.indexOf("view")
        return if (viewIndex in 0 until segments.lastIndex) segments[viewIndex + 1] else null
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
        val deletedIds = notificationDao.getIdsByIds(ids.toList())
        if (deletedIds.isNotEmpty()) {
            notificationDao.deleteByIds(deletedIds)
        }
        return deletedIds.toSet()
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

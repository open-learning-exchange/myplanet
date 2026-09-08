package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.model.TaskNotificationResult
import org.ole.planet.myplanet.model.TeamNotificationInfo

interface NotificationsRepository {
    suspend fun refresh()
    suspend fun markNotificationAsRead(notificationId: String, userId: String?)
    suspend fun getNotifications(userId: String, filter: String, isAdmin: Boolean = false): List<NotificationPayload>
    suspend fun getUnreadCount(userId: String?, isAdmin: Boolean = false): Int
    suspend fun updateResourceNotification(userId: String?, resourceCount: Int)
    suspend fun updateStorageNotification(userId: String?, availablePercent: Int)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
    suspend fun getTaskDetails(relatedId: String?): TaskNotificationResult?
    suspend fun getJoinRequestTeamId(relatedId: String?): String?
    suspend fun getJoinRequestDetails(relatedId: String?): Pair<String, String>
    suspend fun getTaskTeamNamesByTaskIds(taskIds: List<String>): Map<String, String>
    suspend fun getJoinRequestDetailsBatch(relatedIds: List<String>): Map<String, Pair<String, String>>
    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo>
    suspend fun updateTeamNotification(teamId: String, count: Int)
    suspend fun getTaskTeamNamesByTaskTitles(taskTitles: List<String>): Map<String, String>
    suspend fun getPendingSyncNotifications(): List<AppNotification>
    suspend fun markNotificationsSynced(syncResults: List<Pair<String, String?>>)
    suspend fun bulkInsertFromSync(jsonArray: JsonArray)
    suspend fun insert(doc: JsonObject)
    suspend fun deleteNotifications(ids: Set<String>): Set<String>
    fun resolveType(type: String, message: String, subType: String?): String

    companion object {
        val KNOWN_TYPES = setOf("join_request", "team_join", "task", "chat", "voice_reply", "resource", "storage")
    }
}

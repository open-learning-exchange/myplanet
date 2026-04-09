package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.model.TeamNotificationInfo

interface NotificationsRepository {
    suspend fun refresh()
    suspend fun markNotificationAsRead(notificationId: String, userId: String?)
    suspend fun getNotifications(userId: String, filter: String, isAdmin: Boolean = false): List<NotificationPayload>
    suspend fun getUnreadCount(userId: String?, isAdmin: Boolean = false): Int
    suspend fun updateResourceNotification(userId: String?, resourceCount: Int)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
    suspend fun getSurveyId(relatedId: String?): String?
    suspend fun getTaskDetails(relatedId: String?): org.ole.planet.myplanet.model.TaskNotificationResult?
    suspend fun getJoinRequestTeamId(relatedId: String?): String?
    suspend fun getJoinRequestDetails(relatedId: String?): Pair<String, String>
    suspend fun getTaskTeamNamesByTaskIds(taskIds: List<String>): Map<String, String>
    suspend fun getJoinRequestDetailsBatch(relatedIds: List<String>): Map<String, Pair<String, String>>
    suspend fun getTaskTeamName(taskTitle: String): String?
    suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo
    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo>
    suspend fun getPendingSyncNotifications(): List<org.ole.planet.myplanet.model.RealmNotification>
    suspend fun markNotificationsSynced(syncResults: List<Pair<String, String?>>)
}

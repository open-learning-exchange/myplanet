package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.TeamNotificationInfo

interface NotificationsRepository {
    suspend fun refresh()
    suspend fun markNotificationAsRead(notificationId: String, userId: String?)
    suspend fun getNotifications(userId: String, filter: String): List<org.ole.planet.myplanet.model.RealmNotification>
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?, resourceCount: Int)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    )
    suspend fun getSurveyId(relatedId: String?): String?
    suspend fun getTaskDetails(relatedId: String?): Triple<String, String?, String?>?
    suspend fun getJoinRequestTeamId(relatedId: String?): String?
    fun getJoinRequestDetails(relatedId: String?): Pair<String, String>
    fun getTaskTeamName(taskTitle: String): String?
    suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo
    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo>
}

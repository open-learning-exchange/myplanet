package org.ole.planet.myplanet.model

data class NotificationsEnrichment(
    val payloads: List<NotificationPayload>,
    val taskTeamNames: Map<String, String>,
    val joinRequestDetails: Map<String, Pair<String, String>>,
    val parsedTaskDates: Map<String, Pair<String, String>?>,
    val unreadCount: Int
)

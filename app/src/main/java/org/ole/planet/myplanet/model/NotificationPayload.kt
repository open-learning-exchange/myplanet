package org.ole.planet.myplanet.model

data class NotificationPayload(
    val id: String,
    val type: String,
    val message: String,
    val isRead: Boolean,
    val relatedId: String?
)

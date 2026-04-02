package org.ole.planet.myplanet.model

data class NotificationPayload(
    val id: String,
    val userId: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Long,
    val type: String,
    val relatedId: String?,
    val title: String?,
    val link: String?,
    val priority: Int,
    val isFromServer: Boolean,
    val rev: String?,
    val needsSync: Boolean
)

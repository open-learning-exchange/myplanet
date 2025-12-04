package org.ole.planet.myplanet.model.dto

import java.util.Date

data class NotificationItem(
    val id: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Date,
    val type: String?,
    val relatedId: String?,
    val teamId: String?,
    val teamName: String?,
    val teamType: String?
)

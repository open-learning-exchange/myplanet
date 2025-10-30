package org.ole.planet.myplanet.ui.dashboard.notification

import java.util.Date

data class NotificationDisplayItem(
    val id: String,
    val userId: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Date,
    val type: String,
    val relatedId: String?,
    val title: String?,
    val displayHtml: String,
)

package org.ole.planet.myplanet.model

import java.util.Date

data class Notification(
    val id: String,
    val userId: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Date,
    val type: String,
    val relatedId: String?,
    val title: String?
)

fun RealmNotification.toNotification(): Notification {
    return Notification(
        id = this.id,
        userId = this.userId,
        message = this.message,
        isRead = this.isRead,
        createdAt = this.createdAt,
        type = this.type,
        relatedId = this.relatedId,
        title = this.title
    )
}

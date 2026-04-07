package org.ole.planet.myplanet.model

data class Notification(
    val id: String,
    val formattedText: CharSequence,
    val isRead: Boolean,
    val type: String,
    val relatedId: String?,
)

package org.ole.planet.myplanet.repository

enum class NotificationFilter {
    ALL,
    READ,
    UNREAD;

    companion object {
        fun fromSelection(selection: String): NotificationFilter {
            return when (selection.lowercase()) {
                "read" -> READ
                "unread" -> UNREAD
                else -> ALL
            }
        }
    }
}

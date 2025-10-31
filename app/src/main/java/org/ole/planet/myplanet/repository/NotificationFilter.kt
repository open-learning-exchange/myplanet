package org.ole.planet.myplanet.repository

enum class NotificationFilter {
    ALL,
    READ,
    UNREAD,
    ;

    companion object {
        fun fromValue(value: String): NotificationFilter {
            return values().firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: ALL
        }
    }
}

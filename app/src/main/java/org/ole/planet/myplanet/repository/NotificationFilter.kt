package org.ole.planet.myplanet.repository

enum class NotificationFilter(val key: String) {
    ALL("all"),
    READ("read"),
    UNREAD("unread");

    companion object {
        fun fromKey(key: String?): NotificationFilter {
            return values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ALL
        }
    }
}

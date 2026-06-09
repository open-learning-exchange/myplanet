package org.ole.planet.myplanet.model

sealed class NotificationListItem {
    data class Header(
        val type: String,
        val label: String,
        val unreadCount: Int,
        val isExpanded: Boolean = true
    ) : NotificationListItem()

    data class Item(
        val notification: Notification,
        val isSelected: Boolean = false,
        val isSelectionMode: Boolean = false
    ) : NotificationListItem()
}

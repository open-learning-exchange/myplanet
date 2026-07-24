package org.ole.planet.myplanet.model

import android.text.Html

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
    ) : NotificationListItem() {
        val parsedText: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
            Html.fromHtml(notification.formattedText.toString(), Html.FROM_HTML_MODE_LEGACY)
        }
    }
}

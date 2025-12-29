package org.ole.planet.myplanet.ui.dashboard.notifications

interface NotificationListener {
    fun onNotificationCountUpdated(unreadCount: Int)
}

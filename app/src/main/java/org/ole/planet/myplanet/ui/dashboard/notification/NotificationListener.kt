package org.ole.planet.myplanet.ui.dashboard.notification

interface NotificationListener {
    fun onNotificationCountUpdated(unreadCount: Int)
}
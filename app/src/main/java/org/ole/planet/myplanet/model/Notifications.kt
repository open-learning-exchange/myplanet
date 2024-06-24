package org.ole.planet.myplanet.model

data class Notifications(var icon: Int, var text: String) {

    constructor(icon: Int, text: String, timestamp: String, isRead: Boolean) : this(icon, text) {
        this.timestamp = timestamp
        this.isRead = isRead
    }

    var timestamp: String = ""
    var isRead: Boolean = false
}

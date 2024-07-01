package org.ole.planet.myplanet.model

data class Notifications(var id: Int, var icon: Int, var text: String) {

    constructor(id: Int, icon: Int, text: String, timestamp: String, isRead: Boolean) : this(id, icon, text) {
        this.timestamp = timestamp
        this.isRead = isRead
    }

    constructor(icon: Int, text: String) : this(0, icon, text){

    }

    constructor(id: String?, icon: Int, text: String) : this(0,0,text)

    var timestamp: String = ""
    var isRead: Boolean = false
}

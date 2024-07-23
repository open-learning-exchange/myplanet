package org.ole.planet.myplanet.model

data class Notifications(
val _id: String,
val _rev: String,
val addedBy: String,
val title: String,
val createdDate: Long,
val source: String,
val read: String
)
//{

//    constructor(id: Int, icon: Int, text: String, timestamp: String, isRead: Boolean) : this(id, icon, text) {
//        this.timestamp = timestamp
//        this.isRead = isRead
//    }
//
//    constructor(icon: Int, text: String) : this(0, icon, text){
//
//    }
//
//    constructor(id: String?, icon: Int, text: String) : this(0,0,text)
//
//    var timestamp: String = ""
//    var isRead: Boolean = false
//}

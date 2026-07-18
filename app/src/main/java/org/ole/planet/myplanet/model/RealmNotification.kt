package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "notifications", indices = [Index("userId"), Index("type")])
class RealmNotification {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String = ""
    var message: String = ""
    var isRead: Boolean = false
    var createdAt: Date = Date()
    var type: String = ""
    var relatedId: String? = null
    var title: String? = null
    var link: String? = null
    var priority: Int = 0
    var isFromServer: Boolean = false
    var rev: String? = null
    var needsSync: Boolean = false
}

package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Entity(tableName = "notifications", indices = [Index("userId"), Index("type")])
class AppNotification {
    @PrimaryKey
    var id: String = Uuid.random().toString()
    var userId: String = ""
    var message: String = ""
    var isRead: Boolean = false
    var createdAt: Long = Clock.System.now().toEpochMilliseconds()
    var type: String = ""
    var subType: String? = null
    var relatedId: String? = null
    var title: String? = null
    var link: String? = null
    var priority: Int = 0
    var isFromServer: Boolean = false
    var rev: String? = null
    var needsSync: Boolean = false
}

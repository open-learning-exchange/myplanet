package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room replacement for the former Realm `RealmTeamNotification` model. Local-only (not synced or
 * uploaded); persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.TeamNotificationDao].
 */
@Entity(tableName = "team_notification", indices = [Index("type")])
open class RealmTeamNotification {
    @PrimaryKey
    var id: String = ""
    var type: String? = null
    var parentId: String? = null
    var lastCount: Int = 0
}

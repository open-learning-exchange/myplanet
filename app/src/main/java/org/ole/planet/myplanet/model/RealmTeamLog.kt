package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "team_log",
    indices = [Index("teamId"), Index("type"), Index("_id"), Index("_rev")]
)
open class RealmTeamLog {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var teamId: String? = null
    var user: String? = null
    var type: String? = null
    var teamType: String? = null
    var createdOn: String? = null
    var parentCode: String? = null
    var time: Long? = null
    var uploaded = false
}

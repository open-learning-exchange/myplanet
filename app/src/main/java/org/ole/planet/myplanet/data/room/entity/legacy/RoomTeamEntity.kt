package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for teams, memberships, team resources, and reports formerly stored as RealmMyTeam. */
@Entity(tableName = "teams", indices = [Index("_id"), Index("teamId"), Index("userId"), Index("type")])
data class RoomTeamEntity(
    @PrimaryKey @JvmField val id: String,
    @JvmField val _id: String? = null,
    @JvmField val _rev: String? = null,
    val teamId: String? = null,
    val userId: String? = null,
    val name: String? = null,
    val type: String? = null,
    val description: String? = null,
    val status: String? = null,
    val docType: String? = null,
    val courses: List<String>? = null,
    val createdDate: Long = 0,
    val updatedDate: Long = 0,
    val isLeader: Boolean = false,
    val isUpdated: Boolean = false,
)

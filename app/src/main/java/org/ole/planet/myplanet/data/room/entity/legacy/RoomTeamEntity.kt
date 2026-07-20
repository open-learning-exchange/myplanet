package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for teams, memberships, team resources, and reports formerly stored as RealmMyTeam. */
@Entity(tableName = "teams", indices = [Index("_id"), Index("teamId"), Index("userId"), Index("type"), Index("docType")])
data class RoomTeamEntity(
    @PrimaryKey @JvmField val id: String,
    @JvmField val _id: String? = null,
    @JvmField val _rev: String? = null,
    val teamId: String? = null,
    val userId: String? = null,
    val name: String? = null,
    val type: String? = null,
    val description: String? = null,
    val requests: String? = null,
    val sourcePlanet: String? = null,
    val limit: Int = 0,
    val status: String? = null,
    val teamType: String? = null,
    val teamPlanetCode: String? = null,
    val userPlanetCode: String? = null,
    val parentCode: String? = null,
    val docType: String? = null,
    val title: String? = null,
    val route: String? = null,
    val services: String? = null,
    val createdBy: String? = null,
    val rules: String? = null,
    val courses: List<String>? = null,
    val resourceId: String? = null,
    val createdDate: Long = 0,
    val updatedDate: Long = 0,
    val isLeader: Boolean = false,
    val isUpdated: Boolean = false,
    val isPublic: Boolean = false,
    val isDeletePending: Boolean = false,
    val amount: Int = 0,
    val date: Long = 0,
    val beginningBalance: Int = 0,
    val sales: Int = 0,
    val otherIncome: Int = 0,
    val wages: Int = 0,
    val otherExpenses: Int = 0,
    val startDate: Long = 0,
    val endDate: Long = 0,
    val imageName: String? = null,
)

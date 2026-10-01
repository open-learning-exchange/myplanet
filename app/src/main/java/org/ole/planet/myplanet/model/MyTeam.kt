package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "teams", indices = [Index("_id"), Index("teamId"), Index("userId"), Index("type"), Index("docType")])
open class MyTeam(
    @PrimaryKey @JvmField var _id: String = "",
    @JvmField var _rev: String? = null,
    var courses: List<String>? = null,
    var teamId: String? = null,
    var name: String? = null,
    var userId: String? = null,
    var description: String? = null,
    var requests: String? = null,
    var sourcePlanet: String? = null,
    var limit: Int = 0,
    var createdDate: Long = 0,
    var resourceId: String? = null,
    var status: String? = null,
    var teamType: String? = null,
    var teamPlanetCode: String? = null,
    var userPlanetCode: String? = null,
    var parentCode: String? = null,
    var docType: String? = null,
    var title: String? = null,
    var route: String? = null,
    var services: String? = null,
    var createdBy: String? = null,
    var rules: String? = null,
    var isLeader: Boolean = false,
    var type: String? = null,
    var amount: Int = 0,
    var date: Long = 0,
    var isPublic: Boolean = false,
    @ColumnInfo(name = "isUpdated") var updated: Boolean = false,
    var isDeletePending: Boolean = false,
    var beginningBalance: Int = 0,
    var sales: Int = 0,
    var otherIncome: Int = 0,
    var wages: Int = 0,
    var otherExpenses: Int = 0,
    var startDate: Long = 0,
    var endDate: Long = 0,
    var updatedDate: Long = 0,
    var imageName: String? = null
) {
    @get:androidx.room.Ignore
    var id: String
        get() = _id
        set(value) { _id = value }

    companion object
}

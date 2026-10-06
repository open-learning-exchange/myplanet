package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements", indices = [androidx.room.Index("isUpdated")])
class Achievement {
    var achievements: List<String>? = null
    var references: List<String>? = null
    var links: List<String>? = null
    var otherInfo: List<String>? = null
    var purpose: String? = null
    var achievementsHeader: String? = null
    var sendToNation: String? = null
    var _rev: String? = null
    @PrimaryKey
    var _id: String = ""
    var goals: String? = null
    var dateSortOrder: String? = null
    var createdOn: String? = null
    var username: String? = null
    var parentCode: String? = null
    var isUpdated: Boolean = false
    var resumeFileName: String? = null

    companion object
}

package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_activity",
    indices = [Index("_rev"), Index("type"), Index("user")]
)
open class SearchActivity(
    @PrimaryKey
    @JvmField
    var id: String = "",
    @JvmField
    var _id: String = "",
    var _rev: String = "",
    var text: String = "",
    var type: String = "",
    var time: Long = 0,
    var user: String = "",
    var filter: String = "",
    var createdOn: String = "",
    var parentCode: String = ""
)

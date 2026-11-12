package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "resource_activity",
    indices = [Index("_rev"), Index("type"), Index("user")]
)
open class ResourceActivity {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var resourceId: String? = null
    var parentCode: String? = null
    var type: String? = null
    var user: String? = null
    var androidId: String? = null
}

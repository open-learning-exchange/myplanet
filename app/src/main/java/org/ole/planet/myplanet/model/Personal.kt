package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "my_personal", indices = [Index("userId")])
open class Personal {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var isUploaded = false
    var title: String? = null
    var description: String? = null
    var date: Long = 0
    var userId: String? = null
    var userName: String? = null
    var path: String? = null
}

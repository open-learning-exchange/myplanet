package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "news_log",
    indices = [Index("_id"), Index("_rev")]
)
open class NewsLog {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var type: String? = null
    var time: Long? = null
    var userId: String? = null
    var androidId: String? = null

    companion object
}

package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room replacement for the former `Rating` model. Uploaded (Room upload path) and
 * synced; persistence goes through [org.ole.planet.myplanet.data.room.dao.RatingDao].
 */
@Entity(
    tableName = "rating",
    indices = [Index("userId"), Index("isUpdated"), Index("item"), Index("type")]
)
open class Rating {
    // @JvmField on id/_id so Room does not see ambiguous getId/get_id accessors.
    @PrimaryKey
    @JvmField
    var id: String = ""
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var userId: String? = null
    var isUpdated = false
    var rate = 0
    @JvmField
    var _id: String? = null
    var item: String? = null
    var comment: String? = null
    var parentCode: String? = null
    var planetCode: String? = null
    var type: String? = null
    var user: String? = null

    companion object
}

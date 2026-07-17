package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray

/**
 * Room replacement for the former Realm `RealmTag` model. Synced (read-only from the server).
 * `attachedTo` (formerly `RealmList<String>`) is a plain `List<String>` stored as JSON via the
 * shared [org.ole.planet.myplanet.data.room.Converters]. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.TagDao].
 */
@Entity(tableName = "tag", indices = [Index("name"), Index("tagId"), Index("db")])
open class RealmTag {
    // @JvmField on id/_id so Room does not see ambiguous getId/get_id accessors.
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    var linkId: String? = null
    var tagId: String? = null
    var attachedTo: List<String>? = null
    var docType: String? = null
    var db: String? = null
    var isAttached = false

    override fun toString(): String {
        return name.orEmpty()
    }

    fun toTag(): Tag {
        return Tag(
            id = this.id,
            name = this.name
        )
    }

    companion object {
        fun getTagsArray(list: List<RealmTag>): JsonArray {
            val array = JsonArray()
            for (t in list) {
                array.add(t._id)
            }
            return array
        }
    }
}

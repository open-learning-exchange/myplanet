package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray

/**
 * Room replacement for the former `TagEntity` model. Synced (read-only from the server).
 * `attachedTo` (formerly `RealmList<String>`) is a plain `List<String>` stored as JSON via the
 * shared [org.ole.planet.myplanet.data.room.Converters]. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.TagDao].
 */
@Entity(tableName = "tag", indices = [Index("name"), Index("tagId"), Index("db")])
open class TagEntity {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagEntity) return false
        return if (id.isNotEmpty() && other.id.isNotEmpty()) {
            id == other.id
        } else {
            !name.isNullOrEmpty() && name == other.name
        }
    }

    override fun hashCode(): Int {
        return if (id.isNotEmpty()) id.hashCode() else name?.hashCode() ?: 0
    }

    companion object {
        fun getTagsArray(list: List<TagEntity>): JsonArray {
            val array = JsonArray()
            for (t in list) {
                array.add(t._id)
            }
            return array
        }
    }
}

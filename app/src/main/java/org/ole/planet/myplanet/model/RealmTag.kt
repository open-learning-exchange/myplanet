package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmTag : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    var linkId: String? = null
    var tagId: String? = null
    var attachedTo: RealmList<String>? = null
    var docType: String? = null
    var db: String? = null
    var isAttached = false
    private fun setAttachedTo(attachedTo: JsonArray) {
        this.attachedTo = RealmList()
        for (i in 0 until attachedTo.size()) {
            this.attachedTo?.add(JsonUtils.getString(attachedTo, i))
        }
        isAttached = (this.attachedTo?.size ?: 0) > 0
    }

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
        @JvmStatic
        fun getTagsArray(list: List<RealmTag>): JsonArray {
            val array = JsonArray()
            for (t in list) {
                array.add(t._id)
            }
            return array
        }
    }
}

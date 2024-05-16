package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils

open class RealmTag : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var name: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var linkId: String? = null
    @JvmField
    var tagId: String? = null
    @JvmField
    var attachedTo: RealmList<String>? = null
    @JvmField
    var docType: String? = null
    @JvmField
    var db: String? = null
    @JvmField
    var isAttached = false
    private fun setAttachedTo(attachedTo: JsonArray) {
        this.attachedTo = RealmList()
        for (i in 0 until attachedTo.size()) {
            this.attachedTo?.add(JsonUtils.getString(attachedTo, i))
        }
        isAttached = (this.attachedTo?.size ?: 0) > 0
    }

    override fun toString(): String {
        return name!!
    }

    fun setAttachedTo(attachedTo: RealmList<String>?) {
        this.attachedTo = attachedTo
    }

    companion object {
        @JvmStatic
        fun getListAsMap(list: List<RealmTag>): HashMap<String?, RealmTag> {
            val map = HashMap<String?, RealmTag>()
            for (r in list) {
                map[r._id] = r
            }
            return map
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var tag = mRealm.where(RealmTag::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (tag == null) {
                tag = mRealm.createObject(RealmTag::class.java, JsonUtils.getString("_id", act))
            }
            if (tag != null) {
                tag._rev = JsonUtils.getString("_rev", act)
                tag._id = JsonUtils.getString("_id", act)
                tag.name = JsonUtils.getString("name", act)
                tag.db = JsonUtils.getString("db", act)
                tag.docType = JsonUtils.getString("docType", act)
                tag.tagId = JsonUtils.getString("tagId", act)
                tag.linkId = JsonUtils.getString("linkId", act)
                val el = act["attachedTo"]
                if (el != null && el.isJsonArray) {
                    tag.setAttachedTo(JsonUtils.getJsonArray("attachedTo", act))
                } else {
                    tag.attachedTo?.add(JsonUtils.getString("attachedTo", act))
                }
                tag.isAttached = (tag.attachedTo?.size ?: 0) > 0
            }
            mRealm.commitTransaction()
        }

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
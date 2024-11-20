package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
        return name!!
    }

    companion object {
        private val tagDataList: MutableList<Array<String>> = mutableListOf()

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

            val csvRow = arrayOf(
                JsonUtils.getString("_id", act),
                JsonUtils.getString("_rev", act),
                JsonUtils.getString("name", act),
                JsonUtils.getString("db", act),
                JsonUtils.getString("docType", act),
                JsonUtils.getString("tagId", act),
                JsonUtils.getString("linkId", act),
                JsonUtils.getString("attachedTo", act)
            )

            tagDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("_id", "_rev", "name", "db", "docType", "tagId", "linkId", "attachedTo"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun tagWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/tags.csv", tagDataList)
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
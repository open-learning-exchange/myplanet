package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmTag : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    var linkId: String? = null
    var tagId: String? = null
    var attachedTo: RealmList<String> = realmListOf()
    var docType: String? = null
    var db: String? = null
    var isAttached: Boolean = false

    fun setAttachedTo(attachedTo: JsonArray) {
        this.attachedTo.clear()
        for (i in 0 until attachedTo.size()) {
            this.attachedTo.add(JsonUtils.getString(attachedTo, i))
        }
        isAttached = this.attachedTo.isNotEmpty()
    }

    override fun toString(): String {
        return name ?: super.toString()
    }

    companion object {
        private val tagDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: io.realm.kotlin.Realm, act: JsonObject?) {
            if (act == null) return

            realm.write {
                val id = JsonUtils.getString("_id", act)

                val existingTag = query(RealmTag::class, "id == $0", id).first().find()

                val tag = existingTag ?: copyToRealm(RealmTag().apply {
                    this._id = id
                })

                tag.apply {
                    _rev = JsonUtils.getString("_rev", act)
                    name = JsonUtils.getString("name", act)
                    db = JsonUtils.getString("db", act)
                    docType = JsonUtils.getString("docType", act)
                    tagId = JsonUtils.getString("tagId", act)
                    linkId = JsonUtils.getString("linkId", act)

                    val el = act["attachedTo"]
                    if (el != null && el.isJsonArray) {
                        setAttachedTo(JsonUtils.getJsonArray("attachedTo", act))
                    } else {
                        attachedTo.add(JsonUtils.getString("attachedTo", act))
                    }
                    isAttached = attachedTo.isNotEmpty()
                }
            }

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
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("_id", "_rev", "name", "db", "docType", "tagId", "linkId", "attachedTo"))
                    data.forEach { writer.writeNext(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun tagWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/tags.csv", tagDataList)
        }

        fun getTagsArray(list: List<RealmTag>): JsonArray {
            return JsonArray().apply {
                list.forEach { add(it._id) }
            }
        }
    }
}
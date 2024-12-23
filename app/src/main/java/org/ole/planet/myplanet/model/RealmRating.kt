package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmRating : RealmObject {
    @PrimaryKey
    var id: String = ""
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var userId: String? = null
    var isUpdated: Boolean = false
    var rate: Int = 0
    var _id: String? = null
    var item: String? = null
    var comment: String? = null
    var parentCode: String? = null
    var planetCode: String? = null
    var type: String? = null
    var user: String? = null

    companion object {
        private val ratingDataList: MutableList<Array<String>> = mutableListOf()

        fun getRatings(realm: Realm, type: String?, userId: String?): Map<String?, JsonObject> {
            val results = realm.query<RealmRating>("type == $0", type).find()
            val map = mutableMapOf<String?, JsonObject>()

            for (rating in results) {
                val ratingObject = getRatingsById(realm, rating.type, rating.item, userId)
                if (ratingObject != null) map[rating.item] = ratingObject
            }

            return map
        }

        fun getRatingsById(realm: Realm, type: String?, id: String?, userId: String?): JsonObject? {
            val results = realm.query<RealmRating>("type == $0 && itemId == $1", type, id).find()
            if (results.isEmpty()) return null

            val totalRating = results.sumOf { it.rate }
            val avgRating = totalRating.toFloat() / results.size

            val userRating = results.firstOrNull { it.userId == userId }
            val jsonObject = JsonObject()
            jsonObject.addProperty("ratingByUser", userRating?.rate ?: 0)
            jsonObject.addProperty("averageRating", avgRating)
            jsonObject.addProperty("total", results.size)

            return jsonObject
        }

        fun serializeRating(realmRating: RealmRating): JsonObject {
            val jsonObject = JsonObject()
            jsonObject.addProperty("_id", realmRating._id)
            jsonObject.addProperty("_rev", realmRating._rev)
            jsonObject.add("user", Gson().fromJson(realmRating.user, JsonObject::class.java))
            jsonObject.addProperty("item", realmRating.item)
            jsonObject.addProperty("type", realmRating.type)
            jsonObject.addProperty("title", realmRating.title)
            jsonObject.addProperty("time", realmRating.time)
            jsonObject.addProperty("comment", realmRating.comment)
            jsonObject.addProperty("rate", realmRating.rate)
            jsonObject.addProperty("createdOn", realmRating.createdOn)
            jsonObject.addProperty("parentCode", realmRating.parentCode)
            jsonObject.addProperty("planetCode", realmRating.planetCode)
            return jsonObject
        }


        fun insert(realm: Realm, act: JsonObject) {
            realm.writeBlocking {
                val rating = query<RealmRating>("id == $0", act.get("_id").asString).first().find()
                    ?: RealmRating().apply {
                        _id = act.get("_id").asString
                    }.also { copyToRealm(it) }

                rating.apply {
                    _rev = act.get("_rev").asString
                    time = act.get("time").asLong
                    title = act.get("title").asString
                    type = act.get("type").asString
                    item = act.get("item").asString
                    rate = act.get("rate").asInt
                    isUpdated = false
                    comment = act.get("comment").asString
                    user = Gson().toJson(act.getAsJsonObject("user"))
                    userId = act.getAsJsonObject("user").get("_id").asString
                    parentCode = act.get("parentCode").asString
                    planetCode = act.get("planetCode").asString
                    createdOn = act.get("createdOn").asString
                }

                ratingDataList.add(
                    arrayOf(
                        act.get("_id").asString,
                        act.get("_rev").asString,
                        act.get("user").toString(),
                        act.get("item").asString,
                        act.get("type").asString,
                        act.get("title").asString,
                        act.get("time").asString,
                        act.get("comment").asString,
                        act.get("rate").asString,
                        act.get("createdOn").asString,
                        act.get("parentCode").asString,
                        act.get("planetCode").asString
                    )
                )
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(
                    arrayOf(
                        "_id", "_rev", "user", "item", "type", "title",
                        "time", "comment", "rate", "createdOn", "parentCode", "planetCode"
                    )
                )
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun ratingWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/rating.csv", ratingDataList)
        }
    }
}
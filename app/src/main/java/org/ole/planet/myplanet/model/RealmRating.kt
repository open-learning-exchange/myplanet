package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

open class RealmRating : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var userId: String? = null
    var isUpdated = false
    var rate = 0
    var _id: String? = null
    var item: String? = null
    var comment: String? = null
    var parentCode: String? = null
    var planetCode: String? = null
    var type: String? = null
    var user: String? = null

    companion object {
        private val ratingDataList: MutableList<Array<String>> = mutableListOf()
        @JvmStatic
        fun getRatings(mRealm: Realm, type: String?, userId: String?): HashMap<String?, JsonObject> {
            val r = mRealm.where(RealmRating::class.java).equalTo("type", type).findAll()
            val map = HashMap<String?, JsonObject>()
            for (rating in r) {
                val `object` = getRatingsById(mRealm, rating.type, rating.item, userId)
                if (`object` != null) map[rating.item] = `object`
            }
            return map
        }

        @JvmStatic
        fun getRatingsById(mRealm: Realm, type: String?, id: String?, userid: String?): JsonObject? {
            val r = mRealm.where(RealmRating::class.java).equalTo("type", type).equalTo("item", id).findAll()
            if (r.size == 0) {
                return null
            }
            val `object` = JsonObject()
            var totalRating = 0
            for (rating in r) {
                totalRating += rating.rate
            }
            val ratingObject = mRealm.where(RealmRating::class.java).equalTo("type", type)
                .equalTo("userId", userid).equalTo("item", id).findFirst()
            if (ratingObject != null) {
                `object`.addProperty("ratingByUser", ratingObject.rate)
            }
            `object`.addProperty("averageRating", totalRating.toFloat() / r.size)
            `object`.addProperty("total", r.size)
            return `object`
        }

        @JvmStatic
        fun serializeRating(realmRating: RealmRating): JsonObject {
            val ob = JsonObject()
            if (realmRating._id != null) ob.addProperty("_id", realmRating._id)
            if (realmRating._rev != null) ob.addProperty("_rev", realmRating._rev)
            ob.add("user", Gson().fromJson(realmRating.user, JsonObject::class.java))
            ob.addProperty("item", realmRating.item)
            ob.addProperty("type", realmRating.type)
            ob.addProperty("title", realmRating.title)
            ob.addProperty("time", realmRating.time)
            ob.addProperty("comment", realmRating.comment)
            ob.addProperty("rate", realmRating.rate)
            ob.addProperty("createdOn", realmRating.createdOn)
            ob.addProperty("parentCode", realmRating.parentCode)
            ob.addProperty("planetCode", realmRating.planetCode)
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            return ob
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var rating = mRealm.where(RealmRating::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (rating == null) {
                rating = mRealm.createObject(RealmRating::class.java, JsonUtils.getString("_id", act))
            }
            if (rating != null) {
                rating._rev = JsonUtils.getString("_rev", act)
                rating._id = JsonUtils.getString("_id", act)
                rating.time = JsonUtils.getLong("time", act)
                rating.title = JsonUtils.getString("title", act)
                rating.type = JsonUtils.getString("type", act)
                rating.item = JsonUtils.getString("item", act)
                rating.rate = JsonUtils.getInt("rate", act)
                rating.isUpdated = false
                rating.comment = JsonUtils.getString("comment", act)
                rating.user = Gson().toJson(JsonUtils.getJsonObject("user", act))
                rating.userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", act))
                rating.parentCode = JsonUtils.getString("parentCode", act)
                rating.parentCode = JsonUtils.getString("planetCode", act)
                rating.createdOn = JsonUtils.getString("createdOn", act)
            }
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", act),
                JsonUtils.getString("_rev", act),
                JsonUtils.getString("user", act),
                JsonUtils.getString("item", act),
                JsonUtils.getString("type", act),
                JsonUtils.getString("title", act),
                JsonUtils.getLong("time", act).toString(),
                JsonUtils.getString("comment", act),
                JsonUtils.getInt("rate", act).toString(),
                JsonUtils.getString("createdOn", act),
                JsonUtils.getString("parentCode", act),
                JsonUtils.getString("planetCode", act)
            )

            ratingDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("_id", "_rev", "user", "item", "type", "title", "time", "comment", "rate", "createdOn", "parentCode", "planetCode"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun ratingWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/ratings.csv", ratingDataList)
        }
    }
}

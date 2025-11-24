package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils

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
        @JvmStatic
        fun getRatings(mRealm: Realm, type: String?, userId: String?): HashMap<String?, JsonObject> {
            val ratings = mRealm.where(RealmRating::class.java).equalTo("type", type).findAll()
            val aggregated = aggregateRatings(ratings, userId)
            val map = HashMap<String?, JsonObject>()
            for ((item, aggregation) in aggregated) {
                map[item] = aggregation.toJson()
            }
            return map
        }

        @JvmStatic
        fun getRatingsById(mRealm: Realm, type: String?, id: String?, userid: String?): JsonObject? {
            val ratings = mRealm.where(RealmRating::class.java)
                .equalTo("type", type)
                .equalTo("item", id)
                .findAll()
            val aggregated = aggregateRatings(ratings, userid)[id]
            return aggregated?.toJson()
        }

        private fun aggregateRatings(
            ratings: Iterable<RealmRating>,
            userId: String?
        ): Map<String?, RatingAggregation> {
            val aggregationMap = LinkedHashMap<String?, RatingAggregation>()
            for (rating in ratings) {
                val item = rating.item
                val aggregation = aggregationMap.getOrPut(item) { RatingAggregation() }
                aggregation.totalRating += rating.rate
                aggregation.totalCount += 1
                if (userId != null && userId == rating.userId) {
                    aggregation.ratingByUser = rating.rate
                }
            }
            return aggregationMap
        }

        private data class RatingAggregation(
            var totalRating: Int = 0,
            var totalCount: Int = 0,
            var ratingByUser: Int? = null
        ) {
            fun toJson(): JsonObject {
                val `object` = JsonObject()
                if (ratingByUser != null) {
                    `object`.addProperty("ratingByUser", ratingByUser)
                }
                if (totalCount > 0) {
                    `object`.addProperty("averageRating", totalRating.toFloat() / totalCount)
                    `object`.addProperty("total", totalCount)
                }
                return `object`
            }
        }

        @JvmStatic
        fun serializeRating(realmRating: RealmRating): JsonObject {
            val ob = JsonObject()
            if (realmRating._id != null) ob.addProperty("_id", realmRating._id)
            if (realmRating._rev != null) ob.addProperty("_rev", realmRating._rev)
            ob.add("user", GsonUtils.gson.fromJson(realmRating.user, JsonObject::class.java))
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
                rating.user = GsonUtils.gson.toJson(JsonUtils.getJsonObject("user", act))
                rating.userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", act))
                rating.parentCode = JsonUtils.getString("parentCode", act)
                rating.parentCode = JsonUtils.getString("planetCode", act)
                rating.createdOn = JsonUtils.getString("createdOn", act)
            }
        }
    }
}

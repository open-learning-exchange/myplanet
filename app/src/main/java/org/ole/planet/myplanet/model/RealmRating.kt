package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.NetworkUtils

/**
 * Room replacement for the former Realm `RealmRating` model. Uploaded (Room upload path) and
 * synced; persistence goes through [org.ole.planet.myplanet.data.room.dao.RatingDao].
 */
@Entity(
    tableName = "rating",
    indices = [Index("userId"), Index("isUpdated"), Index("item"), Index("type")]
)
open class RealmRating {
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

    companion object {
        fun serializeRating(realmRating: RealmRating): JsonObject {
            val ob = JsonObject()
            if (realmRating._id != null) ob.addProperty("_id", realmRating._id)
            if (realmRating._rev != null) ob.addProperty("_rev", realmRating._rev)
            ob.add("user", org.ole.planet.myplanet.utils.JsonUtils.gson.fromJson(realmRating.user, JsonObject::class.java))
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
    }
}

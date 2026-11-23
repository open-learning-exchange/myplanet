package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

/**
 * Room replacement for the former `Rating` model. Uploaded (Room upload path) and
 * synced; persistence goes through [org.ole.planet.myplanet.data.room.dao.RatingDao].
 */
@Entity(
    tableName = "rating",
    indices = [Index("userId"), Index("isUpdated"), Index("item"), Index("type")]
)
open class Rating {
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
        fun serializeRating(realmRating: Rating, customDeviceName: String): JsonObject {
            val userJson = GsonUtils.gson.fromJson(realmRating.user, JsonObject::class.java)
            val ob = buildJsonObject {
                if (realmRating._id != null) put("_id", realmRating._id)
                if (realmRating._rev != null) put("_rev", realmRating._rev)
                put("user", userJson?.toKotlinx() ?: JsonNull)
                put("item", realmRating.item)
                put("type", realmRating.type)
                put("title", realmRating.title)
                put("time", realmRating.time)
                put("comment", realmRating.comment)
                put("rate", realmRating.rate)
                put("createdOn", realmRating.createdOn)
                put("parentCode", realmRating.parentCode)
                put("planetCode", realmRating.planetCode)
                put("customDeviceName", customDeviceName)
                put("deviceName", NetworkUtils.getDeviceName())
            }.toGson()
            ob.addDocumentOrigin()
            return ob
        }
    }
}

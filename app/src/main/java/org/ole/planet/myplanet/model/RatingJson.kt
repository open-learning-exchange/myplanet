package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson helpers for Rating, kept out of the Room entity.

fun Rating.Companion.serializeRating(realmRating: Rating, customDeviceName: String): JsonObject {
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

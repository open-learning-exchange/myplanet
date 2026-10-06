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

// App-side Gson helpers for SearchActivity, kept out of the Room entity.

fun SearchActivity.serialize(androidId: String?, customDeviceName: String): JsonObject {
    val filterJson = GsonUtils.gson.fromJson(filter, JsonObject::class.java)
    val obj = buildJsonObject {
        put("text", text)
        put("type", type)
        put("time", time)
        put("user", user)
        put("customDeviceName", customDeviceName)
        put("deviceName", NetworkUtils.getDeviceName())
        put("createdOn", createdOn)
        put("parentCode", parentCode)
        put("filter", filterJson?.toKotlinx() ?: JsonNull)
    }.toGson()
    obj.addDocumentOrigin(androidId)
    return obj
}

package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for NewsLog, kept out of the Room entity.

fun NewsLog.Companion.serialize(log: NewsLog, customDeviceName: String): JsonObject {
    val ob = buildJsonObject {
        put("user", log.userId)
        put("type", log.type)
        put("time", log.time)
        put("deviceName", NetworkUtils.getDeviceName())
        put("customDeviceName", customDeviceName)
    }.toGson()
    ob.addDocumentOrigin()
    return ob
}

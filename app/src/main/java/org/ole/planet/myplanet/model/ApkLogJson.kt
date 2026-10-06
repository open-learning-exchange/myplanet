package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for ApkLog, kept out of the Room entity.

fun ApkLog.Companion.serialize(log: ApkLog, customDeviceName: String): JsonObject {
    val `object` = buildJsonObject {
        put("type", log.type)
        put("error", log.error)
        put("page", log.page)
        put("time", log.time)
        put("userId", log.userId)
        put("version", log.version)
        put("createdOn", log.createdOn)
        put("deviceName", NetworkUtils.getDeviceName())
        put("customDeviceName", customDeviceName)
        put("parentCode", log.parentCode)
    }.toGson()
    `object`.addDocumentOrigin()
    return `object`
}

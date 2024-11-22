package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.VersionUtils

class RealmSearchActivity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var _id: String = ""
    var _rev: String = ""
    var text: String = ""
    var type: String = ""
    var time: Long = 0
    var user: String = ""
    var filter: String = ""
    var createdOn: String = ""
    var parentCode: String = ""

    fun serialize(): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(text))
        put("type", JsonPrimitive(type))
        put("time", JsonPrimitive(time))
        put("user", JsonPrimitive(user))
        put("androidId", JsonPrimitive(VersionUtils.getAndroidId(MainApplication.context)))
        put("customDeviceName", JsonPrimitive(NetworkUtils.getCustomDeviceName(MainApplication.context)))
        put("deviceName", JsonPrimitive(NetworkUtils.getDeviceName()))
        put("createdOn", JsonPrimitive(createdOn))
        put("parentCode", JsonPrimitive(parentCode))
        put("filter", kotlinx.serialization.json.Json.parseToJsonElement(filter))
    }

    companion object {
        fun insert(log: RealmNewsLog): JsonObject = buildJsonObject {
            put("user", JsonPrimitive(log.userId))
            put("type", JsonPrimitive(log.type))
            put("time", JsonPrimitive(log.time))
            put("androidId", JsonPrimitive(NetworkUtils.getUniqueIdentifier()))
            put("deviceName", JsonPrimitive(NetworkUtils.getDeviceName()))
            put("customDeviceName", JsonPrimitive(NetworkUtils.getCustomDeviceName(MainApplication.context)))
        }
    }
}
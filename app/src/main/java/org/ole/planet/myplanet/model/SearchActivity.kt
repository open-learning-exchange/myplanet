package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Entity(
    tableName = "search_activity",
    indices = [Index("_rev"), Index("type"), Index("user")]
)
open class SearchActivity(
    @PrimaryKey
    @JvmField
    var id: String = "",
    @JvmField
    var _id: String = "",
    var _rev: String = "",
    var text: String = "",
    var type: String = "",
    var time: Long = 0,
    var user: String = "",
    var filter: String = "",
    var createdOn: String = "",
    var parentCode: String = ""
) {
    fun serialize(androidId: String?, customDeviceName: String): JsonObject {
        val filterJson = JsonUtils.gson.fromJson(filter, JsonObject::class.java)
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

}

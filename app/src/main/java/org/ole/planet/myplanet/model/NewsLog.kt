package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

@Entity(
    tableName = "news_log",
    indices = [Index("_id"), Index("_rev")]
)
open class NewsLog {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var type: String? = null
    var time: Long? = null
    var userId: String? = null
    var androidId: String? = null

    companion object {
        fun serialize(log: NewsLog, customDeviceName: String): JsonObject {
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
    }
}

package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.networkUtils

open class RealmNewsLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var type: String? = null
    var time: Long? = null
    var userId: String? = null
    var androidId: String? = null

    companion object {
        @JvmStatic
        fun serialize(log: RealmNewsLog): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", log.userId)
            ob.addProperty("type", log.type)
            ob.addProperty("time", log.time)
            ob.addProperty("androidId", networkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", networkUtils.getDeviceName())
            ob.addProperty(
                "customDeviceName", networkUtils.getCustomDeviceName(MainApplication.context)
            )
            return ob
        }
    }
}

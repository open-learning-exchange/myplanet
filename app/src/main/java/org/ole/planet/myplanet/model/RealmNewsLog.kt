package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.NetworkUtils

open class RealmNewsLog : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var time: Long? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var androidId: String? = null

    companion object {
        @JvmStatic
        fun serialize(log: RealmNewsLog): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", log.userId)
            ob.addProperty("type", log.type)
            ob.addProperty("time", log.time)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty(
                "customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context)
            )
            return ob
        }
    }
}

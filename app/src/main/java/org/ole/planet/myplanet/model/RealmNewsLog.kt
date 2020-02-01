package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils


open class RealmNewsLog : RealmObject() {
    @PrimaryKey
    private var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var type: String? = null
    var time: Long? = null
    var userId: String? = null
    var androidId: String? = null


    companion object { @JvmStatic
        fun serialize(log: RealmNewsLog): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", log.userId)
            ob.addProperty("type", log.type)
            ob.addProperty("time", log.time)
            ob.addProperty("androidId", NetworkUtils.getMacAddr())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
            return ob
        }
    }

}

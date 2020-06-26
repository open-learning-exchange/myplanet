package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils

open class RealmSearchActivity(
        @PrimaryKey var id: String ="",
        var _id: String ="",
        var _rev: String ="",
        var text: String = "",
        var type: String = "",
        var time: Long = 0,
        var user: String = "",
        var filter: String = "",
        var createdOn: String = "",
        var parentCode: String = ""
) : RealmObject(){
    fun serialize() : JsonObject{
        var obj = JsonObject();
        obj.addProperty("text", text)
        obj.addProperty("type", type)
        obj.addProperty("time", time)
        obj.addProperty("user", user)
        obj.addProperty("androidId", VersionUtils.getAndroidId(MainApplication.context))
        obj.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        obj.addProperty("deviceName",  NetworkUtils.getDeviceName())
        obj.addProperty("createdOn", createdOn)
        obj.addProperty("parentCode", parentCode)
        obj.add("filter", Gson().fromJson(filter, JsonObject::class.java))
        return obj
    }

    companion object { @JvmStatic
    fun insert(log: RealmNewsLog): JsonObject {
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
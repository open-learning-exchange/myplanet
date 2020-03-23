package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

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
        obj.addProperty("createdOn", createdOn)
        obj.addProperty("parentCode", parentCode)
        obj.add("filter", Gson().fromJson(filter, JsonObject::class.java))
        return obj
    }
}
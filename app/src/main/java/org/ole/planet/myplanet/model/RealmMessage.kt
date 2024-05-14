package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmMessage : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var message: String? = null
    var time: String? = null
    var user: String? = null

    companion object {
        @JvmStatic
        fun serialize(messages: RealmList<RealmMessage>): JsonElement {
            val array = JsonArray()
            for (ms in messages) {
                val `object` = JsonObject()
                `object`.addProperty("user", ms.user)
                `object`.addProperty("time", ms.time)
                `object`.addProperty("message", ms.message)
                array.add(`object`)
            }
            return array
        }

//        fun insertFeedback(mRealm: Realm?) {}
    }
}
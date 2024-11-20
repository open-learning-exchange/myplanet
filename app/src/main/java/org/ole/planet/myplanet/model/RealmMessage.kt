package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmMessage : RealmObject {
    @PrimaryKey
    var id: String? = null
    var message: String? = null
    var time: String? = null
    var user: String? = null

    companion object {
        fun serialize(messages: List<RealmMessage>): JsonElement {
            return JsonArray().apply {
                messages.forEach { ms ->
                    val `object` = JsonObject().apply {
                        addProperty("user", ms.user)
                        addProperty("time", ms.time)
                        addProperty("message", ms.message)
                    }
                    add(`object`)
                }
            }
        }
    }
}
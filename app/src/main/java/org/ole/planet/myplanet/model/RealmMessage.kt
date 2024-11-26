package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RealmMessage : RealmObject {
    @PrimaryKey
    var id: String? = null
    var message: String? = null
    var time: String? = null
    var user: String? = null

    companion object {
        fun serialize(messages: List<RealmMessage>): JsonElement {
            return buildJsonArray {
                messages.forEach { ms ->
                    add(buildJsonObject {
                        put("user", ms.user)
                        put("time", ms.time)
                        put("message", ms.message)
                    })
                }
            }
        }
    }
}
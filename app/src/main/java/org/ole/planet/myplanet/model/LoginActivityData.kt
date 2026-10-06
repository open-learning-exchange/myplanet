package org.ole.planet.myplanet.model

import kotlinx.serialization.json.JsonObject

data class LoginActivityData(
    val id: String,
    val userId: String,
    val serialized: JsonObject
)

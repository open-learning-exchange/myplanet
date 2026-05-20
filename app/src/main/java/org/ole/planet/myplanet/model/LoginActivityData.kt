package org.ole.planet.myplanet.model

import com.google.gson.JsonObject

data class LoginActivityData(
    val id: String,
    val userId: String,
    val serialized: JsonObject
)

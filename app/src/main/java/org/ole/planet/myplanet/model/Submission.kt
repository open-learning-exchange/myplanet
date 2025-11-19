package org.ole.planet.myplanet.model

import com.google.gson.JsonObject

data class Submission(
    val id: String?,
    val _id: String?,
    val _rev: String?,
    val requestBody: JsonObject
)

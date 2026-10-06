package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

const val DOCUMENT_ORIGIN = "myplanet"

fun JsonObject.addDocumentOrigin(androidId: String? = NetworkUtils.getUniqueIdentifier()): JsonObject {
    addProperty("androidId", androidId)
    addProperty("app", DOCUMENT_ORIGIN)
    return this
}

/** kotlinx twin of [JsonObject.addDocumentOrigin]: a `null` id is written as JSON `null`. */
fun JsonObjectBuilder.addDocumentOrigin(androidId: String? = NetworkUtils.getUniqueIdentifier()) {
    put("androidId", androidId)
    put("app", DOCUMENT_ORIGIN)
}

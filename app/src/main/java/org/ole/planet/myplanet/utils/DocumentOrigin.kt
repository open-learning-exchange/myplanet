package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject

const val DOCUMENT_ORIGIN = "myplanet"

fun JsonObject.addDocumentOrigin(androidId: String? = NetworkUtils.getUniqueIdentifier()): JsonObject {
    addProperty("androidId", androidId)
    addProperty("app", DOCUMENT_ORIGIN)
    return this
}

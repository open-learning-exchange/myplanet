package org.ole.planet.myplanet.utils

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

const val DOCUMENT_ORIGIN = "myplanet"

/** Stamps the device and app a document came from; a `null` id is written as JSON `null`. */
fun JsonObjectBuilder.addDocumentOrigin(androidId: String? = NetworkUtils.getUniqueIdentifier()) {
    put("androidId", androidId)
    put("app", DOCUMENT_ORIGIN)
}

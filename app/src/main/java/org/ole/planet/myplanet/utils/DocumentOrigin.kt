package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject

/**
 * Value Planet expects in a document's `app` field for documents written by this app.
 * Planet splits activity by this field and only falls back to "has an androidId, so
 * assume myPlanet" when the field is missing, so every document we post carries it.
 */
const val DOCUMENT_APP_IDENTIFIER = "myplanet"

/**
 * Stamps where every document we post came from: the device that wrote it
 * (`androidId`) and the app it was written by (`app`).
 *
 * [androidId] defaults to [NetworkUtils.getUniqueIdentifier]; pass a value only where a
 * document shape already uses a different identifier.
 */
fun JsonObject.addDocumentOrigin(androidId: String? = NetworkUtils.getUniqueIdentifier()): JsonObject {
    addProperty("androidId", androidId)
    addProperty("app", DOCUMENT_APP_IDENTIFIER)
    return this
}

package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject

/**
 * Value Planet expects in a document's `app` field for documents written by this app.
 * Planet splits activity by this field and only falls back to "has an androidId, so
 * assume myPlanet" when the field is missing, so every document we post carries it.
 */
const val APP_IDENTIFIER = "myplanet"

/**
 * Stamps the identity every document we post carries: the device it came from
 * (`androidId`) and the app that wrote it (`app`).
 *
 * [androidId] defaults to [NetworkUtils.getUniqueIdentifier]; pass a value only where a
 * document shape already uses a different identifier.
 */
fun JsonObject.addDocumentIdentity(androidId: String? = NetworkUtils.getUniqueIdentifier()): JsonObject {
    addProperty("androidId", androidId)
    addProperty("app", APP_IDENTIFIER)
    return this
}

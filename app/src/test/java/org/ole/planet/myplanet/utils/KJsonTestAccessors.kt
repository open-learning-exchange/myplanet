package org.ole.planet.myplanet.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Gson's tree accessors over kotlinx trees, for tests whose subject moved from Gson to kotlinx:
 * the assertions keep reading `result.get("x").asString` and expect the same values. Each
 * throws where Gson's accessor throws (a missing member, a wrong type).
 */
val JsonElement?.asString: String
    get() = KJson.gsonAsString(this, KJson.GsonNumbers.LITERAL)

val JsonElement?.asLong: Long
    get() = asPrimitive().let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.content.toLong() }

val JsonElement?.asInt: Int
    get() = asLong.toInt()

val JsonElement?.asDouble: Double
    get() = asPrimitive().let { it.doubleOrNull ?: it.content.toDouble() }

val JsonElement?.asBoolean: Boolean
    get() = asPrimitive().let { it.booleanOrNull ?: it.content.toBoolean() }

val JsonElement?.asJsonObject: JsonObject
    get() = KJson.asJsonObject(this)

val JsonElement?.asJsonArray: JsonArray
    get() = this as? JsonArray ?: throw IllegalStateException("Not a JSON Array: $this")

val JsonElement?.isJsonNull: Boolean
    get() = this is kotlinx.serialization.json.JsonNull

fun JsonObject.getAsJsonObject(name: String): JsonObject = this[name].asJsonObject

fun JsonObject.getAsJsonArray(name: String): JsonArray = this[name].asJsonArray

fun JsonObject.has(name: String): Boolean = containsKey(name)

fun JsonArray.size(): Int = size

private fun JsonElement?.asPrimitive(): JsonPrimitive =
    this as? JsonPrimitive ?: throw IllegalStateException("Not a JSON Primitive: $this")

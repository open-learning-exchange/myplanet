package org.ole.planet.myplanet.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull

object JsonUtils {
    private fun field(fieldName: String, jsonObject: JsonObject?): kotlinx.serialization.json.JsonElement? =
        jsonObject?.get(fieldName)?.takeIf { it != JsonNull }

    fun getString(fieldName: String, jsonObject: JsonObject?): String {
        val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return ""
        return if (primitive.isString) primitive.content else ""
    }

    fun getString(array: JsonArray, index: Int): String {
        val primitive = array.getOrNull(index) as? JsonPrimitive ?: return ""
        return primitive.content
    }

    fun getBoolean(fieldName: String, jsonObject: JsonObject?): Boolean {
        val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return false
        return primitive.booleanOrNull ?: false
    }

    fun getInt(fieldName: String, jsonObject: JsonObject?): Int {
        val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return 0
        return primitive.longOrNull?.toInt() ?: primitive.content.toIntOrNull() ?: 0
    }

    fun getLong(fieldName: String, jsonObject: JsonObject?): Long {
        val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.content.toLongOrNull() ?: 0L
    }

    fun getFloat(fieldName: String, jsonObject: JsonObject?): Float {
        val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return 0f
        return primitive.floatOrNull ?: primitive.content.toFloatOrNull() ?: 0f
    }

    fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject =
        field(fieldName, jsonObject) as? JsonObject ?: JsonObject(emptyMap())

    fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray =
        field(fieldName, jsonObject) as? JsonArray ?: JsonArray(emptyList())

    fun rawString(fieldName: String, jsonObject: JsonObject?): String? =
        (field(fieldName, jsonObject) as? JsonPrimitive)?.content

    fun rawLong(fieldName: String, jsonObject: JsonObject?): Long? =
        (field(fieldName, jsonObject) as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }

    fun rawInt(fieldName: String, jsonObject: JsonObject?): Int? = rawLong(fieldName, jsonObject)?.toInt()

    fun rawBoolean(fieldName: String, jsonObject: JsonObject?): Boolean? =
        (field(fieldName, jsonObject) as? JsonPrimitive)?.booleanOrNull
}

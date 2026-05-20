package org.ole.planet.myplanet.utils

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser.parseString
import io.realm.RealmList

object JsonUtils {
    val gson: Gson by lazy {
        Gson()
    }

    private inline fun <T> safeGet(default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            default
        }
    }

    @JvmStatic
    fun getString(fieldName: String, jsonObject: JsonObject?): String = safeGet("") {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isString) "" else el.asString
        } else ""
    }

    @JvmStatic
    fun getString(array: JsonArray, index: Int): String = safeGet("") {
        val el: JsonElement = array.get(index)
        if (el is JsonNull) "" else el.asString
    }

    @JvmStatic
    fun getAsJsonArray(list: RealmList<String>?): JsonArray {
        val array = JsonArray()
        list?.forEach { s -> array.add(s) }
        return array
    }

    @JvmStatic
    fun getStringAsJsonArray(s: String?): JsonArray {
        val arrayElement = parseString(s)
        return arrayElement.asJsonArray
    }

    @JvmStatic
    fun getBoolean(fieldName: String, jsonObject: JsonObject?): Boolean = safeGet(false) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement? = jsonObject.get(fieldName)
            el !is JsonNull && el?.asBoolean == true
        } else false
    }

    @JvmStatic
    fun addString(`object`: JsonObject, fieldName: String, value: String?) {
        if (!value.isNullOrEmpty()) `object`.addProperty(fieldName, value)
    }

    @JvmStatic
    fun addLong(`object`: JsonObject, fieldName: String, value: Long) {
        if (value > 0) `object`.addProperty(fieldName, value)
    }

    @JvmStatic
    fun addInteger(`object`: JsonObject, fieldName: String, value: Int) {
        if (value != 0) `object`.addProperty(fieldName, value)
    }

    @JvmStatic
    fun addFloat(`object`: JsonObject, fieldName: String, value: Float) {
        if (value != 0f) `object`.addProperty(fieldName, value)
    }

    @JvmStatic
    fun addJson(`object`: JsonObject, fieldName: String, value: JsonObject?) {
        if (value != null && value.keySet().size > 0) `object`.add(fieldName, value)
    }

    @JvmStatic
    fun getInt(fieldName: String, jsonObject: JsonObject?): Int = safeGet(0) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull || el.asString.isEmpty()) 0 else el.asInt
        } else 0
    }

    @JvmStatic
    fun getFloat(fieldName: String, jsonObject: JsonObject?): Float = safeGet(0f) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull || el.asString.isEmpty()) 0f else el.asFloat
        } else getInt(fieldName, jsonObject).toFloat()
    }

    @JvmStatic
    fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray = safeGet(JsonArray()) {
        val array: JsonElement? = jsonObject?.let { getJsonElement(fieldName, it, JsonArray::class.java) }
        if (array is JsonNull || array !is JsonArray) JsonArray() else array.asJsonArray
    }

    @JvmStatic
    fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject = safeGet(JsonObject()) {
        val el: JsonElement? = jsonObject?.let { getJsonElement(fieldName, it, JsonObject::class.java) }
        if (el is JsonObject) el else JsonObject()
    }

    @JvmStatic
    fun getJsonElement(fieldName: String, jsonObject: JsonObject, type: Class<*>): JsonElement = safeGet(JsonObject()) {
        val default: JsonElement = if (type == JsonObject::class.java) JsonObject() else JsonArray()
        if (jsonObject.has(fieldName)) jsonObject.get(fieldName) else default
    }

    @JvmStatic
    fun getLong(fieldName: String, jsonObject: JsonObject?): Long = safeGet(0L) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull) 0L else el.asLong
        } else 0L
    }
}

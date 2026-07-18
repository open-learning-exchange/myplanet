package org.ole.planet.myplanet.utils

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser.parseString
import io.realm.RealmList
import org.ole.planet.myplanet.model.RealmNews

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

    fun extractSharedTeamName(news: RealmNews?): String {
        if (news == null) return ""
        val ar = news.parsedViewIn ?: if (!news.viewIn.isNullOrEmpty()) {
            try {
                gson.fromJson(news.viewIn, JsonArray::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null

        if (ar != null && ar.size() > 1) {
            val ob = ar[0].asJsonObject
            if (ob.has("name") && !ob.get("name").isJsonNull) {
                return ob.get("name").asString
            }
        }
        return ""
    }

    fun getString(fieldName: String, jsonObject: JsonObject?): String = safeGet("") {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isString) "" else el.asString
        } else ""
    }

    fun getString(array: JsonArray, index: Int): String = safeGet("") {
        val el: JsonElement = array.get(index)
        if (el is JsonNull) "" else el.asString
    }

    fun getAsJsonArray(list: List<String>?): JsonArray {
        val array = JsonArray()
        list?.forEach { s -> array.add(s) }
        return array
    }

    fun getStringAsJsonArray(s: String?): JsonArray {
        val arrayElement = parseString(s)
        return arrayElement.asJsonArray
    }

    fun getBoolean(fieldName: String, jsonObject: JsonObject?): Boolean = safeGet(false) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement? = jsonObject.get(fieldName)
            el !is JsonNull && el?.asBoolean == true
        } else false
    }

    fun addString(`object`: JsonObject, fieldName: String, value: String?) {
        if (!value.isNullOrEmpty()) `object`.addProperty(fieldName, value)
    }

    fun addLong(`object`: JsonObject, fieldName: String, value: Long) {
        if (value > 0) `object`.addProperty(fieldName, value)
    }

    fun addInteger(`object`: JsonObject, fieldName: String, value: Int) {
        if (value != 0) `object`.addProperty(fieldName, value)
    }

    fun addFloat(`object`: JsonObject, fieldName: String, value: Float) {
        if (value != 0f) `object`.addProperty(fieldName, value)
    }

    fun addJson(`object`: JsonObject, fieldName: String, value: JsonObject?) {
        if (value != null && value.keySet().size > 0) `object`.add(fieldName, value)
    }

    fun getInt(fieldName: String, jsonObject: JsonObject?): Int = safeGet(0) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull || el.asString.isEmpty()) 0 else el.asInt
        } else 0
    }

    fun getFloat(fieldName: String, jsonObject: JsonObject?): Float = safeGet(0f) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull || el.asString.isEmpty()) 0f else el.asFloat
        } else getInt(fieldName, jsonObject).toFloat()
    }

    fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray = safeGet(JsonArray()) {
        val array: JsonElement? = jsonObject?.let { getJsonElement(fieldName, it, JsonArray::class.java) }
        if (array is JsonNull || array !is JsonArray) JsonArray() else array.asJsonArray
    }

    fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject = safeGet(JsonObject()) {
        val el: JsonElement? = jsonObject?.let { getJsonElement(fieldName, it, JsonObject::class.java) }
        if (el is JsonObject) el else JsonObject()
    }

    fun getJsonElement(fieldName: String, jsonObject: JsonObject, type: Class<*>): JsonElement = safeGet(JsonObject()) {
        val default: JsonElement = if (type == JsonObject::class.java) JsonObject() else JsonArray()
        if (jsonObject.has(fieldName)) jsonObject.get(fieldName) else default
    }

    fun getLong(fieldName: String, jsonObject: JsonObject?): Long = safeGet(0L) {
        if (jsonObject?.has(fieldName) == true) {
            val el: JsonElement = jsonObject.get(fieldName)
            if (el is JsonNull) 0L else el.asLong
        } else 0L
    }
}

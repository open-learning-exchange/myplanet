package org.ole.planet.myplanet.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser.parseString
import org.ole.planet.myplanet.model.News

object JsonUtils {
    private const val TAG = "JsonUtils"

    val gson: Gson by lazy {
        Gson()
    }

    private inline fun <T> safeGet(default: () -> T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            logFallback(e)
            default()
        }
    }

    private fun logFallback(e: Exception) {
        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "expected type mismatch, using fallback: ${e.message}")
            }
        } catch (_: Throwable) {
        }
    }

    fun extractSharedTeamName(news: News?): String {
        if (news == null) return ""
        val ar = news.parsedViewIn ?: if (!news.viewIn.isNullOrEmpty()) {
            try {
                gson.fromJson(news.viewIn, JsonArray::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "failed to parse viewIn", e)
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

    private fun fieldElement(fieldName: String, jsonObject: JsonObject?): JsonElement? =
        jsonObject?.takeIf { it.has(fieldName) }?.get(fieldName)

    private fun <T> getPrimitive(default: T, elementSupplier: () -> JsonElement?, extract: (JsonElement) -> T): T = safeGet({ default }) {
        val el = elementSupplier()
        if (el == null || el is JsonNull) default else extract(el)
    }

    fun getString(fieldName: String, jsonObject: JsonObject?): String =
        getPrimitive("", { fieldElement(fieldName, jsonObject) }) { el ->
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else ""
        }

    fun getString(array: JsonArray, index: Int): String =
        getPrimitive("", { if (index in 0 until array.size()) array.get(index) else null }) { el ->
            if (!el.isJsonPrimitive) "" else el.asString
        }

    fun getAsJsonArray(list: List<String>?): JsonArray {
        val array = JsonArray()
        list?.forEach { s -> array.add(s) }
        return array
    }

    fun getStringAsJsonArray(s: String?): JsonArray {
        if (s.isNullOrBlank()) return JsonArray()
        val arrayElement = parseString(s)
        return if (arrayElement.isJsonArray) arrayElement.asJsonArray else JsonArray()
    }

    fun getBoolean(fieldName: String, jsonObject: JsonObject?): Boolean =
        getPrimitive(false, { fieldElement(fieldName, jsonObject) }) { el ->
            if (!el.isJsonPrimitive) false else el.asBoolean
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
        if (value != null && value.keySet().isNotEmpty()) `object`.add(fieldName, value)
    }

    fun getInt(fieldName: String, jsonObject: JsonObject?): Int =
        getPrimitive(0, { fieldElement(fieldName, jsonObject) }) { el ->
            if (!el.isJsonPrimitive) 0 else el.asJsonPrimitive.let { primitive ->
                if (primitive.isNumber) primitive.asInt else primitive.asString.toIntOrNull() ?: 0
            }
        }

    fun getFloat(fieldName: String, jsonObject: JsonObject?): Float =
        getPrimitive(0f, { fieldElement(fieldName, jsonObject) }) { el ->
            if (!el.isJsonPrimitive) 0f else el.asJsonPrimitive.let { primitive ->
                if (primitive.isNumber) primitive.asFloat else primitive.asString.toFloatOrNull() ?: 0f
            }
        }

    fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray = safeGet({ JsonArray() }) {
        val array: JsonElement? = jsonObject?.let { getJsonElement(fieldName, it, JsonArray::class.java) }
        if (array is JsonNull || array !is JsonArray) JsonArray() else array
    }

    fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject = safeGet({ JsonObject() }) {
        val el: JsonElement? = jsonObject?.let { getJsonElement(fieldName, it, JsonObject::class.java) }
        el as? JsonObject ?: JsonObject()
    }

    private fun getJsonElement(fieldName: String, jsonObject: JsonObject, type: Class<*>): JsonElement {
        if (!jsonObject.has(fieldName)) return if (type == JsonObject::class.java) JsonObject() else JsonArray()
        return safeGet({ if (type == JsonObject::class.java) JsonObject() else JsonArray() }) {
            jsonObject.get(fieldName)
        }
    }

    fun getLong(fieldName: String, jsonObject: JsonObject?): Long =
        getPrimitive(0L, { fieldElement(fieldName, jsonObject) }) { el ->
            if (!el.isJsonPrimitive) 0L else el.asJsonPrimitive.let { primitive ->
                if (primitive.isNumber) primitive.asLong else primitive.asString.toLongOrNull() ?: 0L
            }
        }
}

fun JsonArray.toSyncDocuments(): List<Pair<String, JsonObject>> =
    mapNotNull { element ->
        val doc = JsonUtils.getJsonObject("doc", element.asJsonObject)
        val id = JsonUtils.getString("_id", doc)
        if (id.startsWith("_design")) null else id to doc
    }

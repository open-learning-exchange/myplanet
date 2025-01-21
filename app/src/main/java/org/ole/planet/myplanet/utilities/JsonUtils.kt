package org.ole.planet.myplanet.utilities

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.realm.RealmList

object JsonUtils {
    fun getString(fieldName: String, jsonObject: JsonObject?): String {
        return try {
            if (jsonObject?.has(fieldName) == true) {
                val el: JsonElement = jsonObject.get(fieldName)
                if (el is JsonNull) {
                    ""
                } else if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                    el.asString
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun getString(array: JsonArray, index: Int): String {
        return try {
            val el: JsonElement = array.get(index)
            if (el is JsonNull) "" else el.asString
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun getAsJsonArray(list: RealmList<String>?): JsonArray {
        val array = JsonArray()
        list?.forEach { s -> array.add(s) }
        return array
    }

    fun getBoolean(fieldName: String, jsonObject: JsonObject?): Boolean {
        return try {
            if (jsonObject?.has(fieldName) == true) {
                val el: JsonElement? = jsonObject.get(fieldName)
                el !is JsonNull && el?.asBoolean == true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun addString(`object`: JsonObject, fieldName: String, value: String?) {
        if (!TextUtils.isEmpty(value)) `object`.addProperty(fieldName, value)
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

    fun getInt(fieldName: String, jsonObject: JsonObject?): Int {
        return try {
            if (jsonObject?.has(fieldName) == true) {
                val el: JsonElement = jsonObject.get(fieldName)
                if (el is JsonNull || el.asString.isEmpty()) 0 else el.asInt
            } else {
                0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun getFloat(fieldName: String, jsonObject: JsonObject?): Float {
        return try {
            if (jsonObject?.has(fieldName) == true) {
                val el: JsonElement = jsonObject.get(fieldName)
                if (el is JsonNull || el.asString.isEmpty()) 0f else el.asFloat
            } else {
                getInt(fieldName, jsonObject).toFloat()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0f
        }
    }

    fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray {
        return try {
            val array: JsonElement? = jsonObject?.let {
                getJsonElement(fieldName, it, JsonArray::class.java)
            }
            if (array is JsonNull || array !is JsonArray) JsonArray() else array.asJsonArray
        } catch (e: Exception) {
            e.printStackTrace()
            JsonArray()
        }
    }

    fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject {
        return try {
            val el: JsonElement? = jsonObject?.let {
                getJsonElement(fieldName, it, JsonObject::class.java)
            }
            if (el is JsonObject) el else JsonObject()
        } catch (e: Exception) {
            e.printStackTrace()
            JsonObject()
        }
    }

    fun getJsonElement(fieldName: String, jsonObject: JsonObject, type: Class<*>): JsonElement {
        return try {
            val jsonElement: JsonElement = if (type == JsonObject::class.java) JsonObject() else JsonArray()
            if (jsonObject.has(fieldName)) {
                jsonObject.get(fieldName)
            } else {
                jsonElement
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JsonObject()
        }
    }

    fun getLong(fieldName: String, jsonObject: JsonObject?): Long {
        return try {
            if (jsonObject?.has(fieldName) == true) {
                val el: JsonElement = jsonObject.get(fieldName)
                if (el is JsonNull) 0L else el.asLong
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }
}

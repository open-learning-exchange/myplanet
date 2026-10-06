package org.ole.planet.myplanet.utils

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.model.News

/**
 * kotlinx.serialization twin of [GsonUtils]: the same names and the same results, so a call
 * site can swap `GsonUtils.x(..., gsonObject)` for `KJson.x(..., kotlinxObject)` without a
 * behaviour change. Not wired in yet; `GsonUtilsParityTest` proves the parity case by case.
 *
 * "Same results" means: the same as GsonUtils reading the Gson tree the app actually holds,
 * which for server data is the kotlinx tree bridged with [toGson]. Numbers therefore follow the
 * bridge: an integral literal behaves as a `Long`, anything else as a `Double`.
 *
 * [JsonUtils] predates this and keeps its own, slightly different, coercions (see the parity
 * test); its callers are unchanged.
 */
object KJson {
    private const val TAG = "KJson"

    /** Lenient like Gson's `JsonParser`/`fromJson` (unquoted strings), though Gson also takes comments and single quotes. */
    val lenientJson: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
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

    /**
     * Reads `news.viewIn` (not the Gson `parsedViewIn` cache, which mirrors it). Throws where
     * GsonUtils throws: a first entry that is not an object, or a `name` that is an object or a
     * multi-element array.
     */
    fun extractSharedTeamName(news: News?): String {
        if (news == null) return ""
        val viewIn = news.viewIn
        val ar = if (!viewIn.isNullOrEmpty()) {
            try {
                lenientJson.parseToJsonElement(viewIn) as? JsonArray
            } catch (e: Exception) {
                Log.w(TAG, "failed to parse viewIn", e)
                null
            }
        } else null

        if (ar != null && ar.size > 1) {
            val ob = ar[0] as? JsonObject ?: throw IllegalStateException("Not a JSON Object: ${ar[0]}")
            val name = ob["name"]
            if (name != null && name !is JsonNull) {
                return gsonAsString(name)
            }
        }
        return ""
    }

    private fun primitive(element: JsonElement?): JsonPrimitive? =
        if (element == null || element is JsonNull) null else element as? JsonPrimitive

    /** The value [toGson] gives a non-string primitive: Boolean, Long, Double, or (unparseable) the text itself. */
    private fun bridged(p: JsonPrimitive): Any = when {
        p.isString -> p.content
        p.booleanOrNull != null -> p.booleanOrNull!!
        p.longOrNull != null -> p.longOrNull!!
        p.doubleOrNull != null -> p.doubleOrNull!!
        else -> p.content
    }

    /** Gson's `JsonElement.getAsString()` on the bridged tree. */
    private fun gsonAsString(element: JsonElement): String = when (element) {
        is JsonNull -> throw UnsupportedOperationException("JsonNull")
        is JsonPrimitive -> bridged(element).toString()
        is JsonObject -> throw UnsupportedOperationException("JsonObject")
        is JsonArray -> if (element.size == 1) gsonAsString(element[0]) else throw IllegalStateException("Array must have size 1, but has size ${element.size}")
    }

    fun getString(fieldName: String, jsonObject: JsonObject?): String = safeGet({ "" }) {
        val p = primitive(jsonObject?.get(fieldName)) ?: return@safeGet ""
        if (p.isString) p.content else ""
    }

    fun getString(array: JsonArray, index: Int): String = safeGet({ "" }) {
        val element = if (index in 0 until array.size) array[index] else null
        val p = primitive(element)
        when {
            element == null || element is JsonNull -> ""
            p == null -> ""
            else -> gsonAsString(p)
        }
    }

    fun getAsJsonArray(list: List<String>?): JsonArray = JsonArray(list.orEmpty().map { JsonPrimitive(it) })

    /** Throws (kotlinx `SerializationException`, where Gson throws `JsonSyntaxException`) on malformed input. */
    fun getStringAsJsonArray(s: String?): JsonArray {
        if (s.isNullOrBlank()) return JsonArray(emptyList())
        return lenientJson.parseToJsonElement(s).asGsonLenientValue() as? JsonArray ?: JsonArray(emptyList())
    }

    /** Gson reads lenient unquoted literals (`[a, b]`) as strings; kotlinx keeps them as non-string primitives. */
    private fun JsonElement.asGsonLenientValue(): JsonElement = when (this) {
        is JsonNull -> this
        is JsonPrimitive -> if (isString || booleanOrNull != null || doubleOrNull != null) this else JsonPrimitive(content)
        is JsonArray -> JsonArray(map { it.asGsonLenientValue() })
        is JsonObject -> JsonObject(mapValues { it.value.asGsonLenientValue() })
    }

    fun getBoolean(fieldName: String, jsonObject: JsonObject?): Boolean = safeGet({ false }) {
        val p = primitive(jsonObject?.get(fieldName)) ?: return@safeGet false
        when (val value = bridged(p)) {
            is Boolean -> value
            else -> value.toString().equals("true", ignoreCase = true)
        }
    }

    fun addString(builder: JsonObjectBuilder, fieldName: String, value: String?) {
        if (!value.isNullOrEmpty()) builder.put(fieldName, value)
    }

    fun addLong(builder: JsonObjectBuilder, fieldName: String, value: Long) {
        if (value > 0) builder.put(fieldName, value)
    }

    fun addInteger(builder: JsonObjectBuilder, fieldName: String, value: Int) {
        if (value != 0) builder.put(fieldName, value)
    }

    fun addFloat(builder: JsonObjectBuilder, fieldName: String, value: Float) {
        if (value != 0f) builder.put(fieldName, value)
    }

    fun addJson(builder: JsonObjectBuilder, fieldName: String, value: JsonObject?) {
        if (value != null && value.isNotEmpty()) builder.put(fieldName, value)
    }

    fun getInt(fieldName: String, jsonObject: JsonObject?): Int = safeGet({ 0 }) {
        val p = primitive(jsonObject?.get(fieldName)) ?: return@safeGet 0
        when (val value = bridged(p)) {
            is Long -> value.toInt()
            is Double -> value.toInt()
            else -> value.toString().toIntOrNull() ?: 0
        }
    }

    fun getFloat(fieldName: String, jsonObject: JsonObject?): Float = safeGet({ 0f }) {
        val p = primitive(jsonObject?.get(fieldName)) ?: return@safeGet 0f
        when (val value = bridged(p)) {
            is Long -> value.toFloat()
            is Double -> value.toFloat()
            else -> value.toString().toFloatOrNull() ?: 0f
        }
    }

    fun getLong(fieldName: String, jsonObject: JsonObject?): Long = safeGet({ 0L }) {
        val p = primitive(jsonObject?.get(fieldName)) ?: return@safeGet 0L
        when (val value = bridged(p)) {
            is Long -> value
            is Double -> value.toLong()
            else -> value.toString().toLongOrNull() ?: 0L
        }
    }

    /** Unlike Gson, the returned child is immutable: GsonUtils hands back the live child, so callers' edits reach the parent. */
    fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray = safeGet({ JsonArray(emptyList()) }) {
        jsonObject?.get(fieldName) as? JsonArray ?: JsonArray(emptyList())
    }

    /** Unlike Gson, the returned child is immutable: GsonUtils hands back the live child, so callers' edits reach the parent. */
    fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject = safeGet({ JsonObject(emptyMap()) }) {
        jsonObject?.get(fieldName) as? JsonObject ?: JsonObject(emptyMap())
    }
}

/** kotlinx twin of the Gson `toSyncDocuments`; throws, as it does, on a row that is not an object. */
fun JsonArray.toSyncDocuments(): List<Pair<String, JsonObject>> =
    mapNotNull { element ->
        val row = element as? JsonObject ?: throw IllegalStateException("Not a JSON Object: $element")
        val doc = KJson.getJsonObject("doc", row)
        val id = KJson.getString("_id", doc)
        if (id.startsWith("_design")) null else id to doc
    }

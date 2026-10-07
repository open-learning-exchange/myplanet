package org.ole.planet.myplanet.utils

import kotlinx.serialization.SerializationException
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
    /** Lenient like Gson's `JsonParser`/`fromJson` (unquoted strings), though Gson also takes comments and single quotes. */
    val lenientJson: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private inline fun <T> safeGet(default: () -> T, block: () -> T): T {
        return try {
            block()
        } catch (_: Exception) {
            default()
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
            } catch (_: Exception) {
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

    /**
     * Gson's `JsonElement.getAsString()`, throwing where it throws (a missing element as the
     * `NullPointerException` Kotlin raised on the platform-typed `null`). [numbers] says which
     * Gson tree this mirrors: [GsonNumbers.BRIDGED] for a kotlinx tree Gson saw through
     * [toGson], [GsonNumbers.LITERAL] for a mirror of a tree Gson parsed itself.
     */
    fun gsonAsString(element: JsonElement?, numbers: GsonNumbers = GsonNumbers.BRIDGED): String = when (element) {
        null -> throw NullPointerException("JsonElement is null")
        is JsonNull -> throw UnsupportedOperationException("JsonNull")
        is JsonPrimitive -> if (numbers == GsonNumbers.LITERAL) element.content else bridged(element).toString()
        is JsonObject -> throw UnsupportedOperationException("JsonObject")
        is JsonArray -> if (element.size == 1) gsonAsString(element[0], numbers) else throw IllegalStateException("Array must have size 1, but has size ${element.size}")
    }

    /**
     * Gson's `JsonElement.getAsBoolean()`: a primitive's text read as `"true"` (any case), a
     * one-element array as its element; throws where Gson throws, like [gsonAsString].
     */
    fun gsonAsBoolean(element: JsonElement?): Boolean = when (element) {
        null -> throw NullPointerException("JsonElement is null")
        is JsonNull -> throw UnsupportedOperationException("JsonNull")
        is JsonPrimitive -> element.content.equals("true", ignoreCase = true)
        is JsonObject -> throw UnsupportedOperationException("JsonObject")
        is JsonArray -> if (element.size == 1) gsonAsBoolean(element[0]) else throw IllegalStateException("Array must have size 1, but has size ${element.size}")
    }

    /**
     * Gson's `fromJson(text, JsonElement::class.java)`, through [GsonLenientParser]: `null` for
     * null text or an empty document, Gson's lenient grammar (single quotes, unquoted tokens as
     * strings, comments, ...), and [SerializationException] on malformed text. The result keeps
     * number literals as written, like Gson's `LazilyParsedNumber`.
     */
    fun parseOrNull(text: String?): JsonElement? = text?.let { GsonLenientParser.parse(it) }

    /** Gson's `fromJson(text, JsonObject::class.java)`: as [parseOrNull], throwing for any non-object (including `null`). */
    fun parseObjectOrNull(text: String?): JsonObject? = parseOrNull(text)?.let {
        it as? JsonObject ?: throw SerializationException("Expected a JSON object but was $it")
    }

    /** Gson's `fromJson(text, JsonArray::class.java)`: as [parseOrNull], throwing for any non-array (including `null`). */
    fun parseArrayOrNull(text: String?): JsonArray? = parseOrNull(text)?.let {
        it as? JsonArray ?: throw SerializationException("Expected a JSON array but was $it")
    }

    /** Gson's `JsonElement.getAsJsonObject()`: throws `IllegalStateException` for anything else. */
    fun asJsonObject(element: JsonElement?): JsonObject =
        element as? JsonObject ?: throw IllegalStateException("Not a JSON Object: $element")

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

    /**
     * How [toGsonString] prints a number, matching what the replaced Gson tree held.
     * - [BRIDGED]: the tree came from kotlinx (built in code, or server data decoded by the API
     *   layer) and Gson saw it through [toGson]: an integral literal prints as a `Long`, anything
     *   else as a `Double` (`1e2` -> `100.0`, `1.50` -> `1.5`).
     * - [LITERAL]: Gson parsed the text itself (Room columns, preferences), so its
     *   `LazilyParsedNumber` prints the source text unchanged, which is what kotlinx keeps too.
     */
    enum class GsonNumbers { BRIDGED, LITERAL }

    /**
     * Byte-for-byte what Gson writes for this tree. With the defaults it is Gson's
     * `JsonElement.toString()` (no HTML escaping, `null` members kept); [gsonToJson] gives the
     * `Gson().toJson(...)` flavour. Use it wherever the text is stored and later matched as text
     * (SQL `LIKE`, ids derived from the text, string post-processing); plain payloads can use
     * kotlinx's own encoding, which is structurally equal.
     *
     * Escaping follows Gson's `JsonWriter`: `"` and `\` backslashed, `\t \b \n \r \f` short
     * forms, every other char below U+0020 as a lowercase `\u00xx`, U+2028/U+2029 always
     * escaped, `/` and U+007F never; [htmlSafe] adds `< > & = '` as `\u003c \u003e \u0026
     * \u003d \u0027`. With [serializeNulls] false an object member whose value is `null` is
     * dropped (array `null`s stay), as Gson's writer does.
     */
    fun toGsonString(
        element: JsonElement,
        htmlSafe: Boolean = false,
        serializeNulls: Boolean = true,
        numbers: GsonNumbers = GsonNumbers.BRIDGED,
    ): String = StringBuilder().also { writeGson(it, element, htmlSafe, serializeNulls, numbers) }.toString()

    /** `Gson().toJson(...)` of the same tree: HTML-safe, `null` members dropped; a Kotlin `null` writes `null`. */
    fun gsonToJson(element: JsonElement?, numbers: GsonNumbers = GsonNumbers.BRIDGED): String =
        toGsonString(element ?: JsonNull, htmlSafe = true, serializeNulls = false, numbers = numbers)

    private fun writeGson(out: StringBuilder, element: JsonElement, htmlSafe: Boolean, serializeNulls: Boolean, numbers: GsonNumbers) {
        when (element) {
            is JsonNull -> out.append("null")
            is JsonObject -> {
                out.append('{')
                var first = true
                for ((key, value) in element) {
                    if (value is JsonNull && !serializeNulls) continue
                    if (!first) out.append(',')
                    first = false
                    writeGsonString(out, key, htmlSafe)
                    out.append(':')
                    writeGson(out, value, htmlSafe, serializeNulls, numbers)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                element.forEachIndexed { index, value ->
                    if (index > 0) out.append(',')
                    writeGson(out, value, htmlSafe, serializeNulls, numbers)
                }
                out.append(']')
            }
            is JsonPrimitive -> when {
                element.isString -> writeGsonString(out, element.content, htmlSafe)
                numbers == GsonNumbers.LITERAL && isJsonLiteral(element.content) -> out.append(element.content)
                numbers == GsonNumbers.LITERAL -> writeGsonString(out, element.content, htmlSafe)
                else -> when (val value = bridged(element)) {
                    is String -> writeGsonString(out, value, htmlSafe)
                    else -> out.append(value.toString())
                }
            }
        }
    }

    private fun writeGsonString(out: StringBuilder, value: String, htmlSafe: Boolean) {
        out.append('"')
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\t' -> out.append("\\t")
                c == '\b' -> out.append("\\b")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\u000c' -> out.append("\\f")
                c < ' ' || c == '\u2028' || c == '\u2029' -> appendUnicodeEscape(out, c)
                htmlSafe && (c == '<' || c == '>' || c == '&' || c == '=' || c == '\'') -> appendUnicodeEscape(out, c)
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private val jsonNumberPattern = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?")

    /** Gson's lenient parser reads any other unquoted token (`[a, b]`) as a string. */
    private fun isJsonLiteral(content: String): Boolean =
        content == "true" || content == "false" || jsonNumberPattern.matches(content)

    private fun appendUnicodeEscape(out: StringBuilder, c: Char) {
        val hex = c.code.toString(16)
        out.append("\\u")
        repeat(4 - hex.length) { out.append('0') }
        out.append(hex)
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

/**
 * This tree with every number held the way Gson held it after [toGson]: an integral literal as a
 * `Long`, any other number as a `Double` (`1e2` -> `100`, `1.50` -> `1.5`), an unparseable
 * literal as a string. Exactly `toGson().toKotlinx()`, without the Gson tree: use it where a
 * model's kotlinx document used to be bridged with [toGson] before it was sent or stored, so the
 * bytes stay what they were.
 */
fun JsonElement.withGsonBridgedNumbers(): JsonElement = when (this) {
    is JsonNull -> this
    is JsonObject -> withGsonBridgedNumbers()
    is JsonArray -> withGsonBridgedNumbers()
    is JsonPrimitive -> when {
        isString -> this
        booleanOrNull != null -> JsonPrimitive(booleanOrNull!!)
        longOrNull != null -> JsonPrimitive(longOrNull!!)
        doubleOrNull != null -> JsonPrimitive(doubleOrNull!!)
        else -> JsonPrimitive(content)
    }
}

fun JsonObject.withGsonBridgedNumbers(): JsonObject = JsonObject(mapValues { (_, value) -> value.withGsonBridgedNumbers() })

fun JsonArray.withGsonBridgedNumbers(): JsonArray = JsonArray(map { it.withGsonBridgedNumbers() })

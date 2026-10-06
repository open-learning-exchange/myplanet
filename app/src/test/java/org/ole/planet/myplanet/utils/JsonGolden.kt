package org.ole.planet.myplanet.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.StringReader
import kotlinx.serialization.json.JsonElement as KJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Golden-file assertions that pin the JSON this app builds today (with Gson), so the
 * Gson -> kotlinx.serialization migration can prove it still produces the same documents.
 *
 * Goldens live in `app/src/test/resources/json-golden/<domain>/<name>.json`. A missing golden
 * is recorded from the current output and the test fails once, so a new golden is never
 * accepted silently: re-run and review the new file. Set `JSON_GOLDEN_RECORD=1` to record every
 * missing golden in one run, or `JSON_GOLDEN_UPDATE=1` to rewrite existing ones (then review the
 * diff).
 *
 * [assertJsonEquals] is strict about structure (same keys, explicit `null` vs missing, array
 * order, and the lexical form of numbers, so `1` vs `1.0` fails) but ignores whitespace, object
 * key order and string escaping (`<` vs `\u003c`): strings are compared decoded.
 * [assertMatchesRawGolden] compares exact text, for values whose bytes matter (ids derived from
 * JSON text, strings later post-processed as text).
 */
object JsonGolden {
    private val goldenRoot = File("src/test/resources/json-golden")

    private val prettyGson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    private val canonicalGson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    private val updateGoldens: Boolean
        get() = System.getenv("JSON_GOLDEN_UPDATE") == "1"

    private val recordMissing: Boolean
        get() = updateGoldens || System.getenv("JSON_GOLDEN_RECORD") == "1"

    /** Renders a Gson or kotlinx tree (or JSON text) to JSON text, keeping explicit nulls. */
    fun jsonText(value: Any?): String = when (value) {
        null -> "null"
        is String -> value
        is JsonElement -> canonicalGson.toJson(value)
        is KJsonElement -> value.toString()
        else -> throw IllegalArgumentException("Not a JSON value: ${value::class.java.name}")
    }

    fun assertMatchesGolden(name: String, actual: Any?) {
        val actualText = jsonText(actual)
        val file = File(goldenRoot, "$name.json")
        if (!file.exists() || updateGoldens) {
            record(file, prettyGson.toJson(JsonParser.parseString(actualText)) + "\n")
            if (!recordMissing) fail("Recorded new golden ${file.path}; review it and re-run")
            return
        }
        assertJsonEquals(file.readText(), actualText, "golden $name")
    }

    fun assertMatchesRawGolden(name: String, actual: String) {
        val file = File(goldenRoot, "$name.txt")
        if (!file.exists() || updateGoldens) {
            record(file, actual)
            if (!recordMissing) fail("Recorded new raw golden ${file.path}; review it and re-run")
            return
        }
        assertJsonBytesEqual(file.readText(), actual, "raw golden $name")
    }

    private fun record(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}

fun assertJsonBytesEqual(expected: String, actual: String, label: String = "json") {
    assertEquals("$label: exact text differs", expected, actual)
}

fun assertJsonEquals(expectedText: String, actualText: String, label: String = "json") {
    val expected = try {
        StrictJson.parse(expectedText)
    } catch (e: Exception) {
        throw AssertionError("$label: expected is not strict JSON: ${e.message}", e)
    }
    val actual = try {
        StrictJson.parse(actualText)
    } catch (e: Exception) {
        throw AssertionError("$label: actual is not strict JSON: ${e.message}\n$actualText", e)
    }
    val diffs = mutableListOf<String>()
    StrictJson.diff("$", expected, actual, diffs)
    if (diffs.isNotEmpty()) {
        throw AssertionError(
            "$label: ${diffs.size} difference(s)\n" + diffs.take(25).joinToString("\n") { "  $it" } +
                "\nactual:\n$actualText"
        )
    }
}

/** A tiny JSON tree that keeps what Gson/kotlinx trees blur: number lexical form and duplicate keys. */
internal object StrictJson {
    sealed class Node
    data class Obj(val members: Map<String, Node>) : Node()
    data class Arr(val items: List<Node>) : Node()
    data class Str(val value: String) : Node()
    data class Num(val lexical: String) : Node()
    data class Bool(val value: Boolean) : Node()
    object Null : Node() {
        override fun toString() = "null"
    }

    fun parse(text: String): Node {
        val reader = JsonReader(StringReader(text))
        reader.setStrictness(Strictness.STRICT)
        val node = read(reader)
        if (reader.peek() != JsonToken.END_DOCUMENT) throw IllegalStateException("trailing content")
        return node
    }

    private fun read(reader: JsonReader): Node = when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            reader.beginObject()
            val members = LinkedHashMap<String, Node>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (members.containsKey(name)) throw IllegalStateException("duplicate key '$name'")
                members[name] = read(reader)
            }
            reader.endObject()
            Obj(members)
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            val items = mutableListOf<Node>()
            while (reader.hasNext()) items.add(read(reader))
            reader.endArray()
            Arr(items)
        }
        JsonToken.STRING -> Str(reader.nextString())
        JsonToken.NUMBER -> Num(reader.nextString())
        JsonToken.BOOLEAN -> Bool(reader.nextBoolean())
        JsonToken.NULL -> {
            reader.nextNull()
            Null
        }
        else -> throw IllegalStateException("unexpected token ${reader.peek()}")
    }

    private fun describe(node: Node): String = when (node) {
        is Obj -> "object"
        is Arr -> "array"
        is Str -> "string \"${node.value}\""
        is Num -> "number ${node.lexical}"
        is Bool -> "boolean ${node.value}"
        Null -> "null"
    }

    fun diff(path: String, expected: Node, actual: Node, out: MutableList<String>) {
        when {
            expected is Obj && actual is Obj -> {
                for (key in expected.members.keys - actual.members.keys) out.add("$path.$key: missing (expected ${describe(expected.members.getValue(key))})")
                for (key in actual.members.keys - expected.members.keys) out.add("$path.$key: unexpected ${describe(actual.members.getValue(key))}")
                for (key in expected.members.keys.intersect(actual.members.keys)) {
                    diff("$path.$key", expected.members.getValue(key), actual.members.getValue(key), out)
                }
            }
            expected is Arr && actual is Arr -> {
                if (expected.items.size != actual.items.size) {
                    out.add("$path: array size ${actual.items.size}, expected ${expected.items.size}")
                }
                for (i in 0 until minOf(expected.items.size, actual.items.size)) {
                    diff("$path[$i]", expected.items[i], actual.items[i], out)
                }
            }
            expected != actual -> out.add("$path: ${describe(actual)}, expected ${describe(expected)}")
        }
    }
}

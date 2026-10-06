package org.ole.planet.myplanet.utils

import com.google.gson.JsonElement as GsonJsonElement
import com.google.gson.JsonObject as GsonJsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden

/**
 * Read-side characterization of [GsonUtils], run against its kotlinx twin [KJson] (and the older
 * [JsonUtils]) so the parity is proven input by input.
 *
 * Every input is read four ways:
 * - `gson`: GsonUtils on the kotlinx-parsed tree bridged with toGson(), which is how server and
 *   sync documents reach the app today (the API layer decodes with kotlinx);
 * - `gsonParsed`: GsonUtils on a tree from Gson's own (lenient) JsonParser, the path for JSON
 *   strings read back from Room columns and preferences;
 * - `kjson`: KJson on the kotlinx tree; must equal `gson` for every input;
 * - `jsonUtils`: the pre-existing JsonUtils on the kotlinx tree (kept for its current callers).
 *
 * The full table is pinned in `json-golden/utils/gsonutils-read-characterization.json`, so any
 * change to how a getter coerces an input shows up as a reviewable golden diff.
 */
class GsonUtilsParityTest {

    private val values = listOf(
        MISSING, "null",
        "\"abc\"", "\"\"", "\"5\"", "\" 5\"", "\"5.7\"", "\"1e3\"", "\"3000000000\"", "\"true\"", "\"TRUE\"", "\"yes\"",
        "\"NaN\"", "\"Infinity\"", "\"-Infinity\"",
        "5", "-5", "0", "-0", "5.0", "5.7", "-5.7", "0.1", "1e3", "1E2", "1.5e1", "2.5e-3",
        "3000000000", "$BIG_LONG", "9223372036854775807", "9223372036854775808", "1e400", "-1e400",
        "true", "false",
        "{}", "{\"a\":1}", "[]", "[1]", "[\"x\"]", "[\"x\",\"y\"]",
    )

    private fun kotlinxDoc(value: String): JsonObject = Json.parseToJsonElement(docText(value)).jsonObject
    private fun bridgedDoc(value: String): GsonJsonObject = kotlinxDoc(value).toGson()
    private fun parsedDoc(value: String): GsonJsonObject = JsonParser.parseString(docText(value)).asJsonObject
    private fun docText(value: String) = if (value == MISSING) "{}" else "{\"k\":$value}"

    private fun outcome(block: () -> Any?): String = try {
        when (val result = block()) {
            is GsonJsonElement -> JsonGolden.jsonText(result)
            is JsonElement -> JsonGolden.jsonText(result)
            else -> result.toString()
        }
    } catch (e: Exception) {
        "throws ${e::class.java.simpleName}"
    }

    private data class Getter(
        val name: String,
        val gson: (GsonJsonObject?) -> Any?,
        val kjson: (JsonObject?) -> Any?,
        val jsonUtils: (JsonObject?) -> Any?,
    )

    private val getters = listOf(
        Getter("getString", { GsonUtils.getString("k", it) }, { KJson.getString("k", it) }, { JsonUtils.getString("k", it) }),
        Getter("getInt", { GsonUtils.getInt("k", it) }, { KJson.getInt("k", it) }, { JsonUtils.getInt("k", it) }),
        Getter("getLong", { GsonUtils.getLong("k", it) }, { KJson.getLong("k", it) }, { JsonUtils.getLong("k", it) }),
        Getter("getFloat", { GsonUtils.getFloat("k", it) }, { KJson.getFloat("k", it) }, { JsonUtils.getFloat("k", it) }),
        Getter("getBoolean", { GsonUtils.getBoolean("k", it) }, { KJson.getBoolean("k", it) }, { JsonUtils.getBoolean("k", it) }),
        Getter("getJsonObject", { GsonUtils.getJsonObject("k", it) }, { KJson.getJsonObject("k", it) }, { JsonUtils.getJsonObject("k", it) }),
        Getter("getJsonArray", { GsonUtils.getJsonArray("k", it) }, { KJson.getJsonArray("k", it) }, { JsonUtils.getJsonArray("k", it) }),
    )

    @Test
    fun `KJson getters match GsonUtils on the bridged tree for every input, and the full table is pinned`() {
        val table = GsonJsonObject()
        val mismatches = mutableListOf<String>()
        for (getter in getters) {
            val rows = GsonJsonObject()
            for (value in values) {
                val gson = outcome { getter.gson(bridgedDoc(value)) }
                val kjson = outcome { getter.kjson(kotlinxDoc(value)) }
                if (gson != kjson) mismatches.add("${getter.name}($value): gson=$gson kjson=$kjson")
                rows.add(value, GsonJsonObject().apply {
                    addProperty("gson", gson)
                    addProperty("gsonParsed", outcome { getter.gson(parsedDoc(value)) })
                    addProperty("kjson", kjson)
                    addProperty("jsonUtils", outcome { getter.jsonUtils(kotlinxDoc(value)) })
                })
            }
            table.add(getter.name, rows)
        }
        assertEquals("KJson must match GsonUtils", emptyList<String>(), mismatches)
        assertMatchesGolden("utils/gsonutils-read-characterization", table)
    }

    @Test
    fun `a null object reads as the default everywhere`() {
        for (getter in getters) {
            assertEquals(getter.name, outcome { getter.gson(null) }, outcome { getter.kjson(null) })
        }
    }

    private val arrayText = """["s", 5, 5.70, 1e3, 1.5e300, true, null, {}, [], ["x"], ["x","y"], ""]"""

    @Test
    fun `getString by array index`() {
        val bridged = Json.parseToJsonElement(arrayText).jsonArray.toGson()
        val parsed = JsonParser.parseString(arrayText).asJsonArray
        val kotlinx = Json.parseToJsonElement(arrayText).jsonArray
        val rows = GsonJsonObject()
        for (index in -1..kotlinx.size) {
            val gson = outcome { GsonUtils.getString(bridged, index) }
            assertEquals("index $index", gson, outcome { KJson.getString(kotlinx, index) })
            rows.add("$index", GsonJsonObject().apply {
                addProperty("gson", gson)
                addProperty("gsonParsed", outcome { GsonUtils.getString(parsed, index) })
                addProperty("jsonUtils", outcome { JsonUtils.getString(kotlinx, index) })
            })
        }
        assertMatchesGolden("utils/gsonutils-array-getString", rows)
    }

    @Test
    fun `getStringAsJsonArray`() {
        val inputs = listOf(null, "", "   ", "[1,2.0,\"<a>\"]", "{}", "null", "\"s\"", "[a, b]", "['a']", "[1,]", "[1] // c", "not json", "[1] [2]")
        val rows = GsonJsonObject()
        val expectedDifferences = setOf("['a']", "[1,]", "[1] // c")
        for (input in inputs) {
            val gson = outcome { GsonUtils.getStringAsJsonArray(input) }
            val kjson = outcome { KJson.getStringAsJsonArray(input) }
            if (input !in expectedDifferences) {
                // Exception types differ (JsonSyntaxException vs kotlinx's SerializationException); only the outcome must match.
                assertEquals("input $input", gson.substringBefore("throws ") + if (gson.startsWith("throws")) "throws" else "", kjson.substringBefore("throws ") + if (kjson.startsWith("throws")) "throws" else "")
            }
            rows.add(input ?: "<null>", GsonJsonObject().apply {
                addProperty("gson", gson)
                addProperty("kjson", kjson)
            })
        }
        assertMatchesGolden("utils/gsonutils-getStringAsJsonArray", rows)
    }

    @Test
    fun `getAsJsonArray`() {
        for (list in listOf(null, emptyList(), listOf("a", "<b>", ""))) {
            assertJsonEquals(JsonGolden.jsonText(GsonUtils.getAsJsonArray(list)), JsonGolden.jsonText(KJson.getAsJsonArray(list)))
        }
    }

    @Test
    fun `add helpers write the same members`() {
        val gson = GsonJsonObject()
        val kotlinx = buildJsonObject {
            for ((name, value) in listOf("s-null" to null, "s-empty" to "", "s" to "<x> & 'y'")) {
                GsonUtils.addString(gson, name, value)
                KJson.addString(this, name, value)
            }
            for ((name, value) in listOf("l-0" to 0L, "l-neg" to -1L, "l" to BIG_LONG)) {
                GsonUtils.addLong(gson, name, value)
                KJson.addLong(this, name, value)
            }
            for ((name, value) in listOf("i-0" to 0, "i-neg" to -1, "i" to 7)) {
                GsonUtils.addInteger(gson, name, value)
                KJson.addInteger(this, name, value)
            }
            for ((name, value) in listOf("f-0" to 0f, "f-negzero" to -0f, "f-tenth" to 0.1f, "f" to 1.5f, "f-big" to 3.0e9f, "f-tiny" to 1.0e-5f)) {
                GsonUtils.addFloat(gson, name, value)
                KJson.addFloat(this, name, value)
            }
            val nested = GsonJsonObject().apply { addProperty("a", 1) }
            for ((name, value) in listOf("j-null" to null, "j-empty" to GsonJsonObject(), "j" to nested)) {
                GsonUtils.addJson(gson, name, value)
                KJson.addJson(this, name, value?.toKotlinx()?.jsonObject)
            }
        }
        assertJsonEquals(JsonGolden.jsonText(gson), JsonGolden.jsonText(kotlinx))
        assertMatchesGolden("utils/gsonutils-add-helpers", kotlinx)
    }

    @Test
    fun `toSyncDocuments`() {
        val text = """
            [{"id":"a","doc":{"_id":"a","n":1.0}},
             {"id":"_design/x","doc":{"_id":"_design/x"}},
             {"id":"b"},
             {"doc":null},
             {"doc":{"title":"no id"}},
             {"doc":{"_id":5}},
             {"doc":"string"}]
        """.trimIndent()
        val gson = Json.parseToJsonElement(text).jsonArray.toGson().toSyncDocuments()
        val kotlinx = Json.parseToJsonElement(text).jsonArray.toSyncDocuments()
        assertEquals(gson.map { it.first }, kotlinx.map { it.first })
        gson.zip(kotlinx).forEach { (g, k) -> assertJsonEquals(JsonGolden.jsonText(g.second), JsonGolden.jsonText(k.second)) }
        assertEquals(listOf("a", "", "", "", "", ""), kotlinx.map { it.first })

        val badRow = "[1]"
        assertEquals(
            outcome { Json.parseToJsonElement(badRow).jsonArray.toGson().toSyncDocuments() },
            outcome { Json.parseToJsonElement(badRow).jsonArray.toSyncDocuments() }
        )
        assertEquals("throws IllegalStateException", outcome { Json.parseToJsonElement(badRow).jsonArray.toSyncDocuments() })
    }

    @Test
    fun `extractSharedTeamName`() {
        val viewIns = listOf(
            null, "", "[]", "not json", "{}",
            """[{"name":"only"}]""",
            """[{"name":"Team <A>","_id":"t"},{"section":"community"}]""",
            """[{"name":5.0},{}]""",
            """[{"name":null},{}]""",
            """[{"_id":"t"},{}]""",
            """[{"name":["solo"]},{}]""",
            """[{"name":["a","b"]},{}]""",
            """[{"name":{"x":1}},{}]""",
            """["string",{}]""",
        )
        val rows = GsonJsonObject()
        for (viewIn in viewIns) {
            val news = News().apply { this.viewIn = viewIn }
            val gson = outcome { GsonUtils.extractSharedTeamName(news) }
            assertEquals("viewIn $viewIn", gson, outcome { KJson.extractSharedTeamName(news) })
            rows.addProperty(viewIn ?: "<null>", gson)
        }
        assertEquals("", KJson.extractSharedTeamName(null))
        assertMatchesGolden("utils/gsonutils-extractSharedTeamName", rows)
    }

    private companion object {
        const val MISSING = "<missing>"
    }
}

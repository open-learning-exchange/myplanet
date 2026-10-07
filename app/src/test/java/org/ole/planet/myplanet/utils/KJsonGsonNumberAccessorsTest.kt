package org.ole.planet.myplanet.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

/** [KJson.gsonAsLong] and [KJson.gsonAsInt] match Gson's `getAsLong`/`getAsInt` on the bridged tree, results and exceptions alike. */
class KJsonGsonNumberAccessorsTest {

    private val samples = listOf(
        "1", "-7", "2.5", "-2.5", "1.7E12", "1e2", "9007199254740993", "9223372036854775807", "1e30",
        "\"3\"", "\"-4\"", "\"2.5\"", "\"abc\"", "\"\"", "true", "false", "null", "[5]", "[\"6\"]", "[]", "[1,2]", "{}", "{\"a\":1}",
    )

    private fun outcome(block: () -> Any): String = try {
        "value ${block()}"
    } catch (e: Exception) {
        "threw ${e::class.java.simpleName}"
    }

    @Test
    fun `long and int read as Gson reads the bridged tree`() {
        for (text in samples) {
            val element = (Json.parseToJsonElement("[$text]") as JsonArray)[0]
            val gson = JsonArray(listOf(element)).toGson()[0]
            assertEquals("asLong $text", outcome { gson.asLong }, outcome { KJson.gsonAsLong(element) })
            assertEquals("asInt $text", outcome { gson.asInt }, outcome { KJson.gsonAsInt(element) })
        }
    }

    @Test
    fun `a missing element throws as Gson's platform null did`() {
        val missing: JsonElement? = null
        assertEquals("threw NullPointerException", outcome { KJson.gsonAsLong(missing) })
        assertEquals("threw NullPointerException", outcome { KJson.gsonAsInt(missing) })
    }
}

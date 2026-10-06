package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonGoldenTest {

    private fun assertDiffers(expected: String, actual: String) {
        assertThrows(AssertionError::class.java) { assertJsonEquals(expected, actual) }
    }

    @Test
    fun `whitespace and object key order are ignored`() {
        assertJsonEquals("""{"a":1,"b":[1,2]}""", "{ \"b\" : [1, 2],\n \"a\": 1 }")
    }

    @Test
    fun `html escaping is ignored because strings compare decoded`() {
        val escaped = """{"s":"${u("003c")}a href${u("003d")}${u("0027")}x${u("0027")}${u("003e")}${u("0026")}${u("003d")}${u("003c")}/a${u("003e")}"}"""
        assertEquals(false, escaped.contains("<"))
        assertJsonEquals("""{"s":"<a href='x'>&=</a>"}""", escaped)
    }

    /** Builds a JSON `\uXXXX` escape without the source file itself containing one. */
    private fun u(hex: String) = "\\" + "u" + hex

    @Test
    fun `number lexical form matters`() {
        assertDiffers("""{"n":1}""", """{"n":1.0}""")
        assertDiffers("""{"n":100}""", """{"n":1e2}""")
        assertDiffers("""{"n":0.1}""", """{"n":0.10000000149011612}""")
    }

    @Test
    fun `explicit null is not the same as a missing key`() {
        assertDiffers("""{"a":null}""", """{}""")
        assertDiffers("""{}""", """{"a":null}""")
    }

    @Test
    fun `array order and size matter`() {
        assertDiffers("""[1,2]""", """[2,1]""")
        assertDiffers("""[1,2]""", """[1,2,3]""")
    }

    @Test
    fun `a number and its string form differ`() {
        assertDiffers("""{"a":1}""", """{"a":"1"}""")
        assertDiffers("""{"a":true}""", """{"a":"true"}""")
    }

    @Test
    fun `lenient or duplicate-key input is rejected`() {
        assertThrows(AssertionError::class.java) { assertJsonEquals("""{"a":1}""", """{a:1}""") }
        assertThrows(AssertionError::class.java) { assertJsonEquals("""{"a":1}""", """{"a":1,"a":1}""") }
    }

    @Test
    fun `jsonText keeps explicit nulls from both Gson and kotlinx trees`() {
        val gson = JsonObject().apply { add("a", null) }
        val kotlinx = buildJsonObject { put("a", null as String?) }
        assertEquals("""{"a":null}""", JsonGolden.jsonText(gson))
        assertEquals("""{"a":null}""", JsonGolden.jsonText(kotlinx))
    }

    @Test
    fun `raw comparison is byte exact`() {
        assertThrows(AssertionError::class.java) { assertJsonBytesEqual("""{"s":"<"}""", """{"s":"${u("003c")}"}""") }
    }
}

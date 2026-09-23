package org.ole.planet.myplanet.utils

import com.google.gson.JsonObject as GsonJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JsonUtilsTest {
    private val gsonDoc = GsonJsonObject().apply {
        addProperty("name", "value")
        addProperty("intAsString", "42")
        addProperty("count", 7)
        addProperty("flag", true)
        addProperty("temp", 98.6f)
        add("missing", null)
        add("nested", GsonJsonObject().apply { addProperty("inner", "yes") })
    }
    private val kotlinxDoc = gsonDoc.toKotlinx().jsonObject

    @Test
    fun `getString matches for a present string field`() {
        assertEquals(
            GsonUtils.getString("name", gsonDoc),
            JsonUtils.getString("name", kotlinxDoc)
        )
        assertEquals("value", JsonUtils.getString("name", kotlinxDoc))
    }

    @Test
    fun `getString on a missing field returns empty on both`() {
        assertEquals(
            GsonUtils.getString("nope", gsonDoc),
            JsonUtils.getString("nope", kotlinxDoc)
        )
        assertEquals("", JsonUtils.getString("nope", kotlinxDoc))
    }

    @Test
    fun `getString on a numeric field returns empty on both, matching the string-only contract`() {
        assertEquals(
            GsonUtils.getString("count", gsonDoc),
            JsonUtils.getString("count", kotlinxDoc)
        )
        assertEquals("", JsonUtils.getString("count", kotlinxDoc))
    }

    @Test
    fun `getInt parses a numeric field and a numeric string field the same way`() {
        assertEquals(GsonUtils.getInt("count", gsonDoc), JsonUtils.getInt("count", kotlinxDoc))
        assertEquals(7, JsonUtils.getInt("count", kotlinxDoc))

        assertEquals(
            GsonUtils.getInt("intAsString", gsonDoc),
            JsonUtils.getInt("intAsString", kotlinxDoc)
        )
        assertEquals(42, JsonUtils.getInt("intAsString", kotlinxDoc))
    }

    @Test
    fun `getInt on a missing field returns 0 on both`() {
        assertEquals(GsonUtils.getInt("nope", gsonDoc), JsonUtils.getInt("nope", kotlinxDoc))
    }

    @Test
    fun `getLong matches`() {
        assertEquals(GsonUtils.getLong("count", gsonDoc), JsonUtils.getLong("count", kotlinxDoc))
    }

    @Test
    fun `getFloat matches`() {
        assertEquals(GsonUtils.getFloat("temp", gsonDoc), JsonUtils.getFloat("temp", kotlinxDoc))
    }

    @Test
    fun `getBoolean matches for present and missing fields`() {
        assertEquals(GsonUtils.getBoolean("flag", gsonDoc), JsonUtils.getBoolean("flag", kotlinxDoc))
        assertEquals(true, JsonUtils.getBoolean("flag", kotlinxDoc))
        assertEquals(GsonUtils.getBoolean("nope", gsonDoc), JsonUtils.getBoolean("nope", kotlinxDoc))
        assertFalse(JsonUtils.getBoolean("nope", kotlinxDoc))
    }

    @Test
    fun `getJsonObject returns the nested object, or an empty one when missing`() {
        val nested = JsonUtils.getJsonObject("nested", kotlinxDoc)
        assertEquals("yes", JsonUtils.getString("inner", nested))

        val missing = JsonUtils.getJsonObject("nope", kotlinxDoc)
        assertEquals(0, missing.size)
    }

    @Test
    fun `getJsonArray returns the nested array, or an empty one when missing`() {
        val doc = buildJsonObject {
            putJsonArray("items") {
                add("a")
                add("b")
            }
        }

        assertEquals(2, JsonUtils.getJsonArray("items", doc).size)
        assertEquals(0, JsonUtils.getJsonArray("nope", doc).size)
    }

    @Test
    fun `a null-valued field behaves like a missing field, matching Gson's JsonNull handling`() {
        assertEquals(
            GsonUtils.getString("missing", gsonDoc),
            JsonUtils.getString("missing", kotlinxDoc)
        )
        assertEquals("", JsonUtils.getString("missing", kotlinxDoc))
    }

    @Test
    fun `raw getters return null for a missing field, unlike the default-returning getters`() {
        assertEquals(null, JsonUtils.rawString("nope", kotlinxDoc))
        assertEquals(null, JsonUtils.rawLong("nope", kotlinxDoc))
        assertEquals(null, JsonUtils.rawInt("nope", kotlinxDoc))
        assertEquals(null, JsonUtils.rawBoolean("nope", kotlinxDoc))
    }

    @Test
    fun `raw getters read any primitive kind, matching Gson's forgiving asString-asLong-asBoolean`() {
        assertEquals("7", JsonUtils.rawString("count", kotlinxDoc))
        assertEquals(7L, JsonUtils.rawLong("count", kotlinxDoc))
        assertEquals(7, JsonUtils.rawInt("count", kotlinxDoc))
        assertEquals(true, JsonUtils.rawBoolean("flag", kotlinxDoc))
    }
}

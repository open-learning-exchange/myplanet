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

class KotlinxJsonUtilsTest {
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
            JsonUtils.getString("name", gsonDoc),
            KotlinxJsonUtils.getString("name", kotlinxDoc)
        )
        assertEquals("value", KotlinxJsonUtils.getString("name", kotlinxDoc))
    }

    @Test
    fun `getString on a missing field returns empty on both`() {
        assertEquals(
            JsonUtils.getString("nope", gsonDoc),
            KotlinxJsonUtils.getString("nope", kotlinxDoc)
        )
        assertEquals("", KotlinxJsonUtils.getString("nope", kotlinxDoc))
    }

    @Test
    fun `getString on a numeric field returns empty on both, matching the string-only contract`() {
        assertEquals(
            JsonUtils.getString("count", gsonDoc),
            KotlinxJsonUtils.getString("count", kotlinxDoc)
        )
        assertEquals("", KotlinxJsonUtils.getString("count", kotlinxDoc))
    }

    @Test
    fun `getInt parses a numeric field and a numeric string field the same way`() {
        assertEquals(JsonUtils.getInt("count", gsonDoc), KotlinxJsonUtils.getInt("count", kotlinxDoc))
        assertEquals(7, KotlinxJsonUtils.getInt("count", kotlinxDoc))

        assertEquals(
            JsonUtils.getInt("intAsString", gsonDoc),
            KotlinxJsonUtils.getInt("intAsString", kotlinxDoc)
        )
        assertEquals(42, KotlinxJsonUtils.getInt("intAsString", kotlinxDoc))
    }

    @Test
    fun `getInt on a missing field returns 0 on both`() {
        assertEquals(JsonUtils.getInt("nope", gsonDoc), KotlinxJsonUtils.getInt("nope", kotlinxDoc))
    }

    @Test
    fun `getLong matches`() {
        assertEquals(JsonUtils.getLong("count", gsonDoc), KotlinxJsonUtils.getLong("count", kotlinxDoc))
    }

    @Test
    fun `getFloat matches`() {
        assertEquals(JsonUtils.getFloat("temp", gsonDoc), KotlinxJsonUtils.getFloat("temp", kotlinxDoc))
    }

    @Test
    fun `getBoolean matches for present and missing fields`() {
        assertEquals(JsonUtils.getBoolean("flag", gsonDoc), KotlinxJsonUtils.getBoolean("flag", kotlinxDoc))
        assertEquals(true, KotlinxJsonUtils.getBoolean("flag", kotlinxDoc))
        assertEquals(JsonUtils.getBoolean("nope", gsonDoc), KotlinxJsonUtils.getBoolean("nope", kotlinxDoc))
        assertFalse(KotlinxJsonUtils.getBoolean("nope", kotlinxDoc))
    }

    @Test
    fun `getJsonObject returns the nested object, or an empty one when missing`() {
        val nested = KotlinxJsonUtils.getJsonObject("nested", kotlinxDoc)
        assertEquals("yes", KotlinxJsonUtils.getString("inner", nested))

        val missing = KotlinxJsonUtils.getJsonObject("nope", kotlinxDoc)
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

        assertEquals(2, KotlinxJsonUtils.getJsonArray("items", doc).size)
        assertEquals(0, KotlinxJsonUtils.getJsonArray("nope", doc).size)
    }

    @Test
    fun `a null-valued field behaves like a missing field, matching Gson's JsonNull handling`() {
        assertEquals(
            JsonUtils.getString("missing", gsonDoc),
            KotlinxJsonUtils.getString("missing", kotlinxDoc)
        )
        assertEquals("", KotlinxJsonUtils.getString("missing", kotlinxDoc))
    }

    @Test
    fun `raw getters return null for a missing field, unlike the default-returning getters`() {
        assertEquals(null, KotlinxJsonUtils.rawString("nope", kotlinxDoc))
        assertEquals(null, KotlinxJsonUtils.rawLong("nope", kotlinxDoc))
        assertEquals(null, KotlinxJsonUtils.rawInt("nope", kotlinxDoc))
        assertEquals(null, KotlinxJsonUtils.rawBoolean("nope", kotlinxDoc))
    }

    @Test
    fun `raw getters read any primitive kind, matching Gson's forgiving asString-asLong-asBoolean`() {
        assertEquals("7", KotlinxJsonUtils.rawString("count", kotlinxDoc))
        assertEquals(7L, KotlinxJsonUtils.rawLong("count", kotlinxDoc))
        assertEquals(7, KotlinxJsonUtils.rawInt("count", kotlinxDoc))
        assertEquals(true, KotlinxJsonUtils.rawBoolean("flag", kotlinxDoc))
    }
}

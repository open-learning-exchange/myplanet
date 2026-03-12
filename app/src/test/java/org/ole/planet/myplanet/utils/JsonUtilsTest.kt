package org.ole.planet.myplanet.utils

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.realm.RealmList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsTest {

    // -------------------------------------------------------------------------
    // getString(fieldName, jsonObject)
    // -------------------------------------------------------------------------

    @Test
    fun `getString returns value for existing string field`() {
        val obj = JsonObject().apply { addProperty("key", "hello") }
        assertEquals("hello", JsonUtils.getString("key", obj))
    }

    @Test
    fun `getString returns empty string for missing field`() {
        assertEquals("", JsonUtils.getString("missing", JsonObject()))
    }

    @Test
    fun `getString returns empty string for null JsonObject`() {
        assertEquals("", JsonUtils.getString("key", null))
    }

    @Test
    fun `getString returns empty string for JsonNull value`() {
        val obj = JsonObject().apply { add("key", JsonNull.INSTANCE) }
        assertEquals("", JsonUtils.getString("key", obj))
    }

    @Test
    fun `getString returns empty string for non-string primitive`() {
        val obj = JsonObject().apply { addProperty("key", 42) }
        assertEquals("", JsonUtils.getString("key", obj))
    }

    @Test
    fun `getString returns empty string for boolean primitive`() {
        val obj = JsonObject().apply { addProperty("key", true) }
        assertEquals("", JsonUtils.getString("key", obj))
    }

    // -------------------------------------------------------------------------
    // getString(array, index)
    // -------------------------------------------------------------------------

    @Test
    fun `getString from array returns element at valid index`() {
        val array = JsonArray().apply { add("item") }
        assertEquals("item", JsonUtils.getString(array, 0))
    }

    @Test
    fun `getString from array returns empty for JsonNull element`() {
        val array = JsonArray().apply { add(JsonNull.INSTANCE) }
        assertEquals("", JsonUtils.getString(array, 0))
    }

    @Test
    fun `getString from array returns correct element at non-zero index`() {
        val array = JsonArray().apply { add("first"); add("second"); add("third") }
        assertEquals("third", JsonUtils.getString(array, 2))
    }

    // -------------------------------------------------------------------------
    // getBoolean
    // -------------------------------------------------------------------------

    @Test
    fun `getBoolean returns true for true field`() {
        val obj = JsonObject().apply { addProperty("flag", true) }
        assertTrue(JsonUtils.getBoolean("flag", obj))
    }

    @Test
    fun `getBoolean returns false for false field`() {
        val obj = JsonObject().apply { addProperty("flag", false) }
        assertFalse(JsonUtils.getBoolean("flag", obj))
    }

    @Test
    fun `getBoolean returns false for missing field`() {
        assertFalse(JsonUtils.getBoolean("flag", JsonObject()))
    }

    @Test
    fun `getBoolean returns false for null JsonObject`() {
        assertFalse(JsonUtils.getBoolean("flag", null))
    }

    @Test
    fun `getBoolean returns false for JsonNull value`() {
        val obj = JsonObject().apply { add("flag", JsonNull.INSTANCE) }
        assertFalse(JsonUtils.getBoolean("flag", obj))
    }

    // -------------------------------------------------------------------------
    // getInt
    // -------------------------------------------------------------------------

    @Test
    fun `getInt returns integer value`() {
        val obj = JsonObject().apply { addProperty("n", 5) }
        assertEquals(5, JsonUtils.getInt("n", obj))
    }

    @Test
    fun `getInt returns 0 for missing field`() {
        assertEquals(0, JsonUtils.getInt("n", JsonObject()))
    }

    @Test
    fun `getInt returns 0 for null JsonObject`() {
        assertEquals(0, JsonUtils.getInt("n", null))
    }

    @Test
    fun `getInt returns 0 for JsonNull value`() {
        val obj = JsonObject().apply { add("n", JsonNull.INSTANCE) }
        assertEquals(0, JsonUtils.getInt("n", obj))
    }

    @Test
    fun `getInt returns correct negative value`() {
        val obj = JsonObject().apply { addProperty("n", -7) }
        assertEquals(-7, JsonUtils.getInt("n", obj))
    }

    // -------------------------------------------------------------------------
    // getLong
    // -------------------------------------------------------------------------

    @Test
    fun `getLong returns long value`() {
        val obj = JsonObject().apply { addProperty("ts", 1234567890123L) }
        assertEquals(1234567890123L, JsonUtils.getLong("ts", obj))
    }

    @Test
    fun `getLong returns 0 for missing field`() {
        assertEquals(0L, JsonUtils.getLong("ts", JsonObject()))
    }

    @Test
    fun `getLong returns 0 for null JsonObject`() {
        assertEquals(0L, JsonUtils.getLong("ts", null))
    }

    @Test
    fun `getLong returns 0 for JsonNull`() {
        val obj = JsonObject().apply { add("ts", JsonNull.INSTANCE) }
        assertEquals(0L, JsonUtils.getLong("ts", obj))
    }

    // -------------------------------------------------------------------------
    // getFloat
    // -------------------------------------------------------------------------

    @Test
    fun `getFloat returns float value`() {
        val obj = JsonObject().apply { addProperty("f", 3.14f) }
        assertEquals(3.14f, JsonUtils.getFloat("f", obj), 0.001f)
    }

    @Test
    fun `getFloat returns 0 for missing field`() {
        assertEquals(0f, JsonUtils.getFloat("f", JsonObject()), 0f)
    }

    @Test
    fun `getFloat returns 0 for null JsonObject`() {
        assertEquals(0f, JsonUtils.getFloat("f", null), 0f)
    }

    @Test
    fun `getFloat returns 0 for JsonNull value`() {
        val obj = JsonObject().apply { add("f", JsonNull.INSTANCE) }
        assertEquals(0f, JsonUtils.getFloat("f", obj), 0f)
    }

    // -------------------------------------------------------------------------
    // getJsonArray
    // -------------------------------------------------------------------------

    @Test
    fun `getJsonArray returns array for existing field`() {
        val inner = JsonArray().apply { add("x") }
        val obj = JsonObject().apply { add("arr", inner) }
        val result = JsonUtils.getJsonArray("arr", obj)
        assertEquals(1, result.size())
        assertEquals("x", result[0].asString)
    }

    @Test
    fun `getJsonArray returns empty array for missing field`() {
        assertEquals(0, JsonUtils.getJsonArray("arr", JsonObject()).size())
    }

    @Test
    fun `getJsonArray returns empty array for null JsonObject`() {
        assertEquals(0, JsonUtils.getJsonArray("arr", null).size())
    }

    @Test
    fun `getJsonArray returns empty array when field is not an array`() {
        val obj = JsonObject().apply { addProperty("arr", "not-array") }
        assertEquals(0, JsonUtils.getJsonArray("arr", obj).size())
    }

    @Test
    fun `getJsonArray preserves multiple elements`() {
        val inner = JsonArray().apply { add(1); add(2); add(3) }
        val obj = JsonObject().apply { add("nums", inner) }
        assertEquals(3, JsonUtils.getJsonArray("nums", obj).size())
    }

    // -------------------------------------------------------------------------
    // getJsonObject
    // -------------------------------------------------------------------------

    @Test
    fun `getJsonObject returns nested object`() {
        val nested = JsonObject().apply { addProperty("x", 1) }
        val obj = JsonObject().apply { add("child", nested) }
        val result = JsonUtils.getJsonObject("child", obj)
        assertEquals(1, result.get("x").asInt)
    }

    @Test
    fun `getJsonObject returns empty object for missing field`() {
        assertEquals(0, JsonUtils.getJsonObject("child", JsonObject()).keySet().size)
    }

    @Test
    fun `getJsonObject returns empty object for null input`() {
        assertEquals(0, JsonUtils.getJsonObject("child", null).keySet().size)
    }

    // -------------------------------------------------------------------------
    // getJsonElement
    // -------------------------------------------------------------------------

    @Test
    fun `getJsonElement returns element for existing field`() {
        val obj = JsonObject().apply { addProperty("x", 42) }
        val result = JsonUtils.getJsonElement("x", obj, JsonObject::class.java)
        assertEquals(42, result.asInt)
    }

    @Test
    fun `getJsonElement returns empty JsonObject for missing field when type is JsonObject`() {
        val result = JsonUtils.getJsonElement("missing", JsonObject(), JsonObject::class.java)
        assertTrue(result is JsonObject)
        assertEquals(0, (result as JsonObject).keySet().size)
    }

    @Test
    fun `getJsonElement returns empty JsonArray for missing field when type is JsonArray`() {
        val result = JsonUtils.getJsonElement("missing", JsonObject(), JsonArray::class.java)
        assertTrue(result is JsonArray)
        assertEquals(0, (result as JsonArray).size())
    }

    // -------------------------------------------------------------------------
    // addString
    // -------------------------------------------------------------------------

    @Test
    fun `addString adds non-null non-empty value`() {
        val obj = JsonObject()
        JsonUtils.addString(obj, "key", "value")
        assertEquals("value", obj.get("key").asString)
    }

    @Test
    fun `addString skips null value`() {
        val obj = JsonObject()
        JsonUtils.addString(obj, "key", null)
        assertFalse(obj.has("key"))
    }

    @Test
    fun `addString skips empty value`() {
        val obj = JsonObject()
        JsonUtils.addString(obj, "key", "")
        assertFalse(obj.has("key"))
    }

    // -------------------------------------------------------------------------
    // addLong
    // -------------------------------------------------------------------------

    @Test
    fun `addLong adds positive value`() {
        val obj = JsonObject()
        JsonUtils.addLong(obj, "ts", 1000L)
        assertEquals(1000L, obj.get("ts").asLong)
    }

    @Test
    fun `addLong skips zero value`() {
        val obj = JsonObject()
        JsonUtils.addLong(obj, "ts", 0L)
        assertFalse(obj.has("ts"))
    }

    @Test
    fun `addLong skips negative value`() {
        val obj = JsonObject()
        JsonUtils.addLong(obj, "ts", -1L)
        assertFalse(obj.has("ts"))
    }

    // -------------------------------------------------------------------------
    // addInteger
    // -------------------------------------------------------------------------

    @Test
    fun `addInteger adds non-zero value`() {
        val obj = JsonObject()
        JsonUtils.addInteger(obj, "n", 5)
        assertEquals(5, obj.get("n").asInt)
    }

    @Test
    fun `addInteger skips zero value`() {
        val obj = JsonObject()
        JsonUtils.addInteger(obj, "n", 0)
        assertFalse(obj.has("n"))
    }

    @Test
    fun `addInteger adds negative value`() {
        val obj = JsonObject()
        JsonUtils.addInteger(obj, "n", -3)
        assertEquals(-3, obj.get("n").asInt)
    }

    // -------------------------------------------------------------------------
    // addFloat
    // -------------------------------------------------------------------------

    @Test
    fun `addFloat adds non-zero value`() {
        val obj = JsonObject()
        JsonUtils.addFloat(obj, "f", 1.5f)
        assertEquals(1.5f, obj.get("f").asFloat, 0.001f)
    }

    @Test
    fun `addFloat skips zero value`() {
        val obj = JsonObject()
        JsonUtils.addFloat(obj, "f", 0f)
        assertFalse(obj.has("f"))
    }

    // -------------------------------------------------------------------------
    // addJson
    // -------------------------------------------------------------------------

    @Test
    fun `addJson adds non-null non-empty object`() {
        val outer = JsonObject()
        val inner = JsonObject().apply { addProperty("x", 1) }
        JsonUtils.addJson(outer, "child", inner)
        assertTrue(outer.has("child"))
        assertEquals(1, outer.getAsJsonObject("child").get("x").asInt)
    }

    @Test
    fun `addJson skips null`() {
        val obj = JsonObject()
        JsonUtils.addJson(obj, "child", null)
        assertFalse(obj.has("child"))
    }

    @Test
    fun `addJson skips empty object`() {
        val obj = JsonObject()
        JsonUtils.addJson(obj, "child", JsonObject())
        assertFalse(obj.has("child"))
    }

    // -------------------------------------------------------------------------
    // getAsJsonArray
    // -------------------------------------------------------------------------

    @Test
    fun `getAsJsonArray returns empty array for null list`() {
        assertEquals(0, JsonUtils.getAsJsonArray(null).size())
    }

    @Test
    fun `getAsJsonArray converts non-null RealmList to JsonArray`() {
        val list = RealmList<String>().apply { addAll(listOf("a", "b", "c")) }
        val result = JsonUtils.getAsJsonArray(list)
        assertEquals(3, result.size())
        assertEquals("a", result[0].asString)
        assertEquals("b", result[1].asString)
        assertEquals("c", result[2].asString)
    }

    @Test
    fun `getAsJsonArray returns empty array for empty RealmList`() {
        assertEquals(0, JsonUtils.getAsJsonArray(RealmList<String>()).size())
    }

    // -------------------------------------------------------------------------
    // getStringAsJsonArray
    // -------------------------------------------------------------------------

    @Test
    fun `getStringAsJsonArray parses valid JSON array string`() {
        val result = JsonUtils.getStringAsJsonArray("""["a","b","c"]""")
        assertEquals(3, result.size())
        assertEquals("a", result[0].asString)
    }

    @Test
    fun `getStringAsJsonArray parses empty JSON array string`() {
        val result = JsonUtils.getStringAsJsonArray("[]")
        assertEquals(0, result.size())
    }
}

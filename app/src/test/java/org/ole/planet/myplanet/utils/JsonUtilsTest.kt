package org.ole.planet.myplanet.utils

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.News

class JsonUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetStringWithValidString() {
        val jsonObject = JsonObject()
        jsonObject.addProperty("key", "value")
        assertEquals("value", JsonUtils.getString("key", jsonObject))
    }

    @Test
    fun testGetStringWithJsonNull() {
        val jsonObject = JsonObject()
        jsonObject.add("key", JsonNull.INSTANCE)
        assertEquals("", JsonUtils.getString("key", jsonObject))
    }

    @Test
    fun testGetStringWithMissingKey() {
        val jsonObject = JsonObject()
        assertEquals("", JsonUtils.getString("missing", jsonObject))
    }

    @Test
    fun testGetBoolean() {
        val jsonObject = JsonObject()
        jsonObject.addProperty("flagTrue", true)
        jsonObject.addProperty("flagFalse", false)

        assertEquals(true, JsonUtils.getBoolean("flagTrue", jsonObject))
        assertEquals(false, JsonUtils.getBoolean("flagFalse", jsonObject))
        assertEquals(false, JsonUtils.getBoolean("missing", jsonObject))
    }

    @Test
    fun testGetInt() {
        val obj = JsonObject()
        obj.addProperty("num", 42)
        obj.addProperty("strNum", "42")
        obj.addProperty("empty", "")
        obj.add("nullVal", JsonNull.INSTANCE)
        obj.add("wrongType", JsonObject())

        assertEquals(42, JsonUtils.getInt("num", obj))
        assertEquals(42, JsonUtils.getInt("strNum", obj))
        assertEquals(0, JsonUtils.getInt("empty", obj))
        assertEquals(0, JsonUtils.getInt("nullVal", obj))
        assertEquals(0, JsonUtils.getInt("missing", obj))
        assertEquals(0, JsonUtils.getInt("wrongType", obj))
    }

    @Test
    fun testGetFloat() {
        val obj = JsonObject()
        obj.addProperty("num", 42.5f)
        obj.addProperty("strNum", "42.5")
        obj.addProperty("empty", "")
        obj.add("nullVal", JsonNull.INSTANCE)
        obj.add("wrongType", JsonObject())

        assertEquals(42.5f, JsonUtils.getFloat("num", obj))
        assertEquals(42.5f, JsonUtils.getFloat("strNum", obj))
        assertEquals(0f, JsonUtils.getFloat("empty", obj))
        assertEquals(0f, JsonUtils.getFloat("nullVal", obj))
        assertEquals(0f, JsonUtils.getFloat("missing", obj))
        assertEquals(0f, JsonUtils.getFloat("wrongType", obj))
    }

    @Test
    fun testAddJsonWithNullValue() {
        val obj = JsonObject()
        JsonUtils.addJson(obj, "field", null)
        assertFalse(obj.has("field"))
    }

    @Test
    fun testAddJsonWithEmptyObject() {
        val obj = JsonObject()
        JsonUtils.addJson(obj, "field", JsonObject())
        assertFalse(obj.has("field"))
    }

    @Test
    fun testAddJsonWithNonEmptyObject() {
        val obj = JsonObject()
        val value = JsonObject().apply { addProperty("inner", "val") }
        JsonUtils.addJson(obj, "field", value)
        assertTrue(obj.has("field"))
        assertEquals("val", obj.getAsJsonObject("field").get("inner").asString)
    }

    @Test
    fun testGetJsonArray() {
        val obj = JsonObject()
        val arr = JsonArray()
        arr.add("item")
        obj.add("arr", arr)
        obj.add("nullVal", JsonNull.INSTANCE)
        obj.add("wrongType", JsonObject())

        assertEquals(arr, JsonUtils.getJsonArray("arr", obj))
        assertEquals(JsonArray(), JsonUtils.getJsonArray("nullVal", obj))
        assertEquals(JsonArray(), JsonUtils.getJsonArray("missing", obj))
        assertEquals(JsonArray(), JsonUtils.getJsonArray("wrongType", obj))
    }

    @Test
    fun testGetJsonObject() {
        val obj = JsonObject()
        val innerObj = JsonObject()
        innerObj.addProperty("inner", "val")
        obj.add("obj", innerObj)
        obj.add("nullVal", JsonNull.INSTANCE)
        val arr = JsonArray()
        obj.add("wrongType", arr)

        assertEquals(innerObj, JsonUtils.getJsonObject("obj", obj))
        assertEquals(JsonObject(), JsonUtils.getJsonObject("nullVal", obj))
        assertEquals(JsonObject(), JsonUtils.getJsonObject("missing", obj))
        assertEquals(JsonObject(), JsonUtils.getJsonObject("wrongType", obj))
    }

    @Test
    fun testTypeMismatchesAreQuiet() {
        val obj = JsonObject()
        obj.add("wrongType", JsonObject())
        val array = JsonArray()
        array.add(JsonObject())
        obj.add("wrongArr", array)

        JsonUtils.getInt("wrongType", obj)
        JsonUtils.getFloat("wrongType", obj)
        JsonUtils.getString(array, 0)
        JsonUtils.getJsonArray("wrongType", obj)
        JsonUtils.getJsonObject("wrongArr", obj)
        JsonUtils.getLong("wrongType", obj)
        JsonUtils.getBoolean("wrongType", obj)

        // #16652: accessors type-check instead of throwing, so the catch is never entered.
        verify(exactly = 0) { Log.isLoggable(any(), any()) }
        verify(exactly = 0) { Log.d(any(), any()) }
        verify(exactly = 0) { Log.d(any(), any(), any()) }
    }

    @Test
    fun testExtractSharedTeamNameParseFailureLogsWarning() {
        val news = News()
        news.id = "test"
        news.viewIn = "not a json array"

        assertEquals("", JsonUtils.extractSharedTeamName(news))

        // malformed server data is unexpected, so it surfaces as a warning (with the throwable),
        // not the quiet DEBUG fallback used for expected type mismatches
        verify(atLeast = 1) { Log.w("JsonUtils", "failed to parse viewIn", any()) }
    }
}

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

class GsonUtilsTest {

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
        assertEquals("value", GsonUtils.getString("key", jsonObject))
    }

    @Test
    fun testGetStringWithJsonNull() {
        val jsonObject = JsonObject()
        jsonObject.add("key", JsonNull.INSTANCE)
        assertEquals("", GsonUtils.getString("key", jsonObject))
    }

    @Test
    fun testGetStringWithMissingKey() {
        val jsonObject = JsonObject()
        assertEquals("", GsonUtils.getString("missing", jsonObject))
    }

    @Test
    fun testGetBoolean() {
        val jsonObject = JsonObject()
        jsonObject.addProperty("flagTrue", true)
        jsonObject.addProperty("flagFalse", false)

        assertEquals(true, GsonUtils.getBoolean("flagTrue", jsonObject))
        assertEquals(false, GsonUtils.getBoolean("flagFalse", jsonObject))
        assertEquals(false, GsonUtils.getBoolean("missing", jsonObject))
    }

    @Test
    fun testGetInt() {
        val obj = JsonObject()
        obj.addProperty("num", 42)
        obj.addProperty("strNum", "42")
        obj.addProperty("empty", "")
        obj.add("nullVal", JsonNull.INSTANCE)
        obj.add("wrongType", JsonObject())

        assertEquals(42, GsonUtils.getInt("num", obj))
        assertEquals(42, GsonUtils.getInt("strNum", obj))
        assertEquals(0, GsonUtils.getInt("empty", obj))
        assertEquals(0, GsonUtils.getInt("nullVal", obj))
        assertEquals(0, GsonUtils.getInt("missing", obj))
        assertEquals(0, GsonUtils.getInt("wrongType", obj))
    }

    @Test
    fun testGetFloat() {
        val obj = JsonObject()
        obj.addProperty("num", 42.5f)
        obj.addProperty("strNum", "42.5")
        obj.addProperty("empty", "")
        obj.add("nullVal", JsonNull.INSTANCE)
        obj.add("wrongType", JsonObject())

        assertEquals(42.5f, GsonUtils.getFloat("num", obj))
        assertEquals(42.5f, GsonUtils.getFloat("strNum", obj))
        assertEquals(0f, GsonUtils.getFloat("empty", obj))
        assertEquals(0f, GsonUtils.getFloat("nullVal", obj))
        assertEquals(0f, GsonUtils.getFloat("missing", obj))
        assertEquals(0f, GsonUtils.getFloat("wrongType", obj))
    }

    @Test
    fun testAddJsonWithNullValue() {
        val obj = JsonObject()
        GsonUtils.addJson(obj, "field", null)
        assertFalse(obj.has("field"))
    }

    @Test
    fun testAddJsonWithEmptyObject() {
        val obj = JsonObject()
        GsonUtils.addJson(obj, "field", JsonObject())
        assertFalse(obj.has("field"))
    }

    @Test
    fun testAddJsonWithNonEmptyObject() {
        val obj = JsonObject()
        val value = JsonObject().apply { addProperty("inner", "val") }
        GsonUtils.addJson(obj, "field", value)
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

        assertEquals(arr, GsonUtils.getJsonArray("arr", obj))
        assertEquals(JsonArray(), GsonUtils.getJsonArray("nullVal", obj))
        assertEquals(JsonArray(), GsonUtils.getJsonArray("missing", obj))
        assertEquals(JsonArray(), GsonUtils.getJsonArray("wrongType", obj))
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

        assertEquals(innerObj, GsonUtils.getJsonObject("obj", obj))
        assertEquals(JsonObject(), GsonUtils.getJsonObject("nullVal", obj))
        assertEquals(JsonObject(), GsonUtils.getJsonObject("missing", obj))
        assertEquals(JsonObject(), GsonUtils.getJsonObject("wrongType", obj))
    }

    @Test
    fun testTypeMismatchesAreQuiet() {
        val obj = JsonObject()
        obj.add("wrongType", JsonObject())
        val array = JsonArray()
        array.add(JsonObject())
        obj.add("wrongArr", array)

        GsonUtils.getInt("wrongType", obj)
        GsonUtils.getFloat("wrongType", obj)
        GsonUtils.getString(array, 0)
        GsonUtils.getJsonArray("wrongType", obj)
        GsonUtils.getJsonObject("wrongArr", obj)
        GsonUtils.getLong("wrongType", obj)
        GsonUtils.getBoolean("wrongType", obj)

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

        assertEquals("", GsonUtils.extractSharedTeamName(news))

        // malformed server data is unexpected, so it surfaces as a warning (with the throwable),
        // not the quiet DEBUG fallback used for expected type mismatches
        verify(atLeast = 1) { Log.w("GsonUtils", "failed to parse viewIn", any()) }
    }
}

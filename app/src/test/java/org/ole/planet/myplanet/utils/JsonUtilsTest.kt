package org.ole.planet.myplanet.utils

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonUtilsTest {

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
    fun testGetJsonArray() {
        val obj = JsonObject()
        val arr = com.google.gson.JsonArray()
        arr.add("item")
        obj.add("arr", arr)
        obj.add("nullVal", JsonNull.INSTANCE)
        obj.add("wrongType", JsonObject())

        assertEquals(arr, JsonUtils.getJsonArray("arr", obj))
        assertEquals(com.google.gson.JsonArray(), JsonUtils.getJsonArray("nullVal", obj))
        assertEquals(com.google.gson.JsonArray(), JsonUtils.getJsonArray("missing", obj))
        assertEquals(com.google.gson.JsonArray(), JsonUtils.getJsonArray("wrongType", obj))
    }

    @Test
    fun testGetJsonObject() {
        val obj = JsonObject()
        val innerObj = JsonObject()
        innerObj.addProperty("inner", "val")
        obj.add("obj", innerObj)
        obj.add("nullVal", JsonNull.INSTANCE)
        val arr = com.google.gson.JsonArray()
        obj.add("wrongType", arr)

        assertEquals(innerObj, JsonUtils.getJsonObject("obj", obj))
        assertEquals(JsonObject(), JsonUtils.getJsonObject("nullVal", obj))
        assertEquals(JsonObject(), JsonUtils.getJsonObject("missing", obj))
        assertEquals(JsonObject(), JsonUtils.getJsonObject("wrongType", obj))
    }
}

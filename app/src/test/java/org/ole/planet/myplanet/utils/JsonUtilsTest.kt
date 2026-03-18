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
}

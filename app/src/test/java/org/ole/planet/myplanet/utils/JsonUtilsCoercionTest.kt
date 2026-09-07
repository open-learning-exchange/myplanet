package org.ole.planet.myplanet.utils

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Pins every JsonUtils coercion so #16652 can be verified as behaviour-preserving. */
class JsonUtilsCoercionTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkAll()

    private fun obj(build: JsonObject.() -> Unit) = JsonObject().apply(build)

    @Test
    fun getBoolean_coercions() {
        assertEquals(true, JsonUtils.getBoolean("k", obj { addProperty("k", true) }))
        assertEquals(false, JsonUtils.getBoolean("k", obj { addProperty("k", false) }))
        assertEquals(true, JsonUtils.getBoolean("k", obj { addProperty("k", "true") }))
        assertEquals(false, JsonUtils.getBoolean("k", obj { addProperty("k", "yes") }))
        assertEquals(false, JsonUtils.getBoolean("k", obj { addProperty("k", 1) }))
        assertEquals(false, JsonUtils.getBoolean("k", obj { add("k", JsonObject()) }))
        assertEquals(false, JsonUtils.getBoolean("k", obj { add("k", JsonArray()) }))
        assertEquals(false, JsonUtils.getBoolean("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals(false, JsonUtils.getBoolean("missing", obj { addProperty("k", true) }))
        assertEquals(false, JsonUtils.getBoolean("k", null))
    }

    @Test
    fun getLong_coercions() {
        assertEquals(42L, JsonUtils.getLong("k", obj { addProperty("k", 42L) }))
        assertEquals(42L, JsonUtils.getLong("k", obj { addProperty("k", "42") }))
        assertEquals(0L, JsonUtils.getLong("k", obj { addProperty("k", "abc") }))
        assertEquals(0L, JsonUtils.getLong("k", obj { addProperty("k", "") }))
        assertEquals(0L, JsonUtils.getLong("k", obj { addProperty("k", true) }))
        assertEquals(0L, JsonUtils.getLong("k", obj { add("k", JsonObject()) }))
        assertEquals(0L, JsonUtils.getLong("k", obj { add("k", JsonArray()) }))
        assertEquals(0L, JsonUtils.getLong("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals(0L, JsonUtils.getLong("missing", obj { addProperty("k", 1) }))
        assertEquals(0L, JsonUtils.getLong("k", null))
    }

    @Test
    fun getInt_coercions() {
        assertEquals(7, JsonUtils.getInt("k", obj { addProperty("k", 7) }))
        assertEquals(3, JsonUtils.getInt("k", obj { addProperty("k", 3.7) }))
        assertEquals(7, JsonUtils.getInt("k", obj { addProperty("k", "7") }))
        assertEquals(0, JsonUtils.getInt("k", obj { addProperty("k", "3.7") }))
        assertEquals(0, JsonUtils.getInt("k", obj { addProperty("k", "abc") }))
        assertEquals(0, JsonUtils.getInt("k", obj { addProperty("k", "") }))
        assertEquals(0, JsonUtils.getInt("k", obj { add("k", JsonObject()) }))
        assertEquals(0, JsonUtils.getInt("k", obj { add("k", JsonArray()) }))
        assertEquals(0, JsonUtils.getInt("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals(0, JsonUtils.getInt("missing", obj { addProperty("k", 1) }))
        assertEquals(0, JsonUtils.getInt("k", null))
    }

    @Test
    fun getFloat_coercions() {
        assertEquals(1.5f, JsonUtils.getFloat("k", obj { addProperty("k", 1.5f) }), 0f)
        assertEquals(1.5f, JsonUtils.getFloat("k", obj { addProperty("k", "1.5") }), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", obj { addProperty("k", "abc") }), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", obj { addProperty("k", "") }), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", obj { add("k", JsonObject()) }), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", obj { add("k", JsonArray()) }), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", obj { add("k", JsonNull.INSTANCE) }), 0f)
        assertEquals(0f, JsonUtils.getFloat("missing", obj { addProperty("k", 1) }), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", null), 0f)
    }

    @Test
    fun getStringByField_coercions() {
        assertEquals("v", JsonUtils.getString("k", obj { addProperty("k", "v") }))
        assertEquals("", JsonUtils.getString("k", obj { addProperty("k", 7) }))
        assertEquals("", JsonUtils.getString("k", obj { addProperty("k", true) }))
        assertEquals("", JsonUtils.getString("k", obj { add("k", JsonObject()) }))
        assertEquals("", JsonUtils.getString("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals("", JsonUtils.getString("missing", obj { addProperty("k", "v") }))
        assertEquals("", JsonUtils.getString("k", null))
    }

    @Test
    fun getStringByIndex_coercions() {
        val arr = JsonArray().apply {
            add("v"); add(7); add(true); add(JsonObject()); add(JsonNull.INSTANCE)
        }
        assertEquals("v", JsonUtils.getString(arr, 0))
        assertEquals("7", JsonUtils.getString(arr, 1))
        assertEquals("true", JsonUtils.getString(arr, 2))
        assertEquals("", JsonUtils.getString(arr, 3))
        assertEquals("", JsonUtils.getString(arr, 4))
        assertEquals("", JsonUtils.getString(arr, 99))
        assertEquals("", JsonUtils.getString(arr, -1))
        assertEquals("", JsonUtils.getString(JsonArray(), 0))
    }

    @Test
    fun containerAccessors_coercions() {
        assertEquals(0, JsonUtils.getJsonArray("k", obj { addProperty("k", "v") }).size())
        assertEquals(2, JsonUtils.getJsonArray("k", obj { add("k", JsonArray().apply { add(1); add(2) }) }).size())
        assertEquals(0, JsonUtils.getJsonArray("missing", obj { }).size())
        assertEquals(0, JsonUtils.getJsonObject("k", obj { addProperty("k", "v") }).size())
        assertEquals(1, JsonUtils.getJsonObject("k", obj { add("k", obj { addProperty("a", 1) }) }).size())
        assertEquals(0, JsonUtils.getJsonObject("missing", obj { }).size())
    }
}

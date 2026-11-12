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

/** Pins every GsonUtils coercion so #16652 can be verified as behaviour-preserving. */
class GsonUtilsCoercionTest {

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
        assertEquals(true, GsonUtils.getBoolean("k", obj { addProperty("k", true) }))
        assertEquals(false, GsonUtils.getBoolean("k", obj { addProperty("k", false) }))
        assertEquals(true, GsonUtils.getBoolean("k", obj { addProperty("k", "true") }))
        assertEquals(false, GsonUtils.getBoolean("k", obj { addProperty("k", "yes") }))
        assertEquals(false, GsonUtils.getBoolean("k", obj { addProperty("k", 1) }))
        assertEquals(false, GsonUtils.getBoolean("k", obj { add("k", JsonObject()) }))
        assertEquals(false, GsonUtils.getBoolean("k", obj { add("k", JsonArray()) }))
        assertEquals(false, GsonUtils.getBoolean("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals(false, GsonUtils.getBoolean("missing", obj { addProperty("k", true) }))
        assertEquals(false, GsonUtils.getBoolean("k", null))
    }

    @Test
    fun getLong_coercions() {
        assertEquals(42L, GsonUtils.getLong("k", obj { addProperty("k", 42L) }))
        assertEquals(42L, GsonUtils.getLong("k", obj { addProperty("k", "42") }))
        assertEquals(0L, GsonUtils.getLong("k", obj { addProperty("k", "abc") }))
        assertEquals(0L, GsonUtils.getLong("k", obj { addProperty("k", "") }))
        assertEquals(0L, GsonUtils.getLong("k", obj { addProperty("k", true) }))
        assertEquals(0L, GsonUtils.getLong("k", obj { add("k", JsonObject()) }))
        assertEquals(0L, GsonUtils.getLong("k", obj { add("k", JsonArray()) }))
        assertEquals(0L, GsonUtils.getLong("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals(0L, GsonUtils.getLong("missing", obj { addProperty("k", 1) }))
        assertEquals(0L, GsonUtils.getLong("k", null))
    }

    @Test
    fun getInt_coercions() {
        assertEquals(7, GsonUtils.getInt("k", obj { addProperty("k", 7) }))
        assertEquals(3, GsonUtils.getInt("k", obj { addProperty("k", 3.7) }))
        assertEquals(7, GsonUtils.getInt("k", obj { addProperty("k", "7") }))
        assertEquals(0, GsonUtils.getInt("k", obj { addProperty("k", "3.7") }))
        assertEquals(0, GsonUtils.getInt("k", obj { addProperty("k", "abc") }))
        assertEquals(0, GsonUtils.getInt("k", obj { addProperty("k", "") }))
        assertEquals(0, GsonUtils.getInt("k", obj { add("k", JsonObject()) }))
        assertEquals(0, GsonUtils.getInt("k", obj { add("k", JsonArray()) }))
        assertEquals(0, GsonUtils.getInt("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals(0, GsonUtils.getInt("missing", obj { addProperty("k", 1) }))
        assertEquals(0, GsonUtils.getInt("k", null))
    }

    @Test
    fun getFloat_coercions() {
        assertEquals(1.5f, GsonUtils.getFloat("k", obj { addProperty("k", 1.5f) }), 0f)
        assertEquals(1.5f, GsonUtils.getFloat("k", obj { addProperty("k", "1.5") }), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", obj { addProperty("k", "abc") }), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", obj { addProperty("k", "") }), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", obj { add("k", JsonObject()) }), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", obj { add("k", JsonArray()) }), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", obj { add("k", JsonNull.INSTANCE) }), 0f)
        assertEquals(0f, GsonUtils.getFloat("missing", obj { addProperty("k", 1) }), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", null), 0f)
    }

    @Test
    fun getStringByField_coercions() {
        assertEquals("v", GsonUtils.getString("k", obj { addProperty("k", "v") }))
        assertEquals("", GsonUtils.getString("k", obj { addProperty("k", 7) }))
        assertEquals("", GsonUtils.getString("k", obj { addProperty("k", true) }))
        assertEquals("", GsonUtils.getString("k", obj { add("k", JsonObject()) }))
        assertEquals("", GsonUtils.getString("k", obj { add("k", JsonNull.INSTANCE) }))
        assertEquals("", GsonUtils.getString("missing", obj { addProperty("k", "v") }))
        assertEquals("", GsonUtils.getString("k", null))
    }

    @Test
    fun getStringByIndex_coercions() {
        val arr = JsonArray().apply {
            add("v"); add(7); add(true); add(JsonObject()); add(JsonNull.INSTANCE)
        }
        assertEquals("v", GsonUtils.getString(arr, 0))
        assertEquals("7", GsonUtils.getString(arr, 1))
        assertEquals("true", GsonUtils.getString(arr, 2))
        assertEquals("", GsonUtils.getString(arr, 3))
        assertEquals("", GsonUtils.getString(arr, 4))
        assertEquals("", GsonUtils.getString(arr, 99))
        assertEquals("", GsonUtils.getString(arr, -1))
        assertEquals("", GsonUtils.getString(JsonArray(), 0))
    }

    @Test
    fun containerAccessors_coercions() {
        assertEquals(0, GsonUtils.getJsonArray("k", obj { addProperty("k", "v") }).size())
        assertEquals(2, GsonUtils.getJsonArray("k", obj { add("k", JsonArray().apply { add(1); add(2) }) }).size())
        assertEquals(0, GsonUtils.getJsonArray("missing", obj { }).size())
        assertEquals(0, GsonUtils.getJsonObject("k", obj { addProperty("k", "v") }).size())
        assertEquals(1, GsonUtils.getJsonObject("k", obj { add("k", obj { addProperty("a", 1) }) }).size())
        assertEquals(0, GsonUtils.getJsonObject("missing", obj { }).size())
    }
}

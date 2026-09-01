package org.ole.planet.myplanet.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for #16652.
 *
 * Deliberately stubs nothing: no mockkStatic(Log::class), no Robolectric. Every case below
 * takes JsonUtils' wrong-type fallback path, which used to log from inside safeGet's catch
 * block. android.util.Log throws "not mocked" in a plain JVM test, and because that throw
 * came from inside the catch it escaped safeGet entirely -- so a "safe" accessor propagated
 * instead of returning its default.
 *
 * DO NOT add Log stubbing to this class. Its whole purpose is to run without it; stubbing
 * Log here would make it pass against the bug it exists to catch.
 */
class JsonUtilsNoLogStubTest {

    private fun withObjectValue() = JsonObject().apply { add("k", JsonObject()) }
    private fun withArrayValue() = JsonObject().apply { add("k", JsonArray()) }

    @Test
    fun getBoolean_objectValueReturnsFalse() {
        assertEquals(false, JsonUtils.getBoolean("k", withObjectValue()))
        assertEquals(false, JsonUtils.getBoolean("k", withArrayValue()))
    }

    @Test
    fun getLong_objectValueReturnsZero() {
        assertEquals(0L, JsonUtils.getLong("k", withObjectValue()))
        assertEquals(0L, JsonUtils.getLong("k", JsonObject().apply { addProperty("k", "abc") }))
    }

    @Test
    fun getInt_objectValueReturnsZero() {
        assertEquals(0, JsonUtils.getInt("k", withObjectValue()))
        assertEquals(0, JsonUtils.getInt("k", JsonObject().apply { addProperty("k", "abc") }))
    }

    @Test
    fun getFloat_objectValueReturnsZero() {
        assertEquals(0f, JsonUtils.getFloat("k", withObjectValue()), 0f)
        assertEquals(0f, JsonUtils.getFloat("k", JsonObject().apply { addProperty("k", "abc") }), 0f)
    }

    @Test
    fun getString_objectValueReturnsEmpty() {
        assertEquals("", JsonUtils.getString("k", withObjectValue()))
        assertEquals("", JsonUtils.getString(JsonArray().apply { add(JsonObject()) }, 0))
        assertEquals("", JsonUtils.getString(JsonArray(), 99))
    }

    /**
     * The exact shape from #16609: a true flag alongside a non-boolean sibling. Before the
     * fix the sibling's throw escaped the filter and the caller's own catch swallowed it,
     * discarding every condition.
     */
    @Test
    fun mixedValidAndMalformedValues_goodValuesSurvive() {
        val conditions = JsonObject().apply {
            addProperty("diabetes", true)
            add("asthma", JsonObject())
        }
        val flagged = conditions.keySet().filter { JsonUtils.getBoolean(it, conditions) }
        assertEquals(listOf("diabetes"), flagged)
    }
}

package org.ole.planet.myplanet.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for #16652: every case takes GsonUtils' wrong-type fallback path.
 *
 * DO NOT add Log stubbing here. This class must run without it -- stubbing Log would make
 * it pass against the bug it exists to catch.
 */
class GsonUtilsNoLogStubTest {

    private fun withObjectValue() = JsonObject().apply { add("k", JsonObject()) }
    private fun withArrayValue() = JsonObject().apply { add("k", JsonArray()) }

    @Test
    fun getBoolean_objectValueReturnsFalse() {
        assertEquals(false, GsonUtils.getBoolean("k", withObjectValue()))
        assertEquals(false, GsonUtils.getBoolean("k", withArrayValue()))
    }

    @Test
    fun getLong_objectValueReturnsZero() {
        assertEquals(0L, GsonUtils.getLong("k", withObjectValue()))
        assertEquals(0L, GsonUtils.getLong("k", JsonObject().apply { addProperty("k", "abc") }))
    }

    @Test
    fun getInt_objectValueReturnsZero() {
        assertEquals(0, GsonUtils.getInt("k", withObjectValue()))
        assertEquals(0, GsonUtils.getInt("k", JsonObject().apply { addProperty("k", "abc") }))
    }

    @Test
    fun getFloat_objectValueReturnsZero() {
        assertEquals(0f, GsonUtils.getFloat("k", withObjectValue()), 0f)
        assertEquals(0f, GsonUtils.getFloat("k", JsonObject().apply { addProperty("k", "abc") }), 0f)
    }

    @Test
    fun getString_objectValueReturnsEmpty() {
        assertEquals("", GsonUtils.getString("k", withObjectValue()))
        assertEquals("", GsonUtils.getString(JsonArray().apply { add(JsonObject()) }, 0))
        assertEquals("", GsonUtils.getString(JsonArray(), 99))
    }

    /** The shape from #16609: a true flag alongside a non-boolean sibling. */
    @Test
    fun mixedValidAndMalformedValues_goodValuesSurvive() {
        val conditions = JsonObject().apply {
            addProperty("diabetes", true)
            add("asthma", JsonObject())
        }
        val flagged = conditions.keySet().filter { GsonUtils.getBoolean(it, conditions) }
        assertEquals(listOf("diabetes"), flagged)
    }
}

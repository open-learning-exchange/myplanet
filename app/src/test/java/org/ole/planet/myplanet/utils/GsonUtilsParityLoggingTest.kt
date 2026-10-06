package org.ole.planet.myplanet.utils

import android.app.Application
import android.util.Log
import com.google.gson.JsonObject as GsonJsonObject
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The fallback path both helpers share: a read that throws returns the getter's default and logs
 * one DEBUG line under the helper's own tag (`GsonUtils` / `KJson`), and only when that tag is
 * loggable at DEBUG.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GsonUtilsParityLoggingTest {

    private val gsonObject = mockk<GsonJsonObject>().also { every { it.get(any<String>()) } throws IllegalStateException("boom") }
    private val kotlinxObject = mockk<JsonObject>().also { every { it[any<String>()] } throws IllegalStateException("boom") }

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    private fun readAll(gson: Boolean): List<Any> = if (gson) {
        listOf(
            GsonUtils.getString("k", gsonObject), GsonUtils.getInt("k", gsonObject), GsonUtils.getLong("k", gsonObject),
            GsonUtils.getFloat("k", gsonObject), GsonUtils.getBoolean("k", gsonObject),
            GsonUtils.getJsonObject("k", gsonObject).size(), GsonUtils.getJsonArray("k", gsonObject).size(),
        )
    } else {
        listOf(
            KJson.getString("k", kotlinxObject), KJson.getInt("k", kotlinxObject), KJson.getLong("k", kotlinxObject),
            KJson.getFloat("k", kotlinxObject), KJson.getBoolean("k", kotlinxObject),
            KJson.getJsonObject("k", kotlinxObject).size, KJson.getJsonArray("k", kotlinxObject).size,
        )
    }

    @Test
    fun `a throwing read falls back to the default and logs once per call at DEBUG`() {
        ShadowLog.setLoggable("GsonUtils", Log.DEBUG)
        ShadowLog.setLoggable("KJson", Log.DEBUG)
        val defaults = listOf<Any>("", 0, 0L, 0f, false, 0, 0)

        assertEquals(defaults, readAll(gson = true))
        assertEquals(defaults, readAll(gson = false))

        val gsonLogs = ShadowLog.getLogsForTag("GsonUtils")
        val kjsonLogs = ShadowLog.getLogsForTag("KJson")
        assertEquals(7, gsonLogs.size)
        assertEquals(gsonLogs.map { it.type to it.msg }, kjsonLogs.map { it.type to it.msg })
        assertEquals(Log.DEBUG, kjsonLogs.first().type)
        assertEquals("expected type mismatch, using fallback: boom", kjsonLogs.first().msg)
    }

    @Test
    fun `nothing is logged when the tag is not loggable at DEBUG`() {
        ShadowLog.setLoggable("GsonUtils", Log.INFO)
        ShadowLog.setLoggable("KJson", Log.INFO)
        readAll(gson = true)
        readAll(gson = false)
        assertEquals(0, ShadowLog.getLogsForTag("GsonUtils").size)
        assertEquals(0, ShadowLog.getLogsForTag("KJson").size)
    }
}

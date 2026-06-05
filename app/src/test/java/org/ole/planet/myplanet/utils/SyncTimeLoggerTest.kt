package org.ole.planet.myplanet.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

class SyncTimeLoggerTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkAll()
        // Clean up singleton state
        val field: Field = SyncTimeLogger::class.java.getDeclaredField("apiCallTimes")
        field.isAccessible = true
        val apiCallTimes = field.get(SyncTimeLogger) as ConcurrentHashMap<String, MutableList<SyncTimeLogger.ApiCallLog>>
        apiCallTimes.clear()
    }

    @Test
    fun testExtractProcessNameViaLogApiCall() {
        SyncTimeLogger.startLogging()

        SyncTimeLogger.logApiCall("courses", 100, true)
        SyncTimeLogger.logApiCall("api/v1/courses", 100, true)
        SyncTimeLogger.logApiCall("api/v1/courses?limit=10", 100, true)
        SyncTimeLogger.logApiCall("api/v1/courses/", 100, true)
        SyncTimeLogger.logApiCall("api//v1//courses//", 100, true)

        val field: Field = SyncTimeLogger::class.java.getDeclaredField("apiCallTimes")
        field.isAccessible = true
        val apiCallTimes = field.get(SyncTimeLogger) as ConcurrentHashMap<String, MutableList<SyncTimeLogger.ApiCallLog>>

        val keys = apiCallTimes.keys().toList()

        assertEquals(1, keys.size)
        assertTrue(keys.contains("Courses"))

        // Verify another different test case
        SyncTimeLogger.logApiCall("api/v2/users?limit=5", 100, true)
        val keys2 = apiCallTimes.keys().toList()
        assertEquals(2, keys2.size)
        assertTrue(keys2.contains("Users"))
    }
}

package org.ole.planet.myplanet.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTimeLoggerTest {

    @Test
    fun testGenerateSummary() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.d(any(), any()) } returns 0

        var currentTime = 1000L
        val timeProvider = mockk<TimeProvider> {
            every { now() } answers { currentTime }
        }

        val testDispatcher = UnconfinedTestDispatcher()
        val logger = SyncTimeLogger(
            timeProvider = timeProvider,
            appScope = CoroutineScope(testDispatcher),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            sharedPrefManager = mockk(relaxed = true),
            serverUrlMapper = mockk(relaxed = true),
            diagnosticsRepository = mockk(relaxed = true),
            serverReachabilityProvider = mockk(relaxed = true)
        )

        logger.startLogging()

        currentTime = 1200L
        logger.logApiCall("http://server/api/v1/courses", duration = 300L, success = true, itemsReturned = 5)

        currentTime = 1500L
        logger.logApiCall("http://server/api/v1/courses", duration = 200L, success = false, itemsReturned = 0)

        currentTime = 1700L
        logger.logDbOperation("INSERT", "CourseModel", duration = 400L, itemCount = 5)

        currentTime = 2000L
        logger.stopLogging()

        val summary = logger.generateSummary()

        assertTrue(summary.contains("Total API calls: 2 (Success: 1, Failed: 1)"))
        assertTrue(summary.contains("Total API time: 500ms (50.0% of total sync)"))
        assertTrue(summary.contains("Total Db operations: 1"))
        assertTrue(summary.contains("Total Db time: 400ms (40.0% of total sync)"))
        assertTrue(summary.contains("Total items processed: 5"))
        assertTrue(summary.contains("Network time: 50.0%"))
        assertTrue(summary.contains("Database time: 40.0%"))
        assertTrue(summary.contains("Other processing: 10.0%"))
    }

    @Test
    fun testEndProcess() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns false

        var currentTime = 1000L
        val timeProvider = mockk<TimeProvider> {
            every { now() } answers { currentTime }
        }

        val testDispatcher = UnconfinedTestDispatcher()
        val logger = SyncTimeLogger(
            timeProvider = timeProvider,
            appScope = CoroutineScope(testDispatcher),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            sharedPrefManager = mockk(relaxed = true),
            serverUrlMapper = mockk(relaxed = true),
            diagnosticsRepository = mockk(relaxed = true),
            serverReachabilityProvider = mockk(relaxed = true)
        )

        logger.startLogging()

        // Call endProcess for a process that was never started (missing start key)
        currentTime = 1200L
        logger.endProcess("nonExistentProcess", itemCount = 5)

        // Call startProcess and endProcess for a valid process
        currentTime = 1500L
        logger.startProcess("validProcess")

        currentTime = 2000L
        logger.endProcess("validProcess", itemCount = 10)

        currentTime = 2500L
        logger.stopLogging()

        val summary = logger.generateSummary()
        assertTrue(summary.contains("validProcess"))
        assertTrue(!summary.contains("nonExistentProcess"))
    }

    @Test
    fun testExtractProcessName() {
        assertEquals("Courses", SyncTimeLogger.extractProcessName("courses"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api/v1/courses"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api/v1/courses?limit=10"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api/v1/courses/"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api//v1//courses//"))
        assertEquals("Api", SyncTimeLogger.extractProcessName("api"))
        assertEquals("Unknown", SyncTimeLogger.extractProcessName(""))
    }
}

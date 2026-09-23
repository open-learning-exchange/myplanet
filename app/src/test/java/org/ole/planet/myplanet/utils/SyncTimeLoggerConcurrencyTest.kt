package org.ole.planet.myplanet.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTimeLoggerConcurrencyTest {

    @Test
    fun testConcurrentApiAndDbLogging() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.d(any(), any()) } returns 0

        val timeProvider = mockk<TimeProvider> {
            every { now() } returns 1000L
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

        val threadCount = 10
        val iterationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                for (j in 0 until iterationsPerThread) {
                    logger.logApiCall("http://server/api/v1/courses", duration = 10L, success = true, itemsReturned = 1)
                    logger.logDbOperation("INSERT", "CourseModel", duration = 5L, itemCount = 1)
                    if (j % 10 == 0) {
                        logger.generateSummary()
                    }
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue("Concurrent execution timed out", finished)

        val summary = logger.generateSummary()
        val expectedCalls = threadCount * iterationsPerThread
        assertTrue(summary.contains("Total API calls: $expectedCalls"))
        assertTrue(summary.contains("Total Db operations: $expectedCalls"))
    }
}

package org.ole.planet.myplanet.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryUtilsTest {

    @Test
    fun retryReturnsSuccessImmediately() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 3,
            block = {
                attempts++
                "Success"
            }
        )
        assertEquals("Success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun retryRetriesUntilSuccess() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 3,
            block = {
                attempts++
                if (attempts < 2) null else "Success"
            }
        )
        assertEquals("Success", result)
        assertEquals(2, attempts)
    }

    @Test
    fun retryFailsAfterMaxAttempts() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 3,
            block = {
                attempts++
                null
            }
        )
        assertEquals(null, result)
        assertEquals(3, attempts)
    }

    @Test
    fun retryRespectsBackoffTiming() = runBlocking {
        val delays = mutableListOf<Long>()
        var attempts = 0

        RetryUtils.retry(
            maxAttempts = 3,
            initialDelay = 100L,
            multiplier = 2.0,
            jitter = 0L,
            delayHook = { delays.add(it) },
            block = {
                attempts++
                null
            }
        )

        // Attempts: 1 (fail), delay 100 -> 2 (fail), delay 200 -> 3 (fail)
        // delays should be [100, 200]
        assertEquals(2, delays.size)
        assertEquals(100L, delays[0])
        assertEquals(200L, delays[1])
    }

    @Test
    fun retryRespectsMaxDelay() = runBlocking {
        val delays = mutableListOf<Long>()

        RetryUtils.retry(
            maxAttempts = 4,
            initialDelay = 100L,
            maxDelay = 150L,
            multiplier = 2.0,
            jitter = 0L,
            delayHook = { delays.add(it) },
            block = { null }
        )

        // 1: fail, delay 100. next = 200 > 150 -> 150.
        // 2: fail, delay 150. next = 300 > 150 -> 150.
        // 3: fail, delay 150.
        // 4: fail.

        assertEquals(3, delays.size)
        assertEquals(100L, delays[0])
        assertEquals(150L, delays[1])
        assertEquals(150L, delays[2])
    }

    @Test
    fun retryHandlesExceptionAndErrorClassification() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 3,
            shouldRetry = { _, ex -> ex is IOException },
            block = {
                attempts++
                if (attempts == 1) throw IOException("Retry me")
                else throw RuntimeException("Do not retry")
            }
        )

        // 1: throws IOException. shouldRetry(null, IOException) -> true. Retry.
        // 2: throws RuntimeException. shouldRetry(null, RuntimeException) -> false. Return null.

        assertEquals(null, result)
        assertEquals(2, attempts)
    }
}

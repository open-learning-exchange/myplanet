package org.ole.planet.myplanet.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryUtilsTest {

    @Test
    fun `retry succeeds on first attempt`() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(maxAttempts = 3) {
            attempts++
            "Success"
        }
        assertEquals("Success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retry succeeds after failures`() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 5,
            initialDelay = 10L // Minimal delay for test speed
        ) {
            attempts++
            if (attempts < 3) throw IOException("Fail")
            "Success"
        }
        assertEquals("Success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry fails after max attempts`() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 3,
            initialDelay = 10L
        ) {
            attempts++
            throw IOException("Fail")
        }
        assertEquals(null, result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry respects shouldRetry predicate`() = runBlocking {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 5,
            initialDelay = 10L,
            shouldRetry = { _, ex -> ex !is IllegalArgumentException } // Don't retry on IllegalArgumentException
        ) {
            attempts++
            throw IllegalArgumentException("Fatal")
        }
        assertEquals(null, result)
        assertEquals(1, attempts) // Should stop after first attempt
    }
}

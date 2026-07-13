package org.ole.planet.myplanet.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryUtilsTest {

    @Test
    fun `test retry calls block exactly once when it succeeds on first attempt`() = runTest {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 3,
            delayMs = 1000L,
            shouldRetry = { it == null }
        ) {
            attempts++
            "Success"
        }

        assertEquals("Success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `test retry returns null after exhausting maxAttempts when shouldRetry always returns true`() = runTest {
        var attempts = 0
        val result = RetryUtils.retry(
            maxAttempts = 4,
            delayMs = 10L,
            shouldRetry = { true }
        ) {
            attempts++
            null
        }

        assertNull(result)
        assertEquals(4, attempts)
    }

    @Test
    fun `test retry respects delayMs using UnconfinedTestDispatcher and advanceTimeBy`() = runTest(UnconfinedTestDispatcher()) {
        var attempts = 0
        val maxAttempts = 3
        val delayMs = 2000L

        val job = launch {
            RetryUtils.retry(
                maxAttempts = maxAttempts,
                delayMs = delayMs,
                shouldRetry = { true }
            ) {
                attempts++
                null
            }
        }

        // Initially attempt is 1 (eager execution due to UnconfinedTestDispatcher)
        assertEquals(1, attempts)

        // Advance time by slightly less than delayMs
        advanceTimeBy(1000L)
        assertEquals(1, attempts) // Still 1

        // Advance to cross the delayMs threshold
        advanceTimeBy(1001L) // 1000 + 1001 = 2001 (> delayMs)
        assertEquals(2, attempts) // Attempt 2

        // Advance another delayMs to reach maxAttempts
        advanceTimeBy(2000L)
        assertEquals(3, attempts) // Attempt 3

        // Further advancing shouldn't do anything
        advanceTimeBy(2000L)
        assertEquals(3, attempts)

        job.cancel()
    }

    @Test
    fun `test retry swallows exception and treats it as failed attempt`() = runTest {
        var attempts = 0
        val result = RetryUtils.retry<String>(
            maxAttempts = 3,
            delayMs = 10L,
            shouldRetry = { it == null }
        ) {
            attempts++
            throw RuntimeException("Test Exception")
        }

        assertNull(result)
        assertEquals(3, attempts)
    }
}

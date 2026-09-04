package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryOperationTest {

    @Test
    fun `recordFailedAttempt updates status to pending when attempts remain below maxAttempts`() {
        val op = RetryOperation().apply {
            attemptCount = 1
            maxAttempts = 5
            status = RetryOperation.STATUS_IN_PROGRESS
        }
        val timestamp = 1_700_000_000_000L

        op.recordFailedAttempt("Network error", 503, timestamp)

        assertEquals(2, op.attemptCount)
        assertEquals(timestamp, op.lastAttemptTime)
        assertEquals("Network error", op.errorMessage)
        assertEquals(503, op.httpCode)
        assertEquals(RetryOperation.STATUS_PENDING, op.status)
        val expectedNextRetryTime = RetryOperation.calculateNextRetryTime(2, timestamp)
        assertEquals(expectedNextRetryTime, op.nextRetryTime)
    }

    @Test
    fun `recordFailedAttempt updates status to abandoned when maxAttempts reached`() {
        val op = RetryOperation().apply {
            attemptCount = 4
            maxAttempts = 5
            status = RetryOperation.STATUS_IN_PROGRESS
        }
        val timestamp = 1_700_000_000_000L

        op.recordFailedAttempt("Fatal error", 500, timestamp)

        assertEquals(5, op.attemptCount)
        assertEquals(timestamp, op.lastAttemptTime)
        assertEquals("Fatal error", op.errorMessage)
        assertEquals(500, op.httpCode)
        assertEquals(RetryOperation.STATUS_ABANDONED, op.status)
    }

    @Test
    fun `recordFailedAttempt boundary check for maxAttempts`() {
        val timestamp = 1_700_000_000_000L

        // Below boundary: attempt 3 -> 4 of max 5 => PENDING
        val opBelowBoundary = RetryOperation().apply {
            attemptCount = 3
            maxAttempts = 5
        }
        opBelowBoundary.recordFailedAttempt("Error", null, timestamp)
        assertEquals(4, opBelowBoundary.attemptCount)
        assertEquals(RetryOperation.STATUS_PENDING, opBelowBoundary.status)

        // At boundary: attempt 4 -> 5 of max 5 => ABANDONED
        val opAtBoundary = RetryOperation().apply {
            attemptCount = 4
            maxAttempts = 5
        }
        opAtBoundary.recordFailedAttempt("Error", null, timestamp)
        assertEquals(5, opAtBoundary.attemptCount)
        assertEquals(RetryOperation.STATUS_ABANDONED, opAtBoundary.status)
    }

    @Test
    fun `calculateNextRetryTime uses exponential backoff from currentTime`() {
        val now = 1_000_000L
        // attempt 1: delay = 30000 * 2 = 60000
        assertEquals(now + 60_000L, RetryOperation.calculateNextRetryTime(1, now))
        // attempt 2: delay = 30000 * 4 = 120000
        assertEquals(now + 120_000L, RetryOperation.calculateNextRetryTime(2, now))
    }
}

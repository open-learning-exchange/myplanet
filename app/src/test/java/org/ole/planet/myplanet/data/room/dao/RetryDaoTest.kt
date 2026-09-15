package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.RetryOperation

@RunWith(AndroidJUnit4::class)
class RetryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var retryDao: RetryDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        retryDao = database.retryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun recordFailedAttempt_resetsStatusToPendingAndCalculatesNextRetryTime_whenBelowMaxAttempts() = runBlocking {
        val initialOp = RetryOperation().apply {
            id = "op1"
            attemptCount = 1
            maxAttempts = 5
            status = RetryOperation.STATUS_IN_PROGRESS
        }
        retryDao.insert(initialOp)

        val timestamp = 1_700_000_000_000L
        val rowsUpdated = retryDao.recordFailedAttempt("op1", "Server error", 500, timestamp)

        assertEquals(1, rowsUpdated)
        val result = retryDao.findById("op1")
        assertNotNull(result)
        assertEquals(2, result!!.attemptCount)
        assertEquals(timestamp, result.lastAttemptTime)
        assertEquals("Server error", result.errorMessage)
        assertEquals(500, result.httpCode)
        assertEquals(RetryOperation.STATUS_PENDING, result.status)

        // Exponential backoff for new attemptCount = 2: delay = 30000 * (1 shl 2) = 120,000
        val expectedNextRetry = timestamp + 120_000L
        assertEquals(expectedNextRetry, result.nextRetryTime)
    }

    @Test
    fun recordFailedAttempt_setsStatusToAbandoned_whenReachingMaxAttemptsBoundary() = runBlocking {
        val initialOp = RetryOperation().apply {
            id = "op2"
            attemptCount = 4
            maxAttempts = 5
            status = RetryOperation.STATUS_IN_PROGRESS
            nextRetryTime = 999L
        }
        retryDao.insert(initialOp)

        val timestamp = 1_700_000_000_000L
        val rowsUpdated = retryDao.recordFailedAttempt("op2", "Fatal error", 400, timestamp)

        assertEquals(1, rowsUpdated)
        val result = retryDao.findById("op2")
        assertNotNull(result)
        assertEquals(5, result!!.attemptCount)
        assertEquals(timestamp, result.lastAttemptTime)
        assertEquals("Fatal error", result.errorMessage)
        assertEquals(400, result.httpCode)
        assertEquals(RetryOperation.STATUS_ABANDONED, result.status)
        assertEquals(999L, result.nextRetryTime) // Preserved on abandoned
    }

    @Test
    fun recordFailedAttempt_capsExponentialBackoffAtMaxDelay() = runBlocking {
        val initialOp = RetryOperation().apply {
            id = "op3"
            attemptCount = 10
            maxAttempts = 20
            status = RetryOperation.STATUS_IN_PROGRESS
        }
        retryDao.insert(initialOp)

        val timestamp = 1_700_000_000_000L
        retryDao.recordFailedAttempt("op3", "Error", null, timestamp)

        val result = retryDao.findById("op3")
        assertNotNull(result)
        assertEquals(11, result!!.attemptCount)
        // Max delay cap is 1,800,000 ms (30 mins)
        val expectedNextRetry = timestamp + 1_800_000L
        assertEquals(expectedNextRetry, result.nextRetryTime)
    }
}

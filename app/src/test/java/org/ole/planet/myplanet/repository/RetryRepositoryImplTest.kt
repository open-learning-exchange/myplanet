package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.utils.TestTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class RetryRepositoryImplTest {
    private lateinit var retryDao: RetryDao
    private lateinit var repository: RetryRepositoryImpl
    private val timeProvider = TestTimeProvider(currentTime = 1_700_000_000_000L)

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Before
    fun setUp() {
        retryDao = mockk(relaxed = true)
        repository = RetryRepositoryImpl(retryDao, timeProvider)
    }

    @Test
    fun `enqueue inserts a created operation`() = runTest {
        val insertedSlot = slot<RetryOperation>()
        coEvery { retryDao.insert(capture(insertedSlot)) } returns Unit

        val retryFailure = RetryFailure("itemId", "test error", 500)
        repository.enqueue(
            "testUploadType", retryFailure, "testPayload", "testEndpoint",
            "POST", "testDbId", "TestClass", "testUserId"
        )

        val op = insertedSlot.captured
        assertEquals("testUploadType", op.uploadType)
        assertEquals("itemId", op.itemId)
        assertEquals("testPayload", op.serializedPayload)
        assertEquals("testEndpoint", op.endpoint)
        assertEquals("POST", op.httpMethod)
        assertEquals("testDbId", op.dbId)
        assertEquals("TestClass", op.modelClassName)
        assertEquals("testUserId", op.userId)
        assertEquals(RetryOperation.STATUS_PENDING, op.status)
        assertEquals(1, op.attemptCount)
        assertEquals(500, op.httpCode)
    }

    @Test
    fun `updateAttempt updates operation fields`() = runTest {
        val operation = RetryOperation().apply { attemptCount = 1; maxAttempts = 5 }
        coEvery { retryDao.findById("opId") } returns operation

        repository.updateAttempt("opId", RetryFailure("itemId", "Test Error", 503))

        assertEquals(2, operation.attemptCount)
        assertEquals("Test Error", operation.errorMessage)
        assertEquals(503, operation.httpCode)
        assert(operation.nextRetryTime > 0)
        coVerify { retryDao.update(operation) }
    }

    @Test
    fun `updateAttempt changes status to abandoned when max attempts reached`() = runTest {
        val operation = RetryOperation().apply {
            attemptCount = 4; maxAttempts = 5; status = RetryOperation.STATUS_PENDING
        }
        coEvery { retryDao.findById("opId") } returns operation

        repository.updateAttempt("opId", RetryFailure("itemId", "Unknown error", null))

        assertEquals(5, operation.attemptCount)
        assertEquals(RetryOperation.STATUS_ABANDONED, operation.status)
        coVerify { retryDao.update(operation) }
    }

    @Test
    fun `updateAttempt does nothing when operation not found`() = runTest {
        coEvery { retryDao.findById("missingId") } returns null

        repository.updateAttempt("missingId", RetryFailure("itemId", "Test Error", 503))

        coVerify(exactly = 0) { retryDao.update(any()) }
    }

    @Test
    fun `markInProgress updates status`() = runTest {
        coEvery { retryDao.markInProgress("opId") } returns 1

        repository.markInProgress("opId")

        coVerify { retryDao.markInProgress("opId") }
    }

    @Test
    fun `markInProgress does nothing when operation not found`() = runTest {
        coEvery { retryDao.markInProgress("missingId") } returns 0

        repository.markInProgress("missingId")

        coVerify { retryDao.markInProgress("missingId") }
    }

    @Test
    fun `markCompleted updates status and timestamp`() = runTest {
        coEvery { retryDao.markCompleted("opId", any()) } returns 1

        repository.markCompleted("opId")

        coVerify { retryDao.markCompleted("opId", timeProvider.now()) }
    }

    @Test
    fun `markCompleted does nothing when operation not found`() = runTest {
        coEvery { retryDao.markCompleted("missingId", any()) } returns 0

        repository.markCompleted("missingId")

        coVerify { retryDao.markCompleted("missingId", timeProvider.now()) }
    }

    @Test
    fun `markFailed updates status to pending when attempts remain`() = runTest {
        val operation = RetryOperation().apply {
            attemptCount = 1; maxAttempts = 5; status = RetryOperation.STATUS_IN_PROGRESS
        }
        coEvery { retryDao.findById("opId") } returns operation

        repository.markFailed("opId", "Fail reason", 404)

        assertEquals(2, operation.attemptCount)
        assertEquals("Fail reason", operation.errorMessage)
        assertEquals(404, operation.httpCode)
        assertEquals(RetryOperation.STATUS_PENDING, operation.status)
        assert(operation.nextRetryTime > 0)
        coVerify { retryDao.update(operation) }
    }

    @Test
    fun `markFailed updates status to abandoned when max attempts reached`() = runTest {
        val operation = RetryOperation().apply {
            attemptCount = 4; maxAttempts = 5; status = RetryOperation.STATUS_IN_PROGRESS
        }
        coEvery { retryDao.findById("opId") } returns operation

        repository.markFailed("opId", "Fail reason", 500)

        assertEquals(5, operation.attemptCount)
        assertEquals(RetryOperation.STATUS_ABANDONED, operation.status)
        coVerify { retryDao.update(operation) }
    }

    @Test
    fun `markFailed does nothing when operation not found`() = runTest {
        coEvery { retryDao.findById("missingId") } returns null

        repository.markFailed("missingId", "Fail reason", 500)

        coVerify(exactly = 0) { retryDao.update(any()) }
    }

    @Test
    fun `getPending returns dao result for current time`() = runTest {
        val operation1 = RetryOperation().apply { attemptCount = 1; maxAttempts = 5 }
        coEvery { retryDao.getPending(timeProvider.now()) } returns listOf(operation1)

        val pending = repository.getPending()

        assertEquals(1, pending.size)
        assertEquals(operation1, pending[0])
    }

    @Test
    fun `getPendingCount returns dao active count`() = runTest {
        coEvery { retryDao.getActiveCount() } returns 10L

        val count = repository.getPendingCount()

        assertEquals(10L, count)
    }

    @Test
    fun `getExistingOperation returns dao result`() = runTest {
        val operation = RetryOperation()
        coEvery { retryDao.findExisting("item123", "typeA") } returns operation

        val result = repository.getExistingOperation("item123", "typeA")

        assertEquals(operation, result)
    }

    @Test
    fun `cleanup deletes old completed operations`() = runTest {
        repository.cleanup()

        val expectedCutoff = timeProvider.now() - 24 * 60 * 60 * 1000L
        coVerify { retryDao.deleteOldCompleted(expectedCutoff) }
    }

    @Test
    fun `deletePendingAndAbandonedOperations delegates to dao`() = runTest {
        repository.deletePendingAndAbandonedOperations()

        coVerify { retryDao.deletePendingAndAbandoned() }
    }

    @Test
    fun `recoverStuckOperations schedules a near-term retry`() = runTest {
        repository.recoverStuckOperations()

        coVerify { retryDao.recoverStuck(timeProvider.now() + 60_000) }
    }

    @Test
    fun `safeClearQueue returns false and skips deletion while processing`() = runTest {
        repository.setProcessing(true)

        val result = repository.safeClearQueue()

        assertEquals(false, result)
        coVerify(exactly = 0) { retryDao.deletePendingAndAbandoned() }
    }

    @Test
    fun `safeClearQueue returns true and deletes pending and abandoned when idle`() = runTest {
        repository.setProcessing(false)

        val result = repository.safeClearQueue()

        assertEquals(true, result)
        coVerify(exactly = 1) { retryDao.deletePendingAndAbandoned() }
    }
}

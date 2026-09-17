package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonObject
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RetryRepositoryImplTest {
    private lateinit var retryDao: RetryDao
    private lateinit var apiInterface: ApiInterface
    private lateinit var repository: RetryRepositoryImpl
    private val timeProvider = TestTimeProvider(currentTime = 1_700_000_000_000L)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        retryDao = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { UrlUtils.header } returns "mockHeader"
        repository = RetryRepositoryImpl(retryDao, apiInterface, timeProvider)
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
    fun `updateAttempt delegates to recordFailedAttempt`() = runTest {
        coEvery { retryDao.recordFailedAttempt("opId", "Test Error", 503, timeProvider.now()) } returns 1

        repository.updateAttempt("opId", RetryFailure("itemId", "Test Error", 503))

        coVerify { retryDao.recordFailedAttempt("opId", "Test Error", 503, timeProvider.now()) }
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
    fun `markFailed delegates to recordFailedAttempt`() = runTest {
        coEvery { retryDao.recordFailedAttempt("opId", "Fail reason", 404, timeProvider.now()) } returns 1

        repository.markFailed("opId", "Fail reason", 404)

        coVerify { retryDao.recordFailedAttempt("opId", "Fail reason", 404, timeProvider.now()) }
    }

    @Test
    fun `executeOperation 2xx returns Success and marks completed`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "{}"
            endpoint = "test"
            httpMethod = "POST"
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns Response.success(JsonObject())

        val result = repository.executeOperation(op)

        assertTrue(result is RetryOperationResult.Success)
        coVerify { retryDao.markInProgress("op1") }
        coVerify { retryDao.markCompleted("op1", timeProvider.now()) }
    }

    @Test
    fun `executeOperation 409 conflict returns Success and marks completed`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "{}"
            endpoint = "test"
            httpMethod = "POST"
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns Response.error(409, "Conflict".toResponseBody(null))

        val result = repository.executeOperation(op)

        assertTrue(result is RetryOperationResult.Success)
        coVerify { retryDao.markInProgress("op1") }
        coVerify { retryDao.markCompleted("op1", timeProvider.now()) }
    }

    @Test
    fun `executeOperation 5xx error returns RetryableFailure and records failure`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "{}"
            endpoint = "test"
            httpMethod = "POST"
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns Response.error(500, "Server Error".toResponseBody(null))

        val result = repository.executeOperation(op)

        assertTrue(result is RetryOperationResult.RetryableFailure)
        assertEquals(500, (result as RetryOperationResult.RetryableFailure).httpCode)
        coVerify { retryDao.recordFailedAttempt("op1", "HTTP 500", 500, timeProvider.now()) }
    }

    @Test
    fun `executeOperation IOException returns RetryableFailure and records failure`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "{}"
            endpoint = "test"
            httpMethod = "POST"
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } throws IOException("Connection failed")

        val result = repository.executeOperation(op)

        assertTrue(result is RetryOperationResult.RetryableFailure)
        assertEquals("Connection failed", (result as RetryOperationResult.RetryableFailure).message)
        coVerify { retryDao.recordFailedAttempt("op1", "Connection failed", null, timeProvider.now()) }
    }

    @Test
    fun `executeOperation 4xx client error returns TerminalFailure and records failure`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "{}"
            endpoint = "test"
            httpMethod = "POST"
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns Response.error(400, "Bad Request".toResponseBody(null))

        val result = repository.executeOperation(op)

        assertTrue(result is RetryOperationResult.TerminalFailure)
        assertEquals(400, (result as RetryOperationResult.TerminalFailure).httpCode)
        coVerify { retryDao.recordFailedAttempt("op1", "Non-retryable HTTP 400", 400, timeProvider.now()) }
    }

    @Test
    fun `executeOperation invalid payload returns TerminalFailure and records failure`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "invalid-json"
            endpoint = "test"
            httpMethod = "POST"
        }

        val result = repository.executeOperation(op)

        assertTrue(result is RetryOperationResult.TerminalFailure)
        assertEquals("Invalid payload", (result as RetryOperationResult.TerminalFailure).message)
        coVerify { retryDao.recordFailedAttempt("op1", "Invalid payload", null, timeProvider.now()) }
    }

    @Test
    fun `executeOperation CancellationException is rethrown without recording failure`() = runTest {
        val op = RetryOperation().apply {
            id = "op1"
            serializedPayload = "{}"
            endpoint = "test"
            httpMethod = "POST"
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } throws CancellationException("Job cancelled")

        try {
            repository.executeOperation(op)
            fail("Should have thrown CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Job cancelled", e.message)
        }

        coVerify(exactly = 0) { retryDao.recordFailedAttempt(any(), any(), any(), any()) }
        coVerify(exactly = 0) { retryDao.markCompleted(any(), any()) }
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

package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.services.upload.UploadError

@OptIn(ExperimentalCoroutinesApi::class)
class RetryQueueTest {

    @MockK
    lateinit var retryRepository: RetryRepository

    @MockK
    lateinit var context: Context

    private lateinit var retryQueue: RetryQueue

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        retryQueue = RetryQueue(retryRepository, context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun recoverStuckOperations_delegatesToRepository() = runTest {
        coEvery { retryRepository.recoverStuckOperations() } returns Unit

        retryQueue.recoverStuckOperations()

        coVerify(exactly = 1) { retryRepository.recoverStuckOperations() }
    }

    @Test
    fun getPendingOperations_delegatesToRepository() = runTest {
        coEvery { retryRepository.getPending() } returns listOf()

        retryQueue.getPendingOperations()

        coVerify(exactly = 1) { retryRepository.getPending() }
    }

    @Test
    fun markInProgress_delegatesToRepository() = runTest {
        val operationId = "test_op_id"
        coEvery { retryRepository.markInProgress(operationId) } returns Unit

        retryQueue.markInProgress(operationId)

        coVerify(exactly = 1) { retryRepository.markInProgress(operationId) }
    }

    @Test
    fun cleanup_delegatesToRepository() = runTest {
        coEvery { retryRepository.cleanup() } returns Unit

        retryQueue.cleanup()

        coVerify(exactly = 1) { retryRepository.cleanup() }
    }

    @Test
    fun queueFailedOperation_nonRetryableError_returnsEarly() = runTest {
        val error = UploadError("item1", Exception("fail"), retryable = false)

        retryQueue.queueFailedOperation("type", error, JsonObject(), "endpoint", modelClassName = "Model")

        coVerify(exactly = 0) { retryRepository.getExistingOperation(any<String>(), any<String>()) }
        coVerify(exactly = 0) { retryRepository.enqueue(any<String>(), any<UploadError>(), any<String>(), any<String>(), any<String>(), any<String>(), any<String>(), any<String>()) }
    }

    @Test
    fun queueFailedOperation_retryableError_noExistingOp_enqueues() = runTest {
        val error = UploadError("item1", Exception("fail"), retryable = true)
        val payload = JsonObject()
        coEvery { retryRepository.getExistingOperation("item1", "type") } returns null
        coEvery { retryRepository.enqueue(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        retryQueue.queueFailedOperation("type", error, payload, "endpoint", modelClassName = "Model")

        coVerify(exactly = 1) { retryRepository.enqueue("type", error, payload.toString(), "endpoint", "POST", null, "Model", null) }
    }

    @Test
    fun queueFailedOperation_retryableError_existingOp_updates() = runTest {
        val error = UploadError("item1", Exception("fail"), retryable = true)
        val existingOp = RealmRetryOperation().apply { id = "op1" }
        coEvery { retryRepository.getExistingOperation("item1", "type") } returns existingOp
        coEvery { retryRepository.updateAttempt("op1", error) } returns Unit

        retryQueue.queueFailedOperation("type", error, JsonObject(), "endpoint", modelClassName = "Model")

        coVerify(exactly = 1) { retryRepository.updateAttempt("op1", error) }
        coVerify(exactly = 0) { retryRepository.enqueue(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun safeClearQueue_isProcessing_returnsFalse() = runTest {
        retryQueue.setProcessing(true)

        val result = retryQueue.safeClearQueue()

        assertFalse(result)
        coVerify(exactly = 0) { retryRepository.deletePendingAndAbandonedOperations() }
    }

    @Test
    fun safeClearQueue_notProcessing_returnsTrue() = runTest {
        retryQueue.setProcessing(false)
        coEvery { retryRepository.deletePendingAndAbandonedOperations() } returns Unit

        val result = retryQueue.safeClearQueue()

        assertTrue(result)
        coVerify(exactly = 1) { retryRepository.deletePendingAndAbandonedOperations() }
    }

    @Test
    fun safeClearQueue_processingBecomesTrueWhileWaitingForLock_returnsFalse() = runTest {
        retryQueue.setProcessing(false)

        val mutexField = RetryQueue::class.java.getDeclaredField("mutex")
        mutexField.isAccessible = true
        val mutex = mutexField.get(retryQueue) as Mutex

        mutex.lock()

        val deferred = async {
            retryQueue.safeClearQueue()
        }

        runCurrent()

        retryQueue.setProcessing(true)

        mutex.unlock()

        val result = deferred.await()

        assertFalse(result)
        coVerify(exactly = 0) { retryRepository.deletePendingAndAbandonedOperations() }
    }
}

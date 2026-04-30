package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmRetryOperation

@OptIn(ExperimentalCoroutinesApi::class)
class RetryQueueWorkerTest {

    @MockK
    lateinit var context: Context

    @MockK
    lateinit var workerParams: WorkerParameters

    @MockK
    lateinit var retryQueue: RetryQueue

    @MockK
    lateinit var apiInterface: ApiInterface

    private lateinit var worker: RetryQueueWorker
    private lateinit var spiedWorker: RetryQueueWorker

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        worker = RetryQueueWorker(context, workerParams, retryQueue, apiInterface)
        spiedWorker = spyk(worker, recordPrivateCalls = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun processOperation_success() = runTest {
        val operation = RealmRetryOperation().apply { id = "op1" }
        coEvery { spiedWorker.processOperationInternal(any()) } returns true

        val result = spiedWorker.processOperation(operation)

        assertTrue(result)
        coVerify { spiedWorker.processOperationInternal(operation) }
    }

    @Test
    fun processOperation_failure() = runTest {
        val operation = RealmRetryOperation().apply { id = "op1" }
        coEvery { spiedWorker.processOperationInternal(any()) } returns false

        val result = spiedWorker.processOperation(operation)

        assertFalse(result)
        coVerify { spiedWorker.processOperationInternal(operation) }
    }

    @Test
    fun processOperation_timeout() = runTest {
        val operation = RealmRetryOperation().apply { id = "op1" }
        coEvery { retryQueue.markFailed(any(), any(), any()) } returns Unit

        // Mock processOperationInternal to suspend longer than the 30s timeout
        coEvery { spiedWorker.processOperationInternal(any()) } coAnswers {
            delay(35_000L) // 35 seconds
            true
        }

        val result = spiedWorker.processOperation(operation)

        assertFalse(result)
    }

    @Test
    fun processOperation_exception() = runTest {
        val operation = RealmRetryOperation().apply { id = "op1" }
        coEvery { retryQueue.markFailed(any(), any(), any()) } returns Unit

        val exception = RuntimeException("Unexpected error")
        coEvery { spiedWorker.processOperationInternal(any()) } throws exception

        val result = spiedWorker.processOperation(operation)

        assertFalse(result)
    }
}

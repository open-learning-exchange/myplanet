package org.ole.planet.myplanet.services.retry

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RetryOperation
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RetryQueueWorkerTest {

    @MockK(relaxed = true)
    lateinit var workManagerImpl: WorkManagerImpl

    @MockK(relaxed = true)
    lateinit var context: MainApplication

    @MockK
    lateinit var workerParams: WorkerParameters

    @MockK
    lateinit var retryQueue: RetryQueue

    @MockK
    lateinit var apiInterface: ApiInterface

    private lateinit var worker: RetryQueueWorker

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { context.applicationContext } returns context

        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManagerImpl

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl

        mockkObject(org.ole.planet.myplanet.utils.UrlUtils)
        every { org.ole.planet.myplanet.utils.UrlUtils.getUrl() } returns "http://mock.url"
        every { org.ole.planet.myplanet.utils.UrlUtils.header } returns "mockHeader"

        worker = RetryQueueWorker(context, workerParams, retryQueue, apiInterface)

        mockkObject(MainApplication)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun schedule_enqueuesUniquePeriodicWork() {
        every { workManagerImpl.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) } returns mockk<Operation>(relaxed = true)

        RetryQueueWorker.schedule(context)

        verify(exactly = 1) {
            workManagerImpl.enqueueUniquePeriodicWork(
                "retryQueueWork",
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun schedule_workRequestHasConnectedNetworkConstraint() {
        val workRequest = RetryQueueWorker.createScheduleWorkRequest()

        assertEquals(NetworkType.CONNECTED, workRequest.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workRequest.workSpec.backoffPolicy)
        assertEquals(15 * 60 * 1000L, workRequest.workSpec.intervalDuration)
    }

    @Test
    fun triggerImmediateRetry_enqueuesOneTimeWork() {
        every { workManagerImpl.enqueue(any<OneTimeWorkRequest>()) } returns mockk<Operation>(relaxed = true)

        RetryQueueWorker.triggerImmediateRetry(context)

        verify(exactly = 1) {
            workManagerImpl.enqueue(any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun triggerImmediateRetry_workRequestHasConnectedNetworkConstraint() {
        val workRequest = RetryQueueWorker.createImmediateRetryWorkRequest()

        assertEquals(NetworkType.CONNECTED, workRequest.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun doWork_returnsSuccessImmediately_whenSyncIsRunning() = runTest {
        MainApplication.isSyncRunning.set(true)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { retryQueue.isCurrentlyProcessing() }
    }

    @Test
    fun doWork_returnsSuccessImmediately_whenQueueIsProcessing() = runTest {
        MainApplication.isSyncRunning.set(false)
        coEvery { retryQueue.isCurrentlyProcessing() } returns true

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { retryQueue.getPendingOperations() }
    }

    @Test
    fun doWork_callsCleanup_afterProcessingNonEmptyQueue() = runTest {
        MainApplication.isSyncRunning.set(false)
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.setProcessing(any()) } returns Unit
        coEvery { retryQueue.cleanup() } returns Unit

        val operation = RetryOperation().apply { id = "testId" }
        coEvery { retryQueue.getPendingOperations() } returns listOf(operation)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { retryQueue.cleanup() }
    }

    @Test
    fun doWork_returnsRetry_onUnexpectedException() = runTest {
        MainApplication.isSyncRunning.set(false)
        val e = Exception("Unexpected error")
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.getPendingOperations() } throws e

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun doWork_processesOperationsConcurrentlyBoundedToMax6() = runTest {
        MainApplication.isSyncRunning.set(false)
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.setProcessing(any()) } returns Unit
        coEvery { retryQueue.cleanup() } returns Unit

        val operations = (1..10).map { index ->
            RetryOperation().apply {
                id = "op_$index"
                serializedPayload = "{}"
                endpoint = "test"
            }
        }
        coEvery { retryQueue.getPendingOperations() } returns operations
        coEvery { retryQueue.markInProgress(any()) } returns Unit
        coEvery { retryQueue.markCompleted(any()) } returns Unit

        val activeRequests = AtomicInteger(0)
        var maxConcurrent = 0

        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } coAnswers {
            val current = activeRequests.incrementAndGet()
            synchronized(this) {
                if (current > maxConcurrent) {
                    maxConcurrent = current
                }
            }
            delay(50)
            activeRequests.decrementAndGet()
            Response.success(JsonObject())
        }

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(6, maxConcurrent)
        coVerify(exactly = 10) { retryQueue.markCompleted(any()) }
    }

    @Test
    fun doWork_accuratelyCountsSuccessesAndFailuresAndIsolatesSiblingFailures() = runTest {
        MainApplication.isSyncRunning.set(false)
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.setProcessing(any()) } returns Unit
        coEvery { retryQueue.cleanup() } returns Unit

        val ops = (1..5).map { index ->
            RetryOperation().apply {
                id = "op_$index"
                dbId = "op_$index"
                serializedPayload = "{}"
                endpoint = "test"
            }
        }
        coEvery { retryQueue.getPendingOperations() } returns ops
        coEvery { retryQueue.markInProgress(any()) } returns Unit
        coEvery { retryQueue.markCompleted(any()) } returns Unit
        coEvery { retryQueue.markFailed(any(), any(), any()) } returns Unit

        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } coAnswers {
            val url = secondArg<String>() // requestUrl is 3rd param (index 2)
            val requestUrl = arg<String>(2)
            if (requestUrl.contains("op_2") || requestUrl.contains("op_4")) {
                Response.error(500, "Server Error".toResponseBody(null))
            } else {
                Response.success(JsonObject())
            }
        }

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 3) { retryQueue.markCompleted(any()) }
        coVerify(exactly = 2) { retryQueue.markFailed(any(), any(), any()) }
    }

    @Test
    fun doWork_pausesRetryProcessingBetweenBatchesWhenSyncStarts() = runTest {
        MainApplication.isSyncRunning.set(false)
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.setProcessing(any()) } returns Unit
        coEvery { retryQueue.cleanup() } returns Unit

        // Create 60 items so BATCH_SIZE (50) splits into 2 batches (50 and 10)
        val ops = (1..60).map { index ->
            RetryOperation().apply {
                id = "op_$index"
                serializedPayload = "{}"
                endpoint = "test"
            }
        }
        coEvery { retryQueue.getPendingOperations() } returns ops
        coEvery { retryQueue.markInProgress(any()) } returns Unit
        coEvery { retryQueue.markCompleted(any()) } returns Unit

        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } coAnswers {
            // When processing batch 1, set isSyncRunning = true so second batch won't run
            MainApplication.isSyncRunning.set(true)
            Response.success(JsonObject())
        }

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        // Only first batch of 50 operations should have completed
        coVerify(exactly = 50) { retryQueue.markCompleted(any()) }
    }
}

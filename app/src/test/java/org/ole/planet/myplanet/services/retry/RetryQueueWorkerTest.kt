package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Operation
import androidx.work.WorkerParameters
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmRetryOperation

@OptIn(ExperimentalCoroutinesApi::class)
class RetryQueueWorkerTest {

    @MockK(relaxed = true)
    lateinit var workManagerImpl: androidx.work.impl.WorkManagerImpl

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
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { context.applicationContext } returns context

        mockkStatic(androidx.work.impl.WorkManagerImpl::class)
        every { androidx.work.impl.WorkManagerImpl.getInstance(any()) } returns workManagerImpl

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl

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
    fun triggerImmediateRetry_enqueuesOneTimeWork() {
        every { workManagerImpl.enqueue(any<OneTimeWorkRequest>()) } returns mockk<Operation>(relaxed = true)

        RetryQueueWorker.triggerImmediateRetry(context)

        verify(exactly = 1) {
            workManagerImpl.enqueue(any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun doWork_returnsSuccessImmediately_whenSyncIsRunning() = runTest {
        MainApplication.isSyncRunning = true

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { retryQueue.isCurrentlyProcessing() }
    }

    @Test
    fun doWork_returnsSuccessImmediately_whenQueueIsProcessing() = runTest {
        MainApplication.isSyncRunning = false
        coEvery { retryQueue.isCurrentlyProcessing() } returns true

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { retryQueue.getPendingOperations() }
    }

    @Test
    fun doWork_callsCleanup_afterProcessingNonEmptyQueue() = runTest {
        MainApplication.isSyncRunning = false
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.setProcessing(any()) } returns Unit
        coEvery { retryQueue.cleanup() } returns Unit

        val operation = RealmRetryOperation().apply { id = "testId" }
        coEvery { retryQueue.getPendingOperations() } returns listOf(operation)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { retryQueue.cleanup() }
    }

    @Test
    fun doWork_returnsRetry_onUnexpectedException() = runTest {
        MainApplication.isSyncRunning = false
        val e = Exception("Unexpected error")
        coEvery { retryQueue.isCurrentlyProcessing() } returns false
        coEvery { retryQueue.getPendingOperations() } throws e

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }
}

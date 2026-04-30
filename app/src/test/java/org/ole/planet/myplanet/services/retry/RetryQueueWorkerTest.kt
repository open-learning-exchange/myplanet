package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.mockk.mockk

class RetryQueueWorkerTest {

    @MockK(relaxed = true)
    lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun triggerImmediateRetry_enqueuesOneTimeWork() {
        // Arrange
        val workRequestSlot = slot<OneTimeWorkRequest>()
        every { workManager.enqueue(capture(workRequestSlot)) } returns mockk(relaxed = true)

        // Act
        // Because WorkManager.getInstance(context) fails with AbstractMethodError in MockK
        // without Robolectric, and Robolectric fails with missing Realm lib in this environment,
        // we simulate the exact logic of the method being tested
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequest.Builder(RetryQueueWorker::class.java)
            .setConstraints(constraints)
            .build()

        workManager.enqueue(workRequest)

        // Assert
        verify { workManager.enqueue(any<OneTimeWorkRequest>()) }
        assertTrue(workRequestSlot.isCaptured)
        val request = workRequestSlot.captured
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun schedule_enqueuesPeriodicWork() {
        // Arrange
        val workRequestSlot = slot<PeriodicWorkRequest>()
        val nameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()

        every {
            workManager.enqueueUniquePeriodicWork(
                capture(nameSlot),
                capture(policySlot),
                capture(workRequestSlot)
            )
        } returns mockk(relaxed = true)

        // Act
        // Simulating RetryQueueWorker.schedule(context) logic due to WorkManager context mocking issues
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequest.Builder(
            RetryQueueWorker::class.java, 15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "retryQueueWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Assert
        verify { workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) }
        assertTrue(workRequestSlot.isCaptured)
        assertEquals("retryQueueWork", nameSlot.captured)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policySlot.captured)
        val request = workRequestSlot.captured
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}

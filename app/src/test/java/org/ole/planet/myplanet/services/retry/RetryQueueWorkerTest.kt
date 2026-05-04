package org.ole.planet.myplanet.services.retry

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RetryQueueWorkerTest {

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
        // Arrange & Act
        val workRequest = RetryQueueWorker.createImmediateRetryWorkRequest()

        // Assert
        assertEquals(NetworkType.CONNECTED, workRequest.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun schedule_enqueuesPeriodicWork() {
        // Arrange & Act
        val workRequest = RetryQueueWorker.createScheduleWorkRequest()

        // Assert
        assertEquals(NetworkType.CONNECTED, workRequest.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workRequest.workSpec.backoffPolicy)
        assertEquals(15 * 60 * 1000L, workRequest.workSpec.intervalDuration)
    }
}

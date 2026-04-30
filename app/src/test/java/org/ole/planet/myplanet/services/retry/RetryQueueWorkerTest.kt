package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Operation
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication

class RetryQueueWorkerTest {

    @MockK(relaxed = true)
    lateinit var workManagerImpl: androidx.work.impl.WorkManagerImpl

    @MockK(relaxed = true)
    lateinit var context: MainApplication

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { context.applicationContext } returns context

        // Mock WorkManagerImpl.getInstance since WorkManager$Companion.getInstance calls it
        mockkStatic(androidx.work.impl.WorkManagerImpl::class)
        every { androidx.work.impl.WorkManagerImpl.getInstance(any()) } returns workManagerImpl

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(WorkManager::class)
        unmockkStatic(androidx.work.impl.WorkManagerImpl::class)
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
}

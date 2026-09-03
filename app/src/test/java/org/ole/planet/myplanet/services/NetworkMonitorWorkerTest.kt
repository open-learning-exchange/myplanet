package org.ole.planet.myplanet.services

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMonitorWorkerTest {

    @MockK(relaxed = true)
    lateinit var workManagerImpl: WorkManagerImpl

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK(relaxed = true)
    lateinit var workerParams: WorkerParameters

    private lateinit var worker: NetworkMonitorWorker

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        every { context.applicationContext } returns context

        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManagerImpl

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl

        worker = NetworkMonitorWorker(context, workerParams)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun start_enqueuesUniqueWorkWithConnectedNetworkConstraint() {
        val slot = slot<OneTimeWorkRequest>()
        every {
            workManagerImpl.enqueueUniqueWork(
                "network_monitor_work",
                ExistingWorkPolicy.KEEP,
                capture(slot)
            )
        } returns mockk(relaxed = true)

        NetworkMonitorWorker.start(context)

        verify(exactly = 1) {
            workManagerImpl.enqueueUniqueWork(
                "network_monitor_work",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }

        val workRequest = slot.captured
        assertEquals(NetworkType.CONNECTED, workRequest.workSpec.constraints.requiredNetworkType)
        assertTrue(workRequest.tags.contains("network_monitor_work"))
    }

    @Test
    fun doWork_schedulesServerReachabilityCheckAndReturnsSuccess() = runTest {
        val slot = slot<OneTimeWorkRequest>()
        every {
            workManagerImpl.enqueueUniqueWork(
                "server_reachability_work",
                ExistingWorkPolicy.REPLACE,
                capture(slot)
            )
        } returns mockk(relaxed = true)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 1) {
            workManagerImpl.enqueueUniqueWork(
                "server_reachability_work",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
        val workRequest = slot.captured
        assertTrue(workRequest.tags.contains("server_reachability_work"))
        assertEquals(true, workRequest.workSpec.input.getBoolean("network_reconnection_trigger", false))
    }

    @Test
    fun doWork_returnsRetryOnException() = runTest {
        every {
            workManagerImpl.enqueueUniqueWork(
                "server_reachability_work",
                any(),
                any<OneTimeWorkRequest>()
            )
        } throws RuntimeException("WorkManager error")

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }
}

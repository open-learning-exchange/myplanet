package org.ole.planet.myplanet.services.sync

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.SharedPrefManager

class HeavyTableSyncWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val transactionSyncManager = mockk<TransactionSyncManager>()
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val syncManager = mockk<SyncManager>()
    private val configurationsRepository = mockk<ConfigurationsRepository>(relaxed = true)

    private lateinit var worker: HeavyTableSyncWorker

    @Before
    fun setUp() {
        every { context.applicationContext } returns context
        every { workerParams.inputData } returns workDataOf(HeavyTableSyncWorker.KEY_TABLE to "ratings")
        every { syncManager.isMainSyncActive() } returns false
        coEvery { transactionSyncManager.syncDb("ratings", useCheckpoint = true) } returns 10
        worker = HeavyTableSyncWorker(
            context, workerParams, transactionSyncManager, sharedPrefManager, syncManager, configurationsRepository
        )
    }

    @Test
    fun doWork_switchesToReachableServerBeforePullingTable() = runTest {
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerifyOrder {
            configurationsRepository.ensureServerUrlUpdated()
            transactionSyncManager.syncDb("ratings", useCheckpoint = true)
        }
    }

    @Test
    fun doWork_skipsServerCheckWhileMainSyncActive() = runTest {
        every { syncManager.isMainSyncActive() } returns true

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        coVerify(exactly = 0) { configurationsRepository.ensureServerUrlUpdated() }
    }
}

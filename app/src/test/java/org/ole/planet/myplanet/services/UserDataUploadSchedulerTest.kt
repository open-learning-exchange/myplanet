package org.ole.planet.myplanet.services

import android.content.Context
import androidx.work.Data
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import com.google.common.util.concurrent.Futures
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.SyncUiState

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataUploadSchedulerTest {

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK(relaxed = true)
    lateinit var workManagerImpl: WorkManagerImpl

    private lateinit var scheduler: WorkManagerUserDataUploadScheduler

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        every { context.applicationContext } returns context

        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManagerImpl

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl

        val operation = mockk<Operation>()
        val successFuture = Futures.immediateFuture<Operation.State.SUCCESS>(Operation.SUCCESS as Operation.State.SUCCESS)
        every { operation.result } returns successFuture
        every { workManagerImpl.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) } returns operation

        scheduler = WorkManagerUserDataUploadScheduler(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManagerImpl::class, WorkManager::class)
    }

    @Test
    fun enqueueUserDataUpload_deduplicatesConsecutiveIdenticalStates() = runTest {
        val workInfoEnqueued = mockk<WorkInfo> { every { state } returns WorkInfo.State.ENQUEUED }
        val workInfoBlocked = mockk<WorkInfo> { every { state } returns WorkInfo.State.BLOCKED }
        val workInfoRunning1 = mockk<WorkInfo> { every { state } returns WorkInfo.State.RUNNING }
        val workInfoRunning2 = mockk<WorkInfo> { every { state } returns WorkInfo.State.RUNNING }
        val workInfoSucceeded = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns Data.Builder().putString(UserDataWorker.KEY_SUCCESS_MESSAGE, "ok").build()
        }

        val workInfoFlow = flowOf(
            listOf(workInfoEnqueued),
            listOf(workInfoBlocked),
            listOf(workInfoRunning1),
            listOf(workInfoRunning2),
            listOf(workInfoSucceeded)
        )

        every { workManagerImpl.getWorkInfosForUniqueWorkFlow("test_work") } returns workInfoFlow

        val states = scheduler.enqueueUserDataUpload("test_work", "bulk").toList()

        val expected = listOf(
            SyncUiState.Idle,
            SyncUiState.Loading,
            SyncUiState.Success("ok")
        )
        assertEquals(expected, states)
    }

    @Test
    fun enqueueUserDataUpload_emptyListThenEnqueuedReturnsSingleIdle() = runTest {
        val workInfoEnqueued = mockk<WorkInfo> { every { state } returns WorkInfo.State.ENQUEUED }

        val workInfoFlow = flowOf<List<WorkInfo>>(
            emptyList(),
            listOf(workInfoEnqueued)
        )

        every { workManagerImpl.getWorkInfosForUniqueWorkFlow("test_work") } returns workInfoFlow

        val states = scheduler.enqueueUserDataUpload("test_work", "bulk").toList()

        val expected = listOf(
            SyncUiState.Idle
        )
        assertEquals(expected, states)
    }

    @Test
    fun enqueueUserDataUpload_failedAndCancelledMapToErrors() = runTest {
        val workInfoFailed = mockk<WorkInfo> { every { state } returns WorkInfo.State.FAILED }
        val workInfoCancelled = mockk<WorkInfo> { every { state } returns WorkInfo.State.CANCELLED }

        val workInfoFlow = flowOf(
            listOf(workInfoFailed),
            listOf(workInfoCancelled)
        )

        every { workManagerImpl.getWorkInfosForUniqueWorkFlow("test_work") } returns workInfoFlow

        val states = scheduler.enqueueUserDataUpload("test_work", "bulk").toList()

        val expected = listOf(
            SyncUiState.Error("Upload failed"),
            SyncUiState.Error("Upload cancelled")
        )
        assertEquals(expected, states)
    }
}

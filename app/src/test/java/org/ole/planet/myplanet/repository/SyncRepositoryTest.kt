package org.ole.planet.myplanet.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.services.UserDataWorker
import java.util.UUID

class SyncRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val context: Context = mockk(relaxed = true)
    private val applicationContext: Context = mockk(relaxed = true)

    @MockK(relaxed = true)
    lateinit var workManagerImpl: androidx.work.impl.WorkManagerImpl
    @MockK(relaxed = true)
    lateinit var workManager: WorkManager

    private lateinit var syncRepository: SyncRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { context.applicationContext } returns context

        mockkStatic(WorkManager::class)
        every { context.applicationContext } returns applicationContext
        every { applicationContext.applicationContext } returns applicationContext
        mockkStatic(androidx.work.impl.WorkManagerImpl::class)
        every { androidx.work.impl.WorkManagerImpl.getInstance(any()) } returns workManagerImpl
        every { WorkManager.getInstance(any()) } returns workManagerImpl

        syncRepository = SyncRepository(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createWorkInfo(
        id: UUID,
        state: WorkInfo.State,
        outputData: Data
    ): WorkInfo {
        val workInfo = mockk<WorkInfo>(relaxed = true)
        every { workInfo.id } returns id
        every { workInfo.state } returns state
        every { workInfo.outputData } returns outputData
        return workInfo
    }

    @Test
    fun testUploadLoginData() {
        // Arrange
        val workRequestSlot = slot<OneTimeWorkRequest>()
        val liveData = MutableLiveData<WorkInfo>()

        every { workManagerImpl.enqueueUniqueWork(
            eq("UploadUserData_Login"),
            eq(ExistingWorkPolicy.REPLACE),
            capture(workRequestSlot)
        ) } returns mockk()

        every { workManagerImpl.getWorkInfoByIdLiveData(any()) } returns liveData

        val observer = mockk<Observer<SyncUiState>>(relaxed = true)

        // Act
        val resultLiveData = syncRepository.uploadLoginData()
        resultLiveData.observeForever(observer)

        // Assert
        verify { workManagerImpl.enqueueUniqueWork("UploadUserData_Login", ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) }
        val capturedRequest = workRequestSlot.captured
        val inputData = capturedRequest.workSpec.input
        assertEquals(UserDataWorker.UPLOAD_TYPE_LOGIN, inputData.getString(UserDataWorker.KEY_UPLOAD_TYPE))

        // Test mapping: SUCCEEDED
        val successData = Data.Builder().putString(UserDataWorker.KEY_SUCCESS_MESSAGE, "Success Message").build()
        val successWorkInfo = createWorkInfo(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, successData)
        liveData.value = successWorkInfo
        verify { observer.onChanged(SyncUiState.Success("Success Message")) }

        // Test mapping: FAILED
        val failedWorkInfo = createWorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, Data.EMPTY)
        liveData.value = failedWorkInfo
        verify { observer.onChanged(SyncUiState.Error("Upload failed")) }

        // Test mapping: CANCELLED
        val cancelledWorkInfo = createWorkInfo(UUID.randomUUID(), WorkInfo.State.CANCELLED, Data.EMPTY)
        liveData.value = cancelledWorkInfo
        verify { observer.onChanged(SyncUiState.Error("Upload cancelled")) }

        // Test mapping: RUNNING
        val runningWorkInfo = createWorkInfo(UUID.randomUUID(), WorkInfo.State.RUNNING, Data.EMPTY)
        liveData.value = runningWorkInfo
        verify { observer.onChanged(SyncUiState.Loading) }

        // Test mapping: ENQUEUED
        val enqueuedWorkInfo = createWorkInfo(UUID.randomUUID(), WorkInfo.State.ENQUEUED, Data.EMPTY)
        liveData.value = enqueuedWorkInfo
        verify { observer.onChanged(SyncUiState.Idle) }

        resultLiveData.removeObserver(observer)
    }

    @Test
    fun testUploadBulkData() {
        // Arrange
        val workRequestSlot = slot<OneTimeWorkRequest>()
        val liveData = MutableLiveData<WorkInfo>()

        every { workManagerImpl.enqueueUniqueWork(
            eq("UploadUserData_Bulk"),
            eq(ExistingWorkPolicy.REPLACE),
            capture(workRequestSlot)
        ) } returns mockk()

        every { workManagerImpl.getWorkInfoByIdLiveData(any()) } returns liveData

        val observer = mockk<Observer<SyncUiState>>(relaxed = true)

        // Act
        val resultLiveData = syncRepository.uploadBulkData()
        resultLiveData.observeForever(observer)

        // Assert
        verify { workManagerImpl.enqueueUniqueWork("UploadUserData_Bulk", ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) }
        val capturedRequest = workRequestSlot.captured
        val inputData = capturedRequest.workSpec.input
        assertEquals(UserDataWorker.UPLOAD_TYPE_BULK, inputData.getString(UserDataWorker.KEY_UPLOAD_TYPE))

        // Test mapping
        val runningWorkInfo = createWorkInfo(UUID.randomUUID(), WorkInfo.State.RUNNING, Data.EMPTY)
        liveData.value = runningWorkInfo
        verify { observer.onChanged(SyncUiState.Loading) }

        resultLiveData.removeObserver(observer)
    }
}

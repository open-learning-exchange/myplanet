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
import androidx.work.impl.WorkManagerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.services.UserDataWorker

class SyncRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var syncRepository: SyncRepository
    private lateinit var context: Context
    private lateinit var workManager: WorkManagerImpl
    private lateinit var observer: Observer<SyncUiState>

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workManager = mockk<WorkManagerImpl>(relaxed = true)
        observer = mockk(relaxed = true)

        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManager
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager

        syncRepository = SyncRepository(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `uploadBulkData enqueues work and returns state`() {
        val workId = UUID.randomUUID()
        val workInfoLiveData = MutableLiveData<WorkInfo>()

        val workRequestSlot = slot<OneTimeWorkRequest>()

        every { workManager.enqueueUniqueWork(any(), any(), capture(workRequestSlot)) } returns mockk(relaxed = true)

        every { workManager.getWorkInfoByIdLiveData(any()) } returns workInfoLiveData

        val liveData = syncRepository.uploadBulkData()
        liveData.observeForever(observer)

        verify { workManager.enqueueUniqueWork("UploadUserData_Bulk", ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) }

        val capturedRequest = workRequestSlot.captured
        val expectedData = Data.Builder().putString(UserDataWorker.KEY_UPLOAD_TYPE, UserDataWorker.UPLOAD_TYPE_BULK).build()
        assertEquals(expectedData, capturedRequest.workSpec.input)

        // Test states
        val workInfo = mockk<WorkInfo>(relaxed = true)

        // 1. RUNNING
        every { workInfo.state } returns WorkInfo.State.RUNNING
        workInfoLiveData.value = workInfo
        verify { observer.onChanged(SyncUiState.Loading) }

        // 2. SUCCEEDED
        val successData = Data.Builder().putString(UserDataWorker.KEY_SUCCESS_MESSAGE, "Upload complete").build()
        every { workInfo.state } returns WorkInfo.State.SUCCEEDED
        every { workInfo.outputData } returns successData
        workInfoLiveData.value = workInfo
        verify { observer.onChanged(SyncUiState.Success("Upload complete")) }

        // 3. FAILED
        every { workInfo.state } returns WorkInfo.State.FAILED
        workInfoLiveData.value = workInfo
        verify { observer.onChanged(SyncUiState.Error("Upload failed")) }

        // 4. CANCELLED
        every { workInfo.state } returns WorkInfo.State.CANCELLED
        workInfoLiveData.value = workInfo
        verify { observer.onChanged(SyncUiState.Error("Upload cancelled")) }

        // 5. ENQUEUED (Idle)
        every { workInfo.state } returns WorkInfo.State.ENQUEUED
        workInfoLiveData.value = workInfo
        verify { observer.onChanged(SyncUiState.Idle) }

        liveData.removeObserver(observer)
    }
}

package org.ole.planet.myplanet.services

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.clearAllMocks
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.util.Log
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils

@OptIn(ExperimentalCoroutinesApi::class)
class FreeSpaceWorkerTest {

    private lateinit var worker: FreeSpaceWorker
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var resourcesRepository: ResourcesRepository
    private lateinit var dispatcherProvider: DispatcherProvider

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        resourcesRepository = mockk(relaxed = true)

        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        worker = spyk(FreeSpaceWorker(context, workerParams, resourcesRepository, dispatcherProvider))

        // Mock setProgress
        coEvery { worker.setProgress(any()) } returns Unit

        mockkStatic(FileUtils::class)
        every { FileUtils.getOlePath(any()) } returns "mock/ole/path"

        // Mock application context for getOlePath
        every { context.applicationContext } returns context

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `doWork should mark all resources offline and return success`() = runTest(testDispatcher) {
        coEvery { resourcesRepository.markAllResourcesOffline(false) } returns Unit

        val result = worker.doWork()

        advanceUntilIdle()

        coVerify { resourcesRepository.markAllResourcesOffline(false) }
        assertTrue(result is Result.Success)

        val outputData = (result as Result.Success).outputData
        assertEquals(0, outputData.getInt("deletedFiles", -1))
        assertEquals(0L, outputData.getLong("freedBytes", -1L))
    }

    @Test
    fun `doWork should return failure on exception`() = runTest(testDispatcher) {
        coEvery { resourcesRepository.markAllResourcesOffline(false) } throws RuntimeException("Simulated exception")

        val result = worker.doWork()

        assertTrue(result is Result.Failure)
    }
}

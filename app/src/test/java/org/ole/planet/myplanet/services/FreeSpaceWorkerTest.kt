package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.FileUtils

class FreeSpaceWorkerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private lateinit var worker: FreeSpaceWorker
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var resourcesRepository: ResourcesRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var oleDir: File

    private val testDispatcher get() = mainDispatcherRule.testDispatcher

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

        oleDir = Files.createTempDirectory("ole-test").toFile()
        mockkObject(FileUtils)
        every { FileUtils.getOlePath(any()) } returns oleDir.path

        // Mock application context for getOlePath
        every { context.applicationContext } returns context

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        oleDir.deleteRecursively()
        clearAllMocks()
        unmockkAll()
    }

    private fun addResource(id: String, fileName: String, content: String = "data"): File {
        val dir = File(oleDir, id).apply { mkdirs() }
        return File(dir, fileName).apply { writeText(content) }
    }

    @Test
    fun `doWork deletes resource files then clears only their offline flags`() = runTest(testDispatcher) {
        val book = addResource("res1", "book.pdf")
        val video = addResource("res2", "video.mp4")

        val result = worker.doWork()
        advanceUntilIdle()

        assertTrue(result is Result.Success)
        assertFalse(book.exists())
        assertFalse(video.exists())
        coVerify {
            resourcesRepository.markResourcesAsNotOffline(match { it.toSet() == setOf("res1", "res2") })
        }
        coVerify(exactly = 0) { resourcesRepository.markAllResourcesOffline(any()) }

        val outputData = (result as Result.Success).outputData
        assertTrue(outputData.getInt("deletedFiles", -1) > 0)
        assertTrue(outputData.getLong("freedBytes", -1L) > 0L)
    }

    @Test
    fun `doWork preserves the cv directory holding pending achievement uploads`() = runTest(testDispatcher) {
        val resume = addResource("cv", "resume.pdf", "cv content")
        addResource("res1", "book.pdf")

        val result = worker.doWork()
        advanceUntilIdle()

        assertTrue(result is Result.Success)
        assertTrue(resume.exists())
        coVerify(exactly = 0) {
            resourcesRepository.markResourcesAsNotOffline(match { "cv" in it })
        }
    }

    @Test
    fun `doWork with missing ole directory succeeds without touching the database`() = runTest(testDispatcher) {
        oleDir.deleteRecursively()

        val result = worker.doWork()
        advanceUntilIdle()

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { resourcesRepository.markResourcesAsNotOffline(any()) }

        val outputData = (result as Result.Success).outputData
        assertEquals(0, outputData.getInt("deletedFiles", -1))
        assertEquals(0L, outputData.getLong("freedBytes", -1L))
    }

    @Test
    fun `doWork should return failure on exception`() = runTest(testDispatcher) {
        addResource("res1", "book.pdf")
        coEvery { resourcesRepository.markResourcesAsNotOffline(any()) } throws RuntimeException("Simulated exception")

        val result = worker.doWork()

        assertTrue(result is Result.Failure)
    }
}

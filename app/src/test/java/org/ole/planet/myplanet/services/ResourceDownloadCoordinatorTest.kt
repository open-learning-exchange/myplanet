package org.ole.planet.myplanet.services

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceDownloadCoordinatorTest {

    private lateinit var configurationsRepository: ConfigurationsRepository
    private lateinit var resourcesRepository: ResourcesRepository
    private lateinit var prefData: SharedPrefManager
    private lateinit var context: Context
    private lateinit var applicationScope: CoroutineScope
    private lateinit var coordinator: ResourceDownloadCoordinator

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setUp() {
        configurationsRepository = mockk(relaxed = true)
        resourcesRepository = mockk(relaxed = true)
        prefData = mockk(relaxed = true)
        context = mockk(relaxed = true)
        applicationScope = TestScope(testDispatcher)

        mockkObject(DownloadUtils)

        coordinator = ResourceDownloadCoordinator(
            configurationsRepository,
            resourcesRepository,
            prefData,
            testDispatcherProvider,
            context,
            applicationScope
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startBackgroundDownload triggers openDownloadService when server is available and urls not empty`() = runTest(testDispatcher) {
        coEvery { configurationsRepository.checkServerAvailability() } returns true
        val urls = arrayListOf("http://example.com/file.pdf")

        coordinator.startBackgroundDownload(urls)
        advanceUntilIdle()

        verify(exactly = 1) { DownloadUtils.openDownloadService(context, urls, false) }
    }

    @Test
    fun `startBackgroundDownload does not trigger openDownloadService when server is unavailable`() = runTest(testDispatcher) {
        coEvery { configurationsRepository.checkServerAvailability() } returns false
        val urls = arrayListOf("http://example.com/file.pdf")

        coordinator.startBackgroundDownload(urls)
        advanceUntilIdle()

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), any()) }
    }

    @Test
    fun `startBackgroundDownload does not trigger openDownloadService when urls list is empty`() = runTest(testDispatcher) {
        coEvery { configurationsRepository.checkServerAvailability() } returns true
        val urls = arrayListOf<String>()

        coordinator.startBackgroundDownload(urls)
        advanceUntilIdle()

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), any()) }
    }

    @Test
    fun `runPostSyncDownloads executes queued downloads then beta auto download in order`() = runTest(testDispatcher) {
        val queuedLinks = arrayListOf("http://example.com/queued1.pdf", "http://example.com/queued2.pdf")
        val libraries = listOf(mockk<MyLibrary>(relaxed = true))
        val downloadedUrls = arrayListOf("http://example.com/library1.pdf")

        coEvery { configurationsRepository.getQueuedDownloads() } returns queuedLinks
        every { prefData.getBetaAutoDownload() } returns true
        coEvery { resourcesRepository.getAllLibrariesToSync() } returns libraries
        every { DownloadUtils.downloadAllFiles(libraries) } returns downloadedUrls
        coEvery { configurationsRepository.checkServerAvailability() } returns true

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        coVerifyOrder {
            configurationsRepository.getQueuedDownloads()
            DownloadUtils.openDownloadService(context, queuedLinks, true)
            prefData.getBetaAutoDownload()
            resourcesRepository.getAllLibrariesToSync()
            DownloadUtils.downloadAllFiles(libraries)
            configurationsRepository.checkServerAvailability()
            DownloadUtils.openDownloadService(context, downloadedUrls, false)
        }
    }

    @Test
    fun `runPostSyncDownloads skips openDownloadService for queued downloads when queued downloads list is empty`() = runTest(testDispatcher) {
        val libraries = listOf(mockk<MyLibrary>(relaxed = true))
        val downloadedUrls = arrayListOf("http://example.com/library1.pdf")

        coEvery { configurationsRepository.getQueuedDownloads() } returns emptyList()
        every { prefData.getBetaAutoDownload() } returns true
        coEvery { resourcesRepository.getAllLibrariesToSync() } returns libraries
        every { DownloadUtils.downloadAllFiles(libraries) } returns downloadedUrls
        coEvery { configurationsRepository.checkServerAvailability() } returns true

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        verify(exactly = 0) { DownloadUtils.openDownloadService(context, any(), true) }
        verify(exactly = 1) { DownloadUtils.openDownloadService(context, downloadedUrls, false) }
    }

    @Test
    fun `runPostSyncDownloads skips beta auto download when beta auto download is off`() = runTest(testDispatcher) {
        val queuedLinks = arrayListOf("http://example.com/queued1.pdf")

        coEvery { configurationsRepository.getQueuedDownloads() } returns queuedLinks
        every { prefData.getBetaAutoDownload() } returns false

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        verify(exactly = 1) { DownloadUtils.openDownloadService(context, queuedLinks, true) }
        coVerify(exactly = 0) { resourcesRepository.getAllLibrariesToSync() }
        verify(exactly = 0) { DownloadUtils.downloadAllFiles(any()) }
    }
}

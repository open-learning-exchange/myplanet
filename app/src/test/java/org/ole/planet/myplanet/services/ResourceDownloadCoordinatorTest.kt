package org.ole.planet.myplanet.services

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
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
    fun `runPostSyncDownloads processes queued downloads and beta auto downloads in order when both enabled`() = runTest(testDispatcher) {
        val queuedLinks = listOf("http://example.com/queued1.pdf", "http://example.com/queued2.pdf")
        val libraryItems = listOf(mockk<MyLibrary>())
        val betaUrls = arrayListOf("http://example.com/beta1.pdf")

        coEvery { configurationsRepository.getQueuedDownloads() } returns queuedLinks
        every { prefData.getBetaAutoDownload() } returns true
        coEvery { resourcesRepository.getAllLibrariesToSync() } returns libraryItems
        every { DownloadUtils.downloadAllFiles(libraryItems) } returns betaUrls
        coEvery { configurationsRepository.checkServerAvailability() } returns true

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        verifyOrder {
            DownloadUtils.openDownloadService(context, ArrayList(queuedLinks), true)
            DownloadUtils.downloadAllFiles(libraryItems)
            DownloadUtils.openDownloadService(context, betaUrls, false)
        }
    }

    @Test
    fun `runPostSyncDownloads handles empty queued downloads with beta auto download enabled`() = runTest(testDispatcher) {
        val libraryItems = listOf(mockk<MyLibrary>())
        val betaUrls = arrayListOf("http://example.com/beta1.pdf")

        coEvery { configurationsRepository.getQueuedDownloads() } returns emptyList()
        every { prefData.getBetaAutoDownload() } returns true
        coEvery { resourcesRepository.getAllLibrariesToSync() } returns libraryItems
        every { DownloadUtils.downloadAllFiles(libraryItems) } returns betaUrls
        coEvery { configurationsRepository.checkServerAvailability() } returns true

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), true) }
        verify(exactly = 1) { DownloadUtils.openDownloadService(context, betaUrls, false) }
    }

    @Test
    fun `runPostSyncDownloads handles queued downloads present with beta auto download disabled`() = runTest(testDispatcher) {
        val queuedLinks = listOf("http://example.com/queued1.pdf")

        coEvery { configurationsRepository.getQueuedDownloads() } returns queuedLinks
        every { prefData.getBetaAutoDownload() } returns false

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        verify(exactly = 1) { DownloadUtils.openDownloadService(context, ArrayList(queuedLinks), true) }
        verify(exactly = 0) { DownloadUtils.downloadAllFiles(any()) }
        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), false) }
    }

    @Test
    fun `runPostSyncDownloads does nothing when queued downloads empty and beta auto download disabled`() = runTest(testDispatcher) {
        coEvery { configurationsRepository.getQueuedDownloads() } returns emptyList()
        every { prefData.getBetaAutoDownload() } returns false

        coordinator.runPostSyncDownloads()
        advanceUntilIdle()

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), any()) }
        verify(exactly = 0) { DownloadUtils.downloadAllFiles(any()) }
    }
}

package org.ole.planet.myplanet.services

import android.content.Context
import io.mockk.coEvery
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
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.DownloadUtils

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceDownloadCoordinatorTest {

    private lateinit var configurationsRepository: ConfigurationsRepository
    private lateinit var context: Context
    private lateinit var applicationScope: CoroutineScope
    private lateinit var coordinator: ResourceDownloadCoordinator

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        configurationsRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        applicationScope = TestScope(testDispatcher)

        mockkObject(DownloadUtils)

        coordinator = ResourceDownloadCoordinator(
            configurationsRepository,
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
}

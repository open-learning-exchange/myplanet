package org.ole.planet.myplanet.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.DownloadUtils
import org.robolectric.annotation.Config
import java.util.ArrayList

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ResourceDownloadCoordinatorTest {

    private lateinit var coordinator: ResourceDownloadCoordinator
    private lateinit var configurationsRepository: ConfigurationsRepository
    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = spyk(ApplicationProvider.getApplicationContext())
        configurationsRepository = mockk()
        MainApplication.applicationScope = TestScope(testDispatcher)

        coordinator = ResourceDownloadCoordinator(configurationsRepository, context)
        mockkObject(DownloadUtils)
        every { DownloadUtils.openDownloadService(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `startBackgroundDownload starts service when server available and urls not empty`() = runTest {
        coEvery { configurationsRepository.checkServerAvailability() } returns true
        val urls = arrayListOf("url1", "url2")

        coordinator.startBackgroundDownload(urls)

        verify { DownloadUtils.openDownloadService(context, urls, false) }
    }

    @Test
    fun `startBackgroundDownload does not start service when server unavailable`() = runTest {
        coEvery { configurationsRepository.checkServerAvailability() } returns false
        val urls = arrayListOf("url1", "url2")

        coordinator.startBackgroundDownload(urls)

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), any()) }
    }

    @Test
    fun `startBackgroundDownload does not start service when urls empty`() = runTest {
        coEvery { configurationsRepository.checkServerAvailability() } returns true
        val urls = arrayListOf<String>()

        coordinator.startBackgroundDownload(urls)

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), any()) }
    }
}

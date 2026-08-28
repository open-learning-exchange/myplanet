package org.ole.planet.myplanet.ui.viewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResourceViewerViewModelTest {

    private lateinit var context: Context
    private lateinit var sharedPrefManager: SharedPrefManager
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val configurationsRepository: ConfigurationsRepository = mockk(relaxed = true)
    private lateinit var viewModel: ResourceViewerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sharedPrefManager = SharedPrefManager(context, mockk(relaxed = true))
        viewModel = ResourceViewerViewModel(
            context = context,
            resourcesRepository = resourcesRepository,
            authSessionUpdaterFactory = mockk(relaxed = true),
            configurationsRepository = configurationsRepository,
            sharedPrefManager = sharedPrefManager,
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())
        )
    }

    @Test
    fun saveAndGetPlaybackProgress() {
        assertEquals(0L, viewModel.getPlaybackProgress("res123"))

        viewModel.savePlaybackProgress("res123", 45000L)
        assertEquals(45000L, viewModel.getPlaybackProgress("res123"))

        viewModel.savePlaybackProgress("res123", 0L)
        assertEquals(0L, viewModel.getPlaybackProgress("res123"))
    }

    @Test
    fun saveAndGetPlaybackSpeed() {
        assertEquals(1.0f, viewModel.getPlaybackSpeed(), 0.001f)

        viewModel.savePlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.getPlaybackSpeed(), 0.001f)

        viewModel.savePlaybackSpeed(0.75f)
        assertEquals(0.75f, viewModel.getPlaybackSpeed(), 0.001f)
    }
}

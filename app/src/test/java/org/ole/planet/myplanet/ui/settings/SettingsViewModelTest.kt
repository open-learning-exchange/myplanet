package org.ole.planet.myplanet.ui.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val mainImmediate = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private val configurationsRepository: ConfigurationsRepository = mockk(relaxed = true)
    private val retryRepository: RetryRepository = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)

    @Test
    fun `clearAllData calls clearAllData and clearPreferences on configurationsRepository and emits clearDataEvent`() = runTest {
        coEvery { configurationsRepository.clearAllData() } just runs
        every { configurationsRepository.clearPreferences() } just runs

        val viewModel = SettingsViewModel(
            configurationsRepository,
            retryRepository,
            resourcesRepository,
            dispatcherProvider
        )

        viewModel.clearAllData()
        advanceUntilIdle()

        val event = viewModel.clearDataEvent.first()
        assertNotNull(event)

        coVerify { configurationsRepository.clearAllData() }
        verify { configurationsRepository.clearPreferences() }
    }
}

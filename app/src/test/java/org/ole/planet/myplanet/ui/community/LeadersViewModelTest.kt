package org.ole.planet.myplanet.ui.community

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LeadersViewModelTest {

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

    private val configurationsRepository: ConfigurationsRepository = mockk()


    @Test
    fun `loadLeaders parses non-empty community leaders from configurationsRepository`() = runTest {
        val leadersJson = """{"docs":[{"_id":"leader_1","name":"Alice"}]}"""
        every { configurationsRepository.getCommunityLeaders() } returns leadersJson

        val viewModel = LeadersViewModel(configurationsRepository, dispatcherProvider)
        advanceUntilIdle()

        val result = viewModel.leaders.first()
        assertEquals(1, result?.size)
        assertEquals("leader_1", result?.get(0)?.id)
        verify { configurationsRepository.getCommunityLeaders() }
    }

    @Test
    fun `loadLeaders returns empty list when community leaders is empty`() = runTest {
        every { configurationsRepository.getCommunityLeaders() } returns ""

        val viewModel = LeadersViewModel(configurationsRepository, dispatcherProvider)
        advanceUntilIdle()

        val result = viewModel.leaders.first()
        assertEquals(emptyList<Any>(), result)
        verify { configurationsRepository.getCommunityLeaders() }
    }
}

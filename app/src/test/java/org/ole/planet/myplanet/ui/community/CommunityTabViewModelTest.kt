package org.ole.planet.myplanet.ui.community

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityTabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val configurationsRepository: ConfigurationsRepository = mockk()
    private val userSessionManager: UserSessionManager = mockk()

    @Before
    fun setup() {
        every { configurationsRepository.getParentCode() } returns "parent_code_123"
        every { configurationsRepository.getCommunityName() } returns "community_abc"
        every { configurationsRepository.getPlanetType() } returns "planet_xyz"
    }

    @Test
    fun `init populates state with values from configurationsRepository and userSessionManager`() = runTest {
        val user = UserEntity().apply { planetCode = "planet_code_999" }
        coEvery { userSessionManager.getUserModel() } returns user

        val viewModel = CommunityTabViewModel(configurationsRepository, userSessionManager)
        advanceUntilIdle()

        val state = viewModel.state.first()
        assertEquals("planet_code_999", state?.planetCode)
        assertEquals("parent_code_123", state?.parentCode)
        assertEquals("community_abc", state?.communityName)
        assertEquals("planet_xyz", state?.planetType)

        verify { configurationsRepository.getParentCode() }
        verify { configurationsRepository.getCommunityName() }
        verify { configurationsRepository.getPlanetType() }
        coVerify { userSessionManager.getUserModel() }
    }
}

package org.ole.planet.myplanet.ui.community

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityServicesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val teamsRepository: TeamsRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    @Before
    fun setup() {
        coEvery { teamsRepository.getTeamLinks() } returns emptyList()
    }

    @Test
    fun `init populates teamLinks flow with values from teamsRepository`() = runTest {
        val mockTeam = MyTeam().apply { title = "Health Services" }
        coEvery { teamsRepository.getTeamLinks() } returns listOf(mockTeam)

        val viewModel = CommunityServicesViewModel(teamsRepository, userRepository, dispatcherProvider)
        advanceUntilIdle()

        val links = viewModel.teamLinks.first()
        assertEquals(1, links.size)
        assertEquals("Health Services", links[0].title)
        coVerify { teamsRepository.getTeamLinks() }
    }

    @Test
    fun `isMember queries teamsRepository with user id and team id`() = runTest {
        coEvery { teamsRepository.isMember("user_123", "team_456") } returns true
        coEvery { teamsRepository.isMember(null, "team_456") } returns false

        val viewModel = CommunityServicesViewModel(teamsRepository, userRepository, dispatcherProvider)
        advanceUntilIdle()

        val isMemberUser = viewModel.isMember("user_123", "team_456")
        assertTrue(isMemberUser)

        val isMemberNull = viewModel.isMember(null, "team_456")
        assertFalse(isMemberNull)

        coVerify { teamsRepository.isMember("user_123", "team_456") }
        coVerify { teamsRepository.isMember(null, "team_456") }
    }
}

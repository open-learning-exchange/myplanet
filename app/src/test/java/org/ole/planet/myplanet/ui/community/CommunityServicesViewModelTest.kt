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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamsMembersRepository
import org.ole.planet.myplanet.repository.TeamsShareRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityServicesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val teamsShareRepository: TeamsShareRepository = mockk()
    private val teamsMembersRepository: TeamsMembersRepository = mockk()

    @Before
    fun setup() {
        coEvery { teamsShareRepository.getTeamLinks() } returns emptyList()
    }

    @Test
    fun `init populates teamLinks flow with values from teamsRepository`() = runTest {
        val mockTeam = MyTeam().apply { title = "Health Services" }
        coEvery { teamsShareRepository.getTeamLinks() } returns listOf(mockTeam)

        val viewModel = CommunityServicesViewModel(teamsShareRepository, teamsMembersRepository)
        advanceUntilIdle()

        val links = viewModel.teamLinks.first { it != null }
        assertNotNull(links)
        assertEquals(1, links?.size)
        assertEquals("Health Services", links?.get(0)?.title)
        coVerify { teamsShareRepository.getTeamLinks() }
    }

    @Test
    fun `isMember queries teamsRepository with user id and team id`() = runTest {
        coEvery { teamsMembersRepository.isMember("user_123", "team_456") } returns true
        coEvery { teamsMembersRepository.isMember(null, "team_456") } returns false

        val viewModel = CommunityServicesViewModel(teamsShareRepository, teamsMembersRepository)
        advanceUntilIdle()

        val isMemberUser = viewModel.isMember("user_123", "team_456")
        assertTrue(isMemberUser)

        val isMemberNull = viewModel.isMember(null, "team_456")
        assertFalse(isMemberNull)

        coVerify { teamsMembersRepository.isMember("user_123", "team_456") }
        coVerify { teamsMembersRepository.isMember(null, "team_456") }
    }

    @Test
    fun `isMember returns the repository's answer unchanged for a null userId`() = runTest {
        coEvery { teamsMembersRepository.isMember(null, "t") } returns false

        val viewModel = CommunityServicesViewModel(teamsShareRepository, teamsMembersRepository)
        advanceUntilIdle()

        val result = viewModel.isMember(null, "t")
        assertFalse(result)

        coVerify { teamsMembersRepository.isMember(null, "t") }
    }
}

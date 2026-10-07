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
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityServicesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val teamsRepository: TeamsRepository = mockk()

    @Before
    fun setup() {
        coEvery { teamsRepository.getTeamLinks() } returns emptyList()
    }

    @Test
    fun `init populates teamLinks flow with values from teamsRepository`() = runTest {
        val mockTeam = MyTeam().apply { title = "Health Services" }
        coEvery { teamsRepository.getTeamLinks() } returns listOf(mockTeam)

        val viewModel = CommunityServicesViewModel(teamsRepository)
        advanceUntilIdle()

        val links = viewModel.teamLinks.first { it != null }
        assertNotNull(links)
        assertEquals(1, links?.size)
        assertEquals("Health Services", links?.get(0)?.title)
        coVerify { teamsRepository.getTeamLinks() }
    }

    @Test
    fun `isMember queries teamsRepository with user id and team id`() = runTest {
        coEvery { teamsRepository.isMember("user_123", "team_456") } returns true
        coEvery { teamsRepository.isMember(null, "team_456") } returns false

        val viewModel = CommunityServicesViewModel(teamsRepository)
        advanceUntilIdle()

        val isMemberUser = viewModel.isMember("user_123", "team_456")
        assertTrue(isMemberUser)

        val isMemberNull = viewModel.isMember(null, "team_456")
        assertFalse(isMemberNull)

        coVerify { teamsRepository.isMember("user_123", "team_456") }
        coVerify { teamsRepository.isMember(null, "team_456") }
    }

    @Test
    fun `isMember returns the repository's answer unchanged for a null userId`() = runTest {
        coEvery { teamsRepository.isMember(null, "t") } returns false

        val viewModel = CommunityServicesViewModel(teamsRepository)
        advanceUntilIdle()

        val result = viewModel.isMember(null, "t")
        assertFalse(result)

        coVerify { teamsRepository.isMember(null, "t") }
    }
}

package org.ole.planet.myplanet.ui.teams

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.TeamMemberStatus
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {

    private lateinit var viewModel: TeamViewModel
    private val teamsRepository = mockk<TeamsRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeamViewModel(teamsRepository, testDispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prepareTeamData sorts teams correctly leader then member then neither`() = runTest(testDispatcher) {
        val teams = listOf(
            TeamSummary(_id = "team1", name = "Team 1", teamType = null, teamPlanetCode = null, createdDate = null, type = null, status = "active", teamId = null, description = null, services = null, rules = null),
            TeamSummary(_id = "team2", name = "Team 2", teamType = null, teamPlanetCode = null, createdDate = null, type = null, status = "active", teamId = null, description = null, services = null, rules = null),
            TeamSummary(_id = "team3", name = "Team 3", teamType = null, teamPlanetCode = null, createdDate = null, type = null, status = "active", teamId = null, description = null, services = null, rules = null)
        )

        coEvery { teamsRepository.getRecentVisitCounts(any()) } returns mapOf(
            "team1" to 0L,
            "team2" to 0L,
            "team3" to 0L
        )

        // team1 = neither, team2 = member, team3 = leader
        coEvery { teamsRepository.getTeamMemberStatuses(any(), any()) } returns mapOf(
            "team1" to TeamMemberStatus(isMember = false, isLeader = false, hasPendingRequest = false),
            "team2" to TeamMemberStatus(isMember = true, isLeader = false, hasPendingRequest = false),
            "team3" to TeamMemberStatus(isMember = true, isLeader = true, hasPendingRequest = false)
        )

        viewModel.prepareTeamData(teams, "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(3, data.size)
        assertEquals("team3", data[0]._id) // Leader
        assertEquals("team2", data[1]._id) // Member
        assertEquals("team1", data[2]._id) // Neither
    }

    @Test
    fun `prepareTeamData removes archived teams`() = runTest(testDispatcher) {
        val teams = listOf(
            TeamSummary(_id = "team1", name = "Team 1", teamType = null, teamPlanetCode = null, createdDate = null, type = null, status = "archived", teamId = null, description = null, services = null, rules = null),
            TeamSummary(_id = "team2", name = "Team 2", teamType = null, teamPlanetCode = null, createdDate = null, type = null, status = "active", teamId = null, description = null, services = null, rules = null)
        )

        coEvery { teamsRepository.getRecentVisitCounts(any()) } returns mapOf(
            "team2" to 0L
        )

        coEvery { teamsRepository.getTeamMemberStatuses(any(), any()) } returns mapOf(
            "team2" to TeamMemberStatus(isMember = false, isLeader = false, hasPendingRequest = false)
        )

        viewModel.prepareTeamData(teams, "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(1, data.size)
        assertEquals("team2", data[0]._id)
    }

    @Test
    fun `prepareTeamData with empty list returns empty without hitting repository`() = runTest(testDispatcher) {
        viewModel.prepareTeamData(emptyList(), "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertTrue(data.isEmpty())

        coVerify(exactly = 0) { teamsRepository.getRecentVisitCounts(any()) }
        coVerify(exactly = 0) { teamsRepository.getTeamMemberStatuses(any(), any()) }
    }
}

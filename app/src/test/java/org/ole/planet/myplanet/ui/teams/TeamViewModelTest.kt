package org.ole.planet.myplanet.ui.teams

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {

    private lateinit var viewModel: TeamViewModel
    private val teamsRepository = mockk<TeamsRepository>()
    private val realtimeSyncManager: RealtimeSyncManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeamViewModel(teamsRepository, testDispatcherProvider, realtimeSyncManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTeams sorts teams correctly leader then member then neither`() = runTest(testDispatcher) {
        val teams = listOf(
            TeamDetails(_id = "team3", name = "Team 3", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = TeamStatus(isMember = true, isLeader = true, hasPendingRequest = false), description = null, services = null, rules = null, teamId = null, profileImage = null),
            TeamDetails(_id = "team2", name = "Team 2", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = TeamStatus(isMember = true, isLeader = false, hasPendingRequest = false), description = null, services = null, rules = null, teamId = null, profileImage = null),
            TeamDetails(_id = "team1", name = "Team 1", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = TeamStatus(isMember = false, isLeader = false, hasPendingRequest = false), description = null, services = null, rules = null, teamId = null, profileImage = null)
        )

        coEvery { teamsRepository.getTeamDetails(any()) } returns teams

        viewModel.loadTeams(fromDashboard = false, type = "team", userId = "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(3, data.size)
        assertEquals("team3", data[0]._id) // Leader
        assertEquals("team2", data[1]._id) // Member
        assertEquals("team1", data[2]._id) // Neither
    }
    @Test
    fun `searchTeams filters by name correctly`() = runTest(testDispatcher) {
        val teams = listOf(
            TeamDetails(_id = "team1", name = "Alpha", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = null, description = null, services = null, rules = null, teamId = null, profileImage = null),
            TeamDetails(_id = "team2", name = "Beta", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = null, description = null, services = null, rules = null, teamId = null, profileImage = null)
        )

        coEvery { teamsRepository.getTeamDetails(any()) } returns teams

        viewModel.loadTeams(fromDashboard = false, type = "team", userId = "user1")
        advanceUntilIdle()

        viewModel.searchTeams("Alpha")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(1, data.size)
        assertEquals("team1", data[0]._id)
    }
    @Test
    fun `loadTeams with empty list returns empty`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamDetails(any()) } returns emptyList()

        viewModel.loadTeams(fromDashboard = false, type = "team", userId = "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertTrue(data.isEmpty())
    }
    @Test
    fun `taskList state is updated when loadTasks is called`() = runTest(testDispatcher) {
        val tasks = listOf(TeamTask().apply { id = "task1" })
        coEvery { teamsRepository.getTasksByTeamId("team1") } returns flowOf(tasks)

        viewModel.loadTasks("team1")
        advanceUntilIdle()

        assertEquals(1, viewModel.taskList.value.size)
        assertEquals("task1", viewModel.taskList.value[0].id)
    }

    @Test
    fun `loadTeams fromDashboard when user has joined teams shows joined teams`() = runTest(testDispatcher) {
        val myTeams = listOf(
            TeamDetails(_id = "team1", name = "My Team", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = TeamStatus(isMember = true, isLeader = false, hasPendingRequest = false), description = null, services = null, rules = null, teamId = null)
        )
        coEvery { teamsRepository.getMyTeamDetailsFlow("user1", "team") } returns flowOf(myTeams)

        viewModel.loadTeams(fromDashboard = true, type = "team", userId = "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(1, data.size)
        assertEquals("team1", data[0]._id)
    }

    @Test
    fun `loadTeams fromDashboard when userId is null loads all teams`() = runTest(testDispatcher) {
        val allTeams = listOf(
            TeamDetails(_id = "teamA", name = "Existing Team A", teamType = null, createdDate = null, type = null, status = "active", visitCount = 0L, teamStatus = null, description = null, services = null, rules = null, teamId = null)
        )
        coEvery { teamsRepository.getTeamDetails(null) } returns allTeams

        viewModel.loadTeams(fromDashboard = true, type = "team", userId = null)
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(1, data.size)
        assertEquals("teamA", data[0]._id)
    }

    @Test
    fun `loadTeams fromDashboard when userId is null and type is enterprise loads all enterprises`() = runTest(testDispatcher) {
        val allEnterprises = listOf(
            TeamDetails(_id = "entA", name = "Enterprise A", teamType = null, createdDate = null, type = "enterprise", status = "active", visitCount = 0L, teamStatus = null, description = null, services = null, rules = null, teamId = null)
        )
        coEvery { teamsRepository.getShareableEnterpriseDetails(null) } returns allEnterprises

        viewModel.loadTeams(fromDashboard = true, type = "enterprise", userId = null)
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(1, data.size)
        assertEquals("entA", data[0]._id)
    }

    @Test
    fun `loadTeams when fromDashboard is false and type is enterprise loads enterprise details`() = runTest(testDispatcher) {
        val allEnterprises = listOf(
            TeamDetails(_id = "ent1", name = "Enterprise 1", teamType = null, createdDate = null, type = "enterprise", status = "active", visitCount = 0L, teamStatus = null, description = null, services = null, rules = null, teamId = null)
        )
        coEvery { teamsRepository.getShareableEnterpriseDetails("user1") } returns allEnterprises

        viewModel.loadTeams(fromDashboard = false, type = "enterprise", userId = "user1")
        advanceUntilIdle()

        val data = viewModel.teamData.value
        assertEquals(1, data.size)
        assertEquals("ent1", data[0]._id)
    }
}

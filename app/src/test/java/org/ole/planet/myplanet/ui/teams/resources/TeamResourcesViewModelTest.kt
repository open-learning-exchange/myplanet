package org.ole.planet.myplanet.ui.teams.resources

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.TeamResourceDto
import org.ole.planet.myplanet.repository.TeamsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TeamResourcesViewModelTest {

    private lateinit var viewModel: TeamResourcesViewModel
    private val teamsRepository = mockk<TeamsRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeamResourcesViewModel(teamsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState is null until loadResources emits`() = runTest(testDispatcher) {
        assertNull(viewModel.uiState.value)
    }

    @Test
    fun `loadResources populates state with resources and canRemove when user is leader`() = runTest(testDispatcher) {
        val libraries = listOf(
            MyLibrary().apply { id = "r1"; title = "Resource 1" },
            MyLibrary().apply { id = "r2"; title = "Resource 2" }
        )
        coEvery { teamsRepository.getTeamResources("team1") } returns libraries
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } returns true

        viewModel.loadResources("team1", "user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state?.resources?.size)
        assertTrue(state?.canRemove == true)
    }

    @Test
    fun `loadResources sets canRemove false when user is not leader`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamResources("team1") } returns emptyList()
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } returns false

        viewModel.loadResources("team1", "user1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value?.canRemove == false)
    }

    @Test
    fun `loadResources with no resources yields empty list`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamResources("team1") } returns emptyList()
        coEvery { teamsRepository.isTeamLeader("team1", null) } returns false

        viewModel.loadResources("team1", null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value?.resources?.isEmpty() == true)
    }

    @Test
    fun `addResources links resources and records team activity`() = runTest(testDispatcher) {
        val dtos = listOf(TeamResourceDto("r1", "Resource 1"))
        coEvery { teamsRepository.addResourceLinks("team1", dtos, "user1") } returns Unit
        coEvery { teamsRepository.recordTeamActivity() } returns Unit

        viewModel.addResources("team1", dtos, "user1")

        coVerify(exactly = 1) { teamsRepository.addResourceLinks("team1", dtos, "user1") }
        coVerify(exactly = 1) { teamsRepository.recordTeamActivity() }
    }

    @Test
    fun `removeResource removes link and records team activity`() = runTest(testDispatcher) {
        coEvery { teamsRepository.removeResourceLink("team1", "r1") } returns Unit
        coEvery { teamsRepository.recordTeamActivity() } returns Unit

        viewModel.removeResource("team1", "r1")

        coVerify(exactly = 1) { teamsRepository.removeResourceLink("team1", "r1") }
        coVerify(exactly = 1) { teamsRepository.recordTeamActivity() }
    }

    @Test
    fun `removeResource does not fail when activity recording throws`() = runTest(testDispatcher) {
        coEvery { teamsRepository.removeResourceLink("team1", "r1") } returns Unit
        coEvery { teamsRepository.recordTeamActivity() } throws RuntimeException("recording failed")

        viewModel.removeResource("team1", "r1")

        coVerify(exactly = 1) { teamsRepository.removeResourceLink("team1", "r1") }
        coVerify(exactly = 1) { teamsRepository.recordTeamActivity() }
    }

    @Test
    fun `addResources does not fail when activity recording throws`() = runTest(testDispatcher) {
        val dtos = listOf(TeamResourceDto("r1", "Resource 1"))
        coEvery { teamsRepository.addResourceLinks("team1", dtos, "user1") } returns Unit
        coEvery { teamsRepository.recordTeamActivity() } throws RuntimeException("recording failed")

        viewModel.addResources("team1", dtos, "user1")

        coVerify(exactly = 1) { teamsRepository.addResourceLinks("team1", dtos, "user1") }
        coVerify(exactly = 1) { teamsRepository.recordTeamActivity() }
    }

    @Test
    fun `getAvailableResources delegates to teamsRepository`() = runTest(testDispatcher) {
        val libraries = listOf(MyLibrary().apply { id = "r1"; title = "Available 1" })
        coEvery { teamsRepository.getAvailableResourcesToAdd("team1") } returns libraries

        val result = viewModel.getAvailableResources("team1")

        assertEquals(1, result.size)
        assertEquals("Available 1", result[0].title)
    }

    @Test
    fun `loadResources executes queries concurrently`() = runTest(testDispatcher) {
        val libraries = listOf(MyLibrary().apply { id = "r1"; title = "Resource 1" })
        coEvery { teamsRepository.getTeamResources("team1") } coAnswers {
            delay(100)
            libraries
        }
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } coAnswers {
            delay(100)
            true
        }

        viewModel.loadResources("team1", "user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state?.resources?.size)
        assertTrue(state?.canRemove == true)
    }

    @Test
    fun `loadResources ignores duplicate calls while loadJob is active for same team and user`() = runTest(testDispatcher) {
        val libraries = listOf(MyLibrary().apply { id = "r1"; title = "Resource 1" })
        coEvery { teamsRepository.getTeamResources("team1") } coAnswers {
            delay(100)
            libraries
        }
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } coAnswers {
            delay(100)
            true
        }

        viewModel.loadResources("team1", "user1")
        viewModel.loadResources("team1", "user1")

        advanceUntilIdle()

        coVerify(exactly = 1) { teamsRepository.getTeamResources("team1") }
        coVerify(exactly = 1) { teamsRepository.isTeamLeader("team1", "user1") }
    }

    @Test
    fun `reload cancels active loadJob and triggers new queries`() = runTest(testDispatcher) {
        val firstLibraries = listOf(MyLibrary().apply { id = "r1"; title = "Resource 1" })
        val secondLibraries = listOf(
            MyLibrary().apply { id = "r1"; title = "Resource 1" },
            MyLibrary().apply { id = "r2"; title = "Resource 2" }
        )

        var callCount = 0
        coEvery { teamsRepository.getTeamResources("team1") } coAnswers {
            if (callCount++ == 0) firstLibraries else secondLibraries
        }
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } returns true

        viewModel.loadResources("team1", "user1")
        advanceUntilIdle()

        viewModel.reload("team1", "user1")
        advanceUntilIdle()

        coVerify(exactly = 2) { teamsRepository.getTeamResources("team1") }
        assertEquals(2, viewModel.uiState.value?.resources?.size)
    }

    @Test
    fun `loadResources for different team cancels superseded loadJob`() = runTest(testDispatcher) {
        val team1Libraries = listOf(MyLibrary().apply { id = "r1"; title = "Team 1 Resource" })
        val team2Libraries = listOf(MyLibrary().apply { id = "r2"; title = "Team 2 Resource" })

        coEvery { teamsRepository.getTeamResources("team1") } coAnswers {
            delay(200)
            team1Libraries
        }
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } coAnswers {
            delay(200)
            true
        }

        coEvery { teamsRepository.getTeamResources("team2") } coAnswers {
            delay(50)
            team2Libraries
        }
        coEvery { teamsRepository.isTeamLeader("team2", "user1") } coAnswers {
            delay(50)
            false
        }

        viewModel.loadResources("team1", "user1")
        viewModel.loadResources("team2", "user1")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state?.resources?.size)
        assertEquals("Team 2 Resource", state?.resources?.get(0)?.title)
        assertTrue(state?.canRemove == false)
    }
}

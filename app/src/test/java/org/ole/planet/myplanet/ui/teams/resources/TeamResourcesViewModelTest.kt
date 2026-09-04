package org.ole.planet.myplanet.ui.teams.resources

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        viewModel = TeamResourcesViewModel(teamsRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
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
            kotlinx.coroutines.delay(100)
            libraries
        }
        coEvery { teamsRepository.isTeamLeader("team1", "user1") } coAnswers {
            kotlinx.coroutines.delay(100)
            true
        }

        viewModel.loadResources("team1", "user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state?.resources?.size)
        assertTrue(state?.canRemove == true)
    }
}

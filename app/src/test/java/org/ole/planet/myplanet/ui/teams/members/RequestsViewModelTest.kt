package org.ole.planet.myplanet.ui.teams.members

import io.mockk.coEvery
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
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@ExperimentalCoroutinesApi
class RequestsViewModelTest {

    private lateinit var teamsRepository: TeamsRepository
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var viewModel: RequestsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        teamsRepository = mockk()
        userSessionManager = mockk()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        viewModel = RequestsViewModel(teamsRepository, userSessionManager, testDispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchMembers updates uiState correctly`() = runTest(testDispatcher) {
        val teamId = "team1"
        val user1 = RealmUser().apply { id = "user1" }
        val user2 = RealmUser().apply { id = "user2" }
        val members = listOf(user1, user2)

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMembers(teamId) } returns listOf(user1)

        val currentUser = RealmUser().apply { id = "currentUser" }
        coEvery { userSessionManager.getUserModel() } returns currentUser
        coEvery { teamsRepository.isTeamLeader(teamId, currentUser.id) } returns true

        viewModel.fetchMembers(teamId)

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(members, uiState.members)
        assertEquals(2, uiState.members.size)
        assertTrue(uiState.isLeader)
        assertEquals(1, uiState.memberCount)
    }

    @Test
    fun `respondToRequest success path removes user optimistically and fetches members`() = runTest(testDispatcher) {
        val teamId = "team1"
        val user1 = RealmUser().apply { id = "user1" }
        val user2 = RealmUser().apply { id = "user2" }
        val members = listOf(user1, user2)

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMembers(teamId) } returns emptyList()
        coEvery { userSessionManager.getUserModel() } returns null
        coEvery { teamsRepository.isTeamLeader(teamId, null) } returns false

        viewModel.fetchMembers(teamId)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.members.size)

        coEvery { teamsRepository.respondToMemberRequest(teamId, user1.id!!, true) } returns Result.success(Unit)
        coEvery { teamsRepository.syncTeamActivities() } returns Unit

        // Setup fetchMembers for the success path
        val newMembers = listOf(user2)
        coEvery { teamsRepository.getRequestedMembers(teamId) } returns newMembers

        viewModel.respondToRequest(teamId, user1, true)

        // Assert optimistic update before coroutines finish.
        // This relies on the ViewModel applying it synchronously before the coroutine suspension.
        val uiStateBeforeCompletion = viewModel.uiState.value
        assertEquals(1, uiStateBeforeCompletion.members.size)
        assertEquals(user2.id, uiStateBeforeCompletion.members[0].id)

        advanceUntilIdle()

        val uiStateAfterCompletion = viewModel.uiState.value
        assertEquals(1, uiStateAfterCompletion.members.size)
        assertEquals(user2.id, uiStateAfterCompletion.members[0].id)
    }

    @Test
    fun `respondToRequest failure path reverts to original list`() = runTest(testDispatcher) {
        val teamId = "team1"
        val user1 = RealmUser().apply { id = "user1" }
        val user2 = RealmUser().apply { id = "user2" }
        val members = listOf(user1, user2)

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMembers(teamId) } returns emptyList()
        coEvery { userSessionManager.getUserModel() } returns null
        coEvery { teamsRepository.isTeamLeader(teamId, null) } returns false

        viewModel.fetchMembers(teamId)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.members.size)

        coEvery { teamsRepository.respondToMemberRequest(teamId, user1.id!!, true) } returns Result.failure(Exception("err"))

        viewModel.respondToRequest(teamId, user1, true)

        // Assert optimistic update before coroutines finish.
        // This relies on the ViewModel applying it synchronously before the coroutine suspension.
        val uiStateBeforeCompletion = viewModel.uiState.value
        assertEquals(1, uiStateBeforeCompletion.members.size)
        assertEquals(user2.id, uiStateBeforeCompletion.members[0].id)

        advanceUntilIdle()

        val uiStateAfterCompletion = viewModel.uiState.value
        assertEquals(2, uiStateAfterCompletion.members.size)
        assertEquals(user1.id, uiStateAfterCompletion.members[0].id)
        assertEquals(user2.id, uiStateAfterCompletion.members[1].id)
    }
}

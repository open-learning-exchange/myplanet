package org.ole.planet.myplanet.ui.teams.members

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsMembersRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelLeaderHandOffTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var teamsRepository: TeamsMembersRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: RequestsViewModel

    @Before
    fun setup() {
        teamsRepository = mockk()
        userRepository = mockk()
        viewModel = RequestsViewModel(teamsRepository, userRepository)
    }

    private fun createUserWithNullId(): UserEntity {
        val user = UserEntity()
        UserEntity::class.java.getDeclaredField("id").apply {
            isAccessible = true
            set(user, null)
        }
        return user
    }

    @Test
    fun `leaveTeam with a candidate promotes candidate and leaves team`() = runTest {
        val teamId = "team1"
        val myId = "user1"
        val candidateId = "candidate1"
        val currentUser = UserEntity().apply { id = myId }
        val candidate = UserEntity().apply { id = candidateId }

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, myId) } returns candidate
        coEvery { teamsRepository.updateTeamLeader(teamId, candidateId) } returns true
        coEvery { teamsRepository.removeMember(teamId, myId) } returns Unit
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.leaveTeam(teamId)
        advanceUntilIdle()

        coVerifyOrder {
            teamsRepository.getNextLeaderCandidate(teamId, myId)
            teamsRepository.updateTeamLeader(teamId, candidateId)
            teamsRepository.removeMember(teamId, myId)
        }
        assertEquals(listOf(MemberActionResult.LeftTeam), results)
    }

    @Test
    fun `leaveTeam with no candidate leaves team without promoting`() = runTest {
        val teamId = "team1"
        val myId = "user1"
        val currentUser = UserEntity().apply { id = myId }

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, myId) } returns null
        coEvery { teamsRepository.removeMember(teamId, myId) } returns Unit
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.leaveTeam(teamId)
        advanceUntilIdle()

        coVerify(exactly = 0) { teamsRepository.updateTeamLeader(any(), any()) }
        coVerify(exactly = 1) { teamsRepository.removeMember(teamId, myId) }
        assertEquals(listOf(MemberActionResult.LeftTeam), results)
    }

    @Test
    fun `leaveTeam when candidate has null id leaves team without updating leader`() = runTest {
        val teamId = "team1"
        val myId = "user1"
        val currentUser = UserEntity().apply { id = myId }
        val candidateWithNullId = createUserWithNullId()

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, myId) } returns candidateWithNullId
        coEvery { teamsRepository.removeMember(teamId, myId) } returns Unit
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.leaveTeam(teamId)
        advanceUntilIdle()

        coVerify(exactly = 0) { teamsRepository.updateTeamLeader(any(), any()) }
        coVerify(exactly = 1) { teamsRepository.removeMember(teamId, myId) }
        assertEquals(listOf(MemberActionResult.LeftTeam), results)
    }

    @Test
    fun `removeMember on self when candidate has null id removes member without updating leader`() = runTest {
        val teamId = "team1"
        val myId = "user1"
        val currentUser = UserEntity().apply { id = myId }
        val candidateWithNullId = createUserWithNullId()

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, myId) } returns candidateWithNullId
        coEvery { teamsRepository.removeMember(teamId, myId) } returns Unit
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.removeMember(teamId, myId)
        advanceUntilIdle()

        coVerify(exactly = 0) { teamsRepository.updateTeamLeader(any(), any()) }
        coVerify(exactly = 1) { teamsRepository.removeMember(teamId, myId) }
        assertEquals(listOf(MemberActionResult.MemberRemoved), results)
    }

    @Test
    fun `leaveTeam when getUserModel returns null calls getNextLeaderCandidate with null and emits LeftTeam without removeMember`() = runTest {
        val teamId = "team1"

        coEvery { userRepository.getUserModel() } returns null
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, null) } returns null
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.leaveTeam(teamId)
        advanceUntilIdle()

        coVerify(exactly = 1) { teamsRepository.getNextLeaderCandidate(teamId, null) }
        coVerify(exactly = 0) { teamsRepository.removeMember(any(), any()) }
        assertEquals(listOf(MemberActionResult.LeftTeam), results)
    }
}

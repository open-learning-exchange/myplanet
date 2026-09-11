package org.ole.planet.myplanet.ui.teams.members

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsMembersRepository
import org.ole.planet.myplanet.repository.UserRepository

@ExperimentalCoroutinesApi
class RequestsViewModelTest {

    private lateinit var teamsRepository: TeamsMembersRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: RequestsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        teamsRepository = mockk()
        userRepository = mockk()
        viewModel = RequestsViewModel(teamsRepository, userRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchMembers updates uiState correctly`() = runTest(testDispatcher) {
        val teamId = "team1"
        val user1 = UserEntity().apply { id = "user1" }
        val user2 = UserEntity().apply { id = "user2" }
        val members = listOf(user1, user2)

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMemberCount(teamId) } returns 1

        val currentUser = UserEntity().apply { id = "currentUser" }
        coEvery { userRepository.getUserModel() } returns currentUser
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
        val user1 = UserEntity().apply { id = "user1" }
        val user2 = UserEntity().apply { id = "user2" }
        val members = listOf(user1, user2)

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMemberCount(teamId) } returns 0
        coEvery { userRepository.getUserModel() } returns null
        coEvery { teamsRepository.isTeamLeader(teamId, null) } returns false

        viewModel.fetchMembers(teamId)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.members.size)

        coEvery { teamsRepository.respondToMemberRequest(teamId, user1.id, true) } returns Result.success(Unit)
        coEvery { teamsRepository.recordTeamActivity() } returns Unit

        val newMembers = listOf(user2)
        coEvery { teamsRepository.getRequestedMembers(teamId) } returns newMembers

        viewModel.respondToRequest(teamId, user1, true)

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
        val user1 = UserEntity().apply { id = "user1" }
        val user2 = UserEntity().apply { id = "user2" }
        val members = listOf(user1, user2)

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMemberCount(teamId) } returns 0
        coEvery { userRepository.getUserModel() } returns null
        coEvery { teamsRepository.isTeamLeader(teamId, null) } returns false

        viewModel.fetchMembers(teamId)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.members.size)

        coEvery { teamsRepository.respondToMemberRequest(teamId, user1.id, true) } returns Result.failure(Exception("err"))

        viewModel.respondToRequest(teamId, user1, true)

        val uiStateBeforeCompletion = viewModel.uiState.value
        assertEquals(1, uiStateBeforeCompletion.members.size)
        assertEquals(user2.id, uiStateBeforeCompletion.members[0].id)

        advanceUntilIdle()

        val uiStateAfterCompletion = viewModel.uiState.value
        assertEquals(2, uiStateAfterCompletion.members.size)
        assertEquals(user1.id, uiStateAfterCompletion.members[0].id)
        assertEquals(user2.id, uiStateAfterCompletion.members[1].id)
    }

    @Test
    fun `fetchMembers preserves dependency where isTeamLeader awaits user`() = runTest(testDispatcher) {
        val teamId = "team1"
        val members = listOf(UserEntity().apply { id = "user1" })

        coEvery { teamsRepository.getRequestedMembers(teamId) } returns members
        coEvery { teamsRepository.getJoinedMemberCount(teamId) } returns 1

        val currentUser = UserEntity().apply { id = "currentUser" }
        coEvery { userRepository.getUserModel() } coAnswers {
            kotlinx.coroutines.delay(100)
            currentUser
        }
        coEvery { teamsRepository.isTeamLeader(teamId, currentUser.id) } returns true

        viewModel.fetchMembers(teamId)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(members, uiState.members)
        assertTrue(uiState.isLeader)
        assertEquals(1, uiState.memberCount)
        coVerify(exactly = 1) { teamsRepository.isTeamLeader(teamId, currentUser.id) }
    }

    @Test
    fun `loadJoinedMembers publishes members and derives isLeader from loaded list`() = runTest(testDispatcher) {
        val teamId = "team1"
        val currentUserId = "currentUser"
        val currentUser = UserEntity().apply { id = currentUserId }
        val user2 = UserEntity().apply { id = "user2" }
        val user3 = UserEntity().apply { id = "user3" }

        val member1 = JoinedMemberData(currentUser, 0L, null, "", "", isLeader = true)
        val member2 = JoinedMemberData(user2, 0L, null, "", "", isLeader = false)
        val member3 = JoinedMemberData(user3, 0L, null, "", "", isLeader = false)
        val membersLeader = listOf(member1, member2, member3)

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns membersLeader

        viewModel.loadJoinedMembers(teamId)
        advanceUntilIdle()

        val stateLeader = viewModel.membersState.value
        assertEquals(membersLeader, stateLeader.members)
        assertEquals(currentUserId, stateLeader.currentUserId)
        assertTrue(stateLeader.isLeader)

        val member1NotLeader = JoinedMemberData(currentUser, 0L, null, "", "", isLeader = false)
        val membersNotLeader = listOf(member1NotLeader, member2, member3)

        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns membersNotLeader

        viewModel.loadJoinedMembers(teamId)
        advanceUntilIdle()

        val stateNotLeader = viewModel.membersState.value
        assertEquals(membersNotLeader, stateNotLeader.members)
        assertEquals(currentUserId, stateNotLeader.currentUserId)
        assertFalse(stateNotLeader.isLeader)

        coVerify(exactly = 0) { teamsRepository.isTeamLeader(any(), any()) }
    }

    @Test
    fun `removeMember promotes next leader when user removes themselves`() = runTest(testDispatcher) {
        val teamId = "team1"
        val currentUserId = "currentUser"
        val currentUser = UserEntity().apply { id = currentUserId }
        val candidate = UserEntity().apply { id = "candidate1" }

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, currentUserId) } returns candidate
        coEvery { teamsRepository.updateTeamLeader(teamId, "candidate1") } returns true
        coEvery { teamsRepository.removeMember(teamId, currentUserId) } returns Unit
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.removeMember(teamId, currentUserId)
        advanceUntilIdle()

        coVerifySequence {
            teamsRepository.getNextLeaderCandidate(teamId, currentUserId)
            teamsRepository.updateTeamLeader(teamId, "candidate1")
            teamsRepository.removeMember(teamId, currentUserId)
            teamsRepository.getJoinedMembersWithVisitInfo(teamId)
        }
        assertEquals(listOf(MemberActionResult.MemberRemoved), results)
    }

    @Test
    fun `removeMember refuses to remove last leader`() = runTest(testDispatcher) {
        val teamId = "team1"
        val currentUserId = "currentUser"
        val currentUser = UserEntity().apply { id = currentUserId }

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, currentUserId) } returns null

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.removeMember(teamId, currentUserId)
        advanceUntilIdle()

        assertEquals(listOf(MemberActionResult.CannotRemoveLastLeader), results)
        coVerify(exactly = 0) { teamsRepository.removeMember(any(), any()) }
    }

    @Test
    fun `removeMember skips leader logic for another user`() = runTest(testDispatcher) {
        val teamId = "team1"
        val currentUserId = "currentUser"
        val otherUserId = "otherUser"
        val currentUser = UserEntity().apply { id = currentUserId }

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.removeMember(teamId, otherUserId) } returns Unit
        coEvery { teamsRepository.getJoinedMembersWithVisitInfo(teamId) } returns emptyList()

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.removeMember(teamId, otherUserId)
        advanceUntilIdle()

        coVerify(exactly = 0) { teamsRepository.getNextLeaderCandidate(any(), any()) }
        coVerify(exactly = 1) { teamsRepository.removeMember(teamId, otherUserId) }
        assertEquals(listOf(MemberActionResult.MemberRemoved), results)
    }

    @Test
    fun `repository failure surfaces as Failed with message`() = runTest(testDispatcher) {
        val teamId = "team1"
        val currentUserId = "currentUser"
        val currentUser = UserEntity().apply { id = currentUserId }
        val errorMessage = "Database error"

        coEvery { userRepository.getUserModel() } returns currentUser
        coEvery { teamsRepository.getNextLeaderCandidate(teamId, currentUserId) } throws RuntimeException(errorMessage)

        val results = mutableListOf<MemberActionResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionResults.collect { results.add(it) }
        }

        viewModel.leaveTeam(teamId)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertTrue(results[0] is MemberActionResult.Failed)
        val failedResult = results[0] as MemberActionResult.Failed
        assertEquals(MemberAction.LEAVE_TEAM, failedResult.action)
        assertEquals(errorMessage, failedResult.message)
    }
}

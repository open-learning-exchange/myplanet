package org.ole.planet.myplanet.ui.sync

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val communityRepository: CommunityRepository = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(
            teamsRepository,
            userRepository,
            communityRepository,
            userSessionManager,
            TestDispatcherProvider(mainDispatcherRule.testDispatcher)
        )
    }

    @Test
    fun `init loads saved users from preferences`() = runTest {
        val saved = listOf(User("Full Name", "user1", "pwd", "", "member"))
        coEvery { userRepository.getSavedUsers() } returns saved

        val viewModel = createViewModel()

        assertEquals(saved, viewModel.savedUsers.value)
    }

    @Test
    fun `loadTeamsAsync publishes teams from repository`() = runTest {
        val team = MyTeam().apply { _id = "team1"; name = "Team One" }
        coEvery { teamsRepository.getAllActiveTeams() } returns listOf(team)

        val viewModel = createViewModel()
        viewModel.loadTeamsAsync()

        assertEquals(listOf(team), viewModel.teams.value)
    }

    @Test
    fun `loadTeamsAsync does not reload when teams already present`() = runTest {
        coEvery { teamsRepository.getAllActiveTeams() } returns listOf(MyTeam())

        val viewModel = createViewModel()
        viewModel.loadTeamsAsync()
        viewModel.loadTeamsAsync()

        coVerify(exactly = 1) { teamsRepository.getAllActiveTeams() }
    }

    @Test
    fun `loadTeamsAsync with force reloads even when teams present`() = runTest {
        coEvery { teamsRepository.getAllActiveTeams() } returns listOf(MyTeam())

        val viewModel = createViewModel()
        viewModel.loadTeamsAsync()
        viewModel.loadTeamsAsync(force = true)

        coVerify(exactly = 2) { teamsRepository.getAllActiveTeams() }
    }

    @Test
    fun `getTeamMembers publishes members and refreshes saved users`() = runTest {
        val member = UserEntity().apply { id = "user1" }
        coEvery { teamsRepository.refreshJoinedMembersForLogin("team1") } returns listOf(member)

        val viewModel = createViewModel()
        viewModel.getTeamMembers("team1")

        assertEquals(listOf(member), viewModel.users.value)
        // init + refresh after loading members
        coVerify(exactly = 2) { userRepository.getSavedUsers() }
    }

    @Test
    fun `getTeamMembers with null id clears members without repository call`() = runTest {
        val viewModel = createViewModel()
        viewModel.getTeamMembers(null)

        assertTrue(viewModel.users.value.isEmpty())
        coVerify(exactly = 0) { teamsRepository.refreshJoinedMembersForLogin(any()) }
    }

    @Test
    fun `saveUsers calls upsertSavedUser on repository`() = runTest {
        coEvery { userRepository.upsertSavedUser(any(), any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.saveUsers("guest1", "encrypted", "guest", null, null)

        coVerify(exactly = 1) {
            userRepository.upsertSavedUser("guest1", "encrypted", "guest", null, null)
        }
        // verify reload
        coVerify(exactly = 2) { userRepository.getSavedUsers() } // init + reload
    }

    @Test
    fun `resetGuestAsMember calls resetGuestAsMember on repository`() = runTest {
        coEvery { userRepository.resetGuestAsMember(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.resetGuestAsMember("guest1")

        coVerify(exactly = 1) {
            userRepository.resetGuestAsMember("guest1")
        }
        // verify reload
        coVerify(exactly = 2) { userRepository.getSavedUsers() } // init + reload
    }

    @Test
    fun `getUserByName forwards argument and returns value from repository`() = runTest {
        val expected = UserEntity().apply { id = "user1"; name = "john" }
        coEvery { userRepository.getUserByName("john") } returns expected

        val viewModel = createViewModel()
        val result = viewModel.getUserByName("john")

        assertEquals(expected, result)
        coVerify(exactly = 1) { userRepository.getUserByName("john") }
    }

    @Test
    fun `validateUsername forwards argument and returns value from repository`() = runTest {
        coEvery { userRepository.validateUsername("invalid user") } returns "Invalid username"

        val viewModel = createViewModel()
        val result = viewModel.validateUsername("invalid user")

        assertEquals("Invalid username", result)
        coVerify(exactly = 1) { userRepository.validateUsername("invalid user") }
    }

    @Test
    fun `findUserByName forwards argument and returns value from repository`() = runTest {
        val expected = UserEntity().apply { id = "user2"; name = "guest1" }
        coEvery { userRepository.findUserByName("guest1") } returns expected

        val viewModel = createViewModel()
        val result = viewModel.findUserByName("guest1")

        assertEquals(expected, result)
        coVerify(exactly = 1) { userRepository.findUserByName("guest1") }
    }

    @Test
    fun `syncCommunityDocs forwards call and returns value from repository`() = runTest {
        coEvery { communityRepository.syncCommunityDocs() } returns true

        val viewModel = createViewModel()
        val result = viewModel.syncCommunityDocs()

        assertTrue(result)
        coVerify(exactly = 1) { communityRepository.syncCommunityDocs() }
    }

    @Test
    fun `syncCommunityDocs does not call repository again after a successful sync`() = runTest {
        coEvery { communityRepository.syncCommunityDocs() } returns true

        val viewModel = createViewModel()
        val result1 = viewModel.syncCommunityDocs()
        val result2 = viewModel.syncCommunityDocs()

        assertTrue(result1)
        assertTrue(result2)
        coVerify(exactly = 1) { communityRepository.syncCommunityDocs() }
    }

    @Test
    fun `syncCommunityDocs retries after a failed sync`() = runTest {
        coEvery { communityRepository.syncCommunityDocs() } returnsMany listOf(false, true)

        val viewModel = createViewModel()
        val result1 = viewModel.syncCommunityDocs()
        val result2 = viewModel.syncCommunityDocs()

        org.junit.Assert.assertFalse(result1)
        assertTrue(result2)
        coVerify(exactly = 2) { communityRepository.syncCommunityDocs() }
    }

    @Test
    fun `loginAsGuest saves session and returns model when user created`() = runTest {
        val user = UserEntity().apply { id = "guest_1"; name = "guest_user" }
        coEvery { userRepository.createGuestUser("guest_user") } returns user

        val viewModel = createViewModel()
        val result = viewModel.loginAsGuest("guest_user")

        assertEquals(user, result)
        coVerify(exactly = 1) { userRepository.createGuestUser("guest_user") }
        coVerify(exactly = 1) { userSessionManager.saveUserInfoPref("", user) }
    }

    @Test
    fun `loginAsGuest returns null and does not save session when user creation fails`() = runTest {
        coEvery { userRepository.createGuestUser("guest_user") } returns null

        val viewModel = createViewModel()
        val result = viewModel.loginAsGuest("guest_user")

        assertNull(result)
        coVerify(exactly = 1) { userRepository.createGuestUser("guest_user") }
        coVerify(exactly = 0) { userSessionManager.saveUserInfoPref(any(), any()) }
    }

    @Test
    fun `authenticate returns NoUsers when repository has no users`() = runTest {
        coEvery { userRepository.hasAtLeastOneUser() } returns false

        val viewModel = createViewModel()
        val result = viewModel.authenticate("admin", "pwd", false)

        assertEquals(LoginViewModel.AuthOutcome.NoUsers, result)
        coVerify(exactly = 0) { userRepository.authenticateUser(any(), any(), any()) }
        coVerify(exactly = 0) { userSessionManager.saveUserInfoPref(any(), any()) }
    }

    @Test
    fun `authenticate returns Success and saves session when user authenticated`() = runTest {
        val user = UserEntity().apply { id = "user_1"; name = "john" }
        coEvery { userRepository.hasAtLeastOneUser() } returns true
        coEvery { userRepository.authenticateUser("john", "pwd", false) } returns user

        val viewModel = createViewModel()
        val result = viewModel.authenticate("john", "pwd", false)

        assertEquals(LoginViewModel.AuthOutcome.Success, result)
        coVerify(exactly = 1) { userSessionManager.saveUserInfoPref("pwd", user) }
    }

    @Test
    fun `authenticate returns Failed when authentication returns null`() = runTest {
        coEvery { userRepository.hasAtLeastOneUser() } returns true
        coEvery { userRepository.authenticateUser("john", "wrong", false) } returns null

        val viewModel = createViewModel()
        val result = viewModel.authenticate("john", "wrong", false)

        assertEquals(LoginViewModel.AuthOutcome.Failed, result)
        coVerify(exactly = 0) { userSessionManager.saveUserInfoPref(any(), any()) }
    }

    @Test
    fun `authenticate returns Failed when repository throws exception`() = runTest {
        coEvery { userRepository.hasAtLeastOneUser() } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        val result = viewModel.authenticate("john", "pwd", false)

        assertEquals(LoginViewModel.AuthOutcome.Failed, result)
    }

    @Test(expected = CancellationException::class)
    fun `authenticate rethrows CancellationException`() = runTest {
        coEvery { userRepository.hasAtLeastOneUser() } throws CancellationException("Cancelled")

        val viewModel = createViewModel()
        viewModel.authenticate("john", "pwd", false)
    }
}

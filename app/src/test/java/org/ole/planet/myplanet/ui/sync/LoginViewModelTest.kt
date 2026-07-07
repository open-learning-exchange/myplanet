package org.ole.planet.myplanet.ui.sync

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(
            teamsRepository,
            userRepository,
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
        val team = RealmMyTeam().apply { _id = "team1"; name = "Team One" }
        coEvery { teamsRepository.getAllActiveTeams() } returns listOf(team)

        val viewModel = createViewModel()
        viewModel.loadTeamsAsync()

        assertEquals(listOf(team), viewModel.teams.value)
    }

    @Test
    fun `loadTeamsAsync does not reload when teams already present`() = runTest {
        coEvery { teamsRepository.getAllActiveTeams() } returns listOf(RealmMyTeam())

        val viewModel = createViewModel()
        viewModel.loadTeamsAsync()
        viewModel.loadTeamsAsync()

        coVerify(exactly = 1) { teamsRepository.getAllActiveTeams() }
    }

    @Test
    fun `loadTeamsAsync with force reloads even when teams present`() = runTest {
        coEvery { teamsRepository.getAllActiveTeams() } returns listOf(RealmMyTeam())

        val viewModel = createViewModel()
        viewModel.loadTeamsAsync()
        viewModel.loadTeamsAsync(force = true)

        coVerify(exactly = 2) { teamsRepository.getAllActiveTeams() }
    }

    @Test
    fun `getTeamMembers publishes members and refreshes saved users`() = runTest {
        val member = RealmUser().apply { id = "user1" }
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
    fun `saveUsers calls saveSavedUser on repository`() = runTest {
        coEvery { userRepository.saveSavedUser(any(), any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.saveUsers("guest1", "encrypted", "guest", null, null)

        coVerify(exactly = 1) {
            userRepository.saveSavedUser("guest1", "encrypted", "guest", null, null)
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
}

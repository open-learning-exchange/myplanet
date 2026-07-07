package org.ole.planet.myplanet.ui.sync

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(
            teamsRepository,
            sharedPrefManager,
            TestDispatcherProvider(mainDispatcherRule.testDispatcher)
        )
    }

    @Test
    fun `init loads saved users from preferences`() = runTest {
        val saved = listOf(User("Full Name", "user1", "pwd", "", "member"))
        every { sharedPrefManager.getSavedUsers() } returns saved

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
        verify(exactly = 2) { sharedPrefManager.getSavedUsers() }
    }

    @Test
    fun `getTeamMembers with null id clears members without repository call`() = runTest {
        val viewModel = createViewModel()
        viewModel.getTeamMembers(null)

        assertTrue(viewModel.users.value.isEmpty())
        coVerify(exactly = 0) { teamsRepository.refreshJoinedMembersForLogin(any()) }
    }

    @Test
    fun `saveUsers adds a new guest`() = runTest {
        every { sharedPrefManager.getSavedUsers() } returns emptyList()
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.saveUsers("guest1", "encrypted", "guest", null, null)

        assertEquals(1, savedSlot.captured.size)
        assertEquals("guest1", savedSlot.captured[0].name)
        assertEquals("guest", savedSlot.captured[0].source)
    }

    @Test
    fun `saveUsers replaces existing guest with the same name`() = runTest {
        val existing = User("", "guest1", "oldPwd", "", "guest")
        every { sharedPrefManager.getSavedUsers() } returns listOf(existing)
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.saveUsers("guest1", "newPwd", "guest", null, null)

        assertEquals(1, savedSlot.captured.size)
        assertEquals("newPwd", savedSlot.captured[0].password)
    }

    @Test
    fun `saveUsers replaces existing member with the same username`() = runTest {
        // For members, User.fullName holds the login username (see LoginActivity.saveUsers)
        val existing = User("user1", "Full Name", "oldPwd", "old.jpg", "member")
        every { sharedPrefManager.getSavedUsers() } returns listOf(existing)
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.saveUsers("Full Name", "newPwd", "member", "new.jpg", "user1")

        assertEquals(1, savedSlot.captured.size)
        assertEquals("newPwd", savedSlot.captured[0].password)
        assertEquals("new.jpg", savedSlot.captured[0].image)
    }

    @Test
    fun `saveUsers ignores unknown sources`() = runTest {
        every { sharedPrefManager.getSavedUsers() } returns emptyList()

        val viewModel = createViewModel()
        viewModel.saveUsers("someone", "pwd", "unknown", null, null)

        verify(exactly = 0) { sharedPrefManager.setSavedUsers(any()) }
    }

    @Test
    fun `resetGuestAsMember removes saved users matching the username`() = runTest {
        val guest = User("", "guest1", "pwd", "", "guest")
        val other = User("Full Name", "user2", "pwd", "", "member")
        every { sharedPrefManager.getSavedUsers() } returns listOf(guest, other)
        val savedSlot = slot<List<User>>()
        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.resetGuestAsMember("guest1")

        assertEquals(listOf(other), savedSlot.captured)
    }

    @Test
    fun `resetGuestAsMember does nothing when the username is not saved`() = runTest {
        every { sharedPrefManager.getSavedUsers() } returns
            listOf(User("Full Name", "user2", "pwd", "", "member"))

        val viewModel = createViewModel()
        viewModel.resetGuestAsMember("guest1")

        verify(exactly = 0) { sharedPrefManager.setSavedUsers(any()) }
    }
}

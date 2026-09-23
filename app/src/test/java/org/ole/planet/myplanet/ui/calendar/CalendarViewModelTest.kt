package org.ole.planet.myplanet.ui.calendar

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@ExperimentalCoroutinesApi
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var eventsRepository: EventsRepository
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun setup() {
        eventsRepository = mockk()
        teamsRepository = mockk()
        userRepository = mockk()
    }
    
    private fun createViewModel() = CalendarViewModel(eventsRepository, teamsRepository, userRepository)

    @Test
    fun `init loads meetups and aggregates team names across the user's teams`() = runTest {
        val userId = "user1"
        coEvery { userRepository.getUserModel() } returns UserEntity().apply { id = userId }
        val teams = listOf(
            MyTeam(_id = "team1", name = "Team One"),
            MyTeam(_id = "team2", name = "Team Two")
        )
        coEvery { teamsRepository.getMyTeamsFlow(userId) } returns flowOf(teams)
        val meetups = listOf(
            Meetup().apply { id = "m1"; teamId = "team1" },
            Meetup().apply { id = "m2"; teamId = "team2" }
        )
        coEvery { eventsRepository.getMeetupsForTeams(listOf("team1", "team2")) } returns meetups

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(meetups, viewModel.meetups.value)
        assertEquals(mapOf("team1" to "Team One", "team2" to "Team Two"), viewModel.teamNames.value)
    }

    @Test
    fun `deduplicates team emissions when team ids and names are unchanged`() = runTest {
        val userId = "user1"
        coEvery { userRepository.getUserModel() } returns UserEntity().apply { id = userId }
        val teams1 = listOf(
            MyTeam(_id = "team1", name = "Team One"),
            MyTeam(_id = "team2", name = "Team Two")
        )
        val teams2 = listOf(
            MyTeam(_id = "team1", name = "Team One"),
            MyTeam(_id = "team2", name = "Team Two")
        )
        coEvery { teamsRepository.getMyTeamsFlow(userId) } returns flow {
            emit(teams1)
            emit(teams2)
        }
        val meetups = listOf(
            Meetup().apply { id = "m1"; teamId = "team1" }
        )
        coEvery { eventsRepository.getMeetupsForTeams(listOf("team1", "team2")) } returns meetups

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(meetups, viewModel.meetups.value)
        coVerify(exactly = 1) { eventsRepository.getMeetupsForTeams(listOf("team1", "team2")) }
    }

    @Test
    fun `init leaves meetups empty for a guest or logged-out user`() = runTest {
        coEvery { userRepository.getUserModel() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.meetups.value.isEmpty())
        coVerify(exactly = 0) { teamsRepository.getMyTeamsFlow(any()) }
    }
}

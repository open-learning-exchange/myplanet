package org.ole.planet.myplanet.ui.calendar

import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CalendarViewModelGroupingTest {

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
    fun `groupByLocalDate handles time zones and preserves input order`() {
        val zone = ZoneId.of("America/New_York")

        val meetupLateFeb = Meetup().apply {
            id = "m1"
            startDate = Instant.parse("2026-03-01T02:00:00Z").toEpochMilli()
        }

        val meetupMar1First = Meetup().apply {
            id = "m2"
            startDate = Instant.parse("2026-03-01T15:00:00Z").toEpochMilli()
        }
        val meetupMar1Second = Meetup().apply {
            id = "m3"
            startDate = Instant.parse("2026-03-01T18:00:00Z").toEpochMilli()
        }

        val meetups = listOf(meetupLateFeb, meetupMar1First, meetupMar1Second)
        val grouped = CalendarViewModel.groupByLocalDate(meetups, zone)

        val feb28Date = LocalDate.of(2026, 2, 28)
        val mar1Date = LocalDate.of(2026, 3, 1)

        assertEquals(listOf(meetupLateFeb), grouped[feb28Date])
        assertEquals(listOf(meetupMar1First, meetupMar1Second), grouped[mar1Date])
    }

    @Test
    fun `groupByLocalDate returns empty map for empty list`() {
        val zone = ZoneId.of("America/New_York")
        val result = CalendarViewModel.groupByLocalDate(emptyList(), zone)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `after init meetupsByDate flattened values match meetups value`() = runTest {
        val userId = "user1"
        coEvery { userRepository.getUserModel() } returns UserEntity().apply { id = userId }
        val teams = listOf(MyTeam(_id = "team1", name = "Team One"))
        coEvery { teamsRepository.getMyTeamsFlow(userId) } returns flowOf(teams)
        val meetups = listOf(
            Meetup().apply {
                id = "m1"
                teamId = "team1"
                startDate = Instant.parse("2026-03-01T10:00:00Z").toEpochMilli()
            },
            Meetup().apply {
                id = "m2"
                teamId = "team1"
                startDate = Instant.parse("2026-03-02T10:00:00Z").toEpochMilli()
            }
        )
        coEvery { eventsRepository.getMeetupsForTeams(listOf("team1")) } returns meetups

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(meetups, viewModel.meetups.value)
        assertEquals(viewModel.meetups.value, viewModel.meetupsByDate.value.values.flatten())
    }
}

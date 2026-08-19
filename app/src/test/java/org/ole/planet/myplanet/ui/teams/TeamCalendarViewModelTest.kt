package org.ole.planet.myplanet.ui.teams

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TeamCalendarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockEventsRepository: EventsRepository = mockk(relaxed = true)
    private val mockUserRepository: UserRepository = mockk(relaxed = true)
    private val mockTeamsRepository: TeamsRepository = mockk(relaxed = true)

    private lateinit var viewModel: TeamCalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeamCalendarViewModel(mockEventsRepository, mockUserRepository, mockTeamsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchMeetups updates meetups StateFlow`() = runTest(testDispatcher) {
        val meetupsList = listOf(Meetup().apply { id = "m1"; title = "Test Meetup" })
        coEvery { mockEventsRepository.getMeetupsForTeam("team1") } returns meetupsList

        viewModel.fetchMeetups("team1")
        yield()

        assertEquals(meetupsList, viewModel.meetups.value)
    }

    @Test
    fun `createMeetup calls repository and emits result`() = runTest(testDispatcher) {
        val params = MeetupCreationParams(
            "title", "link", "desc", "loc", "10:00", "11:00", null, "planet", "user", 100L, 200L, "team1"
        )
        coEvery { mockEventsRepository.createMeetup(params) } returns true

        val results = mutableListOf<Boolean>()
        val job = backgroundScope.launch(testDispatcher) {
            viewModel.createMeetupResult.collect { results.add(it) }
        }

        viewModel.createMeetup(params)
        yield()

        coVerify { mockEventsRepository.createMeetup(params) }
        assertEquals(listOf(true), results)
        job.cancel()
    }

    @Test
    fun `updateMeetup delegates to repository`() = runTest(testDispatcher) {
        coEvery {
            mockEventsRepository.updateMeetup("m1", "t", "d", 1L, 2L, "10:00", "11:00", "loc", "link", "none")
        } returns true

        val result = viewModel.updateMeetup("m1", "t", "d", 1L, 2L, "10:00", "11:00", "loc", "link", "none")
        assertTrue(result)
    }

    @Test
    fun `getCommentsForMeetupFlow returns comments from repository`() = runTest(testDispatcher) {
        val mockNews = News().apply { id = "c1"; message = "comment" }
        every { mockEventsRepository.getCommentsForMeetupFlow("m1") } returns flowOf(listOf(mockNews))

        val result = viewModel.getCommentsForMeetupFlow("m1").first()
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `getCommentsForMeetupsFlow returns comments from repository`() = runTest(testDispatcher) {
        val mockNews = News().apply { id = "c1" }
        every { mockEventsRepository.getCommentsForMeetupsFlow(listOf("m1")) } returns flowOf(listOf(mockNews))

        val result = viewModel.getCommentsForMeetupsFlow(listOf("m1")).first()
        assertEquals(1, result.size)
    }

    @Test
    fun `addComment fetches current user and calls repository`() = runTest(testDispatcher) {
        val mockUser = UserEntity().apply { id = "u1" }
        coEvery { mockUserRepository.getUserModel() } returns mockUser

        viewModel.addComment("m1", "team1", "hello")
        yield()

        coVerify { mockEventsRepository.addComment("m1", "team1", "hello", mockUser) }
    }

    @Test
    fun `deleteComment calls repository deleteComment`() = runTest(testDispatcher) {
        viewModel.deleteComment("c1")
        yield()

        coVerify { mockEventsRepository.deleteComment("c1") }
    }

    @Test
    fun `getCurrentUser and isTeamLeader delegate to repositories`() = runTest(testDispatcher) {
        val mockUser = UserEntity().apply { id = "u1" }
        coEvery { mockUserRepository.getUserModel() } returns mockUser
        coEvery { mockTeamsRepository.isTeamLeader("team1", "u1") } returns true

        val user = viewModel.getCurrentUser()
        val isLeader = viewModel.isTeamLeader("team1", "u1")

        assertEquals(mockUser, user)
        assertTrue(isLeader)
    }
}

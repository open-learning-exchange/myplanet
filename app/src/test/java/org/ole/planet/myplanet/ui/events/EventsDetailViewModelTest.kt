package org.ole.planet.myplanet.ui.events

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class EventsDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var eventsRepository: EventsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: EventsDetailViewModel

    @Before
    fun setup() {
        eventsRepository = mockk()
        userRepository = mockk()
        viewModel = EventsDetailViewModel(eventsRepository, userRepository)
    }

    @Test
    fun `loadData with non-blank meetUpId loads user, meetup, and members concurrently`() = runTest {
        val user = UserEntity(id = "user-1", name = "Alice")
        val meetup = Meetup().apply { id = "meetup-local-1"; title = "Team Sync" }
        val members = listOf(
            UserEntity(id = "user-2", name = "Bob"),
            UserEntity(id = "user-3", name = "Carol"),
        )

        coEvery { userRepository.getUserModel() } returns user
        coEvery { eventsRepository.getMeetupByLocalId("meetup-local-1") } returns meetup
        coEvery { eventsRepository.getJoinedMembers("meetup-local-1") } returns members

        viewModel.loadData("meetup-local-1")
        advanceUntilIdle()

        assertSame(user, viewModel.user.first())
        assertSame(meetup, viewModel.meetup.first())
        assertEquals(members, viewModel.members.first())

        coVerify(exactly = 1) { userRepository.getUserModel() }
        coVerify(exactly = 1) { eventsRepository.getMeetupByLocalId("meetup-local-1") }
        coVerify(exactly = 1) { eventsRepository.getJoinedMembers("meetup-local-1") }
    }

    @Test
    fun `loadData runs the three repository calls concurrently`() = runTest(mainDispatcherRule.testDispatcher) {
        val user = UserEntity(id = "user-1", name = "Alice")
        val meetup = Meetup().apply { id = "meetup-local-1"; title = "Team Sync" }
        val members = listOf(UserEntity(id = "user-2", name = "Bob"))

        coEvery { userRepository.getUserModel() } coAnswers { delay(100); user }
        coEvery { eventsRepository.getMeetupByLocalId("meetup-local-1") } coAnswers { delay(100); meetup }
        coEvery { eventsRepository.getJoinedMembers("meetup-local-1") } coAnswers { delay(100); members }

        val start = currentTime
        viewModel.loadData("meetup-local-1")
        advanceUntilIdle()

        assertEquals(user, viewModel.user.first())
        assertEquals(meetup, viewModel.meetup.first())
        assertEquals(members, viewModel.members.first())

        assertEquals(100L, currentTime - start)
        coVerify(exactly = 1) { userRepository.getUserModel() }
        coVerify(exactly = 1) { eventsRepository.getMeetupByLocalId("meetup-local-1") }
        coVerify(exactly = 1) { eventsRepository.getJoinedMembers("meetup-local-1") }
    }

    @Test
    fun `loadData with blank meetUpId short-circuits to user-only`() = runTest {
        val user = UserEntity(id = "user-1", name = "Alice")
        coEvery { userRepository.getUserModel() } returns user

        viewModel.loadData("")
        advanceUntilIdle()

        assertEquals(user, viewModel.user.first())
        assertNull(viewModel.meetup.first())
        assertEquals(emptyList<UserEntity>(), viewModel.members.first())

        coVerify(exactly = 1) { userRepository.getUserModel() }
        coVerify(exactly = 0) { eventsRepository.getMeetupByLocalId(any()) }
        coVerify(exactly = 0) { eventsRepository.getJoinedMembers(any()) }
    }

    @Test
    fun `loadData with null meetUpId short-circuits to user-only`() = runTest {
        val user = UserEntity(id = "user-1", name = "Alice")
        coEvery { userRepository.getUserModel() } returns user

        viewModel.loadData(null)
        advanceUntilIdle()

        assertEquals(user, viewModel.user.first())
        assertNull(viewModel.meetup.first())
        assertEquals(emptyList<UserEntity>(), viewModel.members.first())

        coVerify(exactly = 1) { userRepository.getUserModel() }
        coVerify(exactly = 0) { eventsRepository.getMeetupByLocalId(any()) }
        coVerify(exactly = 0) { eventsRepository.getJoinedMembers(any()) }
    }
}

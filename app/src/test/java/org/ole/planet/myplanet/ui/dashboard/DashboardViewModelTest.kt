package org.ole.planet.myplanet.ui.dashboard

import android.app.Application
import io.mockk.coEvery
import io.mockk.mockk
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var viewModel: DashboardViewModel
    private val application = mockk<Application>()
    private val userRepository = mockk<UserRepository>()
    private val resourcesRepository = mockk<ResourcesRepository>()
    private val coursesRepository = mockk<CoursesRepository>()
    private val teamsRepository = mockk<TeamsRepository>()
    private val submissionsRepository = mockk<SubmissionsRepository>()
    private val notificationsRepository = mockk<NotificationsRepository>()
    private val surveysRepository = mockk<SurveysRepository>()
    private val activitiesRepository = mockk<ActivitiesRepository>()
    private val progressRepository = mockk<ProgressRepository>()
    private val voicesRepository = mockk<VoicesRepository>()
    private val dispatcherProvider = mockk<DispatcherProvider>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        viewModel = DashboardViewModel(
            application,
            userRepository,
            resourcesRepository,
            coursesRepository,
            teamsRepository,
            submissionsRepository,
            notificationsRepository,
            surveysRepository,
            activitiesRepository,
            progressRepository,
            voicesRepository,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadUserContent replaces userContentJob when called multiple times`() = runTest(testDispatcher) {
        val userId = "user1"
        val user = RealmUser().apply { name = "John Doe"; firstName = "John"; lastName = "Doe" }

        coEvery { resourcesRepository.getMyLibrary(userId) } returns listOf(RealmMyLibrary().apply { title = "Lib1" })
        coEvery { coursesRepository.getMyCoursesFlow(userId) } returns flowOf(listOf(RealmMyCourse().apply { courseTitle = "Course1" }))
        coEvery { teamsRepository.getMyTeamsFlow(userId) } returns flowOf(listOf(RealmMyTeam().apply { name = "Team1" }))
        coEvery { userRepository.getUserById(userId) } returns user
        coEvery { activitiesRepository.getOfflineLogins("John Doe") } returns flowOf(listOf(RealmOfflineActivity(), RealmOfflineActivity()))

        viewModel.loadUserContent(userId)

        // Call it again to trigger job cancellation/replacement
        coEvery { resourcesRepository.getMyLibrary(userId) } returns listOf(RealmMyLibrary().apply { title = "Lib2" })
        viewModel.loadUserContent(userId)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("John Doe", state.fullName)
        assertEquals(2, state.offlineLogins)
        assertEquals(1, state.courses.size)
        assertEquals("Course1", (state.courses[0] as RealmMyCourse).courseTitle)
        assertEquals(1, state.teams.size)
        assertEquals("Team1", (state.teams[0] as RealmMyTeam).name)
        assertEquals(1, state.library.size)
        assertEquals("Lib2", (state.library[0] as RealmMyLibrary).title)

        // Ensure that repository methods associated with the initial call were canceled/replaced
        // Since both calls use the same user ID, they get requested twice.
        // We verify that the second library title ("Lib2") is the only one present.
        io.mockk.coVerify(exactly = 1) { resourcesRepository.getMyLibrary(userId) }
    }

    @Test
    fun `loadUserContent updates uiState for users, teams, courses, and offline logins`() = runTest(testDispatcher) {
        val userId = "user1"
        val user = RealmUser().apply { name = "John Doe"; firstName = "John"; lastName = "Doe" }

        coEvery { resourcesRepository.getMyLibrary(userId) } returns listOf(RealmMyLibrary().apply { title = "Lib1" })
        coEvery { coursesRepository.getMyCoursesFlow(userId) } returns flowOf(listOf(RealmMyCourse().apply { courseTitle = "Course1" }))
        coEvery { teamsRepository.getMyTeamsFlow(userId) } returns flowOf(listOf(RealmMyTeam().apply { name = "Team1" }))
        coEvery { userRepository.getUserById(userId) } returns user
        coEvery { activitiesRepository.getOfflineLogins("John Doe") } returns flowOf(listOf(RealmOfflineActivity(), RealmOfflineActivity()))

        viewModel.loadUserContent(userId)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("John Doe", state.fullName)
        assertEquals(2, state.offlineLogins)
        assertEquals(1, state.courses.size)
        assertEquals("Course1", (state.courses[0] as RealmMyCourse).courseTitle)
        assertEquals(1, state.teams.size)
        assertEquals("Team1", (state.teams[0] as RealmMyTeam).name)
        assertEquals(1, state.library.size)
        assertEquals("Lib1", (state.library[0] as RealmMyLibrary).title)
    }

    @Test
    fun `loadUsers updates uiState with sorted users`() = runTest(testDispatcher) {
        val users = listOf(
            RealmUser().apply { name = "User 1" },
            RealmUser().apply { name = "User 2" }
        )

        coEvery { userRepository.getUsersSortedBy("joinDate", Sort.DESCENDING) } returns users

        viewModel.loadUsers()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.users.size)
        assertEquals("User 1", (state.users[0] as RealmUser).name)
        assertEquals("User 2", (state.users[1] as RealmUser).name)
    }

    @Test
    fun `calculateIndividualProgress handles boundary cases properly`() {
        assertEquals(1, viewModel.calculateIndividualProgress(0, false))
        assertEquals(0, viewModel.calculateIndividualProgress(0, true))

        assertEquals(3, viewModel.calculateIndividualProgress(1, false))
        assertEquals(2, viewModel.calculateIndividualProgress(1, true))

        assertEquals(11, viewModel.calculateIndividualProgress(5, false))
        assertEquals(10, viewModel.calculateIndividualProgress(5, true))

        // Coerced value cases
        assertEquals(11, viewModel.calculateIndividualProgress(10, false))
        assertEquals(10, viewModel.calculateIndividualProgress(10, true))
    }

    @Test
    fun `calculateCommunityProgress handles boundary cases properly`() {
        assertEquals(1, viewModel.calculateCommunityProgress(0, false))
        assertEquals(0, viewModel.calculateCommunityProgress(0, true))

        assertEquals(3, viewModel.calculateCommunityProgress(1, false))
        assertEquals(2, viewModel.calculateCommunityProgress(1, true))

        assertEquals(11, viewModel.calculateCommunityProgress(5, false))
        assertEquals(10, viewModel.calculateCommunityProgress(5, true))

        // Ensure cap at 11
        assertEquals(11, viewModel.calculateCommunityProgress(10, false))
        assertEquals(10, viewModel.calculateCommunityProgress(10, true))
    }

    @Test
    fun `loadUserContent with null userId exits early`() = runTest(testDispatcher) {
        viewModel.loadUserContent(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.fullName)
        assertEquals(0, state.offlineLogins)
        assertEquals(0, state.courses.size)
        assertEquals(0, state.teams.size)
        assertEquals(0, state.library.size)
    }
}

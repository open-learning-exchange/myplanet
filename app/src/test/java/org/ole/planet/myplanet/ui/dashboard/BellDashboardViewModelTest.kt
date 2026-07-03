package org.ole.planet.myplanet.ui.dashboard

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class BellDashboardViewModelTest {

    private val progressRepository = mockk<ProgressRepository>()
    private val teamsRepository = mockk<TeamsRepository>()
    private val activitiesRepository = mockk<ActivitiesRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val testDispatcher = StandardTestDispatcher()

    private val day = 86_400_000L
    private val now = 1_750_000_000_000L - (1_750_000_000_000L % day) + day / 2

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(NetworkUtils)
        every { NetworkUtils.isNetworkConnectedFlow } returns MutableStateFlow(false)
        every { timeProvider.now() } returns now
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
        Dispatchers.resetMain()
    }

    private fun progressRecord(courseId: String): RealmCourseProgress {
        return RealmCourseProgress().apply { this.courseId = courseId }
    }

    private fun login(time: Long): RealmOfflineActivity {
        return RealmOfflineActivity().apply { loginTime = time; type = "login" }
    }

    @Test
    fun `loadLearningSummary combines streak, in-progress and completed counts`() = runTest {
        coEvery { progressRepository.getCompletedCourses("u1") } returns listOf(
            CourseCompletion("c1", "Completed Course")
        )
        coEvery { progressRepository.getProgressRecords("u1") } returns listOf(
            progressRecord("c1"), progressRecord("c2"), progressRecord("c2"), progressRecord("c3")
        )
        coEvery { activitiesRepository.getOfflineActivities("learner", "login") } returns listOf(
            login(now), login(now - day), login(now - 5 * day)
        )

        val viewModel = BellDashboardViewModel(progressRepository, teamsRepository, activitiesRepository, timeProvider)
        viewModel.loadLearningSummary("u1", "learner")
        advanceUntilIdle()

        val summary = viewModel.learningSummary.value
        assertEquals(LearningSummary(streakDays = 2, inProgressCourses = 2, completedCourses = 1), summary)
        assertEquals(1, viewModel.completedCourses.value.size)
    }

    @Test
    fun `loadLearningSummary with blank user name skips streak lookup`() = runTest {
        coEvery { progressRepository.getCompletedCourses("u1") } returns emptyList()
        coEvery { progressRepository.getProgressRecords("u1") } returns emptyList()

        val viewModel = BellDashboardViewModel(progressRepository, teamsRepository, activitiesRepository, timeProvider)
        viewModel.loadLearningSummary("u1", null)
        advanceUntilIdle()

        assertEquals(LearningSummary(streakDays = 0, inProgressCourses = 0, completedCourses = 0), viewModel.learningSummary.value)
    }
}

package org.ole.planet.myplanet.ui.teams.courses

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.TeamsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TeamCoursesViewModelTest {

    private lateinit var viewModel: TeamCoursesViewModel
    private val teamsRepository = mockk<TeamsRepository>()
    private val coursesRepository = mockk<CoursesRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeamCoursesViewModel(teamsRepository, coursesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState is null until loadCourses emits`() = runTest(testDispatcher) {
        assertNull(viewModel.uiState.value)
    }

    @Test
    fun `loadCourses populates state with courses and canRemove when user is creator`() = runTest(testDispatcher) {
        val courseIds = listOf("c1", "c2")
        val courses = listOf(
            MyCourse(id = "c1").apply { courseId = "c1"; courseTitle = "Course 1" },
            MyCourse(id = "c2").apply { courseId = "c2"; courseTitle = "Course 2" }
        )
        coEvery { teamsRepository.getTeamCourseIds("team1") } returns courseIds
        coEvery { coursesRepository.getCoursesByIds(courseIds) } returns courses
        coEvery { teamsRepository.getTeamCreator("team1") } returns "user1"

        viewModel.loadCourses("team1", "user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state?.courses?.size)
        assertTrue(state?.canRemove == true)
    }

    @Test
    fun `loadCourses sets canRemove false when user is not creator`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamCourseIds("team1") } returns emptyList()
        coEvery { coursesRepository.getCoursesByIds(emptyList()) } returns emptyList()
        coEvery { teamsRepository.getTeamCreator("team1") } returns "creator"

        viewModel.loadCourses("team1", "other-user")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value?.canRemove == false)
    }

    @Test
    fun `loadCourses handles case-insensitive creator match`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamCourseIds("team1") } returns emptyList()
        coEvery { coursesRepository.getCoursesByIds(emptyList()) } returns emptyList()
        coEvery { teamsRepository.getTeamCreator("team1") } returns "Creator"

        viewModel.loadCourses("team1", "creator")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value?.canRemove == true)
    }

    @Test
    fun `loadCourses with empty course ids yields empty courses`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamCourseIds("team1") } returns emptyList()
        coEvery { coursesRepository.getCoursesByIds(emptyList()) } returns emptyList()
        coEvery { teamsRepository.getTeamCreator("team1") } returns null

        viewModel.loadCourses("team1", "--")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value?.courses?.isEmpty() == true)
    }

    @Test
    fun `removeCourse delegates to teamsRepository`() = runTest(testDispatcher) {
        coEvery { teamsRepository.removeCourseFromTeam("team1", "c1") } returns Result.success(Unit)

        val result = viewModel.removeCourse("team1", "c1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { teamsRepository.removeCourseFromTeam("team1", "c1") }
    }

    @Test
    fun `addCourses delegates to teamsRepository`() = runTest(testDispatcher) {
        coEvery { teamsRepository.addCoursesToTeam("team1", listOf("c1", "c2")) } returns Result.success(Unit)

        val result = viewModel.addCourses("team1", listOf("c1", "c2"))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { teamsRepository.addCoursesToTeam("team1", listOf("c1", "c2")) }
    }

    @Test
    fun `getAvailableCourses filters out existing course ids`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamCourseIds("team1") } returns listOf("c1")
        val all = listOf(
            MyCourse(id = "c1").apply { courseId = "c1" },
            MyCourse(id = "c2").apply { courseId = "c2" },
            MyCourse(id = "c3").apply { courseId = "c3" }
        )
        coEvery { coursesRepository.getAllCourses() } returns all

        val available = viewModel.getAvailableCourses("team1")

        assertEquals(2, available.size)
        assertEquals(listOf("c2", "c3"), available.map { it.courseId })
    }

    @Test
    fun `getAvailableCourses returns all when none are linked`() = runTest(testDispatcher) {
        coEvery { teamsRepository.getTeamCourseIds("team1") } returns emptyList()
        val all = listOf(MyCourse(id = "c1").apply { courseId = "c1" })
        coEvery { coursesRepository.getAllCourses() } returns all

        val available = viewModel.getAvailableCourses("team1")

        assertEquals(1, available.size)
    }
}

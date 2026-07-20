package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TakeCourseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val coursesRepository: CoursesRepository = mockk()
    private val userSessionManager: UserSessionManager = mockk()

    private lateinit var viewModel: TakeCourseViewModel

    private val courseId = "course_1"

    private fun stubCourseLoad(
        course: RealmMyCourse? = RealmMyCourse().apply { courseId = this@TakeCourseViewModelTest.courseId },
        steps: List<RealmCourseStep> = emptyList(),
        currentProgress: Int = 0,
        user: RealmUser? = RealmUser().apply { id = "user_1" }
    ) {
        coEvery { userSessionManager.getUserModel() } returns user
        coEvery { coursesRepository.getCourseById(courseId) } returns course
        coEvery { coursesRepository.getCourseSteps(courseId) } returns steps
        coEvery { coursesRepository.getCourseProgress(user?.id, listOf(courseId)) } returns
            hashMapOf<String?, JsonObject>(courseId to JsonObject().apply { addProperty("current", currentProgress) })
    }

    @Before
    fun setUp() {
        viewModel = TakeCourseViewModel(coursesRepository, userSessionManager)
    }

    @Test
    fun loadCourse_whenCourseExists_emitsSuccessWithAggregatedData() = runTest {
        stubCourseLoad(currentProgress = 3)

        viewModel.loadCourse(courseId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TakeCourseUiState.Success)
        state as TakeCourseUiState.Success
        assertEquals(courseId, state.course.courseId)
        assertEquals(3, state.courseProgress)
    }

    @Test
    fun loadCourse_whenCourseMissing_emitsNotFound() = runTest {
        stubCourseLoad(course = null)

        viewModel.loadCourse(courseId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TakeCourseUiState.NotFound)
    }

    @Test
    fun loadCourse_calledAgainWithSameCourseId_doesNotReQueryRepository() = runTest {
        stubCourseLoad()

        viewModel.loadCourse(courseId)
        advanceUntilIdle()

        // Simulates a configuration change (rotation) re-invoking loadCourse for the
        // same course: this must be served from cache, not hit the repository again.
        viewModel.loadCourse(courseId)
        advanceUntilIdle()

        coVerify(exactly = 1) { coursesRepository.getCourseById(courseId) }
        coVerify(exactly = 1) { coursesRepository.getCourseSteps(courseId) }
        coVerify(exactly = 1) { coursesRepository.getCourseProgress(any(), any<List<String>>()) }
    }

    @Test
    fun loadCourse_withForceRefresh_reQueriesRepositoryEvenForSameCourseId() = runTest {
        stubCourseLoad()

        viewModel.loadCourse(courseId)
        advanceUntilIdle()

        viewModel.loadCourse(courseId, forceRefresh = true)
        advanceUntilIdle()

        coVerify(exactly = 2) { coursesRepository.getCourseById(courseId) }
    }

    @Test
    fun loadCourse_withDifferentCourseId_reQueriesRepository() = runTest {
        val otherCourseId = "course_2"
        stubCourseLoad()
        coEvery { coursesRepository.getCourseById(otherCourseId) } returns
            RealmMyCourse().apply { courseId = otherCourseId }
        coEvery { coursesRepository.getCourseSteps(otherCourseId) } returns emptyList()
        coEvery { coursesRepository.getCourseProgress(any(), listOf(otherCourseId)) } returns
            hashMapOf<String?, JsonObject>()

        viewModel.loadCourse(courseId)
        advanceUntilIdle()
        viewModel.loadCourse(otherCourseId)
        advanceUntilIdle()

        coVerify(exactly = 1) { coursesRepository.getCourseById(courseId) }
        coVerify(exactly = 1) { coursesRepository.getCourseById(otherCourseId) }
    }

    @Test
    fun joinDialog_isOnlyOfferedOnce() {
        assertFalse(viewModel.hasOfferedJoinDialog)
        viewModel.markJoinDialogOffered()
        assertTrue(viewModel.hasOfferedJoinDialog)
    }
}

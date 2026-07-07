package org.ole.planet.myplanet.ui.courses

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.domain.usecase.GetCourseDetailUseCase
import org.ole.planet.myplanet.domain.usecase.GetRatingSummaryUseCase
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val coursesRepository: CoursesRepository = mockk()
    private val submissionsRepository: SubmissionsRepository = mockk()
    private val ratingsRepository: RatingsRepository = mockk()
    private val userSessionManager: UserSessionManager = mockk()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var viewModel: CourseDetailViewModel
    private lateinit var getCourseDetailUseCase: GetCourseDetailUseCase
    private lateinit var getRatingSummaryUseCase: GetRatingSummaryUseCase

    private val courseId = "course_1"

    private var originalContext: Context? = null

    @Before
    fun setUp() {
        // loadCourseDetail builds the markdown base URL from MainApplication.context, which is
        // otherwise uninitialized in a plain JVM unit test and would surface as an Error state.
        try {
            originalContext = MainApplication.context
        } catch (e: Exception) {
            // UninitializedPropertyAccessException if context was never set
        }
        MainApplication.testContext = mockk<Context>(relaxed = true)

        getCourseDetailUseCase = GetCourseDetailUseCase(
            coursesRepository,
            submissionsRepository,
            ratingsRepository,
            userSessionManager,
            dispatcherProvider
        )

        getRatingSummaryUseCase = GetRatingSummaryUseCase(
            ratingsRepository,
            userSessionManager,
            dispatcherProvider
        )

        viewModel = CourseDetailViewModel(
            getCourseDetailUseCase,
            getRatingSummaryUseCase
        )
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
    }

    private fun stubCourseLoad(
        course: RealmMyCourse?,
        examCount: Int = 0,
        steps: List<RealmCourseStep> = emptyList(),
        user: RealmUser? = RealmUser().apply { id = "user_1" },
        ratingSummary: RatingSummary = RatingSummary(
            existingRating = null,
            averageRating = 4.0f,
            totalRatings = 3,
            userRating = 5
        )
    ) {
        every { coursesRepository.getCourseByCourseIdFlow(courseId) } returns flowOf(course)
        coEvery { userSessionManager.getUserModel() } returns user
        coEvery { coursesRepository.getCourseExamCount(courseId) } returns examCount
        coEvery { coursesRepository.getCourseOnlineResources(courseId) } returns emptyList()
        coEvery { coursesRepository.getCourseOfflineResources(courseId) } returns emptyList()
        coEvery { coursesRepository.getCourseSteps(courseId) } returns steps
        coEvery { submissionsRepository.getExamQuestionCount(any()) } returns 2
        coEvery { ratingsRepository.getRatingSummary("course", courseId, any()) } returns ratingSummary
    }

    @Test
    fun loadCourseDetail_whenCourseExists_emitsSuccessWithAggregatedData() = runTest {
        val course = RealmMyCourse().apply { courseId = this@CourseDetailViewModelTest.courseId }
        stubCourseLoad(course = course, examCount = 7)

        viewModel.loadCourseDetail(courseId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CourseDetailUiState.Success)
        state as CourseDetailUiState.Success
        assertEquals(7, state.examCount)
        assertEquals(4.0f, state.ratingSummary?.averageRating)
        coVerify { coursesRepository.getCourseExamCount(courseId) }
    }

    @Test
    fun loadCourseDetail_whenCourseNull_emitsError() = runTest {
        every { coursesRepository.getCourseByCourseIdFlow(courseId) } returns flowOf(null)

        viewModel.loadCourseDetail(courseId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CourseDetailUiState.Error)
        assertEquals("Course not found", (state as CourseDetailUiState.Error).message)
    }

    @Test
    fun loadCourseDetail_populatesStepItemsWithQuestionCounts() = runTest {
        val course = RealmMyCourse().apply { courseId = this@CourseDetailViewModelTest.courseId }
        val step = RealmCourseStep().apply { id = "step_1"; stepTitle = "Intro" }
        stubCourseLoad(course = course, steps = listOf(step))

        viewModel.loadCourseDetail(courseId)
        advanceUntilIdle()

        val steps = viewModel.stepItems.value
        assertEquals(1, steps.size)
        assertEquals("step_1", steps[0].id)
        assertEquals("Intro", steps[0].stepTitle)
        assertEquals(2, steps[0].questionCount)
    }

    @Test
    fun toggleStepDescription_expandsMatchingStepAndCollapsesOthers() = runTest {
        val course = RealmMyCourse().apply { courseId = this@CourseDetailViewModelTest.courseId }
        val stepA = RealmCourseStep().apply { id = "a"; stepTitle = "A" }
        val stepB = RealmCourseStep().apply { id = "b"; stepTitle = "B" }
        stubCourseLoad(course = course, steps = listOf(stepA, stepB))

        viewModel.loadCourseDetail(courseId)
        advanceUntilIdle()

        viewModel.toggleStepDescription("a")
        val afterFirst = viewModel.stepItems.value
        assertTrue(afterFirst.first { it.id == "a" }.isDescriptionVisible)
        assertFalse(afterFirst.first { it.id == "b" }.isDescriptionVisible)

        // Expanding B collapses A
        viewModel.toggleStepDescription("b")
        val afterSecond = viewModel.stepItems.value
        assertFalse(afterSecond.first { it.id == "a" }.isDescriptionVisible)
        assertTrue(afterSecond.first { it.id == "b" }.isDescriptionVisible)
    }

    @Test
    fun refreshRatings_updatesRatingSummaryOnSuccessState() = runTest {
        val course = RealmMyCourse().apply { courseId = this@CourseDetailViewModelTest.courseId }
        stubCourseLoad(course = course)

        viewModel.loadCourseDetail(courseId)
        advanceUntilIdle()

        coEvery { ratingsRepository.getRatingSummary("course", courseId, any()) } returns RatingSummary(
            existingRating = null,
            averageRating = 5.0f,
            totalRatings = 10,
            userRating = 5
        )

        viewModel.refreshRatings(courseId)
        advanceUntilIdle()

        val state = viewModel.uiState.value as CourseDetailUiState.Success
        assertEquals(5.0f, state.ratingSummary?.averageRating)
        assertEquals(10, state.ratingSummary?.totalRatings)
    }
}

package org.ole.planet.myplanet.ui.courses

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class CourseStepViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val context: Context = mockk(relaxed = true)
    private val coursesRepository: CoursesRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val configurationsRepository: ConfigurationsRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val resourceDownloadCoordinator: ResourceDownloadCoordinator = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    private lateinit var viewModel: CourseStepViewModel

    @Before
    fun setUp() {
        every { context.getExternalFilesDir(null) } returns null
        every { sharedPrefManager.getCouchdbUrl() } returns "http://localhost:5984"
        UrlUtils.init(sharedPrefManager)

        viewModel = CourseStepViewModel(
            context,
            coursesRepository,
            userRepository,
            resourcesRepository,
            configurationsRepository,
            progressRepository,
            resourceDownloadCoordinator
        )
    }

    @After
    fun tearDown() {
        UrlUtils.resetForTesting()
    }

    @Test
    fun loadStep_whenStepDataExists_populatesUiStateCorrectly() = runTest {
        val stepId = "step_1"
        val courseId = "course_1"
        val step = CourseStep().apply {
            id = stepId
            this.courseId = courseId
            stepTitle = "Step 1 Title"
            description = "Step Description"
        }
        val user = UserEntity().apply {
            id = "user_1"
            planetCode = "planet_1"
            parentCode = "parent_1"
        }
        val exam = StepExam().apply { id = "exam_1" }
        val survey = StepExam().apply { id = "survey_1" }
        val libraryItem = MyLibrary().apply { id = "lib_1" }

        val stepData = CourseStepData(
            step = step,
            resources = listOf(libraryItem),
            stepExams = listOf(exam),
            stepSurvey = listOf(survey),
            userHasCourse = true,
            hasExam = true,
            hasSurvey = true
        )

        coEvery { userRepository.getUserModel() } returns user
        coEvery { coursesRepository.getCourseStepData(stepId, user.id) } returns stepData
        coEvery { coursesRepository.getCourseTitleById(courseId) } returns "Course Title"
        coEvery { configurationsRepository.checkServerAvailability() } returns false

        viewModel.loadStep(stepId, null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(step, state.step)
        assertEquals(1, state.resources.size)
        assertEquals(1, state.stepExams.size)
        assertEquals(1, state.stepSurvey.size)
        assertEquals("Course Title", state.courseTitle)
        assertTrue(state.userHasCourse)
        assertTrue(state.hasExam)
        assertTrue(state.hasSurvey)
        assertEquals(user, state.user)
        assertFalse(state.isLoading)
        assertFalse(state.isDownloadingResources)
    }

    @Test
    fun loadStep_whenResourcesNotDownloadedAndServerAvailable_triggersAutoDownload() = runTest {
        val stepId = "step_1"
        val libraryItem = MyLibrary().apply {
            id = "lib_1"
            resourceId = "res_1"
            resourceLocalAddress = "file.pdf"
        }
        val stepData = CourseStepData(
            step = CourseStep().apply { id = stepId },
            resources = listOf(libraryItem),
            stepExams = emptyList(),
            stepSurvey = emptyList(),
            userHasCourse = true,
            hasExam = false,
            hasSurvey = false
        )

        coEvery { userRepository.getUserModel() } returns null
        coEvery { coursesRepository.getCourseStepData(stepId, null) } returns stepData
        coEvery { configurationsRepository.checkServerAvailability() } returns true

        viewModel.loadStep(stepId, null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isDownloadingResources)
        coVerify { resourcesRepository.downloadResourcesPriority(listOf(libraryItem)) }
    }

    @Test
    fun loadStep_whenNextStepIdProvided_prefetchesNextStepResources() = runTest {
        val stepId = "step_1"
        val nextStepId = "step_2"
        val nextLibraryItem = MyLibrary().apply {
            id = "lib_2"
            resourceId = "res_2"
            resourceLocalAddress = "file2.pdf"
        }

        val stepData = CourseStepData(
            step = CourseStep().apply { id = stepId },
            resources = emptyList(),
            stepExams = emptyList(),
            stepSurvey = emptyList(),
            userHasCourse = false,
            hasExam = false,
            hasSurvey = false
        )

        coEvery { userRepository.getUserModel() } returns null
        coEvery { coursesRepository.getCourseStepData(stepId, null) } returns stepData
        coEvery { resourcesRepository.getAllStepResources(nextStepId) } returns listOf(nextLibraryItem)

        viewModel.loadStep(stepId, nextStepId)
        advanceUntilIdle()

        coVerify { resourceDownloadCoordinator.startBackgroundDownload(arrayListOf("http://localhost:5984/db/resources/res_2/file2.pdf")) }
    }

    @Test
    fun saveCourseProgress_whenUserHasCourse_callsProgressRepository() = runTest {
        val stepId = "step_1"
        val courseId = "course_1"
        val step = CourseStep().apply {
            id = stepId
            this.courseId = courseId
        }
        val user = UserEntity().apply {
            id = "user_1"
            planetCode = "planet_1"
            parentCode = "parent_1"
        }
        val stepData = CourseStepData(
            step = step,
            resources = emptyList(),
            stepExams = emptyList(),
            stepSurvey = emptyList(),
            userHasCourse = true,
            hasExam = false,
            hasSurvey = false
        )

        coEvery { userRepository.getUserModel() } returns user
        coEvery { coursesRepository.getCourseStepData(stepId, user.id) } returns stepData

        viewModel.loadStep(stepId, null)
        advanceUntilIdle()

        viewModel.saveCourseProgress(2)
        advanceUntilIdle()

        coVerify {
            progressRepository.saveCourseProgress(
                "user_1",
                "planet_1",
                "parent_1",
                courseId,
                2,
                true
            )
        }
    }

    @Test
    fun saveCourseProgress_whenUserDoesNotHaveCourse_skipsSavingProgress() = runTest {
        val stepId = "step_1"
        val step = CourseStep().apply { id = stepId; courseId = "course_1" }
        val stepData = CourseStepData(
            step = step,
            resources = emptyList(),
            stepExams = emptyList(),
            stepSurvey = emptyList(),
            userHasCourse = false,
            hasExam = false,
            hasSurvey = false
        )

        coEvery { userRepository.getUserModel() } returns null
        coEvery { coursesRepository.getCourseStepData(stepId, null) } returns stepData

        viewModel.loadStep(stepId, null)
        advanceUntilIdle()

        viewModel.saveCourseProgress(1)
        advanceUntilIdle()

        coVerify(exactly = 0) { progressRepository.saveCourseProgress(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun refreshInlineResources_updatesResourcesInUiState() = runTest {
        val stepId = "step_1"
        val newResource = MyLibrary().apply { id = "lib_2"; title = "Resource 2" }
        coEvery { resourcesRepository.getAllStepResources(stepId) } returns listOf(newResource)

        viewModel.refreshInlineResources(stepId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.resources.size)
        assertEquals("lib_2", state.resources[0].id)
        assertFalse(state.isDownloadingResources)
    }

    @Test
    fun loadStep_emptyStepPath_handlesNullAndEmptyDataGracefully() = runTest {
        val emptyStepData = CourseStepData(
            step = CourseStep(),
            resources = emptyList(),
            stepExams = emptyList(),
            stepSurvey = emptyList(),
            userHasCourse = false,
            hasExam = false,
            hasSurvey = false
        )

        coEvery { userRepository.getUserModel() } returns null
        coEvery { coursesRepository.getCourseStepData("", null) } returns emptyStepData

        viewModel.loadStep(null, null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.step?.id)
        assertTrue(state.resources.isEmpty())
        assertTrue(state.stepExams.isEmpty())
        assertTrue(state.stepSurvey.isEmpty())
        assertNull(state.courseTitle)
        assertFalse(state.userHasCourse)
    }
}

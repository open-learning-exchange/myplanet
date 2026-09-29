package org.ole.planet.myplanet.ui.exam

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ExamTakingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val applicationScope = TestScope(testDispatcher)

    private lateinit var submissionsRepository: SubmissionsRepository
    private lateinit var coursesRepository: CoursesRepository
    private lateinit var surveysRepository: SurveysRepository
    private lateinit var viewModel: ExamTakingViewModel

    @Before
    fun setup() {
        submissionsRepository = mockk(relaxed = true)
        coursesRepository = mockk(relaxed = true)
        surveysRepository = mockk(relaxed = true)
        viewModel = ExamTakingViewModel(
            submissionsRepository,
            coursesRepository,
            surveysRepository,
            applicationScope
        )
    }

    @Test
    fun `getSubmissionById returns submission when found`() = runTest {
        val submissionId = "sub123"
        val expectedSubmission = mockk<Submission>()
        coEvery { submissionsRepository.getSubmissionById(submissionId) } returns expectedSubmission

        val result = viewModel.getSubmissionById(submissionId)

        assertEquals(expectedSubmission, result)
        coVerify(exactly = 1) { submissionsRepository.getSubmissionById(submissionId) }
    }

    @Test
    fun `getSubmissionById returns null when not found or given null`() = runTest {
        val submissionId = "invalid_id"
        coEvery { submissionsRepository.getSubmissionById(submissionId) } returns null

        val result = viewModel.getSubmissionById(submissionId)

        assertNull(result)
        coVerify(exactly = 1) { submissionsRepository.getSubmissionById(submissionId) }
    }

    @Test
    fun `getExamByStepId returns exam when found`() = runTest {
        val stepId = "step123"
        val expectedExam = mockk<StepExam>()
        coEvery { submissionsRepository.getExamByStepId(stepId) } returns expectedExam

        val result = viewModel.getExamByStepId(stepId)

        assertEquals(expectedExam, result)
        coVerify(exactly = 1) { submissionsRepository.getExamByStepId(stepId) }
    }

    @Test
    fun `getExamByStepId returns null when not found`() = runTest {
        val stepId = "missing_step"
        coEvery { submissionsRepository.getExamByStepId(stepId) } returns null

        val result = viewModel.getExamByStepId(stepId)

        assertNull(result)
        coVerify(exactly = 1) { submissionsRepository.getExamByStepId(stepId) }
    }

    @Test
    fun `getExamById returns exam when found`() = runTest {
        val examId = "exam123"
        val expectedExam = mockk<StepExam>()
        coEvery { submissionsRepository.getExamById(examId) } returns expectedExam

        val result = viewModel.getExamById(examId)

        assertEquals(expectedExam, result)
        coVerify(exactly = 1) { submissionsRepository.getExamById(examId) }
    }

    @Test
    fun `getExamById returns null when not found`() = runTest {
        val examId = "missing_exam"
        coEvery { submissionsRepository.getExamById(examId) } returns null

        val result = viewModel.getExamById(examId)

        assertNull(result)
        coVerify(exactly = 1) { submissionsRepository.getExamById(examId) }
    }

    @Test
    fun `updateSubmissionStatus delegates to repository with status complete`() = runTest {
        val submissionId = "sub123"
        val status = "complete"

        viewModel.updateSubmissionStatus(submissionId, status)

        coVerify(exactly = 1) { submissionsRepository.updateSubmissionStatus(submissionId, status) }
    }

    @Test
    fun `updateSubmissionStatus delegates to repository with null submissionId`() = runTest {
        val status = "complete"

        viewModel.updateSubmissionStatus(null, status)

        coVerify(exactly = 1) { submissionsRepository.updateSubmissionStatus(null, status) }
    }

    @Test
    fun `addSubmissionPhoto delegates to repository on applicationScope`() = runTest {
        val submitId = "submit123"
        val examId = "exam123"
        val courseId = "course123"
        val memberId = "member123"
        val photoPath = "/path/to/photo.jpg"

        viewModel.addSubmissionPhoto(submitId, examId, courseId, memberId, photoPath)
        applicationScope.advanceUntilIdle()

        coVerify(exactly = 1) {
            submissionsRepository.addSubmissionPhoto(
                submitId,
                examId,
                courseId,
                memberId,
                photoPath
            )
        }
    }
}

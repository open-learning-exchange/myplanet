package org.ole.planet.myplanet.ui.exam

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData

@HiltViewModel
class ExamTakingViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val coursesRepository: CoursesRepository,
    private val surveysRepository: SurveysRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?) {
        withContext(dispatcherProvider.io) {
            submissionsRepository.deleteExamSubmissions(examId, courseId, userId)
        }
    }

    suspend fun getExamQuestions(examId: String): List<ExamQuestion>? = withContext(dispatcherProvider.io) {
        surveysRepository.getExamQuestions(examId)
    }

    suspend fun getSubmissionsByParentId(parentId: String, userId: String?, status: String?): List<Submission> = withContext(dispatcherProvider.io) {
        submissionsRepository.getSubmissionsByParentId(parentId, userId, status)
    }

    suspend fun isCourseCertified(courseId: String): Boolean = withContext(dispatcherProvider.io) {
        coursesRepository.isCourseCertified(courseId)
    }

    suspend fun createExamSubmission(request: CreateExamSubmissionRequest): Submission? = withContext(dispatcherProvider.io) {
        submissionsRepository.createExamSubmission(request)
    }

    suspend fun saveExamAnswer(data: ExamAnswerData): Boolean = withContext(dispatcherProvider.io) {
        submissionsRepository.saveExamAnswer(data)
    }

    suspend fun updateCourseProgress(courseId: String?, stepNum: Int, isGraded: Boolean) = withContext(dispatcherProvider.io) {
        coursesRepository.updateCourseProgress(courseId, stepNum, isGraded)
    }
}

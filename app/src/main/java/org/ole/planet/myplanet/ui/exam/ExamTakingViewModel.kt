package org.ole.planet.myplanet.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository

@HiltViewModel
class ExamTakingViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val coursesRepository: CoursesRepository,
    private val surveysRepository: SurveysRepository
) : ViewModel() {

    suspend fun getSubmissionById(id: String): Submission? {
        return submissionsRepository.getSubmissionById(id)
    }

    suspend fun getExamByStepId(stepId: String): StepExam? {
        return submissionsRepository.getExamByStepId(stepId)
    }

    suspend fun getExamById(id: String): StepExam? {
        return submissionsRepository.getExamById(id)
    }

    suspend fun updateSubmissionStatus(submissionId: String?, status: String) {
        submissionsRepository.updateSubmissionStatus(submissionId, status)
    }

    fun addSubmissionPhoto(
        submissionId: String?,
        examId: String?,
        courseId: String?,
        memberId: String?,
        photoPath: String?
    ) {
        viewModelScope.launch {
            submissionsRepository.addSubmissionPhoto(
                submissionId,
                examId,
                courseId,
                memberId,
                photoPath
            )
        }
    }
}

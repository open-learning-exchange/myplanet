package org.ole.planet.myplanet.ui.exam

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ExamTakingViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?) {
        withContext(dispatcherProvider.io) {
            submissionsRepository.deleteExamSubmissions(examId, courseId, userId)
        }
    }
}

package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class CourseProgressViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _courseProgress = MutableStateFlow<CourseProgressData?>(null)
    val courseProgress: StateFlow<CourseProgressData?> = _courseProgress

    fun loadProgress(courseId: String) {
        viewModelScope.launch {
            val user = userSessionManager.getUserModel()
            _courseProgress.value = coursesRepository.getCourseProgress(courseId, user?._id)
        }
    }
}

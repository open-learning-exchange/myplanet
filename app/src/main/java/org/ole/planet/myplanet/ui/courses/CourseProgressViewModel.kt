package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@HiltViewModel
class CourseProgressViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseProgressState>(CourseProgressState.Loading)
    val uiState: StateFlow<CourseProgressState> = _uiState

    fun getCourseProgress(courseId: String) {
        viewModelScope.launch {
            try {
                val user = userProfileDbHandler.userModel
                val courseProgress = courseRepository.getCourseProgress(courseId, user?.id)
                _uiState.value = CourseProgressState.Success(
                    courseProgress.courseTitle,
                    courseProgress.progress,
                    courseProgress.stepProgress,
                    courseProgress.currentProgress,
                    courseProgress.maxProgress
                )
            } catch (e: Exception) {
                _uiState.value = CourseProgressState.Error(e.message)
            }
        }
    }
}

sealed class CourseProgressState {
    object Loading : CourseProgressState()
    data class Success(
        val courseTitle: String?,
        val progress: Int,
        val stepProgress: JsonArray,
        val currentProgress: Int,
        val maxProgress: Int
    ) : CourseProgressState()
    data class Error(val message: String?) : CourseProgressState()
}

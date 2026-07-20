package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.services.UserSessionManager

sealed interface TakeCourseUiState {
    object Loading : TakeCourseUiState
    object NotFound : TakeCourseUiState
    data class Success(
        val course: RealmMyCourse,
        val steps: List<RealmCourseStep>,
        val userModel: RealmUser?,
        val courseProgress: Int
    ) : TakeCourseUiState
}

@HiltViewModel
class TakeCourseViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<TakeCourseUiState>(TakeCourseUiState.Loading)
    val uiState: StateFlow<TakeCourseUiState> = _uiState

    private var loadedCourseId: String? = null

    var hasOfferedJoinDialog = false
        private set

    fun markJoinDialogOffered() {
        hasOfferedJoinDialog = true
    }

    fun loadCourse(courseId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedCourseId == courseId && _uiState.value is TakeCourseUiState.Success) {
            return
        }
        loadedCourseId = courseId
        viewModelScope.launch {
            _uiState.value = TakeCourseUiState.Loading

            val userModel = userSessionManager.getUserModel()
            val course = coursesRepository.getCourseById(courseId)
            if (course == null) {
                _uiState.value = TakeCourseUiState.NotFound
                return@launch
            }

            val steps = coursesRepository.getCourseSteps(courseId)
            val progressMap = coursesRepository.getCourseProgress(userModel?.id, listOf(courseId))
            val progress = progressMap[courseId]?.asJsonObject?.get("current")?.asInt ?: 0

            _uiState.value = TakeCourseUiState.Success(course, steps, userModel, progress)
        }
    }
}

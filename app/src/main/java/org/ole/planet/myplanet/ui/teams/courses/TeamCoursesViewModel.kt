package org.ole.planet.myplanet.ui.teams.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

data class TeamCoursesUiState(
    val courses: List<MyCourse> = emptyList(),
    val canRemove: Boolean = false
)

@HiltViewModel
class TeamCoursesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val coursesRepository: CoursesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<TeamCoursesUiState?>(null)
    val uiState: StateFlow<TeamCoursesUiState?> = _uiState.asStateFlow()

    fun loadCourses(teamId: String, currentUserId: String) {
        viewModelScope.launch {
            coroutineScope {
                val teamCreatorDeferred = async { teamsRepository.getTeamCreator(teamId) }
                val coursesDeferred = async {
                    val courseIds = teamsRepository.getTeamCourseIds(teamId)
                    coursesRepository.getCoursesByIds(courseIds)
                }
                val teamCreator = teamCreatorDeferred.await()
                val canRemove = currentUserId.equals(teamCreator, ignoreCase = true)
                val courses = coursesDeferred.await()
                _uiState.value = TeamCoursesUiState(courses = courses, canRemove = canRemove)
            }
        }
    }

    suspend fun removeCourse(teamId: String, courseId: String): Result<Unit> {
        val previous = _uiState.value
        if (previous != null) {
            _uiState.value = previous.copy(courses = previous.courses.filterNot { it.courseId == courseId })
        }
        return teamsRepository.removeCourseFromTeam(teamId, courseId)
            .onFailure { if (_uiState.value?.courses?.none { it.courseId == courseId } == true) _uiState.value = previous }
    }

    suspend fun addCourses(teamId: String, courses: List<MyCourse>): Result<Unit> {
        val previous = _uiState.value
        val current = previous ?: TeamCoursesUiState()
        val shownIds = current.courses.mapNotNullTo(mutableSetOf()) { it.courseId }
        val added = courses.filter { !it.courseId.isNullOrBlank() && shownIds.add(it.courseId!!) }
        val courseIds = courses.mapNotNull { it.courseId?.takeIf(String::isNotBlank) }
        if (courseIds.isEmpty()) return Result.success(Unit)
        if (added.isNotEmpty()) _uiState.value = current.copy(courses = current.courses + added)
        return teamsRepository.addCoursesToTeam(teamId, courseIds)
            .onFailure { if (_uiState.value?.courses?.containsAll(added) == true) _uiState.value = previous }
    }

    suspend fun getAvailableCourses(teamId: String): List<MyCourse> {
        return withContext(dispatcherProvider.io) {
            val existingIds = teamsRepository.getTeamCourseIds(teamId).toSet()
            val allCourses = coursesRepository.getAllCourses()
            allCourses.filter { it.courseId !in existingIds }
        }
    }
}

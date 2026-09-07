package org.ole.planet.myplanet.ui.teams.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.TeamsRepository

data class TeamCoursesUiState(
    val courses: List<MyCourse> = emptyList(),
    val canRemove: Boolean = false
)

@HiltViewModel
class TeamCoursesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val coursesRepository: CoursesRepository
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
        return teamsRepository.removeCourseFromTeam(teamId, courseId)
    }

    suspend fun addCourses(teamId: String, courseIds: List<String>): Result<Unit> {
        return teamsRepository.addCoursesToTeam(teamId, courseIds)
    }

    suspend fun getAvailableCourses(teamId: String): List<MyCourse> {
        val existingIds = teamsRepository.getTeamCourseIds(teamId).toSet()
        val allCourses = coursesRepository.getAllCourses()
        return allCourses.filter { it.courseId !in existingIds }
    }
}

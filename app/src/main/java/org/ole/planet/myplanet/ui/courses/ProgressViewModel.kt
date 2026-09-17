package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _courseData = MutableStateFlow<List<CoursesProgressRow>>(emptyList())
    val courseData: StateFlow<List<CoursesProgressRow>> = _courseData

    fun loadCourseData() {
        viewModelScope.launch {
            val user = userRepository.getUserModel()
            _courseData.value = progressRepository.getCourseProgressRows(user?.id)
        }
    }
}

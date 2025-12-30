package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.service.UserProfileHandler

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val userProfileDbHandler: UserProfileHandler
) : ViewModel() {

    private val _courseData = MutableStateFlow<JsonArray?>(null)
    val courseData: StateFlow<JsonArray?> = _courseData

    fun loadCourseData() {
        viewModelScope.launch {
            val user = userProfileDbHandler.userModel
            _courseData.value = progressRepository.fetchCourseData(user?.id)
        }
    }
}

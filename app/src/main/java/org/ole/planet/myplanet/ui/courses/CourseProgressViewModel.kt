package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.CourseProgressRepository

import org.ole.planet.myplanet.datamanager.DatabaseService

@HiltViewModel
class CourseProgressViewModel @Inject constructor(
    val courseProgressRepository: CourseProgressRepository,
    private val databaseService: DatabaseService
) : ViewModel() {
    private val _courseProgress = MutableLiveData<JsonArray>()
    val courseProgress: LiveData<JsonArray> = _courseProgress
    private val _courseSteps = MutableLiveData<JsonArray>()
    val courseSteps: LiveData<JsonArray> = _courseSteps

    fun getCourseProgress(userId: String?) {
        viewModelScope.launch {
            val realm = databaseService.realmInstance
            _courseProgress.postValue(courseProgressRepository.fetchCourseData(realm, userId))
        }
    }

    fun getCourseSteps(userId: String?, courseId: String) {
        viewModelScope.launch {
            _courseSteps.postValue(courseProgressRepository.getCourseSteps(userId, courseId))
        }
    }
}

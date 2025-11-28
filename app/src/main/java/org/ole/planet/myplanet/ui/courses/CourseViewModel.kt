package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.service.DatabaseService
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@HiltViewModel
class CourseViewModel @Inject constructor(
    private val databaseService: DatabaseService, private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseScreenState>(CourseScreenState.Loading)
    val uiState = _uiState.asStateFlow()

    fun loadCourses() {
        viewModelScope.launch {
            _uiState.value = CourseScreenState.Loading
            try {
                withContext(Dispatchers.IO) {
                    databaseService.withRealm { realm ->
                        val user = userProfileDbHandler.userModel
                        val courses =
                            realm.where(RealmMyCourse::class.java).findAll()
                                .filter { !it.courseTitle.isNullOrBlank() }
                                .sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
                        val ratings = RealmRating.getRatings(realm, "course", user?.id)
                        val progress = RealmCourseProgress.getCourseProgress(realm, user?.id)
                        _uiState.value = CourseScreenState.Success(
                            realm.copyFromRealm(courses), ratings, progress
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = CourseScreenState.Error("Failed to load courses")
            }
        }
    }
}

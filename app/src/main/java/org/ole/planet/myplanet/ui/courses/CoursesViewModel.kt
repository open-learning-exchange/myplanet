package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.repository.CourseRepository
import javax.inject.Inject

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    private val _courses = MutableLiveData<List<RealmMyCourse?>>()
    val courses: LiveData<List<RealmMyCourse?>> = _courses

    fun loadCourses() {
        viewModelScope.launch {
            _courses.postValue(courseRepository.getAllCourses())
        }
    }
}

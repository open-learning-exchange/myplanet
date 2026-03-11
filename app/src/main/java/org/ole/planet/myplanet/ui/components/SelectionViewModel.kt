package org.ole.planet.myplanet.ui.components

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.ResourceItem

class SelectionViewModel : ViewModel() {

    private val _selectedCourses = MutableStateFlow<List<Course>>(emptyList())
    val selectedCourses: StateFlow<List<Course>> = _selectedCourses.asStateFlow()

    private val _selectedResources = MutableStateFlow<List<ResourceItem>>(emptyList())
    val selectedResources: StateFlow<List<ResourceItem>> = _selectedResources.asStateFlow()

    fun toggleCourseSelection(course: Course) {
        _selectedCourses.update { current ->
            if (current.any { it.courseId == course.courseId }) {
                current.filter { it.courseId != course.courseId }
            } else {
                current + course
            }
        }
    }

    fun selectAllCourses(courses: List<Course>, select: Boolean) {
        _selectedCourses.value = if (select) courses else emptyList()
    }

    fun clearCourseSelections() {
        _selectedCourses.value = emptyList()
    }

    fun toggleResourceSelection(resource: ResourceItem) {
        _selectedResources.update { current ->
            if (current.any { it.id == resource.id }) {
                current.filter { it.id != resource.id }
            } else {
                current + resource
            }
        }
    }

    fun selectAllResources(resources: List<ResourceItem>, select: Boolean) {
        _selectedResources.value = if (select) resources else emptyList()
    }

    fun clearResourceSelections() {
        _selectedResources.value = emptyList()
    }
}

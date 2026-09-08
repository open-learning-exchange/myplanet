package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val userRepository: UserRepository,
    private val gson: Gson,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val type = object : TypeToken<Map<String, Int>>() {}.type

    private val _courseData = MutableStateFlow<List<CoursesProgressRow>>(emptyList())
    val courseData: StateFlow<List<CoursesProgressRow>> = _courseData

    fun loadCourseData() {
        viewModelScope.launch {
            val user = userRepository.getUserModel()
            val jsonArray = progressRepository.fetchCourseData(user?.id)

            val parsedList = withContext(dispatcherProvider.default) {
                jsonArray?.map { element ->
                    val obj = element.asJsonObject

                    val stepMistake = if (obj.has("stepMistake")) {
                        gson.fromJson<Map<String, Int>>(obj.get("stepMistake"), type)
                    } else {
                        null
                    }

                    CoursesProgressRow(
                        courseId = obj.get("courseId").asString,
                        courseName = obj.get("courseName").asString,
                        progressCurrent = obj.getAsJsonObject("progress")?.get("current")?.asInt,
                        progressMax = obj.getAsJsonObject("progress")?.get("max")?.asInt,
                        mistakes = obj.get("mistakes")?.asInt,
                        stepMistake = stepMistake
                    )
                } ?: emptyList()
            }
            _courseData.value = parsedList
        }
    }
}

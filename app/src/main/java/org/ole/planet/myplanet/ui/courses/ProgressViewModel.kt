package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _courseData = MutableStateFlow<List<CourseProgressItem>>(emptyList())
    val courseData: StateFlow<List<CourseProgressItem>> = _courseData

    fun loadCourseData() {
        viewModelScope.launch {
            val user = userProfileDbHandler.userModel
            val jsonArray = progressRepository.fetchCourseData(user?.id)
            _courseData.value = parseCourseData(jsonArray)
        }
    }

    private suspend fun parseCourseData(jsonArray: JsonArray): List<CourseProgressItem> {
        return withContext(Dispatchers.Default) {
            val list = mutableListOf<CourseProgressItem>()
            jsonArray.forEach {
                val item = it.asJsonObject
                val courseId = item["courseId"]?.asString ?: ""
                val courseName = item["courseName"]?.asString ?: ""

                val progress = item["progress"]?.asJsonObject
                val progressCurrent = progress?.get("current")?.asInt ?: 0
                val progressMax = progress?.get("max")?.asInt ?: 0

                val mistakes = item["mistakes"]?.asString ?: "0"

                val stepMistakes = mutableListOf<StepMistake>()
                if (item.has("stepMistake")) {
                    val stepMistakeObj = item["stepMistake"].asJsonObject
                    stepMistakeObj.keySet().forEach { stepKey ->
                        stepMistakes.add(
                            StepMistake(
                                step = stepKey.toInt(),
                                mistakes = stepMistakeObj[stepKey].asInt
                            )
                        )
                    }
                }

                list.add(
                    CourseProgressItem(
                        courseId = courseId,
                        courseName = courseName,
                        progressCurrent = progressCurrent,
                        progressMax = progressMax,
                        mistakes = mistakes,
                        stepMistakes = stepMistakes.sortedBy { it.step }
                    )
                )
            }
            list
        }
    }
}

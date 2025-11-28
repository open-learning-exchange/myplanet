package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse

sealed interface CourseScreenState {
    data object Loading : CourseScreenState
    data class Success(
        val courses: List<RealmMyCourse?>,
        val ratings: HashMap<String?, JsonObject>,
        val progress: HashMap<String?, JsonObject>
    ) : CourseScreenState

    data class Error(val message: String) : CourseScreenState
}

package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse

data class CourseListItem(
    val course: RealmMyCourse,
    val rating: JsonObject?,
    val progress: JsonObject?
) {
    val id: String?
        get() = course.id
}

package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

data class CourseData(
    val courseList: List<RealmMyCourse?>,
    val ratings: HashMap<String, JsonObject>,
    val progressMap: HashMap<String, JsonObject>,
    val resources: List<RealmMyLibrary> = emptyList()
)

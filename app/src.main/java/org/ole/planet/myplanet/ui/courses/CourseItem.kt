package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag

data class CourseItem(
    val course: RealmMyCourse?,
    val tags: List<RealmTag>,
    val rating: JsonObject?
)

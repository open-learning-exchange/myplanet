package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import org.ole.planet.myplanet.utils.GsonUtils

// App-side Gson helpers for Certification, kept out of the Room entity.

fun Certification.setCourseIds(courseIds: JsonArray?) {
    this.courseIds = GsonUtils.gson.toJson(courseIds)
}

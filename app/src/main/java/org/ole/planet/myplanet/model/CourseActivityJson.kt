package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for CourseActivity, kept out of the Room entity.

fun CourseActivity.Companion.serialize(courseActivity: CourseActivity): JsonObject {
    val ob = buildJsonObject {
        put("user", courseActivity.user)
        put("courseId", courseActivity.courseId)
        put("type", courseActivity.type)
        put("title", courseActivity.title)
        put("time", courseActivity.time)
        put("createdOn", courseActivity.createdOn)
        put("parentCode", courseActivity.parentCode)
        put("deviceName", NetworkUtils.getDeviceName())
    }.toGson()
    ob.addDocumentOrigin()
    return ob
}

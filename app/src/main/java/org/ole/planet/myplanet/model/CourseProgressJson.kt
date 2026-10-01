package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for CourseProgress, kept out of the Room entity.

fun CourseProgress.Companion.serializeProgress(progress: CourseProgress): JsonObject {
    val `object` = buildJsonObject {
        put("userId", progress.userId)
        put("parentCode", progress.parentCode)
        put("courseId", progress.courseId)
        put("passed", progress.passed)
        put("stepNum", progress.stepNum)
        put("createdOn", progress.createdOn)
        put("createdDate", progress.createdDate)
        put("updatedDate", progress.updatedDate)
    }.toGson()
    `object`.addDocumentOrigin()
    return `object`
}

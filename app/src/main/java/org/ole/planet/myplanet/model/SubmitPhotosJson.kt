package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for SubmitPhotos, kept out of the Room entity.

fun SubmitPhotos.Companion.serialize(submit: SubmitPhotos): JsonObject {
    val obj = buildJsonObject {
        put("id", submit.id)
        put("submissionId", submit.submissionId)
        put("type", "photo")
        put("courseId", submit.courseId)
        put("examId", submit.examId)
        put("memberId", submit.memberId)
        put("date", submit.date)
        put("macAddress", submit.uniqueId)
        put("photoLocation", submit.photoLocation)
    }.toGson()
    obj.addDocumentOrigin()
    return obj
}

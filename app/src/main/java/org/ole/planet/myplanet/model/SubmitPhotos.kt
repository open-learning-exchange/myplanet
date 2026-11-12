package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

@Entity(tableName = "submit_photos", indices = [androidx.room.Index("uploaded")])
open class SubmitPhotos {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var submissionId: String? = null
    var courseId: String? = null
    var examId: String? = null
    var memberId: String? = null
    var date: String? = null
    var uniqueId: String? = null
    var photoLocation: String? = null
    var uploaded = false

    companion object {
        fun serialize(submit: SubmitPhotos): JsonObject {
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
    }
}

package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonObject

@Entity(tableName = "submit_photos")
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
            val obj = JsonObject()
            obj.addProperty("id", submit.id)
            obj.addProperty("submissionId", submit.submissionId)
            obj.addProperty("type", "photo")
            obj.addProperty("courseId", submit.courseId)
            obj.addProperty("examId", submit.examId)
            obj.addProperty("memberId", submit.memberId)
            obj.addProperty("date", submit.date)
            obj.addProperty("macAddress", submit.uniqueId)
            obj.addProperty("photoLocation", submit.photoLocation)
            return obj
        }
    }
}

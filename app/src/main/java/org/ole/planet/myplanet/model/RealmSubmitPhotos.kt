package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmSubmitPhotos : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var submissionId: String? = null
    var courseId: String? = null
    var examId: String? = null
    var memberId: String? = null
    var date: String? = null
    var uniqueId: String? = null
    var photoLocation: String? = null
    var uploaded: Boolean = false

    companion object {
        fun serializeRealmSubmitPhotos(submit: RealmSubmitPhotos): JsonObject {
            return JsonObject().apply {
                addProperty("id", submit.id)
                addProperty("submissionId", submit.submissionId)
                addProperty("type", "photo")
                addProperty("courseId", submit.courseId)
                addProperty("examId", submit.examId)
                addProperty("memberId", submit.memberId)
                addProperty("date", submit.date)
                addProperty("macAddress", submit.uniqueId)
                addProperty("photoLocation", submit.photoLocation)
            }
        }
    }
}


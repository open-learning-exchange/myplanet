package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmSubmitPhotos : RealmObject() {
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
    var uploaded = false

    companion object {
        /**
         * public static JsonArray serializeRealmSubmitPhotos(RealmList<RealmSubmitPhotos> submitPhotos)
         * {
         * JsonArray arr = new JsonArray();
         * for(RealmSubmitPhotos sub: submitPhotos)
         * {
         * arr.add(createObject(sub));
         * }
         *
         *
         * return arr;
         * }
        </RealmSubmitPhotos> */
        @JvmStatic
        fun serializeRealmSubmitPhotos(submit: RealmSubmitPhotos): JsonObject {
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

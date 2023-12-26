package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmSubmitPhotos : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var submissionId: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var examId: String? = null
    @JvmField
    var memberId: String? = null
    @JvmField
    var date: String? = null
    @JvmField
    var uniqueId: String? = null
    @JvmField
    var photoLocation: String? = null
    @JvmField
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

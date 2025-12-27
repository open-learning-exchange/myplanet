package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils

open class RealmCertification : RealmObject() {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    private var courseIds: String? = null

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds = JsonUtils.gson.toJson(courseIds)
    }

    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, `object`: JsonObject?) {
            val id = JsonUtils.getString("_id", `object`)
            var certification = mRealm.where(RealmCertification::class.java).equalTo("_id", id).findFirst()
            if (certification == null) {
                certification = mRealm.createObject(RealmCertification::class.java, id)
            }
            certification?.name = JsonUtils.getString("name", `object`)
            certification?.setCourseIds(JsonUtils.getJsonArray("courseIds", `object`))
        }

        @JvmStatic
        fun isCourseCertified(realm: Realm, courseId: String?): Boolean {
            if (courseId == null) {
                return false
            }
            if (realm.isClosed) {
                return false
            }
            val c =
                realm.where(RealmCertification::class.java).contains("courseIds", courseId).count()
            return c > 0
        }
    }
}

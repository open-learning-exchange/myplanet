package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities

open class RealmCertification : RealmObject() {
    @PrimaryKey
    private var _id: String? = null
    private var _rev: String? = null
    var name: String? = null
    var courseIds: String? = null
    fun get_id(): String? {
        return _id
    }

    fun set_id(_id: String?) {
        this._id = _id
    }

    fun get_rev(): String? {
        return _rev
    }

    fun set_rev(_rev: String?) {
        this._rev = _rev
    }

    fun setCourseIds(courseIds: JsonArray?) {
        this.courseIds = Gson().toJson(courseIds)
    }

    companion object {
        fun insert(mRealm: Realm, `object`: JsonObject?) {
            val id = JsonUtils.getString("_id", `object`)
            Utilities.log("certification insert")
            var certification = mRealm.where(
                RealmCertification::class.java
            ).equalTo("_id", id).findFirst()
            if (certification == null) {
                certification = mRealm.createObject(RealmCertification::class.java, id)
            }
            certification?.name = JsonUtils.getString("name", `object`)
            certification?.setCourseIds(JsonUtils.getJsonArray("courseIds", `object`))
        }

        @JvmStatic
        fun isCourseCertified(realm: Realm, courseId: String?): Boolean {
            // FIXME
            if (courseId == null) {
                return false
            }
            val c =
                realm.where(RealmCertification::class.java).contains("courseIds", courseId).count()
            Utilities.log("$c size")
            return c > 0
        }
    }
}
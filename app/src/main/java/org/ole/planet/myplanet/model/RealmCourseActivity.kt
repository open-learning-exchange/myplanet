package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.util.Date
import java.util.UUID

open class RealmCourseActivity : RealmObject() {
    @PrimaryKey
    private var id: String? = null
    private var _id: String? = null
    @JvmField
    var createdOn: String? = null
    private var _rev: String? = null
    @JvmField
    var time: Long = 0
    @JvmField
    var title: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var user: String? = null
    fun get_rev(): String? {
        return _rev
    }

    fun set_rev(_rev: String?) {
        this._rev = _rev
    }

    fun get_id(): String? {
        return _id
    }

    fun set_id(_id: String?) {
        this._id = _id
    }

    companion object {
        @JvmStatic
        fun createActivity(realm: Realm, userModel: RealmUserModel, course: RealmMyCourse) {
            if (!realm.isInTransaction) realm.beginTransaction()
            val activity = realm.createObject(
                RealmCourseActivity::class.java, UUID.randomUUID().toString()
            )
            activity.type = "visit"
            activity.title = course.courseTitle
            activity.courseId = course.courseId
            activity.time = Date().time
            activity.parentCode = userModel.parentCode
            activity.createdOn = userModel.planetCode
            activity.createdOn = userModel.planetCode
            activity.user = userModel.name
            realm.commitTransaction()
        }

        @JvmStatic
        fun serializeSerialize(realm_courseActivities: RealmCourseActivity): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realm_courseActivities.user)
            ob.addProperty("courseId", realm_courseActivities.courseId)
            ob.addProperty("type", realm_courseActivities.type)
            ob.addProperty("title", realm_courseActivities.title)
            ob.addProperty("time", realm_courseActivities.time)
            ob.addProperty("createdOn", realm_courseActivities.createdOn)
            ob.addProperty("parentCode", realm_courseActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            return ob
        }
    }
}
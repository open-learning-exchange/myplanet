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
    @JvmField
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var _rev: String? = null
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

    companion object {
        @JvmStatic
        fun createActivity(realm: Realm, userModel: RealmUserModel?, course: RealmMyCourse?) {
            if (!realm.isInTransaction) {
                realm.beginTransaction()
            }
            val activity = realm.createObject(RealmCourseActivity::class.java, UUID.randomUUID().toString())
            activity.type = "visit"
            activity.title = course?.courseTitle
            activity.courseId = course?.courseId
            activity.time = Date().time
            activity.parentCode = userModel?.parentCode
            activity.createdOn = userModel?.planetCode
            activity.createdOn = userModel?.planetCode
            activity.user = userModel?.name
            realm.commitTransaction()
        }

        @JvmStatic
        fun serializeSerialize(realmCourseActivities: RealmCourseActivity): JsonObject {
            val ob = JsonObject()
            ob.addProperty("user", realmCourseActivities.user)
            ob.addProperty("courseId", realmCourseActivities.courseId)
            ob.addProperty("type", realmCourseActivities.type)
            ob.addProperty("title", realmCourseActivities.title)
            ob.addProperty("time", realmCourseActivities.time)
            ob.addProperty("createdOn", realmCourseActivities.createdOn)
            ob.addProperty("parentCode", realmCourseActivities.parentCode)
            ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            ob.addProperty("deviceName", NetworkUtils.getDeviceName())
            return ob
        }
    }
}
package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.*
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.util.Date
import java.util.UUID

open class RealmCourseActivity : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var courseId: String? = null
    var parentCode: String? = null
    var type: String? = null
    var user: String? = null

    companion object {
        @JvmStatic
        suspend fun createActivity(realm: Realm, userModel: RealmUserModel?, course: RealmMyCourse?) {
            withContext(Dispatchers.IO) {
                try {
                    if (!realm.isInTransaction) {
                        realm.executeTransaction { realmInstance ->
                            val activity = realmInstance.createObject(RealmCourseActivity::class.java, UUID.randomUUID().toString())
                            activity.type = "visit"
                            activity.title = course?.courseTitle
                            activity.courseId = course?.courseId
                            activity.time = Date().time
                            activity.parentCode = userModel?.parentCode
                            activity.createdOn = userModel?.planetCode
                            activity.user = userModel?.name
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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

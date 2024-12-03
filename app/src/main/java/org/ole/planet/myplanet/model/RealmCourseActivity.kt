package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.util.Date
import java.util.UUID

class RealmCourseActivity : RealmObject {
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
        fun createActivity(realm: Realm, userModel: RealmUserModel?, course: RealmMyCourse?) {
            realm.writeBlocking {
                val activity = RealmCourseActivity().apply {
                    id = UUID.randomUUID().toString()
                    type = "visit"
                    title = course?.courseTitle
                    courseId = course?.courseId
                    time = Date().time
                    parentCode = userModel?.parentCode
                    createdOn = userModel?.planetCode
                    user = userModel?.name
                }
                copyToRealm(activity)
            }
        }

        fun serializeSerialize(realmCourseActivity: RealmCourseActivity): JsonObject {
            return JsonObject().apply {
                addProperty("user", realmCourseActivity.user)
                addProperty("courseId", realmCourseActivity.courseId)
                addProperty("type", realmCourseActivity.type)
                addProperty("title", realmCourseActivity.title)
                addProperty("time", realmCourseActivity.time)
                addProperty("createdOn", realmCourseActivity.createdOn)
                addProperty("parentCode", realmCourseActivity.parentCode)
                addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                addProperty("deviceName", NetworkUtils.getDeviceName())
            }
        }
    }
}

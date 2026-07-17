package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.NetworkUtils

@Entity(
    tableName = "course_activity",
    indices = [Index("_rev"), Index("courseId"), Index("type")]
)
open class RealmCourseActivity {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
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

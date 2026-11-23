package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

@Entity(
    tableName = "course_activity",
    indices = [Index("_rev"), Index("courseId"), Index("type")]
)
open class CourseActivity {
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
        fun serialize(courseActivity: CourseActivity): JsonObject {
            val ob = buildJsonObject {
                put("user", courseActivity.user)
                put("courseId", courseActivity.courseId)
                put("type", courseActivity.type)
                put("title", courseActivity.title)
                put("time", courseActivity.time)
                put("createdOn", courseActivity.createdOn)
                put("parentCode", courseActivity.parentCode)
                put("deviceName", NetworkUtils.getDeviceName())
            }.toGson()
            ob.addDocumentOrigin()
            return ob
        }
    }
}

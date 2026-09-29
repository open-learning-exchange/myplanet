package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson

@Entity(
    tableName = "course_progress",
    indices = [Index("_id"), Index("userId"), Index("courseId"), Index(value = ["courseId", "userId", "stepNum"])]
)
open class CourseProgress {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var createdOn: String? = null
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var _rev: String? = null
    var stepNum = 0
    var passed = false
    var userId: String? = null
    var courseId: String? = null
    var parentCode: String? = null

    companion object {
        fun serializeProgress(progress: CourseProgress): JsonObject {
            val `object` = buildJsonObject {
                put("userId", progress.userId)
                put("parentCode", progress.parentCode)
                put("courseId", progress.courseId)
                put("passed", progress.passed)
                put("stepNum", progress.stepNum)
                put("createdOn", progress.createdOn)
                put("createdDate", progress.createdDate)
                put("updatedDate", progress.updatedDate)
            }.toGson()
            `object`.addDocumentOrigin()
            return `object`
        }


    }
}

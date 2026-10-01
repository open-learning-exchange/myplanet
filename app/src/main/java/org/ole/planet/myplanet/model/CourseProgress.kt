package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

    companion object
}

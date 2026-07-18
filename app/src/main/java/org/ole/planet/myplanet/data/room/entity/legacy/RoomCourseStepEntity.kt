package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for course steps, replacing CourseStep. */
@Entity(tableName = "course_steps", indices = [Index("courseId")])
data class RoomCourseStepEntity(
    @PrimaryKey val id: String,
    val courseId: String? = null,
    val stepTitle: String? = null,
    val description: String? = null,
    val noOfResources: Int? = null,
)

package org.ole.planet.myplanet.model
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "course_steps", indices = [Index("courseId")])
open class CourseStep(
    @PrimaryKey @JvmField var id: String = "",
    var courseId: String? = null,
    var stepTitle: String? = null,
    var description: String? = null,
    var noOfResources: Int? = null
) {
}

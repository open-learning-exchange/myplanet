package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag

data class CourseItem(
    val id: String?,
    val courseId: String?,
    val courseTitle: String?,
    val description: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val createdDate: Long?,
    var isMyCourse: Boolean,
    val numberOfSteps: Int,
)

fun RealmMyCourse.toCourseItem(
): CourseItem {
    return CourseItem(
        id = this.id,
        courseId = this.courseId,
        courseTitle = this.courseTitle,
        description = this.description,
        gradeLevel = this.gradeLevel,
        subjectLevel = this.subjectLevel,
        createdDate = this.createdDate,
        isMyCourse = this.isMyCourse,
        numberOfSteps = this.getNumberOfSteps(),
    )
}

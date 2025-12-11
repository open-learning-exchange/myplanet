package org.ole.planet.myplanet.model.dto

import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.model.RealmMyCourse

data class CourseItem(
    @PrimaryKey
    var id: String? = null,
    var courseId: String? = null,
    var courseTitle: String? = null,
    var description: String? = null,
)

fun RealmMyCourse.toCourseItem(): CourseItem {
    return CourseItem(
        id = this.id,
        courseId = this.courseId,
        courseTitle = this.courseTitle,
        description = this.description,
    )
}

package org.ole.planet.myplanet.model.mappers

import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.dto.CourseItem

fun RealmMyCourse.toCourseItem(): CourseItem {
    return CourseItem(
        id = this.id,
        courseId = this.courseId,
        courseTitle = this.courseTitle,
        description = this.description,
        gradeLevel = this.gradeLevel,
        subjectLevel = this.subjectLevel,
        createdDate = this.createdDate,
        isMyCourse = this.isMyCourse,
        numberOfSteps = this.getNumberOfSteps()
    )
}

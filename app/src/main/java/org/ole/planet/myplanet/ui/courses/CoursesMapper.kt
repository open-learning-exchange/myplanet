package org.ole.planet.myplanet.ui.courses

import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse

internal fun RealmMyCourse.toCourse(): Course {
    return Course(
        courseId = this.courseId ?: "",
        courseTitle = this.courseTitle ?: "",
        description = this.description ?: "",
        gradeLevel = this.gradeLevel ?: "",
        subjectLevel = this.subjectLevel ?: "",
        createdDate = this.createdDate,
        numberOfSteps = this.getNumberOfSteps(),
        isMyCourse = this.isMyCourse
    )
}

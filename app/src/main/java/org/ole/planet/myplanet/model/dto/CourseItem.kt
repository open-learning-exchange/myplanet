package org.ole.planet.myplanet.model.dto

data class CourseItem(
    val id: String?,
    val courseId: String?,
    val courseTitle: String?,
    val description: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val createdDate: Long,
    var isMyCourse: Boolean,
    val numberOfSteps: Int
)

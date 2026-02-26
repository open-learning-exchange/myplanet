package org.ole.planet.myplanet.model

data class Course(
    val id: String?,
    val courseId: String?,
    val courseTitle: String?,
    val description: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val createdDate: Long,
    var isMyCourse: Boolean = false,
    val numberOfSteps: Int
)

package org.ole.planet.myplanet.model

data class Course(
    val courseId: String,
    val courseTitle: String,
    val description: String,
    val gradeLevel: String,
    val subjectLevel: String,
    val createdDate: Long,
    val numberOfSteps: Int = 0,
    val isMyCourse: Boolean = false
)

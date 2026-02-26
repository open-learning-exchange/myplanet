package org.ole.planet.myplanet.model

data class Course(
    val id: String?,
    val courseId: String?,
    val courseRev: String?,
    val languageOfInstruction: String?,
    val courseTitle: String?,
    val memberLimit: Int?,
    val description: String?,
    val method: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val createdDate: Long,
    val numberOfSteps: Int,
    var isMyCourse: Boolean = false,
    val userId: List<String>? = null
)

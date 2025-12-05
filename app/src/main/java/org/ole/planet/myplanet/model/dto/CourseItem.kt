package org.ole.planet.myplanet.model.dto

data class CourseItem(
    val id: String?,
    val courseId: String?,
    val courseTitle: String?,
    val description: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val date: String?,
    val rating: Float,
    val timesRated: Int,
    val progress: Int,
    val progressMax: Int,
    val isMyCourse: Boolean,
    var isSelected: Boolean = false,
    val numberOfSteps: Int,
    val isGuest: Boolean
)

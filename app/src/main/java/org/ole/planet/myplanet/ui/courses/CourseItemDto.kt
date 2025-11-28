package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject

data class CourseItemDto(
    val id: String?,
    val courseId: String?,
    val courseTitle: String?,
    val description: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val createdDate: Long?,
    val isMyCourse: Boolean,
    val numberOfSteps: Int,
    val rating: JsonObject?,
    val progress: JsonObject?
)

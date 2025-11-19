package org.ole.planet.myplanet.ui.courses

data class CourseProgress(
    val courseName: String?,
    val courseId: String?,
    val progress: Map<String, Int>?,
    val mistakes: Int,
    val stepMistake: Map<String, Int>
)

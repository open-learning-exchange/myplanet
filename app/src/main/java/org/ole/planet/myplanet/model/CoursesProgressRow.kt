package org.ole.planet.myplanet.model

data class CoursesProgressRow(
    val courseId: String,
    val courseName: String,
    val progressCurrent: Int?,
    val progressMax: Int?,
    val mistakes: Int?,
    val stepMistake: Map<String, Int>?
)

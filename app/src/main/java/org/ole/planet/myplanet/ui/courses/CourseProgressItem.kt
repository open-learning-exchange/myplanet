package org.ole.planet.myplanet.ui.courses

data class CourseProgressItem(
    val courseId: String,
    val courseName: String,
    val progressCurrent: Int,
    val progressMax: Int,
    val mistakes: String,
    val stepMistakes: List<StepMistake>
)

data class StepMistake(
    val step: Int,
    val mistakes: Int
)

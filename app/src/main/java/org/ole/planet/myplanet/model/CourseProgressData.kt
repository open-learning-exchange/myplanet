package org.ole.planet.myplanet.model

data class CourseProgressData(
    val title: String?,
    val current: Int,
    val max: Int,
    val steps: List<StepProgress>
)

data class StepProgress(
    val stepId: String,
    var completed: Boolean = false,
    var percentage: Double = 0.0,
    var status: String? = null
)

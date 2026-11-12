package org.ole.planet.myplanet.model

data class CourseProgressState(
    val current: Int,
    val max: Int,
    val completedAt: Long = 0L
)

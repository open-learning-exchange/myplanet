package org.ole.planet.myplanet.model

data class ProgressData(
    val courseId: String,
    val courseName: String,
    val progress: Int,
    val mistakes: Int,
    val stepMistakes: Map<String, Int>
)

package org.ole.planet.myplanet.model

data class StepPrerequisites(
    val isMyCourse: Boolean,
    val hasExam: Boolean,
    val hasSurvey: Boolean,
    val courseTitle: String?
)

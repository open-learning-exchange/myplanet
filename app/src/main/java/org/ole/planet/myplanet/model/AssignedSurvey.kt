package org.ole.planet.myplanet.model

data class AssignedSurvey(
    val exam: StepExam,
    val isTeam: Boolean,
    val teamId: String?
)

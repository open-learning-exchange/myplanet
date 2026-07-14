package org.ole.planet.myplanet.model

data class AssignedSurvey(
    val exam: RealmStepExam,
    val isTeam: Boolean,
    val teamId: String?
)

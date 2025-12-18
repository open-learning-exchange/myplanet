package org.ole.planet.myplanet.model

data class AdoptSurveyRequest(
    val exam: RealmStepExam,
    val teamId: String?,
    val isTeam: Boolean,
    val user: RealmUserModel?,
    val parentCode: String?,
    val planetCode: String?
)

package org.ole.planet.myplanet.model

data class CreateExamSubmissionRequest(
    val userId: String?,
    val userDob: String?,
    val userGender: String?,
    val exam: StepExam,
    val type: String?,
    val teamId: String?
)

package org.ole.planet.myplanet.ui.survey

import org.ole.planet.myplanet.model.RealmStepExam

data class SurveyDisplayModel(
    val realmStepExam: RealmStepExam,
    val submissionCount: String,
    val lastSubmissionDate: String,
    val creationDate: String,
    val questionCount: Long,
    val teamSubmissionIsValid: Boolean?,
)

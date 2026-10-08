package org.ole.planet.myplanet.model

data class SurveyRow(
    val exam: StepExam,
    val surveyInfo: SurveyInfo?,
    val formState: SurveyFormState?
)

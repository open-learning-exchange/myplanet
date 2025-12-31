package org.ole.planet.myplanet.ui.survey

import org.ole.planet.myplanet.model.RealmSubmission

data class SurveyFormState(
    val teamSubmission: RealmSubmission?,
    val questionCount: Int
)

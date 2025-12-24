package org.ole.planet.myplanet.ui.survey

import org.ole.planet.myplanet.model.RealmSubmission

data class SurveyBindingData(
    val teamSubmission: RealmSubmission?,
    val questionCount: Int
)

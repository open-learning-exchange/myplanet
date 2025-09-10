package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmStepExam

interface SurveyAdoptListener {
    fun onAdoptRequested(exam: RealmStepExam, teamId: String?)
}

package org.ole.planet.myplanet.ui.submission

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

data class SubmissionItem(
    val submission: RealmSubmission,
    val exam: RealmStepExam?,
    val count: Int
)

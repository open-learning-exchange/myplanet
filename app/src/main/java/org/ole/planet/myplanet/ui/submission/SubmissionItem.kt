package org.ole.planet.myplanet.ui.submission

import org.ole.planet.myplanet.model.RealmSubmission

data class SubmissionItem(
    val submission: RealmSubmission,
    val examName: String?,
    val submissionCount: Int,
    val userName: String?
)

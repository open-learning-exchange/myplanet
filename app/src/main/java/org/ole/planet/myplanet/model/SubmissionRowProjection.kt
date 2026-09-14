package org.ole.planet.myplanet.model

data class SubmissionRowProjection(
    val submission: Submission,
    val submitterName: String,
    val submissionCount: Int,
)

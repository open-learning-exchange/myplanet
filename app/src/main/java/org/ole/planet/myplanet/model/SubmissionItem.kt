package org.ole.planet.myplanet.model

data class SubmissionItem(
    val id: String?,
    val lastUpdateTime: Long,
    val status: String,
    val uploaded: Boolean
)

package org.ole.planet.myplanet.ui.submission

data class SubmissionItem(
    val id: String?,
    val parentId: String?,
    val type: String?,
    val userId: String?,
    val status: String?,
    val lastUpdateTime: Long?,
    val startTime: Long?,
    val uploaded: Boolean,
    val examName: String?,
    val submissionCount: Int,
    val userName: String?
)

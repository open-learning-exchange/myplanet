package org.ole.planet.myplanet.ui.submissions

data class SubmissionUiModel(
    val id: String?,
    val status: String?,
    val startTime: Long,
    val lastUpdateTime: Long,
    val parentId: String?,
    val userId: String?,
    val submitterName: String
)

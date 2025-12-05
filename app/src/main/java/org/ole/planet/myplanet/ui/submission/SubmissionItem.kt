package org.ole.planet.myplanet.ui.submission

data class SubmissionItem(
    val id: String?,
    val parentId: String?,
    val type: String?,
    val userId: String?,
    val status: String?,
    val startTime: Long?,
    val lastUpdateTime: Long?,
    val examName: String?,
    val userName: String?,
    val count: Int,
)

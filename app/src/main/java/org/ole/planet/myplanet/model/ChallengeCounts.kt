package org.ole.planet.myplanet.model

data class ChallengeCounts(
    val voiceCount: Int,
    val allVoiceCount: Int,
    val hasUnfinishedSurvey: Boolean,
    val courseName: String?,
    val hasSyncAction: Boolean
)

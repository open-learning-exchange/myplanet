package org.ole.planet.myplanet.utils

fun calculateIndividualProgress(voiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
    val earnedDollarsVoice = minOf(voiceCount, 5) * 2
    val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
    val total = earnedDollarsVoice + earnedDollarsSurvey
    return total.coerceAtMost(500)
}

fun calculateCommunityProgress(allVoiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
    val earnedDollarsVoice = minOf(allVoiceCount, 5) * 2
    val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
    val total = earnedDollarsVoice + earnedDollarsSurvey
    return total.coerceAtMost(11)
}

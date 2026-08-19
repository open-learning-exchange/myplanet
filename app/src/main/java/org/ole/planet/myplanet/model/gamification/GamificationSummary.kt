package org.ole.planet.myplanet.model.gamification

data class GamificationSummary(
    val streakInfo: StudyStreakInfo = StudyStreakInfo(),
    val badges: List<GamificationBadge> = emptyList(),
    val certificates: List<CourseCertificate> = emptyList(),
    val totalBadgesCount: Int = 0,
    val unlockedBadgesCount: Int = 0,
    val completedCoursesCount: Int = 0,
    val resourcesReadCount: Long = 0L,
    val tasksCompletedCount: Int = 0
)

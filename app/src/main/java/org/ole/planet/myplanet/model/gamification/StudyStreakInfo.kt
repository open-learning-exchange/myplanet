package org.ole.planet.myplanet.model.gamification

data class StudyStreakInfo(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val isActiveToday: Boolean = false,
    val recentActiveDays: List<Boolean> = List(7) { false },
    val totalActiveDays: Int = 0
)

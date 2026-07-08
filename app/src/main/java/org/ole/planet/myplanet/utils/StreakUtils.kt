package org.ole.planet.myplanet.utils

import java.time.Instant
import java.time.ZoneId

object StreakUtils {
    /**
     * Number of consecutive days with at least one activity, counting back from
     * today. Activity yesterday keeps the streak alive, so it doesn't reset to 0
     * before the user has had a chance to act today.
     */
    fun calculateDayStreak(activityTimes: List<Long>, now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Int {
        if (activityTimes.isEmpty()) return 0
        val days = activityTimes.map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }.toHashSet()
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}

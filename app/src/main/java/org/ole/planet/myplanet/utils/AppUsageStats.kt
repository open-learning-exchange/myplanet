package org.ole.planet.myplanet.utils

/** One app's usage totals over a stats bucket, as reported by the platform. */
data class AppUsageStat(
    val packageName: String,
    val firstTimeStamp: Long,
    val lastTimeStamp: Long,
    val lastTimeUsed: Long,
    val totalTimeInForeground: Long
)

/**
 * Injectable view of the platform's per-app usage statistics. The Android implementation is
 * [AndroidAppUsageStats].
 */
interface AppUsageStats {
    /**
     * Daily usage buckets for every app between [beginTime] and [endTime] (epoch millis), or
     * null when the platform returns none.
     */
    fun queryDailyUsage(beginTime: Long, endTime: Long): List<AppUsageStat>?
}

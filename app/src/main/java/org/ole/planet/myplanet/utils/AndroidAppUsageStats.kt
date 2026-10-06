package org.ole.planet.myplanet.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppUsageStats @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppUsageStats {
    override fun queryDailyUsage(beginTime: Long, endTime: Long): List<AppUsageStat>? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime)?.map { s ->
            AppUsageStat(
                packageName = s.packageName,
                firstTimeStamp = s.firstTimeStamp,
                lastTimeStamp = s.lastTimeStamp,
                lastTimeUsed = s.lastTimeUsed,
                totalTimeInForeground = s.totalTimeInForeground
            )
        }
    }
}

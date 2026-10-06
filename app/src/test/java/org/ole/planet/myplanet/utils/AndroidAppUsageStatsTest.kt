package org.ole.planet.myplanet.utils

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAppUsageStatsTest {

    private val usageStatsManager: UsageStatsManager = mockk()
    private val context: Context = mockk {
        every { getSystemService(Context.USAGE_STATS_SERVICE) } returns usageStatsManager
    }
    private val appUsageStats = AndroidAppUsageStats(context)

    @Test
    fun `queryDailyUsage maps platform stats over the daily interval`() {
        val stats: UsageStats = mockk {
            every { packageName } returns "org.ole.planet.myplanet"
            every { firstTimeStamp } returns 2000L
            every { lastTimeStamp } returns 3000L
            every { lastTimeUsed } returns 4000L
            every { totalTimeInForeground } returns 1000L
        }
        every { usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 1000L, 5000L) } returns listOf(stats)

        val result = appUsageStats.queryDailyUsage(1000L, 5000L)

        assertEquals(listOf(AppUsageStat("org.ole.planet.myplanet", 2000L, 3000L, 4000L, 1000L)), result)
    }

    @Test
    fun `queryDailyUsage is null when the platform returns null`() {
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns null

        assertNull(appUsageStats.queryDailyUsage(0L, 1L))
    }
}

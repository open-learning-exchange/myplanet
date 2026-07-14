package org.ole.planet.myplanet.ui.sync

import java.util.concurrent.TimeUnit

object ForceSyncPolicy {


    const val WEEKLY_MAX_DAYS = 7
    const val MONTHLY_MAX_DAYS = 30

    fun maxDaysForAutoSync(autoSyncEnabled: Boolean, weekly: Boolean, monthly: Boolean): Int? {
        return when {
            !autoSyncEnabled -> null
            weekly -> WEEKLY_MAX_DAYS
            monthly -> MONTHLY_MAX_DAYS
            else -> null
        }
    }

    /**
     * Days since the last sync when the device is overdue, or null when a forced
     * sync is not required (never synced, or synced within [maxDays]).
     */
    fun overdueDays(lastSyncMillis: Long, nowMillis: Long, maxDays: Int): Long? {
        if (lastSyncMillis <= 0) {
            return null
        }
        val daysDiff = TimeUnit.MILLISECONDS.toDays(nowMillis - lastSyncMillis)
        return if (daysDiff >= maxDays) daysDiff else null
    }
}

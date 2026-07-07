package org.ole.planet.myplanet.ui.sync

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForceSyncPolicyTest {

    private val now = 1_700_000_000_000L

    private fun daysAgo(days: Long): Long = now - TimeUnit.DAYS.toMillis(days)

    @Test
    fun `no forced sync when autosync is disabled`() {
        assertNull(ForceSyncPolicy.maxDaysForAutoSync(autoSyncEnabled = false, weekly = true, monthly = true))
    }

    @Test
    fun `weekly autosync forces after seven days`() {
        assertEquals(7, ForceSyncPolicy.maxDaysForAutoSync(autoSyncEnabled = true, weekly = true, monthly = false))
    }

    @Test
    fun `monthly autosync forces after thirty days`() {
        assertEquals(30, ForceSyncPolicy.maxDaysForAutoSync(autoSyncEnabled = true, weekly = false, monthly = true))
    }

    @Test
    fun `weekly wins when both intervals are enabled`() {
        assertEquals(7, ForceSyncPolicy.maxDaysForAutoSync(autoSyncEnabled = true, weekly = true, monthly = true))
    }

    @Test
    fun `no forced sync when autosync is enabled without an interval`() {
        assertNull(ForceSyncPolicy.maxDaysForAutoSync(autoSyncEnabled = true, weekly = false, monthly = false))
    }

    @Test
    fun `never-synced devices are not forced`() {
        assertNull(ForceSyncPolicy.overdueDays(0L, now, 7))
        assertNull(ForceSyncPolicy.overdueDays(-1L, now, 7))
    }

    @Test
    fun `recently synced devices are not forced`() {
        assertNull(ForceSyncPolicy.overdueDays(daysAgo(6), now, 7))
    }

    @Test
    fun `device is forced exactly at the threshold`() {
        assertEquals(7L, ForceSyncPolicy.overdueDays(daysAgo(7), now, 7))
    }

    @Test
    fun `overdue devices report days since last sync`() {
        assertEquals(45L, ForceSyncPolicy.overdueDays(daysAgo(45), now, 30))
    }

    @Test
    fun `partial days do not count toward the threshold`() {
        val almostSevenDays = daysAgo(7) + TimeUnit.HOURS.toMillis(1)
        assertNull(ForceSyncPolicy.overdueDays(almostSevenDays, now, 7))
    }
}

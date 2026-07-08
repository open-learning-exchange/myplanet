package org.ole.planet.myplanet.utils

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakUtilsTest {

    private val zone = ZoneId.of("UTC")
    private val day = 86_400_000L
    // A fixed "now" at noon UTC so day boundaries are unambiguous.
    private val now = 1_750_000_000_000L - (1_750_000_000_000L % day) + day / 2

    @Test
    fun `no activity means no streak`() {
        assertEquals(0, StreakUtils.calculateDayStreak(emptyList(), now, zone))
    }

    @Test
    fun `activity today alone is a one day streak`() {
        assertEquals(1, StreakUtils.calculateDayStreak(listOf(now), now, zone))
    }

    @Test
    fun `consecutive days ending today are counted`() {
        val times = listOf(now, now - day, now - 2 * day)
        assertEquals(3, StreakUtils.calculateDayStreak(times, now, zone))
    }

    @Test
    fun `activity yesterday keeps the streak alive`() {
        val times = listOf(now - day, now - 2 * day)
        assertEquals(2, StreakUtils.calculateDayStreak(times, now, zone))
    }

    @Test
    fun `a full missed day resets the streak`() {
        val times = listOf(now - 2 * day, now - 3 * day)
        assertEquals(0, StreakUtils.calculateDayStreak(times, now, zone))
    }

    @Test
    fun `a gap stops the count`() {
        val times = listOf(now, now - 2 * day, now - 3 * day)
        assertEquals(1, StreakUtils.calculateDayStreak(times, now, zone))
    }

    @Test
    fun `multiple activities on the same day count once`() {
        val times = listOf(now, now - 1000, now - 2000, now - day)
        assertEquals(2, StreakUtils.calculateDayStreak(times, now, zone))
    }
}

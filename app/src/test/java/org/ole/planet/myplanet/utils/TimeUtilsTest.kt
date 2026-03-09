package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class TimeUtilsTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        // Set fixed timezone for deterministic tests where systemDefault is used
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        // Pin the system time to 2024-03-05 16:00:00 UTC for deterministic age calculation
        ShadowSystemClock.setSystemTime(1709654400000L)
    }

    @Test
    fun testGetFormattedDate_withEpochZero() {
        val timestamp: Long = 0
        val expected = "Thursday, Jan 01, 1970"
        val result = TimeUtils.getFormattedDate(timestamp)
        assertEquals(expected, result)
    }

    @Test
    fun testGetFormattedDate_withSpecificDate() {
        val timestamp: Long = 1709654400000 // 2024-03-05 16:00:00 UTC
        val expected = "Tuesday, Mar 05, 2024"
        val result = TimeUtils.getFormattedDate(timestamp)
        assertEquals(expected, result)
    }

    @Test
    fun testGetFormattedDate_withNull() {
        val result = TimeUtils.getFormattedDate(null)
        assertNotNull(result)
        // With pinned time, result should be exactly this:
        assertEquals("Tuesday, Mar 05, 2024", result)
    }

    @Test
    fun testGetFormattedDateWithTime() {
        val timestamp: Long = 1709654400000 // 2024-03-05 16:00:00 UTC
        // Pattern: "EEE dd, MMMM yyyy , hh:mm a"
        val result = TimeUtils.getFormattedDateWithTime(timestamp)
        assertEquals("Tue 05, March 2024 , 04:00 PM", result)
    }

    @Test
    fun testFormatDateTZ() {
        val timestamp: Long = 1709654400000 // 2024-03-05 16:00:00 UTC
        // Pattern: "yyyy-MM-dd HH:mm:ss"
        val result = TimeUtils.formatDateTZ(timestamp)
        assertEquals("2024-03-05 16:00:00", result)
    }

    @Test
    fun testGetAge() {
        // Reference time is 2024-03-05
        val age = TimeUtils.getAge("1990-01-01")
        assertEquals(34, age)

        val ageJustTurned = TimeUtils.getAge("1990-03-05")
        assertEquals(34, ageJustTurned)

        val ageAlmostTurned = TimeUtils.getAge("1990-03-06")
        assertEquals(33, ageAlmostTurned)
    }

    @Test
    fun testGetAge_withDateTime() {
        // Reference time is 2024-03-05
        val age = TimeUtils.getAge("1990-01-01 10:00:00")
        assertEquals(34, age)
    }

    @Test
    fun testGetFormattedDate_withStringPattern() {
        val dateStr = "2024-03-05"
        val pattern = "yyyy-MM-dd"
        val result = TimeUtils.getFormattedDate(dateStr, pattern)
        assertEquals("Tuesday, Mar 05, 2024", result)
    }

    @Test
    fun testFormatDate_simple() {
        val timestamp: Long = 1709654400000
        // Pattern: "EEE dd, MMMM yyyy"
        val result = TimeUtils.formatDate(timestamp)
        assertEquals("Tue 05, March 2024", result)
    }

    @Test
    fun testFormatDate_withCustomPattern() {
        val timestamp: Long = 1709654400000
        val result = TimeUtils.formatDate(timestamp, "dd/MM/yyyy")
        assertEquals("05/03/2024", result)
    }

    @Test
    fun testParseDate() {
        val dateString = "Tue 05, March 2024"
        val result = TimeUtils.parseDate(dateString)
        assertEquals(1709596800000L, result) // 2024-03-05 00:00:00 UTC
    }

    @Test
    fun testParseInstantFromString() {
        val dateString = "2024-03-05T16:00:00Z"
        val result = TimeUtils.parseInstantFromString(dateString)
        assertEquals(1709654400000L, result?.toEpochMilli())
    }

    @Test
    fun testParseInstantFromString_noT() {
        val dateString = "2024-03-05"
        val result = TimeUtils.parseInstantFromString(dateString)
        assertEquals(1709596800000L, result?.toEpochMilli())
    }

    @Test
    fun testConvertToISO8601() {
        val date = "2024-03-05"
        val result = TimeUtils.convertToISO8601(date)
        assertEquals("2024-03-05T00:00:00.000Z", result)
    }

    @Test
    fun testFormatDateToDDMMYYYY() {
        val dateStr = "2024-03-05"
        val result = TimeUtils.formatDateToDDMMYYYY(dateStr)
        assertEquals("05-03-2024", result)
    }

    @Test
    fun testFormatDateToDDMMYYYY_withT() {
        val dateStr = "2024-03-05T16:00:00.000Z"
        val result = TimeUtils.formatDateToDDMMYYYY(dateStr)
        assertEquals("05-03-2024", result)
    }

    @Test
    fun testConvertDDMMYYYYToISO() {
        val dateStr = "05-03-2024"
        val result = TimeUtils.convertDDMMYYYYToISO(dateStr)
        assertEquals("2024-03-05T00:00:00.000Z", result)
    }

    @Test
    fun testGetRelativeTime_past() {
        // Since system time is pinned to 1709654400000 (2024-03-05 16:00:00 UTC)
        val hourAgo = 1709654400000L - 3600 * 1000
        val result = TimeUtils.getRelativeTime(hourAgo)
        // DateUtils.getRelativeTimeSpanString should use the pinned time
        assertTrue("Expected relative time string, got '$result'", result.contains("ago") || result.contains("hour"))
    }

    @Test
    fun testGetRelativeTime_future() {
        val pinnedTime = 1709654400000L
        // Note: The function collapses all future timestamps into a single "Just now" string.
        // This includes events even years in the future.
        val hourHence = pinnedTime + 3600 * 1000
        val yearHence = pinnedTime + (365L * 24 * 3600 * 1000)

        assertEquals("Future timestamps should return 'Just now'", "Just now", TimeUtils.getRelativeTime(hourHence))
        assertEquals("Distant future timestamps should also return 'Just now'", "Just now", TimeUtils.getRelativeTime(yearHence))
    }
}

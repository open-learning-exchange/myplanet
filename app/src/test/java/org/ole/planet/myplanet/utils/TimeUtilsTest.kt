package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class TimeUtilsTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        // Set fixed timezone for deterministic tests where systemDefault is used
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
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
        assertTrue("Result should not be N/A", result != "N/A")
        val pattern = """^[A-Za-z]+, [A-Za-z]+ \d{2}, \d{4}$""".toRegex()
        assertTrue("Result '$result' does not match pattern", pattern.matches(result))
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
        // This test depends on the current date, but we can test with a fixed birth date
        // and check if it returns a reasonable age.
        // Actually getAge uses LocalDate.now() which we can't easily mock here without more dependencies
        // but we can check if it parses correctly.
        val age = TimeUtils.getAge("1990-01-01")
        assertTrue(age >= 34) // As of 2024/2025
    }

    @Test
    fun testGetAge_withDateTime() {
        val age = TimeUtils.getAge("1990-01-01 10:00:00")
        assertTrue(age >= 34)
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
        val now = System.currentTimeMillis()
        val hourAgo = now - 3600 * 1000
        val result = TimeUtils.getRelativeTime(hourAgo)
        assertTrue(result.contains("ago") || result.contains("hour"))
    }

    @Test
    fun testGetRelativeTime_future() {
        val now = System.currentTimeMillis()
        val hourHence = now + 3600 * 1000
        val result = TimeUtils.getRelativeTime(hourHence)
        assertEquals("Just now", result)
    }
}

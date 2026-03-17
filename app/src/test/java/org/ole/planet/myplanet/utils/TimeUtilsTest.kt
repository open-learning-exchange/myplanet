package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TimeUtilsTest {

    @Test
    fun testConvertToISO8601_validDate() {
        val dateString = "2023-05-15"
        val expected = "2023-05-15T00:00:00.000Z"
        val result = TimeUtils.convertToISO8601(dateString)
        assertEquals(expected, result)
    }

    @Test
    fun testConvertToISO8601_ignoresLocalTimeZone() {
        val originalDefault = TimeZone.getDefault()
        try {
            // Set default timezone to something far from UTC
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")) // UTC+5:30
            val dateString = "2023-05-15"
            val expected = "2023-05-15T00:00:00.000Z"
            val result = TimeUtils.convertToISO8601(dateString)
            assertEquals(expected, result)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun testConvertToISO8601_negativeYearMonthDay() {
        // parts[1] becomes empty string, toInt() throws NumberFormatException, returns original
        val dateString = "2023--05-15"
        val result = TimeUtils.convertToISO8601(dateString)
        assertEquals(dateString, result)
    }

    @Test
    fun testConvertToISO8601_singleDigitMonthDay() {
        // "2023-5-5" -> 2023-05-05T00:00:00.000Z
        val dateString = "2023-5-5"
        val expected = "2023-05-05T00:00:00.000Z"
        val result = TimeUtils.convertToISO8601(dateString)
        assertEquals(expected, result)
    }

    @Test
    fun testConvertToISO8601_feb29NonLeapYear() {
        // 2023 is not a leap year. Feb 29 rolls over to March 1.
        val dateString = "2023-02-29"
        val expected = "2023-03-01T00:00:00.000Z"
        val result = TimeUtils.convertToISO8601(dateString)
        assertEquals(expected, result)
    }

    @Test
    fun testConvertToISO8601_invalidFormat() {
        val invalidDate = "2023/05/15"
        val result = TimeUtils.convertToISO8601(invalidDate)
        assertEquals(invalidDate, result)
    }

    @Test
    fun testConvertToISO8601_emptyString() {
        val emptyDate = ""
        val result = TimeUtils.convertToISO8601(emptyDate)
        assertEquals(emptyDate, result)
    }

    @Test
    fun testConvertToISO8601_notNumbers() {
        val invalidDate = "YYYY-MM-DD"
        val result = TimeUtils.convertToISO8601(invalidDate)
        assertEquals(invalidDate, result)
    }

    @Test
    fun testConvertToISO8601_shortDate() {
        val shortDate = "2023-05"
        val result = TimeUtils.convertToISO8601(shortDate)
        assertEquals(shortDate, result)
    }

    @Test
    fun testConvertToISO8601_longDate() {
        val longDate = "2023-05-15-10"
        val result = TimeUtils.convertToISO8601(longDate)
        assertEquals(longDate, result)
    }
}

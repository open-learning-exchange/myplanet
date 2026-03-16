package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun testConvertToISO8601_validDate() {
        val dateString = "2023-05-15"
        val expected = "2023-05-15T00:00:00.000Z"
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

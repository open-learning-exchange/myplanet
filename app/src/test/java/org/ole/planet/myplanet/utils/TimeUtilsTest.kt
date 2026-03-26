package org.ole.planet.myplanet.utils

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TimeUtilsTest {

    private lateinit var defaultLocale: Locale
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultTimeZone = TimeZone.getDefault()

        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun testGetRelativeTime() {
        // We cannot fully test getRelativeTime because it uses android.text.format.DateUtils
        // which returns null or throws an error in standard JUnit without Robolectric.
        // But we can test the fallback for future time.
        val futureTime = System.currentTimeMillis() + 100000
        val relative = TimeUtils.getRelativeTime(futureTime)
        assertEquals("Just now", relative)
    }

    @Test
    fun testGetFormattedDate() {
        // March 11, 2024, 00:00:00 UTC
        val timestamp = 1710115200000L
        val formatted = TimeUtils.getFormattedDate(timestamp)
        assertEquals("Monday, Mar 11, 2024", formatted)

        val formattedNull = TimeUtils.getFormattedDate(null)
        assertNotEquals("N/A", formattedNull) // should return current date, not N/A
    }

    @Test
    fun testGetFormattedDateWithTime() {
        val timestamp = 1710115200000L
        val formatted = TimeUtils.getFormattedDateWithTime(timestamp)
        // With UTC timezone default, it should be Mon 11, March 2024 , 12:00 AM
        // Note: Java 8 DateTimeFormatter with 'EEE' produces 'Mon'
        assertEquals("Mon 11, March 2024 , 12:00 AM", formatted)
    }

    @Test
    fun testFormatDateTZ() {
        val timestamp = 1710115200000L
        val formatted = TimeUtils.formatDateTZ(timestamp)
        assertEquals("2024-03-11 00:00:00", formatted)
    }

    @Test
    fun testGetAge() {
        // Assume age from 2000-01-01 to today. It will be > 20
        val age = TimeUtils.getAge("2000-01-01")
        val expectedAge = java.time.Period.between(LocalDate.of(2000, 1, 1), LocalDate.now()).years
        assertEquals(expectedAge, age)

        // Test with time
        val age2 = TimeUtils.getAge("2000-01-01T10:00:00.000Z")
        assertEquals(expectedAge, age2)

        // Test blank
        assertEquals(0, TimeUtils.getAge(""))

        // Test invalid
        assertEquals(0, TimeUtils.getAge("invalid date"))
    }

    @Test
    fun testGetFormattedDateWithString() {
        val dateString = "2024-03-11T12:00:00.000Z"
        val pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        val formatted = TimeUtils.getFormattedDate(dateString, pattern)
        assertEquals("Monday, Mar 11, 2024", formatted)

        // Date only
        val formatted2 = TimeUtils.getFormattedDate("2024-03-11", "yyyy-MM-dd")
        assertEquals("Monday, Mar 11, 2024", formatted2)

        assertEquals("N/A", TimeUtils.getFormattedDate("", "yyyy-MM-dd"))
        assertEquals("N/A", TimeUtils.getFormattedDate("2024-03-11", ""))
        assertEquals("N/A", TimeUtils.getFormattedDate(null, null))
        assertEquals("N/A", TimeUtils.getFormattedDate("invalid", "yyyy-MM-dd"))
    }

    @Test
    fun testFormatDate() {
        val timestamp = 1710115200000L
        val formatted = TimeUtils.formatDate(timestamp)
        assertEquals("Mon 11, March 2024", formatted)
    }

    @Test
    fun testFormatDateWithPattern() {
        val timestamp = 1710115200000L
        val formatted = TimeUtils.formatDate(timestamp, "yyyy-MM-dd")
        assertEquals("2024-03-11", formatted)

        // null format uses empty string, which might format to empty or throw exception
        // The method catches Exception and returns ""
        val formattedNull = TimeUtils.formatDate(timestamp, null)
        assertEquals("", formattedNull)
    }

    @Test
    fun testParseDate() {
        // parseDate expects format "EEE dd, MMMM yyyy" -> "Mon 11, March 2024"
        val timestamp = TimeUtils.parseDate("Mon 11, March 2024")
        assertNotNull(timestamp)
        assertEquals(1710115200000L, timestamp)

        // Fallback format "dd, MMMM yyyy"
        val timestamp2 = TimeUtils.parseDate("11, March 2024")
        assertNotNull(timestamp2)
        assertEquals(1710115200000L, timestamp2)

        assertNull(TimeUtils.parseDate("invalid"))
    }

    @Test
    fun testParseInstantFromString() {
        val instant1 = TimeUtils.parseInstantFromString("2024-03-11T10:00:00Z")
        assertNotNull(instant1)
        assertEquals(Instant.parse("2024-03-11T10:00:00Z"), instant1)

        val instant2 = TimeUtils.parseInstantFromString("2024-03-11")
        assertNotNull(instant2)
        assertEquals(Instant.parse("2024-03-11T00:00:00.000Z"), instant2)

        assertNull(TimeUtils.parseInstantFromString("invalid"))
    }

    @Test
    fun testConvertToISO8601() {
        val iso = TimeUtils.convertToISO8601("2024-03-11")
        assertEquals("2024-03-11T00:00:00.000Z", iso)

        // Invalid returns same string
        assertEquals("invalid", TimeUtils.convertToISO8601("invalid"))
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
    fun testConvertToISO8601_negativeYearMonthDay() {
        // parts[1] becomes empty string, toInt() throws NumberFormatException, returns original
        val dateString = "2023--05-15"
        val result = TimeUtils.convertToISO8601(dateString)
        assertEquals(dateString, result)
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

    @Test
    fun testFormatDateToDDMMYYYY() {
        val formatted = TimeUtils.formatDateToDDMMYYYY("2024-03-11")
        assertEquals("11-03-2024", formatted)

        val formattedTime = TimeUtils.formatDateToDDMMYYYY("2024-03-11T12:00:00.000Z")
        assertEquals("11-03-2024", formattedTime)

        assertEquals("", TimeUtils.formatDateToDDMMYYYY(""))
        assertEquals("", TimeUtils.formatDateToDDMMYYYY(null))
        assertEquals("invalid", TimeUtils.formatDateToDDMMYYYY("invalid"))
    }

    @Test
    fun testConvertDDMMYYYYToISO() {
        val iso = TimeUtils.convertDDMMYYYYToISO("11-03-2024")
        assertEquals("2024-03-11T00:00:00.000Z", iso)

        assertEquals("", TimeUtils.convertDDMMYYYYToISO(""))
        assertEquals("", TimeUtils.convertDDMMYYYYToISO(null))
        assertEquals("invalid", TimeUtils.convertDDMMYYYYToISO("invalid"))
    }
}

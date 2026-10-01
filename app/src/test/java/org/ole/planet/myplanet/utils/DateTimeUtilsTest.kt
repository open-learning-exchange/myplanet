package org.ole.planet.myplanet.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DateTimeUtilsTest {

    private lateinit var defaultLocale: Locale
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    private fun pin(locale: String, zone: String) {
        Locale.setDefault(Locale.forLanguageTag(locale))
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    private val pins = listOf("en-US" to "UTC", "ar" to "Asia/Kathmandu", "ne" to "America/New_York")

    // The java.time implementation convertToISO8601 had before it moved to kotlinx-datetime.
    private fun legacyConvertToISO8601(date: String): String {
        return try {
            val parts = date.split("-")
            if (parts.size != 3) return date
            val localDate = LocalDate.of(parts[0].toInt(), 1, 1)
                .plusMonths(parts[1].toInt() - 1L)
                .plusDays(parts[2].toInt() - 1L)
            localDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US))
        } catch (_: Exception) {
            date
        }
    }

    @Test
    fun `convertToISO8601 keeps the legacy output for edge cases in any locale and zone`() {
        // Literals captured from the java.time implementation on the parent commit.
        val expected = linkedMapOf(
            "2024-03-11" to "2024-03-11T00:00:00.000Z",
            "0000-01-01" to "0001-01-01T00:00:00.000Z",
            "999-1-1" to "0999-01-01T00:00:00.000Z",
            "12345-06-07" to "+12345-06-07T00:00:00.000Z",
            "2023-13-01" to "2024-01-01T00:00:00.000Z",
            "2023-00-10" to "2022-12-10T00:00:00.000Z",
            "2023-03-00" to "2023-02-28T00:00:00.000Z",
            "2023-01-400" to "2024-02-04T00:00:00.000Z",
            "2023-02-29" to "2023-03-01T00:00:00.000Z",
            "+2023-01-01" to "2023-01-01T00:00:00.000Z",
            " 2023-01-01" to " 2023-01-01",
            "1000000000-01-01" to "1000000000-01-01",
            "2023-99999999999-01" to "2023-99999999999-01",
            "2023--05-15" to "2023--05-15",
            "" to "",
        )
        for ((locale, zone) in pins) {
            pin(locale, zone)
            for ((input, output) in expected) {
                assertEquals("$input in $locale/$zone", output, DateTimeUtils.convertToISO8601(input))
                assertEquals("$input via TimeUtils", output, TimeUtils.convertToISO8601(input))
            }
        }
    }

    @Test
    fun `convertToISO8601 matches the java time implementation across a range of inputs`() {
        pin("ar", "Asia/Kathmandu")
        val years = listOf(-1, 0, 1, 99, 1582, 1900, 1999, 2000, 2023, 2024, 9999, 10000, 999_999_999)
        val offsets = listOf(-400, -13, -1, 0, 1, 2, 12, 13, 28, 29, 30, 31, 32, 60, 366, 5000)
        for (y in years) for (m in offsets) for (d in offsets) {
            val input = "$y-$m-$d"
            assertEquals(input, legacyConvertToISO8601(input), DateTimeUtils.convertToISO8601(input))
        }
    }

    @Test
    fun `plusDays matches Calendar day arithmetic across DST changes`() {
        pin("en-US", "America/New_York")
        // Literals captured from Calendar.add(DAY_OF_YEAR) on the parent commit.
        assertEquals(1707526800000L, DateTimeUtils.plusDays(1710115200000L, -30))
        assertEquals(1730653200000L, DateTimeUtils.plusDays(1730563200000L, 1))
        // 02:07 EST the day before spring-forward: Calendar lands on 01:07 EST, not 03:07 EDT.
        assertEquals(1710050820123L, DateTimeUtils.plusDays(1709968020123L, 1))

        for (zone in listOf("America/New_York", "Asia/Kathmandu", "Australia/Lord_Howe", "Europe/London", "America/Santiago", "UTC")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            // Every 20 minutes (plus an odd offset) through the 2024 DST transitions.
            for (start in listOf(1709251200000L, 1711800000000L, 1725148800000L, 1729900800000L)) {
                for (step in 0 until 72 * 45) {
                    val millis = start + step * 1_200_000L + 7 * 60_000L + 123
                    for (days in listOf(-30, 1)) {
                        val cal = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_YEAR, days) }
                        assertEquals("$zone $millis $days", cal.timeInMillis, DateTimeUtils.plusDays(millis, days))
                    }
                }
            }
        }
    }

    @Test
    fun `formatTimeOfDay matches the java time HH mm ss SSS formatter`() {
        pin("ar", "Asia/Kathmandu")
        assertEquals("05:45:01.234", DateTimeUtils.formatTimeOfDay(1710115201234L))
        for ((locale, zone) in pins) {
            pin(locale, zone)
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
            for (millis in listOf(0L, 1L, 999L, 1710115201234L, 1730610000007L, 4102444799999L)) {
                assertEquals(formatter.format(Instant.ofEpochMilli(millis)), DateTimeUtils.formatTimeOfDay(millis))
            }
        }
    }

    @Test
    fun `currentYear matches Calendar YEAR in the device zone`() {
        for ((locale, zone) in pins + ("en-US" to "Pacific/Kiritimati") + ("en-US" to "Pacific/Pago_Pago")) {
            pin(locale, zone)
            assertEquals(Calendar.getInstance().get(Calendar.YEAR), DateTimeUtils.currentYear())
        }
    }

    @Test
    fun `nowMillis tracks the system clock`() {
        val before = System.currentTimeMillis()
        val now = DateTimeUtils.nowMillis()
        val after = System.currentTimeMillis()
        assertTrue("$now not in $before..$after", now in before..after)
    }
}

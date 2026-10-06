package org.ole.planet.myplanet.utils

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AndroidDateFormatterTest {

    private val formatter = AndroidDateFormatter()
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

    @Test
    fun `formatLegacyTimestamp matches Date toString in US English and the device zone`() {
        pin("ar", "Asia/Kathmandu")
        assertEquals("Mon Mar 11 05:45:00 NPT 2024", formatter.formatLegacyTimestamp(MARCH_11_2024_UTC))
        pin("ne", "America/New_York")
        assertEquals("Sun Mar 10 20:00:00 EDT 2024", formatter.formatLegacyTimestamp(MARCH_11_2024_UTC))
        pin("en-US", "UTC")
        assertEquals("Mon Mar 11 00:00:00 UTC 2024", formatter.formatLegacyTimestamp(MARCH_11_2024_UTC))
    }

    @Test
    fun `monthDayYearTimeFormatter renders the member last-visit pattern`() {
        val rendered = AndroidDateFormatter.monthDayYearTimeFormatter(Locale.US, ZoneId.of("America/New_York"))
            .format(Instant.ofEpochMilli(MARCH_11_2024_UTC))
        assertEquals("March 10, 2024 08:00 PM", rendered)
    }

    @Test
    fun `formatMonthDayYearTime keeps the locale and zone it first saw`() {
        val first = formatter.formatMonthDayYearTime(MARCH_11_2024_UTC)
        pin(if (Locale.getDefault().language == "ar") "en-US" else "ar", "Pacific/Kiritimati")
        assertEquals(first, formatter.formatMonthDayYearTime(MARCH_11_2024_UTC))
    }

    @Test
    fun `format delegates to TimeUtils formatDate in the device zone and locale`() {
        for ((locale, zone) in listOf("en-US" to "UTC", "ar" to "Asia/Kathmandu", "ne" to "America/New_York")) {
            pin(locale, zone)
            assertEquals(TimeUtils.formatDate(MARCH_11_2024_UTC, "MMM dd, yyyy"), formatter.format(MARCH_11_2024_UTC, "MMM dd, yyyy"))
            assertEquals(TimeUtils.getFormattedDateWithTime(MARCH_11_2024_UTC), formatter.formatDateWithTime(MARCH_11_2024_UTC))
            assertEquals(TimeUtils.formatDateForCsv(MARCH_11_2024_UTC), formatter.formatForCsv(MARCH_11_2024_UTC))
        }
        pin("en-US", "America/New_York")
        assertEquals("Mar 10, 2024", formatter.format(MARCH_11_2024_UTC, "MMM dd, yyyy"))
        assertEquals("Sun 10, March 2024, 08:00 PM", formatter.formatDateWithTime(MARCH_11_2024_UTC))
        assertEquals("Sun Mar 10 2024 20:00:00 GMT-0400 (EDT)", formatter.formatForCsv(MARCH_11_2024_UTC))
        assertEquals("", formatter.format(MARCH_11_2024_UTC, "not a {pattern"))
    }

    @Test
    fun `convertDDMMYYYYToISO keeps TimeUtils leniency and fallbacks`() {
        for ((locale, zone) in listOf("en-US" to "UTC", "ar" to "Asia/Kathmandu", "ne" to "America/New_York")) {
            pin(locale, zone)
            assertEquals("2023-05-15T00:00:00.000Z", formatter.convertDDMMYYYYToISO("15-05-2023"))
            assertEquals("2023-02-28T00:00:00.000Z", formatter.convertDDMMYYYYToISO("31-02-2023"))
            assertEquals("5-5-2023", formatter.convertDDMMYYYYToISO("5-5-2023"))
            assertEquals("", formatter.convertDDMMYYYYToISO(null))
            assertEquals("garbage", formatter.convertDDMMYYYYToISO("garbage"))
        }
    }

    private companion object {
        const val MARCH_11_2024_UTC = 1710115200000L
    }
}

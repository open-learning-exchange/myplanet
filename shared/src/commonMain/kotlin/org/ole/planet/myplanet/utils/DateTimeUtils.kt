package org.ole.planet.myplanet.utils

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Platform-free date arithmetic and numeric/ISO formatting on epoch milliseconds. Anything that
 * needs month/day names or locale-specific output goes through [DateFormatter] instead.
 */
object DateTimeUtils {

    /** Current wall-clock time in epoch milliseconds. */
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * [epochMillis] moved by [days] calendar days in [timeZone], keeping the local time of day.
     * Mirrors `GregorianCalendar.add(DAY_OF_YEAR, days)` exactly, including how it resolves a
     * local time that falls in a DST gap (it keeps the pre-transition offset, which can land an
     * hour earlier than java.time / kotlinx-datetime would).
     */
    fun plusDays(epochMillis: Long, days: Int, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val oldOffset = offsetMillis(epochMillis, timeZone)
        val targetLocalDay = (epochMillis + oldOffset).floorDiv(MILLIS_PER_DAY) + days
        val moved = epochMillis + days * MILLIS_PER_DAY
        val offsetChange = oldOffset - offsetMillis(moved, timeZone)
        if (offsetChange == 0L) return moved
        val adjusted = moved + offsetChange
        val adjustedLocalDay = (adjusted + offsetMillis(adjusted, timeZone)).floorDiv(MILLIS_PER_DAY)
        return if (adjustedLocalDay == targetLocalDay) adjusted else moved
    }

    private fun offsetMillis(epochMillis: Long, timeZone: TimeZone): Long =
        timeZone.offsetAt(Instant.fromEpochMilliseconds(epochMillis)).totalSeconds * 1000L

    /** Today's calendar year in [timeZone]. */
    fun currentYear(timeZone: TimeZone = TimeZone.currentSystemDefault()): Int =
        Clock.System.todayIn(timeZone).year

    /** "HH:mm:ss.SSS" of [epochMillis] in [timeZone]. */
    fun formatTimeOfDay(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).time
        return "${pad(time.hour, 2)}:${pad(time.minute, 2)}:${pad(time.second, 2)}.${pad(time.nanosecond / 1_000_000, 3)}"
    }

    /**
     * Turns "year-month-day" into "yyyy-MM-dd'T'00:00:00.000'Z'". Month and day overflow roll into
     * the next month / year (e.g. "2023-02-29" becomes March 1); any unparseable input is returned
     * unchanged.
     */
    fun convertToISO8601(date: String): String {
        return try {
            val parts = date.split("-")
            if (parts.size != 3) return date
            val localDate = LocalDate(parts[0].toInt(), 1, 1)
                .plus(parts[1].toInt() - 1, DateTimeUnit.MONTH)
                .plus(parts[2].toInt() - 1, DateTimeUnit.DAY)
            "${formatYearOfEra(localDate.year)}-${pad(localDate.month.number, 2)}-${pad(localDate.day, 2)}T00:00:00.000Z"
        } catch (_: Exception) {
            date
        }
    }

    // Matches java.time's "yyyy": year-of-era (1 BCE for year 0), zero-padded to 4 digits,
    // with a leading '+' once it needs more than 4.
    private fun formatYearOfEra(year: Int): String {
        val yearOfEra = if (year <= 0) 1L - year else year.toLong()
        val digits = yearOfEra.toString()
        return if (digits.length > 4) "+$digits" else digits.padStart(4, '0')
    }

    private const val MILLIS_PER_DAY = 86_400_000L

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')
}

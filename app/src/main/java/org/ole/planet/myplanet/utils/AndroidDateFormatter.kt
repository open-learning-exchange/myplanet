package org.ole.planet.myplanet.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** [DateFormatter] over [TimeUtils] and the java.time / java.util formatters. */
@Singleton
class AndroidDateFormatter @Inject constructor() : DateFormatter {
    override fun format(epochMillis: Long, pattern: String): String = TimeUtils.formatDate(epochMillis, pattern)

    override fun formatDateWithTime(epochMillis: Long): String = TimeUtils.getFormattedDateWithTime(epochMillis)

    override fun formatMonthDayYearTime(epochMillis: Long): String =
        MONTH_DAY_YEAR_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

    override fun formatForCsv(epochMillis: Long): String = TimeUtils.formatDateForCsv(epochMillis)

    override fun formatLegacyTimestamp(epochMillis: Long): String = Date(epochMillis).toString()

    override fun convertDDMMYYYYToISO(dateString: String?): String = TimeUtils.convertDDMMYYYYToISO(dateString)

    internal companion object {
        // Captures the locale and zone once, as the formatter it replaces did.
        private val MONTH_DAY_YEAR_TIME_FORMATTER: DateTimeFormatter by lazy {
            monthDayYearTimeFormatter(Locale.getDefault(), ZoneId.systemDefault())
        }

        fun monthDayYearTimeFormatter(locale: Locale, zone: ZoneId): DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a", locale).withZone(zone)
    }
}

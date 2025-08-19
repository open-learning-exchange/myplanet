package org.ole.planet.myplanet.utilities

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val defaultLocale: Locale
        get() = Locale.getDefault()

    private val utcZone: ZoneId = ZoneId.of("UTC")

    private const val DEFAULT_DATE_PATTERN = "EEE dd, MMMM yyyy"
    private const val DEFAULT_DATE_WITH_DAY_PATTERN = "EEEE, MMM dd, yyyy"
    private const val DEFAULT_DATE_TIME_PATTERN = "EEE dd, MMMM yyyy , hh:mm a"
    private const val TZ_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun format(dateMillis: Long, pattern: String, zone: ZoneId = ZoneId.systemDefault()): String =
        try {
            val formatter = DateTimeFormatter.ofPattern(pattern, defaultLocale).withZone(zone)
            formatter.format(Instant.ofEpochMilli(dateMillis))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

    fun getFormattedDate(date: Long?): String {
        val result = format(date ?: Instant.now().toEpochMilli(), DEFAULT_DATE_WITH_DAY_PATTERN, utcZone)
        return if (result.isNotEmpty()) result else "N/A"
    }

    fun getFormattedDateWithTime(date: Long): String {
        val result = format(date, DEFAULT_DATE_TIME_PATTERN)
        return if (result.isNotEmpty()) result else "N/A"
    }

    fun formatDateTZ(data: Long): String = format(data, TZ_DATE_TIME_PATTERN)

    fun getAge(date: String): Int =
        try {
            val cleaned = date.replace("T", " ").replace(".000Z", "")
            val dob =
                try {
                    LocalDateTime
                        .parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .toLocalDate()
                } catch (e: Exception) {
                    LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }
            val today = LocalDate.now()
            Period.between(dob, today).years
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }

    fun getFormattedDate(
        stringDate: String?,
        pattern: String?,
    ): String {
        return try {
            if (stringDate.isNullOrBlank() || pattern.isNullOrBlank()) return "N/A"
            val formatter = DateTimeFormatter.ofPattern(pattern, defaultLocale).withZone(utcZone)
            val instant = LocalDate.parse(stringDate, formatter).atStartOfDay(utcZone).toInstant()
            val result = format(instant.toEpochMilli(), DEFAULT_DATE_WITH_DAY_PATTERN, utcZone)
            if (result.isNotEmpty()) result else "N/A"
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    fun formatDate(date: Long): String = format(date, DEFAULT_DATE_PATTERN)

    fun formatDate(
        date: Long,
        format: String?,
    ): String =
        if (format.isNullOrBlank()) {
            formatDate(date)
        } else {
            format(date, format)
        }

    fun parseDate(dateString: String): Long? =
        try {
            val formatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN, defaultLocale)
            val localDate = LocalDate.parse(dateString, formatter)
            localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}

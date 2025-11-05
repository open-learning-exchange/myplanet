package org.ole.planet.myplanet.utilities

import android.text.format.DateUtils
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

    private val defaultDateFormatter by lazy {
        DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy", defaultLocale).withZone(utcZone)
    }

    private val dateTimeFormatter by lazy {
        DateTimeFormatter
            .ofPattern("EEE dd, MMMM yyyy , hh:mm a", defaultLocale)
            .withZone(ZoneId.systemDefault())
    }

    private val tzFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }

    private val dateOnlyFormatter by lazy {
        DateTimeFormatter.ofPattern("EEE dd, MMMM yyyy", defaultLocale).withZone(ZoneId.systemDefault())
    }

    private val fallbackDateFormatter by lazy {
        DateTimeFormatter.ofPattern("dd, MMMM yyyy", defaultLocale).withZone(ZoneId.systemDefault())
    }

    fun getRelativeTime(timestamp: Long): String {
        val timeNow = System.currentTimeMillis()
        return if (timestamp < timeNow) {
            DateUtils.getRelativeTimeSpanString(timestamp, timeNow, 0).toString()
        } else "Just now"
    }

    fun getFormattedDate(date: Long?): String =
        try {
            val instant = date?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
            defaultDateFormatter.format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }

    fun getFormattedDateWithTime(date: Long): String =
        try {
            val instant = Instant.ofEpochMilli(date)
            dateTimeFormatter.format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }

    fun formatDateTZ(data: Long): String =
        try {
            val instant = Instant.ofEpochMilli(data)
            tzFormatter.format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

    fun getAge(date: String): Int {
        return try {
            if (date.isBlank()) return 0
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
    }

    fun getFormattedDate(
        stringDate: String?,
        pattern: String?,
    ): String {
        return try {
            if (stringDate.isNullOrBlank() || pattern.isNullOrBlank()) return "N/A"
            val formatter = DateTimeFormatter.ofPattern(pattern, defaultLocale).withZone(utcZone)
            val instant = if (stringDate.contains("T")) {
                Instant.from(formatter.parse(stringDate))
            } else {
                val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", defaultLocale)
                LocalDate.parse(stringDate, dateOnlyFormatter).atStartOfDay(utcZone).toInstant()
            }
            getFormattedDate(instant.toEpochMilli())
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    fun formatDate(date: Long): String =
        try {
            dateOnlyFormatter.format(Instant.ofEpochMilli(date))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

    fun formatDate(
        date: Long,
        format: String?,
    ): String =
        try {
            val formatter = DateTimeFormatter.ofPattern(format ?: "", defaultLocale).withZone(ZoneId.systemDefault())
            formatter.format(Instant.ofEpochMilli(date))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

    fun parseDate(dateString: String): Long? =
        try {
            val localDate = runCatching {
                LocalDate.parse(dateString, dateOnlyFormatter)
            }.recoverCatching {
                LocalDate.parse(dateString, fallbackDateFormatter)
            }.getOrThrow()
            localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    fun parseInstantFromString(dateString: String): Instant? =
        try {
            if (dateString.contains("T")) {
                Instant.parse(dateString)
            } else {
                Instant.parse("${dateString}T00:00:00.000Z")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    fun convertToISO8601(date: String): String {
        return try {
            val calendar = java.util.Calendar.getInstance()
            val parts = date.split("-")
            if (parts.size == 3) {
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                String.format(
                    Locale.US,
                    "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH) + 1,
                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                    calendar.get(java.util.Calendar.SECOND),
                    calendar.get(java.util.Calendar.MILLISECOND)
                )
            } else {
                date
            }
        } catch (_: Exception) {
            date
        }
    }
}

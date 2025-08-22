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
            getFormattedDate(instant.toEpochMilli())
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    fun currentDate(): String =
        try {
            dateOnlyFormatter.format(Instant.now())
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
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
            val localDate = try {
                LocalDate.parse(dateString, dateOnlyFormatter)
            } catch (_: Exception) {
                val fallbackFormatter = DateTimeFormatter.ofPattern("dd, MMMM yyyy", defaultLocale)
                LocalDate.parse(dateString, fallbackFormatter)
            }
            localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}

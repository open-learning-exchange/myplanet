package org.ole.planet.myplanet.utils

import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TimeUtils {
    const val DATE_FORMAT = "dd MMM yyyy"

    private val defaultLocale: Locale
        get() = Locale.getDefault()

    private val utcZone: ZoneId = ZoneId.of("UTC")

    private data class FormatterKey(
        val pattern: String,
        val zone: ZoneId?,
        val locale: Locale?
    )

    private val formatters = ConcurrentHashMap<FormatterKey, DateTimeFormatter>()

    private fun formatterFor(
        pattern: String,
        zone: ZoneId? = null,
        locale: Locale? = defaultLocale
    ): DateTimeFormatter {
        val key = FormatterKey(pattern, zone, locale)
        return formatters.getOrPut(key) {
            val formatter = if (locale != null) {
                DateTimeFormatter.ofPattern(pattern, locale)
            } else {
                DateTimeFormatter.ofPattern(pattern)
            }
            if (zone != null) formatter.withZone(zone) else formatter
        }
    }

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

    private val csvDateFormatter by lazy {
        DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)", Locale.US).withZone(ZoneId.systemDefault())
    }

    private val iso8601Formatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }

    private fun formatInstant(instant: Instant, fallback: String, formatter: () -> DateTimeFormatter): String =
        try {
            formatter().format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            fallback
        }

    fun getRelativeTime(timestamp: Long, timeProvider: TimeProvider? = null): String {
        val timeNow = timeProvider?.now() ?: System.currentTimeMillis()
        return if (timestamp < timeNow) {
            DateUtils.getRelativeTimeSpanString(timestamp, timeNow, 0).toString()
        } else "Just now"
    }

    fun getFormattedDate(date: Long?): String {
        val instant = date?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        return formatInstant(instant, "N/A") { defaultDateFormatter }
    }

    fun getFormattedShortDate(date: Long): String =
        formatInstant(Instant.ofEpochMilli(date), "N/A") { formatterFor(DATE_FORMAT, ZoneId.systemDefault()) }

    fun getFormattedDateWithTime(date: Long): String =
        formatInstant(Instant.ofEpochMilli(date), "N/A") { dateTimeFormatter }

    fun formatDateTZ(data: Long): String =
        formatInstant(Instant.ofEpochMilli(data), "") { tzFormatter }

    fun formatDateForCsv(date: Long): String =
        formatInstant(Instant.ofEpochMilli(date), "") { csvDateFormatter }

    fun getAge(date: String): Int {
        return try {
            if (date.isBlank()) return 0
            val cleaned = date.replace("T", " ").replace(".000Z", "")
            val dob =
                try {
                    LocalDateTime
                        .parse(cleaned, formatterFor("yyyy-MM-dd HH:mm:ss", locale = null))
                        .toLocalDate()
                } catch (e: Exception) {
                    LocalDate.parse(cleaned, formatterFor("yyyy-MM-dd", locale = null))
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
            val formatter = formatterFor(pattern, utcZone)
            val instant = if (stringDate.contains("T")) {
                Instant.from(formatter.parse(stringDate))
            } else {
                val dateOnlyFormatter = formatterFor("yyyy-MM-dd")
                LocalDate.parse(stringDate, dateOnlyFormatter).atStartOfDay(utcZone).toInstant()
            }
            getFormattedDate(instant.toEpochMilli())
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    fun formatDate(date: Long): String =
        formatInstant(Instant.ofEpochMilli(date), "") { dateOnlyFormatter }

    fun formatDate(
        date: Long,
        format: String?,
    ): String =
        formatInstant(Instant.ofEpochMilli(date), "") { formatterFor(format ?: "", ZoneId.systemDefault()) }

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
            val parts = date.split("-")
            if (parts.size != 3) return date
            val localDate = LocalDate.of(parts[0].toInt(), 1, 1)
                .plusMonths(parts[1].toInt() - 1L)
                .plusDays(parts[2].toInt() - 1L)
            localDate.atStartOfDay().format(iso8601Formatter)
        } catch (_: Exception) {
            date
        }
    }

    fun formatDateToDDMMYYYY(dateString: String?): String {
        return try {
            if (dateString.isNullOrBlank()) return ""

            val localDate = if (dateString.contains("T")) {
                val cleaned = dateString.replace("T", " ").replace(".000Z", "")
                LocalDateTime.parse(cleaned, formatterFor("yyyy-MM-dd HH:mm:ss", locale = null)).toLocalDate()
            } else {
                LocalDate.parse(dateString, formatterFor("yyyy-MM-dd", locale = null))
            }

            val formatter = formatterFor("dd-MM-yyyy")
            localDate.format(formatter)
        } catch (e: Exception) {
            e.printStackTrace()
            dateString ?: ""
        }
    }

    fun convertDDMMYYYYToISO(dateString: String?): String {
        return try {
            if (dateString.isNullOrBlank()) return ""

            val localDate = LocalDate.parse(dateString, formatterFor("dd-MM-yyyy"))
            val isoDate = localDate.format(formatterFor("yyyy-MM-dd", locale = null))
            convertToISO8601(isoDate)
        } catch (e: Exception) {
            e.printStackTrace()
            dateString ?: ""
        }
    }
}

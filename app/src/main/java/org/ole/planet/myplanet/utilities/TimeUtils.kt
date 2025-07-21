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
        DateTimeFormatter.ofPattern("EEE dd, MMMM yyyy , hh:mm a", defaultLocale)
            .withZone(ZoneId.systemDefault())
    }

    private val tzFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }

    private val dateOnlyFormatter by lazy {
        DateTimeFormatter.ofPattern("EEE dd, MMMM yyyy", defaultLocale).withZone(ZoneId.systemDefault())
    }

    @JvmStatic
    fun getFormatedDate(date: Long?): String {
        return try {
            val instant = date?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
            defaultDateFormatter.format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun getFormatedDateWithTime(date: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(date)
            dateTimeFormatter.format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun formatDateTZ(data: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(data)
            tzFormatter.format(instant)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun getAge(date: String): Int {
        return try {
            val cleaned = date.replace("T", " ").replace(".000Z", "")
            val dob = try {
                LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
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

    @JvmStatic
    fun getFormatedDate(stringDate: String?, pattern: String?): String {
        return try {
            if (stringDate.isNullOrBlank() || pattern.isNullOrBlank()) return "N/A"
            val formatter = DateTimeFormatter.ofPattern(pattern, defaultLocale).withZone(utcZone)
            val instant = LocalDate.parse(stringDate, formatter).atStartOfDay(utcZone).toInstant()
            getFormatedDate(instant.toEpochMilli())
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun currentDate(): String {
        return try {
            dateOnlyFormatter.format(Instant.now())
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun formatDate(date: Long): String {
        return try {
            dateOnlyFormatter.format(Instant.ofEpochMilli(date))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun formatDate(date: Long, format: String?): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern(format ?: "", defaultLocale).withZone(ZoneId.systemDefault())
            formatter.format(Instant.ofEpochMilli(date))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}

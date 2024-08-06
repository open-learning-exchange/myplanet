package org.ole.planet.myplanet.utilities

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    private val defaultLocale: Locale
        get() = Locale.getDefault()

    @JvmStatic
    fun getFormatedDate(date: Long?, locale: Locale = defaultLocale): String {
        return try {
            val d = date?.let { Date(it) } ?: Date()
            val f = SimpleDateFormat("EEEE, MMM dd, yyyy", locale)
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.format(d)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun getFormatedDateWithTime(date: Long, locale: Locale = defaultLocale): String {
        val d = Date(date)
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy , hh:mm aa", locale)
        return dateFormat.format(d)
    }

    @JvmStatic
    fun formatDateTZ(data: Long, locale: Locale = defaultLocale): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
        return dateFormat.format(data)
    }

    @JvmStatic
    fun getAge(date: String, locale: Locale = defaultLocale): Int {
        val dateFormat1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
        val dateFormat2 = SimpleDateFormat("yyyy-MM-dd", locale)
        val dob = Calendar.getInstance()
        val today = Calendar.getInstance()
        try {
            if (date.contains("T")) {
                val dt = dateFormat1.parse(date.replace("T", " ").replace(".000Z", ""))
                if (dt != null) {
                    dob.time = dt
                }
            } else {
                val dt2 = dateFormat2.parse(date)
                if (dt2 != null) {
                    dob.time = dt2
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        var age = today[Calendar.YEAR] - dob[Calendar.YEAR]
        if (today[Calendar.DAY_OF_YEAR] < dob[Calendar.DAY_OF_YEAR]) {
            age--
        }
        return age
    }

    @JvmStatic
    fun getFormatedDate(stringDate: String?, pattern: String?, locale: Locale = defaultLocale): String {
        return try {
            val sf = SimpleDateFormat(pattern, locale)
            sf.timeZone = TimeZone.getTimeZone("UTC")
            val date = stringDate?.let { sf.parse(it) }
            getFormatedDate(date?.time, locale)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun currentDate(locale: Locale = defaultLocale): String {
        val c = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy", locale)
        return dateFormat.format(c.time)
    }

    fun currentDateLong(locale: Locale = defaultLocale): Long {
        val c = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy", locale)
        return try {
            c.time = dateFormat.parse(currentDate(locale))!!
            c.timeInMillis
        } catch (e: ParseException) {
            e.printStackTrace()
            0
        }
    }

    @JvmStatic
    fun formatDate(date: Long, locale: Locale = defaultLocale): String {
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy", locale)
        return dateFormat.format(date)
    }

    @JvmStatic
    fun formatDate(date: Long, format: String?, locale: Locale = defaultLocale): String {
        val dateFormat = SimpleDateFormat(format, locale)
        return dateFormat.format(date)
    }

    fun dateToLong(date: String?, locale: Locale = defaultLocale): Any {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
            date?.let { dateFormat.parse(it) }?.time ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}

package org.ole.planet.myplanet.utilities

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    private val defaultLocale: Locale
        get() = Locale.getDefault()

    fun getFormatedDate(date: Long?): String {
        try {
            val d = date?.let { Date(it) } ?: Date()
            val f = SimpleDateFormat("EEEE, MMM dd, yyyy", defaultLocale)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(d)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "N/A"
    }

    fun getFormatedDateWithTime(date: Long): String {
        val d = Date(date)
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy , hh:mm aa")
        return dateFormat.format(d)
    }

    fun formatDateTZ(data: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return dateFormat.format(data)
    }

    fun getAge(date: String): Int {
        val dateFormatTimeIncluded = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val dob = Calendar.getInstance()
        val today = Calendar.getInstance()
        try {
            if (date.contains("T")) {
                val dt = dateFormatTimeIncluded.parse(date.replace("T".toRegex(), " ").replace(".000Z".toRegex(), ""))
                if (dt != null) {
                    dob.time = dt
                }
            } else {
                val dt2 = dateFormat.parse(date)
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

    fun getFormatedDate(stringDate: String?, pattern: String?): String {
        return try {
            val sf = SimpleDateFormat(pattern, defaultLocale)
            sf.timeZone = TimeZone.getTimeZone("UTC")
            val date = stringDate?.let { sf.parse(it) }
            getFormatedDate(date?.time)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    fun currentDate(): String {
        val c = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy")
        return dateFormat.format(c.time)
    }

    fun formatDate(date: Long): String {
        val dateFormat = SimpleDateFormat("EEE dd, MMMM yyyy")
        return dateFormat.format(date)
    }

    fun formatDate(date: Long, format: String?): String {
        val dateFormat = SimpleDateFormat(format)
        return dateFormat.format(date)
    }
}

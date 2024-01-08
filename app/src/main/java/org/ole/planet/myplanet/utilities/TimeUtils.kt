package org.ole.planet.myplanet.utilities

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    @JvmStatic
    fun getFormatedDate(date: Long): String {
        try {
            val d = Date(date)
            val f = SimpleDateFormat("EEEE, MMM dd, yyyy")
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(d)
        } catch (e: Exception) {
            Utilities.log("Exception : " + e.message)
            e.printStackTrace()
        }
        return "N/A"
    }

    @JvmStatic
    fun getFormatedDateWithTime(date: Long): String {
        val d = Date(date)
        val dateformat = SimpleDateFormat("EEE dd, MMMM yyyy , hh:mm aa")
        return dateformat.format(d)
    }

    @JvmStatic
    fun formatDateTZ(data: Long): String {
        val dateformat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return dateformat.format(data)
    }

    @JvmStatic
    fun getAge(date: String): Int {
        val dateformat1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val dateformat2 = SimpleDateFormat("yyyy-MM-dd")
        val dob = Calendar.getInstance()
        val today = Calendar.getInstance()
        try {
            if (date.contains("T")) {
                val dt = dateformat1.parse(date.replace("T".toRegex(), " ").replace(".000Z".toRegex(), ""))
                dob.time = dt!!
            } else {
                val dt2 = dateformat2.parse(date)
                dob.time = dt2!!
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
    fun getFormatedDate(stringDate: String?, pattern: String?): String {
        return try {
            val sf = SimpleDateFormat(pattern, Locale.getDefault())
            sf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sf.parse(stringDate!!)
            getFormatedDate(date!!.time)
        } catch (e: Exception) {
            e.printStackTrace()
            "N/A"
        }
    }

    @JvmStatic
    fun currentDate(): String {
        val c = Calendar.getInstance()
        val dateformat = SimpleDateFormat("EEE dd, MMMM yyyy")
        return dateformat.format(c.time)
    }

    fun currentDateLong(): Long {
        val c = Calendar.getInstance()
        val dateformat = SimpleDateFormat("EEE dd, MMMM yyyy")
        try {
            c.time = dateformat.parse(currentDate())!!
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return c.timeInMillis
    }

    @JvmStatic
    fun formatDate(date: Long): String {
        val dateformat = SimpleDateFormat("EEE dd, MMMM yyyy")
        return dateformat.format(date)
    }

    @JvmStatic
    fun formatDate(date: Long, format: String?): String {
        val dateformat = SimpleDateFormat(format)
        return dateformat.format(date)
    }

    fun dateToLong(date: String?): Long {
        try {
            val dateformat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            return dateformat.parse(date!!)!!.time
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }
}

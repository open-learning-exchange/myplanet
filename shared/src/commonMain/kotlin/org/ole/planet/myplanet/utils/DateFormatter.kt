package org.ole.planet.myplanet.utils

/**
 * Locale- and zone-sensitive date formatting and parsing that needs platform formatters (month /
 * day names, zone names, lenient pattern parsing). Pure arithmetic and ISO / numeric formats live
 * in [DateTimeUtils]. The Android implementation is [AndroidDateFormatter].
 */
interface DateFormatter {
    /** [epochMillis] rendered with [pattern] in the device zone and locale; "" on failure. */
    fun format(epochMillis: Long, pattern: String): String

    /** "EEE dd, MMMM yyyy, hh:mm a" in the device zone and locale; "N/A" on failure. */
    fun formatDateWithTime(epochMillis: Long): String

    /** "MMMM dd, yyyy hh:mm a" in the device zone and locale in effect when first used. */
    fun formatMonthDayYearTime(epochMillis: Long): String

    /** "EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)" in the device zone, US English; "" on failure. */
    fun formatForCsv(epochMillis: Long): String

    /** The legacy timestamp string "EEE MMM dd HH:mm:ss zzz yyyy" (US English, device zone). */
    fun formatLegacyTimestamp(epochMillis: Long): String

    /** Parses "dd-MM-yyyy" into an ISO-8601 midnight-UTC string; returns [dateString] (or "") when unparseable. */
    fun convertDDMMYYYYToISO(dateString: String?): String
}

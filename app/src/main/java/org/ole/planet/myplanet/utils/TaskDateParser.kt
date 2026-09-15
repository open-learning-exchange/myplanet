package org.ole.planet.myplanet.utils

import java.util.regex.Pattern

object TaskDateParser {
    private val TASK_DATE_PATTERN = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")

    internal fun parseTaskDate(message: String): Pair<String, String>? {
        val matcher = TASK_DATE_PATTERN.matcher(message)
        return if (matcher.find()) {
            val taskTitle = message.substring(0, matcher.start()).trim()
            val dateValue = message.substring(matcher.start()).trim()
            Pair(taskTitle, dateValue)
        } else {
            null
        }
    }
}

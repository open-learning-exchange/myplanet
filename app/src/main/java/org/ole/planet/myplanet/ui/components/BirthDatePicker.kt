package org.ole.planet.myplanet.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogBirthDatePickerBinding

// A calendar that opens on today makes every learner page back through decades to reach their
// birth year. Three wheels (year, month, day) let them fling straight to it instead. The month
// and day wheels follow the year and month picked, nothing after today can be chosen, and a
// date the form already holds is where the wheels start.
object BirthDatePicker {
    private const val EARLIEST_YEAR = 1900
    private val DAY_FIRST: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val YEAR_FIRST = Regex("""\d{4}-\d{2}-\d{2}""")
    private val DAY_FIRST_SHAPE = Regex("""\d{2}-\d{2}-\d{4}""")

    // month is 0-based, matching DatePickerDialog, so callers keep their formatting
    fun show(context: Context, current: String?, onPicked: (year: Int, month: Int, dayOfMonth: Int) -> Unit) {
        val today = LocalDate.now()
        val start = parse(current)?.takeUnless { it.isAfter(today) } ?: today
        val monthNames = (1..12).map { Month.of(it).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()) }
        val binding = DialogBirthDatePickerBinding.inflate(LayoutInflater.from(context))
        val year = binding.npYear.apply {
            minValue = EARLIEST_YEAR
            maxValue = today.year
            value = start.year
            wrapSelectorWheel = false
        }
        val month = binding.npMonth.apply { minValue = 1 }
        val day = binding.npDay.apply { minValue = 1 }

        fun limitDays() = day.limitTo(lastDay(year.value, month.value, today), null)
        fun limitMonths() {
            month.limitTo(lastMonth(year.value, today), monthNames)
            limitDays()
        }
        limitMonths()
        month.value = start.monthValue
        limitDays()
        day.value = start.dayOfMonth

        year.setOnValueChangedListener { _, _, _ -> limitMonths() }
        month.setOnValueChangedListener { _, _, _ -> limitDays() }

        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.birth_date)
            .setView(binding.root)
            .setPositiveButton(R.string.set) { _, _ -> onPicked(year.value, month.value - 1, day.value) }
            .setNegativeButton(R.string.txt_cancel, null)
            .show()
    }

    // NumberPicker throws if displayedValues is shorter than the range, so clear the labels
    // before changing the range and keep the current value inside it
    private fun NumberPicker.limitTo(max: Int, labels: List<String>?) {
        val keep = value.coerceIn(1, max)
        displayedValues = null
        maxValue = max
        labels?.let { displayedValues = it.take(max).toTypedArray() }
        wrapSelectorWheel = false
        value = keep
    }

    internal fun lastMonth(year: Int, today: LocalDate): Int =
        if (year == today.year) today.monthValue else 12

    internal fun lastDay(year: Int, month: Int, today: LocalDate): Int =
        if (year == today.year && month == today.monthValue) today.dayOfMonth
        else YearMonth.of(year, month).lengthOfMonth()

    // Forms keep the date as "yyyy-MM-dd", "dd-MM-yyyy" or a stored ISO instant
    // ("2000-11-15T00:00:00.000Z"); anything else (a placeholder label) means no date yet
    internal fun parse(text: String?): LocalDate? {
        val value = text?.trim().orEmpty()
        return runCatching {
            when {
                value.contains('T') -> Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDate()
                YEAR_FIRST.matches(value) -> LocalDate.parse(value)
                DAY_FIRST_SHAPE.matches(value) -> LocalDate.parse(value, DAY_FIRST)
                else -> null
            }
        }.getOrNull()
    }
}

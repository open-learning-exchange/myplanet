package org.ole.planet.myplanet.ui.teams.tasks

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.utils.TimeUtils

@HiltViewModel
class TeamsTasksViewModel @Inject constructor() : ViewModel() {

    private val _deadline = MutableStateFlow<Calendar?>(null)
    val deadline: StateFlow<Calendar?> = _deadline.asStateFlow()

    fun setDeadlineDate(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val newDeadline = Calendar.getInstance()
        newDeadline.set(Calendar.YEAR, year)
        newDeadline.set(Calendar.MONTH, monthOfYear)
        newDeadline.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        _deadline.value = newDeadline
    }

    fun setDeadlineTime(hourOfDay: Int, minute: Int) {
        val newDeadline = (_deadline.value?.clone() as? Calendar) ?: Calendar.getInstance()
        newDeadline.set(Calendar.HOUR_OF_DAY, hourOfDay)
        newDeadline.set(Calendar.MINUTE, minute)
        _deadline.value = newDeadline
    }

    fun setDeadline(dateLong: Long) {
        val newDeadline = Calendar.getInstance()
        newDeadline.time = Date(dateLong)
        _deadline.value = newDeadline
    }

    fun getFormattedDeadlineDate(): String {
        val currentDeadline = _deadline.value ?: Calendar.getInstance()
        return TimeUtils.formatDateTZ(currentDeadline.timeInMillis)
    }

    fun getFormattedDeadlineWithTime(): String {
        val currentDeadline = _deadline.value ?: Calendar.getInstance()
        return TimeUtils.getFormattedDateWithTime(currentDeadline.timeInMillis)
    }

    fun getDeadlineMillis(): Long {
        return (_deadline.value ?: Calendar.getInstance()).timeInMillis
    }

    fun clearDeadline() {
        _deadline.value = null
    }

    fun getDeadlineCalendar(): Calendar {
        return _deadline.value ?: Calendar.getInstance()
    }
}

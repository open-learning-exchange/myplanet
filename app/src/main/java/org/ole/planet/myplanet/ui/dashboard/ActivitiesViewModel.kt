package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ActivitiesViewModel @Inject constructor() : ViewModel() {

    private val _dateRange = MutableStateFlow(calculateDateRange())
    val dateRange: StateFlow<Pair<Long, Long>> = _dateRange.asStateFlow()

    private fun calculateDateRange(): Pair<Long, Long> {
        val endMillis = Calendar.getInstance().timeInMillis
        val startMillis = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis
        return Pair(startMillis, endMillis)
    }
}

package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val endMillis = Calendar.getInstance().timeInMillis
    private val startMillis = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis

    val offlineLogins: StateFlow<List<OfflineActivity>> = flow {
        val userName = userRepository.getUserModel()?.name ?: return@flow
        emitAll(activitiesRepository.getOfflineLogins(userName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyLoginCounts: StateFlow<Map<Int, Int>> = offlineLogins
        .map { computeMonthlyCounts(it, startMillis, endMillis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    internal suspend fun computeMonthlyCounts(
        logins: List<OfflineActivity>,
        startMillis: Long,
        endMillis: Long
    ): Map<Int, Int> = withContext(dispatcherProvider.default) {
        val calendar = Calendar.getInstance()
        logins.fold(mutableMapOf<Int, Int>()) { acc, activity ->
            val loginTime = activity.loginTime
            if (loginTime != null && loginTime in startMillis..endMillis) {
                calendar.timeInMillis = loginTime
                val month = calendar.get(Calendar.MONTH)
                acc[month] = (acc[month] ?: 0) + 1
            }
            acc
        }.toSortedMap()
    }
}

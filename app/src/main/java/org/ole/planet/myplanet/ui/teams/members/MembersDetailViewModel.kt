package org.ole.planet.myplanet.ui.teams.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ActivitiesRepository

data class MemberVisitStatsUiState(
    val numberOfVisits: String = "0",
    val lastLogin: String = "No logout record found"
)

@HiltViewModel
class MembersDetailViewModel @Inject constructor(
    private val activitiesRepository: ActivitiesRepository
) : ViewModel() {

    private val _visitStats = MutableStateFlow<Map<String, MemberVisitStatsUiState>>(emptyMap())
    val visitStats: StateFlow<Map<String, MemberVisitStatsUiState>> = _visitStats.asStateFlow()

    private val dateFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    fun loadMemberVisitStats(memberId: String?, username: String?) {
        if (memberId.isNullOrEmpty()) return
        viewModelScope.launch {
            val stats = activitiesRepository.getMemberVisitStats(memberId, username)
            val formattedLastLogin = stats.lastVisit?.let {
                dateFormatter.format(Instant.ofEpochMilli(it))
            } ?: "No logout record found"
            val uiState = MemberVisitStatsUiState(
                numberOfVisits = stats.offlineVisitCount.toString(),
                lastLogin = formattedLastLogin
            )
            _visitStats.value = _visitStats.value + (memberId to uiState)
        }
    }
}

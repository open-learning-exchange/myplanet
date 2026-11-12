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

/**
 * Visit stats for the member currently on screen. A `null` field means the value could not be
 * resolved (no member id to count visits for, or no recorded logout), so the row is hidden rather
 * than filled with a placeholder.
 */
data class MemberVisitStatsUiState(
    val numberOfVisits: String?,
    val lastLogin: String?
)

@HiltViewModel
class MembersDetailViewModel @Inject constructor(
    private val activitiesRepository: ActivitiesRepository
) : ViewModel() {

    private val _visitStats = MutableStateFlow<MemberVisitStatsUiState?>(null)
    val visitStats: StateFlow<MemberVisitStatsUiState?> = _visitStats.asStateFlow()

    private var loadRequested = false

    private val dateFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    fun loadMemberVisitStats(memberId: String?, username: String?) {
        if (memberId.isNullOrEmpty() && username.isNullOrEmpty()) return
        if (loadRequested) return
        loadRequested = true
        viewModelScope.launch {
            val stats = activitiesRepository.getMemberVisitStats(memberId, username)
            _visitStats.value = MemberVisitStatsUiState(
                numberOfVisits = if (memberId.isNullOrEmpty()) null else stats.offlineVisitCount.toString(),
                lastLogin = stats.lastVisit?.let { dateFormatter.format(Instant.ofEpochMilli(it)) }
            )
        }
    }
}

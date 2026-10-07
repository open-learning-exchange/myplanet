package org.ole.planet.myplanet.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val eventsRepository: EventsRepository,
    private val teamsRepository: TeamsRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _meetups = MutableStateFlow<List<Meetup>>(emptyList())
    val meetups: StateFlow<List<Meetup>> = _meetups.asStateFlow()

    private val _meetupsByDate = MutableStateFlow<Map<LocalDate, List<Meetup>>>(emptyMap())
    val meetupsByDate: StateFlow<Map<LocalDate, List<Meetup>>> = _meetupsByDate.asStateFlow()

    private val _teamNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val teamNames: StateFlow<Map<String, String>> = _teamNames.asStateFlow()

    init {
        loadMeetups()
    }

    private fun loadMeetups() {
        viewModelScope.launch {
            val userId = userRepository.getUserModel()?.id ?: return@launch
            teamsRepository.getMyTeamsFlow(userId)
                .distinctUntilChangedBy { teams -> teams.map { t -> t._id to t.name } }
                .collect { teams ->
                    val teamIds = teams.map { it._id }
                    _teamNames.value = teams.associate { it._id to it.name.orEmpty() }
                    val meetups = eventsRepository.getMeetupsForTeams(teamIds)
                    _meetups.value = meetups
                    _meetupsByDate.value = groupByLocalDate(meetups, ZoneId.systemDefault())
                }
        }
    }

    companion object {
        internal fun groupByLocalDate(meetups: List<Meetup>, zone: ZoneId): Map<LocalDate, List<Meetup>> =
            meetups.groupBy { Instant.ofEpochMilli(it.startDate).atZone(zone).toLocalDate() }
    }
}

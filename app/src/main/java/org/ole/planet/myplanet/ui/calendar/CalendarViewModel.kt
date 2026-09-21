package org.ole.planet.myplanet.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _teamNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val teamNames: StateFlow<Map<String, String>> = _teamNames.asStateFlow()

    init {
        loadMeetups()
    }

    private fun loadMeetups() {
        viewModelScope.launch {
            val userId = userRepository.getUserModel()?.id ?: return@launch
            teamsRepository.getMyTeamsFlow(userId).collect { teams ->
                _teamNames.value = teams.associate { it._id to it.name.orEmpty() }
                _meetups.value = eventsRepository.getMeetupsForTeams(teams.map { it._id })
            }
        }
    }
}

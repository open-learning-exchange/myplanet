package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.repository.EventsRepository

@HiltViewModel
class TeamCalendarViewModel @Inject constructor(
    private val eventsRepository: EventsRepository
) : ViewModel() {

    private val _meetups = MutableStateFlow<List<Meetup>>(emptyList())
    val meetups: StateFlow<List<Meetup>> = _meetups.asStateFlow()

    private val _createMeetupResult = MutableSharedFlow<Boolean>()
    val createMeetupResult: SharedFlow<Boolean> = _createMeetupResult.asSharedFlow()

    fun fetchMeetups(teamId: String) {
        if (teamId.isEmpty()) return
        viewModelScope.launch {
            _meetups.value = eventsRepository.getMeetupsForTeam(teamId)
        }
    }

    fun createMeetup(params: MeetupCreationParams) {
        viewModelScope.launch {
            val success = eventsRepository.createMeetup(params)
            if (success) {
                fetchMeetups(params.teamId)
            }
            _createMeetupResult.emit(success)
        }
    }

    suspend fun updateMeetup(
        meetupId: String, title: String, description: String,
        startDate: Long, endDate: Long, startTime: String,
        endTime: String, meetupLocation: String, meetupLink: String,
        recurring: String
    ): Boolean {
        return eventsRepository.updateMeetup(
            meetupId = meetupId,
            title = title,
            description = description,
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            meetupLocation = meetupLocation,
            meetupLink = meetupLink,
            recurring = recurring
        )
    }
}

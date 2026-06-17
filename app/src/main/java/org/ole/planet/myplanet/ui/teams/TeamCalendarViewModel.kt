package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.repository.EventsRepository
import java.util.UUID
import javax.inject.Inject

data class MeetupCreationParams(
    val title: String,
    val meetupLink: String,
    val description: String,
    val location: String,
    val startTime: String,
    val endTime: String,
    val recurringText: String?,
    val teamPlanetCode: String?,
    val userName: String?,
    val startMillis: Long,
    val endMillis: Long,
    val teamId: String
)

@HiltViewModel
class TeamCalendarViewModel @Inject constructor(
    private val eventsRepository: EventsRepository
) : ViewModel() {

    private val _meetups = MutableStateFlow<List<RealmMeetup>>(emptyList())
    val meetups: StateFlow<List<RealmMeetup>> = _meetups.asStateFlow()

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
            val meetup = RealmMeetup().apply {
                id = "${UUID.randomUUID()}"
                title = params.title
                meetupLink = params.meetupLink
                description = params.description
                meetupLocation = params.location
                creator = params.userName
                startDate = params.startMillis
                endDate = params.endMillis
                startTime = params.startTime
                endTime = params.endTime
                createdDate = System.currentTimeMillis()
                sourcePlanet = params.teamPlanetCode
                val jo = JsonObject()
                jo.addProperty("type", "local")
                jo.addProperty("planetCode", params.teamPlanetCode)
                sync = Gson().toJson(jo)
                if (params.recurringText != null) {
                    recurring = params.recurringText
                }
                val ob = JsonObject()
                ob.addProperty("teams", params.teamId)
                link = Gson().toJson(ob)
                teamId = params.teamId
            }
            val success = eventsRepository.createMeetup(meetup)
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

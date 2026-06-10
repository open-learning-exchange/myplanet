package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.repository.EventsRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TeamCalendarViewModel @Inject constructor(
    private val eventsRepository: EventsRepository
) : ViewModel() {

    private val _meetups = MutableStateFlow<List<RealmMeetup>>(emptyList())
    val meetups: StateFlow<List<RealmMeetup>> = _meetups.asStateFlow()

    fun fetchMeetups(teamId: String) {
        if (teamId.isEmpty()) return
        viewModelScope.launch {
            _meetups.value = eventsRepository.getMeetupsForTeam(teamId)
        }
    }

    fun createMeetup(
        title: String,
        link: String,
        description: String,
        location: String,
        startTimeText: String,
        endTimeText: String,
        defaultPlaceholder: String,
        recurringText: String?,
        teamPlanetCode: String?,
        userName: String?,
        startMillis: Long,
        endMillis: Long,
        currentTeamId: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val meetup = RealmMeetup().apply {
                id = "${UUID.randomUUID()}"
                this.title = title
                meetupLink = link
                this.description = description
                meetupLocation = location
                creator = userName
                startDate = startMillis
                endDate = endMillis
                startTime = if (startTimeText == defaultPlaceholder) "" else startTimeText
                endTime = if (endTimeText == defaultPlaceholder) "" else endTimeText
                createdDate = System.currentTimeMillis()
                sourcePlanet = teamPlanetCode
                val jo = JsonObject()
                jo.addProperty("type", "local")
                jo.addProperty("planetCode", teamPlanetCode)
                sync = Gson().toJson(jo)
                if (recurringText != null) {
                    recurring = recurringText
                }
                val ob = JsonObject()
                ob.addProperty("teams", currentTeamId)
                this.link = Gson().toJson(ob)
                this.teamId = currentTeamId
            }
            val success = eventsRepository.createMeetup(meetup)
            if (success) {
                fetchMeetups(currentTeamId)
            }
            onComplete(success)
        }
    }
}

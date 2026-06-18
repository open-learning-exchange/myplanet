package org.ole.planet.myplanet.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import javax.inject.Inject

@HiltViewModel
class EventsDetailViewModel @Inject constructor(
    private val eventsRepository: EventsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _user = MutableStateFlow<RealmUser?>(null)
    val user: StateFlow<RealmUser?> = _user.asStateFlow()

    private val _meetup = MutableStateFlow<RealmMeetup?>(null)
    val meetup: StateFlow<RealmMeetup?> = _meetup.asStateFlow()

    private val _members = MutableStateFlow<List<RealmUser>>(emptyList())
    val members: StateFlow<List<RealmUser>> = _members.asStateFlow()

    private val _updateSuccess = MutableStateFlow<Boolean?>(null)
    val updateSuccess: StateFlow<Boolean?> = _updateSuccess.asStateFlow()

    fun loadData(meetUpId: String?) {
        viewModelScope.launch {
            _user.value = userSessionManager.getUserModel()

            if (!meetUpId.isNullOrBlank()) {
                val loadedMeetup = eventsRepository.getMeetupByLocalId(meetUpId)
                _meetup.value = loadedMeetup
                _members.value = eventsRepository.getJoinedMembers(meetUpId)
            }
        }
    }

    fun updateMeetup(
        meetupId: String,
        title: String,
        description: String,
        startDate: Long,
        endDate: Long,
        startTime: String,
        endTime: String,
        meetupLocation: String,
        meetupLink: String,
        recurring: String
    ) {
        viewModelScope.launch {
            val success = eventsRepository.updateMeetup(
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

            if (success) {
                _meetup.value = eventsRepository.getMeetupByLocalId(meetupId)
            }
            _updateSuccess.value = success
        }
    }

    fun resetUpdateSuccess() {
        _updateSuccess.value = null
    }

    fun toggleAttendance(meetupId: String) {
        viewModelScope.launch {
            val currentUser = _user.value
            _meetup.value = eventsRepository.toggleAttendance(meetupId, currentUser?.id)
            _members.value = eventsRepository.getJoinedMembers(meetupId)
        }
    }
}

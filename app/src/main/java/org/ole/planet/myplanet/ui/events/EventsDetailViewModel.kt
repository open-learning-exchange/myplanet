package org.ole.planet.myplanet.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.services.UserSessionManager

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

    private val _isEventActive = MutableStateFlow(false)
    val isEventActive: StateFlow<Boolean> = _isEventActive.asStateFlow()

    private fun checkEventActive(meetup: RealmMeetup?) {
        if (meetup == null) {
            _isEventActive.value = false
            return
        }
        val currentTime = Calendar.getInstance().timeInMillis
        _isEventActive.value = currentTime in meetup.startDate..meetup.endDate
    }

    fun loadData(meetUpId: String?) {
        viewModelScope.launch {
            _user.value = userSessionManager.getUserModel()

            if (!meetUpId.isNullOrBlank()) {
                val loadedMeetup = eventsRepository.getMeetupByLocalId(meetUpId)
                _meetup.value = loadedMeetup
                checkEventActive(loadedMeetup)
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
                val updatedMeetup = eventsRepository.getMeetupByLocalId(meetupId)
                _meetup.value = updatedMeetup
                checkEventActive(updatedMeetup)
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
            val updatedMeetup = eventsRepository.toggleAttendance(meetupId, currentUser?.id)
            _meetup.value = updatedMeetup
            checkEventActive(updatedMeetup)
            _members.value = eventsRepository.getJoinedMembers(meetupId)
        }
    }
}

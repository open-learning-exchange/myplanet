package org.ole.planet.myplanet.ui.events

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import javax.inject.Inject

@HiltViewModel
class EventsDetailViewModel @Inject constructor(
    val eventsRepository: EventsRepository,
    val userSessionManager: UserSessionManager
) : ViewModel()

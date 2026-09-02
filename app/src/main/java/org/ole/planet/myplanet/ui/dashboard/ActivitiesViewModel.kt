package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository
) : ViewModel() {

    val offlineLogins: StateFlow<List<OfflineActivity>> = flow {
        val userName = userRepository.getUserModel()?.name ?: return@flow
        emitAll(activitiesRepository.getOfflineLogins(userName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

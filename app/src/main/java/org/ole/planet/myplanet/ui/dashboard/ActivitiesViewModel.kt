package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val activitiesRepository: ActivitiesRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _offlineActivities = MutableStateFlow<List<OfflineActivity>?>(null)
    val offlineActivities: StateFlow<List<OfflineActivity>?> = _offlineActivities.asStateFlow()

    init {
        viewModelScope.launch {
            val userName = userSessionManager.getUserModel()?.name ?: return@launch
            val loginsFlow = activitiesRepository.getOfflineLogins(userName)
            loginsFlow.collectLatest { logins ->
                _offlineActivities.value = logins
            }
        }
    }
}

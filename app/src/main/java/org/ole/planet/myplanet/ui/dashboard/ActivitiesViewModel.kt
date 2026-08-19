package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.services.UserSessionManager
import javax.inject.Inject

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val userSessionManager: UserSessionManager,
    private val activitiesRepository: ActivitiesRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val offlineLogins: Flow<List<OfflineActivity>> = flow {
        val userName = userSessionManager.getUserModel()?.name
        emit(userName)
    }.filterNotNull().flatMapLatest { userName ->
        activitiesRepository.getOfflineLogins(userName)
    }
}

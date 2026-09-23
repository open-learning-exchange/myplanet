package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class DashboardElementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    suspend fun currentUser(): UserEntity? {
        return userRepository.getUserModel()
    }

    suspend fun recordUserChallengeAction(userId: String) {
        activitiesRepository.recordSyncUserChallengeAction(userId)
    }

    suspend fun logout() {
        userSessionManager.logoutAsync()
    }
}

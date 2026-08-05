package org.ole.planet.myplanet.ui.components

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class MarkdownViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    suspend fun getActiveUserIdSuspending(): String {
        return userRepository.getActiveUserIdSuspending()
    }

    suspend fun hasUserSyncAction(userId: String): Boolean {
        return userRepository.hasUserSyncAction(userId)
    }
}

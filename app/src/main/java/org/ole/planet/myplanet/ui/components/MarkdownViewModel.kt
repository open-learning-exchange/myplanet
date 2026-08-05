package org.ole.planet.myplanet.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.ui.dashboard.DashboardElementActivity
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class MarkdownViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    suspend fun getActiveUserIdSuspending(): String {
        return userRepository.getActiveUserIdSuspending()
    }

    suspend fun hasUserSyncAction(userId: String): Boolean {
        return userRepository.hasUserSyncAction(userId)
    }

    fun logSyncInSharedPrefs(activity: DashboardElementActivity) {
        viewModelScope.launch(dispatcherProvider.io) {
            activity.logSyncInSharedPrefs()
        }
    }
}

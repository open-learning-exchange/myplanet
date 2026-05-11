package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class VoicesViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val userRepository: UserRepository,
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    fun deletePost(newsId: String, teamName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                voicesRepository.deletePost(newsId, teamName)
            }
            onComplete()
        }
    }

    fun shareNewsToCommunity(
        newsId: String,
        userId: String,
        planetCode: String,
        parentCode: String,
        teamName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = withContext(dispatcherProvider.io) {
                voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
            }
            onResult(result)
        }
    }

    // Note: The following are read-only suspend functions designed to be called directly from
    // the UI's lifecycleScope, avoiding intermediate MutableStateFlow caching for point-in-time reads.
    suspend fun getUserById(userId: String): RealmUser? {
        return withContext(dispatcherProvider.io) {
            userRepository.getUserById(userId)
        }
    }

    suspend fun getReplyCount(newsId: String): Int {
        return withContext(dispatcherProvider.io) {
            try {
                voicesRepository.getReplyCount(newsId)
            } catch (e: Exception) {
                0
            }
        }
    }

    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return withContext(dispatcherProvider.io) {
            voicesRepository.getLibraryResource(resourceId)
        }
    }

    suspend fun isTeamLeader(teamId: String?, userId: String?): Boolean {
        return withContext(dispatcherProvider.io) {
            try {
                if (teamId != null) teamsRepository.isTeamLeader(teamId, userId) else false
            } catch (e: Exception) {
                false
            }
        }
    }
}

package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _shareNewsResult = MutableSharedFlow<Result<Unit>>()
    val shareNewsResult: SharedFlow<Result<Unit>> = _shareNewsResult.asSharedFlow()

    private val _deletePostComplete = MutableSharedFlow<String>()
    val deletePostComplete: SharedFlow<String> = _deletePostComplete.asSharedFlow()

    fun deletePost(newsId: String, teamName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                voicesRepository.deletePost(newsId, teamName)
            }
            _deletePostComplete.emit(newsId)
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
            _shareNewsResult.emit(result)
            onResult(result)
        }
    }

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

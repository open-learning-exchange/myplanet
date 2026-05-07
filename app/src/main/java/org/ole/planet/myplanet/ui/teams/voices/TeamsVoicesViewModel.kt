package org.ole.planet.myplanet.ui.teams.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import javax.inject.Inject

@HiltViewModel
class TeamsVoicesViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val teamsRepository: TeamsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _discussions = MutableStateFlow<List<RealmNews?>>(emptyList())
    val discussions: StateFlow<List<RealmNews?>> = _discussions.asStateFlow()

    private val _createNewsSuccess = MutableSharedFlow<Boolean>()
    val createNewsSuccess: SharedFlow<Boolean> = _createNewsSuccess.asSharedFlow()

    private var observeJob: kotlinx.coroutines.Job? = null

    suspend fun getFilteredNews(teamId: String): List<RealmNews?> = withContext(dispatcherProvider.io) {
        val newsList = voicesRepository.getFilteredNews(teamId)
        voicesRepository.updateTeamNotification(teamId, newsList.size)
        newsList
    }

    fun observeDiscussions(teamId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatcherProvider.io) {
            voicesRepository.getDiscussionsByTeamIdFlow(teamId).collect {
                _discussions.value = it
            }
        }
    }

    fun createTeamNews(map: HashMap<String?, String>, user: RealmUser, imageList: List<String>) {
        viewModelScope.launch(dispatcherProvider.io) {
            val success = voicesRepository.createTeamNews(map, user, imageList)
            _createNewsSuccess.emit(success)
        }
    }

    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean = withContext(dispatcherProvider.io) {
        teamsRepository.isTeamLeader(teamId, userId)
    }

    suspend fun getUserById(userId: String): RealmUser? = withContext(dispatcherProvider.io) {
        userRepository.getUserById(userId)
    }

    suspend fun getReplyCount(newsId: String): Int = withContext(dispatcherProvider.io) {
        voicesRepository.getReplyCount(newsId)
    }

    suspend fun deletePost(newsId: String, teamName: String) = withContext(dispatcherProvider.io) {
        voicesRepository.deletePost(newsId, teamName)
    }

    suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> = withContext(dispatcherProvider.io) {
        voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
    }

    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? = withContext(dispatcherProvider.io) {
        voicesRepository.getLibraryResource(resourceId)
    }
}

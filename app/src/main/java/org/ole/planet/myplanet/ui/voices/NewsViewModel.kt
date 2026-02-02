package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val voicesRepository: VoicesRepository,
    private val userRepository: UserRepository,
    private val teamsRepository: TeamsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _isTeamLeader = MutableStateFlow(false)
    val isTeamLeader = _isTeamLeader.asStateFlow()

    fun getPrivateImageUrlsCreatedAfter(timestamp: Long, callback: (List<String>) -> Unit) {
        viewModelScope.launch {
            val urls = resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)
            callback(urls)
        }
    }

    fun checkTeamLeader(teamId: String?) {
        if (teamId == null) {
            _isTeamLeader.value = false
            return
        }
        viewModelScope.launch {
            val user = userSessionManager.userModel
            val isLeader = teamsRepository.isTeamLeader(teamId, user?.id)
            _isTeamLeader.value = isLeader
        }
    }

    suspend fun getUser(userId: String): RealmUser? {
        return userRepository.getUserById(userId)
    }

    suspend fun getReplyCount(newsId: String): Int {
        return voicesRepository.getReplies(newsId).size
    }

    fun deletePost(newsId: String, teamName: String) {
        viewModelScope.launch {
            voicesRepository.deletePost(newsId, teamName)
        }
    }

    suspend fun shareNews(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> {
        return voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
    }

    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return voicesRepository.getLibraryResource(resourceId)
    }

    fun toggleLike(news: RealmNews) {
        val newsId = news.id ?: return
        val labels = news.labels
        val isLiked = labels?.contains("Like") == true

        viewModelScope.launch {
            if (isLiked) {
                voicesRepository.removeLabel(newsId, "Like")
            } else {
                voicesRepository.addLabel(newsId, "Like")
            }
        }
    }

    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>> {
        return voicesRepository.getNewsWithReplies(newsId)
    }
}

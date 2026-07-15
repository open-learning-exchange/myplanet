package org.ole.planet.myplanet.ui.teams.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.VoicePostingPolicy
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.repository.toVoicePostingPolicy
import org.ole.planet.myplanet.ui.voices.DefaultLabelManipulator
import org.ole.planet.myplanet.ui.voices.LabelManipulator
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class TeamsVoicesViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel(), LabelManipulator by DefaultLabelManipulator(voicesRepository, dispatcherProvider) {

    private val _teamPolicy = MutableStateFlow<Pair<RealmMyTeam?, VoicePostingPolicy?>?>(null)
    val teamPolicy: StateFlow<Pair<RealmMyTeam?, VoicePostingPolicy?>?> = _teamPolicy.asStateFlow()

    private val _discussions = MutableStateFlow<List<RealmNews?>>(emptyList())
    val discussions: StateFlow<List<RealmNews?>> = _discussions.asStateFlow()

    private val _createNewsSuccess = Channel<Boolean>(Channel.BUFFERED)
    val createNewsSuccess: Flow<Boolean> = _createNewsSuccess.receiveAsFlow()

    private var observeJob: Job? = null

    fun loadTeam(teamId: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            val teamResult = teamsRepository.getTeamByIdOrTeamId(teamId)
            _teamPolicy.value = Pair(teamResult, teamResult?.toVoicePostingPolicy())
        }
    }

    suspend fun getFilteredNews(teamId: String): List<RealmNews?> {
        val newsList = voicesRepository.getFilteredNews(teamId)
        voicesRepository.updateTeamNotification(teamId, newsList.size)
        return newsList
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
            _createNewsSuccess.send(success)
        }
    }

    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean {
        return teamsRepository.isTeamLeader(teamId, userId)
    }

    suspend fun getUserById(userId: String): RealmUser? {
        return voicesRepository.getUserById(userId)
    }

    suspend fun getReplyCount(newsId: String): Int {
        return voicesRepository.getReplyCount(newsId)
    }

    /**
     * Note: This suspend function is called from the Fragment's lifecycleScope.
     * In-flight deletions will be cancelled if the Fragment is destroyed (e.g., on rotation),
     * and the adapter callback will not fire. This limitation is accepted to keep the adapter
     * interface simple without needing complex Flow correlation for individual items.
     */
    suspend fun deletePost(newsId: String, teamName: String) {
        voicesRepository.deletePost(newsId, teamName)
    }

    /**
     * Note: This suspend function is called from the Fragment's lifecycleScope.
     * In-flight shares will be cancelled if the Fragment is destroyed (e.g., on rotation),
     * and the adapter callback will not fire. This limitation is accepted to keep the adapter
     * interface simple without needing complex Flow correlation for individual items.
     */
    suspend fun shareNewsToCommunity(newsId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> {
        return voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
    }

    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return voicesRepository.getLibraryResource(resourceId)
    }

}

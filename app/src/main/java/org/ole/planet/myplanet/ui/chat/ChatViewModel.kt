package org.ole.planet.myplanet.ui.chat

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val teamRepository: TeamRepository,
    @AppPreferences private val settings: SharedPreferences,
) : ViewModel() {
    private val _selectedChatHistory = MutableStateFlow<List<Conversation>?>(null)
    val selectedChatHistory: StateFlow<List<Conversation>?> = _selectedChatHistory.asStateFlow()

    private val _selectedId = MutableStateFlow("")
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _selectedRev = MutableStateFlow("")
    val selectedRev: StateFlow<String> = _selectedRev.asStateFlow()

    private val _selectedAiProvider = MutableStateFlow<String?>(null)
    val selectedAiProvider: StateFlow<String?> = _selectedAiProvider.asStateFlow()

    private val _aiProviders = MutableStateFlow<Map<String, Boolean>?>(null)
    val aiProviders: StateFlow<Map<String, Boolean>?> = _aiProviders.asStateFlow()

    private val _aiProvidersLoading = MutableStateFlow(false)
    val aiProvidersLoading: StateFlow<Boolean> = _aiProvidersLoading.asStateFlow()

    private val _aiProvidersError = MutableStateFlow(false)
    val aiProvidersError: StateFlow<Boolean> = _aiProvidersError.asStateFlow()

    private val _chatHistoryData = MutableStateFlow<ChatHistoryData?>(null)
    val chatHistoryData: StateFlow<ChatHistoryData?> = _chatHistoryData.asStateFlow()

    fun setSelectedChatHistory(conversations: List<Conversation>) {
        _selectedChatHistory.value = conversations
    }

    fun setSelectedId(id: String) {
        _selectedId.value = id
    }

    fun setSelectedRev(rev: String) {
        _selectedRev.value = rev
    }

    fun setSelectedAiProvider(aiProvider: String?) {
        _selectedAiProvider.value = aiProvider
    }

    fun setAiProviders(providers: Map<String, Boolean>?) {
        _aiProviders.value = providers
    }

    fun setAiProvidersLoading(isLoading: Boolean) {
        _aiProvidersLoading.value = isLoading
    }

    fun setAiProvidersError(hasError: Boolean) {
        _aiProvidersError.value = hasError
    }

    fun clearChatState() {
        _selectedChatHistory.value = null
        _selectedId.value = ""
        _selectedRev.value = ""
        _selectedAiProvider.value = null
    }

    fun shouldFetchAiProviders(): Boolean {
        return _aiProviders.value == null && !_aiProvidersLoading.value
    }

    fun loadChatHistoryData(forceRefresh: Boolean = false) {
        if (_chatHistoryData.value != null && !forceRefresh) return

        viewModelScope.launch {
            _chatHistoryData.value = withContext(Dispatchers.IO) {
                val userId = settings.getString("userId", "")
                val user = if (!userId.isNullOrEmpty()) userRepository.getUserById(userId) else null
                val planetCode = user?.planetCode
                val sharedNewsMessages = chatRepository.getPlanetNewsMessages(planetCode)
                val list = chatRepository.getChatHistoryForUser(user?.name)
                val teams = teamRepository.getShareableTeams()
                val enterprises = teamRepository.getShareableEnterprises()
                val parentCode = settings.getString("parentCode", "")
                val communityName = settings.getString("communityName", "")
                val communityId = if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) {
                    "$communityName@$parentCode"
                } else {
                    null
                }
                val community = communityId?.let { teamRepository.getTeamById(it) }
                val shareTargets = ChatShareTargets(community, teams, enterprises)
                ChatHistoryData(user, list, sharedNewsMessages, shareTargets)
            }
        }
    }
}

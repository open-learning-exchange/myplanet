package org.ole.planet.myplanet.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val teamsRepository: TeamsRepository,
    private val voicesRepository: VoicesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _selectedChatHistory = MutableStateFlow<List<RealmConversation>?>(null)
    val selectedChatHistory: StateFlow<List<RealmConversation>?> = _selectedChatHistory.asStateFlow()

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

    private val _conversationSaveSuccess = MutableSharedFlow<Boolean>()
    val conversationSaveSuccess: SharedFlow<Boolean> = _conversationSaveSuccess.asSharedFlow()

    fun continueConversation(id: String, query: String, response: String, rev: String) {
        if (query.isBlank() && response.isBlank()) return

        viewModelScope.launch {
            try {
                chatRepository.continueConversation(id, query, response, rev)
                _conversationSaveSuccess.emit(true)
            } catch (e: Exception) {
                _conversationSaveSuccess.emit(false)
            }
        }
    }

    suspend fun parseNewsConversations(newsConversations: String?): List<ChatMessage> {
        return withContext(dispatcherProvider.io) {
            if (newsConversations.isNullOrBlank()) return@withContext emptyList()
            try {
                val conversations = JsonUtils.gson.fromJson(newsConversations, Array<RealmConversation>::class.java).toList()
                val list = mutableListOf<ChatMessage>()
                val limit = 20
                val limitedConversations = if (conversations.size > limit) conversations.takeLast(limit) else conversations
                for (conversation in limitedConversations) {
                    conversation.query?.let { list.add(ChatMessage(it, ChatMessage.QUERY)) }
                    conversation.response?.let { list.add(ChatMessage(it, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL)) }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun setSelectedChatHistory(conversations: List<RealmConversation>) {
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

    suspend fun loadShareTargets(parentCode: String?, communityName: String?, userId: String?): ChatShareTargets {
        val teams = teamsRepository.getTeamSummaries(userId)
        val enterprises = teamsRepository.getShareableEnterpriseSummaries(userId)
        val communityId = if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) {
            "$communityName@$parentCode"
        } else {
            null
        }
        val community = communityId?.let { id ->
            teamsRepository.getTeamSummaryById(id) ?: TeamSummary(
                _id = id,
                name = communityName ?: "",
                teamType = null,
                teamPlanetCode = null,
                createdDate = null,
                type = null,
                status = null,
                teamId = null,
                description = null,
                services = null,
                rules = null
            )
        }
        return ChatShareTargets(community, teams, enterprises)
    }

    suspend fun shareChatToVoices(chatId: String, viewInId: String, map: HashMap<String?, String>, currentUser: RealmUser?): Result<RealmNews?> {
        if (chatId.isNotEmpty() && viewInId.isNotEmpty() && voicesRepository.isAlreadyShared(chatId, viewInId)) {
            return Result.success(null)
        }
        val createdNews = voicesRepository.createNews(map, currentUser, null)
        return Result.success(createdNews)
    }
}

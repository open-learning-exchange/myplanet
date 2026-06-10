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
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    companion object {
        const val PAGE_SIZE = 20
    }

    var allConversations: List<RealmConversation> = emptyList()
    var loadedCount = 0

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

    suspend fun parseAndBuildInitialPage(newsConversations: String?): List<ChatMessage> {
        return withContext(dispatcherProvider.io) {
            if (newsConversations.isNullOrBlank()) return@withContext emptyList()
            try {
                val conversations = JsonUtils.gson.fromJson(newsConversations, Array<RealmConversation>::class.java).toList()
                allConversations = conversations
                loadedCount = minOf(PAGE_SIZE, conversations.size)
                buildInitialPage()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun processChatHistory(conversations: List<RealmConversation>): List<ChatMessage> {
        allConversations = conversations
        loadedCount = minOf(PAGE_SIZE, conversations.size)
        return buildInitialPage()
    }

    private fun buildInitialPage(): List<ChatMessage> {
        val total = allConversations.size
        val startIndex = maxOf(0, total - loadedCount)
        val messages = mutableListOf<ChatMessage>()
        if (startIndex > 0) messages.add(ChatMessage("", ChatMessage.LOAD_MORE))
        messages.addAll(buildMessagesSlice(startIndex, total))
        return messages
    }

    private fun buildMessagesSlice(startIndex: Int, endIndex: Int): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        for (i in startIndex until endIndex) {
            val conv = allConversations[i]
            conv.query?.let { messages.add(ChatMessage(it, ChatMessage.QUERY)) }
            conv.response?.let { messages.add(ChatMessage(it, ChatMessage.RESPONSE, ChatMessage.RESPONSE_SOURCE_SHARED_VIEW_MODEL)) }
        }
        return messages
    }

    fun loadMoreConversations(): Pair<List<ChatMessage>, Boolean> {
        val total = allConversations.size
        val prevStartIndex = maxOf(0, total - loadedCount)
        loadedCount = minOf(loadedCount + PAGE_SIZE, total)
        val newStartIndex = maxOf(0, total - loadedCount)
        val newMessages = buildMessagesSlice(newStartIndex, prevStartIndex)
        return Pair(newMessages, newStartIndex > 0)
    }

    fun clearPaginationState() {
        allConversations = emptyList()
        loadedCount = 0
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
}

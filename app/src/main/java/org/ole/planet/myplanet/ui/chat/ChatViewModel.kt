package org.ole.planet.myplanet.ui.chat

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ChatResult
import org.ole.planet.myplanet.repository.ChatSearchMode
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.RetryUtils

data class ChatUiState(
    val selectedChatHistory: List<Conversation>? = null,
    val selectedAiProvider: String? = null,
    val selectedId: String = "",
    val selectedRev: String = "",
    val aiProviders: Map<String, Boolean>? = null,
    val aiProvidersLoading: Boolean = false,
    val aiProvidersError: Boolean = false
)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val teamsRepository: TeamsRepository,
    private val voicesRepository: VoicesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val realtimeSyncManager: RealtimeSyncManager
) : ViewModel() {
    companion object {
        const val PAGE_SIZE = 20
    }
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var allConversations: List<Conversation> = emptyList()
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var loadedCount = 0
    private var allChats: List<ChatHistory> = emptyList()
    private val _refreshChatSignal = MutableSharedFlow<Unit>(replay = 1)
    val refreshChatSignal: SharedFlow<Unit> = _refreshChatSignal.asSharedFlow()
    init {
        _refreshChatSignal.tryEmit(Unit)
        viewModelScope.launch {
            realtimeSyncManager.updatesFor("chats")
                .collect { update ->
                    if (update.shouldRefreshUI) {
                        _refreshChatSignal.emit(Unit)
                    }
                }
        }
    }
    private var loadDataJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    sealed class ShareChatResult {
        object AlreadyShared : ShareChatResult()
        data class Shared(val news: News, val chatId: String) : ShareChatResult()
    }
    private val _shareResult = MutableSharedFlow<ShareChatResult>()
    val shareResult: SharedFlow<ShareChatResult> = _shareResult.asSharedFlow()
    private val _screenData = MutableStateFlow<ChatHistoryScreenData?>(null)
    val screenData: StateFlow<ChatHistoryScreenData?> = _screenData.asStateFlow()
    private val _filteredChats = MutableStateFlow<List<ChatHistory>>(emptyList())
    val filteredChats: StateFlow<List<ChatHistory>> = _filteredChats.asStateFlow()
    private var cachedUser: UserEntity? = null
    private var cachedShareTargets: ChatShareTargets? = null
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
    private val aiProvidersFlow = combine(_aiProviders, _aiProvidersLoading, _aiProvidersError) { providers, loading, error ->
        Triple(providers, loading, error)
    }
    val chatUiState: StateFlow<ChatUiState> = combine(
        _selectedChatHistory,
        _selectedAiProvider,
        _selectedId,
        _selectedRev,
        aiProvidersFlow
    ) { history, aiProvider, id, rev, aiState ->
        ChatUiState(
            selectedChatHistory = history,
            selectedAiProvider = aiProvider,
            selectedId = id,
            selectedRev = rev,
            aiProviders = aiState.first,
            aiProvidersLoading = aiState.second,
            aiProvidersError = aiState.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())
    fun loadChatHistoryScreenData(
        userId: String?,
        parentCode: String?,
        communityName: String?
    ) {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            val result = RetryUtils.retry(maxAttempts = 3, delayMs = 2000L) {
                val currentUser = cachedUser ?: loadCurrentUser(userId).also { cachedUser = it }
                val newsMessages = voicesRepository.getPlanetNewsMessages(currentUser?.planetCode)
                val chatHistory = chatRepository.getChatHistoryForUser(currentUser?.name)
                val targets = cachedShareTargets ?: loadShareTargets(parentCode, communityName, currentUser?._id).also { cachedShareTargets = it }
                allChats = chatHistory
                ChatHistoryScreenData(currentUser, chatHistory, newsMessages, targets, chatRepository.extractSharedViewInIds(newsMessages))
            }
            result?.let { data ->
                _screenData.value = data
                _filteredChats.value = allChats
            }
        }
    }
    fun searchChats(query: String, isFullSearch: Boolean, isQuestion: Boolean) {
        if (query.isBlank()) {
            _filteredChats.value = allChats
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val mode = if (!isFullSearch) {
                ChatSearchMode.TITLE
            } else if (isQuestion) {
                ChatSearchMode.QUESTION
            } else {
                ChatSearchMode.RESPONSE
            }
            val results = chatRepository.searchChats(query, mode, allChats)
            _filteredChats.value = results
        }
    }
    private suspend fun loadCurrentUser(userId: String?): UserEntity? {
        if (userId.isNullOrEmpty()) {
            return null
        }
        return userRepository.getUserById(userId)
    }
    private suspend fun loadShareTargets(parentCode: String?, communityName: String?, userId: String?): ChatShareTargets {
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
    suspend fun parseAndBuildInitialPage(newsConversations: String?): List<ChatMessage> {
        val parsedConversations = withContext(dispatcherProvider.io) {
            if (newsConversations.isNullOrBlank()) return@withContext emptyList()
            try {
                JsonUtils.gson.fromJson(newsConversations, Array<Conversation>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        allConversations = parsedConversations
        loadedCount = minOf(PAGE_SIZE, parsedConversations.size)
        return buildInitialPage()
    }
    fun processChatHistory(conversations: List<Conversation>): List<ChatMessage> {
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
    fun shareChatToVoices(chatId: String, viewInId: String, payload: HashMap<String?, String>) {
        viewModelScope.launch {
            if (voicesRepository.isAlreadyShared(chatId, viewInId)) {
                _shareResult.emit(ShareChatResult.AlreadyShared)
            } else {
                val news = voicesRepository.createNews(payload, cachedUser, null)
                _shareResult.emit(ShareChatResult.Shared(news, chatId))
            }
        }
    }
    fun fetchAiProviders(serverUrl: String, cachedProviders: Map<String, Boolean>? = null) {
        if (!shouldFetchAiProviders()) {
            return
        }

        setAiProvidersLoading(true)
        setAiProvidersError(false)

        viewModelScope.launch {
            val providers = chatRepository.fetchAiProviders(serverUrl)
            setAiProvidersLoading(false)
            if (providers == null || providers.values.all { !it }) {
                if (cachedProviders != null) {
                    setAiProvidersError(false)
                    setAiProviders(cachedProviders)
                } else {
                    setAiProvidersError(true)
                    setAiProviders(null)
                }
            } else {
                setAiProvidersError(false)
                setAiProviders(providers)
            }
        }
    }
    suspend fun getLatestRev(id: String): String? {
        return chatRepository.getLatestRev(id)
    }
    suspend fun sendNewChatRequest(query: String, userName: String?, aiProvider: AiProvider): ChatResult {
        return chatRepository.sendNewChatRequest(query, userName, aiProvider)
    }
    suspend fun sendContinueChatRequest(query: String, userName: String?, aiProvider: AiProvider, id: String, rev: String): ChatResult {
        return chatRepository.sendContinueChatRequest(query, userName, aiProvider, id, rev)
    }
    suspend fun getUserById(userId: String): UserEntity? {
        return userRepository.getUserById(userId)
    }
}

package org.ole.planet.myplanet.ui.chat

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.ChatHistoryItem
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val teamsRepository: TeamsRepository,
    private val voicesRepository: VoicesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private data class PrecomputedChat(
        val chat: RealmChatHistory,
        val normalizedTitle: String?,
        val normalizedQueries: List<String?>,
        val normalizedResponses: List<String?>
    )

    companion object {
        const val PAGE_SIZE = 20
        private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var allConversations: List<RealmConversation> = emptyList()
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var loadedCount = 0
    private var allChats: List<ChatHistoryItem> = emptyList()
    private var precomputedChats: List<PrecomputedChat> = emptyList()

    private val _screenData = MutableStateFlow<ChatHistoryScreenData?>(null)
    val screenData: StateFlow<ChatHistoryScreenData?> = _screenData.asStateFlow()

    private val _filteredChats = MutableStateFlow<List<ChatHistoryItem>>(emptyList())
    val filteredChats: StateFlow<List<ChatHistoryItem>> = _filteredChats.asStateFlow()

    private var cachedUser: RealmUser? = null
    private var cachedShareTargets: ChatShareTargets? = null

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
    fun loadChatHistoryScreenData(
        userId: String?,
        parentCode: String?,
        communityName: String?
    ) {
        viewModelScope.launch {
            val currentUser = cachedUser ?: loadCurrentUser(userId).also { cachedUser = it }
            val newsMessages = voicesRepository.getPlanetNewsMessages(currentUser?.planetCode)
            val chatHistory = chatRepository.getChatHistoryForUser(currentUser?.name)
            val targets = cachedShareTargets ?: loadShareTargets(parentCode, communityName, currentUser?._id).also { cachedShareTargets = it }

            withContext(dispatcherProvider.default) {
                val sorted = sortChats(chatHistory)
                allChats = sorted.map { ChatHistoryItem(it._id ?: "", it._rev, it.title, it.aiProvider, it.conversations?.toList()) }
                precomputedChats = buildPrecomputedChats(sorted)
            }
            val data = ChatHistoryScreenData(currentUser, chatHistory, newsMessages, targets)
            _screenData.value = data
            _filteredChats.value = allChats
        }
    }

    private fun sortChats(chats: List<RealmChatHistory>): List<RealmChatHistory> {
        return chats.sortedByDescending { chat ->
            maxOf(chat.createdDate?.toLongOrNull() ?: 0L, chat.updatedDate?.toLongOrNull() ?: 0L)
        }
    }

    private fun buildPrecomputedChats(chats: List<RealmChatHistory>): List<PrecomputedChat> {
        return chats.map { chat ->
            val title = if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                chat.conversations?.get(0)?.query?.let { Utilities.normalizeText(it) }
            } else {
                chat.title?.let { Utilities.normalizeText(it) }
            }
            val queries = chat.conversations?.map { it?.query?.let { q -> Utilities.normalizeText(q) } } ?: emptyList()
            val responses = chat.conversations?.map { it?.response?.let { r -> Utilities.normalizeText(r) } } ?: emptyList()

            PrecomputedChat(chat, title, queries, responses)
        }
    }


    fun searchChats(query: String, isFullSearch: Boolean, isQuestion: Boolean) {
        if (query.isBlank()) {
            _filteredChats.value = allChats
            return
        }
        viewModelScope.launch {
            val results = withContext(dispatcherProvider.default) {
                if (isFullSearch) {
                    fullConvoSearch(query, isQuestion)
                } else {
                    searchByTitle(query)
                }
            }
            _filteredChats.value = results
        }
    }

    private fun fullConvoSearch(s: String, isQuestion: Boolean): List<ChatHistoryItem> {
        var conversation: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val inTitleStartQuery = mutableListOf<ChatHistoryItem>()
        val inTitleContainsQuery = mutableListOf<ChatHistoryItem>()
        val startsWithQuery = mutableListOf<ChatHistoryItem>()
        val containsQuery = mutableListOf<ChatHistoryItem>()

        for (pChat in precomputedChats) {
            val conversations = pChat.chat.conversations
            if (!conversations.isNullOrEmpty()) {
                for (i in 0 until conversations.size) {
                    conversation = if (isQuestion) {
                        pChat.normalizedQueries[i]
                    } else {
                        pChat.normalizedResponses[i]
                    }
                    if (conversation == null) continue
                    if (conversation.startsWith(normalizedQuery, ignoreCase = true)) {
                        if (i == 0) inTitleStartQuery.add(allChats.find { it._id == pChat.chat._id }!!) else startsWithQuery.add(allChats.find { it._id == pChat.chat._id }!!)
                        break
                    } else if (normalizedQueryParts.all { conversation.contains(it, ignoreCase = true) }) {
                        if (i == 0) inTitleContainsQuery.add(allChats.find { it._id == pChat.chat._id }!!) else containsQuery.add(allChats.find { it._id == pChat.chat._id }!!)
                        break
                    }
                }
            }
        }
        return inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery
    }

    private fun searchByTitle(s: String): List<ChatHistoryItem> {
        var title: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val startsWithQuery = mutableListOf<ChatHistoryItem>()
        val containsQuery = mutableListOf<ChatHistoryItem>()

        for (pChat in precomputedChats) {
            title = pChat.normalizedTitle
            if (title == null) continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(allChats.find { it._id == pChat.chat._id }!!)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(allChats.find { it._id == pChat.chat._id }!!)
            }
        }
        return startsWithQuery + containsQuery
    }

    private suspend fun loadCurrentUser(userId: String?): RealmUser? {
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
                JsonUtils.gson.fromJson(newsConversations, Array<RealmConversation>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        allConversations = parsedConversations
        loadedCount = minOf(PAGE_SIZE, parsedConversations.size)
        return buildInitialPage()
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

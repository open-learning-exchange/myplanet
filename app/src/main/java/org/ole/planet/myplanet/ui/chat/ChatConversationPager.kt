package org.ole.planet.myplanet.ui.chat

import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils

class ChatConversationPager(private val dispatcherProvider: DispatcherProvider) {
    companion object {
        const val PAGE_SIZE = 20
    }

    var allConversations: List<Conversation> = emptyList()
        private set
    var loadedCount = 0
        private set

    suspend fun parseAndBuildInitialPage(newsConversations: String?): List<ChatMessage> {
        val parsedConversations = withContext(dispatcherProvider.io) {
            if (newsConversations.isNullOrBlank()) return@withContext emptyList()
            try {
                GsonUtils.gson.fromJson(newsConversations, Array<Conversation>::class.java).toList()
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
}

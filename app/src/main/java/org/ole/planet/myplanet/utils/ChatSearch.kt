package org.ole.planet.myplanet.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.repository.ChatSearchMode

object ChatSearch {

    private data class PrecomputedChat(
        val chat: ChatHistory,
        val normalizedTitle: String?,
        val normalizedQueries: List<String?>,
        val normalizedResponses: List<String?>
    )

    suspend fun search(
        query: String,
        mode: ChatSearchMode,
        chats: List<ChatHistory>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): List<ChatHistory> {
        val precomputedChats = buildPrecomputedChats(chats, dispatcher)
        return if (mode == ChatSearchMode.TITLE) {
            searchByTitle(query, precomputedChats, dispatcher)
        } else {
            fullConvoSearch(query, isQuestion = (mode == ChatSearchMode.QUESTION), precomputedChats, dispatcher)
        }
    }

    private suspend fun buildPrecomputedChats(
        chats: List<ChatHistory>,
        dispatcher: CoroutineDispatcher
    ): List<PrecomputedChat> = withContext(dispatcher) {
        chats.map { chat ->
            val title = if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                chat.conversations?.get(0)?.query?.let { Utilities.normalizeText(it) }
            } else {
                chat.title?.let { Utilities.normalizeText(it) }
            }
            val queries = chat.conversations?.map { it.query?.let { q -> Utilities.normalizeText(q) } } ?: emptyList()
            val responses = chat.conversations?.map { it.response?.let { r -> Utilities.normalizeText(r) } } ?: emptyList()
            PrecomputedChat(chat, title, queries, responses)
        }
    }

    private suspend fun fullConvoSearch(
        s: String,
        isQuestion: Boolean,
        precomputedChats: List<PrecomputedChat>,
        dispatcher: CoroutineDispatcher
    ): List<ChatHistory> = withContext(dispatcher) {
        var conversation: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val inTitleStartQuery = mutableListOf<ChatHistory>()
        val inTitleContainsQuery = mutableListOf<ChatHistory>()
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()
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
                        if (i == 0) inTitleStartQuery.add(pChat.chat) else startsWithQuery.add(pChat.chat)
                        break
                    } else if (normalizedQueryParts.all { conversation.contains(it, ignoreCase = true) }) {
                        if (i == 0) inTitleContainsQuery.add(pChat.chat) else containsQuery.add(pChat.chat)
                        break
                    }
                }
            }
        }
        inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery
    }

    private suspend fun searchByTitle(
        s: String,
        precomputedChats: List<PrecomputedChat>,
        dispatcher: CoroutineDispatcher
    ): List<ChatHistory> = withContext(dispatcher) {
        var title: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()
        for (pChat in precomputedChats) {
            title = pChat.normalizedTitle
            if (title == null) continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(pChat.chat)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(pChat.chat)
            }
        }
        startsWithQuery + containsQuery
    }
}

package org.ole.planet.myplanet.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.repository.ChatSearchMode

object ChatSearch {

    private data class TitleChat(
        val chat: ChatHistory,
        val normalizedTitle: String?
    )

    private data class ConvoChat(
        val chat: ChatHistory,
        val normalized: List<String?>
    )

    suspend fun search(
        query: String,
        mode: ChatSearchMode,
        chats: List<ChatHistory>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): List<ChatHistory> =
        if (mode == ChatSearchMode.TITLE) {
            searchByTitle(query, chats, dispatcher)
        } else {
            fullConvoSearch(query, isQuestion = (mode == ChatSearchMode.QUESTION), chats, dispatcher)
        }

    private suspend fun fullConvoSearch(
        s: String,
        isQuestion: Boolean,
        chats: List<ChatHistory>,
        dispatcher: CoroutineDispatcher
    ): List<ChatHistory> = withContext(dispatcher) {
        val precomputedChats = chats.map { chat ->
            val normalized = chat.conversations?.map { convo ->
                val text = if (isQuestion) convo.query else convo.response
                text?.let { Utilities.normalizeText(it) }
            } ?: emptyList()
            ConvoChat(chat, normalized)
        }

        var conversation: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val inTitleStartQuery = mutableListOf<ChatHistory>()
        val inTitleContainsQuery = mutableListOf<ChatHistory>()
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()

        for (pChat in precomputedChats) {
            run {
                pChat.normalized.forEachIndexed { i, norm ->
                    conversation = norm ?: return@forEachIndexed
                    if (conversation.startsWith(normalizedQuery, ignoreCase = true)) {
                        if (i == 0) inTitleStartQuery.add(pChat.chat) else startsWithQuery.add(pChat.chat)
                        return@run
                    } else if (normalizedQueryParts.all { conversation.contains(it, ignoreCase = true) }) {
                        if (i == 0) inTitleContainsQuery.add(pChat.chat) else containsQuery.add(pChat.chat)
                        return@run
                    }
                }
            }
        }
        inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery
    }

    private suspend fun searchByTitle(
        s: String,
        chats: List<ChatHistory>,
        dispatcher: CoroutineDispatcher
    ): List<ChatHistory> = withContext(dispatcher) {
        val precomputedChats = chats.map { chat ->
            val conversations = chat.conversations
            val title = if (conversations != null && conversations.isNotEmpty()) {
                conversations[0].query?.let { Utilities.normalizeText(it) }
            } else {
                chat.title?.let { Utilities.normalizeText(it) }
            }
            TitleChat(chat, title)
        }

        var title: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()

        for (pChat in precomputedChats) {
            title = pChat.normalizedTitle ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(pChat.chat)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(pChat.chat)
            }
        }
        startsWithQuery + containsQuery
    }
}

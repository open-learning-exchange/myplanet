package org.ole.planet.myplanet.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.repository.ChatSearchMode

object ChatSearch {

    class Index(val chats: List<ChatHistory>) {
        val titles: List<String?> by lazy {
            chats.map { chat ->
                val conversations = chat.conversations
                if (!conversations.isNullOrEmpty()) {
                    conversations[0].query?.let { Utilities.normalizeText(it) }
                } else {
                    chat.title?.let { Utilities.normalizeText(it) }
                }
            }
        }

        val questions: List<List<String?>> by lazy {
            chats.map { chat ->
                chat.conversations?.map { convo ->
                    convo.query?.let { Utilities.normalizeText(it) }
                } ?: emptyList()
            }
        }

        val responses: List<List<String?>> by lazy {
            chats.map { chat ->
                chat.conversations?.map { convo ->
                    convo.response?.let { Utilities.normalizeText(it) }
                } ?: emptyList()
            }
        }
    }

    suspend fun search(
        query: String,
        mode: ChatSearchMode,
        index: Index,
        dispatcher: CoroutineDispatcher
    ): List<ChatHistory> = withContext(dispatcher) {
        if (mode == ChatSearchMode.TITLE) {
            searchByTitle(query, index)
        } else {
            fullConvoSearch(query, isQuestion = (mode == ChatSearchMode.QUESTION), index)
        }
    }

    suspend fun search(
        query: String,
        mode: ChatSearchMode,
        chats: List<ChatHistory>,
        dispatcher: CoroutineDispatcher
    ): List<ChatHistory> = search(query, mode, Index(chats), dispatcher)

    private fun fullConvoSearch(
        s: String,
        isQuestion: Boolean,
        index: Index
    ): List<ChatHistory> {
        val convoList = if (isQuestion) index.questions else index.responses
        var conversation: String?
        val normalizedQueryParts = s.split(" ").filterNot { it.isEmpty() }.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val inTitleStartQuery = mutableListOf<ChatHistory>()
        val inTitleContainsQuery = mutableListOf<ChatHistory>()
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()

        for (chatIdx in index.chats.indices) {
            val chat = index.chats[chatIdx]
            val normalizedConvos = convoList[chatIdx]
            run {
                normalizedConvos.forEachIndexed { i, norm ->
                    conversation = norm ?: return@forEachIndexed
                    if (conversation.startsWith(normalizedQuery)) {
                        if (i == 0) inTitleStartQuery.add(chat) else startsWithQuery.add(chat)
                        return@run
                    } else if (normalizedQueryParts.all { conversation.contains(it) }) {
                        if (i == 0) inTitleContainsQuery.add(chat) else containsQuery.add(chat)
                        return@run
                    }
                }
            }
        }
        return inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery
    }

    private fun searchByTitle(
        s: String,
        index: Index
    ): List<ChatHistory> {
        var title: String?
        val normalizedQueryParts = s.split(" ").filterNot { it.isEmpty() }.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()

        for (i in index.chats.indices) {
            title = index.titles[i] ?: continue
            if (title.startsWith(normalizedQuery)) {
                startsWithQuery.add(index.chats[i])
            } else if (normalizedQueryParts.all { title.contains(it) }) {
                containsQuery.add(index.chats[i])
            }
        }
        return startsWithQuery + containsQuery
    }
}

package org.ole.planet.myplanet.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.repository.ChatSearchMode

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchIndexTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `same Index reused across two different queries returns same results as list overload for TITLE QUESTION and RESPONSE`() = runTest(testDispatcher) {
        val chat1 = ChatHistory().apply {
            title = "Alpha Discussion"
            conversations = listOf(
                Conversation().apply { query = "How is weather?"; response = "Weather is sunny" },
                Conversation().apply { query = "What is time?"; response = "It is noon" }
            )
        }
        val chat2 = ChatHistory().apply {
            title = "Beta Chat"
            conversations = listOf(
                Conversation().apply { query = "Tell me a story"; response = "Once upon a time" }
            )
        }
        val chats = listOf(chat1, chat2)
        val index = ChatSearch.Index(chats)

        // TITLE mode query 1 & 2
        assertEquals(
            ChatSearch.search("How", ChatSearchMode.TITLE, chats, testDispatcher),
            ChatSearch.search("How", ChatSearchMode.TITLE, index, testDispatcher)
        )
        assertEquals(
            ChatSearch.search("Beta", ChatSearchMode.TITLE, chats, testDispatcher),
            ChatSearch.search("Beta", ChatSearchMode.TITLE, index, testDispatcher)
        )

        // QUESTION mode query 1 & 2
        assertEquals(
            ChatSearch.search("weather", ChatSearchMode.QUESTION, chats, testDispatcher),
            ChatSearch.search("weather", ChatSearchMode.QUESTION, index, testDispatcher)
        )
        assertEquals(
            ChatSearch.search("story", ChatSearchMode.QUESTION, chats, testDispatcher),
            ChatSearch.search("story", ChatSearchMode.QUESTION, index, testDispatcher)
        )

        // RESPONSE mode query 1 & 2
        assertEquals(
            ChatSearch.search("sunny", ChatSearchMode.RESPONSE, chats, testDispatcher),
            ChatSearch.search("sunny", ChatSearchMode.RESPONSE, index, testDispatcher)
        )
        assertEquals(
            ChatSearch.search("time", ChatSearchMode.RESPONSE, chats, testDispatcher),
            ChatSearch.search("time", ChatSearchMode.RESPONSE, index, testDispatcher)
        )
    }

    @Test
    fun `ranking chat whose first conversation starts with query precedes later conversation match which precedes contains all parts`() = runTest(testDispatcher) {
        val chatContainsAllParts = ChatHistory().apply {
            title = "Chat Contains All Parts"
            conversations = listOf(
                Conversation().apply { query = "Alpha" },
                Conversation().apply { query = "Some Target text with Match inside" }
            )
        }
        val chatMatchLater = ChatHistory().apply {
            title = "Chat Later Match"
            conversations = listOf(
                Conversation().apply { query = "Alpha" },
                Conversation().apply { query = "Target Match" }
            )
        }
        val chatMatchFirst = ChatHistory().apply {
            title = "Chat First Match"
            conversations = listOf(
                Conversation().apply { query = "Target Match" }
            )
        }

        val chats = listOf(chatContainsAllParts, chatMatchLater, chatMatchFirst)
        val index = ChatSearch.Index(chats)

        val result = ChatSearch.search("Target Match", ChatSearchMode.QUESTION, index, testDispatcher)

        assertEquals(3, result.size)
        assertEquals("Chat First Match", result[0].title)
        assertEquals("Chat Later Match", result[1].title)
        assertEquals("Chat Contains All Parts", result[2].title)
    }

    @Test
    fun `chat whose first conversation has query null and second starts with query lands in startsWith bucket`() = runTest(testDispatcher) {
        val chatMatchFirst = ChatHistory().apply {
            title = "Chat First Match"
            conversations = listOf(
                Conversation().apply { query = "Target Question" }
            )
        }
        val chatNullFirst = ChatHistory().apply {
            title = "Chat Null First Query"
            conversations = listOf(
                Conversation().apply { query = null },
                Conversation().apply { query = "Target Question" }
            )
        }

        val chats = listOf(chatNullFirst, chatMatchFirst)
        val index = ChatSearch.Index(chats)

        val result = ChatSearch.search("Target", ChatSearchMode.QUESTION, index, testDispatcher)

        assertEquals(2, result.size)
        assertEquals("Chat First Match", result[0].title)
        assertEquals("Chat Null First Query", result[1].title)
    }

    @Test
    fun `TITLE falls back to title when conversations is null or empty`() = runTest(testDispatcher) {
        val chatNullConvo = ChatHistory().apply {
            title = "Fallback Title One"
            conversations = null
        }
        val chatEmptyConvo = ChatHistory().apply {
            title = "Fallback Title Two"
            conversations = emptyList()
        }
        val chatWithConvo = ChatHistory().apply {
            title = "Ignored Title"
            conversations = listOf(Conversation().apply { query = "Convo Query" })
        }

        val chats = listOf(chatNullConvo, chatEmptyConvo, chatWithConvo)
        val index = ChatSearch.Index(chats)

        val fallbackResult = ChatSearch.search("Fallback", ChatSearchMode.TITLE, index, testDispatcher)
        assertEquals(2, fallbackResult.size)
        assertEquals("Fallback Title One", fallbackResult[0].title)
        assertEquals("Fallback Title Two", fallbackResult[1].title)

        val convoResult = ChatSearch.search("Convo", ChatSearchMode.TITLE, index, testDispatcher)
        assertEquals(1, convoResult.size)
        assertEquals("Ignored Title", convoResult[0].title)

        val ignoredResult = ChatSearch.search("Ignored", ChatSearchMode.TITLE, index, testDispatcher)
        assertEquals(0, ignoredResult.size)
    }
}

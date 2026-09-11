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
class ChatSearchTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `search by title correctly filters list`() = runTest(testDispatcher) {
        val chat1 = ChatHistory().apply { title = "First Chat" }
        val chat2 = ChatHistory().apply { title = "Second Discussion" }
        val chats = listOf(chat1, chat2)

        val result = ChatSearch.search("First", ChatSearchMode.TITLE, chats, testDispatcher)

        assertEquals(1, result.size)
        assertEquals("First Chat", result[0].title)
    }

    @Test
    fun `search by full conversation filters by question`() = runTest(testDispatcher) {
        val chat1 = ChatHistory().apply {
            title = "Chat 1"
            conversations = listOf(Conversation().apply { query = "How is the weather?" })
        }
        val chat2 = ChatHistory().apply {
            title = "Chat 2"
            conversations = listOf(Conversation().apply { query = "Tell me a joke." })
        }
        val chats = listOf(chat1, chat2)

        val result = ChatSearch.search("weather", ChatSearchMode.QUESTION, chats, testDispatcher)

        assertEquals(1, result.size)
        assertEquals("Chat 1", result[0].title)
    }

    @Test
    fun `search by full conversation filters by response`() = runTest(testDispatcher) {
        val chat1 = ChatHistory().apply {
            title = "Chat 1"
            conversations = listOf(Conversation().apply { query = "How are you?"; response = "I am doing great!" })
        }
        val chat2 = ChatHistory().apply {
            title = "Chat 2"
            conversations = listOf(Conversation().apply { query = "Tell me a joke."; response = "Why did the chicken cross the road?" })
        }
        val chats = listOf(chat1, chat2)

        val result = ChatSearch.search("chicken", ChatSearchMode.RESPONSE, chats, testDispatcher)

        assertEquals(1, result.size)
        assertEquals("Chat 2", result[0].title)
    }

    @Test
    fun `search with empty query returns all chats`() = runTest(testDispatcher) {
        val chat1 = ChatHistory().apply { title = "Chat 1" }
        val chat2 = ChatHistory().apply { title = "Chat 2" }
        val chats = listOf(chat1, chat2)

        val result = ChatSearch.search("", ChatSearchMode.TITLE, chats, testDispatcher)

        assertEquals(2, result.size)
    }
}

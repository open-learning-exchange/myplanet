package org.ole.planet.myplanet.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.ChatMessage
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ChatConversationPaginatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val paginator = ChatConversationPaginator(dispatcherProvider)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parseAndBuildInitialPage with null blank and malformed JSON returns empty list and loadedCount 0`() = runTest {
        val nullResult = paginator.parseAndBuildInitialPage(null)
        assertTrue(nullResult.isEmpty())
        assertEquals(0, paginator.loadedCount)
        assertTrue(paginator.allConversations.isEmpty())

        val blankResult = paginator.parseAndBuildInitialPage("   ")
        assertTrue(blankResult.isEmpty())
        assertEquals(0, paginator.loadedCount)
        assertTrue(paginator.allConversations.isEmpty())

        val malformedResult = paginator.parseAndBuildInitialPage("invalid json payload")
        assertTrue(malformedResult.isEmpty())
        assertEquals(0, paginator.loadedCount)
        assertTrue(paginator.allConversations.isEmpty())
    }

    @Test
    fun `25 conversations initial page starts with LOAD_MORE and loadMoreConversations returns remaining 5 and false`() {
        val conversations = List(25) { Conversation().apply { query = "q$it"; response = "r$it" } }
        val initialMessages = paginator.processChatHistory(conversations)

        assertEquals(20, paginator.loadedCount)
        assertEquals(25, paginator.allConversations.size)
        assertEquals(41, initialMessages.size)
        assertEquals("", initialMessages[0].message)
        assertEquals(ChatMessage.LOAD_MORE, initialMessages[0].viewType)
        assertEquals("q5", initialMessages[1].message)
        assertEquals("r24", initialMessages[40].message)

        val (moreMessages, hasMore) = paginator.loadMoreConversations()
        assertEquals(25, paginator.loadedCount)
        assertFalse(hasMore)
        assertEquals(10, moreMessages.size)
        assertEquals("q0", moreMessages[0].message)
        assertEquals("r4", moreMessages[9].message)
    }

    @Test
    fun `45 conversations first loadMoreConversations returns true second returns false`() {
        val conversations = List(45) { Conversation().apply { query = "q$it"; response = "r$it" } }
        paginator.processChatHistory(conversations)
        assertEquals(20, paginator.loadedCount)

        val (firstMoreMessages, firstHasMore) = paginator.loadMoreConversations()
        assertEquals(40, paginator.loadedCount)
        assertTrue(firstHasMore)
        assertEquals(40, firstMoreMessages.size)

        val (secondMoreMessages, secondHasMore) = paginator.loadMoreConversations()
        assertEquals(45, paginator.loadedCount)
        assertFalse(secondHasMore)
        assertEquals(10, secondMoreMessages.size)
    }

    @Test
    fun `conversation with response null yields only QUERY message`() {
        val conversation = Conversation().apply { query = "query only"; response = null }
        val messages = paginator.processChatHistory(listOf(conversation))

        assertEquals(1, messages.size)
        assertEquals("query only", messages[0].message)
        assertEquals(ChatMessage.QUERY, messages[0].viewType)
    }

    @Test
    fun `clearPaginationState resets allConversations and loadedCount`() {
        val conversations = listOf(Conversation().apply { query = "q1"; response = "r1" })
        paginator.processChatHistory(conversations)
        assertEquals(1, paginator.allConversations.size)
        assertEquals(1, paginator.loadedCount)

        paginator.clearPaginationState()

        assertTrue(paginator.allConversations.isEmpty())
        assertEquals(0, paginator.loadedCount)
    }
}

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
class ChatConversationPagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val pager = ChatConversationPager(dispatcherProvider)

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
        val nullResult = pager.parseAndBuildInitialPage(null)
        assertTrue(nullResult.isEmpty())
        assertEquals(0, pager.loadedCount)
        assertTrue(pager.allConversations.isEmpty())

        val blankResult = pager.parseAndBuildInitialPage("   ")
        assertTrue(blankResult.isEmpty())
        assertEquals(0, pager.loadedCount)
        assertTrue(pager.allConversations.isEmpty())

        val malformedResult = pager.parseAndBuildInitialPage("invalid json payload")
        assertTrue(malformedResult.isEmpty())
        assertEquals(0, pager.loadedCount)
        assertTrue(pager.allConversations.isEmpty())
    }

    @Test
    fun `25 conversations initial page starts with LOAD_MORE and loadMoreConversations returns remaining 5 and false`() {
        val conversations = List(25) { Conversation().apply { query = "q$it"; response = "r$it" } }
        val initialMessages = pager.processChatHistory(conversations)

        assertEquals(20, pager.loadedCount)
        assertEquals(25, pager.allConversations.size)
        assertEquals(41, initialMessages.size)
        assertEquals("", initialMessages[0].message)
        assertEquals(ChatMessage.LOAD_MORE, initialMessages[0].viewType)
        assertEquals("q5", initialMessages[1].message)
        assertEquals("r24", initialMessages[40].message)

        val (moreMessages, hasMore) = pager.loadMoreConversations()
        assertEquals(25, pager.loadedCount)
        assertFalse(hasMore)
        assertEquals(10, moreMessages.size)
        assertEquals("q0", moreMessages[0].message)
        assertEquals("r4", moreMessages[9].message)
    }

    @Test
    fun `45 conversations first loadMoreConversations returns true second returns false`() {
        val conversations = List(45) { Conversation().apply { query = "q$it"; response = "r$it" } }
        pager.processChatHistory(conversations)
        assertEquals(20, pager.loadedCount)

        val (firstMoreMessages, firstHasMore) = pager.loadMoreConversations()
        assertEquals(40, pager.loadedCount)
        assertTrue(firstHasMore)
        assertEquals(40, firstMoreMessages.size)

        val (secondMoreMessages, secondHasMore) = pager.loadMoreConversations()
        assertEquals(45, pager.loadedCount)
        assertFalse(secondHasMore)
        assertEquals(10, secondMoreMessages.size)
    }

    @Test
    fun `conversation with response null yields only QUERY message`() {
        val conversation = Conversation().apply { query = "query only"; response = null }
        val messages = pager.processChatHistory(listOf(conversation))

        assertEquals(1, messages.size)
        assertEquals("query only", messages[0].message)
        assertEquals(ChatMessage.QUERY, messages[0].viewType)
    }

    @Test
    fun `clearPaginationState resets allConversations and loadedCount`() {
        val conversations = listOf(Conversation().apply { query = "q1"; response = "r1" })
        pager.processChatHistory(conversations)
        assertEquals(1, pager.allConversations.size)
        assertEquals(1, pager.loadedCount)

        pager.clearPaginationState()

        assertTrue(pager.allConversations.isEmpty())
        assertEquals(0, pager.loadedCount)
    }
}

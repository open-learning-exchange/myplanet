package org.ole.planet.myplanet.ui.chat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatRepository: ChatRepository
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dispatcherProvider: TestDispatcherProvider

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        dispatcherProvider = TestDispatcherProvider(testDispatcher)
        viewModel = ChatViewModel(chatRepository, dispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shouldFetchAiProviders returns true when aiProviders is null and aiProvidersLoading is false`() {
        assertTrue(viewModel.shouldFetchAiProviders())
    }

    @Test
    fun `shouldFetchAiProviders returns false when aiProvidersLoading is set to true`() {
        viewModel.setAiProvidersLoading(true)
        assertFalse(viewModel.shouldFetchAiProviders())
    }

    @Test
    fun `shouldFetchAiProviders returns false after setAiProviders`() {
        viewModel.setAiProviders(mapOf("openai" to true))
        assertFalse(viewModel.shouldFetchAiProviders())
    }

    @Test
    fun `continueConversation emits true on success`() = runTest {
        coEvery { chatRepository.continueConversation("id1", "query", "response", "rev1") } returns Unit

        val job = launch(testDispatcher) {
            val success = viewModel.conversationSaveSuccess.first()
            assertTrue(success)
        }

        viewModel.continueConversation("id1", "query", "response", "rev1")
        job.join()
        coVerify { chatRepository.continueConversation("id1", "query", "response", "rev1") }
    }

    @Test
    fun `continueConversation emits false on error`() = runTest {
        coEvery { chatRepository.continueConversation("id2", "query", "response", "rev2") } throws Exception("Test Error")

        val job = launch(testDispatcher) {
            val success = viewModel.conversationSaveSuccess.first()
            assertFalse(success)
        }

        viewModel.continueConversation("id2", "query", "response", "rev2")
        job.join()
        coVerify { chatRepository.continueConversation("id2", "query", "response", "rev2") }
    }

    @Test
    fun `continueConversation returns early if both query and response are blank`() = runTest {
        viewModel.continueConversation("id", "", "  ", "rev1")
        coVerify(exactly = 0) { chatRepository.continueConversation(any(), any(), any(), any()) }
    }

    @Test
    fun `clearChatState resets selectedChatHistory, selectedId, selectedRev, and selectedAiProvider to their initial values`() {
        val dummyHistory = listOf(RealmConversation())
        viewModel.setSelectedChatHistory(dummyHistory)
        viewModel.setSelectedId("test_id")
        viewModel.setSelectedRev("test_rev")
        viewModel.setSelectedAiProvider("openai")

        assertEquals(dummyHistory, viewModel.selectedChatHistory.value)
        assertEquals("test_id", viewModel.selectedId.value)
        assertEquals("test_rev", viewModel.selectedRev.value)
        assertEquals("openai", viewModel.selectedAiProvider.value)

        viewModel.clearChatState()

        assertNull(viewModel.selectedChatHistory.value)
        assertEquals("", viewModel.selectedId.value)
        assertEquals("", viewModel.selectedRev.value)
        assertNull(viewModel.selectedAiProvider.value)
    }

    @Test
    fun `parseAndBuildInitialPage sets pagination state and returns messages`() = runTest {
        val json = "[{\"query\":\"q1\",\"response\":\"r1\"}]"
        val messages = viewModel.parseAndBuildInitialPage(json)
        assertEquals(1, viewModel.allConversations.size)
        assertEquals(1, viewModel.loadedCount)
        assertEquals(2, messages.size) // query and response
    }

    @Test
    fun `processChatHistory sets pagination state and returns messages`() {
        val conversations = listOf(RealmConversation().apply { query = "q1"; response = "r1" })
        val messages = viewModel.processChatHistory(conversations)
        assertEquals(1, viewModel.allConversations.size)
        assertEquals(1, viewModel.loadedCount)
        assertEquals(2, messages.size)
    }

    @Test
    fun `loadMoreConversations returns older messages and updates loadedCount`() {
        val conversations = List(25) { RealmConversation().apply { query = "q$it"; response = "r$it" } }
        viewModel.processChatHistory(conversations)
        assertEquals(20, viewModel.loadedCount)
        val (messages, hasMore) = viewModel.loadMoreConversations()
        assertEquals(25, viewModel.loadedCount)
        assertEquals(false, hasMore)
        assertEquals(10, messages.size) // 5 items * 2
    }

    @Test
    fun `clearPaginationState resets allConversations and loadedCount`() {
        viewModel.processChatHistory(listOf(RealmConversation()))
        viewModel.clearPaginationState()
        assertTrue(viewModel.allConversations.isEmpty())
        assertEquals(0, viewModel.loadedCount)
    }
}

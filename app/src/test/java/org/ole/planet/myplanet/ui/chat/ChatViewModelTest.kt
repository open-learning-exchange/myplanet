package org.ole.planet.myplanet.ui.chat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatRepository: ChatRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        viewModel = ChatViewModel(chatRepository)
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

        viewModel.continueConversation("id1", null, null, "query", "response", "rev1")
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

        viewModel.continueConversation("id2", null, null, "query", "response", "rev2")
        job.join()
        coVerify { chatRepository.continueConversation("id2", "query", "response", "rev2") }
    }

    @Test
    fun `continueConversation uses fragmentId if id is blank`() = runTest {
        coEvery { chatRepository.continueConversation("fragId", "query", "response", "rev1") } returns Unit

        val job = launch(testDispatcher) {
            val success = viewModel.conversationSaveSuccess.first()
            assertTrue(success)
        }

        viewModel.continueConversation("", "fragId", "fragCurId", "query", "response", "rev1")
        job.join()
        coVerify { chatRepository.continueConversation("fragId", "query", "response", "rev1") }
    }

    @Test
    fun `continueConversation uses fragmentCurrentId if id and fragmentId are blank`() = runTest {
        coEvery { chatRepository.continueConversation("fragCurId", "query", "response", "rev1") } returns Unit

        val job = launch(testDispatcher) {
            val success = viewModel.conversationSaveSuccess.first()
            assertTrue(success)
        }

        viewModel.continueConversation(null, "", "fragCurId", "query", "response", "rev1")
        job.join()
        coVerify { chatRepository.continueConversation("fragCurId", "query", "response", "rev1") }
    }

    @Test
    fun `continueConversation returns early if all ids are blank`() = runTest {
        viewModel.continueConversation(null, "", null, "query", "response", "rev1")
        coVerify(exactly = 0) { chatRepository.continueConversation(any(), any(), any(), any()) }
    }

    @Test
    fun `continueConversation returns early if both query and response are blank`() = runTest {
        viewModel.continueConversation("id", "fragId", "fragCurId", "", "  ", "rev1")
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
}

package org.ole.planet.myplanet.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmConversation

class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        viewModel = ChatViewModel()
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

package org.ole.planet.myplanet.ui.chat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ChatResult
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatRepository: ChatRepository
    private lateinit var userRepository: UserRepository
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var voicesRepository: VoicesRepository
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dispatcherProvider: TestDispatcherProvider
    private lateinit var realtimeSyncManager: RealtimeSyncManager
    private val dataUpdateFlow = MutableSharedFlow<TableDataUpdate>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        voicesRepository = mockk(relaxed = true)
        dispatcherProvider = TestDispatcherProvider(testDispatcher)
        realtimeSyncManager = mockk(relaxed = true)
        io.mockk.every { realtimeSyncManager.updatesFor("chats") } returns dataUpdateFlow
        viewModel = ChatViewModel(chatRepository, userRepository, teamsRepository, voicesRepository, dispatcherProvider, realtimeSyncManager)
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
    fun `refreshChatSignal seeds an initial load value for a cold ViewModel`() = runTest {
        val signals = mutableListOf<Unit>()
        val job = launch(testDispatcher) {
            viewModel.refreshChatSignal.collect { signals.add(it) }
        }
        testScheduler.advanceUntilIdle()

        assertEquals(1, signals.size)
        job.cancel()
    }

    @Test
    fun `refreshChatSignal emits when RealtimeSyncManager emits chats update`() = runTest {
        val signals = mutableListOf<Unit>()
        val job = launch(testDispatcher) {
            viewModel.refreshChatSignal.collect { signals.add(it) }
        }
        testScheduler.advanceUntilIdle()
        signals.clear()

        dataUpdateFlow.emit(TableDataUpdate("chats", 0, 1))
        testScheduler.advanceUntilIdle()

        assertEquals(1, signals.size)
        job.cancel()
    }

    @Test
    fun `refreshChatSignal delivers a missed update to a subscriber that attaches after the emit`() = runTest {
        testScheduler.advanceUntilIdle()

        dataUpdateFlow.emit(TableDataUpdate("chats", 1, 0, true))
        testScheduler.advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch(testDispatcher) {
            viewModel.refreshChatSignal.collect { signals.add(it) }
        }
        testScheduler.advanceUntilIdle()

        assertEquals(1, signals.size)
        job.cancel()
    }

    @Test
    fun `clearChatState resets selectedChatHistory, selectedId, selectedRev, and selectedAiProvider to their initial values`() {
        val dummyHistory = listOf(Conversation())
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
        val conversations = listOf(Conversation().apply { query = "q1"; response = "r1" })
        val messages = viewModel.processChatHistory(conversations)
        assertEquals(1, viewModel.allConversations.size)
        assertEquals(1, viewModel.loadedCount)
        assertEquals(2, messages.size)
    }

    @Test
    fun `loadMoreConversations returns older messages and updates loadedCount`() {
        val conversations = List(25) { Conversation().apply { query = "q$it"; response = "r$it" } }
        viewModel.processChatHistory(conversations)
        assertEquals(20, viewModel.loadedCount)
        val (messages, hasMore) = viewModel.loadMoreConversations()
        assertEquals(25, viewModel.loadedCount)
        assertEquals(false, hasMore)
        assertEquals(10, messages.size) // 5 conversations * 2 messages each = 10 messages
    }

    @Test
    fun `clearPaginationState resets allConversations and loadedCount`() {
        viewModel.processChatHistory(listOf(Conversation()))
        viewModel.clearPaginationState()
        assertTrue(viewModel.allConversations.isEmpty())
        assertEquals(0, viewModel.loadedCount)
    }

    @Test
    fun `loadChatHistoryScreenData fetches all data correctly when no caches are provided`() = runTest {
        val user = UserEntity(_id = "user123", planetCode = "planet1", name = "Test User")
        val conversation = ChatHistory().apply {
            createdDate = "123"
            updatedDate = "123"
        }
        val news = News()
        val team = mockk<TeamSummary>(relaxed = true)

        coEvery { userRepository.getUserById("user123") } returns user
        coEvery { voicesRepository.getPlanetNewsMessages("planet1") } returns listOf(news)
        coEvery { chatRepository.getChatHistoryForUser("Test User") } returns listOf(conversation)
        coEvery { teamsRepository.getTeamSummaries("user123") } returns listOf(team)
        coEvery { teamsRepository.getShareableEnterpriseSummaries("user123") } returns listOf(team)
        coEvery { teamsRepository.getTeamSummaryById("community1@parent1") } returns team

        val job = launch(testDispatcher) {
            viewModel.screenData.collect {}
        }

        viewModel.loadChatHistoryScreenData(
            userId = "user123",
            parentCode = "parent1",
            communityName = "community1"
        )

        testScheduler.advanceUntilIdle()

        val result = viewModel.screenData.value
        org.junit.Assert.assertNotNull(result)
        result!!

        assertEquals(user, result.currentUser)
        assertEquals(listOf(conversation), result.chatHistory)
        assertEquals(listOf(news), result.newsMessages)
        assertEquals(listOf(team), result.shareTargets.teams)
        assertEquals(listOf(team), result.shareTargets.enterprises)
        assertEquals(team, result.shareTargets.community)

        coVerify { userRepository.getUserById("user123") }
        coVerify { voicesRepository.getPlanetNewsMessages("planet1") }
        coVerify { chatRepository.getChatHistoryForUser("Test User") }
        coVerify { teamsRepository.getTeamSummaries("user123") }

        job.cancel()
    }

    @Test
    fun `searchChats delegates to repository correctly and empty query resets filtered list`() = runTest {
        val chat1 = ChatHistory().apply { title = "Chat 1" }
        val chat2 = ChatHistory().apply { title = "Chat 2" }

        coEvery { chatRepository.getChatHistoryForUser(any()) } returns listOf(chat1, chat2)
        coEvery { chatRepository.searchChats("Chat 1", org.ole.planet.myplanet.repository.ChatSearchMode.TITLE, any()) } returns listOf(chat1)

        viewModel.loadChatHistoryScreenData("user123", null, null)
        testScheduler.advanceUntilIdle()

        viewModel.searchChats("Chat 1", isFullSearch = false, isQuestion = false)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.filteredChats.value.size)

        viewModel.searchChats("", isFullSearch = false, isQuestion = false)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.filteredChats.value.size)

        coVerify { chatRepository.searchChats("Chat 1", org.ole.planet.myplanet.repository.ChatSearchMode.TITLE, any()) }
    }

    @Test
    fun `loadChatHistoryScreenData uses cached data and handles nulls gracefully`() = runTest {
        val cachedUser = mockk<UserEntity>(relaxed = true)
        val conversation = ChatHistory().apply {
            createdDate = "123"
            updatedDate = "123"
        }
        val news = News()

        coEvery { userRepository.getUserById("user123") } returns cachedUser
        coEvery { cachedUser.planetCode } returns "planet2"
        coEvery { cachedUser.name } returns "Cached User"
        coEvery { voicesRepository.getPlanetNewsMessages("planet2") } returns listOf(news)
        coEvery { chatRepository.getChatHistoryForUser("Cached User") } returns listOf(conversation)
        coEvery { teamsRepository.getTeamSummaries(any()) } returns emptyList()
        coEvery { teamsRepository.getShareableEnterpriseSummaries(any()) } returns emptyList()

        // First call to populate cache
        viewModel.loadChatHistoryScreenData(
            userId = "user123",
            parentCode = "parent1",
            communityName = "community1"
        )
        testScheduler.advanceUntilIdle()

        // Clear mocks to verify cache usage
        io.mockk.clearMocks(userRepository, teamsRepository, voicesRepository, chatRepository, answers = false)

        val job = launch(testDispatcher) {
            viewModel.screenData.collect {}
        }

        // Second call should use cache
        viewModel.loadChatHistoryScreenData(
            userId = "user123",
            parentCode = "parent1",
            communityName = "community1"
        )
        testScheduler.advanceUntilIdle()

        val result = viewModel.screenData.value
        org.junit.Assert.assertNotNull(result)
        result!!

        assertEquals(cachedUser, result.currentUser)
        assertEquals(listOf(conversation), result.chatHistory)
        assertEquals(listOf(news), result.newsMessages)

        coVerify(exactly = 0) { userRepository.getUserById(any()) }
        coVerify(exactly = 0) { teamsRepository.getTeamSummaries(any()) }

        job.cancel()
    }

    @Test
    fun `shareChatToVoices emits AlreadyShared when previously shared`() = runTest {
        val chatId = "chat_123"
        val viewInId = "view_456"
        val payload = hashMapOf<String?, String>("test" to "data")

        coEvery { voicesRepository.isAlreadyShared(chatId, viewInId) } returns true

        val emissions = mutableListOf<ChatViewModel.ShareChatResult>()
        val job = launch(testDispatcher) {
            viewModel.shareResult.collect { emissions.add(it) }
        }

        viewModel.shareChatToVoices(chatId, viewInId, payload)
        testScheduler.advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ChatViewModel.ShareChatResult.AlreadyShared)

        coVerify(exactly = 1) { voicesRepository.isAlreadyShared(chatId, viewInId) }
        coVerify(exactly = 0) { voicesRepository.createNews(any(), any(), any()) }

        job.cancel()
    }

    @Test
    fun `shareChatToVoices emits Shared when successfully creates news`() = runTest {
        val chatId = "chat_123"
        val viewInId = "view_456"
        val payload = hashMapOf<String?, String>("test" to "data")
        val createdNews = News().apply { id = "news_123" }

        coEvery { voicesRepository.isAlreadyShared(chatId, viewInId) } returns false
        coEvery { voicesRepository.createNews(payload, any(), null) } returns createdNews

        val emissions = mutableListOf<ChatViewModel.ShareChatResult>()
        val job = launch(testDispatcher) {
            viewModel.shareResult.collect { emissions.add(it) }
        }

        viewModel.shareChatToVoices(chatId, viewInId, payload)
        testScheduler.advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ChatViewModel.ShareChatResult.Shared)
        val result = emissions[0] as ChatViewModel.ShareChatResult.Shared
        assertEquals(createdNews, result.news)
        assertEquals(chatId, result.chatId)

        coVerify(exactly = 1) { voicesRepository.isAlreadyShared(chatId, viewInId) }
        coVerify(exactly = 1) { voicesRepository.createNews(payload, any(), null) }

        job.cancel()
    }

    @Test
    fun `viewModel fetchAiProviders proxies to chatRepository`() = runTest {
        val serverUrl = "https://example.com"
        val expectedProviders = mapOf("provider1" to true, "provider2" to false)
        coEvery { chatRepository.fetchAiProviders(serverUrl) } returns expectedProviders

        viewModel.fetchAiProviders(serverUrl)
        testScheduler.advanceUntilIdle()

        assertEquals(expectedProviders, viewModel.aiProviders.value)
        assertEquals(false, viewModel.aiProvidersError.value)
        assertEquals(false, viewModel.aiProvidersLoading.value)
        coVerify(exactly = 1) { chatRepository.fetchAiProviders(serverUrl) }
    }

    @Test
    fun `viewModel getLatestRev proxies to chatRepository`() = runTest {
        val id = "chat123"
        val expectedRev = "1-abc"
        coEvery { chatRepository.getLatestRev(id) } returns expectedRev

        val result = viewModel.getLatestRev(id)
        assertEquals(expectedRev, result)
        coVerify(exactly = 1) { chatRepository.getLatestRev(id) }
    }

    @Test
    fun `viewModel sendNewChatRequest proxies to chatRepository`() = runTest {
        val query = "hello"
        val userName = "User"
        val aiProvider = AiProvider("model1", "provider1")
        val expectedResult = ChatResult.Success("response", "chat123", "1-abc")
        coEvery { chatRepository.sendNewChatRequest(query, userName, aiProvider) } returns expectedResult

        val result = viewModel.sendNewChatRequest(query, userName, aiProvider)
        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { chatRepository.sendNewChatRequest(query, userName, aiProvider) }
    }

    @Test
    fun `viewModel sendContinueChatRequest proxies to chatRepository`() = runTest {
        val query = "hello again"
        val userName = "User"
        val aiProvider = AiProvider("model1", "provider1")
        val id = "chat123"
        val rev = "1-abc"
        val expectedResult = ChatResult.Success("response", id, "2-def")
        coEvery { chatRepository.sendContinueChatRequest(query, userName, aiProvider, id, rev) } returns expectedResult

        val result = viewModel.sendContinueChatRequest(query, userName, aiProvider, id, rev)
        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { chatRepository.sendContinueChatRequest(query, userName, aiProvider, id, rev) }
    }

    @Test
    fun `viewModel getUserById proxies to userRepository`() = runTest {
        val userId = "user123"
        val expectedUser = UserEntity(_id = userId, name = "Test User")
        coEvery { userRepository.getUserById(userId) } returns expectedUser

        val result = viewModel.getUserById(userId)
        assertEquals(expectedUser, result)
        coVerify(exactly = 1) { userRepository.getUserById(userId) }
    }

    @Test
    fun `viewModel chatUiState correctly combines state flows`() = runTest {
        val job = launch(testDispatcher) {
            viewModel.chatUiState.collect {}
        }

        testScheduler.advanceUntilIdle()

        // Initial state
        var currentState = viewModel.chatUiState.value
        assertEquals(null, currentState.selectedChatHistory)
        assertEquals(null, currentState.selectedAiProvider)
        assertEquals("", currentState.selectedId)
        assertEquals("", currentState.selectedRev)
        assertEquals(null, currentState.aiProviders)
        assertEquals(false, currentState.aiProvidersLoading)
        assertEquals(false, currentState.aiProvidersError)

        // Update states
        val history = listOf(Conversation().apply { query = "q"; response = "r" })
        viewModel.setSelectedChatHistory(history)
        viewModel.setSelectedAiProvider("OpenAI")
        viewModel.setSelectedId("chat123")
        viewModel.setSelectedRev("1-abc")
        viewModel.setAiProviders(mapOf("OpenAI" to true))
        viewModel.setAiProvidersLoading(true)
        viewModel.setAiProvidersError(true)

        testScheduler.advanceUntilIdle()

        currentState = viewModel.chatUiState.value
        assertEquals(history, currentState.selectedChatHistory)
        assertEquals("OpenAI", currentState.selectedAiProvider)
        assertEquals("chat123", currentState.selectedId)
        assertEquals("1-abc", currentState.selectedRev)
        assertEquals(mapOf("OpenAI" to true), currentState.aiProviders)
        assertEquals(true, currentState.aiProvidersLoading)
        assertEquals(true, currentState.aiProvidersError)

        job.cancel()
    }
}

package org.ole.planet.myplanet.ui.chat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.utils.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider

class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatRepository: ChatRepository
    private lateinit var userRepository: UserRepository
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var voicesRepository: VoicesRepository
    private val testDispatcher get() = mainDispatcherRule.testDispatcher
    private lateinit var dispatcherProvider: TestDispatcherProvider

    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        voicesRepository = mockk(relaxed = true)
        dispatcherProvider = TestDispatcherProvider(testDispatcher)
        viewModel = ChatViewModel(chatRepository, userRepository, teamsRepository, voicesRepository, dispatcherProvider)
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
        assertEquals(10, messages.size) // 5 conversations * 2 messages each = 10 messages
    }

    @Test
    fun `clearPaginationState resets allConversations and loadedCount`() {
        viewModel.processChatHistory(listOf(RealmConversation()))
        viewModel.clearPaginationState()
        assertTrue(viewModel.allConversations.isEmpty())
        assertEquals(0, viewModel.loadedCount)
    }

    @Test
    fun `loadChatHistoryScreenData fetches all data correctly when no caches are provided`() = runTest {
        val user = mockk<RealmUser>(relaxed = true)
        val conversation = RealmChatHistory().apply {
            createdDate = "123"
            updatedDate = "123"
        }
        val news = RealmNews()
        val team = mockk<TeamSummary>(relaxed = true)

        coEvery { userRepository.getUserById("user123") } returns user
        coEvery { user.planetCode } returns "planet1"
        coEvery { user.name } returns "Test User"
        coEvery { user._id } returns "user123"
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

        // Wait for coroutine to process
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
    fun `searchChats by title correctly filters list`() = runTest {
        val chat1 = RealmChatHistory().apply { title = "First Chat" }
        val chat2 = RealmChatHistory().apply { title = "Second Discussion" }

        coEvery { chatRepository.getChatHistoryForUser(any()) } returns listOf(chat1, chat2)

        viewModel.loadChatHistoryScreenData("user123", null, null)
        testScheduler.advanceUntilIdle()

        viewModel.searchChats("First", isFullSearch = false, isQuestion = false)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.filteredChats.value.size)
        assertEquals("First Chat", viewModel.filteredChats.value[0].title)
    }

    @Test
    fun `searchChats by full conversation filters by question`() = runTest {
        val chat1 = RealmChatHistory().apply {
            title = "Chat 1"
            conversations = io.realm.RealmList(RealmConversation().apply { query = "How is the weather?" })
        }
        val chat2 = RealmChatHistory().apply {
            title = "Chat 2"
            conversations = io.realm.RealmList(RealmConversation().apply { query = "Tell me a joke." })
        }

        coEvery { chatRepository.getChatHistoryForUser(any()) } returns listOf(chat1, chat2)

        viewModel.loadChatHistoryScreenData("user123", null, null)
        testScheduler.advanceUntilIdle()

        viewModel.searchChats("weather", isFullSearch = true, isQuestion = true)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.filteredChats.value.size)
        assertEquals("Chat 1", viewModel.filteredChats.value[0].title)
    }

    @Test
    fun `searchChats with empty query resets filtered list`() = runTest {
        val chat1 = RealmChatHistory().apply { title = "Chat 1" }
        val chat2 = RealmChatHistory().apply { title = "Chat 2" }

        coEvery { chatRepository.getChatHistoryForUser(any()) } returns listOf(chat1, chat2)

        viewModel.loadChatHistoryScreenData("user123", null, null)
        testScheduler.advanceUntilIdle()

        viewModel.searchChats("Chat 1", isFullSearch = false, isQuestion = false)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.filteredChats.value.size)

        viewModel.searchChats("", isFullSearch = false, isQuestion = false)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.filteredChats.value.size)
    }

    @Test
    fun `loadChatHistoryScreenData uses cached data and handles nulls gracefully`() = runTest {
        val cachedUser = mockk<RealmUser>(relaxed = true)
        val conversation = RealmChatHistory().apply {
            createdDate = "123"
            updatedDate = "123"
        }
        val news = RealmNews()

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

        // Verify user and targets were NOT fetched again
        coVerify(exactly = 0) { userRepository.getUserById(any()) }
        coVerify(exactly = 0) { teamsRepository.getTeamSummaries(any()) }

        job.cancel()
    }
}

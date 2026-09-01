package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class VoicesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var voicesRepository: VoicesRepository
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository
    private lateinit var resourcesRepository: org.ole.planet.myplanet.repository.ResourcesRepository
    private lateinit var viewModel: VoicesViewModel

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val mainImmediate: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val io: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val default: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val unconfined: CoroutineDispatcher = UnconfinedTestDispatcher()
    }

    @Before
    fun setup() {
        voicesRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        resourcesRepository = mockk(relaxed = true)
        viewModel = VoicesViewModel(voicesRepository, teamsRepository, testDispatcherProvider, userRepository, resourcesRepository)
    }

    @Test
    fun `test search and label filter results`() = runTest {
        val news1 = mockk<News>(relaxed = true) {
            coEvery { message } returns "This is a Test message"
            coEvery { labels } returns listOf("Label1")
            coEvery { userName } returns "User1"
            coEvery { newsTitle } returns "Title1"
        }
        val news2 = mockk<News>(relaxed = true) {
            coEvery { message } returns "Another Message"
            coEvery { labels } returns listOf("Label2")
            coEvery { userName } returns "User2"
            coEvery { newsTitle } returns "Title2"
        }

        coEvery { voicesRepository.getCommunityNews(any()) } returns flowOf(listOf(news1, news2))

        var result: List<News?> = emptyList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredNews.collect {
                result = it
            }
        }

        viewModel.observeCommunityNews("test_user")
        advanceUntilIdle()

        assertEquals(2, result.size)

        // Test search query pre-trimmed and case insensitive
        viewModel.updateSearchQuery(" tEsT  ")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(news1, result[0])

        viewModel.updateSearchQuery("")

        // Test label filter
        viewModel.updateSelectedLabel("Label2")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(news2, result[0])
    }

    @Test
    fun `test simultaneous query and label filtering`() = runTest {
        val news1 = mockk<News>(relaxed = true) {
            coEvery { message } returns "Apple"
            coEvery { labels } returns listOf("Fruit")
        }
        val news2 = mockk<News>(relaxed = true) {
            coEvery { message } returns "Banana"
            coEvery { labels } returns listOf("Fruit")
        }
        val news3 = mockk<News>(relaxed = true) {
            coEvery { message } returns "Carrot"
            coEvery { labels } returns listOf("Vegetable")
        }

        coEvery { voicesRepository.getCommunityNews(any()) } returns flowOf(listOf(news1, news2, news3))

        var result: List<News?> = emptyList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredNews.collect {
                result = it
            }
        }

        viewModel.observeCommunityNews("test_user")
        advanceUntilIdle()

        // Set both states
        viewModel.updateSelectedLabel("Fruit")
        viewModel.updateSearchQuery("apple")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(news1, result[0])

        // Change query but keep label
        viewModel.updateSearchQuery("banana")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(news2, result[0])

        // Query for item with different label
        viewModel.updateSearchQuery("carrot")
        advanceUntilIdle()

        assertEquals(0, result.size)
    }

    @Test
    fun `test static label filter uses hoisted label map`() = runTest {
        val offerNews = mockk<News>(relaxed = true) {
            coEvery { message } returns "Offering something"
            coEvery { labels } returns listOf("offer")
            coEvery { userName } returns "User1"
            coEvery { newsTitle } returns "Title1"
        }
        val helpNews = mockk<News>(relaxed = true) {
            coEvery { message } returns "Need help"
            coEvery { labels } returns listOf("help")
            coEvery { userName } returns "User2"
            coEvery { newsTitle } returns "Title2"
        }

        coEvery { voicesRepository.getCommunityNews(any()) } returns flowOf(listOf(offerNews, helpNews))

        var result: List<News?> = emptyList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredNews.collect { result = it }
        }

        viewModel.observeCommunityNews("test_user")
        advanceUntilIdle()

        viewModel.updateSelectedLabel("Offer")
        advanceUntilIdle()
        assertEquals(1, result.size)
        assertEquals(offerNews, result[0])

        viewModel.updateSelectedLabel("Help wanted")
        advanceUntilIdle()
        assertEquals(1, result.size)
        assertEquals(helpNews, result[0])
    }

    @Test
    fun `test dynamic label filter resolves unknown label values`() = runTest {
        val customLabelValue = "some:custom:label"
        val customNews = mockk<News>(relaxed = true) {
            coEvery { message } returns "Custom post"
            coEvery { labels } returns listOf(customLabelValue)
            coEvery { userName } returns "User1"
            coEvery { newsTitle } returns "Title1"
        }
        val otherNews = mockk<News>(relaxed = true) {
            coEvery { message } returns "Other post"
            coEvery { labels } returns listOf("offer")
            coEvery { userName } returns "User2"
            coEvery { newsTitle } returns "Title2"
        }

        coEvery { voicesRepository.getCommunityNews(any()) } returns flowOf(listOf(customNews, otherNews))

        var result: List<News?> = emptyList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredNews.collect { result = it }
        }

        viewModel.observeCommunityNews("test_user")
        advanceUntilIdle()

        val expectedDisplayName = org.ole.planet.myplanet.services.VoicesLabelManager.formatLabelValue(customLabelValue)
        viewModel.updateSelectedLabel(expectedDisplayName)
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals(customNews, result[0])
    }

    @Test
    fun `test downloadReferencedResources skips news with empty images and downloads referenced ids`() = runTest {
        val newsWithResource = News().apply {
            id = "news1"
            images = """[{"resourceId":"res-123"}]"""
        }
        val newsWithEmptyImages = News().apply {
            id = "news2"
            images = null
        }
        val newsWithNoResourceId = News().apply {
            id = "news3"
            images = """[{"resourceId":""}]"""
        }

        coEvery { resourcesRepository.getLibraryItemsByIds(any()) } returns emptyList()
        coEvery { resourcesRepository.downloadResources(any()) } returns true

        viewModel.downloadReferencedResources(listOf(newsWithResource, newsWithEmptyImages, newsWithNoResourceId))
        advanceUntilIdle()

        coVerify {
            resourcesRepository.getLibraryItemsByIds(match { it.contains("res-123") && it.size == 1 })
            resourcesRepository.downloadResources(any())
        }
    }

    @Test
    fun `test downloadReferencedResources does nothing for null news or empty list`() = runTest {
        coEvery { resourcesRepository.getLibraryItemsByIds(any()) } returns emptyList()
        coEvery { resourcesRepository.downloadResources(any()) } returns true

        viewModel.downloadReferencedResources(listOf(null))
        advanceUntilIdle()

        coVerify(exactly = 0) { resourcesRepository.downloadResources(any()) }
    }
}

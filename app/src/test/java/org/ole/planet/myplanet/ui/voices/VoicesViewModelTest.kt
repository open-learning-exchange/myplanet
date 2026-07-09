package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
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
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class VoicesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var voicesRepository: VoicesRepository
    private lateinit var userRepository: UserRepository
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var viewModel: VoicesViewModel

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val io: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val default: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val unconfined: CoroutineDispatcher = UnconfinedTestDispatcher()
    }

    @Before
    fun setup() {
        voicesRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        viewModel = VoicesViewModel(voicesRepository, userRepository, teamsRepository, testDispatcherProvider)
    }

    @Test
    fun `test search and label filter results`() = runTest {
        val news1 = mockk<RealmNews>(relaxed = true) {
            coEvery { message } returns "This is a Test message"
            coEvery { labels } returns io.realm.RealmList("Label1")
            coEvery { userName } returns "User1"
            coEvery { newsTitle } returns "Title1"
        }
        val news2 = mockk<RealmNews>(relaxed = true) {
            coEvery { message } returns "Another Message"
            coEvery { labels } returns io.realm.RealmList("Label2")
            coEvery { userName } returns "User2"
            coEvery { newsTitle } returns "Title2"
        }

        coEvery { voicesRepository.getCommunityNews(any()) } returns flowOf(listOf(news1, news2))

        var result: List<RealmNews?> = emptyList()
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
}

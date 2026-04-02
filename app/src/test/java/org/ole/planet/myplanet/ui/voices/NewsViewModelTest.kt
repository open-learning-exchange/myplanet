package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.MainDispatcherRule
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var resourcesRepository: ResourcesRepository
    private lateinit var viewModel: NewsViewModel

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val io: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val default: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val unconfined: CoroutineDispatcher = UnconfinedTestDispatcher()
    }

    @Before
    fun setup() {
        resourcesRepository = mockk()
        viewModel = NewsViewModel(resourcesRepository, testDispatcherProvider)
    }

    @Test
    fun `getPrivateImageUrlsCreatedAfter updates flow with list`() = runTest {
        val timestamp = 123456789L
        val expectedUrls = listOf("url1", "url2")
        coEvery { resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp) } returns expectedUrls

        var capturedResult: List<String>? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.privateImageUrls.collect { urls ->
                capturedResult = urls
            }
        }

        viewModel.getPrivateImageUrlsCreatedAfter(timestamp)

        advanceUntilIdle()

        assertEquals(expectedUrls, capturedResult)
    }

    @Test
    fun `getPrivateImageUrlsCreatedAfter updates flow with empty list`() = runTest {
        val timestamp = 123456789L
        val expectedUrls = emptyList<String>()
        coEvery { resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp) } returns expectedUrls

        var capturedResult: List<String>? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.privateImageUrls.collect { urls ->
                capturedResult = urls
            }
        }

        viewModel.getPrivateImageUrlsCreatedAfter(timestamp)

        advanceUntilIdle()

        assertEquals(expectedUrls, capturedResult)
    }
}

package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var voicesRepository: VoicesRepository
    private lateinit var viewModel: NewsViewModel

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainDispatcherRule.testDispatcher
        override val io: CoroutineDispatcher = mainDispatcherRule.testDispatcher
        override val default: CoroutineDispatcher = mainDispatcherRule.testDispatcher
        override val unconfined: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    }

    @Before
    fun setup() {
        voicesRepository = mockk()
        viewModel = NewsViewModel(voicesRepository, testDispatcherProvider)
    }

    @Test
    fun `getPrivateImageUrlsCreatedAfter updates flow with list`() = runTest {
        val timestamp = 123456789L
        val expectedUrls = listOf("url1", "url2")
        coEvery { voicesRepository.getPrivateImageUrlsCreatedAfter(timestamp) } returns expectedUrls

        var capturedResult: List<String>? = null
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
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
        coEvery { voicesRepository.getPrivateImageUrlsCreatedAfter(timestamp) } returns expectedUrls

        var capturedResult: List<String>? = null
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.privateImageUrls.collect { urls ->
                capturedResult = urls
            }
        }

        viewModel.getPrivateImageUrlsCreatedAfter(timestamp)

        advanceUntilIdle()

        assertEquals(expectedUrls, capturedResult)
    }
}

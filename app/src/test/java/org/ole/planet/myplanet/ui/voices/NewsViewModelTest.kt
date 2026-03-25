package org.ole.planet.myplanet.ui.voices

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.MainDispatcherRule
import org.ole.planet.myplanet.repository.ResourcesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var resourcesRepository: ResourcesRepository
    private lateinit var viewModel: NewsViewModel

    @Before
    fun setup() {
        resourcesRepository = mockk()
        viewModel = NewsViewModel(resourcesRepository)
    }

    @Test
    fun `getPrivateImageUrlsCreatedAfter returns list via callback`() = runTest {
        val timestamp = 123456789L
        val expectedUrls = listOf("url1", "url2")
        coEvery { resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp) } returns expectedUrls

        var capturedResult: List<String>? = null
        viewModel.getPrivateImageUrlsCreatedAfter(timestamp) { urls ->
            capturedResult = urls
        }

        advanceUntilIdle()

        assertNotNull(capturedResult)
        assertEquals(expectedUrls, capturedResult)
    }

    @Test
    fun `getPrivateImageUrlsCreatedAfter returns empty list via callback`() = runTest {
        val timestamp = 123456789L
        val expectedUrls = emptyList<String>()
        coEvery { resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp) } returns expectedUrls

        var capturedResult: List<String>? = null
        viewModel.getPrivateImageUrlsCreatedAfter(timestamp) { urls ->
            capturedResult = urls
        }

        advanceUntilIdle()

        assertNotNull(capturedResult)
        assertEquals(expectedUrls, capturedResult)
    }
}

package org.ole.planet.myplanet.ui.resources

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.mockk.coEvery
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.LibraryWithMetadata
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import com.google.gson.JsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesViewModelTest {

    private lateinit var viewModel: ResourcesViewModel
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ResourcesViewModel(
            resourcesRepository,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addResourcesToUserLibrary with successful result returns success`() = runTest {
        val resourceIds = listOf("res1", "res2")
        val userId = "user123"
        coEvery { resourcesRepository.addResourcesToUserLibrary(resourceIds, userId) } returns Result.success(Unit)

        val result = viewModel.addResourcesToUserLibrary(resourceIds, userId)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `addResourcesToUserLibrary with failure result returns failure`() = runTest {
        val resourceIds = listOf("res1", "res2")
        val userId = "user123"
        val exception = Exception("Failed to add resources")
        coEvery { resourcesRepository.addResourcesToUserLibrary(resourceIds, userId) } returns Result.failure(exception)

        val result = viewModel.addResourcesToUserLibrary(resourceIds, userId)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `addResourcesToUserLibrary with empty resource list returns result from repository`() = runTest {
        val resourceIds = emptyList<String>()
        val userId = "user123"
        coEvery { resourcesRepository.addResourcesToUserLibrary(resourceIds, userId) } returns Result.success(Unit)

        val result = viewModel.addResourcesToUserLibrary(resourceIds, userId)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `observeOpenedResourceIds updates openedResourceIds state flow`() = runTest {
        val userId = "user123"
        val mockFlow = kotlinx.coroutines.flow.flowOf(setOf("res1", "res2"))
        coEvery { resourcesRepository.observeOpenedResourceIds(userId) } returns mockFlow

        viewModel.observeOpenedResourceIds(userId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("res1", "res2"), viewModel.openedResourceIds.value)
    }

    @Test
    fun `getLibraryListModels maps enriched libraries to ResourceListModels`() = runTest {
        val mockLibrary = mockk<RealmMyLibrary>(relaxed = true) {
            every { id } returns "lib1"
            every { title } returns "Library 1"
            every { isResourceOffline() } returns true
        }
        val mockRating = mockk<JsonObject>(relaxed = true)
        val mockTag = mockk<RealmTag>(relaxed = true) {
            every { id } returns "tag1"
            every { name } returns "Tag 1"
        }

        coEvery { resourcesRepository.getEnrichedLibraries(any(), any()) } returns listOf(
            LibraryWithMetadata(mockLibrary, mockRating, listOf(mockTag))
        )

        val result = viewModel.getLibraryListModels(true, "modelId")

        assertEquals(1, result.size)
        assertEquals("lib1", result[0].library.id)
    }
}
